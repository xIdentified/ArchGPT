package me.xidentified.archgpt.commands;

import me.xidentified.archgpt.ArchGPT;
import me.xidentified.archgpt.reports.ReportGUI;
import me.xidentified.archgpt.utils.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class AdminReportCommandExecutor implements CommandExecutor {
    private final ArchGPT plugin;

    public AdminReportCommandExecutor(ArchGPT plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player admin)) {
            plugin.sendMessage(sender, Messages.GENERAL_CMD_PLAYER_ONLY);
            return true;
        }

        if (!sender.hasPermission("archgpt.admin")) {
            plugin.sendMessage(sender, Messages.GENERAL_CMD_NO_PERM);
            return true;
        }

        new ReportGUI(plugin).openGUI(admin);

        return true;
    }
}
