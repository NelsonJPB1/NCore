package pl.nelson.ncore.comandos;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import pl.nelson.ncore.Main;

public class ComandoSpawn implements CommandExecutor {

    private final Main plugin;

    public ComandoSpawn(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo los jugadores pueden usar este comando.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("setspawn")) {
            Location loc = player.getLocation();
            plugin.getConfig().set("spawn.world", loc.getWorld().getName());
            plugin.getConfig().set("spawn.x", loc.getX());
            plugin.getConfig().set("spawn.y", loc.getY());
            plugin.getConfig().set("spawn.z", loc.getZ());
            plugin.getConfig().set("spawn.yaw", (double) loc.getYaw());
            plugin.getConfig().set("spawn.pitch", (double) loc.getPitch());
            plugin.saveConfig();
            player.sendMessage("§6ARZONE: §aPunto de spawn establecido correctamente!");
            return true;
        }

        // Comando /spawn — teleportar al spawn
        String worldName = plugin.getConfig().getString("spawn.world", "world");
        double x = plugin.getConfig().getDouble("spawn.x", 0);
        double y = plugin.getConfig().getDouble("spawn.y", 64);
        double z = plugin.getConfig().getDouble("spawn.z", 0);
        float yaw   = (float) plugin.getConfig().getDouble("spawn.yaw", 0);
        float pitch = (float) plugin.getConfig().getDouble("spawn.pitch", 0);

        org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
        if (world == null) {
            player.sendMessage("§cEl mundo del spawn no está disponible.");
            return true;
        }

        player.teleport(new Location(world, x, y, z, yaw, pitch));
        player.sendMessage("§6ARZONE: §eTeleportado al spawn.");
        return true;
    }
}
