package com.mystenchants.commands;

import com.mystenchants.MystEnchants;
import com.mystenchants.enchants.CustomEnchant;
import com.mystenchants.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Handles the /enchant command for applying enchants to held items
 */
public class EnchantApplyCommand implements CommandExecutor, TabCompleter {

    private final MystEnchants plugin;

    public EnchantApplyCommand(MystEnchants plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check basic permission
        if (!sender.hasPermission("mystenchants.enchant.use")) {
            sender.sendMessage(ColorUtils.color(plugin.getConfigManager().getString("config.yml", "messages.no-permission", "&cYou don't have permission!")));
            return true;
        }

        // Usage: /enchant <player> give <enchant> <level>
        if (args.length < 4) {
            sender.sendMessage(ColorUtils.color("&cUsage: /enchant <player> give <enchant> <level>"));
            return true;
        }





        if (!args[1].equalsIgnoreCase("give") && !args[1].equalsIgnoreCase("debug")) {
            sender.sendMessage(ColorUtils.color("&cUsage: /enchant <player> give <enchant> <level>"));
            return true;
        }

        if (args[1].equalsIgnoreCase("debug")) {
            sender.sendMessage(ColorUtils.color("&eDebug command not implemented yet"));
            return true;
        }


        String targetName = args[0];
        String enchantName = args[2];
        String levelStr = args[3];

        // Find target player
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            sender.sendMessage(ColorUtils.color(plugin.getConfigManager().getString("config.yml", "messages.player-not-found", "&cPlayer not found!")));
            return true;
        }

        // Find enchant
        CustomEnchant enchant = plugin.getEnchantManager().getEnchant(enchantName);
        if (enchant == null) {
            sender.sendMessage(ColorUtils.color("&cEnchant '" + enchantName + "' not found!"));
            return true;
        }

        // Parse level
        int level;
        try {
            level = Integer.parseInt(levelStr);
        } catch (NumberFormatException e) {
            sender.sendMessage(ColorUtils.color("&cInvalid level! Must be a number."));
            return true;
        }

        // Validate level
        if (level < 1 || level > enchant.getMaxLevel()) {
            sender.sendMessage(ColorUtils.color("&cInvalid level! Must be between 1 and " + enchant.getMaxLevel()));
            return true;
        }

        // Check if target has item in hand
        ItemStack heldItem = target.getInventory().getItemInMainHand();
        if (heldItem == null || heldItem.getType().isAir()) {
            sender.sendMessage(ColorUtils.color("&c" + target.getName() + " is not holding any item!"));
            return true;
        }

        // Check if enchant is applicable to the held item
        if (!enchant.isApplicableTo(heldItem.getType())) {
            sender.sendMessage(ColorUtils.color("&c" + enchant.getDisplayName() + " cannot be applied to " + formatMaterialName(heldItem.getType()) + "!"));

            // Show compatible items
            StringBuilder compatibleItems = new StringBuilder();
            for (org.bukkit.Material material : enchant.getApplicableItems()) {
                if (compatibleItems.length() > 0) compatibleItems.append(", ");
                compatibleItems.append(formatMaterialName(material));
            }
            sender.sendMessage(ColorUtils.color("&7Compatible items: &f" + compatibleItems.toString()));
            return true;
        }


        // Check permissions for bypassing unlock requirements
        boolean canBypass = sender.hasPermission("mystenchants.admin") || sender.hasPermission("mystenchants.enchant.bypass");

        if (!canBypass) {
            // Check if player has unlocked this enchant level
            plugin.getPlayerDataManager().getEnchantLevel(target.getUniqueId(), enchant.getName())
                    .thenAccept(currentLevel -> {
                        if (currentLevel < level) {
                            String message = plugin.getConfigManager().getString("config.yml", "messages.enchant-not-unlocked",
                                    "&c{player} has not unlocked {enchant} Level {level}!");
                            message = message.replace("{player}", target.getName())
                                    .replace("{enchant}", enchant.getDisplayName())
                                    .replace("{level}", String.valueOf(level));
                            sender.sendMessage(ColorUtils.color(message));
                            return;
                        }

                        // Player has unlocked it, proceed with application
                        applyEnchantToHeldItem(sender, target, enchant, level, heldItem);
                    });
        } else {
            // Admin bypass - apply directly
            applyEnchantToHeldItem(sender, target, enchant, level, heldItem);
        }

        return true;
    }




    /**
     * Applies the enchant to the player's held item
     */
    private void applyEnchantToHeldItem(CommandSender sender, Player target, CustomEnchant enchant, int level, ItemStack heldItem) {
        // Check if item already has a custom enchant
        if (plugin.getEnchantManager().hasCustomEnchant(heldItem)) {
            CustomEnchant existingEnchant = plugin.getEnchantManager().getCustomEnchant(heldItem);
            int existingLevel = plugin.getEnchantManager().getCustomEnchantLevel(heldItem);

            if (existingEnchant != null && existingEnchant.getName().equals(enchant.getName())) {
                // Same enchant - check if we're upgrading or downgrading
                if (existingLevel == level) {
                    sender.sendMessage(ColorUtils.color("&c" + target.getName() + "'s " + formatMaterialName(heldItem.getType()) + " already has " + enchant.getDisplayName() + " Level " + level + "!"));
                    return;
                }

                String action = level > existingLevel ? "upgraded" : "changed";

                // Remove existing enchant first
                ItemStack updatedItem = plugin.getEnchantManager().removeEnchant(heldItem);
                // Apply new level
                ItemStack enchantedItem = plugin.getEnchantManager().applyEnchant(updatedItem, enchant, level);
                target.getInventory().setItemInMainHand(enchantedItem);

                // Success messages
                String adminMessage = plugin.getConfigManager().getString("config.yml", "messages.enchant-apply-admin-success",
                        "&a{action} {player}'s {item} to {enchant} Level {level}!");
                adminMessage = adminMessage.replace("{action}", action.substring(0, 1).toUpperCase() + action.substring(1))
                        .replace("{player}", target.getName())
                        .replace("{item}", formatMaterialName(heldItem.getType()))
                        .replace("{enchant}", enchant.getDisplayName())
                        .replace("{level}", String.valueOf(level));
                sender.sendMessage(ColorUtils.color(adminMessage));

                String playerMessage = plugin.getConfigManager().getString("config.yml", "messages.enchant-apply-player-received",
                        "&aYour {item} has been {action} to {enchant} Level {level}!");
                playerMessage = playerMessage.replace("{action}", action)
                        .replace("{item}", formatMaterialName(heldItem.getType()))
                        .replace("{enchant}", enchant.getDisplayName())
                        .replace("{level}", String.valueOf(level));
                target.sendMessage(ColorUtils.color(playerMessage));

                return;
            } else {
                // Different enchant - ask for confirmation or force replace
                boolean forceReplace = plugin.getConfigManager().getBoolean("config.yml", "enchant-command.force-replace-different-enchant", false);

                if (!forceReplace) {
                    sender.sendMessage(ColorUtils.color("&c" + target.getName() + "'s " + formatMaterialName(heldItem.getType()) + " already has " + existingEnchant.getDisplayName() + " Level " + existingLevel + "!"));
                    sender.sendMessage(ColorUtils.color("&7Set 'enchant-command.force-replace-different-enchant: true' in config to allow replacing different enchants."));
                    return;
                }

                // Remove existing enchant
                heldItem = plugin.getEnchantManager().removeEnchant(heldItem);
            }
        }

        // Apply the enchant
        ItemStack enchantedItem = plugin.getEnchantManager().applyEnchant(heldItem, enchant, level);
        target.getInventory().setItemInMainHand(enchantedItem);

        // Play effects
        playEnchantApplyEffects(target);

        // Success messages
        String adminMessage = plugin.getConfigManager().getString("config.yml", "messages.enchant-apply-admin-success",
                "&aApplied {enchant} Level {level} to {player}'s {item}!");
        adminMessage = adminMessage.replace("{player}", target.getName())
                .replace("{item}", formatMaterialName(heldItem.getType()))
                .replace("{enchant}", enchant.getDisplayName())
                .replace("{level}", String.valueOf(level));
        sender.sendMessage(ColorUtils.color(adminMessage));

        String playerMessage = plugin.getConfigManager().getString("config.yml", "messages.enchant-apply-player-received",
                "&aYour {item} has been enchanted with {enchant} Level {level}!");
        playerMessage = playerMessage.replace("{item}", formatMaterialName(heldItem.getType()))
                .replace("{enchant}", enchant.getDisplayName())
                .replace("{level}", String.valueOf(level));
        target.sendMessage(ColorUtils.color(playerMessage));
    }

    /**
     * Plays enchant application effects
     */
    private void playEnchantApplyEffects(Player player) {
        String soundName = plugin.getConfigManager().getString("config.yml", "effects.enchant-apply.sound", "BLOCK_ENCHANTMENT_TABLE_USE");
        float volume = (float) plugin.getConfigManager().getDouble("config.yml", "effects.enchant-apply.sound-volume", 1.0);
        float pitch = (float) plugin.getConfigManager().getDouble("config.yml", "effects.enchant-apply.sound-pitch", 1.2);

        try {
            org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException e) {
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
        }

        String particleName = plugin.getConfigManager().getString("config.yml", "effects.enchant-apply.particles", "ENCHANTMENT_TABLE");
        int particleCount = plugin.getConfigManager().getInt("config.yml", "effects.enchant-apply.particle-count", 30);

        try {
            org.bukkit.Particle particle = org.bukkit.Particle.valueOf(particleName);
            player.spawnParticle(particle, player.getLocation().add(0, 1, 0), particleCount, 0.5, 0.5, 0.5, 0.1);
        } catch (Exception e) {
            // Ignore if particles don't work
        }
    }

    /**
     * Formats material names to be more readable
     */
    private String formatMaterialName(org.bukkit.Material material) {
        String name = material.name().toLowerCase().replace("_", " ");
        String[] words = name.split(" ");
        StringBuilder formatted = new StringBuilder();

        for (String word : words) {
            if (formatted.length() > 0) formatted.append(" ");
            formatted.append(word.substring(0, 1).toUpperCase()).append(word.substring(1));
        }

        return formatted.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!sender.hasPermission("mystenchants.enchant.use")) {
            return completions;
        }

        if (args.length == 1) {
            // Player names
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(player.getName());
                }
            }
        } else if (args.length == 2) {
            List<String> actions = Arrays.asList("unlock", "give", "remove", "setstat");
            if ("give".toLowerCase().startsWith(args[1].toLowerCase())) {
                completions.add("give");
            }
        } else if (args.length == 3) {
            if (args[1].equalsIgnoreCase("setstat")) {
                // Statistic names for setstat command
                List<String> stats = Arrays.asList("blocks_mined", "blocks_walked", "wheat_broken",
                        "creepers_killed", "iron_ingots_traded", "pants_crafted", "souls_collected");
                for (String stat : stats) {
                    if (stat.toLowerCase().startsWith(args[2].toLowerCase())) {
                        completions.add(stat);
                    }
                }
            } else {
                // Enchant names for other commands
                for (CustomEnchant enchant : plugin.getEnchantManager().getAllEnchants()) {
                    if (enchant.getName().toLowerCase().startsWith(args[2].toLowerCase())) {
                        completions.add(enchant.getName());
                    }
                }
            }
        } else if (args.length == 4) {
            // Levels
            CustomEnchant enchant = plugin.getEnchantManager().getEnchant(args[2]);
            if (enchant != null) {
                for (int i = 1; i <= enchant.getMaxLevel(); i++) {
                    completions.add(String.valueOf(i));
                }
            }
        }

        return completions;
    }
}