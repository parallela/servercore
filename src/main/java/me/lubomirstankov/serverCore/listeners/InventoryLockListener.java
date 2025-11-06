package me.lubomirstankov.serverCore.listeners;

import me.lubomirstankov.serverCore.ServerCore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

/**
 * Listener that prevents players from modifying their inventory.
 * Players with bypass permission can still modify their inventory.
 */
public class InventoryLockListener implements Listener {
    private final ServerCore plugin;
    private static final String CONFIG_PATH = "inventory-lock";

    public InventoryLockListener(ServerCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ConfigurationSection section = plugin.getConfig().getConfigurationSection(CONFIG_PATH);
        if (section == null || !section.getBoolean("enabled", false)) {
            return;
        }

        if (!section.getBoolean("prevent-click", true)) {
            return;
        }

        String bypassPermission = section.getString("bypass-permission", "servercore.inventory.bypass");
        if (player.hasPermission(bypassPermission)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();

        ConfigurationSection section = plugin.getConfig().getConfigurationSection(CONFIG_PATH);
        if (section == null || !section.getBoolean("enabled", false)) {
            return;
        }

        if (!section.getBoolean("prevent-drop", true)) {
            return;
        }

        String bypassPermission = section.getString("bypass-permission", "servercore.inventory.bypass");
        if (player.hasPermission(bypassPermission)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        ConfigurationSection section = plugin.getConfig().getConfigurationSection(CONFIG_PATH);
        if (section == null || !section.getBoolean("enabled", false)) {
            return;
        }

        if (!section.getBoolean("prevent-pickup", true)) {
            return;
        }

        String bypassPermission = section.getString("bypass-permission", "servercore.inventory.bypass");
        if (player.hasPermission(bypassPermission)) {
            return;
        }

        event.setCancelled(true);
    }
}

