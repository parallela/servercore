package me.lubomirstankov.serverCore;

import me.lubomirstankov.serverCore.core.Main;
import org.bukkit.plugin.java.JavaPlugin;

public final class ServerCore extends JavaPlugin {
    private Main main;

    @Override
    public void onEnable() {
        main = new Main(this);
        main.register();
    }

    @Override
    public void onDisable() {
        if (main != null) {
            main.destroy();
        }
    }
}
