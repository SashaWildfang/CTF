package com.sasha.ctfbuilder.v2.maps;

import com.sasha.ctfbuilder.v2.Arena;
import com.sasha.ctfbuilder.v2.Kit;
import com.sasha.ctfbuilder.v2.Land;
import com.sasha.ctfbuilder.v2.Theme;
import com.sasha.ctfbuilder.v2.W.P;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.type.Snow;

import java.util.Set;

/**
 * Frostfall: two Viking villages on headland islands either side of the Ice Wall, for two teams. Nothing joins the
 * islands: players build every crossing, and the gaps are wide.
 *
 * The Ice Wall is a glacier standing across the middle of the map, so the villages can't see or walk straight to
 * each other. Two tunnels run through it at the floes' level, each opening halfway into a chamber where a stair
 * winds up round a pillar of blue ice to a hatch on top; up there the old longship lies frozen across the wall (a
 * hill), its prow and stern jutting out over the void on either face. Hold the tunnels, climb, bridge up and over,
 * or go round.
 *
 * Round the ends of the wall the two flanks play differently. On one side the floes: a snow floe with a boat
 * turned over on it and a long sled run of blue ice, very fast underfoot and wide open. On the other the skerries:
 * rock islands at different heights thick with pines, a hunter's lodge on one, a stream freezing over the edge of
 * another in a curtain of ice, a lookout on the last. Beyond each end of the wall a pass with a ring of rune stones
 * is a hill.
 *
 * Each village sits on its headland inside a palisade: a great longhouse with a steep snowy roof and carved
 * dragon heads where the team spawns, houses, a smithy, a storehouse on stilts, a well, a gatehouse over a stone
 * stairway down to a jetty over the void, watchtowers at the corners and posterns to the flanks. In front of it a
 * fishing floe with huts, ice spikes and a pressure ridge.
 */
public final class Frostfall extends Arena {

    static final int ICE = 64, HOME = 72, WALL = 90, PASS = 66;
    static final int PAL_W = -108, PAL_E = -68, PAL_Z = 27, POSTERN_X = -90, PASS_Z = 62, TUNNEL_Z = 12;

    Land land;

    public Frostfall(World world) {
        super(world, "frostfall", false, Theme.get("frostfall"));
        name = "Frostfall";
        icon = "PACKED_ICE";
        minutes = 18;
        kothPoints = 250;
        reach = 124;
        low = 15;
        high = 140;
        voidY = 15;
        spectator = new double[] {0.5, 110, 0.5, -90, 40};
    }

    // ================================================================ the islands

    /** Noise that's the same at (x, z) and (-x, -z), so the two halves meet without a seam. */
    double rn(double x, double z, double scale, double off) {
        return (w.fbm(x + off, z - off, scale, 2) + w.fbm(-x + off, -z - off, scale, 2)) * 0.7;
    }

    /** An island: an oval (or a squarer superellipse, sq > 2) turned by ang; the top block of its ground; ice or rock. */
    record Isle(String name, double x, double z, double rx, double rz, double ang, double sq, int top, boolean ice) {
        double size() {
            return Math.min(rx, rz);
        }
    }

    // the west half (the east half is the same turned about the middle), and the two passes between them
    static final Isle HOME_ISLE = new Isle("headland", -90, 0, 25.5, 37, 0, 2.6, HOME, false);
    static final Isle FLOE = new Isle("fishing floe", -33, 0, 8, 17, 0, 2, ICE, true);
    static final Isle F1 = new Isle("snow floe", -64, -48, 8, 6, 0.35, 2, ICE - 1, true);
    static final Isle SLED = new Isle("sled run", -34, -60, 14, 3.5, -0.05, 2, ICE, true);
    static final Isle S1 = new Isle("lodge skerry", -67, 50, 9.5, 7.5, -0.2, 2, 70, false);
    static final Isle S2 = new Isle("pine stack", -44, 62, 6, 6, 0, 2, 77, false);
    static final Isle S3 = new Isle("lookout skerry", -24, 58, 4.5, 4.5, 0, 2, 71, false);
    static final Isle PASS_N = new Isle("north pass", 0, -62, 9.5, 8.5, 0, 2, PASS, false);
    static final Isle PASS_S = new Isle("south pass", 0, 62, 9.5, 8.5, 0, 2, PASS, false);
    static final Isle[] ISLES = {HOME_ISLE, FLOE, F1, SLED, S1, S2, S3, PASS_N, PASS_S};

    double inside(Isle i, int x, int z) {
        double dx = x - i.x(), dz = z - i.z(), c = Math.cos(i.ang()), s = Math.sin(i.ang());
        double u = Math.abs((dx * c + dz * s) / i.rx()), v = Math.abs((-dx * s + dz * c) / i.rz());
        return 1 - Math.pow(Math.pow(u, i.sq()) + Math.pow(v, i.sq()), 1 / i.sq()) + rn(x, z, 6, i.top() * 3) * 0.06 + rn(x, z, 2.2, 7) * 0.025;
    }

    /** The island at (x, z), or null (the wall, its ledges and the headland's tongue are separate tests). */
    Isle isle(int x, int z) {
        if (headland(x, z)) return HOME_ISLE;
        if (x > 0) {
            x = -x;
            z = -z;
        }
        Isle best = null;
        double bv = 0;
        for (Isle i : ISLES) {
            double v = inside(i, x, z);
            if (v > bv) {
                bv = v;
                best = i;
            }
        }
        return best;
    }

    static double rr(double x, double z, double x0, double x1, double z0, double z1, double r) {
        double tx = Math.max(0, Math.max(x0 + r - x, x - (x1 - r))), tz = Math.max(0, Math.max(z0 + r - z, z - (z1 - r)));
        return Math.hypot(tx, tz) - r;
    }

    /** The Ice Wall: a glacier standing across the middle of the map, 27 wide and 85 long, sheer on every side. */
    boolean wall(int x, int z) {
        return rr(x, z, -13, 13, -42, 42, 6) <= rn(x, z, 3, 41) * 0.35;
    }

    /** The ledges under the tunnel mouths, sticking out of the wall's faces at the floes' level. */
    boolean ledge(int x, int z) {
        if (x > 0) {
            x = -x;
            z = -z;
        }
        return rr(x, z, -16, -12, -TUNNEL_Z - 3.5, -TUNNEL_Z + 3.5, 1) <= 0 || rr(x, z, -16, -12, TUNNEL_Z - 3.5, TUNNEL_Z + 3.5, 1) <= 0;
    }

    /** The headland under the whole palisade and its corner towers. */
    boolean headland(int x, int z) {
        if (x > 0) {
            x = -x;
            z = -z;
        }
        return rr(x, z, -113, -63, -31, 31, 5) <= rn(x, z, 4, 13) * 0.6;
    }

    /** The headland's tongue under the stairway, where the jetty goes out. */
    boolean tongue(int x, int z) {
        if (x > 0) {
            x = -x;
            z = -z;
        }
        return rr(x, z, -67, -55, -4.5, 4.5, 2) <= 0;
    }

    double height(int x, int z) {
        if (wall(x, z)) return WALL;
        if (ledge(x, z)) return ICE;
        if (headland(x, z)) return HOME;
        Isle i = isle(x, z);
        if (i == S2 || i == S3) {
            // the pine skerries rise to a rounded crown
            int cx = x > 0 ? -x : x, cz = x > 0 ? -z : z;
            double e = Math.max(0, inside(i, cx, cz));
            return i.top() + Math.floor(Math.min(1, e * 1.6) * (i == S2 ? 2.2 : 1.6));
        }
        if (i != null) return i.top();
        return tongue(x, z) ? ICE : Double.NaN;
    }

    String surface(int x, int z, int y, int steep, double edge) {
        double fine = w.n2(x * 1.9, z * 1.9, 2);
        if (wall(x, z)) {
            if (steep >= 2) return iceAt(x, y, z);
            return w.fbm(x * 0.22 + 7, z * 1.1, 6, 2) > 0.5 ? "packed_ice" : "snow_block";
        }
        Isle i = isle(x, z);
        if (ledge(x, z) || i != null && i.ice()) return "packed_ice";
        if (i == null) return fine > 0.3 ? "gravel" : "snow_block";
        if (steep >= 3) return rock(x, y, z);
        if (i == S1 || i == S2 || i == S3) {
            double n = w.fbm(x * 1.1 + 70, z * 1.1, 6, 2);
            return n > 0.38 ? "podzol" : n > 0.3 ? "coarse_dirt" : "snow_block";
        }
        return "snow_block";
    }

    static final String[] ICE_BANDS = {"packed_ice", "packed_ice", "blue_ice", "packed_ice", "snow_block", "packed_ice", "packed_ice",
            "blue_ice", "blue_ice", "packed_ice", "snow_block", "snow_block"};

    /** Glacier ice: bands of packed ice, deep blue ice and old snow, waving a little. */
    String iceAt(int x, int y, int z) {
        double warp = w.fbm(x * 0.35 + 3, z * 0.35, 10, 2) * 1.8;
        double t = w.n3(x * 1.7, y * 1.7, z * 1.7, 3);
        if (t > 0.62) return "blue_ice";
        if (t < -0.66) return "snow_block";
        return ICE_BANDS[Math.floorMod((int) Math.floor(y + warp), ICE_BANDS.length)];
    }

    static final String[] LAYERS = {"stone", "andesite", "stone", "tuff", "diorite", "stone", "cobbled_deepslate", "packed_ice", "deepslate", "andesite", "stone"};
    static final int[] THICK = {3, 2, 2, 1, 1, 2, 2, 1, 2, 2, 2};
    static final int CYCLE = 20;

    /** The body of an island: ice for the wall and the floes, banded rock for the rest. */
    String rock(int x, int y, int z) {
        Isle i = isle(x, z);
        if (wall(x, z) || ledge(x, z) || i != null && i.ice()) return iceAt(x, y, z);
        double warp = w.fbm(x * 0.5 + 11, z * 0.5 - 7, 28, 2) * 2.0;
        int at = Math.floorMod((int) Math.floor(y + warp), CYCLE);
        int k = 0;
        while (at >= THICK[k]) at -= THICK[k++];
        double tex = w.n3(x * 1.9, y * 1.9, z * 1.9, 4);
        if (tex > 0.62) k = (k + 1) % LAYERS.length;
        else if (tex < -0.64) k = (k + LAYERS.length - 1) % LAYERS.length;
        return LAYERS[k];
    }

    /**
     * The undersides, tapering from the rim: the wall a huge iceberg going far down, the floes ragged ice, the pine
     * stack a tall column of rock, the rest rounded rock.
     */
    int bottom(int x, int z, int top, double edge) {
        double size, depth, jag = 0;
        Isle i = isle(x, z);
        if (wall(x, z) || ledge(x, z)) {
            size = 13;
            depth = 44;
            jag = 3;
        } else if (i == null) {
            size = 4;
            depth = 5;
        } else if (i.ice()) {
            size = i.size();
            depth = 5 + size * 1.6;
            jag = 3.5;
        } else if (i == HOME_ISLE) {
            size = 25;
            depth = 38;
        } else if (i == S2) {
            size = 6;
            depth = 26;
        } else {
            size = i.size();
            depth = 6 + size * 1.7;
        }
        double e = Math.min(1, edge / (size * 0.9)), lens = 1 - Math.pow(1 - e, 1.8);
        double teeth = jag * Math.max(0, rn(x, z, 1.6, 9)) * Math.min(1, edge / 3.0);
        return (int) Math.round(top - 2 - depth * lens - teeth + rn(x, z, 5, 5) * 1.6 * Math.min(1, edge / 5.0));
    }

    // ================================================================ build

    @Override
    protected void build() {
        land = new Land(w, t, -124, 124, -74, 74);
        land.top = this::height;
        land.surface = this::surface;
        land.rock = this::rock;
        land.bottom = this::bottom;
        land.soil = P.of("snow_block");
        land.build();
        floeIce();

        village();
        gateway();
        homeTrails();

        // the Ice Wall: two tunnels through it, a stair up a shaft from each, the longship on top
        tunnels();
        shaft(-TUNNEL_Z, Math.PI);
        shaft(TUNNEL_Z, 0);
        wallTop();
        longship();

        // the fishing floe in front of the headland
        hut(-36, -9, "south");
        hut(-30, 9, "north");
        ridge(-36, -1, -31, 3);
        spike(-28, -13, ICE, 9, 1.8);
        spike(-27, -10, ICE, 5, 1.2);
        spike(-38, 13, ICE, 7, 1.5);
        berg(-35, -1, 1.8, 2);

        // the floe side: the snow floe and the sled run
        overturnedBoat(-64, -48);
        sledRun();

        // the pine side: the skerries
        lodge(-67, 51);
        frozenFalls();
        lookout();
        // a trail along the flank over each skerry, kept clear of pines
        trail(new double[][] {{-67, 43}, {-67, 46}});
        trail(new double[][] {{-63, 46}, {-60, 50}, {-58, 53}});
        trail(new double[][] {{-50, 63}, {-45, 62}, {-39, 61}});
        trail(new double[][] {{-28, 58}, {-24, 58}, {-20, 57}});
        trees(new Theme.Tree[] {Theme.Tree.SPRUCE}, new int[] {1}, 0.22, 4, 1, (x, z) -> {
            Isle i = isle(x, z);
            return (i == S1 || i == S2 || i == S3) && inside(i, x, z) > 0.12;
        });
        youngPines(0.08, (x, z) -> {
            Isle i = isle(x, z);
            return (i == S1 || i == S2 || i == S3) && inside(i, x, z) > 0.08 && !isClear(x, z, 1);
        });

        // the passes, and pines on the headland behind and beside the village
        runeRing(-PASS_Z);
        runeRing(PASS_Z);
        trees(new Theme.Tree[] {Theme.Tree.SPRUCE}, new int[] {1}, 0.04, 6, 2,
                (x, z) -> isle(x, z) == HOME_ISLE && (x < PAL_W - 2 || Math.abs(z) > PAL_Z + 3) && inside(HOME_ISLE, x, z) > 0.12);
        youngPines(0.03, (x, z) -> isle(x, z) == HOME_ISLE && (x < PAL_W - 2 || Math.abs(z) > PAL_Z + 3) && inside(HOME_ISLE, x, z) > 0.08);
        scatter();
        snowCover();
        thaw();

        spawn = new double[] {-90.5, HOME + 1, 0.5, -90};
        flag = new int[] {-75, HOME + 1, 0};
        pads.add(new int[] {-76, HOME + 1, -13});
        pads.add(new int[] {-76, HOME + 1, 13});
        hills.add(new Hill("North Pass", -3, PASS + 1, -PASS_Z - 3, 3, PASS + 4, -PASS_Z + 3));
        hills.add(new Hill("Longship", -4, WALL + 3, -2, 4, WALL + 6, 2));
        hills.add(new Hill("South Pass", -3, PASS + 1, PASS_Z - 3, 3, PASS + 4, PASS_Z + 3));
    }

    // ================================================================ roofs and carvings

    /**
     * A steep Norse roof over walls x0..x1 × z0..z1, one block up for each block in, the eaves a block out and a
     * block down. Dark wood along the eaves and the verges, snow (smooth quartz) above a ragged snowline, a snow
     * ridge, and a spruce ceiling under it so it's wood seen from inside. The gable walls are filled with
     * {@code gable} if given. y is the first layer above the walls. Returns the ridge's y.
     */
    int norse(int x0, int z0, int x1, int z1, int y, boolean alongX, P gable) {
        int ex0 = x0 - 1, ex1 = x1 + 1, ez0 = z0 - 1, ez1 = z1 + 1;
        int span = alongX ? ez1 - ez0 : ex1 - ex0;
        for (int x = ex0; x <= ex1; x++) for (int z = ez0; z <= ez1; z++) {
            int a = alongX ? z - ez0 : x - ex0, b = span - a;
            int s = Math.min(a, b), level = y - 1 + s;
            boolean verge = alongX ? x == ex0 || x == ex1 : z == ez0 || z == ez1;
            boolean onGable = alongX ? (x == x0 || x == x1) && z >= z0 && z <= z1 : (z == z0 || z == z1) && x >= x0 && x <= x1;
            boolean inside = x > x0 && x < x1 && z > z0 && z < z1;
            String up = alongX ? (a < b ? "south" : "north") : (a < b ? "east" : "west");
            if (a == b) {
                w.set(x, level, z, verge ? "dark_oak_planks" : "snow_block");
                if (!verge && w.r.nextInt(3) > 0) w.set(x, level + 1, z, "snow[layers=" + (1 + w.r.nextInt(2)) + "]");
                if (inside) w.set(x, level - 1, z, "stripped_spruce_log[axis=" + (alongX ? "x" : "z") + "]");
            } else {
                boolean snowy = !verge && s >= 2 + (w.n2(x * 1.7, z * 1.7, 2) > 0.35 ? 1 : 0);
                w.set(x, level, z, (snowy ? "smooth_quartz_stairs" : "dark_oak_stairs") + "[facing=" + up + ",half=bottom]");
                if (inside && s >= 2) w.set(x, level - 1, z, "spruce_stairs[facing=" + Kit.opp(up) + ",half=top]");
            }
            if (onGable && gable != null) for (int yy = y; yy < level; yy++) w.set(x, yy, z, gable.pick(w.r));
        }
        return y - 1 + span / 2;
    }

    /** A hipped roof in the same dark wood, turned to snow above its lowest two rows. */
    int hipRoof(int x0, int z0, int x1, int z1, int y) {
        int top = k.hip(x0, z0, x1, z1, y, 1, Kit.Roof.of("dark_oak_planks"), null);
        for (int x = x0 - 1; x <= x1 + 1; x++) for (int z = z0 - 1; z <= z1 + 1; z++) for (int yy = y + 1; yy <= y + 12; yy++) {
            Material m = w.type(x, yy, z);
            if (m == Material.DARK_OAK_STAIRS) w.set(x, yy, z, w.at(x, yy, z).getBlockData().getAsString().replace("dark_oak_stairs", "smooth_quartz_stairs"));
            else if (m == Material.DARK_OAK_PLANKS) w.set(x, yy, z, "snow_block");
            else if (m == Material.DARK_OAK_SLAB) w.set(x, yy, z, "smooth_quartz_slab[type=bottom]");
        }
        return top;
    }

    /** A carved dragon's head on a gable: a neck up from the ridge, the head looking out, jaws open. */
    void dragonHead(int x, int y, int z, String out) {
        int ox = Kit.dx(out), oz = Kit.dz(out);
        w.set(x, y, z, "dark_oak_planks");
        w.set(x, y + 1, z, "dark_oak_fence");
        w.set(x, y + 2, z, "dark_oak_planks");
        w.set(x + ox, y + 2, z + oz, "dark_oak_stairs[facing=" + Kit.opp(out) + ",half=top]");
        w.set(x + ox, y + 1, z + oz, "dark_oak_slab[type=bottom]");
        w.set(x, y + 3, z, "dark_oak_trapdoor[facing=" + out + ",half=bottom,open=false]");
        w.set(x - ox, y + 2, z - oz, "dark_oak_stairs[facing=" + out + ",half=top]");
    }

    // ================================================================ the village

    void village() {
        int y = HOME;
        level(PAL_W + 1, -PAL_Z + 1, PAL_E - 1, PAL_Z - 1, y, P.of("snow_block"), P.of("snow_block"));
        keepClear(PAL_W - 3, -PAL_Z - 3, PAL_E + 3, PAL_Z + 3);
        square();
        path(-81, -2, -81, 2, 0);
        for (int s : new int[] {-1, 1}) {
            path(-81, s * 9, -90, s * 12, 1);
            path(POSTERN_X, s * 12, POSTERN_X, s * (PAL_Z - 1), 1);
            path(-90, s * 12, -100, s * 11, 1);
        }
        longhouse();
        house(-105, -21, -95, -13, "south");
        house(-105, 13, -95, 21, "north");
        smithy(-84, -23, -77, -17);
        storehouse(-84, 17, -78, 23);
        well(-87, -14);
        palisade();
        gatehouse();
        cornerTower(-PAL_Z);
        cornerTower(PAL_Z);
        // firewood along the north palisade, fish drying along the south
        for (int x = -104; x <= -99; x++) for (int dy = 1; dy <= 2; dy++) {
            w.set(x, y + dy, -25, "spruce_log[axis=x]");
            if (dy == 1 || x > -104 && x < -99) w.set(x, y + dy, -24, "spruce_log[axis=x]");
        }
        for (int x = -103; x <= -100; x++) w.set(x, y + 3, -25, "spruce_log[axis=x]");
        for (int r = 0; r < 2; r++) {
            int x0 = -104 + r * 5;
            for (int dy = 1; dy <= 3; dy++) {
                w.set(x0, y + dy, 24, "spruce_fence");
                w.set(x0 + 3, y + dy, 24, "spruce_fence");
            }
            for (int x = x0; x <= x0 + 3; x++) w.set(x, y + 4, 24, "stripped_spruce_log[axis=x]");
            for (int x = x0 + 1; x <= x0 + 2; x++) {
                w.set(x, y + 3, 24, "dark_oak_fence");
                w.set(x, y + 2, 24, "dried_kelp_block");
            }
        }
        // barrels, a cart and a sled about the place
        for (int[] b : new int[][] {{-83, -8}, {-82, -8}, {-83, 8}, {-94, -11}, {-94, 11}, {-93, 11}}) w.set(b[0], y + 1, b[1], "barrel[facing=up]");
        w.set(-82, y + 2, -8, "barrel[facing=up]");
        sled(-88, 22);
        cart(-80, -12);
    }

    /** Stone along a path through the snow, ragged at its edges. */
    void path(int ax, int az, int bx, int bz, int half) {
        int steps = Math.max(Math.abs(bx - ax), Math.abs(bz - az));
        for (int i = 0; i <= steps; i++) {
            int x = ax + (int) Math.round((bx - ax) * (double) i / Math.max(1, steps));
            int z = az + (int) Math.round((bz - az) * (double) i / Math.max(1, steps));
            for (int ox = -half; ox <= half; ox++) for (int oz = -half; oz <= half; oz++) {
                int px = x + ox, pz = z + oz;
                int g = land.topAt(px, pz);
                if (g < ICE || !w.air(px, g + 1, pz)) continue;
                Material m = w.type(px, g, pz);
                if (m != Material.SNOW_BLOCK && m != Material.GRAVEL && m != Material.COBBLESTONE && m != Material.MOSSY_COBBLESTONE) continue;
                double n = w.n2(px * 2.3, pz * 2.3, 2);
                if (Math.abs(ox) + Math.abs(oz) >= 2 && n > 0.2) continue;
                w.set(px, g, pz, n > 0.45 ? "mossy_cobblestone" : n < -0.35 ? "gravel" : "cobblestone");
            }
        }
    }

    /** The square inside the gate: flagstones round the flag's dais, rune stones and lamp posts at its corners. */
    void square() {
        int y = HOME;
        for (int x = -80; x <= -71; x++) for (int z = -9; z <= 9; z++) {
            double d = Math.hypot(x + 75, z);
            double n = w.n2(x * 2.1, z * 2.1, 2), n2 = w.n2(x * 1.7 + 5, z * 1.7, 2);
            String p = d < 1 ? "chiseled_stone_bricks" : d < 2.6 ? "polished_andesite" : d < 3.5 ? "stone_bricks"
                    : n > 0.4 ? "mossy_stone_bricks" : n2 > 0.4 ? "cracked_stone_bricks" : n2 < -0.45 ? "andesite" : "stone_bricks";
            if (Math.abs(z) == 9 || x == -80 || x == -71) p = n > 0.3 ? "mossy_cobblestone" : "cobblestone";
            w.set(x, y, z, p);
        }
        for (int s : new int[] {-1, 1}) {
            runeStone(-79, s * 5, 4);
            for (int dy = 1; dy <= 3; dy++) w.set(-72, y + dy, s * 8, "stripped_spruce_log[axis=y]");
            w.set(-72, y + 4, s * 8, "lantern");
        }
    }

    /** A rune stone: a carved pillar on a stone, snow on its head. */
    void runeStone(int x, int z, int h) {
        int y = land.topAt(x, z);
        w.set(x, y, z, "cobblestone");
        for (int dy = 1; dy <= h; dy++) w.set(x, y + dy, z, dy == 2 || dy == h ? "chiseled_stone_bricks" : dy == 1 ? "stone_bricks" : "polished_andesite");
        w.set(x, y + h + 1, z, "snow[layers=2]");
    }

    P stave() {
        return P.of("spruce_log[axis=y]", 3, "stripped_spruce_log[axis=y]", 1);
    }

    /**
     * The great longhouse, where the team spawns: a stone footing, stave walls between dark posts under a wall
     * plate, the steep snowy roof with a dragon's head at each gable, a carved doorway under a porch. Inside, two
     * rows of posts carry tie beams with lanterns, benches and tables down both sides, a long cold hearth with its
     * pot, shields on the walls, and the high seat on its dais at the far end.
     */
    void longhouse() {
        int y = HOME, x0 = -104, x1 = -84, z0 = -6, z1 = 6;
        k.walls(x0, z0, x1, z1, y, 5, Kit.Bands.of(0, P.of("cobblestone", 3, "mossy_cobblestone", 1), 2, stave()), "dark_oak_log", 4, P.of("spruce_planks"));
        k.beam(x0, z0, x1, z1, y + 5, "stripped_dark_oak_log");
        int ridge = norse(x0, z0, x1, z1, y + 6, true, P.of("spruce_planks", 3, "stripped_spruce_log[axis=y]", 1));
        dragonHead(x0 - 1, ridge + 1, 0, "west");
        dragonHead(x1 + 1, ridge + 1, 0, "east");
        // small windows in the gables
        for (int dy = 8; dy <= 9; dy++) {
            w.set(x1, y + dy, 0, "glass_pane");
            w.set(x0, y + dy, 0, "glass_pane");
        }
        // the doorway: three wide in a carved frame
        for (int dz = -1; dz <= 1; dz++) for (int dy = 1; dy <= 3; dy++) w.set(x1, y + dy, dz, "air");
        for (int s : new int[] {-2, 2}) for (int dy = 1; dy <= 4; dy++) w.set(x1, y + dy, s, "stripped_dark_oak_log[axis=y]");
        for (int dz = -2; dz <= 2; dz++) w.set(x1, y + 4, dz, "stripped_dark_oak_log[axis=z]");
        // the porch
        for (int x = x1 + 1; x <= x1 + 3; x++) for (int z = -3; z <= 3; z++) w.set(x, y, z, "spruce_planks");
        for (int s : new int[] {-3, 3}) {
            for (int dy = 1; dy <= 4; dy++) w.set(x1 + 3, y + dy, s, "spruce_log[axis=y]");
            for (int x = x1 + 1; x <= x1 + 3; x++) w.set(x, y + 4, s, "stripped_spruce_log[axis=x]");
        }
        for (int z = -2; z <= 2; z++) w.set(x1 + 3, y + 4, z, "stripped_spruce_log[axis=z]");
        norse(x1 + 2, -3, x1 + 3, 3, y + 5, true, P.of("spruce_planks"));
        w.hangLight(x1 + 2, y + 3, 0, "lantern");
        for (int s : new int[] {-1, 1}) w.set(x1 + 1, y + 3, s * 2, "TEAM_wall_banner[facing=east]");
        // windows with shutters, a shield over each window inside
        for (int x = x0 + 2; x <= x1 - 2; x += 4) {
            k.window(x, y + 3, z0, "north", 1, 1, "glass_pane", null, "spruce_trapdoor");
            k.window(x, y + 3, z1, "south", 1, 1, "glass_pane", null, "spruce_trapdoor");
            w.set(x, y + 4, z0 + 1, "TEAM_wall_banner[facing=south]");
            w.set(x, y + 4, z1 - 1, "TEAM_wall_banner[facing=north]");
        }
        // posts and tie beams, lanterns from the beams
        for (int x = x0 + 4; x <= x1 - 4; x += 4) {
            for (int s : new int[] {-3, 3}) for (int dy = 1; dy <= 5; dy++) w.set(x, y + dy, s, "dark_oak_log[axis=y]");
            for (int z = z0 + 1; z <= z1 - 1; z++) w.set(x, y + 6, z, "stripped_spruce_log[axis=z]");
            w.hangLight(x, y + 5, 0, "lantern");
        }
        // benches along the walls, tables in front of them, food and candles on the tables
        for (int x = x0 + 4; x <= x1 - 2; x++) {
            w.set(x, y + 1, z0 + 1, "spruce_stairs[facing=north,half=bottom]");
            w.set(x, y + 1, z1 - 1, "spruce_stairs[facing=south,half=bottom]");
            if ((x - x0) % 4 == 0) continue;
            w.set(x, y + 1, z0 + 2, "spruce_slab[type=top]");
            w.set(x, y + 1, z1 - 2, "spruce_slab[type=top]");
        }
        for (int x = x0 + 5; x <= x1 - 3; x += 2) for (int z : new int[] {z0 + 2, z1 - 2}) {
            if ((x - x0) % 4 == 0) continue;
            int pick = Math.floorMod(x * 7 + z, 4);
            w.set(x, y + 2, z, pick == 0 ? "decorated_pot" : pick == 1 ? "candle[candles=3,lit=true]" : pick == 2 ? "barrel[facing=up]" : "white_candle[candles=2,lit=true]");
        }
        // the long hearth: a stone kerb round dead coals, a pot over the middle
        for (int x = -98; x <= -93; x++) for (int z = -1; z <= 1; z++) {
            boolean kerb = x == -98 || x == -93 || Math.abs(z) == 1;
            w.set(x, y, z, kerb ? "stone_bricks" : "cobblestone");
            w.set(x, y + 1, z, kerb ? "stone_brick_slab[type=bottom]" : "campfire[lit=false]");
        }
        w.set(-96, y + 2, 0, "cauldron");
        w.set(-95, y + 2, 0, "cauldron");
        // the high seat on its dais, banners behind it
        for (int z = -3; z <= 3; z++) {
            w.set(x0 + 1, y + 1, z, "dark_oak_planks");
            w.set(x0 + 2, y + 1, z, "dark_oak_slab[type=bottom]");
            w.set(x0 + 1, y + 2, z, "TEAM_carpet");
        }
        w.set(x0 + 1, y + 2, 0, "dark_oak_stairs[facing=west,half=bottom]");
        for (int s : new int[] {-1, 1}) {
            w.set(x0 + 1, y + 2, s, "dark_oak_fence");
            w.set(x0 + 1, y + 3, s, "lantern");
            w.set(x0 + 1, y + 4, s * 2, "TEAM_wall_banner[facing=east]");
        }
        w.set(x0 + 1, y + 5, 0, "TEAM_wall_banner[facing=east]");
        // firewood and barrels against the outside walls
        for (int x = x0 + 2; x <= x0 + 5; x++) w.set(x, y + 1, z1 + 1, "spruce_log[axis=x]");
        for (int x = x0 + 3; x <= x0 + 4; x++) w.set(x, y + 2, z1 + 1, "spruce_log[axis=x]");
        w.set(x0 - 1, y + 1, -3, "barrel[facing=up]");
        w.set(x0 - 1, y + 1, 3, "barrel[facing=up]");
        w.set(x0 - 1, y + 2, 3, "barrel[facing=up]");
    }

    /** A house: stone footing, stave walls, a steep snowy roof with dragon heads, its door toward the village. */
    void house(int x0, int z0, int x1, int z1, String door) {
        int y = HOME, h = 4, cx = (x0 + x1) / 2, cz = (z0 + z1) / 2;
        k.walls(x0, z0, x1, z1, y, h, Kit.Bands.of(0, "cobblestone", 2, stave()), "dark_oak_log", 5, P.of("spruce_planks"));
        k.beam(x0, z0, x1, z1, y + h, "stripped_spruce_log");
        int ridge = norse(x0, z0, x1, z1, y + h + 1, true, P.of("spruce_planks", 3, "stripped_spruce_log[axis=y]", 1));
        dragonHead(x0 - 1, ridge + 1, cz, "west");
        dragonHead(x1 + 1, ridge + 1, cz, "east");
        int dz = door.equals("north") ? z0 : z1, in = door.equals("north") ? 1 : -1;
        w.set(cx, y + 1, dz, "spruce_door[facing=" + Kit.opp(door) + ",half=lower,hinge=left,open=false]");
        w.set(cx, y + 2, dz, "spruce_door[facing=" + Kit.opp(door) + ",half=upper,hinge=left,open=false]");
        w.set(cx, y + 3, dz, "stripped_dark_oak_log[axis=x]");
        w.set(cx, y, dz - in, "cobblestone");
        for (int s : new int[] {-3, 3}) k.window(cx + s, y + 2, dz, door, 1, 1, "glass_pane", "spruce_stairs", "spruce_trapdoor");
        k.window(cx, y + 2, door.equals("north") ? z1 : z0, Kit.opp(door), 1, 1, "glass_pane", "spruce_stairs", "spruce_trapdoor");
        for (int x : new int[] {x0, x1}) k.window(x, y + 2, cz, x == x0 ? "west" : "east", 1, 1, "glass_pane", null, "spruce_trapdoor");
        // inside: beds at the back, a chest and a loom, a hearth-stove, a table, a beam with a lamp
        int back = door.equals("north") ? z1 - 1 : z0 + 1;
        k.bed(x0 + 1, y + 1, back, "west", "brown");
        k.bed(x1 - 2, y + 1, back, "east", "white");
        w.set(cx, y + 1, back, "chest[facing=" + door + "]");
        w.set(cx - 1, y + 1, back, "barrel[facing=up]");
        w.set(cx + 1, y + 1, back, "loom[facing=" + door + "]");
        w.set(x0 + 1, y + 1, dz + in, "smoker[facing=east,lit=false]");
        w.set(x0 + 1, y + 1, dz + in * 2, "barrel[facing=up]");
        k.table(x1 - 2, y + 1, cz, "spruce_fence", "spruce_pressure_plate");
        w.set(x1 - 1, y + 1, cz, "spruce_stairs[facing=east,half=bottom]");
        w.set(x1 - 3, y + 1, cz, "spruce_stairs[facing=west,half=bottom]");
        w.set(cx, y + 1, cz, "brown_carpet");
        w.set(cx + 1, y + 1, cz, "brown_carpet");
        for (int z = z0 + 1; z <= z1 - 1; z++) w.set(cx, y + h + 1, z, "stripped_spruce_log[axis=z]");
        w.hangLight(cx, y + h, cz, "lantern");
    }

    /** The smithy: an open-fronted shed, a stone forge with its chimney through the roof, anvil, grindstone, quench tub. */
    void smithy(int x0, int z0, int x1, int z1) {
        int y = HOME;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            w.set(x, y, z, ((x + z) & 1) == 0 ? "stone_bricks" : "cobblestone");
            boolean back = z == z0, post = (x == x0 || x == x1) && (z == z0 || z == z1);
            for (int dy = 1; dy <= 4; dy++) {
                if (post || dy == 4 && (x == x0 || x == x1 || back || z == z1)) w.set(x, y + dy, z, post ? "spruce_log[axis=y]" : z == z1 || back ? "stripped_spruce_log[axis=x]" : "stripped_spruce_log[axis=z]");
                else if (back || x == x0 || x == x1) w.set(x, y + dy, z, dy == 1 ? "cobblestone" : "spruce_planks");
                else w.set(x, y + dy, z, "air");
            }
        }
        norse(x0, z0, x1, z1, y + 5, true, P.of("spruce_planks"));
        // the forge: a stone hearth against the back, its chimney, the bellows-furnace
        for (int x = x0 + 1; x <= x0 + 3; x++) {
            w.set(x, y + 1, z0 + 1, "stone_bricks");
            w.set(x, y + 2, z0 + 1, x == x0 + 2 ? "campfire[lit=false]" : "stone_brick_slab[type=bottom]");
        }
        w.set(x0 + 2, y + 1, z0 + 1, "blast_furnace[facing=south,lit=true]");
        for (int dy = 3; dy <= 11; dy++) w.set(x0 + 2, y + dy, z0 + 1, dy <= 4 ? "stone_bricks" : "cobblestone");
        w.set(x0 + 2, y + 12, z0 + 1, "cobblestone_wall");
        w.set(x0 + 4, y + 1, z0 + 2, "anvil[facing=east]");
        w.set(x1 - 1, y + 1, z0 + 1, "grindstone[face=floor,facing=south]");
        w.set(x1 - 1, y + 1, z0 + 3, "water_cauldron[level=3]");
        w.set(x1 - 1, y + 1, z0 + 2, "smithing_table");
        w.set(x0 + 1, y + 1, z1 - 1, "chest[facing=east]");
        w.hangLight(x0 + 4, y + 4, z0 + 3, "lantern");
        // iron and coal stacked outside
        w.set(x1 + 1, y + 1, z0 + 1, "barrel[facing=up]");
        w.set(x1 + 1, y + 1, z0 + 2, "coal_block");
    }

    /** A storehouse on stone stilts, to keep the stores off the snow: plank walls, a snowy roof, steps to its door. */
    void storehouse(int x0, int z0, int x1, int z1) {
        int y = HOME + 2, cx = (x0 + x1) / 2;
        for (int[] p : new int[][] {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}, {cx, z0}, {cx, z1}}) w.set(p[0], HOME + 1, p[1], "stone_bricks");
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) if (!w.type(x, HOME + 1, z).name().equals("STONE_BRICKS")) w.set(x, HOME + 1, z, "air");
        k.walls(x0, z0, x1, z1, y, 4, Kit.Bands.of(0, "spruce_planks", 1, P.of("spruce_planks", 3, "stripped_spruce_log[axis=y]", 1)), "spruce_log", 3, P.of("spruce_planks"));
        k.beam(x0, z0, x1, z1, y + 4, "stripped_spruce_log");
        norse(x0, z0, x1, z1, y + 5, true, P.of("spruce_planks"));
        w.set(cx, y + 1, z0, "spruce_door[facing=south,half=lower,hinge=left,open=false]");
        w.set(cx, y + 2, z0, "spruce_door[facing=south,half=upper,hinge=left,open=false]");
        w.set(cx, y, z0 - 1, "spruce_stairs[facing=south,half=bottom]");
        w.set(cx, y - 1, z0 - 1, "spruce_planks");
        w.set(cx, y - 1, z0 - 2, "spruce_stairs[facing=south,half=bottom]");
        for (int x = x0 + 1; x <= x1 - 1; x++) {
            w.set(x, y + 1, z1 - 1, (x & 1) == 0 ? "barrel[facing=up]" : "hay_block");
            if ((x & 1) == 1) w.set(x, y + 2, z1 - 1, "hay_block");
        }
        w.set(x0 + 1, y + 1, z0 + 1, "chest[facing=east]");
        w.set(x1 - 1, y + 1, z0 + 1, "barrel[facing=up]");
    }

    void well(int cx, int cz) {
        int y = HOME;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            boolean mid = dx == 0 && dz == 0;
            for (int yy = y - 3; yy <= y; yy++) w.set(cx + dx, yy, cz + dz, mid ? "water" : "cobblestone");
            w.set(cx + dx, y - 4, cz + dz, "cobblestone");
            if (!mid) w.set(cx + dx, y + 1, cz + dz, dx == 0 || dz == 0 ? "cobblestone_wall" : "mossy_cobblestone");
        }
        for (int s : new int[] {-1, 1}) for (int dy = 2; dy <= 4; dy++) w.set(cx + s, y + dy, cz, "spruce_fence");
        norse(cx - 1, cz - 1, cx + 1, cz + 1, y + 4, true, null);
        w.set(cx, y + 3, cz, "iron_chain[axis=y]");
    }

    void sled(int x, int z) {
        int y = HOME;
        for (int dx = 0; dx <= 3; dx++) {
            w.set(x + dx, y + 1, z, "spruce_trapdoor[facing=south,half=bottom,open=false]");
            w.set(x + dx, y + 1, z + 1, "spruce_trapdoor[facing=north,half=bottom,open=false]");
        }
        w.set(x + 3, y + 2, z, "barrel[facing=up]");
        w.set(x + 2, y + 2, z, "white_wool");
        w.set(x, y + 2, z + 1, "spruce_fence");
    }

    void cart(int x, int z) {
        int y = HOME;
        for (int dx = 0; dx <= 2; dx++) w.set(x + dx, y + 1, z, "spruce_slab[type=top]");
        w.set(x + 1, y + 2, z, "hay_block");
        w.set(x - 1, y + 1, z, "spruce_fence");
    }

    /**
     * The palisade: logs standing in the ground, uneven at the top, sharpened by the fence posts along it; a
     * postern north and south.
     */
    void palisade() {
        int y = HOME;
        for (int x = PAL_W; x <= PAL_E; x++) for (int z = -PAL_Z; z <= PAL_Z; z++) {
            boolean ring = x == PAL_W || x == PAL_E || Math.abs(z) == PAL_Z;
            if (!ring || x == PAL_E && Math.abs(z) <= 7 || x >= PAL_E - 2 && Math.abs(z) >= PAL_Z - 2) continue;
            int g = land.topAt(x, z);
            for (int yy = Math.min(g, y) - 2; yy <= y; yy++) w.set(x, yy, z, "cobblestone");
            if (Math.abs(z) == PAL_Z && Math.abs(x - POSTERN_X) <= 1) {
                w.set(x, y + 4, z, "stripped_spruce_log[axis=x]");
                for (int dy = 1; dy <= 3; dy++) w.set(x, y + dy, z, "air");
                w.set(x, y, z, "cobblestone");
                continue;
            }
            int h = 5 + (w.n2(x * 3.1, z * 3.1, 1) > 0.15 ? 1 : 0);
            boolean frame = Math.abs(z) == PAL_Z && Math.abs(x - POSTERN_X) == 2;
            String log = frame ? "stripped_dark_oak_log[axis=y]" : Math.floorMod(x * 3 + z * 7, 5) == 0 ? "stripped_spruce_log[axis=y]" : "spruce_log[axis=y]";
            if (frame) h = 6;
            for (int dy = 1; dy <= h; dy++) w.set(x, y + dy, z, log);
            w.set(x, y + h + 1, z, "spruce_fence");
        }
        // a rail of split logs along the inside, and the posterns' lamps
        for (int x = PAL_W + 1; x <= PAL_E - 3; x++) for (int s : new int[] {-1, 1}) {
            if (Math.abs(x - POSTERN_X) <= 2) continue;
            w.set(x, y + 3, s * (PAL_Z - 1), "stripped_spruce_log[axis=x]");
        }
        for (int z = -PAL_Z + 1; z <= PAL_Z - 1; z++) w.set(PAL_W + 1, y + 3, z, "stripped_spruce_log[axis=z]");
        for (int z = -PAL_Z + 3; z <= PAL_Z - 3; z++) if (Math.abs(z) >= 8) w.set(PAL_E - 1, y + 3, z, "stripped_spruce_log[axis=z]");
        for (int s : new int[] {-1, 1}) {
            w.set(POSTERN_X - 2, y + 4, s * (PAL_Z - 1), "lantern[hanging=false]");
            w.set(POSTERN_X - 2, y + 3, s * (PAL_Z - 1), "stripped_spruce_log[axis=x]");
        }
    }

    /**
     * The gatehouse over the way in: two stone towers with ladders inside, a timbered upper floor across them
     * with arrow slits toward the lake, a snowy roof with dragon heads, a portcullis raised over the gate.
     */
    void gatehouse() {
        int y = HOME, x0 = -70, x1 = -66;
        P stone = P.of("cobblestone", 3, "stone_bricks", 2, "mossy_cobblestone", 1);
        for (int x = x0; x <= x1; x++) for (int z = -7; z <= 7; z++) {
            k.footing(x, z, x, z, y, P.of("cobblestone"));
            boolean tower = Math.abs(z) >= 3;
            boolean edge = x == x0 || x == x1 || Math.abs(z) == 3 || Math.abs(z) == 7;
            w.set(x, y, z, tower ? "cobblestone" : "stone_bricks");
            for (int dy = 1; dy <= 4; dy++) w.set(x, y + dy, z, tower && edge ? stone.pick(w.r) : "air");
            w.set(x, y + 5, z, "spruce_planks");
            boolean upperEdge = x == x0 || x == x1 || Math.abs(z) == 7;
            boolean corner = (x == x0 || x == x1) && (Math.abs(z) == 7 || Math.abs(z) == 3 || z == 0);
            for (int dy = 6; dy <= 9; dy++) w.set(x, y + dy, z, !upperEdge ? "air" : corner || dy == 9 ? "dark_oak_log[axis=y]" : "spruce_planks");
        }
        for (int x = x0; x <= x1; x++) for (int s : new int[] {-1, 1}) w.set(x, y + 9, s * 7, "stripped_dark_oak_log[axis=x]");
        for (int z = -7; z <= 7; z++) for (int x : new int[] {x0, x1}) w.set(x, y + 9, z, "stripped_dark_oak_log[axis=z]");
        // arrow slits east and west, windows north and south
        for (int z : new int[] {-5, -2, 2, 5}) for (int dy = 7; dy <= 8; dy++) {
            w.set(x1, y + dy, z, "air");
            if (Math.abs(z) == 5) w.set(x0, y + dy, z, "air");
        }
        for (int s : new int[] {-1, 1}) for (int dy = 7; dy <= 8; dy++) w.set(-68, y + dy, s * 7, "air");
        int ridge = norse(x0, -7, x1, 7, y + 10, false, P.of("spruce_planks", 3, "stripped_spruce_log[axis=y]", 1));
        dragonHead(-68, ridge + 1, -8, "north");
        dragonHead(-68, ridge + 1, 8, "south");
        // each tower: a way in from the village, a ladder up its east wall through the floor
        for (int s : new int[] {-1, 1}) {
            for (int dy = 1; dy <= 2; dy++) w.set(x0, y + dy, s * 5, "air");
            for (int dy = 1; dy <= 5; dy++) w.set(x1 - 1, y + dy, s * 5, "ladder[facing=west]");
            w.hangLight(-68, y + 4, s * 5, "lantern");
            w.set(x1 + 1, y + 4, s * 4, "TEAM_wall_banner[facing=east]");
            w.set(x0 - 1, y + 4, s * 4, "TEAM_wall_banner[facing=west]");
        }
        // the portcullis, raised, and a lamp in the gateway
        for (int z = -2; z <= 2; z++) w.set(x1, y + 4, z, "iron_bars");
        w.hangLight(-68, y + 4, 0, "lantern");
        for (int x = x0; x <= x1; x++) for (int z = -2; z <= 2; z++) w.set(x, y, z, w.n2(x * 2.3, z * 2.3, 2) > 0.3 ? "mossy_cobblestone" : "cobblestone");
        // inside the gallery: a lamp, a rack of spears (fences), barrels
        w.hangLight(-68, y + 8, 0, "lantern");
        w.set(x0 + 1, y + 6, -6, "barrel[facing=up]");
        w.set(x0 + 1, y + 6, 6, "barrel[facing=up]");
    }

    /** A corner watchtower: a stone base, a timbered upper storey, a railed lookout under a hipped snowy roof. */
    void cornerTower(int cz) {
        int y = HOME, cx = PAL_E, out = Integer.signum(cz);
        P stone = P.of("cobblestone", 3, "stone_bricks", 2, "mossy_cobblestone", 1);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            int x = cx + dx, z = cz + dz;
            boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2, corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
            k.footing(x, z, x, z, y, P.of("cobblestone"));
            w.set(x, y, z, "cobblestone");
            for (int dy = 1; dy <= 4; dy++) w.set(x, y + dy, z, edge ? stone.pick(w.r) : "air");
            for (int dy = 5; dy <= 9; dy++) w.set(x, y + dy, z, !edge ? "air" : corner ? "dark_oak_log[axis=y]" : dy == 5 || dy == 9 ? "stripped_spruce_log[axis=" + (Math.abs(dx) == 2 ? "z" : "x") + "]" : "spruce_planks");
            w.set(x, y + 10, z, "spruce_planks");
            if (corner) for (int dy = 11; dy <= 13; dy++) w.set(x, y + dy, z, "dark_oak_log[axis=y]");
            else if (edge) w.set(x, y + 11, z, "spruce_fence");
        }
        hipRoof(cx - 2, cz - 2, cx + 2, cz + 2, y + 14);
        // door from the village, slits on the outer faces, a ladder up the east wall
        for (int dy = 1; dy <= 2; dy++) w.set(cx - 1, y + dy, cz - out * 2, "air");
        for (int dy = 6; dy <= 7; dy++) {
            w.set(cx + 2, y + dy, cz + out, "air");
            w.set(cx, y + dy, cz + out * 2, "air");
        }
        for (int dy = 1; dy <= 10; dy++) w.set(cx + 1, y + dy, cz, "ladder[facing=west]");
        w.hangLight(cx - 1, y + 8, cz, "lantern");
        w.set(cx, y + 4, cz + out * 3, "TEAM_wall_banner[facing=" + (out < 0 ? "north" : "south") + "]");
    }

    // ================================================================ the way down to the lake

    /** The stone stairway from the gate down to the jetty, walled either side, and the jetty out onto the ice. */
    void gateway() {
        int y = HOME;
        P steps = P.of("stone_brick_stairs", 3, "cobblestone_stairs", 1, "mossy_stone_brick_stairs", 1);
        for (int i = 0; i < 8; i++) {
            int x = -65 + i, sy = y - i;
            for (int z = -3; z <= 3; z++) {
                boolean side = Math.abs(z) == 3;
                int g = land.topAt(x, z);
                for (int yy = sy - 1; yy >= Math.min(g, sy - 1) - 1; yy--) w.set(x, yy, z, side ? "stone_bricks" : "cobblestone");
                for (int yy = sy + 1; yy <= sy + 5; yy++) w.set(x, yy, z, "air");
                if (side) {
                    w.set(x, sy, z, "stone_bricks");
                    w.set(x, sy + 1, z, "stone_brick_wall");
                } else {
                    w.set(x, sy, z, steps.pick(w.r) + "[facing=west,half=bottom]");
                }
            }
        }
        for (int s : new int[] {-1, 1}) {
            w.set(-58, 67, s * 3, "stone_bricks");
            w.set(-58, 68, s * 3, "lantern");
        }
        // the jetty: a deck of slabs half a block over the tongue's end and out over the void, posts either side,
        // the deck carried on beams under it where it's past the rock
        for (int x = -57; x <= -51; x++) for (int z = -1; z <= 1; z++) {
            w.set(x, ICE + 1, z, "spruce_slab[type=bottom]");
            for (int yy = ICE + 2; yy <= ICE + 5; yy++) w.set(x, yy, z, "air");
            if (land.topAt(x, z) == Integer.MIN_VALUE) w.set(x, ICE, z, (x & 1) == 0 ? "stripped_spruce_log[axis=z]" : "spruce_planks");
        }
        for (int x = -56; x <= -52; x += 4) for (int s : new int[] {-2, 2}) {
            w.set(x, ICE, s, "spruce_log[axis=y]");
            w.set(x, ICE + 1, s, "spruce_log[axis=y]");
            w.set(x, ICE + 2, s, "spruce_fence");
            if (x == -52) w.set(x, ICE + 3, s, "lantern");
        }
        keepClear(-66, -4, -50, 4);
    }

    /** A rowing boat frozen into the ice, snow in it. */
    void boat(int cx, int cz) {
        for (int dx = -3; dx <= 3; dx++) {
            int half = Math.abs(dx) == 3 ? 0 : 1;
            for (int dz = -half; dz <= half; dz++) {
                boolean side = Math.abs(dz) == half;
                w.set(cx + dx, ICE, cz + dz, "spruce_planks");
                w.set(cx + dx, ICE + 1, cz + dz, side ? "spruce_planks" : "snow[layers=3]");
            }
        }
        w.set(cx, ICE + 1, cz, "spruce_slab[type=bottom]");
        w.set(cx + 4, ICE + 1, cz, "spruce_fence");
        keepClear(cx - 4, cz - 2, cx + 4, cz + 2);
    }

    // ================================================================ the lake

    /** Half the longship's beam at x, at height yy: broadest amidships, flaring toward the gunwale. */
    int half(int x, int yy) {
        double t = 1 - Math.pow(Math.abs(x) / 17.5, 2.2);
        double flare = 0.72 + 0.28 * Math.max(0, Math.min(5, yy - (WALL - 2))) / 5.0;
        return (int) Math.round(4.2 * t * flare);
    }

    /**
     * The longship frozen into the top of the Ice Wall, across it, prow and stern out over the void: clinker strakes, stems curling up at both ends into dragon
     * heads, shields along the gunwales (red at one end, blue at the other), oars through the oar ports, rowing
     * benches, the mast and yard lowered onto crutches with the sail furled, cargo at the ends, and a drift of snow
     * up each side to climb aboard by. Built half and copied.
     */
    void longship() {
        int deck = WALL + 2, gun = deck + 1;
        for (int x = -17; x <= 0; x++) for (int yy = WALL - 2; yy <= gun; yy++) {
            int h = half(x, yy);
            for (int z = -h; z <= h; z++) {
                if (!w.canon(x, z)) continue;
                boolean side = Math.abs(z) == h;
                String strake = yy == gun ? "stripped_dark_oak_log[axis=x]" : ((yy & 1) == 0 ? "dark_oak_planks" : "spruce_planks");
                if (side || yy == WALL - 2 || h == 0) c(x, yy, z, strake);
                else if (yy < deck) c(x, yy, z, "spruce_planks");
                else if (yy == deck) c(x, yy, z, ((x + z) & 1) == 0 ? "spruce_planks" : "stripped_spruce_wood");
                else c(x, yy, z, "air");
            }
        }
        for (int x = -17; x <= 0; x++) for (int yy = gun + 1; yy <= gun + 4; yy++) for (int z = -6; z <= 6; z++)
            if (w.canon(x, z) && Math.abs(z) <= half(x, gun)) c(x, yy, z, "air");
        // shields on the gunwales, benches across, oars out through the ports, cargo by the stem
        for (int x = -15; x <= -1; x += 2) {
            if (x == -7) continue;
            int h = half(x, gun);
            for (int s : new int[] {-1, 1}) {
                int z = s * (h + 1);
                if (w.canon(x, z)) c(x, gun, z, "TEAM_wall_banner[facing=" + (s < 0 ? "north" : "south") + "]");
            }
        }
        for (int x : new int[] {-13, -9, -5}) {
            int h = half(x, gun);
            for (int z = -h + 1; z <= h - 1; z++) if (w.canon(x, z)) c(x, gun, z, "spruce_slab[type=bottom]");
        }
        for (int x : new int[] {-14, -10, -2}) {
            int hd = half(x, deck);
            for (int s : new int[] {-1, 1}) {
                for (int k2 = 1; k2 <= 3; k2++) c(x, deck, s * (hd + k2), "spruce_fence");
                c(x, deck, s * (hd + 4), "spruce_trapdoor[facing=" + (s < 0 ? "north" : "south") + ",half=bottom,open=true]");
            }
        }
        c(-15, gun, 0, "chest[facing=east]");
        c(-14, gun, -1, "barrel[facing=up]");
        c(-14, gun, 1, "barrel[facing=up]");
        // the stem rising and curling at this end, the dragon's head on top looking out
        int[][] stem = {{-18, WALL - 1}, {-18, WALL}, {-18, WALL + 1}, {-18, deck}, {-19, deck}, {-19, deck + 1}, {-19, deck + 2},
                {-20, deck + 2}, {-20, deck + 3}, {-20, deck + 4}, {-20, deck + 5}, {-20, deck + 6}, {-20, deck + 7}};
        for (int[] p : stem) c(p[0], p[1], 0, p[1] == deck && p[0] == -19 || p[1] == deck + 2 && p[0] == -20 ? "stripped_dark_oak_log[axis=x]" : "stripped_dark_oak_log[axis=y]");
        c(-17, WALL - 1, 0, "dark_oak_planks");
        c(-20, deck + 8, 0, "dark_oak_planks");
        c(-21, deck + 8, 0, "dark_oak_stairs[facing=east,half=top]");
        c(-21, deck + 7, 0, "dark_oak_slab[type=bottom]");
        c(-19, deck + 8, 0, "dark_oak_stairs[facing=west,half=top]");
        c(-20, deck + 9, 0, "dark_oak_trapdoor[facing=west,half=bottom,open=false]");
        // the mast and the yard lowered for the winter onto crutches either side of the middle (which stays open
        // to the sky: the hill's beacon shines up there), the sail furled on the yard
        for (int x = -14; x <= -1; x++) c(x, deck + 5, -2, "stripped_spruce_log[axis=x]");
        for (int x = -12; x <= -3; x++) if ((x & 1) == 0) c(x, deck + 4, -2, (x / 2 & 1) == 0 ? "red_wool" : "white_wool");
        for (int x : new int[] {-13, -2}) for (int dy = 1; dy <= 4; dy++) c(x, deck + dy, -2, dy == 4 ? "dark_oak_fence" : "spruce_fence");
        c(-15, deck + 5, -2, "spruce_trapdoor[facing=west,half=top,open=true]");
        // snow drifted up her sides: a ramp by halves to a gap in the gunwale
        for (int s : new int[] {-1, 1}) for (int x = -7; x <= -6; x++) {
            int hg = half(x, gun), hd = half(x, deck);
            for (int z = Math.min(hd, hg); z <= hg; z++) {
                c(x, gun, s * z, "air");
                c(x, deck, s * z, "spruce_planks");
            }
            for (int k2 = 0; k2 < 4; k2++) {
                int z = s * (hg + 1 + k2);
                double walk = (deck + 1) - k2 * 0.5;
                int full = (int) Math.floor(walk) - 1;
                for (int yy = WALL; yy <= full; yy++) c(x, yy, z, "snow_block");
                for (int yy = full + 1; yy <= full + 4; yy++) c(x, yy, z, "air");
                if (walk != Math.floor(walk)) c(x, full + 1, z, "snow[layers=5]");
            }
        }
        // a dusting of snow on her deck and gunwales
        for (int x = -17; x <= 0; x++) for (int z = -5; z <= 5; z++) {
            if (!w.canon(x, z)) continue;
            for (int yy = deck; yy <= gun; yy++) {
                Material m = w.type(x, yy, z);
                if (m != Material.SPRUCE_PLANKS && m != Material.STRIPPED_SPRUCE_WOOD && m != Material.STRIPPED_DARK_OAK_LOG) continue;
                if (w.air(x, yy + 1, z) && w.r.nextInt(3) == 0) c(x, yy + 1, z, "snow[layers=1]");
            }
        }
        keepClear(-22, -9, 22, 9);
    }

    // ================================================================ the Ice Wall

    /**
     * The two tunnels through the wall at the floes' level, each from a ledge on one face to a ledge on the other:
     * an arched vault in the banded ice, a floor of trodden snow, lamps frozen into its walls. Halfway through, each
     * opens into its shaft's chamber and goes round the foot of the stair. (Built west of the middle and turned.)
     */
    void tunnels() {
        for (int tz : new int[] {-TUNNEL_Z, TUNNEL_Z}) {
            for (int x = -16; x <= 0; x++) for (int dz = -3; dz <= 3; dz++) {
                int z = tz + dz, ad = Math.abs(dz);
                if (ad == 3) {
                    if (x == -10) w.set(x, ICE + 2, z, "sea_lantern");
                    continue;
                }
                int h = ad <= 1 ? 4 : 3;
                double n = w.n2(x * 1.7, z * 1.7, 2);
                w.set(x, ICE, z, n > 0.35 ? "packed_ice" : n < -0.4 ? "gravel" : "snow_block");
                for (int dy = 1; dy <= h; dy++) w.set(x, ICE + dy, z, "air");
            }
            keepClear(-17, tz - 4, 0, tz + 4);
        }
    }

    static final double SLOPE = 0.3;

    /** A walking surface at walk height q (a multiple of a half): a full block, or a bottom slab. Returns its block y. */
    int tread(int x, int z, double q, String full, String slab) {
        boolean s = q != Math.floor(q);
        int y = s ? (int) Math.floor(q) : (int) q - 1;
        c(x, y, z, s ? slab + "[type=bottom]" : full);
        return y;
    }

    /**
     * A shaft up through the wall from a tunnel to the top: a round chamber round a pillar of blue ice; a stair of
     * spruce treads (a block or a slab, so every step is half a block) winding up round the pillar with a rope rail
     * outside and a shelf of ice under each tread, lamps frozen into the chamber's wall; then a straight flight up
     * through a hatch onto the top, railed round. Both shafts straddle the middle line: each is built in the west
     * half only, and the two are each other's copies (thetaEnd differs by half a turn).
     */
    void shaft(int cz, double thetaEnd) {
        int base = ICE, top = WALL, walk0 = base + 1;
        double r0 = 3.5, r1 = 6.5, rm = (r0 + r1) / 2, k = SLOPE * rm;
        int ws = top - 6;
        double tb = thetaEnd - (ws - walk0) / k;
        // the chamber
        for (int dx = -9; dx <= 9; dx++) for (int dz = -9; dz <= 9; dz++) {
            double d = Math.hypot(dx, dz);
            if (d > 7.6) continue;
            int x = dx, z = cz + dz;
            for (int y = base + 1; y <= top - 3; y++) c(x, y, z, d <= 2.9 ? ((y / 3 & 1) == 0 ? "blue_ice" : "packed_ice") : "air");
            if (d > 2.9) c(x, base, z, d > 6.8 ? "packed_ice" : "snow_block");
        }
        for (int y = base + 3; y <= top - 5; y += 3) {
            double a = y * 1.3;
            c((int) Math.round(Math.cos(a) * 8), y, cz + (int) Math.round(Math.sin(a) * 8), "sea_lantern");
        }
        // the winding stair
        int R = (int) Math.ceil(r1 + 1);
        for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) {
            double d = Math.hypot(dx, dz);
            if (d < r0 - 0.2 || d > r1 + 0.6) continue;
            boolean rail = d > r1 - 0.4;
            double phi = Math.atan2(dz, dx);
            double th = phi + 2 * Math.PI * Math.ceil((tb - phi) / (2 * Math.PI));
            int x = dx, z = cz + dz;
            for (; th <= thetaEnd + 1e-9; th += 2 * Math.PI) {
                double q = Math.max(walk0, Math.round((walk0 + k * (th - tb)) * 2) / 2.0);
                int y;
                if (rail) {
                    y = (int) Math.floor(q) - 1;
                    c(x, y, z, "stripped_spruce_wood");
                    c(x, y + 1, z, "spruce_fence");
                    c(x, y + 2, z, "air");
                } else {
                    y = tread(x, z, q, "spruce_planks", "spruce_slab");
                    for (int yy = y + 1; yy <= y + 3; yy++) c(x, yy, z, "air");
                }
                if (y - 1 > base) c(x, y - 1, z, "packed_ice");
            }
        }
        // the flight up through the hatch
        int tx = (int) Math.round(-Math.sin(thetaEnd)), tz = (int) Math.round(Math.cos(thetaEnd));
        int ux = (int) Math.round(Math.cos(thetaEnd)), uz = (int) Math.round(Math.sin(thetaEnd));
        int px = (int) Math.round(rm * ux), pz = cz + (int) Math.round(rm * uz);
        String face = tx > 0 ? "east" : tx < 0 ? "west" : tz > 0 ? "south" : "north";
        int steps = top - ws + 1;
        for (int i = 0; i < steps; i++) for (int o = -2; o <= 2; o++) {
            int x = px + tx * (i + 1) + ux * o, z = pz + tz * (i + 1) + uz * o;
            int y = ws + i;
            if (Math.abs(o) == 2) {
                if (y + 1 < top - 2) {
                    c(x, y, z, "stripped_spruce_wood");
                    c(x, y + 1, z, "spruce_fence");
                }
                continue;
            }
            c(x, y, z, "spruce_stairs[facing=" + face + ",half=bottom]");
            c(x, y - 1, z, "packed_ice");
            for (int yy = y + 1; yy <= Math.min(top, y + 3); yy++) c(x, yy, z, "air");
        }
        // the rail round the hatch on top, with a lamp post at each corner
        for (int i = 1; i < steps; i++) for (int o = -2; o <= 2; o++) {
            boolean ring = Math.abs(o) == 2 || i == 1;
            if (!ring) continue;
            int x = px + tx * (i + 1) + ux * o, z = pz + tz * (i + 1) + uz * o;
            if (w.type(x, top, z).isSolid() && w.air(x, top + 1, z)) c(x, top + 1, z, i == 1 && Math.abs(o) == 2 ? "stripped_spruce_log[axis=y]" : "spruce_fence");
        }
        int hx0 = Math.min(px + tx - 2 * Math.abs(ux), px + tx * (steps + 1) - 2 * Math.abs(ux)), hx1 = Math.max(px + tx + 2 * Math.abs(ux), px + tx * (steps + 1) + 2 * Math.abs(ux));
        int hz0 = Math.min(pz + tz - 2 * Math.abs(uz), pz + tz * (steps + 1) - 2 * Math.abs(uz)), hz1 = Math.max(pz + tz + 2 * Math.abs(uz), pz + tz * (steps + 1) + 2 * Math.abs(uz));
        keepClear(hx0 - 1, hz0 - 1, hx1 + 1, hz1 + 1);
        keepClear(-hx1 - 1, -hz1 - 1, -hx0 + 1, -hz0 + 1);
        keepClear(-9, cz - 9, 9, cz + 9);
    }

    /**
     * The wall's top: a crevasse across each end with a snow bridge over it, seracs of blue ice standing about for
     * cover round the longship, cairns.
     */
    void wallTop() {
        int y = WALL;
        for (int x = -13; x <= 13; x++) {
            double mid = -34.5 + w.fbm(x * 0.6, 9, 4, 1) * 1.2;
            boolean bridge = Math.abs(x + 6) <= 1;
            for (int z = -38; z <= -31; z++) {
                if (Math.abs(z - mid) > 1.4 || !wall(x, z)) continue;
                for (int yy = y - 7; yy <= y; yy++)
                    w.set(x, yy, z, bridge && yy == y ? "snow_block" : bridge && yy == y - 1 ? "packed_ice" : yy == y - 7 ? "blue_ice" : "air");
            }
        }
        keepClear(-14, -38, 14, -31);
        for (int[] s : new int[][] {{-10, -24, 4}, {-4, -28, 3}, {-11, 21, 3}, {-2, 29, 4}, {-9, -7, 2}}) serac(s[0], s[1], s[2]);
        for (int[] c : new int[][] {{-9, -20}, {-6, 25}}) {
            w.set(c[0], y + 1, c[1], "cobblestone");
            w.set(c[0], y + 2, c[1], "mossy_cobblestone");
            w.set(c[0], y + 3, c[1], "spruce_fence");
            w.set(c[0], y + 4, c[1], "lantern");
        }
    }

    /** A serac: a leaning block of glacier ice, blue at its heart. */
    void serac(int cx, int cz, int h) {
        int y = WALL;
        for (int dy = 1; dy <= h; dy++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            double e = Math.abs(dx - (dy > h / 2 ? 0.4 : 0)) + Math.abs(dz) + w.n3(cx + dx, dy, cz + dz, 1.3) * 0.8;
            if (e > 1.7 - dy * 0.12) continue;
            w.set(cx + dx, y + dy, cz + dz, dx == 0 && dz == 0 ? "blue_ice" : "packed_ice");
        }
        keepClear(cx - 2, cz - 2, cx + 2, cz + 2);
    }

    // ================================================================ the floes

    /** The floes' ice: packed ice with blue cracks running through it, clear ice in patches, drifts of snow. */
    void floeIce() {
        for (int x = -124; x <= 0; x++) for (int z = -74; z <= 74; z++) {
            if (!w.canon(x, z)) continue;
            Isle i = isle(x, z);
            if (i == null || !i.ice() || wall(x, z)) continue;
            int y = land.topAt(x, z);
            if (y == Integer.MIN_VALUE) continue;
            if (i == SLED) {
                w.set(x, y, z, inside(SLED, x, z) > 0.2 ? "blue_ice" : "packed_ice");
                continue;
            }
            double crack = Math.abs(w.fbm(x * 0.9 + 17, z * 0.9 - 5, 7, 2));
            double crack2 = Math.abs(w.fbm(x * 1.3 - 40, z * 1.3 + 9, 5, 1));
            double drift = w.fbm(x * 0.22 + 3, z * 0.9, 9, 2);
            double clear = w.fbm(x * 0.8 - 60, z * 0.8 + 33, 6, 1);
            String top;
            if (crack < 0.04 || crack2 < 0.03) top = "blue_ice";
            else if (drift > 0.4) top = "snow_block";
            else if (clear > 0.5 && inside(i, x, z) > 0.15) top = "ice";
            else top = "packed_ice";
            w.set(x, y, z, top);
            if (top.equals("snow_block") && drift > 0.5) w.set(x, y + 1, z, "snow[layers=" + (drift > 0.62 ? 2 : 1) + "]");
        }
    }

    /**
     * The sled run: a long strip of blue ice, very fast underfoot and wide open, slalom poles with pennants down
     * both edges, a sled and a lamp post at each end.
     */
    void sledRun() {
        int y = SLED.top();
        double c = Math.cos(SLED.ang()), s = Math.sin(SLED.ang());
        for (int k = -11; k <= 11; k += 4) for (int side = -1; side <= 1; side += 2) {
            int x = (int) Math.round(SLED.x() + k * c - side * 2.4 * s), z = (int) Math.round(SLED.z() + k * s + side * 2.4 * c);
            if (land.topAt(x, z) != y || !w.air(x, y + 1, z)) continue;
            w.set(x, y + 1, z, "spruce_fence");
            w.set(x, y + 2, z, "spruce_fence");
            w.set(x, y + 3, z, ((k + 11) / 4 + (side > 0 ? 1 : 0)) % 2 == 0 ? "orange_wool" : "white_wool");
        }
        for (int e = -1; e <= 1; e += 2) {
            int x = (int) Math.round(SLED.x() + e * 13 * c), z = (int) Math.round(SLED.z() + e * 13 * s);
            if (land.topAt(x, z) != y) continue;
            w.set(x, y + 1, z, "spruce_log[axis=y]");
            w.set(x, y + 2, z, "spruce_fence");
            w.set(x, y + 3, z, "lantern");
            int sx = x - e * 3;
            for (int dx = 0; dx <= 2; dx++) {
                w.set(sx + dx * -e, y + 1, z - 1, "spruce_trapdoor[facing=south,half=bottom,open=false]");
                w.set(sx + dx * -e, y + 1, z + 1, "spruce_trapdoor[facing=north,half=bottom,open=false]");
            }
        }
    }

    // ================================================================ the skerries

    /** Level ground for a building, only where there's island under it. */
    void levelOn(int x0, int z0, int x1, int z1, int y, P top) {
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            int g = land.topAt(x, z);
            if (g == Integer.MIN_VALUE) continue;
            for (int yy = y + 1; yy <= y + 8; yy++) if (!w.air(x, yy, z)) w.set(x, yy, z, "air");
            w.set(x, y, z, top.pick(w.r));
            for (int yy = y - 1; yy >= g; yy--) w.set(x, yy, z, "stone");
        }
    }

    /**
     * The frozen falls: a stream across the pine stack's top that freezes as it pours over the edge, hanging down
     * its cliff in a curtain of blue ice and icicles.
     */
    void frozenFalls() {
        int y = S2.top();
        // the frozen stream from the middle of the stack to its south-east brink
        double ax = S2.x(), az = S2.z();
        int lx = 0, lz = 0;
        for (double t = 0; t <= 9; t += 0.5) {
            int x = (int) Math.round(ax + t * 0.71), z = (int) Math.round(az + t * 0.71);
            if (land.topAt(x, z) != y) break;
            lx = x;
            lz = z;
            for (int o = -1; o <= 1; o++) {
                int px = x + o, pz = z - o;
                if (land.topAt(px, pz) == y) w.set(px, y, pz, o == 0 ? "blue_ice" : "packed_ice");
            }
        }
        // the curtain down the cliff below the brink
        for (int o = -1; o <= 1; o++) {
            int x = lx + 1 + o, z = lz + 1 - o;
            int len = 9 + (o == 0 ? 5 : 0) + w.r.nextInt(3);
            for (int dy = 0; dy < len; dy++) {
                if (!w.air(x, y - dy, z)) continue;
                w.set(x, y - dy, z, o == 0 && (dy & 1) == 0 ? "blue_ice" : dy > len - 3 ? "ice" : "packed_ice");
            }
        }
        keepClear(lx - 3, lz - 3, lx + 2, lz + 2);
    }

    /** The lookout on the little skerry: a cairn with a lamp, a dead fire, log seats, a rune stone looking out. */
    void lookout() {
        int cx = (int) S3.x(), cz = (int) S3.z(), g = land.topAt(cx, cz);
        w.set(cx, g + 1, cz, "campfire[lit=false]");
        for (int[] s : new int[][] {{-2, 0, 1}, {2, 0, 1}, {0, -2, 0}}) {
            int x = cx + s[0], z = cz + s[1];
            if (land.topAt(x, z) == g && w.air(x, g + 1, z)) w.set(x, g + 1, z, "spruce_log[axis=" + (s[2] == 1 ? "z" : "x") + "]");
        }
        int kx = cx + 2, kz = cz + 2;
        w.set(kx, g + 1, kz, "cobblestone");
        w.set(kx, g + 2, kz, "mossy_cobblestone");
        w.set(kx, g + 3, kz, "spruce_fence");
        w.set(kx, g + 4, kz, "lantern");
        bigRuneStone(cx - 2, cz + 2, true, 3);
        keepClear(cx - 3, cz - 3, cx + 3, cz + 3);
    }

    /** Trails from the posterns out to the headland's ends. */
    void homeTrails() {
        trail(new double[][] {{POSTERN_X, -PAL_Z - 1}, {-88, -31}, {-83, -34}});
        trail(new double[][] {{POSTERN_X, PAL_Z + 1}, {-88, 31}, {-83, 34}});
    }

    /** An ice-fishing hut: a plank shack on the ice with a hole through it, a stool, a barrel, a lamp. */
    void hut(int cx, int cz, String door) {
        int y = ICE;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2, corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
            w.set(cx + dx, y, cz + dz, "spruce_planks");
            for (int dy = 1; dy <= 3; dy++) w.set(cx + dx, y + dy, cz + dz, corner ? "spruce_log[axis=y]" : edge ? (dy == 3 ? "stripped_spruce_log[axis=" + (Math.abs(dx) == 2 ? "z" : "x") + "]" : "spruce_planks") : "air");
        }
        norse(cx - 2, cz - 2, cx + 2, cz + 2, y + 4, true, P.of("spruce_planks"));
        int dz = door.equals("south") ? 2 : -2;
        w.set(cx, y + 1, cz + dz, "spruce_door[facing=" + Kit.opp(door) + ",half=lower,hinge=left,open=false]");
        w.set(cx, y + 2, cz + dz, "spruce_door[facing=" + Kit.opp(door) + ",half=upper,hinge=left,open=false]");
        k.window(cx - 2, y + 2, cz, "west", 1, 1, "glass_pane", null, null);
        k.window(cx + 2, y + 2, cz, "east", 1, 1, "glass_pane", null, null);
        w.set(cx, y, cz - dz / 2, "water");
        w.set(cx + 1, y + 1, cz - dz / 2, "spruce_stairs[facing=east,half=bottom]");
        w.set(cx - 1, y + 1, cz - dz / 2, "barrel[facing=up]");
        w.hangLight(cx, y + 3, cz, "lantern");
        // a sled and a hole outside
        w.set(cx + 3, y + 1, cz, "spruce_trapdoor[facing=west,half=bottom,open=false]");
        w.set(cx + 3, y + 1, cz + 1, "spruce_trapdoor[facing=west,half=bottom,open=false]");
        keepClear(cx - 3, cz - 3, cx + 3, cz + 3);
    }

    /** A pressure ridge: slabs of ice shoved up where the floe cracked, a jagged low wall for cover. */
    void ridge(int ax, int az, int bx, int bz) {
        int n = Math.max(Math.abs(bx - ax), Math.abs(bz - az));
        boolean alongX = Math.abs(bx - ax) > Math.abs(bz - az);
        for (int i = 0; i <= n; i++) {
            int x = ax + (int) Math.round((bx - ax) * (double) i / n), z = az + (int) Math.round((bz - az) * (double) i / n);
            int h = 1 + (int) Math.round((w.fbm(x * 1.7, z * 1.7, 3, 1) + 1) * 0.9) - (i == 0 || i == n ? 1 : 0);
            for (int o = 0; o <= 1; o++) {
                int px = x + (alongX ? 0 : o), pz = z + (alongX ? o : 0);
                if (isle(px, pz) != FLOE || land.topAt(px, pz) != ICE) continue;
                int hh = o == 0 ? h : Math.max(1, h - 1);
                for (int dy = 1; dy <= hh; dy++) w.set(px, ICE + dy, pz, dy == hh && w.r.nextInt(3) == 0 ? "snow_block" : (dy & 1) == 0 ? "blue_ice" : "packed_ice");
            }
        }
        keepClear(Math.min(ax, bx) - 1, Math.min(az, bz) - 1, Math.max(ax, bx) + 1, Math.max(az, bz) + 1);
    }

    /** An ice spike: a tapering spire of packed ice round a blue core, rooted a little below its base. */
    void spike(int cx, int cz, int base, int h, double r0) {
        for (int dy = -2; dy <= h; dy++) {
            double t = Math.max(0, dy) / (double) (h + 1);
            double r = r0 * Math.pow(1 - t, 0.9) + 0.35;
            int rr = (int) Math.ceil(r);
            for (int dx = -rr; dx <= rr; dx++) for (int dz = -rr; dz <= rr; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > r) continue;
                w.set(cx + dx, base + dy, cz + dz, d < r - 1.1 ? "blue_ice" : "packed_ice");
            }
        }
        w.set(cx, base + h + 1, cz, "packed_ice");
        keepClear(cx - 3, cz - 3, cx + 3, cz + 3);
    }

    /** An iceberg calved off the glacier, frozen into the bay. */
    void berg(int cx, int cz, double r, int h) {
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) for (int dy = 1; dy <= h + 1; dy++) {
            double e = Math.pow(dx / r, 2) + Math.pow(dz / (r * 0.8), 2) + Math.pow((dy - 0.5) / (h + 0.3), 2) + w.n3(cx + dx, dy, cz + dz, 1.5) * 0.25;
            if (e > 1) continue;
            w.set(cx + dx, ICE + dy, cz + dz, w.n3(cx + dx + 9, dy, cz + dz, 2) > 0.2 ? "blue_ice" : "packed_ice");
        }
        keepClear(cx - 3, cz - 3, cx + 3, cz + 3);
    }

    // ================================================================ the glacier

    // ================================================================ the pine hills

    /** A hunter's lodge in a clearing: a log cabin under a snowy roof, a porch, a drying rack, a woodpile. */
    void lodge(int cx, int cz) {
        int y = land.topAt(cx, cz);
        levelOn(cx - 7, cz - 6, cx + 7, cz + 5, y, P.of("snow_block", 3, "podzol", 1));
        int x0 = cx - 4, x1 = cx + 4, z0 = cz - 3, z1 = cz + 3;
        k.walls(x0, z0, x1, z1, y, 4, Kit.Bands.of(0, "cobblestone", 2, "spruce_log[axis=x]"), "stripped_spruce_log", 4, P.of("spruce_planks"));
        for (int z = z0 + 1; z <= z1 - 1; z++) for (int dy = 2; dy <= 3; dy++) {
            w.set(x0, y + dy, z, "spruce_log[axis=z]");
            w.set(x1, y + dy, z, "spruce_log[axis=z]");
        }
        k.beam(x0, z0, x1, z1, y + 4, "stripped_spruce_log");
        int ridge = norse(x0, z0, x1, z1, y + 5, true, P.of("spruce_planks"));
        dragonHead(x0 - 1, ridge + 1, cz, "west");
        dragonHead(x1 + 1, ridge + 1, cz, "east");
        w.set(cx, y + 1, z0, "spruce_door[facing=south,half=lower,hinge=left,open=true]");
        w.set(cx, y + 2, z0, "spruce_door[facing=south,half=upper,hinge=left,open=true]");
        for (int s : new int[] {-2, 2}) k.window(cx + s, y + 2, z0, "north", 1, 1, "glass_pane", "spruce_stairs", "spruce_trapdoor");
        k.window(cx, y + 2, z1, "south", 1, 1, "glass_pane", "spruce_stairs", "spruce_trapdoor");
        // the porch: a lean-to on two posts
        for (int x = x0; x <= x1; x++) w.set(x, y, z0 - 1, "spruce_planks");
        for (int x = x0 + 1; x <= x1 - 1; x++) w.set(x, y, z0 - 2, "spruce_planks");
        for (int s : new int[] {x0 + 1, x1 - 1}) for (int dy = 1; dy <= 2; dy++) w.set(s, y + dy, z0 - 2, "spruce_log[axis=y]");
        for (int x = x0; x <= x1; x++) w.set(x, y + 3, z0 - 2, "dark_oak_stairs[facing=south,half=bottom]");
        w.hangLight(cx + 2, y + 2, z0 - 1, "lantern");
        // inside: bed, stove, chest, a rug, antlers of fence over the hearth
        k.bed(x1 - 2, y + 1, z1 - 1, "east", "brown");
        w.set(x0 + 1, y + 1, z1 - 1, "smoker[facing=east,lit=false]");
        w.set(x0 + 1, y + 1, z1 - 2, "barrel[facing=up]");
        w.set(x0 + 2, y + 1, z1 - 1, "chest[facing=north]");
        w.set(x0 + 1, y + 1, z0 + 1, "crafting_table");
        for (int x = cx - 1; x <= cx + 1; x++) w.set(x, y + 1, cz, "brown_carpet");
        for (int z = z0 + 1; z <= z1 - 1; z++) w.set(cx, y + 5, z, "stripped_spruce_log[axis=z]");
        w.hangLight(cx, y + 4, cz, "lantern");
        // a drying rack of hides and a woodpile
        for (int s : new int[] {-2, 2}) for (int dy = 1; dy <= 2; dy++) w.set(x1 + 3, y + dy, cz + s, "spruce_fence");
        for (int dz = -2; dz <= 2; dz++) w.set(x1 + 3, y + 3, cz + dz, "stripped_spruce_log[axis=z]");
        for (int dz = -1; dz <= 1; dz++) w.set(x1 + 3, y + 2, cz + dz, dz == 0 ? "brown_wool" : "white_wool");
        for (int dz = -1; dz <= 1; dz++) for (int dy = 1; dy <= 2; dy++) w.set(x0 - 3, y + dy, cz + dz, "spruce_log[axis=x]");
        w.set(x0 - 3, y + 3, cz, "spruce_log[axis=x]");
        w.set(x0 - 3, y + 1, cz + 3, "spruce_log[axis=y]");
        keepClear(cx - 8, cz - 7, cx + 8, cz + 6);
    }

    /** Young pines: small snowy cones between the big trees. */
    void youngPines(double density, java.util.function.BiPredicate<Integer, Integer> where) {
        for (int x = -124; x <= 0; x++) for (int z = -64; z <= 64; z++) {
            if (!w.canon(x, z) || w.r.nextDouble() > density || !where.test(x, z) || isClear(x, z, 1)) continue;
            int g = land.topAt(x, z);
            if (g == Integer.MIN_VALUE) continue;
            Material m = w.type(x, g, z);
            if (m != Material.SNOW_BLOCK && m != Material.PODZOL && m != Material.COARSE_DIRT) continue;
            boolean free = true;
            for (int dx = -1; dx <= 1 && free; dx++) for (int dz = -1; dz <= 1 && free; dz++) for (int dy = 1; dy <= 3; dy++)
                if (!w.air(x + dx, g + dy, z + dz)) { free = false; break; }
            if (!free) continue;
            int h = 4 + w.r.nextInt(4);
            for (int dy = 1; dy <= h; dy++) w.set(x, g + dy, z, "spruce_log[axis=y]");
            for (int dy = 2; dy <= h + 1; dy++) {
                double r = (h + 1 - dy) / 2.6 + 0.4;
                if ((dy & 1) == 1) r *= 0.7;
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                    if (dx == 0 && dz == 0 && dy <= h) continue;
                    if (Math.hypot(dx, dz) > r) continue;
                    if (w.air(x + dx, g + dy, z + dz)) w.set(x + dx, g + dy, z + dz, "spruce_leaves[persistent=true]");
                }
            }
            w.set(x, g + h + 1, z, "spruce_leaves[persistent=true]");
            w.set(x, g + h + 2, z, "spruce_leaves[persistent=true]");
        }
    }

    // ================================================================ the passes and the open ground

    /** The rune circle at a pass: flagstones in a ring of carved stones round the hill. */
    void runeRing(int cz) {
        for (int x = -6; x <= 6; x++) for (int z = cz - 6; z <= cz + 6; z++) {
            if (!w.canon(x, z)) continue;
            double d = Math.hypot(x, z - cz);
            if (d > 5.6) continue;
            int g = land.topAt(x, z);
            if (g == Integer.MIN_VALUE) continue;
            double n = w.n2(x * 2.2, z * 2.2, 2), n2 = w.n2(x * 1.3 + 9, z * 1.3, 2);
            boolean ring = d > 3.8 && d < 5.0, hub = d < 1.7;
            if ((ring || hub) && n2 > -0.45) w.set(x, g, z, hub && d < 0.8 ? "chiseled_stone_bricks"
                    : n > 0.35 ? "mossy_stone_bricks" : n < -0.35 ? "cracked_stone_bricks" : n2 > 0.4 ? "polished_andesite" : "stone_bricks");
            else if (!ring && !hub && n > 0.55) w.set(x, g, z, "mossy_cobblestone");
        }
        for (int k2 = 0; k2 < 6; k2++) {
            double a = Math.PI / 6 + k2 * Math.PI / 3;
            int sx = (int) Math.round(Math.sin(a) * 6.6), sz = cz + (int) Math.round(Math.cos(a) * 6.6);
            if (!w.canon(sx, sz) && !w.canon(sx + 1, sz)) continue;
            bigRuneStone(sx, sz, Math.abs(Math.cos(a)) > Math.abs(Math.sin(a)), 3 + (k2 % 2));
        }
        keepClear(-9, cz - 9, 9, cz + 9);
    }

    /** A standing rune stone two blocks broad, its top worn down on one side, carved faces, snow on its head. */
    void bigRuneStone(int x, int z, boolean alongX, int h) {
        int ox = alongX ? 1 : 0, oz = alongX ? 0 : 1;
        for (int i = 0; i <= 1; i++) {
            int px = x + ox * i, pz = z + oz * i, g = land.topAt(px, pz);
            int hh = i == 0 ? h : h - 1;
            for (int yy = g - 1; yy <= g; yy++) w.set(px, yy, pz, "cobblestone");
            for (int dy = 1; dy <= hh; dy++)
                w.set(px, g + dy, pz, dy == hh && i == 1 ? "stone_brick_stairs[facing=" + (alongX ? "west" : "north") + ",half=bottom]"
                        : dy == 2 ? "chiseled_stone_bricks" : dy == 1 ? "stone_bricks" : (dy & 1) == 1 ? "polished_andesite" : "chiseled_stone_bricks");
            if (i == 0) w.set(px, g + hh + 1, pz, "snow[layers=2]");
        }
    }

    /** A rock outcrop: a lump of stone with moss in its cracks and snow on its back. */
    void outcrop(int cx, int cz) {
        int g = land.topAt(cx, cz);
        if (g == Integer.MIN_VALUE) return;
        double rx = 2.2 + w.r.nextDouble(), rz = 1.6 + w.r.nextDouble(), h = 2.2 + w.r.nextDouble() * 1.5;
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            double e = Math.pow(dx / rx, 2) + Math.pow(dz / rz, 2);
            if (e > 1) continue;
            int top = (int) Math.round((1 - e) * h + 0.4);
            int base = land.topAt(cx + dx, cz + dz);
            if (base == Integer.MIN_VALUE) continue;
            for (int yy = base; yy <= g + top; yy++) {
                double n = w.n3(cx + dx, yy, cz + dz, 1.4);
                w.set(cx + dx, yy, cz + dz, yy == g + top ? "snow_block" : n > 0.35 ? "andesite" : n < -0.4 ? "mossy_cobblestone" : "stone");
            }
        }
        keepClear(cx - 4, cz - 4, cx + 4, cz + 4);
    }

    /** A trodden trail through the snow. */
    void trail(double[][] line) {
        for (int i = 0; i + 1 < line.length; i++) {
            double[] a = line[i], b = line[i + 1];
            int n = (int) Math.ceil(Math.max(Math.abs(b[0] - a[0]), Math.abs(b[1] - a[1])));
            for (int j = 0; j <= n; j++) {
                int x = (int) Math.round(a[0] + (b[0] - a[0]) * j / n), z = (int) Math.round(a[1] + (b[1] - a[1]) * j / n);
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    int px = x + dx, pz = z + dz;
                    int g = land.topAt(px, pz);
                    if (g < ICE + 2 || !w.air(px, g + 1, pz) || wall(px, pz)) continue;
                    Material m = w.type(px, g, pz);
                    if (m != Material.SNOW_BLOCK && m != Material.PODZOL && m != Material.COARSE_DIRT) continue;
                    double nn = w.n2(px * 2.5, pz * 2.5, 2);
                    if (Math.abs(dx) + Math.abs(dz) == 2 && nn > -0.2) continue;
                    w.set(px, g, pz, nn > 0.35 ? "podzol" : nn < -0.35 ? "gravel" : "coarse_dirt");
                }
                keepClear(x - 2, z - 2, x + 2, z + 2);
            }
        }
    }

    /** A boat dragged up the shore and turned over for the winter, propped on a log. */
    void overturnedBoat(int cx, int cz) {
        int g = land.topAt(cx, cz);
        for (int dx = -4; dx <= 4; dx++) {
            int half = Math.abs(dx) == 4 ? 0 : 1;
            for (int dz = -half; dz <= half; dz++) {
                boolean keel = dz == 0;
                int base = land.topAt(cx + dx, cz + dz);
                for (int yy = base + 1; yy <= g + 1; yy++) w.set(cx + dx, yy, cz + dz, "spruce_planks");
                w.set(cx + dx, g + 2, cz + dz, keel ? "stripped_dark_oak_log[axis=x]" : "spruce_slab[type=bottom]");
            }
        }
        w.set(cx + 5, g + 1, cz, "spruce_log[axis=y]");
        w.set(cx + 5, g + 2, cz, "dark_oak_fence");
        keepClear(cx - 6, cz - 3, cx + 6, cz + 3);
    }

    /** Life on the rock islands' snow: stones poking through, low junipers, tufts of frozen grass, frozen puddles. */
    void scatter() {
        P stones = P.of("stone", 2, "andesite", 1, "cobblestone", 1, "mossy_cobblestone", 1);
        for (int x = -124; x <= 0; x++) for (int z = -74; z <= 74; z++) {
            if (!w.canon(x, z) || isClear(x, z, 1)) continue;
            Isle i = isle(x, z);
            if (i == null || i.ice() || wall(x, z)) continue;
            int g = land.topAt(x, z);
            if (g == Integer.MIN_VALUE || !w.air(x, g + 1, z) || w.type(x, g, z) != Material.SNOW_BLOCK) continue;
            boolean pines = i == S1 || i == S2 || i == S3;
            double roll = w.r.nextDouble(), tuft = w.fbm(x * 0.9 + 222, z * 0.9, 7, 2);
            if (roll < 0.006) {
                w.set(x, g + 1, z, stones.pick(w.r));
            } else if (roll < 0.012 && !pines) {
                w.set(x, g + 1, z, "spruce_leaves[persistent=true]");
                if (w.r.nextBoolean() && land.topAt(x + 1, z) == g && w.air(x + 1, g + 1, z)) w.set(x + 1, g + 1, z, "spruce_leaves[persistent=true]");
            } else if (roll < (pines ? 0.12 : 0.09) && tuft > 0.45) {
                w.set(x, g, z, "grass_block[snowy=false]");
                w.set(x, g + 1, z, w.r.nextInt(3) == 0 ? "fern" : "short_grass");
            }
        }
        // frozen puddles in hollows of the headland's snow
        for (int[] p : new int[][] {{-96, 33}, {-104, -31}, {-112, 8}}) {
            int g = land.topAt(p[0], p[1]);
            if (g == Integer.MIN_VALUE || isClear(p[0], p[1], 0)) continue;
            for (int dx = -2; dx <= 2; dx++) for (int dz = -1; dz <= 1; dz++) {
                if (Math.abs(dx) == 2 && Math.abs(dz) == 1) continue;
                int x = p[0] + dx, z = p[1] + dz;
                if (land.topAt(x, z) == g && w.air(x, g + 1, z) && w.type(x, g, z) == Material.SNOW_BLOCK) w.set(x, g, z, "packed_ice");
            }
        }
    }

    // ================================================================ snow, and keeping it from melting

    static final Set<Material> GROUND = Set.of(Material.SNOW_BLOCK, Material.DIRT, Material.COARSE_DIRT, Material.PODZOL, Material.GRAVEL,
            Material.STONE, Material.ANDESITE, Material.COBBLESTONE, Material.MOSSY_COBBLESTONE, Material.PACKED_ICE, Material.ICE, Material.BLUE_ICE,
            Material.DIORITE, Material.TUFF, Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS, Material.CRACKED_STONE_BRICKS, Material.SAND, Material.CLAY);

    static boolean ice(Material m) {
        return m == Material.PACKED_ICE || m == Material.ICE || m == Material.BLUE_ICE;
    }

    /**
     * Snow over the open ground. Walking heights are worked out first: every open square starts at its ground
     * and is lifted (by snow, half a block or a block at most) until it's within half a block of each uphill
     * neighbour that's at most a block above it, so slopes can be walked without jumping; real cliffs stay cliffs.
     * Then a dusting on the snowfields and snow on the pines. (Ice won't hold snow, so ice that needs a lift is
     * made snow first.)
     */
    void snowCover() {
        int x0 = -124, x1 = 124, z0 = -74, z1 = 74, sx = x1 - x0 + 1, sz = z1 - z0 + 1;
        int[][] ground = new int[sx][sz];
        double[][] walk = new double[sx][sz];
        boolean[][] open = new boolean[sx][sz];
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            int y = 118;
            while (y > 40) {
                Material m = w.type(x, y, z);
                if (m.isAir() || m == Material.SNOW || m == Material.SPRUCE_LEAVES || m == Material.SPRUCE_LOG && !GROUND.contains(w.type(x, y - 1, z))) { y--; continue; }
                break;
            }
            Material m = w.type(x, y, z);
            int i = x - x0, j = z - z0;
            ground[i][j] = y;
            open[i][j] = y > 40 && GROUND.contains(m) && w.air(x, y + 1, z) && w.air(x, y + 2, z) && w.air(x, y + 3, z);
            walk[i][j] = y + 1;
        }
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int pass = 0; pass < 12; pass++) {
            boolean changed = false;
            for (int i = 1; i < sx - 1; i++) for (int j = 1; j < sz - 1; j++) {
                if (!open[i][j]) continue;
                for (int[] d : dirs) {
                    int ni = i + d[0], nj = j + d[1];
                    if (!open[ni][nj] || ground[ni][nj] - ground[i][j] > 1 || ground[ni][nj] < ground[i][j]) continue;
                    double want = walk[ni][nj] - 0.5;
                    if (want > walk[i][j] && want <= ground[i][j] + 2) {
                        walk[i][j] = want;
                        changed = true;
                    }
                }
            }
            if (!changed) break;
        }
        int lifted = 0;
        for (int x = x0 + 1; x < x1; x++) for (int z = z0 + 1; z < z1; z++) {
            if (!w.canon(x, z)) continue;
            int i = x - x0, j = z - z0, g = ground[i][j];
            Material top = w.type(x, g + 1, z);
            if (top == Material.SPRUCE_LEAVES) continue;
            if (!open[i][j]) continue;
            Material m = w.type(x, g, z);
            double extra = walk[i][j] - (g + 1);
            if (extra > 0.25) {
                lifted++;
                if (extra > 0.75) w.set(x, g + 1, z, "snow_block");
                int y = extra > 0.75 ? g + 1 : g;
                if (ice(m) && y == g) w.set(x, g, z, "snow_block");
                if (Math.abs(extra - 0.5) < 0.01 || Math.abs(extra - 1.5) < 0.01) w.set(x, y + 1, z, "snow[layers=5]");
                else if (w.r.nextInt(3) > 0) w.set(x, y + 1, z, "snow[layers=1]");
            } else if (m == Material.SNOW_BLOCK && w.r.nextInt(3) > 0) {
                w.set(x, g + 1, z, "snow[layers=" + (w.r.nextInt(5) == 0 ? 2 : 1) + "]");
            }
        }
        // snow on the pines' needles, wherever it's open sky above them
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            if (!w.canon(x, z)) continue;
            int y = 118;
            while (y > 40 && w.air(x, y, z)) y--;
            if (w.type(x, y, z) == Material.SPRUCE_LEAVES && w.r.nextInt(4) > 0) w.set(x, y + 1, z, "snow[layers=" + (1 + w.r.nextInt(2)) + "]");
        }
        org.bukkit.Bukkit.getLogger().info("[CtfBuilder] frostfall snow lifted " + lifted + " squares for walking");
    }

    /**
     * Snow layers and clear ice melt where block light is above 11 (random ticks run in a match). Near every lamp
     * the snow becomes carpet (or a quartz slab where it's a half step) and the ice becomes packed ice.
     */
    void thaw() {
        int changed = 0;
        for (int x = -124; x <= 0; x++) for (int z = -74; z <= 74; z++) {
            if (!w.canon(x, z)) continue;
            for (int y = 50; y <= 115; y++) {
                if (w.air(x, y, z)) continue;
                int e = w.at(x, y, z).getBlockData().getLightEmission();
                if (e <= 11) continue;
                int r = e - 12;
                for (int dx = -r; dx <= r; dx++) for (int dy = -r; dy <= r; dy++) for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > r) continue;
                    int px = x + dx, py = y + dy, pz = z + dz;
                    Material m = w.type(px, py, pz);
                    if (m == Material.SNOW) {
                        int layers = ((Snow) w.at(px, py, pz).getBlockData()).getLayers();
                        w.set(px, py, pz, layers >= 4 ? "smooth_quartz_slab[type=bottom]" : "white_carpet");
                        changed++;
                    } else if (m == Material.ICE) {
                        w.set(px, py, pz, "packed_ice");
                        changed++;
                    }
                }
            }
        }
        org.bukkit.Bukkit.getLogger().info("[CtfBuilder] frostfall thaw-proofed " + changed + " blocks near lamps");
    }
}
