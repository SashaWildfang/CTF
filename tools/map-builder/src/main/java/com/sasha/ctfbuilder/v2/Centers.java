package com.sasha.ctfbuilder.v2;

import com.sasha.ctfbuilder.v2.W.P;

/**
 * What sits in the middle of each map. The middle of the centre (the hill in King of the Hill) is always a
 * paved plaza open to the sky: the plugin puts the hill's beacon there only when that mode is played.
 */
public final class Centers {

    final W w;
    final Theme t;
    final Terrain land;
    final Arch a;

    public Centers(W w, Theme t, Terrain land) {
        this.w = w;
        this.t = t;
        this.land = land;
        this.a = new Arch(w, t, land);
    }

    /** The paved middle: a disc of {@code pave} with a {@code ring} border, sky clear above. */
    void plaza(int y, double r, P pave, P ring) {
        int ir = (int) Math.ceil(r);
        for (int x = -ir; x <= ir; x++)
            for (int z = -ir; z <= ir; z++) {
                if (!w.canon(x, z)) continue;
                double d = Math.hypot(x, z);
                if (d > r) continue;
                for (int dy = 1; dy <= 40; dy++) if (!w.air(x, y + dy, z)) w.set(x, y + dy, z, "air");
                w.set(x, y, z, d > r - 1.2 ? ring.pick(w.r) : pave.pick(w.r));
                for (int dy = 1; dy <= 3; dy++) if (w.air(x, y - dy, z)) w.set(x, y - dy, z, t.under.pick(w.r));
            }
        land.keeps.add(new Terrain.Keep(0, 0, r + 3));
    }

    /** Hill corners for the map file: {min, max}. */
    static int[][] hill(int y) {
        return new int[][] {{-4, y + 1, -4}, {4, y + 4, 4}};
    }

    // ---------------------------------------------------------------- 1. Frostfall: a frozen lake and a runestone circle

    public int frostfall(int y) {
        // the lake: ice over water, the hill in its middle on a stone islet
        for (int x = -16; x <= 16; x++)
            for (int z = -16; z <= 16; z++) {
                if (!w.canon(x, z)) continue;
                double d = Math.hypot(x, z) + w.n(x, z, 4) * 1.5;
                if (d > 16) continue;
                for (int dy = 1; dy <= 30; dy++) if (!w.air(x, y + dy, z)) w.set(x, y + dy, z, "air");
                if (d > 7.5) {
                    w.set(x, y - 1, z, "water");
                    w.set(x, y, z, w.r.nextInt(6) == 0 ? "packed_ice" : "ice");
                }
            }
        plaza(y, 7.5, P.of("stone_bricks", 3, "polished_andesite", 2, "snow_block", 1), P.of("mossy_stone_bricks"));
        // the runestones: tall slabs of stone in a ring on the shore
        for (int i = 0; i < 8; i++) {
            double ang = i * Math.PI / 4 + Math.PI / 8;
            int x = (int) Math.round(Math.cos(ang) * 19), z = (int) Math.round(Math.sin(ang) * 19);
            if (!w.canon(x, z)) continue;
            int gy = w.ground(x, z);
            if (gy < y - 4) gy = y;
            int h = 5 + (i % 3);
            for (int dy = 1; dy <= h; dy++) {
                w.set(x, gy + dy, z, dy == h - 1 ? "chiseled_stone_bricks" : "polished_andesite");
                w.set(x + (Math.abs(z) > Math.abs(x) ? 1 : 0), gy + dy, z + (Math.abs(z) > Math.abs(x) ? 0 : 1), dy < h ? "andesite" : "air");
            }
            w.set(x, gy + h + 1, z, "snow[layers=2]");
        }
        land.keeps.add(new Terrain.Keep(0, 0, 21));
        return y;
    }

    // ---------------------------------------------------------------- 2. Dustbowl: an oasis among ruined obelisks

    public int dustbowl(int y) {
        plaza(y, 9, P.of("smooth_sandstone", 3, "cut_sandstone", 1, "chiseled_sandstone", 0), P.of("cut_red_sandstone"));
        // the oasis pool round the plaza, palms on its banks
        for (int x = -17; x <= 17; x++)
            for (int z = -17; z <= 17; z++) {
                if (!w.canon(x, z)) continue;
                double d = Math.hypot(x, z) + w.n(x, z, 5) * 1.6;
                if (d <= 10.5 || d > 15) continue;
                boolean bridgeLane = Math.abs(z) <= 2 || Math.abs(x) <= 2;
                for (int dy = 1; dy <= 12; dy++) if (!w.air(x, y + dy, z)) w.set(x, y + dy, z, "air");
                if (bridgeLane) w.set(x, y, z, "smooth_sandstone");
                else {
                    w.set(x, y, z, "water");
                    w.set(x, y - 1, z, d < 13 ? "water" : "sand");
                    w.set(x, y - 2, z, "sand");
                }
            }
        for (int i = 0; i < 6; i++) {
            double ang = i * Math.PI / 3 + 0.4;
            int x = (int) Math.round(Math.cos(ang) * 17.5), z = (int) Math.round(Math.sin(ang) * 17.5);
            if (!w.canon(x, z)) continue;
            int gy = w.ground(x, z);
            if (gy > 0) new Flora(w, t, land).tree(Theme.Tree.PALM, x, gy + 1, z);
        }
        // two broken obelisks (they're copied to the other side, four in all)
        for (int[] o : new int[][] {{-6, -20}, {-20, 6}}) {
            int gy = w.ground(o[0], o[1]);
            if (gy < 0) continue;
            int h = 9 + (o[0] & 3);
            for (int dy = 1; dy <= h; dy++) {
                int r = dy < 3 ? 1 : 0;
                for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) w.set(o[0] + dx, gy + dy, o[1] + dz, dy % 4 == 0 ? "chiseled_red_sandstone" : "cut_red_sandstone");
            }
            w.set(o[0], gy + h + 1, o[1], "cut_red_sandstone_slab[type=bottom]");
        }
        land.keeps.add(new Terrain.Keep(0, 0, 21));
        return y;
    }

    // ---------------------------------------------------------------- 3. Four Kingdoms: the tournament ground

    public int kingdoms(int y) {
        plaza(y, 10, P.of("stone_bricks", 3, "polished_andesite", 2, "cobblestone", 1), P.of("chiseled_stone_bricks", 1, "stone_bricks", 2));
        // a ring of stands with banners for the four kingdoms, a fence round the lists
        for (int x = -16; x <= 16; x++)
            for (int z = -16; z <= 16; z++) {
                if (!w.canon(x, z)) continue;
                double d = Math.hypot(x, z);
                if (d <= 10 || d > 14) continue;
                for (int dy = 1; dy <= 10; dy++) if (!w.air(x, y + dy, z)) w.set(x, y + dy, z, "air");
                w.set(x, y, z, "dirt_path");
                boolean gap = Math.abs(x) <= 2 || Math.abs(z) <= 2;
                if (Math.abs(d - 13.5) < 0.6 && !gap) w.set(x, y + 1, z, "spruce_fence");
            }
        // the canonical quarter's stand: tiered seats under an awning in the team colour
        for (int i = 0; i < 5; i++) {
            int x = -13 + i, z = -8 - i / 2;
            for (int row = 0; row < 3; row++) {
                int sx = -16 - row, sz = -4 + i * 2;
                if (!w.canon(sx, sz)) continue;
                w.set(sx, y + 1 + row, sz, "spruce_stairs[facing=west]");
                for (int dy = 1; dy <= row; dy++) w.set(sx, y + dy, sz, "spruce_planks");
            }
        }
        for (int dz = -5; dz <= 5; dz++) for (int dx = 0; dx <= 3; dx++) if (w.canon(-15 - dx, dz)) w.set(-15 - dx, y + 6, dz, ((dz & 1) == 0) ? "TEAM_wool" : "white_wool");
        for (int dz : new int[] {-5, 5}) for (int dy = 1; dy <= 5; dy++) if (w.canon(-15, dz)) w.set(-15, y + dy, dz, "spruce_fence");
        // four tall flagpoles at the diagonals
        int fx = -9, fz = -9;
        for (int dy = 1; dy <= 11; dy++) w.set(fx, y + dy, fz, dy == 11 ? "stripped_spruce_log[axis=y]" : "spruce_fence");
        w.set(fx + 1, y + 11, fz, "TEAM_wall_banner[facing=east]");
        w.set(fx, y + 11, fz + 1, "TEAM_wall_banner[facing=south]");
        w.set(fx, y + 12, fz, "lantern[hanging=false]");
        land.keeps.add(new Terrain.Keep(0, 0, 19));
        return y;
    }

    // ---------------------------------------------------------------- 4. Sakura: the shrine of the great cherry

    public int sakura(int y) {
        plaza(y, 9, P.of("polished_andesite", 2, "andesite", 1, "gravel", 1), P.of("dark_oak_planks"));
        // torii gates on the two approaches, stone lanterns round the plaza
        for (int side : new int[] {-1}) {
            int gx = side * 13;
            for (int s : new int[] {-3, 3}) for (int dy = 1; dy <= 6; dy++) w.set(gx, y + dy, s, "red_concrete");
            for (int z = -5; z <= 5; z++) w.set(gx, y + 7, z, Math.abs(z) == 5 ? "dark_oak_slab[type=bottom]" : "black_concrete");
            for (int z = -3; z <= 3; z++) w.set(gx, y + 5, z, "red_concrete");
        }
        for (int i = 0; i < 6; i++) {
            double ang = i * Math.PI / 3 + Math.PI / 6;
            int x = (int) Math.round(Math.cos(ang) * 10.5), z = (int) Math.round(Math.sin(ang) * 10.5);
            if (!w.canon(x, z)) continue;
            w.set(x, y + 1, z, "stone_brick_wall");
            w.set(x, y + 2, z, "lantern[hanging=false]");
            w.set(x, y + 3, z, "stone_brick_slab[type=bottom]");
        }
        // the great cherries on the shoulders of the island, a koi pond beside one
        Flora f = new Flora(w, t, land);
        for (int[] c : new int[][] {{-4, -17}}) {
            int gy = w.ground(c[0], c[1]);
            if (gy > 0) {
                f.tree(Theme.Tree.CHERRY, c[0], gy + 1, c[1]);
                f.tree(Theme.Tree.CHERRY, c[0] + 3, gy + 1, c[1] - 3);
            }
        }
        land.keeps.add(new Terrain.Keep(0, 0, 14));
        return y;
    }

    // ---------------------------------------------------------------- 5. Emberforge: the caldera

    public int emberforge(int y) {
        plaza(y, 9, P.of("polished_blackstone", 2, "obsidian", 1, "polished_basalt[axis=y]", 1), P.of("polished_blackstone_bricks"));
        // a moat of lava round the plaza with four causeways, a ring of basalt columns
        for (int x = -15; x <= 15; x++)
            for (int z = -15; z <= 15; z++) {
                if (!w.canon(x, z)) continue;
                double d = Math.hypot(x, z) + w.n(x, z, 3);
                if (d <= 10.5 || d > 13.5) continue;
                for (int dy = 1; dy <= 12; dy++) if (!w.air(x, y + dy, z)) w.set(x, y + dy, z, "air");
                boolean causeway = Math.abs(x) <= 1 || Math.abs(z) <= 1;
                if (causeway) {
                    w.set(x, y, z, "polished_blackstone_bricks");
                    w.set(x, y - 1, z, "blackstone");
                } else {
                    w.set(x, y - 1, z, "lava");
                    w.set(x, y - 2, z, "magma_block");
                    w.set(x, y, z, "air");
                }
            }
        for (int i = 0; i < 12; i++) {
            double ang = i * Math.PI / 6 + Math.PI / 12;
            int x = (int) Math.round(Math.cos(ang) * 16.5), z = (int) Math.round(Math.sin(ang) * 16.5);
            if (!w.canon(x, z)) continue;
            int gy = w.ground(x, z);
            if (gy < y - 5) gy = y;
            int h = 4 + (int) ((w.n(x, z, 2) + 1) * 4);
            for (int dy = 1; dy <= h; dy++) w.set(x, gy + dy, z, "basalt[axis=y]");
            w.set(x, gy + h + 1, z, "polished_basalt[axis=y]");
            if (i % 3 == 0) w.set(x, gy + h + 2, z, "campfire[lit=true]");
        }
        land.keeps.add(new Terrain.Keep(0, 0, 19));
        return y;
    }

    // ---------------------------------------------------------------- 6. Verdant Ruins: the sunken ziggurat

    public int verdant(int y) {
        // a three-step platform rising to the plaza, broken pillars round it
        for (int step = 2; step >= 0; step--) {
            double r = 9 + step * 3;
            int sy = y - step;
            for (int x = -(int) r; x <= r; x++)
                for (int z = -(int) r; z <= r; z++) {
                    if (!w.canon(x, z) || Math.max(Math.abs(x), Math.abs(z)) > r) continue;
                    for (int dy = 1; dy <= 25; dy++) if (!w.air(x, sy + dy, z) && sy + dy > y) w.set(x, sy + dy, z, "air");
                    w.set(x, sy, z, Math.max(Math.abs(x), Math.abs(z)) > r - 1 ? "mossy_stone_brick_slab[type=top]".replace("_slab[type=top]", "s") : w.r.nextInt(4) == 0 ? "moss_block" : "mossy_stone_bricks");
                    for (int dy = 1; dy <= 4; dy++) if (w.air(x, sy - dy, z)) w.set(x, sy - dy, z, "mossy_cobblestone");
                }
        }
        for (int x = -9; x <= 9; x++) for (int z = -9; z <= 9; z++) if (w.canon(x, z)) w.set(x, y, z, ((x + z) % 4 == 0) ? "chiseled_stone_bricks" : w.r.nextInt(5) == 0 ? "moss_block" : "mossy_stone_bricks");
        for (int[] p : new int[][] {{-10, -10}, {-10, 10}, {-10, 0}, {0, -10}}) {
            if (!w.canon(p[0], p[1])) continue;
            int h = 3 + (int) ((w.n(p[0], p[1], 2) + 1) * 2.5);
            for (int dy = 1; dy <= h; dy++) w.set(p[0], y + dy, p[1], dy == 1 ? "chiseled_stone_bricks" : "mossy_stone_brick_wall");
            if (h > 4) w.set(p[0], y + h + 1, p[1], "mossy_stone_brick_slab[type=bottom]");
        }
        // a colossal stone head half sunk into the shoulder of the island
        int hx = -18, hz = -14, gy = w.ground(hx, hz);
        if (gy > 0) {
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) for (int dy = 0; dy <= 7; dy++) {
                double d = Math.sqrt(dx * dx + dz * dz * 1.2 + (dy - 3) * (dy - 3) * 0.5);
                if (d > 3.6) continue;
                w.set(hx + dx, gy + dy, hz + dz, w.r.nextInt(3) == 0 ? "mossy_cobblestone" : "mossy_stone_bricks");
            }
            for (int dz : new int[] {-1, 1}) w.set(hx + 3, gy + 5, hz + dz, "glow_lichen[west=true]".replace("glow_lichen[west=true]", "black_concrete"));
            w.set(hx + 4, gy + 3, hz, "mossy_stone_brick_stairs[facing=west]");
            land.keeps.add(new Terrain.Keep(hx, hz, 5));
        }
        land.keeps.add(new Terrain.Keep(0, 0, 16));
        return y;
    }

    // ---------------------------------------------------------------- 7. Tidecrest: twin lighthouses

    public int tidecrest(int y) {
        plaza(y, 8, P.of("smooth_sandstone", 2, "sandstone", 1, "birch_planks", 1), P.of("cut_sandstone"));
        // a lighthouse on each shoulder (the other is the copy)
        int lx = -2, lz = -14, gy = Math.max(y, w.ground(lx, lz));
        for (int dy = 0; dy <= 18; dy++)
            for (int dx = -3; dx <= 3; dx++)
                for (int dz = -3; dz <= 3; dz++) {
                    double d = Math.hypot(dx, dz);
                    int r = dy < 6 ? 3 : 2;
                    if (d > r + 0.4) continue;
                    boolean shell = d > r - 0.7;
                    w.set(lx + dx, gy + dy, lz + dz, shell ? ((dy / 3) % 2 == 0 ? "white_concrete" : "red_concrete") : dy == 0 ? "birch_planks" : "air");
                }
        for (int dy = 1; dy <= 18; dy++) w.set(lx - 1, gy + dy, lz, "ladder[facing=east]");
        for (int dy = 1; dy <= 2; dy++) w.set(lx + 2, gy + dy, lz, "air");
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) if (Math.hypot(dx, dz) <= 3.4) {
            w.set(lx + dx, gy + 19, lz + dz, "dark_prismarine");
            if (Math.hypot(dx, dz) > 2.6) w.set(lx + dx, gy + 20, lz + dz, "birch_fence");
        }
        w.set(lx - 1, gy + 19, lz, "ladder[facing=east]");
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = 20; dy <= 22; dy++)
            w.set(lx + dx, gy + dy, lz + dz, dx == 0 && dz == 0 ? "sea_lantern" : (dy == 22 ? "dark_prismarine" : "glass"));
        a.cone(lx, lz, gy + 23, 1.6, "dark_prismarine_stairs", "dark_prismarine", "lightning_rod");
        // a wrecked boat on the other shoulder
        int bx = -16, bz = 8, by = Math.max(y, w.ground(bx, bz));
        for (int dx = -4; dx <= 4; dx++) for (int dz = -2; dz <= 2; dz++) {
            boolean hull = Math.abs(dz) == 2 || Math.abs(dx) == 4;
            if (hull && (dx + dz * 3) % 5 != 0) w.set(bx + dx, by + 1, bz + dz, "dark_oak_planks");
            if (Math.abs(dz) < 2 && Math.abs(dx) < 4) w.set(bx + dx, by, bz + dz, "dark_oak_planks");
        }
        w.set(bx, by + 1, bz, "dark_oak_fence");
        w.set(bx, by + 2, bz, "dark_oak_fence");
        w.set(bx + 1, by + 1, bz - 1, "barrel[facing=west]");
        land.keeps.add(new Terrain.Keep(lx, lz, 5));
        land.keeps.add(new Terrain.Keep(bx, bz, 6));
        return y;
    }

    // ---------------------------------------------------------------- 8. Mycelia: the ring of giants

    public int mycelia(int y) {
        plaza(y, 8, P.of("podzol", 2, "mycelium", 2, "coarse_dirt", 1), P.of("stripped_oak_wood"));
        Flora f = new Flora(w, t, land);
        for (int i = 0; i < 3; i++) {
            double ang = i * Math.PI / 3 + Math.PI / 6 + Math.PI / 2;
            int x = (int) Math.round(Math.cos(ang) * 15), z = (int) Math.round(Math.sin(ang) * 15);
            if (!w.canon(x, z)) continue;
            int gy = w.ground(x, z);
            if (gy < 0) continue;
            // a giant: thick stem and a broad cap
            for (int dy = 1; dy <= 14; dy++) for (int dx = 0; dx <= 1; dx++) for (int dz = 0; dz <= 1; dz++) w.set(x + dx, gy + dy, z + dz, "mushroom_stem");
            boolean red = i % 2 == 0;
            double r = red ? 6 : 7.5;
            for (int dx = -8; dx <= 8; dx++) for (int dz = -8; dz <= 8; dz++) {
                double d = Math.hypot(dx - 0.5, dz - 0.5);
                if (d > r) continue;
                int lift = red ? (int) Math.round(Math.sqrt(Math.max(0, r * r - d * d)) * 0.55) : (d > r - 1 ? -1 : 0);
                w.set(x + dx, gy + 15 + lift, z + dz, red ? "red_mushroom_block" : "brown_mushroom_block");
                if (red && d > r - 1.2) w.set(x + dx, gy + 14 + lift, z + dz, "red_mushroom_block");
            }
            for (int k = 0; k < 6; k++) w.soft(x + w.r.nextInt(7) - 3, gy + 14, z + w.r.nextInt(7) - 3, "shroomlight");
        }
        for (int i = 0; i < 6; i++) {
            double ang = i * Math.PI / 3;
            int x = (int) Math.round(Math.cos(ang) * 9.5), z = (int) Math.round(Math.sin(ang) * 9.5);
            if (!w.canon(x, z)) continue;
            w.set(x, y + 1, z, "mushroom_stem");
            w.set(x, y + 2, z, "shroomlight");
        }
        land.keeps.add(new Terrain.Keep(0, 0, 20));
        return y;
    }

    // ---------------------------------------------------------------- 9. Pale Grove: the ruined cathedral

    public int palegrove(int y) {
        plaza(y, 7, P.of("polished_deepslate", 2, "deepslate_tiles", 1, "cobbled_deepslate", 1), P.of("deepslate_bricks"));
        // the nave runs east-west through the middle; its roof fell long ago
        P stone = P.of("deepslate_bricks", 4, "cracked_deepslate_bricks", 3, "cobbled_deepslate", 1);
        for (int x = -24; x <= 24; x++)
            for (int z = -9; z <= 9; z++) {
                if (!w.canon(x, z)) continue;
                boolean wall = Math.abs(z) == 9 && Math.abs(x) > 3;
                boolean pier = Math.abs(z) == 6 && Math.abs(x) % 6 == 3 && Math.abs(x) > 6;
                int gy = w.ground(x, z);
                if (gy < y - 6) continue;
                if (Math.abs(x) > 7 || Math.abs(z) > 7) {
                    w.set(x, y, z, w.r.nextInt(3) == 0 ? "cobbled_deepslate" : "deepslate_tiles");
                    for (int dy = 1; dy <= 14; dy++) if (!w.air(x, y + dy, z)) w.set(x, y + dy, z, "air");
                }
                int h = (int) Math.round(4 + (w.n(x * 0.7, z, 5) + 1) * 5);
                if (wall) for (int dy = 1; dy <= h; dy++) w.set(x, y + dy, z, (dy == 5 || dy == 6) && Math.abs(x) % 4 == 1 ? "gray_stained_glass_pane" : stone.pick(w.r));
                if (pier) for (int dy = 1; dy <= h + 3; dy++) w.set(x, y + dy, z, dy % 4 == 0 ? "chiseled_deepslate" : "polished_deepslate");
            }
        land.keeps.add(new Terrain.Keep(0, 0, 18));
        land.keeps.add(new Terrain.Keep(-18, 0, 8));
        return y;
    }

    // ---------------------------------------------------------------- 10. Skyreach: the Rift

    public int skyreach(int y) {
        plaza(y, 9, P.of("end_stone_bricks", 2, "purpur_block", 1, "amethyst_block", 0), P.of("purpur_pillar[axis=y]"));
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) if (w.canon(x, z) && Math.abs(Math.hypot(x, z) - 5) < 0.6) w.set(x, y, z, "amethyst_block");
        // obsidian pillars leaning out, with crying obsidian veins and end rods
        for (int i = 0; i < 8; i++) {
            double ang = i * Math.PI / 4 + Math.PI / 8;
            double cx = Math.cos(ang) * 13, cz = Math.sin(ang) * 13;
            int gx = (int) Math.round(cx), gz = (int) Math.round(cz);
            if (!w.canon(gx, gz)) continue;
            for (int dy = 1; dy <= 14; dy++) {
                double lean = dy * 0.12;
                int x = (int) Math.round(cx + Math.cos(ang) * lean), z = (int) Math.round(cz + Math.sin(ang) * lean);
                w.set(x, y + dy, z, dy % 5 == 2 ? "crying_obsidian" : "obsidian");
                if (dy == 14) w.set(x, y + dy + 1, z, "end_rod[facing=up]");
            }
        }
        land.keeps.add(new Terrain.Keep(0, 0, 17));
        return y;
    }
}
