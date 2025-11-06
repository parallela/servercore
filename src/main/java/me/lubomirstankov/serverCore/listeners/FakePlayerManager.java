package me.lubomirstankov.serverCore.listeners;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.*;
import me.lubomirstankov.serverCore.ServerCore;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages fake player entries in the tab list using ProtocolLib 5.4.0.
 * Fully compatible with Minecraft 1.21.x protocol changes.
 *
 * Key Features:
 * - Global fake players visible to all online players
 * - Flicker-free updates using UPDATE_DISPLAY_NAME action
 * - Proper packet construction for MC 1.21.x PLAYER_INFO packets
 * - Thread-safe operations with ConcurrentHashMap
 * - Automatic cleanup on player disconnect and plugin disable
 */
public class FakePlayerManager {
    private final ServerCore plugin;
    private final ProtocolManager protocolManager;

    /**
     * Maps viewer UUID -> Set of fake player UUIDs they can see.
     * Used to track which fake players need to be removed when viewer quits.
     */
    private final Map<UUID, Set<UUID>> viewerFakePlayers;

    /**
     * Maps viewer UUID -> Map of fake player UUID -> FakePlayerEntry.
     * Caches current state to detect changes and prevent unnecessary packets.
     */
    private final Map<UUID, Map<UUID, FakePlayerEntry>> viewerFakePlayersCache;

    public FakePlayerManager(ServerCore plugin) {
        this.plugin = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
        this.viewerFakePlayers = new ConcurrentHashMap<>();
        this.viewerFakePlayersCache = new ConcurrentHashMap<>();
    }

    /**
     * Adds a fake player to the viewer's tab list.
     * Sends PLAYER_INFO_UPDATE packet with ADD_PLAYER + UPDATE_LISTED actions.
     *
     * In 1.21.x, UPDATE_LISTED is REQUIRED to make the player visible!
     * Without it, the player is added but remains invisible in the client.
     *
     * @param viewer The player who will see this fake player
     * @param entry The fake player data to add
     */
    public void addFakePlayer(Player viewer, FakePlayerEntry entry) {
        if (viewer == null || !viewer.isOnline() || entry == null) {
            return;
        }

        // Track this fake player for the viewer
        viewerFakePlayers
            .computeIfAbsent(viewer.getUniqueId(), k -> ConcurrentHashMap.newKeySet())
            .add(entry.uuid());

        // Cache the entry for future updates
        viewerFakePlayersCache
            .computeIfAbsent(viewer.getUniqueId(), k -> new ConcurrentHashMap<>())
            .put(entry.uuid(), entry);

        // Send ADD packet with all initial data
        sendPlayerInfoPacket(viewer, entry, EnumSet.of(
            EnumWrappers.PlayerInfoAction.ADD_PLAYER,
            EnumWrappers.PlayerInfoAction.UPDATE_LISTED,
            EnumWrappers.PlayerInfoAction.UPDATE_LATENCY,
            EnumWrappers.PlayerInfoAction.UPDATE_DISPLAY_NAME,
            EnumWrappers.PlayerInfoAction.UPDATE_GAME_MODE
        ));
    }

    /**
     * Updates an existing fake player's display name and ping.
     * Uses UPDATE-only actions to prevent flickering in the client.
     *
     * This is much more efficient than removing and re-adding the player!
     *
     * @param viewer The player who sees this fake player
     * @param entry The updated fake player data
     */
    public void updateFakePlayer(Player viewer, FakePlayerEntry entry) {
        if (viewer == null || !viewer.isOnline() || entry == null) {
            return;
        }

        // Check if this fake player exists for this viewer
        Set<UUID> fakePlayers = viewerFakePlayers.get(viewer.getUniqueId());
        if (fakePlayers == null || !fakePlayers.contains(entry.uuid())) {
            // Not added yet, add it instead
            addFakePlayer(viewer, entry);
            return;
        }

        // Check if the entry actually changed (avoid unnecessary packets)
        Map<UUID, FakePlayerEntry> cache = viewerFakePlayersCache.get(viewer.getUniqueId());
        if (cache != null) {
            FakePlayerEntry cached = cache.get(entry.uuid());
            if (cached != null && cached.equals(entry)) {
                // No change, skip update
                return;
            }
            // Update cache
            cache.put(entry.uuid(), entry);
        }

        // Send UPDATE packet (no ADD_PLAYER = no flicker)
        sendPlayerInfoPacket(viewer, entry, EnumSet.of(
            EnumWrappers.PlayerInfoAction.UPDATE_LATENCY,
            EnumWrappers.PlayerInfoAction.UPDATE_DISPLAY_NAME
        ));
    }

    /**
     * Removes a fake player from the viewer's tab list.
     * Uses PLAYER_INFO_REMOVE packet (separate packet type in 1.19.3+).
     *
     * @param viewer The player who will no longer see this fake player
     * @param fakeUuid The UUID of the fake player to remove
     */
    public void removeFakePlayer(Player viewer, UUID fakeUuid) {
        if (viewer == null || fakeUuid == null) {
            return;
        }

        // Untrack this fake player
        Set<UUID> fakePlayers = viewerFakePlayers.get(viewer.getUniqueId());
        if (fakePlayers != null) {
            fakePlayers.remove(fakeUuid);
        }

        // Remove from cache
        Map<UUID, FakePlayerEntry> cache = viewerFakePlayersCache.get(viewer.getUniqueId());
        if (cache != null) {
            cache.remove(fakeUuid);
        }

        // Send REMOVE packet
        sendRemovePacket(viewer, fakeUuid);
    }

    /**
     * Removes all fake players from the viewer's tab list.
     * Called when a player quits or when fake players are disabled.
     *
     * @param viewer The player whose fake players should be removed
     */
    public void removeAllFakePlayers(Player viewer) {
        if (viewer == null) {
            return;
        }

        Set<UUID> fakePlayers = viewerFakePlayers.remove(viewer.getUniqueId());
        if (fakePlayers != null && !fakePlayers.isEmpty()) {
            // Send bulk REMOVE packet
            sendRemovePacket(viewer, new ArrayList<>(fakePlayers));
        }

        // Clear cache
        viewerFakePlayersCache.remove(viewer.getUniqueId());
    }

    /**
     * Gets all fake player UUIDs currently visible to a viewer.
     *
     * @param viewer The viewer to query
     * @return Unmodifiable set of fake player UUIDs, or empty set if none
     */
    public Set<UUID> getFakePlayersForViewer(Player viewer) {
        if (viewer == null) {
            return Collections.emptySet();
        }
        Set<UUID> fakePlayers = viewerFakePlayers.get(viewer.getUniqueId());
        return fakePlayers == null ? Collections.emptySet() : Collections.unmodifiableSet(fakePlayers);
    }

    /**
     * Sends a PLAYER_INFO packet with specified actions.
     * For ProtocolLib 5.4.0 on MC 1.21.x, uses the PLAYER_INFO packet type.
     *
     * Protocol breakdown:
     * - Field 0: EnumSet<PlayerInfoAction> - Which fields to update
     * - Field 1: List<PlayerInfoData> - The actual player data
     *
     * @param viewer The player to send the packet to
     * @param entry The fake player data
     * @param actions Which actions to perform (ADD_PLAYER, UPDATE_DISPLAY_NAME, etc.)
     */
    private void sendPlayerInfoPacket(
        Player viewer,
        FakePlayerEntry entry,
        EnumSet<EnumWrappers.PlayerInfoAction> actions
    ) {
        try {
            // Create the packet container for PLAYER_INFO
            // In 1.21.x + ProtocolLib 5.4.0, this handles both old and new protocol versions
            PacketContainer packet = protocolManager.createPacket(
                PacketType.Play.Server.PLAYER_INFO
            );

            // Set the actions to perform
            packet.getPlayerInfoActions().write(0, actions);

            // Create the game profile (UUID + name)
            // Name is used for sorting in the tab list
            WrappedGameProfile profile = new WrappedGameProfile(
                entry.uuid(),
                entry.name()
            );

            // Convert Adventure Component to JSON for ProtocolLib
            // MC 1.21.x uses JSON text components internally
            String displayNameJson = GsonComponentSerializer.gson()
                .serialize(entry.displayName());
            WrappedChatComponent displayName = WrappedChatComponent.fromJson(displayNameJson);

            // Create the player info data object
            // Constructor: (profile, latency, gameMode, displayName, chatSession)
            PlayerInfoData data = new PlayerInfoData(
                profile,
                entry.ping(),
                entry.gameMode(),
                displayName,
                null  // RemoteChatSessionData - not needed for fake players
            );

            // Set the player data list (field 1)
            packet.getPlayerInfoDataLists().write(1, List.of(data));

            // Send the packet to the viewer
            protocolManager.sendServerPacket(viewer, packet);

        } catch (Exception e) {
            plugin.getLogger().warning(
                "Failed to send fake player packet to " + viewer.getName() +
                " for fake player " + entry.name() + ": " + e.getMessage()
            );
        }
    }

    /**
     * Sends a PLAYER_INFO_REMOVE packet to remove a single fake player.
     * In 1.19.3+, removal is a separate packet type, not an action.
     *
     * @param viewer The player to send the packet to
     * @param fakeUuid The UUID of the fake player to remove
     */
    private void sendRemovePacket(Player viewer, UUID fakeUuid) {
        sendRemovePacket(viewer, List.of(fakeUuid));
    }

    /**
     * Sends a PLAYER_INFO_REMOVE packet to remove multiple fake players.
     * More efficient than sending multiple individual remove packets.
     *
     * @param viewer The player to send the packet to
     * @param fakeUuids The UUIDs of the fake players to remove
     */
    private void sendRemovePacket(Player viewer, List<UUID> fakeUuids) {
        if (fakeUuids == null || fakeUuids.isEmpty() || viewer == null || !viewer.isOnline()) {
            return;
        }

        try {
            // Create the PLAYER_INFO_REMOVE packet
            PacketContainer packet = protocolManager.createPacket(
                PacketType.Play.Server.PLAYER_INFO_REMOVE
            );

            // Set the list of UUIDs to remove
            packet.getUUIDLists().write(0, fakeUuids);

            // Send the packet
            protocolManager.sendServerPacket(viewer, packet);

        } catch (Exception e) {
            plugin.getLogger().warning(
                "Failed to send remove packet to " + viewer.getName() +
                " for " + fakeUuids.size() + " fake players: " + e.getMessage()
            );
        }
    }

    /**
     * Cleans up all fake players for all viewers.
     * Called when the plugin is disabled.
     */
    public void cleanup() {
        // Remove all fake players from all viewers
        for (UUID viewerUuid : new HashSet<>(viewerFakePlayers.keySet())) {
            Player viewer = plugin.getServer().getPlayer(viewerUuid);
            if (viewer != null && viewer.isOnline()) {
                removeAllFakePlayers(viewer);
            }
        }

        // Clear all maps
        viewerFakePlayers.clear();
        viewerFakePlayersCache.clear();
    }
}

