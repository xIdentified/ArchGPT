package me.xidentified.archgpt.reports;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public class ReportManager {
    private final JavaPlugin plugin;
    private final List<Report> reports;
    private File reportFile;
    private FileConfiguration reportConfig;
    public Set<UUID> reportingPlayers = new HashSet<>();
    public Set<UUID> selectingReportTypePlayers = new HashSet<>();
    private final Map<UUID, String> selectedReportTypes = new HashMap<>();

    public ReportManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.reports = new ArrayList<>();
        setupReportFile();
        loadReports();
    }

    private void setupReportFile() {
        reportFile = new File(plugin.getDataFolder(), "reports.yml");
        if (!reportFile.exists()) {
            try {
                boolean fileCreated = reportFile.createNewFile();
                if (!fileCreated) {
                    plugin.getLogger().warning("Failed to create the reports.yml file!");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        reportConfig = YamlConfiguration.loadConfiguration(reportFile);
    }

    public void addReport(Report report) {
        reports.add(report);
        saveReports();
    }

    public List<Report> listReports() {
        return reports;
    }

    public void deleteReport(int reportId) {
        if(reportId >= 0 && reportId < reports.size()) {
            reports.remove(reportId);
            saveReports();
        }
    }

    public void loadReports() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        if(reportConfig.isConfigurationSection("reports")) {
            Set<String> keys = reportConfig.getConfigurationSection("reports").getKeys(false);
            if (keys != null) {
                for (String key : keys) {
                    String playerName = reportConfig.getString("reports." + key + ".playerName");
                    String npcName = reportConfig.getString("reports." + key + ".npcName");
                    String reportType = reportConfig.getString("reports." + key + ".reportType");
                    String npcResponse = reportConfig.getString("reports." + key + ".npcResponse");
                    String feedbackString = reportConfig.getString("reports." + key + ".feedback");
                    Component feedbackComponent = Component.text(feedbackString);
                    String timestampStr = reportConfig.getString("reports." + key + ".timestamp");
                    if (timestampStr != null) {
                        LocalDateTime timestamp = LocalDateTime.parse(timestampStr, formatter);
                        reports.add(new Report(playerName, npcName, reportType, feedbackComponent, npcResponse, timestamp));
                    } else {
                        plugin.getLogger().warning("Failed to load report with key: " + key + " due to missing timestamp.");
                    }
                }
            }
        }
    }

    public void saveReports() {
        // Clear out old reports from the config
        if (reportConfig.isConfigurationSection("reports")) {
            for (String key : reportConfig.getConfigurationSection("reports").getKeys(false)) {
                reportConfig.set("reports." + key, null);
            }
        }

        int id = 0;
        for (Report report : reports) {
            reportConfig.set("reports." + id + ".playerName", report.getPlayerName());
            reportConfig.set("reports." + id + ".npcName", report.getNpcName());
            reportConfig.set("reports." + id + ".reportType", report.getReportType());
            reportConfig.set("reports." + id + ".npcResponse", report.getNpcResponse());
            reportConfig.set("reports." + id + ".feedback", report.getFeedback());
            reportConfig.set("reports." + id + ".timestamp", report.getFormattedTimestamp());
            id++;
        }
        try {
            reportConfig.save(reportFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void enterReportingState(UUID playerUUID) {
        reportingPlayers.add(playerUUID);
    }

    public void exitReportingState(UUID playerUUID) {
        reportingPlayers.remove(playerUUID);
    }

    public boolean isInReportingState(UUID playerUUID) {
        return reportingPlayers.contains(playerUUID);
    }

    public String getSelectedReportType(UUID playerUUID) {
        return selectedReportTypes.get(playerUUID);
    }

}
