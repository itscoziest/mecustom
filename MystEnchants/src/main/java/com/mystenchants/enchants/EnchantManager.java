package com.mystenchants.enchants;

import com.mystenchants.MystEnchants;
import com.mystenchants.utils.ColorUtils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Enhanced EnchantManager with comprehensive requirement support
 * Manages all custom enchants and their properties with full configurability
 */
public class EnchantManager {

    private final MystEnchants plugin;
    private final Map<String, CustomEnchant> enchants = new HashMap<>();
    private final Map<EnchantTier, List<CustomEnchant>> enchantsByTier = new EnumMap<>(EnchantTier.class);

    // Namespaced keys for persistent data
    private final NamespacedKey enchantKey;
    private final NamespacedKey levelKey;

    // Current enchant being processed (for requirement loading)
    private String currentEnchantName;
    private int currentLevel;

    public EnchantManager(MystEnchants plugin) {
        this.plugin = plugin;
        this.enchantKey = new NamespacedKey(plugin, "custom_enchant");
        this.levelKey = new NamespacedKey(plugin, "enchant_level");

        loadEnchants();
    }

    /**
     * Loads all enchants from configuration
     */
    private void loadEnchants() {
        enchants.clear();
        enchantsByTier.clear();

        ConfigurationSection enchantsSection = plugin.getConfigManager().getEnchantsConfig().getConfigurationSection("enchants");
        if (enchantsSection == null) {
            plugin.getLogger().warning("No enchants found in enchants.yml!");
            return;
        }

        for (String enchantName : enchantsSection.getKeys(false)) {
            try {
                CustomEnchant enchant = loadEnchant(enchantName, enchantsSection.getConfigurationSection(enchantName));
                if (enchant != null) {
                    enchants.put(enchantName, enchant);
                    enchantsByTier.computeIfAbsent(enchant.getTier(), k -> new ArrayList<>()).add(enchant);
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to load enchant: " + enchantName + " - " + e.getMessage());
            }
        }

        plugin.getLogger().info("Loaded " + enchants.size() + " custom enchants.");
    }

    /**
     * Enhanced enchant loading with full configuration support
     */
    private CustomEnchant loadEnchant(String name, ConfigurationSection section) {
        if (section == null) return null;

        try {
            this.currentEnchantName = name; // Set for requirement loading

            EnchantTier tier = EnchantTier.valueOf(section.getString("tier", "COMMON"));
            int maxLevel = section.getInt("max-level", 3);
            String displayName = ColorUtils.color(section.getString("display-name", name));
            List<String> description = ColorUtils.color(section.getStringList("description"));

            // Load applicable items
            List<Material> applicableItems = section.getStringList("applicable-items").stream()
                    .map(itemName -> {
                        try {
                            return Material.valueOf(itemName.toUpperCase());
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Invalid material in " + name + ": " + itemName);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            // Enhanced unlock requirements loading
            Map<Integer, UnlockRequirement> requirements = new HashMap<>();
            ConfigurationSection reqSection = section.getConfigurationSection("unlock-requirements");
            if (reqSection != null) {
                for (String levelStr : reqSection.getKeys(false)) {
                    try {
                        int level = Integer.parseInt(levelStr.replace("level-", ""));
                        this.currentLevel = level; // Set for requirement loading
                        ConfigurationSection levelReq = reqSection.getConfigurationSection(levelStr);
                        if (levelReq != null) {
                            UnlockRequirement requirement = loadUnlockRequirement(levelReq);
                            requirements.put(level, requirement);
                        }
                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning("Invalid level in " + name + ": " + levelStr);
                    }
                }
            }

            // Additional properties
            boolean requiresFullSet = section.getBoolean("requires-full-set", false);
            int cooldown = section.getInt("cooldown", 0);
            String deathMessage = section.getString("death-message", "");

            // Load enchant effects
            Map<String, Object> effects = loadEnchantEffects(section);

            CustomEnchant enchant = new CustomEnchant(name, tier, maxLevel, displayName, description,
                    applicableItems, requirements, requiresFullSet, cooldown, deathMessage);

            // Set effects (would need to modify CustomEnchant class to store effects)
            // enchant.setEffects(effects);

            return enchant;

        } catch (Exception e) {
            plugin.getLogger().severe("Error loading enchant " + name + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Enhanced unlock requirement loading with all requirement types
     */
    private UnlockRequirement loadUnlockRequirement(ConfigurationSection section) {
        RequirementType type = RequirementType.valueOf(section.getString("type", "NONE"));
        long amount = section.getLong("amount", 0);
        String message = ColorUtils.color(section.getString("message", ""));

        // FIXED: Removed the broken ConfigManager method calls
        // Just use the amount from the config section directly
        if (section.contains("amount")) {
            amount = section.getLong("amount");
        }

        return new UnlockRequirement(type, amount, message);
    }

    /**
     * Load enchant effects with arrays for multi-level enchants
     */
    private Map<String, Object> loadEnchantEffects(ConfigurationSection section) {
        ConfigurationSection effectsSection = section.getConfigurationSection("effects");
        if (effectsSection == null) return new HashMap<>();

        Map<String, Object> effects = new HashMap<>();

        // Load effect arrays for multi-level enchants
        if (effectsSection.contains("haste-levels")) {
            effects.put("haste-levels", effectsSection.getIntegerList("haste-levels"));
        }
        if (effectsSection.contains("speed-levels")) {
            effects.put("speed-levels", effectsSection.getIntegerList("speed-levels"));
        }
        if (effectsSection.contains("strength-levels")) {
            effects.put("strength-levels", effectsSection.getIntegerList("strength-levels"));
        }
        if (effectsSection.contains("exp-multipliers")) {
            effects.put("exp-multipliers", effectsSection.getDoubleList("exp-multipliers"));
        }
        if (effectsSection.contains("bleed-durations")) {
            effects.put("bleed-durations", effectsSection.getIntegerList("bleed-durations"));
        }
        if (effectsSection.contains("heal-amounts")) {
            effects.put("heal-amounts", effectsSection.getDoubleList("heal-amounts"));
        }
        if (effectsSection.contains("heal-chances")) {
            effects.put("heal-chances", effectsSection.getDoubleList("heal-chances"));
        }
        if (effectsSection.contains("steal-chances")) {
            effects.put("steal-chances", effectsSection.getDoubleList("steal-chances"));
        }
        if (effectsSection.contains("head-drop-chances")) {
            effects.put("head-drop-chances", effectsSection.getDoubleList("head-drop-chances"));
        }
        if (effectsSection.contains("area-sizes")) {
            effects.put("area-sizes", effectsSection.getIntegerList("area-sizes"));
        }
        if (effectsSection.contains("golem-counts")) {
            effects.put("golem-counts", effectsSection.getIntegerList("golem-counts"));
        }

        // Load other effect properties
        for (String key : effectsSection.getKeys(false)) {
            if (!effects.containsKey(key)) {
                effects.put(key, effectsSection.get(key));
            }
        }

        return effects;
    }

    /**
     * Gets an enchant by name
     */
    public CustomEnchant getEnchant(String name) {
        return enchants.get(name.toLowerCase());
    }

    /**
     * Gets all enchants
     */
    public Collection<CustomEnchant> getAllEnchants() {
        return enchants.values();
    }

    /**
     * Gets enchants by tier
     */
    public List<CustomEnchant> getEnchantsByTier(EnchantTier tier) {
        return enchantsByTier.getOrDefault(tier, new ArrayList<>());
    }

    /**
     * Checks if an item has a custom enchant
     */
    public boolean hasCustomEnchant(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        return container.has(enchantKey, PersistentDataType.STRING);
    }

    /**
     * Gets the custom enchant on an item
     */
    public CustomEnchant getCustomEnchant(ItemStack item) {
        if (!hasCustomEnchant(item)) return null;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        String enchantName = container.get(enchantKey, PersistentDataType.STRING);
        return getEnchant(enchantName);
    }

    /**
     * Gets the level of a custom enchant on an item
     */
    public int getCustomEnchantLevel(ItemStack item) {
        if (!hasCustomEnchant(item)) return 0;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        return container.getOrDefault(levelKey, PersistentDataType.INTEGER, 0);
    }

    /**
     * Applies a custom enchant to an item
     */
    public ItemStack applyEnchant(ItemStack item, CustomEnchant enchant, int level) {
        if (item == null || enchant == null || level <= 0 || level > enchant.getMaxLevel()) {
            return item;
        }

        // Check if item is applicable
        if (!enchant.isApplicableTo(item.getType())) {
            return item;
        }

        ItemStack result = item.clone();
        ItemMeta meta = result.getItemMeta();

        if (meta == null) return item;

        // Set persistent data
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(enchantKey, PersistentDataType.STRING, enchant.getName());
        container.set(levelKey, PersistentDataType.INTEGER, level);

        // Update display name and lore
        updateItemDisplay(meta, enchant, level);

        // Add enchantment glow
        meta.addEnchant(Enchantment.LUCK, 1, true);

        result.setItemMeta(meta);
        return result;
    }

    /**
     * Updates item display with enchant information
     */
    private void updateItemDisplay(ItemMeta meta, CustomEnchant enchant, int level) {
        // Update lore to show enchant
        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();

        // Remove existing custom enchant lore
        lore.removeIf(line -> line.contains("§d") && line.contains("§l"));

        // Add new enchant lore
        String enchantLine = ColorUtils.color("&d&l" + enchant.getDisplayName() + " " +
                getRomanNumeral(level));
        lore.add(0, enchantLine);

        meta.setLore(lore);
    }

    /**
     * Removes a custom enchant from an item
     */
    public ItemStack removeEnchant(ItemStack item) {
        if (!hasCustomEnchant(item)) return item;

        ItemStack result = item.clone();
        ItemMeta meta = result.getItemMeta();

        if (meta == null) return item;

        // Remove persistent data
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.remove(enchantKey);
        container.remove(levelKey);

        // Remove enchant from lore
        if (meta.hasLore()) {
            List<String> lore = meta.getLore();
            lore.removeIf(line -> line.contains("§d") && line.contains("§l"));
            meta.setLore(lore);
        }

        // Remove enchantment glow if no other enchants
        if (meta.getEnchants().size() == 1 && meta.hasEnchant(Enchantment.LUCK)) {
            meta.removeEnchant(Enchantment.LUCK);
        }

        result.setItemMeta(meta);
        return result;
    }

    /**
     * Checks if an enchant can be applied to an item type
     */
    public boolean canApplyEnchant(CustomEnchant enchant, Material material) {
        return enchant != null && enchant.isApplicableTo(material);
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
     * Enhanced enchant book creation with configurable instructions
     */
    public ItemStack createEnchantDye(CustomEnchant enchant, int level) {
        ItemStack dye = new ItemStack(enchant.getTier().getGuiItem());
        ItemMeta meta = dye.getItemMeta();

        if (meta == null) return dye;

        meta.setDisplayName(ColorUtils.color(enchant.getTier().getColor() + "&l" +
                enchant.getDisplayName() + " " + getRomanNumeral(level)));

        List<String> lore = new ArrayList<>();
        lore.addAll(enchant.getDescription());
        lore.add("");

        String instructionHeader = plugin.getConfigManager().getString("config.yml",
                "messages.enchant-apply-instructions", "&6&lHow to Apply: &eDrag and drop &7onto compatible item");
        lore.add(ColorUtils.color(instructionHeader));
        lore.add("");
        lore.add(ColorUtils.color("&a&lCompatible Items:"));

        for (Material material : enchant.getApplicableItems()) {
            String itemName = formatMaterialName(material);
            lore.add(ColorUtils.color("&7• " + itemName));
        }

        meta.setLore(lore);

        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(enchantKey, PersistentDataType.STRING, enchant.getName());
        container.set(levelKey, PersistentDataType.INTEGER, level);

        dye.setItemMeta(meta);
        return dye;
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
     * Gets enchant effect value for a specific level
     */
    public Object getEnchantEffect(String enchantName, String effectName, int level) {
        CustomEnchant enchant = getEnchant(enchantName);
        if (enchant == null) return null;

        // This would require CustomEnchant class to store effects
        // For now, we'll read directly from config
        ConfigurationSection enchantSection = plugin.getConfigManager().getEnchantsConfig()
                .getConfigurationSection("enchants." + enchantName + ".effects");

        if (enchantSection == null) return null;

        if (enchantSection.contains(effectName)) {
            Object value = enchantSection.get(effectName);

            // Handle arrays (for multi-level effects)
            if (value instanceof List) {
                List<?> list = (List<?>) value;
                if (level > 0 && level <= list.size()) {
                    return list.get(level - 1);
                }
            } else {
                return value;
            }
        }

        return null;
    }

    /**
     * Gets haste level for Tempo enchant
     */
    public int getTempoHasteLevel(int enchantLevel) {
        Object levels = getEnchantEffect("tempo", "haste-levels", enchantLevel);
        return levels instanceof Number ? ((Number) levels).intValue() : enchantLevel;
    }

    /**
     * Gets speed level for Pace enchant
     */
    public int getPaceSpeedLevel(int enchantLevel) {
        Object levels = getEnchantEffect("pace", "speed-levels", enchantLevel);
        return levels instanceof Number ? ((Number) levels).intValue() : enchantLevel;
    }

    /**
     * Gets strength level for Zetsubo enchant
     */
    public int getZetsuboStrengthLevel(int enchantLevel) {
        Object levels = getEnchantEffect("zetsubo", "strength-levels", enchantLevel);
        return levels instanceof Number ? ((Number) levels).intValue() : enchantLevel;
    }

    /**
     * Gets EXP multiplier for Scholar enchant
     */
    public double getScholarExpMultiplier(int enchantLevel) {
        Object multipliers = getEnchantEffect("scholar", "exp-multipliers", enchantLevel);
        return multipliers instanceof Number ? ((Number) multipliers).doubleValue() : 1.05;
    }

    /**
     * Gets bleed duration for Serrate enchant (in ticks)
     */
    public int getSerrateBleedDuration(int enchantLevel) {
        Object durations = getEnchantEffect("serrate", "bleed-durations", enchantLevel);
        return durations instanceof Number ? ((Number) durations).intValue() : 30;
    }

    /**
     * Gets heal amount for Rejuvenate enchant
     */
    public double getRejuvenateHealAmount(int enchantLevel) {
        Object amounts = getEnchantEffect("rejuvenate", "heal-amounts", enchantLevel);
        return amounts instanceof Number ? ((Number) amounts).doubleValue() : 4.0;
    }

    /**
     * Gets heal chance for Rejuvenate enchant
     */
    public double getRejuvenateHealChance(int enchantLevel) {
        Object chances = getEnchantEffect("rejuvenate", "heal-chances", enchantLevel);
        return chances instanceof Number ? ((Number) chances).doubleValue() : 0.10;
    }

    /**
     * Gets head drop chance for Guillotine enchant
     */
    public double getGuillotineHeadDropChance(int enchantLevel) {
        Object chances = getEnchantEffect("guillotine", "head-drop-chances", enchantLevel);
        return chances instanceof Number ? ((Number) chances).doubleValue() : 0.10;
    }

    /**
     * Gets steal chance for Pantsed enchant
     */
    public double getPantsedStealChance(int enchantLevel) {
        Object chances = getEnchantEffect("pantsed", "steal-chances", enchantLevel);
        return chances instanceof Number ? ((Number) chances).doubleValue() : 0.03;
    }

    /**
     * Gets area size for Detonate enchant
     */
    public int getDetonateAreaSize(int enchantLevel) {
        Object sizes = getEnchantEffect("detonate", "area-sizes", enchantLevel);
        return sizes instanceof Number ? ((Number) sizes).intValue() : 1;
    }

    /**
     * Gets golem count for Backup enchant
     */
    public int getBackupGolemCount(int enchantLevel) {
        Object counts = getEnchantEffect("backup", "golem-counts", enchantLevel);
        return counts instanceof Number ? ((Number) counts).intValue() : 1;
    }

    /**
     * Gets Almighty Push radius
     */
    public double getAlmightyPushRadius() {
        Object radius = getEnchantEffect("almighty_push", "push-radius", 1);
        return radius instanceof Number ? ((Number) radius).doubleValue() : 10.0;
    }

    /**
     * Gets Almighty Push strength
     */
    public double getAlmightyPushStrength() {
        Object strength = getEnchantEffect("almighty_push", "push-strength", 1);
        return strength instanceof Number ? ((Number) strength).doubleValue() : 3.0;
    }

    /**
     * Gets configurable trigger health for Rejuvenate
     */
    public double getRejuvenateTriggerHealth() {
        Object triggerHealth = getEnchantEffect("rejuvenate", "trigger-health", 1);
        return triggerHealth instanceof Number ? ((Number) triggerHealth).doubleValue() : 6.0;
    }

    /**
     * Checks if an enchant requires a full armor set
     */
    public boolean requiresFullSet(String enchantName) {
        return plugin.getConfigManager().getBoolean("enchants.yml", "enchants." + enchantName + ".requires-full-set", false);
    }

    /**
     * Gets enchant cooldown
     */
    public int getEnchantCooldown(String enchantName) {
        return plugin.getConfigManager().getInt("enchants.yml", "enchants." + enchantName + ".cooldown", 0);
    }

    /**
     * Gets enchant death message
     */
    public String getEnchantDeathMessage(String enchantName) {
        return plugin.getConfigManager().getString("enchants.yml", "enchants." + enchantName + ".death-message", "");
    }

    /**
     * Check if player meets enchant requirements - FIXED
     */
    public CompletableFuture<Boolean> meetsRequirements(org.bukkit.entity.Player player, String enchantName, int level) {
        CustomEnchant enchant = getEnchant(enchantName);
        if (enchant == null) return CompletableFuture.completedFuture(false);

        UnlockRequirement requirement = enchant.getUnlockRequirement(level);
        if (requirement == null || requirement.getType() == RequirementType.NONE) {
            return CompletableFuture.completedFuture(true);
        }

        switch (requirement.getType()) {
            case BLOCKS_MINED:
            case BLOCKS_WALKED:
            case WHEAT_BROKEN:
            case CREEPERS_KILLED:
            case IRON_INGOTS:
            case PANTS_CRAFTED:
            case SOULS:
                String statisticName = getStatisticName(requirement.getType());
                return plugin.getPlayerDataManager().getStatistic(player.getUniqueId(), statisticName)
                        .thenApply(current -> current >= requirement.getAmount());

            case MONEY:
                if (plugin.getEconomy() != null) {
                    double balance = plugin.getEconomy().getBalance(player);
                    return CompletableFuture.completedFuture(balance >= requirement.getAmount());
                }
                return CompletableFuture.completedFuture(false);

            case EXP_LEVELS:
                return CompletableFuture.completedFuture(player.getLevel() >= requirement.getAmount());

            case BOSS_FIGHT:
                // Redemption enchant - unlocked through boss fight
                return plugin.getPlayerDataManager().hasEnchantUnlocked(player.getUniqueId(), enchantName);

            default:
                return CompletableFuture.completedFuture(true);
        }
    }

    /**
     * Get requirement progress for display - FIXED
     */
    public CompletableFuture<String> getRequirementProgress(org.bukkit.entity.Player player, String enchantName, int level) {
        CustomEnchant enchant = getEnchant(enchantName);
        if (enchant == null) return CompletableFuture.completedFuture("Unknown enchant");

        UnlockRequirement requirement = enchant.getUnlockRequirement(level);
        if (requirement == null || requirement.getType() == RequirementType.NONE) {
            return CompletableFuture.completedFuture("&aNo requirements");
        }

        if (requirement.getType().requiresStatistics()) {
            String statisticName = getStatisticName(requirement.getType());
            return plugin.getPlayerDataManager().getStatistic(player.getUniqueId(), statisticName)
                    .thenApply(current -> {
                        String formatted = plugin.getStatisticManager().getFormattedProgress(current, requirement.getAmount());
                        return requirement.getFormattedMessage(current) + " " + formatted;
                    });
        } else {
            return CompletableFuture.completedFuture(requirement.getMessage());
        }
    }

    /**
     * Convert requirement type to statistic name
     */
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

    /**
     * Gets current enchant name (for requirement loading)
     */
    public String getCurrentEnchantName() {
        return currentEnchantName;
    }

    /**
     * Gets current level (for requirement loading)
     */
    public int getCurrentLevel() {
        return currentLevel;
    }

    /**
     * Reloads all enchants from configuration
     */
    public void reload() {
        loadEnchants();
    }
}