package me.xidentified.archgpt.reports;

import me.xidentified.archgpt.ArchGPT;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;

public class ReportGUIListener implements Listener {

    private final ArchGPT plugin;

    public ReportGUIListener(ArchGPT plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        // Check if the inventory holder is an instance of ReportGUI
        if (!(event.getInventory().getHolder() instanceof ReportGUI)) return;

        // If it is, then it's our custom inventory
        event.setCancelled(true); // Prevent items from being taken

        if (event.isShiftClick() && event.isRightClick()) {
            // Use the slot index directly for deletion
            int clickedSlot = event.getSlot();
            List<Report> reports = plugin.getReportManager().listReports();

            if (clickedSlot < reports.size()) {
                plugin.getReportManager().deleteReport(clickedSlot);

                // Refresh the GUI to reflect the deletion
                new ReportGUI(plugin).openGUI((Player) event.getWhoClicked());

                event.getWhoClicked().sendMessage(Component.text("Report successfully deleted.", NamedTextColor.YELLOW));
            }
        }
    }
}