package com.mystenchants.enchants;

/**
 * Types of unlock requirements for enchants
 */
public enum RequirementType {

    // No requirement
    NONE,

    // Block-based requirements
    BLOCKS_MINED,
    BLOCKS_WALKED,
    WHEAT_BROKEN,

    // Entity-based requirements
    CREEPERS_KILLED,

    // Item-based requirements
    IRON_INGOTS,
    PANTS_CRAFTED,

    // Currency-based requirements
    SOULS,
    MONEY,
    EXP_LEVELS,

    // Special requirements
    BOSS_FIGHT,

    SACRIFICE_COMPLETED,
    TBD; // To be determined



    /**
     * Checks if this requirement type requires currency
     */
    public boolean isCurrency() {
        return this == SOULS || this == MONEY || this == EXP_LEVELS;
    }

    /**
     * Checks if this requirement type requires statistics
     */
    public boolean requiresStatistics() {
        return this == BLOCKS_MINED || this == BLOCKS_WALKED ||
                this == WHEAT_BROKEN || this == CREEPERS_KILLED ||
                this == IRON_INGOTS || this == PANTS_CRAFTED;
    }

    /**
     * Checks if this requirement type is special
     */
    public boolean isSpecial() {
        return this == BOSS_FIGHT || this == TBD || this == NONE;
    }
}