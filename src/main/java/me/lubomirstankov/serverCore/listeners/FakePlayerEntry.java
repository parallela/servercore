package me.lubomirstankov.serverCore.listeners;

import com.comphenix.protocol.wrappers.EnumWrappers;
import net.kyori.adventure.text.Component;

import java.util.UUID;

/**
 * Immutable record representing a fake player entry for the tab list.
 * Uses Java 17+ record syntax for clean, thread-safe data storage.
 *
 * @param uuid Unique identifier for this fake player (must be deterministic)
 * @param name Short name shown in sorting (max 16 chars, use prefix for positioning)
 * @param displayName Formatted display name with MiniMessage support
 * @param ping Latency in milliseconds (0 = full bars, 1000+ = red bars)
 * @param gameMode Game mode icon shown in tab list
 */
public record FakePlayerEntry(
    UUID uuid,
    String name,
    Component displayName,
    int ping,
    EnumWrappers.NativeGameMode gameMode
) {
    /**
     * Compact constructor for validation - runs before field assignment
     */
    public FakePlayerEntry {
        if (uuid == null) {
            throw new IllegalArgumentException("UUID cannot be null");
        }
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Name cannot be null or empty");
        }
        if (name.length() > 16) {
            throw new IllegalArgumentException("Name cannot exceed 16 characters: " + name);
        }
        if (displayName == null) {
            throw new IllegalArgumentException("Display name cannot be null");
        }
        if (gameMode == null) {
            throw new IllegalArgumentException("Game mode cannot be null");
        }
        if (ping < 0) {
            throw new IllegalArgumentException("Ping cannot be negative: " + ping);
        }
    }

    /**
     * Factory method for creating a fake player entry with default values
     *
     * @param uuid Unique identifier
     * @param name Short name (max 16 chars)
     * @param displayName Formatted display name
     * @return New fake player entry with default ping (0) and SURVIVAL mode
     */
    public static FakePlayerEntry create(UUID uuid, String name, Component displayName) {
        return new FakePlayerEntry(
            uuid,
            name,
            displayName,
            0,
            EnumWrappers.NativeGameMode.SURVIVAL
        );
    }
}

