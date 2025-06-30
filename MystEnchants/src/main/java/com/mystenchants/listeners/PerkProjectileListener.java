package com.mystenchants.listeners;

import com.mystenchants.MystEnchants;
import com.mystenchants.utils.ColorUtils;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;

/**
 * ENHANCED: Uses ALL configurable values and messages for projectile perks
 */
public class PerkProjectileListener implements Listener {

    private final MystEnchants plugin;
    private final NamespacedKey perkEffectKey;

    public PerkProjectileListener(MystEnchants plugin) {
        this.plugin = plugin;
        this.perkEffectKey = new NamespacedKey(plugin, "perk_effect");
    }

    @EventHandler
    public void onPlayerFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        ItemStack rod = player.getInventory().getItemInMainHand();

        // Check if using grappling hook perk
        if (isPerkEffectItem(rod) && "grappling-hook".equals(getPerkEffectType(rod))) {

            if (event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY &&
                    event.getCaught() instanceof Player) {

                Player target = (Player) event.getCaught();

                // Cancel normal fishing
                event.setCancelled(true);

                // Apply grappling hook effect with configurable values
                handleGrapplingHook(player, target);

                // Consume the rod
                consumeItem(player, rod);
            }
        }
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player)) return;

        Player shooter = (Player) event.getEntity().getShooter();
        ItemStack item = shooter.getInventory().getItemInMainHand();

        // Check if the item being thrown is a perk effect item
        if (isPerkEffectItem(item)) {
            String perkType = getPerkEffectType(item);

            if (event.getEntity() instanceof Snowball && "teleport-snowball".equals(perkType)) {
                tagProjectile(event.getEntity(), "teleport-snowball");
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    consumeItem(shooter, item);
                }, 1L);

            } else if (event.getEntity() instanceof Egg && "tradeoff-egg".equals(perkType)) {
                tagProjectile(event.getEntity(), "tradeoff-egg");
                event.getEntity().setMetadata("custom_perk", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    consumeItem(shooter, item);
                }, 1L);
            }
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player)) return;

        Player shooter = (Player) event.getEntity().getShooter();
        String perkType = getProjectilePerkType(event.getEntity());

        if (perkType == null) return;

        // Handle different hit scenarios
        if (event.getHitEntity() instanceof Player) {
            // Hit a player
            Player target = (Player) event.getHitEntity();
            handlePlayerHit(shooter, target, perkType, event.getEntity().getLocation());

        } else if (event.getHitBlock() != null) {
            // Hit a block
            handleBlockHit(shooter, perkType, event.getEntity().getLocation());
        }
    }

    private void handlePlayerHit(Player shooter, Player target, String perkType, org.bukkit.Location hitLocation) {
        switch (perkType) {
            case "teleport-snowball":
                handleTeleportSnowball(shooter, target, hitLocation);
                break;
            case "tradeoff-egg":
                handleTradeoffEgg(shooter, target);
                break;
        }
    }

    private void handleBlockHit(Player shooter, String perkType, org.bukkit.Location hitLocation) {
        switch (perkType) {
            case "teleport-snowball":
                // Teleport to the hit location
                shooter.teleport(hitLocation);

                // Use configurable message
                String impactMessage = plugin.getConfigManager().getString("config.yml",
                        "messages.perk-teleport-impact", "&bTeleported to impact location!");
                shooter.sendMessage(ColorUtils.color(impactMessage));

                shooter.getWorld().playSound(hitLocation, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                break;
            case "tradeoff-egg":
                // Use configurable message
                String blockHitMessage = plugin.getConfigManager().getString("config.yml",
                        "messages.perk-tradeoff-block-hit", "&eTradeoff egg can only affect players!");
                shooter.sendMessage(ColorUtils.color(blockHitMessage));
                break;
        }
    }

    private void handleTeleportSnowball(Player shooter, Player target, org.bukkit.Location hitLocation) {
        // Teleport shooter to target's location
        shooter.teleport(target.getLocation());

        // Use configurable messages
        String shooterMessage = plugin.getConfigManager().getString("config.yml",
                "messages.perk-teleport-success", "&bTeleported to {player}!");
        shooterMessage = shooterMessage.replace("{player}", target.getName());
        shooter.sendMessage(ColorUtils.color(shooterMessage));

        String targetMessage = plugin.getConfigManager().getString("config.yml",
                "messages.perk-teleport-target", "&b{player} teleported to you!");
        targetMessage = targetMessage.replace("{player}", shooter.getName());
        target.sendMessage(ColorUtils.color(targetMessage));

        // Sound effects
        shooter.getWorld().playSound(shooter.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        target.getWorld().playSound(target.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

        // Visual effects
        shooter.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, shooter.getLocation(), 50, 1, 1, 1, 0.1);
        target.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, target.getLocation(), 50, 1, 1, 1, 0.1);
    }

    private void handleTradeoffEgg(Player shooter, Player target) {
        // Get current potion effects
        List<PotionEffect> shooterEffects = new ArrayList<>();
        List<PotionEffect> targetEffects = new ArrayList<>();

        // Collect all effects
        for (PotionEffect effect : shooter.getActivePotionEffects()) {
            shooterEffects.add(new PotionEffect(
                    effect.getType(),
                    effect.getDuration(),
                    effect.getAmplifier(),
                    effect.isAmbient(),
                    effect.hasParticles(),
                    effect.hasIcon()
            ));
        }

        for (PotionEffect effect : target.getActivePotionEffects()) {
            targetEffects.add(new PotionEffect(
                    effect.getType(),
                    effect.getDuration(),
                    effect.getAmplifier(),
                    effect.isAmbient(),
                    effect.hasParticles(),
                    effect.hasIcon()
            ));
        }

        // Clear ALL effects from both players first
        for (PotionEffect effect : shooter.getActivePotionEffects()) {
            shooter.removePotionEffect(effect.getType());
        }
        for (PotionEffect effect : target.getActivePotionEffects()) {
            target.removePotionEffect(effect.getType());
        }

        // Give shooter the target's effects
        for (PotionEffect effect : targetEffects) {
            shooter.addPotionEffect(effect);
        }

        // Give target the shooter's effects
        for (PotionEffect effect : shooterEffects) {
            target.addPotionEffect(effect);
        }

        // Get configurable lock duration
        int lockDuration = plugin.getPerkManager().getPerkLockDuration("tradeoff-egg");

        // Use configurable messages with actual effect counts
        String shooterMessage = plugin.getConfigManager().getString("config.yml",
                "messages.perk-tradeoff-swapped", "&eYou swapped {your_effects} effects with {player} for their {their_effects} effects!");
        shooterMessage = shooterMessage.replace("{your_effects}", String.valueOf(shooterEffects.size()))
                .replace("{their_effects}", String.valueOf(targetEffects.size()))
                .replace("{player}", target.getName());
        shooter.sendMessage(ColorUtils.color(shooterMessage));

        String targetMessage = plugin.getConfigManager().getString("config.yml",
                "messages.perk-tradeoff-swapped-target", "&e{player} swapped their {their_effects} effects with your {your_effects} effects!");
        targetMessage = targetMessage.replace("{player}", shooter.getName())
                .replace("{their_effects}", String.valueOf(shooterEffects.size()))
                .replace("{your_effects}", String.valueOf(targetEffects.size()));
        target.sendMessage(ColorUtils.color(targetMessage));

        // Visual and sound effects
        org.bukkit.Location shooterLoc = shooter.getLocation();
        org.bukkit.Location targetLoc = target.getLocation();

        shooter.getWorld().spawnParticle(org.bukkit.Particle.SPELL_WITCH, shooterLoc.add(0, 1, 0), 50, 1, 1, 1, 0.1);
        target.getWorld().spawnParticle(Particle.SPELL_WITCH, targetLoc.add(0, 1, 0), 50, 1, 1, 1, 0.1);
        shooter.getWorld().playSound(shooterLoc, org.bukkit.Sound.ENTITY_WITCH_CELEBRATE, 1.0f, 1.0f);
        target.getWorld().playSound(targetLoc, org.bukkit.Sound.ENTITY_WITCH_CELEBRATE, 1.0f, 1.0f);

        // Schedule unlock message after configurable duration
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            String unlockMessage = plugin.getConfigManager().getString("config.yml",
                    "messages.perk-tradeoff-lock-expired", "&7Potion effect lock expired.");

            if (shooter.isOnline()) {
                shooter.sendMessage(ColorUtils.color(unlockMessage));
            }
            if (target.isOnline()) {
                target.sendMessage(ColorUtils.color(unlockMessage));
            }
        }, lockDuration * 20L);
    }

    private void handleGrapplingHook(Player shooter, Player target) {
        // Get configurable values
        double pullStrength = plugin.getPerkManager().getPerkPullStrength("grappling-hook");

        // Calculate direction and pull target towards shooter
        org.bukkit.util.Vector direction = shooter.getLocation().toVector().subtract(target.getLocation().toVector()).normalize();
        direction.multiply(pullStrength);
        direction.setY(Math.max(direction.getY(), 0.5)); // Ensure some upward movement

        target.setVelocity(direction);

        // Use configurable messages
        String shooterMessage = plugin.getConfigManager().getString("config.yml",
                "messages.perk-grappling-hook-success", "&aYou hooked {player}!");
        shooterMessage = shooterMessage.replace("{player}", target.getName());
        shooter.sendMessage(ColorUtils.color(shooterMessage));

        String targetMessage = plugin.getConfigManager().getString("config.yml",
                "messages.perk-grappling-hook-hooked", "&a{player} hooked you with a grappling hook!");
        targetMessage = targetMessage.replace("{player}", shooter.getName());
        target.sendMessage(ColorUtils.color(targetMessage));

        // Sound effects
        shooter.getWorld().playSound(shooter.getLocation(), org.bukkit.Sound.ENTITY_LEASH_KNOT_PLACE, 1.0f, 1.0f);
        target.getWorld().playSound(target.getLocation(), org.bukkit.Sound.ENTITY_LEASH_KNOT_PLACE, 1.0f, 1.0f);
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

    private void tagProjectile(Projectile projectile, String perkType) {
        projectile.getPersistentDataContainer().set(perkEffectKey, PersistentDataType.STRING, perkType);
    }

    private String getProjectilePerkType(Projectile projectile) {
        PersistentDataContainer container = projectile.getPersistentDataContainer();
        return container.get(perkEffectKey, PersistentDataType.STRING);
    }

    private void consumeItem(Player player, ItemStack item) {
        if (item == null || item.getAmount() <= 0) return;

        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        // Verify this is still the same item
        if (itemInHand == null || !itemInHand.equals(item)) return;

        // Consume exactly one item
        if (itemInHand.getAmount() > 1) {
            itemInHand.setAmount(itemInHand.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }
}