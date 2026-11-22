package com.sasha.ctfbuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The three arenas. Each returns its map.yml. */
final class Arenas {

    private Arenas() {}

    /** Collects map.yml as it's built. */
    static final class Yml {
        final StringBuilder out = new StringBuilder();
        Yml line(String text) { out.append(text).append('\n'); return this; }
        static String spot(double[] s, float pitch) { return String.format(Locale.ROOT, "\"%.1f %d %.1f %.0f %.0f\"", s[0], (int) s[1], s[2], s[3], pitch); }
        static String pos(int[] p) { return "\"" + p[0] + " " + p[1] + " " + p[2] + "\""; }
    }

    static void team(Yml yml, String name, Fort fort, boolean wool) {
        yml.line("  " + name + ":");
        yml.line("    spawn: " + Yml.spot(fort.spawn, 0));
        yml.line("    flag: " + Yml.pos(fort.flag));
        if (wool) {
            List<String> w = new ArrayList<>();
            for (int[] p : fort.wools) w.add(Yml.pos(p));
            yml.line("    wools: [" + String.join(", ", w) + "]");
        } else {
            yml.line("    wools: []");
        }
    }

    /** Clears space above a footprint and levels the ground to y (surface block on top). */
    static void flatten(B b, int x1, int z1, int x2, int z2, int y, B.Pal surface, B.Pal fill) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                for (int yy = y + 1; yy <= y + 30; yy++) b.set(x, yy, z, "air");
                for (int yy = y - 8; yy < y; yy++) if (b.air(x, yy, z)) b.set(x, yy, z, fill);
                b.set(x, y, z, surface);
            }
    }

    // =================================================================== FROSTFALL

    static String frostfall(B b) {
        final int base = 64;
        B.Pal snow = B.Pal.of("snow_block");
        B.Pal outcrop = B.Pal.of("stone", 3, "andesite", 2, "cobblestone", 1, "snow_block", 2);
        B.Pal dirt = B.Pal.of("dirt", 4, "coarse_dirt", 1, "snow_block", 2);
        B.Pal rock = B.Pal.of("stone", 6, "andesite", 3, "tuff", 2, "cobblestone", 1, "diorite", 1);
        // terrain: a long island, a frozen river down the middle, ridges along the long sides
        b.island(-112, 112, -62, 62, (x, z) -> b.roundRect(x, z, 0, 0, 108, 56, 26, 9), (x, z) -> {
            double ridge = Math.max(0, (Math.abs(z) - 38) / 14.0);
            int h = base + (int) Math.round(b.fbm(x, z, 38, 3) * 3 + ridge * ridge * 9 + b.fbm(x + 99, z, 9, 2) * ridge * 3);
            int rx = x - (int) Math.round(b.n(3.1, z, 26) * 4);
            int ax = Math.abs(rx);
            if (ax <= 6) return new B.Column(base - 3, B.Pal.of("packed_ice", 5, "ice", 2, "blue_ice", 1), B.Pal.of("ice"), rock);
            if (ax <= 9) {
                int bank = base - 3 + (int) Math.round((ax - 6) / 3.0 * (h - base + 3));
                return new B.Column(bank, B.Pal.of("snow_block", 3, "gravel", 1, "stone", 1), dirt, rock);
            }
            return new B.Column(h, b.patch(x, z, snow, outcrop, 0.42, 11), dirt, rock);
        });

        // forts
        B.Pal stone = B.Pal.of("stone_bricks", 8, "cracked_stone_bricks", 2, "mossy_stone_bricks", 1, "andesite", 1);
        B.Pal cap = B.Pal.of("polished_andesite", 3, "stone_brick_slab[type=bottom]", 0, "smooth_stone", 1);
        B.Pal courtyard = B.Pal.of("cobblestone", 3, "stone_bricks", 4, "polished_andesite", 2, "snow_block", 1);
        B.Pal trim = B.Pal.of("chiseled_stone_bricks");
        flatten(b, -95, -24, -50, 24, base, snow, rock);
        flatten(b, 50, -24, 95, 24, base, snow, rock);
        Fort red = new Fort(b, -72, 0, base, 1, 0, 15, 16, "red", stone, cap, courtyard, trim, B.roof("red"));
        Fort blue = new Fort(b, 72, 0, base, -1, 0, 15, 16, "blue", stone, cap, courtyard, trim, B.roof("blue"));
        red.build();
        blue.build();

        // bridges over the river (north and south) and the hill plaza in the middle
        for (int bz : new int[] {-28, 28}) stoneBridge(b, bz, base, -12, 12);
        hillRuin(b, base);

        // paths from the gates to the bridges and the plaza
        B.Pal path = B.Pal.of("gravel", 3, "cobblestone", 2, "coarse_dirt", 2, "snow_block", 1);
        for (int side : new int[] {-1, 1}) {
            int gate = side * 56;
            b.path(B.pts(gate, 0, side * 30, 0, side * 10, 0), 2, path);
            b.path(B.pts(gate, 0, side * 40, -14, side * 20, -28, side * 12, -28), 1, path);
            b.path(B.pts(gate, 0, side * 40, 14, side * 20, 28, side * 12, 28), 1, path);
            for (int[] p : new int[][] {{side * 44, 3}, {side * 30, -3}, {side * 20, 3}, {side * 32, -20}, {side * 32, 20}}) {
                int gy = b.ground(p[0], p[1]);
                b.lampPost(p[0], gy + 1, p[1], "spruce_fence");
            }
        }

        // outposts: ruined stone shelters for cover on the side lanes
        for (int sx : new int[] {-1, 1})
            for (int sz : new int[] {-1, 1}) ruin(b, sx * 34, sz * 16, rock);

        // forests, boulders and snow cover
        for (int i = 0; i < 520; i++) {
            int x = b.r.nextInt(206) - 103, z = b.r.nextInt(104) - 52;
            if (Math.abs(x) < 14 || Math.abs(x) > 48 && Math.abs(z) < 26 || Math.abs(z) < 6 || Math.abs(Math.abs(z) - 28) < 5 && Math.abs(x) < 46) continue;
            if (b.fbm(x, z, 22, 2) < -0.05 && Math.abs(z) < 36) continue;
            int gy = b.ground(x, z);
            if (!b.at(x, gy, z).getType().name().equals("SNOW_BLOCK") || !b.air(x, gy + 1, z)) continue;
            if (Math.abs(x) > 48 && Math.abs(x) < 98 && Math.abs(z) < 27) continue;
            b.spruce(x, gy + 1, z, 7 + b.r.nextInt(6), true);
        }
        for (int i = 0; i < 40; i++) {
            int x = b.r.nextInt(190) - 95, z = b.r.nextInt(96) - 48;
            if (Math.abs(x) > 46 && Math.abs(z) < 26 || Math.abs(x) < 13) continue;
            b.boulder(x, b.ground(x, z), z, 1.4 + b.r.nextDouble() * 1.6, rock);
        }
        // sniper watchtowers on the ridges, ice spikes and a frozen pond behind each fort
        for (int sx : new int[] {-1, 1})
            for (int sz : new int[] {-1, 1}) watchtower(b, sx * 40, sz * 42, "spruce");
        for (int i = 0; i < 14; i++) {
            int x = (b.r.nextBoolean() ? 1 : -1) * (18 + b.r.nextInt(30)), z = (b.r.nextBoolean() ? 1 : -1) * (34 + b.r.nextInt(16));
            iceSpike(b, x, b.ground(x, z) + 1, z, 5 + b.r.nextInt(9));
        }
        for (int side : new int[] {-1, 1}) pond(b, side * 100, 0, base, B.Pal.of("packed_ice", 3, "ice", 2, "blue_ice", 1), "snow_block");
        snowLayers(b, -108, 108, -58, 58);

        Yml yml = new Yml();
        yml.line("name: Frostfall");
        yml.line("authors: [Fractal Build Team]");
        yml.line("modes: [ctf, koth, dtw]");
        yml.line("time-limit-minutes: 15");
        yml.line("capture-limit: 3");
        yml.line("koth-points: 250");
        yml.line("void-y: " + (base - 34));
        yml.line("spectator-spawn: \"0.5 " + (base + 28) + " 0.5 -90 45\"");
        yml.line("hill:");
        yml.line("  corner-1: \"-5 " + (base + 1) + " -5\"");
        yml.line("  corner-2: \"5 " + (base + 4) + " 5\"");
        yml.line("teams:");
        team(yml, "red", red, true);
        team(yml, "blue", blue, true);
        return yml.out.toString();
    }

    /** A stone bridge across the river along x, at z, with railings and lanterns. */
    static void stoneBridge(B b, int z, int base, int x1, int x2) {
        B.Pal deck = B.Pal.of("spruce_planks", 4, "stripped_spruce_wood", 1);
        for (int x = x1; x <= x2; x++) {
            for (int dz = -2; dz <= 2; dz++) b.set(x, base, z + dz, Math.abs(dz) == 2 ? B.Pal.of("stone_bricks", 3, "mossy_stone_bricks", 1) : deck);
            b.set(x, base + 1, z - 2, "stone_brick_wall");
            b.set(x, base + 1, z + 2, "stone_brick_wall");
            // arch underneath
            int arch = (int) Math.round(2.5 - Math.abs(x) / 5.0);
            for (int dy = 1; dy <= Math.max(0, arch); dy++)
                for (int dz = -2; dz <= 2; dz++) b.set(x, base - dy, z + dz, "stone_bricks");
        }
        for (int x : new int[] {x1, x2}) {
            b.set(x, base + 2, z - 2, "lantern");
            b.set(x, base + 2, z + 2, "lantern");
        }
    }

    /** The King of the Hill plaza over the river: a raised round platform among ruined pillars. */
    static void hillRuin(B b, int base) {
        B.Pal stone = B.Pal.of("stone_bricks", 6, "cracked_stone_bricks", 3, "mossy_stone_bricks", 2);
        for (int x = -12; x <= 12; x++)
            for (int z = -12; z <= 12; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > 11.5) continue;
                for (int y = base - 6; y < base; y++) b.set(x, y, z, stone);
                b.set(x, base, z, d > 10.5 ? B.Pal.of("chiseled_stone_bricks") : B.Pal.of("polished_andesite", 3, "stone_bricks", 2, "snow_block", 1));
                if (d <= 5.5) b.set(x, base, z, B.Pal.of("polished_diorite", 3, "calcite", 1));
                for (int y = base + 1; y < base + 12; y++) if (!b.air(x, y, z)) b.set(x, y, z, "air");
            }
        // raised centre and the compass inlay
        for (int x = -5; x <= 5; x++)
            for (int z = -5; z <= 5; z++) if (x * x + z * z <= 26) b.set(x, base, z, (x == 0 || z == 0) ? "smooth_quartz" : "polished_diorite");
        b.set(0, base, 0, "lodestone");
        // ruined pillars around the edge, some broken
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            int px = (int) Math.round(Math.cos(a) * 9), pz = (int) Math.round(Math.sin(a) * 9);
            int height = 3 + b.r.nextInt(5);
            for (int dy = 1; dy <= height; dy++) b.set(px, base + dy, pz, dy == height ? "chiseled_stone_bricks" : "stone_brick_wall");
            if (height > 5) b.set(px, base + height + 1, pz, "lantern");
            else b.set(px + (int) Math.signum(px), base + 1, pz + (int) Math.signum(pz), stone);
        }
        // approaches from both banks
        for (int side : new int[] {-1, 1}) {
            for (int x = 11; x <= 16; x++)
                for (int z = -3; z <= 3; z++) {
                    b.set(side * x, base, z, B.Pal.of("stone_bricks", 3, "polished_andesite", 1));
                    for (int y = base - 5; y < base; y++) b.set(side * x, y, z, "stone_bricks");
                }
        }
    }

    /** A wooden sniper tower: 3x3 legs, ladder, a fenced platform with a roof. */
    static void watchtower(B b, int x, int z, String wood) {
        int gy = b.ground(x, z);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int y = gy + 1; y <= gy + 14; y++) b.set(x + dx, y, z + dz, "air");
        for (int[] c : new int[][] {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}})
            for (int dy = 1; dy <= 12; dy++) b.set(x + c[0], gy + dy, z + c[1], wood + "_log");
        for (int dy = 1; dy <= 9; dy++) {
            b.set(x, gy + dy, z + 1, wood + "_planks");
            b.set(x, gy + dy, z, "ladder[facing=north]");
        }
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                b.set(x + dx, gy + 9, z + dz, (dx == 0 && dz == 0) ? "air" : wood + "_planks");
                if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                    if (!(Math.abs(dx) == 2 && Math.abs(dz) == 2)) b.set(x + dx, gy + 10, z + dz, wood + "_fence");
                }
                b.set(x + dx, gy + 13, z + dz, wood + "_slab[type=bottom]");
            }
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) if (Math.abs(dx) == 3 || Math.abs(dz) == 3) b.set(x + dx, gy + 12, z + dz, wood + "_slab[type=top]");
        b.set(x, gy + 10, z, "ladder[facing=north]");
        b.set(x + 1, gy + 12, z + 1, "lantern[hanging=true]");
        // diagonal braces
        for (int dy = 2; dy <= 7; dy += 5) for (int d = -1; d <= 1; d++) {
            b.set(x + d, gy + dy, z - 2, wood + "_fence");
            b.set(x - 2, gy + dy, z + d, wood + "_fence");
            b.set(x + 2, gy + dy, z + d, wood + "_fence");
        }
    }

    static void iceSpike(B b, int x, int y, int z, int height) {
        for (int dy = 0; dy < height; dy++) {
            double radius = (1 - dy / (double) height) * 1.6;
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) if (dx * dx + dz * dz <= radius * radius + 0.3) b.set(x + dx, y + dy, z + dz, "packed_ice");
        }
        for (int dy = -2; dy < 0; dy++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) b.set(x + dx, y + dy, z + dz, "packed_ice");
    }

    /** A small rounded pond set a block below the ground, with a rim. */
    static void pond(B b, int cx, int cz, int base, B.Pal liquid, String rim) {
        for (int x = -7; x <= 7; x++)
            for (int z = -10; z <= 10; z++) {
                double d = Math.sqrt(x * x / 36.0 + z * z / 81.0) + b.n(cx + x, cz + z, 3) * 0.12;
                if (d > 1.12) continue;
                int gy = b.ground(cx + x, cz + z);
                if (d <= 1.0) {
                    for (int y = gy - 1; y <= gy + 3; y++) b.set(cx + x, y, cz + z, "air");
                    b.set(cx + x, gy - 2, cz + z, "dirt");
                    b.set(cx + x, gy - 1, cz + z, liquid);
                } else b.set(cx + x, gy, cz + z, rim);
            }
    }

    static void ruin(B b, int cx, int cz, B.Pal rock) {
        B.Pal walls = B.Pal.of("cobblestone", 4, "mossy_cobblestone", 3, "stone_bricks", 2, "cracked_stone_bricks", 2);
        int gy = b.ground(cx, cz);
        for (int x = -4; x <= 4; x++)
            for (int z = -4; z <= 4; z++) {
                boolean edge = Math.abs(x) == 4 || Math.abs(z) == 4;
                b.set(cx + x, gy, cz + z, B.Pal.of("cobblestone", 2, "gravel", 1, "snow_block", 1));
                if (!edge) continue;
                int h = 1 + (int) Math.round((b.n(cx + x, cz + z, 3) + 1) * 1.6);
                if ((x == 0 || z == 0)) h = 0; // doorways
                for (int dy = 1; dy <= h; dy++) b.set(cx + x, gy + dy, cz + z, walls);
            }
        b.set(cx, gy + 1, cz, "campfire[lit=false]");
        b.set(cx + 2, gy + 1, cz + 2, "barrel[facing=up]");
    }

    static void snowLayers(B b, int x1, int x2, int z1, int z2) {
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                int y = b.top(x, z);
                if (y < b.w.getMinHeight()) continue;
                String t = b.at(x, y, z).getType().name();
                if ((t.equals("SNOW_BLOCK") || t.equals("COBBLESTONE") || t.equals("GRAVEL")) && b.r.nextInt(5) == 0) {
                    b.set(x, y + 1, z, "snow[layers=" + (1 + b.r.nextInt(2)) + "]");
                }
            }
    }

    // =================================================================== DUSTBOWL CANYON

    static String canyon(B b) {
        final int base = 70, floor = 56;
        B.Pal sand = B.Pal.of("sand", 12, "sandstone", 1);
        B.Pal redSand = B.Pal.of("red_sand", 8, "coarse_dirt", 1);
        B.Pal sub = B.Pal.of("sandstone", 3, "red_sandstone", 1, "terracotta", 1);
        B.Pal rock = B.Pal.of("terracotta", 4, "orange_terracotta", 2, "red_sandstone", 2, "sandstone", 1);
        b.island(-66, 66, -114, 114, (x, z) -> b.roundRect(x, z, 0, 0, 58, 108, 22, 8), (x, z) -> {
            int h = base + (int) Math.round(b.fbm(x, z, 30, 3) * 2.5);
            int rz = z - (int) Math.round(b.n(x, 5.5, 24) * 5);
            int az = Math.abs(rz);
            if (az <= 13) return new B.Column(floor + (int) Math.round(b.n(x, z, 6)), B.Pal.of("coarse_dirt", 3, "red_sand", 3, "gravel", 1), sub, rock);
            if (az <= 16) {
                int wall = floor + (int) Math.round((az - 13) / 3.0 * (h - floor) * 0.35);
                return new B.Column(wall, B.Pal.of("red_sand", 2, "terracotta", 1), sub, rock);
            }
            return new B.Column(h, b.patch(x, z, sand, redSand, 0.25, 18), sub, rock);
        });
        // cliff walls: make the canyon sides sheer, then band every exposed face in mesa stripes
        for (int x = -60; x <= 60; x++)
            for (int z = -22; z <= 22; z++) {
                int rz = z - (int) Math.round(b.n(x, 5.5, 24) * 5);
                if (Math.abs(rz) > 13 && Math.abs(rz) <= 16) {
                    int h = b.ground(x, z + (rz > 0 ? 4 : -4));
                    if (h > floor) for (int y = floor; y <= h; y++) if (b.air(x, y, z)) b.set(x, y, z, rock);
                }
            }
        strata(b, -64, 64, -112, 112, floor - 30, base + 6);

        B.Pal stone = B.Pal.of("sandstone", 6, "smooth_sandstone", 3, "cut_sandstone", 2);
        B.Pal cap = B.Pal.of("cut_sandstone", 3, "smooth_sandstone", 1);
        B.Pal yard = B.Pal.of("smooth_sandstone", 3, "sand", 2, "cut_sandstone", 2, "terracotta", 1);
        B.Pal trim = B.Pal.of("chiseled_sandstone");
        flatten(b, -24, -98, 24, -52, base, sand, rock);
        flatten(b, -24, 52, 24, 98, base, sand, rock);
        Fort red = new Fort(b, 0, -75, base, 0, 1, 14, 16, "red", stone, cap, yard, trim, B.roof("red"));
        Fort blue = new Fort(b, 0, 75, base, 0, -1, 14, 16, "blue", stone, cap, yard, trim, B.roof("blue"));
        red.build();
        blue.build();

        // rope bridges at the sides, the hill plaza bridge in the middle, ramps down into the canyon
        for (int bx : new int[] {-34, 34}) ropeBridge(b, bx, base, -20, 20);
        canyonPlaza(b, base);
        for (int side : new int[] {-1, 1}) ramp(b, side * 50, base, floor, side);

        B.Pal path = B.Pal.of("smooth_sandstone", 2, "sandstone", 2, "coarse_dirt", 2, "gravel", 1);
        for (int side : new int[] {-1, 1}) {
            int gate = side * 60;
            b.path(B.pts(0, gate, 0, side * 30, 0, side * 14), 2, path);
            b.path(B.pts(0, gate, -24, side * 40, -34, side * 22), 1, path);
            b.path(B.pts(0, gate, 24, side * 40, 34, side * 22), 1, path);
            b.path(B.pts(-24, side * 40, -50, side * 22), 1, path);
            for (int[] p : new int[][] {{4, side * 44}, {-4, side * 30}, {-30, side * 34}, {30, side * 34}}) {
                b.lampPost(p[0], b.ground(p[0], p[1]) + 1, p[1], "birch_fence");
            }
        }
        // oases on the flanks, hoodoo rock pillars, ruined walls for cover
        for (int sx : new int[] {-1, 1})
            for (int sz : new int[] {-1, 1}) {
                oasis(b, sx * 42, sz * 46);
                desertRuin(b, sx * 11, sz * 40);
            }
        int[][] hoodoos = {{-50, -30}, {50, 30}, {-48, 66}, {48, -66}, {-26, -52}, {26, 52}, {-52, 100}, {52, -100}, {30, -88}, {-30, 88}};
        for (int[] h : hoodoos) hoodoo(b, h[0], h[1], 6 + b.r.nextInt(8));
        // market stalls near the middle, cacti, dead bushes, palms
        for (int[] s : new int[][] {{-18, -30}, {18, -30}, {-18, 30}, {18, 30}}) stall(b, s[0], s[1], base);
        for (int i = 0; i < 220; i++) {
            int x = b.r.nextInt(116) - 58, z = b.r.nextInt(212) - 106;
            int gy = b.ground(x, z);
            String t = b.at(x, gy, z).getType().name();
            if (!(t.equals("SAND") || t.equals("RED_SAND")) || !b.air(x, gy + 1, z)) continue;
            if (Math.abs(z) > 50 && Math.abs(z) < 100 && Math.abs(x) < 26) continue;
            int roll = b.r.nextInt(10);
            if (roll < 3) for (int dy = 1; dy <= 1 + b.r.nextInt(3); dy++) b.set(x, gy + dy, z, "cactus");
            else if (roll < 8) b.set(x, gy + 1, z, "dead_bush");
            else palm(b, x, gy + 1, z);
        }
        for (int i = 0; i < 60; i++) {
            int x = b.r.nextInt(110) - 55, z = b.r.nextInt(24) - 12;
            int gy = b.ground(x, z);
            if (gy < floor + 3 && b.air(x, gy + 1, z)) b.set(x, gy + 1, z, b.r.nextBoolean() ? "dead_bush" : "short_dry_grass");
        }

        Yml yml = new Yml();
        yml.line("name: Dustbowl Canyon");
        yml.line("authors: [Fractal Build Team]");
        yml.line("modes: [ctf, koth, dtw]");
        yml.line("time-limit-minutes: 15");
        yml.line("capture-limit: 3");
        yml.line("koth-points: 250");
        yml.line("void-y: " + (floor - 30));
        yml.line("spectator-spawn: \"0.5 " + (base + 26) + " 0.5 0 45\"");
        yml.line("hill:");
        yml.line("  corner-1: \"-5 " + (base + 1) + " -5\"");
        yml.line("  corner-2: \"5 " + (base + 4) + " 5\"");
        yml.line("teams:");
        team(yml, "red", red, true);
        team(yml, "blue", blue, true);
        return yml.out.toString();
    }

    /** Mesa banding: every block in range that touches air sideways gets a stripe colour for its height. */
    static void strata(B b, int x1, int x2, int z1, int z2, int y1, int y2) {
        String[] bands = {"terracotta", "orange_terracotta", "terracotta", "yellow_terracotta", "red_terracotta", "terracotta",
                "white_terracotta", "orange_terracotta", "brown_terracotta", "terracotta", "light_gray_terracotta", "red_terracotta"};
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++)
                for (int y = y1; y <= y2; y++) {
                    var type = b.at(x, y, z).getType();
                    if (!type.isSolid()) continue;
                    String name = type.name();
                    if (!(name.contains("TERRACOTTA") || name.contains("SANDSTONE"))) continue;
                    if (b.air(x + 1, y, z) || b.air(x - 1, y, z) || b.air(x, y, z + 1) || b.air(x, y, z - 1) || b.air(x, y - 1, z)) {
                        b.set(x, y, z, bands[Math.floorMod(y, bands.length)]);
                    }
                }
    }

    static void ropeBridge(B b, int x, int base, int z1, int z2) {
        for (int z = z1; z <= z2; z++) {
            double sag = Math.cos((z - (z1 + z2) / 2.0) / ((z2 - z1) / 2.0) * Math.PI / 2);
            int y = base - (int) Math.round(sag * 2);
            for (int dx = -1; dx <= 1; dx++) b.set(x + dx, y, z, dx == 0 ? "spruce_planks" : "spruce_slab[type=top]");
            b.set(x - 2, y + 1, z, "spruce_fence");
            b.set(x + 2, y + 1, z, "spruce_fence");
            b.set(x - 2, y, z, "spruce_slab[type=top]");
            b.set(x + 2, y, z, "spruce_slab[type=top]");
            if (z % 4 == 0) {
                b.set(x - 2, y + 2, z, "spruce_fence");
                b.set(x + 2, y + 2, z, "spruce_fence");
            }
        }
        for (int z : new int[] {z1, z2})
            for (int dx : new int[] {-2, 2}) {
                for (int dy = 1; dy <= 3; dy++) b.set(x + dx, base + dy, z, "stripped_spruce_log");
                b.set(x + dx, base + 4, z, "lantern");
            }
    }

    static void canyonPlaza(B b, int base) {
        B.Pal stone = B.Pal.of("smooth_sandstone", 4, "cut_sandstone", 3, "sandstone", 2);
        for (int x = -11; x <= 11; x++)
            for (int z = -22; z <= 22; z++) {
                double d = Math.sqrt(x * x + z * z * (Math.abs(z) > 9 ? 0.0 : 1));
                boolean deck = Math.abs(z) <= 9 ? Math.sqrt(x * x + z * z) <= 11 : Math.abs(x) <= 3;
                if (!deck) continue;
                for (int y = base - 3; y < base; y++) b.set(x, y, z, stone);
                b.set(x, base, z, stone);
                for (int y = base + 1; y < base + 10; y++) if (!b.air(x, y, z)) b.set(x, y, z, "air");
                if (Math.abs(z) > 9) {
                    b.set(-4, base + 1, z, "sandstone_wall");
                    b.set(4, base + 1, z, "sandstone_wall");
                }
            }
        // support pillars down to the canyon floor
        for (int[] p : new int[][] {{-7, -7}, {7, -7}, {-7, 7}, {7, 7}, {0, 0}})
            for (int y = base - 1; y > 40; y--) {
                if (!b.air(p[0], y, p[1]) && y < base - 3) break;
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) b.set(p[0] + dx, y, p[1] + dz, "cut_sandstone");
            }
        // obelisks and the sun inlay on the hill
        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) if (x * x + z * z <= 26) b.set(x, base, z, (x + z) % 2 == 0 ? "orange_glazed_terracotta" : "smooth_sandstone");
        b.set(0, base, 0, "gold_block");
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3;
            int px = (int) Math.round(Math.cos(a) * 9), pz = (int) Math.round(Math.sin(a) * 9);
            for (int dy = 1; dy <= 5; dy++) b.set(px, base + dy, pz, dy == 5 ? "chiseled_sandstone" : "cut_sandstone");
            b.set(px, base + 6, pz, "lantern");
        }
    }

    static void ramp(B b, int x, int base, int floor, int side) {
        // a sloped track from the plateau down into the canyon, along z, on both sides
        for (int dir : new int[] {-1, 1}) {
            for (int i = 0; i <= 15; i++) {
                int z = dir * (17 - i);
                int y = base - (int) Math.round(i * (base - floor) / 15.0);
                for (int dx = -2; dx <= 2; dx++) {
                    for (int yy = y + 1; yy <= y + 6; yy++) b.set(x + dx, yy, z, "air");
                    b.set(x + dx, y, z, B.Pal.of("coarse_dirt", 2, "packed_mud", 1, "red_sand", 1));
                    for (int yy = y - 1; yy >= floor - 2 && b.air(x + dx, yy, z); yy--) b.set(x + dx, yy, z, "terracotta");
                }
                if (i % 4 == 0) b.set(x + (side > 0 ? 3 : -3), y + 1, z, "spruce_fence");
            }
        }
    }

    static void oasis(B b, int cx, int cz) {
        pond(b, cx, cz, 0, B.Pal.of("water"), "grass_block");
        for (int x = -10; x <= 10; x++)
            for (int z = -13; z <= 13; z++) {
                double d = Math.sqrt(x * x / 100.0 + z * z / 169.0);
                int gy = b.ground(cx + x, cz + z);
                String t = b.at(cx + x, gy, cz + z).getType().name();
                if (d > 1 || !(t.contains("SAND") && !t.contains("STONE"))) continue;
                if (b.r.nextDouble() < 1.2 - d) {
                    b.set(cx + x, gy, cz + z, "grass_block");
                    if (b.air(cx + x, gy + 1, cz + z) && b.r.nextInt(3) == 0) b.set(cx + x, gy + 1, cz + z, b.r.nextInt(4) == 0 ? "fern" : "short_grass");
                }
            }
        for (int i = 0; i < 4; i++) {
            int x = cx + (b.r.nextBoolean() ? 1 : -1) * (7 + b.r.nextInt(3)), z = cz + b.r.nextInt(17) - 8;
            palm(b, x, b.ground(x, z) + 1, z);
        }
        b.set(cx + 6, b.ground(cx + 6, cz + 9) + 1, cz + 9, "sugar_cane");
    }

    /** A tall rock pillar in mesa bands with a wider cap. */
    static void hoodoo(B b, int x, int z, int height) {
        int gy = b.ground(x, z);
        String[] bands = {"terracotta", "orange_terracotta", "red_terracotta", "yellow_terracotta", "white_terracotta", "brown_terracotta"};
        for (int dy = -3; dy <= height; dy++) {
            double radius = dy > height - 2 ? 2.4 : 1.4 + b.n(x, dy * 3.0, 2) * 0.5 + (dy < 2 ? 0.8 : 0);
            String band = bands[Math.floorMod(gy + dy, bands.length)];
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) if (dx * dx + dz * dz <= radius * radius + 0.3) b.set(x + dx, gy + dy, z + dz, band);
        }
    }

    static void desertRuin(B b, int cx, int cz) {
        B.Pal walls = B.Pal.of("sandstone", 3, "cut_sandstone", 2, "smooth_sandstone", 1, "chiseled_sandstone", 0);
        int gy = b.ground(cx, cz);
        for (int x = -4; x <= 4; x++)
            for (int z = -3; z <= 3; z++) {
                boolean edge = Math.abs(x) == 4 || Math.abs(z) == 3;
                if (!edge) {
                    if (b.r.nextInt(3) == 0) b.set(cx + x, gy, cz + z, "smooth_sandstone");
                    continue;
                }
                int h = (int) Math.round((b.n(cx + x, cz + z, 2.5) + 1) * 2);
                if (x == 0 || z == 0) h = Math.min(h, 0);
                for (int dy = 1; dy <= h; dy++) b.set(cx + x, gy + dy, cz + z, walls);
                if (h >= 3) b.set(cx + x, gy + h + 1, cz + z, "sandstone_slab[type=bottom]");
            }
        b.set(cx - 2, gy + 1, cz + 1, "decorated_pot");
        b.set(cx + 2, gy + 1, cz - 1, "barrel[facing=up]");
    }

    static void stall(B b, int x, int z, int base) {
        String[] wool = {"red_wool", "white_wool", "orange_wool", "yellow_wool"};
        String canopy = wool[b.r.nextInt(wool.length)];
        int gy = b.ground(x, z);
        for (int[] c : new int[][] {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) for (int dy = 1; dy <= 3; dy++) b.set(x + c[0], gy + dy, z + c[1], "spruce_fence");
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) b.set(x + dx, gy + 4, z + dz, (dx + dz) % 2 == 0 ? canopy : "white_wool");
        b.set(x - 1, gy + 1, z, "barrel[facing=up]");
        b.set(x + 1, gy + 1, z, "decorated_pot");
        b.set(x, gy + 1, z + 1, "hay_block");
    }

    static void palm(B b, int x, int y, int z) {
        int h = 5 + b.r.nextInt(3);
        for (int dy = 0; dy < h; dy++) b.set(x + (dy > 3 ? 1 : 0), y + dy, z, "jungle_log");
        int tx = x + 1, ty = y + h;
        b.set(tx, ty, z, "jungle_leaves[persistent=true]");
        for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}})
            for (int i = 1; i <= 3; i++) b.set(tx + d[0] * i, ty - (i == 3 ? 1 : 0), z + d[1] * i, "jungle_leaves[persistent=true]");
    }

    // =================================================================== FOUR KINGDOMS

    static String kingdoms(B b) {
        final int base = 64;
        B.Pal grass = B.Pal.of("grass_block");
        B.Pal meadow = B.Pal.of("grass_block", 6, "moss_block", 1, "coarse_dirt", 1, "podzol", 1);
        B.Pal dirt = B.Pal.of("dirt", 5, "coarse_dirt", 1);
        B.Pal rock = B.Pal.of("stone", 6, "andesite", 2, "cobblestone", 1, "tuff", 1);
        b.island(-104, 104, -104, 104, (x, z) -> b.roundRect(x, z, 0, 0, 100, 100, 34, 8), (x, z) -> {
            int h = base + (int) Math.round(b.fbm(x, z, 34, 3) * 3);
            int rx = x - (int) Math.round(b.n(z, 1.5, 22) * 3), rz = z - (int) Math.round(b.n(x, 7.5, 22) * 3);
            int d = Math.min(Math.abs(rx), Math.abs(rz));
            double centre = Math.sqrt(x * x + z * z);
            if (centre <= 13) return new B.Column(base + 1, B.Pal.of("stone_bricks"), rock, rock);
            if (d <= 4) return new B.Column(base - 4, B.Pal.of("gravel", 2, "sand", 1, "clay", 1), rock, rock);
            if (d <= 7) {
                int bank = base - 4 + (int) Math.round((d - 4) / 3.0 * (h - base + 4));
                return new B.Column(bank, B.Pal.of("grass_block", 2, "sand", 1, "gravel", 1), dirt, rock);
            }
            return new B.Column(h, b.patch(x, z, grass, meadow, 0.4, 9), dirt, rock);
        });
        // fill the rivers with water
        for (int x = -100; x <= 100; x++)
            for (int z = -100; z <= 100; z++) {
                int y = b.ground(x, z);
                if (y == base - 4 && Math.sqrt(x * x + z * z) > 13) for (int yy = base - 3; yy <= base - 1; yy++) b.set(x, yy, z, "water");
            }

        B.Pal stone = B.Pal.of("stone_bricks", 7, "cracked_stone_bricks", 2, "mossy_stone_bricks", 2, "cobblestone", 1);
        B.Pal cap = B.Pal.of("smooth_stone", 2, "polished_andesite", 1);
        B.Pal yard = B.Pal.of("cobblestone", 3, "stone_bricks", 2, "gravel", 1, "moss_block", 1);
        B.Pal trim = B.Pal.of("chiseled_stone_bricks");
        int c = 64;
        Fort red = kingdom(b, -c, -c, 0, 1, "red", base, stone, cap, yard, trim, grass, rock);
        Fort green = kingdom(b, c, -c, 0, 1, "lime", base, stone, cap, yard, trim, grass, rock);
        Fort blue = kingdom(b, c, c, 0, -1, "blue", base, stone, cap, yard, trim, grass, rock);
        Fort yellow = kingdom(b, -c, c, 0, -1, "yellow", base, stone, cap, yard, trim, grass, rock);

        temple(b, base);
        // bridges over the rivers between neighbouring kingdoms
        for (int s : new int[] {-1, 1}) {
            plankBridge(b, s * 46, 0, base, true);   // across the east-west river (bridge runs along z)
            plankBridge(b, 0, s * 46, base, false);  // across the north-south river (bridge runs along x)
        }
        // roads: gate → village → centre, gate → nearest bridge
        B.Pal road = B.Pal.of("dirt_path", 5, "coarse_dirt", 2, "gravel", 1);
        for (int sx : new int[] {-1, 1})
            for (int sz : new int[] {-1, 1}) {
                int gateZ = sz * (c - 14);
                b.path(B.pts(sx * c, gateZ, sx * c, sz * 34, sx * 20, sz * 20, sx * 12, sz * 12), 2, road);
                b.path(B.pts(sx * c, sz * 34, sx * 46, sz * 12, sx * 46, sz * 6), 1, road);
                b.path(B.pts(sx * 34, sz * 34, sx * 12, sz * 46, sx * 6, sz * 46), 1, road);
                cottage(b, sx * 38, sz * 30, base, sx > 0 ? (sz > 0 ? "blue" : "lime") : (sz > 0 ? "yellow" : "red"));
                cottage(b, sx * 28, sz * 42, base, sx > 0 ? (sz > 0 ? "blue" : "lime") : (sz > 0 ? "yellow" : "red"));
                farm(b, sx * 40, sz * 44, base);
                well(b, sx * 33, sz * 36);
                for (int[] p : new int[][] {{sx * c, sz * 38}, {sx * 30, sz * 27}, {sx * 16, sz * 16}}) {
                    int gy = b.ground(p[0], p[1]);
                    if (b.air(p[0], gy + 1, p[1])) b.lampPost(p[0], gy + 1, p[1], "oak_fence");
                }
            }
        // woods: oak and birch groves, keeping roads, castles and rivers clear
        for (int i = 0; i < 420; i++) {
            int x = b.r.nextInt(190) - 95, z = b.r.nextInt(190) - 95;
            if (Math.abs(x) < 10 || Math.abs(z) < 10 || Math.abs(x) > 44 && Math.abs(z) > 44 || Math.sqrt(x * x + z * z) < 20) continue;
            if (b.fbm(x, z, 18, 2) < 0.05) continue;
            int gy = b.ground(x, z);
            if (!b.at(x, gy, z).getType().name().equals("GRASS_BLOCK") || !b.air(x, gy + 1, z)) continue;
            boolean birch = b.r.nextInt(3) == 0;
            b.blobTree(x, gy + 1, z, 4 + b.r.nextInt(3), birch ? "birch_log" : "oak_log", birch ? "birch_leaves" : "oak_leaves");
        }
        b.plants(-100, 100, -100, 100, 0.12, B.Pal.of("short_grass", 10, "tall_grass[half=lower]", 0, "poppy", 1, "dandelion", 1,
                "cornflower", 1, "oxeye_daisy", 1, "azure_bluet", 1), "GRASS_BLOCK");

        Yml yml = new Yml();
        yml.line("name: Four Kingdoms");
        yml.line("authors: [Fractal Build Team]");
        yml.line("modes: [ctf, ctf4, koth, dtw]");
        yml.line("time-limit-minutes: 15");
        yml.line("capture-limit: 3");
        yml.line("koth-points: 250");
        yml.line("void-y: " + (base - 34));
        yml.line("spectator-spawn: \"0.5 " + (base + 36) + " 0.5 0 60\"");
        yml.line("hill:");
        yml.line("  corner-1: \"-5 " + (base + 2) + " -5\"");
        yml.line("  corner-2: \"5 " + (base + 5) + " 5\"");
        yml.line("teams:");
        // 2-team modes play red (north-west) against blue (south-east): opposite corners
        team(yml, "red", red, true);
        team(yml, "blue", blue, true);
        team(yml, "green", green, false);
        team(yml, "yellow", yellow, false);
        return yml.out.toString();
    }

    static Fort kingdom(B b, int cx, int cz, int fx, int fz, String team, int base, B.Pal stone, B.Pal cap, B.Pal yard, B.Pal trim, B.Pal grass, B.Pal rock) {
        flatten(b, cx - 20, cz - 20, cx + 20, cz + 20, base, grass, rock);
        Fort fort = new Fort(b, cx, cz, base, fx, fz, 14, 14, team, stone, cap, yard, trim, B.roof(team));
        fort.build();
        return fort;
    }

    static void temple(B b, int base) {
        for (int x = -13; x <= 13; x++)
            for (int z = -13; z <= 13; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > 12.5) continue;
                for (int y = base - 8; y <= base; y++) b.set(x, y, z, "stone_bricks");
                b.set(x, base + 1, z, d > 11.5 ? "quartz_bricks" : d <= 6 ? ((x + z) % 2 == 0 ? "smooth_quartz" : "polished_diorite") : "smooth_stone");
                for (int y = base + 2; y <= base + 14; y++) b.set(x, y, z, "air");
            }
        b.set(0, base + 1, 0, "sea_lantern");
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4 + Math.PI / 8;
            int px = (int) Math.round(Math.cos(a) * 9), pz = (int) Math.round(Math.sin(a) * 9);
            for (int dy = 2; dy <= 8; dy++) b.set(px, base + dy, pz, dy == 2 || dy == 8 ? "chiseled_quartz_block" : "quartz_pillar");
        }
        // ring roof on the pillars, open in the middle
        for (int x = -11; x <= 11; x++)
            for (int z = -11; z <= 11; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d >= 7.5 && d <= 10.5) b.set(x, base + 9, z, "smooth_quartz_slab[type=bottom]");
            }
        for (int[] p : new int[][] {{0, 9}, {0, -9}, {9, 0}, {-9, 0}}) b.set(p[0], base + 8, p[1], "lantern[hanging=true]");
        // steps down to the water on the four diagonals, onto each quadrant
        for (int sx : new int[] {-1, 1})
            for (int sz : new int[] {-1, 1})
                for (int i = 9; i <= 16; i++)
                    for (int w = -2; w <= 2; w++) {
                        int x = sx * i + w * sx, z = sz * i - w * sz;
                        b.set(x, base + (i < 13 ? 1 : 0), z, "stone_bricks");
                        for (int y = base - 5; y < base + (i < 13 ? 1 : 0); y++) b.set(x, y, z, "stone_bricks");
                        b.set(x, base + 2, z, "air");
                    }
    }

    static void plankBridge(B b, int x, int z, int base, boolean alongZ) {
        for (int i = -9; i <= 9; i++)
            for (int w = -2; w <= 2; w++) {
                int bx = alongZ ? x + w : x + i, bz = alongZ ? z + i : z + w;
                b.set(bx, base, bz, Math.abs(w) == 2 ? "stripped_oak_log[axis=" + (alongZ ? "z" : "x") + "]" : "oak_planks");
                if (Math.abs(w) == 2) b.set(bx, base + 1, bz, "oak_fence");
                for (int y = base - 4; y < base; y++) if (b.air(bx, y, bz) || b.at(bx, y, bz).getType().name().equals("WATER")) {
                    if (Math.abs(i) % 4 == 0 && Math.abs(w) == 2) b.set(bx, y, bz, "oak_log");
                }
            }
        for (int i : new int[] {-9, 9}) for (int w : new int[] {-2, 2}) {
            int bx = alongZ ? x + w : x + i, bz = alongZ ? z + i : z + w;
            b.set(bx, base + 2, bz, "lantern");
        }
    }

    static void cottage(B b, int x, int z, int base, String team) {
        int gy = b.ground(x, z);
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) {
                for (int y = gy + 1; y <= gy + 9; y++) b.set(x + dx, y, z + dz, "air");
                b.set(x + dx, gy, z + dz, "cobblestone");
                boolean edge = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                boolean corner = Math.abs(dx) == 3 && Math.abs(dz) == 3;
                for (int dy = 1; dy <= 3; dy++) {
                    if (!edge) continue;
                    String block = corner ? "oak_log" : dy == 2 && (dx == 0 || dz == 0) ? "glass_pane" : "white_terracotta";
                    b.set(x + dx, gy + dy, z + dz, block);
                }
            }
        for (int dy = 1; dy <= 2; dy++) b.set(x, gy + dy, z - 3, "air");
        for (int level = 0; level <= 3; level++)
            for (int dx = -4 + level; dx <= 4 - level; dx++)
                for (int dz = -4 + level; dz <= 4 - level; dz++) {
                    if (Math.abs(dx) == 4 - level || Math.abs(dz) == 4 - level) b.set(x + dx, gy + 4 + level, z + dz, B.roof(team));
                }
        b.set(x, gy + 3, z, "lantern[hanging=true]");
        b.set(x - 2, gy + 1, z + 2, "crafting_table");
        b.set(x + 2, gy + 1, z + 2, "barrel[facing=up]");
    }

    static void well(B b, int x, int z) {
        int gy = b.ground(x, z);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                for (int y = gy + 1; y <= gy + 5; y++) b.set(x + dx, y, z + dz, "air");
                boolean centre = dx == 0 && dz == 0;
                b.set(x + dx, gy, z + dz, centre ? "water" : "cobblestone");
                if (!centre) b.set(x + dx, gy + 1, z + dz, "cobblestone_wall");
            }
        for (int[] c : new int[][] {{-1, 0}, {1, 0}}) for (int dy = 2; dy <= 3; dy++) b.set(x + c[0], gy + dy, z, "oak_fence");
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) b.set(x + dx, gy + 4, z + dz, "oak_slab[type=bottom]");
        b.set(x, gy + 3, z, "iron_chain");
    }

    static void farm(B b, int x, int z, int base) {
        int gy = b.ground(x, z);
        for (int dx = -4; dx <= 4; dx++)
            for (int dz = -3; dz <= 3; dz++) {
                boolean edge = Math.abs(dx) == 4 || Math.abs(dz) == 3;
                b.set(x + dx, gy, z + dz, edge ? "oak_log" : dz == 0 ? "water" : "farmland[moisture=7]");
                b.set(x + dx, gy + 1, z + dz, edge || dz == 0 ? "air" : "wheat[age=7]");
                for (int y = gy + 2; y <= gy + 4; y++) b.set(x + dx, y, z + dz, "air");
            }
        b.set(x + 4, gy + 1, z + 3, "composter");
        b.set(x - 4, gy + 1, z - 3, "hay_block");
    }
}
