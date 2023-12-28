package me.xidentified.archgpt.reports;
import java.util.*;

import me.xidentified.archgpt.ArchGPT;
import me.xidentified.archgpt.storage.dao.ReportDAO;
import me.xidentified.archgpt.storage.impl.YamlReportDAO;

public class ReportManager {
    private final ReportDAO reportDAO;
    public Set<UUID> reportingPlayers = new HashSet<>();
    public Set<UUID> selectingReportTypePlayers = new HashSet<>();
    private final Map<UUID, String> selectedReportTypes = new HashMap<>();

    public ReportManager(ArchGPT plugin) {
        this.reportDAO = new YamlReportDAO(plugin.getDataFolder(), plugin);
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

}
