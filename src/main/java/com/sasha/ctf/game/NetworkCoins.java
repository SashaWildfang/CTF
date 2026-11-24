/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.game;

import com.sasha.ctf.FractalCTF;
import com.sasha.ctf.config.Messages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.sql.SQLException;
import java.util.logging.Level;

/**
 * Pays CTF coins waiting in the network database (network level rewards claimed in the Lobby) when a player joins
 * either CTF server.
 */
public final class NetworkCoins implements Listener {

    private final FractalCTF plugin;

    public NetworkCoins(FractalCTF plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.network() == null || plugin.database() == null) return;
        String game = plugin.ctfConfig().hubServer().toLowerCase(java.util.Locale.ROOT);
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            try {
                int amount = plugin.network().takePendingCoins(player.getUniqueId(), game);
                if (amount <= 0) return;
                int balance = plugin.database().addCoins(player.getUniqueId(), player.getName(), amount);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.kits().setCoins(player.getUniqueId(), balance);
                    if (player.isOnline()) plugin.messages().send(player, "network-xp.coins-arrived", Messages.arg("coins", amount),
                            Messages.arg("balance", com.sasha.ctf.util.Numbers.coins(balance)));
                });
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Couldn't pay the level reward coins for " + player.getName(), e);
            }
        }, 40L);
    }
}
