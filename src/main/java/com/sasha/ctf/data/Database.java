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
    private final String match, maps, kits, stats;

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
        createTables();
        logger.info("Connected to the CTF database.");
    }

    private void createTables() throws SQLException {
        try (Connection connection = pool.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + match + " (id VARCHAR(16) PRIMARY KEY, state VARCHAR(16) NOT NULL,"
                    + " map VARCHAR(64), mode VARCHAR(8), roster MEDIUMTEXT, score VARCHAR(255), winner VARCHAR(8), changed BIGINT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + maps + " (id VARCHAR(64) PRIMARY KEY, name VARCHAR(128) NOT NULL,"
                    + " modes VARCHAR(64) NOT NULL, changed BIGINT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + kits + " (uuid CHAR(36) PRIMARY KEY, kit VARCHAR(16) NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + stats + " (uuid CHAR(36) PRIMARY KEY, name VARCHAR(32) NOT NULL,"
                    + " kills INT NOT NULL DEFAULT 0, deaths INT NOT NULL DEFAULT 0, captures INT NOT NULL DEFAULT 0,"
                    + " wins INT NOT NULL DEFAULT 0, losses INT NOT NULL DEFAULT 0, games INT NOT NULL DEFAULT 0)");
            statement.executeUpdate("INSERT IGNORE INTO " + match + " (id, state, changed) VALUES ('" + MATCH_ID + "', 'IDLE', "
                    + System.currentTimeMillis() + ")");
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
                     + " (uuid, name, kills, deaths, captures, wins, losses, games) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
                     + " ON DUPLICATE KEY UPDATE name = VALUES(name), kills = kills + VALUES(kills), deaths = deaths + VALUES(deaths),"
                     + " captures = captures + VALUES(captures), wins = wins + VALUES(wins), losses = losses + VALUES(losses),"
                     + " games = games + VALUES(games)")) {
            statement.setString(1, player.toString());
            statement.setString(2, name);
            statement.setInt(3, add.kills);
            statement.setInt(4, add.deaths);
            statement.setInt(5, add.captures);
            statement.setInt(6, add.wins);
            statement.setInt(7, add.losses);
            statement.setInt(8, add.games);
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
                }
                return result;
            }
        }
    }
}
