package com.mystenchants.listeners;

import com.mystenchants.MystEnchants;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Handles player join events
 */
public class PlayerJoinListener implements Listener {

    private final MystEnchants plugin;

    public PlayerJoinListener(MystEnchants plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Create player data if it doesn't exist
        plugin.getPlayerDataManager().createPlayerData(event.getPlayer());
    }
}