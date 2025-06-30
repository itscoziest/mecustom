package com.mystenchants.listeners;

import com.mystenchants.MystEnchants;
import com.mystenchants.enchants.CustomEnchant;
import com.mystenchants.enchants.EnchantTier;
import com.mystenchants.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Sound;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Handles GUI interactions
 */
public class InventoryClickListener implements Listener {

    private final MystEnchants plugin;

    public InventoryClickListener(MystEnchants plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        String title = ChatColor.stripColor(event.getView().getTitle());
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        ItemMeta meta = clickedItem.getItemMeta();
        String itemName = ChatColor.stripColor(meta.getDisplayName());

        // Handle different GUI types
        if (title.equals("Enchants")) {
            event.setCancelled(true);
            handleEnchantsGui(player, itemName);
        } else if (title.contains("Enchants") && !title.equals("Enchants")) {
            event.setCancelled(true);
            handleTierGui(player, itemName, event, clickedItem);
        } else if (title.equals("Oracle")) {
            event.setCancelled(true);
            handleOracleGui(player, itemName, clickedItem);
        } else if (title.contains("Details")) {
            event.setCancelled(true);
            handleOracleDetailsGui(player, itemName);
        } else if (title.equals("Purchase Upgrades")) {
            event.setCancelled(true);
            handlePurchaseGui(player, itemName, clickedItem);
        } else if (title.equals("Soul Shop") || title.equals("Soul Shop (Page 2)")) {
            // FIXED: Handle both page 1 and page 2 titles
            event.setCancelled(true);
            handleSoulShopGui(player, itemName, clickedItem);
        } else if (title.equals("Perks")) {
            event.setCancelled(true);
            handlePerksGui(player, itemName, clickedItem);
        } else if (title.equals("Redemption Boss Fight")) {
            event.setCancelled(true);
            handleRedemptionGui(player, itemName);
        }
    }

    private void handleEnchantsGui(Player player, String itemName) {
        for (EnchantTier tier : EnchantTier.values()) {
            if (itemName.contains(tier.getDisplayName())) {
                player.openInventory(plugin.getGuiManager().createTierGui(player, tier));
                return;
            }
        }
    }

    private void handleTierGui(Player player, String itemName, InventoryClickEvent event, ItemStack clickedItem) {
        if (itemName.equals("Back")) {
            player.openInventory(plugin.getGuiManager().createEnchantsGui(player));
            return;
        }

        // Handle enchant detail clicks - find the enchant by looking at the item
        if (clickedItem != null && clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasLore()) {
            List<String> lore = clickedItem.getItemMeta().getLore();

            // Check if this item has "Click to view details" in lore
            boolean hasClickToView = false;
            for (String line : lore) {
                if (ChatColor.stripColor(line).contains("Click to view details")) {
                    hasClickToView = true;
                    break;
                }
            }

            if (hasClickToView) {
                // Extract tier from title
                String title = player.getOpenInventory().getTitle();
                EnchantTier tier = null;
                for (EnchantTier t : EnchantTier.values()) {
                    if (title.contains(t.getDisplayName())) {
                        tier = t;
                        break;
                    }
                }

                if (tier != null) {
                    // Get enchants from this tier and find the clicked one
                    List<CustomEnchant> tierEnchants = plugin.getEnchantManager().getEnchantsByTier(tier);
                    String clickedDisplayName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());

                    for (CustomEnchant enchant : tierEnchants) {
                        String enchantDisplayName = ChatColor.stripColor(enchant.getDisplayName());
                        if (clickedDisplayName.contains(enchantDisplayName)) {
                            player.openInventory(plugin.getGuiManager().createOracleDetailsGui(player, enchant));
                            return;
                        }
                    }
                }
            }
        }
    }

    private void handleOracleGui(Player player, String itemName, ItemStack item) {
        if (itemName.equals("Purchase Upgrades")) {
            player.openInventory(plugin.getGuiManager().createOraclePurchaseGui(player));
            return;
        }

        // Handle clicking on enchants in oracle
        if (item != null && item.hasItemMeta() && item.getItemMeta().hasLore()) {
            List<String> lore = item.getItemMeta().getLore();

            // Check if this item has "Click to view details" in lore
            boolean hasClickToView = false;
            for (String line : lore) {
                if (ChatColor.stripColor(line).contains("Click to view details")) {
                    hasClickToView = true;
                    break;
                }
            }

            if (hasClickToView) {
                String displayName = item.getItemMeta().getDisplayName();

                // Check all enchants to find match
                for (CustomEnchant enchant : plugin.getEnchantManager().getAllEnchants()) {
                    String enchantDisplayName = enchant.getDisplayName();

                    // Remove color codes for comparison
                    String cleanDisplay = ChatColor.stripColor(displayName);
                    String cleanEnchant = ChatColor.stripColor(enchantDisplayName);

                    if (cleanDisplay.contains(cleanEnchant)) {
                        player.openInventory(plugin.getGuiManager().createOracleDetailsGui(player, enchant));
                        return;
                    }
                }
            }
        }
    }

    private void handleOracleDetailsGui(Player player, String itemName) {
        if (itemName.equals("Back")) {
            player.openInventory(plugin.getGuiManager().createOracleGui(player));
        }
    }

    private void handlePurchaseGui(Player player, String itemName, ItemStack item) {
        if (itemName.equals("Back")) {
            player.openInventory(plugin.getGuiManager().createOracleGui(player));
            return;
        }

        // Handle enchant upgrade purchase
        if (item != null && item.hasItemMeta() && item.getItemMeta().hasLore()) {
            List<String> lore = item.getItemMeta().getLore();

            // Check if this is a purchase item (has "Click to purchase" in lore)
            boolean isPurchaseItem = false;
            for (String line : lore) {
                if (ChatColor.stripColor(line).contains("Click to purchase")) {
                    isPurchaseItem = true;
                    break;
                }
            }

            if (isPurchaseItem) {
                String displayName = ChatColor.stripColor(item.getItemMeta().getDisplayName());

                // Find the enchant and level
                for (CustomEnchant enchant : plugin.getEnchantManager().getAllEnchants()) {
                    String enchantName = ChatColor.stripColor(enchant.getDisplayName());
                    if (displayName.contains(enchantName)) {
                        // Extract level from display name (should be at the end)
                        String[] parts = displayName.split(" ");
                        try {
                            int level = Integer.parseInt(parts[parts.length - 1]);
                            purchaseEnchantUpgrade(player, enchant, level);
                        } catch (NumberFormatException e) {
                            // Try to find level in a different way
                            for (int i = 1; i <= enchant.getMaxLevel(); i++) {
                                if (displayName.contains(String.valueOf(i))) {
                                    purchaseEnchantUpgrade(player, enchant, i);
                                    break;
                                }
                            }
                        }
                        return;
                    }
                }
            }
        }
    }

    /**
     * FIXED: Handle soul shop clicks with proper enchant detection
     */
    private void handleSoulShopGui(Player player, String itemName, ItemStack clickedItem) {
        // Handle page navigation FIRST - before checking for purchasable items
        if (itemName.equals("Next Page") || itemName.contains("Next Page")) {
            player.openInventory(plugin.getGuiManager().createSoulShopPage2Gui(player));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            return;
        }

        if (itemName.equals("Previous Page") || itemName.contains("Previous Page")) {
            player.openInventory(plugin.getGuiManager().createSoulShopGui(player));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            return;
        }

        // Handle page info clicks (do nothing)
        if (itemName.contains("Soul Shop") || itemName.contains("Page")) {
            return;
        }

        // FIXED: Better enchant item detection - check for "Enchant" in the item name
        if (clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasDisplayName()) {
            String displayName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());

            // Check if this is a glass pane or filler item (ignore these completely)
            if (displayName.trim().isEmpty() || displayName.equals(" ")) {
                return; // Silent return for glass panes and filler items
            }

            // Check if this is an enchant item by looking for "Enchant" in the name
            if (!displayName.toLowerCase().contains("enchant")) {
                return; // Not an enchant item, ignore silently
            }

            // FIXED: More robust enchant detection
            if (clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasLore()) {
                List<String> lore = clickedItem.getItemMeta().getLore();

                // Verify this is actually an enchant item by checking lore content
                boolean isEnchantItem = false;
                for (String line : lore) {
                    String cleanLine = ChatColor.stripColor(line).toLowerCase();
                    if (cleanLine.contains("apply") || cleanLine.contains("cost:") || cleanLine.contains("souls")) {
                        isEnchantItem = true;
                        break;
                    }
                }

                if (!isEnchantItem) {
                    return; // Not a real enchant item, ignore silently
                }

                // Extract enchant name and level from the item
                String enchantName = extractEnchantName(clickedItem);
                int enchantLevel = extractEnchantLevel(clickedItem);

                if (enchantName != null && enchantLevel > 0) {
                    CustomEnchant enchant = plugin.getEnchantManager().getEnchant(enchantName);
                    if (enchant != null) {
                        // Process the enchant purchase
                        purchaseEnchantBookFixed(player, enchant, enchantLevel, clickedItem);
                        return;
                    }
                }
            }
        }

        // If we get here, it's not a valid enchant item - ignore silently
        // No error message for non-enchant items like glass panes
    }

    /**
     * FIXED: Extract enchant level from item display name or lore
     */
    private int extractEnchantLevel(ItemStack item) {
        if (!item.hasItemMeta()) return 1;

        String displayName = ChatColor.stripColor(item.getItemMeta().getDisplayName()).toLowerCase();

        // Check for "level X" in display name
        if (displayName.contains("level 3")) return 3;
        if (displayName.contains("level 2")) return 2;
        if (displayName.contains("level 1")) return 1;

        // Default to level 1 if no level specified
        return 1;
    }

    /**
     * Extracts enchant name from item display name
     */
    private String extractEnchantName(ItemStack item) {
        if (!item.hasItemMeta()) return null;

        String displayName = ChatColor.stripColor(item.getItemMeta().getDisplayName()).toLowerCase();

        // Remove "enchant", "level", numbers, and extra text
        displayName = displayName.replace(" enchant", "")
                .replace(" level 1", "")
                .replace(" level 2", "")
                .replace(" level 3", "")
                .trim();

        // Map display names to actual enchant names
        switch (displayName) {
            case "tempo": return "tempo";
            case "scholar": return "scholar";
            case "serrate": return "serrate";
            case "rejuvenate": return "rejuvenate";
            case "backup": return "backup";
            case "guillotine": return "guillotine";
            case "pace": return "pace";
            case "pantsed": return "pantsed";
            case "detonate": return "detonate";
            case "almighty push": return "almighty_push";
            case "redemption": return "redemption";
            case "zetsubo": return "zetsubo";
            default: return null;
        }
    }

    private void handlePerksGui(Player player, String itemName, ItemStack clickedItem) {
        // FIXED: Only allow purchasing from shop items, not functional items
        if (!plugin.getPerkManager().isPerkShopItem(clickedItem)) {
            return; // Not a shop item, ignore
        }

        String perkName = plugin.getPerkManager().getPerkNameFromShopItem(clickedItem);
        if (perkName != null) {
            plugin.getPerkManager().purchasePerk(player, perkName);
        }
    }

    private void handleRedemptionGui(Player player, String itemName) {
        if (itemName.equals("Confirm")) {
            plugin.getRedemptionManager().startRedemption(player);
            player.closeInventory();
        } else if (itemName.equals("Cancel")) {
            player.closeInventory();
        }
    }

    private void purchaseEnchantUpgrade(Player player, CustomEnchant enchant, int level) {
        int expCost = plugin.getConfigManager().getInt("config.yml",
                "exp-costs." + enchant.getName() + ".level-" + level, 50);

        if (player.getLevel() < expCost) {
            String message = plugin.getConfigManager().getString("config.yml", "messages.insufficient-exp", "&cYou don't have enough EXP levels!");
            player.sendMessage(ColorUtils.color(message));
            return;
        }

        // Check if player has previous level
        plugin.getPlayerDataManager().getEnchantLevel(player.getUniqueId(), enchant.getName())
                .thenAccept(currentLevel -> {
                    if (currentLevel < level - 1) {
                        String message = plugin.getConfigManager().getString("config.yml", "messages.enchant-previous-level-required", "&cYou need to unlock the previous level first!");
                        player.sendMessage(ColorUtils.color(message));
                        return;
                    }

                    // Purchase upgrade
                    player.setLevel(player.getLevel() - expCost);
                    plugin.getPlayerDataManager().setEnchantLevel(player.getUniqueId(), enchant.getName(), level)
                            .thenRun(() -> {
                                String message = plugin.getConfigManager().getString("config.yml", "messages.enchant-purchase-success", "&aYou have purchased &6{enchant} Level {level} &afor &6{cost} EXP levels&a!");
                                message = message.replace("{enchant}", enchant.getDisplayName())
                                        .replace("{level}", String.valueOf(level))
                                        .replace("{cost}", String.valueOf(expCost));
                                player.sendMessage(ColorUtils.color(message));

                                // Refresh GUI
                                player.openInventory(plugin.getGuiManager().createOraclePurchaseGui(player));
                            });
                });
    }

    /**
     * FIXED: Purchase enchant book with proper config messages and dye creation
     */
    private void purchaseEnchantBookFixed(Player player, CustomEnchant enchant, int level, ItemStack clickedItem) {
        // Get cost for the specific level
        String costPath = "shop.items." + enchant.getName() + "-book-level-" + level + ".cost";
        String fallbackPath = "shop.items." + enchant.getName() + "-book.cost";
        int cost = plugin.getConfigManager().getPerksConfig().getInt(costPath,
                plugin.getConfigManager().getPerksConfig().getInt(fallbackPath, 500));

        // FIXED: Check actual player data instead of visual indicators
        plugin.getPlayerDataManager().getPlayerEnchants(player.getUniqueId())
                .thenAccept(playerEnchants -> {
                    Integer currentLevel = playerEnchants.get(enchant.getName());

                    // Check if player already owns this level or higher
                    if (currentLevel != null && currentLevel >= level) {
                        // Use config message for already owned
                        String message = plugin.getConfigManager().getString("config.yml",
                                "messages.enchant-already-owned", "&cYou already own this enchant level!");
                        player.sendMessage(ColorUtils.color(message));
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        return;
                    }

                    // Check if player can purchase this level (must have previous level for level 2+)
                    if (level > 1 && (currentLevel == null || currentLevel < level - 1)) {
                        // Use config message for previous level required
                        String message = plugin.getConfigManager().getString("config.yml",
                                "messages.enchant-previous-level-required", "&cYou must own Level {level} first!");
                        message = message.replace("{level}", String.valueOf(level - 1));
                        player.sendMessage(ColorUtils.color(message));
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        return;
                    }

                    // Check requirements
                    plugin.getEnchantManager().meetsRequirements(player, enchant.getName(), level)
                            .thenAccept(meetsRequirements -> {
                                if (!meetsRequirements) {
                                    // Use config message for requirements not met
                                    String message = plugin.getConfigManager().getString("config.yml",
                                            "messages.enchant-requirements-not-met", "&cYou don't meet the requirements for this enchant level!");
                                    player.sendMessage(ColorUtils.color(message));

                                    String helpMessage = plugin.getConfigManager().getString("config.yml",
                                            "messages.enchant-check-oracle", "&7Check the Oracle for requirement details.");
                                    player.sendMessage(ColorUtils.color(helpMessage));
                                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                                    return;
                                }

                                // Check if player has enough souls
                                plugin.getSoulManager().hasSouls(player.getUniqueId(), cost)
                                        .thenAccept(hasSouls -> {
                                            if (!hasSouls) {
                                                // FIXED: Use config message for insufficient souls
                                                plugin.getSoulManager().getSouls(player.getUniqueId()).thenAccept(currentSouls -> {
                                                    String message = plugin.getConfigManager().getString("config.yml",
                                                            "messages.insufficient-souls", "&cYou don't have enough souls!");
                                                    player.sendMessage(ColorUtils.color(message));

                                                    // Show detailed message with current/required souls
                                                    String detailMessage = plugin.getConfigManager().getString("config.yml",
                                                            "messages.souls-needed-detail", "&cYou need &6{cost} &csouls but only have &6{current}&c!");
                                                    detailMessage = detailMessage.replace("{cost}", String.valueOf(cost))
                                                            .replace("{current}", String.valueOf(currentSouls));
                                                    player.sendMessage(ColorUtils.color(detailMessage));

                                                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                                                });
                                                return;
                                            }

                                            // Player has enough souls and meets requirements - proceed with purchase
                                            plugin.getSoulManager().removeSouls(player.getUniqueId(), cost)
                                                    .thenAccept(success -> {
                                                        if (success) {
                                                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                                                // FIXED: Create the enchant dye using the plugin's enchant manager
                                                                ItemStack dye = plugin.getEnchantManager().createEnchantDye(enchant, level);

                                                                // Give the dye to player
                                                                HashMap<Integer, ItemStack> remaining = player.getInventory().addItem(dye);
                                                                for (ItemStack item : remaining.values()) {
                                                                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                                                                }

                                                                // FIXED: Use config messages for success
                                                                String successMessage = plugin.getConfigManager().getString("config.yml",
                                                                        "messages.enchant-purchase-success", "&aYou purchased {enchant} Level {level} Dye for {cost} souls!");
                                                                successMessage = successMessage.replace("{enchant}", enchant.getDisplayName())
                                                                        .replace("{level}", String.valueOf(level))
                                                                        .replace("{cost}", String.valueOf(cost));
                                                                player.sendMessage(ColorUtils.color(successMessage));

                                                                String instructionMessage = plugin.getConfigManager().getString("config.yml",
                                                                        "messages.enchant-dye-instruction", "&eDrag and drop the dye onto a compatible item to apply the enchant!");
                                                                player.sendMessage(ColorUtils.color(instructionMessage));

                                                                // Play success sound from config
                                                                String successSound = plugin.getConfigManager().getString("config.yml",
                                                                        "sounds.purchase-success", "ENTITY_PLAYER_LEVELUP");
                                                                try {
                                                                    Sound sound = Sound.valueOf(successSound);
                                                                    player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
                                                                } catch (IllegalArgumentException e) {
                                                                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                                                                }

                                                                // Refresh the GUI to show updated status
                                                                String currentTitle = ChatColor.stripColor(player.getOpenInventory().getTitle());
                                                                if (currentTitle.equals("Soul Shop (Page 2)")) {
                                                                    player.openInventory(plugin.getGuiManager().createSoulShopPage2Gui(player));
                                                                } else {
                                                                    player.openInventory(plugin.getGuiManager().createSoulShopGui(player));
                                                                }
                                                            });
                                                        } else {
                                                            // Use config message for transaction failure
                                                            String errorMessage = plugin.getConfigManager().getString("config.yml",
                                                                    "messages.soul-transaction-failed", "&cFailed to remove souls from your account!");
                                                            player.sendMessage(ColorUtils.color(errorMessage));
                                                            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                                                        }
                                                    });
                                        });
                            });
                })
                .exceptionally(throwable -> {
                    // Use config message for errors
                    String errorMessage = plugin.getConfigManager().getString("config.yml",
                            "messages.purchase-error", "&cError processing purchase: {error}");
                    errorMessage = errorMessage.replace("{error}", throwable.getMessage());
                    player.sendMessage(ColorUtils.color(errorMessage));
                    plugin.getLogger().warning("Error in purchaseEnchantBookFixed: " + throwable.getMessage());
                    return null;
                });
    }
}