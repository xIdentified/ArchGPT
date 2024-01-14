package me.xidentified.archgpt.commands;

import me.xidentified.archgpt.ArchGPT;
import me.xidentified.archgpt.utils.Messages;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
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
        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("archgpt.reload")) {
                    plugin.sendMessage(sender, Messages.GENERAL_CMD_NO_PERM);
                    return true;
                }
                plugin.reloadConfig();
                // TODO: Unregister and register listeners
                plugin.sendMessage(sender, Messages.RELOAD_SUCCESS);
                return true;
            } else if (args[0].equalsIgnoreCase("clear-all-conversations")) {
                if (!sender.hasPermission("archgpt.admin")) {
                    plugin.sendMessage(sender, Messages.GENERAL_CMD_NO_PERM);
                    return true;
                }
                clearConversationStorage(sender);
                return true;
            } else if (args[0].equalsIgnoreCase("version")) {
                if (!sender.hasPermission("archgpt.admin")) {
                    plugin.sendMessage(sender, Messages.GENERAL_CMD_NO_PERM);
                    return true;
                }
                displayVersionInfo(sender);
                return true;
            } else if (args[0].equalsIgnoreCase("setnpc")) {
                if (args.length < 3) {
                    sender.sendMessage("Usage: /archgpt setnpc <npcname> <prompt>");
                    return true;
                }
                String npcName = args[1];
                String prompt = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                setNpcPrompt(sender, npcName, prompt);
                return true;
            }
        }

            plugin.sendMessage(sender, Messages.CMD_USAGE);
            return true;
        }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, Command command, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (command.getName().equalsIgnoreCase("archgpt") && args.length == 1) {
            if (sender.hasPermission("archgpt.admin")) {
                completions.addAll(Arrays.asList("version", "reload", "setnpc", "clear-all-conversations"));
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
            plugin.sendMessage(sender, Messages.NPC_NOT_FOUND.formatted(
                    Placeholder.unparsed("name", npcName)
            ));
            return;
        }

        // Access the plugin's configuration
        FileConfiguration config = plugin.getConfig();

        // Update the NPC's prompt in the configuration
        config.set("npcs." + npc.getName(), prompt);
        plugin.sendMessage(sender, Messages.NPC_PROMPT_UPDATED.formatted(
                Placeholder.unparsed("name", npcName)
        ));

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