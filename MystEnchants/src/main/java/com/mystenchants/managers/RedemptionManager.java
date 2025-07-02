package com.mystenchants.managers;

import com.mystenchants.MystEnchants;
import com.mystenchants.enchants.CustomEnchant;
import com.mystenchants.utils.ColorUtils;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
/**
 * ENHANCED: Manages redemption boss fights with position saving and victory celebration
 */
public class RedemptionManager {

    private final MystEnchants plugin;
    private Location bossSpawnPoint;
    private Location playerSpawnPoint;
    private Location fighterOriginalLocation; // ADDED: Store original location
    private Player currentFighter;
    private LivingEntity boss;
    private final Set<Player> spectators = new HashSet<>();
    private BukkitTask fightTask;
    private BukkitTask celebrationTask; // ADDED: For victory celebration
    private long fightStartTime;

    public RedemptionManager(MystEnchants plugin) {
        this.plugin = plugin;
        // Load spawn points after a short delay to ensure database is ready
        Bukkit.getScheduler().runTaskLater(plugin, this::loadSpawnPoints, 20L);
    }

    /**
     * ADDED: Check if an entity is the redemption boss
     */
    public boolean isRedemptionBoss(Entity entity) {
        return boss != null && entity.equals(boss);
    }

    /**
     * Loads spawn points from database
     */
    private void loadSpawnPoints() {
        plugin.getDatabaseManager().queryAsync(
                "SELECT * FROM redemption_data ORDER BY id LIMIT 1",
                resultSet -> {
                    if (resultSet.next()) {
                        try {
                            double bossX = resultSet.getDouble("boss_spawn_x");
                            double bossY = resultSet.getDouble("boss_spawn_y");
                            double bossZ = resultSet.getDouble("boss_spawn_z");
                            String bossWorld = resultSet.getString("boss_spawn_world");

                            double playerX = resultSet.getDouble("player_spawn_x");
                            double playerY = resultSet.getDouble("player_spawn_y");
                            double playerZ = resultSet.getDouble("player_spawn_z");
                            String playerWorld = resultSet.getString("player_spawn_world");

                            if (bossWorld != null && Bukkit.getWorld(bossWorld) != null) {
                                bossSpawnPoint = new Location(Bukkit.getWorld(bossWorld), bossX, bossY, bossZ);
                                plugin.getLogger().info("Loaded boss spawn point: " + bossSpawnPoint);
                            }

                            if (playerWorld != null && Bukkit.getWorld(playerWorld) != null) {
                                playerSpawnPoint = new Location(Bukkit.getWorld(playerWorld), playerX, playerY, playerZ);
                                plugin.getLogger().info("Loaded player spawn point: " + playerSpawnPoint);
                            }
                        } catch (Exception e) {
                            plugin.getLogger().warning("Error loading spawn points: " + e.getMessage());
                        }
                    } else {
                        plugin.getLogger().info("No redemption spawn points found in database");
                    }
                    return null;
                }
        ).exceptionally(throwable -> {
            plugin.getLogger().warning("Failed to load redemption spawn points: " + throwable.getMessage());
            return null;
        });
    }

    /**
     * Sets the boss spawn point
     */
    public CompletableFuture<Void> setBossSpawnPoint(Location location) {
        this.bossSpawnPoint = location;

        String sql;
        Object[] params;

        if (plugin.getDatabaseManager().isMySQL()) {
            sql = "INSERT INTO redemption_data (id, boss_spawn_x, boss_spawn_y, boss_spawn_z, boss_spawn_world) " +
                    "VALUES (1, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE boss_spawn_x = ?, boss_spawn_y = ?, boss_spawn_z = ?, boss_spawn_world = ?";
            params = new Object[]{
                    location.getX(), location.getY(), location.getZ(), location.getWorld().getName(),
                    location.getX(), location.getY(), location.getZ(), location.getWorld().getName()
            };
        } else {
            // For SQLite, we need to handle the UPSERT differently
            sql = "INSERT OR REPLACE INTO redemption_data (id, boss_spawn_x, boss_spawn_y, boss_spawn_z, boss_spawn_world, " +
                    "player_spawn_x, player_spawn_y, player_spawn_z, player_spawn_world, current_fighter, fight_start_time) " +
                    "VALUES (1, ?, ?, ?, ?, " +
                    "COALESCE((SELECT player_spawn_x FROM redemption_data WHERE id = 1), 0), " +
                    "COALESCE((SELECT player_spawn_y FROM redemption_data WHERE id = 1), 0), " +
                    "COALESCE((SELECT player_spawn_z FROM redemption_data WHERE id = 1), 0), " +
                    "COALESCE((SELECT player_spawn_world FROM redemption_data WHERE id = 1), 'world'), " +
                    "NULL, 0)";
            params = new Object[]{location.getX(), location.getY(), location.getZ(), location.getWorld().getName()};
        }

        return plugin.getDatabaseManager().executeAsync(sql, params);
    }

    /**
     * Sets the player spawn point
     */
    public CompletableFuture<Void> setPlayerSpawnPoint(Location location) {
        this.playerSpawnPoint = location;

        String sql;
        Object[] params;

        if (plugin.getDatabaseManager().isMySQL()) {
            sql = "INSERT INTO redemption_data (id, player_spawn_x, player_spawn_y, player_spawn_z, player_spawn_world) " +
                    "VALUES (1, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE player_spawn_x = ?, player_spawn_y = ?, player_spawn_z = ?, player_spawn_world = ?";
            params = new Object[]{
                    location.getX(), location.getY(), location.getZ(), location.getWorld().getName(),
                    location.getX(), location.getY(), location.getZ(), location.getWorld().getName()
            };
        } else {
            // For SQLite, we need to handle the UPSERT differently
            sql = "INSERT OR REPLACE INTO redemption_data (id, boss_spawn_x, boss_spawn_y, boss_spawn_z, boss_spawn_world, " +
                    "player_spawn_x, player_spawn_y, player_spawn_z, player_spawn_world, current_fighter, fight_start_time) " +
                    "VALUES (1, " +
                    "COALESCE((SELECT boss_spawn_x FROM redemption_data WHERE id = 1), 0), " +
                    "COALESCE((SELECT boss_spawn_y FROM redemption_data WHERE id = 1), 0), " +
                    "COALESCE((SELECT boss_spawn_z FROM redemption_data WHERE id = 1), 0), " +
                    "COALESCE((SELECT boss_spawn_world FROM redemption_data WHERE id = 1), 'world'), " +
                    "?, ?, ?, ?, NULL, 0)";
            params = new Object[]{location.getX(), location.getY(), location.getZ(), location.getWorld().getName()};
        }

        return plugin.getDatabaseManager().executeAsync(sql, params);
    }

    /**
     * Checks if spawn points are set
     */
    public boolean areSpawnPointsSet() {
        boolean isSet = bossSpawnPoint != null && playerSpawnPoint != null;
        if (!isSet) {
            plugin.getLogger().info("Spawn points check - Boss: " + (bossSpawnPoint != null) + ", Player: " + (playerSpawnPoint != null));
        }
        return isSet;
    }

    /**
     * Checks if a redemption fight is active
     */
    public boolean isRedemptionActive() {
        return currentFighter != null && boss != null && !boss.isDead();
    }

    /**
     * ENHANCED: Starts redemption with inventory space checking
     */
    public void startRedemption(Player player) {
        if (isRedemptionActive()) {
            player.sendMessage(ColorUtils.color("&cA redemption is already active!"));
            return;
        }

        if (!areSpawnPointsSet()) {
            player.sendMessage(ColorUtils.color("&cSpawn points are not set!"));
            return;
        }

        // ADDED: Check inventory space for rewards
        if (!hasInventorySpaceForRewards(player)) {
            String message = plugin.getConfigManager().getString("config.yml", "messages.redemption-inventory-full",
                    "&cYour inventory is full! You need at least {slots} empty slots for redemption rewards!");
            int requiredSlots = plugin.getConfigManager().getInt("config.yml", "boss-fight.required-inventory-slots", 2);
            message = message.replace("{slots}", String.valueOf(requiredSlots));
            player.sendMessage(ColorUtils.color(message));

            player.sendMessage(ColorUtils.color("&7Redemption rewards include:"));
            player.sendMessage(ColorUtils.color("&7• &dRedemption Enchant"));
            player.sendMessage(ColorUtils.color("&7• Other potential boss drops"));
            return;
        }

        currentFighter = player;
        fightStartTime = System.currentTimeMillis();

        // ADDED: Save player's original location
        fighterOriginalLocation = player.getLocation().clone();

        // Clear player inventory (they lose items if they die)
        player.getInventory().clear();

        // Teleport player to spawn point
        player.teleport(playerSpawnPoint);

        // Spawn boss
        spawnBoss();

        // Start fight monitoring task
        startFightTask();

        // Broadcast start message
        String startMessage = plugin.getConfigManager().getString("config.yml", "messages.redemption-started", "&6{player} &ahas started a redemption boss fight!");
        startMessage = startMessage.replace("{player}", player.getName());
        Bukkit.broadcastMessage(ColorUtils.color(startMessage));

        // Save fight data
        plugin.getDatabaseManager().executeAsync(
                "UPDATE redemption_data SET current_fighter = ?, fight_start_time = ? WHERE id = 1",
                player.getUniqueId().toString(), fightStartTime
        );
    }



    /**
     * ADDED: Checks if player has enough inventory space for redemption rewards
     */
    private boolean hasInventorySpaceForRewards(Player player) {
        int requiredSlots = plugin.getConfigManager().getInt("config.yml", "boss-fight.required-inventory-slots", 2);
        int emptySlots = 0;

        for (int i = 0; i < 36; i++) { // Check main inventory (not armor/offhand)
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.getType().isAir()) {
                emptySlots++;
            }
        }

        return emptySlots >= requiredSlots;
    }

    /**
     * Spawns the redemption boss (now supports MythicMobs)
     */
    private void spawnBoss() {
        // Check if MythicMobs integration is available and enabled
        if (plugin.getMythicBossFightManager() != null) {
            boss = plugin.getMythicBossFightManager().startBossFight(bossSpawnPoint, currentFighter);
        } else {
            boss = spawnVanillaBoss(bossSpawnPoint);
        }
    }

    /**
     * Spawns a vanilla boss (for fallback or when MythicMobs is disabled)
     */
    public LivingEntity spawnVanillaBoss(Location location) {
        String bossTypeStr = plugin.getConfigManager().getString("config.yml", "boss-fight.boss-type", "ZOMBIE");
        double bossHealth = plugin.getConfigManager().getDouble("config.yml", "boss-fight.boss-health", 1000.0);
        String bossName = plugin.getConfigManager().getString("config.yml", "boss-fight.boss-name", "&4&lRedemption Boss");

        try {
            EntityType bossType = EntityType.valueOf(bossTypeStr);

            // Use a different boss type if Wither causes issues
            if (bossType == EntityType.WITHER) {
                // Use ZOMBIE instead of Wither to avoid destruction
                bossType = EntityType.ZOMBIE;
                plugin.getLogger().info("Using ZOMBIE instead of WITHER for redemption boss to prevent world damage");
            }

            LivingEntity boss = (LivingEntity) location.getWorld().spawnEntity(location, bossType);
            boss.setMaxHealth(bossHealth);
            boss.setHealth(bossHealth);
            boss.setCustomName(ColorUtils.color(bossName));
            boss.setCustomNameVisible(true);
            boss.setRemoveWhenFarAway(false);

            // Make the boss more challenging
            if (bossType == EntityType.ZOMBIE) {
                org.bukkit.entity.Zombie zombie = (org.bukkit.entity.Zombie) boss;
                zombie.setAdult();
                zombie.setBaby(false);
                zombie.setCanPickupItems(false);
                zombie.setShouldBurnInDay(false);

                // Give the zombie powerful equipment
                zombie.getEquipment().setHelmet(new org.bukkit.inventory.ItemStack(Material.DIAMOND_HELMET));
                zombie.getEquipment().setChestplate(new org.bukkit.inventory.ItemStack(Material.DIAMOND_CHESTPLATE));
                zombie.getEquipment().setLeggings(new org.bukkit.inventory.ItemStack(Material.DIAMOND_LEGGINGS));
                zombie.getEquipment().setBoots(new org.bukkit.inventory.ItemStack(Material.DIAMOND_BOOTS));
                zombie.getEquipment().setItemInMainHand(new org.bukkit.inventory.ItemStack(Material.DIAMOND_SWORD));

                // Prevent equipment from dropping
                zombie.getEquipment().setHelmetDropChance(0.0f);
                zombie.getEquipment().setChestplateDropChance(0.0f);
                zombie.getEquipment().setLeggingsDropChance(0.0f);
                zombie.getEquipment().setBootsDropChance(0.0f);
                zombie.getEquipment().setItemInMainHandDropChance(0.0f);
            }

            // Add attributes to make boss stronger
            boss.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(10.0);
            boss.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.35);
            boss.getAttribute(org.bukkit.attribute.Attribute.GENERIC_KNOCKBACK_RESISTANCE).setBaseValue(1.0);

            plugin.getLogger().info("Vanilla redemption boss spawned: " + bossType + " with " + bossHealth + " health");
            return boss;

        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid boss type: " + bossTypeStr + ", using ZOMBIE instead");
            LivingEntity fallbackBoss = (LivingEntity) location.getWorld().spawnEntity(location, EntityType.ZOMBIE);
            fallbackBoss.setMaxHealth(bossHealth);
            fallbackBoss.setHealth(bossHealth);
            fallbackBoss.setCustomName(ColorUtils.color(bossName));
            fallbackBoss.setCustomNameVisible(true);
            fallbackBoss.setRemoveWhenFarAway(false);
            return fallbackBoss;
        }
    }

    /**
     * Starts the fight monitoring task
     */
    private void startFightTask() {
        int maxDuration = plugin.getConfigManager().getInt("config.yml", "boss-fight.max-fight-duration", 600);

        fightTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // Check if fight should end
            if (!isRedemptionActive()) {
                endFightTask();
                return;
            }

            // Check timeout
            long elapsed = (System.currentTimeMillis() - fightStartTime) / 1000;
            if (elapsed >= maxDuration) {
                endRedemption(false, "Timeout");
                return;
            }

            // Check if player is too far away
            if (currentFighter != null && playerSpawnPoint != null) {
                double distance = currentFighter.getLocation().distance(playerSpawnPoint);
                int arenaRadius = plugin.getConfigManager().getInt("config.yml", "boss-fight.arena-radius", 20);

                if (distance > arenaRadius) {
                    // Teleport player back or end fight
                    currentFighter.teleport(playerSpawnPoint);
                    currentFighter.sendMessage(ColorUtils.color("&cYou cannot leave the arena!"));
                }
            }

            // Check if boss is dead
            if (boss != null && boss.isDead()) {
                // Check if this was a MythicMobs boss
                if (plugin.getMythicBossFightManager() != null) {
                    plugin.getMythicBossFightManager().handleBossDefeat(currentFighter, false);
                } else {
                    endRedemption(true, "Boss defeated");
                }
            }

        }, 0L, 20L); // Run every second
    }

    /**
     * ENHANCED: Ends the redemption fight with victory celebration and dye reward
     */
    public void endRedemption(boolean success, String reason) {
        if (currentFighter == null) return;

        Player fighter = currentFighter;

        if (success) {
            // Player won - give redemption enchant dye
            plugin.getPlayerDataManager().unlockEnchant(fighter.getUniqueId(), "redemption", 1)
                    .thenRun(() -> {
                        // Create redemption enchant dye
                        CustomEnchant redemptionEnchant = plugin.getEnchantManager().getEnchant("redemption");
                        if (redemptionEnchant != null) {
                            ItemStack redemptionDye = plugin.getEnchantManager().createEnchantDye(redemptionEnchant, 1);

                            // Try to give the dye to player
                            HashMap<Integer, ItemStack> remaining = fighter.getInventory().addItem(redemptionDye);

                            if (!remaining.isEmpty()) {
                                // Inventory was full, drop the item
                                for (ItemStack item : remaining.values()) {
                                    fighter.getWorld().dropItemNaturally(fighter.getLocation(), item);
                                }
                                fighter.sendMessage(ColorUtils.color("&6Your inventory was full! Redemption Dye dropped on the ground."));
                            }
                        }
                    });

            // FIXED: Give proper soul rewards for redemption boss
            int redemptionSouls = plugin.getConfigManager().getInt("config.yml", "integrations.mythicmobs.redemption-boss.extra-rewards.souls", 50);
            plugin.getSoulManager().addSouls(fighter.getUniqueId(), redemptionSouls);

            // FIXED: Give extra EXP
            int extraExp = plugin.getConfigManager().getInt("config.yml", "integrations.mythicmobs.redemption-boss.extra-rewards.exp", 100);
            fighter.giveExp(extraExp);

            String successMessage = plugin.getConfigManager().getString("config.yml", "messages.redemption-completed", "&6{player} &ahas completed the redemption boss fight!");
            successMessage = successMessage.replace("{player}", fighter.getName());
            Bukkit.broadcastMessage(ColorUtils.color(successMessage));

            fighter.sendMessage(ColorUtils.color("&a&lCongratulations! You have unlocked the Redemption enchant!"));
            fighter.sendMessage(ColorUtils.color("&d&lYou received a Redemption Dye! Apply it to your gear."));
            fighter.sendMessage(ColorUtils.color("&6+50 bonus souls for defeating the redemption boss!"));
            fighter.sendMessage(ColorUtils.color("&a+100 bonus EXP for defeating the redemption boss!"));

            // ADDED: Start victory celebration
            startVictoryCelebration(fighter);

        } else {
            // Player failed - teleport back immediately
            String failMessage = plugin.getConfigManager().getString("config.yml", "messages.redemption-failed", "&6{player} &afailed the redemption boss fight!");
            failMessage = failMessage.replace("{player}", fighter.getName());
            Bukkit.broadcastMessage(ColorUtils.color(failMessage));

            // Teleport back to original location
            if (fighterOriginalLocation != null) {
                fighter.teleport(fighterOriginalLocation);
                fighter.sendMessage(ColorUtils.color("&7You have been teleported back to your original location."));
            }
        }

        // Set cooldown
        long cooldownDuration = plugin.getConfigManager().getInt("config.yml", "cooldowns.redemption", 604800) * 1000L;
        long cooldownEnd = System.currentTimeMillis() + cooldownDuration;
        plugin.getPlayerDataManager().setRedemptionCooldown(fighter.getUniqueId(), cooldownEnd);

        // Clean up (but don't teleport if victory celebration is running)
        if (!success) {
            cleanup();
        }
    }

    /**
     * ADDED: Victory celebration with countdown and fireworks
     */
    private void startVictoryCelebration(Player fighter) {
        final int[] countdown = {10}; // 10 seconds countdown

        celebrationTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!fighter.isOnline()) {
                celebrationTask.cancel();
                cleanup();
                return;
            }

            if (countdown[0] > 0) {
                // Show title with countdown
                fighter.sendTitle(
                        ColorUtils.color("&6&lVICTORY!"),
                        ColorUtils.color("&aTeleporting back in &f" + countdown[0] + " &aseconds"),
                        10, 20, 10
                );

                // Play sound
                fighter.playSound(fighter.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f + (countdown[0] * 0.1f));

                // Spawn fireworks
                spawnFirework(fighter.getLocation());

                countdown[0]--;
            } else {
                // Countdown finished - teleport back
                celebrationTask.cancel();

                fighter.sendTitle(
                        ColorUtils.color("&6&lCongratulations!"),
                        ColorUtils.color("&aRedemption enchant unlocked!"),
                        10, 40, 10
                );

                // Final firework burst
                for (int i = 0; i < 5; i++) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> spawnFirework(fighter.getLocation()), i * 4L);
                }

                // Teleport back to original location after final fireworks
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (fighterOriginalLocation != null && fighter.isOnline()) {
                        fighter.teleport(fighterOriginalLocation);
                        fighter.sendMessage(ColorUtils.color("&7You have been teleported back to your original location."));
                    }
                    cleanup();
                }, 20L);
            }
        }, 0L, 20L); // Run every second
    }

    /**
     * ADDED: Spawn firework at location
     */
    private void spawnFirework(Location location) {
        org.bukkit.entity.Firework firework = location.getWorld().spawn(location.clone().add(0, 1, 0), org.bukkit.entity.Firework.class);
        org.bukkit.inventory.meta.FireworkMeta meta = firework.getFireworkMeta();

        // Random colors
        Color[] colors = {Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW, Color.PURPLE, Color.ORANGE};
        Color color1 = colors[(int) (Math.random() * colors.length)];
        Color color2 = colors[(int) (Math.random() * colors.length)];

        org.bukkit.FireworkEffect effect = org.bukkit.FireworkEffect.builder()
                .withColor(color1, color2)
                .with(org.bukkit.FireworkEffect.Type.BALL_LARGE)
                .withFlicker()
                .withTrail()
                .build();

        meta.addEffect(effect);
        meta.setPower(1);
        firework.setFireworkMeta(meta);
    }

    /**
     * ENHANCED: Handles player death/disconnect during fight
     */
    public void handlePlayerDeath(Player player) {
        if (currentFighter != null && currentFighter.equals(player)) {
            // Player died - teleport back to original location after respawn
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (fighterOriginalLocation != null && player.isOnline()) {
                    player.teleport(fighterOriginalLocation);
                    player.sendMessage(ColorUtils.color("&7You have been teleported back to your original location."));
                }
            }, 40L); // 2 seconds after respawn

            endRedemption(false, "Player died");
        }
    }

    /**
     * ENHANCED: Handles player disconnect during fight
     */
    public void handlePlayerDisconnect(Player player) {
        if (currentFighter != null && currentFighter.equals(player)) {
            // Store the original location for when they reconnect
            if (fighterOriginalLocation != null) {
                // We could save this to database for when they reconnect, but for now just end the fight
                plugin.getLogger().info("Player " + player.getName() + " disconnected during redemption fight");
            }
            endRedemption(false, "Player disconnected");
        }
    }

    /**
     * Handles MythicMobs boss defeat
     */
    public void handleMythicBossDefeat(Player fighter) {
        if (currentFighter != null && currentFighter.equals(fighter)) {
            plugin.getMythicBossFightManager().handleBossDefeat(fighter, true);
        }
    }

    /**
     * Adds a spectator to the fight
     */
    public void addSpectator(Player player) {
        if (!isRedemptionActive()) {
            player.sendMessage(ColorUtils.color("&cNo redemption fight is currently active!"));
            return;
        }

        spectators.add(player);
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(bossSpawnPoint.clone().add(0, 10, 0));

        String message = plugin.getConfigManager().getString("config.yml", "messages.redemption-spectate-join", "&aYou are now spectating the redemption fight!");
        player.sendMessage(ColorUtils.color(message));
    }

    /**
     * Removes a spectator from the fight
     */
    public void removeSpectator(Player player) {
        if (spectators.remove(player)) {
            player.setGameMode(GameMode.SURVIVAL);

            String message = plugin.getConfigManager().getString("config.yml", "messages.redemption-spectate-leave", "&aYou are no longer spectating the redemption fight!");
            player.sendMessage(ColorUtils.color(message));
        }
    }

    /**
     * Force ends the redemption fight (admin command)
     */
    public void forceEndRedemption() {
        if (isRedemptionActive()) {
            // Teleport fighter back to original location
            if (currentFighter != null && fighterOriginalLocation != null && currentFighter.isOnline()) {
                currentFighter.teleport(fighterOriginalLocation);
                currentFighter.sendMessage(ColorUtils.color("&7You have been teleported back to your original location."));
            }
            endRedemption(false, "Force ended by admin");
        }
    }

    /**
     * Cleans up the fight
     */
    public void cleanup() {
        // Cancel celebration task if running
        if (celebrationTask != null) {
            celebrationTask.cancel();
            celebrationTask = null;
        }

        // Remove boss
        if (boss != null && !boss.isDead()) {
            boss.remove();
        }
        boss = null;

        // Reset spectators
        for (Player spectator : spectators) {
            removeSpectator(spectator);
        }
        spectators.clear();

        // End task
        endFightTask();

        // Clear fighter and original location
        currentFighter = null;
        fighterOriginalLocation = null;

        // Clear database
        plugin.getDatabaseManager().executeAsync(
                "UPDATE redemption_data SET current_fighter = NULL, fight_start_time = 0 WHERE id = 1"
        );
    }

    /**
     * Ends the fight monitoring task
     */
    private void endFightTask() {
        if (fightTask != null) {
            fightTask.cancel();
            fightTask = null;
        }
    }

    /**
     * Gets the current fighter
     */
    public Player getCurrentFighter() {
        return currentFighter;
    }

    /**
     * Gets spectators
     */
    public Set<Player> getSpectators() {
        return new HashSet<>(spectators);
    }

    /**
     * Gets the boss spawn point
     */
    public Location getBossSpawnPoint() {
        return bossSpawnPoint;
    }

    /**
     * Gets the player spawn point
     */
    public Location getPlayerSpawnPoint() {
        return playerSpawnPoint;
    }
}