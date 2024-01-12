package me.xidentified.archgpt;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import io.papermc.paper.event.player.AsyncChatEvent;
import lombok.Getter;
import me.xidentified.archgpt.context.EnvironmentalContextProvider;
import me.xidentified.archgpt.context.PlayerContextProvider;
import me.xidentified.archgpt.storage.model.Conversation;
import me.xidentified.archgpt.storage.model.Report;
import me.xidentified.archgpt.utils.ConversationTimeoutManager;
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
    private final ConcurrentHashMap<UUID, AtomicInteger> conversationTokenCounters; //Ensures conversation doesn't go over token limit
    @Getter private final ConversationTimeoutManager conversationTimeoutManager; //Handles conversation timeout logic
    public final Map<UUID, Long> npcCommentCooldown = new ConcurrentHashMap<>(); //Stores cooldown for NPC greeting to passing player
    public final Map<UUID, NPC> playerNPCMap = new ConcurrentHashMap<>(); //Stores the NPC the player is talking to
    public final ConcurrentHashMap<UUID, List<Component>> npcChatStatesCache;
    protected final ConcurrentHashMap<UUID, Long> playerCooldowns; //Stores if the player is in a cooldown, which would cancel their sent message

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
        String greetingContext = combinedContext + " The player, " + player.getName() + ", approaches you. How do you greet them?";

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

        // Store conversation state
        playerNPCMap.put(playerUUID, npc);
        npcChatStatesCache.put(playerUUID, initialConversationState);
        synchronized (plugin.getActiveConversations()) {
            plugin.getActiveConversations().put(playerUUID, true);
        }

        plugin.sendMessage(player, Messages.CONVERSATION_STARTED.formatted(
                Placeholder.unparsed("npc", npcName),
                Placeholder.unparsed("cancel", Objects.requireNonNull(plugin.getConfig().getString("conversation_end_phrase")))
        ));

        // Fetch past conversations asynchronously and update the conversation state
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Conversation> pastConversations = plugin.getConversationDAO().getConversations(playerUUID, npcName);
            // Ensure this runs on the main thread
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                List<Component> updatedConversationState = new ArrayList<>(initialConversationState);
                for (Conversation pastConversation : pastConversations) {
                    Component pastMessage = Component.text(pastConversation.getMessage());
                    updatedConversationState.add(pastMessage);
                    plugin.debugLog("Added previous message: " + pastMessage);
                }
                npcChatStatesCache.put(playerUUID, updatedConversationState);
            });
        });

        // Start timeout
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

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Conversation conversation = new Conversation(player.getUniqueId(), "NPC_NAME", PlainTextComponentSerializer.plainText().serialize(playerMessage), System.currentTimeMillis());
            plugin.getConversationDAO().saveConversation(conversation);
        });
    }

    public void sendNPCMessage(Player player, UUID playerUUID, String npcName, Component response) {
        TextColor npcNameColor = fromConfigString(configHandler.getNpcNameColor());
        TextColor npcMessageColor = fromConfigString(configHandler.getNpcMessageColor());

        boolean splitLongMessages = configHandler.isShouldSplitLongMsg();

        if (splitLongMessages) {
            // Split the response into parts and send each part as a separate message
            response.children().forEach(part -> {
                Component npcMessageComponent = part.color(npcMessageColor);
                sendIndividualNPCMessage(player, playerUUID, npcName, npcNameColor, npcMessageComponent);
            });
        } else {
            // Send the entire response as a single message
            Component npcMessageComponent = response.color(npcMessageColor);
            sendIndividualNPCMessage(player, playerUUID, npcName, npcNameColor, npcMessageComponent);
        }

        // Save NPC's message
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Conversation conversation = new Conversation(player.getUniqueId(), npcName, PlainTextComponentSerializer.plainText().serialize(response), System.currentTimeMillis());
            plugin.getConversationDAO().saveConversation(conversation);
        });
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

    public boolean isInActiveConversation(UUID playerUUID) {
        return plugin.getActiveConversations().containsKey(playerUUID);
    }

    public boolean handleReportingState(Player player, AsyncChatEvent event) {
        UUID playerUUID = player.getUniqueId();

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
            plugin.sendMessage(player, Messages.REPORT_SUBMITTED.formatted(
                    Placeholder.unparsed("cancel", Objects.requireNonNull(plugin.getConfig().getString("conversation_end_phrase")))
            ));
            event.setCancelled(true);
            return true;
        }
        return false;
    }


    public boolean handleCancelCommand(Player player, String message) {
        if (message.equalsIgnoreCase(plugin.getConfig().getString("conversation_end_phrase", "cancel"))) {
            plugin.sendMessage(player, Messages.CONVERSATION_ENDED);
            endConversation(player.getUniqueId());
            return true;
        }
        return false;
    }

    public void processPlayerMessage(Player player, Component playerMessage, HologramManager hologramManager) {
        UUID playerUUID = player.getUniqueId();

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
        Gson gson = new GsonBuilder().create();
        String sanitizedPlayerMessage = gson.toJson(PlainTextComponentSerializer.plainText().serialize(playerMessage));

        // Since gson.toJson() adds additional quotes, we need to remove them
        sanitizedPlayerMessage = sanitizedPlayerMessage.substring(1, sanitizedPlayerMessage.length() - 1);

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

                    Object leftObject = rawPair.getLeft();
                    Object rightObject = rawPair.getRight();

                    if (leftObject instanceof Component response && rightObject instanceof List<?> rawList) {
                        // Check if the list contains Components
                        if (rawList.stream().allMatch(item -> item instanceof Component)) {
                            @SuppressWarnings("unchecked") // Safe cast after checking all elements
                            List<Component> updatedConversationState = (List<Component>) rawList;

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
                                        Conversation conversation = new Conversation(player.getUniqueId(), npc.getName(), PlainTextComponentSerializer.plainText().serialize(response), System.currentTimeMillis());
                                        plugin.getConversationDAO().saveConversation(conversation);
                                    }
                                    hologramManager.removePlayerHologram(playerUUID);
                                }
                            }.runTaskLater(plugin, 20L);
                            updateConversationTokenCounter(playerUUID);
                            getConversationTimeoutManager().resetConversationTimeout(playerUUID);
                        }
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