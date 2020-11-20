package me.realized.duels.gui.settings.buttons;

import me.realized.duels.DuelsPlugin;
import me.realized.duels.gui.BaseButton;
import me.realized.duels.setting.Settings;
import me.realized.duels.util.TeamUtil;
import me.realized.duels.util.compat.Items;
import me.realized.duels.util.inventory.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

public class RequestDetailsButton extends BaseButton {

    public RequestDetailsButton(final DuelsPlugin plugin) {
        super(plugin, ItemBuilder.of(Items.SIGN).name(plugin.getLang().getMessage("GUI.settings.buttons.details.name")).build());
    }

    @Override
    public void update(final Player player) {
        final Settings settings = settingManager.getSafely(player);

        String targetLore = TeamUtil.getTeamString(settings.getTargetTeam());
        String allyLore = TeamUtil.getTeamString(settings.getAllyTeam());


        final String lore = lang.getMessage("GUI.settings.buttons.details.lore",
            "opponent", targetLore,
            "ally", allyLore,
            "kit", settings.getKit() != null ? settings.getKit().getName() : lang.getMessage("GENERAL.not-selected"),
            "arena", settings.getArena() != null ? settings.getArena().getName() : lang.getMessage("GENERAL.random"),
            "item_betting", settings.isItemBetting() ? lang.getMessage("GENERAL.enabled") : lang.getMessage("GENERAL.disabled"),
            "bet_amount", settings.getBet()
        );
        setLore(lore.split("\n"));
    }

}
