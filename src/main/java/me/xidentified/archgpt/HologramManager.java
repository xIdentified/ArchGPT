package me.xidentified.archgpt;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class HologramManager {

    private final JavaPlugin plugin;
    private ArmorStand armorStand;
    private int animationState = 0;
    private BukkitRunnable animationTask;
    private final List<ArmorStand> allHolograms = new CopyOnWriteArrayList<>();
    private final Map<UUID, ArmorStand> playerHolograms = new HashMap<>();

    public HologramManager(JavaPlugin plugin) {
        this.plugin = plugin;
        startCleanupTask();
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
        stopAnimation();
        if (armorStand != null) {
            armorStand.remove();
            armorStand = null;
        }
    }

    public void removeAllHolograms() {
        for (ArmorStand hologram : allHolograms) {
            hologram.remove();
            allHolograms.remove(hologram);
        }
        allHolograms.clear();
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
        Bukkit.getScheduler().runTaskTimer(plugin, () -> allHolograms.forEach(hologram -> {
            if (hologram.getTicksLived() > ArchGPTConstants.MAX_HOLOGRAM_LIFETIME) {
                hologram.remove();
                allHolograms.remove(hologram);
            }
        }), 600L, 600L);  // run every 30 seconds
    }

}
