package me.xidentified.archgpt.context;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class PlayerContextProvider {
    private final Player player;
    public PlayerContextProvider(Player player) {
        this.player = player;
    }
    public String getFormattedContext(String npcPrompt) {
        String playerExperience = getPlayerExperience();
        String playerArmor = getPlayerArmor();
        String playerHandItem = getPlayerHeldItem();
        String playerHealthContext = getPlayerHealthContext();
        String playerHungerContext = getPlayerHungerContext();

        return String.format("%s. The player you're speaking to is wearing %s and holding %s. %s %s %s",
                npcPrompt, playerArmor, playerHandItem, playerHealthContext, playerHungerContext, playerExperience);
    }

    public String getPlayerExperience() {
        try {
            long ticksPlayed = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
            long hoursPlayed = ticksPlayed / (20 * 60 * 60);
            int mobsKilled = player.getStatistic(Statistic.MOB_KILLS);

            if (!player.hasPlayedBefore()) {
                return "It is their first time playing on the server!";
            } else if (hoursPlayed < 8 || mobsKilled < 20 ) {
                return "They seem like a newbie to the server!";
            } else if (hoursPlayed < 50 || mobsKilled > 50 ) {
                return "They seem like an experienced player.";
            } else {
                return "They seem like a veteran of the server.";
            }
        } catch (IllegalArgumentException e) {
            Bukkit.getLogger().severe(e.toString());
            return "";
        }
    }

    public String getPlayerHealthContext() {
        double health = player.getHealth();
        double maxHealth = player.getMaxHealth();

        if (health <= maxHealth * 0.25) {
            return "The player appears severely injured.";
        } else if (health <= maxHealth * 0.5) {
            return "The player has visible injuries.";
        }
        return "";
    }

    public String getPlayerHungerContext() {
        int hunger = player.getFoodLevel();

        if (hunger <= 6) {
            return "The player looks extremely hungry and malnourished.";
        } else if (hunger <= 12) {
            return "The player seems hungry.";
        }
        return "";
    }

    public String getPlayerArmor() {
        ItemStack[] armor = player.getInventory().getArmorContents();
        StringBuilder armorDesc = new StringBuilder();
        for (ItemStack item : armor) {
            if (item != null && item.getType() != Material.AIR) {
                armorDesc.append(item.getType().name());
                if (item.hasItemMeta() && item.getItemMeta().hasEnchants()) {
                    armorDesc.append("(Enchanted)");
                }
                armorDesc.append(", ");
            }
        }
        return armorDesc.length() > 2 ? armorDesc.substring(0, armorDesc.length() - 2) : "none";
    }

    public String getPlayerHeldItem() {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != Material.AIR) {
            String itemName = item.getType().name();
            if (item.hasItemMeta() && item.getItemMeta().hasEnchants()) {
                itemName += "(Enchanted)";
            }
            return itemName;
        } else {
            return "nothing";
        }
    }
}
