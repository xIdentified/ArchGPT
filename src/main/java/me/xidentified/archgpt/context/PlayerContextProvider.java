package me.xidentified.archgpt.context;

import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.api.player.profess.PlayerClass;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class PlayerContextProvider {
    private final Player player;
    private final boolean isMMOCoreInstalled;

    public PlayerContextProvider(Player player) {
        this.player = player;
        this.isMMOCoreInstalled = Bukkit.getPluginManager().isPluginEnabled("MMOCore");
    }
    public String getFormattedContext(String npcPrompt) {
        String playerExperience = getPlayerExperience();
        String playerArmor = getPlayerArmor();
        String playerHandItem = getPlayerHeldItem();
        String playerHealthContext = getPlayerHealthContext();
        String playerHungerContext = getPlayerHungerContext();
        String mmocoreContext = isMMOCoreInstalled ? getMMOCoreContext() : "";

        return String.format("%s The adventurer before you is clad in %s, wielding %s in their grasp. %s %s %s %s",
                npcPrompt, playerArmor, playerHandItem, playerHealthContext, playerHungerContext, playerExperience, mmocoreContext);
    }

    public String getPlayerExperience() {
        try {
            long ticksPlayed = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
            long hoursPlayed = ticksPlayed / (20 * 60 * 60);
            int mobsKilled = player.getStatistic(Statistic.MOB_KILLS);

            if (!player.hasPlayedBefore()) {
                return "A fresh face in our lands, embarking on their very first journey!";
            } else if (hoursPlayed < 8 || mobsKilled < 20) {
                return "A new adventurer, still finding their footing in our world.";
            } else if (hoursPlayed < 50 || mobsKilled > 50) {
                return "An experienced wanderer, familiar with the twists and turns of these lands.";
            } else {
                return "A seasoned veteran, well-versed in the lore and challenges of our realm.";
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
            return "Bearing scars of fierce battles, they stand, weathered and weary.";
        } else if (health <= maxHealth * 0.5) {
            return "With wounds still fresh, they carry the marks of recent strife.";
        }
        return "";
    }

    public String getPlayerHungerContext() {
        int hunger = player.getFoodLevel();

        if (hunger <= 6) {
            return "Their gaunt appearance speaks of a dire need for sustenance.";
        } else if (hunger <= 12) {
            return "A hint of hunger lingers in their eyes, a subtle reminder of their mortal needs.";
        }
        return "";
    }

    public String getPlayerArmor() {
        ItemStack[] armor = player.getInventory().getArmorContents();
        StringBuilder armorDesc = new StringBuilder();
        for (ItemStack item : armor) {
            if (item != null && item.getType() != Material.AIR) {
                String itemName = item.getType().name().replace("_", " ").toLowerCase();
                int durability = item.getType().getMaxDurability() - (int) item.getDurability();
                int maxDurability = item.getType().getMaxDurability();
                double durabilityPercentage = durability / (double) maxDurability * 100;

                if (item.hasItemMeta() && item.getItemMeta().hasEnchants()) {
                    itemName += " (enchanted)";
                }

                if (durabilityPercentage > 80) {
                    armorDesc.append("well-maintained ").append(itemName);
                } else if (durabilityPercentage > 50) {
                    armorDesc.append("moderately used ").append(itemName);
                } else {
                    armorDesc.append("worn-out ").append(itemName);
                }
                armorDesc.append(", ");
            }
        }
        return armorDesc.length() > 2 ? "armor crafted from " + armorDesc.substring(0, armorDesc.length() - 2) : "no protective gear";
    }

    public String getPlayerHeldItem() {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != Material.AIR) {
            String itemName = item.getType().name();
            if (item.hasItemMeta() && item.getItemMeta().hasEnchants()) {
                itemName += "(imbued with enchantments)";
            }
            return itemName;
        } else {
            return "nothing";
        }
    }

    private String getMMOCoreContext() {
        PlayerData playerData = PlayerData.get(player);

        // Get player class and level from MMOCore
        PlayerClass playerClass = playerData.getProfess();
        int playerLevel = playerData.getLevel();

        // Check for party status
        boolean isInParty = playerData.getParty() != null;

        String partyStatus = isInParty ? "Joined by comrades in a party," : "Venturing alone, a solitary figure against the world,";
        String classStatus = playerClass != null ? "a " + playerClass.getName() + " by trade," : "undecided in their path,";
        String levelStatus = "at the level of " + playerLevel + ",";

        return String.format("%s %s their journey has brought them to the level of %s", partyStatus, classStatus, levelStatus);
    }
}
