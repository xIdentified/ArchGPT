package me.xidentified.archgpt;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class EnvironmentalContextProvider {

    private final Player player;

    public EnvironmentalContextProvider(Player player) {
        this.player = player;
    }

    public String getFormattedContext(String npcPrompt) {
        String npcLocationContext = getLocationContext();
        String timeOfDay = getTimeOfDay();
        String weather = getWeather();
        String biome = getBiome();
        String playerArmor = getPlayerArmor();
        String playerHandItem = getPlayerHeldItem();
        String entityContext = getNearbyEntitiesContext();
        String playerHealthContext = getPlayerHealthContext();
        String playerHungerContext = getPlayerHungerContext();

        return String.format("%s. The time is %s and the weather is %s. The biome (environment) is %s. The player you're talking to is wearing %s and holding %s. %s. We are %s.",
                npcPrompt, timeOfDay, weather, biome, playerArmor, playerHandItem, playerHealthContext, playerHungerContext, entityContext, npcLocationContext);
    }

    public String getTimeOfDay() {
        long time = player.getWorld().getTime();
        return time < 6000 ? "morning" :
                time < 12000 ? "afternoon" :
                        time < 18000 ? "evening" : "night";
    }

    public String getWeather() {
        World world = player.getWorld();
        boolean isRaining = world.hasStorm();
        boolean isThundering = world.isThundering();
        boolean isSnowing = isRaining && player.getLocation().getBlock().getBiome().name().contains("SNOW");

        return isThundering ? "thunderstorm" :
                isSnowing ? "snowing" :
                        isRaining ? "rainy" : "clear";
    }

    public String getBiome() {
        Biome biome = player.getLocation().getBlock().getBiome();
        return biome.toString();
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

    public String getLocationContext() {
        Location loc = player.getLocation();

        // Check if the player is near water
        if (loc.getBlock().getType() == Material.WATER) {
            return "standing by a body of water";
        }

        int radius = 5;
        int naturalBlocks = 0;
        int manMadeBlocks = 0;
        int totalBlocks = (radius * 2 + 1) * (radius * 2 + 1) * (radius * 2 + 1);  // Total blocks in the sampling cube
        int lightLevel = loc.getBlock().getLightLevel();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = loc.getWorld().getBlockAt(loc.getBlockX() + x, loc.getBlockY() + y, loc.getBlockZ() + z);
                    Material mat = block.getType();

                    if (mat == Material.STONE || mat == Material.DIRT || mat == Material.GRAVEL) {
                        naturalBlocks++;
                    } else if (mat.isBlock() && mat != Material.AIR) {
                        manMadeBlocks++;
                    }
                }
            }
        }

        boolean isOutside = false;
        boolean isUnderLeaves = false;
        boolean isUnderGlass = false;
        int y = loc.getBlockY() + 1; // start checking from one block above the player
        while (y <= 256) {
            Material aboveMaterial = loc.getWorld().getBlockAt(loc.getBlockX(), y, loc.getBlockZ()).getType();
            if (aboveMaterial.name().contains("LEAVES")) {
                isUnderLeaves = true;
            } else if (aboveMaterial.name().contains("GLASS")) {
                isUnderGlass = true;
            } else if (aboveMaterial.isOccluding()) {  // Check if the block obstructs light/view
                break;
            } else if (y == 256) {
                isOutside = true;
            }
            y++;
        }

        if (isOutside && isUnderLeaves) {
            return "standing outside under the shade of trees";
        } else if (isOutside) {
            return "standing outside in the open";
        } else if (isUnderGlass) {
            return "inside a glass-enclosed structure";
        } else {
            // Additional logic to differentiate between a cave and a building
            if (loc.getBlockY() < 60 || (lightLevel < 8 && ((double) naturalBlocks / totalBlocks) > 0.6)) {
                return "deep inside a cave";
            } else {
                return "inside a man-made structure";
            }
        }
    }

    public String getNearbyEntitiesContext() {
        List<Entity> nearbyEntities = player.getNearbyEntities(10, 10, 10);

        // Lists to hold different types of entities
        List<Entity> hostileMobs = new ArrayList<>();
        List<Entity> peacefulMobs = new ArrayList<>();
        List<Entity> otherEntities = new ArrayList<>();

        for (Entity entity : nearbyEntities) {
            if (entity instanceof Monster) {
                hostileMobs.add(entity);
            } else if (entity instanceof Animals || entity instanceof WaterMob || entity instanceof Golem) {
                peacefulMobs.add(entity);
            } else {
                otherEntities.add(entity);
            }
        }

        StringBuilder context = new StringBuilder();

        // Check for hostile mobs and add them to the context
        if (!hostileMobs.isEmpty()) {
            context.append("There are hostile mobs nearby, like ");
            context.append(getEntityNames(hostileMobs));
            context.append(". ");
        }

        // Check for peaceful mobs and add them to the context
        if (!peacefulMobs.isEmpty()) {
            context.append("There are peaceful creatures around, such as ");
            context.append(getEntityNames(peacefulMobs));
            context.append(". ");
        }

        return context.toString().trim();
    }

    // Helper method to get a comma-separated list of entity names
    private String getEntityNames(List<Entity> entities) {
        List<String> names = new ArrayList<>();
        for (Entity entity : entities) {
            names.add(entity.getType().toString().toLowerCase().replace("_", " "));
        }
        return String.join(", ", names);
    }

}

