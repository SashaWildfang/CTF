/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.game;

import com.sasha.ctf.FractalCTF;
import com.sasha.ctf.config.Messages;
import com.sasha.ctf.model.BlockPos;
import com.sasha.ctf.model.Cuboid;
import com.sasha.ctf.model.TeamColor;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * King of the Hill: every second that exactly one team has players on the hill, that team scores a point.
 * First to the map's koth-points wins; at the time limit, the most points wins.
 */
final class HillRules implements Rules {

    private final FractalCTF plugin;
    private final Match match;
    private final Cuboid hill;
    private final Map<TeamColor, Integer> points = new EnumMap<>(TeamColor.class);
    private final BossBar bar;
    private TeamColor holder;
    private int ticks;

    HillRules(FractalCTF plugin, Match match) {
        this.plugin = plugin;
        this.match = match;
        this.hill = match.map().hill();
        for (TeamColor team : match.mode().teams()) points.put(team, 0);
        this.bar = BossBar.bossBar(Component.empty(), 0f, BossBar.Color.WHITE, BossBar.Overlay.NOTCHED_10);
    }

    @Override
    public void start() {
        updateBar(null);
    }

    @Override
    public void tick() {
        ticks++;
        for (Player player : match.world().getPlayers()) player.showBossBar(bar);
        Set<TeamColor> onHill = EnumSet.noneOf(TeamColor.class);
        for (Player player : match.playing()) {
            if (!player.isDead() && hill.contains(player.getLocation())) onHill.add(match.teamOf(player.getUniqueId()));
        }
        TeamColor scoring = onHill.size() == 1 ? onHill.iterator().next() : null;
        if (scoring != holder) {
            if (scoring != null) {
                match.broadcast("hill.taken", Messages.comp("team", Match.teamName(scoring)));
                for (Player player : match.world().getPlayers()) player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.3f);
            } else if (onHill.size() > 1) {
                match.broadcast("hill.contested");
            }
            holder = scoring;
        }
        if (scoring != null) {
            int total = points.merge(scoring, 1, Integer::sum);
            if (total >= match.map().kothPoints) {
                bar.progress(1f);
                match.finish(scoring, null);
                hideBar();
                return;
            }
        }
        updateBar(onHill.size() > 1 ? "contested" : null);
        if (ticks % 2 == 0) outline(scoring);
    }

    private void updateBar(String state) {
        TeamColor leader = leaderOrFirst();
        int limit = match.map().kothPoints;
        bar.progress(Math.min(1f, points.get(leader) / (float) limit));
        if ("contested".equals(state)) {
            bar.name(plugin.messages().component("hill.bar-contested"));
            bar.color(BossBar.Color.WHITE);
        } else if (holder != null) {
            bar.name(plugin.messages().component("hill.bar-held", Messages.comp("team", Match.teamName(holder)),
                    Messages.arg("points", points.get(holder)), Messages.arg("limit", limit)));
            bar.color(holder == TeamColor.RED ? BossBar.Color.RED : holder == TeamColor.BLUE ? BossBar.Color.BLUE
                    : holder == TeamColor.GREEN ? BossBar.Color.GREEN : BossBar.Color.YELLOW);
        } else {
            bar.name(plugin.messages().component("hill.bar-free"));
            bar.color(BossBar.Color.WHITE);
        }
    }

    void hideBar() {
        for (Player player : match.world().getPlayers()) player.hideBossBar(bar);
    }

    /** Dust along the edges of the hill at its floor, in the holding team's colour. */
    private void outline(TeamColor scoring) {
        World world = match.world();
        Particle.DustOptions dust = new Particle.DustOptions(scoring == null ? org.bukkit.Color.WHITE : scoring.leather(), 1.5f);
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
        return List.of();
    }

    @Override
    public List<Component> lines() {
        List<Component> lines = new ArrayList<>();
        for (Map.Entry<TeamColor, Integer> entry : points.entrySet()) {
            lines.add(plugin.messages().component(entry.getKey() == holder ? "hill.sidebar-line-holding" : "hill.sidebar-line",
                    Messages.comp("team", Match.teamName(entry.getKey())), Messages.arg("points", entry.getValue()),
                    Messages.arg("limit", match.map().kothPoints)));
        }
        return lines;
    }

    @Override
    public String scoreLine() {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<TeamColor, Integer> entry : points.entrySet()) {
            parts.add("<" + entry.getKey().id() + ">" + entry.getKey().display() + " " + entry.getValue() + "</" + entry.getKey().id() + ">");
        }
        return String.join(" <dark_gray>-</dark_gray> ", parts);
    }

    @Override
    public TeamColor leader() {
        TeamColor best = null;
        int bestScore = -1;
        boolean tie = false;
        for (Map.Entry<TeamColor, Integer> entry : points.entrySet()) {
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
        for (Map.Entry<TeamColor, Integer> entry : points.entrySet()) if (entry.getValue() > points.get(best)) best = entry.getKey();
        return best;
    }
}
