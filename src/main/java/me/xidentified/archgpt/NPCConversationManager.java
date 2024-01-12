package me.xidentified.archgpt;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import lombok.Getter;
import me.xidentified.archgpt.storage.model.Conversation;
import me.xidentified.archgpt.utils.ConversationTimeoutManager;
import me.xidentified.archgpt.utils.ConversationUtils;
import me.xidentified.archgpt.utils.Messages;
import net.citizensnpcs.api.npc.NPC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class NPCConversationManager {

    private final ArchGPT plugin;
    @Getter private final ArchGPTConfig configHandler;
    @Getter private final ConversationUtils conversationUtils;
    @Getter private final ChatRequestHandler chatRequestHandler; //Handles requests sent to ChatGPT
    @Getter private final ConversationTimeoutManager conversationTimeoutManager; //Handles conversation timeout logic
    public final Map<UUID, Long> npcCommentCooldown = new ConcurrentHashMap<>(); //Stores cooldown for NPC greeting to passing player
    public final Map<UUID, NPC> playerNPCMap = new ConcurrentHashMap<>(); //Stores the NPC the player is talking to
    public final ConcurrentHashMap<UUID, List<Component>> npcChatStatesCache;
    private final ConcurrentHashMap<UUID, Long> playerCooldowns; //Stores if the player is in a cooldown, which would cancel their sent message
    private static final JsonObject SYSTEM_MESSAGE = createStaticSystemMessage();

    public NPCConversationManager(ArchGPT plugin, ArchGPTConfig configHandler) {
        this.plugin = plugin;
        this.configHandler = configHandler;
        this.chatRequestHandler = new ChatRequestHandler(plugin);
        this.npcChatStatesCache = new ConcurrentHashMap<>();
        this.playerCooldowns = new ConcurrentHashMap<>();
        this.conversationTimeoutManager = new ConversationTimeoutManager(plugin);
        this.conversationUtils = new ConversationUtils(plugin, configHandler, this);
    }

    private static JsonObject createStaticSystemMessage() {
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", "You are an intelligent NPC capable of conversational interaction.");
        return systemMessage;
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
        messages.add(SYSTEM_MESSAGE);

        // Get combined context for the NPC with the specific greeting
        String combinedContext = conversationUtils.getCombinedContext(npc.getName(), player);
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

    public void startConversation(Player player, NPC npc) {
        UUID playerUUID = player.getUniqueId();
        String npcName = npc.getName();

        // Get combined context for the NPC
        String combinedContext = conversationUtils.getCombinedContext(npcName, player);

        // Prepare initial conversation state
        Component npcIntroComponent = Component.text(combinedContext);
        List<Component> initialConversationState = new ArrayList<>();
        initialConversationState.add(npcIntroComponent);

        // Store conversation state
        playerNPCMap.put(playerUUID, npc);
        npcChatStatesCache.put(playerUUID, initialConversationState);
        plugin.getActiveConversations().put(playerUUID, true);

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
        conversationUtils.sendPlayerMessage(player, playerMessage);

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
                            @SuppressWarnings("unchecked") // Safe after checking all elements
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
                                        conversationUtils.sendNPCMessage(player, playerUUID, npcName, response);
                                        npc.data().set("last_message", PlainTextComponentSerializer.plainText().serialize(response));
                                        Conversation conversation = new Conversation(player.getUniqueId(), npc.getName(), PlainTextComponentSerializer.plainText().serialize(response), System.currentTimeMillis());
                                        plugin.getConversationDAO().saveConversation(conversation);
                                    }
                                    hologramManager.removePlayerHologram(playerUUID);
                                }
                            }.runTaskLater(plugin, 20L);
                            conversationUtils.updateConversationTokenCounter(playerUUID);
                            getConversationTimeoutManager().resetConversationTimeout(playerUUID);
                        }
                    }
                }
            }
        });
    }

    public boolean playerInConversation(UUID playerUUID) {
        return plugin.getActiveConversations().containsKey(playerUUID);
    }

}