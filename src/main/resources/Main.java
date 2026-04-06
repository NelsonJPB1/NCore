package pl.nelson.ncore.listeners;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import pl.nelson.ncore.Main;

public class SalidaListener implements Listener {

    private final Main plugin;

    public SalidaListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void alSalir(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Location loc = player.getLocation();
        String path = "ultimas_posiciones." + player.getUniqueId();

        plugin.getDataConfig().set(path + ".world", loc.getWorld().getName());
        plugin.getDataConfig().set(path + ".x",     loc.getX());
        plugin.getDataConfig().set(path + ".y",     loc.getY());
        plugin.getDataConfig().set(path + ".z",     loc.getZ());
        plugin.getDataConfig().set(path + ".yaw",   (double) loc.getYaw());
        plugin.getDataConfig().set(path + ".pitch", (double) loc.getPitch());
        plugin.saveDataConfig();
    }
}
