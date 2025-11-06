package me.lubomirstankov.serverCore.commands;

import me.lubomirstankov.serverCore.ServerCore;
import me.lubomirstankov.serverCore.utils.PlaceholderUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Command to display a beautifully formatted list of online players
 * with full MiniMessage support and customizable formatting
 */
public class ListCommand implements CommandExecutor, TabCompleter {
    private final ServerCore plugin;
    private static final String CONFIG_PATH = "commands.list";
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    public ListCommand(ServerCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(CONFIG_PATH);

        if (section == null || !section.getBoolean("enabled", true)) {
            sender.sendMessage(Component.text("This command is disabled."));
            return true;
        }

        // Check permission
        String permission = section.getString("permission", "");
        if (!permission.isEmpty() && !sender.hasPermission(permission)) {
            String noPermMsg = section.getString("no-permission-message", "<red>You don't have permission to use this command!</red>");
            sender.sendMessage(MINI_MESSAGE.deserialize(noPermMsg));
            return true;
        }

        // Get online players
        List<? extends Player> onlinePlayers = plugin.getServer().getOnlinePlayers().stream().toList();
        int playerCount = onlinePlayers.size();
        int maxPlayers = plugin.getServer().getMaxPlayers();

        // Send header
        List<String> headerLines = section.getStringList("format.header");
        for (String line : headerLines) {
            String formatted = line
                    .replace("{online}", String.valueOf(playerCount))
                    .replace("{max}", String.valueOf(maxPlayers));
            sender.sendMessage(MINI_MESSAGE.deserialize(formatted));
        }

        // Send player list
        if (playerCount == 0) {
            String emptyMessage = section.getString("format.empty-message", "<gray>No players online</gray>");
            sender.sendMessage(MINI_MESSAGE.deserialize(emptyMessage));
        } else {
            String playerFormat = section.getString("format.player-format", "<gray>• <white>{player}</white></gray>");

            for (Player player : onlinePlayers) {
                String formatted = playerFormat
                        .replace("{player}", player.getName())
                        .replace("{displayname}", player.displayName().toString())
                        .replace("{world}", player.getWorld().getName())
                        .replace("{gamemode}", player.getGameMode().name());

                // Apply PlaceholderAPI if available
                formatted = PlaceholderUtil.applyPlaceholdersWithBrackets(player, formatted);

                sender.sendMessage(MINI_MESSAGE.deserialize(formatted));
            }
        }

        // Send footer
        List<String> footerLines = section.getStringList("format.footer");
        for (String line : footerLines) {
            String formatted = line
                    .replace("{online}", String.valueOf(playerCount))
                    .replace("{max}", String.valueOf(maxPlayers));
            sender.sendMessage(MINI_MESSAGE.deserialize(formatted));
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        // No tab completion needed for list command
        return new ArrayList<>();
    }
}

