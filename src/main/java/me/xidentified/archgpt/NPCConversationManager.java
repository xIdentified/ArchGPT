package me.xidentified.archgpt;

import lombok.Getter;
import me.xidentified.archgpt.context.MemoryContext;
import me.xidentified.archgpt.storage.model.Conversation;
import me.xidentified.archgpt.utils.*;
import net.citizensnpcs.api.npc.NPC;
import net.kyori.adventure.text.Component;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class NPCConversationManager {

    private final ArchGPT plugin;
    private final MemoryContext memoryContext;
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
        this.memoryContext = new MemoryContext(plugin);
        this.chatRequestHandler = new ChatRequestHandler(plugin);
        this.npcChatStatesCache = new ConcurrentHashMap<>();
        this.playerCooldowns = new ConcurrentHashMap<>();
        this.conversationTimeoutManager = new ConversationTimeoutManager(plugin);
        this.conversationUtils = new ConversationUtils(plugin, configHandler, this);
    }

    public JsonObject createSystemMessage(NPC npc, Player player) {
        String npcCharacter = npc.getName();
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");

        // Use conversationUtils instance field
        String combinedContext = this.conversationUtils.getCombinedContext(npcCharacter, player);

        // Construct the system message content
        String systemMessageContent = String.format(
                "You are %s, a resident of the vibrant world of Minecraft. " +
                        "You interact with players in a casual manner, making them feel part of an ongoing adventure. " +
                        "Engage them with stories, observations, and comments that reflect your unique personality and knowledge. " +
                        "Offer help or guidance only when they seek it, making your interactions feel natural and integral to their experience. " +
                        "You're not just an NPC, you're a living, breathing part of this world with your own thoughts and feelings. " +
                        "Here's whats going on: %s", npcCharacter, combinedContext);

        systemMessage.addProperty("content", systemMessageContent);
        return systemMessage;
    }

    public CompletableFuture<String> getGreeting(Player player, NPC npc) {
        // Use the new MCP approach instead of building the request manually
        String greetingPrompt = "A player known as " + player.getName() + " approaches you. " +
                "Give them a greeting consisting of 40 completion_tokens or less.";
        
        return getChatRequestHandler().processMCPRequest(
            player, npc, greetingPrompt, 
            ChatRequestHandler.RequestType.GREETING, 
            null
        ).thenApply(responseObject -> (String) responseObject);
    }

    public void processPlayerMessage(Player player, Component playerMessage, HologramManager hologramManager) {
        // This method should only be called from the main thread
        if (!Bukkit.isPrimaryThread()) {
            plugin.getLogger().warning("processPlayerMessage called from async thread! Scheduling on main thread.");
            Bukkit.getScheduler().runTask(plugin, () -> processPlayerMessage(player, playerMessage, hologramManager));
            return;
        }
        
        UUID playerUUID = player.getUniqueId();
        NPC npc = playerNPCMap.get(playerUUID);

        // Check if player message is too short
        if (PlainTextComponentSerializer.plainText().serialize(playerMessage).length() < configHandler.getMinCharLength()) {
            plugin.sendMessage(player, Messages.MSG_TOO_SHORT.insertNumber("size", configHandler.getMinCharLength()));
            return;
        }

        // Send player message
        conversationUtils.sendPlayerMessage(player, playerMessage);

        // Start animation over NPC head while it processes response
        displayHologramOverNPC(playerUUID, npc, hologramManager);

        // Cooldown logic
        long currentTimeMillis = System.currentTimeMillis();
        if (playerCooldowns.containsKey(playerUUID)) {
            long lastTriggerTimeMillis = playerCooldowns.get(playerUUID);
            long cooldownMillis = configHandler.getChatCooldownMillis();
            if (currentTimeMillis - lastTriggerTimeMillis < cooldownMillis) {
                return;
            }
        }
        playerCooldowns.put(playerUUID, currentTimeMillis);

        // Process chat request
        List<JsonObject> conversationState = npcChatStatesCache.get(playerUUID);
        String playerMessageText = PlainTextComponentSerializer.plainText().serialize(playerMessage);

        // Handle summary of past conversations if needed
        String conversationSummary = memoryContext.getConversationSummary(playerMessage, playerUUID, npc.getName());
        if (conversationSummary != null) {
            // Update context with conversation summary
            plugin.getContextManager().updateContextElement(player, "conversation_summary", conversationSummary);
        }

        // Send the request and process the response using the new MCP approach
        CompletableFuture<Object> future = getChatRequestHandler().processMCPRequest(
            player, npc, playerMessageText, 
            ChatRequestHandler.RequestType.CONVERSATION, 
            conversationState
        );
        
        processNpcResponse(future, player, npc, hologramManager);
    }

    public void startConversation(Player player, NPC npc) {
        UUID playerUUID = player.getUniqueId();

        // Store conversation state
        playerNPCMap.put(playerUUID, npc);
        List<JsonObject> initialConversationState = new ArrayList<>();

        // Add the system message with NPC's context
        JsonObject systemMessageJson = createSystemMessage(npc, player);
        initialConversationState.add(systemMessageJson);

        // Store the initial conversation state
        npcChatStatesCache.put(playerUUID, initialConversationState);
        plugin.getActiveConversations().put(playerUUID, true);

        plugin.sendMessage(player, Messages.CONVERSATION_STARTED
                .insertObject("npc", npc)
                .insertString("cancel", Objects.requireNonNull(plugin.getConfig().getString("conversation_end_phrase"))));

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

    private void displayHologramOverNPC(UUID playerUUID, NPC npc, HologramManager hologramManager) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (npc == null || !npc.isSpawned()) return;
                hologramManager.removePlayerHologram(playerUUID);
                Location npcLocation = npc.getEntity().getLocation();
                hologramManager.createHologram(playerUUID, npcLocation.add(0, 1, 0), "...");
                hologramManager.animateHologram();
            }
        }.runTask(plugin);
    }

    private void processNpcResponse(CompletableFuture<Object> future, Player player, NPC npc, HologramManager hologramManager) {
        UUID playerUUID = player.getUniqueId();
        future.thenAccept(responseObject -> {
            synchronized (npcChatStatesCache) {
                if (!plugin.getActiveConversations().containsKey(playerUUID)) return;
                if (responseObject instanceof Pair<?, ?> rawPair) {

                    Object leftObject = rawPair.getLeft();
                    Object rightObject = rawPair.getRight();

                    if (leftObject instanceof String response && rightObject instanceof List<?>) {
                        // Check if the list contains JsonObjects
                        if (((List<?>) rawPair.getRight()).stream().allMatch(item -> item instanceof JsonObject)) {
                            @SuppressWarnings("unchecked") // Safe after checking all elements
                            List<JsonObject> updatedConversationState = (List<JsonObject>) rawPair.getRight();

                            npcChatStatesCache.put(playerUUID, updatedConversationState);

                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    if (plugin.getActiveConversations().containsKey(playerUUID)) {
                                        conversationUtils.sendNPCMessage(player, npc, response);

                                        // Save the message if the response is a significant length
                                        List<String> relevantSentences = conversationUtils.filterShortSentences(response, ArchGPTConstants.MINIMUM_SAVED_SENTENCE_LENGTH);

                                        if (!relevantSentences.isEmpty()) {
                                            String filteredResponseText = String.join(" ", relevantSentences);
                                            Conversation conversation = new Conversation(
                                                    player.getUniqueId(),
                                                    npc.getName(),
                                                    filteredResponseText,
                                                    System.currentTimeMillis(),
                                                    true
                                            );
                                            plugin.getConversationDAO().saveConversation(conversation);
                                        }

                                        hologramManager.removePlayerHologram(playerUUID);
                                    }
                                }
                            }.runTaskLater(plugin, 20L);
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
