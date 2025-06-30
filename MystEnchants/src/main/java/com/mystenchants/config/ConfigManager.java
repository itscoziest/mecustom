package com.mystenchants.config;

import com.mystenchants.MystEnchants;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Enhanced ConfigManager with comprehensive configuration support
 * Manages all configuration files for the plugin with full configurability
 */
public class ConfigManager {

    private final MystEnchants plugin;
    private final Map<String, FileConfiguration> configs = new HashMap<>();
    private final Map<String, File> configFiles = new HashMap<>();

    private final String[] CONFIG_FILES = {
            "config.yml",
            "enchants.yml",
            "guis.yml",
            "perks.yml",
            "statistics.yml"
    };

    public ConfigManager(MystEnchants plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads all configuration files
     */
    public void loadConfigs() {
        // Create plugin data folder if it doesn't exist
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        // Load each configuration file
        for (String fileName : CONFIG_FILES) {
            loadConfig(fileName);
        }

        plugin.getLogger().info("Loaded " + configs.size() + " configuration files.");
    }

    /**
     * Loads a specific configuration file
     */
    private void loadConfig(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);

        // Create file from resource if it doesn't exist
        if (!file.exists()) {
            try {
                // Save resource from plugin jar
                InputStream inputStream = plugin.getResource(fileName);
                if (inputStream != null) {
                    Files.copy(inputStream, file.toPath());
                    plugin.getLogger().info("Created default " + fileName);
                } else {
                    // Create empty file if resource doesn't exist
                    file.createNewFile();
                    plugin.getLogger().warning("Resource " + fileName + " not found, created empty file.");
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create " + fileName, e);
                return;
            }
        }

        // Load the configuration
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        configs.put(fileName, config);
        configFiles.put(fileName, file);
    }

    /**
     * Gets a configuration by file name
     */
    public FileConfiguration getConfig(String fileName) {
        return configs.get(fileName);
    }

    /**
     * Gets the main config.yml
     */
    public FileConfiguration getMainConfig() {
        return getConfig("config.yml");
    }

    /**
     * Gets the enchants.yml config
     */
    public FileConfiguration getEnchantsConfig() {
        return getConfig("enchants.yml");
    }

    /**
     * Gets the guis.yml config
     */
    public FileConfiguration getGuisConfig() {
        return getConfig("guis.yml");
    }

    /**
     * Gets the perks.yml config
     */
    public FileConfiguration getPerksConfig() {
        return getConfig("perks.yml");
    }

    /**
     * Gets the statistics.yml config
     */
    public FileConfiguration getStatisticsConfig() {
        return getConfig("statistics.yml");
    }

    /**
     * Saves a specific configuration file
     */
    public void saveConfig(String fileName) {
        FileConfiguration config = configs.get(fileName);
        File file = configFiles.get(fileName);

        if (config != null && file != null) {
            try {
                config.save(file);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not save " + fileName, e);
            }
        }
    }

    /**
     * Saves all configuration files
     */
    public void saveAllConfigs() {
        for (String fileName : configs.keySet()) {
            saveConfig(fileName);
        }
    }

    /**
     * Reloads a specific configuration file
     */
    public void reloadConfig(String fileName) {
        File file = configFiles.get(fileName);
        if (file != null && file.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            configs.put(fileName, config);
        }
    }

    /**
     * Reloads all configuration files
     */
    public void reloadAllConfigs() {
        for (String fileName : CONFIG_FILES) {
            reloadConfig(fileName);
        }
    }

    /**
     * Gets a string value with color code support
     */
    public String getString(String fileName, String path, String defaultValue) {
        FileConfiguration config = getConfig(fileName);
        if (config != null) {
            return config.getString(path, defaultValue);
        }
        return defaultValue;
    }

    /**
     * Gets an integer value
     */
    public int getInt(String fileName, String path, int defaultValue) {
        FileConfiguration config = getConfig(fileName);
        if (config != null) {
            return config.getInt(path, defaultValue);
        }
        return defaultValue;
    }

    /**
     * Gets a double value
     */
    public double getDouble(String fileName, String path, double defaultValue) {
        FileConfiguration config = getConfig(fileName);
        if (config != null) {
            return config.getDouble(path, defaultValue);
        }
        return defaultValue;
    }

    /**
     * Gets a long value
     */
    public long getLong(String fileName, String path, long defaultValue) {
        FileConfiguration config = getConfig(fileName);
        if (config != null) {
            return config.getLong(path, defaultValue);
        }
        return defaultValue;
    }

    /**
     * Gets a boolean value
     */
    public boolean getBoolean(String fileName, String path, boolean defaultValue) {
        FileConfiguration config = getConfig(fileName);
        if (config != null) {
            return config.getBoolean(path, defaultValue);
        }
        return defaultValue;
    }

    /**
     * Sets a value in a configuration file
     */
    public void setValue(String fileName, String path, Object value) {
        FileConfiguration config = getConfig(fileName);
        if (config != null) {
            config.set(path, value);
        }
    }

    /**
     * Checks if a path exists in a configuration file
     */
    public boolean contains(String fileName, String path) {
        FileConfiguration config = getConfig(fileName);
        return config != null && config.contains(path);
    }

    // ========================================
    // ENHANCED METHODS FOR FULL CONFIGURABILITY
    // ========================================

    /**
     * Gets enchant requirement amounts (supports all requirement types)
     */
    public long getEnchantRequirement(String enchantName, int level, String requirementType) {
        String path = requirementType.toLowerCase().replace("_", "-") + "-requirements." + enchantName + ".level-" + level;
        return getLong("config.yml", path, 0);
    }

    /**
     * Gets all perk effect values with full configurability
     */
    public Object getPerkProperty(String perkName, String property) {
        return getPerksConfig().get("perks." + perkName + "." + property);
    }

    /**
     * Gets sound configuration with volume and pitch
     */
    public SoundConfig getSoundConfig(String path) {
        String soundName = getString("config.yml", path + ".sound", "UI_BUTTON_CLICK");
        float volume = (float) getDouble("config.yml", path + ".sound-volume", 1.0);
        float pitch = (float) getDouble("config.yml", path + ".sound-pitch", 1.0);
        return new SoundConfig(soundName, volume, pitch);
    }

    /**
     * Gets perk sound configuration with volume and pitch
     */
    public SoundConfig getPerkSoundConfig(String perkName, String effectType) {
        String basePath = "perks." + perkName + ".effects";
        String soundName = getString("perks.yml", basePath + "." + effectType + "-sound", "UI_BUTTON_CLICK");
        float volume = (float) getDouble("perks.yml", basePath + "." + effectType + "-volume", 1.0);
        float pitch = (float) getDouble("perks.yml", basePath + "." + effectType + "-pitch", 1.0);
        return new SoundConfig(soundName, volume, pitch);
    }

    /**
     * Gets particle configuration
     */
    public ParticleConfig getParticleConfig(String path) {
        String particleName = getString("config.yml", path + ".particles", "ENCHANTMENT_TABLE");
        int count = getInt("config.yml", path + ".particle-count", 30);
        return new ParticleConfig(particleName, count);
    }

    /**
     * Gets GUI sound effects
     */
    public String getGuiSound(String soundType) {
        return getString("guis.yml", "sounds." + soundType, "UI_BUTTON_CLICK");
    }

    /**
     * Gets statistic tracking settings
     */
    public boolean isStatisticTrackingEnabled(String statisticType) {
        return getBoolean("statistics.yml", "tracking.track-" + statisticType.replace("_", "-"), true);
    }

    /**
     * Gets anti-farm timer for specific requirement type
     */
    public long getAntiFarmTimer(String requirementType) {
        return getLong("statistics.yml", "unlock-requirements." + requirementType + ".anti-farm-timer", 300) * 1000L;
    }

    /**
     * Gets milestone percentages for progress tracking
     */
    public java.util.List<Integer> getMilestonePercentages() {
        return getStatisticsConfig().getIntegerList("progress-display.milestone-percentages");
    }

    /**
     * Gets leaderboard configuration
     */
    public boolean isLeaderboardEnabled() {
        return getBoolean("statistics.yml", "leaderboards.enabled", true);
    }

    /**
     * Gets tracked blocks for mining statistics
     */
    public java.util.List<String> getTrackedBlocks() {
        return getStatisticsConfig().getStringList("unlock-requirements.BLOCKS_MINED.track-blocks");
    }

    /**
     * Gets tracked entities for kill statistics
     */
    public java.util.List<String> getTrackedEntities(String requirementType) {
        return getStatisticsConfig().getStringList("unlock-requirements." + requirementType + ".track-entities");
    }

    /**
     * Gets EXP cost for enchant upgrade
     */
    public int getEnchantExpCost(String enchantName, int level) {
        return getInt("config.yml", "exp-costs." + enchantName + ".level-" + level, 50);
    }

    /**
     * Gets boss fight configuration
     */
    public String getBossFightSetting(String setting) {
        return getString("config.yml", "boss-fight." + setting, "");
    }

    public int getBossFightIntSetting(String setting, int defaultValue) {
        return getInt("config.yml", "boss-fight." + setting, defaultValue);
    }

    public double getBossFightDoubleSetting(String setting, double defaultValue) {
        return getDouble("config.yml", "boss-fight." + setting, defaultValue);
    }

    // Helper classes for complex config objects
    public static class SoundConfig {
        public final String sound;
        public final float volume;
        public final float pitch;

        public SoundConfig(String sound, float volume, float pitch) {
            this.sound = sound;
            this.volume = volume;
            this.pitch = pitch;
        }
    }

    public static class ParticleConfig {
        public final String particle;
        public final int count;

        public ParticleConfig(String particle, int count) {
            this.particle = particle;
            this.count = count;
        }
    }
}