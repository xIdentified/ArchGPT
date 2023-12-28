package me.xidentified.archgpt.storage.impl;

import me.xidentified.archgpt.ArchGPT;
import me.xidentified.archgpt.storage.model.Report;
import me.xidentified.archgpt.storage.dao.ReportDAO;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class YamlReportDAO implements ReportDAO {
    private final ArchGPT plugin;
    private final File reportFile;
    private YamlConfiguration reportConfig;
    private final List<Report> reports = new ArrayList<>();

    public YamlReportDAO(File dataFolder, ArchGPT plugin) {
        // Create storage directory if it doesn't exist
        File storageFolder = new File(dataFolder, "storage");
        if (!storageFolder.exists()) {
            storageFolder.mkdirs();
        }

        // Initialize report file within the storage folder
        this.reportFile = new File(storageFolder, "reports.yml");
        this.plugin = plugin;

        // Setup report file and load existing reports
        setupReportFile();
        loadReports();
    }

    private void setupReportFile() {
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

    @Override
    public void saveReport(Report report) {
        // Save a single report
        reports.add(report);
        saveReports();
    }

    @Override
    public List<Report> getAllReports() {
        return new ArrayList<>(reports);
    }

    @Override
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
                    int reportId = Integer.parseInt(key); // Parse the key as the report ID
                    String playerName = reportConfig.getString("reports." + key + ".playerName");
                    String npcName = reportConfig.getString("reports." + key + ".npcName");
                    String reportType = reportConfig.getString("reports." + key + ".reportType");
                    String npcResponse = reportConfig.getString("reports." + key + ".npcResponse");
                    String feedbackString = reportConfig.getString("reports." + key + ".feedback");
                    Component feedbackComponent = Component.text(feedbackString);
                    String timestampStr = reportConfig.getString("reports." + key + ".timestamp");
                    if (timestampStr != null) {
                        LocalDateTime timestamp = LocalDateTime.parse(timestampStr, formatter);
                        reports.add(new Report(reportId, playerName, npcName, reportType, feedbackComponent, npcResponse, timestamp));
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

}
