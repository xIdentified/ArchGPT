package me.xidentified.archgpt.storage.dao;

import me.xidentified.archgpt.storage.model.Report;
import net.kyori.adventure.text.Component;

import java.io.File;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class SQLiteReportDAO implements ReportDAO {
    private final String dbPath;

    public SQLiteReportDAO(File dataFolder) {
        File storageFolder = new File(dataFolder, "storage");
        if (!storageFolder.exists()) {
            storageFolder.mkdirs();
        }
        this.dbPath = new File(storageFolder, "reports.db").getPath();
        initialize();
    }

    private void initialize() {
        try (Connection conn = this.connect();
             Statement stmt = conn.createStatement()) {
            String sql = "CREATE TABLE IF NOT EXISTS reports (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "playerName TEXT NOT NULL," +
                    "npcName TEXT NOT NULL," +
                    "reportType TEXT NOT NULL," +
                    "feedback TEXT," +
                    "npcResponse TEXT," +
                    "timestamp TEXT NOT NULL" +
                    ");";
            stmt.execute(sql);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    private Connection connect() {
        String url = "jdbc:sqlite:" + this.dbPath;
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(url);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return conn;
    }

    @Override
    public void saveReport(Report report) {
        String sql = "INSERT INTO reports (playerName, npcName, reportType, feedback, npcResponse, timestamp) VALUES(?,?,?,?,?,?)";

        try (Connection conn = this.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, report.getPlayerName());
            pstmt.setString(2, report.getNpcName());
            pstmt.setString(3, report.getReportType());
            pstmt.setString(4, report.getFeedback());
            pstmt.setString(5, report.getNpcResponse());
            pstmt.setString(6, report.getFormattedTimestamp());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public List<Report> getAllReports() {
        List<Report> reports = new ArrayList<>();
        String sql = "SELECT * FROM reports";

        try (Connection conn = this.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                int id = rs.getInt("id");
                System.out.println("Report ID: " + id); // Log the report ID
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                LocalDateTime timestamp = LocalDateTime.parse(rs.getString("timestamp"), formatter);
                reports.add(new Report(
                        id, // Include the ID in the Report object
                        rs.getString("playerName"),
                        rs.getString("npcName"),
                        rs.getString("reportType"),
                        Component.text(rs.getString("feedback")),
                        rs.getString("npcResponse"),
                        timestamp
                ));
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return reports;
    }

    @Override
    public void deleteReport(int reportId) {
        String sql = "DELETE FROM reports WHERE id = ?";

        try (Connection conn = this.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, reportId);
            int affectedRows = pstmt.executeUpdate();

            if (affectedRows == 0) {
                System.out.println("No report found with id: " + reportId);
            } else {
                System.out.println("Report successfully deleted: " + reportId);
            }

        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public boolean reportExists(int reportId) {
        String sql = "SELECT id FROM reports WHERE id = ?";

        try (Connection conn = this.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, reportId);
            ResultSet rs = pstmt.executeQuery();
            return rs.next();

        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    @Override
    public void loadReports() {
        // This method is not needed for SQLite as the getAllReports() method already handles loading.
    }}
