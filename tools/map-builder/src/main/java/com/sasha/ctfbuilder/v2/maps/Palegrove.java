package com.sasha.ctfbuilder.v2.maps;

import com.sasha.ctfbuilder.v2.Arena;
import com.sasha.ctfbuilder.v2.Kit;
import com.sasha.ctfbuilder.v2.Land;
import com.sasha.ctfbuilder.v2.Theme;
import com.sasha.ctfbuilder.v2.W.P;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * Palegrove: a pale, haunted country of islands in the mist, two teams.
 *
 * The islands float apart with nothing between them, at least 7.5 blocks at the nearest: players build their own
 * bridges. Worn stone landings with a lamp mark the brinks where crossings are worth building.
 *
 * Each team's abbey stands on its home island: a church of pale stone with twin towers and a spire on each, the
 * spawn in the nave, the flag in the cloister garth beside it; an abbot's lodge, a herb garden and a well; east of the
 * great door the abbey close, with a preaching cross on a paved rosette, running out to the broken-off first arch of
 * the old aqueduct at the brink. Nearer the middle lie the graveyard isle (a mortuary chapel among the
 * headstones, a lych-gate, an old pale oak) and the mill isle (a watermill on its pond, a granary on staddle stones).
 *
 * In the middle, the ruined priory: the clustered piers of a crossing tower, two tall and two broken, a broken arch,
 * stubs of nave and transept walls, the aqueduct's last broken pier, rubble and pale oaks, all grown over with pale
 * moss; its crossing is the middle hill. North and south of it, two
 * small isles with stone circles are the other two hills.
 */
public final class Palegrove extends Arena {


    /** An island: centre, radii, turn, top block, and how square it is (2 = an ellipse). */
    record Isle(double x, double z, double rx, double rz, double ang, int top, double sq) {
        int walk() {
            return top + 1;
        }
    }

    static final Isle HOME = new Isle(-74, 0, 28, 26, 0, 72, 2.6);
    static final Isle GRAVE = new Isle(-36, -24, 11, 9.5, 0.4, 71, 2.2);
    static final Isle MILL = new Isle(-36, 24, 12.5, 10.5, -0.3, 69, 2.2);
    static final Isle CENTER = new Isle(0, 0, 17, 15, 0.5, 74, 2.3);
    static final Isle NORTH = new Isle(0, -38, 9.5, 8.5, 0, 70, 2);
    static final Isle SOUTH = new Isle(0, 38, 9.5, 8.5, 0, 70, 2);
    static final Isle[] ISLES = {HOME, GRAVE, MILL, CENTER, NORTH, SOUTH};

    Land land;

    public Palegrove(World world) {
        super(world, "palegrove", false, Theme.get("palegrove"));
        name = "Palegrove";
        icon = "PALE_OAK_SAPLING";
        minutes = 15;
        kothPoints = 250;
        captureSeconds = 8;
        reach = 108;
        low = 15;
        high = 130;
        voidY = 15;
        spectator = new double[] {0.5, 112, 0.5, -90, 45};
    }

    // ================================================================ helpers

    /** Noise that's the same at (x, z) and (-x, -z), so the halves meet without a seam. */
    double sn(double x, double z, double scale, double off) {
        return (w.fbm(x + off, z - off, scale, 2) + w.fbm(-x + off, -z - off, scale, 2)) * 0.7;
    }

    void put(boolean half, int x, int y, int z, String spec) {
        if (half) c(x, y, z, spec);
        else w.set(x, y, z, spec);
    }

    /** How far inside an island a point is (> 0 inside). */
    double inside(Isle i, int x, int z) {
        double dx = x - i.x, dz = z - i.z, c = Math.cos(i.ang), s = Math.sin(i.ang);
        double u = Math.abs((dx * c + dz * s) / i.rx), v = Math.abs((-dx * s + dz * c) / i.rz);
        double r = Math.pow(Math.pow(u, i.sq) + Math.pow(v, i.sq), 1 / i.sq);
        return 1 - r + sn(x, z, 7, i.x + i.z) * 0.06 + sn(x, z, 2.5, 3) * 0.025;
    }

    /** The narrowest gap left between two islands, so none can be jumped and every crossing is built. */
    static final double GAP = 7.5;
    static final int GX = -120, GZ = -70, GW = 241, GD = 141;
    byte[] grid;

    /**
     * The island at (x, z). Where two islands come closer than {@link #GAP}, both facing edges are cut back by the
     * same amount until the gap is that wide.
     */
    Isle isle(int x, int z) {
        if (grid == null) carve();
        int gx = x - GX, gz = z - GZ;
        if (gx < 0 || gz < 0 || gx >= GW || gz >= GD) return rawIsle(x, z);
        int k = grid[gz * GW + gx];
        return k < 0 ? null : ISLES[k];
    }

    void carve() {
        byte[] raw = new byte[GW * GD];
        for (int gz = 0; gz < GD; gz++) for (int gx = 0; gx < GW; gx++) {
            Isle i = rawIsle(gx + GX, gz + GZ);
            raw[gz * GW + gx] = (byte) (i == null ? -1 : java.util.Arrays.asList(ISLES).indexOf(i));
        }
        int n = ISLES.length;
        double[][] d0 = new double[n][n];
        for (double[] row : d0) java.util.Arrays.fill(row, 99);
        for (int c = 0; c < GW * GD; c++) {
            int a = raw[c];
            if (a >= 0) for (int b = 0; b < n; b++) if (b != a) d0[a][b] = Math.min(d0[a][b], dist(raw, c, b));
        }
        // first the earlier island of each close pair gives up half of what is missing, then the later one the rest
        grid = raw.clone();
        for (int c = 0; c < GW * GD; c++) {
            int a = raw[c];
            if (a >= 0) for (int b = a + 1; b < n; b++)
                if (d0[a][b] < GAP + 1 && dist(raw, c, b) < (GAP + 1 + d0[a][b]) / 2) grid[c] = -1;
        }
        byte[] half = grid.clone();
        for (int c = 0; c < GW * GD; c++) {
            int b = half[c];
            if (b >= 0) for (int a = 0; a < b; a++)
                if (d0[a][b] < GAP + 1 && dist(half, c, a) < GAP + 1) grid[c] = -1;
        }
    }

    /** Distance from grid cell c to the nearest cell of island k (99 beyond 12). */
    static double dist(byte[] g, int c, int k) {
        int gx = c % GW, gz = c / GW, R = 12;
        double best = 99;
        for (int dz = -R; dz <= R; dz++) for (int dx = -R; dx <= R; dx++) {
            int x = gx + dx, z = gz + dz;
            if (x < 0 || z < 0 || x >= GW || z >= GD || g[z * GW + x] != k) continue;
            best = Math.min(best, Math.sqrt(dx * dx + dz * dz));
        }
        return best;
    }

    /** Whether another island comes within r of (x, z): no tree crowns reaching out across a gap. */
    boolean near(Isle i, int x, int z, int r) {
        for (int dz = -r; dz <= r; dz++) for (int dx = -r; dx <= r; dx++) {
            if (dx * dx + dz * dz > r * r) continue;
            Isle o = isle(x + dx, z + dz);
            if (o != null && o != i) return true;
        }
        return false;
    }

    Isle rawIsle(int x, int z) {
        Isle best = null;
        double bv = 0;
        for (Isle i : ISLES) {
            double v = inside(i, x, z);
            if (v > bv) {
                bv = v;
                best = i;
            }
        }
        return best;
    }

    // ================================================================ the land

    double height(int x, int z) {
        Isle i = isle(x, z);
        if (i == null) return Double.NaN;
        if (i == MILL && isPond(x, z)) return MILL.top - 2;
        return i.top;
    }

    double pond(int x, int z) {
        return Land.ellipse(x, z, -43, 28, 5.6, 3.8, 0.15) * -5 + sn(x, z, 2, 9) * 0.4;
    }

    boolean isPond(int x, int z) {
        return pond(x, z) < 0 && inside(MILL, x, z) > 0.15;
    }

    String surface(int x, int z, int y, int steep, double edge) {
        Isle i = isle(x, z);
        if (i == MILL && isPond(x, z)) return "mud";
        double n1 = sn(x, z, 9, 5), n2 = sn(x, z, 5, 50), n3 = sn(x, z, 3, 21);
        if (edge > 1.5 && steep >= 2) return rock(x, y, z);
        if (n1 < -0.3) return "grass_block";
        if (n2 > 0.42) return "podzol";
        if (n2 < -0.45) return n3 > 0 ? "coarse_dirt" : "rooted_dirt";
        return "pale_moss_block";
    }

    static final String[] LAYERS = {"deepslate", "tuff", "deepslate", "cobbled_deepslate", "tuff", "calcite", "deepslate", "andesite", "tuff", "deepslate", "smooth_basalt", "tuff"};
    static final int[] THICK = {3, 2, 2, 1, 2, 1, 3, 2, 2, 2, 1, 2};
    static final int CYCLE = 23;

    String rock(int x, int y, int z) {
        double warp = sn(x, z, 24, 11) * 2.2;
        int at = Math.floorMod((int) Math.floor(y + warp), CYCLE), i = 0;
        while (at >= THICK[i]) at -= THICK[i++];
        double tex = w.n3(x * 1.9, y * 1.9, z * 1.9, 4);
        if (tex > 0.64) i = (i + 1) % LAYERS.length;
        else if (tex < -0.66) i = (i + LAYERS.length - 1) % LAYERS.length;
        return LAYERS[i];
    }

    int bottom(int x, int z, int top, double edge) {
        Isle i = isle(x, z);
        double size = i == null ? 10 : Math.min(i.rx, i.rz);
        double e = Math.min(1, edge / (size * 0.95)), lens = Math.sqrt(2 * e - e * e);
        double depth = 10 + size * 1.25;
        double bulge = Math.max(0, sn(x, z, 9, 17)) * 5 * Math.min(1, edge / 8.0);
        return (int) Math.round(top - 2 - depth * lens - bulge + sn(x, z, 5, 5) * 1.8 * Math.min(1, edge / 6.0));
    }

    // ================================================================ build

    @Override
    protected void build() {
        land = new Land(w, t, -108, 108, -54, 54);
        land.top = this::height;
        land.surface = this::surface;
        land.rock = this::rock;
        land.bottom = this::bottom;
        land.water = (x, z) -> isle(x, z) == MILL && isPond(x, z) ? MILL.top : Double.NaN;
        land.soil = P.of("dirt", 3, "rooted_dirt", 1);
        land.build();
        hangers();

        abbey();
        graveyard();
        mill();
        priory();
        circle(NORTH, 0);
        circle(SOUTH, Math.PI);

        // no bridges: the islands float apart and players build their own crossings. Where the old bridges stood,
        // a worn landing with a lamp at the island's edge shows the way across.
        abutment(-60, -19, 1, 0, false);
        abutment(-60, 19, 1, 0, false);
        abutment(-31, -21, 0.83, 0.55, false);
        abutment(-30, -26, 0.93, -0.36, false);
        abutment(-40, 19, -1, 0, false);
        abutment(-31, 21, 0.83, -0.55, false);
        abutment(-30, 26, 0.93, 0.36, false);
        brokenPier();
        overgrow(-18, -18, 0, 18, CENTER.top);
        overgrow(-53, -6, -44, 6, HOME.top);
        abutment(-9, -6, -0.83, -0.55, true);
        abutment(-9, 6, -0.83, 0.55, true);
        abutment(0, -10, 0, -1, true);
        abutment(0, 10, 0, 1, true);
        abutment(0, -33, 0, 1, true);
        abutment(0, 33, 0, -1, true);
        abutment(-4, -36, -0.93, 0.36, true);
        abutment(-4, 36, -0.93, -0.36, true);

        paths();
        trees(new Theme.Tree[] {Theme.Tree.PALE, Theme.Tree.DEAD}, new int[] {9, 1}, 0.03, 8, 2, this::wild);
        plants();
        cleanPaths();

        spawn = new double[] {-86.5, HOME.walk(), 0.5, -90};
        flag = new int[] {-83, HOME.walk(), 15};
        pads.add(new int[] {-64, HOME.walk(), -11});
        pads.add(new int[] {-64, HOME.walk(), 11});
        hills.add(new Hill("North Stones", -3, NORTH.walk(), -41, 3, NORTH.walk() + 3, -35));
        hills.add(new Hill("Ruined Crossing", -3, CENTER.walk(), -3, 3, CENTER.walk() + 3, 3));
        hills.add(new Hill("South Stones", -3, SOUTH.walk(), 35, 3, SOUTH.walk() + 3, 41));
    }

    /** Pale hanging moss and roots under the islands. */
    void hangers() {
        for (int x = -108; x <= 0; x++) for (int z = -54; z <= 54; z++) {
            if (!w.canon(x, z) || isle(x, z) == null || w.r.nextInt(6) != 0) continue;
            int y = low;
            while (y < 80 && w.air(x, y, z)) y++;
            if (y >= 66 || !w.air(x, y - 1, z)) continue;
            int r = w.r.nextInt(5);
            if (r < 2) {
                int len = 1 + w.r.nextInt(3);
                for (int k2 = 1; k2 <= len; k2++) w.set(x, y - k2, z, "pale_hanging_moss[tip=" + (k2 == len) + "]");
            } else w.set(x, y - 1, z, r == 2 ? "hanging_roots" : "pale_hanging_moss[tip=true]");
        }
    }

    boolean wild(int x, int z) {
        if (isClear(x, z, 1)) return false;
        Isle i = isle(x, z);
        if (i == null || inside(i, x, z) < 0.12 || near(i, x, z, 14)) return false;
        Material g = w.type(x, i.top, z);
        return w.air(x, i.top + 1, z) && (g == Material.GRASS_BLOCK || g == Material.PALE_MOSS_BLOCK || g == Material.PODZOL);
    }

    // ================================================================ the abbey

    /** Pale stone: grey stone bricks weathered with patches of tuff, moss creeping up from the ground, a few cracks. */
    String stone(int x, int y, int z, int dy) {
        double n = w.n3(x * 0.9, y * 0.9, z * 0.9, 2), moss = Math.max(0, 0.36 - dy * 0.06);
        if (n > 0.58 - moss) return "mossy_stone_bricks";
        if (n < -0.62) return "cracked_stone_bricks";
        double t2 = w.n3(x * 0.55 + 5, y * 0.55, z * 0.55, 2);
        if (t2 > 0.42) return "tuff_bricks";
        return "stone_bricks";
    }

    void abbey() {
        int y = HOME.top;
        forecourt();
        church(y);
        towers(y);
        cloister(y);
        lodge(y);
        herbGarden(y);
    }

    /** The forecourt before the church: a paved court, a cross of darker paving, lamps on posts. */
    /**
     * The abbey close, between the church door and the island's eastern brink: a dark processional way, a paved
     * rosette round a stone preaching cross, flagstones that fray into moss toward the edges, lamp posts, and at the
     * brink the broken-off first arch of the old aqueduct, which once strode out from here to the priory.
     */
    void forecourt() {
        int y = HOME.top, cx = -58;
        for (int x = -70; x <= -44; x++) for (int z = -9; z <= 9; z++) {
            if (x < -66 && Math.abs(z) > 6 || isle(x, z) != HOME) continue;
            double in = inside(HOME, x, z), n = sn(x, z, 2, 31), d = Math.hypot(x - cx, z);
            if (in < 0.02) continue;
            double fray = Math.min(9.5 - Math.abs(z), in * 45);
            if (fray < 3 && n * 1.4 + fray * 0.45 < 0.9 && Math.abs(z) > 1) continue;
            String p;
            if (d > 4.4 && d <= 5.5) p = "polished_deepslate";
            else if (d <= 4.4) {
                int ax = Math.abs(x - cx), az = Math.abs(z);
                p = ax == az || ax == 0 || az == 0 ? "polished_tuff" : d > 3.2 ? "calcite" : "chiseled_tuff_bricks";
            } else if (Math.abs(z) <= 1) p = Math.abs(z) == 1 ? "polished_deepslate" : Math.floorMod(x, 4) == 0 ? "chiseled_tuff" : "deepslate_tiles";
            else p = (((x >> 1) + (z >> 1)) & 1) == 0 ? "stone_bricks" : "polished_tuff";
            if (d > 5.5 && Math.abs(z) > 1) {
                if (n > 0.42 || fray < 4 && n > 0.1) p = "mossy_stone_bricks";
                else if (n < -0.5) p = "cracked_stone_bricks";
            }
            w.set(x, y, z, p);
            for (int yy = y + 1; yy <= y + 6; yy++) if (!w.air(x, yy, z)) w.set(x, yy, z, "air");
        }
        // the preaching cross on a stepped base
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) w.set(cx + dx, y + 1, dz, dx == 0 && dz == 0 ? "polished_tuff" : "tuff_brick_slab[type=bottom]");
        for (int dy = 2; dy <= 5; dy++) w.set(cx, y + dy, 0, dy == 2 ? "chiseled_tuff" : "tuff_brick_wall");
        w.set(cx, y + 6, 0, "chiseled_stone_bricks");
        w.set(cx, y + 6, -1, "stone_brick_stairs[facing=south,half=top]");
        w.set(cx, y + 6, 1, "stone_brick_stairs[facing=north,half=top]");
        w.set(cx, y + 7, 0, "stone_brick_wall");
        // benches facing the cross
        for (int z : new int[] {-3, 3}) for (int dx = -1; dx <= 1; dx++)
            w.set(cx + dx, y + 1, z * 2, "pale_oak_stairs[facing=" + (z < 0 ? "north" : "south") + ",half=bottom]");
        for (int[] p : new int[][] {{-67, -7}, {-67, 7}, {-52, -6}, {-52, 6}}) {
            w.set(p[0], y + 1, p[1], "polished_tuff");
            w.set(p[0], y + 2, p[1], "tuff_brick_wall");
            w.set(p[0], y + 3, p[1], "tuff_brick_wall");
            w.set(p[0], y + 4, p[1], "soul_lantern");
        }
        brokenArch(y);
        keepClear(-71, -9, -44, 9);
    }

    /** The first arch of the old aqueduct, standing at the brink with its channel broken off over the void. */
    void brokenArch(int y) {
        int x0 = -50;
        while (isle(x0 + 2, 0) == HOME && isle(x0 + 2, 4) == HOME && isle(x0 + 2, -4) == HOME) x0++;
        int x1 = x0 + 1;
        for (int x = x0; x <= x1; x++) for (int s = -1; s <= 1; s += 2) {
            int top = s < 0 ? 10 : 6;
            for (int z = 3 * s; Math.abs(z) <= 4; z += s) for (int dy = 1; dy <= top; dy++) {
                if (s > 0 && dy == top && (x + z & 1) == 0) continue;
                w.set(x, y + dy, z, dy == 1 ? "polished_tuff" : dy == 6 ? "chiseled_tuff" : stone(x, y + dy, z, dy));
            }
            // the arch springs from both piers; only the northern half still meets the crown
            w.set(x, y + 6, 2 * s, "tuff_brick_stairs[facing=" + (s < 0 ? "south" : "north") + ",half=top]");
            if (s < 0) {
                w.set(x, y + 7, -2, "tuff_bricks");
                w.set(x, y + 7, -1, "tuff_brick_stairs[facing=south,half=top]");
                w.set(x, y + 8, -2, "tuff_bricks");
                w.set(x, y + 8, -1, "tuff_bricks");
                w.set(x, y + 8, 0, "tuff_brick_stairs[facing=south,half=top]");
                w.set(x, y + 9, -2, "tuff_bricks");
                w.set(x, y + 9, -1, "tuff_brick_slab[type=bottom]");
            }
        }
        // a stub of the channel on the tall pier, snapped off over the void
        for (int x = x0 - 1; x <= x1 + 2; x++) {
            w.set(x, y + 10, -4, x > x1 ? "tuff_brick_slab[type=top]" : "tuff_bricks");
            w.set(x, y + 10, -3, x > x1 + 1 ? "air" : "tuff_brick_slab[type=top]");
            if (x <= x1) w.set(x, y + 11, -4, "tuff_brick_wall");
        }
        w.set(x0 - 1, y + 9, -4, "tuff_brick_stairs[facing=east,half=top]");
        // fallen stones
        for (int[] r : new int[][] {{x0 - 2, 2}, {x0 - 1, 5}, {x0 - 3, 4}, {x0, 6}}) {
            if (isle(r[0], r[1]) != HOME || !w.air(r[0], y + 1, r[1])) continue;
            w.set(r[0], y + 1, r[1], r[1] == 5 ? "tuff_brick_stairs[facing=west,half=bottom]" : "tuff_brick_slab[type=bottom]");
        }
    }

    /**
     * The church: a nave of pale tuff five bays long with buttresses, tall pointed windows of grey glass and a steep
     * deepslate roof; the great door east under a rose window between the towers; inside, two arcades of pillars,
     * pews either side of a carpeted aisle, the altar on a dais at the west end under the team's banners, soul-lamp
     * chandeliers; doors to the cloister and to the herb garden.
     */
    void church(int y) {
        int x0 = -92, x1 = -74, z0 = -6, z1 = 6, h = 11;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
            w.set(x, y, z, edge ? "polished_tuff" : ((x + z) & 1) == 0 ? "polished_deepslate" : "deepslate_tiles");
            for (int dy = 1; dy <= h + 1; dy++) w.set(x, y + dy, z, !edge ? "air" : dy == 1 ? "polished_tuff" : dy == 8 ? "chiseled_tuff" : dy == h ? "polished_tuff" : stone(x, y + dy, z, dy));
        }
        // buttresses at each bay, stepped
        for (int x = x0 + 3; x <= x1 - 3; x += 4) for (int s = -1; s <= -1; s += 2) {
            int z = s * 7;
            for (int dy = 1; dy <= 9; dy++) w.set(x, y + dy, z, stone(x, y + dy, z, dy));
            w.set(x, y + 10, z, "tuff_brick_stairs[facing=" + (s < 0 ? "south" : "north") + ",half=bottom]");
            for (int dy = 1; dy <= 5; dy++) w.set(x, y + dy, s * 8, stone(x, y + dy, s * 8, dy));
            w.set(x, y + 6, s * 8, "tuff_brick_stairs[facing=" + (s < 0 ? "south" : "north") + ",half=bottom]");
        }
        // tall pointed windows between the buttresses
        for (int x = x0 + 5; x <= x1 - 3; x += 4) for (int z : new int[] {z0, z1}) {
            for (int dy = 3; dy <= 8; dy++) w.set(x, y + dy, z, "gray_stained_glass_pane");
            w.set(x - 1, y + 8, z, "tuff_brick_stairs[facing=east,half=top]");
            w.set(x + 1, y + 8, z, "tuff_brick_stairs[facing=west,half=top]");
            w.set(x, y + 9, z, "chiseled_tuff");
            w.set(x, y + 2, z, "tuff_brick_slab[type=top]");
        }
        // the west end: three lancets over the altar
        for (int z = -2; z <= 2; z += 2) for (int dy = 4; dy <= 8; dy++) w.set(x0, y + dy, z, z == 0 ? "light_gray_stained_glass_pane" : "gray_stained_glass_pane");
        // the roof, and a cross on each gable
        int ridge = k.gable(x0, z0, x1, z1, y + h + 1, true, 1, Kit.Roof.of("deepslate_tiles"), P.of("tuff_bricks", 4, "polished_tuff", 1));
        for (int x : new int[] {x0 - 1, x1 + 1}) {
            for (int dy = 1; dy <= 2; dy++) w.set(x, ridge + dy, 0, "polished_tuff_wall");
            w.set(x, ridge + 2, 0, "chiseled_tuff");
        }
        // the great door: a pointed arch three wide, five high, a rose window above
        for (int z = -1; z <= 1; z++) for (int dy = 1; dy <= 5; dy++) w.set(x1, y + dy, z, dy == 5 && z != 0 ? "tuff_brick_stairs[facing=" + (z < 0 ? "north" : "south") + ",half=top]" : "air");
        w.set(x1, y + 6, 0, "chiseled_tuff");
        for (int z = -2; z <= 2; z += 4) for (int dy = 1; dy <= 5; dy++) w.set(x1 + 1, y + dy, z, dy == 5 ? "tuff_brick_stairs[facing=west,half=bottom]" : "polished_tuff");
        int ry = y + 10;
        for (int dz = -2; dz <= 2; dz++) for (int dy = -2; dy <= 2; dy++) {
            double d = Math.hypot(dz, dy);
            if (d > 2.4) continue;
            w.set(x1, ry + dy, dz, d < 0.6 ? "purple_stained_glass_pane" : d < 1.6 ? "light_gray_stained_glass_pane" : "gray_stained_glass_pane");
        }
        w.set(x1 + 1, y + 7, -3, "TEAM_wall_banner[facing=east]");
        w.set(x1 + 1, y + 7, 3, "TEAM_wall_banner[facing=east]");
        // the arcades: pillars on z = +-3, arches between, the clerestory above
        for (int x = x0 + 3; x <= x1 - 3; x += 3) for (int s = -1; s <= 1; s += 2) {
            for (int dy = 1; dy <= 7; dy++) w.set(x, y + dy, s * 3, dy == 1 || dy == 7 ? "chiseled_tuff" : "polished_tuff");
            for (int dx = 1; dx <= 2; dx++) if (x + dx < x1 - 1) {
                w.set(x + dx, y + 7, s * 3, dx == 1 ? "tuff_brick_stairs[facing=west,half=top]" : "tuff_brick_stairs[facing=east,half=top]");
                w.set(x + dx, y + 8, s * 3, "tuff_bricks");
            }
            w.set(x, y + 8, s * 3, "tuff_bricks");
        }
        for (int x = x0 + 1; x <= x1 - 1; x++) for (int z = -5; z <= 5; z++) if (Math.abs(z) >= 4) w.set(x, y + 9, z, "pale_oak_slab[type=top]");
        // pews either side of the aisle, the carpet, the altar on its dais
        for (int x = x0 + 6; x <= x1 - 3; x += 2) for (int z = -2; z <= 2; z++) {
            if (z == 0) continue;
            w.set(x, y + 1, z, "pale_oak_stairs[facing=east,half=bottom]");
        }
        for (int x = x0 + 3; x <= x1 - 1; x++) w.set(x, y + 1, 0, "TEAM_carpet");
        for (int x = x0 + 1; x <= x0 + 2; x++) for (int z = -3; z <= 3; z++) w.set(x, y + 1, z, x == x0 + 2 ? "polished_deepslate_slab[type=bottom]" : "polished_deepslate");
        w.set(x0 + 1, y + 2, 0, "chiseled_tuff");
        w.set(x0 + 1, y + 3, 0, "candle[candles=4,lit=true]");
        for (int s = -1; s <= 1; s += 2) {
            w.set(x0 + 1, y + 2, s * 2, "candle[candles=3,lit=true]");
            w.set(x0 + 1, y + 6, s * 2, "TEAM_wall_banner[facing=east]");
            w.set(x0 + 1, y + 2, s * 3, "chest[facing=east]");
        }
        w.set(x0 + 1, y + 7, 0, "TEAM_wall_banner[facing=east]");
        for (int x = x0 + 4; x <= x1 - 3; x += 5) {
            for (int z = z0 + 1; z <= z1 - 1; z++) w.set(x, y + h, z, "stripped_pale_oak_log[axis=z]");
            for (int dy = h - 1; dy >= h - 2; dy--) w.set(x, y + dy, 0, "iron_chain[axis=y]");
            w.set(x, y + h - 3, 0, "soul_lantern[hanging=true]");
        }
        // the side doors: south into the cloister, north into the garden
        for (int dy = 1; dy <= 3; dy++) {
            w.set(-81, y + dy, z1, dy == 3 ? "tuff_brick_slab[type=top]" : "air");
            w.set(-80, y + dy, z1, dy == 3 ? "tuff_brick_slab[type=top]" : "air");
            w.set(-85, y + dy, z0, dy == 3 ? "tuff_brick_slab[type=top]" : "air");
            w.set(-84, y + dy, z0, dy == 3 ? "tuff_brick_slab[type=top]" : "air");
        }
        keepClear(x0 - 2, z0 - 3, x1 + 2, z1 + 3);
    }

    /**
     * The twin towers either side of the great door: four storeys of pale tuff, a ladder up inside to a belfry of
     * open pointed arches (a bell in the north one), a broach spire of deepslate with a finial.
     */
    void towers(int y) {
        for (int s = -1; s <= 1; s += 2) {
            int x0 = -75, x1 = -71, z0 = s < 0 ? -11 : 7, z1 = s < 0 ? -7 : 11, h = 17;
            int cx = -73, cz = (z0 + z1) / 2;
            for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1, corner = (x == x0 || x == x1) && (z == z0 || z == z1);
                w.set(x, y, z, "polished_tuff");
                for (int dy = 1; dy <= h; dy++) {
                    String b;
                    if (!edge) b = dy == 8 || dy == 13 ? "pale_oak_planks" : "air";
                    else if (corner) b = dy % 4 == 0 ? "chiseled_tuff" : "polished_tuff";
                    else b = dy == 1 || dy == 8 || dy == 13 ? "polished_tuff" : stone(x, y + dy, z, dy);
                    w.set(x, y + dy, z, b);
                }
            }
            // the door on the outer face, slit windows, the belfry arches
            int dz = s < 0 ? z0 : z1;
            for (int dy = 1; dy <= 3; dy++) w.set(cx, y + dy, dz, dy == 3 ? "tuff_brick_slab[type=top]" : "air");
            for (int dy : new int[] {5, 10}) {
                w.set(x1, y + dy, cz, "gray_stained_glass_pane");
                w.set(cx, y + dy, s < 0 ? z0 : z1, "gray_stained_glass_pane");
            }
            for (int dy = 14; dy <= 16; dy++) {
                w.set(x0, y + dy, cz, dy == 16 ? "tuff_brick_slab[type=top]" : "air");
                w.set(x1, y + dy, cz, dy == 16 ? "tuff_brick_slab[type=top]" : "air");
                w.set(cx, y + dy, z0, dy == 16 ? "tuff_brick_slab[type=top]" : "air");
                w.set(cx, y + dy, z1, dy == 16 ? "tuff_brick_slab[type=top]" : "air");
            }
            // the ladder up the inside of the back wall, through holes in the floors
            int lz = s < 0 ? z1 - 1 : z0 + 1;
            for (int dy = 1; dy <= 13; dy++) w.set(x0 + 1, y + dy, lz, "ladder[facing=" + (s < 0 ? "north" : "south") + "]");
            for (int x = x0 + 1; x < x1; x++) w.set(x, y + 17, cz, "stripped_pale_oak_log[axis=x]");
            if (s < 0) w.set(cx, y + 16, cz, "bell[attachment=ceiling,facing=north]");
            w.hangLight(cx + 1, y + 7, cz, "soul_lantern");
            // the spire
            for (int lvl = 0; lvl <= 10; lvl++) {
                double r = 3.2 - lvl * 0.31;
                if (r < 0.3) break;
                for (int ddx = -3; ddx <= 3; ddx++) for (int ddz = -3; ddz <= 3; ddz++) {
                    double d = Math.max(Math.abs(ddx), Math.abs(ddz)) * 0.8 + Math.min(Math.abs(ddx), Math.abs(ddz)) * 0.35;
                    if (d > r || d < r - 1.1) continue;
                    w.set(cx + ddx, y + h + 1 + lvl, cz + ddz, "deepslate_tiles");
                }
            }
            for (int dy = 0; dy <= 1; dy++) w.set(cx, y + h + 11 + dy, cz, "polished_tuff_wall");
            for (int[] c : new int[][] {{x0, z0}, {x0, z1}, {x1, z0}, {x1, z1}}) {
                w.set(c[0], y + h + 1, c[1], "polished_tuff");
                w.set(c[0], y + h + 2, c[1], "polished_tuff_wall");
            }
            keepClear(x0 - 1, z0 - 1, x1 + 1, z1 + 1);
        }
    }

    /**
     * The cloister south of the nave: a walk three wide round a square garth, arcades of slender columns on its inner
     * side, a lean-to roof, outer walls with windows; gates east and south; the flag in the middle of the garth on its
     * ring of paving, herb beds in the corners.
     */
    void cloister(int y) {
        int x0 = -90, x1 = -76, z0 = 7, z1 = 21;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            boolean walk = x <= x0 + 2 || x >= x1 - 2 || z <= z0 + 2 || z >= z1 - 2;
            for (int yy = y + 1; yy <= y + 6; yy++) w.set(x, yy, z, "air");
            w.set(x, y, z, walk ? (((x + z) & 1) == 0 ? "polished_tuff" : "tuff_bricks") : "grass_block");
        }
        // outer walls (the nave is the north side)
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            boolean outer = (x == x0 || x == x1 || z == z1) && z >= z0;
            if (!outer) continue;
            for (int dy = 1; dy <= 5; dy++) w.set(x, y + dy, z, dy == 1 ? "polished_tuff" : dy == 5 ? "polished_tuff" : stone(x, y + dy, z, dy));
        }
        for (int i = 0; i < 4; i++) {
            w.set(x0, y + 3, z0 + 3 + i * 3, "gray_stained_glass_pane");
            w.set(x0 + 3 + i * 3, y + 3, z1, "gray_stained_glass_pane");
        }
        // gates
        for (int dy = 1; dy <= 3; dy++) for (int o = -1; o <= 1; o++) {
            w.set(x1, y + dy, 14 + o, dy == 3 && o != 0 ? "tuff_brick_stairs[facing=" + (o < 0 ? "north" : "south") + ",half=top]" : "air");
            w.set(-83 + o, y + dy, z1, dy == 3 && o != 0 ? "tuff_brick_stairs[facing=" + (o < 0 ? "west" : "east") + ",half=top]" : "air");
        }
        // the arcade round the garth (x -87..-79, z 10..18)
        int gx0 = x0 + 3, gx1 = x1 - 3, gz0 = z0 + 3, gz1 = z1 - 3;
        for (int x = gx0; x <= gx1; x++) for (int z = gz0; z <= gz1; z++) {
            boolean ring = x == gx0 || x == gx1 || z == gz0 || z == gz1;
            if (!ring) continue;
            boolean col = Math.floorMod(x - gx0, 2) == 0 && Math.floorMod(z - gz0, 2) == 0;
            boolean opening = (x == -83 && (z == gz0 || z == gz1)) || (z == 14 && (x == gx0 || x == gx1));
            if (col && !opening) {
                w.set(x, y + 1, z, "polished_tuff");
                w.set(x, y + 2, z, "polished_tuff_wall");
                w.set(x, y + 3, z, "polished_tuff_wall");
            }
            w.set(x, y + 4, z, col ? "chiseled_tuff" : "tuff_bricks");
            w.set(x, y, z, "polished_tuff");
        }
        // the roof over the walk
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            boolean walk = x <= x0 + 2 || x >= x1 - 2 || z <= z0 + 2 || z >= z1 - 2;
            if (!walk) continue;
            w.set(x, y + 5, z, "deepslate_tiles");
            w.set(x, y + 6, z, (x == x0 || x == x1 || z == z1) ? "deepslate_tiles" : "deepslate_tile_slab[type=bottom]");
        }
        for (int x = gx0 + 1; x < gx1; x += 3) for (int z : new int[] {z0 + 1, z1 - 1}) w.hangLight(x, y + 4, z, "soul_lantern");
        // the garth: the flag's ring, cross paths, herb beds
        int fx = -83, fz = 14;
        for (int x = gx0 + 1; x < gx1; x++) for (int z = gz0 + 1; z < gz1; z++) {
            double d = Math.hypot(x - fx, z - fz);
            if (d < 2.6) w.set(x, y, z, d < 1 ? "chiseled_tuff" : "polished_tuff");
            else if (x == fx || z == fz) w.set(x, y, z, "packed_mud");
            else if (Math.abs(x - fx) >= 2 && Math.abs(z - fz) >= 2) {
                w.set(x, y, z, "rooted_dirt");
                int r = w.r.nextInt(4);
                w.set(x, y + 1, z, r == 0 ? "lily_of_the_valley" : r == 1 ? "open_eyeblossom" : r == 2 ? "fern" : "short_grass");
            }
        }
        keepClear(x0 - 1, z0, x1 + 1, z1 + 2);
    }

    /**
     * The abbot's lodge north of the church: a stone ground floor and a timber-framed upper floor of pale oak and
     * calcite under a deepslate roof, a door toward the church, a study below and a bedroom above (up a ladder).
     */
    void lodge(int y) {
        int x0 = -92, x1 = -82, z0 = -21, z1 = -14;
        level(x0 - 1, z0 - 1, x1 + 1, z1 + 1, y, P.of("polished_tuff"), P.of("tuff"));
        k.walls(x0, z0, x1, z1, y, 4, Kit.Bands.of(0, "polished_tuff", 1, P.of("tuff_bricks", 4, "mossy_stone_bricks", 1)), "polished_tuff", 0, P.of("pale_oak_planks"));
        k.walls(x0, z0, x1, z1, y + 4, 4, Kit.Bands.of(0, "stripped_pale_oak_wood", 1, "calcite", 4, "stripped_pale_oak_log[axis=x]"), "pale_oak_log", 3, P.of("pale_oak_planks"));
        int ridge = k.gable(x0, z0, x1, z1, y + 9, true, 1, Kit.Roof.of("deepslate_tiles"), P.of("calcite", 3, "stripped_pale_oak_wood", 1));
        for (int yy = y + 1; yy <= ridge + 2; yy++) w.set(x0 + 2, yy, z0, "tuff_bricks");
        w.set(x0 + 2, ridge + 3, z0, "tuff_brick_wall");
        k.doorway(-87, y, z1, "south", 1, "pale_oak_door", null, null, null);
        w.set(-87, y + 1, z1, "pale_oak_door[facing=north,half=lower,hinge=left,open=true]");
        w.set(-87, y + 2, z1, "pale_oak_door[facing=north,half=upper,hinge=left,open=true]");
        for (int x : new int[] {-90, -84}) {
            k.window(x, y + 2, z1, "south", 1, 1, "glass_pane", null, "pale_oak_trapdoor");
            k.window(x, y + 6, z1, "south", 1, 1, "glass_pane", null, null);
            k.window(x, y + 6, z0, "north", 1, 1, "glass_pane", null, null);
        }
        k.window(x1, y + 2, -17, "east", 1, 1, "glass_pane", null, "pale_oak_trapdoor");
        // inside: the study (desk, lectern, shelves), a ladder to the bedroom
        for (int x = x0 + 1; x <= x0 + 3; x++) for (int dy = 1; dy <= 2; dy++) w.set(x, y + dy, z0 + 1, "bookshelf");
        w.set(x1 - 2, y + 1, z0 + 1, "lectern[facing=south]");
        w.set(x1 - 1, y + 1, z0 + 2, "barrel[facing=up]");
        w.set(-88, y + 1, -18, "pale_oak_slab[type=top]");
        w.set(-87, y + 1, -18, "pale_oak_slab[type=top]");
        w.set(-87, y + 1, -17, "pale_oak_stairs[facing=south,half=bottom]");
        w.set(-88, y + 2, -18, "candle[candles=2,lit=true]");
        for (int dy = 1; dy <= 5; dy++) w.set(x1 - 1, y + dy, z1 - 1, "ladder[facing=north]");
        k.bed(x0 + 2, y + 5, -17, "west", "white");
        w.set(x0 + 1, y + 5, -19, "chest[facing=east]");
        w.hangLight(-87, y + 3, -17, "soul_lantern");
        w.hangLight(-87, y + 7, -17, "soul_lantern");
        keepClear(x0 - 2, z0 - 2, x1 + 2, z1 + 2);
    }

    /** The herb garden: raised beds edged with tuff, a well in the middle, paths of packed mud, a low hedge round it. */
    void herbGarden(int y) {
        int x0 = -78, x1 = -64, z0 = -25, z1 = -15;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            if (isle(x, z) != HOME) continue;
            boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
            boolean path = Math.abs(x - (x0 + x1) / 2) <= 1 || Math.abs(z - (z0 + z1) / 2) <= 0;
            for (int yy = y + 1; yy <= y + 4; yy++) if (!w.air(x, yy, z)) w.set(x, yy, z, "air");
            if (edge) {
                boolean gap = Math.abs(x - (x0 + x1) / 2) <= 1 || Math.abs(z - (z0 + z1) / 2) <= 1;
                w.set(x, y, z, "grass_block");
                if (!gap) w.set(x, y + 1, z, "pale_oak_leaves[persistent=true]");
                continue;
            }
            if (path) {
                w.set(x, y, z, "packed_mud");
                continue;
            }
            boolean rim = Math.abs(x - (x0 + x1) / 2) == 2 || Math.abs(z - (z0 + z1) / 2) == 1 || x == x0 + 1 || x == x1 - 1 || z == z0 + 1 || z == z1 - 1;
            w.set(x, y, z, rim ? "polished_tuff" : "rooted_dirt");
            if (!rim) {
                int r = w.r.nextInt(6);
                w.set(x, y + 1, z, r == 0 ? "lily_of_the_valley" : r == 1 ? "allium" : r == 2 ? "fern" : r == 3 ? "azure_bluet" : r == 4 ? "sweet_berry_bush[age=3]" : "open_eyeblossom");
            }
        }
        // the well
        int wx = (x0 + x1) / 2, wz = (z0 + z1) / 2;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            boolean mid = dx == 0 && dz == 0;
            for (int yy = y - 4; yy <= y; yy++) w.set(wx + dx, yy, wz + dz, mid && yy > y - 4 ? "water" : "cobbled_deepslate");
            if (!mid) w.set(wx + dx, y + 1, wz + dz, dx == 0 || dz == 0 ? "polished_tuff_wall" : "polished_tuff");
        }
        for (int s = -1; s <= 1; s += 2) for (int dy = 2; dy <= 3; dy++) w.set(wx + s, y + dy, wz, "pale_oak_fence");
        for (int dx = -1; dx <= 1; dx++) w.set(wx + dx, y + 4, wz, "stripped_pale_oak_log[axis=x]");
        w.set(wx, y + 3, wz, "iron_chain[axis=y]");
        w.set(wx, y + 2, wz, "soul_lantern[hanging=true]");
        keepClear(x0, z0, x1, z1);
    }

    // ================================================================ the graveyard isle

    /**
     * The graveyard: a little mortuary chapel (a bell gable, a pointed door, benches and an altar inside), rows of
     * headstones on mounds, an old pale oak, a lych-gate at the western brink facing the abbey, lamps.
     */
    void graveyard() {
        int y = GRAVE.top;
        // the chapel
        int x0 = -42, x1 = -34, z0 = -31, z1 = -27;
        level(x0 - 1, z0 - 1, x1 + 1, z1 + 1, y, P.of("polished_tuff"), P.of("tuff"));
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
            w.set(x, y, z, edge ? "polished_tuff" : "deepslate_tiles");
            for (int dy = 1; dy <= 6; dy++) w.set(x, y + dy, z, !edge ? "air" : dy == 1 ? "polished_tuff" : stone(x, y + dy, z, dy));
        }
        k.gable(x0, z0, x1, z1, y + 7, true, 1, Kit.Roof.of("deepslate_tiles"), P.of("tuff_bricks"));
        for (int dy = 1; dy <= 4; dy++) w.set(-38, y + dy, z1, dy == 4 ? "chiseled_tuff" : "air");
        w.set(-39, y + 3, z1, "tuff_brick_stairs[facing=east,half=top]");
        w.set(-37, y + 3, z1, "tuff_brick_stairs[facing=west,half=top]");
        for (int x : new int[] {-41, -35}) for (int dy = 2; dy <= 4; dy++) w.set(x, y + dy, z1, "gray_stained_glass_pane");
        for (int dy = 2; dy <= 4; dy++) w.set(x1, y + dy, -29, "light_gray_stained_glass_pane");
        // the bell gable on the west end
        for (int dy = 7; dy <= 11; dy++) for (int z = -30; z <= -28; z++) w.set(x0, y + dy, z, dy == 11 ? (z == -29 ? "chiseled_tuff" : "tuff_brick_slab[type=bottom]") : dy == 8 || dy == 9 ? (z == -29 ? "air" : "tuff_bricks") : "tuff_bricks");
        w.set(x0, y + 10, -29, "tuff_bricks");
        w.set(x0, y + 9, -29, "bell[attachment=ceiling,facing=east]");
        // inside
        for (int x = x0 + 2; x <= x1 - 2; x += 2) for (int z : new int[] {-30, -28}) if (x != -38) w.set(x, y + 1, z, "pale_oak_stairs[facing=east,half=bottom]");
        w.set(x1 - 1, y + 1, -29, "polished_deepslate");
        w.set(x1 - 1, y + 2, -29, "candle[candles=3,lit=true]");
        w.hangLight(-38, y + 5, -29, "soul_lantern");
        keepClear(x0 - 2, z0 - 2, x1 + 2, z1 + 1);
        // headstones in rows on their mounds
        for (int x = -43; x <= -30; x += 2) for (int z : new int[] {-24, -16}) {
            if (isle(x, z) != GRAVE || inside(GRAVE, x, z) < 0.15 || isClear(x, z, 0)) continue;
            if (!w.air(x, y + 1, z) || !w.air(x, y + 1, z + 1)) continue;
            boolean firm = true;
            for (int dz = 0; dz <= 3; dz++) if (isle(x, z + dz) != GRAVE || land.topAt(x, z + dz) != y) firm = false;
            if (!firm) continue;
            int r = w.r.nextInt(4);
            w.set(x, y + 1, z, r == 0 ? "polished_tuff_wall" : r == 1 ? "cobblestone_wall" : r == 2 ? "mossy_stone_brick_wall" : "tuff_brick_wall");
            if (r == 1) w.set(x, y + 2, z, "polished_tuff_slab[type=bottom]");
            w.set(x, y, z + 1, "coarse_dirt");
            w.set(x, y, z + 2, w.r.nextBoolean() ? "coarse_dirt" : "rooted_dirt");
            if (w.r.nextInt(3) == 0) w.set(x, y + 1, z + 2, w.r.nextBoolean() ? "lily_of_the_valley" : "closed_eyeblossom");
        }
        // the lych-gate at the graveyard's western brink, on firm ground
        int gx = -44;
        boolean firm;
        do {
            gx++;
            firm = true;
            for (int z = -22; z <= -16; z++) if (isle(gx, z) != GRAVE || isle(gx - 1, z) != GRAVE && z >= -21 && z <= -17 && Math.abs(z + 19) == 2) firm = false;
        } while (!firm && gx < -38);
        for (int z : new int[] {-21, -17}) for (int dy = 1; dy <= 3; dy++) w.set(gx, y + dy, z, "stripped_pale_oak_log[axis=y]");
        for (int z = -22; z <= -16; z++) {
            int dz = Math.abs(z + 19);
            w.set(gx, y + 4 + (dz <= 1 ? 1 : 0) + (dz == 0 ? 1 : 0), z, dz == 0 ? "deepslate_tile_slab[type=bottom]" : "deepslate_tile_stairs[facing=" + (z < -19 ? "south" : "north") + ",half=bottom]");
            if (dz <= 2) w.set(gx, y + 4, z, "stripped_pale_oak_log[axis=z]");
        }
        w.set(gx, y + 3, -19, "soul_lantern[hanging=true]");
        w.set(gx, y + 4, -19, "stripped_pale_oak_log[axis=z]");
        lampPost(gx + 2, y, -22);
        lampPost(-33, y, -18);
        flora.tree(Theme.Tree.PALE, -32, y + 1, -31);
    }

    void lampPost(int x, int y, int z) {
        w.set(x, y + 1, z, "polished_tuff");
        w.set(x, y + 2, z, "polished_tuff_wall");
        w.set(x, y + 3, z, "polished_tuff_wall");
        w.set(x, y + 4, z, "soul_lantern");
    }

    // ================================================================ the mill isle

    /**
     * The mill: a stone-and-timber watermill on the bank of its pond, the wheel on its west side turning in the
     * water; inside, the millstones, sacks and a hopper. A granary on staddle stones, a jetty, reeds round the pond.
     */
    void mill() {
        int y = MILL.top;
        int x0 = -37, x1 = -32, z0 = 26, z1 = 32;
        level(x0, z0 - 1, x1 + 1, z1 + 1, y, P.of("cobbled_deepslate", 2, "tuff", 1), P.of("tuff"));
        k.walls(x0, z0, x1, z1, y, 3, Kit.Bands.of(0, "polished_tuff", 1, P.of("tuff_bricks", 3, "cobbled_deepslate", 1)), "polished_tuff", 0, P.of("pale_oak_planks"));
        k.walls(x0, z0, x1, z1, y + 3, 3, Kit.Bands.of(0, "stripped_pale_oak_wood", 1, "pale_oak_planks", 3, "stripped_pale_oak_log[axis=x]"), "pale_oak_log", 3, P.of("pale_oak_planks"));
        k.gable(x0, z0, x1, z1, y + 7, false, 1, Kit.Roof.of("dark_oak_planks"), P.of("pale_oak_planks", 3, "stripped_pale_oak_wood", 1));
        k.doorway(x1, y, 29, "east", 1, "pale_oak_door", null, null, null);
        w.set(x1, y + 1, 29, "pale_oak_door[facing=west,half=lower,hinge=left,open=true]");
        w.set(x1, y + 2, 29, "pale_oak_door[facing=west,half=upper,hinge=left,open=true]");
        k.window(-35, y + 2, z0, "north", 1, 1, "glass_pane", null, "pale_oak_trapdoor");
        k.window(-35, y + 5, z1, "south", 1, 1, "glass_pane", null, "pale_oak_trapdoor");
        k.window(x1, y + 5, 28, "east", 1, 1, "glass_pane", null, null);
        // inside: the millstones on their bed, sacks, the hopper
        w.set(-35, y + 1, 29, "smooth_stone_slab[type=bottom]");
        w.set(-35, y + 1, 30, "smooth_stone_slab[type=bottom]");
        w.set(-35, y + 2, 29, "grindstone[face=floor,facing=north]");
        w.set(-35, y + 3, 29, "hopper[facing=down]");
        for (int[] p : new int[][] {{-34, 31}, {-33, 31}, {-33, 27}}) w.set(p[0], y + 1, p[1], "hay_block");
        w.set(-34, y + 1, 27, "barrel[facing=up]");
        w.hangLight(-34, y + 4, 29, "soul_lantern");
        // the wheel, in the pond against the west wall
        int wx = x0 - 1, wy = y + 2, wz = 29;
        for (int a = 0; a < 360; a += 10) {
            double t = Math.toRadians(a);
            int dz = (int) Math.round(Math.cos(t) * 3), dy = (int) Math.round(Math.sin(t) * 3);
            w.set(wx, wy + dy, wz + dz, "stripped_dark_oak_log[axis=x]");
        }
        for (int i = -2; i <= 2; i++) {
            w.set(wx, wy, wz + i, "dark_oak_planks");
            w.set(wx, wy + i, wz, "dark_oak_planks");
        }
        for (int[] p : new int[][] {{3, 0}, {-3, 0}, {0, 3}, {0, -3}}) w.set(wx - 1, wy + p[1], wz + p[0], "dark_oak_trapdoor[facing=west,half=bottom,open=true]");
        w.set(wx, wy, wz, "stripped_dark_oak_log[axis=x]");
        // the granary on its staddle stones
        int gx0 = -35, gx1 = -32, gz0 = 15, gz1 = 18;
        for (int[] c : new int[][] {{gx0, gz0}, {gx0, gz1}, {gx1, gz0}, {gx1, gz1}}) {
            w.set(c[0], y + 1, c[1], "polished_tuff_wall");
            w.set(c[0], y + 2, c[1], "polished_tuff_slab[type=bottom]");
        }
        k.walls(gx0, gz0, gx1, gz1, y + 2, 3, Kit.Bands.of(0, "pale_oak_planks", 1, "stripped_pale_oak_wood"), "pale_oak_log", 3, P.of("pale_oak_planks"));
        k.gable(gx0, gz0, gx1, gz1, y + 6, true, 1, Kit.Roof.of("dark_oak_planks"), P.of("pale_oak_planks"));
        w.set(gx1, y + 4, (gz0 + gz1) / 2, "pale_oak_trapdoor[facing=east,half=bottom,open=false]");
        keepClear(gx0 - 1, gz0 - 1, gx1 + 1, gz1 + 1);
        // the jetty and reeds round the pond
        for (int x = -45; x <= -43; x++) for (int z = 31; z <= 33; z++) if (isle(x, z) == MILL) w.set(x, y, z, "pale_oak_planks");
        for (int x = -50; x <= -34; x++) for (int z = 20; z <= 36; z++) {
            double p = pond(x, z);
            if (isle(x, z) != MILL || inside(MILL, x, z) < 0.12) continue;
            if (p < 0 && p > -1.2 && w.type(x, y, z) == Material.WATER && w.r.nextInt(5) == 0) w.set(x, y + 1, z, "lily_pad");
            if (p >= 0 && p < 1.1 && w.type(x, y, z).isSolid() && w.air(x, y + 1, z) && !isClear(x, z, 0) && w.r.nextInt(2) == 0) {
                w.set(x, y, z, "mud");
                w.set(x, y + 1, z, "sugar_cane");
                if (w.r.nextBoolean()) w.set(x, y + 2, z, "sugar_cane");
            }
        }
        lampPost(-39, y, 22);
        keepClear(x0 - 2, z0 - 2, x1 + 2, z1 + 2);
    }

    // ================================================================ the ruined priory

    /**
     * The ruined priory in the middle: a paved floor gone to moss and cracks; the crossing's four great piers, two
     * standing tall and two broken low; the stubs of the nave walls either side of where the aqueduct comes in and of
     * the transept walls at the north and south brinks; window tracery in the taller fragments; fallen
     * column drums and rubble. The crossing itself is the middle hill, open to the sky.
     */
    /** Pale moss settles on the broken tops of the ruins and hangs in strands under their arches and ledges. */
    void overgrow(int x0, int z0, int x1, int z1, int ylo) {
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            if (!w.canon(x, z)) continue;
            for (int y = ylo + 30; y >= ylo; y--) {
                Material m = w.type(x, y, z);
                if (m == Material.AIR) continue;
                String k = m.name();
                boolean stone = k.contains("TUFF") || k.contains("STONE_BRICK") || k.contains("DEEPSLATE");
                if (!stone) continue;
                boolean full = !k.endsWith("WALL") && !k.endsWith("STAIRS") && !k.endsWith("SLAB");
                double n = sn(x, z, 1.7, 83) + w.n3(x * 0.5, y * 0.5, z * 0.5, 9) * 0.4;
                if (y >= ylo + 3 && w.air(x, y + 1, z) && !k.endsWith("WALL") && n > 0.12) w.set(x, y + 1, z, "pale_moss_carpet");
                if (y >= ylo + 4 && full && w.air(x, y - 1, z) && w.air(x, y - 2, z) && n > 0.05) {
                    int len = 1 + (int) Math.round(Math.max(0, n) * 3);
                    for (int i = 1; i <= len && w.air(x, y - i - 1, z); i++) w.set(x, y - i, z, "pale_hanging_moss[tip=" + (i == len || !w.air(x, y - i - 2, z)) + "]");
                }
            }
        }
    }

    /** The last pier of the aqueduct on the priory's rim, broken off above its springer, rubble at its foot. */
    void brokenPier() {
        int y = CENTER.top, x0 = -16;
        while (isle(x0 - 1, 3) == CENTER && isle(x0 - 1, 2) == CENTER) x0--;
        x0 += 1;
        for (int x = x0; x <= x0 + 1; x++) for (int z = 2; z <= 3; z++) for (int dy = 1; dy <= 8; dy++) {
            if (dy >= 7 && (x + z + dy & 1) == 0) continue;
            w.set(x, y + dy, z, dy == 1 ? "polished_tuff" : dy == 6 ? "chiseled_tuff" : stone(x, y + dy, z, dy));
        }
        for (int x = x0; x <= x0 + 1; x++) w.set(x, y + 6, 1, "tuff_brick_stairs[facing=south,half=top]");
        for (int[] r : new int[][] {{x0 + 2, 5}, {x0 + 3, 4}, {x0, 5}, {x0 + 2, -1}}) {
            if (isle(r[0], r[1]) != CENTER || !w.air(r[0], y + 1, r[1])) continue;
            w.set(r[0], y + 1, r[1], r[1] == 4 ? "tuff_brick_stairs[facing=north,half=bottom]" : "tuff_brick_slab[type=bottom]");
        }
    }

    void priory() {
        int y = CENTER.top;
        for (int x = -18; x <= 0; x++) for (int z = -18; z <= 18; z++) {
            if (!w.canon(x, z) || isle(x, z) != CENTER) continue;
            double d = Math.hypot(x, z), n = sn(x, z, 2.5, 7), n2 = sn(x, z, 1.4, 33);
            if (d > 13.5 + sn(x, z, 3, 1) * 1.5) continue;
            // toward its edge the old floor is broken up and the ground grows back through it
            if (d > 8.5 && sn(x, z, 1.8, 61) * 1.3 + (13.5 - d) * 0.22 < 0.55) continue;
            String p;
            if (d < 1.5) p = "chiseled_tuff";
            else if (d < 4.6 && Math.max(Math.abs(x), Math.abs(z)) <= 3) p = ((x + z) & 1) == 0 ? "polished_tuff" : "tuff_bricks";
            else p = n > 0.35 ? "pale_moss_block" : n < -0.4 ? "cracked_stone_bricks" : n2 > 0.4 ? "mossy_stone_bricks" : ((x + z) & 1) == 0 ? "polished_deepslate" : "deepslate_tiles";
            w.set(x, y, z, p);
            if (p.equals("pale_moss_block") && w.r.nextInt(3) == 0) w.set(x, y + 1, z, "pale_moss_carpet");
        }
        // the crossing piers: 3 x 3, the north-west one tall with an arch springing toward the north-east
        int[][] piers = {{-7, -7, 15}, {-7, 5, 7}};
        for (int[] p : piers) for (int dx = 0; dx < 3; dx++) for (int dz = 0; dz < 3; dz++) {
            int x = p[0] + dx, z = p[1] + dz;
            int h = p[2] - (int) Math.round(Math.max(0, sn(x, z, 1.3, 5)) * 3) - (dx == 2 || dz == (p[1] < 0 ? 2 : 0) ? 0 : 0);
            boolean corner = dx != 1 && dz != 1, core = dx == 1 && dz == 1;
            for (int dy = 1; dy <= h; dy++) {
                String b = dy == 1 ? "polished_tuff" : corner ? (dy == h || dy % 5 == 0 ? "polished_tuff" : "polished_tuff_wall") : core || dy % 5 == 0 ? "chiseled_tuff" : stone(x, y + dy, z, dy);
                c(x, y + dy, z, b);
            }
            if (w.r.nextInt(3) == 0) c(x, y + h + 1, z, "tuff_brick_slab[type=bottom]");
        }
        // the arch from the tall pier, broken off over the crossing
        for (int i = 1; i <= 5; i++) {
            int x = -4 + i - 1, yy = y + 14 - (i > 3 ? 0 : 0);
            c(x, yy, -6, i == 1 ? "tuff_brick_stairs[facing=west,half=top]" : "tuff_bricks");
            c(x, yy + 1, -6, i <= 4 ? "tuff_bricks" : "tuff_brick_slab[type=bottom]");
            if (i <= 3) c(x, yy - 1, -6, i == 1 ? "tuff_bricks" : "tuff_brick_stairs[facing=west,half=top]");
        }
        for (int i = 1; i <= 3; i++) {
            int z = -4 + i - 1, yy = y + 13;
            c(-6, yy, z, i == 1 ? "tuff_brick_stairs[facing=north,half=top]" : "tuff_bricks");
            c(-6, yy + 1, z, i <= 2 ? "tuff_bricks" : "tuff_brick_slab[type=bottom]");
            c(-6, yy + 2, z, i == 1 ? "tuff_bricks" : "air");
        }
        // the nave walls' stubs either side of the aqueduct's landing, with a tall window fragment
        for (int x = -16; x <= -10; x++) for (int s = -1; s <= 1; s += 2) {
            int z = s * 6;
            if (isle(x, z) != CENTER) continue;
            int h = 1 + (int) Math.round((sn(x, z, 2, 13) + 0.6) * 3.2);
            h = Math.max(1, Math.min(9, h));
            if (x >= -13 && x <= -11) h = x == -12 ? 9 : 8;
            for (int dy = 1; dy <= h; dy++) c(x, y + dy, z, dy == 1 ? "polished_tuff" : stone(x, y + dy, z, dy));
            if (x == -12 && h >= 8) {
                for (int dy = 3; dy <= 6; dy++) c(x, y + dy, z, "air");
                c(x, y + 7, z, "chiseled_tuff");
            }
        }
        // the transept walls' stubs at the north and south brinks
        for (int z = -15; z <= -10; z++) for (int zz : new int[] {z, -z}) {
            int x = -6;
            if (isle(x, zz) != CENTER) continue;
            int h = 1 + (int) Math.round((sn(x, zz, 2, 23) + 0.6) * 2.8);
            h = Math.max(1, Math.min(7, h));
            for (int dy = 1; dy <= h; dy++) c(x, y + dy, zz, dy == 1 ? "polished_tuff" : stone(x, y + dy, zz, dy));
        }
        // fallen column drums and rubble
        for (int[] r : new int[][] {{-11, -2}, {-9, 9}, {-13, 3}, {-3, -10}, {-2, 10}}) {
            int x = r[0], z = r[1];
            if (!w.canon(x, z)) continue;
            boolean along = (x + z & 1) == 0;
            for (int i = 0; i < 3; i++) c(x + (along ? i : 0), y + 1, z + (along ? 0 : i), i == 1 ? "chiseled_tuff" : "polished_tuff");
            c(x + (along ? 0 : 1), y + 1, z + (along ? 1 : 0), "tuff_brick_slab[type=bottom]");
        }
        for (int i = 0; i < 26; i++) {
            int x = -14 + w.r.nextInt(14), z = -13 + w.r.nextInt(27);
            if (!w.canon(x, z) || Math.max(Math.abs(x), Math.abs(z)) <= 4 || Math.abs(z) <= 2 && x < -9) continue;
            if (!w.air(x, y + 1, z) || !w.type(x, y, z).isSolid()) continue;
            c(x, y + 1, z, w.r.nextInt(3) == 0 ? "cobbled_deepslate_slab[type=bottom]" : w.r.nextBoolean() ? "tuff_brick_slab[type=bottom]" : "pale_moss_carpet");
        }
        // lamps on the tall pier and on posts
        for (int[] p : new int[][] {{-10, -4}, {-10, 4}}) {
            c(p[0], y + 1, p[1], "polished_tuff");
            c(p[0], y + 2, p[1], "polished_tuff_wall");
            c(p[0], y + 3, p[1], "soul_lantern");
        }
        keepClear(-9, -9, 9, 9);
        keepClear(-17, -3, -9, 3);
    }

    /**
     * A stone circle on a small isle: seven standing stones (tall and short, some leaning or fallen), a ring of pale
     * moss, eyeblossoms; the hill in the middle, open to the sky. NORTH and SOUTH are built as each other's copies.
     */
    void circle(Isle c, double turn) {
        int y = c.top, cx = (int) c.x, cz = (int) c.z;
        for (int dx = -8; dx <= 8; dx++) for (int dz = -8; dz <= 8; dz++) {
            int x = cx + dx, z = cz + dz;
            if (!w.canon(x, z) || isle(x, z) != c) continue;
            double d = Math.hypot(dx, dz);
            if (d < 1.2) c(x, y, z, "chiseled_tuff");
            else if (d < 3.6) c(x, y, z, (int) Math.round(d * 2) % 2 == 0 ? "polished_tuff" : "pale_moss_block");
            else if (d > 5 && d < 7.6 && sn(x, z, 1.8, 4) > -0.1) c(x, y, z, "pale_moss_block");
        }
        double[] angles = {210, 240, 270, 300, 330, 55, 125};
        int[] heights = {4, 5, 5, 3, 4, 2, 4};
        for (int i = 0; i < angles.length; i++) {
            double a = Math.toRadians(angles[i]) + turn;
            int x = cx + (int) Math.round(Math.cos(a) * 6.3), z = cz + (int) Math.round(Math.sin(a) * 6.3);
            if (!w.canon(x, z)) continue;
            for (int dy = 1; dy <= heights[i]; dy++) {
                String b = dy == heights[i] ? (heights[i] >= 3 ? "polished_tuff" : "tuff") : "tuff";
                if (w.n3(x, dy, z, 1.5) > 0.4) b = "mossy_cobblestone";
                c(x, y + dy, z, b);
            }
            if (heights[i] >= 4) c(x + (int) Math.round(Math.cos(a)), y + 1, z + (int) Math.round(Math.sin(a)), "tuff_slab[type=bottom]");
        }
        // the lintel across the two tall stones on the far side (240 and 270)
        int ax = cx + (int) Math.round(Math.cos(Math.toRadians(240) + turn) * 6.3), az = cz + (int) Math.round(Math.sin(Math.toRadians(240) + turn) * 6.3);
        int bx = cx + (int) Math.round(Math.cos(Math.toRadians(270) + turn) * 6.3), bz = cz + (int) Math.round(Math.sin(Math.toRadians(270) + turn) * 6.3);
        int n = Math.max(Math.abs(bx - ax), Math.abs(bz - az));
        for (int i = 0; i <= n; i++) {
            int x = ax + (int) Math.round((bx - ax) * (double) i / n), z = az + (int) Math.round((bz - az) * (double) i / n);
            c(x, y + 6, z, "polished_tuff");
            for (int yy = y + 1; yy <= y + 5; yy++) if (i > 0 && i < n && w.type(x, yy, z) == Material.AIR) continue;
        }
        for (int dx = -7; dx <= 7; dx++) for (int dz = -7; dz <= 7; dz++) {
            int x = cx + dx, z = cz + dz;
            double d = Math.hypot(dx, dz);
            if (!w.canon(x, z) || d < 4 || isle(x, z) != c || !w.air(x, y + 1, z) || w.r.nextInt(4) != 0) continue;
            if (w.type(x, y, z) == Material.PALE_MOSS_BLOCK || w.type(x, y, z) == Material.GRASS_BLOCK)
                c(x, y + 1, z, w.r.nextBoolean() ? "open_eyeblossom" : "pale_moss_carpet");
        }
        keepClear(cx - 4, cz - 4, cx + 4, cz + 4);
    }

    // ================================================================ the landings

    /**
     * Walks from (x, z) toward (dx, dz) to the edge of its island; paves the last few squares three wide with worn
     * stone and sets a lamp post either side at the brink. Nothing crosses the gap.
     */
    void abutment(double x, double z, double dx, double dz, boolean half) {
        Isle home = isle((int) Math.round(x), (int) Math.round(z));
        if (home == null) return;
        double len = Math.hypot(dx, dz), ux = dx / len, uz = dz / len, nx = -uz, nz = ux;
        double s = 0;
        while (s < 40 && isle((int) Math.round(x + ux * (s + 1)), (int) Math.round(z + uz * (s + 1))) == home && inside(home, (int) Math.round(x + ux * (s + 1)), (int) Math.round(z + uz * (s + 1))) > 0.04) s += 0.5;
        int y = home.top;
        for (double t = Math.max(0, s - 3.5); t <= s; t += 0.5) for (double o = -1.5; o <= 1.5; o += 0.5) {
            int px = (int) Math.round(x + ux * t + nx * o), pz = (int) Math.round(z + uz * t + nz * o);
            if (isle(px, pz) != home || land.topAt(px, pz) != y || !w.air(px, y + 1, pz)) continue;
            double n = sn(px, pz, 1.5, 71);
            put(half, px, y, pz, n > 0.35 ? "mossy_stone_bricks" : n < -0.35 ? "cracked_stone_bricks" : "polished_tuff");
        }
        for (int side = -1; side <= 1; side += 2) {
            int px = (int) Math.round(x + ux * (s - 0.5) + nx * 2.5 * side), pz = (int) Math.round(z + uz * (s - 0.5) + nz * 2.5 * side);
            if (isle(px, pz) != home || land.topAt(px, pz) != y || !w.air(px, y + 1, pz)) continue;
            put(half, px, y + 1, pz, "polished_tuff");
            if (side < 0) {
                put(half, px, y + 2, pz, "polished_tuff_wall");
                put(half, px, y + 3, pz, "soul_lantern");
            } else put(half, px, y + 2, pz, "tuff_brick_slab[type=bottom]");
        }
    }

    // ================================================================ paths, plants

    /** Gravel and packed-mud paths on the home, graveyard and mill isles between the landings and the buildings. */
    void paths() {
        int[][][] lines = {
            {{-56, -8}, {-59, -14}, {-62, -19}},
            {{-56, 8}, {-59, 14}, {-62, 19}},
            {{-64, -9}, {-66, -13}, {-86, -13}},
            {{-85, -7}, {-85, -13}},
            {{-76, 14}, {-70, 15}, {-64, 17}, {-62, 19}},
            {{-83, 22}, {-83, 24}},
            {{-44, -19}, {-36, -19}, {-31, -21}},
            {{-36, -19}, {-32, -24}, {-28, -27}},
            {{-38, -20}, {-38, -26}},
            {{-41, 19}, {-36, 20}, {-31, 20}},
            {{-36, 20}, {-31, 24}, {-28, 27}},
            {{-31, 21}, {-31, 29}},
        };
        for (int[][] line : lines) for (int i = 0; i + 1 < line.length; i++) {
            int[] p = line[i], q = line[i + 1];
            int n = Math.max(1, Math.max(Math.abs(q[0] - p[0]), Math.abs(q[1] - p[1])));
            for (int j = 0; j <= n; j++) {
                double x = p[0] + (q[0] - p[0]) * (double) j / n, z = p[1] + (q[1] - p[1]) * (double) j / n;
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                    int px = (int) Math.round(x) + dx, pz = (int) Math.round(z) + dz;
                    if (Math.hypot(px - x, pz - z) > 1.25 + sn(px, pz, 2, 41) * 0.4) continue;
                    Isle is = isle(px, pz);
                    if (is == null) continue;
                    Material m = w.type(px, is.top, pz);
                    if (!w.air(px, is.top + 1, pz)) continue;
                    if (m != Material.GRASS_BLOCK && m != Material.PALE_MOSS_BLOCK && m != Material.PODZOL && m != Material.COARSE_DIRT && m != Material.ROOTED_DIRT) continue;
                    double n2 = sn(px, pz, 1.6, 7);
                    w.set(px, is.top, pz, n2 > 0.35 ? "gravel" : n2 < -0.3 ? "packed_mud" : "dirt_path");
                }
                keepClear((int) x - 2, (int) z - 2, (int) x + 2, (int) z + 2);
            }
        }
    }

    void plants() {
        for (int x = -108; x <= 0; x++) for (int z = -54; z <= 54; z++) {
            if (!w.canon(x, z)) continue;
            Isle i = isle(x, z);
            if (i == null || !w.air(x, i.top + 1, z)) continue;
            Material g = w.type(x, i.top, z);
            if (g != Material.GRASS_BLOCK && g != Material.PALE_MOSS_BLOCK && g != Material.PODZOL && g != Material.ROOTED_DIRT) continue;
            if (isClear(x, z, 0) && w.r.nextInt(4) != 0) continue;
            double drift = sn(x, z, 5, 93), roll = w.r.nextDouble();
            String p = null;
            if (g == Material.PALE_MOSS_BLOCK && roll < 0.4) p = roll < 0.3 ? "pale_moss_carpet" : "closed_eyeblossom";
            else if (drift > 0.25 && roll < 0.3) p = roll < 0.08 ? "open_eyeblossom" : roll < 0.16 ? "fern" : roll < 0.2 ? "lily_of_the_valley" : "short_grass";
            else if (roll < 0.07) p = roll < 0.015 ? "large_fern" : "short_grass";
            if (p == null) continue;
            if (p.equals("large_fern")) {
                w.set(x, i.top + 1, z, "large_fern[half=lower]");
                w.set(x, i.top + 2, z, "large_fern[half=upper]");
            } else w.set(x, i.top + 1, z, p);
        }
    }

    void cleanPaths() {
        for (int x = -108; x <= 0; x++) for (int z = -54; z <= 54; z++) {
            if (!w.canon(x, z)) continue;
            for (int y = 65; y <= 90; y++) if (w.type(x, y, z) == Material.DIRT_PATH && !w.air(x, y + 1, z)) {
                Material a = w.type(x, y + 1, z);
                if (a.isSolid() || a.name().endsWith("_SLAB") || a.name().endsWith("_STAIRS")) w.set(x, y, z, "coarse_dirt");
            }
        }
    }
}
