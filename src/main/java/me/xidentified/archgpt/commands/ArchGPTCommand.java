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
            plugin.getManager().getConversationUtils().sendNPCMessage(player, npc, response);
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
                completions.addAll(Arrays.asList("broadcast", "version", "reload", "setnpc", "reset-npc-memory", "clear-all-conversations", "debug"));
            }
        }

        return completions;
    }

    private void displayVersionInfo(CommandSender sender) {
        // Fetch server info, ie paper or spigot, plugin version, and java version for debugging
        String serverVersion = Bukkit.getServer().getVersion();
        String pluginVersion = plugin.getDescription().getVersion();
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

}