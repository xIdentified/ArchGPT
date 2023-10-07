package me.xidentified.archgpt;

import me.xidentified.archgpt.reports.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.*;
import java.util.concurrent.*;
import com.google.common.util.concurrent.RateLimiter;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.util.logging.Level;

public class ArchGPT extends JavaPlugin {

    // Cache-related variables
    private final Cache<UUID, List<String>> npcChatStatesCache = CacheBuilder.newBuilder()
            .expireAfterWrite(ArchGPTConstants.CACHE_EXPIRATION_MINUTES, TimeUnit.MINUTES)
            .build();

    // Configuration and utilities
    private ArchGPTConfig configHandler;
    private RateLimiter apiRateLimiter;

    // State management
    private final Map<UUID, Boolean> activeConversations = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerCooldowns = new HashMap<>();
    private final Map<UUID, AtomicInteger> conversationTokenCounters = new HashMap<>();

    // Managers
    private HologramManager hologramManager;
    private ReportManager reportManager;

    @Override
    public void onEnable() {
        try {
            // Initialize the API rate limiter with the desired rate (e.g., 5 API calls per second)
            apiRateLimiter = RateLimiter.create(getConfig().getDouble("max_api_calls_per_second"));

            this.configHandler = new ArchGPTConfig(this);
            this.hologramManager = new HologramManager(this);
            this.reportManager = new ReportManager(this);

            // Register the event listeners
            NPCConversationManager manager = new NPCConversationManager(this, new ArchGPTConfig(this));
            getServer().getPluginManager().registerEvents(new NPCEventListener(this, manager, configHandler), this);
            getServer().getPluginManager().registerEvents(new ReportGUIListener(this), this);

            // Register commands
            this.getCommand("npcreports").setExecutor(new AdminReportCommandExecutor(this));
            getCommand("selectreporttype").setExecutor(new ReportTypeCommandExecutor(this));
            this.getCommand("reportnpcmessage").setExecutor(new ReportTypeCommandExecutor(this));

            // Set the logger level based on debugMode
            Level loggerLevel = configHandler.isDebugMode() ? Level.INFO : Level.WARNING;
            getLogger().setLevel(loggerLevel);

            // Notify the user that the plugin is enabled
            configHandler.printConfigToConsole();

        } catch (Exception e) {
            getLogger().severe("An error occurred while enabling the plugin. Please check the configuration file.");
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    public ArchGPTConfig getConfigHandler() {
        return configHandler;
    }

    public Cache<UUID, List<String>> getNpcChatStatesCache() {
        return npcChatStatesCache;
    }

    public Map<UUID, Boolean> getActiveConversations() {
        return activeConversations;
    }

    public Map<UUID, Long> getPlayerCooldowns() {
        return playerCooldowns;
    }

    public Map<UUID, AtomicInteger> getConversationTokenCounters() {
        return conversationTokenCounters;
    }

    public RateLimiter getApiRateLimiter() {
        return apiRateLimiter;
    }

    public HologramManager getHologramManager() {
        return hologramManager;
    }

    public ReportManager getReportManager() { return this.reportManager; }

    @Override
    public void onDisable() {
        // Remove all holograms (armor stands) created by the plugin
        if (this.hologramManager != null) {
            this.hologramManager.removeAllHolograms();
        }
    }

    public void debugLog(String message) {
        if (configHandler.isDebugMode()) {
            getLogger().info("[DEBUG] " + message);
        }
    }

}
