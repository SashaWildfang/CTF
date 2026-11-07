package com.bearnation.ctf.tablist;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class TablistListenerCTF implements Listener {

    private final JavaPlugin plugin;
    private final TablistManager tabs;
    private final TablistGridManager grid;

    public TablistListenerCTF(JavaPlugin plugin, TablistManager manager, TablistGridManager grid) {
        this.plugin = plugin;
        this.tabs = manager;
        this.grid = grid;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        tabs.applyHeaderFooter(e.getPlayer());
        tabs.setRole(e.getPlayer(), TablistManager.Role.SPECTATOR); // default until assigned
        grid.enableFor(e.getPlayer());

        // Top off everyone’s grid next tick so counts are correct
        Bukkit.getScheduler().runTask(plugin, grid::rebuildAll);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        grid.disableFor(e.getPlayer());
        // Re-top-off remaining viewers next tick
        Bukkit.getScheduler().runTask(plugin, grid::rebuildAll);
    }
}
