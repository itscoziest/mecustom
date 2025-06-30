package com.mystenchants.listeners;

import com.mystenchants.MystEnchants;
import com.mystenchants.enchants.CustomEnchant;
import com.mystenchants.utils.ColorUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * COMPILATION FIXED: Handles player interactions for perk usage and enchant abilities
 * Key fix: Correct Particle enum names for 1.20.4
 */
public class PlayerInteractListener implements Listener {

    private final MystEnchants plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final NamespacedKey perkEffectKey;

    public PlayerInteractListener(MystEnchants plugin) {
        this.plugin = plugin;
        this.perkEffectKey = new NamespacedKey(plugin, "perk_effect");
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null) return;

        // Only handle right-click actions for perks
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // Handle perk usage from shop purchase
        if (plugin.getPerkManager().isPerkItem(item)) {
            String perkName = plugin.getPerkManager().getPerkName(item);

            if (perkName != null) {
                event.setCancelled(true); // Cancel functional items
                handlePerkUsage(player, perkName, item);
                return;
            }
        }

        // Handle perk effect items (the actual functional items)
        if (isPerkEffectItem(item)) {
            String perkType = getPerkEffectType(item);

            if (perkType != null) {
                // Handle grappling hook specifically
                if (perkType.equals("grappling-hook")) {
                    handleGrapplingHookCast(player, item, event);
                    return;
                }

                // CRITICAL FIX: Only cancel non-throwable items
                boolean isThrowable = isThrowablePerk(perkType);

                if (!isThrowable) {
                    event.setCancelled(true); // Only cancel right-click items
                }

                handlePerkEffectUsage(player, perkType, item, isThrowable);
                return;
            }
        }

        // Handle enchant abilities (like Almighty Push)
        if (plugin.getEnchantManager().hasCustomEnchant(item)) {
            CustomEnchant enchant = plugin.getEnchantManager().getCustomEnchant(item);
            if (enchant != null && enchant.getName().equals("almighty_push")) {
                event.setCancelled(true);
                handleAlmightyPush(player);
            }
        }
    }

    private void handleGrapplingHookCast(Player player, ItemStack item, PlayerInteractEvent event) {
        // Cancel the event to prevent normal fishing
        event.setCancelled(true);

        // Check cooldown
        UUID playerUUID = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        int cooldownTime = plugin.getConfigManager().getInt("perks.yml", "perks.grappling-hook.cooldown", 45) * 1000;

        if (cooldowns.containsKey(playerUUID)) {
            long timeLeft = (cooldowns.get(playerUUID) + cooldownTime) - currentTime;
            if (timeLeft > 0) {
                player.sendMessage(ColorUtils.color("&cGrappling hook is on cooldown for " + (timeLeft / 1000) + " seconds!"));
                return;
            }
        }

        // Look for nearby players to hook
        double maxDistance = plugin.getConfigManager().getDouble("perks.yml", "perks.grappling-hook.max-distance", 30);
        Player closestPlayer = null;
        double closestDistance = maxDistance;

        for (Entity entity : player.getNearbyEntities(maxDistance, maxDistance, maxDistance)) {
            if (entity instanceof Player && !entity.equals(player)) {
                double distance = player.getLocation().distance(entity.getLocation());
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestPlayer = (Player) entity;
                }
            }
        }

        if (closestPlayer != null) {
            // Set cooldown
            cooldowns.put(playerUUID, currentTime);

            // Apply grappling hook effect
            handleGrapplingHook(player, closestPlayer);

            // FIXED: DON'T consume the grappling hook - it's reusable with cooldown
            // consumeItem(player, item); // REMOVED THIS LINE

            player.sendMessage(ColorUtils.color("&7Grappling hook will be ready again in " + (cooldownTime/1000) + " seconds."));
        } else {
            player.sendMessage(ColorUtils.color("&cNo players in range to grapple!"));
        }
    }

    private void handleGrapplingHook(Player shooter, Player target) {
        plugin.getLogger().info("Grappling hook activated: " + shooter.getName() + " -> " + target.getName());

        double pullStrength = plugin.getConfigManager().getDouble("perks.yml", "perks.grappling-hook.pull-strength", 2.0);

        // Calculate direction and pull target towards shooter
        Vector direction = shooter.getLocation().toVector().subtract(target.getLocation().toVector()).normalize();
        direction.multiply(pullStrength);
        direction.setY(Math.max(direction.getY(), 0.5)); // Ensure some upward movement

        target.setVelocity(direction);

        // Messages
        shooter.sendMessage(ColorUtils.color("&aYou hooked " + target.getName() + "!"));
        target.sendMessage(ColorUtils.color("&a" + shooter.getName() + " hooked you with a grappling hook!"));

        // Sound and visual effects
        shooter.getWorld().playSound(shooter.getLocation(), org.bukkit.Sound.ENTITY_LEASH_KNOT_PLACE, 1.0f, 1.0f);
        target.getWorld().playSound(target.getLocation(), org.bukkit.Sound.ENTITY_LEASH_KNOT_PLACE, 1.0f, 1.0f);

        // FIXED: Use correct particle names for 1.20.4
        shooter.getWorld().spawnParticle(org.bukkit.Particle.BUBBLE_COLUMN_UP,
                shooter.getLocation().add(0, 1, 0), 10, 0.1, 0.1, 0.1, 0.1);
        target.getWorld().spawnParticle(org.bukkit.Particle.BUBBLE_COLUMN_UP,
                target.getLocation().add(0, 1, 0), 10, 0.1, 0.1, 0.1, 0.1);
    }

    private boolean isThrowablePerk(String perkType) {
        return perkType.equals("teleport-snowball") || perkType.equals("tradeoff-egg");
    }

    private void handlePerkUsage(Player player, String perkName, ItemStack item) {
        plugin.getPerkManager().usePerk(player, perkName).thenAccept(success -> {
            if (success) {
                // Remove item from inventory
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                } else {
                    player.getInventory().setItem(player.getInventory().getHeldItemSlot(), null);
                }

                // Execute perk effect - give the functional item
                executePerkEffect(player, perkName);
            }
        }).exceptionally(throwable -> {
            plugin.getLogger().warning("Error using perk " + perkName + ": " + throwable.getMessage());
            return null;
        });
    }

    private void handlePerkEffectUsage(Player player, String perkType, ItemStack item, boolean isThrowable) {
        switch (perkType) {
            case "worthy-sacrifice":
                spawnProtectiveWitch(player);
                consumeItem(player, item);
                break;
            case "snowman-egg":
                spawnAttackingSnowman(player);
                consumeItem(player, item);
                break;
            case "teleport-snowball":
                // Don't consume here - let it be thrown naturally
                player.sendMessage(ColorUtils.color("&bThrow the snowball at a player to teleport!"));
                break;
            case "tradeoff-egg":
                // Don't consume here - let it be thrown naturally
                player.sendMessage(ColorUtils.color("&eThrow the egg at a player to swap effects!"));
                break;
            case "grappling-hook":
                // This is now handled in handleGrapplingHookCast
                break;
            case "spellbreaker":
                player.sendMessage(ColorUtils.color("&6Hit players to remove their effects!"));
                break;
            case "lovestruck":
                player.sendMessage(ColorUtils.color("&dHit players to make them lovestruck!"));
                break;
        }
    }

    private void handleAlmightyPush(Player player) {
        UUID playerUUID = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        // Check cooldown (30 seconds)
        if (cooldowns.containsKey(playerUUID)) {
            long timeLeft = (cooldowns.get(playerUUID) + 30000) - currentTime;
            if (timeLeft > 0) {
                player.sendMessage(ColorUtils.color("&cAlmighty Push is on cooldown for " + (timeLeft / 1000) + " seconds!"));
                return;
            }
        }

        // Set cooldown
        cooldowns.put(playerUUID, currentTime);

        // Push nearby entities away
        Location playerLoc = player.getLocation();
        double radius = 10.0; // 10 block radius
        double pushStrength = 3.0;

        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof Player && !entity.equals(player)) {
                Vector direction = entity.getLocation().toVector().subtract(playerLoc.toVector()).normalize();
                direction.setY(0.5); // Add upward component
                direction.multiply(pushStrength);

                entity.setVelocity(direction);
            }
        }

        player.sendMessage(ColorUtils.color("&6&lALMIGHTY PUSH! &7Nearby players have been blasted away!"));

        // Visual/sound effects
        player.getWorld().createExplosion(playerLoc, 0, false, false);
    }

    private void executePerkEffect(Player player, String perkName) {
        switch (perkName) {
            case "teleport-snowball":
                player.sendMessage(ColorUtils.color("&bTeleport Snowball activated! Throw it at a player to teleport!"));
                giveEffectItem(player, Material.SNOWBALL, "&bTeleport Snowball", "teleport-snowball");
                break;
            case "grappling-hook":
                player.sendMessage(ColorUtils.color("&aGrappling Hook activated! Right-click to hook nearby players!"));
                giveEffectItem(player, Material.FISHING_ROD, "&aGrappling Hook", "grappling-hook");
                break;
            case "snowman-egg":
                player.sendMessage(ColorUtils.color("&fSnowman Egg activated! Right-click to spawn an attacking snowman!"));
                giveEffectItem(player, Material.PUMPKIN, "&fSnowman Egg", "snowman-egg");
                break;
            case "spellbreaker":
                player.sendMessage(ColorUtils.color("&6Spellbreaker activated! Hit players to remove their effects!"));
                giveEffectItem(player, Material.BLAZE_ROD, "&6Spellbreaker", "spellbreaker");
                break;
            case "tradeoff-egg":
                player.sendMessage(ColorUtils.color("&eTradeoff Egg activated! Throw it to switch effects with target!"));
                giveEffectItem(player, Material.EGG, "&eTradeoff Egg", "tradeoff-egg");
                break;
            case "worthy-sacrifice":
                player.sendMessage(ColorUtils.color("&5Worthy Sacrifice activated! Right-click to spawn a protective witch!"));
                giveEffectItem(player, Material.WITCH_SPAWN_EGG, "&5Worthy Sacrifice", "worthy-sacrifice");
                break;
            case "lovestruck":
                player.sendMessage(ColorUtils.color("&dLovestruck activated! Hit players to give them nausea and roses!"));
                giveEffectItem(player, Material.ROSE_BUSH, "&dLovestruck", "lovestruck");
                break;
            default:
                player.sendMessage(ColorUtils.color("&aUsed perk: " + perkName));
                break;
        }
    }

    private void giveEffectItem(Player player, Material material, String name, String perkType) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ColorUtils.color(name));

            // Add usage instructions to lore
            java.util.List<String> lore = new java.util.ArrayList<>();
            switch (perkType) {
                case "teleport-snowball":
                    lore.add(ColorUtils.color("&7Throw at a player to teleport to them"));
                    break;
                case "tradeoff-egg":
                    lore.add(ColorUtils.color("&7Throw at a player to swap potion effects"));
                    break;
                case "grappling-hook":
                    lore.add(ColorUtils.color("&7Right-click to hook nearby players"));
                    break;
                case "spellbreaker":
                    lore.add(ColorUtils.color("&7Hit players 5 times to remove their effects"));
                    break;
                case "lovestruck":
                    lore.add(ColorUtils.color("&7Hit players 5 times to make them lovestruck"));
                    break;
                case "snowman-egg":
                    lore.add(ColorUtils.color("&7Right-click to spawn attacking snowman"));
                    break;
                case "worthy-sacrifice":
                    lore.add(ColorUtils.color("&7Right-click to spawn protective witch"));
                    break;
            }
            meta.setLore(lore);

            // Add perk effect identifier
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(perkEffectKey, PersistentDataType.STRING, perkType);

            item.setItemMeta(meta);
        }

        player.getInventory().addItem(item);
    }

    private void consumeItem(Player player, ItemStack item) {
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItem(player.getInventory().getHeldItemSlot(), null);
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

    private void spawnProtectiveWitch(Player player) {
        Location spawnLoc = player.getLocation().add(2, 0, 0);
        org.bukkit.entity.Witch witch = (org.bukkit.entity.Witch) player.getWorld().spawnEntity(spawnLoc, org.bukkit.entity.EntityType.WITCH);

        // Configure witch to follow player but not attack them
        witch.setRemoveWhenFarAway(false);

        // Set high health
        int health = plugin.getConfigManager().getInt("perks.yml", "perks.worthy-sacrifice.witch-health", 300);
        witch.setMaxHealth(health);
        witch.setHealth(health);

        // Allow movement but prevent attacking the owner
        witch.setTarget(null); // Clear initial target
        witch.setAware(true); // Allow pathfinding
        witch.setAI(true); // Allow AI but we'll control targeting
        witch.setCollidable(true); // Make it solid again

        // The WorthySacrificeManager will handle preventing attacks on owner
        plugin.getWorthySacrificeManager().addProtectiveWitch(player, witch);

        player.sendMessage(ColorUtils.color("&5Guardian witch summoned! It will follow and protect you."));
        player.sendMessage(ColorUtils.color("&7The witch's health will display above it."));
    }

    private void spawnAttackingSnowman(Player player) {
        Location spawnLoc = player.getLocation().add(1, 0, 1);
        org.bukkit.entity.Snowman snowman = (org.bukkit.entity.Snowman) player.getWorld().spawnEntity(spawnLoc, org.bukkit.entity.EntityType.SNOWMAN);

        // Configure snowman
        snowman.setCustomName(ColorUtils.color("&f" + player.getName() + "'s Snowman"));
        snowman.setCustomNameVisible(true);
        snowman.setRemoveWhenFarAway(false);

        // Set duration and effects
        int duration = plugin.getConfigManager().getInt("perks.yml", "perks.snowman-egg.duration", 15);

        // Store snowman data for attacking behavior
        plugin.getSnowmanManager().addAttackingSnowman(player, snowman, duration);

        player.sendMessage(ColorUtils.color("&fAttacking snowman summoned! It will attack nearby enemies for " + duration + " seconds."));
    }
}