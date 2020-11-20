package me.realized.duels.request;

import java.util.*;
import java.util.stream.Collectors;

import com.massivecraft.massivecore.xlib.mongodb.util.Hash;
import me.realized.duels.DuelsPlugin;
import me.realized.duels.api.event.request.RequestSendEvent;
import me.realized.duels.config.Config;
import me.realized.duels.config.Lang;
import me.realized.duels.setting.Settings;
import me.realized.duels.util.Loadable;
import me.realized.duels.util.TeamUtil;
import me.realized.duels.util.TextBuilder;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent.Action;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class RequestManager implements Loadable, Listener {

    private final DuelsPlugin plugin;
    private final Config config;
    private final Lang lang;
    private final Map<UUID, Map<UUID, Request>> requests = new HashMap<>();
    private final Map<UUID, Map<UUID, TeamRequest>> teamRequests = new HashMap<>();

    public RequestManager(final DuelsPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfiguration();
        this.lang = plugin.getLang();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void handleLoad() {}

    @Override
    public void handleUnload() {
        requests.clear();
    }

    private Map<UUID, TeamRequest> getTeamRequest(Player player, boolean create) {
        Map<UUID, TeamRequest> cached = teamRequests.get(player.getUniqueId());
        if (cached == null && create) {
            teamRequests.put(player.getUniqueId(), cached = new HashMap<>());
            return cached;
        }

        return cached;
    }

    private Map<UUID, Request> get(final Player player, final boolean create) {
        Map<UUID, Request> cached = requests.get(player.getUniqueId());

        if (cached == null && create) {
            requests.put(player.getUniqueId(), cached = new HashMap<>());
            return cached;
        }

        return cached;
    }

    public void send(Player sender, Set<UUID> team1, Set<UUID> team2, Settings settings) {
        TeamRequest teamRequest = new TeamRequest(team1, team2, settings);
        teamRequest.addAccepted(sender.getUniqueId());
        Set<UUID> combined = new HashSet<>(team1);
        combined.addAll(team2);
        combined.remove(sender.getUniqueId());
        combined.forEach(uuid -> {
            getTeamRequest(sender, true).put(uuid, teamRequest);
        });
        Set<Player> combinedPlayers = combined.stream().map(Bukkit::getPlayer).collect(Collectors.toSet());
        String kit = settings.getKit() != null ? settings.getKit().getName() : lang.getMessage("GENERAL.not-selected");
        String arena = settings.getArena() != null ? settings.getArena().getName() : lang.getMessage("GENERAL.random");
        int betAmount = settings.getBet();
        final String itemBetting = settings.isItemBetting() ? lang.getMessage("GENERAL.enabled") : lang.getMessage("GENERAL.disabled");
        Set<Player> alliesWithSender = team1.stream().map(Bukkit::getPlayer).collect(Collectors.toSet());
        Set<Player> allies = alliesWithSender.stream().filter(i -> !i.getUniqueId().equals(sender.getUniqueId())).collect(Collectors.toSet());

        Set<Player> enemies = team2.stream().map(Bukkit::getPlayer).collect(Collectors.toSet());
        lang.sendMessage(sender, "COMMAND.duel.request.send.team.sender",
                "allies", TeamUtil.getTeamStringByPlayers(allies),
                "targets", TeamUtil.getTeamStringByPlayers(enemies),
                "kit", kit,
                "arena", arena,
                "bet_amount", betAmount,
                "item_betting", itemBetting);

        allies.forEach(player -> {
            lang.sendMessage(player, "COMMAND.duel.request.send.team.receiver.ally",
                    "name", sender.getName(),
                    "allies", TeamUtil.getTeamStringByPlayers(alliesWithSender),
                    "targets", TeamUtil.getTeamStringByPlayers(enemies),
                    "kit", kit,
                    "arena", arena,
                    "bet_amount", betAmount,
                    "item_betting", itemBetting);
        });

        enemies.forEach(player -> {
            lang.sendMessage(player, "COMMAND.duel.request.send.team.receiver.target",
                    "name", sender.getName(),
                    "allies", TeamUtil.getTeamStringByPlayers(enemies),
                    "targets", TeamUtil.getTeamStringByPlayers(alliesWithSender),
                    "kit", kit,
                    "arena", arena,
                    "bet_amount", betAmount,
                    "item_betting", itemBetting);
        });
        final String path = "COMMAND.duel.request.send.clickable-text.";
        TextBuilder text = TextBuilder
                .of(lang.getMessage(path + "info.text"), null, null, Action.SHOW_TEXT, lang.getMessage(path + "info.hover-text"))
                .add(lang.getMessage(path + "accept.text"),
                        ClickEvent.Action.RUN_COMMAND, "/teamduel accept " + sender.getName(),
                        Action.SHOW_TEXT, lang.getMessage(path + "accept.hover-text"))
                .add(lang.getMessage(path + "deny.text"),
                        ClickEvent.Action.RUN_COMMAND, "/teamduel deny " + sender.getName(),
                        Action.SHOW_TEXT, lang.getMessage(path + "deny.hover-text"));
        combinedPlayers.forEach(text::send);
        sender.closeInventory();
    }

    public void send(final Player sender, final Player target, final Settings settings) {
        final Request request = new Request(sender, target, settings);
        final RequestSendEvent event = new RequestSendEvent(sender, target, request);
        plugin.getServer().getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return;
        }

        get(sender, true).put(target.getUniqueId(), request);
        final String kit = settings.getKit() != null ? settings.getKit().getName() : lang.getMessage("GENERAL.not-selected");
        final String arena = settings.getArena() != null ? settings.getArena().getName() : lang.getMessage("GENERAL.random");
        final int betAmount = settings.getBet();
        final String itemBetting = settings.isItemBetting() ? lang.getMessage("GENERAL.enabled") : lang.getMessage("GENERAL.disabled");

        lang.sendMessage(sender, "COMMAND.duel.request.send.sender",
            "name", target.getName(), "kit", kit, "arena", arena, "bet_amount", betAmount, "item_betting", itemBetting);
        lang.sendMessage(target, "COMMAND.duel.request.send.receiver",
            "name", sender.getName(), "kit", kit, "arena", arena, "bet_amount", betAmount, "item_betting", itemBetting);

        final String path = "COMMAND.duel.request.send.clickable-text.";

        TextBuilder
            .of(lang.getMessage(path + "info.text"), null, null, Action.SHOW_TEXT, lang.getMessage(path + "info.hover-text"))
            .add(lang.getMessage(path + "accept.text"),
                ClickEvent.Action.RUN_COMMAND, "/duel accept " + sender.getName(),
                Action.SHOW_TEXT, lang.getMessage(path + "accept.hover-text"))
            .add(lang.getMessage(path + "deny.text"),
                ClickEvent.Action.RUN_COMMAND, "/duel deny " + sender.getName(),
                Action.SHOW_TEXT, lang.getMessage(path + "deny.hover-text"))
            .send(target);
        TextBuilder.of(lang.getMessage(path + "extra.text"), null, null, Action.SHOW_TEXT, lang.getMessage(path + "extra.hover-text")).send(target);
    }


    public Set<TeamRequest> getPartOfTeamRequest(Player player) {
        Set<TeamRequest> returnMap = new HashSet<>();
        if (teamRequests.containsKey(player.getUniqueId()))
            returnMap.addAll(teamRequests.get(player.getUniqueId()).values());
        for (Map<UUID, TeamRequest> innerMap : teamRequests.values()) {
            if (innerMap.containsKey(player.getUniqueId()))
                returnMap.add(innerMap.get(player.getUniqueId()));
        }
        return returnMap;
    }

    public TeamRequest getTeamRequest(Player sender, Player target) {
        final Map<UUID, TeamRequest> cached = getTeamRequest(sender, false);
        if (cached == null)
            return null;
        TeamRequest request = cached.get(target.getUniqueId());
        if (request == null) {
            return null;
        }

        if (System.currentTimeMillis() - request.getLastUpdated() >= config.getExpiration() * 1000L) {
            cached.remove(target.getUniqueId());
            return null;
        }

        return request;
    }

    public Request get(final Player sender, final Player target) {
        final Map<UUID, Request> cached = get(sender, false);

        if (cached == null) {
            return null;
        }

        final Request request = cached.get(target.getUniqueId());

        if (request == null) {
            return null;
        }

        if (System.currentTimeMillis() - request.getCreation() >= config.getExpiration() * 1000L) {
            cached.remove(target.getUniqueId());
            return null;
        }

        return request;
    }

    public boolean has(final Player sender, final Player target) {
        return get(sender, target) != null;
    }

    public void removeTeamRequest(Player player) {
        teamRequests.remove(player.getUniqueId());
        for (Map<UUID, TeamRequest> value : teamRequests.values()) {
            value.remove(player.getUniqueId());
        }
    }
    public TeamRequest removeTeamRequest(Player sender, Player target) {
        final Map<UUID, TeamRequest> cached = getTeamRequest(sender, false);

        if (cached == null) {
            return null;
        }

        final TeamRequest request = cached.remove(target.getUniqueId());

        if (request == null) {
            return null;
        }

        if (System.currentTimeMillis() - request.getLastUpdated() >= config.getExpiration() * 1000L) {
            cached.remove(target.getUniqueId());
            return null;
        }

        return request;
    }

    public Request remove(final Player sender, final Player target) {
        final Map<UUID, Request> cached = get(sender, false);

        if (cached == null) {
            return null;
        }

        final Request request = cached.remove(target.getUniqueId());

        if (request == null) {
            return null;
        }

        if (System.currentTimeMillis() - request.getCreation() >= config.getExpiration() * 1000L) {
            cached.remove(target.getUniqueId());
            return null;
        }

        return request;
    }



    @EventHandler
    public void on(final PlayerQuitEvent event) {
        teamRequests.remove(event.getPlayer().getUniqueId());
        requests.remove(event.getPlayer().getUniqueId());
    }
}
