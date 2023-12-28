package me.xidentified.archgpt.storage.dao;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.xidentified.archgpt.storage.model.Report;
import net.kyori.adventure.text.Component;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class MySQLReportDAO implements ReportDAO {
    private final HikariDataSource dataSource;
    public MySQLReportDAO(String host, int port, String database, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false");
        config.setUsername(username);
        config.setPassword(password);

        this.dataSource = new HikariDataSource(config);

        // Initialize the database (create tables etc.)
        initialize();
    }
    private void initialize() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            String sql = "CREATE TABLE IF NOT EXISTS reports (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY," +
                    "playerName VARCHAR(255) NOT NULL," +
                    "npcName VARCHAR(255) NOT NULL," +
                    "reportType VARCHAR(255) NOT NULL," +
                    "feedback TEXT," +
                    "npcResponse TEXT," +
                    "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ");";
            stmt.execute(sql);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void saveReport(Report report) {
        if (report.getReportType() == null) {
            // Handle null reportType, e.g., log an error or set a default value
            System.out.println("Error: reportType was null!");
        }

        String sql = "INSERT INTO reports (playerName, npcName, reportType, feedback, npcResponse, timestamp) VALUES(?,?,?,?,?,?)";

        try (Connection conn = this.dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, report.getPlayerName());
            pstmt.setString(2, report.getNpcName());
            pstmt.setString(3, report.getReportType());
            pstmt.setString(4, report.getFeedback());
            pstmt.setString(5, report.getNpcResponse());
            pstmt.setTimestamp(6, Timestamp.valueOf(report.getTimestamp()));
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public List<Report> getAllReports() {
        List<Report> reports = new ArrayList<>();
        String sql = "SELECT * FROM reports";

        try (Connection conn = dataSource.getConnection();
             Statement stmt  = conn.createStatement();
             ResultSet rs    = stmt.executeQuery(sql)) {

            while (rs.next()) {
                int id = rs.getInt("id"); // Fetch the report ID
                String playerName = rs.getString("playerName");
                String npcName = rs.getString("npcName");
                String reportType = rs.getString("reportType");
                String feedback = rs.getString("feedback");
                String npcResponse = rs.getString("npcResponse");
                LocalDateTime timestamp = rs.getTimestamp("timestamp").toLocalDateTime();

                // Pass the fetched ID to the Report constructor
                Report report = new Report(
                        id,
                        playerName,
                        npcName,
                        reportType,
                        Component.text(feedback),
                        npcResponse,
                        timestamp);

                reports.add(report);
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return reports;
    }

    @Override
    public void deleteReport(int reportId) {
        String sql = "DELETE FROM reports WHERE id = ?";

        try (Connection conn = this.dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, reportId);
            pstmt.executeUpdate();

        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public void loadReports() {
        // This method is not needed for MySQL as the getAllReports() method already handles loading.
    }

    public void closeConnectionPool() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
