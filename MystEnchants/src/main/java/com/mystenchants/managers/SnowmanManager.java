package com.mystenchants.managers;

import com.mystenchants.MystEnchants;
import com.mystenchants.utils.ColorUtils;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Snowman;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ENHANCED: Uses ALL configurable values for snowman egg perk
 */
public class SnowmanManager implements Listener {

    private final MystEnchants plugin;
    private final Map<UUID, SnowmanData> activeSnowmen = new HashMap<>();
    private final Map<UUID, UUID> snowmanOwners = new HashMap<>();

    public SnowmanManager(MystEnchants plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void addAttackingSnowman(Player owner, Snowman snowman, int duration) {
        removeSnowman(owner);

        // Get configurable values
        int configuredDuration = plugin.getPerkManager().getPerkDuration("snowman-egg");
        int snowmanHealth = plugin.getPerkManager().getPerk("snowman-egg").getIntProperty("snowman-health", 20);
        double attackInterval = plugin.getPerkManager().getPerk("snowman-egg").getDoubleProperty("attack-interval", 1.5);

        // Use configured duration instead of parameter
        duration = configuredDuration;

        // Configure snowman
        snowman.setTarget(null);
        snowman.setAware(false);
        snowman.setAI(false);
        snowman.setMaxHealth(snowmanHealth);
        snowman.setHealth(snowmanHealth);

        snowmanOwners.put(snowman.getUniqueId(), owner.getUniqueId());

        // Attack task using configurable interval
        BukkitTask attackTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (snowman.isDead() || !owner.isOnline()) {
                removeSnowman(owner);
                return;
            }
            manuallyAttackEnemyPlayers(owner, snowman);
        }, 20L, (long)(attackInterval * 20)); // Convert seconds to ticks

        // Removal task using configurable duration
        BukkitTask removalTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            removeSnowman(owner);
            if (owner.isOnline()) {
                // Use configurable message
                String expiredMessage = plugin.getConfigManager().getString("config.yml",
                        "messages.perk-snowman-expired", "&fYour snowman has expired.");
                owner.sendMessage(ColorUtils.color(expiredMessage));
            }
        }, duration * 20L);

        SnowmanData data = new SnowmanData(snowman, attackTask, removalTask);
        activeSnowmen.put(owner.getUniqueId(), data);

        // Use configurable spawn message
        String spawnMessage = plugin.getConfigManager().getString("config.yml",
                "messages.perk-snowman-spawned", "&fAttacking snowman summoned for {duration} seconds!");
        spawnMessage = spawnMessage.replace("{duration}", String.valueOf(duration));
        owner.sendMessage(ColorUtils.color(spawnMessage));
    }

    @EventHandler
    public void onSnowmanTarget(EntityTargetEvent event) {
        if (!(event.getEntity() instanceof Snowman)) return;

        Snowman snowman = (Snowman) event.getEntity();
        UUID ownerUUID = snowmanOwners.get(snowman.getUniqueId());

        if (ownerUUID != null) {
            event.setCancelled(true);
            snowman.setTarget(null);
        }
    }

    public void removeSnowman(Player owner) {
        SnowmanData data = activeSnowmen.remove(owner.getUniqueId());
        if (data != null) {
            snowmanOwners.remove(data.snowman.getUniqueId());
            data.attackTask.cancel();
            data.removalTask.cancel();
            if (!data.snowman.isDead()) {
                data.snowman.remove();
            }
        }
    }

    private void manuallyAttackEnemyPlayers(Player owner, Snowman snowman) {
        // Get configurable attack range
        int attackRange = plugin.getPerkManager().getPerkAttackRange("snowman-egg");

        Player closestTarget = null;
        double closestDistance = Double.MAX_VALUE;

        for (Entity entity : snowman.getNearbyEntities(attackRange, attackRange, attackRange)) {
            if (entity instanceof Player && !entity.equals(owner)) {
                Player potentialTarget = (Player) entity;
                double distance = snowman.getLocation().distance(potentialTarget.getLocation());

                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestTarget = potentialTarget;
                }
            }
        }

        if (closestTarget != null) {
            manuallyShootAtPlayer(owner, snowman, closestTarget);
        }
    }

    private void manuallyShootAtPlayer(Player owner, Snowman snowman, Player target) {
        org.bukkit.Location snowmanLoc = snowman.getLocation();
        org.bukkit.Location targetLoc = target.getLocation();

        org.bukkit.util.Vector lookDirection = targetLoc.toVector().subtract(snowmanLoc.toVector());
        lookDirection.setY(0);
        snowmanLoc.setDirection(lookDirection);
        snowman.teleport(snowmanLoc);

        Snowball snowball = snowman.launchProjectile(Snowball.class);

        org.bukkit.util.Vector velocity = target.getLocation().add(0, 1, 0).toVector()
                .subtract(snowman.getLocation().add(0, 1.5, 0).toVector()).normalize();
        velocity.multiply(1.2);

        snowball.setVelocity(velocity);

        snowman.getWorld().playSound(snowman.getLocation(), org.bukkit.Sound.ENTITY_SNOWBALL_THROW, 1.0f, 1.0f);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (target.isOnline() &&
                    target.getLocation().distance(snowball.getLocation()) < 2.5) {

                // Get configurable slow effect values
                int slowDuration = plugin.getPerkManager().getPerkSlowDuration("snowman-egg");
                int slowAmplifier = plugin.getPerkManager().getPerkSlowAmplifier("snowman-egg");

                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, slowDuration * 20, slowAmplifier - 1));
                target.damage(2.0, snowman);

                // Use configurable hit messages
                String targetMessage = plugin.getConfigManager().getString("config.yml",
                        "messages.perk-snowman-hit-by", "&bYou were hit by {player}'s snowman!");
                targetMessage = targetMessage.replace("{player}", owner.getName());
                target.sendMessage(ColorUtils.color(targetMessage));

                if (owner.isOnline()) {
                    String ownerMessage = plugin.getConfigManager().getString("config.yml",
                            "messages.perk-snowman-hit", "&fYour snowman hit {player}!");
                    ownerMessage = ownerMessage.replace("{player}", target.getName());
                    owner.sendMessage(ColorUtils.color(ownerMessage));
                }

                target.getWorld().spawnParticle(org.bukkit.Particle.SNOWBALL, target.getLocation().add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0.1);
                target.getWorld().playSound(target.getLocation(), org.bukkit.Sound.ENTITY_SNOWBALL_THROW, 1.0f, 0.5f);
            }
        }, 25L);
    }

    public void cleanupPlayerSnowmen(Player player) {
        removeSnowman(player);
    }

    public Snowman getPlayerSnowman(Player player) {
        SnowmanData data = activeSnowmen.get(player.getUniqueId());
        return data != null ? data.snowman : null;
    }

    public boolean hasActiveSnowman(Player player) {
        SnowmanData data = activeSnowmen.get(player.getUniqueId());
        return data != null && !data.snowman.isDead();
    }

    private static class SnowmanData {
        final Snowman snowman;
        final BukkitTask attackTask;
        final BukkitTask removalTask;

        SnowmanData(Snowman snowman, BukkitTask attackTask, BukkitTask removalTask) {
            this.snowman = snowman;
            this.attackTask = attackTask;
            this.removalTask = removalTask;
        }
    }
}