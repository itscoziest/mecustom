package com.mystenchants.gui;

import org.bukkit.Material;

import java.util.List;

/**
 * Represents an item in a GUI template
 */
public class GuiItem {

    private final int slot;
    private final Material material;
    private final String name;
    private final List<String> lore;
    private final boolean glow;

    public GuiItem(int slot, Material material, String name, List<String> lore, boolean glow) {
        this.slot = slot;
        this.material = material;
        this.name = name;
        this.lore = lore;
        this.glow = glow;
    }

    public int getSlot() {
        return slot;
    }

    public Material getMaterial() {
        return material;
    }

    public String getName() {
        return name;
    }

    public List<String> getLore() {
        return lore;
    }

    public boolean hasGlow() {
        return glow;
    }
}