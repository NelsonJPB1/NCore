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

import java.util.HashMap;
import java.util.Random;
import java.util.UUID;

public class ComandoRTP implements CommandExecutor {

    private static final long COOLDOWN_SEGUNDOS = 300L; // 5 minutos
    private final Random random = new Random();
    private final HashMap<UUID, Long> cooldowns = new HashMap<>();
    private final Main plugin;

    public ComandoRTP(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo los jugadores pueden usar este comando.");
            return true;
        }

        UUID uuid = player.getUniqueId();
        long ahora = System.currentTimeMillis();

        // Saltarse cooldown si tiene permiso admin
        if (!player.hasPermission("ncore.admin")) {
            if (cooldowns.containsKey(uuid)) {
                long restante = (COOLDOWN_SEGUNDOS * 1000L) - (ahora - cooldowns.get(uuid));
                if (restante > 0) {
                    long segs = restante / 1000;
                    player.sendMessage("§6ARZONE: §eDebes esperar §c" + segs + "s §epara usar /rtp.");
                    return true;
                }
            }
        }

        cooldowns.put(uuid, ahora);

        World world = Bukkit.getWorld("world");
        if (world == null) {
            player.sendMessage("§cNo se encontró el mundo principal.");
            return true;
        }

        // Buscar una ubicación aleatoria segura
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Location loc = encontrarUbicacionSegura(world);
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.teleport(loc);
                player.sendMessage("§6ARZONE: §eTeleportado a una ubicación aleatoria.");
            });
        });

        return true;
    }

    private Location encontrarUbicacionSegura(World world) {
        int intentos = 0;
        while (intentos < 20) {
            int x = random.nextInt(10000) - 5000;
            int z = random.nextInt(10000) - 5000;
            int y = world.getHighestBlockYAt(x, z) + 1;

            Location loc = new Location(world, x + 0.5, y, z + 0.5);
            if (loc.getBlock().getType().isAir() &&
                    loc.clone().add(0, -1, 0).getBlock().getType().isSolid()) {
                return loc;
            }
            intentos++;
        }
        // Fallback al centro del mundo
        return new Location(world, 0.5, world.getHighestBlockYAt(0, 0) + 1, 0.5);
    }
}
