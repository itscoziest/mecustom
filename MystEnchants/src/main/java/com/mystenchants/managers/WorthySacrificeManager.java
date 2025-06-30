package com.mystenchants.managers;

import com.mystenchants.MystEnchants;
import com.mystenchants.utils.ColorUtils;
import org.bukkit.entity.Player;
import org.bukkit.entity.Witch;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ENHANCED: Uses ALL configurable values for worthy sacrifice
 */
public class WorthySacrificeManager implements Listener {

    private final MystEnchants plugin;
    private final Map<UUID, Witch> protectiveWitches = new HashMap<>();
    private final Map<UUID, UUID> witchOwners = new HashMap<>();
    private final Map<UUID, BukkitTask> witchTasks = new HashMap<>();

    public WorthySacrificeManager(MystEnchants plugin) {
        this.plugin = plugin;
    }

    public void addProtectiveWitch(Player owner, Witch witch) {
        // Remove existing witch if any
        removeProtectiveWitch(owner);

        // Get configurable values
        int witchHealth = plugin.getPerkManager().getPerkWitchHealth("worthy-sacrifice");
        boolean healthbarEnabled = plugin.getPerkManager().getPerkHealthbarEnabled("worthy-sacrifice");

        // Configure witch
        witch.setTarget(null);
        witch.setAware(true);
        witch.setAI(true);
        witch.setSilent(false);

        // Set configurable health
        witch.setMaxHealth(witchHealth);
        witch.setHealth(witchHealth);

        // Set initial health display if enabled
        if (healthbarEnabled) {
            updateWitchHealthDisplay(witch);
        }

        protectiveWitches.put(owner.getUniqueId(), witch);
        witchOwners.put(witch.getUniqueId(), owner.getUniqueId());

        // Create management task for this witch
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (witch.isDead() || !owner.isOnline()) {
                removeProtectiveWitch(owner);
                return;
            }

            // Prevent witch from targeting the owner
            if (witch.getTarget() == owner) {
                witch.setTarget(null);
            }

            // Update health display if enabled
            if (healthbarEnabled) {
                updateWitchHealthDisplay(witch);
            }

            // Make witch follow player if too far away
            double distance = witch.getLocation().distance(owner.getLocation());
            int followRange = plugin.getPerkManager().getPerk("worthy-sacrifice").getIntProperty("follow-range", 15);

            if (distance > followRange) {
                // Too far - teleport witch to player
                witch.teleport(owner.getLocation().add(2, 0, 0));
            } else if (distance > 5) {
                // Close enough to walk - make witch pathfind to player
                try {
                    org.bukkit.Location targetLoc = owner.getLocation().add(
                            Math.random() * 4 - 2, 0, Math.random() * 4 - 2);
                    witch.getPathfinder().moveTo(targetLoc, 1.0);
                } catch (Exception e) {
                    // Fallback: teleport if pathfinding fails
                    if (distance > 10) {
                        witch.teleport(owner.getLocation().add(2, 0, 0));
                    }
                }
            }

        }, 10L, 10L);

        witchTasks.put(owner.getUniqueId(), task);

        // Use configurable messages
        String spawnedMessage = plugin.getConfigManager().getString("config.yml",
                "messages.perk-worthy-sacrifice-spawned", "&5Guardian witch summoned! It will follow and protect you.");
        owner.sendMessage(ColorUtils.color(spawnedMessage));

        if (healthbarEnabled) {
            String healthMessage = plugin.getConfigManager().getString("config.yml",
                    "messages.perk-worthy-sacrifice-health-display", "&7The witch's health will display above it.");
            owner.sendMessage(ColorUtils.color(healthMessage));
        }
    }

    public void removeProtectiveWitch(Player owner) {
        UUID ownerUUID = owner.getUniqueId();
        Witch existingWitch = protectiveWitches.remove(ownerUUID);

        if (existingWitch != null) {
            witchOwners.remove(existingWitch.getUniqueId());
            if (!existingWitch.isDead()) {
                existingWitch.remove();
            }
        }

        // Cancel management task
        BukkitTask task = witchTasks.remove(ownerUUID);
        if (task != null) {
            task.cancel();
        }
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        Witch protectiveWitch = protectiveWitches.get(player.getUniqueId());

        if (protectiveWitch != null && !protectiveWitch.isDead()) {
            // Get damage transfer percentage from config
            int damageTransfer = plugin.getPerkManager().getPerk("worthy-sacrifice").getIntProperty("damage-transfer", 100);

            if (damageTransfer >= 100) {
                // Cancel damage to player completely
                event.setCancelled(true);
            } else {
                // Reduce damage based on transfer percentage
                double reducedDamage = event.getDamage() * (1.0 - (damageTransfer / 100.0));
                event.setDamage(reducedDamage);
            }

            // Apply damage to witch instead
            double damage = event.getFinalDamage();
            if (protectiveWitch.getHealth() <= damage) {
                // Witch will die
                protectiveWitch.damage(protectiveWitch.getHealth());

                // Use configurable message
                String destroyedMessage = plugin.getConfigManager().getString("config.yml",
                        "messages.perk-worthy-sacrifice-destroyed", "&5Your guardian witch has been destroyed!");
                player.sendMessage(ColorUtils.color(destroyedMessage));

                removeProtectiveWitch(player);
            } else {
                // Witch takes damage
                protectiveWitch.damage(damage);

                // Update health display above witch
                if (plugin.getPerkManager().getPerkHealthbarEnabled("worthy-sacrifice")) {
                    updateWitchHealthDisplay(protectiveWitch);
                }
            }
        }
    }

    private void updateWitchHealthDisplay(Witch witch) {
        double currentHealth = witch.getHealth();
        double maxHealth = witch.getMaxHealth();

        // Get configurable health bar format
        String healthFormat = plugin.getPerkManager().getPerkHealthbarFormat("worthy-sacrifice");

        // Create health bar
        String healthBar = createHealthBar(currentHealth, maxHealth);
        int healthPercent = (int) ((currentHealth / maxHealth) * 100);

        // Apply format with placeholders
        String healthDisplay = healthFormat.replace("{health}", String.valueOf((int)currentHealth))
                .replace("{max-health}", String.valueOf((int)maxHealth))
                .replace("{percentage}", String.valueOf(healthPercent))
                .replace("{bar}", healthBar);

        witch.setCustomName(ColorUtils.color(healthDisplay));
        witch.setCustomNameVisible(true);
    }

    @EventHandler
    public void onWitchDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Witch)) return;

        Witch witch = (Witch) event.getEntity();
        UUID ownerUUID = witchOwners.get(witch.getUniqueId());

        if (ownerUUID != null) {
            protectiveWitches.remove(ownerUUID);
            witchOwners.remove(witch.getUniqueId());

            // Cancel management task
            BukkitTask task = witchTasks.remove(ownerUUID);
            if (task != null) {
                task.cancel();
            }

            Player owner = plugin.getServer().getPlayer(ownerUUID);
            if (owner != null) {
                // Use configurable message
                String fallenMessage = plugin.getConfigManager().getString("config.yml",
                        "messages.perk-worthy-sacrifice-fallen", "&5Your guardian witch has fallen!");
                owner.sendMessage(ColorUtils.color(fallenMessage));
            }
        }
    }

    @EventHandler
    public void onWitchThrowPotion(org.bukkit.event.entity.PotionSplashEvent event) {
        // Prevent protective witches from throwing potions at their owners
        if (!(event.getEntity().getShooter() instanceof Witch)) return;

        Witch witch = (Witch) event.getEntity().getShooter();
        UUID ownerUUID = witchOwners.get(witch.getUniqueId());

        if (ownerUUID != null) {
            Player owner = plugin.getServer().getPlayer(ownerUUID);

            // Check if the owner would be affected by this potion
            if (owner != null && event.getAffectedEntities().contains(owner)) {
                // Remove the owner from affected entities
                event.setIntensity(owner, 0.0);
            }

            // Also prevent witch from targeting the owner
            if (witch.getTarget() == owner) {
                witch.setTarget(null);
            }
        }
    }

    @EventHandler
    public void onEntityTarget(org.bukkit.event.entity.EntityTargetEvent event) {
        // Prevent protective witches from targeting their owners
        if (!(event.getEntity() instanceof Witch)) return;
        if (!(event.getTarget() instanceof Player)) return;

        Witch witch = (Witch) event.getEntity();
        Player target = (Player) event.getTarget();
        UUID ownerUUID = witchOwners.get(witch.getUniqueId());

        if (ownerUUID != null && target.getUniqueId().equals(ownerUUID)) {
            // Cancel targeting of owner
            event.setCancelled(true);
            witch.setTarget(null);
        }
    }

    @EventHandler
    public void onEntityTargetLivingEntity(org.bukkit.event.entity.EntityTargetLivingEntityEvent event) {
        // Additional protection against targeting the owner
        if (!(event.getEntity() instanceof Witch)) return;
        if (!(event.getTarget() instanceof Player)) return;

        Witch witch = (Witch) event.getEntity();
        Player target = (Player) event.getTarget();
        UUID ownerUUID = witchOwners.get(witch.getUniqueId());

        if (ownerUUID != null && target.getUniqueId().equals(ownerUUID)) {
            // Cancel targeting of owner
            event.setCancelled(true);
            witch.setTarget(null);
        }
    }

    private String createHealthBar(double current, double max) {
        int barLength = 20;
        int filledBars = (int) ((current / max) * barLength);

        StringBuilder bar = new StringBuilder("&c");
        for (int i = 0; i < barLength; i++) {
            if (i < filledBars) {
                bar.append("█");
            } else {
                bar.append("░");
            }
        }

        return bar.toString();
    }
}