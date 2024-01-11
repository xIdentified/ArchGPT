package me.xidentified.archgpt;

import lombok.Getter;
import net.citizensnpcs.api.npc.NPC;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Getter
public class HologramManager {

    private final JavaPlugin plugin;
    private ArmorStand armorStand;
    private int animationState = 0;
    private BukkitRunnable animationTask;
    private final List<ArmorStand> allHolograms = new CopyOnWriteArrayList<>();
    private final Map<UUID, ArmorStand> playerHolograms = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitRunnable> animationTasks = new ConcurrentHashMap<>();

    public HologramManager(JavaPlugin plugin) {
        this.plugin = plugin;
        startCleanupTask();
    }

    /**
     * Displays a hologram for first time players instructing them on how to interact with an NPC
     *
     * @param npc    The NPC for which the hologram is being created.
     * @param player The player interacting with the NPC.
     */
    public void showInteractionHologram(NPC npc, Player player) {
        // if (!player.hasPlayedBefore) {
        String hologramText = plugin.getConfig().getString("conversation_start_popup");
        if (hologramText != null) {
            Location hologramLocation = getHologramLocation(npc.getEntity().getLocation());
            UUID playerUUID = player.getUniqueId();

            // Create the hologram at the desired location
            createHologram(playerUUID, hologramLocation, hologramText);

            // Scroll text if it's longer than the character limit
            int characterLimit = 12;
            if (hologramText.length() > characterLimit) {
                createScrollingHologram(playerUUID, hologramText, characterLimit);
            }

            // Remove the hologram after a delay
            Bukkit.getScheduler().runTaskLater(plugin, () -> removePlayerHologram(playerUUID), 200L); // 10s delay
        }
    }


    public Location getHologramLocation(Location npcLocation) {
        // The offset in front of the NPC where the hologram will appear
        double frontOffset = 0.2;
        double heightOffset = npcLocation.getY() - 0.5;

        // Get the direction vector of the NPC's location
        Vector direction = npcLocation.getDirection();

        // Normalize and scale the direction vector by the front offset
        direction.normalize().multiply(frontOffset);

        // Create the hologram location
        Location hologramLocation = npcLocation.clone().add(direction);
        hologramLocation.setY(heightOffset);

        return hologramLocation;
    }

    public void createHologram(UUID playerUUID, Location location, String text) {
        World world = location.getWorld();

        // Spawn the armor stand 10 blocks below the intended location
        Location spawnLocation = location.clone().add(0, -10, 0);

        armorStand = (ArmorStand) world.spawnEntity(spawnLocation, EntityType.ARMOR_STAND);
        armorStand.setVisible(false);
        armorStand.setGravity(false);
        armorStand.customName(Component.text(text));
        armorStand.setCustomNameVisible(true);
        armorStand.setMarker(true);

        // Move the armor stand to the intended location after a delay of 5 ticks
        Bukkit.getScheduler().runTaskLater(plugin, () -> armorStand.teleport(location.add(0, 1.2, 0)), 5L);

        allHolograms.add(armorStand);
        playerHolograms.put(playerUUID, armorStand);
    }

    /**
     * Creates a scrolling hologram that displays text in a scrolling manner.
     *
     * @param playerUUID  The UUID of the player for whom the hologram is being created.
     * @param fullText    The full text to be displayed in a scrolling fashion.
     * @param characterLimit The number of characters to display at once.
     */
    public void createScrollingHologram(UUID playerUUID, String fullText, int characterLimit) {
        ArmorStand hologram = playerHolograms.get(playerUUID);
        if (hologram == null) return;

        // Create a looped text by appending the full text to itself
        String loopedText = fullText + " " + fullText;

        BukkitRunnable scrollingTask = new BukkitRunnable() {
            int startIndex = 0;

            @Override
            public void run() {
                // Calculate endIndex, ensuring it doesn't exceed the length of loopedText
                int endIndex = startIndex + characterLimit;
                if (endIndex > loopedText.length()) {
                    endIndex = endIndex % loopedText.length();
                }

                String displayText = loopedText.substring(startIndex, Math.min(endIndex, loopedText.length()));

                // Display the substring of loopedText
                hologram.customName(Component.text(displayText));

                // Update startIndex for the next iteration
                startIndex = (startIndex + 1) % fullText.length(); // Reset after one full cycle
            }
        };

        scrollingTask.runTaskTimer(plugin, 0L, 5L);
        animationTasks.put(playerUUID, scrollingTask);
    }

    public void animateHologram() {
        String[] animations = {".", "..", "..."};

        animationTask = new BukkitRunnable() {
            @Override
            public void run() {
                armorStand.customName(Component.text(animations[animationState]));
                animationState = (animationState + 1) % animations.length;
            }
        };
        animationTask.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * Stops any ongoing animation associated with the specified player's hologram.
     *
     * @param playerUUID The UUID of the player whose hologram animation should be stopped.
     */
    public void stopAnimation(UUID playerUUID) {
        BukkitRunnable task = animationTasks.get(playerUUID);
        if (task != null) {
            task.cancel();
            animationTasks.remove(playerUUID);
        }
    }

    /**
     * Removes a hologram associated with a specific player.
     *
     * @param playerUUID The UUID of the player whose hologram should be removed.
     */
    public void removePlayerHologram(UUID playerUUID) {
        ArmorStand hologram = playerHolograms.get(playerUUID);
        if (hologram != null && hologram.isValid()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                stopAnimation(playerUUID);
                hologram.remove();
                playerHolograms.remove(playerUUID);
                allHolograms.remove(hologram);
            });
        }
    }

    // Starts a cleanup task that periodically checks for and removes expired holograms.
    private void startCleanupTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> allHolograms.removeIf(hologram -> {
            if (hologram.getTicksLived() > ArchGPTConstants.MAX_HOLOGRAM_LIFETIME) {
                hologram.remove();
                return true;
            }
            return false;
        }), 600L, 600L);
    }

}
