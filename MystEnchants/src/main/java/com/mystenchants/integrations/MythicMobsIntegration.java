package com.mystenchants.integrations;

import com.mystenchants.MystEnchants;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Handles MythicMobs integration for redemption boss fights
 */
public class MythicMobsIntegration implements Listener {

    private final MystEnchants plugin;
    private boolean mythicMobsAvailable = false;

    public MythicMobsIntegration(MystEnchants plugin) {
        this.plugin = plugin;
        this.mythicMobsAvailable = checkMythicMobsAvailable();

        if (mythicMobsAvailable) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            plugin.getLogger().info("MythicMobs integration available and ready!");
        } else {
            plugin.getLogger().info("MythicMobs integration not available.");
        }
    }

    /**
     * FIXED: Checks if MythicMobs plugin is available (regardless of config)
     */
    private boolean checkMythicMobsAvailable() {
        try {
            Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            boolean pluginExists = plugin.getServer().getPluginManager().getPlugin("MythicMobs") != null;
            if (pluginExists) {
                plugin.getLogger().info("MythicMobs plugin detected and available!");
            } else {
                plugin.getLogger().info("MythicMobs classes found but plugin not loaded.");
            }
            return pluginExists;
        } catch (ClassNotFoundException e) {
            plugin.getLogger().info("MythicMobs not installed on this server.");
            return false;
        }
    }

    /**
     * FIXED: Checks if we should use MythicMobs for this boss fight
     */
    public boolean shouldUseMythicMobs() {
        boolean configEnabled = plugin.getConfigManager().getBoolean("config.yml", "integrations.mythicmobs.enabled", false);
        boolean available = mythicMobsAvailable;

        plugin.getLogger().info("MythicMobs check - Config enabled: " + configEnabled + ", Available: " + available);

        return configEnabled && available;
    }

    /**
     * Spawns a redemption boss using MythicMobs or vanilla
     */
    public LivingEntity spawnRedemptionBoss(Location location) {
        if (shouldUseMythicMobs()) {
            plugin.getLogger().info("Attempting to spawn MythicMobs redemption boss...");
            return spawnMythicRedemptionBoss(location);
        } else {
            plugin.getLogger().info("Using vanilla redemption boss (MythicMobs disabled or unavailable)");
            return spawnVanillaRedemptionBoss(location);
        }
    }

    /**
     * Spawns a MythicMobs redemption boss
     */
    private LivingEntity spawnMythicRedemptionBoss(Location location) {
        try {
            String mobType = plugin.getConfigManager().getString("config.yml", "integrations.mythicmobs.redemption-boss.mob-type", "RedemptionBoss");
            double level = plugin.getConfigManager().getDouble("config.yml", "integrations.mythicmobs.redemption-boss.level", 1.0);

            plugin.getLogger().info("Spawning MythicMobs boss: " + mobType + " at level " + level);

            // Check if mob type exists first
            if (!MythicBukkit.inst().getMobManager().getMythicMob(mobType).isPresent()) {
                plugin.getLogger().warning("MythicMobs mob type '" + mobType + "' not found! Available mobs: " +
                        MythicBukkit.inst().getMobManager().getMobNames());
                plugin.getLogger().warning("Falling back to vanilla boss...");
                return spawnVanillaRedemptionBoss(location);
            }

            // Spawn the MythicMob
            ActiveMob mythicMob = MythicBukkit.inst().getMobManager().spawnMob(mobType, location, level);

            if (mythicMob != null && mythicMob.getEntity() != null && mythicMob.getEntity().getBukkitEntity() instanceof LivingEntity) {
                LivingEntity boss = (LivingEntity) mythicMob.getEntity().getBukkitEntity();

                // Configure boss properties
                configureMythicBoss(boss, mythicMob);

                plugin.getLogger().info("Successfully spawned MythicMobs redemption boss: " + mobType);
                return boss;
            } else {
                plugin.getLogger().warning("Failed to spawn MythicMobs boss - mob spawned but entity is null or invalid!");
                return spawnVanillaRedemptionBoss(location);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error spawning MythicMobs boss: " + e.getMessage());
            e.printStackTrace();
            plugin.getLogger().info("Falling back to vanilla boss...");
            return spawnVanillaRedemptionBoss(location);
        }
    }

    /**
     * Configures the MythicMobs boss with additional properties
     */
    private void configureMythicBoss(LivingEntity boss, ActiveMob mythicMob) {
        // Set custom name from config if not already set by MythicMobs
        if (boss.getCustomName() == null || boss.getCustomName().isEmpty()) {
            String bossName = plugin.getConfigManager().getString("config.yml", "integrations.mythicmobs.redemption-boss.display-name", "&4&lMythic Redemption Boss");
            boss.setCustomName(com.mystenchants.utils.ColorUtils.color(bossName));
        }
        boss.setCustomNameVisible(true);

        // Prevent boss from being removed
        boss.setRemoveWhenFarAway(false);

        // Additional boss configuration
        boolean preventDespawn = plugin.getConfigManager().getBoolean("config.yml", "integrations.mythicmobs.redemption-boss.prevent-despawn", true);
        if (preventDespawn) {
            boss.setPersistent(true);
        }

        // Set boss bar if enabled
        boolean showBossBar = plugin.getConfigManager().getBoolean("config.yml", "integrations.mythicmobs.redemption-boss.show-boss-bar", true);
        if (showBossBar) {
            // MythicMobs handles boss bars automatically if configured in the mob file
            plugin.getLogger().info("Boss bar enabled - make sure to configure BossBar in your MythicMobs mob file");
        }

        plugin.getLogger().info("MythicMobs boss configured: " + boss.getName() + " (Health: " + boss.getHealth() + "/" + boss.getMaxHealth() + ")");
    }

    /**
     * Spawns a vanilla redemption boss (fallback)
     */
    private LivingEntity spawnVanillaRedemptionBoss(Location location) {
        // Use the existing vanilla boss spawning logic from RedemptionManager
        return plugin.getRedemptionManager().spawnVanillaBoss(location);
    }

    /**
     * Handles MythicMobs boss death events
     */
    @EventHandler
    public void onMythicMobDeath(MythicMobDeathEvent event) {
        if (!mythicMobsAvailable || !shouldUseMythicMobs()) return;

        String mobType = event.getMobType().getInternalName();
        String expectedBossType = plugin.getConfigManager().getString("config.yml", "integrations.mythicmobs.redemption-boss.mob-type", "RedemptionBoss");

        plugin.getLogger().info("MythicMobs death event: " + mobType + " (expected: " + expectedBossType + ")");

        // Check if this is our redemption boss
        if (mobType.equals(expectedBossType)) {
            plugin.getLogger().info("MythicMobs redemption boss defeated!");

            // Get the killer if it's a player
            if (event.getKiller() instanceof Player) {
                Player killer = (Player) event.getKiller();

                // Check if this player is currently in a redemption fight
                if (plugin.getRedemptionManager().getCurrentFighter() != null &&
                        plugin.getRedemptionManager().getCurrentFighter().equals(killer)) {

                    // Handle redemption completion
                    plugin.getRedemptionManager().handleMythicBossDefeat(killer);
                } else {
                    plugin.getLogger().info("Boss killed by " + killer.getName() + " but they are not the current fighter");
                }
            } else {
                plugin.getLogger().info("Boss killed but killer is not a player");
            }
        }
    }

    /**
     * Checks if MythicMobs integration is enabled and available
     */
    public boolean isMythicMobsEnabled() {
        return shouldUseMythicMobs();
    }

    /**
     * Gets information about the current boss configuration
     */
    public String getBossInfo() {
        if (shouldUseMythicMobs()) {
            String mobType = plugin.getConfigManager().getString("config.yml", "integrations.mythicmobs.redemption-boss.mob-type", "RedemptionBoss");
            double level = plugin.getConfigManager().getDouble("config.yml", "integrations.mythicmobs.redemption-boss.level", 1.0);
            return "MythicMobs: " + mobType + " (Level " + level + ")";
        } else {
            return "Vanilla: " + plugin.getConfigManager().getString("config.yml", "boss-fight.boss-type", "ZOMBIE");
        }
    }

    /**
     * Validates that the configured MythicMobs boss exists
     */
    public boolean validateMythicBoss() {
        if (!shouldUseMythicMobs()) return true;

        try {
            String mobType = plugin.getConfigManager().getString("config.yml", "integrations.mythicmobs.redemption-boss.mob-type", "RedemptionBoss");
            boolean exists = MythicBukkit.inst().getMobManager().getMythicMob(mobType).isPresent();

            if (!exists) {
                plugin.getLogger().warning("MythicMobs boss '" + mobType + "' does not exist!");
                plugin.getLogger().info("Available MythicMobs: " + MythicBukkit.inst().getMobManager().getMobNames());
            }

            return exists;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to validate MythicMobs boss: " + e.getMessage());
            return false;
        }
    }

    /**
     * Lists available MythicMobs for debugging
     */
    public void listAvailableMobs() {
        if (!mythicMobsAvailable) {
            plugin.getLogger().info("MythicMobs not available - cannot list mobs");
            return;
        }

        try {
            plugin.getLogger().info("Available MythicMobs: " + MythicBukkit.inst().getMobManager().getMobNames());
        } catch (Exception e) {
            plugin.getLogger().warning("Error listing MythicMobs: " + e.getMessage());
        }
    }
}