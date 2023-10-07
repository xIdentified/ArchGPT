package me.xidentified.archgpt;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.*;

public class ConversationTimeoutManager {
    private final ArchGPT plugin;
    private final ConcurrentHashMap<UUID, ScheduledFuture<?>> conversationTimeoutTasks;
    private final ScheduledExecutorService executorService;

    public ConversationTimeoutManager(ArchGPT plugin) {
        this.plugin = plugin;
        this.conversationTimeoutTasks = new ConcurrentHashMap<>();
        this.executorService = Executors.newSingleThreadScheduledExecutor();
    }

    public void startConversationTimeout(UUID playerUUID) {

        plugin.debugLog("Conversation timeout started for " + playerUUID);

        // Get the timeout duration from the configuration
        long conversationTimeoutMillis = plugin.getConfigHandler().getNpcChatTimeoutMillis();

        // Schedule the timeout task
        ScheduledFuture<?> timeoutTask = executorService.schedule(() -> {
            synchronized (plugin.getNpcChatStatesCache()) {
                Player player = plugin.getServer().getPlayer(playerUUID);
                if (player != null) {
                    player.sendMessage(Component.text("Conversation ended due to inactivity.", NamedTextColor.YELLOW));

                    // Clean up the conversation caches
                    plugin.getNpcChatStatesCache().invalidate(playerUUID);
                    plugin.getActiveConversations().remove(playerUUID);
                    plugin.getPlayerCooldowns().remove(playerUUID);
                    plugin.getConversationTokenCounters().remove(playerUUID);

                    plugin.getHologramManager().removePlayerHologram(playerUUID);
                    plugin.getHologramManager().removeHologram();
                }
            }
        }, conversationTimeoutMillis, TimeUnit.MILLISECONDS);

        // Store the timeout task in the map
        conversationTimeoutTasks.put(playerUUID, timeoutTask);
    }

    public void resetConversationTimeout(UUID playerUUID) {
        plugin.debugLog("Conversation Timer reset for " + playerUUID);
        cancelConversationTimeout(playerUUID);
        startConversationTimeout(playerUUID);
    }

    public void cancelConversationTimeout(UUID playerUUID) {
        plugin.debugLog("Player " + playerUUID + " canceled the conversation");

        // Get the existing conversation timeout task for the player
        synchronized (plugin.getNpcChatStatesCache()) {
            ScheduledFuture<?> timeoutTask = conversationTimeoutTasks.remove(playerUUID);

            // Cancel the task if it exists and is not yet executed
            if (timeoutTask != null && !timeoutTask.isDone()) {
                timeoutTask.cancel(true);
            }
        }
    }

}
