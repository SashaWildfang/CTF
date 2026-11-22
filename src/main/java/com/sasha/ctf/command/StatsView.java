/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.command;

import com.sasha.ctf.FractalCTF;
import com.sasha.ctf.config.Messages;
import com.sasha.ctf.data.PlayerStats;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.sql.SQLException;
import java.util.Locale;
import java.util.logging.Level;

/** /ctfstats: a player's totals from the database. */
public final class StatsView {

    private StatsView() {}

    public static void show(FractalCTF plugin, CommandSender viewer, OfflinePlayer target) {
        if (plugin.database() == null) {
            plugin.messages().send(viewer, "general.no-database");
            return;
        }
        String name = target.getName() == null ? "?" : target.getName();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                PlayerStats stats = plugin.database().readStats(target.getUniqueId());
                Bukkit.getScheduler().runTask(plugin, () -> plugin.messages().send(viewer, "stats.show",
                        Messages.arg("player", name), Messages.arg("kills", stats.kills), Messages.arg("deaths", stats.deaths),
                        Messages.arg("kdr", String.format(Locale.ROOT, "%.2f", stats.kdr())), Messages.arg("captures", stats.captures),
                        Messages.arg("wins", stats.wins), Messages.arg("losses", stats.losses), Messages.arg("games", stats.games),
                        Messages.arg("assists", stats.assists), Messages.arg("coins", stats.coins)));
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Couldn't read stats", e);
                Bukkit.getScheduler().runTask(plugin, () -> plugin.messages().send(viewer, "general.no-database"));
            }
        });
    }
}
