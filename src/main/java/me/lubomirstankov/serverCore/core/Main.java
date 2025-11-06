package me.lubomirstankov.serverCore.core;

import me.lubomirstankov.serverCore.ServerCore;
import me.lubomirstankov.serverCore.utils.PlaceholderUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.plugin.java.JavaPlugin;

public class Main {
    private final JavaPlugin plugin;
    public final EventManager eventManager;
    public final CommandManager commandManager;

    public Main(JavaPlugin plugin) {
        this.plugin = plugin;
        this.eventManager = new EventManager((ServerCore) this.plugin);
        this.commandManager = new CommandManager((ServerCore) this.plugin);
    }

    public void register() {
        // Initialize configuration first
        plugin.saveDefaultConfig();

        // Initialize PlaceholderAPI support
        PlaceholderUtil.initialize();

        // Notify enabling
        this.notifyEnabling();

        // Register events
        this.eventManager.registerEvents();

        // Register commands
        this.commandManager.registerCommands();
    }

    public void destroy() {
        // Cleanup events (including TabListener)
        if (this.eventManager != null) {
            this.eventManager.cleanup();
        }

        // Cleanup commands
        if (this.commandManager != null) {
            this.commandManager.unregisterCommands();
        }
    }


    //TODO: refactor
    private void notifyEnabling() {
        Component comp = MiniMessage.miniMessage().deserialize(
                "<gradient:#FF0000:#0000FF>ServerCore has been enabled!</gradient>"
        );

        plugin.getLogger().info(PlainTextComponentSerializer.plainText().serialize(comp));
    }
}
