package me.xidentified.archgpt.storage.impl;

import me.xidentified.archgpt.storage.dao.ConversationDAO;
import me.xidentified.archgpt.storage.model.Conversation;
import java.util.List;
import java.util.UUID;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class YamlConversationDAO implements ConversationDAO {
    private final File dataFile;
    private final YamlConfiguration config;

    public YamlConversationDAO(File pluginFolder) {
        File storageFolder = new File(pluginFolder, "storage");
        if (!storageFolder.exists()) {
            storageFolder.mkdirs();
        }
        this.dataFile = new File(storageFolder, "conversations.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        this.config = YamlConfiguration.loadConfiguration(dataFile);
    }


    @Override
    public synchronized void saveConversation(Conversation conversation) {
        String path = conversation.getPlayerUUID().toString() + "." + conversation.getTimestamp();
        config.set(path + ".npcName", conversation.getNpcName());
        config.set(path + ".message", conversation.getMessage());
        try {
            config.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public synchronized List<Conversation> getConversations(UUID playerUUID, String npcName) {
        List<Conversation> conversations = new ArrayList<>();
        String basePath = playerUUID.toString();
        if (config.contains(basePath)) {
            ConfigurationSection playerSection = config.getConfigurationSection(basePath);
            for (String key : playerSection.getKeys(false)) {
                String storedNpcName = playerSection.getString(key + ".npcName");
                if (storedNpcName.equals(npcName)) {
                    String message = playerSection.getString(key + ".message");
                    long timestamp = playerSection.getLong(key + ".timestamp");
                    conversations.add(new Conversation(playerUUID, npcName, message, timestamp));
                }
            }
        }
        return conversations;
    }

}