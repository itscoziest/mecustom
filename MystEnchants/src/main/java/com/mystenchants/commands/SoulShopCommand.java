package com.mystenchants.commands;

import com.mystenchants.MystEnchants;
import com.mystenchants.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles the /soulshop command for opening the soul shop GUI
 */
public class SoulShopCommand implements CommandExecutor {

    private final MystEnchants plugin;

    public SoulShopCommand(MystEnchants plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ColorUtils.color("&cThis command can only be used by players!"));
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("mystenchants.soulshop")) {
            player.sendMessage(ColorUtils.color(plugin.getConfigManager().getString("config.yml", "messages.no-permission", "&cYou don't have permission!")));
            return true;
        }

        player.openInventory(plugin.getGuiManager().createSoulShopGui(player));
        return true;
    }
}