package me.xidentified.archgpt.utils;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.xidentified.archgpt.ArchGPT;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class Placeholders extends PlaceholderExpansion {
    private final ArchGPT plugin;

    public Placeholders(ArchGPT plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getAuthor() {
        return "xIdentified";
    }

    @Override
    public @NotNull String getIdentifier() {
        return "ArchGPT";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true; // This is required or else PlaceholderAPI will unregister the Expansion on reload
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if(params.equalsIgnoreCase("placeholder1")){
            return plugin.getConfig().getString("placeholders.placeholder1", "default1");
        }

        return null; // Placeholder is unknown by the Expansion
    }
}