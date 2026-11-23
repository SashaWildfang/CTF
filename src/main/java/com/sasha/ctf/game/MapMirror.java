/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.game;

import com.sasha.ctf.FractalCTF;
import com.sasha.ctf.model.BlockPos;
import com.sasha.ctf.model.MapDef;
import com.sasha.ctf.model.TeamColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Mirror mode for the map editor (/ctfmap mirror): every block you place or break in the map you are editing is
 * placed or broken at the same spot in the other team's base (or bases), turned the way that base is turned.
 * How the bases relate (a half turn, a mirror, quarter turns) is worked out from the map itself: the flags give
 * the middle, and the turn or mirror that maps the map's blocks onto themselves best, and one flag onto the
 * other, is used.
 */
final class MapMirror implements Listener {

    /** A quarter-turn count (clockwise seen from above) followed by an optional east-west flip. */
    record Turn(int quarters, boolean flip) {
        int[] apply(int dx2, int dz2) {
            int x = dx2, z = dz2;
            for (int i = 0; i < quarters; i++) {
                int t = x;
                x = -z;
                z = t;
            }
            if (flip) x = -x;
            return new int[] {x, z};
        }

        BlockData apply(BlockData data) {
            BlockData out = data.clone();
            StructureRotation rotation = switch (quarters & 3) {
                case 1 -> StructureRotation.CLOCKWISE_90;
                case 2 -> StructureRotation.CLOCKWISE_180;
                case 3 -> StructureRotation.COUNTERCLOCKWISE_90;
                default -> StructureRotation.NONE;
            };
            if (rotation != StructureRotation.NONE) out.rotate(rotation);
            if (flip) out.mirror(Mirror.FRONT_BACK);
            return out;
        }
    }

    /** A map's mirror: its middle (doubled, so it can sit between blocks) and the turns to the other bases. */
    record Plan(int cx2, int cz2, List<Turn> turns) {}

    private final FractalCTF plugin;
    private final Map<UUID, Plan> on = new HashMap<>();

    MapMirror(FractalCTF plugin) {
        this.plugin = plugin;
    }

    boolean active(Player player) {
        return on.containsKey(player.getUniqueId());
    }

    void off(Player player) {
        on.remove(player.getUniqueId());
    }

    /** Turns mirror mode on for someone editing this map; null and a reason if the map has no clear mirror. */
    String on(Player player, MapDef map, World world) {
        Plan plan = plan(map, world);
        if (plan == null || plan.turns().isEmpty()) return null;
        on.put(player.getUniqueId(), plan);
        List<String> names = new ArrayList<>();
        for (Turn turn : plan.turns()) names.add(describe(turn));
        return String.join(", ", names);
    }

    static String describe(Turn turn) {
        if (!turn.flip()) return switch (turn.quarters()) {
            case 1 -> "quarter turn";
            case 2 -> "half turn";
            case 3 -> "three-quarter turn";
            default -> "none";
        };
        return switch (turn.quarters()) {
            case 0 -> "mirror east-west";
            case 2 -> "mirror north-south";
            default -> "mirror on a diagonal";
        };
    }

    /** Works out the middle and the turns from the flags (or spawns) and the blocks around them. */
    private Plan plan(MapDef map, World world) {
        List<int[]> marks = new ArrayList<>();                     // doubled block centres of each team's flag (or spawn)
        List<TeamColor> teams = new ArrayList<>();
        for (Map.Entry<TeamColor, MapDef.TeamPoints> entry : map.teams.entrySet()) {
            BlockPos flag = entry.getValue().flag;
            if (flag != null) marks.add(new int[] {flag.x() * 2 + 1, flag.z() * 2 + 1});
            else if (entry.getValue().spawn != null) marks.add(new int[] {(int) Math.floor(entry.getValue().spawn.x()) * 2 + 1, (int) Math.floor(entry.getValue().spawn.z()) * 2 + 1});
            else continue;
            teams.add(entry.getKey());
        }
        if (marks.size() < 2) return null;
        long sx = 0, sz = 0;
        for (int[] m : marks) {
            sx += m[0];
            sz += m[1];
        }
        int cx2 = (int) Math.round(sx / (double) marks.size()), cz2 = (int) Math.round(sz / (double) marks.size());
        // blocks to test against: solid ones round the first base
        int[] base = marks.get(0);
        Random random = new Random(7);
        List<int[]> samples = new ArrayList<>();
        for (int i = 0; i < 6000 && samples.size() < 1500; i++) {
            int x = (base[0] - 1) / 2 + random.nextInt(81) - 40, z = (base[1] - 1) / 2 + random.nextInt(81) - 40, y = 40 + random.nextInt(80);
            if (!world.getBlockAt(x, y, z).getType().isAir()) samples.add(new int[] {x, y, z});
        }
        List<Turn> chosen = new ArrayList<>();
        for (int t = 1; t < marks.size(); t++) {
            int[] target = marks.get(t);
            Turn best = null;
            double bestScore = 0.55;                                 // below this it isn't really a copy
            for (int quarters = 0; quarters < 4; quarters++) {
                for (boolean flip : new boolean[] {false, true}) {
                    if (quarters == 0 && !flip) continue;
                    Turn turn = new Turn(quarters, flip);
                    int[] moved = turn.apply(base[0] - cx2, base[1] - cz2);
                    if (Math.abs(moved[0] + cx2 - target[0]) > 2 || Math.abs(moved[1] + cz2 - target[1]) > 2) continue;   // must take flag to flag
                    if (((moved[0] + cx2) & 1) == 0) continue;
                    int same = 0;
                    for (int[] s : samples) {
                        int[] to = turn.apply(s[0] * 2 + 1 - cx2, s[2] * 2 + 1 - cz2);
                        if (!world.getBlockAt((to[0] + cx2 - 1) / 2, s[1], (to[1] + cz2 - 1) / 2).getType().isAir()) same++;
                    }
                    double score = samples.isEmpty() ? 0 : same / (double) samples.size();
                    if (score > bestScore) {
                        bestScore = score;
                        best = turn;
                    }
                }
            }
            if (best != null && !chosen.contains(best)) chosen.add(best);
        }
        return new Plan(cx2, cz2, chosen);
    }

    /** Where a block goes under a turn. */
    private static Block image(Block block, Plan plan, Turn turn) {
        int[] to = turn.apply(block.getX() * 2 + 1 - plan.cx2(), block.getZ() * 2 + 1 - plan.cz2());
        return block.getWorld().getBlockAt((to[0] + plan.cx2() - 1) / 2, block.getY(), (to[1] + plan.cz2() - 1) / 2);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Plan plan = on.get(event.getPlayer().getUniqueId());
        if (plan == null || !event.getBlock().getWorld().getName().startsWith("ctf_edit_")) return;
        Block placed = event.getBlockPlaced();
        // next tick, once doors, beds and tall plants have their other half
        Bukkit.getScheduler().runTask(plugin, () -> {
            List<Block> parts = new ArrayList<>();
            parts.add(placed);
            BlockData data = placed.getBlockData();
            if (data instanceof org.bukkit.block.data.Bisected) parts.add(placed.getRelative(BlockFace.UP));
            if (data instanceof Bed bed) parts.add(placed.getRelative(bed.getPart() == Bed.Part.FOOT ? bed.getFacing() : bed.getFacing().getOppositeFace()));
            for (Turn turn : plan.turns()) {
                for (Block part : parts) {
                    if (part.getType().isAir()) continue;
                    image(part, plan, turn).setBlockData(turn.apply(part.getBlockData()), false);
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Plan plan = on.get(event.getPlayer().getUniqueId());
        if (plan == null || !event.getBlock().getWorld().getName().startsWith("ctf_edit_")) return;
        Block broken = event.getBlock();
        BlockData was = broken.getBlockData();
        List<Block> targets = new ArrayList<>();
        for (Turn turn : plan.turns()) {
            Block image = image(broken, plan, turn);
            targets.add(image);
            if (was instanceof org.bukkit.block.data.Bisected half) {
                targets.add(image.getRelative(half.getHalf() == org.bukkit.block.data.Bisected.Half.BOTTOM ? BlockFace.UP : BlockFace.DOWN));
            }
            if (was instanceof Directional && was instanceof Bed) targets.add(image.getRelative(((Directional) turn.apply(was)).getFacing()));
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Block target : targets) if (!target.getType().isAir()) target.setType(Material.AIR, false);
        });
    }
}
