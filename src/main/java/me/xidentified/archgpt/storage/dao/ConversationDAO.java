package me.xidentified.archgpt.storage.dao;

import me.xidentified.archgpt.storage.model.Conversation;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

public interface ConversationDAO {
    void saveConversation(Conversation conversation);
    List<Conversation> getConversations(UUID playerUUID, String npcName, Duration memoryDuration);
    void clearAllConversations();
    void clearConversationsForNpc(String npcName);
}
