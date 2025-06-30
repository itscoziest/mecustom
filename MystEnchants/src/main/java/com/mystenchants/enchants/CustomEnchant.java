package com.mystenchants.enchants;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

/**
 * Represents a custom enchantment
 */
public class CustomEnchant {

    private final String name;
    private final EnchantTier tier;
    private final int maxLevel;
    private final String displayName;
    private final List<String> description;
    private final List<Material> applicableItems;
    private final Map<Integer, UnlockRequirement> unlockRequirements;
    private final boolean requiresFullSet;
    private final int cooldown;
    private final String deathMessage;

    public CustomEnchant(String name, EnchantTier tier, int maxLevel, String displayName,
                         List<String> description, List<Material> applicableItems,
                         Map<Integer, UnlockRequirement> unlockRequirements,
                         boolean requiresFullSet, int cooldown, String deathMessage) {
        this.name = name;
        this.tier = tier;
        this.maxLevel = maxLevel;
        this.displayName = displayName;
        this.description = description;
        this.applicableItems = applicableItems;
        this.unlockRequirements = unlockRequirements;
        this.requiresFullSet = requiresFullSet;
        this.cooldown = cooldown;
        this.deathMessage = deathMessage;
    }

    public String getName() {
        return name;
    }

    public EnchantTier getTier() {
        return tier;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getDescription() {
        return description;
    }

    public List<Material> getApplicableItems() {
        return applicableItems;
    }

    public Map<Integer, UnlockRequirement> getUnlockRequirements() {
        return unlockRequirements;
    }

    public UnlockRequirement getUnlockRequirement(int level) {
        return unlockRequirements.get(level);
    }

    public boolean requiresFullSet() {
        return requiresFullSet;
    }

    public int getCooldown() {
        return cooldown;
    }

    public String getDeathMessage() {
        return deathMessage;
    }

    public boolean isApplicableTo(Material material) {
        return applicableItems.contains(material);
    }

    public boolean hasUnlockRequirement(int level) {
        return unlockRequirements.containsKey(level);
    }

    @Override
    public String toString() {
        return "CustomEnchant{" +
                "name='" + name + '\'' +
                ", tier=" + tier +
                ", maxLevel=" + maxLevel +
                '}';
    }
}