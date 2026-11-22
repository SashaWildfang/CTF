package com.sasha.ctfbuilder.v2;

import com.sasha.ctfbuilder.v2.Terrain.Island;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The ten arenas. Each is a chain of floating islands from one team's home island to the other's, with
 * void gaps that have to be bridged: only a couple of crossings are built for you.
 */
public final class Maps {

    private Maps() {}

    public static final String[] IDS = {"frostfall", "dustbowl", "kingdoms", "sakura", "emberforge", "verdant", "tidecrest", "mycelia", "palegrove", "skyreach"};

    public static boolean fourTeams(String id) {
        return id.equals("kingdoms") || id.equals("skyreach");
    }

    /** Builds a map into the world and returns its map.yml. */
    public static String build(org.bukkit.World world, String id) {
        boolean four = fourTeams(id);
        W w = new W(world, id.hashCode() * 977L + 13, four ? 4 : 2);
        Theme t = Theme.get(id);
        Terrain land = new Terrain(w, t);
        Build build = new Build(w, t, land);
        Flora flora = new Flora(w, t, land);
        Layout layout = switch (id) {
            case "dustbowl", "emberforge", "mycelia" -> zigzag(w, land, id);
            case "kingdoms", "skyreach" -> cross(w, land, id);
            default -> lanes(w, land, id);
        };
        // load the area first
        int reach = four ? 13 : 14;
        for (int cx = -reach; cx <= reach; cx++) for (int cz = -reach; cz <= reach; cz++) world.getChunkAt(cx, cz).load(true);

        land.flats.add(new Terrain.Flat(layout.baseX - 2, 0, 20, layout.baseY));
        land.flats.add(new Terrain.Flat(layout.baseX - 2, layout.monumentZ, 5, layout.baseY));
        land.flats.add(new Terrain.Flat(layout.baseX - 2, -layout.monumentZ, 5, layout.baseY));
        land.flats.add(new Terrain.Flat(0, 0, 11, layout.hillY));
        land.build();
        Build.Base base = build.base(layout.baseX, layout.baseY, layout.monumentZ);
        int[][] hill = build.hill(layout.hillY);
        for (int[] b : layout.bridges) build.ropeBridge(b[0], b[1], b[2], b[3], b[4], b[5]);
        for (int[] p : layout.paths) land.path(p[0], java.util.Arrays.copyOfRange(p, 1, p.length));
        for (int[] p : layout.ponds) build.pond(p[0], p[1], p[2]);
        land.keeps.add(new Terrain.Keep(0, 0, 16));
        for (int[] r : layout.ruins) {
            int y = w.ground(r[0], r[1]);
            if (y > 0) build.ruin(r[0], y, r[1]);
        }
        flora.decorate();
        if (t.snow) land.snow(-230, 230, -230, 230);

        // the map file
        StringBuilder yml = new StringBuilder();
        yml.append("name: ").append(t.name).append('\n');
        yml.append("authors: [Fractal Build Team]\n");
        yml.append("modes: ").append(four ? "[ctf, ctf4, koth, dtw]" : "[ctf, koth, dtw]").append('\n');
        yml.append("time-limit-minutes: 20\ncapture-limit: 3\nkoth-points: 100\n");
        int lowest = layout.baseY;
        for (Island i : land.islands) lowest = Math.min(lowest, i.top - (int) i.depth);
        yml.append("void-y: ").append(Math.max(-40, lowest - 12)).append('\n');
        yml.append("spectator-spawn: \"0.5 ").append(layout.hillY + 32).append(" 0.5 -90 50\"\n");
        yml.append("hill:\n");
        yml.append(String.format(Locale.ROOT, "  corner-1: \"%d %d %d\"%n  corner-2: \"%d %d %d\"%n", hill[0][0], hill[0][1], hill[0][2], hill[1][0], hill[1][1], hill[1][2]));
        yml.append("teams:\n");
        String[] names = four ? new String[] {"red", "green", "blue", "yellow"} : new String[] {"red", "blue"};
        for (int k = 0; k < names.length; k++) {
            int turns = four ? k : k * 2;
            double[] s = base.spawn;
            double[] sp = rotate(s[0], s[2], turns);
            float yaw = (float) (s[3] + 90 * turns);
            while (yaw > 180) yaw -= 360;
            int[] f = rotateBlock(base.flag[0], base.flag[2], turns);
            yml.append("  ").append(names[k]).append(":\n");
            yml.append(String.format(Locale.ROOT, "    spawn: \"%.1f %d %.1f %.0f 0\"%n", sp[0], (int) s[1], sp[1], yaw));
            yml.append(String.format(Locale.ROOT, "    flag: \"%d %d %d\"%n", f[0], base.flag[1], f[1]));
            boolean wool = k == 0 || names[k].equals("blue");
            List<String> wools = new ArrayList<>();
            if (wool) for (int[] p : base.wools) {
                int[] q = rotateBlock(p[0], p[2], turns);
                wools.add("\"" + q[0] + " " + p[1] + " " + q[1] + "\"");
            }
            yml.append("    wools: [").append(String.join(", ", wools)).append("]\n");
        }
        return yml.toString();
    }

    /** Rotates a point (block centres) by quarter turns clockwise, the same way the writer copies blocks. */
    static double[] rotate(double x, double z, int turns) {
        // x, z here are block-centre coordinates (e.g. 12.5); rotate the block, then re-add the half
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        int[] b = rotateBlock(bx, bz, turns);
        return new double[] {b[0] + 0.5, b[1] + 0.5};
    }

    static int[] rotateBlock(int x, int z, int turns) {
        for (int i = 0; i < turns; i++) {
            int nx = -z, nz = x;
            x = nx;
            z = nz;
        }
        return new int[] {x, z};
    }

    // ---------------------------------------------------------------- layouts

    /** Where things go: the base, the hill, crossings that are built, paths and ruins. */
    static final class Layout {
        int baseX, baseY, hillY, monumentZ = 21;
        final List<int[]> bridges = new ArrayList<>();
        final List<int[]> paths = new ArrayList<>();
        final List<int[]> ruins = new ArrayList<>();
        final List<int[]> ponds = new ArrayList<>();
    }

    /** Three lanes: a home island, wing islands, forward islands, mid islands and a big centre island. */
    static Layout lanes(W w, Terrain land, String id) {
        Layout l = new Layout();
        int L = 150, y = 70;
        l.baseX = -L; l.baseY = y; l.hillY = y + 2;
        int j = Math.floorMod(id.hashCode(), 7) - 3; // a little variety per map
        land.add(new Island("home", -L - 4, 0, 36, 33, 0.1, y).hill(2).depth(30));
        land.add(new Island("wingN", -L + 40, -44 + j, 15, 11, 0.4, y + 3).depth(18));
        land.add(new Island("wingS", -L + 42, 46 - j, 14, 12, -0.3, y - 2).depth(18));
        land.add(new Island("fore", -L + 58, 2, 14, 12, 0.2, y - 1).depth(20));
        land.add(new Island("midN", -60 + j, -40, 17, 13, 0.3, y + 4).hill(4).depth(22));
        land.add(new Island("midS", -58, 38 + j, 16, 14, -0.2, y - 3).hill(4).depth(22));
        land.add(new Island("midC", -44, 3, 9, 8, 0, y + 1).depth(14));
        land.add(new Island("centre", 0, 0, 28, 24, 0, y + 1).hill(3).depth(34).trees(0.45));
        // stepping stones
        land.add(new Island("islet1", -L + 22, -24, 5, 4, 0, y + 2).depth(8));
        land.add(new Island("islet2", -88, -20, 5, 5, 0, y + 5).depth(8));
        land.add(new Island("islet3", -86, 24, 6, 4, 0.5, y - 4).depth(8));
        land.add(new Island("islet4", -28, -36, 5, 5, 0, y + 3).depth(8));
        land.add(new Island("islet5", -30, 36, 5, 4, 0, y - 1).depth(8));
        // a built crossing: home to the north wing (the rest has to be bridged)
        l.bridges.add(new int[] {-L + 24, y + 1, -26, -L + 32, y + 3, -36});
        l.paths.add(new int[] {1, -L + 14, 0, -L + 28, 0});
        l.paths.add(new int[] {1, -L + 14, -6, -L + 22, -22});
        l.paths.add(new int[] {1, -14, 0, -22, 0});
        l.ruins.add(new int[] {-60 + j, -44});
        l.ruins.add(new int[] {-L + 58, 6});
        l.ponds.add(new int[] {-56, 40 + j, 5});
        return l;
    }

    /** Zigzag: the islands alternate sides, with a long ridge island down one flank. */
    static Layout zigzag(W w, Terrain land, String id) {
        Layout l = new Layout();
        int L = 155, y = 72;
        l.baseX = -L; l.baseY = y; l.hillY = y;
        land.add(new Island("home", -L - 4, 0, 36, 32, -0.1, y).hill(2).depth(30));
        land.add(new Island("z1", -L + 50, -30, 16, 13, 0.6, y + 4).hill(3).depth(22));
        land.add(new Island("z2", -L + 78, 22, 17, 13, -0.5, y - 2).hill(4).depth(22));
        land.add(new Island("z3", -55, -26, 16, 14, 0.4, y + 6).hill(4).depth(24));
        land.add(new Island("ridge", -95, 56, 34, 7, 0.08, y - 4).hill(2).depth(16));
        land.add(new Island("z4", -40, 30, 12, 10, 0.2, y - 1).depth(16));
        land.add(new Island("centre", 0, 0, 27, 24, 0.3, y).hill(4).depth(34).trees(0.45));
        land.add(new Island("islet1", -L + 30, 30, 5, 4, 0, y - 3).depth(8));
        land.add(new Island("islet2", -100, -2, 6, 5, 0, y + 1).depth(8));
        land.add(new Island("islet3", -24, -34, 5, 5, 0, y + 4).depth(8));
        land.add(new Island("islet4", -70, 8, 4, 4, 0, y + 3).depth(7));
        l.bridges.add(new int[] {-L + 26, y + 1, 18, -L + 64, y - 1, 26});
        l.paths.add(new int[] {1, -L + 14, 0, -L + 26, 14});
        l.paths.add(new int[] {1, -14, 0, -24, 0});
        l.ruins.add(new int[] {-55, -26});
        l.ruins.add(new int[] {-110, 56});
        l.ponds.add(new int[] {-L + 80, 24, 5});
        return l;
    }

    /** Four teams: homes on the four sides, arms of islands between them and a big centre. */
    static Layout cross(W w, Terrain land, String id) {
        Layout l = new Layout();
        int L = 128, y = 70;
        l.baseX = -L; l.baseY = y; l.hillY = y + 2; l.monumentZ = 20;
        land.add(new Island("home", -L - 4, 0, 34, 31, 0, y).hill(2).depth(28));
        land.add(new Island("fore", -L + 50, -6, 14, 12, 0.3, y + 2).depth(20));
        land.add(new Island("diag", -62, -60, 18, 13, 0.78, y + 4).hill(4).depth(22));
        land.add(new Island("step", -88, -34, 7, 6, 0, y + 1).depth(10));
        land.add(new Island("centre", 0, 0, 30, 30, 0.78, y + 1).hill(3).depth(36).trees(0.4));
        land.add(new Island("islet", -44, -18, 6, 5, 0.2, y + 3).depth(8));
        l.paths.add(new int[] {1, -L + 14, 0, -L + 30, -4});
        l.paths.add(new int[] {1, -14, 0, -26, 0});
        l.ruins.add(new int[] {-62, -60});
        l.ponds.add(new int[] {-58, -54, 4});
        return l;
    }
}
