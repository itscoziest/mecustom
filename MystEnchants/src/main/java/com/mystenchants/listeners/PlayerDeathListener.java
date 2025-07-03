package com.mystenchants.listeners;

import com.mystenchants.MystEnchants;
import com.mystenchants.utils.ColorUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class PlayerDeathListener implements Listener {

    private final MystEnchants plugin;

    public PlayerDeathListener(MystEnchants plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // First, check if the player died during the redemption boss fight. If so, let that manager handle it.
        if (plugin.getRedemptionManager().isRedemptionActive() && player.equals(plugin.getRedemptionManager().getCurrentFighter())) {
            plugin.getRedemptionManager().handlePlayerDeath(player);
            return; // Do not trigger the keep inventory effect during the boss fight.
        }

        // Now, check for the Redemption "Keep Inventory" effect.
        PlayerInventory inventory = player.getInventory();

        // Check every item in the main inventory and armor slots.
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);

            if (item == null) {
                continue;
            }

            // Use the reliable method to check for the Redemption enchant.
            int redemptionLevel = plugin.getEnchantManager().getSpecificCustomEnchantLevel(item, "redemption");

            if (redemptionLevel > 0) {
                // Redemption Enchant Found!

                // 1. Tell the game to keep the player's inventory and EXP.
                event.setKeepInventory(true);
                event.setDroppedExp(0);

                // 2. Clear the drops list to prevent any items from dropping accidentally.
                event.getDrops().clear();

                player.sendMessage(ColorUtils.color("&a&lYour Redemption enchant has saved your inventory!"));

                // 3. Consume the enchant (one-time use) by removing it from the item that triggered the effect.
                ItemStack consumedItem = plugin.getEnchantManager().removeSpecificEnchantByName(item.clone(), "redemption");
                inventory.setItem(i, consumedItem); // Update the item in the inventory.

                // 4. We found the enchant and triggered the effect, so we can stop the loop.
                return;
            }
        }
    }
}