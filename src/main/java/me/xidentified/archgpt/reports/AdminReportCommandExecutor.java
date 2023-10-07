package me.xidentified.archgpt.reports;

import me.xidentified.archgpt.ArchGPT;
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
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be run by players.");
            return true;
        }

        Player admin = (Player) sender;
        new ReportGUI(plugin).openGUI(admin);

        return true;
    }
}
