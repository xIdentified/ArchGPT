package me.xidentified.archgpt;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;
import java.util.logging.Logger;

public class ArchGPTConfig {
    private final Logger logger;
    private final JavaPlugin plugin;
    private boolean debugMode;
    private double maxApiCallsPerSecond;
    private long npcChatTimeoutMillis;
    private String defaultPrompt;
    private String apiKey;
    private String chatGptEngine;
    private String playerMessageColor;
    private String npcNameColor;
    private String playerNameColor;
    private String npcMessageColor;
    private int minCharLength;
    private int maxResponseLength;
    private long chatCooldownMillis;

    public ArchGPTConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        loadConfig();
    }

    private void loadConfig() {
        // Load the configuration file and set default values
        saveDefaultConfig();

        FileConfiguration config = plugin.getConfig();
        debugMode = config.getBoolean("debug_mode", false);
        maxApiCallsPerSecond = config.getDouble("max_api_calls_per_second", 5.0);
        npcChatTimeoutMillis = config.getLong("response_timeout", 60000);
        defaultPrompt = config.getString("default_prompt", "Hello!");
        chatGptEngine = config.getString("chatgpt_engine", "text-davinci-003");
        minCharLength = config.getInt("min_char_length", 10);
        maxResponseLength = config.getInt("max_response_length", 200);
        chatCooldownMillis = config.getLong("chat_cooldown", 3000);
        npcNameColor = config.getString("chat_colors.npc_name");
        playerNameColor = config.getString("chat_colors.player_name");
        npcMessageColor = config.getString("chat_colors.npc_message");
        playerMessageColor = config.getString("chat_colors.player_message");

        // Set the logger level based on debugMode
        Level loggerLevel = debugMode ? Level.INFO : Level.WARNING;
        logger.setLevel(loggerLevel);

        // Check if the API key is set in the configuration
        apiKey = config.getString("api_key", "YOUR_OPENAI_API_KEY");
        if (apiKey.equals("YOUR_OPENAI_API_KEY")) {
            logger.severe("OpenAI API key is not set in the config.yml. Plugin will not function properly without it!");
            throw new IllegalStateException("OpenAI API key is missing or invalid.");
        }
    }

    public void saveDefaultConfig() {
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveDefaultConfig();
    }

    public boolean isNPCConfigured(String npcName) {
        FileConfiguration config = plugin.getConfig();
        // Assuming you have a configuration section called 'npcs' that lists all NPCs
        return config.getConfigurationSection("npcs").contains(npcName);
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public long getNpcChatTimeoutMillis() {
        return npcChatTimeoutMillis;
    }

    public String getDefaultPrompt() {
        return defaultPrompt;
    }

    public String getApiKey() {
        return apiKey;
    }

    public int getMaxResponseLength() {
        return maxResponseLength;
    }

    public String getChatGptEngine() {
        return chatGptEngine;
    }

    public int getMinCharLength() {
        return minCharLength;
    }

    public long getChatCooldownMillis() { return chatCooldownMillis; }

    public String getNpcNameColor() {
        return npcNameColor;
    }

    public String getPlayerNameColor() {
        return playerNameColor;
    }

    public String getNpcMessageColor() {
        return npcMessageColor;
    }

    public String getPlayerMessageColor() {
        return playerMessageColor;
    }

    public void printConfigToConsole() {
        // ANSI color codes
        String RESET = "\u001B[0m";
        String GREEN = "\u001B[32m";
        String YELLOW = "\u001B[33m";

        logger.info(GREEN + "----- ArchGPT Configuration -----" + RESET);
        logger.info(YELLOW + "Debug Mode: " + debugMode + RESET);
        logger.info(YELLOW + "Max API Calls Per Second: " + maxApiCallsPerSecond + RESET);
        logger.info(YELLOW + "NPC Chat Timeout (Millis): " + npcChatTimeoutMillis + RESET);
        logger.info(YELLOW + "Default Prompt: " + defaultPrompt + RESET);
        logger.info(YELLOW + "ChatGPT Engine: " + chatGptEngine + RESET);
        logger.info(GREEN + "--------------------------------" + RESET);
    }


}
