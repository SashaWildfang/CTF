/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.game;

import com.sasha.ctf.FractalCTF;
import com.sasha.ctf.config.Messages;
import com.sasha.ctf.model.BlockPos;
import com.sasha.ctf.model.MapDef.WoolGoal;
import com.sasha.ctf.model.TeamColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Capture the Wool: each team has wools to bring home (map.yml ctw-wools). The wool is in the enemy's wool
 * room (its source blocks): hitting a source gives the attacker one wool and the block stays. Placing that wool
 * on the team's own podium captures it. The first team with every wool home wins; at the time limit, the team
 * with the most captured (then the most picked up) wins.
 */
final class CaptureWoolRules implements Rules {

    private final FractalCTF plugin;
    private final Match match;
    private final List<WoolGoal> goals;
    private final Map<WoolGoal, Set<BlockPos>> sources = new LinkedHashMap<>();
    private final Set<WoolGoal> captured = new HashSet<>();
    /** Goals someone has picked up at least once (a tie-break and the sidebar's "touched" mark). */
    private final Set<WoolGoal> touched = new HashSet<>();
    /** Who carries which goal's wool right now. */
    private final Map<WoolGoal, Set<UUID>> carriers = new HashMap<>();
    /** Players we made glow (so we only turn off what we turned on). */
    private final Set<UUID> glowing = new HashSet<>();
    /** First pick-up coins once per player per goal. */
    private final Set<String> paidPickup = new HashSet<>();

    CaptureWoolRules(FractalCTF plugin, Match match) {
        this.plugin = plugin;
        this.match = match;
        this.goals = new ArrayList<>(match.map().woolGoals);
        goals.removeIf(g -> !match.mode().teams().contains(g.team()));
    }

    @Override
    public void start() {
        for (WoolGoal goal : goals) {
            Set<BlockPos> set = new HashSet<>(goal.sources());
            if (set.isEmpty()) set.addAll(scan(goal));
            for (BlockPos pos : set) {
                Block block = pos.block(match.world());
                if (block.getType().isAir()) block.setType(goal.material(), false);   // chests and wool already there stay
            }
            sources.put(goal, set);
            carriers.put(goal, new HashSet<>());
            // an empty podium: nothing may sit there until the wool comes home
            Block podium = goal.podium().block(match.world());
            if (podium.getType() != Material.AIR) podium.setType(Material.AIR, false);
        }
    }

    /** No sources listed: wool of the goal's colour in loaded chunks (not on any podium). */
    private Set<BlockPos> scan(WoolGoal goal) {
        Set<BlockPos> found = new HashSet<>();
        Material material = goal.material();
        for (var chunk : match.world().getLoadedChunks()) {
            var snap = chunk.getChunkSnapshot(false, false, false);
            int minY = match.world().getMinHeight(), maxY = match.world().getMaxHeight();
            for (int x = 0; x < 16; x++)
                for (int z = 0; z < 16; z++)
                    for (int y = minY; y < maxY; y++) {
                        if (snap.getBlockType(x, y, z) != material) continue;
                        BlockPos pos = new BlockPos(chunk.getX() * 16 + x, y, chunk.getZ() * 16 + z);
                        if (goals.stream().noneMatch(g -> g.podium().equals(pos))) found.add(pos);
                    }
        }
        return found;
    }

    private WoolGoal goalFor(Material material, TeamColor team) {
        for (WoolGoal goal : goals) if (goal.material() == material && goal.team() == team) return goal;
        return null;
    }

    private boolean objectiveWool(Material material) {
        for (WoolGoal goal : goals) if (goal.material() == material) return true;
        return false;
    }

    static TextColor colourOf(WoolGoal goal) {
        try {
            var dye = org.bukkit.DyeColor.valueOf(goal.colour().toUpperCase(java.util.Locale.ROOT).replace(' ', '_'));
            return TextColor.color(dye.getColor().asRGB());
        } catch (IllegalArgumentException e) {
            return TextColor.color(0xFFFFFF);
        }
    }

    private Component woolName(WoolGoal goal) {
        return Component.text(goal.name(), colourOf(goal));
    }

    @Override
    public Result onBreak(Player player, Block block) {
        BlockPos pos = BlockPos.of(block);
        for (WoolGoal goal : goals) if (goal.podium().equals(pos)) return Result.DENY;   // a wool already home
        Result taken = take(player, pos);
        return taken == null ? Result.PASS : Result.DENY;                  // a source stays: there's always more
    }

    /** Right- or left-clicking a source (a wool chest or wool block in the wool room) takes one wool. */
    @Override
    public boolean onInteract(Player player, Block block) {
        return take(player, BlockPos.of(block)) != null;
    }

    /** Takes one wool from a source at pos; null when pos isn't a source. */
    private Result take(Player player, BlockPos pos) {
        TeamColor team = match.teamOf(player.getUniqueId());
        for (WoolGoal goal : goals) {
            if (!sources.getOrDefault(goal, Set.of()).contains(pos)) continue;
            if (goal.team() != team) {
                // another team's goal: their wool room, which this player's team defends
                boolean otherGoalOurs = goals.stream().anyMatch(g -> g.team() == team && sources.getOrDefault(g, Set.of()).contains(pos));
                if (otherGoalOurs) continue;
                plugin.messages().actionBar(player, "ctw.defend");
                return Result.DENY;
            }
            if (captured.contains(goal)) {
                plugin.messages().actionBar(player, "ctw.already-home", Messages.comp("wool", woolName(goal)));
                return Result.DENY;
            }
            if (player.getInventory().contains(goal.material())) {
                plugin.messages().actionBar(player, "ctw.got-one", Messages.comp("wool", woolName(goal)));
                return Result.DENY;
            }
            player.getInventory().addItem(new ItemStack(goal.material()));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 0.8f);
            Block block = pos.block(match.world());
            block.getWorld().spawnParticle(Particle.BLOCK, block.getLocation().add(0.5, 0.8, 0.5), 10, 0.3, 0.3, 0.3, goal.material().createBlockData());
            pickedUp(player, goal);
            return Result.ALLOW;
        }
        return null;
    }

    private void pickedUp(Player player, WoolGoal goal) {
        boolean first = touched.add(goal);
        carriers.get(goal).add(player.getUniqueId());
        if (first) {
            match.broadcast("ctw.picked-up", Messages.comp("player", Component.text(player.getName(), goal.team().text())),
                    Messages.comp("wool", woolName(goal)));
            for (Player p : match.world().getPlayers()) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.4f);
        }
        if (paidPickup.add(player.getUniqueId() + goal.name())) {
            match.award(player, plugin.getConfig().getInt("coins.ctw-pickup", 2), "wool-pickup", Messages.comp("wool", woolName(goal)));
        }
    }

    @Override
    public Result onPlace(Player player, Block block, ItemStack item) {
        BlockPos pos = BlockPos.of(block);
        TeamColor team = match.teamOf(player.getUniqueId());
        Material material = item == null ? block.getType() : item.getType();
        for (WoolGoal goal : goals) {
            if (!goal.podium().equals(pos)) continue;
            if (goal.material() != material || goal.team() != team || captured.contains(goal)) {
                plugin.messages().actionBar(player, "ctw.wrong-podium", Messages.comp("wool", woolName(goal)));
                return Result.DENY;
            }
            capture(player, goal, block);
            return Result.ALLOW;
        }
        if (objectiveWool(material)) {
            plugin.messages().actionBar(player, "ctw.place-on-podium");
            return Result.DENY;                                              // objective wool is not for building
        }
        return Result.PASS;
    }

    private void capture(Player player, WoolGoal goal, Block block) {
        captured.add(goal);
        carriers.get(goal).clear();
        for (Player p : match.playing()) p.getInventory().remove(goal.material());   // nobody needs the rest now
        match.stats(player.getUniqueId()).captures++;
        match.award(player, plugin.getConfig().getInt("coins.ctw-capture", 10), "wool-capture", Messages.comp("wool", woolName(goal)));
        match.broadcast("ctw.captured", Messages.comp("player", Component.text(player.getName(), goal.team().text())),
                Messages.comp("wool", woolName(goal)), Messages.comp("team", Match.teamName(goal.team())));
        block.getWorld().spawnParticle(Particle.FIREWORK, block.getLocation().add(0.5, 1, 0.5), 40, 0.4, 0.6, 0.4, 0.08);
        for (Player p : match.world().getPlayers()) {
            boolean ours = match.teamOf(p.getUniqueId()) == goal.team();
            p.playSound(p.getLocation(), ours ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_WITHER_SPAWN, ours ? 0.6f : 0.3f, ours ? 1.2f : 1.6f);
        }
        if (goals.stream().filter(g -> g.team() == goal.team()).allMatch(captured::contains)) {
            Bukkit.getScheduler().runTask(plugin, () -> match.finish(goal.team(), null));
        }
    }

    @Override
    public void tick() {
        Set<UUID> carryingNow = new HashSet<>();
        for (WoolGoal goal : goals) {
            Set<UUID> set = carriers.get(goal);
            if (set == null) continue;
            set.clear();
            if (captured.contains(goal)) continue;
            for (Player player : match.playing()) {
                if (match.teamOf(player.getUniqueId()) != goal.team() || !player.getInventory().contains(goal.material())) continue;
                set.add(player.getUniqueId());
                carryingNow.add(player.getUniqueId());
                plugin.messages().actionBar(player, "ctw.carrying-bar", Messages.comp("wool", woolName(goal)));
            }
        }
        for (Player player : match.playing()) {
            UUID id = player.getUniqueId();
            if (carryingNow.contains(id)) {
                if (glowing.add(id)) player.setGlowing(true);
            } else if (glowing.remove(id)) {
                player.setGlowing(false);
            }
        }
    }

    @Override
    public boolean carrying(Player player) {
        for (Set<UUID> set : carriers.values()) if (set.contains(player.getUniqueId())) return true;
        return false;
    }

    @Override
    public void onDeathOrQuit(Player player, Location where) {
        for (WoolGoal goal : goals) {
            if (carriers.containsKey(goal) && carriers.get(goal).remove(player.getUniqueId())) {
                player.getInventory().remove(goal.material());
                match.broadcast("ctw.dropped", Messages.comp("player", Component.text(player.getName(), goal.team().text())),
                        Messages.comp("wool", woolName(goal)));
            }
        }
        if (glowing.remove(player.getUniqueId())) player.setGlowing(false);
    }

    @Override
    public List<BlockPos> objectiveBlocks() {
        List<BlockPos> blocks = new ArrayList<>();
        for (WoolGoal goal : goals) blocks.add(goal.podium());
        return blocks;
    }

    private int capturedBy(TeamColor team) {
        int n = 0;
        for (WoolGoal goal : goals) if (goal.team() == team && captured.contains(goal)) n++;
        return n;
    }

    private int touchedBy(TeamColor team) {
        int n = 0;
        for (WoolGoal goal : goals) if (goal.team() == team && touched.contains(goal)) n++;
        return n;
    }

    @Override
    public List<Component> lines() {
        List<Component> lines = new ArrayList<>();
        Map<TeamColor, List<WoolGoal>> byTeam = new EnumMap<>(TeamColor.class);
        for (WoolGoal goal : goals) byTeam.computeIfAbsent(goal.team(), t -> new ArrayList<>()).add(goal);
        for (Map.Entry<TeamColor, List<WoolGoal>> entry : byTeam.entrySet()) {
            List<Component> marks = new ArrayList<>();
            for (WoolGoal goal : entry.getValue()) {
                String mark = captured.contains(goal) ? "■" : !carriers.getOrDefault(goal, Set.of()).isEmpty() ? "◩" : touched.contains(goal) ? "▣" : "□";
                marks.add(Component.text(mark, colourOf(goal)));
            }
            lines.add(plugin.messages().component("ctw.sidebar-line", Messages.comp("team", Match.teamName(entry.getKey())),
                    Messages.comp("wools", Component.join(net.kyori.adventure.text.JoinConfiguration.separator(Component.space()), marks)),
                    Messages.arg("captured", capturedBy(entry.getKey())), Messages.arg("total", entry.getValue().size())));
        }
        return lines;
    }

    @Override
    public String scoreLine() {
        List<String> parts = new ArrayList<>();
        for (TeamColor team : match.mode().teams()) {
            long total = goals.stream().filter(g -> g.team() == team).count();
            parts.add("<" + team.id() + ">" + team.display() + " " + capturedBy(team) + "/" + total + "</" + team.id() + ">");
        }
        return String.join(" <dark_gray>-</dark_gray> ", parts);
    }

    @Override
    public TeamColor leader() {
        TeamColor best = null;
        int bestScore = -1;
        boolean tie = false;
        for (TeamColor team : match.mode().teams()) {
            int score = capturedBy(team) * 100 + touchedBy(team);
            if (score > bestScore) {
                best = team;
                bestScore = score;
                tie = false;
            } else if (score == bestScore) {
                tie = true;
            }
        }
        return tie || bestScore <= 0 ? null : best;
    }
}
