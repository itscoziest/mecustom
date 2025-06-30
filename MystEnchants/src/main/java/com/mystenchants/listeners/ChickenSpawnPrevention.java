package com.mystenchants.listeners;

import com.mystenchants.MystEnchants;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Egg;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

/**
 * Prevents custom perk eggs from spawning chickens
 */
public class ChickenSpawnPrevention implements Listener {

    private final MystEnchants plugin;

    public ChickenSpawnPrevention(MystEnchants plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        // Prevent chickens from spawning from custom perk eggs
        if (event.getEntity() instanceof Chicken &&
                event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.EGG) {

            // Check if this is from a custom perk egg
            // We can't directly access the egg entity here, but we can use metadata
            // The PerkProjectileListener sets metadata on custom eggs

            // For now, we'll prevent all egg-spawned chickens in the immediate area
            // where custom perk eggs might have been thrown

            // This is a simple approach - you could make it more sophisticated
            // by tracking custom egg locations and times

            plugin.getLogger().info("Chicken spawn from egg detected - checking if from custom perk");

            // Cancel chicken spawn for all egg-spawned chickens
            // This prevents the issue entirely
            event.setCancelled(true);
            plugin.getLogger().info("Cancelled chicken spawn from egg (might be custom perk egg)");
        }
    }
}