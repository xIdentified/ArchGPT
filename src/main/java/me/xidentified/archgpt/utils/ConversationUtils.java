package me.xidentified.archgpt.utils;

import me.xidentified.archgpt.ArchGPT;
import me.xidentified.archgpt.ArchGPTConfig;
import me.xidentified.archgpt.NPCConversationManager;
import me.xidentified.archgpt.context.EnvironmentalContextProvider;
import me.xidentified.archgpt.context.PlayerContextProvider;
import me.xidentified.archgpt.storage.model.Report;
import net.citizensnpcs.api.npc.NPC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.time.LocalDateTime;
import java.util.*;

public class ConversationUtils {
    private final ArchGPT plugin;
    private final ArchGPTConfig configHandler;
    private final NPCConversationManager manager;

    public ConversationUtils(ArchGPT plugin, ArchGPTConfig configHandler, NPCConversationManager manager) {
        this.plugin = plugin;
        this.configHandler = configHandler;
        this.manager = manager;
    }

    // Fetches the current context surrounding the NPC
    public String getCombinedContext(String npcName, Player player) {
        // Fetch specific NPC prompt or use the default if none is set
        String npcPrompt = npcName.isEmpty() ? configHandler.getDefaultPrompt() : configHandler.getNpcPrompt(npcName, player);

        EnvironmentalContextProvider envContext = new EnvironmentalContextProvider(plugin, player);
        PlayerContextProvider playerContext = new PlayerContextProvider(player);
        String environmentalContext = envContext.getFormattedContext(npcPrompt);
        String playerSpecificContext = playerContext.getFormattedContext("");

        return environmentalContext + " " + playerSpecificContext;
    }

    public boolean isInLineOfSight(NPC npc, Player player) {
        if (!npc.isSpawned() || !npc.getEntity().getWorld().equals(player.getWorld())) {
            return false;
        }

        // Get the entity's location associated with the NPC
        Location npcLocation = npc.getEntity().getLocation();
        Location playerLocation = player.getLocation();

        // Validate locations
        if (isValidLocation(npcLocation) || isValidLocation(playerLocation)) {
            return false;
        }

        // Check if the player is within range of the NPC
        if (npcLocation.distance(playerLocation) > ArchGPTConstants.MAX_DISTANCE_LINE_OF_SIGHT) {
            return false;
        }

        // Use ray tracing to check if there's a direct line of sight
        Vector toPlayer = playerLocation.toVector().subtract(npcLocation.toVector()).normalize();
        RayTraceResult rayTrace = npcLocation.getWorld().rayTraceBlocks(npcLocation, toPlayer, 4, FluidCollisionMode.NEVER, true);

        // If the ray trace didn't hit anything, or it hit the player, then there's a direct line of sight
        return rayTrace == null || rayTrace.getHitEntity() == player;
    }

    private boolean isValidLocation(Location location) {
        return location == null || !Double.isFinite(location.getX()) || !Double.isFinite(location.getY()) || !Double.isFinite(location.getZ());
    }

    public boolean handleReportingState(Player player, AsyncPlayerChatEvent event) {
        UUID playerUUID = player.getUniqueId();

        if (plugin.getReportManager().selectingReportTypePlayers.contains(playerUUID)) {
            plugin.sendMessage(player, Messages.REPORT_SELECT_TYPE);
            event.setCancelled(true);
            return true;
        }

        if (plugin.getReportManager().isInReportingState(playerUUID)) {
            String msg = event.getMessage();
            Component feedback = Component.text(msg);

            String reportType = plugin.getReportManager().getSelectedReportType(playerUUID);
            NPC npc = manager.playerNPCMap.get(playerUUID);
            String npcLastMessage = npc.data().get("last_message");
            if (npcLastMessage == null) {
                npcLastMessage = "Unknown NPC Message";
            }
            int newReportId = -1;
            Report report = new Report(newReportId, player.getName(), npc.getName(), reportType, feedback, npcLastMessage, LocalDateTime.now());
            plugin.getReportManager().addReport(report);
            plugin.getReportManager().exitReportingState(playerUUID);
            plugin.sendMessage(player, Messages.REPORT_SUBMITTED.formatted(
                    Placeholder.unparsed("cancel", Objects.requireNonNull(plugin.getConfig().getString("conversation_end_phrase")))
            ));
            event.setCancelled(true);
            return true;
        }
        return false;
    }

    public void sendPlayerMessage(Player player, Component message) {

        plugin.sendMessage(player, Messages.GENERAL_PLAYER_MESSAGE
                        .insertObject("player", player)
                        .insertComponent("message", message));
    }

    public void sendNPCMessage(Player player, NPC npc, String response) {
        boolean splitLongMessages = configHandler.isShouldSplitLongMsg();
        int MAX_MESSAGE_LENGTH = 256;

        if (splitLongMessages) {
            // Split the response into sentences
            String[] sentences = response.split("(?<=[.!?])\\s+");
            StringBuilder partTextBuilder = new StringBuilder();

            for (String sentence : sentences) {
                // Check if adding the next sentence would exceed the max message length
                if (partTextBuilder.length() + sentence.length() + 1 > MAX_MESSAGE_LENGTH) {
                    // Send the current partText if adding another sentence would exceed the limit
                    sendMessageFormatted(player, npc, partTextBuilder.toString());
                    partTextBuilder = new StringBuilder(); // Reset the builder for the next part
                }
                if (!partTextBuilder.isEmpty()) partTextBuilder.append(" "); // Add space before the next sentence if not the first sentence
                partTextBuilder.append(sentence);
            }

            // Send any remaining text
            if (!partTextBuilder.isEmpty()) {
                sendMessageFormatted(player, npc, partTextBuilder.toString());
            }
        } else {
            // When not splitting by sentences, ensure splitting by word boundary if needed
            while (!response.isEmpty()) {
                if (response.length() <= MAX_MESSAGE_LENGTH) {
                    // If the remaining response is shorter than the limit, send it all
                    sendMessageFormatted(player, npc, response);
                    break;
                }

                // Find the last space within the MAX_MESSAGE_LENGTH to split the message
                int lastSpaceIndex = response.lastIndexOf(" ", MAX_MESSAGE_LENGTH);
                if (lastSpaceIndex == -1) lastSpaceIndex = MAX_MESSAGE_LENGTH; // Fallback if no spaces found

                String part = response.substring(0, lastSpaceIndex).trim();
                sendMessageFormatted(player, npc, part);

                // Remove the part that was just sent from the response
                response = response.substring(lastSpaceIndex).trim();
            }
        }
    }

    private void sendMessageFormatted(Player player, NPC npc, String message) {
        npc.data().set("last_message", message);

        plugin.sendMessage(player, Messages.GENERAL_NPC_MESSAGE
                .insertObject("npc", npc)
                .insertString("message", message));
    }

    public List<String> filterShortSentences(String message, int minLength) {
        List<String> filteredSentences = new ArrayList<>();
        String[] sentences = message.split("(?<!\\w\\.\\w.)(?<![A-Z][a-z]\\.)(?<=[.?!])\\s");

        for (String sentence : sentences) {
            if (sentence.length() >= minLength) {
                filteredSentences.add(sentence.trim());
            }
        }

        return filteredSentences;
    }

}
