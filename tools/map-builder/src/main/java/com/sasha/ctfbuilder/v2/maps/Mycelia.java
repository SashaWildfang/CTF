package com.sasha.ctfbuilder.v2.maps;

import com.sasha.ctfbuilder.v2.Arena;
import com.sasha.ctfbuilder.v2.Kit;
import com.sasha.ctfbuilder.v2.Land;
import com.sasha.ctfbuilder.v2.Theme;
import com.sasha.ctfbuilder.v2.W.P;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * Mycelia: islands of swamp drifting over the void, grown with giant mushrooms, two teams. Nothing joins the
 * islands: players build every crossing, and no two islands are alike.
 *
 * Each team's village sits on top of a great red cap over its home island, a kidney of moss and mud: a round spawn
 * hall in the middle, cottages, the flag on its ring, the wool towers, a rail round the edge. Shelf-fungus stairs
 * wind round the stem down to the island, where a forager's hut stands on stilts over a pool at the north end and a
 * boardwalk crosses a pool at the south end.
 *
 * Out from home every way is different. North runs the Long Gill, a long thin curving island reaching most of the
 * way to the north hill, with a lookout mushroom (a ladder up its stem) and a fallen giant mushroom to walk through.
 * South, three spore isles step away at different heights and spacings, the middle one carrying a red mushroom with
 * a ladder. Straight ahead, a tall thin spire mushroom stands on a little islet low in the void.
 *
 * The middle is a broken fairy ring: curved arcs of ground round an open hole, rings of small mushrooms on them; the
 * north and south arcs are hills. In the hole the Great Mushroom rises from its root ball, a double stair round its
 * stem up to the third hill on its cap. Drift rocks hang at odd heights.
 */
public final class Mycelia extends Arena {

    static final double SLOPE = 0.3;

    /** A giant mushroom: centre, top block, cap radius, flat-top radius, stem radius, red or brown, its island's ground. */
    record Cap(int x, int z, int top, int r, double flat, int stem, boolean red, int base) {
        int walk() {
            return top + 1;
        }
    }

    /**
     * An island: one circle stretched by rx/rz and turned (a round or oval isle), or a chain of circles (x, z, radius)
     * swept into one shape (a long curving island, or an arc of the ring); and the top block of its ground.
     */
    static final class Isle {
        final String name;
        final int top;
        final double[][] p;
        final double rx, rz, ang, size;

        Isle(String name, int top, double[][] p, double rx, double rz, double ang) {
            this.name = name;
            this.top = top;
            this.p = p;
            this.rx = rx;
            this.rz = rz;
            this.ang = ang;
            double s = 0;
            for (double[] q : p) s = Math.max(s, q[2]);
            this.size = p.length == 1 ? Math.min(rx, rz) : s;
        }

        static Isle blob(String name, double x, double z, double rx, double rz, double ang, int top) {
            return new Isle(name, top, new double[][] {{x, z, 1}}, rx, rz, ang);
        }

        static Isle chain(String name, int top, double[]... p) {
            return new Isle(name, top, p, 1, 1, 0);
        }

        /** An arc of the ring round the middle: radius r from a0 to a1 degrees (0 east, 90 south), half-width hw. */
        static Isle arc(String name, double r, double a0, double a1, double hw, int top) {
            int n = (int) Math.ceil(Math.abs(a1 - a0) / 4) + 1;
            double[][] p = new double[n][];
            for (int i = 0; i < n; i++) {
                double a = Math.toRadians(a0 + (a1 - a0) * i / (n - 1));
                p[i] = new double[] {r * Math.cos(a), r * Math.sin(a), hw};
            }
            return new Isle(name, top, p, 1, 1, 0);
        }

        int walk() {
            return top + 1;
        }
    }

    // the west half; the east half is the same turned about the middle
    static final Isle HOME_ISLE = Isle.chain("home", 61, new double[] {-62, -29, 11}, new double[] {-72, 0, 18}, new double[] {-62, 29, 11});
    static final Isle GILL = Isle.chain("long gill", 63, new double[] {-50, -45, 3.5}, new double[] {-42, -47, 6.5}, new double[] {-32, -45, 6.5},
            new double[] {-23, -41, 5}, new double[] {-15, -36, 3});
    static final Isle S1 = Isle.blob("spore 1", -46, 43, 4.5, 3.5, 0.6, 59);
    static final Isle S2 = Isle.blob("spore 2", -31, 49, 7, 4.5, 0.15, 61);
    static final Isle S3 = Isle.blob("spore 3", -14, 38, 3.5, 3, 0, 65);
    static final Isle SPIRE_ISLE = Isle.blob("spire", -34, 0, 4.5, 3.5, 0, 55);
    static final Isle D1 = Isle.blob("drift 1", -44, -14, 3, 2.6, 0.4, 70);
    static final Isle D2 = Isle.blob("drift 2", -8, -50, 3.2, 2.6, -0.3, 74);
    static final Isle RING_WN = Isle.arc("ring north-west", 26, 200, 224, 4, 65);
    static final Isle RING_WS = Isle.arc("ring south-west", 26, 140, 160, 3.5, 64);
    static final Isle RING_N = Isle.arc("north ring", 26, 256, 284, 4.5, 67);
    static final Isle RING_S = Isle.arc("south ring", 26, 76, 104, 4.5, 67);
    static final Isle ROOT = Isle.blob("root ball", 0, 0, 10, 10, 0, 59);
    static final Isle[] ISLES = {HOME_ISLE, GILL, S1, S2, S3, SPIRE_ISLE, D1, D2, RING_WN, RING_WS, RING_N, RING_S, ROOT};

    static final Cap HOME = new Cap(-68, 0, 86, 22, 18, 7, true, 61);
    static final Cap A = new Cap(-43, -48, 81, 8, 5, 3, false, 63);
    static final Cap B = new Cap(-31, 49, 78, 7, 4, 3, true, 61);
    static final Cap SPIRE = new Cap(-34, 0, 83, 4, 2.5, 2, false, 55);
    static final Cap GREAT = new Cap(0, 0, 89, 14, 11, 4, true, 59);
    static final Cap[] CAPS = {HOME, A, B, SPIRE, GREAT};

    /** Pools (x, z, radius), each with its copy turned about the middle. */
    static final double[][] POOLS = {{-63, -30, 4.0}, {-63, 31, 3.2}, {-36, -46, 2.3}};

    Land land;

    public Mycelia(World world) {
        super(world, "mycelia", false, Theme.get("mycelia"));
        name = "Mycelia";
        icon = "RED_MUSHROOM";
        minutes = 14;
        kothPoints = 250;
        captureSeconds = 8;
        reach = 104;
        low = 12;
        high = 130;
        voidY = 12;
        spectator = new double[] {0.5, 118, 0.5, -90, 40};
    }

    // ================================================================ helpers

    /** Noise that's the same at (x, z) and (-x, -z), so the two halves meet without a seam. */
    double sn(double x, double z, double scale, double off) {
        return (w.fbm(x + off, z - off, scale, 2) + w.fbm(-x + off, -z - off, scale, 2)) * 0.7;
    }

    void put(boolean half, int x, int y, int z, String spec) {
        if (half) c(x, y, z, spec);
        else w.set(x, y, z, spec);
    }

    /** A walking surface at walk height q (a multiple of a half): a full block, or a bottom slab. Returns its block y. */
    int tread(boolean half, int x, int z, double q, String full, String slab) {
        boolean s = q != Math.floor(q);
        int y = s ? (int) Math.floor(q) : (int) q - 1;
        put(half, x, y, z, s ? slab + "[type=bottom]" : full);
        return y;
    }

    // ================================================================ the islands

    /** How far inside an island a point is (> 0 inside, 0 at its edge), with a ragged edge. */
    double inside(Isle i, int x, int z) {
        double v;
        if (i.p.length == 1) {
            double dx = x - i.p[0][0], dz = z - i.p[0][1], c = Math.cos(i.ang), s = Math.sin(i.ang);
            v = 1 - Math.hypot((dx * c + dz * s) / i.rx, (-dx * s + dz * c) / i.rz);
        } else {
            v = -9;
            for (int k = 0; k + 1 < i.p.length; k++) {
                double[] a = i.p[k], b = i.p[k + 1];
                double ex = b[0] - a[0], ez = b[1] - a[1], l2 = ex * ex + ez * ez;
                double t = Math.max(0, Math.min(1, ((x - a[0]) * ex + (z - a[1]) * ez) / l2));
                double d = Math.hypot(x - a[0] - ex * t, z - a[1] - ez * t), r = a[2] + (b[2] - a[2]) * t;
                v = Math.max(v, 1 - d / r);
            }
        }
        return v + sn(x, z, 6, i.top * 7) * 0.08 + sn(x, z, 2.2, 3) * 0.03;
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

    /** The narrowest gap left between two islands, so none can be jumped and every crossing is built. */
    static final double GAP = 5;
    static final int GX = -120, GZ = -70, GW = 241, GD = 141;
    byte[] grid;

    /** The island at (x, z): where two come closer than {@link #GAP}, their facing edges are cut back until it's that wide. */
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
        // the earlier island of each close pair gives up half of what is missing, then the later one the rest
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

    /** Distance from grid cell c to the nearest cell of island k (99 beyond 10). */
    static double dist(byte[] g, int c, int k) {
        int gx = c % GW, gz = c / GW, R = 10;
        double best = 99;
        for (int dz = -R; dz <= R; dz++) for (int dx = -R; dx <= R; dx++) {
            int x = gx + dx, z = gz + dz;
            if (x < 0 || z < 0 || x >= GW || z >= GD || g[z * GW + x] != k) continue;
            best = Math.min(best, Math.sqrt(dx * dx + dz * dz));
        }
        return best;
    }

    /** Whether another island comes within r of (x, z): no mushroom crowns reaching across a gap. */
    boolean near(Isle i, int x, int z, int r) {
        for (int dz = -r; dz <= r; dz++) for (int dx = -r; dx <= r; dx++) {
            if (dx * dx + dz * dz > r * r) continue;
            Isle o = isle(x + dx, z + dz);
            if (o != null && o != i) return true;
        }
        return false;
    }

    double pool(int x, int z) {
        double best = 1e9;
        for (double[] p : POOLS) {
            for (int s = -1; s <= 1; s += 2) {
                double d = Math.hypot(x - s * p[0], z - s * p[1]) - p[2] - sn(x, z, 3, p[0]) * 0.6;
                best = Math.min(best, d);
            }
        }
        return best;
    }

    boolean isPool(int x, int z) {
        Isle i = isle(x, z);
        return i != null && pool(x, z) < 0 && inside(i, x, z) > 0.2;
    }

    /** The top block of the ground at (x, z), or Integer.MIN_VALUE over the void. */
    int ground(int x, int z) {
        Isle i = isle(x, z);
        return i == null ? Integer.MIN_VALUE : i.top;
    }

    double height(int x, int z) {
        Isle i = isle(x, z);
        if (i == null) return Double.NaN;
        return isPool(x, z) ? i.top - 1 : i.top;
    }

    String surface(int x, int z, int y, int steep, double edge) {
        Isle i = isle(x, z);
        int top = i == null ? y : i.top;
        if (y < top) return sn(x, z, 2.5, 9) > 0.2 ? "clay" : "mud";
        double n1 = sn(x, z, 11, 5), n2 = sn(x, z, 7, 50);
        if (pool(x, z) < 1.2 && i != null && inside(i, x, z) > 0.15) return n2 > 0 ? "mud" : "muddy_mangrove_roots";
        if (edge < 1.2 && n2 > 0.1) return "rooted_dirt";
        if (n1 > 0.42) return "mycelium";
        if (n2 > 0.33) return "podzol";
        if (n2 < -0.45) return "rooted_dirt";
        return "moss_block";
    }

    static final String[] LAYERS = {"stone", "tuff", "stone", "andesite", "stone", "granite", "stone", "cobbled_deepslate", "tuff", "stone", "deepslate", "andesite"};
    static final int[] THICK = {3, 1, 2, 2, 2, 1, 3, 1, 2, 2, 2, 2};
    static final int CYCLE = 23;

    String rock(int x, int y, int z) {
        Isle i = isle(x, z);
        int top = i == null ? 61 : i.top;
        if (y >= top - 3) return y >= top - 1 ? "dirt" : "rooted_dirt";
        double warp = sn(x, z, 26, 11) * 2.2;
        int at = Math.floorMod((int) Math.floor(y + warp), CYCLE), k = 0;
        while (at >= THICK[k]) at -= THICK[k++];
        double tex = w.n3(x * 1.9, y * 1.9, z * 1.9, 4);
        if (tex > 0.62) k = (k + 1) % LAYERS.length;
        else if (tex < -0.64) k = (k + LAYERS.length - 1) % LAYERS.length;
        return LAYERS[k];
    }

    /**
     * The underside: it tapers away from the rim like an upturned hill (not a drum with a flat bottom), rounding off
     * underneath, about as deep as the island is wide.
     */
    int bottom(int x, int z, int top, double edge) {
        Isle i = isle(x, z);
        double size = i == null ? 4 : i.size;
        double e = Math.min(1, edge / (size * 0.9)), lens = 1 - Math.pow(1 - e, 1.8);
        double depth = 6 + size * 1.7;
        double bulge = Math.max(0, sn(x, z, 9, 17)) * size * 0.3 * Math.min(1, edge / 6.0);
        return (int) Math.round(top - 2 - depth * lens - bulge + sn(x, z, 5, 5) * 1.6 * Math.min(1, edge / 5.0));
    }

    // ================================================================ build

    @Override
    protected void build() {
        land = new Land(w, t, -104, 104, -62, 62);
        land.top = this::height;
        land.surface = this::surface;
        land.rock = this::rock;
        land.bottom = this::bottom;
        land.water = (x, z) -> isPool(x, z) ? isle(x, z).top : Double.NaN;
        land.soil = P.of("dirt");
        land.build();
        roots();

        // the mushrooms, the stairs round the big stems and ladders up the small ones
        for (Cap c : new Cap[] {HOME, A, B, SPIRE}) mushroom(c, false);
        mushroom(GREAT, true);
        spiral(HOME, 7.5, 10.5, Math.PI, false);
        spiral(GREAT, 4.5, 7.5, Math.PI / 2, true, 0);
        spiral(GREAT, 4.5, 7.5, Math.PI * 1.5, true, 0);
        ladder(A);
        ladder(B);
        ladder(SPIRE);

        village();
        capTops();
        greatCap();
        hillArc(-26);
        hillArc(26);
        fairyRings();

        // the islands' ground
        swampPaths();
        boardwalks();
        stiltHut();
        fallenMushroom();
        swampLamps();
        trees(new Theme.Tree[] {Theme.Tree.BROWN_MUSHROOM, Theme.Tree.RED_MUSHROOM, Theme.Tree.AZALEA}, new int[] {3, 2, 2}, 0.025, 8, 2, this::wild);
        swampPlants();
        cleanPaths();

        spawn = new double[] {HOME.x - 4.5, HOME.walk(), 0.5, -90};
        flag = new int[] {HOME.x + 12, HOME.walk(), 0};
        pads.add(new int[] {HOME.x + 6, HOME.walk(), -11});
        pads.add(new int[] {HOME.x + 6, HOME.walk(), 11});
        hills.add(new Hill("North Ring", -3, RING_N.walk(), -29, 3, RING_N.walk() + 3, -23));
        hills.add(new Hill("Great Cap", GREAT.x - 3, GREAT.walk(), GREAT.z - 3, GREAT.x + 3, GREAT.walk() + 3, GREAT.z + 3));
        hills.add(new Hill("South Ring", -3, RING_S.walk(), 23, 3, RING_S.walk() + 3, 29));
    }

    /** Where wild things grow: open ground well inside an island, away from stems, stairs, paths, pools and other islands. */
    boolean wild(int x, int z) {
        Isle i = isle(x, z);
        if (i == null || isClear(x, z, 1) || pool(x, z) < 2 || inside(i, x, z) < 0.3 || near(i, x, z, 9)) return false;
        if (w.type(x, i.top, z) == Material.AIR || !w.air(x, i.top + 1, z)) return false;
        for (Cap c : CAPS) for (int s = -1; s <= 1; s += 2) {
            double d = Math.hypot(x - s * c.x, z - s * c.z);
            if (d < c.stem + 5.5 || (d < c.r + 2 && c.top - c.base < 16)) return false;
        }
        return true;
    }

    /** Hanging roots and glow lichen under each island's lowest blocks. */
    void roots() {
        for (int x = -104; x <= 0; x++) for (int z = -62; z <= 62; z++) {
            if (!w.canon(x, z) || w.r.nextInt(6) != 0) continue;
            Isle i = isle(x, z);
            if (i == null) continue;
            int y = low;
            while (y < i.top && w.air(x, y, z)) y++;
            if (y >= i.top - 3 || !w.air(x, y - 1, z)) continue;
            w.set(x, y - 1, z, w.r.nextInt(3) == 0 ? "glow_lichen[up=true]" : "hanging_roots");
        }
    }

    // ================================================================ giant mushrooms

    /**
     * A giant mushroom: a stem of fibrous white rising from rooted moss on its island and widening into a collar under the cap; a
     * cap 4 thick under its flat top and drooping to a curled rim, pale gills underneath lit here and there with
     * glowing spores, white spots on the red ones; moss and mycelium grown over the flat top.
     */
    void mushroom(Cap c, boolean half) {
        // the stem
        for (int y = c.base - 2; y <= c.top - 3; y++) {
            double collar = y > c.top - 8 ? (y - (c.top - 8)) * 0.4 : 0;
            double rr = c.stem + collar;
            int R = (int) Math.ceil(rr + 1);
            for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > rr + 0.45) continue;
                int x = c.x + dx, z = c.z + dz;
                double fib = w.n3(Math.atan2(dz, dx) * c.stem * 1.3 + c.x, y * 0.25, c.z, 1.2);
                put(half, x, y, z, d > rr - 0.6 && fib > 0.45 ? "calcite" : "mushroom_stem");
            }
        }
        // the moss and roots round its foot
        for (int dx = -c.stem - 4; dx <= c.stem + 4; dx++) for (int dz = -c.stem - 4; dz <= c.stem + 4; dz++) {
            double d = Math.hypot(dx, dz);
            if (d <= c.stem + 0.45 || d > c.stem + 3.5 + sn(c.x + dx, c.z + dz, 2, 3)) continue;
            int x = c.x + dx, z = c.z + dz;
            if (w.type(x, c.base, z) == Material.AIR || ground(x, z) != c.base || pool(x, z) < 0.5) continue;
            put(half, x, c.base, z, d < c.stem + 1.6 ? "rooted_dirt" : "moss_block");
        }
        // the cap
        double drop = Math.max(2, c.r - c.flat);
        for (int dx = -c.r - 1; dx <= c.r + 1; dx++) for (int dz = -c.r - 1; dz <= c.r + 1; dz++) {
            double d0 = Math.hypot(dx, dz), d = d0 <= c.flat ? d0 : c.flat + (d0 - c.flat) * (1 + sn(c.x + dx, c.z + dz, 3, 7) * 0.12);
            if (d > c.r + 0.4) continue;
            int x = c.x + dx, z = c.z + dz;
            int top = d <= c.flat ? c.top : c.top - (int) Math.round(drop * Math.pow((d - c.flat) / (c.r - c.flat), 1.7));
            int under = d <= c.flat + 0.5 ? c.top - 3 : d > c.r - 1.2 ? top - 2 : top - 1;
            double ang = Math.atan2(dz, dx);
            for (int y = under; y <= top; y++) {
                String spec;
                if (y == under) {
                    boolean gill = Math.floorMod((int) Math.round(ang * c.r * 1.1), 2) == 0;
                    spec = d > c.r - 1.2 ? capColour(c, x, y, z) : gill ? "mushroom_stem" : "white_terracotta";
                    if (d > c.stem + 2 && d < c.r - 2 && w.r.nextInt(28) == 0) spec = "shroomlight";
                } else if (y == top && d <= c.flat + 1.2 + sn(x, z, 2.2, 31) * 1.2) {
                    spec = topCover(c, x, z);
                } else {
                    spec = capColour(c, x, y, z);
                }
                put(half, x, y, z, spec);
            }
            for (int y = top + 1; y <= top + 5; y++) if (!w.air(x, y, z)) put(half, x, y, z, "air");
        }
        keepClear(c.x - c.r, c.z - c.r, c.x + c.r, c.z + c.r);
        if (c.x == 0) keepClear(-c.r, -c.z - c.r, c.r, -c.z + c.r);
    }

    String capColour(Cap c, int x, int y, int z) {
        if (!c.red) {
            double n = w.n3(x * 0.9, y * 0.9, z * 0.9, 2.5);
            return n > 0.62 ? "mushroom_stem" : "brown_mushroom_block";
        }
        double spot = w.n3(x * 1.3 + 11, y * 1.3, z * 1.3, 1.8);
        return spot > 0.58 ? "mushroom_stem" : "red_mushroom_block";
    }

    String topCover(Cap c, int x, int z) {
        double n = sn(x, z, 4.5, 21), m = sn(x, z, 2.5, 77);
        if (n > 0.42) return "mycelium";
        if (n < -0.35) return m > 0 ? "podzol" : "moss_block";
        return m > 0.45 ? "rooted_dirt" : "moss_block";
    }

    /**
     * Shelf-fungus stairs round a stem. Treads of mud brick (a block or a slab, so every step is half a block)
     * climb SLOPE a block along the middle line from the island to just under the cap, with fungus shelves under
     * them and a rope rail outside; then a straight flight of stairs runs on up through a hatch in the cap,
     * railed round on top. thetaEnd points along an axis, so the flight's stairs face straight.
     */
    void spiral(Cap c, double r0, double r1, double thetaEnd, boolean half) {
        spiral(c, r0, r1, thetaEnd, half, 0.7);
    }

    void spiral(Cap c, double r0, double r1, double thetaEnd, boolean half, double shelf) {
        double rm = (r0 + r1) / 2, k = SLOPE * rm;
        int ws = c.top - 6;
        double tb = thetaEnd - (ws - c.base - 1) / k;
        int R = (int) Math.ceil(r1 + 1);
        for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) {
            double d = Math.hypot(dx, dz);
            if (d < r0 - 0.2 || d > r1 + 0.6) continue;
            boolean rail = d > r1 - 0.4;
            double phi = Math.atan2(dz, dx);
            double th = phi + 2 * Math.PI * Math.ceil((tb - phi) / (2 * Math.PI));
            int x = c.x + dx, z = c.z + dz;
            for (; th <= thetaEnd + 1e-9; th += 2 * Math.PI) {
                double q = Math.max(c.base + 1, Math.round((c.base + 1 + k * (th - tb)) * 2) / 2.0);
                int y;
                if (rail) {
                    y = (int) Math.floor(q) - 1;
                    put(half, x, y, z, "stripped_spruce_wood");
                    put(half, x, y + 1, z, "spruce_fence");
                    put(half, x, y + 2, z, "air");
                } else {
                    y = tread(half, x, z, q, "mud_bricks", "mud_brick_slab");
                    for (int yy = y + 1; yy <= y + 3; yy++) put(half, x, yy, z, "air");
                }
                int thick = 1 + (int) Math.round((r1 - d) * shelf);
                for (int yy = y - thick; yy < y; yy++) if (yy > c.base || !w.type(x, yy, z).isSolid()) put(half, x, yy, z, "brown_mushroom_block");
            }
        }
        // the flight up through the cap
        int tx = (int) Math.round(-Math.sin(thetaEnd)), tz = (int) Math.round(Math.cos(thetaEnd));
        int ux = (int) Math.round(Math.cos(thetaEnd)), uz = (int) Math.round(Math.sin(thetaEnd));
        int px = c.x + (int) Math.round(rm * ux), pz = c.z + (int) Math.round(rm * uz);
        String face = tx > 0 ? "east" : tx < 0 ? "west" : tz > 0 ? "south" : "north";
        int steps = c.top - ws + 1;
        for (int i = 0; i < steps; i++) for (int o = -2; o <= 2; o++) {
            int x = px + tx * (i + 1) + ux * o, z = pz + tz * (i + 1) + uz * o;
            int y = ws + i;
            if (Math.abs(o) == 2) {
                if (y + 1 < c.top - 3) {
                    put(half, x, y, z, "stripped_spruce_wood");
                    put(half, x, y + 1, z, "spruce_fence");
                }
                continue;
            }
            put(half, x, y, z, "mud_brick_stairs[facing=" + face + ",half=bottom]");
            put(half, x, y - 1, z, "brown_mushroom_block");
            for (int yy = y + 1; yy <= y + 3; yy++) if (yy <= c.top) put(half, x, yy, z, "air");
        }
        // a rail round the hatch on top
        for (int i = 1; i < steps; i++) for (int o = -2; o <= 2; o++) {
            boolean ring = Math.abs(o) == 2 || i == 1;
            if (!ring) continue;
            int x = px + tx * (i + 1) + ux * o, z = pz + tz * (i + 1) + uz * o;
            if (half && !w.canon(x, z)) continue;
            if (w.type(x, c.top, z).isSolid() && w.air(x, c.top + 1, z)) put(half, x, c.top + 1, z, "spruce_fence");
        }
        int hx0 = Math.min(px + tx - 2 * Math.abs(ux), px + tx * steps + 2 * Math.abs(ux)), hx1 = Math.max(px + tx + 2 * Math.abs(ux), px + tx * (steps + 1) - 2 * Math.abs(ux));
        int hz0 = Math.min(pz + tz - 2 * Math.abs(uz), pz + tz * steps + 2 * Math.abs(uz)), hz1 = Math.max(pz + tz + 2 * Math.abs(uz), pz + tz * (steps + 1) - 2 * Math.abs(uz));
        keepClear(hx0 - 1, hz0 - 1, hx1 + 1, hz1 + 1);
        keepClear(c.x - R - 1, c.z - R - 1, c.x + R + 1, c.z + R + 1);
        if (half) keepClear(-c.x - R - 1, -c.z - R - 1, -c.x + R + 1, -c.z + R + 1);
    }

    /** A ladder up the west side of a small mushroom's stem, through the cap, with a post either side of the hole on top. */
    void ladder(Cap c) {
        int x = c.x - c.stem - 1, z = c.z;
        for (int y = c.base + 1; y <= c.top; y++) {
            if (!w.type(x + 1, y, z).isSolid()) w.set(x + 1, y, z, "mushroom_stem");
            w.set(x, y, z, "ladder[facing=west]");
        }
        w.set(x, c.base, z, "rooted_dirt");
        w.set(x - 1, c.base, z, "packed_mud");
        for (int s = -1; s <= 1; s += 2) {
            w.set(x, c.top + 1, z + s, "spruce_fence");
            w.set(x, c.top + 2, z + s, "spruce_fence");
        }
        w.set(x, c.top + 3, z - 1, "lantern");
        w.set(x, c.top + 3, z + 1, "lantern");
        keepClear(x - 2, z - 2, x + 1, z + 2);
    }

    // ================================================================ the home village

    /**
     * The village on the home cap: the spawn hall over the stem, two cottages, the flag on its ring of mud brick,
     * the wool towers' plinths, mushroom lamps, mud-brick lanes between them, little mushroom gardens, and a rope
     * rail round the edge of the flat top (open east and to either side, where bridges are worth building).
     */
    void village() {
        int y = HOME.top;
        // lanes: east door to the flag and on to the openings in the rail, round the hall to the cottages and the hatch
        lane(HOME.x + 7, 0, HOME.x + 15, 0);
        lane(HOME.x + 12, 0, HOME.x + 14, -10);
        lane(HOME.x + 12, 0, HOME.x + 14, 10);
        lane(HOME.x - 7, 0, HOME.x - 12, -4);
        lane(HOME.x - 12, -4, HOME.x - 9, -8);
        for (int s = -1; s <= 1; s += 2) {
            ringLane(s);
        }
        hall(HOME.x, y, HOME.z);
        cottage(HOME.x - 3, y, -13, "south", 0);
        cottage(HOME.x - 3, y, 13, "north", 1);
        // the flag's ring
        int fx = HOME.x + 12;
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            double d = Math.hypot(dx, dz);
            if (d > 3.2) continue;
            w.set(fx + dx, y, dz, d < 1 ? "chiseled_tuff" : d < 2.2 ? "mud_bricks" : "packed_mud");
        }
        // lamps
        for (int[] p : new int[][] {{8, -5}, {8, 5}, {-12, -9}, {-12, 9}, {15, -4}, {15, 4}}) lamp(HOME.x + p[0], y, p[1], true);
        // mushroom gardens between the buildings
        for (int[] g : new int[][] {{-12, 6}, {4, -15}, {4, 15}, {-10, 12}}) garden(HOME.x + g[0], y, g[1]);
        // the rope rail round the edge, open east and to either side
        int hf = (int) Math.ceil(HOME.flat) + 1;
        for (int dx = -hf; dx <= hf; dx++) for (int dz = -hf; dz <= hf; dz++) {
            double d = Math.hypot(dx, dz);
            if (d < HOME.flat - 0.6 || d > HOME.flat + 0.4) continue;
            int x = HOME.x + dx, z = dz;
            double ang = Math.toDegrees(Math.atan2(dz, dx));
            if (Math.abs(Math.abs(ang) - 38) < 9 || Math.abs(ang) < 7) continue;
            if (!w.type(x, y, z).isSolid() || !w.air(x, y + 1, z)) continue;
            w.set(x, y + 1, z, Math.floorMod((int) Math.round(ang), 15) < 3 ? "stripped_spruce_log[axis=y]" : "spruce_fence");
        }
    }

    void ringLane(int s) {
        // round the hall at radius 9.5 from the flag lane to the cottages
        for (int a = 0; a <= 180; a += 3) {
            double r = Math.toRadians(a);
            int x = HOME.x + (int) Math.round(Math.cos(r) * 9.5), z = s * (int) Math.round(Math.sin(r) * 9.5);
            if (a > 128 && s < 0) break;
            pave(x, z, 1);
        }
    }

    void lane(int x0, int z0, int x1, int z1) {
        int n = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0));
        for (int i = 0; i <= n; i++) {
            int x = x0 + (int) Math.round((x1 - x0) * (double) i / Math.max(1, n)), z = z0 + (int) Math.round((z1 - z0) * (double) i / Math.max(1, n));
            pave(x, z, 1);
        }
    }

    void pave(int x, int z, int r) {
        int y = HOME.top;
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            if (Math.abs(dx) + Math.abs(dz) > r + (sn(x + dx, z + dz, 1.5, 3) > 0.2 ? 1 : 0)) continue;
            int px = x + dx, pz = z + dz;
            if (!w.type(px, y, pz).isSolid() || !w.air(px, y + 1, pz)) continue;
            if (Math.hypot(px - HOME.x, pz) > HOME.flat - 0.5) continue;
            double n = sn(px, pz, 1.7, 13);
            w.set(px, y, pz, n > 0.3 ? "packed_mud" : n < -0.35 ? "coarse_dirt" : "mud_bricks");
        }
    }

    /** A mushroom lamp: a short white stem, a shroomlight under a little red cap. */
    void lamp(int x, int y, int z, boolean red) {
        w.set(x, y + 1, z, "mushroom_stem");
        w.set(x, y + 2, z, "mushroom_stem");
        w.set(x, y + 3, z, "shroomlight");
        w.set(x, y + 4, z, red ? "red_mushroom_block" : "brown_mushroom_block");
        for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) w.set(x + d[0], y + 4, z + d[1], (red ? "red" : "brown") + "_mushroom_block");
    }

    /** A little bed of small mushrooms, moss and a fern or two, edged with mud brick. */
    void garden(int cx, int y, int cz) {
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            double d = Math.hypot(dx, dz);
            if (d > 2.3) continue;
            int x = cx + dx, z = cz + dz;
            if (!w.air(x, y + 1, z)) continue;
            if (d > 1.6) {
                w.set(x, y + 1, z, "mud_brick_slab[type=bottom]");
                continue;
            }
            int r = w.r.nextInt(5);
            w.set(x, y, z, r < 2 ? "mycelium" : "moss_block");
            w.set(x, y + 1, z, r == 0 ? "red_mushroom" : r == 1 ? "brown_mushroom" : r == 2 ? "fern" : r == 3 ? "moss_carpet" : "short_grass");
        }
    }

    /**
     * The spawn hall: a round hall of stem-white walls on a timber sill, under a great spotted red cap. A wide door
     * east toward the flag, a back door west toward the stairs, round windows between. Inside: a round table with
     * benches, the team's banners, barrels and chests of kit round the wall, a shroomlight chandelier.
     */
    void hall(int cx, int y, int cz) {
        int r = 7, h = 6;
        roundWalls(cx, y, cz, r, h, "spruce_planks", "stripped_spruce_log");
        // doors: a wide arch east, a door west
        for (int dz = -1; dz <= 1; dz++) for (int dy = 1; dy <= 3; dy++) w.set(cx + r, y + dy, cz + dz, "air");
        for (int dz = -1; dz <= 1; dz++) w.set(cx + r - 1, y + 4, cz + dz, "stripped_spruce_log[axis=z]");
        w.set(cx + r, y + 4, cz, "stripped_spruce_log[axis=x]");
        for (int dy = 1; dy <= 2; dy++) w.set(cx - r, y + dy, cz, "air");
        w.set(cx - r, y + 1, cz, "spruce_door[facing=east,half=lower,hinge=left,open=true]");
        w.set(cx - r, y + 2, cz, "spruce_door[facing=east,half=upper,hinge=left,open=true]");
        roundWindows(cx, y, cz, r, new int[] {45, 90, 135, 225, 270, 315});
        mushroomRoof(cx, y + h + 1, cz, r + 2.4, r, true, 0.8);
        // inside
        k.table(cx, y + 1, cz, "spruce_fence", "spruce_pressure_plate");
        for (int[] b : new int[][] {{1, 0, 0}, {-1, 0, 0}, {0, 1, 1}, {0, -1, 1}}) {
            String f = b[0] > 0 ? "west" : b[0] < 0 ? "east" : b[1] > 0 ? "north" : "south";
            w.set(cx + b[0] * 2, y + 1, cz + b[1] * 2, "spruce_stairs[facing=" + f + ",half=bottom]");
        }
        for (int[] p : new int[][] {{-5, -3}, {-5, 3}, {-3, -5}, {-3, 5}, {3, -5}, {3, 5}}) w.set(cx + p[0], y + 1, cz + p[1], (p[0] + p[1] & 1) == 0 ? "barrel[facing=up]" : "chest[facing=" + (p[1] < 0 ? "south" : "north") + "]");
        w.set(cx - 5, y + 2, cz - 3, "red_mushroom");
        w.set(cx - 5, y + 2, cz + 3, "brown_mushroom");
        w.set(cx + 1, y + 1, cz - 6, "crafting_table");
        w.set(cx + 1, y + 1, cz + 6, "smithing_table");
        for (Object[] p : new Object[][] {{-6, -2, "east"}, {-6, 2, "east"}, {0, -6, "south"}, {0, 6, "north"}, {5, -4, "south"}, {5, 4, "north"}})
            w.set(cx + (int) p[0], y + 4, cz + (int) p[1], "TEAM_wall_banner[facing=" + p[2] + "]");
        for (int dy = h; dy >= h - 1; dy--) w.set(cx, y + dy, cz, "iron_chain[axis=y]");
        w.set(cx, y + h - 2, cz, "shroomlight");
        for (int s = -1; s <= 1; s += 2) w.hangLight(cx - 4, y + h - 1, cz + s * 3, "lantern");
        for (int s = -1; s <= 1; s += 2) w.hangLight(cx + 4, y + h - 1, cz + s * 3, "lantern");
        keepClear(cx - r - 1, cz - r - 1, cx + r + 1, cz + r + 1);
    }

    /** Round walls: a floor, a timber sill and plate, stem-white between, posts at the quarters. */
    void roundWalls(int cx, int y, int cz, int r, int h, String floor, String timber) {
        for (int dx = -r - 1; dx <= r + 1; dx++) for (int dz = -r - 1; dz <= r + 1; dz++) {
            double d = Math.hypot(dx, dz);
            if (d > r + 0.45) continue;
            int x = cx + dx, z = cz + dz;
            boolean shell = d > r - 0.55;
            boolean post = shell && (Math.abs(dx) <= 0 || Math.abs(dz) <= 0 || Math.abs(Math.abs(dx) - Math.abs(dz)) <= 0);
            w.set(x, y, z, shell ? "stripped_spruce_wood" : ((dx * dx + dz * dz) / 6 % 2 == 0 ? floor : "stripped_spruce_wood"));
            for (int dy = 1; dy <= h; dy++) {
                String s;
                if (!shell) s = "air";
                else if (dy == 1 || dy == h) s = timber + "[axis=y]";
                else s = post ? timber + "[axis=y]" : "mushroom_stem";
                w.set(x, y + dy, z, s);
            }
        }
    }

    void roundWindows(int cx, int y, int cz, int r, int[] angles) {
        for (int a : angles) {
            double t = Math.toRadians(a);
            int x = cx + (int) Math.round(Math.cos(t) * r), z = cz + (int) Math.round(Math.sin(t) * r);
            w.set(x, y + 2, z, "glass_pane");
            w.set(x, y + 3, z, "glass_pane");
        }
    }

    /** A mushroom-cap roof: a dome, spotted if red, the rim hanging past the walls with pale gills under it. */
    void mushroomRoof(int cx, int y, int cz, double R, boolean red) {
        mushroomRoof(cx, y, cz, R, R - 3.2, red);
    }

    void mushroomRoof(int cx, int y, int cz, double R, double wallR, boolean red) {
        mushroomRoof(cx, y, cz, R, wallR, red, 0.62);
    }

    void mushroomRoof(int cx, int y, int cz, double R, double wallR, boolean red, double tall) {
        int n = (int) Math.ceil(R) + 1;
        for (int dx = -n; dx <= n; dx++) for (int dz = -n; dz <= n; dz++) {
            double d = Math.hypot(dx, dz);
            if (d > R - 0.25) continue;
            int x = cx + dx, z = cz + dz;
            int top = y + (int) Math.round(Math.sqrt(Math.max(0, R * R - d * d)) * tall);
            boolean eave = d > wallR + 0.5;
            int bottom = eave ? Math.max(y - 1, top - 1) : y - 1;
            if (d > R - 1) bottom = Math.min(bottom, top - 2);
            for (int yy = bottom; yy <= top; yy++) {
                String s;
                if (yy == bottom && eave) s = "mushroom_stem";
                else if (red) s = w.n3(x * 1.4 + 3, yy * 1.4, z * 1.4, 1.5) > 0.56 ? "mushroom_stem" : "red_mushroom_block";
                else s = "brown_mushroom_block";
                w.set(x, yy, z, s);
            }
        }
    }

    /**
     * A cottage: a round house under a cap, a door, round windows. kind 0: a bed, a chest, a table and stools, a
     * stove; kind 1: a herbalist's room of shelves, a brewing stand, a cauldron, pots of mushrooms.
     */
    void cottage(int cx, int y, int cz, String door, int kind) {
        int r = 4, h = 4;
        roundWalls(cx, y, cz, r, h, "spruce_planks", "stripped_spruce_log");
        int ox = Kit.dx(door), oz = Kit.dz(door), sb = -oz;
        int dx0 = cx + ox * r, dz0 = cz + oz * r;
        w.set(dx0, y + 1, dz0, "spruce_door[facing=" + Kit.opp(door) + ",half=lower,hinge=left,open=true]");
        w.set(dx0, y + 2, dz0, "spruce_door[facing=" + Kit.opp(door) + ",half=upper,hinge=left,open=true]");
        roundWindows(cx, y, cz, r, new int[] {0, 180, sb < 0 ? 270 : 90, sb < 0 ? 225 : 135});
        mushroomRoof(cx, y + h + 1, cz, r + 2.0, r, kind == 0, 0.85);
        if (kind == 0) {
            k.bed(cx - 2, y + 1, cz + sb, sb < 0 ? "north" : "south", "TEAM");
            w.set(cx - 1, y + 1, cz + 3 * sb, "chest[facing=" + door + "]");
            w.set(cx + 2, y + 1, cz + 2 * sb, "furnace[facing=" + door + "]");
            w.set(cx + 3, y + 1, cz, "crafting_table");
            w.set(cx, y + 1, cz, "spruce_slab[type=top]");
            w.set(cx, y + 2, cz, "potted_red_mushroom");
            w.set(cx + 1, y + 1, cz, "spruce_stairs[facing=west,half=bottom]");
        } else {
            w.set(cx, y + 1, cz + 3 * sb, "brewing_stand");
            w.set(cx - 1, y + 1, cz + 3 * sb, "cauldron");
            w.set(cx + 1, y + 1, cz + 3 * sb, "barrel[facing=up]");
            for (int s = -1; s <= 1; s += 2) {
                w.set(cx + s * 3, y + 1, cz, "bookshelf");
                w.set(cx + s * 3, y + 2, cz, "potted_brown_mushroom");
            }
            w.set(cx + 2, y + 1, cz + 2 * sb, "composter");
            w.set(cx - 2, y + 1, cz + 2 * sb, "barrel[facing=up]");
        }
        w.hangLight(cx, y + h - 1, cz, "lantern");
        keepClear(cx - r - 3, cz - r - 3, cx + r + 3, cz + r + 3);
    }

    // ================================================================ the tops of the small caps and the hills

    /** A: a lookout post (a tall stem with a lamp and a little cap), benches and barrels. B: a forager's hut. The spire: a lamp. */
    void capTops() {
        int y = A.top;
        for (int dy = 1; dy <= 5; dy++) w.set(A.x, y + dy, A.z, "mushroom_stem");
        w.set(A.x, y + 6, A.z, "shroomlight");
        mushroomRoof(A.x, y + 7, A.z, 2.6, 0.5, false);
        for (int[] d : new int[][] {{2, 0, 0}, {-2, 0, 0}, {0, 2, 1}, {0, -2, 1}}) {
            String f = d[0] > 0 ? "east" : d[0] < 0 ? "west" : d[1] > 0 ? "south" : "north";
            w.set(A.x + d[0], y + 1, A.z + d[1], "spruce_stairs[facing=" + f + ",half=bottom]");
        }
        w.set(A.x + 2, y + 1, A.z + 2, "barrel[facing=up]");
        w.set(A.x - 2, y + 1, A.z - 2, "barrel[facing=up]");
        y = B.top;
        roundWalls(B.x + 1, y, B.z, 2, 3, "spruce_planks", "stripped_spruce_log");
        w.set(B.x + 1 - 2, y + 1, B.z, "air");
        w.set(B.x + 1 - 2, y + 2, B.z, "air");
        roundWindows(B.x + 1, y, B.z, 2, new int[] {0, 90, 270});
        mushroomRoof(B.x + 1, y + 4, B.z, 4.2, 2, true);
        w.set(B.x + 1, y + 1, B.z + 1, "barrel[facing=up]");
        w.set(B.x + 2, y + 1, B.z, "composter");
        w.set(B.x + 1, y + 1, B.z - 1, "barrel[facing=up]");
        w.hangLight(B.x + 1, y + 3, B.z, "lantern");
        lamp(SPIRE.x + 1, SPIRE.top, SPIRE.z, false);
    }

    /** The Great Cap's hill in the middle of its flat top, open to the sky, in a fairy ring of small mushrooms, lamps. */
    void greatCap() {
        int y = GREAT.top;
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            int m = Math.max(Math.abs(dx), Math.abs(dz));
            c(dx, y, dz, m == 3 ? "mud_bricks" : m == 0 ? "chiseled_tuff" : "packed_mud");
        }
        for (int a = 0; a < 360; a += 9) {
            double t = Math.toRadians(a);
            int x = (int) Math.round(Math.cos(t) * 6.5), z = (int) Math.round(Math.sin(t) * 6.5);
            if (!w.canon(x, z) || !w.air(x, y + 1, z) || !w.type(x, y, z).isSolid()) continue;
            w.set(x, y, z, "mycelium");
            w.set(x, y + 1, z, (a / 9) % 3 == 0 ? "brown_mushroom" : "red_mushroom");
        }
        for (int[] p : new int[][] {{-9, -4}, {-5, -9}}) lamp(p[0], y, p[1], true);
    }

    /** The hill on the north (cz < 0) or south ring arc: a floor of mud brick round chiseled tuff in a fairy ring, lamps. */
    void hillArc(int cz) {
        int y = RING_N.top;
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            int x = dx, z = cz + dz;
            if (isle(x, z) == null) continue;
            int m = Math.max(Math.abs(dx), Math.abs(dz));
            c(x, y, z, m == 3 ? "mud_bricks" : m == 0 ? "chiseled_tuff" : "packed_mud");
        }
        for (int a = 0; a < 360; a += 10) {
            double t = Math.toRadians(a);
            int x = (int) Math.round(Math.cos(t) * 5.6), z = cz + (int) Math.round(Math.sin(t) * 5.6);
            if (isle(x, z) == null || !w.air(x, y + 1, z) || !w.type(x, y, z).isSolid()) continue;
            c(x, y, z, "mycelium");
            c(x, y + 1, z, (a / 10) % 3 == 0 ? "brown_mushroom" : "red_mushroom");
        }
        for (int s = -1; s <= 1; s += 2) {
            int x = s * 9, z = cz - Integer.signum(cz);
            if (isle(x, z) != null && w.air(x, y + 1, z)) lamp(x, y, z, s < 0);
        }
        keepClear(-10, cz - 6, 10, cz + 6);
    }

    /** Fairy rings of small mushrooms along the west arcs of the ring, on mycelium. */
    void fairyRings() {
        for (Isle arc : new Isle[] {RING_WN, RING_WS}) {
            double a0 = Math.atan2(arc.p[0][1], arc.p[0][0]), a1 = Math.atan2(arc.p[arc.p.length - 1][1], arc.p[arc.p.length - 1][0]);
            for (double r : new double[] {23.4, 28.6}) for (int k = 0; k <= 40; k++) {
                double a = a0 + (a1 - a0) * k / 40.0;
                int x = (int) Math.round(Math.cos(a) * r), z = (int) Math.round(Math.sin(a) * r);
                if (isle(x, z) != arc || !w.air(x, arc.top + 1, z) || !w.type(x, arc.top, z).isSolid() || isClear(x, z, 0) && k % 3 == 0) continue;
                w.set(x, arc.top, z, "mycelium");
                w.set(x, arc.top + 1, z, (k & 1) == 0 ? "red_mushroom" : "brown_mushroom");
            }
        }
    }

    // ================================================================ the islands' ground

    /** Trodden paths on the home island, along the Long Gill and the ring's arcs. */
    void swampPaths() {
        int[][][] lines = {
            {{-74, 9}, {-70, 16}, {-66, 22}, {-63, 25}},
            {{-74, 9}, {-81, 11}, {-84, 2}, {-82, -10}, {-75, -18}, {-63, -22}},
            {{-70, 16}, {-61, 14}, {-56, 7}},
            {{-49, -43}, {-42, -42}, {-36, -42}, {-31, -44}},
            {{-31, -44}, {-20, -40}, {-15, -37}},
            {{-24, -9}, {-22, -14}, {-19, -18}},
            {{-20, 17}, {-22, 13}, {-24, 9}},
            {{-6, -25}, {-3, -26}},
            {{-6, 25}, {-3, 26}},
        };
        for (int[][] line : lines) for (int i = 0; i + 1 < line.length; i++) {
            int[] p = line[i], q = line[i + 1];
            int n = Math.max(Math.abs(q[0] - p[0]), Math.abs(q[1] - p[1]));
            for (int j = 0; j <= n; j++) {
                double x = p[0] + (q[0] - p[0]) * (double) j / n, z = p[1] + (q[1] - p[1]) * (double) j / n;
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                    int px = (int) Math.round(x) + dx, pz = (int) Math.round(z) + dz;
                    double d = Math.hypot(px - x, pz - z);
                    if (d > 1.3 + sn(px, pz, 2, 41) * 0.5) continue;
                    int g = ground(px, pz);
                    if (g == Integer.MIN_VALUE || !w.air(px, g + 1, pz)) continue;
                    Material m = w.type(px, g, pz);
                    if (m != Material.MOSS_BLOCK && m != Material.MYCELIUM && m != Material.PODZOL && m != Material.ROOTED_DIRT && m != Material.MUD
                            && m != Material.MUDDY_MANGROVE_ROOTS) continue;
                    double n2 = sn(px, pz, 1.6, 7);
                    w.set(px, g, pz, n2 > 0.35 ? "coarse_dirt" : n2 < -0.3 ? "packed_mud" : "dirt_path");
                }
                keepClear((int) x - 2, (int) z - 2, (int) x + 2, (int) z + 2);
            }
        }
    }

    /** A boardwalk over the south pool, flush with the ground, on posts; lily pads, and cattail reeds along the pools' edges. */
    void boardwalks() {
        walk(-63, 26, -63, 36, HOME_ISLE.top);
        for (int x = -104; x <= 0; x++) for (int z = -62; z <= 62; z++) {
            if (!w.canon(x, z)) continue;
            int g = ground(x, z);
            if (g == Integer.MIN_VALUE) continue;
            double p = pool(x, z);
            if (p < 0 && p > -1.1 && w.type(x, g, z) == Material.WATER && w.air(x, g + 1, z) && w.r.nextInt(5) == 0) w.set(x, g + 1, z, "lily_pad");
            if (p >= 0 && p < 1 && w.type(x, g, z).isSolid() && w.air(x, g + 1, z) && !isClear(x, z, 0) && w.r.nextInt(3) == 0) {
                boolean wet = false;
                for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) if (w.type(x + d[0], g, z + d[1]) == Material.WATER) wet = true;
                if (wet) {
                    w.set(x, g, z, "mud");
                    w.set(x, g + 1, z, "sugar_cane");
                    if (w.r.nextBoolean()) w.set(x, g + 2, z, "sugar_cane");
                }
            }
        }
    }

    void walk(int x0, int z0, int x1, int z1, int g) {
        boolean alongX = Math.abs(x1 - x0) > Math.abs(z1 - z0);
        int n = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0));
        for (int i = 0; i <= n; i++) {
            int x = x0 + (alongX ? Integer.signum(x1 - x0) * i : 0), z = z0 + (alongX ? 0 : Integer.signum(z1 - z0) * i);
            for (int o = -1; o <= 1; o++) {
                int px = x + (alongX ? 0 : o), pz = z + (alongX ? o : 0);
                w.set(px, g, pz, ((i + o) & 3) == 0 ? "stripped_spruce_wood" : "spruce_planks");
                for (int yy = g + 1; yy <= g + 3; yy++) if (!w.air(px, yy, pz)) w.set(px, yy, pz, "air");
                if (Math.abs(o) == 1 && i % 4 == 0 && w.type(px, g - 1, pz) == Material.WATER) w.set(px, g - 1, pz, "spruce_log");
            }
            if (i % 4 == 0) for (int o = -1; o <= 1; o += 2) w.set(x + (alongX ? 0 : o), g - 1, z + (alongX ? o : 0), "spruce_log");
        }
        keepClear(Math.min(x0, x1) - 2, Math.min(z0, z1) - 2, Math.max(x0, x1) + 2, Math.max(z0, z1) + 2);
    }

    /**
     * A hut on stilts over the pool at the home island's north end: plank walls on a log frame, a steep roof with a
     * mushroom growing through, a porch, stairs up from a boardwalk on the shore. Inside, a mushroom forager's room.
     */
    void stiltHut() {
        int g = HOME_ISLE.top, mx = -63, x0 = mx - 3, x1 = mx + 3, z1 = -32, z0 = z1 - 5, fy = g + 4, wz = z1 - 2;
        walk(mx, z1 + 6, mx, z1 + 9, g);
        for (int i = 0; i < 4; i++) for (int o = -1; o <= 1; o++) {
            int z = z1 + 5 - i, y = g + 1 + i;
            w.set(mx + o, y, z, "spruce_stairs[facing=north,half=bottom]");
            for (int yy = g; yy < y; yy++) w.set(mx + o, yy, z, o == 0 ? "spruce_planks" : "stripped_spruce_wood");
            for (int yy = y + 1; yy <= y + 3; yy++) w.set(mx + o, yy, z, "air");
        }
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1 + 1; z++) {
            w.set(x, fy, z, z == z1 + 1 ? "spruce_slab[type=top]" : "spruce_planks");
            boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1 + 1);
            if (corner || x == mx && z == z0) for (int yy = g - 1; yy < fy; yy++) w.set(x, yy, z, "spruce_log");
        }
        w.set(mx, fy, z1 + 1, "spruce_planks");
        for (int x = x0; x <= x1; x++) if (Math.abs(x - mx) > 1) w.set(x, fy + 1, z1 + 1, "spruce_fence");
        k.walls(x0, z0, x1, z1, fy, 4, Kit.Bands.of(0, "spruce_planks", 1, "mud_bricks", 2, "spruce_planks", 4, "stripped_dark_oak_log[axis=x]"), "dark_oak_log", 3, P.of("spruce_planks"));
        k.doorway(mx, fy, z1, "south", 1, "spruce_door", null, null, null);
        w.set(mx, fy + 1, z1, "spruce_door[facing=north,half=lower,hinge=left,open=true]");
        w.set(mx, fy + 2, z1, "spruce_door[facing=north,half=upper,hinge=left,open=true]");
        k.window(x0, fy + 2, wz, "west", 1, 1, "glass_pane", null, "spruce_trapdoor");
        k.window(x1, fy + 2, wz, "east", 1, 1, "glass_pane", null, "spruce_trapdoor");
        k.window(mx - 2, fy + 2, z1, "south", 1, 1, "glass_pane", null, null);
        int ridge = k.gable(x0, z0, x1, z1, fy + 5, true, 1, Kit.Roof.of("dark_oak_planks"), P.of("stripped_spruce_wood"));
        for (int z = z0; z <= z0 + 2; z++) w.set(x0 - 1, fy + 3, z, "brown_mushroom_block");
        w.set(x0 - 1, fy + 2, z0 + 1, "brown_mushroom_block");
        w.set(mx, ridge + 1, wz, "mushroom_stem");
        w.set(mx, ridge + 2, wz, "red_mushroom_block");
        w.set(x0 + 1, fy + 1, z0 + 1, "barrel[facing=up]");
        w.set(x0 + 2, fy + 1, z0 + 1, "smoker[facing=south]");
        w.set(x1 - 1, fy + 1, z0 + 1, "cauldron");
        k.bed(x1 - 1, fy + 1, z1 - 1, "north", "brown");
        w.set(x0 + 1, fy + 1, z1 - 1, "composter");
        w.hangLight(mx, fy + 3, wz, "lantern");
        w.set(x0, fy + 1, z1 + 1, "barrel[facing=up]");
        w.set(x1, fy + 1, z1 + 1, "brown_mushroom_block");
        keepClear(x0 - 2, z0 - 2, x1 + 2, z1 + 10);
    }

    /**
     * A giant mushroom that fell long ago along the Long Gill: a hollow stem lying down the island (you walk through
     * it), shelf fungus and moss on its back.
     */
    void fallenMushroom() {
        int x0 = -31, x1 = -20, g = GILL.top, yc = g + 2;
        double z0 = -44.6, slope = 0.44;
        for (int x = x0; x <= x1; x++) for (int dz = -3; dz <= 3; dz++) for (int dy = -3; dy <= 3; dy++) {
            double d = Math.hypot(dz, dy);
            int z = (int) Math.round(z0 + (x - x0) * slope) + dz, y = yc + dy;
            if (d > 3.2 || y < g || isle(x, z) != GILL) continue;
            if (d < 2.0 && y > g) {
                w.set(x, y, z, "air");
                continue;
            }
            if (y == g && d < 2.0) {
                w.set(x, y, z, "moss_block");
                continue;
            }
            String s = d > 2.6 && dy >= 2 ? (w.r.nextInt(3) == 0 ? "moss_block" : "mushroom_stem") : "mushroom_stem";
            w.set(x, y, z, s);
            if (d > 2.6 && dy >= 2 && w.air(x, y + 1, z) && w.r.nextInt(4) == 0) w.set(x, y + 1, z, "moss_carpet");
        }
        for (int x = x0 + 2; x <= x1 - 2; x += 4) {
            int z = (int) Math.round(z0 + (x - x0) * slope) - 4;
            if (isle(x, z) != GILL) continue;
            w.set(x, yc, z, "brown_mushroom_block");
            w.set(x + 1, yc, z, "brown_mushroom_block");
        }
        keepClear(x0 - 1, (int) z0 - 5, x1 + 1, (int) Math.round(z0 + (x1 - x0) * slope) + 5);
    }

    /** Mushroom lamps by the paths and on the small isles. */
    void swampLamps() {
        for (int[] p : new int[][] {{-84, -8}, {-59, -15}, {-59, 15}, {-80, 18}, {-49, -41}, {-25, -37}, {-25, -14}, {-25, 14}, {-46, 43}, {-14, 38}, {-27, 51}}) {
            int g = ground(p[0], p[1]);
            if (g == Integer.MIN_VALUE || !w.type(p[0], g, p[1]).isSolid() || !w.air(p[0], g + 1, p[1])) continue;
            lamp(p[0], g, p[1], (p[0] + p[1] & 1) == 0);
            keepClear(p[0] - 1, p[1] - 1, p[0] + 1, p[1] + 1);
        }
    }

    /** Small mushrooms, ferns, moss carpet, fireflies and glow berries over the islands. */
    void swampPlants() {
        for (int x = -104; x <= 0; x++) for (int z = -62; z <= 62; z++) {
            if (!w.canon(x, z)) continue;
            int f = ground(x, z);
            if (f == Integer.MIN_VALUE || !w.air(x, f + 1, z)) continue;
            Material g = w.type(x, f, z);
            if (g != Material.MOSS_BLOCK && g != Material.MYCELIUM && g != Material.PODZOL && g != Material.ROOTED_DIRT) continue;
            if (isClear(x, z, 0) && !wild(x, z) && w.r.nextInt(3) != 0) continue;
            double drift = sn(x, z, 6, 93), roll = w.r.nextDouble();
            String p = null;
            if (g == Material.MYCELIUM && roll < 0.18) p = w.r.nextBoolean() ? "red_mushroom" : "brown_mushroom";
            else if (drift > 0.25 && roll < 0.35) p = roll < 0.12 ? "fern" : roll < 0.2 ? "moss_carpet" : roll < 0.25 ? "firefly_bush" : "short_grass";
            else if (roll < 0.06) p = roll < 0.02 ? "large_fern" : "short_grass";
            if (p == null) continue;
            if (p.equals("large_fern")) {
                w.set(x, f + 1, z, "large_fern[half=lower]");
                w.set(x, f + 2, z, "large_fern[half=upper]");
            } else w.set(x, f + 1, z, p);
        }
    }

    /** A path block with something standing on it becomes coarse dirt. */
    void cleanPaths() {
        for (int x = -104; x <= 0; x++) for (int z = -62; z <= 62; z++) {
            if (!w.canon(x, z)) continue;
            for (int y = 50; y <= 100; y++) if (w.type(x, y, z) == Material.DIRT_PATH && !w.air(x, y + 1, z)) {
                Material a = w.type(x, y + 1, z);
                if (a.isSolid() || a.name().endsWith("_SLAB") || a.name().endsWith("_STAIRS")) w.set(x, y, z, "coarse_dirt");
            }
        }
    }
}
