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
    private final HubPortal portal;
    private final HubLabels labels;
    /** The vote is closed and the winning map is loading while the countdown's last seconds run down. */
    private boolean locked;
    /** The option that won the vote, from the moment the vote closes until the next vote. */
    private VoteOption chosen;
    private boolean sawLoading;
    private final Map<UUID, Long> throughGate = new HashMap<>();
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
        this.portal = new HubPortal(plugin, this);
        this.labels = new HubLabels(plugin);
        this.stats = new com.sasha.ctf.command.StatsMenu(plugin);
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(new HubListener(plugin, this), plugin);
        npcs.start();
        topBoards.start();
        seats.start();
        portal.start();
        labels.start();
        refreshMaps(true);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::second, 20L, 20L);
        for (Player player : Bukkit.getOnlinePlayers()) welcome(player);
    }

    public void stop() {
        if (task != null) task.cancel();
        npcs.stop();
        topBoards.stop();
        seats.stop();
        portal.stop();
        labels.stop();
        board.clear();
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
        portal.reloadFile();
        labels.reloadFile();
    }

    HubPortal portal() { return portal; }

    /** Whether the vote has closed (the winner is loading). */
    public boolean locked() { return locked; }

    /** The map vote is open: counting down to the start, before the vote closes, and not paused. */
    public boolean votingOpen() { return phase == Phase.COUNTDOWN && !locked && !paused; }
    public VoteOption chosen() { return chosen; }

    /** Someone walked through the open gate: into the match, if it is there to walk into yet. */
    public void enterGate(Player player) {
        MatchState state = row.state();
        if (state != MatchState.READY && state != MatchState.PLAYING || lobby.editing(player)) return;
        long now = System.currentTimeMillis();
        if (now - throughGate.getOrDefault(player.getUniqueId(), 0L) < 5000) return;
        throughGate.put(player.getUniqueId(), now);
        plugin.messages().send(player, "hub.joining", Messages.arg("map", mapName(row.map())));
        Network.send(plugin, player, plugin.ctfConfig().gameServer());
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
        move(player, spawn());
        items.give(player);
        board.show(player);
        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE && player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
            boolean fly = flying.contains(player.getUniqueId()) && player.hasPermission("ctf.fly");
            if (!fly) flying.remove(player.getUniqueId());
            player.setAllowFlight(fly);
            if (!fly) player.setFlying(false);
        }
        plugin.kits().load(player.getUniqueId(), () -> {
            if (!player.isOnline()) return;
            board.refreshTag(player);
            board.update(player);
            plugin.boosters().tell(player, true);
            if (plugin.kits().selected(player.getUniqueId()) == null) {
                plugin.messages().send(player, "kits.pick-reminder", Messages.arg("kit", plugin.kits().defaultKit().display()));
            }
        });
        // A match is on: nobody is sent into it any more; they join through the gate, the Join item or /join.
        // hub.auto-join-running-match: true brings back being sent straight in (onto whichever team needs players).
        if (plugin.getConfig().getBoolean("hub.auto-join-running-match", false) && joinable() && !player.hasPermission("ctf.hub.stay")) {
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
        if (pause && phase == Phase.COUNTDOWN && !locked) phase = Phase.WAITING;
        plugin.messages().broadcast(pause ? "hub.paused" : "hub.resumed");
        board.updateAll();
        return true;
    }

    /**
     * /setmap (/ctf setmap): this map and mode win the next vote, whatever the votes say, even if it is not one of the
     * maps on offer. The vote carries on as it is; when it closes, this map is announced as the winner.
     * Mode null: the map's first mode. Returns false if the map doesn't exist or can't play that mode.
     */
    public boolean setNext(String mapId, Mode mode) {
        var map = maps.get(mapId);
        if (map == null || map.getValue().isEmpty()) return false;
        if (mode == null) mode = map.getValue().stream().sorted().findFirst().orElse(null);
        if (mode == null || !map.getValue().contains(mode)) return false;
        nextMatch = new VoteOption(mapId, map.getKey(), mode);
        board.updateAll();
        return true;
    }

    /** /endvoting (and /setmap): the vote closes on the next second and the gate countdown starts. */
    private boolean closeVoteNow;

    /** Ends the vote now (also with too few players). False if there's no vote to end. */
    public boolean endVoting() {
        if (locked || paused || phase == Phase.STARTING || phase == Phase.IN_MATCH) return false;
        if (options.isEmpty()) newVote();
        if (options.isEmpty() && nextMatch == null) return false;
        if (phase == Phase.WAITING) {
            phase = Phase.COUNTDOWN;
            secondsLeft = gateSeconds() + 1;
            forced = true;
        }
        closeVoteNow = true;
        returnedAt = 0;                                              // no after-match breather either
        return true;
    }

    /** The map /setmap picked for the next match (null: the vote decides). */
    public VoteOption forced() { return nextMatch; }

    /** /setmap off: the vote decides again. */
    public boolean clearNext() {
        if (nextMatch == null) return false;
        nextMatch = null;
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

    /** Who has /fly switched on (kept while the lobby runs, so it is still on when they come back from a match). */
    private final Set<UUID> flying = new java.util.HashSet<>();

    /** /fly in the lobby: on or off. */
    public void fly(Player player) {
        if (lobby.editing(player)) return;                           // creative flies anyway
        boolean on = !flying.remove(player.getUniqueId());
        if (on) flying.add(player.getUniqueId());
        player.setAllowFlight(on);
        if (!on) player.setFlying(false);
        plugin.messages().send(player, on ? "fly.on" : "fly.off");
    }

    public void leave(Player player) {
        votes.remove(player.getUniqueId());
        plugin.kits().forget(player.getUniqueId());
        board.forget(player);
    }

    /**
     * Moves a hub player. The kit line under their name rides on them, and a teleport can refuse someone who is
     * carrying something: then the line is taken off first (it is back within a second).
     */
    public void move(Player player, Location to) {
        if (player.teleport(to)) return;
        board.unride(player);
        player.eject();
        player.teleport(to);
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
        if (paused) {
            messages.send(player, "vote.paused");
            return;
        }
        if (!votingOpen() || option < 0 || option >= options.size()) {
            messages.send(player, "vote.closed");
            return;
        }
        Integer before = votes.get(player.getUniqueId());
        if (before != null && before != option && !player.hasPermission("ctf.vote.change")) {
            messages.send(player, "perk.vote-change", Messages.arg("map", options.get(before).mapName()));
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
    /** When everyone came back from the last match (0: not yet). */
    private long returnedAt;

    // ---------------------------------------------------------------- every second

    private void second() {
        ticks++;
        poll();
        if (ticks % 30 == 0) refreshMaps(false);
        MatchRow current = row;
        switch (current.state()) {
            case IDLE -> idle(true);
            case REQUESTED, LOADING -> {
                if (locked) {
                    sawLoading = true;
                    lastSeconds(current);
                } else {
                    setPhase(Phase.STARTING);
                    if (System.currentTimeMillis() - current.changed() > STUCK_MILLIS) unstick(current);
                }
            }
            case READY -> {
                if (locked) {
                    sawLoading = true;
                    lastSeconds(current);
                } else {
                    ready(current);
                }
            }
            case PLAYING -> {
                // (also when everyone walked through the gate and the match began before the countdown ran out)
                locked = false;
                sawLoading = false;
                requested = false;
                setPhase(Phase.IN_MATCH);
                lastPlayed = current.map();
            }
            // The match is over and players are on their way back: voting for the next one opens now,
            // and the countdown waits for the game server to finish cleaning up.
            case ENDING -> idle(false);
        }
        portal.set(locked || phase == Phase.STARTING || phase == Phase.IN_MATCH);
        board.updateAll();
        for (Player player : Bukkit.getOnlinePlayers()) items.syncVote(player);
        // A match is on: say so over the hotbar, with how to get in
        if (phase == Phase.IN_MATCH && joinable()) {
            for (Player player : Bukkit.getOnlinePlayers()) if (!lobby.editing(player)) plugin.messages().actionBar(player, "hub.in-match-bar");
        }
    }

    private void idle(boolean gameFree) {
        if (phase == Phase.STARTING && requested) return; // our request hasn't shown up in the row yet
        if (phase == Phase.STARTING || phase == Phase.IN_MATCH) {
            // A match just finished (or never started): back to voting.
            if (phase == Phase.IN_MATCH) returnedAt = System.currentTimeMillis();
            setPhase(Phase.WAITING);
            newVote();
        }
        if (locked) {
            if (sawLoading) {                                        // the game server dropped the match it was loading: a fresh vote
                locked = false;
                sawLoading = false;
                requested = false;
                setPhase(Phase.WAITING);
                newVote();
                return;
            }
            lastSeconds(row);                                        // our request hasn't shown up in the row yet
            return;
        }
        if (options.isEmpty()) newVote();
        int players = 0;                                             // who'd be sent: not those who turned auto-join off
        for (Player player : Bukkit.getOnlinePlayers()) if (joins(player)) players++;
        CtfConfig config = plugin.ctfConfig();
        if (paused) {
            if (phase == Phase.COUNTDOWN) phase = Phase.WAITING;
            return;
        }
        if (phase == Phase.WAITING) {
            if (players >= config.minPlayers() && !options.isEmpty()) {
                phase = Phase.COUNTDOWN;
                secondsLeft = Math.max(config.countdown(), gateSeconds() + 5);
                plugin.messages().broadcast("hub.voting-open", Messages.arg("seconds", secondsLeft - gateSeconds()));
            }
            return;
        }
        // COUNTDOWN: the vote is open. It closes gateSeconds() before the start (or as soon as enough have voted);
        // then the map loads, the gate opens and the last gateSeconds() count down.
        if (players < config.minPlayers() && !forced) {
            phase = Phase.WAITING;
            plugin.messages().broadcast("hub.countdown-cancelled", Messages.arg("needed", config.minPlayers() - players));
            return;
        }
        secondsLeft--;                                               // this second (the sidebar shows the same number as chat)
        int gate = gateSeconds();
        // Just back from a match: everyone gets a breather to vote and pick a kit before the next one can start.
        long breather = plugin.getConfig().getInt("hub.after-match-seconds", 45) * 1000L - (System.currentTimeMillis() - returnedAt);
        if (breather > 0) {
            int left = (int) ((breather + 999) / 1000) + gate;
            if (secondsLeft < left) secondsLeft = left;
            return;
        }
        int voters = 0;
        for (Player player : Bukkit.getOnlinePlayers()) if (!lobby.editing(player) && joins(player)) voters++;
        boolean everyoneVoted = voters > 0 && votes.size() >= voters;
        // Two thirds have voted (hub.quick-start-fraction): no need to wait for the rest.
        double fraction = plugin.getConfig().getDouble("hub.quick-start-fraction", 2.0 / 3.0);
        boolean enoughVoted = voters > 1 && fraction > 0 && votes.size() >= Math.ceil(voters * fraction - 1e-9);
        boolean closeNow = (everyoneVoted || enoughVoted) && plugin.getConfig().getBoolean("hub.open-when-all-voted", true)
                || players >= config.maxPlayers() || closeVoteNow;
        if (closeNow || secondsLeft <= gate) {
            if (!gameFree) {                                         // the game server is still cleaning up: the vote waits
                secondsLeft = Math.max(secondsLeft, gate + 1);
                return;
            }
            secondsLeft = Math.min(Math.max(secondsLeft, 1), gate);
            closeVoteNow = false;
            request(true);
            if (locked) chime();
            return;
        }
        if (secondsLeft == gate + 10) plugin.messages().broadcast("hub.vote-closing", Messages.arg("seconds", 10));
    }

    /** How long the gate is open before the match starts: the vote closes this many seconds before the end. */
    public int gateSeconds() {
        return Math.max(5, plugin.getConfig().getInt("hub.gate-countdown-seconds", 5));
    }

    /** Whether "starts in Ns" is said at this second (hub.countdown-warnings: 30, 10, 5, 4, 3, 2, 1). */
    private boolean warnAt(int seconds) {
        List<Integer> at = plugin.getConfig().getIntegerList("hub.countdown-warnings");
        return seconds > 0 && (at.isEmpty() ? seconds == 30 || seconds == 10 || seconds <= 5 : at.contains(seconds));
    }

    private void chime() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.playerSettings().get(player, com.sasha.ctf.settings.PlayerSettings.Setting.SOUNDS)) player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, secondsLeft <= 3 ? 1.5f : 1f);
        }
    }

    /** The vote is closed and the map is loading: the last seconds run down meanwhile. */
    private void lastSeconds(MatchRow current) {
        secondsLeft--;
        if (secondsLeft > 0) {
            if (warnAt(secondsLeft)) {
                plugin.messages().broadcast("hub.countdown", Messages.arg("seconds", secondsLeft));
                chime();
            }
            return;
        }
        secondsLeft = 0;
        locked = false;
        sawLoading = false;
        if (current.state() == MatchState.READY) ready(current);
        else setPhase(Phase.STARTING);                               // not loaded yet: the old wait, for as long as it still takes
    }

    /**
     * Picks the winning map and the teams and asks the game server to load it: `early` while the countdown still
     * has its last seconds to run (the vote closes there), otherwise at zero.
     */
    private void request(boolean early) {
        forced = false;
        if (options.isEmpty() || plugin.database() == null) {
            plugin.messages().broadcast(plugin.database() == null ? "hub.no-database" : "hub.no-maps");
            phase = Phase.WAITING;
            return;
        }
        boolean picked = nextMatch != null;
        VoteOption winner = picked ? nextMatch : options.get(VoteOption.winner(options.size(), votes, random));
        nextMatch = null;
        chosen = winner;
        List<UUID> players = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) if (joins(player)) players.add(player.getUniqueId());
        // Who is coming (the game server waits for them all). Nobody gets a team here: everyone arrives as a spectator
        // and picks one with the team helmet, so whoever is away from keyboard doesn't end up on a team.
        Map<UUID, TeamColor> roster = TeamBalancer.balance(players, winner.mode().teams(), random, Map.of());
        MatchRow request = new MatchRow(MatchState.REQUESTED, winner.mapId(), winner.mode(), roster, "", null, System.currentTimeMillis());
        // When the lobby will send everyone: the game server doesn't start the match on the first few through the gate before then.
        long sendAt = System.currentTimeMillis() + (early ? Math.max(0, secondsLeft) * 1000L : 0L);
        phase = early ? Phase.COUNTDOWN : Phase.STARTING;
        locked = early;
        sawLoading = false;
        requested = true;
        lastMap = winner.mapId();
        int got = picked ? 0 : votesFor(options.indexOf(winner));
        String key = got == 0 ? "hub.vote-won-none" : got == 1 ? "hub.vote-won-one" : "hub.vote-won";
        if (!picked) plugin.messages().broadcast(key, Messages.arg("map", winner.mapName()), Messages.arg("mode", winner.mode().display()),
                Messages.arg("votes", got));
        if (early) {
            plugin.messages().broadcast("hub.gate-countdown", Messages.arg("seconds", Math.max(1, secondsLeft)));
            // the first number of the countdown ("Teleporting to the match in 5s"); the rest come each second
            if (warnAt(secondsLeft)) plugin.messages().broadcast("hub.countdown", Messages.arg("seconds", secondsLeft));
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.playerSettings().get(player, com.sasha.ctf.settings.PlayerSettings.Setting.SOUNDS)) player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.database().setting("send-at", Long.toString(sendAt));
                if (!plugin.database().moveMatch(MatchState.IDLE, request)) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        requested = false;
                        if (locked) phase = Phase.WAITING;
                        locked = false;
                        plugin.messages().broadcast("hub.game-busy");
                    });
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Couldn't ask the game server for a match", e);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    requested = false;
                    locked = false;
                    phase = Phase.WAITING;
                    plugin.messages().broadcast("hub.no-database");
                });
            }
        });
    }

    /** Whether this player is sent to matches automatically (/settings auto-join). */
    public boolean joins(Player player) {
        return plugin.playerSettings().get(player, com.sasha.ctf.settings.PlayerSettings.Setting.AUTO_JOIN);
    }

    /**
     * /settings hide players: who this player sees in the lobby. Called when they join, when the setting changes, and
     * (for everyone else's setting) when someone joins.
     */
    public void applyHidden(Player player) {
        boolean hide = plugin.playerSettings().get(player, com.sasha.ctf.settings.PlayerSettings.Setting.HIDE_PLAYERS);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) continue;
            if (hide) player.hidePlayer(plugin, other);
            else player.showPlayer(plugin, other);
            if (plugin.playerSettings().get(other, com.sasha.ctf.settings.PlayerSettings.Setting.HIDE_PLAYERS)) other.hidePlayer(plugin, player);
        }
    }

    /** The game server has the map loaded: send everyone over. */
    private void ready(MatchRow current) {
        if (phase == Phase.IN_MATCH) return;
        requested = false;
        locked = false;
        sawLoading = false;
        setPhase(Phase.IN_MATCH);
        plugin.messages().broadcast("hub.sending", Messages.arg("map", mapName(current.map())));
        int delay = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (lobby.editing(player)) continue;                     // whoever is editing the lobby stays to finish
            if (!joins(player)) {                                    // /settings: they stay in the lobby
                plugin.messages().send(player, "settings.stayed");
                continue;
            }
            boolean watch = plugin.playerSettings().get(player, com.sasha.ctf.settings.PlayerSettings.Setting.JOIN_AS_SPECTATOR);
            // A few per tick, so the proxy isn't hit by everyone at once.
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                if (!watch || plugin.database() == null) {
                    Network.send(plugin, player, plugin.ctfConfig().gameServer());
                    return;
                }
                java.util.UUID id = player.getUniqueId();                 // /settings: they arrive as a spectator
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        plugin.database().requestSpectate(id);
                    } catch (SQLException e) {
                        plugin.getLogger().warning("Couldn't mark " + player.getName() + " as watching: " + e.getMessage());
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) Network.send(plugin, player, plugin.ctfConfig().gameServer());
                    });
                });
            }, delay++ / 4);
        }
    }

    /** The game server stopped answering mid-way: free the row so voting can go on. */
    private void unstick(MatchRow current) {
        plugin.getLogger().warning("The game server didn't load " + current.map() + " in time: starting a new vote.");
        requested = false;
        locked = false;
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
        chosen = null;
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
