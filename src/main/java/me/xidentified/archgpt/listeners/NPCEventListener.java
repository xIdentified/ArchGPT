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

public class NPCEventListener implements Listener {
    private final ArchGPT plugin;
    private final NPCConversationManager conversationManager;
    private final ArchGPTConfig configHandler;
    private final Map<UUID, Long> lastChatTimestamps = new HashMap<>();

    private final Map<UUID, Long> playerGreetingCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> playerGreetedState = new ConcurrentHashMap<>();

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

        // Check if the player is on cooldown to be greeted (e.g., 5 minutes)
        if (playerGreetingCooldown.containsKey(playerUUID) &&
                currentTime - playerGreetingCooldown.get(playerUUID) < ArchGPTConstants.GREETING_COOLDOWN_MS) {
            return; // Player is on cooldown, no greeting necessary
        }

        Location to = event.getTo();

        // Check for nearby NPCs within a defined radius
        double radius = 5.0; // Example radius
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!CitizensAPI.getNPCRegistry().isNPC(entity)) continue;
            NPC npc = CitizensAPI.getNPCRegistry().getNPC(entity);

            // Ensure the NPC is spawned and the player hasn't been greeted yet
            if (npc.isSpawned() && !playerGreetedState.getOrDefault(playerUUID, false)) {
                // Optional: Check line of sight if necessary
                if (conversationManager.getConversationUtils().isInLineOfSight(npc, player)) {
                    // Greet the player and set the greeted state to true
                    greetPlayer(player, npc);
                    playerGreetedState.put(playerUUID, true);
                    playerGreetingCooldown.put(playerUUID, currentTime);
                }
            }
        }
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
        NPC npc = conversationManager.playerNPCMap.get(playerUUID);
        Location to = event.getTo();

        // Check if the player is in an active conversation
        if (!this.plugin.getActiveConversations().containsKey(playerUUID)) {
            // Also check if the player was previously greeted by an NPC and has moved away
            if (playerGreetedState.getOrDefault(playerUUID, false)) {
                // Assuming ArchGPTConstants.GREETING_RADIUS is the radius within which greetings occur
                if (npc != null && npc.isSpawned() && to.distance(npc.getEntity().getLocation()) > ArchGPTConstants.GREETING_RADIUS) {
                    playerGreetedState.put(playerUUID, false); // Reset the greeted state
                }
            }
            return;
        }

        // Handle world change
        if (player.getWorld() != npc.getEntity().getWorld()) {
            conversationManager.endConversation(playerUUID);
            plugin.sendMessage(player, Messages.CONVERSATION_ENDED_CHANGED_WORLDS);
        } else {
            // Check if player strayed too far from the NPC
            if (npc.isSpawned()) {
                double distance = to.distance(npc.getEntity().getLocation());
                if (distance > ArchGPTConstants.MAX_DISTANCE_FROM_NPC) {
                    conversationManager.endConversation(playerUUID);
                    plugin.sendMessage(player, Messages.CONVERSATION_ENDED_WALKED_AWAY);
                    // Reset the greeted state as the player has walked away from the NPC
                    playerGreetedState.put(playerUUID, false);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event) {
        UUID playerUUID = event.getPlayer().getUniqueId();
        plugin.playerSemaphores.remove(playerUUID);
    }

}