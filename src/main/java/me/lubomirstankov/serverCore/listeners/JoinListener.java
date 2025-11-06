package me.lubomirstankov.serverCore.listeners;

import me.lubomirstankov.serverCore.ServerCore;
import me.lubomirstankov.serverCore.utils.PlaceholderUtil;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class JoinListener implements Listener {
    private final ServerCore plugin;
    private static final String JOIN_MESSAGE_CONFIG_PATH = "join-message";
    private static final String DEFAULT_JOIN_MESSAGE = "<green>{player} has joined the server!</green>";

    public JoinListener(ServerCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(JOIN_MESSAGE_CONFIG_PATH);

        if (section == null) {
            return;
        }

        boolean enabled = section.getBoolean("enabled", true);

        if (!enabled) {
            event.joinMessage(null);
            return;
        }

        Player player = event.getPlayer();
        String worldName = player.getWorld().getName();

        // Check for per-world configuration
        ConfigurationSection perWorldSection = section.getConfigurationSection("per-world");
        ConfigurationSection worldConfig = null;

        if (perWorldSection != null && perWorldSection.contains(worldName)) {
            worldConfig = perWorldSection.getConfigurationSection(worldName);

            // Check if this world's messages are enabled
            if (worldConfig != null && !worldConfig.getBoolean("enabled", true)) {
                event.joinMessage(null);
                return;
            }
        }

        // Get message (per-world overrides default)
        String raw;
        if (worldConfig != null && worldConfig.contains("message")) {
            raw = worldConfig.getString("message", DEFAULT_JOIN_MESSAGE);
        } else {
            raw = section.getString("message", DEFAULT_JOIN_MESSAGE);
        }

        raw = raw.replace("{player}", player.getName());

        // Apply PlaceholderAPI placeholders if available
        raw = PlaceholderUtil.applyPlaceholdersWithBrackets(player, raw);

        Component comp = MiniMessage.miniMessage().deserialize(raw);
        plugin.getServer().broadcast(comp);
        event.joinMessage(null);

        // Handle join sound (per-world overrides default)
        ConfigurationSection soundSection;
        if (worldConfig != null && worldConfig.contains("sound")) {
            soundSection = worldConfig.getConfigurationSection("sound");
        } else {
            soundSection = section.getConfigurationSection("sound");
        }

        if (soundSection != null && soundSection.getBoolean("enabled", false)) {
            playSoundForAll(soundSection);
        }
    }

    private void playSoundForAll(ConfigurationSection soundSection) {
        try {
            String soundType = soundSection.getString("type", "ENTITY_PLAYER_LEVELUP");
            float volume = (float) soundSection.getDouble("volume", 0.75);
            float pitch = (float) soundSection.getDouble("pitch", 1.0);

            // Convert ENTITY_PLAYER_LEVELUP to entity.player.levelup format
            String soundKey = soundType.toLowerCase().replace("_", ".");

            Sound sound = Sound.sound(Key.key(soundKey), Sound.Source.MASTER, volume, pitch);

            for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
                onlinePlayer.playSound(sound);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid sound type in config: " + e.getMessage());
        }
    }
}
