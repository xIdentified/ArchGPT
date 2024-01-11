package me.xidentified.archgpt.reports;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import me.xidentified.archgpt.ArchGPT;
import me.xidentified.archgpt.storage.dao.MySQLReportDAO;
import me.xidentified.archgpt.storage.dao.ReportDAO;
import me.xidentified.archgpt.storage.dao.SQLiteReportDAO;
import me.xidentified.archgpt.storage.model.Report;
import org.bukkit.configuration.ConfigurationSection;

public class ReportManager {
    private final ReportDAO reportDAO;
    public Set<UUID> reportingPlayers = new HashSet<>();
    public Set<UUID> selectingReportTypePlayers = new HashSet<>();
    private final Map<UUID, String> selectedReportTypes = new ConcurrentHashMap<>();

    public ReportManager(ArchGPT plugin) {
        String storageType = plugin.getConfig().getString("storage.type", "sqlite");
        switch (storageType.toLowerCase()) {
            case "sqlite":
                this.reportDAO = new SQLiteReportDAO(plugin.getDataFolder());
                break;
            case "mysql":
                ConfigurationSection dbConfig = plugin.getConfig().getConfigurationSection("storage.mysql");
                String host = dbConfig.getString("host");
                int port = dbConfig.getInt("port");
                String database = dbConfig.getString("database");
                String username = dbConfig.getString("username");
                String password = dbConfig.getString("password");
                this.reportDAO = new MySQLReportDAO(host, port, database, username, password);
                break;
            default:
                // Default to SQLite if the specified storage type is not recognized
                this.reportDAO = new SQLiteReportDAO(plugin.getDataFolder());
                break;
        }
    }

    public void addReport(Report report) {
        reportDAO.saveReport(report);
    }

    public List<Report> listReports() {
        return reportDAO.getAllReports();
    }

    public void deleteReport(int reportId) {
        reportDAO.deleteReport(reportId);
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

    public void setSelectedReportType(UUID playerUUID, String reportType) {
        selectedReportTypes.put(playerUUID, reportType);
    }

    public void closeResources() {
        if (this.reportDAO instanceof MySQLReportDAO) {
            ((MySQLReportDAO)this.reportDAO).closeConnectionPool();
        }
    }

}
