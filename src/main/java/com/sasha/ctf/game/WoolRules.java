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
    /** Wool grouped into monuments by the beacon each sits next to; the beacon's glass shows the team until it falls. */
    private final Map<Block, Set<BlockPos>> monuments = new java.util.HashMap<>();
    private final Map<Block, TeamColor> monumentTeam = new java.util.HashMap<>();

    WoolRules(FractalCTF plugin, Match match) {
        this.plugin = plugin;
        this.match = match;
        for (TeamColor team : match.mode().teams()) {
            Set<BlockPos> blocks = new LinkedHashSet<>(match.map().teams.get(team).wools);
            wool.put(team, blocks);
            total.put(team, blocks.size());
        }
    }

    @Override
    public void start() {
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
        TeamColor breaker = match.teamOf(player.getUniqueId());
        for (Map.Entry<TeamColor, Set<BlockPos>> entry : wool.entrySet()) {
            if (!entry.getValue().contains(pos)) continue;
            TeamColor owner = entry.getKey();
            if (owner == breaker) {
                plugin.messages().send(player, "wool.own");
                return Result.DENY;
            }
            entry.getValue().remove(pos);
            int left = entry.getValue().size();
            for (Map.Entry<Block, Set<BlockPos>> monument : monuments.entrySet()) {
                if (monument.getValue().remove(pos) && monument.getValue().isEmpty()) {
                    beam(monument.getKey(), null);
                    match.broadcast("wool.monument-down", Messages.comp("team", Match.teamName(owner)));
                }
            }
            match.stats(player.getUniqueId()).captures++;
            match.award(player, plugin.ctfConfig().coins().objective(), "wool", Messages.comp("team", Match.teamName(owner)));
            match.broadcast("wool.destroyed", Messages.comp("player", Component.text(player.getName(), breaker.text())),
                    Messages.comp("team", Match.teamName(owner)), Messages.arg("left", left));
            for (Player everyone : match.world().getPlayers()) everyone.playSound(everyone.getLocation(), Sound.ENTITY_WITHER_BREAK_BLOCK, 0.6f, 1.4f);
            if (left == 0) {
                match.broadcast("wool.team-out", Messages.comp("team", Match.teamName(owner)));
                List<TeamColor> standing = new ArrayList<>();
                for (Map.Entry<TeamColor, Set<BlockPos>> other : wool.entrySet()) if (!other.getValue().isEmpty()) standing.add(other.getKey());
                if (standing.size() == 1) match.finish(standing.get(0), null);
                else if (standing.isEmpty()) match.finish(null, null);
            }
            return Result.ALLOW;
        }
        return Result.PASS;
    }

    @Override
    public List<BlockPos> objectiveBlocks() {
        List<BlockPos> blocks = new ArrayList<>();
        for (TeamColor team : match.mode().teams()) blocks.addAll(match.map().teams.get(team).wools);
        for (Block beacon : monumentTeam.keySet()) blocks.add(BlockPos.of(beacon));
        return blocks;
    }

    @Override
    public List<Component> lines() {
        List<Component> lines = new ArrayList<>();
        for (Map.Entry<TeamColor, Set<BlockPos>> entry : wool.entrySet()) {
            lines.add(plugin.messages().component("wool.sidebar-line", Messages.comp("team", Match.teamName(entry.getKey())),
                    Messages.arg("left", entry.getValue().size()), Messages.arg("total", total.get(entry.getKey()))));
        }
        return lines;
    }

    @Override
    public String scoreLine() {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<TeamColor, Set<BlockPos>> entry : wool.entrySet()) {
            String id = entry.getKey().id();
            parts.add("<" + id + ">" + entry.getKey().display() + " " + entry.getValue().size() + "/" + total.get(entry.getKey()) + "</" + id + ">");
        }
        return String.join(" <dark_gray>-</dark_gray> ", parts);
    }

    @Override
    public TeamColor leader() {
        TeamColor best = null;
        int bestLeft = -1;
        boolean tie = false;
        for (Map.Entry<TeamColor, Set<BlockPos>> entry : wool.entrySet()) {
            int left = entry.getValue().size();
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
