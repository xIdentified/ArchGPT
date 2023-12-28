package me.xidentified.archgpt.storage.dao;


import me.xidentified.archgpt.storage.model.Report;

import java.util.List;

public interface ReportDAO {
    void saveReport(Report report);
    List<Report> getAllReports();
    void deleteReport(int reportId);
    void loadReports();
}
