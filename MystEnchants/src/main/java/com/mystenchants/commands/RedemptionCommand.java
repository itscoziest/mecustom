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
 * Handles the /redemption command for boss fights
 */
public class RedemptionCommand implements CommandExecutor, TabCompleter {

    private final MystEnchants plugin;

    public RedemptionCommand(MystEnchants plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            // Open redemption GUI for player
            if (!(sender instanceof Player)) {
                sender.sendMessage(ColorUtils.color("&cThis command can only be used by players!"));
                return true;
            }

            Player player = (Player) sender;
            if (!player.hasPermission("mystenchants.redemption")) {
                player.sendMessage(ColorUtils.color(plugin.getConfigManager().getString("config.yml", "messages.no-permission", "&cYou don't have permission!")));
                return true;
            }

            // Check if spawn points are set
            if (!plugin.getRedemptionManager().areSpawnPointsSet()) {
                player.sendMessage(ColorUtils.color(plugin.getConfigManager().getString("config.yml", "messages.redemption-boss-spawn-not-set", "&cRedemption boss spawn point has not been set!")));
                return true;
            }

            // Check if player is on cooldown
            plugin.getPlayerDataManager().isOnRedemptionCooldown(player.getUniqueId())
                    .thenAccept(onCooldown -> {
                        if (onCooldown) {
                            plugin.getPlayerDataManager().getRemainingRedemptionCooldown(player.getUniqueId())
                                    .thenAccept(remaining -> {
                                        String message = plugin.getConfigManager().getString("config.yml", "messages.redemption-cooldown", "&cYou are on redemption cooldown for &6{time}&c!");
                                        message = message.replace("{time}", ColorUtils.formatTime(remaining));
                                        player.sendMessage(ColorUtils.color(message));
                                    });
                            return;
                        }

                        // Check if a redemption is already active
                        if (plugin.getRedemptionManager().isRedemptionActive()) {
                            player.sendMessage(ColorUtils.color(plugin.getConfigManager().getString("config.yml", "messages.redemption-active", "&cA redemption is already active!")));
                            return;
                        }

                        // Open confirmation GUI
                        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                            player.openInventory(plugin.getGuiManager().createRedemptionGui(player));
                        });
                    });

            return true;
        }

        String subCommand = args[0];

        if (subCommand.equalsIgnoreCase("spec")) {
            // Spectate redemption fight
            if (!(sender instanceof Player)) {
                sender.sendMessage(ColorUtils.color("&cThis command can only be used by players!"));
                return true;
            }

            Player player = (Player) sender;

            if (!plugin.getRedemptionManager().isRedemptionActive()) {
                player.sendMessage(ColorUtils.color("&cNo redemption fight is currently active!"));
                return true;
            }

            plugin.getRedemptionManager().addSpectator(player);
            return true;
        }

        if (subCommand.equalsIgnoreCase("admin")) {
            // Admin commands
            if (!sender.hasPermission("mystenchants.admin")) {
                sender.sendMessage(ColorUtils.color(plugin.getConfigManager().getString("config.yml", "messages.no-permission", "&cYou don't have permission!")));
                return true;
            }

            if (args.length < 2) {
                sender.sendMessage(ColorUtils.color("&cUsage: /redemption admin <bossSetSpawn|playerSetSpawn>"));
                return true;
            }

            String adminCommand = args[1];

            if (!(sender instanceof Player)) {
                sender.sendMessage(ColorUtils.color("&cThis command can only be used by players!"));
                return true;
            }

            Player player = (Player) sender;

            if (adminCommand.equalsIgnoreCase("bossSetSpawn")) {
                plugin.getRedemptionManager().setBossSpawnPoint(player.getLocation())
                        .thenRun(() -> {
                            player.sendMessage(ColorUtils.color(plugin.getConfigManager().getString("config.yml", "messages.redemption-boss-spawn-set", "&aRedemption boss spawn point set!")));
                        });
                return true;
            }

            if (adminCommand.equalsIgnoreCase("playerSetSpawn")) {
                plugin.getRedemptionManager().setPlayerSpawnPoint(player.getLocation())
                        .thenRun(() -> {
                            player.sendMessage(ColorUtils.color(plugin.getConfigManager().getString("config.yml", "messages.redemption-player-spawn-set", "&aRedemption player spawn point set!")));
                        });
                return true;
            }

            sender.sendMessage(ColorUtils.color("&cInvalid admin command! Use: bossSetSpawn, playerSetSpawn"));
            return true;
        }

        if (subCommand.equalsIgnoreCase("end")) {
            // Force end redemption (admin only)
            if (!sender.hasPermission("mystenchants.admin")) {
                sender.sendMessage(ColorUtils.color(plugin.getConfigManager().getString("config.yml", "messages.no-permission", "&cYou don't have permission!")));
                return true;
            }

            if (!plugin.getRedemptionManager().isRedemptionActive()) {
                sender.sendMessage(ColorUtils.color("&cNo redemption fight is currently active!"));
                return true;
            }

            plugin.getRedemptionManager().forceEndRedemption();
            sender.sendMessage(ColorUtils.color(plugin.getConfigManager().getString("config.yml", "messages.redemption-force-ended", "&cRedemption fight has been force ended!")));
            return true;
        }

        sender.sendMessage(ColorUtils.color("&cUsage: /redemption [spec|admin|end]"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("spec");
            if (sender.hasPermission("mystenchants.admin")) {
                subCommands = Arrays.asList("spec", "admin", "end");
            }

            for (String subCommand : subCommands) {
                if (subCommand.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(subCommand);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("mystenchants.admin")) {
            List<String> adminCommands = Arrays.asList("bossSetSpawn", "playerSetSpawn");
            for (String adminCommand : adminCommands) {
                if (adminCommand.toLowerCase().startsWith(args[1].toLowerCase())) {
                    completions.add(adminCommand);
                }
            }
        }

        return completions;
    }
}