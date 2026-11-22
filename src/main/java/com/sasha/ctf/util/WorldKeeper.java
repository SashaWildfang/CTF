/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.util;

import com.sasha.ctf.FractalCTF;
import com.sasha.ctf.config.Messages;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerLoginEvent;

/**
 * Keeps every world on this server in daylight with clear skies and no natural mobs, and holds the
 * player cap: max-players from config.yml, with ctf.joinfull (donors and staff) always let in.
 */
public final class WorldKeeper implements Listener {

    private final FractalCTF plugin;

    public WorldKeeper(FractalCTF plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::keep, 20L, 200L);
    }

    private void keep() {
        for (World world : Bukkit.getWorlds()) {
            world.setGameRule(GameRules.SPAWN_MOBS, false);
            if (!plugin.ctfConfig().alwaysDay()) continue;
            world.setGameRule(GameRules.ADVANCE_TIME, false);
            world.setGameRule(GameRules.ADVANCE_WEATHER, false);
            try {
                if (Math.abs(world.getTime() - 6000) > 100) world.setTime(6000);
            } catch (IllegalArgumentException noClock) {
                // the nether and the end have no clock
            }
            if (world.hasStorm()) world.setStorm(false);
            if (world.isThundering()) world.setThundering(false);
        }
    }

    /** Natural spawns are off everywhere; plugin-spawned mobs (abilities) still work. */
    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        switch (event.getSpawnReason()) {
            case NATURAL, PATROL, REINFORCEMENTS, VILLAGE_DEFENSE, VILLAGE_INVASION, RAID, TRAP, JOCKEY, MOUNT -> event.setCancelled(true);
            default -> { }
        }
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGH)
    public void onLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();
        int max = plugin.ctfConfig().serverCap();
        if (Bukkit.getOnlinePlayers().size() < max) {
            if (event.getResult() == PlayerLoginEvent.Result.KICK_FULL) event.allow();
            return;
        }
        if (player.hasPermission("ctf.joinfull")) {
            event.allow();
            return;
        }
        event.disallow(PlayerLoginEvent.Result.KICK_FULL, plugin.messages().component("general.server-full", Messages.arg("max", max)));
    }
}
