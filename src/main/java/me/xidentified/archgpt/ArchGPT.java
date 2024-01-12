package me.xidentified.archgpt;

import de.cubbossa.translations.Message;
import de.cubbossa.translations.StyleSet;
import de.cubbossa.translations.Translations;
import de.cubbossa.translations.TranslationsFramework;
import de.cubbossa.translations.persistent.YamlMessageStorage;
import de.cubbossa.translations.persistent.YamlStyleStorage;
import lombok.Getter;
import me.xidentified.archgpt.commands.AdminReportCommandExecutor;
import me.xidentified.archgpt.commands.ArchGPTCommand;
import me.xidentified.archgpt.commands.ReportTypeCommandExecutor;
import me.xidentified.archgpt.listeners.NPCEventListener;
import me.xidentified.archgpt.reports.*;
import me.xidentified.archgpt.storage.dao.ConversationDAO;
import me.xidentified.archgpt.storage.dao.MySQLConversationDAO;
import me.xidentified.archgpt.storage.dao.SQLiteConversationDAO;
import me.xidentified.archgpt.utils.Messages;
import me.xidentified.archgpt.utils.Metrics;
import me.xidentified.archgpt.utils.Placeholders;
import me.xidentified.archgpt.utils.TranslationService;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.ComponentLike;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.*;
import java.util.concurrent.*;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.util.logging.Level;

@Getter
public class ArchGPT extends JavaPlugin {
    // Maps
    private final Cache<UUID, List<String>> npcChatStatesCache = CacheBuilder.newBuilder()
            .expireAfterWrite(ArchGPTConstants.CACHE_EXPIRATION_MINUTES, TimeUnit.MINUTES)
            .build();
    private final Map<UUID, Boolean> activeConversations = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerCooldowns = new ConcurrentHashMap<>();
    public final ConcurrentHashMap<UUID, Semaphore> playerSemaphores = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicInteger> conversationTokenCounters = new ConcurrentHashMap<>();
    private final CloseableHttpClient httpClient = HttpClients.custom()
            .disableCookieManagement()
            .build();

    // Managers
    private ArchGPTConfig configHandler;
    private HologramManager hologramManager;
    private ReportManager reportManager;
    private TranslationService translationService;
    private NPCConversationManager manager;
    private ConversationDAO conversationDAO;
    BukkitAudiences audiences;
    Translations translations;

    @Override
    public void onEnable() {
        try {
            // Check if Citizens is installed and enabled
            if (Bukkit.getPluginManager().getPlugin("Citizens") == null || !Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("Citizens")).isEnabled()) {
                getLogger().severe("Citizens is required for ArchGPT to work. Please install Citizens and restart the server.");
                Bukkit.getPluginManager().disablePlugin(this);
                return;
            }

            this.configHandler = new ArchGPTConfig(this);
            this.hologramManager = new HologramManager(this);
            this.reportManager = new ReportManager(this);

            audiences = BukkitAudiences.create(this);
            TranslationsFramework.enable(new File(getDataFolder(), "/../"));
            translations = TranslationsFramework.application("ArchGPT");
            translations.setMessageStorage(new YamlMessageStorage(new File(getDataFolder(), "/lang/")));
            translations.setStyleStorage(new YamlStyleStorage(new File(getDataFolder(), "/lang/styles.yml")));

            translations.addMessages(TranslationsFramework.messageFieldsFromClass(Messages.class));

            loadLanguages();

            // Set the LocaleProvider
            translations.setLocaleProvider(audience -> {
                // Read settings from config
                boolean usePlayerClientLocale = getConfig().getBoolean("use-player-client-locale", true);
                String fallbackLocaleCode = getConfig().getString("default-locale", "en");
                Locale fallbackLocale = Locale.forLanguageTag(fallbackLocaleCode);

                if (audience == null || !usePlayerClientLocale) {
                    return fallbackLocale;
                }

                return audience.getOrDefault(Identity.LOCALE, fallbackLocale);
            });

            // Initialize TranslationService for API responses
            String libreTranslateAPIEndpoint = getConfig().getString("translation.libre_endpoint");
            this.translationService = new TranslationService(libreTranslateAPIEndpoint, this.getLogger());

            // Register the event listeners
            this.manager = new NPCConversationManager(this, configHandler);
            getServer().getPluginManager().registerEvents(new NPCEventListener(this, manager, configHandler), this);
            getServer().getPluginManager().registerEvents(new ReportGUI(this), this);

            // Register commands
            Objects.requireNonNull(getCommand("npcreports")).setExecutor(new AdminReportCommandExecutor(this));
            Objects.requireNonNull(getCommand("selectreporttype")).setExecutor(new ReportTypeCommandExecutor(this));
            Objects.requireNonNull(getCommand("reportnpcmessage")).setExecutor(new ReportTypeCommandExecutor(this));

            Objects.requireNonNull(getCommand("archgpt")).setExecutor(new ArchGPTCommand(this));
            Objects.requireNonNull(getCommand("archgpt")).setTabCompleter(new ArchGPTCommand(this));

            // Set storage type
            String storageType = getConfig().getString("storage.type", "sqlite");
            switch (storageType.toLowerCase()) {
                case "sqlite":
                    // Create the SQLite file in the plugin's storage directory
                    File sqliteFile = new File(getDataFolder(), "storage/conversations.db");
                    conversationDAO = new SQLiteConversationDAO(sqliteFile);
                    break;
                case "mysql":
                    String host = getConfig().getString("storage.mysql.host");
                    int port = getConfig().getInt("storage.mysql.port");
                    String database = getConfig().getString("storage.mysql.database");
                    String username = getConfig().getString("storage.mysql.username");
                    String password = getConfig().getString("storage.mysql.password");
                    conversationDAO = new MySQLConversationDAO(host, port, database, username, password);
                    break;
            }

            // Set the logger level based on debugMode
            Level loggerLevel = configHandler.isDebugMode() ? Level.INFO : Level.WARNING;
            getLogger().setLevel(loggerLevel);

            // Register PlaceholderAPI expansion
            if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                new Placeholders(this).register();
                debugLog("PlaceholderAPI expansion enabled!");
            }

            // Register bStats
            int pluginId = 20587;
            Metrics metrics = new Metrics(this, pluginId);

            // Notify the user that the plugin is enabled
            configHandler.printConfigToConsole();

        } catch (Exception e) {
            getLogger().severe("An error occurred while enabling the plugin. Please check the configuration file.");
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    public void loadLanguages() {
        this.translations.loadStyles();
        boolean saveStyles = false;
        StyleSet set = this.translations.getStyleSet();
        if (!set.containsKey("negative")) {
            this.translations.getStyleSet().put("negative", "<red>");
            saveStyles = true;
        }

        if (!set.containsKey("positive")) {
            this.translations.getStyleSet().put("positive", "<green>");
            saveStyles = true;
        }

        if (!set.containsKey("warning")) {
            this.translations.getStyleSet().put("warning", "<yellow>");
            saveStyles = true;
        }

        if (saveStyles) {
            this.translations.saveStyles();
        }

        this.translations.saveLocale(Locale.ENGLISH);

        this.saveResource("lang/de.yml", false);
        this.translations.loadLocales();
    }

    public void sendMessage(CommandSender sender, ComponentLike componentLike) {
        Audience audience = audiences.sender(sender);
        if (componentLike instanceof Message msg) {
            // Translate the message into the locale of the command sender
            componentLike = msg.formatted(audience);
        }
        audience.sendMessage(componentLike);
    }

    @Override
    public void onDisable() {
        // Close database connection
        if (this.reportManager != null) {
            this.reportManager.closeResources();
        }

        // Remove all holograms
        if (hologramManager != null) {
            hologramManager.getAllHolograms().forEach(ArmorStand::remove);
            hologramManager.getPlayerHolograms().values().forEach(ArmorStand::remove);
            hologramManager.getAllHolograms().clear();
            hologramManager.getPlayerHolograms().clear();
        }

        // Close translations framework
        audiences.close();
        translations.close();
        TranslationsFramework.disable();

        // Unregister events
        HandlerList.unregisterAll();

        try {
            httpClient.close();
        } catch (IOException e) {
            getLogger().warning("Error closing HttpClient: " + e.getMessage());
        }

        playerSemaphores.clear();
        conversationTokenCounters.clear();
        playerCooldowns.clear();
    }

    public void debugLog(String message) {
        if (configHandler.isDebugMode()) {
            getLogger().info("[DEBUG] " + message);
        }
    }

}
