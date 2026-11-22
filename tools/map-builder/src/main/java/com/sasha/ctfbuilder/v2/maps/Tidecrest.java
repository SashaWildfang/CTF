package com.sasha.ctfbuilder.v2.maps;

import com.sasha.ctfbuilder.v2.Arena;
import com.sasha.ctfbuilder.v2.Kit;
import com.sasha.ctfbuilder.v2.Land;
import com.sasha.ctfbuilder.v2.Theme;
import com.sasha.ctfbuilder.v2.W.P;
import org.bukkit.World;

/**
 * Tidecrest: an atoll. One ring of sand and palms round a shallow turquoise lagoon, the two harbour towns at
 * its ends. Small and fast: three ways across — the sandbar straight over the lagoon past the islet fort in
 * the middle (quick, but in the open), or round either arc of the ring: one side a palm grove with a beach bar,
 * the other a rocky reef with tide pools and a wrecked ship to fight through. Each arc is half of each, so
 * both teams meet the same ground.
 */
public final class Tidecrest extends Arena {

    static final int WL = 63, PLATEAU = 67, ISLET = 67;

    public Tidecrest(World world) {
        super(world, "tidecrest", false, Theme.get("tidecrest"));
        name = "Tidecrest";
        icon = "HEART_OF_THE_SEA";
        minutes = 12;
        kothPoints = 100;
        reach = 96;
        low = 20;
        high = 120;
        voidY = 18;
        spectator = new double[] {0.5, 92, 0.5, -90, 45};
    }

    // ---------------------------------------------------------------- the land

    double inner(int x, int z) {
        return Land.ellipse(x, z, 0, 0, 45, 23, 0) + w.fbm(x + 40, z - 20, 12, 2) * 0.05;
    }

    double outer(int x, int z) {
        return Land.ellipse(x, z, 0, 0, 80, 47, 0) + w.fbm(x, z, 18, 2) * 0.035;
    }

    double islet(int x, int z) {
        return Land.ellipse(x, z, 0, 0, 10, 8.5, 0.5) + w.fbm(x - 9, z + 3, 6, 2) * 0.10;
    }

    boolean reef(int x, int z) {
        return z > 18 && x > -50 && x < -4;
    }

    double height(int x, int z) {
        double e = outer(x, z);
        if (e < 0) return Double.NaN;
        double in = inner(x, z);
        if (in > 0) {
            double isl = islet(x, z);
            if (isl > 0) return WL + 1 + Math.min(1, isl / 0.35) * (ISLET - WL - 1);
            double bar = Land.segment(x, z, -9, 3, -47, 7);
            if (bar < 2.3 + w.n(x, z, 4) * 0.5) return WL;
            if (w.fbm(x * 1.3 + 7, z * 1.3, 10, 2) > 0.42) return WL - 1; // shoals to wade over
            return WL - 3 - 1.5 * Land.smooth(in / 0.5);
        }
        double dIn = -in * 25, dOut = e * 45, d = Math.min(dIn, dOut);
        double h = WL + 1 + 2.2 * Land.smooth(d / 7) + w.fbm(x + 100, z, 14, 3) * 1.8 * Land.smooth(d / 6);
        if (reef(x, z)) h += Math.max(0, w.fbm(x * 2 + 5, z * 2 - 9, 9, 2)) * 3 * Land.smooth(d / 5);
        if (x < -46) {
            double p = Land.smooth((-46 - x) / 9.0);
            h = h * (1 - p) + PLATEAU * p;
        }
        if (dOut < 1.5) h -= 1;
        return h;
    }

    String surface(int x, int z, int y, int steep, double edge) {
        double n = w.fbm(x + 913, z - 377, 9, 2);
        if (y < WL) return n > 0.3 ? "gravel" : n < -0.35 ? "clay" : "sand";
        if (y == WL) return "sand";
        if (reef(x, z) && y > WL + 1) {
            // outcrops of rock in clusters, sand and shingle between them
            double rock = w.fbm(x * 0.8 + 31, z * 0.8 - 12, 7, 2);
            if (rock > 0.28) return rock > 0.45 ? (w.r.nextInt(5) == 0 ? "mossy_cobblestone" : "andesite") : (w.r.nextInt(3) == 0 ? "tuff" : "stone");
            return rock > 0.12 ? "gravel" : "sand";
        }
        if (steep >= 3 && x < -46) return w.r.nextBoolean() ? "sandstone" : "stone";
        if (y <= WL + 1 || edge < 2.5) return "sand";
        if (n > 0.32) return "sand";
        if (n < -0.45) return "coarse_dirt";
        return "grass_block";
    }

    // ---------------------------------------------------------------- building it

    @Override
    protected void build() {
        Land land = new Land(w, t, -90, 90, -56, 56);
        land.top = this::height;
        land.surface = this::surface;
        land.water = (x, z) -> inner(x, z) > 0 ? WL : Double.NaN;
        land.depth = 20;
        land.soil = P.of("sand", 3, "sandstone", 2, "dirt", 1);
        land.build();

        int bx = -68;
        harbourHall(bx, 0);
        plaza(-55, 0);
        lighthouse(-66, -15, PLATEAU);
        stiltHut(-33, -13);
        sandbar();
        palmGrove();
        reefAndWreck();
        isletFort();
        pads.add(new int[] {-56, PLATEAU + 1, -15});
        pads.add(new int[] {-56, PLATEAU + 1, 15});
        for (int[] p : pads) {
            level(p[0] - 3, p[2] - 3, p[0] + 3, p[2] + 3, PLATEAU, P.of("smooth_sandstone"), P.of("sandstone"));
            keepClear(p[0] - 4, p[2] - 4, p[0] + 4, p[2] + 4);
        }

        // plants: palms and a few broad trees on the grass, beach grass, cane by the water, seagrass in the lagoon
        trees(new Theme.Tree[] {Theme.Tree.PALM}, new int[] {1}, 0.02, 7, 2, (x, z) -> !reef(x, z) && inner(x, z) < -0.08);
        plants(P.of("short_grass", 8, "fern", 2, "dead_bush", 1, "dandelion", 1, "bush", 2, "short_dry_grass", 2), 0.22,
                (x, z) -> inner(x, z) < -0.05);
        for (int x = -90; x <= 0; x++)
            for (int z = -56; z <= 56; z++) {
                if (!w.canon(x, z)) continue;
                int gy = w.ground(x, z);
                if (gy < 50) continue;
                org.bukkit.Material m = w.type(x, gy, z);
                if (inner(x, z) > 0 && gy < WL - 1 && w.type(x, gy + 1, z) == org.bukkit.Material.WATER && w.r.nextInt(5) == 0) {
                    boolean tall = w.r.nextInt(3) == 0 && w.type(x, gy + 2, z) == org.bukkit.Material.WATER;
                    if (tall) {
                        w.set(x, gy + 1, z, "tall_seagrass[half=lower]");
                        w.set(x, gy + 2, z, "tall_seagrass[half=upper]");
                    } else {
                        w.set(x, gy + 1, z, w.r.nextInt(9) == 0 ? "sea_pickle[pickles=" + (1 + w.r.nextInt(4)) + ",waterlogged=true]" : "seagrass");
                    }
                }
                if ((m == org.bukkit.Material.SAND || m == org.bukkit.Material.GRASS_BLOCK) && gy == WL + 1 && w.air(x, gy + 1, z) && w.r.nextInt(4) == 0
                        && !isClear(x, z, 0)) {
                    boolean wet = false;
                    for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) wet |= w.type(x + d[0], gy, z + d[1]) == org.bukkit.Material.WATER;
                    if (wet) for (int i = 1; i <= 1 + w.r.nextInt(3); i++) w.set(x, gy + i, z, "sugar_cane");
                }
            }

        spawn = new double[] {bx + 0.5, PLATEAU + 1, 0.5, -90};
        flag = new int[] {-54, PLATEAU + 1, 0};
        hills.add(new Hill("Islet", -3, ISLET + 1, -3, 3, ISLET + 4, 3));
    }

    // ---------------------------------------------------------------- the harbour town

    /** The harbour hall the team spawns in: sandstone over a stone base, brick roof, an arched door to the plaza. */
    void harbourHall(int cx, int cz) {
        int x0 = cx - 6, x1 = cx + 6, z0 = cz - 5, z1 = cz + 5, y = PLATEAU;
        level(x0 - 2, z0 - 2, x1 + 2, z1 + 2, y, P.of("smooth_sandstone"), P.of("sandstone"));
        k.footing(x0, z0, x1, z1, y - 1, P.of("stone_bricks", 3, "sandstone", 1));
        Kit.Bands bands = Kit.Bands.of(0, P.of("stone_bricks", 4, "mossy_stone_bricks", 1, "cracked_stone_bricks", 1),
                2, P.of("cut_sandstone", 3, "smooth_sandstone", 2, "sandstone", 1), 6, "smooth_sandstone");
        k.walls(x0, z0, x1, z1, y, 6, bands, "stripped_oak_log", 4, P.of("oak_planks", 3, "stripped_oak_wood", 1));
        k.beam(x0, z0, x1, z1, y + 6, "stripped_oak_log");
        // windows with shutters, two per long wall and one in each gable
        for (int x : new int[] {x0 + 2, x1 - 2}) {
            k.window(x, y + 2, z0, "north", 1, 2, "glass_pane", "sandstone_stairs", "oak_trapdoor");
            k.window(x, y + 2, z1, "south", 1, 2, "glass_pane", "sandstone_stairs", "oak_trapdoor");
        }
        k.window(x0, y + 2, cz, "west", 2, 2, "glass_pane", "sandstone_stairs", null);
        // the roof, its gables in sandstone with a round window
        k.gable(x0, z0, x1, z1, y + 7, true, 1, Kit.Roof.of("bricks"), P.of("smooth_sandstone"));
        w.set(x0, y + 9, cz, "glass_pane");
        w.set(x1, y + 9, cz, "glass_pane");
        // the door: a 3-wide arch to the plaza, brackets with lanterns either side, team banners
        for (int dz = -1; dz <= 1; dz++) for (int dy = 1; dy <= 3; dy++) w.set(x1, y + dy, cz + dz, "air");
        w.set(x1, y + 4, cz, "chiseled_sandstone");
        w.set(x1, y + 3, cz - 1, "sandstone_stairs[facing=south,half=top]");
        w.set(x1, y + 3, cz + 1, "sandstone_stairs[facing=north,half=top]");
        for (int side : new int[] {-3, 3}) {
            w.set(x1 + 1, y + 4, cz + side, "oak_fence");
            w.set(x1 + 1, y + 3, cz + side, "lantern[hanging=true]");
            w.set(x1 + 1, y + 5, cz + side + (side < 0 ? 1 : -1), "TEAM_wall_banner[facing=east]");
        }
        // inside: benches along the walls, a chart table, nets and crates; light from above
        for (int x = x0 + 2; x <= x1 - 2; x++) {
            if (x == cx) continue;
            w.set(x, y + 1, z0 + 1, "oak_stairs[facing=north,half=bottom]");
            w.set(x, y + 1, z1 - 1, "oak_stairs[facing=south,half=bottom]");
        }
        w.set(x0 + 1, y + 1, z0 + 1, "barrel[facing=up]");
        w.set(x0 + 1, y + 2, z0 + 1, "barrel[facing=up]");
        w.set(x0 + 1, y + 1, z1 - 1, "barrel[facing=up]");
        w.set(x0 + 1, y + 1, z1 - 2, "chest[facing=east]");
        w.set(x0 + 1, y + 1, cz, "cartography_table");
        w.set(x0 + 1, y + 1, cz - 1, "lectern[facing=east]");
        for (int x = x0 + 2; x <= x1 - 2; x++) w.set(x, y + 1, cz, x == x0 + 2 ? "TEAM_carpet" : "TEAM_carpet");
        for (int x = x0 + 3; x <= x1 - 3; x += 3) w.hangLight(x, y + 5, cz, "lantern");
        // the beams across, so the lanterns hang from something
        for (int x = x0 + 3; x <= x1 - 3; x += 3) for (int z = z0 + 1; z < z1; z++) w.set(x, y + 6, z, "stripped_oak_log[axis=z]");
        keepClear(x0 - 2, z0 - 2, x1 + 2, z1 + 2);
    }

    /** The plaza in front of the hall: patterned sandstone, the flag on a dais, market stalls at the corners. */
    void plaza(int cx, int cz) {
        int y = PLATEAU;
        for (int x = cx - 6; x <= cx + 6; x++)
            for (int z = cz - 8; z <= cz + 8; z++) {
                boolean border = x == cx - 6 || x == cx + 6 || Math.abs(z - cz) == 8;
                boolean ring = Math.abs(Math.hypot(x - cx, z - cz) - 4) < 0.6;
                String block = border ? "cut_sandstone" : ring ? "chiseled_sandstone" : ((x + z) & 1) == 0 ? "smooth_sandstone" : "sandstone";
                w.set(x, y, z, block);
                for (int dy = 1; dy <= 8; dy++) if (!w.air(x, y + dy, z)) w.set(x, y + dy, z, "air");
                for (int yy = y - 1; yy > y - 10 && !w.type(x, yy, z).isSolid(); yy--) w.set(x, yy, z, "sandstone");
            }
        // the flag's dais
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) w.set(cx + 1 + dx, y, cz + dz, dx == 0 && dz == 0 ? "chiseled_sandstone" : "smooth_sandstone");
        // stalls with striped awnings in the team colour
        for (int side : new int[] {-1, 1}) {
            int sx = cx - 4, sz = cz + side * 6;
            for (int dx : new int[] {-1, 2}) for (int dy = 1; dy <= 3; dy++) w.set(sx + dx, y + dy, sz + side, "oak_fence");
            for (int dx = -2; dx <= 3; dx++) for (int dz = 0; dz <= 1; dz++) {
                int z = sz + side * dz;
                w.set(sx + dx, y + 4, z, ((dx & 1) == 0 ? "TEAM" : "white") + "_wool");
            }
            for (int dx = -1; dx <= 2; dx++) w.set(sx + dx, y + 1, sz, dx == 0 ? "barrel[facing=up]" : dx == 1 ? "composter" : "oak_planks");
            w.set(sx - 1, y + 2, sz, "potted_red_tulip");
            w.set(sx + 2, y + 2, sz, "melon");
        }
        // planters with small palms at the open corners toward the lagoon
        for (int side : new int[] {-1, 1}) {
            int px = cx + 5, pz = cz + side * 6;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                w.set(px + dx, y + 1, pz + dz, dx == 0 && dz == 0 ? "grass_block" : "sandstone_slab[type=bottom]");
            }
            w.set(px, y, pz, "dirt");
            flora.tree(Theme.Tree.PALM, px, y + 2, pz);
        }
        keepClear(cx - 8, cz - 9, cx + 7, cz + 9);
    }

    /** A lighthouse: stone base, banded tower, gallery with a railing, lantern room and a copper cap. */
    void lighthouse(int cx, int cz, int y) {
        level(cx - 5, cz - 5, cx + 5, cz + 5, y, P.of("smooth_sandstone", 2, "sandstone", 1), P.of("sandstone"));
        for (int dx = -5; dx <= 5; dx++)
            for (int dz = -5; dz <= 5; dz++) {
                double d = Math.max(Math.max(Math.abs(dx), Math.abs(dz)), (Math.abs(dx) + Math.abs(dz)) * 0.72);
                int x = cx + dx, z = cz + dz;
                if (d <= 4.3) {
                    k.footing(x, z, x, z, y - 1, P.of("stone_bricks"));
                    for (int dy = 0; dy <= 2; dy++) w.set(x, y + dy, z, d > 3.4 ? (dy == 2 ? "stone_brick_slab[type=bottom]" : "stone_bricks") : "stone_bricks");
                }
                double r = Math.hypot(dx, dz);
                for (int dy = 3; dy <= 20; dy++) {
                    double rr = 3.3 - (dy - 3) * 0.035;
                    if (r > rr + 0.4) continue;
                    boolean shell = r > rr - 0.6;
                    String band = ((dy - 3) / 3) % 2 == 0 ? "white_concrete" : "TEAM_concrete";
                    w.set(x, y + dy, z, shell ? band : "air");
                }
            }
        // windows up the tower
        for (int dy : new int[] {6, 11, 16}) w.set(cx + 3, y + dy, cz, "glass_pane");
        // door to the east, a ladder up the inside
        w.set(cx + 4, y + 1, cz, "air");
        w.set(cx + 4, y + 2, cz, "air");
        for (int dx = 3; dx >= 0; dx--) { w.set(cx + dx, y + 1, cz, "air"); w.set(cx + dx, y + 2, cz, "air"); }
        w.set(cx + 4, y + 3, cz, "stone_brick_stairs[facing=west,half=top]");
        for (int dy = 1; dy <= 2; dy++) for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++)
            if (Math.hypot(dx, dz) <= 2.6 && !(dz == 0 && dx >= 0)) w.set(cx + dx, y + dy, cz + dz, "air");
        for (int dy = 1; dy <= 21; dy++) w.set(cx - 2, y + dy, cz, "ladder[facing=east]");
        // gallery and railing
        int g = y + 21;
        for (int dx = -5; dx <= 5; dx++)
            for (int dz = -5; dz <= 5; dz++) {
                double r = Math.hypot(dx, dz);
                if (r > 4.5) continue;
                w.set(cx + dx, g, cz + dz, r > 3.6 ? "smooth_stone_slab[type=top]" : "smooth_stone");
                if (r > 3.6) w.set(cx + dx, g + 1, cz + dz, "iron_bars");
            }
        w.set(cx - 2, g, cz, "spruce_trapdoor[facing=east,half=top,open=false]");
        // the lantern room
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                double r = Math.hypot(dx, dz);
                if (r > 2.5) continue;
                for (int dy = 1; dy <= 3; dy++) {
                    boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                    if (corner) continue;
                    boolean edge = r > 1.5;
                    w.set(cx + dx, g + dy, cz + dz, edge ? (Math.abs(dx) == 2 && Math.abs(dz) == 1 || Math.abs(dz) == 2 && Math.abs(dx) == 1 ? "iron_bars" : "glass_pane") : "air");
                }
            }
        w.set(cx, g + 1, cz, "polished_andesite");
        w.set(cx, g + 2, cz, "sea_lantern");
        k.dome(cx, cz, g + 4, 2.6, new Kit.Roof("waxed_weathered_cut_copper_stairs", "waxed_weathered_cut_copper_slab", "waxed_weathered_cut_copper"), null);
        w.set(cx, g + 7, cz, "waxed_weathered_cut_copper_slab[type=bottom]");
        keepClear(cx - 6, cz - 6, cx + 6, cz + 6);
    }

    /** A fishing hut on stilts over the lagoon, a jetty back to the shore and a rowboat tied up. */
    void stiltHut(int cx, int cz) {
        int y = WL + 2;
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            boolean post = Math.abs(dx) == 3 && Math.abs(dz) == 3;
            if (post) for (int yy = y; yy > WL - 6; yy--) {
                if (yy < y && w.type(cx + dx, yy, cz + dz).isSolid() && !w.type(cx + dx, yy, cz + dz).name().contains("LOG")) break;
                w.set(cx + dx, yy, cz + dz, "stripped_jungle_log[axis=y]");
            }
            w.set(cx + dx, y, cz + dz, Math.abs(dx) == 3 || Math.abs(dz) == 3 ? "stripped_bamboo_block[axis=y]" : "bamboo_mosaic");
        }
        k.walls(cx - 2, cz - 2, cx + 2, cz + 2, y, 3, Kit.Bands.of(0, "bamboo_planks"), "bamboo_block", 0, P.of("bamboo_mosaic"));
        k.hip(cx - 2, cz - 2, cx + 2, cz + 2, y + 4, 1, new Kit.Roof("bamboo_mosaic_stairs", "bamboo_mosaic_slab", "bamboo_mosaic"), P.of("bamboo_planks"));
        // door toward the shore (west), windows on the other sides
        w.set(cx - 2, y + 1, cz, "bamboo_door[facing=east,half=lower,hinge=left,open=false]");
        w.set(cx - 2, y + 2, cz, "bamboo_door[facing=east,half=upper,hinge=left,open=false]");
        k.window(cx + 2, y + 2, cz, "east", 1, 1, "glass_pane", null, "bamboo_trapdoor");
        k.window(cx, y + 2, cz - 2, "north", 1, 1, "glass_pane", null, "bamboo_trapdoor");
        w.set(cx + 1, y + 1, cz - 1, "barrel[facing=up]");
        k.bed(cx, y + 1, cz + 1, "east", "TEAM");
        w.hangLight(cx, y + 3, cz, "lantern");
        // nets drying on the deck rail
        for (int dz = -3; dz <= 3; dz++) w.set(cx + 3, y + 1, cz + dz, dz == 0 ? "bamboo_fence_gate[facing=east]" : "bamboo_fence");
        // the jetty west to the shore
        for (int x = cx - 4; x >= cx - 16; x--) {
            int gy = w.ground(x, cz);
            if (gy >= y) break;
            w.set(x, y, cz, "bamboo_planks");
            w.set(x, y, cz + 1, "bamboo_planks");
            if ((x & 3) == 0) for (int yy = y - 1; yy > gy; yy--) w.set(x, yy, cz + 2, "stripped_jungle_log[axis=y]");
            if ((x & 3) == 0) w.set(x, y + 1, cz + 2, "bamboo_fence");
        }
        // a rowboat on the water beside it
        int bz = cz + 5;
        for (int dx = -2; dx <= 2; dx++) {
            String side = dx == -2 ? "spruce_stairs[facing=east,half=bottom]" : dx == 2 ? "spruce_stairs[facing=west,half=bottom]" : "spruce_slab[type=bottom]";
            w.set(cx + dx, WL, bz, side);
            if (Math.abs(dx) < 2) {
                w.set(cx + dx, WL + 1, bz - 1, "spruce_trapdoor[facing=north,half=bottom,open=true]");
                w.set(cx + dx, WL + 1, bz + 1, "spruce_trapdoor[facing=south,half=bottom,open=true]");
            }
        }
        keepClear(cx - 4, cz - 4, cx + 4, cz + 6);
    }

    /** Posts along the sandbar, a lamp halfway, a ring of stones where it meets the islet. */
    void sandbar() {
        for (int i = 1; i <= 6; i++) {
            double k2 = i / 7.0;
            int x = (int) Math.round(-47 + (38) * k2), z = (int) Math.round(7 - 4 * k2);
            for (int side : new int[] {-3, 3}) {
                int px = x, pz = z + side;
                int gy = w.ground(px, pz);
                if (gy < 50) continue;
                for (int yy = gy + 1; yy <= WL + 2; yy++) w.set(px, yy, pz, "stripped_spruce_log[axis=y]");
                if (i == 3) w.set(px, WL + 3, pz, "lantern[hanging=false]");
            }
        }
        keepClear(-47, 3, -9, 11);
    }

    // ---------------------------------------------------------------- the arcs

    /** The north arc near home: palms, a bamboo beach bar, a rowboat pulled up on the sand, deck chairs. */
    void palmGrove() {
        int bx = -26, bz = -32, gy = w.ground(bx, bz);
        if (gy < 50) return;
        level(bx - 5, bz - 4, bx + 5, bz + 4, gy, P.of("sand"), P.of("sandstone"));
        // the bar: a counter under a thatch of bamboo on four posts
        for (int[] p : new int[][] {{-3, -2}, {3, -2}, {-3, 2}, {3, 2}}) for (int dy = 1; dy <= 3; dy++) w.set(bx + p[0], gy + dy, bz + p[1], "bamboo_block[axis=y]");
        k.hip(bx - 3, bz - 2, bx + 3, bz + 2, gy + 4, 1, new Kit.Roof("bamboo_mosaic_stairs", "bamboo_mosaic_slab", "bamboo_mosaic"), null);
        for (int dx = -2; dx <= 2; dx++) {
            w.set(bx + dx, gy + 1, bz - 1, "bamboo_planks");
            w.set(bx + dx, gy + 2, bz - 1, dx == 0 ? "flower_pot" : "bamboo_slab[type=bottom]");
        }
        w.set(bx - 2, gy + 1, bz - 2, "barrel[facing=up]");
        w.set(bx + 2, gy + 1, bz - 2, "barrel[facing=up]");
        for (int dx : new int[] {-2, 0, 2}) w.set(bx + dx, gy + 1, bz + 1, "bamboo_stairs[facing=south,half=bottom]");
        w.hangLight(bx, gy + 3, bz, "lantern");
        // deck chairs facing the lagoon, a rowboat upside down on the sand
        for (int dx : new int[] {-8, -6}) {
            int cy = w.ground(bx + dx, bz + 6);
            if (cy > 50) w.set(bx + dx, cy + 1, bz + 6, "birch_stairs[facing=north,half=bottom]");
        }
        int ry = w.ground(bx + 10, bz - 3);
        if (ry > 50) for (int dx = -2; dx <= 2; dx++) {
            w.set(bx + 10 + dx, ry + 1, bz - 3, Math.abs(dx) == 2 ? "spruce_stairs[facing=" + (dx < 0 ? "west" : "east") + ",half=top]" : "spruce_slab[type=top]");
        }
        keepClear(bx - 6, bz - 5, bx + 6, bz + 5);
    }

    /** The south arc near home: rock stacks with an arch, tide pools, and a wreck on its side. */
    void reefAndWreck() {
        // two rock stacks with an arch between them
        int ax = -36, az = 33, ay = w.ground(ax, az);
        if (ay > 50) {
            for (int[] st : new int[][] {{0, 0, 7}, {7, 2, 6}}) {
                for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) for (int dy = 0; dy <= st[2]; dy++) {
                    double rr = 2.6 - dy * 0.15 + w.n3(ax + st[0] + dx, ay + dy, az + st[1] + dz, 2) * 0.8;
                    if (Math.hypot(dx, dz) > rr) continue;
                    w.set(ax + st[0] + dx, ay + dy, az + st[1] + dz, w.r.nextInt(5) == 0 ? "tuff" : w.r.nextInt(4) == 0 ? "mossy_cobblestone" : "andesite");
                }
            }
            for (int i = 0; i <= 7; i++) {
                int h = (int) Math.round(Math.sin(i / 7.0 * Math.PI) * 2.5);
                for (int dz = 0; dz <= 2; dz++) for (int dy = 0; dy <= 1; dy++) w.set(ax + i, ay + 5 + h + dy, az + dz, dy == 1 && w.r.nextBoolean() ? "moss_block" : "andesite");
            }
            keepClear(ax - 4, az - 4, ax + 11, az + 6);
        }
        // tide pools
        for (int[] p : new int[][] {{-44, 26}, {-18, 27}, {-10, 40}}) {
            int py = w.ground(p[0], p[1]);
            if (py < 50) continue;
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                if (Math.hypot(dx, dz) > 2.3) continue;
                w.set(p[0] + dx, py, p[1] + dz, "water");
                w.set(p[0] + dx, py - 1, p[1] + dz, w.r.nextBoolean() ? "gravel" : "clay");
                if (w.r.nextInt(4) == 0) w.set(p[0] + dx, py, p[1] + dz, "sea_pickle[pickles=2,waterlogged=true]");
            }
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > 3.3 || d <= 2.3) continue;
                w.set(p[0] + dx, py, p[1] + dz, w.r.nextBoolean() ? "mossy_cobblestone" : "tuff");
            }
        }
        wreck(-22, 34);
    }

    /**
     * A wrecked ship run aground: the bow buried in the sand, the stern up, its starboard side stove in so the
     * ribs show, the mast snapped with the yard lying across the deck, cargo spilled on the beach.
     */
    void wreck(int cx, int cz) {
        int gy = w.ground(cx, cz);
        if (gy < 50) return;
        int[] halfAt = {1, 2, 3, 3};          // hull half-width by height above the keel
        for (int i = -9; i <= 9; i++) {
            double taper = 1 - Math.pow(Math.abs(i + 0.5) / 10.0, 3);
            int keel = gy - 1 + (int) Math.round((i + 9) * 0.22);  // bow (−x) sunk, stern raised
            for (int h = 0; h < halfAt.length; h++) {
                int half = Math.max(0, (int) Math.round(halfAt[h] * taper));
                for (int s = -half; s <= half; s++) {
                    int x = cx + i, y = keel + h, z = cz + s;
                    boolean side = Math.abs(s) == half;
                    boolean bottom = h == 0;
                    if (!side && !bottom) {
                        w.set(x, y, z, "air");
                        continue;
                    }
                    // the stove-in side: planks gone between the ribs
                    boolean breach = s > 0 && i > -5 && i < 4 && h >= 1 && h <= 2;
                    boolean rib = (i & 1) == 0;
                    if (breach && !rib) continue;
                    String spec = rib && side ? "stripped_dark_oak_log[axis=y]" : h == 3 ? "spruce_planks" : "dark_oak_planks";
                    w.set(x, y, z, spec);
                }
            }
            // the gunwale rail and the deck at the stern end
            int top = keel + halfAt.length;
            int half = Math.max(0, (int) Math.round(3 * taper));
            if (i > 3) for (int s = -half + 1; s <= half - 1; s++) w.set(cx + i, top - 1, cz + s, "spruce_slab[type=top]");
            if (Math.abs(i) < 8 && w.r.nextInt(3) != 0) {
                w.set(cx + i, top, cz - half, "dark_oak_fence");
                if (half > 0 && !(i > -5 && i < 4)) w.set(cx + i, top, cz + half, "dark_oak_fence");
            }
        }
        // sand drifted into the bow
        for (int i = -9; i <= -4; i++) for (int s = -2; s <= 2; s++) {
            int x = cx + i, z = cz + s;
            int keel = gy - 1 + (int) Math.round((i + 9) * 0.22);
            for (int y = keel + 1; y <= keel + 1 + (-4 - i) / 2; y++) if (w.air(x, y, z)) w.set(x, y, z, "sand");
        }
        // the bowsprit and the stern cabin wall
        int bowY = gy - 1 + 3;
        for (int i = 1; i <= 3; i++) w.set(cx - 9 - i, bowY + i / 2, cz, "dark_oak_fence");
        int sternKeel = gy - 1 + (int) Math.round(18 * 0.22);
        for (int s = -2; s <= 2; s++) for (int h = 4; h <= 5; h++) w.set(cx + 9, sternKeel + h, cz + s, h == 5 && Math.abs(s) == 2 ? "dark_oak_fence" : "dark_oak_planks");
        w.set(cx + 9, sternKeel + 4, cz, "glass_pane");
        // the mast snapped above the deck, its top and the yard lying over the side on the sand
        int mastX = cx + 1, mastBase = gy - 1 + (int) Math.round(10 * 0.22);
        for (int h = 0; h <= 7; h++) w.set(mastX, mastBase + h, cz, "stripped_spruce_log[axis=y]");
        for (int i = 0; i <= 7; i++) {
            int x = mastX - 2 - i, z = cz - 5;
            int y = w.ground(x, z) + 1;
            if (y > 50) w.set(x, y, z, "stripped_spruce_log[axis=x]");
        }
        for (int i = -3; i <= 3; i++) {
            int z = cz - 5 + i, x = mastX - 6;
            int y = w.ground(x, z) + 1;
            if (y > 50 && i != 0) w.set(x, y, z, "spruce_fence");
        }
        // a torn sail caught on the stump
        w.set(mastX, mastBase + 7, cz + 1, "white_wool");
        w.set(mastX, mastBase + 6, cz + 1, "white_wool");
        w.set(mastX, mastBase + 6, cz + 2, "white_carpet");
        // cargo on the beach
        for (int[] p : new int[][] {{3, 6}, {4, 7}, {-2, 6}, {6, -5}}) {
            int x = cx + p[0], z = cz + p[1], y = w.ground(x, z) + 1;
            if (y > 50) w.set(x, y, z, w.r.nextBoolean() ? "barrel[facing=up]" : "chest[facing=north]");
        }
        keepClear(cx - 13, cz - 7, cx + 10, cz + 8);
    }

    // ---------------------------------------------------------------- the middle

    /** The islet: a ruined round fort with broken walls and four gaps, an open court for the hill. */
    void isletFort() {
        int y = ISLET;
        for (int x = -9; x <= 9; x++)
            for (int z = -9; z <= 9; z++) {
                if (!w.canon(x, z)) continue;
                double d = Math.hypot(x, z);
                if (d <= 4.6) {
                    c(x, y, z, d > 3.6 ? "mossy_stone_bricks" : ((x + z) & 1) == 0 ? "stone_bricks" : "cracked_stone_bricks");
                    for (int dy = 1; dy <= 6; dy++) c(x, y + dy, z, "air");
                    for (int yy = y - 1; yy > y - 6 && !w.type(x, yy, z).isSolid(); yy--) c(x, yy, z, "stone_bricks");
                }
                if (d > 6.4 && d <= 7.5) {
                    double ang = Math.atan2(z, x);
                    double gap = Math.abs(Math.sin(ang * 2));
                    if (gap < 0.28) continue; // four ways in
                    int gy = w.ground(x, z);
                    if (gy < 55) continue;
                    int h = 1 + (int) Math.round((w.n(x * 3, z * 3, 3) + 1) * 2);
                    for (int dy = 1; dy <= h; dy++) c(x, gy + dy, z, w.r.nextInt(4) == 0 ? "mossy_stone_bricks" : w.r.nextInt(4) == 0 ? "cracked_stone_bricks" : "stone_bricks");
                    if (h >= 3 && w.r.nextInt(3) == 0) c(x, gy + h + 1, z, "stone_brick_slab[type=bottom]");
                }
            }
        // a fallen column across one of the ways in
        for (int i = 0; i < 4; i++) c(-6 - i, w.ground(-6 - i, -3) + 1, -3, "stone_brick_wall");
        keepClear(-10, -10, 10, 10);
    }
}
