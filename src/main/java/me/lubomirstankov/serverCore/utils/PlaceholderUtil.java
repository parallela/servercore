package me.lubomirstankov.serverCore.utils;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Utility class for PlaceholderAPI integration
 * Provides safe methods to use PlaceholderAPI with fallback support
 */
public class PlaceholderUtil {
    private static boolean placeholderAPIEnabled = false;

    /**
     * Initialize PlaceholderAPI integration
     * Call this during plugin initialization
     */
    public static void initialize() {
        placeholderAPIEnabled = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
        if (placeholderAPIEnabled) {
            Bukkit.getLogger().info("[ServerCore] PlaceholderAPI found! Placeholder support enabled.");
        } else {
            Bukkit.getLogger().info("[ServerCore] PlaceholderAPI not found. Using built-in placeholders only.");
        }
    }

    /**
     * Check if PlaceholderAPI is available
     * @return true if PlaceholderAPI is enabled
     */
    public static boolean isEnabled() {
        return placeholderAPIEnabled;
    }

    /**
     * Apply PlaceholderAPI placeholders to text for a specific player
     * If PlaceholderAPI is not available, returns the original text
     *
     * @param player the player context
     * @param text the text with placeholders
     * @return the text with placeholders replaced
     */
    public static String applyPlaceholders(Player player, String text) {
        if (placeholderAPIEnabled && player != null) {
            try {
                return PlaceholderAPI.setPlaceholders(player, text);
            } catch (Exception e) {
                Bukkit.getLogger().warning("[ServerCore] Error applying PlaceholderAPI placeholders: " + e.getMessage());
                return text;
            }
        }
        return text;
    }

    /**
     * Apply PlaceholderAPI placeholders to text without player context
     * If PlaceholderAPI is not available, returns the original text
     *
     * @param text the text with placeholders
     * @return the text with placeholders replaced
     */
    public static String applyPlaceholders(String text) {
        if (placeholderAPIEnabled) {
            try {
                return PlaceholderAPI.setPlaceholders(null, text);
            } catch (Exception e) {
                Bukkit.getLogger().warning("[ServerCore] Error applying PlaceholderAPI placeholders: " + e.getMessage());
                return text;
            }
        }
        return text;
    }

    /**
     * Apply placeholders with bracket notation support
     * Supports both %placeholder% and {placeholder} formats
     *
     * @param player the player context
     * @param text the text with placeholders
     * @return the text with placeholders replaced
     */
    public static String applyPlaceholdersWithBrackets(Player player, String text) {
        if (placeholderAPIEnabled && player != null) {
            try {
                // First apply PlaceholderAPI placeholders
                text = PlaceholderAPI.setBracketPlaceholders(player, text);
                text = PlaceholderAPI.setPlaceholders(player, text);
            } catch (Exception e) {
                Bukkit.getLogger().warning("[ServerCore] Error applying PlaceholderAPI placeholders: " + e.getMessage());
            }
        }
        return text;
    }
}

