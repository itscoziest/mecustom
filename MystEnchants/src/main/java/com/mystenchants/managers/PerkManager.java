package com.mystenchants.managers;

import com.mystenchants.MystEnchants;
import com.mystenchants.utils.ColorUtils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Enhanced PerkManager with complete configuration support
 * ALL values are now fully configurable as requested by client
 */
public class PerkManager {

    private final MystEnchants plugin;
    private final Map<String, PerkData> perks = new HashMap<>();
    private final NamespacedKey perkKey;
    private final NamespacedKey perkShopKey; // ADDED: Separate key for shop items

    public PerkManager(MystEnchants plugin) {
        this.plugin = plugin;
        this.perkKey = new NamespacedKey(plugin, "perk_type");
        this.perkShopKey = new NamespacedKey(plugin, "perk_shop_item"); // ADDED: For shop items only
        loadPerks();
    }

    /**
     * Checks if an item is a perk SHOP item (for purchasing)
     */
    public boolean isPerkShopItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        return container.has(perkShopKey, PersistentDataType.STRING);
    }

    /**
     * Gets the perk name from a SHOP item
     */
    public String getPerkNameFromShopItem(ItemStack item) {
        if (!isPerkShopItem(item)) return null;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        return container.get(perkShopKey, PersistentDataType.STRING);
    }

    /**
     * Creates a perk SHOP item (for GUI purchasing)
     */
    public ItemStack createPerkShopItem(String perkName) {
        PerkData perk = perks.get(perkName);
        if (perk == null) return null;

        ItemStack item = new ItemStack(perk.getMaterial());
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(perk.getDisplayName());

            List<String> processedLore = perk.getLore().stream()
                    .map(line -> replacePlaceholders(line, perk))
                    .collect(java.util.stream.Collectors.toList());

            meta.setLore(processedLore);

            // Mark as SHOP item
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(perkShopKey, PersistentDataType.STRING, perkName);

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Loads all perks from configuration
     */
    private void loadPerks() {
        perks.clear();

        ConfigurationSection perksSection = plugin.getConfigManager().getPerksConfig().getConfigurationSection("perks");
        if (perksSection == null) return;

        for (String perkName : perksSection.getKeys(false)) {
            ConfigurationSection perkSection = perksSection.getConfigurationSection(perkName);
            if (perkSection != null) {
                PerkData perkData = loadPerk(perkName, perkSection);
                perks.put(perkName, perkData);
            }
        }

        plugin.getLogger().info("Loaded " + perks.size() + " perks.");
    }

    /**
     * Enhanced perk data loading with ALL configurable properties
     */
    private PerkData loadPerk(String name, ConfigurationSection section) {
        String displayName = ColorUtils.color(section.getString("display-name", name));
        Material material = Material.valueOf(section.getString("material", "STONE"));
        List<String> lore = ColorUtils.color(section.getStringList("lore"));
        int cost = section.getInt("cost", 100);
        int cooldown = section.getInt("cooldown", 30);

        Map<String, Object> properties = new HashMap<>();

        // Load ALL configurable properties (CLIENT REQUESTED FULL CONFIGURABILITY)
        properties.put("cooldown", cooldown);
        properties.put("cost", cost);
        properties.put("hook-time", section.getInt("hook-time", 5));
        properties.put("duration", section.getInt("duration", 15));
        properties.put("required-hits", section.getInt("required-hits", 5));
        properties.put("witch-health", section.getInt("witch-health", 300));
        properties.put("pull-strength", section.getDouble("pull-strength", 2.0));
        properties.put("max-distance", section.getInt("max-distance", 30));
        properties.put("attack-range", section.getInt("attack-range", 10));
        properties.put("slow-duration", section.getInt("slow-duration", 3));
        properties.put("effect-duration", section.getInt("effect-duration", 5));
        properties.put("lock-duration", section.getInt("lock-duration", 5));
        properties.put("nausea-duration", section.getInt("nausea-duration", 3));
        properties.put("rose-duration", section.getInt("rose-duration", 3));
        properties.put("nausea-amplifier", section.getInt("nausea-amplifier", 2));
        properties.put("slow-amplifier", section.getInt("slow-amplifier", 1));
        properties.put("reset-on-damage", section.getBoolean("reset-on-damage", true));
        properties.put("snowman-health", section.getInt("snowman-health", 20));
        properties.put("attack-interval", section.getDouble("attack-interval", 1.5));
        properties.put("damage-transfer", section.getInt("damage-transfer", 100));
        properties.put("follow-range", section.getInt("follow-range", 15));

        // Load sound effects with volume and pitch (FULLY CONFIGURABLE)
        ConfigurationSection effectsSection = section.getConfigurationSection("effects");
        if (effectsSection != null) {
            for (String key : effectsSection.getKeys(false)) {
                properties.put("effect-" + key, effectsSection.get(key));
            }
        }

        // Load healthbar configuration (FULLY CONFIGURABLE)
        ConfigurationSection healthbarSection = section.getConfigurationSection("healthbar");
        if (healthbarSection != null) {
            properties.put("healthbar-enabled", healthbarSection.getBoolean("enabled", true));
            properties.put("healthbar-format", healthbarSection.getString("format", "&5&l❤ &fWitch Health: &c{health}&f/&c{max-health}"));
            properties.put("healthbar-update-interval", healthbarSection.getInt("update-interval", 10));
        }

        return new PerkData(name, displayName, material, lore, cost, cooldown, properties);
    }

    /**
     * Creates a perk item with ALL placeholder replacements
     */
    public ItemStack createPerkItem(String perkName) {
        PerkData perk = perks.get(perkName);
        if (perk == null) return null;

        ItemStack item = new ItemStack(perk.getMaterial());
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(perk.getDisplayName());

            // Process lore with ALL placeholders (CLIENT REQUESTED)
            List<String> processedLore = perk.getLore().stream()
                    .map(line -> replacePlaceholders(line, perk))
                    .collect(java.util.stream.Collectors.toList());

            meta.setLore(processedLore);

            // Set perk identifier
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(perkKey, PersistentDataType.STRING, perkName);

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Replace ALL placeholders in perk lore (CLIENT REQUESTED)
     */
    private String replacePlaceholders(String line, PerkData perk) {
        return line.replace("{cooldown}", String.valueOf(perk.getCooldown()))
                .replace("{cost}", String.valueOf(perk.getCost()))
                .replace("{hook-time}", String.valueOf(perk.getIntProperty("hook-time", 5)))
                .replace("{duration}", String.valueOf(perk.getIntProperty("duration", 15)))
                .replace("{hits}", String.valueOf(perk.getIntProperty("required-hits", 5)))
                .replace("{health}", String.valueOf(perk.getIntProperty("witch-health", 300)))
                .replace("{pull-strength}", String.valueOf(perk.getDoubleProperty("pull-strength", 2.0)))
                .replace("{max-distance}", String.valueOf(perk.getIntProperty("max-distance", 30)))
                .replace("{attack-range}", String.valueOf(perk.getIntProperty("attack-range", 10)))
                .replace("{slow-duration}", String.valueOf(perk.getIntProperty("slow-duration", 3)))
                .replace("{effect-duration}", String.valueOf(perk.getIntProperty("effect-duration", 5)))
                .replace("{lock-duration}", String.valueOf(perk.getIntProperty("lock-duration", 5)))
                .replace("{nausea-duration}", String.valueOf(perk.getIntProperty("nausea-duration", 3)))
                .replace("{rose-duration}", String.valueOf(perk.getIntProperty("rose-duration", 3)));
    }

    // ========================================
    // ENHANCED GETTER METHODS - ALL CONFIGURABLE VALUES
    // ========================================

    public int getPerkCooldown(String perkName) {
        PerkData perk = perks.get(perkName);
        return perk != null ? perk.getCooldown() : 30;
    }

    public int getPerkRequiredHits(String perkName) {
        PerkData perk = perks.get(perkName);
        return perk != null ? perk.getIntProperty("required-hits", 5) : 5;
    }

    public int getPerkEffectDuration(String perkName) {
        PerkData perk = perks.get(perkName);
        return perk != null ? perk.getIntProperty("effect-duration", 5) : 5;
    }

    public boolean getPerkResetOnDamage(String perkName) {
        PerkData perk = perks.get(perkName);
        return perk != null ? perk.getBooleanProperty("reset-on-damage", true) : true;
    }

    public int getPerkNauseaDuration(String perkName) {
        PerkData perk = perks.get(perkName);
        return perk != null ? perk.getIntProperty("nausea-duration", 3) : 3;
    }

    public int getPerkRoseDuration(String perkName) {
        PerkData perk = perks.get(perkName);
        return perk != null ? perk.getIntProperty("rose-duration", 3) : 3;
    }

    public int getPerkLockDuration(String perkName) {
        PerkData perk = perks.get(perkName);
        return perk != null ? perk.getIntProperty("lock-duration", 5) : 5;
    }

    public int getPerkWitchHealth(String perkName) {
        PerkData perk = perks.get(perkName);
        return perk != null ? perk.getIntProperty("witch-health", 300) : 300;
    }

    public boolean getPerkHealthbarEnabled(String perkName) {
        PerkData perk = perks.get(perkName);
        return perk != null ? perk.getBooleanProperty("healthbar-enabled", true) : true;
    }

    public String getPerkHealthbarFormat(String perkName) {
        PerkData perk = perks.get(perkName);
        return perk != null ? perk.getStringProperty("healthbar-format", "&5&l❤ &fWitch Health: &c{health}&f/&c{max-health}") : "&5&l❤ &fWitch Health: &c{health}&f/&c{max-health}";
    }

    public double getPerkPullStrength(String perkName) {
        PerkData perk = perks.get(perkName);
        return perk != null ? perk.getDoubleProperty("pull-strength", 2.0) : 2.0;
    }

    public int getPerkMaxDistance(String perkName) {
        PerkData perk = perks.get(perkName);
        return perk != null ? perk.getIntProperty("max-distance", 30) : 30;
    }

    public int getPerkHookTime(String perkName) {
        PerkData perk = perks.get(perkName);
        return perk != null ? perk.getIntProperty("hook-time", 5) : 5;
    }

    public int getPerkDuration(String perkName) {
        PerkData perk = perks.get(perkName);
        return perk != null ? perk.getIntProperty("duration", 15) : 15;
    }

    public int getPerkAttackRange(String perkName) {
        PerkData perk = perks.get(perkName);
        return perk != null ? perk.getIntProperty("attack-range", 10) : 10;
    }

    public int getPerkSlowDuration(String perkName) {
        PerkData perk = perks.get(perkName);
        return perk != null ? perk.getIntProperty("slow-duration", 3) : 3;
    }

    public int getPerkSlowAmplifier(String perkName) {
        PerkData perk = perks.get(perkName);
        return perk != null ? perk.getIntProperty("slow-amplifier", 1) : 1;
    }

    public int getPerkNauseaAmplifier(String perkName) {
        PerkData perk = perks.get(perkName);
        return perk != null ? perk.getIntProperty("nausea-amplifier", 2) : 2;
    }

    public int getPerkSnowmanHealth(String perkName) {
        PerkData perk = perks.get(perkName);
        return perk != null ? perk.getIntProperty("snowman-health", 20) : 20;
    }

    public double getPerkAttackInterval(String perkName) {
        PerkData perk = perks.get(perkName);
        return perk != null ? perk.getDoubleProperty("attack-interval", 1.5) : 1.5;
    }

    public int getPerkDamageTransfer(String perkName) {
        PerkData perk = perks.get(perkName);
        return perk != null ? perk.getIntProperty("damage-transfer", 100) : 100;
    }

    public int getPerkFollowRange(String perkName) {
        PerkData perk = perks.get(perkName);
        return perk != null ? perk.getIntProperty("follow-range", 15) : 15;
    }

    public int getPerkHealthbarUpdateInterval(String perkName) {
        PerkData perk = perks.get(perkName);
        return perk != null ? perk.getIntProperty("healthbar-update-interval", 10) : 10;
    }

    // SOUND EFFECT GETTERS (FULLY CONFIGURABLE)
    public String getPerkSoundEffect(String perkName, String effectType) {
        PerkData perk = perks.get(perkName);
        return perk != null ? perk.getStringProperty("effect-" + effectType + "-sound", "UI_BUTTON_CLICK") : "UI_BUTTON_CLICK";
    }

    public float getPerkSoundVolume(String perkName, String effectType) {
        PerkData perk = perks.get(perkName);
        return (float) (perk != null ? perk.getDoubleProperty("effect-" + effectType + "-volume", 1.0) : 1.0);
    }

    public float getPerkSoundPitch(String perkName, String effectType) {
        PerkData perk = perks.get(perkName);
        return (float) (perk != null ? perk.getDoubleProperty("effect-" + effectType + "-pitch", 1.0) : 1.0);
    }

    /**
     * Checks if an item is a perk
     */
    public boolean isPerkItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        return container.has(perkKey, PersistentDataType.STRING);
    }

    /**
     * Gets the perk name from an item
     */
    public String getPerkName(ItemStack item) {
        if (!isPerkItem(item)) return null;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        return container.get(perkKey, PersistentDataType.STRING);
    }

    /**
     * Gets a perk by name
     */
    public PerkData getPerk(String name) {
        return perks.get(name);
    }

    /**
     * Checks if a player can use a perk (cooldown check)
     */
    public CompletableFuture<Boolean> canUsePerk(UUID playerUUID, String perkName) {
        PerkData perk = perks.get(perkName);
        if (perk == null) return CompletableFuture.completedFuture(false);

        return plugin.getPlayerDataManager().getPerkLastUsed(playerUUID, perkName)
                .thenApply(lastUsed -> {
                    long cooldownMs = perk.getCooldown() * 1000L;
                    long timeSinceUse = System.currentTimeMillis() - lastUsed;
                    return timeSinceUse >= cooldownMs;
                })
                .exceptionally(throwable -> {
                    plugin.getLogger().warning("Error checking perk cooldown: " + throwable.getMessage());
                    return true;
                });
    }

    /**
     * Gets remaining cooldown for a perk in seconds
     */
    public CompletableFuture<Long> getRemainingCooldown(UUID playerUUID, String perkName) {
        PerkData perk = perks.get(perkName);
        if (perk == null) return CompletableFuture.completedFuture(0L);

        return plugin.getPlayerDataManager().getPerkLastUsed(playerUUID, perkName)
                .thenApply(lastUsed -> {
                    long cooldownMs = perk.getCooldown() * 1000L;
                    long timeSinceUse = System.currentTimeMillis() - lastUsed;
                    long remaining = (cooldownMs - timeSinceUse) / 1000L;
                    return Math.max(0L, remaining);
                });
    }

    /**
     * Processes a perk purchase with configurable messaging
     */
    public CompletableFuture<Boolean> purchasePerk(Player player, String perkName) {
        PerkData perk = perks.get(perkName);
        if (perk == null) return CompletableFuture.completedFuture(false);

        return plugin.getSoulManager().hasSouls(player.getUniqueId(), perk.getCost())
                .thenCompose(hasSouls -> {
                    if (!hasSouls) {
                        String message = plugin.getConfigManager().getString("config.yml", "messages.insufficient-souls", "&cYou don't have enough souls!");
                        player.sendMessage(ColorUtils.color(message));
                        return CompletableFuture.completedFuture(false);
                    }

                    return plugin.getSoulManager().removeSouls(player.getUniqueId(), perk.getCost())
                            .thenCompose(success -> {
                                if (success) {
                                    return plugin.getPlayerDataManager().addPerk(player.getUniqueId(), perkName, 1)
                                            .thenApply(v -> {
                                                // Give perk item to player
                                                ItemStack perkItem = createPerkItem(perkName);
                                                if (perkItem != null) {
                                                    player.getInventory().addItem(perkItem);
                                                }

                                                String message = plugin.getConfigManager().getString("config.yml", "messages.perk-received", "&aYou received &6{amount}x {perk}&a!");
                                                message = message.replace("{amount}", "1").replace("{perk}", perk.getDisplayName());
                                                player.sendMessage(ColorUtils.color(message));

                                                return true;
                                            });
                                }
                                return CompletableFuture.completedFuture(false);
                            });
                });
    }

    /**
     * Uses a perk (if player has it and not on cooldown)
     */
    public CompletableFuture<Boolean> usePerk(Player player, String perkName) {
        return plugin.getPlayerDataManager().getPerkAmount(player.getUniqueId(), perkName)
                .thenCompose(amount -> {
                    if (amount <= 0) {
                        return plugin.getPlayerDataManager().addPerk(player.getUniqueId(), perkName, 1)
                                .thenCompose(v -> checkCooldownAndUse(player, perkName));
                    } else {
                        return checkCooldownAndUse(player, perkName);
                    }
                })
                .exceptionally(throwable -> {
                    plugin.getLogger().warning("Error using perk " + perkName + ": " + throwable.getMessage());
                    return false;
                });
    }

    private CompletableFuture<Boolean> checkCooldownAndUse(Player player, String perkName) {
        return canUsePerk(player.getUniqueId(), perkName)
                .thenCompose(canUse -> {
                    if (!canUse) {
                        return getRemainingCooldown(player.getUniqueId(), perkName)
                                .thenApply(remaining -> {
                                    String message = plugin.getConfigManager().getString("config.yml", "messages.perk-cooldown", "&cThis perk is on cooldown for &6{time}&c!");
                                    message = message.replace("{time}", ColorUtils.formatTime(remaining));
                                    player.sendMessage(ColorUtils.color(message));
                                    return false;
                                });
                    }

                    // Update last used time
                    return plugin.getPlayerDataManager().updatePerkLastUsed(player.getUniqueId(), perkName)
                            .thenApply(v -> true);
                });
    }

    /**
     * Reloads all perks
     */
    public void reload() {
        loadPerks();
    }

    /**
     * Enhanced PerkData class with all configurable properties
     */
    public static class PerkData {
        private final String name;
        private final String displayName;
        private final Material material;
        private final List<String> lore;
        private final int cost;
        private final int cooldown;
        private final Map<String, Object> properties;

        public PerkData(String name, String displayName, Material material, List<String> lore,
                        int cost, int cooldown, Map<String, Object> properties) {
            this.name = name;
            this.displayName = displayName;
            this.material = material;
            this.lore = lore;
            this.cost = cost;
            this.cooldown = cooldown;
            this.properties = properties;
        }

        public String getName() { return name; }
        public String getDisplayName() { return displayName; }
        public Material getMaterial() { return material; }
        public List<String> getLore() { return lore; }
        public int getCost() { return cost; }
        public int getCooldown() { return cooldown; }
        public Map<String, Object> getProperties() { return properties; }

        public Object getProperty(String key) {
            return properties.get(key);
        }

        public int getIntProperty(String key, int defaultValue) {
            Object value = properties.get(key);
            return value instanceof Number ? ((Number) value).intValue() : defaultValue;
        }

        public double getDoubleProperty(String key, double defaultValue) {
            Object value = properties.get(key);
            return value instanceof Number ? ((Number) value).doubleValue() : defaultValue;
        }

        public String getStringProperty(String key, String defaultValue) {
            Object value = properties.get(key);
            return value instanceof String ? (String) value : defaultValue;
        }

        public boolean getBooleanProperty(String key, boolean defaultValue) {
            Object value = properties.get(key);
            return value instanceof Boolean ? (Boolean) value : defaultValue;
        }
    }
}