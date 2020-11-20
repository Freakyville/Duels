package me.realized.duels.command.commands.teamduel;

import me.realized.duels.DuelsPlugin;
import me.realized.duels.Permissions;
import me.realized.duels.command.BaseCommand;
import me.realized.duels.command.commands.teamduel.subcommands.AcceptCommand;
import me.realized.duels.command.commands.teamduel.subcommands.DenyCommand;
import me.realized.duels.data.UserData;
import me.realized.duels.hook.hooks.CombatLogXHook;
import me.realized.duels.hook.hooks.CombatTagPlusHook;
import me.realized.duels.hook.hooks.PvPManagerHook;
import me.realized.duels.hook.hooks.VaultHook;
import me.realized.duels.hook.hooks.worldguard.WorldGuardHook;
import me.realized.duels.setting.Settings;
import me.realized.duels.util.NumberUtil;
import me.realized.duels.util.inventory.InventoryUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TeamDuelCommand extends BaseCommand {
    private final CombatTagPlusHook combatTagPlus;
    private final PvPManagerHook pvpManager;
    private final CombatLogXHook combatLogX;
    private final WorldGuardHook worldGuard;
    private final VaultHook vault;

    public TeamDuelCommand(DuelsPlugin duelsPlugin) {
        super(duelsPlugin, "teamduel", Permissions.DUEL, true);
        child(new AcceptCommand(duelsPlugin),
                new DenyCommand(duelsPlugin));
        this.combatTagPlus = hookManager.getHook(CombatTagPlusHook.class);
        this.pvpManager = hookManager.getHook(PvPManagerHook.class);
        this.combatLogX = hookManager.getHook(CombatLogXHook.class);
        this.worldGuard = hookManager.getHook(WorldGuardHook.class);
        this.vault = hookManager.getHook(VaultHook.class);
    }
    @Override
    protected void execute(final CommandSender sender, final String label, final String[] args) {}

    // Disables default TabCompleter
    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
        return null;
    }
    @Override
    protected boolean executeFirst(final CommandSender sender, final String label, final String[] args) {

        Player player = (Player) sender;

        if (config.isRequiresClearedInventory() && InventoryUtil.hasItem(player)) {
            lang.sendMessage(sender, "ERROR.duel.inventory-not-empty");
            return true;
        }

        GameMode gameMode = null;
        if (isChild(args[0])) {
            return false;
        }
        if (args.length < 3) {
            lang.sendMessage(sender, "COMMAND.duel.teamusage", "command", label);
            return true;
        }


        if (config.isPreventCreativeMode() && (gameMode = player.getGameMode()) == GameMode.CREATIVE) {
            lang.sendMessage(sender, "ERROR.duel.in-creative-mode");
            return true;
        }

        if (config.getBlacklistedWorlds().contains(player.getWorld().getName())) {
            lang.sendMessage(sender, "ERROR.duel.in-blacklisted-world");
            return true;
        }
        if ((combatTagPlus != null && combatTagPlus.isTagged(player))
                || (pvpManager != null && pvpManager.isTagged(player))
                || (combatLogX != null && combatLogX.isTagged(player))) {
            lang.sendMessage(sender, "ERROR.duel.is-tagged");
            return true;
        }

        String duelzone = null;

        if (worldGuard != null && config.isDuelzoneEnabled() && (duelzone = worldGuard.findDuelZone(player)) == null) {
            lang.sendMessage(sender, "ERROR.duel.not-in-duelzone", "regions", config.getDuelzones());
            return true;
        }

        if (arenaManager.isInMatch(player)) {
            lang.sendMessage(sender, "ERROR.duel.already-in-match.sender");
            return true;
        }

        if (spectateManager.isSpectating(player)) {
            lang.sendMessage(sender, "ERROR.spectate.already-spectating.sender");
            return true;
        }



        if (args.length < 5) {
            if (!setup2v2(player, args)) {
                return true;
            }
        } else {
            if (!setup3v3(player, args)) {
                return true;
            }
        }

        kitManager.getGui().open(player);
        return true;
    }

    private boolean setup3v3(Player creator, String[] args) {
        Set<Player> team1 = new HashSet<>();
        Set<Player> team2 = new HashSet<>();
        Player p2 = getAndVerifyPlayer(creator, args[0]);
        Player p3 = getAndVerifyPlayer(creator, args[1]);
        Player p4 = getAndVerifyPlayer(creator, args[2]);
        Player p5 = getAndVerifyPlayer(creator, args[3]);
        Player p6 = getAndVerifyPlayer(creator, args[4]);
        if (p2 == null || p3 == null || p4 == null || p5 == null || p6 == null) {
            lang.sendMessage(creator, "ERROR.team.notonline");
            return false;
        }
        team1.add(creator);
        team1.add(p2);
        team1.add(p3);
        team2.add(p4);
        team2.add(p5);
        team2.add(p6);

        if (team1.size() != 3 || team2.size() != 3) {
            lang.sendMessage(creator, "ERROR.team.same-players");
            return false;
        }

        final Settings settings = settingManager.getSafely(creator);
        settings.setBet(0);

        Integer bet = getBet(creator, args[3]);
        if (bet == null)
            return false;

        settings.setAllyTeam(team1.stream().map(Entity::getUniqueId).collect(Collectors.toSet()));
        settings.setTargetTeam(team2.stream().map(Entity::getUniqueId).collect(Collectors.toSet()));

        return true;
    }

    private boolean setup2v2(Player creator, String[] args) {
        Set<Player> team1 = new HashSet<>();
        Set<Player> team2 = new HashSet<>();

        Player p2 = getAndVerifyPlayer(creator, args[0]);
        Player p3 = getAndVerifyPlayer(creator, args[1]);
        Player p4 = getAndVerifyPlayer(creator, args[2]);
        System.out.println("null check: " + (p2 == null) + " - " + (p3 == null) + " - " + (p4 == null) + Arrays.toString(args));
        if (p2 == null || p3 == null || p4 == null) {
            lang.sendMessage(creator, "ERROR.team.notonline");
            return false;
        }
        team1.add(creator);
        team1.add(p2);
        team2.add(p3);
        team2.add(p4);
        if (team1.size() != 2 || team2.size() != 2) {
            lang.sendMessage(creator, "ERROR.team.same-players");
            return false;
        }
        final Settings settings = settingManager.getSafely(creator);
        settings.setBet(0);
        String stringBet = "0";
        if (args.length == 4) {
            stringBet = args[3];
        }
        Integer bet = getBet(creator, stringBet);
        if (bet != null)
            settings.setBet(bet);
        settings.setAllyTeam(team1.stream().map(Entity::getUniqueId).collect(Collectors.toSet()));
        settings.setTargetTeam(team2.stream().map(Entity::getUniqueId).collect(Collectors.toSet()));
        return true;

    }

    private Integer getBet(Player player, String arg) {
        final int amount = NumberUtil.parseInt(arg).orElse(0);

        if (amount > 0 && config.isMoneyBettingEnabled()) {
            if (config.isMoneyBettingUsePermission() && !player.hasPermission(Permissions.MONEY_BETTING) && !player.hasPermission(Permissions.SETTING_ALL)) {
                lang.sendMessage(player, "ERROR.no-permission", "permission", Permissions.MONEY_BETTING);
                return null;
            }

            if (vault == null || vault.getEconomy() == null) {
                lang.sendMessage(player, "ERROR.setting.disabled-option", "option", lang.getMessage("GENERAL.betting"));
                return null;
            }

            if (!vault.getEconomy().has(player, amount)) {
                lang.sendMessage(player, "ERROR.command.not-enough-money");
                return null;
            }

            return amount;
        }
        return null;
    }

    private boolean handlePlayerData(Player sender, Player p) {
        final UserData user = userManager.get(p);
        if (user == null) {
            lang.sendMessage(sender, "ERROR.data.not-found", "name", p.getName());
            return false;
        }
        if (!sender.hasPermission(Permissions.ADMIN) && !user.canRequest()) {
            lang.sendMessage(sender, "ERROR.duel.requests-disabled", "name", p.getName());
            return false;
        }
        return true;
    }

    private Player getAndVerifyPlayer(Player creator, String name) {
        Player player = Bukkit.getPlayerExact(name);
        if (player == null || !player.isOnline()) {
            lang.sendMessage(creator, "ERROR.player.not-found", "name", name);
            return null;
        }

        if (arenaManager.isInMatch(player)) {
            lang.sendMessage(creator, "ERROR.duel.already-in-match.target", "name", player.getName());
            return null;
        }

        if (spectateManager.isSpectating(player)) {
            lang.sendMessage(creator, "ERROR.spectate.already-spectating.target", "name", player.getName());
            return null;
        }

        if (!handlePlayerData(creator, player)) {
            return null;
        }

        return player;
    }
}
