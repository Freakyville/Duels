package me.realized.duels.command.commands.teamduel.subcommands;

import me.realized.duels.DuelsPlugin;
import me.realized.duels.command.BaseCommand;
import me.realized.duels.hook.hooks.CombatLogXHook;
import me.realized.duels.hook.hooks.CombatTagPlusHook;
import me.realized.duels.hook.hooks.PvPManagerHook;
import me.realized.duels.hook.hooks.worldguard.WorldGuardHook;
import me.realized.duels.request.Request;
import me.realized.duels.request.TeamRequest;
import me.realized.duels.setting.Settings;
import me.realized.duels.util.inventory.InventoryUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class AcceptCommand extends BaseCommand {

    private final CombatTagPlusHook combatTagPlus;
    private final PvPManagerHook pvpManager;
    private final CombatLogXHook combatLogX;
    private final WorldGuardHook worldGuard;

    public AcceptCommand(DuelsPlugin plugin) {
        super(plugin, "accept", "accept [player]", "Accepts a team duel request.", 2, true);
        this.combatTagPlus = hookManager.getHook(CombatTagPlusHook.class);
        this.pvpManager = hookManager.getHook(PvPManagerHook.class);
        this.combatLogX = plugin.getHookManager().getHook(CombatLogXHook.class);
        this.worldGuard = hookManager.getHook(WorldGuardHook.class);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        final Player player = (Player) sender;

        if (config.isRequiresClearedInventory() && InventoryUtil.hasItem(player)) {
            lang.sendMessage(sender, "ERROR.duel.inventory-not-empty");
            return;
        }

        GameMode gameMode = null;

        if (config.isPreventCreativeMode() && (gameMode = player.getGameMode()) == GameMode.CREATIVE) {
            lang.sendMessage(sender, "ERROR.duel.in-creative-mode");
            return;
        }
        if ((combatTagPlus != null && combatTagPlus.isTagged(player))
                || (pvpManager != null && pvpManager.isTagged(player))
                || (combatLogX != null && combatLogX.isTagged(player))) {
            lang.sendMessage(sender, "ERROR.duel.is-tagged");
            return;
        }

        String duelzone = null;

        if (worldGuard != null && config.isDuelzoneEnabled() && (duelzone = worldGuard.findDuelZone(player)) == null) {
            lang.sendMessage(sender, "ERROR.duel.not-in-duelzone", "regions", config.getDuelzones());
            return;
        }

        if (arenaManager.isInMatch(player)) {
            lang.sendMessage(sender, "ERROR.duel.already-in-match.sender");
            return;
        }

        if (spectateManager.isSpectating(player)) {
            lang.sendMessage(sender, "ERROR.spectate.already-spectating.sender");
            return;
        }

        final Player target = Bukkit.getPlayerExact(args[1]);

        if (target == null) {
            lang.sendMessage(sender, "ERROR.player.not-found", "name", args[1]);
            return;
        }

        final TeamRequest request = requestManager.getTeamRequest(target, player);

        if (request == null) {
            lang.sendMessage(sender, "ERROR.duel.no-request", "name", target.getName());
            return;
        }
        if (arenaManager.isInMatch(target)) {
            lang.sendMessage(sender, "ERROR.duel.already-in-match.target", "name", target.getName());
            return;
        }
        if (spectateManager.isSpectating(target)) {
            lang.sendMessage(sender, "ERROR.spectate.already-spectating.target", "name", target.getName());
            return;
        }

        final Settings settings = request.getSettings();
        final String kit = settings.getKit() != null ? settings.getKit().getName() : lang.getMessage("GENERAL.not-selected");
        final String arena = settings.getArena() != null ? settings.getArena().getName() : lang.getMessage("GENERAL.random");
        final double bet = settings.getBet();

        lang.sendMessage(player, "COMMAND.duel.request.team.accept.receiver",
                "name", target.getName(), "kit", kit, "arena", arena, "bet_amount", bet);
        if (!request.getAccepted().contains(player.getUniqueId())) {
            request.addAccepted(player.getUniqueId());
            notifyAccepters(player, request.getAccepted(), arena, kit, bet, request.getAcceptRequirement());
        }
        if (request.getAccepted().size() == request.getAcceptRequirement()) {
            requestManager.removeTeamRequest(player);
            Set<Player> allyTeam = request.getTeam1().stream().map(Bukkit::getPlayer).collect(Collectors.toSet());
            Set<Player> targetTeam = request.getTeam2().stream().map(Bukkit::getPlayer).collect(Collectors.toSet());
            if (allyTeam.contains(null) || targetTeam.contains(null)) {
                lang.sendMessage(sender, "COMMAND.duel.request.team.logged-off");
                return;
            }

            duelManager.startTeamMatch(player, allyTeam, targetTeam, settings);
        }

    }
    private void notifyAccepters(Player accepter, Set<UUID> accepters, String arena, String kit, double bet, int required) {
        accepters.forEach(uuid -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null)
                return;
            if (accepter.getUniqueId().equals(uuid)) {
                return;
            }
            lang.sendMessage(player, "COMMAND.duel.request.team.accept.accepter",
                    "name", accepter.getName(),
                    "kit", kit,
                    "arena", arena,
                    "bet_amount", bet,
                    "required", (required),
                    "count", (accepters.size()));
        });
    }
}
