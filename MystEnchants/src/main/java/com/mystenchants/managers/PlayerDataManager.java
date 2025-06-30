package com.mystenchants.managers;

import com.mystenchants.MystEnchants;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PlayerDataManager {

    private final MystEnchants plugin;

    public PlayerDataManager(MystEnchants plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<Void> createPlayerData(Player player) {
        UUID uuid = player.getUniqueId();
        String username = player.getName();
        long currentTime = System.currentTimeMillis();

        String sql;
        if (plugin.getDatabaseManager().isMySQL()) {
            sql = "INSERT INTO player_data (uuid, username, souls, last_seen, redemption_cooldown) " +
                    "VALUES (?, ?, 0, ?, 0) " +
                    "ON DUPLICATE KEY UPDATE username = ?, last_seen = ?";
        } else {
            sql = "INSERT OR REPLACE INTO player_data (uuid, username, souls, last_seen, redemption_cooldown) " +
                    "VALUES (?, ?, COALESCE((SELECT souls FROM player_data WHERE uuid = ?), 0), ?, " +
                    "COALESCE((SELECT redemption_cooldown FROM player_data WHERE uuid = ?), 0))";
        }

        return plugin.getDatabaseManager().executeAsync(sql,
                plugin.getDatabaseManager().isMySQL() ?
                        new Object[]{uuid.toString(), username, currentTime, username, currentTime} :
                        new Object[]{uuid.toString(), username, uuid.toString(), currentTime, uuid.toString()}
        ).thenCompose(v -> {
            String statsSql;
            if (plugin.getDatabaseManager().isMySQL()) {
                statsSql = "INSERT IGNORE INTO player_statistics (uuid) VALUES (?)";
            } else {
                statsSql = "INSERT OR IGNORE INTO player_statistics (uuid) VALUES (?)";
            }
            return plugin.getDatabaseManager().executeAsync(statsSql, uuid.toString());
        });
    }

    public CompletableFuture<Void> updateLastSeen(UUID playerUUID) {
        return plugin.getDatabaseManager().executeAsync(
                "UPDATE player_data SET last_seen = ? WHERE uuid = ?",
                System.currentTimeMillis(), playerUUID.toString()
        );
    }

    public CompletableFuture<Map<String, Integer>> getPlayerEnchants(UUID playerUUID) {
        return plugin.getDatabaseManager().queryAsync(
                "SELECT enchant_name, level FROM player_enchants WHERE uuid = ?",
                resultSet -> {
                    Map<String, Integer> enchants = new HashMap<>();
                    while (resultSet.next()) {
                        enchants.put(resultSet.getString("enchant_name"),
                                resultSet.getInt("level"));
                    }
                    return enchants;
                },
                playerUUID.toString()
        );
    }

    public CompletableFuture<Integer> getEnchantLevel(UUID playerUUID, String enchantName) {
        return plugin.getDatabaseManager().queryAsync(
                "SELECT level FROM player_enchants WHERE uuid = ? AND enchant_name = ?",
                resultSet -> {
                    if (resultSet.next()) {
                        return resultSet.getInt("level");
                    }
                    return 0;
                },
                playerUUID.toString(), enchantName
        );
    }

    public CompletableFuture<Void> setEnchantLevel(UUID playerUUID, String enchantName, int level) {
        if (level <= 0) {
            return removeEnchant(playerUUID, enchantName);
        }

        String sql;
        Object[] params;

        if (plugin.getDatabaseManager().isMySQL()) {
            sql = "INSERT INTO player_enchants (uuid, enchant_name, level, unlocked_at) " +
                    "VALUES (?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE level = ?";
            params = new Object[]{playerUUID.toString(), enchantName, level, System.currentTimeMillis(), level};
        } else {
            sql = "INSERT OR REPLACE INTO player_enchants (uuid, enchant_name, level, unlocked_at) " +
                    "VALUES (?, ?, ?, ?)";
            params = new Object[]{playerUUID.toString(), enchantName, level, System.currentTimeMillis()};
        }

        return plugin.getDatabaseManager().executeAsync(sql, params);
    }

    public CompletableFuture<Void> unlockEnchant(UUID playerUUID, String enchantName, int level) {
        return setEnchantLevel(playerUUID, enchantName, level).thenCompose(v ->
                incrementStatistic(playerUUID, "enchants_unlocked", 1)
        );
    }

    public CompletableFuture<Void> removeEnchant(UUID playerUUID, String enchantName) {
        return plugin.getDatabaseManager().executeAsync(
                "DELETE FROM player_enchants WHERE uuid = ? AND enchant_name = ?",
                playerUUID.toString(), enchantName
        );
    }

    public CompletableFuture<Boolean> hasEnchantUnlocked(UUID playerUUID, String enchantName) {
        return getEnchantLevel(playerUUID, enchantName)
                .thenApply(level -> level > 0);
    }

    public CompletableFuture<Long> getStatistic(UUID playerUUID, String statisticName) {
        return plugin.getDatabaseManager().queryAsync(
                "SELECT " + statisticName + " FROM player_statistics WHERE uuid = ?",
                resultSet -> {
                    if (resultSet.next()) {
                        return resultSet.getLong(statisticName);
                    }
                    return 0L;
                },
                playerUUID.toString()
        );
    }

    public CompletableFuture<Void> setStatistic(UUID playerUUID, String statisticName, long value) {
        return plugin.getDatabaseManager().executeAsync(
                "UPDATE player_statistics SET " + statisticName + " = ? WHERE uuid = ?",
                value, playerUUID.toString()
        );
    }

    public CompletableFuture<Void> incrementStatistic(UUID playerUUID, String statisticName, long amount) {
        return plugin.getDatabaseManager().executeAsync(
                "UPDATE player_statistics SET " + statisticName + " = " + statisticName + " + ? WHERE uuid = ?",
                amount, playerUUID.toString()
        );
    }

    public CompletableFuture<Map<String, Long>> getPlayerStatistics(UUID playerUUID) {
        return plugin.getDatabaseManager().queryAsync(
                "SELECT * FROM player_statistics WHERE uuid = ?",
                resultSet -> {
                    Map<String, Long> stats = new HashMap<>();
                    if (resultSet.next()) {
                        stats.put("blocks_mined", resultSet.getLong("blocks_mined"));
                        stats.put("blocks_walked", resultSet.getLong("blocks_walked"));
                        stats.put("wheat_broken", resultSet.getLong("wheat_broken"));
                        stats.put("creepers_killed", resultSet.getLong("creepers_killed"));
                        stats.put("iron_ingots_traded", resultSet.getLong("iron_ingots_traded"));
                        stats.put("pants_crafted", resultSet.getLong("pants_crafted"));
                        stats.put("souls_collected", resultSet.getLong("souls_collected"));
                        stats.put("enchants_unlocked", resultSet.getLong("enchants_unlocked"));
                    }
                    return stats;
                },
                playerUUID.toString()
        );
    }

    public CompletableFuture<Long> getRedemptionCooldown(UUID playerUUID) {
        return plugin.getDatabaseManager().queryAsync(
                "SELECT redemption_cooldown FROM player_data WHERE uuid = ?",
                resultSet -> {
                    if (resultSet.next()) {
                        return resultSet.getLong("redemption_cooldown");
                    }
                    return 0L;
                },
                playerUUID.toString()
        );
    }

    public CompletableFuture<Void> setRedemptionCooldown(UUID playerUUID, long cooldownEnd) {
        String sql;
        Object[] params;

        if (plugin.getDatabaseManager().isMySQL()) {
            sql = "INSERT INTO player_data (uuid, username, souls, last_seen, redemption_cooldown) " +
                    "VALUES (?, 'Unknown', 0, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE redemption_cooldown = ?";
            params = new Object[]{playerUUID.toString(), System.currentTimeMillis(), cooldownEnd, cooldownEnd};
        } else {
            sql = "INSERT OR REPLACE INTO player_data (uuid, username, souls, last_seen, redemption_cooldown) " +
                    "VALUES (?, " +
                    "COALESCE((SELECT username FROM player_data WHERE uuid = ?), 'Unknown'), " +
                    "COALESCE((SELECT souls FROM player_data WHERE uuid = ?), 0), " +
                    "?, ?)";
            params = new Object[]{playerUUID.toString(), playerUUID.toString(), playerUUID.toString(), System.currentTimeMillis(), cooldownEnd};
        }

        return plugin.getDatabaseManager().executeAsync(sql, params);
    }

    public CompletableFuture<Boolean> isOnRedemptionCooldown(UUID playerUUID) {
        return getRedemptionCooldown(playerUUID)
                .thenApply(cooldownEnd -> System.currentTimeMillis() < cooldownEnd);
    }

    public CompletableFuture<Long> getRemainingRedemptionCooldown(UUID playerUUID) {
        return getRedemptionCooldown(playerUUID)
                .thenApply(cooldownEnd -> {
                    long remaining = cooldownEnd - System.currentTimeMillis();
                    return Math.max(0, remaining / 1000);
                });
    }

    public CompletableFuture<Map<String, Integer>> getPlayerPerks(UUID playerUUID) {
        return plugin.getDatabaseManager().queryAsync(
                "SELECT perk_name, amount FROM player_perks WHERE uuid = ?",
                resultSet -> {
                    Map<String, Integer> perks = new HashMap<>();
                    while (resultSet.next()) {
                        perks.put(resultSet.getString("perk_name"),
                                resultSet.getInt("amount"));
                    }
                    return perks;
                },
                playerUUID.toString()
        );
    }

    public CompletableFuture<Integer> getPerkAmount(UUID playerUUID, String perkName) {
        return plugin.getDatabaseManager().queryAsync(
                "SELECT amount FROM player_perks WHERE uuid = ? AND perk_name = ?",
                resultSet -> {
                    if (resultSet.next()) {
                        return resultSet.getInt("amount");
                    }
                    return 0;
                },
                playerUUID.toString(), perkName
        );
    }

    public CompletableFuture<Void> setPerkAmount(UUID playerUUID, String perkName, int amount) {
        if (amount <= 0) {
            return plugin.getDatabaseManager().executeAsync(
                    "DELETE FROM player_perks WHERE uuid = ? AND perk_name = ?",
                    playerUUID.toString(), perkName
            );
        }

        String sql;
        Object[] params;

        if (plugin.getDatabaseManager().isMySQL()) {
            sql = "INSERT INTO player_perks (uuid, perk_name, amount, last_used) " +
                    "VALUES (?, ?, ?, 0) " +
                    "ON DUPLICATE KEY UPDATE amount = ?";
            params = new Object[]{playerUUID.toString(), perkName, amount, amount};
        } else {
            sql = "INSERT OR REPLACE INTO player_perks (uuid, perk_name, amount, last_used) " +
                    "VALUES (?, ?, ?, COALESCE((SELECT last_used FROM player_perks WHERE uuid = ? AND perk_name = ?), 0))";
            params = new Object[]{playerUUID.toString(), perkName, amount, playerUUID.toString(), perkName};
        }

        return plugin.getDatabaseManager().executeAsync(sql, params);
    }

    public CompletableFuture<Void> addPerk(UUID playerUUID, String perkName, int amount) {
        return getPerkAmount(playerUUID, perkName)
                .thenCompose(currentAmount ->
                        setPerkAmount(playerUUID, perkName, currentAmount + amount)
                );
    }

    public CompletableFuture<Boolean> usePerk(UUID playerUUID, String perkName) {
        return getPerkAmount(playerUUID, perkName)
                .thenCompose(currentAmount -> {
                    if (currentAmount > 0) {
                        return setPerkAmount(playerUUID, perkName, currentAmount - 1)
                                .thenCompose(v -> updatePerkLastUsed(playerUUID, perkName))
                                .thenApply(v -> true);
                    }
                    return CompletableFuture.completedFuture(false);
                });
    }

    public CompletableFuture<Void> updatePerkLastUsed(UUID playerUUID, String perkName) {
        String sql;
        Object[] params;

        if (plugin.getDatabaseManager().isMySQL()) {
            sql = "UPDATE player_perks SET last_used = ? WHERE uuid = ? AND perk_name = ?";
            params = new Object[]{System.currentTimeMillis(), playerUUID.toString(), perkName};
        } else {
            sql = "INSERT OR REPLACE INTO player_perks (uuid, perk_name, amount, last_used) " +
                    "VALUES (?, ?, COALESCE((SELECT amount FROM player_perks WHERE uuid = ? AND perk_name = ?), 1), ?)";
            params = new Object[]{playerUUID.toString(), perkName, playerUUID.toString(), perkName, System.currentTimeMillis()};
        }

        return plugin.getDatabaseManager().executeAsync(sql, params);
    }

    public CompletableFuture<Long> getPerkLastUsed(UUID playerUUID, String perkName) {
        return plugin.getDatabaseManager().queryAsync(
                "SELECT last_used FROM player_perks WHERE uuid = ? AND perk_name = ?",
                resultSet -> {
                    if (resultSet.next()) {
                        return resultSet.getLong("last_used");
                    }
                    return 0L;
                },
                playerUUID.toString(), perkName
        );
    }
}