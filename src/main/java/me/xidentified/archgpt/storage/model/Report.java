package me.xidentified.archgpt.storage.model;

import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
@Getter
public class Report {
    private final int id; // Unique identifier for each report
    private final String playerName;
    private final String reportType; // e.g., Inappropriate, Off-topic, Other
    private final String feedback;
    private final String npcResponse;
    private final LocalDateTime timestamp;
    private final String npcName;

    public Report(int id, String playerName, String npcName, String reportType, Component feedbackComponent, String npcResponse, LocalDateTime timestamp) {
        this.id = id;
        this.playerName = playerName;
        this.npcName = npcName;
        this.reportType = reportType;
        this.feedback = PlainTextComponentSerializer.plainText().serialize(feedbackComponent);
        this.npcResponse = npcResponse;
        this.timestamp = timestamp;
    }

    public String getFormattedTimestamp() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return timestamp.format(formatter);
    }

    @Override
    public String toString() {
        return "Report ID: " + id + " - Report from " + playerName + " about NPC " + npcName + " on " + getFormattedTimestamp() + ": Type - " + reportType + ", Feedback - " + feedback + ", NPC Response - " + npcResponse;
    }
}
