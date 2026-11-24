/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.ConfigurationSection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * The network database, shared with the Lobby (FractalLobby makes it on the Lobby server): network boosters
 * (players activate them in the Lobby, they run here) and network XP (earned here, shown in the Lobby).
 * Optional: without it, boosters stay in the CTF database as before and no XP is given.
 */
public final class NetworkLink implements AutoCloseable {

    /** A booster running or waiting: when (milliseconds), what it multiplies coins by, and who activated it. */
    public record Booster(long start, long end, double multiplier, String owner) {}

    private final HikariDataSource pool;

    private NetworkLink(HikariDataSource pool) {
        this.pool = pool;
    }

    /** Connects with config.yml network-database:, or null when it isn't filled in (or can't connect). */
    public static NetworkLink open(ConfigurationSection c, Logger logger) {
        if (c == null || !c.getBoolean("enabled", true)) return null;
        String name = c.getString("name", ""), user = c.getString("user", "");
        if (name.isBlank() || user.isBlank()) return null;
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + c.getString("host", "172.18.0.1") + ":" + c.getInt("port", 3306) + "/" + name
                + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8");
        config.setUsername(user);
        config.setPassword(c.getString("password", ""));
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setMaximumPoolSize(3);
        config.setMinimumIdle(1);
        config.setPoolName("FractalCTF-network");
        config.setConnectionTimeout(5000);
        try {
            NetworkLink link = new NetworkLink(new HikariDataSource(config));
            link.createTables();
            logger.info("Connected to the network database (boosters and network XP).");
            return link;
        } catch (Exception e) {
            logger.severe("Couldn't connect to the network database (" + e.getMessage() + "): no network XP, boosters from the CTF database.");
            return null;
        }
    }

    /** The same tables FractalLobby makes, so either can start first. */
    private void createTables() throws SQLException {
        try (Connection c = pool.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS network_players (uuid CHAR(36) PRIMARY KEY, name VARCHAR(32) NOT NULL,"
                    + " xp BIGINT NOT NULL DEFAULT 0, updated BIGINT NOT NULL)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS network_boosters (id BIGINT AUTO_INCREMENT PRIMARY KEY, game VARCHAR(16) NOT NULL,"
                    + " multiplier DOUBLE NOT NULL, starts BIGINT NOT NULL, ends BIGINT NOT NULL, owner_uuid CHAR(36),"
                    + " owner_name VARCHAR(32) NOT NULL, created BIGINT NOT NULL, INDEX (game, ends))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS network_pending_coins (id BIGINT AUTO_INCREMENT PRIMARY KEY, uuid CHAR(36) NOT NULL,"
                    + " name VARCHAR(32) NOT NULL, game VARCHAR(16) NOT NULL, coins INT NOT NULL, source VARCHAR(64), created BIGINT NOT NULL,"
                    + " INDEX (uuid))");
        }
    }

    /**
     * Takes the CTF coins waiting for this player (network level rewards from the Lobby): they're deleted here in
     * the same transaction, so two servers can't both pay them. Returns how many.
     */
    public int takePendingCoins(UUID player, String game) throws SQLException {
        try (Connection c = pool.getConnection()) {
            c.setAutoCommit(false);
            try {
                int total = 0;
                List<Long> ids = new ArrayList<>();
                try (PreparedStatement s = c.prepareStatement("SELECT id, coins FROM network_pending_coins WHERE uuid = ? AND game = ? FOR UPDATE")) {
                    s.setString(1, player.toString());
                    s.setString(2, game);
                    try (ResultSet r = s.executeQuery()) {
                        while (r.next()) {
                            ids.add(r.getLong(1));
                            total += r.getInt(2);
                        }
                    }
                }
                try (PreparedStatement d = c.prepareStatement("DELETE FROM network_pending_coins WHERE id = ?")) {
                    for (long id : ids) {
                        d.setLong(1, id);
                        d.addBatch();
                    }
                    if (!ids.isEmpty()) d.executeBatch();
                }
                c.commit();
                return total;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /** A player's network XP (0 with none). */
    public long xp(UUID player) throws SQLException {
        try (Connection c = pool.getConnection(); PreparedStatement s = c.prepareStatement("SELECT xp FROM network_players WHERE uuid = ?")) {
            s.setString(1, player.toString());
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? r.getLong(1) : 0;
            }
        }
    }

    /** Boosters running or waiting on this game, first to run first. */
    public List<Booster> boosters(String game) throws SQLException {
        List<Booster> list = new ArrayList<>();
        try (Connection c = pool.getConnection(); PreparedStatement s = c.prepareStatement(
                "SELECT starts, ends, multiplier, owner_name FROM network_boosters WHERE game = ? AND ends > ? ORDER BY starts")) {
            s.setString(1, game);
            s.setLong(2, System.currentTimeMillis());
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) list.add(new Booster(r.getLong(1), r.getLong(2), r.getDouble(3), r.getString(4)));
            }
        }
        return list;
    }

    /** Adds a booster after any running or waiting on this game; returns when it starts. */
    public long addBooster(String game, double multiplier, int minutes, String owner, UUID ownerId, long notBefore) throws SQLException {
        try (Connection c = pool.getConnection()) {
            c.setAutoCommit(false);
            try {
                long start = Math.max(System.currentTimeMillis(), notBefore);
                try (PreparedStatement last = c.prepareStatement("SELECT MAX(ends) FROM network_boosters WHERE game = ? FOR UPDATE")) {
                    last.setString(1, game);
                    try (ResultSet r = last.executeQuery()) {
                        if (r.next()) start = Math.max(start, r.getLong(1));
                    }
                }
                insert(c, game, multiplier, start, start + minutes * 60_000L, owner, ownerId);
                c.commit();
                return start;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /** Puts a booster in as it is (moving the old CTF-database list over). */
    public void putBooster(String game, Booster booster) throws SQLException {
        try (Connection c = pool.getConnection()) {
            insert(c, game, booster.multiplier(), booster.start(), booster.end(), booster.owner(), null);
        }
    }

    private static void insert(Connection c, String game, double multiplier, long start, long end, String owner, UUID ownerId) throws SQLException {
        try (PreparedStatement add = c.prepareStatement("INSERT INTO network_boosters (game, multiplier, starts, ends, owner_uuid,"
                + " owner_name, created) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            add.setString(1, game);
            add.setDouble(2, multiplier);
            add.setLong(3, start);
            add.setLong(4, end);
            add.setString(5, ownerId == null ? null : ownerId.toString());
            add.setString(6, owner);
            add.setLong(7, System.currentTimeMillis());
            add.executeUpdate();
        }
    }

    /** Ends every booster running or waiting on this game (they stay in the table as history). */
    public void clearBoosters(String game) throws SQLException {
        try (Connection c = pool.getConnection(); PreparedStatement s = c.prepareStatement(
                "UPDATE network_boosters SET ends = ? WHERE game = ? AND ends > ?")) {
            long now = System.currentTimeMillis();
            s.setLong(1, now);
            s.setString(2, game);
            s.setLong(3, now);
            s.executeUpdate();
        }
    }

    /** Gives each player this much network XP; returns everyone's new total. */
    public Map<UUID, Long> addXp(Map<UUID, String> players, long xp) throws SQLException {
        Map<UUID, Long> totals = new HashMap<>();
        if (players.isEmpty()) return totals;
        try (Connection c = pool.getConnection()) {
            long now = System.currentTimeMillis();
            try (PreparedStatement add = c.prepareStatement("INSERT INTO network_players (uuid, name, xp, updated) VALUES (?, ?, ?, ?)"
                    + " ON DUPLICATE KEY UPDATE xp = xp + VALUES(xp), name = VALUES(name), updated = VALUES(updated)")) {
                for (var entry : players.entrySet()) {
                    add.setString(1, entry.getKey().toString());
                    add.setString(2, entry.getValue());
                    add.setLong(3, xp);
                    add.setLong(4, now);
                    add.addBatch();
                }
                add.executeBatch();
            }
            try (PreparedStatement read = c.prepareStatement("SELECT xp FROM network_players WHERE uuid = ?")) {
                for (UUID id : players.keySet()) {
                    read.setString(1, id.toString());
                    try (ResultSet r = read.executeQuery()) {
                        if (r.next()) totals.put(id, r.getLong(1));
                    }
                }
            }
        }
        return totals;
    }

    @Override
    public void close() {
        pool.close();
    }
}
