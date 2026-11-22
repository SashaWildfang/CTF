/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.data;

import com.sasha.ctf.config.CtfConfig;
import com.sasha.ctf.model.MatchRow;
import com.sasha.ctf.model.MatchState;
import com.sasha.ctf.model.Mode;
import com.sasha.ctf.model.TeamColor;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * The shared MySQL database. Both CTF servers use the same one: the match row is how the hub and the game
 * server talk, and kit choices and stats follow players between them. Every method blocks, so call them
 * off the main thread.
 */
public final class Database implements AutoCloseable {

    private static final String MATCH_ID = "main";

    private final HikariDataSource pool;
    private final String match, maps, kits, stats, owned, layouts, settings;

    public Database(CtfConfig.Database settings, Logger logger) throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + settings.host() + ":" + settings.port() + "/" + settings.name()
                + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8");
        config.setUsername(settings.user());
        config.setPassword(settings.password());
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(1);
        config.setPoolName("FractalCTF");
        config.setConnectionTimeout(5000);
        this.pool = new HikariDataSource(config);
        String prefix = settings.prefix().replaceAll("[^A-Za-z0-9_]", "");
        this.match = prefix + "match";
        this.maps = prefix + "maps";
        this.kits = prefix + "kits";
        this.stats = prefix + "stats";
        this.owned = prefix + "owned_kits";
        this.layouts = prefix + "kit_layouts";
        this.settings = prefix + "settings";
        createTables();
        logger.info("Connected to the CTF database.");
    }

    private void createTables() throws SQLException {
        try (Connection connection = pool.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + match + " (id VARCHAR(16) PRIMARY KEY, state VARCHAR(16) NOT NULL,"
                    + " map VARCHAR(64), mode VARCHAR(8), roster MEDIUMTEXT, score VARCHAR(255), winner VARCHAR(8), changed BIGINT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + maps + " (id VARCHAR(64) PRIMARY KEY, name VARCHAR(128) NOT NULL,"
                    + " modes VARCHAR(64) NOT NULL, changed BIGINT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + kits + " (uuid CHAR(36) PRIMARY KEY, kit VARCHAR(32) NOT NULL)");
            statement.executeUpdate("ALTER TABLE " + kits + " MODIFY kit VARCHAR(32) NOT NULL"); // 1.0.0 made it too short
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + stats + " (uuid CHAR(36) PRIMARY KEY, name VARCHAR(32) NOT NULL,"
                    + " kills INT NOT NULL DEFAULT 0, deaths INT NOT NULL DEFAULT 0, captures INT NOT NULL DEFAULT 0,"
                    + " wins INT NOT NULL DEFAULT 0, losses INT NOT NULL DEFAULT 0, games INT NOT NULL DEFAULT 0)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + owned + " (uuid CHAR(36) NOT NULL, kit VARCHAR(32) NOT NULL,"
                    + " PRIMARY KEY (uuid, kit))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + layouts + " (uuid CHAR(36) NOT NULL, kit VARCHAR(32) NOT NULL,"
                    + " layout VARCHAR(1024) NOT NULL, PRIMARY KEY (uuid, kit))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + settings + " (name VARCHAR(32) PRIMARY KEY, value MEDIUMTEXT)");
            addColumn(connection, stats, "assists", "INT NOT NULL DEFAULT 0");
            addColumn(connection, stats, "coins", "INT NOT NULL DEFAULT 0");
            statement.executeUpdate("INSERT IGNORE INTO " + match + " (id, state, changed) VALUES ('" + MATCH_ID + "', 'IDLE', "
                    + System.currentTimeMillis() + ")");
        }
    }

    /** Adds a column to a table made by an older version (MySQL has no ADD COLUMN IF NOT EXISTS). */
    private static void addColumn(Connection connection, String table, String column, String type) throws SQLException {
        try (PreparedStatement check = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.columns"
                + " WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?")) {
            check.setString(1, table);
            check.setString(2, column);
            try (ResultSet rows = check.executeQuery()) {
                rows.next();
                if (rows.getInt(1) > 0) return;
            }
        }
        try (Statement alter = connection.createStatement()) {
            alter.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
        }
    }

    @Override
    public void close() {
        pool.close();
    }

    // ---------------------------------------------------------------- the match row

    public MatchRow readMatch() throws SQLException {
        try (Connection connection = pool.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + match + " WHERE id = ?")) {
            statement.setString(1, MATCH_ID);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return MatchRow.idle();
                MatchState state;
                try {
                    state = MatchState.valueOf(rows.getString("state"));
                } catch (IllegalArgumentException e) {
                    state = MatchState.IDLE;
                }
                return new MatchRow(state, rows.getString("map"), Mode.parse(rows.getString("mode")).orElse(null),
                        decodeRoster(rows.getString("roster")), rows.getString("score") == null ? "" : rows.getString("score"),
                        TeamColor.parse(rows.getString("winner")).orElse(null), rows.getLong("changed"));
            }
        }
    }

    /**
     * Writes the row only if it's still in {@code from}, so the two servers can't trample each other.
     * Returns whether it was written.
     */
    public boolean moveMatch(MatchState from, MatchRow to) throws SQLException {
        return writeMatch(to, from);
    }

    /** Writes the row whatever state it's in (an admin stop, or a server cleaning up after a restart). */
    public void forceMatch(MatchRow to) throws SQLException {
        writeMatch(to, null);
    }

    private boolean writeMatch(MatchRow row, MatchState from) throws SQLException {
        String sql = "UPDATE " + match + " SET state = ?, map = ?, mode = ?, roster = ?, score = ?, winner = ?, changed = ? WHERE id = ?"
                + (from == null ? "" : " AND state = ?");
        try (Connection connection = pool.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, row.state().name());
            statement.setString(2, row.map());
            statement.setString(3, row.mode() == null ? null : row.mode().id());
            statement.setString(4, encodeRoster(row.roster()));
            statement.setString(5, row.score() == null ? "" : truncate(row.score(), 255));
            statement.setString(6, row.winner() == null ? null : row.winner().id());
            statement.setLong(7, System.currentTimeMillis());
            statement.setString(8, MATCH_ID);
            if (from != null) statement.setString(9, from.name());
            return statement.executeUpdate() > 0;
        }
    }

    /** Only the score line, while a match is being played. */
    public void updateScore(String score) throws SQLException {
        try (Connection connection = pool.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE " + match + " SET score = ? WHERE id = ? AND state = 'PLAYING'")) {
            statement.setString(1, truncate(score, 255));
            statement.setString(2, MATCH_ID);
            statement.executeUpdate();
        }
    }

    static String encodeRoster(Map<UUID, TeamColor> roster) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<UUID, TeamColor> entry : roster.entrySet()) {
            if (!out.isEmpty()) out.append(';');
            out.append(entry.getKey()).append('=').append(entry.getValue().id());
        }
        return out.toString();
    }

    static Map<UUID, TeamColor> decodeRoster(String text) {
        Map<UUID, TeamColor> roster = new LinkedHashMap<>();
        if (text == null || text.isBlank()) return roster;
        for (String part : text.split(";")) {
            String[] pair = part.split("=", 2);
            if (pair.length != 2) continue;
            try {
                UUID id = UUID.fromString(pair[0]);
                TeamColor.parse(pair[1]).ifPresent(team -> roster.put(id, team));
            } catch (IllegalArgumentException ignored) {
                // a broken entry: skip it
            }
        }
        return roster;
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    // ---------------------------------------------------------------- maps

    /** The game server's maps and the modes each can be played in, for the hub's vote. */
    public void saveMap(String id, String name, Set<Mode> modes) throws SQLException {
        try (Connection connection = pool.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO " + maps + " (id, name, modes, changed) VALUES (?, ?, ?, ?)"
                     + " ON DUPLICATE KEY UPDATE name = VALUES(name), modes = VALUES(modes), changed = VALUES(changed)")) {
            statement.setString(1, id);
            statement.setString(2, truncate(name, 128));
            statement.setString(3, String.join(",", modes.stream().map(Mode::id).toList()));
            statement.setLong(4, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    /** Removes maps the game server no longer has. */
    public void keepOnlyMaps(Set<String> ids) throws SQLException {
        try (Connection connection = pool.getConnection(); Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT id FROM " + maps)) {
            while (rows.next()) {
                String id = rows.getString(1);
                if (ids.contains(id)) continue;
                try (PreparedStatement delete = connection.prepareStatement("DELETE FROM " + maps + " WHERE id = ?")) {
                    delete.setString(1, id);
                    delete.executeUpdate();
                }
            }
        }
    }

    /** Map id → (name, playable modes). */
    public Map<String, Map.Entry<String, Set<Mode>>> readMaps() throws SQLException {
        Map<String, Map.Entry<String, Set<Mode>>> result = new HashMap<>();
        try (Connection connection = pool.getConnection(); Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT id, name, modes FROM " + maps)) {
            while (rows.next()) {
                Set<Mode> modes = EnumSet.noneOf(Mode.class);
                for (String mode : rows.getString("modes").split(",")) Mode.parse(mode).ifPresent(modes::add);
                result.put(rows.getString("id"), Map.entry(rows.getString("name"), modes));
            }
        }
        return result;
    }

    // ---------------------------------------------------------------- kits

    public Optional<String> readKit(UUID player) throws SQLException {
        try (Connection connection = pool.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT kit FROM " + kits + " WHERE uuid = ?")) {
            statement.setString(1, player.toString());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(rows.getString(1)) : Optional.empty();
            }
        }
    }

    public void saveKit(UUID player, String kit) throws SQLException {
        try (Connection connection = pool.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO " + kits + " (uuid, kit) VALUES (?, ?)"
                     + " ON DUPLICATE KEY UPDATE kit = VALUES(kit)")) {
            statement.setString(1, player.toString());
            statement.setString(2, kit);
            statement.executeUpdate();
        }
    }

    // ---------------------------------------------------------------- stats

    /** Adds one match's numbers to a player's totals. */
    public void addStats(UUID player, String name, PlayerStats add) throws SQLException {
        try (Connection connection = pool.getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO " + stats
                     + " (uuid, name, kills, deaths, captures, wins, losses, games, assists, coins) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                     + " ON DUPLICATE KEY UPDATE name = VALUES(name), kills = kills + VALUES(kills), deaths = deaths + VALUES(deaths),"
                     + " captures = captures + VALUES(captures), wins = wins + VALUES(wins), losses = losses + VALUES(losses),"
                     + " games = games + VALUES(games), assists = assists + VALUES(assists), coins = coins + VALUES(coins)")) {
            statement.setString(1, player.toString());
            statement.setString(2, name);
            statement.setInt(3, add.kills);
            statement.setInt(4, add.deaths);
            statement.setInt(5, add.captures);
            statement.setInt(6, add.wins);
            statement.setInt(7, add.losses);
            statement.setInt(8, add.games);
            statement.setInt(9, add.assists);
            statement.setInt(10, add.coins);
            statement.executeUpdate();
        }
    }

    public PlayerStats readStats(UUID player) throws SQLException {
        try (Connection connection = pool.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + stats + " WHERE uuid = ?")) {
            statement.setString(1, player.toString());
            try (ResultSet rows = statement.executeQuery()) {
                PlayerStats result = new PlayerStats();
                if (rows.next()) {
                    result.kills = rows.getInt("kills");
                    result.deaths = rows.getInt("deaths");
                    result.captures = rows.getInt("captures");
                    result.wins = rows.getInt("wins");
                    result.losses = rows.getInt("losses");
                    result.games = rows.getInt("games");
                    result.assists = rows.getInt("assists");
                    result.coins = rows.getInt("coins");
                }
                return result;
            }
        }
    }

    // ---------------------------------------------------------------- coins, owned kits and layouts

    /** Everything about a player's kits: what they own, their layouts, and their coins. */
    public record KitData(Set<String> owned, Map<String, String> layouts, int coins) {}

    public KitData readKitData(UUID player) throws SQLException {
        Set<String> ownedKits = new java.util.HashSet<>();
        Map<String, String> savedLayouts = new HashMap<>();
        int coins = 0;
        try (Connection connection = pool.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT kit FROM " + owned + " WHERE uuid = ?")) {
                statement.setString(1, player.toString());
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) ownedKits.add(rows.getString(1));
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("SELECT kit, layout FROM " + layouts + " WHERE uuid = ?")) {
                statement.setString(1, player.toString());
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) savedLayouts.put(rows.getString(1), rows.getString(2));
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("SELECT coins FROM " + stats + " WHERE uuid = ?")) {
                statement.setString(1, player.toString());
                try (ResultSet rows = statement.executeQuery()) {
                    if (rows.next()) coins = rows.getInt(1);
                }
            }
        }
        return new KitData(ownedKits, savedLayouts, coins);
    }

    /**
     * Takes the coins and adds the kit in one go. Returns the coins left, or -1 if they didn't have
     * enough (nothing changes then).
     */
    public int buyKit(UUID player, String name, String kit, int price) throws SQLException {
        try (Connection connection = pool.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement ensure = connection.prepareStatement("INSERT IGNORE INTO " + stats + " (uuid, name) VALUES (?, ?)")) {
                    ensure.setString(1, player.toString());
                    ensure.setString(2, name);
                    ensure.executeUpdate();
                }
                try (PreparedStatement spend = connection.prepareStatement("UPDATE " + stats + " SET coins = coins - ? WHERE uuid = ? AND coins >= ?")) {
                    spend.setInt(1, price);
                    spend.setString(2, player.toString());
                    spend.setInt(3, price);
                    if (spend.executeUpdate() == 0) {
                        connection.rollback();
                        return -1;
                    }
                }
                try (PreparedStatement add = connection.prepareStatement("INSERT IGNORE INTO " + owned + " (uuid, kit) VALUES (?, ?)")) {
                    add.setString(1, player.toString());
                    add.setString(2, kit);
                    add.executeUpdate();
                }
                int left;
                try (PreparedStatement read = connection.prepareStatement("SELECT coins FROM " + stats + " WHERE uuid = ?")) {
                    read.setString(1, player.toString());
                    try (ResultSet rows = read.executeQuery()) {
                        rows.next();
                        left = rows.getInt(1);
                    }
                }
                connection.commit();
                return left;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    /** Gives (or with a negative amount takes) coins; never goes below zero. Returns the new balance. */
    public int addCoins(UUID player, String name, int amount) throws SQLException {
        try (Connection connection = pool.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO " + stats + " (uuid, name, coins) VALUES (?, ?, GREATEST(0, ?))"
                    + " ON DUPLICATE KEY UPDATE coins = GREATEST(0, coins + ?)")) {
                statement.setString(1, player.toString());
                statement.setString(2, name);
                statement.setInt(3, amount);
                statement.setInt(4, amount);
                statement.executeUpdate();
            }
            try (PreparedStatement read = connection.prepareStatement("SELECT coins FROM " + stats + " WHERE uuid = ?")) {
                read.setString(1, player.toString());
                try (ResultSet rows = read.executeQuery()) {
                    return rows.next() ? rows.getInt(1) : 0;
                }
            }
        }
    }

    public void saveLayout(UUID player, String kit, String layout) throws SQLException {
        try (Connection connection = pool.getConnection();
             PreparedStatement statement = connection.prepareStatement(layout == null
                     ? "DELETE FROM " + layouts + " WHERE uuid = ? AND kit = ?"
                     : "INSERT INTO " + layouts + " (uuid, kit, layout) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE layout = VALUES(layout)")) {
            statement.setString(1, player.toString());
            statement.setString(2, kit);
            if (layout != null) statement.setString(3, layout);
            statement.executeUpdate();
        }
    }

    /** Gives a kit for free (admin) or takes it away. */
    public void setOwned(UUID player, String kit, boolean owns) throws SQLException {
        try (Connection connection = pool.getConnection();
             PreparedStatement statement = connection.prepareStatement(owns
                     ? "INSERT IGNORE INTO " + owned + " (uuid, kit) VALUES (?, ?)"
                     : "DELETE FROM " + owned + " WHERE uuid = ? AND kit = ?")) {
            statement.setString(1, player.toString());
            statement.setString(2, kit);
            statement.executeUpdate();
        }
    }

    // ---------------------------------------------------------------- shared settings (both servers read them)

    /** A shared setting, e.g. "disabled-kits" or "command"; null when not set. */
    public String setting(String name) throws SQLException {
        try (Connection connection = pool.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT value FROM " + settings + " WHERE name = ?")) {
            statement.setString(1, name);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getString(1) : null;
            }
        }
    }

    public void setting(String name, String value) throws SQLException {
        try (Connection connection = pool.getConnection();
             PreparedStatement statement = connection.prepareStatement(value == null
                     ? "DELETE FROM " + settings + " WHERE name = ?"
                     : "INSERT INTO " + settings + " (name, value) VALUES (?, ?) ON DUPLICATE KEY UPDATE value = VALUES(value)")) {
            statement.setString(1, name);
            if (value != null) statement.setString(2, value);
            statement.executeUpdate();
        }
    }

    /** Reads and clears a one-off command left for the game server (e.g. "stop"). */
    public String takeCommand() throws SQLException {
        try (Connection connection = pool.getConnection()) {
            String value;
            try (PreparedStatement read = connection.prepareStatement("SELECT value FROM " + settings + " WHERE name = 'command'")) {
                try (ResultSet rows = read.executeQuery()) {
                    value = rows.next() ? rows.getString(1) : null;
                }
            }
            if (value == null) return null;
            try (PreparedStatement clear = connection.prepareStatement("DELETE FROM " + settings + " WHERE name = 'command' AND value = ?")) {
                clear.setString(1, value);
                return clear.executeUpdate() > 0 ? value : null;
            }
        }
    }
}
