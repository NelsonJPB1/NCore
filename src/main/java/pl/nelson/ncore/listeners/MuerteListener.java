package pl.nelson.ncore.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import pl.nelson.ncore.Main;
import pl.nelson.ncore.deathchest.DeathChest;
import pl.nelson.ncore.deathchest.DeathChestManager;

import java.util.ArrayList;
import java.util.List;

public class MuerteListener implements Listener {

    private final Main plugin;
    private final DeathChestManager manager;

    public MuerteListener(Main plugin, DeathChestManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Muerte del jugador → crear cofre
    // ════════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // Si keepInventory está activo, no crear cofre
        if (event.getKeepInventory()) return;

        // Capturar los ítems ANTES de que el evento los suelte
        List<ItemStack> drops = new ArrayList<>(event.getDrops());
        if (drops.isEmpty()) return;

        // Vaciar los drops del evento (el cofre los custodiará)
        event.getDrops().clear();

        // Crear el cofre de muerte (async no — necesitamos acceder al mundo en el main thread)
        manager.createChest(player, player.getLocation(), drops);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Interacción con el cofre → solo el dueño puede abrirlo
    // ════════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Solo clicks en bloques con la mano principal
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getClickedBlock() == null) return;
        if (event.getClickedBlock().getType() != Material.CHEST) return;

        Block block = event.getClickedBlock();
        DeathChest dc = manager.getByLocation(block.getLocation());

        if (dc == null) return; // No es un cofre de muerte

        Player player = event.getPlayer();

        // El dueño puede abrirlo libremente
        if (player.getUniqueId().equals(dc.getOwnerUUID())) return;

        // Admins con permiso ncore.admin también pueden (para soporte)
        if (player.hasPermission("ncore.admin")) {
            player.sendMessage(Component.text("[Admin] Cofre de muerte de " + dc.getOwnerName(),
                    NamedTextColor.GRAY));
            return;
        }

        // Bloquear a otros jugadores
        event.setCancelled(true);
        player.sendMessage(Component.text("⚠ Ese cofre pertenece a ", NamedTextColor.RED)
                .append(Component.text(dc.getOwnerName(), NamedTextColor.YELLOW))
                .append(Component.text(".", NamedTextColor.RED)));
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Rotura del cofre → proteger / permitir al dueño
    // ════════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.CHEST) return;

        DeathChest dc = manager.getByLocation(block.getLocation());
        if (dc == null) return;

        Player player = event.getPlayer();

        // Solo el dueño (o admin) puede romperlo
        if (!player.getUniqueId().equals(dc.getOwnerUUID())
                && !player.hasPermission("ncore.admin")) {
            event.setCancelled(true);
            player.sendMessage(Component.text("⚠ No puedes romper el cofre de ",
                    NamedTextColor.RED)
                    .append(Component.text(dc.getOwnerName(), NamedTextColor.YELLOW))
                    .append(Component.text(".", NamedTextColor.RED)));
            return;
        }

        // El dueño lo rompe manualmente: soltar ítems y eliminar cofre limpiamente
        // Cancelamos la rotura vanilla para controlar el drop nosotros
        event.setCancelled(true);

        Chest chestState = (Chest) block.getState();
        Inventory inv = chestState.getInventory();
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                block.getWorld().dropItemNaturally(block.getLocation(), item);
            }
        }
        inv.clear();

        manager.forceRemoveChest(dc, false); // ya soltamos los ítems arriba
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Cerrar el inventario → si está vacío, eliminar cofre automáticamente
    // ════════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        // Buscar si el jugador tiene un cofre de muerte activo
        DeathChest dc = manager.getByOwner(player.getUniqueId());
        if (dc == null) return;

        // Comprobar que el inventario cerrado ES el cofre de muerte del jugador
        Block block = player.getWorld().getBlockAt(dc.getX(), dc.getY(), dc.getZ());
        if (block.getType() != Material.CHEST) return;

        Chest chestState = (Chest) block.getState();
        if (!event.getInventory().equals(chestState.getInventory())) return;

        // Si el cofre quedó vacío, eliminarlo silenciosamente
        boolean empty = true;
        for (ItemStack item : chestState.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                empty = false;
                break;
            }
        }

        if (empty) {
            manager.onChestEmptied(dc);
            player.sendMessage(Component.text("✔ Cofre de muerte recogido.", NamedTextColor.GREEN));
        }
    }
}
