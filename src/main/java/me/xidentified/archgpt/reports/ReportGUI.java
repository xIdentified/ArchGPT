package me.xidentified.archgpt.reports;

import lombok.Getter;
import me.xidentified.archgpt.ArchGPT;
import me.xidentified.archgpt.storage.model.Report;
import me.xidentified.archgpt.utils.Messages;
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
    private int currentPage = 0;
    private final int totalPages;

    public ReportGUI(ArchGPT plugin) {
        this.plugin = plugin;
        List<Report> reports = plugin.getReportManager().listReports();
        int slotsPerPage = 45; // Assuming the last row is reserved for navigation
        this.totalPages = (int) Math.ceil((double) reports.size() / slotsPerPage);
        int size = Math.min(54, (slotsPerPage + 9)); // 54 is max inventory size with 9 slots for navigation
        this.inventory = Bukkit.createInventory(this, size, Component.text("Reports").decoration(TextDecoration.ITALIC, false));
    }

    public void openGUI(Player admin) {
        List<Report> reports = plugin.getReportManager().listReports();

        if (reports.isEmpty()) {
            admin.closeInventory();
            plugin.sendMessage(admin, Messages.REPORT_NONE_TO_DISPLAY);
            return;
        }

        int start = this.currentPage * 45;
        int end = Math.min(start + 45, reports.size());

        this.inventory.clear();
        slotToReportIdMap.clear();

        int slot = 0;
        for (int i = start; i < end; i++) {
            Report report = reports.get(i);
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

                // Update the slotToReportIdMap for the current page
                slotToReportIdMap.put(slot, report.getId());
                slot++;
            }

            this.inventory.addItem(reportItem);
        }

        addNavigationItems();

        admin.openInventory(this.inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!(event.getInventory().getHolder() instanceof ReportGUI reportGUI)) return;
        event.setCancelled(true);

        int clickedSlot = event.getSlot();

        if (isNavigationSlot(clickedSlot)) {
            handleNavigationClick(reportGUI, clickedSlot, (Player) event.getWhoClicked());
        } else if (event.isShiftClick() && event.isRightClick()) {
            // Handle report deletion
            Integer reportId = reportGUI.getSlotToReportIdMap().get(clickedSlot);
            if (reportId != null) {
                plugin.getReportManager().deleteReport(reportId);
                List<Report> updatedReports = plugin.getReportManager().listReports();
                if (updatedReports.isEmpty()) {
                    event.getWhoClicked().closeInventory();
                    plugin.sendMessage(event.getWhoClicked(), Messages.REPORT_NONE_TO_DISPLAY);
                } else {
                    reportGUI.openGUI((Player) event.getWhoClicked());
                    plugin.sendMessage(event.getWhoClicked(), Messages.REPORT_DELETED);
                }
            }
        }
    }

    private boolean isNavigationSlot(int slot) {
        // Assuming the last two slots are used for navigation (e.g., previous and next page)
        return slot >= this.inventory.getSize() - 2;
    }

    private void handleNavigationClick(ReportGUI reportGUI, int clickedSlot, Player player) {
        if (clickedSlot == this.inventory.getSize() - 2) { // Previous page button slot
            reportGUI.currentPage = Math.max(0, reportGUI.currentPage - 1);
        } else if (clickedSlot == this.inventory.getSize() - 1) { // Next page button slot
            reportGUI.currentPage = Math.min(reportGUI.totalPages - 1, reportGUI.currentPage + 1);
        }
        reportGUI.openGUI(player);
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

    private void addNavigationItems() {
        if (this.totalPages > 1) { // Add navigation items only if more than one page is needed
            ItemStack prevPageItem = createNavigationItem("Previous Page");
            ItemStack nextPageItem = createNavigationItem("Next Page");

            this.inventory.setItem(this.inventory.getSize() - 2, prevPageItem); // Slot for previous page
            this.inventory.setItem(this.inventory.getSize() - 1, nextPageItem); // Slot for next page
        }
    }

    private ItemStack createNavigationItem(String name) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    @NotNull
    public Inventory getInventory() {
        return this.inventory;
    }

}