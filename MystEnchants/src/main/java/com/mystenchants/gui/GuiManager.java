package com.mystenchants.gui;

import com.mystenchants.MystEnchants;
import com.mystenchants.enchants.CustomEnchant;
import com.mystenchants.enchants.EnchantTier;
import com.mystenchants.enchants.RequirementType;
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
 * COMPLETELY FIXED GuiManager - ALL ISSUES RESOLVED
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
     * Creates the main enchants GUI
     */
    public Inventory createEnchantsGui(Player player) {
        GuiTemplate template = templates.get("enchants");
        if (template == null) {
            return Bukkit.createInventory(null, 54, ColorUtils.color("&6&lEnchants"));
        }

        Inventory inventory = Bukkit.createInventory(null, template.getSize(), template.getTitle());

        // Add tier buttons (Skip mystical for main GUI)
        for (EnchantTier tier : EnchantTier.values()) {
            if (tier == EnchantTier.MYSTICAL) continue;

            GuiItem tierItem = template.getItems().get(tier.name().toLowerCase());
            if (tierItem != null) {
                ItemStack item = createTierItem(tier, player);
                inventory.setItem(tierItem.getSlot(), item);
            }
        }

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

        int slot = 10;
        for (CustomEnchant enchant : enchants) {
            if (slot > 43) break;

            ItemStack item = createEnchantDisplayItem(enchant, player);
            inventory.setItem(slot, item);

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
     * Creates the oracle GUI with all player's unlocked enchants
     */
    public Inventory createOracleGui(Player player) {
        GuiTemplate template = templates.get("oracle");
        if (template == null) {
            return Bukkit.createInventory(null, 54, ColorUtils.color("&6&lOracle"));
        }

        Inventory inventory = Bukkit.createInventory(null, template.getSize(), template.getTitle());

        PlayerDataManager playerData = plugin.getPlayerDataManager();
        Map<String, Integer> playerEnchants = playerData.getPlayerEnchants(player.getUniqueId()).join();

        int slot = 10;
        for (EnchantTier tier : EnchantTier.values()) {
            List<CustomEnchant> tierEnchants = plugin.getEnchantManager().getEnchantsByTier(tier);

            for (CustomEnchant enchant : tierEnchants) {
                if (playerEnchants.containsKey(enchant.getName())) {
                    if (slot > 43) break;

                    ItemStack item = createOracleEnchantItem(enchant, player);
                    inventory.setItem(slot, item);

                    slot++;
                    if (slot % 9 == 8) slot += 2;
                }
            }
        }

        // Add purchase section button
        GuiItem purchaseItem = template.getItems().get("purchase-section");
        if (purchaseItem != null) {
            ItemStack item = createItemStack(purchaseItem.getMaterial(), purchaseItem.getName(), purchaseItem.getLore());
            inventory.setItem(purchaseItem.getSlot(), item);
        }

        // Add info item
        GuiItem infoItem = template.getItems().get("info-item");
        if (infoItem != null) {
            ItemStack item = createItemStack(infoItem.getMaterial(), infoItem.getName(), infoItem.getLore());
            inventory.setItem(infoItem.getSlot(), item);
        }

        fillEmptySlots(inventory);
        return inventory;
    }

    /**
     * FIXED: Creates enchant details GUI with SINGLE progress display
     */
    public Inventory createOracleDetailsGui(Player player, CustomEnchant enchant) {
        String title = ColorUtils.color("&6&l" + enchant.getDisplayName() + " Details");
        Inventory inventory = Bukkit.createInventory(null, 27, title);

        PlayerDataManager playerData = plugin.getPlayerDataManager();
        int currentLevel = playerData.getEnchantLevel(player.getUniqueId(), enchant.getName()).join();

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

        Material glassMaterial = getGlassMaterial(currentLevel, 1, 2);
        ItemStack glassPane = createGlassPane(glassMaterial);
        inventory.setItem(12, glassPane);
        inventory.setItem(13, glassPane);

        ItemStack level2Item = createLevelItem(enchant, 2, currentLevel, player);
        inventory.setItem(14, level2Item);
    }

    private void createThreeLevelLayout(Inventory inventory, CustomEnchant enchant, int currentLevel, Player player) {
        ItemStack level1Item = createLevelItem(enchant, 1, currentLevel, player);
        inventory.setItem(10, level1Item);

        Material glass1Material = getGlassMaterial(currentLevel, 1, 2);
        ItemStack glassPane1 = createGlassPane(glass1Material);
        inventory.setItem(11, glassPane1);
        inventory.setItem(12, glassPane1);

        ItemStack level2Item = createLevelItem(enchant, 2, currentLevel, player);
        inventory.setItem(13, level2Item);

        Material glass2Material = getGlassMaterial(currentLevel, 2, 3);
        ItemStack glassPane2 = createGlassPane(glass2Material);
        inventory.setItem(14, glassPane2);
        inventory.setItem(15, glassPane2);

        ItemStack level3Item = createLevelItem(enchant, 3, currentLevel, player);
        inventory.setItem(16, level3Item);
    }

    private Material getGlassMaterial(int currentLevel, int afterLevel, int maxLevel) {
        if (currentLevel < afterLevel) {
            return Material.RED_STAINED_GLASS_PANE;
        } else if (currentLevel == afterLevel && afterLevel < maxLevel) {
            return Material.YELLOW_STAINED_GLASS_PANE;
        } else {
            return Material.LIME_STAINED_GLASS_PANE;
        }
    }

    private void createRedemptionLayout(Inventory inventory, CustomEnchant enchant, int currentLevel, Player player) {
        List<String> lore = new ArrayList<>();

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

        Material itemMaterial = currentLevel >= 1 ? Material.PINK_DYE : Material.GRAY_DYE;
        ItemStack redemptionItem = createItemStack(itemMaterial,
                ColorUtils.color("&d&l" + enchant.getDisplayName()),
                lore);

        inventory.setItem(13, redemptionItem);
    }

    /**
     * FIXED: Creates level item with SINGLE progress display only
     */
    private ItemStack createLevelItem(CustomEnchant enchant, int level, int currentLevel, Player player) {
        List<String> lore = new ArrayList<>();

        if (currentLevel >= level) {
            lore.add(ColorUtils.color("&a&l✓ LEVEL " + level + " UNLOCKED"));
        } else {
            lore.add(ColorUtils.color("&c&l✗ LEVEL " + level + " LOCKED"));
        }

        lore.add("");
        lore.add(ColorUtils.color("&6Level " + level + " Effect:"));
        String effectDescription = getEnchantLevelEffectDescription(enchant, level);
        lore.add(ColorUtils.color("&f" + effectDescription));
        lore.add("");

        if (currentLevel < level) {
            UnlockRequirement req = enchant.getUnlockRequirement(level);
            if (req != null && req.getType() != RequirementType.NONE) {
                lore.add(ColorUtils.color("&cRequirements:"));

                if (req.getType().requiresStatistics()) {
                    String statName = getStatisticName(req.getType());
                    try {
                        Long current = plugin.getPlayerDataManager().getStatistic(player.getUniqueId(), statName).join();
                        if (current == null) current = 0L;

                        lore.add(ColorUtils.color("&7" + req.getFormattedMessage(current)));

                        // FIXED: SINGLE progress display with percentage and current/max
                        double progress = req.getProgress(current);
                        String progressLine = String.format("&7Progress: &f%.1f%% &7(%s)",
                                progress, formatProgress(current, req.getAmount()));
                        lore.add(ColorUtils.color(progressLine));
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

        Material itemMaterial = enchant.getTier().getGuiItem();
        if (currentLevel < level) {
            itemMaterial = Material.GRAY_DYE;
        }

        return createItemStack(itemMaterial,
                ColorUtils.color(enchant.getTier().getColor() + "&l" + enchant.getDisplayName() + " Level " + level),
                lore);
    }

    /**
     * Creates the oracle purchase GUI
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

                int nextLevel = currentLevel + 1;
                UnlockRequirement req = enchant.getUnlockRequirement(nextLevel);

                boolean isExpUpgrade = (req == null ||
                        req.getType() == RequirementType.NONE ||
                        req.getType() == RequirementType.EXP_LEVELS);

                if (isExpUpgrade) {
                    ItemStack item = createPurchaseItem(enchant, player, nextLevel);
                    inventory.setItem(slot, item);
                    upgradesFound++;

                    slot++;
                    if (slot % 9 == 8) slot += 2;
                }
            }
        }

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
     * FIXED: Soul Shop Page 1 with 45 slots (layer 5)
     * Shows: Tempo, Scholar, Serrate, Rejuvenate, Backup, Guillotine
     * Starting at row 1 (slots 10-16)
     */
    public Inventory createSoulShopGui(Player player) {
        String title = ColorUtils.color("&6&lSoul Shop (Page 1)");
        Inventory inventory = Bukkit.createInventory(null, 45, title); // FIXED: Changed from 54 to 45

        Map<String, Integer> playerEnchants = plugin.getPlayerDataManager().getPlayerEnchants(player.getUniqueId()).join();

        // PAGE 1 ENCHANTS: Tempo, Scholar, Serrate, Rejuvenate, Backup, Guillotine
        String[] page1Enchants = {"tempo", "scholar", "serrate", "rejuvenate", "backup", "guillotine"};
        int[] baseSlots = {10, 11, 12, 14, 15, 16}; // Starting at row 1, skip slot 13 for spacing

        for (int i = 0; i < page1Enchants.length; i++) {
            String enchantName = page1Enchants[i];
            int baseSlot = baseSlots[i];

            CustomEnchant enchant = plugin.getEnchantManager().getEnchant(enchantName);
            if (enchant != null) {
                // Place enchant levels vertically below each enchant
                for (int level = 1; level <= enchant.getMaxLevel(); level++) {
                    int slotOffset = (level - 1) * 9; // Each level goes 9 slots down (next row)
                    int finalSlot = baseSlot + slotOffset;

                    // Make sure we don't go past the inventory bounds (45 slots now)
                    if (finalSlot < 45) {
                        ItemStack book = createSoulShopBook(enchant, level, player, playerEnchants);
                        inventory.setItem(finalSlot, book);
                    }
                }
            }
        }

        // FIXED: Add navigation with 45-slot layout
        addSoulShopNavigation45(inventory, 1);
        fillEmptySlots(inventory);
        return inventory;
    }

    /**
     * FIXED: Soul Shop Page 2 with 45 slots (layer 5)
     * Shows: Pace, Pantsed, Detonate (legendary), Almighty Push (legendary), Redemption, Zetsubo
     * Starting at row 1 (slots 10-16)
     */
    public Inventory createSoulShopPage2Gui(Player player) {
        String title = ColorUtils.color("&6&lSoul Shop (Page 2)");
        Inventory inventory = Bukkit.createInventory(null, 45, title); // FIXED: Changed from 54 to 45

        Map<String, Integer> playerEnchants = plugin.getPlayerDataManager().getPlayerEnchants(player.getUniqueId()).join();

        // PAGE 2 ENCHANTS: Pace, Pantsed, Detonate, Almighty Push, Redemption, Zetsubo
        String[] page2Enchants = {"pace", "pantsed", "detonate", "almighty_push", "redemption", "zetsubo"};
        int[] baseSlots = {10, 11, 12, 13, 15, 16}; // Starting at row 1, almighty_push at 13 (beside detonate)

        for (int i = 0; i < page2Enchants.length; i++) {
            String enchantName = page2Enchants[i];
            int baseSlot = baseSlots[i];

            CustomEnchant enchant = plugin.getEnchantManager().getEnchant(enchantName);
            if (enchant != null) {
                // Place enchant levels vertically below each enchant
                for (int level = 1; level <= enchant.getMaxLevel(); level++) {
                    int slotOffset = (level - 1) * 9; // Each level goes 9 slots down (next row)
                    int finalSlot = baseSlot + slotOffset;

                    // Make sure we don't go past the inventory bounds (45 slots now)
                    if (finalSlot < 45) {
                        ItemStack book = createSoulShopBook(enchant, level, player, playerEnchants);
                        inventory.setItem(finalSlot, book);
                    }
                }
            }
        }

        // FIXED: Add navigation with 45-slot layout
        addSoulShopNavigation45(inventory, 2);
        fillEmptySlots(inventory);
        return inventory;
    }

    /**
     * NEW: Adds soul shop navigation for 45-slot layout
     */
    private void addSoulShopNavigation45(Inventory inventory, int page) {
        if (page == 1) {
            // Next page button - bottom right corner of 45-slot inventory
            ItemStack nextPage = createItemStack(Material.ARROW,
                    ColorUtils.color("&a&lNext Page →"),
                    Arrays.asList(
                            ColorUtils.color("&7View Legendary & Mystical enchants"),
                            ColorUtils.color("&eClick to navigate!")
                    ));
            inventory.setItem(44, nextPage); // FIXED: Changed from 53 to 44 (last slot in 45-slot GUI)

            // Page info - top center
            ItemStack pageInfo = createItemStack(Material.GHAST_TEAR,
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
            inventory.setItem(4, pageInfo);

        } else {
            // Previous page button - bottom left corner of 45-slot inventory
            ItemStack previousPage = createItemStack(Material.ARROW,
                    ColorUtils.color("&c&l← Previous Page"),
                    Arrays.asList(
                            ColorUtils.color("&7Go back to page 1"),
                            ColorUtils.color("&eClick to navigate!")
                    ));
            inventory.setItem(36, previousPage); // FIXED: Changed from 45 to 36 (first slot of last row in 45-slot GUI)

            // Page info - top center
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
        }
    }


    /**
     * FIXED: Creates the perks GUI - Now reads slots from perks.yml perk-shop-layout
     */
    public Inventory createPerksGui(Player player) {
        // FIXED: Use 4 rows (36 slots) as per your requirement
        String title = ColorUtils.color("&6&lPerks");
        Inventory inventory = Bukkit.createInventory(null, 36, title);

        // Add info item at top center
        ItemStack infoItem = createItemStack(Material.NETHER_STAR,
                ColorUtils.color("&6&lPerks Information"),
                Arrays.asList(
                        ColorUtils.color("&7Purchase powerful single-use items"),
                        ColorUtils.color("&7that provide unique abilities!"),
                        ColorUtils.color(""),
                        ColorUtils.color("&7Each perk has a cooldown after use"),
                        ColorUtils.color("&7to prevent spam and maintain balance."),
                        ColorUtils.color(""),
                        ColorUtils.color("&7Some perks require multiple hits"),
                        ColorUtils.color("&7or specific conditions to activate.")
                ));
        inventory.setItem(4, infoItem);

        // FIXED: Read perk slots from perks.yml perk-shop-layout section
        String[] perkNames = {
                "teleport-snowball",
                "grappling-hook",
                "snowman-egg",
                "spellbreaker",
                "tradeoff-egg",
                "worthy-sacrifice",
                "lovestruck"
        };

        for (String perkName : perkNames) {
            // Read slot from perks.yml perk-shop-layout
            int slot = plugin.getConfigManager().getPerksConfig().getInt("perk-shop-layout." + perkName, -1);

            plugin.getLogger().info("Loading perk " + perkName + " at slot " + slot);

            if (slot >= 0 && slot < 36) { // Valid slot for 4-row GUI
                // Use PerkManager to create the perk item
                ItemStack item = plugin.getPerkManager().createPerkShopItem(perkName);

                if (item != null) {
                    inventory.setItem(slot, item);
                    plugin.getLogger().info("Placed " + perkName + " at slot " + slot);
                } else {
                    // Fallback: create basic item if PerkManager fails
                    ItemStack fallbackItem = createBasicPerkItem(perkName);
                    if (fallbackItem != null) {
                        inventory.setItem(slot, fallbackItem);
                        plugin.getLogger().info("Placed fallback " + perkName + " at slot " + slot);
                    }
                }
            } else {
                plugin.getLogger().warning("Invalid slot " + slot + " for perk " + perkName + " (GUI has 36 slots, 0-35)");
            }
        }

        fillEmptySlots(inventory);
        return inventory;
    }


    /**
     * HELPER: Creates basic perk item as fallback
     */
    private ItemStack createBasicPerkItem(String perkName) {
        switch (perkName) {
            case "teleport-snowball":
                return createItemStack(Material.SNOWBALL,
                        ColorUtils.color("&b&lTeleport Snowball"),
                        Arrays.asList(
                                ColorUtils.color("&7Teleport to the player you hit"),
                                ColorUtils.color("&7with this snowball"),
                                ColorUtils.color(""),
                                ColorUtils.color("&7Cost: &6500 souls"),
                                ColorUtils.color(""),
                                ColorUtils.color("&eClick to purchase!")
                        ));
            case "grappling-hook":
                return createItemStack(Material.FISHING_ROD,
                        ColorUtils.color("&a&lGrappling Hook"),
                        Arrays.asList(
                                ColorUtils.color("&7Hook onto players and pull"),
                                ColorUtils.color("&7them towards you"),
                                ColorUtils.color(""),
                                ColorUtils.color("&7Cost: &6750 souls"),
                                ColorUtils.color(""),
                                ColorUtils.color("&eClick to purchase!")
                        ));
            case "snowman-egg":
                return createItemStack(Material.PUMPKIN,
                        ColorUtils.color("&f&lSnowman Egg"),
                        Arrays.asList(
                                ColorUtils.color("&7Spawns a snowman that attacks"),
                                ColorUtils.color("&7enemies for 30 seconds"),
                                ColorUtils.color(""),
                                ColorUtils.color("&7Cost: &6600 souls"),
                                ColorUtils.color(""),
                                ColorUtils.color("&eClick to purchase!")
                        ));
            case "spellbreaker":
                return createItemStack(Material.BLAZE_ROD,
                        ColorUtils.color("&6&lSpellbreaker"),
                        Arrays.asList(
                                ColorUtils.color("&7Hit a player 5 times to remove"),
                                ColorUtils.color("&7their potion effects"),
                                ColorUtils.color(""),
                                ColorUtils.color("&7Cost: &6800 souls"),
                                ColorUtils.color(""),
                                ColorUtils.color("&eClick to purchase!")
                        ));
            case "tradeoff-egg":
                return createItemStack(Material.EGG,
                        ColorUtils.color("&e&lTradeoff Egg"),
                        Arrays.asList(
                                ColorUtils.color("&7Switch potion effects with"),
                                ColorUtils.color("&7target for 10 seconds"),
                                ColorUtils.color(""),
                                ColorUtils.color("&7Cost: &6900 souls"),
                                ColorUtils.color(""),
                                ColorUtils.color("&eClick to purchase!")
                        ));
            case "worthy-sacrifice":
                return createItemStack(Material.WITCH_SPAWN_EGG,
                        ColorUtils.color("&5&lWorthy Sacrifice"),
                        Arrays.asList(
                                ColorUtils.color("&7Spawns a witch that absorbs"),
                                ColorUtils.color("&7damage for you"),
                                ColorUtils.color(""),
                                ColorUtils.color("&7Cost: &61200 souls"),
                                ColorUtils.color(""),
                                ColorUtils.color("&eClick to purchase!")
                        ));
            case "lovestruck":
                return createItemStack(Material.ROSE_BUSH,
                        ColorUtils.color("&d&lLovestruck"),
                        Arrays.asList(
                                ColorUtils.color("&7Hit a player 5 times to give"),
                                ColorUtils.color("&7nausea and rose inventory"),
                                ColorUtils.color(""),
                                ColorUtils.color("&7Cost: &6700 souls"),
                                ColorUtils.color(""),
                                ColorUtils.color("&eClick to purchase!")
                        ));
            default:
                return null;
        }
    }

    /**
     * Creates redemption confirmation GUI
     */
    public Inventory createRedemptionGui(Player player) {
        String title = ColorUtils.color("&4&lRedemption Boss Fight");
        Inventory inventory = Bukkit.createInventory(null, 27, title);

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

    // Helper methods

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

        lore.add(ColorUtils.color("&a&lApplicable To:"));
        for (Material material : enchant.getApplicableItems()) {
            String itemName = formatMaterialName(material);
            lore.add(ColorUtils.color("&7• " + itemName));
        }
        lore.add("");

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
     * SIMPLE FIX: Creates soul shop book - Just removed "dye" references, no material changes
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
        if (currentLevel != null && currentLevel >= level) {
            statusMessage = ColorUtils.color("&a&l✓ OWNED");
            isPurchasable = false;
        } else if (level > 1 && (currentLevel == null || currentLevel < level - 1)) {
            statusMessage = ColorUtils.color("&c&l✗ REQUIRES LEVEL " + (level - 1));
            isPurchasable = false;
        } else {
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
        lore.add(statusMessage);
        lore.add("");

        // REMOVED: "enchant dye" references - just describe the enchant
        if (currentLevel != null && currentLevel >= level) {
            lore.add(ColorUtils.color("&7You already own this enchant level"));
        } else {
            lore.add(ColorUtils.color("&7" + enchant.getDisplayName() + " Level " + level));
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
                lore.add(ColorUtils.color("&c&lRequirements:"));
                lore.add(ColorUtils.color("&7Check Oracle for details"));
            }
            lore.add("");
        }

        // Add cost and purchase info
        lore.add(ColorUtils.color("&7Cost: &6" + cost + " souls"));
        lore.add("");

        if (isPurchasable) {
            // UPDATED: Clean purchase instructions without "dye"
            lore.add(ColorUtils.color("&eClick to purchase!"));
            lore.add(ColorUtils.color("&7Drag onto item to apply"));
        } else {
            lore.add(ColorUtils.color("&c&lCannot purchase yet"));
            if (currentLevel == null || currentLevel < level) {
                lore.add(ColorUtils.color("&7Complete requirements to unlock"));
            }
        }

        // UNCHANGED: Keep original material logic (no green dye changes)
        String itemName = enchant.getTier().getColor() + "&l" + enchant.getDisplayName();
        if (level > 1) {
            itemName += " Level " + level;
        }

        // UNCHANGED: Use tier material, don't change to green when purchased
        Material bookMaterial = enchant.getTier().getGuiItem();

        ItemStack book = createItemStack(bookMaterial, ColorUtils.color(itemName), lore);

        // Add glow effect for available items only
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
     * Gets effect description for specific enchant level
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

    private String formatProgress(long current, long max) {
        return formatLargeNumber(current) + "/" + formatLargeNumber(max);
    }

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

    private ItemStack createGlassPane(Material material) {
        return createItemStack(material, " ", Arrays.asList());
    }

    private String getStatisticName(RequirementType type) {
        switch (type) {
            case BLOCKS_MINED: return "blocks_mined";
            case BLOCKS_WALKED: return "blocks_walked";
            case WHEAT_BROKEN: return "wheat_broken";
            case CREEPERS_KILLED: return "creepers_killed";
            case IRON_INGOTS: return "iron_ingots_traded";
            case PANTS_CRAFTED: return "pants_crafted";
            case SOULS: return "souls_collected";
            default: return "blocks_mined";
        }
    }

    private String getRomanNumeral(int number) {
        String[] romanNumerals = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        if (number <= 0 || number >= romanNumerals.length) {
            return String.valueOf(number);
        }
        return romanNumerals[number];
    }

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

    public void reload() {
        templates.clear();
        loadGuiTemplates();
    }

    /**
     * DEBUG: Method to test and log soul shop availability with detailed logging
     */
    public void debugSoulShopAvailability(Player player) {
        plugin.getLogger().info("=== SOUL SHOP DEBUG for " + player.getName() + " ===");

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

        plugin.getLogger().info("=== END SOUL SHOP DEBUG ===");
    }

    /**
     * Helper method to check if player can purchase an enchant level
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

}