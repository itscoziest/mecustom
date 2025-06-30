package com.mystenchants.listeners;

import com.mystenchants.MystEnchants;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Handles player movement events for statistics tracking
 */
public class PlayerMoveListener implements Listener {

    private final MystEnchants plugin;

    public PlayerMoveListener(MystEnchants plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // Track player movement for statistics (Pace enchant unlock requirements)
        plugin.getStatisticManager().trackPlayerMovement(event.getPlayer());
    }
}