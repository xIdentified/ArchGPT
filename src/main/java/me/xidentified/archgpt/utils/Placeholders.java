package me.xidentified.archgpt.utils;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.xidentified.archgpt.ArchGPT;
import me.xidentified.archgpt.storage.model.Conversation;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

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
        UUID playerUUID = player.getUniqueId();
        String npcName = getCurrentNPCName(player);
        Duration memoryDuration = Duration.ofHours(1);

        List<Conversation> conversations = plugin.getConversationDAO().getConversations(playerUUID, npcName, memoryDuration);
        if (conversations.isEmpty()) {
            return "No recent messages";
        }

        // TODO: Get the last message
        return null;
    }

    private boolean isInConversation(OfflinePlayer player) {
        return plugin.getConversationManager().playerInConversation(player.getUniqueId());
    }

    private String getCurrentNPCName(OfflinePlayer player) {
        if (isInConversation(player)) {
            NPC npc = plugin.getConversationManager().playerNPCMap.get(player.getUniqueId());
            if (npc != null) return npc.getName();
        }
        return "None";
    }

}