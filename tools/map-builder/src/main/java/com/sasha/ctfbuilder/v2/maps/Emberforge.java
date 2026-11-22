package com.sasha.ctfbuilder.v2.maps;

import com.sasha.ctfbuilder.v2.Arena;
import com.sasha.ctfbuilder.v2.Kit;
import com.sasha.ctfbuilder.v2.Land;
import com.sasha.ctfbuilder.v2.Theme;
import com.sasha.ctfbuilder.v2.W.P;
import org.bukkit.World;

/**
 * Emberforge: a volcanic island. Each team holds a dwarven forge hall cut into the foot of a ridge. Between
 * them stands a hollow volcano: the Forge Road tunnels straight through it into the open caldera, where a
 * basalt platform sits in a ring of lava. North and south of the volcano, rivers of lava run out to the edges
 * and are crossed by basalt bridges at the fords. King of the Hill uses all three: North Ford, Caldera,
 * South Ford. Fall in the lava and you're done.
 */
public final class Emberforge extends Arena {

    static final int Y = 70, HALL = 72, CRATER = 84, LAVA = 66;

    public Emberforge(World world) {
        super(world, "emberforge", false, Theme.get("emberforge"));
        name = "Emberforge";
        icon = "MAGMA_BLOCK";
        minutes = 18;
        kothPoints = 250;
        reach = 124;
        low = 15;
        high = 140;
        voidY = 15;
        spectator = new double[] {0.5, 118, 0.5, -90, 40};
    }

    double riverX(int z) {
        return Math.sin(z / 7.0) * 3;
    }

    boolean river(int x, int z) {
        return Math.abs(z) > 24 && Math.abs(z) < 49 && Math.abs(x - riverX(z)) < 3.2;
    }

    double height(int x, int z) {
        double e = Land.ellipse(x, z, 0, 0, 112, 54, 0) + w.fbm(x * 0.6, z * 0.6, 14, 2) * 0.07;
        if (e < 0) return Double.NaN;
        double d = Math.hypot(x, z);
        double h = Y + w.fbm(x + 300, z - 120, 16, 3) * 2;
        // the volcano: a cone with a hollow crater
        double cone = Y + Math.max(0, (30 - d)) * 1.6 + w.fbm(x * 1.5, z * 1.5, 6, 2) * 2;
        if (d < 30) h = Math.max(h, cone);
        if (d < 11.5) h = CRATER;
        // the ridge behind each hall
        if (x < -96) h = Math.max(h, Y + (-96 - x) * 2.2 + w.fbm(x, z, 6, 2) * 2);
        // the hall's courtyard is level
        if (x < -66 && x >= -96 && Math.abs(z) < 22) h = HALL;
        // lava rivers: channels running from the volcano out to the edges
        if (river(x, z)) h = LAVA - 1;
        else if (Math.abs(z) > 24 && Math.abs(z) < 49 && Math.abs(x - riverX(z)) < 5.5) h = Math.min(h, Y - 1);
        if (e < 0.04) h -= 2;
        return h;
    }

    String surface(int x, int z, int y, int steep, double edge) {
        double n = w.fbm(x + 913, z - 377, 8, 2), m = w.fbm(x * 2.2 + 50, z * 2.2, 5, 2);
        if (y < LAVA) return "magma_block";
        double d = Math.hypot(x, z);
        if (d < 30 && y > Y + 2) {
            // the cone: grey ash and basalt in bands, glowing cracks running down it
            if (Math.abs(Math.sin(Math.atan2(z, x) * 5 + d * 0.15)) < 0.08 && m > -0.2) return "magma_block";
            return ((y / 3) & 1) == 0 ? (n > 0 ? "smooth_basalt" : "tuff") : (n > 0.3 ? "blackstone" : "basalt[axis=y]");
        }
        if (steep >= 3) return n > 0 ? "basalt[axis=y]" : "tuff";
        if (m > 0.55) return "magma_block";
        if (n > 0.3) return "smooth_basalt";
        if (n < -0.35) return "blackstone";
        if (n < -0.1) return "gravel";
        return "tuff";
    }

    @Override
    protected void build() {
        Land land = new Land(w, t, -120, 120, -60, 60);
        land.top = this::height;
        land.surface = this::surface;
        land.water = null;
        land.depth = 28;
        land.build();
        // the lava in the river channels
        for (int x = -10; x <= 0; x++) for (int z = -60; z <= 60; z++) {
            if (!w.canon(x, z) || !river(x, z)) continue;
            int gy = w.ground(x, z);
            if (gy < 50) continue;
            c(x, gy + 1, z, "lava");
        }

        forgeHall(-88, 0);
        volcano();
        ford(-38);
        ford(38);
        // basalt columns and dead trees about the place
        for (int i = 0; i < 26; i++) {
            int x = -95 + w.r.nextInt(90), z = -48 + w.r.nextInt(96);
            if (!w.canon(x, z) || isClear(x, z, 4) || Math.hypot(x, z) < 34 || river(x, z)) continue;
            int gy = w.ground(x, z);
            if (gy < 60) continue;
            int h = 2 + w.r.nextInt(6);
            for (int dx = 0; dx <= 1; dx++) for (int dz = 0; dz <= 1; dz++) {
                int hh = h - w.r.nextInt(3);
                for (int dy = 1; dy <= hh; dy++) w.set(x + dx, gy + dy, z + dz, "basalt[axis=y]");
            }
            keepClear(x - 1, z - 1, x + 2, z + 2);
        }
        trees(new Theme.Tree[] {Theme.Tree.DEAD, Theme.Tree.CRIMSON}, new int[] {2, 1}, 0.004, 12, 3, (x, z) -> Math.hypot(x, z) > 34 && !river(x, z));
        plants(P.of("crimson_roots", 2, "nether_sprouts", 2, "dead_bush", 1, "crimson_fungus", 1), 0.06, (x, z) -> true);

        spawn = new double[] {-97.5, HALL + 1, 0.5, -90};
        flag = new int[] {-74, HALL + 1, 0};
        pads.add(new int[] {-76, HALL + 1, -14});
        pads.add(new int[] {-76, HALL + 1, 14});
        hills.add(new Hill("North Ford", -3, Y + 1, -41, 3, Y + 4, -35));
        hills.add(new Hill("Caldera", -3, CRATER + 2, -3, 3, CRATER + 5, 3));
        hills.add(new Hill("South Ford", -3, Y + 1, 35, 3, Y + 4, 41));
    }

    // ---------------------------------------------------------------- the forge hall

    /**
     * A dwarven hall cut into the ridge: a great gate between two square pillars under a carved lintel,
     * a long pillared hall inside with forges and anvils along the walls (the lava is behind glass), the
     * team's banners down the length of it, and the courtyard in front where the flag stands.
     */
    void forgeHall(int cx, int cz) {
        int y = HALL;
        int x0 = cx - 18, x1 = cx + 4, z0 = cz - 8, z1 = cz + 8;
        // carve the hall out of the ridge and floor it
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            boolean wall = x == x0 || x == x1 || z == z0 || z == z1;
            w.set(x, y, z, wall ? "polished_blackstone_bricks" : ((x + z) & 1) == 0 ? "polished_blackstone" : "polished_deepslate");
            for (int dy = 1; dy <= 9; dy++) {
                String spec = wall ? (dy <= 1 ? "polished_blackstone" : dy % 4 == 0 ? "chiseled_polished_blackstone" : P.of("polished_blackstone_bricks", 5, "cracked_polished_blackstone_bricks", 1).pick(w.r)) : "air";
                w.set(x, y + dy, z, spec);
            }
            w.set(x, y + 10, z, wall ? "polished_blackstone_bricks" : "deepslate_tiles");
        }
        // a roof above it where it stands out of the ridge
        k.gable(x0, z0, x1, z1, y + 11, true, 1, Kit.Roof.of("deepslate_tiles"), P.of("polished_blackstone_bricks"));
        // pillars in two rows, with gold capitals
        for (int x = x0 + 3; x <= x1 - 3; x += 4) for (int z : new int[] {cz - 4, cz + 4}) {
            w.set(x, y + 1, z, "chiseled_polished_blackstone");
            for (int dy = 2; dy <= 8; dy++) w.set(x, y + dy, z, "polished_blackstone_bricks");
            w.set(x, y + 9, z, "gilded_blackstone");
        }
        // forges along the side walls: blast furnaces, lava behind glass, anvils and grindstones
        for (int x = x0 + 2; x <= x1 - 4; x += 4) for (int s : new int[] {-1, 1}) {
            int z = s < 0 ? z0 + 1 : z1 - 1;
            String face = s < 0 ? "south" : "north";
            w.set(x, y + 1, z, "blast_furnace[facing=" + face + ",lit=true]");
            w.set(x + 1, y + 1, z, "smithing_table");
            w.set(x + 1, y + 2, z - s, "air");
            w.set(x, y + 2, z, "polished_blackstone_bricks");
            w.set(x, y + 3, z, "polished_blackstone_bricks");
            // the lava niche in the wall itself, glazed
            int wz = s < 0 ? z0 : z1;
            w.set(x + 2, y + 2, wz, "glass");
            w.set(x + 2, y + 3, wz, "glass");
            w.set(x + 2, y + 2, wz + s, "lava");
            w.set(x + 2, y + 3, wz + s, "lava");
            w.set(x + 2, y + 1, wz + s, "polished_blackstone");
            w.set(x + 2, y + 4, wz + s, "polished_blackstone");
            w.set(x + 3, y + 2, wz + s, "polished_blackstone");
            w.set(x + 3, y + 3, wz + s, "polished_blackstone");
            w.set(x + 1, y + 2, wz + s, "polished_blackstone");
            w.set(x + 1, y + 3, wz + s, "polished_blackstone");
            w.set(x + 2, y + 2, wz + 2 * s, "polished_blackstone");
            w.set(x + 2, y + 3, wz + 2 * s, "polished_blackstone");
            w.set(x + 3, y + 1, z, "anvil[facing=" + (s < 0 ? "east" : "west") + "]");
        }
        // banners hung down the hall, chandeliers on chains
        for (int x = x0 + 5; x <= x1 - 2; x += 4) {
            w.set(x, y + 6, z0 + 1, "TEAM_wall_banner[facing=south]");
            w.set(x, y + 6, z1 - 1, "TEAM_wall_banner[facing=north]");
            w.hangLight(x, y + 7, cz, "lantern");
        }
        // the throne end: a raised dais with the team's colours
        for (int z = cz - 3; z <= cz + 3; z++) for (int x = x0 + 1; x <= x0 + 2; x++) w.set(x, y + 1, z, "polished_blackstone_slab[type=bottom]");
        w.set(x0 + 1, y + 2, cz, "polished_blackstone_stairs[facing=east,half=bottom]");
        w.set(x0 + 1, y + 4, cz, "TEAM_wall_banner[facing=east]");
        // the great gate in the east wall: 5 wide, 7 high, square pillars, a carved lintel, chains and lanterns
        for (int dz = -2; dz <= 2; dz++) for (int dy = 1; dy <= 7; dy++) w.set(x1, y + dy, cz + dz, "air");
        for (int s : new int[] {-3, 3}) for (int dx = 0; dx <= 1; dx++) for (int dy = 0; dy <= 9; dy++) {
            w.set(x1 + dx, y + dy, cz + s, dy == 0 ? "polished_blackstone" : dy == 9 ? "gilded_blackstone" : dy % 3 == 0 ? "chiseled_polished_blackstone" : "polished_blackstone_bricks");
            w.set(x1 + dx, y + dy, cz + s + Integer.signum(s), dy == 9 ? "polished_blackstone_bricks" : "polished_blackstone_bricks");
        }
        for (int dz = -2; dz <= 2; dz++) {
            w.set(x1 + 1, y + 8, cz + dz, "chiseled_polished_blackstone");
            w.set(x1 + 1, y + 9, cz + dz, "polished_blackstone_bricks");
            w.set(x1, y + 8, cz + dz, "polished_blackstone_bricks");
        }
        w.set(x1 + 1, y + 7, cz - 2, "polished_blackstone_stairs[facing=south,half=top]");
        w.set(x1 + 1, y + 7, cz + 2, "polished_blackstone_stairs[facing=north,half=top]");
        for (int s : new int[] {-5, 5}) {
            w.set(x1 + 2, y + 6, cz + s, "polished_blackstone_wall");
            w.set(x1 + 2, y + 5, cz + s, "lantern[hanging=true]");
        }
        // the courtyard: flagstones, braziers, the flag on a raised round dais
        for (int x = x1 + 3; x <= x1 + 22; x++) for (int z = cz - 20; z <= cz + 20; z++) {
            double n = w.fbm(x * 1.3, z * 1.3, 5, 2);
            boolean border = x == x1 + 3 || x == x1 + 22 || Math.abs(z - cz) == 20 || Math.abs(z - cz) % 5 == 0;
            w.set(x, y, z, border ? "polished_blackstone_bricks" : n > 0.4 ? "cracked_stone_bricks" : n < -0.3 ? "polished_tuff" : "tuff_bricks");
            for (int dy = 1; dy <= 8; dy++) if (!w.air(x, y + dy, z) && !(x <= x1 + 2 && Math.abs(z - cz) <= 4)) w.set(x, y + dy, z, "air");
        }
        int fx = cx + 14;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            double d = Math.hypot(dx, dz);
            if (d > 2.5) continue;
            w.set(fx + dx, y, cz + dz, d < 1 ? "gilded_blackstone" : "chiseled_polished_blackstone");
        }
        for (int[] b : new int[][] {{x1 + 6, -8}, {x1 + 6, 8}, {x1 + 16, -8}, {x1 + 16, 8}}) {
            w.set(b[0], y + 1, b[1], "polished_blackstone");
            w.set(b[0], y + 2, b[1], "polished_blackstone_wall");
            w.set(b[0], y + 3, b[1], "lantern[hanging=false]");
        }
        keepClear(x0 - 2, z0 - 22, x1 + 24, z1 + 22);
    }

    // ---------------------------------------------------------------- the volcano

    /**
     * The volcano: the Forge Road tunnels in from the west, timbered with basalt and lit, climbing to the
     * caldera floor; in the caldera a ring of lava round the central platform, crossed by two bridges.
     */
    void volcano() {
        // the tunnel from the west flank up to the caldera floor
        int ty0 = Y, tx0 = -34, tx1 = -10;
        for (int x = tx0; x <= tx1; x++) {
            int floor = ty0 + (int) Math.round((x - tx0) * (CRATER - ty0) / (double) (tx1 - tx0));
            boolean step = x > tx0 && x < tx1;
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = 1; dy <= 5; dy++) w.set(x, floor + dy, dz, Math.abs(dz) == 3 ? (dy <= 4 ? "air" : "air") : "air");
                w.set(x, floor, dz, Math.abs(dz) == 3 ? "polished_basalt[axis=y]" : step && Math.abs(dz) <= 1 ? "polished_blackstone_brick_stairs[facing=east,half=bottom]" : "polished_blackstone_bricks");
                for (int b = floor - 1; b > floor - 4; b--) if (w.air(x, b, dz)) w.set(x, b, dz, "blackstone");
            }
            // basalt frames every 5 blocks with a lantern
            if ((x - tx0) % 5 == 0) {
                for (int dy = 1; dy <= 5; dy++) {
                    w.set(x, floor + dy, -3, "polished_basalt[axis=y]");
                    w.set(x, floor + dy, 3, "polished_basalt[axis=y]");
                }
                for (int dz = -3; dz <= 3; dz++) w.set(x, floor + 6, dz, "polished_basalt[axis=z]");
                w.set(x, floor + 5, 0, "lantern[hanging=true]");
            }
        }
        // the tunnel mouth: a carved arch in the flank
        int mouthY = ty0;
        for (int dz = -4; dz <= 4; dz++) for (int dy = 0; dy <= 7; dy++) {
            boolean frame = Math.abs(dz) == 4 || dy == 7;
            if (frame) w.set(tx0 - 1, mouthY + dy, dz, dy == 7 ? "chiseled_polished_blackstone" : "polished_blackstone_bricks");
        }
        // the caldera: an outer floor, the lava ring, the platform in the middle (built half and copied)
        for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++) {
            if (!w.canon(x, z)) continue;
            double d = Math.hypot(x, z) + w.n(x, z, 3) * 0.4;
            if (d > 11.5) continue;
            for (int dy = 1; dy <= 12; dy++) c(x, CRATER + dy, z, "air");
            if (d <= 6.5) {
                c(x, CRATER + 1, z, d > 5.5 ? "polished_blackstone_bricks" : ((x + z) & 1) == 0 ? "polished_blackstone" : "polished_basalt[axis=y]");
                for (int yy = CRATER; yy > CRATER - 4; yy--) c(x, yy, z, "blackstone");
            } else if (d <= 8.8) {
                c(x, CRATER, z, "lava");
                c(x, CRATER - 1, z, "magma_block");
                c(x, CRATER - 2, z, "blackstone");
            } else {
                c(x, CRATER, z, w.r.nextInt(4) == 0 ? "magma_block" : "blackstone");
            }
        }
        // bridges over the lava ring from the tunnel side
        for (int x = -10; x <= -6; x++) for (int z = -1; z <= 1; z++) {
            c(x, CRATER + 1, z, "polished_blackstone_bricks");
            if (Math.abs(z) == 1) c(x, CRATER + 2, z, "polished_blackstone_wall");
        }
        keepClear(-36, -5, -8, 5);
        keepClear(-12, -12, 12, 12);
    }

    /** A basalt bridge over a lava river at the ford: an arch with parapets and four lanterns. */
    void ford(int z) {
        int y = Y;
        for (int x = -9; x <= 9; x++) for (int dz = -3; dz <= 3; dz++) {
            int px = x, pz = z + dz;
            if (!w.canon(px, pz)) continue;
            int arch = (int) Math.round(Math.sqrt(Math.max(0, 81 - x * x)) * 0.12);
            int deck = y + arch;
            boolean side = Math.abs(dz) == 3;
            c(px, deck, pz, side ? "polished_basalt[axis=x]" : ((x + dz) & 1) == 0 ? "polished_blackstone_bricks" : "polished_blackstone");
            if (side) c(px, deck + 1, pz, "polished_blackstone_wall");
            for (int h = side ? 2 : 1; h <= 5; h++) c(px, deck + h, pz, "air");
            for (int b = deck - 1; b > deck - 3; b--) if (w.air(px, b, pz) || w.type(px, b, pz) == org.bukkit.Material.LAVA) {
                if (Math.abs(x) > 3) c(px, b, pz, "blackstone");
            }
        }
        for (int[] p : new int[][] {{-9, -3}, {9, -3}, {-9, 3}, {9, 3}}) {
            int px = p[0], pz = z + p[1];
            if (!w.canon(px, pz)) continue;
            c(px, y + 1, pz, "polished_blackstone");
            c(px, y + 2, pz, "lantern[hanging=false]");
        }
        keepClear(-10, z - 4, 10, z + 4);
    }
}
