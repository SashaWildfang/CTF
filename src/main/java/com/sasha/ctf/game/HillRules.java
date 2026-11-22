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
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * King of the Hill, capture and hold: a team that stands on a point alone for the map's capture time takes
 * it, and then scores a point every second it owns it, whether anyone is standing there or not. Enemies on
 * the point stop the clock (contested); an enemy alone first wears the owner's hold down, then captures it.
 * Maps can have one point or several (left, middle and right). First to the map's koth-points wins; at the
 * time limit, the most points wins.
 */
final class HillRules implements Rules {

    /** One capture point. Progress counts up to the capture time for {@code capturer}. */
    private static final class Point {
        final String name;
        final Cuboid area;
        TeamColor owner;
        TeamColor capturer;
        double progress;
        boolean contested;
        Block glass;
        String beam = "";

        Point(String name, Cuboid area) {
            this.name = name;
            this.area = area;
        }
    }

    private final FractalCTF plugin;
    private final Match match;
    private final List<Point> points = new ArrayList<>();
    private final Map<TeamColor, Integer> score = new EnumMap<>(TeamColor.class);
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
    }

    @Override
    public void prepare() {
        for (Point point : points) {
            point.glass = findBeacon(point.area);
            if (point.glass == null) point.glass = Structures.hillBeacon(match.world(), point.area);
        }
    }

    @Override
    public void start() {
        for (Point point : points) {
            if (point.glass == null) point.glass = findBeacon(point.area);
            beam(point);
        }
        updateBar();
    }

    /** A beacon in the hill (or up to 4 blocks under its floor): the block above it gets tinted glass. */
    private Block findBeacon(Cuboid hill) {
        World world = match.world();
        for (int x = hill.min().x(); x <= hill.max().x(); x++)
            for (int z = hill.min().z(); z <= hill.max().z(); z++)
                for (int y = hill.min().y() - 4; y <= hill.max().y(); y++) {
                    if (world.getBlockAt(x, y, z).getType() == org.bukkit.Material.BEACON) return world.getBlockAt(x, y + 1, z);
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
        org.bukkit.Material material = org.bukkit.Material.matchMaterial(glass + "_stained_glass");
        if (material != null) point.glass.setType(material, false);
    }

    private String label(Point point) {
        return points.size() == 1 ? plugin.messages().raw("hill.the-hill") : point.name;
    }

    @Override
    public void tick() {
        ticks++;
        for (Player player : match.world().getPlayers()) player.showBossBar(bar);
        for (Point point : points) tickPoint(point);
        // every owned point scores for its owner
        for (Point point : points) {
            if (point.owner == null) continue;
            int total = score.merge(point.owner, 1, Integer::sum);
            if (total >= match.map().kothPoints) {
                bar.progress(1f);
                match.finish(point.owner, null);
                hideBar();
                return;
            }
        }
        updateBar();
        if (ticks % 2 == 0) for (Point point : points) outline(point);
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
                            Messages.arg("point", label(point)));
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
        // what the players on the point see
        for (Player player : standing) {
            TeamColor team = match.teamOf(player.getUniqueId());
            if (point.contested) {
                plugin.messages().actionBar(player, "hill.action-contested", Messages.arg("point", label(point)));
            } else if (team == point.owner && point.capturer == null) {
                plugin.messages().actionBar(player, "hill.action-holding", Messages.arg("point", label(point)));
            } else {
                int percent = (int) Math.round(100 * point.progress / captureSeconds);
                plugin.messages().actionBar(player, point.capturer == team ? "hill.action-capturing" : "hill.action-clearing",
                        Messages.arg("point", label(point)), Messages.arg("percent", Math.max(0, Math.min(100, percent))));
            }
        }
        beam(point);
    }

    private void capture(Point point, TeamColor team, List<Player> standing) {
        TeamColor before = point.owner;
        point.owner = team;
        point.capturer = null;
        point.progress = 0;
        if (before == null) match.broadcast("hill.captured", Messages.comp("team", Match.teamName(team)), Messages.arg("point", label(point)));
        else match.broadcast("hill.captured-from", Messages.comp("team", Match.teamName(team)), Messages.arg("point", label(point)),
                Messages.comp("from", Match.teamName(before)));
        for (Player player : match.world().getPlayers()) {
            TeamColor mine = match.teamOf(player.getUniqueId());
            player.playSound(player.getLocation(), mine == team ? Sound.BLOCK_BEACON_POWER_SELECT : Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.2f);
        }
        for (Player player : standing) {
            if (match.teamOf(player.getUniqueId()) == team) {
                match.stats(player.getUniqueId()).captures++;
                match.award(player, plugin.ctfConfig().coins().hill(), "hill");
            }
        }
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
        for (Map.Entry<TeamColor, Integer> entry : score.entrySet()) {
            scores.add(Component.text(entry.getKey().display() + " " + entry.getValue(), entry.getKey().text()));
        }
        bar.name(plugin.messages().component("hill.bar", Messages.comp("points", status),
                Messages.comp("scores", Component.join(net.kyori.adventure.text.JoinConfiguration.separator(
                        Component.text(" - ", NamedTextColor.DARK_GRAY)), scores)), Messages.arg("limit", limit)));
        TeamColor owner = points.size() == 1 ? points.get(0).owner : leader;
        bar.color(owner == TeamColor.RED ? BossBar.Color.RED : owner == TeamColor.BLUE ? BossBar.Color.BLUE
                : owner == TeamColor.GREEN ? BossBar.Color.GREEN : owner == TeamColor.YELLOW ? BossBar.Color.YELLOW : BossBar.Color.WHITE);
    }

    /** "[A]" in the owner's colour (gray while contested, with the capture % while someone's taking it). */
    private Component pointTag(Point point) {
        String key = point.contested ? "hill.tag-contested" : point.capturer != null ? "hill.tag-capturing" : "hill.tag";
        Component name = Component.text(points.size() == 1 ? plugin.messages().raw("hill.short") : point.name,
                point.owner == null ? NamedTextColor.WHITE : point.owner.text());
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

    @Override
    public List<Component> lines() {
        List<Component> lines = new ArrayList<>();
        Set<TeamColor> owning = EnumSet.noneOf(TeamColor.class);
        for (Point point : points) if (point.owner != null) owning.add(point.owner);
        for (Map.Entry<TeamColor, Integer> entry : score.entrySet()) {
            lines.add(plugin.messages().component(owning.contains(entry.getKey()) ? "hill.sidebar-line-holding" : "hill.sidebar-line",
                    Messages.comp("team", Match.teamName(entry.getKey())), Messages.arg("points", entry.getValue()),
                    Messages.arg("limit", match.map().kothPoints)));
        }
        if (points.size() > 1) {
            List<Component> parts = new ArrayList<>();
            for (Point point : points) parts.add(pointTag(point));
            lines.add(Component.join(net.kyori.adventure.text.JoinConfiguration.separator(Component.text(" ")), parts));
        }
        return lines;
    }

    @Override
    public String scoreLine() {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<TeamColor, Integer> entry : score.entrySet()) {
            parts.add("<" + entry.getKey().id() + ">" + entry.getKey().display() + " " + entry.getValue() + "</" + entry.getKey().id() + ">");
        }
        return String.join(" <dark_gray>-</dark_gray> ", parts);
    }

    @Override
    public TeamColor leader() {
        TeamColor best = null;
        int bestScore = -1;
        boolean tie = false;
        for (Map.Entry<TeamColor, Integer> entry : score.entrySet()) {
            if (entry.getValue() > bestScore) {
                best = entry.getKey();
                bestScore = entry.getValue();
                tie = false;
            } else if (entry.getValue() == bestScore) {
                tie = true;
            }
        }
        hideBar();
        return tie ? null : best;
    }

    private TeamColor leaderOrFirst() {
        TeamColor best = match.mode().teams().get(0);
        for (Map.Entry<TeamColor, Integer> entry : score.entrySet()) if (entry.getValue() > score.get(best)) best = entry.getKey();
        return best;
    }
}
