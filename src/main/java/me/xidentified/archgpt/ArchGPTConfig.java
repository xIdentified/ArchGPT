package me.xidentified.archgpt;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private Duration npcMemoryDuration;
    private int minCharLength;
    private int maxResponseLength;
    private long chatCooldownMillis;
    private boolean shouldSplitLongMsg;

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
        String durationString = config.getString("npc_memory_duration", "7d");
        npcMemoryDuration = parseMinecraftDuration(durationString);
        shouldSplitLongMsg = config.getBoolean("split_long_messages", false);

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

        // Check if the NPC is specifically configured in config.yml
        if (!config.contains("npcs." + npcName)) {
            return null;  // Return null if the NPC is not configured
        }

        // Fetch default prompt from config
        String defaultPrompt = config.getString("default_prompt", "You are an intelligent NPC on a Minecraft Java server.");

        // Fetch prompt for NPC from config
        String npcSpecificPrompt = config.getString("npcs." + npcName);

        // Combine the default prompt with the NPC prompt
        String combinedPrompt = defaultPrompt + (npcSpecificPrompt.isEmpty() ? "" : " " + npcSpecificPrompt);

        // If PAPI is installed, parse the prompt
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, combinedPrompt);
        }

        // Return the combined prompt
        return combinedPrompt;
    }

    // Get in game time from config string
    private Duration parseMinecraftDuration(String durationString) {
        Pattern pattern = Pattern.compile("(?:(\\d+)w)?\\s*(?:(\\d+)d)?\\s*(?:(\\d+)h)?\\s*(?:(\\d+)m)?");
        Matcher matcher = pattern.matcher(durationString);

        if (matcher.matches()) {
            long weeks = matcher.group(1) != null ? Long.parseLong(matcher.group(1)) : 0;
            long days = matcher.group(2) != null ? Long.parseLong(matcher.group(2)) : 0;
            long hours = matcher.group(3) != null ? Long.parseLong(matcher.group(3)) : 0;
            long minutes = matcher.group(4) != null ? Long.parseLong(matcher.group(4)) : 0;

            // Convert Minecraft days to real-time minutes
            return Duration.ofMinutes(minutes)
                    .plusHours(hours)
                    .plusMinutes(days * 20)
                    .plusMinutes(weeks * 7 * 20);
        }
        throw new IllegalArgumentException("Invalid duration format: " + durationString);
    }

    public void toggleDebugMode() {
        debugMode = !debugMode; // Toggle debug mode
        Level loggerLevel = debugMode ? Level.INFO : Level.WARNING;
        logger.setLevel(loggerLevel);

        // Save the updated debug mode to the config
        FileConfiguration config = plugin.getConfig();
        config.set("debug_mode", debugMode);
        plugin.saveConfig();

        logger.info("Debug mode is now " + (debugMode ? "enabled" : "disabled"));
    }

    public void printConfigToConsole() {
        // ANSI color codes
        String RESET = "\u001B[0m";
        String YELLOW = "\u001B[33m";
        String DARK_BLUE = "\u001B[34m"; // Dark Blue ANSI code
        String LIGHT_BLUE = "\u001B[94m"; // Light Blue ANSI code

        String asciiArt =
                "\n" + LIGHT_BLUE +
                        "    _          _    ___ ___ _____ \n" + LIGHT_BLUE +
                        "   /_\\  _ _ __| |_ / __| _ \\_   _|\n" + LIGHT_BLUE +
                        "  / _ \\| '_/ _| ' \\ (_ |  _/ | |  \n" + LIGHT_BLUE +
                        " /_/ \\_\\_| \\__|_||_\\___|_|   |_|  \n" + LIGHT_BLUE +
                        "                                  \n"
                        + RESET + DARK_BLUE + "--- Settings ---\n" + RESET +
                        YELLOW + "Debug Mode: " + debugMode + "\n" + YELLOW +
                        "ChatGPT Engine: " + chatGptEngine + "\n" + YELLOW +
                        "Max API Calls Per Second: " + maxApiCallsPerSecond + "\n" + YELLOW +
                        "Base Prompt: " + defaultPrompt + "\n" + YELLOW +
                        "NPC Memory Duration: " + plugin.getConfig().getString("npc_memory_duration") + "\n" + YELLOW +
                        "Conversation Timeout: " + npcChatTimeoutMillis + "\n" + YELLOW +
                        "Split Long Chats: " + shouldSplitLongMsg + "\n";

        logger.info(LIGHT_BLUE + asciiArt + RESET);

    }

}
