package com.sasha.ctfbuilder.v2;

import com.sasha.ctfbuilder.v2.Terrain.Island;
import com.sasha.ctfbuilder.v2.Terrain.Shape;
import com.sasha.ctfbuilder.v2.W.P;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The ten arenas, third edition: each its own size, island shapes and route through the void, its own
 * base architecture and its own centrepiece.
 *
 * <pre>
 *   map         teams  size    feel
 *   tidecrest   2      small   fast: sandbars and a short pier across
 *   mycelia     2      small   vertical: mushroom caps high and low
 *   sakura      2      medium  terraced gardens, a crescent island, a pond isle
 *   verdant     2      medium  a dense cluster of jungle islands at many heights
 *   palegrove   2      medium  two lanes split by void, a spine down the middle
 *   dustbowl    2      long    mesas in a zigzag with a canyon ridge on one flank
 *   frostfall   2      large   icebergs, a glacier spine, a terraced mid island
 *   emberforge  2      large   long narrow basalt ridges and a volcano
 *   kingdoms    4      medium  four castles, villages between them
 *   skyreach    4      large   a staircase of floating islets to the Rift
 * </pre>
 */
public final class Maps3 {

    private Maps3() {}

    public static final String[] IDS = {"tidecrest", "mycelia", "sakura", "verdant", "palegrove", "dustbowl", "frostfall", "emberforge", "kingdoms", "skyreach"};

    public static boolean fourTeams(String id) {
        return id.equals("kingdoms") || id.equals("skyreach");
    }

    /** One map's plan. */
    static final class Plan {
        int L, y, mz, padDx, minutes = 15;
        String icon;
        final List<Island> islands = new ArrayList<>();
        /** Built crossings: {fromX, fromY, fromZ, toX, toY, toZ}. */
        final List<int[]> bridges = new ArrayList<>();
        String deck, edge, rail;
        final List<int[]> ponds = new ArrayList<>();

        Island add(String name, int x, int z, double rx, double rz, double angle, int top) {
            Island island = new Island(name, x, z, rx, rz, angle, top);
            islands.add(island);
            return island;
        }
    }

    static Plan plan(String id) {
        Plan p = new Plan();
        switch (id) {
            case "tidecrest" -> {
                p.L = 70; p.y = 66; p.mz = 14; p.padDx = -10; p.minutes = 12; p.icon = "HEART_OF_THE_SEA";
                p.add("home", -p.L - 4, 0, 26, 24, 0, p.y).depth(24).hill(1).beach("sand");
                p.add("sandbar", -40, -26, 15, 4, 0.5, p.y - 1).shape(Shape.SPINE).wobble(0.35).depth(12).beach("sand").hill(1);
                p.add("reef", -38, 20, 8, 7, 0, p.y + 1).lobes(new double[] {5, 4, 6}).depth(14).beach("sand");
                p.add("islet", -22, -6, 5, 4, 0, p.y + 1).depth(8).beach("sand");
                p.add("centre", 0, 0, 20, 17, 0.3, p.y).depth(28).hill(1).beach("sand").trees(0.5);
                p.bridges.add(new int[] {-p.L + 6, p.y + 1, 8, -38, p.y + 2, 20});
                p.deck = "spruce_planks"; p.edge = "spruce_log[axis=y]"; p.rail = "spruce_fence";
            }
            case "mycelia" -> {
                p.L = 80; p.y = 68; p.mz = 16; p.padDx = -10; p.minutes = 12; p.icon = "RED_MUSHROOM";
                p.add("home", -p.L - 5, 0, 26, 24, 0, p.y).lobes(new double[] {-6, 16, 14}).depth(26).hill(2);
                p.add("highcap", -46, -22, 9, 9, 0, p.y + 9).depth(10).hill(1);
                p.add("lowcap", -48, 22, 10, 9, 0.2, p.y - 4).depth(14).hill(1);
                p.add("step", -26, 0, 6, 6, 0, p.y + 4).depth(9);
                p.add("islet", -62, 0, 5, 4, 0, p.y + 2).depth(8);
                p.add("centre", 0, 0, 21, 20, 0, p.y).depth(30).hill(2).trees(0.6);
                p.bridges.add(new int[] {-p.L + 4, p.y + 1, 10, -48, p.y - 3, 22});
                p.deck = "oak_planks"; p.edge = "mushroom_stem"; p.rail = "oak_fence";
            }
            case "sakura" -> {
                p.L = 105; p.y = 70; p.mz = 18; p.padDx = 2; p.icon = "CHERRY_SAPLING";
                p.add("home", -p.L - 6, 0, 32, 30, 0, p.y).shape(Shape.TERRACE).step(3).hill(4).depth(30);
                p.add("garden", -62, -26, 15, 13, 0.4, p.y + 3).bite(0.65, 0.15, 0.55).depth(22).hill(2);
                p.add("pondisle", -64, 26, 14, 12, -0.3, p.y - 2).depth(22).hill(1);
                p.add("stepping", -32, -6, 7, 6, 0, p.y + 1).depth(10);
                p.add("islet1", -86, -2, 4, 4, 0, p.y + 4).depth(8);
                p.add("islet2", -40, 22, 5, 4, 0, p.y + 3).depth(8);
                p.add("centre", 0, 0, 24, 22, 0.2, p.y).shape(Shape.TERRACE).step(2).hill(3).depth(32).trees(0.5);
                p.bridges.add(new int[] {-p.L + 6, p.y + 1, -8, -62, p.y + 4, -26});
                p.ponds.add(new int[] {-66, 28, 5});
                p.deck = "crimson_planks"; p.edge = "stripped_dark_oak_log[axis=y]"; p.rail = "crimson_fence";
            }
            case "verdant" -> {
                p.L = 115; p.y = 70; p.mz = 19; p.padDx = 1; p.icon = "JUNGLE_SAPLING";
                p.add("home", -p.L - 6, 0, 30, 28, 0, p.y).lobes(new double[] {10, -18, 14}, new double[] {8, 20, 12}).depth(30).hill(3);
                p.add("a", -84, -28, 13, 11, 0.3, p.y + 2).depth(20).hill(3);
                p.add("b", -82, 24, 12, 11, -0.2, p.y - 3).depth(18).hill(3);
                p.add("c", -58, -6, 10, 9, 0, p.y + 6).depth(18).hill(2);
                p.add("d", -42, -34, 12, 10, 0.5, p.y - 2).depth(18).hill(3);
                p.add("e", -38, 28, 13, 11, -0.4, p.y + 5).depth(20).hill(3);
                p.add("f", -20, 2, 7, 6, 0, p.y + 3).depth(10);
                p.add("centre", 0, 0, 24, 22, 0.1, p.y).depth(34).hill(2).trees(0.6);
                p.bridges.add(new int[] {-p.L + 6, p.y + 1, -12, -84, p.y + 3, -28});
                p.ponds.add(new int[] {-40, 30, 4});
                p.deck = "jungle_planks"; p.edge = "jungle_log[axis=y]"; p.rail = "jungle_fence";
            }
            case "palegrove" -> {
                p.L = 110; p.y = 70; p.mz = 18; p.padDx = -6; p.icon = "PALE_OAK_SAPLING";
                p.add("home", -p.L - 6, 0, 30, 27, 0, p.y).depth(30).hill(2);
                p.add("north1", -78, -32, 12, 10, 0.2, p.y + 2).depth(18).hill(2);
                p.add("north2", -46, -36, 12, 10, -0.1, p.y + 4).depth(18).hill(2);
                p.add("south1", -76, 32, 12, 10, -0.2, p.y - 2).depth(18).hill(2);
                p.add("south2", -44, 36, 11, 10, 0.1, p.y).depth(18).hill(2);
                p.add("spine", -52, 0, 18, 4, 0, p.y + 1).shape(Shape.SPINE).wobble(0.25).depth(12).hill(1);
                p.add("centre", 0, 0, 26, 19, 0, p.y).depth(32).hill(1).trees(0.4);
                p.bridges.add(new int[] {-p.L + 6, p.y + 1, -10, -78, p.y + 3, -32});
                p.deck = "pale_oak_planks"; p.edge = "polished_deepslate"; p.rail = "pale_oak_fence";
            }
            case "dustbowl" -> {
                p.L = 125; p.y = 72; p.mz = 20; p.padDx = -2; p.icon = "CACTUS";
                p.add("home", -p.L - 5, 0, 32, 30, 0, p.y).shape(Shape.MESA).depth(36).hill(1);
                p.add("mesa1", -86, -30, 14, 11, 0.5, p.y + 6).shape(Shape.MESA).depth(26).hill(1);
                p.add("mesa2", -62, 26, 15, 12, -0.4, p.y - 3).shape(Shape.MESA).depth(24).hill(1);
                p.add("mesa3", -36, -28, 12, 10, 0.3, p.y + 8).shape(Shape.MESA).depth(22).hill(1);
                p.add("ridge", -76, 58, 36, 6, 0.12, p.y + 2).shape(Shape.SPINE).wobble(0.3).depth(18).hill(2);
                p.add("islet1", -104, 6, 5, 4, 0, p.y + 3).depth(8);
                p.add("islet2", -50, 0, 5, 5, 0, p.y + 4).depth(8);
                p.add("islet3", -20, 22, 5, 4, 0, p.y + 2).depth(8);
                p.add("centre", 0, 0, 26, 23, 0.3, p.y).shape(Shape.MESA).depth(36).hill(1).trees(0.6);
                p.bridges.add(new int[] {-p.L + 4, p.y + 1, 12, -62, p.y - 2, 26});
                p.deck = "jungle_planks"; p.edge = "stripped_jungle_log[axis=y]"; p.rail = "jungle_fence";
            }
            case "frostfall" -> {
                p.L = 150; p.y = 72; p.mz = 19; p.padDx = -2; p.minutes = 20; p.icon = "PACKED_ICE";
                p.add("home", -p.L - 6, 0, 36, 33, 0.05, p.y).depth(32).hill(2);
                p.add("glacier", -110, -46, 26, 8, 0.35, p.y + 3).shape(Shape.SPINE).wobble(0.35).depth(18).hill(2)
                        .surface(P.of("packed_ice", 3, "snow_block", 3, "blue_ice", 1));
                p.add("berg1", -104, 28, 9, 8, 0, p.y - 2).depth(14).surface(P.of("packed_ice", 2, "snow_block", 2, "blue_ice", 1));
                p.add("berg2", -82, 46, 8, 7, 0, p.y + 1).depth(12).surface(P.of("packed_ice", 2, "snow_block", 2));
                p.add("mid", -70, -8, 18, 15, 0.2, p.y + 2).shape(Shape.TERRACE).step(3).hill(6).depth(24);
                p.add("north2", -40, -34, 12, 10, 0.1, p.y + 5).depth(18).hill(2);
                p.add("south2", -40, 28, 12, 11, -0.3, p.y - 3).depth(18).hill(2);
                p.add("islet1", -56, 22, 5, 4, 0, p.y).depth(8);
                p.add("islet2", -20, -14, 6, 5, 0, p.y + 2).depth(8);
                p.add("centre", 0, 0, 27, 25, 0, p.y).depth(36).hill(2).trees(0.4);
                p.bridges.add(new int[] {-p.L + 8, p.y + 1, -16, -110, p.y + 4, -46});
                p.deck = "spruce_planks"; p.edge = "stripped_spruce_log[axis=y]"; p.rail = "spruce_fence";
            }
            case "emberforge" -> {
                p.L = 150; p.y = 72; p.mz = 20; p.padDx = 1; p.minutes = 20; p.icon = "MAGMA_BLOCK";
                p.add("home", -p.L - 8, 0, 36, 32, 0, p.y).shape(Shape.MESA).depth(34).hill(1);
                p.add("ridge1", -106, -16, 22, 5, 0.35, p.y + 1).shape(Shape.SPINE).wobble(0.25).depth(16).hill(2);
                p.add("ridge2", -74, 14, 22, 5, -0.4, p.y + 3).shape(Shape.SPINE).wobble(0.25).depth(16).hill(2);
                p.add("volcano", -58, -34, 14, 12, 0, p.y + 6).shape(Shape.MESA).depth(26).hill(1);
                p.add("ash", -34, 22, 10, 9, 0.3, p.y - 2).depth(16).hill(2);
                p.add("spire1", -88, -40, 4, 4, 0, p.y + 8).depth(14);
                p.add("spire2", -46, -6, 5, 5, 0, p.y + 1).depth(12);
                p.add("centre", 0, 0, 26, 24, 0.5, p.y).depth(38).hill(1).trees(0.3);
                p.bridges.add(new int[] {-p.L + 8, p.y + 1, 4, -106, p.y + 2, -16});
                p.ponds.add(new int[] {-58, -34, 4});
                p.deck = "polished_blackstone_bricks"; p.edge = "polished_blackstone"; p.rail = "nether_brick_fence";
            }
            case "kingdoms" -> {
                p.L = 105; p.y = 70; p.mz = 18; p.padDx = -4; p.minutes = 18; p.icon = "GOLDEN_HELMET";
                p.add("home", -p.L - 4, 0, 30, 28, 0, p.y).depth(30).hill(2);
                p.add("fore", -66, -2, 11, 10, 0.3, p.y + 2).depth(18).hill(2);
                p.add("village", -52, -50, 14, 12, 0.78, p.y + 4).depth(20).hill(2);
                p.add("step", -34, -14, 6, 5, 0, p.y + 1).depth(9);
                p.add("centre", 0, 0, 25, 25, 0.78, p.y).depth(34).hill(2).trees(0.5);
                p.bridges.add(new int[] {-p.L + 4, p.y + 1, -2, -66, p.y + 3, -2});
                p.deck = "spruce_planks"; p.edge = "stone_bricks"; p.rail = "spruce_fence";
            }
            case "skyreach" -> {
                p.L = 135; p.y = 74; p.mz = 18; p.padDx = 1; p.minutes = 20; p.icon = "CHORUS_FLOWER";
                p.add("home", -p.L - 6, 0, 30, 28, 0, p.y).lobes(new double[] {-8, 14, 16}).depth(34).hill(1);
                p.add("s1", -98, -22, 9, 8, 0, p.y + 6).depth(14);
                p.add("s2", -76, -40, 8, 7, 0, p.y + 12).depth(12);
                p.add("s3", -58, -22, 10, 9, 0, p.y + 4).depth(14);
                p.add("s4", -92, 20, 8, 7, 0, p.y - 4).depth(12);
                p.add("s5", -56, 18, 9, 8, 0, p.y).depth(14);
                p.add("diag", -62, -62, 12, 10, 0.78, p.y + 8).depth(18).hill(2);
                p.add("centre", 0, 0, 26, 26, 0.78, p.y).depth(40).hill(1).trees(0.4);
                p.bridges.add(new int[] {-p.L + 4, p.y + 1, -10, -98, p.y + 7, -22});
                p.deck = "end_stone_bricks"; p.edge = "purpur_pillar[axis=y]"; p.rail = "warped_fence";
            }
            default -> throw new IllegalArgumentException(id);
        }
        return p;
    }

    /** Builds a map into the world and returns its map.yml. */
    public static String build(World world, String id) {
        boolean four = fourTeams(id);
        W w = new W(world, id.hashCode() * 6151L + 29, four ? 4 : 2);
        Theme t = Theme.get(id);
        Terrain land = new Terrain(w, t);
        Plan p = plan(id);
        for (Island i : p.islands) land.add(i);
        int reach = (p.L + 60) / 16 + 1;
        for (int cx = -reach; cx <= reach; cx++) for (int cz = -reach; cz <= reach; cz++) world.getChunkAt(cx, cz).load(true);

        int bx = -p.L, by = p.y;
        land.flats.add(new Terrain.Flat(bx - 3, 0, 24, by));
        land.flats.add(new Terrain.Flat(bx + p.padDx, p.mz, 6, by));
        land.flats.add(new Terrain.Flat(bx + p.padDx, -p.mz, 6, by));
        land.flats.add(new Terrain.Flat(0, 0, 14, p.y));
        land.build();

        Bases bases = new Bases(w, t, land);
        Bases.Base base = switch (id) {
            case "tidecrest" -> bases.tidecrest(bx, by, p.mz);
            case "mycelia" -> bases.mycelia(bx, by, p.mz);
            case "sakura" -> bases.sakura(bx, by, p.mz);
            case "verdant" -> bases.verdant(bx, by, p.mz);
            case "palegrove" -> bases.palegrove(bx, by, p.mz);
            case "dustbowl" -> bases.dustbowl(bx, by, p.mz);
            case "frostfall" -> bases.frostfall(bx, by, p.mz);
            case "emberforge" -> bases.emberforge(bx, by, p.mz);
            case "kingdoms" -> bases.kingdoms(bx, by, p.mz);
            default -> bases.skyreach(bx, by, p.mz);
        };
        Centers centers = new Centers(w, t, land);
        int hy = switch (id) {
            case "tidecrest" -> centers.tidecrest(p.y);
            case "mycelia" -> centers.mycelia(p.y);
            case "sakura" -> centers.sakura(p.y);
            case "verdant" -> centers.verdant(p.y);
            case "palegrove" -> centers.palegrove(p.y);
            case "dustbowl" -> centers.dustbowl(p.y);
            case "frostfall" -> centers.frostfall(p.y);
            case "emberforge" -> centers.emberforge(p.y);
            case "kingdoms" -> centers.kingdoms(p.y);
            default -> centers.skyreach(p.y);
        };
        Arch a = bases.a;
        for (int[] b : p.bridges) a.bridge(b[0], b[1], b[2], b[3], b[4], b[5], p.deck, p.edge, p.rail, true);
        for (int[] pond : p.ponds) pond(w, t, land, pond[0], pond[1], pond[2]);
        extras(id, w, t, land, a, p);
        new Flora(w, t, land).decorate();
        if (t.snow) land.snow(-p.L - 60, p.L + 60, -p.L - 60, p.L + 60);
        w.joinAll();

        // the map file
        StringBuilder yml = new StringBuilder();
        yml.append("name: ").append(t.name).append('\n');
        yml.append("authors: [Fractal Build Team]\n");
        yml.append("icon: ").append(p.icon).append('\n');
        yml.append("modes: ").append(four ? "[ctf, ctf4, koth, dtw]" : "[ctf, koth, dtw]").append('\n');
        yml.append("time-limit-minutes: ").append(p.minutes).append("\ncapture-limit: 3\nkoth-points: 100\nwool-layers: 2\n");
        int lowest = p.y;
        for (Island i : land.islands) lowest = Math.min(lowest, i.top - (int) i.depth - 6);
        yml.append("void-y: ").append(Math.max(-40, lowest - 10)).append('\n');
        yml.append("spectator-spawn: \"0.5 ").append(hy + 30).append(" 0.5 -90 50\"\n");
        int[][] hill = Centers.hill(hy);
        yml.append("hill:\n");
        yml.append(String.format(Locale.ROOT, "  corner-1: \"%d %d %d\"%n  corner-2: \"%d %d %d\"%n", hill[0][0], hill[0][1], hill[0][2], hill[1][0], hill[1][1], hill[1][2]));
        yml.append("teams:\n");
        String[] names = four ? new String[] {"red", "green", "blue", "yellow"} : new String[] {"red", "blue"};
        for (int k = 0; k < names.length; k++) {
            int turns = four ? k : k * 2;
            double[] s = base.spawn;
            double[] sp = Maps.rotate(s[0], s[2], turns);
            float yaw = (float) (s[3] + 90 * turns);
            while (yaw > 180) yaw -= 360;
            int[] f = Maps.rotateBlock(base.flag[0], base.flag[2], turns);
            yml.append("  ").append(names[k]).append(":\n");
            yml.append(String.format(Locale.ROOT, "    spawn: \"%.1f %d %.1f %.0f 0\"%n", sp[0], (int) s[1], sp[1], yaw));
            yml.append(String.format(Locale.ROOT, "    flag: \"%d %d %d\"%n", f[0], base.flag[1], f[1]));
            List<String> pads = new ArrayList<>();
            for (int[] pad : base.pads) {
                int[] q = Maps.rotateBlock(pad[0], pad[2], turns);
                pads.add("\"" + q[0] + " " + pad[1] + " " + q[1] + "\"");
            }
            yml.append("    monuments: [").append(String.join(", ", pads)).append("]\n");
            yml.append("    wools: []\n");
        }
        return yml.toString();
    }

    /** A pond sunk into the ground: level water, a soft bank of the island's own ground, reeds only where they can grow. */
    static void pond(W w, Theme t, Terrain land, int cx, int cz, int r) {
        int y = w.ground(cx, cz);
        if (y < 0) return;
        String liquid = t.liquid.equals("ice") ? "water" : t.liquid;
        for (int dx = -r - 2; dx <= r + 2; dx++)
            for (int dz = -r - 2; dz <= r + 2; dz++) {
                int x = cx + dx, z = cz + dz;
                if (!w.canon(x, z)) continue;
                double d = Math.hypot(dx, dz) + w.n(x, z, 3) * 1.2;
                if (d > r + 1.5) continue;
                for (int dy = 1; dy <= 6; dy++) if (!w.air(x, y + dy, z)) w.set(x, y + dy, z, "air");
                if (d <= r) {
                    int deep = d < r - 2 ? 2 : 1;
                    for (int k = 0; k < deep; k++) w.set(x, y - k, z, liquid);
                    w.set(x, y - deep, z, liquid.equals("lava") ? "magma_block" : "clay");
                } else {
                    w.set(x, y, z, liquid.equals("lava") ? "blackstone" : t.surface.specs.get(0).contains("sand") ? "sand" : "grass_block");
                }
            }
        for (int dx = -r - 2; dx <= r + 2; dx++)
            for (int dz = -r - 2; dz <= r + 2; dz++) {
                int x = cx + dx, z = cz + dz;
                if (!w.canon(x, z) || !liquid.equals("water")) continue;
                if (w.type(x, y, z) == org.bukkit.Material.WATER && w.air(x, y + 1, z) && w.r.nextInt(8) == 0) w.set(x, y + 1, z, "lily_pad");
                boolean bank = w.type(x, y, z) == org.bukkit.Material.GRASS_BLOCK || w.type(x, y, z) == org.bukkit.Material.SAND;
                boolean wet = w.type(x + 1, y, z) == org.bukkit.Material.WATER || w.type(x - 1, y, z) == org.bukkit.Material.WATER
                        || w.type(x, y, z + 1) == org.bukkit.Material.WATER || w.type(x, y, z - 1) == org.bukkit.Material.WATER;
                if (bank && wet && w.air(x, y + 1, z) && w.r.nextInt(3) == 0) {
                    w.set(x, y + 1, z, "sugar_cane");
                    w.set(x, y + 2, z, "sugar_cane");
                }
            }
        land.keeps.add(new Terrain.Keep(cx, cz, r + 2));
    }

    /** Small things that make each map's islands its own. */
    static void extras(String id, W w, Theme t, Terrain land, Arch a, Plan p) {
        switch (id) {
            case "kingdoms" -> {
                // a village between kingdoms: cottages, a well and wheat fields
                int vx = -52, vz = -50, gy = w.ground(vx, vz);
                if (gy > 0) {
                    for (int[] c : new int[][] {{-5, -3}, {4, 4}}) {
                        int x0 = vx + c[0] - 2, z0 = vz + c[1] - 2;
                        a.storey(x0, z0, x0 + 5, z0 + 4, gy, 3, P.of("white_terracotta"), "stripped_spruce_log", "spruce_log", P.of("spruce_planks"), 0);
                        a.door(x0 + 5, gy, z0 + 2, "east", "spruce_door");
                        a.windows(x0, z0, x0 + 5, z0 + 4, gy, 2, 1, 3, "glass_pane", true);
                        a.gable(x0, z0, x0 + 5, z0 + 4, gy + 4, true, 1, "spruce_stairs", "spruce_slab", "spruce_planks", P.of("white_terracotta"));
                        a.bed(x0 + 1, gy + 1, z0 + 1, "east", "red");
                        w.set(x0 + 1, gy + 1, z0 + 3, "crafting_table");
                        w.hangLight(x0 + 3, gy + 3, z0 + 2, "lantern");
                    }
                    for (int x = vx - 4; x <= vx + 4; x++) for (int z = vz + 6; z <= vz + 9; z++) {
                        if (!w.canon(x, z)) continue;
                        boolean ditch = x == vx;
                        w.set(x, gy, z, ditch ? "water" : "farmland[moisture=7]");
                        if (!ditch) w.set(x, gy + 1, z, "wheat[age=" + (5 + w.r.nextInt(3)) + "]");
                    }
                    land.keeps.add(new Terrain.Keep(vx, vz, 12));
                }
            }
            case "emberforge" -> {
                // lava falls off the volcano's rim
                int gy = w.ground(-58, -45);
                if (gy > 0) w.set(-58, gy, -45, "lava");
            }
            case "sakura" -> {
                // an arched red bridge over the pond
                int gy = w.ground(-66, 28);
                if (gy > 0) for (int dx = -6; dx <= 6; dx++) {
                    int h = (int) Math.round(Math.sqrt(Math.max(0, 36 - dx * dx)) * 0.4);
                    w.set(-66 + dx, gy + 1 + h, 28, "crimson_planks");
                    w.set(-66 + dx, gy + 2 + h, 27, "crimson_fence");
                    w.set(-66 + dx, gy + 2 + h, 29, "crimson_fence");
                }
            }
            default -> { }
        }
    }
}
