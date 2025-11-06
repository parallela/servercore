package me.lubomirstankov.serverCore.listeners;

import me.lubomirstankov.serverCore.ServerCore;
import me.lubomirstankov.serverCore.utils.PlaceholderUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;

/**
 * Displays a Message of the Day (MOTD) to players when they join
 * Supports multi-line messages with MiniMessage formatting
 */
public class MotdListener implements Listener {
    private final ServerCore plugin;
    private static final String CONFIG_PATH = "motd.player";
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    public MotdListener(ServerCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(CONFIG_PATH);

        if (section == null || !section.getBoolean("enabled", true)) {
            return;
        }

        Player player = event.getPlayer();

        // Check if there's a delay configured
        int delayTicks = section.getInt("delay-ticks", 20); // Default 1 second

        // Schedule the MOTD display
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                displayMotd(player, section);
            }
        }, delayTicks);
    }

    /**
     * Display the MOTD to the player
     */
    private void displayMotd(Player player, ConfigurationSection section) {
        List<String> motdLines = section.getStringList("lines");

        if (motdLines.isEmpty()) {
            // Fallback to single line if list is empty
            String singleLine = section.getString("message", "");
            if (!singleLine.isEmpty()) {
                motdLines = List.of(singleLine);
            }
        }

        // Send each line with placeholder replacement
        for (String line : motdLines) {
            String formatted = line
                    .replace("{player}", player.getName())
                    .replace("{displayname}", player.displayName().toString())
                    .replace("{world}", player.getWorld().getName())
                    .replace("{online}", String.valueOf(plugin.getServer().getOnlinePlayers().size()))
                    .replace("{max}", String.valueOf(plugin.getServer().getMaxPlayers()));

            // Apply PlaceholderAPI placeholders if available
            formatted = PlaceholderUtil.applyPlaceholdersWithBrackets(player, formatted);

            Component component = MINI_MESSAGE.deserialize(formatted);
            player.sendMessage(component);
        }
    }
}

