package me.xidentified.archgpt.reports;

import lombok.Getter;
import me.xidentified.archgpt.ArchGPT;
import me.xidentified.archgpt.storage.model.Report;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReportGUI implements InventoryHolder, Listener {

    private final ArchGPT plugin;
    private final Inventory inventory;
    @Getter private Map<Integer, Integer> slotToReportIdMap = new HashMap<>();

    public ReportGUI(ArchGPT plugin) {
        this.plugin = plugin;
        int size = Math.min(54, (plugin.getReportManager().listReports().size() / 9 + 1) * 9);
        this.inventory = Bukkit.createInventory(this, size, Component.text("Reports").decoration(TextDecoration.ITALIC, false));
    }

    public void openGUI(Player admin) {
        List<Report> reports = plugin.getReportManager().listReports();
        this.inventory.clear();
        slotToReportIdMap.clear();

        int slot = 0;
        for (Report report : reports) {
            ItemStack reportItem = new ItemStack(Material.WRITABLE_BOOK);
            ItemMeta meta = reportItem.getItemMeta();

            if (meta != null) {
                Component displayName = Component.text("Report by ", NamedTextColor.GREEN)
                        .append(Component.text(report.getPlayerName()))
                        .decoration(TextDecoration.ITALIC, false);
                meta.displayName(displayName);

                List<Component> lore = new ArrayList<>();
                addFormattedLore(lore, "NPC Name: ", report.getNpcName());
                addFormattedLore(lore, "Response: ", report.getNpcResponse());
                addFormattedLore(lore, "Feedback: ", report.getFeedback());
                addFormattedLore(lore, "Timestamp: ", "'" + report.getFormattedTimestamp() + "'");
                lore.add(Component.empty());
                lore.add(Component.text("Shift + Right-Click to Remove", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));

                meta.lore(lore);
                reportItem.setItemMeta(meta);

                // Map the current slot to the report's ID
                slotToReportIdMap.put(slot, report.getId());
                slot++;
            }

            this.inventory.addItem(reportItem);
        }

        admin.openInventory(this.inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!(event.getInventory().getHolder() instanceof ReportGUI)) return;
        event.setCancelled(true);

        ReportGUI reportGUI = (ReportGUI) event.getInventory().getHolder();
        if (event.isShiftClick() && event.isRightClick()) {
            int clickedSlot = event.getSlot();

            // Get the report ID from the slotToReportIdMap
            Integer reportId = reportGUI.getSlotToReportIdMap().get(clickedSlot);
            if (reportId != null) {
                plugin.getReportManager().deleteReport(reportId);
                new ReportGUI(plugin).openGUI((Player) event.getWhoClicked());
                event.getWhoClicked().sendMessage(Component.text("Report successfully deleted.", NamedTextColor.YELLOW));
            } else {
                event.getWhoClicked().sendMessage(Component.text("No report found with id: " + clickedSlot, NamedTextColor.RED));
            }
        }
    }

    private void addFormattedLore(List<Component> lore, String fieldName, String content) {
        final int MAX_LENGTH = 50;
        String fullFieldName = fieldName;
        String fullContent = content;

        while ((fullFieldName + fullContent).length() > MAX_LENGTH) {
            int splitIndex = (fullFieldName + fullContent).lastIndexOf(' ', MAX_LENGTH);
            if (splitIndex == -1) splitIndex = MAX_LENGTH;

            int fieldNameLength = Math.min(splitIndex, fullFieldName.length());
            int contentLength = Math.max(0, splitIndex - fieldNameLength);

            Component line = Component.text(fullFieldName.substring(0, fieldNameLength), NamedTextColor.YELLOW)
                    .append(Component.text(fullContent.substring(0, contentLength), NamedTextColor.WHITE))
                    .decoration(TextDecoration.ITALIC, false);

            lore.add(line);

            if (fullFieldName.length() > splitIndex) {
                fullFieldName = fullFieldName.substring(splitIndex).trim();
                fullContent = "";  // Reset content since fieldName took up the entire split length
            } else {
                fullFieldName = "";
                fullContent = fullContent.substring(splitIndex - fieldNameLength).trim();
            }
        }

        Component remainingLine = Component.text(fullFieldName, NamedTextColor.YELLOW)
                .append(Component.text(fullContent, NamedTextColor.WHITE))
                .decoration(TextDecoration.ITALIC, false);
        lore.add(remainingLine);
    }

    @Override
    @NotNull
    public Inventory getInventory() {
        return this.inventory;
    }

}