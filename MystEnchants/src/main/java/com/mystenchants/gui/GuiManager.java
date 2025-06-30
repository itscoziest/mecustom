
package com.mystenchants.gui;

import com.mystenchants.MystEnchants;
import com.mystenchants.enchants.CustomEnchant;
import com.mystenchants.enchants.EnchantTier;
import com.mystenchants.enchants.UnlockRequirement;
import com.mystenchants.managers.PlayerDataManager;
import com.mystenchants.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.stream.Collectors;

/**
 * FIXED: GuiManager with proper soul shop logic and complete error fixes
 */
public class GuiManager {

    private final MystEnchants plugin;
    private final Map<String, GuiTemplate> templates = new HashMap<>();

    public GuiManager(MystEnchants plugin) {
        this.plugin = plugin;
        loadGuiTemplates();
    }

    /**
     * Loads GUI templates from configuration
     */
    private void loadGuiTemplates() {
        ConfigurationSection guisSection = plugin.getConfigManager().getGuisConfig().getConfigurationSection("guis");
        if (guisSection == null) return;

        for (String guiName : guisSection.getKeys(false)) {
            ConfigurationSection guiSection = guisSection.getConfigurationSection(guiName);
            if (guiSection != null) {
                GuiTemplate template = loadGuiTemplate(guiName, guiSection);
                templates.put(guiName, template);
            }
        }
    }

    /**
     * Loads a single GUI template
     */
    private GuiTemplate loadGuiTemplate(String name, ConfigurationSection section) {
        String title = ColorUtils.color(section.getString("title", name));
        int size = section.getInt("size", 54);

        Map<String, GuiItem> items = new HashMap<>();
        ConfigurationSection itemsSection = section.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String itemName : itemsSection.getKeys(false)) {
                ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemName);
                if (itemSection != null) {
                    GuiItem item = loadGuiItem(itemSection);
                    items.put(itemName, item);
                }
            }
        }

        // Load back button if exists
        GuiItem backButton = null;
        ConfigurationSection backSection = section.getConfigurationSection("back-button");
        if (backSection != null) {
            backButton = loadGuiItem(backSection);
        }

        return new GuiTemplate(name, title, size, items, backButton);
    }

    /**
     * Loads a GUI item from configuration
     */
    private GuiItem loadGuiItem(ConfigurationSection section) {
        int slot = section.getInt("slot", 0);
        Material material = Material.valueOf(section.getString("material", "STONE"));
        String name = ColorUtils.color(section.getString("name", ""));
        List<String> lore = ColorUtils.color(section.getStringList("lore"));
        boolean glow = section.getBoolean("glow", false);

        return new GuiItem(slot, material, name, lore, glow);
    }

    /**
     * Creates the main enchants GUI (Hide Mystical tier)
     */
    public Inventory createEnchantsGui(Player player) {
        GuiTemplate template = templates.get("enchants");
        if (template == null) {
            return Bukkit.createInventory(null, 54, ColorUtils.color("&6&lEnchants"));
        }

        Inventory inventory = Bukkit.createInventory(null, template.getSize(), template.getTitle());

        // Add tier buttons (SKIP MYSTICAL)
        for (EnchantTier tier : EnchantTier.values()) {
            if (tier == EnchantTier.MYSTICAL) continue; // Hide mystical tier

            GuiItem tierItem = template.getItems().get(tier.name().toLowerCase());
            if (tierItem != null) {
                ItemStack item = createTierItem(tier, player);
                inventory.setItem(tierItem.getSlot(), item);
            }
        }

        // Fill empty slots
        fillEmptySlots(inventory);

        return inventory;
    }

    /**
     * Creates a tier-specific enchants GUI
     */
    public Inventory createTierGui(Player player, EnchantTier tier) {
        String title = ColorUtils.color("&6&l" + tier.getDisplayName() + " Enchants");
        Inventory inventory = Bukkit.createInventory(null, 54, title);

        List<CustomEnchant> enchants = plugin.getEnchantManager().getEnchantsByTier(tier);
        PlayerDataManager playerData = plugin.getPlayerDataManager();

        int slot = 10;
        for (CustomEnchant enchant : enchants) {
            if (slot > 43) break; // Prevent overflow

            ItemStack item = createEnchantDisplayItem(enchant, player);
            inventory.setItem(slot, item);

            // Move to next slot (skip edges)
            slot++;
            if (slot % 9 == 8) slot += 2;
        }

        // Add back button
        GuiTemplate template = templates.get("tier-view");
        if (template != null && template.getBackButton() != null) {
            GuiItem backButton = template.getBackButton();
            ItemStack backItem = createItemStack(backButton.getMaterial(), backButton.getName(), backButton.getLore());
            inventory.setItem(backButton.getSlot(), backItem);
        }

        fillEmptySlots(inventory);
        return inventory;
    }

    /**
     * Creates the oracle GUI - ENHANCED to include Redemption enchant
     */
    public Inventory createOracleGui(Player player) {
        GuiTemplate template = templates.get("oracle");
        if (template == null) {
            return Bukkit.createInventory(null, 54, ColorUtils.color("&6&lOracle"));
        }

        Inventory inventory = Bukkit.createInventory(null, template.getSize(), template.getTitle());

        PlayerDataManager playerData = plugin.getPlayerDataManager();
        Map<String, Integer> playerEnchants = playerData.getPlayerEnchants(player.getUniqueId()).join();

        // Add enchants by tier (INCLUDING MYSTICAL if unlocked)
        int slot = 10;
        for (EnchantTier tier : EnchantTier.values()) {
            List<CustomEnchant> tierEnchants = plugin.getEnchantManager().getEnchantsByTier(tier);

            for (CustomEnchant enchant : tierEnchants) {
                // Show Mystical enchants if player has unlocked them
                if (tier == EnchantTier.MYSTICAL) {
                    if (playerEnchants.containsKey(enchant.getName())) {
                        if (slot > 43) break;

                        ItemStack item = createOracleEnchantItem(enchant, player);
                        inventory.setItem(slot, item);

                        slot++;
                        if (slot % 9 == 8) slot += 2;
                    }
                } else {
                    // Regular enchants (non-mystical)
                    if (playerEnchants.containsKey(enchant.getName())) {
                        if (slot > 43) break;

                        ItemStack item = createOracleEnchantItem(enchant, player);
                        inventory.setItem(slot, item);

                        slot++;
                        if (slot % 9 == 8) slot += 2;
                    }
                }
            }
        }

        // Add purchase section button
        GuiItem purchaseItem = template.getItems().get("purchase-section");
        if (purchaseItem != null) {
            ItemStack item = createItemStack(purchaseItem.getMaterial(), purchaseItem.getName(), purchaseItem.getLore());
            inventory.setItem(purchaseItem.getSlot(), item);
        }

        fillEmptySlots(inventory);
        return inventory;
    }

    /**
     * ENHANCED: Creates enchant details GUI with Redemption support
     */
    public Inventory createOracleDetailsGui(Player player, CustomEnchant enchant) {
        String title = ColorUtils.color("&6&l" + enchant.getDisplayName() + " Details");
        Inventory inventory = Bukkit.createInventory(null, 27, title);

        PlayerDataManager playerData = plugin.getPlayerDataManager();
        int currentLevel = playerData.getEnchantLevel(player.getUniqueId(), enchant.getName()).join();

        // Handle Redemption enchant (single level, special case)
        if (enchant.getName().equals("redemption")) {
            createRedemptionLayout(inventory, enchant, currentLevel, player);
        } else if (enchant.getMaxLevel() == 1) {
            createSingleLevelLayout(inventory, enchant, currentLevel, player);
        } else if (enchant.getMaxLevel() == 2) {
            createTwoLevelLayout(inventory, enchant, currentLevel, player);
        } else if (enchant.getMaxLevel() == 3) {
            createThreeLevelLayout(inventory, enchant, currentLevel, player);
        }

        fillEmptySlots(inventory);
        return inventory;
    }

    private void createSingleLevelLayout(Inventory inventory, CustomEnchant enchant, int currentLevel, Player player) {
        ItemStack levelItem = createLevelItem(enchant, 1, currentLevel, player);
        inventory.setItem(13, levelItem);
    }

    private void createTwoLevelLayout(Inventory inventory, CustomEnchant enchant, int currentLevel, Player player) {
        ItemStack level1Item = createLevelItem(enchant, 1, currentLevel, player);
        inventory.setItem(11, level1Item);

        Material glassMaterial;
        if (currentLevel == 0) {
            glassMaterial = Material.RED_STAINED_GLASS_PANE; // Red when level 1 is locked
        } else if (currentLevel == 1) {
            glassMaterial = Material.YELLOW_STAINED_GLASS_PANE; // Yellow when level 1 unlocked but level 2 locked
        } else {
            glassMaterial = Material.LIME_STAINED_GLASS_PANE; // Lime when both levels unlocked
        }

        ItemStack glassPane = createGlassPane(glassMaterial);
        inventory.setItem(12, glassPane);
        inventory.setItem(13, glassPane);

        ItemStack level2Item = createLevelItem(enchant, 2, currentLevel, player);
        inventory.setItem(14, level2Item);
    }

    private void createThreeLevelLayout(Inventory inventory, CustomEnchant enchant, int currentLevel, Player player) {
        ItemStack level1Item = createLevelItem(enchant, 1, currentLevel, player);
        inventory.setItem(10, level1Item);

        // Glass panes after level 1
        Material glass1Material;
        if (currentLevel == 0) {
            glass1Material = Material.RED_STAINED_GLASS_PANE; // Red when level 1 is locked
        } else if (currentLevel == 1) {
            glass1Material = Material.YELLOW_STAINED_GLASS_PANE; // Yellow when level 1 unlocked but level 2 locked
        } else {
            glass1Material = Material.LIME_STAINED_GLASS_PANE; // Lime when level 2+ unlocked
        }

        ItemStack glassPane1 = createGlassPane(glass1Material);
        inventory.setItem(11, glassPane1);
        inventory.setItem(12, glassPane1);

        ItemStack level2Item = createLevelItem(enchant, 2, currentLevel, player);
        inventory.setItem(13, level2Item);

        // Glass panes after level 2
        Material glass2Material;
        if (currentLevel < 2) {
            glass2Material = Material.RED_STAINED_GLASS_PANE; // Red when level 2 is locked
        } else if (currentLevel == 2) {
            glass2Material = Material.YELLOW_STAINED_GLASS_PANE; // Yellow when level 2 unlocked but level 3 locked
        } else {
            glass2Material = Material.LIME_STAINED_GLASS_PANE; // Lime when all levels unlocked
        }

        ItemStack glassPane2 = createGlassPane(glass2Material);
        inventory.setItem(14, glassPane2);
        inventory.setItem(15, glassPane2);

        ItemStack level3Item = createLevelItem(enchant, 3, currentLevel, player);
        inventory.setItem(16, level3Item);
    }

    /**
     * ADDED: Creates special layout for Redemption enchant
     */
    private void createRedemptionLayout(Inventory inventory, CustomEnchant enchant, int currentLevel, Player player) {
        List<String> lore = new ArrayList<>();

        // Status
        if (currentLevel >= 1) {
            lore.add(ColorUtils.color("&a&l✓ REDEMPTION UNLOCKED"));
            lore.add("");
            lore.add(ColorUtils.color("&6Effect:"));
            lore.add(ColorUtils.color("&fKeep this item upon death"));
            lore.add(ColorUtils.color("&7(One-time use, enchant is consumed)"));
            lore.add("");
            lore.add(ColorUtils.color("&a&lCongratulations!"));
            lore.add(ColorUtils.color("&7You have proven yourself worthy"));
            lore.add(ColorUtils.color("&7by defeating the Redemption Boss!"));
        } else {
            lore.add(ColorUtils.color("&c&l✗ REDEMPTION LOCKED"));
            lore.add("");
            lore.add(ColorUtils.color("&6Effect:"));
            lore.add(ColorUtils.color("&fKeep this item upon death"));
            lore.add(ColorUtils.color("&7(One-time use, enchant is consumed)"));
            lore.add("");
            lore.add(ColorUtils.color("&cRequirements:"));
            lore.add(ColorUtils.color("&7Defeat the Redemption Boss"));
            lore.add("");
            lore.add(ColorUtils.color("&7Use &f/redemption &7to start the fight"));
            lore.add(ColorUtils.color("&c&lWARNING: &7Extremely difficult!"));
        }

        // Use special Redemption material
        Material itemMaterial = currentLevel >= 1 ? Material.PINK_DYE : Material.GRAY_DYE;

        ItemStack redemptionItem = createItemStack(itemMaterial,
                ColorUtils.color("&d&l" + enchant.getDisplayName()),
                lore);

        inventory.setItem(13, redemptionItem); // Center slot
    }

    /**
     * Refreshes the Soul Shop GUI for a player
     */
    public void refreshSoulShopGui(Player player) {
        // Check if player has soul shop open
        String currentTitle = ChatColor.stripColor(player.getOpenInventory().getTitle());
        if (currentTitle.equals("Soul Shop")) {
            // Refresh the GUI
            player.openInventory(createSoulShopGui(player));
        }
    }

    /**
     * Creates a level item showing enchant information for specific level
     */
    private ItemStack createLevelItem(CustomEnchant enchant, int level, int currentLevel, Player player) {
        List<String> lore = new ArrayList<>();

        // Level status
        if (currentLevel >= level) {
            lore.add(ColorUtils.color("&a&l✓ LEVEL " + level + " UNLOCKED"));
        } else {
            lore.add(ColorUtils.color("&c&l✗ LEVEL " + level + " LOCKED"));
        }

        lore.add("");

        // Effect description for this level
        lore.add(ColorUtils.color("&6Level " + level + " Effect:"));
        String effectDescription = getEnchantLevelEffectDescription(enchant, level);
        lore.add(ColorUtils.color("&f" + effectDescription));
        lore.add("");

        // Show unlock requirements if locked
        if (currentLevel < level) {
            UnlockRequirement req = enchant.getUnlockRequirement(level);
            if (req != null && req.getType() != com.mystenchants.enchants.RequirementType.NONE) {
                lore.add(ColorUtils.color("&cRequirements:"));

                if (req.getType().requiresStatistics()) {
                    String statName = getStatisticName(req.getType());
                    try {
                        Long current = plugin.getPlayerDataManager().getStatistic(player.getUniqueId(), statName).join();
                        if (current == null) current = 0L;

                        lore.add(ColorUtils.color("&7" + req.getFormattedMessage(current)));

                        // Add both percentage and current/max progress
                        double progress = req.getProgress(current);
                        lore.add(ColorUtils.color("&7Progress: &f" + String.format("%.1f", progress) + "%"));
                        lore.add(ColorUtils.color("&7Progress: &f" + formatProgress(current, req.getAmount())));
                    } catch (Exception e) {
                        lore.add(ColorUtils.color("&7" + req.getMessage()));
                    }
                } else {
                    lore.add(ColorUtils.color("&7" + req.getMessage()));
                }
            } else {
                lore.add(ColorUtils.color("&aNo requirements needed!"));
            }
        }

        // Use enchant tier material
        Material itemMaterial = enchant.getTier().getGuiItem();

        // If locked, use gray version
        if (currentLevel < level) {
            itemMaterial = Material.GRAY_DYE;
        }

        return createItemStack(itemMaterial,
                ColorUtils.color(enchant.getTier().getColor() + "&l" + enchant.getDisplayName() + " Level " + level),
                lore);
    }

    /**
     * Formats progress as current/max with formatting for large numbers
     */
    private String formatProgress(long current, long max) {
        return formatLargeNumber(current) + "/" + formatLargeNumber(max);
    }

    /**
     * Formats large numbers with K, M, B suffixes
     */
    private String formatLargeNumber(long number) {
        if (number < 1000) {
            return String.valueOf(number);
        } else if (number < 1000000) {
            return String.format("%.1fK", number / 1000.0);
        } else if (number < 1000000000) {
            return String.format("%.1fM", number / 1000000.0);
        } else {
            return String.format("%.1fB", number / 1000000000.0);
        }
    }

    /**
     * Creates a glass pane with no name or lore
     */
    private ItemStack createGlassPane(Material material) {
        return createItemStack(material, " ", Arrays.asList());
    }

    /**
     * FIXED: Gets the effect description for a specific enchant level
     */
    private String getEnchantLevelEffectDescription(CustomEnchant enchant, int level) {
        switch (enchant.getName()) {
            case "tempo":
                return "Haste " + getRomanNumeral(level);
            case "scholar":
                double[] scholarMultipliers = {1.05, 1.08, 1.15};
                return "+" + (int)((scholarMultipliers[level-1] - 1.0) * 100) + "% EXP";
            case "serrate":
                double[] serrateDurations = {1.5, 2.0, 3.5};
                return serrateDurations[level-1] + " seconds bleed";
            case "rejuvenate":
                int[] healAmounts = {2, 3, 5};
                double[] healChances = {10, 12, 17};
                return healAmounts[level-1] + " hearts, " + (int)healChances[level-1] + "% chance";
            case "backup":
                return level + " iron golem" + (level > 1 ? "s" : "");
            case "guillotine":
                double[] headChances = {10, 30, 70};
                return (int)headChances[level-1] + "% head drop chance";
            case "pace":
                return "Speed " + getRomanNumeral(level);
            case "pantsed":
                double[] stealChances = {3, 7, 12};
                return (int)stealChances[level-1] + "% steal chance";
            case "detonate":
                String[] areaSizes = {"2x2", "3x3", "5x5"};
                return areaSizes[level-1] + " area mining";
            case "zetsubo":
                return "Strength " + getRomanNumeral(level);
            case "almighty_push":
                return "Blast players away";
            case "redemption":
                return "Keep item on death";
            default:
                return "Level " + level + " effect";
        }
    }

    /**
     * Creates the oracle purchase GUI - FIXED to show EXP upgradeable enchants
     */
    public Inventory createOraclePurchaseGui(Player player) {
        String title = ColorUtils.color("&a&lPurchase Upgrades");
        Inventory inventory = Bukkit.createInventory(null, 54, title);

        PlayerDataManager playerData = plugin.getPlayerDataManager();
        Map<String, Integer> playerEnchants = playerData.getPlayerEnchants(player.getUniqueId()).join();

        int slot = 10;
        int upgradesFound = 0;

        for (Map.Entry<String, Integer> entry : playerEnchants.entrySet()) {
            CustomEnchant enchant = plugin.getEnchantManager().getEnchant(entry.getKey());
            int currentLevel = entry.getValue();

            if (enchant != null && currentLevel < enchant.getMaxLevel()) {
                if (slot > 43) break;

                // Check if next level requirements are EXP-based
                int nextLevel = currentLevel + 1;
                UnlockRequirement req = enchant.getUnlockRequirement(nextLevel);

                // Show if it's EXP based OR if no specific requirement (default to EXP upgrade)
                boolean isExpUpgrade = (req == null ||
                        req.getType() == com.mystenchants.enchants.RequirementType.NONE ||
                        req.getType() == com.mystenchants.enchants.RequirementType.EXP_LEVELS);

                if (isExpUpgrade) {
                    ItemStack item = createPurchaseItem(enchant, player, nextLevel);
                    inventory.setItem(slot, item);
                    upgradesFound++;

                    slot++;
                    if (slot % 9 == 8) slot += 2;
                }
            }
        }

        // If no upgrades found, add info item
        if (upgradesFound == 0) {
            ItemStack infoItem = createItemStack(Material.BARRIER,
                    ColorUtils.color("&c&lNo EXP Upgrades Available"),
                    Arrays.asList(
                            ColorUtils.color("&7You don't have any enchants that"),
                            ColorUtils.color("&7can be upgraded with EXP levels."),
                            ColorUtils.color(""),
                            ColorUtils.color("&7EXP Upgradeable Enchants:"),
                            ColorUtils.color("&7• &bTempo &7(Mining speed)"),
                            ColorUtils.color("&7• &bScholar &7(More EXP from kills)"),
                            ColorUtils.color(""),
                            ColorUtils.color("&7Unlock these from the Soul Shop first!")
                    ));
            inventory.setItem(22, infoItem);
        }

        // Add back button
        GuiTemplate template = templates.get("oracle-purchase");
        if (template != null && template.getBackButton() != null) {
            GuiItem backButton = template.getBackButton();
            ItemStack backItem = createItemStack(backButton.getMaterial(), backButton.getName(), backButton.getLore());
            inventory.setItem(backButton.getSlot(), backItem);
        }

        fillEmptySlots(inventory);
        return inventory;
    }

    /**
     * FIXED: Creates page 1 of the soul shop GUI - Common, Uncommon, Rare, and Ultimate enchants
     */
    public Inventory createSoulShopGui(Player player) {
        String title = ColorUtils.color("&6&lSoul Shop");
        Inventory inventory = Bukkit.createInventory(null, 54, title);

        int slot = 10;

        // Get player's current enchant levels
        Map<String, Integer> playerEnchants = plugin.getPlayerDataManager().getPlayerEnchants(player.getUniqueId()).join();

        // FIXED: Page 1 enchants - Common (1), Uncommon (2), Rare (3), Ultimate (4) tiers
        String[] page1Enchants = {
                // Common enchants (Level 1)
                "tempo", "scholar",
                // Uncommon enchants (Level 2)
                "serrate", "rejuvenate",
                // Rare enchants (Level 3)
                "backup", "guillotine",
                // Ultimate enchants (Level 4)
                "pace", "pantsed"
        };

        for (String enchantName : page1Enchants) {
            CustomEnchant enchant = plugin.getEnchantManager().getEnchant(enchantName);
            if (enchant != null) {
                // Show ALL levels of this enchant
                for (int level = 1; level <= enchant.getMaxLevel(); level++) {
                    if (slot > 43) break;

                    ItemStack book = createSoulShopBook(enchant, level, player, playerEnchants);

                    // Get configurable slot for this enchant level
                    int enchantSlot = getConfigurableSlot(enchant.getName(), level, slot);

                    inventory.setItem(enchantSlot, book);
                    slot = Math.max(slot + 1, enchantSlot + 1);
                }

                // Skip slots if we placed items in configured positions
                if (slot % 9 == 8) slot += 2;
            }
        }

        // Add navigation to page 2
        ItemStack nextPageButton = createItemStack(Material.ARROW,
                ColorUtils.color("&a&lNext Page →"),
                Arrays.asList(
                        ColorUtils.color("&7View Legendary & Mystical enchants"),
                        ColorUtils.color("&eClick to navigate!")
                ));
        inventory.setItem(53, nextPageButton);

        // Add page info
        ItemStack pageInfoButton = createItemStack(Material.GHAST_TEAR,
                ColorUtils.color("&6&lSoul Shop (Page 1)"),
                Arrays.asList(
                        ColorUtils.color("&7Purchase enchant books and perks"),
                        ColorUtils.color("&7using souls collected from kills!"),
                        ColorUtils.color(""),
                        ColorUtils.color("&7• &f1 soul &7per mob kill"),
                        ColorUtils.color("&7• &f5 souls &7per player kill"),
                        ColorUtils.color(""),
                        ColorUtils.color("&bCommon &7• &aUncommon &7• &eRare &7• &6Ultimate"),
                        ColorUtils.color("&6Page 1 of 2")
                ));
        inventory.setItem(4, pageInfoButton);

        fillEmptySlots(inventory);
        return inventory;
    }

    /**
     * FIXED: Creates page 2 of the soul shop GUI - Legendary and Mystical enchants
     */
    public Inventory createSoulShopPage2Gui(Player player) {
        String title = ColorUtils.color("&6&lSoul Shop (Page 2)");
        Inventory inventory = Bukkit.createInventory(null, 54, title);

        Map<String, Integer> playerEnchants = plugin.getPlayerDataManager().getPlayerEnchants(player.getUniqueId()).join();

        // FIXED: Page 2 enchants - Legendary (5) and Mystical (6) enchants
        String[] page2Enchants = {
                // Legendary enchants (Level 5)
                "detonate", "almighty_push",
                // Mystical enchants (Level 6)
                "redemption", "zetsubo"
        };

        for (String enchantName : page2Enchants) {
            CustomEnchant enchant = plugin.getEnchantManager().getEnchant(enchantName);
            if (enchant != null) {
                for (int level = 1; level <= enchant.getMaxLevel(); level++) {
                    ItemStack book = createSoulShopBook(enchant, level, player, playerEnchants);

                    // FIXED: Use page 2 specific slot configuration
                    int configuredSlot = getConfigurableSlotPage2(enchant.getName(), level);

                    if (configuredSlot != -1) {
                        inventory.setItem(configuredSlot, book);
                    }
                }
            }
        }

        // Add navigation buttons with DISTINCT names
        ItemStack previousPage = createItemStack(Material.ARROW,
                ColorUtils.color("&c&l← Previous Page"),
                Arrays.asList(
                        ColorUtils.color("&7Go back to page 1"),
                        ColorUtils.color("&eClick to navigate!")
                ));
        inventory.setItem(45, previousPage);

        // Add page info
        ItemStack pageInfo = createItemStack(Material.GHAST_TEAR,
                ColorUtils.color("&6&lSoul Shop (Page 2)"),
                Arrays.asList(
                        ColorUtils.color("&7Purchase enchant books and perks"),
                        ColorUtils.color("&7using souls collected from kills!"),
                        ColorUtils.color(""),
                        ColorUtils.color("&7• &f1 soul &7per mob kill"),
                        ColorUtils.color("&7• &f5 souls &7per player kill"),
                        ColorUtils.color(""),
                        ColorUtils.color("&cLegendary &7• &dMystical"),
                        ColorUtils.color("&6Page 2 of 2")
                ));
        inventory.setItem(4, pageInfo);

        fillEmptySlots(inventory);
        return inventory;
    }

    /**
     * FIXED: Gets configurable slot position for page 2 enchants with proper fallback
     */
    private int getConfigurableSlotPage2(String enchantName, int level) {
        String slotPath = "shop.layout-page-2." + enchantName + ".level-" + level + ".slot";
        int configuredSlot = plugin.getConfigManager().getPerksConfig().getInt(slotPath, -1);

        // Return the configured slot if valid, otherwise return -1 to skip
        if (configuredSlot >= 10 && configuredSlot <= 43) {
            return configuredSlot;
        }

        return -1; // Invalid slot, don't place item
    }

    // In GuiManager.java - Find and REPLACE these two methods:

    /**
     * UPDATED: Simplified - just checks basic availability
     */
    private boolean canPurchaseEnchantLevel(Player player, CustomEnchant enchant, int level, Map<String, Integer> playerEnchants) {
        Integer currentLevel = playerEnchants.get(enchant.getName());

        // Can't purchase if already owned
        if (currentLevel != null && currentLevel >= level) {
            return false;
        }

        // For level 1, just check if they don't have it
        if (level == 1) {
            return currentLevel == null || currentLevel < 1;
        }

        // For higher levels, must have previous level
        if (currentLevel == null || currentLevel < level - 1) {
            return false;
        }

        // Must be the next level (can't skip)
        return level == currentLevel + 1;
    }

    /**
     * Creates the perks GUI - FIXED to use PerkManager
     */
    public Inventory createPerksGui(Player player) {
        GuiTemplate template = templates.get("perks");
        if (template == null) {
            return Bukkit.createInventory(null, 54, ColorUtils.color("&6&lPerks"));
        }

        Inventory inventory = Bukkit.createInventory(null, template.getSize(), template.getTitle());

        // Add perk items from template using PerkManager
        for (Map.Entry<String, GuiItem> entry : template.getItems().entrySet()) {
            GuiItem perkGuiItem = entry.getValue();

            // Use PerkManager to create the actual perk shop item
            ItemStack item = plugin.getPerkManager().createPerkShopItem(entry.getKey());

            if (item != null) {
                inventory.setItem(perkGuiItem.getSlot(), item);
            } else {
                // Fallback: create basic item if PerkManager fails
                ItemStack fallbackItem = createItemStack(perkGuiItem.getMaterial(), perkGuiItem.getName(), perkGuiItem.getLore());
                inventory.setItem(perkGuiItem.getSlot(), fallbackItem);
            }
        }

        fillEmptySlots(inventory);
        return inventory;
    }

    /**
     * Creates redemption confirmation GUI
     */
    public Inventory createRedemptionGui(Player player) {
        String title = ColorUtils.color("&4&lRedemption Boss Fight");
        Inventory inventory = Bukkit.createInventory(null, 27, title);

        // Confirm button
        ItemStack confirm = createItemStack(Material.GREEN_WOOL,
                ColorUtils.color("&a&lConfirm"),
                Arrays.asList(
                        ColorUtils.color("&7Start the redemption boss fight"),
                        ColorUtils.color(""),
                        ColorUtils.color("&c&lWARNING:"),
                        ColorUtils.color("&cYou will lose all items if you die!"),
                        ColorUtils.color(""),
                        ColorUtils.color("&eClick to confirm!")
                ));
        inventory.setItem(11, confirm);

        // Cancel button
        ItemStack cancel = createItemStack(Material.RED_WOOL,
                ColorUtils.color("&c&lCancel"),
                Arrays.asList(
                        ColorUtils.color("&7Cancel the redemption fight"),
                        ColorUtils.color(""),
                        ColorUtils.color("&eClick to cancel!")
                ));
        inventory.setItem(15, cancel);

        fillEmptySlots(inventory);
        return inventory;
    }

    // Helper methods for creating specific items

    private ItemStack createTierItem(EnchantTier tier, Player player) {
        PlayerDataManager playerData = plugin.getPlayerDataManager();
        List<CustomEnchant> tierEnchants = plugin.getEnchantManager().getEnchantsByTier(tier);
        Map<String, Integer> playerEnchants = playerData.getPlayerEnchants(player.getUniqueId()).join();

        int unlockedCount = 0;
        for (CustomEnchant enchant : tierEnchants) {
            if (playerEnchants.containsKey(enchant.getName())) {
                unlockedCount++;
            }
        }

        List<String> lore = Arrays.asList(
                ColorUtils.color("&7Level " + tier.getLevel() + " enchants"),
                ColorUtils.color("&7Unlocked: &f" + unlockedCount + "&7/&f" + tierEnchants.size()),
                ColorUtils.color(""),
                ColorUtils.color("&eClick to view enchants!")
        );

        return createItemStack(tier.getGuiItem(), ColorUtils.color(tier.getColor() + "&l" + tier.getDisplayName() + " Enchants"), lore);
    }

    private ItemStack createEnchantDisplayItem(CustomEnchant enchant, Player player) {
        PlayerDataManager playerData = plugin.getPlayerDataManager();
        int playerLevel = playerData.getEnchantLevel(player.getUniqueId(), enchant.getName()).join();

        List<String> lore = new ArrayList<>();
        lore.addAll(enchant.getDescription());
        lore.add("");

        // Show compatible items
        lore.add(ColorUtils.color("&a&lApplicable To:"));
        for (Material material : enchant.getApplicableItems()) {
            String itemName = formatMaterialName(material);
            lore.add(ColorUtils.color("&7• " + itemName));
        }
        lore.add("");

        // Show unlock status like in reference image
        if (playerLevel > 0) {
            lore.add(ColorUtils.color("&a&l✓ UNLOCKED"));
            lore.add(ColorUtils.color("&7Current Level: &f" + playerLevel));
            lore.add(ColorUtils.color("&7Max Level: &f" + enchant.getMaxLevel()));

            if (playerLevel < enchant.getMaxLevel()) {
                lore.add("");
                lore.add(ColorUtils.color("&eUpgrade available in Oracle!"));
            }
        } else {
            lore.add(ColorUtils.color("&c&l✗ LOCKED"));
            lore.add(ColorUtils.color("&7Available in Soul Shop!"));
        }

        lore.add("");
        lore.add(ColorUtils.color("&eClick to view details!"));

        Material displayMaterial = playerLevel > 0 ? enchant.getTier().getGuiItem() : Material.GRAY_DYE;
        return createItemStack(displayMaterial,
                ColorUtils.color(enchant.getTier().getColor() + "&l" + enchant.getDisplayName()), lore);
    }

    private ItemStack createOracleEnchantItem(CustomEnchant enchant, Player player) {
        PlayerDataManager playerData = plugin.getPlayerDataManager();
        int currentLevel = playerData.getEnchantLevel(player.getUniqueId(), enchant.getName()).join();

        List<String> lore = new ArrayList<>();
        lore.add(ColorUtils.color("&7Current Level: &f" + currentLevel));
        lore.add(ColorUtils.color("&7Max Level: &f" + enchant.getMaxLevel()));
        lore.add("");
        lore.addAll(enchant.getDescription());
        lore.add("");
        lore.add(ColorUtils.color("&eClick to view details!"));

        return createItemStack(enchant.getTier().getGuiItem(),
                ColorUtils.color(enchant.getTier().getColor() + "&l" + enchant.getDisplayName()), lore);
    }

    private ItemStack createPurchaseItem(CustomEnchant enchant, Player player, int level) {
        int expCost = plugin.getConfigManager().getInt("config.yml",
                "exp-costs." + enchant.getName() + ".level-" + level, 50);

        List<String> lore = new ArrayList<>();
        lore.add(ColorUtils.color("&7Upgrade to Level " + level));
        lore.add("");
        lore.add(ColorUtils.color("&7Cost: &a" + expCost + " EXP Levels"));
        lore.add("");

        if (player.getLevel() >= expCost) {
            lore.add(ColorUtils.color("&aYou can afford this upgrade!"));
            lore.add(ColorUtils.color("&eClick to purchase!"));
        } else {
            lore.add(ColorUtils.color("&cYou need " + (expCost - player.getLevel()) + " more EXP levels!"));
        }

        return createItemStack(enchant.getTier().getGuiItem(),
                ColorUtils.color(enchant.getTier().getColor() + "&l" + enchant.getDisplayName() + " " + level), lore);
    }

    /**
     * Gets configurable slot position for enchant level
     */
    private int getConfigurableSlot(String enchantName, int level, int defaultSlot) {
        String slotPath = "shop.layout." + enchantName + ".level-" + level + ".slot";
        int configuredSlot = plugin.getConfigManager().getPerksConfig().getInt(slotPath, -1);

        if (configuredSlot >= 10 && configuredSlot <= 43) {
            return configuredSlot;
        }

        return defaultSlot;
    }

    /**
     * UPDATED: Creates soul shop book with availability checking
     */
    private ItemStack createSoulShopBook(CustomEnchant enchant, int level, Player player, Map<String, Integer> playerEnchants) {
        String costPath = "shop.items." + enchant.getName() + "-book-level-" + level + ".cost";
        String fallbackPath = "shop.items." + enchant.getName() + "-book.cost";
        int cost = plugin.getConfigManager().getPerksConfig().getInt(costPath,
                plugin.getConfigManager().getPerksConfig().getInt(fallbackPath, 500));

        Integer currentLevel = playerEnchants.get(enchant.getName());
        boolean isPurchasable = canPurchaseEnchantLevel(player, enchant, level, playerEnchants);
        boolean meetsRequirements = false;
        String statusMessage = "";

        // Determine status and requirements
        // Determine status and requirements with PROPER color formatting
        if (currentLevel != null && currentLevel >= level) {
            statusMessage = ColorUtils.color("&a&l✓ OWNED");
            isPurchasable = false;
        } else if (level > 1 && (currentLevel == null || currentLevel < level - 1)) {
            statusMessage = ColorUtils.color("&c&l✗ REQUIRES LEVEL " + (level - 1));
            isPurchasable = false;
        } else {
            // Check if meets unlock requirements
            try {
                meetsRequirements = plugin.getEnchantManager()
                        .meetsRequirements(player, enchant.getName(), level).join();

                if (meetsRequirements) {
                    statusMessage = ColorUtils.color("&a&l✓ AVAILABLE");
                    isPurchasable = true;
                } else {
                    statusMessage = ColorUtils.color("&c&l✗ REQUIREMENTS NOT MET");
                    isPurchasable = false;
                }
            } catch (Exception e) {
                statusMessage = ColorUtils.color("&c&l✗ ERROR CHECKING REQUIREMENTS");
                isPurchasable = false;
            }
        }

        List<String> lore = new ArrayList<>();
        lore.add(statusMessage); // This will now have proper colors
        lore.add("");

        if (currentLevel != null && currentLevel >= level) {
            lore.add(ColorUtils.color("&7You already own this enchant level"));
        } else {
            lore.add(ColorUtils.color("&7Apply " + enchant.getDisplayName() + " Level " + level));
            lore.add(ColorUtils.color("&7to your equipment"));
        }
        lore.add("");

        // Add level-specific effect description
        String effectDescription = getEnchantLevelEffectDescription(enchant, level);
        lore.add(ColorUtils.color("&6Level " + level + " Effect:"));
        lore.add(ColorUtils.color("&f" + effectDescription));
        lore.add("");

        lore.add(ColorUtils.color("&a&lApplicable To:"));
        for (Material material : enchant.getApplicableItems()) {
            String itemName = formatMaterialName(material);
            lore.add(ColorUtils.color("&7• " + itemName));
        }
        lore.add("");

        // Show requirements if not met
        if (!meetsRequirements && !(currentLevel != null && currentLevel >= level)) {
            if (level > 1 && (currentLevel == null || currentLevel < level - 1)) {
                lore.add(ColorUtils.color("&c&lRequirements:"));
                lore.add(ColorUtils.color("&7Must own Level " + (level - 1) + " first"));
            } else {
                // Get requirement details
                plugin.getEnchantManager().getRequirementProgress(player, enchant.getName(), level)
                        .thenAccept(progress -> {
                            // This is async, but we'll add a placeholder for now
                        });

                lore.add(ColorUtils.color("&c&lRequirements:"));
                lore.add(ColorUtils.color("&7Check Oracle for details"));
            }
            lore.add("");
        }

        // Add cost and purchase info
        lore.add(ColorUtils.color("&7Cost: &6" + cost + " souls"));
        lore.add("");

        if (isPurchasable) {
            lore.add(ColorUtils.color("&6&lDrag and drop onto item to apply!"));
            lore.add(ColorUtils.color("&eClick to purchase!"));
        } else {
            lore.add(ColorUtils.color("&c&lCannot purchase yet"));
            if (currentLevel == null || currentLevel < level) {
                lore.add(ColorUtils.color("&7Complete requirements to unlock"));
            }
        }

        // Create item name with status
        String itemName = enchant.getTier().getColor() + "&l" + enchant.getDisplayName();
        if (level > 1) {
            itemName += " Level " + level;
        }
        itemName += " Enchant";

        // Choose material based on status
        Material bookMaterial;
        if (currentLevel != null && currentLevel >= level) {
            bookMaterial = Material.LIME_DYE; // Owned - green
        } else if (isPurchasable) {
            bookMaterial = enchant.getTier().getGuiItem(); // Available - tier color
        } else {
            bookMaterial = Material.GRAY_DYE; // Locked - gray
        }

        ItemStack book = createItemStack(bookMaterial, ColorUtils.color(itemName), lore);

        // Add glow effect for available items
        if (isPurchasable) {
            ItemMeta meta = book.getItemMeta();
            if (meta != null) {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.LUCK, 1, true);
                book.setItemMeta(meta);
            }
        }

        return book;
    }

    /**
     * Convert requirement type to database column name
     */
    private String getStatisticName(com.mystenchants.enchants.RequirementType type) {
        switch (type) {
            case BLOCKS_MINED:
                return "blocks_mined";
            case BLOCKS_WALKED:
                return "blocks_walked";
            case WHEAT_BROKEN:
                return "wheat_broken";
            case CREEPERS_KILLED:
                return "creepers_killed";
            case IRON_INGOTS:
                return "iron_ingots_traded";
            case PANTS_CRAFTED:
                return "pants_crafted";
            case SOULS:
                return "souls_collected";
            default:
                return "blocks_mined"; // fallback
        }
    }

    /**
     * Gets roman numeral representation of a number
     */
    private String getRomanNumeral(int number) {
        String[] romanNumerals = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        if (number <= 0 || number >= romanNumerals.length) {
            return String.valueOf(number);
        }
        return romanNumerals[number];
    }

    /**
     * Creates a basic ItemStack with meta
     */
    private ItemStack createItemStack(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            if (name != null && !name.isEmpty()) {
                meta.setDisplayName(name);
            }
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Fills empty slots with glass panes
     */
    private void fillEmptySlots(Inventory inventory) {
        boolean fillEmpty = plugin.getConfigManager().getBoolean("config.yml", "gui.fill-empty-slots", true);
        if (!fillEmpty) return;

        String materialName = plugin.getConfigManager().getString("config.yml", "gui.fill-item.material", "BLACK_STAINED_GLASS_PANE");
        String itemName = plugin.getConfigManager().getString("config.yml", "gui.fill-item.name", " ");

        Material fillMaterial;
        try {
            fillMaterial = Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            fillMaterial = Material.BLACK_STAINED_GLASS_PANE;
        }

        ItemStack fillItem = createItemStack(fillMaterial, ColorUtils.color(itemName), Collections.emptyList());

        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, fillItem);
            }
        }
    }

    /**
     * Formats material names to be more readable
     */
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

    /**
     * ENHANCED DEBUG: Method to test and log soul shop availability with detailed logging
     */
    public void debugSoulShopAvailability(Player player) {
        plugin.getLogger().info("=== ENHANCED SOUL SHOP DEBUG for " + player.getName() + " ===");

        Map<String, Integer> playerEnchants = plugin.getPlayerDataManager().getPlayerEnchants(player.getUniqueId()).join();
        plugin.getLogger().info("Player enchants: " + playerEnchants);

        for (CustomEnchant enchant : plugin.getEnchantManager().getAllEnchants()) {
            if (enchant.getTier() == EnchantTier.MYSTICAL) continue;

            plugin.getLogger().info("--- Checking " + enchant.getName() + " ---");

            for (int level = 1; level <= enchant.getMaxLevel(); level++) {
                boolean canPurchase = canPurchaseEnchantLevel(player, enchant, level, playerEnchants);

                plugin.getLogger().info("Level " + level + " purchasable: " + canPurchase);

                if (canPurchase) {
                    plugin.getLogger().info("✓ Can purchase " + enchant.getName() + " level " + level);
                } else {
                    plugin.getLogger().info("✗ Cannot purchase " + enchant.getName() + " level " + level);

                    Integer currentLevel = playerEnchants.get(enchant.getName());
                    plugin.getLogger().info("  Current level: " + currentLevel);
                    plugin.getLogger().info("  Checking level: " + level);

                    if (level == 1) {
                        if (currentLevel != null && currentLevel >= 1) {
                            plugin.getLogger().info("  Reason: Already has level 1 or higher");
                        }
                    } else {
                        if (currentLevel == null || currentLevel < 1) {
                            plugin.getLogger().info("  Reason: Must have level 1 first");
                        } else if (currentLevel >= level) {
                            plugin.getLogger().info("  Reason: Already has this level or higher");
                        } else if (level != currentLevel + 1) {
                            plugin.getLogger().info("  Reason: Can only upgrade one level at a time (has " + currentLevel + ", checking " + level + ")");
                        } else {
                            plugin.getLogger().info("  Reason: Should be purchasable - check logic!");
                        }
                    }
                }
            }
        }

        plugin.getLogger().info("=== END ENHANCED SOUL SHOP DEBUG ===");
    }

    /**
     * Reloads GUI templates
     */
    public void reload() {
        templates.clear();
        loadGuiTemplates();
    }
}