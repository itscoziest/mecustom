package com.mystenchants.listeners;

import com.mystenchants.MystEnchants;
import com.mystenchants.enchants.CustomEnchant;
import com.mystenchants.utils.ColorUtils;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Handles the Backup enchant - spawns protective iron golems
 */
public class BackupEnchantListener implements Listener {

    private final MystEnchants plugin;
    private final Map<UUID, Set<IronGolem>> playerGolems = new HashMap<>();
    private final Map<UUID, UUID> golemOwners = new HashMap<>();
    private final Map<UUID, Long> lastBackupUse = new HashMap<>();
    private static final long BACKUP_COOLDOWN = 30000; // 30 seconds

    public BackupEnchantListener(MystEnchants plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;

        Player attacker = (Player) event.getDamager();
        ItemStack weapon = attacker.getInventory().getItemInMainHand();

        // Check if weapon has Backup enchant
        if (!plugin.getEnchantManager().hasCustomEnchant(weapon)) return;

        CustomEnchant enchant = plugin.getEnchantManager().getCustomEnchant(weapon);
        if (enchant == null || !enchant.getName().equals("backup")) return;

        int level = plugin.getEnchantManager().getCustomEnchantLevel(weapon);

        // Check cooldown
        UUID playerUUID = attacker.getUniqueId();
        long currentTime = System.currentTimeMillis();
        Long lastUse = lastBackupUse.get(playerUUID);

        if (lastUse != null && (currentTime - lastUse) < BACKUP_COOLDOWN) {
            long remainingSeconds = (BACKUP_COOLDOWN - (currentTime - lastUse)) / 1000;
            attacker.sendMessage(ColorUtils.color("&cBackup is on cooldown for " + remainingSeconds + " seconds!"));
            return;
        }

        // Set cooldown
        lastBackupUse.put(playerUUID, currentTime);

        // Spawn iron golems based on level
        spawnBackupGolems(attacker, level);
    }

    private void spawnBackupGolems(Player owner, int level) {
        // Get configurable values from enchants.yml
        int golemCount = plugin.getEnchantManager().getBackupGolemCount(level);
        double golemHealth = plugin.getConfigManager().getDouble("enchants.yml",
                "enchants.backup.effects.golem-health", 100.0);
        double golemDamage = plugin.getConfigManager().getDouble("enchants.yml",
                "enchants.backup.effects.golem-damage", 7.0);

        // Remove existing golems for this player
        removePlayerGolems(owner);

        Set<IronGolem> newGolems = new HashSet<>();
        Location playerLoc = owner.getLocation();

        for (int i = 0; i < golemCount; i++) {
            // Spawn golems around the player
            double angle = (2 * Math.PI * i) / golemCount;
            double x = playerLoc.getX() + 3 * Math.cos(angle);
            double z = playerLoc.getZ() + 3 * Math.sin(angle);

            Location spawnLoc = new Location(playerLoc.getWorld(), x, playerLoc.getY(), z);

            // Make sure spawn location is safe
            spawnLoc = findSafeSpawnLocation(spawnLoc);

            IronGolem golem = (IronGolem) playerLoc.getWorld().spawnEntity(spawnLoc, EntityType.IRON_GOLEM);

            // Configure the golem
            configureBackupGolem(golem, owner, golemHealth, golemDamage);

            newGolems.add(golem);
            golemOwners.put(golem.getUniqueId(), owner.getUniqueId());
        }

        playerGolems.put(owner.getUniqueId(), newGolems);

        // Send success message
        String message = plugin.getConfigManager().getString("config.yml",
                "messages.backup-activated", "&6&lBACKUP! &7Spawned {count} iron golem{s} to protect you!");
        message = message.replace("{count}", String.valueOf(golemCount))
                .replace("{s}", golemCount > 1 ? "s" : "");
        owner.sendMessage(ColorUtils.color(message));

        // Schedule golem removal after duration
        int duration = plugin.getConfigManager().getInt("enchants.yml",
                "enchants.backup.effects.golem-duration", 60); // 60 seconds default

        new BukkitRunnable() {
            @Override
            public void run() {
                removePlayerGolems(owner);
                if (owner.isOnline()) {
                    String expiredMessage = plugin.getConfigManager().getString("config.yml",
                            "messages.backup-expired", "&7Your backup golems have disappeared.");
                    owner.sendMessage(ColorUtils.color(expiredMessage));
                }
            }
        }.runTaskLater(plugin, duration * 20L);
    }

    private void configureBackupGolem(IronGolem golem, Player owner, double health, double damage) {
        // Set custom name
        String golemName = plugin.getConfigManager().getString("enchants.yml",
                "enchants.backup.effects.golem-name", "&6{player}'s Guardian");
        golemName = golemName.replace("{player}", owner.getName());
        golem.setCustomName(ColorUtils.color(golemName));
        golem.setCustomNameVisible(true);

        // Set health and damage
        golem.setMaxHealth(health);
        golem.setHealth(health);
        golem.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(damage);

        // Make it not naturally despawn
        golem.setRemoveWhenFarAway(false);
        golem.setPersistent(true);

        // DON'T set player-created to true - this prevents it from attacking players
        // golem.setPlayerCreated(true);

        // CRITICAL FIX: Make the golem aggressive immediately
        // Find the nearest enemy player and make the golem target them
        Player nearestEnemy = findNearestEnemyPlayer(owner, 16.0); // 16 block radius
        if (nearestEnemy != null) {
            // Use scheduler to set target after spawn to ensure it works
            new BukkitRunnable() {
                @Override
                public void run() {
                    golem.setTarget(nearestEnemy);
                    // Damage the golem slightly to make it aggressive (vanilla mechanic)
                    golem.damage(0.1);
                }
            }.runTaskLater(plugin, 2L);

        } else {
            // If no enemy found, make the golem patrol around the owner
            plugin.getLogger().info("No enemy players found, golem will patrol");
        }

        // Schedule a task to find targets every few seconds
        new BukkitRunnable() {
            @Override
            public void run() {
                if (golem.isDead() || !golem.isValid()) {
                    this.cancel();
                    return;
                }

                // If golem has no target, find one
                if (golem.getTarget() == null) {
                    Player enemy = findNearestEnemyPlayer(owner, 20.0);
                    if (enemy != null && !enemy.equals(owner)) {
                        golem.setTarget(enemy);
                        // Damage slightly to trigger aggressive behavior
                        golem.damage(0.1);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 40L); // Check every 2 seconds

        // Clear any initial target to owner
        if (golem.getTarget() != null && golem.getTarget().equals(owner)) {
            golem.setTarget(null);
        }
    }

    /**
     * Finds the nearest enemy player (not the owner) within range
     */
    private Player findNearestEnemyPlayer(Player owner, double range) {
        Player nearest = null;
        double nearestDistance = range;

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.equals(owner)) continue; // Skip the owner
            if (!player.getWorld().equals(owner.getWorld())) continue; // Same world only

            double distance = player.getLocation().distance(owner.getLocation());
            if (distance <= range && distance < nearestDistance) {
                nearest = player;
                nearestDistance = distance;
            }
        }

        return nearest;
    }

    private Location findSafeSpawnLocation(Location original) {
        Location safe = original.clone();

        // Check if the location is safe (not in a wall)
        while (safe.getBlock().getType().isSolid() ||
                safe.clone().add(0, 1, 0).getBlock().getType().isSolid()) {
            safe.add(0, 1, 0);

            // Prevent infinite loop
            if (safe.getY() > original.getY() + 10) {
                break;
            }
        }

        return safe;
    }

    @EventHandler
    public void onEntityTarget(EntityTargetEvent event) {
        if (!(event.getEntity() instanceof IronGolem)) return;

        IronGolem golem = (IronGolem) event.getEntity();
        UUID ownerUUID = golemOwners.get(golem.getUniqueId());

        if (ownerUUID == null) return; // Not one of our backup golems

        if (event.getTarget() instanceof Player) {
            Player target = (Player) event.getTarget();

            // ONLY prevent golem from targeting its owner, allow targeting other players
            if (target.getUniqueId().equals(ownerUUID)) {
                event.setCancelled(true);
                golem.setTarget(null);

                // Instead, find another player to target
                Player owner = plugin.getServer().getPlayer(ownerUUID);
                if (owner != null) {
                    Player enemy = findNearestEnemyPlayer(owner, 20.0);
                    if (enemy != null) {
                        // Use scheduler to set target after event
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                golem.setTarget(enemy);
                                // Damage slightly to make aggressive
                                golem.damage(0.1);
                            }
                        }.runTaskLater(plugin, 1L);
                    }
                }
            } else {
                // Allow targeting other players and trigger aggressive behavior
                // Damage the golem slightly to make it aggressive (vanilla mechanic)
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        golem.damage(0.1);
                    }
                }.runTaskLater(plugin, 1L);
                plugin.getLogger().info("Backup golem targeting enemy player: " + target.getName());
            }
        }
    }

    @EventHandler
    public void onGolemDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof IronGolem)) return;

        IronGolem golem = (IronGolem) event.getEntity();
        UUID ownerUUID = golemOwners.remove(golem.getUniqueId());

        if (ownerUUID != null) {
            Set<IronGolem> golems = playerGolems.get(ownerUUID);
            if (golems != null) {
                golems.remove(golem);

                Player owner = plugin.getServer().getPlayer(ownerUUID);
                if (owner != null && owner.isOnline()) {
                    String deathMessage = plugin.getConfigManager().getString("config.yml",
                            "messages.backup-golem-died", "&cOne of your backup golems has been destroyed!");
                    owner.sendMessage(ColorUtils.color(deathMessage));
                }
            }
        }
    }

    private void removePlayerGolems(Player owner) {
        Set<IronGolem> golems = playerGolems.remove(owner.getUniqueId());
        if (golems != null) {
            for (IronGolem golem : golems) {
                if (!golem.isDead()) {
                    golemOwners.remove(golem.getUniqueId());
                    golem.remove();
                }
            }
        }
    }

    public void cleanupPlayer(Player player) {
        removePlayerGolems(player);
        lastBackupUse.remove(player.getUniqueId());
    }

    public void cleanupAll() {
        // Clean up all golems on plugin disable
        for (Set<IronGolem> golems : playerGolems.values()) {
            for (IronGolem golem : golems) {
                if (!golem.isDead()) {
                    golem.remove();
                }
            }
        }
        playerGolems.clear();
        golemOwners.clear();
        lastBackupUse.clear();
    }
}