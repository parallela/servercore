package me.lubomirstankov.serverCore.core;

import me.lubomirstankov.serverCore.ServerCore;
import me.lubomirstankov.serverCore.commands.ListCommand;
import org.bukkit.command.PluginCommand;

/**
 * Manages all command registration for the plugin
 * Follows single responsibility principle - only handles command registration
 */
public class CommandManager {
    private final ServerCore plugin;

    public CommandManager(ServerCore plugin) {
        this.plugin = plugin;
    }

    /**
     * Register all plugin commands
     */
    public void registerCommands() {
        registerListCommand();
    }

    /**
     * Register the /list command
     */
    private void registerListCommand() {
        PluginCommand listCommand = plugin.getCommand("list");
        if (listCommand != null) {
            ListCommand executor = new ListCommand(plugin);
            listCommand.setExecutor(executor);
            listCommand.setTabCompleter(executor);
        } else {
            plugin.getLogger().warning("Failed to register /list command - command not found in plugin.yml");
        }
    }

    /**
     * Cleanup resources if needed
     */
    public void unregisterCommands() {
        // Cleanup if needed in the future
    }
}

