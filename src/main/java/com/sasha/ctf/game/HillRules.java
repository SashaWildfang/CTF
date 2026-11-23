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
 * Each point has a label over it (who holds it, what it is worth, how long until its holder wins at the rate
 * it is scoring), its edge turns the holder's colour in a ripple when it is captured, and the whole match is
 * warned as a team closes in on the win.
 */
final class HillRules implements Rules {

    /** One capture point. Progress counts up to the capture time for {@code capturer}. */
    private static final class Point {
        final String name;
        final Cuboid area;
        int value = 1;
        TeamColor owner;
        TeamColor capturer;
        double progress;
        boolean contested;
        Block glass;
        String beam = "";
        TextDisplay label;
        /** The floor blocks round the point's edge, as the map had them: they take the holder's colour. */
        final Map<Block, BlockData> edge = new LinkedHashMap<>();

        Point(String name, Cuboid area) {
            this.name = name;
            this.area = area;
        }

        Location centre(World world) {
            return new Location(world, (area.min().x() + area.max().x() + 1) / 2.0, area.min().y(), (area.min().z() + area.max().z() + 1) / 2.0);
        }
    }

    private static final int[] WARN_AT = {120, 60, 30, 10};

    private final FractalCTF plugin;
    private final Match match;
    private final List<Point> points = new ArrayList<>();
    private final Map<TeamColor, Integer> score = new EnumMap<>(TeamColor.class);
    /** The last "will win in" warning each team got, so each is said once (until the team falls back). */
    private final Map<TeamColor, Integer> warned = new EnumMap<>(TeamColor.class);
    /** Seconds each player has stood on a point their team holds since their last holding coins. */
    private final Map<UUID, Integer> holding = new HashMap<>();
    private final BossBar bar;
    private final int captureSeconds;
    private int ticks;

    HillRules(FractalCTF plugin, Match match) {
        this.plugin = plugin;
        this.match = match;
        for (MapDef.HillDef hill : match.map().allHills()) points.add(new Point(hill.name(), hill.area()));
        for (TeamColor team : match.mode().teams()) score.put(team, 0);
        this.captureSeconds = match.map().captureSeconds;
        this.bar = BossBar.bossBar(Component.empty(), 0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
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
            if (point.glass == null) point.glass = Structures.hillBeacon(match.world(), point.area);
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
        updateBar();
    }

    /** The solid floor blocks along the point's border, nearest the middle first (for the ripple). */
    private void rememberEdge(Point point) {
        World world = match.world();
        Cuboid hill = point.area;
        int y = hill.min().y() - 1;
        Location centre = point.centre(world);
        List<Block> edge = new ArrayList<>();
        for (int x = hill.min().x(); x <= hill.max().x(); x++) {
            for (int z = hill.min().z(); z <= hill.max().z(); z++) {
                boolean border = x == hill.min().x() || x == hill.max().x() || z == hill.min().z() || z == hill.max().z();
                if (!border) continue;
                Block block = world.getBlockAt(x, y, z);
                if (block.equals(point.glass) || !block.getType().isSolid() || !block.getType().isOccluding()) continue;
                edge.add(block);
            }
        }
        edge.sort(java.util.Comparator.comparingDouble(b -> b.getLocation().add(0.5, 0, 0.5).distanceSquared(centre)));
        for (Block block : edge) point.edge.put(block, block.getBlockData());
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

    /** Points a second the team is scoring now. */
    private int rate(TeamColor team) {
        int rate = 0;
        for (Point point : points) if (point.owner == team) rate += point.value;
        return rate;
    }

    /** Seconds until the team wins at the rate it scores now; -1 if it isn't scoring. */
    private int secondsToWin(TeamColor team) {
        int rate = rate(team);
        if (rate <= 0) return -1;
        int left = Math.max(0, match.map().kothPoints - score.get(team));
        return (left + rate - 1) / rate;
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
        for (Player player : match.world().getPlayers()) player.showBossBar(bar);
        for (Point point : points) tickPoint(point);
        // every owned point scores for its owner
        for (Point point : points) {
            if (point.owner == null) continue;
            int total = score.merge(point.owner, point.value, Integer::sum);
            if (total >= match.map().kothPoints) {
                score.put(point.owner, match.map().kothPoints);
                bar.progress(1f);
                removeLabels();
                match.finish(point.owner, null);
                hideBar();
                return;
            }
        }
        warnings();
        updateBar();
        for (Point point : points) label(point);
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
            if (player.isDead() || !point.area.contains(player.getLocation())) continue;
            TeamColor team = match.teamOf(player.getUniqueId());
            if (team == null) continue;
            on.merge(team, 1, Integer::sum);
            standing.add(player);
        }
        point.contested = on.size() > 1;
        if (on.size() == 1) {
            TeamColor team = on.keySet().iterator().next();
            int people = on.get(team);
            double rate = Math.min(2.0, 1.0 + 0.25 * (people - 1)); // more people capture faster, up to twice as fast
            if (team == point.owner) {
                // the owners push back any enemy progress
                point.progress = Math.max(0, point.progress - rate);
                if (point.progress == 0) point.capturer = null;
            } else if (point.capturer != null && point.capturer != team) {
                // someone else had started: wear their progress down first
                point.progress -= rate;
                if (point.progress <= 0) {
                    point.progress = 0;
                    point.capturer = team;
                }
            } else {
                if (point.capturer == null) {
                    point.capturer = team;
                    if (point.owner != null) match.broadcast("hill.under-attack", Messages.comp("team", Match.teamName(point.owner)),
                            Messages.arg("point", label(point, false)));
                }
                point.progress += rate;
                if (point.progress >= captureSeconds) capture(point, team, standing);
            }
            for (Player player : standing) match.stats(player.getUniqueId()).hillSeconds++;
        } else if (on.isEmpty() && point.capturer != null) {
            // nobody there: half-finished captures slowly fade
            point.progress = Math.max(0, point.progress - 0.5);
            if (point.progress == 0) point.capturer = null;
        }
        // coins for holding: standing on a point your team owns, uncontested
        int every = Math.max(5, plugin.getConfig().getInt("coins.hill-hold-seconds", 15));
        int coins = plugin.getConfig().getInt("coins.hill-hold", 1);
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
                int percent = (int) Math.round(100 * point.progress / captureSeconds);
                plugin.messages().actionBar(player, point.capturer == team ? "hill.action-capturing" : "hill.action-clearing",
                        Messages.arg("point", label(point, false)), Messages.arg("percent", Math.max(0, Math.min(100, percent))));
            }
        }
        beam(point);
    }

    private void capture(Point point, TeamColor team, List<Player> standing) {
        TeamColor before = point.owner;
        point.owner = team;
        point.capturer = null;
        point.progress = 0;
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
            if (before != null) match.award(player, plugin.getConfig().getInt("coins.hill-retake", 2), "hill-retake",
                    Messages.arg("point", label(point, false)), Messages.comp("team", Match.teamName(before)));
        }
        ripple(point, team);
    }

    /** The point's edge turns the new holder's colour, from the middle outward, with a burst of its blocks. */
    private void ripple(Point point, TeamColor team) {
        World world = match.world();
        Material concrete = Material.valueOf((team == TeamColor.GREEN ? "LIME" : team.name()) + "_CONCRETE");
        BlockData colour = concrete.createBlockData();
        Location centre = point.centre(world);
        world.spawnParticle(Particle.FIREWORK, centre.clone().add(0, 1.5, 0), 60, 1.5, 0.5, 1.5, 0.08);
        world.playSound(centre, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1.5f, 1f);
        int i = 0;
        for (Block block : point.edge.keySet()) {
            long delay = 1 + (long) (block.getLocation().add(0.5, 0, 0.5).distance(centre) * 1.5);
            int index = i++;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (match.phase() == Match.Phase.OVER || point.owner != team) return;
                block.setBlockData(colour, false);
                Location at = block.getLocation().add(0.5, 1.05, 0.5);
                world.spawnParticle(Particle.BLOCK, at, 6, 0.3, 0.05, 0.3, colour);
                if (index % 4 == 0) world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 1.4f + (index % 8) * 0.05f);
            }, delay);
        }
    }

    // ---------------------------------------------------------------- the label over each point

    private void label(Point point) {
        World world = match.world();
        if (point.label == null || !point.label.isValid()) {
            Location at = point.centre(world).add(0, Math.max(3.2, point.area.max().y() - point.area.min().y() + 1.2), 0);
            point.label = world.spawn(at, TextDisplay.class, display -> {
                display.setPersistent(false);
                display.setBillboard(Display.Billboard.CENTER);
                display.setShadowed(true);
                display.setBackgroundColor(org.bukkit.Color.fromARGB(90, 0, 0, 0));
                display.setLineWidth(400);
                display.setViewRange(1.2f);
            });
        }
        Messages messages = plugin.messages();
        List<Component> lines = new ArrayList<>();
        Component name = Component.text(label(point, true), point.owner == null ? NamedTextColor.WHITE : point.owner.text());
        lines.add(messages.component("hill.label.name", Messages.comp("point", name), Messages.arg("value", point.value)));
        if (point.contested) {
            lines.add(messages.component("hill.label.contested"));
        } else if (point.capturer != null) {
            int percent = (int) Math.round(100 * point.progress / captureSeconds);
            lines.add(messages.component("hill.label.capturing", Messages.comp("team", Match.teamName(point.capturer)),
                    Messages.arg("percent", Math.max(0, Math.min(100, percent))), Messages.parsed("bar", progressBar(point.progress / captureSeconds, point.capturer))));
        } else if (point.owner == null) {
            lines.add(messages.component("hill.label.free"));
        } else {
            lines.add(messages.component("hill.label.held", Messages.comp("team", Match.teamName(point.owner))));
        }
        if (point.owner != null) {
            int seconds = secondsToWin(point.owner);
            if (seconds >= 0) lines.add(messages.component("hill.label.win-in", Messages.comp("team", Match.teamName(point.owner)),
                    Messages.arg("time", duration(seconds)), Messages.arg("rate", rate(point.owner))));
        }
        Component text = Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(), lines);
        if (!text.equals(point.label.text())) point.label.text(text);
    }

    private static String progressBar(double done, TeamColor team) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, done)) * 10);
        return "<" + team.id() + ">" + "|".repeat(filled) + "</" + team.id() + "><dark_gray>" + "|".repeat(10 - filled) + "</dark_gray>";
    }

    private void removeLabels() {
        for (Point point : points) if (point.label != null) point.label.remove();
    }

    /** One bar for everyone: each point's owner, and the leading score. */
    private void updateBar() {
        TeamColor leader = leaderOrFirst();
        int limit = match.map().kothPoints;
        bar.progress(Math.max(0f, Math.min(1f, score.get(leader) / (float) limit)));
        List<Component> parts = new ArrayList<>();
        for (Point point : points) parts.add(pointTag(point));
        Component status = Component.join(net.kyori.adventure.text.JoinConfiguration.separator(Component.text("  ")), parts);
        List<Component> scores = new ArrayList<>();
        for (TeamColor team : ranked()) scores.add(Component.text(team.display() + " " + score.get(team), team.text()));
        bar.name(plugin.messages().component("hill.bar", Messages.comp("points", status),
                Messages.comp("scores", Component.join(net.kyori.adventure.text.JoinConfiguration.separator(
                        Component.text(" - ", NamedTextColor.DARK_GRAY)), scores)), Messages.arg("limit", limit)));
        TeamColor owner = points.size() == 1 ? points.get(0).owner : strictLeader();
        bar.color(owner == TeamColor.RED ? BossBar.Color.RED : owner == TeamColor.BLUE ? BossBar.Color.BLUE
                : owner == TeamColor.GREEN ? BossBar.Color.GREEN : owner == TeamColor.YELLOW ? BossBar.Color.YELLOW : BossBar.Color.WHITE);
    }

    /** "[North Ring]" in the owner's colour (gray while contested, with the capture % while someone's taking it). */
    private Component pointTag(Point point) {
        String key = point.contested ? "hill.tag-contested" : point.capturer != null ? "hill.tag-capturing" : "hill.tag";
        Component name = Component.text(label(point, true), point.owner == null ? NamedTextColor.WHITE : point.owner.text());
        int percent = (int) Math.round(100 * point.progress / captureSeconds);
        return plugin.messages().component(key, Messages.comp("point", name), Messages.arg("percent", Math.max(0, Math.min(100, percent))),
                Messages.comp("capturer", point.capturer == null ? Component.empty() : Component.text(point.capturer.display(), point.capturer.text())));
    }

    void hideBar() {
        for (Player player : match.world().getPlayers()) player.hideBossBar(bar);
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
        teams.sort((a, b) -> Integer.compare(score.get(b), score.get(a)));
        return teams;
    }

    /** The team with the most points, or null while nobody has any or the top is tied. */
    private TeamColor strictLeader() {
        List<TeamColor> teams = ranked();
        if (teams.isEmpty() || score.get(teams.get(0)) == 0) return null;
        if (teams.size() > 1 && score.get(teams.get(1)).equals(score.get(teams.get(0)))) return null;
        return teams.get(0);
    }

    /** The teams (most points first, the leader crowned), then each point on its own line. */
    @Override
    public List<Component> lines() {
        List<Component> lines = new ArrayList<>();
        TeamColor leader = strictLeader();
        for (TeamColor team : ranked()) {
            lines.add(plugin.messages().component(team == leader ? "hill.sidebar-line-leading" : "hill.sidebar-line",
                    Messages.comp("team", Match.teamName(team)), Messages.arg("points", score.get(team)),
                    Messages.arg("limit", match.map().kothPoints)));
        }
        lines.add(Component.text("      "));
        for (Point point : points) lines.add(pointLine(point));
        return lines;
    }

    /** "▪ North Ring  Red +1/s", "▪ Great Cap  Blue 40%", "▪ South Ring  ⚔". */
    private Component pointLine(Point point) {
        Messages messages = plugin.messages();
        Component name = Component.text(label(point, true), point.owner == null ? NamedTextColor.WHITE : point.owner.text());
        if (point.contested) return messages.component("hill.point-line-contested", Messages.comp("point", name));
        if (point.capturer != null) {
            int percent = (int) Math.round(100 * point.progress / captureSeconds);
            return messages.component("hill.point-line-capturing", Messages.comp("point", name),
                    Messages.comp("team", Match.teamName(point.capturer)), Messages.arg("percent", Math.max(0, Math.min(100, percent))));
        }
        if (point.owner == null) return messages.component("hill.point-line-free", Messages.comp("point", name), Messages.arg("value", point.value));
        return messages.component("hill.point-line", Messages.comp("point", name), Messages.comp("team", Match.teamName(point.owner)),
                Messages.arg("value", point.value));
    }

    @Override
    public String scoreLine() {
        List<String> parts = new ArrayList<>();
        for (TeamColor team : ranked()) {
            parts.add("<" + team.id() + ">" + team.display() + " " + score.get(team) + "</" + team.id() + ">");
        }
        return String.join(" <dark_gray>-</dark_gray> ", parts);
    }

    @Override
    public TeamColor leader() {
        hideBar();
        removeLabels();
        return strictLeader();
    }

    private TeamColor leaderOrFirst() {
        TeamColor best = match.mode().teams().get(0);
        for (Map.Entry<TeamColor, Integer> entry : score.entrySet()) if (entry.getValue() > score.get(best)) best = entry.getKey();
        return best;
    }
}
