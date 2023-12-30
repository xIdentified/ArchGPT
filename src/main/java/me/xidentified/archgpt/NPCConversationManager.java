package me.xidentified.archgpt;

import com.google.gson.JsonArray;
import io.papermc.paper.event.player.AsyncChatEvent;
import lombok.Getter;
import me.xidentified.archgpt.storage.model.Report;
import me.xidentified.archgpt.utils.Messages;
import me.xidentified.archgpt.utils.TranslationService;
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
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
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
    public final Map<UUID, Long> npcCommentCooldown = new HashMap<>(); //Stores cooldown for NPC greeting to passing player
    public final Map<UUID, NPC> playerNPCMap = new ConcurrentHashMap<>(); //Stores the NPC the player is talking to
    public final Map<UUID, List<Component>> npcChatStatesCache;
    protected final Map<UUID, Long> playerCooldowns; //Stores if the player is in a cooldown, which would cancel their sent message

    private ConfigurationSection npcSection;
    private int maxResponseLength;

    public NPCConversationManager(ArchGPT plugin, ArchGPTConfig configHandler) {
        this.plugin = plugin;
        this.configHandler = configHandler;
        this.chatRequestHandler = new ChatRequestHandler(plugin);
        this.npcChatStatesCache = new ConcurrentHashMap<>();
        this.playerCooldowns = new ConcurrentHashMap<>();
        this.conversationTokenCounters = new ConcurrentHashMap<>();
        this.conversationTimeoutManager = new ConversationTimeoutManager(plugin);
        loadConfigurations();
    }

    private void loadConfigurations() {
        // Configuration values that will be loaded once
        FileConfiguration config = plugin.getConfig();
        npcSection = config.getConfigurationSection("npcs");
        maxResponseLength = configHandler.getMaxResponseLength();
    }

    public void startConversation(Player player, NPC npc) {
        UUID playerUUID = player.getUniqueId();
        String npcName = npc.getName();

        // Player is not in an ongoing conversation, start a new one
        playerNPCMap.put(playerUUID, npc);
        EnvironmentalContextProvider envContext = new EnvironmentalContextProvider(player);
        String defaultPrompt = configHandler.getDefaultPrompt();
        String npcSpecificPrompt = npcSection.getString(npcName, "");
        String npcPrompt = defaultPrompt + (npcSpecificPrompt.isEmpty() ? "" : " " + npcSpecificPrompt);
        String formattedPrompt = envContext.getFormattedContext(npcPrompt);

        Component npcIntroComponent = Component.text(formattedPrompt);
        List<Component> initialConversationState = new ArrayList<>();
        initialConversationState.add(npcIntroComponent);

        plugin.debugLog("Initial Conversation State: " + initialConversationState);

        npcChatStatesCache.put(playerUUID, initialConversationState);
        synchronized (plugin.getActiveConversations()) {
            plugin.getActiveConversations().put(playerUUID, true);
        }

        plugin.sendMessage(player, Messages.CONVERSATION_STARTED.formatted(
                Placeholder.unparsed("npc", npcName)
                ));

        conversationTimeoutManager.startConversationTimeout(playerUUID);

        if (!npcSection.contains(npcName)) {
            synchronized (plugin.getActiveConversations()) {
                plugin.getActiveConversations().remove(playerUUID);
            }
        }
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
        plugin.getHologramManager().removeHologram();
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

    public CompletableFuture<Component> getGreeting(Component prompt, Player player) {
        // Prepare the API request
        JsonObject requestBodyJson = new JsonObject();

        // Specify the model
        String chatGptEngine = configHandler.getChatGptEngine();
        requestBodyJson.addProperty("model", chatGptEngine);

        // Create messages array
        JsonArray messages = new JsonArray();

        // Add system prompt message
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", "You are an intelligent NPC capable of conversational interaction.");
        messages.add(systemMessage);

        // Add user prompt message if needed
        if (prompt != null && !prompt.equals("")) {
            JsonObject userMessage = new JsonObject();
            String promptText = PlainTextComponentSerializer.plainText().serialize(prompt);
            userMessage.addProperty("role", "user");
            userMessage.addProperty("content", promptText);
            messages.add(userMessage);
        }

        // Add messages to the request body
        requestBodyJson.add("messages", messages);

        // Use the processChatGPTRequest method with RequestType.GREETING
        return getChatRequestHandler()
                .processChatGPTRequest(player, requestBodyJson, ChatRequestHandler.RequestType.GREETING, null, null)
                .thenApply(responseObject -> (Component) responseObject); // Cast the responseObject to Component
    }


    public boolean isInLineOfSight(NPC npc, Player player) {
        if (!npc.isSpawned() || !npc.getEntity().getWorld().equals(player.getWorld())) {
            return false;
        }

        // Get the entity's location associated with the NPC
        Location npcLocation = npc.getEntity().getLocation();
        Location playerLocation = player.getLocation();

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

    public boolean canComment(NPC npc) {
        long lastCommentTime = npcCommentCooldown.getOrDefault(npc.getUniqueId(), 0L);
        long commentCooldown = 3 * 60 * 1000; // 3 minutes in milliseconds
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
            player.sendMessage(Component.text("Please click one of the report types above to continue.", NamedTextColor.RED));
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
        if (message.equalsIgnoreCase("cancel")) {
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
                    hologramManager.removeHologram();
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

        // Add the player's current message
        JsonObject userMessageJson = new JsonObject();
        String sanitizedPlayerMessage = StringEscapeUtils.escapeJson(PlainTextComponentSerializer.plainText().serialize(playerMessage));
        userMessageJson.addProperty("role", "user");
        userMessageJson.addProperty("content", sanitizedPlayerMessage);
        messages.add(userMessageJson);

        // Set the 'model' and 'messages' fields in the request
        jsonRequest.addProperty("model", configHandler.getChatGptEngine());
        jsonRequest.add("messages", messages);

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
                                hologramManager.removeHologram();
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