package me.xidentified.archgpt.context;

import me.xidentified.archgpt.ArchGPT;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
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

        return String.format("%s. The hour is %s, and %s. " +
                        "You find yourself in %s, a realm where %s " +
                        "Currently, I am %s. Nearby, you can find places of note: %s",
                npcPrompt, timeOfDay, weather, biome, entityContext, npcLocationContext, poiContext);
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
                        if (location != null) worldPOIs.put(poiName, location);
                    });
                    pointsOfInterest.put(worldName, worldPOIs);
                }
            });
        }
    }

    public String getPointsOfInterestContext() {
        StringBuilder poiContext = new StringBuilder();
        ConcurrentHashMap<String, String> worldPOIs = pointsOfInterest.get(player.getWorld().getName());

        if (worldPOIs != null && !worldPOIs.isEmpty()) {
            poiContext.append("In this world, landmarks such as ");
            worldPOIs.forEach((poiName, location) -> poiContext.append(poiName).append(" at ").append(location).append(", "));
            // Remove the last comma and space
            poiContext.setLength(poiContext.length() - 2);
            poiContext.append(", stand as testaments to the world's rich history and culture.");
        } else {
            poiContext.append("There are no notable landmarks within my knowledge in this part of the world.");
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

        if (isThundering) {
            return "a ferocious thunderstorm rages above, the sky alight with flashes of lightning";
        } else if (isSnowing) {
            return "a gentle snowfall blankets the landscape, each flake a whisper in the serene quiet";
        } else if (isRaining) {
            return "a steady rain falls from the grey heavens, the rhythmic patter a constant companion";
        } else {
            return "the sky is clear, a vast canvas painted with the vibrant hues of the day";
        }
    }

    public String getLocationContext() {
        Location loc = player.getLocation();
        World world = loc.getWorld();

        // Near water check
        if (world.getBlockAt(loc).getType() == Material.WATER) {
            return "beside the gentle ripples of a serene body of water, the reflection of the sky dancing on its surface.";
        }

        // Check surroundings
        int radius = 5;
        Map<Material, Integer> materialCounts = new HashMap<>();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Material mat = world.getBlockAt(loc.clone().add(x, y, z)).getType();
                    materialCounts.put(mat, materialCounts.getOrDefault(mat, 0) + 1);
                }
            }
        }

        // Determine environment characteristics
        boolean isOutside = true, isUnderLeaves = false, isUnderGlass = false;
        for (int y = loc.getBlockY() + 1; y <= world.getMaxHeight(); y++) {
            Material aboveMaterial = world.getBlockAt(loc.getBlockX(), y, loc.getBlockZ()).getType();
            isUnderLeaves = aboveMaterial.name().contains("LEAVES");
            isUnderGlass = aboveMaterial.name().contains("GLASS");
            if (aboveMaterial.isOccluding()) {
                isOutside = false;
                break;
            }
        }

        // Describing the environment
        String locationDescription; // unknown location
        if (isOutside) {
            locationDescription = isUnderLeaves ? "outside under the shade of trees" : "outside in the open";
        } else if (isUnderGlass) {
            locationDescription = "within a structure of glass, surrounded by walls that shimmer like a crystal palace under the sun";
        } else {
            // Specific building types
            if (materialCounts.keySet().stream().anyMatch(mat -> mat.name().contains("BRICK"))) {
                locationDescription = "ensconced within the sturdy walls of a brick edifice, the air echoing with tales of old.";
            } else if (materialCounts.keySet().stream().anyMatch(mat -> mat.name().contains("WOOD") || mat.name().contains("LOG"))) {
                locationDescription = "inside a quaint wooden abode, the scent of pine lingering in the air.";
            } else if (loc.getBlockY() < 60 && materialCounts.getOrDefault(Material.STONE, 0) > materialCounts.size() / 2) {
                locationDescription = "deep within the bowels of a cavern, where the rocks whisper secrets of the earth.";
            } else {
                locationDescription = "in a man-made structure, where the hand of creation has molded the surroundings.";
            }
        }

        return locationDescription;
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

    public String getBiome() {
        Biome biome = player.getLocation().getBlock().getBiome();

        return switch (biome) {
            case BADLANDS -> "a rugged terrain of red sand and terracotta, where the sun beats down mercilessly";
            case BAMBOO_JUNGLE -> "a lush jungle thick with towering bamboo stalks, teeming with life";
            case BEACH -> "a serene beach, with gentle waves washing over the soft sand";
            case BIRCH_FOREST -> "a peaceful birch forest, the white bark trees standing tall and proud";
            case DARK_FOREST -> "a foreboding dark forest, its dense canopy casting shadows below";
            case DESERT -> "an endless desert, the hot sun glaring down on the vast expanse of sand";
            case THE_END -> "the mysterious End, a strange dimension of floating islands and eerie endermen";
            case FLOWER_FOREST -> "a beautiful flower forest, bursting with color and the scent of blooms";
            case FOREST -> "a vibrant forest, with a variety of trees and the sound of wildlife all around";
            case FROZEN_OCEAN -> "a frozen ocean, its icy surface stretching out under the cold sky";
            case FROZEN_RIVER -> "a frozen river, its icy path winding through the chilly landscape";
            case JUNGLE -> "a dense jungle, full of vibrant green foliage and the calls of exotic animals";
            case MUSHROOM_FIELDS -> "the surreal mushroom fields, dotted with giant mushrooms and a unique ecosystem";
            case NETHER_WASTES -> "the Nether, a hellish landscape of fire, lava, and danger at every turn";
            case OCEAN -> "the vast ocean, its deep waters holding untold secrets and marine life";
            case PLAINS -> "the open plains, a wide expanse of grassland under the wide sky";
            case RIVER -> "a winding river, its waters flowing through varied landscapes";
            case SAVANNA -> "a dry savanna, dotted with acacia trees and teeming with wildlife";
            case SNOWY_BEACH -> "a snowy beach, where snow meets the icy waters of the ocean";
            case SNOWY_TAIGA -> "a snow-covered taiga, with frosted trees and a quiet, wintry atmosphere";
            case SWAMP -> "a murky swamp, with waterlogged trees and a sense of mystery in the air";
            case TAIGA -> "a taiga biome, home to spruce trees and a rich array of wildlife";
            case WARM_OCEAN -> "a warm ocean, its waters teeming with vibrant coral and marine life";
            case WINDSWEPT_HILLS -> "windswept hills, with rolling landscapes that stretch as far as the eye can see";
            case WINDSWEPT_FOREST -> "a windswept forest, where trees bend in the constant, howling wind";
            case WINDSWEPT_GRAVELLY_HILLS -> "gravelly hills with sparse vegetation, shaped by the relentless wind";
            case WINDSWEPT_SAVANNA -> "a windswept savanna, where hardy trees and grasses withstand the gusty breezes";
            case WOODED_BADLANDS -> "wooded badlands, where stark terrain is broken by patches of forest";
            case THE_VOID -> "the Void, an empty expanse stretching into the unknown";
            case DRIPSTONE_CAVES ->
                    "dripstone caves, where stalactites and stalagmites create a striking underground landscape";
            case LUSH_CAVES -> "lush caves, a hidden world of vibrant flora and flowing waterfalls";
            case DEEP_DARK -> "the deep dark, a realm of shadows and forgotten secrets";
            case MEADOW -> "a serene meadow, blanketed with wildflowers and the hum of bees";
            case GROVE -> "a tranquil grove, where light filters softly through the leaves";
            case SNOWY_SLOPES -> "snowy slopes, a crisp and icy terrain that sparkles under the sun";
            case FROZEN_PEAKS -> "frozen peaks, where the air is thin and the snow never melts";
            case JAGGED_PEAKS -> "jagged peaks, a treacherous and rugged mountain range";
            case STONY_PEAKS -> "stony peaks, where bare rock faces tower over the landscape";
            case MANGROVE_SWAMP -> "a mangrove swamp, tangled with roots and teeming with life";
            case DEEP_LUKEWARM_OCEAN -> "the deep lukewarm ocean, its waters hiding a world of aquatic wonders";
            case DEEP_COLD_OCEAN -> "the deep cold ocean, a chilly expanse home to hardy marine creatures";
            case DEEP_FROZEN_OCEAN -> "the deep frozen ocean, where icebergs float on the icy surface";
            // Any additional biomes introduced in future updates
            default -> "an uncharted realm, its secrets yet to be revealed";
        };
    }


}

