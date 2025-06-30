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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Handles the /enchants command and its sub-commands
 */
public class EnchantsCommand implements CommandExecutor, TabCompleter {

    private final MystEnchants plugin;

    public EnchantsCommand(MystEnchants plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            // Open enchants GUI for player
            if (!(sender instanceof Player)) {
                sender.sendMessage(ColorUtils.color("&cThis command can only be used by players!"));
                return true;
            }

            Player player = (Player) sender;
            if (!player.hasPermission("mystenchants.enchants")) {
                player.sendMessage(ColorUtils.color(plugin.getConfigManager().getString("config.yml", "messages.no-permission", "&cYou don't have permission!")));
                return true;
            }

            player.openInventory(plugin.getGuiManager().createEnchantsGui(player));
            return true;
        }

        // Admin commands
        if (!sender.hasPermission("mystenchants.admin")) {
            sender.sendMessage(ColorUtils.color(plugin.getConfigManager().getString("config.yml", "messages.no-permission", "&cYou don't have permission!")));
            return true;
        }

        if (args.length == 5 && args[0].equalsIgnoreCase("setstat")) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ColorUtils.color(plugin.getConfigManager().getString("config.yml", "messages.player-not-found", "&cPlayer not found!")));
                return true;
            }

            String statName = args[2];
            try {
                long amount = Long.parseLong(args[3]);

                plugin.getPlayerDataManager().setStatistic(target.getUniqueId(), statName, amount)
                        .thenRun(() -> {
                            sender.sendMessage(ColorUtils.color("&aSet " + statName + " to " + amount + " for " + target.getName()));
                        });

            } catch (NumberFormatException e) {
                sender.sendMessage(ColorUtils.color("&cInvalid amount!"));
            }

            return true;
        }

        if (args.length == 1) {
            // /enchants <player> - show player's enchants
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ColorUtils.color(plugin.getConfigManager().getString("config.yml", "messages.player-not-found", "&cPlayer not found!")));
                return true;
            }

            showPlayerEnchants(sender, target);
            return true;
        }

        if (args.length >= 4) {
            String targetName = args[0];
            String action = args[1];
            String enchantName = args[2];

            Player target = Bukkit.getPlayer(targetName);
            if (target == null) {
                sender.sendMessage(ColorUtils.color(plugin.getConfigManager().getString("config.yml", "messages.player-not-found", "&cPlayer not found!")));
                return true;
            }

            CustomEnchant enchant = plugin.getEnchantManager().getEnchant(enchantName);
            if (enchant == null) {
                sender.sendMessage(ColorUtils.color("&cEnchant '" + enchantName + "' not found!"));
                return true;
            }

            try {
                int level = Integer.parseInt(args[3]);

                if (action.equalsIgnoreCase("unlock")) {
                    if (level < 1 || level > enchant.getMaxLevel()) {
                        sender.sendMessage(ColorUtils.color("&cInvalid level! Must be between 1 and " + enchant.getMaxLevel()));
                        return true;
                    }

                    // Check if player already has this level or higher
                    plugin.getPlayerDataManager().getEnchantLevel(target.getUniqueId(), enchantName)
                            .thenAccept(currentLevel -> {
                                if (currentLevel >= level) {
                                    sender.sendMessage(ColorUtils.color("&c" + target.getName() + " already has " + enchant.getDisplayName() + " Level " + currentLevel + " or higher!"));
                                    return;
                                }

                                plugin.getPlayerDataManager().unlockEnchant(target.getUniqueId(), enchantName, level)
                                        .thenRun(() -> {
                                            sender.sendMessage(ColorUtils.color("&aUnlocked " + enchant.getDisplayName() + " Level " + level + " for " + target.getName()));
                                            target.sendMessage(ColorUtils.color(plugin.getConfigManager().getString("config.yml", "messages.enchant-unlocked", "&aYou have unlocked &6{enchant} Level {level}&a!")
                                                    .replace("{enchant}", enchant.getDisplayName())
                                                    .replace("{level}", String.valueOf(level))));
                                        });
                            });

                } else if (action.equalsIgnoreCase("remove")) {
                    if (level == 0) {
                        plugin.getPlayerDataManager().removeEnchant(target.getUniqueId(), enchantName)
                                .thenRun(() -> {
                                    sender.sendMessage(ColorUtils.color("&aRemoved " + enchant.getDisplayName() + " from " + target.getName()));
                                });
                    } else {
                        plugin.getPlayerDataManager().setEnchantLevel(target.getUniqueId(), enchantName, level - 1)
                                .thenRun(() -> {
                                    sender.sendMessage(ColorUtils.color("&aSet " + enchant.getDisplayName() + " to Level " + (level - 1) + " for " + target.getName()));
                                });
                    }
                } else {
                    sender.sendMessage(ColorUtils.color("&cInvalid action! Use 'unlock' or 'remove'"));
                    return true;
                }

            } catch (NumberFormatException e) {
                sender.sendMessage(ColorUtils.color("&cInvalid level number!"));
                return true;
            }

            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("debug") && args[1].equalsIgnoreCase("soulshop")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ColorUtils.color("&cThis command can only be used by players!"));
                return true;
            }

            Player player = (Player) sender;
            plugin.getGuiManager().debugSoulShopAvailability(player);
            player.sendMessage(ColorUtils.color("&aDebug information printed to console!"));
            return true;
        }

        sender.sendMessage(ColorUtils.color("&cUsage: /enchants [player] [unlock/remove] [enchant] [level]"));
        return true;
    }



    private void showPlayerEnchants(CommandSender sender, Player target) {
        plugin.getPlayerDataManager().getPlayerEnchants(target.getUniqueId())
                .thenAccept(enchants -> {
                    sender.sendMessage(ColorUtils.color("&6&l" + target.getName() + "'s Enchants:"));

                    if (enchants.isEmpty()) {
                        sender.sendMessage(ColorUtils.color("&7No enchants unlocked."));
                        return;
                    }

                    for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
                        CustomEnchant enchant = plugin.getEnchantManager().getEnchant(entry.getKey());
                        if (enchant != null) {
                            sender.sendMessage(ColorUtils.color("&7- " + enchant.getTier().getColor() + enchant.getDisplayName() + " &7Level &f" + entry.getValue()));
                        }
                    }
                });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!sender.hasPermission("mystenchants.admin")) {
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
            // Actions
            List<String> actions = Arrays.asList("unlock", "remove");
            for (String action : actions) {
                if (action.toLowerCase().startsWith(args[1].toLowerCase())) {
                    completions.add(action);
                }
            }
        } else if (args.length == 3) {
            // Enchant names
            for (CustomEnchant enchant : plugin.getEnchantManager().getAllEnchants()) {
                if (enchant.getName().toLowerCase().startsWith(args[2].toLowerCase())) {
                    completions.add(enchant.getName());
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