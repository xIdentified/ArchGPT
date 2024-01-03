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
import me.xidentified.archgpt.utils.Messages;
import me.xidentified.archgpt.utils.Metrics;
import me.xidentified.archgpt.utils.Placeholders;
import me.xidentified.archgpt.utils.TranslationService;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.ComponentLike;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
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
    // Managers
    private ArchGPTConfig configHandler;
    private HologramManager hologramManager;
    private ReportManager reportManager;
    private TranslationService translationService;
    private NPCConversationManager manager;
    BukkitAudiences audiences;
    Translations translations;

    @Override
    public void onEnable() {

        try {
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
            Objects.requireNonNull(this.getCommand("npcreports")).setExecutor(new AdminReportCommandExecutor(this));
            Objects.requireNonNull(this.getCommand("selectreporttype")).setExecutor(new ReportTypeCommandExecutor(this));
            Objects.requireNonNull(this.getCommand("reportnpcmessage")).setExecutor(new ReportTypeCommandExecutor(this));
            Objects.requireNonNull(this.getCommand("archgpt")).setExecutor(new ArchGPTCommand(this));
            Objects.requireNonNull(this.getCommand("archgpt")).setTabCompleter(new ArchGPTCommand(this));


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
        this.saveResource("lang/ru.yml", false);
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
