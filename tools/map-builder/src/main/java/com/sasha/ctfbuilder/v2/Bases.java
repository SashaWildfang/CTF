package com.sasha.ctfbuilder.v2;

import com.sasha.ctfbuilder.v2.Arch.Room;
import com.sasha.ctfbuilder.v2.W.P;

import java.util.ArrayList;
import java.util.List;

/**
 * The ten team bases, one per map, each its own architecture: a Nordic longhouse hold, an adobe fort, a
 * stone castle, a pagoda temple, a dwarven forge-hall, a jungle pyramid, a harbour, a mushroom village,
 * a gothic chapel and an elven citadel. All are built for red at (bx, by, 0) facing +x (toward the middle);
 * the writer copies them to the other teams. Every one has a spawn inside, a flag dais in front and two
 * monument pads on its flanks (the plugin builds wool towers on those in Destroy the Wool).
 */
public final class Bases {

    /** What a base gives the map file. */
    public static final class Base {
        public double[] spawn;
        public int[] flag;
        public final List<int[]> pads = new ArrayList<>();
    }

    final W w;
    final Theme t;
    final Terrain land;
    final Arch a;

    public Bases(W w, Theme t, Terrain land) {
        this.w = w;
        this.t = t;
        this.land = land;
        this.a = new Arch(w, t, land);
    }

    // ---------------------------------------------------------------- shared pieces

    /** Paving with a border, and clear air above it. */
    void yard(int x0, int z0, int x1, int z1, int y, P pave, P border, int clear) {
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                w.set(x, y, z, edge && border != null ? border.pick(w.r) : pave.pick(w.r));
                for (int dy = 1; dy <= clear; dy++) if (!w.air(x, y + dy, z)) w.set(x, y + dy, z, "air");
            }
    }

    /** A round yard. */
    void disc(int cx, int cz, double r, int y, P pave, P border, int clear) {
        int ir = (int) Math.ceil(r);
        for (int dx = -ir; dx <= ir; dx++)
            for (int dz = -ir; dz <= ir; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > r) continue;
                int x = cx + dx, z = cz + dz;
                if (!w.canon(x, z)) continue;
                w.set(x, y, z, d > r - 1 && border != null ? border.pick(w.r) : pave.pick(w.r));
                for (int dy = 1; dy <= clear; dy++) if (!w.air(x, y + dy, z)) w.set(x, y + dy, z, "air");
            }
    }

    /** The flag dais: team concrete inside a rim, a post with a light at each corner. Returns the flag block. */
    int[] dais(int fx, int y, P rim, String postSpec) {
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                w.set(fx + dx, y, dz, edge ? rim.pick(w.r) : (dx == 0 && dz == 0 ? "TEAM_glazed_terracotta" : "TEAM_concrete"));
                for (int dy = 1; dy <= 6; dy++) w.set(fx + dx, y + dy, dz, "air");
            }
        for (int[] c : new int[][] {{-2, -2}, {-2, 2}, {2, -2}, {2, 2}}) {
            w.set(fx + c[0], y + 1, c[1], postSpec);
            w.set(fx + c[0], y + 2, c[1], postSpec);
            w.set(fx + c[0], y + 3, c[1], t.light.endsWith("lantern") ? a.lantern() + "[hanging=false]" : t.light);
        }
        land.keeps.add(new Terrain.Keep(fx, 0, 5));
        return new int[] {fx, y + 1, 0};
    }

    /**
     * A monument pad: a 5x5 stone floor with open sky above (the wool tower goes on it), a ring of posts with
     * banners, and a low wall round it. Returns the first block above the pad's middle.
     */
    int[] pad(int px, int y, int pz, P ring, String postSpec) {
        for (int dx = -4; dx <= 4; dx++)
            for (int dz = -4; dz <= 4; dz++) {
                int x = px + dx, z = pz + dz;
                int m = Math.max(Math.abs(dx), Math.abs(dz));
                for (int dy = 1; dy <= 12; dy++) if (!w.air(x, y + dy, z)) w.set(x, y + dy, z, "air");
                if (m <= 2) w.set(x, y, z, "polished_deepslate");
                else w.set(x, y, z, ring.pick(w.r));
                for (int dy = 1; dy <= 3; dy++) if (w.air(x, y - dy, z)) w.set(x, y - dy, z, ring.pick(w.r));
            }
        for (int[] c : new int[][] {{-4, -4}, {-4, 4}, {4, -4}, {4, 4}}) {
            for (int dy = 1; dy <= 2; dy++) w.set(px + c[0], y + dy, pz + c[1], postSpec);
            w.set(px + c[0], y + 3, pz + c[1], t.light.endsWith("lantern") ? a.lantern() + "[hanging=false]" : t.light);
        }
        land.keeps.add(new Terrain.Keep(px, pz, 6));
        return new int[] {px, y + 1, pz};
    }

    /** Stone path from a door to somewhere, two wide, laid over the ground. */
    void walk(int x1, int z1, int x2, int z2, P pave) {
        int steps = (int) Math.max(1, Math.hypot(x2 - x1, z2 - z1));
        for (int s = 0; s <= steps; s++) {
            int x = x1 + (x2 - x1) * s / steps, z = z1 + (z2 - z1) * s / steps;
            for (int dx = 0; dx <= 1; dx++) for (int dz = 0; dz <= 1; dz++) {
                int gy = w.ground(x + dx, z + dz);
                if (gy > 0 && land.natural(w.type(x + dx, gy, z + dz))) w.set(x + dx, gy, z + dz, pave.pick(w.r));
            }
        }
    }

    // ================================================================ 1. Frostfall: a Nordic hold

    public Base frostfall(int bx, int by, int mz) {
        Base base = new Base();
        P logs = P.of("stripped_spruce_log[axis=x]");
        P stone = P.of("cobblestone", 3, "mossy_cobblestone", 1, "stone", 1);
        // the yard: packed snow and gravel inside a palisade
        disc(bx - 2, 0, 24, by, P.of("snow_block", 4, "gravel", 2, "packed_ice", 1, "coarse_dirt", 1), null, 14);
        // the longhouse: log walls, dark oak posts, a steep roof with crossed gable beams
        int x0 = bx - 20, x1 = bx - 5, z0 = -6, z1 = 6;
        a.foundation(x0, z0, x1, z1, by, stone);
        a.storey(x0, z0, x1, z1, by, 5, P.of("stripped_spruce_log[axis=x]"), "dark_oak_log", "dark_oak_log", P.of("spruce_planks", 3, "stripped_spruce_wood", 1), 3);
        for (int x = x0 + 1; x < x1; x++) for (int dy = 1; dy <= 4; dy++) {
            if ((x - x0) % 3 == 0) continue;
            w.set(x, by + dy, z0, "stripped_spruce_log[axis=x]");
            w.set(x, by + dy, z1, "stripped_spruce_log[axis=x]");
        }
        for (int z = z0 + 1; z < z1; z++) for (int dy = 1; dy <= 4; dy++) {
            if ((z - z0) % 3 == 0) continue;
            w.set(x0, by + dy, z, "stripped_spruce_log[axis=z]");
            w.set(x1, by + dy, z, "stripped_spruce_log[axis=z]");
        }
        a.windows(x0, z0, x1, z1, by, 2, 2, 3, "spruce_trapdoor[facing=north,half=top,open=false]".equals("") ? "" : "glass_pane", true);
        a.archway(x1, by, 0, "east", 3, 3, "spruce_stairs");
        int ridge = a.gable(x0, z0, x1, z1, by + 6, true, 2, "spruce_stairs", "spruce_slab", "dark_oak_planks", P.of("spruce_planks"));
        // dragon-head beams crossing at the gable ends
        for (int i = 0; i < 4; i++) {
            w.set(x1 + 2 + i / 2, ridge + 1 + i, -1 + (i & 1) * 0, "dark_oak_fence");
            w.set(x0 - 2 - i / 2, ridge + 1 + i, 1, "dark_oak_fence");
        }
        w.set(x1 + 4, ridge + 4, 0, "dark_oak_log[axis=x]");
        w.set(x1 + 5, ridge + 4, 0, "dark_oak_trapdoor[facing=east,half=top,open=true]");
        // inside: a hearth down the middle, long tables either side, bunks at the back
        Room hall = new Room(x0, z0, x1, z1, by, 6);
        for (int x = x0 + 5; x <= x1 - 4; x++) {
            w.set(x, by, 0, "stone_bricks");
            if ((x & 1) == 0) w.set(x, by + 1, 0, "campfire[lit=true]");
        }
        a.longTable(x0 + 6, -3, x1 - 4, -3, by + 1, "spruce");
        a.longTable(x0 + 6, 3, x1 - 4, 3, by + 1, "spruce");
        for (int z = z0 + 1; z <= z1 - 1; z += 3) a.bed(x0 + 1, by + 1, z, "east", "white");
        for (int z = z0 + 1; z <= z1 - 1; z += 3) w.set(x0 + 3, by + 1, z, "chest[facing=west]");
        a.banner(x0 + 1, by + 4, 0, "TEAM", "east");
        a.lights(hall);
        base.spawn = new double[] {x0 + 4.5, by + 1, 0.5, -90};
        // the palisade with its gate toward the middle
        for (int i = 0; i < 360; i += 2) {
            double ang = Math.toRadians(i);
            int x = bx - 2 + (int) Math.round(Math.cos(ang) * 25), z = (int) Math.round(Math.sin(ang) * 25);
            if (!w.canon(x, z)) continue;
            if (x > bx + 18 && Math.abs(z) <= 4) continue;           // the main gate
            if (Math.abs(z) > 22 && Math.abs(x - (bx - 2)) <= 3) continue; // side gates by the monuments
            int gy = w.ground(x, z);
            if (gy < by - 6) continue;
            for (int dy = 1; dy <= 3; dy++) w.set(x, gy + dy, z, "spruce_log[axis=y]");
            w.set(x, gy + 4, z, (i & 3) == 0 ? "spruce_fence" : "spruce_slab[type=bottom]");
        }
        for (int side : new int[] {-1, 1}) {
            for (int dy = 1; dy <= 6; dy++) w.set(bx + 22, by + dy, side * 5, "spruce_log[axis=y]");
            w.set(bx + 22, by + 7, side * 5, "lantern[hanging=false]");
            a.banner(bx + 23, by + 5, side * 5, "TEAM", "east");
        }
        // the watchtower on stilts, and a smithy shed
        int tx = bx + 12, tz = -14;
        for (int[] c : new int[][] {{-2, -2}, {-2, 2}, {2, -2}, {2, 2}}) for (int dy = 1; dy <= 9; dy++) w.set(tx + c[0], by + dy, tz + c[1], "spruce_log[axis=y]");
        a.fill(tx - 2, by + 10, tz - 2, tx + 2, by + 10, tz + 2, "spruce_planks");
        a.crenels(tx - 2, tz - 2, tx + 2, tz + 2, by + 11, "spruce_fence", null);
        for (int dy = 1; dy <= 10; dy++) w.set(tx, by + dy, tz + 2, dy == 10 ? "air" : "ladder[facing=north]");
        for (int dy = 1; dy <= 9; dy++) w.set(tx, by + dy, tz + 3, "spruce_planks");
        a.hip(tx - 2, tz - 2, tx + 2, tz + 2, by + 14, 1, "spruce_stairs", "spruce_slab", "spruce_planks", "lightning_rod");
        for (int[] c : new int[][] {{-2, -2}, {-2, 2}, {2, -2}, {2, 2}}) for (int dy = 12; dy <= 13; dy++) w.set(tx + c[0], by + dy, tz + c[1], "spruce_fence");
        w.hangLight(tx, by + 13, tz, "lantern");
        int sx = bx + 4, sz = 13;
        for (int[] c : new int[][] {{-3, -2}, {3, -2}, {-3, 2}, {3, 2}}) for (int dy = 1; dy <= 3; dy++) w.set(sx + c[0], by + dy, sz + c[1], "spruce_log[axis=y]");
        a.gable(sx - 3, sz - 2, sx + 3, sz + 2, by + 4, true, 1, "spruce_stairs", "spruce_slab", "spruce_planks", null);
        w.set(sx - 2, by + 1, sz + 1, "blast_furnace[facing=north,lit=true]");
        w.set(sx - 1, by + 1, sz + 1, "anvil[facing=east]");
        w.set(sx, by + 1, sz + 1, "grindstone[face=floor,facing=north]");
        w.set(sx + 1, by + 1, sz + 1, "smithing_table");
        w.set(sx + 2, by + 1, sz + 1, "water_cauldron[level=3]");
        w.set(sx + 2, by + 1, sz - 1, "barrel[facing=up]");
        w.hangLight(sx, by + 3, sz, "lantern");
        // firewood stacks and a rune stone
        for (int z = -16; z <= -12; z++) for (int dy = 1; dy <= 2; dy++) w.set(bx - 2, by + dy, z, "spruce_log[axis=x]");
        base.flag = dais(bx + 7, by, P.of("polished_andesite"), "spruce_fence");
        for (int side : new int[] {-1, 1}) {
            int[] p = pad(bx - 2, by, side * mz, P.of("stone_bricks", 3, "mossy_stone_bricks", 1), "spruce_fence");
            base.pads.add(p);
        }
        walk(x1 + 1, 0, bx + 4, 0, P.of("gravel", 2, "cobblestone", 1));
        land.keeps.add(new Terrain.Keep(bx - 2, 0, 27));
        return base;
    }

    // ================================================================ 2. Dustbowl: an adobe fort

    public Base dustbowl(int bx, int by, int mz) {
        Base base = new Base();
        P mud = P.of("smooth_sandstone", 4, "sandstone", 2, "cut_sandstone", 1);
        P terra = P.of("terracotta", 3, "orange_terracotta", 1, "white_terracotta", 1);
        int f0 = bx - 18, f1 = bx + 12, w0 = -15, w1 = 15;
        yard(f0, w0, f1, w1, by, P.of("smooth_sandstone", 3, "sandstone", 2, "packed_mud", 1), null, 16);
        // the curtain wall: thick adobe with a walkway and crenels, a gate toward the middle
        for (int x = f0; x <= f1; x++)
            for (int z = w0; z <= w1; z++) {
                boolean edge = x == f0 || x == f1 || z == w0 || z == w1;
                if (!edge) continue;
                if (x == f1 && Math.abs(z) <= 2) continue;
                for (int dy = 1; dy <= 5; dy++) w.set(x, by + dy, z, dy == 5 ? "cut_sandstone" : mud.pick(w.r));
                w.set(x, by + 6, z, ((x + z) & 1) == 0 ? "sandstone_wall" : "air");
            }
        // side gates toward the monuments
        for (int side : new int[] {-1, 1}) for (int x = bx - 4; x <= bx - 0; x++) for (int dy = 1; dy <= 3; dy++) w.set(x, by + dy, side * w1, "air");
        a.archway(f1, by, 0, "east", 5, 4, "sandstone_stairs");
        for (int x = f1; x <= f1; x++) for (int z = -3; z <= 3; z++) w.set(x, by + 5, z, "cut_sandstone");
        a.banner(f1 + 1, by + 4, -3, "TEAM", "east");
        a.banner(f1 + 1, by + 4, 3, "TEAM", "east");
        // square corner towers with domes
        for (int[] c : new int[][] {{f0, w0}, {f0, w1}, {f1, w0}, {f1, w1}}) {
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 0; dy <= 9; dy++) {
                boolean shell = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                w.set(c[0] + dx, by + dy, c[1] + dz, shell ? (dy == 9 ? "cut_red_sandstone" : terra.pick(w.r)) : dy == 0 || dy == 9 ? "smooth_sandstone" : "air");
            }
            a.dome(c[0], by + 10, c[1], 2.4, "smooth_red_sandstone", "lightning_rod");
            for (int dy = 1; dy <= 9; dy++) w.set(c[0], by + dy, c[1] + (c[1] < 0 ? 1 : -1), "ladder[facing=" + (c[1] < 0 ? "south" : "north") + "]");
            for (int dy = 1; dy <= 2; dy++) w.set(c[0] + (c[0] == f0 ? 2 : -2), by + dy, c[1], "air");
            w.hangLight(c[0], by + 8, c[1], "lantern");
        }
        // the hall (spawn): flat roof with a dome, arched windows, rugs and pottery inside
        int h0 = f0 + 3, h1 = bx - 6, hz0 = -8, hz1 = 8;
        a.storey(h0, hz0, h1, hz1, by, 6, terra, "cut_red_sandstone", "smooth_red_sandstone", P.of("smooth_sandstone", 2, "terracotta", 1), 4);
        a.windows(h0, hz0, h1, hz1, by, 2, 3, 4, "orange_stained_glass_pane", false);
        a.archway(h1, by, 0, "east", 3, 4, "red_sandstone_stairs");
        a.fill(h0, by + 7, hz0, h1, by + 7, hz1, "smooth_red_sandstone");
        a.crenels(h0, hz0, h1, hz1, by + 8, "red_sandstone_wall", null);
        a.dome((h0 + h1) / 2, by + 8, 0, 4.4, "smooth_quartz", "gold_block");
        Room hall = new Room(h0, hz0, h1, hz1, by, 7);
        a.rug(h0 + 2, -3, h1 - 2, 3, by + 1, "orange", "red");
        for (int x = h0 + 1; x <= h1 - 1; x += 2) {
            w.set(x, by + 1, hz0 + 1, (x & 2) == 0 ? "decorated_pot" : "barrel[facing=up]");
            w.set(x, by + 1, hz1 - 1, (x & 2) == 0 ? "barrel[facing=up]" : "decorated_pot");
        }
        a.bed(h0 + 1, by + 1, -4, "west", "orange");
        a.bed(h0 + 1, by + 1, 4, "west", "orange");
        w.set(h0 + 1, by + 1, 0, "loom[facing=east]");
        a.lights(hall);
        base.spawn = new double[] {h0 + 3.5, by + 1, 0.5, -90};
        // the courtyard: a well with a roof, a market of awnings, palms in planters
        int wx = bx + 2, wz = -8;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            boolean rim = dx != 0 || dz != 0;
            w.set(wx + dx, by + 1, wz + dz, rim ? "sandstone_wall" : "water");
            w.set(wx + dx, by, wz + dz, rim ? "chiseled_sandstone" : "water");
        }
        for (int[] c : new int[][] {{-1, -1}, {1, 1}}) for (int dy = 2; dy <= 3; dy++) w.set(wx + c[0], by + dy, wz + c[1], "jungle_fence");
        a.hip(wx - 1, wz - 1, wx + 1, wz + 1, by + 4, 0, "smooth_red_sandstone_stairs", "smooth_red_sandstone_slab", "smooth_red_sandstone", null);
        for (int x = f0 + 3; x <= f1 - 3; x += 6) for (int side : new int[] {-1, 1}) {
            int z = side * (w1 - 2);
            if (Math.abs(x - (bx - 2)) <= 3) continue;
            for (int dy = 1; dy <= 3; dy++) w.set(x, by + dy, z - side, "jungle_fence");
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) w.set(x + dx, by + 4, z + dz, dx == 0 ? "TEAM_wool" : "white_wool");
            w.set(x - 1, by + 1, z, "barrel[facing=up]");
            w.set(x + 1, by + 1, z, "decorated_pot");
        }
        for (int[] p : new int[][] {{bx - 2, -11}, {bx + 8, 9}}) {
            w.set(p[0], by, p[1], "sand");
            new Flora(w, t, land).tree(Theme.Tree.PALM, p[0], by + 1, p[1]);
        }
        base.flag = dais(bx + 5, by, P.of("cut_red_sandstone"), "jungle_fence");
        for (int side : new int[] {-1, 1}) base.pads.add(pad(bx - 2, by, side * mz, P.of("cut_sandstone", 2, "smooth_sandstone", 1), "jungle_fence"));
        land.keeps.add(new Terrain.Keep(bx - 3, 0, 26));
        return base;
    }

    // ================================================================ 3. Four Kingdoms: a stone castle

    public Base kingdoms(int bx, int by, int mz) {
        Base base = new Base();
        P wall = P.of("stone_bricks", 6, "cracked_stone_bricks", 2, "mossy_stone_bricks", 2, "andesite", 1);
        int c0 = bx - 18, c1 = bx + 10, cz0 = -13, cz1 = 13;
        yard(c0, cz0, c1, cz1, by, P.of("gravel", 2, "cobblestone", 2, "dirt_path", 2, "coarse_dirt", 1), null, 18);
        // curtain walls with a wall-walk and battlements
        for (int x = c0; x <= c1; x++)
            for (int z = cz0; z <= cz1; z++) {
                boolean edge = x == c0 || x == c1 || z == cz0 || z == cz1;
                if (!edge) continue;
                for (int dy = 1; dy <= 6; dy++) w.set(x, by + dy, z, wall.pick(w.r));
                w.set(x, by + 7, z, ((x + z) & 1) == 0 ? "stone_bricks" : "air");
            }
        for (int x = c0 + 1; x < c1; x++) {
            w.set(x, by + 6, cz0 + 1, "stone_brick_slab[type=top]");
            w.set(x, by + 6, cz1 - 1, "stone_brick_slab[type=top]");
        }
        // the gatehouse with a portcullis of bars (open below), toward the middle
        for (int z = -3; z <= 3; z++) for (int dy = 1; dy <= 10; dy++) for (int dx = 0; dx <= 3; dx++) {
            boolean shell = Math.abs(z) == 3 || dx == 0 || dx == 3;
            w.set(c1 + dx - 1, by + dy, z, shell ? wall.pick(w.r) : dy == 10 ? "stone_bricks" : "air");
        }
        for (int z = -1; z <= 1; z++) for (int dx = -1; dx <= 3; dx++) for (int dy = 1; dy <= 4; dy++) w.set(c1 + dx - 1, by + dy, z, "air");
        for (int z = -1; z <= 1; z++) w.set(c1 + 2, by + 4, z, "iron_bars");
        a.crenels(c1 - 1, -3, c1 + 2, 3, by + 11, "stone_bricks", null);
        a.banner(c1 + 3, by + 8, -2, "TEAM", "east");
        a.banner(c1 + 3, by + 8, 2, "TEAM", "east");
        for (int side : new int[] {-1, 1}) for (int x = bx - 4; x <= bx; x++) for (int dy = 1; dy <= 3; dy++) w.set(x, by + dy, side * cz1, "air");
        // round towers at the front corners with cone roofs
        for (int side : new int[] {-1, 1}) a.roundTower(c1, side * cz1, by, 3, 12, wall, "spruce_planks", side < 0 ? "south" : "north", true);
        // the keep (spawn): three storeys, great hall, solar and armoury
        int k0 = c0 + 1, k1 = c0 + 13, kz0 = -6, kz1 = 6;
        a.storey(k0, kz0, k1, kz1, by, 5, wall, "polished_andesite", null, P.of("spruce_planks", 2, "stripped_spruce_wood", 1), 0);
        a.storey(k0, kz0, k1, kz1, by + 5, 5, wall, "polished_andesite", null, P.of("spruce_planks"), 0);
        a.storey(k0, kz0, k1, kz1, by + 10, 5, wall, "polished_andesite", null, P.of("spruce_planks"), 0);
        for (int f = 0; f < 3; f++) a.windows(k0, kz0, k1, kz1, by + f * 5, 2, 2, 4, "glass_pane", false);
        a.crenels(k0, kz0, k1, kz1, by + 16, "stone_bricks", null);
        a.fill(k0, by + 15, kz0, k1, by + 15, kz1, "stone_bricks");
        for (int[] c : new int[][] {{k0, kz0}, {k0, kz1}, {k1, kz0}, {k1, kz1}}) {
            for (int dy = 16; dy <= 18; dy++) w.set(c[0], by + dy, c[1], "stone_bricks");
            w.set(c[0], by + 19, c[1], "lantern[hanging=false]");
        }
        for (int dy = 16; dy <= 22; dy++) w.set((k0 + k1) / 2, by + dy, 0, "spruce_fence");
        a.banner((k0 + k1) / 2 + 1, by + 21, 0, "TEAM", "east");
        a.door(k1, by, 0, "east", "spruce_door");
        Room greatHall = new Room(k0, kz0, k1, kz1, by, 5);
        a.greatHall(greatHall, "west", by + 18);
        a.flight(k1 - 2, by + 1, kz1 - 1, "west", 5, "stone_brick_stairs", "stone_bricks");
        a.flight(k0 + 2, by + 6, kz0 + 1, "east", 5, "stone_brick_stairs", "stone_bricks");
        a.reserve(k1 - 7, kz1 - 1, k1 - 1, kz1 - 1);
        a.reserve(k0 + 1, kz0 + 1, k0 + 7, kz0 + 1);
        a.bunkroom(new Room(k0, kz0, k1, kz1, by + 5, 5), "TEAM");
        a.library(new Room(k0, kz0, k1, kz1, by + 10, 5));
        for (int dy = 11; dy <= 15; dy++) w.set(k0 + 1, by + dy, 0, "ladder[facing=east]");
        w.set(k0 + 1, by + 15, 0, "ladder[facing=east]");
        base.spawn = new double[] {k0 + 4.5, by + 1, 2.5, -90};
        // stables and a smithy against the side walls
        for (int side : new int[] {-1, 1}) {
            int z = side * (cz1 - 3);
            for (int x = bx + 2; x <= bx + 8; x += 3) for (int dy = 1; dy <= 3; dy++) w.set(x, by + dy, z - side * 2, "spruce_log[axis=y]");
            for (int x = bx + 2; x <= bx + 8; x++) for (int dz = -2; dz <= 2; dz++) w.set(x, by + 4, z + dz * side * 0 + (dz == 0 ? 0 : dz), "spruce_slab[type=bottom]");
            for (int x = bx + 3; x <= bx + 7; x += 2) w.set(x, by + 1, z + side, "hay_block");
            w.set(bx + 4, by + 1, z, side < 0 ? "anvil[facing=east]" : "composter");
            w.set(bx + 6, by + 1, z, side < 0 ? "blast_furnace[facing=" + (side < 0 ? "south" : "north") + ",lit=true]" : "water_cauldron[level=3]");
        }
        base.flag = dais(bx + 1, by, P.of("polished_andesite"), "stone_brick_wall");
        for (int side : new int[] {-1, 1}) base.pads.add(pad(bx - 4, by, side * mz, P.of("stone_bricks", 3, "cracked_stone_bricks", 1), "stone_brick_wall"));
        land.keeps.add(new Terrain.Keep(bx - 4, 0, 24));
        return base;
    }

    // ================================================================ 4. Sakura: a pagoda temple

    public Base sakura(int bx, int by, int mz) {
        Base base = new Base();
        // a raised stone platform for the main hall
        int h0 = bx - 19, h1 = bx - 5, hz0 = -7, hz1 = 7;
        // a gravel walk round the hall and a stone path to the gate; the rest stays garden
        yard(bx - 23, -11, bx - 1, 11, by, P.of("gravel", 4, "andesite", 1, "moss_block", 1), null, 3);
        yard(bx - 1, -2, bx + 13, 2, by, P.of("polished_andesite", 3, "andesite", 1), P.of("mossy_stone_bricks"), 6);
        for (int x = h0 - 2; x <= h1 + 2; x++) for (int z = hz0 - 2; z <= hz1 + 2; z++) {
            w.set(x, by, z, "stone_bricks");
            w.set(x, by + 1, z, (x == h0 - 2 || x == h1 + 2 || z == hz0 - 2 || z == hz1 + 2) ? "stone_brick_slab[type=bottom]" : "dark_oak_planks");
        }
        int fy = by + 1;
        a.storey(h0, hz0, h1, hz1, fy, 5, P.of("white_terracotta", 2, "calcite", 1), "stripped_dark_oak_log", "dark_oak_log", P.of("dark_oak_planks"), 3);
        // paper screens: birch trapdoors closed over white glass between the posts
        for (int x = h0 + 1; x < h1; x++) for (int dy = 2; dy <= 3; dy++) {
            if ((x - h0) % 3 == 0) continue;
            w.set(x, fy + dy, hz0, "white_stained_glass_pane");
            w.set(x, fy + dy, hz1, "white_stained_glass_pane");
        }
        // veranda all round, with red rails
        for (int x = h0 - 2; x <= h1 + 2; x++) for (int z = hz0 - 2; z <= hz1 + 2; z++) {
            boolean rim = x == h0 - 2 || x == h1 + 2 || z == hz0 - 2 || z == hz1 + 2;
            if (rim && !(x == h1 + 2 && Math.abs(z) <= 1)) w.set(x, fy + 1, z, "crimson_fence");
        }
        a.archway(h1, fy, 0, "east", 3, 3, "dark_oak_stairs");
        int y1 = a.pagodaTier(h0, hz0, h1, hz1, fy + 6, 3, "deepslate_tile_stairs", "deepslate_tile_slab", "deepslate_tiles");
        a.storey(h0 + 3, hz0 + 3, h1 - 3, hz1 - 3, y1, 3, P.of("white_terracotta"), "stripped_dark_oak_log", "dark_oak_log", P.of("dark_oak_planks"), 3);
        int y2 = a.pagodaTier(h0 + 3, hz0 + 3, h1 - 3, hz1 - 3, y1 + 4, 2, "deepslate_tile_stairs", "deepslate_tile_slab", "deepslate_tiles");
        a.hip(h0 + 5, hz0 + 5, h1 - 5, hz1 - 5, y2, 1, "deepslate_tile_stairs", "deepslate_tile_slab", "deepslate_tiles", "lightning_rod");
        // inside: tatami (bamboo mosaic and birch), low tables, a shrine at the back, futons
        Room hall = new Room(h0, hz0, h1, hz1, fy, 5);
        for (int x = h0 + 1; x < h1; x++) for (int z = hz0 + 1; z < hz1; z++) w.set(x, fy, z, ((x / 2 + z / 3) & 1) == 0 ? "bamboo_mosaic" : "stripped_bamboo_block[axis=x]");
        for (int z = -4; z <= 4; z += 4) {
            w.set(h0 + 6, fy + 1, z, "dark_oak_trapdoor[facing=east,half=top]");
            w.set(h0 + 7, fy + 1, z, "dark_oak_trapdoor[facing=west,half=top]");
            w.set(h0 + 6, fy + 2, z, "candle[candles=2,lit=true]");
        }
        w.set(h0 + 1, fy + 1, 0, "chiseled_quartz_block");
        w.set(h0 + 1, fy + 2, 0, "decorated_pot");
        w.set(h0 + 1, fy + 1, -2, "candle[candles=4,lit=true]");
        w.set(h0 + 1, fy + 1, 2, "candle[candles=4,lit=true]");
        a.banner(h0 + 1, fy + 4, 0, "TEAM", "east");
        for (int z = hz0 + 1; z <= hz1 - 1; z += 4) a.bed(h1 - 4, fy + 1, z, "south", "white");
        a.lights(hall);
        base.spawn = new double[] {h0 + 3.5, fy + 1, 0.5, -90};
        // a five-tier pagoda tower on the north flank
        int px = bx - 8, pz = -15;
        for (int tier = 0; tier < 5; tier++) {
            int ty = by + 1 + tier * 4, r = 2;
            a.storey(px - r, pz - r, px + r, pz + r, ty, 3, P.of("white_terracotta"), "stripped_dark_oak_log", null, P.of("dark_oak_planks"), 4);
            a.pagodaTier(px - r, pz - r, px + r, pz + r, ty + 3, 1, "deepslate_tile_stairs", "deepslate_tile_slab", "deepslate_tiles");
        }
        for (int dy = 0; dy < 4; dy++) w.set(px, by + 21 + dy, pz, dy == 3 ? "lightning_rod" : "iron_chain[axis=y]");
        // torii gate on the path to the middle
        int gx = bx + 10;
        for (int side : new int[] {-3, 3}) for (int dy = 1; dy <= 5; dy++) w.set(gx, by + dy, side, "red_concrete");
        for (int z = -4; z <= 4; z++) w.set(gx, by + 6, z, Math.abs(z) == 4 ? "dark_oak_slab[type=bottom]" : "black_concrete");
        for (int z = -3; z <= 3; z++) w.set(gx, by + 4, z, "red_concrete");
        // zen garden: raked gravel, three stones, a little cherry; and stone lanterns
        for (int x = bx - 4; x <= bx + 4; x++) for (int z = 10; z <= 18; z++) w.set(x, by, z, ((z & 1) == 0) ? "gravel" : "light_gray_concrete_powder");
        for (int[] s : new int[][] {{bx - 2, 12}, {bx + 2, 15}, {bx, 17}}) {
            w.set(s[0], by + 1, s[1], "mossy_cobblestone");
            w.set(s[0] + 1, by + 1, s[1], "cobblestone_slab[type=bottom]");
        }
        for (int[] l : new int[][] {{bx + 6, -5}, {bx + 6, 5}, {bx - 3, -10}, {bx - 3, 10}}) {
            w.set(l[0], by + 1, l[1], "stone_brick_wall");
            w.set(l[0], by + 2, l[1], "lantern[hanging=false]");
            w.set(l[0], by + 3, l[1], "stone_brick_slab[type=bottom]");
        }
        base.flag = dais(bx + 4, by, P.of("polished_andesite"), "crimson_fence");
        for (int side : new int[] {-1, 1}) base.pads.add(pad(bx + 2, by, side * mz, P.of("stone_bricks", 2, "andesite", 1), "dark_oak_fence"));
        land.keeps.add(new Terrain.Keep(bx - 5, 0, 26));
        return base;
    }

    // ================================================================ 5. Emberforge: a dwarven forge-hall

    public Base emberforge(int bx, int by, int mz) {
        Base base = new Base();
        P rock = P.of("blackstone", 3, "basalt[axis=y]", 2, "polished_blackstone_bricks", 1, "cracked_polished_blackstone_bricks", 1);
        // a mountain of basalt and blackstone at the back, the hall carved into it
        int m0 = bx - 26, m1 = bx - 4;
        for (int x = m0 - 4; x <= m1; x++)
            for (int z = -18; z <= 18; z++) {
                double fx = (x - m0) / (double) (m1 - m0), fz = Math.abs(z) / 18.0;
                double h = 18 * Math.max(0, 1 - fz * fz) * Math.min(1, (m1 - x) / 8.0 + 0.2) + w.n(x, z, 4) * 2.5;
                if (h <= 1) continue;
                for (int dy = 1; dy <= h; dy++) w.set(x, by + dy, z, rock.pick(w.r));
            }
        int h0 = m0 + 2, h1 = m1, hz0 = -7, hz1 = 7;
        Room hall = new Room(h0, hz0, h1, hz1, by, 9);
        for (int x = h0; x <= h1; x++) for (int z = hz0; z <= hz1; z++) {
            boolean edge = x == h0 || x == h1 || z == hz0 || z == hz1;
            w.set(x, by, z, edge ? "polished_blackstone" : ((x + z) & 1) == 0 ? "polished_blackstone_bricks" : "polished_basalt[axis=y]");
            for (int dy = 1; dy <= 9; dy++) if (!edge) w.set(x, by + dy, z, "air");
            if (edge) for (int dy = 1; dy <= 9; dy++) w.set(x, by + dy, z, dy == 9 ? "chiseled_polished_blackstone" : "polished_blackstone_bricks");
        }
        // the facade: a huge doorway between gilded pillars
        for (int z = -2; z <= 2; z++) for (int dy = 1; dy <= 6; dy++) w.set(h1, by + dy, z, "air");
        for (int side : new int[] {-4, 4}) for (int dy = 1; dy <= 12; dy++) w.set(h1 + 1, by + dy, side, dy % 4 == 0 ? "gilded_blackstone" : "polished_blackstone_bricks");
        for (int z = -4; z <= 4; z++) w.set(h1 + 1, by + 12, z, "chiseled_polished_blackstone");
        for (int z = -3; z <= 3; z++) w.set(h1 + 1, by + 7, z, "polished_blackstone_brick_stairs[facing=east,half=top]");
        a.reserve(h1 - 4, -2, h1 + 5, 2);
        // inside: lava channels behind bars down the walls, forges, anvils, the war table
        for (int x = h0 + 2; x <= h1 - 2; x++) for (int side : new int[] {-1, 1}) {
            int z = side * (hz1 - 1);
            w.set(x, by, z, "lava");
            w.set(x, by + 1, z, "iron_bars");
            w.set(x, by - 1, z, "magma_block");
        }
        for (int x = h0 + 3; x <= h1 - 3; x += 4) {
            for (int side : new int[] {-1, 1}) {
                w.set(x, by + 1, side * (hz1 - 2), "blast_furnace[facing=" + (side < 0 ? "south" : "north") + ",lit=true]");
                w.set(x + 1, by + 1, side * (hz1 - 2), side < 0 ? "anvil[facing=east]" : "smithing_table");
            }
            for (int dy = 1; dy <= 8; dy++) w.set(x, by + dy, 0, dy == 8 ? "chiseled_polished_blackstone" : "polished_basalt[axis=y]");
        }
        for (int x = h0 + 3; x <= h1 - 3; x += 4) w.hangLight(x + 2, by + 7, 0, "soul_lantern");
        a.longTable(h0 + 2, -2, h0 + 5, 2, by + 1, "crimson");
        a.banner(h0 + 1, by + 6, 0, "TEAM", "east");
        a.bed(h0 + 1, by + 1, -4, "east", "TEAM");
        a.bed(h0 + 1, by + 1, 4, "east", "TEAM");
        base.spawn = new double[] {h1 - 4.5, by + 1, 0.5, -90};
        // great chimneys out of the mountain
        for (int[] c : new int[][] {{m0 + 6, -9}, {m0 + 6, 9}, {m0 + 12, 0}}) {
            for (int dy = 10; dy <= 26; dy++) for (int dx = 0; dx <= 1; dx++) for (int dz = 0; dz <= 1; dz++)
                w.set(c[0] + dx, by + dy, c[1] + dz, dy > 23 ? "polished_blackstone_bricks" : "nether_bricks");
            w.set(c[0], by + 27, c[1], "campfire[lit=true,signal_fire=true]");
        }
        // a terrace of basalt in front, with braziers
        yard(h1 + 1, -14, bx + 14, 14, by, P.of("polished_blackstone", 2, "polished_basalt[axis=y]", 1, "smooth_basalt", 1), P.of("polished_blackstone_bricks"), 12);
        for (int[] b : new int[][] {{bx + 2, -8}, {bx + 2, 8}, {bx + 12, -12}, {bx + 12, 12}}) {
            w.set(b[0], by + 1, b[1], "polished_blackstone_wall");
            w.set(b[0], by + 2, b[1], "campfire[lit=true]");
        }
        base.flag = dais(bx + 7, by, P.of("polished_blackstone_bricks"), "nether_brick_fence");
        for (int side : new int[] {-1, 1}) base.pads.add(pad(bx + 1, by, side * mz, P.of("polished_blackstone", 2, "blackstone", 1), "nether_brick_fence"));
        land.keeps.add(new Terrain.Keep(bx - 6, 0, 26));
        return base;
    }

    // ================================================================ 6. Verdant Ruins: a jungle pyramid

    public Base verdant(int bx, int by, int mz) {
        Base base = new Base();
        P moss = P.of("mossy_stone_bricks", 4, "cracked_stone_bricks", 2, "stone_bricks", 2, "mossy_cobblestone", 1);
        // a stepped pyramid; the spawn chamber inside its base, a shrine on top
        int px = bx - 12, half = 12;
        for (int step = 0; step < 5; step++) {
            int r = half - step * 2;
            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) for (int dy = 0; dy < 3; dy++)
                w.set(px + dx, by + 1 + step * 3 + dy, dz, (Math.abs(dx) == r || Math.abs(dz) == r) && dy == 2 && ((dx + dz) & 1) == 0 ? "mossy_stone_brick_slab[type=bottom]" : moss.pick(w.r));
        }
        // the grand stair up the front, the chamber inside
        for (int i = 0; i < 15; i++) for (int z = -2; z <= 2; z++) {
            w.set(px + half - i, by + 1 + i, z, "mossy_stone_brick_stairs[facing=west]");
            for (int dy = 1; dy <= 3; dy++) if (px + half - i + 1 <= px + half) w.set(px + half - i, by + 1 + i + dy, z, "air");
        }
        Room chamber = new Room(px - 8, -7, px + 6, 7, by, 6);
        for (int x = chamber.x0() + 1; x < chamber.x1(); x++) for (int z = -6; z <= 6; z++) {
            w.set(x, by, z, ((x + z) % 3 == 0) ? "chiseled_stone_bricks" : "mossy_stone_bricks");
            for (int dy = 1; dy <= 5; dy++) w.set(x, by + dy, z, "air");
        }
        // the chamber's door: through the base of the pyramid, under the stair, to the front
        for (int x = px + 6; x <= px + half + 1; x++) for (int z = -1; z <= 1; z++) for (int dy = 1; dy <= 3; dy++) w.set(x, by + dy, z, "air");
        a.reserve(px + 2, -2, px + half + 4, 2);
        for (int z = -6; z <= 6; z += 3) {
            w.set(chamber.x0() + 1, by + 1, z, "chiseled_stone_bricks");
            w.set(chamber.x0() + 1, by + 2, z, "torch".equals("") ? "" : "soul_lantern[hanging=false]");
        }
        for (int x = chamber.x0() + 3; x < chamber.x1() - 2; x += 3) {
            w.set(x, by + 1, -6, "decorated_pot");
            w.set(x, by + 1, 6, "chest[facing=north]");
        }
        a.bed(chamber.x0() + 2, by + 1, -3, "east", "lime");
        a.bed(chamber.x0() + 2, by + 1, 3, "east", "lime");
        a.banner(chamber.x0() + 1, by + 4, 0, "TEAM", "east");
        for (int x = chamber.x0() + 2; x < chamber.x1() - 1; x += 4) {
            w.set(x, by + 5, 0, "glow_lichen[up=true]");
            w.hangLight(x, by + 4, 0, "lantern");
        }
        base.spawn = new double[] {chamber.x0() + 4.5, by + 1, 0.5, -90};
        // the shrine on top: four pillars and a roof
        int ty = by + 16;
        for (int[] c : new int[][] {{-2, -2}, {-2, 2}, {2, -2}, {2, 2}}) for (int dy = 0; dy <= 3; dy++) w.set(px + c[0], ty + dy, c[1], "chiseled_stone_bricks");
        a.hip(px - 2, -2, px + 2, 2, ty + 4, 1, "mossy_stone_brick_stairs", "mossy_stone_brick_slab", "mossy_stone_bricks", null);
        w.set(px, ty, 0, "gold_block");
        w.hangLight(px, ty + 3, 0, "lantern");
        // vines down the pyramid
        for (int i = 0; i < 80; i++) {
            int x = px + w.r.nextInt(25) - 12, z = w.r.nextInt(25) - 12;
            int top = w.top(x, z);
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                if (w.air(x + d[0], top - 1, z + d[1])) {
                    String face = d[0] == 1 ? "west" : d[0] == -1 ? "east" : d[1] == 1 ? "north" : "south";
                    for (int k = 1; k <= 1 + w.r.nextInt(3); k++) if (w.air(x + d[0], top - k, z + d[1])) w.set(x + d[0], top - k, z + d[1], "vine[" + face + "=true]");
                    break;
                }
            }
        }
        // a ruined colonnade lining the way to the flag
        for (int x = bx + 1; x <= bx + 13; x += 4) for (int side : new int[] {-5, 5}) {
            int h = 2 + (int) ((w.n(x, side, 2) + 1) * 2.2);
            for (int dy = 1; dy <= h; dy++) w.set(x, by + dy, side, dy == 1 ? "chiseled_stone_bricks" : "mossy_stone_brick_wall");
        }
        yard(bx - 0, -6, bx + 14, 6, by, P.of("mossy_stone_bricks", 2, "moss_block", 1, "mossy_cobblestone", 1), null, 6);
        base.flag = dais(bx + 9, by, P.of("chiseled_stone_bricks"), "mossy_stone_brick_wall");
        for (int side : new int[] {-1, 1}) base.pads.add(pad(bx + 1, by, side * mz, P.of("mossy_stone_bricks", 2, "stone_bricks", 1), "mossy_stone_brick_wall"));
        land.keeps.add(new Terrain.Keep(bx - 6, 0, 24));
        return base;
    }

    // ================================================================ 7. Tidecrest: a harbour

    public Base tidecrest(int bx, int by, int mz) {
        Base base = new Base();
        P plaster = P.of("white_concrete", 3, "white_terracotta", 1, "smooth_quartz", 1);
        // the harbour house (spawn): two storeys of white plaster with a prismarine roof
        int h0 = bx - 15, h1 = bx - 4, hz0 = -6, hz1 = 6;
        a.foundation(h0, hz0, h1, hz1, by, P.of("sandstone"));
        a.storey(h0, hz0, h1, hz1, by, 4, plaster, "stripped_birch_log", "birch_planks", P.of("birch_planks", 2, "stripped_birch_wood", 1), 4);
        a.storey(h0, hz0, h1, hz1, by + 4, 4, plaster, "stripped_birch_log", "birch_planks", P.of("birch_planks"), 4);
        a.windows(h0, hz0, h1, hz1, by, 2, 2, 4, "glass_pane", true);
        a.windows(h0, hz0, h1, hz1, by + 4, 2, 2, 4, "glass_pane", true);
        a.door(h1, by, 0, "east", "birch_door");
        a.gable(h0, hz0, h1, hz1, by + 9, false, 1, "prismarine_brick_stairs", "prismarine_brick_slab", "prismarine_bricks", plaster);
        Room ground = new Room(h0, hz0, h1, hz1, by, 4);
        a.kitchen(new Room(h0, hz0, h0 + 5, hz1, by, 4));
        a.flight(h1 - 2, by + 1, hz0 + 1, "west", 4, "birch_stairs", "birch_planks");
        for (int x = h1 - 5; x <= h1 - 2; x++) w.set(x, by + 4, hz0 + 1, "air");
        a.reserve(h1 - 6, hz0 + 1, h1 - 1, hz0 + 1);
        a.bunkroom(new Room(h0, hz0, h1, hz1, by + 4, 4), "light_blue");
        a.rug(h0 + 7, -2, h1 - 2, 2, by + 1, "light_blue", "white");
        a.lights(ground);
        base.spawn = new double[] {h1 - 3.5, by + 1, 2.5, -90};
        // the boathouse and piers out over the edge, a boat moored
        int bh0 = bx - 4, bz0 = 12, bz1 = 20;
        for (int x = bh0; x <= bh0 + 10; x++) for (int z = bz0; z <= bz1; z++) {
            w.set(x, by, z, "spruce_planks");
            if ((x - bh0) % 4 == 0 && (z == bz0 || z == bz1)) for (int dy = 1; dy <= 8; dy++) if (w.air(x, by - dy, z)) w.set(x, by - dy, z, "spruce_log[axis=y]");
        }
        for (int x = bh0; x <= bh0 + 10; x += 5) for (int z : new int[] {bz0, bz1}) for (int dy = 1; dy <= 3; dy++) w.set(x, by + dy, z, "stripped_spruce_log[axis=y]");
        a.gable(bh0, bz0, bh0 + 10, bz1, by + 4, true, 1, "spruce_stairs", "spruce_slab", "spruce_planks", null);
        for (int x = bh0 + 2; x <= bh0 + 8; x++) for (int z = 15; z <= 17; z++) {
            boolean hull = z == 15 || z == 17 || x == bh0 + 2 || x == bh0 + 8;
            w.set(x, by + 1, z, hull ? "oak_planks" : "air");
        }
        w.set(bh0 + 5, by + 1, 16, "oak_fence");
        w.set(bh0 + 5, by + 2, 16, "oak_fence");
        w.set(bh0 + 5, by + 3, 16, "white_wool");
        w.set(bh0 + 1, by + 1, 13, "barrel[facing=up]");
        w.set(bh0 + 1, by + 1, 14, "barrel[facing=up]");
        w.hangLight(bh0 + 5, by + 3, 13, "lantern");
        // the quay: market stalls and crates of fish
        yard(bx - 3, -10, bx + 12, 10, by, P.of("smooth_sandstone", 2, "sandstone", 1, "birch_planks", 1), P.of("cut_sandstone"), 8);
        for (int[] s : new int[][] {{bx + 1, -8}, {bx + 6, -8}}) {
            for (int[] c : new int[][] {{-1, 0}, {1, 0}}) for (int dy = 1; dy <= 2; dy++) w.set(s[0] + c[0], by + dy, s[1], "birch_fence");
            for (int dx = -1; dx <= 1; dx++) w.set(s[0] + dx, by + 3, s[1], dx == 0 ? "TEAM_wool" : "white_wool");
            w.set(s[0], by + 1, s[1] - 1, "barrel[facing=up]");
        }
        base.flag = dais(bx + 7, by, P.of("dark_prismarine"), "birch_fence");
        for (int side : new int[] {-1, 1}) base.pads.add(pad(bx - 10, by, side * mz, P.of("prismarine_bricks", 1, "smooth_sandstone", 1), "birch_fence"));
        land.keeps.add(new Terrain.Keep(bx - 4, 0, 20));
        return base;
    }

    // ================================================================ 8. Mycelia: a mushroom village

    public Base mycelia(int bx, int by, int mz) {
        Base base = new Base();
        // the great mushroom house (spawn): a stem tower with a red cap roof
        int cx = bx - 10, r = 6;
        mushroomHouse(cx, by, 0, r, 8, true, "east");
        Room inside = new Room(cx - r + 1, -r + 1, cx + r - 1, r - 1, by, 7);
        a.rug(cx - 2, -2, cx + 2, 2, by + 1, "brown", "red");
        for (int z = -3; z <= 3; z += 3) a.bed(cx - 4, by + 1, z, "west", "brown");
        w.set(cx + 3, by + 1, -3, "brewing_stand");
        w.set(cx + 3, by + 1, 3, "cauldron");
        w.set(cx - 1, by + 1, -4, "barrel[facing=up]");
        w.set(cx + 1, by + 7, 0, "spore_blossom");
        w.set(cx - 1, by + 7, 0, "shroomlight");
        a.banner(cx - r + 1, by + 4, 0, "TEAM", "east");
        base.spawn = new double[] {cx + 0.5, by + 1, 0.5, -90};
        // two little houses and a lookout cap on a tall stem
        mushroomHouse(bx + 2, by, -12, 4, 5, false, "south");
        mushroomHouse(bx + 2, by, 12, 4, 5, true, "north");
        int lx = bx - 2, lz = -18;
        for (int dy = 1; dy <= 12; dy++) for (int dx = 0; dx <= 1; dx++) for (int dz = 0; dz <= 1; dz++) w.set(lx + dx, by + dy, lz + dz, "mushroom_stem");
        for (int dy = 1; dy <= 12; dy++) w.set(lx + 2, by + dy, lz, "ladder[facing=east]");
        for (int dx = -3; dx <= 4; dx++) for (int dz = -3; dz <= 4; dz++) {
            double d = Math.hypot(dx - 0.5, dz - 0.5);
            if (d > 4.2) continue;
            w.set(lx + dx, by + 13, lz + dz, d > 3.3 ? "brown_mushroom_block" : "oak_planks");
        }
        w.set(lx + 2, by + 13, lz, "air");
        w.set(lx, by + 14, lz + 2, "shroomlight");
        // boardwalks and glowing mushrooms in the yard
        yard(bx - 2, -7, bx + 12, 7, by, P.of("oak_planks", 2, "stripped_oak_wood", 1, "podzol", 1), null, 8);
        for (int[] g : new int[][] {{bx + 10, -6}, {bx + 10, 6}, {bx - 3, -8}, {bx - 3, 8}}) {
            w.set(g[0], by + 1, g[1], "mushroom_stem");
            w.set(g[0], by + 2, g[1], "shroomlight");
        }
        base.flag = dais(bx + 6, by, P.of("stripped_oak_wood"), "oak_fence");
        for (int side : new int[] {-1, 1}) base.pads.add(pad(bx - 10, by, side * mz, P.of("brown_mushroom_block", 1, "stripped_oak_wood", 2), "oak_fence"));
        land.keeps.add(new Terrain.Keep(bx - 4, 0, 20));
        return base;
    }

    /** A round house in a mushroom stem, with a domed red (or flat brown) cap, round windows and a door. */
    void mushroomHouse(int cx, int y, int cz, int r, int h, boolean red, String out) {
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > r + 0.45) continue;
                boolean shell = d > r - 0.8;
                for (int dy = 0; dy <= h; dy++) {
                    int x = cx + dx, z = cz + dz;
                    if (shell) w.set(x, y + dy, z, dy == 0 ? "stripped_oak_wood" : (dy == 3 || dy == 4) && (dx == 0 || dz == 0) ? "glass_pane" : "mushroom_stem");
                    else w.set(x, y + dy, z, dy == 0 ? "oak_planks" : "air");
                }
            }
        // the cap
        double cr = r + 2.5;
        int ic = (int) Math.ceil(cr);
        for (int dx = -ic; dx <= ic; dx++)
            for (int dz = -ic; dz <= ic; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > cr) continue;
                int lift = red ? (int) Math.round(Math.sqrt(Math.max(0, cr * cr - d * d)) * 0.7) : (d > cr - 1 ? -1 : 0);
                w.set(cx + dx, y + h + 1 + lift, cz + dz, red ? "red_mushroom_block" : "brown_mushroom_block");
                if (red && d > cr - 1.2) w.set(cx + dx, y + h + lift, cz + dz, "red_mushroom_block");
                if (d < r) for (int k = y + h + 1; k < y + h + 1 + lift; k++) w.set(cx + dx, k, cz + dz, "air");
            }
        for (int dx = -r + 1; dx <= r - 1; dx++) for (int dz = -r + 1; dz <= r - 1; dz++) if (Math.hypot(dx, dz) <= r - 0.8) w.set(cx + dx, y + h + 1, cz + dz, "oak_planks");
        int ox = Arch.dx(out), oz = Arch.dz(out);
        for (int dy = 1; dy <= 2; dy++) w.set(cx + ox * r, y + dy, cz + oz * r, "air");
        a.door(cx + ox * r, y, cz + oz * r, out, "oak_door");
        w.hangLight(cx, y + h, cz, "lantern");
        w.set(cx + ox * (r + 1), y + 3, cz + oz * (r + 1), "shroomlight");
    }

    // ================================================================ 9. Pale Grove: a gothic chapel

    public Base palegrove(int bx, int by, int mz) {
        Base base = new Base();
        P stone = P.of("deepslate_bricks", 5, "cracked_deepslate_bricks", 2, "deepslate_tiles", 1);
        int n0 = bx - 22, n1 = bx - 5, nz0 = -6, nz1 = 6;
        a.foundation(n0, nz0, n1, nz1, by, P.of("cobbled_deepslate"));
        a.storey(n0, nz0, n1, nz1, by, 9, stone, "polished_deepslate", "polished_deepslate", P.of("pale_oak_planks", 2, "polished_deepslate", 1), 3);
        // tall pointed windows of stained glass between buttresses
        for (int x = n0 + 1; x < n1; x++) {
            if ((x - n0) % 3 == 0) continue;
            if ((x - n0) % 3 != 1) continue;
            for (int dy = 2; dy <= 7; dy++) for (int z : new int[] {nz0, nz1}) w.set(x, by + dy, z, dy == 7 ? "purple_stained_glass_pane" : dy % 2 == 0 ? "light_gray_stained_glass_pane" : "magenta_stained_glass_pane");
        }
        for (int x = n0; x <= n1; x += 3) for (int z : new int[] {nz0 - 1, nz1 + 1}) {
            for (int dy = 1; dy <= 6; dy++) w.set(x, by + dy, z, "polished_deepslate");
            w.set(x, by + 7, z, "deepslate_tile_stairs[facing=" + (z < 0 ? "south" : "north") + "]");
        }
        // a rose window over the door, the door itself
        a.archway(n1, by, 0, "east", 3, 4, "deepslate_brick_stairs");
        for (int dz = -2; dz <= 2; dz++) for (int dy = 6; dy <= 8; dy++) if (Math.hypot(dz, dy - 7) <= 1.8) w.set(n1, by + dy, dz, (dz == 0 && dy == 7) ? "red_stained_glass_pane" : "purple_stained_glass_pane");
        int ridge = a.gable(n0, nz0, n1, nz1, by + 10, true, 1, "deepslate_tile_stairs", "deepslate_tile_slab", "deepslate_tiles", stone);
        // the bell tower with a spire at the back
        int tx = n0 + 2;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int yy = by + 10; yy <= ridge + 6; yy++) {
            boolean shell = Math.abs(dx) == 2 || Math.abs(dz) == 2;
            if (yy > ridge + 2 && yy < ridge + 6 && shell && (dx == 0 || dz == 0)) w.set(tx + dx, yy, dz, "air");
            else w.set(tx + dx, yy, dz, shell ? stone.pick(w.r) : "air");
        }
        w.set(tx, ridge + 4, 0, "bell[attachment=ceiling,facing=east]");
        w.set(tx, ridge + 5, 0, "polished_deepslate");
        a.hip(tx - 2, -2, tx + 2, 2, ridge + 7, 0, "deepslate_tile_stairs", "deepslate_tile_slab", "deepslate_tiles", null);
        for (int dy = 0; dy < 6; dy++) w.set(tx, ridge + 10 + dy, 0, dy < 4 ? "deepslate_tile_wall" : "lightning_rod");
        // inside: pews, an altar, candles and soul lanterns
        Room nave = new Room(n0, nz0, n1, nz1, by, 9);
        for (int x = n0 + 5; x <= n1 - 3; x += 2) for (int z : new int[] {-4, -3, -2, 2, 3, 4}) w.set(x, by + 1, z, "pale_oak_stairs[facing=east]");
        for (int x = n0 + 1; x <= n1 - 1; x++) w.set(x, by + 1, 0, "red_carpet");
        w.set(n0 + 2, by + 1, 0, "polished_deepslate");
        w.set(n0 + 2, by + 2, 0, "candle[candles=4,lit=true]");
        w.set(n0 + 2, by + 1, -1, "lectern[facing=east]");
        w.set(n0 + 2, by + 1, 2, "candle[candles=3,lit=true]");
        a.banner(n0 + 1, by + 6, 0, "TEAM", "east");
        for (int x = n0 + 4; x <= n1 - 3; x += 4) w.hangLight(x, by + 8, 0, "soul_lantern");
        a.bed(n0 + 3, by + 1, -4, "south", "black");
        a.bed(n0 + 3, by + 1, 3, "south", "black");
        base.spawn = new double[] {n0 + 5.5, by + 1, 0.5, -90};
        // a graveyard with iron fences and stones
        for (int x = bx - 2; x <= bx + 10; x++) for (int side : new int[] {-1, 1}) {
            int z = side * 14;
            w.set(x, by + 1, z, "iron_bars");
        }
        for (int x = bx; x <= bx + 8; x += 2) for (int z : new int[] {-11, -9, 9, 11}) {
            w.set(x, by + 1, z, "cobbled_deepslate_wall");
            w.set(x, by, z, "coarse_dirt");
        }
        yard(bx - 4, -6, bx + 12, 6, by, P.of("cobbled_deepslate", 2, "deepslate_tiles", 1, "polished_deepslate", 1), null, 8);
        base.flag = dais(bx + 6, by, P.of("polished_deepslate"), "pale_oak_fence");
        for (int side : new int[] {-1, 1}) base.pads.add(pad(bx - 6, by, side * mz, P.of("deepslate_tiles", 2, "polished_deepslate", 1), "pale_oak_fence"));
        land.keeps.add(new Terrain.Keep(bx - 6, 0, 22));
        return base;
    }

    // ================================================================ 10. Skyreach: an elven citadel

    public Base skyreach(int bx, int by, int mz) {
        Base base = new Base();
        P purpur = P.of("purpur_block", 4, "end_stone_bricks", 2, "purpur_pillar[axis=y]", 1);
        // a hall with a high vaulted roof and tall spires at its corners
        int h0 = bx - 18, h1 = bx - 5, hz0 = -6, hz1 = 6;
        a.storey(h0, hz0, h1, hz1, by, 8, purpur, "purpur_pillar", "end_stone_bricks", P.of("end_stone_bricks", 2, "purpur_block", 1), 3);
        for (int x = h0 + 1; x < h1; x++) {
            if ((x - h0) % 3 == 0) continue;
            for (int dy = 2; dy <= 6; dy++) for (int z : new int[] {hz0, hz1}) w.set(x, by + dy, z, dy == 6 ? "magenta_stained_glass_pane" : "purple_stained_glass_pane");
        }
        a.archway(h1, by, 0, "east", 3, 5, "purpur_stairs");
        a.gable(h0, hz0, h1, hz1, by + 9, true, 0, "purpur_stairs", "purpur_slab", "purpur_block", purpur);
        for (int[] c : new int[][] {{h0, hz0}, {h0, hz1}, {h1, hz0}, {h1, hz1}}) {
            for (int dy = 1; dy <= 18; dy++) w.set(c[0], by + dy, c[1], dy % 5 == 0 ? "amethyst_block" : "purpur_pillar[axis=y]");
            for (int dy = 19; dy <= 21; dy++) w.set(c[0], by + dy, c[1], "end_rod[facing=up]");
        }
        // inside: crystal light, a reflecting pool, beds and a library alcove
        Room hall = new Room(h0, hz0, h1, hz1, by, 8);
        for (int x = h0 + 4; x <= h1 - 4; x++) for (int z = -1; z <= 1; z++) w.set(x, by, z, "water");
        for (int x = h0 + 4; x <= h1 - 4; x++) for (int z : new int[] {-2, 2}) w.set(x, by, z, "purpur_pillar[axis=x]");
        a.shelves(h0 + 1, hz0 + 1, h0 + 3, hz0 + 1, by + 1, 3, "south");
        a.bed(h0 + 1, by + 1, 3, "east", "purple");
        a.bed(h0 + 1, by + 1, 1, "east", "purple");
        w.set(h0 + 1, by + 1, -1, "enchanting_table");
        a.banner(h0 + 1, by + 5, 0, "TEAM", "east");
        for (int x = h0 + 3; x <= h1 - 2; x += 4) for (int z : new int[] {-3, 3}) w.set(x, by + 7, z, "end_rod[facing=down]");
        base.spawn = new double[] {h0 + 3.5, by + 1, -3.5, -90};
        // a slender spire on each flank with a crystal at the top
        for (int side : new int[] {-1, 1}) {
            int sx = bx - 2, sz = side * 11;
            for (int dy = 1; dy <= 22; dy++) {
                int r = dy < 6 ? 2 : dy < 14 ? 1 : 0;
                for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) w.set(sx + dx, by + dy, sz + dz, dy % 6 == 0 ? "amethyst_block" : "purpur_block");
            }
            w.set(sx, by + 23, sz, "amethyst_cluster[facing=up]");
        }
        yard(bx - 4, -9, bx + 12, 9, by, P.of("end_stone_bricks", 2, "purpur_block", 1, "smooth_quartz", 1), P.of("purpur_pillar[axis=y]"), 8);
        for (int x = bx; x <= bx + 12; x += 4) for (int z : new int[] {-8, 8}) {
            w.set(x, by + 1, z, "purpur_pillar[axis=y]");
            w.set(x, by + 2, z, "end_rod[facing=up]");
        }
        base.flag = dais(bx + 7, by, P.of("purpur_pillar[axis=y]"), "end_rod[facing=up]");
        for (int side : new int[] {-1, 1}) base.pads.add(pad(bx + 1, by, side * mz, P.of("end_stone_bricks", 2, "purpur_block", 1), "purpur_pillar[axis=y]"));
        land.keeps.add(new Terrain.Keep(bx - 5, 0, 20));
        return base;
    }
}
