package me.xidentified.archgpt.commands;

import me.xidentified.archgpt.ArchGPT;
import me.xidentified.archgpt.utils.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ArchGPTCommand implements CommandExecutor, TabCompleter {

    private final ArchGPT plugin;

    public ArchGPTCommand(ArchGPT plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("archgpt.reload")) {
                    plugin.sendMessage(sender, Messages.GENERAL_CMD_NO_PERM);
                    return true;
                }
                plugin.reloadConfig();
                // TODO: Unregister and register listeners
                plugin.sendMessage(sender, Messages.RELOAD_SUCCESS);
                return true;
            } else if (args[0].equalsIgnoreCase("clear-all-conversations")) {
                if (!sender.hasPermission("archgpt.admin")) {
                    plugin.sendMessage(sender, Messages.GENERAL_CMD_NO_PERM);
                    return true;
                }
                clearConversationStorage(sender);
                return true;
            }
            plugin.sendMessage(sender, Messages.GENERAL_CMD_USAGE);
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, Command command, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (command.getName().equalsIgnoreCase("archgpt") && args.length == 1) {
            completions.add("reload");
            if (sender.hasPermission("archgpt.admin")) {
                completions.add("clear-all-conversations");
            }
        }

        return completions;
    }

    private void clearConversationStorage(CommandSender sender) {
        // Implement logic to clear conversation storage
        try {
            plugin.getConversationDAO().clearAllConversations();
            plugin.sendMessage(sender, Messages.CLEAR_STORAGE_SUCCESS);
        } catch (Exception e) {
            plugin.getLogger().severe("Error clearing conversation storage: " + e.getMessage());
            plugin.sendMessage(sender, Messages.CLEAR_STORAGE_ERROR);
        }
    }
}
