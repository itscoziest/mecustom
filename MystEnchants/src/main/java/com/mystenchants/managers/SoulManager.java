package com.mystenchants.managers;

import com.mystenchants.MystEnchants;
import com.mystenchants.utils.ColorUtils;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Enhanced SoulManager with configurable soul rewards
 * Manages soul currency for players with full configurability
 */
public class SoulManager {

    private final MystEnchants plugin;

    public SoulManager(MystEnchants plugin) {
        this.plugin = plugin;
    }

    /**
     * Gets a player's soul count
     */
    public CompletableFuture<Long> getSouls(UUID playerUUID) {
        return plugin.getDatabaseManager().queryAsync(
                "SELECT souls FROM player_data WHERE uuid = ?",
                resultSet -> {
                    if (resultSet.next()) {
                        return resultSet.getLong("souls");
                    }
                    return 0L;
                },
                playerUUID.toString()
        );
    }

    /**
     * Sets a player's soul count
     */
    public CompletableFuture<Void> setSouls(UUID playerUUID, long souls) {
        if (souls < 0) souls = 0;

        return plugin.getDatabaseManager().executeAsync(
                "UPDATE player_data SET souls = ? WHERE uuid = ?",
                souls, playerUUID.toString()
        );
    }

    /**
     * Adds souls to a player
     */
    public CompletableFuture<Void> addSouls(UUID playerUUID, long amount) {
        if (amount <= 0) return CompletableFuture.completedFuture(null);

        return getSouls(playerUUID).thenCompose(currentSouls ->
                setSouls(playerUUID, currentSouls + amount)
        );
    }

    /**
     * Removes souls from a player
     */
    public CompletableFuture<Boolean> removeSouls(UUID playerUUID, long amount) {
        if (amount <= 0) return CompletableFuture.completedFuture(true);

        return getSouls(playerUUID).thenCompose(currentSouls -> {
            if (currentSouls >= amount) {
                return setSouls(playerUUID, currentSouls - amount)
                        .thenApply(v -> true);
            } else {
                return CompletableFuture.completedFuture(false);
            }
        });
    }

    /**
     * Checks if a player has enough souls
     */
    public CompletableFuture<Boolean> hasSouls(UUID playerUUID, long amount) {
        return getSouls(playerUUID).thenApply(souls -> souls >= amount);
    }

    /**
     * Enhanced soul rewards - configurable player kill rewards
     */
    public void handlePlayerKill(Player killer) {
        // Get configurable soul reward for player kills
        int soulsPerPlayerKill = plugin.getConfigManager().getInt("perks.yml", "shop.souls-per-player-kill", 5);

        addSouls(killer.getUniqueId(), soulsPerPlayerKill).thenRun(() -> {
            String message = plugin.getConfigManager().getString("config.yml",
                    "messages.souls-received", "&aYou received &6{amount} &asouls!");
            message = message.replace("{amount}", String.valueOf(soulsPerPlayerKill));
            killer.sendMessage(ColorUtils.color(message));
        });

        // Track soul collection statistic
        plugin.getStatisticManager().trackSoulCollected(killer, soulsPerPlayerKill);
    }

    /**
     * Enhanced soul rewards - configurable mob kill rewards
     */
    public void handleMobKill(Player killer) {
        // Get configurable soul reward for mob kills
        int soulsPerKill = plugin.getConfigManager().getInt("perks.yml", "shop.souls-per-kill", 1);

        addSouls(killer.getUniqueId(), soulsPerKill).thenRun(() -> {
            String message = plugin.getConfigManager().getString("config.yml",
                    "messages.souls-received", "&aYou received &6{amount} &asouls!");
            message = message.replace("{amount}", String.valueOf(soulsPerKill));
            killer.sendMessage(ColorUtils.color(message));
        });

        // Track soul collection statistic
        plugin.getStatisticManager().trackSoulCollected(killer, soulsPerKill);
    }

    /**
     * Processes a soul shop purchase
     */
    public CompletableFuture<Boolean> processPurchase(UUID playerUUID, int cost) {
        return removeSouls(playerUUID, cost);
    }

    /**
     * Gets formatted soul count for display
     */
    public CompletableFuture<String> getFormattedSouls(UUID playerUUID) {
        return getSouls(playerUUID).thenApply(souls -> {
            if (souls < 1000) {
                return String.valueOf(souls);
            } else if (souls < 1000000) {
                return String.format("%.1fK", souls / 1000.0);
            } else if (souls < 1000000000) {
                return String.format("%.1fM", souls / 1000000.0);
            } else {
                return String.format("%.1fB", souls / 1000000000.0);
            }
        });
    }

    /**
     * Transfers souls between players
     */
    public CompletableFuture<Boolean> transferSouls(UUID fromPlayer, UUID toPlayer, long amount) {
        return removeSouls(fromPlayer, amount).thenCompose(success -> {
            if (success) {
                return addSouls(toPlayer, amount).thenApply(v -> true);
            } else {
                return CompletableFuture.completedFuture(false);
            }
        });
    }

    /**
     * Gets the top soul holders for leaderboards
     */
    public CompletableFuture<java.util.List<java.util.Map.Entry<String, Long>>> getTopSoulHolders(int limit) {
        return plugin.getDatabaseManager().queryAsync(
                "SELECT username, souls FROM player_data ORDER BY souls DESC LIMIT ?",
                resultSet -> {
                    java.util.List<java.util.Map.Entry<String, Long>> leaderboard = new java.util.ArrayList<>();
                    while (resultSet.next()) {
                        String username = resultSet.getString("username");
                        long souls = resultSet.getLong("souls");
                        leaderboard.add(new java.util.AbstractMap.SimpleEntry<>(username, souls));
                    }
                    return leaderboard;
                },
                limit
        );
    }

    /**
     * Gets total souls in circulation (for economy tracking)
     */
    public CompletableFuture<Long> getTotalSoulsInCirculation() {
        return plugin.getDatabaseManager().queryAsync(
                "SELECT SUM(souls) as total FROM player_data",
                resultSet -> {
                    if (resultSet.next()) {
                        return resultSet.getLong("total");
                    }
                    return 0L;
                }
        );
    }

    /**
     * Gets average souls per player (for economy balancing)
     */
    public CompletableFuture<Double> getAverageSoulsPerPlayer() {
        return plugin.getDatabaseManager().queryAsync(
                "SELECT AVG(souls) as average FROM player_data WHERE souls > 0",
                resultSet -> {
                    if (resultSet.next()) {
                        return resultSet.getDouble("average");
                    }
                    return 0.0;
                }
        );
    }

    /**
     * Bulk soul operation for admin commands
     */
    public CompletableFuture<Void> bulkAddSouls(java.util.Map<UUID, Long> playerSouls) {
        Object[][] parameterSets = new Object[playerSouls.size()][];
        int i = 0;
        for (java.util.Map.Entry<UUID, Long> entry : playerSouls.entrySet()) {
            parameterSets[i++] = new Object[]{entry.getValue(), entry.getKey().toString()};
        }

        return plugin.getDatabaseManager().executeBatchAsync(
                "UPDATE player_data SET souls = souls + ? WHERE uuid = ?",
                parameterSets
        );
    }

    /**
     * Reset all souls (admin command for economy resets)
     */
    public CompletableFuture<Void> resetAllSouls() {
        return plugin.getDatabaseManager().executeAsync(
                "UPDATE player_data SET souls = 0"
        );
    }

    /**
     * Set minimum soul amount for all players (economy floor)
     */
    public CompletableFuture<Void> setMinimumSouls(long minimumAmount) {
        return plugin.getDatabaseManager().executeAsync(
                "UPDATE player_data SET souls = ? WHERE souls < ?",
                minimumAmount, minimumAmount
        );
    }
}