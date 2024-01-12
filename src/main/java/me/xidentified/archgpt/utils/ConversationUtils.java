package me.xidentified.archgpt.utils;

import me.xidentified.archgpt.ArchGPT;
import me.xidentified.archgpt.ArchGPTConfig;
import me.xidentified.archgpt.ArchGPTConstants;
import me.xidentified.archgpt.NPCConversationManager;
import me.xidentified.archgpt.context.EnvironmentalContextProvider;
import me.xidentified.archgpt.context.PlayerContextProvider;
import me.xidentified.archgpt.storage.model.Conversation;
import me.xidentified.archgpt.storage.model.Report;
import net.citizensnpcs.api.npc.NPC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.time.LocalDateTime;
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

    public static TextColor fromConfigString(String colorCode) {

        if (colorCode == null || colorCode.isEmpty()) {
            return NamedTextColor.WHITE; // Default color if the code is empty
        }

        // Check if it's a legacy Minecraft color code
        if (colorCode.startsWith("&")) {
            Component translatedComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(colorCode + "text");
            if (translatedComponent.color() != null) {
                return translatedComponent.color();
            }
        }

        // Check if it's a Hex color code
        if (colorCode.startsWith("#")) {
            try {
                int hexValue = Integer.parseInt(colorCode.substring(1), 16);
                return TextColor.color(hexValue);
            } catch (NumberFormatException e) {
                // Invalid Hex color code, fall back to default
            }
        }

        return NamedTextColor.WHITE; // Default color if the code is invalid
    }

    public void sendPlayerMessage(Player player, Component playerMessage) {
        String messageText = PlainTextComponentSerializer.plainText().serialize(playerMessage);

        // Format and send player message using Messages class
        plugin.sendMessage(player, Messages.GENERAL_PLAYER_MESSAGE
                .replacePlaceholder("player_name", "You")
                .replacePlaceholder("message", messageText));

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Conversation conversation = new Conversation(player.getUniqueId(), "NPC_NAME", PlainTextComponentSerializer.plainText().serialize(playerMessage), System.currentTimeMillis());
            plugin.getConversationDAO().saveConversation(conversation);
        });
    }

    public void sendNPCMessage(Player player, String npcName, Component response) {
        boolean splitLongMessages = configHandler.isShouldSplitLongMsg();

        if (splitLongMessages) {
            // Split the response into parts and send each part as a separate message
            response.children().forEach(part -> {
                String partText = PlainTextComponentSerializer.plainText().serialize(part);
                sendMessageFormatted(player, partText, npcName);
            });
        } else {
            // Send the entire response as a single message
            String responseText = PlainTextComponentSerializer.plainText().serialize(response);
            sendMessageFormatted(player, responseText, npcName);
        }

        // Save NPC's message
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Conversation conversation = new Conversation(player.getUniqueId(), npcName, PlainTextComponentSerializer.plainText().serialize(response), System.currentTimeMillis());
            plugin.getConversationDAO().saveConversation(conversation);
        });
    }

    private void sendMessageFormatted(Player player, String message, String npcName) {
        // Format and send NPC message using Messages class
        plugin.sendMessage(player, Messages.GENERAL_NPC_MESSAGE
                .replacePlaceholder("npc_name", npcName)
                .replacePlaceholder("message", message));
    }

    private void sendIndividualNPCMessage(Player player, UUID playerUUID, String npcName, TextColor npcNameColor, Component npcMessageComponent) {
        Component npcNameComponent = Component.text(npcName + ": ")
                .color(npcNameColor);
        String uniqueMessageIdentifier = playerUUID.toString() + "_" + System.currentTimeMillis();

        if (plugin.getActiveConversations().containsKey(playerUUID)) {
            // Player is in a conversation, attach hover and click events
            Component messageComponent = npcNameComponent.append(npcMessageComponent)
                    .hoverEvent(HoverEvent.showText(Component.text("Click to report", NamedTextColor.RED)))
                    .clickEvent(ClickEvent.runCommand("/reportnpcmessage " + uniqueMessageIdentifier));

            player.sendMessage(messageComponent);
        } else {
            // Player is not in a conversation, send message without report option
            player.sendMessage(npcNameComponent.append(npcMessageComponent));
        }
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
