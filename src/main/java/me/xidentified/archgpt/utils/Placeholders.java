package me.xidentified.archgpt.utils;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.xidentified.archgpt.ArchGPT;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class Placeholders extends PlaceholderExpansion {
    private final ArchGPT plugin;

    public Placeholders(ArchGPT plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getAuthor() {
        return "xIdentified";
    }

    @Override
    public @NotNull String getIdentifier() {
        return "ArchGPT";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true; // This is required or else PlaceholderAPI will unregister the Expansion on reload
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        switch (params.toLowerCase()) {
            case "recent_message":
                // Assuming you have a method to get the most recent message received by the player
                return getMostRecentMessage(player);

            case "in_conversation":
                // Assuming you have a method to check if the player is currently in a conversation
                return isInConversation(player) ? "Yes" : "No";

            case "npc_name":
                // Assuming you have a method to get the name of the NPC the player is conversing with
                return getCurrentNPCName(player);

            default:
                return null;
        }
    }

    private String getMostRecentMessage(OfflinePlayer player) {
        return "Your most recent message"; // TODO: finish
    }

    private boolean isInConversation(OfflinePlayer player) {
        return plugin.getConversationManager().playerInConversation(player.getUniqueId());
    }

    private String getCurrentNPCName(OfflinePlayer player) {
        if (isInConversation(player)) {
            return "NPC Name"; // TODO: finish this
        }
        return "None";
    }

}