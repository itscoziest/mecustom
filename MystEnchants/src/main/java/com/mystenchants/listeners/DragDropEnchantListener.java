package com.mystenchants.listeners;

import com.mystenchants.MystEnchants;
import com.mystenchants.enchants.CustomEnchant;
import com.mystenchants.utils.ColorUtils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * WORKING: Drag and drop enchant listener based on your successful old code
 * Fixed to handle existing enchants and proper replacement logic
 */
public class DragDropEnchantListener implements Listener {

    private final MystEnchants plugin;
    private final NamespacedKey enchantKey;
    private final NamespacedKey levelKey;

    // Spam prevention for enchant apply messages
    private final Map<UUID, Long> lastEnchantApply = new HashMap<>();
    private static final long ENCHANT_APPLY_COOLDOWN = 1000; // 1 second cooldown

    public DragDropEnchantListener(MystEnchants plugin) {
        this.plugin = plugin;
        this.enchantKey = new NamespacedKey(plugin, "custom_enchant");
        this.levelKey = new NamespacedKey(plugin, "enchant_level");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();

        // Debug logging
        plugin.getLogger().info("=== DRAG DROP DEBUG ===");
        plugin.getLogger().info("Player: " + player.getName());
        plugin.getLogger().info("Action: " + event.getAction());
        plugin.getLogger().info("Cursor: " + (cursor != null ? cursor.getType() + " - " + (cursor.hasItemMeta() ? cursor.getItemMeta().getDisplayName() : "no meta") : "null"));
        plugin.getLogger().info("Current: " + (current != null ? current.getType() : "null"));

        if (cursor == null) {
            plugin.getLogger().info("Cursor is null, returning");
            return;
        }

        if (!isCustomEnchantDye(cursor)) {
            plugin.getLogger().info("Cursor is not a custom enchant dye");
            return;
        }

        plugin.getLogger().info("DETECTED ENCHANT DYE IN CURSOR!");

        if (current == null || current.getType() == Material.AIR) {
            plugin.getLogger().info("Current item is null or air, returning");
            return;
        }

        // CRITICAL: These are the actions that occur during drag/drop operations
        if (event.getAction() == InventoryAction.SWAP_WITH_CURSOR ||
                event.getAction() == InventoryAction.PLACE_ALL ||
                event.getAction() == InventoryAction.PLACE_ONE ||
                event.getAction() == InventoryAction.PLACE_SOME) {

            plugin.getLogger().info("Valid drag/drop action detected: " + event.getAction());

            CustomEnchant enchant = getEnchantFromDye(cursor);
            int level = getEnchantLevel(cursor);

            plugin.getLogger().info("Enchant: " + (enchant != null ? enchant.getName() : "null"));
            plugin.getLogger().info("Level: " + level);

            // FIXED: Pass level to canApplyToItem method
            if (enchant != null && canApplyToItemWithLevel(enchant, level, current)) {
                plugin.getLogger().info("Can apply enchant to item - proceeding with application");
                event.setCancelled(true);

                // Spam prevention
                UUID playerUUID = player.getUniqueId();
                long currentTime = System.currentTimeMillis();
                Long lastApply = lastEnchantApply.get(playerUUID);

                if (lastApply != null && (currentTime - lastApply) < ENCHANT_APPLY_COOLDOWN) {
                    plugin.getLogger().info("Spam prevention triggered - ignoring request");
                    return;
                }

                lastEnchantApply.put(playerUUID, currentTime);

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    applyEnchantToItem(player, enchant, level, current, event.getSlot());
                    consumeDye(player, cursor);
                });

            } else if (enchant != null) {
                plugin.getLogger().info("Cannot apply enchant to item - showing error message");
                event.setCancelled(true);
                showIncompatibleMessage(player, enchant, current);
            } else {
                plugin.getLogger().warning("Enchant was null despite being detected as enchant dye");
            }
        } else {
            plugin.getLogger().info("Action not relevant for drag/drop: " + event.getAction());
        }

        plugin.getLogger().info("=== END DRAG DROP DEBUG ===");
    }

    /**
     * NEW: Enhanced version that receives the level parameter
     */
    private boolean canApplyToItemWithLevel(CustomEnchant enchant, int newLevel, ItemStack item) {
        if (item.getType() == Material.AIR) {
            plugin.getLogger().info("Cannot apply to air");
            return false;
        }

        if (!enchant.isApplicableTo(item.getType())) {
            plugin.getLogger().info("Enchant " + enchant.getName() + " not applicable to " + item.getType());
            plugin.getLogger().info("Applicable items: " + enchant.getApplicableItems());
            return false;
        }

        // FIXED: Handle existing enchants with proper upgrade logic
        if (plugin.getEnchantManager().hasCustomEnchant(item)) {
            CustomEnchant existingEnchant = plugin.getEnchantManager().getCustomEnchant(item);
            int existingLevel = plugin.getEnchantManager().getCustomEnchantLevel(item);

            plugin.getLogger().info("Item has existing enchant: " + existingEnchant.getName() + " Level " + existingLevel);

            if (existingEnchant != null && existingEnchant.getName().equals(enchant.getName())) {
                // SAME enchant - check levels properly
                plugin.getLogger().info("Same enchant detected. Existing: Level " + existingLevel + ", Applying: Level " + newLevel);

                if (newLevel > existingLevel) {
                    plugin.getLogger().info("ALLOWING UPGRADE from Level " + existingLevel + " to Level " + newLevel);
                    return true; // Allow upgrade
                } else if (newLevel == existingLevel) {
                    plugin.getLogger().info("BLOCKING - same level (" + newLevel + ")");
                    return false; // Block same level
                } else {
                    plugin.getLogger().info("BLOCKING - downgrade attempt (Level " + existingLevel + " to Level " + newLevel + ")");
                    return false; // Block downgrade
                }
            } else {
                // DIFFERENT enchant - always allow replacement
                plugin.getLogger().info("Different enchant detected - ALLOWING replacement of " + existingEnchant.getName() + " with " + enchant.getName());
                return true;
            }
        }

        plugin.getLogger().info("No existing enchant - can apply");
        return true;
    }

    /**
     * FIXED: Properly detect enchant dyes vs enchanted items
     * This prevents enchanted swords from being treated as consumable dyes
     */
    private boolean isCustomEnchantDye(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        boolean hasEnchantKey = container.has(enchantKey, PersistentDataType.STRING);
        boolean hasLevelKey = container.has(levelKey, PersistentDataType.INTEGER);

        plugin.getLogger().info("Checking enchant dye - Has enchant key: " + hasEnchantKey + ", Has level key: " + hasLevelKey);
        plugin.getLogger().info("Item type: " + item.getType() + ", Display name: " + (meta.hasDisplayName() ? meta.getDisplayName() : "none"));

        if (!hasEnchantKey) {
            plugin.getLogger().info("No enchant key - not an enchant dye");
            return false;
        }

        // CRITICAL FIX: Check if this is actually a DYE item, not an enchanted weapon/armor
        if (isDyeMaterial(item.getType())) {
            plugin.getLogger().info("Item is a dye material - this is an enchant dye");
            return true;
        } else {
            plugin.getLogger().info("Item is NOT a dye material (" + item.getType() + ") - this is an enchanted item, not a dye");
            return false;
        }
    }

    /**
     * NEW: Check if the material is actually a dye (enchant books are stored as dyes)
     */
    private boolean isDyeMaterial(Material material) {
        // These are the materials used for enchant dyes based on tiers
        switch (material) {
            case LIGHT_BLUE_DYE:    // Common enchants
            case LIME_DYE:          // Uncommon enchants
            case YELLOW_DYE:        // Rare enchants
            case ORANGE_DYE:        // Ultimate enchants
            case RED_DYE:           // Legendary enchants
            case PINK_DYE:          // Mystical enchants
                return true;
            default:
                return false;
        }
    }

    private CustomEnchant getEnchantFromDye(ItemStack dye) {
        ItemMeta meta = dye.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        String enchantName = container.get(enchantKey, PersistentDataType.STRING);
        return plugin.getEnchantManager().getEnchant(enchantName);
    }

    private int getEnchantLevel(ItemStack dye) {
        ItemMeta meta = dye.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.getOrDefault(levelKey, PersistentDataType.INTEGER, 1);
    }

    /**
     * KEEP: Old method for compatibility, but redirect to new method
     */
    private boolean canApplyToItem(CustomEnchant enchant, ItemStack item) {
        // Fallback to level 1 if called without level
        return canApplyToItemWithLevel(enchant, 1, item);
    }

    /**
     * NEW: Get enchant level from the dye currently being processed
     * This is a helper method to get the level from the cursor item during drag/drop
     */
    private int getEnchantLevelFromCurrentDye() {
        // We need to access the current cursor during the event processing
        // Since we can't easily pass the cursor here, we'll use a different approach
        return 1; // Default fallback - will be fixed in the calling method
    }

    /**
     * Helper to get current cursor (for level checking)
     */
    private ItemStack getCurrentCursor() {
        // This is a bit of a hack, but we need access to the cursor for level checking
        // In practice, this should be passed as a parameter, but maintaining compatibility
        return null; // Will default to level 1 in getEnchantLevel if null
    }

    /**
     * ENHANCED: Apply enchant with proper level handling
     */
    private void applyEnchantToItem(Player player, CustomEnchant enchant, int level, ItemStack targetItem, int slot) {
        plugin.getLogger().info("Applying enchant " + enchant.getName() + " Level " + level + " to " + targetItem.getType());

        // ENHANCED: Check for existing enchant and handle upgrades
        if (plugin.getEnchantManager().hasCustomEnchant(targetItem)) {
            CustomEnchant existingEnchant = plugin.getEnchantManager().getCustomEnchant(targetItem);
            int existingLevel = plugin.getEnchantManager().getCustomEnchantLevel(targetItem);

            if (existingEnchant != null && existingEnchant.getName().equals(enchant.getName())) {
                // Same enchant - check if upgrading
                if (level > existingLevel) {
                    plugin.getLogger().info("UPGRADING same enchant from Level " + existingLevel + " to Level " + level);
                } else if (level == existingLevel) {
                    plugin.getLogger().info("BLOCKING - attempting to apply same level (" + level + ")");
                    player.sendMessage(ColorUtils.color("&cThis item already has " + enchant.getDisplayName() + " Level " + level + "!"));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return;
                } else {
                    plugin.getLogger().info("BLOCKING - attempting to downgrade from Level " + existingLevel + " to Level " + level);
                    player.sendMessage(ColorUtils.color("&cCannot downgrade " + enchant.getDisplayName() + " from Level " + existingLevel + " to Level " + level + "!"));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return;
                }

                // Remove existing enchant before applying new level
                plugin.getLogger().info("Removing existing enchant before upgrade");
                targetItem = plugin.getEnchantManager().removeEnchant(targetItem);
            } else {
                // Different enchant - remove existing one
                plugin.getLogger().info("Removing existing different enchant: " + existingEnchant.getName());
                targetItem = plugin.getEnchantManager().removeEnchant(targetItem);
            }
        }

        // Apply the new enchant
        ItemStack enchantedItem = plugin.getEnchantManager().applyEnchant(targetItem, enchant, level);

        if (enchantedItem != null) {
            player.getInventory().setItem(slot, enchantedItem);

            String successMessage = plugin.getConfigManager().getString("config.yml",
                    "messages.enchant-apply-success", "&a&l✓ APPLIED! &7{enchant} Level {level} → {item}");
            successMessage = successMessage.replace("{enchant}", enchant.getDisplayName())
                    .replace("{level}", String.valueOf(level))
                    .replace("{item}", formatMaterialName(targetItem.getType()));
            player.sendMessage(ColorUtils.color(successMessage));

            playEnchantApplyEffects(player);
            plugin.getLogger().info("Enchant applied successfully!");
        } else {
            plugin.getLogger().warning("Failed to apply enchant - result was null");
            player.sendMessage(ColorUtils.color("&cFailed to apply enchant!"));
        }
    }

    private void consumeDye(Player player, ItemStack dye) {
        plugin.getLogger().info("Consuming dye. Current amount: " + dye.getAmount());

        if (dye.getAmount() > 1) {
            dye.setAmount(dye.getAmount() - 1);
            plugin.getLogger().info("Reduced dye amount to: " + dye.getAmount());
        } else {
            player.setItemOnCursor(new ItemStack(Material.AIR));
            plugin.getLogger().info("Set cursor to air (last dye consumed)");
        }
    }

    private void showIncompatibleMessage(Player player, CustomEnchant enchant, ItemStack item) {
        // Spam prevention for error messages
        UUID playerUUID = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        Long lastApply = lastEnchantApply.get(playerUUID);

        if (lastApply != null && (currentTime - lastApply) < 500) {
            return;
        }

        lastEnchantApply.put(playerUUID, currentTime);

        String message = plugin.getConfigManager().getString("config.yml",
                "messages.enchant-apply-no-compatible", "&cCannot apply {enchant} to {item}!");
        message = message.replace("{enchant}", enchant.getDisplayName())
                .replace("{item}", formatMaterialName(item.getType()));
        player.sendMessage(ColorUtils.color(message));

        StringBuilder compatibleItems = new StringBuilder();
        for (Material material : enchant.getApplicableItems()) {
            if (compatibleItems.length() > 0) compatibleItems.append(", ");
            compatibleItems.append(formatMaterialName(material));
        }

        String compatibleMessage = plugin.getConfigManager().getString("config.yml",
                "messages.enchant-apply-compatible-items", "&7Compatible items: &f{items}");
        compatibleMessage = compatibleMessage.replace("{items}", compatibleItems.toString());
        player.sendMessage(ColorUtils.color(compatibleMessage));

        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
    }

    private void playEnchantApplyEffects(Player player) {
        String soundName = plugin.getConfigManager().getString("config.yml",
                "effects.enchant-apply.sound", "BLOCK_ENCHANTMENT_TABLE_USE");
        float volume = (float) plugin.getConfigManager().getDouble("config.yml",
                "effects.enchant-apply.sound-volume", 1.0);
        float pitch = (float) plugin.getConfigManager().getDouble("config.yml",
                "effects.enchant-apply.sound-pitch", 1.2);

        try {
            Sound sound = Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException e) {
            player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
        }

        String particleName = plugin.getConfigManager().getString("config.yml",
                "effects.enchant-apply.particles", "ENCHANTMENT_TABLE");
        int particleCount = plugin.getConfigManager().getInt("config.yml",
                "effects.enchant-apply.particle-count", 30);

        try {
            org.bukkit.Particle particle = org.bukkit.Particle.valueOf(particleName);
            player.spawnParticle(particle, player.getLocation().add(0, 1, 0),
                    particleCount, 0.5, 0.5, 0.5, 0.1);
        } catch (Exception e) {
            // Ignore if particles don't work
        }
    }

    private String formatMaterialName(Material material) {
        String name = material.name().toLowerCase().replace("_", " ");
        String[] words = name.split(" ");
        StringBuilder formatted = new StringBuilder();

        for (String word : words) {
            if (formatted.length() > 0) formatted.append(" ");
            formatted.append(word.substring(0, 1).toUpperCase()).append(word.substring(1));
        }

        return formatted.toString();
    }
}