package me.xidentified.archgpt.storage.dao;

import me.xidentified.archgpt.storage.model.Conversation;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SQLiteConversationDAO implements ConversationDAO {
    private final String url;

    public SQLiteConversationDAO(File sqliteFile) {
        // Ensure the directory for the SQLite file exists
        if (!sqliteFile.getParentFile().exists()) {
            sqliteFile.getParentFile().mkdirs();
        }

        // Construct the JDBC URL using the file path
        this.url = "jdbc:sqlite:" + sqliteFile.getAbsolutePath();
        initializeDatabase();
    }

    private void initializeDatabase() {
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement stmt = conn.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS conversations (" +
                             "player_uuid TEXT, " +
                             "npc_name TEXT, " +
                             "message TEXT, " +
                             "timestamp INTEGER)")) {
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void saveConversation(Conversation conversation) {
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO conversations (player_uuid, npc_name, message, timestamp) VALUES (?, ?, ?, ?)")) {
            stmt.setString(1, conversation.getPlayerUUID().toString());
            stmt.setString(2, conversation.getNpcName());
            stmt.setString(3, conversation.getMessage());
            stmt.setLong(4, conversation.getTimestamp());
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<Conversation> getConversations(UUID playerUUID, String npcName) {
        List<Conversation> conversations = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT * FROM conversations WHERE player_uuid = ? AND npc_name = ?")) {
            stmt.setString(1, playerUUID.toString());
            stmt.setString(2, npcName);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String message = rs.getString("message");
                    long timestamp = rs.getLong("timestamp");
                    conversations.add(new Conversation(playerUUID, npcName, message, timestamp));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return conversations;
    }
}
