package com.mystenchants.listeners;

import com.mystenchants.MystEnchants;
import com.mystenchants.utils.ColorUtils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles hit-based perk effects like Spellbreaker and Lovestruck
 * CLEAN VERSION: Removed debug messages
 */
public class PerkCombatListener implements Listener {

    private final MystEnchants plugin;
    private final NamespacedKey perkEffectKey;
    private final Map<UUID, Integer> spellbreakerHits = new HashMap<>();
    private final Map<UUID, Integer> lovestruckHits = new HashMap<>();
    private final Map<UUID, Long> lastHitTime = new HashMap<>();
    private final Map<UUID, UUID> lastHitTarget = new HashMap<>();

    public PerkCombatListener(MystEnchants plugin) {
        this.plugin = plugin;
        this.perkEffectKey = new NamespacedKey(plugin, "perk_effect");
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        if (!(event.getEntity() instanceof Player)) return;

        Player attacker = (Player) event.getDamager();
        Player victim = (Player) event.getEntity();
        ItemStack weapon = attacker.getInventory().getItemInMainHand();

        if (isPerkEffectItem(weapon)) {
            String perkType = getPerkEffectType(weapon);

            switch (perkType) {
                case "spellbreaker":
                    handleSpellbreakerHit(attacker, victim);
                    break;
                case "lovestruck":
                    handleLovestruckHit(attacker, victim);
                    break;
            }
        }

        // Track when players are hit for combo breaking
        lastHitTime.put(victim.getUniqueId(), System.currentTimeMillis());
    }

    private void handleSpellbreakerHit(Player attacker, Player victim) {
        int requiredHits = plugin.getConfigManager().getInt("perks.yml", "perks.spellbreaker.required-hits", 5);
        boolean resetOnDamage = plugin.getConfigManager().getBoolean("perks.yml", "perks.spellbreaker.reset-on-damage", true);

        // Check if attacker was hit recently (reset counter)
        if (resetOnDamage && wasRecentlyHit(attacker)) {
            spellbreakerHits.put(attacker.getUniqueId(), 0);
            lastHitTarget.remove(attacker.getUniqueId());
            attacker.sendMessage(ColorUtils.color("&6Spellbreaker combo broken! You were hit."));
            return;
        }

        // Check if hitting the same target
        UUID lastTarget = lastHitTarget.get(attacker.getUniqueId());
        if (lastTarget != null && !lastTarget.equals(victim.getUniqueId())) {
            spellbreakerHits.put(attacker.getUniqueId(), 0);
            attacker.sendMessage(ColorUtils.color("&6Spellbreaker combo reset - different target!"));
        }

        // Update target tracking
        lastHitTarget.put(attacker.getUniqueId(), victim.getUniqueId());

        // Increment hit counter
        int hits = spellbreakerHits.getOrDefault(attacker.getUniqueId(), 0) + 1;
        spellbreakerHits.put(attacker.getUniqueId(), hits);

        // Show hit counter
        attacker.sendMessage(ColorUtils.color("&6Spellbreaker: &f" + hits + "&7/&f" + requiredHits + " &7hits"));

        if (hits >= requiredHits) {
            // Activate spellbreaker effect
            int effectDuration = plugin.getConfigManager().getInt("perks.yml", "perks.spellbreaker.effect-duration", 5);

            // Remove all potion effects from victim
            int effectsRemoved = 0;
            for (PotionEffect effect : victim.getActivePotionEffects()) {
                if (effect.getDuration() < 999999) {
                    victim.removePotionEffect(effect.getType());
                    effectsRemoved++;
                }
            }

            // Apply temporary effect to prevent new positive effects
            victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, effectDuration * 20, 0, false, true));

            attacker.sendMessage(ColorUtils.color("&6&lSPELLBREAKER! &7Removed " + effectsRemoved + " effects from " + victim.getName() + "!"));
            victim.sendMessage(ColorUtils.color("&6" + attacker.getName() + " broke your magical effects for " + effectDuration + " seconds!"));

            // Visual effects
            victim.getWorld().spawnParticle(org.bukkit.Particle.SPELL_WITCH, victim.getLocation().add(0, 1, 0), 30, 1, 1, 1, 0.1);
            victim.getWorld().playSound(victim.getLocation(), org.bukkit.Sound.ENTITY_WITCH_DRINK, 1.0f, 0.5f);

            // Reset counter and consume item
            spellbreakerHits.remove(attacker.getUniqueId());
            lastHitTarget.remove(attacker.getUniqueId());
            consumePerkItemSafely(attacker);
        }
    }

    private void handleLovestruckHit(Player attacker, Player victim) {
        int requiredHits = plugin.getConfigManager().getInt("perks.yml", "perks.lovestruck.required-hits", 5);
        boolean resetOnDamage = plugin.getConfigManager().getBoolean("perks.yml", "perks.lovestruck.reset-on-damage", true);

        // Check if attacker was hit recently (reset counter)
        if (resetOnDamage && wasRecentlyHit(attacker)) {
            lovestruckHits.put(attacker.getUniqueId(), 0);
            lastHitTarget.remove(attacker.getUniqueId());
            attacker.sendMessage(ColorUtils.color("&dLovestruck combo broken! You were hit."));
            return;
        }

        // Check if hitting the same target
        UUID lastTarget = lastHitTarget.get(attacker.getUniqueId());
        if (lastTarget != null && !lastTarget.equals(victim.getUniqueId())) {
            lovestruckHits.put(attacker.getUniqueId(), 0);
            attacker.sendMessage(ColorUtils.color("&dLovestruck combo reset - different target!"));
        }

        // Update target tracking
        lastHitTarget.put(attacker.getUniqueId(), victim.getUniqueId());

        // Increment hit counter
        int hits = lovestruckHits.getOrDefault(attacker.getUniqueId(), 0) + 1;
        lovestruckHits.put(attacker.getUniqueId(), hits);

        attacker.sendMessage(ColorUtils.color("&dLovestruck: &f" + hits + "&7/&f" + requiredHits + " &7hits"));

        if (hits >= requiredHits) {
            // Activate lovestruck effect
            int nauseaDuration = plugin.getConfigManager().getInt("perks.yml", "perks.lovestruck.nausea-duration", 3);
            int roseDuration = plugin.getConfigManager().getInt("perks.yml", "perks.lovestruck.rose-duration", 3);

            // Apply multiple effects for full lovestruck experience
            victim.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, nauseaDuration * 20, 0, false, true));
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, nauseaDuration * 20, 1, false, true));
            victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, (nauseaDuration * 20) / 2, 0, false, true));

            // Replace inventory with roses
            replaceInventoryWithRoses(victim, roseDuration);

            attacker.sendMessage(ColorUtils.color("&d&lLOVESTRUCK! &7" + victim.getName() + " is overwhelmed with love!"));
            victim.sendMessage(ColorUtils.color("&d" + attacker.getName() + " has made you lovestruck!"));
            victim.sendMessage(ColorUtils.color("&7Your screen will start shaking in 1-2 seconds..."));

            // Visual effects
            victim.getWorld().spawnParticle(org.bukkit.Particle.HEART, victim.getLocation().add(0, 2, 0), 30, 1, 1, 1, 0.1);
            victim.getWorld().spawnParticle(org.bukkit.Particle.VILLAGER_HAPPY, victim.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);
            victim.getWorld().playSound(victim.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_YES, 1.0f, 1.5f);

            // Reset counter and consume item
            lovestruckHits.remove(attacker.getUniqueId());
            lastHitTarget.remove(attacker.getUniqueId());
            consumePerkItemSafely(attacker);
        }
    }

    private boolean wasRecentlyHit(Player player) {
        Long lastHit = lastHitTime.get(player.getUniqueId());
        return lastHit != null && (System.currentTimeMillis() - lastHit) < 3000;
    }

    private void replaceInventoryWithRoses(Player player, int duration) {
        ItemStack[] originalInventory = player.getInventory().getContents().clone();

        ItemStack rose = new ItemStack(Material.ROSE_BUSH);
        ItemMeta roseMeta = rose.getItemMeta();
        if (roseMeta != null) {
            roseMeta.setDisplayName(ColorUtils.color("&d&lLove Rose"));
            roseMeta.setLore(java.util.Arrays.asList(
                    ColorUtils.color("&7You are overwhelmed with love!"),
                    ColorUtils.color("&7Your inventory will return shortly...")
            ));
            rose.setItemMeta(roseMeta);
        }

        for (int i = 0; i < 36; i++) {
            player.getInventory().setItem(i, rose.clone());
        }

        player.sendMessage(ColorUtils.color("&d&lYour inventory is filled with roses of love!"));

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                for (int i = 0; i < 36; i++) {
                    ItemStack originalItem = originalInventory[i];
                    player.getInventory().setItem(i, originalItem);
                }
                player.sendMessage(ColorUtils.color("&7Your inventory has been restored."));
                player.getWorld().spawnParticle(org.bukkit.Particle.ENCHANTMENT_TABLE, player.getLocation().add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0.1);
            }
        }, duration * 20L);
    }

    private void consumePerkItemSafely(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item != null && item.getAmount() > 0 && isPerkEffectItem(item)) {
            int currentAmount = item.getAmount();

            if (currentAmount > 1) {
                item.setAmount(currentAmount - 1);
            } else {
                player.getInventory().setItemInMainHand(null);
            }

            player.sendMessage(ColorUtils.color("&7Perk item consumed."));
        }
    }

    private boolean isPerkEffectItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.has(perkEffectKey, PersistentDataType.STRING);
    }

    private String getPerkEffectType(ItemStack item) {
        if (!isPerkEffectItem(item)) return null;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.get(perkEffectKey, PersistentDataType.STRING);
    }

    public void cleanupPlayer(Player player) {
        UUID playerUUID = player.getUniqueId();
        spellbreakerHits.remove(playerUUID);
        lovestruckHits.remove(playerUUID);
        lastHitTime.remove(playerUUID);
        lastHitTarget.remove(playerUUID);
    }
}