package me.xidentified.archgpt.context;

import lombok.Getter;
import java.util.LinkedList;
import java.util.Queue;

@Getter
public class MemoryContext {
    private final Queue<String> recentConversations; // Store recent conversations

    public MemoryContext() {
        this.recentConversations = new LinkedList<>();
    }

    public void addConversation(String conversation) {
        // Add new conversation, remove the oldest if exceeding limit
        if (recentConversations.size() >= 10) {
            recentConversations.poll();
        }
        recentConversations.offer(conversation);
    }

    public String getSummarizedContext() {
        // Logic to summarize or return the most relevant points
        return String.join(" ", recentConversations);
    }
}

