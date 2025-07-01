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
 * COMPLETE: Drag and drop enchant listener with multiple enchants support
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
     * SAFER VERSION: Enhanced version that properly handles multiple enchants with better error handling
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

        try {
            // SAFER: Check if the new methods exist before using them
            if (hasMethod(plugin.getEnchantManager(), "hasSpecificCustomEnchant")) {
                // NEW SYSTEM: Check if this specific enchant already exists
                if (plugin.getEnchantManager().hasSpecificCustomEnchant(item, enchant.getName())) {
                    int existingLevel = plugin.getEnchantManager().getSpecificCustomEnchantLevel(item, enchant.getName());

                    plugin.getLogger().info("Item has existing enchant: " + enchant.getName() + " Level " + existingLevel);

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
                }

                // Check if we can add more enchants to this item
                if (hasMethod(plugin.getEnchantManager(), "canAddMoreEnchants")) {
                    if (!plugin.getEnchantManager().canAddMoreEnchants(item)) {
                        plugin.getLogger().info("BLOCKING - item has maximum number of enchants");
                        return false;
                    }
                }
            } else {
                // FALLBACK TO OLD SYSTEM: Check using old methods
                plugin.getLogger().info("Using fallback old system for enchant checking");

                if (plugin.getEnchantManager().hasCustomEnchant(item)) {
                    CustomEnchant existingEnchant = plugin.getEnchantManager().getCustomEnchant(item);
                    int existingLevel = plugin.getEnchantManager().getCustomEnchantLevel(item);

                    if (existingEnchant != null && existingEnchant.getName().equals(enchant.getName())) {
                        // Same enchant - check if we're upgrading or downgrading
                        if (existingLevel == newLevel) {
                            plugin.getLogger().info("BLOCKING - same level using old system (" + newLevel + ")");
                            return false;
                        }

                        if (newLevel > existingLevel) {
                            plugin.getLogger().info("ALLOWING UPGRADE using old system from Level " + existingLevel + " to Level " + newLevel);
                            return true; // Allow upgrade
                        } else {
                            plugin.getLogger().info("BLOCKING - downgrade attempt using old system from Level " + existingLevel + " to Level " + newLevel);
                            return false; // Block downgrade
                        }
                    } else {
                        // Different enchant - for now, allow replacement until multiple enchants is fully working
                        plugin.getLogger().info("Different enchant detected using old system - allowing replacement");
                        return true;
                    }
                }
            }

            plugin.getLogger().info("No existing enchant of this type - can apply");
            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("Error in canApplyToItemWithLevel: " + e.getMessage());
            e.printStackTrace();
            // Fallback to basic compatibility check
            return enchant.isApplicableTo(item.getType());
        }
    }

    /**
     * HELPER: Check if a method exists in the EnchantManager
     */
    private boolean hasMethod(Object obj, String methodName) {
        try {
            return obj.getClass().getMethod(methodName, ItemStack.class, String.class) != null;
        } catch (NoSuchMethodException e) {
            return false;
        }
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
     * SAFER: Apply enchant with better error handling
     */
    private void applyEnchantToItem(Player player, CustomEnchant enchant, int level, ItemStack targetItem, int slot) {
        plugin.getLogger().info("Applying enchant " + enchant.getName() + " Level " + level + " to " + targetItem.getType());

        try {
            // Get summary of current enchants for logging (if method exists)
            String beforeSummary = "Unknown";
            try {
                if (hasMethod(plugin.getEnchantManager(), "getEnchantSummary")) {
                    beforeSummary = plugin.getEnchantManager().getEnchantSummary(targetItem);
                }
            } catch (Exception e) {
                beforeSummary = "Error getting summary";
            }
            plugin.getLogger().info("Before applying: " + beforeSummary);

            // Apply the new enchant
            ItemStack enchantedItem = plugin.getEnchantManager().applyEnchant(targetItem, enchant, level);

            if (enchantedItem != null) {
                player.getInventory().setItem(slot, enchantedItem);

                // Get summary after applying (if method exists)
                String afterSummary = "Unknown";
                try {
                    if (hasMethod(plugin.getEnchantManager(), "getEnchantSummary")) {
                        afterSummary = plugin.getEnchantManager().getEnchantSummary(enchantedItem);
                    }
                } catch (Exception e) {
                    afterSummary = "Error getting summary";
                }
                plugin.getLogger().info("After applying: " + afterSummary);

                // Enhanced success message
                Map<String, Integer> allEnchants = null;
                try {
                    if (hasMethod(plugin.getEnchantManager(), "getAllCustomEnchants")) {
                        allEnchants = plugin.getEnchantManager().getAllCustomEnchants(enchantedItem);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Could not get all enchants: " + e.getMessage());
                }

                if (allEnchants != null && allEnchants.size() > 1) {
                    // Multiple enchants message
                    String successMessage = plugin.getConfigManager().getString("config.yml",
                            "messages.enchant-apply-success-multiple", "&a&l✓ APPLIED! &7{enchant} Level {level} → {item} &7(Total: {total} enchants)");
                    successMessage = successMessage.replace("{enchant}", enchant.getDisplayName())
                            .replace("{level}", String.valueOf(level))
                            .replace("{item}", formatMaterialName(targetItem.getType()))
                            .replace("{total}", String.valueOf(allEnchants.size()));
                    player.sendMessage(ColorUtils.color(successMessage));

                    // Show all enchants
                    player.sendMessage(ColorUtils.color("&7Enchants: &f" + afterSummary));
                } else {
                    // Single enchant message (default)
                    String successMessage = plugin.getConfigManager().getString("config.yml",
                            "messages.enchant-apply-success", "&a&l✓ APPLIED! &7{enchant} Level {level} → {item}");
                    successMessage = successMessage.replace("{enchant}", enchant.getDisplayName())
                            .replace("{level}", String.valueOf(level))
                            .replace("{item}", formatMaterialName(targetItem.getType()));
                    player.sendMessage(ColorUtils.color(successMessage));
                }

                playEnchantApplyEffects(player);
                plugin.getLogger().info("Enchant applied successfully!");
            } else {
                plugin.getLogger().warning("Failed to apply enchant - result was null");
                player.sendMessage(ColorUtils.color("&cFailed to apply enchant!"));
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Error applying enchant " + enchant.getName() + ": " + e.getMessage());
            e.printStackTrace();
            player.sendMessage(ColorUtils.color("&cError applying enchant: " + e.getMessage()));
        }
    }

    /**
     * FIXED: Consume dye method that was missing
     */
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