package me.xidentified.archgpt;

import com.google.gson.JsonArray;
import io.papermc.paper.event.player.AsyncChatEvent;
import lombok.Getter;
import me.xidentified.archgpt.context.EnvironmentalContextProvider;
import me.xidentified.archgpt.context.PlayerContextProvider;
import me.xidentified.archgpt.storage.model.Report;
import me.xidentified.archgpt.utils.Messages;
import net.citizensnpcs.api.npc.NPC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.apache.commons.lang3.StringEscapeUtils;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.apache.commons.lang3.tuple.Pair;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class NPCConversationManager {

    private final ArchGPT plugin;
    @Getter private final ArchGPTConfig configHandler;
    @Getter private final ChatRequestHandler chatRequestHandler; //Handles requests sent to ChatGPT
    private final Map<UUID, AtomicInteger> conversationTokenCounters; //Ensures conversation doesn't go over token limit
    @Getter private final ConversationTimeoutManager conversationTimeoutManager; //Handles conversation timeout logic
    public final Map<UUID, Long> npcCommentCooldown = new ConcurrentHashMap<>(); //Stores cooldown for NPC greeting to passing player
    public final Map<UUID, NPC> playerNPCMap = new ConcurrentHashMap<>(); //Stores the NPC the player is talking to
    public final Map<UUID, List<Component>> npcChatStatesCache;
    protected final Map<UUID, Long> playerCooldowns; //Stores if the player is in a cooldown, which would cancel their sent message

    public NPCConversationManager(ArchGPT plugin, ArchGPTConfig configHandler) {
        this.plugin = plugin;
        this.configHandler = configHandler;
        this.chatRequestHandler = new ChatRequestHandler(plugin);
        this.npcChatStatesCache = new ConcurrentHashMap<>();
        this.playerCooldowns = new ConcurrentHashMap<>();
        this.conversationTokenCounters = new ConcurrentHashMap<>();
        this.conversationTimeoutManager = new ConversationTimeoutManager(plugin);
    }

    public String getCombinedContext(String npcName, Player player) {
        // Fetch specific NPC prompt or use the default if none is set
        String npcPrompt = npcName.isEmpty() ? configHandler.getDefaultPrompt() : configHandler.getNpcPrompt(npcName, player);

        EnvironmentalContextProvider envContext = new EnvironmentalContextProvider(plugin, player);
        PlayerContextProvider playerContext = new PlayerContextProvider(player);
        String environmentalContext = envContext.getFormattedContext(npcPrompt);
        String playerSpecificContext = playerContext.getFormattedContext("");

        return environmentalContext + " " + playerSpecificContext;
    }

    public CompletableFuture<Component> getGreeting(Player player, NPC npc) {
        // Prepare the API request
        JsonObject requestBodyJson = new JsonObject();
        String chatGptEngine = configHandler.getChatGptEngine();
        requestBodyJson.addProperty("model", chatGptEngine);
        requestBodyJson.addProperty("max_tokens", configHandler.getMaxResponseLength());

        // Create messages array
        JsonArray messages = new JsonArray();

        // Add system prompt message
        messages.add(createSystemMessage());

        // Get combined context for the NPC with the specific greeting
        String combinedContext = getCombinedContext(npc.getName(), player);
        String greetingContext = combinedContext + " A player named " + player.getName() + " approaches you. How do you greet them?";

        // Add user prompt message with combined context
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", greetingContext);
        messages.add(userMessage);

        // Add messages to the request body and process the request
        requestBodyJson.add("messages", messages);
        return getChatRequestHandler().processChatGPTRequest(player, requestBodyJson, ChatRequestHandler.RequestType.GREETING, null, null)
                .thenApply(responseObject -> (Component) responseObject);
    }

    private JsonObject createSystemMessage() {
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", "You are an intelligent NPC capable of conversational interaction.");
        return systemMessage;
    }

    public void startConversation(Player player, NPC npc) {
        UUID playerUUID = player.getUniqueId();
        String npcName = npc.getName();

        // Get combined context for the NPC
        String combinedContext = getCombinedContext(npcName, player);

        // Prepare initial conversation state
        Component npcIntroComponent = Component.text(combinedContext);
        List<Component> initialConversationState = new ArrayList<>();
        initialConversationState.add(npcIntroComponent);

        // Store conversation state and start conversation
        npcChatStatesCache.put(playerUUID, initialConversationState);
        synchronized (plugin.getActiveConversations()) {
            plugin.getActiveConversations().put(playerUUID, true);
        }

        plugin.sendMessage(player, Messages.CONVERSATION_STARTED.formatted(
                Placeholder.unparsed("npc", npcName),
                Placeholder.unparsed("cancel", Objects.requireNonNull(plugin.getConfig().getString("conversation_end_phrase")))
        ));
        conversationTimeoutManager.startConversationTimeout(playerUUID);
    }

    public void endConversation(UUID playerUUID) {
        plugin.debugLog("Conversation ended for player " + playerUUID);

        synchronized (npcChatStatesCache) {
            npcChatStatesCache.remove(playerUUID);
        }

        synchronized (plugin.getActiveConversations()){
            plugin.getActiveConversations().remove(playerUUID);
        }

        conversationTimeoutManager.cancelConversationTimeout(playerUUID);
        plugin.getHologramManager().removePlayerHologram(playerUUID);
    }

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

    public boolean isInLineOfSight(NPC npc, Player player) {
        if (!npc.isSpawned() || !npc.getEntity().getWorld().equals(player.getWorld())) {
            return false;
        }

        // Get the entity's location associated with the NPC
        Location npcLocation = npc.getEntity().getLocation();
        Location playerLocation = player.getLocation();

        // Validate locations
        if (!isValidLocation(npcLocation) || !isValidLocation(playerLocation)) {
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
        return location != null && Double.isFinite(location.getX()) && Double.isFinite(location.getY()) && Double.isFinite(location.getZ());
    }

    public boolean canComment(NPC npc) {
        long lastCommentTime = npcCommentCooldown.getOrDefault(npc.getUniqueId(), 0L);
        long commentCooldown = ArchGPTConstants.GREETING_COOLDOWN_MS;
        return System.currentTimeMillis() - lastCommentTime > commentCooldown;
    }


    public void sendPlayerMessage(Player player, Component playerMessage) {
        TextColor playerNameColor = fromConfigString(configHandler.getPlayerNameColor());
        TextColor playerMessageColor = fromConfigString(configHandler.getPlayerMessageColor());

        Component playerNameComponent = Component.text("You: ")
                .color(playerNameColor);

        Component playerMessageComponent = playerMessage.color(playerMessageColor);

        player.sendMessage(playerNameComponent.append(playerMessageComponent));
    }

    public void sendNPCMessage(Player player, UUID playerUUID, String npcName, Component response) {
        TextColor npcNameColor = fromConfigString(configHandler.getNpcNameColor());
        TextColor npcMessageColor = fromConfigString(configHandler.getNpcMessageColor());

        Component npcNameComponent = Component.text(npcName + ": ")
                .color(npcNameColor);

        Component npcMessageComponent = response.color(npcMessageColor);

        String uniqueMessageIdentifier = playerUUID.toString() + "_" + System.currentTimeMillis();

        Component messageComponent = npcNameComponent.append(npcMessageComponent)
                .hoverEvent(HoverEvent.showText(Component.text("Click to report", NamedTextColor.RED)))
                .clickEvent(ClickEvent.runCommand("/reportnpcmessage " + uniqueMessageIdentifier));

        player.sendMessage(messageComponent);
    }

    public boolean isInActiveConversation(UUID playerUUID) {
        return plugin.getActiveConversations().containsKey(playerUUID);
    }

    public boolean handleReportingState(Player player, UUID playerUUID, AsyncChatEvent event) {
        if (plugin.getReportManager().selectingReportTypePlayers.contains(playerUUID)) {
            plugin.sendMessage(player, Messages.REPORT_SELECT_TYPE);
            event.setCancelled(true);
            return true;
        }

        if (plugin.getReportManager().isInReportingState(playerUUID)) {
            Component feedback = event.originalMessage();
            String reportType = plugin.getReportManager().getSelectedReportType(playerUUID);
            NPC npc = playerNPCMap.get(playerUUID);
            String npcLastMessage = npc.data().get("last_message");
            if (npcLastMessage == null) {
                npcLastMessage = "Unknown NPC Message";
            }
            int newReportId = -1;
            Report report = new Report(newReportId, player.getName(), npc.getName(), reportType, feedback, npcLastMessage, LocalDateTime.now());
            plugin.getReportManager().addReport(report);
            plugin.getReportManager().exitReportingState(playerUUID);
            plugin.sendMessage(player, Messages.REPORT_SUBMITTED);
            event.setCancelled(true);
            return true;
        }
        return false;
    }


    public boolean handleCancelCommand(Player player, UUID playerUUID, String message) {
        if (message.equalsIgnoreCase(plugin.getConfig().getString("conversation_end_phrase", "cancel"))) {
            plugin.sendMessage(player, Messages.CONVERSATION_ENDED);
            endConversation(playerUUID);
            return true;
        }
        return false;
    }

    public void processPlayerMessage(Player player, UUID playerUUID, Component playerMessage, HologramManager hologramManager) {
        if (PlainTextComponentSerializer.plainText().serialize(playerMessage).length() < configHandler.getMinCharLength()) {
            plugin.sendMessage(player, Messages.MSG_TOO_SHORT.formatted(
                    Placeholder.unparsed("size", String.valueOf(configHandler.getMinCharLength()))
            ));
            return;
        }

        // Send player message
        sendPlayerMessage(player, playerMessage);

        // Start animation over NPC head while it processes response
        new BukkitRunnable() {
            @Override
            public void run() {
                NPC npc = playerNPCMap.get(playerUUID);
                if (npc == null) return;
                if (npc.isSpawned()) {
                    hologramManager.removePlayerHologram(playerUUID);
                    Location npcLocation = npc.getEntity().getLocation();
                    String hologramText = "...";
                    hologramManager.createHologram(playerUUID, npcLocation.add(0, 1, 0), hologramText);
                }
                hologramManager.animateHologram();
            }
        }.runTask(plugin);

        // Cooldown logic
        long currentTimeMillis = System.currentTimeMillis();
        if (playerCooldowns.containsKey(playerUUID)) {
            long lastTriggerTimeMillis = playerCooldowns.get(playerUUID);
            long cooldownMillis = getConfigHandler().getChatCooldownMillis();
            if (currentTimeMillis - lastTriggerTimeMillis < cooldownMillis) {
                return;
            }
        }
        playerCooldowns.put(playerUUID, currentTimeMillis);

        // Process chat request
        List<Component> conversationState = npcChatStatesCache.get(playerUUID);

        // Prepare the request payload as a JsonObject
        JsonObject jsonRequest = new JsonObject();
        JsonArray messages = new JsonArray();

        // Add previous conversation messages to 'messages'
        for (Component messageComponent : conversationState) {
            JsonObject messageJson = new JsonObject();
            String role = (messageComponent.style().color() == NamedTextColor.WHITE) ? "assistant" : "user";
            String content = PlainTextComponentSerializer.plainText().serialize(messageComponent);
            messageJson.addProperty("role", role);
            messageJson.addProperty("content", content);
            messages.add(messageJson);
        }

        // Add the player's current message, with parsed placeholders
        JsonObject userMessageJson = new JsonObject();
        String sanitizedPlayerMessage = StringEscapeUtils.escapeJson(PlainTextComponentSerializer.plainText().serialize(playerMessage));

        userMessageJson.addProperty("role", "user");
        userMessageJson.addProperty("content", sanitizedPlayerMessage);
        messages.add(userMessageJson);

        // Set the 'model', 'messages', and 'max_tokens' fields in the request
        jsonRequest.addProperty("model", configHandler.getChatGptEngine());
        jsonRequest.add("messages", messages);
        jsonRequest.addProperty("max_tokens", configHandler.getMaxResponseLength());

        // Send the request
        CompletableFuture<Object> future = getChatRequestHandler().processChatGPTRequest(player, jsonRequest, ChatRequestHandler.RequestType.CONVERSATION, playerMessage, conversationState);

        future.thenAccept(responseObject -> {
            synchronized (npcChatStatesCache) {
                if (!plugin.getActiveConversations().containsKey(playerUUID)) return;
                if (responseObject instanceof Pair<?, ?> rawPair) {

                    if (rawPair.getLeft() instanceof Component response && rawPair.getRight() instanceof List) {
                        List<Component> updatedConversationState = (List<Component>) rawPair.getRight();

                        if (!conversationState.equals(updatedConversationState)) {
                            npcChatStatesCache.put(playerUUID, updatedConversationState);
                        }
                        NPC npc = playerNPCMap.get(playerUUID);
                        String npcName = npc.getName();
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (plugin.getActiveConversations().containsKey(playerUUID)) {
                                    sendNPCMessage(player, playerUUID, npcName, response);
                                    npc.data().set("last_message", PlainTextComponentSerializer.plainText().serialize(response));
                                }
                                hologramManager.removePlayerHologram(playerUUID);
                            }
                        }.runTaskLater(plugin, 20L);
                        updateConversationTokenCounter(playerUUID);
                        getConversationTimeoutManager().resetConversationTimeout(playerUUID);
                    }
                }
            }
        });
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

}