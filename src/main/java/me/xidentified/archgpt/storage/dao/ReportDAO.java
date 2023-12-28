package me.xidentified.archgpt.storage.dao;


import me.xidentified.archgpt.reports.Report;

import java.util.List;

public interface ReportDAO {
    void saveReport(Report report);
    List<Report> getAllReports();
    void deleteReport(int reportId);
    void loadReports();
}
