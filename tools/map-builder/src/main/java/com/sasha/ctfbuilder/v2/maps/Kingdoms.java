package com.sasha.ctfbuilder.v2.maps;

import com.sasha.ctfbuilder.v2.Arena;
import com.sasha.ctfbuilder.v2.Kit;
import com.sasha.ctfbuilder.v2.Land;
import com.sasha.ctfbuilder.v2.Theme;
import com.sasha.ctfbuilder.v2.W.P;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * Kingdoms: four castles on floating crags round a floating market town, for four teams. Nothing joins the
 * islands: players build every crossing, and the islands are all different lengths, shapes and heights.
 *
 * Each castle stands high on its crag: a curtain wall with a wall-walk and round towers, a gatehouse, and a keep
 * at the back (the great hall is the spawn), the flag in the bailey. Below the crag to either side lie its
 * terraces, reached by the postern stairs: an orchard with bee hives on one, a farmstead on the other. From the
 * gate the old causeway road runs down toward the town and stops short of it over the void.
 *
 * Off each castle's flanks float its own lands: Mill Hill, high, with the windmill turning toward the town; and a
 * long low strip of fields with ditches, hedgerows and a scarecrow.
 *
 * In the middle the town sits low on its disc in a moat: four gates over stone bridges, four posterns over
 * causeways on the diagonals, timber houses and tower houses round a paved market square (a hill). On each
 * diagonal between two kingdoms a little waystone islet hangs off the town's postern; beyond it a ruined tower
 * stands on a tall rock pinnacle, the highest ground (a hill), and beyond that a long border wood with a spring.
 */
public final class Kingdoms extends Arena {

    static final int TOWN = 64, WATER = 61, CASTLE = 77, TERRACE = 68, MILL = 74, FIELDS = 62, TOWER = 82, WOOD = 66, WAY = 70;
    static final int CX = -82, CW = 18, BT = 48;

    Land land;

    public Kingdoms(World world) {
        super(world, "kingdoms", true, Theme.get("kingdoms"));
        name = "Kingdoms";
        icon = "GOLDEN_HELMET";
        minutes = 18;
        kothPoints = 300;
        reach = 124;
        low = 15;
        high = 150;
        voidY = 15;
        spectator = new double[] {0.5, 120, 0.5, -90, 50};
    }

    // ================================================================ shape of the land

    /** Noise that's the same under every quarter turn and mirror, so the four parts meet without seams. */
    double dn(int x, int z, double k, double o, double scale, int oct) {
        int a = Math.max(Math.abs(x), Math.abs(z)), b = Math.min(Math.abs(x), Math.abs(z));
        return w.fbm(a * k + o, b * k - o, scale, oct);
    }

    static double r(int x, int z) {
        return Math.hypot(x, z);
    }

    /** Distance to the nearest border tower's middle. */
    static double tower(int x, int z) {
        return Math.hypot(Math.abs(x) - BT, Math.abs(z) - BT);
    }

    /** Distance from the nearest diagonal, and how far along it. */
    static double diagOff(int x, int z) {
        return Math.abs(Math.abs(x) - Math.abs(z)) / Math.sqrt(2);
    }

    static double diagAlong(int x, int z) {
        return (Math.abs(x) + Math.abs(z)) / Math.sqrt(2);
    }

    static double spring(int x, int z) {
        return Math.hypot(Math.abs(x) - 66, Math.abs(z) - 66);
    }

    boolean moat(int x, int z) {
        double r = r(x, z);
        return r > 31.5 && r < 35.5;
    }

    /** Turns (x, z) by quarter turns into the west kingdom's wedge (x <= 0, |z| <= -x). */
    static int[] wedge(int x, int z) {
        for (int k = 0; k < 4; k++) {
            if (x <= 0 && Math.abs(z) <= -x) return new int[] {x, z};
            int t = x;
            x = z;
            z = -t;
        }
        return new int[] {x, z};
    }

    /** How far (x, z) is outside a rectangle with rounded corners of radius rad (negative inside). */
    static double rr(int x, int z, int x0, int x1, int z0, int z1, double rad) {
        double tx = Math.max(0, Math.max(x0 + rad - x, x - (x1 - rad))), tz = Math.max(0, Math.max(z0 + rad - z, z - (z1 - rad)));
        return Math.hypot(tx, tz) - rad;
    }

    /** A ragged edge, the same on every copy. */
    double rag(int x, int z) {
        return dn(x, z, 1.1, 21, 4, 2) * 1.1;
    }

    /** The brook from the spring down the border wood toward the tower, ending in a little pool. */
    boolean brook(int x, int z) {
        double s = diagAlong(x, z);
        return diagOff(x, z) <= 1.3 && s > 84.5 && s < 90 || Math.hypot(s - 84.5, diagOff(x, z)) < 2.2;
    }

    boolean pond(int x, int z) {
        return spring(x, z) < 4.6;
    }

    boolean water(int x, int z) {
        return moat(x, z) || wood(x, z) && (brook(x, z) || pond(x, z));
    }

    // the islands on the diagonals, shared by two kingdoms
    boolean pinnacle(int x, int z) {
        return tower(x, z) <= 7.5 + rag(x, z) * 0.4;
    }

    boolean wood(int x, int z) {
        double s = diagAlong(x, z), sc = Math.max(90, Math.min(106, s));
        return Math.hypot(s - sc, diagOff(x, z)) <= 8 + rag(x, z);
    }

    boolean waystone(int x, int z) {
        return Math.hypot(diagAlong(x, z) - 47, diagOff(x, z)) <= 3.2 + rag(x, z) * 0.3;
    }

    // each kingdom's own islands, in the west kingdom's coordinates
    boolean terraceN(int x, int z) {
        return rr(x, z, -103, -68, -43, -20, 4) <= rag(x, z);
    }

    boolean terraceS(int x, int z) {
        return rr(x, z, -103, -63, 20, 42, 4) <= rag(x, z);
    }

    boolean road(int x, int z) {
        return x >= -58 && x <= -44 && Math.abs(z) <= 4.5;
    }

    boolean millHill(int x, int z) {
        return rr(x, z, -53, -42, -33, -18, 5) <= rag(x, z) * 0.8;
    }

    boolean fieldStrip(int x, int z) {
        return rr(x, z, -52, -42, 11, 31, 3) <= rag(x, z) * 0.5;
    }

    /** The castle's crag: its rounded-square top (with the forecourt before the gate), falling steeply to its terraces. */
    double motte(int x, int z) {
        double dx = Math.abs(x - CX), dz = Math.abs(z);
        double d = Math.hypot(Math.max(0, dx - 17), Math.max(0, dz - 17)) - 4;
        if (x >= CX + 19 && x <= -58 && dz <= 9) d = Math.min(d, Math.max(0, dz - 9) + Math.max(0, x + 58));
        // the rock carries on under the corner towers, which stand out past the wall
        for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2)
            if (Math.hypot(x - (CX + sx * CW), z - sz * CW) <= 5.8) d = Math.min(d, 1.5);
        if (d > 2.6 + rag(x, z) * 0.5) return Double.NaN;
        return d <= 0 ? CASTLE : Math.max(TERRACE, CASTLE - d * 3.4 + dn(x, z, 0.9, 40, 5, 2) * 1.6);
    }

    /** The west kingdom's own ground at (x, z) (its castle island, Mill Hill, the fields), NaN over the void. */
    double kingdom(int x, int z) {
        double h = motte(x, z);
        if (terraceN(x, z) || terraceS(x, z)) h = Double.isNaN(h) ? TERRACE : Math.max(h, TERRACE);
        if (road(x, z)) {
            double r = 77.5 - 0.5 * (x + 57);
            double e = r - 1 - 1.6 * Math.max(0, Math.abs(z) - 3);
            h = Double.isNaN(h) ? e : Math.max(h, e);
        }
        if (!Double.isNaN(h)) return h;
        if (millHill(x, z)) return MILL;
        if (fieldStrip(x, z)) return FIELDS;
        return Double.NaN;
    }

    double height(int x, int z) {
        double r = r(x, z);
        if (r <= 37.3 + rag(x, z) * 0.3) {
            if (moat(x, z)) return WATER - 3;
            return r < 31.5 ? TOWN : TOWN - 2;
        }
        if (pinnacle(x, z)) return TOWER;
        if (waystone(x, z)) return WAY;
        if (wood(x, z)) return pond(x, z) ? WOOD - 3 : brook(x, z) ? WOOD - 2 : WOOD;
        int[] c = wedge(x, z);
        return kingdom(c[0], c[1]);
    }

    String surface(int x, int z, int y, int steep, double edge) {
        double n = dn(x, z, 1.7, 9, 3, 1);
        if (water(x, z) && y < (moat(x, z) ? WATER : WOOD - 1)) return moat(x, z) ? (n > 0.2 ? "gravel" : "mud") : n > 0.3 ? "gravel" : n < -0.3 ? "clay" : "sand";
        if (r(x, z) < 31.5) return "dirt";
        if (steep >= 2) return rock(x, y, z);
        return "grass_block";
    }

    static final String[] LAYERS = {"stone", "andesite", "stone", "tuff", "stone", "cobblestone", "stone", "andesite", "cobbled_deepslate", "stone", "tuff", "deepslate"};
    static final int[] THICK = {3, 2, 2, 1, 3, 1, 2, 2, 1, 2, 2, 2};
    static final int CYCLE = 22;

    String rock(int x, int y, int z) {
        double warp = dn(x, z, 0.5, 11, 28, 2) * 2.2;
        int at = Math.floorMod((int) Math.floor(y + warp), CYCLE), i = 0;
        while (at >= THICK[i]) at -= THICK[i++];
        double tex = w.n3(x * 1.9, y * 1.9, z * 1.9, 4);
        if (tex > 0.62) i = (i + 1) % LAYERS.length;
        else if (tex < -0.64) i = (i + LAYERS.length - 1) % LAYERS.length;
        if (y > 60 && w.n3(x * 1.3 + 5, y * 1.3, z * 1.3, 3) > 0.7) return "mossy_cobblestone";
        return LAYERS[i];
    }

    /**
     * The underside, tapering from the rim like an upturned hill: the town deep and broad, the tower's rock a tall
     * narrow pinnacle, the castle crag a great wedge of rock under its terraces, the small islands shallow.
     */
    int bottom(int x, int z, int top, double edge) {
        double size, depth, base = top;
        if (r(x, z) <= 38) {
            size = 37;
            depth = 30;
            base = TOWN - 2;
        } else if (tower(x, z) <= 9) {
            size = 7.5;
            depth = 30;
        } else if (diagOff(x, z) < 10 && diagAlong(x, z) > 78) {
            size = 8;
            depth = 17;
            base = WOOD - 2;
        } else if (diagOff(x, z) < 5 && diagAlong(x, z) < 56) {
            size = 3.2;
            depth = 9;
        } else {
            int[] c = wedge(x, z);
            if (millHill(c[0], c[1])) {
                size = 7;
                depth = 17;
            } else if (fieldStrip(c[0], c[1])) {
                size = 5;
                depth = 12;
            } else {
                size = 22;
                depth = 36;
                base = Math.min(top, TERRACE);
            }
        }
        double e = Math.min(1, edge / (size * 0.9)), lens = 1 - Math.pow(1 - e, 1.8);
        double bulge = Math.max(0, dn(x, z, 0.35, 17, 22, 2)) * size * 0.25 * Math.min(1, edge / 8.0);
        return (int) Math.round(base - 2 - depth * lens - bulge + dn(x, z, 0.9, 5, 9, 2) * 2.0 * Math.min(1, edge / 6.0));
    }

    // ================================================================ build

    @Override
    protected void build() {
        land = new Land(w, t, -124, 124, -124, 124);
        land.top = this::height;
        land.surface = this::surface;
        land.rock = this::rock;
        land.bottom = this::bottom;
        land.water = (x, z) -> moat(x, z) && r(x, z) <= 37 ? WATER : wood(x, z) && (brook(x, z) || pond(x, z)) ? WOOD - 1 : Double.NaN;
        land.soil = P.of("dirt");
        land.build();

        roots();
        banks();
        castle();
        town();
        borderLand();
        windmill(-47, -26);
        farmstead(-94, 34);
        orchard(-100, -40);
        fields(-51, -43, 12, 30);
        terracePaths();

        trees(new Theme.Tree[] {Theme.Tree.OAK, Theme.Tree.BIRCH, Theme.Tree.SPRUCE}, new int[] {5, 3, 2}, 0.3, 4, 1, this::woods);
        trees(new Theme.Tree[] {Theme.Tree.OAK, Theme.Tree.BIRCH}, new int[] {3, 1}, 0.004, 10, 3, (x, z) -> meadow(x, z) && !woods(x, z));
        meadows();
        for (int x = -124; x <= 0; x++) for (int z = -124; z <= 124; z++) {
            if (!w.canon(x, z)) continue;
            for (int y = 60; y <= 90; y++) if (w.type(x, y, z) == Material.DIRT_PATH && !w.air(x, y + 1, z)) {
                Material a = w.type(x, y + 1, z);
                if (a != Material.SHORT_GRASS && !a.name().endsWith("_CARPET")) w.set(x, y, z, "coarse_dirt");
            }
        }

        spawn = new double[] {CX - 10.5, CASTLE + 1, 0.5, -90};
        flag = new int[] {CX + 7, CASTLE + 1, 0};
        pads.add(new int[] {CX + 9, CASTLE + 1, -9});
        pads.add(new int[] {CX + 9, CASTLE + 1, 9});
        hills.add(new Hill("Market Square", -4, TOWN + 1, -4, 4, TOWN + 4, 4));
        int ty = TOWER + 1;
        hills.add(new Hill("North-West Tower", -BT - 2, ty, -BT - 2, -BT + 2, ty + 3, -BT + 2));
        hills.add(new Hill("North-East Tower", BT - 2, ty, -BT - 2, BT + 2, ty + 3, -BT + 2));
        hills.add(new Hill("South-East Tower", BT - 2, ty, BT - 2, BT + 2, ty + 3, BT + 2));
        hills.add(new Hill("South-West Tower", -BT - 2, ty, BT - 2, -BT + 2, ty + 3, BT + 2));
    }

    /** Where the woods grow: the border wood away from its spring, brook and paths; a few trees on the castle terraces. */
    boolean woods(int x, int z) {
        if (isClear(x, z, 1) || water(x, z) || !wood(x, z) || spring(x, z) < 6.4) return false;
        return diagOff(x, z) > 2.2 && land.topAt(x, z) == WOOD;
    }

    boolean meadow(int x, int z) {
        if (isClear(x, z, 1) || water(x, z)) return false;
        int[] c = wedge(x, z);
        return (terraceN(c[0], c[1]) || terraceS(c[0], c[1])) && land.topAt(x, z) == TERRACE && rr(c[0], c[1], -103, -63, -43, 42, 0) < -2.5;
    }

    /** Hanging roots and moss under each island's lowest blocks. */
    void roots() {
        for (int x = -124; x <= 0; x++) for (int z = -124; z <= 124; z++) {
            if (!w.canon(x, z) || Double.isNaN(height(x, z)) || w.r.nextInt(6) != 0) continue;
            int y = low;
            while (y < 90 && w.air(x, y, z)) y++;
            if (y >= 60 || !w.air(x, y - 1, z)) continue;
            w.set(x, y - 1, z, w.r.nextInt(4) == 0 ? "glow_lichen[up=true]" : "hanging_roots");
        }
    }

    // ================================================================ water's edges and the ring road

    /** The town's revetment down into the moat, gravel at the foot of the outer bank. */
    void banks() {
        for (int x = -40; x <= 0; x++) for (int z = -40; z <= 40; z++) {
            if (!w.canon(x, z)) continue;
            double r = r(x, z);
            if (r >= 30.2 && r <= 31.5) {
                for (int yy = WATER - 3; yy <= TOWN; yy++) w.set(x, yy, z, yy <= WATER - 1 ? "mossy_stone_bricks" : stoneAt(x, yy, z, yy - WATER));
            }
            if (moat(x, z) && w.r.nextInt(9) == 0 && (r < 32.6 || r > 34.4)) w.set(x, WATER + 1, z, "lily_pad");
            if (moat(x, z) && w.r.nextInt(4) == 0) w.set(x, WATER - 2, z, w.r.nextBoolean() ? "seagrass" : "tall_seagrass[half=lower]");
        }
        // lily pads on the spring's pool
        for (int x = -80; x <= 0; x++) for (int z = -80; z <= 80; z++) {
            if (!w.canon(x, z)) continue;
            if (wood(x, z) && pond(x, z) && w.r.nextInt(8) == 0 && w.air(x, WOOD, z)) m(x, WOOD, z, "lily_pad");
        }
    }

    /** Castle stone: mossy low down, a few cracked and weathered bricks, cleaner higher up. */
    String stoneAt(int x, int y, int z, int dy) {
        double n = w.n3(x * 0.8, y * 0.8, z * 0.8, 2), moss = Math.max(0, 0.42 - dy * 0.045);
        if (n > 0.55 - moss) return "mossy_stone_bricks";
        if (n < -0.6) return "cracked_stone_bricks";
        if (w.n3(x * 2.1 + 9, y * 2.1, z * 2.1, 2) > 0.72) return "tuff_bricks";
        return "stone_bricks";
    }

    P base() {
        return P.of("cobblestone", 3, "mossy_cobblestone", 2, "stone", 1, "andesite", 1);
    }

    // ================================================================ the castle

    void castle() {
        int y = CASTLE;
        bailey();
        curtain();
        for (int[] c : new int[][] {{CX - CW, -CW}, {CX - CW, CW}, {CX + CW, -CW}, {CX + CW, CW}}) roundTower(c[0], c[1], 4, 14, true);
        gatehouse();
        keep();
        stables(-15, "south");
        smithy(15, "north");
        posterns();
        causeway();
        keepClear(CX - 24, -26, -57, 26);
        keepClear(-58, -6, -36, 6);
    }

    /** The bailey's floor: a paved way from the gate to the keep door, the flag's circle, beaten earth round it. */
    void bailey() {
        int y = CASTLE;
        for (int x = CX - 15; x <= CX + 15; x++) for (int z = -15; z <= 15; z++) {
            double n = w.n2(x * 1.7, z * 1.7, 2), n2 = w.n2(x * 0.9 + 4, z * 0.9, 2);
            boolean trodden = Math.abs(z) <= 5 + 2 * n2 || Math.abs(x - (CX + 3)) <= 1 + n2;
            String p = trodden ? (n > 0.3 ? "coarse_dirt" : n < -0.35 ? "gravel" : "dirt_path") : n > 0.45 ? "coarse_dirt" : "grass_block";
            boolean way = Math.abs(z) <= 2 && x >= CX - 2;
            if (way) p = Math.abs(z) == 2 ? "polished_andesite" : ((x + z) & 1) == 0 ? "stone_bricks" : (n > 0.3 ? "mossy_stone_bricks" : "andesite");
            double f = Math.hypot(x - (CX + 7), z);
            if (f <= 3.5) p = f < 1 ? "chiseled_stone_bricks" : f < 2.5 ? "polished_andesite" : "stone_bricks";
            w.set(x, y, z, p);
        }
        // the well, the archery butts, a cart of hay, barrels by the keep
        int wx = CX + 3, wz = 8;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            boolean mid = dx == 0 && dz == 0;
            for (int yy = y - 4; yy <= y; yy++) w.set(wx + dx, yy, wz + dz, mid && yy > y - 4 ? "water" : "cobblestone");
            if (!mid) w.set(wx + dx, y + 1, wz + dz, dx == 0 || dz == 0 ? "stone_brick_wall" : "mossy_stone_bricks");
        }
        for (int s : new int[] {-1, 1}) for (int dy = 2; dy <= 3; dy++) w.set(wx + s, y + dy, wz, "spruce_fence");
        for (int dx = -1; dx <= 1; dx++) w.set(wx + dx, y + 4, wz, "stripped_spruce_log[axis=x]");
        w.set(wx, y + 3, wz, "iron_chain[axis=y]");
        w.set(wx, y + 2, wz, "lantern[hanging=true]");
        for (int dz = -7; dz <= -4; dz += 3) {
            w.set(CX + 3, y + 1, dz, "hay_block");
            w.set(CX + 3, y + 2, dz, "target");
        }
        for (int[] b : new int[][] {{CX - 1, -10}, {CX - 1, -11}, {CX - 1, 11}, {CX, 11}}) w.set(b[0], y + 1, b[1], "barrel[facing=up]");
    }

    /**
     * The curtain wall: three thick, a wall-walk along the top behind a crenellated parapet carried on a corbel
     * table; a battered base course, buttresses and arrow slits outside; stairs up from the bailey by the gate.
     */
    void curtain() {
        int y = CASTLE;
        for (int x = CX - CW; x <= CX + CW; x++) for (int z = -CW; z <= CW; z++) {
            int dx = x - CX, m = Math.max(Math.abs(dx), Math.abs(z));
            if (m < CW - 2) continue;
            boolean outer = m == CW;
            k.footing(x, z, x, z, y, base());
            for (int dy = 1; dy <= 8; dy++) w.set(x, y + dy, z, dy <= 2 ? base().pick(w.r) : dy == 6 && outer ? "polished_andesite" : stoneAt(x, y + dy, z, dy));
            if (outer) {
                w.set(x, y + 9, z, stoneAt(x, y + 9, z, 9));
                if (((x + z) & 1) == 0) w.set(x, y + 10, z, "stone_brick_wall");
                // corbels under the parapet's lip
                int ox = Math.abs(dx) == CW ? Integer.signum(dx) : 0, oz = Math.abs(z) == CW ? Integer.signum(z) : 0;
                if (ox != 0 && oz != 0) continue;
                String in = ox > 0 ? "west" : ox < 0 ? "east" : oz > 0 ? "north" : "south";
                w.set(x + ox, y + 8, z + oz, "stone_brick_stairs[facing=" + in + ",half=top]");
                w.set(x + ox, y + 1, z + oz, "cobblestone_stairs[facing=" + in + ",half=bottom]");
                int along = ox != 0 ? z : dx;
                if (Math.floorMod(along, 7) == 3 && Math.abs(along) < CW - 5) {
                    // a buttress
                    for (int dy = 1; dy <= 5; dy++) w.set(x + ox, y + dy, z + oz, dy <= 2 ? "cobblestone" : stoneAt(x, y + dy, z, dy));
                    w.set(x + ox, y + 6, z + oz, "stone_brick_stairs[facing=" + in + ",half=bottom]");
                } else if (Math.floorMod(along, 7) == 0 && Math.abs(along) < CW - 4) {
                    // an arrow slit
                    w.set(x, y + 4, z, "air");
                    w.set(x, y + 5, z, "air");
                }
            }
        }
        // stairs up to the wall-walk, either side of the gate inside
        for (int s : new int[] {-1, 1}) for (int i = 0; i < 8; i++) {
            int x = CX + CW - 3, z = s * (4 + i), sy = y + 1 + i;
            for (int yy = y + 1; yy < sy; yy++) w.set(x, yy, z, stoneAt(x, yy, z, yy - y));
            w.set(x, sy, z, "stone_brick_stairs[facing=" + (s > 0 ? "south" : "north") + ",half=bottom]");
            for (int yy = sy + 1; yy <= sy + 3; yy++) w.set(x, yy, z, "air");
        }
        // banners hung along the inside of the wall
        for (int s : new int[] {-1, 1}) for (int x = CX - 6; x <= CX + 10; x += 8) w.set(x, y + 7, s * (CW - 3), "TEAM_wall_banner[facing=" + (s < 0 ? "south" : "north") + "]");
    }

    /**
     * A round tower: battered base, string course, arrow slits, a floor at the wall-walk's height with doorways
     * onto the walk, and a tall conical slate roof with a finial and the team's banner.
     */
    void roundTower(int tcx, int tcz, double r, int h, boolean onWall) {
        int y = CASTLE;
        int R = (int) Math.ceil(r + 1);
        for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) {
            double d = Math.hypot(dx, dz);
            if (d > r + 0.45) continue;
            int x = tcx + dx, z = tcz + dz;
            boolean shell = d > r - 0.8;
            k.footing(x, z, x, z, y, base());
            for (int dy = 1; dy <= h; dy++) {
                String s;
                if (shell) s = dy <= 2 ? base().pick(w.r) : dy == 7 ? "polished_andesite" : stoneAt(x, y + dy, z, dy);
                else s = dy < 8 ? stoneAt(x, y + dy, z, dy) : dy == 8 ? "spruce_planks" : "air";
                w.set(x, y + dy, z, s);
            }
            if (shell) w.set(x, y + h + 1, z, "stone_brick_slab[type=bottom]");
        }
        // slits all round, high up
        for (int a = 0; a < 8; a++) {
            double ang = a * Math.PI / 4 + Math.PI / 8;
            int x = tcx + (int) Math.round(Math.cos(ang) * r), z = tcz + (int) Math.round(Math.sin(ang) * r);
            for (int dy = 10; dy <= 11; dy++) w.set(x, y + dy, z, "air");
        }
        // doorways onto the wall-walk where it meets the tower
        if (onWall) for (int[] dir : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            for (int kk = 1; kk <= R; kk++) for (int o = -2; o <= 2; o++) {
                int x = tcx + dir[0] * kk + (dir[0] == 0 ? o : 0), z = tcz + dir[1] * kk + (dir[1] == 0 ? o : 0);
                int mm = Math.max(Math.abs(x - CX), Math.abs(z));
                if (mm != CW - 1 && mm != CW - 2) continue;
                double d = Math.hypot(x - tcx, z - tcz);
                if (d > r - 0.8 && d <= r + 0.45) for (int dy = 9; dy <= 10; dy++) w.set(x, y + dy, z, "air");
            }
        }
        int top = k.cone(tcx, tcz, y + h + 2, (int) Math.round(r), 1, 1.7, Kit.Roof.of("deepslate_tiles"));
        w.set(tcx, top + 1, tcz, "stripped_dark_oak_log[axis=y]");
        w.set(tcx, top + 2, tcz, "stripped_dark_oak_log[axis=y]");
        w.set(tcx, top + 3, tcz, "dark_oak_fence");
        w.set(tcx + 1, top + 2, tcz, "TEAM_wall_banner[facing=east]");
        w.hangLight(tcx, y + 12, tcz, "lantern");
    }

    /**
     * The gatehouse: a pointed arch between two drum towers, the portcullis raised in its slot, a corbelled
     * parapet over the gate with machicolations, the team's banners, the wall-walk carried across the top.
     */
    void gatehouse() {
        int y = CASTLE, gx = CX + CW;
        for (int x = gx - 2; x <= gx + 1; x++) for (int z = -3; z <= 3; z++) {
            for (int dy = 1; dy <= 8; dy++) w.set(x, y + dy, z, dy <= 2 ? base().pick(w.r) : stoneAt(x, y + dy, z, dy));
            if (x == gx + 1) {
                w.set(x, y + 9, z, stoneAt(x, y + 9, z, 9));
                if ((z & 1) == 0) w.set(x, y + 10, z, "stone_brick_wall");
                w.set(x + 1, y + 8, z, "stone_brick_stairs[facing=west,half=top]");
            }
        }
        for (int s : new int[] {-1, 1}) roundTower(gx + 1, s * 5, 3.5, 13, true);
        // the passage, with a pointed arch at each end
        for (int x = gx - 3; x <= gx + 2; x++) for (int z = -1; z <= 1; z++) for (int dy = 1; dy <= 5; dy++) {
            if (dy == 5 && z != 0) continue;
            w.set(x, y + dy, z, "air");
        }
        for (int x : new int[] {gx - 2, gx + 1}) {
            w.set(x, y + 5, -1, "stone_brick_stairs[facing=north,half=top]");
            w.set(x, y + 5, 1, "stone_brick_stairs[facing=south,half=top]");
            w.set(x, y + 6, 0, "chiseled_stone_bricks");
        }
        for (int x = gx - 3; x <= gx + 2; x++) for (int z = -1; z <= 1; z++) w.set(x, y, z, ((x + z) & 1) == 0 ? "stone_bricks" : "cobblestone");
        // the portcullis, raised; murder holes above the passage
        for (int z = -1; z <= 1; z++) {
            w.set(gx, y + 4, z, "iron_bars");
            w.set(gx, y + 5, z, z == 0 ? "iron_bars" : "stone_bricks");
        }
        w.set(gx - 1, y + 6, 0, "iron_bars");
        // banners over the arch and on the drums
        w.set(gx + 2, y + 7, 0, "TEAM_wall_banner[facing=east]");
        for (int s : new int[] {-1, 1}) w.set(gx + 2, y + 6, s * 2, "TEAM_wall_banner[facing=east]");
        w.hangLight(gx - 1, y + 4, 0, "lantern");
        // lamps either side of the gate outside
        for (int s : new int[] {-1, 1}) {
            w.set(gx + 4, y + 1, s * 9, "stone_bricks");
            w.set(gx + 4, y + 2, s * 9, "stone_brick_wall");
            w.set(gx + 4, y + 3, s * 9, "lantern");
        }
    }

    /**
     * The keep: a tall stone tower against the back wall. Inside, the great hall (the spawn) with two rows of
     * pillars, the long tables, a hooded fireplace and the throne on its dais under the banners; stairs up the
     * walls to the lord's chamber, the barracks, and the battlemented roof with a turret at each corner.
     */
    void keep() {
        int y = CASTLE, x0 = CX - 15, x1 = CX - 2, z0 = -8, z1 = 8;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
            boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
            boolean pilaster = edge && !corner && ((x == x0 || x == x1) ? Math.floorMod(z, 4) == 0 : Math.floorMod(x - x0, 4) == 0);
            for (int dy = 1; dy <= 18; dy++) {
                if (edge) w.set(x, y + dy, z, dy <= 2 ? base().pick(w.r) : corner || pilaster ? (dy % 6 == 0 ? "chiseled_stone_bricks" : "stone_bricks")
                        : dy == 7 || dy == 12 ? "polished_andesite" : stoneAt(x, y + dy, z, dy));
                else w.set(x, y + dy, z, dy == 7 || dy == 12 ? "spruce_planks" : dy == 17 ? "stone_bricks" : "air");
            }
            w.set(x, y, z, edge ? "cobblestone" : Math.floorMod(x + z, 6) == 0 ? "stripped_dark_oak_wood" : "dark_oak_planks");
            if (edge) {
                w.set(x, y + 18, z, stoneAt(x, y + 18, z, 18));
                if (((x + z) & 1) == 0 && !corner) w.set(x, y + 19, z, "stone_brick_wall");
            }
        }
        // beams under the upper floors
        for (int x = x0 + 2; x <= x1 - 2; x += 3) for (int z = z0 + 1; z <= z1 - 1; z++) {
            w.set(x, y + 6, z, "stripped_dark_oak_log[axis=z]");
            w.set(x, y + 11, z, "stripped_dark_oak_log[axis=z]");
        }
        // windows: tall pointed ones lighting the hall, smaller above
        for (int x = x0 + 2; x <= x1 - 2; x += 4) for (int z : new int[] {z0, z1}) {
            for (int dy = 2; dy <= 4; dy++) w.set(x, y + dy, z, "glass_pane");
            w.set(x, y + 5, z, "stone_brick_stairs[facing=" + (z == z0 ? "north" : "south") + ",half=top]");
            for (int dy = 9; dy <= 10; dy++) w.set(x, y + dy, z, "glass_pane");
            w.set(x, y + 14, z, "glass_pane");
        }
        // the door: a pointed arch, the banners
        for (int z = -1; z <= 1; z++) for (int dy = 1; dy <= 4; dy++) w.set(x1, y + dy, z, dy == 4 && z != 0 ? (z < 0 ? "stone_brick_stairs[facing=north,half=top]" : "stone_brick_stairs[facing=south,half=top]") : "air");
        w.set(x1, y + 5, 0, "chiseled_stone_bricks");
        for (int s : new int[] {-1, 1}) {
            w.set(x1 + 1, y + 5, s * 3, "TEAM_wall_banner[facing=east]");
            w.set(x1 + 1, y + 3, s * 2, "lantern");
            w.set(x1 + 1, y + 2, s * 2, "stone_brick_wall");
            w.set(x1 + 1, y + 1, s * 2, "stone_bricks");
        }
        // the hall: pillars, tables, carpet, throne, fireplace, banners, chandeliers
        for (int x = x0 + 4; x <= x1 - 3; x += 4) for (int s : new int[] {-4, 4}) for (int dy = 1; dy <= 6; dy++)
            w.set(x, y + dy, s, dy == 1 || dy == 6 ? "chiseled_stone_bricks" : "stone_bricks");
        // the long tables either side of the aisle, benches on their outer sides
        for (int x = x0 + 3; x <= x1 - 2; x++) w.set(x, y + 1, 0, "TEAM_carpet");
        for (int x = x0 + 5; x <= x1 - 3; x++) for (int s : new int[] {-1, 1}) {
            w.set(x, y + 1, s * 2, "dark_oak_slab[type=top]");
            w.set(x, y + 1, s * 3, "spruce_stairs[facing=" + (s < 0 ? "north" : "south") + ",half=bottom]");
            if (Math.floorMod(x, 2) == 0) w.set(x, y + 2, s * 2, Math.floorMod(x, 4) == 0 ? "candle[candles=3,lit=true]" : "decorated_pot");
        }
        for (int z = -7; z <= -6; z++) w.set(x1 - 1, y + 1, z, "barrel[facing=up]");
        w.set(x1 - 2, y + 1, -7, "barrel[facing=up]");
        for (int z = -3; z <= 3; z++) {
            w.set(x0 + 1, y + 1, z, "dark_oak_planks");
            w.set(x0 + 2, y + 1, z, "dark_oak_slab[type=bottom]");
            w.set(x0 + 1, y + 2, z, "TEAM_carpet");
        }
        w.set(x0 + 1, y + 2, 0, "dark_oak_stairs[facing=west,half=bottom]");
        for (int s : new int[] {-1, 1}) {
            w.set(x0 + 1, y + 2, s, "dark_oak_fence");
            w.set(x0 + 1, y + 3, s, "candle[candles=2,lit=true]");
            w.set(x0 + 1, y + 5, s * 2, "TEAM_wall_banner[facing=east]");
        }
        w.set(x0 + 1, y + 5, 0, "TEAM_wall_banner[facing=east]");
        int fx = CX - 9;
        for (int dx = -1; dx <= 1; dx++) {
            w.set(fx + dx, y + 1, z0 + 1, dx == 0 ? "campfire[lit=false]" : "stone_bricks");
            w.set(fx + dx, y + 2, z0 + 1, dx == 0 ? "air" : "stone_bricks");
            w.set(fx + dx, y + 3, z0 + 1, "stone_brick_slab[type=top]");
            w.set(fx + dx, y + 4, z0 + 1, "stone_brick_stairs[facing=north,half=bottom]");
        }
        w.set(fx, y + 5, z0 + 1, "stone_bricks");
        w.set(fx, y + 6, z0 + 1, "stone_bricks");
        for (int x = x0 + 4; x <= x1 - 3; x += 4) {
            w.hangLight(x - 2, y + 5, 0, "lantern");
            for (int s : new int[] {-1, 1}) w.set(x - 2, y + 4, s * (z1 - 1), "TEAM_wall_banner[facing=" + (s < 0 ? "south" : "north") + "]");
        }
        // stairs: hall to the first floor (south wall, rising east), first to second (north wall, rising west),
        // second to the roof (south wall, rising east); each top stair flush with the floor above
        stairFlight(x0 + 2, z1 - 1, y + 1, 7, 1, "east");
        stairFlight(x0 + 8, z0 + 1, y + 8, 5, -1, "west");
        stairFlight(x0 + 4, z1 - 1, y + 13, 5, 1, "east");
        // the lord's chamber and the armoury on the first floor
        int f1 = y + 7;
        k.bed(x0 + 2, f1 + 1, z0 + 2, "west", "TEAM");
        for (int z = z0 + 4; z <= z0 + 6; z++) for (int dy = 1; dy <= 2; dy++) w.set(x0 + 1, f1 + dy, z, "bookshelf");
        w.set(x0 + 3, f1 + 1, z0 + 1, "chest[facing=south]");
        w.set(x0 + 4, f1 + 1, z0 + 1, "lectern[facing=south]");
        for (int z = -5; z <= -1; z++) w.set(x1 - 1, f1 + 1, z, z == -3 ? "anvil[facing=south]" : (z & 1) == 0 ? "barrel[facing=up]" : "grindstone[face=floor,facing=west]");
        for (int x = x0 + 2; x <= x1 - 2; x++) w.set(x, f1 + 1, 0, "TEAM_carpet");
        w.hangLight(x0 + 5, f1 + 4, 0, "lantern");
        w.hangLight(x1 - 4, f1 + 4, 0, "lantern");
        // the barracks above
        int f2 = y + 12;
        for (int x = x0 + 10; x <= x1 - 1; x += 2) {
            k.bed(x, f2 + 1, z0 + 2, "north", "TEAM");
            k.bed(x, f2 + 1, z1 - 2, "south", "TEAM");
        }
        w.set(x0 + 11, f2 + 1, z0 + 1, "chest[facing=south]");
        w.set(x0 + 11, f2 + 1, z1 - 1, "chest[facing=north]");
        w.set(x0 + 1, f2 + 1, -1, "barrel[facing=up]");
        w.set(x0 + 1, f2 + 1, 0, "crafting_table");
        w.set(x0 + 1, f2 + 1, 1, "barrel[facing=up]");
        w.hangLight(x0 + 6, f2 + 4, 0, "lantern");
        // the corner turrets on the roof
        for (int[] c : new int[][] {{x0, z0}, {x0, z1}, {x1, z0}, {x1, z1}}) {
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > 1.9) continue;
                for (int dy = 15; dy <= 21; dy++) w.set(c[0] + dx, y + dy, c[1] + dz, d > 1.1 || dy < 18 ? (dy == 15 ? "stone_brick_stairs[facing=" + (dx < 0 ? "east" : dx > 0 ? "west" : dz < 0 ? "south" : "north") + ",half=top]" : stoneAt(c[0] + dx, y + dy, c[1] + dz, dy)) : "air");
            }
            k.cone(c[0], c[1], y + 22, 2, 1, 1.8, Kit.Roof.of("deepslate_tiles"));
        }
        // the flag over the keep
        for (int dy = 18; dy <= 24; dy++) w.set(x0 + 6, y + dy, 0, dy == 24 ? "dark_oak_fence" : "stripped_dark_oak_log[axis=y]");
        w.set(x0 + 7, y + 23, 0, "TEAM_wall_banner[facing=east]");
        w.set(x0 + 5, y + 23, 0, "TEAM_wall_banner[facing=west]");
    }

    /** A straight flight of n stairs, the first at (x, y, z), each one higher going dir (1 east / -1 west); cleared over. */
    void stairFlight(int x, int z, int y, int n, int dir, String facing) {
        for (int i = 0; i < n; i++) {
            int sx = x + dir * i, sy = y + i;
            for (int zz = z; zz == z; zz++) {
                w.set(sx, sy, zz, "stone_brick_stairs[facing=" + facing + ",half=bottom]");
                for (int yy = sy + 1; yy <= sy + 3; yy++) w.set(sx, yy, zz, "air");
                int nz = z < 0 ? z + 1 : z - 1;
                w.set(sx, sy, nz, "stone_brick_stairs[facing=" + facing + ",half=bottom]");
                for (int yy = sy + 1; yy <= sy + 3; yy++) w.set(sx, yy, nz, "air");
            }
        }
    }

    /** Stables along the north wall: a lean-to on posts, stalls with hay and water troughs. */
    void stables(int zw, String face) {
        int y = CASTLE, x0 = CX + 4, x1 = CX + 13, s = Integer.signum(zw), zf = zw - s * 2;
        for (int x = x0; x <= x1; x++) {
            for (int z = Math.min(zw, zf); z <= Math.max(zw, zf); z++) {
                w.set(x, y, z, Math.floorMod(x + z, 3) == 0 ? "hay_block" : "coarse_dirt");
                w.set(x, y + 4, z, z == zw ? "dark_oak_slab[type=top]" : "dark_oak_slab[type=bottom]");
            }
            w.set(x, y + 4, zf - s, "dark_oak_stairs[facing=" + face + ",half=bottom]");
            if (Math.floorMod(x - x0, 3) == 0) {
                for (int dy = 1; dy <= 3; dy++) w.set(x, y + dy, zf, "spruce_log[axis=y]");
                w.set(x, y + 1, zw, "spruce_fence");
                w.set(x, y + 1, zw - s, "spruce_fence");
            } else if (Math.floorMod(x - x0, 3) == 1) {
                w.set(x, y + 1, zw, "water_cauldron[level=3]");
            } else {
                w.set(x, y + 1, zw, "hay_block");
            }
        }
        w.hangLight(x0 + 5, y + 3, zw - s, "lantern");
    }

    /** The smithy and armoury along the south wall: a lean-to with a forge, its chimney up the wall, an anvil. */
    void smithy(int zw, String face) {
        int y = CASTLE, x0 = CX + 4, x1 = CX + 13, s = Integer.signum(zw), zf = zw - s * 2;
        for (int x = x0; x <= x1; x++) {
            for (int z = Math.min(zw, zf); z <= Math.max(zw, zf); z++) {
                w.set(x, y, z, ((x + z) & 1) == 0 ? "stone_bricks" : "cobblestone");
                w.set(x, y + 4, z, z == zw ? "dark_oak_slab[type=top]" : "dark_oak_slab[type=bottom]");
            }
            w.set(x, y + 4, zf - s, "dark_oak_stairs[facing=" + face + ",half=bottom]");
            if (Math.floorMod(x - x0, 3) == 0) for (int dy = 1; dy <= 3; dy++) w.set(x, y + dy, zf, "spruce_log[axis=y]");
        }
        w.set(x0 + 1, y + 1, zw, "blast_furnace[facing=" + face + ",lit=true]");
        w.set(x0 + 2, y + 1, zw, "stone_bricks");
        w.set(x0 + 2, y + 2, zw, "campfire[lit=false]");
        for (int dy = 3; dy <= 11; dy++) w.set(x0 + 2, y + dy, zw, dy <= 3 ? "stone_bricks" : "bricks");
        w.set(x0 + 4, y + 1, zw - s, "anvil[facing=east]");
        w.set(x0 + 6, y + 1, zw, "grindstone[face=floor,facing=" + face + "]");
        w.set(x0 + 7, y + 1, zw, "smithing_table");
        w.set(x0 + 8, y + 1, zw, "water_cauldron[level=3]");
        w.set(x1, y + 1, zw, "barrel[facing=up]");
        w.set(x1, y + 2, zw, "barrel[facing=up]");
        w.hangLight(x0 + 5, y + 3, zw - s, "lantern");
    }

    /** Posterns through the north and south walls, and stone stairs down the hill to the border lands. */
    void posterns() {
        int y = CASTLE;
        for (int s : new int[] {-1, 1}) {
            for (int x = CX + 1; x <= CX + 3; x++) {
                for (int zz = CW - 2; zz <= CW; zz++) for (int dy = 1; dy <= 3; dy++) w.set(x, y + dy, s * zz, "air");
                for (int zz = CW - 2; zz <= CW; zz++) w.set(x, y + 4, s * zz, zz == CW ? "chiseled_stone_bricks" : "stone_bricks");
                for (int zz = CW - 2; zz <= CW + 3; zz++) w.set(x, y, s * zz, "cobblestone");
                // the battered base course and the buttress outside the door go
                for (int zz = CW + 1; zz <= CW + 3; zz++) for (int dy = 1; dy <= 7; dy++) if (zz > CW + 1 || dy <= 6) w.set(x, y + dy, s * zz, "air");
            }
            // the stairs: the first flush with the hilltop, down to the meadow
            String up = s < 0 ? "south" : "north";
            for (int i = 0; i < 9; i++) {
                int z = s * (CW + 4 + i), sy = y - i;
                for (int x = CX; x <= CX + 4; x++) {
                    boolean side = (x == CX || x == CX + 4) && i < 7;
                    int g = land.topAt(x, z);
                    for (int yy = Math.min(g, sy) - 1; yy < sy; yy++) w.set(x, yy, z, side ? stoneAt(x, yy, z, 3) : "cobblestone");
                    for (int yy = sy + 1; yy <= Math.max(g, sy + 4); yy++) if (!side || yy > sy + 1) w.set(x, yy, z, "air");
                    if (side) {
                        w.set(x, sy, z, "stone_bricks");
                        w.set(x, sy + 1, z, "stone_brick_wall");
                    } else w.set(x, sy, z, "stone_brick_stairs[facing=" + up + ",half=bottom]");
                }
            }
            int lg = land.topAt(CX - 1, s * (CW + 10));
            w.set(CX - 1, lg + 1, s * (CW + 10), "stone_bricks");
            w.set(CX - 1, lg + 2, s * (CW + 10), "stone_brick_wall");
            w.set(CX - 1, lg + 3, s * (CW + 10), "lantern");
            keepClear(CX - 1, Math.min(s * (CW + 1), s * (CW + 14)), CX + 5, Math.max(s * (CW + 1), s * (CW + 14)));
        }
    }

    /**
     * The causeway road from the gate down toward the town: half a block a block on a stone embankment, a parapet
     * each side, lamp posts; it stops over the void short of the town.
     */
    void causeway() {
        for (int x = -57; x <= -44; x++) {
            double walk = 77.5 - 0.5 * (x + 57);
            int b = (int) Math.floor(walk) - (walk == Math.floor(walk) ? 1 : 0);
            boolean half = walk != Math.floor(walk);
            for (int z = -3; z <= 3; z++) {
                boolean side = Math.abs(z) == 3;
                int g = land.topAt(x, z);
                for (int yy = Math.min(g, b) - 1; yy < b; yy++) w.set(x, yy, z, side ? stoneAt(x, yy, z, 3) : "cobblestone");
                for (int yy = b + 1; yy <= b + 5; yy++) w.set(x, yy, z, "air");
                if (side) {
                    w.set(x, b, z, stoneAt(x, b, z, 3));
                    w.set(x, b + 1, z, "stone_brick_wall");
                } else if (half) {
                    w.set(x, b - 1, z, "cobblestone");
                    w.set(x, b, z, Math.abs(z) == 2 ? "stone_brick_slab[type=bottom]" : "cobblestone_slab[type=bottom]");
                } else {
                    w.set(x, b, z, Math.abs(z) == 2 ? "stone_bricks" : (((x + z) & 1) == 0 ? "cobblestone" : "andesite"));
                }
            }
            if (Math.floorMod(x, 6) == 0) for (int s : new int[] {-1, 1}) {
                w.set(x, b + 1, s * 3, "stone_bricks");
                w.set(x, b + 2, s * 3, "spruce_fence");
                w.set(x, b + 3, s * 3, "lantern");
            }
        }
        // where it breaks off over the void: a worn end of setts, a milestone
        w.set(-44, land.topAt(-44, -3) + 1, -3, "stone_bricks");
        w.set(-44, land.topAt(-44, -3) + 2, -3, "lantern");
    }

    // ================================================================ the town

    void town() {
        int y = TOWN;
        // ground: streets, the ring lane, the square, yards
        for (int x = -32; x <= 0; x++) for (int z = -32; z <= 32; z++) {
            if (!w.canon(x, z)) continue;
            double r = r(x, z);
            if (r > 30.2) continue;
            for (int dy = 1; dy <= 12; dy++) if (!w.air(x, y + dy, z)) w.set(x, y + dy, z, "air");
            double n = w.n2(x * 1.9, z * 1.9, 2);
            int ax = Math.abs(x), az = Math.abs(z);
            String p;
            if (Math.max(ax, az) <= 9 && ax + az <= 15) p = plaza(x, z);
            else if (Math.min(ax, az) <= 2 || r >= 24) p = n > 0.4 ? "mossy_cobblestone" : n < -0.35 ? "andesite" : ((x + z) & 1) == 0 ? "cobblestone" : "stone_bricks";
            else p = n > 0.3 ? "coarse_dirt" : n < -0.3 ? "packed_mud" : "grass_block";
            w.set(x, y, z, p);
        }
        townWall();
        townTurret(-24, -15);
        townBlock();
        fountains();
        for (int x = -36; x <= 0; x++) for (int z = -36; z <= 36; z++) {
            if (!w.canon(x, z)) continue;
            bridgeCell(x, z);
        }
        keepClear(-40, -40, 0, 40);
    }

    /** The square's paving: rings of andesite round a compass of stone, setts between. */
    String plaza(int x, int z) {
        double d = r(x, z);
        if (d < 1.5) return "chiseled_stone_bricks";
        if (d < 2.6) return "polished_andesite";
        if (d < 3.6) return "stone_bricks";
        if (d < 4.6) return "polished_tuff";
        if ((x == 0 || z == 0) && d < 8.4) return "polished_andesite";
        if (d > 8.4 && d < 9.6) return "polished_tuff";
        double n = w.n2(x * 2.2, z * 2.2, 2);
        return n > 0.45 ? "mossy_stone_bricks" : n < -0.45 ? "cracked_stone_bricks" : ((x + z) & 1) == 0 ? "stone_bricks" : "andesite";
    }

    /**
     * The town wall: crenellated stone on the moat's revetment, a gatehouse at each bridge (two square towers
     * with slate roofs, an arch with a portcullis), a postern on each diagonal.
     */
    void townWall() {
        int y = TOWN;
        for (int x = -31; x <= 0; x++) for (int z = -31; z <= 31; z++) {
            if (!w.canon(x, z)) continue;
            double r = r(x, z);
            if (r < 28 || r > 30.2) continue;
            for (int dy = 1; dy <= 6; dy++) w.set(x, y + dy, z, dy <= 1 ? base().pick(w.r) : stoneAt(x, y + dy, z, dy));
            if (r > 29.4) {
                double ang = Math.atan2(z, x);
                if (((int) Math.floor(ang * 30 / Math.PI)) % 2 == 0) w.set(x, y + 7, z, "stone_brick_wall");
            }
            // the gate (the street) and the postern (the diagonal)
            boolean gate = Math.abs(z) <= 2, postern = diagOff(x, z) <= 1.0;
            if (gate) for (int dy = 1; dy <= 5; dy++) w.set(x, y + dy, z, dy == 5 && Math.abs(z) == 2 ? "stone_brick_stairs[facing=" + (z < 0 ? "north" : "south") + ",half=top]" : "air");
            if (postern) for (int dy = 1; dy <= 3; dy++) w.set(x, y + dy, z, "air");
            if (postern) w.set(x, y + 4, z, "chiseled_stone_bricks");
        }
        // the gatehouse astride the wall over the street: an arch through it, battlements, a little bartizan each corner
        for (int x = -31; x <= -25; x++) for (int z = -5; z <= 5; z++) {
            k.footing(x, z, x, z, y, base());
            boolean edge = x == -31 || x == -25 || Math.abs(z) == 5;
            for (int dy = 1; dy <= 9; dy++) w.set(x, y + dy, z, !edge && dy >= 6 && dy <= 8 ? "air" : dy <= 1 ? base().pick(w.r) : dy == 6 ? "polished_andesite" : stoneAt(x, y + dy, z, dy));
            if (!edge) w.set(x, y + 5, z, "spruce_planks");
            if (edge && ((x + z) & 1) == 0) w.set(x, y + 10, z, "stone_brick_wall");
            if (edge) {
                int ox = x == -31 ? -1 : x == -25 ? 1 : 0, oz = Math.abs(z) == 5 ? Integer.signum(z) : 0;
                if (ox != 0 && oz == 0) w.set(x + ox, y + 9, z, "stone_brick_stairs[facing=" + (ox < 0 ? "east" : "west") + ",half=top]");
                if (oz != 0 && ox == 0) w.set(x, y + 9, z + oz, "stone_brick_stairs[facing=" + (oz < 0 ? "south" : "north") + ",half=top]");
            }
        }
        for (int x = -31; x <= -25; x++) for (int z = -2; z <= 2; z++) {
            for (int dy = 1; dy <= 4; dy++) w.set(x, y + dy, z, "air");
            if (Math.abs(z) <= 1) w.set(x, y + 5, z, "air");
            w.set(x, y, z, ((x + z) & 1) == 0 ? "stone_bricks" : "cobblestone");
        }
        for (int x : new int[] {-31, -25}) {
            w.set(x, y + 5, -2, "stone_brick_stairs[facing=north,half=top]");
            w.set(x, y + 5, 2, "stone_brick_stairs[facing=south,half=top]");
            w.set(x, y + 6, 0, "chiseled_stone_bricks");
        }
        for (int z = -1; z <= 1; z++) w.set(-30, y + 5, z, "iron_bars");
        // slits, banners and lamps on the outer face; a lamp in the arch
        for (int s : new int[] {-1, 1}) {
            for (int dy = 6; dy <= 7; dy++) w.set(-31, y + dy, s * 4, "air");
            w.set(-32, y + 7, s * 2, "TEAM_wall_banner[facing=west]");
        }
        w.hangLight(-28, y + 4, 0, "lantern");
    }

    /** A round turret on the town wall beside a postern (and its mirror on the other side): solid, a slate cone. */
    void townTurret(int tcx, int tcz) {
        int y = TOWN;
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            double d = Math.hypot(dx, dz);
            if (d > 2.45) continue;
            int x = tcx + dx, z = tcz + dz;
            for (int dy = 1; dy <= 9; dy++) m(x, y + dy, z, dy <= 1 ? base().pick(w.r) : dy == 7 ? "polished_andesite" : stoneAt(x, y + dy, z, dy));
        }
        for (int a = 0; a < 4; a++) {
            int x = tcx + (a == 0 ? 2 : a == 1 ? -2 : 0), z = tcz + (a == 2 ? 2 : a == 3 ? -2 : 0);
            m(x, y + 8, z, "air");
        }
        k.cone(tcx, tcz, y + 10, 3, 1, 1.6, Kit.Roof.of("deepslate_tiles"));
        k.cone(tcz, tcx, y + 10, 3, 1, 1.6, Kit.Roof.of("deepslate_tiles"));
    }

    /** A bridge or causeway cell: over the moat from the island's rim up to the gate or the postern. */
    void bridgeCell(int x, int z) {
        double r = r(x, z);
        if (r < 31.5 || r > 36.6) return;
        boolean street = Math.abs(z) <= 3 && x < 0, diag = diagOff(x, z) <= 1.45;
        if (!street && !diag) return;
        boolean rail = street ? Math.abs(z) == 3 : diagOff(x, z) > 1.0;
        double walk = Math.max(TOWN - 1, Math.min(TOWN + 1, Math.round((TOWN - 1 + 2 * (36.6 - r) / 5.1) * 2) / 2.0));
        int b = (int) Math.floor(walk) - (walk == Math.floor(walk) ? 1 : 0);
        boolean half = walk != Math.floor(walk);
        String deck = street ? "stone_bricks" : "spruce_planks", slab = street ? "stone_brick_slab" : "spruce_slab";
        for (int yy = b + 1; yy <= b + 4; yy++) w.set(x, yy, z, "air");
        if (rail) {
            w.set(x, b, z, street ? "stone_bricks" : "stripped_spruce_log[axis=y]");
            w.set(x, b + 1, z, street ? "stone_brick_wall" : "spruce_fence");
        } else if (half) {
            w.set(x, b - 1, z, deck);
            w.set(x, b, z, slab + "[type=bottom]");
        } else {
            w.set(x, b, z, deck);
        }
        // piers down into the water
        if (moat(x, z) && street && Math.round(r) == 33 && Math.abs(z) % 2 == 1) for (int yy = WATER - 3; yy < b; yy++) w.set(x, yy, z, "stone_bricks");
        if (moat(x, z) && !street && Math.abs(r - 33.5) < 0.8 && !rail) for (int yy = WATER - 3; yy < b - 1; yy++) w.set(x, yy, z, "spruce_log[axis=y]");
    }

    /** Fountains at the square's four corners: a basin, a spout, lamps. */
    void fountains() {
        int y = TOWN, fx = -7, fz = -7;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            boolean mid = dx == 0 && dz == 0;
            w.set(fx + dx, y, fz + dz, mid ? "stone_bricks" : "water");
            w.set(fx + dx, y - 1, fz + dz, "stone_bricks");
        }
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) != 2) continue;
            w.set(fx + dx, y, fz + dz, "stone_bricks");
            w.set(fx + dx, y + 1, fz + dz, Math.abs(dx) == 2 && Math.abs(dz) == 2 ? "chiseled_stone_bricks" : "stone_brick_slab[type=bottom]");
        }
        w.set(fx, y + 1, fz, "stone_brick_wall");
        w.set(fx, y + 2, fz, "stone_brick_wall");
        w.set(fx, y + 3, fz, "lantern");
    }

    /**
     * One block of the town (copied round to the other three): a merchant's house along the west street, a
     * tavern along the north street, a tower house on the corner toward the square, and the yards behind.
     */
    void townBlock() {
        townHouse(-22, -9, -13, -4, "south", 0);
        townHouse(-9, -22, -4, -13, "east", 1);
        towerHouse(-16, -16, -11, -11);
        // a market stall in front of each house, under the kingdom's colour
        stall(-21, -3, true);
        stall(-3, -21, false);
        // the yards: a well, a cart, a woodpile
        w.set(-20, TOWN + 1, -12, "barrel[facing=up]");
        w.set(-20, TOWN + 1, -13, "barrel[facing=up]");
        for (int x = -12; x <= -10; x++) w.set(x, TOWN + 1, -20, "spruce_log[axis=x]");
        w.set(-11, TOWN + 2, -20, "spruce_log[axis=x]");
    }

    /** A market stall: four posts, a striped awning, goods on the counter. */
    void stall(int cx, int cz, boolean alongX) {
        int y = TOWN;
        for (int[] p : new int[][] {{-1, 0}, {1, 0}}) {
            int x = cx + (alongX ? p[0] * 2 : 0), z = cz + (alongX ? 0 : p[0] * 2);
            for (int dy = 1; dy <= 2; dy++) w.set(x, y + dy, z, "spruce_fence");
        }
        for (int o = -2; o <= 2; o++) {
            int x = cx + (alongX ? o : 0), z = cz + (alongX ? 0 : o);
            w.set(x, y + 3, z, (o & 1) == 0 ? "TEAM_wool" : "white_wool");
            if (Math.abs(o) <= 1) w.set(x, y + 1, z, o == 0 ? "barrel[facing=up]" : "spruce_slab[type=top]");
        }
        w.set(cx + (alongX ? -1 : 0), y + 2, cz + (alongX ? 0 : -1), "melon");
        w.set(cx + (alongX ? 1 : 0), y + 2, cz + (alongX ? 0 : 1), "pumpkin");
    }

    /**
     * A timber-framed town house: stone or brick ground floor with a shop, the upper floor jettied out over the
     * street, a steep roof (red tile or slate) with a chimney, shuttered windows with flower boxes, a hanging
     * lamp by the door. style 0: brick and plaster under red tiles; 1: stone and plaster under slate.
     */
    void townHouse(int x0, int z0, int x1, int z1, String face, int style) {
        int y = TOWN;
        boolean alongX = x1 - x0 >= z1 - z0;
        String ground = style == 0 ? "bricks" : "stone_bricks", frame = style == 0 ? "dark_oak_log" : "spruce_log",
                plaster = style == 0 ? "calcite" : "white_terracotta", roof = style == 0 ? "bricks" : "deepslate_tiles";
        int fx = Kit.dx(face), fz = Kit.dz(face);
        k.walls(x0, z0, x1, z1, y, 4, Kit.Bands.of(0, "cobblestone", 1, ground), frame, 3, P.of("spruce_planks"));
        // the jettied upper floor: one out over the street face
        int ux0 = x0 + Math.min(0, fx), ux1 = x1 + Math.max(0, fx), uz0 = z0 + Math.min(0, fz), uz1 = z1 + Math.max(0, fz);
        k.walls(ux0, uz0, ux1, uz1, y + 4, 4, Kit.Bands.of(0, "stripped_" + frame.replace("_log", "_wood"), 1, plaster, 4, "stripped_" + frame + "[axis=" + (alongX ? "x" : "z") + "]"), frame, 3,
                P.of("spruce_planks"));
        int sx0 = fx > 0 ? x1 + 1 : fx < 0 ? x0 - 1 : x0, sx1 = fx > 0 ? x1 + 1 : fx < 0 ? x0 - 1 : x1;
        int sz0 = fz > 0 ? z1 + 1 : fz < 0 ? z0 - 1 : z0, sz1 = fz > 0 ? z1 + 1 : fz < 0 ? z0 - 1 : z1;
        for (int x = sx0; x <= sx1; x++) for (int z = sz0; z <= sz1; z++) w.set(x, y + 4, z, (style == 0 ? "dark_oak" : "spruce") + "_stairs[facing=" + Kit.opp(face) + ",half=top]");
        int ridge = k.gable(ux0, uz0, ux1, uz1, y + 9, alongX, 1, Kit.Roof.of(roof), P.of(plaster, 3, "stripped_" + frame + "[axis=y]", 1));
        // a chimney up the gable wall
        int cx = alongX ? x0 : (x0 + x1) / 2, cz = alongX ? (z0 + z1) / 2 : z0;
        for (int yy = y + 1; yy <= ridge + 2; yy++) w.set(cx, yy, cz, "bricks");
        w.set(cx, ridge + 3, cz, "brick_wall");
        // door, windows below and above with flower boxes, the lamp
        int dx = fx != 0 ? (fx > 0 ? x1 : x0) : (x0 + x1) / 2, dz = fz != 0 ? (fz > 0 ? z1 : z0) : (z0 + z1) / 2;
        k.doorway(dx, y, dz, face, 1, "spruce_door", null, null, null);
        w.set(dx, y + 1, dz, "spruce_door[facing=" + Kit.opp(face) + ",half=lower,hinge=left,open=true]");
        w.set(dx, y + 2, dz, "spruce_door[facing=" + Kit.opp(face) + ",half=upper,hinge=left,open=true]");
        for (int o : new int[] {-3, 0, 3}) {
            int wx = dx + (fz != 0 ? o : 0), wz = dz + (fx != 0 ? o : 0);
            if (wx < x0 + 1 || wx > x1 - 1 || wz < z0 + 1 || wz > z1 - 1) continue;
            if (o != 0) k.window(wx, y + 2, wz, face, 1, 1, "glass_pane", null, "spruce_trapdoor");
            int uwx = fx != 0 ? (fx > 0 ? ux1 : ux0) : wx, uwz = fz != 0 ? (fz > 0 ? uz1 : uz0) : wz;
            k.window(uwx, y + 6, uwz, face, 1, 1, "glass_pane", null, null);
            w.set(uwx + fx, y + 5, uwz + fz, "spruce_trapdoor[facing=" + face + ",half=top,open=false]");
            w.set(uwx + fx, y + 6, uwz + fz, w.r.nextBoolean() ? "potted_red_tulip" : "potted_cornflower");
        }
        // windows on the back too, upstairs and down, so the yards behind look lived in
        String back = Kit.opp(face);
        int bxc = fx > 0 ? x0 : fx < 0 ? x1 : 0, bzc = fz > 0 ? z0 : fz < 0 ? z1 : 0;
        for (int o : new int[] {2, -2}) {
            int wx = fz != 0 ? (o > 0 ? x0 + o : x1 + o) : bxc, wz = fx != 0 ? (o > 0 ? z0 + o : z1 + o) : bzc;
            k.window(wx, y + 6, wz, back, 1, 1, "glass_pane", null, "spruce_trapdoor");
            if (o < 0) k.window(wx, y + 2, wz, back, 1, 1, "glass_pane", null, "spruce_trapdoor");
        }
        int lx = dx + fx + (fz != 0 ? 1 : 0), lz = dz + fz + (fx != 0 ? 1 : 0);
        w.set(lx, y + 3, lz, frame.replace("_log", "_fence"));
        w.set(lx, y + 2, lz, "lantern[hanging=true]");
        // inside: a counter and goods below, a bed and a chest above (up a ladder)
        int ix = (x0 + x1) / 2, iz = (z0 + z1) / 2;
        for (int o = -1; o <= 1; o++) w.set(ix + (fz != 0 ? o : 0), y + 1, iz + (fx != 0 ? o : 0), "spruce_slab[type=top]");
        w.set(x0 + 1, y + 1, z1 - 1, "barrel[facing=up]");
        w.set(x1 - 1, y + 1, z1 - 1, "crafting_table");
        w.hangLight(ix, y + 3, iz, "lantern");
        int lx2 = fx != 0 ? (fx > 0 ? x0 + 1 : x1 - 1) : x0 + 1, lz2 = fz != 0 ? (fz > 0 ? z0 + 1 : z1 - 1) : z0 + 1;
        for (int dy = 1; dy <= 5; dy++) w.set(lx2, y + dy, lz2, "ladder[facing=" + face + "]");
        k.bed(fx != 0 ? ix : x1 - 2, y + 5, fz != 0 ? iz : z0 + 2, fx != 0 ? "north" : "east", "white");
        w.set(x1 - 1, y + 5, fz > 0 ? z0 + 1 : z1 - 1, "chest[facing=" + face + "]");
    }

    /**
     * A merchant's tower house on the corner of the square: a stone ground floor, two storeys of brick with stone
     * quoins above it, a corbelled top under a steep timber-shingled hip roof, doors toward the square both ways.
     */
    void towerHouse(int x0, int z0, int x1, int z1) {
        int y = TOWN, h = 12;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            boolean edge = x == x0 || x == x1 || z == z0 || z == z1, corner = (x == x0 || x == x1) && (z == z0 || z == z1);
            for (int dy = 1; dy <= h; dy++) {
                String wall = dy <= 1 ? base().pick(w.r) : dy <= 4 ? stoneAt(x, y + dy, z, dy) : dy == 5 ? "polished_andesite"
                        : corner ? ((dy & 1) == 0 ? "stone_bricks" : "polished_andesite") : "bricks";
                w.set(x, y + dy, z, edge ? wall : dy == 5 || dy == 9 ? "spruce_planks" : "air");
            }
            w.set(x, y, z, edge ? "cobblestone" : "spruce_planks");
            if (edge && !corner) {
                int ox = x == x0 ? -1 : x == x1 ? 1 : 0, oz = z == z0 ? -1 : z == z1 ? 1 : 0;
                w.set(x + ox, y + h, z + oz, "stone_brick_stairs[facing=" + (ox < 0 ? "east" : ox > 0 ? "west" : oz < 0 ? "south" : "north") + ",half=top]");
            }
        }
        k.hip(x0 - 1, z0 - 1, x1 + 1, z1 + 1, y + h + 1, 0, Kit.Roof.of("dark_oak_planks"), null);
        // doors toward the square on both faces under little stone hoods, windows all round each storey
        for (int dy = 1; dy <= 2; dy++) {
            w.set(x1, y + dy, z1 - 2, "air");
            w.set(x1 - 2, y + dy, z1, "air");
        }
        w.set(x1 + 1, y + 3, z1 - 2, "stone_brick_slab[type=top]");
        w.set(x1 - 2, y + 3, z1 + 1, "stone_brick_slab[type=top]");
        for (int fl : new int[] {7, 11}) for (int o = 2; o <= 3; o++) {
            w.set(x0 + o, y + fl, z0, "glass_pane");
            w.set(x0, y + fl, z0 + o, "glass_pane");
            w.set(x0 + o, y + fl, z1, "glass_pane");
            w.set(x1, y + fl, z0 + o, "glass_pane");
        }
        for (int fl : new int[] {7, 11}) {
            w.set(x0 + 2, y + fl - 1, z1 + 1, "spruce_trapdoor[facing=south,half=top,open=false]");
            w.set(x0 + 3, y + fl - 1, z1 + 1, "spruce_trapdoor[facing=south,half=top,open=false]");
            w.set(x1 + 1, y + fl - 1, z0 + 2, "spruce_trapdoor[facing=east,half=top,open=false]");
            w.set(x1 + 1, y + fl - 1, z0 + 3, "spruce_trapdoor[facing=east,half=top,open=false]");
        }
        // a ladder up inside, a lamp on each floor, the counting room
        for (int dy = 1; dy <= 9; dy++) w.set(x0 + 1, y + dy, z0 + 1, "ladder[facing=south]");
        w.hangLight(x1 - 2, y + 4, z0 + 2, "lantern");
        w.hangLight(x1 - 2, y + 8, z1 - 2, "lantern");
        w.hangLight(x0 + 2, y + 12, z1 - 2, "lantern");
        w.set(x0 + 1, y + 1, z1 - 1, "lectern[facing=east]");
        w.set(x0 + 2, y + 1, z1 - 1, "chest[facing=north]");
        w.set(x1 - 1, y + 1, z0 + 1, "barrel[facing=up]");
        w.set(x1 - 1, y + 6, z0 + 1, "barrel[facing=up]");
        k.bed(x1 - 2, y + 10, z0 + 1, "east", "TEAM");
        w.set(x1 + 1, y + 4, (z0 + z1) / 2 - 1, "TEAM_wall_banner[facing=east]");
        w.set((x0 + x1) / 2 - 1, y + 4, z1 + 1, "TEAM_wall_banner[facing=south]");
    }

    // ================================================================ the border lands

    /** Reflects a block across the diagonal x = z: east and south change places, as do west and north. */
    static String mir(String s) {
        if (!s.contains("[")) return s;
        return s.replace("facing=east", "facing=#S").replace("facing=south", "facing=east").replace("facing=#S", "facing=south")
                .replace("facing=west", "facing=#N").replace("facing=north", "facing=west").replace("facing=#N", "facing=north")
                .replace("axis=x", "axis=#Z").replace("axis=z", "axis=x").replace("axis=#Z", "axis=z")
                .replace("east=true", "#S=true").replace("south=true", "east=true").replace("#S=true", "south=true")
                .replace("west=true", "#N=true").replace("north=true", "west=true").replace("#N=true", "north=true");
    }

    /** Sets a block and its mirror across the north-west diagonal (and with them, all their quarter-turn copies). */
    void m(int x, int y, int z, String spec) {
        w.set(x, y, z, spec);
        if (x != z) w.set(z, y, x, mir(spec));
    }

    /**
     * The north-west diagonal (copied round): the ruined tower on its rock pinnacle; the waystone on its islet off
     * the town's postern; the border wood, with the spring among its rocks and the brook running down the wood to a
     * little pool, a plank footbridge over it.
     */
    void borderLand() {
        int c = -BT;
        ruin(c, c);
        // the waystone: a standing stone with a lantern on it, a cairn either side, moss
        int y = WAY, wx = -33;
        m(wx, y + 1, wx, "mossy_stone_bricks");
        m(wx, y + 2, wx, "stone_bricks");
        m(wx, y + 3, wx, "chiseled_stone_bricks");
        m(wx, y + 4, wx, "lantern");
        m(wx - 2, y + 1, wx + 1, "mossy_cobblestone");
        m(wx - 2, y + 2, wx + 1, "cobblestone_slab[type=bottom]");
        m(wx + 1, y + 1, wx + 2, "cobblestone_slab[type=bottom]");
        m(wx - 1, y + 1, wx + 1, "moss_carpet");
        keepClear(wx - 4, wx - 4, wx + 4, wx + 4);
        // the spring: rocks round the pool, ferns and moss
        int sp = -66;
        for (int a = 0; a < 360; a += 24) {
            double rad = Math.toRadians(a);
            int x = sp + (int) Math.round(Math.cos(rad) * 5.6), z = sp + (int) Math.round(Math.sin(rad) * 5.6);
            if (x > z || brook(x, z)) continue;
            int g = land.topAt(x, z);
            int h = 1 + (a / 24) % 2;
            for (int dy = 1; dy <= h; dy++) m(x, g + dy, z, dy == h ? "mossy_cobblestone" : w.r.nextBoolean() ? "stone" : "andesite");
        }
        // the plank footbridge over the brook, flush with the banks
        for (int x = -66; x <= -58; x++) for (int z = -66; z <= -58; z++) {
            double s = diagAlong(x, z);
            if (diagOff(x, z) > 2.2 || s < 86.5 || s > 88.5 || x > z) continue;
            m(x, WOOD, z, "spruce_planks");
            for (int yy = WOOD + 1; yy <= WOOD + 3; yy++) m(x, yy, z, "air");
        }
        keepClear(c - 9, c - 9, c + 9, c + 9);
        keepClear(-64, -64, -58, -58);
    }

    /** A trodden path along a polyline, mirrored across the diagonal if asked. */
    void path(int[][] pts, boolean mirror) {
        for (int i = 0; i + 1 < pts.length; i++) {
            int[] a = pts[i], b = pts[i + 1];
            int n = Math.max(Math.abs(b[0] - a[0]), Math.abs(b[1] - a[1]));
            for (int j = 0; j <= n; j++) {
                int x = a[0] + (int) Math.round((b[0] - a[0]) * (double) j / n), z = a[1] + (int) Math.round((b[1] - a[1]) * (double) j / n);
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    int px = x + dx, pz = z + dz;
                    int g = land.topAt(px, pz);
                    if (g == Integer.MIN_VALUE || w.type(px, g, pz) != Material.GRASS_BLOCK) continue;
                    double nn = w.n2(px * 2.4, pz * 2.4, 2);
                    if (Math.abs(dx) + Math.abs(dz) == 2 && nn > -0.1) continue;
                    String s = nn > 0.35 ? "coarse_dirt" : nn < -0.3 ? "gravel" : "dirt_path";
                    if (mirror) m(px, g, pz, s);
                    else w.set(px, g, pz, s);
                }
                keepClear(x - 2, z - 2, x + 2, z + 2);
                if (mirror) keepClear(z - 2, x - 2, z + 2, x + 2);
            }
        }
    }

    /**
     * The ruined tower on its rock pinnacle: a round shell broken down to a jagged top (higher on its outer side),
     * a doorway on each side, rubble, ivy. Open to the sky.
     */
    void ruin(int cx, int cz) {
        int y = TOWER;
        double r = 5.5;
        // the island: old paving inside, grass and gravel round it
        for (int dx = -8; dx <= 8; dx++) for (int dz = -8; dz <= 8; dz++) {
            int x = cx + dx, z = cz + dz;
            double d = Math.hypot(dx, dz);
            if (d >= 8 || x > z || land.topAt(x, z) != y) continue;
            double n = w.n2(x * 2.1, z * 2.1, 2);
            m(x, y, z, d < 4.3 ? (n > 0.3 ? "mossy_stone_bricks" : n < -0.3 ? "cracked_stone_bricks" : d < 1 ? "chiseled_stone_bricks" : "stone_bricks")
                    : n > 0.3 ? "coarse_dirt" : n < -0.4 ? "gravel" : "grass_block");
        }
        for (int dx = -7; dx <= 7; dx++) for (int dz = -7; dz <= 7; dz++) {
            int x = cx + dx, z = cz + dz;
            double d = Math.hypot(dx, dz);
            if (d > r + 0.45 || d <= r - 1.4 || x > z) continue;
            boolean outerSkin = d > r - 0.5;
            // the broken top: tall toward the outside of the map, down to stumps toward the town
            double out = -(dx + dz) / (Math.sqrt(2) * r);
            int h = (int) Math.round(3.5 + 8 * Math.max(0, out) + 2.2 * w.n2(x * 0.9 + 7, z * 0.9 + 7, 1) + (outerSkin ? 0 : -1));
            h = Math.max(2, Math.min(13, h));
            boolean door = Math.abs(dx) <= 1 && Math.abs(dz) >= 4 || Math.abs(dz) <= 1 && Math.abs(dx) >= 4;
            for (int dy = 1; dy <= h; dy++) {
                if (door && dy <= 3) {
                    m(x, y + dy, z, "air");
                    continue;
                }
                String st = dy <= 1 ? base().pick(w.r) : dy == 5 && outerSkin ? "polished_andesite" : stoneAt(x, y + dy, z, dy + 1);
                if (dy == h && h > 3 && w.r.nextInt(3) == 0) st = "mossy_cobblestone";
                m(x, y + dy, z, st);
            }
            if (door && h >= 5) {
                m(x, y + 4, z, "chiseled_stone_bricks");
            } else if (door) {
                for (int dy = 4; dy <= h; dy++) m(x, y + dy, z, "air");
            }
            // a window high in the tall side
            boolean diagonal = Math.abs(Math.abs(dx) - Math.abs(dz)) <= 0 && out > 0.3;
            if (diagonal && h >= 10) for (int dy = 7; dy <= 8; dy++) m(x, y + dy, z, "air");
            if (outerSkin && h >= 6 && w.r.nextInt(3) == 0) {
                int ox = Math.abs(dx) >= Math.abs(dz) ? Integer.signum(dx) : 0, oz = ox == 0 ? Integer.signum(dz) : 0;
                String toward = ox < 0 ? "east" : ox > 0 ? "west" : oz < 0 ? "south" : "north";
                for (int dy = 2; dy <= h - 1; dy++) if (w.air(x + ox, y + dy, z + oz) && !w.air(x, y + dy, z)) m(x + ox, y + dy, z + oz, "vine[" + toward + "=true]");
            }
        }
        // fallen stones round the outside
        for (int[] p : new int[][] {{-7, -3}, {-7, 2}, {-3, -7}, {3, 6}, {-6, 4}, {5, 4}}) {
            int x = cx + p[0], z = cz + p[1];
            if (x > z || land.topAt(x, z) != y || !w.air(x, y + 1, z)) continue;
            m(x, y + 1, z, w.r.nextBoolean() ? "cobblestone_slab[type=bottom]" : "mossy_cobblestone");
        }
    }

    // ================================================================ each kingdom's lands

    /**
     * The fields: a long strip of wheat, carrots, potatoes and beetroot between irrigation ditches, a hedgerow round
     * it with gaps, a path down the middle, a scarecrow among the crops and a haystack at the end.
     */
    void fields(int x0, int x1, int za, int zb) {
        int g = FIELDS, xm = (x0 + x1) / 2, zm = (za + zb) / 2;
        for (int x = x0; x <= x1; x++) for (int z = za; z <= zb; z++) {
            if (land.topAt(x, z) != g || isClear(x, z, 0)) continue;
            boolean edge = x == x0 || x == x1 || z == za || z == zb;
            if (edge) {
                boolean gap = Math.abs(z - zm) <= 1 || x == xm;
                if (!gap) w.set(x, g + 1, z, "oak_leaves[persistent=true]");
                continue;
            }
            int row = z - za;
            if (x == xm) {
                w.set(x, g, z, row % 6 == 3 ? "coarse_dirt" : "dirt_path");
                continue;
            }
            if (row % 6 == 3) {
                w.set(x, g, z, "water");
                continue;
            }
            int strip = (row / 6 + (x > xm ? 1 : 0)) % 4;
            w.set(x, g, z, "farmland[moisture=7]");
            w.set(x, g + 1, z, strip == 0 ? "wheat[age=7]" : strip == 1 ? "carrots[age=7]" : strip == 2 ? "potatoes[age=7]" : "beetroots[age=3]");
        }
        keepClear(x0, za, x1, zb);
        // a scarecrow among the crops
        int sx = x1 - 2, sz = za + 7;
        w.set(sx, g, sz, "coarse_dirt");
        w.set(sx, g + 1, sz, "spruce_fence");
        w.set(sx, g + 2, sz, "hay_block");
        w.set(sx, g + 3, sz, "carved_pumpkin[facing=east]");
        w.set(sx, g + 2, sz - 1, "spruce_fence");
        w.set(sx, g + 2, sz + 1, "spruce_fence");
        // the haystack at the far end
        for (int[] p : new int[][] {{0, 0}, {1, 0}, {0, 0, 1}}) {
            int x = xm + p[0], z = zb + 1, y = g + 1 + (p.length > 2 ? 1 : 0);
            if (land.topAt(x, z) == g) w.set(x, y, z, "hay_block");
        }
    }

    /** Paths on the castle terraces: from the foot of each postern stair to the orchard, the farmstead and the brink. */
    void terracePaths() {
        path(new int[][] {{-80, -32}, {-86, -34}, {-92, -36}}, false);
        path(new int[][] {{-80, -32}, {-74, -30}, {-70, -27}}, false);
        path(new int[][] {{-80, 32}, {-84, 34}, {-88, 34}}, false);
        path(new int[][] {{-80, 32}, {-73, 30}, {-66, 28}}, false);
    }

    /** The windmill: a round stone tower, a timber cap with its sails turned to the town, a door and a stair of sacks. */
    void windmill(int cx, int cz) {
        int y = land.topAt(cx, cz), h = 12;
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            double d = Math.hypot(dx, dz);
            if (d > 3.45) continue;
            int x = cx + dx, z = cz + dz;
            boolean shell = d > 2.6;
            for (int dy = 1; dy <= h; dy++) w.set(x, y + dy, z, !shell ? (dy == 6 ? "spruce_planks" : "air") : dy <= 6 ? (dy <= 1 ? base().pick(w.r) : stoneAt(x, y + dy, z, dy)) : dy == 7 ? "stripped_spruce_wood" : "spruce_planks");
            w.set(x, y, z, "cobblestone");
        }
        int top = k.cone(cx, cz, y + h + 1, 3, 1, 1.0, Kit.Roof.of("dark_oak_planks"));
        // the door facing the road and windows
        for (int dy = 1; dy <= 2; dy++) w.set(cx, y + dy, cz + 3, "air");
        w.set(cx, y + 3, cz + 3, "stone_brick_stairs[facing=north,half=top]");
        w.set(cx + 3, y + 9, cz, "glass_pane");
        w.set(cx - 3, y + 9, cz, "glass_pane");
        // the sails: a hub on the east side, four lattice arms with their cloth turned like a pinwheel
        int hx = cx + 4, hy = y + h - 1;
        w.set(cx + 3, hy, cz, "spruce_log[axis=x]");
        w.set(hx, hy, cz, "stripped_spruce_log[axis=x]");
        w.set(hx + 1, hy, cz, "spruce_planks");
        int[][] arms = {{1, 0, 0, 1}, {0, 1, -1, 0}, {-1, 0, 0, -1}, {0, -1, 1, 0}};
        for (int[] a : arms) for (int kk = 1; kk <= 7; kk++) {
            int ay = hy + a[0] * kk, az = cz + a[1] * kk;
            w.set(hx + 1, ay, az, "spruce_fence");
            if (kk >= 2) w.set(hx + 1, ay + a[2], az + a[3], "white_wool");
        }
        // sacks of flour by the door
        w.set(cx + 1, y + 1, cz + 4, "barrel[facing=up]");
        w.set(cx - 1, y + 1, cz + 4, "white_wool");
        keepClear(cx - 4, cz - 8, cx + 6, cz + 4);
    }

    /** A farmstead: a long barn with a red-brown roof and open doors, a cottage, a cart, pens. */
    void farmstead(int cx, int cz) {
        int y = TERRACE;
        // the barn
        int x0 = cx - 6, x1 = cx + 4, z0 = cz - 3, z1 = cz + 3;
        k.walls(x0, z0, x1, z1, y, 5, Kit.Bands.of(0, "cobblestone", 1, P.of("spruce_planks", 3, "stripped_spruce_log[axis=y]", 1)), "dark_oak_log", 4, P.of("coarse_dirt"));
        k.gable(x0, z0, x1, z1, y + 6, true, 1, Kit.Roof.of("mud_bricks"), P.of("spruce_planks"));
        for (int dz = -1; dz <= 1; dz++) for (int dy = 1; dy <= 4; dy++) w.set(x1, y + dy, cz + dz, "air");
        for (int x = x0 + 1; x <= x1 - 2; x++) for (int dy = 1; dy <= (x & 1) + 1; dy++) w.set(x, y + dy, z0 + 1, "hay_block");
        w.hangLight(cx, y + 4, cz, "lantern");
        // a pen beside it
        for (int x = x1 + 2; x <= x1 + 7; x++) for (int z = z0; z <= z1; z++) {
            boolean edge = x == x1 + 2 || x == x1 + 7 || z == z0 || z == z1;
            if (edge && !(x == x1 + 7 && z == cz)) w.set(x, y + 1, z, "spruce_fence");
            else if (!edge) w.set(x, y, z, "coarse_dirt");
        }
        w.set(x1 + 4, y + 1, cz, "water_cauldron[level=3]");
        // a cart
        for (int x = cx - 2; x <= cx; x++) w.set(x, y + 1, cz + 6, "spruce_slab[type=top]");
        w.set(cx - 1, y + 2, cz + 6, "hay_block");
        w.set(cx + 1, y + 1, cz + 6, "spruce_fence");
        keepClear(x0 - 2, z0 - 2, x1 + 8, z1 + 6);
    }

    /** An orchard of flowering fruit trees in rows, bee hives at the end. */
    void orchard(int cx, int cz) {
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) {
            int x = cx + i * 5, z = cz + j * 5;
            if (!w.canon(x, z)) continue;
            int g = land.topAt(x, z);
            if (g != TERRACE) continue;
            for (int dy = 1; dy <= 3; dy++) w.set(x, g + dy, z, "oak_log[axis=y]");
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 3; dy <= 5; dy++) {
                double d = Math.hypot(dx, dz) + Math.abs(dy - 4) * 0.9;
                if (d > 2.4 || dx == 0 && dz == 0 && dy == 3) continue;
                w.set(x + dx, g + dy, z + dz, w.r.nextInt(3) == 0 ? "flowering_azalea_leaves[persistent=true]" : "oak_leaves[persistent=true]");
            }
        }
        w.set(cx + 12, TERRACE + 1, cz + 1, "beehive[facing=east]");
        w.set(cx + 12, TERRACE + 1, cz + 9, "beehive[facing=east]");
        keepClear(cx - 3, cz - 3, cx + 13, cz + 13);
    }

    /** Flowers in drifts over the meadows, tall grass along the hedges, ferns in the woods. */
    void meadows() {
        for (int x = -120; x <= 0; x++) for (int z = -120; z <= 120; z++) {
            if (!w.canon(x, z)) continue;
            int g = land.topAt(x, z);
            if (g == Integer.MIN_VALUE || w.type(x, g, z) != Material.GRASS_BLOCK || !w.air(x, g + 1, z)) continue;
            double drift = dn(x, z, 0.8, 120, 8, 2), kind = dn(x, z, 0.35, 333, 12, 1);
            double roll = w.r.nextDouble();
            if (drift > 0.3 && roll < 0.3) {
                String f = kind > 0.25 ? "poppy" : kind > 0 ? "oxeye_daisy" : kind > -0.25 ? "cornflower" : "dandelion";
                if (roll < 0.05) f = "azure_bluet";
                w.set(x, g + 1, z, f);
            } else if (roll < 0.12) {
                w.set(x, g + 1, z, "short_grass");
            } else if (roll < 0.135 && !isClear(x, z, 3)) {
                w.set(x, g + 1, z, "tall_grass[half=lower]");
                w.set(x, g + 2, z, "tall_grass[half=upper]");
            }
        }
    }
}
