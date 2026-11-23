package com.sasha.ctfbuilder.v2.maps;

import com.sasha.ctfbuilder.v2.Arena;
import com.sasha.ctfbuilder.v2.Kit;
import com.sasha.ctfbuilder.v2.Land;
import com.sasha.ctfbuilder.v2.Theme;
import com.sasha.ctfbuilder.v2.W.P;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * Tidecrest: a sea in the sky. One great basin of water held in a lip of rock floats over the void, and the sea
 * catches you: fall or be knocked off anything inside the rim and you land in water and swim. Only the outer rim
 * drops into the void.
 *
 * Each team's village stands on a headland at one end of the sea, whitewashed houses in tiers down the cliff with
 * domes in the team's colour: the plaza on top (the chapel is the spawn, the flag at the head of the great stair),
 * two terraces of houses and windmills, the harbour at the waterline. A bubble lift in a sea cave under the
 * terraces shoots you from the harbour to the plaza in a breath; the headland's flanks drop sheer into deep bays,
 * and a diving board off each side of the plaza is the quickest way down.
 *
 * Between the villages sea stacks of every size rise out of the water, and nothing is built between them: swim,
 * or build your own way across. The Needle (tall and thin, a ladder to a sniper's nest), the Arch Stacks (two
 * pillars whose arch has fallen, its stubs still reaching for each other), Saddle Rock (a ridge down to the palm
 * islet off the harbour). In the middle the Twin Lights stack: two lighthouses, one in each team's colour, with the
 * hill between them and a stair cut up each face from the water. At sea level: a beached galleon on each flank
 * (hills), sandbars with palms, a kelp forest, a coral reef, a sunken colonnade.
 */
public final class Tidecrest extends Arena {

    static final int WL = 63, TOP = 88, T2 = 80, T3 = 72, QUAY = 66, TWIN = 77, SAND = 64, STACK = 70;
    static final double FX = -100;                                       // the headland's terraces are rings round (FX, 0)
    static final double R_TOP = 14, R_T2 = 23, R_T3 = 32, R_QUAY = 38;
    static final int X0 = -136, X1 = 136, Z0 = -72, Z1 = 72;
    /** Sea stacks: x, z, radius, top. Saddle Rock, Arch Stack A, Arch Stack B, the Needle. */
    static final double[][] STACKS = {{-26, -16, 6.5, STACK}, {-50, 18, 6, STACK}, {-36, 30, 5, STACK}, {-50, -32, 3.6, 86}};
    static final double[] RIDGE = {-14, 6};                              // Saddle Rock's ridge runs down this way to the islet
    static final int WX = -22, WZ = -38, LEN = 13;                       // the galleon (the North Wreck; its copy is the South)
    static final double[][] BARS = {{-46, -8, 6.5}, {-14, 30, 7}};
    static final int LIFT_X = -97, LIFT_Z = -10, FLAG_X = -89;
    static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    static final Kit.Roof DOME = new Kit.Roof(null, null, "TEAM_concrete");

    Land land;
    boolean[][] seaMask;
    double[][] toRim, toSea;
    /** Footprints (x0, z0, x1, z1) of stairs, houses and boards: no brink wall or bollard goes in them. */
    final List<int[]> foot = new ArrayList<>();

    public Tidecrest(World world) {
        super(world, "tidecrest", false, Theme.get("tidecrest"));
        name = "Tidecrest";
        icon = "HEART_OF_THE_SEA";
        minutes = 14;
        kothPoints = 250;
        reach = 132;
        low = 10;
        high = 130;
        voidY = 8;
        spectator = new double[] {0.5, 104, 24.5, 180, 30};
    }

    // ================================================================ shapes

    static double supere(double x, double z, double rx, double rz, double n) {
        return 1 - Math.pow(Math.pow(Math.abs(x) / rx, n) + Math.pow(Math.abs(z) / rz, n), 1 / n);
    }

    static boolean in(int x, int z) {
        return x >= X0 && x <= X1 && z >= Z0 && z <= Z1;
    }

    static boolean canonical(int x, int z) {
        return x < 0 || x == 0 && z <= 0;
    }

    /** The sea and the distances either side of its edge, worked out on the canonical half so both halves agree. */
    void distances() {
        int sx = X1 - X0 + 1, sz = Z1 - Z0 + 1;
        seaMask = new boolean[sx][sz];
        for (int x = X0; x <= X1; x++) for (int z = Z0; z <= Z1; z++) {
            int cx = canonical(x, z) ? x : -x, cz = canonical(x, z) ? z : -z;
            seaMask[x - X0][z - Z0] = supere(cx, cz, 112, 54, 2.8) + w.fbm(cx * 0.9 - 300, cz * 0.9, 10, 2) * 0.02 > 0;
        }
        toRim = chamfer(seaMask, true);
        toSea = chamfer(seaMask, false);
    }

    /** Distance from every cell whose mask equals {@code from} to the nearest cell where it doesn't. */
    static double[][] chamfer(boolean[][] mask, boolean from) {
        int sx = mask.length, sz = mask[0].length;
        double[][] d = new double[sx][sz];
        double s2 = Math.sqrt(2);
        for (int i = 0; i < sx; i++) for (int j = 0; j < sz; j++) d[i][j] = mask[i][j] == from ? 1e9 : 0;
        for (int i = 0; i < sx; i++) for (int j = 0; j < sz; j++) {
            if (d[i][j] == 0) continue;
            double v = d[i][j];
            if (i > 0) v = Math.min(v, d[i - 1][j] + 1);
            if (j > 0) v = Math.min(v, d[i][j - 1] + 1);
            if (i > 0 && j > 0) v = Math.min(v, d[i - 1][j - 1] + s2);
            if (i > 0 && j < sz - 1) v = Math.min(v, d[i - 1][j + 1] + s2);
            d[i][j] = v;
        }
        for (int i = sx - 1; i >= 0; i--) for (int j = sz - 1; j >= 0; j--) {
            if (d[i][j] == 0) continue;
            double v = d[i][j];
            if (i < sx - 1) v = Math.min(v, d[i + 1][j] + 1);
            if (j < sz - 1) v = Math.min(v, d[i][j + 1] + 1);
            if (i < sx - 1 && j < sz - 1) v = Math.min(v, d[i + 1][j + 1] + s2);
            if (i < sx - 1 && j > 0) v = Math.min(v, d[i + 1][j - 1] + s2);
            d[i][j] = v;
        }
        return d;
    }

    /** The landmass: the sea, the headlands, and a lip of rock six to eleven blocks wide round them. */
    boolean shelf(int x, int z) {
        if (!in(x, z)) return false;
        if (seaMask[x - X0][z - Z0] || !Double.isNaN(headland(x, z))) return true;
        return toSea[x - X0][z - Z0] <= 8.5 + 2.5 * w.fbm(x * 0.8, z * 0.8 + 400, 12, 2);
    }

    static double hd(int x, int z) {
        return Math.hypot(x - FX, z);
    }

    static double ha(int x, int z) {
        return Math.toDegrees(Math.atan2(z, x - FX));
    }

    /** The headland's ground at (x, z) (the west one; the east is its copy), or NaN: the plaza, two terraces, the quay. */
    static double headland(int x, int z) {
        if (x > -40) return Double.NaN;
        double d = hd(x, z), a = Math.abs(ha(x, z));
        if (d <= R_TOP) return TOP;
        if (d <= R_T2 && a < 75) return T2;
        if (d <= R_T3 && a < 65) return T3;
        if (d <= R_QUAY && a < 55) return QUAY;
        return Double.NaN;
    }

    double height(int x, int z) {
        if (!shelf(x, z)) return Double.NaN;
        int i = x - X0, j = z - Z0;
        double h;
        if (seaMask[i][j]) {
            // the sea floor: shallow by the rim, deep in the middle
            h = 61 - 6.5 * Land.smooth(toRim[i][j] / 14.0) + w.fbm(x * 1.1 + 70, z * 1.1, 9, 2) * 1.3;
            double d = hd(x, z), a = Math.abs(ha(x, z));
            if (d > R_QUAY - 1 && d < R_QUAY + 9 && a < 58) h = Math.min(h, 57);                // the harbour is deep
            if (Math.hypot(x + 6, z + 22) < 8 + w.fbm(x, z, 4, 1)) h = Math.max(h, 60);       // the reef shelves up
            if (Math.hypot(x + 64, z - 36) < 10) h = Math.min(h, 55);                          // the kelp forest is deep
        } else {
            // the lip: a beach rising half a block a block from the water
            double s = toSea[i][j];
            h = 61 + Math.min(5, s * 0.5) + (s > 4 ? w.fbm(x * 1.3, z * 1.3, 12, 2) * 0.5 : 0);
        }
        double hl = headland(x, z);
        if (!Double.isNaN(hl)) h = Math.max(h, hl);
        for (double[] s : STACKS) {
            double d = Math.hypot(x - s[0], z - s[1]) + w.fbm(x * 1.6 + s[0], z * 1.6, 3, 2) * 0.8;
            if (d < s[2]) h = Math.max(h, s[3]);
        }
        // Saddle Rock's ridge, falling half a block a block to the islet
        double[] sr = STACKS[0];
        double rl = Math.hypot(RIDGE[0], RIDGE[1]), ux = RIDGE[0] / rl, uz = RIDGE[1] / rl;
        double t = (x - sr[0]) * ux + (z - sr[1]) * uz, o = Math.abs(-(x - sr[0]) * uz + (z - sr[1]) * ux);
        if (t > 0 && t < 20 && o < 3.2 + w.fbm(x * 1.5, z * 1.5, 4, 1) * 0.6) h = Math.max(h, Math.min(STACK, STACK - (t - sr[2]) * 0.5));
        if (supere(x, z, 10, 10, 3) > 0) h = Math.max(h, TWIN);
        for (double[] b : BARS) {
            double d = Math.hypot(x - b[0], z - b[1]) + w.fbm(x * 1.2, z * 1.2 - b[0], 4, 2) * 1.2;
            if (d < b[2]) h = Math.max(h, d > b[2] - 1.6 ? WL : SAND);
        }
        double wb = Math.hypot((x - WX) / 16.0, (z - WZ) / 8.0) + w.fbm(x * 1.2, z * 1.2, 4, 1) * 0.08;
        if (wb < 1) h = Math.max(h, wb > 0.84 ? WL : SAND);
        return h;
    }

    static final String[] BANDS = {"tuff", "andesite", "brown_terracotta", "tuff", "calcite", "deepslate", "andesite", "granite", "tuff", "white_terracotta"};

    /** Santorini cliffs: dark volcanic rock in bands, pale pumice and rust between. */
    String rock(int x, int y, int z) {
        double warp = w.fbm(x * 0.5 + 11, z * 0.5 - 7, 24, 2) * 1.5;
        int i = Math.floorMod((int) Math.floor((y + warp) / 2.3), BANDS.length);
        return w.n3(x * 2.1, y * 2.1, z * 2.1, 4) > 0.58 ? "smooth_basalt" : BANDS[i];
    }

    String surface(int x, int z, int y, int steep, double edge) {
        double n = w.fbm(x + 913, z - 377, 7, 2), f = w.n2(x * 1.7, z * 1.7, 3);
        if (y < WL) return n > 0.35 ? "gravel" : n < -0.4 ? "clay" : f > 0.55 ? "sandstone" : "sand";
        if (y <= SAND) return "sand";
        double hl = headland(x, z);
        if (hl == QUAY) return f > 0.3 ? "stone_bricks" : "smooth_stone";
        if (!Double.isNaN(hl)) return f > 0.4 ? "white_terracotta" : n > 0.45 ? "smooth_quartz" : "calcite";
        if (steep >= 3) return rock(x, y, z);
        if (y < 67) return n > 0.1 ? "sand" : n < -0.45 ? "coarse_dirt" : "grass_block";
        return n > 0.25 ? "moss_block" : n < -0.4 ? "stone" : "grass_block";
    }

    int bottom(int x, int z, int top, double edge) {
        double lens = 1 - Math.pow(1 - Math.min(1, edge / 26.0), 1.7);
        double b = top - 5 - 40 * lens + w.fbm(x * 0.9 + 5, z * 0.9 - 5, 14, 2) * 4 * Math.min(1, edge / 8.0);
        double cone = w.fbm(x * 1.6 + 300, z * 1.6 - 200, 8, 2);
        if (cone > 0.34 && edge > 5) b -= (cone - 0.34) * 45 * Math.min(1, edge / 14.0);
        return Math.max(low + 3, (int) Math.round(Math.min(b, top - 3)));
    }

    // ================================================================ the map

    @Override
    protected void build() {
        distances();
        land = new Land(w, t, X0, X1, Z0, Z1);
        land.top = this::height;
        land.surface = this::surface;
        land.rock = this::rock;
        land.bottom = this::bottom;
        land.soil = P.of("sand", 2, "sandstone", 1, "dirt", 1);
        land.water = (x, z) -> WL;
        land.build();
        halfSteps();

        plaza();
        chapel();
        terraces();
        lift();
        quay();
        brinkWalls();
        twinLights();
        stacks();
        wreck();
        ruins();
        seaLife();
        falls();
        trees(new Theme.Tree[] {Theme.Tree.PALM}, new int[] {1}, 0.035, 6, 2, (x, z) -> {
            int g = land(x, z);
            return g >= SAND && g <= 67 && Double.isNaN(headland(x, z)) && !onFoot(x, z);
        });
        plants(P.of("short_grass", 8, "fern", 2, "dandelion", 1, "bush", 2, "short_dry_grass", 2), 0.18,
                (x, z) -> land(x, z) > SAND && Double.isNaN(headland(x, z)) && !onFoot(x, z));

        spawn = new double[] {FX - 3.5, TOP + 1, 0.5, -90};
        flag = new int[] {FLAG_X, TOP + 1, 0};
        pads.add(new int[] {(int) FX + 7, TOP + 1, -7});
        pads.add(new int[] {(int) FX + 7, TOP + 1, 7});
        hills.add(new Hill("North Wreck", WX - 3, SAND + 4, WZ - 3, WX + 3, SAND + 7, WZ + 3));
        hills.add(new Hill("Twin Lights", -3, TWIN + 1, -3, 3, TWIN + 4, 3));
        hills.add(new Hill("South Wreck", -WX - 3, SAND + 4, -WZ - 3, -WX + 3, SAND + 7, -WZ + 3));
    }

    int land(int x, int z) {
        return land.topAt(x, z);
    }

    boolean onFoot(int x, int z) {
        for (int[] f : foot) if (x >= f[0] && x <= f[2] && z >= f[1] && z <= f[3]) return true;
        return false;
    }

    /** The easternmost cell of row z standing at y or higher: the brink of the headland's level y. */
    int brink(int z, int y) {
        for (int x = (int) FX + 42; x >= (int) FX - 16; x--) {
            int g = land(x, z);
            if (g != Integer.MIN_VALUE && g >= y) return x;
        }
        return Integer.MIN_VALUE;
    }

    /**
     * Half steps on the natural ground (the beaches, the sandbars, Saddle Rock's ridge): a bottom slab on every
     * square a block below its neighbour, where that keeps the walk to half a block, so nothing needs a jump.
     */
    void halfSteps() {
        int sx = X1 - X0 + 1, sz = Z1 - Z0 + 1;
        int[][] top = new int[sx][sz];
        double[][] walk = new double[sx][sz];
        for (int x = X0; x <= X1; x++) for (int z = Z0; z <= Z1; z++) {
            int t = land(x, z);
            top[x - X0][z - Z0] = t;
            walk[x - X0][z - Z0] = t + 1;
        }
        for (int pass = 0; pass < 8; pass++) {
            boolean changed = false;
            for (int i = 1; i < sx - 1; i++) for (int j = 1; j < sz - 1; j++) {
                int t = top[i][j];
                if (t == Integer.MIN_VALUE || t < WL) continue;
                for (int[] d : DIRS) {
                    if (top[i + d[0]][j + d[1]] != t + 1) continue;
                    double want = walk[i + d[0]][j + d[1]] - 0.5;
                    if (want > walk[i][j] && want <= t + 1.5) {
                        walk[i][j] = want;
                        changed = true;
                    }
                }
            }
            if (!changed) break;
        }
        for (int x = X0 + 1; x < X1; x++) for (int z = Z0 + 1; z < Z1; z++) {
            if (!w.canon(x, z) || !Double.isNaN(headland(x, z))) continue;
            int i = x - X0, j = z - Z0, t = top[i][j];
            if (t == Integer.MIN_VALUE || t < WL || walk[i][j] - t < 1.25) continue;
            String n = w.type(x, t, z).name();
            String slab = n.contains("SAND") ? "smooth_sandstone_slab"
                    : n.contains("GRASS") || n.contains("MOSS") || n.contains("DIRT") ? "mossy_cobblestone_slab" : "tuff_slab";
            w.set(x, t + 1, z, slab + "[type=bottom]");
        }
    }

    // ================================================================ the village

    /** The plaza on top of the headland: whitewashed paving, a rosette round the flag at the head of the great stair. */
    void plaza() {
        int y = TOP;
        for (int x = (int) FX - 15; x <= (int) FX + 15; x++) for (int z = -15; z <= 15; z++) {
            if (headland(x, z) != TOP) continue;
            int dx = x - FLAG_X;
            double d = Math.hypot(dx, z);
            String s;
            if (d < 1.6) s = "chiseled_quartz_block";
            else if (d < 3.4) s = (((int) Math.floor((Math.toDegrees(Math.atan2(z, dx)) + 180) / 22.5)) & 1) == 0 ? "TEAM_glazed_terracotta" : "white_glazed_terracotta";
            else if (d < 4.3) s = "quartz_bricks";
            else s = Math.floorMod(x + z, 6) == 0 ? "white_terracotta" : Math.floorMod(x * 2 + z, 5) == 0 ? "smooth_quartz" : "calcite";
            w.set(x, y, z, s);
        }
        // planters of flowering azalea, palms behind the chapel
        for (int[] p : new int[][] {{(int) FX + 9, -4}, {(int) FX + 9, 4}, {(int) FX - 1, -10}, {(int) FX - 6, 11}}) {
            w.set(p[0], y, p[1], "moss_block");
            w.set(p[0], y + 1, p[1], "flowering_azalea");
        }
        for (int s : new int[] {-1, 1}) {
            int px = (int) FX - 10, pz = s * 6;
            w.set(px, y, pz, "moss_block");
            flora.tree(Theme.Tree.PALM, px, y + 1, pz);
        }
        // a diving board off each side, over the bay
        for (int s : new int[] {-1, 1}) {
            int bx = (int) FX, edge = s * 14;
            for (int i = 1; i <= 3; i++) w.set(bx, y, edge + s * i, i == 3 ? "spruce_slab[type=top]" : "spruce_planks");
            w.set(bx, y - 1, edge + s, "spruce_stairs[facing=" + (s < 0 ? "south" : "north") + ",half=top]");
            foot.add(new int[] {bx, Math.min(edge, edge + s * 3), bx, Math.max(edge, edge + s * 3)});
        }
        keepClear((int) FX - 16, -16, (int) FX + 16, 16);
    }

    /** The chapel (the spawn): a white nave under a great dome of the team's colour, a bell tower beside it. */
    void chapel() {
        int y = TOP, x0 = (int) FX - 7, x1 = (int) FX + 2, z0 = -5, z1 = 5, h = 7;
        k.walls(x0, z0, x1, z1, y, h, Kit.Bands.of(0, "smooth_quartz", 1, "white_concrete", 7, "smooth_quartz"), "quartz_pillar", 3, P.of("polished_diorite", 2, "smooth_quartz", 1));
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) w.set(x, y + h + 1, z, "smooth_quartz");
        int cx = (x0 + x1) / 2;
        k.dome(cx, 0, y + h + 2, 4.2, DOME, null);
        // the door east onto the plaza, arched; tall windows of the team's glass
        for (int z = -1; z <= 1; z++) for (int dy = 1; dy <= 4; dy++) w.set(x1, y + dy, z, "air");
        w.set(x1, y + 4, -1, "quartz_stairs[facing=south,half=top]");
        w.set(x1, y + 4, 1, "quartz_stairs[facing=north,half=top]");
        for (int x = x0 + 2; x <= x1 - 2; x += 3) for (int z : new int[] {z0, z1}) for (int dy = 2; dy <= 5; dy++) w.set(x, y + dy, z, "TEAM_stained_glass_pane");
        // inside: pews facing the altar, a runner, candles, banners, two chandeliers
        for (int x = x0 + 3; x <= x1 - 2; x += 2) for (int z : new int[] {-3, -2, 2, 3}) w.set(x, y + 1, z, "birch_stairs[facing=east]");
        for (int x = x0 + 2; x <= x1 - 1; x++) w.set(x, y + 1, 0, "TEAM_carpet");
        for (int z = -2; z <= 2; z++) w.set(x0 + 1, y + 1, z, z == 0 ? "chiseled_quartz_block" : "smooth_quartz_slab[type=bottom]");
        w.set(x0 + 1, y + 2, 0, "candle[candles=4,lit=true]");
        for (int s : new int[] {-2, 2}) w.set(x0 + 1, y + 4, s, "TEAM_wall_banner[facing=east]");
        w.hangLight(x0 + 3, y + 6, 0, "lantern");
        w.hangLight(x1 - 3, y + 6, 0, "lantern");
        // the bell tower: open arches at the top, a bell, a small dome
        int bx = (int) FX - 3, bz = 9;
        for (int dy = 1; dy <= 12; dy++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            boolean corner = Math.abs(dx) == 1 && Math.abs(dz) == 1;
            boolean arch = dy >= 9 && dy <= 11 && !corner;
            w.set(bx + dx, y + dy, bz + dz, arch ? "air" : dy == 8 || dy == 12 ? "smooth_quartz" : "white_concrete");
        }
        w.set(bx, y + 11, bz, "bell[attachment=ceiling,facing=east]");
        k.dome(bx, bz, y + 13, 1.6, DOME, null);
        keepClear(x0 - 2, z0 - 2, x1 + 2, z1 + 2);
    }

    /**
     * The terraces: the great stair down the middle in three flights to the harbour, side stairs that zigzag down
     * either flank, houses along the outer edge of each terrace with their backs to the sea, windmills at the ends.
     */
    void terraces() {
        int last = 0;
        for (int[] f : new int[][] {{TOP, T2}, {T2, T3}, {T3, QUAY}}) last = flight(-2, 2, f[0], f[1]);
        for (int s : new int[] {-1, 1}) {
            flight(s < 0 ? -12 : 11, s < 0 ? -11 : 12, TOP, T2);
            flight(s < 0 ? -16 : 15, s < 0 ? -15 : 16, T2, T3);
            flight(s < 0 ? -21 : 20, s < 0 ? -20 : 21, T3, QUAY);
        }
        // houses on the outer edges, between the stairs
        int[][] slots = {{T2, -8, 0}, {T2, 4, 1}, {T3, -13, 1}, {T3, -8, 0}, {T3, 4, 1}, {T3, 9, 0}};
        for (int[] sl : slots) houseOnEdge(sl[1], sl[0], sl[2] == 1);
        // the lower terrace narrows where stair B comes down: keep the way on from its landing free of the wall
        foot.add(new int[] {-74, 18, -74, 18});
        foot.add(new int[] {-74, -18, -74, -18});
        // windmills at the ends of the lower terrace
        for (int s : new int[] {-1, 1}) windmill((int) Math.round(FX + 13.75), s * 24, T3);
        keepClear((int) FX - 16, -36, (int) FX + 40, 36);
    }

    /**
     * A flight of whitewashed steps from the brink of the level at {@code hi} east down onto the one at {@code lo},
     * over rows z0..z1, walled both sides. Where the steps are high enough the walk along the terrace below passes
     * under them. Returns the x of the last step.
     */
    int flight(int z0, int z1, int hi, int lo) {
        int start = Integer.MIN_VALUE;
        for (int z = z0; z <= z1; z++) start = Math.max(start, brink(z, hi));
        // square the top off so every row starts together
        for (int z = z0 - 1; z <= z1 + 1; z++) {
            for (int x = brink(z, hi) + 1; x <= start; x++) {
                for (int yy = Math.max(land(x, z) + 1, lo); yy < hi; yy++) w.set(x, yy, z, rock(x, yy, z));
                w.set(x, hi, z, "smooth_quartz");
            }
        }
        int n = hi - lo;
        for (int i = 1; i <= n; i++) {
            int x = start + i, sy = hi + 1 - i;
            boolean arch = sy >= lo + 4;
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                boolean side = z == z0 - 1 || z == z1 + 1;
                int from = arch ? lo + 4 : lo + 1;
                for (int yy = lo + 1; yy < from; yy++) w.set(x, yy, z, "air");
                for (int yy = from; yy < sy; yy++) w.set(x, yy, z, side ? "white_concrete" : "smooth_quartz");
                if (side) {
                    boolean post = i == 1 || i == n;
                    w.set(x, sy, z, "white_concrete");
                    w.set(x, sy + 1, z, post ? "smooth_quartz" : "diorite_wall");
                    w.set(x, sy + 2, z, post ? "lantern" : "air");
                    for (int dy = 3; dy <= 4; dy++) w.set(x, sy + dy, z, "air");
                } else {
                    w.set(x, sy, z, "smooth_quartz_stairs[facing=west,half=bottom]");
                    for (int dy = 1; dy <= 4; dy++) w.set(x, sy + dy, z, "air");
                }
            }
        }
        foot.add(new int[] {start - 2, z0 - 1, start + n + 1, z1 + 1});
        return start + n;
    }

    /** A house on the outer edge of the terrace at y over rows z0..z0+4: its back on the brink, its door on the walk. */
    void houseOnEdge(int z0, int y, boolean dome) {
        int east = Integer.MAX_VALUE;
        for (int z = z0; z <= z0 + 4; z++) east = Math.min(east, brink(z, y));
        house(east - 3, z0, east, z0 + 4, y, dome);
        foot.add(new int[] {east - 3, z0, east + 1, z0 + 4});
    }

    /** A cube house, whitewashed, its door and windows in the team's colour, a dome or a vine-hung roof terrace on top. */
    void house(int x0, int z0, int x1, int z1, int y, boolean dome) {
        int h = 4, cz = (z0 + z1) / 2;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
            for (int yy = y - 3; yy < y; yy++) if (!w.type(x, yy, z).isSolid()) w.set(x, yy, z, rock(x, yy, z));
            w.set(x, y, z, edge ? "calcite" : "smooth_quartz");
            for (int dy = 1; dy <= h; dy++) w.set(x, y + dy, z, edge ? (dy == h ? "smooth_quartz" : "white_concrete") : "air");
            w.set(x, y + h + 1, z, edge ? "smooth_quartz_slab[type=bottom]" : "white_concrete");
        }
        // the door on the walk side (west), windows either side; the team's banner over it
        w.set(x0, y + 1, cz, "spruce_door[facing=east,half=lower,hinge=left,open=false]");
        w.set(x0, y + 2, cz, "spruce_door[facing=east,half=upper,hinge=left,open=false]");
        w.set(x0 - 1, y + 3, cz, "TEAM_wall_banner[facing=west]");
        for (int z : new int[] {cz - 1, cz + 1}) w.set(x0, y + 2, z, "TEAM_stained_glass_pane");
        // the sea side: two windows with shutters, the side walls one each
        for (int z : new int[] {z0 + 1, z1 - 1}) w.set(x1, y + 2, z, "TEAM_stained_glass_pane");
        w.set(x0 + 2, y + 2, z0, "TEAM_stained_glass_pane");
        w.set(x0 + 2, y + 2, z1, "TEAM_stained_glass_pane");
        // inside: a lamp, a barrel, a rug
        w.hangLight(x0 + 2, y + 4, cz, "lantern");
        w.set(x1 - 1, y + 1, z0 + 1, "barrel[facing=up]");
        w.set(x0 + 2, y + 1, cz, "TEAM_carpet");
        if (dome) {
            k.dome(x0 + 2, cz, y + h + 2, 2.2, DOME, null);
        } else {
            // a roof terrace: a pergola of dark oak hung with flowering azalea
            for (int z : new int[] {z0, z1}) for (int x : new int[] {x0, x1}) w.set(x, y + h + 2, z, "dark_oak_fence");
            for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
                if ((x == x0 || x == x1) && (z == z0 || z == z1)) w.set(x, y + h + 3, z, "dark_oak_slab[type=bottom]");
                else if ((x + z & 1) == 0) w.set(x, y + h + 3, z, "flowering_azalea_leaves[persistent=true]");
            }
        }
    }

    /** A Mykonos windmill: a round white tower, a thatched cap, four sails of canvas on a hub facing the sea. */
    void windmill(int cx, int cz, int y) {
        for (int dy = 0; dy <= 9; dy++) for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            double d = Math.hypot(dx, dz);
            if (d > 2.6) continue;
            if (dy == 0) { w.set(cx + dx, y, cz + dz, "smooth_quartz"); continue; }
            w.set(cx + dx, y + dy, cz + dz, d > 1.6 ? (dy == 1 ? "smooth_quartz" : "white_concrete") : "air");
        }
        w.set(cx - 2, y + 1, cz, "spruce_door[facing=east,half=lower,hinge=left,open=false]");
        w.set(cx - 2, y + 2, cz, "spruce_door[facing=east,half=upper,hinge=left,open=false]");
        w.set(cx, y + 4, cz - 2, "TEAM_stained_glass_pane");
        w.set(cx, y + 4, cz + 2, "TEAM_stained_glass_pane");
        k.cone(cx, cz, y + 10, 2, 1, 1.2, new Kit.Roof("dark_oak_stairs", "dark_oak_slab", "hay_block"));
        int hy = y + 8, hx = cx + 3;
        w.set(cx + 2, hy, cz, "stripped_dark_oak_log[axis=x]");
        w.set(hx, hy, cz, "stripped_dark_oak_wood");
        for (int[] arm : new int[][] {{0, 1}, {0, -1}, {1, 0}, {-1, 0}}) {
            for (int i = 1; i <= 5; i++) {
                int z = cz + arm[0] * i, yy = hy + arm[1] * i;
                w.set(hx, yy, z, "dark_oak_fence");
                if (i >= 2) w.set(hx + 1, yy + (arm[0] != 0 ? 1 : 0), z + (arm[1] != 0 ? 1 : 0), "white_wool");
            }
        }
        foot.add(new int[] {cx - 3, cz - 3, cx + 3, cz + 3});
    }

    /** The bubble lift: a sea cave from the harbour into the rock, a column of water that shoots you up to the plaza. */
    void lift() {
        int y = QUAY, mouth = brink(LIFT_Z, T3);
        // the cave: in from the cliff at the back of the quay, two wide, three high, beams across the roof
        for (int x = LIFT_X + 1; x <= mouth; x++) for (int z = LIFT_Z - 1; z <= LIFT_Z; z++) {
            w.set(x, y, z, (x & 1) == 0 ? "stone_bricks" : "cracked_stone_bricks");
            for (int dy = 1; dy <= 3; dy++) w.set(x, y + dy, z, "air");
            w.set(x, y + 4, z, Math.floorMod(x, 4) == 0 ? "stripped_spruce_log[axis=z]" : rock(x, y + 4, z));
        }
        for (int x = LIFT_X + 4; x < mouth; x += 5) w.hangLight(x, y + 3, LIFT_Z - 1, "lantern");
        for (int z = LIFT_Z - 1; z <= LIFT_Z; z++) w.set(mouth, y + 4, z, "smooth_quartz");
        int sz = LIFT_Z - 2, sx = brink(sz, T3) + 1;
        w.set(sx, y + 2, sz, "birch_wall_sign[facing=east]");
        w.signText(sx, y + 2, sz, true, "", "LIFT", "to the plaza", "");
        // the shaft: soul sand under a column of bubbles to the plaza, sealed all round but for the way in at its foot
        w.set(LIFT_X, y - 1, LIFT_Z, "soul_sand");
        for (int yy = y; yy <= TOP; yy++) {
            for (int[] d : DIRS) {
                int nx = LIFT_X + d[0], nz = LIFT_Z + d[1];
                if (yy == TOP || nx == LIFT_X + 1 && nz == LIFT_Z && yy > y && yy <= y + 2) continue;
                w.set(nx, yy, nz, yy > y + 3 ? rock(nx, yy, nz) : "stone_bricks");
            }
            w.set(LIFT_X, yy, LIFT_Z, "bubble_column[drag=false]");
        }
        // the way in: two signs on the side of the opening hold the water back, and you walk through them
        w.set(LIFT_X + 1, y + 1, LIFT_Z, "birch_wall_sign[facing=north]");
        w.signText(LIFT_X + 1, y + 1, LIFT_Z, true, "", "BUBBLE LIFT", "walk in", "");
        w.set(LIFT_X + 1, y + 2, LIFT_Z, "birch_wall_sign[facing=north]");
        w.signText(LIFT_X + 1, y + 2, LIFT_Z, true, "", "^ ^ ^", "to the plaza", "");
        // the kiosk over the top: four white posts and a slab roof, open all round
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            int x = LIFT_X + dx, z = LIFT_Z + dz;
            boolean corner = dx != 0 && dz != 0, centre = dx == 0 && dz == 0;
            if (!centre) w.set(x, TOP, z, corner ? "chiseled_quartz_block" : "quartz_bricks");
            if (corner) for (int dy = 1; dy <= 3; dy++) w.set(x, TOP + dy, z, "quartz_pillar");
            w.set(x, TOP + 4, z, centre ? "TEAM_stained_glass" : "smooth_quartz_slab[type=bottom]");
        }
        foot.add(new int[] {LIFT_X - 1, LIFT_Z - 1, LIFT_X + 1, LIFT_Z + 1});
    }

    /** The harbour: bollards and lamps along the quay's edge, steps down into the water, barrels, a sailboat. */
    void quay() {
        waterSteps(-2, 2);
        waterSteps(-21, -20);
        waterSteps(20, 21);
        for (int x = (int) FX + 20; x <= (int) FX + 40; x++) for (int z = -34; z <= 34; z++) {
            if (headland(x, z) != QUAY || onFoot(x, z) || !w.air(x, QUAY + 1, z)) continue;
            boolean edge = false;
            for (int[] d : DIRS) if (land(x + d[0], z + d[1]) < WL) edge = true;
            if (!edge) continue;
            int m = Math.floorMod(z * 7 + x, 9);
            if (m == 0) w.set(x, QUAY + 1, z, "andesite_wall");
            else if (m == 4) {
                w.set(x, QUAY + 1, z, "spruce_fence");
                w.set(x, QUAY + 2, z, "lantern");
            }
        }
        // barrels and crates at the foot of the cliff
        for (int z : new int[] {-14, -13, 13, 25, 26, -26}) {
            int x = brink(z, T3) + 1;
            if (headland(x, z) != QUAY || !w.air(x, QUAY + 1, z)) continue;
            w.set(x, QUAY + 1, z, (z & 1) == 0 ? "barrel[facing=up]" : "spruce_planks");
            if (z == -14 || z == 25) w.set(x, QUAY + 2, z, "barrel[facing=up]");
        }
        sailboat(-58, -18, true);
        sailboat(-63, 27, false);
        for (int[] b : new int[][] {{-57, 3}, {-57, 9}}) buoy(b[0], b[1]);
    }

    /** A buoy marking the channel: a red-and-white float on a chain to the sea floor, a lamp on top. */
    void buoy(int x, int z) {
        int g = land(x, z);
        for (int yy = g + 1; yy < WL; yy++) w.set(x, yy, z, "iron_chain[axis=y]");
        w.set(x, WL, z, "red_wool");
        w.set(x, WL + 1, z, "white_wool");
        w.set(x, WL + 2, z, "lantern");
    }

    /** Stone steps from the quay's edge down into the water over rows z0..z1. */
    void waterSteps(int z0, int z1) {
        int start = Integer.MIN_VALUE;
        for (int z = z0; z <= z1; z++) start = Math.max(start, brink(z, QUAY));
        for (int z = z0; z <= z1; z++) for (int x = brink(z, QUAY) + 1; x <= start; x++) {
            for (int yy = land(x, z) + 1; yy <= QUAY; yy++) w.set(x, yy, z, "stone_bricks");
            for (int dy = 1; dy <= 3; dy++) w.set(x, QUAY + dy, z, "air");
        }
        for (int i = 1; i <= 4; i++) {
            int x = start + i, sy = QUAY + 1 - i;
            for (int z = z0; z <= z1; z++) {
                for (int yy = land(x, z) + 1; yy < sy; yy++) w.set(x, yy, z, "stone_bricks");
                w.set(x, sy, z, "stone_brick_stairs[facing=west,half=bottom" + (sy <= WL ? ",waterlogged=true" : "") + "]");
                for (int dy = 1; dy <= 3; dy++) w.set(x, sy + dy, z, sy + dy <= WL ? "water" : "air");
            }
        }
        foot.add(new int[] {start - 1, z0 - 1, start + 4, z1 + 1});
    }

    /** A small sailboat moored in the harbour: a plank hull on the water, a mast and a furled sail. */
    void sailboat(int cx, int cz, boolean mast) {
        for (int i = -4; i <= 4; i++) {
            int half = Math.abs(i) >= 4 ? 0 : 1;
            for (int s = -half; s <= half; s++) {
                boolean side = Math.abs(s) == half && half > 0 || Math.abs(i) == 4;
                w.set(cx + i, WL, cz + s, side ? "spruce_planks" : "spruce_slab[type=top]");
                if (side) w.set(cx + i, WL + 1, cz + s, "spruce_trapdoor[facing=" + (s < 0 ? "south" : s > 0 ? "north" : i < 0 ? "east" : "west") + ",half=bottom,open=true]");
                else w.set(cx + i, WL + 1, cz + s, "air");
            }
        }
        if (!mast) {
            w.set(cx - 1, WL + 1, cz, "spruce_slab[type=bottom]");
            w.set(cx + 1, WL + 1, cz, "barrel[facing=up]");
            return;
        }
        for (int dy = 1; dy <= 7; dy++) w.set(cx, WL + dy, cz, "stripped_spruce_log[axis=y]");
        for (int i = -3; i <= 2; i++) if (i != 0) w.set(cx + i, WL + 4, cz, "white_wool");
        w.set(cx, WL + 8, cz, "TEAM_banner[rotation=4]");
    }

    /**
     * A low white wall along the brinks of the plaza and the terraces, a lamp now and then. The plaza is walled
     * all round but for its stairs and diving boards; the terraces only where the drop is onto rock, so their ends
     * over the bays stay open for diving.
     */
    void brinkWalls() {
        for (int x = (int) FX - 16; x <= (int) FX + 40; x++) for (int z = -40; z <= 40; z++) {
            double hl = headland(x, z);
            if (Double.isNaN(hl) || hl == QUAY) continue;
            int g = (int) hl;
            if (land(x, z) != g || onFoot(x, z) || !w.air(x, g + 1, z)) continue;
            boolean drop = false, wet = true;
            for (int[] d : DIRS) {
                int n = land(x + d[0], z + d[1]);
                if (n != Integer.MIN_VALUE && n >= g - 2) continue;
                drop = true;
                if (n == Integer.MIN_VALUE || n >= WL) wet = false;
            }
            if (!drop || wet && g != TOP) continue;
            boolean post = Math.floorMod(x * 3 + z * 5, 9) == 0;
            w.set(x, g + 1, z, post ? "smooth_quartz" : "diorite_wall");
            if (post) w.set(x, g + 2, z, "lantern");
        }
    }

    // ================================================================ the middle

    /** The Twin Lights: two lighthouses on the middle stack, the hill between them, a stair up each face from the water. */
    void twinLights() {
        for (int x = -10; x <= 0; x++) for (int z = -10; z <= 10; z++) {
            if (!w.canon(x, z) || land(x, z) != TWIN) continue;
            double d = Math.hypot(x, z);
            c(x, TWIN, z, d < 1.6 ? "chiseled_stone_bricks" : d < 4.6 ? (((x + z) & 1) == 0 ? "stone_bricks" : "polished_andesite") : d < 5.6 ? "calcite" : "stone_bricks");
        }
        lighthouse(0, -7, TWIN);
        // the stair up the west face: from a ledge at the water in the south-west, north along the face to a landing
        // at the top in the north-west (its copy climbs the east face)
        for (int z = 11; z >= -7; z--) {
            int i = 9 - z, top = i < 1 ? SAND : i > TWIN - SAND ? TWIN : SAND + i;
            int face = -1;
            while (face > -14 && land(face - 1, z) >= TWIN) face--;
            int east = Math.min(face - 1, -8);
            for (int x = -12; x <= east; x++) {
                int g = land(x, z);
                for (int yy = Math.max(g + 1, 40); yy < top; yy++) w.set(x, yy, z, rock(x, yy, z));
                boolean step = i >= 1 && i <= TWIN - SAND;
                w.set(x, top, z, step ? "stone_brick_stairs[facing=north,half=bottom]" : "stone_bricks");
                for (int dy = 1; dy <= 3; dy++) w.set(x, top + dy, z, "air");
            }
        }
        keepClear(-15, -13, 15, 13);
    }

    /** A lighthouse: a round tower banded white and the side's colour, a gallery with a rail, a glass lantern room, a copper dome. */
    void lighthouse(int cx, int cz, int y) {
        for (int dy = 1; dy <= 14; dy++) for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            double d = Math.hypot(dx, dz);
            if (d > 2.7 - dy * 0.03) continue;
            w.set(cx + dx, y + dy, cz + dz, ((dy - 1) / 3) % 2 == 0 ? "white_concrete" : "{red|blue}_concrete");
        }
        int g = y + 15;
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            double d = Math.hypot(dx, dz);
            if (d > 3.8) continue;
            w.set(cx + dx, g, cz + dz, d > 2.9 ? "smooth_stone_slab[type=top]" : "smooth_stone");
            if (d > 2.9) w.set(cx + dx, g + 1, cz + dz, "iron_bars");
        }
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            double d = Math.hypot(dx, dz);
            if (d > 2.3) continue;
            for (int dy = 1; dy <= 3; dy++) w.set(cx + dx, g + dy, cz + dz, d > 1.4 ? "glass_pane" : dy == 2 && d < 0.5 ? "sea_lantern" : "air");
        }
        w.set(cx, g + 1, cz, "polished_andesite");
        k.dome(cx, cz, g + 4, 2.4, new Kit.Roof("waxed_oxidized_cut_copper_stairs", "waxed_oxidized_cut_copper_slab", "waxed_oxidized_cut_copper"), null);
    }

    /** The stacks' furniture: the Needle's ladder and nest, the broken arch, skerries out in the lanes. */
    void stacks() {
        // the Needle: a ladder up its south face from a ledge at the water to a nest on top
        double[] n = STACKS[3];
        int nx = (int) Math.round(n[0]), nz = (int) Math.round(n[1]), top = (int) n[3];
        int ladZ = nz + 4;
        for (int x = nx - 1; x <= nx + 1; x++) for (int z = ladZ; z <= ladZ + 1; z++) {
            for (int yy = Math.max(land(x, z) + 1, 40); yy <= SAND; yy++) w.set(x, yy, z, yy == SAND ? "stone_bricks" : rock(x, yy, z));
            for (int dy = 1; dy <= 3; dy++) w.set(x, SAND + dy, z, "air");
        }
        for (int yy = SAND - 4; yy <= top; yy++) if (!w.type(nx, yy, ladZ - 1).isSolid()) w.set(nx, yy, ladZ - 1, rock(nx, yy, ladZ - 1));
        for (int yy = SAND + 1; yy <= top; yy++) w.set(nx, yy, ladZ, "ladder[facing=south]");
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            int x = nx + dx, z = nz + dz;
            boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
            w.set(x, top, z, "spruce_planks");
            for (int dy = 1; dy <= 3; dy++) w.set(x, top + dy, z, "air");
            if (edge && !(dx == 0 && dz == 2)) w.set(x, top + 1, z, "spruce_fence");
        }
        w.set(nx, top, nz + 3, "spruce_planks");
        w.set(nx + 1, top + 1, nz - 1, "barrel[facing=up]");
        // the natural arch from Arch Stack A to B, fallen in the middle: two stubs reaching for each other
        double[] a = STACKS[1], b = STACKS[2];
        double len = Math.hypot(b[0] - a[0], b[1] - a[1]), ux = (b[0] - a[0]) / len, uz = (b[1] - a[1]) / len;
        double s0 = a[2] - 1.5, s1 = len - b[2] + 1.5;
        for (double s = s0; s <= s1; s += 0.3) {
            double t = (s - s0) / (s1 - s0), lift = Math.sin(Math.PI * t);
            if (t > 0.26 && t < 0.74) continue;
            double ragged = Math.min(Math.abs(t - 0.26), Math.abs(t - 0.74));
            for (double o = -2.4; o <= 2.4; o += 0.4) {
                if (ragged < 0.06 && Math.abs(o) > 1.2 + w.n2(s * 3, o * 3, 1)) continue;
                int x = (int) Math.round(a[0] + ux * s - uz * o), z = (int) Math.round(a[1] + uz * s + ux * o);
                int under = (int) Math.round(STACK - 2 - 4 * (1 - lift) - (Math.abs(o) > 1.7 ? 1 : 0));
                for (int yy = under; yy <= STACK; yy++) w.set(x, yy, z, yy == STACK ? (Math.abs(o) < 1.8 ? "grass_block" : "moss_block") : rock(x, yy, z));
                for (int dy = 1; dy <= 3; dy++) if (w.type(x, STACK + dy, z).isSolid()) w.set(x, STACK + dy, z, "air");
            }
        }
        keepClear(nx - 3, nz - 3, nx + 3, nz + 6);
        // skerries: rocks just breaking the surface out in the lanes, a lamp on the biggest
        double[][] sk = {{-66, -42, 2.4}, {-6, -47, 1.8}, {-80, 41, 2.2}, {-40, 45, 2.0}, {-24, 47, 1.5}};
        for (double[] k2 : sk) {
            int best = 0;
            for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
                int x = (int) k2[0] + dx, z = (int) k2[1] + dz;
                double d = Math.hypot(dx, dz) + w.fbm(x * 2.0, z * 2.0, 2, 1) * 0.7;
                int g = land(x, z);
                if (d > k2[2] || g == Integer.MIN_VALUE || g >= WL) continue;
                int peak = WL + 1 + (int) Math.round((k2[2] - d) * 1.4);
                best = Math.max(best, peak);
                for (int yy = g + 1; yy <= peak; yy++) w.set(x, yy, z, yy == peak && peak > WL + 1 ? "moss_block" : rock(x, yy, z));
            }
            if (k2[2] > 2.3) w.set((int) k2[0], best + 1, (int) k2[1], "lantern");
            keepClear((int) k2[0] - 3, (int) k2[1] - 3, (int) k2[0] + 3, (int) k2[1] + 3);
        }
    }

    // ================================================================ at sea level

    /**
     * The beached galleon: a hull on the sandbar, a flush main deck (the hill), the forecastle and the captain's
     * cabin raised at either end, the foremast standing with a torn sail, the mainmast a stump, the mizzen lying on
     * the sand; a gangplank down each side and a ladder over the bow.
     */
    void wreck() {
        int deck = SAND + 3, keel = SAND;
        for (int i = -LEN; i <= LEN; i++) {
            int half = halfBeam(i);
            for (int s = -half; s <= half; s++) {
                int x = WX + i, z = WZ + s;
                boolean side = Math.abs(s) == half || Math.abs(i) == LEN;
                for (int yy = keel - 1; yy <= deck; yy++) {
                    String b = side ? ((i & 1) == 0 && yy < deck ? "stripped_dark_oak_log[axis=y]" : "dark_oak_planks")
                            : yy == deck ? "spruce_planks" : yy <= keel ? "dark_oak_planks" : "air";
                    w.set(x, yy, z, b);
                }
                if (side) w.set(x, deck + 1, z, Math.abs(i) < LEN - 1 && i % 5 != 0 ? "dark_oak_fence" : "dark_oak_planks");
                for (int dy = side ? 2 : 1; dy <= 6; dy++) w.set(x, deck + dy, z, "air");
            }
        }
        // the captain's cabin at the stern (+x), its roof a poop deck with a ladder up its front
        for (int i = 9; i <= LEN - 1; i++) for (int s = -3; s <= 3; s++) {
            if (Math.abs(s) > halfBeam(i)) continue;
            int x = WX + i, z = WZ + s;
            boolean wall = i == 9 || Math.abs(s) == Math.min(3, halfBeam(i)) || i == LEN - 1;
            for (int dy = 1; dy <= 3; dy++) w.set(x, deck + dy, z, wall ? (dy == 2 && (s == 0 || Math.abs(s) == 2) && i != 9 ? "glass_pane" : "dark_oak_planks") : "air");
            w.set(x, deck + 4, z, "spruce_planks");
            if (wall && i != 9) w.set(x, deck + 5, z, "dark_oak_fence");
        }
        w.set(WX + 9, deck + 1, WZ, "dark_oak_door[facing=east,half=lower,hinge=left,open=false]");
        w.set(WX + 9, deck + 2, WZ, "dark_oak_door[facing=east,half=upper,hinge=left,open=false]");
        w.set(WX + 11, deck + 1, WZ - 1, "chest[facing=west]");
        w.hangLight(WX + 11, deck + 3, WZ, "lantern");
        for (int dy = 1; dy <= 4; dy++) w.set(WX + 8, deck + dy, WZ + 2, "ladder[facing=west]");
        // the forecastle at the bow, a step up
        for (int i = -LEN + 1; i <= -9; i++) for (int s = -halfBeam(i) + 1; s <= halfBeam(i) - 1; s++)
            w.set(WX + i, deck + 1, WZ + s, i == -9 ? "spruce_stairs[facing=west,half=bottom]" : "spruce_planks");
        // the masts: the foremast standing with a yard and a torn sail, the mainmast a stump, the mizzen fallen
        int fx = WX - 6;
        for (int dy = 1; dy <= 13; dy++) w.set(fx, deck + dy, WZ, "stripped_spruce_log[axis=y]");
        for (int s = -4; s <= 4; s++) if (s != 0) w.set(fx, deck + 11, WZ + s, "stripped_spruce_log[axis=z]");
        for (int s = -3; s <= 3; s++) for (int dy = 7; dy <= 10; dy++) {
            if (Math.abs(s) == 3 && dy < 9 || Math.floorMod(s * 3 + dy, 5) == 0) continue;
            w.set(fx + 1, deck + dy, WZ + s, "white_wool");
        }
        for (int dy = 1; dy <= 3; dy++) w.set(WX + 5, deck + dy, WZ, "stripped_spruce_log[axis=y]");
        for (int i = 0; i < 8; i++) {
            int x = WX + 4 - i, z = WZ + 6 + (i >= 4 ? 1 : 0), g = land(x, z);
            if (g == SAND) w.set(x, g + 1, z, "stripped_spruce_log[axis=x]");
        }
        // a gangplank down each side, along the hull, to the sand
        for (int s : new int[] {-1, 1}) {
            int ox = WX + 3 * -s, oz = WZ + 4 * s;
            w.set(ox, deck + 1, oz, "air");
            for (int j = 0; j <= 4; j++) {
                int x = ox + s * j, z = oz + s;
                double h = deck + 0.5 - 0.5 * j;
                boolean halfStep = h != Math.floor(h);
                int yy = halfStep ? (int) Math.floor(h) : (int) h - 1;
                for (int y2 = keel; y2 < yy; y2++) w.set(x, y2, z, "spruce_planks");
                w.set(x, yy, z, halfStep ? "spruce_slab[type=bottom]" : "spruce_planks");
                for (int dy = 1; dy <= 3; dy++) w.set(x, yy + dy, z, "air");
            }
        }
        // the ladder over the bow
        int lx = WX - LEN - 1;
        for (int yy = Math.max(land(lx, WZ) + 1, WL); yy <= deck + 1; yy++) w.set(lx, yy, WZ, "ladder[facing=west]");
        // cargo spilled on the sand
        for (int[] p : new int[][] {{-8, 7}, {-6, 7}, {3, -7}, {6, -6}, {9, 6}}) {
            int x = WX + p[0], z = WZ + p[1], g = land(x, z);
            if (g == SAND && w.air(x, g + 1, z)) w.set(x, g + 1, z, (p[0] & 1) == 0 ? "barrel[facing=up]" : "chest[facing=north]");
        }
        keepClear(WX - LEN - 3, WZ - 9, WX + LEN + 3, WZ + 9);
    }

    static int halfBeam(int i) {
        double t = Math.abs(i) / (double) LEN;
        return Math.max(1, (int) Math.round(4 * Math.sqrt(Math.max(0, 1 - t * t * t))));
    }

    /** A sunken colonnade on the sea floor: broken columns and two arches to swim through. */
    void ruins() {
        int cx = -38, cz = -26;
        for (int k2 = 0; k2 < 6; k2++) {
            int x = cx + (k2 % 3) * 5 - 5, z = cz + (k2 / 3) * 7 - 3;
            int g = land(x, z);
            if (g == Integer.MIN_VALUE || g >= WL - 3) continue;
            int h = 2 + (int) Math.round((w.n2(x * 3, z * 3, 2) + 1) * 2);
            for (int dy = 1; dy <= h && g + dy < WL - 1; dy++) w.set(x, g + dy, z, dy == h ? "chiseled_stone_bricks" : (dy & 1) == 0 ? "mossy_stone_bricks" : "stone_bricks");
        }
        for (int[] arch : new int[][] {{cx - 5, cz - 3, cx, cz - 3}, {cx, cz + 4, cx + 5, cz + 4}}) {
            int top = WL - 3;
            for (int x = arch[0]; x <= arch[2]; x++) {
                int g = land(x, arch[1]);
                if (g == Integer.MIN_VALUE || g >= top) continue;
                boolean end = x == arch[0] || x == arch[2];
                if (end) for (int yy = g + 1; yy <= top; yy++) w.set(x, yy, arch[1], "mossy_stone_bricks");
                else w.set(x, top, arch[1], "stone_brick_slab[type=top,waterlogged=true]");
            }
        }
    }

    /** Kelp, seagrass, coral and pickles on the sea floor. */
    void seaLife() {
        String[] corals = {"tube", "brain", "bubble", "fire", "horn"};
        for (int x = X0; x <= 0; x++) for (int z = Z0; z <= Z1; z++) {
            if (!w.canon(x, z)) continue;
            int g = land(x, z);
            if (g == Integer.MIN_VALUE || g >= WL - 1 || w.type(x, g + 1, z) != Material.WATER) continue;
            boolean reef = Math.hypot(x + 6, z + 22) < 8.5, kelp = Math.hypot(x + 64, z - 36) < 11;
            double r = w.r.nextDouble();
            if (reef) {
                String c = corals[w.r.nextInt(corals.length)];
                if (r < 0.35) w.set(x, g, z, c + "_coral_block");
                else if (r < 0.7) w.set(x, g + 1, z, c + (w.r.nextBoolean() ? "_coral" : "_coral_fan") + "[waterlogged=true]");
                else if (r < 0.8) w.set(x, g + 1, z, "sea_pickle[pickles=" + (1 + w.r.nextInt(4)) + ",waterlogged=true]");
            } else if (kelp && r < 0.45) {
                int topY = WL - 1 - w.r.nextInt(3);
                for (int yy = g + 1; yy < topY; yy++) w.set(x, yy, z, "kelp_plant");
                w.set(x, topY, z, "kelp[age=20]");
            } else if (r < 0.12) {
                if (r < 0.04 && w.type(x, g + 2, z) == Material.WATER) {
                    w.set(x, g + 1, z, "tall_seagrass[half=lower]");
                    w.set(x, g + 2, z, "tall_seagrass[half=upper]");
                } else w.set(x, g + 1, z, "seagrass");
            } else if (r < 0.125) w.set(x, g + 1, z, "sea_pickle[pickles=2,waterlogged=true]");
        }
    }

    /** Falls off the rim: springs in its outer face pouring into the void. */
    void falls() {
        for (int[] f : new int[][] {{-80, -1}, {-34, -1}, {-60, 1}, {-12, 1}}) {
            int x = f[0], s = f[1], last = Integer.MIN_VALUE;
            for (int zz = 0; zz <= 72; zz++) {
                int g = land(x, s * zz);
                if (g == Integer.MIN_VALUE && last != Integer.MIN_VALUE) {
                    w.set(x, last - 1, s * (zz - 1), "water");
                    break;
                }
                last = g;
            }
        }
    }
}
