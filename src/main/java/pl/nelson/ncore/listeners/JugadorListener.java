package pl.nelson.ncore.listeners;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import pl.nelson.ncore.Main;

public class JugadorListener implements Listener {

    private final Main plugin;

    public JugadorListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void alCaerAlVacio(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (player.getLocation().getY() < player.getWorld().getMinHeight()) {
            teletransportarAlSpawn(player);
        }
    }

    private void teletransportarAlSpawn(Player player) {
        FileConfiguration config = plugin.getConfig();

        String worldName = config.getString("spawn.world", "world");
        double x = config.getDouble("spawn.x", 0);
        double y = config.getDouble("spawn.y", 64);
        double z = config.getDouble("spawn.z", 0);
        float yaw = (float) config.getDouble("spawn.yaw", 0);
        float pitch = (float) config.getDouble("spawn.pitch", 0);

        org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
        if (world == null) return;

        Location spawn = new Location(world, x, y, z, yaw, pitch);
        player.teleport(spawn);
        player.playSound(spawn, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
        player.sendMessage("§6ARZONE §r» §eFuiste teleportado al spawn.");
    }
}
