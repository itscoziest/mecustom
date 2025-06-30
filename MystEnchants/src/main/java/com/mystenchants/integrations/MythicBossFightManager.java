package com.mystenchants.integrations;

import com.mystenchants.MystEnchants;
import com.mystenchants.utils.ColorUtils;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * ENHANCED: Manages MythicMobs specific boss fight features with proper reward handling
 */
public class MythicBossFightManager {

    private final MystEnchants plugin;
    private final MythicMobsIntegration mythicIntegration;

    public MythicBossFightManager(MystEnchants plugin, MythicMobsIntegration mythicIntegration) {
        this.plugin = plugin;
        this.mythicIntegration = mythicIntegration;
    }

    /**
     * FIXED: Starts a boss fight with appropriate boss type
     */
    public LivingEntity startBossFight(Location spawnLocation, Player fighter) {
        plugin.getLogger().info("Starting redemption boss fight for " + fighter.getName());

        // Announce boss type being used
        String bossInfo = mythicIntegration.getBossInfo();
        plugin.getLogger().info("Boss type: " + bossInfo);

        // Validate MythicMobs boss if using MythicMobs
        if (mythicIntegration.shouldUseMythicMobs()) {
            plugin.getLogger().info("MythicMobs is enabled, validating boss...");

            if (!mythicIntegration.validateMythicBoss()) {
                plugin.getLogger().warning("MythicMobs boss validation failed! Falling back to vanilla.");
                String fallbackMessage = plugin.getConfigManager().getString("config.yml", "integrations.mythicmobs.messages.fallback-to-vanilla", "&cMythicMobs boss not found! Using vanilla boss instead.");
                fighter.sendMessage(ColorUtils.color(fallbackMessage));

                // List available mobs for debugging
                mythicIntegration.listAvailableMobs();
            } else {
                plugin.getLogger().info("MythicMobs boss validation passed!");
            }
        } else {
            plugin.getLogger().info("MythicMobs is disabled or unavailable, using vanilla boss");
        }

        // Spawn the boss
        LivingEntity boss = mythicIntegration.spawnRedemptionBoss(spawnLocation);

        if (boss == null) {
            plugin.getLogger().severe("Failed to spawn any boss! Aborting fight.");
            return null;
        }

        // Send boss type message to fighter
        if (mythicIntegration.shouldUseMythicMobs()) {
            String mythicMessage = plugin.getConfigManager().getString("config.yml", "integrations.mythicmobs.messages.boss-spawned", "&6A powerful MythicMobs boss has appeared!");
            fighter.sendMessage(ColorUtils.color(mythicMessage));
            plugin.getLogger().info("MythicMobs boss successfully spawned and configured!");
        } else {
            String vanillaMessage = plugin.getConfigManager().getString("config.yml", "integrations.mythicmobs.messages.vanilla-boss-spawned", "&6The redemption boss has appeared!");
            fighter.sendMessage(ColorUtils.color(vanillaMessage));
        }

        return boss;
    }

    /**
     * ENHANCED: Handles boss defeat logic for MythicMobs bosses with proper rewards
     */
    public void handleBossDefeat(Player fighter, boolean isMythicMob) {
        plugin.getLogger().info("Handling boss defeat for " + fighter.getName() + " (MythicMob: " + isMythicMob + ")");

        if (isMythicMob) {
            String victoryMessage = plugin.getConfigManager().getString("config.yml", "integrations.mythicmobs.messages.mythic-boss-defeated", "&a&lYou have defeated the MythicMobs redemption boss!");
            fighter.sendMessage(ColorUtils.color(victoryMessage));

            // FIXED: Always give extra rewards regardless of config (since this is the redemption boss)
            giveExtraRewards(fighter);
        } else {
            // FIXED: Also give rewards for vanilla boss
            giveExtraRewards(fighter);
        }

        // Continue with normal redemption completion
        plugin.getRedemptionManager().endRedemption(true, "Boss defeated");
    }

    /**
     * ENHANCED: Gives extra rewards for defeating redemption boss (both MythicMobs and vanilla)
     */
    private void giveExtraRewards(Player fighter) {
        // FIXED: Always give redemption boss rewards
        int extraSouls = plugin.getConfigManager().getInt("config.yml", "integrations.mythicmobs.redemption-boss.extra-rewards.souls", 50);
        int extraExp = plugin.getConfigManager().getInt("config.yml", "integrations.mythicmobs.redemption-boss.extra-rewards.exp", 100);

        // Give extra souls
        if (extraSouls > 0) {
            plugin.getSoulManager().addSouls(fighter.getUniqueId(), extraSouls);
            String soulMessage = plugin.getConfigManager().getString("config.yml", "integrations.mythicmobs.messages.extra-souls", "&6+{souls} bonus souls for defeating the redemption boss!");
            soulMessage = soulMessage.replace("{souls}", String.valueOf(extraSouls));
            fighter.sendMessage(ColorUtils.color(soulMessage));
        }

        // Give extra EXP
        if (extraExp > 0) {
            fighter.giveExp(extraExp);
            String expMessage = plugin.getConfigManager().getString("config.yml", "integrations.mythicmobs.messages.extra-exp", "&a+{exp} bonus EXP for defeating the redemption boss!");
            expMessage = expMessage.replace("{exp}", String.valueOf(extraExp));
            fighter.sendMessage(ColorUtils.color(expMessage));
        }

        plugin.getLogger().info("Gave redemption boss rewards to " + fighter.getName() + ": " + extraSouls + " souls, " + extraExp + " EXP");
    }
}