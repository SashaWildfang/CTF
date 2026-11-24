/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.game;

import com.sasha.ctf.FractalCTF;
import com.sasha.ctf.config.Messages;
import com.sasha.ctf.model.BlockPos;
import com.sasha.ctf.model.TeamColor;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Destroy the Wool: each team guards wool blocks in its colour. Enemies break them; a team's own players
 * can't. A team with no wool left is out, and the last team with wool wins. At the time limit, the team
 * with the most wool left wins.
 */
final class WoolRules implements Rules {

    private final FractalCTF plugin;
    private final Match match;
    private final Map<TeamColor, Set<BlockPos>> wool = new EnumMap<>(TeamColor.class);
    private final Map<TeamColor, Integer> total = new EnumMap<>(TeamColor.class);
    /** How many of a team's wool must be broken to knock it out (game.wool-to-break, 10), and how many have been. */
    private final Map<TeamColor, Integer> toBreak = new EnumMap<>(TeamColor.class);
    private final Map<TeamColor, Integer> broken = new EnumMap<>(TeamColor.class);
    /** Wool grouped into monuments by the beacon each sits next to; the beacon's glass shows the team until it falls. */
    private final Map<Block, Set<BlockPos>> monuments = new java.util.HashMap<>();
    private final Map<Block, TeamColor> monumentTeam = new java.util.HashMap<>();

    /** Monuments the plugin built (their glass is tinted black when they fall). */
    private final Map<Block, List<Block>> monumentGlass = new java.util.HashMap<>();
    /** Every wool block there was at the start (protected from building around). */
    private final List<BlockPos> allWool = new ArrayList<>();

    WoolRules(FractalCTF plugin, Match match) {
        this.plugin = plugin;
        this.match = match;
    }

    /** Builds a wool tower on every monument pad (maps made before 1.4 list their wool blocks instead). */
    @Override
    public void prepare() {
        for (TeamColor team : match.mode().teams()) {
            var points = match.map().teams.get(team);
            Set<BlockPos> blocks = new LinkedHashSet<>(points.wools);
            for (BlockPos pad : points.monuments) {
                Structures.decor = plugin.getConfig().getBoolean("game.wool-tower-decor", true);
                Structures.Monument built = Structures.woolTower(match.world(), pad, team, match.map().woolLayers);
                blocks.addAll(built.wool());
                monuments.put(built.beacon(), new LinkedHashSet<>(built.wool()));
                monumentTeam.put(built.beacon(), team);
                monumentGlass.put(built.beacon(), built.glass());
            }
            wool.put(team, blocks);
            total.put(team, blocks.size());
            int need = plugin.getConfig().getInt("game.wool-to-break", 10);
            toBreak.put(team, need <= 0 ? blocks.size() : Math.min(need, blocks.size()));
            broken.put(team, 0);
            allWool.addAll(blocks);
        }
    }

    @Override
    public void start() {
        if (!monumentGlass.isEmpty()) return; // built in prepare()
        for (Map.Entry<TeamColor, Set<BlockPos>> entry : wool.entrySet()) {
            for (BlockPos pos : entry.getValue()) {
                pos.block(match.world()).setType(entry.getKey().wool(), false);
                Block beacon = nearestBeacon(pos);
                if (beacon == null) continue;
                monuments.computeIfAbsent(beacon, b -> new LinkedHashSet<>()).add(pos);
                monumentTeam.put(beacon, entry.getKey());
            }
        }
        for (Map.Entry<Block, TeamColor> entry : monumentTeam.entrySet()) beam(entry.getKey(), entry.getValue());
    }

    private Block nearestBeacon(BlockPos pos) {
        Block best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -3; dx <= 3; dx++)
            for (int dy = -3; dy <= 1; dy++)
                for (int dz = -3; dz <= 3; dz++) {
                    Block block = match.world().getBlockAt(pos.x() + dx, pos.y() + dy, pos.z() + dz);
                    if (block.getType() != org.bukkit.Material.BEACON) continue;
                    double distance = dx * dx + dy * dy + dz * dz;
                    if (distance < bestDistance) {
                        best = block;
                        bestDistance = distance;
                    }
                }
        return best;
    }

    /** Tints the glass above a monument's beacon: the team colour, or black once its wool is gone. */
    private void beam(Block beacon, TeamColor team) {
        List<Block> column = monumentGlass.get(beacon);
        if (column != null) {
            for (Block glass : column) glass.setType(Structures.glass(team), false);
            return;
        }
        Block glass = beacon.getRelative(org.bukkit.block.BlockFace.UP);
        String colour = team == null ? "black" : team == TeamColor.GREEN ? "lime" : team.id();
        org.bukkit.Material material = org.bukkit.Material.matchMaterial(colour + "_stained_glass");
        if (material != null) glass.setType(material, false);
    }

    @Override
    public void tick() {
    }

    @Override
    public Result onBreak(Player player, Block block) {
        BlockPos pos = BlockPos.of(block);
        for (List<Block> column : monumentGlass.values()) if (column.contains(block)) return Result.DENY;  // the beam's glass
        TeamColor breaker = match.teamOf(player.getUniqueId());
        for (Map.Entry<TeamColor, Set<BlockPos>> entry : wool.entrySet()) {
            if (!entry.getValue().contains(pos)) continue;
            TeamColor owner = entry.getKey();
            if (owner == breaker) {
                plugin.messages().send(player, "wool.own");
                return Result.DENY;
            }
            entry.getValue().remove(pos);
            int done = broken.merge(owner, 1, Integer::sum);
            int left = Math.max(0, toBreak.get(owner) - done);
            for (Map.Entry<Block, Set<BlockPos>> monument : monuments.entrySet()) {
                if (monument.getValue().remove(pos) && monument.getValue().isEmpty()) {
                    beam(monument.getKey(), null);
                    match.broadcast("wool.monument-down", Messages.comp("team", Match.teamName(owner)));
                }
            }
            match.stats(player.getUniqueId()).wools++;
            int slow = plugin.ctfConfig().woolSlowness();
            if (slow > 0) {
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS, slow * 20, 1, false, true, true));
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.MINING_FATIGUE, slow * 20,
                        Math.max(0, plugin.getConfig().getInt("game.wool-mining-fatigue", 1) - 1), false, true, true));
                plugin.messages().actionBar(player, "wool.slowed", Messages.arg("seconds", slow));
            }
            match.award(player, plugin.getConfig().getInt("coins.wool", 3), "wool", Messages.comp("team", Match.teamName(owner)));
            match.broadcast("wool.destroyed", Messages.comp("player", Component.text(player.getName(), breaker.text())),
                    Messages.comp("team", Match.teamName(owner)), Messages.arg("left", left), Messages.arg("total", toBreak.get(owner)));
            for (Player everyone : match.world().getPlayers()) everyone.playSound(everyone.getLocation(), Sound.ENTITY_WITHER_BREAK_BLOCK, 0.6f, 1.4f);
            if (left == 0) {
                match.broadcast("wool.team-out", Messages.comp("team", Match.teamName(owner)));
                collapse(owner);
                List<TeamColor> standing = new ArrayList<>();
                for (TeamColor other : wool.keySet()) if (toBreak.get(other) - broken.get(other) > 0) standing.add(other);
                if (standing.size() == 1) match.finish(standing.get(0), null);
                else if (standing.isEmpty()) match.finish(null, null);
            }
            return Result.ALLOW;
        }
        return Result.PASS;
    }

    /** A team is out: the rest of its wool falls away and its beams go dark. */
    private void collapse(TeamColor team) {
        World world = match.world();
        for (BlockPos pos : new ArrayList<>(wool.get(team))) {
            Block block = pos.block(world);
            org.bukkit.block.data.BlockData data = block.getBlockData();
            block.setType(org.bukkit.Material.AIR, false);
            world.spawnParticle(org.bukkit.Particle.BLOCK, block.getLocation().add(0.5, 0.5, 0.5), 6, 0.3, 0.3, 0.3, data);
        }
        wool.get(team).clear();
        for (Map.Entry<Block, TeamColor> entry : monumentTeam.entrySet()) if (entry.getValue() == team) beam(entry.getKey(), null);
        for (Player everyone : world.getPlayers()) everyone.playSound(everyone.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 0.8f);
    }

    /** Wool still to break before a team is out. */
    private int left(TeamColor team) {
        return Math.max(0, toBreak.get(team) - broken.get(team));
    }

    @Override
    public List<BlockPos> objectiveBlocks() {
        List<BlockPos> blocks = new ArrayList<>();
        blocks.addAll(allWool);
        for (Block beacon : monumentTeam.keySet()) blocks.add(BlockPos.of(beacon));
        return blocks;
    }

    @Override
    public List<Component> lines() {
        List<Component> lines = new ArrayList<>();
        for (TeamColor team : wool.keySet()) {
            lines.add(plugin.messages().component("wool.sidebar-line", Messages.comp("team", Match.teamName(team)),
                    Messages.arg("left", left(team)), Messages.arg("total", toBreak.get(team)),
                    Messages.comp("square", Component.text(left(team) > 0 ? "■" : "✖", team.text()))));
        }
        return lines;
    }

    @Override
    public String scoreLine() {
        List<String> parts = new ArrayList<>();
        for (TeamColor team : wool.keySet()) {
            String id = team.id();
            parts.add("<" + id + ">" + team.display() + " " + left(team) + "/" + toBreak.get(team) + " " + (left(team) > 0 ? "■" : "✖") + "</" + id + ">");
        }
        return String.join(" <dark_gray>-</dark_gray> ", parts);
    }

    /** Wool is counted out of 10 at most: with 20 blocks, every 2 broken is one fewer. Never shows 0 while some is left. */
    static int shown(int left, int total) {
        if (total <= 10 || left <= 0) return left;
        return (int) Math.ceil(left * 10.0 / total);
    }

    static int shownTotal(int total) {
        return Math.min(10, total);
    }

    @Override
    public TeamColor leader() {
        TeamColor best = null;
        int bestLeft = -1;
        boolean tie = false;
        for (Map.Entry<TeamColor, Set<BlockPos>> entry : wool.entrySet()) {
            int left = left(entry.getKey());
            if (left > bestLeft) {
                best = entry.getKey();
                bestLeft = left;
                tie = false;
            } else if (left == bestLeft) {
                tie = true;
            }
        }
        return tie ? null : best;
    }
}
