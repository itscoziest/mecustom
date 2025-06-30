package com.mystenchants.listeners;

import com.mystenchants.MystEnchants;
import com.mystenchants.enchants.CustomEnchant;
import org.bukkit.Material;
import org.bukkit.block.data.Ageable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Handles block break events for statistics tracking and enchant effects
 */
public class BlockBreakListener implements Listener {

    private final MystEnchants plugin;

    public BlockBreakListener(MystEnchants plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;

        // Track statistics
        plugin.getStatisticManager().trackBlockMined(event.getPlayer(), event.getBlock().getType());

        // Handle wheat tracking (only fully grown)
        if (event.getBlock().getType() == Material.WHEAT) {
            if (event.getBlock().getBlockData() instanceof Ageable) {
                Ageable ageable = (Ageable) event.getBlock().getBlockData();
                if (ageable.getAge() == ageable.getMaximumAge()) {
                    plugin.getStatisticManager().trackWheatBroken(event.getPlayer());
                }
            }
        }

        // Handle enchant effects
        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        if (plugin.getEnchantManager().hasCustomEnchant(tool)) {
            CustomEnchant enchant = plugin.getEnchantManager().getCustomEnchant(tool);
            int level = plugin.getEnchantManager().getCustomEnchantLevel(tool);

            if (enchant != null) {
                handleEnchantEffect(event, enchant, level);
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        // Track block placement for anti-farm system
        plugin.getStatisticManager().trackBlockPlaced(event.getPlayer(), event.getBlock().getType());
    }

    private void handleEnchantEffect(BlockBreakEvent event, CustomEnchant enchant, int level) {
        switch (enchant.getName()) {
            case "detonate":
                handleDetonate(event, level);
                break;
            // Add other mining-related enchants here
        }
    }

    private void handleDetonate(BlockBreakEvent event, int level) {
        // Area mining effect
        int radius = level == 1 ? 1 : level == 2 ? 1 : 2; // 2x2, 3x3, 5x5

        org.bukkit.Location center = event.getBlock().getLocation();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x == 0 && y == 0 && z == 0) continue; // Skip center block (already broken)

                    org.bukkit.Location blockLoc = center.clone().add(x, y, z);
                    org.bukkit.block.Block block = blockLoc.getBlock();

                    if (block.getType() != Material.AIR && block.getType() != Material.BEDROCK) {
                        // Check if player can break this block
                        if (block.getType().getHardness() >= 0) {
                            block.breakNaturally(event.getPlayer().getInventory().getItemInMainHand());
                        }
                    }
                }
            }
        }
    }
}