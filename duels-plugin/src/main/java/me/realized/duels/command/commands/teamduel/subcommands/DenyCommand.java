package me.realized.duels.command.commands.teamduel.subcommands;

import me.realized.duels.DuelsPlugin;
import me.realized.duels.api.event.request.RequestDenyEvent;
import me.realized.duels.command.BaseCommand;
import me.realized.duels.request.Request;
import me.realized.duels.request.TeamRequest;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

public class DenyCommand extends BaseCommand {
    public DenyCommand(DuelsPlugin plugin) {
        super(plugin, "deny", "deny [player]", "Declines a teamduel request.", 2, true);
    }
    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        final Player player = (Player) sender;
        final Player target = Bukkit.getPlayerExact(args[1]);

        if (target == null || !player.canSee(target)) {
            lang.sendMessage(sender, "ERROR.player.not-found", "name", args[1]);
            return;
        }

        final TeamRequest request;

        if ((request = requestManager.removeTeamRequest(target, player)) == null) {
            lang.sendMessage(sender, "ERROR.duel.no-request", "name", target.getName());
            return;
        }

        /*final RequestDenyEvent event = new RequestDenyEvent(player, target, request);
        plugin.getServer().getPluginManager().callEvent(event);*/

        notifyAccepters(player.getName(), request.getAccepted());
        lang.sendMessage(player, "COMMAND.duel.request.deny.receiver", "name", target.getName());
        //lang.sendMessage(target, "COMMAND.duel.request.deny.sender", "name", player.getName());
    }

    private void notifyAccepters(String accepterName, Set<UUID> accepters) {
        accepters.forEach(uuid -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null)
                return;
            lang.sendMessage(player, "COMMAND.duel.request.deny.sender",
                    "name", accepterName);
        });
    }
}
