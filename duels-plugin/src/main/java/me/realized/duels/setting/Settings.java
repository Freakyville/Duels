package me.realized.duels.setting;

import java.util.*;

import lombok.Getter;
import lombok.Setter;
import me.realized.duels.DuelsPlugin;
import me.realized.duels.arena.Arena;
import me.realized.duels.gui.settings.SettingsGui;
import me.realized.duels.kit.Kit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class Settings {

    private final DuelsPlugin plugin;
    private final SettingsGui gui;

    @Getter
    private Set<UUID> allyTeam;

    @Getter
    private Set<UUID> targetTeam;
    @Getter
    @Setter
    private Kit kit;
    @Getter
    @Setter
    private Arena arena;
    @Getter
    @Setter
    private int bet;
    @Getter
    @Setter
    private boolean itemBetting;
    @Getter
    private Map<UUID, CachedInfo> cache = new HashMap<>();

    public Settings(final DuelsPlugin plugin, final Player player) {
        this.plugin = plugin;
        this.gui = player != null ? plugin.getGuiListener().addGui(player, new SettingsGui(plugin)) : null;
    }

    public Settings(final DuelsPlugin plugin) {
        this(plugin, null);
    }

    public void reset() {
        targetTeam = null;
        allyTeam = null;
        kit = null;
        arena = null;
        bet = 0;
        itemBetting = false;
    }
    public void setAlly(UUID uuid) {
        setAllyTeam(Collections.singleton(uuid));
    }
    public void setAllyTeam(Set<UUID> allyTeam) {
        this.allyTeam = allyTeam;
    }

    public void setTargetTeam(Set<UUID> targetTeam){
        if (this.targetTeam != null) {
            reset();
        }
        this.targetTeam = targetTeam;
    }

    public void setTarget(final Player target) {
        System.out.println("setting target! " + (this.targetTeam == null));
        if (this.targetTeam != null && !this.targetTeam.contains(target.getUniqueId())) {
            reset();
        }
        this.targetTeam = Collections.singleton(target.getUniqueId());

    }

    public void updateGui(final Player player) {
        if (gui != null) {
            gui.update(player);
        }
    }

    public void openGui(final Player player) {
        gui.open(player);
    }

    public void setBaseLoc(final Player player) {
        cache.computeIfAbsent(player.getUniqueId(), result -> new CachedInfo()).setLocation(player.getLocation().clone());
    }

    public Location getBaseLoc(final Player player) {
        final CachedInfo info = cache.get(player.getUniqueId());

        if (info == null) {
            return null;
        }

        return info.getLocation();
    }

    public void setDuelzone(final Player player, final String duelzone) {
        cache.computeIfAbsent(player.getUniqueId(), result -> new CachedInfo()).setDuelzone(duelzone);
    }

    public String getDuelzone(final Player player) {
        final CachedInfo info = cache.get(player.getUniqueId());

        if (info == null) {
            return null;
        }

        return info.getDuelzone();
    }

    public void setGameMode(final Player player, final GameMode gameMode) {
        this.cache.computeIfAbsent(player.getUniqueId(), result -> new CachedInfo()).setGameMode(gameMode);
    }

    public GameMode getGameMode(final Player player) {
        final CachedInfo info = cache.get(player.getUniqueId());

        if (info == null) {
            return null;
        }

        return info.getGameMode();
    }

    // Don't copy the gui since it won't be required to start a match
    public Settings lightCopy() {
        final Settings copy = new Settings(plugin);
        copy.targetTeam = targetTeam;
        copy.allyTeam = allyTeam;
        copy.kit = kit;
        copy.arena = arena;
        copy.bet = bet;
        copy.itemBetting = itemBetting;
        copy.cache = new HashMap<>(cache);
        return copy;
    }
}
