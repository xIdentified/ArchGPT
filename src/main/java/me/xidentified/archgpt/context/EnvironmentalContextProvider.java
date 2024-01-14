package me.xidentified.archgpt.context;

import me.xidentified.archgpt.ArchGPT;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EnvironmentalContextProvider {
    private final ArchGPT plugin;
    private final Player player;
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, String>> pointsOfInterest; // WorldName, POI Name, Location

    public EnvironmentalContextProvider(ArchGPT plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.pointsOfInterest = new ConcurrentHashMap<>();
        loadPointsOfInterest();
    }

    public String getFormattedContext(String npcPrompt) {
        String npcLocationContext = getLocationContext();
        String timeOfDay = getTimeOfDay();
        String weather = getWeather();
        String biome = getBiome();
        String entityContext = getNearbyEntitiesContext();
        String poiContext = getPointsOfInterestContext();

        return String.format("%s. The current time is %s and the weather is %s. " +
                        "The biome (environment) you are in is %s. %s " +
                        "Your current location is %s %s",
                npcPrompt, timeOfDay, weather, biome,
                entityContext, npcLocationContext, poiContext);
    }

    private void loadPointsOfInterest() {
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection poiSection = config.getConfigurationSection("points_of_interest");
        if (poiSection != null) {
            poiSection.getKeys(false).forEach(worldName -> {
                ConcurrentHashMap<String, String> worldPOIs = new ConcurrentHashMap<>();
                ConfigurationSection worldSection = poiSection.getConfigurationSection(worldName);
                if (worldSection != null) {
                    worldSection.getKeys(false).forEach(poiName -> {
                        String location = worldSection.getString(poiName);
                        worldPOIs.put(poiName, location);
                    });
                    pointsOfInterest.put(worldName, worldPOIs);
                }
            });
        }
    }

    public String getPointsOfInterestContext() {
        StringBuilder poiContext = new StringBuilder("Existing landmarks on the server are: ");
        ConcurrentHashMap<String, String> worldPOIs = pointsOfInterest.get(player.getWorld().getName());

        if (worldPOIs != null) {
            for (ConcurrentHashMap.Entry<String, String> entry : worldPOIs.entrySet()) {
                poiContext.append("The ").append(entry.getKey())
                        .append(" is at ").append(entry.getValue()).append(". ");
            }
        }

        return poiContext.toString();
    }

    public String getTimeOfDay() {
        long time = player.getWorld().getTime();

        // Align with Minecraft's day/night cycle as per the wiki
        return time < 1000 ? "early morning, just after sunrise" :
                time < 3000 ? "late morning" :
                        time < 6000 ? "midday, when the sun is at its peak" :
                                time < 9000 ? "early afternoon" :
                                        time < 12000 ? "mid afternoon" :
                                                time < 13000 ? "late afternoon, as the sun begins to set" :
                                                        time < 14000 ? "sunset" :
                                                                time < 15000 ? "dusk, the sky turning dark" :
                                                                        time < 18000 ? "early night, with the moon rising" :
                                                                                time < 21000 ? "late night, under a high moon" :
                                                                                        time < 24000 ? "deep night, just before dawn" :
                                                                                                "unknown";  // Fallback for unexpected values
    }

    public boolean isSnowyBiome(Biome biome) {
        // List of snowy biomes in Minecraft (adjust as needed)
        List<Biome> snowyBiomes = Arrays.asList(
                Biome.SNOWY_BEACH, Biome.SNOWY_SLOPES,
                Biome.SNOWY_TAIGA, Biome.SNOWY_PLAINS,
                Biome.ICE_SPIKES, Biome.FROZEN_RIVER,
                Biome.FROZEN_OCEAN, Biome.FROZEN_PEAKS,
                Biome.DEEP_FROZEN_OCEAN
        );

        return snowyBiomes.contains(biome);
    }

    public String getWeather() {
        World world = player.getWorld();
        boolean isRaining = world.hasStorm();
        boolean isThundering = world.isThundering();

        Biome currentBiome = player.getLocation().getBlock().getBiome();
        boolean isSnowing = isRaining && isSnowyBiome(currentBiome);

        return isThundering ? "thunderstorm" :
                isSnowing ? "snowing" :
                        isRaining ? "rainy" : "clear";
    }


    public String getBiome() {
        Biome biome = player.getLocation().getBlock().getBiome();
        return biome.toString();
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
            return "outside under the shade of trees";
        } else if (isOutside) {
            return "outside in the open";
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

        // Maps to hold counts of different types of entities
        Map<String, Integer> hostileMobCounts = new HashMap<>();
        Map<String, Integer> peacefulMobCounts = new HashMap<>();

        for (Entity entity : nearbyEntities) {
            String entityName = entity.getType().name();
            if (entity instanceof Monster) {
                hostileMobCounts.put(entityName, hostileMobCounts.getOrDefault(entityName, 0) + 1);
            } else if (entity instanceof Animals || entity instanceof WaterMob || entity instanceof Golem) {
                peacefulMobCounts.put(entityName, peacefulMobCounts.getOrDefault(entityName, 0) + 1);
            }
        }

        String context = describeEntityCounts(hostileMobCounts, "hostile") +
                describeEntityCounts(peacefulMobCounts, "peaceful");

        return context.trim();
    }

    private String describeEntityCounts(Map<String, Integer> entityCounts, String entityType) {
        StringBuilder description = new StringBuilder();
        if (!entityCounts.isEmpty()) {
            description.append("There are ").append(entityType).append(" creatures around, such as ");
            for (Map.Entry<String, Integer> entry : entityCounts.entrySet()) {
                String entityName = entry.getKey();
                int count = entry.getValue();
                description.append(describeQuantity(count)).append(" ").append(entityName).append(count > 1 ? "s" : "").append(", ");
            }
            // Remove the last comma and space
            description.setLength(description.length() - 2);
            description.append(". ");
        }
        return description.toString();
    }

    private String describeQuantity(int count) {
        if (count == 1) {
            return "a";
        } else if (count > 1 && count <= 3) {
            return "a few";
        } else if (count > 3 && count <= 6) {
            return "several";
        } else {
            return "a lot of";
        }
    }

}

