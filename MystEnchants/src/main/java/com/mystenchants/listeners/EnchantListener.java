package com.mystenchants.listeners;

import com.mystenchants.MystEnchants;
import com.mystenchants.enchants.CustomEnchant;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * FIXED: Handles passive enchant effects with immediate application/removal
 */
public class EnchantListener implements Listener {

    private final MystEnchants plugin;

    public EnchantListener(MystEnchants plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemHeld(PlayerItemHeldEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();

        // Apply effects immediately and after a small delay for safety
        applyPassiveEffects(player);
        new BukkitRunnable() {
            @Override
            public void run() {
                applyPassiveEffects(player);
            }
        }.runTaskLater(plugin, 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Apply effects when player joins
        new BukkitRunnable() {
            @Override
            public void run() {
                applyPassiveEffects(player);
            }
        }.runTaskLater(plugin, 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        // Check if armor was changed or main hand item was moved
        if (isArmorSlot(event) || isMainHandSlot(event, player)) {
            // Apply effects immediately and after delay
            new BukkitRunnable() {
                @Override
                public void run() {
                    applyPassiveEffects(player);
                }
            }.runTaskLater(plugin, 1L);

            new BukkitRunnable() {
                @Override
                public void run() {
                    applyPassiveEffects(player);
                }
            }.runTaskLater(plugin, 3L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();

        // Apply effects immediately when swapping items
        new BukkitRunnable() {
            @Override
            public void run() {
                applyPassiveEffects(player);
            }
        }.runTaskLater(plugin, 1L);
    }

    /**
     * Checks if the clicked slot is an armor slot
     */
    private boolean isArmorSlot(InventoryClickEvent event) {
        return event.getSlotType() == InventoryType.SlotType.ARMOR ||
                (event.getSlot() >= 36 && event.getSlot() <= 39); // Armor slots in player inventory
    }

    /**
     * Checks if the clicked slot is the main hand slot
     */
    private boolean isMainHandSlot(InventoryClickEvent event, Player player) {
        return event.getSlot() == player.getInventory().getHeldItemSlot() + 36; // Convert to raw slot
    }

    /**
     * FIXED: Applies all passive enchant effects to a player with immediate effect
     */
    public void applyPassiveEffects(Player player) {
        // Clear ALL custom effects first to ensure clean state
        removeCustomEffects(player);

        // FIXED: Apply Tempo (haste) from tools - check specifically for tempo
        checkAndApplyTempo(player);

        // FIXED: Apply Pace (speed) from boots - check specifically for pace
        checkAndApplyPace(player);

        // FIXED: Apply Zetsubo (strength) - requires full armor set with zetsubo
        checkAndApplyZetsubo(player);
    }

    /**
     * Removes only the effects that our enchants provide
     */
    private void removeCustomEffects(Player player) {
        // Only remove effects if they're infinite duration (our custom effects)
        if (player.hasPotionEffect(PotionEffectType.FAST_DIGGING)) {
            PotionEffect effect = player.getPotionEffect(PotionEffectType.FAST_DIGGING);
            if (effect != null && effect.getDuration() > 999999) {
                player.removePotionEffect(PotionEffectType.FAST_DIGGING);
            }
        }

        if (player.hasPotionEffect(PotionEffectType.SPEED)) {
            PotionEffect effect = player.getPotionEffect(PotionEffectType.SPEED);
            if (effect != null && effect.getDuration() > 999999) {
                player.removePotionEffect(PotionEffectType.SPEED);
            }
        }

        if (player.hasPotionEffect(PotionEffectType.INCREASE_DAMAGE)) {
            PotionEffect effect = player.getPotionEffect(PotionEffectType.INCREASE_DAMAGE);
            if (effect != null && effect.getDuration() > 999999) {
                player.removePotionEffect(PotionEffectType.INCREASE_DAMAGE);
            }
        }
    }

    private void checkAndApplyTempo(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();

        // FIXED: Check specifically for Tempo enchant
        if (mainHand != null && plugin.getEnchantManager().hasSpecificCustomEnchant(mainHand, "tempo")) {
            int level = plugin.getEnchantManager().getSpecificCustomEnchantLevel(mainHand, "tempo");
            int hasteLevel = plugin.getEnchantManager().getTempoHasteLevel(level);

            plugin.getLogger().info("TEMPO ENCHANT DETECTED! Level: " + level + ", Haste Level: " + hasteLevel + " on " + mainHand.getType());

            // Apply infinite duration haste effect
            player.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING,
                    Integer.MAX_VALUE, hasteLevel - 1, false, false, false));
        }
    }

    private void checkAndApplyPace(Player player) {
        ItemStack boots = player.getInventory().getBoots();

        // FIXED: Check specifically for Pace enchant
        if (boots != null && plugin.getEnchantManager().hasSpecificCustomEnchant(boots, "pace")) {
            int level = plugin.getEnchantManager().getSpecificCustomEnchantLevel(boots, "pace");
            int speedLevel = plugin.getEnchantManager().getPaceSpeedLevel(level);

            plugin.getLogger().info("PACE ENCHANT DETECTED! Level: " + level + ", Speed Level: " + speedLevel + " on " + boots.getType());

            // Apply infinite duration speed effect
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                    Integer.MAX_VALUE, speedLevel - 1, false, false, false));
        }
    }

    private void checkAndApplyZetsubo(Player player) {
        ItemStack helmet = player.getInventory().getHelmet();
        ItemStack chestplate = player.getInventory().getChestplate();
        ItemStack leggings = player.getInventory().getLeggings();
        ItemStack boots = player.getInventory().getBoots();

        // FIXED: Check if all armor pieces have Zetsubo enchant specifically
        if (hasZetsuboEnchant(helmet) && hasZetsuboEnchant(chestplate) &&
                hasZetsuboEnchant(leggings) && hasZetsuboEnchant(boots)) {

            // Get the minimum level across all pieces
            int minLevel = Math.min(
                    Math.min(getZetsuboLevel(helmet), getZetsuboLevel(chestplate)),
                    Math.min(getZetsuboLevel(leggings), getZetsuboLevel(boots))
            );

            if (minLevel > 0) {
                int strengthLevel = plugin.getEnchantManager().getZetsuboStrengthLevel(minLevel);

                plugin.getLogger().info("ZETSUBO FULL SET DETECTED! Min Level: " + minLevel + ", Strength Level: " + strengthLevel);

                // Apply infinite duration strength effect
                player.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE,
                        Integer.MAX_VALUE, strengthLevel - 1, false, false, false));
            }
        }
    }

    private boolean hasZetsuboEnchant(ItemStack item) {
        if (item == null) return false;

        // FIXED: Check specifically for Zetsubo enchant
        return plugin.getEnchantManager().hasSpecificCustomEnchant(item, "zetsubo");
    }

    private int getZetsuboLevel(ItemStack item) {
        if (!hasZetsuboEnchant(item)) return 0;
        return plugin.getEnchantManager().getSpecificCustomEnchantLevel(item, "zetsubo");
    }

    /**
     * Public method to manually refresh effects (can be called from other listeners)
     */
    public void refreshPlayerEffects(Player player) {
        applyPassiveEffects(player);
    }
}