package com.mystenchants.listeners;

import com.mystenchants.MystEnchants;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * ENHANCED: Handles player quit events with redemption fight cleanup
 */
public class PlayerQuitListener implements Listener {

    private final MystEnchants plugin;

    public PlayerQuitListener(MystEnchants plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // ADDED: Handle redemption fight disconnection
        plugin.getRedemptionManager().handlePlayerDisconnect(event.getPlayer());

        // Update last seen
        plugin.getPlayerDataManager().updateLastSeen(event.getPlayer().getUniqueId());

        // Clean up statistic tracking
        plugin.getStatisticManager().cleanupPlayer(event.getPlayer());

        // Clean up snowman data
        plugin.getSnowmanManager().cleanupPlayerSnowmen(event.getPlayer());

        // Clean up perk combat tracking
        plugin.getPerkCombatListener().cleanupPlayer(event.getPlayer());

        // ADDED: Clean up backup golems
        plugin.getBackupEnchantListener().cleanupPlayer(event.getPlayer());

        // Remove from redemption spectators if spectating
        plugin.getRedemptionManager().removeSpectator(event.getPlayer());

        plugin.getLogger().info("Cleaned up data for player: " + event.getPlayer().getName());
    }
}