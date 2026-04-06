package pl.nelson.ncore.deathchest;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.TextDisplay;

import java.util.UUID;

/**
 * Representa un cofre de muerte activo en el mundo.
 */
public class DeathChest {

    private final UUID id;           // ID único del cofre
    private final UUID ownerUUID;    // UUID del jugador dueño
    private final String ownerName;  // Nombre del jugador (para el holograma)
    private final String worldName;
    private final int x, y, z;
    private final long expiryTime;   // System.currentTimeMillis() de expiración

    // No persistido — se recrea en memoria al cargar
    private TextDisplay hologram;
    private UUID hologramEntityId;   // Para recuperar el ArmorStand si el chunk se recarga

    // Constructor para cofres nuevos
    public DeathChest(UUID id, UUID ownerUUID, String ownerName, Location location, long expiryTime) {
        this.id = id;
        this.ownerUUID = ownerUUID;
        this.ownerName = ownerName;
        this.worldName = location.getWorld().getName();
        this.x = location.getBlockX();
        this.y = location.getBlockY();
        this.z = location.getBlockZ();
        this.expiryTime = expiryTime;
    }

    // Constructor para cargar desde SQLite
    public DeathChest(UUID id, UUID ownerUUID, String ownerName,
                      String worldName, int x, int y, int z, long expiryTime) {
        this.id = id;
        this.ownerUUID = ownerUUID;
        this.ownerName = ownerName;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.expiryTime = expiryTime;
    }

    // ── Getters ─────────────────────────────────────────────────────────────

    public UUID getId()          { return id; }
    public UUID getOwnerUUID()   { return ownerUUID; }
    public String getOwnerName() { return ownerName; }
    public String getWorldName() { return worldName; }
    public int getX()            { return x; }
    public int getY()            { return y; }
    public int getZ()            { return z; }
    public long getExpiryTime()  { return expiryTime; }

    /** Devuelve la Location del cofre, o null si el mundo no está cargado. */
    public Location getLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, x, y, z);
    }

    /** Location del cofre (centro del bloque), usada para TP. */
    public Location getTeleportLocation() {
        Location loc = getLocation();
        if (loc == null) return null;
        // Encima del cofre + centrado en el bloque
        return new Location(loc.getWorld(), x + 0.5, y + 1, z + 0.5, 0f, 0f);
    }

    // ── Estado ──────────────────────────────────────────────────────────────

    public boolean isExpired() {
        return System.currentTimeMillis() >= expiryTime;
    }

    /** Milisegundos restantes antes de expirar (nunca negativo). */
    public long getRemainingMillis() {
        return Math.max(0L, expiryTime - System.currentTimeMillis());
    }

    /** Formato "MM:SS" del tiempo restante. */
    public String getRemainingFormatted() {
        long secs = getRemainingMillis() / 1000;
        return String.format("%02d:%02d", secs / 60, secs % 60);
    }

    // ── Holograma ───────────────────────────────────────────────────────────

    public TextDisplay getHologram()          { return hologram; }
    public void setHologram(TextDisplay h)    { this.hologram = h; }

    public UUID getHologramEntityId()         { return hologramEntityId; }
    public void setHologramEntityId(UUID uid) { this.hologramEntityId = uid; }
}
