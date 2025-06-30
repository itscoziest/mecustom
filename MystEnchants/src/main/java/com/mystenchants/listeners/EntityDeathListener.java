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
 * Handles entity death events for enchant effects and statistics
 * FIXED: Prevent redemption boss from giving souls + handle extra rewards
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
        if (plugin.getEnchantManager().hasCustomEnchant(weapon)) {
            CustomEnchant enchant = plugin.getEnchantManager().getCustomEnchant(weapon);
            int level = plugin.getEnchantManager().getCustomEnchantLevel(weapon);

            if (enchant != null) {
                handleEnchantEffect(event, enchant, level, killer);
            }
        }
    }

    private void handleEnchantEffect(EntityDeathEvent event, CustomEnchant enchant, int level, Player killer) {
        switch (enchant.getName()) {
            case "scholar":
                handleScholar(event, level);
                break;
            case "guillotine":
                handleGuillotine(event, level, killer);
                break;
            // Add other combat enchants here
        }
    }

    private void handleScholar(EntityDeathEvent event, int level) {
        // Increase EXP from mob kills
        double multiplier = level == 1 ? 1.05 : level == 2 ? 1.08 : 1.15; // 5%, 8%, 15%
        int originalExp = event.getDroppedExp();
        int bonusExp = (int) (originalExp * (multiplier - 1.0));

        event.setDroppedExp(originalExp + bonusExp);
    }

    private void handleGuillotine(EntityDeathEvent event, int level, Player killer) {
        // Only works on player kills
        if (!(event.getEntity() instanceof Player)) return;

        Player victim = (Player) event.getEntity();

        // Calculate chance based on level
        double chance = level == 1 ? 0.10 : level == 2 ? 0.30 : 0.70; // 10%, 30%, 70%

        if (random.nextDouble() < chance) {
            // Create player head
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();

            if (meta != null) {
                meta.setOwningPlayer(victim);

                // Set custom death message from config
                String deathMessage = plugin.getEnchantManager().getEnchant("guillotine").getDeathMessage();
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
        }
    }
}