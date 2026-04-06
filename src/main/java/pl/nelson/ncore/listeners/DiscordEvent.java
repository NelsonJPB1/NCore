package pl.nelson.ncore.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public class DiscordEvent implements Listener {

    @EventHandler
    public void onCommandDiscord(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage().toLowerCase();

        if (!message.equals("/discord")) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        player.sendMessage("§6ARZONE §r» §eDiscord: §bhttps://discord.gg/arzone");
    }
}
