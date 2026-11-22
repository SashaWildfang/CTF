package com.sasha.ctfbuilder.v2.maps;

import com.sasha.ctfbuilder.v2.Arena;
import com.sasha.ctfbuilder.v2.Kit;
import com.sasha.ctfbuilder.v2.Land;
import com.sasha.ctfbuilder.v2.Theme;
import com.sasha.ctfbuilder.v2.W.P;
import org.bukkit.World;

/**
 * Mycelia: a forest of giant mushrooms over a swamp. Each team lives on top of a great red cap; the fighting
 * is up in the air between caps at different heights, joined by rope bridges, with the swamp floor far
 * below as the slow way round (every stem has a spiral of shelf fungus to climb back up). Three caps in the
 * middle are the King of the Hill points: the Great Cap highest in the centre, the North and South caps lower
 * either side. From home, one lane runs north and one south, each over two stepping caps.
 */
public final class Mycelia extends Arena {

    static final int FLOOR = 60;

    /** A cap: centre, top height, radius, stem radius, red or brown. */
    record Cap(int x, int z, int top, int r, int stem, boolean red, int flat) {}

    static final Cap BASE = new Cap(-60, 0, 88, 14, 4, true, 10);
    static final Cap A = new Cap(-36, -17, 82, 7, 3, false, 4);
    static final Cap B = new Cap(-33, 15, 78, 7, 3, true, 4);
    static final Cap C = new Cap(-19, -2, 86, 6, 3, false, 3);
    static final Cap GREAT = new Cap(0, 0, 94, 11, 5, true, 7);
    static final Cap NORTH = new Cap(0, -30, 80, 9, 4, false, 5);
    static final Cap SOUTH = new Cap(0, 30, 80, 9, 4, false, 5);

    public Mycelia(World world) {
        super(world, "mycelia", false, Theme.get("mycelia"));
        name = "Mycelia";
        icon = "RED_MUSHROOM";
        minutes = 14;
        kothPoints = 250;
        captureSeconds = 8;
        reach = 96;
        low = 15;
        high = 130;
        voidY = 12;
        spectator = new double[] {0.5, 115, 0.5, -90, 40};
    }

    // ---------------------------------------------------------------- the swamp floor

    double floor(int x, int z) {
        double e = Land.ellipse(x, z, 0, 0, 84, 42, 0) + w.fbm(x, z, 16, 2) * 0.08;
        double lobe = Land.ellipse(x, z, -60, 0, 26, 26, 0) + w.fbm(x + 9, z, 10, 2) * 0.08;
        double inside = Math.max(e, lobe);
        if (inside < 0) return Double.NaN;
        double h = FLOOR + w.fbm(x + 300, z - 120, 18, 3) * 2.2 + 1.2 * Land.smooth(inside / 0.15);
        if (w.fbm(x * 0.9 + 77, z * 0.9 - 33, 11, 2) > 0.38) h = FLOOR - 2; // mud pools under a little water
        return h;
    }

    String floorSurface(int x, int z, int y, int steep, double edge) {
        double n = w.fbm(x + 913, z - 377, 8, 2);
        if (y <= FLOOR - 1) return "mud";
        if (steep >= 3) return "rooted_dirt";
        if (n > 0.35) return "moss_block";
        if (n < -0.4) return "podzol";
        return w.r.nextInt(9) == 0 ? "coarse_dirt" : "mycelium";
    }

    @Override
    protected void build() {
        Land land = new Land(w, t, -92, 92, -50, 50);
        land.top = this::floor;
        land.surface = this::floorSurface;
        land.water = (x, z) -> w.fbm(x * 0.9 + 77, z * 0.9 - 33, 11, 2) > 0.38 ? FLOOR - 1 : Double.NaN;
        land.depth = 24;
        land.build();

        // the caps (the ones across the middle are built half each, the copy completes them)
        for (Cap cap : new Cap[] {BASE, A, B, C}) cap(cap, false);
        for (Cap cap : new Cap[] {GREAT, NORTH, SOUTH}) cap(cap, true);
        // spiral climbs round the stems
        spiral(BASE, 0.0);
        spiral(A, 2.0);
        spiral(B, 4.0);
        // the middle caps are built half each, so their climbs are double spirals (one per half)
        spiral(GREAT, 1.0);
        spiral(GREAT, 1.0 + Math.PI);
        spiral(NORTH, 3.0);
        spiral(SOUTH, 3.0 + Math.PI);

        // bridges between caps (rope bridges: planks, rope rails, lamps)
        String[] b = {"oak_planks", "oak_slab", "stripped_oak_wood", "oak_fence", "stripped_oak_log", null};
        k.bridge(BASE.x + 8, BASE.z - 6, A.x, A.z, 3, b[0], b[1], b[2], b[3], b[4], b[5]);
        k.bridge(BASE.x + 8, BASE.z + 6, B.x, B.z, 3, b[0], b[1], b[2], b[3], b[4], b[5]);
        k.bridge(A.x, A.z, C.x, C.z, 3, b[0], b[1], b[2], b[3], b[4], b[5]);
        k.bridge(A.x, A.z, NORTH.x - 2, NORTH.z + 2, 3, b[0], b[1], b[2], b[3], b[4], b[5]);
        k.bridge(B.x, B.z, SOUTH.x - 2, SOUTH.z - 2, 3, b[0], b[1], b[2], b[3], b[4], b[5]);
        k.bridge(C.x, C.z, GREAT.x - 3, GREAT.z, 3, b[0], b[1], b[2], b[3], b[4], b[5]);

        village();

        // the floor: big and small mushrooms, roots, moss and ferns
        trees(new Theme.Tree[] {Theme.Tree.BROWN_MUSHROOM, Theme.Tree.RED_MUSHROOM, Theme.Tree.AZALEA}, new int[] {3, 2, 2}, 0.012, 9, 3,
                (x, z) -> w.ground(x, z) < FLOOR + 6 && !underCap(x, z, 2));
        plants(P.of("red_mushroom", 3, "brown_mushroom", 3, "short_grass", 4, "fern", 3, "moss_carpet", 2, "bush", 1, "firefly_bush", 1), 0.25,
                (x, z) -> w.ground(x, z) < FLOOR + 6);

        int[] home = {BASE.x, BASE.top, BASE.z};
        spawn = new double[] {home[0] - 6.5, home[1] + 1, 0.5, -90};
        flag = new int[] {home[0] + 8, home[1] + 1, 0};
        pads.add(new int[] {home[0] + 4, home[1] + 1, -8});
        pads.add(new int[] {home[0] + 4, home[1] + 1, 8});
        hills.add(new Hill("North", NORTH.x - 3, NORTH.top + 1, NORTH.z - 3, NORTH.x + 3, NORTH.top + 4, NORTH.z + 3));
        hills.add(new Hill("Great Cap", GREAT.x - 3, GREAT.top + 1, GREAT.z - 3, GREAT.x + 3, GREAT.top + 4, GREAT.z + 3));
        hills.add(new Hill("South", SOUTH.x - 3, SOUTH.top + 1, SOUTH.z - 3, SOUTH.x + 3, SOUTH.top + 4, SOUTH.z + 3));
    }

    boolean underCap(int x, int z, int margin) {
        for (Cap c : new Cap[] {BASE, A, B, C, GREAT, NORTH, SOUTH}) {
            for (int k2 = 0; k2 < 2; k2++) {
                int cx = k2 == 0 ? c.x : -c.x, cz = k2 == 0 ? c.z : -c.z;
                if (Math.hypot(x - cx, z - cz) < c.r + margin) return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- mushrooms

    void put(boolean middle, int x, int y, int z, String spec) {
        if (middle) c(x, y, z, spec);
        else w.set(x, y, z, spec);
    }

    /**
     * A giant mushroom: a flared stem from the swamp, a cap with a flat top (moss and mycelium to walk on), the
     * rim drooping, white spots on red caps, pale gills underneath.
     */
    void cap(Cap cap, boolean middle) {
        int R = cap.r;
        // the stem
        for (int y = FLOOR - 4; y <= cap.top - 2; y++) {
            double flare = y < FLOOR + 4 ? (FLOOR + 4 - y) * 0.45 : 0;
            double rr = cap.stem + flare + (y > cap.top - 6 ? (y - (cap.top - 6)) * 0.35 : 0);
            for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) {
                double d = Math.hypot(dx, dz) + w.n3(cap.x + dx, y, cap.z + dz, 3) * 0.4;
                if (d > rr) continue;
                put(middle, cap.x + dx, y, cap.z + dz, d > rr - 1.2 ? "mushroom_stem" : "mushroom_stem");
            }
        }
        // the cap: a top that's flat in the middle, falling away to a rim that curls under
        for (int dx = -R - 1; dx <= R + 1; dx++)
            for (int dz = -R - 1; dz <= R + 1; dz++) {
                double d = Math.hypot(dx, dz) + w.n(cap.x + dx, cap.z + dz, 3) * 0.5;
                if (d > R + 0.4) continue;
                int x = cap.x + dx, z = cap.z + dz;
                double t2 = Math.max(0, (d - cap.flat) / Math.max(1, R - cap.flat));
                int top = cap.top - (int) Math.round(t2 * t2 * 4);
                int under = d > R - 1.5 ? top - 2 : cap.top - 3;
                for (int y = under; y <= top; y++) {
                    String spec;
                    if (y == top && d <= cap.flat + 0.5) {
                        double n = w.fbm(x + 50, z, 5, 2);
                        spec = n > 0.05 ? "moss_block" : n < -0.3 ? "podzol" : "mycelium";
                    } else if (y == under && d < R - 1.5) {
                        spec = "mushroom_stem"; // the gills, pale underneath
                    } else if (cap.red) {
                        spec = w.n(x * 1.7, z * 1.7, 1.6) > 0.62 ? "mushroom_stem" : "red_mushroom_block";
                    } else {
                        spec = "brown_mushroom_block";
                    }
                    put(middle, x, y, z, spec);
                }
                // the inside of the cap between top and gills is solid mushroom flesh
                for (int y = under + 1; y < top; y++) put(middle, x, y, z, cap.red ? "red_mushroom_block" : "brown_mushroom_block");
                for (int y = top + 1; y <= top + 6; y++) if (!w.air(x, y, z)) put(middle, x, y, z, "air");
            }
        keepClear(cap.x - R, cap.z - R, cap.x + R, cap.z + R);
    }

    /**
     * Shelf fungus climbing round a stem from the swamp to the cap: wedge-shaped steps one block apart, two
     * wide, and a hole through the cap where it arrives.
     */
    void spiral(Cap cap, double a0) {
        double r0 = cap.stem + 0.6, r1 = cap.stem + 2.6;
        double mid = (r0 + r1) / 2;
        double sector = 1.25 / mid;
        int y = (int) Math.round(floor(cap.x + (int) Math.round(Math.cos(a0) * mid), cap.z + (int) Math.round(Math.sin(a0) * mid)));
        if (y < FLOOR - 2) y = FLOOR;
        double a = a0;
        int last = cap.top;
        while (y < last) {
            for (int dx = -(int) r1 - 1; dx <= r1 + 1; dx++)
                for (int dz = -(int) r1 - 1; dz <= r1 + 1; dz++) {
                    double d = Math.hypot(dx, dz);
                    if (d < r0 || d > r1) continue;
                    double ang = Math.atan2(dz, dx);
                    double diff = Math.atan2(Math.sin(ang - a), Math.cos(ang - a));
                    if (diff < 0 || diff >= sector) continue;
                    int x = cap.x + dx, z = cap.z + dz;
                    boolean middle = cap.x == 0;
                    put(middle, x, y, z, "brown_mushroom_block");
                    for (int h = 1; h <= 3; h++) if (!w.air(x, y + h, z)) put(middle, x, y + h, z, "air");
                }
            a += sector;
            y++;
        }
        keepClear(cap.x - (int) r1, cap.z - (int) r1, cap.x + (int) r1, cap.z + (int) r1);
    }

    // ---------------------------------------------------------------- home

    /** The village on the home cap: the spawn house, two small houses, mushroom lamps, the flag's ring. */
    void village() {
        int y = BASE.top;
        int hx = BASE.x - 7, hz = BASE.z;
        mushroomHouse(hx, y, hz, 4, "east", true);
        // the flag's ring of moss with stone edging
        int fx = BASE.x + 8, fz = BASE.z;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            double d = Math.hypot(dx, dz);
            if (d > 2.5) continue;
            w.set(fx + dx, y, fz + dz, d > 1.5 ? "mossy_stone_bricks" : "moss_block");
        }
        // lamps: shroomlights on short stems
        for (int[] p : new int[][] {{-1, -6}, {-1, 6}, {10, -4}, {10, 4}}) {
            int x = BASE.x + p[0], z = BASE.z + p[1];
            w.set(x, y + 1, z, "mushroom_stem");
            w.set(x, y + 2, z, "mushroom_stem");
            w.set(x, y + 3, z, "shroomlight");
            w.set(x, y + 4, z, "red_mushroom_block");
        }
        // little mushrooms in the moss
        for (int i = 0; i < 30; i++) {
            int x = BASE.x - 9 + w.r.nextInt(19), z = BASE.z - 9 + w.r.nextInt(19);
            if (Math.hypot(x - BASE.x, z - BASE.z) > BASE.flat - 0.5) continue;
            if (!w.air(x, y + 1, z)) continue;
            String g = w.type(x, y, z).name();
            if (g.equals("MYCELIUM") || g.equals("PODZOL")) w.set(x, y + 1, z, w.r.nextBoolean() ? "red_mushroom" : "brown_mushroom");
            else if (g.equals("MOSS_BLOCK")) w.set(x, y + 1, z, w.r.nextBoolean() ? "moss_carpet" : "short_grass");
        }
    }

    /**
     * A house in a mushroom: a round stem-white wall with windows, a domed red cap for a roof with spots,
     * a door, and a furnished room. The big one is the spawn.
     */
    void mushroomHouse(int cx, int y, int cz, int r, String door, boolean spawnHouse) {
        int h = spawnHouse ? 5 : 4;
        for (int dx = -r - 1; dx <= r + 1; dx++)
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > r + 0.45) continue;
                boolean shell = d > r - 0.55;
                int x = cx + dx, z = cz + dz;
                w.set(x, y, z, shell ? "mushroom_stem" : (((dx + dz) & 1) == 0 ? "oak_planks" : "stripped_oak_wood"));
                for (int dy = 1; dy <= h; dy++) w.set(x, y + dy, z, shell ? "mushroom_stem" : "air");
            }
        // round windows on the four sides (not the door side)
        for (String f : new String[] {"north", "south", "east", "west"}) {
            if (f.equals(door)) continue;
            int x = cx + Kit.dx(f) * r, z = cz + Kit.dz(f) * r;
            w.set(x, y + 2, z, "glass_pane");
            w.set(x, y + 3, z, "glass_pane");
        }
        // the door, and a little step of stem outside
        int ox = Kit.dx(door), oz = Kit.dz(door);
        int dxp = cx + ox * r, dzp = cz + oz * r;
        String facing = Kit.opp(door);
        w.set(dxp, y + 1, dzp, "oak_door[facing=" + facing + ",half=lower,hinge=left,open=false]");
        w.set(dxp, y + 2, dzp, "oak_door[facing=" + facing + ",half=upper,hinge=left,open=false]");
        if (spawnHouse) {
            // a double opening for the spawn: no door to fumble
            int sx = oz != 0 ? 1 : 0, sz = ox != 0 ? 1 : 0;
            for (int s = -1; s <= 1; s++) for (int dy = 1; dy <= 3; dy++) w.set(dxp + sx * s, y + dy, dzp + sz * s, "air");
        }
        // the cap roof: a dome of red with spots, gills under the eaves
        int capY = y + h + 1;
        double R = r + 2.4;
        for (int dx = -(int) R - 1; dx <= R + 1; dx++)
            for (int dz = -(int) R - 1; dz <= R + 1; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > R) continue;
                int x = cx + dx, z = cz + dz;
                int top = capY + (int) Math.round(Math.sqrt(Math.max(0, R * R - d * d)) * 0.75);
                int bottom = d > r + 0.5 ? top - 1 : capY - 1;
                for (int yy = bottom; yy <= top; yy++) {
                    boolean under = yy == bottom && d > r + 0.5;
                    w.set(x, yy, z, under ? "mushroom_stem" : w.n(x * 2.2, z * 2.2 + yy, 1.4) > 0.6 ? "mushroom_stem" : "red_mushroom_block");
                }
            }
        // inside
        w.hangLight(cx, y + h - 1, cz, "lantern");
        int bx = cx - ox * (r - 1), bz = cz - oz * (r - 1);
        if (spawnHouse) {
            // benches round the wall, a table in the middle, banners in the team colour
            for (int i = 0; i < 360; i += 30) {
                double a2 = Math.toRadians(i);
                int x = cx + (int) Math.round(Math.cos(a2) * (r - 1)), z = cz + (int) Math.round(Math.sin(a2) * (r - 1));
                if (Math.abs(x - dxp) <= 1 && Math.abs(z - dzp) <= 1) continue;
                if (!w.air(x, y + 1, z)) continue;
                String face = Math.abs(x - cx) > Math.abs(z - cz) ? (x > cx ? "east" : "west") : (z > cz ? "south" : "north");
                w.set(x, y + 1, z, "oak_stairs[facing=" + face + ",half=bottom]");
            }
            k.table(cx, y + 1, cz, "oak_fence", "oak_pressure_plate");
            w.set(bx, y + 3, bz, "TEAM_wall_banner[facing=" + door + "]");
        } else {
            k.bed(bx + (oz != 0 ? 1 : 0), y + 1, bz + (ox != 0 ? 1 : 0), Kit.opp(door), "TEAM");
            w.set(bx - (oz != 0 ? 1 : 0), y + 1, bz - (ox != 0 ? 1 : 0), "barrel[facing=up]");
            w.set(cx, y + 1, cz, "brown_mushroom_block");
        }
        keepClear(cx - r - 2, cz - r - 2, cx + r + 2, cz + r + 2);
    }
}
