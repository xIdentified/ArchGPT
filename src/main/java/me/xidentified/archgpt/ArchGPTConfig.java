package me.xidentified.archgpt;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;
import java.util.logging.Logger;

@Slf4j
@Getter
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
        chatGptEngine = config.getString("chatgpt_engine", "gpt-3.5-turbo-1106");
        minCharLength = config.getInt("min_char_length", 10);
        maxResponseLength = config.getInt("max_response_length", 200); // in tokens
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

    public String getNpcPrompt(String npcName, Player player) {
        FileConfiguration config = plugin.getConfig();

        // Fetch default prompt from config
        String defaultPrompt = config.getString("default_prompt", "You are an intelligent NPC on a Minecraft Java server.");

        // Fetch prompt for NPC from config
        String npcSpecificPrompt = config.getString("npcs." + npcName, "");

        // Combine the default prompt with the NPC prompt
        String combinedPrompt = defaultPrompt + (npcSpecificPrompt.isEmpty() ? "" : " " + npcSpecificPrompt);

        // If PAPI is installed parse the prompt
        if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, combinedPrompt);
        }

        // Or just return the combined prompt
        return combinedPrompt;
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
