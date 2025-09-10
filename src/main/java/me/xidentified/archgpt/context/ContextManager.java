package me.xidentified.archgpt.context;

import com.google.gson.JsonObject;
import me.xidentified.archgpt.ArchGPT;
import me.xidentified.archgpt.ChatRequestHandler;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ContextManager {
    private final ArchGPT plugin;
    private final Map<UUID, JsonObject> playerContextCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastContextUpdate = new ConcurrentHashMap<>();
    private static final long CONTEXT_UPDATE_INTERVAL = 30000; // 30 seconds
    
    public ContextManager(ArchGPT plugin) {
        this.plugin = plugin;
    }
    
    public JsonObject getOrganizedContext(Player player, NPC npc, ChatRequestHandler.RequestType requestType) {
        UUID playerUUID = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        
        // Check if we need to update the context (either first time or after interval)
        if (!playerContextCache.containsKey(playerUUID) || 
            currentTime - lastContextUpdate.getOrDefault(playerUUID, 0L) > CONTEXT_UPDATE_INTERVAL) {
            
            JsonObject context = new JsonObject();
            
            // Add environmental context
            EnvironmentalContextProvider envProvider = new EnvironmentalContextProvider(plugin, player);
            String envContext = envProvider.getFormattedContext("");
            context.addProperty("environment", envContext != null ? envContext : "");
            
            // Add player context
            PlayerContextProvider playerProvider = new PlayerContextProvider(player);
            String playerContext = playerProvider.getFormattedContext("");
            context.addProperty("player", playerContext != null ? playerContext : "");
            
            // Add NPC context
            context.addProperty("npc", npc.getName());
            context.addProperty("npc_id", npc.getId());
            
            // Add request type
            context.addProperty("request_type", requestType.name());
            
            // Cache the context
            playerContextCache.put(playerUUID, context);
            lastContextUpdate.put(playerUUID, currentTime);
            
            plugin.debugLog("Context updated for player: " + player.getName());
            return context;
        }
        
        // Return cached context
        plugin.debugLog("Using cached context for player: " + player.getName());
        return playerContextCache.get(playerUUID);
    }
    
    public void updateContextElement(Player player, String key, String value) {
        UUID playerUUID = player.getUniqueId();
        if (playerContextCache.containsKey(playerUUID)) {
            playerContextCache.get(playerUUID).addProperty(key, value);
            lastContextUpdate.put(playerUUID, System.currentTimeMillis());
        }
    }
    
    public void clearPlayerContext(UUID playerUUID) {
        playerContextCache.remove(playerUUID);
        lastContextUpdate.remove(playerUUID);
        plugin.debugLog("Cleared context for player: " + playerUUID);
    }
    
    public void clearAllContexts() {
        int clearedCount = playerContextCache.size();
        playerContextCache.clear();
        lastContextUpdate.clear();
        plugin.debugLog("Cleared all contexts. Removed " + clearedCount + " player contexts.");
    }
    
    // Optional: Add a method to periodically clean up contexts for offline players
    public void cleanupInactiveContexts() {
        long currentTime = System.currentTimeMillis();
        long inactivityThreshold = 3600000; // 1 hour
        
        playerContextCache.keySet().removeIf(playerUUID -> {
            Long lastUpdate = lastContextUpdate.get(playerUUID);
            if (lastUpdate != null && currentTime - lastUpdate > inactivityThreshold) {
                lastContextUpdate.remove(playerUUID);
                plugin.debugLog("Removed inactive context for player: " + playerUUID);
                return true;
            }
            return false;
        });
    }
}