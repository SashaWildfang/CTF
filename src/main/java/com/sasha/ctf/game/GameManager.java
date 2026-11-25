/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.game;

import com.sasha.ctf.FractalCTF;
import com.sasha.ctf.config.Messages;
import com.sasha.ctf.data.PlayerStats;
import com.sasha.ctf.model.MapDef;
import com.sasha.ctf.model.MatchRow;
import com.sasha.ctf.model.MatchState;
import com.sasha.ctf.model.Mode;
import com.sasha.ctf.model.TeamColor;
import com.sasha.ctf.util.Network;
import com.sasha.ctf.util.WorldFiles;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The game server: waits for the hub to ask for a match (through the shared match row), copies and
 * loads the map, plays the match, saves everyone's stats and sends them back to the hub.
 */
public final class GameManager {

    private static final String WORLD_PREFIX = "ctf_match_";
    private static final long EMPTY_ABORT_MILLIS = 60_000;

    private final FractalCTF plugin;
    private volatile MatchRow row = MatchRow.idle();
    private MatchState local = MatchState.IDLE;
    private Match match;
    private MatchRow request;
    private long readySince;
    /** When the lobby sends (or sent) everyone over, as it told us; 0 if it didn't. */
    private volatile long sendAt;
    private int ticks;
    private int endIn = -1;
    private boolean polling;
    private BukkitTask task;
    private int worldNumber;
    private Abilities abilities;

    public GameManager(FractalCTF plugin) {
        this.plugin = plugin;
    }

    public void start() {
        abilities = new Abilities(plugin, this);
        spectators = new SpectatorTools(plugin, this);
        picker = new TeamPicker(plugin);
        spawnReturn = new SpawnReturn(plugin, this);
        Bukkit.getPluginManager().registerEvents(spawnReturn, plugin);
        Bukkit.getPluginManager().registerEvents(abilities, plugin);
        Bukkit.getPluginManager().registerEvents(new GameListener(plugin, this, abilities), plugin);
        cleanOldWorlds();
        // A match that was running when the server stopped can't be picked up again: free the row.
        if (plugin.database() != null) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    MatchRow current = plugin.database().readMatch();
                    if (current.state() != MatchState.IDLE && current.state() != MatchState.REQUESTED) {
                        plugin.database().forceMatch(MatchRow.idle());
                    }
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "Couldn't check the match row", e);
                }
            });
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::second, 20L, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
        if (match != null) {
            for (Player player : match.world().getPlayers()) Network.send(plugin, player, plugin.ctfConfig().hubServer());
            if (plugin.database() != null) {
                try {
                    plugin.database().forceMatch(MatchRow.idle());
                } catch (SQLException ignored) {
                    // shutting down anyway
                }
            }
        }
    }

    public Match match() { return match; }
    public Abilities abilities() { return abilities; }
    public SpectatorTools spectatorTools() { return spectators; }
    public TeamPicker teamPicker() { return picker; }
    public SpawnReturn spawnReturn() { return spawnReturn; }
    private SpawnReturn spawnReturn;
    private TeamPicker picker;

    private SpectatorTools spectators;

    /**
     * A player arrived for the match: if they asked the hub to watch, they spectate; otherwise they play.
     * (The database is asked first, so they wait a moment at the spectator spawn.)
     */
    void arrive(Player player) {
        if (match == null) return;
        if (plugin.database() == null) {
            match.join(player);
            return;
        }
        player.teleport(match.spectatorSpawn());
        java.util.UUID id = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean watch;
            try {
                watch = plugin.database().takeSpectate(id);
            } catch (SQLException e) {
                watch = false;
            }
            boolean spectate = watch;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && match != null && match.world().equals(player.getWorld())) match.join(player, spectate);
                else if (player.isOnline() && match != null) {
                    player.teleport(match.spectatorSpawn());
                    match.join(player, spectate);
                }
            });
        });
    }

    /** /match pause and /match resume on this server. */
    public boolean pauseMatch(boolean pause) {
        return pauseMatch(pause, null);
    }

    /** {@code by}: the name of whoever paused or resumed it (shown to everyone). */
    public boolean pauseMatch(boolean pause, String by) {
        if (match == null || local != MatchState.PLAYING || !match.live()) return false;
        if (pause == match.paused()) return false;                   // already paused (or already running): /resumematch resumes
        return match.pause(pause, by);
    }
    public MatchState state() { return local; }

    // ---------------------------------------------------------------- every second

    private void second() {
        ticks++;
        poll();
        if (ticks % 2 == 0 && match != null && (local == MatchState.READY || local == MatchState.PLAYING)) checkCommands();
        switch (local) {
            case IDLE -> {
                if (row.state() == MatchState.REQUESTED) load(row);
            }
            case READY -> waitForPlayers();
            case PLAYING -> {
                match.second();
                if (match.live() && !match.paused()) abilities.tick(match);
                if (match.phase() == Match.Phase.OVER) ending();
                else if (ticks % 3 == 0) shareScore();
            }
            case ENDING -> {
                if (--endIn == 0) sendEveryoneBack();
                else if (endIn == -4) unload();
                else if (endIn > 0 && match != null) {                     // "Returning to the lobby in 10s"
                    for (Player player : match.world().getPlayers()) {
                        plugin.messages().actionBar(player, "game.returning", com.sasha.ctf.config.Messages.arg("seconds", endIn));
                    }
                }
            }
            default -> { }
        }
    }

    private void poll() {
        if (plugin.database() == null || polling || local != MatchState.IDLE) return;
        polling = true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                row = plugin.database().readMatch();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Couldn't read the match row", e);
            } finally {
                polling = false;
            }
        });
    }

    private boolean checking;

    /** The hub's /ctf end leaves a "stop" note in the database. */
    private void checkCommands() {
        if (plugin.database() == null || checking) return;
        checking = true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String command = plugin.database().takeCommand();
                if ("stop".equals(command)) Bukkit.getScheduler().runTask(plugin, this::stopMatch);
                else if (command != null && (command.equals("pause") || command.startsWith("pause:"))) {
                    String by = command.contains(":") ? command.substring(command.indexOf(':') + 1) : null;
                    Bukkit.getScheduler().runTask(plugin, () -> pauseMatch(true, by));
                } else if (command != null && (command.equals("resume") || command.startsWith("resume:"))) {
                    String by = command.contains(":") ? command.substring(command.indexOf(':') + 1) : null;
                    Bukkit.getScheduler().runTask(plugin, () -> pauseMatch(false, by));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.FINE, "Couldn't read commands", e);
            } finally {
                checking = false;
            }
        });
    }

    // ---------------------------------------------------------------- loading

    private void load(MatchRow wanted) {
        MapDef map = plugin.maps().get(wanted.map()).orElse(null);
        Mode mode = wanted.mode();
        if (map == null || mode == null || !map.missing(mode).isEmpty()) {
            plugin.getLogger().warning("The hub asked for " + wanted.map() + " (" + wanted.mode() + "), which this server can't play: ignoring it.");
            write(MatchState.REQUESTED, MatchRow.idle());
            row = MatchRow.idle();
            return;
        }
        local = MatchState.LOADING;
        request = wanted;
        String worldName = WORLD_PREFIX + (++worldNumber);
        Path source = plugin.maps().folder(map.id());
        Path target = Bukkit.getWorldContainer().toPath().resolve(worldName);
        plugin.getLogger().info("Loading " + map.name + " (" + mode.display() + ") for the hub's match.");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (!plugin.database().moveMatch(MatchState.REQUESTED, withState(wanted, MatchState.LOADING))) {
                    Bukkit.getScheduler().runTask(plugin, () -> local = MatchState.IDLE);
                    return;
                }
                WorldFiles.delete(target);
                WorldFiles.copy(source, target);
            } catch (IOException | SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Couldn't copy map " + map.id(), e);
                Bukkit.getScheduler().runTask(plugin, this::abort);
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> loadWorld(worldName, map, mode, wanted));
        });
    }

    private void loadWorld(String worldName, MapDef map, Mode mode, MatchRow wanted) {
        World world = new WorldCreator(worldName).generator(new VoidGenerator()).createWorld();
        if (world == null) {
            abort();
            return;
        }
        world.setAutoSave(false);
        world.setDifficulty(Difficulty.NORMAL);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRules.KEEP_INVENTORY, false);
        world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        world.setGameRule(GameRules.SHOW_DEATH_MESSAGES, false);
        world.setGameRule(GameRules.MOB_GRIEFING, false);
        world.setGameRule(GameRules.BLOCK_DROPS, true);
        world.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, true);
        world.setStorm(false);
        world.setTime(6000);
        match = new Match(plugin, world, map, mode, wanted.roster());
        match.clearOtherModes();                                     // a ported map's objectives for other modes go
        // The chunks everyone arrives into are loaded before the hub is told the map is ready, so nobody's
        // arrival waits on them (they stay loaded while players are there, and go with the world).
        Location arrival = match.spectatorSpawn();
        int cx = arrival.getBlockX() >> 4, cz = arrival.getBlockZ() >> 4, radius = 3;
        int[] left = {(2 * radius + 1) * (2 * radius + 1)};
        boolean[] told = {false};
        Match loaded = match;
        Runnable ready = () -> {
            if (told[0] || match != loaded || local != MatchState.LOADING) return;
            told[0] = true;
            local = MatchState.READY;
            readySince = System.currentTimeMillis();
            sendAt = 0;
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    long at = Long.parseLong(String.valueOf(plugin.database().setting("send-at")));
                    long now = System.currentTimeMillis();
                    if (at > now - 60_000 && at < now + 600_000) sendAt = at;   // anything else is left over from another day
                } catch (java.sql.SQLException | NumberFormatException ignored) {
                    // no note from the lobby: counted from when the map was ready, as before
                }
            });
            write(MatchState.LOADING, withState(wanted, MatchState.READY));
            plugin.getLogger().info(map.name + " is loaded: the hub is sending the players.");
        };
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                world.getChunkAtAsync(x, z).thenAccept(chunk -> {
                    chunk.addPluginChunkTicket(plugin);
                    if (--left[0] == 0) ready.run();
                });
            }
        }
        Bukkit.getScheduler().runTaskLater(plugin, ready, 60L);      // or after three seconds, whatever is still loading
    }

    /** After the hub sends everyone: start once they're all here, or once the wait is over. */
    private void waitForPlayers() {
        // Counted from when the lobby sends everyone, not from when the map was ready: the lobby's gate lets
        // players in early, and the match must not begin on the first few of them while the rest are still to come.
        long waited = System.currentTimeMillis() - Math.max(readySince, sendAt);
        int here = match.world().getPlayers().size();
        // (everyone arrives without a team now, so it's who is here, not who is playing)
        boolean allHere = here > 0 && here >= request.roster().size();
        boolean waitedEnough = here > 0 && waited >= plugin.ctfConfig().joinWait() * 1000L;
        if (allHere || waitedEnough) {
            match.beginCountdown(plugin.ctfConfig().startCountdown());
            local = MatchState.PLAYING;
            write(MatchState.READY, new MatchRow(MatchState.PLAYING, request.map(), request.mode(), request.roster(),
                    match.scoreLine(), null, System.currentTimeMillis()));
        } else if (here == 0 && waited > EMPTY_ABORT_MILLIS) {
            plugin.getLogger().warning("Nobody arrived for the match: unloading it.");
            abort();
        } else {
            match.updateSidebar();
        }
    }

    // ---------------------------------------------------------------- ending

    private void ending() {
        local = MatchState.ENDING;
        abilities.clear();
        spawnReturn.clear();
        endIn = plugin.ctfConfig().endSeconds();
        for (Player player : match.world().getPlayers()) {
            plugin.messages().actionBar(player, "game.returning", com.sasha.ctf.config.Messages.arg("seconds", endIn));
        }
        Match.Result result = match.result();
        TeamColor winner = result == null ? null : result.winner();
        write(MatchState.PLAYING, new MatchRow(MatchState.ENDING, request.map(), request.mode(), request.roster(),
                match.scoreLine(), winner, System.currentTimeMillis()));
        Map<UUID, Map.Entry<String, PlayerStats>> results = new HashMap<>(match.results());
        // Whoever played and may not keep a kit between matches picks again in the lobby (or plays the default).
        for (Player player : match.world().getPlayers()) {
            if (results.containsKey(player.getUniqueId()) && !player.hasPermission("ctf.keepkit")) plugin.kits().clearChoice(player.getUniqueId());
        }
        String mapId = match.map().id();
        Mode mode = match.mode();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (Map.Entry<UUID, Map.Entry<String, PlayerStats>> entry : results.entrySet()) {
                try {
                    plugin.database().addStats(entry.getKey(), entry.getValue().getKey(), entry.getValue().getValue(), mapId, mode);
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "Couldn't save stats", e);
                }
            }
        });
    }

    private void sendEveryoneBack() {
        // (no chat line: the action bar has been counting down to it)
        for (Player player : Bukkit.getOnlinePlayers()) Network.send(plugin, player, plugin.ctfConfig().hubServer());
    }

    /** /ctf stop: ends the match now with no winner. */
    public boolean stopMatch() {
        if (match == null || local == MatchState.ENDING) return false;
        if (local == MatchState.PLAYING) {
            match.finish(null, "game.stopped");
            return true;
        }
        abort();
        return true;
    }

    private void abort() {
        if (match != null) {
            for (Player player : match.world().getPlayers()) Network.send(plugin, player, plugin.ctfConfig().hubServer());
        }
        local = MatchState.ENDING;
        endIn = -100; // unloaded by the task below, not the countdown
        Bukkit.getScheduler().runTaskLater(plugin, this::unload, 60L);
    }

    private void unload() {
        abilities.clear();
        if (match != null) {
            World world = match.world();
            World fallback = Bukkit.getWorlds().get(0);
            for (Player player : world.getPlayers()) player.teleport(fallback.getSpawnLocation());
            Path folder = world.getWorldFolder().toPath();
            Bukkit.unloadWorld(world, false);
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                try {
                    WorldFiles.delete(folder);
                } catch (IOException e) {
                    plugin.getLogger().log(Level.WARNING, "Couldn't delete the old match world " + folder.getFileName(), e);
                }
            }, 40L);
        }
        match = null;
        request = null;
        local = MatchState.IDLE;
        endIn = -1;
        row = MatchRow.idle();
        if (plugin.database() != null) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    plugin.database().forceMatch(MatchRow.idle());
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "Couldn't free the match row", e);
                }
            });
        }
    }

    // ---------------------------------------------------------------- helpers

    private void shareScore() {
        String score = match.scoreLine();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.database().updateScore(score);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.FINE, "Couldn't share the score", e);
            }
        });
    }

    private void write(MatchState from, MatchRow to) {
        if (plugin.database() == null) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.database().moveMatch(from, to);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Couldn't update the match row", e);
            }
        });
    }

    private static MatchRow withState(MatchRow row, MatchState state) {
        return new MatchRow(state, row.map(), row.mode(), row.roster(), row.score(), row.winner(), System.currentTimeMillis());
    }

    /** Match worlds left over from a crash or restart. */
    private void cleanOldWorlds() {
        List<java.io.File> folders = new ArrayList<>();
        java.io.File[] top = Bukkit.getWorldContainer().listFiles((dir, name) -> name.startsWith(WORLD_PREFIX));
        if (top != null) folders.addAll(List.of(top));
        // Paper 26 moves loaded worlds to world/dimensions/minecraft/<name>
        Path dimensions = WorldFiles.dimensionsFolder(Bukkit.getWorlds().get(0).getWorldFolder().toPath());
        java.io.File[] moved = dimensions == null ? null : dimensions.toFile().listFiles((dir, name) -> name.startsWith(WORLD_PREFIX));
        if (moved != null) folders.addAll(List.of(moved));
        for (java.io.File folder : folders) {
            if (Bukkit.getWorld(folder.getName()) != null) continue;
            try {
                WorldFiles.delete(folder.toPath());
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Couldn't delete old match world " + folder.getName(), e);
            }
        }
    }

    /** Players who arrive when there's no match: back to the hub. */
    void noMatch(Player player) {
        player.setGameMode(GameMode.SPECTATOR);
        plugin.messages().send(player, "game.no-match");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && !player.hasPermission("ctf.admin")) Network.send(plugin, player, plugin.ctfConfig().hubServer());
        }, 40L);
    }
}
