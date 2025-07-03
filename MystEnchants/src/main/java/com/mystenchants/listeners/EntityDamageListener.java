package com.mystenchants.listeners;

import com.mystenchants.MystEnchants;
import com.mystenchants.enchants.CustomEnchant;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Random;

/**
 * FIXED: Handles entity damage events for enchant effects
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

        // FIXED: Handle armor enchants - check each piece for specific enchants
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null) continue;

            // Check for Rejuvenate enchant
            if (plugin.getEnchantManager().hasSpecificCustomEnchant(armor, "rejuvenate")) {
                int level = plugin.getEnchantManager().getSpecificCustomEnchantLevel(armor, "rejuvenate");
                plugin.getLogger().info("REJUVENATE ENCHANT DETECTED! Level: " + level + " on " + armor.getType());
                handleRejuvenate(player, level, event);
            }

            // Add other armor enchants here if needed
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // FIX: Immediately stop processing if the event was cancelled by another plugin (like WorldGuard).
        if (event.isCancelled()) {
            return;
        }

        if (!(event.getDamager() instanceof Player)) {
            return;
        }

        Player attacker = (Player) event.getDamager();
        ItemStack weapon = attacker.getInventory().getItemInMainHand();

        // Check for Serrate enchant
        if (plugin.getEnchantManager().hasSpecificCustomEnchant(weapon, "serrate")) {
            int level = plugin.getEnchantManager().getSpecificCustomEnchantLevel(weapon, "serrate");
            handleSerrate(event, level);
        }

        // Check for Pantsed enchant on leggings
        ItemStack leggings = attacker.getInventory().getLeggings();
        if (leggings != null && plugin.getEnchantManager().hasSpecificCustomEnchant(leggings, "pantsed")) {
            int level = plugin.getEnchantManager().getSpecificCustomEnchantLevel(leggings, "pantsed");
            handlePantsed(event, level, attacker);
        }
    }

    private void handleRejuvenate(Player player, int level, EntityDamageEvent event) {
        // Only trigger when player is at low health
        double triggerHealth = plugin.getEnchantManager().getRejuvenateTriggerHealth();
        double healthAfterDamage = player.getHealth() - event.getFinalDamage();

        if (healthAfterDamage > triggerHealth) return;

        double chance = plugin.getEnchantManager().getRejuvenateHealChance(level);
        double healing = plugin.getEnchantManager().getRejuvenateHealAmount(level);

        plugin.getLogger().info("Rejuvenate check: " + healthAfterDamage + " HP, " + (chance * 100) + "% chance, " + healing + " healing");

        if (random.nextDouble() < chance) {
            double newHealth = Math.min(player.getMaxHealth(), player.getHealth() + healing);
            player.setHealth(newHealth);
            player.sendMessage(org.bukkit.ChatColor.GREEN + "Rejuvenate activated! +" + (healing/2) + " hearts");
            plugin.getLogger().info("Rejuvenate activated! Healed " + healing + " HP");
        } else {
            plugin.getLogger().info("Rejuvenate failed - no healing");
        }
    }

    private void handleSerrate(EntityDamageByEntityEvent event, int level) {
        if (!(event.getEntity() instanceof Player)) return;

        Player victim = (Player) event.getEntity();
        int duration = plugin.getEnchantManager().getSerrateBleedDuration(level);

        // Apply bleed effect (poison)
        victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, duration, 0, false, true));

        plugin.getLogger().info("Serrate applied poison for " + duration + " ticks to " + victim.getName());
    }

    private void handlePantsed(EntityDamageByEntityEvent event, int level, Player attacker) {
        if (!(event.getEntity() instanceof Player)) return;

        Player victim = (Player) event.getEntity();
        double chance = plugin.getEnchantManager().getPantsedStealChance(level);

        plugin.getLogger().info("Pantsed check: " + (chance * 100) + "% chance for Level " + level);

        if (random.nextDouble() < chance) {
            ItemStack victimLeggings = victim.getInventory().getLeggings();
            if (victimLeggings != null) {
                // Steal the pants
                victim.getInventory().setLeggings(null);
                attacker.getInventory().addItem(victimLeggings);

                attacker.sendMessage(org.bukkit.ChatColor.GOLD + "Pantsed! You stole " + victim.getName() + "'s pants!");
                victim.sendMessage(org.bukkit.ChatColor.RED + "Your pants were stolen by " + attacker.getName() + "!");

                plugin.getLogger().info("Pantsed succeeded! " + attacker.getName() + " stole " + victim.getName() + "'s pants");
            }
        } else {
            plugin.getLogger().info("Pantsed failed - no pants stolen");
        }
    }
}