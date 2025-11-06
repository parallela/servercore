package me.lubomirstankov.serverCore.listeners;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import me.lubomirstankov.serverCore.ServerCore;
import me.lubomirstankov.serverCore.utils.PlaceholderUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Handles server list ping events to customize the MOTD and player hover
 * Supports multi-line MOTD and custom player hover messages
 */
public class ServerMotdListener implements Listener {
    private final ServerCore plugin;
    private static final String CONFIG_PATH = "motd.server";
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    public ServerMotdListener(ServerCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onServerListPing(PaperServerListPingEvent event) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(CONFIG_PATH);

        if (section == null || !section.getBoolean("enabled", true)) {
            return;
        }

        // Set custom MOTD
        setCustomMotd(event, section);

        // Set custom player hover
        setCustomPlayerHover(event, section);

        // Set custom max players if configured
        int customMaxPlayers = section.getInt("custom-max-players", -1);
        if (customMaxPlayers > 0) {
            event.setMaxPlayers(customMaxPlayers);
        }
    }

    /**
     * Set custom MOTD from config
     */
    private void setCustomMotd(PaperServerListPingEvent event, ConfigurationSection section) {
        List<String> motdLines = section.getStringList("motd-lines");

        if (motdLines.isEmpty()) {
            return;
        }

        int online = event.getNumPlayers();
        int max = event.getMaxPlayers();

        // Build MOTD (first line and second line)
        String firstLine = !motdLines.isEmpty() ? motdLines.get(0) : "";
        String secondLine = motdLines.size() > 1 ? motdLines.get(1) : "";

        // Replace placeholders
        firstLine = replacePlaceholders(firstLine, online, max);
        secondLine = replacePlaceholders(secondLine, online, max);

        // Combine lines with newline
        String fullMotd = secondLine.isEmpty() ? firstLine : firstLine + "\n" + secondLine;

        Component motdComponent = MINI_MESSAGE.deserialize(fullMotd);
        event.motd(motdComponent);
    }

    /**
     * Set custom player hover text using Paper's protocol API
     */
    private void setCustomPlayerHover(PaperServerListPingEvent event, ConfigurationSection section) {
        ConfigurationSection hoverSection = section.getConfigurationSection("player-hover");

        if (hoverSection == null || !hoverSection.getBoolean("enabled", true)) {
            return;
        }

        List<String> hoverLines = hoverSection.getStringList("lines");

        if (hoverLines.isEmpty()) {
            return;
        }

        int online = event.getNumPlayers();
        int max = event.getMaxPlayers();

        // Build custom player sample for hover
        try {
            List<PaperServerListPingEvent.ListedPlayerInfo> playerInfoList = new ArrayList<>();

            // Create fake player info for hover text (max 12 lines for good display)
            int linesToShow = Math.min(hoverLines.size(), 12);

            for (int i = 0; i < linesToShow; i++) {
                String line = replacePlaceholders(hoverLines.get(i), online, max);

                // Strip MiniMessage tags and limit to 16 characters for Minecraft username limit
                String displayName = stripMiniMessage(line);
                if (displayName.length() > 16) {
                    displayName = displayName.substring(0, 16);
                }

                // Create ListedPlayerInfo using the name and a random UUID
                PaperServerListPingEvent.ListedPlayerInfo playerInfo =
                    new PaperServerListPingEvent.ListedPlayerInfo(displayName, UUID.randomUUID());
                playerInfoList.add(playerInfo);
            }

            // Set the custom player sample
            event.getListedPlayers().clear();
            event.getListedPlayers().addAll(playerInfoList);

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to set custom player hover: " + e.getMessage());
        }
    }

    /**
     * Replace common placeholders in text
     */
    private String replacePlaceholders(String text, int online, int max) {
        text = text
                .replace("{online}", String.valueOf(online))
                .replace("{max}", String.valueOf(max))
                .replace("{server}", plugin.getServer().getName())
                .replace("{version}", plugin.getServer().getVersion());

        // Apply PlaceholderAPI placeholders (without player context for server MOTD)
        text = PlaceholderUtil.applyPlaceholders(text);

        return text;
    }

    /**
     * Strip MiniMessage tags from text for plain display
     * This is a simple implementation - for hover we want clean text
     */
    private String stripMiniMessage(String text) {
        // Remove MiniMessage tags using regex
        return text.replaceAll("<[^>]*>", "");
    }
}

