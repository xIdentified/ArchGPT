package me.xidentified.archgpt.commands;

import me.xidentified.archgpt.ArchGPT;
import me.xidentified.archgpt.utils.Messages;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ArchGPTCommand implements CommandExecutor, TabCompleter {
    private final ArchGPT plugin;

    public ArchGPTCommand(ArchGPT plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!sender.hasPermission("archgpt.admin")) {
            plugin.sendMessage(sender, Messages.GENERAL_CMD_NO_PERM);
            return true;
        }

        if (args.length == 0) {
            plugin.sendMessage(sender, Messages.CMD_USAGE);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                plugin.reloadConfig();
                plugin.loadLanguages();
                // TODO: Unregister and register listeners
                plugin.sendMessage(sender, Messages.RELOAD_SUCCESS);
                break;

            case "clear-all-conversations":
                clearConversationStorage(sender);
                break;

            case "version":
                displayVersionInfo(sender);
                break;

            case "setnpc":
                if (args.length < 3) {
                    plugin.sendMessage(sender, Messages.SETNPC_CMD_USAGE);
                    return true;
                }
                String npcName = args[1];
                String prompt = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                setNpcPrompt(sender, npcName, prompt);
                break;

            case "broadcast":
                handleBroadcastCommand(sender, args);
                break;

            case "reset-npc-memory":
                handleResetNpcMemoryCommand(sender, args);
                break;

            case "debug":
                toggleDebugMode(sender);
                break;
            case "checkprovider":
                if (args.length < 2) {
                    plugin.sendMessage(sender, Messages.CHECKPROVIDER_CMD_USAGE);
                    return true;
                }
                String providerArg = args[1];
                checkProvider(sender, providerArg);
                break;
            default:
                plugin.sendMessage(sender, Messages.CMD_USAGE);
                break;
        }
        return true;
    }

    private void handleBroadcastCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.sendMessage(sender, Messages.BROADCAST_CMD_USAGE);
            return;
        }

        String npcName = args[1];
        NPC npc = null;
		for (NPC current : CitizensAPI.getNPCRegistry()) {
			if (current.getName().equals(npcName)) {
				npc = current;
				break;
			}
		}
        if (npc == null) {
            // TODO error handling
            return;
        }

        String messageText = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        broadcastNpcMessage(npc, messageText);
    }

    private void broadcastNpcMessage(NPC npc, String response) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.getConversationManager().getConversationUtils().sendNPCMessage(player, npc, response);
        }
    }

    private void handleResetNpcMemoryCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.sendMessage(sender, Messages.RESETMEMORY_CMD_USAGE);
            return;
        }

        String npcName = args[1];
        NPC npc = findNpcByName(npcName);
        if (npc == null) {
            plugin.sendMessage(sender, Messages.NPC_NOT_FOUND.insertString("name", npcName));
            return;
        }

        plugin.getConversationDAO().clearConversationsForNpc(npcName);
        plugin.sendMessage(sender, Messages.NPC_MEMORY_RESET.insertObject("npc", npc));
    }

    private void toggleDebugMode(CommandSender sender) {
        plugin.getConfigHandler().toggleDebugMode();
        boolean isDebugMode = plugin.getConfigHandler().isDebugMode();

        plugin.sendMessage(sender, Messages.DEBUG_MODE.insertBool("toggle", isDebugMode));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, Command command, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (command.getName().equalsIgnoreCase("archgpt") && args.length == 1) {
            if (sender.hasPermission("archgpt.admin")) {
                completions.addAll(Arrays.asList("broadcast", "checkprovider", "version", "reload", "setnpc", "reset-npc-memory", "clear-all-conversations", "debug"));
            }
        }

        if (args.length == 2 && "checkprovider".equalsIgnoreCase(args[0])) {
            completions.addAll(Arrays.asList("openai", "deepseek", "anthropic", "openrouter", "google_ai_studio", "huggingface", "all"));
        }

        return completions;
    }

    private void displayVersionInfo(CommandSender sender) {
        // Fetch server info, ie paper or spigot, plugin version, and java version for debugging
        String serverVersion = Bukkit.getServer().getVersion();
        String pluginVersion = plugin.getPluginMeta().getVersion();
        String javaVersion = System.getProperty("java.version");

        // Displaying info
        plugin.sendMessage(sender, Messages.VERSION_INFO.formatted(
                Placeholder.unparsed("server-ver", serverVersion),
                Placeholder.unparsed("plugin-ver", pluginVersion),
                Placeholder.unparsed("java-ver", javaVersion)
        ));
    }

    private void clearConversationStorage(CommandSender sender) {
        // Implement logic to clear conversation storage
        try {
            plugin.getConversationDAO().clearAllConversations();
            plugin.sendMessage(sender, Messages.CLEAR_STORAGE_SUCCESS);
        } catch (Exception e) {
            plugin.getLogger().severe("Error clearing conversation storage: " + e.getMessage());
            plugin.sendMessage(sender, Messages.CLEAR_STORAGE_ERROR);
        }
    }

    private void setNpcPrompt(CommandSender sender, String npcName, String prompt) {
        NPC npc = findNpcByName(npcName);
        if (npc == null) {
            plugin.sendMessage(sender, Messages.NPC_NOT_FOUND.insertString("name", npcName));
            return;
        }

        // Access the plugin's configuration
        FileConfiguration config = plugin.getConfig();

        // Update the NPC's prompt in the configuration
        config.set("npcs." + npc.getName(), prompt);
        plugin.sendMessage(sender, Messages.NPC_PROMPT_UPDATED.insertObject("npc", npc));

        // Save changes to the configuration
        plugin.saveConfig();
    }

    private NPC findNpcByName(String name) {
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (npc.getName().equalsIgnoreCase(name)) {
                return npc;
            }
        }
        return null;
    }

    private void checkProvider(CommandSender sender, String providerArg) {
    List<String> providersToTest = new ArrayList<>();
    
    if ("all".equalsIgnoreCase(providerArg)) {
        providersToTest.addAll(Arrays.asList("openai", "deepseek", "anthropic", "openrouter", "google_ai_studio", "huggingface"));
    } else {
        providersToTest.add(providerArg);
    }
    
    plugin.sendMessage(sender, Component.text("Testing " + (providersToTest.size() > 1 ? "all providers" : providerArg) + "..."));
    
    // Run the tests asynchronously to avoid blocking the server
    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
        for (String provider : providersToTest) {
            // Send message on the main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.sendMessage(sender, Messages.CHECKPROVIDER_TESTING.insertString("provider", provider));
            });
            
            try {
                boolean success = testProvider(provider);
                
                // Send result on the main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (success) {
                        plugin.sendMessage(sender, Messages.CHECKPROVIDER_SUCCESS.insertString("provider", provider));
                    } else {
                        plugin.sendMessage(sender, Messages.CHECKPROVIDER_ERROR
                            .insertString("provider", provider)
                            .insertString("error", "Check console for details"));
                    }
                });
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.sendMessage(sender, Messages.CHECKPROVIDER_ERROR
                        .insertString("provider", provider)
                        .insertString("error", e.getMessage()));
                });
            }
            
            // Add a small delay between tests
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // Send completion message
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (providersToTest.size() > 1) {
                plugin.sendMessage(sender, Messages.CHECKPROVIDER_ALL_COMPLETE);
            }
        });
    });
}

private boolean testProvider(String provider) {
    try {
        // Create test request
        JsonObject testContext = new JsonObject();
        testContext.addProperty("npc", "TestNPC");
        testContext.addProperty("environment", "Test environment");
        testContext.addProperty("player", "Test player");
        
        JsonObject request = new JsonObject();
        request.add("context", testContext);
        request.addProperty("message", "Hello, this is a test message.");
        request.addProperty("request_type", "CONVERSATION");
        request.addProperty("provider", provider);
        request.addProperty("max_tokens", 50);
        
        // Send request to MCP server
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(plugin.getConfigHandler().getMcpServerUrl()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(request.toString()))
                .build();
        
        HttpResponse<String> response = plugin.getHttpClient().send(httpRequest, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            plugin.getLogger().warning("Provider " + provider + " test failed with status: " + response.statusCode());
            plugin.getLogger().warning("Response: " + response.body());
            return false;
        }
        
        JsonObject responseJson = JsonParser.parseString(response.body()).getAsJsonObject();
        if (!responseJson.has("output")) {
            plugin.getLogger().warning("Provider " + provider + " test failed: No output in response");
            return false;
        }
        
        String output = responseJson.get("output").getAsString();
        plugin.getLogger().info("Provider " + provider + " test successful: " + output);
        return true;
        
    } catch (Exception e) {
        plugin.getLogger().severe("Provider " + provider + " test failed: " + e.getMessage());
        return false;
    }
}

}