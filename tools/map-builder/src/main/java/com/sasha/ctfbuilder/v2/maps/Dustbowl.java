package com.sasha.ctfbuilder.v2.maps;

import com.sasha.ctfbuilder.v2.Arena;
import com.sasha.ctfbuilder.v2.Kit;
import com.sasha.ctfbuilder.v2.Land;
import com.sasha.ctfbuilder.v2.Theme;
import com.sasha.ctfbuilder.v2.W.P;
import org.bukkit.World;

/**
 * Dustbowl: a long badlands canyon. The teams' adobe forts sit on raised ground at either end; between them
 * runs the canyon floor — a dry riverbed with a frontier town halfway (the hill is its square) — walled in by
 * striped mesas. Three heights to fight on: the floor (fast, but everyone above can see you), and the two mesa
 * rims (high ground, rope bridges over side gorges). A mine tunnel climbs through each team's near mesa from the
 * floor to the rim. Long sightlines: bows matter here.
 */
public final class Dustbowl extends Arena {

    static final int FLOOR = 66, FORT = 76, RIM = 87, GORGE_X = -42;

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

    double rimEdge(int x, int z) {
        return 18 + w.fbm(x * 0.6 + 5, z > 0 ? 40 : -40, 10, 2) * 3;
    }

    double height(int x, int z) {
        double ex = Math.max(0, Math.abs(x) - 104), ez = Math.max(0, Math.abs(z) - 40);
        double edge = Math.hypot(ex, ez) + w.fbm(x * 0.5, z * 0.5, 12, 2) * 4;
        if (edge > 8) return Double.NaN;
        double az = Math.abs(z);
        double re = rimEdge(x, z);
        double h;
        if (az > re + 4) {
            h = RIM + Math.round(w.fbm(x + 300, z, 20, 2) * 1.5);
            // a gorge cuts the mesa
            double g = Math.abs(x - GORGE_X) - 3.5 - w.fbm(z, x, 4, 2) * 1.2;
            if (g < 0) h = FLOOR + 2;
        } else if (az > re) {
            double k2 = (az - re) / 4.0;
            h = FLOOR + (RIM - FLOOR) * Land.smooth(k2 * 1.1);  // the cliff
        } else {
            h = FLOOR + w.fbm(x + 77, z - 9, 14, 2) * 1.2;
            if (az < 3.5) h = FLOOR - 1;                       // the dry riverbed
        }
        // the fort's raised ground at the west end, and the ramp down from its gate
        if (x < -77) {
            double p = Land.smooth((-77 - x) / 4.0);
            if (az <= re + 2) h = Math.max(h, h * (1 - p) + FORT * p);
        }
        if (x >= -78 && x < -62 && az < 4) h = Math.max(h, FORT - (x + 78) * 10.0 / 16.0);
        if (edge > 4) h -= (edge - 4);
        return h;
    }

    String surface(int x, int z, int y, int steep, double edge) {
        double n = w.fbm(x + 913, z - 377, 9, 2);
        if (steep >= 3) return y > FLOOR + 6 ? strata(y) : "terracotta";
        if (y >= RIM - 2) return n > 0.3 ? "coarse_dirt" : n < -0.3 ? "red_sand" : "red_sandstone";
        if (y < FLOOR) return n > 0 ? "gravel" : "sand";
        return n > 0.35 ? "red_sand" : n < -0.4 ? "coarse_dirt" : "sand";
    }

    static final String[] STRATA = {"red_terracotta", "orange_terracotta", "terracotta", "yellow_terracotta", "terracotta", "white_terracotta",
            "orange_terracotta", "red_terracotta", "brown_terracotta", "terracotta", "light_gray_terracotta", "orange_terracotta"};

    static String strata(int y) {
        return STRATA[Math.floorMod(y / 2, STRATA.length)];
    }

    @Override
    protected void build() {
        Land land = new Land(w, t, -118, 118, -54, 54);
        land.top = this::height;
        land.surface = this::surface;
        land.depth = 26;
        land.bands = new String[] {"red_terracotta", "orange_terracotta", "terracotta", "yellow_terracotta", "terracotta", "white_terracotta",
                "orange_terracotta", "red_terracotta", "brown_terracotta", "terracotta", "light_gray_terracotta", "orange_terracotta"};
        land.warp = 0.15;
        land.speck = P.of("terracotta", 3, "red_sandstone", 1);
        land.soil = P.of("red_sandstone", 2, "terracotta", 2);
        land.build();

        fort(-92, 0);
        town();
        mine();
        String[] rope = {"spruce_planks", "spruce_slab", "stripped_spruce_wood", "spruce_fence", "spruce_log", null};
        k.bridge(GORGE_X - 7, -28, GORGE_X + 7, -28, 3, rope[0], rope[1], rope[2], rope[3], rope[4], rope[5]);
        k.bridge(GORGE_X - 7, 28, GORGE_X + 7, 28, 3, rope[0], rope[1], rope[2], rope[3], rope[4], rope[5]);
        // trails up from the fort to both rims
        for (int s : new int[] {-1, 1}) trail(-74, s * 8, -54, s * 32);

        // hoodoos and rock stacks on the rims: cover for the snipers up there
        for (int i = 0; i < 22; i++) {
            int x = -100 + w.r.nextInt(100), z = (w.r.nextBoolean() ? -1 : 1) * (26 + w.r.nextInt(14));
            if (isClear(x, z, 3)) continue;
            int gy = w.ground(x, z);
            if (gy < RIM - 2) continue;
            int h = 3 + w.r.nextInt(6);
            double r0 = 1.2 + w.r.nextDouble() * 1.3;
            for (int dy = 1; dy <= h; dy++) {
                double rr = r0 * (dy == h ? 1.25 : 1 - dy * 0.06);
                for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++)
                    if (Math.hypot(dx, dz) <= rr) w.set(x + dx, gy + dy, z + dz, strata(gy + dy));
            }
            keepClear(x - 3, z - 3, x + 3, z + 3);
        }
        // the desert: cacti and dead bushes on the floor, dry grass on the rims, a few dead trees
        plants(P.of("dead_bush", 3, "short_dry_grass", 6, "tall_dry_grass", 2, "cactus", 2), 0.08, (x, z) -> true);
        trees(new Theme.Tree[] {Theme.Tree.DEAD}, new int[] {1}, 0.002, 18, 3, (x, z) -> Math.abs(z) > 24);

        spawn = new double[] {-97.5, FORT + 1, 0.5, -90};
        flag = new int[] {-86, FORT + 1, 0};
        pads.add(new int[] {-85, FORT + 1, -9});
        pads.add(new int[] {-85, FORT + 1, 9});
        hills.add(new Hill("Town Square", -3, FLOOR + 1, -3, 3, FLOOR + 4, 3));
    }

    // ---------------------------------------------------------------- the fort

    /**
     * An adobe fort: mud-brick curtain walls with a walk and parapet, square corner towers with beams poking
     * out of them, a gatehouse to the east over heavy doors, barracks along the west wall (the spawn), a well
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
            // arrow slits and a ladder up inside
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
        // the gate in the east wall: a gatehouse arch with doors open, beams over
        for (int dz = -2; dz <= 2; dz++) for (int dy = 1; dy <= 4; dy++) w.set(x1, y + dy, cz + dz, "air");
        for (int dz = -3; dz <= 3; dz++) for (int dy = 5; dy <= 9; dy++) {
            boolean edge = Math.abs(dz) == 3 || dy == 9;
            w.set(x1, y + dy, cz + dz, dy == 5 ? "stripped_spruce_log[axis=z]" : edge ? "mud_bricks" : "mud_bricks");
            w.set(x1 + 1, y + dy, cz + dz, dy == 5 ? "stripped_spruce_log[axis=z]" : edge || dy == 9 ? "mud_bricks" : "air");
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
        for (int dz = -1; dz <= 1; dz++) for (int dy = 1; dy <= 3; dy++) w.set(bx1, y + dy, cz + dz, "air");
        for (int z = bz0 + 2; z <= bz1 - 2; z += 4) {
            k.window(bx1, y + 2, z, "east", 1, 2, "glass_pane", null, "spruce_trapdoor");
        }
        for (int z = bz0 + 1; z <= bz1 - 1; z += 2) {
            if (Math.abs(z - cz) <= 2) continue;
            k.bed(bx0 + 2, y + 1, z, "west", "TEAM");
            w.set(bx0 + 3, y + 1, z, "barrel[facing=up]");
        }
        for (int z = bz0 + 3; z <= bz1 - 3; z += 4) w.hangLight(bx0 + 5, y + 4, z, "lantern");
        w.set(bx0 + 1, y + 3, cz, "TEAM_wall_banner[facing=east]");
        // the yard: a well, water troughs, hay, a cart
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
        // the flag's post in the middle of the yard
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) w.set(cx + 6 + dx, y, cz + dz, "chiseled_red_sandstone");
        keepClear(x0 - 4, z0 - 4, x1 + 4, z1 + 4);
    }

    /** A switchback trail cut into the slope from the fort up to the rim. */
    void trail(int ax, int az, int bx, int bz) {
        double len = Math.hypot(bx - ax, bz - az);
        int ya = w.ground(ax, az), yb = RIM;
        for (double s = 0; s <= len; s += 0.4) {
            double k2 = s / len;
            int x = (int) Math.round(ax + (bx - ax) * k2), z = (int) Math.round(az + (bz - az) * k2);
            int yy = (int) Math.round(ya + (yb - ya) * k2);
            String up = Math.abs(bx - ax) >= Math.abs(bz - az) ? (bx > ax ? "east" : "west") : (bz > az ? "south" : "north");
            for (int o = -1; o <= 1; o++) {
                int px = x + o, pz = z;
                for (int h = 1; h <= 4; h++) w.set(px, yy + h, pz, "air");
                w.set(px, yy, pz, yy < yb ? "mud_brick_stairs[facing=" + up + ",half=bottom]" : "packed_mud");
                for (int b = yy - 1; b > yy - 6 && !w.type(px, b, pz).isSolid(); b--) w.set(px, b, pz, "mud_bricks");
            }
        }
        keepClear(Math.min(ax, bx) - 2, Math.min(az, bz) - 2, Math.max(ax, bx) + 2, Math.max(az, bz) + 2);
    }

    // ---------------------------------------------------------------- the town

    /**
     * The frontier town in the middle of the canyon: on the near side of the square a saloon with a balcony and
     * a general store opposite, a water tower; boardwalks along the fronts. The square in the middle is open.
     */
    void town() {
        int y = FLOOR;
        // the street: packed dirt between boardwalks, the square in the middle
        for (int x = -30; x <= 0; x++) for (int z = -6; z <= 6; z++) {
            if (!w.canon(x, z)) continue;
            int gy = w.ground(x, z);
            c(x, y, z, Math.abs(x) <= 5 && Math.abs(z) <= 5 ? (((x + z) & 1) == 0 ? "smooth_sandstone" : "cut_sandstone") : "coarse_dirt");
            for (int yy = gy + 1; yy < y; yy++) c(x, yy, z, "sand");
            for (int h = 1; h <= 8; h++) c(x, y + h, z, "air");
        }
        // a well in the square (built half each)
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (!w.canon(dx, dz)) continue;
            boolean mid = dx == 0 && dz == 0;
            c(dx, y, dz, mid ? "water" : "smooth_sandstone");
        }
        falseFront(-14, -12, 9, 7, "north", true);   // the saloon
        falseFront(-14, 12, 7, 6, "south", false);   // the store
        // the water tower behind the store
        int tx = -26, tz = 14, ty = w.ground(tx, tz);
        if (ty > 50) {
            for (int[] l : new int[][] {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) for (int dy = 1; dy <= 8; dy++) w.set(tx + l[0], ty + dy, tz + l[1], "stripped_spruce_log[axis=y]");
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) w.set(tx + dx, ty + 9, tz + dz, "spruce_planks");
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > 2.4) continue;
                for (int dy = 10; dy <= 13; dy++) w.set(tx + dx, ty + dy, tz + dz, d > 1.4 ? (dy == 11 ? "stripped_spruce_wood" : "spruce_planks") : "water");
            }
            k.cone(tx, tz, ty + 14, 2, 1, 1.0, Kit.Roof.of("spruce_planks"));
            for (int dy = 1; dy <= 9; dy++) w.set(tx + 3, ty + dy, tz + 2, "ladder[facing=east]");
            keepClear(tx - 4, tz - 4, tx + 4, tz + 4);
        }
        // hitching rails and barrels along the street
        for (int x = -28; x <= -8; x += 5) for (int z : new int[] {-7, 7}) {
            int gy = w.ground(x, z);
            if (gy < 50 || !w.air(x, gy + 1, z)) continue;
            w.set(x, gy + 1, z, x % 2 == 0 ? "barrel[facing=up]" : "spruce_fence");
        }
        keepClear(-31, -18, 0, 18);
    }

    /**
     * A false-front building: a two-storey sign front taller than the gable behind it, a porch roof on posts
     * over a boardwalk, a door and windows; the saloon gets a balcony and swing doors.
     */
    void falseFront(int cx, int frontZ, int width, int depth, String faces, boolean saloon) {
        int dirZ = faces.equals("north") ? -1 : 1; // the building runs back away from the street
        int x0 = cx - width / 2, x1 = cx + width / 2;
        int zf = frontZ, zb = frontZ + dirZ * depth;
        int y = FLOOR;
        int z0 = Math.min(zf, zb), z1 = Math.max(zf, zb);
        level(x0 - 1, z0 - 1, x1 + 1, z1 + 1, y, P.of("coarse_dirt"), P.of("sand"));
        int h = saloon ? 8 : 5;
        k.walls(x0, z0, x1, z1, y, h, Kit.Bands.of(0, "spruce_planks", 1, P.of("spruce_planks", 4, "stripped_spruce_wood", 1)), "stripped_dark_oak_log", 4,
                P.of("oak_planks"));
        k.gable(x0, z0, x1, z1, y + h + 1, false, 1, Kit.Roof.of("dark_oak_planks"), P.of("spruce_planks"));
        // the false front: the street wall carried up square above the roof line, a cornice on top
        int frontTop = y + h + 1 + (x1 - x0) / 2 + 1;
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            for (int yy = y + h + 1; yy <= frontTop; yy++) w.set(x, yy, zf, x == x0 - 1 || x == x1 + 1 ? "stripped_dark_oak_log[axis=y]" : "spruce_planks");
            w.set(x, frontTop + 1, zf, "dark_oak_slab[type=bottom]");
        }
        // the sign board across the front
        for (int x = x0 + 1; x <= x1 - 1; x++) w.set(x, frontTop - 1, zf + dirZ * -1, "dark_oak_trapdoor[facing=" + faces + ",half=top,open=true]");
        // porch: a roof on posts over the boardwalk
        int pz = zf - dirZ * 3;
        for (int x = x0 - 1; x <= x1 + 1; x++) for (int z = Math.min(zf - dirZ, pz); z <= Math.max(zf - dirZ, pz); z++) {
            w.set(x, y, z, "spruce_planks");
            for (int hh = 1; hh <= 4; hh++) if (!w.air(x, y + hh, z)) w.set(x, y + hh, z, "air");
        }
        for (int x = x0 - 1; x <= x1 + 1; x += Math.max(2, (width + 2) / 3)) for (int hh = 1; hh <= 3; hh++) w.set(x, y + hh, pz, "stripped_spruce_log[axis=y]");
        for (int hh = 1; hh <= 3; hh++) w.set(x1 + 1, y + hh, pz, "stripped_spruce_log[axis=y]");
        for (int x = x0 - 1; x <= x1 + 1; x++) for (int z = Math.min(zf - dirZ, pz); z <= Math.max(zf - dirZ, pz); z++) {
            w.set(x, y + 4, z, "dark_oak_slab[type=bottom]");
        }
        // the door and windows on the street side
        w.set(cx, y + 1, zf, saloon ? "air" : "spruce_door[facing=" + Kit.opp(faces) + ",half=lower,hinge=left,open=false]");
        w.set(cx, y + 2, zf, saloon ? "air" : "spruce_door[facing=" + Kit.opp(faces) + ",half=upper,hinge=left,open=false]");
        if (saloon) {
            w.set(cx - 1, y + 1, zf, "air");
            w.set(cx - 1, y + 2, zf, "air");
            w.set(cx, y + 1, zf - dirZ, "air");
        }
        for (int x : new int[] {x0 + 1, x1 - 1}) {
            w.set(x, y + 2, zf, "glass_pane");
            w.set(x, y + 3, zf, "glass_pane");
        }
        if (saloon) {
            // upstairs windows and the balcony on the porch roof
            for (int x = x0 + 1; x <= x1 - 1; x += 2) w.set(x, y + 6, zf, "glass_pane");
            for (int x = x0 - 1; x <= x1 + 1; x++) w.set(x, y + 5, pz, "spruce_fence");
            // inside: the bar, tables, a piano (a note block with a stair seat)
            for (int x = x0 + 1; x <= x1 - 1; x++) w.set(x, y + 1, zb - dirZ * 2, "stripped_spruce_wood");
            for (int x = x0 + 1; x <= x1 - 1; x++) w.set(x, y + 1, zb - dirZ, x % 2 == 0 ? "barrel[facing=up]" : "air");
            k.table(x0 + 2, y + 1, zf + dirZ * 2, "spruce_fence", "spruce_pressure_plate");
            k.table(x1 - 2, y + 1, zf + dirZ * 2, "spruce_fence", "spruce_pressure_plate");
            w.set(x0 + 1, y + 1, zf + dirZ * 4, "note_block");
            w.hangLight(cx, y + h - 1, (zf + zb) / 2, "lantern");
            // the upstairs floor and a stair up to it
            for (int x = x0 + 1; x <= x1 - 1; x++) for (int z = z0 + 1; z <= z1 - 1; z++) w.set(x, y + 4, z, "spruce_planks");
            for (int i = 0; i < 3; i++) {
                int sz = zb - dirZ * (1 + i);
                w.set(x1 - 1, y + 1 + i, sz, "spruce_stairs[facing=" + Kit.opp(faces) + ",half=bottom]");
                for (int hh = 1; hh <= 3; hh++) w.set(x1 - 1, y + 1 + i + hh, sz, "air");
            }
        } else {
            for (int x = x0 + 1; x <= x1 - 1; x++) for (int s : new int[] {1, 2}) w.set(x, y + 1, zb - dirZ * s, s == 1 ? "barrel[facing=up]" : "air");
            w.set(x0 + 1, y + 1, zf + dirZ * 2, "crafting_table");
            w.hangLight(cx, y + h - 1, (zf + zb) / 2, "lantern");
        }
        keepClear(x0 - 2, Math.min(z0, pz) - 1, x1 + 2, Math.max(z1, pz) + 1);
    }

    // ---------------------------------------------------------------- the mine

    /** A mine through the north mesa: timbered entrance on the floor, a sloping tunnel up, out onto the rim. */
    void mine() {
        int ex = -56, ez = -16, ey = FLOOR;
        // the tunnel climbs north-west from the canyon floor up inside the mesa
        int steps = RIM - FLOOR;
        for (int i = 0; i <= steps + 3; i++) {
            int x = ex - (i / 3), z = ez - i;
            int yy = Math.min(RIM, ey + i);
            for (int o = -1; o <= 1; o++) {
                for (int h = 1; h <= 3; h++) w.set(x + o, yy + h, z, "air");
                w.set(x + o, yy, z, i % 4 == 0 && o == 0 ? "rail" : "packed_mud");
                if (i < steps) w.set(x + o, yy, z, o == 0 ? "spruce_stairs[facing=north,half=bottom]" : "packed_mud");
            }
            // timber frames every 4
            if (i % 4 == 0 && i < steps) {
                for (int h = 1; h <= 3; h++) {
                    w.set(x - 2, yy + h, z, "stripped_spruce_log[axis=y]");
                    w.set(x + 2, yy + h, z, "stripped_spruce_log[axis=y]");
                }
                for (int o = -2; o <= 2; o++) w.set(x + o, yy + 4, z, "stripped_spruce_log[axis=x]");
                w.set(x, yy + 3, z, "lantern[hanging=true]");
            }
        }
        // the entrance frame on the canyon floor
        for (int h = 1; h <= 4; h++) {
            w.set(ex - 2, ey + h, ez + 1, "stripped_spruce_log[axis=y]");
            w.set(ex + 2, ey + h, ez + 1, "stripped_spruce_log[axis=y]");
        }
        for (int o = -2; o <= 2; o++) w.set(ex + o, ey + 5, ez + 1, "stripped_spruce_log[axis=x]");
        keepClear(ex - 12, ez - steps - 6, ex + 3, ez + 3);
    }
}
