package com.mystenchants.commands;

import com.mystenchants.MystEnchants;
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

/**
 * Handles the /souls command for managing soul currency
 */
public class SoulsCommand implements CommandExecutor, TabCompleter {

    private final MystEnchants plugin;

    public SoulsCommand(MystEnchants plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            // Show own souls
            if (!(sender instanceof Player)) {
                sender.sendMessage(ColorUtils.color("&cThis command can only be used by players!"));
                return true;
            }

            Player player = (Player) sender;
            plugin.getSoulManager().getSouls(player.getUniqueId())
                    .thenAccept(souls -> {
                        String message = plugin.getConfigManager().getString("config.yml", "messages.souls-balance", "&aYou have &6{souls} &asouls.");
                        message = message.replace("{souls}", String.valueOf(souls));
                        player.sendMessage(ColorUtils.color(message));
                    });
            return true;
        }

        if (args.length == 1) {
            // Show other player's souls (admin only)
            if (!sender.hasPermission("mystenchants.admin")) {
                sender.sendMessage(ColorUtils.color(plugin.getConfigManager().getString("config.yml", "messages.no-permission", "&cYou don't have permission!")));
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ColorUtils.color(plugin.getConfigManager().getString("config.yml", "messages.player-not-found", "&cPlayer not found!")));
                return true;
            }

            plugin.getSoulManager().getSouls(target.getUniqueId())
                    .thenAccept(souls -> {
                        String message = plugin.getConfigManager().getString("config.yml", "messages.souls-balance-other", "&a{player} has &6{souls} &asouls.");
                        message = message.replace("{player}", target.getName()).replace("{souls}", String.valueOf(souls));
                        sender.sendMessage(ColorUtils.color(message));
                    });
            return true;
        }

        if (args.length == 3) {
            // Admin commands: give/take souls
            if (!sender.hasPermission("mystenchants.admin")) {
                sender.sendMessage(ColorUtils.color(plugin.getConfigManager().getString("config.yml", "messages.no-permission", "&cYou don't have permission!")));
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ColorUtils.color(plugin.getConfigManager().getString("config.yml", "messages.player-not-found", "&cPlayer not found!")));
                return true;
            }

            String action = args[1];

            try {
                long amount = Long.parseLong(args[2]);

                if (amount <= 0) {
                    sender.sendMessage(ColorUtils.color("&cAmount must be greater than 0!"));
                    return true;
                }

                if (action.equalsIgnoreCase("give")) {
                    plugin.getSoulManager().addSouls(target.getUniqueId(), amount)
                            .thenRun(() -> {
                                String adminMessage = plugin.getConfigManager().getString("config.yml", "messages.souls-given", "&aGiven &6{amount} &asouls to &6{player}&a.");
                                adminMessage = adminMessage.replace("{amount}", String.valueOf(amount)).replace("{player}", target.getName());
                                sender.sendMessage(ColorUtils.color(adminMessage));

                                String playerMessage = plugin.getConfigManager().getString("config.yml", "messages.souls-received", "&aYou received &6{amount} &asouls!");
                                playerMessage = playerMessage.replace("{amount}", String.valueOf(amount));
                                target.sendMessage(ColorUtils.color(playerMessage));
                            });

                } else if (action.equalsIgnoreCase("take")) {
                    plugin.getSoulManager().removeSouls(target.getUniqueId(), amount)
                            .thenAccept(success -> {
                                if (success) {
                                    String adminMessage = plugin.getConfigManager().getString("config.yml", "messages.souls-taken", "&aTaken &6{amount} &asouls from &6{player}&a.");
                                    adminMessage = adminMessage.replace("{amount}", String.valueOf(amount)).replace("{player}", target.getName());
                                    sender.sendMessage(ColorUtils.color(adminMessage));

                                    String playerMessage = plugin.getConfigManager().getString("config.yml", "messages.souls-lost", "&cYou lost &6{amount} &asouls!");
                                    playerMessage = playerMessage.replace("{amount}", String.valueOf(amount));
                                    target.sendMessage(ColorUtils.color(playerMessage));
                                } else {
                                    sender.sendMessage(ColorUtils.color("&c" + target.getName() + " doesn't have enough souls!"));
                                }
                            });

                } else {
                    sender.sendMessage(ColorUtils.color("&cInvalid action! Use 'give' or 'take'"));
                    return true;
                }

            } catch (NumberFormatException e) {
                sender.sendMessage(ColorUtils.color("&cInvalid amount! Must be a number."));
                return true;
            }

            return true;
        }

        sender.sendMessage(ColorUtils.color("&cUsage: /souls [player] [give/take] [amount]"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Player names (admin only)
            if (sender.hasPermission("mystenchants.admin")) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                        completions.add(player.getName());
                    }
                }
            }
        } else if (args.length == 2 && sender.hasPermission("mystenchants.admin")) {
            // Actions
            List<String> actions = Arrays.asList("give", "take");
            for (String action : actions) {
                if (action.toLowerCase().startsWith(args[1].toLowerCase())) {
                    completions.add(action);
                }
            }
        } else if (args.length == 3 && sender.hasPermission("mystenchants.admin")) {
            // Amount suggestions
            completions.addAll(Arrays.asList("1", "10", "100", "1000"));
        }

        return completions;
    }
}