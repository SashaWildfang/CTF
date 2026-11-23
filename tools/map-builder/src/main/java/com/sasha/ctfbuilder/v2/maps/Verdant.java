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
 * Verdant: the Canopy. There is no ground. Giant trees stand in the void with their roots hanging into it, and
 * everything is built in them, at three heights: the roots (63), the branches (79) and the canopy (95).
 *
 * Each team lives in a colossal hollow tree. Inside the trunk the root hall at the bottom, a switchback stair up
 * to the trunk hall (the spawn), and out of its door the branch platform with the flag and the treehouses. The root
 * deck hangs under the platform; a stair up the outside of the trunk climbs to the canopy deck in the crown. Two
 * smaller trees stand off its flanks, each with a deck at branch height and another in its crown; living branch
 * roads join them to the great tree, and rope walkways join the crowns.
 *
 * In the middle, held up on a lump of earth and roots, the Sky Temple: a stepped pyramid with a grand stair up each
 * side to the summit (the hill), a strangler fig growing out of one corner. Three ways reach it: the roots route
 * (along great roots, over a fungal rock, to the foot of the grand stair), the living root bridges from the flank
 * trees to its upper terrace (a shrine halfway along one of them is a hill), and rope walkways from the flank
 * trees' crowns down to the summit.
 */
public final class Verdant extends Arena {

    static final int ROOT = 63, BRANCH = 79, CANOPY = 95;
    static final int GX = -94;                                   // the great tree's trunk, on z = 0
    static final int FX = -54, FZ = 36;                          // the flank trees, at (FX, -FZ) and (FX, FZ)
    static final int APRON = 65, T1 = 70, T2 = 75, SUMMIT = 80;  // the temple's apron, terraces and summit
    static final int A_H = 24, T1_H = 15, T2_H = 10, S_H = 5;    // and their half-widths
    static final int ROCK_X = -48;                               // the fungal rock on the roots route
    static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    Land land;

    public Verdant(World world) {
        super(world, "verdant", false, Theme.get("verdant"));
        name = "Verdant";
        icon = "JUNGLE_SAPLING";
        minutes = 16;
        kothPoints = 250;
        reach = 130;
        low = 14;
        high = 140;
        voidY = 18;
        spectator = new double[] {0.5, 100, 14.5, 180, 25};
    }

    // ================================================================ the ground there is

    /** Floating lumps of earth: the temple's, the fungal rock, mossy rocks to build out to. x, z, radius, top. */
    static final int SHRINE_Z = 42, SHRINE = 74;                 // the shrine rocks, north and south of the temple
    static final double[][] LUMPS = {{0, 0, 27, 64}, {ROCK_X, 0, 7.5, ROOT}, {0, -SHRINE_Z, 7.2, SHRINE}, {0, SHRINE_Z, 7.2, SHRINE},
            {-77, 33, 4.6, 71}, {-27, -45, 4.2, 78}, {-24, 31, 4.4, 69}, {-108, -32, 4.2, 83}, {-80, -46, 3.8, 90}};

    double lumpTop(int x, int z) {
        double best = Double.NaN;
        for (double[] l : LUMPS) {
            double d = Math.hypot(x - l[0], z - l[1]) + w.fbm(x * 1.3 + l[0], z * 1.3 - l[1], 5, 2) * (l[2] > 10 ? 2.2 : 0.9);
            if (d < l[2] && (Double.isNaN(best) || l[3] > best)) best = l[3];
        }
        return best;
    }

    String surface(int x, int z, int y, int steep, double edge) {
        double n = w.fbm(x + 211, z - 97, 6, 2);
        if (steep >= 3) return n > 0.3 ? "mossy_cobblestone" : n < -0.3 ? "andesite" : "stone";
        return n > 0.35 ? "rooted_dirt" : n < -0.35 ? "podzol" : "moss_block";
    }

    String rock(int x, int y, int z) {
        double n = w.n3(x * 1.7, y * 1.7, z * 1.7, 4);
        return n > 0.5 ? "mossy_cobblestone" : n > 0.2 ? "dirt" : n < -0.45 ? "andesite" : n < -0.2 ? "rooted_dirt" : "stone";
    }

    int bottom(int x, int z, int top, double edge) {
        double lens = 1 - Math.pow(1 - Math.min(1, edge / 18.0), 1.6);
        double b = top - 3 - 24 * lens + w.fbm(x * 1.1 - 9, z * 1.1 + 4, 8, 2) * 3 * Math.min(1, edge / 5.0);
        return Math.max(low + 3, (int) Math.round(Math.min(b, top - 2)));
    }

    // ================================================================ the map

    @Override
    protected void build() {
        land = new Land(w, t, -130, 130, -60, 60);
        land.top = this::lumpTop;
        land.surface = this::surface;
        land.rock = this::rock;
        land.bottom = this::bottom;
        land.soil = P.of("dirt", 3, "rooted_dirt", 2, "mud", 1);
        land.build();

        temple();
        greatTree();
        for (int s : new int[] {-1, 1}) flankTree(FX, s * FZ, s);
        fungalRock();
        shrines();
        fig(-20, 20);
        lumpDressing();

        spawn = new double[] {GX - 2.5, BRANCH + 1, -2.5, -90};
        flag = new int[] {-75, BRANCH + 1, 0};
        pads.add(new int[] {-73, BRANCH + 1, -6});
        pads.add(new int[] {-73, BRANCH + 1, 6});
        hills.add(new Hill("North Shrine", -3, SHRINE + 1, -SHRINE_Z - 3, 3, SHRINE + 4, -SHRINE_Z + 3));
        hills.add(new Hill("Sky Temple", -3, SUMMIT + 1, -3, 3, SUMMIT + 4, 3));
        hills.add(new Hill("South Shrine", -3, SHRINE + 1, SHRINE_Z - 3, 3, SHRINE + 4, SHRINE_Z + 3));
    }

    int ground(int x, int z) {
        return land.topAt(x, z);
    }

    // ================================================================ drawing helpers

    /** Fills a ball of radius r round (cx, cy, cz) wherever the spot is free (air, leaves or plants), canonical cells only. */
    void ball(double cx, double cy, double cz, double r, String spec, boolean overLeaves) {
        int x0 = (int) Math.floor(cx - r), x1 = (int) Math.ceil(cx + r), y0 = (int) Math.floor(cy - r), y1 = (int) Math.ceil(cy + r);
        int z0 = (int) Math.floor(cz - r), z1 = (int) Math.ceil(cz + r);
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) {
            double dx = x + 0.5 - cx, dy = y + 0.5 - cy, dz = z + 0.5 - cz;
            if (dx * dx + dy * dy + dz * dz > r * r || !w.canon(x, z)) continue;
            Material m = w.type(x, y, z);
            if (m.isAir() || overLeaves && m.name().endsWith("LEAVES") || !m.isSolid() && m != Material.WATER) w.set(x, y, z, spec);
        }
    }

    /** A tube of wood along a polyline (x, y, z points), its radius going from r0 to r1. */
    void tube(double[][] pts, double r0, double r1, String spec) {
        double total = 0;
        for (int i = 0; i + 1 < pts.length; i++) total += dist(pts[i], pts[i + 1]);
        double acc = 0;
        for (int i = 0; i + 1 < pts.length; i++) {
            double[] a = pts[i], b = pts[i + 1];
            double len = dist(a, b);
            for (double s = 0; s <= len; s += 0.35) {
                double f = len == 0 ? 0 : s / len, r = r0 + (r1 - r0) * ((acc + s) / Math.max(1e-6, total));
                ball(a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f, r, spec, true);
            }
            acc += len;
        }
    }

    static double dist(double[] a, double[] b) {
        return Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
    }

    /** A blob of leaves: an ellipsoid with a ragged edge and a few holes. */
    void leaves(double cx, double cy, double cz, double r, double squash, String spec) {
        int R = (int) Math.ceil(r) + 1;
        for (int dx = -R; dx <= R; dx++) for (int dy = -R; dy <= R; dy++) for (int dz = -R; dz <= R; dz++) {
            int x = (int) Math.floor(cx) + dx, y = (int) Math.floor(cy) + dy, z = (int) Math.floor(cz) + dz;
            if (!w.canon(x, z)) continue;
            double ex = x + 0.5 - cx, ey = (y + 0.5 - cy) / squash, ez = z + 0.5 - cz;
            double d = Math.sqrt(ex * ex + ey * ey + ez * ez) + w.n3(x * 2.3, y * 2.3, z * 2.3, 3) * 1.4;
            if (d > r || w.n3(x * 4.1 + 7, y * 4.1, z * 4.1, 2) > 0.62) continue;
            if (w.air(x, y, z)) w.set(x, y, z, spec);
        }
    }

    /** Vines down the outside of whatever is solid next to (x, z) from y, up to len blocks. */
    void vine(int x, int y, int z, int len) {
        for (int k = 0; k < len; k++) {
            int yy = y - k;
            if (!w.air(x, yy, z)) return;
            StringBuilder faces = new StringBuilder();
            String[] names = {"east", "west", "south", "north"};
            for (int i = 0; i < 4; i++) {
                Material m = w.type(x + DIRS[i][0], yy, z + DIRS[i][1]);
                if (m.isSolid() && m.isOccluding()) faces.append(faces.length() > 0 ? "," : "").append(names[i]).append("=true");
            }
            if (faces.length() == 0) return;
            w.set(x, yy, z, "vine[" + faces + "]");
        }
    }

    /** Whether plants can grow on the block at (x, y, z). */
    boolean dirt(int x, int y, int z) {
        String n = w.type(x, y, z).name();
        return n.equals("GRASS_BLOCK") || n.endsWith("DIRT") || n.equals("PODZOL") || n.equals("MOSS_BLOCK") || n.equals("MUD") || n.equals("MYCELIUM");
    }

    /** Hanging roots under (x, y, z) if it's free and the block above can hold them. */
    void hangRoot(int x, int y, int z) {
        if (w.air(x, y, z) && w.type(x, y + 1, z).isSolid()) w.set(x, y, z, "hanging_roots");
    }

    // ================================================================ the great tree

    /** The great tree's radius at height y: a column of twelve tapering upward, flaring into buttresses below 60. */
    static double greatR(double y) {
        return y >= 60 ? 12 - 0.045 * (y - 60) : 12 + 6 * Math.pow((60 - y) / 12.0, 1.5);
    }

    /** The bark's fluting: ridges that wander a little with height. */
    static double flute(double a, double y, double seed) {
        return 1 + 0.06 * Math.sin(6 * a + y * 0.07 + seed) + 0.03 * Math.sin(11 * a - y * 0.11 + seed * 2);
    }

    String bark(int x, int y, int z, double a) {
        double n = w.n3(x * 0.4, y * 0.3, z * 0.4, 3);
        if (n > 0.6) return "jungle_wood";
        if (Math.sin(a) < -0.2 && w.n3(x * 0.6 + 40, y * 0.6, z * 0.6, 2) > 0.42) return "moss_block";
        return "jungle_log[axis=y]";
    }

    /**
     * The great tree: the trunk (hollow from the root hall up through the trunk hall), its buttresses and great roots
     * hanging into the void, the root deck, the branch platform with the flag and treehouses, the stair up the outside
     * to the canopy deck, the crown.
     */
    void greatTree() {
        int cx = GX;
        // the trunk
        for (int y = 38; y <= 108; y++) {
            double r = greatR(Math.max(y, 48));
            if (y < 48) r *= Math.sqrt(Math.max(0, 1 - Math.pow((48 - y) / 10.0, 2)));
            if (r < 0.5) continue;
            int R = (int) Math.ceil(r * 1.1) + 1;
            double rin = greatR(y) - 2.2;
            boolean hall = y >= 64 && y <= 78 || y >= 80 && y <= 92;
            for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) {
                int x = cx + dx, z = dz;
                double a = Math.atan2(dz, dx), d = Math.hypot(dx, dz), re = r * flute(a, y, 0.3);
                if (d > re) continue;
                if (hall && d < rin) {
                    w.set(x, y, z, "air");
                    continue;
                }
                w.set(x, y, z, d > re - 1.4 ? bark(x, y, z, a) : "stripped_jungle_wood");
            }
        }
        // great roots: out from the buttresses and down into the void, hanging roots off them
        for (int k = 0; k < 9; k++) {
            double a = k * 2 * Math.PI / 9 + 0.2, a2 = a + (w.r.nextDouble() - 0.5) * 0.5;
            double r0 = 15, y0 = 50 - (k % 3);
            double[][] pts = {{cx + Math.cos(a) * 11, y0 + 4, Math.sin(a) * 11}, {cx + Math.cos(a) * r0, y0, Math.sin(a) * r0},
                    {cx + Math.cos(a2) * 22, y0 - 7, Math.sin(a2) * 22}, {cx + Math.cos(a2) * 25, y0 - 18 - k % 4 * 2, Math.sin(a2) * 25}};
            tube(pts, 2.8, 0.9, "jungle_wood");
        }
        // the halls inside: floors, doors, windows, lamps
        for (int dx = -12; dx <= 12; dx++) for (int dz = -12; dz <= 12; dz++) {
            int x = cx + dx, z = dz;
            double d = Math.hypot(dx, dz);
            if (d < greatR(63) - 2.2) w.set(x, ROOT, z, ((dx + dz) & 3) == 0 ? "rooted_dirt" : "moss_block");
            if (d < greatR(79) - 2.2) w.set(x, BRANCH, z, d < 3.5 ? ((((dx + 9) / 2 + (dz + 9) / 2) & 1) == 0 ? "TEAM_glazed_terracotta" : "jungle_planks")
                    : Math.floorMod(dx, 3) == 0 ? "stripped_jungle_wood" : "jungle_planks");
        }
        for (int dy = 1; dy <= 4; dy++) for (int dz = -1; dz <= 1; dz++) {
            for (int x = cx + 7; x <= cx + 13; x++) {
                if (dy <= 3 || dz == 0) w.set(x, ROOT + dy, dz, "air");
                if (dy <= 3 || dz == 0) w.set(x, BRANCH + dy, dz, "air");
            }
        }
        for (double a : new double[] {Math.PI * 0.6, Math.PI, Math.PI * 1.4, Math.PI * 0.25, -Math.PI * 0.25}) {
            int x = cx + (int) Math.round(Math.cos(a) * (greatR(85) - 1)), z = (int) Math.round(Math.sin(a) * (greatR(85) - 1));
            for (int dy = 4; dy <= 6; dy++) for (int k = -1; k <= 1; k++) {
                int xx = cx + (int) Math.round(Math.cos(a) * (greatR(85) - 1 + k)), zz = (int) Math.round(Math.sin(a) * (greatR(85) - 1 + k));
                w.set(xx, BRANCH + dy, zz, "air");
            }
            w.set(x, BRANCH + 3, z, "jungle_trapdoor[facing=" + (Math.cos(a) > 0.5 ? "west" : Math.cos(a) < -0.5 ? "east" : Math.sin(a) > 0 ? "north" : "south") + ",half=top,open=false]");
        }
        w.hangLight(cx, BRANCH + 9, 0, "lantern");
        w.hangLight(cx - 4, BRANCH + 8, -4, "lantern");
        w.hangLight(cx - 4, BRANCH + 8, 4, "lantern");
        w.hangLight(cx + 3, BRANCH + 8, -5, "lantern");
        for (int[] b : new int[][] {{-1, 0}, {0, -1}, {0, 1}}) {
            // walk out from the middle to the wall and hang the banner on it, facing in
            for (int r = 1; r < 13; r++) {
                int x = cx + b[0] * r, z = b[1] * r;
                if (w.air(x, BRANCH + 4, z) && w.type(x + b[0], BRANCH + 4, z + b[1]).isSolid()) {
                    String f = b[0] < 0 ? "east" : b[1] < 0 ? "south" : "north";
                    w.set(x, BRANCH + 4, z, "TEAM_wall_banner[facing=" + f + "]");
                    break;
                }
            }
        }
        for (int dz = -2; dz <= 2; dz++) w.set(cx - 7, BRANCH + 1, dz, dz == 0 ? "lectern[facing=east]" : "jungle_stairs[facing=west]");
        // shroomlights and glow in the root hall
        for (double a = 0.3; a < 2 * Math.PI; a += 0.9) {
            int x = cx + (int) Math.round(Math.cos(a) * (greatR(70) - 1.6)), z = (int) Math.round(Math.sin(a) * (greatR(70) - 1.6));
            w.set(x, ROOT + 4 + (int) (a * 2) % 5, z, "shroomlight");
        }
        rootStair(cx);
        rootDeck(cx);
        platform(cx);
        canopyDeck(cx);
        canopyStair(cx);
        crown(cx, 0, 104, 24, 12, "jungle_leaves[persistent=true]");
        keepClear(cx - 30, -30, cx + 30, 30);
    }

    /** The switchback inside the trunk from the root hall up to the trunk hall, with a rail round its well. */
    void rootStair(int cx) {
        for (int i = 1; i <= 8; i++) {
            int x = cx + 7 - i, sy = ROOT + i;
            for (int z = 1; z <= 2; z++) {
                for (int yy = ROOT + 1; yy < sy; yy++) w.set(x, yy, z, "stripped_jungle_wood");
                w.set(x, sy, z, "jungle_stairs[facing=west,half=bottom]");
                for (int dy = 1; dy <= 3; dy++) w.set(x, sy + dy, z, "air");
            }
        }
        for (int x = cx - 5; x <= cx - 2; x++) for (int z = 1; z <= 6; z++) {
            for (int yy = ROOT + 1; yy < ROOT + 8; yy++) w.set(x, yy, z, "stripped_jungle_wood");
            w.set(x, ROOT + 8, z, "jungle_planks");
            for (int dy = 1; dy <= 3; dy++) w.set(x, ROOT + 8 + dy, z, "air");
        }
        for (int i = 1; i <= 8; i++) {
            int x = cx - 2 + i, sy = ROOT + 8 + i;
            for (int z = 4; z <= 5; z++) {
                for (int yy = ROOT + 1; yy < sy; yy++) if (z == 5 || yy > ROOT + 8) w.set(x, yy, z, "stripped_jungle_wood");
                w.set(x, sy, z, "jungle_stairs[facing=east,half=bottom]");
                for (int dy = 1; dy <= 3; dy++) w.set(x, sy + dy, z, "air");
            }
        }
        // the spine between the flights, and a rail round the well in the trunk hall's floor
        for (int x = cx - 1; x <= cx + 6; x++) for (int yy = ROOT + 1; yy <= ROOT + 8 + (x - cx + 2); yy++) if (yy < BRANCH) w.set(x, yy, 3, "stripped_jungle_wood");
        for (int x = cx + 2; x <= cx + 5; x++) {
            w.set(x, BRANCH + 1, 3, "jungle_fence");
            w.set(x, BRANCH + 1, 6, "jungle_fence");
        }
        for (int z = 4; z <= 5; z++) w.set(cx + 2, BRANCH + 1, z, "jungle_fence");
        w.hangLight(cx - 3, ROOT + 12, 3, "lantern");
    }

    /** The root deck: boards laid over the great roots under the branch platform, the door into the root hall. */
    void rootDeck(int cx) {
        for (int x = cx + 9; x <= cx + 24; x++) for (int z = -9; z <= 9; z++) {
            if (Math.hypot(x - cx, z) < greatR(ROOT) - 0.5) continue;
            boolean rim = x == cx + 24 || Math.abs(z) == 9;
            w.set(x, ROOT, z, ((x + z) & 3) == 0 ? "stripped_jungle_wood" : "jungle_planks");
            for (int dy = 1; dy <= 4; dy++) if (!w.air(x, ROOT + dy, z) && x > cx + 12) w.set(x, ROOT + dy, z, "air");
            boolean gate = x == cx + 24 && Math.abs(z) <= 2;
            if (rim && !gate) w.set(x, ROOT + 1, z, "jungle_fence");
            if (rim && !gate && (x + z) % 6 == 0) w.set(x, ROOT + 2, z, "lantern");
            if (((x * 3 + z) & 7) == 0) hangRoot(x, ROOT - 1, z);
        }
        lamp(cx + 24, ROOT, -3);
        lamp(cx + 24, ROOT, 3);
        // the beams under it: great roots running out from the buttresses
        for (int z : new int[] {-7, 0, 7}) tube(new double[][] {{cx + 10, ROOT - 3, z}, {cx + 18, ROOT - 2.4, z * 1.1}, {cx + 25, ROOT - 4, z * 1.2}, {cx + 29, ROOT - 12, z * 1.3}}, 2.0, 0.8, "jungle_wood");
        for (int[] p : new int[][] {{cx + 14, -7}, {cx + 20, 6}, {cx + 22, -3}}) {
            w.set(p[0], ROOT + 1, p[1], "shroomlight");
            w.set(p[0] + 1, ROOT, p[1], "podzol");
            w.set(p[0] + 1, ROOT + 1, p[1], (p[1] & 1) == 0 ? "red_mushroom" : "brown_mushroom");
        }
    }

    /** The branch platform at the trunk hall's door: the flag, two treehouses, a rail with gaps where the roads leave. */
    void platform(int cx) {
        int y = BRANCH;
        for (int x = cx + 9; x <= cx + 27; x++) for (int z = -12; z <= 12; z++) {
            if (Math.hypot(x - cx, z) < greatR(y) - 0.4) continue;
            w.set(x, y, z, Math.floorMod(z, 4) == 0 ? "stripped_jungle_log[axis=x]" : "jungle_planks");
            for (int dy = 1; dy <= 5; dy++) if (!w.air(x, y + dy, z) && Math.hypot(x - cx, z) > greatR(y + dy) + 0.6) w.set(x, y + dy, z, "air");
        }
        // the flag's ring of the team's colour
        for (int x = -78; x <= -72; x++) for (int z = -3; z <= 3; z++) {
            double d = Math.hypot(x + 75, z);
            if (d < 3.3) w.set(x, y, z, d < 1.5 ? "chiseled_tuff_bricks" : "TEAM_terracotta");
        }
        // the rail round the outer edges, open where the roads go north-east and south-east and the stair goes up
        for (int x = cx + 9; x <= cx + 27; x++) for (int z = -12; z <= 12; z++) {
            boolean edge = x == cx + 27 || Math.abs(z) == 12;
            if (!edge || Math.hypot(x - cx, z) < greatR(y)) continue;
            if (Math.abs(z) == 12 && x >= cx + 18 && x <= cx + 25) continue;            // the branch roads
            if (z == -12 && x <= cx + 11) continue;                                     // the canopy stair
            boolean post = Math.floorMod(x + z, 5) == 0;
            w.set(x, y + 1, z, post ? "stripped_jungle_log[axis=y]" : "jungle_fence");
            if (post) w.set(x, y + 2, z, "lantern");
        }
        for (int sz : new int[] {-12, 12}) {
            lamp(cx + 17, y, sz);
            lamp(cx + 26, y, sz);
        }
        // the beams under it
        for (int z : new int[] {-9, 0, 9}) tube(new double[][] {{cx + 9, y - 4, z * 0.6}, {cx + 18, y - 2.6, z}, {cx + 28, y - 2.4, z * 1.1}}, 2.4, 1.6, "jungle_wood");
        for (int x = cx + 10; x <= cx + 27; x += 3) for (int z = -12; z <= 12; z += 4) hangRoot(x, y - 1, z);
        // two treehouses by the trunk, roofs of the team's colour
        hut(cx + 11, -11, cx + 15, -7, y, "south");
        hut(cx + 11, 7, cx + 15, 11, y, "north");
    }

    /** A treehouse: bamboo walls on posts, open windows, a stepped roof of the team's wool, a lamp. */
    void hut(int x0, int z0, int x1, int z1, int y, String door) {
        int h = 4;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1), edge = x == x0 || x == x1 || z == z0 || z == z1;
            w.set(x, y, z, "bamboo_planks");
            for (int dy = 1; dy <= h; dy++) w.set(x, y + dy, z, corner ? "stripped_bamboo_block[axis=y]" : edge ? (dy == 2 && ((x + z) & 1) == 0 ? "air" : "bamboo_planks") : "air");
        }
        int cx = (x0 + x1) / 2, cz = (z0 + z1) / 2;
        int dx = Kit.dx(door), dz = Kit.dz(door);
        int doorX = dx > 0 ? x1 : dx < 0 ? x0 : cx, doorZ = dz > 0 ? z1 : dz < 0 ? z0 : cz;
        for (int dy = 1; dy <= 3; dy++) w.set(doorX, y + dy, doorZ, "air");
        // the roof: rings of wool stepping in, a carpet fringe
        for (int k = 0; k <= 3; k++) {
            for (int x = x0 - 1 + k; x <= x1 + 1 - k; x++) for (int z = z0 - 1 + k; z <= z1 + 1 - k; z++) {
                boolean ring = x == x0 - 1 + k || x == x1 + 1 - k || z == z0 - 1 + k || z == z1 + 1 - k;
                if (ring || k == 3) w.set(x, y + h + 1 + k, z, "TEAM_wool");
            }
        }
        w.hangLight(cx, y + h, cz, "lantern");
        w.set(x0 + 1, y + 1, z0 + 1, "barrel[facing=up]");
    }

    /** The stair up the north side of the trunk from the branch platform to the canopy deck: two flights and landings. */
    void canopyStair(int cx) {
        int y = BRANCH;
        for (int x = cx + 10; x <= cx + 11; x++) for (int z = -14; z <= -12; z++) {
            w.set(x, y, z, "jungle_planks");
            for (int dy = 1; dy <= 3; dy++) w.set(x, y + dy, z, "air");
        }
        for (int i = 1; i <= 8; i++) {
            int x = cx + 10 - i, sy = y + i;
            for (int z = -14; z <= -13; z++) {
                w.set(x, sy, z, "jungle_stairs[facing=west,half=bottom]");
                w.set(x, sy - 1, z, "jungle_planks");
                for (int dy = 1; dy <= 3; dy++) w.set(x, sy + dy, z, "air");
            }
            w.set(x, sy, -15, "jungle_planks");
            w.set(x, sy + 1, -15, i % 4 == 0 ? "stripped_jungle_log[axis=y]" : "jungle_fence");
            if (w.air(x, sy, -12) && w.air(x, sy + 1, -12)) {
                w.set(x, sy, -12, "jungle_planks");
                w.set(x, sy + 1, -12, "jungle_fence");
            }
        }
        for (int x = cx - 2; x <= cx + 1; x++) for (int z = -17; z <= -13; z++) {
            w.set(x, y + 8, z, "jungle_planks");
            for (int dy = 1; dy <= 3; dy++) w.set(x, y + 8 + dy, z, "air");
            if (x == cx - 2 || z == -17 && x < cx || z == -13 && x < cx + 1) w.set(x, y + 9, z, "jungle_fence");
        }
        w.set(cx - 2, y + 10, -17, "lantern");
        for (int i = 1; i <= 8; i++) {
            int x = cx + 1 + i, sy = y + 8 + i;
            for (int z = -17; z <= -16; z++) {
                w.set(x, sy, z, "jungle_stairs[facing=east,half=bottom]");
                w.set(x, sy - 1, z, "jungle_planks");
                for (int dy = 1; dy <= 3; dy++) w.set(x, sy + dy, z, "air");
            }
            w.set(x, sy, -18, "jungle_planks");
            w.set(x, sy + 1, -18, i % 4 == 0 ? "stripped_jungle_log[axis=y]" : "jungle_fence");
        }
        for (int x = cx + 10; x <= cx + 13; x++) for (int z = -18; z <= -13; z++) {
            w.set(x, CANOPY, z, "jungle_planks");
            for (int dy = 1; dy <= 3; dy++) w.set(x, CANOPY + dy, z, "air");
            if (x == cx + 13 || z == -18) w.set(x, CANOPY + 1, z, "jungle_fence");
        }
        // posts down to the platform under the landings
        for (int[] p : new int[][] {{cx - 2, -17}, {cx + 1, -17}, {cx + 12, -18}}) {
            int top = p[0] > cx + 5 ? CANOPY - 1 : y + 7;
            for (int yy = y + 1; yy <= top; yy++) if (w.air(p[0], yy, p[1])) w.set(p[0], yy, p[1], "stripped_jungle_log[axis=y]");
        }
    }

    /** The canopy deck: a ring of boards round the trunk high in the crown, a rail round it. */
    void canopyDeck(int cx) {
        int y = CANOPY;
        for (int dx = -19; dx <= 19; dx++) for (int dz = -19; dz <= 19; dz++) {
            int x = cx + dx, z = dz;
            double d = Math.hypot(dx, dz), a = Math.atan2(dz, dx);
            double inner = greatR(y) * flute(a, y, 0.3);
            if (d <= inner - 0.3 || d > 17.6) continue;
            if (w.type(x, y, z).name().endsWith("STAIRS")) continue;
            w.set(x, y, z, ((int) Math.floor(d) & 1) == 0 ? "jungle_planks" : "stripped_jungle_wood");
            for (int dy = 1; dy <= 3; dy++) if (!w.air(x, y + dy, z) && d > inner + 0.5) w.set(x, y + dy, z, "air");
            if (d > 16.6) {
                boolean gap = dx >= 8 && dx <= 12 && dz < -10 || Math.abs(a - Math.toRadians(-38)) < 0.12 || Math.abs(a - Math.toRadians(38)) < 0.12;
                if (!gap) w.set(x, y + 1, z, Math.floorMod((int) Math.round(Math.toDegrees(a)), 20) < 3 ? "stripped_jungle_log[axis=y]" : "jungle_fence");
            }
        }
        // its beams: boughs out from the trunk
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4 + 0.39;
            tube(new double[][] {{cx + Math.cos(a) * 9, y - 4, Math.sin(a) * 9}, {cx + Math.cos(a) * 17, y - 1.6, Math.sin(a) * 17}}, 1.8, 1.1, "jungle_wood");
        }
    }

    /**
     * A crown: boughs out from the top of the trunk to a ring of leaf masses and one over the middle, flowering
     * azalea through it, shroomlights in its depths, vines hanging from its edge.
     */
    void crown(int cx, int cz, int y0, double r, int n, String leaf) {
        for (int k = 0; k < n; k++) {
            double a = k * 2 * Math.PI / n + w.r.nextDouble() * 0.4, rr = r * (0.45 + w.r.nextDouble() * 0.45);
            double bx = cx + Math.cos(a) * rr, bz = cz + Math.sin(a) * rr, by = y0 + 2 + w.r.nextDouble() * (r * 0.4);
            tube(new double[][] {{cx + Math.cos(a) * 2, y0 - 4, cz + Math.sin(a) * 2}, {(cx + bx) / 2, by - 2, (cz + bz) / 2}, {bx, by, bz}}, r > 15 ? 1.9 : 1.2, 0.7, "jungle_wood");
            double lr = r * (0.3 + w.r.nextDouble() * 0.12);
            leaves(bx, by + 1, bz, lr, 0.62, w.r.nextDouble() < 0.25 ? "flowering_azalea_leaves[persistent=true]" : leaf);
            if (w.r.nextDouble() < 0.6) w.set((int) Math.floor(bx), (int) Math.floor(by), (int) Math.floor(bz), "shroomlight");
        }
        leaves(cx, y0 + r * 0.45, cz, r * 0.45, 0.6, leaf);
        // vines hanging down the outside of the crown
        for (int i = 0; i < n * 10; i++) {
            double a = w.r.nextDouble() * 2 * Math.PI;
            for (double rr = r * 1.3; rr > 2; rr -= 0.5) {
                int x = (int) Math.round(cx + Math.cos(a) * rr), z = (int) Math.round(cz + Math.sin(a) * rr);
                int yy = y0 + (int) (w.r.nextDouble() * r * 0.5);
                int ix = (int) Math.round(cx + Math.cos(a) * (rr - 1)), iz = (int) Math.round(cz + Math.sin(a) * (rr - 1));
                if (!w.air(x, yy, z) || (ix == x && iz == z) || !w.type(ix, yy, iz).name().endsWith("LEAVES")) continue;
                String face = ix > x ? "east" : ix < x ? "west" : iz > z ? "south" : "north";
                if (ix != x && iz != z) break;
                int len = 3 + w.r.nextInt(6);
                for (int k = 0; k < len && w.air(x, yy - k, z); k++) w.set(x, yy - k, z, "vine[" + face + "=true]");
                break;
            }
        }
    }

    // ================================================================ the flank trees

    /**
     * A flank tree: a smaller trunk standing on nothing, hanging roots below it, a deck at branch height with a
     * treehouse, a stair up its outside to a deck in its crown.
     */
    void flankTree(int cx, int cz, int s) {
        for (int y = 52; y <= 106; y++) {
            double r = y >= 66 ? 5.4 - 0.03 * (y - 66) : 5.4 * Math.sqrt(Math.max(0, 1 - Math.pow((66 - y) / 14.0, 2)));
            if (r < 0.6) continue;
            int R = (int) Math.ceil(r) + 1;
            for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) {
                double a = Math.atan2(dz, dx), d = Math.hypot(dx, dz);
                if (d > r * flute(a, y, cz * 0.1)) continue;
                w.set(cx + dx, y, cz + dz, d > r - 1.3 ? (w.n3((cx + dx) * 0.5, y * 0.4, (cz + dz) * 0.5, 3) > 0.55 ? "mangrove_wood" : "mangrove_log[axis=y]") : "stripped_mangrove_wood");
            }
        }
        // prop roots arching out and down, as a mangrove's
        for (int k = 0; k < 7; k++) {
            double a = k * 2 * Math.PI / 7 + 0.5;
            tube(new double[][] {{cx + Math.cos(a) * 4, 68, cz + Math.sin(a) * 4}, {cx + Math.cos(a) * 9, 63, cz + Math.sin(a) * 9},
                    {cx + Math.cos(a) * 11, 52 - k % 3 * 3, cz + Math.sin(a) * 11}}, 1.5, 0.6, "mangrove_wood");
        }
        for (int i = 0; i < 40; i++) {
            double a = w.r.nextDouble() * 2 * Math.PI, rr = 3 + w.r.nextDouble() * 8;
            int x = (int) Math.round(cx + Math.cos(a) * rr), z = (int) Math.round(cz + Math.sin(a) * rr);
            for (int yy = 70; yy > 44; yy--) {
                if (!w.air(x, yy, z) && w.air(x, yy - 1, z)) {
                    for (int k = 1; k <= 1 + w.r.nextInt(3) && w.air(x, yy - k, z); k++) w.set(x, yy - k, z, "mangrove_roots");
                    hangRoot(x, yy - 4, z);
                    break;
                }
            }
        }
        // the branch deck
        for (int dx = -11; dx <= 11; dx++) for (int dz = -11; dz <= 11; dz++) {
            double d = Math.hypot(dx, dz);
            int x = cx + dx, z = cz + dz;
            if (d > 10.6 || !w.air(x, BRANCH, z) && w.type(x, BRANCH, z).name().contains("MANGROVE")) continue;
            w.set(x, BRANCH, z, ((int) Math.floor(d) & 1) == 0 ? "mangrove_planks" : "stripped_mangrove_wood");
            for (int dy = 1; dy <= 4; dy++) if (!w.air(x, BRANCH + dy, z) && !w.type(x, BRANCH + dy, z).name().contains("MANGROVE")) w.set(x, BRANCH + dy, z, "air");
            if (d > 9.7) {
                double a = Math.toDegrees(Math.atan2(dz, dx));
                // gaps: toward the great tree's road, toward the temple's root bridge, at the stair
                boolean gap = near(a, s < 0 ? 125 : -125, 14) || near(a, s < 0 ? 35 : -35, 14) || dz * s > 5 && dx >= 3 && dx <= 6;
                if (!gap) w.set(x, BRANCH + 1, z, Math.floorMod((int) Math.round(a), 30) < 4 ? "stripped_mangrove_log[axis=y]" : "mangrove_fence");
            }
            if (((dx * 5 + dz * 3) & 7) == 0) hangRoot(x, BRANCH - 1, z);
        }
        for (int k = 0; k < 6; k++) {
            double a = k * Math.PI / 3 + 0.2;
            tube(new double[][] {{cx + Math.cos(a) * 4, BRANCH - 4, cz + Math.sin(a) * 4}, {cx + Math.cos(a) * 10, BRANCH - 1.4, cz + Math.sin(a) * 10}}, 1.6, 1.0, "mangrove_wood");
        }
        // lamps either side of the gaps in the rail, where crossings to the great tree and to the temple start
        for (double gapA : new double[] {s < 0 ? 125 : -125, s < 0 ? 35 : -35}) for (int side : new int[] {-1, 1}) {
            double a = Math.toRadians(gapA + side * 15);
            int x = cx + (int) Math.round(Math.cos(a) * 10.1), z = cz + (int) Math.round(Math.sin(a) * 10.1);
            if (w.type(x, BRANCH, z).isSolid()) {
                w.set(x, BRANCH + 1, z, "stripped_mangrove_log[axis=y]");
                w.set(x, BRANCH + 2, z, "lantern");
            }
        }
        // a treehouse on the deck, on the side away from the temple
        if (s < 0) hut(cx - 9, cz - 3, cx - 5, cz + 1, BRANCH, "east");
        else hut(cx - 9, cz - 1, cx - 5, cz + 3, BRANCH, "east");
        // the stair up the outside, on the side away from the middle
        int r1 = cz + s * 6, r2 = cz + s * 9;
        for (int i = 1; i <= 8; i++) {
            int x = cx + 5 - i, sy = BRANCH + i;
            for (int k = 0; k <= 1; k++) {
                int z = r1 + s * k;
                w.set(x, sy, z, "mangrove_stairs[facing=west,half=bottom]");
                w.set(x, sy - 1, z, "mangrove_planks");
                for (int dy = 1; dy <= 3; dy++) w.set(x, sy + dy, z, "air");
            }
        }
        for (int x = cx - 7; x <= cx - 4; x++) for (int k = 0; k <= 4; k++) {
            int z = r1 + s * k;
            w.set(x, BRANCH + 8, z, "mangrove_planks");
            for (int dy = 1; dy <= 3; dy++) w.set(x, BRANCH + 8 + dy, z, "air");
            if (x == cx - 7 || k == 4) w.set(x, BRANCH + 9, z, "mangrove_fence");
        }
        for (int i = 1; i <= 8; i++) {
            int x = cx - 4 + i, sy = BRANCH + 8 + i;
            for (int k = 0; k <= 1; k++) {
                int z = r2 + s * k;
                w.set(x, sy, z, "mangrove_stairs[facing=east,half=bottom]");
                w.set(x, sy - 1, z, "mangrove_planks");
                for (int dy = 1; dy <= 3; dy++) w.set(x, sy + dy, z, "air");
            }
            w.set(x, sy, r2 + s * 2, "mangrove_planks");
            w.set(x, sy + 1, r2 + s * 2, "mangrove_fence");
        }
        // the canopy deck
        for (int dx = -9; dx <= 9; dx++) for (int dz = -9; dz <= 9; dz++) {
            double d = Math.hypot(dx, dz);
            int x = cx + dx, z = cz + dz;
            boolean landing = dx >= 5 && dx <= 7 && dz * s >= 6 && dz * s <= 11;
            if (d > 8.6 && !landing || w.type(x, CANOPY, z).name().contains("LOG") || w.type(x, CANOPY, z).name().contains("STRIPPED")) continue;
            w.set(x, CANOPY, z, ((int) Math.floor(d) & 1) == 0 ? "mangrove_planks" : "stripped_mangrove_wood");
            for (int dy = 1; dy <= 3; dy++) if (!w.air(x, CANOPY + dy, z) && !w.type(x, CANOPY + dy, z).name().contains("MANGROVE_LOG")) w.set(x, CANOPY + dy, z, "air");
            if (d > 7.7 && !landing) {
                double a = Math.toDegrees(Math.atan2(dz, dx));
                boolean gap = near(a, s < 0 ? 120 : -120, 16) || near(a, s < 0 ? 40 : -40, 16);
                if (!gap) w.set(x, CANOPY + 1, z, "mangrove_fence");
            }
        }
        crown(cx, cz, 101, 14, 7, "mangrove_leaves[persistent=true]");
        keepClear(cx - 16, cz - 16, cx + 16, cz + 16);
    }

    static boolean near(double a, double b, double tol) {
        double d = Math.abs(((a - b) % 360 + 540) % 360 - 180);
        return d < tol;
    }

    // ================================================================ out in the gaps

    /**
     * The fungal rock halfway between the great tree's root deck and the temple's apron: shroomlights, mushrooms,
     * ferns, roots hanging off its underside. Nothing joins it to anything: you bridge out to it.
     */
    void fungalRock() {
        for (int x = ROCK_X - 8; x <= ROCK_X + 8; x++) for (int z = -8; z <= 8; z++) {
            int g = ground(x, z);
            if (g == Integer.MIN_VALUE) continue;
            double r = w.r.nextDouble();
            if (r < 0.08) w.set(x, g, z, "shroomlight");
            else if (r < 0.2 && w.air(x, g + 1, z)) {
                w.set(x, g, z, "podzol");
                w.set(x, g + 1, z, r < 0.14 ? "red_mushroom" : "brown_mushroom");
            } else if (r < 0.26 && w.air(x, g + 1, z) && dirt(x, g, z)) w.set(x, g + 1, z, "fern");
            if (w.r.nextDouble() < 0.3) hangRoot(x, bottomAt(x, z) - 1, z);
        }
        keepClear(ROCK_X - 3, -3, ROCK_X + 3, 3);
    }

    int bottomAt(int x, int z) {
        int g = ground(x, z);
        int y = g;
        while (y > 20 && !w.air(x, y - 1, z)) y--;
        return y;
    }

    /**
     * The shrines (hills) on their rocks north and south of the temple, as far from one team as the other: a paved
     * square with four mossy posts and lamps, an offering stone in the middle, the sky open over it.
     */
    void shrines() {
        for (int s : new int[] {-1, 1}) {
            int cz = s * SHRINE_Z, y = SHRINE;
            for (int x = -7; x <= 7; x++) for (int z = cz - 7; z <= cz + 7; z++) {
                if (!w.canon(x, z) || ground(x, z) != SHRINE) continue;
                double d = Math.hypot(x, z - cz);
                if (d < 1) c(x, y, z, "chiseled_stone_bricks");
                else if (d < 4.7) c(x, y, z, (((int) Math.floor(Math.toDegrees(Math.atan2(z - cz, x)) / 30) + (int) d) & 1) == 0 ? "stone_bricks" : "mossy_stone_bricks");
                else if (d < 5.5) c(x, y, z, "mossy_cobblestone");
                else if (w.r.nextDouble() < 0.35 && w.air(x, y + 1, z) && dirt(x, y, z)) c(x, y + 1, z, w.r.nextBoolean() ? "fern" : "short_grass");
                if (d >= 1 && d < 4.7 && w.r.nextDouble() < 0.12 && w.air(x, y + 1, z)) c(x, y + 1, z, "moss_carpet");
            }
            for (int[] p : new int[][] {{-3, -3}, {-3, 3}}) {
                int x = p[0], z = cz + p[1];
                for (int dy = 1; dy <= 3; dy++) c(x, y + dy, z, dy == 3 ? "chiseled_stone_bricks" : "mossy_stone_bricks");
                c(x, y + 4, z, "lantern");
                for (int k = 1; k <= 2; k++) if (w.air(x - 1, y + 3 - k, z)) c(x - 1, y + 3 - k, z, "vine[east=true]");
            }
            if (s < 0) {
                // the posts on the shrine's other side come from the south shrine's copy; the middle stone is shared
                c(0, y, cz, "chiseled_stone_bricks");
            }
            keepClear(-7, cz - 7, 7, cz + 7);
        }
    }

    /** A post with a lamp at a brink, marking where a crossing is worth building. */
    void lamp(int x, int y, int z) {
        if (!w.type(x, y, z).isSolid()) return;
        w.set(x, y + 1, z, "stripped_jungle_log[axis=y]");
        w.set(x, y + 2, z, "lantern");
    }

    // ================================================================ the Sky Temple

    /**
     * The Sky Temple: a stepped pyramid of mossy stone on its lump of earth: the apron, two terraces and the summit,
     * five high and five wide a step, so its grand stairs (west and east) rise straight from the apron to the summit
     * and meet each terrace level with it. The summit (the hill) has a carved stela at each corner and a gold sun in
     * the middle of its paving.
     */
    void temple() {
        int[][] tiers = {{A_H, APRON}, {T1_H, T1}, {T2_H, T2}, {S_H, SUMMIT}};
        for (int x = -A_H - 1; x <= 0; x++) for (int z = -A_H - 1; z <= A_H + 1; z++) {
            if (!w.canon(x, z)) continue;
            int m = Math.max(Math.abs(x), Math.abs(z));
            int top = Integer.MIN_VALUE, tier = -1;
            for (int i = 0; i < tiers.length; i++) if (m <= tiers[i][0]) { top = tiers[i][1]; tier = i; }
            if (top == Integer.MIN_VALUE) continue;
            int g = ground(x, z);
            int from = g == Integer.MIN_VALUE ? APRON - 4 : Math.min(g, APRON - 1);
            for (int y = from; y < top; y++) w.set(x, y, z, stone(x, y, z, false));
            boolean rim = m == tiers[tier][0];
            w.set(x, top, z, paving(x, z, tier, rim));
            for (int dy = 1; dy <= 4; dy++) if (!w.air(x, top + dy, z)) w.set(x, top + dy, z, "air");
            // a carved band round the top of each tier's face
            if (tier > 0 && rim) w.set(x, top - 1, z, ((x + z) & 1) == 0 ? "chiseled_stone_bricks" : "mossy_stone_bricks");
        }
        // the grand stair up the west face (its copy climbs the east face)
        for (int i = 0; i <= SUMMIT - APRON - 1; i++) {
            int x = -19 + i, sy = APRON + 1 + i;
            for (int z = -2; z <= 2; z++) {
                for (int y = APRON; y < sy; y++) w.set(x, y, z, stone(x, y, z, false));
                w.set(x, sy, z, (i & 3) == 3 ? "mossy_stone_brick_stairs[facing=east,half=bottom]" : "stone_brick_stairs[facing=east,half=bottom]");
                for (int dy = 1; dy <= 3; dy++) w.set(x, sy + dy, z, "air");
            }
            // the balustrades: low walls of carved stone either side, stepping up with the stair
            for (int z : new int[] {-3, 3}) {
                for (int y = APRON; y <= sy; y++) w.set(x, y, z, stone(x, y, z, true));
                boolean terrace = sy == T1 || sy == T2 || sy == SUMMIT;
                if (!terrace) w.set(x, sy + 1, z, (i % 5 == 0) ? "chiseled_stone_bricks" : "mossy_stone_brick_wall");
                else for (int dy = 1; dy <= 3; dy++) w.set(x, sy + dy, z, "air");
            }
        }
        // the summit: stelae at the corners, a gold sun, braziers
        for (int[] p : new int[][] {{-S_H, -S_H}, {-S_H, S_H}}) {
            int x = p[0], z = p[1];
            for (int dy = 1; dy <= 4; dy++) w.set(x, SUMMIT + dy, z, dy == 4 ? "chiseled_tuff_bricks" : dy == 2 ? "chiseled_stone_bricks" : "mossy_stone_bricks");
            w.set(x, SUMMIT + 5, z, "lantern");
        }
        for (int x = -2; x <= 0; x++) for (int z = -2; z <= 2; z++) {
            if (!w.canon(x, z)) continue;
            double d = Math.hypot(x, z);
            if (d < 2.6) w.set(x, SUMMIT, z, d < 1 ? "gold_block" : (((x + z) & 1) == 0 ? "waxed_cut_copper" : "chiseled_stone_bricks"));
        }
        // the terraces: braziers on the corners, ferns and moss in the cracks, vines down the faces
        for (int[] p : new int[][] {{-T1_H, -T1_H}, {-T1_H, T1_H}, {-T2_H, -T2_H}, {-T2_H, T2_H}, {-A_H, -A_H}, {-A_H, A_H}}) {
            int top = p[0] == -T1_H ? T1 : p[0] == -T2_H ? T2 : APRON;
            w.set(p[0], top + 1, p[1], "mossy_stone_brick_wall");
            w.set(p[0], top + 2, p[1], "campfire[lit=true,signal_fire=false]");
        }
        for (int x = -A_H; x <= 0; x++) for (int z = -A_H; z <= A_H; z++) {
            if (!w.canon(x, z)) continue;
            int m = Math.max(Math.abs(x), Math.abs(z));
            int top = m <= S_H ? SUMMIT : m <= T2_H ? T2 : m <= T1_H ? T1 : APRON;
            if (Math.abs(z) <= 3 && x <= -S_H) continue;
            double r = w.r.nextDouble();
            if (w.air(x, top + 1, z) && top < SUMMIT) {
                if (r < 0.05 && dirt(x, top, z)) w.set(x, top + 1, z, "fern");
                else if (r < 0.08) w.set(x, top + 1, z, "moss_carpet");
            }
            // vines down the face of the tier above
            int upper = m == T1_H + 1 ? T1 : m == T2_H + 1 ? T2 : m == S_H + 1 ? SUMMIT : 0;
            if (upper > 0 && r > 0.7) vine(x, upper - 1, z, 2 + w.r.nextInt(3));
        }
        keepClear(-A_H - 2, -A_H - 2, A_H + 2, A_H + 2);
    }

    String stone(int x, int y, int z, boolean fine) {
        double n = w.n3(x * 1.9, y * 1.9, z * 1.9, 3);
        if (fine) return n > 0.3 ? "mossy_stone_bricks" : "stone_bricks";
        return n > 0.45 ? "mossy_stone_bricks" : n > 0.2 ? "cracked_stone_bricks" : n < -0.4 ? "mossy_cobblestone" : n < -0.15 ? "andesite" : "stone_bricks";
    }

    String paving(int x, int z, int tier, boolean rim) {
        double n = w.n2(x * 2.2, z * 2.2, 3);
        if (rim) return tier == 0 ? "mossy_stone_bricks" : "chiseled_stone_bricks";
        if (tier == 3) return ((x + z) & 1) == 0 ? "polished_andesite" : "stone_bricks";
        return n > 0.4 ? "moss_block" : n > 0.15 ? "mossy_stone_bricks" : n < -0.4 ? "cracked_stone_bricks" : "stone_bricks";
    }

    /**
     * A strangler fig growing out of the temple's south-west corner (its copy the north-east): a trunk of braided
     * roots, roots spilling down the tiers and off the apron's edge, a broad crown over that corner.
     */
    void fig(int cx, int cz) {
        for (int k = 0; k < 5; k++) {
            double a = k * 2 * Math.PI / 5;
            double[][] pts = {{cx + Math.cos(a) * 2.2, APRON + 1, cz + Math.sin(a) * 2.2}, {cx + Math.cos(a + 0.8) * 1.6, 80, cz + Math.sin(a + 0.8) * 1.6},
                    {cx + Math.cos(a + 1.6) * 2.0, 92, cz + Math.sin(a + 1.6) * 2.0}};
            tube(pts, 1.4, 1.0, "jungle_wood");
        }
        // roots over the tiers toward the middle and off the edge into the void
        for (int k = 0; k < 7; k++) {
            double a = Math.toRadians(-60 + k * 30) + Math.PI * (k < 3 ? 0 : 0);
            double dx = Math.cos(a), dz = Math.sin(a);
            boolean inward = dx > 0 && dz < 0;
            double[][] pts = inward ? new double[][] {{cx, APRON + 3, cz}, {cx + dx * 5, APRON + 1.5, cz + dz * 5}, {cx + dx * 8, T1 + 0.6, cz + dz * 8}}
                    : new double[][] {{cx, APRON + 3, cz}, {cx - dx * 4, APRON + 1, cz + Math.abs(dz) * 4}, {cx - dx * 6, APRON - 3, cz + Math.abs(dz) * 7}, {cx - dx * 7, APRON - 14, cz + Math.abs(dz) * 9}};
            tube(pts, 0.9, 0.5, "jungle_wood");
        }
        crown(cx, cz, 94, 12, 6, "jungle_leaves[persistent=true]");
    }

    /** The fungal rock's and mossy rocks' dressing: ferns, a small tree on the bigger rocks, roots hanging below. */
    void lumpDressing() {
        for (int i = 4; i < LUMPS.length; i++) {
            double[] l = LUMPS[i];
            int x = (int) l[0], z = (int) l[1], g = ground(x, z);
            if (g == Integer.MIN_VALUE) continue;
            if (l[2] > 4.3) flora.tree(Theme.Tree.AZALEA, x, g + 1, z);
            for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) {
                int gx = x + dx, gz = z + dz, gg = ground(gx, gz);
                if (gg == Integer.MIN_VALUE) continue;
                if (w.r.nextDouble() < 0.25 && w.air(gx, gg + 1, gz) && dirt(gx, gg, gz)) w.set(gx, gg + 1, gz, w.r.nextBoolean() ? "fern" : "short_grass");
                if (w.r.nextDouble() < 0.35) hangRoot(gx, bottomAt(gx, gz) - 1, gz);
            }
        }
    }
}
