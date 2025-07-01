package com.mystenchants.listeners;

import com.mystenchants.MystEnchants;
import com.mystenchants.enchants.CustomEnchant;
import com.mystenchants.utils.ColorUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Random;

/**
 * FIXED: Handles entity death events for enchant effects and statistics
 */
public class EntityDeathListener implements Listener {

    private final MystEnchants plugin;
    private final Random random = new Random();

    public EntityDeathListener(MystEnchants plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity().getKiller() instanceof Player)) return;

        Player killer = event.getEntity().getKiller();

        // FIXED: Check if this is the redemption boss - don't give normal soul rewards
        if (plugin.getRedemptionManager().isRedemptionBoss(event.getEntity())) {
            // This is handled by RedemptionManager - don't give normal souls
            handleEnchantEffects(event, killer);
            return;
        }

        // Track statistics for non-redemption entities
        plugin.getStatisticManager().trackEntityKilled(killer, event.getEntity().getType());

        // Handle enchant effects
        handleEnchantEffects(event, killer);

        // Handle soul rewards for regular entities
        if (event.getEntity() instanceof Player) {
            // Player kill - 5 souls
            plugin.getSoulManager().handlePlayerKill(killer);
        } else {
            // Mob kill - 1 soul
            plugin.getSoulManager().handleMobKill(killer);
        }
    }

    private void handleEnchantEffects(EntityDeathEvent event, Player killer) {
        ItemStack weapon = killer.getInventory().getItemInMainHand();

        // FIXED: Check for specific enchants instead of generic check

        // Check for Scholar enchant
        if (plugin.getEnchantManager().hasSpecificCustomEnchant(weapon, "scholar")) {
            int level = plugin.getEnchantManager().getSpecificCustomEnchantLevel(weapon, "scholar");
            plugin.getLogger().info("SCHOLAR ENCHANT DETECTED! Level: " + level + " on " + weapon.getType());
            handleScholar(event, level);
        }

        // Check for Guillotine enchant
        if (plugin.getEnchantManager().hasSpecificCustomEnchant(weapon, "guillotine")) {
            int level = plugin.getEnchantManager().getSpecificCustomEnchantLevel(weapon, "guillotine");
            plugin.getLogger().info("GUILLOTINE ENCHANT DETECTED! Level: " + level + " on " + weapon.getType());
            handleGuillotine(event, level, killer);
        }

        // Add more enchant checks here as needed
    }

    private void handleScholar(EntityDeathEvent event, int level) {
        // Increase EXP from mob kills
        double multiplier = plugin.getEnchantManager().getScholarExpMultiplier(level);
        int originalExp = event.getDroppedExp();
        int bonusExp = (int) (originalExp * (multiplier - 1.0));

        event.setDroppedExp(originalExp + bonusExp);

        plugin.getLogger().info("Scholar bonus: " + originalExp + " -> " + (originalExp + bonusExp) + " (x" + multiplier + ")");
    }

    private void handleGuillotine(EntityDeathEvent event, int level, Player killer) {
        // Only works on player kills
        if (!(event.getEntity() instanceof Player)) return;

        Player victim = (Player) event.getEntity();

        // Calculate chance based on level
        double chance = plugin.getEnchantManager().getGuillotineHeadDropChance(level);

        plugin.getLogger().info("Guillotine chance: " + (chance * 100) + "% for Level " + level);

        if (random.nextDouble() < chance) {
            // Create player head
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();

            if (meta != null) {
                meta.setOwningPlayer(victim);

                // Set custom death message from config
                String deathMessage = plugin.getEnchantManager().getEnchantDeathMessage("guillotine");
                if (!deathMessage.isEmpty()) {
                    deathMessage = deathMessage.replace("{victim}", victim.getName())
                            .replace("{killer}", killer.getName());
                    meta.setDisplayName(ColorUtils.color(deathMessage));
                }

                head.setItemMeta(meta);
            }

            // Drop the head
            event.getDrops().add(head);

            killer.sendMessage(ColorUtils.color("&6&lGuillotine! &7You obtained " + victim.getName() + "'s head!"));
            plugin.getLogger().info("Guillotine succeeded! Dropped " + victim.getName() + "'s head");
        } else {
            plugin.getLogger().info("Guillotine failed - no head dropped");
        }
    }
}