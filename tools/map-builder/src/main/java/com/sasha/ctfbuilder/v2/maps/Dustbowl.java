package com.sasha.ctfbuilder.v2.maps;

import com.sasha.ctfbuilder.v2.Arena;
import com.sasha.ctfbuilder.v2.Kit;
import com.sasha.ctfbuilder.v2.Land;
import com.sasha.ctfbuilder.v2.Theme;
import com.sasha.ctfbuilder.v2.W.P;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Dustbowl: two great mesas lying side by side the length of the map, a winding void gorge between them. Each team
 * owns a whole mesa; the forts stand at opposite ends, so the flag is always across the gorge and far down the
 * other side, and the game is choosing where to cross.
 *
 * Every mesa steps down in benches from its fort: the fort on its high end (spawn in the barracks, flag in the
 * yard), a grand stair down to the railroad town (station, depot, saloon, sheriff, store, smithy, a locomotive at
 * the end of the line), the arch zone (a butte with a slot canyon through it and a stair to a lookout on top), the
 * hoodoo flats (a hill) and the ranch bench, which ends right under the enemy's fort. Scarps between the benches
 * are cliffs, taken by ramps.
 *
 * Fixed crossings: the Arch (a natural sandstone span in the middle, the main hill) and two timber truss bridges
 * carrying the railroad, each from a team's town to the far end of the enemy mesa. Anywhere else the gorge is
 * bridged by hand: about nine blocks under the forts, up to twenty-five at the rock pillars.
 */
public final class Dustbowl extends Arena {

    static final int FORT = 84, TOWN = 72, ARCH = 68, FLATS = 65, BENCH = 72;
    static final int WEST = -133, EAST = 112;
    static final int FCX = -108, FCZ = -32;        // the fort's middle
    static final int SCARP = -86;                   // the fort mesa's east face, where the grand stair comes down
    static final int BX = -62;                      // the truss bridge (its copy is at +62)
    static final int RAIL_Z = -17;                  // the main line through the town
    static final int SZ = -32;                      // the town street's middle
    static final double BUTTE_X = -22, BUTTE_Z = -33, BUTTE_RX = 13, BUTTE_RZ = 9;
    static final int BUTTE_TOP = 80;
    static final int PILLAR_X = -30;
    static final int FLATS_X = 37;                  // the hoodoo flats hill (north mesa; its copy is on the south mesa)

    /** A ramp through a scarp, running along the mesa a set distance in from its rim (or its outer edge). */
    record Ramp(double x0, double x1, boolean fromOuter, double off, double half, double h0, double h1) {}

    static final Ramp[] RAMPS = {
            new Ramp(-39, -27, false, 5, 3.5, TOWN, ARCH),       // town to the arch zone, by the rim
            new Ramp(-39, -27, true, 6, 4, TOWN, ARCH),          // and by the outer edge
            new Ramp(18, 30, false, 4.5, 3, ARCH, FLATS),        // arch zone to the flats, by the rim
            new Ramp(18, 30, false, 22, 4, ARCH, FLATS),         // and through the middle
            new Ramp(42, 60, true, 5, 3.5, FLATS, BENCH),        // flats up to the bench, by the outer edge
    };

    /** Box canyons bitten into the outer edge: x, depth, radius. */
    static final double[][] BITES = {{-14, 13, 9}, {86, 11, 7.5}};

    Land land;

    public Dustbowl(World world) {
        super(world, "dustbowl", false, Theme.get("dustbowl"));
        name = "Dustbowl";
        icon = "CACTUS";
        minutes = 18;
        kothPoints = 250;
        reach = 136;
        low = 15;
        high = 140;
        voidY = 15;
        spectator = new double[] {0.5, 110, -40.5, 0, 40};
    }

    // ================================================================ the land

    static double sq(double v) { return v * v; }

    /** The gorge's centre line (odd, so the gorge turns into itself under the half-turn). */
    static double g(double x) { return 5 * Math.sin(x / 38) + 2.5 * Math.sin(x / 15); }

    /** The gorge's half width (even). */
    double hw(double x) {
        double ax = Math.abs(x);
        return 7 + 5.5 * Math.exp(-sq((ax - 30) / 11)) + 2 * Math.exp(-sq((ax - 62) / 7)) - 1.5 * Math.exp(-sq((ax - 96) / 10))
                + w.fbm(ax, 3, 9, 2);
    }

    /** The north mesa's rim over the gorge, before its ends are rounded. */
    double rawRim(double x) { return g(x) - hw(x) + w.fbm(x * 1.3, 77, 5, 2); }

    double width(double x) {
        return 40 + 7 * Math.sin(x / 23 + 1) + 2 * Math.sin(x / 9 + 2) + 9 * Math.exp(-sq((x + 62) / 15)) + w.fbm(x, 50, 12, 2) * 4;
    }

    /** The north mesa's rim and outer edge at x, with the ends rounded off: {rim, outer} (z), or null past its ends. */
    double[] span(double x) {
        if (x < WEST - 3 || x > EAST + 3) return null;
        double rim = rawRim(x), outer = rim - width(x);
        double end = Land.smooth((x - WEST) / 14) * Land.smooth((EAST - x) / 12);
        double mid = (rim + outer) / 2, half = (rim - outer) / 2 * (0.25 + 0.75 * Math.sqrt(end));
        return new double[] {mid + half, mid - half};
    }

    /** The north mesa's middle (z) at x. */
    double midZ(double x) {
        double[] s = span(x);
        return (s[0] + s[1]) / 2;
    }

    /** The bench a column of the north mesa is on, before ramps and features. */
    double tier(int x, int z) {
        double j = w.fbm(z * 1.1, 300, 7, 2) * 2.5;
        double face = Math.abs(z - FCZ) <= 5 ? 0 : w.fbm(z * 1.3, 900, 6, 1) * 1.2;
        if (x < -121 + j * 1.5) {
            // the rim behind the fort: broken rock rising toward the mesa's end
            double rug = FORT + 7 + (-121 - x) * 0.7 + w.fbm(x * 1.3, z * 1.3, 9, 3) * 4;
            double spike = w.fbm(x * 2.6 + 50, z * 2.6, 5, 2);
            if (spike > 0.42) rug += (spike - 0.42) * 26;
            return rug;
        }
        if (x <= SCARP + face) return FORT;
        if (x < -36 + j) return TOWN;
        if (x < 24 + j) return ARCH;
        if (x < 50 + j) return FLATS;
        return BENCH;
    }

    /** The north mesa's ground at (x, z), or NaN off it. */
    double north(int x, int z) {
        double[] s = span(x);
        if (s == null || z > s[0] || z < s[1]) return Double.NaN;
        double outer = s[1];
        for (double[] b : BITES) if (Math.hypot(x - b[0], (z - outer) * 0.8) < b[2] && z - outer < b[1]) return Double.NaN;
        double h = tier(x, z);
        // the butte, a slot canyon winding through it
        double bu = 1 - Math.hypot((x - BUTTE_X) / BUTTE_RX, (z - BUTTE_Z) / BUTTE_RZ) + w.fbm(x * 1.4, z * 1.4, 4, 2) * 0.07;
        if (bu > 0) {
            h = Math.max(h, ARCH + Math.min(BUTTE_TOP - ARCH, bu * 120));
            if (Math.abs(z - slotZ(x)) < 1.35 + w.fbm(x * 2, 40, 3, 1) * 0.35) h = ARCH;
        }
        for (Ramp r : RAMPS) {
            if (x < r.x0 || x > r.x1) continue;
            double cz = r.fromOuter ? s[1] + r.off : s[0] - r.off;
            if (Math.abs(z - cz) < r.half) h = r.h0 + (r.h1 - r.h0) * (x - r.x0) / (r.x1 - r.x0);
        }
        return h;
    }

    /** The slot canyon's line through the butte. */
    static double slotZ(double x) { return BUTTE_Z + 2.6 * Math.sin((x - BUTTE_X) / 4.2); }

    /** The Arch over the gorge in the middle: its deck height, or NaN. */
    double arch(int x, int z) {
        if (Math.abs(z) > 12) return Double.NaN;
        double half = 4.5 + 2.4 * sq(z / 10.0) + w.fbm(z * 1.7, 11, 4, 1) * 0.5;
        if (Math.abs(x) > half) return Double.NaN;
        return ARCH + Math.max(0, 3 * (1 - sq(z / 9.5)));
    }

    /** The rock pillar standing in the gorge (its copy stands at -PILLAR_X): its top, or NaN. */
    double pillar(int x, int z) {
        double d = Math.hypot(x - PILLAR_X, z - g(PILLAR_X));
        return d < 2.9 ? FLATS + 1 : Double.NaN;
    }

    double height(int x, int z) {
        double a = north(x, z), b = north(-x, -z);
        double h = Double.isNaN(a) ? b : Double.isNaN(b) ? a : Math.max(a, b);
        for (double e : new double[] {arch(x, z), pillar(x, z)})
            if (!Double.isNaN(e)) h = Double.isNaN(h) ? e : Math.max(h, e);
        return h;
    }

    /** Is this column part of a mesa (not just the arch or the pillar)? */
    boolean onMesa(int x, int z) {
        return !Double.isNaN(north(x, z)) || !Double.isNaN(north(-x, -z));
    }

    /** The point on the north mesa this column stands for: itself, or (for the south mesa) its turned copy. */
    static int[] nc(int x, int z) {
        return z <= g(x) ? new int[] {x, z} : new int[] {-x, -z};
    }

    String surface(int x, int z, int y, int steep, double edge) {
        if (steep >= 3) return rock(x, y, z);
        int nx = nc(x, z)[0];
        double big = w.fbm(x + 913, z - 377, 8, 2), fine = w.n2(x * 1.7, z * 1.7, 3);
        if (nx < -121) return big > 0.2 ? "coarse_dirt" : fine > 0.3 ? "terracotta" : "red_sand";
        if (nx <= SCARP) return big > 0.25 ? "coarse_dirt" : big < -0.3 ? "packed_mud" : "red_sand";
        if (y >= BUTTE_TOP - 1) return big > 0.1 ? "coarse_dirt" : fine > 0.4 ? "terracotta" : "red_sand";
        if (nx < -36) return big > 0.38 ? "coarse_dirt" : big < -0.5 ? "sand" : fine > 0.6 ? "packed_mud" : "red_sand";
        if (nx < 24) return big > 0.42 ? "coarse_dirt" : big < -0.5 ? "terracotta" : fine > 0.62 ? "packed_mud" : "red_sand";
        if (nx < 50) return big > 0.35 ? "sand" : big < -0.45 ? "coarse_dirt" : fine > 0.6 ? "sand" : "red_sand";
        return big > 0.15 ? "coarse_dirt" : big < -0.55 ? "sand" : fine > 0.55 ? "packed_mud" : "red_sand";
    }

    static final String[] LAYERS = {"terracotta", "brown_terracotta", "red_terracotta", "orange_terracotta", "yellow_terracotta",
            "orange_terracotta", "white_terracotta", "light_gray_terracotta", "terracotta", "red_terracotta", "smooth_red_sandstone", "orange_terracotta"};
    static final int[] THICK = {3, 2, 3, 2, 1, 2, 1, 2, 2, 2, 2, 3};
    static final int CYCLE = 25;

    /** The rock: crisp, nearly level strata, the odd block taking the colour of the layer next to it. */
    String rock(int x, int y, int z) {
        double warp = w.fbm(x * 0.6 + 11, z * 0.6 - 7, 36, 2) * 1.4;
        int at = Math.floorMod((int) Math.floor(y + warp), CYCLE);
        int i = 0;
        while (at >= THICK[i]) at -= THICK[i++];
        double tex = w.n3(x * 1.9, y * 1.9, z * 1.9, 4);
        if (tex > 0.6) i = (i + 1) % LAYERS.length;
        else if (tex < -0.62) i = (i + LAYERS.length - 1) % LAYERS.length;
        return LAYERS[i];
    }

    /** Mesas hang deep under their middles, taper to their rims and drip spires; the arch is a thin span. */
    int bottom(int x, int z, int top, double edge) {
        if (!onMesa(x, z)) {
            if (!Double.isNaN(pillar(x, z))) {
                double d = Math.hypot(x - PILLAR_X, z - g(PILLAR_X));
                return (int) Math.round(26 + d * 9 + w.fbm(x * 2, z * 2, 3, 1) * 3);
            }
            return (int) Math.round(top - 5 - 30 * Math.pow(Math.abs(z) / 11.0, 2.5));
        }
        double lens = 1 - Math.pow(1 - Math.min(1, edge / 11.0), 2.2);
        double b = top - 3 - 38 * lens + w.fbm(x * 0.9 + 5, z * 0.9 - 5, 14, 2) * 3 * Math.min(1, edge / 6.0);
        double cone = w.fbm(x * 1.6 + 300, z * 1.6 - 200, 8, 2);
        if (cone > 0.36 && edge > 4) b -= (cone - 0.36) * 45 * Math.min(1, edge / 12.0);
        return Math.max(low + 3, (int) Math.round(b));
    }

    // ================================================================ walking

    /**
     * Half steps: every open square of ground is lifted by a bottom slab where that brings it within half a block
     * of a neighbour a block above it, so the ramps and slopes can be walked without jumping; cliffs stay cliffs.
     */
    void halfSteps() {
        int x0 = -136, x1 = 136, z0 = -70, z1 = 70, sx = x1 - x0 + 1, sz = z1 - z0 + 1;
        int[][] top = new int[sx][sz];
        double[][] walk = new double[sx][sz];
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            int t = land.topAt(x, z);
            top[x - x0][z - z0] = t;
            walk[x - x0][z - z0] = t + 1;
        }
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int pass = 0; pass < 8; pass++) {
            boolean changed = false;
            for (int i = 1; i < sx - 1; i++) for (int j = 1; j < sz - 1; j++) {
                int t = top[i][j];
                if (t == Integer.MIN_VALUE) continue;
                for (int[] d : dirs) {
                    int nt = top[i + d[0]][j + d[1]];
                    if (nt != t + 1) continue;
                    double want = walk[i + d[0]][j + d[1]] - 0.5;
                    if (want > walk[i][j] && want <= t + 1.5) {
                        walk[i][j] = want;
                        changed = true;
                    }
                }
            }
            if (!changed) break;
        }
        for (int x = x0 + 1; x < x1; x++) for (int z = z0 + 1; z < z1; z++) {
            if (!w.canon(x, z)) continue;
            int i = x - x0, j = z - z0, t = top[i][j];
            if (t == Integer.MIN_VALUE || walk[i][j] - t < 1.25) continue;
            w.set(x, t + 1, z, slabFor(w.type(x, t, z)) + "[type=bottom]");
        }
    }

    static String slabFor(Material m) {
        String n = m.name();
        if (n.equals("SAND") || n.contains("SANDSTONE") && !n.contains("RED")) return "smooth_sandstone_slab";
        if (n.equals("PACKED_MUD") || n.equals("MUD_BRICKS") || n.equals("DIRT_PATH")) return "mud_brick_slab";
        return "smooth_red_sandstone_slab";
    }

    // ================================================================ the map

    @Override
    protected void build() {
        land = new Land(w, t, -136, 136, -70, 70);
        land.top = this::height;
        land.surface = this::surface;
        land.rock = this::rock;
        land.bottom = this::bottom;
        land.soil = P.of("terracotta", 3, "red_sandstone", 1);
        land.build();
        halfSteps();

        fort(FCX, FCZ);
        grandStair();
        rimStair();
        fortFlag();
        trussBridge();
        railroad();
        town();
        slotCanyon();
        butte();
        archEnds();
        hoodooFlats();
        ranch();
        rubble();
        cacti();
        boulder(-100, -50, 2);
        boulder(-30, -48, 1);
        boulder(56, -18, 2);
        boulder(100, -28, 1);
        boulder(108, -12, 2);
        for (int[] tr : new int[][] {{-80, -46}, {-32, -50}, {12, -40}, {54, -30}, {90, -32}})
            if (land(tr[0], tr[1]) != Integer.MIN_VALUE) flora.tree(Theme.Tree.DEAD, tr[0], land(tr[0], tr[1]) + 1, tr[1]);
        // the desert: dead bushes, dry grass and the odd cactus; drier on the flats, grassier on the ranch bench
        plants(P.of("dead_bush", 4, "short_dry_grass", 8, "tall_dry_grass", 3), 0.06, (x, z) -> nc(x, z)[0] < 50);
        plants(P.of("short_dry_grass", 10, "tall_dry_grass", 5, "dead_bush", 1), 0.12, (x, z) -> nc(x, z)[0] >= 50);

        spawn = new double[] {FCX - 5.5, FORT + 1, FCZ + 0.5, -90};
        flag = new int[] {FCX + 8, FORT + 1, FCZ};
        pads.add(new int[] {FCX + 9, FORT + 1, FCZ - 9});
        pads.add(new int[] {FCX + 9, FORT + 1, FCZ + 9});
        int fz = (int) Math.round(midZ(FLATS_X));
        hills.add(new Hill("North Hoodoos", FLATS_X - 3, FLATS + 1, fz - 3, FLATS_X + 3, FLATS + 4, fz + 3));
        hills.add(new Hill("The Arch", -3, ARCH + 4, -3, 3, ARCH + 7, 3));
        hills.add(new Hill("South Hoodoos", -FLATS_X - 3, FLATS + 1, -fz - 3, -FLATS_X + 3, FLATS + 4, -fz + 3));
    }

    // ================================================================ the fort

    /**
     * An adobe fort: mud-brick curtain walls with a walk and parapet, square corner towers with beams poking
     * out of them, a gatehouse to the east, barracks along the west wall (the spawn) with a shaded porch, a well
     * and the flag in the yard.
     */
    void fort(int cx, int cz) {
        int y = FORT;
        int x0 = cx - 12, x1 = cx + 12, z0 = cz - 15, z1 = cz + 15;
        level(x0 - 2, z0 - 2, x1 + 2, z1 + 2, y, P.of("packed_mud", 3, "coarse_dirt", 1, "red_sand", 1), P.of("terracotta"));
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            boolean wall = x == x0 || x == x1 || z == z0 || z == z1;
            boolean walk = !wall && (x == x0 + 1 || x == x1 - 1 || z == z0 + 1 || z == z1 - 1);
            if (wall) {
                for (int dy = 1; dy <= 6; dy++) w.set(x, y + dy, z, dy <= 1 ? "terracotta" : P.of("mud_bricks", 5, "packed_mud", 1).pick(w.r));
                if (((x + z) & 1) == 0) w.set(x, y + 7, z, "mud_brick_wall");
            } else if (walk) {
                w.set(x, y + 5, z, "spruce_slab[type=top]");
            }
        }
        for (int[] c : new int[][] {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
            int tx = c[0], tz = c[1];
            k.walls(tx - 3, tz - 3, tx + 3, tz + 3, y, 10, Kit.Bands.of(0, "terracotta", 1, P.of("mud_bricks", 5, "packed_mud", 1)), null, 0, P.of("packed_mud"));
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
                boolean edge = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                w.set(tx + dx, y + 10, tz + dz, edge ? "mud_bricks" : "spruce_planks");
                if (edge && ((dx + dz) & 1) == 0) w.set(tx + dx, y + 11, tz + dz, "mud_brick_wall");
            }
            for (int o = -2; o <= 2; o += 2) {
                w.set(tx + o, y + 9, tz - 4, "stripped_spruce_log[axis=z]");
                w.set(tx + o, y + 9, tz + 4, "stripped_spruce_log[axis=z]");
                w.set(tx - 4, y + 9, tz + o, "stripped_spruce_log[axis=x]");
                w.set(tx + 4, y + 9, tz + o, "stripped_spruce_log[axis=x]");
            }
            w.set(tx + 1, y + 7, tz - 3, "air");
            w.set(tx + 1, y + 7, tz + 3, "air");
            w.set(tx - 3, y + 7, tz + 1, "air");
            w.set(tx + 3, y + 7, tz + 1, "air");
            int lx = tx < cx ? tx - 2 : tx + 2;
            for (int dy = 1; dy <= 10; dy++) w.set(lx, y + dy, tz, "ladder[facing=" + (tx < cx ? "east" : "west") + "]");
            int dx2 = tx < cx ? 3 : -3;
            w.set(tx + dx2, y + 1, tz + (tz < cz ? 2 : -2), "air");
            w.set(tx + dx2, y + 2, tz + (tz < cz ? 2 : -2), "air");
        }
        // the gate in the east wall: a gatehouse arch, beams over
        for (int dz = -2; dz <= 2; dz++) for (int dy = 1; dy <= 4; dy++) w.set(x1, y + dy, cz + dz, "air");
        for (int dz = -3; dz <= 3; dz++) for (int dy = 5; dy <= 9; dy++) {
            boolean edge = Math.abs(dz) == 3 || dy == 9;
            w.set(x1, y + dy, cz + dz, dy == 5 ? "stripped_spruce_log[axis=z]" : "mud_bricks");
            w.set(x1 + 1, y + dy, cz + dz, dy == 5 ? "stripped_spruce_log[axis=z]" : edge ? "mud_bricks" : "air");
        }
        for (int dz = -3; dz <= 3; dz += 2) w.set(x1 + 2, y + 8, cz + dz, "stripped_spruce_log[axis=x]");
        w.set(x1 + 1, y + 7, cz, "TEAM_wall_banner[facing=east]");
        for (int s : new int[] {-3, 3}) {
            w.set(x1 + 1, y + 3, cz + s, "spruce_fence");
            w.set(x1 + 1, y + 2, cz + s, "lantern[hanging=true]");
        }
        // the barracks along the west wall: the spawn, a long room with bunks
        int bx0 = x0 + 1, bx1 = x0 + 9, bz0 = cz - 7, bz1 = cz + 7;
        k.walls(bx0, bz0, bx1, bz1, y, 5, Kit.Bands.of(0, "terracotta", 1, P.of("mud_bricks", 4, "packed_mud", 1)), null, 0, P.of("spruce_planks"));
        for (int x = bx0; x <= bx1; x++) for (int z = bz0; z <= bz1; z++) w.set(x, y + 6, z, x == bx0 || x == bx1 || z == bz0 || z == bz1 ? "mud_bricks" : "spruce_planks");
        for (int z = bz0 + 1; z < bz1; z += 2) w.set(bx1 + 1, y + 5, z, "stripped_spruce_log[axis=x]");
        for (int dz = -1; dz <= 1; dz++) for (int dy = 1; dy <= 3; dy++) w.set(bx1, y + dy, cz + dz, "air");
        for (int dz = -2; dz <= 2; dz++) w.set(bx1, y + 4, cz + dz, "stripped_spruce_log[axis=z]");
        for (int s : new int[] {-1, 1}) k.window(bx1, y + 2, cz + s * 4, "east", 1, 2, "glass_pane", null, "spruce_trapdoor");
        for (int s : new int[] {-3, 3}) for (int dy = 1; dy <= 3; dy++) w.set(bx1 + 3, y + dy, cz + s, "stripped_spruce_log[axis=y]");
        for (int dz = -3; dz <= 3; dz++) {
            w.set(bx1 + 1, y + 4, cz + dz, "spruce_slab[type=top]");
            w.set(bx1 + 2, y + 4, cz + dz, "spruce_slab[type=top]");
            w.set(bx1 + 3, y + 4, cz + dz, "stripped_spruce_log[axis=z]");
        }
        w.hangLight(bx1 + 3, y + 3, cz, "lantern");
        for (int z = bz0 + 1; z <= bz1 - 1; z += 2) {
            if (Math.abs(z - cz) <= 2) continue;
            k.bed(bx0 + 2, y + 1, z, "west", "TEAM");
            w.set(bx0 + 3, y + 1, z, "barrel[facing=up]");
        }
        for (int z = bz0 + 3; z <= bz1 - 3; z += 4) w.hangLight(bx0 + 5, y + 4, z, "lantern");
        w.set(bx0 + 1, y + 3, cz, "TEAM_wall_banner[facing=east]");
        // the yard: a well, water troughs, hay
        int wx = cx - 3, wz = cz + 11;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            boolean mid = dx == 0 && dz == 0;
            w.set(wx + dx, y, wz + dz, mid ? "water" : "mud_bricks");
            if (!mid) w.set(wx + dx, y + 1, wz + dz, "mud_brick_wall");
        }
        for (int[] p : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) w.set(wx + p[0], y + 2, wz + p[1], "spruce_fence");
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) w.set(wx + dx, y + 3, wz + dz, "spruce_slab[type=bottom]");
        w.set(cx - 4, y + 1, cz - 11, "hay_block");
        w.set(cx - 3, y + 1, cz - 11, "hay_block");
        w.set(cx - 4, y + 2, cz - 11, "hay_block");
        w.set(cx - 1, y + 1, cz - 12, "water_cauldron[level=3]");
        w.set(cx, y + 1, cz - 12, "water_cauldron[level=3]");
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) w.set(cx + 8 + dx, y, cz + dz, "chiseled_red_sandstone");
        keepClear(x0 - 4, z0 - 4, x1 + 4, z1 + 4);
    }

    /** The grand stair from the fort's forecourt down the mesa's face into the head of the town street. */
    void grandStair() {
        // the forecourt between the gate and the stair head, level with the yard
        level(FCX + 13, FCZ - 6, SCARP, FCZ + 6, FORT, P.of("packed_mud", 3, "coarse_dirt", 1), P.of("terracotta"));
        for (int i = 1; i <= 12; i++) {
            int x = SCARP + i, sy = FORT + 1 - i;
            for (int dz = -3; dz <= 3; dz++) {
                int z = FCZ + dz;
                for (int yy = TOWN - 2; yy < sy; yy++) w.set(x, yy, z, "smooth_sandstone");
                w.set(x, sy, z, "smooth_sandstone_stairs[facing=west,half=bottom]");
                for (int dy = 1; dy <= 5; dy++) w.set(x, sy + dy, z, "air");
            }
            for (int s : new int[] {-4, 4}) {
                for (int yy = TOWN + 1; yy <= sy; yy++) w.set(x, yy, FCZ + s, "mud_bricks");
                w.set(x, sy + 1, FCZ + s, "mud_brick_stairs[facing=west,half=bottom]");
            }
        }
        for (int s : new int[] {-4, 4}) {
            w.set(SCARP, FORT + 1, FCZ + s, "mud_bricks");
            w.set(SCARP, FORT + 2, FCZ + s, "mud_bricks");
            w.set(SCARP, FORT + 3, FCZ + s, "lantern");
            int fx = SCARP + 13;
            for (int yy = TOWN + 1; yy <= TOWN + 3; yy++) w.set(fx, yy, FCZ + s, "mud_bricks");
            w.set(fx, TOWN + 4, FCZ + s, "lantern");
            w.set(fx + 1, TOWN + 3, FCZ + s, "TEAM_wall_banner[facing=east]");
        }
        // a low wall along the top of the face, either side of the stair
        for (int z = FCZ - 16; z <= FCZ + 14; z++) {
            if (Math.abs(z - FCZ) <= 4) continue;
            for (int x = SCARP + 2; x >= SCARP - 2; x--) {
                if (land.topAt(x, z) != FORT || land.topAt(x + 1, z) >= FORT) continue;
                w.set(x, FORT + 1, z, "mud_brick_wall");
                break;
            }
        }
        keepClear(SCARP - 1, FCZ - 6, SCARP + 15, FCZ + 6);
    }

    // ================================================================ the rim stair

    /** The fort's second way down: a narrow stair cut down the mesa's face along the rim, to the end of the line. */
    void rimStair() {
        int z0 = -14, z1 = -12;
        for (int i = 1; i <= 12; i++) {
            int x = SCARP + i, sy = FORT + 1 - i;
            for (int z = z0; z <= z1; z++) {
                for (int yy = TOWN - 1; yy < sy; yy++) w.set(x, yy, z, rock(x, yy, z));
                w.set(x, sy, z, "smooth_red_sandstone_stairs[facing=west,half=bottom]");
                for (int dy = 1; dy <= 4; dy++) w.set(x, sy + dy, z, "air");
            }
            // a rib of rock on the gorge side, a mud-brick cheek on the town side
            for (int yy = TOWN + 1; yy <= sy + 1; yy++) w.set(x, yy, z1 + 1, rock(x, yy, z1 + 1));
            for (int yy = TOWN + 1; yy <= sy; yy++) w.set(x, yy, z0 - 1, "mud_bricks");
            w.set(x, sy + 1, z0 - 1, "mud_brick_wall");
        }
        // the stair head: firm ground, a post and a lamp
        for (int x = SCARP - 2; x <= SCARP; x++) for (int z = z0; z <= z1; z++) {
            w.set(x, FORT, z, "packed_mud");
            for (int dy = 1; dy <= 4; dy++) w.set(x, FORT + dy, z, "air");
        }
        w.set(SCARP, FORT + 1, z1 + 1, "mud_bricks");
        w.set(SCARP, FORT + 2, z1 + 1, "lantern");
        keepClear(SCARP - 3, z0 - 2, SCARP + 13, z1 + 2);
    }

    // ================================================================ the town

    /** A town building: u runs along x from x0, v from the front wall (0) back; dy counts up from its floor. */
    final class Bld {
        final int x0, x1, zf, zb, in, y;
        final String out, back;

        Bld(int x0, int x1, int zf, int zb, int y) {
            this.x0 = x0;
            this.x1 = x1;
            this.zf = zf;
            this.zb = zb;
            this.y = y;
            in = Integer.signum(zb - zf);
            out = in < 0 ? "south" : "north";
            back = Kit.opp(out);
        }

        int x(int u) { return x0 + u; }
        int z(int v) { return zf + in * v; }
        int n() { return x1 - x0; }
        int d() { return Math.abs(zb - zf); }
        void set(int u, int dy, int v, String spec) { w.set(x(u), y + dy, z(v), spec); }
        void hang(int u, int dy, int v) { w.hangLight(x(u), y + dy, z(v), "lantern"); }
        String door(String wood, boolean upper, String hinge) {
            return wood + "_door[facing=" + back + ",half=" + (upper ? "upper" : "lower") + ",hinge=" + hinge + ",open=false]";
        }
    }

    /**
     * The railroad town on the bench below the fort: a street running east from the foot of the grand stair, a
     * boardwalk either side; the sheriff, the saloon and the general store on its outer side; the depot, the water
     * tower and the smithy between it and the platform; the line along the rim with a locomotive standing at its end.
     */
    void town() {
        int y = TOWN;
        // the street: packed dirt with wheel ruts
        for (int x = -73; x <= -38; x++) for (int z = SZ - 2; z <= SZ + 2; z++) {
            if (land.topAt(x, z) != TOWN) continue;
            double n = w.n2(x * 1.9, z * 1.9, 2);
            String top = Math.abs(z - SZ) == 1 && x < -40 ? (n > -0.35 ? "dirt_path" : "coarse_dirt")
                    : n > 0.45 ? "packed_mud" : n < -0.55 ? "gravel" : "coarse_dirt";
            w.set(x, y, z, top);
        }
        boardwalk(-72, -41, SZ - 5, SZ - 3);
        boardwalk(-71, -45, SZ + 3, SZ + 5);
        sheriff(new Bld(-72, -64, SZ - 6, SZ - 13, y + 1));
        saloon(new Bld(-62, -52, SZ - 6, SZ - 15, y + 1));
        store(new Bld(-50, -41, SZ - 6, SZ - 14, y + 1));
        depot();
        waterTower(-58, -23);
        smithy(new Bld(-54, -46, SZ + 6, SZ + 11, y + 1));
        platform();
        locomotive();
        // lamps and hitching rails along the street, barrels and crates on the boardwalks
        for (int x : new int[] {-66, -56, -46}) {
            for (int dy = 1; dy <= 3; dy++) w.set(x, y + dy, SZ + 2, "stripped_spruce_log[axis=y]");
            w.set(x, y + 4, SZ + 2, "lantern");
        }
        for (int x : new int[] {-62, -61, -60, -50, -49}) w.set(x, y + 1, SZ - 2, "spruce_fence");
        for (int[] b : new int[][] {{-63, SZ - 4}, {-50, SZ - 5}, {-49, SZ - 5}, {-66, SZ + 4}}) w.set(b[0], y + 2, b[1], "barrel[facing=up]");
        w.set(-49, y + 3, SZ - 5, "barrel[facing=up]");
        wagon(-42, SZ, y + 1, false);
        outhouse(-76, SZ - 9, y);
        keepClear(-74, SZ - 15, -37, RAIL_Z + 1);
    }

    /** A raised boardwalk: planks a block above the street's ground and a slab on them (a half step up from the road). */
    void boardwalk(int x0, int x1, int z0, int z1) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) for (int z = z0; z <= z1; z++) {
            if (land.topAt(x, z) != TOWN) continue;
            w.set(x, TOWN, z, "spruce_planks");
            w.set(x, TOWN + 1, z, "spruce_slab[type=bottom]");
        }
    }

    void shell(Bld b, int h, Kit.Bands bands, String post, P floor) {
        k.walls(b.x0, Math.min(b.zf, b.zb), b.x1, Math.max(b.zf, b.zb), b.y, h, bands, post, 0, floor);
    }

    /** A low gable roof of slabs, ridge running back from the street; the back gable walled up. Returns the steps to the ridge. */
    int slabRoof(Bld b, int yTop, String plank, String slab) {
        int ex0 = b.x0 - 1, ex1 = b.x1 + 1, smax = 0;
        for (int x = ex0; x <= ex1; x++) {
            int s = Math.min(x - ex0, ex1 - x);
            smax = Math.max(smax, s);
            int level = yTop + s / 2;
            boolean top = s % 2 == 1;
            for (int v = 1; v <= b.d() + 1; v++) {
                int z = b.z(v);
                if (v == b.d() && x >= b.x0 && x <= b.x1) {
                    for (int yy = yTop; yy < level + (top ? 1 : 0); yy++) w.set(x, yy, z, plank);
                    if (!top) w.set(x, level, z, slab + "[type=bottom]");
                } else {
                    w.set(x, level, z, slab + "[type=" + (top ? "top" : "bottom") + "]");
                }
            }
        }
        return smax;
    }

    /** The false front: the street wall carried up past the roof, a sign board, a cornice and a raised middle. */
    int falseFront(Bld b, int h, int smax, String fill, String pilaster, String trim, String board, String cornice, String cap, String wood, String... signs) {
        int yTop = b.y + h + 1, top = yTop + smax / 2 + 2, n = b.n();
        int fz = b.zf, oz = b.zf - b.in;
        for (int u = 0; u <= n; u++) for (int yy = yTop; yy <= top; yy++) w.set(b.x(u), yy, fz, u == 0 || u == n ? pilaster : fill);
        int by0 = yTop + 1, by1 = yTop + 2;
        for (int u = 1; u < n; u++) {
            w.set(b.x(u), by0 - 1, fz, trim);
            w.set(b.x(u), by1 + 1, fz, trim);
            for (int yy = by0; yy <= by1; yy++) w.set(b.x(u), yy, fz, u == 1 || u == n - 1 ? trim : board);
        }
        for (String sign : signs) {
            int u = Integer.parseInt(sign.substring(0, sign.indexOf(':')));
            String text = sign.substring(sign.indexOf(':') + 1);
            w.set(b.x(u), by1, oz, wood + "_wall_sign[facing=" + b.out + "]");
            w.signText(b.x(u), by1, oz, true, "", text, "", "");
        }
        for (int u = -1; u <= n + 1; u++) w.set(b.x(u), top, oz, cornice + "[facing=" + b.back + ",half=top]");
        for (int u = 0; u <= n; u++) w.set(b.x(u), top + 1, fz, cap + "[type=bottom]");
        int m0 = n / 3, m1 = n - n / 3;
        for (int u = m0; u <= m1; u++) {
            w.set(b.x(u), top + 1, fz, fill);
            w.set(b.x(u), top + 2, fz, fill);
            w.set(b.x(u), top + 2, oz, cornice + "[facing=" + b.back + ",half=top]");
            w.set(b.x(u), top + 3, fz, cap + "[type=bottom]");
        }
        w.set(b.x(m0 - 1), top + 1, fz, cornice + "[facing=east,half=bottom]");
        w.set(b.x(m1 + 1), top + 1, fz, cornice + "[facing=west,half=bottom]");
        return top;
    }

    /** A porch over the boardwalk: posts on its street edge, a beam, a roof of slabs (or a balcony with a rail). */
    void porch(Bld b, int height, boolean balcony, int... posts) {
        int n = b.n(), yr = b.y + height;
        for (int u = -1; u <= n + 1; u++) {
            for (int dd = 1; dd <= 3; dd++) {
                int z = b.zf - b.in * dd;
                w.set(b.x(u), yr, z, dd == 3 ? "stripped_spruce_log[axis=x]" : balcony ? "spruce_planks" : "spruce_slab[type=top]");
            }
            if (balcony) w.set(b.x(u), yr + 1, b.zf - b.in * 3, "spruce_fence");
        }
        if (balcony) for (int u : new int[] {-1, n + 1}) for (int dd = 1; dd <= 2; dd++) w.set(b.x(u), yr + 1, b.zf - b.in * dd, "spruce_fence");
        for (int u : posts) for (int yy = b.y; yy < yr; yy++) w.set(b.x(u), yy, b.zf - b.in * 3, "stripped_spruce_log[axis=y]");
        for (int i = 0; i + 1 < posts.length; i++) w.hangLight(b.x((posts[i] + posts[i + 1]) / 2), yr - 1, b.zf - b.in * (balcony ? 2 : 3), "lantern");
    }

    void sideWindows(Bld b, int[] vs, int[] dys) {
        for (int v : vs) for (int dy : dys) {
            k.window(b.x0, b.y + dy, b.z(v), "west", 1, 2, "glass_pane", "spruce_stairs", "spruce_trapdoor");
            k.window(b.x1, b.y + dy, b.z(v), "east", 1, 2, "glass_pane", "spruce_stairs", "spruce_trapdoor");
        }
    }

    /** The saloon: two storeys, swing doors, a balcony over the porch; the bar, tables and a piano below, bunks above. */
    void saloon(Bld b) {
        int h = 9, n = b.n(), d = b.d();
        shell(b, h, Kit.Bands.of(0, "cobblestone", 1, "spruce_planks", 5, "stripped_dark_oak_wood", 6, "spruce_planks"), "stripped_dark_oak_log", P.of("dark_oak_planks"));
        for (int u = 1; u < n; u++) for (int v = 1; v < d; v++) b.set(u, 5, v, "spruce_planks");
        int yTop = b.y + h + 1;
        int smax = slabRoof(b, yTop, "spruce_planks", "dark_oak_slab");
        falseFront(b, h, smax, "spruce_planks", "stripped_dark_oak_log", "stripped_dark_oak_wood", "dark_oak_planks", "dark_oak_stairs", "dark_oak_slab", "dark_oak", "5:SALOON");
        porch(b, 5, true, -1, 3, 7, 11);
        for (int u = 4; u <= 6; u++) for (int dy = 1; dy <= 2; dy++) b.set(u, dy, 0, "air");
        b.set(4, 1, 0, "spruce_trapdoor[facing=east,open=true,half=top]");
        b.set(6, 1, 0, "spruce_trapdoor[facing=west,open=true,half=top]");
        for (int u = 3; u <= 7; u++) b.set(u, 3, 0, "stripped_dark_oak_log[axis=x]");
        for (int u : new int[] {1, 2, 8, 9}) for (int dy = 1; dy <= 2; dy++) b.set(u, dy, 0, "glass_pane");
        b.set(5, 6, 0, b.door("spruce", false, "left"));
        b.set(5, 7, 0, b.door("spruce", true, "left"));
        for (int u : new int[] {2, 8}) k.window(b.x(u), b.y + 6, b.zf, b.out, 1, 2, "glass_pane", null, "spruce_trapdoor");
        sideWindows(b, new int[] {3, 6}, new int[] {2, 6});
        b.set(2, 1, d, "spruce_door[facing=" + b.out + ",half=lower,hinge=left,open=false]");
        b.set(2, 2, d, "spruce_door[facing=" + b.out + ",half=upper,hinge=left,open=false]");
        for (int u : new int[] {3, 7}) k.window(b.x(u), b.y + 6, b.zb, b.back, 1, 2, "glass_pane", "spruce_stairs", "spruce_trapdoor");
        for (int yy = b.y - 1; yy <= yTop + smax / 2 + 1; yy++) b.set(8, yy - b.y, d + 1, yy <= b.y + 1 ? "cobblestone" : "bricks");
        // inside, downstairs: the stair up along the east wall, the bar across the back, tables, a piano
        for (int i = 0; i < 5; i++) {
            int v = 6 - i, dy = 1 + i;
            b.set(9, dy, v, "spruce_stairs[facing=" + b.out + ",half=bottom]");
            for (int f = 1; f < dy; f++) b.set(9, f, v, "spruce_planks");
        }
        for (int v = 3; v <= 6; v++) b.set(9, 5, v, "air");
        for (int v = 3; v <= 6; v++) b.set(8, 6, v, "spruce_fence");
        b.set(9, 6, 7, "spruce_fence");
        String[] onBar = {"decorated_pot", "brewing_stand", "potted_cactus", "brewing_stand", "candle[candles=3]", "decorated_pot"};
        for (int u = 1; u <= 6; u++) {
            b.set(u, 1, 6, "stripped_spruce_wood");
            b.set(u, 1, 8, "barrel[facing=" + b.out + "]");
            b.set(u, 2, 8, onBar[u - 1]);
            b.set(u, 3, 8, "spruce_trapdoor[facing=" + b.out + ",half=top,open=false]");
        }
        b.set(2, 2, 6, "brewing_stand");
        b.set(5, 2, 6, "lantern");
        b.set(7, 1, 8, "smoker[facing=" + b.out + ",lit=true]");
        for (int u : new int[] {2, 4, 6}) {
            b.set(u, 1, 5, "spruce_fence");
            b.set(u, 2, 5, "red_carpet");
        }
        for (int[] tb : new int[][] {{2, 2}, {7, 2}}) {
            b.set(tb[0], 1, tb[1], "spruce_fence");
            b.set(tb[0], 2, tb[1], "spruce_pressure_plate");
            b.set(tb[0] - 1, 1, tb[1], "spruce_stairs[facing=west]");
            b.set(tb[0] + 1, 1, tb[1], "spruce_stairs[facing=east]");
            b.set(tb[0], 1, tb[1] + 1, "spruce_stairs[facing=" + b.back + "]");
        }
        b.set(1, 1, 4, "note_block");
        b.set(1, 2, 4, "jukebox");
        b.set(2, 1, 4, "spruce_stairs[facing=east]");
        for (int v = 1; v <= 4; v++) b.set(5, 1, v, "red_carpet");
        b.hang(3, 4, 3);
        b.hang(7, 4, 4);
        b.hang(4, 4, 7);
        // upstairs: a landing along the front to the balcony, bunks behind a partition
        for (int u = 1; u <= 7; u++) for (int dy = 6; dy <= 9; dy++) b.set(u, dy, 3, "spruce_planks");
        b.set(4, 6, 3, "spruce_door[facing=" + b.back + ",half=lower,hinge=left,open=false]");
        b.set(4, 7, 3, "spruce_door[facing=" + b.back + ",half=upper,hinge=left,open=false]");
        for (int u : new int[] {1, 3, 6}) k.bed(b.x(u), b.y + 6, b.z(7), b.back, "red");
        b.set(2, 6, 8, "barrel[facing=up]");
        b.set(5, 6, 8, "chest[facing=" + b.out + "]");
        b.hang(5, 8, 2);
        b.hang(4, 8, 6);
    }

    /** The general store: a tall shop front of display windows, a counter, shelves of goods. */
    void store(Bld b) {
        int h = 6, n = b.n(), d = b.d();
        shell(b, h, Kit.Bands.of(0, "cobblestone", 1, "oak_planks", 4, "stripped_dark_oak_wood", 5, "oak_planks"), "stripped_dark_oak_log", P.of("spruce_planks"));
        int yTop = b.y + h + 1;
        int smax = slabRoof(b, yTop, "oak_planks", "spruce_slab");
        falseFront(b, h, smax, "oak_planks", "stripped_dark_oak_log", "stripped_dark_oak_wood", "dark_oak_planks", "dark_oak_stairs", "dark_oak_slab", "dark_oak", "4:GENERAL", "5:STORE");
        porch(b, 4, false, -1, 3, 6, 10);
        String l = b.back.equals("south") ? "right" : "left", r = b.back.equals("south") ? "left" : "right";
        b.set(4, 1, 0, b.door("oak", false, l));
        b.set(4, 2, 0, b.door("oak", true, l));
        b.set(5, 1, 0, b.door("oak", false, r));
        b.set(5, 2, 0, b.door("oak", true, r));
        for (int u = 3; u <= 6; u++) b.set(u, 3, 0, "stripped_dark_oak_log[axis=x]");
        for (int u : new int[] {1, 2, 7, 8}) for (int dy = 1; dy <= 3; dy++) b.set(u, dy, 0, "glass_pane");
        sideWindows(b, new int[] {3, 6}, new int[] {2});
        b.set(7, 1, d, "oak_door[facing=" + b.out + ",half=lower,hinge=left,open=false]");
        b.set(7, 2, d, "oak_door[facing=" + b.out + ",half=upper,hinge=left,open=false]");
        for (int u = 1; u <= 6; u++) b.set(u, 1, 4, "stripped_dark_oak_wood");
        b.set(1, 2, 4, "lantern");
        b.set(3, 2, 4, "potted_cactus");
        b.set(5, 2, 4, "decorated_pot");
        String[][] backWall = {{"barrel[facing=" + b.out + "]", "barrel[facing=" + b.out + "]"}, {"bookshelf", "bookshelf", "bookshelf"},
                {"chest[facing=" + b.out + "]", "barrel[facing=" + b.out + "]"}, {"barrel[facing=" + b.out + "]", "barrel[facing=" + b.out + "]"},
                {"chest[facing=" + b.out + "]", "barrel[facing=" + b.out + "]"}, {"bookshelf", "bookshelf", "bookshelf"}};
        for (int u = 1; u <= 6; u++) for (int i = 0; i < backWall[u - 1].length; i++) b.set(u, 1 + i, d - 1, backWall[u - 1][i]);
        b.set(8, 1, d - 1, "hay_block");
        b.set(8, 2, d - 1, "hay_block");
        for (int v = 1; v <= 3; v++) {
            b.set(1, 1, v, v == 3 ? "barrel[facing=up]" : "composter");
            b.set(1, 3, v, "spruce_slab[type=top]");
            b.set(1, 4, v, v == 1 ? "potted_cactus" : v == 2 ? "potted_dead_bush" : "flower_pot");
        }
        b.set(8, 1, 1, "fletching_table");
        b.set(8, 1, 2, "cartography_table");
        b.set(8, 1, 3, "loom[facing=west]");
        b.hang(3, 5, 2);
        b.hang(6, 5, 2);
        b.hang(4, 5, d - 2);
    }

    /** The sheriff's office: sandstone, a desk and wanted posters in front, two barred cells behind. */
    void sheriff(Bld b) {
        int h = 5, n = b.n(), d = b.d();
        shell(b, h, Kit.Bands.of(0, "cut_sandstone", 1, "smooth_sandstone", 5, "cut_sandstone"), "cut_sandstone", P.of("spruce_planks"));
        int yTop = b.y + h + 1;
        int smax = slabRoof(b, yTop, "smooth_sandstone", "spruce_slab");
        falseFront(b, h, smax, "smooth_sandstone", "cut_sandstone", "cut_sandstone", "smooth_red_sandstone", "sandstone_stairs", "smooth_sandstone_slab", "dark_oak", "4:SHERIFF");
        porch(b, 4, false, -1, 3, 5, 9);
        b.set(4, 1, 0, b.door("spruce", false, "left"));
        b.set(4, 2, 0, b.door("spruce", true, "left"));
        b.set(4, 3, 0, "chiseled_sandstone");
        for (int u : new int[] {1, 2, 6, 7}) for (int dy = 1; dy <= 2; dy++) b.set(u, dy, 0, "glass_pane");
        sideWindows(b, new int[] {2}, new int[] {2});
        for (int u : new int[] {2, 6}) b.set(u, 3, d, "iron_bars");
        b.set(2, 1, 2, "stripped_spruce_wood");
        b.set(3, 1, 2, "stripped_spruce_wood");
        b.set(2, 2, 2, "lantern");
        b.set(2, 1, 3, "spruce_stairs[facing=" + b.back + "]");
        b.set(1, 1, 1, "chest[facing=east]");
        String[][] posters = {{"WANTED", "Flag Thief", "Dead or Alive", "$1,000"}, {"WANTED", "Bridge Builder", "", "$500"}};
        for (int i = 0; i < 2; i++) {
            b.set(n - 1, 2, 1 + i, "spruce_wall_sign[facing=west]");
            w.signText(b.x(n - 1), b.y + 2, b.z(1 + i), false, posters[i]);
        }
        for (int u = 1; u < n; u++) for (int dy = 1; dy <= 4; dy++) b.set(u, dy, 4, "iron_bars");
        for (int v = 5; v < d; v++) for (int dy = 1; dy <= 4; dy++) b.set(4, dy, v, "iron_bars");
        b.set(2, 1, 4, "iron_door[facing=" + b.back + ",half=lower,hinge=left,open=false]");
        b.set(2, 2, 4, "iron_door[facing=" + b.back + ",half=upper,hinge=left,open=false]");
        b.set(6, 1, 4, "iron_door[facing=" + b.back + ",half=lower,hinge=left,open=true]");
        b.set(6, 2, 4, "iron_door[facing=" + b.back + ",half=upper,hinge=left,open=true]");
        k.bed(b.x(1), b.y + 1, b.z(5), b.back, "white");
        k.bed(b.x(7), b.y + 1, b.z(5), b.back, "white");
        b.hang(4, 4, 2);
        b.hang(2, 4, 5);
        b.hang(6, 4, 5);
    }

    /** The blacksmith: an open-fronted shed with a forge and chimney, an anvil and a grindstone. */
    void smithy(Bld b) {
        int h = 5, n = b.n(), d = b.d();
        for (int u = 0; u <= n; u++) for (int v = 0; v <= d; v++) {
            b.set(u, 0, v, P.of("cobblestone", 3, "packed_mud", 2, "gravel", 1).pick(w.r));
            for (int dy = 1; dy <= h + 1; dy++) b.set(u, dy, v, "air");
        }
        for (int u = 0; u <= n; u++) for (int v = 0; v <= d; v++) {
            boolean wall = u == 0 || u == n || v == d;
            if (!wall) continue;
            boolean post = (u == 0 || u == n) && (v == 0 || v == d);
            for (int dy = 1; dy <= h; dy++) b.set(u, dy, v, post ? "stripped_spruce_log[axis=y]" : dy <= 2 ? "cobblestone" : "spruce_planks");
        }
        for (int u : new int[] {3, 6}) for (int dy = 1; dy <= h; dy++) b.set(u, dy, 0, "stripped_spruce_log[axis=y]");
        for (int u = 0; u <= n; u++) b.set(u, h, 0, "stripped_spruce_log[axis=x]");
        k.gable(b.x0, Math.min(b.zf, b.zb), b.x1, Math.max(b.zf, b.zb), b.y + h + 1, true, 1, Kit.Roof.of("spruce_planks"), P.of("spruce_planks"));
        b.set(1, 1, d - 1, "bricks");
        b.set(2, 1, d - 1, "blast_furnace[facing=" + b.out + ",lit=true]");
        b.set(3, 1, d - 1, "furnace[facing=" + b.out + ",lit=true]");
        for (int u = 1; u <= 3; u++) {
            b.set(u, 2, d - 1, "brick_slab[type=bottom]");
            b.set(u, 3, d - 1, "bricks");
            b.set(u, 4, d - 1, "bricks");
        }
        for (int dy = 1; dy <= h + 6; dy++) b.set(2, dy, d, "bricks");
        b.set(5, 1, 2, "anvil[facing=east]");
        b.set(7, 1, 2, "grindstone[face=floor,facing=" + b.out + "]");
        b.set(5, 1, d - 1, "water_cauldron[level=3]");
        b.set(7, 1, d - 1, "smithing_table");
        b.hang(5, 4, 2);
        b.set(4, h - 1, 0, "spruce_hanging_sign[rotation=" + (b.out.equals("north") ? 8 : 0) + "]");
        w.signText(b.x(4), b.y + h - 1, b.z(0), true, "", "BLACKSMITH", "", "");
    }

    /** The depot: a waiting room and ticket office under a gable, its back onto the platform under a deep awning. */
    void depot() {
        int x0 = -71, x1 = -61, zf = SZ + 6, zb = RAIL_Z - 4, y = TOWN + 1, h = 5;
        k.walls(x0, zf, x1, zb, y, h, Kit.Bands.of(0, "dark_oak_planks", 1, "spruce_planks", 3, "stripped_spruce_wood", 4, "spruce_planks"),
                "stripped_dark_oak_log", 4, P.of("spruce_planks"));
        k.gable(x0, zf, x1, zb, y + h + 1, true, 1, Kit.Roof.of("dark_oak_planks"), P.of("spruce_planks"));
        int cx = (x0 + x1) / 2;
        // doors front and back, windows, the name board on the street gable
        for (int z : new int[] {zf, zb}) {
            String face = z == zf ? "north" : "south";
            w.set(cx, y + 1, z, "spruce_door[facing=" + (z == zf ? "south" : "north") + ",half=lower,hinge=left,open=false]");
            w.set(cx, y + 2, z, "spruce_door[facing=" + (z == zf ? "south" : "north") + ",half=upper,hinge=left,open=false]");
            for (int x : new int[] {x0 + 2, x1 - 2}) k.window(x, y + 2, z, face, 1, 2, "glass_pane", null, "spruce_trapdoor");
        }
        w.set(cx, y + 4, zf - 1, "dark_oak_wall_sign[facing=north]");
        w.signText(cx, y + 4, zf - 1, true, "", "DUSTBOWL", "elev. 72", "");
        // inside: the ticket office behind a grille, benches, a stove
        for (int x = x0 + 1; x <= x0 + 3; x++) {
            w.set(x, y + 1, zf + 3, "stripped_spruce_wood");
            w.set(x, y + 2, zf + 3, x == x0 + 2 ? "air" : "iron_bars");
        }
        w.set(x0 + 1, y + 1, zf + 1, "lectern[facing=south]");
        w.set(x0 + 2, y + 1, zf + 4, "chest[facing=north]");
        for (int x = cx + 1; x <= x1 - 1; x++) w.set(x, y + 1, zf + 1, "spruce_stairs[facing=south]");
        for (int x = cx + 1; x <= x1 - 1; x++) w.set(x, y + 1, zb - 1, "spruce_stairs[facing=north]");
        w.set(x1 - 1, y + 1, zf + 3, "smoker[facing=west,lit=true]");
        w.hangLight(cx - 2, y + 4, zf + 2, "lantern");
        w.hangLight(cx + 2, y + 4, zb - 2, "lantern");
        // the awning over the platform: slabs out to posts at the platform's edge
        for (int x = x0 - 1; x <= x1 + 1; x++) for (int z = zb + 1; z <= RAIL_Z - 1; z++)
            w.set(x, y + h - 1, z, z == RAIL_Z - 1 ? "stripped_spruce_log[axis=x]" : "spruce_slab[type=top]");
        for (int x = x0; x <= x1; x += 5) for (int dy = 0; dy < h - 1; dy++) w.set(x, y + dy, RAIL_Z - 1, "stripped_spruce_log[axis=y]");
        w.hangLight(cx, y + h - 2, RAIL_Z - 2, "lantern");
        w.set(cx - 3, y + h - 2, zb + 1, "dark_oak_wall_sign[facing=south]");
        w.signText(cx - 3, y + h - 2, zb + 1, true, "<- FORT", "", "BRIDGE ->", "");
    }

    /** The platform: a half step up from the ground, from the depot's west end to the water tower. */
    void platform() {
        for (int x = -71; x <= -55; x++) for (int z = RAIL_Z - 3; z <= RAIL_Z - 1; z++) {
            if (land.topAt(x, z) != TOWN || !w.air(x, TOWN + 1, z)) continue;
            w.set(x, TOWN, z, "spruce_planks");
            w.set(x, TOWN + 1, z, z == RAIL_Z - 1 ? "smooth_stone_slab[type=bottom]" : "spruce_slab[type=bottom]");
        }
        for (int x = -68; x <= -58; x += 5) {
            w.set(x, TOWN + 2, RAIL_Z - 3, "barrel[facing=up]");
        }
        w.set(-57, TOWN + 2, RAIL_Z - 2, "hay_block");
    }

    /** The line: buffers at the foot of the cliff, along the rim past the platform, round onto the bridge and over. */
    void railroad() {
        int y = TOWN + 1;
        for (int x = SCARP + 2; x < BX; x++) {
            w.set(x, TOWN, RAIL_Z, "gravel");
            w.set(x, y, RAIL_Z, "rail[shape=east_west]");
            for (int dy = 1; dy <= 3; dy++) if (!w.air(x, y + dy, RAIL_Z) && w.type(x, y + dy, RAIL_Z) != Material.SPRUCE_SLAB) w.set(x, y + dy, RAIL_Z, "air");
        }
        w.set(SCARP + 1, y, RAIL_Z, "spruce_fence");
        w.set(SCARP + 1, y + 1, RAIL_Z, "red_terracotta");
        w.set(BX, TOWN, RAIL_Z, "gravel");
        w.set(BX, y, RAIL_Z, "rail[shape=south_west]");
        for (int z = RAIL_Z + 1; z <= 8; z++) w.set(BX, y, z, "rail[shape=north_south]");
        // the spur on the far side: off the other bridge (its copy lands here) round onto the bench, to the pen
        int sx = -BX;
        for (int z = -9; z >= -14; z--) {
            w.set(sx, BENCH, z, "gravel");
            w.set(sx, BENCH + 1, z, "rail[shape=north_south]");
        }
        w.set(sx, BENCH, -15, "gravel");
        w.set(sx, BENCH + 1, -15, "rail[shape=south_east]");
        for (int x = sx + 1; x <= sx + 11; x++) {
            w.set(x, BENCH, -15, "gravel");
            w.set(x, BENCH + 1, -15, "rail[shape=east_west]");
        }
        w.set(sx + 12, BENCH + 1, -15, "spruce_fence");
        w.set(sx + 12, BENCH + 2, -15, "red_terracotta");
    }

    /**
     * A locomotive at the end of the line, facing the bridge: tender, cab, a black boiler on red wheels with
     * a smokestack (smoke from a campfire in its top), a bell, a headlamp and a cowcatcher.
     */
    void locomotive() {
        int y = TOWN + 1, z = RAIL_Z;
        int t0 = -84, c0 = -80, b0 = -77, f = -72;
        // wheels either side of the rail, rods between them
        for (int x = t0; x <= f - 1; x++) for (int s : new int[] {-1, 1}) {
            boolean wheel = x == t0 || x == t0 + 2 || x == c0 + 1 || x == b0 || x == b0 + 2 || x == f - 1;
            w.set(x, y, z + s, wheel ? (x >= b0 ? "red_terracotta" : "polished_blackstone_wall") : x >= c0 ? "iron_chain[axis=x]" : "air");
        }
        // the frame: a running board along the whole engine
        for (int x = t0; x <= f; x++) for (int s = -1; s <= 1; s++) w.set(x, y + 1, z + s, x < c0 ? "dark_oak_planks" : "polished_blackstone_slab[type=bottom]");
        // the tender: coal in a timber box
        for (int x = t0; x < c0; x++) for (int s = -1; s <= 1; s++) {
            boolean edge = s != 0 || x == t0 || x == c0 - 1;
            w.set(x, y + 2, z + s, edge ? "dark_oak_planks" : "coal_block");
            if (edge) w.set(x, y + 3, z + s, x == t0 || x == c0 - 1 ? "dark_oak_slab[type=bottom]" : "TEAM_terracotta");
            else w.set(x, y + 3, z + s, "coal_block");
        }
        // the cab: walls with windows, a roof overhanging the sides
        for (int x = c0; x < b0; x++) for (int s = -1; s <= 1; s += 2) {
            w.set(x, y + 2, z + s, "TEAM_terracotta");
            w.set(x, y + 3, z + s, x == c0 + 1 ? "glass_pane" : "dark_oak_planks");
            w.set(x, y + 4, z + s, "dark_oak_planks");
        }
        for (int s = -1; s <= 1; s++) for (int dy = 2; dy <= 4; dy++) w.set(c0, y + dy, z + s, s == 0 && dy < 4 ? "air" : "dark_oak_planks");
        for (int x = c0 - 1; x < b0; x++) for (int s = -2; s <= 2; s++) w.set(x, y + 5, z + s, "dark_oak_slab[type=bottom]");
        w.set(c0 + 1, y + 2, z, "furnace[facing=east,lit=true]");
        w.hangLight(c0 + 1, y + 4, z, "lantern");
        // the boiler: a black drum, rounded at its corners, banded, domes and the stack on top
        for (int x = b0; x <= f - 1; x++) {
            boolean band = (x - b0) % 2 == 1;
            String body = band ? "polished_blackstone" : "blackstone";
            w.set(x, y + 2, z, body);
            w.set(x, y + 3, z, body);
            w.set(x, y + 4, z, body);
            for (int s : new int[] {-1, 1}) {
                String side = s < 0 ? "north" : "south";
                w.set(x, y + 2, z + s, "polished_blackstone_stairs[facing=" + Kit.opp(side) + ",half=top]");
                w.set(x, y + 3, z + s, body);
                w.set(x, y + 4, z + s, "polished_blackstone_stairs[facing=" + Kit.opp(side) + ",half=bottom]");
            }
        }
        w.set(b0 + 1, y + 5, z, "gold_block");
        w.set(b0 + 3, y + 5, z, "polished_blackstone_wall");
        w.set(b0 + 2, y + 5, z, "bell[attachment=floor,facing=north]");
        for (int dy = 5; dy <= 7; dy++) w.set(f - 1, y + dy, z, dy == 7 ? "campfire[lit=true,signal_fire=false]" : "polished_blackstone_wall");
        w.set(f - 2, y + 7, z, "polished_blackstone_slab[type=bottom]");
        w.set(f, y + 7, z, "polished_blackstone_slab[type=bottom]");
        w.set(f - 1, y + 7, z - 1, "polished_blackstone_slab[type=bottom]");
        w.set(f - 1, y + 7, z + 1, "polished_blackstone_slab[type=bottom]");
        // the smokebox front, the headlamp, the cowcatcher
        for (int s = -1; s <= 1; s++) for (int dy = 2; dy <= 4; dy++) w.set(f, y + dy, z + s, s == 0 && dy == 4 ? "ochre_froglight" : "polished_blackstone");
        for (int s = -1; s <= 1; s++) w.set(f + 1, y, z + s, "iron_bars");
        w.set(f + 1, y + 1, z, "polished_blackstone_stairs[facing=west,half=bottom]");
        keepClear(t0 - 1, z - 3, f + 2, z + 3);
    }

    /** A water tower: four legs braced across, a deck, a round tank with a pointed lid, a spout swung over the rail. */
    void waterTower(int cx, int cz) {
        int y = TOWN;
        for (int[] l : new int[][] {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) for (int dy = 1; dy <= 9; dy++) w.set(cx + l[0], y + dy, cz + l[1], "stripped_spruce_log[axis=y]");
        for (int o = -1; o <= 1; o++) for (int s : new int[] {-2, 2}) {
            w.set(cx + o, y + 5, cz + s, "stripped_spruce_log[axis=x]");
            w.set(cx + s, y + 5, cz + o, "stripped_spruce_log[axis=z]");
        }
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) w.set(cx + dx, y + 10, cz + dz, "spruce_planks");
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            double d = Math.hypot(dx, dz);
            if (d > 2.4) continue;
            for (int dy = 11; dy <= 14; dy++) w.set(cx + dx, y + dy, cz + dz, d > 1.4 ? (dy == 12 ? "stripped_spruce_wood" : "spruce_planks") : dy < 14 ? "water" : "spruce_planks");
        }
        k.cone(cx, cz, y + 15, 2, 1, 1.0, Kit.Roof.of("spruce_planks"));
        for (int z = cz + 3; z <= RAIL_Z - 1; z++) w.set(cx, y + 9, z, z == RAIL_Z - 1 ? "iron_chain[axis=y]" : "stripped_spruce_log[axis=z]");
        w.set(cx, y + 8, RAIL_Z - 1, "iron_chain[axis=y]");
        keepClear(cx - 4, cz - 4, cx + 4, cz + 4);
    }

    void outhouse(int cx, int cz, int y) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            boolean wall = dx != 0 || dz != 0;
            for (int dy = 1; dy <= 3; dy++) if (wall) w.set(cx + dx, y + dy, cz + dz, Math.abs(dx) == 1 && Math.abs(dz) == 1 ? "stripped_spruce_log[axis=y]" : "spruce_planks");
            w.set(cx + dx, y + 4, cz + dz, "spruce_slab[type=bottom]");
        }
        w.set(cx, y + 1, cz + 1, "spruce_door[facing=south,half=lower,hinge=left,open=false]");
        w.set(cx, y + 2, cz + 1, "spruce_door[facing=south,half=upper,hinge=left,open=false]");
        w.set(cx, y + 3, cz + 1, "spruce_trapdoor[facing=north,half=top,open=true]");
    }

    /** A covered wagon: wheels, a slab bed, hoops of canvas; a broken one has half its canvas fallen in. */
    void wagon(int cx, int cz, int y, boolean broken) {
        for (int x = cx - 2; x <= cx + 3; x++) for (int z = cz - 1; z <= cz + 1; z++) w.set(x, y, z, "spruce_slab[type=top]");
        for (int x : new int[] {cx - 1, cx + 2}) {
            w.set(x, y, cz - 2, "spruce_trapdoor[facing=north,open=true,half=bottom]");
            if (!broken || x != cx + 2) w.set(x, y, cz + 2, "spruce_trapdoor[facing=south,open=true,half=bottom]");
        }
        int len = broken ? 0 : 3;
        for (int x = cx - 2; x <= cx + len; x++) {
            for (int dy = 1; dy <= 2; dy++) {
                w.set(x, y + dy, cz - 1, "white_wool");
                w.set(x, y + dy, cz + 1, "white_wool");
            }
            for (int z = cz - 1; z <= cz + 1; z++) w.set(x, y + 3, z, "white_wool");
        }
        if (!broken) for (int x : new int[] {cx - 2, cx + 3}) w.set(x, y + 1, cz, "barrel[facing=" + (x < cx ? "west" : "east") + "]");
        else {
            w.set(cx + 1, y + 1, cz - 1, "white_wool");
            w.set(cx + 2, y + 1, cz + 1, "barrel[facing=up]");
        }
        w.set(cx + 4, y, cz, "spruce_slab[type=bottom]");
        w.set(cx + 5, y, cz, "spruce_slab[type=bottom]");
        keepClear(cx - 3, cz - 3, cx + 6, cz + 3);
    }

    // ================================================================ the bridge

    /**
     * A timber truss bridge carrying the line over the gorge from the town to the far end of the other mesa: a
     * plank deck between two lattice trusses (bottom and top chords, posts every four blocks, crossed braces),
     * portal beams overhead, girders and floor beams underneath, a sign over each portal. Its copy is the other
     * bridge.
     */
    void trussBridge() {
        int x = BX, y = TOWN, za = -15, zb = 9;
        for (int z = za; z <= zb; z++) {
            boolean land = onMesa(x, z) && land(x, z) >= TOWN - 1;
            for (int dx = -2; dx <= 2; dx++) {
                boolean side = Math.abs(dx) == 2;
                if (!side) {
                    w.set(x + dx, y, z, dx == 0 ? "spruce_planks" : "stripped_spruce_wood");
                    for (int dy = 1; dy <= 4; dy++) if (!(dx == 0 && dy == 1)) w.set(x + dx, y + dy, z, "air");
                    if (!land) w.set(x + dx, y - 1, z, (z & 1) == 0 ? "stripped_spruce_log[axis=x]" : "air");
                }
            }
            if (!land) for (int dx : new int[] {-1, 1}) w.set(x + dx, y - 1, z, "stripped_spruce_log[axis=z]");
        }
        // the trusses: chords, posts every four blocks, crossed braces between
        int t0 = za + 2, t1 = zb - 2;
        for (int s : new int[] {-2, 2}) {
            for (int z = t0; z <= t1; z++) {
                w.set(x + s, y, z, "stripped_spruce_log[axis=z]");
                w.set(x + s, y + 4, z, "stripped_spruce_log[axis=z]");
                int p = Math.floorMod(z - t0, 4);
                if (p == 0) for (int dy = 1; dy <= 3; dy++) w.set(x + s, y + dy, z, "stripped_spruce_log[axis=y]");
                else {
                    for (int dy = 1; dy <= 3; dy++) w.set(x + s, y + dy, z, "air");
                    w.set(x + s, y + p, z, "spruce_fence");
                    w.set(x + s, y + 4 - p, z, "spruce_fence");
                }
            }
            for (int dy = 1; dy <= 4; dy++) {
                w.set(x + s, y + dy, t0 - 1, "air");
                w.set(x + s, y + dy, t1 + 1, "air");
            }
            w.set(x + s, y + 5, t0, "spruce_slab[type=bottom]");
            w.set(x + s, y + 5, t1, "spruce_slab[type=bottom]");
        }
        // portal and sway beams overhead, lanterns under them
        for (int z = t0; z <= t1; z += 4) {
            for (int dx = -1; dx <= 1; dx++) w.set(x + dx, y + 4, z, "stripped_spruce_log[axis=x]");
            if (z != t0 && z != t1) w.hangLight(x, y + 3, z, "lantern");
        }
        for (int z : new int[] {t0, t1}) {
            boolean north = z == t0;
            w.set(x, y + 3, z, "spruce_hanging_sign[attached=true,rotation=" + (north ? 8 : 0) + "]");
            w.signText(x, y + 3, z, true, "", "D & W", "RAILROAD", "");
        }
        // cribbing under the ends where they sit on the rims
        for (int z = za; z <= zb; z++) {
            if (!(onMesa(x, z) && land(x, z) >= TOWN - 1)) continue;
            for (int dx = -2; dx <= 2; dx++) if (w.air(x + dx, y + 1, z) && Math.abs(dx) == 2 && (z < t0 || z > t1)) w.set(x + dx, y + 1, z, "spruce_fence");
        }
        keepClear(x - 4, za - 2, x + 4, zb + 2);
        keepClear(-x - 4, -zb - 2, -x + 4, -za + 2);
    }

    int land(int x, int z) { return land.topAt(x, z); }

    // ================================================================ the arch zone

    /** The butte: a rock-cut stair up its outer flank to a lookout on top (a shade, sandbags, a spyglass on a post). */
    void butte() {
        int z0 = (int) Math.round(BUTTE_Z - BUTTE_RZ * 0.62), rail = z0 - 2;
        int bx0 = (int) Math.round(BUTTE_X) - 12;
        int steps = BUTTE_TOP - ARCH;
        for (int i = 1; i <= steps; i++) {
            int x = bx0 + i, sy = ARCH + i;
            for (int z = z0 - 1; z <= z0; z++) {
                for (int yy = ARCH; yy < sy; yy++) w.set(x, yy, z, rock(x, yy, z));
                w.set(x, sy, z, "smooth_red_sandstone_stairs[facing=east,half=bottom]");
                for (int dy = 1; dy <= 4; dy++) w.set(x, sy + dy, z, "air");
            }
            if (i >= 2) for (int yy = ARCH + 1; yy <= sy + 1; yy++) w.set(x, yy, rail, rock(x, yy, rail));
        }
        int tx = bx0 + steps + 1;
        for (int x = tx; x <= tx + 1; x++) for (int z = z0 - 1; z <= z0; z++) {
            w.set(x, BUTTE_TOP, z, "red_sand");
            for (int dy = 1; dy <= 4; dy++) w.set(x, BUTTE_TOP + dy, z, "air");
        }
        // the lookout: four posts, a slab shade, sandbags round three sides, open to the stair
        int lx = (int) Math.round(BUTTE_X) + 4, lz = (int) Math.round(BUTTE_Z) - 1, y = BUTTE_TOP;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            if (land(lx + dx, lz + dz) != BUTTE_TOP) continue;
            boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
            if (corner) for (int dy = 1; dy <= 3; dy++) w.set(lx + dx, y + dy, lz + dz, "stripped_spruce_log[axis=y]");
            w.set(lx + dx, y + 4, lz + dz, corner ? "stripped_spruce_log[axis=y]" : "spruce_slab[type=bottom]");
            boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
            if (edge && !corner && dx != -2 && (dx + dz & 1) == 0) w.set(lx + dx, y + 1, lz + dz, "packed_mud");
        }
        w.set(lx + 1, y + 1, lz, "spruce_fence");
        w.set(lx + 1, y + 2, lz, "TEAM_banner[rotation=4]");
        w.hangLight(lx, y + 3, lz, "lantern");
        keepClear(bx0 - 1, (int) (BUTTE_Z - BUTTE_RZ - 2), (int) (BUTTE_X + BUTTE_RX + 1), (int) (BUTTE_Z + BUTTE_RZ + 2));
    }

    /** The slot canyon's floor: sand drifted in, a few fallen blocks, light falling through. */
    void slotCanyon() {
        for (int x = (int) (BUTTE_X - BUTTE_RX); x <= BUTTE_X + BUTTE_RX; x++) for (int dz = -2; dz <= 2; dz++) {
            int z = (int) Math.round(slotZ(x)) + dz;
            if (land(x, z) != ARCH) continue;
            boolean walled = land(x, z - 2) > ARCH + 4 || land(x, z + 2) > ARCH + 4 || land(x, z - 3) > ARCH + 4 || land(x, z + 3) > ARCH + 4;
            if (!walled) continue;
            w.set(x, ARCH, z, w.n2(x * 2.3, z * 2.3, 2) > 0.3 ? "sand" : "red_sand");
        }
    }

    /** Rocks and dead wood round the Arch's ends: cover for whoever holds the span. */
    void archEnds() {
        int[][] rocks = {{-13, -13, 2}, {10, -15, 1}, {-7, -20, 1}};
        for (int[] r : rocks) boulder(r[0], r[1], r[2]);
        flora.tree(Theme.Tree.DEAD, -16, ARCH + 1, -19);
        skeleton(-4, -27, ARCH + 1);
        wagon(4, -30, ARCH + 1, true);
    }

    /** A boulder fallen from a cliff: an oval heap of rock a block or three high. */
    void boulder(int cx, int cz, int size) {
        double rx = 1.6 + size * 0.5, rz = 1.3 + size * 0.4;
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            double e = sq(dx / rx) + sq(dz / rz);
            if (e > 1) continue;
            int x = cx + dx, z = cz + dz, gy = land(x, z);
            if (gy == Integer.MIN_VALUE || !w.air(x, gy + 1, z) && w.type(x, gy + 1, z).isSolid()) continue;
            int hgt = (int) Math.round((1 - e) * (size + 1) + 0.3);
            for (int dy = 1; dy <= hgt; dy++) w.set(x, gy + dy, z, rock(x, gy + dy, z));
            if (hgt == 0) w.set(x, gy + 1, z, "smooth_red_sandstone_slab[type=bottom]");
        }
        keepClear(cx - 4, cz - 4, cx + 4, cz + 4);
    }

    /** The bleached bones of a steer half sunk in the sand. */
    void skeleton(int cx, int cz, int y) {
        for (int x = cx - 2; x <= cx + 1; x++) w.set(x, y, cz, "bone_block[axis=x]");
        for (int x : new int[] {cx - 1, cx + 1}) {
            w.set(x, y, cz - 1, "bone_block[axis=z]");
            w.set(x, y, cz + 1, "bone_block[axis=z]");
        }
        w.set(cx + 2, y, cz, "skeleton_skull[rotation=12]");
        keepClear(cx - 3, cz - 2, cx + 3, cz + 2);
    }

    // ================================================================ the hoodoo flats

    /** Hoodoos round the hill: columns of strata pinched at the neck under a wider cap of harder rock. */
    void hoodooFlats() {
        int fz = (int) Math.round(midZ(FLATS_X));
        int[][] spots = {{FLATS_X - 8, fz - 9, 15, 26}, {FLATS_X + 7, fz + 8, 12, 24}, {FLATS_X + 6, fz - 12, 10, 21}, {FLATS_X - 7, fz + 10, 11, 22},
                {FLATS_X - 1, fz - 17, 7, 17}, {FLATS_X + 1, fz + 15, 8, 18}};
        for (int[] s : spots) hoodoo(s[0], s[1], s[2], s[3] / 10.0);
        boulder(FLATS_X - 12, fz + 2, 1);
        boulder(FLATS_X + 10, fz - 3, 2);
        skeleton(FLATS_X + 4, fz + 4, FLATS + 1);
        keepClear(FLATS_X - 5, fz - 5, FLATS_X + 5, fz + 5);
    }

    void hoodoo(int cx, int cz, int h, double r0) {
        int gy = land(cx, cz);
        if (gy == Integer.MIN_VALUE) return;
        for (int dy = 1; dy <= h + 1; dy++) {
            double t = dy / (double) h;
            double rr = dy > h - 1 ? r0 * 1.25 : r0 * (1 - 0.3 * t) * (t > 0.7 ? 0.75 : 1);
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
                if (Math.hypot(dx, dz * 1.1) > rr + w.n2((cx + dx) * 3, (cz + dz + dy) * 3, 2) * 0.25) continue;
                int x = cx + dx, z = cz + dz;
                if (dy == 1 && land(x, z) < gy) continue;
                String spec = dy > h - 1 ? (dy == h + 1 ? "smooth_red_sandstone_slab[type=bottom]" : "cut_red_sandstone") : rock(x, gy + dy, z);
                w.set(x, gy + dy, z, spec);
            }
        }
        keepClear(cx - 3, cz - 3, cx + 3, cz + 3);
    }

    // ================================================================ the ranch bench

    /**
     * The ranch on the bench at the far end of the mesa, right under the enemy fort: a windpump over a stock tank,
     * a farmhouse with a porch, a corral, a big barn with doors at both ends (cover all the way to the gorge), a
     * loading pen at the end of the spur.
     */
    void ranch() {
        int y = BENCH;
        // the windpump: a braced lattice tower, a wheel of blades, a tail vane; the tank at its foot
        int wx = 70, wz = (int) Math.round(midZ(70)) - 8;
        for (int dy = 1; dy <= 13; dy++) {
            int o = dy <= 5 ? 1 : 0;
            for (int[] l : new int[][] {{-o, -o}, {o, -o}, {-o, o}, {o, o}}) w.set(wx + l[0], y + dy, wz + l[1], o == 0 ? "stripped_spruce_log[axis=y]" : "spruce_fence");
        }
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (dx != 0 || dz != 0) w.set(wx + dx, y + 6, wz + dz, "spruce_slab[type=bottom]");
        int hy = y + 14;
        w.set(wx, hy, wz, "iron_block");
        for (int dz = -3; dz <= 3; dz++) for (int dy = -3; dy <= 3; dy++) {
            double d = Math.hypot(dz, dy);
            if (d < 1 || d > 3.4) continue;
            boolean blade = Math.abs(dz) == Math.abs(dy) || dz == 0 || dy == 0;
            if (blade) w.set(wx - 1, hy + dy, wz + dz, d > 2.5 ? "iron_trapdoor[facing=east,open=true,half=bottom]" : "iron_bars");
        }
        for (int x = wx + 1; x <= wx + 3; x++) w.set(x, hy, wz, "iron_bars");
        for (int dy = -1; dy <= 1; dy++) w.set(wx + 4, hy + dy, wz, "birch_planks");
        w.set(wx + 5, hy, wz, "birch_planks");
        for (int dx = -2; dx <= 2; dx++) for (int dz = 2; dz <= 5; dz++) {
            boolean rim = Math.abs(dx) == 2 || dz == 2 || dz == 5;
            w.set(wx + dx, y, wz + dz, rim ? "stone_bricks" : "water");
            if (rim) w.set(wx + dx, y + 1, wz + dz, "stone_brick_slab[type=bottom]");
        }
        keepClear(wx - 3, wz - 3, wx + 3, wz + 6);

        farmhouse(58, (int) Math.round(midZ(62)) - 9);
        corral(74, 86, (int) Math.round(midZ(80)) - 6, (int) Math.round(midZ(80)) + 5);
        barn(93, 104, (int) Math.round(midZ(98)) - 5, (int) Math.round(midZ(98)) + 5);
        // the loading pen at the end of the spur, a chute up to the car
        int px = -BX + 6;
        for (int x = px - 3; x <= px + 3; x++) for (int z = -21; z <= -17; z++) {
            boolean edge = Math.abs(x - px) == 3 || z == -21 || z == -17;
            if (edge && !(z == -17 && Math.abs(x - px) <= 1)) w.set(x, y + 1, z, "spruce_fence");
        }
        w.set(px + 3, y + 1, -19, "spruce_fence_gate[facing=east,open=false]");
        w.set(px - 1, y + 1, -20, "hay_block");
        w.set(px, y + 1, -20, "hay_block");
        w.set(px - 1, y + 2, -20, "hay_block");
        keepClear(px - 4, -22, px + 7, -13);
    }

    /** A small frame farmhouse: one storey under a gable, a porch on its rim side, a stovepipe. */
    void farmhouse(int x0, int z0) {
        int x1 = x0 + 8, z1 = z0 + 6, y = BENCH + 1, h = 4;
        k.walls(x0, z0, x1, z1, y, h, Kit.Bands.of(0, "stripped_spruce_wood", 1, "birch_planks", 4, "stripped_spruce_wood"), "stripped_spruce_log", 4, P.of("spruce_planks"));
        k.gable(x0, z0, x1, z1, y + h + 1, true, 1, Kit.Roof.of("spruce_planks"), P.of("birch_planks"));
        int cx = (x0 + x1) / 2;
        w.set(cx, y + 1, z1, "spruce_door[facing=north,half=lower,hinge=left,open=false]");
        w.set(cx, y + 2, z1, "spruce_door[facing=north,half=upper,hinge=left,open=false]");
        for (int x : new int[] {x0 + 2, x1 - 2}) {
            k.window(x, y + 2, z1, "south", 1, 2, "glass_pane", null, "spruce_trapdoor");
            k.window(x, y + 2, z0, "north", 1, 2, "glass_pane", null, "spruce_trapdoor");
        }
        k.window(x0, y + 2, z0 + 3, "west", 1, 2, "glass_pane", null, "spruce_trapdoor");
        k.window(x1, y + 2, z0 + 3, "east", 1, 2, "glass_pane", null, "spruce_trapdoor");
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            w.set(x, y, z1 + 1, "spruce_planks");
            w.set(x, y, z1 + 2, "spruce_planks");
            w.set(x, y + 4, z1 + 1, "spruce_slab[type=top]");
            w.set(x, y + 4, z1 + 2, "spruce_slab[type=top]");
        }
        for (int x : new int[] {x0 - 1, cx - 2, cx + 2, x1 + 1}) for (int dy = 1; dy <= 3; dy++) w.set(x, y + dy, z1 + 2, "stripped_spruce_log[axis=y]");
        for (int x = x0 - 1; x <= x1 + 1; x++) if (w.air(x, y, z1 + 3)) w.set(x, y, z1 + 3, "spruce_slab[type=bottom]");
        w.set(cx - 2, y + 1, z1 + 1, "spruce_stairs[facing=north]");
        w.set(cx + 1, y + 1, z1 + 1, "barrel[facing=up]");
        w.hangLight(cx, y + 3, z1 + 1, "lantern");
        for (int dy = 1; dy <= h + 4; dy++) w.set(x1 - 1, y + dy, z0 + 1, dy <= h ? "air" : "polished_blackstone_wall");
        w.set(x1 - 1, y + 1, z0 + 1, "smoker[facing=west,lit=true]");
        k.bed(x0 + 2, y + 1, z0 + 1, "north", "brown");
        w.set(x0 + 1, y + 1, z0 + 4, "crafting_table");
        w.set(x0 + 4, y + 1, z0 + 3, "spruce_fence");
        w.set(x0 + 4, y + 2, z0 + 3, "spruce_pressure_plate");
        w.hangLight(cx, y + h - 1, z0 + 3, "lantern");
        keepClear(x0 - 2, z0 - 2, x1 + 2, z1 + 4);
    }

    /** A corral: a split-rail fence round a dusty yard, gates on two sides, a trough and hay. */
    void corral(int x0, int x1, int z0, int z1) {
        int y = BENCH;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            if (land(x, z) != BENCH) continue;
            if (w.r.nextInt(3) == 0) w.set(x, y, z, w.r.nextBoolean() ? "coarse_dirt" : "packed_mud");
            boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
            if (!edge) continue;
            boolean gate = (z == z0 || z == z1) && Math.abs(x - (x0 + x1) / 2) <= 1 || (x == x0 || x == x1) && Math.abs(z - (z0 + z1) / 2) <= 1;
            if (gate) continue;
            w.set(x, y + 1, z, "spruce_fence");
        }
        int cz = (z0 + z1) / 2;
        for (int x = x0 + 3; x <= x0 + 6; x++) w.set(x, y + 1, z0 + 2, x == x0 + 3 || x == x0 + 6 ? "spruce_planks" : "water_cauldron[level=3]");
        w.set(x1 - 3, y + 1, cz, "hay_block");
        w.set(x1 - 3, y + 2, cz, "hay_block");
        w.set(x1 - 2, y + 1, cz, "hay_block");
        keepClear(x0 - 1, z0 - 1, x1 + 1, z1 + 1);
    }

    /** The barn: a gambrel roof over plank walls, big doorways at both ends, a hayloft along one side, a weathervane. */
    void barn(int x0, int x1, int z0, int z1) {
        int y = BENCH, h = 6;
        k.walls(x0, z0, x1, z1, y, h, Kit.Bands.of(0, "cobblestone", 1, "red_terracotta"), "stripped_birch_log", 4, P.of("packed_mud", 2, "coarse_dirt", 1));
        int cz = (z0 + z1) / 2, half = (z1 - z0) / 2;
        // the gambrel: steep lower slopes, shallow upper ones, red sides
        for (int x = x0 - 1; x <= x1 + 1; x++) for (int dz = -half - 1; dz <= half + 1; dz++) {
            int a = half + 1 - Math.abs(dz);   // steps in from the eave
            int ry = a <= 2 ? y + h + a * 2 - 1 : y + h + 3 + (a - 2) / 2;
            boolean end = x == x0 || x == x1;
            String spec = a <= 2 ? "dark_oak_planks" : "dark_oak_slab[type=" + ((a - 2) % 2 == 1 ? "top" : "bottom") + "]";
            w.set(x, ry, cz + dz, spec);
            if (a <= 2) w.set(x, ry - 1, cz + dz, "dark_oak_planks");
            if (end) for (int yy = y + h + 1; yy < ry; yy++) w.set(x, yy, cz + dz, "red_terracotta");
        }
        // doorways at both ends, four wide, a beam over each
        for (int x : new int[] {x0, x1}) {
            for (int dz = -2; dz <= 1; dz++) for (int dy = 1; dy <= 4; dy++) w.set(x, y + dy, cz + dz, "air");
            for (int dz = -3; dz <= 2; dz++) w.set(x, y + 5, cz + dz, "stripped_spruce_log[axis=z]");
            w.set(x, y + 7, cz, "white_terracotta");
        }
        // the hayloft along the north side, a ladder up, hay
        for (int x = x0 + 1; x <= x1 - 1; x++) for (int z = z0 + 1; z <= z0 + 3; z++) {
            w.set(x, y + 4, z, "spruce_planks");
            if (z == z0 + 1 && w.r.nextInt(3) > 0) w.set(x, y + 5, z, "hay_block");
        }
        for (int x = x0 + 1; x <= x1 - 1; x++) w.set(x, y + 5, z0 + 3, x % 3 == 0 ? "spruce_fence" : "air");
        for (int x : new int[] {x0 + 3, x1 - 3}) w.hangLight(x, y + 3, cz + 2, "lantern");
        for (int x = x0 + 2; x <= x1 - 2; x += 3) {
            w.set(x, y + 1, z1 - 1, "hay_block");
            if ((x & 1) == 0) w.set(x, y + 2, z1 - 1, "hay_block");
        }
        int top = y + h + 3 + (half - 1) / 2 + 1;
        w.set((x0 + x1) / 2, top, cz, "spruce_fence");
        w.set((x0 + x1) / 2, top + 1, cz, "end_rod[facing=up]");
        keepClear(x0 - 2, z0 - 2, x1 + 2, z1 + 2);
    }

    // ================================================================ the fort's flag

    /** A giant flag of the team's colour on a pole on the rocks behind the fort, waving out over the yard. */
    void fortFlag() {
        int px = -124, pz = FCZ;
        int gy = land(px, pz);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) w.set(px + dx, gy + 1, pz + dz, "mud_bricks");
        int top = gy + 18;
        for (int yy = gy + 2; yy <= top; yy++) w.set(px, yy, pz, "stripped_spruce_log[axis=y]");
        w.set(px, top + 1, pz, "gold_block");
        for (int i = 1; i <= 9; i++) {
            int x = px + i;
            int wave = (int) Math.round(Math.sin(i * 0.8) * 0.9);
            for (int dy = 0; dy <= 4; dy++) w.set(x, top - dy, pz + wave, (dy == 0 || dy == 4) && i > 1 ? "TEAM_wool" : i == 1 ? "white_wool" : "TEAM_wool");
        }
        keepClear(px - 2, pz - 3, px + 11, pz + 3);
    }

    // ================================================================ scenery

    /** Cacti on the open sand, two to four tall, never against anything. */
    void cacti() {
        List<int[]> placed = new ArrayList<>();
        for (int tries = 0; tries < 6000 && placed.size() < 22; tries++) {
            int x = -126 + w.r.nextInt(236), z = -60 + w.r.nextInt(60);
            int[] c = nc(x, z);
            if (c[0] != x || isClear(x, z, 1)) continue;
            int g = land(x, z);
            if (g == Integer.MIN_VALUE) continue;
            String ground = w.type(x, g, z).name();
            if (!ground.equals("SAND") && !ground.equals("RED_SAND")) continue;
            boolean ok = true;
            for (int dx = -1; dx <= 1 && ok; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = 1; dy <= 4; dy++)
                if (!w.air(x + dx, g + dy, z + dz)) { ok = false; break; }
            for (int[] p : placed) if (Math.abs(p[0] - x) < 9 && Math.abs(p[1] - z) < 9) ok = false;
            if (!ok || land.edgeAt(x, z) < 3) continue;
            int h = 2 + w.r.nextInt(3);
            for (int dy = 1; dy <= h; dy++) w.set(x, g + dy, z, "cactus");
            if (w.r.nextInt(5) == 0) w.set(x, g + h + 1, z, "cactus_flower");
            placed.add(new int[] {x, z});
        }
    }

    /** Fallen rock along the feet of the scarps and the butte: a ragged talus, a block or a slab high. */
    void rubble() {
        for (int x = -120; x <= 110; x++) for (int z = -60; z <= 0; z++) {
            int[] c = nc(x, z);
            if (c[0] != x || c[1] != z || isClear(x, z, 1)) continue;
            int g = land(x, z);
            if (g == Integer.MIN_VALUE) continue;
            int high = 0;
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) high = Math.max(high, land(x + dx, z + dz) - g);
            if (high < 4) continue;
            double n = w.fbm(x * 1.4 + 70, z * 1.4 - 30, 5, 2);
            if (n < 0.2 || !w.air(x, g + 1, z)) continue;
            boolean close = false;
            for (int[] o : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) if (land(x + o[0], z + o[1]) - g >= 3) close = true;
            if (n > 0.45 || close && n > 0.32) {
                w.set(x, g + 1, z, rock(x, g + 1, z));
                if (close && n > 0.58) w.set(x, g + 2, z, rock(x, g + 2, z));
            } else {
                w.set(x, g + 1, z, w.r.nextInt(3) == 0 ? "red_sandstone_slab[type=bottom]" : "smooth_red_sandstone_slab[type=bottom]");
            }
        }
    }

    // ================================================================ symmetry

    /** Keeps a box clear of plants and trees, and its turned copy too (things built on either mesa have both). */
    @Override
    protected void keepClear(int x0, int z0, int x1, int z1) {
        super.keepClear(x0, z0, x1, z1);
        super.keepClear(-Math.max(x0, x1), -Math.max(z0, z1), -Math.min(x0, x1), -Math.min(z0, z1));
    }
}
