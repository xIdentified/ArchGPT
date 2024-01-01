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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
                        "The biome (environment) you are in is %s. %s. " +
                        "Your current location is %s. %s",
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
        StringBuilder poiContext = new StringBuilder();
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

        // Minecraft day starts at 0 (sunrise) and ends at 23999
        return time < 2300 ? "early morning" : // 0 - 2299
                time < 4500 ? "mid morning" :   // 2300 - 4499
                        time < 6000 ? "late morning" :  // 4500 - 5999
                                time < 9000 ? "noon" :     // 6000 - 8999
                                        time < 12000 ? "early afternoon" : // 9000 - 11999
                                                time < 13500 ? "mid afternoon" :  // 12000 - 13499
                                                        time < 18000 ? "late afternoon" : // 13500 - 17999
                                                                time < 21000 ? "early evening" : // 18000 - 20999
                                                                        time < 24000 ? "night" :  // 21000 - 23999
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

