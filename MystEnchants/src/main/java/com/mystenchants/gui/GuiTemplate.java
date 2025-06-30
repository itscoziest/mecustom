package com.mystenchants.gui;

import java.util.Map;

/**
 * Represents a GUI template loaded from configuration
 */
public class GuiTemplate {

    private final String name;
    private final String title;
    private final int size;
    private final Map<String, GuiItem> items;
    private final GuiItem backButton;

    public GuiTemplate(String name, String title, int size, Map<String, GuiItem> items, GuiItem backButton) {
        this.name = name;
        this.title = title;
        this.size = size;
        this.items = items;
        this.backButton = backButton;
    }

    public String getName() {
        return name;
    }

    public String getTitle() {
        return title;
    }

    public int getSize() {
        return size;
    }

    public Map<String, GuiItem> getItems() {
        return items;
    }

    public GuiItem getBackButton() {
        return backButton;
    }

    public GuiItem getItem(String name) {
        return items.get(name);
    }
}