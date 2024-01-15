package me.xidentified.archgpt.utils;

import com.google.gson.JsonObject;
import me.xidentified.archgpt.ArchGPT;
import me.xidentified.archgpt.ArchGPTConfig;
import me.xidentified.archgpt.NPCConversationManager;
import me.xidentified.archgpt.context.EnvironmentalContextProvider;
import me.xidentified.archgpt.context.PlayerContextProvider;
import me.xidentified.archgpt.storage.model.Conversation;
import me.xidentified.archgpt.storage.model.Report;
import net.citizensnpcs.api.npc.NPC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.time.Instant;
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
        String tokenContext = "Use no more than " + configHandler.getMaxResponseLength() + " completion_tokens in your response.";

        return environmentalContext + " " + playerSpecificContext + " " + tokenContext;
    }

    public String getTimeContext(long pastTimestamp) {
        long currentTimestamp = Instant.now().toEpochMilli();
        long timeDifferenceMillis = currentTimestamp - pastTimestamp;
        long timeDifferenceInMinecraftDays = timeDifferenceMillis / (1200 * 1000);  // Convert milliseconds to Minecraft days

        if (timeDifferenceInMinecraftDays < 1) {
            return "Earlier today";
        } else if (timeDifferenceInMinecraftDays < 7) {
            return "A few days ago";
        } else if (timeDifferenceInMinecraftDays < 30) {
            return "Earlier this month";
        } else {
            return "Some time ago";
        }
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

    public boolean canComment(NPC npc) {
        long lastCommentTime = manager.npcCommentCooldown.getOrDefault(npc.getUniqueId(), 0L);
        long commentCooldown = ArchGPTConstants.GREETING_COOLDOWN_MS;
        return System.currentTimeMillis() - lastCommentTime > commentCooldown;
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

        if (splitLongMessages) {
            // Split the response into parts after every few sentences and send each part as a separate message
            String[] sentences = response.split("(?<=[.!?])\\s+");
            for (int i = 0; i < sentences.length; i += 2) {
                String partText = StringUtils.join(sentences, ' ', i, Math.min(i + 2, sentences.length));
                sendMessageFormatted(player, npc, partText);
            }
        } else {
            // Send the entire response as a single message
            sendMessageFormatted(player, npc, response);
        }
    }

    private void sendMessageFormatted(Player player, NPC npc, String message) {
        // Prepare and send formatted NPC message
        plugin.sendMessage(player, Messages.GENERAL_NPC_MESSAGE
                .insertObject("npc", npc)
                .insertString("message", message));
    }

    public List<String> filterShortSentences(String message, int minLength) {
        List<String> filteredSentences = new ArrayList<>();
        String[] sentences = message.split("(?<!\\w\\.\\w.)(?<![A-Z][a-z]\\.)(?<=\\.|\\?|!)\\s");

        for (String sentence : sentences) {
            if (sentence.length() >= minLength) {
                filteredSentences.add(sentence.trim());
            }
        }

        return filteredSentences;
    }

}
