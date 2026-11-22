package com.sasha.ctfbuilder.v2.maps;

import com.sasha.ctfbuilder.v2.Arena;
import com.sasha.ctfbuilder.v2.Kit;
import com.sasha.ctfbuilder.v2.Land;
import com.sasha.ctfbuilder.v2.Theme;
import com.sasha.ctfbuilder.v2.W.P;
import org.bukkit.World;

/**
 * Frostfall: two Viking villages on snowy headlands either side of a frozen lake. The lake is the short way,
 * a wide open sheet of slippery ice with a longship frozen in the middle of it (the hill). Round it: on one
 * flank a glacier you can climb over or run through by its ice cave, on the other pine hills with a hunter's
 * lodge — the long, covered ways. Each village sits inside a palisade with a gate toward the lake.
 */
public final class Frostfall extends Arena {

    static final int ICE = 64, SHORE = 71, HOME = 74;

    public Frostfall(World world) {
        super(world, "frostfall", false, Theme.get("frostfall"));
        name = "Frostfall";
        icon = "PACKED_ICE";
        minutes = 18;
        kothPoints = 120;
        reach = 124;
        low = 15;
        high = 140;
        voidY = 15;
        spectator = new double[] {0.5, 110, 0.5, -90, 40};
    }

    double lake(int x, int z) {
        return Land.ellipse(x, z, 0, 0, 56, 27, 0) + w.fbm(x * 0.7, z * 0.7, 12, 2) * 0.08;
    }

    boolean glacier(int x, int z) {
        return z < -30 && x > -72 && x < -6;
    }

    double height(int x, int z) {
        double e = Land.ellipse(x, z, 0, 0, 112, 60, 0) + w.fbm(x * 0.6, z * 0.6, 14, 2) * 0.06;
        double head = Land.ellipse(x, z, -88, 0, 26, 30, 0) + w.fbm(x, z, 10, 2) * 0.1;
        if (e < 0 && head < 0) return Double.NaN;
        double l = lake(x, z);
        if (l > 0) return ICE - 1;
        double shore = Math.max(0, -l) * 30; // blocks from the lake edge, roughly
        double h = ICE + 1 + Math.min(SHORE - ICE - 1, shore * 1.1) + w.fbm(x + 300, z - 120, 16, 3) * 3 * Land.smooth(shore / 8);
        if (glacier(x, z)) {
            double g = Land.smooth((-30 - z) / 8.0) * Land.smooth(Math.min(x + 72, -6 - x) / 10.0);
            h += g * 12 + w.fbm(x * 2, z * 2, 6, 2) * 2 * g;
        }
        if (z > 30 && x < -6) h += Math.max(0, w.fbm(x + 40, z, 12, 2)) * 6; // pine hills
        double home = Land.smooth((-62 - x) / 10.0);
        h = h * (1 - home) + HOME * home;
        if (e < 0.05 && head < 0.05) h -= 1;
        return h;
    }

    String surface(int x, int z, int y, int steep, double edge) {
        double n = w.fbm(x + 913, z - 377, 9, 2);
        if (y < ICE) return "packed_ice";
        if (glacier(x, z) && y > SHORE + 2) return n > 0.2 ? "blue_ice" : n < -0.2 ? "snow_block" : "packed_ice";
        if (steep >= 3) return n > 0 ? "stone" : "packed_ice";
        if (n > 0.4) return "podzol";
        if (n < -0.45) return "coarse_dirt";
        return "snow_block";
    }

    @Override
    protected void build() {
        Land land = new Land(w, t, -120, 120, -66, 66);
        land.top = this::height;
        land.surface = this::surface;
        land.depth = 28;
        land.build();
        // the lake's ice: packed ice with snow drifts and clear patches
        for (int x = -60; x <= 0; x++) for (int z = -30; z <= 30; z++) {
            if (!w.canon(x, z) || lake(x, z) <= 0) continue;
            double n = w.fbm(x * 1.4, z * 1.4, 6, 2);
            w.set(x, ICE, z, n > 0.45 ? "snow_block" : n < -0.35 ? "ice" : "packed_ice");
            if (n > 0.55) w.set(x, ICE + 1, z, "snow[layers=2]");
        }
        // ice spikes standing out of the lake for cover
        for (int[] p : new int[][] {{-36, -12}, {-30, 14}, {-18, -18}, {-12, 8}, {-44, 4}}) iceSpike(p[0], p[1], 6 + w.r.nextInt(6));

        village(-88, 0);
        longship();
        iceCave();
        lodge(-38, 44);

        trees(new Theme.Tree[] {Theme.Tree.SPRUCE}, new int[] {1}, 0.03, 6, 2, (x, z) -> lake(x, z) < -0.1 && !glacier(x, z));
        plants(P.of("fern", 3, "short_grass", 2, "sweet_berry_bush[age=3]", 1, "large_fern[half=lower]", 1), 0.05, (x, z) -> lake(x, z) < -0.1);
        // snow layers over the open ground
        for (int x = -120; x <= 0; x++) for (int z = -66; z <= 66; z++) {
            if (!w.canon(x, z) || w.r.nextInt(3) != 0) continue;
            int gy = w.ground(x, z);
            if (gy < 60 || w.type(x, gy, z) != org.bukkit.Material.SNOW_BLOCK && w.type(x, gy, z) != org.bukkit.Material.PODZOL) continue;
            if (w.air(x, gy + 1, z)) w.set(x, gy + 1, z, "snow[layers=" + (1 + w.r.nextInt(2)) + "]");
        }

        spawn = new double[] {-86.5, HOME + 1, 0.5, -90};
        flag = new int[] {-74, HOME + 1, 0};
        pads.add(new int[] {-74, HOME + 1, -12});
        pads.add(new int[] {-74, HOME + 1, 12});
        hills.add(new Hill("Longship", -4, ICE + 3, -2, 4, ICE + 6, 2));
    }

    void iceSpike(int x, int z, int h) {
        for (int dy = 1; dy <= h; dy++) {
            double r = 1.6 * (1 - dy / (double) (h + 1)) + 0.4;
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
                if (Math.hypot(dx, dz) <= r) w.set(x + dx, ICE + dy, z + dz, dy > h - 2 ? "packed_ice" : (dx + dz & 1) == 0 ? "packed_ice" : "ice");
        }
        keepClear(x - 2, z - 2, x + 2, z + 2);
    }

    // ---------------------------------------------------------------- the village

    void village(int cx, int cz) {
        int y = HOME;
        level(cx - 14, cz - 22, cx + 20, cz + 22, y, P.of("snow_block", 3, "packed_mud", 1, "coarse_dirt", 1), P.of("dirt"));
        longhouse(cx - 4, cz);
        watchtower(cx + 18, cz - 19);
        watchtower(cx + 18, cz + 19);
        smithy(cx + 6, cz - 14);
        storehouse(cx + 6, cz + 14);
        palisade(cx, cz);
        // the square in front of the longhouse: flagstones round the flag, braziers of lanterns on stones
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            double d = Math.hypot(dx, dz);
            if (d > 4.4) continue;
            w.set(cx + 14 + dx, y, cz + dz, d < 1.5 ? "chiseled_stone_bricks" : (dx + dz & 1) == 0 ? "cobblestone" : "stone_bricks");
        }
        for (int s : new int[] {-1, 1}) {
            w.set(cx + 14, y + 1, cz + s * 5, "cobblestone_wall");
            w.set(cx + 14, y + 2, cz + s * 5, "lantern[hanging=false]");
        }
        keepClear(cx - 15, cz - 23, cx + 22, cz + 23);
    }

    /**
     * The longhouse, where the team spawns: stone footing, walls of stacked logs, a steep roof that comes low
     * at the sides with crossed bargeboards at both gables, a door at the east gable under a porch. Inside:
     * benches down both walls, tables, a stone hearth lit by lanterns (no open fire), shields on the walls.
     */
    void longhouse(int cx, int cz) {
        int y = HOME;
        int x0 = cx - 11, x1 = cx + 11, z0 = cz - 5, z1 = cz + 5;
        k.walls(x0, z0, x1, z1, y, 4, Kit.Bands.of(0, P.of("cobblestone", 2, "mossy_cobblestone", 1), 1, "stripped_spruce_wood"), "spruce_log", 4,
                P.of("spruce_planks", 3, "stripped_spruce_wood", 1));
        // log courses along the long walls
        for (int x = x0 + 1; x < x1; x++) for (int dy = 2; dy <= 4; dy++) {
            if ((x - x0) % 4 == 0) continue;
            w.set(x, y + dy, z0, "stripped_spruce_log[axis=x]");
            w.set(x, y + dy, z1, "stripped_spruce_log[axis=x]");
        }
        k.gable(x0, z0, x1, z1, y + 5, true, 2, Kit.Roof.of("spruce_planks"), P.of("stripped_spruce_log[axis=z]"));
        int ridge = w.top(x0, cz);
        // crossed bargeboards at the gables (dragon-head style horns)
        for (int gx : new int[] {x0 - 2, x1 + 2}) {
            int out = gx < cx ? -1 : 1;
            w.set(gx, ridge + 1, cz - 1, "dark_oak_fence");
            w.set(gx, ridge + 1, cz + 1, "dark_oak_fence");
            w.set(gx, ridge + 2, cz - 2, "dark_oak_fence");
            w.set(gx, ridge + 2, cz + 2, "dark_oak_fence");
        }
        // the east door with a porch roof
        for (int dz = -1; dz <= 1; dz++) for (int dy = 1; dy <= 3; dy++) w.set(x1, y + dy, cz + dz, dz == 0 || dy < 3 ? "air" : "stripped_spruce_log[axis=z]");
        w.set(x1, y + 1, cz, "spruce_door[facing=west,half=lower,hinge=left,open=true]");
        w.set(x1, y + 2, cz, "spruce_door[facing=west,half=upper,hinge=left,open=true]");
        for (int dz = -2; dz <= 2; dz++) w.set(x1 + 1, y + 4, cz + dz, "spruce_slab[type=bottom]");
        for (int s : new int[] {-2, 2}) for (int dy = 1; dy <= 3; dy++) w.set(x1 + 1, y + dy, cz + s, "stripped_spruce_log[axis=y]");
        w.set(x1 + 1, y + 3, cz, "lantern[hanging=true]");
        // windows: small, high, with shutters
        for (int x = x0 + 2; x < x1 - 1; x += 4) {
            k.window(x, y + 3, z0, "north", 1, 1, "glass_pane", null, "spruce_trapdoor");
            k.window(x, y + 3, z1, "south", 1, 1, "glass_pane", null, "spruce_trapdoor");
        }
        // inside: benches along the walls, two long tables, the hearth
        for (int x = x0 + 2; x <= x1 - 2; x++) {
            if (Math.abs(x - cx) <= 2) continue;
            w.set(x, y + 1, z0 + 1, "spruce_stairs[facing=north,half=bottom]");
            w.set(x, y + 1, z1 - 1, "spruce_stairs[facing=south,half=bottom]");
        }
        for (int x = x0 + 3; x <= x1 - 3; x++) {
            if (Math.abs(x - cx) <= 2) continue;
            w.set(x, y + 1, z0 + 2, "stripped_spruce_wood");
            w.set(x, y + 1, z1 - 2, "stripped_spruce_wood");
        }
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) w.set(cx + dx, y + 1, cz + dz, dx == 0 && dz == 0 ? "lantern[hanging=false]" : "cobblestone_wall");
        w.set(cx, y, cz, "cobblestone");
        // shields: banners on the walls between the windows
        for (int x = x0 + 4; x < x1 - 2; x += 4) {
            w.set(x, y + 3, z0 + 1, "TEAM_wall_banner[facing=south]");
            w.set(x, y + 3, z1 - 1, "TEAM_wall_banner[facing=north]");
        }
        // a high table at the west end
        for (int dz = -2; dz <= 2; dz++) w.set(x0 + 2, y + 1, cz + dz, "dark_oak_planks");
        w.set(x0 + 1, y + 1, cz, "spruce_stairs[facing=west,half=bottom]");
        w.set(x0 + 2, y + 2, cz, "decorated_pot");
        // beams across to hang lights from
        for (int x = x0 + 3; x <= x1 - 3; x += 5) {
            for (int z = z0 + 1; z < z1; z++) w.set(x, y + 5, z, "stripped_spruce_log[axis=z]");
            w.hangLight(x, y + 4, cz, "lantern");
        }
        keepClear(x0 - 3, z0 - 3, x1 + 3, z1 + 3);
    }

    /** A watchtower: four log legs braced across, a plank platform with a rail, a roof, a ladder up. */
    void watchtower(int cx, int cz) {
        int y = HOME;
        int top = y + 10;
        for (int[] l : new int[][] {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
            k.footing(cx + l[0], cz + l[1], cx + l[0], cz + l[1], y, P.of("cobblestone"));
            for (int dy = 1; dy <= top - y + 4; dy++) w.set(cx + l[0], y + dy, cz + l[1], "spruce_log[axis=y]");
        }
        // cross braces on each side at two heights
        for (int lvl : new int[] {y + 4, y + 8}) for (int o = -1; o <= 1; o++) {
            w.set(cx + o, lvl, cz - 2, "stripped_spruce_log[axis=x]");
            w.set(cx + o, lvl, cz + 2, "stripped_spruce_log[axis=x]");
            w.set(cx - 2, lvl, cz + o, "stripped_spruce_log[axis=z]");
            w.set(cx + 2, lvl, cz + o, "stripped_spruce_log[axis=z]");
        }
        // the platform, wider than the legs, with a rail
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            boolean leg = Math.abs(dx) == 2 && Math.abs(dz) == 2;
            if (!leg) w.set(cx + dx, top, cz + dz, "spruce_planks");
            if ((Math.abs(dx) == 3 || Math.abs(dz) == 3)) w.set(cx + dx, top + 1, cz + dz, "spruce_fence");
        }
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) if (Math.abs(dx) == 3 || Math.abs(dz) == 3)
            if (w.air(cx + dx, top - 1, cz + dz)) w.set(cx + dx, top - 1, cz + dz, "spruce_trapdoor[facing=" + (dx == 3 ? "west" : dx == -3 ? "east" : dz == 3 ? "north" : "south") + ",half=top,open=false]");
        // the roof on the legs
        k.hip(cx - 2, cz - 2, cx + 2, cz + 2, top + 5, 2, Kit.Roof.of("spruce_planks"), null);
        // a ladder up the inside of one leg pair, a hatch through the platform
        for (int dy = 1; dy < top - y; dy++) w.set(cx - 2, y + dy, cz - 1, "ladder[facing=south]");
        w.set(cx - 2, top, cz - 1, "ladder[facing=south]");
        w.set(cx, top + 1, cz, "TEAM_banner[rotation=0]");
        keepClear(cx - 4, cz - 4, cx + 4, cz + 4);
    }

    /** The smithy: an open-fronted shed with an anvil, a grindstone, a blast furnace and a quench trough. */
    void smithy(int cx, int cz) {
        int y = HOME;
        for (int dx = -3; dx <= 3; dx++) for (int dz = -2; dz <= 2; dz++) w.set(cx + dx, y, cz + dz, "stone_bricks");
        for (int[] p : new int[][] {{-3, -2}, {3, -2}, {-3, 2}, {3, 2}}) for (int dy = 1; dy <= 3; dy++) w.set(cx + p[0], y + dy, cz + p[1], "spruce_log[axis=y]");
        for (int dx = -2; dx <= 2; dx++) for (int dy = 1; dy <= 3; dy++) w.set(cx + dx, y + dy, cz - 2, dy == 1 ? "cobblestone" : "stripped_spruce_log[axis=x]");
        k.gable(cx - 3, cz - 2, cx + 3, cz + 2, y + 4, true, 1, Kit.Roof.of("spruce_planks"), P.of("stripped_spruce_log[axis=z]"));
        w.set(cx - 2, y + 1, cz - 1, "blast_furnace[facing=south,lit=true]");
        w.set(cx - 1, y + 1, cz - 1, "blast_furnace[facing=south,lit=true]");
        w.set(cx + 1, y + 1, cz, "anvil[facing=east]");
        w.set(cx + 2, y + 1, cz - 1, "grindstone[face=floor,facing=south]");
        w.set(cx + 2, y + 1, cz + 1, "water_cauldron[level=3]");
        w.hangLight(cx, y + 3, cz, "lantern");
        keepClear(cx - 4, cz - 3, cx + 4, cz + 3);
    }

    /** A storehouse on short stilts (to keep the stores dry), with barrels and a hayloft. */
    void storehouse(int cx, int cz) {
        int y = HOME + 1;
        for (int[] p : new int[][] {{-3, -2}, {3, -2}, {-3, 2}, {3, 2}, {0, -2}, {0, 2}}) w.set(cx + p[0], y, cz + p[1], "stone_bricks");
        k.walls(cx - 3, cz - 2, cx + 3, cz + 2, y, 3, Kit.Bands.of(0, "spruce_planks", 1, "stripped_spruce_log[axis=x]"), "spruce_log", 3, P.of("spruce_planks"));
        k.gable(cx - 3, cz - 2, cx + 3, cz + 2, y + 4, true, 1, Kit.Roof.of("spruce_planks"), P.of("spruce_planks"));
        w.set(cx, y + 1, cz - 2, "spruce_door[facing=south,half=lower,hinge=left,open=false]");
        w.set(cx, y + 2, cz - 2, "spruce_door[facing=south,half=upper,hinge=left,open=false]");
        w.set(cx, y, cz - 3, "spruce_stairs[facing=south,half=bottom]");
        for (int dx = -2; dx <= 2; dx++) w.set(cx + dx, y + 1, cz + 1, dx % 2 == 0 ? "barrel[facing=up]" : "hay_block");
        keepClear(cx - 4, cz - 4, cx + 4, cz + 3);
    }

    /**
     * The palisade round the village: sharpened logs standing in the ground (each one reaches down to it), a
     * gate east toward the lake, the cliffs behind left open.
     */
    void palisade(int cx, int cz) {
        int x0 = cx - 13, x1 = cx + 22, z0 = cz - 22, z1 = cz + 22;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            boolean ring = x == x1 || z == z0 || z == z1;
            if (!ring) continue;
            if (x == x1 && Math.abs(z - cz) <= 3) continue;          // the gate
            if (Math.abs(x - (cx + 18)) <= 3 && (Math.abs(z - z0) <= 3 || Math.abs(z - z1) <= 3)) continue; // the towers stand in the line
            int gy = w.ground(x, z);
            if (gy < HOME - 6) continue;
            for (int yy = gy - 2; yy <= gy + 3; yy++) w.set(x, yy, z, "spruce_log[axis=y]");
            w.set(x, gy + 4, z, ((x + z) & 1) == 0 ? "spruce_fence" : "air");
        }
        // the gate's posts and the beam over it
        for (int s : new int[] {-4, 4}) for (int dy = 1; dy <= 6; dy++) w.set(x1, HOME + dy, cz + s, "stripped_spruce_log[axis=y]");
        for (int dz = -4; dz <= 4; dz++) w.set(x1, HOME + 6, cz + dz, "spruce_log[axis=z]");
        w.set(x1 + 1, HOME + 6, cz, "TEAM_wall_banner[facing=east]");
    }

    // ---------------------------------------------------------------- the lake

    /**
     * A longship frozen in the middle of the lake: double-ended, its stems curling up at both ends, shields
     * along the gunwales, oars locked in the ice, rowing benches. Built half and copied, like the boat is.
     */
    void longship() {
        int deck = ICE + 2;
        for (int x = -15; x <= 0; x++) {
            double t = 1 - Math.pow(Math.abs(x) / 15.5, 2.4);
            int half = Math.max(0, (int) Math.round(3.6 * t));
            for (int z = -half; z <= half; z++) {
                if (!w.canon(x, z)) continue;
                boolean side = Math.abs(z) == half;
                // the hull from below the ice up to the gunwale
                for (int yy = ICE - 2; yy <= deck + 1; yy++) {
                    boolean shell = side || yy == ICE - 2;
                    if (yy > deck && !side) { c(x, yy, z, "air"); continue; }
                    if (yy == deck && !side) { c(x, yy, z, "spruce_planks"); continue; }
                    c(x, yy, z, shell ? ((yy & 1) == 0 ? "dark_oak_planks" : "stripped_dark_oak_log[axis=x]") : yy < deck ? "spruce_planks" : "air");
                }
                if (side && Math.abs(x) <= 11 && (x & 1) == 0) c(x, deck + 1, z + Integer.signum(z), (x & 3) == 0 ? "TEAM_wall_banner[facing=" + (z < 0 ? "north" : "south") + "]" : "white_wall_banner[facing=" + (z < 0 ? "north" : "south") + "]");
            }
            // rowing benches across, every third frame
            if (x % 3 == 0 && Math.abs(x) <= 9) for (int z = -half + 1; z <= half - 1; z++) if (w.canon(x, z)) c(x, deck + 1, z, "spruce_slab[type=bottom]");
        }
        // the stem at this end curling up and over, a carved head on top
        int[][] stem = {{-16, 0}, {-16, 1}, {-17, 2}, {-17, 3}, {-17, 4}, {-17, 5}, {-16, 6}, {-15, 6}};
        for (int[] p : stem) c(p[0], deck + p[1], 0, "stripped_dark_oak_log[axis=y]");
        c(-15, deck + 7, 0, "dark_oak_planks");
        c(-14, deck + 6, 0, "dark_oak_fence");
        // oars locked in the ice
        for (int x = -10; x <= -2; x += 4) for (int s : new int[] {-1, 1}) {
            int z = s * 5;
            if (!w.canon(x, z)) continue;
            c(x, ICE + 1, z, "spruce_fence");
            c(x, ICE + 1, z + s, "spruce_fence");
        }
        // snow drifted up against the hull so you can climb aboard
        for (int x = -12; x <= -4; x++) for (int s : new int[] {-1, 1}) {
            int z = s * 5;
            if (!w.canon(x, z) || x % 4 == 2) continue;
            c(x, ICE + 1, z, "snow_block");
        }
        keepClear(-18, -6, 18, 6);
    }

    // ---------------------------------------------------------------- the flanks

    /** A tunnel through the glacier: a blue-ice vault with icicles and lanterns frozen into the walls. */
    void iceCave() {
        int y = SHORE;
        for (int x = -66; x <= -12; x++) {
            int z = -40 + (int) Math.round(Math.sin(x / 8.0) * 3);
            for (int dz = -3; dz <= 3; dz++) for (int dy = 0; dy <= 5; dy++) {
                double d = Math.hypot(dz / 3.2, (dy - 1.5) / 4.0);
                if (d > 1) continue;
                int gy = w.ground(x, z + dz);
                if (gy < y + 3) continue; // only under the glacier
                w.set(x, y + dy, z + dz, dy == 0 ? "packed_ice" : "air");
            }
            for (int dz : new int[] {-4, 4}) if (!w.air(x, y + 2, z + dz) && x % 7 == 0) w.set(x, y + 2, z + dz, "sea_lantern");
        }
        keepClear(-68, -46, -10, -34);
    }

    /** A hunter's lodge in the pines on the other flank: a log cabin with a porch, antlers of fences, a meat rack. */
    void lodge(int cx, int cz) {
        int y = w.ground(cx, cz);
        if (y < 60) return;
        level(cx - 6, cz - 5, cx + 6, cz + 5, y, P.of("podzol", 2, "snow_block", 1), P.of("dirt"));
        k.walls(cx - 4, cz - 3, cx + 4, cz + 3, y, 4, Kit.Bands.of(0, "cobblestone", 1, "stripped_spruce_log[axis=x]"), "spruce_log", 4, P.of("spruce_planks"));
        k.gable(cx - 4, cz - 3, cx + 4, cz + 3, y + 5, true, 1, Kit.Roof.of("dark_oak_planks"), P.of("stripped_spruce_log[axis=z]"));
        k.doorway(cx, y, cz - 3, "north", 1, "spruce_door", "spruce_stairs", "spruce_fence", "lantern");
        k.window(cx - 2, y + 2, cz - 3, "north", 1, 1, "glass_pane", "spruce_stairs", "spruce_trapdoor");
        k.window(cx + 2, y + 2, cz - 3, "north", 1, 1, "glass_pane", "spruce_stairs", "spruce_trapdoor");
        k.bed(cx + 3, y + 1, cz + 1, "east", "brown");
        w.set(cx - 3, y + 1, cz + 2, "barrel[facing=up]");
        w.set(cx - 3, y + 1, cz + 1, "smoker[facing=east,lit=false]");
        w.hangLight(cx, y + 4, cz, "lantern");
        // a drying rack outside
        for (int s : new int[] {-2, 2}) for (int dy = 1; dy <= 2; dy++) w.set(cx + 6 + s * 0, y + dy, cz + s, "spruce_fence");
        for (int dz = -2; dz <= 2; dz++) w.set(cx + 6, y + 3, cz + dz, "stripped_spruce_log[axis=z]");
        keepClear(cx - 7, cz - 6, cx + 8, cz + 6);
    }
}
