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

// Anything in this class applies to NPCs regardless of server type
public class NPCEventListener implements Listener {
    private final ArchGPT plugin;
    private final NPCConversationManager conversationManager;
    private final ArchGPTConfig configHandler;
    private final Set<UUID> npcsProcessingGreeting = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastChatTimestamps = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastPlayerMessages = new ConcurrentHashMap<>();

    public NPCEventListener(ArchGPT plugin, NPCConversationManager conversationManager, ArchGPTConfig configHandler) {
        this.plugin = plugin;
        this.conversationManager = conversationManager;
        this.configHandler = configHandler;
    }

    // Event listener for right-clicking an NPC
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
            lastPlayerMessages.put(player.getUniqueId(), message);

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

    //Listener for player movement for NPC greetings, and to end conversation if player walks away
    @EventHandler
    public void onPlayerGreeting(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Check if nearby NPCs want to greet the player
        double radius = 4.0;
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!CitizensAPI.getNPCRegistry().isNPC(entity)) {
                continue;
            }

            NPC npc = CitizensAPI.getNPCRegistry().getNPC(entity);

            if (npc.isSpawned() && conversationManager.getConversationUtils().isInLineOfSight(npc, player) && conversationManager.getConversationUtils().canComment(npc)) {
                if (!npcsProcessingGreeting.add(npc.getUniqueId())) {
                    // This NPC is already processing a greeting, skip to the next NPC
                    continue;
                }
                // Update the cooldown for the NPC
                conversationManager.npcCommentCooldown.put(npc.getUniqueId(), System.currentTimeMillis());

                // Fetch the prompt from config
                String prompt = plugin.getConfig().getString("npcs." + npc.getName());
                if (prompt != null && !prompt.isEmpty()) {
                    // Get the greeting for the NPC asynchronously
                    conversationManager.getGreeting(player, npc).thenAccept(greeting -> {
                        if (greeting != null) {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                // Utilize the sendNPCMessage method to send the greeting
                                conversationManager.getConversationUtils().sendNPCMessage(player, npc, greeting);

                                // For new players, a hologram appears prompting them to right-click the NPC to interact
                                if (!player.hasPlayedBefore()) {
                                    plugin.getHologramManager().showInteractionHologram(npc, player);
                                }

                                // Mark NPC as processing greeting so this doesn't trigger more than once
                                npcsProcessingGreeting.remove(npc.getUniqueId());
                            });
                        }
                    });
                }
            }
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
                }
            }
        }
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event) {
        UUID playerUUID = event.getPlayer().getUniqueId();
        npcsProcessingGreeting.remove(playerUUID);
        plugin.playerSemaphores.remove(playerUUID);
    }

    public String getLastMessage(UUID playerUUID) {
        return lastPlayerMessages.getOrDefault(playerUUID, "");
    }

}