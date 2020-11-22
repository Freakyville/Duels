package me.realized.duels.duel;

import java.util.*;
import java.util.stream.Collectors;

import com.massivecraft.massivecore.xlib.mongodb.util.Hash;
import lombok.Getter;
import me.realized.duels.DuelsPlugin;
import me.realized.duels.api.event.match.MatchEndEvent.Reason;
import me.realized.duels.api.event.match.MatchStartEvent;
import me.realized.duels.arena.Arena;
import me.realized.duels.arena.ArenaManager;
import me.realized.duels.arena.Match;
import me.realized.duels.config.Config;
import me.realized.duels.config.Lang;
import me.realized.duels.data.MatchData;
import me.realized.duels.data.UserData;
import me.realized.duels.data.UserManager;
import me.realized.duels.hook.hooks.CombatLogXHook;
import me.realized.duels.hook.hooks.CombatTagPlusHook;
import me.realized.duels.hook.hooks.EssentialsHook;
import me.realized.duels.hook.hooks.McMMOHook;
import me.realized.duels.hook.hooks.MyPetHook;
import me.realized.duels.hook.hooks.PvPManagerHook;
import me.realized.duels.hook.hooks.SimpleClansHook;
import me.realized.duels.hook.hooks.VaultHook;
import me.realized.duels.hook.hooks.worldguard.WorldGuardHook;
import me.realized.duels.inventories.InventoryManager;
import me.realized.duels.kit.Kit;
import me.realized.duels.player.PlayerInfo;
import me.realized.duels.player.PlayerInfoManager;
import me.realized.duels.queue.Queue;
import me.realized.duels.queue.QueueManager;
import me.realized.duels.setting.Settings;
import me.realized.duels.spectate.SpectateManager;
import me.realized.duels.util.*;
import me.realized.duels.util.compat.CompatUtil;
import me.realized.duels.util.compat.Players;
import me.realized.duels.util.compat.Titles;
import net.md_5.bungee.api.chat.ClickEvent.Action;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;

public class DuelManager implements Loadable {

    private final DuelsPlugin plugin;
    private final Config config;
    private final Lang lang;
    private final UserManager userDataManager;
    private final ArenaManager arenaManager;
    private final PlayerInfoManager playerManager;
    private final InventoryManager inventoryManager;
    private final SpectateManager spectateManager;

    private QueueManager queueManager;
    private Teleport teleport;
    private CombatTagPlusHook combatTagPlus;
    private PvPManagerHook pvpManager;
    private CombatLogXHook combatLogX;
    private VaultHook vault;
    private EssentialsHook essentials;
    private McMMOHook mcMMO;
    private WorldGuardHook worldGuard;
    private MyPetHook myPet;
    private SimpleClansHook simpleClans;
    private int durationCheckTask;

    public DuelManager(final DuelsPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfiguration();
        this.lang = plugin.getLang();
        this.userDataManager = plugin.getUserManager();
        this.arenaManager = plugin.getArenaManager();
        this.playerManager = plugin.getPlayerManager();
        this.inventoryManager = plugin.getInventoryManager();
        this.spectateManager = plugin.getSpectateManager();
        plugin.doSyncAfter(() -> plugin.getServer().getPluginManager().registerEvents(new DuelListener(), plugin), 1L);
    }

    @Override
    public void handleLoad() {
        this.queueManager = plugin.getQueueManager();
        this.teleport = plugin.getTeleport();
        this.combatTagPlus = plugin.getHookManager().getHook(CombatTagPlusHook.class);
        this.pvpManager = plugin.getHookManager().getHook(PvPManagerHook.class);
        this.combatLogX = plugin.getHookManager().getHook(CombatLogXHook.class);
        this.vault = plugin.getHookManager().getHook(VaultHook.class);
        this.essentials = plugin.getHookManager().getHook(EssentialsHook.class);
        this.mcMMO = plugin.getHookManager().getHook(McMMOHook.class);
        this.worldGuard = plugin.getHookManager().getHook(WorldGuardHook.class);
        this.myPet = plugin.getHookManager().getHook(MyPetHook.class);
        this.simpleClans = plugin.getHookManager().getHook(SimpleClansHook.class);

        if (config.getMaxDuration() > 0) {
            this.durationCheckTask = plugin.doSyncRepeat(() -> {
                final long now = System.currentTimeMillis();

                for (final Arena arena : arenaManager.getArenas()) {
                    final Match match = arena.getMatch();

                    if (match == null || now - match.getStart() < (config.getMaxDuration() * 60 * 1000) || arena.size() <= 1) {
                        continue;
                    }

                    for (final Player player : match.getAllPlayers()) {
                        handleTiePlayer(player, arena, match, true);
                        lang.sendMessage(player, "DUEL.on-end.tie");
                    }

                    arena.endMatch(null, null, Reason.MAX_TIME_REACHED);
                }
            }, 0L, 20L).getTaskId();
        }
    }

    @Override
    public void handleUnload() {
        plugin.cancelTask(durationCheckTask);

        for (final Arena arena : arenaManager.getArenas()) {
            final Match match = arena.getMatch();

            if (match == null) {
                continue;
            }

            final int size = arena.size();
            final boolean ongoing = size > 1;
            final boolean tie = size == 0;

            for (final Player player : match.getAllPlayers()) {
                // This case wouldn't be called if ongoing = true
                if (match.isDead(player)) {
                    continue;
                }

                // Now checks for non-dead players. ongoing = false is the case after winner is decided
                if (ongoing) {
                    handleTiePlayer(player, arena, match, true);
                } else if (!tie) {
                    handleWinner(player, getOther(player, match.getAllPlayers()), arena, match);
                }
            }

            arena.getPlayers().forEach(player -> lang.sendMessage(player, "DUEL.on-end.plugin-disable"));
            if (match.getSettings().getAllyTeam().size() > 1) {
                arena.endTeamMatch(new HashSet<>(), new HashSet<>(), Reason.PLUGIN_DISABLE);
            } else {
                arena.endMatch(null, null, Reason.PLUGIN_DISABLE);
            }
        }

        Players.getOnlinePlayers().stream().filter(Player::isDead).forEach(player -> {
            final PlayerInfo info = playerManager.removeAndGet(player);

            if (info != null) {
                player.spigot().respawn();
                teleport.tryTeleport(player, info.getLocation());
                PlayerUtil.reset(player);
                info.restore(player);
            }
        });
    }

    private Player getOther(final Player player, final Set<Player> players) {
        final Set<Player> copy = new HashSet<>(players);
        copy.remove(player);

        if (copy.isEmpty()) {
            return null;
        }

        return copy.iterator().next();
    }

    private void handleTiePlayer(final Player player, final Arena arena, final Match match, boolean alive) {
        arena.remove(player);

        if (vault != null && match.getBet() > 0) {
            vault.add(match.getBet(), player);
        }

        if (mcMMO != null) {
            mcMMO.enableSkills(player);
        }

        if (alive && !config.isUseOwnInventoryEnabled()) {
            PlayerUtil.reset(player);
        }

        final PlayerInfo info = playerManager.get(player);

        if (alive) {
            playerManager.remove(player);

            if (info != null) {
                teleport.tryTeleport(player, info.getLocation(), failed -> {
                    failed.setHealth(0);
                    failed.sendMessage(StringUtil.color("&cTeleportation failed! You were killed to prevent staying in the arena."));
                });
                info.restore(player);
            } else {
                // If somehow PlayerInfo is not found...
                teleport.tryTeleport(player, playerManager.getLobby());
            }

            match.getItems(player).stream().filter(Objects::nonNull).forEach(item -> player.getInventory().addItem(item));
        } else {
            final List<ItemStack> items = match.getItems(player);

            if (player.isDead()) {
                addItems(info, items);
            } else {
                // Note to self: Unsure if this case will ever be called, since player has to respawn instantly after a tick.
                items.stream().filter(Objects::nonNull).forEach(item -> player.getInventory().addItem(item));
            }
        }
    }

    private void handleTeamWinner(Set<Player> winners, Set<Player> opponents, Arena arena, Match match) {
        winners.forEach(winner -> {
            arena.remove(winner);
            if (vault != null && match.getBet() > 0) {
                final int amount = match.getBet() * 2;
                vault.add(amount, winner);
                lang.sendMessage(winner, "DUEL.reward.money.message", "name", opponents != null ? TeamUtil.getTeamStringByPlayers(opponents) : lang.getMessage("GENERAL.none"), "money", amount);

                final String title = lang.getMessage("DUEL.reward.money.title", "name", opponents != null ? TeamUtil.getTeamStringByPlayers(opponents) : lang.getMessage("GENERAL.none"), "money", amount);

                if (title != null) {
                    Titles.send(winner, title, null, 0, 20, 50);
                }
            }
            if (mcMMO != null) {
                mcMMO.enableSkills(winner);
            }
            final List<ItemStack> items = match.getItems();
            final PlayerInfo info = playerManager.get(winner);
            if (winner.isDead()) {
                addItems(info, items);
            } else if (winner.isOnline()) {
                playerManager.remove(winner);

                if (info != null) {
                    if (!config.isUseOwnInventoryEnabled()) {
                        PlayerUtil.reset(winner);
                    }

                    teleport.tryTeleport(winner, info.getLocation(), failed -> {
                        failed.setHealth(0);
                        failed.sendMessage(StringUtil.color("&cTeleportation failed! You were killed to prevent staying in the arena."));
                    });
                    info.restore(winner);
                }

                boolean added = false;

                for (final ItemStack item : items) {
                    if (item == null) {
                        continue;
                    }

                    if (!added) {
                        added = true;
                    }

                    winner.getInventory().addItem(item);
                }

                if (added) {
                    lang.sendMessage(winner, "DUEL.reward.items.message", "name", opponents != null ? TeamUtil.getTeamStringByPlayers(opponents) : lang.getMessage("GENERAL.none"));
                }
            }
        });

    }

    private void handleWinner(final Player player, final Player opponent, final Arena arena, final Match match) {
        arena.remove(player);

        if (vault != null && match.getBet() > 0) {
            final int amount = match.getBet() * 2;
            vault.add(amount, player);
            lang.sendMessage(player, "DUEL.reward.money.message", "name", opponent != null ? opponent.getName() : lang.getMessage("GENERAL.none"), "money", amount);

            final String title = lang.getMessage("DUEL.reward.money.title", "name", opponent != null ? opponent.getName() : lang.getMessage("GENERAL.none"), "money", amount);

            if (title != null) {
                Titles.send(player, title, null, 0, 20, 50);
            }
        }

        if (mcMMO != null) {
            mcMMO.enableSkills(player);
        }

        final List<ItemStack> items = match.getItems();
        final PlayerInfo info = playerManager.get(player);

        if (player.isDead()) {
            addItems(info, items);
        } else if (player.isOnline()) {
            playerManager.remove(player);

            if (info != null) {
                if (!config.isUseOwnInventoryEnabled()) {
                    PlayerUtil.reset(player);
                }

                teleport.tryTeleport(player, info.getLocation(), failed -> {
                    failed.setHealth(0);
                    failed.sendMessage(StringUtil.color("&cTeleportation failed! You were killed to prevent staying in the arena."));
                });
                info.restore(player);
            }

            boolean added = false;

            for (final ItemStack item : items) {
                if (item == null) {
                    continue;
                }

                if (!added) {
                    added = true;
                }

                player.getInventory().addItem(item);
            }

            if (added) {
                lang.sendMessage(player, "DUEL.reward.items.message", "name", opponent != null ? opponent.getName() : lang.getMessage("GENERAL.none"));
            }
        }
    }

    private void addItems(final PlayerInfo info, final List<ItemStack> items) {
        if (info != null) {
            info.getExtra().addAll(items);
        }
    }

    public void startTeamMatch(Player starter, Set<Player> team1, Set<Player> team2, Settings settings) {
        Kit kit = settings.getKit();
        Set<Player> allPlayers = new HashSet<>(team1);
        allPlayers.addAll(team2);
        if (!config.isUseOwnInventoryEnabled() && kit == null) {
            lang.sendMessage(allPlayers, "DUEL.start-failure.no-kit-selected");
            return;
        }
        final int bet = settings.getBet();

        for (Player player : allPlayers) {
            if (isBlacklistedWorld(player)) {
                lang.sendMessage(allPlayers, "DUEL.start-failure.in-blacklisted-world");
                return;
            }
            if (isTagged(player)) {
                lang.sendMessage(allPlayers, "DUEL.start-failure.is-tagged");
                return;
            }
            if (config.isCancelIfMoved() && (notInLoc(player, settings.getBaseLoc(player)))) {
                lang.sendMessage(allPlayers, "DUEL.start-failure.player-moved");
                return;
            }
            if (config.isDuelzoneEnabled() && worldGuard != null && (notInDz(player, settings.getDuelzone(player)))) {
                lang.sendMessage(allPlayers, "DUEL.start-failure.not-in-duelzone");
                return;
            }
            if (config.isPreventCreativeMode() && (notNoCreative(player, settings.getGameMode(player)))) {
                lang.sendMessage(allPlayers, "DUEL.start-failure.in-creative-mode");
                return;
            }
            if (bet > 0 && vault != null && vault.getEconomy() != null) {
                if (!vault.has(bet, player)) {
                    lang.sendMessage(allPlayers, "DUEL.start-failure.not-enough-money", "bet_amount", bet);
                    return;
                }
                vault.remove(bet, player);
            }
        }
        final Arena arena = settings.getArena() != null ? settings.getArena() : arenaManager.randomArena(kit);

        if (arena == null || !arena.isAvailable()) {
            lang.sendMessage(allPlayers, "DUEL.start-failure." + (settings.getArena() != null ? "arena-in-use" : "no-arena-available"));
            return;
        }

        if (kit != null && !arenaManager.isSelectable(kit, arena)) {
            lang.sendMessage(allPlayers, "DUEL.start-failure.arena-not-applicable");
            return;
        }
        final Match match = arena.startMatch(kit, null, settings.getBet(), null, settings);
        Location locationTeam1 = arena.getPosition(1);
        Location locationTeam2 = arena.getPosition(2);
        addPlayersTeam(null, arena, kit, locationTeam1, team1);
        addPlayersTeam(null, arena, kit, locationTeam2, team2);

    }

    public void startMatch(final Player first, final Player second, final Settings settings, final Map<UUID, List<ItemStack>> items, final Queue source) {
        final Kit kit = settings.getKit();

        if (!config.isUseOwnInventoryEnabled() && kit == null) {
            lang.sendMessage(Arrays.asList(first, second), "DUEL.start-failure.no-kit-selected");
            refundItems(items, first, second);
            return;
        }

        if (isBlacklistedWorld(first) || isBlacklistedWorld(second)) {
            lang.sendMessage(Arrays.asList(first, second), "DUEL.start-failure.in-blacklisted-world");
            refundItems(items, first, second);
            return;
        }

        if (isTagged(first) || isTagged(second)) {
            lang.sendMessage(Arrays.asList(first, second), "DUEL.start-failure.is-tagged");
            refundItems(items, first, second);
            return;
        }

        if (config.isCancelIfMoved() && (notInLoc(first, settings.getBaseLoc(first)) || notInLoc(second, settings.getBaseLoc(second)))) {
            lang.sendMessage(Arrays.asList(first, second), "DUEL.start-failure.player-moved");
            refundItems(items, first, second);
            return;
        }

        if (config.isDuelzoneEnabled() && worldGuard != null && (notInDz(first, settings.getDuelzone(first)) || notInDz(second, settings.getDuelzone(second)))) {
            lang.sendMessage(Arrays.asList(first, second), "DUEL.start-failure.not-in-duelzone");
            refundItems(items, first, second);
            return;
        }

        if (config.isPreventCreativeMode() && (notNoCreative(first, settings.getGameMode(first)) || notNoCreative(second, settings.getGameMode(second)))) {
            lang.sendMessage(Arrays.asList(first, second), "DUEL.start-failure.in-creative-mode");
            refundItems(items, first, second);
            return;
        }

        final Arena arena = settings.getArena() != null ? settings.getArena() : arenaManager.randomArena(kit);

        if (arena == null || !arena.isAvailable()) {
            lang.sendMessage(Arrays.asList(first, second), "DUEL.start-failure." + (settings.getArena() != null ? "arena-in-use" : "no-arena-available"));
            refundItems(items, first, second);
            return;
        }

        if (kit != null && !arenaManager.isSelectable(kit, arena)) {
            lang.sendMessage(Arrays.asList(first, second), "DUEL.start-failure.arena-not-applicable");
            refundItems(items, first, second);
            return;
        }

        final int bet = settings.getBet();

        if (bet > 0 && vault != null && vault.getEconomy() != null) {
            if (!vault.has(bet, first, second)) {
                lang.sendMessage(Arrays.asList(first, second), "DUEL.start-failure.not-enough-money", "bet_amount", bet);
                refundItems(items, first, second);
                return;
            }

            vault.remove(bet, first, second);
        }

        final Match match = arena.startMatch(kit, items, settings.getBet(), source, settings);
        addPlayers(source, arena, kit, arena.getPositions(), first, second);

        if (config.isCdEnabled()) {
            final Map<UUID, OpponentInfo> info = new HashMap<>();
            info.put(first.getUniqueId(), new OpponentInfo(second.getName(), getRating(kit, userDataManager.get(second))));
            info.put(second.getUniqueId(), new OpponentInfo(first.getName(), getRating(kit, userDataManager.get(first))));
            arena.startCountdown(kit != null ? kit.getName() : lang.getMessage("GENERAL.none"), info);
        }

        final MatchStartEvent event = new MatchStartEvent(match, first, second);
        plugin.getServer().getPluginManager().callEvent(event);
    }

    private void refundItems(final Map<UUID, List<ItemStack>> items, final Player... players) {
        if (items != null) {
            Arrays.stream(players).forEach(player -> {
                final List<ItemStack> list = items.get(player.getUniqueId());

                if (list != null) {
                    list.stream().filter(Objects::nonNull).forEach(item -> player.getInventory().addItem(item));
                }
            });
        }
    }

    private boolean isBlacklistedWorld(final Player player) {
        return config.getBlacklistedWorlds().contains(player.getWorld().getName());
    }

    private boolean isTagged(final Player player) {
        return (combatTagPlus != null && combatTagPlus.isTagged(player))
                || (pvpManager != null && pvpManager.isTagged(player))
                || (combatLogX != null && combatLogX.isTagged(player));
    }

    private boolean notInLoc(final Player player, final Location location) {
        if (location == null) {
            return false;
        }

        final Location source = player.getLocation();
        return !source.getWorld().equals(location.getWorld()) || source.getBlockX() != location.getBlockX() || source.getBlockY() != location.getBlockY() || source.getBlockZ() != location.getBlockZ();
    }

    private boolean notInDz(final Player player, final String duelzone) {
        return duelzone != null && !duelzone.equals(worldGuard.findDuelZone(player));
    }

    private boolean notNoCreative(final Player player, final GameMode gameMode) {
        return gameMode != null && player.getGameMode() != gameMode;
    }

    private int getRating(final Kit kit, final UserData user) {
        return user != null ? user.getRating(kit) : config.getDefaultRating();
    }

    private void addPlayersTeam(final Queue source, final Arena arena, final Kit kit, Location location, final Set<Player> players) {

        for (final Player player : players) {
            if (source == null) {
                queueManager.remove(player);
            }

            inventoryManager.remove(player);

            if (player.getAllowFlight()) {
                player.setFlying(false);
                player.setAllowFlight(false);
            }

            player.closeInventory();
            playerManager.put(player, new PlayerInfo(player, !config.isUseOwnInventoryEnabled()));

            if (!config.isUseOwnInventoryEnabled()) {
                PlayerUtil.reset(player);

                if (kit != null) {
                    kit.equip(player);
                }
            }

            if (config.isStartCommandsEnabled()) {
                try {
                    for (final String command : config.getStartCommands()) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", player.getName()));
                    }
                } catch (Exception ex) {
                    Log.warn(DuelManager.this, "Error while running match start commands: " + ex.getMessage());
                }
            }

            if (myPet != null) {
                myPet.removePet(player);
            }

            teleport.tryTeleport(player, location);

            if (essentials != null) {
                essentials.tryUnvanish(player);
            }

            if (mcMMO != null) {
                mcMMO.disableSkills(player);
            }

            arena.add(player);
        }
    }

    private void addPlayers(final Queue source, final Arena arena, final Kit kit, final Map<Integer, Location> locations, final Player... players) {
        int position = 0;

        for (final Player player : players) {
            if (source == null) {
                queueManager.remove(player);
            }

            inventoryManager.remove(player);

            if (player.getAllowFlight()) {
                player.setFlying(false);
                player.setAllowFlight(false);
            }

            player.closeInventory();
            playerManager.put(player, new PlayerInfo(player, !config.isUseOwnInventoryEnabled()));

            if (!config.isUseOwnInventoryEnabled()) {
                PlayerUtil.reset(player);

                if (kit != null) {
                    kit.equip(player);
                }
            }

            if (config.isStartCommandsEnabled()) {
                try {
                    for (final String command : config.getStartCommands()) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", player.getName()));
                    }
                } catch (Exception ex) {
                    Log.warn(DuelManager.this, "Error while running match start commands: " + ex.getMessage());
                }
            }

            if (myPet != null) {
                myPet.removePet(player);
            }

            teleport.tryTeleport(player, locations.get(++position));

            if (essentials != null) {
                essentials.tryUnvanish(player);
            }

            if (mcMMO != null) {
                mcMMO.disableSkills(player);
            }

            arena.add(player);
        }
    }

    private void handleInventories(final Match match) {
        if (!config.isDisplayInventories()) {
            return;
        }

        String color = lang.getMessage("DUEL.inventories.name-color");
        color = color != null ? color : "";
        boolean start = true;
        final TextBuilder builder = TextBuilder.of(lang.getMessage("DUEL.inventories.message"));

        for (final Player matchPlayer : match.getAllPlayers()) {
            if (!start) {
                builder.add(StringUtil.color(color + ", "));
            } else {
                start = false;
            }

            builder.add(StringUtil.color(color + matchPlayer.getName()), Action.RUN_COMMAND, "/duel _ " + matchPlayer.getUniqueId());
        }

        builder.send(match.getAllPlayers());
    }

    private void handleEndUsersTeam(Match match, Set<UserData> winners, Set<UserData> losers, MatchData matchData) {

        String winnerNames = winners.stream().map(UserData::getName).collect(Collectors.joining(", "));
        String loserNames = losers.stream().map(UserData::getName).collect(Collectors.joining(", "));

        final String message = lang.getMessage("DUEL.on-end.opponent-defeat",
                "winner", winnerNames,
                "loser", loserNames,
                "health", matchData.getHealth(),
                "kit", matchData.getKit(),
                "arena", match.getArena().getName()
        );
        if (message == null) {
            return;
        }
        if (config.isArenaOnlyEndMessage()) {
            match.getArena().broadcast(message);
        } else {
            Players.getOnlinePlayers().forEach(player -> player.sendMessage(message));
        }
    }

    private void handleEndUsers(final Match match, final UserData winner, final UserData loser, final MatchData matchData) {
        if (winner != null && loser != null) {
            winner.addWin();
            loser.addLoss();
            winner.addMatch(matchData);
            loser.addMatch(matchData);

            final Kit kit = match.getKit();
            int winnerRating = kit == null ? winner.getRating() : winner.getRating(kit);
            int loserRating = kit == null ? loser.getRating() : loser.getRating(kit);
            int change = 0;

            if (config.isRatingEnabled() && !(!match.isFromQueue() && config.isQueueMatchesOnly())) {
                change = RatingUtil.getChange(config.getKFactor(), winnerRating, loserRating);
                winner.setRating(kit, winnerRating = winnerRating + change);
                loser.setRating(kit, loserRating = loserRating - change);
            }

            final String message = lang.getMessage("DUEL.on-end.opponent-defeat",
                    "winner", winner.getName(),
                    "loser", loser.getName(),
                    "health", matchData.getHealth(),
                    "kit", matchData.getKit(),
                    "arena", match.getArena().getName(),
                    "winner_rating", winnerRating,
                    "loser_rating", loserRating,
                    "change", change
            );

            if (message == null) {
                return;
            }

            if (config.isArenaOnlyEndMessage()) {
                match.getArena().broadcast(message);
            } else {
                Players.getOnlinePlayers().forEach(player -> player.sendMessage(message));
            }
        }
    }

    private void cancelDamage(final EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        final Player player = (Player) event.getEntity();
        final Arena arena = arenaManager.get(player);

        if (arena == null) {
            return;
        }

        if (arena.size() > 1) {
            return;
        }

        event.setCancelled(true);
    }

    public class OpponentInfo {

        @Getter
        private final String name;
        @Getter
        private final int rating;

        OpponentInfo(final String name, final int rating) {
            this.name = name;
            this.rating = rating;
        }
    }

    // Separating out the listener fixes weird error with 1.7 spigot
    private class DuelListener implements Listener {

        @EventHandler(priority = EventPriority.LOWEST)
        public void onLowest(final PlayerDeathEvent event) {
            if (!arenaManager.isInMatch(event.getEntity())) {
                return;
            }

            if (!(!config.isUseOwnInventoryEnabled() || config.isUseOwnInventoryKeepItems())) {
                return;
            }

            event.setKeepLevel(true);
            event.setDroppedExp(0);
            event.getDrops().clear();
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void on(final PlayerDeathEvent event) {
            final Player player = event.getEntity();
            final Arena arena = arenaManager.get(player);

            if (arena == null) {
                return;
            }

            if (mcMMO != null) {
                mcMMO.enableSkills(player);
            }

            if (simpleClans != null) {
                simpleClans.removeDeath(player);
            }

            event.setKeepInventory(config.isUseOwnInventoryEnabled() && config.isUseOwnInventoryKeepItems());
            inventoryManager.create(player);
            arena.remove(player);

            // Call end task only on the first death
            if (arena.size() <= 0) {
                return;
            }

            plugin.doSyncAfter(() -> {
                final Match match = arena.getMatch();

                if (match == null) {
                    return;
                }

                if (arena.size() == 0) {
                    match.getAllPlayers().forEach(matchPlayer -> {
                        handleTiePlayer(matchPlayer, arena, match, false);
                        lang.sendMessage(matchPlayer, "DUEL.on-end.tie");
                    });
                    handleInventories(match);
                    arena.endMatch(null, null, Reason.TIE);
                    return;
                }
                if (match.getSettings().getAllyTeam().size() > 1) {
                    handleTeamDeath(arena, match, player);
                    return;
                }

                final Player winner = arena.first();
                inventoryManager.create(winner);

                if (config.isSpawnFirework()) {
                    final Firework firework = (Firework) winner.getWorld().spawnEntity(winner.getEyeLocation(), EntityType.FIREWORK);
                    final FireworkMeta meta = firework.getFireworkMeta();
                    meta.addEffect(FireworkEffect.builder().withColor(Color.RED).with(FireworkEffect.Type.BALL_LARGE).withTrail().build());
                    firework.setFireworkMeta(meta);
                }

                final double health = Math.ceil(winner.getHealth()) * 0.5;
                final String kitName = match.getKit() != null ? match.getKit().getName() : lang.getMessage("GENERAL.none");
                final long duration = System.currentTimeMillis() - match.getStart();
                final long time = new GregorianCalendar().getTimeInMillis();
                final MatchData matchData = new MatchData(winner.getName(), player.getName(), kitName, time, duration, health);
                handleEndUsers(match, userDataManager.get(winner), userDataManager.get(player), matchData);
                handleInventories(match);
                plugin.doSyncAfter(() -> {
                    handleWinner(winner, player, arena, match);

                    if (config.isEndCommandsEnabled()) {
                        try {
                            for (final String command : config.getEndCommands()) {
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command
                                        .replace("%winner%", winner.getName()).replace("%loser%", player.getName())
                                        .replace("%kit%", kitName).replace("%arena%", arena.getName())
                                        .replace("%bet_amount%", String.valueOf(match.getBet()))
                                );
                            }
                        } catch (Exception ex) {
                            Log.warn(DuelManager.this, "Error while running match end commands: " + ex.getMessage());
                        }
                    }

                    arena.endMatch(winner.getUniqueId(), player.getUniqueId(), Reason.OPPONENT_DEFEAT);
                }, config.getTeleportDelay() * 20L);
            }, 1L);
        }

        private void handleTeamDeath(Arena arena, Match match, Player deadPlayer) {
            Set<Player> alliesAlive = getPlayersAllive(match.getSettings().getAllyTeam(), match);
            Set<Player> targetsAlive = getPlayersAllive(match.getSettings().getTargetTeam(), match);
            if (alliesAlive.size() == 0) { //targets won

                final long duration = System.currentTimeMillis() - match.getStart();
                final long time = new GregorianCalendar().getTimeInMillis();
                handleEndUsersTeam(match,
                        match.getSettings().getTargetTeam().stream().map(userDataManager::get).collect(Collectors.toSet()),
                        match.getSettings().getAllyTeam().stream().map(userDataManager::get).collect(Collectors.toSet()),
                        new MatchData("", "", match.getKit().getName(), time, duration, targetsAlive.stream().mapToDouble(Damageable::getHealth).sum()));
                handleWinnerTeam(arena, match, match.getSettings().getTargetTeam(), targetsAlive, match.getSettings().getAllyTeam());
            } else if (targetsAlive.size() == 0) { //allies won
                final long duration = System.currentTimeMillis() - match.getStart();
                final long time = new GregorianCalendar().getTimeInMillis();
                handleEndUsersTeam(match,
                        match.getSettings().getAllyTeam().stream().map(userDataManager::get).collect(Collectors.toSet()),
                        match.getSettings().getTargetTeam().stream().map(userDataManager::get).collect(Collectors.toSet()),
                        new MatchData("", "", match.getKit().getName(), time, duration, alliesAlive.stream().mapToDouble(Damageable::getHealth).sum()));
                handleWinnerTeam(arena, match, match.getSettings().getAllyTeam(), alliesAlive, match.getSettings().getTargetTeam());
            } else {
                MethodMessage.sendMessage(deadPlayer, "Du døde, tryk her for at spectate dine teammates", () -> {
                    boolean isAlly = match.getSettings().getAllyTeam().contains(deadPlayer.getUniqueId());
                    boolean isTarget = match.getSettings().getTargetTeam().contains(deadPlayer.getUniqueId());
                    boolean allAlliesDead = true;
                    if (isAlly) {
                        Set<Player> players = getPlayersAllive(match.getSettings().getAllyTeam(), match);
                        if (players.size() == 0) {
                            deadPlayer.sendMessage("§cAlle dine teammates er døde og du kan derfor ikke spectate fighten");
                        } else {
                            spectateManager.startSpectating(deadPlayer, players.stream().findFirst().get());
                        }
                    }
                }, 15);

            }
        }
        private Set<Player> getPlayersAllive(Set<UUID> teamToCheck, Match match) {
            return teamToCheck.stream()
                    .map(uuid -> {
                        Player player = Bukkit.getPlayer(uuid);
                        if (player == null)
                            return null;
                        if (match.isDead(player))
                            return null;
                        return player;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        }

        private void handleWinnerTeam(Arena arena, Match match, Set<UUID> winningPlayerUUIDs, Set<Player> playersAlive, Set<UUID> losingPlayers) {
            if (config.isSpawnFirework()) {
                playersAlive.forEach(winner -> {
                    final Firework firework = (Firework) winner.getWorld().spawnEntity(winner.getEyeLocation(), EntityType.FIREWORK);
                    final FireworkMeta meta = firework.getFireworkMeta();
                    meta.addEffect(FireworkEffect.builder().withColor(Color.RED).with(FireworkEffect.Type.BALL_LARGE).withTrail().build());
                    firework.setFireworkMeta(meta);
                });
            }
            plugin.doSyncAfter(() -> {
                Set<Player> winningPlayers = winningPlayerUUIDs.stream().map(Bukkit::getPlayer).filter(Objects::nonNull).collect(Collectors.toSet());
                Set<Player> opponents = losingPlayers.stream().map(Bukkit::getPlayer).filter(Objects::nonNull).collect(Collectors.toSet());
                handleTeamWinner(winningPlayers, opponents, arena, match);

                if (config.isEndCommandsEnabled()) {
                    try {
                        for (final String command : config.getEndCommands()) {
                            if (command.contains("%winner%")) {
                                winningPlayers.forEach(winner -> {
                                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command
                                            .replace("%winner%", winner.getName())
                                            .replace("%kit%", match.getKit() == null ? "Intet kit" : match.getKit().getName())
                                            .replace("%arena%", arena.getName())
                                            .replace("%bet_amount%", String.valueOf(match.getBet()))
                                    );
                                });
                            } else if (command.contains("%loser%")) {
                                opponents.forEach(loser -> {
                                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command
                                            .replace("%loser%", loser.getName())
                                            .replace("%kit%", match.getKit() == null ? "Intet kit" : match.getKit().getName())
                                            .replace("%arena%", arena.getName())
                                            .replace("%bet_amount%", String.valueOf(match.getBet()))
                                    );
                                });
                            } else {
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command
                                        .replace("%kit%", match.getKit() == null ? "Intet kit" : match.getKit().getName())
                                        .replace("%arena%", arena.getName())
                                        .replace("%bet_amount%", String.valueOf(match.getBet()))
                                );
                            }

                        }
                    } catch (Exception ex) {
                        Log.warn(DuelManager.this, "Error while running match end commands: " + ex.getMessage());
                    }
                }


                arena.endTeamMatch(winningPlayers, opponents, Reason.OPPONENT_DEFEAT);
            }, config.getTeleportDelay() * 20L);
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void on(final PlayerRespawnEvent event) {
            final Player player = event.getPlayer();
            final PlayerInfo info = playerManager.removeAndGet(player);

            if (info != null) {
                event.setRespawnLocation(info.getLocation());

                if (essentials != null) {
                    essentials.setBackLocation(player, event.getRespawnLocation());
                }

                plugin.doSyncAfter(() -> {
                    if (!player.isOnline()) {
                        info.setGiveOnLogin(true);
                        playerManager.put(player, info);
                        return;
                    }

                    info.restore(player);
                }, 1L);
            }
        }

        @EventHandler(ignoreCancelled = true)
        public void on(final EntityDamageEvent event) {
            cancelDamage(event);
        }

        @EventHandler(ignoreCancelled = true)
        public void on(final EntityDamageByEntityEvent event) {
            cancelDamage(event);
        }

        @EventHandler
        public void on(final PlayerQuitEvent event) {
            final Player player = event.getPlayer();

            if (!arenaManager.isInMatch(player)) {
                return;
            }

            player.setHealth(0);
        }

        @EventHandler(ignoreCancelled = true)
        public void on(final PlayerDropItemEvent event) {
            if (!config.isPreventItemDrop() || !arenaManager.isInMatch(event.getPlayer())) {
                return;
            }

            event.setCancelled(true);
            lang.sendMessage(event.getPlayer(), "DUEL.prevent.item-drop");
        }

        @EventHandler(ignoreCancelled = true)
        public void on(final PlayerPickupItemEvent event) {
            if (!config.isPreventItemPickup() || !arenaManager.isInMatch(event.getPlayer())) {
                return;
            }

            if (!CompatUtil.isPre1_13() && event.getItem().getType() == EntityType.TRIDENT) {
                return;
            }

            event.setCancelled(true);
        }

        @EventHandler(ignoreCancelled = true)
        public void on(final PlayerCommandPreprocessEvent event) {
            final String command = event.getMessage().substring(1).split(" ")[0].toLowerCase();

            if (!arenaManager.isInMatch(event.getPlayer()) || (config.isBlockAllCommands() ? config.getWhitelistedCommands().contains(command)
                    : !config.getBlacklistedCommands().contains(command))) {
                return;
            }

            event.setCancelled(true);
            lang.sendMessage(event.getPlayer(), "DUEL.prevent.command", "command", event.getMessage());
        }

        @EventHandler(ignoreCancelled = true)
        public void on(PlayerTeleportEvent event) {
            final Player player = event.getPlayer();
            final Location to = event.getTo();

            if (!config.isLimitTeleportEnabled() || event.getCause() == TeleportCause.ENDER_PEARL || !arenaManager.isInMatch(player)) {
                return;
            }

            final Location from = event.getFrom();

            if (from.getWorld().equals(to.getWorld()) && from.distance(to) <= config.getDistanceAllowed()) {
                return;
            }

            event.setCancelled(true);
            lang.sendMessage(player, "DUEL.prevent.teleportation");
        }

        @EventHandler(ignoreCancelled = true)
        public void on(final InventoryOpenEvent event) {
            if (!config.isPreventInventoryOpen()) {
                return;
            }

            final Player player = (Player) event.getPlayer();

            if (!arenaManager.isInMatch(player)) {
                return;
            }

            event.setCancelled(true);
            lang.sendMessage(player, "DUEL.prevent.inventory-open");
        }

        @EventHandler(ignoreCancelled = true)
        public void on(final PlayerItemDamageEvent event) {
            if (!config.isUseOwnInventoryEnabled() || !arenaManager.isInMatch(event.getPlayer())) {
                return;
            }

            event.setCancelled(true);
        }
    }
}