/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.game;

import com.sasha.ctf.FractalCTF;
import com.sasha.ctf.config.Messages;
import com.sasha.ctf.model.BlockPos;
import com.sasha.ctf.model.Cuboid;
import com.sasha.ctf.model.MapDef;
import com.sasha.ctf.model.TeamColor;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * King of the Hill, capture and hold: a team that stands on a point alone for the map's capture time takes
 * it, and then scores every second it owns it, whether anyone is standing there or not. The point nearest the
 * middle of the map scores {@code game.koth-middle-points} (2) a second, the others 1. Enemies on the point stop
 * the clock (contested); an enemy alone first wears the owner's hold down, then captures it. First to the map's
 * koth-points wins; at the time limit, the most points wins.
 *
 * Each point has a label beside it (its name and who holds it), its own boss bar in the holder's colour, and a
 * pattern in its floor (the edge, a ring round the beacon, the diagonals and a middle ring) that turns the
 * capturing team's colour block by block as the capture goes on, from the beacon outward. A half-done capture
 * fades slowly when its team steps off. The more of the holders stand on a point, the faster it scores (up to
 * twice). The sidebar says which team wins first, and when; the match is warned as a team closes in.
 */
public final class HillRules implements Rules {

    /** One capture point. Progress counts up to the capture time for {@code capturer}. */
    private static final class Point {
        final String name;
        final Cuboid area;
        int value = 1;
        TeamColor owner;
        TeamColor capturer;
        double progress;
        /** Seconds in a row nobody from the owning team has stood on it. */
        int away;
        /** How much of its hold the owner still has (1 = full); drains once they've been away longer than the grace. */
        double hold = 1;
        /** Who was last announced as capturing it, and when (so stepping on and off doesn't spam chat). */
        TeamColor announcedTeam;
        long announcedAt;
        /** Capture points per second right now (for "captures in 4s"). */
        double rate;
        /** What is happening on it this second: gaining, defending (owners pushing back), wearing (another team's capture being undone), fading (nobody on it), idle. */
        String state = "idle";
        boolean contested;
        Block glass;
        String beam = "";
        TextDisplay label;
        /** The point's own boss bar. */
        BossBar bar;
        /** Holders standing on it now (more of them score faster). */
        int holders;
        /** The pattern in the floor, as the map had it, nearest the beacon first: it takes the teams' colours. */
        final Map<Block, BlockData> edge = new LinkedHashMap<>();
        /** What each pattern block shows now (null = the map's own block). */
        final Map<Block, TeamColor> shown = new HashMap<>();

        /** Where the beacon goes (null = the middle of the floor). */
        BlockPos beaconAt;

        Point(String name, Cuboid area) {
            this.name = name;
            this.area = area;
        }

        Location centre(World world) {
            return new Location(world, (area.min().x() + area.max().x() + 1) / 2.0, area.min().y(), (area.min().z() + area.max().z() + 1) / 2.0);
        }
    }

    /** The hills' names, in map order (/tptohill). */
    public List<String> pointNames() {
        List<String> names = new ArrayList<>();
        for (Point point : points) names.add(point.name);
        return names;
    }

    /** Where to stand on a hill: its middle, by name (any case, spaces optional) or number from 1; null if none. */
    public Location pointSpot(String wanted) {
        String key = wanted.replace(" ", "").toLowerCase(java.util.Locale.ROOT);
        for (int i = 0; i < points.size(); i++) {
            Point point = points.get(i);
            if (point.name.replace(" ", "").toLowerCase(java.util.Locale.ROOT).equals(key) || String.valueOf(i + 1).equals(key)) {
                return point.centre(match.world()).add(0, 0.1, 0);
            }
        }
        return null;
    }

    private static final int[] WARN_AT = {120, 60, 30, 10};

    private final FractalCTF plugin;
    private final Match match;
    private final List<Point> points = new ArrayList<>();
    /** Points per team (fractions build up from crowd bonuses; the whole number is what's shown). */
    private final Map<TeamColor, Double> score = new EnumMap<>(TeamColor.class);
    /** The last "will win in" warning each team got, so each is said once (until the team falls back). */
    private final Map<TeamColor, Integer> warned = new EnumMap<>(TeamColor.class);
    /** Seconds each player has stood on a point their team holds since their last holding coins. */
    private final Map<UUID, Integer> holding = new HashMap<>();
    private final int captureSeconds;
    private int ticks;

    HillRules(FractalCTF plugin, Match match) {
        this.plugin = plugin;
        this.match = match;
        for (MapDef.HillDef hill : match.map().allHills()) {
            Point point = new Point(hill.name(), hill.area());
            point.beaconAt = hill.beacon();
            points.add(point);
        }
        for (TeamColor team : match.mode().teams()) score.put(team, 0.0);
        this.captureSeconds = match.map().captureSeconds;
        for (Point point : points) point.bar = BossBar.bossBar(Component.empty(), 0f, BossBar.Color.WHITE, BossBar.Overlay.NOTCHED_10);
        // The point nearest the middle of all the points is worth more (a map with one point: just 1 a second).
        if (points.size() > 1) {
            double cx = 0, cz = 0;
            for (Point point : points) {
                cx += (point.area.min().x() + point.area.max().x()) / 2.0;
                cz += (point.area.min().z() + point.area.max().z()) / 2.0;
            }
            cx /= points.size();
            cz /= points.size();
            Point middle = null;
            double best = Double.MAX_VALUE;
            for (Point point : points) {
                double dx = (point.area.min().x() + point.area.max().x()) / 2.0 - cx, dz = (point.area.min().z() + point.area.max().z()) / 2.0 - cz;
                if (dx * dx + dz * dz < best) {
                    best = dx * dx + dz * dz;
                    middle = point;
                }
            }
            if (middle != null) middle.value = Math.max(1, plugin.getConfig().getInt("game.koth-middle-points", 2));
        }
    }

    @Override
    public void prepare() {
        for (Point point : points) {
            point.glass = findBeacon(point.area);
            if (point.glass == null) point.glass = Structures.hillBeacon(match.world(), point.area, point.beaconAt);
            rememberEdge(point);
        }
    }

    @Override
    public void start() {
        for (Point point : points) {
            if (point.glass == null) point.glass = findBeacon(point.area);
            if (point.edge.isEmpty()) rememberEdge(point);
            beam(point);
            label(point);
        }
        updateBars();
    }

    /**
     * The floor pattern: the border, a ring round the beacon, the diagonals from the beacon to the corners and a ring
     * half-way out, ordered from the beacon outward (that is the order they change colour in).
     */
    private void rememberEdge(Point point) {
        World world = match.world();
        Cuboid hill = point.area;
        int y = hill.min().y() - 1;
        double cx = point.glass != null ? point.glass.getX() : (hill.min().x() + hill.max().x()) / 2.0;
        double cz = point.glass != null ? point.glass.getZ() : (hill.min().z() + hill.max().z()) / 2.0;
        int half = Math.max(1, Math.min(hill.max().x() - hill.min().x(), hill.max().z() - hill.min().z()) / 4);
        List<Block> pattern = new ArrayList<>();
        for (int x = hill.min().x(); x <= hill.max().x(); x++) {
            for (int z = hill.min().z(); z <= hill.max().z(); z++) {
                int dx = (int) Math.round(Math.abs(x - cx)), dz = (int) Math.round(Math.abs(z - cz));
                int ring = Math.max(dx, dz);
                boolean border = x == hill.min().x() || x == hill.max().x() || z == hill.min().z() || z == hill.max().z();
                boolean keep = border || ring == 1 || dx == dz || ring == half + 1;
                if (!keep || ring == 0) continue;
                Block block = world.getBlockAt(x, y, z);
                if (block.equals(point.glass) || !block.getType().isSolid() || !block.getType().isOccluding()
                        || block.getType() == Material.BEACON || block.getType().name().endsWith("_GLASS")) continue;
                pattern.add(block);
            }
        }
        pattern.sort(java.util.Comparator.comparingDouble(b -> Math.hypot(b.getX() - cx, b.getZ() - cz)));
        for (Block block : pattern) point.edge.put(block, block.getBlockData());
    }

    /** A beacon in the hill (or up to 4 blocks under its floor): the block above it gets tinted glass. */
    private Block findBeacon(Cuboid hill) {
        World world = match.world();
        for (int x = hill.min().x(); x <= hill.max().x(); x++)
            for (int z = hill.min().z(); z <= hill.max().z(); z++)
                for (int y = hill.min().y() - 4; y <= hill.max().y(); y++) {
                    if (world.getBlockAt(x, y, z).getType() == Material.BEACON) return world.getBlockAt(x, y + 1, z);
                }
        return null;
    }

    /** The point's beam: the owner's colour, white while nobody owns it, gray while it's fought over. */
    private void beam(Point point) {
        if (point.glass == null) return;
        TeamColor team = point.owner;
        String glass = point.contested ? "light_gray" : team == null ? "white" : team == TeamColor.GREEN ? "lime" : team.id();
        if (glass.equals(point.beam)) return;
        point.beam = glass;
        Material material = Material.matchMaterial(glass + "_stained_glass");
        if (material != null) point.glass.setType(material, false);
    }

    private String label(Point point, boolean shortName) {
        if (points.size() == 1) return plugin.messages().raw(shortName ? "hill.short" : "hill.the-hill");
        return point.name;
    }

    /** Seconds without the owner on a point before its hold starts draining (game.koth-grace-seconds). */
    private int grace() {
        return Math.max(0, plugin.getConfig().getInt("game.koth-grace-seconds", 8));
    }

    /** Seconds a full hold takes to drain back to neutral (game.koth-neutralize-seconds). */
    private int neutralizeSeconds() {
        return Math.max(1, plugin.getConfig().getInt("game.koth-neutralize-seconds", 10));
    }

    /** Seconds until an unattended point goes neutral (0 while the owner is on it). */
    private int secondsToNeutral(Point point) {
        if (point.owner == null || point.away == 0) return 0;
        return Math.max(0, grace() - point.away) + (int) Math.ceil(point.hold * neutralizeSeconds());
    }

    /**
     * What a point scores its owner each second: full (more with more of them on it) while they stand on it,
     * game.koth-away-rate of that (half) while they're away but within the grace, nothing once it is draining.
     */
    private double pointRate(Point point) {
        if (point.owner == null) return 0;
        if (point.away > grace()) return 0;
        if (point.holders > 0) {
            double bonus = plugin.getConfig().getDouble("game.koth-crowd-bonus", 0.25);
            return point.value * (point.holders <= 1 ? 1 : Math.min(2.0, 1 + bonus * (point.holders - 1)));
        }
        return point.value * Math.max(0, plugin.getConfig().getDouble("game.koth-away-rate", 0.5));
    }

    /** Points a second the team is scoring now. */
    private double rate(TeamColor team) {
        double rate = 0;
        for (Point point : points) if (point.owner == team) rate += pointRate(point);
        return rate;
    }

    /** Seconds until the team wins at the rate it scores now; -1 if it isn't scoring. */
    private int secondsToWin(TeamColor team) {
        double rate = rate(team);
        if (rate <= 0) return -1;
        double left = Math.max(0, match.map().kothPoints - score.get(team));
        return (int) Math.ceil(left / rate);
    }

    /** 75 -> "1m 15s", 40 -> "40s". */
    static String duration(int seconds) {
        seconds = Math.max(0, seconds);
        if (seconds < 60) return seconds + "s";
        return seconds / 60 + "m" + (seconds % 60 == 0 ? "" : " " + seconds % 60 + "s");
    }

    @Override
    public void tick() {
        ticks++;
        for (Player player : match.world().getPlayers()) for (Point point : points) player.showBossBar(point.bar);
        for (Point point : points) tickPoint(point);
        // owned points score for their owner: most with them on it, half while away, nothing once draining
        for (Point point : points) {
            if (point.owner == null) continue;
            double add = pointRate(point);
            if (add <= 0) continue;
            double total = score.merge(point.owner, add, Double::sum);
            if (total >= match.map().kothPoints) {
                score.put(point.owner, (double) match.map().kothPoints);
                removeLabels();
                match.finish(point.owner, null);
                hideBar();
                return;
            }
        }
        warnings();
        updateBars();
        for (Point point : points) {
            label(point);
            paint(point);
        }
        if (ticks % 2 == 0) for (Point point : points) outline(point);
    }

    /** "Red Team will win in 1m 15s!" as a team closes in, once at each step. */
    private void warnings() {
        for (TeamColor team : score.keySet()) {
            int seconds = secondsToWin(team);
            Integer last = warned.get(team);
            if (seconds < 0) {
                warned.remove(team);
                continue;
            }
            if (last != null && seconds > last + 10) warned.remove(team);  // it fell back: warn again later
            for (int at : WARN_AT) {
                if (seconds > at || seconds < at - 3) continue;
                if (last != null && last <= at) break;
                warned.put(team, at);
                match.broadcast("hill.win-warning", Messages.comp("team", Match.teamName(team)), Messages.arg("time", duration(seconds)));
                for (Player player : match.world().getPlayers()) {
                    boolean mine = match.teamOf(player.getUniqueId()) == team;
                    player.playSound(player.getLocation(), mine ? Sound.BLOCK_NOTE_BLOCK_CHIME : Sound.BLOCK_NOTE_BLOCK_BASS, 1f, mine ? 1.2f : 0.7f);
                }
                break;
            }
        }
    }

    private void tickPoint(Point point) {
        Map<TeamColor, Integer> on = new EnumMap<>(TeamColor.class);
        List<Player> standing = new ArrayList<>();
        for (Player player : match.alive()) {
            if (player.isDead() || !inZone(point, player.getLocation())) continue;
            TeamColor team = match.teamOf(player.getUniqueId());
            if (team == null) continue;
            on.merge(team, 1, Integer::sum);
            standing.add(player);
        }
        point.contested = on.size() > 1;
        point.state = "idle";
        point.rate = 0;
        point.holders = point.owner == null || point.contested ? 0 : on.getOrDefault(point.owner, 0);
        // Holding needs presence: away from it longer than the grace, the owner's hold drains back to neutral.
        if (point.owner != null) {
            if (on.containsKey(point.owner)) {
                point.away = 0;
                point.hold = 1;
            } else if (++point.away > grace()) {
                point.hold -= 1.0 / neutralizeSeconds();
                if (point.hold <= 0) {
                    TeamColor lost = point.owner;
                    point.owner = null;
                    point.hold = 1;
                    point.away = 0;
                    match.broadcast("hill.neutralized", Messages.comp("team", Match.teamName(lost)), Messages.arg("point", label(point, false)));
                    for (Player player : match.world().getPlayers()) player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.6f, 0.9f);
                }
            } else if (point.away == 1 && point.capturer == null) {
                for (Player player : match.world().getPlayers()) {
                    if (match.teamOf(player.getUniqueId()) == point.owner) plugin.messages().actionBar(player, "hill.left-alone", Messages.arg("point", label(point, false)),
                            Messages.arg("seconds", grace() + neutralizeSeconds()));
                }
            }
        }
        if (on.size() == 1) {
            TeamColor team = on.keySet().iterator().next();
            int people = on.get(team);
            double rate = Math.min(2.0, 1.0 + 0.25 * (people - 1)); // more people capture faster, up to twice as fast
            point.rate = rate;
            if (team == point.owner) {
                // the owners push back any enemy progress
                if (point.capturer != null) point.state = "defending";
                point.progress = Math.max(0, point.progress - rate);
                if (point.progress == 0) point.capturer = null;
            } else if (point.capturer != null && point.capturer != team) {
                // someone else had started: wear their progress down first
                point.state = "wearing";
                point.progress -= rate;
                if (point.progress <= 0) {
                    point.progress = 0;
                    point.capturer = team;
                }
            } else {
                if (point.capturer == null) {
                    point.capturer = team;
                    // "Red is capturing South Ring!" (not again for the same team on the same point within 15 s)
                    long now = System.currentTimeMillis();
                    if (team != point.announcedTeam || now - point.announcedAt > 15_000) {
                        point.announcedTeam = team;
                        point.announcedAt = now;
                        if (point.owner != null) {
                            match.broadcast("hill.capturing-owned", Messages.comp("team", Match.teamName(team)),
                                    Messages.comp("owner", Match.teamName(point.owner)), Messages.arg("point", label(point, false)));
                        } else {
                            match.broadcast("hill.capturing-start", Messages.comp("team", Match.teamName(team)), Messages.arg("point", label(point, false)));
                        }
                        for (Player player : match.world().getPlayers()) player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BIT, 0.7f, 1.2f);
                    }
                }
                point.state = "gaining";
                point.progress += rate;
                if (point.progress >= captureSeconds) capture(point, team, standing);
            }
            for (Player player : standing) match.stats(player.getUniqueId()).hillSeconds++;
        } else if (on.isEmpty() && point.capturer != null) {
            // nobody there: a half-finished capture fades slowly (game.koth-decay-per-second of the capture time)
            double decay = plugin.getConfig().getDouble("game.koth-decay-per-second", 0.05) * captureSeconds;
            point.state = "fading";
            point.progress = Math.max(0, point.progress - Math.max(0.05, decay));
            if (point.progress == 0) point.capturer = null;
        }
        // coins for holding: standing on a point your team owns, uncontested
        int every = Math.max(5, plugin.getConfig().getInt("coins.hill-hold-seconds", 15));
        int coins = plugin.getConfig().getInt("coins.hill-hold", 2);
        for (Player player : standing) {
            TeamColor team = match.teamOf(player.getUniqueId());
            if (point.contested || team != point.owner) continue;
            int held = holding.merge(player.getUniqueId(), 1, Integer::sum);
            if (held >= every) {
                holding.put(player.getUniqueId(), 0);
                match.award(player, coins, "hill-hold", Messages.arg("point", label(point, false)));
            }
        }
        // what the players on the point see
        for (Player player : standing) {
            TeamColor team = match.teamOf(player.getUniqueId());
            if (point.contested) {
                plugin.messages().actionBar(player, "hill.action-contested", Messages.arg("point", label(point, false)));
            } else if (team == point.owner && point.capturer == null) {
                plugin.messages().actionBar(player, "hill.action-holding", Messages.arg("point", label(point, false)), Messages.arg("value", point.value));
            } else {
                double rate = point.rate > 0 ? point.rate : 1.0;
                int clear = Math.max(1, (int) Math.ceil(point.progress / rate));
                plugin.messages().actionBar(player, point.capturer == team ? "hill.action-capturing" : "hill.action-clearing",
                        Messages.arg("point", label(point, false)), Messages.arg("seconds", point.capturer == team ? secondsToCapture(point) : clear));
            }
        }
        beam(point);
    }

    private void capture(Point point, TeamColor team, List<Player> standing) {
        TeamColor before = point.owner;
        point.owner = team;
        point.capturer = null;
        point.progress = 0;
        point.away = 0;
        point.hold = 1;
        if (before == null) match.broadcast("hill.captured", Messages.comp("team", Match.teamName(team)), Messages.arg("point", label(point, false)));
        else match.broadcast("hill.captured-from", Messages.comp("team", Match.teamName(team)), Messages.arg("point", label(point, false)),
                Messages.comp("from", Match.teamName(before)));
        for (Player player : match.world().getPlayers()) {
            TeamColor mine = match.teamOf(player.getUniqueId());
            player.playSound(player.getLocation(), mine == team ? Sound.BLOCK_BEACON_POWER_SELECT : Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.2f);
        }
        for (Player player : standing) {
            if (match.teamOf(player.getUniqueId()) != team) continue;
            match.stats(player.getUniqueId()).captures++;
            holding.put(player.getUniqueId(), 0);
            match.award(player, plugin.ctfConfig().coins().hill(), "hill", Messages.arg("point", label(point, false)));
            if (before != null) match.award(player, plugin.getConfig().getInt("coins.hill-retake", 5), "hill-retake",
                    Messages.arg("point", label(point, false)), Messages.comp("team", Match.teamName(before)));
        }
        ripple(point, team);
    }

    /** A burst over the point for its new holder (the floor's last blocks take their colour in paint()). */
    private void ripple(Point point, TeamColor team) {
        World world = match.world();
        Location centre = point.centre(world);
        world.spawnParticle(Particle.FIREWORK, centre.clone().add(0, 1.5, 0), 60, 1.5, 0.5, 1.5, 0.08);
        world.playSound(centre, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1.5f, 1f);
    }

    /**
     * The floor pattern shows the holder's colour (the map's own blocks while nobody holds it); while a team is
     * capturing, as much of it as the capture has done shows the capturer's colour, from the beacon outward.
     */
    private void paint(Point point) {
        int total = point.edge.size();
        if (total == 0) return;
        int taken = point.capturer == null ? 0 : (int) Math.floor(total * Math.max(0, Math.min(1, point.progress / captureSeconds)));
        World world = match.world();
        int i = 0, changed = 0;
        for (Map.Entry<Block, BlockData> entry : point.edge.entrySet()) {
            TeamColor want = i++ < taken ? point.capturer : point.owner;
            if (point.shown.get(entry.getKey()) == want && (want != null || point.shown.containsKey(entry.getKey()))) continue;
            point.shown.put(entry.getKey(), want);
            BlockData data = want == null ? entry.getValue() : Material.valueOf((want == TeamColor.GREEN ? "LIME" : want.name()) + "_CONCRETE").createBlockData();
            entry.getKey().setBlockData(data, false);
            if (changed++ < 6) {
                Location at = entry.getKey().getLocation().add(0.5, 1.05, 0.5);
                world.spawnParticle(Particle.BLOCK, at, 5, 0.3, 0.05, 0.3, data);
                if (changed % 3 == 1) world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.3f + changed * 0.05f);
            }
        }
    }

    // ---------------------------------------------------------------- the label over each point

    private void label(Point point) {
        World world = match.world();
        if (point.label == null || !point.label.isValid()) {
            // Beside the point (on the edge that faces the middle of the map), not in the beacon's beam, at head height.
            Location at = labelSpot(point);
            point.label = world.spawn(at, TextDisplay.class, display -> {
                display.setPersistent(false);
                display.setBillboard(Display.Billboard.CENTER);
                display.setShadowed(true);
                display.setBackgroundColor(org.bukkit.Color.fromARGB(90, 0, 0, 0));
                display.setLineWidth(400);
                display.setViewRange(1.2f);
                display.setSeeThrough(false);
            });
        }
        Messages messages = plugin.messages();
        List<Component> lines = new ArrayList<>();
        Component name = Component.text(label(point, true), point.owner == null ? NamedTextColor.WHITE : point.owner.text());
        lines.add(messages.component("hill.label.name", Messages.comp("point", name), Messages.arg("value", point.value)));
        if (point.contested) {
            lines.add(messages.component("hill.label.contested"));
        } else if (point.capturer != null) {
            lines.add(messages.component("hill.label.capturing", Messages.comp("team", Match.teamName(point.capturer)),
                    Messages.arg("seconds", secondsToCapture(point)), Messages.parsed("bar", progressBar(point.progress / captureSeconds, point.capturer))));
        } else if (point.owner == null) {
            lines.add(messages.component("hill.label.free"));
        } else {
            lines.add(messages.component("hill.label.held", Messages.comp("team", Match.teamName(point.owner))));
        }
        Component text = Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(), lines);
        if (!text.equals(point.label.text())) point.label.text(text);
    }

    /**
     * Where a point's label goes: on the point itself, a little way from its middle (where the beacon's beam is) toward
     * the middle of the map, a little above head height.
     */
    private Location labelSpot(Point point) {
        Location centre = point.centre(match.world());
        double mx = 0, mz = 0;
        for (TeamColor team : match.mode().teams()) {
            Location spawn = match.spawn(team);
            mx += spawn.getX();
            mz += spawn.getZ();
        }
        mx /= match.mode().teams().size();
        mz /= match.mode().teams().size();
        double dx = mx - centre.getX(), dz = mz - centre.getZ(), len = Math.hypot(dx, dz);
        if (len < 3) { dx = 0; dz = -1; len = 1; }                  // the point is the middle: toward the north
        double half = Math.min(point.area.max().x() - point.area.min().x() + 1, point.area.max().z() - point.area.min().z() + 1) / 2.0;
        double out = Math.max(1.6, Math.min(2.5, half - 0.5));        // clear of the beam, still over the point
        // toward the spawns first; if that's inside a wall, the next free direction round the point (8 tries, 45° apart)
        double angle = Math.atan2(dz, dx);
        Location first = null;
        for (int step = 0; step < 8; step++) {
            double a = angle + Math.PI / 4 * ((step + 1) / 2) * (step % 2 == 0 ? 1 : -1);
            Location at = centre.clone().add(Math.cos(a) * out, 0, Math.sin(a) * out);
            at.setY(point.area.min().y() + 2.4);
            if (first == null) first = at;
            if (at.getBlock().isPassable() && at.clone().add(0, 0.6, 0).getBlock().isPassable()) return at;
        }
        return first;
    }

    private static String progressBar(double done, TeamColor team) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, done)) * 10);
        return "<" + team.id() + ">" + "|".repeat(filled) + "</" + team.id() + "><dark_gray>" + "|".repeat(10 - filled) + "</dark_gray>";
    }

    private void removeLabels() {
        for (Point point : points) if (point.label != null) point.label.remove();
    }

    private static String fmt(double rate) {
        return rate == Math.floor(rate) ? String.valueOf((long) rate) : String.format(java.util.Locale.ROOT, "%.1f", rate);
    }

    /** Seconds until the capturer has the point at the rate they're going now (at least 1). */
    private int secondsToCapture(Point point) {
        double rate = point.state.equals("gaining") && point.rate > 0 ? point.rate : 1.0;
        return Math.max(1, (int) Math.ceil((captureSeconds - point.progress) / rate));
    }

    /** Each point's own bar: what is happening on it in words ("Red captures North Pass in 4s"), the team colour, and how far a capture has got. */
    private void updateBars() {
        Messages messages = plugin.messages();
        for (Point point : points) {
            TeamColor colour = point.capturer != null && !point.contested ? point.capturer : point.owner;
            Component name = Component.text(label(point, true), point.owner == null ? NamedTextColor.WHITE : point.owner.text());
            Component title;
            float progress = (float) Math.max(0, Math.min(1, point.progress / captureSeconds));
            if (point.contested) {
                title = messages.component("hill.bar-contested", Messages.comp("point", name));
            } else if (point.capturer != null) {
                String key = switch (point.state) {
                    case "gaining" -> "hill.bar-capturing";
                    case "defending" -> "hill.bar-defending";
                    case "wearing" -> "hill.bar-wearing";
                    default -> "hill.bar-fading";
                };
                TeamColor other = point.state.equals("defending") ? point.owner : point.capturer;
                title = messages.component(key, Messages.comp("point", name), Messages.comp("team", Match.teamName(point.capturer)),
                        Messages.comp("other", Match.teamName(other == null ? point.capturer : other)), Messages.arg("seconds", secondsToCapture(point)));
            } else if (point.owner != null && point.away > grace()) {
                title = messages.component("hill.bar-draining", Messages.comp("point", name), Messages.comp("team", Match.teamName(point.owner)),
                        Messages.arg("seconds", secondsToNeutral(point)));
                progress = (float) Math.max(0, Math.min(1, point.hold));
            } else if (point.owner != null && point.away > 0) {
                title = messages.component("hill.bar-held-away", Messages.comp("point", name), Messages.comp("team", Match.teamName(point.owner)),
                        Messages.arg("rate", fmt(pointRate(point))), Messages.arg("seconds", secondsToNeutral(point)));
                progress = 1f;
            } else if (point.owner != null) {
                title = messages.component("hill.bar-held", Messages.comp("point", name), Messages.comp("team", Match.teamName(point.owner)),
                        Messages.arg("value", fmt(pointRate(point))));
                progress = 1f;
            } else {
                title = messages.component("hill.bar-free", Messages.comp("point", name), Messages.arg("value", point.value));
                progress = 0f;
            }
            point.bar.name(title);
            point.bar.progress(progress);
            point.bar.color(point.contested ? BossBar.Color.PURPLE : colour == TeamColor.RED ? BossBar.Color.RED : colour == TeamColor.BLUE ? BossBar.Color.BLUE
                    : colour == TeamColor.GREEN ? BossBar.Color.GREEN : colour == TeamColor.YELLOW ? BossBar.Color.YELLOW : BossBar.Color.WHITE);
        }
    }

    void hideBar() {
        for (Player player : match.world().getPlayers()) for (Point point : points) player.hideBossBar(point.bar);
    }

    /** Dust along the edges of each point at its floor, in the owner's colour. */
    private void outline(Point point) {
        World world = match.world();
        org.bukkit.Color colour = point.contested ? org.bukkit.Color.SILVER : point.owner == null ? org.bukkit.Color.WHITE : point.owner.leather();
        Particle.DustOptions dust = new Particle.DustOptions(colour, 1.5f);
        Cuboid hill = point.area;
        double y = hill.min().y() + 0.2;
        double x1 = hill.min().x(), x2 = hill.max().x() + 1, z1 = hill.min().z(), z2 = hill.max().z() + 1;
        for (double x = x1; x <= x2; x += 1) {
            world.spawnParticle(Particle.DUST, x, y, z1, 1, 0, 0, 0, dust);
            world.spawnParticle(Particle.DUST, x, y, z2, 1, 0, 0, 0, dust);
        }
        for (double z = z1; z <= z2; z += 1) {
            world.spawnParticle(Particle.DUST, x1, y, z, 1, 0, 0, 0, dust);
            world.spawnParticle(Particle.DUST, x2, y, z, 1, 0, 0, 0, dust);
        }
    }

    @Override
    public List<BlockPos> objectiveBlocks() {
        List<BlockPos> blocks = new ArrayList<>();
        for (Point point : points) if (point.glass != null) blocks.add(BlockPos.of(point.glass));
        return blocks;
    }

    /** The teams, most points first. */
    private List<TeamColor> ranked() {
        List<TeamColor> teams = new ArrayList<>(score.keySet());
        teams.sort((a, b) -> Integer.compare(shown(b), shown(a)));
        return teams;
    }

    /** The team with the most points, or null while nobody has any or the top is tied. */
    private TeamColor strictLeader() {
        List<TeamColor> teams = ranked();
        if (teams.isEmpty() || shown(teams.get(0)) == 0) return null;
        if (teams.size() > 1 && shown(teams.get(1)) == shown(teams.get(0))) return null;
        return teams.get(0);
    }

    /** A team's points as shown (whole points). */
    private int shown(TeamColor team) {
        return (int) Math.floor(score.getOrDefault(team, 0.0) + 1e-9);
    }

    /** The teams (most points first, the leader crowned), then each point on its own line. */
    @Override
    public List<Component> lines() {
        List<Component> lines = new ArrayList<>();
        TeamColor leader = strictLeader();
        for (TeamColor team : ranked()) {
            lines.add(plugin.messages().component(team == leader ? "hill.sidebar-line-leading" : "hill.sidebar-line",
                    Messages.comp("team", Match.teamName(team)), Messages.arg("points", shown(team)),
                    Messages.arg("limit", match.map().kothPoints)));
        }
        lines.add(Component.text("      "));
        lines.add(plugin.messages().component("hill.sidebar-points-title"));
        for (Point point : points) lines.add(pointLine(point));
        // who wins first at the rates they score now, and when
        TeamColor first = null;
        int soonest = Integer.MAX_VALUE;
        for (TeamColor team : score.keySet()) {
            int seconds = secondsToWin(team);
            if (seconds >= 0 && seconds < soonest) {
                soonest = seconds;
                first = team;
            }
        }
        if (first != null) {
            lines.add(Component.text("       "));
            lines.add(plugin.messages().component("hill.sidebar-win-in", Messages.comp("team", Match.teamName(first)), Messages.arg("time", duration(soonest))));
        }
        return lines;
    }

    /** "▪ North Ring  Red +1/s", "▪ Great Cap  Blue 40%", "▪ South Ring  ⚔". */
    private Component pointLine(Point point) {
        Messages messages = plugin.messages();
        Component name = Component.text(label(point, true), point.owner == null ? NamedTextColor.WHITE : point.owner.text());
        if (point.contested) return messages.component("hill.point-line-contested", Messages.comp("point", name));
        if (point.capturer != null) {
            return messages.component("hill.point-line-capturing", Messages.comp("point", name),
                    Messages.comp("team", Match.teamName(point.capturer)), Messages.arg("seconds", secondsToCapture(point)));
        }
        if (point.owner == null) return messages.component("hill.point-line-free", Messages.comp("point", name));
        if (point.away > grace()) return messages.component("hill.point-line-draining", Messages.comp("point", name),
                Messages.comp("team", Match.teamName(point.owner)), Messages.arg("seconds", secondsToNeutral(point)));
        return messages.component("hill.point-line", Messages.comp("point", name), Messages.comp("team", Match.teamName(point.owner)));
    }

    @Override
    public String scoreLine() {
        List<String> parts = new ArrayList<>();
        for (TeamColor team : ranked()) {
            parts.add("<" + team.id() + ">" + team.display() + " " + shown(team) + "</" + team.id() + ">");
        }
        return String.join(" <dark_gray>-</dark_gray> ", parts);
    }

    @Override
    public java.util.List<String> endLines() {
        List<String> lines = new ArrayList<>();
        for (TeamColor team : ranked()) {
            int n = shown(team);
            lines.add("<" + team.id() + ">" + team.display() + ": " + n + (n == 1 ? " point" : " points") + "</" + team.id() + ">");
        }
        return lines;
    }

    @Override
    /**
     * Who wins when the time runs out: the team with the most points, counted exactly (half points and all), so a
     * match only ends in a draw when the top teams have exactly the same score (or nobody scored at all).
     */
    public TeamColor leader() {
        hideBar();
        removeLabels();
        TeamColor best = null;
        double top = 0, second = 0;
        for (TeamColor team : match.mode().teams()) {
            double points = score.getOrDefault(team, 0.0);
            if (points > top) {
                second = top;
                top = points;
                best = team;
            } else if (points > second) {
                second = points;
            }
        }
        if (best == null || top <= 0 || Math.abs(top - second) < 1e-9) return null;
        return best;
    }

    /**
     * Whether someone counts as on a point: its floor area plus a small margin round it (game.koth-zone-margin, 1.5
     * blocks out) and a little above and below (game.koth-zone-below 1, koth-zone-above 2), so standing on the edge
     * or jumping still counts, but you must be right at it.
     */
    private boolean inZone(Point point, Location at) {
        double margin = plugin.getConfig().getDouble("game.koth-zone-margin", 1.5);
        double below = plugin.getConfig().getDouble("game.koth-zone-below", 1);
        double above = plugin.getConfig().getDouble("game.koth-zone-above", 2);
        var min = point.area.min();
        var max = point.area.max();
        return at.getX() >= min.x() - margin && at.getX() < max.x() + 1 + margin
                && at.getZ() >= min.z() - margin && at.getZ() < max.z() + 1 + margin
                && at.getY() >= min.y() - below && at.getY() < max.y() + 1 + above;
    }

    private TeamColor leaderOrFirst() {
        TeamColor best = match.mode().teams().get(0);
        for (Map.Entry<TeamColor, Double> entry : score.entrySet()) if (entry.getValue() > score.get(best)) best = entry.getKey();
        return best;
    }
}
