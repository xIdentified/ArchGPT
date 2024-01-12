package me.xidentified.archgpt.listeners;

import me.xidentified.archgpt.*;
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
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// Anything in this class applies to NPCs regardless of server type
public class NPCEventListener implements Listener {
    private final ArchGPT plugin;
    private final NPCConversationManager conversationManager;
    private final ArchGPTConfig configHandler;
    private final Set<UUID> npcsProcessingGreeting = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastChatTimestamps = new ConcurrentHashMap<>();
    private final Map<UUID, Location> lastSignificantLocations = new ConcurrentHashMap<>();

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
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        Location from = event.getFrom();
        Location to = event.getTo();

        // Check if the player has moved a significant distance (e.g., 3 blocks)
        Location lastLocation = lastSignificantLocations.getOrDefault(playerUUID, from);
        if (!from.getWorld().equals(to.getWorld()) || lastLocation.distanceSquared(to) < 9) {
            return;
        }

        lastSignificantLocations.put(playerUUID, to.clone());

        // Handle stuff if player is in conversation - walking away, change worlds, etc
        if (this.plugin.getActiveConversations().containsKey(playerUUID)) {
            NPC npc = conversationManager.playerNPCMap.get(playerUUID);

            if (npc != null && npc.isSpawned()) {
                Entity npcEntity = npc.getEntity();

                // Check if player changed worlds
                if (!npcEntity.getWorld().equals(player.getWorld())) {
                    // End conversation if player is in a different world
                    conversationManager.endConversation(playerUUID);
                    plugin.sendMessage(player, Messages.CONVERSATION_ENDED_CHANGED_WORLDS);
                    return; // Skip further processing since conversation has ended
                }

                // Check if player strayed too far
                if (npcEntity.getWorld().equals(player.getWorld())) {
                    double distance = player.getLocation().distance(npcEntity.getLocation());
                    if (distance > ArchGPTConstants.MAX_DISTANCE_FROM_NPC) {
                        conversationManager.endConversation(playerUUID);
                        plugin.sendMessage(player, Messages.CONVERSATION_ENDED_WALKED_AWAY);
                    }
                }
            }
            return;
        }

        // If not in conversation, check if nearby NPCs want to greet the player
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
                                conversationManager.getConversationUtils().sendNPCMessage(player, npc.getName(), greeting);

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
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        lastSignificantLocations.put(player.getUniqueId(), player.getLocation());
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event) {
        UUID playerUUID = event.getPlayer().getUniqueId();
        npcsProcessingGreeting.remove(playerUUID);
        plugin.playerSemaphores.remove(playerUUID);
    }

}