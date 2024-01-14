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
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ConversationUtils {
    private final ArchGPT plugin;
    private final ArchGPTConfig configHandler;
    private final NPCConversationManager manager;
    private final ConcurrentHashMap<UUID, AtomicInteger> conversationTokenCounters; //Ensures conversation doesn't go over token limit

    public ConversationUtils(ArchGPT plugin, ArchGPTConfig configHandler, NPCConversationManager manager) {
        this.plugin = plugin;
        this.configHandler = configHandler;
        this.manager = manager;
        this.conversationTokenCounters = new ConcurrentHashMap<>();
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

    public void sendPlayerMessage(Player player, Component playerMessage) {
        String messageText = PlainTextComponentSerializer.plainText().serialize(playerMessage);

        plugin.sendMessage(player, Messages.GENERAL_PLAYER_MESSAGE.formatted(
                Placeholder.unparsed("player_name", "You"),
                Placeholder.parsed("player_name_color", configHandler.getPlayerNameColor()),
                Placeholder.unparsed("message", messageText),
                Placeholder.parsed("message_color", configHandler.getPlayerMessageColor())
                ));
    }

    public void sendNPCMessage(Player player, String npcName, Component response) {
        boolean splitLongMessages = configHandler.isShouldSplitLongMsg();
        String responseText = PlainTextComponentSerializer.plainText().serialize(response);

        if (splitLongMessages) {
            // Split the response into parts after every few sentences and send each part as a separate message
            String[] sentences = responseText.split("(?<=[.!?])\\s+");
            for (int i = 0; i < sentences.length; i += 2) {
                String partText = StringUtils.join(sentences, ' ', i, Math.min(i + 2, sentences.length));
                sendMessageFormatted(player, partText, npcName);
            }
        } else {
            // Send the entire response as a single message
            sendMessageFormatted(player, responseText, npcName);
        }
    }

    private void sendMessageFormatted(Player player, String message, String npcName) {
        // Prepare and send formatted NPC message
        plugin.sendMessage(player, Messages.GENERAL_NPC_MESSAGE.formatted(
                Placeholder.unparsed("npc_name", npcName),
                Placeholder.parsed("npc_name_color", configHandler.getNpcNameColor()),
                Placeholder.unparsed("message", message),
                Placeholder.parsed("message_color", configHandler.getNpcMessageColor())
        ));
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

    // Update conversation token counter
    public void updateConversationTokenCounter(UUID playerUUID) {
        AtomicInteger tokenCounter = conversationTokenCounters.get(playerUUID);
        if (tokenCounter == null) {
            tokenCounter = new AtomicInteger(1);
        } else {
            int currentValue = tokenCounter.get();
            tokenCounter.set(currentValue + 1);
        }
        conversationTokenCounters.put(playerUUID, tokenCounter);
    }

}
