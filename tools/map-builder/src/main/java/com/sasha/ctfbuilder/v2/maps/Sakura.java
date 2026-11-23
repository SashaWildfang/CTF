package com.sasha.ctfbuilder.v2.maps;

import com.sasha.ctfbuilder.v2.Arena;
import com.sasha.ctfbuilder.v2.Kit;
import com.sasha.ctfbuilder.v2.Land;
import com.sasha.ctfbuilder.v2.Theme;
import com.sasha.ctfbuilder.v2.W.P;
import org.bukkit.World;

/**
 * Sakura Isles: a stroll garden seen from above. Every island stands at the same height; nothing joins them, so
 * players build every crossing.
 *
 * The garden is square and the two temples sit in opposite corners, so the map is played along its diagonal. Each
 * temple stands on a blossom: an island shaped like a five-petalled cherry flower, the flag at its heart among
 * the stamens, the hall on the back petal, a pagoda and a bell tower on the others, a torii at the tip of each
 * front petal. The other two corners hold pagoda courts, each as far from one temple as from the other (North
 * Pagoda and South Pagoda are hills).
 *
 * Three ways across. Straight down the diagonal, stepping stones lead over the pond to Hōrai, the island of the
 * immortals in the middle, with its moon-viewing stage (a hill) under two great cherries: short hops, wide open.
 * Round the edges, two garden walks: from each temple one way passes a tunnel of torii gates and a raked
 * stone garden, the other a bamboo grove on a leaf-shaped island and a pond garden on an island shaped like a
 * koi, each walk passing a pagoda corner on its way to the other temple. From the koi, the crane island cuts in
 * towards the middle.
 */
public final class Sakura extends Arena {

    static final int Y = 68;
    static final int HX = -66, HZ = -66, CX = -66, CZ = 66;          // the blossom's heart; the pagoda court
    static final double FACE = Math.toRadians(45);
    static final double[] PETALS = {81, 153, 225, 297, 9};           // the petals' bearings (degrees)
    static final double[][] STONES = {{-40.5, -40.5}, {-29.1, -29.1}, {-17.6, -17.6}, {-17.6, 17.6}};

    enum Isle { HOME, CORNER, LEAF, KOI, TORII, ZEN, HORAI, STONE, CRANE }

    Land land;

    public Sakura(World world) {
        super(world, "sakura", false, Theme.get("sakura"));
        name = "Sakura Isles";
        icon = "CHERRY_SAPLING";
        minutes = 15;
        kothPoints = 250;
        reach = 104;
        low = 20;
        high = 130;
        voidY = 20;
        spectator = new double[] {0.5, 116, 0.5, -45, 55};
    }

    // ================================================================ shapes (> 0 inside)

    static double[] rot(double dx, double dz, double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new double[] {dx * c + dz * s, -dx * s + dz * c};
    }

    /** A cherry petal pointing out at bearing a from (cx, cz): an egg, wider toward its tip, with a V notch in the tip. */
    static double petal(double x, double z, double cx, double cz, double a, double d, double len, double wid, double notch) {
        double[] uv = rot(x - cx, z - cz, a);
        double u = uv[0] - d, v = uv[1];
        double b = wid * (1 + 0.25 * Math.max(-1, Math.min(1, u / len)));
        if (u > len - notch && Math.abs(v) < (u - (len - notch)) * 1.1) return -1;
        return 1 - Math.hypot(u / len, v / b);
    }

    /** A five-petalled blossom round (cx, cz), facing bearing face (the notch between the front petals). */
    static double blossom(double x, double z, double cx, double cz, double face, double disc, double d, double len, double wid, double notch) {
        double best = 1 - Math.hypot(x - cx, z - cz) / disc;
        for (int k = 0; k < 5; k++) best = Math.max(best, petal(x, z, cx, cz, face + Math.toRadians(36 + 72 * k), d, len, wid, notch));
        return best;
    }

    /** A koi from above: a fat body tapering to a forked tail, pectoral fins behind the head; heading a. */
    static double koi(double x, double z, double cx, double cz, double a, double len, double wid) {
        double[] uv = rot(x - cx, z - cz, a);
        double u = uv[0], v = uv[1], t = (u + len / 2) / len;
        double best = -9;
        if (t >= 0 && t <= 1) {
            double f = t < 0.7 ? 0.24 + 0.76 * Math.pow(Math.sin(Math.PI / 2 * t / 0.7), 0.8) : Math.sqrt(Math.max(0, 1 - Math.pow((t - 0.7) / 0.3, 2)));
            best = 1 - Math.abs(v) / Math.max(0.1, wid / 2 * f);
        }
        for (int s = -1; s <= 1; s += 2) {
            double[] tu = rot(u + len / 2 + 3.0, v - s * 2.3, s * 0.5);
            best = Math.max(best, 1 - Math.hypot(tu[0] / 5.4, tu[1] / 2.5));
            double[] fu = rot(u - len * 0.12, v - s * (wid / 2 + 0.4), -s * 0.7);
            best = Math.max(best, 1 - Math.hypot(fu[0] / 3.6, fu[1] / 1.8));
        }
        return best;
    }

    /** A bamboo leaf from (ax, az) to (bx, bz): long and pointed at both ends, widest nearer its base. */
    static double leaf(double x, double z, double ax, double az, double bx, double bz, double wid) {
        double vx = bx - ax, vz = bz - az, l = Math.hypot(vx, vz);
        double t = ((x - ax) * vx + (z - az) * vz) / (l * l);
        if (t < 0 || t > 1) return -1;
        double d = Math.abs(((x - ax) * vz - (z - az) * vx) / l);
        double half = wid * Math.pow(Math.sin(Math.PI * Math.pow(t, 0.8)), 0.7);
        return 1 - d / Math.max(0.1, half);
    }

    /** A crane flying, from above: slim body and neck, broad wings swept back, tail feathers; heading a. */
    static double crane(double x, double z, double cx, double cz, double a) {
        double[] uv = rot(x - cx, z - cz, a);
        double u = uv[0], v = uv[1];
        double best = 1 - Math.hypot(u / 9.0, v / 2.4);
        best = Math.max(best, 1 - Math.hypot((u - 9.5) / 2.2, v / 1.7));
        for (int s = -1; s <= 1; s += 2) {
            double[] wv = rot(u - 1.0, v - s * 7.5, s * 0.35);
            best = Math.max(best, 1 - Math.hypot(wv[0] / 4.2, wv[1] / 7.0));
        }
        return Math.max(best, 1 - Math.hypot((u + 9) / 3.0, v / 3.2));
    }

    static double octagon(double x, double z, double cx, double cz, double r) {
        double dx = Math.abs(x - cx), dz = Math.abs(z - cz);
        return 1 - Math.max(Math.max(dx, dz), (dx + dz) * 0.7071) / r;
    }

    static double chain(double x, double z, double[][] pts, double[] wid) {
        double best = -9;
        for (int i = 0; i + 1 < pts.length; i++) {
            double ax = pts[i][0], az = pts[i][1], vx = pts[i + 1][0] - ax, vz = pts[i + 1][1] - az;
            double t = Math.max(0, Math.min(1, ((x - ax) * vx + (z - az) * vz) / (vx * vx + vz * vz)));
            double d = Math.hypot(x - ax - vx * t, z - az - vz * t), ww = wid[i] + (wid[i + 1] - wid[i]) * t;
            best = Math.max(best, 1 - d / ww);
        }
        return best;
    }

    static final double[][] TORII = {{-22.5, -71}, {-14, -73}, {-5.5, -71}};
    static final double[] TORII_W = {4.5, 5.5, 4.5};

    double jag(int x, int z, double amp) {
        return w.fbm(x * 0.9 + 31, z * 0.9 - 17, 5, 2) * amp;
    }

    double inside(Isle i, int x, int z) {
        return switch (i) {
            case HOME -> blossom(x, z, HX, HZ, FACE, 12, 18.5, 11, 8.8, 3.5) + jag(x, z, 0.03);
            case CORNER -> octagon(x, z, CX, CZ, 14);
            case LEAF -> leaf(x, z, -72, -27, -67, 1, 8) + jag(x, z, 0.06);
            case KOI -> koi(x, z, -69, 26, Math.toRadians(-90), 19, 14) + jag(x, z, 0.03);
            case TORII -> chain(x, z, TORII, TORII_W) + jag(x, z, 0.05);
            case ZEN -> 1 - Math.pow(Math.pow(Math.abs(x + 25.5) / 16, 2.6) + Math.pow(Math.abs(z - 69) / 8.5, 2.6), 1 / 2.6) + jag(x, z, 0.03);
            case HORAI -> 1 - Math.hypot(x, z) / 11.6 + jag(x, z, 0.03);
            case STONE -> {
                double best = -9;
                for (double[] s : STONES) best = Math.max(best, 1 - Math.hypot(x - s[0], z - s[1]) / 3.3);
                yield best;
            }
            case CRANE -> crane(x, z, -35, 37, Math.toRadians(-45));
        };
    }

    /** The island at (x, z), or null. Hōrai is its own copy; everything else is built in the west half and turned. */
    Isle isle(int x, int z) {
        if (inside(Isle.HORAI, x, z) > 0) return Isle.HORAI;
        if (x > 0 || x == 0 && z > 0) {
            x = -x;
            z = -z;
        }
        for (Isle i : Isle.values()) if (i != Isle.HORAI && inside(i, x, z) > 0) return i;
        return null;
    }

    /** (x, z) in the west half's frame. */
    static int[] canon(int x, int z) {
        return x > 0 || x == 0 && z > 0 ? new int[] {-x, -z} : new int[] {x, z};
    }

    // ================================================================ the land

    double height(int x, int z) {
        return isle(x, z) == null ? Double.NaN : Y;
    }

    String surface(int x, int z, int y, int steep, double edge) {
        double n = w.fbm(x + 913, z - 377, 9, 2), fine = w.n2(x * 1.9, z * 1.9, 2);
        Isle i = isle(x, z);
        if (i == Isle.STONE) return fine > 0.2 ? "moss_block" : fine < -0.3 ? "mossy_cobblestone" : "stone";
        if (edge < 1.3) return fine > 0.3 ? "moss_block" : "grass_block";
        if (n > 0.38) return "moss_block";
        if (n < -0.45 && fine > 0) return "podzol";
        return "grass_block";
    }

    /** The underside: tapering from the rim; the stepping stones stand on tall stalks of rock. */
    int bottom(int x, int z, int top, double edge) {
        Isle i = isle(x, z);
        double size, depth;
        switch (i == null ? Isle.STONE : i) {
            case HOME -> { size = 11; depth = 30; }
            case CORNER, HORAI -> { size = 11; depth = 26; }
            case ZEN, LEAF, KOI -> { size = 6.5; depth = 18; }
            case TORII -> { size = 4.5; depth = 14; }
            case CRANE -> { size = 3.5; depth = 11; }
            default -> { size = 3.0; depth = 16; }
        }
        double e = Math.min(1, edge / (size * 0.9)), lens = 1 - Math.pow(1 - e, 1.8);
        double teeth = 2.5 * Math.max(0, w.fbm(x * 1.6 + 9, z * 1.6, 3, 1)) * Math.min(1, edge / 3.0);
        return (int) Math.round(top - 2 - depth * lens - teeth + w.fbm(x, z, 5, 2) * 1.4 * Math.min(1, edge / 4.0));
    }

    // ================================================================ build

    @Override
    protected void build() {
        land = new Land(w, t, -100, 100, -100, 100);
        land.top = this::height;
        land.surface = this::surface;
        land.bottom = this::bottom;
        land.build();

        blossomHome();
        toriiWalk();
        zenGarden();
        bambooLeaf();
        koiGarden();
        pagodaCourt();
        craneIsle();
        horai();
        steppingStones();

        // cherries over the gardens, petals under them
        trees(new Theme.Tree[] {Theme.Tree.CHERRY}, new int[] {1}, 0.03, 7, 2, (x, z) -> {
            Isle i = isle(x, z);
            int[] c = canon(x, z);
            return (i == Isle.HOME || i == Isle.CORNER) && inside(i, c[0], c[1]) > 0.18;
        });
        plants(P.of("pink_petals[flower_amount=4]", 6, "pink_petals[flower_amount=2]", 3, "short_grass", 4, "fern", 2, "lily_of_the_valley", 1, "allium", 1),
                0.26, (x, z) -> {
                    Isle i = isle(x, z);
                    return i == Isle.HOME || i == Isle.KOI || i == Isle.CORNER || i == Isle.HORAI || i == Isle.TORII;
                });

        spawn = new double[] {-78.5, Y + 1, -78.5, -45};
        flag = new int[] {HX, Y + 1, HZ};
        pads.add(new int[] {-56, Y + 1, -64});
        pads.add(new int[] {-64, Y + 1, -56});
        hills.add(new Hill("North Pagoda", 58, Y + 1, -64, 64, Y + 4, -58));
        hills.add(new Hill("Hōrai", -4, Y + 2, -4, 4, Y + 5, 4));
        hills.add(new Hill("South Pagoda", -64, Y + 1, 58, -58, Y + 4, 64));
    }

    // ================================================================ little things

    /** A stone lantern (tōrō): plinth, post, the light in its box, a cap. */
    void lantern(int x, int y, int z) {
        w.set(x, y + 1, z, "polished_andesite");
        w.set(x, y + 2, z, "andesite_wall");
        w.set(x, y + 3, z, "lantern[hanging=false]");
        w.set(x, y + 4, z, "andesite_slab[type=bottom]");
    }

    /**
     * A vermilion torii across a way: posts at (x0, z0) and (x1, z1) (in a line along x or z) on black feet, a tie
     * beam between them (big ones: with a strut up the middle), the top beam reaching past the posts under a black cap.
     */
    void torii(int x0, int z0, int x1, int z1, int y, boolean big) {
        boolean alongX = z0 == z1;
        int h = big ? 6 : 4;
        for (int[] p : new int[][] {{x0, z0}, {x1, z1}}) {
            w.set(p[0], y + 1, p[1], "polished_blackstone");
            for (int dy = 2; dy <= h; dy++) w.set(p[0], y + dy, p[1], "stripped_mangrove_log[axis=y]");
        }
        int a0 = alongX ? Math.min(x0, x1) : Math.min(z0, z1), a1 = alongX ? Math.max(x0, x1) : Math.max(z0, z1), m = (a0 + a1) / 2;
        for (int a = a0 - 1; a <= a1 + 1; a++) {
            int x = alongX ? a : x0, z = alongX ? z0 : a;
            if (big && a > a0 && a < a1) w.set(x, y + h - 2, z, "mangrove_planks");
            if (big && a == m) w.set(x, y + h - 1, z, "mangrove_planks");
            if (a >= a0 && a <= a1 || big) w.set(x, y + h, z, a == a0 || a == a1 ? "stripped_mangrove_log[axis=y]" : "mangrove_planks");
            w.set(x, y + h + 1, z, a == a0 - 1 || a == a1 + 1 ? "polished_blackstone_slab[type=bottom]" : "polished_blackstone");
        }
    }

    // ================================================================ the temple blossom

    double bearingOf(int x, int z) {
        return Math.toDegrees(Math.atan2(z - HZ, x - HX));
    }

    void blossomHome() {
        int y = Y;
        // the heart: a raked gravel court, stone ways out along each petal (the veins), the flag's dais in the middle
        for (int x = HX - 32; x <= HX + 32; x++) for (int z = HZ - 32; z <= HZ + 32; z++) {
            if (isle(x, z) != Isle.HOME) continue;
            double r = Math.hypot(x - HX, z - HZ);
            boolean vein = false;
            for (double b : PETALS) {
                double[] uv = rot(x - HX, z - HZ, Math.toRadians(b));
                if (uv[0] > 2 && uv[0] < 24 && Math.abs(uv[1]) < 1.6) vein = true;
            }
            String s = null;
            if (r < 2.6) s = "polished_andesite";
            else if (r < 3.6) s = "chiseled_stone_bricks";
            else if (r < 12) s = vein ? (((x + z) & 1) == 0 ? "polished_andesite" : "andesite")
                    : (Math.floorMod((int) Math.round(r * 1.4), 2) == 0 ? "gravel" : "light_gray_concrete_powder");
            else if (vein) s = ((x + z) & 1) == 0 ? "polished_andesite" : "andesite";
            if (s != null) w.set(x, y, z, s);
        }
        // the stamens round the flag: slender posts with golden tips, in the gaps between the ways
        for (int k = 0; k < 5; k++) {
            double b = Math.toRadians(PETALS[k] + 36);
            int x = HX + (int) Math.round(Math.cos(b) * 6), z = HZ + (int) Math.round(Math.sin(b) * 6);
            w.set(x, y + 1, z, "cherry_fence");
            w.set(x, y + 2, z, "cherry_fence");
            w.set(x, y + 3, z, "ochre_froglight");
        }
        hall(-78, -78);
        pagoda(-58, -82, 5);
        bellTower(-82, -58);
        // a great torii at the tip of each front petal, lanterns along the ways to them
        torii(-42, -65, -42, -59, y, true);
        torii(-65, -42, -59, -42, y, true);
        for (double b : new double[] {9, 81}) for (int r = 15; r <= 23; r += 8) for (int sd = -1; sd <= 1; sd += 2) {
            double a = Math.toRadians(b);
            int x = HX + (int) Math.round(Math.cos(a) * r - Math.sin(a) * 3 * sd), z = HZ + (int) Math.round(Math.sin(a) * r + Math.cos(a) * 3 * sd);
            if (isle(x, z) == Isle.HOME) lantern(x, y, z);
        }
        keepClear(HX - 3, HZ - 3, HX + 3, HZ + 3);
        keepClear(-44, -66, -40, -58);
        keepClear(-66, -44, -58, -40);
    }

    /**
     * The hall on the back petal, where the team spawns: a square hall under a pyramid roof of dark tiles (the
     * kind built to be walked round), white walls between dark posts, shoji, a veranda, open on the two sides
     * that face the flower's heart.
     */
    void hall(int cx, int cz) {
        int x0 = cx - 5, x1 = cx + 5, z0 = cz - 5, z1 = cz + 5, y = Y;
        for (int x = x0 - 1; x <= x1 + 1; x++) for (int z = z0 - 1; z <= z1 + 1; z++) {
            boolean edge = x == x0 - 1 || x == x1 + 1 || z == z0 - 1 || z == z1 + 1;
            w.set(x, y, z, edge ? "stone_bricks" : "dark_oak_planks");
            for (int dy = 1; dy <= 9; dy++) if (!w.air(x, y + dy, z)) w.set(x, y + dy, z, "air");
        }
        k.walls(x0, z0, x1, z1, y, 5, Kit.Bands.of(0, "dark_oak_planks", 1, "white_concrete", 5, "dark_oak_planks"), "dark_oak_log", 2,
                P.of("dark_oak_planks", 3, "stripped_dark_oak_wood", 1));
        for (int z = z0 + 1; z < z1; z++) for (int dy = 2; dy <= 3; dy++) if ((z - z0) % 2 == 1) w.set(x0, y + dy, z, "white_stained_glass_pane");
        for (int x = x0 + 1; x < x1; x++) for (int dy = 2; dy <= 3; dy++) if ((x - x0) % 2 == 1) w.set(x, y + dy, z0, "white_stained_glass_pane");
        // open toward the heart: three bays wide on the east and south sides
        for (int o = -2; o <= 2; o++) for (int dy = 1; dy <= 3; dy++) {
            w.set(x1, y + dy, cz + o, "air");
            w.set(cx + o, y + dy, z1, "air");
        }
        for (int o = -2; o <= 2; o++) {
            w.set(x1, y + 4, cz + o, "dark_oak_planks");
            w.set(cx + o, y + 4, z1, "dark_oak_planks");
        }
        k.hip(x0 - 1, z0 - 1, x1 + 1, z1 + 1, y + 6, 1, Kit.Roof.of("deepslate_tiles"), P.of("dark_oak_planks"));
        // tatami, the altar in the back corner, banners, lanterns
        for (int x = x0 + 1; x < x1; x++) for (int z = z0 + 1; z < z1; z++) {
            if (x == x0 + 1 && z <= z0 + 2 || z == z0 + 1 && x <= x0 + 2) continue;
            w.set(x, y + 1, z, ((x / 2 + z / 3) & 1) == 0 ? "lime_carpet" : "green_carpet");
        }
        w.set(x0 + 1, y + 1, z0 + 1, "dark_oak_planks");
        w.set(x0 + 1, y + 2, z0 + 1, "decorated_pot");
        w.set(x0 + 2, y + 1, z0 + 1, "candle[candles=3,lit=true]");
        w.set(x0 + 1, y + 1, z0 + 2, "candle[candles=3,lit=true]");
        w.set(x0 + 1, y + 4, cz, "TEAM_wall_banner[facing=east]");
        w.set(cx, y + 4, z0 + 1, "TEAM_wall_banner[facing=south]");
        w.hangLight(cx, y + 5, cz, "lantern");
        keepClear(x0 - 2, z0 - 2, x1 + 2, z1 + 2);
    }

    /** A pagoda: storeys stepping in, each under its own deep tiled roof, a spire of rings on top. */
    void pagoda(int cx, int cz, int storeys) {
        int y = Y;
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            w.set(cx + dx, y, cz + dz, Math.abs(dx) == 4 || Math.abs(dz) == 4 ? "stone_bricks" : "polished_andesite");
            for (int dy = 1; dy <= 40; dy++) if (!w.air(cx + dx, y + dy, cz + dz)) w.set(cx + dx, y + dy, cz + dz, "air");
        }
        int yy = y;
        for (int i = 0; i < storeys; i++) {
            int h = i < 2 ? 3 : 2, tall = i == 0 ? 4 : 3;
            k.walls(cx - h, cz - h, cx + h, cz + h, yy, tall, Kit.Bands.of(0, i == 0 ? "dark_oak_planks" : "white_concrete", tall, "dark_oak_planks"),
                    "stripped_mangrove_log", 0, P.of("dark_oak_planks"));
            for (int dy = 2; dy <= tall - 1; dy++) {
                w.set(cx, yy + dy, cz - h, "white_stained_glass_pane");
                w.set(cx, yy + dy, cz + h, "white_stained_glass_pane");
                w.set(cx - h, yy + dy, cz, "white_stained_glass_pane");
                w.set(cx + h, yy + dy, cz, "white_stained_glass_pane");
            }
            int roofY = yy + tall + 1;
            k.hip(cx - h, cz - h, cx + h, cz + h, roofY, 2, Kit.Roof.of("deepslate_tiles"), P.of("dark_oak_planks"));
            yy = roofY + 1;
            if (i + 1 < storeys) {
                int nh = i + 1 < 2 ? 3 : 2;
                for (int dx = -nh; dx <= nh; dx++) for (int dz = -nh; dz <= nh; dz++) w.set(cx + dx, yy, cz + dz, "dark_oak_planks");
            }
        }
        int top = w.top(cx, cz);
        for (int dy = 1; dy <= 6; dy++) w.set(cx, top + dy, cz, dy % 2 == 0 ? "copper_block" : "polished_blackstone_wall");
        w.hangLight(cx, y + 3, cz, "lantern");
        // a door on each side at ground level
        for (int[] d : new int[][] {{0, -3}, {0, 3}, {-3, 0}, {3, 0}}) for (int dy = 1; dy <= 2; dy++) w.set(cx + d[0], y + dy, cz + d[1], "air");
        keepClear(cx - 5, cz - 5, cx + 5, cz + 5);
    }

    /** A bell tower (shōrō): four posts on a stone base, a great bronze bell hung from the beam, a hipped roof. */
    void bellTower(int cx, int cz) {
        int y = Y;
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            w.set(cx + dx, y, cz + dz, Math.abs(dx) == 3 || Math.abs(dz) == 3 ? "stone_bricks" : "polished_andesite");
            for (int dy = 1; dy <= 10; dy++) if (!w.air(cx + dx, y + dy, cz + dz)) w.set(cx + dx, y + dy, cz + dz, "air");
        }
        for (int[] p : new int[][] {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) for (int dy = 1; dy <= 5; dy++) w.set(cx + p[0], y + dy, cz + p[1], "stripped_dark_oak_log[axis=y]");
        k.beam(cx - 2, cz - 2, cx + 2, cz + 2, y + 6, "stripped_dark_oak_log");
        for (int x = cx - 2; x <= cx + 2; x++) w.set(x, y + 6, cz, "stripped_dark_oak_log[axis=x]");
        w.set(cx, y + 5, cz, "bell[attachment=ceiling,facing=north]");
        k.hip(cx - 2, cz - 2, cx + 2, cz + 2, y + 7, 1, Kit.Roof.of("deepslate_tiles"), P.of("dark_oak_planks"));
        keepClear(cx - 4, cz - 4, cx + 4, cz + 4);
    }

    // ================================================================ the walks

    /**
     * The torii walk (north side, west half): a tunnel of vermilion gates one after another along the island, a
     * gravel way inside, lanterns hung in it now and then.
     */
    void toriiWalk() {
        int y = Y;
        for (int x = -27; x <= -1; x++) {
            // the way's centre at x, following the island's curve
            double zc = x <= -14 ? -71 + (x + 22.5) * (-2.0 / 8.5) : -73 + (x + 14) * (2.0 / 8.5);
            int z0 = (int) Math.round(zc);
            for (int dz = -2; dz <= 2; dz++) if (isle(x, z0 + dz) == Isle.TORII) w.set(x, y, z0 + dz, Math.abs(dz) == 2 ? "andesite" : "gravel");
            if (Math.floorMod(x, 2) == 0 && isle(x, z0 - 3) == Isle.TORII && isle(x, z0 + 3) == Isle.TORII) {
                torii(x, z0 - 3, x, z0 + 3, y, false);
                if (Math.floorMod(x, 8) == 0) w.hangLight(x, y + 4, z0, "lantern");
            }
        }
        keepClear(-28, -79, 0, -64);
    }

    /**
     * The raked garden (south side, west half): white gravel raked in lines and in rings round three groups of
     * standing stones on moss, a roofed veranda along the outer rim to watch it from.
     */
    void zenGarden() {
        int y = Y;
        double[][] rocks = {{-35, 67, 2.6}, {-24, 71, 2.2}, {-14, 66, 2.0}};
        for (int x = -43; x <= -8; x++) for (int z = 60; z <= 78; z++) {
            if (isle(x, z) != Isle.ZEN) continue;
            double best = 99;
            double[] near = null;
            for (double[] r : rocks) {
                double d = Math.hypot(x - r[0], z - r[1]) - r[2];
                if (d < best) {
                    best = d;
                    near = r;
                }
            }
            String s;
            if (best < 0) s = "moss_block";
            else if (best < 6) s = Math.floorMod((int) Math.floor(best), 2) == 0 ? "white_concrete_powder" : "light_gray_concrete_powder";
            else s = Math.floorMod(z, 2) == 0 ? "white_concrete_powder" : "light_gray_concrete_powder";
            w.set(x, y, z, s);
        }
        String[] stone = {"tuff", "andesite", "stone", "mossy_cobblestone", "cobblestone"};
        for (double[] r : rocks) {
            int cx = (int) Math.round(r[0]), cz = (int) Math.round(r[1]);
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                double d = Math.hypot(dx, dz * 1.2);
                if (d > r[2] - 0.2) continue;
                int h = (int) Math.round((r[2] - d) * 1.9 + w.r.nextInt(2)) + (dx == 0 && dz == 0 ? 1 : 0);
                for (int dy = 1; dy <= h; dy++) w.set(cx + dx, y + dy, cz + dz, stone[w.r.nextInt(stone.length)]);
            }
        }
        // the veranda along the outer rim, on dark posts, under a tiled lean-to roof
        for (int x = -36; x <= -14; x++) {
            int zo = 75;
            while (zo > 70 && isle(x, zo) != Isle.ZEN) zo--;
            for (int z = zo - 2; z <= zo; z++) {
                if (isle(x, z) != Isle.ZEN) continue;
                w.set(x, y, z, "dark_oak_planks");
                if (Math.floorMod(x, 4) == 0 && (z == zo - 2 || z == zo)) for (int dy = 1; dy <= 3; dy++) w.set(x, y + dy, z, "stripped_dark_oak_log[axis=y]");
                w.set(x, y + 4, z, z == zo - 2 ? "deepslate_tile_slab[type=bottom]" : z == zo ? "deepslate_tiles" : "deepslate_tile_slab[type=top]");
            }
        }
        keepClear(-43, 59, -8, 78);
    }

    /** The bamboo grove on the leaf (west side): bamboo standing thick, one way winding through it between fences. */
    void bambooLeaf() {
        int y = Y;
        for (int z = -30; z <= 4; z++) for (int x = -80; x <= -60; x++) {
            if (isle(x, z) != Isle.LEAF) continue;
            double wayX = -72 + 5 * (z + 27) / 28.0 + 2.0 * Math.sin(z / 4.5) * Math.sin(Math.PI * Math.max(0, Math.min(1, (z + 27) / 28.0)));
            double d = Math.abs(x - wayX);
            if (d < 1.6) {
                w.set(x, y, z, ((x + z) & 1) == 0 ? "gravel" : "coarse_dirt");
                continue;
            }
            if (d < 2.6) {
                if (Math.floorMod(z, 3) != 0) w.set(x, y + 1, z, "bamboo_fence");
                continue;
            }
            w.set(x, y, z, w.r.nextInt(4) == 0 ? "podzol" : "grass_block");
            if (w.r.nextInt(5) < 3) {
                int h = 8 + w.r.nextInt(7);
                for (int i = 1; i <= h; i++) w.set(x, y + i, z, "bamboo[age=1,leaves=" + (i >= h - 1 ? "large" : i >= h - 3 ? "small" : "none") + ",stage=0]");
            }
        }
        keepClear(-80, -30, -60, 4);
    }

    /**
     * The koi (west side): a pond in its body with lily pads and an arched footbridge, a tea house on its head
     * looking down the pond, dark stones for its eyes, cherries on its tail fins.
     */
    void koiGarden() {
        int y = Y, cx = -69, cz = 26;
        for (int x = cx - 12; x <= cx + 12; x++) for (int z = cz - 18; z <= cz + 20; z++) {
            if (isle(x, z) != Isle.KOI) continue;
            double[] uv = rot(x - cx, z - cz, Math.toRadians(-90));
            double u = uv[0], v = uv[1];
            // the pond: an oval in the body, behind the head
            double pond = Math.hypot((u + 1.5) / 6.0, v / 3.8);
            if (pond < 1) {
                w.set(x, y, z, "water");
                w.set(x, y - 1, z, pond < 0.6 ? "water" : "clay");
                w.set(x, y - 2, z, "clay");
                if (w.r.nextInt(6) == 0 && pond > 0.3) w.set(x, y + 1, z, "lily_pad");
            } else if (pond < 1.22) w.set(x, y, z, w.r.nextBoolean() ? "mossy_cobblestone" : "stone");
            // eyes, and a kerb of mossy stone round the rim so its outline reads from far off
            for (int s = -1; s <= 1; s += 2) if (Math.hypot(u - 7.2, v - s * 3.3) < 1.1) w.set(x, y, z, "polished_blackstone");
            boolean rim = false;
            for (int[] n : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) if (isle(x + n[0], z + n[1]) != Isle.KOI) rim = true;
            if (rim) w.set(x, y, z, "mossy_stone_bricks");
        }
        // the footbridge across the pond (east to west across the koi's body)
        int bz = cz + 1;
        for (int dx = -5; dx <= 5; dx++) {
            int h = Math.abs(dx) <= 1 ? 1 : 0;
            for (int dz = -1; dz <= 1; dz++) {
                int x = cx + dx, z = bz + dz;
                if (isle(x, z) != Isle.KOI) continue;
                w.set(x, y + h, z, h == 1 ? "mangrove_slab[type=bottom]" : "mangrove_planks");
                if (h == 1) w.set(x, y, z, "stripped_mangrove_log[axis=x]");
                for (int dy = h + 1; dy <= h + 3; dy++) w.set(x, y + dy, z, "air");
            }
        }
        // the tea house on the head, facing back down the pond
        int tx = cx, tz = cz - 7;
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            w.set(tx + dx, y, tz + dz, Math.abs(dx) == 3 || Math.abs(dz) == 3 ? "stone_bricks" : "dark_oak_planks");
            for (int dy = 1; dy <= 6; dy++) if (!w.air(tx + dx, y + dy, tz + dz)) w.set(tx + dx, y + dy, tz + dz, "air");
        }
        k.walls(tx - 2, tz - 2, tx + 2, tz + 2, y, 3, Kit.Bands.of(0, "dark_oak_planks", 1, "white_concrete", 3, "dark_oak_planks"), "stripped_dark_oak_log", 2, P.of("dark_oak_planks"));
        for (int o = -1; o <= 1; o++) for (int dy = 1; dy <= 2; dy++) {
            w.set(tx + o, y + dy, tz + 2, "air");
            w.set(tx + o, y + dy, tz - 2, "air");
        }
        k.hip(tx - 2, tz - 2, tx + 2, tz + 2, y + 4, 1, Kit.Roof.of("dark_oak_planks"), P.of("dark_oak_planks"));
        w.set(tx, y + 1, tz, "dark_oak_slab[type=bottom]");
        w.set(tx, y + 2, tz, "flower_pot");
        w.hangLight(tx, y + 3, tz, "lantern");
        lantern(cx + 4, y, cz + 8);
        lantern(cx - 4, y, cz + 8);
        keepClear(tx - 4, tz - 4, tx + 4, tz + 4);
        keepClear(cx - 7, cz - 6, cx + 7, cz + 6);
    }

    // ================================================================ the corners and the middle

    /**
     * The pagoda court on its octagon (south-west; its copy is the north-east): paved stone, a five-storey pagoda
     * in the outer half, the hill's court in the inner half toward the pond, lanterns at the eight corners.
     */
    void pagodaCourt() {
        int y = Y;
        for (int x = CX - 15; x <= CX + 15; x++) for (int z = CZ - 15; z <= CZ + 15; z++) {
            if (isle(x, z) != Isle.CORNER) continue;
            double o = octagon(x, z, CX, CZ, 14);
            String s = o < 0.08 ? "mossy_stone_bricks" : Math.floorMod(x + z, 4) == 0 ? "polished_andesite" : ((x ^ z) & 1) == 0 ? "stone_bricks" : "andesite";
            w.set(x, y, z, s);
        }
        pagoda(CX - 4, CZ + 4, 5);
        for (int[] p : new int[][] {{CX - 9, CZ - 4}, {CX + 4, CZ + 9}}) {
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
                w.set(p[0] + dx, y, p[1] + dz, Math.abs(dx) == 2 || Math.abs(dz) == 2 ? "mossy_stone_bricks" : "moss_block");
            flora.tree(Theme.Tree.CHERRY, p[0], y + 1, p[1]);
        }
        // the hill's court: a ring of lanterns round a moss-and-stone square
        int hx = CX + 5, hz = CZ - 5;
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++)
            w.set(hx + dx, y, hz + dz, Math.abs(dx) == 3 || Math.abs(dz) == 3 ? "chiseled_stone_bricks" : ((dx + dz) & 1) == 0 ? "moss_block" : "polished_andesite");
        for (int[] p : new int[][] {{-4, 0}, {4, 0}, {0, -4}, {0, 4}}) lantern(hx + p[0], y, hz + p[1]);
        for (int k2 = 0; k2 < 8; k2++) {
            double a = k2 * Math.PI / 4 + Math.PI / 8;
            int x = CX + (int) Math.round(Math.cos(a) * 12), z = CZ + (int) Math.round(Math.sin(a) * 12);
            if (isle(x, z) == Isle.CORNER && Math.hypot(x - hx, z - hz) > 5) lantern(x, y, z);
        }
        keepClear(hx - 5, hz - 5, hx + 5, hz + 5);
    }

    /**
     * The crane (between the koi and the middle): white wings of raked gravel, a black neck and tail, a red crown;
     * a clipped pine on each wing and a lantern on its back.
     */
    void craneIsle() {
        int y = Y, cx = -35, cz = 37;
        for (int x = cx - 14; x <= cx + 14; x++) for (int z = cz - 14; z <= cz + 14; z++) {
            if (isle(x, z) != Isle.CRANE) continue;
            double[] uv = rot(x - cx, z - cz, Math.toRadians(-45));
            double u = uv[0], v = uv[1];
            String s;
            if (Math.hypot((u - 9.5) / 2.2, v / 1.7) < 0.75) s = "red_concrete_powder";
            else if (u > 5.5 && Math.abs(v) < 1.6) s = "polished_blackstone";
            else if (u < -7) s = "blackstone";
            else if (Math.abs(v) > 11.5 || Math.abs(v) > 4 && u < -2.8) s = "polished_blackstone";
            else s = Math.floorMod((int) Math.floor(u), 2) == 0 ? "white_concrete_powder" : "calcite";
            w.set(x, y, z, s);
        }
        for (int sg = -1; sg <= 1; sg += 2) {
            double[] p = {1.0, sg * 7.5};
            double a = Math.toRadians(-45);
            int px = cx + (int) Math.round(p[0] * Math.cos(a) - p[1] * Math.sin(a)), pz = cz + (int) Math.round(p[0] * Math.sin(a) + p[1] * Math.cos(a));
            pine(px, y + 1, pz);
        }
        lantern(cx, y, cz);
        keepClear(cx - 12, cz - 12, cx + 12, cz + 12);
    }

    /** A clipped Japanese pine (niwaki): a leaning trunk, flat clouds of needles on short limbs. */
    void pine(int x, int y, int z) {
        int h = 4;
        int dx = 0;
        for (int i = 0; i < h; i++) {
            if (i == 2) dx = 1;
            w.set(x + dx, y + i, z, "spruce_log[axis=y]");
        }
        int[][] clouds = {{-2, 2, 0, 2}, {2, 3, 1, 2}, {0, 5, -1, 2}};
        for (int[] c : clouds) {
            int cx = x + dx + c[0], cy = y + c[1], cz = z + c[2];
            for (int ax = -c[3]; ax <= c[3]; ax++) for (int az = -c[3]; az <= c[3]; az++) {
                if (ax * ax + az * az > c[3] * c[3] + 1) continue;
                w.set(cx + ax, cy, cz + az, "spruce_leaves[persistent=true]");
                if (ax * ax + az * az <= 1) w.set(cx + ax, cy + 1, cz + az, "spruce_leaves[persistent=true]");
            }
            // a limb from the trunk to the cloud
            int lx = x + dx, lz = z;
            while (lx != cx || lz != cz) {
                if (lx != cx) lx += Integer.signum(cx - lx);
                else lz += Integer.signum(cz - lz);
                if (w.air(lx, cy - 1, lz) || w.type(lx, cy - 1, lz).name().endsWith("LEAVES")) w.set(lx, cy - 1, lz, "spruce_wood");
            }
        }
    }

    /**
     * Hōrai, the island of the immortals in the middle: the moon-viewing stage (a hill), a round pale deck half a
     * block up; two old cherries either side (each the other's copy); turtle stones in the moss; lanterns.
     */
    void horai() {
        int y = Y;
        for (int x = -13; x <= 13; x++) for (int z = -13; z <= 13; z++) {
            if (!w.canon(x, z) || isle(x, z) != Isle.HORAI) continue;
            double r = Math.hypot(x, z);
            if (r < 4.6) {
                c(x, y + 1, z, "smooth_quartz");
                c(x, y, z, "stone_bricks");
            } else if (r < 5.6) {
                c(x, y + 1, z, "smooth_quartz_slab[type=bottom]");
            } else if (r < 6.6) c(x, y, z, "polished_diorite");
            for (int dy = 2; dy <= 8; dy++) if (r < 6 && !w.air(x, y + dy, z)) c(x, y + dy, z, "air");
        }
        for (int[] p : new int[][] {{-6, -2}, {-2, -6}}) lantern(p[0], y, p[1]);
        // turtle stones, a basin
        for (int[] p : new int[][] {{-9, 2}, {-3, 9}}) {
            w.set(p[0], y + 1, p[1], "mossy_cobblestone");
            w.set(p[0] + 1, y + 1, p[1], "stone");
            w.set(p[0], y + 2, p[1], "mossy_cobblestone_slab[type=bottom]");
        }
        flora.tree(Theme.Tree.CHERRY, -8, y + 1, 7);
        keepClear(-12, -12, 12, 12);
    }

    /** The stepping stones over the pond: each a flat stone on a tall stalk of rock, a lantern on one side. */
    void steppingStones() {
        int y = Y;
        for (double[] s : STONES) {
            int sx = (int) Math.round(s[0]), sz = (int) Math.round(s[1]);
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
                if (isle(sx + dx, sz + dz) != Isle.STONE) continue;
                double d = Math.hypot(dx, dz);
                w.set(sx + dx, y, sz + dz, d < 1.6 ? "polished_andesite" : d < 2.6 ? "stone" : "mossy_stone_bricks");
            }
            lantern(sx + (s[1] > 0 ? 2 : -2), y, sz + (s[1] > 0 ? -2 : 2));
            keepClear(sx - 3, sz - 3, sx + 3, sz + 3);
        }
    }
}
