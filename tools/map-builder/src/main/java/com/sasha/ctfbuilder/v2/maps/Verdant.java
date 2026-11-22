package com.sasha.ctfbuilder.v2.maps;

import com.sasha.ctfbuilder.v2.Arena;
import com.sasha.ctfbuilder.v2.Kit;
import com.sasha.ctfbuilder.v2.Land;
import com.sasha.ctfbuilder.v2.Theme;
import com.sasha.ctfbuilder.v2.W.P;
import org.bukkit.World;

/**
 * Verdant Ruins: one great slab of jungle, no void to cross until the middle. Each team's step pyramid
 * holds its flag up in the temple on top (the defenders spawn inside and climb an inner stair; attackers take
 * the great stair outside). In front of each pyramid a river cuts the jungle from edge to edge: cross it on
 * the old stone bridge, wade the ford, or run the fallen giant across it. In the middle the jungle opens on a
 * cenote, a sinkhole into the void, with a ruin on a pillar in it reached by four rope bridges: the hill.
 * Dense trees and broken walls everywhere: close fighting, ambushes, few long sightlines.
 */
public final class Verdant extends Arena {

    static final int Y = 70, RIVER_X = -42, PYR_X = -76;

    public Verdant(World world) {
        super(world, "verdant", false, Theme.get("verdant"));
        name = "Verdant Ruins";
        icon = "JUNGLE_SAPLING";
        minutes = 16;
        kothPoints = 120;
        reach = 112;
        low = 20;
        high = 140;
        voidY = 18;
        spectator = new double[] {0.5, 110, 0.5, -90, 45};
    }

    double riverZ(int z) {
        return RIVER_X + Math.sin(z / 9.0) * 3;
    }

    double cenote(int x, int z) {
        return Math.hypot(x, z) + w.fbm(x, z, 6, 2) * 2;
    }

    double height(int x, int z) {
        double dx = Math.max(0, Math.abs(x) - 82), dz = Math.max(0, Math.abs(z) - 32);
        double edge = Math.hypot(dx, dz) + w.fbm(x * 0.7, z * 0.7, 14, 2) * 5 + w.fbm(x * 0.3 + 50, z * 0.3, 9, 2) * 6;
        if (edge > 17) return Double.NaN;
        double c = cenote(x, z);
        if (c < 17 && Math.hypot(x, z) > 6.5) return Double.NaN;      // the sinkhole
        if (Math.hypot(x, z) <= 6.5) return Y + 2;                        // the pillar in it
        double h = Y + w.fbm(x + 300, z - 120, 18, 3) * 3 * Land.smooth((Math.abs(x - PYR_X) - 22) / 10.0);
        // the river: a bed below the banks, a ford where it widens
        double r = Math.abs(x - riverZ(z));
        boolean ford = Math.abs(z + 25) < 5;
        if (r < 3.6) return ford ? Y - 2 : Y - 4;
        if (r < 5.5) h = Math.min(h, Y - 1);
        // the pyramid's plaza is flat
        if (Math.abs(x - PYR_X) < 22 && Math.abs(z) < 26) h = Y;
        if (edge > 13) h -= (edge - 13) * 0.6;
        return h;
    }

    String surface(int x, int z, int y, int steep, double edge) {
        double n = w.fbm(x + 913, z - 377, 8, 2);
        if (y < Y - 1) return n > 0.2 ? "gravel" : n < -0.3 ? "clay" : "mud";
        if (Math.abs(x - riverZ(z)) < 5.5) return n > 0 ? "mud" : "coarse_dirt";
        if (steep >= 3) return "mossy_cobblestone";
        if (n > 0.3) return "moss_block";
        if (n < -0.45) return "podzol";
        return w.r.nextInt(12) == 0 ? "coarse_dirt" : "grass_block";
    }

    @Override
    protected void build() {
        Land land = new Land(w, t, -104, 104, -54, 54);
        land.top = this::height;
        land.surface = this::surface;
        land.water = (x, z) -> Math.abs(x - riverZ(z)) < 3.6 && Math.abs(z) < 44 ? Y - 1 : Double.NaN;
        land.depth = 28;
        land.build();
        // close the river's ends so it can't run off the map: a rocky lip at each end
        for (int z : new int[] {-46, -45, 45, 46})
            for (int x = RIVER_X - 7; x <= RIVER_X + 7; x++) {
                int gy = w.ground(x, z);
                if (gy > 50 && gy < Y) for (int yy = gy + 1; yy <= Y; yy++) w.set(x, yy, z, "mossy_cobblestone");
            }

        pyramid(PYR_X, 0);
        river();
        cenoteRuin();
        ruins();

        trees(new Theme.Tree[] {Theme.Tree.JUNGLE, Theme.Tree.OAK, Theme.Tree.AZALEA}, new int[] {4, 2, 3}, 0.03, 7, 2,
                (x, z) -> Math.abs(x - riverZ(z)) > 7 && Math.hypot(x, z) > 20);
        // undergrowth: bushes of leaves, ferns, moss
        for (int x = -104; x <= 0; x++)
            for (int z = -54; z <= 54; z++) {
                if (!w.canon(x, z) || w.r.nextInt(70) != 0 || isClear(x, z, 1)) continue;
                int gy = w.ground(x, z);
                if (gy < 60 || !soil(w.type(x, gy, z)) || !w.air(x, gy + 1, z)) continue;
                flora.bush(x, gy + 1, z, 1.2 + w.r.nextDouble());
            }
        plants(P.of("fern", 6, "large_fern[half=lower]", 2, "short_grass", 5, "moss_carpet", 2, "azalea", 1, "flowering_azalea", 1, "bush", 2), 0.3,
                (x, z) -> true);

        spawn = new double[] {PYR_X + 0.5, Y + 1, 0.5, -90};
        flag = new int[] {PYR_X, Y + 10, 0};
        pads.add(new int[] {PYR_X + 8, Y + 1, -18});
        pads.add(new int[] {PYR_X + 8, Y + 1, 18});
        hills.add(new Hill("Cenote", -3, Y + 3, -3, 3, Y + 6, 3));
    }

    // ---------------------------------------------------------------- the pyramid

    /**
     * A step pyramid: four tiers of weathered stone with a carved band at the top of each, the great stair up
     * the east face, a temple on top with the flag inside its open front. Inside the base, the spawn chamber
     * with doors north and south and a stair up through the tiers to the temple.
     */
    void pyramid(int cx, int cz) {
        int y = Y;
        // the plaza round it: worn paving with moss in the joints
        for (int x = cx - 20; x <= cx + 20; x++) for (int z = cz - 24; z <= cz + 24; z++) {
            double n = w.fbm(x * 1.3, z * 1.3, 5, 2);
            boolean paved = Math.abs(x - cx) <= 17 && Math.abs(z - cz) <= 17;
            if (!paved) continue;
            w.set(x, y, z, n > 0.35 ? "moss_block" : n > 0.15 ? "mossy_stone_bricks" : n < -0.4 ? "cracked_stone_bricks" : "stone_bricks");
            for (int dy = 1; dy <= 6; dy++) if (!w.air(x, y + dy, z)) w.set(x, y + dy, z, "air");
        }
        int[] half = {12, 9, 6, 3};
        int yy = y;
        for (int tier = 0; tier < 4; tier++) {
            int h = tier < 3 ? 3 : 0;
            int r = half[tier];
            if (h == 0) break;
            for (int x = cx - r; x <= cx + r; x++) for (int z = cz - r; z <= cz + r; z++) {
                for (int dy = 1; dy <= h; dy++) {
                    boolean face = Math.abs(x - cx) == r || Math.abs(z - cz) == r;
                    String spec;
                    if (!face) spec = "stone_bricks";
                    else if (dy == h) spec = (x + z & 3) == 0 ? "chiseled_stone_bricks" : "polished_andesite";
                    else spec = tier == 0 ? P.of("mossy_cobblestone", 2, "mossy_stone_bricks", 3, "cracked_stone_bricks", 1).pick(w.r)
                            : tier == 1 ? P.of("mossy_stone_bricks", 2, "stone_bricks", 3, "cracked_stone_bricks", 1).pick(w.r)
                            : P.of("tuff_bricks", 3, "mossy_stone_bricks", 1).pick(w.r);
                    w.set(x, yy + dy, z, spec);
                }
            }
            // a cornice round the top of the tier, moss and leaves on its terrace, vines down its face
            for (int x = cx - r - 1; x <= cx + r + 1; x++) for (int z = cz - r - 1; z <= cz + r + 1; z++) {
                boolean ring = Math.abs(x - cx) == r + 1 || Math.abs(z - cz) == r + 1;
                if (!ring || Math.abs(x - cx) == r + 1 && Math.abs(z - cz) == r + 1) continue;
                if (x > cx + r && Math.abs(z - cz) <= 2) continue; // the stair
                String face = Math.abs(x - cx) == r + 1 ? (x > cx ? "west" : "east") : (z > cz ? "north" : "south");
                w.set(x, yy + h, z, (tier == 2 ? "tuff_brick_stairs" : "stone_brick_stairs") + "[facing=" + face + ",half=top]");
                if (w.r.nextInt(4) == 0) {
                    String vf = face.equals("west") ? "west" : face.equals("east") ? "east" : face.equals("north") ? "north" : "south";
                    for (int dy = h - 1; dy >= 1 && dy >= h - 2; dy--) if (w.air(x, yy + dy, z)) w.set(x, yy + dy, z, "vine[" + vf + "=true]");
                }
            }
            if (tier < 2) {
                int nr = half[tier + 1];
                for (int x = cx - r; x <= cx + r; x++) for (int z = cz - r; z <= cz + r; z++) {
                    boolean terraceCell = Math.abs(x - cx) > nr || Math.abs(z - cz) > nr;
                    if (!terraceCell || x > cx + nr && Math.abs(z - cz) <= 2) continue;
                    if (w.r.nextInt(5) == 0) w.set(x, yy + h + 1, z, w.r.nextBoolean() ? "moss_carpet" : "jungle_leaves[persistent=true]");
                }
            }
            yy += h;
        }
        int top = yy; // the platform the temple stands on (y + 9)
        for (int x = cx - 3; x <= cx + 3; x++) for (int z = cz - 3; z <= cz + 3; z++) w.set(x, top, z, "polished_andesite");
        // the temple on top: thick walls, pillars at the front, a stepped roof
        int t0 = top;
        k.walls(cx - 3, cz - 3, cx + 3, cz + 3, t0, 4, Kit.Bands.of(0, "mossy_stone_bricks", 4, "chiseled_stone_bricks"), "chiseled_stone_bricks", 0,
                P.of("polished_andesite"));
        for (int dz = -1; dz <= 1; dz++) for (int dy = 1; dy <= 3; dy++) w.set(cx + 3, t0 + dy, cz + dz, "air");
        for (int s : new int[] {-2, 2}) for (int dy = 1; dy <= 3; dy++) w.set(cx + 4, t0 + dy, cz + s, "mossy_stone_brick_wall");
        for (int dz = -2; dz <= 2; dz++) w.set(cx + 4, t0 + 4, cz + dz, "chiseled_stone_bricks");
        k.hip(cx - 3, cz - 3, cx + 4, cz + 3, t0 + 5, 0, Kit.Roof.of("mossy_stone_bricks"), P.of("mossy_stone_bricks"));
        // the roof comb: a carved crest standing up from the ridge
        int crest = w.top(cx, cz);
        for (int dz = -2; dz <= 2; dz++) for (int dy = 1; dy <= 3; dy++) {
            boolean hole = dy == 2 && Math.abs(dz) == 1;
            if (!hole) w.set(cx, crest + dy, cz + dz, dy == 3 ? "chiseled_tuff" : "tuff_bricks");
        }
        w.set(cx - 2, t0 + 1, cz, "chiseled_stone_bricks");
        w.set(cx - 2, t0 + 2, cz, "TEAM_wall_banner[facing=east]");
        w.set(cx - 2, t0 + 1, cz - 2, "lantern[hanging=false]");
        w.set(cx - 2, t0 + 1, cz + 2, "lantern[hanging=false]");
        // the great stair up the east face
        for (int i = 0; i <= 8; i++) for (int dz = -2; dz <= 2; dz++) {
            int x = cx + 12 + 1 - i, sy = y + 1 + i;
            if (x <= cx + 3) break;
            String spec = Math.abs(dz) == 2 ? "mossy_stone_bricks" : "stone_brick_stairs[facing=west,half=bottom]";
            w.set(x, sy, cz + dz, spec);
            for (int b = sy - 1; b > y; b--) if (w.air(x, b, cz + dz)) w.set(x, b, cz + dz, "stone_bricks");
            for (int h = 1; h <= 3; h++) if (!w.air(x, sy + h, cz + dz) && Math.abs(dz) < 2) w.set(x, sy + h, cz + dz, "air");
        }
        // braziers flanking the foot of the stair (fire in iron, off the floor)
        for (int s : new int[] {-4, 4}) {
            w.set(cx + 14, y + 1, cz + s, "mossy_stone_bricks");
            w.set(cx + 14, y + 2, cz + s, "lantern[hanging=false]");
        }
        // the spawn chamber inside the base, doors out to north and south, a stair up inside
        for (int x = cx - 6; x <= cx + 6; x++) for (int z = cz - 6; z <= cz + 6; z++) {
            boolean wall = Math.abs(x - cx) == 6 || Math.abs(z - cz) == 6;
            w.set(x, y, z, wall ? "stone_bricks" : ((x + z) & 1) == 0 ? "polished_andesite" : "stone_bricks");
            for (int dy = 1; dy <= 5; dy++) w.set(x, y + dy, z, wall ? "stone_bricks" : "air");
        }
        for (int s : new int[] {-1, 1}) {
            // a corridor from the chamber out to the plaza
            for (int z = cz + s * 6; Math.abs(z - cz) <= 13; z += s) for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 1; dy <= 3; dy++) w.set(cx + dx, y + dy, z, "air");
                w.set(cx + dx, y, z, "stone_bricks");
                if (Math.abs(z - cz) <= 12) w.set(cx + dx, y + 4, z, Math.abs(dx) == 1 ? "chiseled_stone_bricks" : "stone_bricks");
            }
            w.set(cx - 1, y + 3, cz + s * 12, "stone_brick_stairs[facing=east,half=top]");
            w.set(cx + 1, y + 3, cz + s * 12, "stone_brick_stairs[facing=west,half=top]");
        }
        for (int[] p : new int[][] {{-5, -5}, {5, -5}, {-5, 5}, {5, 5}}) w.hangLight(cx + p[0], y + 3, cz + p[1], "lantern");
        w.set(cx - 5, y + 3, cz, "TEAM_wall_banner[facing=east]");
        // four carved pillars hold the chamber up; stores against the walls
        for (int[] p : new int[][] {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
            w.set(cx + p[0], y + 1, cz + p[1], "chiseled_stone_bricks");
            for (int dy = 2; dy <= 4; dy++) w.set(cx + p[0], y + dy, cz + p[1], "stone_brick_wall");
            w.set(cx + p[0], y + 5, cz + p[1], "chiseled_stone_bricks");
        }
        for (int dz : new int[] {-5, -4, 4, 5}) w.set(cx + 5, y + 1, cz + dz, dz % 2 == 0 ? "barrel[facing=up]" : "chest[facing=west]");
        w.set(cx + 5, y + 2, cz - 5, "decorated_pot");
        // the inner stair: up the west side of the chamber, then through the tiers to the temple floor
        int sx = cx - 4, sy = y + 1;
        for (int i = 0; i < 9; i++) {
            int z = cz - 4 + i;
            w.set(sx, sy + i, z, "stone_brick_stairs[facing=south,half=bottom]");
            for (int h = 1; h <= 3; h++) w.set(sx, sy + i + h, z, "air");
            if (i > 0) w.set(sx, sy + i - 1, z, "stone_bricks");
        }
        // it comes out on the top terrace beside the temple, under a little arch
        w.set(sx, top + 4, cz + 4, "chiseled_stone_bricks");
        w.set(sx - 1, top + 1, cz + 4, "mossy_stone_brick_wall");
        w.set(sx - 1, top + 2, cz + 4, "mossy_stone_brick_wall");
        w.set(sx - 1, top + 3, cz + 4, "mossy_stone_brick_wall");
        w.set(sx - 1, top + 4, cz + 4, "mossy_stone_bricks");
        keepClear(cx - 22, cz - 26, cx + 22, cz + 26);
    }

    // ---------------------------------------------------------------- the river

    void river() {
        // the old bridge: a stone arch across at z = 0
        for (int dz = -2; dz <= 2; dz++)
            for (int dx = -7; dx <= 7; dx++) {
                int x = (int) Math.round(riverZ(dz)) + dx, z = dz;
                int arch = (int) Math.round(Math.sqrt(Math.max(0, 49 - dx * dx)) * 0.22);
                int deck = Y + arch;
                w.set(x, deck, z, Math.abs(dz) == 2 ? "mossy_stone_bricks" : (x & 1) == 0 ? "stone_bricks" : "cracked_stone_bricks");
                for (int h = 1; h <= 4; h++) w.set(x, deck + h, z, "air");
                if (Math.abs(dz) == 2 && (dx & 1) == 0) w.set(x, deck + 1, z, "mossy_stone_brick_wall");
                // the arch underneath, open in the middle for the water
                if (Math.abs(dx) > 3) for (int yy = deck - 1; yy > Y - 5; yy--) {
                    if (w.type(x, yy, z).isSolid() && !w.type(x, yy, z).name().contains("WATER")) break;
                    w.set(x, yy, z, "stone_bricks");
                }
                else w.set(x, deck - 1, z, "stone_brick_stairs[facing=" + (dx < 0 ? "east" : "west") + ",half=top]");
            }
        // stepping stones over the ford
        for (int i = -3; i <= 3; i++) {
            int x = (int) Math.round(riverZ(-25)) + i, z = -25 + (i & 1);
            w.set(x, Y - 1, z, "mossy_cobblestone");
        }
        // a fallen giant: a jungle trunk lying across the river, roots on one bank, a broken crown on the other
        int fz = 24;
        for (int dx = -10; dx <= 9; dx++) {
            int x = (int) Math.round(riverZ(fz)) + dx;
            for (int s = 0; s <= 1; s++) for (int h = -1; h <= 0; h++) w.set(x, Y + h, fz + s, "jungle_wood");
            for (int s = 0; s <= 1; s++) for (int h = 1; h <= 3; h++) if (!w.air(x, Y + h, fz + s) && dx > -9) w.set(x, Y + h, fz + s, "air");
            if (w.r.nextInt(4) == 0) w.set(x, Y + 1, fz + w.r.nextInt(2), "moss_carpet");
        }
        int rx = (int) Math.round(riverZ(fz)) - 11;
        for (int[] d : new int[][] {{0, -1}, {0, 2}, {-1, 0}, {-1, 1}, {0, 0}, {0, 1}}) for (int h = 0; h <= 2; h++) w.set(rx + d[0], Y + h, fz + d[1], "jungle_wood");
        w.set(rx, Y + 3, fz, "moss_carpet");
        keepClear(RIVER_X - 9, -6, RIVER_X + 9, 6);
        keepClear(RIVER_X - 14, 20, RIVER_X + 12, 28);
    }

    // ---------------------------------------------------------------- the middle

    /** The ruin on the pillar in the cenote: a broken colonnade round an open floor, four rope bridges. */
    void cenoteRuin() {
        int y = Y + 2;
        for (int x = -7; x <= 7; x++) for (int z = -7; z <= 7; z++) {
            if (!w.canon(x, z)) continue;
            double d = Math.hypot(x, z);
            if (d > 6.5) continue;
            c(x, y, z, d > 5.5 ? "mossy_stone_bricks" : d < 1.5 ? "chiseled_stone_bricks" : ((x + z) & 1) == 0 ? "stone_bricks" : "mossy_stone_bricks");
            for (int h = 1; h <= 8; h++) c(x, y + h, z, "air");
        }
        // columns round the edge, some whole with a lintel, some broken
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4 + Math.PI / 8;
            int x = (int) Math.round(Math.cos(a) * 5.4), z = (int) Math.round(Math.sin(a) * 5.4);
            if (!w.canon(x, z)) continue;
            int h = i % 3 == 0 ? 2 : 4;
            c(x, y + 1, z, "chiseled_stone_bricks");
            for (int dy = 2; dy <= h; dy++) c(x, y + dy, z, "mossy_stone_brick_wall");
            if (h == 4) c(x, y + 5, z, "chiseled_stone_bricks");
        }
        // the rope bridges: from the west rim and from the north-west rim
        String[] b = {"jungle_planks", "jungle_slab", "stripped_jungle_wood", "jungle_fence", "jungle_log", "lantern"};
        k.bridge(-26, 0, -4, 0, 3, b[0], b[1], b[2], b[3], b[4], b[5]);
        k.bridge(-12, -22, -2, -4, 3, b[0], b[1], b[2], b[3], b[4], b[5]);
        // the rim: a ruined wall round the sinkhole with gaps
        for (int x = -24; x <= 24; x++) for (int z = -24; z <= 24; z++) {
            if (!w.canon(x, z)) continue;
            double cd = cenote(x, z);
            if (cd < 17 || cd > 18.4) continue;
            if (Math.abs(z) <= 3 && x < 0 || Math.abs(x - (-7)) < 4 && z < -10) continue;
            int gy = w.ground(x, z);
            if (gy < 60) continue;
            int h = 1 + (int) Math.round((w.n(x * 2, z * 2, 3) + 1) * 1.2);
            for (int dy = 1; dy <= h; dy++) c(x, gy + dy, z, w.r.nextInt(3) == 0 ? "mossy_cobblestone" : "mossy_stone_bricks");
        }
        keepClear(-26, -26, 26, 26);
    }

    /** Ruins in the jungle on the near side: a roofless shrine of columns, an archway, broken walls. */
    void ruins() {
        // the shrine
        int sx = -20, sz = -32, gy = w.ground(sx, sz);
        if (gy > 60) {
            for (int dx = -5; dx <= 5; dx++) for (int dz = -4; dz <= 4; dz++) {
                w.set(sx + dx, gy, sz + dz, w.r.nextInt(3) == 0 ? "moss_block" : "mossy_stone_bricks");
                for (int h = 1; h <= 6; h++) if (!w.air(sx + dx, gy + h, sz + dz)) w.set(sx + dx, gy + h, sz + dz, "air");
            }
            for (int dx = -4; dx <= 4; dx += 2) for (int dz : new int[] {-3, 3}) {
                int h = 2 + w.r.nextInt(3);
                for (int dy = 1; dy <= h; dy++) w.set(sx + dx, gy + dy, sz + dz, dy == 1 ? "chiseled_stone_bricks" : "mossy_stone_brick_wall");
                if (h == 4) w.set(sx + dx, gy + 5, sz + dz, "mossy_stone_bricks");
            }
            for (int dx = -4; dx <= 0; dx++) w.set(sx + dx, gy + 5, sz - 3, "mossy_stone_bricks");
            w.set(sx - 4, gy + 5, sz - 3, "mossy_stone_bricks");
            for (int dx = -4; dx <= 0; dx += 2) for (int h = 2; h <= 4; h++) w.set(sx + dx, gy + h, sz - 3, "mossy_stone_brick_wall");
            w.set(sx + 3, gy + 1, sz, "chiseled_stone_bricks");
            w.set(sx + 3, gy + 2, sz, "decorated_pot");
            keepClear(sx - 6, sz - 5, sx + 6, sz + 5);
        }
        // the archway, half swallowed by the jungle
        int ax = -22, az = 30, ay = w.ground(ax, az);
        if (ay > 60) {
            for (int dz = -4; dz <= 4; dz++) {
                int h = Math.abs(dz) >= 3 ? 6 : Math.abs(dz) == 2 ? 6 : 0;
                if (h > 0) for (int dy = 1; dy <= h; dy++) w.set(ax, ay + dy, az + dz, w.r.nextInt(3) == 0 ? "mossy_stone_bricks" : "stone_bricks");
            }
            for (int dz = -2; dz <= 2; dz++) w.set(ax, ay + 6, az + dz, "mossy_stone_bricks");
            w.set(ax, ay + 5, az - 1, "stone_brick_stairs[facing=south,half=top]");
            w.set(ax, ay + 5, az + 1, "stone_brick_stairs[facing=north,half=top]");
            w.set(ax, ay + 7, az, "chiseled_stone_bricks");
            for (int dz = -4; dz <= 4; dz++) if (w.air(ax - 1, ay + 4, az + dz) && Math.abs(dz) >= 3) w.set(ax - 1, ay + 4, az + dz, "vine[east=true]");
            keepClear(ax - 3, az - 6, ax + 3, az + 6);
        }
        // broken walls here and there for cover
        for (int[] p : new int[][] {{-58, -12}, {-60, 14}, {-30, -6}, {-28, 12}, {-12, 34}, {-50, -36}}) {
            int x0 = p[0], z0 = p[1];
            boolean alongX = w.r.nextBoolean();
            for (int i = 0; i < 6; i++) {
                int x = alongX ? x0 + i : x0, z = alongX ? z0 : z0 + i;
                int gy2 = w.ground(x, z);
                if (gy2 < 60) continue;
                int h = 1 + (int) Math.round((Math.sin(i * 1.3) + 1) * 1.2);
                for (int dy = 1; dy <= h; dy++) w.set(x, gy2 + dy, z, w.r.nextInt(3) == 0 ? "mossy_cobblestone" : "mossy_stone_bricks");
            }
            keepClear(x0 - 1, z0 - 1, x0 + 7, z0 + 7);
        }
    }
}
