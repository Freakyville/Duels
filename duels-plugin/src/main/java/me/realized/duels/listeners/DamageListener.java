package me.realized.duels.listeners;

import me.realized.duels.DuelsPlugin;
import me.realized.duels.arena.Arena;
import me.realized.duels.arena.ArenaManager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.Set;
import java.util.UUID;

public class DamageListener implements Listener {

    private final ArenaManager arenaManager;

    public DamageListener(final DuelsPlugin plugin) {
        this.arenaManager = plugin.getArenaManager();

        if (plugin.getConfiguration().isForceAllowCombat()) {
            plugin.doSyncAfter(() -> plugin.getServer().getPluginManager().registerEvents(this, plugin), 1L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(final EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player) {
            final Player player = (Player) event.getEntity();
            final Player damager = getDamager(event);
            if (damager == null)
                return;
            Arena playerArena = arenaManager.get(player);
            Arena damagerArena = arenaManager.get(damager);
            if (playerArena != null && damagerArena != null) {
                if (!playerArena.getName().equals(damagerArena.getName()))
                    return;
                Set<UUID> allyTeam = playerArena.getMatch().getSettings().getAllyTeam();
                Set<UUID> targetTeam = damagerArena.getMatch().getSettings().getTargetTeam();
                if (allyTeam.contains(player.getUniqueId())) {
                    if (allyTeam.contains(damager.getUniqueId())) {
                        event.setCancelled(true);
                        return;
                    }
                } else {
                    if (targetTeam.contains(damager.getUniqueId())) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }

            if (!event.isCancelled()) {
                return;
            }


            if (!(arenaManager.isInMatch(player) && arenaManager.isInMatch(damager))) {
                return;
            }

            event.setCancelled(false);
        }
    }


    private Player getDamager(EntityDamageByEntityEvent event) {
        final Player damager;
        if (event.getDamager() instanceof Player) {
            damager = (Player) event.getDamager();
        } else if (event.getDamager() instanceof Projectile && ((Projectile) event.getDamager()).getShooter() instanceof Player) {
            damager = (Player) ((Projectile) event.getDamager()).getShooter();
        } else {
            return null;
        }
        return damager;
    }
}
