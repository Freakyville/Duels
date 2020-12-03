package me.realized.duels.util;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MethodMessage implements Listener {
    private static Map<UUID, Runnable> methods = new HashMap<>();
    private final static String CMD_IDENTIFIER = "methodmessage";
    private static JavaPlugin plugin;


    public MethodMessage(JavaPlugin plugin) {
        MethodMessage.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public static UUID sendMessage(Player player, String message, Runnable methodToRun, int timeout) {
        return sendMessage(player, message, "", methodToRun, timeout);
    }

    public static UUID sendMessage(Player player, String message, String hover, Runnable methodToRun, int timeout) {
        UUID uuid = sendMessage(player, message, hover, methodToRun);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            System.out.println("removing from methods - " + uuid);
            methods.remove(uuid);
        }, timeout * 20);
        return uuid;
    }
    public static UUID sendMessage(Player player, String message, String hover, Runnable methodToRun) {
        UUID id = UUID.randomUUID();
        System.out.println(player.getName() + " - Added id: " + id.toString());
        methods.put(id, methodToRun);
        TextComponent textComponent = new TextComponent(message);
        textComponent.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                "/" + CMD_IDENTIFIER + " :" + id.toString()));
        if (hover != null && !hover.isEmpty()) {
            TextComponent[] hoverComponent = {new TextComponent(hover)};
            textComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverComponent ));
        }
        player.spigot().sendMessage(textComponent);
        return id;
    }
    public static UUID sendMessage(Player player, String message, Runnable methodToRun) {
        return sendMessage(player, message, "", methodToRun);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void preprocessedCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (!message.startsWith("/" + CMD_IDENTIFIER))
            return;
        System.out.println("message: " + message);
        String[] split = message.split(" :");
        if (split.length != 2)
            return;
        UUID id = UUID.fromString(split[1]);
        System.out.println("contains: " + methods.containsKey(id) + " - " + Arrays.toString(split) + " - " + methods.size());
        methods.keySet().forEach(uuid -> {
            System.out.println("uuid in methods: " + uuid.toString());
        });
        if (methods.containsKey(id)) {
            methods.get(id).run();
            System.out.println("removing id: " + id);
            methods.remove(id);
            event.setCancelled(true);
        }
    }
}
