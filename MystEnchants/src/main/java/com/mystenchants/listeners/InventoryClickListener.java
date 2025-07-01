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
import org.bukkit.Material;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import com.mystenchants.enchants.CustomEnchant;
import com.mystenchants.utils.ColorUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

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

        // ADD THIS FOR SOUL SHOP
        if (title.equals("Soul Shop") || title.contains("Soul Shop")) {
            event.setCancelled(true);

            if (clickedItem != null && clickedItem.hasItemMeta()) {
                PersistentDataContainer container = clickedItem.getItemMeta().getPersistentDataContainer();
                NamespacedKey soulShopKey = new NamespacedKey(plugin, "soul_shop_item");

                if (container.has(soulShopKey, PersistentDataType.STRING)) {
                    handleSoulShopPurchase(player, clickedItem);
                    return;
                }
            }
        }

        ItemMeta meta = clickedItem.getItemMeta();
        String itemName = ChatColor.stripColor(meta.getDisplayName());

        // Handle different GUI types - ALWAYS CANCEL THE EVENT FOR GUIs
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
        } else if (title.contains("Soul Shop")) {
            event.setCancelled(true); // CRITICAL: Always cancel for soul shop
            handleSoulShopGui(player, itemName, clickedItem);
        } else if (title.equals("Perks")) {
            event.setCancelled(true);
            handlePerksGui(player, itemName, clickedItem);
        } else if (title.equals("Redemption Boss Fight")) {
            event.setCancelled(true);
            handleRedemptionGui(player, itemName);
        }
    }

    /**
     * Helper method to check if an inventory title is a GUI
     */
    private boolean isGuiInventory(String title) {
        return title.equals("Enchants") ||
                title.contains("Enchants") ||
                title.equals("Oracle") ||
                title.contains("Details") ||
                title.equals("Purchase Upgrades") ||
                title.contains("Soul Shop") ||
                title.equals("Perks") ||
                title.equals("Redemption Boss Fight");
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
     * COMPLETE: Handle soul shop purchases
     */
    private void handleSoulShopPurchase(Player player, ItemStack clickedItem) {
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        ItemMeta meta = clickedItem.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        NamespacedKey soulShopKey = new NamespacedKey(plugin, "soul_shop_item");
        NamespacedKey levelKey = new NamespacedKey(plugin, "soul_shop_level");
        NamespacedKey costKey = new NamespacedKey(plugin, "soul_shop_cost");
        NamespacedKey purchasableKey = new NamespacedKey(plugin, "soul_shop_purchasable");

        if (!container.has(soulShopKey, PersistentDataType.STRING)) return;

        String enchantName = container.get(soulShopKey, PersistentDataType.STRING);
        int level = container.getOrDefault(levelKey, PersistentDataType.INTEGER, 1);
        int cost = container.getOrDefault(costKey, PersistentDataType.INTEGER, 500);
        boolean isPurchasable = container.getOrDefault(purchasableKey, PersistentDataType.BYTE, (byte) 0) == 1;

        plugin.getLogger().info("Soul shop click: " + enchantName + " Level " + level + ", Cost: " + cost + ", Purchasable: " + isPurchasable);

        if (!isPurchasable) {
            player.sendMessage(ColorUtils.color("&cYou cannot purchase this enchant yet!"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Check if player has enough souls
        long playerSouls = plugin.getSoulManager().getSouls(player.getUniqueId()).join();
        if (playerSouls < cost) {
            player.sendMessage(ColorUtils.color("&cYou need " + (cost - playerSouls) + " more souls!"));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Get the enchant
        CustomEnchant enchant = plugin.getEnchantManager().getEnchant(enchantName);
        if (enchant == null) {
            player.sendMessage(ColorUtils.color("&cEnchant not found!"));
            return;
        }

        // Check requirements one more time
        try {
            boolean meetsRequirements = plugin.getEnchantManager().meetsRequirements(player, enchantName, level).join();
            if (!meetsRequirements) {
                player.sendMessage(ColorUtils.color("&cYou don't meet the requirements for this enchant!"));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }
        } catch (Exception e) {
            player.sendMessage(ColorUtils.color("&cError checking requirements!"));
            return;
        }

        // Deduct souls
        plugin.getSoulManager().removeSouls(player.getUniqueId(), cost);

        // Update player data FIRST
        plugin.getPlayerDataManager().setEnchantLevel(player.getUniqueId(), enchantName, level);

        // Give enchant book to player
        ItemStack enchantBook = plugin.getEnchantManager().createEnchantDye(enchant, level);

        // Try to add to inventory
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(enchantBook);
        if (!leftover.isEmpty()) {
            // Drop on ground if inventory full
            for (ItemStack item : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
            player.sendMessage(ColorUtils.color("&7Some items were dropped on the ground (inventory full)"));
        }

        // Success message
        player.sendMessage(ColorUtils.color("&a&lPURCHASED! &7" + enchant.getDisplayName() + " Level " + level + " for " + cost + " souls"));

        // Play success sound
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);

        // REFRESH THE GUI instead of closing
        String currentTitle = player.getOpenInventory().getTitle();
        if (currentTitle.contains("Page 2")) {
            player.openInventory(plugin.getGuiManager().createSoulShopPage2Gui(player));
        } else {
            player.openInventory(plugin.getGuiManager().createSoulShopGui(player));
        }

        plugin.getLogger().info("Player " + player.getName() + " purchased " + enchantName + " Level " + level + " for " + cost + " souls");
    }


    private void handleSoulShopGui(Player player, String itemName, ItemStack clickedItem) {
        // Handle page navigation FIRST
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

        // ADD THIS DEBUG SECTION:
        if (clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasDisplayName()) {
            String displayName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());

            // DEBUG: Log all clicks for troubleshooting
            plugin.getLogger().info("=== SOUL SHOP CLICK DEBUG ===");
            plugin.getLogger().info("Player: " + player.getName());
            plugin.getLogger().info("Item clicked: " + displayName);
            plugin.getLogger().info("Item material: " + clickedItem.getType());

            // Check if this is a glass pane or filler item
            if (displayName.trim().isEmpty() || displayName.equals(" ")) {
                plugin.getLogger().info("Ignoring glass pane/filler item");
                return;
            }

            // Check if this is an enchant item
            if (!displayName.toLowerCase().contains("enchant")) {
                plugin.getLogger().info("Not an enchant item: " + displayName);
                return;
            }

            // Check lore for enchant indicators
            if (clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasLore()) {
                List<String> lore = clickedItem.getItemMeta().getLore();

                boolean isEnchantItem = false;
                for (String line : lore) {
                    String cleanLine = ChatColor.stripColor(line).toLowerCase();
                    if (cleanLine.contains("apply") || cleanLine.contains("cost:") || cleanLine.contains("souls")) {
                        isEnchantItem = true;
                        plugin.getLogger().info("Detected enchant item by lore: " + cleanLine);
                        break;
                    }
                }

                if (!isEnchantItem) {
                    plugin.getLogger().info("Not a real enchant item based on lore");
                    return;
                }

                // Extract enchant name and level
                String enchantName = extractEnchantName(clickedItem);
                int enchantLevel = extractEnchantLevel(clickedItem);

                plugin.getLogger().info("Extracted enchant: " + enchantName + ", level: " + enchantLevel);

                if (enchantName != null && enchantLevel > 0) {
                    CustomEnchant enchant = plugin.getEnchantManager().getEnchant(enchantName);
                    if (enchant != null) {
                        plugin.getLogger().info("Found enchant object, proceeding with purchase");
                        purchaseEnchantBookFixed(player, enchant, enchantLevel, clickedItem);
                        return;
                    } else {
                        plugin.getLogger().warning("Enchant object is null for: " + enchantName);
                    }
                } else {
                    plugin.getLogger().warning("Could not extract enchant name or level from: " + displayName);
                }
            }

            plugin.getLogger().info("=== END SOUL SHOP CLICK DEBUG ===");
        }
    }

    /**
     * FIXED: Enhanced enchant name extraction with better pattern matching
     */
    private String extractEnchantNameFixed(String displayName) {
        String cleanName = displayName.toLowerCase()
                .replace("enchant", "")
                .replace("level", "")
                .replace("i", "").replace("ii", "").replace("iii", "")
                .trim();

        // Remove tier colors and formatting
        cleanName = cleanName.replaceAll("^(tempo|scholar|serrate|rejuvenate|backup|guillotine|pace|pantsed|detonate|almighty push|redemption|zetsubo).*", "$1");

        plugin.getLogger().info("Cleaning display name: '" + displayName + "' -> '" + cleanName + "'");

        // Enhanced mapping with better pattern matching
        if (cleanName.contains("tempo")) return "tempo";
        if (cleanName.contains("scholar")) return "scholar";
        if (cleanName.contains("serrate")) return "serrate";
        if (cleanName.contains("rejuvenate")) return "rejuvenate";
        if (cleanName.contains("backup")) return "backup";
        if (cleanName.contains("guillotine")) return "guillotine";
        if (cleanName.contains("gullotne")) return "guillotine";
        if (cleanName.contains("pace")) return "pace";
        if (cleanName.contains("pantsed")) return "pantsed";
        if (cleanName.contains("detonate")) return "detonate";
        if (cleanName.contains("almighty") || cleanName.contains("push")) return "almighty_push";
        if (cleanName.contains("redemption")) return "redemption";
        if (cleanName.contains("zetsubo")) return "zetsubo";

        return null;
    }

    /**
     * Extracts enchant level from item display name or lore
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

        // DEBUG: Add this line temporarily
        plugin.getLogger().info("DEBUG: Final cleaned name for switch: '" + displayName + "'");

        // Map display names to actual enchant names
        switch (displayName) {
            case "tempo": return "tempo";
            case "scholar": return "scholar";
            case "serrate": return "serrate";
            case "rejuvenate": return "rejuvenate";
            case "backup": return "backup";
            case "guillotine": return "guillotine";
            case "gullotne": return "guillotine";
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
        // Check if this is an info item or non-purchasable item
        if (itemName.contains("Information") || clickedItem.getType() == Material.BLACK_STAINED_GLASS_PANE) {
            return; // Ignore info items and glass panes
        }

        // FIXED: Check if this is a perk shop item using the PerkManager
        if (!plugin.getPerkManager().isPerkShopItem(clickedItem)) {
            // Debug: log what was clicked
            plugin.getLogger().info("Clicked non-perk item: " + itemName + " | Material: " + clickedItem.getType());
            return; // Not a shop item, ignore
        }

        String perkName = plugin.getPerkManager().getPerkNameFromShopItem(clickedItem);
        if (perkName != null) {
            plugin.getLogger().info("Player " + player.getName() + " attempting to purchase perk: " + perkName);

            // Purchase the perk asynchronously
            plugin.getPerkManager().purchasePerk(player, perkName)
                    .thenAccept(success -> {
                        if (success) {
                            plugin.getLogger().info("Perk purchase successful for " + player.getName() + ": " + perkName);

                            // Refresh the GUI on the main thread
                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                player.openInventory(plugin.getGuiManager().createPerksGui(player));
                            });
                        } else {
                            plugin.getLogger().info("Perk purchase failed for " + player.getName() + ": " + perkName);
                        }
                    })
                    .exceptionally(throwable -> {
                        plugin.getLogger().warning("Error purchasing perk " + perkName + " for " + player.getName() + ": " + throwable.getMessage());
                        player.sendMessage(ColorUtils.color("&cError purchasing perk: " + throwable.getMessage()));
                        return null;
                    });
        } else {
            plugin.getLogger().warning("Could not extract perk name from clicked item: " + itemName);
            player.sendMessage(ColorUtils.color("&cError: Could not identify perk item"));
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
     * FIXED: Purchase method with comprehensive requirement checking
     */
    private void purchaseEnchantBookFixed(Player player, CustomEnchant enchant, int level, ItemStack clickedItem) {
        // Get cost for the specific level
        String costPath = "shop.items." + enchant.getName() + "-book-level-" + level + ".cost";
        String fallbackPath = "shop.items." + enchant.getName() + "-book.cost";
        int cost = plugin.getConfigManager().getPerksConfig().getInt(costPath,
                plugin.getConfigManager().getPerksConfig().getInt(fallbackPath, 500));

        plugin.getPlayerDataManager().getPlayerEnchants(player.getUniqueId())
                .thenAccept(playerEnchants -> {
                    Integer currentLevel = playerEnchants.get(enchant.getName());

                    // FIXED: Only check for sequential level requirement (for level 2+)
                    if (level > 1 && (currentLevel == null || currentLevel < level - 1)) {
                        String message = plugin.getConfigManager().getString("config.yml",
                                "messages.enchant-previous-level-required", "&cYou must own Level {level} first!");
                        message = message.replace("{level}", String.valueOf(level - 1));
                        player.sendMessage(ColorUtils.color(message));
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        return;
                    }

                    // FIXED: Check requirements OR if player already owns the enchant
                    boolean canPurchase = false;

                    if (currentLevel != null && currentLevel >= level) {
                        // Player already owns this level, allow purchase
                        canPurchase = true;
                    } else {
                        // Check actual requirements for new enchants
                        try {
                            canPurchase = plugin.getEnchantManager().meetsRequirements(player, enchant.getName(), level).join();
                        } catch (Exception e) {
                            canPurchase = false;
                        }
                    }

                    if (!canPurchase) {
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
                                    plugin.getSoulManager().getSouls(player.getUniqueId()).thenAccept(currentSouls -> {
                                        String message = plugin.getConfigManager().getString("config.yml",
                                                "messages.insufficient-souls", "&cYou don't have enough souls!");
                                        player.sendMessage(ColorUtils.color(message));

                                        String detailMessage = plugin.getConfigManager().getString("config.yml",
                                                "messages.souls-needed-detail", "&cYou need &6{cost} &csouls but only have &6{current}&c!");
                                        detailMessage = detailMessage.replace("{cost}", String.valueOf(cost))
                                                .replace("{current}", String.valueOf(currentSouls));
                                        player.sendMessage(ColorUtils.color(detailMessage));

                                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                                    });
                                    return;
                                }

                                // FIXED: Proceed with purchase
                                plugin.getSoulManager().removeSouls(player.getUniqueId(), cost)
                                        .thenAccept(success -> {
                                            if (success) {
                                                plugin.getServer().getScheduler().runTask(plugin, () -> {
                                                    // Create the enchant dye
                                                    ItemStack dye = plugin.getEnchantManager().createEnchantDye(enchant, level);

                                                    // Give the dye to player
                                                    HashMap<Integer, ItemStack> remaining = player.getInventory().addItem(dye);
                                                    for (ItemStack item : remaining.values()) {
                                                        player.getWorld().dropItemNaturally(player.getLocation(), item);
                                                    }

                                                    // Success messages
                                                    String successMessage = plugin.getConfigManager().getString("config.yml",
                                                            "messages.enchant-purchase-success", "&aYou purchased {enchant} Level {level} Dye for {cost} souls!");
                                                    successMessage = successMessage.replace("{enchant}", enchant.getDisplayName())
                                                            .replace("{level}", String.valueOf(level))
                                                            .replace("{cost}", String.valueOf(cost));
                                                    player.sendMessage(ColorUtils.color(successMessage));

                                                    String instructionMessage = plugin.getConfigManager().getString("config.yml",
                                                            "messages.enchant-dye-instruction", "&eDrag and drop the dye onto a compatible item to apply the enchant!");
                                                    player.sendMessage(ColorUtils.color(instructionMessage));

                                                    // Play success sound
                                                    String successSound = plugin.getConfigManager().getString("config.yml",
                                                            "sounds.purchase-success", "ENTITY_PLAYER_LEVELUP");
                                                    try {
                                                        Sound sound = Sound.valueOf(successSound);
                                                        player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
                                                    } catch (IllegalArgumentException e) {
                                                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                                                    }

                                                    // FIXED: Refresh the GUI to maintain correct status
                                                    String currentTitle = ChatColor.stripColor(player.getOpenInventory().getTitle());
                                                    if (currentTitle.equals("Soul Shop (Page 2)")) {
                                                        player.openInventory(plugin.getGuiManager().createSoulShopPage2Gui(player));
                                                    } else {
                                                        player.openInventory(plugin.getGuiManager().createSoulShopGui(player));
                                                    }
                                                });
                                            } else {
                                                String errorMessage = plugin.getConfigManager().getString("config.yml",
                                                        "messages.soul-transaction-failed", "&cFailed to remove souls from your account!");
                                                player.sendMessage(ColorUtils.color(errorMessage));
                                                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                                            }
                                        });
                            });
                })
                .exceptionally(throwable -> {
                    String errorMessage = plugin.getConfigManager().getString("config.yml",
                            "messages.purchase-error", "&cError processing purchase: {error}");
                    errorMessage = errorMessage.replace("{error}", throwable.getMessage());
                    player.sendMessage(ColorUtils.color(errorMessage));
                    plugin.getLogger().warning("Error in purchaseEnchantBookFixed: " + throwable.getMessage());
                    return null;
                });
    }

}