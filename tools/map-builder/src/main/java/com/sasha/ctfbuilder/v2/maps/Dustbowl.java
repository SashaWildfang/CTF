package com.sasha.ctfbuilder.v2.maps;

import com.sasha.ctfbuilder.v2.Arena;
import com.sasha.ctfbuilder.v2.Kit;
import com.sasha.ctfbuilder.v2.Land;
import com.sasha.ctfbuilder.v2.Theme;
import com.sasha.ctfbuilder.v2.W.P;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Dustbowl: a long badlands canyon. Each team's adobe fort stands on a raised plateau at one end, with a grand
 * stair down to the canyon floor and a stair cut into the rock up to each mesa rim. The floor is flat sand with
 * a dry wash, boulders and a broken wagon for cover; halfway along is a frontier town (saloon, sheriff's office,
 * general store, blacksmith) round a paved square, which is the hill. The rims are the high ground: a box canyon
 * cuts into each of them (rope bridges across, or the long way round its end), a timber stairway climbs the
 * cliff behind the town, and a mine leads from the floor up a ladder shaft to a headframe on the rim.
 */
public final class Dustbowl extends Arena {

    static final int FLOOR = 66, FORT = 76, RIM = 87;
    static final int FORT_CX = -93, FACE = -75, PLATEAU_Z = 25;
    static final int GORGE_X = -44, GORGE_END = 37;
    static final int MINE_X = -60;
    static final int TOWN_W = 21;
    static final int WASH_A = -64, WASH_B = -39;

    Land land;

    public Dustbowl(World world) {
        super(world, "dustbowl", false, Theme.get("dustbowl"));
        name = "Dustbowl";
        icon = "CACTUS";
        minutes = 16;
        kothPoints = 120;
        reach = 124;
        low = 15;
        high = 140;
        voidY = 15;
        spectator = new double[] {0.5, 112, 0.5, -90, 40};
    }

    // ---------------------------------------------------------------- the land

    /** Half the width of the canyon floor at x (the cliffs start there), on one side. */
    double canyonW(int x, int side) {
        double ax = Math.abs(x);
        if (ax <= 30) return TOWN_W;
        if (ax <= 38) return TOWN_W + (16.5 - TOWN_W) * Land.smooth((ax - 30) / 8.0);
        if (ax <= 61) return 16.5 + w.fbm(ax + 40, side * 31, 9, 2) * 1.8 * Land.smooth((ax - 38) / 4.0) * Land.smooth((61 - ax) / 4.0);
        return 16.5 + (PLATEAU_Z - 16.5) * Land.smooth((ax - 61) / 13.0);
    }

    /** Where the plateau's front face is, along z (straight where the grand stair meets it). */
    int faceAt(int z) {
        return FACE + (Math.abs(z) > 5 ? (int) Math.round(w.fbm(z * 1.3, 900, 6, 1) * 0.9) : 0);
    }

    double washZ(int x) {
        double t = x - WASH_A;
        return 4.5 * Math.sin(t / 7.5) + 2.0 * Math.sin(t / 3.3 + 1);
    }

    /** How far (x, z) is from the dry wash's bed (negative inside it). */
    double washD(int x, int z) {
        if (x < WASH_A || x > WASH_B) return 99;
        double hw = 1.6 * Land.smooth((x - WASH_A) / 5.0) * Land.smooth((WASH_B - x) / 7.0);
        return hw <= 0.2 ? 99 : Math.abs(z - washZ(x)) - hw;
    }

    double height(int x, int z) {
        double ax = -x, az = Math.abs(z);
        int side = z >= 0 ? 1 : -1;
        // the outline: a rounded rectangle with ragged sides
        double ox = ax - (114 + w.fbm(z * 0.8, 300, 14, 2) * 2.5);
        double oz = az - (44 + w.fbm(ax * 0.8, side * 500, 16, 2) * 2.5);
        double r = 9, qx = ox + r, qz = oz + r;
        if (Math.hypot(Math.max(qx, 0), Math.max(qz, 0)) + Math.min(Math.max(qx, qz), 0) - r > 0) return Double.NaN;
        double h;
        if (x <= faceAt(z)) {
            // the plateau the fort stands on, with cliffs up to the rims either side
            if (az <= PLATEAU_Z) h = FORT;
            else if (az < PLATEAU_Z + 4) h = FORT + (RIM - FORT) * Land.smooth((az - PLATEAU_Z) / 4.0);
            else h = RIM;
        } else {
            double wv = canyonW(x, side);
            if (az <= wv) h = FLOOR;
            else if (az < wv + 4) h = FLOOR + (RIM - FLOOR) * Land.smooth((az - wv) / 4.0);
            else h = RIM;
            // the dry wash, a block deep
            if (az < wv - 2 && washD(x, z) < 0) h = FLOOR - 1;
            // the box canyon cut into each mesa
            double gx = Math.abs(x - GORGE_X);
            if (gx < 9 && az > wv - 1) {
                double mouth = Land.smooth(1 - (az - wv) / 6.0);
                double hw = 3.5 + 1.8 * mouth + w.fbm(z * 1.5, 77, 5, 1) * 0.4;
                double endC = GORGE_END - hw;
                double d = az > endC ? Math.hypot(gx, az - endC) - hw : gx - hw;
                if (d < 2.5) h = Math.min(h, FLOOR + (RIM - FLOOR) * Land.smooth(Math.max(0, d) / 2.5));
            }
        }
        return h;
    }

    String surface(int x, int z, int y, int steep, double edge) {
        if (steep >= 3) return rock(x, y, z);
        double big = w.fbm(x + 913, z - 377, 16, 2), fine = w.n2(x * 1.7, z * 1.7, 3);
        if (y >= RIM - 1) return big > 0.3 ? "coarse_dirt" : big < -0.42 ? (fine > 0 ? "terracotta" : "red_sandstone") : fine > 0.6 ? "coarse_dirt" : "red_sand";
        if (y == FORT && x <= FACE + 1) return big > 0.25 ? "coarse_dirt" : big < -0.3 ? "packed_mud" : "red_sand";
        if (y < FLOOR) return fine > 0.15 ? "gravel" : fine < -0.4 ? "packed_mud" : "coarse_dirt";
        if (y == FLOOR) {
            if (washD(x, z) < 1.15) return "smooth_sandstone_slab[type=bottom]";
            double toCliff = canyonW(x, z >= 0 ? 1 : -1) - Math.abs(z);
            if (toCliff < 2.2 + fine) return big > 0.05 ? "coarse_dirt" : "red_sand";
            return big > 0.36 ? "red_sand" : big < -0.42 ? "coarse_dirt" : "sand";
        }
        return rock(x, y, z);
    }

    static final String[] LAYERS = {"terracotta", "brown_terracotta", "red_terracotta", "orange_terracotta", "yellow_terracotta",
            "orange_terracotta", "white_terracotta", "light_gray_terracotta", "terracotta", "red_terracotta", "smooth_red_sandstone", "orange_terracotta"};
    static final int[] THICK = {3, 2, 3, 2, 1, 2, 1, 2, 2, 2, 2, 3};
    static final int CYCLE = 25;

    /** The rock: crisp, nearly level strata, the odd block taking the colour of the layer next to it. */
    String rock(int x, int y, int z) {
        double warp = w.fbm(x * 0.6 + 11, z * 0.6 - 7, 36, 2) * 1.4;
        int at = Math.floorMod((int) Math.floor(y + warp), CYCLE);
        int i = 0;
        while (at >= THICK[i]) at -= THICK[i++];
        double tex = w.n3(x * 1.9, y * 1.9, z * 1.9, 4);
        if (tex > 0.6) i = (i + 1) % LAYERS.length;
        else if (tex < -0.62) i = (i + LAYERS.length - 1) % LAYERS.length;
        return LAYERS[i];
    }

    /** Sheer strata walls round the edge, the underside rounding off beneath them. */
    int bottom(int x, int z, int top, double edge) {
        double t = Land.smooth(Math.min(1, edge / 28.0));
        return (int) Math.round(63 - 23 * t + w.fbm(x * 0.9 + 5, z * 0.9 - 5, 20, 2) * 2.5 * Math.min(1, edge / 8.0));
    }

    @Override
    protected void build() {
        land = new Land(w, t, -122, 122, -54, 54);
        land.top = this::height;
        land.surface = this::surface;
        land.rock = this::rock;
        land.bottom = this::bottom;
        land.soil = P.of("terracotta", 3, "red_sandstone", 1);
        land.build();

        fort(FORT_CX, 0);
        grandStair();
        cliffStair(-1);
        cliffStair(1);
        town();
        mine();
        String[] rope = {"spruce_planks", "spruce_slab", "stripped_spruce_wood", "spruce_fence", "spruce_log", null};
        for (int s : new int[] {-1, 1}) {
            k.bridge(GORGE_X - 9, s * 33, GORGE_X + 9, s * 33, 3, rope[0], rope[1], rope[2], rope[3], rope[4], rope[5]);
            keepClear(GORGE_X - 10, s * 31, GORGE_X + 10, s * 35);
            fossil(s);
        }
        wagon(-53, 8, true);
        boulders();
        pillar(-52, -7, 7);
        pillar(-70, 9, 9);
        skeleton(-47, 3);
        rubble();
        hoodoos();
        cacti();
        flora.tree(Theme.Tree.DEAD, -58, FLOOR + 1, 14);
        flora.tree(Theme.Tree.DEAD, -66, FLOOR + 1, -6);
        // the desert: cacti, dead bushes and dry grass on the floor and the rims, the odd dead tree
        plants(P.of("dead_bush", 4, "short_dry_grass", 8, "tall_dry_grass", 3, "cactus", 3), 0.07, (x, z) -> land.topAt(x, z) <= FLOOR);
        plants(P.of("short_dry_grass", 8, "tall_dry_grass", 3, "dead_bush", 2, "cactus", 1), 0.06, (x, z) -> land.topAt(x, z) >= RIM - 1);
        trees(new Theme.Tree[] {Theme.Tree.DEAD}, new int[] {1}, 0.0015, 20, 3, (x, z) -> true);

        spawn = new double[] {FORT_CX - 5.5, FORT + 1, 0.5, -90};
        flag = new int[] {FORT_CX + 8, FORT + 1, 0};
        pads.add(new int[] {FORT_CX + 9, FORT + 1, -9});
        pads.add(new int[] {FORT_CX + 9, FORT + 1, 9});
        hills.add(new Hill("Town Square", -3, FLOOR + 1, -3, 3, FLOOR + 4, 3));
    }

    // ---------------------------------------------------------------- the fort

    /**
     * An adobe fort: mud-brick curtain walls with a walk and parapet, square corner towers with beams poking
     * out of them, a gatehouse to the east, barracks along the west wall (the spawn) with a shaded porch, a well
     * and the flag in the yard.
     */
    void fort(int cx, int cz) {
        int y = FORT;
        int x0 = cx - 12, x1 = cx + 12, z0 = cz - 15, z1 = cz + 15;
        level(x0 - 2, z0 - 2, x1 + 2, z1 + 2, y, P.of("packed_mud", 3, "coarse_dirt", 1, "red_sand", 1), P.of("terracotta"));
        // the curtain walls: 6 high, a walk on top inside, crenellations
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            boolean wall = x == x0 || x == x1 || z == z0 || z == z1;
            boolean walk = !wall && (x == x0 + 1 || x == x1 - 1 || z == z0 + 1 || z == z1 - 1);
            if (wall) {
                for (int dy = 1; dy <= 6; dy++) w.set(x, y + dy, z, dy <= 1 ? "terracotta" : P.of("mud_bricks", 5, "packed_mud", 1).pick(w.r));
                if (((x + z) & 1) == 0) w.set(x, y + 7, z, "mud_brick_wall");
            } else if (walk) {
                w.set(x, y + 5, z, "spruce_slab[type=top]");
            }
        }
        // corner towers: 7x7, a storey above the walls, beam ends sticking out
        for (int[] c : new int[][] {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
            int tx = c[0], tz = c[1];
            k.walls(tx - 3, tz - 3, tx + 3, tz + 3, y, 10, Kit.Bands.of(0, "terracotta", 1, P.of("mud_bricks", 5, "packed_mud", 1)), null, 0, P.of("packed_mud"));
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
                boolean edge = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                w.set(tx + dx, y + 10, tz + dz, edge ? "mud_bricks" : "spruce_planks");
                if (edge && ((dx + dz) & 1) == 0) w.set(tx + dx, y + 11, tz + dz, "mud_brick_wall");
            }
            for (int o = -2; o <= 2; o += 2) {
                w.set(tx + o, y + 9, tz - 4, "stripped_spruce_log[axis=z]");
                w.set(tx + o, y + 9, tz + 4, "stripped_spruce_log[axis=z]");
                w.set(tx - 4, y + 9, tz + o, "stripped_spruce_log[axis=x]");
                w.set(tx + 4, y + 9, tz + o, "stripped_spruce_log[axis=x]");
            }
            // arrow slits
            w.set(tx + 1, y + 7, tz - 3, "air");
            w.set(tx + 1, y + 7, tz + 3, "air");
            w.set(tx - 3, y + 7, tz + 1, "air");
            w.set(tx + 3, y + 7, tz + 1, "air");
            // a ladder up the inside of the outer wall, through a hatch in the roof
            int lx = tx < cx ? tx - 2 : tx + 2;
            for (int dy = 1; dy <= 10; dy++) w.set(lx, y + dy, tz, "ladder[facing=" + (tx < cx ? "east" : "west") + "]");
            // a door from the yard
            int dx2 = tx < cx ? 3 : -3;
            w.set(tx + dx2, y + 1, tz + (tz < cz ? 2 : -2), "air");
            w.set(tx + dx2, y + 2, tz + (tz < cz ? 2 : -2), "air");
        }
        // the gate in the east wall: a gatehouse arch, beams over
        for (int dz = -2; dz <= 2; dz++) for (int dy = 1; dy <= 4; dy++) w.set(x1, y + dy, cz + dz, "air");
        for (int dz = -3; dz <= 3; dz++) for (int dy = 5; dy <= 9; dy++) {
            boolean edge = Math.abs(dz) == 3 || dy == 9;
            w.set(x1, y + dy, cz + dz, dy == 5 ? "stripped_spruce_log[axis=z]" : "mud_bricks");
            w.set(x1 + 1, y + dy, cz + dz, dy == 5 ? "stripped_spruce_log[axis=z]" : edge ? "mud_bricks" : "air");
        }
        for (int dz = -3; dz <= 3; dz += 2) w.set(x1 + 2, y + 8, cz + dz, "stripped_spruce_log[axis=x]");
        w.set(x1 + 1, y + 7, cz, "TEAM_wall_banner[facing=east]");
        for (int s : new int[] {-3, 3}) {
            w.set(x1 + 1, y + 3, cz + s, "spruce_fence");
            w.set(x1 + 1, y + 2, cz + s, "lantern[hanging=true]");
        }
        // the barracks along the west wall: the spawn, a long room with bunks
        int bx0 = x0 + 1, bx1 = x0 + 9, bz0 = cz - 7, bz1 = cz + 7;
        k.walls(bx0, bz0, bx1, bz1, y, 5, Kit.Bands.of(0, "terracotta", 1, P.of("mud_bricks", 4, "packed_mud", 1)), null, 0, P.of("spruce_planks"));
        for (int x = bx0; x <= bx1; x++) for (int z = bz0; z <= bz1; z++) w.set(x, y + 6, z, x == bx0 || x == bx1 || z == bz0 || z == bz1 ? "mud_bricks" : "spruce_planks");
        for (int z = bz0 + 1; z < bz1; z += 2) w.set(bx1 + 1, y + 5, z, "stripped_spruce_log[axis=x]");
        // the door: three wide under a beam; a window either side of it, the same distance off
        for (int dz = -1; dz <= 1; dz++) for (int dy = 1; dy <= 3; dy++) w.set(bx1, y + dy, cz + dz, "air");
        for (int dz = -2; dz <= 2; dz++) w.set(bx1, y + 4, cz + dz, "stripped_spruce_log[axis=z]");
        for (int s : new int[] {-1, 1}) k.window(bx1, y + 2, cz + s * 4, "east", 1, 2, "glass_pane", null, "spruce_trapdoor");
        // a shaded porch in front of the door: posts, a beam, a slab roof under the beam ends
        for (int s : new int[] {-3, 3}) for (int dy = 1; dy <= 3; dy++) w.set(bx1 + 3, y + dy, cz + s, "stripped_spruce_log[axis=y]");
        for (int dz = -3; dz <= 3; dz++) {
            w.set(bx1 + 1, y + 4, cz + dz, "spruce_slab[type=top]");
            w.set(bx1 + 2, y + 4, cz + dz, "spruce_slab[type=top]");
            w.set(bx1 + 3, y + 4, cz + dz, "stripped_spruce_log[axis=z]");
        }
        w.hangLight(bx1 + 3, y + 3, cz, "lantern");
        for (int z = bz0 + 1; z <= bz1 - 1; z += 2) {
            if (Math.abs(z - cz) <= 2) continue;
            k.bed(bx0 + 2, y + 1, z, "west", "TEAM");
            w.set(bx0 + 3, y + 1, z, "barrel[facing=up]");
        }
        for (int z = bz0 + 3; z <= bz1 - 3; z += 4) w.hangLight(bx0 + 5, y + 4, z, "lantern");
        w.set(bx0 + 1, y + 3, cz, "TEAM_wall_banner[facing=east]");
        // the yard: a well, water troughs, hay
        int wx = cx - 3, wz = cz + 11;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            boolean mid = dx == 0 && dz == 0;
            w.set(wx + dx, y, wz + dz, mid ? "water" : "mud_bricks");
            if (!mid) w.set(wx + dx, y + 1, wz + dz, "mud_brick_wall");
        }
        for (int[] p : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) w.set(wx + p[0], y + 2, wz + p[1], "spruce_fence");
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) w.set(wx + dx, y + 3, wz + dz, "spruce_slab[type=bottom]");
        w.set(cx - 4, y + 1, cz - 11, "hay_block");
        w.set(cx - 3, y + 1, cz - 11, "hay_block");
        w.set(cx - 4, y + 2, cz - 11, "hay_block");
        w.set(cx - 1, y + 1, cz - 12, "water_cauldron[level=3]");
        w.set(cx, y + 1, cz - 12, "water_cauldron[level=3]");
        // the flag's pad in the middle of the yard
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) w.set(cx + 8 + dx, y, cz + dz, "chiseled_red_sandstone");
        keepClear(x0 - 4, z0 - 4, x1 + 4, z1 + 4);
    }

    /** The stair from the fort's gate down the plateau's face to the canyon floor, between mud-brick cheeks. */
    void grandStair() {
        // ten steps: the top one level with the plateau, the bottom one a half step off the floor
        for (int i = 1; i <= 10; i++) {
            int x = FACE + i, sy = FORT + 1 - i;
            for (int z = -3; z <= 3; z++) {
                for (int yy = FLOOR; yy < sy; yy++) w.set(x, yy, z, "smooth_sandstone");
                w.set(x, sy, z, "smooth_sandstone_stairs[facing=west,half=bottom]");
                for (int dy = 1; dy <= 5; dy++) w.set(x, sy + dy, z, "air");
            }
            for (int s : new int[] {-4, 4}) {
                for (int yy = FLOOR + 1; yy <= sy; yy++) w.set(x, yy, s, "mud_bricks");
                w.set(x, sy + 1, s, "mud_brick_stairs[facing=west,half=bottom]");
            }
        }
        for (int z = -3; z <= 3; z++) w.set(FACE, FORT, z, "smooth_sandstone");
        for (int s : new int[] {-4, 4}) {
            // posts at the top, lanterns on them
            w.set(FACE, FORT + 1, s, "mud_bricks");
            w.set(FACE, FORT + 2, s, "mud_bricks");
            w.set(FACE, FORT + 3, s, "lantern");
            // posts at the foot, with the team's banners facing down the canyon
            int fx = FACE + 11;
            for (int yy = FLOOR + 1; yy <= FLOOR + 3; yy++) w.set(fx, yy, s, "mud_bricks");
            w.set(fx, FLOOR + 4, s, "lantern");
            w.set(fx + 1, FLOOR + 3, s, "TEAM_wall_banner[facing=east]");
        }
        // a low wall along the top of the plateau's face
        for (int az = 5; az <= PLATEAU_Z - 2; az++) for (int s : new int[] {-1, 1}) {
            int z = s * az, fx = faceAt(z);
            if (land.topAt(fx, z) == FORT) w.set(fx, FORT + 1, z, "mud_brick_wall");
        }
        keepClear(FACE - 1, -6, FACE + 13, 6);
    }

    /** A stair cut into the rock beside the plateau, from the fort up to a rim, with a rib of rock as its rail. */
    void cliffStair(int s) {
        int rail = s * PLATEAU_Z;
        // the pocket at the foot, cut back into the cliff
        for (int x = -77; x <= -76; x++) for (int az = 26; az <= 28; az++) {
            int z = s * az;
            w.set(x, FORT, z, "packed_mud");
            for (int yy = FORT + 1; yy <= RIM + 3; yy++) w.set(x, yy, z, "air");
        }
        for (int i = 1; i <= 11; i++) {
            int x = -77 - i, sy = FORT + i;
            for (int az = 26; az <= 28; az++) {
                int z = s * az;
                for (int yy = FORT; yy < sy; yy++) w.set(x, yy, z, rock(x, yy, z));
                w.set(x, sy, z, "smooth_red_sandstone_stairs[facing=west,half=bottom]");
                for (int yy = sy + 1; yy <= RIM + 3; yy++) w.set(x, yy, z, "air");
            }
            // (the first step stays open on the plateau side, so it can be taken from either way)
            if (i >= 2) for (int yy = FORT + 1; yy <= Math.min(sy + 1, RIM + 1); yy++) w.set(x, yy, rail, rock(x, yy, rail));
            else w.set(x, sy, rail, "smooth_red_sandstone_stairs[facing=west,half=bottom]");
        }
        // the landing at the top, level with the rim, walled at its far end
        for (int x = -90; x <= -89; x++) {
            for (int az = 26; az <= 28; az++) {
                int z = s * az;
                for (int yy = FORT; yy <= RIM; yy++) w.set(x, yy, z, yy == RIM ? "red_sand" : rock(x, yy, z));
                for (int yy = RIM + 1; yy <= RIM + 3; yy++) w.set(x, yy, z, "air");
            }
            for (int yy = FORT + 1; yy <= RIM + 1; yy++) w.set(x, yy, rail, rock(x, yy, rail));
        }
        for (int az = 25; az <= 28; az++) {
            int z = s * az;
            for (int yy = FORT; yy <= RIM + 1; yy++) w.set(-91, yy, z, rock(-91, yy, z));
        }
        keepClear(-92, s * 24, -75, s * 30);
    }

    // ---------------------------------------------------------------- the town

    /** A town building: u runs along x from x0, v from the front wall (0) back; dy counts up from its floor. */
    final class Bld {
        final int x0, x1, zf, zb, in, y;
        final String out, back;

        Bld(int x0, int x1, int zf, int zb, int y) {
            this.x0 = x0;
            this.x1 = x1;
            this.zf = zf;
            this.zb = zb;
            this.y = y;
            in = Integer.signum(zb - zf);
            out = in < 0 ? "south" : "north";
            back = Kit.opp(out);
        }

        int x(int u) { return x0 + u; }
        int z(int v) { return zf + in * v; }
        int n() { return x1 - x0; }
        int d() { return Math.abs(zb - zf); }
        void set(int u, int dy, int v, String spec) { w.set(x(u), y + dy, z(v), spec); }
        void hang(int u, int dy, int v) { w.hangLight(x(u), y + dy, z(v), "lantern"); }
        String door(String wood, boolean upper, String hinge) {
            return wood + "_door[facing=" + back + ",half=" + (upper ? "upper" : "lower") + ",hinge=" + hinge + ",open=false]";
        }
    }

    void town() {
        int y = FLOOR;
        // the town's ground: trodden sand with scuffs of dirt, no big patches
        for (int x = -38; x <= 9; x++) for (int z = -21; z <= 21; z++) {
            if (land.topAt(x, z) != FLOOR || Math.abs(x) > 36) continue;
            int[] c = w.toCanon(x, z);
            if (c[0] != x || c[1] != z) continue;
            double n = w.n2(x * 2.1, z * 2.1, 2);
            w.set(x, y, z, n > 0.5 ? "coarse_dirt" : n < -0.6 ? "packed_mud" : "sand");
        }
        // the road: packed dirt with wheel ruts, ragged at the edges, fading out beyond the town
        for (int x = -47; x <= 0; x++) for (int z = -9; z <= 9; z++) {
            if (!w.canon(x, z)) continue;
            double fade = Land.smooth((x + 47) / 9.0);
            double edge = (4 + w.fbm(x * 1.3, z > 0 ? 11 : -11, 4, 1) * 0.9) * fade;
            if (Math.abs(z) > edge) continue;
            double n = w.n2(x * 1.9, z * 1.9, 2);
            String top = Math.abs(z) == 2 && fade > 0.6 ? (n > -0.35 ? "dirt_path" : "coarse_dirt")
                    : n > 0.45 ? "packed_mud" : n < -0.55 ? "gravel" : "coarse_dirt";
            w.set(x, y, z, top);
        }
        // the square: sandstone paving, a compass rose of red sandstone, worn at the edges
        for (int x = -6; x <= 0; x++) for (int z = -6; z <= 6; z++) {
            if (!w.canon(x, z)) continue;
            int ax = Math.abs(x), az = Math.abs(z), d = Math.max(ax, az);
            String p;
            if (d <= 1) p = d == 0 ? "chiseled_red_sandstone" : "smooth_red_sandstone";
            else if (d == 2) p = "cut_sandstone";
            else if (d == 6) p = ax == 6 && az == 6 ? "chiseled_sandstone" : "cut_sandstone";
            else if (ax == 0 || az == 0) p = "cut_red_sandstone";
            else if (ax == az && d == 4) p = "chiseled_sandstone";
            else p = "smooth_sandstone";
            if (d >= 5 && w.n2(x * 2.3, z * 2.3, 2) > 0.5) p = d == 6 ? "coarse_dirt" : "sandstone";
            w.set(x, y, z, p);
        }
        boardwalk(-33, -9, -1);
        boardwalk(-33, -9, 1);
        saloon(new Bld(-20, -10, -8, -17, y + 1));
        sheriff(new Bld(-32, -24, -8, -15, y + 1));
        store(new Bld(-19, -10, 8, 16, y + 1));
        smithy(new Bld(-32, -23, 8, 13, y + 1));
        bank(new Bld(-7, 7, -8, -14, y + 1));
        wagon(-5, -18, false);
        stairway();
        waterTower(-37, -13);
        arch(-40);
        // lamps at the corners of the square, cover on the hill
        for (int s : new int[] {-1, 1}) {
            for (int dy = 1; dy <= 3; dy++) w.set(-7, y + dy, s * 7, "stripped_spruce_log[axis=y]");
            w.set(-7, y + 4, s * 7, "lantern");
        }
        w.set(-3, y + 1, -3, "hay_block");
        w.set(-3, y + 2, -3, "hay_block");
        w.set(-2, y + 1, -3, "hay_block");
        w.set(-3, y + 1, 3, "barrel[facing=up]");
        w.set(-3, y + 1, 2, "barrel[facing=up]");
        // hitching rails along the road, barrels on the boardwalks
        for (int s : new int[] {-1, 1}) {
            for (int x : new int[] {-18, -17, -16, -13, -12, -11}) w.set(x, y + 1, s * 4, "spruce_fence");
            for (int x : new int[] {-22, -21}) w.set(x, y + 1, s * 7, "barrel[facing=up]");
        }
        w.set(-21, y + 2, -7, "barrel[facing=up]");
        // behind the buildings: an outhouse, a woodpile, crates
        outhouse(-16, -20);
        for (int x = -30; x <= -28; x++) for (int dy = 1; dy <= 2; dy++) {
            w.set(x, y + dy, -17, "spruce_log[axis=x]");
            if (dy == 1) w.set(x, y + dy, -18, "spruce_log[axis=x]");
        }
        for (int[] c : new int[][] {{-16, 17}, {-15, 17}, {-16, 18}}) w.set(c[0], y + 1, c[1], "barrel[facing=up]");
        w.set(-16, y + 2, 17, "barrel[facing=up]");
        keepClear(-42, -22, 0, 22);
    }

    /** A raised boardwalk of slabs, half a block above the road and half below the floors, along one side of the street. */
    void boardwalk(int x0, int x1, int side) {
        for (int x = x0; x <= x1; x++) for (int az = 5; az <= 7; az++) {
            w.set(x, FLOOR, side * az, "spruce_planks");
            w.set(x, FLOOR + 1, side * az, "spruce_slab[type=bottom]");
        }
    }

    void shell(Bld b, int h, Kit.Bands bands, String post, P floor) {
        k.walls(b.x0, Math.min(b.zf, b.zb), b.x1, Math.max(b.zf, b.zb), b.y, h, bands, post, 0, floor);
    }

    /**
     * A low gable roof of slabs (a half block up for each block in), ridge running back from the street, from just
     * behind the front to one past the back wall. The back gable is walled up under it. Returns the steps to the ridge.
     */
    int slabRoof(Bld b, int yTop, String plank, String slab) {
        int ex0 = b.x0 - 1, ex1 = b.x1 + 1, smax = 0;
        for (int x = ex0; x <= ex1; x++) {
            int s = Math.min(x - ex0, ex1 - x);
            smax = Math.max(smax, s);
            int level = yTop + s / 2;
            boolean top = s % 2 == 1;
            for (int v = 1; v <= b.d() + 1; v++) {
                int z = b.z(v);
                if (v == b.d() && x >= b.x0 && x <= b.x1) {
                    for (int yy = yTop; yy < level + (top ? 1 : 0); yy++) w.set(x, yy, z, plank);
                    if (!top) w.set(x, level, z, slab + "[type=bottom]");
                } else {
                    w.set(x, level, z, slab + "[type=" + (top ? "top" : "bottom") + "]");
                }
            }
        }
        return smax;
    }

    /** The roof's height at column u (the first block above its surface). */
    static int roofAbove(Bld b, int yTop, int u) {
        int s = Math.min(u + 1, b.n() + 1 - u);
        return yTop + s / 2 + 1;
    }

    /**
     * The false front: the street wall carried up past the roof between pilasters, a framed sign board with the
     * name on it, a cornice along the top and a raised middle with sloped shoulders. Studs brace it from behind.
     * Signs are "u:TEXT". Returns the top row.
     */
    int falseFront(Bld b, int h, int smax, String fill, String pilaster, String trim, String board, String cornice, String cap, String wood, String... signs) {
        int yTop = b.y + h + 1, top = yTop + smax / 2 + 2, n = b.n();
        int fz = b.zf, oz = b.zf - b.in;
        for (int u = 0; u <= n; u++) for (int yy = yTop; yy <= top; yy++) w.set(b.x(u), yy, fz, u == 0 || u == n ? pilaster : fill);
        int by0 = yTop + 1, by1 = yTop + 2;
        for (int u = 1; u < n; u++) {
            w.set(b.x(u), by0 - 1, fz, trim);
            w.set(b.x(u), by1 + 1, fz, trim);
            for (int yy = by0; yy <= by1; yy++) w.set(b.x(u), yy, fz, u == 1 || u == n - 1 ? trim : board);
        }
        for (String sign : signs) {
            int u = Integer.parseInt(sign.substring(0, sign.indexOf(':')));
            String text = sign.substring(sign.indexOf(':') + 1);
            w.set(b.x(u), by1, oz, wood + "_wall_sign[facing=" + b.out + "]");
            w.signText(b.x(u), by1, oz, true, "", text, "", "");
        }
        // the cornice and its cap
        for (int u = -1; u <= n + 1; u++) w.set(b.x(u), top, oz, cornice + "[facing=" + b.back + ",half=top]");
        for (int u = 0; u <= n; u++) w.set(b.x(u), top + 1, fz, cap + "[type=bottom]");
        // the raised middle, with a sloped shoulder either side
        int m0 = n / 3, m1 = n - n / 3;
        for (int u = m0; u <= m1; u++) {
            w.set(b.x(u), top + 1, fz, fill);
            w.set(b.x(u), top + 2, fz, fill);
            w.set(b.x(u), top + 2, oz, cornice + "[facing=" + b.back + ",half=top]");
            w.set(b.x(u), top + 3, fz, cap + "[type=bottom]");
        }
        w.set(b.x(m0 - 1), top + 1, fz, cornice + "[facing=east,half=bottom]");
        w.set(b.x(m1 + 1), top + 1, fz, cornice + "[facing=west,half=bottom]");
        return top;
    }

    /** A porch over the boardwalk: posts on its street edge, a beam, a roof of slabs (or, for a balcony, a floor with a rail). */
    void porch(Bld b, int height, boolean balcony, int... posts) {
        int n = b.n(), yr = b.y + height;
        for (int u = -1; u <= n + 1; u++) {
            for (int dd = 1; dd <= 3; dd++) {
                int z = b.zf - b.in * dd;
                w.set(b.x(u), yr, z, dd == 3 ? "stripped_spruce_log[axis=x]" : balcony ? "spruce_planks" : "spruce_slab[type=top]");
            }
            if (balcony) w.set(b.x(u), yr + 1, b.zf - b.in * 3, "spruce_fence");
        }
        if (balcony) for (int u : new int[] {-1, n + 1}) for (int dd = 1; dd <= 2; dd++) w.set(b.x(u), yr + 1, b.zf - b.in * dd, "spruce_fence");
        for (int u : posts) for (int yy = b.y; yy < yr; yy++) w.set(b.x(u), yy, b.zf - b.in * 3, "stripped_spruce_log[axis=y]");
        for (int i = 0; i + 1 < posts.length; i++) w.hangLight(b.x((posts[i] + posts[i + 1]) / 2), yr - 1, b.zf - b.in * (balcony ? 2 : 3), "lantern");
    }

    /** Windows down both side walls at the given depths and heights (shutters and a sill outside). */
    void sideWindows(Bld b, int[] vs, int[] dys) {
        for (int v : vs) for (int dy : dys) {
            k.window(b.x0, b.y + dy, b.z(v), "west", 1, 2, "glass_pane", "spruce_stairs", "spruce_trapdoor");
            k.window(b.x1, b.y + dy, b.z(v), "east", 1, 2, "glass_pane", "spruce_stairs", "spruce_trapdoor");
        }
    }

    /** The saloon: two storeys, swing doors, a balcony over the porch; the bar, tables and a piano below, bunks above. */
    void saloon(Bld b) {
        int h = 9, n = b.n(), d = b.d();
        shell(b, h, Kit.Bands.of(0, "cobblestone", 1, "spruce_planks", 5, "stripped_dark_oak_wood", 6, "spruce_planks"), "stripped_dark_oak_log", P.of("dark_oak_planks"));
        for (int u = 1; u < n; u++) for (int v = 1; v < d; v++) b.set(u, 5, v, "spruce_planks");
        int yTop = b.y + h + 1;
        int smax = slabRoof(b, yTop, "spruce_planks", "dark_oak_slab");
        falseFront(b, h, smax, "spruce_planks", "stripped_dark_oak_log", "stripped_dark_oak_wood", "dark_oak_planks", "dark_oak_stairs", "dark_oak_slab", "dark_oak", "5:SALOON");
        porch(b, 5, true, -1, 3, 7, 11);
        // the street front: swing doors under a beam, windows either side; upstairs, a door out onto the balcony
        for (int u = 4; u <= 6; u++) for (int dy = 1; dy <= 2; dy++) b.set(u, dy, 0, "air");
        b.set(4, 1, 0, "spruce_trapdoor[facing=east,open=true,half=top]");
        b.set(6, 1, 0, "spruce_trapdoor[facing=west,open=true,half=top]");
        for (int u = 3; u <= 7; u++) b.set(u, 3, 0, "stripped_dark_oak_log[axis=x]");
        for (int u : new int[] {1, 2, 8, 9}) for (int dy = 1; dy <= 2; dy++) b.set(u, dy, 0, "glass_pane");
        b.set(5, 6, 0, b.door("spruce", false, "left"));
        b.set(5, 7, 0, b.door("spruce", true, "left"));
        for (int u : new int[] {2, 8}) k.window(b.x(u), b.y + 6, b.zf, b.out, 1, 2, "glass_pane", null, "spruce_trapdoor");
        sideWindows(b, new int[] {3, 6}, new int[] {2, 6});
        // the back: a door to the yard, windows, a brick chimney
        b.set(2, 1, d, "spruce_door[facing=" + b.out + ",half=lower,hinge=left,open=false]");
        b.set(2, 2, d, "spruce_door[facing=" + b.out + ",half=upper,hinge=left,open=false]");
        for (int u : new int[] {3, 7}) k.window(b.x(u), b.y + 6, b.zb, b.back, 1, 2, "glass_pane", "spruce_stairs", "spruce_trapdoor");
        for (int yy = FLOOR; yy <= yTop + smax / 2 + 1; yy++) b.set(8, yy - b.y, d + 1, yy <= b.y + 1 ? "cobblestone" : "bricks");
        b.set(7, 0, d + 1, "cobblestone");
        b.set(7, 1, d + 1, "cobblestone_stairs[facing=east,half=bottom]");

        // inside, downstairs: the stair up along the east wall, climbing toward the street, a closet under it
        for (int i = 0; i < 5; i++) {
            int v = 6 - i, dy = 1 + i;
            b.set(9, dy, v, "spruce_stairs[facing=" + b.out + ",half=bottom]");
            for (int f = 1; f < dy; f++) b.set(9, f, v, "spruce_planks");
        }
        for (int v = 3; v <= 6; v++) b.set(9, 5, v, "air");
        for (int v = 3; v <= 6; v++) b.set(8, 6, v, "spruce_fence");
        b.set(9, 6, 7, "spruce_fence");
        // the bar across the back: the counter, the barman's aisle, barrels with bottles on them, a shelf, a stove
        String[] onBar = {"decorated_pot", "brewing_stand", "potted_cactus", "brewing_stand", "candle[candles=3]", "decorated_pot"};
        for (int u = 1; u <= 6; u++) {
            b.set(u, 1, 6, "stripped_spruce_wood");
            b.set(u, 1, 8, "barrel[facing=" + b.out + "]");
            b.set(u, 2, 8, onBar[u - 1]);
            b.set(u, 3, 8, "spruce_trapdoor[facing=" + b.out + ",half=top,open=false]");
        }
        b.set(2, 2, 6, "brewing_stand");
        b.set(5, 2, 6, "lantern");
        b.set(7, 1, 8, "smoker[facing=" + b.out + ",lit=true]");
        for (int u : new int[] {2, 4, 6}) {
            b.set(u, 1, 5, "spruce_fence");
            b.set(u, 2, 5, "red_carpet");
        }
        // tables and chairs, a piano by the west wall, a rug in from the door
        for (int[] t : new int[][] {{2, 2}, {7, 2}}) {
            b.set(t[0], 1, t[1], "spruce_fence");
            b.set(t[0], 2, t[1], "spruce_pressure_plate");
            b.set(t[0] - 1, 1, t[1], "spruce_stairs[facing=west]");
            b.set(t[0] + 1, 1, t[1], "spruce_stairs[facing=east]");
            b.set(t[0], 1, t[1] + 1, "spruce_stairs[facing=" + b.back + "]");
        }
        b.set(1, 1, 4, "note_block");
        b.set(1, 2, 4, "jukebox");
        b.set(2, 1, 4, "spruce_stairs[facing=east]");
        for (int v = 1; v <= 4; v++) b.set(5, 1, v, "red_carpet");
        b.set(8, 1, 1, "cauldron");
        b.hang(3, 4, 3);
        b.hang(7, 4, 4);
        b.hang(4, 4, 7);
        // upstairs: a landing along the front to the balcony, a room of bunks behind a partition
        for (int u = 1; u <= 7; u++) for (int dy = 6; dy <= 9; dy++) b.set(u, dy, 3, "spruce_planks");
        b.set(4, 6, 3, "spruce_door[facing=" + b.back + ",half=lower,hinge=left,open=false]");
        b.set(4, 7, 3, "spruce_door[facing=" + b.back + ",half=upper,hinge=left,open=false]");
        for (int u : new int[] {1, 3, 6}) k.bed(b.x(u), b.y + 6, b.z(7), b.back, "red");
        b.set(2, 6, 8, "barrel[facing=up]");
        b.set(2, 7, 8, "lantern");
        b.set(5, 6, 8, "chest[facing=" + b.out + "]");
        b.set(7, 6, 5, "cauldron");
        b.set(1, 6, 1, "potted_dead_bush");
        b.set(9, 6, 1, "barrel[facing=up]");
        b.hang(5, 8, 2);
        b.hang(4, 8, 6);
    }

    /** The general store: a tall shop front of display windows, a counter, shelves of goods. */
    void store(Bld b) {
        int h = 6, n = b.n(), d = b.d();
        shell(b, h, Kit.Bands.of(0, "cobblestone", 1, "oak_planks", 4, "stripped_dark_oak_wood", 5, "oak_planks"), "stripped_dark_oak_log", P.of("spruce_planks"));
        int yTop = b.y + h + 1;
        int smax = slabRoof(b, yTop, "oak_planks", "spruce_slab");
        falseFront(b, h, smax, "oak_planks", "stripped_dark_oak_log", "stripped_dark_oak_wood", "dark_oak_planks", "dark_oak_stairs", "dark_oak_slab", "dark_oak", "4:GENERAL", "5:STORE");
        porch(b, 4, false, -1, 3, 6, 10);
        // the shop front: double doors under a beam, tall display windows, small windows above the porch
        b.set(4, 1, 0, b.door("oak", false, b.back.equals("south") ? "right" : "left"));
        b.set(4, 2, 0, b.door("oak", true, b.back.equals("south") ? "right" : "left"));
        b.set(5, 1, 0, b.door("oak", false, b.back.equals("south") ? "left" : "right"));
        b.set(5, 2, 0, b.door("oak", true, b.back.equals("south") ? "left" : "right"));
        for (int u = 3; u <= 6; u++) b.set(u, 3, 0, "stripped_dark_oak_log[axis=x]");
        for (int u : new int[] {1, 2, 7, 8}) for (int dy = 1; dy <= 3; dy++) b.set(u, dy, 0, "glass_pane");
        for (int u : new int[] {2, 7}) b.set(u, 5, 0, "glass_pane");
        sideWindows(b, new int[] {3, 6}, new int[] {2});
        b.set(7, 1, d, "oak_door[facing=" + b.out + ",half=lower,hinge=left,open=false]");
        b.set(7, 2, d, "oak_door[facing=" + b.out + ",half=upper,hinge=left,open=false]");
        k.window(b.x(2), b.y + 2, b.zb, b.back, 1, 2, "glass_pane", "spruce_stairs", "spruce_trapdoor");

        // the counter across the room, open at the east end; goods along the back wall and both sides
        for (int u = 1; u <= 6; u++) b.set(u, 1, 4, "stripped_dark_oak_wood");
        b.set(1, 2, 4, "lantern");
        b.set(3, 2, 4, "potted_cactus");
        b.set(5, 2, 4, "decorated_pot");
        String[][] back = {{"barrel[facing=" + b.out + "]", "barrel[facing=" + b.out + "]"}, {"barrel[facing=" + b.out + "]", "barrel[facing=" + b.out + "]"},
                {"bookshelf", "bookshelf", "bookshelf"}, {"chest[facing=" + b.out + "]", "barrel[facing=" + b.out + "]"},
                {"chest[facing=" + b.out + "]", "barrel[facing=" + b.out + "]"}, {"bookshelf", "bookshelf", "bookshelf"}};
        for (int u = 1; u <= 6; u++) for (int i = 0; i < back[u - 1].length; i++) b.set(u, 1 + i, 7, back[u - 1][i]);
        b.set(8, 1, 7, "hay_block");
        b.set(8, 2, 7, "hay_block");
        b.set(8, 1, 6, "hay_block");
        for (int v = 1; v <= 3; v++) {
            b.set(1, 1, v, v == 3 ? "barrel[facing=up]" : "composter");
            b.set(1, 3, v, "spruce_slab[type=top]");
            b.set(1, 4, v, v == 1 ? "potted_cactus" : v == 2 ? "potted_dead_bush" : "flower_pot");
        }
        b.set(8, 1, 1, "fletching_table");
        b.set(8, 1, 2, "cartography_table");
        b.set(8, 1, 3, "loom[facing=west]");
        b.set(6, 1, 2, "barrel[facing=up]");
        b.hang(3, 5, 2);
        b.hang(6, 5, 2);
        b.hang(4, 5, 6);
    }

    /** The sheriff's office: sandstone, a desk and wanted posters in front, two barred cells behind. */
    void sheriff(Bld b) {
        int h = 5, n = b.n(), d = b.d();
        shell(b, h, Kit.Bands.of(0, "cut_sandstone", 1, "smooth_sandstone", 5, "cut_sandstone"), "cut_sandstone", P.of("spruce_planks"));
        int yTop = b.y + h + 1;
        int smax = slabRoof(b, yTop, "smooth_sandstone", "spruce_slab");
        falseFront(b, h, smax, "smooth_sandstone", "cut_sandstone", "cut_sandstone", "smooth_red_sandstone", "sandstone_stairs", "smooth_sandstone_slab", "dark_oak", "4:SHERIFF");
        porch(b, 4, false, -1, 3, 5, 9);
        b.set(4, 1, 0, b.door("spruce", false, "left"));
        b.set(4, 2, 0, b.door("spruce", true, "left"));
        b.set(4, 3, 0, "chiseled_sandstone");
        for (int u : new int[] {1, 2, 6, 7}) for (int dy = 1; dy <= 2; dy++) b.set(u, dy, 0, "glass_pane");
        sideWindows(b, new int[] {2}, new int[] {2});
        for (int u : new int[] {2, 6}) b.set(u, 3, d, "iron_bars");

        // the office: a desk and chair, a chest, wanted posters on the wall
        b.set(2, 1, 2, "stripped_spruce_wood");
        b.set(3, 1, 2, "stripped_spruce_wood");
        b.set(2, 2, 2, "lantern");
        b.set(3, 2, 2, "flower_pot");
        b.set(2, 1, 3, "spruce_stairs[facing=" + b.back + "]");
        b.set(1, 1, 1, "chest[facing=east]");
        b.set(1, 1, 3, "barrel[facing=up]");
        String[][] posters = {{"WANTED", "Flag Thief", "Dead or Alive", "$1,000"}, {"WANTED", "Wool Rustler", "", "$500"}};
        for (int i = 0; i < 2; i++) {
            b.set(n - 1, 2, 1 + i, "spruce_wall_sign[facing=west]");
            w.signText(b.x(n - 1), b.y + 2, b.z(1 + i), false, posters[i]);
        }
        // the cells: a wall of bars, a door each (one left open), a bunk and a bucket
        for (int u = 1; u < n; u++) for (int dy = 1; dy <= 4; dy++) b.set(u, dy, 4, "iron_bars");
        for (int v = 5; v < d; v++) for (int dy = 1; dy <= 4; dy++) b.set(4, dy, v, "iron_bars");
        b.set(2, 1, 4, "iron_door[facing=" + b.back + ",half=lower,hinge=left,open=false]");
        b.set(2, 2, 4, "iron_door[facing=" + b.back + ",half=upper,hinge=left,open=false]");
        b.set(6, 1, 4, "iron_door[facing=" + b.back + ",half=lower,hinge=left,open=true]");
        b.set(6, 2, 4, "iron_door[facing=" + b.back + ",half=upper,hinge=left,open=true]");
        k.bed(b.x(1), b.y + 1, b.z(5), b.back, "white");
        k.bed(b.x(7), b.y + 1, b.z(5), b.back, "white");
        b.set(3, 1, 6, "cauldron");
        b.hang(4, 4, 2);
        b.hang(2, 4, 5);
        b.hang(6, 4, 5);
    }

    /**
     * The bank at the head of the square: red sandstone, iron-barred windows, a door between chiseled columns up a
     * step; inside a teller's counter behind bars, desks, strongboxes. Built whole on the north lot; its copy
     * stands on the south lot.
     */
    void bank(Bld b) {
        int h = 6, n = b.n(), d = b.d();
        shell(b, h, Kit.Bands.of(0, "smooth_red_sandstone", 1, "cut_red_sandstone", 5, "smooth_red_sandstone", 6, "cut_red_sandstone"),
                "chiseled_red_sandstone", P.of("smooth_sandstone"));
        int yTop = b.y + h + 1;
        int smax = slabRoof(b, yTop, "cut_red_sandstone", "dark_oak_slab");
        falseFront(b, h, smax, "cut_red_sandstone", "chiseled_red_sandstone", "smooth_red_sandstone", "smooth_sandstone",
                "smooth_red_sandstone_stairs", "smooth_red_sandstone_slab", "dark_oak", n / 2 + ":BANK");
        int c = n / 2;
        // the entrance: a door between columns, a step up to it, lamps either side of the step
        b.set(c, 1, 0, b.door("dark_oak", false, "left"));
        b.set(c, 2, 0, b.door("dark_oak", true, "left"));
        for (int s : new int[] {-1, 1}) for (int dy = 1; dy <= 3; dy++) b.set(c + s, dy, 0, "chiseled_red_sandstone");
        for (int u = c - 1; u <= c + 1; u++) b.set(u, 4, 0, "smooth_red_sandstone_stairs[facing=" + b.back + ",half=top]");
        for (int u = c - 1; u <= c + 1; u++) w.set(b.x(u), b.y, b.zf - b.in, "smooth_red_sandstone_stairs[facing=" + b.back + ",half=bottom]");
        for (int s : new int[] {-2, 2}) {
            w.set(b.x(c + s), b.y, b.zf - b.in, "red_sandstone_wall");
            w.set(b.x(c + s), b.y + 1, b.zf - b.in, "lantern");
        }
        // barred windows: three wide either side of the door, single ones down the sides and in the back
        for (int u : new int[] {2, 3, 4, n - 4, n - 3, n - 2}) for (int dy = 2; dy <= 3; dy++) b.set(u, dy, 0, "iron_bars");
        for (int u : new int[] {2, 3, 4, n - 4, n - 3, n - 2}) b.set(u, 1, 0, "smooth_red_sandstone_slab[type=top]");
        for (int v = 2; v <= d - 2; v += 2) for (int dy = 2; dy <= 3; dy++) {
            b.set(0, dy, v, "iron_bars");
            b.set(n, dy, v, "iron_bars");
        }
        for (int u : new int[] {3, n - 3}) b.set(u, 3, d, "iron_bars");
        // inside: the teller's counter with its cage, a gate at the end, desks and strongboxes behind
        for (int u = 1; u <= n - 3; u++) {
            b.set(u, 1, 3, "stripped_dark_oak_wood");
            boolean window = u == 3 || u == c || u == n - 4;
            b.set(u, 2, 3, window ? "air" : "iron_bars");
            b.set(u, 3, 3, "iron_bars");
        }
        b.set(n - 2, 1, 3, "dark_oak_fence_gate[facing=" + b.out + "]");
        b.set(n - 1, 1, 3, "stripped_dark_oak_wood");
        b.set(n - 1, 2, 3, "iron_bars");
        b.set(n - 1, 3, 3, "iron_bars");
        for (int u : new int[] {3, n - 4}) {
            b.set(u, 1, 4, "lectern[facing=" + b.out + "]");
        }
        b.set(c, 1, 4, "spruce_stairs[facing=" + b.back + "]");
        for (int u = 1; u <= n - 1; u++) b.set(u, 1, d - 1, u % 3 == 1 ? "barrel[facing=" + b.out + "]" : "chest[facing=" + b.out + "]");
        for (int u = 1; u <= n - 1; u += 3) b.set(u, 2, d - 1, "barrel[facing=" + b.out + "]");
        // the lobby: benches under the windows, a potted cactus by the door
        for (int u : new int[] {2, 3, 4, n - 4, n - 3, n - 2}) b.set(u, 1, 1, "spruce_stairs[facing=" + b.out + "]");
        b.set(c - 2, 1, 1, "potted_cactus");
        b.set(c + 2, 1, 1, "potted_cactus");
        for (int u : new int[] {3, c, n - 3}) {
            b.hang(u, 4, 2);
            b.hang(u, 4, d - 2);
        }
    }

    /** The blacksmith: an open-fronted shed with a forge and chimney, an anvil and a corral behind. */
    void smithy(Bld b) {
        int h = 5, n = b.n(), d = b.d();
        for (int u = 0; u <= n; u++) for (int v = 0; v <= d; v++) {
            b.set(u, 0, v, P.of("cobblestone", 3, "packed_mud", 2, "gravel", 1).pick(w.r));
            for (int dy = 1; dy <= h + 1; dy++) b.set(u, dy, v, "air");
        }
        for (int u = 0; u <= n; u++) for (int v = 0; v <= d; v++) {
            boolean wall = u == 0 || u == n || v == d;
            if (!wall) continue;
            boolean post = (u == 0 || u == n) && (v == 0 || v == d);
            for (int dy = 1; dy <= h; dy++) b.set(u, dy, v, post ? "stripped_spruce_log[axis=y]" : dy <= 2 ? "cobblestone" : "spruce_planks");
        }
        for (int u : new int[] {3, 6}) for (int dy = 1; dy <= h; dy++) b.set(u, dy, 0, "stripped_spruce_log[axis=y]");
        for (int u = 0; u <= n; u++) b.set(u, h, 0, "stripped_spruce_log[axis=x]");
        k.gable(b.x0, Math.min(b.zf, b.zb), b.x1, Math.max(b.zf, b.zb), b.y + h + 1, true, 1, Kit.Roof.of("spruce_planks"), P.of("spruce_planks"));
        // the forge in the back corner, its chimney up through the roof
        b.set(1, 1, d - 1, "bricks");
        b.set(2, 1, d - 1, "blast_furnace[facing=" + b.out + ",lit=true]");
        b.set(3, 1, d - 1, "furnace[facing=" + b.out + ",lit=true]");
        for (int u = 1; u <= 3; u++) {
            b.set(u, 2, d - 1, "brick_slab[type=bottom]");
            b.set(u, 3, d - 1, "bricks");
            b.set(u, 4, d - 1, "bricks");
        }
        for (int dy = 1; dy <= h + 6; dy++) b.set(2, dy, d, "bricks");
        b.set(5, 1, 2, "anvil[facing=east]");
        b.set(7, 1, 2, "grindstone[face=floor,facing=" + b.out + "]");
        b.set(5, 1, d - 1, "water_cauldron[level=3]");
        b.set(8, 1, d - 1, "smithing_table");
        b.set(8, 1, 1, "barrel[facing=up]");
        b.set(7, 1, d - 1, "barrel[facing=up]");
        b.hang(5, 4, 2);
        b.set(4, h - 1, 0, "spruce_hanging_sign[rotation=" + (b.out.equals("north") ? 8 : 0) + "]");
        w.signText(b.x(4), b.y + h - 1, b.z(0), true, "", "BLACKSMITH", "", "");
        // the corral behind: a fence, a gate, hay and a trough
        int z0 = b.zb + 1, z1 = b.zb + 5;
        for (int x = b.x0; x <= b.x1; x++) for (int z = z0; z <= z1; z++) {
            w.set(x, FLOOR, z, P.of("coarse_dirt", 2, "packed_mud", 1, "sand", 1).pick(w.r));
            boolean edge = x == b.x0 || x == b.x1 || z == z1;
            if (edge) w.set(x, FLOOR + 1, z, x == b.x1 && z == z0 + 2 ? "spruce_fence_gate[facing=east]" : "spruce_fence");
        }
        w.set(b.x0 + 2, FLOOR + 1, z0 + 2, "hay_block");
        w.set(b.x0 + 2, FLOOR + 2, z0 + 2, "hay_block");
        w.set(b.x0 + 3, FLOOR + 1, z0 + 2, "hay_block");
        for (int x = b.x0 + 5; x <= b.x0 + 8; x++) for (int z = z0 + 1; z <= z0 + 3; z++) {
            boolean water = z == z0 + 2 && x > b.x0 + 5 && x < b.x0 + 8;
            w.set(x, FLOOR + 1, z, water ? "water" : "spruce_planks");
        }
    }

    /** A timber stairway up the cliff behind the store: stringer, balusters, trestle bents, a landing at the rim. */
    void stairway() {
        int rail = 19;
        for (int i = 1; i <= 21; i++) {
            int x = -4 - i, sy = FLOOR + i;
            for (int z = 20; z <= 21; z++) {
                w.set(x, sy, z, "spruce_stairs[facing=west,half=bottom]");
                for (int dy = 1; dy <= 3; dy++) if (!w.air(x, sy + dy, z)) w.set(x, sy + dy, z, "air");
            }
            w.set(x, sy, rail, "stripped_spruce_log[axis=x]");
            w.set(x, sy + 1, rail, "spruce_fence");
            if (i % 4 == 0) {
                for (int z : new int[] {rail, 21}) for (int yy = FLOOR + 1; yy < sy; yy++) if (w.free(x, yy, z)) w.set(x, yy, z, "stripped_spruce_log[axis=y]");
                if (sy - 1 > FLOOR) w.set(x, sy - 1, 20, "stripped_spruce_log[axis=z]");
                if (sy > FLOOR + 8) for (int z = rail; z <= 21; z++) w.set(x, FLOOR + (sy - FLOOR) / 2, z, "stripped_spruce_log[axis=z]");
            }
        }
        // the landing, level with the rim, posts under its corners, a rail on its open sides
        for (int x = -28; x <= -26; x++) for (int z = rail; z <= 24; z++) {
            w.set(x, RIM, z, z == rail ? "stripped_spruce_log[axis=x]" : "spruce_planks");
            for (int dy = 1; dy <= 3; dy++) w.set(x, RIM + dy, z, "air");
        }
        for (int x = -28; x <= -26; x++) w.set(x, RIM + 1, rail, "spruce_fence");
        for (int z = rail; z <= 24; z++) if (land.topAt(-29, z) < RIM) w.set(-29, RIM + 1, z, "spruce_fence");
        for (int x : new int[] {-28, -26}) for (int z : new int[] {rail, 22}) for (int yy = RIM - 1; yy > FLOOR; yy--) {
            if (!w.free(x, yy, z)) break;
            w.set(x, yy, z, "stripped_spruce_log[axis=y]");
        }
        keepClear(-31, 17, -3, 26);
    }

    /** A water tower at the town's edge: four legs braced across, a deck, a round tank with a pointed lid. */
    void waterTower(int cx, int cz) {
        int y = FLOOR;
        for (int[] l : new int[][] {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) for (int dy = 1; dy <= 9; dy++) w.set(cx + l[0], y + dy, cz + l[1], "stripped_spruce_log[axis=y]");
        for (int o = -1; o <= 1; o++) for (int s : new int[] {-2, 2}) {
            w.set(cx + o, y + 5, cz + s, "stripped_spruce_log[axis=x]");
            w.set(cx + s, y + 5, cz + o, "stripped_spruce_log[axis=z]");
        }
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) w.set(cx + dx, y + 10, cz + dz, "spruce_planks");
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            double d = Math.hypot(dx, dz);
            if (d > 2.4) continue;
            for (int dy = 11; dy <= 14; dy++) w.set(cx + dx, y + dy, cz + dz, d > 1.4 ? (dy == 12 ? "stripped_spruce_wood" : "spruce_planks") : dy < 14 ? "water" : "spruce_planks");
        }
        k.cone(cx, cz, y + 15, 2, 1, 1.0, Kit.Roof.of("spruce_planks"));
        keepClear(cx - 4, cz - 4, cx + 4, cz + 4);
    }

    /** The arch over the road into town, its name on a sign hanging from the beam. */
    void arch(int x) {
        int y = FLOOR;
        for (int s : new int[] {-6, 6}) {
            for (int dy = 1; dy <= 6; dy++) w.set(x, y + dy, s, "stripped_spruce_log[axis=y]");
            w.set(x, y + 6, s - Integer.signum(s), "spruce_stairs[facing=" + (s < 0 ? "north" : "south") + ",half=top]");
        }
        for (int z = -7; z <= 7; z++) w.set(x, y + 7, z, "stripped_spruce_log[axis=z]");
        w.set(x, y + 6, 0, "spruce_hanging_sign[rotation=4]");
        w.signText(x, y + 6, 0, true, "WELCOME TO", "DUSTBOWL", "", "");
        for (int s : new int[] {-3, 3}) w.hangLight(x, y + 6, s, "lantern");
    }

    void outhouse(int cx, int cz) {
        int y = FLOOR;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            boolean wall = dx != 0 || dz != 0;
            for (int dy = 1; dy <= 3; dy++) if (wall) w.set(cx + dx, y + dy, cz + dz, Math.abs(dx) == 1 && Math.abs(dz) == 1 ? "stripped_spruce_log[axis=y]" : "spruce_planks");
            w.set(cx + dx, y + 4, cz + dz, "spruce_slab[type=bottom]");
        }
        w.set(cx, y + 1, cz + 1, "spruce_door[facing=north,half=lower,hinge=left,open=false]");
        w.set(cx, y + 2, cz + 1, "spruce_door[facing=north,half=upper,hinge=left,open=false]");
        w.set(cx, y + 3, cz + 1, "spruce_trapdoor[facing=south,half=top,open=true]");
    }

    // ---------------------------------------------------------------- the mine

    /**
     * The mine in the north mesa: a timbered portal at the foot of the cliff, a stair climbing into the rock,
     * then a ladder shaft up to a headframe on the rim.
     */
    void mine() {
        int mx = MINE_X;
        int w0 = (int) Math.floor(canyonW(mx, -1));
        // the portal and a short level run in, rails out onto the floor
        for (int j = 0; j <= 2; j++) {
            int z = -(w0 + j);
            for (int o = -1; o <= 1; o++) {
                w.set(mx + o, FLOOR, z, "packed_mud");
                for (int dy = 1; dy <= 4; dy++) w.set(mx + o, FLOOR + dy, z, "air");
            }
        }
        int pz = -(w0 + 1);
        for (int s : new int[] {-2, 2}) for (int dy = 1; dy <= 4; dy++) w.set(mx + s, FLOOR + dy, pz, "stripped_spruce_log[axis=y]");
        for (int o = -2; o <= 2; o++) w.set(mx + o, FLOOR + 5, pz, "stripped_spruce_log[axis=x]");
        w.set(mx, FLOOR + 5, pz + 1, "dark_oak_wall_sign[facing=south]");
        w.signText(mx, FLOOR + 5, pz + 1, true, "", "MINE", "", "");
        for (int s : new int[] {-1, 1}) w.hangLight(mx + s, FLOOR + 4, pz, "lantern");
        for (int z = pz; z <= pz + 5; z++) w.set(mx, FLOOR + 1, z, "rail");
        w.set(mx, FLOOR + 1, pz + 6, "hay_block");
        w.set(mx - 2, FLOOR + 1, pz + 3, "barrel[facing=up]");
        w.set(mx + 2, FLOOR + 1, pz + 4, "barrel[facing=up]");
        // the stair up into the rock, timbered every third step
        int steps = 10;
        for (int i = 1; i <= steps; i++) {
            int z = -(w0 + 2 + i), sy = FLOOR + i;
            for (int o = -1; o <= 1; o++) {
                for (int yy = sy - 1; yy >= sy - 2; yy--) if (!w.type(mx + o, yy, z).isSolid()) w.set(mx + o, yy, z, rock(mx + o, yy, z));
                w.set(mx + o, sy, z, "spruce_stairs[facing=north,half=bottom]");
                for (int dy = 1; dy <= 4; dy++) w.set(mx + o, sy + dy, z, "air");
            }
            if (i % 3 == 0) {
                for (int s : new int[] {-2, 2}) for (int dy = 1; dy <= 3; dy++) w.set(mx + s, sy + dy, z, "stripped_spruce_log[axis=y]");
                for (int o = -2; o <= 2; o++) w.set(mx + o, sy + 4, z, "stripped_spruce_log[axis=x]");
                if (i % 6 == 0) w.set(mx, sy + 3, z, "lantern[hanging=true]");
            }
        }
        // a level gallery, then the shaft: ladders up the north side to the rim
        int gy = FLOOR + steps, gz = -(w0 + 2 + steps);
        for (int j = 1; j <= 2; j++) for (int o = -1; o <= 1; o++) {
            w.set(mx + o, gy, gz - j, "packed_mud");
            for (int dy = 1; dy <= 4; dy++) w.set(mx + o, gy + dy, gz - j, "air");
        }
        int sz = gz - 3;
        for (int o = -1; o <= 1; o++) {
            w.set(mx + o, gy, sz, "packed_mud");
            for (int yy = gy + 1; yy <= RIM + 4; yy++) w.set(mx + o, yy, sz, "air");
        }
        for (int yy = gy + 1; yy <= RIM; yy++) w.set(mx, yy, sz, "ladder[facing=south]");
        w.hangLight(mx, gy + 3, gz - 1, "lantern");
        // the headframe over the shaft: four legs, a frame on top, the sheave and its chain
        int top = RIM + 8;
        for (int s : new int[] {-2, 2}) for (int t2 : new int[] {-1, 1}) for (int yy = RIM + 1; yy <= top; yy++) w.set(mx + s, yy, sz + t2, "stripped_spruce_log[axis=y]");
        for (int o = -2; o <= 2; o++) for (int t2 : new int[] {-1, 1}) w.set(mx + o, top + 1, sz + t2, "stripped_spruce_log[axis=x]");
        for (int s : new int[] {-2, 2}) w.set(mx + s, top + 1, sz, "stripped_spruce_log[axis=z]");
        for (int o = -1; o <= 1; o++) w.set(mx + o, top + 1, sz, "spruce_planks");
        w.set(mx, top, sz, "grindstone[face=ceiling,facing=east]");
        for (int yy = RIM + 4; yy <= top; yy++) w.set(mx + 1, yy, sz, "iron_chain[axis=y]");
        for (int s : new int[] {-2, 2}) w.set(mx + s, RIM + 4, sz, "stripped_spruce_log[axis=z]");
        for (int o = -1; o <= 1; o++) w.set(mx + o, RIM + 1, sz - 1, "spruce_fence");
        for (int s : new int[] {-2, 2}) w.set(mx + s, RIM + 1, sz, "spruce_fence");
        keepClear(mx - 5, sz - 4, mx + 5, -(w0 - 7));
    }

    // ---------------------------------------------------------------- scenery

    /** Bones in the end wall of a box canyon. */
    void fossil(int s) {
        int[][] bones = new int[11][];
        int i = 0;
        for (int dx = -3; dx <= 3; dx++) bones[i++] = new int[] {dx, 5};
        bones[i++] = new int[] {-2, 4};
        bones[i++] = new int[] {0, 4};
        bones[i++] = new int[] {2, 4};
        bones[i] = new int[] {0, 3};
        for (int[] bone : bones) {
            if (bone == null) continue;
            int x = GORGE_X + bone[0], y = FLOOR + bone[1];
            for (int az = GORGE_END - 6; az <= GORGE_END + 3; az++) {
                int z = s * az;
                if (!w.type(x, y, z).isSolid()) continue;
                w.set(x, y, z, bone[1] == 5 ? "bone_block[axis=x]" : "bone_block[axis=y]");
                break;
            }
        }
    }

    /** A covered wagon: wheels, a slab bed, hoops of canvas; a broken one has half its canvas fallen in and a wheel off. */
    void wagon(int cx, int cz, boolean broken) {
        int y = FLOOR + 1;
        for (int x = cx - 2; x <= cx + 3; x++) for (int z = cz - 1; z <= cz + 1; z++) w.set(x, y, z, "spruce_slab[type=top]");
        for (int x : new int[] {cx - 1, cx + 2}) {
            w.set(x, y, cz - 2, "spruce_trapdoor[facing=north,open=true,half=bottom]");
            if (!broken || x != cx + 2) w.set(x, y, cz + 2, "spruce_trapdoor[facing=south,open=true,half=bottom]");
        }
        if (!broken) {
            for (int x = cx - 2; x <= cx + 3; x++) {
                for (int dy = 1; dy <= 2; dy++) {
                    w.set(x, y + dy, cz - 1, "white_wool");
                    w.set(x, y + dy, cz + 1, "white_wool");
                }
                for (int z = cz - 1; z <= cz + 1; z++) w.set(x, y + 3, z, "white_wool");
            }
            for (int x : new int[] {cx - 2, cx + 3}) w.set(x, y + 1, cz, "barrel[facing=" + (x < cx ? "west" : "east") + "]");
            w.set(cx + 4, y, cz, "spruce_slab[type=bottom]");
            w.set(cx + 5, y, cz, "spruce_slab[type=bottom]");
            w.set(cx + 6, y, cz, "spruce_fence");
            w.set(cx + 6, y, cz + 2, "hay_block");
            w.set(cx + 7, y, cz + 2, "hay_block");
            w.set(cx + 6, y + 1, cz + 2, "hay_block");
            keepClear(cx - 3, cz - 3, cx + 8, cz + 3);
            return;
        }
        for (int x = cx - 2; x <= cx; x++) {
            for (int dy = 1; dy <= 2; dy++) {
                w.set(x, y + dy, cz - 1, "white_wool");
                w.set(x, y + dy, cz + 1, "white_wool");
            }
            w.set(x, y + 3, cz, "white_wool");
            w.set(x, y + 3, cz - 1, "white_wool");
            w.set(x, y + 3, cz + 1, "white_wool");
        }
        w.set(cx + 1, y + 1, cz - 1, "white_wool");
        w.set(cx + 2, y + 1, cz + 1, "barrel[facing=up]");
        w.set(cx + 4, FLOOR + 1, cz, "spruce_slab[type=bottom]");
        w.set(cx + 5, FLOOR + 1, cz, "spruce_slab[type=bottom]");
        w.set(cx - 4, FLOOR + 1, cz + 2, "spruce_trapdoor[half=bottom,open=false,facing=north]");
        // a cow's skull on a post by the wash
        int sx = cx - 3, sz = (int) Math.round(washZ(cx - 3)) - 4;
        w.set(sx, FLOOR + 1, sz, "spruce_fence");
        w.set(sx, FLOOR + 2, sz, "skeleton_skull[rotation=6]");
        keepClear(cx - 5, cz - 3, cx + 6, cz + 3);
    }

    /** Boulders fallen from the cliffs: cover out on the open floor. */
    void boulders() {
        double[][] rocks = {{-68, -11, 2.6, 1.9, 3}, {-62, 12, 2.1, 1.6, 2}, {-48, -10, 2.3, 1.7, 3}, {-43, 12, 1.8, 1.5, 2}};
        for (double[] b : rocks) {
            int cx = (int) b[0], cz = (int) b[1];
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
                double e = Math.pow(dx / b[2], 2) + Math.pow(dz / b[3], 2);
                if (e > 1) continue;
                int x = cx + dx, z = cz + dz;
                if (land.topAt(x, z) != FLOOR) continue;
                int hgt = (int) Math.round((1 - e) * b[4] + 0.4);
                for (int dy = 1; dy <= hgt; dy++) w.set(x, FLOOR + dy, z, rock(x, FLOOR + dy, z));
                if (hgt == 0) w.set(x, FLOOR + 1, z, "smooth_red_sandstone_slab[type=bottom]");
            }
            keepClear(cx - 3, cz - 3, cx + 3, cz + 3);
        }
    }

    /** A rock pillar standing on the canyon floor: tall cover, banded like the cliffs, narrowing to a ledge cap. */
    void pillar(int cx, int cz, int h) {
        for (int dy = 1; dy <= h; dy++) {
            double rr = dy == h ? 2.3 : dy == h - 1 ? 1.9 : 2.1 - dy * 0.12;
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
                if (Math.hypot(dx, dz * 1.15) > rr) continue;
                int x = cx + dx, z = cz + dz;
                if (dy == 1 && land.topAt(x, z) != FLOOR) continue;
                w.set(x, FLOOR + dy, z, rock(x, FLOOR + dy, z));
            }
        }
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            int x = cx + dx, z = cz + dz;
            if (Math.hypot(dx, dz) > 2.9 || Math.hypot(dx, dz) < 2.0 || !w.air(x, FLOOR + 1, z) || land.topAt(x, z) != FLOOR) continue;
            if (w.n2(x * 3.1, z * 3.1, 2) > 0) w.set(x, FLOOR + 1, z, "smooth_red_sandstone_slab[type=bottom]");
        }
        keepClear(cx - 4, cz - 4, cx + 4, cz + 4);
    }

    /** The bleached bones of a steer half sunk in the sand. */
    void skeleton(int cx, int cz) {
        for (int x = cx - 2; x <= cx + 1; x++) w.set(x, FLOOR + 1, cz, "bone_block[axis=x]");
        for (int x : new int[] {cx - 1, cx + 1}) {
            w.set(x, FLOOR + 1, cz - 1, "bone_block[axis=z]");
            w.set(x, FLOOR + 1, cz + 1, "bone_block[axis=z]");
        }
        w.set(cx + 2, FLOOR + 1, cz, "skeleton_skull[rotation=12]");
        keepClear(cx - 3, cz - 2, cx + 3, cz + 2);
    }

    /** Cacti on the open sand, two to four tall, never against anything. */
    void cacti() {
        List<int[]> placed = new ArrayList<>();
        for (int tries = 0; tries < 3000 && placed.size() < 34; tries++) {
            int x = -110 + w.r.nextInt(110), z = -42 + w.r.nextInt(85);
            if (!w.canon(x, z) || isClear(x, z, 1)) continue;
            int g = land.topAt(x, z);
            if (g != FLOOR && g < RIM - 1) continue;
            String ground = w.type(x, g, z).name();
            if (!ground.equals("SAND") && !ground.equals("RED_SAND")) continue;
            boolean ok = true;
            for (int dx = -1; dx <= 1 && ok; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = 1; dy <= 4; dy++)
                if (!w.air(x + dx, g + dy, z + dz)) { ok = false; break; }
            for (int[] p : placed) if (Math.abs(p[0] - x) < 5 && Math.abs(p[1] - z) < 5) ok = false;
            if (!ok) continue;
            int h = 2 + w.r.nextInt(3);
            for (int dy = 1; dy <= h; dy++) w.set(x, g + dy, z, "cactus");
            if (w.r.nextInt(3) == 0) w.set(x, g + h + 1, z, "cactus_flower");
            placed.add(new int[] {x, z});
        }
    }

    /** Fallen rock along the feet of the cliffs: a ragged talus, a block or a slab high. */
    void rubble() {
        for (int x = -112; x <= 0; x++) for (int z = -44; z <= 44; z++) {
            if (!w.canon(x, z) || isClear(x, z, 1)) continue;
            int g = land.topAt(x, z);
            if (g != FLOOR) continue;
            int high = 0;
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) high = Math.max(high, land.topAt(x + dx, z + dz) - g);
            if (high < 6) continue;
            double n = w.fbm(x * 1.4 + 70, z * 1.4 - 30, 5, 2);
            if (n < 0.15 || !w.air(x, g + 1, z)) continue;
            boolean close = false;
            for (int[] o : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) if (land.topAt(x + o[0], z + o[1]) - g >= 3) close = true;
            if (n > 0.42 || close && n > 0.3) {
                w.set(x, g + 1, z, rock(x, g + 1, z));
                if (close && n > 0.55) w.set(x, g + 2, z, rock(x, g + 2, z));
            } else {
                w.set(x, g + 1, z, w.r.nextInt(3) == 0 ? "red_sandstone_slab[type=bottom]" : "smooth_red_sandstone_slab[type=bottom]");
            }
        }
    }

    /** Hoodoos on the rims: columns of strata under a wider cap, cover for whoever holds the high ground. */
    void hoodoos() {
        List<int[]> placed = new ArrayList<>();
        for (int tries = 0; tries < 400 && placed.size() < 13; tries++) {
            int x = -108 + w.r.nextInt(108), z = (w.r.nextBoolean() ? -1 : 1) * (24 + w.r.nextInt(18));
            if (!w.canon(x, z) || isClear(x, z, 3)) continue;
            boolean flat = true;
            for (int dx = -3; dx <= 3 && flat; dx++) for (int dz = -3; dz <= 3; dz++) if (land.topAt(x + dx, z + dz) < RIM) { flat = false; break; }
            if (!flat) continue;
            boolean crowded = false;
            for (int[] p : placed) if (Math.abs(p[0] - x) < 11 && Math.abs(p[1] - z) < 11) crowded = true;
            if (crowded) continue;
            int gy = RIM;
            int h = 3 + w.r.nextInt(5);
            double r0 = 1.2 + w.r.nextDouble() * 1.1;
            for (int dy = 1; dy <= h; dy++) {
                double rr = r0 * (dy == h ? 1.35 : 1 - dy * 0.05);
                for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++)
                    if (Math.hypot(dx, dz) <= rr) w.set(x + dx, gy + dy, z + dz, rock(x + dx, gy + dy, z + dz));
            }
            placed.add(new int[] {x, z});
            keepClear(x - 3, z - 3, x + 3, z + 3);
        }
    }
}
