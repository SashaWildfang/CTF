package com.sasha.ctfbuilder.v2.maps;

import com.sasha.ctfbuilder.v2.Arena;
import com.sasha.ctfbuilder.v2.Kit;
import com.sasha.ctfbuilder.v2.Land;
import com.sasha.ctfbuilder.v2.Theme;
import com.sasha.ctfbuilder.v2.W.P;
import org.bukkit.World;

/**
 * Skyreach: four citadels floating round the Rift, for four teams. From each citadel a spoke bridge runs in
 * over a waystone island to the Rift Ring, a ring of land round a hole in the sky; four thin bridges cross
 * the hole to the platform hanging in the middle (the hill), under four great amethyst spires. Out on the
 * diagonals, a stepping island between each pair of neighbours is the back way into the next citadel.
 */
public final class Skyreach extends Arena {

    static final int Y = 80, CIT = -88;

    record Isle(int x, int z, double r, int top) {}

    static final Isle HOME = new Isle(CIT, 0, 23, Y);
    static final Isle WAY = new Isle(-50, 0, 8, Y - 2);
    static final Isle DIAG = new Isle(-62, -62, 11, Y + 4);
    static final Isle DIAG2 = new Isle(-62, 62, 11, Y + 4);

    public Skyreach(World world) {
        super(world, "skyreach", true, Theme.get("skyreach"));
        name = "Skyreach";
        icon = "CHORUS_FLOWER";
        minutes = 18;
        kothPoints = 120;
        reach = 120;
        low = 20;
        high = 150;
        voidY = 25;
        spectator = new double[] {0.5, 120, 0.5, -90, 60};
    }

    double height(int x, int z) {
        double best = -1;
        int top = Y;
        for (Isle i : new Isle[] {HOME, WAY, DIAG, DIAG2}) {
            double v = 1 - (Math.hypot(x - i.x, z - i.z) + w.fbm(x + i.x, z + i.z, 8, 2) * 2.5) / i.r;
            if (v > best) { best = v; top = i.top; }
        }
        // the Rift Ring and the platform in the middle
        double d = Math.hypot(x, z) + w.fbm(x, z, 7, 2) * 1.5;
        double ring = Math.min(d - 13.5, 27 - d) / 6.0;
        if (ring > best) { best = ring; top = Y; }
        double plat = 1 - Math.hypot(x, z) / 6.5;
        if (plat > best) { best = plat; top = Y + 2; }
        if (best < 0) return Double.NaN;
        return top + Math.round(w.fbm(x * 1.3, z * 1.3, 10, 2) * 1.2 * Land.smooth(best / 0.4)) - (best < 0.08 ? 1 : 0);
    }

    String surface(int x, int z, int y, int steep, double edge) {
        double n = w.fbm(x + 913, z - 377, 8, 2);
        if (steep >= 3) return "end_stone";
        if (n > 0.45) return "moss_block";
        if (n < -0.45) return "end_stone_bricks";
        return "end_stone";
    }

    @Override
    protected void build() {
        Land land = new Land(w, t, -116, 116, -116, 116);
        land.top = this::height;
        land.surface = this::surface;
        land.depth = 20;
        land.bands = new String[] {"end_stone", "end_stone", "purpur_block", "end_stone", "obsidian", "end_stone", "end_stone", "amethyst_block"};
        land.speck = P.of("amethyst_block", 2, "obsidian", 1, "crying_obsidian", 1);
        land.under = P.of("end_stone", 3, "obsidian", 1);
        land.build();

        citadel(CIT, 0);
        rift();
        waystone();
        String[] b = {"end_stone_bricks", "end_stone_brick_slab", "purpur_block", "end_stone_brick_wall", "purpur_pillar", "lantern"};
        k.bridge(CIT + 18, 0, WAY.x, 0, 5, b[0], b[1], b[2], b[3], b[4], b[5]);
        k.bridge(WAY.x, 0, -21, 0, 5, b[0], b[1], b[2], b[3], b[4], b[5]);
        k.bridge(-17, 0, -2, 0, 3, b[0], b[1], b[2], b[3], b[4], b[5]);
        k.bridge(CIT + 6, -18, DIAG.x + 4, DIAG.z + 6, 3, b[0], b[1], b[2], b[3], b[4], b[5]);
        k.bridge(CIT + 6, 18, DIAG2.x + 4, DIAG2.z - 6, 3, b[0], b[1], b[2], b[3], b[4], b[5]);
        // chorus on the open ground, a few amethyst buds
        trees(new Theme.Tree[] {Theme.Tree.CHORUS}, new int[] {1}, 0.006, 10, 3, (x, z) -> Math.hypot(x, z) > 30);
        plants(P.of("small_amethyst_bud[facing=up]", 2, "medium_amethyst_bud[facing=up]", 1, "short_grass", 2), 0.04, (x, z) -> true);

        spawn = new double[] {CIT - 8.5, Y + 1, 0.5, -90};
        flag = new int[] {CIT + 9, Y + 1, 0};
        pads.add(new int[] {CIT + 6, Y + 1, -13});
        pads.add(new int[] {CIT + 6, Y + 1, 13});
        hills.add(new Hill("Rift", -3, Y + 3, -3, 3, Y + 6, 3));
    }

    // ---------------------------------------------------------------- the citadel

    /**
     * The citadel: the spawn hall (purpur over an end-stone-brick footing, tall magenta windows, a vaulted
     * roof), the spire beside it, a terrace for the flag, a parapet round the island's edge.
     */
    void citadel(int cx, int cz) {
        int y = Y;
        level(cx - 16, cz - 16, cx + 14, cz + 16, y, P.of("end_stone_bricks", 1, "end_stone", 4), P.of("end_stone"));
        // the hall: 13 x 11, walls 7 high in clean courses — footing of end stone bricks, then purpur, a pillar band
        int x0 = cx - 14, x1 = cx - 2, z0 = cz - 5, z1 = cz + 5;
        k.walls(x0, z0, x1, z1, y, 7, Kit.Bands.of(0, "end_stone_bricks", 2, "purpur_block", 7, "purpur_pillar[axis=x]"), "purpur_pillar", 3,
                P.of("purpur_block", 2, "end_stone_bricks", 1));
        for (int x = x0 + 1; x < x1; x++) {
            if ((x - x0) % 3 == 0) continue;
            for (int dy = 2; dy <= 5; dy++) {
                w.set(x, y + dy, z0, "magenta_stained_glass_pane");
                w.set(x, y + dy, z1, "magenta_stained_glass_pane");
            }
        }
        k.gable(x0, z0, x1, z1, y + 8, true, 1, new Kit.Roof("purpur_stairs", "purpur_slab", "purpur_block"), P.of("purpur_block"));
        // the door to the terrace: a tall arch
        for (int dz = -1; dz <= 1; dz++) for (int dy = 1; dy <= 4; dy++) w.set(x1, y + dy, cz + dz, "air");
        w.set(x1, y + 5, cz, "purpur_pillar[axis=y]");
        w.set(x1, y + 4, cz - 1, "purpur_stairs[facing=south,half=top]");
        w.set(x1, y + 4, cz + 1, "purpur_stairs[facing=north,half=top]");
        for (int s : new int[] {-2, 2}) w.set(x1 + 1, y + 5, cz + s, "TEAM_wall_banner[facing=east]");
        // inside: clean floor, benches, lights hanging from the roof beams (lanterns, no end rods)
        for (int x = x0 + 2; x <= x1 - 2; x += 3) for (int z = z0 + 1; z < z1; z++) w.set(x, y + 7, z, "stripped_warped_stem[axis=z]");
        for (int x = x0 + 2; x <= x1 - 2; x += 3) w.hangLight(x, y + 5, cz, "lantern");
        for (int x = x0 + 2; x <= x1 - 2; x++) {
            if ((x - x0) % 3 == 0) continue;
            w.set(x, y + 1, z0 + 1, "purpur_stairs[facing=north,half=bottom]");
            w.set(x, y + 1, z1 - 1, "purpur_stairs[facing=south,half=bottom]");
        }
        for (int x = x0 + 1; x < x1; x++) w.set(x, y + 1, cz, "TEAM_carpet");
        w.set(x0 + 1, y + 1, cz - 2, "ender_chest[facing=east]");
        w.set(x0 + 1, y + 1, cz + 2, "ender_chest[facing=east]");
        w.set(x0 + 1, y + 3, cz, "TEAM_wall_banner[facing=east]");
        // the spire: a square purpur tower behind the hall, stepping in, a pointed purpur cap
        int sx = cx - 8, sz = cz - 9;
        for (int dy = 0; dy <= 26; dy++) {
            int r = dy < 12 ? 2 : dy < 20 ? 1 : 1;
            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                boolean corner = Math.abs(dx) == r && Math.abs(dz) == r;
                w.set(sx + dx, y + dy, sz + dz, corner ? "purpur_pillar[axis=y]" : dy % 6 == 0 ? "end_stone_bricks" : "purpur_block");
            }
        }
        for (int dy : new int[] {6, 12, 18}) for (int[] d : new int[][] {{0, -3}, {0, 3}, {-3, 0}, {3, 0}}) {
            int r = dy <= 12 ? 3 : 2;
            w.set(sx + Integer.signum(d[0]) * r, y + dy, sz + Integer.signum(d[1]) * r,
                    "purpur_stairs[facing=" + (d[0] < 0 ? "east" : d[0] > 0 ? "west" : d[1] < 0 ? "south" : "north") + ",half=top]");
        }
        k.cone(sx, sz, y + 28, 1, 1, 2.2, new Kit.Roof("purpur_stairs", "purpur_slab", "purpur_block"));
        // the terrace for the flag: polished paving in a ring, a low wall with posts, lanterns on posts
        for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) {
            double d = Math.hypot(dx, dz);
            if (d > 5.4) continue;
            w.set(cx + 9 + dx, y, cz + dz, d < 1.5 ? "purpur_pillar[axis=y]" : ((dx + dz) & 1) == 0 ? "end_stone_bricks" : "purpur_block");
        }
        // lamp posts round the terrace, obelisks at the head of the spoke bridge
        for (int i = 0; i < 6; i++) {
            double ang = i * Math.PI / 3 + Math.PI / 6;
            int lx = cx + 9 + (int) Math.round(Math.cos(ang) * 7), lz = cz + (int) Math.round(Math.sin(ang) * 7);
            w.set(lx, y + 1, lz, "purpur_pillar[axis=y]");
            w.set(lx, y + 2, lz, "end_stone_brick_wall");
            w.set(lx, y + 3, lz, "lantern[hanging=false]");
        }
        for (int s2 : new int[] {-4, 4}) {
            for (int dy = 1; dy <= 6; dy++) w.set(cx + 17, y + dy, cz + s2, dy == 6 ? "purpur_block" : dy % 2 == 0 ? "obsidian" : "crying_obsidian");
            w.set(cx + 17, y + 7, cz + s2, "purpur_slab[type=bottom]");
        }
        // the island's parapet: end stone brick walls round the edge except where the bridges leave
        for (int x = cx - 26; x <= cx + 26; x++) for (int z = cz - 26; z <= cz + 26; z++) {
            int gy = w.ground(x, z);
            if (gy < Y - 3 || gy > Y + 2) continue;
            boolean rim = false;
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) if (w.ground(x + d[0], z + d[1]) < Y - 6) rim = true;
            if (!rim) continue;
            // leave the parapet open where the three bridges leave the island
            if (Land.segment(x, z, CIT + 18, 0, WAY.x, 0) < 5 || Land.segment(x, z, CIT + 6, -18, DIAG.x + 4, DIAG.z + 6) < 4
                    || Land.segment(x, z, CIT + 6, 18, DIAG2.x + 4, DIAG2.z - 6) < 4) continue;
            w.set(x, gy + 1, z, (x + z) % 7 == 0 ? "purpur_pillar[axis=y]" : "end_stone_brick_wall");
        }
        keepClear(cx - 18, cz - 14, cx + 16, cz + 14);
    }

    // ---------------------------------------------------------------- the middle

    /**
     * The Rift: the ring's paving, four amethyst spires on the diagonals of the ring, and the hanging platform in
     * the middle with a rim of purpur. Built a quarter at a time and turned round with the rest.
     */
    void rift() {
        for (int x = -27; x <= 0; x++) for (int z = -27; z <= 27; z++) {
            if (!w.canon(x, z)) continue;
            double d = Math.hypot(x, z);
            int gy = w.ground(x, z);
            if (gy < Y - 3) continue;
            if (d > 15 && d < 25 && Math.abs(Math.abs(d - 20)) < 1) c(x, gy, z, "end_stone_bricks");
            if (d <= 6.5) {
                c(x, Y + 2, z, d > 5.5 ? "purpur_block" : ((x + z) & 1) == 0 ? "end_stone_bricks" : "purpur_block");
                for (int h = 1; h <= 8; h++) c(x, Y + 2 + h, z, "air");
            }
        }
        // the four spires on the ring's diagonals (one per quarter, at the quarter's own diagonal)
        int sx = -14, sz = -14;
        int gy = w.ground(sx, sz);
        if (gy > 50) {
            for (int dy = 0; dy <= 22; dy++) {
                double r = 2.6 * (1 - dy / 24.0) + 0.3;
                for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++)
                    if (Math.hypot(dx, dz) <= r) w.set(sx + dx, gy + dy, sz + dz, dy % 5 == 0 ? "budding_amethyst" : "amethyst_block");
            }
            w.set(sx, gy + 23, sz, "amethyst_cluster[facing=up]");
        }
        keepClear(-27, -27, 27, 27);
    }

    /** The waystone island on the spoke: a ring of standing stones round an obsidian obelisk. */
    void waystone() {
        int cx = WAY.x, cz = WAY.z, y = w.ground(cx, cz);
        if (y < 50) return;
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3 + Math.PI / 6;
            int x = cx + (int) Math.round(Math.cos(a) * 5), z = cz + (int) Math.round(Math.sin(a) * 5);
            if (Math.abs(z - cz) < 4) continue; // the spoke runs through here
            int gy = w.ground(x, z);
            int h = 2 + (i % 2);
            for (int dy = 1; dy <= h; dy++) w.set(x, gy + dy, z, dy == h ? "purpur_pillar[axis=y]" : "end_stone_bricks");
        }
        keepClear(cx - 7, cz - 7, cx + 7, cz + 7);
    }
}
