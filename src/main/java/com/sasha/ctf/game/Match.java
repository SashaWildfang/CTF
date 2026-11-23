/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.game;

import com.sasha.ctf.FractalCTF;
import com.sasha.ctf.config.Messages;
import com.sasha.ctf.data.PlayerStats;
import com.sasha.ctf.kit.Kit;
import org.bukkit.inventory.ItemStack;
import com.sasha.ctf.model.BlockPos;
import com.sasha.ctf.model.MapDef;
import com.sasha.ctf.model.Mode;
import com.sasha.ctf.model.TeamBalancer;
import com.sasha.ctf.model.TeamColor;
import com.sasha.ctf.util.Sidebar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One match on one copy of a map: who's on which team, the scoreboard, the clock, building, and the
 * mode's {@link Rules}. Players keep their team if they leave and come back.
 */
public final class Match {

    public enum Phase { WAITING, COUNTDOWN, PLAYING, OVER }

    private final FractalCTF plugin;
    private final World world;
    private final MapDef map;
    private final Mode mode;
    private final Map<UUID, TeamColor> teams = new LinkedHashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final Map<UUID, PlayerStats> stats = new HashMap<>();
    private final Map<UUID, Long> protectedUntil = new HashMap<>();
    private final Map<UUID, UUID> lastAttacker = new HashMap<>();
    /** Who hurt each player recently, and when: for assists. */
    private final Map<UUID, Map<UUID, Long>> damagers = new HashMap<>();
    private final Set<Long> placed = new HashSet<>();
    /** Everyone gets their own scoreboard (for their own kills and deaths); the teams are the same on all. */
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Map<UUID, Sidebar> sidebars = new HashMap<>();
    private final Map<String, TeamColor> shownTeam = new HashMap<>();
    private final Set<UUID> rostered = new HashSet<>();
    /** Coins earned this match, by reason: reason -> [times, coins]. */
    private final Map<UUID, Map<String, int[]>> earned = new HashMap<>();
    /** Flags captured, wool destroyed and hill time, for the end-of-match top 3. */
    private final Map<UUID, Integer> objectives = new HashMap<>();
    private final Rules rules;
    private int shortHanded;
    /** Players waiting to respawn (seconds left): invisible, flying, no items. */
    private final Map<UUID, Integer> respawning = new HashMap<>();
    /** Where each player died, for the respawn camera. */
    private final Map<UUID, Location> deathSpot = new HashMap<>();
    /** Players who left their team to watch (/spectate). */
    private final Set<UUID> spectators = new HashSet<>();
    /** Spawn protection that lasts while the player stays in their spawn: where they spawned, and when. */
    private final Map<UUID, Location> spawnedAt = new HashMap<>();
    private final Map<UUID, Long> spawnedTime = new HashMap<>();
    /** When each player started playing (for time played). */
    private final Map<UUID, Long> playingSince = new HashMap<>();
    private boolean paused;

    private Phase phase = Phase.WAITING;
    private int countdown;
    private int secondsLeft;

    Match(FractalCTF plugin, World world, MapDef map, Mode mode, Map<UUID, TeamColor> roster) {
        this.plugin = plugin;
        this.world = world;
        this.map = map;
        this.mode = mode;
        // The lobby's roster is only who is coming: everyone picks a team when they get here (TeamPicker).
        this.secondsLeft = map.timeLimitMinutes * 60;
        this.rules = switch (mode) {
            case CTF, CTF4 -> new FlagRules(plugin, this);
            case KOTH -> new HillRules(plugin, this);
            case DTW -> new WoolRules(plugin, this);
        };
        rules.prepare();
    }

    // ---------------------------------------------------------------- what's what

    /** Scoreboard team names sort the tab list: red, blue, green, yellow. */
    static String teamKey(TeamColor team) {
        return team.ordinal() + "_" + team.id();
    }

    /** Gives a player their own scoreboard for this match (teams, sidebar, health under names). */
    Scoreboard attach(Player player) {
        Scoreboard board = boards.computeIfAbsent(player.getUniqueId(), id -> {
            Scoreboard fresh = Bukkit.getScoreboardManager().getNewScoreboard();
            for (TeamColor color : mode.teams()) {
                Team team = fresh.registerNewTeam(teamKey(color));
                team.color(color.text());
                team.prefix(Component.empty());                       // the colour says the team; no [R] before the name
                team.setAllowFriendlyFire(false);
                team.setCanSeeFriendlyInvisibles(true);
            }
            for (Map.Entry<String, TeamColor> entry : shownTeam.entrySet()) fresh.getTeam(teamKey(entry.getValue())).addEntry(entry.getKey());
            // The text under each name is set per player (health, or "Returning to base 4s"), so the objective's own name is blank.
            var health = fresh.registerNewObjective("ctf_health", org.bukkit.scoreboard.Criteria.DUMMY, Component.empty());
            health.setDisplaySlot(org.bukkit.scoreboard.DisplaySlot.BELOW_NAME);
            sidebars.put(id, new Sidebar(fresh, plugin.messages().component("game.sidebar.title", Messages.arg("mode", mode.display()))));
            return fresh;
        });
        player.setScoreboard(board);
        var health = board.getObjective("ctf_health");
        if (health != null) for (Player other : world.getPlayers()) showHealth(other);
        showHealth(player);
        return board;
    }

    /** Text shown under a player's name in place of their health for a while (null: their health again). */
    private final Map<UUID, Component> underName = new HashMap<>();

    public void noteUnderName(Player player, Component note) {
        if (note == null) underName.remove(player.getUniqueId());
        else underName.put(player.getUniqueId(), note);
        showHealth(player);
    }

    /** Health under the name (20 = full, rounded up) on everyone's scoreboard, or a note in its place. */
    void showHealth(Player player) {
        int hearts = hearts(player);
        Component note = underName.get(player.getUniqueId());
        Component text = note != null ? note : plugin.messages().component("game.health-under-name", Messages.arg("health", hearts));
        for (Scoreboard each : boards.values()) {
            var health = each.getObjective("ctf_health");
            if (health == null) continue;
            var score = health.getScore(player.getName());
            score.setScore(hearts);
            score.numberFormat(io.papermc.paper.scoreboard.numbers.NumberFormat.fixed(text));
        }
    }

    /** Health points (20 = full), rounded up. */
    private static int hearts(Player player) {
        return player.isDead() ? 0 : (int) Math.ceil(player.getHealth());
    }

    /** Puts a name under a team on every scoreboard. */
    private void showTeam(String entry, TeamColor team) {
        shownTeam.put(entry, team);
        for (Scoreboard each : boards.values()) {
            Team target = each.getTeam(teamKey(team));
            if (target != null) target.addEntry(entry);
        }
    }

    /** Shows a player under another team's colour (a Spy's disguise), or back under their own. */
    void showAs(Player player, TeamColor team) {
        showTeam(player.getName(), team);
        plugin.chat().refresh(player);
    }

    public boolean paused() { return paused; }

    /** Staff given flight for a pause. */
    private final Set<UUID> staffFlying = new HashSet<>();

    /** /match pause: the clock, the scoring and everyone stop where they are. {@code by}: who did it (null: not known). */
    public boolean pause(boolean pause, String by) {
        if (paused == pause || phase == Phase.OVER) return false;
        paused = pause;
        String who = by == null || by.isBlank() ? plugin.messages().raw("game.staff") : by;
        // Staff move freely while it's paused, and fly; it's taken away again when it goes on.
        for (Player player : world.getPlayers()) {
            if (!player.hasPermission("ctf.admin") || ghost(player.getUniqueId()) || teams.get(player.getUniqueId()) == null) continue;
            if (pause) {
                staffFlying.add(player.getUniqueId());
                player.setAllowFlight(true);
                player.setFlying(true);
            } else if (staffFlying.remove(player.getUniqueId())) {
                player.setFlying(false);
                player.setAllowFlight(false);
            }
        }
        if (!pause) staffFlying.clear();
        broadcast(pause ? "game.paused-by" : "game.resumed-by", Messages.arg("player", who));
        for (Player player : world.getPlayers()) {
            plugin.messages().title(player, pause ? "game.paused-by-title" : "game.resumed-by-title", 5, 40, 10, Messages.arg("player", who));
            player.playSound(player.getLocation(), pause ? Sound.BLOCK_BEACON_DEACTIVATE : Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1f);
        }
        updateSidebar();
        return true;
    }

    public World world() { return world; }
    public MapDef map() { return map; }
    public Mode mode() { return mode; }
    public Phase phase() { return phase; }
    public Rules rules() { return rules; }
    public int secondsLeft() { return secondsLeft; }

    public TeamColor teamOf(UUID player) {
        return teams.get(player);
    }

    /** Players on a team, online in the match world, and not spectating. */
    public List<Player> playing() {
        List<Player> players = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            if (teams.containsKey(player.getUniqueId()) && player.getGameMode() != GameMode.SPECTATOR
                    && !spectators.contains(player.getUniqueId())) players.add(player);
        }
        return players;
    }

    /** Players on a team who are alive right now (not waiting to respawn). */
    public List<Player> alive() {
        List<Player> players = playing();
        players.removeIf(player -> respawning.containsKey(player.getUniqueId()));
        return players;
    }

    public List<Player> online(TeamColor team) {
        List<Player> players = new ArrayList<>();
        for (Player player : world.getPlayers()) if (teams.get(player.getUniqueId()) == team) players.add(player);
        return players;
    }

    public boolean live() {
        return phase == Phase.PLAYING;
    }

    /** Playing with at least two teams on: only then can objectives be taken and coins earned. */
    public boolean contested() {
        return phase == Phase.PLAYING && !stalled;
    }

    /** Whether this block is (part of) an objective: a flag stand, a wool or a hill. */
    public boolean objectiveBlock(BlockPos pos) {
        for (BlockPos objective : rules.objectiveBlocks()) if (objective.equals(pos)) return true;
        return false;
    }

    /** Fewer than two teams have players: the match goes on, but nothing can be scored until someone joins. */
    private boolean stalled;
    /** Seconds played with two teams or more (play and win coins need at least a minute of a real match). */
    private int contestedSeconds;

    public PlayerStats stats(UUID player) {
        return stats.computeIfAbsent(player, id -> new PlayerStats());
    }

    public Location spawn(TeamColor team) {
        var points = map.teams.get(team);
        if (points != null && points.spawn != null) return points.spawn.in(world);
        return world.getSpawnLocation();
    }

    public Location spectatorSpawn() {
        return map.spectatorSpawn != null ? map.spectatorSpawn.in(world) : spawn(mode.teams().get(0));
    }

    // ---------------------------------------------------------------- players

    /** A player arrived on the game server: put them on their team (or the smallest one) and spawn them. */
    void join(Player player) {
        join(player, false);
    }

    /** {@code watch}: the player asked to spectate (from the hub), so they don't get a team. */
    void join(Player player, boolean watch) {
        attach(player);
        names.put(player.getUniqueId(), player.getName());
        hideGhostsFrom(player);
        if (watch && phase != Phase.OVER) {
            TeamColor had = teams.remove(player.getUniqueId());
            if (had != null) rostered.remove(player.getUniqueId());
            startSpectating(player);
            updateSidebar();
            return;
        }
        if (phase == Phase.OVER) {
            player.teleport(spectatorSpawn());
            afterMatch(player);
            return;
        }
        intro(player);
        TeamColor team = teams.get(player.getUniqueId());
        if (team == null) {
            arriveWithoutTeam(player);
            return;
        }
        // Back after leaving: on their team again. Joined after the start (not sent by the vote): plays this match with the default kit.
        if (!rostered.contains(player.getUniqueId())) plugin.kits().forceDefault(player.getUniqueId());
        spectators.remove(player.getUniqueId());
        showTeam(player.getName(), team);
        plugin.chat().refresh(player);
        plugin.messages().send(player, "game.joined", Messages.comp("team", teamName(team)),
                Messages.arg("map", map.name), Messages.arg("mode", mode.display()));
        if (!map.authors.isEmpty()) plugin.messages().send(player, "game.map-by", Messages.arg("authors", String.join(", ", map.authors)));
        TeamColor finalTeam = team;
        boolean preview = phase == Phase.WAITING || phase == Phase.COUNTDOWN;
        plugin.kits().load(player.getUniqueId(), () -> {
            if (!rostered.contains(player.getUniqueId())) plugin.kits().forceDefault(player.getUniqueId());
            if (player.isOnline() && world.equals(player.getWorld()) && live()) respawnNow(player, finalTeam);
        });
        if (preview) {
            // Before the start everyone flies around to look at the map; kits come when it starts.
            player.teleport(spectatorSpawn());
            afterMatch(player);
            plugin.messages().send(player, "game.preview", Messages.arg("seconds", plugin.ctfConfig().startCountdown()));
        } else {
            player.teleport(spawn(team));
            player.setGameMode(GameMode.SURVIVAL);
            playingSince.put(player.getUniqueId(), System.currentTimeMillis());
        }
        updateSidebar();
    }

    /**
     * Someone arrived with no team (everyone does, unless they were on one and come back): before the start they look
     * round the map with the team helmet; once it has started they spectate, and the helmet is on their hotbar there too.
     */
    private void arriveWithoutTeam(Player player) {
        intro(player);
        plugin.kits().load(player.getUniqueId(), () -> { });
        spectators.remove(player.getUniqueId());
        plugin.chat().refresh(player);
        if (phase == Phase.WAITING || phase == Phase.COUNTDOWN) {
            player.teleport(spectatorSpawn());
            afterMatch(player);
            plugin.game().teamPicker().give(player);
            plugin.messages().send(player, "game.pick-team", Messages.arg("map", map.name), Messages.arg("mode", mode.display()));
            plugin.messages().title(player, "game.pick-team-title", 5, 60, 10);
        } else {
            startSpectating(player);
            plugin.messages().send(player, "game.pick-team-live", Messages.arg("map", map.name), Messages.arg("mode", mode.display()));
        }
        updateSidebar();
    }

    /**
     * Joins a team (team null: the one that needs players most). Before the start they stay looking round and get
     * their kit when it begins; during the match they come out of spectating and spawn (with the default kit, as
     * anyone who joins late does). A team can't be picked when it already has more players than the smallest one.
     */
    void choose(Player player, TeamColor wanted) {
        UUID id = player.getUniqueId();
        var messages = plugin.messages();
        if (phase == Phase.OVER) {
            messages.send(player, "team.no-match");
            return;
        }
        boolean before = phase == Phase.WAITING || phase == Phase.COUNTDOWN;
        TeamColor current = teams.get(id);
        if (current != null && !before && !spectators.contains(id)) {
            messages.send(player, "team.already-playing", Messages.comp("team", teamName(current)));
            return;
        }
        Map<UUID, TeamColor> others = onlineTeams();
        others.remove(id);
        for (UUID watcher : spectators) others.remove(watcher);
        TeamColor team = wanted != null ? wanted : TeamBalancer.smallest(others, mode.teams());
        if (wanted != null) {
            Map<TeamColor, Integer> sizes = new java.util.EnumMap<>(TeamColor.class);
            for (TeamColor t : mode.teams()) sizes.put(t, 0);
            for (TeamColor t : others.values()) sizes.merge(t, 1, Integer::sum);
            int smallest = java.util.Collections.min(sizes.values());
            if (sizes.getOrDefault(wanted, 0) > smallest) {
                messages.send(player, "team.full-now", Messages.comp("team", teamName(wanted)));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
                return;
            }
        }
        if (team == current && !spectators.contains(id)) {
            messages.send(player, "team.already-on", Messages.comp("team", teamName(team)));
            return;
        }
        if (current != null) {
            for (Scoreboard each : boards.values()) {
                Team old = each.getTeam(teamKey(current));
                if (old != null) old.removeEntry(player.getName());
            }
        }
        teams.put(id, team);
        showTeam(player.getName(), team);
        plugin.chat().refresh(player);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.3f);
        plugin.messages().title(player, "team.joined-title", 5, 40, 10, Messages.comp("team", teamName(team)));
        if (before) {
            rostered.add(id);                                        // here for the start: plays with the kit they picked
            messages.send(player, "team.joined", Messages.comp("team", teamName(team)), Messages.arg("seconds", Math.max(0, countdown)));
        } else {
            spectators.remove(id);
            unghost(player);
            plugin.kits().forceDefault(id);
            messages.send(player, "spectate.joined", Messages.comp("team", teamName(team)));
            broadcast("team.joined-late", Messages.comp("player", Component.text(player.getName(), team.text())), Messages.comp("team", teamName(team)));
            playingSince.put(id, System.currentTimeMillis());
            respawnNow(player, team);
        }
        updateSidebar();
    }

    private final Set<UUID> introduced = new HashSet<>();

    /** How the mode is played, once per player per match, laid out like the end screen. */
    void intro(Player player) {
        if (!introduced.add(player.getUniqueId()) || !plugin.getConfig().getBoolean("game.intro", true)) return;
        var messages = plugin.messages();
        String key = switch (mode) {
            case CTF, CTF4 -> "ctf";
            case KOTH -> "koth";
            case DTW -> "dtw";
        };
        var args = new net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[] {
                Messages.arg("limit", mode == Mode.KOTH ? map.kothPoints : map.captureLimit),
                Messages.arg("seconds", map.captureSeconds), Messages.arg("return", plugin.ctfConfig().flagReturn()),
                Messages.arg("wool", plugin.getConfig().getInt("game.wool-to-break", 10)), Messages.arg("teams", mode.teams().size()),
                Messages.arg("minutes", map.timeLimitMinutes)};
        List<Component> out = new ArrayList<>();
        out.add(messages.component("end.line"));
        out.add(center(messages.component("intro.title", Messages.arg("mode", mode.display()))));
        out.add(center(messages.component("intro.map", Messages.arg("map", map.name),
                Messages.arg("authors", map.authors.isEmpty() ? "Fractal" : String.join(", ", map.authors)))));
        out.add(Component.empty());
        // The rules read top to bottom, left-aligned (centring wrapped lines left them ragged); short lines, no wrapping.
        for (Component line : messages.lines("intro." + key, args)) out.add(Component.text(" ").append(line));
        out.add(Component.empty());
        for (Component line : messages.lines("intro.always", args)) out.add(center(line));
        out.add(messages.component("end.line"));
        for (Component line : out) player.sendMessage(line);
    }

    /** Who still has no team (before the start): reminded every second. */
    private void remindToPick() {
        for (Player player : world.getPlayers()) {
            if (!teams.containsKey(player.getUniqueId()) && !spectators.contains(player.getUniqueId())) {
                plugin.messages().actionBar(player, "game.pick-team-bar");
            }
        }
    }

    /** The teams of the players who are online right now (for balancing late joiners). */
    private Map<UUID, TeamColor> onlineTeams() {
        Map<UUID, TeamColor> online = new HashMap<>();
        for (Player player : world.getPlayers()) {
            TeamColor team = teams.get(player.getUniqueId());
            if (team != null) online.put(player.getUniqueId(), team);
        }
        return online;
    }

    void quit(Player player) {
        if (live() && teams.containsKey(player.getUniqueId()) && !respawning.containsKey(player.getUniqueId())) {
            rules.onDeathOrQuit(player, player.getLocation());
        }
        stopClock(player.getUniqueId());
        respawning.remove(player.getUniqueId());
        spectators.remove(player.getUniqueId());
        player.setInvisible(false);
        plugin.kits().forget(player.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, this::rebalance, 2L);
    }

    private void stopClock(UUID id) {
        Long since = playingSince.remove(id);
        if (since != null) stats(id).playSeconds += (int) ((System.currentTimeMillis() - since) / 1000);
    }

    // ---------------------------------------------------------------- spectating (/spectate)

    public boolean spectating(UUID player) {
        return spectators.contains(player);
    }

    /** /spectate: leaves the team (dropping any flag) and watches with the spectator tools. */
    public void leaveToSpectate(Player player) {
        UUID id = player.getUniqueId();
        if (spectators.contains(id)) return;
        TeamColor team = teams.remove(id);
        if (team != null) {
            if (live() && !respawning.containsKey(id)) rules.onDeathOrQuit(player, player.getLocation());
            stopClock(id);
            shownTeam.remove(player.getName());
            for (Scoreboard each : boards.values()) {
                Team t = each.getTeam(teamKey(team));
                if (t != null) t.removeEntry(player.getName());
            }
        }
        respawning.remove(id);
        startSpectating(player);
        plugin.chat().refresh(player);
        if (team != null) broadcast("spectate.left-team", Messages.comp("player", Component.text(player.getName(), team.text())));
        updateSidebar();
        rebalance();
    }

    /**
     * Evens the teams out when one has two or more players more than another (someone went to spectate or
     * left): a player from the biggest team, never a flag carrier, moves to the smallest and respawns there.
     */
    void rebalance() {
        if (phase == Phase.OVER || !plugin.getConfig().getBoolean("game.auto-balance", true)) return;
        for (int round = 0; round < 8; round++) {
            Map<TeamColor, List<Player>> members = new java.util.EnumMap<>(TeamColor.class);
            for (TeamColor team : mode.teams()) members.put(team, new ArrayList<>());
            for (Player player : world.getPlayers()) {
                TeamColor team = teams.get(player.getUniqueId());
                if (team != null && !spectators.contains(player.getUniqueId()) && members.containsKey(team)) members.get(team).add(player);
            }
            TeamColor big = null, small = null;
            for (TeamColor team : mode.teams()) {
                if (big == null || members.get(team).size() > members.get(big).size()) big = team;
                if (small == null || members.get(team).size() < members.get(small).size()) small = team;
            }
            if (big == null || members.get(big).size() - members.get(small).size() < 2) return;
            List<Player> movable = new ArrayList<>();
            for (Player player : members.get(big)) if (!rules.carrying(player)) movable.add(player);
            if (movable.isEmpty()) return;
            // the one who joined last moves (they have had the least time on their team)
            Player moving = movable.get(0);
            for (Player player : movable) {
                if (playingSince.getOrDefault(player.getUniqueId(), 0L) > playingSince.getOrDefault(moving.getUniqueId(), 0L)) moving = player;
            }
            moveTo(moving, big, small);
        }
    }

    private void moveTo(Player player, TeamColor from, TeamColor to) {
        teams.put(player.getUniqueId(), to);
        for (Scoreboard each : boards.values()) {
            Team old = each.getTeam(teamKey(from));
            if (old != null) old.removeEntry(player.getName());
        }
        showTeam(player.getName(), to);
        plugin.chat().refresh(player);
        broadcast("game.balanced", Messages.comp("player", Component.text(player.getName(), to.text())),
                Messages.comp("from", teamName(from)), Messages.comp("team", teamName(to)));
        plugin.messages().title(player, "game.balanced-title", 5, 50, 10, Messages.comp("team", teamName(to)));
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
        if (live() && !respawning.containsKey(player.getUniqueId())) respawnNow(player, to);
        else if (!live()) player.teleport(spectatorSpawn());
        updateSidebar();
    }

    private void startSpectating(Player player) {
        spectators.add(player.getUniqueId());
        ghostBody(player);
        player.teleport(spectatorSpawn());
        plugin.game().spectatorTools().give(player);
        plugin.messages().send(player, "spectate.now");
    }

    /** From spectating back onto a team: the team menu. */
    public void playAgain(Player player) {
        if (!spectators.contains(player.getUniqueId()) || phase == Phase.OVER) return;
        plugin.game().teamPicker().open(player, this);
    }

    /** From spectating back onto the team with the fewest players (no menu). */
    public void playAgainNow(Player player) {
        UUID id = player.getUniqueId();
        if (!spectators.remove(id) || phase == Phase.OVER) return;
        TeamColor team = TeamBalancer.smallest(onlineTeams(), mode.teams());
        teams.put(id, team);
        plugin.kits().forceDefault(id);
        showTeam(player.getName(), team);
        plugin.chat().refresh(player);
        unghost(player);
        plugin.messages().send(player, "spectate.joined", Messages.comp("team", teamName(team)));
        if (live()) {
            playingSince.put(id, System.currentTimeMillis());
            respawnNow(player, team);
        } else {
            player.teleport(spectatorSpawn());
            afterMatch(player);
        }
        updateSidebar();
    }

    // ---------------------------------------------------------------- dead players waiting to respawn

    public boolean ghost(UUID player) {
        return respawning.containsKey(player) || spectators.contains(player);
    }

    public boolean respawning(UUID player) {
        return respawning.containsKey(player);
    }

    /** Where a dead player's camera starts (where they died, or above their spawn if it was the void). */
    Location ghostSpot(Player player) {
        Location died = deathSpot.remove(player.getUniqueId());
        TeamColor team = teamOf(player.getUniqueId());
        if (died == null || died.getY() < map.voidY + 4) {
            Location base = team == null ? spectatorSpawn() : spawn(team);
            return base.clone().add(0, 8, 0);
        }
        return died.clone().add(0, 2, 0);
    }

    /** A player died: they watch for a few seconds, then respawn. */
    void startRespawn(Player player) {
        int seconds = plugin.ctfConfig().respawnSeconds();
        if (seconds <= 0 || !live()) {
            TeamColor team = teamOf(player.getUniqueId());
            if (team != null && live()) respawnNow(player, team);
            return;
        }
        respawning.put(player.getUniqueId(), seconds);
        ghostBody(player);
        respawnTitle(player, seconds);
    }

    private void respawnTitle(Player player, int seconds) {
        plugin.messages().title(player, "game.respawn", 0, 25, 5, Messages.arg("seconds", seconds));
    }

    /** Invisible to everyone, flying, nothing in hand, can't touch anything. */
    private void ghostBody(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.setItemOnCursor(null);
        for (var effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
        player.setFireTicks(0);
        player.setGlowing(false);
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setCollidable(false);
        player.setInvisible(true);
        var max = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        player.setHealth(max == null ? 20 : max.getValue());
    }

    private void unghost(Player player) {
        player.setInvisible(false);
        player.setCollidable(true);
        player.setAllowFlight(false);
        player.setFlying(false);
    }

    /** Ghosts are invisible (an entity flag), so newcomers need nothing extra; kept for clarity. */
    private void hideGhostsFrom(Player viewer) {
        viewer.setInvisible(ghost(viewer.getUniqueId()));
    }

    /** Once a second: counts the dead down and brings them back. */
    private void tickRespawns() {
        for (var it = respawning.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !world.equals(player.getWorld())) {
                it.remove();
                continue;
            }
            int left = entry.getValue() - 1;
            if (left > 0) {
                entry.setValue(left);
                respawnTitle(player, left);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.2f);
                continue;
            }
            it.remove();
            TeamColor team = teamOf(player.getUniqueId());
            if (team == null) continue;
            unghost(player);
            player.clearTitle();
            Location torch = plugin.game().abilities().respawnSpot(player, this);
            player.teleport(torch != null ? torch : spawn(team));
            equip(player, team);
            showHealth(player);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.6f);
        }
    }

    /** Puts a player back at their spawn with their kit (after joining or dying). */
    void respawnNow(Player player, TeamColor team) {
        respawning.remove(player.getUniqueId());
        if (player.isInvisible()) unghost(player);
        player.teleport(spawn(team));
        equip(player, team);
    }

    void equip(Player player, TeamColor team) {
        if (mode != Mode.CTF && mode != Mode.CTF4) {
            // CTF hides players from the locator bar (its dots are the flags); the other modes show them as usual.
            var range = player.getAttribute(org.bukkit.attribute.Attribute.WAYPOINT_TRANSMIT_RANGE);
            if (range != null && range.getBaseValue() == 0) range.setBaseValue(range.getDefaultValue());
        }
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setGlowing(false);
        Kit kit = plugin.kits().of(player.getUniqueId());
        plugin.kits().equip(player, team);
        protectedUntil.put(player.getUniqueId(), System.currentTimeMillis() + plugin.ctfConfig().spawnProtection() * 1000L);
        spawnedAt.put(player.getUniqueId(), player.getLocation());
        spawnedTime.put(player.getUniqueId(), System.currentTimeMillis());
        if (phase == Phase.COUNTDOWN || phase == Phase.WAITING) plugin.messages().actionBar(player, "game.your-kit", Messages.arg("kit", kit.display()));
    }

    /**
     * Can't be hurt: for a few seconds after spawning, and for as long as they stay in their spawn after
     * that (up to 20 seconds), so nobody can camp a spawn. Attacking or walking out ends it.
     */
    public boolean spawnProtected(Player player) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (protectedUntil.getOrDefault(id, 0L) > now) return true;
        Location at = spawnedAt.get(id);
        if (at == null) return false;
        int radius = Math.max(3, plugin.ctfConfig().spawnProtectRadius());
        if (!at.getWorld().equals(player.getWorld()) || at.distanceSquared(player.getLocation()) > radius * radius
                || now - spawnedTime.getOrDefault(id, 0L) > 20_000) {
            spawnedAt.remove(id);
            return false;
        }
        return true;
    }

    void hit(Player victim, Player attacker) {
        lastAttacker.put(victim.getUniqueId(), attacker.getUniqueId());
        damagers.computeIfAbsent(victim.getUniqueId(), id -> new HashMap<>()).put(attacker.getUniqueId(), System.currentTimeMillis());
        protectedUntil.remove(attacker.getUniqueId()); // attacking ends your own spawn protection
        spawnedAt.remove(attacker.getUniqueId());
    }

    /** How someone died, for the kill message. */
    public enum Cause { MELEE, ARROW, VOID, FALL, FIRE, EXPLOSION, MAGIC, UNDEAD, OTHER }

    /** When a player was last hit by an arrow: shooter, distance and time (for "shot off the edge"). */
    private final Map<UUID, Object[]> lastArrow = new HashMap<>();
    private final Map<UUID, Integer> streaks = new HashMap<>();
    private boolean firstBlood;
    private boolean shotOff;
    private final java.util.Random random = new java.util.Random();

    void shot(Player victim, Player shooter, double distance) {
        lastArrow.put(victim.getUniqueId(), new Object[] {shooter.getUniqueId(), distance, System.currentTimeMillis()});
    }

    void died(Player victim, Player killer) {
        died(victim, killer, Cause.OTHER, 0);
    }

    /** A player died: count it, credit the killer, and tell everyone how. */
    void died(Player victim, Player killer, Cause cause, double distance) {
        deathSpot.put(victim.getUniqueId(), victim.getLocation());
        Object[] arrow = lastArrow.remove(victim.getUniqueId());
        if (arrow != null && System.currentTimeMillis() - (Long) arrow[2] < 6000 && (cause == Cause.VOID || cause == Cause.FALL)) {
            // shot off a ledge: the archer gets it, and it counts as a bow kill
            Player shooter = Bukkit.getPlayer((UUID) arrow[0]);
            if (shooter != null && (killer == null || killer.equals(shooter))) {
                killer = shooter;
                distance = (Double) arrow[1];
                cause = cause == Cause.VOID ? Cause.VOID : Cause.ARROW;
                shotOff = true;
            }
        }
        UUID last = lastAttacker.remove(victim.getUniqueId());
        if (killer == null && last != null) {
            // A fall, the void or fire after a fight: the last enemy to hurt them (with a hit, an arrow, a turret,
            // TNT, undead, a slam...) gets the kill, if it was recent enough.
            Long when = damagers.getOrDefault(victim.getUniqueId(), Map.of()).get(last);
            int window = Math.max(plugin.ctfConfig().coins().assistSeconds(), plugin.getConfig().getInt("game.kill-credit-seconds", 20));
            if (when != null && System.currentTimeMillis() - when <= window * 1000L) {
                killer = Bukkit.getPlayer(last);
            }
        }
        if (live()) {
            stats(victim.getUniqueId()).deaths++;
            rules.onDeathOrQuit(victim, victim.getLocation());
        }
        TeamColor victimTeam = teamOf(victim.getUniqueId());
        Component victimName = victimTeam == null ? victim.name() : Component.text(victim.getName(), victimTeam.text());
        // Everyone else who hurt them recently gets an assist.
        List<Player> assists = new ArrayList<>();
        Map<UUID, Long> hurtBy = damagers.remove(victim.getUniqueId());
        long since = System.currentTimeMillis() - plugin.ctfConfig().coins().assistSeconds() * 1000L;
        if (hurtBy != null) {
            for (Map.Entry<UUID, Long> entry : hurtBy.entrySet()) {
                if (entry.getValue() < since || killer != null && entry.getKey().equals(killer.getUniqueId())) continue;
                Player helper = Bukkit.getPlayer(entry.getKey());
                if (helper != null && !helper.equals(victim) && teamOf(helper.getUniqueId()) != victimTeam) assists.add(helper);
            }
        }
        streaks.remove(victim.getUniqueId());
        boolean shotOffNow = shotOff;
        shotOff = false;
        if (killer != null && !killer.equals(victim)) {
            TeamColor killerTeam = teamOf(killer.getUniqueId());
            Component killerName = killerTeam == null ? killer.name() : Component.text(killer.getName(), killerTeam.text());
            int range = (int) Math.round(distance);
            if (live()) {
                PlayerStats mine = stats(killer.getUniqueId());
                mine.kills++;
                if (cause == Cause.ARROW || shotOffNow) {
                    mine.bowKills++;
                    mine.longestShot = Math.max(mine.longestShot, range);
                }
                if (cause == Cause.VOID) mine.voidKills++;
                award(killer, plugin.ctfConfig().coins().kill(), "kill", Messages.comp("victim", victimName));
                rewardKill(killer);
            }
            boolean longShot = (cause == Cause.ARROW || cause == Cause.VOID && shotOffNow) && range >= plugin.ctfConfig().longShot();
            String key = switch (cause) {
                case ARROW -> shotOffNow ? "shot-off" : longShot ? "sniped" : "shot";
                case VOID -> shotOffNow ? (longShot ? "sniped-void" : "shot-void") : "void";
                case FALL -> "fall";
                case FIRE -> "fire";
                case EXPLOSION -> "explosion";
                case MAGIC -> "magic";
                case UNDEAD -> "undead";
                default -> "melee";
            };
            List<String> variants = plugin.messages().list("game.kill." + key);
            String line = variants.isEmpty() ? plugin.messages().raw("game.killed") : variants.get(random.nextInt(variants.size()));
            Component message = plugin.messages().parse(line, Messages.comp("victim", victimName), Messages.comp("killer", killerName),
                    Messages.arg("distance", range));
            if (!assists.isEmpty()) {
                List<String> helperNames = new ArrayList<>();
                for (Player helper : assists) helperNames.add(helper.getName());
                message = message.append(plugin.messages().component("game.kill.assist-suffix", Messages.arg("assists", String.join(", ", helperNames))));
            }
            broadcastComponent(message);
            killer.playSound(killer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.6f);
            if (longShot) {
                plugin.messages().title(killer, "game.kill.long-shot-title", 0, 30, 10, Messages.arg("distance", range));
                killer.playSound(killer.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 1f, 0.6f);
            }
            if (live()) {
                if (!firstBlood) {
                    firstBlood = true;
                    broadcast("game.kill.first-blood", Messages.comp("killer", killerName));
                    award(killer, plugin.getConfig().getInt("coins.first-blood", 3), "first-blood");
                }
                int streak = streaks.merge(killer.getUniqueId(), 1, Integer::sum);
                stats(killer.getUniqueId()).bestStreak = Math.max(stats(killer.getUniqueId()).bestStreak, streak);
                String streakKey = switch (streak) {
                    case 3 -> "game.kill.streak-3";
                    case 5 -> "game.kill.streak-5";
                    case 7 -> "game.kill.streak-7";
                    case 10 -> "game.kill.streak-10";
                    default -> streak > 10 && streak % 5 == 0 ? "game.kill.streak-more" : null;
                };
                if (streakKey != null) {
                    broadcast(streakKey, Messages.comp("killer", killerName), Messages.arg("streak", streak));
                    for (Player player : world.getPlayers()) player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.6f, 1.4f);
                }
            }
        } else {
            String key = switch (cause) {
                case VOID -> "game.died-void";
                case FALL -> "game.died-fall";
                case FIRE -> "game.died-fire";
                default -> "game.died";
            };
            broadcast(key, Messages.comp("victim", victimName));
        }
        if (live()) {
            for (Player helper : assists) {
                stats(helper.getUniqueId()).assists++;
                award(helper, plugin.ctfConfig().coins().assist(), "assist", Messages.comp("victim", victimName));
            }
        }
    }

    /** A kill earns golden apples (eating one starts a cooldown, see GameListener). */
    private void rewardKill(Player killer) {
        int apples = plugin.ctfConfig().killApples();
        if (apples <= 0) return;
        // Onto the kit's own golden apples, so they stack.
        var inventory = killer.getInventory();
        int left = apples;
        ItemStack template = null;
        for (int slot = 0; slot < 36 && left > 0; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() != org.bukkit.Material.GOLDEN_APPLE) continue;
            if (template == null) template = item;
            int room = item.getMaxStackSize() - item.getAmount();
            int add = Math.min(room, left);
            item.setAmount(item.getAmount() + add);
            left -= add;
        }
        if (left > 0) {
            ItemStack extra = template != null ? template.clone()
                    : com.sasha.ctf.util.Items.layoutKey(ItemStack.of(org.bukkit.Material.GOLDEN_APPLE), "gapple");
            extra.setAmount(left);
            if (!inventory.addItem(extra).isEmpty()) return;
        }
        plugin.messages().actionBar(killer, "game.kill.apple", Messages.arg("apples", apples));
    }

    private void broadcastComponent(Component message) {
        for (Player player : world.getPlayers()) player.sendMessage(message);
        Bukkit.getConsoleSender().sendMessage(message);
    }

    /**
     * Gives coins for something done in this match (saved with the stats at the end). The reason is a
     * key under coins.reason in messages.yml; its placeholders come from {@code args}.
     */
    public void award(Player player, int coins, String reason, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... args) {
        if (reason.equals("capture") || reason.equals("wool") || reason.equals("hill")) {
            objectives.merge(player.getUniqueId(), 1, Integer::sum);
        }
        if (coins <= 0 || stalled) return;                        // nothing is earned while only one team is here
        int boost = plugin.boosters().multiplier();                  // a coin booster from the store multiplies everything earned
        coins *= boost;
        stats(player.getUniqueId()).coins += coins;
        stats(player.getUniqueId()).coinsEarned += coins;
        int[] tally = earned.computeIfAbsent(player.getUniqueId(), id -> new LinkedHashMap<>()).computeIfAbsent(reason, r -> new int[2]);
        tally[0]++;
        tally[1] += coins;
        if (reason.equals("play") || reason.equals("win")) return; // shown in the end-of-match summary
        plugin.messages().send(player, boost > 1 ? "coins.earned-boosted" : "coins.earned", Messages.arg("coins", coins), Messages.arg("multiplier", boost),
                Messages.comp("reason", plugin.messages().component("coins.reason." + reason, args)));
    }

    private TeamColor winner;

    /** Objectives a player did this match: flags, wool and hill points. */
    private int objectivesOf(UUID id) {
        return objectives.getOrDefault(id, 0);
    }

    /**
     * The end screen in chat, laid out like the big servers do it: the result, the top 3, your own numbers
     * and your coins, then how to rate the map.
     */
    private void summaries() {
        Messages messages = plugin.messages();
        List<UUID> ranked = new ArrayList<>();
        for (UUID id : stats.keySet()) if (names.containsKey(id) && (stats(id).kills > 0 || objectivesOf(id) > 0)) ranked.add(id);
        ranked.sort((a, b) -> {
            int score = Integer.compare(stats(b).kills + 2 * objectivesOf(b), stats(a).kills + 2 * objectivesOf(a));
            return score != 0 ? score : Integer.compare(stats(b).kills, stats(a).kills);
        });
        List<Component> shared = new ArrayList<>();
        Component rule = messages.component("end.line");
        shared.add(rule);
        shared.add(center(messages.component("end.title", Messages.arg("mode", mode.display()))));
        shared.add(center(messages.component("end.map", Messages.arg("map", map.name))));
        shared.add(Component.empty());
        shared.add(center(winner == null ? messages.component("end.draw")
                : messages.component("end.winner", Messages.comp("team", teamName(winner)), Messages.arg("score", plainScore()))));
        shared.add(Component.empty());
        if (ranked.isEmpty()) shared.add(center(messages.component("end.top-none")));
        for (int i = 0; i < Math.min(3, ranked.size()); i++) {
            UUID id = ranked.get(i);
            TeamColor team = teamOf(id);
            String name = names.getOrDefault(id, "?");
            shared.add(center(messages.component("end.place-" + (i + 1),
                    Messages.comp("player", team == null ? Component.text(name) : Component.text(name, team.text())),
                    Messages.arg("kills", stats(id).kills), Messages.arg("objectives", objectivesOf(id)))));
        }
        shared.add(Component.empty());
        for (Player player : world.getPlayers()) {
            UUID id = player.getUniqueId();
            List<Component> out = new ArrayList<>(shared);
            TeamColor team = teamOf(id);
            boolean played = stats.containsKey(id) && (team != null || stats(id).playSeconds > 0);
            out.add(center(messages.component(team == null ? "end.result-watched" : winner == null ? "end.result-draw"
                    : team == winner ? "end.result-win" : "end.result-loss")));
            if (played) {
                var numbers = stats(id);
                out.add(center(messages.component("end.you", Messages.arg("kills", numbers.kills), Messages.arg("deaths", numbers.deaths),
                        Messages.arg("assists", numbers.assists), Messages.arg("objectives", objectivesOf(id)))));
                out.add(rule);
                Map<String, int[]> mine = earned.getOrDefault(id, Map.of());
                int total = 0;
                for (int[] tally : mine.values()) total += tally[1];
                out.add(center(messages.component("end.rewards")));
                out.add(center(messages.component("end.rewards-total", Messages.arg("coins", total))));
                for (Map.Entry<String, int[]> entry : mine.entrySet()) {
                    out.add(center(messages.component("end.rewards-line", Messages.arg("coins", entry.getValue()[1]),
                            Messages.arg("times", entry.getValue()[0]), Messages.comp("label", messages.component("coins.label." + entry.getKey())))));
                }
            }
            out.add(rule);
            out.add(center(messages.component("end.rate", Messages.arg("map", map.name))));
            for (Component line : out) player.sendMessage(line);
        }
    }

    private static Component center(Component line) {
        return com.sasha.ctf.util.ChatCenter.center(line);
    }

    /**
     * After the match: no items, no fighting, free to fly around and watch the celebration until
     * everyone is sent back to the hub.
     */
    void afterMatch(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.setItemOnCursor(null);
        for (var effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
        player.setFireTicks(0);
        player.setGlowing(false);
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setCollidable(false);
    }

    // ---------------------------------------------------------------- building

    public boolean placedByPlayer(BlockPos pos) {
        return placed.contains(pos.key());
    }

    void markPlaced(BlockPos pos) {
        placed.add(pos.key());
    }

    void unmarkPlaced(BlockPos pos) {
        placed.remove(pos.key());
    }

    /** Map blocks near a spawn, a flag, wool, the hill or a beacon can't be broken. */
    boolean mapProtected(BlockPos pos) {
        int spawnRadius = plugin.ctfConfig().spawnProtectRadius(), objectiveRadius = plugin.ctfConfig().objectiveProtectRadius();
        for (TeamColor team : mode.teams()) {
            var points = map.teams.get(team);
            if (points == null) continue;
            if (points.spawn != null) {
                double dx = points.spawn.x() - (pos.x() + 0.5), dy = points.spawn.y() - pos.y(), dz = points.spawn.z() - (pos.z() + 0.5);
                if (dx * dx + dy * dy + dz * dz <= (spawnRadius + 0.5) * (spawnRadius + 0.5)) return true;
            }
            List<BlockPos> near = new ArrayList<>(points.wools);
            if (points.flag != null) near.add(points.flag);
            for (BlockPos objective : near) if (close(objective, pos, objectiveRadius)) return true;
        }
        for (BlockPos objective : rules.objectiveBlocks()) if (close(objective, pos, objectiveRadius)) return true;
        if (mode == Mode.KOTH) for (var point : map.allHills()) {
            var hill = point.area();
            if (pos.x() >= hill.min().x() - 2 && pos.x() <= hill.max().x() + 2 && pos.z() >= hill.min().z() - 2
                    && pos.z() <= hill.max().z() + 2 && pos.y() >= hill.min().y() - 6 && pos.y() <= hill.max().y() + 4) return true;
        }
        return false;
    }

    private static boolean close(BlockPos a, BlockPos b, int radius) {
        int dx = a.x() - b.x(), dy = a.y() - b.y(), dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    /** Nobody may build this close to a spawn, flag stand or wool. */
    boolean nearObjective(BlockPos pos) {
        int radius = plugin.ctfConfig().protectedRadius();
        if (radius <= 0) return false;
        double limit = (radius + 0.5) * (radius + 0.5);
        for (TeamColor team : mode.teams()) {
            var points = map.teams.get(team);
            if (points != null && points.spawn != null) {
                double dx = points.spawn.x() - (pos.x() + 0.5), dy = points.spawn.y() - pos.y(), dz = points.spawn.z() - (pos.z() + 0.5);
                if (dx * dx + dy * dy + dz * dz <= limit) return true;
            }
        }
        for (BlockPos objective : rules.objectiveBlocks()) {
            int dx = objective.x() - pos.x(), dy = objective.y() - pos.y(), dz = objective.z() - pos.z();
            if (dx * dx + dy * dy + dz * dz <= limit) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- the clock

    void beginCountdown(int seconds) {
        phase = Phase.COUNTDOWN;
        countdown = seconds;
        rules.start();
        broadcast("game.get-ready", Messages.arg("seconds", seconds));
    }

    /** Once a second. Returns the winner (or a "draw" marker) when the match ends this second. */
    Result second() {
        if (paused) {
            for (Player player : world.getPlayers()) plugin.messages().actionBar(player, "game.paused-bar");
            updateSidebar();
            return null;
        }
        tickRespawns();
        switch (phase) {
            case OVER -> {
                return null;
            }
            case WAITING -> {
                remindToPick();
                return null;
            }
            case COUNTDOWN -> {
                countdown--;                                         // this second (the sidebar shows the same number as chat)
                if (countdown <= 0) {
                    phase = Phase.PLAYING;
                    // Nobody picked a team for them: they watch (away from keyboard, or just looking). /jointeam still works.
                    for (Player player : world.getPlayers()) {
                        UUID id = player.getUniqueId();
                        if (teams.containsKey(id) || spectators.contains(id)) continue;
                        startSpectating(player);
                        plugin.messages().send(player, "game.no-team-spectating");
                    }
                    for (Player player : playing()) {
                        if (spectators.contains(player.getUniqueId())) continue;
                        player.setCollidable(true);
                        respawnNow(player, teamOf(player.getUniqueId()));
                        playingSince.put(player.getUniqueId(), System.currentTimeMillis());
                    }
                    for (Player player : world.getPlayers()) {
                        plugin.messages().title(player, "game.started", 5, 40, 10, Messages.arg("mode", mode.display()));
                        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.2f);
                    }
                    broadcast("game.started.chat", Messages.arg("mode", mode.display()), Messages.parsed("goal", goal()));
                } else {
                    remindToPick();
                    if (countdown == 30 || countdown == 10 || countdown <= 5) broadcast("game.countdown-chat", Messages.arg("seconds", countdown));
                    if (countdown <= 5) {
                        for (Player player : world.getPlayers()) {
                            plugin.messages().title(player, "game.countdown", 0, 25, 0, Messages.arg("seconds", countdown));
                            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                        }
                    }
                }
            }
            case PLAYING -> {
                // Only one team (or nobody) here: the match goes on, but nothing can be scored until another team has players.
                List<TeamColor> withPlayers = new ArrayList<>();
                for (TeamColor team : mode.teams()) if (!online(team).isEmpty()) withPlayers.add(team);
                boolean nowStalled = withPlayers.size() < 2;
                if (nowStalled != stalled) {
                    stalled = nowStalled;
                    broadcast(stalled ? "game.waiting-for-teams" : "game.teams-ready");
                }
                if (stalled) {
                    for (Player player : playing()) plugin.messages().actionBar(player, "game.waiting-for-teams-bar");
                } else {
                    contestedSeconds++;
                    rules.tick();
                }
                if (phase != Phase.PLAYING) return pending;
                secondsLeft--;
                if (secondsLeft == 600 || secondsLeft == 300 || secondsLeft == 180 || secondsLeft == 120 || secondsLeft == 60) {
                    broadcast(secondsLeft == 60 ? "game.ends-in-one-minute" : "game.ends-in-minutes", Messages.arg("minutes", secondsLeft / 60));
                    for (Player player : world.getPlayers()) player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.9f, 0.8f);
                } else if (secondsLeft == 30 || secondsLeft == 10) {
                    broadcast("game.ends-in-seconds", Messages.arg("seconds", secondsLeft));
                    for (Player player : world.getPlayers()) player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1f, 0.6f);
                } else if (secondsLeft <= 5 && secondsLeft > 0) {
                    for (Player player : world.getPlayers()) plugin.messages().actionBar(player, "game.time-final", Messages.arg("seconds", secondsLeft));
                }
                if (secondsLeft <= 0) {
                    finish(rules.leader(), "game.time-up");
                    return pending;
                }
                // (an empty match carries on: the clock runs out as usual, and anyone can still join it)

            }
        }
        updateSidebar();
        return null;
    }

    private Result pending;

    /** How it ended, once it has. */
    Result result() {
        return pending;
    }

    /** How the match ended. {@code winner} is null for a draw. */
    record Result(TeamColor winner) {}

    /** Ends the match (called by the rules when a team wins, or when time runs out). */
    void finish(TeamColor winner, String reasonKey) {
        if (phase == Phase.OVER) return;
        phase = Phase.OVER;
        paused = false;
        pending = new Result(winner);
        for (UUID id : new ArrayList<>(playingSince.keySet())) stopClock(id);
        for (UUID id : new ArrayList<>(respawning.keySet())) {
            respawning.remove(id);
            Player ghost = Bukkit.getPlayer(id);
            if (ghost != null) {
                ghost.clearTitle();
                unghost(ghost);
            }
        }
        if (reasonKey != null) broadcast(reasonKey);
        for (Player player : world.getPlayers()) {
            TeamColor team = teamOf(player.getUniqueId());
            if (winner == null) {
                plugin.messages().title(player, "game.draw", 5, 80, 20);
            } else {
                plugin.messages().title(player, team == winner ? "game.victory" : "game.defeat", 5, 80, 20,
                        Messages.comp("team", teamName(winner)));
            }
            player.playSound(player.getLocation(), team == winner ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_WITHER_DEATH, 0.7f, 1f);
            player.setGlowing(false);
        }
        if (winner != null) {
            for (Player player : online(winner)) firework(player.getLocation(), winner);
        }
        this.winner = winner;
        for (Map.Entry<UUID, TeamColor> entry : teams.entrySet()) {
            // never really a match (one team only the whole time): no coins, and it doesn't count as a game
            if (contestedSeconds < plugin.getConfig().getInt("game.min-contested-seconds", 60)) continue;
            PlayerStats numbers = stats(entry.getKey());
            numbers.games = 1;
            Player player = Bukkit.getPlayer(entry.getKey());
            boolean here = player != null && world.equals(player.getWorld());
            if (here) award(player, plugin.ctfConfig().coins().play(), "play");
            if (winner != null) {
                if (entry.getValue() == winner) {
                    numbers.wins = 1;
                    if (here) award(player, plugin.ctfConfig().coins().win(), "win");
                } else {
                    numbers.losses = 1;
                }
            }
        }
        for (Player player : world.getPlayers()) {
            if (spectators.contains(player.getUniqueId())) {
                spectators.remove(player.getUniqueId());
                unghost(player);
            }
            afterMatch(player);
        }
        summaries();
        updateSidebar();
    }

    void firework(Location location, TeamColor team) {
        firework(location, team, 0);
    }

    /** A firework of the team's colour; {@code power} 0 bursts right away, each step higher flies about 10 blocks more. */
    void firework(Location location, TeamColor team, int power) {
        world.spawn(location.clone().add(0, 1, 0), Firework.class, firework -> {
            var meta = firework.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE).withColor(team.leather()).withFade(org.bukkit.Color.WHITE)
                    .trail(power > 0).flicker(power > 0).build());
            meta.setPower(Math.max(0, Math.min(3, power)));
            firework.setFireworkMeta(meta);
        });
    }

    /** A capture: a few fireworks shot high over the capturer (game.capture-firework-power, -count). */
    void captureFireworks(Location location, TeamColor team) {
        int power = plugin.getConfig().getInt("game.capture-firework-power", 2);
        int count = Math.max(1, plugin.getConfig().getInt("game.capture-firework-count", 3));
        for (int i = 0; i < count; i++) {
            int n = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (n < 0 || Bukkit.getWorld(world.getUID()) == null) return;
                firework(location.clone().add(random.nextDouble() * 2 - 1, 0, random.nextDouble() * 2 - 1), team, power);
            }, i * 6L);
        }
    }

    /** What to do, for the start-of-match message. */
    private String goal() {
        return switch (mode) {
            case CTF, CTF4 -> plugin.messages().raw("game.goal.ctf").replace("<limit>", String.valueOf(map.captureLimit));
            case KOTH -> plugin.messages().raw("game.goal.koth").replace("<limit>", String.valueOf(map.kothPoints));
            case DTW -> plugin.messages().raw("game.goal.dtw");
        };
    }

    // ---------------------------------------------------------------- display

    public void broadcast(String key, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... args) {
        Component message = plugin.messages().component(key, args);
        for (Player player : world.getPlayers()) player.sendMessage(message);
        Bukkit.getConsoleSender().sendMessage(message);
    }

    public static Component teamName(TeamColor team) {
        return Component.text(team.display(), team.text());
    }

    void updateSidebar() {
        Messages messages = plugin.messages();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.empty());
        lines.add(messages.component("game.sidebar.map", Messages.arg("map", map.name)));
        lines.add(paused ? messages.component("game.sidebar.paused") : switch (phase) {
            case WAITING -> messages.component("game.sidebar.waiting");
            case COUNTDOWN -> messages.component("game.sidebar.starting", Messages.arg("seconds", Math.max(0, countdown)));
            case PLAYING -> messages.component("game.sidebar.time", Messages.arg("time", clock(secondsLeft)));
            case OVER -> messages.component("game.sidebar.over");
        });
        lines.add(Component.text(" "));
        lines.addAll(rules.lines());
        lines.add(Component.text("  "));
        lines.add(messages.component("game.sidebar.footer"));
        // A sidebar holds 15 lines: with many (four teams, five hills) the spacer lines go first, top and bottom.
        if (lines.size() > 15) lines.remove(0);
        if (lines.size() > 15) lines.remove(lines.size() - 2);
        while (lines.size() > 15) lines.remove(lines.size() - 2);
        for (Sidebar sidebar : sidebars.values()) sidebar.lines(lines);
    }

    public String scoreLine() {
        return rules.scoreLine();
    }

    /** The teams' score lines (as on the sidebar). */
    public List<Component> scoreLines() {
        return rules.lines();
    }

    private String plainScore() {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(plugin.messages().parse(rules.scoreLine()));
    }

    static String clock(int seconds) {
        seconds = Math.max(0, seconds);
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    /** Everyone's numbers from this match, with their names, for saving. */
    Map<UUID, Map.Entry<String, PlayerStats>> results() {
        Map<UUID, Map.Entry<String, PlayerStats>> results = new HashMap<>();
        for (UUID id : stats.keySet()) {
            PlayerStats numbers = stats.get(id);
            if (!teams.containsKey(id) && numbers.kills + numbers.deaths + numbers.assists + numbers.playSeconds == 0) continue;
            String name = names.get(id);
            if (name != null) results.put(id, Map.entry(name, stats(id)));
        }
        return results;
    }
}
