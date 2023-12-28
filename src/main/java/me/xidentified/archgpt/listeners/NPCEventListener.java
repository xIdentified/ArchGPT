package me.xidentified.archgpt.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.xidentified.archgpt.*;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.*;

// Anything in this class applies to NPCs regardless of server type
public class NPCEventListener implements Listener {

    private final ArchGPT plugin;
    private final NPCConversationManager conversationManager;
    private final ArchGPTConfig configHandler;

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

            if (!configHandler.isNPCConfigured((npc.getName()))) {
                // NPC is not in the config, exit method
                return;
            }

            // Check if the player is already in an ongoing conversation
            if (conversationManager.isInActiveConversation(player.getUniqueId())) {
                // Player is already in a conversation, exit method
                return;
            }

            plugin.debugLog("NPCRightClickEvent triggered for player '" + player.getName() + "' on NPC '" + npc.getName() + "'.");

            // Start a new conversation if not already in one
            conversationManager.startConversation(player, npc);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncChatEvent event) {
        synchronized (conversationManager.npcChatStatesCache) {
            Player player = event.getPlayer();
            UUID playerUUID = player.getUniqueId();
            Component playerMessageComponent = event.originalMessage();
            HologramManager hologramManager = new HologramManager(plugin);

            plugin.debugLog("onPlayerChat event triggered for player '" + player.getName() + "'. Message: " + PlainTextComponentSerializer.plainText().serialize(playerMessageComponent));

            // Check if player is already in conversation
            if (!conversationManager.isInActiveConversation(playerUUID)) {
                return;
            }

            event.setCancelled(true); // Prevent chat messages from going out to everyone

            // Handle player reporting state
            if (conversationManager.handleReportingState(player, playerUUID, event)) {
                return;
            }

            // If the player types 'cancel', end the conversation
            if (conversationManager.handleCancelCommand(player, playerUUID, PlainTextComponentSerializer.plainText().serialize(playerMessageComponent))) {
                event.setCancelled(true);
                return;
            }

            // Process the player's message
            conversationManager.processPlayerMessage(player, playerUUID, playerMessageComponent, hologramManager);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void deprecatedOnPlayerChat(AsyncPlayerChatEvent event) {
        synchronized (conversationManager.npcChatStatesCache) {
            Player player = event.getPlayer();
            UUID playerUUID = player.getUniqueId();

            //Check if player is already in conversation
            if (conversationManager.isInActiveConversation(playerUUID)) {
                event.setCancelled(true);
            }
        }
    }

    //Listener for player movement for NPC greetings, and to end conversation if player walks away
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        if (this.plugin.getActiveConversations().containsKey(playerUUID)) {
            NPC npc = conversationManager.playerNPCMap.get(playerUUID);

            if (npc != null && npc.isSpawned()) {
                Entity npcEntity = npc.getEntity();

                // Check if player changed worlds
                if (!npcEntity.getWorld().equals(player.getWorld())) {
                    // End conversation if player is in a different world
                    conversationManager.endConversation(playerUUID);
                    player.sendMessage(Component.text("Conversation ended because you changed worlds.", NamedTextColor.YELLOW));
                    return; // Skip further processing since conversation has ended
                }

                // Check if player strayed too far
                if (npcEntity.getWorld().equals(player.getWorld())) {
                    double distance = player.getLocation().distance(npcEntity.getLocation());
                    if (distance > ArchGPTConstants.MAX_DISTANCE_FROM_NPC) {
                        conversationManager.endConversation(playerUUID);
                        player.sendMessage(Component.text("Conversation ended because you walked away.", NamedTextColor.YELLOW));
                    }
                }
            }
        }

        // If not in conversation, check if any NPC wants to greet the player
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (npc.isSpawned() && conversationManager.isInLineOfSight(npc, player) && conversationManager.canComment(npc)) {
                String prompt = plugin.getConfig().getString("npcs." + npc.getName());
                if (prompt != null && !prompt.isEmpty()) {
                    // Modify the prompt to specify a greeting context
                    Component promptComponent = Component.text(prompt)
                            .append(Component.newline())
                            .append(Component.text("A player approaches you. How do you greet them?"));

                    // Get the greeting for the NPC asynchronously
                    conversationManager.getGreeting(promptComponent).thenAccept(greeting -> {
                        // Utilize the sendNPCMessage method to send the greeting
                        conversationManager.sendNPCMessage(player, playerUUID, npc.getName(), greeting);

                        // Update the cooldown for the NPC
                        conversationManager.npcCommentCooldown.put(npc.getUniqueId(), System.currentTimeMillis());
                    });
                }
            }
        }
    }

}