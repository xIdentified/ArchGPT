package me.xidentified.archgpt.listeners;

import me.xidentified.archgpt.*;
import me.xidentified.archgpt.utils.ArchGPTConstants;
import me.xidentified.archgpt.utils.Messages;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class NPCEventListener implements Listener {
    private final ArchGPT plugin;
    private final NPCConversationManager conversationManager;
    private final ArchGPTConfig configHandler;
    private final Map<UUID, Long> lastChatTimestamps = new HashMap<>();

    // Maps each player UUID to a map of NPC UUIDs and their respective last greeting times
    private final Map<UUID, Map<UUID, Long>> playerNPCGreetingCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Boolean>> playerNPCGreetedState = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerLastGreetedTime = new ConcurrentHashMap<>();

    public NPCEventListener(ArchGPT plugin, NPCConversationManager conversationManager, ArchGPTConfig configHandler) {
        this.plugin = plugin;
        this.conversationManager = conversationManager;
        this.configHandler = configHandler;
    }

    @EventHandler
    public void onPlayerApproachNPC(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        Location to = event.getTo();
        double radius = 5.0; // Example radius

        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!CitizensAPI.getNPCRegistry().isNPC(entity)) continue;
            NPC npc = CitizensAPI.getNPCRegistry().getNPC(entity);
            UUID npcUUID = npc.getUniqueId();

            // Check if NPC is configured in config
            if (!isNPCConfigured(npc)) {
                continue;
            }

            // Retrieve or initialize the cooldown and greeted state maps for this player
            Map<UUID, Long> npcCooldowns = playerNPCGreetingCooldown.computeIfAbsent(playerUUID, k -> new ConcurrentHashMap<>());
            Map<UUID, Boolean> npcGreetedStates = playerNPCGreetedState.computeIfAbsent(playerUUID, k -> new ConcurrentHashMap<>());

            // Check if the player has already been greeted by this specific NPC
            if (npcGreetedStates.getOrDefault(npcUUID, false)) {
                continue; // Player has already been greeted by this NPC, no further action required
            }

            // Check if the player is on cooldown to be greeted by this specific NPC
            if (npcCooldowns.containsKey(npcUUID) && currentTime - npcCooldowns.get(npcUUID) < ArchGPTConstants.GREETING_COOLDOWN_MS) {
                continue; // Player is on cooldown for this NPC, no greeting necessary
            }

            // Ensure the NPC is spawned
            if (npc.isSpawned()) {
                // Check if the player was recently greeted by any NPC to prevent rapid re-greetings
                if (wasPlayerRecentlyGreeted(playerUUID)) {
                    continue; // Player was recently greeted, skip further greetings for now
                }

                // Optional: Check line of sight if necessary
                if (conversationManager.getConversationUtils().isInLineOfSight(npc, player)) {
                    // Greet the player and update the greeted state and cooldown for this NPC
                    greetPlayer(player, npc);
                    npcGreetedStates.put(npcUUID, true); // Mark the player as greeted by this NPC
                    npcCooldowns.put(npcUUID, currentTime); // Update cooldown for this NPC
                }
            }
        }
    }

    private boolean isNPCConfigured(NPC npc) {
        String npcConfigPath = "npcs." + npc.getName();
        return plugin.getConfig().contains(npcConfigPath);
    }

    private void greetPlayer(Player player, NPC npc) {
        conversationManager.getGreeting(player, npc).thenAccept(greeting -> {
            if (greeting != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    conversationManager.getConversationUtils().sendNPCMessage(player, npc, greeting);
                    // If the player is new, instruct them on how to start conversation
                    if (!player.hasPlayedBefore()) {
                        plugin.getHologramManager().showInteractionHologram(npc, player);
                    }
                });
            }
        });
        playerLastGreetedTime.put(player.getUniqueId(), System.currentTimeMillis());
    }

    private boolean wasPlayerRecentlyGreeted(UUID playerUUID) {
        Long lastGreetedTime = playerLastGreetedTime.get(playerUUID);
        long currentTime = System.currentTimeMillis();
        return lastGreetedTime != null && (currentTime - lastGreetedTime) < ArchGPTConstants.RECENT_GREETING_COOLDOWN_MS;
    }

    @EventHandler
    public void onNPCRightClick(NPCRightClickEvent event) {
        synchronized (conversationManager.npcChatStatesCache) {
            Player player = event.getClicker();
            NPC npc = event.getNPC();

            // Check if the NPC is configured in config.yml
            String npcPrompt = configHandler.getNpcPrompt(npc.getName(), player);
            if (npcPrompt == null || npcPrompt.isEmpty()) {
                // NPC is not configured, exit method
                plugin.debugLog("NPC '" + npc.getName() + "' is not configured in config.yml.");
                return;
            }

            // Check if the player is already in an ongoing conversation
            if (conversationManager.playerInConversation(player.getUniqueId())) {
                plugin.debugLog("Player '" + player.getName() + "' is already in a conversation.");
                return;
            }

            plugin.debugLog("NPCRightClickEvent triggered for player '" + player.getName() + "' on NPC '" + npc.getName() + "'.");

            // Start a new conversation if not already in one
            conversationManager.startConversation(player, npc);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        synchronized (conversationManager.npcChatStatesCache) {
            Player player = event.getPlayer();
            UUID playerUUID = player.getUniqueId();
            String message = event.getMessage();

            Component playerMessageComponent = Component.text(message);
            HologramManager hologramManager = new HologramManager(plugin);
            long now = System.currentTimeMillis();
            long lastChatTimestamp = lastChatTimestamps.getOrDefault(playerUUID, 0L);

            if (now - lastChatTimestamp < ArchGPTConstants.CHAT_COOLDOWN_MS) {
                // If the player is trying to chat during the cooldown period
                plugin.sendMessage(player, Messages.GENERAL_CHAT_COOLDOWN);
                event.setCancelled(true);
                return;
            }

            // Check if player is already in conversation
            if (!conversationManager.playerInConversation(playerUUID)) {
                return;
            }

            event.setCancelled(true); // Prevent chat messages from going out to everyone

            // Handle player reporting state
            if (conversationManager.getConversationUtils().handleReportingState(player, event)) {
                return;
            }

            // If the player types 'cancel', end the conversation
            if (conversationManager.handleCancelCommand(player, PlainTextComponentSerializer.plainText().serialize(playerMessageComponent))) {
                event.setCancelled(true);
                return;
            }

            // Process the player's message
            conversationManager.processPlayerMessage(player, playerMessageComponent, hologramManager);
            lastChatTimestamps.put(playerUUID, now);
        }
    }


    @EventHandler
    public void onPlayerLeavesConversation(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        Location to = event.getTo();
        AtomicBoolean conversationEnded = new AtomicBoolean(false);
        long currentTime = System.currentTimeMillis();

        // Retrieve the player's cooldown map and greeted state map for NPCs
        Map<UUID, Long> npcCooldowns = playerNPCGreetingCooldown.get(playerUUID);
        Map<UUID, Boolean> npcGreetedStates = playerNPCGreetedState.get(playerUUID);

        if (npcCooldowns != null && npcGreetedStates != null) {
            npcCooldowns.keySet().forEach(npcUUID -> {
                NPC npc = CitizensAPI.getNPCRegistry().getByUniqueId(npcUUID);
                if (npc != null && npc.isSpawned() && npcGreetedStates.getOrDefault(npcUUID, false)) {
                    double distance = to.distance(npc.getEntity().getLocation());
                    if (distance > ArchGPTConstants.GREETING_RADIUS) {
                        // Player has definitively moved away from this NPC, reset cooldown and greeted state
                        npcCooldowns.put(npcUUID, currentTime);  // start cooldown
                        npcGreetedStates.put(npcUUID, false);
                    }
                }
            });
        }

        // Perform actions based on conditions checked inside the lambda
        if (conversationEnded.get()) {
            conversationManager.endConversation(playerUUID);
            plugin.sendMessage(player, Messages.CONVERSATION_ENDED_WALKED_AWAY);
        } else {
            // Handle world change for the NPC player was having a conversation with, if any
            NPC npc = conversationManager.playerNPCMap.get(playerUUID);
            if (npc != null && player.getWorld() != npc.getEntity().getWorld()) {
                conversationManager.endConversation(playerUUID);
                plugin.sendMessage(player, Messages.CONVERSATION_ENDED_CHANGED_WORLDS);
            }
        }
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event) {
        UUID playerUUID = event.getPlayer().getUniqueId();
        playerNPCGreetingCooldown.remove(playerUUID);
        plugin.playerSemaphores.remove(playerUUID);
    }

}