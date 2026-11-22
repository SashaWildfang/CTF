package com.sasha.ctfbuilder.v2.maps;

import com.sasha.ctfbuilder.v2.Arena;
import com.sasha.ctfbuilder.v2.Kit;
import com.sasha.ctfbuilder.v2.Land;
import com.sasha.ctfbuilder.v2.Theme;
import com.sasha.ctfbuilder.v2.W.P;
import org.bukkit.World;

/**
 * Sakura Isles: temple gardens in three lanes. Each team's temple compound stands on a walled stone terrace;
 * from its three gates run three long islands side by side with chasms between them: the Grand Approach in
 * the middle (a straight stone way under torii gates and cherry trees — quick but open) and a garden lane
 * either side, each half bamboo grove with a stream and half koi-pond garden with a tea house. Red arched
 * bridges cross the chasms. King of the Hill: three points across the middle — the North Shrine, the Moon
 * Stage on the Approach, the South Shrine.
 */
public final class Sakura extends Arena {

    static final int Y = 68, TERRACE = 72, LANE_N = -26, LANE_S = 26;

    public Sakura(World world) {
        super(world, "sakura", false, Theme.get("sakura"));
        name = "Sakura Isles";
        icon = "CHERRY_SAPLING";
        minutes = 15;
        kothPoints = 250;
        reach = 112;
        low = 20;
        high = 130;
        voidY = 20;
        spectator = new double[] {0.5, 100, 0.5, -90, 40};
    }

    // ---------------------------------------------------------------- the land

    double lane(int x, int z, int laneZ, double r) {
        return 1 - Land.segment(x, z + w.fbm(x * 0.5, laneZ, 14, 2) * 3, -66, laneZ, 66, laneZ) / r;
    }

    double terrace(int x, int z) {
        // the temple's terrace: a rounded rectangle at the west end
        double dx = Math.max(0, Math.abs(x + 78) - 13), dz = Math.max(0, Math.abs(z) - 30);
        return 1 - (Math.hypot(dx, dz) + w.fbm(x, z, 9, 2) * 1.2) / 5.0;
    }

    double height(int x, int z) {
        double t2 = terrace(x, z);
        double n = lane(x, z, LANE_N, 10), m = lane(x, z, 0, 8), s = lane(x, z, LANE_S, 10);
        double best = Math.max(Math.max(n, m), Math.max(s, t2));
        if (best < 0) return Double.NaN;
        if (t2 >= 0 && t2 >= best - 0.001) return TERRACE;
        double h = Y + w.fbm(x + 300, z - 120, 16, 3) * 1.4 * Land.smooth(best / 0.4);
        // the bamboo side of each garden lane climbs in terraces
        if (n == best && x < -6) h += Math.floor(Land.smooth((n - 0.1) / 0.5) * 3);
        if (s == best && x < -6) h -= 0.5;
        // ramps up to the terrace
        double near = Land.smooth((x + 66) / -8.0);
        h = h * (1 - near) + TERRACE * near;
        return h;
    }

    String surface(int x, int z, int y, int steep, double edge) {
        double n = w.fbm(x + 913, z - 377, 10, 2);
        if (steep >= 3) return w.r.nextBoolean() ? "mossy_cobblestone" : "stone";
        if (edge < 1.5) return "coarse_dirt";
        if (n > 0.4) return "moss_block";
        if (n < -0.45) return "podzol";
        return "grass_block";
    }

    @Override
    protected void build() {
        Land land = new Land(w, t, -100, 100, -44, 44);
        land.top = this::height;
        land.surface = this::surface;
        land.depth = 26;
        land.bands = new String[] {"stone", "andesite", "diorite", "stone", "tuff", "andesite", "stone", "cobblestone"};
        land.speck = P.of("mossy_cobblestone", 2, "gravel", 1, "coal_ore", 1);
        land.build();

        temple();
        approach();
        bambooGrove();
        koiGarden();
        bridges();
        stage();
        shrine(0, LANE_N);
        shrine(0, LANE_S);

        trees(new Theme.Tree[] {Theme.Tree.CHERRY, Theme.Tree.AZALEA}, new int[] {5, 1}, 0.012, 9, 2,
                (x, z) -> x > -62 && !(Math.abs(z) < 9) && !bamboo(x, z));
        plants(P.of("pink_petals[flower_amount=4]", 5, "short_grass", 8, "fern", 3, "lily_of_the_valley", 1, "allium", 1, "pink_tulip", 1, "bush", 2),
                0.24, (x, z) -> !bamboo(x, z));

        spawn = new double[] {-81.5, TERRACE + 2, 0.5, -90};
        flag = new int[] {-67, TERRACE + 1, 0};
        pads.add(new int[] {-69, TERRACE + 1, -15});
        pads.add(new int[] {-69, TERRACE + 1, 15});
        hills.add(new Hill("North Shrine", -3, Y + 1, LANE_N - 3, 3, Y + 4, LANE_N + 3));
        hills.add(new Hill("Moon Stage", -3, Y + 2, -3, 3, Y + 5, 3));
        hills.add(new Hill("South Shrine", -3, Y + 1, LANE_S - 3, 3, Y + 4, LANE_S + 3));
    }

    boolean bamboo(int x, int z) {
        return z < -17 && x > -58 && x < -8;
    }

    // ---------------------------------------------------------------- the temple compound

    void temple() {
        int y = TERRACE;
        // retaining wall round the terrace, a low wall with a roof of tiles on top, three gates to the lanes
        for (int x = -96; x <= -60; x++)
            for (int z = -40; z <= 40; z++) {
                if (!w.canon(x, z)) continue;
                double t2 = terrace(x, z);
                if (t2 < 0) continue;
                boolean rim = t2 < 0.25;
                int gy = w.ground(x, z);
                if (gy != y) continue;
                // gardens of moss and grass, walks of andesite from the hall to the three gates, a raked gravel court
                // in front of the hall where the flag stands
                boolean walk = Math.abs(z) <= 2 && x > -76 || x >= -66 && x <= -63 && Math.abs(z) <= 28
                        || Math.abs(Math.abs(z) - 26) <= 2 && x > -66;
                boolean court = x >= -73 && x <= -63 && Math.abs(z) <= 8;
                double n = w.fbm(x * 1.2, z * 1.2, 6, 2);
                String ground = rim ? "stone_bricks" : walk ? (((x + z) & 1) == 0 ? "polished_andesite" : "andesite")
                        : court ? "gravel" : n > 0.2 ? "moss_block" : "grass_block";
                w.set(x, y, z, ground);
                if (rim) {
                    boolean gate = Math.abs(z) <= 2 || Math.abs(z - LANE_N) <= 2 || Math.abs(z - LANE_S) <= 2;
                    if (!gate || x < -66) {
                        w.set(x, y + 1, z, "stone_bricks");
                        w.set(x, y + 2, z, "white_concrete");
                        w.set(x, y + 3, z, "deepslate_tile_slab[type=bottom]");
                    }
                }
            }
        // a gate roof over each of the three ways out: two posts and a little roof
        for (int gz : new int[] {LANE_N, 0, LANE_S}) {
            int gx = gateX(gz);
            for (int side : new int[] {-3, 3}) {
                w.set(gx, y + 1, gz + side, "polished_blackstone");
                for (int dy = 2; dy <= 4; dy++) w.set(gx, y + dy, gz + side, "stripped_mangrove_log[axis=y]");
            }
            for (int dz = -3; dz <= 3; dz++) w.set(gx, y + 5, gz + dz, "stripped_mangrove_log[axis=z]");
            k.gable(gx - 1, gz - 3, gx + 1, gz + 3, y + 6, false, 1, Kit.Roof.of("deepslate_tiles"), P.of("mangrove_planks"));
            keepClear(gx - 3, gz - 4, gx + 3, gz + 4);
        }
        hall(-82, 0);
        pagoda(-86, -20);
        pagoda(-86, 20);
        // the inner garden: stone lanterns along the walk, cherry trees in moss beds, a stone basin
        for (int z = -24; z <= 24; z += 8) if (Math.abs(z) > 6) { lantern(-62, y, z); lantern(-67, y, z); }
        lantern(-74, y, -4);
        lantern(-74, y, 4);
        // azalea hedges along the compound wall, ferns and petals in the moss
        for (int x = -94; x <= -62; x++) for (int z = -36; z <= 36; z++) {
            if (!w.canon(x, z) || terrace(x, z) < 0.25 || terrace(x, z) > 0.42) continue;
            if (w.type(x, y, z) == org.bukkit.Material.GRASS_BLOCK || w.type(x, y, z) == org.bukkit.Material.MOSS_BLOCK) {
                if (w.air(x, y + 1, z)) w.set(x, y + 1, z, ((x + z) % 5 == 0) ? "flowering_azalea_leaves[persistent=true]" : "azalea_leaves[persistent=true]");
            }
        }
        for (int[] p : new int[][] {{-73, -26}, {-73, 26}}) {
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) w.set(p[0] + dx, y, p[1] + dz, Math.abs(dx) == 2 || Math.abs(dz) == 2 ? "mossy_stone_bricks" : "moss_block");
            flora.tree(Theme.Tree.CHERRY, p[0], y + 1, p[1]);
        }
        keepClear(-96, -40, -60, 40);
    }

    int gateX(int gz) {
        int x = -60;
        while (x > -96 && terrace(x, gz) < 0) x--;
        return x;
    }

    /** A stone lantern (tōrō): plinth, post, the light in its box, a little cap. */
    void lantern(int x, int y, int z) {
        w.set(x, y + 1, z, "polished_andesite");
        w.set(x, y + 2, z, "andesite_wall");
        w.set(x, y + 3, z, "lantern[hanging=false]");
        w.set(x, y + 4, z, "andesite_slab[type=bottom]");
    }

    /**
     * The main hall, where the team spawns: a raised stone base with steps, dark posts and white walls behind a
     * veranda, shoji windows, and a deep hipped roof of dark tiles with a gable on the front.
     */
    void hall(int cx, int cz) {
        int x0 = cx - 6, x1 = cx + 6, z0 = cz - 9, z1 = cz + 9, y = TERRACE + 1;
        // the base, one step up, with steps at the front
        for (int x = x0 - 2; x <= x1 + 2; x++) for (int z = z0 - 2; z <= z1 + 2; z++) {
            boolean edge = x == x0 - 2 || x == x1 + 2 || z == z0 - 2 || z == z1 + 2;
            w.set(x, y, z, edge ? "stone_bricks" : "dark_oak_planks");
        }
        for (int dz = -3; dz <= 3; dz++) w.set(x1 + 3, y, cz + dz, "stone_brick_stairs[facing=west,half=bottom]");
        // walls: dark posts every 3, white panels, a band of dark wood under the eaves
        k.walls(x0, z0, x1, z1, y, 5, Kit.Bands.of(0, "dark_oak_planks", 1, "white_concrete", 5, "dark_oak_planks"), "dark_oak_log", 3,
                P.of("dark_oak_planks", 3, "stripped_dark_oak_wood", 1));
        // the veranda posts round the outside
        for (int x = x0 - 2; x <= x1 + 2; x += 3) for (int z : new int[] {z0 - 2, z1 + 2}) for (int dy = 1; dy <= 5; dy++) w.set(x, y + dy, z, "stripped_dark_oak_log[axis=y]");
        for (int z = z0 - 2; z <= z1 + 2; z += 3) for (int x : new int[] {x0 - 2, x1 + 2}) {
            if (Math.abs(z - cz) <= 2 && x == x1 + 2) continue;
            for (int dy = 1; dy <= 5; dy++) w.set(x, y + dy, z, "stripped_dark_oak_log[axis=y]");
        }
        // shoji windows between the posts
        for (int z = z0 + 1; z < z1; z++) {
            if ((z - z0) % 3 == 0) continue;
            for (int dy = 2; dy <= 3; dy++) {
                w.set(x0, y + dy, z, "white_stained_glass_pane");
                if (Math.abs(z - cz) > 2) w.set(x1, y + dy, z, "white_stained_glass_pane");
            }
        }
        for (int x = x0 + 1; x < x1; x++) {
            if ((x - x0) % 3 == 0) continue;
            for (int dy = 2; dy <= 3; dy++) {
                w.set(x, y + dy, z0, "white_stained_glass_pane");
                w.set(x, y + dy, z1, "white_stained_glass_pane");
            }
        }
        // the open front: three bays wide
        for (int dz = -2; dz <= 2; dz++) for (int dy = 1; dy <= 3; dy++) w.set(x1, y + dy, cz + dz, "air");
        for (int dz = -2; dz <= 2; dz++) w.set(x1, y + 4, cz + dz, "dark_oak_planks");
        // the roof: wide hip, and a front gable over the entrance
        k.hip(x0 - 2, z0 - 2, x1 + 2, z1 + 2, y + 6, 1, Kit.Roof.of("deepslate_tiles"), P.of("dark_oak_planks"));
        // inside: tatami mats (carpets), a low altar at the back, lanterns hung from beams, team banners
        for (int x = x0 + 1; x < x1; x++) for (int z = z0 + 1; z < z1; z++) {
            boolean border = x == x0 + 1 || z == z0 + 1 || z == z1 - 1;
            w.set(x, y + 1, z, border ? "air" : ((x / 2 + z / 3) & 1) == 0 ? "lime_carpet" : "green_carpet");
        }
        for (int z = cz - 2; z <= cz + 2; z++) w.set(x0 + 1, y + 1, z, "dark_oak_planks");
        w.set(x0 + 1, y + 2, cz, "decorated_pot");
        w.set(x0 + 1, y + 2, cz - 2, "candle[candles=3,lit=true]");
        w.set(x0 + 1, y + 2, cz + 2, "candle[candles=3,lit=true]");
        for (int z = z0 + 3; z <= z1 - 3; z += 3) for (int x = x0 + 1; x < x1; x++) w.set(x, y + 6, z, "stripped_dark_oak_log[axis=x]");
        for (int z = z0 + 3; z <= z1 - 3; z += 6) w.hangLight(cx, y + 4, z, "lantern");
        w.set(x0 + 1, y + 4, cz - 4, "TEAM_wall_banner[facing=east]");
        w.set(x0 + 1, y + 4, cz + 4, "TEAM_wall_banner[facing=east]");
        keepClear(x0 - 3, z0 - 3, x1 + 4, z1 + 3);
    }

    /** A three-tiered pagoda: each storey smaller, each with its own deep tiled roof, a spire on top. */
    void pagoda(int cx, int cz) {
        int y = TERRACE + 1;
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) w.set(cx + dx, y, cz + dz, Math.abs(dx) == 4 || Math.abs(dz) == 4 ? "stone_bricks" : "polished_andesite");
        int[] half = {3, 2, 2};
        int[] tall = {4, 3, 3};
        int yy = y;
        for (int i = 0; i < 3; i++) {
            int h = half[i];
            k.walls(cx - h, cz - h, cx + h, cz + h, yy, tall[i], Kit.Bands.of(0, i == 0 ? "dark_oak_planks" : "white_concrete", tall[i], "dark_oak_planks"),
                    "stripped_mangrove_log", 0, P.of("dark_oak_planks"));
            for (int dy = 2; dy <= tall[i] - 1; dy++) for (int o : new int[] {-1, 0, 1}) {
                if (o != 0 && h < 3) continue;
                w.set(cx + o, yy + dy, cz - h, "white_stained_glass_pane");
                w.set(cx + o, yy + dy, cz + h, "white_stained_glass_pane");
                w.set(cx - h, yy + dy, cz + o, "white_stained_glass_pane");
                w.set(cx + h, yy + dy, cz + o, "white_stained_glass_pane");
            }
            int roofY = yy + tall[i] + 1;
            k.hip(cx - h, cz - h, cx + h, cz + h, roofY, 2, Kit.Roof.of("deepslate_tiles"), P.of("dark_oak_planks"));
            yy = roofY + 1;
            // the next storey stands on the roof's inner part
            if (i < 2) for (int dx = -half[i + 1]; dx <= half[i + 1]; dx++) for (int dz = -half[i + 1]; dz <= half[i + 1]; dz++) w.set(cx + dx, yy, cz + dz, "dark_oak_planks");
        }
        // the spire
        int top = w.top(cx, cz);
        for (int dy = 1; dy <= 4; dy++) w.set(cx, top + dy, cz, dy == 4 ? "polished_blackstone_slab[type=bottom]" : "polished_blackstone_wall");
        // a door on the garden side
        int door = cz > 0 ? -1 : 1;
        w.set(cx, y + 1, cz + door * 3, "dark_oak_door[facing=" + (door > 0 ? "north" : "south") + ",half=lower,hinge=left,open=false]");
        w.set(cx, y + 2, cz + door * 3, "dark_oak_door[facing=" + (door > 0 ? "north" : "south") + ",half=upper,hinge=left,open=false]");
        w.hangLight(cx, y + 3, cz, "lantern");
        keepClear(cx - 6, cz - 6, cx + 6, cz + 6);
    }

    // ---------------------------------------------------------------- the lanes

    /** The Grand Approach: a stone way under three torii gates, lanterns all along, cherries beyond. */
    void approach() {
        for (int x = -66; x <= 0; x++)
            for (int z = -3; z <= 3; z++) {
                if (!w.canon(x, z)) continue;
                int gy = w.ground(x, z);
                if (gy < 50) continue;
                boolean edge = Math.abs(z) == 3;
                c(x, gy, z, edge ? "andesite" : ((x + z) & 1) == 0 ? "polished_andesite" : "smooth_stone");
                for (int dy = 1; dy <= 5; dy++) if (!w.air(x, gy + dy, z)) c(x, gy + dy, z, "air");
            }
        for (int gx : new int[] {-52, -36, -20}) torii(gx, w.ground(gx, 0));
        for (int x = -58; x <= -8; x += 8) for (int side : new int[] {-5, 5}) {
            int gy = w.ground(x, side);
            if (gy > 50) lantern(x, gy, side);
        }
        keepClear(-66, -6, 0, 6);
    }

    /** A vermilion torii across the way at x. */
    void torii(int x, int gy) {
        for (int side : new int[] {-4, 4}) {
            w.set(x, gy + 1, side, "polished_blackstone");
            for (int dy = 2; dy <= 6; dy++) w.set(x, gy + dy, side, "stripped_mangrove_log[axis=y]");
        }
        for (int z = -5; z <= 5; z++) w.set(x, gy + 5, z, "mangrove_planks");
        w.set(x, gy + 6, 0, "mangrove_planks");
        for (int z = -5; z <= 5; z++) w.set(x, gy + 7, z, "polished_blackstone");
        w.set(x, gy + 7, -6, "polished_blackstone_stairs[facing=south,half=top]");
        w.set(x, gy + 7, 6, "polished_blackstone_stairs[facing=north,half=top]");
        w.set(x, gy + 8, -6, "polished_blackstone_slab[type=bottom]");
        w.set(x, gy + 8, 6, "polished_blackstone_slab[type=bottom]");
    }

    /** North lane, home half: bamboo on rising terraces, a stream with stepping stones, a little shrine. */
    void bambooGrove() {
        // the stream: from the terrace edge winding east along the lane
        for (int x = -58; x <= -8; x++) {
            int z = LANE_N + (int) Math.round(Math.sin(x / 7.0) * 3);
            for (int dz = -1; dz <= 1; dz++) {
                int gy = w.ground(x, z + dz);
                if (gy < 50) continue;
                w.set(x, gy, z + dz, "water");
                w.set(x, gy - 1, z + dz, w.r.nextBoolean() ? "gravel" : "clay");
                for (int dy = 1; dy <= 2; dy++) w.set(x, gy + dy, z + dz, "air");
            }
            if (x % 9 == 0) {
                int gy = w.ground(x, z);
                w.set(x, gy, z, "mossy_cobblestone");
            }
        }
        // bamboo, in clumps
        for (int x = -58; x <= -8; x++)
            for (int z = -36; z <= -17; z++) {
                if (!bamboo(x, z) || w.fbm(x * 1.5, z * 1.5, 6, 2) < 0.05 || w.r.nextInt(3) != 0) continue;
                int gy = w.ground(x, z);
                if (gy < 50 || !soil(w.type(x, gy, z)) || !w.air(x, gy + 1, z)) continue;
                int h = 7 + w.r.nextInt(7);
                for (int i = 1; i <= h; i++) w.set(x, gy + i, z, "bamboo[age=1,leaves=" + (i >= h - 1 ? "large" : i >= h - 3 ? "small" : "none") + ",stage=0]");
            }
        // a small shrine by the stream
        int sx = -34, sz = LANE_N - 6, gy = w.ground(sx, sz);
        if (gy > 50) {
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 1; dy <= 6; dy++) w.set(sx + dx, gy + dy, sz + dz, "air");
                w.set(sx + dx, gy, sz + dz, "stone_bricks");
            }
            k.walls(sx - 1, sz - 1, sx + 1, sz + 1, gy, 3, Kit.Bands.of(0, "dark_oak_planks"), "stripped_mangrove_log", 0, P.of("dark_oak_planks"));
            w.set(sx, gy + 1, sz + 1, "air");
            w.set(sx, gy + 2, sz + 1, "air");
            w.set(sx, gy + 1, sz, "decorated_pot");
            k.hip(sx - 1, sz - 1, sx + 1, sz + 1, gy + 4, 1, Kit.Roof.of("deepslate_tiles"), P.of("dark_oak_planks"));
            keepClear(sx - 3, sz - 3, sx + 3, sz + 3);
        }
    }

    /** South lane, home half: a koi pond with an arched bridge, a tea house on the bank, maples and lanterns. */
    void koiGarden() {
        int px = -34, pz = LANE_S, gy = w.ground(px, pz);
        if (gy < 50) return;
        for (int dx = -9; dx <= 9; dx++)
            for (int dz = -6; dz <= 6; dz++) {
                double d = Math.hypot(dx / 9.0, dz / 6.0) + w.n(px + dx, pz + dz, 3) * 0.08;
                if (d > 1.05) continue;
                int x = px + dx, z = pz + dz;
                for (int dy = 1; dy <= 6; dy++) w.set(x, gy + dy, z, "air");
                if (d > 0.88) {
                    w.set(x, gy, z, w.r.nextBoolean() ? "mossy_cobblestone" : "stone");
                } else {
                    w.set(x, gy, z, "water");
                    w.set(x, gy - 1, z, d < 0.6 ? "water" : "clay");
                    w.set(x, gy - 2, z, "clay");
                    if (w.r.nextInt(7) == 0) w.set(x, gy + 1, z, "lily_pad");
                }
            }
        // the arched bridge across the middle of the pond (north–south)
        for (int dz = -7; dz <= 7; dz++) {
            int h = (int) Math.round(Math.sqrt(Math.max(0, 49 - dz * dz)) * 0.32);
            for (int dx = -1; dx <= 1; dx++) {
                w.set(px + dx, gy + h, pz + dz, "mangrove_planks");
                for (int dy = 1; dy <= 3; dy++) w.set(px + dx, gy + h + dy, pz + dz, "air");
                if (h > 0) w.set(px + dx, gy + h - 1, pz + dz, dx == 0 ? "air" : "stripped_mangrove_log[axis=z]");
            }
            w.set(px - 2, gy + h + 1, pz + dz, "mangrove_fence");
            w.set(px + 2, gy + h + 1, pz + dz, "mangrove_fence");
            w.set(px - 2, gy + h, pz + dz, "stripped_mangrove_log[axis=z]");
            w.set(px + 2, gy + h, pz + dz, "stripped_mangrove_log[axis=z]");
        }
        // the tea house on the west bank
        int tx = px - 15, tz = pz, ty = w.ground(tx, tz);
        if (ty > 50) {
            for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
                for (int dy = 1; dy <= 7; dy++) w.set(tx + dx, ty + dy, tz + dz, "air");
                w.set(tx + dx, ty, tz + dz, Math.abs(dx) == 4 || Math.abs(dz) == 4 ? "stone_bricks" : "dark_oak_planks");
            }
            k.walls(tx - 3, tz - 3, tx + 3, tz + 3, ty, 4, Kit.Bands.of(0, "dark_oak_planks", 1, "white_concrete", 4, "dark_oak_planks"),
                    "stripped_dark_oak_log", 3, P.of("dark_oak_planks"));
            for (int o = -1; o <= 1; o++) for (int dy = 2; dy <= 3; dy++) {
                w.set(tx + o, ty + dy, tz - 3, "white_stained_glass_pane");
                w.set(tx + o, ty + dy, tz + 3, "white_stained_glass_pane");
            }
            // open to the pond
            for (int o = -1; o <= 1; o++) for (int dy = 1; dy <= 3; dy++) w.set(tx + 3, ty + dy, tz + o, "air");
            k.hip(tx - 3, tz - 3, tx + 3, tz + 3, ty + 5, 2, Kit.Roof.of("dark_oak_planks"), P.of("dark_oak_planks"));
            for (int dx = -2; dx <= 1; dx++) for (int dz = -2; dz <= 2; dz++) w.set(tx + dx, ty + 1, tz + dz, ((dx + dz) & 1) == 0 ? "lime_carpet" : "green_carpet");
            // a low table (a slab on the floor) with a tea set
            w.set(tx - 1, ty + 1, tz, "dark_oak_slab[type=bottom]");
            w.set(tx - 1, ty + 1, tz + 1, "dark_oak_slab[type=bottom]");
            w.set(tx - 1, ty + 2, tz, "flower_pot");
            w.set(tx - 1, ty + 2, tz + 1, "flower_pot");
            w.hangLight(tx, ty + 4, tz, "lantern");
            keepClear(tx - 5, tz - 5, tx + 5, tz + 5);
        }
        lantern(px + 11, w.ground(px + 11, pz - 4), pz - 4);
        lantern(px + 11, w.ground(px + 11, pz + 4), pz + 4);
        keepClear(px - 10, pz - 7, px + 10, pz + 7);
    }

    /** Arched red bridges over the chasms between the lanes. */
    void bridges() {
        String[] b = {"mangrove_planks", "mangrove_slab", "stripped_mangrove_wood", "mangrove_fence", "stripped_mangrove_log", "lantern"};
        k.bridge(-42, -18, -42, -4, 3, b[0], b[1], b[2], b[3], b[4], b[5]);
        k.bridge(-22, 4, -22, 18, 3, b[0], b[1], b[2], b[3], b[4], b[5]);
    }

    // ---------------------------------------------------------------- the middle

    /** The Moon Stage: a raised cypress deck in the middle of the Approach, steps on all sides, lanterns at the corners. */
    void stage() {
        int gy = Y;
        for (int x = -6; x <= 6; x++)
            for (int z = -6; z <= 6; z++) {
                if (!w.canon(x, z)) continue;
                int ax = Math.abs(x), az = Math.abs(z);
                for (int dy = 1; dy <= 8; dy++) c(x, gy + dy, z, "air");
                if (ax <= 4 && az <= 4) {
                    c(x, gy, z, "stone_bricks");
                    c(x, gy + 1, z, ax == 4 || az == 4 ? "stripped_dark_oak_wood" : ((x + z) & 1) == 0 ? "dark_oak_planks" : "stripped_dark_oak_wood");
                } else if (ax <= 5 && az <= 5) {
                    String face = ax >= az ? (x > 0 ? "west" : "east") : (z > 0 ? "north" : "south");
                    c(x, gy + 1, z, ax == az ? "stone_brick_slab[type=bottom]" : "dark_oak_stairs[facing=" + face + ",half=bottom]");
                    c(x, gy, z, "stone_bricks");
                } else {
                    c(x, gy, z, "polished_andesite");
                }
            }
        for (int[] p : new int[][] {{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) {
            if (!w.canon(p[0], p[1])) continue;
            c(p[0], gy + 2, p[1], "stripped_mangrove_log[axis=y]");
            c(p[0], gy + 3, p[1], "stripped_mangrove_log[axis=y]");
            c(p[0], gy + 4, p[1], "lantern[hanging=false]");
        }
        keepClear(-7, -7, 7, 7);
    }

    /** A shrine point on a garden lane: a stone court with lanterns at the corners, a small offering stone. */
    void shrine(int cx, int cz) {
        int gy = Y;
        for (int x = cx - 5; x <= cx + 5; x++)
            for (int z = cz - 5; z <= cz + 5; z++) {
                if (!w.canon(x, z)) continue;
                double d = Math.max(Math.abs(x - cx), Math.abs(z - cz));
                for (int dy = 1; dy <= 6; dy++) c(x, gy + dy, z, "air");
                c(x, gy, z, d == 5 ? "mossy_stone_bricks" : ((x + z) & 1) == 0 ? "stone_bricks" : "polished_andesite");
                for (int yy = gy - 1; yy > gy - 6 && !w.type(x, yy, z).isSolid(); yy--) c(x, yy, z, "stone");
            }
        for (int[] p : new int[][] {{-5, -5}, {5, -5}, {-5, 5}, {5, 5}}) {
            int x = cx + p[0], z = cz + p[1];
            if (!w.canon(x, z)) continue;
            c(x, gy + 1, z, "polished_andesite");
            c(x, gy + 2, z, "andesite_wall");
            c(x, gy + 3, z, "lantern[hanging=false]");
            c(x, gy + 4, z, "andesite_slab[type=bottom]");
        }
        keepClear(cx - 6, cz - 6, cx + 6, cz + 6);
    }
}
