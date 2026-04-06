package pl.nelson.ncore.deathchest;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import pl.nelson.ncore.Main;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;

/**
 * Gestiona todos los cofres de muerte activos:
 *  - Persistencia en SQLite
 *  - Creación / eliminación de cofres y hologramas (TextDisplay)
 *  - Tarea periódica que actualiza el timer y expira cofres
 */
public class DeathChestManager {

    private final Main plugin;
    private Connection connection;

    // ownerUUID → DeathChest
    private final Map<UUID, DeathChest> byOwner = new HashMap<>();
    // "world,x,y,z" → ownerUUID  (búsqueda rápida por bloque clicado)
    private final Map<String, UUID> byLocation = new HashMap<>();

    private final NamespacedKey PDC_KEY;

    public DeathChestManager(Main plugin) {
        this.plugin = plugin;
        this.PDC_KEY = new NamespacedKey(plugin, "death_chest_owner");
        initDatabase();
        loadChests();
        startTickTask();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  SQLite
    // ════════════════════════════════════════════════════════════════════════

    private void initDatabase() {
        // Intentar cargar el driver SQLite: primero el nombre con relocación (JAR compilado
        // con mvn package), luego el original (por si se carga desde el IDE sin shade).
        boolean driverFound = false;
        for (String driverClass : new String[]{
                "pl.nelson.ncore.libs.sqlite.JDBC", // nombre tras relocation con maven-shade
                "org.sqlite.JDBC"                    // nombre original (sin shade / IDE)
        }) {
            try {
                Class.forName(driverClass);
                driverFound = true;
                break;
            } catch (ClassNotFoundException ignored) { }
        }

        if (!driverFound) {
            plugin.getLogger().severe("[DeathChest] Driver SQLite no encontrado. " +
                    "Ejecuta 'mvn clean package' y usa el JAR de la carpeta target/.");
            return;
        }

        try {
            plugin.getDataFolder().mkdirs();
            File db = new File(plugin.getDataFolder(), "death_chests.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + db.getAbsolutePath());

            try (Statement st = connection.createStatement()) {
                st.execute("""
                    CREATE TABLE IF NOT EXISTS death_chests (
                        id          TEXT PRIMARY KEY,
                        owner_uuid  TEXT NOT NULL,
                        owner_name  TEXT NOT NULL,
                        world       TEXT NOT NULL,
                        x           INTEGER NOT NULL,
                        y           INTEGER NOT NULL,
                        z           INTEGER NOT NULL,
                        expiry_time INTEGER NOT NULL
                    )
                """);
            }

            plugin.getLogger().info("[DeathChest] Base de datos SQLite lista.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[DeathChest] Error al inicializar SQLite", e);
        }
    }

    private void loadChests() {
        if (connection == null) return;

        int loaded = 0;
        int cleaned = 0;

        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM death_chests")) {

            while (rs.next()) {
                UUID id        = UUID.fromString(rs.getString("id"));
                UUID owner     = UUID.fromString(rs.getString("owner_uuid"));
                String name    = rs.getString("owner_name");
                String world   = rs.getString("world");
                int x          = rs.getInt("x");
                int y          = rs.getInt("y");
                int z          = rs.getInt("z");
                long expiry    = rs.getLong("expiry_time");

                DeathChest chest = new DeathChest(id, owner, name, world, x, y, z, expiry);

                // Si ya expiró, limpiar
                if (chest.isExpired()) {
                    deleteFromDB(id);
                    tryRemoveBlock(chest, false);
                    cleaned++;
                    continue;
                }

                Location loc = chest.getLocation();
                if (loc == null) {
                    // Mundo no cargado todavía — lo registramos igual y lo
                    // verificamos la primera vez que se intente acceder
                    byOwner.put(owner, chest);
                    byLocation.put(locKey(world, x, y, z), owner);
                    loaded++;
                    continue;
                }

                // El bloque ya no es un cofre (alguien lo rompió)
                if (loc.getBlock().getType() != Material.CHEST) {
                    deleteFromDB(id);
                    cleaned++;
                    continue;
                }

                byOwner.put(owner, chest);
                byLocation.put(locKey(world, x, y, z), owner);
                spawnHologram(chest);
                loaded++;
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[DeathChest] Error al cargar cofres", e);
        }

        plugin.getLogger().info("[DeathChest] " + loaded + " cofres cargados, " + cleaned + " expirados limpiados.");
    }

    private void saveDB(DeathChest chest) {
        if (connection == null) return;
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO death_chests VALUES (?,?,?,?,?,?,?,?)")) {
            ps.setString(1, chest.getId().toString());
            ps.setString(2, chest.getOwnerUUID().toString());
            ps.setString(3, chest.getOwnerName());
            ps.setString(4, chest.getWorldName());
            ps.setInt(5, chest.getX());
            ps.setInt(6, chest.getY());
            ps.setInt(7, chest.getZ());
            ps.setLong(8, chest.getExpiryTime());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[DeathChest] Error al guardar cofre", e);
        }
    }

    private void deleteFromDB(UUID chestId) {
        if (connection == null) return;
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM death_chests WHERE id = ?")) {
            ps.setString(1, chestId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[DeathChest] Error al eliminar cofre de DB", e);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Creación de cofres
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Crea un cofre de muerte para el jugador en la ubicación de su muerte.
     * Llama esto desde MuerteListener tras limpiar event.getDrops().
     */
    public void createChest(Player player, Location deathLoc, List<ItemStack> items) {

        // Filtrar ítems nulos o vacíos
        List<ItemStack> validItems = items.stream()
                .filter(i -> i != null && i.getType() != Material.AIR)
                .toList();

        if (validItems.isEmpty()) return; // Sin ítems, sin cofre

        // Si ya tiene un cofre activo, eliminarlo antes (sin soltar ítems)
        if (byOwner.containsKey(player.getUniqueId())) {
            forceRemoveChest(byOwner.get(player.getUniqueId()), false);
        }

        // Buscar bloque válido donde colocar el cofre
        Location chestLoc = findPlacementLocation(deathLoc);
        if (chestLoc == null) {
            // No hay sitio — soltar ítems normalmente
            for (ItemStack item : validItems) {
                deathLoc.getWorld().dropItemNaturally(deathLoc, item);
            }
            player.sendMessage(Component.text("☠ No se pudo crear el cofre de muerte — " +
                    "los objetos fueron soltados.", NamedTextColor.RED));
            return;
        }

        // Colocar bloque de cofre
        Block block = chestLoc.getBlock();
        block.setType(Material.CHEST);

        // Llenar inventario
        Chest chestState = (Chest) block.getState();
        Inventory inv = chestState.getInventory();

        List<ItemStack> overflow = new ArrayList<>();
        for (ItemStack item : validItems) {
            Map<Integer, ItemStack> leftover = inv.addItem(item.clone());
            overflow.addAll(leftover.values());
        }

        // Ítems que no caben → soltar al lado del cofre
        for (ItemStack extra : overflow) {
            chestLoc.getWorld().dropItemNaturally(chestLoc, extra);
        }

        // Construir objeto DeathChest
        long expiry   = System.currentTimeMillis() + getExpiryMillis();
        UUID chestId  = UUID.randomUUID();
        DeathChest dc = new DeathChest(chestId, player.getUniqueId(), player.getName(), chestLoc, expiry);

        // Registrar
        byOwner.put(player.getUniqueId(), dc);
        byLocation.put(locKey(chestLoc), player.getUniqueId());
        saveDB(dc);

        // Holograma encima del cofre
        spawnHologram(dc);

        // Avisar al jugador
        long mins = getExpiryMillis() / 60_000;
        player.sendMessage(Component.empty()
                .append(Component.text("☠ ", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text("Tus objetos están guardados en ", NamedTextColor.YELLOW))
                .append(Component.text("X:" + chestLoc.getBlockX() +
                                       " Y:" + chestLoc.getBlockY() +
                                       " Z:" + chestLoc.getBlockZ(), NamedTextColor.WHITE))
                .append(Component.text(" · Expira en ", NamedTextColor.YELLOW))
                .append(Component.text(mins + " min", NamedTextColor.RED)));

        player.sendMessage(Component.text("Usa ", NamedTextColor.GRAY)
                .append(Component.text("/cofre", NamedTextColor.AQUA))
                .append(Component.text(" para teletransportarte a él.", NamedTextColor.GRAY)));
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Eliminación de cofres
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Elimina el cofre (bloque + holograma + DB) de forma interna.
     * @param dropItems Si true, suelta los ítems al suelo antes de eliminar.
     */
    public void forceRemoveChest(DeathChest chest, boolean dropItems) {
        // Soltar o limpiar ítems
        tryRemoveBlock(chest, dropItems);

        // Eliminar holograma
        removeHologram(chest);

        // Desregistrar
        byOwner.remove(chest.getOwnerUUID());
        byLocation.remove(locKey(chest.getWorldName(), chest.getX(), chest.getY(), chest.getZ()));
        deleteFromDB(chest.getId());
    }

    /**
     * Llamar cuando el jugador recogió todos sus ítems (inventario vacío al cerrar).
     * Elimina cofre y holograma sin soltar nada.
     */
    public void onChestEmptied(DeathChest chest) {
        forceRemoveChest(chest, false);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Helpers de eliminación
    // ════════════════════════════════════════════════════════════════════════

    private void tryRemoveBlock(DeathChest chest, boolean dropItems) {
        Location loc = chest.getLocation();
        if (loc == null) return;

        Block block = loc.getBlock();
        if (block.getType() != Material.CHEST) return;

        if (dropItems) {
            Chest state = (Chest) block.getState();
            for (ItemStack item : state.getInventory().getContents()) {
                if (item != null && item.getType() != Material.AIR) {
                    loc.getWorld().dropItemNaturally(loc, item);
                }
            }
            state.getInventory().clear();
        }

        block.setType(Material.AIR, false);
    }

    private void removeHologram(DeathChest chest) {
        TextDisplay td = chest.getHologram();
        if (td != null && td.isValid()) {
            td.remove();
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Hologramas (TextDisplay — nativo de 1.19.4+)
    // ════════════════════════════════════════════════════════════════════════

    private void spawnHologram(DeathChest chest) {
        Location loc = chest.getLocation();
        if (loc == null || loc.getWorld() == null) return;

        // Centrado encima del cofre
        Location holoLoc = loc.clone().add(0.5, 1.35, 0.5);

        TextDisplay td = loc.getWorld().spawn(holoLoc, TextDisplay.class, display -> {
            display.text(buildHologramText(chest));
            display.setGravity(false);
            display.setInvulnerable(true);
            display.setPersistent(true);
            display.setBillboard(Display.Billboard.CENTER); // Siempre mira al jugador
            display.setDefaultBackground(false);
            display.setShadowed(true);
            display.setViewRange(24f);
            // Tag PDC para identificar que pertenece a un cofre de muerte
            display.getPersistentDataContainer().set(
                    PDC_KEY, PersistentDataType.STRING, chest.getOwnerUUID().toString());
        });

        chest.setHologram(td);
        chest.setHologramEntityId(td.getUniqueId());
    }

    /** Actualiza el texto del holograma de un cofre. */
    private void updateHologram(DeathChest chest) {
        TextDisplay td = chest.getHologram();

        // Si la referencia es nula o inválida (e.g. chunk recargado),
        // intentar recuperar por UUID de entidad
        if (td == null || !td.isValid()) {
            if (chest.getHologramEntityId() != null) {
                org.bukkit.entity.Entity e =
                        Bukkit.getEntity(chest.getHologramEntityId());
                if (e instanceof TextDisplay recovered) {
                    chest.setHologram(recovered);
                    td = recovered;
                } else {
                    // No recuperable — crear nuevo holograma
                    spawnHologram(chest);
                    return;
                }
            } else {
                spawnHologram(chest);
                return;
            }
        }

        td.text(buildHologramText(chest));
    }

    private Component buildHologramText(DeathChest chest) {
        return Component.empty()
                .append(Component.text("☠ ", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text(chest.getOwnerName(), NamedTextColor.YELLOW))
                .append(Component.text("\n"))
                .append(Component.text("⏳ ", NamedTextColor.GRAY))
                .append(Component.text(chest.getRemainingFormatted(), NamedTextColor.WHITE));
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Tarea de tick (cada segundo)
    // ════════════════════════════════════════════════════════════════════════

    private void startTickTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                List<DeathChest> toExpire = new ArrayList<>();

                for (DeathChest chest : byOwner.values()) {
                    if (chest.isExpired()) {
                        toExpire.add(chest);
                    } else {
                        updateHologram(chest);
                    }
                }

                for (DeathChest chest : toExpire) {
                    forceRemoveChest(chest, true); // Soltar ítems al expirar

                    // Avisar al jugador si está online
                    Player p = Bukkit.getPlayer(chest.getOwnerUUID());
                    if (p != null) {
                        p.sendMessage(Component.empty()
                                .append(Component.text("☠ ", NamedTextColor.RED, TextDecoration.BOLD))
                                .append(Component.text(
                                        "Tu cofre de muerte expiró. Los objetos fueron soltados en " +
                                        "X:" + chest.getX() + " Y:" + chest.getY() + " Z:" + chest.getZ(),
                                        NamedTextColor.YELLOW)));
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // cada 1 segundo
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Búsqueda
    // ════════════════════════════════════════════════════════════════════════

    /** Devuelve el cofre activo del jugador, o null. */
    public DeathChest getByOwner(UUID ownerUUID) {
        return byOwner.get(ownerUUID);
    }

    /** Devuelve el cofre asociado a esa ubicación de bloque, o null. */
    public DeathChest getByLocation(Location loc) {
        UUID owner = byLocation.get(locKey(loc));
        return owner != null ? byOwner.get(owner) : null;
    }

    /** True si la Location corresponde a un cofre de muerte activo. */
    public boolean isDeathChest(Location loc) {
        return byLocation.containsKey(locKey(loc));
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Ubicación de colocación del cofre
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Busca el bloque más cercano donde se puede colocar el cofre.
     * Prioriza la posición exacta de muerte, luego bloques adyacentes.
     */
    private Location findPlacementLocation(Location death) {
        World world = death.getWorld();
        int baseX = death.getBlockX();
        int baseY = death.getBlockY();
        int baseZ = death.getBlockZ();

        // Primero probar en la posición exacta y uno arriba
        for (int dy = 0; dy <= 1; dy++) {
            Location candidate = new Location(world, baseX, baseY + dy, baseZ);
            if (isValidPlacement(candidate)) return candidate;
        }

        // Luego probar el radio inmediato
        int[] deltas = {-1, 0, 1};
        for (int dx : deltas) {
            for (int dz : deltas) {
                if (dx == 0 && dz == 0) continue;
                for (int dy = -1; dy <= 2; dy++) {
                    Location candidate = new Location(world, baseX + dx, baseY + dy, baseZ + dz);
                    if (isValidPlacement(candidate)) return candidate;
                }
            }
        }

        return null; // No hay sitio libre
    }

    private boolean isValidPlacement(Location loc) {
        int y = loc.getBlockY();
        World world = loc.getWorld();
        if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 1) return false;

        Material type = loc.getBlock().getType();
        return type == Material.AIR
                || type == Material.CAVE_AIR
                || type == Material.VOID_AIR
                || type == Material.WATER
                || type == Material.LAVA
                || !type.isSolid();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Utilidades
    // ════════════════════════════════════════════════════════════════════════

    private String locKey(Location loc) {
        return locKey(loc.getWorld().getName(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    private String locKey(String world, int x, int y, int z) {
        return world + "," + x + "," + y + "," + z;
    }

    private long getExpiryMillis() {
        return plugin.getConfig().getLong("death-chest.expiry-minutes", 10) * 60_000L;
    }

    /** Llamar en onDisable() para cerrar la conexión SQLite correctamente. */
    public void shutdown() {
        // Eliminar todos los hologramas de memoria (no del mundo, persisten en el chunk)
        for (DeathChest chest : byOwner.values()) {
            // Los hologramas son entidades persistentes, no hace falta eliminarlos.
            // Solo cerrar la conexión DB.
        }
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("[DeathChest] Conexión SQLite cerrada.");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[DeathChest] Error al cerrar SQLite", e);
        }
    }
}
