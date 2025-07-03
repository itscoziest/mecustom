package com.mystenchants.managers;

import com.mystenchants.MystEnchants;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Enhanced StatisticManager with comprehensive tracking configuration
 * Manages player statistics tracking for enchant unlocks with full configurability
 */
public class StatisticManager {

    private final MystEnchants plugin;
    private final Map<UUID, Location> lastPlayerLocations = new HashMap<>();
    private final Map<String, Long> lastBlockPlaced = new HashMap<>();
    private final Map<UUID, Long> lastMovementTime = new HashMap<>();

    public StatisticManager(MystEnchants plugin) {
        this.plugin = plugin;
    }

    /**
     * Enhanced block mining tracking with comprehensive configuration
     */
    public void trackBlockMined(Player player, Material material) {
        // Check if tracking is enabled
        if (!plugin.getConfigManager().getBoolean("statistics.yml", "tracking.track-blocks-mined", true)) {
            return;
        }

        // Check if block should be tracked - FIXED: Use proper config method
        List<String> trackedBlocks = plugin.getConfigManager().getStatisticsConfig().getStringList("unlock-requirements.BLOCKS_MINED.track-blocks");
        if (!trackedBlocks.contains(material.name())) {
            return;
        }

        // Check game mode exclusions
        if (shouldExcludePlayer(player)) {
            return;
        }

        // Enhanced anti-farm check
        if (isBlockRecentlyPlaced(player, material)) {
            return;
        }

        // Check tool requirement if enabled
        if (plugin.getConfigManager().getBoolean("statistics.yml", "tracking.blocks.require-proper-tool", true)) {
            if (!hasProperTool(player, material)) {
                return;
            }
        }

        // Increment statistic
        plugin.getPlayerDataManager().incrementStatistic(player.getUniqueId(), "blocks_mined", 1);

        // Check milestone notifications
        checkMilestones(player, "blocks_mined");
    }

    /**
     * Enhanced player movement tracking with configurable settings
     */
    public void trackPlayerMovement(Player player) {
        if (!plugin.getConfigManager().getBoolean("statistics.yml", "tracking.track-blocks-walked", true)) {
            return;
        }

        // Check movement timeout
        UUID playerUUID = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        long movementTimeout = plugin.getConfigManager().getLong("statistics.yml", "tracking.movement.movement-timeout", 1000);

        if (lastMovementTime.containsKey(playerUUID)) {
            long timeSinceLastMovement = currentTime - lastMovementTime.get(playerUUID);
            if (timeSinceLastMovement < movementTimeout) {
                return; // Too soon since last movement
            }
        }
        lastMovementTime.put(playerUUID, currentTime);

        Location currentLocation = player.getLocation();
        Location lastLocation = lastPlayerLocations.get(playerUUID);

        if (lastLocation != null && lastLocation.getWorld().equals(currentLocation.getWorld())) {
            double distance = lastLocation.distance(currentLocation);
            double minDistance = plugin.getConfigManager().getDouble("statistics.yml", "tracking.movement.minimum-distance", 1.0);

            if (distance >= minDistance) {
                // Check movement requirements
                boolean requireOnGround = plugin.getConfigManager().getBoolean("statistics.yml", "tracking.movement.require-on-ground", true);
                boolean excludeFlying = plugin.getConfigManager().getBoolean("statistics.yml", "tracking.movement.exclude-flying", true);
                boolean excludeVehicles = plugin.getConfigManager().getBoolean("statistics.yml", "tracking.movement.exclude-vehicles", true);

                // Check conditions
                if (requireOnGround && !player.isOnGround()) return;
                if (excludeFlying && player.isFlying()) return;
                if (excludeVehicles && player.isInsideVehicle()) return;
                if (shouldExcludePlayer(player)) return;

                // Update frequency check
                int updateFrequency = plugin.getConfigManager().getInt("statistics.yml", "performance.update-frequency.blocks-walked", 5);
                if (distance >= updateFrequency) {
                    plugin.getPlayerDataManager().incrementStatistic(playerUUID, "blocks_walked", (long) distance);
                    checkMilestones(player, "blocks_walked");
                }
            }
        }

        lastPlayerLocations.put(playerUUID, currentLocation.clone());
    }

    /**
     * Enhanced wheat harvesting tracking
     */
    public void trackWheatBroken(Player player) {
        if (!plugin.getConfigManager().getBoolean("statistics.yml", "tracking.track-blocks-mined", true)) {
            return;
        }

        if (shouldExcludePlayer(player)) {
            return;
        }

        plugin.getPlayerDataManager().incrementStatistic(player.getUniqueId(), "wheat_broken", 1);
        checkMilestones(player, "wheat_broken");
    }

    /**
     * Enhanced entity kill tracking with comprehensive filtering
     */
    public void trackEntityKilled(Player player, EntityType entityType) {
        if (!plugin.getConfigManager().getBoolean("statistics.yml", "tracking.track-entities-killed", true)) {
            return;
        }

        if (shouldExcludePlayer(player)) {
            return;
        }

        // Check if this entity type should be tracked
        if (entityType == EntityType.CREEPER) {
            List<String> trackedEntities = plugin.getConfigManager().getStatisticsConfig().getStringList("unlock-requirements.CREEPERS_KILLED.track-entities");
            if (trackedEntities.contains("CREEPER")) {
                plugin.getPlayerDataManager().incrementStatistic(player.getUniqueId(), "creepers_killed", 1);
                checkMilestones(player, "creepers_killed");
            }
        }
    }

    /**
     * Enhanced iron ingot trading tracking
     */
    public void trackIronIngotTraded(Player player, int amount) {
        if (!plugin.getConfigManager().getBoolean("statistics.yml", "tracking.track-items-traded", true)) {
            return;
        }

        plugin.getPlayerDataManager().incrementStatistic(player.getUniqueId(), "iron_ingots_traded", amount);
        checkMilestones(player, "iron_ingots_traded");
    }

    /**
     * Enhanced pants crafting tracking
     */
    public void trackPantsCrafted(Player player, Material material) {
        if (!plugin.getConfigManager().getBoolean("statistics.yml", "tracking.track-items-crafted", true)) {
            return;
        }

        List<String> trackedPants = plugin.getConfigManager().getStatisticsConfig()
                .getStringList("unlock-requirements.PANTS_CRAFTED.track-crafting");

        if (trackedPants.contains(material.name())) {
            plugin.getPlayerDataManager().incrementStatistic(player.getUniqueId(), "pants_crafted", 1);
            checkMilestones(player, "pants_crafted");
        }
    }

    /**
     * Enhanced soul collection tracking
     */
    public void trackSoulCollected(Player player, int amount) {
        if (!plugin.getConfigManager().getBoolean("statistics.yml", "tracking.track-souls-collected", true)) {
            return;
        }

        plugin.getPlayerDataManager().incrementStatistic(player.getUniqueId(), "souls_collected", amount);
        checkMilestones(player, "souls_collected");
    }

    /**
     * Track block placement for anti-farm system
     */
    public void trackBlockPlaced(Player player, Material material) {
        String blockKey = player.getUniqueId() + ":" + material.name();
        lastBlockPlaced.put(blockKey, System.currentTimeMillis());
    }

    /**
     * Enhanced milestone checking with configurable notifications
     */
    private void checkMilestones(Player player, String statisticName) {
        boolean milestonesEnabled = plugin.getConfigManager().getBoolean("statistics.yml",
                "progress-display.milestone-notifications", true);

        if (!milestonesEnabled) return;

        plugin.getPlayerDataManager().getStatistic(player.getUniqueId(), statisticName)
                .thenAccept(currentValue -> {
                    List<Integer> milestonePercentages = plugin.getConfigManager().getStatisticsConfig().getIntegerList("progress-display.milestone-percentages");

                    // Check against enchant requirements to see if milestones are reached
                    checkSpecificMilestone(player, statisticName, currentValue, milestonePercentages);
                });
    }

    /**
     * Enhanced milestone checking against actual enchant requirements
     */
    private void checkSpecificMilestone(Player player, String statisticName, long currentValue, List<Integer> milestonePercentages) {
        // Check all enchants that use this statistic type
        for (com.mystenchants.enchants.CustomEnchant enchant : plugin.getEnchantManager().getAllEnchants()) {
            for (int level = 1; level <= enchant.getMaxLevel(); level++) {
                com.mystenchants.enchants.UnlockRequirement req = enchant.getUnlockRequirement(level);
                if (req != null && isMatchingStatistic(req.getType(), statisticName)) {
                    long required = req.getAmount();
                    if (required > 0) {
                        double percentage = (currentValue * 100.0) / required;

                        for (int milestonePercent : milestonePercentages) {
                            if (percentage >= milestonePercent && percentage < milestonePercent + 5) {
                                // Player just reached this milestone
                                sendMilestoneNotification(player, enchant.getDisplayName(), level, milestonePercent, currentValue, required);
                                break;
                            }
                        }

                        // Check if requirement is completed
                        if (currentValue >= required) {
                            sendCompletionNotification(player, enchant.getDisplayName(), level);
                        }
                    }
                }
            }
        }
    }

    /**
     * Send milestone notification with configurable message and effects
     */
    private void sendMilestoneNotification(Player player, String enchantName, int level, int percentage, long current, long required) {
        // Create a unique identifier to prevent spam
        String milestoneKey = player.getUniqueId().toString() + ":" + enchantName + ":" + level + ":" + percentage;

        // If this notification was already sent, do nothing.
        if (notifiedMilestones.contains(milestoneKey)) {
            return;
        }

        String message = plugin.getConfigManager().getString("statistics.yml",
                "progress-display.milestone-message",
                "&a&l[MystEnchants] &7You've reached &6{percentage}% &7progress on &6{enchant} &7level &6{level}&7!");

        message = message.replace("{percentage}", String.valueOf(percentage))
                .replace("{enchant}", enchantName)
                .replace("{level}", String.valueOf(level))
                .replace("{current}", String.valueOf(current))
                .replace("{required}", String.valueOf(required));

        player.sendMessage(com.mystenchants.utils.ColorUtils.color(message));

        // Play milestone sound
        String soundName = plugin.getConfigManager().getString("statistics.yml", "progress-display.milestone-sound", "ENTITY_PLAYER_LEVELUP");
        try {
            org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException e) {
            // Invalid sound, use default
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }

        // Mark this milestone as notified to prevent future spam
        notifiedMilestones.add(milestoneKey);
    }

    private final java.util.Set<String> notifiedMilestones = new java.util.HashSet<>();


    /**
     * Send completion notification when requirement is met
     */
    private void sendCompletionNotification(Player player, String enchantName, int level) {
        String message = plugin.getConfigManager().getString("statistics.yml",
                "progress-display.completion-message",
                "&a&l[MystEnchants] &7You can now unlock &6{enchant} &7level &6{level}&7!");

        message = message.replace("{enchant}", enchantName).replace("{level}", String.valueOf(level));
        player.sendMessage(com.mystenchants.utils.ColorUtils.color(message));

        // Play completion sound
        String soundName = plugin.getConfigManager().getString("statistics.yml", "progress-display.completion-sound", "UI_TOAST_CHALLENGE_COMPLETE");
        try {
            org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, 1.0f, 1.2f);
        } catch (IllegalArgumentException e) {
            // Invalid sound, use default
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
        }
    }

    /**
     * Check if requirement type matches statistic name
     */
    private boolean isMatchingStatistic(com.mystenchants.enchants.RequirementType type, String statisticName) {
        switch (type) {
            case BLOCKS_MINED: return "blocks_mined".equals(statisticName);
            case BLOCKS_WALKED: return "blocks_walked".equals(statisticName);
            case WHEAT_BROKEN: return "wheat_broken".equals(statisticName);
            case CREEPERS_KILLED: return "creepers_killed".equals(statisticName);
            case IRON_INGOTS: return "iron_ingots_traded".equals(statisticName);
            case PANTS_CRAFTED: return "pants_crafted".equals(statisticName);
            case SOULS: return "souls_collected".equals(statisticName);
            default: return false;
        }
    }

    /**
     * Check if player should be excluded from tracking
     */
    private boolean shouldExcludePlayer(Player player) {
        boolean excludeCreative = plugin.getConfigManager().getBoolean("statistics.yml", "tracking.blocks.exclude-creative", true);
        boolean excludeSpectator = plugin.getConfigManager().getBoolean("statistics.yml", "tracking.blocks.exclude-spectator", true);

        return (excludeCreative && player.getGameMode() == GameMode.CREATIVE) ||
                (excludeSpectator && player.getGameMode() == GameMode.SPECTATOR);
    }

    /**
     * Check if block was recently placed (anti-farm)
     */
    private boolean isBlockRecentlyPlaced(Player player, Material material) {
        boolean antiFarmEnabled = plugin.getConfigManager().getBoolean("statistics.yml",
                "unlock-requirements.BLOCKS_MINED.anti-farm-protection", true);

        if (!antiFarmEnabled) return false;

        String blockKey = player.getUniqueId() + ":" + material.name();
        long antiFarmTimer = plugin.getConfigManager().getLong("statistics.yml", "unlock-requirements.BLOCKS_MINED.anti-farm-timer", 300000);

        if (lastBlockPlaced.containsKey(blockKey)) {
            long timeSincePlaced = System.currentTimeMillis() - lastBlockPlaced.get(blockKey);
            return timeSincePlaced < antiFarmTimer;
        }

        return false;
    }

    /**
     * Check if player has proper tool for mining
     */
    private boolean hasProperTool(Player player, Material material) {
        org.bukkit.inventory.ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool == null || tool.getType() == Material.AIR) {
            return false;
        }

        // Basic tool checking (can be enhanced with more sophisticated logic)
        switch (material) {
            case STONE:
            case COBBLESTONE:
            case COAL_ORE:
            case IRON_ORE:
            case GOLD_ORE:
            case DIAMOND_ORE:
            case EMERALD_ORE:
            case LAPIS_ORE:
            case REDSTONE_ORE:
            case DEEPSLATE_COAL_ORE:
            case DEEPSLATE_IRON_ORE:
            case DEEPSLATE_GOLD_ORE:
            case DEEPSLATE_DIAMOND_ORE:
            case DEEPSLATE_EMERALD_ORE:
            case DEEPSLATE_LAPIS_ORE:
            case DEEPSLATE_REDSTONE_ORE:
                return tool.getType().name().contains("PICKAXE");
            case OAK_LOG:
            case BIRCH_LOG:
            case SPRUCE_LOG:
            case JUNGLE_LOG:
            case ACACIA_LOG:
            case DARK_OAK_LOG:
            case CHERRY_LOG:
            case MANGROVE_LOG:
                return tool.getType().name().contains("AXE");
            case DIRT:
            case GRASS_BLOCK:
            case SAND:
            case GRAVEL:
                return tool.getType().name().contains("SHOVEL");
            default:
                return true; // Allow other materials
        }
    }

    /**
     * Gets formatted progress for a statistic towards a goal
     */
    public String getFormattedProgress(long current, long required) {
        if (required <= 0) return "100%";

        double percentage = Math.min(100.0, (current * 100.0) / required);

        int barLength = plugin.getConfigManager().getInt("statistics.yml", "progress-display.progress-bar.length", 20);
        String completedChar = plugin.getConfigManager().getString("statistics.yml", "progress-display.progress-bar.completed-char", "█");
        String incompleteChar = plugin.getConfigManager().getString("statistics.yml", "progress-display.progress-bar.incomplete-char", "░");
        String format = plugin.getConfigManager().getString("statistics.yml", "progress-display.progress-bar.format", "&7[{bar}&7] &f{percentage}% &7({current}/{max})");

        String progressBar = createProgressBar(percentage, barLength, completedChar, incompleteChar);

        return format.replace("{bar}", progressBar)
                .replace("{percentage}", String.format("%.1f", percentage))
                .replace("{current}", formatLargeNumber(current))
                .replace("{max}", formatLargeNumber(required));
    }

    /**
     * Gets formatted progress with custom format
     */
    public String getFormattedProgress(long current, long required, String customFormat) {
        if (required <= 0) return "100%";

        double percentage = Math.min(100.0, (current * 100.0) / required);

        int barLength = plugin.getConfigManager().getInt("statistics.yml", "progress-display.progress-bar.length", 20);
        String completedChar = plugin.getConfigManager().getString("statistics.yml", "progress-display.progress-bar.completed-char", "█");
        String incompleteChar = plugin.getConfigManager().getString("statistics.yml", "progress-display.progress-bar.incomplete-char", "░");

        String progressBar = createProgressBar(percentage, barLength, completedChar, incompleteChar);

        return customFormat.replace("{bar}", progressBar)
                .replace("{percentage}", String.format("%.1f", percentage))
                .replace("{current}", formatLargeNumber(current))
                .replace("{max}", formatLargeNumber(required));
    }

    /**
     * Formats large numbers with K, M, B suffixes
     */
    private String formatLargeNumber(long number) {
        if (number < 1000) {
            return String.valueOf(number);
        } else if (number < 1000000) {
            return String.format("%.1fK", number / 1000.0);
        } else if (number < 1000000000) {
            return String.format("%.1fM", number / 1000000.0);
        } else {
            return String.format("%.1fB", number / 1000000000.0);
        }
    }

    /**
     * Creates a visual progress bar with configurable colors
     */
    private String createProgressBar(double percentage, int length, String completedChar, String incompleteChar) {
        int completed = (int) (length * (percentage / 100.0));
        int incomplete = length - completed;

        // Get configurable colors
        String lowColor = plugin.getConfigManager().getString("statistics.yml", "progress-display.progress-bar.colors.low", "&c");
        String mediumColor = plugin.getConfigManager().getString("statistics.yml", "progress-display.progress-bar.colors.medium", "&e");
        String highColor = plugin.getConfigManager().getString("statistics.yml", "progress-display.progress-bar.colors.high", "&a");

        StringBuilder bar = new StringBuilder();

        // Choose color based on percentage
        if (percentage < 25) {
            bar.append(lowColor);
        } else if (percentage < 75) {
            bar.append(mediumColor);
        } else {
            bar.append(highColor);
        }

        for (int i = 0; i < completed; i++) {
            bar.append(completedChar);
        }
        bar.append("&7");
        for (int i = 0; i < incomplete; i++) {
            bar.append(incompleteChar);
        }

        return bar.toString();
    }

    /**
     * Gets all statistics for a player
     */
    public CompletableFuture<Map<String, Long>> getAllStatistics(UUID playerUUID) {
        return plugin.getPlayerDataManager().getPlayerStatistics(playerUUID);
    }

    /**
     * Gets leaderboard data for a specific statistic
     */
    public CompletableFuture<java.util.List<java.util.Map.Entry<String, Long>>> getLeaderboard(String statisticName, int limit) {
        return plugin.getDatabaseManager().queryAsync(
                "SELECT pd.username, ps." + statisticName + " FROM player_data pd " +
                        "JOIN player_statistics ps ON pd.uuid = ps.uuid " +
                        "ORDER BY ps." + statisticName + " DESC LIMIT ?",
                resultSet -> {
                    java.util.List<java.util.Map.Entry<String, Long>> leaderboard = new java.util.ArrayList<>();
                    while (resultSet.next()) {
                        String username = resultSet.getString("username");
                        long value = resultSet.getLong(statisticName);
                        leaderboard.add(new java.util.AbstractMap.SimpleEntry<>(username, value));
                    }
                    return leaderboard;
                },
                limit
        );
    }

    /**
     * Reset statistics for a player (admin command)
     */
    public CompletableFuture<Void> resetPlayerStatistics(UUID playerUUID) {
        return plugin.getDatabaseManager().executeAsync(
                "UPDATE player_statistics SET " +
                        "blocks_mined = 0, blocks_walked = 0, wheat_broken = 0, " +
                        "creepers_killed = 0, iron_ingots_traded = 0, pants_crafted = 0, " +
                        "souls_collected = 0, enchants_unlocked = 0 " +
                        "WHERE uuid = ?",
                playerUUID.toString()
        );
    }

    /**
     * Set specific statistic value (admin command)
     */
    public CompletableFuture<Void> setStatistic(UUID playerUUID, String statisticName, long value) {
        return plugin.getPlayerDataManager().setStatistic(playerUUID, statisticName, value);
    }

    /**
     * Check if a statistic requirement is met for an enchant
     */
    public CompletableFuture<Boolean> isRequirementMet(UUID playerUUID, String enchantName, int level) {
        com.mystenchants.enchants.CustomEnchant enchant = plugin.getEnchantManager().getEnchant(enchantName);
        if (enchant == null) return CompletableFuture.completedFuture(false);

        com.mystenchants.enchants.UnlockRequirement requirement = enchant.getUnlockRequirement(level);
        if (requirement == null || requirement.getType() == com.mystenchants.enchants.RequirementType.NONE) {
            return CompletableFuture.completedFuture(true);
        }

        if (!requirement.getType().requiresStatistics()) {
            return CompletableFuture.completedFuture(true); // Non-statistic requirements handled elsewhere
        }

        String statisticName = getStatisticName(requirement.getType());
        return plugin.getPlayerDataManager().getStatistic(playerUUID, statisticName)
                .thenApply(current -> current >= requirement.getAmount());
    }

    /**
     * Convert requirement type to database column name
     */
    private String getStatisticName(com.mystenchants.enchants.RequirementType type) {
        switch (type) {
            case BLOCKS_MINED: return "blocks_mined";
            case BLOCKS_WALKED: return "blocks_walked";
            case WHEAT_BROKEN: return "wheat_broken";
            case CREEPERS_KILLED: return "creepers_killed";
            case IRON_INGOTS: return "iron_ingots_traded";
            case PANTS_CRAFTED: return "pants_crafted";
            case SOULS: return "souls_collected";
            default: return "blocks_mined"; // fallback
        }
    }

    /**
     * Cleans up player data when they leave
     */
    public void cleanupPlayer(Player player) {
        UUID playerUUID = player.getUniqueId();
        lastPlayerLocations.remove(playerUUID);
        lastMovementTime.remove(playerUUID);

        // Clean up any block placement tracking for this player
        lastBlockPlaced.entrySet().removeIf(entry -> entry.getKey().startsWith(playerUUID.toString()));
    }
}