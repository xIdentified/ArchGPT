package me.xidentified.archgpt.storage.model;

import lombok.Getter;

import java.util.UUID;

@Getter
public class Conversation {
    private final UUID playerUUID;
    private final String npcName;
    private final String message;
    private final long timestamp;
    private final boolean isFromNPC;

    public Conversation(UUID playerUUID, String npcName, String message, long timestamp, boolean isFromNPC) {
        this.playerUUID = playerUUID;
        this.npcName = npcName;
        this.message = message;
        this.timestamp = timestamp;
        this.isFromNPC = isFromNPC;
    }
}
