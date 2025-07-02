package com.mystenchants.managers;

import com.mystenchants.MystEnchants;
import com.mystenchants.enchants.CustomEnchant;
import com.mystenchants.utils.ColorUtils;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ZetsuboSacrificeManager {

    private final MystEnchants plugin;
    private final Map<UUID, Location> playerLastLocations = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> activeRituals = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> lockTasks = new ConcurrentHashMap<>();
    private final Set<UUID> completedSacrifices = new HashSet<>();
    private final Map<UUID, Float> playerOriginalYaw = new ConcurrentHashMap<>();
    private final Map<UUID, Float> playerOriginalPitch = new ConcurrentHashMap<>();

    public ZetsuboSacrificeManager(MystEnchants plugin) {
        this.plugin = plugin;
        loadCompletedSacrifices();
    }

    public void onPlayerEnterSacrificeRegion(Player player, String regionName) {
        if (hasCompletedSacrifice(player.getUniqueId())) {
            player.sendMessage(ColorUtils.color("&cYou have already completed the Zetsubo sacrifice ritual!"));
            Location lastLocation = playerLastLocations.get(player.getUniqueId());
            if (lastLocation != null) {
                player.teleport(lastLocation);
            }
            return;
        }
        startSacrificeRitual(player);
    }

    public void onPlayerLeaveRegion(Player player, Location location) {
        if (!isInSacrificeRegion(location)) {
            playerLastLocations.put(player.getUniqueId(), location.clone());
        }
    }

    private void startSacrificeRitual(Player player) {
        UUID playerId = player.getUniqueId();

        cancelRitual(playerId);

        playerOriginalYaw.put(playerId, player.getLocation().getYaw());
        playerOriginalPitch.put(playerId, player.getLocation().getPitch());

        lockPlayer(player);

        String enterTitle = "&4&lYou have entered";
        String enterSubtitle = "&4the Sacrification Room...";
        player.sendTitle(ColorUtils.color(enterTitle), ColorUtils.color(enterSubtitle), 10, 60, 10);

        BukkitTask ritual = new BukkitRunnable() {
            int ticksElapsed = 0;
            boolean hasCheckedItems = false;

            @Override
            public void run() {
                plugin.getLogger().info("Ritual tick " + ticksElapsed + " for " + player.getName());

                if (!player.isOnline()) {
                    plugin.getLogger().info("Player offline, cancelling ritual");
                    cancelRitual(playerId);
                    return;
                }

                ticksElapsed += 20;

                if (ticksElapsed == 60 && !hasCheckedItems) {
                    String checkTitle = "&6Please wait";
                    String checkSubtitle = "&6while we check if you have all required items...";
                    player.sendTitle(ColorUtils.color(checkTitle), ColorUtils.color(checkSubtitle), 10, 40, 10);
                    plugin.getLogger().info("Showing check message for " + player.getName());
                }

                if (ticksElapsed >= 120 && !hasCheckedItems) {
                    hasCheckedItems = true;
                    plugin.getLogger().info("Checking items for " + player.getName());

                    if (hasRequiredItems(player)) {
                        plugin.getLogger().info("Player has items, completing sacrifice");
                        completeSacrifice(player);
                        cancel(); // FIXED: Cancel ritual task
                    } else {
                        plugin.getLogger().info("Player missing items, rejecting");
                        rejectPlayer(player);
                        cancel(); // FIXED: Cancel ritual task
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);

        activeRituals.put(playerId, ritual);
        plugin.getLogger().info("Started ritual for " + player.getName());
    }

    private void rejectPlayer(Player player) {
        UUID playerId = player.getUniqueId();

        String rejectTitle = "&c&lYou do not have";
        String rejectSubtitle = "&cthe required sacrifice items!";
        player.sendTitle(ColorUtils.color(rejectTitle), ColorUtils.color(rejectSubtitle), 10, 60, 10);

        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.5f);

        // Unlock player immediately
        unlockPlayer(player);

        // Clean up immediately
        activeRituals.remove(playerId);
        cleanupRitual(playerId);
    }

    private void unlockPlayer(Player player) {
        UUID playerId = player.getUniqueId();

        plugin.getLogger().info("Unlocking player " + player.getName());

        // Cancel lock task first
        BukkitTask lockTask = lockTasks.remove(playerId);
        if (lockTask != null) {
            lockTask.cancel();
            plugin.getLogger().info("Cancelled lock task for " + player.getName());
        }

        // Remove all potion effects
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.SLOW);
        player.removePotionEffect(PotionEffectType.JUMP);
        player.removePotionEffect(PotionEffectType.SLOW_DIGGING);

        plugin.getLogger().info("Removed all effects for " + player.getName());
    }

    private void lockPlayer(Player player) {
        UUID playerId = player.getUniqueId();

        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 999999, 1, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 999999, 10, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 999999, -10, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, 999999, 10, false, false));

        player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, 1.0f, 0.5f);

        BukkitTask lockTask = new BukkitRunnable() {
            final Location lockLocation = player.getLocation().clone();
            final float originalYaw = playerOriginalYaw.get(playerId);
            final float originalPitch = playerOriginalPitch.get(playerId);

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }

                if (!activeRituals.containsKey(playerId)) {
                    cancel();
                    return;
                }

                if (player.getLocation().distance(lockLocation) > 0.1) {
                    Location resetLoc = lockLocation.clone();
                    resetLoc.setYaw(originalYaw);
                    resetLoc.setPitch(originalPitch);
                    player.teleport(resetLoc);
                }

                if (Math.abs(player.getLocation().getYaw() - originalYaw) > 1 ||
                        Math.abs(player.getLocation().getPitch() - originalPitch) > 1) {
                    Location resetLoc = player.getLocation().clone();
                    resetLoc.setYaw(originalYaw);
                    resetLoc.setPitch(originalPitch);
                    player.teleport(resetLoc);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);

        lockTasks.put(playerId, lockTask);
    }

    private boolean hasRequiredItems(Player player) {
        List<String> requiredItems = Arrays.asList(
                "NETHERITE_INGOT:10",
                "DRAGON_HEAD:1",
                "TOTEM_OF_UNDYING:3",
                "NETHER_STAR:5",
                "ENCHANTED_GOLDEN_APPLE:8"
        );

        for (String itemConfig : requiredItems) {
            String[] parts = itemConfig.split(":");
            Material material = Material.valueOf(parts[0]);
            int amount = Integer.parseInt(parts[1]);

            if (!hasItem(player, material, amount)) {
                plugin.getLogger().info("Player missing: " + amount + "x " + material.name());
                return false;
            }
        }
        plugin.getLogger().info("Player has all required items");
        return true;
    }

    private boolean hasItem(Player player, Material material, int amount) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count >= amount;
    }

    private void removeRequiredItems(Player player) {
        List<String> requiredItems = Arrays.asList(
                "NETHERITE_INGOT:10",
                "DRAGON_HEAD:1",
                "TOTEM_OF_UNDYING:3",
                "NETHER_STAR:5",
                "ENCHANTED_GOLDEN_APPLE:8"
        );

        for (String itemConfig : requiredItems) {
            String[] parts = itemConfig.split(":");
            Material material = Material.valueOf(parts[0]);
            int amount = Integer.parseInt(parts[1]);

            removeItem(player, material, amount);
        }
    }

    private void removeItem(Player player, Material material, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();

        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == material) {
                int itemAmount = item.getAmount();
                if (itemAmount <= remaining) {
                    contents[i] = null;
                    remaining -= itemAmount;
                } else {
                    item.setAmount(itemAmount - remaining);
                    remaining = 0;
                }
            }
        }

        player.getInventory().setContents(contents);
        player.updateInventory();
    }

    private void completeSacrifice(Player player) {
        UUID playerId = player.getUniqueId();
        Location ritualLocation = player.getLocation();

        plugin.getLogger().info("Starting sacrifice completion for " + player.getName());

        removeRequiredItems(player);

        String successTitle = "&a&lAll sacrifice items";
        String successSubtitle = "&ahave been collected!";
        player.sendTitle(ColorUtils.color(successTitle), ColorUtils.color(successSubtitle), 10, 60, 10);

        ArmorStand totem = (ArmorStand) player.getWorld().spawnEntity(
                ritualLocation.clone().add(0, 2, 0), EntityType.ARMOR_STAND);
        totem.setVisible(false);
        totem.setGravity(false);
        totem.setInvulnerable(true);
        totem.getEquipment().setHelmet(new ItemStack(Material.TOTEM_OF_UNDYING));

        new BukkitRunnable() {
            int strikes = 0;

            @Override
            public void run() {
                if (strikes < 3) {
                    // Visual effect without damage
                    Location effectLoc = ritualLocation.clone().add(2, 0, 2);
                    player.getWorld().createExplosion(effectLoc, 0.0f, false, false);
                    player.playSound(ritualLocation, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);

                    // ADDED: Totem activation effect during lightning
                    player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);

                    // ADDED: Totem particles effect
                    for (int i = 0; i < 30; i++) {
                        double x = ritualLocation.getX() + (Math.random() - 0.5) * 4;
                        double y = ritualLocation.getY() + Math.random() * 3;
                        double z = ritualLocation.getZ() + (Math.random() - 0.5) * 4;
                        Location particleLoc = new Location(player.getWorld(), x, y, z);
                        player.getWorld().spawnParticle(org.bukkit.Particle.TOTEM, particleLoc, 1, 0, 0, 0, 0);
                    }

                    plugin.getLogger().info("Lightning strike " + (strikes + 1) + " for " + player.getName());
                    strikes++;
                } else {
                    plugin.getLogger().info("Completing sacrifice for " + player.getName());

                    totem.remove();

                    // Unlock player FIRST
                    unlockPlayer(player);

                    // FIXED: Give enchant on main thread with delay to ensure unlock completes
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        giveZetsuboEnchant(player);

                        // Mark as completed
                        completedSacrifices.add(playerId);
                        saveCompletedSacrifices();

                        plugin.getLogger().info("Sacrifice completed for " + player.getName());
                    }, 5L); // 5 tick delay

                    cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);

        // Clean up ritual immediately
        activeRituals.remove(playerId);
        cleanupRitual(playerId);
    }

    private void giveZetsuboEnchant(Player player) {
        plugin.getLogger().info("Giving Zetsubo enchant to " + player.getName());

        // Set both levels 1 and 2 to be unlocked
        plugin.getPlayerDataManager().setEnchantLevel(player.getUniqueId(), "zetsubo", 2)
                .thenRun(() -> {
                    plugin.getLogger().info("Set Zetsubo level 2 for " + player.getName());
                });

        // CRITICAL FIX: Use EnchantManager to create proper enchant dye
        CustomEnchant zetsuboEnchant = plugin.getEnchantManager().getEnchant("zetsubo");

        if (zetsuboEnchant == null) {
            plugin.getLogger().severe("ERROR: Zetsubo enchant not found in EnchantManager!");
            player.sendMessage(ColorUtils.color("&cError: Zetsubo enchant not configured properly!"));
            return;
        }

        // Create proper enchant dye using EnchantManager
        ItemStack zetsuboDye = plugin.getEnchantManager().createEnchantDye(zetsuboEnchant, 1);

        if (zetsuboDye == null) {
            plugin.getLogger().severe("ERROR: Failed to create Zetsubo enchant!");
            player.sendMessage(ColorUtils.color("&cError: Failed to create Zetsubo enchant item!"));
            return;
        }

        plugin.getLogger().info("Created Zetsubo dye: " + zetsuboDye.getType() + " with meta: " +
                (zetsuboDye.hasItemMeta() ? zetsuboDye.getItemMeta().getDisplayName() : "no meta"));

        // Give the dye to player
        HashMap<Integer, ItemStack> remaining = player.getInventory().addItem(zetsuboDye);
        for (ItemStack item : remaining.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }

        String enchantMessage = "&d&lYou have received the Zetsubo Level 1 enchant!";
        player.sendMessage(ColorUtils.color(enchantMessage));

        String unlockMessage = "&aBoth Zetsubo Level 1 and 2 are now available in the Soul Shop!";
        player.sendMessage(ColorUtils.color(unlockMessage));

        String instructionMessage = "&6&lDrag and drop this enchant onto your armor to apply it!";
        player.sendMessage(ColorUtils.color(instructionMessage));

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        plugin.getLogger().info("Zetsubo enchant given to " + player.getName());
    }

    private void cancelRitual(UUID playerId) {
        BukkitTask task = activeRituals.remove(playerId);
        if (task != null) {
            task.cancel();
        }

        BukkitTask lockTask = lockTasks.remove(playerId);
        if (lockTask != null) {
            lockTask.cancel();
        }

        cleanupRitual(playerId);
    }

    private void cleanupRitual(UUID playerId) {
        playerOriginalYaw.remove(playerId);
        playerOriginalPitch.remove(playerId);
    }

    private boolean isInSacrificeRegion(Location location) {
        String regionName = "zetsubo_sacrifice";

        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionQuery query = container.createQuery();

            ApplicableRegionSet regions = query.getApplicableRegions(BukkitAdapter.adapt(location));

            for (ProtectedRegion region : regions) {
                if (region.getId().equals(regionName)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean hasCompletedSacrifice(UUID playerId) {
        return completedSacrifices.contains(playerId);
    }

    private void loadCompletedSacrifices() {
        // Load from config if exists
    }

    private void saveCompletedSacrifices() {
        // Save to config
    }

    public void resetPlayerSacrifice(UUID playerId) {
        completedSacrifices.remove(playerId);
        saveCompletedSacrifices();
    }

    public void shutdown() {
        for (BukkitTask task : activeRituals.values()) {
            task.cancel();
        }
        activeRituals.clear();

        for (BukkitTask task : lockTasks.values()) {
            task.cancel();
        }
        lockTasks.clear();

        playerLastLocations.clear();
        playerOriginalYaw.clear();
        playerOriginalPitch.clear();
    }
}