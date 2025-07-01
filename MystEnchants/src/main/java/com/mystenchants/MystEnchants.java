package com.mystenchants;

import com.mystenchants.commands.*;
import com.mystenchants.config.ConfigManager;
import com.mystenchants.database.DatabaseManager;
import com.mystenchants.enchants.EnchantManager;
import com.mystenchants.gui.GuiManager;
import com.mystenchants.listeners.*;
import com.mystenchants.managers.*;
import com.mystenchants.integrations.*;
import com.mystenchants.utils.ColorUtils;
import com.mystenchants.managers.ZetsuboSacrificeManager;
import com.mystenchants.listeners.ZetsuboRegionMoveListener;
import com.mystenchants.commands.ZetsuboCommand;

import net.milkbowl.vault.economy.Economy;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class MystEnchants extends JavaPlugin {

    private static MystEnchants instance;
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private EnchantManager enchantManager;
    private GuiManager guiManager;
    private SoulManager soulManager;
    private PlayerDataManager playerDataManager;
    private RedemptionManager redemptionManager;
    private PerkManager perkManager;
    private StatisticManager statisticManager;
    private WorthySacrificeManager worthySacrificeManager;
    private SnowmanManager snowmanManager;
    private Economy economy;
    private MythicMobsIntegration mythicMobsIntegration;
    private MythicBossFightManager mythicBossFightManager;
    private BackupEnchantListener backupEnchantListener;
    private ZetsuboSacrificeManager zetsuboSacrificeManager;


    // ADDED: Store listener instances for cleanup access
    private PerkCombatListener perkCombatListener;

    @Override
    public void onEnable() {
        instance = this;

        // Initialize configuration
        configManager = new ConfigManager(this);
        configManager.loadConfigs();

        // Setup economy
        if (!setupEconomy()) {
            getLogger().severe("Vault not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.zetsuboSacrificeManager = new ZetsuboSacrificeManager(this);

        mythicMobsIntegration = new MythicMobsIntegration(this);
        if (mythicMobsIntegration.isMythicMobsEnabled()) {
            mythicBossFightManager = new MythicBossFightManager(this, mythicMobsIntegration);
            getLogger().info("MythicMobs boss fight integration loaded!");
        }

        // Initialize database
        try {
            databaseManager = new DatabaseManager(this);
            databaseManager.initialize();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to initialize database!", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize managers
        enchantManager = new EnchantManager(this);
        guiManager = new GuiManager(this);
        soulManager = new SoulManager(this);
        playerDataManager = new PlayerDataManager(this);
        redemptionManager = new RedemptionManager(this);
        perkManager = new PerkManager(this);
        statisticManager = new StatisticManager(this);
        worthySacrificeManager = new WorthySacrificeManager(this);
        snowmanManager = new SnowmanManager(this);
        this.zetsuboSacrificeManager = new ZetsuboSacrificeManager(this);

        // Register WorldGuard integration using PlayerMoveEvent instead of SessionManager
        if (getServer().getPluginManager().getPlugin("WorldGuard") != null) {
            getServer().getPluginManager().registerEvents(new ZetsuboRegionMoveListener(this), this);
            getLogger().info("WorldGuard integration enabled for Zetsubo Sacrifice system");
        } else {
            getLogger().warning("WorldGuard not found! Zetsubo Sacrifice system will not work.");
        }



        // Register commands
        registerCommands();

        // Register listeners
        registerListeners();

        if (getServer().getPluginManager().getPlugin("WorldGuard") != null) {
            getServer().getPluginManager().registerEvents(new ZetsuboRegionMoveListener(this), this);
            getLogger().info("WorldGuard integration enabled for Zetsubo Sacrifice");
        }

        // Initialize metrics
        new Metrics(this, 19584);

        getLogger().info(ColorUtils.color("&aMystEnchants v" + getDescription().getVersion() + " has been enabled!"));
    }

    @Override
    public void onDisable() {
        if (redemptionManager != null) {
            redemptionManager.cleanup();
        }

        // ADDED: Cleanup backup golems on disable
        if (backupEnchantListener != null) {
            backupEnchantListener.cleanupAll();
        }

        if (databaseManager != null) {
            databaseManager.close();
        }

        if (zetsuboSacrificeManager != null) {
            zetsuboSacrificeManager.shutdown();
        }

        getLogger().info(ColorUtils.color("&cMystEnchants has been disabled!"));
    }

    // ADDED: Getter for BackupEnchantListener cleanup access
    public BackupEnchantListener getBackupEnchantListener() {
        return backupEnchantListener;
    }

    private void registerCommands() {
        getCommand("enchants").setExecutor(new EnchantsCommand(this));
        getCommand("soulshop").setExecutor(new SoulShopCommand(this));
        getCommand("souls").setExecutor(new SoulsCommand(this));
        getCommand("oracle").setExecutor(new OracleCommand(this));
        getCommand("perks").setExecutor(new PerksCommand(this));
        getCommand("redemption").setExecutor(new RedemptionCommand(this));
        getCommand("enchant").setExecutor(new EnchantApplyCommand(this));
        getCommand("zetsubo").setExecutor(new ZetsuboCommand(this));
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockBreakListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerMoveListener(this), this);
        getServer().getPluginManager().registerEvents(new EntityDamageListener(this), this);
        getServer().getPluginManager().registerEvents(new EntityDeathListener(this), this);
        getServer().getPluginManager().registerEvents(new CraftItemListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerInteractListener(this), this);

        getServer().getPluginManager().registerEvents(new InventoryClickListener(this), this);

        getServer().getPluginManager().registerEvents(new EnchantListener(this), this);
        getServer().getPluginManager().registerEvents(new PerkListener(this), this);

        getServer().getPluginManager().registerEvents(new DragDropEnchantListener(this), this);

        perkCombatListener = new PerkCombatListener(this);
        getServer().getPluginManager().registerEvents(perkCombatListener, this);
        getServer().getPluginManager().registerEvents(new PerkProjectileListener(this), this);
        getServer().getPluginManager().registerEvents(worthySacrificeManager, this);

        getServer().getPluginManager().registerEvents(new ChickenSpawnPrevention(this), this);

        backupEnchantListener = new BackupEnchantListener(this);
        getServer().getPluginManager().registerEvents(backupEnchantListener, this);
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }


    public ZetsuboSacrificeManager getZetsuboSacrificeManager() {
        return zetsuboSacrificeManager;
    }


    public static MystEnchants getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public EnchantManager getEnchantManager() {
        return enchantManager;
    }

    public GuiManager getGuiManager() {
        return guiManager;
    }

    public SoulManager getSoulManager() {
        return soulManager;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public RedemptionManager getRedemptionManager() {
        return redemptionManager;
    }

    public PerkManager getPerkManager() {
        return perkManager;
    }

    public StatisticManager getStatisticManager() {
        return statisticManager;
    }

    public WorthySacrificeManager getWorthySacrificeManager() {
        return worthySacrificeManager;
    }

    public SnowmanManager getSnowmanManager() {
        return snowmanManager;
    }

    public Economy getEconomy() {
        return economy;
    }

    // ADDED: Getter for PerkCombatListener cleanup access
    public PerkCombatListener getPerkCombatListener() {
        return perkCombatListener;
    }

    public MythicMobsIntegration getMythicMobsIntegration() {
        return mythicMobsIntegration;
    }

    public MythicBossFightManager getMythicBossFightManager() {
        return mythicBossFightManager;
    }
}