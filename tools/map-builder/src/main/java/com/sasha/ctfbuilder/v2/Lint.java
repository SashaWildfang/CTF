package com.sasha.ctfbuilder.v2;

import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Leaves;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The last pass over a finished build, so nothing in it can look broken in game:
 * <ul>
 *   <li>small lumps that aren't attached to anything (floating blocks) are removed</li>
 *   <li>everything that needs support (plants, carpets, lanterns, torches, doors, banners, cane...) and
 *       wouldn't survive a block update where it is, is removed, the way the game itself would pop it</li>
 *   <li>half beds and half doors are removed</li>
 *   <li>leaves are made persistent, so they never decay</li>
 *   <li>lightning rods are removed (they read as junk on roofs)</li>
 * </ul>
 * It reports what it removed and where, so the build code can be fixed at the source.
 */
public final class Lint {

    private Lint() {}

    public record Report(int unsupported, int floating, int halves, int rods, List<String> lumps, Map<String, Integer> byType) {
        public String summary() {
            return "unsupported " + unsupported + " " + byType + ", floating " + floating + " in " + lumps.size() + " lumps "
                    + lumps.subList(0, Math.min(25, lumps.size())) + ", half beds/doors " + halves + ", rods " + rods;
        }
    }

    public static Report run(World w, int x0, int x1, int z0, int z1, int y0, int y1, int minLump) {
        Map<String, Integer> byType = new TreeMap<>();
        int rods = 0, halves = 0, unsupported = 0;
        // 1. rods, leaves, half beds and doors
        int sx = x1 - x0 + 1, sz = z1 - z0 + 1, sy = y1 - y0 + 1;
        Grid grid = new Grid(w, x0, z0, y0, sx, sz, sy);
        List<int[]> soft = new ArrayList<>();
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++)
                for (int y = y0; y <= y1; y++) {
                    Material m = grid.type(x, y, z);
                    if (m.isAir()) continue;
                    if (m == Material.LIGHTNING_ROD) {
                        w.getBlockAt(x, y, z).setType(Material.AIR, false);
                        grid.clear(x, y, z);
                        rods++;
                        continue;
                    }
                    if (!m.isOccluding() || m.hasGravity()) soft.add(new int[] {x, y, z});
                }
        int settled = 0;
        for (int[] p : soft) {
            Block block = w.getBlockAt(p[0], p[1], p[2]);
            BlockData data = block.getBlockData();
            if (block.getType().hasGravity()) {
                // sand over air would fall the first time something next to it changes
                Material below = block.getRelative(org.bukkit.block.BlockFace.DOWN).getType();
                if (below.isAir() || below == Material.WATER || below == Material.LAVA || !below.isSolid()) {
                    Material firm = firm(block.getType());
                    if (firm != null) {
                        block.setType(firm, false);
                        settled++;
                    }
                }
                continue;
            }
            if (data instanceof Leaves leaves && !leaves.isPersistent()) {
                leaves.setPersistent(true);
                block.setBlockData(leaves, false);
            } else if (data instanceof Bed bed) {
                Block other = block.getRelative(bed.getPart() == Bed.Part.FOOT ? bed.getFacing() : bed.getFacing().getOppositeFace());
                if (!(other.getBlockData() instanceof Bed o) || o.getPart() == bed.getPart() || o.getFacing() != bed.getFacing()) {
                    block.setType(Material.AIR, false);
                    grid.clear(p[0], p[1], p[2]);
                    halves++;
                }
            } else if (data instanceof org.bukkit.block.data.type.Door door) {
                Block other = block.getRelative(door.getHalf() == Bisected.Half.BOTTOM ? org.bukkit.block.BlockFace.UP : org.bukkit.block.BlockFace.DOWN);
                if (!(other.getBlockData() instanceof org.bukkit.block.data.type.Door o) || o.getHalf() == door.getHalf()) {
                    block.setType(Material.AIR, false);
                    grid.clear(p[0], p[1], p[2]);
                    halves++;
                }
            }
        }
        // 2. lumps floating in the air
        List<String> lumps = new ArrayList<>();
        int floating = 0;
        BitSet seen = new BitSet();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        List<int[]> component = new ArrayList<>();
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++)
                for (int y = y0; y <= y1; y++) {
                    if (grid.type(x, y, z).isAir() || seen.get(grid.index(x, y, z))) continue;
                    component.clear();
                    queue.add(new int[] {x, y, z});
                    seen.set(grid.index(x, y, z));
                    boolean big = false;
                    while (!queue.isEmpty()) {
                        int[] p = queue.poll();
                        if (!big) component.add(p);
                        if (component.size() >= minLump) big = true;
                        for (int dx = -1; dx <= 1; dx++)
                            for (int dy = -1; dy <= 1; dy++)
                                for (int dz = -1; dz <= 1; dz++) {
                                    int n = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                                    if (n == 0 || n == 3) continue; // faces and edges, not just corners
                                    int nx = p[0] + dx, ny = p[1] + dy, nz = p[2] + dz;
                                    if (!grid.inside(nx, ny, nz)) continue;
                                    int i = grid.index(nx, ny, nz);
                                    if (seen.get(i) || grid.type(nx, ny, nz).isAir()) continue;
                                    seen.set(i);
                                    queue.add(new int[] {nx, ny, nz});
                                }
                    }
                    if (big) continue;
                    int[] first = component.get(0);
                    lumps.add(first[0] + "," + first[1] + "," + first[2] + "(" + component.size() + " " + grid.type(first[0], first[1], first[2]).name().toLowerCase() + ")");
                    for (int[] p : component) {
                        w.getBlockAt(p[0], p[1], p[2]).setType(Material.AIR, false);
                        grid.clear(p[0], p[1], p[2]);
                        floating++;
                    }
                }
        // 3. whatever wouldn't survive an update, until nothing changes
        List<String> where = new ArrayList<>();
        for (int pass = 0; pass < 8; pass++) {
            List<int[]> next = new ArrayList<>();
            for (int[] p : soft) {
                Block block = w.getBlockAt(p[0], p[1], p[2]);
                Material m = block.getType();
                if (m.isAir() || m == Material.WATER || m == Material.LAVA || m == Material.BUBBLE_COLUMN) continue;
                if (block.getBlockData().isSupported(block)) {
                    next.add(p);
                    continue;
                }
                byType.merge(m.name().toLowerCase(), 1, Integer::sum);
                if (where.size() < 24) where.add(m.name().toLowerCase() + "@" + p[0] + "," + p[1] + "," + p[2] + "/below=" + w.getBlockAt(p[0], p[1] - 1, p[2]).getType().name().toLowerCase());
                if (m == Material.DIRT_PATH || m == Material.FARMLAND) {
                    // covered paths and fields turn to dirt in game; do it now rather than leave a hole
                    block.setType(Material.DIRT, false);
                    continue;
                }
                // a door or tall plant goes as a whole
                if (block.getBlockData() instanceof Bisected bisected && !(bisected instanceof org.bukkit.block.data.type.Stairs)
                        && !(bisected instanceof org.bukkit.block.data.type.TrapDoor)) {
                    Block other = block.getRelative(bisected.getHalf() == Bisected.Half.BOTTOM ? org.bukkit.block.BlockFace.UP : org.bukkit.block.BlockFace.DOWN);
                    if (other.getType() == m) other.setType(Material.AIR, false);
                }
                block.setType(Material.AIR, false);
                unsupported++;
            }
            if (next.size() == soft.size()) break;
            soft = next;
        }
        if (settled > 0) byType.put("sand-made-firm", settled);
        Report report = new Report(unsupported, floating, halves, rods, lumps, byType);
        Bukkit.getLogger().info("[CtfBuilder] lint: " + report.summary());
        if (!where.isEmpty()) Bukkit.getLogger().info("[CtfBuilder] lint at: " + where);
        return report;
    }

    /** A block that looks the same but doesn't fall. */
    static Material firm(Material m) {
        String n = m.name();
        if (n.equals("SAND") || n.equals("SUSPICIOUS_SAND")) return Material.SANDSTONE;
        if (n.equals("RED_SAND")) return Material.RED_SANDSTONE;
        if (n.equals("GRAVEL") || n.equals("SUSPICIOUS_GRAVEL")) return Material.ANDESITE;
        if (n.endsWith("_CONCRETE_POWDER")) return Material.valueOf(n.replace("_POWDER", ""));
        return null;
    }

    /** The area's block types, read once from chunk snapshots. */
    private static final class Grid {
        final int x0, z0, y0, sx, sz, sy;
        final Material[] types;

        Grid(World w, int x0, int z0, int y0, int sx, int sz, int sy) {
            this.x0 = x0;
            this.z0 = z0;
            this.y0 = y0;
            this.sx = sx;
            this.sz = sz;
            this.sy = sy;
            types = new Material[sx * sz * sy];
            for (int cx = Math.floorDiv(x0, 16); cx <= Math.floorDiv(x0 + sx - 1, 16); cx++)
                for (int cz = Math.floorDiv(z0, 16); cz <= Math.floorDiv(z0 + sz - 1, 16); cz++) {
                    ChunkSnapshot snap = w.getChunkAt(cx, cz).getChunkSnapshot(false, false, false);
                    for (int lx = 0; lx < 16; lx++)
                        for (int lz = 0; lz < 16; lz++) {
                            int x = cx * 16 + lx, z = cz * 16 + lz;
                            if (x < x0 || x >= x0 + sx || z < z0 || z >= z0 + sz) continue;
                            for (int y = y0; y < y0 + sy; y++) types[index(x, y, z)] = snap.getBlockType(lx, y, lz);
                        }
                }
        }

        boolean inside(int x, int y, int z) {
            return x >= x0 && x < x0 + sx && z >= z0 && z < z0 + sz && y >= y0 && y < y0 + sy;
        }

        int index(int x, int y, int z) {
            return ((x - x0) * sz + (z - z0)) * sy + (y - y0);
        }

        Material type(int x, int y, int z) {
            Material m = types[index(x, y, z)];
            return m == null ? Material.AIR : m;
        }

        void clear(int x, int y, int z) {
            types[index(x, y, z)] = Material.AIR;
        }
    }
}
