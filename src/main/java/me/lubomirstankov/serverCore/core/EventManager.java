package me.lubomirstankov.serverCore.core;

import me.lubomirstankov.serverCore.ServerCore;
import me.lubomirstankov.serverCore.listeners.DoubleJumpListener;
import me.lubomirstankov.serverCore.listeners.InventoryLockListener;
import me.lubomirstankov.serverCore.listeners.JoinListener;
import me.lubomirstankov.serverCore.listeners.MotdListener;
import me.lubomirstankov.serverCore.listeners.ServerMotdListener;
import me.lubomirstankov.serverCore.listeners.TabListManager;
import org.bukkit.event.Listener;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

public class EventManager {
    private final ServerCore plugin;
    private final List<Listener> registeredListeners = new ArrayList<>();
    private TabListManager tabListManager;

    private final List<Class<? extends Listener>> listenerClasses = List.of(
            JoinListener.class,
            InventoryLockListener.class,
            DoubleJumpListener.class,
            MotdListener.class,
            ServerMotdListener.class,
            TabListManager.class
            // Add more listener classes here
    );

    public EventManager(ServerCore plugin) {
        this.plugin = plugin;
    }

    public void registerEvents() {
        for (Class<? extends Listener> listenerClass : listenerClasses) {
            try {
                Listener listener = createListener(listenerClass);
                plugin.getServer().getPluginManager().registerEvents(listener, plugin);
                registeredListeners.add(listener);

                // Store TabListManager reference for cleanup
                if (listener instanceof TabListManager) {
                    tabListManager = (TabListManager) listener;
                }

                plugin.getLogger().info("Registered listener: " + listenerClass.getSimpleName());
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to register listener " + listenerClass.getSimpleName() + ": " + e.getMessage());
            }
        }
    }

    public void cleanup() {
        // Clean up TabListManager
        if (tabListManager != null) {
            tabListManager.cleanup();
        }

        registeredListeners.clear();
    }

    private Listener createListener(Class<? extends Listener> listenerClass) throws Exception {
        // Try constructor with ServerCore parameter
        try {
            Constructor<? extends Listener> constructor = listenerClass.getConstructor(ServerCore.class);
            return constructor.newInstance(plugin);
        } catch (NoSuchMethodException e) {
            // Try no-arg constructor
            Constructor<? extends Listener> constructor = listenerClass.getConstructor();
            return constructor.newInstance();
        }
    }
}
