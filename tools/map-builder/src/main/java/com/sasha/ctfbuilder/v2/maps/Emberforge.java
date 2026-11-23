package com.sasha.ctfbuilder.v2.maps;

import com.sasha.ctfbuilder.v2.Arena;
import com.sasha.ctfbuilder.v2.Kit;
import com.sasha.ctfbuilder.v2.Land;
import com.sasha.ctfbuilder.v2.Theme;
import com.sasha.ctfbuilder.v2.W.P;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Emberforge: a map in three storeys. Nothing joins the islands; players build every crossing.
 *
 * Each team's forge is a mountain with three gates, one for each storey. The team spawns in the hall carved into
 * the mountain and comes out into the courtyard where its flag stands, on the middle storey; a stair tower goes
 * down to the quay on the low storey, and a stair cut along the crag climbs to the bastion on the high storey.
 * Above the hall a dragon crouches on the summit, its neck arched out over the courtyard, a stream of lava
 * pouring from its jaws into a pool on the quay.
 *
 * The low storey (y 62) is lava country: the Slag Fields, a broad crust of cooled lava cut by glowing rivers that
 * spill off its edges; a cinder cone; and in the middle the Crucible, a ring of rock round a sea of lava with an
 * islet in it (North Rim and South Rim are hills). The middle storey (y 81) is the forge road: the Smeltery, whose
 * hoist climbs from the Slag Fields and whose slag pours down onto them, then the Forge, a long platform laid
 * corner to corner across the middle. The high storey (y 98 to 104) is the sky road: three tall basalt pipes,
 * each higher than the last, then the Anvil (a hill), a huge anvil floating crosswise over the Forge.
 *
 * The three storeys meet in the middle: the Flue, the Forge's chimney, stands on the Crucible's islet and goes up
 * through the Forge to the Anvil's face, two stairs winding round each other inside it. Lava falls between the
 * storeys everywhere; quench pools catch anyone who jumps down from the Forge, the Smeltery or the courtyard.
 */
public final class Emberforge extends Arena {

    // the tops of the storeys (you walk a block higher)
    static final int SEA = 59, BEACH = 60, LOW = 61, QUAY = 62, RIM = 63, MID = 80, HIGH = 97, ANVIL = 99;
    // the forge mountain
    static final int FACE_X = -105, HALL_X0 = -120, YARD_X1 = -93, QUAY_X0 = -92, QUAY_X1 = -85, HORN_X1 = -84;
    static final int FLAG_X = -99, STAIR_Z = 10, BASTION_Z = 13, CRAG_Z = -16, QUAY_Z = 8;
    static final double FORGE_ANG = 0.42, ANVIL_ANG = -0.42;
    static final int CONE_X = -34, CONE_Z = 5;

    Land lo, md, hi;

    public Emberforge(World world) {
        super(world, "emberforge", false, Theme.get("emberforge"));
        name = "Emberforge";
        icon = "MAGMA_BLOCK";
        minutes = 18;
        kothPoints = 250;
        reach = 124;
        low = 0;
        high = 140;
        voidY = 12;
        spectator = new double[] {0.5, 122, 0.5, -90, 45};
    }

    // ================================================================ shapes

    /** A superellipse turned by a: 1 at the centre, 0 on the rim, negative outside. */
    static double ell(double x, double z, double cx, double cz, double rx, double rz, double a, double sq) {
        double dx = x - cx, dz = z - cz, c = Math.cos(a), s = Math.sin(a);
        double u = Math.abs((dx * c + dz * s) / rx), v = Math.abs((-dx * s + dz * c) / rz);
        return 1 - Math.pow(Math.pow(u, sq) + Math.pow(v, sq), 1 / sq);
    }

    /** Signed distance to a rounded rectangle. */
    static double rr(double x, double z, double x0, double x1, double z0, double z1, double r) {
        double tx = Math.max(0, Math.max(x0 + r - x, x - (x1 - r))), tz = Math.max(0, Math.max(z0 + r - z, z - (z1 - r)));
        return Math.hypot(tx, tz) - r;
    }

    /** Along/across an axis through the middle at angle a. */
    static double along(double x, double z, double a) {
        return x * Math.cos(a) + z * Math.sin(a);
    }

    static double across(double x, double z, double a) {
        return -x * Math.sin(a) + z * Math.cos(a);
    }

    double jag(int x, int z, double amp) {
        return w.fbm(x * 0.9 + 31, z * 0.9 - 17, 6, 2) * amp;
    }

    // ---------------------------------------------------------------- the forge mountain (home)

    boolean homeIn(int x, int z) {
        if (x >= QUAY_X0 && Math.abs(z) <= QUAY_Z) return x <= QUAY_X1;
        if (x >= -90 && x <= HORN_X1 && z >= 9 && z <= 17) return true;      // firm ground at the bastion stair's head
        double j = jag(x, z, 0.05) + w.fbm(x * 0.4 + 5, z * 0.4 - 3, 9, 2) * 0.07;
        if (ell(x, z, -104, 0, 19.5, 28.5, 0, 2.3) + j > 0) return true;
        return rr(x, z, -99, HORN_X1, -27, -9, 3) <= j * 20 || rr(x, z, -99, HORN_X1, 9, 27, 3) <= j * 20;
    }

    /** The mountain behind the gate: a ridge the dragon crouches on, a peak either side of it, gullies and crags. */
    double mountain(int x, int z) {
        double back = -x + FACE_X;          // 0 at the face, 17 at the back edge
        double ridge = 97 + 9 * Land.smooth(back / 7.0) - 5 * Land.smooth((back - 12) / 5.0);
        double pn = 114 - 1.35 * Math.hypot(x + 113, (z + 16) * 0.85), ps = 111 - 1.35 * Math.hypot(x + 112, (z - 17) * 0.85);
        double crag = Math.pow(Math.max(0, 1 - Math.abs(w.fbm(x * 0.8 + 40, z * 0.8, 9, 2)) * 2.2), 2);
        double h = Math.max(ridge, Math.max(pn, ps)) + crag * 2.5 + w.fbm(x * 1.2, z * 1.2, 6, 2) * 1.2;
        // its outer flanks fall away to crags at the island's rim
        double e = ell(x, z, -104, 0, 19.5, 28.5, 0, 2.3);
        double fall = Land.smooth(e / 0.24);
        return 83 + (h - 83) * fall + (1 - fall) * crag * 5;
    }

    double homeTop(int x, int z) {
        if (x <= FACE_X) return mountain(x, z);
        if (x >= QUAY_X0 && Math.abs(z) <= QUAY_Z) return QUAY;
        if (z >= BASTION_Z) return HIGH;
        if (z >= STAIR_Z - 1) return x <= -103 ? MID : x <= -86 ? MID + (x + 102) : HIGH;     // the bastion stair (z 9 is its parapet)
        if (x <= -100 && z <= CRAG_Z) return 89 + Math.round(w.fbm(x * 1.1, z * 1.1, 5, 2) * 2);   // the crag north of the yard
        return MID;
    }

    // ---------------------------------------------------------------- the low storey

    double slagIn(int x, int z) {
        double a = ell(x, z, -63.5, -1, 11.5, 13.5, 0, 2), b = ell(x, z, -55, 9, 8.5, 6.5, 0.5, 2), c = ell(x, z, -67, 15, 7.5, 5.5, -0.2, 2);
        return Math.max(a, Math.max(b, c)) + jag(x, z, 0.06);
    }

    double coneIn(int x, int z) {
        return ell(x, z, CONE_X, CONE_Z, 3.9, 3.9, 0, 2) + jag(x, z, 0.03);
    }

    double crucibleIn(int x, int z) {
        return ell(x, z, 0, 0, 20, 16, 0, 2) + jag(x, z, 0.03);
    }

    double seaIn(int x, int z) {
        return ell(x, z, 0, 0, 15, 10.5, 0, 2.2) + w.fbm(x * 0.7 - 9, z * 0.7 + 4, 5, 2) * 0.04;
    }

    /** The islet in the sea, under the Flue, and the causeway out to it from the west rim (and its copy from the east). */
    boolean islet(int x, int z) {
        if (Math.hypot(x, z) <= 8.6) return true;
        return x <= 0 && x >= -16 && Math.abs(z - 1) <= 1.5 + (x < -13 ? 1 : 0);
    }

    /** The river's course across the Slag Fields, from the slag pool to the south lobe's edge. */
    static final double[][] RIVER = {{-52, -6}, {-54, -1}, {-59, 3}, {-60, 8}, {-64, 12}, {-66, 17}, {-68, 23}};

    /** Distance to the river (negative inside it): the slag's run-off from the Smeltery, and a short spring in the west. */
    double riverD(int x, int z) {
        double best = 99;
        for (int i = 0; i + 1 < RIVER.length; i++) best = Math.min(best, Land.segment(x, z, RIVER[i][0], RIVER[i][1], RIVER[i + 1][0], RIVER[i + 1][1]));
        double wob = w.fbm(x * 0.6 + 3, z * 0.6, 4, 1) * 0.5;
        double spring = Land.segment(x, z, -70, -4, -74, -10) - 0.8;
        return Math.min(best - 1.3 - wob, spring);
    }

    /** Where the river can be crossed dry: crust left across it. */
    boolean ford(int x, int z) {
        return Math.abs(z - 1) <= 1 && x > -58 || Math.abs(z - 13) <= 1 || Math.abs(x + 72) <= 1;
    }

    double lowTop(int x, int z) {
        if (homeIn(x, z)) return homeTop(x, z);
        if (slagIn(x, z) > 0) {
            if (riverD(x, z) < 0 && !ford(x, z)) return LOW - 2;
            return LOW;
        }
        if (coneIn(x, z) > 0) return QUAY;
        if (crucibleIn(x, z) > 0) {
            if (islet(x, z)) return RIM;
            double s = seaIn(x, z);
            if (s > 0.16) return SEA - 2;
            if (s > 0) return SEA - 1;
            if (s > -0.07) return BEACH;
            return RIM;
        }
        return Double.NaN;
    }

    // ---------------------------------------------------------------- the middle storey

    double smelteryIn(int x, int z) {
        return ell(x, z, -56, -13, 13, 7.5, 0.22, 2) + jag(x, z, 0.05);
    }

    double forgeIn(int x, int z) {
        return Math.max(ell(x, z, 0, 0, 33, 5.5, FORGE_ANG, 2.6), 1 - Math.hypot(x, z) / 8.6);
    }

    double midTop(int x, int z) {
        if (smelteryIn(x, z) > 0 || forgeIn(x, z) > 0) return MID;
        return Double.NaN;
    }

    // ---------------------------------------------------------------- the high storey: the basalt pipes

    /** The pipes: {x, z, radius, top}, and the angles (degrees) of the thinner columns fused to each, kept off its lanes. */
    static final double[][] PIPES = {{-69, 26, 4.5, HIGH}, {-53, 34, 3.5, HIGH + 3}, {-39, 21, 4.0, HIGH + 6}};
    static final double[][] SIDE = {{-100, -40, 100}, {80, 140, -100}, {-110, 45, -170}};
    static final int[][] SIDE_TOP = {{3, -5, -9}, {-4, 4, -8}, {-6, 3, -10}};

    static double hex(double dx, double dz) {
        return Math.max(Math.abs(dz), Math.abs(dx) * 0.866 + Math.abs(dz) * 0.5);
    }

    /** The pipe at (x, z): {index, top, bottom} of the main column or one of the thinner ones fused to its side. */
    double[] pipeAt(int x, int z) {
        for (int i = 0; i < PIPES.length; i++) {
            double[] p = PIPES[i];
            double dx = x - p[0], dz = z - p[1];
            if (hex(dx, dz) <= p[2]) return new double[] {i, p[3], p[3] - 19 - Math.floorMod(x * 3 + z * 5, 4) * 2};
            for (int k = 0; k < 3; k++) {
                double a = Math.toRadians(SIDE[i][k]), r = p[2] + 1.0, sr = 1.4 + (k == 0 ? 0.3 : 0);
                double sx = p[0] + Math.cos(a) * r, sz = p[1] + Math.sin(a) * r;
                if (hex(x - sx, z - sz) <= sr) return new double[] {i, p[3] + SIDE_TOP[i][k], p[3] - 12 - k * 3 - Math.floorMod(x + z, 3) * 2};
            }
        }
        return null;
    }

    double highTop(int x, int z) {
        double[] p = pipeAt(x, z);
        return p == null ? Double.NaN : p[1];
    }

    /** The highest ground of any storey at (x, z), or Integer.MIN_VALUE. */
    int topAt(int x, int z) {
        int t = Math.max(lo.topAt(x, z), md.topAt(x, z));
        return Math.max(t, hi.topAt(x, z));
    }

    // ================================================================ materials

    String lowSurface(int x, int z, int y, int steep, double edge) {
        double big = w.fbm(x + 913, z - 377, 12, 2), fine = w.n2(x * 1.9, z * 1.9, 2);
        if (homeIn(x, z)) {
            if (steep >= 3) return rock(x, y, z);
            // ash lying on the mountain's ledges and peaks, bare rock between
            if (y > MID + 1) return big + fine * 0.3 > 0.05 ? (fine > 0.2 ? "light_gray_concrete_powder" : "tuff") : fine > 0.3 ? "smooth_basalt" : "basalt[axis=y]";
            return fine > 0.4 ? "cobbled_deepslate" : fine < -0.45 ? "andesite" : "tuff";
        }
        if (coneIn(x, z) > 0) return fine > 0.2 ? "red_terracotta" : fine < -0.3 ? "blackstone" : "basalt[axis=y]";
        if (crucibleIn(x, z) > 0) {
            if (y <= SEA) return "magma_block";
            if (y == BEACH) return fine > 0.2 ? "obsidian" : fine < -0.3 ? "crying_obsidian" : "blackstone";
            if (islet(x, z) && seaIn(x, z) > -0.02) return fine > 0 ? "polished_blackstone" : "blackstone";
            return big > 0.15 ? (fine > 0 ? "light_gray_concrete_powder" : "tuff") : big < -0.3 ? "red_terracotta" : fine > 0.3 ? "smooth_basalt" : fine < -0.3 ? "basalt[axis=y]" : "blackstone";
        }
        // the slag fields: black crust, cracked with glowing seams, ash blown into the hollows
        if (y < LOW) return "magma_block";
        double rd = riverD(x, z);
        if (rd < 1.4) return fine > 0.25 ? "obsidian" : "blackstone";
        double seam = Math.abs(w.fbm(x * 0.9 - 50, z * 0.9 + 20, 4, 2));
        if (seam < 0.04) return "magma_block";
        // drifts of pale ash over black crust, rusty cinders between
        if (big > 0.12) return fine > 0.25 ? "light_gray_concrete_powder" : fine < -0.3 ? "gravel" : "tuff";
        if (big < -0.35) return fine > 0.2 ? "red_terracotta" : "blackstone";
        return fine > 0.3 ? "basalt[axis=y]" : fine < -0.35 ? "smooth_basalt" : "blackstone";
    }

    String midSurface(int x, int z, int y, int steep, double edge) {
        double fine = w.n2(x * 1.9, z * 1.9, 2);
        if (forgeIn(x, z) > 0) return "polished_blackstone_bricks";
        if (steep >= 3) return rock(x, y, z);
        double big = w.fbm(x - 40, z + 90, 9, 2);
        return big > 0.2 ? (fine > 0 ? "light_gray_concrete_powder" : "gravel") : fine > 0.4 ? "gravel" : fine < -0.4 ? "andesite" : fine > 0 ? "tuff" : "cobbled_deepslate";
    }

    String highSurface(int x, int z, int y, int steep, double edge) {
        double fine = w.n2(x * 2.1, z * 2.1, 2);
        return steep >= 2 ? "basalt[axis=y]" : fine > 0.45 ? "light_gray_concrete_powder" : fine > 0.1 ? "smooth_basalt" : "polished_basalt[axis=y]";
    }

    static final String[] LAYERS = {"basalt[axis=y]", "tuff", "blackstone", "smooth_basalt", "deepslate", "tuff", "MAGMA", "basalt[axis=y]",
            "cobbled_deepslate", "blackstone", "tuff", "red_terracotta"};
    static final int[] THICK = {3, 2, 2, 2, 3, 1, 1, 3, 2, 2, 2, 1};
    static final int CYCLE = 24;

    /** Volcanic strata, a seam of glowing magma in each cycle (broken up), the odd block borrowing its neighbour's colour. */
    String rock(int x, int y, int z) {
        double warp = w.fbm(x * 0.5 + 11, z * 0.5 - 7, 30, 2) * 2.2;
        int at = Math.floorMod((int) Math.floor(y + warp), CYCLE);
        int i = 0;
        while (at >= THICK[i]) at -= THICK[i++];
        double tex = w.n3(x * 1.9, y * 1.9, z * 1.9, 4);
        if (tex > 0.62) i = (i + 1) % LAYERS.length;
        else if (tex < -0.64) i = (i + LAYERS.length - 1) % LAYERS.length;
        String s = LAYERS[i];
        if (s.equals("MAGMA")) return w.fbm(x * 0.8, z * 0.8 + y, 6, 1) > 0.1 ? "magma_block" : "blackstone";
        return s;
    }

    /** Basalt pipes: columns of basalt with a dark seam every so often. */
    String pipeRock(int x, int y, int z) {
        int band = Math.floorMod(y + (int) Math.round(w.fbm(x, z, 4, 1) * 2), 9);
        return band == 0 ? "blackstone" : band == 5 && w.n3(x, y, z, 2) > 0.3 ? "magma_block" : "basalt[axis=y]";
    }

    // ---------------------------------------------------------------- the undersides

    int lowBottom(int x, int z, int top, double edge) {
        double size, depth;
        if (homeIn(x, z)) {
            size = 26;
            depth = 34;
        } else if (slagIn(x, z) > 0) {
            size = 10;
            depth = 20;
        } else if (coneIn(x, z) > 0) {
            size = 4.6;
            depth = 22;
        } else {
            size = 16;
            depth = 26;
            top = Math.max(top, RIM);          // the sea's floor hangs as deep as the rim's
        }
        double e = Math.min(1, edge / (size * 0.9)), lens = 1 - Math.pow(1 - e, 1.8);
        double teeth = 3 * Math.max(0, w.fbm(x * 1.6 + 9, z * 1.6, 3, 1)) * Math.min(1, edge / 3.0);
        if (homeIn(x, z)) {
            // a lip of rock under the rim, then the mountain's root hanging far down beneath it
            double lip = Math.min(top, MID) - 5 - w.fbm(x * 0.7, z * 0.7, 4, 1) * 1.5;
            double root = 1 - Math.pow(1 - e, 1.35);
            return (int) Math.round(lip - (lip - 28) * root - teeth * 2);
        }
        return (int) Math.round(Math.min(top, RIM) - 2 - depth * lens - teeth + w.fbm(x, z, 5, 2) * 1.6 * Math.min(1, edge / 5.0));
    }

    int midBottom(int x, int z, int top, double edge) {
        if (forgeIn(x, z) > 0) {
            // a thin deck on ribs, a deeper hearth round the middle
            double hearth = Land.smooth(1 - Math.hypot(x, z) / 9.0);
            int ribs = Math.floorMod((int) Math.round(along(x, z, FORGE_ANG)), 6) == 0 ? 2 : 0;
            return top - 3 - ribs - (int) Math.round(hearth * 6) - (int) Math.round(Math.min(1, edge / 3.0) * 1.5);
        }
        double e = Math.min(1, edge / 6.5), lens = 1 - Math.pow(1 - e, 1.8);
        return (int) Math.round(top - 2 - 9 * lens + w.fbm(x, z, 4, 2) * 1.2 * Math.min(1, edge / 4.0));
    }

    int highBottom(int x, int z, int top, double edge) {
        double[] p = pipeAt(x, z);
        return p == null ? top - 2 : (int) Math.round(p[2]);
    }

    // ================================================================ build

    @Override
    protected void build() {
        lo = new Land(w, t, -124, 124, -40, 40);
        lo.top = this::lowTop;
        lo.surface = this::lowSurface;
        lo.rock = this::rock;
        lo.bottom = this::lowBottom;
        lo.soil = P.of("tuff", 2, "basalt[axis=y]", 1);
        lo.build();
        md = new Land(w, t, -100, 100, -36, 36);
        md.top = this::midTop;
        md.surface = this::midSurface;
        md.rock = this::rock;
        md.bottom = this::midBottom;
        md.soil = P.of("tuff", 2, "cobbled_deepslate", 1);
        md.build();
        hi = new Land(w, t, -90, 90, -46, 46);
        hi.top = this::highTop;
        hi.surface = this::highSurface;
        hi.rock = this::pipeRock;
        hi.bottom = this::highBottom;
        hi.soil = P.of("basalt[axis=y]");
        hi.build();

        // the forge mountain
        hall(0);
        facade(0);
        courtyard();
        bastion();
        quay();
        stairTower();
        homeLava();
        yardDetails();
        watchtower();
        dragon();

        // the low storey
        slagFields();
        cinderCone();
        crucible();

        // the middle storey
        smeltery();
        forge();

        // the high storey
        pipes();
        anvil();

        // the Flue, through all three
        flue();
        hammers();

        spawn = new double[] {-114.5, MID + 1, 0.5, -90};
        flag = new int[] {FLAG_X, MID + 1, 0};
        pads.add(new int[] {-102, MID + 1, -11});
        pads.add(new int[] {-102, MID + 1, 8});
        hills.add(new Hill("North Rim", -3, RIM + 1, -16, 3, RIM + 4, -12));
        hills.add(new Hill("Anvil", -7, ANVIL + 1, -7, 7, ANVIL + 4, 7));
        hills.add(new Hill("South Rim", -3, RIM + 1, 12, 3, RIM + 4, 16));
    }

    // ================================================================ lava

    /** A lava fall: a spout's source at (x, y, z), lava falling straight down from it to the pool or the void below. */
    void lavaFall(int x, int y, int z, int floor) {
        w.set(x, y, z, "lava");
        for (int yy = y - 1; yy > floor; yy--) {
            if (!w.air(x, yy, z) && !w.type(x, yy, z).name().equals("LAVA")) break;
            w.set(x, yy, z, "lava[level=8]");
        }
    }

    /** A pool of lava: sources at y over a hollow cut into the ground, ringed by what's there. */
    void lavaPool(int cx, int cz, double r, int y, String rim) {
        int R = (int) Math.ceil(r + 1);
        for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) {
            double d = Math.hypot(dx, dz);
            int x = cx + dx, z = cz + dz;
            if (d <= r) {
                w.set(x, y, z, "lava");
                w.set(x, y - 1, z, "magma_block");
                for (int yy = y + 1; yy <= y + 3; yy++) if (!w.air(x, yy, z)) w.set(x, yy, z, "air");
            } else if (d <= r + 1 && rim != null && !w.type(x, y, z).name().equals("LAVA")) {
                w.set(x, y, z, rim);
            }
        }
    }

    /** A quench pool: clear water three deep, to land in from far above, lined with stone. */
    void quench(int x0, int z0, int x1, int z1, int y) {
        for (int x = x0 - 1; x <= x1 + 1; x++) for (int z = z0 - 1; z <= z1 + 1; z++) {
            boolean in = x >= x0 && x <= x1 && z >= z0 && z <= z1;
            for (int yy = y - 3; yy <= y; yy++) w.set(x, yy, z, in && yy > y - 3 ? "water" : "polished_blackstone_bricks");
            if (!in) w.set(x, y, z, "polished_blackstone_bricks");
            if (in) for (int yy = y + 1; yy <= y + 6; yy++) if (!w.air(x, yy, z)) w.set(x, yy, z, "air");
        }
        keepClear(x0 - 1, z0 - 1, x1 + 1, z1 + 1);
    }

    // ================================================================ the forge mountain

    /**
     * The hall inside the mountain: a stepped vault on two rows of pillars, forges between them with lava behind
     * glass in the walls, chandeliers, banners, the team's runner down the middle and a throne dais at the back.
     */
    void hall(int cz) {
        int y = MID, x0 = HALL_X0, x1 = FACE_X - 1, z0 = cz - 9, z1 = cz + 9;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            int az = Math.abs(z - cz);
            boolean wall = x == x0 || z == z0 || z == z1;
            int ceil = az >= 7 ? 9 : az >= 4 ? 10 : 11;
            w.set(x, y, z, wall ? "polished_blackstone_bricks" : az <= 1 ? "polished_deepslate" : az == 2 || az == 5 ? "polished_blackstone_bricks"
                    : ((x + z) & 1) == 0 ? "deepslate_tiles" : "polished_deepslate");
            for (int dy = 1; dy <= 12; dy++) {
                String spec;
                if (wall) spec = dy == 1 ? "polished_blackstone" : dy % 4 == 0 ? "chiseled_polished_blackstone" : "polished_blackstone_bricks";
                else if (dy < ceil) spec = "air";
                else if (dy == ceil) spec = "deepslate_tiles";
                else spec = "deepslate_bricks";
                w.set(x, y + dy, z, spec);
            }
            if (!wall && (az == 6 || az == 3)) w.set(x, y + ceil, z, "deepslate_tile_stairs[half=top,facing=" + (z > cz ? "south" : "north") + "]");
            if (!wall && az <= 1 && x > x0 + 3) w.set(x, y + 1, z, "TEAM_carpet");
        }
        for (int x = x0 + 4; x <= x1 - 2; x += 4) for (int s : new int[] {-1, 1}) {
            int z = cz + s * 5;
            for (int dy = 1; dy <= 9; dy++) w.set(x, y + dy, z, dy == 1 || dy == 9 ? "chiseled_polished_blackstone" : dy == 4 ? "waxed_cut_copper" : "polished_blackstone_bricks");
            w.set(x, y + 6, z - s, "TEAM_wall_banner[facing=" + (s < 0 ? "south" : "north") + "]");
        }
        for (int x = x0 + 6; x <= x1 - 4; x += 4) for (int s : new int[] {-1, 1}) {
            int wz = s < 0 ? z0 : z1, iz = wz - s;
            String in = s < 0 ? "south" : "north";
            w.set(x - 1, y + 1, iz, "blast_furnace[facing=" + in + ",lit=true]");
            w.set(x, y + 1, iz, "anvil[facing=east]");
            w.set(x + 1, y + 1, iz, "barrel[facing=up]");
            for (int dy = 2; dy <= 3; dy++) {
                w.set(x, y + dy, wz, "glass");
                w.set(x, y + dy, wz + s, "lava");
            }
            for (int[] o : new int[][] {{-1, 2}, {-1, 3}, {1, 2}, {1, 3}}) w.set(x + o[0], y + o[1], wz + s, "polished_blackstone");
            w.set(x, y + 1, wz + s, "polished_blackstone");
            w.set(x, y + 4, wz + s, "polished_blackstone");
            for (int dy = 2; dy <= 3; dy++) w.set(x, y + dy, wz + 2 * s, "polished_blackstone");
            for (int dy = 5; dy < 9; dy++) w.set(x, y + dy, iz, "iron_chain[axis=y]");
            w.set(x, y + 4, iz, "lantern[hanging=true]");
        }
        for (int x = x0 + 6; x <= x1 - 2; x += 4) w.hangLight(x, y + 8, cz, "lantern");
        for (int x = x0 + 1; x <= x0 + 3; x++) for (int z = cz - 4; z <= cz + 4; z++) w.set(x, y + 1, z, x == x0 + 3 ? "polished_blackstone_brick_slab[type=bottom]" : "polished_blackstone_bricks");
        w.set(x0 + 1, y + 2, cz, "polished_blackstone_stairs[facing=west,half=bottom]");
        for (int s : new int[] {-1, 1}) {
            w.set(x0 + 1, y + 2, cz + s, "polished_blackstone_wall");
            w.set(x0 + 1, y + 3, cz + s, "lantern");
        }
        w.set(x0 + 1, y + 5, cz, "TEAM_wall_banner[facing=east]");
        keepClear(x0 - 2, z0 - 2, x1 + 2, z1 + 2);
    }

    /** The gate in the mountain's face: dressed stone, a pointed arch, great pillars with fire on top, banners, a stepped crown. */
    void facade(int cz) {
        int y = MID, x = FACE_X, ox = x + 1;
        for (int dz = -13; dz <= 13; dz++) for (int dy = 0; dy <= 18; dy++) {
            int az = Math.abs(dz);
            int limit = 18 - Math.max(0, az - 5);
            if (dy > limit) continue;
            String spec = dy == 0 ? "polished_blackstone" : dy == limit ? "polished_blackstone_bricks"
                    : dy == 6 || dy == 13 ? "chiseled_deepslate" : az % 2 == 0 ? "deepslate_bricks" : "polished_deepslate";
            w.set(x, y + dy, cz + dz, spec);
        }
        for (int dz = -2; dz <= 2; dz++) for (int dy = 1; dy <= 8; dy++) {
            boolean open = dy <= 6 || dy == 7 && Math.abs(dz) <= 1 || dy == 8 && dz == 0;
            if (open) w.set(x, y + dy, cz + dz, "air");
        }
        for (int s : new int[] {-1, 1}) {
            w.set(x, y + 7, cz + 2 * s, "polished_blackstone_brick_stairs[half=top,facing=" + (s < 0 ? "north" : "south") + "]");
            w.set(x, y + 8, cz + s, "polished_blackstone_brick_stairs[half=top,facing=" + (s < 0 ? "north" : "south") + "]");
        }
        w.set(x, y + 9, cz, "chiseled_polished_blackstone");
        for (int s : new int[] {-1, 1}) for (int dz = 4; dz <= 5; dz++) for (int dy = 0; dy <= 13; dy++) {
            String spec = dy <= 1 ? "chiseled_polished_blackstone" : dy == 4 || dy == 9 ? "waxed_cut_copper" : dy == 13 ? "chiseled_polished_blackstone" : "polished_blackstone_bricks";
            w.set(x, y + dy, cz + s * dz, spec);
            w.set(ox, y + dy, cz + s * dz, spec);
        }
        for (int s : new int[] {-1, 1}) {
            w.set(ox, y + 14, cz + s * 4, "campfire[lit=true]");
            w.set(ox, y + 14, cz + s * 5, "polished_blackstone_brick_slab[type=bottom]");
            w.set(x, y + 14, cz + s * 4, "polished_blackstone_brick_slab[type=bottom]");
            w.set(x, y + 14, cz + s * 5, "polished_blackstone_brick_slab[type=bottom]");
            w.set(ox, y + 8, cz + s * 3, "TEAM_wall_banner[facing=east]");
            w.set(ox, y + 6, cz + s * 7, "polished_blackstone_wall");
            w.set(ox, y + 5, cz + s * 7, "lantern[hanging=true]");
            for (int dy = 0; dy <= 9; dy++) w.set(ox, y + dy, cz + s * 10, dy == 9 ? "chiseled_polished_blackstone" : dy % 3 == 0 ? "polished_blackstone" : "polished_blackstone_bricks");
            w.set(ox, y + 10, cz + s * 10, "lantern");
            for (int dy = 15; dy <= 16; dy++) {
                w.set(x, y + dy, cz + s * 2, "orange_stained_glass");
                w.set(x - 1, y + dy, cz + s * 2, "shroomlight");
            }
        }
        for (int dz = -3; dz <= 3; dz++) {
            w.set(ox, y + 11, cz + dz, "polished_blackstone_brick_stairs[half=top,facing=west]");
            w.set(ox, y + 12, cz + dz, "polished_blackstone_brick_slab[type=bottom]");
        }
    }

    /**
     * The courtyard before the gate, and the north horn beyond it, one paved floor on the middle storey: the
     * flag's dais, braziers, a parapet along the edge over the quay with a gap where you can jump down into the
     * quench pool.
     */
    void courtyard() {
        int y = MID;
        for (int x = FACE_X + 1; x <= HORN_X1 + 1; x++) for (int z = -28; z <= STAIR_Z - 2; z++) {
            if (lo.topAt(x, z) != MID) continue;
            boolean horn = z <= -9 && x >= -99;
            double fd = Math.hypot(x - FLAG_X, z);
            String p;
            if (fd < 1) p = "waxed_cut_copper";
            else if (fd < 2.5) p = "polished_deepslate";
            else if (fd < 3.5) p = "chiseled_polished_blackstone";
            else if (Math.abs(fd - 6.5) < 0.5) p = "deepslate_tiles";
            else if (!horn && Math.abs(z) <= 2 && x < FLAG_X) p = Math.abs(z) == 2 ? "deepslate_tiles" : "polished_deepslate";
            else p = w.n2(x * 1.6, z * 1.6, 2) > 0.45 ? "tuff_bricks" : w.n2(x * 1.6, z * 1.6, 2) < -0.5 ? "cracked_polished_blackstone_bricks" : "polished_tuff";
            w.set(x, y, z, p);
            for (int dy = 1; dy <= 6; dy++) if (!w.air(x, y + dy, z) && x > FACE_X) w.set(x, y + dy, z, "air");
        }
        // the parapet along the drop: over the quay, and round the horn's edges
        for (int x = FACE_X + 1; x <= HORN_X1 + 1; x++) for (int z = -28; z <= STAIR_Z - 2; z++) {
            if (lo.topAt(x, z) != MID) continue;
            boolean edge = false;
            for (int[] n : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int t = lo.topAt(x + n[0], z + n[1]);
                if (t < MID - 1) edge = true;
            }
            if (!edge) continue;
            boolean gap = x == YARD_X1 && z >= 3 && z <= 6;          // the jump into the quench pool
            boolean lane = x >= -88 && z <= -12 && z >= -24;         // the horn's front, where the middle road leaves
            if (gap || lane) continue;
            w.set(x, y + 1, z, (x + z) % 5 == 0 ? "chiseled_polished_blackstone" : "polished_blackstone_wall");
        }
        for (int[] b : new int[][] {{-95, -14}, {-95, 7}, {-90, -26}, {-97, -26}}) {
            w.set(b[0], y + 1, b[1], "chiseled_polished_blackstone");
            w.set(b[0], y + 2, b[1], "polished_blackstone_wall");
            w.set(b[0], y + 3, b[1], "campfire[lit=true]");
        }
        keepClear(FACE_X - 1, -28, HORN_X1 + 1, STAIR_Z);
    }

    /** The stair cut along the south crag from the courtyard up to the bastion, and the bastion's parapets and fires. */
    void bastion() {
        for (int x = -102; x <= -86; x++) {
            int k = x + 102, y = MID + 1 + k;
            for (int z = STAIR_Z; z <= STAIR_Z + 2; z++) {
                w.set(x, y, z, "polished_blackstone_brick_stairs[facing=east,half=bottom]");
                for (int yy = y + 1; yy <= y + 4; yy++) w.set(x, yy, z, "air");
            }
            // the parapet on the open side, the crag's dressed face on the other
            w.set(x, y, STAIR_Z - 1, "polished_blackstone_bricks");
            w.set(x, y + 1, STAIR_Z - 1, (k % 4 == 0) ? "chiseled_polished_blackstone" : "polished_blackstone_wall");
            for (int yy = y; yy <= HIGH; yy++) w.set(x, yy, STAIR_Z + 3, k % 4 == 0 ? "polished_basalt[axis=y]" : "polished_blackstone_bricks");
        }
        // the bastion's floor and its parapet round the edge (open toward the pipes)
        for (int x = -104; x <= HORN_X1 + 1; x++) for (int z = STAIR_Z; z <= 28; z++) {
            if (lo.topAt(x, z) != HIGH) continue;
            if (z > STAIR_Z + 2 || x > -86) w.set(x, HIGH, z, w.n2(x * 1.4, z * 1.4, 2) > 0.3 ? "polished_basalt[axis=y]" : "polished_blackstone_bricks");
            boolean edge = false;
            for (int[] n : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) if (lo.topAt(x + n[0], z + n[1]) < HIGH - 1) edge = true;
            if (!edge || z <= STAIR_Z + 2 && x <= -86) continue;
            boolean lane = x >= -88 && z >= 17 && z <= 28;            // toward the first pipe
            if (!lane) w.set(x, HIGH + 1, z, (x * 3 + z) % 7 == 0 ? "chiseled_polished_blackstone" : "polished_blackstone_wall");
        }
        for (int[] b : new int[][] {{-97, 24}, {-90, 20}, {-96, 15}}) {
            if (lo.topAt(b[0], b[1]) != HIGH) continue;
            w.set(b[0], HIGH + 1, b[1], "chiseled_polished_blackstone");
            w.set(b[0], HIGH + 2, b[1], "campfire[lit=true]");
        }
        // the landing at the stair's foot, paved like the yard
        for (int x = -104; x <= -103; x++) for (int z = STAIR_Z - 1; z <= STAIR_Z + 2; z++) w.set(x, MID, z, "polished_tuff");
        keepClear(-105, STAIR_Z - 1, HORN_X1 + 1, 28);
    }

    /** The quay on the low storey: a paved shelf under the courtyard's cliff, the dragon's lava pool, the quench pool, the low gate. */
    void quay() {
        for (int x = QUAY_X0; x <= QUAY_X1; x++) for (int z = -QUAY_Z; z <= QUAY_Z; z++) {
            if (lo.topAt(x, z) != QUAY) continue;
            w.set(x, QUAY, z, ((x + z) & 1) == 0 ? "polished_blackstone_bricks" : "deepslate_tiles");
            for (int yy = QUAY + 1; yy <= QUAY + 6; yy++) if (!w.air(x, yy, z)) w.set(x, yy, z, "air");
        }
        // the dragon's pool: a basin rimmed with chiseled stone, lava falling into it from the jaws far above
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            int x = -88 + dx, z = dz;
            boolean in = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
            w.set(x, QUAY, z, in ? "lava" : "chiseled_polished_blackstone");
            w.set(x, QUAY - 1, z, "magma_block");
            if (!in) w.set(x, QUAY + 1, z, (dx + dz & 1) == 0 ? "polished_blackstone_wall" : "air");
        }
        quench(QUAY_X0, 4, QUAY_X0 + 2, 7, QUAY);
        // the low gate: two pillars and a lintel at the quay's front edge
        for (int s : new int[] {-1, 1}) {
            for (int dy = 1; dy <= 6; dy++) w.set(QUAY_X1, QUAY + dy, s * 6, dy == 6 ? "chiseled_polished_blackstone" : dy % 3 == 0 ? "waxed_cut_copper" : "polished_blackstone_bricks");
            w.set(QUAY_X1, QUAY + 7, s * 6, "campfire[lit=true]");
        }
        for (int z = -5; z <= 5; z++) w.set(QUAY_X1, QUAY + 6, z, Math.abs(z) == 5 || z == 0 ? "chiseled_polished_blackstone" : "polished_blackstone_bricks");
        for (int z = -3; z <= 3; z += 6) w.set(QUAY_X1, QUAY + 5, z, "lantern[hanging=true]");
        keepClear(QUAY_X0, -QUAY_Z, QUAY_X1 + 1, QUAY_Z);
    }

    static final double TOWER_X = -88.5, TOWER_Z = -5.5;

    /** The stair tower from the quay up to the north horn: a round tower against the cliff, a stair winding up inside. */
    void stairTower() {
        double cx = TOWER_X, cz = TOWER_Z, r0 = 1.0, r1 = 3.1, rw = 4.1;
        int base = QUAY, top = MID;
        for (int x = (int) Math.floor(cx - rw - 1); x <= (int) Math.ceil(cx + rw + 1); x++)
            for (int z = (int) Math.floor(cz - rw - 1); z <= (int) Math.ceil(cz + rw + 1); z++) {
                double d = Math.hypot(x - cx, z - cz);
                if (d > rw + 0.5) continue;
                boolean wall = d > r1 + 0.6;
                for (int y = base; y <= top + 5; y++) {
                    String spec;
                    if (y == base) spec = "polished_blackstone_bricks";
                    else if (wall) spec = y > top + 3 ? ((x + z & 1) == 0 ? "polished_blackstone_brick_wall" : "air")
                            : y % 6 == 0 ? "chiseled_polished_blackstone" : "polished_blackstone_bricks";
                    else spec = y > top ? "air" : d <= r0 ? "polished_basalt[axis=y]" : "air";
                    w.set(x, y, z, spec);
                }
                if (wall) w.set(x, top + 4, z, (x + z & 1) == 0 ? "polished_blackstone_bricks" : "air");
            }
        // the stair: treads (a block or a slab) winding up anticlockwise from the door to the top
        double thetaEnd = -Math.PI / 2;                 // ends on the north side, at the horn
        double rm = (r0 + r1) / 2, k = 0.42 * rm;
        int walk0 = base + 1;
        double tb = thetaEnd - (top + 1 - walk0) / k;
        for (int x = (int) Math.floor(cx - r1 - 1); x <= (int) Math.ceil(cx + r1 + 1); x++)
            for (int z = (int) Math.floor(cz - r1 - 1); z <= (int) Math.ceil(cz + r1 + 1); z++) {
                double d = Math.hypot(x - cx, z - cz);
                if (d < r0 + 0.3 || d > r1 + 0.6) continue;
                double phi = Math.atan2(z - cz, x - cx);
                double th = phi + 2 * Math.PI * Math.ceil((tb - phi) / (2 * Math.PI));
                for (; th <= thetaEnd + 1e-9; th += 2 * Math.PI) {
                    double q = Math.max(walk0, Math.round((walk0 + k * (th - tb)) * 2) / 2.0);
                    tread(x, z, q, "polished_blackstone_bricks", "polished_blackstone_brick_slab");
                }
            }
        // doors: onto the quay (south) and out onto the horn (north) at the top, and slits of light
        int dx = (int) Math.round(cx), dzS = (int) Math.round(cz + r1 + 1), dzN = (int) Math.round(cz - r1 - 1);
        for (int o = -1; o <= 1; o++) for (int dy = 1; dy <= 3; dy++) {
            w.set(dx + o, base + dy, dzS, "air");
            w.set(dx + o, top + dy, dzN, "air");
            w.set(dx + o, top + dy, dzN - 1, "air");
        }
        for (int o = -1; o <= 1; o++) w.set(dx + o, top, dzN, "polished_blackstone_bricks");
        for (int y = base + 4; y < top; y += 5) w.set((int) Math.round(cx + rw), y, (int) Math.round(cz), "orange_stained_glass");
        for (int y = base + 3; y < top; y += 5) w.set((int) Math.round(cx), y + 2, (int) Math.round(cz), "shroomlight");
        keepClear((int) (cx - rw - 1), (int) (cz - rw - 1), (int) (cx + rw + 1), (int) (cz + rw + 1));
    }

    /** A walking surface at walk height q (a multiple of a half): a full block, or a bottom slab. Returns its block y. */
    int tread(int x, int z, double q, String full, String slab) {
        boolean s = q != Math.floor(q);
        int y = s ? (int) Math.floor(q) : (int) q - 1;
        w.set(x, y, z, s ? slab + "[type=bottom]" : full);
        return y;
    }

    /**
     * Lava from the mountain: a spout in its face pours onto the crag north of the yard, runs along the crag's top
     * and falls onto the horn, where a channel takes it to the north edge and over. A spring on the bastion runs
     * off its south edge. The quay's corner spills over its front.
     */
    void homeLava() {
        int fz = -21;
        // the spout: a lip of dark stone round a source set into the face
        int sy = 95;
        w.set(FACE_X + 1, sy, fz, "lava");
        for (int[] o : new int[][] {{0, -1}, {0, 1}, {1, 0}}) w.set(FACE_X + 1 + o[0], sy, fz + o[1], "polished_blackstone_bricks");
        w.set(FACE_X + 1, sy + 1, fz, "polished_blackstone_brick_slab[type=bottom]");
        for (int y = sy - 1; y > 89; y--) w.set(FACE_X + 1, y, fz, "lava[level=8]");
        // along the crag's top
        for (int x = FACE_X + 1; x <= -100; x++) {
            for (int y = 90; y <= 94; y++) if (x > FACE_X + 1 || y > sy) w.set(x, y, fz, "air");
            w.set(x, 89, fz, "lava");
            w.set(x, 88, fz, "magma_block");
            for (int s = -1; s <= 1; s += 2) {
                for (int y = 86; y <= 89; y++) w.set(x, y, fz + s, "blackstone");
                w.set(x, 90, fz + s, "polished_blackstone_wall");
            }
        }
        int fx = -99;
        lavaFall(fx, 89, fz, MID);
        for (int z = fz; z >= -30; z--) {
            if (lo.topAt(fx, z) == Integer.MIN_VALUE) {
                lavaFall(fx, MID, z, 30);
                break;
            }
            for (int x = fx - 1; x <= fx + 1; x++) {
                boolean mid = x == fx;
                w.set(x, MID, z, mid ? "lava" : "polished_blackstone_bricks");
                w.set(x, MID - 1, z, "magma_block");
                if (!mid && z < fz) w.set(x, MID + 1, z, "polished_blackstone_wall");
            }
        }
        // the bastion's spring, running south to the edge
        int sx = -101;
        for (int z = 21; z <= 30; z++) {
            if (lo.topAt(sx, z) == Integer.MIN_VALUE) {
                lavaFall(sx, HIGH, z, 30);
                break;
            }
            for (int x = sx - 1; x <= sx + 1; x++) {
                boolean mid = x == sx;
                w.set(x, HIGH, z, mid ? "lava" : "polished_blackstone_bricks");
                w.set(x, HIGH - 1, z, "magma_block");
                if (!mid) w.set(x, HIGH + 1, z, z == 21 ? "polished_blackstone_wall" : "air");
            }
        }
        for (int x = sx - 1; x <= sx + 1; x++) w.set(x, HIGH + 1, 20, "polished_blackstone_wall");
        // the quay's south corner: a gutter over the front edge
        for (int x = -87; x <= QUAY_X1; x++) {
            w.set(x, QUAY, 7, "lava");
            w.set(x, QUAY - 1, 7, "magma_block");
        }
        lavaFall(QUAY_X1 + 1, QUAY, 7, 28);
    }

    /**
     * The yard's furniture: two forging stations against the north crag (hearth, anvil, quench tub, a rack of
     * chains), crates and a cart on the horn for cover where the middle road leaves, lamps on posts.
     */
    void yardDetails() {
        int y = MID;
        for (int x : new int[] {-102, -97}) {
            int z = -15;
            if (lo.topAt(x, z) != MID) continue;
            w.set(x - 1, y + 1, z, "blast_furnace[facing=south,lit=true]");
            w.set(x, y + 1, z, "lava_cauldron");
            w.set(x + 1, y + 1, z, "blast_furnace[facing=south,lit=true]");
            for (int dx = -1; dx <= 1; dx++) w.set(x + dx, y + 2, z, "polished_blackstone_bricks");
            w.set(x, y + 3, z, "polished_blackstone_brick_slab[type=bottom]");
            w.set(x, y + 1, z + 2, "anvil[facing=east]");
            w.set(x + 2, y + 1, z + 1, "water_cauldron[level=3]");
        }
        // cover on the horn: crates, a cart of ore, a stack of ingots
        int[][] crates = {{-89, -14}, {-89, -13}, {-88, -14}, {-91, -21}, {-87, -24}, {-86, -24}};
        for (int[] c2 : crates) if (lo.topAt(c2[0], c2[1]) == MID) w.set(c2[0], y + 1, c2[1], (c2[0] + c2[1]) % 3 == 0 ? "barrel[facing=up]" : "dark_oak_planks");
        if (lo.topAt(-89, -14) == MID) w.set(-89, y + 2, -14, "barrel[facing=up]");
        for (int[] c2 : new int[][] {{-93, -19}, {-93, -18}}) if (lo.topAt(c2[0], c2[1]) == MID) {
            w.set(c2[0], y + 1, c2[1], "rail[shape=north_south]");
        }
        // lamp posts along the yard's sides
        for (int[] p : new int[][] {{-104, -6}, {-104, 6}, {-94, -12}}) {
            if (lo.topAt(p[0], p[1]) != MID) continue;
            for (int dy = 1; dy <= 3; dy++) w.set(p[0], y + dy, p[1], "polished_blackstone_wall");
            w.set(p[0], y + 4, p[1], "lantern");
        }
    }

    /** A squat watchtower on the bastion's corner by the mountain: battlements, a great brazier, the team's banners. */
    void watchtower() {
        int x0 = -104, x1 = -100, z0 = 15, z1 = 19, y = HIGH;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
            boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
            for (int dy = 1; dy <= 9; dy++) w.set(x, y + dy, z, dy == 9 ? "polished_blackstone_bricks" : corner ? "polished_basalt[axis=y]" : dy % 4 == 0 ? "chiseled_polished_blackstone" : "polished_blackstone_bricks");
            if (edge && (x + z & 1) == 0) w.set(x, y + 10, z, "polished_blackstone_brick_wall");
        }
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) w.set(x0 + 2 + dx, y + 10, z0 + 2 + dz, dx == 0 && dz == 0 ? "campfire[lit=true,signal_fire=true]" : "magma_block");
        w.set(x0 + 2, y + 9, z0 + 2, "hay_block");
        w.set(x1 + 1, y + 6, z0 + 2, "TEAM_wall_banner[facing=east]");
        w.set(x0 + 2, y + 6, z0 - 1, "TEAM_wall_banner[facing=north]");
        for (int dy = 3; dy <= 4; dy++) {
            w.set(x1, y + dy, z0 + 2, "orange_stained_glass");
            w.set(x1 - 1, y + dy, z0 + 2, "shroomlight");
        }
        keepClear(x0 - 1, z0 - 1, x1 + 1, z1 + 1);
    }

    /** Lava spilling over an edge: a source at (x, y, z) on the edge, falling from the cell beyond it (dx, dz) to whatever's below. */
    void spill(int x, int y, int z, int dx, int dz) {
        w.set(x, y, z, "lava");
        w.set(x, y - 1, z, "magma_block");
        for (int yy = y; yy > 24; yy--) {
            int bx = x + dx, bz = z + dz;
            if (!w.air(bx, yy, bz)) break;
            w.set(bx, yy, bz, "lava[level=8]");
        }
    }

    /** Where a column dropped from (x, y, z) first meets something: its y, and whether that's lava or nothing at all. */
    boolean fallsClear(int x, int y, int z) {
        for (int yy = y - 1; yy > 24; yy--) {
            if (w.air(x, yy, z)) continue;
            return w.type(x, yy, z).name().equals("LAVA");
        }
        return true;
    }

    // ================================================================ the low storey

    void slagFields() {
        // the rivers: lava a block below the crust, magma under it, falls where they leave the island
        for (int x = -80; x <= -40; x++) for (int z = -16; z <= 26; z++) {
            if (slagIn(x, z) <= 0 || riverD(x, z) >= 0 || ford(x, z)) continue;
            w.set(x, LOW - 1, z, "lava");
            w.set(x, LOW - 2, z, "magma_block");
            for (int[] n : new int[][] {{0, 1}, {0, -1}, {1, 0}, {-1, 0}}) {
                int nx = x + n[0], nz = z + n[1];
                if (lo.topAt(nx, nz) == Integer.MIN_VALUE) lavaFall(nx, LOW - 1, nz, 26);
            }
        }
        // the fords' kerbs: a slab lip so the lava is plainly to either side
        vent(-74, -10);
        vent(-50, 9);
        vent(-70, 15);
        boulder(-57, 15);
        boulder(-56, 9);
        organPipes(-72, 5, 4);
        organPipes(-63, 19, 5);
        charred(-67, 3);
        charred(-61, -11);
    }

    /** The first z south of the Smeltery's edge at x (where you'd fall from it), or 0 if none. */
    int belowEdge(int x) {
        for (int z = 0; z >= -30; z--) if (smelteryIn(x, z) > 0) return z + 1;
        return 0;
    }

    void cinderCone() {
        int cx = CONE_X, cz = CONE_Z;
        // a spatter cone of red cinders round a vent of lava, a lip of rock round the island's edge
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            double d = Math.hypot(dx, dz);
            int x = cx + dx, z = cz + dz;
            if (d > 2.9) continue;
            int h = d < 1.0 ? 0 : (int) Math.round(3.2 - d);
            for (int dy = 1; dy <= h; dy++) w.set(x, QUAY + dy, z, w.n3(x, dy, z, 1.5) > 0.2 ? "red_terracotta" : "blackstone");
            if (d < 1.0) {
                w.set(x, QUAY, z, "lava");
                w.set(x, QUAY - 1, z, "magma_block");
            }
        }
        w.set(cx + 2, QUAY + 3, cz + 1, "hay_block");
        w.set(cx + 2, QUAY + 4, cz + 1, "campfire[lit=true,signal_fire=true]");
        keepClear(cx - 4, cz - 4, cx + 4, cz + 4);
    }

    /** The Crucible: rim, sea of lava, the islet and the causeway; spillways over the rim into the void. */
    void crucible() {
        for (int x = -22; x <= 0; x++) for (int z = -17; z <= 17; z++) {
            if (!w.canon(x, z) || crucibleIn(x, z) <= 0 || islet(x, z)) continue;
            int top = lo.topAt(x, z);
            if (top < BEACH) for (int y = top + 1; y <= SEA; y++) w.set(x, y, z, "lava");
        }
        // cinder piles along the rim, kept off the hills
        for (int[] p : new int[][] {{-17, -6}, {-12, 12}, {-19, 6}}) {
            int x = p[0], z = p[1];
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                if (lo.topAt(x + dx, z + dz) != RIM) continue;
                int h = dx == 0 && dz == 0 ? 2 : (dx * dz == 0 ? 1 : 0);
                for (int dy = 1; dy <= h; dy++) w.set(x + dx, RIM + dy, z + dz, dy == h ? "basalt[axis=y]" : "blackstone");
            }
        }
        // the hills' markers: a ring of basalt posts round each
        for (int s : new int[] {-1, 1}) {
            int x = -4, z = s * 14;
            for (int dy = 1; dy <= 2; dy++) w.set(x, RIM + dy, z, "polished_basalt[axis=y]");
            w.set(x, RIM + 3, z, "campfire[lit=true]");
        }
        // islets of obsidian standing out of the sea, a block above the lava
        for (int[] p : new int[][] {{-11, -5}, {-9, 7}, {-4, -9}}) {
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                if (Math.abs(dx) + Math.abs(dz) == 2 && (p[0] + dx + p[1]) % 2 == 0) continue;
                int x = p[0] + dx, z = p[1] + dz;
                if (seaIn(x, z) <= 0.05 || islet(x, z)) continue;
                for (int yy = SEA - 2; yy <= SEA + 1; yy++) w.set(x, yy, z, yy == SEA + 1 ? (dx == 0 && dz == 0 ? "crying_obsidian" : "obsidian") : "obsidian");
            }
        }
        // the islet: paving round the Flue, a kerb along the causeway
        for (int x = -16; x <= 0; x++) for (int z = -8; z <= 8; z++) {
            if (!islet(x, z) || !w.canon(x, z)) continue;
            w.set(x, RIM, z, Math.hypot(x, z) <= 8.6 ? (((x + z) & 1) == 0 ? "polished_blackstone_bricks" : "polished_blackstone") : "obsidian");
        }
        keepClear(-4, -17, 4, -10);
        keepClear(-4, 10, 4, 17);
    }

    /**
     * The hoist: a ladder up a timber post from the Slag Fields to the Smeltery's south edge at (cx, cz) (cz the first
     * z off the edge), a gantry over it with a chain and a hook.
     */
    void hoist(int cx, int cz) {
        int y0 = LOW, y1 = MID;
        for (int y = y0 + 1; y < y1; y++) w.set(cx, y, cz - 1, "dark_oak_planks");
        for (int y = y0 + 1; y <= y1; y++) w.set(cx, y, cz, "ladder[facing=south]");
        for (int y = y1 + 1; y <= y1 + 3; y++) w.set(cx, y, cz - 1, "air");
        for (int s = -2; s <= 2; s += 4) {
            for (int y = y0 + 1; y <= y1 + 5; y++) w.set(cx + s, y, cz, "dark_oak_log[axis=y]");
            w.set(cx + s, y0, cz, "polished_blackstone_bricks");
        }
        for (int x = cx - 2; x <= cx + 2; x++) w.set(x, y1 + 6, cz, "dark_oak_log[axis=x]");
        for (int y = y1 + 1; y <= y1 + 5; y++) w.set(cx + 1, y, cz + 1, "iron_chain[axis=y]");
        w.set(cx + 1, y1 + 6, cz + 1, "dark_oak_planks");
        keepClear(cx - 3, cz - 2, cx + 3, cz + 2);
    }

    // ================================================================ the middle storey

    void smeltery() {
        int y = MID;
        smelter(-57, -16);
        // under its south edge, on the Slag Fields: the quench pool, the hoist, the pool its slag pours into
        int qz = belowEdge(-65);
        quench(-66, qz, -64, qz + 2, LOW);
        int hz = belowEdge(-60);
        hoist(-60, hz);
        int pz = belowEdge(-52);
        lavaPool(-52, pz + 1, 1.3, LOW - 1, "obsidian");
        // rails from the smelter to the slag tip at the south edge, a cart tipped over it
        for (int x = -55; x <= -52; x++) w.set(x, y + 1, -13, "rail[shape=east_west]");
        // the slag pour: a channel from the smelter to the edge, lava falling to the pool on the Slag Fields
        for (int z = -12; z < pz; z++) {
            w.set(-52, y, z, "lava");
            w.set(-52, y - 1, z, "magma_block");
            for (int x : new int[] {-53, -51}) if (md.topAt(x, z) == MID) w.set(x, y + 1, z, "polished_blackstone_wall");
        }
        for (int yy = y; yy > LOW; yy--) if (w.air(-52, yy, pz)) w.set(-52, yy, pz, "lava[level=8]");
        int bz = (pz - 12) / 2;
        for (int x = -53; x <= -51; x++) w.set(x, y + 1, bz, "polished_blackstone_brick_slab[type=bottom]");
        keepClear(-68, qz - 4, -50, qz + 3);
    }

    /** A dwarven smelter: an open-fronted stone shed, furnaces under a tall smoking chimney, lava behind glass. */
    void smelter(int cx, int cz) {
        int y = MID, x0 = cx - 4, x1 = cx + 4, z0 = cz - 3, z1 = cz + 3;    // the front (z1) faces south, toward the hoist
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            w.set(x, y, z, ((x + z) & 1) == 0 ? "polished_deepslate" : "cobbled_deepslate");
            for (int dy = 1; dy <= 7; dy++) w.set(x, y + dy, z, "air");
            boolean wall = x == x0 || x == x1 || z == z0;
            boolean post = (x == x0 || x == x1) && (z == z0 || z == z1);
            if (wall) for (int dy = 1; dy <= 5; dy++) w.set(x, y + dy, z, post ? "polished_basalt[axis=y]" : dy <= 2 ? "cobbled_deepslate" : "deepslate_bricks");
        }
        for (int x : new int[] {cx - 1, cx + 1}) for (int dy = 1; dy <= 5; dy++) w.set(x, y + dy, z1, "polished_basalt[axis=y]");
        for (int x = x0; x <= x1; x++) w.set(x, y + 5, z1, "polished_basalt[axis=x]");
        k.gable(x0, z0, x1, z1, y + 6, true, 1, Kit.Roof.of("deepslate_tiles"), P.of("deepslate_bricks"));
        w.set(cx - 1, y + 1, z0 + 1, "blast_furnace[facing=south,lit=true]");
        w.set(cx, y + 1, z0 + 1, "furnace[facing=south,lit=true]");
        w.set(cx + 1, y + 1, z0 + 1, "blast_furnace[facing=south,lit=true]");
        for (int dy = 2; dy <= 3; dy++) for (int x = cx - 1; x <= cx + 1; x++) w.set(x, y + dy, z0 + 1, "deepslate_bricks");
        for (int dy = 4; dy <= 14; dy++) for (int x = cx - 1; x <= cx; x++) w.set(x, y + dy, z0 + (dy <= 5 ? 1 : 0), dy == 14 ? "polished_blackstone_bricks" : "bricks");
        w.set(cx, y + 14, z0, "hay_block");
        w.set(cx, y + 15, z0, "campfire[lit=true,signal_fire=true]");
        w.set(cx - 1, y + 15, z0, "polished_blackstone_brick_slab[type=bottom]");
        for (int dy = 2; dy <= 3; dy++) {
            w.set(x0, y + dy, cz, "glass");
            w.set(x0 - 1, y + dy, cz, "lava");
            for (int[] o : new int[][] {{-1, -1}, {-1, 1}, {-2, 0}}) w.set(x0 + o[0], y + dy, cz + o[1], "cobbled_deepslate");
        }
        w.set(x0 - 1, y + 1, cz, "cobbled_deepslate");
        w.set(x0 - 1, y + 4, cz, "cobbled_deepslate");
        w.set(x1 - 1, y + 1, cz, "lava_cauldron");
        w.set(cx + 2, y + 1, z1 - 1, "anvil[facing=east]");
        w.set(x0 + 1, y + 1, z0 + 1, "barrel[facing=up]");
        w.hangLight(cx, y + 4, cz + 1, "lantern");
        keepClear(x0 - 3, z0 - 2, x1 + 2, z1 + 3);
    }

    /**
     * The Forge: a long deck corner to corner across the middle on dark ribs, the hearth round the Flue in the
     * middle, quench troughs and anvils at its ends, drop holes over pools on the Crucible's rim, and lava
     * running off its sides into the sea.
     */
    void forge() {
        int y = MID;
        for (int x = -34; x <= 0; x++) for (int z = -16; z <= 16; z++) {
            if (!w.canon(x, z) || forgeIn(x, z) <= 0) continue;
            double u = along(x, z, FORGE_ANG), v = across(x, z, FORGE_ANG);
            double r = Math.hypot(x, z);
            String p;
            if (r < 8.6) p = Math.abs(r - 7.2) < 0.6 ? "chiseled_polished_blackstone" : ((x + z) & 1) == 0 ? "polished_deepslate" : "deepslate_tiles";
            else if (Math.abs(v) < 1.2) p = "polished_deepslate";
            else if (forgeIn(x, z) < 0.12) p = "polished_blackstone";
            else p = Math.floorMod((int) Math.round(u), 6) == 0 ? "chiseled_polished_blackstone" : "polished_blackstone_bricks";
            c(x, y, z, p);
        }
        // a low kerb along its long edges, gaps at the drop holes and the ends
        for (int x = -34; x <= 0; x++) for (int z = -16; z <= 16; z++) {
            if (!w.canon(x, z) || md.topAt(x, z) != MID || Math.hypot(x, z) < 9.5) continue;
            boolean edge = false;
            for (int[] n : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) if (md.topAt(x + n[0], z + n[1]) == Integer.MIN_VALUE) edge = true;
            double u = along(x, z, FORGE_ANG);
            if (!edge || Math.abs(u) > 26) continue;
            if (Math.floorMod((int) Math.round(u), 4) == 0) c(x, y + 1, z, "polished_blackstone_wall");
        }
        // the drop holes: over each pool on the Crucible's rim, where the deck crosses it
        int[][] drops = {{-16, -7}};
        for (int[] d : drops) {
            for (int dx = 0; dx <= 1; dx++) for (int dz = 0; dz <= 1; dz++) for (int yy = y - 8; yy <= y; yy++) if (!w.air(d[0] + dx, yy, d[1] + dz)) w.set(d[0] + dx, yy, d[1] + dz, "air");
            int ry = lo.topAt(d[0], d[1]);
            if (ry >= RIM - 1) quench(d[0], d[1], d[0] + 1, d[1] + 1, RIM);
            for (int dx = -1; dx <= 2; dx++) for (int dz = -1; dz <= 2; dz++) {
                boolean hole = dx >= 0 && dx <= 1 && dz >= 0 && dz <= 1;
                if (!hole && md.topAt(d[0] + dx, d[1] + dz) == MID) w.set(d[0] + dx, y, d[1] + dz, "chiseled_polished_blackstone");
            }
        }
        // lava off its sides: gutters at the edge where the sea is below
        for (int[] f : new int[][] {{-12, 5}, {-9, -5}, {-20, 5}}) {
            double ux = Math.cos(FORGE_ANG), uz = Math.sin(FORGE_ANG), vx = -Math.sin(FORGE_ANG), vz = Math.cos(FORGE_ANG);
            int s2 = Integer.signum(f[1]);
            int ex = 0, ez = 0;
            for (double v = 0; v <= 8; v += 0.5) {
                int x = (int) Math.round(f[0] * ux + s2 * v * vx), z = (int) Math.round(f[0] * uz + s2 * v * vz);
                if (md.topAt(x, z) != MID) break;
                ex = x;
                ez = z;
            }
            int dx = (int) Math.round(s2 * vx), dz = (int) Math.round(s2 * vz);
            if (Math.abs(dx) + Math.abs(dz) == 2) dz = 0;
            if (!w.canon(ex, ez) || !w.air(ex + dx, MID, ez + dz) || !fallsClear(ex + dx, MID, ez + dz)) continue;
            spill(ex, MID, ez, dx, dz);
        }
        // hearths along the deck: low stone forges glowing with lava, cover on the long run to the middle
        for (int s : new int[] {-1, 1}) {
            double u0 = 15 * s, v0 = -2.0 * s;
            for (int du = -1; du <= 1; du++) for (int dv = -1; dv <= 0; dv++) {
                int x = (int) Math.round((u0 + du) * Math.cos(FORGE_ANG) - (v0 + dv) * Math.sin(FORGE_ANG));
                int z = (int) Math.round((u0 + du) * Math.sin(FORGE_ANG) + (v0 + dv) * Math.cos(FORGE_ANG));
                if (!w.canon(x, z) || md.topAt(x, z) != MID) continue;
                w.set(x, y + 1, z, du == 0 ? "magma_block" : "polished_blackstone_bricks");
                w.set(x, y + 2, z, du == 0 ? "lava_cauldron" : "polished_blackstone_brick_slab[type=bottom]");
            }
            double u1 = 21 * s, v1 = 2.5 * s;
            int x = (int) Math.round(u1 * Math.cos(FORGE_ANG) - v1 * Math.sin(FORGE_ANG)), z = (int) Math.round(u1 * Math.sin(FORGE_ANG) + v1 * Math.cos(FORGE_ANG));
            if (w.canon(x, z) && md.topAt(x, z) == MID) {
                w.set(x, y + 1, z, "barrel[facing=up]");
                w.set(x + 1, y + 1, z, "barrel[facing=up]");
                w.set(x, y + 2, z, "barrel[facing=up]");
            }
        }
        // anvils and quench troughs at the ends
        for (int s : new int[] {-1, 1}) {
            double ux = Math.cos(FORGE_ANG) * 27 * s, uz = Math.sin(FORGE_ANG) * 27 * s;
            int x = (int) Math.round(ux), z = (int) Math.round(uz);
            w.set(x, y + 1, z, "anvil[facing=east]");
            w.set(x + 1, y + 1, z + 1, "water_cauldron[level=3]");
            w.set(x - 1, y + 1, z - 1, "blast_furnace[facing=north,lit=true]");
        }
        keepClear(-34, -16, 34, 16);
    }

    // ================================================================ the high storey

    void pipes() {
        for (double[] p : PIPES) {
            int cx = (int) Math.round(p[0]), cz = (int) Math.round(p[1]), top = (int) p[3];
            // a ring of basalt round the top's edge, a brazier in the middle
            for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) {
                int x = cx + dx, z = cz + dz;
                if (hi.topAt(x, z) != top) continue;
                boolean edge = false;
                for (int[] n : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) if (hi.topAt(x + n[0], z + n[1]) < top) edge = true;
                if (edge && ((x * 7 + z * 3) & 3) == 0) w.set(x, top + 1, z, "polished_basalt[axis=y]");
            }
            w.set(cx, top + 1, cz, "chiseled_polished_blackstone");
            w.set(cx, top + 2, cz, "campfire[lit=true]");
            keepClear(cx - 5, cz - 5, cx + 5, cz + 5);
        }
        // the last pipe's top spills lava down its outer side into the void
        int top = (int) PIPES[2][3];
        for (int z = 21; z <= 30; z++) if (hi.topAt(-39, z + 1) < top && hi.topAt(-39, z) == top) {
            if (fallsClear(-39, top, z + 1)) spill(-39, top, z, 0, 1);
            break;
        }
    }

    /**
     * The Anvil: a giant anvil floating crosswise above the Forge, its face a hill, its horns pointing at each
     * side's last pipe; the Flue comes up through a hatch in the middle of its face.
     */
    void anvil() {
        for (int x = -34; x <= 0; x++) for (int z = -16; z <= 16; z++) {
            if (!w.canon(x, z)) continue;
            double u = along(x, z, ANVIL_ANG), v = across(x, z, ANVIL_ANG), au = Math.abs(u), av = Math.abs(v);
            int top, bot;
            double hw;
            if (au <= 9) {                         // the face
                hw = 9;
                top = ANVIL;
                bot = ANVIL - 2;
            } else if (au <= 13) {                 // the shoulders
                hw = 9 - (au - 9) * 0.75;
                top = ANVIL;
                bot = ANVIL - 2;
            } else if (au <= 31) {                 // the horns: round, tapering to a point and dipping a little
                double t = (au - 13) / 18.0, r = 6 * Math.pow(1 - t, 0.9);
                hw = r;
                top = ANVIL - (int) Math.floor(t * 2.6);
                bot = top - Math.max(0, (int) Math.round(r * 1.1 - Math.pow(av, 2) / Math.max(0.5, r) * 0.7));
            } else continue;
            if (av > hw) continue;
            for (int y = bot; y <= top; y++) {
                String spec;
                boolean rimEdge = av > hw - 1.1;
                if (y == top) spec = rimEdge ? "chiseled_polished_blackstone" : au <= 13 ? (((int) Math.floor(u + 50) / 2 + (int) Math.floor(v + 50) / 2 & 1) == 0 ? "polished_deepslate" : "deepslate_tiles") : "polished_blackstone";
                else if (y == top - 1 && rimEdge && au <= 13) spec = "waxed_cut_copper";
                else spec = "polished_blackstone_bricks";
                c(x, y, z, spec);
            }
        }
        // the waist under the face, and the foot under that
        for (int x = -16; x <= 0; x++) for (int z = -12; z <= 12; z++) {
            if (!w.canon(x, z)) continue;
            double u = along(x, z, ANVIL_ANG), v = across(x, z, ANVIL_ANG), au = Math.abs(u), av = Math.abs(v);
            if (au <= 7.5 && av <= 6.5) for (int y = ANVIL - 8; y <= ANVIL - 3; y++) c(x, y, z, av > 5.5 || au > 6.5 ? "polished_blackstone_bricks" : "blackstone");
            double foot = Math.max(au / 11.5, av / 8.5);
            if (foot <= 1) for (int y = ANVIL - 12; y <= ANVIL - 9; y++) {
                boolean chamfer = y == ANVIL - 9 && foot > 0.82 || y == ANVIL - 12 && foot > 0.82;
                if (!chamfer) c(x, y, z, y == ANVIL - 9 ? "polished_blackstone_bricks" : (y == ANVIL - 11 && foot > 0.9 ? "waxed_cut_copper" : "blackstone"));
            }
        }
        // the hot work: a ring of glowing iron round the hardy hole (the Flue's head)
        for (int x = -8; x <= 0; x++) for (int z = -8; z <= 8; z++) {
            double d = Math.hypot(x, z);
            if (w.canon(x, z) && d > 6.0 && d < 7.4) c(x, ANVIL, z, d < 6.7 ? "magma_block" : "chiseled_polished_blackstone");
        }
        // lava running off its horns: wherever a drop falls clear into the sea or the void
        int n = 0;
        for (int x = -32; x <= 0 && n < 3; x++) for (int z = -16; z <= 16 && n < 3; z++) {
            if (!w.canon(x, z) || !w.type(x, ANVIL, z).name().startsWith("POLISHED_BLACKSTONE")) continue;
            double u = along(x, z, ANVIL_ANG);
            if (Math.abs(u) < 14 || Math.abs(u) > 24 || (Math.abs((int) Math.round(u)) % 5) != 0) continue;
            for (int[] o : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                if (!w.air(x + o[0], ANVIL, z + o[1]) || !fallsClear(x + o[0], ANVIL, z + o[1])) continue;
                spill(x, ANVIL, z, o[0], o[1]);
                n++;
                break;
            }
        }
        keepClear(-31, -14, 31, 14);
    }

    /**
     * A great smith's hammer lying across each side of the Anvil's face (each the other's copy): a head of dark
     * iron, a haft of dark oak with a bound grip. Cover for whoever holds the hill.
     */
    void hammers() {
        double ux = Math.cos(ANVIL_ANG), uz = Math.sin(ANVIL_ANG), vx = -Math.sin(ANVIL_ANG), vz = Math.cos(ANVIL_ANG);
        Map<Long, String> cells = new HashMap<>();
        for (double v = -7.5; v <= 3.5; v += 0.25) {
            int x = (int) Math.round(8.5 * ux + v * vx), z = (int) Math.round(8.5 * uz + v * vz);
            cells.put(key(x, 0, z), Math.abs(v + 6) < 1 ? "stripped_dark_oak_log[axis=y]" : "dark_oak_log[axis=y]");
        }
        for (double u = 7; u <= 10; u += 0.25) for (double v = 3.5; v <= 6.5; v += 0.25) {
            int x = (int) Math.round(u * ux + v * vx), z = (int) Math.round(u * uz + v * vz);
            cells.put(key(x, 0, z), "HEAD");
        }
        for (var e : cells.entrySet()) {
            int x = (int) ((e.getKey() >> 40) - 512), z = (int) ((e.getKey() & 0xFFFFF) - 512);
            if (Math.hypot(x, z) < 6.2) continue;                  // never over the Flue's head
            if (e.getValue().equals("HEAD")) {
                w.set(x, ANVIL + 1, z, "polished_blackstone");
                w.set(x, ANVIL + 2, z, (x + z & 1) == 0 ? "polished_blackstone_bricks" : "chiseled_polished_blackstone");
            } else w.set(x, ANVIL + 1, z, e.getValue().replace("[axis=y]", "[axis=x]"));
        }
    }

    // ================================================================ the Flue

    /**
     * The Flue: a round chimney from the Crucible's islet up through the Forge to the Anvil's face. Inside, two
     * stairs wind up round each other (each the other turned half round), with doors onto the islet, the Forge
     * and the Anvil.
     */
    void flue() {
        double r0 = 1.3, r1 = 4.4, rw = 5.4;
        int base = RIM, top = ANVIL;
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) {
            double d = Math.hypot(x, z);
            if (d > rw + 0.5) continue;
            boolean wall = d > r1 + 0.45;
            for (int y = base; y <= top; y++) {
                String spec;
                if (y == base) spec = "polished_blackstone";
                else if (wall) spec = y == top ? "polished_blackstone_bricks" : (y % 7 == 3 ? "chiseled_polished_blackstone" : (x * 3 + z + y) % 11 == 0 ? "magma_block" : "polished_blackstone_bricks");
                else if (y == top) spec = "air";
                else spec = d <= r0 ? (y % 5 == 0 ? "shroomlight" : "polished_basalt[axis=y]") : "air";
                w.set(x, y, z, spec);
            }
        }
        // two stairs, each half a turn round from the other
        double rm = (r0 + r1) / 2, k = 0.5 * rm;
        int walk0 = base + 1;
        double thetaEnd = Math.PI * 0.75;
        double tb = thetaEnd - (top + 1 - walk0) / k;
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) {
            double d = Math.hypot(x, z);
            if (d < r0 + 0.3 || d > r1 + 0.45) continue;
            double phi = Math.atan2(z, x);
            double th = phi + 2 * Math.PI * Math.ceil((tb - phi) / (2 * Math.PI));
            for (; th <= thetaEnd + 1e-9; th += 2 * Math.PI) {
                double q = Math.max(walk0, Math.round((walk0 + k * (th - tb)) * 2) / 2.0);
                tread(x, z, q, "polished_blackstone_bricks", "polished_blackstone_brick_slab");
            }
        }
        // doors: where each stair passes the islet and the Forge's deck (its copy cuts the other stair's)
        for (int lvl : new int[] {RIM + 1, MID + 1}) doorway(0, 0, tb + (lvl - walk0) / k, r1 - 0.4, rw + 0.6, lvl, 1.6);
        keepClear(-7, -7, 7, 7);
    }

    /** A doorway through a round wall: every cell of the ring within half-width hw of the line out at angle a, three high. */
    void doorway(double cx, double cz, double a, double rin, double rout, int y, double hw) {
        double ux = Math.cos(a), uz = Math.sin(a);
        for (int x = (int) Math.floor(cx - rout - 1); x <= (int) Math.ceil(cx + rout + 1); x++)
            for (int z = (int) Math.floor(cz - rout - 1); z <= (int) Math.ceil(cz + rout + 1); z++) {
                double dx = x - cx, dz = z - cz, along = dx * ux + dz * uz, across = Math.abs(-dx * uz + dz * ux);
                if (along < rin || along > rout || across > hw) continue;
                for (int dy = 0; dy <= 2; dy++) w.set(x, y + dy, z, "air");
            }
    }

    // ================================================================ the dragon

    /**
     * The dragon on the summit behind the hall: crouched, hind feet on the crest, forefeet gripping the top of the
     * gate's face, wings raised; its neck arches out over the courtyard and from its jaws lava pours down past the
     * courtyard's edge into the pool on the quay. Built at the west mountain; its copy holds the east.
     */
    void dragon() {
        int ox = -115, oy = 106, oz = 0;          // local (u, f, v): u = z, f = toward the map (+x), v = up
        double hf = 13.5, hv = 9.6;               // where the head sits relative to the old head's frame
        List<double[]> neck = new ArrayList<>();
        double[] p0 = {0, 1.5, 15.5}, p1 = {0, 5, 23}, p2 = {0, 11, 23.5}, p3 = {0, 1.5 + hf, 9.4 + hv};
        for (int i = 0; i <= 30; i++) {
            double t = i / 30.0, a = (1 - t) * (1 - t) * (1 - t), b = 3 * (1 - t) * (1 - t) * t, c = 3 * (1 - t) * t * t, d = t * t * t;
            neck.add(new double[] {0, a * p0[1] + b * p1[1] + c * p2[1] + d * p3[1], a * p0[2] + b * p1[2] + c * p2[2] + d * p3[2], 3.8 - 1.0 * t});
        }
        List<double[]> caps = new ArrayList<>();
        for (int i = 0; i + 1 < neck.size(); i++) {
            double[] q = neck.get(i), r = neck.get(i + 1);
            caps.add(new double[] {q[0], q[1], q[2], q[3], r[0], r[1], r[2], r[3], 1});
        }
        // the head (as in the old frame, moved by hf, hv)
        caps.add(new double[] {0, 5.5 + hf, 10.6 + hv, 3.0, 0, 12.8 + hf, 9.0 + hv, 1.7, 0});
        caps.add(new double[] {0, 4.5 + hf, 7.2 + hv, 2.0, 0, 12.0 + hf, 4.0 + hv, 1.2, 1});
        // the body, leaning forward from the hips to the chest
        caps.add(new double[] {0, -6, 7, 5.0, 0, 1, 14, 5.2, 1});
        // the tail, curling down the mountain's back and round to the side
        double[][] tail = {{0, -8, 4, 3.6}, {0, -9, 0, 3.0}, {4, -9, -3, 2.4}, {8, -7, -5, 1.8}, {11, -4, -6, 1.2}, {12, 0, -6, 0.7}};
        for (int i = 0; i + 1 < tail.length; i++) caps.add(new double[] {tail[i][0], tail[i][1], tail[i][2], tail[i][3], tail[i + 1][0], tail[i + 1][1], tail[i + 1][2], tail[i + 1][3], 1});
        for (int s : new int[] {-1, 1}) {
            // horns sweeping back, spikes off the cheeks
            caps.add(new double[] {s * 2.2, 2.0 + hf, 13.6 + hv, 1.3, s * 3.8, -2.0 + hf, 17.2 + hv, 0.95, 2});
            caps.add(new double[] {s * 3.8, -2.0 + hf, 17.2 + hv, 0.95, s * 4.6, -6.5 + hf, 18.4 + hv, 0.4, 2});
            caps.add(new double[] {s * 3.4, 2.5 + hf, 9.8 + hv, 0.85, s * 6.0, -0.4 + hf, 10.6 + hv, 0.6, 2});
            // hind legs crouched on the crest
            caps.add(new double[] {s * 4.5, -5, 6, 2.6, s * 7.5, -1, 4.5, 2.0, 0});
            caps.add(new double[] {s * 7.5, -1, 4.5, 1.9, s * 6.5, -5, 1.2, 1.4, 0});
            caps.add(new double[] {s * 6.5, -5, 1.2, 1.2, s * 6.5, -1, 0.6, 0.9, 0});
            for (double o : new double[] {-1.2, 0, 1.2}) caps.add(new double[] {s * 6.5 + o, -1, 0.6, 0.5, s * 6.5 + o * 1.4, 1.4, 0.2, 0.35, 2});
            // forelegs reaching down to grip the top of the gate's face
            caps.add(new double[] {s * 5, 2, 13, 2.3, s * 9, 5, 6, 1.9, 0});
            caps.add(new double[] {s * 9, 5, 6, 1.9, s * 7.5, 6.5, -4, 1.5, 0});
            caps.add(new double[] {s * 7.5, 6.5, -4, 1.4, s * 7.5, 7.5, -6.5, 1.1, 0});
            for (double o : new double[] {-1.3, 0, 1.3}) {
                caps.add(new double[] {s * 7.5 + o, 7.5, -6.5, 0.6, s * 7.5 + o * 1.4, 9.0, -7.0, 0.5, 0});
                caps.add(new double[] {s * 7.5 + o * 1.4, 9.0, -7.0, 0.45, s * 7.5 + o * 1.5, 9.6, -8.8, 0.35, 2});
            }
            // the wing: arm and fingers, raised
            double[] root = {s * 5, -3, 12}, elbow = {s * 13, -5, 21}, wrist = {s * 20, -6, 27};
            caps.add(new double[] {root[0], root[1], root[2], 1.2, elbow[0], elbow[1], elbow[2], 0.9, 3});
            caps.add(new double[] {elbow[0], elbow[1], elbow[2], 0.9, wrist[0], wrist[1], wrist[2], 0.7, 3});
            caps.add(new double[] {wrist[0], wrist[1], wrist[2], 0.6, s * 21, -6, 29.5, 0.35, 2});
            for (double[] tip : wingTips(s)) caps.add(new double[] {wrist[0], wrist[1], wrist[2], 0.5, tip[0], tip[1], tip[2], 0.3, 3});
        }
        List<double[]> ells = new ArrayList<>();
        ells.add(new double[] {0, 3.4 + hf, 11.0 + hv, 4.0, 4.4, 3.4, 0});    // the skull
        ells.add(new double[] {0, -3, 10, 5.6, 6.2, 6.4, 1});                  // the chest
        for (int s : new int[] {-1, 1}) ells.add(new double[] {s * 2.8, 6.0 + hf, 13.2 + hv, 1.5, 2.2, 1.0, 0});    // brows
        List<double[][]> tris = new ArrayList<>();
        for (int s : new int[] {-1, 1}) {
            double[] root = {s * 5, -3, 12}, elbow = {s * 13, -5, 21}, wrist = {s * 20, -6, 27};
            double[][] tip = wingTips(s);
            for (int i = 0; i + 1 < tip.length; i++) tris.add(new double[][] {wrist, tip[i], tip[i + 1], {1}});
            tris.add(new double[][] {elbow, wrist, tip[tip.length - 1], {0}});
            tris.add(new double[][] {root, elbow, tip[tip.length - 1], {0}});
            tris.add(new double[][] {root, tip[tip.length - 1], {s * 4.5, -5, 6}, {0}});
        }

        P scales = P.of("deepslate_tiles", 6, "cracked_deepslate_tiles", 1, "polished_blackstone", 1);
        Map<Long, String> body = new HashMap<>();
        for (int u = -34; u <= 34; u++) for (int f = -10; f <= 30; f++) for (int v = -12; v <= 32; v++) {
            double best = 0.0;
            int kind = -1;
            double[] axis = null;
            for (double[] c : caps) {
                double[] q = new double[3];
                double d = capsule(u, f, v, c, q);
                if (d < best) {
                    best = d;
                    kind = (int) c[8];
                    axis = q;
                }
            }
            for (double[] e : ells) {
                double x = (u - e[0]) / e[3], y = (f - e[1]) / e[4], z = (v - e[2]) / e[5];
                double d = (Math.sqrt(x * x + y * y + z * z) - 1) * Math.min(e[3], Math.min(e[4], e[5]));
                if (d < best) {
                    best = d;
                    kind = (int) e[6];
                    axis = new double[] {e[0], e[1], e[2]};
                }
            }
            String spec = null;
            if (kind >= 0) {
                if (kind == 2) spec = "bone_block";
                else if (kind == 3) spec = "polished_blackstone";
                else if (best < -1.6) spec = "blackstone";
                else if (kind == 1) {
                    double du = u - axis[0], df = f - axis[1], dv = v - axis[2];
                    double len = Math.max(0.01, Math.sqrt(du * du + df * df + dv * dv));
                    double belly = (df * 0.62 - dv * 0.78) / len;
                    spec = belly > 0.5 ? (Math.floorMod(v, 3) == 0 ? "nether_bricks" : "red_nether_bricks") : scales.pick(w.r);
                } else spec = scales.pick(w.r);
            } else {
                for (double[][] tri : tris) if (membrane(u, f, v, tri)) { spec = "red_stained_glass"; break; }
            }
            if (spec != null) body.put(key(u, f, v), spec);
        }
        int H = (int) Math.round(hf), V = (int) Math.round(hv);
        // carve the mouth between the jaws, light its throat, set the teeth
        for (int f = 5; f <= 14; f++) for (int u = -1; u <= 1; u++) {
            double top = mouthTop(f), bot = mouthBottom(f);
            for (int v = (int) Math.ceil(bot); v <= (int) Math.floor(top); v++) {
                body.remove(key(u, f + H, v + V));
                if (f <= 6) body.put(key(u, f + H, v + V), u == 0 ? "shroomlight" : "magma_block");
            }
            if (f >= 7 && u == 0) body.put(key(0, f + H, (int) Math.ceil(bot) - 1 + V), "magma_block");
        }
        for (int f = 7; f <= 12; f += 2) for (int s : new int[] {-1, 1}) {
            int vt = (int) Math.floor(mouthTop(f)), vb = (int) Math.ceil(mouthBottom(f));
            if (body.containsKey(key(s, f + H, vt + 1 + V))) body.put(key(s, f + H, vt + V), "pointed_dripstone[vertical_direction=down,thickness=tip]");
            if (vt - vb >= 1 && body.containsKey(key(s, f + H, vb - 1 + V))) body.put(key(s, f + H, vb + V), "pointed_dripstone[vertical_direction=up,thickness=tip]");
        }
        for (int s : new int[] {-1, 1}) for (int v = 12; v <= 13; v++) {
            for (int u = s * 6; u != 0; u -= s) {
                if (!body.containsKey(key(u, 7 + H, v + V))) continue;
                body.put(key(u, 7 + H, v + V), "ochre_froglight");
                break;
            }
        }
        for (int s : new int[] {-1, 1}) {
            int f = 12 + H, u = s;
            int v = 15 + V;
            while (v > 5 + V && !body.containsKey(key(u, f, v))) v--;
            body.put(key(u, f, v), "campfire[lit=true,signal_fire=true]");
            body.put(key(u, f, v - 1), "hay_block");
        }
        for (int i = 4; i < neck.size() - 4; i += 3) {
            double[] q = neck.get(i), r = neck.get(i + 1);
            boolean upright = Math.abs(r[2] - q[2]) > Math.abs(r[1] - q[1]);
            int f = (int) Math.round(q[1]), v = (int) Math.round(q[2]);
            for (int k2 = 0; k2 < 8; k2++) {
                long kk = upright ? key(0, f - k2, v) : key(0, f, v + k2);
                if (!body.containsKey(kk)) {
                    body.put(kk, "red_nether_brick_wall");
                    break;
                }
            }
        }
        // spines down the back
        for (int f = -10; f <= -2; f += 2) {
            for (int v = 25; v > 0; v--) {
                if (body.containsKey(key(0, f, v))) {
                    body.put(key(0, f, v + 1), "red_nether_brick_wall");
                    break;
                }
            }
        }
        for (var e : body.entrySet()) {
            int u = (int) ((e.getKey() >> 40) - 512), f = (int) (((e.getKey() >> 20) & 0xFFFFF) - 512), v = (int) ((e.getKey() & 0xFFFFF) - 512);
            int x = ox + f, y = oy + v, z = oz + u;
            if (!w.air(x, y, z) && lo.topAt(x, z) >= y && !e.getValue().equals("bone_block")) continue;   // never into the mountain
            w.set(x, y, z, e.getValue());
        }
        // the lava from its jaws, down past the courtyard's edge into the pool on the quay
        // (it drools from the front of the lower jaw)
        int mx = -88, my = oy + 1 + (int) Math.round(hv + 4.0);
        while (my < oy + 30 && !w.air(mx, my, 0)) my++;
        w.set(mx, my, 0, "lava");
        for (int y = my - 1; y > QUAY; y--) if (w.air(mx, y, 0)) w.set(mx, y, 0, "lava[level=8]");
    }

    static double[][] wingTips(int s) {
        return new double[][] {{s * 31, -6, 24}, {s * 30, -8, 14}, {s * 25, -8, 6}, {s * 15, -7, 3}};
    }

    double mouthTop(double f) {
        double t = Math.max(0, Math.min(1, (f - 5.5) / 7.3));
        return 10.6 - 1.6 * t - (3.0 - 1.3 * t);
    }

    double mouthBottom(double f) {
        double t = Math.max(0, Math.min(1, (f - 4.5) / 7.5));
        return 7.2 - 3.2 * t + (2.0 - 0.8 * t);
    }

    static long key(int u, int f, int v) {
        return ((long) (u + 512) << 40) | ((long) (f + 512) << 20) | (v + 512);
    }

    static double capsule(double u, double f, double v, double[] c, double[] q) {
        double bx = c[4] - c[0], by = c[5] - c[1], bz = c[6] - c[2];
        double px = u - c[0], py = f - c[1], pz = v - c[2];
        double l2 = bx * bx + by * by + bz * bz;
        double t = l2 == 0 ? 0 : Math.max(0, Math.min(1, (px * bx + py * by + pz * bz) / l2));
        q[0] = c[0] + bx * t;
        q[1] = c[1] + by * t;
        q[2] = c[2] + bz * t;
        double dx = u - q[0], dy = f - q[1], dz = v - q[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz) - (c[3] + (c[7] - c[3]) * t);
    }

    static boolean membrane(double u, double f, double v, double[][] tri) {
        double[] a = tri[0], b = tri[1], c = tri[2];
        double e1x = b[0] - a[0], e1y = b[1] - a[1], e1z = b[2] - a[2];
        double e2x = c[0] - a[0], e2y = c[1] - a[1], e2z = c[2] - a[2];
        double nx = e1y * e2z - e1z * e2y, ny = e1z * e2x - e1x * e2z, nz = e1x * e2y - e1y * e2x;
        double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (nl == 0) return false;
        nx /= nl;
        ny /= nl;
        nz /= nl;
        double px = u - a[0], py = f - a[1], pz = v - a[2];
        double dist = px * nx + py * ny + pz * nz;
        if (Math.abs(dist) > 0.62) return false;
        double qx = px - dist * nx, qy = py - dist * ny, qz = pz - dist * nz;
        double d00 = e1x * e1x + e1y * e1y + e1z * e1z, d01 = e1x * e2x + e1y * e2y + e1z * e2z, d11 = e2x * e2x + e2y * e2y + e2z * e2z;
        double d20 = qx * e1x + qy * e1y + qz * e1z, d21 = qx * e2x + qy * e2y + qz * e2z;
        double den = d00 * d11 - d01 * d01;
        double bb = (d11 * d20 - d01 * d21) / den, cc = (d00 * d21 - d01 * d20) / den, aa = 1 - bb - cc;
        if (aa < -0.02 || bb < -0.02 || cc < -0.02) return false;
        if (tri[3][0] > 0 && bb + cc > 0.01) {
            double along = cc / (bb + cc);
            if (aa < 0.14 * Math.sin(Math.PI * along)) return false;
        }
        return true;
    }

    // ================================================================ small things on the Slag Fields

    void organPipes(int cx, int cz, int h) {
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            double d = Math.hypot(dx, dz * 1.2);
            if (d > 2.8) continue;
            int x = cx + dx, z = cz + dz;
            if (lo.topAt(x, z) != LOW || riverD(x, z) < 2) continue;
            int hh = Math.max(1, (int) Math.round(h - d * 1.3 + w.r.nextInt(3) - 1));
            for (int dy = 1; dy <= hh; dy++) w.set(x, LOW + dy, z, dy == hh ? "polished_basalt[axis=y]" : "basalt[axis=y]");
        }
        keepClear(cx - 3, cz - 3, cx + 3, cz + 3);
    }

    void vent(int cx, int cz) {
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            double d = Math.hypot(dx, dz);
            int x = cx + dx, z = cz + dz;
            if (d > 2.4 || lo.topAt(x, z) != LOW) continue;
            w.set(x, LOW, z, d < 0.9 ? "lava" : d < 1.6 ? "magma_block" : w.r.nextBoolean() ? "obsidian" : "blackstone");
            if (d < 0.9) w.set(x, LOW - 1, z, "magma_block");
        }
        int sx = cx + 2, sz = cz + 2;
        if (lo.topAt(sx, sz) == LOW) {
            w.set(sx, LOW, sz, "hay_block");
            w.set(sx, LOW + 1, sz, "campfire[lit=true,signal_fire=true]");
            for (int[] o : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) for (int dy = 1; dy <= 2; dy++)
                if (lo.topAt(sx + o[0], sz + o[1]) == LOW) w.set(sx + o[0], LOW + dy, sz + o[1], dy == 2 ? "basalt[axis=y]" : "polished_basalt[axis=y]");
        }
        keepClear(cx - 3, cz - 3, cx + 3, cz + 3);
    }

    void boulder(int cx, int cz) {
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            double e = Math.pow(dx / 2.4, 2) + Math.pow(dz / 1.8, 2);
            int x = cx + dx, z = cz + dz;
            if (e > 1 || lo.topAt(x, z) != LOW) continue;
            int h = (int) Math.round((1 - e) * 2.6 + 0.4);
            for (int dy = 1; dy <= h; dy++) w.set(x, LOW + dy, z, w.n3(x, LOW + dy, z, 2) > 0 ? "obsidian" : "basalt[axis=y]");
        }
        keepClear(cx - 3, cz - 3, cx + 3, cz + 3);
    }

    void charred(int cx, int cz) {
        if (lo.topAt(cx, cz) != LOW) return;
        int h = 4 + w.r.nextInt(3);
        for (int dy = 1; dy <= h; dy++) w.set(cx, LOW + dy, cz, "dark_oak_log[axis=y]");
        for (int b = 0; b < 3; b++) {
            double a = w.r.nextDouble() * Math.PI * 2;
            int px = cx, py = LOW + h - w.r.nextInt(2), pz = cz;
            int tx = (int) Math.round(cx + Math.cos(a) * 2.6), ty = py + 1 + w.r.nextInt(2), tz = (int) Math.round(cz + Math.sin(a) * 2.6);
            while (px != tx || py != ty || pz != tz) {
                if (px != tx) px += Integer.signum(tx - px);
                else if (pz != tz) pz += Integer.signum(tz - pz);
                else py += Integer.signum(ty - py);
                w.set(px, py, pz, "dark_oak_wood");
            }
        }
        keepClear(cx - 3, cz - 3, cx + 3, cz + 3);
    }
}
