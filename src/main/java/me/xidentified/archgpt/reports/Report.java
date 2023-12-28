package me.xidentified.archgpt.reports;

import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Report {
    @Getter private final String playerName;
    @Getter private final String reportType; // e.g., Inappropriate, Off-topic, Other
    @Getter private final String feedback;
    @Getter private final String npcResponse;
    private final LocalDateTime timestamp;
    @Getter private final String npcName;

    public Report(String playerName, String npcName, String reportType, Component feedbackComponent, String npcResponse, LocalDateTime timestamp) {
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
        return "Report from " + playerName + " about NPC " + npcName + " on " + getFormattedTimestamp() + ": Type - " + reportType + ", Feedback - " + feedback + ", NPC Response - " + npcResponse;
    }


}