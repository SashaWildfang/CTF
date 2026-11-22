package com.sasha.ctfbuilder.v2.maps;

import com.sasha.ctfbuilder.v2.Arena;
import com.sasha.ctfbuilder.v2.Kit;
import com.sasha.ctfbuilder.v2.Land;
import com.sasha.ctfbuilder.v2.Theme;
import com.sasha.ctfbuilder.v2.W.P;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Emberforge: a volcanic island. Each team's dwarven forge hall is carved into a mountain at one end, behind a
 * monumental gate and a paved courtyard. Between them stands the volcano: the Forge Road climbs its flank on a
 * causeway, then through a gate in the rim into the caldera, where a platform sits in a ring of lava. North and
 * south of the volcano, lava rivers run out to the island's edges, crossed by a stone ford (and, for the brave,
 * stepping stones); where each river ends a dragon has climbed the edge, its claws either side of the lava, its
 * head peering over at the ford. King of the Hill uses North Ford, Caldera and South Ford.
 */
public final class Emberforge extends Arena {

    static final int Y = 70, HALL = 72, CRATER = 84, LAVA = 67, RIM_H = 96;
    static final double CONE_R = 30, RIM_R = 13, CRATER_R = 11.5;
    static final int FACE_X = -92, YARD_E = -69, FLAG_X = -78;
    static final int FORD_Z = 35, STONES_Z = 43, DRAGON_Z = 53;

    Land land;

    public Emberforge(World world) {
        super(world, "emberforge", false, Theme.get("emberforge"));
        name = "Emberforge";
        icon = "MAGMA_BLOCK";
        minutes = 18;
        kothPoints = 250;
        reach = 124;
        low = 0;
        high = 140;
        voidY = 12;
        spectator = new double[] {0.5, 118, 0.5, -90, 40};
    }

    // ---------------------------------------------------------------- the land

    double riverX(int z) {
        return 2.5 * Math.sin(z / 6.5);
    }

    /** Half the width of the lava at z: a rounded head at the volcano's foot, a basin under the dragon. */
    double riverHalf(int z) {
        double az = Math.abs(z);
        if (az < 27 || az > 52) return -1;
        if (az < 29.6) return Math.sqrt(Math.max(0, 2.6 * 2.6 - (29.6 - az) * (29.6 - az)));
        double basin = 3.6 * 3.6 - (az - 48) * (az - 48);
        return basin > 2.6 * 2.6 ? Math.sqrt(basin) : az > 48 ? (basin > 0 ? Math.sqrt(basin) : -1) : 2.6;
    }

    /** How far (x, z) is from the lava (negative inside it). */
    double riverD(int x, int z) {
        double hw = riverHalf(z);
        return hw <= 0 ? 99 : Math.abs(x - riverX(z)) - hw;
    }

    double coneH(int x, int z) {
        double d = Math.hypot(x, z);
        double th = Math.atan2(z, x);
        double dd = d * (1 + 0.05 * Math.sin(th * 7 + w.fbm(d * 0.2, th * 3, 3, 1) * 2) + w.fbm(x * 0.7, z * 0.7, 8, 2) * 0.03);
        if (dd >= CONE_R) return Y;
        if (dd >= RIM_R) return Y + (RIM_H - Y) * Math.pow((CONE_R - dd) / (CONE_R - RIM_R), 1.5);
        if (dd >= CRATER_R) return CRATER + (RIM_H - CRATER) * Land.smooth((dd - CRATER_R) / (RIM_R - CRATER_R));
        return CRATER;
    }

    double height(int x, int z) {
        double ax = -x, az = Math.abs(z);
        double rag = ax < 22 ? 0 : w.fbm(x * 0.5, z * 0.5, 12, 2) * 0.05 * Land.smooth((ax - 22) / 10);
        if (Math.pow(ax / 114.0, 2.6) + Math.pow(az / 53.5, 2.6) + rag > 1) return Double.NaN;
        double h = Y;
        int face = FACE_X + (az > 14 ? (int) Math.round(w.fbm(z * 1.1, 400, 6, 1)) : 0);
        if (x <= face) {
            // the mountain the hall is carved into: a sheer face, a rough crest behind it
            double mx = face - x;
            double crag = Math.pow(Math.max(0, 1 - Math.abs(w.fbm(x * 0.8 + 40, z * 0.8, 9, 2)) * 2.2), 2);
            h = HALL + 15 + 8 * Land.smooth(mx / 12.0) + crag * 7 * Land.smooth(mx / 4.0) + w.fbm(x * 1.2, z * 1.2, 7, 2) * 1.5;
        } else if (x <= -58 && az > 19) {
            // the spurs either side of the courtyard, falling away east into foothills
            double base = x <= YARD_E ? HALL : Y;
            double amp = 12 * Land.smooth((-58 - x) / 18.0) + w.fbm(x * 0.9, z * 0.9, 6, 2) * 1.5;
            h = Math.max(base, base + amp * Land.smooth((az - 19) / 3.0));
        } else if (x <= YARD_E) {
            h = HALL;
        }
        h = Math.max(h, coneH(x, z));
        if (riverD(x, z) < 0) h = LAVA - 1;
        return h;
    }

    String surface(int x, int z, int y, int steep, double edge) {
        double big = w.fbm(x + 913, z - 377, 14, 2), fine = w.n2(x * 1.9, z * 1.9, 2);
        if (y < Y - 1) return "magma_block";
        if (steep >= 3) return rock(x, y, z);
        double d = Math.hypot(x, z);
        if (d < CONE_R + 1 && y > Y) {
            // the cone: ash and cinder in bands, glowing fissures running down it
            double th = Math.atan2(z, x);
            double fis = Math.abs(Math.sin(th * 6 + w.fbm(d * 0.15, th * 2, 3, 1) * 1.5));
            if (fis < 0.07 && d > RIM_R + 1.5) return "magma_block";
            int band = Math.floorMod((int) Math.floor((y + w.fbm(x * 0.5, z * 0.5, 10, 1) * 1.5) / 2.0), 4);
            return band == 0 ? "tuff" : band == 1 ? (fine > 0 ? "basalt[axis=y]" : "smooth_basalt") : band == 2 ? "blackstone" : fine > 0.3 ? "gravel" : "tuff";
        }
        if (x <= FACE_X + 1 || y > HALL + 1) return fine > 0.45 ? "cobbled_deepslate" : fine < -0.5 ? "andesite" : "tuff";
        double rd = riverD(x, z);
        if (rd >= 0 && rd < 1.6) return fine > 0.3 ? "obsidian" : fine < -0.3 ? "polished_basalt[axis=y]" : "blackstone";
        if (rd >= 1.6 && rd < 5 && big + fine * 0.4 > 0.15) return "crimson_nylium";
        // the ash plain: dark cinder near the volcano grading out to pale ash toward the halls, finely mixed
        double heat = Land.smooth(1 - (d - 31) / 34.0) + big * 0.18 + fine * 0.08;
        if (heat > 0.72) return fine > 0.15 ? "basalt[axis=y]" : "blackstone";
        if (heat > 0.48) return fine > 0.3 ? "tuff" : "smooth_basalt";
        if (heat > 0.24) return fine > 0.42 ? "gravel" : fine < -0.45 ? "andesite" : "tuff";
        return fine > 0.35 ? "light_gray_concrete_powder" : fine < -0.3 ? "tuff" : "andesite";
    }

    static final String[] LAYERS = {"basalt[axis=y]", "tuff", "blackstone", "smooth_basalt", "deepslate", "tuff", "MAGMA", "basalt[axis=y]",
            "cobbled_deepslate", "blackstone", "tuff", "red_terracotta"};
    static final int[] THICK = {3, 2, 2, 2, 3, 1, 1, 3, 2, 2, 2, 1};
    static final int CYCLE = 24;

    /** Volcanic strata, a seam of glowing magma in each cycle (broken up), the odd block borrowing its neighbour's colour. */
    String rock(int x, int y, int z) {
        double warp = w.fbm(x * 0.5 + 11, z * 0.5 - 7, 30, 2) * 2.2;
        int at = Math.floorMod((int) Math.floor(y + warp), CYCLE);
        int i = 0;
        while (at >= THICK[i]) at -= THICK[i++];
        double tex = w.n3(x * 1.9, y * 1.9, z * 1.9, 4);
        if (tex > 0.62) i = (i + 1) % LAYERS.length;
        else if (tex < -0.64) i = (i + LAYERS.length - 1) % LAYERS.length;
        String s = LAYERS[i];
        if (s.equals("MAGMA")) return w.fbm(x * 0.8, z * 0.8 + y, 6, 1) > 0.1 ? "magma_block" : "blackstone";
        return s;
    }

    int bottom(int x, int z, int top, double edge) {
        double t = Land.smooth(Math.min(1, edge / 26.0));
        return (int) Math.round(62 - 24 * t + w.fbm(x * 0.9 + 5, z * 0.9 - 5, 18, 2) * 2.5 * Math.min(1, edge / 8.0));
    }

    @Override
    protected void build() {
        land = new Land(w, t, -122, 122, -60, 60);
        land.top = this::height;
        land.surface = this::surface;
        land.rock = this::rock;
        land.bottom = this::bottom;
        land.soil = P.of("tuff", 2, "basalt[axis=y]", 1);
        land.build();
        // the lava rivers
        for (int x = -12; x <= 0; x++) for (int z = -56; z <= 56; z++) {
            if (!w.canon(x, z) || riverD(x, z) >= 0) continue;
            w.set(x, LAVA, z, "lava");
            w.set(x, LAVA - 1, z, "magma_block");
        }

        hall(0);
        facade(0);
        courtyard();
        road();
        crater();
        for (int s : new int[] {-1, 1}) {
            ford(s);
            stones(s);
            track(s);
        }
        dragon();
        watchtower(-50, -38);
        smelter(-50, 38);
        for (int[] p : new int[][] {{-58, 10, 6}, {-46, -14, 5}, {-38, 45, 7}, {-30, -43, 5}, {-62, 26, 4}}) organPipes(p[0], p[1], p[2]);
        for (int[] p : new int[][] {{-61, -33}, {-42, 21}, {-64, -10}}) vent(p[0], p[1]);
        for (int[] p : new int[][] {{-40, -8}, {-35, 15}, {-70, 33}}) boulder(p[0], p[1]);
        for (int[] p : new int[][] {{-54, 18}, {-44, -27}, {-24, 36}, {-66, -36}}) charred(p[0], p[1]);
        rimSmoke();
        plants(P.of("crimson_roots", 4, "nether_sprouts", 3, "crimson_fungus", 1), 0.3, (x, z) -> w.type(x, land.topAt(x, z), z).name().equals("CRIMSON_NYLIUM"));

        spawn = new double[] {-104.5, HALL + 1, 0.5, -90};
        flag = new int[] {FLAG_X, HALL + 1, 0};
        pads.add(new int[] {-82, HALL + 1, -12});
        pads.add(new int[] {-82, HALL + 1, 12});
        hills.add(new Hill("North Ford", -3, Y + 1, -FORD_Z - 3, 3, Y + 4, -FORD_Z + 3));
        hills.add(new Hill("Caldera", -3, CRATER + 2, -3, 3, CRATER + 5, 3));
        hills.add(new Hill("South Ford", -3, Y + 1, FORD_Z - 3, 3, Y + 4, FORD_Z + 3));
    }

    // ---------------------------------------------------------------- the forge hall

    /**
     * The hall inside the mountain: a stepped vault on two rows of pillars, forges between them with lava behind
     * glass in the walls, chandeliers, banners, the team's runner down the middle and a throne dais at the back.
     */
    void hall(int cz) {
        int y = HALL, x0 = -110, x1 = FACE_X - 1, z0 = cz - 9, z1 = cz + 9;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            int az = Math.abs(z - cz);
            boolean wall = x == x0 || z == z0 || z == z1;
            int ceil = az >= 7 ? 9 : az >= 4 ? 10 : 11;
            w.set(x, y, z, wall ? "polished_blackstone_bricks" : az <= 1 ? "polished_deepslate" : az == 2 || az == 5 ? "polished_blackstone_bricks"
                    : ((x + z) & 1) == 0 ? "deepslate_tiles" : "polished_deepslate");
            for (int dy = 1; dy <= 12; dy++) {
                String spec;
                if (wall) spec = dy == 1 ? "polished_blackstone" : dy % 4 == 0 ? "chiseled_polished_blackstone" : "polished_blackstone_bricks";
                else if (dy < ceil) spec = "air";
                else if (dy == ceil) spec = "deepslate_tiles";
                else spec = "deepslate_bricks";
                w.set(x, y + dy, z, spec);
            }
            if (!wall && (az == 6 || az == 3)) w.set(x, y + ceil, z, "deepslate_tile_stairs[half=top,facing=" + (z > cz ? "south" : "north") + "]");
            if (!wall && az <= 1 && x > x0 + 3) w.set(x, y + 1, z, "TEAM_carpet");
        }
        // two rows of pillars with copper bands and carved capitals; the team's banners on their inner faces
        for (int x = x0 + 4; x <= x1 - 2; x += 4) for (int s : new int[] {-1, 1}) {
            int z = cz + s * 5;
            for (int dy = 1; dy <= 9; dy++) w.set(x, y + dy, z, dy == 1 || dy == 9 ? "chiseled_polished_blackstone" : dy == 4 ? "waxed_cut_copper" : "polished_blackstone_bricks");
            w.set(x, y + 6, z - s, "TEAM_wall_banner[facing=" + (s < 0 ? "south" : "north") + "]");
        }
        // forges between the pillars: a blast furnace, lava behind glass in the wall, an anvil, chains overhead
        for (int x = x0 + 6; x <= x1 - 4; x += 4) for (int s : new int[] {-1, 1}) {
            int wz = s < 0 ? z0 : z1, iz = wz - s;
            String in = s < 0 ? "south" : "north";
            w.set(x - 1, y + 1, iz, "blast_furnace[facing=" + in + ",lit=true]");
            w.set(x, y + 1, iz, "anvil[facing=east]");
            w.set(x + 1, y + 1, iz, "barrel[facing=up]");
            for (int dy = 2; dy <= 3; dy++) {
                w.set(x, y + dy, wz, "glass");
                w.set(x, y + dy, wz + s, "lava");
            }
            for (int[] o : new int[][] {{-1, 2}, {-1, 3}, {1, 2}, {1, 3}}) w.set(x + o[0], y + o[1], wz + s, "polished_blackstone");
            w.set(x, y + 1, wz + s, "polished_blackstone");
            w.set(x, y + 4, wz + s, "polished_blackstone");
            for (int dy = 2; dy <= 3; dy++) w.set(x, y + dy, wz + 2 * s, "polished_blackstone");
            for (int dy = 5; dy < 9; dy++) w.set(x, y + dy, iz, "iron_chain[axis=y]");
            w.set(x, y + 4, iz, "lantern[hanging=true]");
        }
        for (int x = x0 + 6; x <= x1 - 2; x += 4) w.hangLight(x, y + 8, cz, "lantern");
        // the throne dais at the back
        for (int x = x0 + 1; x <= x0 + 3; x++) for (int z = cz - 4; z <= cz + 4; z++) w.set(x, y + 1, z, x == x0 + 3 ? "polished_blackstone_brick_slab[type=bottom]" : "polished_blackstone_bricks");
        w.set(x0 + 1, y + 2, cz, "polished_blackstone_stairs[facing=west,half=bottom]");
        for (int s : new int[] {-1, 1}) {
            w.set(x0 + 1, y + 2, cz + s, "polished_blackstone_wall");
            w.set(x0 + 1, y + 3, cz + s, "lantern");
        }
        w.set(x0 + 1, y + 5, cz, "TEAM_wall_banner[facing=east]");
        keepClear(x0 - 2, z0 - 2, x1 + 2, z1 + 2);
    }

    /** The gate in the mountain's face: dressed stone, a pointed arch, great pillars with fire on top, banners, a stepped crown. */
    void facade(int cz) {
        int y = HALL, x = FACE_X, ox = x + 1;
        for (int dz = -13; dz <= 13; dz++) for (int dy = 0; dy <= 18; dy++) {
            int az = Math.abs(dz);
            int limit = 18 - Math.max(0, az - 5);
            if (dy > limit) continue;
            String spec = dy == 0 ? "polished_blackstone" : dy == limit ? "polished_blackstone_bricks"
                    : dy == 6 || dy == 13 ? "chiseled_deepslate" : az % 2 == 0 ? "deepslate_bricks" : "polished_deepslate";
            w.set(x, y + dy, cz + dz, spec);
        }
        // the gateway: a pointed arch, open right through into the hall
        for (int dz = -2; dz <= 2; dz++) for (int dy = 1; dy <= 8; dy++) {
            boolean open = dy <= 6 || dy == 7 && Math.abs(dz) <= 1 || dy == 8 && dz == 0;
            if (open) w.set(x, y + dy, cz + dz, "air");
        }
        for (int s : new int[] {-1, 1}) {
            w.set(x, y + 7, cz + 2 * s, "polished_blackstone_brick_stairs[half=top,facing=" + (s < 0 ? "north" : "south") + "]");
            w.set(x, y + 8, cz + s, "polished_blackstone_brick_stairs[half=top,facing=" + (s < 0 ? "north" : "south") + "]");
        }
        w.set(x, y + 9, cz, "chiseled_polished_blackstone");
        // the great pillars either side of the gate, standing proud of the face, a fire burning on each
        for (int s : new int[] {-1, 1}) for (int dz = 4; dz <= 5; dz++) for (int dy = 0; dy <= 13; dy++) {
            String spec = dy <= 1 ? "chiseled_polished_blackstone" : dy == 4 || dy == 9 ? "waxed_cut_copper" : dy == 13 ? "chiseled_polished_blackstone" : "polished_blackstone_bricks";
            w.set(x, y + dy, cz + s * dz, spec);
            w.set(ox, y + dy, cz + s * dz, spec);
        }
        for (int s : new int[] {-1, 1}) {
            w.set(ox, y + 14, cz + s * 4, "campfire[lit=true]");
            w.set(ox, y + 14, cz + s * 5, "polished_blackstone_brick_slab[type=bottom]");
            w.set(x, y + 14, cz + s * 4, "polished_blackstone_brick_slab[type=bottom]");
            w.set(x, y + 14, cz + s * 5, "polished_blackstone_brick_slab[type=bottom]");
            // banners between the pillars and the gate, lamps on arms beyond them
            w.set(ox, y + 8, cz + s * 3, "TEAM_wall_banner[facing=east]");
            w.set(ox, y + 6, cz + s * 7, "polished_blackstone_wall");
            w.set(ox, y + 5, cz + s * 7, "lantern[hanging=true]");
            // the outer pillars at the face's edges
            for (int dy = 0; dy <= 9; dy++) w.set(ox, y + dy, cz + s * 10, dy == 9 ? "chiseled_polished_blackstone" : dy % 3 == 0 ? "polished_blackstone" : "polished_blackstone_bricks");
            w.set(ox, y + 10, cz + s * 10, "lantern");
            // slit windows high up, lit from behind
            for (int dy = 15; dy <= 16; dy++) {
                w.set(x, y + dy, cz + s * 2, "orange_stained_glass");
                w.set(x - 1, y + dy, cz + s * 2, "shroomlight");
            }
        }
        // a cornice over the gate
        for (int dz = -3; dz <= 3; dz++) {
            w.set(ox, y + 11, cz + dz, "polished_blackstone_brick_stairs[half=top,facing=west]");
            w.set(ox, y + 12, cz + dz, "polished_blackstone_brick_slab[type=bottom]");
        }
    }

    /** The courtyard before the gate: patterned paving, the flag's dais, braziers, steps down to the ash plain. */
    void courtyard() {
        int y = HALL;
        for (int x = FACE_X + 1; x <= YARD_E; x++) for (int z = -19; z <= 19; z++) {
            int az = Math.abs(z);
            double fd = Math.hypot(x - FLAG_X, z);
            String p;
            if (fd < 1) p = "waxed_cut_copper";
            else if (fd < 2.5) p = "polished_deepslate";
            else if (fd < 3.5) p = "chiseled_polished_blackstone";
            else if (az <= 2) p = az == 2 ? "deepslate_tiles" : "polished_deepslate";
            else if (az == 19 || x == FACE_X + 1) p = "polished_blackstone";
            else if (Math.abs(fd - 7) < 0.5) p = "deepslate_tiles";
            else if (az >= 17 || x <= FACE_X + 3 || x >= YARD_E - 1) p = "tuff_bricks";
            else p = w.n2(x * 1.6, z * 1.6, 2) > 0.5 ? "tuff_bricks" : "polished_tuff";
            w.set(x, y, z, p);
            for (int dy = 1; dy <= 12; dy++) if (!w.air(x, y + dy, z) && !(x == FACE_X + 1 && az <= 13)) w.set(x, y + dy, z, "air");
        }
        // steps down: a broad flight on the road, narrower ones toward the fords, a balustrade between
        for (int z = -19; z <= 19; z++) {
            int az = Math.abs(z);
            boolean steps = az <= 6 || az >= 14 && az <= 16;
            if (steps) {
                w.set(YARD_E + 1, y, z, "polished_blackstone_brick_stairs[facing=west,half=bottom]");
                w.set(YARD_E + 2, y - 1, z, "polished_blackstone_brick_stairs[facing=west,half=bottom]");
                for (int dy = 1; dy <= 4; dy++) {
                    w.set(YARD_E + 1, y + dy, z, "air");
                    w.set(YARD_E + 2, y - 1 + dy, z, "air");
                }
            } else {
                w.set(YARD_E, y + 1, z, "polished_blackstone_wall");
                w.set(YARD_E + 1, y, z, "polished_blackstone_bricks");
                w.set(YARD_E + 1, y - 1, z, "polished_blackstone_bricks");
            }
        }
        for (int s : new int[] {-1, 1}) for (int az : new int[] {7, 13, 17}) {
            int z = s * az;
            w.set(YARD_E, y + 1, z, "chiseled_polished_blackstone");
            w.set(YARD_E, y + 2, z, "lantern");
        }
        // braziers round the yard
        for (int[] b : new int[][] {{-88, -15}, {-88, 15}, {-73, -16}, {-73, 16}, {-86, -5}, {-86, 5}}) {
            w.set(b[0], y + 1, b[1], "chiseled_polished_blackstone");
            w.set(b[0], y + 2, b[1], "polished_blackstone_wall");
            w.set(b[0], y + 3, b[1], "campfire[lit=true]");
        }
        keepClear(FACE_X - 1, -21, YARD_E + 3, 21);
    }

    // ---------------------------------------------------------------- the road and the volcano

    /**
     * The Forge Road: paved from the courtyard steps to the volcano, then up its flank half a block a step, on a
     * walled causeway where the slope is low and in a cutting where it rises, through a gate in the crater's rim.
     */
    void road() {
        for (int x = YARD_E + 3; x <= -40; x++) for (int z = -3; z <= 3; z++) {
            int az = Math.abs(z);
            w.set(x, Y, z, az == 3 ? "polished_blackstone" : az == 0 ? "polished_deepslate" : ((x + z) & 1) == 0 ? "polished_blackstone_bricks" : "deepslate_tiles");
            for (int dy = 1; dy <= 5; dy++) if (!w.air(x, Y + dy, z)) w.set(x, Y + dy, z, "air");
            if (az == 3 && Math.floorMod(x, 7) == 0) {
                for (int dy = 1; dy <= 2; dy++) w.set(x, Y + dy, z, "polished_basalt[axis=y]");
                w.set(x, Y + 3, z, "lantern");
            }
        }
        int portal = Integer.MIN_VALUE;
        for (int x = -39; x <= -11; x++) {
            double s = Y + 1 + 0.5 * (x + 39);            // where you walk
            int k = (int) Math.floor(s);
            boolean half = s - k > 0.25;
            int top = half ? k : k - 1;                   // the highest block of the road (a slab if half)
            int terrain = land.topAt(x, 0);
            boolean tunnel = terrain >= k + 6;
            if (tunnel && portal == Integer.MIN_VALUE) portal = x;
            for (int z = -3; z <= 3; z++) {
                int az = Math.abs(z);
                // solid under the road down to the ground
                for (int yy = top - (half ? 1 : 0); yy > Y - 2 && (yy >= land.topAt(x, z) || az == 3 || yy >= top - 2); yy--)
                    if (yy != top || !half) w.set(x, yy, z, az == 3 ? "polished_blackstone_bricks" : "polished_blackstone");
                if (az < 3) {
                    if (half) w.set(x, top, z, "polished_blackstone_brick_slab[type=bottom]");
                    else w.set(x, top, z, az == 0 ? "polished_deepslate" : "polished_blackstone_bricks");
                    for (int yy = top + 1; yy <= (tunnel ? k + 4 : Math.max(k + 4, land.topAt(x, z) + 1)); yy++) w.set(x, yy, z, "air");
                } else {
                    // the sides: a parapet on the causeway, a dressed wall in the cutting and the tunnel
                    int side = land.topAt(x, z);
                    if (tunnel) for (int yy = k; yy <= k + 4; yy++) w.set(x, yy, z, "polished_blackstone_bricks");
                    else if (side < k) w.set(x, k, z, "polished_blackstone_wall");
                    else for (int yy = k; yy <= Math.min(side, k + 3); yy++) w.set(x, yy, z, "polished_blackstone_bricks");
                }
            }
            if (tunnel) {
                for (int z = -2; z <= 2; z++) w.set(x, k + 5, z, "polished_basalt[axis=z]");
                if ((x - portal) % 4 == 2) {
                    for (int s2 : new int[] {-3, 3}) for (int yy = k; yy <= k + 4; yy++) w.set(x, yy, s2, "polished_basalt[axis=y]");
                    w.set(x, k + 4, 0, "lantern[hanging=true]");
                }
            } else if (Math.floorMod(x, 6) == 0 && land.topAt(x, 3) < k) {
                w.set(x, k + 1, 3, "lantern");
                w.set(x, k + 1, -3, "lantern");
            }
        }
        // the gate into the rim: pillars, a lintel, fires
        if (portal != Integer.MIN_VALUE) {
            int px = portal - 1, k = (int) Math.floor(Y + 1 + 0.5 * (px + 39));
            for (int s : new int[] {-1, 1}) for (int dz = 3; dz <= 4; dz++) for (int yy = k - 1; yy <= k + 6; yy++)
                w.set(px, yy, s * dz, yy == k + 6 ? "chiseled_polished_blackstone" : yy % 3 == 0 ? "waxed_cut_copper" : "polished_blackstone_bricks");
            for (int z = -4; z <= 4; z++) {
                w.set(px, k + 5, z, Math.abs(z) <= 2 ? "polished_blackstone_brick_stairs[half=top,facing=east]" : "polished_blackstone_bricks");
                w.set(px, k + 6, z, z == 0 ? "chiseled_polished_blackstone" : "polished_blackstone_bricks");
            }
            for (int s : new int[] {-1, 1}) {
                w.set(px - 1, k + 4, s * 4, "polished_blackstone_wall");
                w.set(px - 1, k + 3, s * 4, "lantern[hanging=true]");
            }
        }
        keepClear(YARD_E + 2, -5, -10, 5);
    }

    /** The caldera: a floor round the crater wall, a moat of lava, the platform in the middle, a bridge from each gate. */
    void crater() {
        for (int x = -12; x <= 0; x++) for (int z = -12; z <= 12; z++) {
            if (!w.canon(x, z)) continue;
            double d = Math.hypot(x, z);
            if (d > CRATER_R) continue;
            for (int dy = 1; dy <= 14; dy++) w.set(x, CRATER + dy, z, "air");
            if (d <= 6.5) {
                w.set(x, CRATER + 1, z, d > 5.6 ? "polished_blackstone_bricks" : d < 1.5 ? "chiseled_polished_blackstone"
                        : ((x + z) & 1) == 0 ? "polished_deepslate" : "polished_blackstone");
                for (int yy = CRATER; yy > CRATER - 4; yy--) w.set(x, yy, z, "blackstone");
            } else if (d <= 8.9) {
                w.set(x, CRATER, z, "air");
                w.set(x, CRATER - 1, z, "lava");
                w.set(x, CRATER - 2, z, "magma_block");
            } else {
                w.set(x, CRATER, z, d > 10.6 ? "basalt[axis=y]" : ((x * 7 + z * 3) & 3) == 0 ? "polished_basalt[axis=y]" : "blackstone");
            }
        }
        // the bridge over the moat, half a step up from the floor
        for (int z = -2; z <= 2; z++) {
            w.set(-10, CRATER + 1, z, Math.abs(z) == 2 ? "polished_blackstone_bricks" : "polished_blackstone_brick_slab[type=bottom]");
            for (int x = -9; x <= -6; x++) {
                w.set(x, CRATER + 1, z, Math.abs(z) == 2 ? "polished_blackstone_bricks" : "polished_deepslate");
                w.set(x, CRATER, z, "polished_blackstone_bricks");
                if (Math.abs(z) == 2) w.set(x, CRATER + 2, z, "polished_blackstone_wall");
            }
        }
        // four pillars round the platform, lamps on top; smoke from niches in the crater wall
        for (int[] p : new int[][] {{-4, -4}, {-4, 4}}) {
            for (int dy = 2; dy <= 5; dy++) w.set(p[0], CRATER + dy, p[1], dy == 5 ? "chiseled_polished_blackstone" : "polished_basalt[axis=y]");
            w.set(p[0], CRATER + 6, p[1], "lantern");
        }
        for (int[] p : new int[][] {{0, -11}, {-8, -8}, {-8, 8}}) {
            int x = p[0], z = p[1];
            int nx = (int) Math.round(x * 1.12), nz = (int) Math.round(z * 1.12);
            w.set(nx, CRATER - 1, nz, "hay_block");
            w.set(nx, CRATER, nz, "campfire[lit=true,signal_fire=true]");
            for (int dy = 2; dy <= 4; dy++) w.set(nx, CRATER + dy, nz, "air");
        }
        keepClear(-13, -13, 13, 13);
    }

    /** Signal fires on the rim: the volcano smokes. */
    void rimSmoke() {
        for (double deg : new double[] {150, 210, 120}) {
            double a = Math.toRadians(deg);
            int x = (int) Math.round(Math.cos(a) * (RIM_R + 0.6)), z = (int) Math.round(Math.sin(a) * (RIM_R + 0.6));
            if (!w.canon(x, z)) continue;
            int top = land.topAt(x, z);
            w.set(x, top - 1, z, "hay_block");
            w.set(x, top, z, "campfire[lit=true,signal_fire=true]");
        }
    }

    // ---------------------------------------------------------------- the rivers

    /** The ford: a thick stone bridge over the lava, an arch under each side, parapets, braziers at its ends. */
    void ford(int s) {
        int zc = s * FORD_Z;
        for (int x = -9; x <= 0; x++) for (int dz = -3; dz <= 3; dz++) {
            int z = zc + dz;
            if (!w.canon(x, z)) continue;
            int adz = Math.abs(dz);
            boolean over = riverD(x, z) < 0.5;
            for (int yy = Y - 2; yy <= Y; yy++) w.set(x, yy, z, adz == 3 ? "polished_blackstone_bricks" : "polished_blackstone");
            w.set(x, Y, z, adz == 3 ? "polished_blackstone_bricks" : adz == 0 ? "waxed_cut_copper" : ((x + dz) & 1) == 0 ? "polished_deepslate" : "deepslate_tiles");
            if (adz == 3) {
                w.set(x, Y + 1, z, "polished_blackstone_wall");
                if (over) w.set(x, Y - 2, z, "polished_blackstone_brick_stairs[half=top,facing=" + (dz < 0 ? "south" : "north") + "]");
            }
            for (int dy = (adz == 3 ? 2 : 1); dy <= 5; dy++) if (!w.air(x, Y + dy, z)) w.set(x, Y + dy, z, "air");
        }
        for (int dz : new int[] {-3, 3}) {
            int z = zc + dz;
            if (!w.canon(-9, z)) continue;
            w.set(-9, Y + 1, z, "chiseled_polished_blackstone");
            w.set(-9, Y + 2, z, "polished_blackstone_wall");
            w.set(-9, Y + 3, z, "campfire[lit=true]");
        }
        keepClear(-11, zc - 5, 0, zc + 5);
    }

    /** Stepping stones: two basalt columns across the lava with a one-block gap between them. */
    void stones(int s) {
        int zc = s * STONES_Z;
        for (int z = zc - 1; z <= zc; z++) {
            double rx = riverX(z);
            for (int x = (int) Math.round(rx - 2); x <= (int) Math.round(rx + 2); x++) {
                if (!w.canon(x, z) || Math.abs(x - rx) < 0.6 || riverD(x, z) >= 0) continue;
                for (int yy = LAVA - 1; yy <= Y; yy++) w.set(x, yy, z, yy == Y ? "polished_basalt[axis=y]" : "basalt[axis=y]");
            }
        }
    }

    /** A worn track from the courtyard's side steps round the volcano to the ford. */
    void track(int s) {
        double[] a = {YARD_E + 3, s * 15}, b = {-46, s * 34}, c = {-10, s * FORD_Z};
        for (double t = 0; t <= 1; t += 0.004) {
            double u = 1 - t;
            double px = u * u * a[0] + 2 * u * t * b[0] + t * t * c[0], pz = u * u * a[1] + 2 * u * t * b[1] + t * t * c[1];
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                int x = (int) Math.round(px) + dx, z = (int) Math.round(pz) + dz;
                if (Math.hypot(x - px, z - pz) > 1.7 || !w.canon(x, z) || land.topAt(x, z) != Y || riverD(x, z) < 2) continue;
                if (!w.type(x, Y + 1, z).isAir()) continue;
                double n = w.n2(x * 2.7, z * 2.7, 2);
                w.set(x, Y, z, n > 0.35 ? "cobbled_deepslate" : n < -0.35 ? "tuff" : "gravel");
            }
        }
        keepClear((int) a[0], Math.min(s * 13, s * 37), -9, Math.max(s * 13, s * 37));
    }

    // ---------------------------------------------------------------- the dragons

    /**
     * The dragon at the end of each river, climbed up over the edge: its forelegs reach over the rim either side of
     * the lava and its claws dig into the ash, its head on a long neck peers over at the ford, smoke curling from
     * its nostrils; behind it the wings rise, red membranes between black bones, and its body and tail hang down
     * the island's side. Built once at the north edge; its copy holds the south.
     */
    void dragon() {
        int ox = 0, oy = Y, oz = -DRAGON_Z;   // local (u, f, v): u = x, f = toward the map (+z), v = up from the rim
        List<double[]> neck = new ArrayList<>();   // (u, f, v, r) along the neck
        double[] p0 = {0, -9, -15}, p1 = {0, -6, -2}, p2 = {0, -2, 6}, p3 = {0, 1.5, 9.4};
        for (int i = 0; i <= 30; i++) {
            double t = i / 30.0, a = (1 - t) * (1 - t) * (1 - t), b = 3 * (1 - t) * (1 - t) * t, c = 3 * (1 - t) * t * t, d = t * t * t;
            neck.add(new double[] {a * p0[0] + b * p1[0] + c * p2[0] + d * p3[0], a * p0[1] + b * p1[1] + c * p2[1] + d * p3[1],
                    a * p0[2] + b * p1[2] + c * p2[2] + d * p3[2], 3.6 - 0.8 * t});
        }
        // capsule parts: {u1, f1, v1, r1, u2, f2, v2, r2, kind}; kinds: 0 scale, 1 belly side, 2 bone, 3 wing bone
        List<double[]> caps = new ArrayList<>();
        for (int i = 0; i + 1 < neck.size(); i++) {
            double[] q = neck.get(i), r = neck.get(i + 1);
            caps.add(new double[] {q[0], q[1], q[2], q[3], r[0], r[1], r[2], r[3], 1});
        }
        caps.add(new double[] {0, 5.5, 10.6, 3.0, 0, 12.8, 9.0, 1.7, 0});        // the upper jaw
        caps.add(new double[] {0, 4.5, 7.2, 2.0, 0, 12.0, 4.0, 1.2, 1});        // the lower jaw
        caps.add(new double[] {0, -8, -21, 4.6, 0, -5, -30, 3.6, 1});           // the tail, curling under the island
        caps.add(new double[] {0, -5, -30, 3.6, 3, -1, -36, 2.6, 1});
        caps.add(new double[] {3, -1, -36, 2.6, 8, 2, -39, 1.8, 1});
        caps.add(new double[] {8, 2, -39, 1.8, 13, 1, -40, 1.0, 1});
        for (int s : new int[] {-1, 1}) {
            // horns sweeping back, spikes off the cheeks
            caps.add(new double[] {s * 2.2, 2.0, 13.6, 1.3, s * 3.8, -2.0, 17.2, 0.95, 2});
            caps.add(new double[] {s * 3.8, -2.0, 17.2, 0.95, s * 4.6, -6.5, 18.4, 0.4, 2});
            caps.add(new double[] {s * 3.4, 2.5, 9.8, 0.85, s * 6.0, -0.4, 10.6, 0.6, 2});
            // forelegs: shoulder, elbow out over the drop, forearm over the rim, the hand flat on the ash
            caps.add(new double[] {s * 6.5, -6, -6, 2.3, s * 11, -2.5, 1.2, 1.9, 0});
            caps.add(new double[] {s * 11, -2.5, 1.2, 1.9, s * 10, 1.8, 1.4, 1.5, 0});
            for (double o : new double[] {-1.6, 0, 1.6}) {
                caps.add(new double[] {s * 10 + o, 4.0, 1.3, 0.75, s * 10 + o * 1.5, 7.2, 1.1, 0.55, 0});
                caps.add(new double[] {s * 10 + o * 1.5, 7.2, 1.1, 0.5, s * 10 + o * 1.6, 8.3, 0.6, 0.4, 2});
            }
            caps.add(new double[] {s * 8.2, 2.6, 1.3, 0.7, s * 6.6, 4.6, 1.1, 0.5, 0});
            caps.add(new double[] {s * 6.6, 4.6, 1.1, 0.45, s * 6.0, 5.4, 0.6, 0.35, 2});
            // hind legs hanging under the body
            caps.add(new double[] {s * 5, -10, -17, 2.4, s * 7, -6, -23, 1.7, 0});
            caps.add(new double[] {s * 7, -6, -23, 1.7, s * 6, -3, -29, 1.3, 0});
            caps.add(new double[] {s * 6, -3, -29, 1.0, s * 6, 0, -30, 0.6, 2});
            // the wing: arm and fingers
            caps.add(new double[] {s * 5.5, -8.5, -4, 1.1, s * 13, -10, 8, 0.85, 3});
            caps.add(new double[] {s * 13, -10, 8, 0.85, s * 19, -9, 15.5, 0.65, 3});
            caps.add(new double[] {s * 19, -9, 15.5, 0.6, s * 20, -9, 17.5, 0.35, 2});
            for (double[] tip : wingTips(s)) caps.add(new double[] {s * 19, -9, 15.5, 0.5, tip[0], tip[1], tip[2], 0.3, 3});
        }
        // ellipsoids: {u, f, v, ru, rf, rv, kind}
        List<double[]> ells = new ArrayList<>();
        ells.add(new double[] {0, 3.4, 11.0, 4.0, 4.4, 3.4, 0});    // the skull
        ells.add(new double[] {0, -9, -13, 6.5, 5.5, 9, 1});        // the chest and body
        for (int s : new int[] {-1, 1}) {
            ells.add(new double[] {s * 2.8, 6.0, 13.2, 1.5, 2.2, 1.0, 0});    // brows
            ells.add(new double[] {s * 10, 3.0, 1.3, 2.3, 2.2, 0.9, 0});      // the hand
        }
        // the wing membranes, as triangles: {a, b, c} each (u, f, v), and whether the far edge is scalloped
        List<double[][]> tris = new ArrayList<>();
        for (int s : new int[] {-1, 1}) {
            double[] root = {s * 5.5, -8.5, -4}, elbow = {s * 13, -10, 8}, wrist = {s * 19, -9, 15.5};
            double[][] tip = wingTips(s);
            for (int i = 0; i + 1 < tip.length; i++) tris.add(new double[][] {wrist, tip[i], tip[i + 1], {1}});
            tris.add(new double[][] {elbow, wrist, tip[tip.length - 1], {0}});
            tris.add(new double[][] {root, elbow, tip[tip.length - 1], {0}});
            tris.add(new double[][] {root, tip[tip.length - 1], {s * 4.5, -9.5, -13}, {0}});
        }

        P scales = P.of("deepslate_tiles", 6, "cracked_deepslate_tiles", 1, "polished_blackstone", 1);
        Map<Long, String> body = new HashMap<>();
        for (int u = -32; u <= 32; u++) for (int f = -31; f <= 17; f++) for (int v = -45; v <= 21; v++) {
            double best = 0.0;
            int kind = -1;
            double[] axis = null;
            double rad = 1;
            for (double[] c : caps) {
                double[] q = new double[3];
                double d = capsule(u, f, v, c, q);
                if (d < best) {
                    best = d;
                    kind = (int) c[8];
                    axis = q;
                    rad = c[3];
                }
            }
            for (double[] e : ells) {
                double x = (u - e[0]) / e[3], y = (f - e[1]) / e[4], z = (v - e[2]) / e[5];
                double d = (Math.sqrt(x * x + y * y + z * z) - 1) * Math.min(e[3], Math.min(e[4], e[5]));
                if (d < best) {
                    best = d;
                    kind = (int) e[6];
                    axis = new double[] {e[0], e[1], e[2]};
                    rad = Math.min(e[3], e[5]);
                }
            }
            String spec = null;
            if (kind >= 0) {
                if (kind == 2) spec = "bone_block";
                else if (kind == 3) spec = "polished_blackstone";
                else if (best < -1.6) spec = "blackstone";
                else if (kind == 1) {
                    // the throat and belly face forward and down: red plates there, scales elsewhere
                    double du = u - axis[0], df = f - axis[1], dv = v - axis[2];
                    double len = Math.max(0.01, Math.sqrt(du * du + df * df + dv * dv));
                    double belly = (df * 0.62 - dv * 0.78) / len;
                    spec = belly > 0.5 ? (Math.floorMod(v, 3) == 0 ? "nether_bricks" : "red_nether_bricks") : scales.pick(w.r);
                } else spec = scales.pick(w.r);
            } else {
                for (double[][] tri : tris) if (membrane(u, f, v, tri)) { spec = "red_stained_glass"; break; }
            }
            if (spec == null) continue;
            body.put(key(u, f, v), spec);
        }
        // carve the mouth between the jaws, light its throat, set the teeth and the tongue
        for (int f = 5; f <= 14; f++) for (int u = -1; u <= 1; u++) {
            double top = mouthTop(f), bot = mouthBottom(f);
            for (int v = (int) Math.ceil(bot); v <= (int) Math.floor(top); v++) {
                body.remove(key(u, f, v));
                if (f <= 6) body.put(key(u, f, v), u == 0 ? "shroomlight" : "magma_block");
            }
            if (f >= 7 && u == 0) body.put(key(0, f, (int) Math.ceil(bot) - 1), "magma_block");
        }
        for (int f = 7; f <= 12; f += 2) for (int s : new int[] {-1, 1}) {
            int vt = (int) Math.floor(mouthTop(f)), vb = (int) Math.ceil(mouthBottom(f));
            if (body.containsKey(key(s, f, vt + 1))) body.put(key(s, f, vt), "pointed_dripstone[vertical_direction=down,thickness=tip]");
            if (vt - vb >= 1 && body.containsKey(key(s, f, vb - 1))) body.put(key(s, f, vb), "pointed_dripstone[vertical_direction=up,thickness=tip]");
        }
        // eyes: glowing, set into the skull's sides under the brows
        for (int s : new int[] {-1, 1}) for (int v = 12; v <= 13; v++) {
            for (int u = s * 6; u != 0; u -= s) {
                if (!body.containsKey(key(u, 7, v))) continue;
                body.put(key(u, 7, v), "ochre_froglight");
                break;
            }
        }
        // nostrils that smoke: a signal fire in each, on a bale of hay
        for (int s : new int[] {-1, 1}) {
            int f = 12, u = s;
            int v = 15;
            while (v > 5 && !body.containsKey(key(u, f, v))) v--;
            body.put(key(u, f, v), "campfire[lit=true,signal_fire=true]");
            body.put(key(u, f, v - 1), "hay_block");
        }
        // spines down the back of the neck
        for (int i = 4; i < neck.size() - 4; i += 3) {
            double[] q = neck.get(i), r = neck.get(i + 1);
            boolean upright = Math.abs(r[2] - q[2]) > Math.abs(r[1] - q[1]);
            int f = (int) Math.round(q[1]), v = (int) Math.round(q[2]);
            // walk out of the neck straight back (where it rises) or straight up (where it leans over), then sit on its face
            for (int k2 = 0; k2 < 8; k2++) {
                long kk = upright ? key(0, f - k2, v) : key(0, f, v + k2);
                if (!body.containsKey(kk)) {
                    body.put(kk, "red_nether_brick_wall");
                    break;
                }
            }
        }
        // claws dug into the ash
        for (int s : new int[] {-1, 1}) for (double o : new double[] {-1.6, 0, 1.6}) body.put(key((int) Math.round(s * 10 + o * 1.6), 8, 0), "bone_block");
        for (int s : new int[] {-1, 1}) body.put(key((int) Math.round(s * 6.0), 5, 0), "bone_block");

        for (var e : body.entrySet()) {
            int u = (int) ((e.getKey() >> 40) - 512), f = (int) (((e.getKey() >> 20) & 0xFFFFF) - 512), v = (int) ((e.getKey() & 0xFFFFF) - 512);
            int x = ox + u, y = oy + v, z = oz + f;
            if (v <= 0 && !w.air(x, y, z) && !e.getValue().equals("bone_block")) continue;   // never into the island or the lava
            w.set(x, y, z, e.getValue());
        }
        keepClear(-14, -DRAGON_Z, 14, -DRAGON_Z + 9);
    }

    static double[][] wingTips(int s) {
        return new double[][] {{s * 29, -8, 11}, {s * 28, -10.5, 2}, {s * 23, -11, -6}, {s * 14, -10, -10}};
    }

    double mouthTop(double f) {          // the underside of the upper jaw
        double t = Math.max(0, Math.min(1, (f - 5.5) / 7.3));
        return 10.6 - 1.6 * t - (3.0 - 1.3 * t);
    }

    double mouthBottom(double f) {       // the top of the lower jaw
        double t = Math.max(0, Math.min(1, (f - 4.5) / 7.5));
        return 7.2 - 3.2 * t + (2.0 - 0.8 * t);
    }

    static long key(int u, int f, int v) {
        return ((long) (u + 512) << 40) | ((long) (f + 512) << 20) | (v + 512);
    }

    /** Distance from (u, f, v) to a tapered capsule (negative inside); its nearest axis point goes in q. */
    static double capsule(double u, double f, double v, double[] c, double[] q) {
        double bx = c[4] - c[0], by = c[5] - c[1], bz = c[6] - c[2];
        double px = u - c[0], py = f - c[1], pz = v - c[2];
        double l2 = bx * bx + by * by + bz * bz;
        double t = l2 == 0 ? 0 : Math.max(0, Math.min(1, (px * bx + py * by + pz * bz) / l2));
        q[0] = c[0] + bx * t;
        q[1] = c[1] + by * t;
        q[2] = c[2] + bz * t;
        double dx = u - q[0], dy = f - q[1], dz = v - q[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz) - (c[3] + (c[7] - c[3]) * t);
    }

    /** Is (u, f, v) on the membrane triangle (within half a block of its plane, inside it, short of a scalloped edge)? */
    static boolean membrane(double u, double f, double v, double[][] tri) {
        double[] a = tri[0], b = tri[1], c = tri[2];
        double e1x = b[0] - a[0], e1y = b[1] - a[1], e1z = b[2] - a[2];
        double e2x = c[0] - a[0], e2y = c[1] - a[1], e2z = c[2] - a[2];
        double nx = e1y * e2z - e1z * e2y, ny = e1z * e2x - e1x * e2z, nz = e1x * e2y - e1y * e2x;
        double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (nl == 0) return false;
        nx /= nl;
        ny /= nl;
        nz /= nl;
        double px = u - a[0], py = f - a[1], pz = v - a[2];
        double dist = px * nx + py * ny + pz * nz;
        if (Math.abs(dist) > 0.62) return false;
        // barycentric coordinates of the projection
        double qx = px - dist * nx, qy = py - dist * ny, qz = pz - dist * nz;
        double d00 = e1x * e1x + e1y * e1y + e1z * e1z, d01 = e1x * e2x + e1y * e2y + e1z * e2z, d11 = e2x * e2x + e2y * e2y + e2z * e2z;
        double d20 = qx * e1x + qy * e1y + qz * e1z, d21 = qx * e2x + qy * e2y + qz * e2z;
        double den = d00 * d11 - d01 * d01;
        double bb = (d11 * d20 - d01 * d21) / den, cc = (d00 * d21 - d01 * d20) / den, aa = 1 - bb - cc;
        if (aa < -0.02 || bb < -0.02 || cc < -0.02) return false;
        if (tri[3][0] > 0 && bb + cc > 0.01) {
            double along = cc / (bb + cc);
            if (aa < 0.14 * Math.sin(Math.PI * along)) return false;
        }
        return true;
    }

    // ---------------------------------------------------------------- about the plain

    /** A ruined dwarven watchtower: eight-sided, its top broken off, a ladder up to a lookout floor. */
    void watchtower(int cx, int cz) {
        int y = Y;
        for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) {
            double oct = Math.max(Math.max(Math.abs(dx), Math.abs(dz)), (Math.abs(dx) + Math.abs(dz)) * 0.7071);
            int x = cx + dx, z = cz + dz;
            if (oct > 4.3) continue;
            w.set(x, y, z, oct > 3.6 ? "tuff_bricks" : "deepslate_tiles");
            for (int yy = y - 1; yy > y - 5 && !w.type(x, yy, z).isSolid(); yy--) w.set(x, yy, z, "tuff_bricks");
            if (oct > 3.6) continue;
            double th = Math.atan2(dz, dx);
            int h = 9 + (int) Math.round((w.fbm(Math.cos(th) * 3 + cx, Math.sin(th) * 3, 2, 2) + 1) * 2.5);
            boolean wall = oct > 2.6;
            for (int dy = 1; dy <= 15; dy++) {
                if (wall) {
                    if (dy > h) w.set(x, y + dy, z, "air");
                    else w.set(x, y + dy, z, dy == 1 ? "polished_tuff" : dy == 8 ? "chiseled_deepslate" : P.of("deepslate_bricks", 5, "cracked_deepslate_bricks", 2, "cobbled_deepslate", 1).pick(w.r));
                } else w.set(x, y + dy, z, dy == 8 ? "deepslate_tile_slab[type=top]" : "air");
            }
        }
        // slits, a door toward the track, the ladder and the hole it climbs through
        for (int[] o : new int[][] {{3, 0}, {-3, 0}, {0, 3}, {2, -2}}) for (int dy : new int[] {4, 11}) if (w.type(cx + o[0], y + dy + 1, cz + o[1]).isSolid()) w.set(cx + o[0], y + dy, cz + o[1], "air");
        for (int dy = 1; dy <= 2; dy++) w.set(cx, y + dy, cz + 3, "air");
        for (int dy = 1; dy <= 8; dy++) w.set(cx, y + dy, cz - 2, "ladder[facing=south]");
        w.hangLight(cx + 1, y + 6, cz, "lantern");
        // fallen stones from the broken side
        for (int i = 0; i < 12; i++) {
            int x = cx - 5 - w.r.nextInt(3), z = cz - 3 + w.r.nextInt(7);
            if (land.topAt(x, z) != Y || !w.air(x, Y + 1, z)) continue;
            w.set(x, Y + 1, z, w.r.nextBoolean() ? "cobbled_deepslate" : "cobbled_deepslate_slab[type=bottom]");
        }
        keepClear(cx - 8, cz - 6, cx + 6, cz + 6);
    }

    /** A dwarven smelter: an open-fronted stone shed, furnaces under a tall smoking chimney, lava behind glass, ore by the door. */
    void smelter(int cx, int cz) {
        int y = Y, x0 = cx - 4, x1 = cx + 4, z0 = cz - 3, z1 = cz + 3;    // the front (z0) faces north, toward the track
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            w.set(x, y, z, ((x + z) & 1) == 0 ? "polished_deepslate" : "cobbled_deepslate");
            for (int dy = 1; dy <= 7; dy++) w.set(x, y + dy, z, "air");
            boolean wall = x == x0 || x == x1 || z == z1;
            boolean post = (x == x0 || x == x1) && (z == z0 || z == z1);
            if (wall) for (int dy = 1; dy <= 5; dy++) w.set(x, y + dy, z, post ? "polished_basalt[axis=y]" : dy <= 2 ? "cobbled_deepslate" : "deepslate_bricks");
        }
        for (int x : new int[] {cx - 1, cx + 1}) for (int dy = 1; dy <= 5; dy++) w.set(x, y + dy, z0, "polished_basalt[axis=y]");
        for (int x = x0; x <= x1; x++) w.set(x, y + 5, z0, "polished_basalt[axis=x]");
        k.gable(x0, z0, x1, z1, y + 6, true, 1, Kit.Roof.of("deepslate_tiles"), P.of("deepslate_bricks"));
        // the furnaces along the back, the chimney above them through the roof, smoke from its top
        w.set(cx - 1, y + 1, z1 - 1, "blast_furnace[facing=north,lit=true]");
        w.set(cx, y + 1, z1 - 1, "furnace[facing=north,lit=true]");
        w.set(cx + 1, y + 1, z1 - 1, "blast_furnace[facing=north,lit=true]");
        for (int dy = 2; dy <= 3; dy++) for (int x = cx - 1; x <= cx + 1; x++) w.set(x, y + dy, z1 - 1, "deepslate_bricks");
        for (int dy = 4; dy <= 13; dy++) for (int x = cx - 1; x <= cx; x++) w.set(x, y + dy, z1 - (dy <= 5 ? 1 : 0), dy == 13 ? "polished_blackstone_bricks" : "bricks");
        w.set(cx, y + 13, z1, "hay_block");
        w.set(cx, y + 14, z1, "campfire[lit=true,signal_fire=true]");
        w.set(cx - 1, y + 14, z1, "polished_blackstone_brick_slab[type=bottom]");
        for (int dy = 6; dy <= 12; dy++) for (int x = cx - 1; x <= cx; x++) w.set(x, y + dy, z1, "bricks");
        // lava behind glass in the side wall, a crucible, an anvil, chains
        for (int dy = 2; dy <= 3; dy++) {
            w.set(x0, y + dy, cz, "glass");
            w.set(x0 - 1, y + dy, cz, "lava");
            for (int[] o : new int[][] {{-1, -1}, {-1, 1}, {-2, 0}}) w.set(x0 + o[0], y + dy, cz + o[1], "cobbled_deepslate");
        }
        w.set(x0 - 1, y + 1, cz, "cobbled_deepslate");
        w.set(x0 - 1, y + 4, cz, "cobbled_deepslate");
        w.set(x1 - 1, y + 1, cz, "lava_cauldron");
        w.set(cx + 2, y + 1, z0 + 1, "anvil[facing=east]");
        w.set(x0 + 1, y + 1, z1 - 1, "barrel[facing=up]");
        w.hangLight(cx, y + 4, cz - 1, "lantern");
        // rails out of the door to a heap of ore and slag
        for (int z = z0 - 1; z >= z0 - 5; z--) w.set(cx, Y + 1, z, "rail");
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            double d = Math.hypot(dx, dz);
            int x = cx + 4 + dx, z = z0 - 5 + dz;
            if (d > 2.3 || land.topAt(x, z) != Y) continue;
            int h = d < 1 ? 2 : 1;
            for (int dy = 1; dy <= h; dy++) w.set(x, Y + dy, z, w.r.nextInt(3) == 0 ? "tuff" : "gravel");
        }
        keepClear(x0 - 3, z0 - 8, x1 + 7, z1 + 2);
    }

    /** Organ pipes: a cluster of basalt columns of uneven heights. */
    void organPipes(int cx, int cz, int h) {
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            double d = Math.hypot(dx, dz * 1.2);
            if (d > 2.8) continue;
            int x = cx + dx, z = cz + dz;
            if (land.topAt(x, z) != Y || riverD(x, z) < 2) continue;
            int hh = Math.max(1, (int) Math.round(h - d * 1.3 + w.r.nextInt(3) - 1));
            for (int dy = 1; dy <= hh; dy++) w.set(x, Y + dy, z, dy == hh ? "polished_basalt[axis=y]" : "basalt[axis=y]");
        }
        keepClear(cx - 3, cz - 3, cx + 3, cz + 3);
    }

    /** A lava vent: a glowing pool a block deep ringed with crust, and a stone chimney beside it trailing smoke. */
    void vent(int cx, int cz) {
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            double d = Math.hypot(dx, dz);
            int x = cx + dx, z = cz + dz;
            if (d > 2.4 || land.topAt(x, z) != Y) continue;
            w.set(x, Y, z, d < 0.9 ? "lava" : d < 1.6 ? "magma_block" : w.r.nextBoolean() ? "obsidian" : "blackstone");
            if (d < 0.9) w.set(x, Y - 1, z, "magma_block");
        }
        int sx = cx + 2, sz = cz + 2;
        w.set(sx, Y, sz, "hay_block");
        w.set(sx, Y + 1, sz, "campfire[lit=true,signal_fire=true]");
        for (int[] o : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) for (int dy = 1; dy <= 2; dy++) w.set(sx + o[0], Y + dy, sz + o[1], dy == 2 ? "basalt[axis=y]" : "polished_basalt[axis=y]");
        keepClear(cx - 3, cz - 3, cx + 3, cz + 3);
    }

    void boulder(int cx, int cz) {
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            double e = Math.pow(dx / 2.4, 2) + Math.pow(dz / 1.8, 2);
            int x = cx + dx, z = cz + dz;
            if (e > 1 || land.topAt(x, z) != Y) continue;
            int h = (int) Math.round((1 - e) * 2.6 + 0.4);
            for (int dy = 1; dy <= h; dy++) w.set(x, Y + dy, z, w.n3(x, Y + dy, z, 2) > 0 ? "blackstone" : "basalt[axis=y]");
        }
        keepClear(cx - 3, cz - 3, cx + 3, cz + 3);
    }

    /** A charred tree: a black trunk, a few bare limbs, each piece touching the last by a face. */
    void charred(int cx, int cz) {
        if (land.topAt(cx, cz) != Y) return;
        int h = 4 + w.r.nextInt(3);
        for (int dy = 1; dy <= h; dy++) w.set(cx, Y + dy, cz, "dark_oak_log[axis=y]");
        for (int b = 0; b < 3; b++) {
            double a = w.r.nextDouble() * Math.PI * 2;
            int px = cx, py = Y + h - w.r.nextInt(2), pz = cz;
            int tx = (int) Math.round(cx + Math.cos(a) * 2.6), ty = py + 1 + w.r.nextInt(2), tz = (int) Math.round(cz + Math.sin(a) * 2.6);
            while (px != tx || py != ty || pz != tz) {
                if (px != tx) px += Integer.signum(tx - px);
                else if (pz != tz) pz += Integer.signum(tz - pz);
                else py += Integer.signum(ty - py);
                w.set(px, py, pz, "dark_oak_wood");
            }
        }
        keepClear(cx - 3, cz - 3, cx + 3, cz + 3);
    }
}
