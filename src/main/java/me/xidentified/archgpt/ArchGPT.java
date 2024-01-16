package me.xidentified.archgpt;

import de.cubbossa.tinytranslations.TinyTranslations;
import de.cubbossa.tinytranslations.TinyTranslationsBukkit;
import de.cubbossa.tinytranslations.Translator;
import de.cubbossa.tinytranslations.persistent.YamlMessageStorage;
import de.cubbossa.tinytranslations.persistent.YamlStyleStorage;
import lombok.Getter;
import me.xidentified.archgpt.commands.AdminReportCommandExecutor;
import me.xidentified.archgpt.commands.ArchGPTCommand;
import me.xidentified.archgpt.commands.ReportTypeCommandExecutor;
import me.xidentified.archgpt.listeners.NPCEventListener;
import me.xidentified.archgpt.reports.*;
import me.xidentified.archgpt.storage.dao.ConversationDAO;
import me.xidentified.archgpt.storage.dao.MySQLConversationDAO;
import me.xidentified.archgpt.storage.dao.SQLiteConversationDAO;
import me.xidentified.archgpt.utils.*;
import net.citizensnpcs.api.npc.NPC;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
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
    private NPCConversationManager conversationManager;
    private NPCEventListener npcEventListener;
    private ConversationDAO conversationDAO;
    private BukkitAudiences audiences;
    Translator translations;

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
            this.audiences = BukkitAudiences.create(this);

            translations = TinyTranslationsBukkit.application(this);
            translations.setMessageStorage(new YamlMessageStorage(new File(getDataFolder(), "/lang/")));
            translations.setStyleStorage(new YamlStyleStorage(new File(getDataFolder(), "/lang/styles.yml")));

            TinyTranslationsBukkit.NM.getObjectTypeResolverMap().put(NPC.class, Map.of(
                    "name", NPC::getName,
                    "fullname", NPC::getFullName,
                    "id", NPC::getId,
                    "uuid", NPC::getUniqueId
            ), npc -> Component.text(npc.getName()));

            translations.addMessages(TinyTranslations.messageFieldsFromClass(Messages.class));

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
            this.conversationManager = new NPCConversationManager(this, configHandler);
            this.npcEventListener = new NPCEventListener(this, conversationManager, configHandler);
            getServer().getPluginManager().registerEvents(npcEventListener, this);
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

            // Check if Google Cloud NLP is enabled in the configuration

            if (getConfig().getBoolean("google_cloud.enabled", false)) {
                try {
                    this.languageServiceClient = configHandler.initializeGoogleCloud();
                    getLogger().info("Google Cloud LanguageServiceClient initialized successfully.");
                } catch (IOException e) {
                    getLogger().severe("Failed to initialize Google Cloud LanguageServiceClient: " + e.getMessage());
                }
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
        if (!new File(getDataFolder(), "/lang/styles.yml").exists()) {
            this.saveResource("lang/styles.yml", false);
        }
        this.translations.loadStyles();

        this.translations.saveLocale(Locale.ENGLISH);
        // wrap with if statement to prevent spigot console warning
        if (!new File(getDataFolder(), "/lang/de.yml").exists()) {
            this.saveResource("lang/de.yml", false);
        }
        this.translations.loadLocales();
    }

    public void sendMessage(CommandSender sender, ComponentLike componentLike) {
        TinyTranslationsBukkit.sendMessage(sender, componentLike);
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

        if (translations != null) {
            // Close translations framework
            translations.close();
        }
        audiences.close();

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
