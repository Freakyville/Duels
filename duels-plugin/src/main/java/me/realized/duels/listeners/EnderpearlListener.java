package me.realized.duels.listeners;

import me.realized.duels.DuelsPlugin;
import me.realized.duels.arena.ArenaManager;
import me.realized.duels.config.Lang;
import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class EnderpearlListener implements Listener {
    private final ArenaManager arenaManager;
    private final Lang lang;
    private final DuelsPlugin plugin;
    private Set<UUID> recentlyThrew = new HashSet<>();

    public EnderpearlListener(DuelsPlugin plugin) {
        this.arenaManager = plugin.getArenaManager();
        this.lang = plugin.getLang();
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onEnderPearlThrow(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player)) {
            return;
        }
        if (!(event.getEntity().getType() == EntityType.ENDER_PEARL)) {
            return;
        }
        Player player = (Player) event.getEntity().getShooter();
        if (recentlyThrew.contains(player.getUniqueId())) {
            lang.sendMessage(player, "DUEL.prevent.enderpearl-spam");
            event.setCancelled(true);
            return;
        }
        recentlyThrew.add(player.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            recentlyThrew.remove(player.getUniqueId());
        }, 30);
    }
}
