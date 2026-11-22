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
            for (BlockPos pos : entry.getValue()) pos.block(match.world()).setType(entry.getKey().wool(), false);
        }
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
            match.stats(player.getUniqueId()).captures++;
            match.award(player, plugin.ctfConfig().coins().objective(), "wool");
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
