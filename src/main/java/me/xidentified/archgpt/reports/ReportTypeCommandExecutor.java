package me.xidentified.archgpt.reports;

import me.xidentified.archgpt.ArchGPT;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.jetbrains.annotations.NotNull;

public class ReportTypeCommandExecutor implements CommandExecutor {
    private final ArchGPT plugin;

    public ReportTypeCommandExecutor(ArchGPT plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be run by players.");
            return true;
        }

        Player player = (Player) sender;

        if ("reportnpcmessage".equalsIgnoreCase(cmd.getName())) {
            // Extract the uniqueMessageIdentifier from args
            String uniqueMessageIdentifier = args.length > 0 ? args[0] : null;

            if (uniqueMessageIdentifier != null) {
                // For now, we'll just log it for debugging purposes
                // Later, you can use this to tie back the report to a specific NPC message
                plugin.debugLog("Received report request for message ID: " + uniqueMessageIdentifier);
            }

            // Add the player to the list of players selecting a report type
            plugin.getReportManager().selectingReportTypePlayers.add(player.getUniqueId());

            // Send clickable components for report types
            Component inappropriateResponse = Component.text("[Inappropriate Response]")
                    .color(NamedTextColor.GREEN)
                    .clickEvent(ClickEvent.runCommand("/selectreporttype INAPPROPRIATE_RESPONSE"))
                    .hoverEvent(HoverEvent.showText(Component.text("Report an inappropriate response")));

            Component inaccurateResponse = Component.text("[Inaccurate Response]")
                    .color(NamedTextColor.GREEN)
                    .clickEvent(ClickEvent.runCommand("/selectreporttype INACCURATE_RESPONSE"))
                    .hoverEvent(HoverEvent.showText(Component.text("Report an inaccurate response")));

            Component other = Component.text("[Other]")
                    .color(NamedTextColor.GREEN)
                    .clickEvent(ClickEvent.runCommand("/selectreporttype SOMETHING_ELSE"))
                    .hoverEvent(HoverEvent.showText(Component.text("Other report reasons")));

            player.sendMessage(inappropriateResponse);
            player.sendMessage(inaccurateResponse);
            player.sendMessage(other);

        } else if ("selectreporttype".equalsIgnoreCase(cmd.getName()) && args.length > 0) {
            // Code for the /selectreporttype command
            String selectedType = args[0];

            // Store the selected report type
            plugin.getReportManager().setSelectedReportType(player.getUniqueId(), selectedType);

            // Remove the player from the report type selection state
            plugin.getReportManager().selectingReportTypePlayers.remove(player.getUniqueId());

            // Place the player into the reporting state to provide feedback
            plugin.getReportManager().enterReportingState(player.getUniqueId());
            player.sendMessage(Component.text("You've selected " + selectedType + ". Enter your feedback about the last NPC message.", NamedTextColor.GREEN));
        }
        return true;
    }
}
