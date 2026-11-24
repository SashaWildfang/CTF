/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.kit;

import com.sasha.ctf.FractalCTF;
import com.sasha.ctf.config.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Coin boosters: while one is running, everyone earns its multiple of the coins a match gives. They are for the
 * whole of CTF, so they live in a shared database: each is written with the time it starts and ends, and a new one
 * is put after the last, so two together run one after the other. Both CTF servers read the list every few seconds.
 *
 * <p>With the network database (config.yml network-database:) they are network boosters: players buy them into their
 * booster inventory in the store and activate them from My Profile in the Lobby, which shows the running one in the
 * realm selector; the game name is servers.hub ("ctf"). Any left in the CTF database are moved over on start.
 * Without it, they stay in the CTF database (the store runs /coinbooster add from the console, as before).
 */
public final class Boosters {

    /** One booster: when it runs (milliseconds), what it multiplies coins by, and who bought it. */
    public record Booster(long start, long end, int multiplier, String buyer) {
        long minutesLeft(long now) { return Math.max(1, (end - Math.max(now, start) + 59_999) / 60_000); }
    }

    private static final String SETTING = "boosters";

    private final FractalCTF plugin;
    private volatile List<Booster> boosters = List.of();
    /** The booster players here were last told about. */
    private Booster told;
    private boolean first = true;

    public Boosters(FractalCTF plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (plugin.network() != null) Bukkit.getScheduler().runTaskAsynchronously(plugin, this::moveToNetwork);
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::refresh, 40L, 100L);
    }

    /** The game boosters are for in the network database: the hub's server name, as the Lobby's realm selector has it. */
    private String game() {
        return plugin.ctfConfig().hubServer().toLowerCase(java.util.Locale.ROOT);
    }

    private static List<Booster> fromNetwork(List<com.sasha.ctf.data.NetworkLink.Booster> rows) {
        List<Booster> list = new ArrayList<>();
        for (var row : rows) list.add(new Booster(row.start(), row.end(), (int) Math.max(1, Math.round(row.multiplier())), row.owner()));
        return list;
    }

    /** Boosters from before the network database (in the CTF database) are moved over, so none are lost. */
    private void moveToNetwork() {
        if (plugin.database() == null) return;
        try {
            List<Booster> old = parse(plugin.database().setting(SETTING));
            if (old.isEmpty()) return;
            for (Booster booster : old) {
                plugin.network().putBooster(game(), new com.sasha.ctf.data.NetworkLink.Booster(booster.start(), booster.end(),
                        booster.multiplier(), booster.buyer()));
            }
            plugin.database().setting(SETTING, null);
            plugin.getLogger().info("Moved " + old.size() + " coin booster(s) to the network database.");
        } catch (SQLException e) {
            plugin.getLogger().warning("Couldn't move the coin boosters to the network database: " + e.getMessage());
        }
    }

    /** The booster running now, or null. */
    public Booster active() {
        long now = System.currentTimeMillis();
        for (Booster booster : boosters) if (booster.start() <= now && now < booster.end()) return booster;
        return null;
    }

    /** What coins are multiplied by right now (1 with no booster). */
    public int multiplier() {
        Booster active = active();
        return active == null ? 1 : Math.max(1, active.multiplier());
    }

    /** Minutes left of the booster running now (0 with none). */
    public long minutesLeft() {
        Booster active = active();
        return active == null ? 0 : active.minutesLeft(System.currentTimeMillis());
    }

    private static List<Booster> parse(String text) {
        List<Booster> list = new ArrayList<>();
        if (text == null || text.isBlank()) return list;
        long now = System.currentTimeMillis();
        for (String entry : text.split(";")) {
            String[] part = entry.split(",", 4);
            try {
                Booster booster = new Booster(Long.parseLong(part[0]), Long.parseLong(part[1]), Integer.parseInt(part[2]), part.length > 3 ? part[3] : "");
                if (booster.end() > now) list.add(booster);          // the ones that are over drop out here
            } catch (RuntimeException ignored) {
                // an entry that isn't four fields is left out
            }
        }
        return list;
    }

    private static String write(List<Booster> list) {
        StringBuilder out = new StringBuilder();
        for (Booster booster : list) {
            if (!out.isEmpty()) out.append(';');
            out.append(booster.start()).append(',').append(booster.end()).append(',').append(booster.multiplier()).append(',').append(booster.buyer());
        }
        return out.toString();
    }

    /** Reads the list again (off the main thread) and tells the players here when a booster starts or ends. */
    private void refresh() {
        if (plugin.database() == null && plugin.network() == null) return;
        try {
            boosters = plugin.network() != null ? fromNetwork(plugin.network().boosters(game())) : parse(plugin.database().setting(SETTING));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.FINE, "Couldn't read the coin boosters", e);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, this::announce);
    }

    private void announce() {
        Booster active = active();
        boolean same = active == null ? told == null : active.equals(told);
        if (same) {
            first = false;
            return;
        }
        Messages messages = plugin.messages();
        if (active != null) {
            // One that was already running when this server started is not announced as new
            // with the network database, Core on the proxy tells the whole network (with a click to tip)
            if (plugin.network() == null && (!first || System.currentTimeMillis() - active.start() < 30_000)) {
                messages.broadcast("booster.started", Messages.arg("player", active.buyer().isEmpty() ? "Someone" : active.buyer()),
                        Messages.arg("multiplier", active.multiplier()), Messages.arg("minutes", active.minutesLeft(System.currentTimeMillis())));
                for (Player player : Bukkit.getOnlinePlayers()) player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
            }
        } else {
            messages.broadcast("booster.ended");
        }
        told = active;
        first = false;
    }

    /** What a player is told about the booster when they ask, or arrive while one runs. */
    public void tell(CommandSender to, boolean onlyIfActive) {
        Booster active = active();
        Messages messages = plugin.messages();
        if (active == null) {
            if (!onlyIfActive) messages.send(to, "booster.none");
            return;
        }
        long now = System.currentTimeMillis();
        int waiting = 0;
        for (Booster booster : boosters) if (booster.start() > now) waiting++;
        messages.send(to, "booster.status", Messages.arg("player", active.buyer().isEmpty() ? "Someone" : active.buyer()),
                Messages.arg("multiplier", active.multiplier()), Messages.arg("minutes", active.minutesLeft(now)));
        if (waiting > 0 && !onlyIfActive) messages.send(to, "booster.queued", Messages.arg("count", waiting));
    }

    /** /coinbooster add: a new booster after any that are running or waiting. */
    public void add(CommandSender sender, String buyer, int minutes, int multiplier) {
        if (plugin.database() == null && plugin.network() == null) {
            plugin.messages().send(sender, "general.no-database");
            return;
        }
        String name = buyer.replaceAll("[,;\\s]", "");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<Booster> list;
                if (plugin.network() != null) {
                    plugin.network().addBooster(game(), multiplier, minutes, name, null, 0);
                    list = fromNetwork(plugin.network().boosters(game()));
                } else {
                    list = parse(plugin.database().setting(SETTING));
                    long start = System.currentTimeMillis();
                    for (Booster booster : list) start = Math.max(start, booster.end());
                    list.add(new Booster(start, start + minutes * 60_000L, multiplier, name));
                    plugin.database().setting(SETTING, write(list));
                }
                boosters = list;
                boolean now = list.size() == 1;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.messages().send(sender, now ? "booster.added-now" : "booster.added-queued", Messages.arg("player", name),
                            Messages.arg("multiplier", multiplier), Messages.arg("minutes", minutes), Messages.arg("count", list.size() - 1));
                    announce();
                });
            } catch (SQLException e) {
                Bukkit.getScheduler().runTask(plugin, () -> plugin.messages().send(sender, "general.no-database"));
            }
        });
    }

    /** /coinbooster clear: every booster, running or waiting, is ended. */
    public void clear(CommandSender sender) {
        if (plugin.database() == null && plugin.network() == null) {
            plugin.messages().send(sender, "general.no-database");
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (plugin.network() != null) plugin.network().clearBoosters(game());
                if (plugin.database() != null) plugin.database().setting(SETTING, null);
                boosters = List.of();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.messages().send(sender, "booster.cleared");
                    announce();
                });
            } catch (SQLException e) {
                Bukkit.getScheduler().runTask(plugin, () -> plugin.messages().send(sender, "general.no-database"));
            }
        });
    }
}
