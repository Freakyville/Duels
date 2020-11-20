package me.realized.duels.request;

import lombok.Getter;
import me.realized.duels.setting.Settings;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class TeamRequest {
    @Getter
    private final Set<UUID> team1;
    @Getter
    private final Set<UUID> team2;

    @Getter
    private final Set<UUID> accepted = new HashSet<>();

    @Getter
    private final Settings settings;
    @Getter
    private long lastUpdated;

    public TeamRequest(Set<UUID> team1, Set<UUID> team2, Settings settings) {
        this.team1 = team1;
        this.team2 = team2;
        this.settings = settings;
        this.lastUpdated = System.currentTimeMillis();
    }

    public void addAccepted(UUID uuid) {
        accepted.add(uuid);
        lastUpdated = System.currentTimeMillis();
    }
    public int getAcceptRequirement() {
        return team1.size() + team2.size();
    }
}
