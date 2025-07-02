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

        if (args.length >= 4 && args[1].equalsIgnoreCase("setstat")) {
            Player target = Bukkit.getPlayer(args[0]); // args[0] is the player name
            if (target == null) {
                sender.sendMessage(ColorUtils.color(plugin.getConfigManager().getString("config.yml", "messages.player-not-found", "&cPlayer not found!")));
                return true;
            }

            String statName = args[2]; // args[2] is the statistic name
            plugin.getLogger().info("SETSTAT DEBUG - Player: " + target.getName() + ", Stat: " + statName + ", Raw Amount: " + args[3]);

            try {
                long amount = Long.parseLong(args[3]); // args[3] is the amount
                plugin.getLogger().info("SETSTAT DEBUG - Parsed amount: " + amount);

                plugin.getPlayerDataManager().setStatistic(target.getUniqueId(), statName, amount)
                        .thenRun(() -> {
                            sender.sendMessage(ColorUtils.color("&aSet " + statName + " to " + amount + " for " + target.getName()));
                            plugin.getLogger().info("SETSTAT DEBUG - Successfully set statistic");
                        })
                        .exceptionally(throwable -> {
                            sender.sendMessage(ColorUtils.color("&cError setting statistic: " + throwable.getMessage()));
                            plugin.getLogger().severe("SETSTAT ERROR: " + throwable.getMessage());
                            return null;
                        });

            } catch (NumberFormatException e) {
                sender.sendMessage(ColorUtils.color("&cInvalid amount!"));
                plugin.getLogger().warning("SETSTAT DEBUG - Invalid number format: " + args[3]);
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

                } else if (action.equalsIgnoreCase("give")) {
                    // NEW: Give physical enchant item with optional amount
                    if (level < 1 || level > enchant.getMaxLevel()) {
                        sender.sendMessage(ColorUtils.color("&cInvalid level! Must be between 1 and " + enchant.getMaxLevel()));
                        return true;
                    }

                    // Get amount (default to 1 if not specified)
                    int amount = 1;
                    if (args.length >= 5) {
                        try {
                            amount = Integer.parseInt(args[4]);
                            if (amount < 1) {
                                sender.sendMessage(ColorUtils.color("&cAmount must be at least 1!"));
                                return true;
                            }
                            if (amount > 64) {
                                sender.sendMessage(ColorUtils.color("&cAmount cannot exceed 64!"));
                                return true;
                            }
                        } catch (NumberFormatException e) {
                            sender.sendMessage(ColorUtils.color("&cInvalid amount! Must be a number."));
                            return true;
                        }
                    }

                    // Check if target has enough free inventory space
                    int freeSlots = 0;
                    for (ItemStack item : target.getInventory().getContents()) {
                        if (item == null) freeSlots++;
                    }

                    int slotsNeeded = (int) Math.ceil(amount / 64.0); // Each slot can hold up to 64 items
                    if (freeSlots < slotsNeeded) {
                        sender.sendMessage(ColorUtils.color("&c" + target.getName() + " doesn't have enough inventory space! Needs " + slotsNeeded + " slots, has " + freeSlots));
                        return true;
                    }

                    // Create and give enchant dye items
                    for (int i = 0; i < amount; i++) {
                        ItemStack enchantDye = plugin.getEnchantManager().createEnchantDye(enchant, level);
                        target.getInventory().addItem(enchantDye);
                    }

                    String amountText = amount == 1 ? "" : " x" + amount;
                    sender.sendMessage(ColorUtils.color("&aGave " + enchant.getDisplayName() + " Level " + level + " dye" + amountText + " to " + target.getName()));
                    target.sendMessage(ColorUtils.color("&aYou received " + enchant.getDisplayName() + " Level " + level + " enchant dye" + amountText + "!"));
                    target.sendMessage(ColorUtils.color("&7Drag and drop these onto compatible items to apply the enchant."));

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
                    sender.sendMessage(ColorUtils.color("&cInvalid action! Use 'unlock', 'give', or 'remove'"));
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

        sender.sendMessage(ColorUtils.color("&cUsage:"));
        sender.sendMessage(ColorUtils.color("&7/enchants [player] - Show player's enchants"));
        sender.sendMessage(ColorUtils.color("&7/enchants [player] unlock [enchant] [level] - Unlock enchant"));
        sender.sendMessage(ColorUtils.color("&7/enchants [player] give [enchant] [level] [amount] - Give enchant dye"));
        sender.sendMessage(ColorUtils.color("&7/enchants [player] remove [enchant] [level] - Remove enchant"));
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
            // Player names and debug
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(player.getName());
                }
            }
            if ("debug".startsWith(args[0].toLowerCase())) {
                completions.add("debug");
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("debug")) {
                if ("soulshop".startsWith(args[1].toLowerCase())) {
                    completions.add("soulshop");
                }
            } else {
                // Actions
                List<String> actions = Arrays.asList("unlock", "give", "remove", "setstat");
                for (String action : actions) {
                    if (action.toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(action);
                    }
                }
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
                // Enchant names
                for (CustomEnchant enchant : plugin.getEnchantManager().getAllEnchants()) {
                    if (enchant.getName().toLowerCase().startsWith(args[2].toLowerCase())) {
                        completions.add(enchant.getName());
                    }
                }
            }
        } else if (args.length == 4) {
            if (args[1].equalsIgnoreCase("setstat")) {
                // Amount for setstat command
                completions.add("0");
                completions.add("100");
                completions.add("1000");
            } else {
                // Levels
                CustomEnchant enchant = plugin.getEnchantManager().getEnchant(args[2]);
                if (enchant != null) {
                    for (int i = 1; i <= enchant.getMaxLevel(); i++) {
                        completions.add(String.valueOf(i));
                    }
                }
            }
        } else if (args.length == 5) {
            // Amount for give command
            if (args[1].equalsIgnoreCase("give")) {
                completions.add("1");
                completions.add("5");
                completions.add("10");
                completions.add("16");
                completions.add("32");
                completions.add("64");
            }
        }

        return completions;
    }
}