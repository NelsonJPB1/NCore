package pl.nelson.ncore.comandos;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import pl.nelson.ncore.Main;

public class ComandoRegresar implements CommandExecutor {

    private final Main plugin;

    public ComandoRegresar(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo los jugadores pueden usar este comando.");
            return true;
        }

        String uuid = player.getUniqueId().toString();
        String path = "ultimas_posiciones." + uuid;

        if (!plugin.getConfig().contains(path)) {
            player.sendMessage("§6ARZONE: §eNo tienes una posición guardada.");
            return true;
        }

        String worldName = plugin.getConfig().getString(path + ".world", "world");
        double x = plugin.getConfig().getDouble(path + ".x");
        double y = plugin.getConfig().getDouble(path + ".y");
        double z = plugin.getConfig().getDouble(path + ".z");
        float yaw   = (float) plugin.getConfig().getDouble(path + ".yaw");
        float pitch = (float) plugin.getConfig().getDouble(path + ".pitch");

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            player.sendMessage("§cEl mundo guardado no está disponible.");
            return true;
        }

        player.teleport(new Location(world, x, y, z, yaw, pitch));
        player.sendMessage("§6ARZONE: §eTeleportado a tu última posición.");
        return true;
    }
}
