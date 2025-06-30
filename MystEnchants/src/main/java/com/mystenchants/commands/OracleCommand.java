package com.mystenchants.commands;

import com.mystenchants.MystEnchants;
import com.mystenchants.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles the /oracle command for viewing and upgrading enchants
 */
public class OracleCommand implements CommandExecutor {

    private final MystEnchants plugin;

    public OracleCommand(MystEnchants plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ColorUtils.color("&cThis command can only be used by players!"));
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("mystenchants.oracle")) {
            player.sendMessage(ColorUtils.color(plugin.getConfigManager().getString("config.yml", "messages.no-permission", "&cYou don't have permission!")));
            return true;
        }

        player.openInventory(plugin.getGuiManager().createOracleGui(player));
        return true;
    }
}