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
    private final Scoreboard board;
    private final Sidebar sidebar;
    private final Rules rules;

    private Phase phase = Phase.WAITING;
    private int countdown;
    private int secondsLeft;

    Match(FractalCTF plugin, World world, MapDef map, Mode mode, Map<UUID, TeamColor> roster) {
        this.plugin = plugin;
        this.world = world;
        this.map = map;
        this.mode = mode;
        this.teams.putAll(roster);
        this.secondsLeft = map.timeLimitMinutes * 60;
        this.board = Bukkit.getScoreboardManager().getNewScoreboard();
        for (TeamColor color : mode.teams()) {
            Team team = board.registerNewTeam(teamKey(color));
            team.color(color.text());
            team.prefix(Component.text("[" + color.display().charAt(0) + "] ", color.text()));
            team.setAllowFriendlyFire(false);
            team.setCanSeeFriendlyInvisibles(true);
        }
        this.sidebar = new Sidebar(board, plugin.messages().component("game.sidebar.title", Messages.arg("mode", mode.display())));
        this.rules = switch (mode) {
            case CTF, CTF4 -> new FlagRules(plugin, this);
            case KOTH -> new HillRules(plugin, this);
            case DTW -> new WoolRules(plugin, this);
        };
    }

    // ---------------------------------------------------------------- what's what

    /** Scoreboard team names sort the tab list: red, blue, green, yellow. */
    static String teamKey(TeamColor team) {
        return team.ordinal() + "_" + team.id();
    }

    public Scoreboard board() { return board; }

    /** Shows a player under another team's colour (a Spy's disguise), or back under their own. */
    void showAs(Player player, TeamColor team) {
        Team target = board.getTeam(teamKey(team));
        if (target != null) target.addEntry(player.getName());
        plugin.chat().refresh(player);
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
            if (teams.containsKey(player.getUniqueId()) && player.getGameMode() != GameMode.SPECTATOR) players.add(player);
        }
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
        player.setScoreboard(board);
        names.put(player.getUniqueId(), player.getName());
        if (phase == Phase.OVER) {
            player.teleport(spectatorSpawn());
            afterMatch(player);
            return;
        }
        TeamColor team = teams.get(player.getUniqueId());
        if (team == null) {
            team = TeamBalancer.smallest(onlineTeams(), mode.teams());
            teams.put(player.getUniqueId(), team);
        }
        board.getTeam(teamKey(team)).addEntry(player.getName());
        plugin.chat().refresh(player);
        plugin.messages().send(player, "game.joined", Messages.comp("team", teamName(team)),
                Messages.arg("map", map.name), Messages.arg("mode", mode.display()));
        if (!map.authors.isEmpty()) plugin.messages().send(player, "game.map-by", Messages.arg("authors", String.join(", ", map.authors)));
        TeamColor finalTeam = team;
        plugin.kits().load(player.getUniqueId(), () -> {
            if (player.isOnline() && world.equals(player.getWorld())) respawnNow(player, finalTeam);
        });
        player.teleport(spawn(team));
        player.setGameMode(GameMode.SURVIVAL);
        updateSidebar();
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
        if (live() && teams.containsKey(player.getUniqueId())) rules.onDeathOrQuit(player, player.getLocation());
        plugin.kits().forget(player.getUniqueId());
    }

    void spectate(Player player) {
        player.getInventory().clear();
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(spectatorSpawn());
    }

    /** Puts a player back at their spawn with their kit (after joining or dying). */
    void respawnNow(Player player, TeamColor team) {
        player.teleport(spawn(team));
        equip(player, team);
    }

    void equip(Player player, TeamColor team) {
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setGlowing(false);
        Kit kit = plugin.kits().of(player.getUniqueId());
        plugin.kits().equip(player, team);
        protectedUntil.put(player.getUniqueId(), System.currentTimeMillis() + plugin.ctfConfig().spawnProtection() * 1000L);
        if (phase == Phase.COUNTDOWN || phase == Phase.WAITING) plugin.messages().actionBar(player, "game.your-kit", Messages.arg("kit", kit.display()));
    }

    public boolean spawnProtected(Player player) {
        return protectedUntil.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis();
    }

    void hit(Player victim, Player attacker) {
        lastAttacker.put(victim.getUniqueId(), attacker.getUniqueId());
        damagers.computeIfAbsent(victim.getUniqueId(), id -> new HashMap<>()).put(attacker.getUniqueId(), System.currentTimeMillis());
        protectedUntil.remove(attacker.getUniqueId()); // attacking ends your own spawn protection
    }

    /** A player died: count it, credit the killer, and tell everyone. */
    void died(Player victim, Player killer) {
        UUID last = lastAttacker.remove(victim.getUniqueId());
        if (killer == null && last != null) {
            // A fall, the void or fire after a fight: the last enemy to hit them gets the kill, if it was recent.
            Long when = damagers.getOrDefault(victim.getUniqueId(), Map.of()).get(last);
            if (when != null && System.currentTimeMillis() - when <= plugin.ctfConfig().coins().assistSeconds() * 1000L) {
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
        if (killer != null && !killer.equals(victim)) {
            TeamColor killerTeam = teamOf(killer.getUniqueId());
            Component killerName = killerTeam == null ? killer.name() : Component.text(killer.getName(), killerTeam.text());
            if (live()) {
                stats(killer.getUniqueId()).kills++;
                award(killer, plugin.ctfConfig().coins().kill(), "kill");
            }
            if (assists.isEmpty()) {
                broadcast("game.killed", Messages.comp("victim", victimName), Messages.comp("killer", killerName));
            } else {
                List<String> names = new ArrayList<>();
                for (Player helper : assists) names.add(helper.getName());
                broadcast("game.killed-assist", Messages.comp("victim", victimName), Messages.comp("killer", killerName),
                        Messages.arg("assists", String.join(", ", names)));
            }
            killer.playSound(killer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.6f);
        } else {
            broadcast("game.died", Messages.comp("victim", victimName));
        }
        if (live()) {
            for (Player helper : assists) {
                stats(helper.getUniqueId()).assists++;
                award(helper, plugin.ctfConfig().coins().assist(), "assist");
            }
        }
    }

    /** Gives coins for something done in this match (saved with the stats at the end). */
    public void award(Player player, int coins, String reason) {
        if (coins <= 0) return;
        stats(player.getUniqueId()).coins += coins;
        plugin.messages().send(player, "coins.earned", Messages.arg("coins", coins),
                Messages.comp("reason", plugin.messages().component("coins.reason." + reason)));
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
        for (Player player : playing()) player.teleport(spawn(teamOf(player.getUniqueId())));
        broadcast("game.get-ready", Messages.arg("seconds", seconds));
    }

    /** Once a second. Returns the winner (or a "draw" marker) when the match ends this second. */
    Result second() {
        switch (phase) {
            case WAITING, OVER -> {
                return null;
            }
            case COUNTDOWN -> {
                if (countdown <= 0) {
                    phase = Phase.PLAYING;
                    for (Player player : world.getPlayers()) {
                        plugin.messages().title(player, "game.started", 5, 40, 10, Messages.arg("mode", mode.display()));
                        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.2f);
                    }
                    broadcast("game.started.chat", Messages.arg("mode", mode.display()), Messages.parsed("goal", goal()));
                } else {
                    if (countdown <= 5) {
                        for (Player player : world.getPlayers()) {
                            plugin.messages().title(player, "game.countdown", 0, 25, 0, Messages.arg("seconds", countdown));
                            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                        }
                    }
                    countdown--;
                }
            }
            case PLAYING -> {
                rules.tick();
                if (phase != Phase.PLAYING) return pending;
                secondsLeft--;
                if (secondsLeft == 60 || secondsLeft == 300) broadcast("game.time-warning", Messages.arg("minutes", secondsLeft / 60));
                if (secondsLeft <= 0) {
                    finish(rules.leader(), "game.time-up");
                    return pending;
                }
                if (playing().isEmpty()) {
                    finish(null, "game.everyone-left");
                    return pending;
                }
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
        pending = new Result(winner);
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
            broadcast("game.winner", Messages.comp("team", teamName(winner)), Messages.arg("score", plainScore()));
            for (Player player : online(winner)) firework(player.getLocation(), winner);
        }
        for (Map.Entry<UUID, TeamColor> entry : teams.entrySet()) {
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
        for (Player player : world.getPlayers()) afterMatch(player);
        updateSidebar();
    }

    void firework(Location location, TeamColor team) {
        world.spawn(location.clone().add(0, 1, 0), Firework.class, firework -> {
            var meta = firework.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE).withColor(team.leather()).withFade(org.bukkit.Color.WHITE).build());
            meta.setPower(0);
            firework.setFireworkMeta(meta);
        });
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
        lines.add(switch (phase) {
            case WAITING -> messages.component("game.sidebar.waiting");
            case COUNTDOWN -> messages.component("game.sidebar.starting", Messages.arg("seconds", Math.max(0, countdown)));
            case PLAYING -> messages.component("game.sidebar.time", Messages.arg("time", clock(secondsLeft)));
            case OVER -> messages.component("game.sidebar.over");
        });
        lines.add(Component.text(" "));
        lines.addAll(rules.lines());
        lines.add(Component.text("  "));
        lines.add(messages.component("game.sidebar.footer"));
        sidebar.lines(lines);
    }

    public String scoreLine() {
        return rules.scoreLine();
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
        for (UUID id : teams.keySet()) {
            String name = names.get(id);
            if (name != null) results.put(id, Map.entry(name, stats(id)));
        }
        return results;
    }
}
