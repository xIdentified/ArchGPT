package me.xidentified.archgpt;

import lombok.Getter;
import me.xidentified.archgpt.reports.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.*;
import java.util.concurrent.*;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.util.logging.Level;

@Getter
public class ArchGPT extends JavaPlugin {

    // Cache-related variables
    private final Cache<UUID, List<String>> npcChatStatesCache = CacheBuilder.newBuilder()
            .expireAfterWrite(ArchGPTConstants.CACHE_EXPIRATION_MINUTES, TimeUnit.MINUTES)
            .build();

    // Configuration and utilities
    private ArchGPTConfig configHandler;

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
