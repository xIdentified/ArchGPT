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

    public HologramManager(JavaPlugin plugin) {
        this.plugin = plugin;
        startCleanupTask();
    }

    // Method to show the hologram
    public void showInteractionHologram(NPC npc, Player player) {
        Location npcLocation = npc.getEntity().getLocation();

        // Calculate the location above the NPC for the hologram
        Location hologramLocation = getHologramLocation(npcLocation);

        // Create and show the hologram
        createHologram(player.getUniqueId(), hologramLocation, plugin.getConfig().getString("conversation_start_popup"));

        // Optionally, remove the hologram after a delay
        Bukkit.getScheduler().runTaskLater(plugin, () -> removePlayerHologram(player.getUniqueId()), 100L); // 100 ticks delay
    }

    public Location getHologramLocation(Location npcLocation) {
        // The offset in front of the NPC where the hologram will appear
        double frontOffset = 1.0;
        double heightOffset = npcLocation.getY() + 1.5;

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

    public void stopAnimation() {
        if (animationTask != null) {
            animationTask.cancel();
        }
    }

    public void removeHologram() {
        if (armorStand != null) {
            // Schedule the removal operation to be run on the main server thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                stopAnimation();
                armorStand.remove();
                armorStand = null;
            });
        }
    }

    //Removes player specific holograms
    public void removePlayerHologram(UUID playerUUID) {
        if (playerHolograms.containsKey(playerUUID)) {
            ArmorStand hologram = playerHolograms.get(playerUUID);
            hologram.remove();
            playerHolograms.remove(playerUUID);
            allHolograms.remove(hologram);
        }
    }

    private void startCleanupTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // Collect holograms that need to be removed
            List<ArmorStand> toRemove = new ArrayList<>();

            for (ArmorStand hologram : allHolograms) {
                if (hologram.getTicksLived() > ArchGPTConstants.MAX_HOLOGRAM_LIFETIME) {
                    hologram.remove();
                    toRemove.add(hologram);
                }
            }

            // Remove the collected holograms from the list
            allHolograms.removeAll(toRemove);
        }, 600L, 600L);  // run every 30 seconds
    }

}
