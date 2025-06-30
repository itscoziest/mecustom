package com.mystenchants.commands;

import com.mystenchants.MystEnchants;
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
 * Handles the /perks command
 */
public class PerksCommand implements CommandExecutor, TabCompleter {

    private final MystEnchants plugin;

    public PerksCommand(MystEnchants plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            // Open perks GUI for player
            if (!(sender instanceof Player)) {
                sender.sendMessage(ColorUtils.color("&cThis command can only be used by players!"));
                return true;
            }

            Player player = (Player) sender;
            if (!player.hasPermission("mystenchants.perks")) {
                player.sendMessage(ColorUtils.color(plugin.getConfigManager().getString("config.yml", "messages.no-permission", "&cYou don't have permission!")));
                return true;
            }

            player.openInventory(plugin.getGuiManager().createPerksGui(player));
            return true;
        }

        // Admin commands
        if (!sender.hasPermission("mystenchants.admin")) {
            sender.sendMessage(ColorUtils.color(plugin.getConfigManager().getString("config.yml", "messages.no-permission", "&cYou don't have permission!")));
            return true;
        }

        if (args.length >= 4 && args[0].equalsIgnoreCase("give")) {
            String targetName = args[1];
            String perkName = args[2];

            Player target = Bukkit.getPlayer(targetName);
            if (target == null) {
                sender.sendMessage(ColorUtils.color(plugin.getConfigManager().getString("config.yml", "messages.player-not-found", "&cPlayer not found!")));
                return true;
            }

            try {
                int amount = Integer.parseInt(args[3]);
                if (amount <= 0) {
                    sender.sendMessage(ColorUtils.color("&cAmount must be greater than 0!"));
                    return true;
                }

                // Convert perk name to key format
                String perkKey = perkName.toLowerCase().replace(" ", "-");

                // Check if perk exists
                if (plugin.getPerkManager().getPerk(perkKey) == null) {
                    sender.sendMessage(ColorUtils.color("&cPerk '" + perkName + "' not found!"));
                    return true;
                }

                plugin.getPlayerDataManager().addPerk(target.getUniqueId(), perkKey, amount)
                        .thenRun(() -> {
                            // Give perk items to player
                            for (int i = 0; i < amount; i++) {
                                ItemStack perkItem = plugin.getPerkManager().createPerkItem(perkKey);
                                if (perkItem != null) {
                                    target.getInventory().addItem(perkItem);
                                }
                            }

                            String adminMessage = plugin.getConfigManager().getString("config.yml", "messages.perk-given", "&aGiven &6{amount}x {perk} &ato &6{player}&a!");
                            adminMessage = adminMessage.replace("{amount}", String.valueOf(amount))
                                    .replace("{perk}", perkName)
                                    .replace("{player}", target.getName());
                            sender.sendMessage(ColorUtils.color(adminMessage));

                            String playerMessage = plugin.getConfigManager().getString("config.yml", "messages.perk-received", "&aYou received &6{amount}x {perk}&a!");
                            playerMessage = playerMessage.replace("{amount}", String.valueOf(amount))
                                    .replace("{perk}", perkName);
                            target.sendMessage(ColorUtils.color(playerMessage));
                        });

            } catch (NumberFormatException e) {
                sender.sendMessage(ColorUtils.color("&cInvalid amount! Must be a number."));
                return true;
            }

            return true;
        }

        sender.sendMessage(ColorUtils.color("&cUsage: /perks [give] [player] [perk] [amount]"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!sender.hasPermission("mystenchants.admin")) {
            return completions;
        }

        if (args.length == 1) {
            if ("give".toLowerCase().startsWith(args[0].toLowerCase())) {
                completions.add("give");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            // Player names
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    completions.add(player.getName());
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            // Perk names
            List<String> perkNames = Arrays.asList("teleport-snowball", "grappling-hook", "snowman-egg",
                    "spellbreaker", "tradeoff-egg", "worthy-sacrifice", "lovestruck");
            for (String perkName : perkNames) {
                if (perkName.toLowerCase().startsWith(args[2].toLowerCase())) {
                    completions.add(perkName);
                }
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            // Amount suggestions
            completions.addAll(Arrays.asList("1", "5", "10"));
        }

        return completions;
    }
}