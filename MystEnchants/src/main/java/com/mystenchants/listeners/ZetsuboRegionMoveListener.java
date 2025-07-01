package com.mystenchants.listeners;

import com.mystenchants.MystEnchants;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ZetsuboRegionMoveListener implements Listener {

    private final MystEnchants plugin;
    private final String sacrificeRegionName;
    private final Map<UUID, Boolean> playerInRegion = new HashMap<>();

    public ZetsuboRegionMoveListener(MystEnchants plugin) {
        this.plugin = plugin;
        this.sacrificeRegionName = "zetsubo_sacrifice";
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockY() == event.getTo().getBlockY() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        boolean wasInRegion = playerInRegion.getOrDefault(playerId, false);
        boolean isInRegion = isInSacrificeRegion(event.getTo());

        playerInRegion.put(playerId, isInRegion);

        if (!wasInRegion && isInRegion) {
            plugin.getZetsuboSacrificeManager().onPlayerEnterSacrificeRegion(player, sacrificeRegionName);
        } else if (wasInRegion && !isInRegion) {
            plugin.getZetsuboSacrificeManager().onPlayerLeaveRegion(player, event.getFrom());
        }
    }

    private boolean isInSacrificeRegion(org.bukkit.Location location) {
        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionQuery query = container.createQuery();

            ApplicableRegionSet regions = query.getApplicableRegions(BukkitAdapter.adapt(location));

            for (ProtectedRegion region : regions) {
                if (region.getId().equals(sacrificeRegionName)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            plugin.getLogger().warning("Error checking WorldGuard region: " + e.getMessage());
            return false;
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerInRegion.remove(event.getPlayer().getUniqueId());
    }
}