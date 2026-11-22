package com.sasha.ctfbuilder.v2.maps;

import com.sasha.ctfbuilder.v2.Arena;
import com.sasha.ctfbuilder.v2.Kit;
import com.sasha.ctfbuilder.v2.Land;
import com.sasha.ctfbuilder.v2.Theme;
import com.sasha.ctfbuilder.v2.W.P;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * Mycelia: a forest of giant mushrooms over a swamp, two teams.
 *
 * Each team's village sits on top of a great red cap: a round spawn hall in the middle, cottages, the flag on its
 * ring, the wool towers, a rail round the edge. Shelf-fungus stairs wind round its stem down to the swamp and come
 * up through a hatch behind the hall. Rope bridges run out to two lane caps (each with a ladder up its stem) and
 * from those to a middle cap and on to the three hill caps: the Great Cap in the centre, highest, with a double
 * stair round its stem, and the North and South caps either side, each with its own stair.
 *
 * The swamp below is the slow way round and the safe one: flat moss and mycelium, pools with boardwalks, a stilt
 * hut, a fallen giant mushroom you can walk through, small mushrooms, ferns and lamps.
 */
public final class Mycelia extends Arena {

    static final int F = 61, WALK = 62;
    static final double SLOPE = 0.3;

    /** A giant mushroom: centre, top block, cap radius, flat-top radius, stem radius, red or brown. */
    record Cap(int x, int z, int top, int r, double flat, int stem, boolean red) {
        int walk() {
            return top + 1;
        }
    }

    static final Cap HOME = new Cap(-64, 0, 86, 22, 18, 7, true);
    static final Cap A = new Cap(-36, -22, 82, 9, 6, 3, false);
    static final Cap B = new Cap(-34, 22, 81, 9, 6, 3, true);
    static final Cap C = new Cap(-28, 0, 85, 7, 4, 3, false);
    static final Cap GREAT = new Cap(0, 0, 89, 14, 11, 4, true);
    static final Cap NORTH = new Cap(0, -30, 79, 13, 10.5, 3, false);
    static final Cap SOUTH = new Cap(0, 30, 79, 13, 10.5, 3, false);
    static final Cap[] CAPS = {HOME, A, B, C, GREAT, NORTH, SOUTH};

    /** Pools in the swamp (x, z, radius). The two on the centre line are each other's copies. */
    static final double[][] POOLS = {{-47, 0, 5.5}, {-20, -16, 4.5}, {-52, -34, 6.2}, {-12, 41, 4.0}, {-78, 30, 4}, {0, -15, 3.4}, {0, 15, 3.4}};

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

    // ================================================================ the swamp floor

    double shape(int x, int z) {
        double ax = Math.abs(x), az = Math.abs(z);
        double e = Math.pow(ax / 98.0, 2.6) + Math.pow(az / 50.0, 2.6);
        double lobe = Math.pow(Math.hypot(ax - 64, az) / 32.0, 2);
        return Math.min(e, lobe) - sn(x, z, 14, 3) * 0.05;
    }

    double pool(int x, int z) {
        double best = 1e9;
        for (double[] p : POOLS) {
            for (int s = -1; s <= 1; s += 2) {
                double d = Math.hypot(x - s * p[0], z - s * p[1]) - p[2] - sn(x, z, 3, p[0]) * 0.8;
                best = Math.min(best, d);
            }
        }
        return best;
    }

    double height(int x, int z) {
        if (shape(x, z) > 1) return Double.NaN;
        return pool(x, z) < 0 ? F - 1 : F;
    }

    String surface(int x, int z, int y, int steep, double edge) {
        if (y < F) return sn(x, z, 2.5, 9) > 0.2 ? "clay" : "mud";
        double n1 = sn(x, z, 11, 5), n2 = sn(x, z, 7, 50);
        if (pool(x, z) < 1.2) return n2 > 0 ? "mud" : "muddy_mangrove_roots";
        if (n1 > 0.42) return "mycelium";
        if (n2 > 0.33) return "podzol";
        if (n2 < -0.45) return "rooted_dirt";
        return "moss_block";
    }

    static final String[] LAYERS = {"stone", "tuff", "stone", "andesite", "stone", "granite", "stone", "cobbled_deepslate", "tuff", "stone", "deepslate", "andesite"};
    static final int[] THICK = {3, 1, 2, 2, 2, 1, 3, 1, 2, 2, 2, 2};
    static final int CYCLE = 23;

    String rock(int x, int y, int z) {
        if (y >= F - 3) return y >= F - 1 ? "dirt" : "rooted_dirt";
        double warp = sn(x, z, 26, 11) * 2.2;
        int at = Math.floorMod((int) Math.floor(y + warp), CYCLE), i = 0;
        while (at >= THICK[i]) at -= THICK[i++];
        double tex = w.n3(x * 1.9, y * 1.9, z * 1.9, 4);
        if (tex > 0.62) i = (i + 1) % LAYERS.length;
        else if (tex < -0.64) i = (i + LAYERS.length - 1) % LAYERS.length;
        return LAYERS[i];
    }

    int bottom(int x, int z, int top, double edge) {
        double e = Math.min(1, edge / 52.0), lens = Math.sqrt(2 * e - e * e);
        double bulge = Math.max(0, sn(x, z, 18, 17)) * 7 * Math.min(1, edge / 16.0);
        return (int) Math.round(F - 2 - 40 * lens - bulge + sn(x, z, 8, 5) * 2.2 * Math.min(1, edge / 8.0));
    }

    // ================================================================ build

    @Override
    protected void build() {
        land = new Land(w, t, -104, 104, -58, 58);
        land.top = this::height;
        land.surface = this::surface;
        land.rock = this::rock;
        land.bottom = this::bottom;
        land.water = (x, z) -> pool(x, z) < 0 ? F : Double.NaN;
        land.soil = P.of("dirt");
        land.build();
        roots();

        // the mushrooms
        for (Cap c : new Cap[] {HOME, A, B, C}) mushroom(c, false);
        for (Cap c : new Cap[] {GREAT, NORTH, SOUTH}) mushroom(c, true);
        // the stairs round the stems, and ladders up the lane caps
        spiral(HOME, 7.5, 10.5, Math.PI, false);
        spiral(GREAT, 4.5, 7.5, Math.PI / 2, true, 0);
        spiral(GREAT, 4.5, 7.5, Math.PI * 1.5, true, 0);
        spiral(NORTH, 3.5, 6.5, Math.PI * 1.5, true);
        spiral(SOUTH, 3.5, 6.5, Math.PI / 2, true);
        ladder(A);
        ladder(B);

        village();
        laneCaps();
        hillCaps();

        // rope bridges
        bridge(HOME, A);
        bridge(HOME, B);
        bridge(A, C);
        bridge(B, C);
        bridge(C, GREAT);
        bridge(A, NORTH);
        bridge(B, SOUTH);

        // the swamp
        swampPaths();
        boardwalks();
        stiltHut();
        fallenMushroom();
        swampLamps();
        trees(new Theme.Tree[] {Theme.Tree.BROWN_MUSHROOM, Theme.Tree.RED_MUSHROOM, Theme.Tree.AZALEA}, new int[] {3, 2, 2}, 0.02, 8, 2, this::wild);
        swampPlants();
        cleanPaths();

        spawn = new double[] {HOME.x - 4.5, HOME.walk(), 0.5, -90};
        flag = new int[] {HOME.x + 12, HOME.walk(), 0};
        pads.add(new int[] {HOME.x + 6, HOME.walk(), -11});
        pads.add(new int[] {HOME.x + 6, HOME.walk(), 11});
        hills.add(new Hill("North", NORTH.x - 3, NORTH.walk(), NORTH.z - 3, NORTH.x + 3, NORTH.walk() + 3, NORTH.z + 3));
        hills.add(new Hill("Great Cap", GREAT.x - 3, GREAT.walk(), GREAT.z - 3, GREAT.x + 3, GREAT.walk() + 3, GREAT.z + 3));
        hills.add(new Hill("South", SOUTH.x - 3, SOUTH.walk(), SOUTH.z - 3, SOUTH.x + 3, SOUTH.walk() + 3, SOUTH.z + 3));
    }

    /** Where wild things grow on the swamp floor: open ground away from stems, stairs, paths and pools. */
    boolean wild(int x, int z) {
        if (isClear(x, z, 1) || pool(x, z) < 2) return false;
        if (w.type(x, F, z) == Material.AIR || !w.air(x, F + 1, z)) return false;
        for (Cap c : CAPS) for (int s = -1; s <= 1; s += 2) {
            double d = Math.hypot(x - s * c.x, z - s * c.z);
            if (d < c.stem + 5.5 || (d < c.r + 2 && c.top - F < 18)) return false;
        }
        return true;
    }

    /** Hanging roots and lichen under the island's lowest blocks. */
    void roots() {
        for (int x = -104; x <= 0; x++) for (int z = -58; z <= 58; z++) {
            if (!w.canon(x, z) || Double.isNaN(height(x, z)) || w.r.nextInt(7) != 0) continue;
            int y = low;
            while (y < F && w.air(x, y, z)) y++;
            if (y >= F - 4 || !w.air(x, y - 1, z)) continue;
            w.set(x, y - 1, z, w.r.nextInt(3) == 0 ? "glow_lichen[up=true]" : "hanging_roots");
        }
    }

    // ================================================================ giant mushrooms

    /**
     * A giant mushroom: a stem of fibrous white rising from rooted moss and widening into a collar under the cap; a
     * cap 4 thick under its flat top and drooping to a curled rim, pale gills underneath lit here and there with
     * glowing spores, white spots on the red ones; moss and mycelium grown over the flat top.
     */
    void mushroom(Cap c, boolean half) {
        // the stem
        for (int y = F - 2; y <= c.top - 3; y++) {
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
            if (w.type(x, F, z) == Material.AIR || pool(x, z) < 0.5) continue;
            put(half, x, F, z, d < c.stem + 1.6 ? "rooted_dirt" : "moss_block");
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
     * climb SLOPE a block along the middle line from the swamp to just under the cap, with fungus shelves under
     * them and a rope rail outside; then a straight flight of stairs runs on up through a hatch in the cap,
     * railed round on top. thetaEnd points along an axis, so the flight's stairs face straight.
     */
    void spiral(Cap c, double r0, double r1, double thetaEnd, boolean half) {
        spiral(c, r0, r1, thetaEnd, half, 0.7);
    }

    void spiral(Cap c, double r0, double r1, double thetaEnd, boolean half, double shelf) {
        double rm = (r0 + r1) / 2, k = SLOPE * rm;
        int ws = c.top - 6;
        double tb = thetaEnd - (ws - WALK) / k;
        int R = (int) Math.ceil(r1 + 1);
        for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) {
            double d = Math.hypot(dx, dz);
            if (d < r0 - 0.2 || d > r1 + 0.6) continue;
            boolean rail = d > r1 - 0.4;
            double phi = Math.atan2(dz, dx);
            double th = phi + 2 * Math.PI * Math.ceil((tb - phi) / (2 * Math.PI));
            int x = c.x + dx, z = c.z + dz;
            for (; th <= thetaEnd + 1e-9; th += 2 * Math.PI) {
                double q = Math.max(WALK, Math.round((WALK + k * (th - tb)) * 2) / 2.0);
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
                for (int yy = y - thick; yy < y; yy++) if (yy > F || !w.type(x, yy, z).isSolid()) put(half, x, yy, z, "brown_mushroom_block");
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

    /** A ladder up the west side of a lane cap's stem, through the cap, with a post either side of the hole on top. */
    void ladder(Cap c) {
        int x = c.x - c.stem - 1, z = c.z;
        for (int y = F + 1; y <= c.top; y++) {
            if (!w.type(x + 1, y, z).isSolid()) w.set(x + 1, y, z, "mushroom_stem");
            w.set(x, y, z, "ladder[facing=west]");
        }
        w.set(x, F, z, "rooted_dirt");
        w.set(x - 1, F, z, "packed_mud");
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
     * rail round the edge of the flat top (open where the bridges leave).
     */
    void village() {
        int y = HOME.top;
        // lanes: east door to the flag and on to both bridges, round the hall to the cottages and the hatch
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
        // the rope rail round the edge, open at the bridge heads
        int hf = (int) Math.ceil(HOME.flat) + 1;
        for (int dx = -hf; dx <= hf; dx++) for (int dz = -hf; dz <= hf; dz++) {
            double d = Math.hypot(dx, dz);
            if (d < HOME.flat - 0.6 || d > HOME.flat + 0.4) continue;
            int x = HOME.x + dx, z = dz;
            double ang = Math.toDegrees(Math.atan2(dz, dx));
            if (Math.abs(Math.abs(ang) - Math.toDegrees(Math.atan2(22, 28))) < 9) continue;
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

    // ================================================================ the lane caps and the hill caps

    /**
     * A: a lookout post (a tall stem with a lamp and a little cap), benches and barrels. B: a forager's hut. C, where
     * the bridges cross: a ring of benches round a lamp.
     */
    void laneCaps() {
        // A
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
        // B
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
        // C
        y = C.top;
        lamp(C.x, y, C.z, false);
        for (int[] d : new int[][] {{2, 2}, {-2, 2}, {2, -2}, {-2, -2}}) {
            w.set(C.x + d[0], y, C.z + d[1], "mycelium");
            w.set(C.x + d[0], y + 1, C.z + d[1], "red_mushroom");
        }
    }

    /**
     * The three hill caps: the hill in the middle of each flat top, open to the sky; round it, mushroom lamps and a
     * fairy ring of small mushrooms, broken where the bridges land and the stairs come up.
     */
    void hillCaps() {
        for (Cap c : new Cap[] {GREAT, NORTH}) {
            int y = c.top;
            // the hill's floor: a ring of mud brick round chiseled tuff
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
                int x = c.x + dx, z = c.z + dz;
                if (!w.canon(x, z)) continue;
                int m = Math.max(Math.abs(dx), Math.abs(dz));
                w.set(x, y, z, m == 3 ? "mud_bricks" : m == 0 ? "chiseled_tuff" : "packed_mud");
            }
            double ring = c == GREAT ? 6.5 : 5.2;
            for (int a = 0; a < 360; a += 9) {
                double t = Math.toRadians(a);
                int x = c.x + (int) Math.round(Math.cos(t) * ring), z = c.z + (int) Math.round(Math.sin(t) * ring);
                if (!w.canon(x, z) || !w.air(x, y + 1, z) || !w.type(x, y, z).isSolid()) continue;
                w.set(x, y, z, "mycelium");
                w.set(x, y + 1, z, (a / 9) % 3 == 0 ? "brown_mushroom" : "red_mushroom");
            }
            int[][] lamps = c == GREAT ? new int[][] {{-9, -4}, {-5, -9}} : new int[][] {{-4, 4}, {-3, -5}};
            for (int[] p : lamps) lamp(c.x + p[0], y, c.z + p[1], c == GREAT);
        }
    }

    // ================================================================ rope bridges

    /**
     * A rope bridge from one cap's flat top to another's: a plank deck three wide that sags a little in the middle
     * (never steeper than half a block a block), stepping by slabs; rope rails on posts, a cross beam under every
     * fourth plank, a lantern on the posts at each end.
     */
    void bridge(Cap a, Cap b) {
        double dx = b.x - a.x, dz = b.z - a.z, len = Math.hypot(dx, dz);
        double ux = dx / len, uz = dz / len, nx = -uz, nz = ux;
        double s0 = a.flat - 1.5, s1 = len - (b.flat - 1.5);
        double h0 = a.walk(), h1 = b.walk(), span = s1 - s0;
        double lin = Math.abs(h1 - h0) / span;
        double sag = Math.max(0, Math.min(1.5, (0.42 - lin) * span / 4));
        // the deck: every square whose middle is within 1.85 of the line; the rail: the ring of squares just outside
        java.util.Map<Long, Double> deck = new java.util.HashMap<>();
        java.util.Map<Long, Double> along = new java.util.HashMap<>();
        java.util.Set<Long> rails = new java.util.HashSet<>();
        int bx0 = (int) Math.floor(Math.min(a.x + ux * s0, a.x + ux * s1)) - 4, bx1 = (int) Math.ceil(Math.max(a.x + ux * s0, a.x + ux * s1)) + 4;
        int bz0 = (int) Math.floor(Math.min(a.z + uz * s0, a.z + uz * s1)) - 4, bz1 = (int) Math.ceil(Math.max(a.z + uz * s0, a.z + uz * s1)) + 4;
        for (int x = bx0; x <= bx1; x++) for (int z = bz0; z <= bz1; z++) {
            double sc = (x - a.x) * ux + (z - a.z) * uz, off = Math.abs((x - a.x) * nx + (z - a.z) * nz);
            if (sc < s0 - 0.5 || sc > s1 + 0.5) continue;
            long key = ((long) x << 32) | (z & 0xffffffffL);
            double cl = Math.max(s0, Math.min(s1, sc)), t2 = (cl - s0) / span;
            if (off <= 1.85) {
                deck.put(key, h0 + (h1 - h0) * t2 - sag * 4 * t2 * (1 - t2));
                along.put(key, cl);
            } else if (off <= 2.75 && sc > s0 + 0.5 && sc < s1 - 0.5) {
                rails.add(key);
            }
        }
        for (var e : deck.entrySet()) {
            int x = (int) (e.getKey() >> 32), z = (int) (long) e.getKey();
            boolean onCap = Math.hypot(x - a.x, z - a.z) <= a.flat - 0.5 || Math.hypot(x - b.x, z - b.z) <= b.flat - 0.5;
            double q = Math.round(e.getValue() * 2) / 2.0;
            if (onCap) {
                q = Math.hypot(x - a.x, z - a.z) <= a.flat - 0.5 ? a.walk() : b.walk();
            }
            int y = tread(false, x, z, q, "spruce_planks", "spruce_slab");
            for (int yy = y + 1; yy <= y + 3; yy++) if (!w.air(x, yy, z)) w.set(x, yy, z, "air");
            if (!onCap && Math.floorMod((int) Math.round(along.get(e.getKey())), 4) == 0 && w.air(x, y - 1, z))
                w.set(x, y - 1, z, "stripped_spruce_log[axis=" + (Math.abs(nx) > Math.abs(nz) ? "x" : "z") + "]");
        }
        for (long key : rails) {
            int x = (int) (key >> 32), z = (int) key;
            if (Math.hypot(x - a.x, z - a.z) <= a.flat - 0.5 || Math.hypot(x - b.x, z - b.z) <= b.flat - 0.5) continue;
            // the rail's height follows the deck beside it
            double best = 1e9, hq = 0, sAlong = 0;
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                long n = ((long) (x + d[0]) << 32) | ((z + d[1]) & 0xffffffffL);
                Double v = deck.get(n);
                if (v != null && v < best) {
                    best = v;
                    hq = Math.round(v * 2) / 2.0;
                    sAlong = along.get(n);
                }
            }
            if (best > 1e8) continue;
            int y = (int) Math.floor(hq) - 1;
            boolean post = Math.floorMod((int) Math.round(sAlong), 4) == 0;
            w.set(x, y, z, post ? "stripped_spruce_log[axis=y]" : "stripped_spruce_wood");
            w.set(x, y + 1, z, "spruce_fence");
            if (post) w.set(x, y + 2, z, "spruce_fence");
            if (post && (sAlong < s0 + 3 || sAlong > s1 - 3)) w.set(x, y + 3, z, "lantern");
        }
    }

    // ================================================================ the swamp

    /**
     * Trodden paths: from the foot of the home stair to each lane cap's ladder and on to the hill caps' stairs, and
     * under the middle to the Great Cap's stairs.
     */
    void swampPaths() {
        int[][][] lines = {
            {{-66, 10}, {-60, 15}, {-50, 19}, {-41, 22}},
            {{-62, 13}, {-52, 9}, {-52, -8}, {-47, -16}, {-41, -22}},
            {{-41, -22}, {-32, -27}, {-20, -29}, {-12, -30}, {-8, -30}},
            {{-41, 22}, {-32, 27}, {-20, 29}, {-12, 30}, {-8, 30}},
            {{-41, 22}, {-30, 14}, {-20, 12}, {-10, 9}, {-3, 6}},
            {{-41, -22}, {-30, -12}, {-24, -4}, {-20, 4}, {-20, 12}},
            {{-47, -16}, {-50, -26}},
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
                    if (w.air(px, F, pz) || w.type(px, F, pz) == Material.WATER || !w.air(px, F + 1, pz)) continue;
                    Material m = w.type(px, F, pz);
                    if (m != Material.MOSS_BLOCK && m != Material.MYCELIUM && m != Material.PODZOL && m != Material.ROOTED_DIRT && m != Material.MUD
                            && m != Material.MUDDY_MANGROVE_ROOTS) continue;
                    double n2 = sn(px, pz, 1.6, 7);
                    w.set(px, F, pz, n2 > 0.35 ? "coarse_dirt" : n2 < -0.3 ? "packed_mud" : "dirt_path");
                }
                keepClear((int) x - 2, (int) z - 2, (int) x + 2, (int) z + 2);
            }
        }
    }

    /** Boardwalks over the pools, flush with the ground, on posts; cattail reeds along the pools' edges. */
    void boardwalks() {
        walk(-47, -7, -47, 7);
        walk(-26, -16, -14, -16);
        for (int x = -104; x <= 0; x++) for (int z = -58; z <= 58; z++) {
            if (!w.canon(x, z)) continue;
            double p = pool(x, z);
            if (p < 0 && p > -1.1 && w.type(x, F, z) == Material.WATER && w.r.nextInt(5) == 0) w.set(x, F + 1, z, "lily_pad");
            if (p >= 0 && p < 1 && w.type(x, F, z).isSolid() && w.air(x, F + 1, z) && !isClear(x, z, 0) && w.r.nextInt(3) == 0) {
                boolean wet = false;
                for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) if (w.type(x + d[0], F, z + d[1]) == Material.WATER) wet = true;
                if (wet) {
                    w.set(x, F, z, "mud");
                    w.set(x, F + 1, z, "sugar_cane");
                    if (w.r.nextBoolean()) w.set(x, F + 2, z, "sugar_cane");
                }
            }
        }
    }

    void walk(int x0, int z0, int x1, int z1) {
        boolean alongX = Math.abs(x1 - x0) > Math.abs(z1 - z0);
        int n = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0));
        for (int i = 0; i <= n; i++) {
            int x = x0 + (alongX ? Integer.signum(x1 - x0) * i : 0), z = z0 + (alongX ? 0 : Integer.signum(z1 - z0) * i);
            for (int o = -1; o <= 1; o++) {
                int px = x + (alongX ? 0 : o), pz = z + (alongX ? o : 0);
                w.set(px, F, pz, ((i + o) & 3) == 0 ? "stripped_spruce_wood" : "spruce_planks");
                for (int yy = F + 1; yy <= F + 3; yy++) if (!w.air(px, yy, pz)) w.set(px, yy, pz, "air");
                if (Math.abs(o) == 1 && i % 4 == 0 && w.type(px, F - 1, pz) == Material.WATER) w.set(px, F - 1, pz, "spruce_log");
            }
            if (i % 4 == 0) for (int o = -1; o <= 1; o += 2) w.set(x + (alongX ? 0 : o), F - 1, z + (alongX ? o : 0), "spruce_log");
        }
        keepClear(Math.min(x0, x1) - 2, Math.min(z0, z1) - 2, Math.max(x0, x1) + 2, Math.max(z0, z1) + 2);
    }

    /**
     * A hut on stilts over the north-west pool: plank walls on a log frame, a steep roof of spruce shingles with a
     * mushroom growing through, a porch, stairs up from a boardwalk on the shore. Inside, a mushroom forager's room.
     */
    void stiltHut() {
        int x0 = -55, x1 = -49, z0 = -41, z1 = -36, fy = F + 4;
        // the boardwalk from the shore and the stairs up
        walk(-52, -27, -52, -30);
        for (int i = 0; i < 4; i++) for (int o = -1; o <= 1; o++) {
            int z = -31 - i, y = F + 1 + i;
            w.set(-52 + o, y, z, "spruce_stairs[facing=north,half=bottom]");
            for (int yy = F; yy < y; yy++) w.set(-52 + o, yy, z, o == 0 ? "spruce_planks" : "stripped_spruce_wood");
            for (int yy = y + 1; yy <= y + 3; yy++) w.set(-52 + o, yy, z, "air");
        }
        // the porch and the floor on stilts
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1 + 1; z++) {
            w.set(x, fy, z, z == z1 + 1 ? "spruce_slab[type=top]" : "spruce_planks");
            boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1 + 1);
            if (corner || x == -52 && z == z0) for (int yy = F - 1; yy < fy; yy++) w.set(x, yy, z, "spruce_log");
        }
        w.set(-52, fy, z1 + 1, "spruce_planks");
        for (int x = x0; x <= x1; x++) if (x != -52 && x != -53 && x != -51) {
            w.set(x, fy + 1, z1 + 1, "spruce_fence");
        }
        k.walls(x0, z0, x1, z1, fy, 4, Kit.Bands.of(0, "spruce_planks", 1, "mud_bricks", 2, "spruce_planks", 4, "stripped_dark_oak_log[axis=x]"), "dark_oak_log", 3, P.of("spruce_planks"));
        k.doorway(-52, fy, z1, "south", 1, "spruce_door", null, null, null);
        w.set(-52, fy + 1, z1, "spruce_door[facing=north,half=lower,hinge=left,open=true]");
        w.set(-52, fy + 2, z1, "spruce_door[facing=north,half=upper,hinge=left,open=true]");
        k.window(x0, fy + 2, -38, "west", 1, 1, "glass_pane", null, "spruce_trapdoor");
        k.window(x1, fy + 2, -38, "east", 1, 1, "glass_pane", null, "spruce_trapdoor");
        k.window(-54, fy + 2, z1, "south", 1, 1, "glass_pane", null, null);
        int ridge = k.gable(x0, z0, x1, z1, fy + 5, true, 1, Kit.Roof.of("dark_oak_planks"), P.of("stripped_spruce_wood"));
        // a bracket fungus on the west gable, a little mushroom on the ridge
        for (int z = -41; z <= -39; z++) w.set(x0 - 1, fy + 3, z, "brown_mushroom_block");
        w.set(x0 - 1, fy + 2, -40, "brown_mushroom_block");
        w.set(-52, ridge + 1, -38, "mushroom_stem");
        w.set(-52, ridge + 2, -38, "red_mushroom_block");
        // inside
        w.set(x0 + 1, fy + 1, z0 + 1, "barrel[facing=up]");
        w.set(x0 + 2, fy + 1, z0 + 1, "smoker[facing=south]");
        w.set(x1 - 1, fy + 1, z0 + 1, "cauldron");
        k.bed(x1 - 1, fy + 1, z1 - 1, "north", "brown");
        w.set(x0 + 1, fy + 1, z1 - 1, "composter");
        w.hangLight(-52, fy + 3, -38, "lantern");
        // drying racks of mushrooms on the porch
        w.set(-55, fy + 1, z1 + 1, "barrel[facing=up]");
        w.set(-49, fy + 1, z1 + 1, "brown_mushroom_block");
        keepClear(x0 - 2, z0 - 2, x1 + 2, -26);
    }

    /**
     * A giant mushroom that fell long ago: a hollow stem lying across the south swamp (you can walk through it),
     * shelf fungus and moss on its back, the broken cap lying beside its end.
     */
    void fallenMushroom() {
        int x0 = -48, x1 = -32, zc = 42, yc = F + 2;
        for (int x = x0; x <= x1; x++) for (int dz = -3; dz <= 3; dz++) for (int dy = -3; dy <= 3; dy++) {
            double d = Math.hypot(dz, dy);
            int z = zc + dz + (int) Math.round((x - x0) * -0.15), y = yc + dy;
            if (d > 3.2 || y < F) continue;
            boolean hollow = d < 2.0 && y > F;
            if (hollow) {
                w.set(x, y, z, "air");
                continue;
            }
            if (y == F && d < 2.0) {
                w.set(x, y, z, "moss_block");
                continue;
            }
            String s = d > 2.6 && dy >= 2 ? (w.r.nextInt(3) == 0 ? "moss_block" : "mushroom_stem") : "mushroom_stem";
            w.set(x, y, z, s);
            if (d > 2.6 && dy >= 2 && w.air(x, y + 1, z) && w.r.nextInt(4) == 0) w.set(x, y + 1, z, "moss_carpet");
        }
        // the shelf fungi on its side
        for (int x = x0 + 2; x <= x1 - 2; x += 4) {
            int z = zc - 4 + (int) Math.round((x - x0) * -0.15);
            w.set(x, yc, z, "brown_mushroom_block");
            w.set(x + 1, yc, z, "brown_mushroom_block");
        }
        // the broken cap at the far end, lying on its side
        mushroomRoof(x1 + 6, F, zc - 3, 5.2, 0, true);
        keepClear(x0 - 1, zc - 7, x1 + 12, zc + 5);
    }

    /** Mushroom lamps along the swamp paths. */
    void swampLamps() {
        for (int[] p : new int[][] {{-58, 17}, {-54, 8}, {-44, -19}, {-28, -32}, {-28, 30}, {-14, 12}, {-25, -6}, {-60, -32}}) {
            int g = F;
            if (!w.type(p[0], g, p[1]).isSolid()) continue;
            lamp(p[0], g, p[1], (p[0] + p[1] & 1) == 0);
        }
    }

    /** Small mushrooms, ferns, moss carpet, fireflies and glow berries over the swamp floor. */
    void swampPlants() {
        for (int x = -104; x <= 0; x++) for (int z = -58; z <= 58; z++) {
            if (!w.canon(x, z) || !w.air(x, F + 1, z)) continue;
            Material g = w.type(x, F, z);
            if (g != Material.MOSS_BLOCK && g != Material.MYCELIUM && g != Material.PODZOL && g != Material.ROOTED_DIRT) continue;
            if (isClear(x, z, 0) && !wild(x, z) && w.r.nextInt(3) != 0) continue;
            double drift = sn(x, z, 6, 93), roll = w.r.nextDouble();
            String p = null;
            if (g == Material.MYCELIUM && roll < 0.18) p = w.r.nextBoolean() ? "red_mushroom" : "brown_mushroom";
            else if (drift > 0.25 && roll < 0.35) p = roll < 0.12 ? "fern" : roll < 0.2 ? "moss_carpet" : roll < 0.25 ? "firefly_bush" : "short_grass";
            else if (roll < 0.06) p = roll < 0.02 ? "large_fern" : "short_grass";
            if (p == null) continue;
            if (p.equals("large_fern")) {
                w.set(x, F + 1, z, "large_fern[half=lower]");
                w.set(x, F + 2, z, "large_fern[half=upper]");
            } else w.set(x, F + 1, z, p);
        }
    }

    /** A path block with something standing on it becomes coarse dirt. */
    void cleanPaths() {
        for (int x = -104; x <= 0; x++) for (int z = -58; z <= 58; z++) {
            if (!w.canon(x, z)) continue;
            for (int y = F; y <= 95; y++) if (w.type(x, y, z) == Material.DIRT_PATH && !w.air(x, y + 1, z)) {
                Material a = w.type(x, y + 1, z);
                if (a.isSolid() || a.name().endsWith("_SLAB") || a.name().endsWith("_STAIRS")) w.set(x, y, z, "coarse_dirt");
            }
        }
    }
}
