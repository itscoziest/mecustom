package com.mystenchants.listeners;

import com.mystenchants.MystEnchants;
import com.mystenchants.enchants.CustomEnchant;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Random;

/**
 * Handles entity damage events for enchant effects
 */
public class EntityDamageListener implements Listener {

    private final MystEnchants plugin;
    private final Random random = new Random();

    public EntityDamageListener(MystEnchants plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();

        // Handle armor enchants
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && plugin.getEnchantManager().hasCustomEnchant(armor)) {
                CustomEnchant enchant = plugin.getEnchantManager().getCustomEnchant(armor);
                int level = plugin.getEnchantManager().getCustomEnchantLevel(armor);

                if (enchant != null && enchant.getName().equals("rejuvenate")) {
                    handleRejuvenate(player, level, event);
                }
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;

        Player attacker = (Player) event.getDamager();
        ItemStack weapon = attacker.getInventory().getItemInMainHand();

        if (plugin.getEnchantManager().hasCustomEnchant(weapon)) {
            CustomEnchant enchant = plugin.getEnchantManager().getCustomEnchant(weapon);
            int level = plugin.getEnchantManager().getCustomEnchantLevel(weapon);

            if (enchant != null && enchant.getName().equals("serrate")) {
                handleSerrate(event, level);
            }
        }
    }

    private void handleRejuvenate(Player player, int level, EntityDamageEvent event) {
        // Only trigger when player is at low health
        double healthAfterDamage = player.getHealth() - event.getFinalDamage();
        if (healthAfterDamage > 6.0) return; // 3 hearts

        double chance = level == 1 ? 0.10 : level == 2 ? 0.12 : 0.17;
        double healing = level == 1 ? 4.0 : level == 2 ? 6.0 : 10.0; // 2, 3, 5 hearts

        if (random.nextDouble() < chance) {
            double newHealth = Math.min(player.getMaxHealth(), player.getHealth() + healing);
            player.setHealth(newHealth);
            player.sendMessage(org.bukkit.ChatColor.GREEN + "Rejuvenate activated! +" + (healing/2) + " hearts");
        }
    }

    private void handleSerrate(EntityDamageByEntityEvent event, int level) {
        if (!(event.getEntity() instanceof Player)) return;

        Player victim = (Player) event.getEntity();
        int duration = level == 1 ? 30 : level == 2 ? 40 : 70; // 1.5s, 2s, 3.5s (in ticks)

        // Apply bleed effect (poison)
        victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, duration, 0, false, true));
    }
}