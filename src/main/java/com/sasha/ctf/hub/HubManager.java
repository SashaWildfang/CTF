/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.hub;

import com.sasha.ctf.FractalCTF;
import com.sasha.ctf.config.CtfConfig;
import com.sasha.ctf.config.Messages;
import com.sasha.ctf.model.MatchRow;
import com.sasha.ctf.model.MatchState;
import com.sasha.ctf.model.Mode;
import com.sasha.ctf.model.TeamBalancer;
import com.sasha.ctf.model.TeamColor;
import com.sasha.ctf.model.VoteOption;
import com.sasha.ctf.util.Network;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The CTF hub: players wait here, vote for the next map and pick kits. Once enough are in, a countdown
 * runs; at zero the hub asks the game server (through the shared match row) to load the winning map,
 * and when it's loaded sends everyone over. While a match is on, the hub shows the score and players
 * can join late.
 */
public final class HubManager {

    /** What the hub is doing. */
    public enum Phase { WAITING, COUNTDOWN, STARTING, IN_MATCH }

    private static final long STUCK_MILLIS = 90_000;

    private final FractalCTF plugin;
    private final Random random = new Random();
    private final HubItems items;
    private final HubBoard board;
    private final HubNpcs npcs;
    private final HubBoards topBoards;
    private final HubLobby lobby;
    private final HubSeats seats;
    private final com.sasha.ctf.command.StatsMenu stats;
    private final Map<UUID, Integer> votes = new HashMap<>();

    private Phase phase = Phase.WAITING;
    private int secondsLeft;
    private List<VoteOption> options = List.of();
    private Map<String, Map.Entry<String, Set<Mode>>> maps = Map.of();
    private String lastMap;
    private volatile MatchRow row = MatchRow.idle();
    private boolean requested;
    private int ticks;
    private BukkitTask task;

    public HubManager(FractalCTF plugin) {
        this.plugin = plugin;
        this.items = new HubItems(plugin, this);
        this.board = new HubBoard(plugin, this);
        this.npcs = new HubNpcs(plugin, this);
        this.topBoards = new HubBoards(plugin, this);
        this.lobby = new HubLobby(plugin, this);
        this.seats = new HubSeats(plugin);
        this.stats = new com.sasha.ctf.command.StatsMenu(plugin);
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(new HubListener(plugin, this), plugin);
        npcs.start();
        topBoards.start();
        seats.start();
        refreshMaps(true);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::second, 20L, 20L);
        for (Player player : Bukkit.getOnlinePlayers()) welcome(player);
    }

    public void stop() {
        if (task != null) task.cancel();
        npcs.stop();
        topBoards.stop();
        seats.stop();
    }

    // ---------------------------------------------------------------- state

    public Phase phase() { return phase; }
    public int secondsLeft() { return secondsLeft; }
    public List<VoteOption> options() { return options; }
    public MatchRow row() { return row; }
    public HubItems items() { return items; }
    public HubBoard board() { return board; }
    public HubNpcs npcs() { return npcs; }
    public HubLobby lobby() { return lobby; }

    /** hub-npcs.yml is read again: characters, kit stands, spawn and boards. */
    public void reloadFile() {
        npcs.reload();
        topBoards.reloadFile();
    }

    public com.sasha.ctf.command.StatsMenu stats() { return stats; }

    public int votesFor(int option) {
        int count = 0;
        for (int vote : votes.values()) if (vote == option) count++;
        return count;
    }

    public Integer voteOf(UUID player) {
        return votes.get(player);
    }

    /** Whether a match is being played that hub players can still join. */
    public boolean joinable() {
        return phase == Phase.IN_MATCH && row.state() == MatchState.PLAYING;
    }

    // ---------------------------------------------------------------- players

    public void welcome(Player player) {
        player.teleport(spawn());
        items.give(player);
        board.show(player);
        plugin.kits().load(player.getUniqueId(), () -> board.update(player));
        // A match is on: straight in, onto whichever team needs players (with the default kit).
        if (joinable() && !player.hasPermission("ctf.hub.stay")) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && joinable()) joinMatch(player);
            }, 40L);
        }
    }

    // ---------------------------------------------------------------- admin

    private boolean paused;
    private VoteOption nextMatch;

    public boolean paused() { return paused; }

    /** /ctf pause: no countdown until /ctf resume. */
    public boolean pause(boolean pause) {
        if (paused == pause) return false;
        paused = pause;
        if (pause && phase == Phase.COUNTDOWN) phase = Phase.WAITING;
        plugin.messages().broadcast(pause ? "hub.paused" : "hub.resumed");
        board.updateAll();
        return true;
    }

    /** /ctf setmap: the next match is this map and mode, whatever the vote says. */
    public boolean setNext(String mapId, Mode mode) {
        var map = maps.get(mapId);
        if (map == null || !map.getValue().contains(mode)) return false;
        nextMatch = new VoteOption(mapId, map.getKey(), mode);
        options = List.of(nextMatch);
        votes.clear();
        board.updateAll();
        return true;
    }

    public Map<String, Map.Entry<String, Set<Mode>>> maps() { return maps; }

    private volatile Map<String, double[]> ratings = Map.of();
    private String lastPlayed;

    /** The map of the last match that was played (for /rate in the hub). */
    public String lastPlayed() { return lastPlayed; }

    /** "★4.3" for a map with ratings, "" otherwise. */
    public String ratingText(String mapId) {
        double[] rating = ratings.get(mapId);
        if (rating == null || rating[1] < 1) return "";
        return String.format(java.util.Locale.ROOT, "★%.1f", rating[0]);
    }

    public double[] rating(String mapId) {
        return ratings.get(mapId);
    }

    public void refreshRatings() {
        if (plugin.database() == null) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ratings = plugin.database().ratings();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.FINE, "Couldn't read map ratings", e);
            }
        });
    }

    /** /ctf end on the hub: leaves a "stop" note for the game server. */
    public boolean endMatch() {
        if (plugin.database() == null || phase != Phase.IN_MATCH && phase != Phase.STARTING) return false;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.database().setting("command", "stop");
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Couldn't ask the game server to stop", e);
            }
        });
        return true;
    }

    /** /spectate in the hub: watch the match that's on without joining a team. */
    public void spectate(Player player) {
        if (!(phase == Phase.IN_MATCH && (row.state() == MatchState.PLAYING || row.state() == MatchState.READY)) || plugin.database() == null) {
            plugin.messages().send(player, "hub.no-match");
            return;
        }
        java.util.UUID id = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.database().requestSpectate(id);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.messages().send(player, "hub.spectating", Messages.arg("map", mapName(row.map())));
                    Network.send(plugin, player, plugin.ctfConfig().gameServer());
                });
            } catch (SQLException e) {
                Bukkit.getScheduler().runTask(plugin, () -> plugin.messages().send(player, "general.no-database"));
            }
        });
    }

    /** /match pause|resume from the hub: the game server picks it up within two seconds. */
    public boolean matchCommand(String command) {
        if (plugin.database() == null || phase != Phase.IN_MATCH) return false;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.database().setting("command", command);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Couldn't send a command to the game server", e);
            }
        });
        return true;
    }

    public void leave(Player player) {
        votes.remove(player.getUniqueId());
        plugin.kits().forget(player.getUniqueId());
        board.forget(player);
    }

    public Location spawn() {
        CtfConfig config = plugin.ctfConfig();
        var world = Bukkit.getWorlds().get(0);
        if (config.hubSpawn() != null) return config.hubSpawn().in(world);
        // "auto": where the hub's builder put it (in front of the title), else the world spawn
        Location built = npcs == null ? null : npcs.spawn();
        return built != null ? built : world.getSpawnLocation().add(0.5, 0, 0.5);
    }

    public void vote(Player player, int option) {
        Messages messages = plugin.messages();
        if (phase != Phase.WAITING && phase != Phase.COUNTDOWN || option < 0 || option >= options.size()) {
            messages.send(player, "vote.closed");
            return;
        }
        votes.put(player.getUniqueId(), option);
        VoteOption chosen = options.get(option);
        messages.send(player, "vote.voted", Messages.arg("map", chosen.mapName()));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.4f);
        board.updateAll();
    }

    /** Sends a hub player into the match being played. */
    public void joinMatch(Player player) {
        if (!joinable()) {
            plugin.messages().send(player, "hub.no-match");
            return;
        }
        plugin.messages().send(player, "hub.joining", Messages.arg("map", mapName(row.map())));
        Network.send(plugin, player, plugin.ctfConfig().gameServer());
    }

    /** /ctf start: the countdown jumps to a few seconds (starting it even with too few players). */
    public boolean forceStart() {
        if (phase != Phase.WAITING && phase != Phase.COUNTDOWN) return false;
        if (options.isEmpty()) newVote();
        if (options.isEmpty()) return false;
        phase = Phase.COUNTDOWN;
        secondsLeft = Math.min(secondsLeft <= 0 ? 5 : secondsLeft, 5);
        forced = true;
        return true;
    }

    private boolean forced;

    // ---------------------------------------------------------------- every second

    private void second() {
        ticks++;
        poll();
        if (ticks % 30 == 0) refreshMaps(false);
        MatchRow current = row;
        switch (current.state()) {
            case IDLE -> idle(true);
            case REQUESTED, LOADING -> {
                setPhase(Phase.STARTING);
                if (System.currentTimeMillis() - current.changed() > STUCK_MILLIS) unstick(current);
            }
            case READY -> ready(current);
            case PLAYING -> {
                setPhase(Phase.IN_MATCH);
                lastPlayed = current.map();
            }
            // The match is over and players are on their way back: voting for the next one opens now,
            // and the countdown waits for the game server to finish cleaning up.
            case ENDING -> idle(false);
        }
        board.updateAll();
    }

    private void idle(boolean gameFree) {
        if (phase == Phase.STARTING && requested) return; // our request hasn't shown up in the row yet
        if (phase == Phase.STARTING || phase == Phase.IN_MATCH) {
            // A match just finished (or never started): back to voting.
            setPhase(Phase.WAITING);
            newVote();
        }
        if (options.isEmpty()) newVote();
        int players = Bukkit.getOnlinePlayers().size();
        CtfConfig config = plugin.ctfConfig();
        if (paused) {
            if (phase == Phase.COUNTDOWN) phase = Phase.WAITING;
            return;
        }
        if (phase == Phase.WAITING) {
            if (players >= config.minPlayers() && !options.isEmpty()) {
                phase = Phase.COUNTDOWN;
                secondsLeft = config.countdown();
                plugin.messages().broadcast("hub.countdown-started", Messages.arg("seconds", secondsLeft));
            }
            return;
        }
        // COUNTDOWN
        if (players < config.minPlayers() && !forced) {
            phase = Phase.WAITING;
            plugin.messages().broadcast("hub.countdown-cancelled", Messages.arg("needed", config.minPlayers() - players));
            return;
        }
        boolean everyoneVoted = players > 0 && votes.size() >= players;
        if ((players >= config.maxPlayers() || everyoneVoted) && secondsLeft > config.shortCountdown()) {
            secondsLeft = config.shortCountdown();
            plugin.messages().broadcast("hub.countdown-shortened", Messages.arg("seconds", secondsLeft));
        }
        if (secondsLeft <= 10 && secondsLeft > 0 || secondsLeft == 30 || secondsLeft == 20 || secondsLeft == 15) {
            plugin.messages().broadcast("hub.countdown", Messages.arg("seconds", secondsLeft));
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, secondsLeft <= 3 ? 1.5f : 1f);
            }
        }
        if (secondsLeft <= 1 && !gameFree) {
            secondsLeft = 1; // hold until the game server is free
            return;
        }
        if (--secondsLeft <= 0) request();
    }

    /** The countdown hit zero: pick the winning map and the teams, and ask the game server to load it. */
    private void request() {
        forced = false;
        if (options.isEmpty() || plugin.database() == null) {
            plugin.messages().broadcast(plugin.database() == null ? "hub.no-database" : "hub.no-maps");
            phase = Phase.WAITING;
            return;
        }
        VoteOption winner = nextMatch != null ? nextMatch : options.get(VoteOption.winner(options.size(), votes, random));
        nextMatch = null;
        List<UUID> players = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) players.add(player.getUniqueId());
        Map<UUID, TeamColor> roster = TeamBalancer.balance(players, winner.mode().teams(), random);
        MatchRow request = new MatchRow(MatchState.REQUESTED, winner.mapId(), winner.mode(), roster, "", null, System.currentTimeMillis());
        phase = Phase.STARTING;
        requested = true;
        lastMap = winner.mapId();
        plugin.messages().broadcast("hub.vote-won", Messages.arg("map", winner.mapName()), Messages.arg("mode", winner.mode().display()),
                Messages.arg("votes", votesFor(options.indexOf(winner))));
        for (Player player : Bukkit.getOnlinePlayers()) {
            TeamColor team = roster.get(player.getUniqueId());
            if (team != null) plugin.messages().send(player, "hub.your-team", Messages.comp("team", teamName(team)));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (!plugin.database().moveMatch(MatchState.IDLE, request)) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        requested = false;
                        plugin.messages().broadcast("hub.game-busy");
                    });
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Couldn't ask the game server for a match", e);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    requested = false;
                    phase = Phase.WAITING;
                    plugin.messages().broadcast("hub.no-database");
                });
            }
        });
    }

    /** The game server has the map loaded: send everyone over. */
    private void ready(MatchRow current) {
        if (phase == Phase.IN_MATCH) return;
        requested = false;
        setPhase(Phase.IN_MATCH);
        plugin.messages().broadcast("hub.sending", Messages.arg("map", mapName(current.map())));
        int delay = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (lobby.editing(player)) continue;                     // whoever is editing the lobby stays to finish
            // A few per tick, so the proxy isn't hit by everyone at once.
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) Network.send(plugin, player, plugin.ctfConfig().gameServer());
            }, delay++ / 4);
        }
    }

    /** The game server stopped answering mid-way: free the row so voting can go on. */
    private void unstick(MatchRow current) {
        plugin.getLogger().warning("The game server didn't load " + current.map() + " in time: starting a new vote.");
        requested = false;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.database().moveMatch(current.state(), MatchRow.idle());
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Couldn't reset the match row", e);
            }
        });
        plugin.messages().broadcast("hub.game-not-responding");
    }

    private void setPhase(Phase next) {
        if (phase == next) return;
        phase = next;
        for (Player player : Bukkit.getOnlinePlayers()) items.give(player);
    }

    private void newVote() {
        votes.clear();
        if (nextMatch != null) {
            options = List.of(nextMatch);
            return;
        }
        options = VoteOption.pick(maps, plugin.ctfConfig().voteOptions(), lastMap, random);
    }

    // ---------------------------------------------------------------- database

    private boolean polling;

    private void poll() {
        if (plugin.database() == null || polling) return;
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

    /** Map id -> {icon, authors}, shared by the game server. */
    private volatile Map<String, String[]> mapInfo = Map.of();

    public String mapIcon(String id) {
        String[] info = mapInfo.get(id);
        return info == null ? null : info[0];
    }

    public String mapAuthors(String id) {
        String[] info = mapInfo.get(id);
        return info == null || info.length < 2 ? "" : info[1];
    }

    private void refreshMaps(boolean resetVote) {
        if (plugin.database() == null) return;
        refreshRatings();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String info = plugin.database().setting("map-info");
                if (info != null) {
                    Map<String, String[]> parsed = new HashMap<>();
                    for (String line : info.split("\n")) {
                        String[] parts = line.split("\t", -1);
                        if (parts.length >= 2) parsed.put(parts[0], new String[] {parts[1], parts.length > 2 ? parts[2] : ""});
                    }
                    mapInfo = parsed;
                }
                Map<String, Map.Entry<String, Set<Mode>>> fresh = plugin.database().readMaps();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    boolean changed = !fresh.equals(maps);
                    maps = fresh;
                    // New maps arrived while nobody has voted yet: offer them.
                    if (resetVote || changed && votes.isEmpty() && (phase == Phase.WAITING)) newVote();
                });
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Couldn't read the map list", e);
            }
        });
    }

    public String mapName(String id) {
        if (id == null) return "?";
        var map = maps.get(id);
        return map == null ? id : map.getKey();
    }

    public static Component teamName(TeamColor team) {
        return Component.text(team.display(), team.text());
    }
}
