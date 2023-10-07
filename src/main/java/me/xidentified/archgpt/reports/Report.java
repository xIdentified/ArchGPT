package me.xidentified.archgpt.reports;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Report {
    private final String playerName;
    private final String reportType; // e.g., Inappropriate, Off-topic, Other
    private final String feedback;
    private final String npcResponse;
    private final LocalDateTime timestamp;
    private final String npcName;

    public Report(String playerName, String npcName, String reportType, Component feedbackComponent, String npcResponse, LocalDateTime timestamp) {
        this.playerName = playerName;
        this.npcName = npcName;
        this.reportType = reportType;
        this.feedback = PlainTextComponentSerializer.plainText().serialize(feedbackComponent);
        this.npcResponse = npcResponse;
        this.timestamp = timestamp;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getReportType() {
        return reportType;
    }

    public String getFeedback() {
        return feedback;
    }

    public String getNpcResponse() {
        return npcResponse;
    }

    public String getNpcName() {
        return npcName;
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