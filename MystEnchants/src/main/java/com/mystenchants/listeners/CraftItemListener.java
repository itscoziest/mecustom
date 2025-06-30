package com.mystenchants.listeners;

import com.mystenchants.MystEnchants;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;

/**
 * Handles crafting events for statistics tracking
 */
public class CraftItemListener implements Listener {

    private final MystEnchants plugin;

    public CraftItemListener(MystEnchants plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        Material craftedItem = event.getRecipe().getResult().getType();

        // Track pants crafting for Pantsed enchant unlock requirements
        if (isPants(craftedItem)) {
            plugin.getStatisticManager().trackPantsCrafted(player, craftedItem);
        }
    }

    /**
     * Checks if the material is a type of pants/leggings
     */
    private boolean isPants(Material material) {
        return material == Material.LEATHER_LEGGINGS ||
                material == Material.CHAINMAIL_LEGGINGS ||
                material == Material.IRON_LEGGINGS ||
                material == Material.GOLDEN_LEGGINGS ||
                material == Material.DIAMOND_LEGGINGS ||
                material == Material.NETHERITE_LEGGINGS;
    }
}