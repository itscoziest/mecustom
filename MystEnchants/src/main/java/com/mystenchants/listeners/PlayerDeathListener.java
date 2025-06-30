package com.mystenchants.listeners;

import com.mystenchants.MystEnchants;
import com.mystenchants.enchants.CustomEnchant;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Handles player death events for enchant effects
 */
public class PlayerDeathListener implements Listener {

    private final MystEnchants plugin;

    public PlayerDeathListener(MystEnchants plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // Handle redemption boss fight deaths
        plugin.getRedemptionManager().handlePlayerDeath(player);

        // Handle redemption enchant (keep items on death)
        handleRedemptionEnchant(event);
    }

    private void handleRedemptionEnchant(PlayerDeathEvent event) {
        Player player = event.getEntity();
        List<ItemStack> itemsToKeep = new ArrayList<>();

        // Check all items for redemption enchant
        Iterator<ItemStack> iterator = event.getDrops().iterator();
        while (iterator.hasNext()) {
            ItemStack item = iterator.next();

            if (plugin.getEnchantManager().hasCustomEnchant(item)) {
                CustomEnchant enchant = plugin.getEnchantManager().getCustomEnchant(item);

                if (enchant != null && enchant.getName().equals("redemption")) {
                    // Keep this item and remove the enchant (one-time use)
                    ItemStack keptItem = plugin.getEnchantManager().removeEnchant(item);
                    itemsToKeep.add(keptItem);
                    iterator.remove();

                    player.sendMessage(org.bukkit.ChatColor.GOLD + "Redemption enchant activated! Item saved from death.");

                    // Remove enchant from player's unlocked enchants
                    plugin.getPlayerDataManager().removeEnchant(player.getUniqueId(), "redemption");
                }
            }
        }

        // Add kept items back to player inventory on respawn
        if (!itemsToKeep.isEmpty()) {
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (ItemStack item : itemsToKeep) {
                    if (player.getInventory().firstEmpty() != -1) {
                        player.getInventory().addItem(item);
                    } else {
                        player.getWorld().dropItemNaturally(player.getLocation(), item);
                    }
                }
            }, 1L);
        }
    }
}