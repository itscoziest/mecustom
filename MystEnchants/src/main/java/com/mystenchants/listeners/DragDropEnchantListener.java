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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DragDropEnchantListener implements Listener {

    private final MystEnchants plugin;
    private final NamespacedKey enchantKey;
    private final NamespacedKey levelKey;
    private final Map<UUID, Long> lastEnchantApply = new HashMap<>();
    private static final long ENCHANT_APPLY_COOLDOWN = 1000;

    public DragDropEnchantListener(MystEnchants plugin) {
        this.plugin = plugin;
        this.enchantKey = new NamespacedKey(plugin, "custom_enchant");
        this.levelKey = new NamespacedKey(plugin, "enchant_level");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();

        if (cursor == null || !isCustomEnchantDye(cursor)) return;
        if (current == null || current.getType() == Material.AIR) return;

        // Only handle specific drag/drop actions
        if (event.getAction() == InventoryAction.SWAP_WITH_CURSOR ||
                event.getAction() == InventoryAction.PLACE_ALL ||
                event.getAction() == InventoryAction.PLACE_ONE ||
                event.getAction() == InventoryAction.PLACE_SOME) {

            CustomEnchant enchant = getEnchantFromDye(cursor);
            int level = getEnchantLevel(cursor);

            if (enchant != null) {
                // Always cancel the event first to prevent default behavior
                event.setCancelled(true);

                if (canApplyToItem(enchant, current)) {
                    // Check spam prevention
                    UUID playerUUID = player.getUniqueId();
                    long currentTime = System.currentTimeMillis();
                    Long lastApply = lastEnchantApply.get(playerUUID);

                    if (lastApply != null && (currentTime - lastApply) < ENCHANT_APPLY_COOLDOWN) {
                        return;
                    }

                    lastEnchantApply.put(playerUUID, currentTime);

                    // Apply enchant in next tick
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        applyEnchantToItem(player, enchant, level, current, event.getSlot());
                        consumeDye(player, cursor);
                    });
                } else {
                    // Show incompatible message but don't consume the dye
                    showIncompatibleMessage(player, enchant, current);
                }
            }
        }
    }

    private boolean isCustomEnchantDye(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.has(enchantKey, PersistentDataType.STRING);
    }

    private CustomEnchant getEnchantFromDye(ItemStack dye) {
        ItemMeta meta = dye.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        String enchantName = container.get(enchantKey, PersistentDataType.STRING);
        return plugin.getEnchantManager().getEnchant(enchantName);
    }

    private int getEnchantLevel(ItemStack dye) {
        ItemMeta meta = dye.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.getOrDefault(levelKey, PersistentDataType.INTEGER, 1);
    }

    private boolean canApplyToItem(CustomEnchant enchant, ItemStack item) {
        if (item.getType() == Material.AIR) return false;
        if (!enchant.isApplicableTo(item.getType())) return false;

        // Check if item already has this enchant
        if (plugin.getEnchantManager().hasCustomEnchant(item)) {
            CustomEnchant existingEnchant = plugin.getEnchantManager().getCustomEnchant(item);
            if (existingEnchant != null && existingEnchant.getName().equals(enchant.getName())) {
                return false;
            }
        }

        return true;
    }

    private void applyEnchantToItem(Player player, CustomEnchant enchant, int level, ItemStack targetItem, int slot) {
        // Clone the original item to avoid reference issues
        ItemStack clonedItem = targetItem.clone();
        ItemStack enchantedItem = plugin.getEnchantManager().applyEnchant(clonedItem, enchant, level);

        // Set the enchanted item
        player.getInventory().setItem(slot, enchantedItem);
        player.updateInventory();

        String successMessage = plugin.getConfigManager().getString("config.yml",
                "messages.enchant-apply-success", "&a&l✓ APPLIED! &7{enchant} Level {level} → {item}");
        successMessage = successMessage.replace("{enchant}", enchant.getDisplayName())
                .replace("{level}", String.valueOf(level))
                .replace("{item}", formatMaterialName(targetItem.getType()));
        player.sendMessage(ColorUtils.color(successMessage));

        playEnchantApplyEffects(player);
    }

    private void consumeDye(Player player, ItemStack dye) {
        ItemStack newCursor;
        if (dye.getAmount() > 1) {
            newCursor = dye.clone();
            newCursor.setAmount(dye.getAmount() - 1);
        } else {
            newCursor = null;
        }
        player.setItemOnCursor(newCursor);
    }

    private void showIncompatibleMessage(Player player, CustomEnchant enchant, ItemStack item) {
        UUID playerUUID = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        Long lastApply = lastEnchantApply.get(playerUUID);

        if (lastApply != null && (currentTime - lastApply) < 500) {
            return;
        }

        lastEnchantApply.put(playerUUID, currentTime);

        String message = plugin.getConfigManager().getString("config.yml",
                "messages.enchant-apply-no-compatible", "&cCannot apply {enchant} to {item}!");
        message = message.replace("{enchant}", enchant.getDisplayName())
                .replace("{item}", formatMaterialName(item.getType()));
        player.sendMessage(ColorUtils.color(message));

        StringBuilder compatibleItems = new StringBuilder();
        for (Material material : enchant.getApplicableItems()) {
            if (compatibleItems.length() > 0) compatibleItems.append(", ");
            compatibleItems.append(formatMaterialName(material));
        }

        String compatibleMessage = plugin.getConfigManager().getString("config.yml",
                "messages.enchant-apply-compatible-items", "&7Compatible items: &f{items}");
        compatibleMessage = compatibleMessage.replace("{items}", compatibleItems.toString());
        player.sendMessage(ColorUtils.color(compatibleMessage));

        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
    }

    private void playEnchantApplyEffects(Player player) {
        String soundName = plugin.getConfigManager().getString("config.yml",
                "effects.enchant-apply.sound", "BLOCK_ENCHANTMENT_TABLE_USE");
        float volume = (float) plugin.getConfigManager().getDouble("config.yml",
                "effects.enchant-apply.sound-volume", 1.0);
        float pitch = (float) plugin.getConfigManager().getDouble("config.yml",
                "effects.enchant-apply.sound-pitch", 1.2);

        try {
            Sound sound = Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException e) {
            player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
        }

        String particleName = plugin.getConfigManager().getString("config.yml",
                "effects.enchant-apply.particles", "ENCHANTMENT_TABLE");
        int particleCount = plugin.getConfigManager().getInt("config.yml",
                "effects.enchant-apply.particle-count", 30);

        try {
            org.bukkit.Particle particle = org.bukkit.Particle.valueOf(particleName);
            player.spawnParticle(particle, player.getLocation().add(0, 1, 0),
                    particleCount, 0.5, 0.5, 0.5, 0.1);
        } catch (Exception e) {
            // Ignore if particles don't work
        }
    }

    private String formatMaterialName(Material material) {
        String name = material.name().toLowerCase().replace("_", " ");
        String[] words = name.split(" ");
        StringBuilder formatted = new StringBuilder();

        for (String word : words) {
            if (formatted.length() > 0) formatted.append(" ");
            formatted.append(word.substring(0, 1).toUpperCase()).append(word.substring(1));
        }

        return formatted.toString();
    }
}