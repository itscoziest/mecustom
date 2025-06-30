package com.mystenchants.enchants;

import org.bukkit.Material;

/**
 * Represents the different tiers of enchantments
 */
public enum EnchantTier {

    COMMON("&b", "Common", Material.LIGHT_BLUE_DYE, 1),
    UNCOMMON("&a", "Uncommon", Material.LIME_DYE, 2),
    RARE("&e", "Rare", Material.YELLOW_DYE, 3),
    ULTIMATE("&6", "Ultimate", Material.ORANGE_DYE, 4),
    LEGENDARY("&c", "Legendary", Material.RED_DYE, 5),
    MYSTICAL("&d", "Mystical", Material.PINK_DYE, 6);

    private final String color;
    private final String displayName;
    private final Material guiItem;
    private final int level;

    EnchantTier(String color, String displayName, Material guiItem, int level) {
        this.color = color;
        this.displayName = displayName;
        this.guiItem = guiItem;
        this.level = level;
    }

    public String getColor() {
        return color;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getGuiItem() {
        return guiItem;
    }

    public int getLevel() {
        return level;
    }

    public String getColoredName() {
        return color + displayName;
    }

    /**
     * Gets tier by level
     */
    public static EnchantTier getByLevel(int level) {
        for (EnchantTier tier : values()) {
            if (tier.level == level) {
                return tier;
            }
        }
        return COMMON;
    }
}