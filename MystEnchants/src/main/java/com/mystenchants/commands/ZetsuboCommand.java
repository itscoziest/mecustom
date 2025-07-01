package com.mystenchants.commands;

import com.mystenchants.MystEnchants;
import com.mystenchants.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class ZetsuboCommand implements CommandExecutor {

    private final MystEnchants plugin;

    public ZetsuboCommand(MystEnchants plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ColorUtils.color("&6&lZetsubo Sacrifice Commands:"));
            sender.sendMessage(ColorUtils.color("&e/zetsubo info [player] &7- Check sacrifice status"));
            sender.sendMessage(ColorUtils.color("&e/zetsubo reset <player> &7- Reset sacrifice status (admin)"));
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "info":
                return handleInfo(sender, args);
            case "reset":
                return handleReset(sender, args);
            default:
                sender.sendMessage(ColorUtils.color("&cUnknown subcommand: " + subCommand));
                return false;
        }
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        Player target;

        if (args.length == 1) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ColorUtils.color("&cYou must specify a player when using console!"));
                return true;
            }
            target = (Player) sender;
        } else {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ColorUtils.color("&cPlayer not found: " + args[1]));
                return true;
            }
        }

        boolean hasCompleted = plugin.getZetsuboSacrificeManager().hasCompletedSacrifice(target.getUniqueId());

        sender.sendMessage(ColorUtils.color("&6&lZetsubo Sacrifice Status for " + target.getName() + ":"));
        if (hasCompleted) {
            sender.sendMessage(ColorUtils.color("&a✓ Has completed the sacrifice ritual"));
            sender.sendMessage(ColorUtils.color("&7Can purchase Zetsubo enchants from Soul Shop"));
        } else {
            sender.sendMessage(ColorUtils.color("&c✗ Has not completed the sacrifice ritual"));
            sender.sendMessage(ColorUtils.color("&7Must enter the sacrifice region with required items"));
        }

        return true;
    }

    private boolean handleReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mystenchants.zetsubo.admin")) {
            sender.sendMessage(ColorUtils.color("&cYou don't have permission to use this command!"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ColorUtils.color("&cUsage: /zetsubo reset <player>"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            try {
                UUID targetUUID = Bukkit.getOfflinePlayer(args[1]).getUniqueId();
                plugin.getZetsuboSacrificeManager().resetPlayerSacrifice(targetUUID);
                sender.sendMessage(ColorUtils.color("&aReset sacrifice status for " + args[1]));
            } catch (Exception e) {
                sender.sendMessage(ColorUtils.color("&cPlayer not found: " + args[1]));
            }
            return true;
        }

        plugin.getZetsuboSacrificeManager().resetPlayerSacrifice(target.getUniqueId());
        sender.sendMessage(ColorUtils.color("&aReset sacrifice status for " + target.getName()));
        target.sendMessage(ColorUtils.color("&6Your Zetsubo sacrifice status has been reset by an admin."));

        return true;
    }
}