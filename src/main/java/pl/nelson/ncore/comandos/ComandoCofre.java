package pl.nelson.ncore.comandos;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import pl.nelson.ncore.Main;
import pl.nelson.ncore.deathchest.DeathChest;

public class ComandoCofre implements CommandExecutor {

    private final Main plugin;

    public ComandoCofre(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Solo los jugadores pueden usar este comando.",
                    NamedTextColor.RED));
            return true;
        }

        DeathChest chest = plugin.getDeathChestManager().getByOwner(player.getUniqueId());

        if (chest == null) {
            player.sendMessage(Component.text("⚰ No tienes ningún cofre de muerte activo.",
                    NamedTextColor.GRAY));
            return true;
        }

        Location tpLoc = chest.getTeleportLocation();
        if (tpLoc == null) {
            player.sendMessage(Component.text("⚠ El mundo de tu cofre no está cargado.",
                    NamedTextColor.RED));
            return true;
        }

        // Teletransportar
        player.teleport(tpLoc);
        player.playSound(tpLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);

        player.sendMessage(Component.text("⚰ Teletransportado a tu cofre de muerte · ",
                        NamedTextColor.YELLOW)
                .append(Component.text("⏳ " + chest.getRemainingFormatted() + " restantes",
                        NamedTextColor.GRAY)));

        return true;
    }
}
