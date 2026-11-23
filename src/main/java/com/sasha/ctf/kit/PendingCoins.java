/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.kit;

import com.sasha.ctf.FractalCTF;
import com.sasha.ctf.config.Messages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Coins bought in the store for someone who has never been on CTF: /coinsadmin add can't find them yet, so the
 * coins wait here (in the shared database, by name) and are paid the first time they join either CTF server.
 */
public final class PendingCoins implements Listener {

    private static final String SETTING = "pending-coins";
    private final FractalCTF plugin;

    public PendingCoins(FractalCTF plugin) {
        this.plugin = plugin;
    }

    /** Keeps {@code amount} coins for {@code name} until they join. Call off the main thread. */
    public void hold(String name, int amount) throws SQLException {
        synchronized (PendingCoins.class) {
            Map<String, Integer> all = read();
            all.merge(name.toLowerCase(Locale.ROOT), amount, Integer::sum);
            write(all);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.database() == null) return;
        String key = player.getName().toLowerCase(Locale.ROOT);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int amount;
                synchronized (PendingCoins.class) {
                    Map<String, Integer> all = read();
                    Integer waiting = all.remove(key);
                    if (waiting == null || waiting <= 0) return;
                    write(all);
                    amount = waiting;
                }
                int balance = plugin.database().addCoins(player.getUniqueId(), player.getName(), amount);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.kits().setCoins(player.getUniqueId(), balance);
                    if (player.isOnline()) plugin.messages().send(player, "coins.received", Messages.arg("coins", amount),
                            Messages.arg("balance", com.sasha.ctf.util.Numbers.coins(balance)));
                });
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Couldn't pay the coins waiting for " + player.getName(), e);
            }
        });
    }

    private Map<String, Integer> read() throws SQLException {
        Map<String, Integer> all = new LinkedHashMap<>();
        String text = plugin.database().setting(SETTING);
        if (text == null || text.isBlank()) return all;
        for (String part : text.split(";")) {
            int at = part.lastIndexOf(':');
            if (at <= 0) continue;
            try {
                all.put(part.substring(0, at), Integer.parseInt(part.substring(at + 1)));
            } catch (NumberFormatException ignored) {
                // a damaged entry is dropped
            }
        }
        return all;
    }

    private void write(Map<String, Integer> all) throws SQLException {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, Integer> entry : all.entrySet()) {
            if (out.length() > 0) out.append(';');
            out.append(entry.getKey()).append(':').append(entry.getValue());
        }
        plugin.database().setting(SETTING, out.length() == 0 ? null : out.toString());
    }
}
