package me.lubomirstankov.serverCore.listeners;

import com.comphenix.protocol.wrappers.EnumWrappers;
import me.lubomirstankov.serverCore.ServerCore;
import me.lubomirstankov.serverCore.utils.PlaceholderUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.*;

/**
 * Tab List Manager - Displays header/footer stats and fake players in the tab list.
 * Fully compatible with Minecraft 1.21.x + ProtocolLib 5.4.0.
 *
 * Features:
 * - Dynamic header/footer with live server stats
 * - Fake player entries for displaying custom information
 * - Per-viewer or global fake player modes
 * - Flicker-free updates using UPDATE_DISPLAY_NAME action
 * - Proper cleanup on player quit and plugin disable
 */
public class TabListManager implements Listener {
    private final ServerCore plugin;
    private final FakePlayerManager fakePlayerManager;

    private static final String CONFIG_PATH = "tab-list";
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final DecimalFormat TPS_FORMAT = new DecimalFormat("0.00");
    
    private BukkitRunnable updateTask;
    private final long pluginStartTime;

    public TabListManager(ServerCore plugin) {
        this.plugin = plugin;
        this.fakePlayerManager = new FakePlayerManager(plugin);
        this.pluginStartTime = System.currentTimeMillis();
        startTabUpdateTask();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(CONFIG_PATH);
        if (section == null || !section.getBoolean("enabled", true)) {
            return;
        }

        // Delay to ensure player connection is fully initialized
        // Critical for 1.21.x - packets sent too early may be ignored
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                updateTabList(event.getPlayer());
                updateFakePlayers(event.getPlayer());
            }
        }, 20L);  // 20 ticks = 1 second delay
    }

    /**
     * Remove fake players when a player quits to prevent memory leaks
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        fakePlayerManager.removeAllFakePlayers(event.getPlayer());
    }

    private void startTabUpdateTask() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(CONFIG_PATH);
        if (section == null || !section.getBoolean("enabled", true)) {
            return;
        }

        int updateInterval = section.getInt("update-interval-ticks", 20);

        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                ConfigurationSection currentSection = plugin.getConfig().getConfigurationSection(CONFIG_PATH);
                if (currentSection == null || !currentSection.getBoolean("enabled", true)) {
                    return;
                }

                for (Player player : Bukkit.getOnlinePlayers()) {
                    // Update header/footer
                    updateTabList(player);

                    // Update fake players (flicker-free updates)
                    updateFakePlayers(player);
                }
            }
        };

        updateTask.runTaskTimer(plugin, 20L, updateInterval);
    }

    private void updateTabList(Player player) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(CONFIG_PATH);
        if (section == null) return;

        String headerText = buildText(section.getStringList("header"), player);
        Component header = MINI_MESSAGE.deserialize(headerText);

        String footerText = buildText(section.getStringList("footer"), player);
        Component footer = MINI_MESSAGE.deserialize(footerText);

        player.sendPlayerListHeaderAndFooter(header, footer);
    }

    private String buildText(List<String> lines, Player player) {
        if (lines.isEmpty()) return "";

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            builder.append(replacePlaceholders(lines.get(i), player));
            if (i < lines.size() - 1) {
                builder.append("\n");
            }
        }
        return builder.toString();
    }

    private String replacePlaceholders(String text, Player player) {
        int onlinePlayers = Bukkit.getOnlinePlayers().size();
        int maxPlayers = Bukkit.getMaxPlayers();

        double tps = getTPS();
        String tpsFormatted = TPS_FORMAT.format(tps);
        String tpsColor = getTpsColor(tps);

        long uptimeMillis = System.currentTimeMillis() - pluginStartTime;
        String uptime = formatUptime(uptimeMillis);

        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMemory = runtime.maxMemory() / (1024 * 1024);

        text = text
            .replace("{player}", player.getName())
            .replace("{displayname}", player.displayName().toString())
            .replace("{online}", String.valueOf(onlinePlayers))
            .replace("{max}", String.valueOf(maxPlayers))
            .replace("{world}", player.getWorld().getName())
            .replace("{tps}", tpsFormatted)
            .replace("{tps_color}", tpsColor)
            .replace("{ping}", String.valueOf(player.getPing()))
            .replace("{uptime}", uptime)
            .replace("{memory_used}", String.valueOf(usedMemory))
            .replace("{memory_max}", String.valueOf(maxMemory))
            .replace("{server}", Bukkit.getServer().getName())
            .replace("{version}", Bukkit.getServer().getVersion());

        text = PlaceholderUtil.applyPlaceholdersWithBrackets(player, text);
        return text;
    }

    private double getTPS() {
        try {
            return Bukkit.getTPS()[0];
        } catch (Exception e) {
            return 20.0;
        }
    }

    private String getTpsColor(double tps) {
        if (tps >= 19.0) return "<green>";
        if (tps >= 17.0) return "<yellow>";
        if (tps >= 15.0) return "<gold>";
        return "<red>";
    }

    private String formatUptime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) return days + "d " + (hours % 24) + "h";
        if (hours > 0) return hours + "h " + (minutes % 60) + "m";
        if (minutes > 0) return minutes + "m " + (seconds % 60) + "s";
        return seconds + "s";
    }

    /**
     * Updates fake players in the tab list for a specific player.
     * Parses config, creates FakePlayerEntry objects, and sends packets.
     *
     * Supports two layout modes:
     * - "auto": Single list of fake players (traditional mode)
     * - "columns": Left and right column positioning
     *
     * Uses update-only mode for existing fake players to prevent flickering.
     * Fake players are shown to ALL online players (global view).
     */
    private void updateFakePlayers(Player player) {
        ConfigurationSection fakeSection = plugin.getConfig()
            .getConfigurationSection(CONFIG_PATH + ".fake-players");

        if (fakeSection == null || !fakeSection.getBoolean("enabled", false)) {
            // Fake players disabled - remove any existing ones
            Set<UUID> existing = fakePlayerManager.getFakePlayersForViewer(player);
            if (!existing.isEmpty()) {
                fakePlayerManager.removeAllFakePlayers(player);
            }
            return;
        }

        String layout = fakeSection.getString("layout", "auto").toLowerCase();
        List<FakePlayerEntry> entries;

        if ("three-columns".equals(layout)) {
            // Three-column layout: left, center, and right columns
            entries = parseThreeColumnEntries(player, fakeSection);
        } else if ("columns".equals(layout)) {
            // Two-column layout: left and right columns
            entries = parseColumnBasedEntries(player, fakeSection);
        } else {
            // Auto layout: single list (traditional)
            List<String> playerLines = fakeSection.getStringList("players");
            entries = parseFakePlayerEntries(player, playerLines, 0);
        }

        if (entries.isEmpty()) {
            return;
        }

        // Get currently shown fake players
        Set<UUID> currentFakePlayers = fakePlayerManager.getFakePlayersForViewer(player);
        Set<UUID> newFakePlayers = new HashSet<>();

        // Add or update fake players
        for (FakePlayerEntry entry : entries) {
            newFakePlayers.add(entry.uuid());

            if (currentFakePlayers.contains(entry.uuid())) {
                // Update existing (no flicker)
                fakePlayerManager.updateFakePlayer(player, entry);
            } else {
                // Add new
                fakePlayerManager.addFakePlayer(player, entry);
            }
        }

        // Remove fake players that are no longer in config
        for (UUID existingUuid : currentFakePlayers) {
            if (!newFakePlayers.contains(existingUuid)) {
                fakePlayerManager.removeFakePlayer(player, existingUuid);
            }
        }
    }

    /**
     * Parses fake players for column-based layout.
     * Creates entries for left column (0-19) and right column (20-39).
     * Adds spacers to push content to the correct column.
     *
     * Tab list column layout:
     * - Positions 0-19: Left column
     * - Positions 20-39: Right column
     * - Positions 40-59: Third column (not used yet)
     * - Positions 60-79: Fourth column (not used yet)
     *
     * IMPORTANT: Uses "!" prefix for left column to sort before real players,
     * allowing real players to appear in the center/right naturally.
     *
     * @param viewer The player viewing these fake players
     * @param fakeSection The config section containing fake-players settings
     * @return List of fake player entries with proper column positioning
     */
    private List<FakePlayerEntry> parseColumnBasedEntries(Player viewer, ConfigurationSection fakeSection) {
        List<FakePlayerEntry> allEntries = new ArrayList<>();

        // Parse left column entries (using "!" prefix to sort BEFORE real players)
        List<String> leftLines = fakeSection.getStringList("left-column");
        List<FakePlayerEntry> leftEntries = parseFakePlayerEntriesForColumn(viewer, leftLines, 0, "!");
        allEntries.addAll(leftEntries);

        // Parse right column entries (using "~" prefix to sort AFTER real players)
        List<String> rightLines = fakeSection.getStringList("right-column");
        List<FakePlayerEntry> rightEntries = parseFakePlayerEntriesForColumn(viewer, rightLines, 20, "~");

        // Add spacer entries to reach position 20 if needed
        // This ensures right column content appears on the right side
        int spacersNeeded = 20 - allEntries.size();
        for (int i = 0; i < spacersNeeded; i++) {
            int spacerIndex = allEntries.size();
            UUID uuid = UUID.nameUUIDFromBytes(
                ("Spacer-" + spacerIndex).getBytes(StandardCharsets.UTF_8)
            );

            // Create invisible spacer (empty display name)
            // Using "!" prefix to keep it in the left column
            FakePlayerEntry spacer = new FakePlayerEntry(
                uuid,
                String.format("!%02d", spacerIndex),  // ! prefix to sort before real players
                Component.empty(),  // Empty display name = invisible
                0,
                EnumWrappers.NativeGameMode.SURVIVAL
            );
            allEntries.add(spacer);
        }

        // Add right column entries
        allEntries.addAll(rightEntries);

        return allEntries;
    }

    /**
     * Parses fake players for three-column layout.
     * Creates entries for left column (0-19), center column (20-39), and right column (40-59).
     * The center column is mostly invisible spacers to allow real players to show there.
     *
     * Tab list column layout for 3 columns:
     * - Positions 0-19: Left column (SERVER INFO)
     * - Positions 20-39: Center column (REAL PLAYERS with title)
     * - Positions 40-59: Right column (YOUR STATS)
     *
     * IMPORTANT: Minecraft 1.21.x sorts tab list entries ALPHABETICALLY by player name,
     * then distributes them into columns. To ensure proper ordering:
     * - Left column uses names starting with "!" (sorts BEFORE A-Z player names)
     * - Center column title uses "!" (appears BEFORE real players)
     * - Center column spacers use "~" (sorts AFTER A-Z, fills remaining slots)
     * - Right column uses "~" (sorts AFTER real players)
     *
     * Result: Left content | Title -> Real Players | Right content
     *
     * @param viewer The player viewing these fake players
     * @param fakeSection The config section containing fake-players settings
     * @return List of fake player entries with proper three-column positioning
     */
    private List<FakePlayerEntry> parseThreeColumnEntries(Player viewer, ConfigurationSection fakeSection) {
        List<FakePlayerEntry> allEntries = new ArrayList<>();

        // Parse left column entries (0-19)
        // Uses "!" prefix to sort BEFORE real player names (A-Z)
        List<String> leftLines = fakeSection.getStringList("left-column");
        List<FakePlayerEntry> leftEntries = parseFakePlayerEntriesForColumn(viewer, leftLines, 0, "!");
        allEntries.addAll(leftEntries);

        // Add spacers to fill left column to position 19
        int leftSpacersNeeded = 20 - allEntries.size();
        for (int i = 0; i < leftSpacersNeeded; i++) {
            int spacerIndex = allEntries.size();
            UUID uuid = UUID.nameUUIDFromBytes(
                ("LeftSpacer-" + spacerIndex).getBytes(StandardCharsets.UTF_8)
            );

            FakePlayerEntry spacer = new FakePlayerEntry(
                uuid,
                String.format("!%02d", spacerIndex),  // ! sorts BEFORE real players (A-Z)
                Component.empty(),  // Invisible
                0,
                EnumWrappers.NativeGameMode.SURVIVAL
            );
            allEntries.add(spacer);
        }

        // Parse center column entries (20-39)
        // Uses "!" prefix to sort BEFORE real player names (A-Z)
        // This makes the title/header appear FIRST, then real players appear below it
        List<String> centerLines = fakeSection.getStringList("center-column");
        List<FakePlayerEntry> centerEntries = parseFakePlayerEntriesForColumn(viewer, centerLines, 20, "!");
        allEntries.addAll(centerEntries);

        // Check if we need to add "...and X more" counter
        int maxCenterPlayers = fakeSection.getInt("max-center-players", -1);
        if (maxCenterPlayers > 0) {
            int totalPlayers = Bukkit.getOnlinePlayers().size();
            if (totalPlayers > maxCenterPlayers) {
                int remainingPlayers = totalPlayers - maxCenterPlayers;

                // Add spacers to position the counter near the bottom
                int currentSize = allEntries.size();
                int targetPosition = 20 + centerLines.size() + maxCenterPlayers + 1; // +1 for spacing
                int spacersBeforeCounter = Math.max(0, targetPosition - currentSize);

                for (int i = 0; i < spacersBeforeCounter; i++) {
                    int spacerIndex = allEntries.size();
                    UUID uuid = UUID.nameUUIDFromBytes(
                        ("PreCounterSpacer-" + spacerIndex).getBytes(StandardCharsets.UTF_8)
                    );

                    FakePlayerEntry spacer = new FakePlayerEntry(
                        uuid,
                        String.format("~%02d", spacerIndex),
                        Component.empty(),
                        0,
                        EnumWrappers.NativeGameMode.SURVIVAL
                    );
                    allEntries.add(spacer);
                }

                // Add the "...and X more" entry
                UUID counterUuid = UUID.nameUUIDFromBytes(
                    "PlayerCounter".getBytes(StandardCharsets.UTF_8)
                );

                String counterText = String.format("<dark_gray>...and <gold>%d</gold> more</dark_gray>", remainingPlayers);
                Component counterDisplay = MINI_MESSAGE.deserialize(counterText);

                FakePlayerEntry counterEntry = new FakePlayerEntry(
                    counterUuid,
                    "~counter",
                    counterDisplay,
                    0,
                    EnumWrappers.NativeGameMode.SURVIVAL
                );
                allEntries.add(counterEntry);
            }
        }

        // Add spacers to fill center column to position 39
        // These spacers use "~" to sort AFTER real players, pushing them to the right column
        int centerSpacersNeeded = 40 - allEntries.size();
        for (int i = 0; i < centerSpacersNeeded; i++) {
            int spacerIndex = allEntries.size();
            UUID uuid = UUID.nameUUIDFromBytes(
                ("CenterSpacer-" + spacerIndex).getBytes(StandardCharsets.UTF_8)
            );

            FakePlayerEntry spacer = new FakePlayerEntry(
                uuid,
                String.format("~%02d", spacerIndex),  // ~ sorts AFTER real players (A-Z)
                Component.empty(),  // Invisible - lets real players show
                0,
                EnumWrappers.NativeGameMode.SURVIVAL
            );
            allEntries.add(spacer);
        }

        // Parse right column entries (40-59)
        // Uses "~" prefix to sort AFTER real player names
        List<String> rightLines = fakeSection.getStringList("right-column");
        List<FakePlayerEntry> rightEntries = parseFakePlayerEntriesForColumn(viewer, rightLines, 40, "~");
        allEntries.addAll(rightEntries);

        return allEntries;
    }

    /**
     * Parses fake player entries from config lines with a specific sorting prefix.
     * Each line becomes a fake player with a deterministic UUID and formatted display name.
     *
     * This overload allows specifying the sorting prefix to control column positioning:
     * - "!" prefix: Sorts BEFORE A-Z (use for left column)
     * - "~" prefix: Sorts AFTER A-Z (use for center/right columns and spacers)
     *
     * @param viewer The player viewing these fake players (for placeholder replacement)
     * @param lines The config lines to parse
     * @param startIndex The starting position in the tab list (0=left, 20=center, 40=right)
     * @param sortPrefix The prefix for sorting ("!" or "~")
     * @return List of fake player entries ready to be added to the tab list
     */
    private List<FakePlayerEntry> parseFakePlayerEntriesForColumn(Player viewer, List<String> lines, int startIndex, String sortPrefix) {
        List<FakePlayerEntry> entries = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);

            // Replace placeholders for this viewer
            String processed = replacePlaceholders(line, viewer);

            // Generate deterministic UUID (same line index + column = same UUID)
            int globalIndex = startIndex + i;
            UUID uuid = UUID.nameUUIDFromBytes(
                ("FakeLine-" + globalIndex).getBytes(StandardCharsets.UTF_8)
            );

            // Create short name for sorting (max 16 chars)
            // Using specified prefix (! or ~) to control sort order
            // Format: !00, !01 (left) or ~00, ~01 (center/right)
            String name = String.format("%s%02d", sortPrefix, globalIndex);

            // Parse display name with MiniMessage
            Component displayName;
            try {
                displayName = MINI_MESSAGE.deserialize(processed);
            } catch (Exception e) {
                // Fallback to plain text if MiniMessage parsing fails
                plugin.getLogger().warning(
                    "Failed to parse MiniMessage for fake player line " + globalIndex + ": " + e.getMessage()
                );
                displayName = Component.text(processed);
            }

            // Create fake player entry
            FakePlayerEntry entry = new FakePlayerEntry(
                uuid,
                name,
                displayName,
                0,  // Ping (0 = full bars, could be dynamic based on config)
                EnumWrappers.NativeGameMode.SURVIVAL  // Gamemode icon
            );

            entries.add(entry);
        }

        return entries;
    }

    /**
     * Parses fake player entries from config lines.
     * Each line becomes a fake player with a deterministic UUID and formatted display name.
     *
     * This version uses "~" prefix by default for backwards compatibility.
     *
     * @param viewer The player viewing these fake players (for placeholder replacement)
     * @param lines The config lines to parse
     * @param startIndex The starting position in the tab list (0=left column, 20=right column)
     * @return List of fake player entries ready to be added to the tab list
     */
    private List<FakePlayerEntry> parseFakePlayerEntries(Player viewer, List<String> lines, int startIndex) {
        return parseFakePlayerEntriesForColumn(viewer, lines, startIndex, "~");
    }

    public void cleanup() {
        if (updateTask != null) {
            updateTask.cancel();
        }

        // Clean up all fake players
        fakePlayerManager.cleanup();
    }
}

