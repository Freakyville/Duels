package me.realized.duels.listeners;

import me.realized.duels.DuelsPlugin;
import me.realized.duels.config.Lang;
import me.realized.duels.request.RequestManager;
import me.realized.duels.request.TeamRequest;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Set;

public class SessionListener implements Listener {
    private final RequestManager requestManager;
    private final Lang lang;

    public SessionListener(DuelsPlugin plugin, RequestManager requestManager, Lang lang) {
        this.requestManager = requestManager;
        this.lang = lang;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Set<TeamRequest> requests = requestManager.getPartOfTeamRequest(event.getPlayer());
        if (requests.size() > 0) {
            requests.forEach(request -> {
                request.getAccepted().forEach(requester -> {
                    Player player = Bukkit.getPlayer(requester);
                    if (player != null) {
                        lang.sendMessage(player, "COMMAND.duel.request.team.logged-off", "name", event.getPlayer().getName());
                    }

                });
            });
        }
        requestManager.removeTeamRequest(event.getPlayer());
    }
}
