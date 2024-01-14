package me.xidentified.archgpt;

import com.google.gson.JsonArray;
import lombok.Getter;
import me.xidentified.archgpt.storage.model.Conversation;
import me.xidentified.archgpt.utils.ConversationTimeoutManager;
import me.xidentified.archgpt.utils.ConversationUtils;
import me.xidentified.archgpt.utils.Messages;
import net.citizensnpcs.api.npc.NPC;
import net.kyori.adventure.text.Component;
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
    public final ConcurrentHashMap<UUID, List<JsonObject>> npcChatStatesCache;
    private final ConcurrentHashMap<UUID, Long> playerCooldowns; //Stores if the player is in a cooldown, which would cancel their sent message

    public NPCConversationManager(ArchGPT plugin, ArchGPTConfig configHandler) {
        this.plugin = plugin;
        this.configHandler = configHandler;
        this.chatRequestHandler = new ChatRequestHandler(plugin);
        this.npcChatStatesCache = new ConcurrentHashMap<>();
        this.playerCooldowns = new ConcurrentHashMap<>();
        this.conversationTimeoutManager = new ConversationTimeoutManager(plugin);
        this.conversationUtils = new ConversationUtils(plugin, configHandler, this);
    }

    public JsonObject createSystemMessage(NPC npc, Player player) {
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");

        // Use conversationUtils instance field
        String combinedContext = this.conversationUtils.getCombinedContext(npc.getName(), player);

        // Construct the system message content
        String systemMessageContent = "Your name is " + npc.getName() + ". You are an intelligent NPC in a Minecraft Java server " +
                "capable of conversational interaction. Do not make any reference to being an AI, it ruins immersion. " +
                "Always respond as your living character, utilizing the following context: " + combinedContext;
        systemMessage.addProperty("content", systemMessageContent);

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
        JsonObject systemMessage = createSystemMessage(npc, player);
        messages.add(systemMessage);

        // Get combined context for the NPC with the specific greeting
        String combinedContext = conversationUtils.getCombinedContext(npc.getName(), player);
        String greetingContext = combinedContext + " A player known as " + player.getName() + " approaches you. How do you greet them?";

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

        // Store conversation state
        playerNPCMap.put(playerUUID, npc);
        List<JsonObject> initialConversationState = new ArrayList<>();

        // Add the system message with NPC's context
        JsonObject systemMessageJson = createSystemMessage(npc, player);
        initialConversationState.add(systemMessageJson);  // This is now a JsonObject directly

        // Store the initial conversation state as JsonObjects
        npcChatStatesCache.put(playerUUID, initialConversationState);
        plugin.getActiveConversations().put(playerUUID, true);

        plugin.sendMessage(player, Messages.CONVERSATION_STARTED.formatted(
                Placeholder.unparsed("npc", npcName),
                Placeholder.unparsed("cancel", Objects.requireNonNull(plugin.getConfig().getString("conversation_end_phrase")))
        ));

        // Update conversation state with past conversations
        addPastConversations(playerUUID, npcName);

        // Start timeout
        conversationTimeoutManager.startConversationTimeout(playerUUID);
    }

    public void addPastConversations(UUID playerUUID, String npcName) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Conversation> pastConversations = plugin.getConversationDAO().getConversations(playerUUID, npcName, configHandler.getNpcMemoryDuration());
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                List<JsonObject> updatedConversationState = new ArrayList<>();
                for (Conversation pastConversation : pastConversations) {
                    JsonObject pastMessageJson = new JsonObject();
                    String role = pastConversation.isFromNPC() ? "assistant" : "user";
                    pastMessageJson.addProperty("role", role);
                    pastMessageJson.addProperty("content", pastConversation.getMessage());
                    updatedConversationState.add(pastMessageJson);
                    plugin.debugLog("Added previous message: " + pastConversation.getMessage());
                }
                // Add the initial conversation state, if any, at the beginning of the list
                List<JsonObject> initialConversationState = npcChatStatesCache.getOrDefault(playerUUID, new ArrayList<>());
                updatedConversationState.addAll(0, initialConversationState);
                npcChatStatesCache.put(playerUUID, updatedConversationState);
            });
        });
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
        List<JsonObject> conversationState = npcChatStatesCache.get(playerUUID);

        // Prepare the request payload as a JsonObject
        JsonObject jsonRequest = new JsonObject();
        JsonArray messages = new JsonArray();

        // Add previous conversation messages to 'messages'
        for (JsonObject messageJson : conversationState) {
            messages.add(messageJson);
        }

        // Add the player's current message as a new JsonObject
        JsonObject userMessageJson = new JsonObject();
        String sanitizedPlayerMessage = PlainTextComponentSerializer.plainText().serialize(playerMessage);
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

                    if (leftObject instanceof Component response && rightObject instanceof List<?>) {
                        // Check if the list contains JsonObjects
                        if (((List<?>) rawPair.getRight()).stream().allMatch(item -> item instanceof JsonObject)) {
                            @SuppressWarnings("unchecked") // Safe after checking all elements
                            List<JsonObject> updatedConversationState = (List<JsonObject>) rawPair.getRight();

                            if (!conversationState.equals(updatedConversationState)) {
                                npcChatStatesCache.put(playerUUID, updatedConversationState);
                            }

                            NPC npc = playerNPCMap.get(playerUUID);
                            String npcName = npc.getName();
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    if (plugin.getActiveConversations().containsKey(playerUUID)) {
                                        conversationUtils.sendNPCMessage(player, npcName, response);
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