package me.xidentified.archgpt.commands;

import me.xidentified.archgpt.ArchGPT;
import me.xidentified.archgpt.utils.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
                plugin.sendMessage(sender, Messages.RELOAD_SUCCESS);
                return true;
            }
        }

        plugin.sendMessage(sender, Messages.RELOAD_CMD_USAGE);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (command.getName().equalsIgnoreCase("archgpt") && args.length == 1) {
            completions.add("reload");
        }

        return completions;
    }
}
