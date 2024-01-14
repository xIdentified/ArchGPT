package me.xidentified.archgpt.storage.dao;

import me.xidentified.archgpt.storage.model.Conversation;

import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MySQLConversationDAO implements ConversationDAO {
    private final String url;
    private final String username;
    private final String password;

    public MySQLConversationDAO(String host, int port, String database, String username, String password) {
        this.url = "jdbc:mysql://" + host + ":" + port + "/" + database;
        this.username = username;
        this.password = password;
        initializeDatabase();
        addIndexOnNPCName();
    }

    private void addIndexOnNPCName() {
        try (Connection conn = DriverManager.getConnection(url, username, password);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_npc_name ON conversations (npc_name);");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void initializeDatabase() {
        try (Connection conn = DriverManager.getConnection(url, username, password);
             PreparedStatement stmt = conn.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS conversations (" +
                             "player_uuid VARCHAR(36), " +
                             "npc_name VARCHAR(255), " +
                             "message TEXT, " +
                             "timestamp BIGINT, " +
                             "PRIMARY KEY (player_uuid, timestamp))")) {
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void saveConversation(Conversation conversation) {
        try (Connection conn = DriverManager.getConnection(url, username, password);
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO conversations (player_uuid, npc_name, message, timestamp, is_from_npc) VALUES (?, ?, ?, ?, ?)")) {
            stmt.setString(1, conversation.getPlayerUUID().toString());
            stmt.setString(2, conversation.getNpcName());
            stmt.setString(3, conversation.getMessage());
            stmt.setLong(4, conversation.getTimestamp());
            stmt.setBoolean(5, conversation.isFromNPC());
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<Conversation> getConversations(UUID playerUUID, String npcName, Duration memoryDuration) {
        List<Conversation> conversations = new ArrayList<>();
        long durationMillis = memoryDuration.toMillis();
        long cutoffTimestamp = Instant.now().toEpochMilli() - durationMillis;

        String query = "SELECT * FROM conversations WHERE player_uuid = ? AND npc_name = ? AND timestamp > ? ORDER BY timestamp DESC";
        try (Connection conn = DriverManager.getConnection(url, username, password);
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, playerUUID.toString());
            stmt.setString(2, npcName);
            stmt.setLong(3, cutoffTimestamp);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String message = rs.getString("message");
                    long timestamp = rs.getLong("timestamp");
                    boolean isFromNPC = rs.getBoolean("is_from_npc");
                    conversations.add(new Conversation(playerUUID, npcName, message, timestamp, isFromNPC));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return conversations;
    }

    @Override
    public void clearAllConversations() {
        try (Connection conn = DriverManager.getConnection(url, username, password);
             Statement stmt = conn.createStatement()) {
            // Delete all records from the 'conversations' table
            String sql = "DELETE FROM conversations";
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            // Handle exceptions
            throw new RuntimeException("Error clearing conversations: " + e.getMessage(), e);
        }
    }

    @Override
    public void clearConversationsForNpc(String npcName) {
        try (Connection conn = DriverManager.getConnection(url, username, password);
             PreparedStatement stmt = conn.prepareStatement(
                     "DELETE FROM conversations WHERE npc_name = ?")) {
            stmt.setString(1, npcName);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

}
