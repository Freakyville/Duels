package me.realized.duels.util;

import lombok.Setter;
import me.realized.duels.config.Lang;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class TeamUtil {
    @Setter
    private static Lang lang;

    public static String getTeamString(Set<UUID> team) {
        Set<Player> teamOfPlayers = new HashSet<>();
        for (UUID uuid : team) {
            Player target = Bukkit.getPlayer(uuid);
            if (target == null) {
                return null;
            }
            teamOfPlayers.add(target);
        }
        return getTeamStringByPlayers(teamOfPlayers);
    }
    public static String getTeamStringByPlayers(Set<Player> team) {
        StringBuilder sb = new StringBuilder();
        return team.stream().map(i -> i.getName()).collect(Collectors.joining(", "));
    }
}
