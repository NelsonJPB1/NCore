package pl.nelson.ncore.listeners;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import pl.nelson.ncore.Main;

public class VidaListener implements Listener {

    private final Main plugin;

    public VidaListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void alEntrar(PlayerJoinEvent event) {
        setMaxHealth(event.getPlayer());
    }

    @EventHandler
    public void alRevivir(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> setMaxHealth(player), 1L);
    }

    @EventHandler(ignoreCancelled = true)
    public void alRecibirDano(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        // Si el daño mataría al jugador, dejar que el juego lo maneje normalmente
        if (player.getHealth() - event.getFinalDamage() <= 0) return;
    }

    private void setMaxHealth(Player player) {
        var attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(20.0);
            player.setHealth(Math.min(player.getHealth(), 20.0));
        }
    }
}
