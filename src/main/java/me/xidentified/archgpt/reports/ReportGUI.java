package me.xidentified.archgpt.reports;

import me.xidentified.archgpt.ArchGPT;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ReportGUI implements InventoryHolder {

    private final ArchGPT plugin;
    private final Inventory inventory;

    public ReportGUI(ArchGPT plugin) {
        this.plugin = plugin;
        int size = Math.min(54, (plugin.getReportManager().listReports().size() / 9 + 1) * 9);
        this.inventory = Bukkit.createInventory(this, size, Component.text("Reports").decoration(TextDecoration.ITALIC, false));
    }

    public void openGUI(Player admin) {
        List<Report> reports = plugin.getReportManager().listReports();

        // Clear the inventory before adding items to it
        this.inventory.clear();

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
            }

            this.inventory.addItem(reportItem);
        }

        admin.openInventory(this.inventory);
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