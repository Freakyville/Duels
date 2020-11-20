package me.realized.duels.gui.settings.buttons;

import me.realized.duels.DuelsPlugin;
import me.realized.duels.gui.BaseButton;
import me.realized.duels.setting.Settings;
import me.realized.duels.util.compat.Items;
import me.realized.duels.util.inventory.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class RequestSendButton extends BaseButton {

    public RequestSendButton(final DuelsPlugin plugin) {
        super(plugin, ItemBuilder.of(Items.GREEN_PANE.clone()).name(plugin.getLang().getMessage("GUI.settings.buttons.send.name")).build());
    }

    @Override
    public void onClick(final Player player) {
        final Settings settings = settingManager.getSafely(player);

        if (settings.getTargetTeam() == null) {
            settings.reset();
            player.closeInventory();
            return;
        }

        if (settings.getTargetTeam().size() == 1) {
            final Player target = Bukkit.getPlayer(settings.getTargetTeam().stream().findFirst().get());

            if (target == null) {
                settings.reset();
                player.closeInventory();
                lang.sendMessage(player, "ERROR.player.no-longer-online");
                return;
            }

            if (!config.isUseOwnInventoryEnabled() && settings.getKit() == null) {
                player.closeInventory();
                return;
            }

            player.closeInventory();
            requestManager.send(player, target, settings);
        } else {
            requestManager.send(player, settings.getAllyTeam(), settings.getTargetTeam(), settings);
        }
    }
}
