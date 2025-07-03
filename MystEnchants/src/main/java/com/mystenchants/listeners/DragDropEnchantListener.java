package com.mystenchants.listeners;

import com.mystenchants.MystEnchants;
import com.mystenchants.enchants.CustomEnchant;
import com.mystenchants.utils.ColorUtils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class DragDropEnchantListener implements Listener {

    private final MystEnchants plugin;
    private final NamespacedKey enchantKey;
    private final NamespacedKey levelKey;

    public DragDropEnchantListener(MystEnchants plugin) {
        this.plugin = plugin;
        this.enchantKey = new NamespacedKey(plugin, "custom_enchant");
        this.levelKey = new NamespacedKey(plugin, "enchant_level");
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        // We only care about the SWAP_WITH_CURSOR action for drag-and-drop
        if (event.getAction() != InventoryAction.SWAP_WITH_CURSOR) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        ItemStack cursor = event.getCursor();         // The item on the cursor (the dye)
        ItemStack targetItem = event.getCurrentItem(); // The item being enchanted

        if (cursor == null || targetItem == null || targetItem.getType() == Material.AIR) {
            return;
        }

        // Check if the item on the cursor is one of our custom enchant dyes
        if (!isCustomEnchantDye(cursor)) {
            return;
        }

        // We now know a dye is being dropped on an item. Take full control of the event.
        event.setCancelled(true);

        CustomEnchant enchant = getEnchantFromDye(cursor);
        int level = getEnchantLevel(cursor);

        if (enchant != null && canApplyToItemWithLevel(enchant, level, targetItem)) {
            // FIXED: Let applyEnchant handle vanilla enchant preservation
            // No need to duplicate the logic here since EnchantManager.applyEnchant() already handles it
            ItemStack enchantedItem = plugin.getEnchantManager().applyEnchant(targetItem, enchant, level);

            if (enchantedItem != null) {
                // Set the new, fully enchanted item back into the slot synchronously.
                event.setCurrentItem(enchantedItem);

                // Manually consume one dye from the cursor since the event is cancelled.
                if (cursor.getAmount() > 1) {
                    cursor.setAmount(cursor.getAmount() - 1);
                    // Manually update the cursor in the player's view
                    player.setItemOnCursor(cursor);
                } else {
                    player.setItemOnCursor(null);
                }

                // Play effects and send messages.
                playEnchantApplyEffects(player);
                player.sendMessage(ColorUtils.color("&a&l✓ APPLIED! &7" + enchant.getDisplayName() + " &7Level &7" + level));
            }
        } else if (enchant != null) {
            // If not compatible, show an error and do not consume the dye.
            showIncompatibleMessage(player, enchant, targetItem);
        }
    }

    private boolean canApplyToItemWithLevel(CustomEnchant enchant, int newLevel, ItemStack item) {
        if (item.getType() == org.bukkit.Material.AIR) {
            return false;
        }

        if (!enchant.isApplicableTo(item.getType())) {
            return false;
        }

        // Check if this specific enchant already exists on the item.
        if (plugin.getEnchantManager().hasSpecificCustomEnchant(item, enchant.getName())) {
            int existingLevel = plugin.getEnchantManager().getSpecificCustomEnchantLevel(item, enchant.getName());

            // Allow the enchant only if the new level is higher than what's already there.
            return newLevel > existingLevel;
        }

        // If the enchant isn't on the item, and since there is no limit, it can always be applied.
        return true;
    }

    private boolean isCustomEnchantDye(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        if (!container.has(enchantKey, PersistentDataType.STRING)) {
            return false;
        }
        // Check if the item is one of the specific dye materials you use
        switch (item.getType()) {
            case LIGHT_BLUE_DYE:
            case LIME_DYE:
            case YELLOW_DYE:
            case ORANGE_DYE:
            case RED_DYE:
            case PINK_DYE:
                return true;
            default:
                return false;
        }
    }

    private CustomEnchant getEnchantFromDye(ItemStack dye) {
        String enchantName = dye.getItemMeta().getPersistentDataContainer().get(enchantKey, PersistentDataType.STRING);
        return plugin.getEnchantManager().getEnchant(enchantName);
    }

    private int getEnchantLevel(ItemStack dye) {
        return dye.getItemMeta().getPersistentDataContainer().getOrDefault(levelKey, PersistentDataType.INTEGER, 1);
    }

    private void showIncompatibleMessage(Player player, CustomEnchant enchant, ItemStack item) {
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        String message = "&cCannot apply {enchant} to {item}!";
        player.sendMessage(ColorUtils.color(message.replace("{enchant}", enchant.getDisplayName()).replace("{item}", item.getType().name().toLowerCase().replace("_", " "))));
    }

    private void playEnchantApplyEffects(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
        player.spawnParticle(org.bukkit.Particle.ENCHANTMENT_TABLE, player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
    }
}