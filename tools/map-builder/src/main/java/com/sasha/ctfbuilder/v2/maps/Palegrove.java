package com.sasha.ctfbuilder.v2.maps;

import com.sasha.ctfbuilder.v2.Arena;
import com.sasha.ctfbuilder.v2.Kit;
import com.sasha.ctfbuilder.v2.Land;
import com.sasha.ctfbuilder.v2.Theme;
import com.sasha.ctfbuilder.v2.W.P;
import org.bukkit.World;

/**
 * Palegrove: a pale, haunted country of separate islands joined by old stonework. Each team's abbey stands
 * on its home island (spawn in the nave, the flag in the cloister). Two ways out: the main road over the
 * graveyard island and along the aqueduct, high above the void, into the ruined junction in the middle; or
 * the long causeway round the flank by the mill pond. Every route passes the junction — the hill — so the
 * middle is everything: hold it and you choose which way to attack.
 */
public final class Palegrove extends Arena {

    static final int Y = 70, AQ = 78;

    record Isle(int x, int z, double rx, double rz, double angle) {}

    static final Isle HOME = new Isle(-70, 0, 25, 25, 0.2);
    static final Isle GRAVE = new Isle(-36, -15, 12, 10, 0.5);
    static final Isle POND = new Isle(-30, 30, 14, 11, -0.3);
    static final Isle MID = new Isle(0, 0, 15, 13, 0.6);

    public Palegrove(World world) {
        super(world, "palegrove", false, Theme.get("palegrove"));
        name = "Palegrove";
        icon = "PALE_OAK_SAPLING";
        minutes = 15;
        kothPoints = 120;
        reach = 104;
        low = 15;
        high = 140;
        voidY = 15;
        spectator = new double[] {0.5, 108, 0.5, -90, 45};
    }

    double inside(Isle i, int x, int z) {
        return Land.ellipse(x, z, i.x, i.z, i.rx, i.rz, i.angle) + w.fbm(x * 0.8 + i.x, z * 0.8, 9, 2) * 0.12;
    }

    double height(int x, int z) {
        double best = -1;
        for (Isle i : new Isle[] {HOME, GRAVE, POND, MID}) best = Math.max(best, inside(i, x, z));
        if (best < 0) return Double.NaN;
        double h = Y + w.fbm(x + 300, z - 120, 16, 3) * 2.2 * Land.smooth(best / 0.3) + Math.round(Land.smooth(best / 0.12));
        if (inside(HOME, x, z) > 0.25) h = Y + 1;   // the abbey's level ground
        if (inside(MID, x, z) > 0.35) h = Y + 1;
        return h - 1;
    }

    String surface(int x, int z, int y, int steep, double edge) {
        double n = w.fbm(x + 913, z - 377, 9, 2);
        if (steep >= 3) return w.r.nextBoolean() ? "cobbled_deepslate" : "tuff";
        if (n > 0.3) return "pale_moss_block";
        if (n < -0.4) return "podzol";
        if (n < -0.2) return "coarse_dirt";
        return "grass_block";
    }

    @Override
    protected void build() {
        Land land = new Land(w, t, -100, 100, -60, 60);
        land.top = this::height;
        land.surface = this::surface;
        land.depth = 24;
        land.bands = new String[] {"deepslate", "tuff", "cobbled_deepslate", "stone", "tuff", "deepslate", "andesite", "cobbled_deepslate"};
        land.speck = P.of("deepslate_coal_ore", 1, "gravel", 2, "mossy_cobblestone", 1);
        land.under = P.of("deepslate", 3, "tuff", 2, "cobbled_deepslate", 2);
        land.build();

        abbey(-74, 0);
        graveyard();
        millPond();
        junction();
        aqueduct(-27, -11, -12, -4);
        String[] cw = {"cobbled_deepslate", "cobbled_deepslate_slab", "deepslate_bricks", "cobbled_deepslate_wall", "deepslate_brick_wall", "soul_lantern"};
        k.bridge(-54, -9, -44, -13, 4, cw[0], cw[1], cw[2], cw[3], cw[4], cw[5]);
        k.bridge(-56, 14, -38, 26, 3, cw[0], cw[1], cw[2], cw[3], cw[4], cw[5]);
        k.bridge(-22, 24, -10, 8, 3, cw[0], cw[1], cw[2], cw[3], cw[4], cw[5]);

        trees(new Theme.Tree[] {Theme.Tree.PALE, Theme.Tree.DEAD}, new int[] {5, 1}, 0.02, 8, 2, (x, z) -> true);
        plants(P.of("pale_moss_carpet", 4, "short_grass", 4, "fern", 2, "open_eyeblossom", 1, "closed_eyeblossom", 2, "bush", 1), 0.22, (x, z) -> true);

        spawn = new double[] {-77.5, Y + 1, 0.5, -90};
        flag = new int[] {-71, Y + 1, -14};
        pads.add(new int[] {-58, Y + 1, 11});
        pads.add(new int[] {-82, Y + 1, 13});
        hills.add(new Hill("Junction", -3, Y + 1, -3, 3, Y + 4, 3));
    }

    // ---------------------------------------------------------------- the abbey

    /**
     * The abbey: a tall nave of deepslate with buttresses and pointed windows under a steep roof, the bell
     * tower at its west end with a spire, and the cloister on its north side round a garth.
     */
    void abbey(int cx, int cz) {
        int y = Y;
        int x0 = cx - 11, x1 = cx + 11, z0 = cz - 5, z1 = cz + 5;
        level(x0 - 2, z0 - 2, x1 + 3, z1 + 2, y, P.of("deepslate_tiles", 2, "cobbled_deepslate", 1), P.of("cobbled_deepslate"));
        // the nave: walls 9 high, banded dark at the base
        k.walls(x0, z0, x1, z1, y, 9, Kit.Bands.of(0, "polished_deepslate", 1, P.of("deepslate_bricks", 4, "cracked_deepslate_bricks", 1), 8, "polished_deepslate"),
                null, 0, P.of("deepslate_tiles", 2, "polished_deepslate", 1));
        // buttresses between the windows, stepping in as they rise
        for (int x = x0 + 2; x <= x1 - 2; x += 4) for (int z : new int[] {z0 - 1, z1 + 1}) {
            int out = z < cz ? -1 : 1;
            for (int dy = 1; dy <= 7; dy++) w.set(x, y + dy, z, "deepslate_bricks");
            for (int dy = 1; dy <= 4; dy++) w.set(x, y + dy, z + out, "deepslate_bricks");
            w.set(x, y + 5, z + out, "deepslate_brick_stairs[facing=" + (out < 0 ? "south" : "north") + ",half=bottom]");
            w.set(x, y + 8, z, "deepslate_brick_stairs[facing=" + (out < 0 ? "south" : "north") + ",half=bottom]");
        }
        // pointed windows between the buttresses
        for (int x = x0 + 4; x <= x1 - 4; x += 4) for (int z : new int[] {z0, z1}) {
            for (int dy = 3; dy <= 6; dy++) w.set(x, y + dy, z, "gray_stained_glass_pane");
            w.set(x, y + 7, z, "gray_stained_glass_pane");
        }
        // a rose window in the east gable, the great door under it
        int ry = y + 12;
        for (int dz = -2; dz <= 2; dz++) for (int dy = -2; dy <= 2; dy++) if (Math.hypot(dz, dy) <= 2.3) w.set(x1, ry + dy, cz + dz, Math.hypot(dz, dy) < 1 ? "purple_stained_glass_pane" : "gray_stained_glass_pane");
        // the roof: steep, dark tiles
        k.gable(x0, z0, x1, z1, y + 10, true, 1, Kit.Roof.of("deepslate_tiles"), P.of("deepslate_bricks"));
        for (int dz = -1; dz <= 1; dz++) for (int dy = 1; dy <= 4; dy++) w.set(x1, y + dy, cz + dz, "air");
        w.set(x1, y + 5, cz, "chiseled_deepslate");
        w.set(x1, y + 4, cz - 1, "deepslate_brick_stairs[facing=south,half=top]");
        w.set(x1, y + 4, cz + 1, "deepslate_brick_stairs[facing=north,half=top]");
        for (int dz = -2; dz <= 2; dz++) w.set(x1 + 1, y, cz + dz, "deepslate_tile_stairs[facing=west,half=bottom]");
        for (int s : new int[] {-3, 3}) {
            w.set(x1 + 1, y + 4, cz + s, "pale_oak_fence");
            w.set(x1 + 1, y + 3, cz + s, "soul_lantern[hanging=true]");
        }
        // inside: pews in rows, an aisle, the altar at the west under the tower arch, chandeliers
        for (int x = x0 + 5; x <= x1 - 3; x += 2) for (int z = z0 + 1; z <= z1 - 1; z++) {
            if (Math.abs(z - cz) <= 1) continue;
            w.set(x, y + 1, z, "pale_oak_stairs[facing=east,half=bottom]");
        }
        for (int x = x0 + 1; x <= x1 - 1; x++) w.set(x, y + 1, cz, x < x0 + 4 ? "air" : "red_carpet");
        w.set(x0 + 2, y + 1, cz, "chiseled_deepslate");
        w.set(x0 + 2, y + 2, cz, "candle[candles=4,lit=true]");
        w.set(x0 - 5, y + 4, cz, "TEAM_wall_banner[facing=east]");
        for (int x = x0 + 3; x <= x1 - 3; x += 6) w.hangLight(x, y + 6, cz, "soul_lantern");
        for (int x = x0 + 3; x <= x1 - 3; x += 6) for (int z = z0 + 1; z <= z1 - 1; z++) w.set(x, y + 9, z, "stripped_pale_oak_log[axis=z]");
        // the bell tower at the west end, over the altar
        int tx0 = x0 - 6, tx1 = x0, tz0 = cz - 3, tz1 = cz + 3;
        k.walls(tx0, tz0, tx1, tz1, y, 22, Kit.Bands.of(0, "polished_deepslate", 1, P.of("deepslate_bricks", 4, "cracked_deepslate_bricks", 1),
                8, "polished_deepslate", 9, P.of("deepslate_bricks", 5, "tuff_bricks", 1), 22, "polished_deepslate"), "deepslate_tiles", 0, P.of("deepslate_tiles"));
        for (int dz = -1; dz <= 1; dz++) for (int dy = 1; dy <= 5; dy++) w.set(tx1, y + dy, cz + dz, "air"); // the arch into the nave
        for (int dy : new int[] {8, 16}) for (int x = tx0 + 1; x < tx1; x++) for (int z = tz0 + 1; z < tz1; z++) w.set(x, y + dy, z, "pale_oak_planks");
        // the belfry: open arches on all four sides, the bell hanging in the middle
        for (int dy = 17; dy <= 20; dy++) for (int o = -1; o <= 1; o++) {
            w.set(tx0, y + dy, cz + o, dy == 20 && o != 0 ? "deepslate_brick_stairs[facing=" + (o < 0 ? "south" : "north") + ",half=top]" : dy == 20 ? "polished_deepslate" : "air");
            w.set(tx1, y + dy, cz + o, dy == 20 && o != 0 ? "deepslate_brick_stairs[facing=" + (o < 0 ? "south" : "north") + ",half=top]" : dy == 20 ? "polished_deepslate" : "air");
            int bx = (tx0 + tx1) / 2;
            w.set(bx + o, y + dy, tz0, dy == 20 && o != 0 ? "deepslate_brick_stairs[facing=" + (o < 0 ? "east" : "west") + ",half=top]" : dy == 20 ? "polished_deepslate" : "air");
            w.set(bx + o, y + dy, tz1, dy == 20 && o != 0 ? "deepslate_brick_stairs[facing=" + (o < 0 ? "east" : "west") + ",half=top]" : dy == 20 ? "polished_deepslate" : "air");
        }
        int bx = (tx0 + tx1) / 2;
        for (int z = tz0 + 1; z < tz1; z++) w.set(bx, y + 21, z, "stripped_pale_oak_log[axis=z]");
        w.set(bx, y + 20, cz, "bell[attachment=ceiling,facing=north]");
        // the spire: a steep stone pyramid
        for (int lvl = 0; lvl <= 9; lvl++) {
            int r = 3 - lvl / 3;
            if (r < 0) break;
            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                boolean edge = Math.abs(dx) == r || Math.abs(dz) == r;
                if (!edge && r > 0) continue;
                w.set(bx + dx, y + 23 + lvl, cz + dz, "deepslate_tiles");
            }
        }
        w.set(bx, y + 33, cz, "deepslate_tile_wall");
        w.set(bx, y + 34, cz, "deepslate_tile_wall");
        // a ladder up the tower to the belfry
        for (int dy = 1; dy <= 19; dy++) w.set(tx0 + 1, y + dy, tz1 - 1, "ladder[facing=east]");
        for (int dy : new int[] {8, 16}) w.set(tx0 + 1, y + dy, tz1 - 1, "ladder[facing=east]");
        // the cloister on the north side: a covered walk on columns round a garth where the flag stands
        int gx0 = cx - 4, gx1 = cx + 10, gz0 = z0 - 16, gz1 = z0 - 2;
        for (int x = gx0; x <= gx1; x++) for (int z = gz0; z <= gz1; z++) {
            boolean walk = x <= gx0 + 2 || x >= gx1 - 2 || z <= gz0 + 2 || z >= gz1 - 2;
            int gy = y;
            for (int dy = 1; dy <= 8; dy++) if (!w.air(x, gy + dy, z)) w.set(x, gy + dy, z, "air");
            w.set(x, gy, z, walk ? (((x + z) & 1) == 0 ? "polished_deepslate" : "deepslate_tiles") : "grass_block");
            for (int yy = gy - 1; yy > gy - 8 && !w.type(x, yy, z).isSolid(); yy--) w.set(x, yy, z, "cobbled_deepslate");
        }
        // the arcade: columns on the inner edge of the walk, arches between, a lean-to roof
        for (int x = gx0; x <= gx1; x++) for (int z = gz0; z <= gz1; z++) {
            boolean outer = x == gx0 || x == gx1 || z == gz0 || z == gz1;
            boolean inner = (x == gx0 + 3 || x == gx1 - 3) && z >= gz0 + 3 && z <= gz1 - 3 || (z == gz0 + 3 || z == gz1 - 3) && x >= gx0 + 3 && x <= gx1 - 3;
            if (outer && z != gz1) {
                for (int dy = 1; dy <= 4; dy++) w.set(x, y + dy, z, dy == 1 ? "polished_deepslate" : "deepslate_bricks");
            }
            if (inner) {
                boolean column = ((x - gx0) % 3 == 0) && ((z - gz0) % 3 == 0);
                if (column) {
                    w.set(x, y + 1, z, "chiseled_deepslate");
                    w.set(x, y + 2, z, "deepslate_brick_wall");
                    w.set(x, y + 3, z, "deepslate_brick_wall");
                }
                w.set(x, y + 4, z, column ? "chiseled_deepslate" : "polished_deepslate");
            }
            boolean walk = x <= gx0 + 3 || x >= gx1 - 3 || z <= gz0 + 3 || z >= gz1 - 3;
            if (walk) w.set(x, y + 5, z, "deepslate_tile_slab[type=bottom]");
        }
        // the way in from the south-east corner and out to the island
        for (int dy = 1; dy <= 3; dy++) for (int o = 0; o <= 1; o++) w.set(gx1, y + dy, gz0 + 7 + o, "air");
        // the garth: a well in the middle, flowers
        int fx = (gx0 + gx1) / 2, fz = (gz0 + gz1) / 2;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) w.set(fx + dx, y, fz + dz, dx == 0 && dz == 0 ? "polished_deepslate" : "deepslate_tiles");
        for (int i = 0; i < 18; i++) {
            int px = gx0 + 4 + w.r.nextInt(gx1 - gx0 - 7), pz = gz0 + 4 + w.r.nextInt(gz1 - gz0 - 7);
            if (Math.abs(px - fx) <= 2 && Math.abs(pz - fz) <= 2) continue;
            if (w.type(px, y, pz) == org.bukkit.Material.GRASS_BLOCK && w.air(px, y + 1, pz)) w.set(px, y + 1, pz, w.r.nextBoolean() ? "lily_of_the_valley" : "open_eyeblossom");
        }
        keepClear(gx0 - 1, gz0 - 1, gx1 + 1, z1 + 3);
        keepClear(tx0 - 2, tz0 - 2, x1 + 3, z1 + 3);
    }

    // ---------------------------------------------------------------- the islands on the way

    /** The graveyard: rows of headstones inside an iron fence, a mausoleum, dead trees, a lych-gate. */
    void graveyard() {
        int cx = GRAVE.x, cz = GRAVE.z, y = w.ground(cx, cz);
        if (y < 60) return;
        for (int dx = -7; dx <= 7; dx++) for (int dz = -6; dz <= 6; dz++) {
            int x = cx + dx, z = cz + dz;
            int gy = w.ground(x, z);
            if (gy < 60) continue;
            boolean fence = Math.abs(dx) == 7 || Math.abs(dz) == 6;
            boolean gate = Math.abs(dz) <= 1 && Math.abs(dx) == 7;
            if (fence && !gate) {
                w.set(x, gy + 1, z, (Math.abs(dx) == 7 && Math.abs(dz) == 6) ? "deepslate_brick_wall" : "iron_bars");
                if (Math.abs(dx) == 7 && Math.abs(dz) == 6) w.set(x, gy + 2, z, "soul_lantern[hanging=false]");
            } else if (!fence && dx % 3 == 0 && Math.abs(dz) >= 2 && dx < 4) {
                // a headstone, its grave a mound of coarse dirt
                w.set(x, gy + 1, z, w.r.nextBoolean() ? "cobblestone_wall" : "stone_brick_wall");
                w.set(x + 1, gy, z, "coarse_dirt");
            }
        }
        // the mausoleum at the east end
        int mx = cx + 4, mz = cz, my = y;
        k.walls(mx - 2, mz - 2, mx + 2, mz + 2, my, 4, Kit.Bands.of(0, "polished_deepslate", 1, "deepslate_bricks"), "chiseled_deepslate", 0, P.of("deepslate_tiles"));
        k.gable(mx - 2, mz - 2, mx + 2, mz + 2, my + 5, true, 1, Kit.Roof.of("deepslate_tiles"), P.of("deepslate_bricks"));
        w.set(mx - 2, my + 1, mz, "iron_bars");
        w.set(mx - 2, my + 2, mz, "iron_bars");
        w.set(mx, my + 1, mz, "chiseled_deepslate");
        w.set(mx, my + 2, mz, "candle[candles=2,lit=true]");
        keepClear(cx - 8, cz - 7, cx + 8, cz + 7);
    }

    /** The mill pond on the flank: a dark pond, a watermill with its wheel in the water, a cottage. */
    void millPond() {
        int cx = POND.x, cz = POND.z, y = w.ground(cx, cz);
        if (y < 60) return;
        for (int dx = -5; dx <= 4; dx++) for (int dz = -4; dz <= 3; dz++) {
            double d = Math.hypot(dx / 5.0, dz / 4.0) + w.n(cx + dx, cz + dz, 2) * 0.1;
            if (d > 1) continue;
            int x = cx + dx, z = cz + dz;
            for (int dy = 1; dy <= 5; dy++) w.set(x, y + dy, z, "air");
            w.set(x, y, z, "water");
            w.set(x, y - 1, z, d < 0.6 ? "water" : "mud");
            w.set(x, y - 2, z, "mud");
            if (w.r.nextInt(6) == 0) w.set(x, y + 1, z, "lily_pad");
        }
        // the mill on the pond's east bank
        int mx = cx + 8, mz = cz - 1, my = w.ground(mx, mz);
        if (my < 60) return;
        level(mx - 3, mz - 3, mx + 3, mz + 3, my, P.of("cobbled_deepslate"), P.of("cobbled_deepslate"));
        k.walls(mx - 2, mz - 3, mx + 3, mz + 3, my, 5, Kit.Bands.of(0, "cobbled_deepslate", 2, "stripped_pale_oak_wood", 3, "pale_oak_planks"), "pale_oak_log", 3,
                P.of("pale_oak_planks"));
        k.gable(mx - 2, mz - 3, mx + 3, mz + 3, my + 6, false, 1, Kit.Roof.of("dark_oak_planks"), P.of("pale_oak_planks"));
        k.doorway(mx + 3, my, mz, "east", 1, "pale_oak_door", "pale_oak_stairs", "pale_oak_fence", "soul_lantern");
        k.window(mx, my + 2, mz - 3, "north", 1, 2, "glass_pane", "pale_oak_stairs", "pale_oak_trapdoor");
        // the wheel on the pond side (west wall), in the water
        int wx = mx - 3;
        for (int a = 0; a < 360; a += 15) {
            double r = Math.toRadians(a);
            int dz = (int) Math.round(Math.cos(r) * 3), dy = (int) Math.round(Math.sin(r) * 3);
            w.set(wx, my + 2 + dy, mz + dz, "stripped_dark_oak_log[axis=x]");
        }
        for (int i = -2; i <= 2; i++) {
            w.set(wx, my + 2, mz + i, "dark_oak_planks");
            w.set(wx, my + 2 + i, mz, "dark_oak_planks");
        }
        w.set(wx + 1, my + 2, mz, "stripped_dark_oak_log[axis=x]");
        keepClear(mx - 6, mz - 5, mx + 5, mz + 5);
    }

    // ---------------------------------------------------------------- the middle

    /** The junction: an open court in the ruins of a crossing, broken arches round it, the aqueduct's end. */
    void junction() {
        int y = Y;
        for (int x = -9; x <= 9; x++) for (int z = -9; z <= 9; z++) {
            if (!w.canon(x, z)) continue;
            double d = Math.max(Math.abs(x), Math.abs(z)) + Math.abs(Math.abs(x) - Math.abs(z)) * 0.0;
            if (Math.hypot(x, z) > 9.4) continue;
            for (int dy = 1; dy <= 8; dy++) c(x, y + dy, z, "air");
            c(x, y, z, Math.hypot(x, z) < 1.5 ? "chiseled_deepslate" : ((x + z) & 1) == 0 ? "deepslate_tiles" : "polished_deepslate");
            for (int yy = y - 1; yy > y - 6 && !w.type(x, yy, z).isSolid(); yy--) c(x, yy, z, "cobbled_deepslate");
        }
        // four corner piers with a fallen arch between two of them
        for (int[] p : new int[][] {{-7, -7}, {7, -7}, {-7, 7}, {7, 7}}) {
            if (!w.canon(p[0], p[1])) continue;
            int h = p[0] < 0 && p[1] < 0 ? 8 : 5;
            for (int dx = 0; dx <= 1; dx++) for (int dz = 0; dz <= 1; dz++) for (int dy = 1; dy <= h; dy++)
                c(p[0] + dx * Integer.signum(p[0]), y + dy, p[1] + dz * Integer.signum(p[1]), dy == 1 ? "polished_deepslate" : "deepslate_bricks");
        }
        for (int x = -7; x <= 0; x++) c(x, y + 8, -7, x == -7 ? "deepslate_bricks" : x < -3 ? "deepslate_brick_slab[type=top]" : "air");
        keepClear(-10, -10, 10, 10);
    }

    /**
     * The aqueduct from the graveyard island to the junction: a channel deck on round arches over the void,
     * stairs up at the graveyard end, down at the junction.
     */
    void aqueduct(int ax, int az, int bx, int bz) {
        double len = Math.hypot(bx - ax, bz - az);
        double ux = (bx - ax) / len, uz = (bz - az) / len;
        double nx = -uz, nz = ux;
        for (double s = -6; s <= len + 6; s += 0.4) {
            for (double o = -2; o <= 2; o += 0.4) {
                int x = (int) Math.round(ax + ux * s + nx * o), z = (int) Math.round(az + uz * s + nz * o);
                if (!w.canon(x, z)) continue;
                boolean side = Math.abs(o) > 1.6;
                int deck = AQ;
                // the ramps at both ends, in steps
                String up = null;
                if (s < 2) { deck = Math.max(Y, AQ - (int) Math.round(2 - s)); up = face(ux, uz); }
                if (s > len - 2) { deck = Math.max(Y, AQ - (int) Math.round(s - (len - 2))); up = face(-ux, -uz); }
                w.set(x, deck, z, side ? "deepslate_bricks" : up != null && deck < AQ && deck > Y ? "polished_deepslate_stairs[facing=" + up + ",half=bottom]" : "polished_deepslate");
                if (up != null && deck > Y) for (int yy = deck - 1; yy >= Y && !w.type(x, yy, z).isSolid(); yy--) w.set(x, yy, z, "deepslate_bricks");
                if (side) w.set(x, deck + 1, z, "deepslate_brick_wall");
                for (int h = side ? 2 : 1; h <= 4; h++) if (!w.air(x, deck + h, z)) w.set(x, deck + h, z, "air");
                // the arches underneath: piers every 6 blocks, round arches between
                double phase = ((s % 6) + 6) % 6;
                int archTop = deck - 1;
                int depth = (int) Math.round(Math.sqrt(Math.max(0, 9 - (phase - 3) * (phase - 3))) * 1.2);
                for (int yy = deck - 1; yy >= deck - 1 - Math.max(1, 3 - depth); yy--) {
                    if (w.type(x, yy, z).isSolid() && w.type(x, yy, z) != org.bukkit.Material.DEEPSLATE_BRICKS) break;
                    w.set(x, yy, z, "deepslate_bricks");
                }
                if (phase < 0.8 || phase > 5.2) {
                    // a pier down to the island, or a hanging pillar into the void
                    for (int yy = deck - 1; yy > deck - 18; yy--) {
                        if (w.type(x, yy, z).isSolid() && w.type(x, yy, z) != org.bukkit.Material.DEEPSLATE_BRICKS) break;
                        w.set(x, yy, z, yy < deck - 14 ? "deepslate_brick_wall" : "deepslate_bricks");
                    }
                }
            }
        }
        keepClear(ax - 8, az - 8, bx + 4, bz + 4);
    }

    static String face(double ux, double uz) {
        return Math.abs(ux) >= Math.abs(uz) ? (ux > 0 ? "east" : "west") : (uz > 0 ? "south" : "north");
    }
}
