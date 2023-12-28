package me.xidentified.archgpt.utils;

import org.bukkit.entity.Player;

public class LocaleUtils {

    public static String getPlayerLocale(Player player) {
        return player.getLocale(); // Returns the locale in format like "en_US"
    }
}
