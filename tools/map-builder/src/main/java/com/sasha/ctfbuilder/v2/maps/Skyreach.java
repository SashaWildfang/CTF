package com.sasha.ctfbuilder.v2.maps;

import com.sasha.ctfbuilder.v2.Arena;
import com.sasha.ctfbuilder.v2.Kit;
import com.sasha.ctfbuilder.v2.Land;
import com.sasha.ctfbuilder.v2.Theme;
import com.sasha.ctfbuilder.v2.W.P;
import org.bukkit.World;

import java.util.HashMap;
import java.util.Map;

/**
 * Skyreach: four realms round the Rift, for four teams, and every realm is built from its own blocks. The shapes
 * are the same on every side (fair play); the materials are not: Emberhold (red) is red nether brick, blackstone,
 * gilded blackstone, crimson and shroomlight; Mossgarde (green) mossy stone brick, moss, jungle wood, mangrove,
 * azalea and verdant froglight; Tidewater (blue) prismarine, dark prismarine, lapis, warped wood and sea lanterns;
 * Sunspire (yellow) sandstone, gold, birch, bamboo mosaic, honeycomb and ochre froglight. (A spec "{a|b|c|d}" is
 * one block per copy; see W.alt.)
 *
 * Each realm: a citadel on a great floating crag (spawn in the keep, the flag in the courtyard, the Reach tower
 * behind the keep throwing the team's beam into the sky), a realm bridge east to the gate isle, a ring of rock
 * round a hole in the sky with the Realm Gate standing over the crossing, and a bridge on to the Rift Ring. Off its
 * flanks, a high fungal grove and a low quarry isle with a fall pouring off its tip (lava, water, ice, lava).
 *
 * Between each pair of realms a border isle with a palette of its own and an open rotunda (a hill): Copperworks
 * (copper in every stage of weathering), Cherry Court, Marble Temple (quartz and gold), Deepslate Hollow
 * (deepslate and sculk); an amethyst shard floats between it and the ring.
 *
 * The middle: the Rift Ring (end stone, purpur, obsidian) round a hole, and in the hole the Crown Spire, a geode
 * column of amethyst, calcite and basalt. Four helix ramps, one from each realm's bridge, wind up round it to the
 * Crown on top, thirty blocks up (the main hill), under a crystal halo.
 *
 * Built crossings run citadel, gate, ring, spire. Everything else (groves, quarries, border isles, shards, the
 * gaps in the ring on the diagonals) is bridged by hand, 8 to 16 blocks.
 */
public final class Skyreach extends Arena {

    // heights (the top block)
    static final int CIT = 76, RING = 76, GROVE = 84, QUARRY = 68, BORDER = 78, SHARD = 72, CROWN = 106;
    // places in the built quarter (the west: Emberhold)
    static final int CX = -106, GX = -61;
    static final double GROVE_X = -75, GROVE_Z = -33, QUARRY_X = -74, QUARRY_Z = 32;
    static final int BD = 50, SD = 33;                  // border isles and shards on the diagonals
    static final double RING_IN = 22, RING_OUT = 34, RING_GAP = 9.5;
    static final int KX0 = -117, KX1 = -103, KZ = 10;    // the keep
    static final int RTX = -121;                         // the Reach tower
    static final int FLAG_X = -93;
    static final double HELIX_IN = 12, HELIX_OUT = 15.5, SPIRE_R = 11;
    static final double TURN = 30 / (0.8 * 2 * Math.PI);  // rise per radian of the helices

    enum Zone { CITADEL, GATE, GROVE, QUARRY, BORDER, SHARD, RING }

    // ================================================================ palettes

    /** One block per realm: Emberhold (red), Mossgarde (green), Tidewater (blue), Sunspire (yellow). */
    static String R(String red, String green, String blue, String yellow) {
        return "{" + red + "|" + green + "|" + blue + "|" + yellow + "}";
    }

    /** One block per border isle (NW, NE, SE, SW), for something built at the NW isle (its copies are the others). */
    static String B(String nw, String ne, String se, String sw) {
        return "{" + nw + "|" + ne + "|" + se + "|" + sw + "}";
    }

    /** The same for a column of the built quarter: one south of the axis is the SW isle's at copy 0. */
    static String Bc(int z, String nw, String ne, String se, String sw) {
        return z <= 0 ? B(nw, ne, se, sw) : B(sw, nw, ne, se);
    }

    // the realms
    static final String WALL = R("red_nether_bricks", "mossy_stone_bricks", "prismarine_bricks", "cut_sandstone");
    static final String WALL2 = R("nether_bricks", "stone_bricks", "dark_prismarine", "smooth_sandstone");
    static final String PLINTH = R("polished_blackstone_bricks", "mossy_cobblestone", "polished_diorite", "sandstone");
    static final String TRIM = R("gilded_blackstone", "chiseled_stone_bricks", "lapis_block", "chiseled_sandstone");
    static final String LIGHT = R("shroomlight", "verdant_froglight", "sea_lantern", "ochre_froglight");
    static final String PILLAR = R("stripped_crimson_stem", "stripped_jungle_log", "stripped_warped_stem", "stripped_birch_log");
    static final String PLANKS = R("crimson_planks", "jungle_planks", "warped_planks", "birch_planks");
    static final String WSTAIRS = R("crimson_stairs", "jungle_stairs", "warped_stairs", "birch_stairs");
    static final String WSLAB = R("crimson_slab", "jungle_slab", "warped_slab", "birch_slab");
    static final String FENCE = R("crimson_fence", "jungle_fence", "warped_fence", "birch_fence");
    static final String TRAPDOOR = R("crimson_trapdoor", "jungle_trapdoor", "warped_trapdoor", "birch_trapdoor");
    static final String WSIGN = R("crimson_wall_sign", "jungle_wall_sign", "warped_wall_sign", "birch_wall_sign");
    static final String SSTAIRS = R("red_nether_brick_stairs", "mossy_stone_brick_stairs", "prismarine_brick_stairs", "smooth_sandstone_stairs");
    static final String SSLAB = R("red_nether_brick_slab", "mossy_stone_brick_slab", "prismarine_brick_slab", "cut_sandstone_slab");
    static final String SWALL = R("red_nether_brick_wall", "mossy_stone_brick_wall", "prismarine_wall", "sandstone_wall");
    static final String ROOFS = R("nether_brick_stairs", "mossy_cobblestone_stairs", "dark_prismarine_stairs", "bamboo_mosaic_stairs");
    static final String ROOFSL = R("nether_brick_slab", "mossy_cobblestone_slab", "dark_prismarine_slab", "bamboo_mosaic_slab");
    static final String ROOFF = R("nether_bricks", "mossy_cobblestone", "dark_prismarine", "bamboo_mosaic");
    static final String FLOOR = R("polished_blackstone", "polished_andesite", "prismarine", "smooth_sandstone");
    static final String FLOOR2 = R("polished_blackstone_bricks", "stone_bricks", "dark_prismarine", "cut_sandstone");
    static final String GLASS = R("red_stained_glass", "lime_stained_glass", "light_blue_stained_glass", "yellow_stained_glass");
    static final String PANE = R("red_stained_glass_pane", "lime_stained_glass_pane", "light_blue_stained_glass_pane", "yellow_stained_glass_pane");
    static final String LANTERN = R("lantern", "lantern", "soul_lantern", "lantern");
    static final String BEACON_BASE = R("netherite_block", "emerald_block", "diamond_block", "gold_block");
    static final String GROUND = R("crimson_nylium", "moss_block", "warped_nylium", "sand");
    static final String GROUND2 = R("netherrack", "grass_block", "snow_block", "red_sand");
    static final String GROUND3 = R("blackstone", "rooted_dirt", "calcite", "smooth_sandstone");
    static final String SOIL = R("netherrack", "dirt", "netherrack", "sandstone");
    static final String[] BANDS = {R("netherrack", "stone", "prismarine", "sandstone"), R("blackstone", "andesite", "dark_prismarine", "orange_terracotta"),
            R("netherrack", "stone", "prismarine", "sandstone"), R("basalt", "tuff", "calcite", "yellow_terracotta"),
            R("netherrack", "mossy_cobblestone", "prismarine", "smooth_sandstone"), R("magma_block", "moss_block", "packed_ice", "terracotta"),
            R("blackstone", "andesite", "dark_prismarine", "white_terracotta")};
    static final String SPECK = R("nether_gold_ore", "emerald_ore", "lapis_ore", "gold_ore");
    static final String HANG_TOP = R("weeping_vines_plant", "cave_vines_plant", "hanging_roots", "cave_vines_plant");
    static final String HANG_MID = R("weeping_vines_plant", "cave_vines_plant", "air", "cave_vines_plant");
    static final String HANG_TIP = R("weeping_vines", "cave_vines", "air", "cave_vines");
    static final String HANG_ONE = R("weeping_vines", "cave_vines", "hanging_roots", "cave_vines");
    static final String PLANT = R("crimson_roots", "short_grass", "warped_roots", "short_dry_grass");
    static final String PLANT2 = R("crimson_fungus", "fern", "warped_fungus", "dead_bush");
    // the groves: giant fungi
    static final String STEM = R("crimson_stem", "mangrove_log", "warped_stem", "birch_log");
    static final String CAP = R("nether_wart_block", "moss_block", "warped_wart_block", "honeycomb_block");
    static final String SPOT = R("shroomlight", "flowering_azalea_leaves", "sea_lantern", "ochre_froglight");
    // the quarries
    static final String QSTONE = R("polished_basalt", "mud_bricks", "packed_ice", "smooth_sandstone");
    static final String QSTONE2 = R("blackstone", "packed_mud", "blue_ice", "cut_red_sandstone");
    static final String QCAP = R("gilded_blackstone", "mangrove_planks", "snow_block", "raw_gold_block");
    static final String QGROUND = R("blackstone", "packed_mud", "snow_block", "smooth_sandstone");
    static final String QROCK = R("basalt", "mud", "packed_ice", "red_sandstone");
    static final String FALL_TOP = R("lava", "water", "blue_ice", "lava");
    static final String FALL = R("lava[level=8]", "water[level=8]", "blue_ice", "lava[level=8]");
    static final String NAME = R("EMBERHOLD", "MOSSGARDE", "TIDEWATER", "SUNSPIRE");

    // the border isles (NW Copperworks, NE Cherry Court, SE Marble Temple, SW Deepslate Hollow)
    static final String B_FLOOR = B("waxed_cut_copper", "calcite", "smooth_quartz", "polished_deepslate");
    static final String B_FLOOR2 = B("waxed_exposed_cut_copper", "pink_terracotta", "quartz_bricks", "deepslate_tiles");
    static final String B_FLOOR3 = B("waxed_weathered_cut_copper", "cherry_planks", "chiseled_quartz_block", "cracked_deepslate_tiles");
    static final String B_FLOOR4 = B("waxed_oxidized_cut_copper", "stripped_cherry_wood", "polished_diorite", "sculk");
    static final String B_COL = B("waxed_copper_block", "cherry_log", "quartz_pillar", "deepslate");
    static final String B_CAP = B("waxed_chiseled_copper", "stripped_cherry_log", "chiseled_quartz_block", "chiseled_deepslate");
    static final String B_STAIRS = B("waxed_cut_copper_stairs", "cherry_stairs", "quartz_stairs", "deepslate_tile_stairs");
    static final String B_SLAB = B("waxed_cut_copper_slab", "cherry_slab", "quartz_slab", "deepslate_tile_slab");
    static final String B_ROOFS = B("waxed_oxidized_cut_copper_stairs", "cherry_stairs", "smooth_quartz_stairs", "deepslate_brick_stairs");
    static final String B_ROOFSL = B("waxed_oxidized_cut_copper_slab", "cherry_slab", "smooth_quartz_slab", "deepslate_brick_slab");
    static final String B_ROOFF = B("waxed_oxidized_cut_copper", "cherry_planks", "smooth_quartz", "deepslate_bricks");
    static final String B_LIGHT = B("waxed_copper_bulb[lit=true]", "pearlescent_froglight", "sea_lantern", "crying_obsidian");
    static final String B_LANTERN = B("waxed_copper_lantern", "lantern", "lantern", "soul_lantern");
    static final String B_TRUNK = B("waxed_weathered_copper", "cherry_log", "quartz_pillar", "deepslate");
    static final String B_CROWN = B("waxed_oxidized_copper_grate", "cherry_leaves", "gold_block", "sculk");
    static final String B_ACCENT = B("waxed_copper_grate", "pink_terracotta", "gold_block", "sculk_catalyst");

    Land land;
    final Map<Long, Zone> zones = new HashMap<>();

    public Skyreach(World world) {
        super(world, "skyreach", true, Theme.get("skyreach"));
        name = "Skyreach";
        icon = "AMETHYST_CLUSTER";
        minutes = 18;
        kothPoints = 300;
        reach = 132;
        low = 15;
        high = 160;
        voidY = 15;
        spectator = new double[] {0.5, 110, 6.5, 180, 20};
    }

    // ================================================================ the land

    static double supere(double x, double z, double cx, double cz, double rx, double rz, double n, double ang) {
        double dx = x - cx, dz = z - cz, c = Math.cos(ang), s = Math.sin(ang);
        double u = (dx * c + dz * s) / rx, v = (-dx * s + dz * c) / rz;
        return 1 - Math.pow(Math.pow(Math.abs(u), n) + Math.pow(Math.abs(v), n), 1 / n);
    }

    static double disc(double x, double z, double cx, double cz, double r) {
        return 1 - Math.hypot(x - cx, z - cz) / r;
    }

    /** How far round from the nearest diagonal (degrees, 0..45). */
    static double offDiagonal(double x, double z) {
        double a = Math.toDegrees(Math.atan2(z, x));
        return Math.abs(Math.floorMod((long) Math.floor(a * 1000), 90000) / 1000.0 - 45);
    }

    /** Which island a column of the built quarter is on (and how far inside), or null. */
    Zone zoneOf(int x, int z, double[] depth) {
        Zone best = null;
        double v, top = 0;
        v = supere(x, z, CX, 0, 21, 26, 2.6, 0) + w.fbm(x, z, 10, 2) * 0.06;
        if (v > top) { top = v; best = Zone.CITADEL; }
        v = supere(x, z, GX, 0, 9, 10.5, 2.2, 0) + w.fbm(x + 40, z, 6, 2) * 0.05;
        if (Math.hypot(x - GX, z) < 4.5) v = -1;
        if (v > top) { top = v; best = Zone.GATE; }
        v = supere(x, z, GROVE_X, GROVE_Z, 11, 7.5, 2.0, 0.35) + w.fbm(x - 70, z, 6, 2) * 0.07;
        if (v > top) { top = v; best = Zone.GROVE; }
        v = supere(x, z, QUARRY_X, QUARRY_Z, 11.5, 7, 1.4, -0.75) + w.fbm(x, z - 70, 6, 2) * 0.05;
        if (v > top) { top = v; best = Zone.QUARRY; }
        v = Math.max(disc(x, z, -BD, -BD, 11), disc(x, z, -BD, BD, 11));
        if (v > top) { top = v; best = Zone.BORDER; }
        v = Math.max(disc(x, z, -SD, -SD, 4.2), disc(x, z, -SD, SD, 4.2));
        if (v > top) { top = v; best = Zone.SHARD; }
        double r = Math.hypot(x, z);
        v = offDiagonal(x, z) < RING_GAP ? -1 : Math.min(r - RING_IN, RING_OUT - r) / 6.0;
        if (v > top) { top = v; best = Zone.RING; }
        if (depth != null) depth[0] = top;
        return best;
    }

    Zone zone(int x, int z) {
        int[] c = w.toCanon(x, z);
        return zones.get(key(c[0], c[1]));
    }

    static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    double height(int x, int z) {
        Zone zn = zoneOf(x, z, null);
        if (zn == null) return Double.NaN;
        zones.put(key(x, z), zn);
        return switch (zn) {
            case CITADEL, GATE, RING -> CIT;
            case GROVE -> GROVE;
            case QUARRY -> QUARRY;
            case BORDER -> BORDER;
            case SHARD -> SHARD;
        };
    }

    String surface(int x, int z, int y, int steep, double edge) {
        Zone zn = zones.get(key(x, z));
        if (steep >= 3) return rock(x, y, z);
        double n = w.fbm(x + 913, z - 377, 7, 2), f = w.n2(x * 1.7, z * 1.7, 3);
        return switch (zn) {
            case CITADEL, GATE -> n > 0.4 ? GROUND2 : n < -0.45 ? GROUND3 : GROUND;
            case GROVE -> n > 0.5 ? GROUND3 : GROUND;
            case QUARRY -> f > 0.45 ? QSTONE2 : QGROUND;
            case BORDER -> Bc(z, "waxed_oxidized_copper", "moss_block", "calcite", "sculk");
            case SHARD -> f > 0.3 ? "budding_amethyst" : "amethyst_block";
            case RING -> n > 0.45 ? "end_stone_bricks" : "end_stone";
        };
    }

    String soil(int x, int y, int z) {
        Zone zn = zones.get(key(x, z));
        return switch (zn) {
            case CITADEL, GATE, GROVE -> SOIL;
            case QUARRY -> QROCK;
            case BORDER -> Bc(z, "waxed_copper_block", "dirt", "calcite", "deepslate");
            case SHARD -> "calcite";
            case RING -> "end_stone";
        };
    }

    String rock(int x, int y, int z) {
        Zone zn = zones.get(key(x, z));
        double warp = w.fbm(x * 0.6 + 11, z * 0.6 - 7, 30, 2) * 2;
        int band = Math.floorMod((int) Math.floor((y + warp) / 2.7), 7);
        boolean speck = w.n3(x * 2.1, y * 2.1, z * 2.1, 5) > 0.58;
        return switch (zn) {
            case CITADEL, GATE, GROVE -> speck ? SPECK : BANDS[band];
            case QUARRY -> speck ? QSTONE2 : band % 3 == 0 ? QSTONE : QROCK;
            case BORDER -> speck ? Bc(z, "copper_ore", "pink_terracotta", "nether_quartz_ore", "sculk")
                    : band % 2 == 0 ? Bc(z, "stone", "calcite", "diorite", "deepslate") : Bc(z, "raw_copper_block", "white_terracotta", "calcite", "tuff");
            case SHARD -> band % 3 == 0 ? "calcite" : band % 3 == 1 ? "amethyst_block" : "smooth_basalt";
            case RING -> speck ? "crying_obsidian" : band == 2 ? "obsidian" : band == 5 ? "purpur_block" : "end_stone";
        };
    }

    int bottom(int x, int z, int top, double edge) {
        Zone zn = zones.get(key(x, z));
        double noise = w.fbm(x * 0.9 + 5, z * 0.9 - 5, 12, 2);
        double cone = w.fbm(x * 1.6 + 300, z * 1.6 - 200, 7, 2);
        double b;
        switch (zn) {
            case CITADEL -> {
                double lens = 1 - Math.pow(1 - Math.min(1, edge / 18.0), 1.8);
                b = top - 4 - 42 * lens + noise * 4 * Math.min(1, edge / 6.0);
                if (cone > 0.32 && edge > 3) b -= (cone - 0.32) * 50 * Math.min(1, edge / 10.0);
            }
            case GATE -> b = top - 3 - 16 * (1 - Math.pow(1 - Math.min(1, edge / 5.0), 2)) + noise * 2;
            case GROVE -> b = top - 4 - 26 * (1 - Math.pow(1 - Math.min(1, edge / 7.0), 1.6)) + noise * 3;
            case QUARRY -> b = top - 3 - 34 * Math.pow(Math.min(1, edge / 6.5), 0.9) + noise * 2;
            case BORDER -> {
                double d = Math.min(Math.hypot(x + BD, z + BD), Math.hypot(x + BD, z - BD));
                b = top - 3 - 12 * (1 - Math.pow(1 - Math.min(1, edge / 7.0), 2));
                if (d < 3.2) b = top - 34 + d * 4;
            }
            case SHARD -> {
                double d = Math.min(Math.hypot(x + SD, z + SD), Math.hypot(x + SD, z - SD));
                b = top - 6 - 34 * Math.pow(Math.max(0, 1 - d / 4.4), 0.7);
            }
            default -> b = top - 3 - 10 * (1 - Math.pow(1 - Math.min(1, edge / 5.0), 2)) + noise * 2;
        }
        return Math.max(low + 3, (int) Math.round(b));
    }

    // ================================================================ the map

    @Override
    protected void build() {
        land = new Land(w, t, -132, 132, -132, 132);
        land.top = this::height;
        land.surface = this::surface;
        land.rock = this::rock;
        land.soilAt = this::soil;
        land.bottom = this::bottom;
        land.build();

        citadel();
        realmBridge();
        gateIsle();
        rimBridge();
        grove();
        quarry();
        borderIsle();
        shard();
        ring();
        spire();
        crown();
        helix();
        halo();
        undergrowth();
        // rocks floating off each realm, vines under its crags
        floater(-98, 96, -40, 3.6, true);
        floater(-120, 104, 31, 3.2, false);
        floater(-86, 91, 47, 3.4, true);
        floater(-126, 88, -22, 2.8, false);
        dangle(-128, -84, -27, 27, 0.05);
        dangle(-90, -60, -45, 45, 0.05);

        spawn = new double[] {-112.5, CIT + 1, 0.5, -90};
        flag = new int[] {FLAG_X, CIT + 1, 0};
        pads.add(new int[] {FLAG_X - 4, CIT + 1, -9});
        pads.add(new int[] {FLAG_X - 4, CIT + 1, 9});
        hills.add(new Hill("The Crown", -4, CROWN + 1, -4, 4, CROWN + 4, 4));
        hills.add(new Hill("Copperworks", -BD - 3, BORDER + 1, -BD - 3, -BD + 3, BORDER + 4, -BD + 3));
        hills.add(new Hill("Cherry Court", BD - 3, BORDER + 1, -BD - 3, BD + 3, BORDER + 4, -BD + 3));
        hills.add(new Hill("Marble Temple", BD - 3, BORDER + 1, BD - 3, BD + 3, BORDER + 4, BD + 3));
        hills.add(new Hill("Deepslate Hollow", -BD - 3, BORDER + 1, BD - 3, -BD + 3, BORDER + 4, BD + 3));
    }

    // ================================================================ the citadel

    int land(int x, int z) {
        return land.topAt(x, z);
    }

    /**
     * The citadel: the keep (the spawn) with four corner towers under the realm's roof, the Reach tower behind it
     * with the team's beacon on top, the courtyard in front with the flag, a parapet round the crag with a gate
     * east to the realm bridge and two posterns, gardens of giant fungi either side of the keep.
     */
    void citadel() {
        int y = CIT;
        Kit.Roof roof = new Kit.Roof(ROOFS, ROOFSL, ROOFF);
        // the courtyard: paved, the way through it inlaid, the flag on a dais
        level(-102, -15, -88, 15, y, P.of(FLOOR), P.of(WALL2));
        for (int x = -102; x <= -88; x++) for (int z = -15; z <= 15; z++) {
            int az = Math.abs(z);
            String s = az <= 1 ? FLOOR2 : az == 2 ? TRIM : (Math.floorMod(x, 4) == 0 || az % 4 == 0) ? FLOOR2 : FLOOR;
            if (Math.abs(x - FLAG_X) <= 1 && az <= 1) s = TRIM;
            w.set(x, y, z, s);
        }
        // the keep: banded walls on a plinth, posts every four, the realm's roof
        int x0 = KX0, x1 = KX1, z0 = -KZ, z1 = KZ, h = 10;
        k.walls(x0, z0, x1, z1, y, h, Kit.Bands.of(0, PLINTH, 1, WALL, 4, WALL2, 5, WALL, 9, WALL2, 10, TRIM), PILLAR, 4, P.of(FLOOR));
        k.gable(x0, z0, x1, z1, y + h + 1, false, 1, roof, P.of(WALL));
        for (int x = x0 + 2; x <= x1 - 2; x += 4) for (int z : new int[] {z0, z1}) k.window(x, y + 3, z, z == z0 ? "north" : "south", 1, 4, PANE, SSTAIRS, null);
        for (int z : new int[] {-6, 6}) k.window(x1, y + 3, z, "east", 1, 4, PANE, SSTAIRS, null);
        // the great door east onto the courtyard, banners either side
        for (int z = -2; z <= 2; z++) for (int dy = 1; dy <= 5; dy++) w.set(x1, y + dy, z, "air");
        w.set(x1, y + 5, -2, SSTAIRS + "[facing=south,half=top]");
        w.set(x1, y + 5, 2, SSTAIRS + "[facing=north,half=top]");
        w.set(x1, y + 6, 0, TRIM);
        for (int s : new int[] {-3, 3}) w.set(x1 + 1, y + 6, s, "TEAM_wall_banner[facing=east]");
        // inside: a runner from the dais to the door, columns and arches down both sides, benches, lights
        for (int x = x0 + 3; x <= x1 - 1; x++) for (int z = -1; z <= 1; z++) w.set(x, y + 1, z, "TEAM_carpet");
        for (int x = x0 + 3; x <= x1 - 3; x += 4) for (int s : new int[] {-6, 6}) {
            for (int dy = 1; dy <= 9; dy++) w.set(x, y + dy, s, PILLAR);
            w.set(x, y + 10, s, LIGHT);
        }
        for (int x = x0 + 1; x <= x1 - 1; x++) for (int s : new int[] {-1, 1}) {
            if (Math.floorMod(x - x0 - 3, 4) == 0) continue;
            w.set(x, y + 9, s * 6, SSTAIRS + "[facing=" + (Math.floorMod(x - x0 - 3, 4) == 1 ? "west" : "east") + ",half=top]");
            if (Math.floorMod(x - x0 - 3, 4) == 2) w.set(x, y + 9, s * 6, SSLAB + "[type=top]");
            if (x > x0 + 3 && x < x1 - 1) w.set(x, y + 1, s * 8, WSTAIRS + "[facing=" + (s < 0 ? "north" : "south") + "]");
        }
        for (int x = x0 + 2; x <= x1 - 2; x += 4) for (int z = z0 + 1; z <= z1 - 1; z++) w.set(x, y + h, z, PILLAR + "[axis=z]");
        for (int x = x0 + 4; x <= x1 - 2; x += 4) for (int s : new int[] {-3, 3}) w.hangLight(x, y + 7, s, LANTERN);
        // the dais at the west end, the realm's banners over it
        for (int z = -3; z <= 3; z++) {
            w.set(x0 + 1, y + 1, z, TRIM);
            w.set(x0 + 2, y + 1, z, SSLAB + "[type=bottom]");
        }
        w.set(x0 + 1, y + 2, 0, SSTAIRS + "[facing=west,half=bottom]");
        for (int s : new int[] {-2, 0, 2}) w.set(x0 + 1, y + 5, s, "TEAM_wall_banner[facing=east]");
        // the corner towers: square, banded, slit windows, the realm's roof
        for (int[] c : new int[][] {{x0, z0}, {x0, z1}, {x1, z0}, {x1, z1}}) {
            int tx = c[0], tz = c[1];
            k.walls(tx - 2, tz - 2, tx + 2, tz + 2, y, 15, Kit.Bands.of(0, PLINTH, 1, WALL, 7, WALL2, 8, WALL, 14, TRIM), PILLAR, 0, P.of(FLOOR));
            for (int dy = 1; dy <= 14; dy++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) w.set(tx + dx, y + dy, tz + dz, WALL);
            int top = k.hip(tx - 2, tz - 2, tx + 2, tz + 2, y + 16, 1, roof, P.of(WALL));
            w.set(tx, top + 1, tz, LIGHT);
            for (int[] f : new int[][] {{0, -3, 'n'}, {0, 3, 's'}, {-3, 0, 'w'}, {3, 0, 'e'}}) {
                int wx = tx + Integer.signum(f[0]) * 2, wz = tz + Integer.signum(f[1]) * 2;
                for (int dy : new int[] {9, 10, 11}) w.set(wx, y + dy, wz, PANE);
            }
        }
        reachTower();
        // the gate east: two little towers, a beam over the way, the team's banners on it
        for (int s : new int[] {-5, 5}) {
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = 1; dy <= 8; dy++)
                w.set(-87 + dx, y + dy, s + dz, Math.abs(dx) == 1 && Math.abs(dz) == 1 ? PILLAR : dy % 4 == 0 ? WALL2 : WALL);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) w.set(-87 + dx, y + 9, s + dz, SSLAB + "[type=bottom]");
            w.set(-87, y + 9, s, LIGHT);
        }
        for (int z = -4; z <= 4; z++) w.set(-87, y + 7, z, PILLAR + "[axis=z]");
        for (int z = -3; z <= 3; z += 2) w.set(-86, y + 7, z, "TEAM_wall_banner[facing=east]");
        // the parapet round the crag: a wall at the brink, posts with lanterns; open at the gate and the posterns
        for (int x = -128; x <= -84; x++) for (int z = -27; z <= 27; z++) {
            if (land(x, z) != CIT || zone(x, z) != Zone.CITADEL) continue;
            boolean brink = false;
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) if (land(x + d[0], z + d[1]) == Integer.MIN_VALUE) brink = true;
            if (!brink || !w.air(x, y + 1, z)) continue;
            if (x > -92 && Math.abs(z) <= 3) continue;                       // the gate
            if (x >= -99 && x <= -92 && Math.abs(z) > 16) continue;          // the posterns
            boolean post = Math.floorMod(x * 7 + z * 3, 9) == 0;
            w.set(x, y + 1, z, post ? PLINTH : SWALL);
            if (post) w.set(x, y + 2, z, LANTERN);
        }
        // a star of the realm's trim round the flag, lit from beneath
        for (int x = FLAG_X - 6; x <= FLAG_X + 6; x++) for (int z = -6; z <= 6; z++) {
            int dx = x - FLAG_X, adx = Math.abs(dx), az = Math.abs(z);
            double d = Math.hypot(dx, z);
            if (adx <= 1 && az <= 1) continue;
            boolean ray = (adx == 0 || az == 0 || adx == az) && d < 6.2;
            if (ray) w.set(x, y, z, d > 4.5 ? LIGHT : TRIM);
            else if (d > 5.5 && d < 6.5) w.set(x, y, z, FLOOR2);
        }
        // the realm's name over the gate
        w.set(-86, y + 7, 0, WSIGN + "[facing=east]");
        w.signText(-86, y + 7, 0, true, "", NAME, "", "");
        w.set(-88, y + 7, 0, WSIGN + "[facing=west]");
        w.signText(-88, y + 7, 0, true, "", NAME, "", "");
        // falls pouring from springs in the crag's west face
        for (int s : new int[] {-12, 12}) fall(-110, s, -1, 0, CIT);
        // the gardens either side of the keep: giant fungi, roots
        for (int s : new int[] {-1, 1}) {
            fungus(-113, y + 1, s * 19, 9, 4.2);
            fungus(-104, y + 1, s * 21, 7, 3.4);
            fungus(-123, y + 1, s * 16, 6, 3.0);
        }
        keepClear(-128, -27, -84, 27);
    }

    /** A fall: walk from (x, z) along (dx, dz) to the brink; a spring in the cliff face two below the top, falling into the void. */
    void fall(int x, int z, int dx, int dz, int top) {
        int lx = x, lz = z;
        for (int i = 0; i < 40; i++) {
            int nx = lx + dx, nz = lz + dz;
            if (land(nx, nz) == Integer.MIN_VALUE) {
                w.set(lx, top - 2, lz, FALL_TOP);
                for (int yy = top - 2; yy >= 25; yy--) if (w.air(nx, yy, nz)) w.set(nx, yy, nz, FALL);
                return;
            }
            lx = nx;
            lz = nz;
        }
    }

    /** A rock floating high off the realm: a disc of its ground on a banded cone, a little fungus or a light on it. */
    void floater(int cx, int cy, int cz, double r, boolean fungus) {
        int R = (int) Math.ceil(r);
        for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) {
            double d = Math.hypot(dx, dz) + w.n2((cx + dx) * 1.3, (cz + dz) * 1.3, 2) * 0.5;
            if (d > r) continue;
            int depth = (int) Math.round((r - d) * 2.6 + 1);
            for (int i = 0; i <= depth; i++) {
                int yy = cy - i;
                w.set(cx + dx, yy, cz + dz, i == 0 ? GROUND : i < 2 ? SOIL : BANDS[Math.floorMod(yy, 7)]);
            }
        }
        if (fungus) fungus(cx, cy + 1, cz, 4, 2.3);
        else w.set(cx, cy + 1, cz, LIGHT);
    }

    /** Vines and roots hanging under a crag. */
    void dangle(int x0, int x1, int z0, int z1, double chance) {
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            if (!w.canon(x, z) || land(x, z) == Integer.MIN_VALUE || w.r.nextDouble() > chance) continue;
            int yy = land(x, z) - 1;
            while (yy > 20 && !w.air(x, yy, z)) yy--;
            if (yy <= 20) continue;
            int len = 1 + w.r.nextInt(3);
            for (int i = 0; i < len; i++) w.set(x, yy - i, z, len == 1 ? HANG_ONE : i == 0 ? HANG_TOP : i == len - 1 ? HANG_TIP : HANG_MID);
        }
    }

    /** The Reach tower behind the keep: forty blocks of banded stone, a crown of battlements, the team's beacon. */
    void reachTower() {
        int y = CIT, cx = RTX, top = y + 40;
        for (int dy = 1; dy <= 40; dy++) for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            boolean corner = Math.abs(dx) == 3 && Math.abs(dz) == 3;
            boolean face = Math.abs(dx) == 3 || Math.abs(dz) == 3;
            String s = corner ? PILLAR : !face ? WALL : dy % 12 == 0 ? TRIM : dy % 6 == 0 ? WALL2 : WALL;
            if (face && !corner && (dx == 0 || dz == 0) && dy % 6 >= 2 && dy % 6 <= 4 && dy > 4) s = PANE;
            if (face && !corner && Math.abs(dx + dz) == 2 && dy % 12 == 6) s = LIGHT;
            w.set(cx + dx, y + dy, dz, s);
        }
        // corbels and a platform, battlements, the beacon on its base, the team's glass
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            boolean edge = Math.abs(dx) == 4 || Math.abs(dz) == 4;
            w.set(cx + dx, top, dz, edge ? WALL2 : FLOOR2);
            if (edge) {
                String f = Math.abs(dx) == 4 ? (dx < 0 ? "east" : "west") : (dz < 0 ? "south" : "north");
                if (!(Math.abs(dx) == 4 && Math.abs(dz) == 4)) w.set(cx + dx, top - 1, dz, SSTAIRS + "[facing=" + f + ",half=top]");
                w.set(cx + dx, top + 1, dz, (dx + dz & 1) == 0 ? SWALL : PLINTH);
            }
        }
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) w.set(cx + dx, top, dz, BEACON_BASE);
        w.set(cx, top + 1, 0, "beacon");
        w.set(cx, top + 2, 0, "TEAM_stained_glass");
        for (int[] c : new int[][] {{-4, -4}, {-4, 4}, {4, -4}, {4, 4}}) {
            w.set(cx + c[0], top + 1, c[1], PILLAR);
            w.set(cx + c[0], top + 2, c[1], LIGHT);
        }
    }

    /** A giant fungus: a stem thickening at its foot, a domed cap with glowing spots, its rim curling down and dripping. */
    void fungus(int x, int y, int z, int h, double cr) {
        int R = (int) Math.ceil(cr);
        int crownUp = (int) Math.round(Math.sqrt(cr * cr) * 0.5);
        for (int dy = 0; dy < h + crownUp; dy++) w.set(x, y + dy, z, STEM);
        for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            w.set(x + d[0], y, z + d[1], STEM);
            if (h > 7) w.set(x + d[0], y + 1, z + d[1], STEM);
        }
        int cy = y + h;
        for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) {
            double d = Math.hypot(dx, dz);
            if (d > cr + 0.35) continue;
            int s = (int) Math.round(Math.sqrt(Math.max(0, cr * cr - d * d)) * 0.5);
            boolean spot = w.n2((x + dx) * 3.1, (z + dz + h) * 3.1, 2) > 0.42;
            for (int yy = cy + Math.max(0, s - 2); yy <= cy + s; yy++) w.set(x + dx, yy, z + dz, yy == cy + s && spot ? SPOT : CAP);
            if (d > cr - 1.3) {
                w.set(x + dx, cy - 1, z + dz, CAP);
                int len = (int) Math.round((w.n2((x + dx) * 5.3, (z + dz) * 5.3, 1) + 1) * 1.6);
                for (int i = 0; i < len; i++)
                    w.set(x + dx, cy - 2 - i, z + dz, len == 1 ? HANG_ONE : i == 0 ? HANG_TOP : i == len - 1 ? HANG_TIP : HANG_MID);
            }
        }
        keepClear(x - R, z - R, x + R, z + R);
    }

    // ================================================================ the bridges and the gate isle

    /**
     * A deck along x at z -2..2 from xa to xb at y: banded paving, a plinth edge with a wall rail, posts with lamps,
     * girders and ribs under it where it spans the void.
     */
    void deck(int xa, int xb, int y, String a, String b, String edge, String rail, String post, String lamp, String girder, String rib) {
        for (int x = xa; x <= xb; x++) {
            boolean over = land(x, 0) == Integer.MIN_VALUE || land(x, 0) < y - 2;
            for (int z = -3; z <= 3; z++) {
                int az = Math.abs(z);
                w.set(x, y, z, az == 3 ? edge : az == 1 ? a : b);
                for (int dy = 1; dy <= 5; dy++) if (az < 3 || dy > 1) w.set(x, y + dy, z, "air");
                if (az == 3) {
                    boolean isPost = Math.floorMod(x - xa, 6) == 3;
                    boolean onLand = land(x, z) != Integer.MIN_VALUE && land(x, z) >= y - 1;
                    w.set(x, y + 1, z, onLand && !isPost ? "air" : isPost ? post : rail);
                    if (isPost) {
                        w.set(x, y + 2, z, post);
                        w.set(x, y + 3, z, lamp);
                    }
                }
            }
            if (over) {
                for (int z : new int[] {-2, 2}) w.set(x, y - 1, z, girder);
                if (Math.floorMod(x - xa, 6) == 3) for (int z = -2; z <= 2; z++) w.set(x, y - 1, z, rib);
            }
        }
    }

    /** The realm bridge from the citadel's gate to the gate isle, in the realm's stone. */
    void realmBridge() {
        deck(-86, -69, CIT, FLOOR, FLOOR2, PLINTH, SWALL, PILLAR, LIGHT, PILLAR + "[axis=x]", WALL2);
    }

    /** On from the gate isle to the Rift Ring: the realm's stone gives way to purpur and end stone. */
    void rimBridge() {
        deck(-53, -33, RING, "purpur_block", "end_stone_bricks", "purpur_pillar[axis=x]", "end_stone_brick_wall", "purpur_pillar", "pearlescent_froglight",
                "purpur_pillar[axis=x]", "end_stone_bricks");
    }

    /**
     * The gate isle: a ring of rock round a hole in the sky. The way crosses the hole on a glass floor between the
     * two pylons of the Realm Gate, the realm's name on its lintel.
     */
    void gateIsle() {
        int y = CIT, cx = GX;
        // the way over the isle and the hole
        for (int x = -70; x <= -52; x++) for (int z = -3; z <= 3; z++) {
            int az = Math.abs(z);
            boolean hole = Math.hypot(x - cx, z) < 4.6;
            w.set(x, y, z, hole && az <= 1 ? GLASS : az == 3 ? PLINTH : az == 2 ? FLOOR2 : FLOOR);
            if (hole && az == 3) w.set(x, y + 1, z, SWALL);
            if (hole) w.set(x, y - 1, z, az == 3 || (x & 1) == 0 && az == 2 ? PILLAR + "[axis=x]" : "air");
        }
        // the pylons, the lintel, the name
        for (int s : new int[] {-1, 1}) {
            for (int dx = -2; dx <= 2; dx++) for (int dz = 5; dz <= 7; dz++) for (int dy = 1; dy <= 18; dy++) {
                boolean corner = Math.abs(dx) == 2 && (dz == 5 || dz == 7);
                String sp = corner ? PILLAR : dy % 9 == 0 ? TRIM : dy % 3 == 0 ? WALL2 : WALL;
                if (dz == 5 && dx == 0 && dy >= 3 && dy <= 9) sp = dy == 9 ? LIGHT : PANE;
                w.set(cx + dx, y + dy, s * dz, sp);
            }
            for (int dx = -2; dx <= 2; dx++) for (int dz = 5; dz <= 7; dz++) w.set(cx + dx, y + 19, s * dz, Math.abs(dx) == 2 || dz != 6 ? SWALL : LIGHT);
            w.set(cx - 3, y + 7, s * 6, "TEAM_wall_banner[facing=west]");
            w.set(cx + 3, y + 7, s * 6, "TEAM_wall_banner[facing=east]");
        }
        for (int dx = -2; dx <= 2; dx++) for (int z = -4; z <= 4; z++) {
            for (int dy = 13; dy <= 15; dy++) w.set(cx + dx, y + dy, z, dy == 14 && Math.abs(dx) == 2 ? TRIM : WALL2);
            int az = Math.abs(z);
            if (az >= 3) w.set(cx + dx, y + 12, z, SSTAIRS + "[facing=" + (z < 0 ? "north" : "south") + ",half=top]");
            if (az == 4) w.set(cx + dx, y + 11, z, SSTAIRS + "[facing=" + (z < 0 ? "north" : "south") + ",half=top]");
        }
        for (int s : new int[] {-1, 1}) {
            int sx = cx + s * 3;
            w.set(sx, y + 14, 0, WSIGN + "[facing=" + (s < 0 ? "west" : "east") + "]");
            w.signText(sx, y + 14, 0, true, "", NAME, "", "");
            w.hangLight(cx + s, y + 11, -2, LANTERN);
            w.hangLight(cx + s, y + 11, 2, LANTERN);
        }
        keepClear(cx - 10, -11, cx + 10, 11);
    }

    // ================================================================ the flanks

    /** The high grove: giant fungi over a carpet of roots, a little pool. */
    void grove() {
        int y = GROVE + 1;
        double c = Math.cos(0.35), s = Math.sin(0.35);
        int[][] spots = {{-7, 0, 13, 52}, {0, -1, 9, 38}, {6, 1, 11, 44}};
        for (int[] f : spots) {
            int x = (int) Math.round(GROVE_X + f[0] * c - f[1] * s), z = (int) Math.round(GROVE_Z + f[0] * s + f[1] * c);
            fungus(x, y, z, f[2], f[3] / 10.0);
        }
        // the pool at the outer side, kerbed
        int px = (int) Math.round(GROVE_X - 2), pz = (int) Math.round(GROVE_Z - 4);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (land(px + dx, pz + dz) != GROVE) continue;
            boolean rim = Math.abs(dx) == 2 || Math.abs(dz) == 1;
            w.set(px + dx, GROVE, pz + dz, rim ? QSTONE2 : "water");
        }
    }

    /**
     * The low quarry isle, a shard of rock: a field of columns of the realm's stone at its tip, cut blocks lying
     * about, and a fall pouring off its point into the void.
     */
    void quarry() {
        int y = QUARRY;
        double c = Math.cos(-0.75), s = Math.sin(-0.75);
        // the column field on the outer half
        for (int u = -10; u <= -2; u++) for (int v = -5; v <= 5; v++) {
            int x = (int) Math.round(QUARRY_X + u * c - v * s), z = (int) Math.round(QUARRY_Z + u * s + v * c);
            if (land(x, z) != QUARRY || land.edgeAt(x, z) < 1.5) continue;
            double n = w.n2(x * 0.9, z * 0.9, 2);
            int hgt = (int) Math.round(1 + (n + 1) * 2.2 + (-u - 2) * 0.35);
            if (Math.abs(v) <= 1 && u > -6) continue;   // a way in between them
            for (int dy = 1; dy <= hgt; dy++) w.set(x, y + dy, z, dy == hgt ? QCAP : (dy & 1) == 0 ? QSTONE : QSTONE2);
        }
        // cut blocks near the inner end
        int[][] blocks = {{4, -2}, {5, 2}, {2, 3}};
        for (int[] b : blocks) {
            int x = (int) Math.round(QUARRY_X + b[0] * c - b[1] * s), z = (int) Math.round(QUARRY_Z + b[0] * s + b[1] * c);
            if (land(x, z) == QUARRY) {
                w.set(x, y + 1, z, QSTONE);
                if (land(x + 1, z) == QUARRY) w.set(x + 1, y + 1, z, QSTONE + "");
            }
        }
        // the fall off the far point: walk out along the axis to the brink, the spring on the last rock
        int lx = (int) Math.round(QUARRY_X), lz = (int) Math.round(QUARRY_Z);
        for (double u = 0; u > -20; u -= 0.5) {
            int x = (int) Math.round(QUARRY_X + u * c), z = (int) Math.round(QUARRY_Z + u * s);
            if (land(x, z) == Integer.MIN_VALUE) {
                // the spring comes out of the cliff face below the brink, out of reach
                w.set(lx, y - 2, lz, FALL_TOP);
                for (int yy = y - 2; yy >= 30; yy--) if (w.air(x, yy, z)) w.set(x, yy, z, FALL);
                break;
            }
            lx = x;
            lz = z;
        }
        keepClear((int) QUARRY_X - 12, (int) QUARRY_Z - 12, (int) QUARRY_X + 12, (int) QUARRY_Z + 12);
    }

    // ================================================================ the border isles

    static String inward(int dx, int dz) {
        return Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? "west" : "east") : (dz > 0 ? "north" : "south");
    }

    /**
     * A border isle (built at the NW one; the copies are the others, each in its own palette): paving in rings, an
     * open rotunda of eight columns under a ring roof with an oculus over the hill, four garden "trees" (copper
     * grates, cherry blossom, golden orbs, sculk), lamps.
     */
    void borderIsle() {
        int cx = -BD, cz = -BD, y = BORDER;
        for (int dx = -12; dx <= 12; dx++) for (int dz = -12; dz <= 12; dz++) {
            int x = cx + dx, z = cz + dz;
            if (land(x, z) != BORDER) continue;
            double d = Math.hypot(dx, dz), a = Math.atan2(dz, dx);
            int sector = (int) Math.floor((a + Math.PI) / (Math.PI / 8));
            String sp = d < 1.6 ? B_CAP : d < 2.6 ? B_FLOOR2 : d < 4.6 ? ((sector & 1) == 0 ? B_FLOOR : B_FLOOR3)
                    : d < 5.6 ? B_FLOOR2 : d < 8.6 ? ((sector & 1) == 0 ? B_FLOOR : B_FLOOR2) : B_FLOOR4;
            w.set(x, y, z, sp);
        }
        // the columns and their capitals
        for (int k = 0; k < 8; k++) {
            double a = Math.toRadians(22.5 + 45 * k);
            int x = cx + (int) Math.round(Math.cos(a) * 7.5), z = cz + (int) Math.round(Math.sin(a) * 7.5);
            w.set(x, y, z, B_CAP);
            for (int dy = 1; dy <= 6; dy++) w.set(x, y + dy, z, B_COL);
            w.set(x, y + 7, z, B_CAP);
        }
        // the ring roof: an eave on the capitals, a slope up inward, the oculus open over the hill
        for (int dx = -9; dx <= 9; dx++) for (int dz = -9; dz <= 9; dz++) {
            double d = Math.hypot(dx, dz);
            int x = cx + dx, z = cz + dz;
            if (d >= 7.0 && d < 8.7) {
                w.set(x, y + 8, z, B_ROOFF);
                if (d >= 8.0) w.set(x, y + 7, z, B_ROOFSL + "[type=top]");
            } else if (d >= 5.9 && d < 7.0) {
                w.set(x, y + 8, z, B_ROOFF);
                w.set(x, y + 9, z, B_ROOFS + "[facing=" + inward(dx, dz) + ",half=bottom]");
            } else if (d >= 4.8 && d < 5.9) {
                w.set(x, y + 9, z, B_ROOFF);
                w.set(x, y + 10, z, B_ROOFSL + "[type=bottom]");
            }
        }
        for (int k = 0; k < 8; k++) {
            double a = Math.toRadians(45 * k);
            w.hangLight(cx + (int) Math.round(Math.cos(a) * 7.5), y + 6, cz + (int) Math.round(Math.sin(a) * 7.5), B_LANTERN);
        }
        // the garden "trees"
        for (int k = 0; k < 4; k++) {
            double a = Math.toRadians(90 * k);
            int tx = cx + (int) Math.round(Math.cos(a) * 9.6), tz = cz + (int) Math.round(Math.sin(a) * 9.6);
            if (land(tx, tz) != BORDER) continue;
            for (int dy = 1; dy <= 4; dy++) w.set(tx, y + dy, tz, B_TRUNK);
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 4; dy <= 7; dy++) {
                double e = Math.sqrt(dx * dx + dz * dz + (dy - 5.5) * (dy - 5.5) * 1.6);
                if (e > 2.1 || dx == 0 && dz == 0 && dy <= 4) continue;
                w.set(tx + dx, y + dy, tz + dz, dx == 0 && dz == 0 && dy == 5 ? B_LIGHT : B_CROWN);
            }
        }
        // lamp posts toward the other isles
        for (int k : new int[] {3, 5, 7}) {
            double a = Math.toRadians(45 * k);
            int lx = cx + (int) Math.round(Math.cos(a) * 9.8), lz = cz + (int) Math.round(Math.sin(a) * 9.8);
            if (land(lx, lz) != BORDER) continue;
            w.set(lx, y + 1, lz, B_COL);
            w.set(lx, y + 2, lz, B_CAP);
            w.set(lx, y + 3, lz, B_LANTERN);
        }
        keepClear(cx - 12, cz - 12, cx + 12, cz + 12);
    }

    /** A crystal of amethyst growing from (x, y, z) along a direction, thinning to a tip with a cluster on it. */
    void crystal(int x, int y, int z, double dx, double dy, double dz, double len, double r0) {
        double n = Math.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= n;
        dy /= n;
        dz /= n;
        int R = (int) Math.ceil(r0) + 1;
        for (double t = 0; t <= len; t += 0.4) {
            double r = r0 * Math.pow(Math.max(0, 1 - t / len), 0.7);
            double px = x + dx * t, py = y + dy * t, pz = z + dz * t;
            for (int ox = -R; ox <= R; ox++) for (int oy = -R; oy <= R; oy++) for (int oz = -R; oz <= R; oz++) {
                int bx = (int) Math.round(px + ox), by = (int) Math.round(py + oy), bz = (int) Math.round(pz + oz);
                double d = Math.sqrt(Math.pow(bx - px, 2) + Math.pow(by - py, 2) + Math.pow(bz - pz, 2));
                if (d > r + 0.3) continue;
                w.set(bx, by, bz, d > r - 0.7 && ((bx + by + bz) % 5 == 0) ? "budding_amethyst" : "amethyst_block");
            }
        }
        int tx = (int) Math.round(x + dx * len), ty = (int) Math.round(y + dy * len), tz = (int) Math.round(z + dz * len);
        for (int up = 0; up <= 3; up++) {
            if (!w.air(tx, ty + up, tz)) continue;
            if (w.type(tx, ty + up - 1, tz).isSolid()) w.set(tx, ty + up, tz, "amethyst_cluster[facing=up]");
            break;
        }
    }

    /** The shard between the border isle and the ring: amethyst crystals leaning out at its sides, buds on top. */
    void shard() {
        int cx = -SD, cz = -SD, y = SHARD;
        crystal(cx - 3, y, cz + 3, -0.55, 1, 0.55, 7, 1.4);
        crystal(cx + 3, y, cz - 3, 0.5, 1, -0.5, 5, 1.1);
        for (int[] b : new int[][] {{-2, 1}, {2, -1}, {1, 2}, {-1, -2}}) {
            int x = cx + b[0], z = cz + b[1];
            if (land(x, z) == SHARD && w.air(x, y + 1, z)) w.set(x, y + 1, z, ((b[0] + b[1]) & 1) == 0 ? "small_amethyst_bud[facing=up]" : "medium_amethyst_bud[facing=up]");
        }
        keepClear(cx - 5, cz - 5, cx + 5, cz + 5);
    }

    // ================================================================ the middle

    /** The Rift Ring: a paved way round it, pylons of obsidian at the ends of each arc, the bridge in to the spire. */
    void ring() {
        int y = RING;
        for (int x = -35; x <= 0; x++) for (int z = -35; z <= 35; z++) {
            if (!w.canon(x, z) || land(x, z) != RING || zone(x, z) != Zone.RING) continue;
            double r = Math.hypot(x, z);
            if (r > 27.3 && r < 29.0) w.set(x, y, z, ((int) Math.floor(Math.toDegrees(Math.atan2(z, x)) / 5) & 1) == 0 ? "purpur_block" : "end_stone_bricks");
        }
        for (int s : new int[] {-1, 1}) {
            double a = Math.toRadians(180 + s * 29);
            int px = (int) Math.round(Math.cos(a) * 30), pz = (int) Math.round(Math.sin(a) * 30);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) w.set(px + dx, y + 1, pz + dz, Math.abs(dx) + Math.abs(dz) == 2 ? "end_stone_brick_slab[type=bottom]" : "end_stone_bricks");
            for (int dy = 2; dy <= 7; dy++) {
                w.set(px, y + dy, pz, dy == 4 || dy == 6 ? "crying_obsidian" : "obsidian");
                if (dy <= 3) for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) w.set(px + d[0], y + dy, pz + d[1], "obsidian");
            }
            w.set(px, y + 8, pz, "purpur_slab[type=bottom]");
            w.set(px, y + 9, pz, "amethyst_cluster[facing=up]");
            keepClear(px - 2, pz - 2, px + 2, pz + 2);
        }
        // in to the spire
        deck(-23, -12, RING, "purpur_block", "end_stone_bricks", "purpur_pillar[axis=x]", "end_stone_brick_wall", "purpur_pillar", "pearlescent_froglight",
                "purpur_pillar[axis=x]", "end_stone_bricks");
        keepClear(-24, -4, -10, 4);
    }

    /** The spire's radius at height y (a spike underneath, a column through the helices, a flare under the crown). */
    static double spireR(int y) {
        if (y < 18) return -1;
        if (y < 76) return SPIRE_R * Math.pow((y - 18) / 58.0, 0.55);
        if (y <= 100) return SPIRE_R - 0.5 + 0.5 * Math.sin((y - 76) / 4.0);
        return SPIRE_R + (y - 100) * 0.2;
    }

    /** The Crown Spire: a geode column, amethyst banded with calcite and basalt, hanging into the Rift. */
    void spire() {
        for (int x = -14; x <= 0; x++) for (int z = -14; z <= 14; z++) {
            if (!w.canon(x, z)) continue;
            double d = Math.hypot(x, z);
            for (int y = 18; y <= CROWN - 2; y++) {
                double r = spireR(y);
                if (d > r) continue;
                int band = Math.floorMod(y, 9);
                boolean skin = d > r - 1.2;
                String s = band <= 1 ? "calcite" : band == 2 ? "smooth_basalt" : skin && w.n3(x * 2.3, y * 2.3, z * 2.3, 3) > 0.5 ? "budding_amethyst" : "amethyst_block";
                w.set(x, y, z, s);
            }
        }
        // clusters on the hanging spike
        for (int y = 30; y < 74; y += 3) {
            double r = spireR(y);
            for (int k = 0; k < 3; k++) {
                double a = Math.toRadians(150 + k * 30 + y * 7);
                int x = (int) Math.round(Math.cos(a) * (r + 1)), z = (int) Math.round(Math.sin(a) * (r + 1));
                if (!w.canon(x, z) || !w.air(x, y, z)) continue;
                String f = Math.abs(x) > Math.abs(z) ? (x < 0 ? "west" : "east") : (z < 0 ? "north" : "south");
                int bx = x - Kit.dx(f), bz = z - Kit.dz(f);
                if (w.type(bx, y, bz).isSolid()) w.set(x, y, z, "amethyst_cluster[facing=" + f + "]");
            }
        }
    }

    /** A walking surface at standing height q (a multiple of a half): a full block, or a bottom slab. Returns its y. */
    int tread(int x, int z, double q, String full, String slab) {
        boolean half = q != Math.floor(q);
        int y = half ? (int) Math.floor(q) : (int) q - 1;
        w.set(x, y, z, half ? slab + "[type=bottom]" : full);
        return y;
    }

    /**
     * The helix: a ramp three and a half blocks wide winding up round the spire from the end of the realm's bridge
     * (y 77 to stand on) to the Crown (y 107), four-fifths of a turn, half a block a block at its inner edge, with a
     * wall along its outer edge. Its three copies are the other realms' helices, interleaved with it.
     */
    void helix() {
        double total = 30 / TURN;
        for (int x = -17; x <= 17; x++) for (int z = -17; z <= 17; z++) {
            double r = Math.hypot(x, z);
            if (r < HELIX_IN || r > HELIX_OUT + 1.0) continue;
            double phi = Math.atan2(z, x);
            double t0 = Math.floorMod((long) Math.round((phi - Math.PI) * 1e6), (long) Math.round(2 * Math.PI * 1e6)) / 1e6;
            for (double t = t0; t <= total + 0.02; t += 2 * Math.PI) {
                double q = Math.min(CROWN + 1, Math.round((CROWN + 1 - 30 + TURN * t) * 2) / 2.0);
                if (r <= HELIX_OUT) {
                    String full = r < 13.1 ? "purpur_block" : r > 14.6 ? "purpur_block" : "end_stone_bricks";
                    String slab = r < 13.1 ? "purpur_slab" : r > 14.6 ? "purpur_slab" : "end_stone_brick_slab";
                    int ty = tread(x, z, q, full, slab);
                    w.set(x, ty - 1, z, r < 13.1 || r > 14.6 ? "purpur_pillar" : "end_stone_bricks");
                    for (int dy = 1; dy <= 4; dy++) if (!w.air(x, ty + dy, z)) w.set(x, ty + dy, z, "air");
                } else if (t > 0.5) {
                    // the wall along the outer edge, on a kerb
                    int base = (int) Math.floor(q) - (q == Math.floor(q) ? 1 : 0);
                    w.set(x, base, z, "purpur_block");
                    w.set(x, base - 1, z, "purpur_pillar");
                    w.set(x, base + 1, z, "end_stone_brick_wall");
                    if (Math.floorMod(Math.round(t * 10), 9) == 0) w.set(x, base + 2, z, "pearlescent_froglight");
                }
            }
        }
    }

    /**
     * The Crown on top of the spire: a disc paved in rings round a gold heart (the hill), a parapet with lamps open
     * where the four helices arrive, crystal points leaning out from its rim between them.
     */
    void crown() {
        int y = CROWN;
        double arrive = Math.toDegrees(Math.PI + 30 / TURN) % 90;   // where the helices come up (mod a quarter turn)
        for (int x = -14; x <= 0; x++) for (int z = -14; z <= 14; z++) {
            if (!w.canon(x, z)) continue;
            double d = Math.hypot(x, z);
            if (d > 11.9) continue;
            String s = d < 1.6 ? "gold_block" : d < 4.6 ? (((x + z) & 1) == 0 ? "purpur_block" : "end_stone_bricks") : d < 5.6 ? "amethyst_block"
                    : d < 10.4 ? (((int) Math.floor(Math.toDegrees(Math.atan2(z, x)) / 7.5) & 1) == 0 ? "end_stone_bricks" : "purpur_block") : "purpur_block";
            w.set(x, y, z, s);
            w.set(x, y - 1, z, "purpur_block");
            if (d < 11) w.set(x, y - 2, z, "end_stone_bricks");
            for (int dy = 1; dy <= 6; dy++) if (!w.air(x, y + dy, z)) w.set(x, y + dy, z, "air");
            if (d >= 11.2) {
                double a = Math.toDegrees(Math.atan2(z, x));
                double off = Math.abs(((a - arrive) % 90 + 135) % 90 - 45);
                if (off < 26) continue;
                boolean post = Math.floorMod((int) Math.round(a), 15) < 3;
                w.set(x, y + 1, z, post ? "purpur_pillar" : "end_stone_brick_wall");
                if (post) w.set(x, y + 2, z, "pearlescent_froglight");
            }
        }
        for (double a : new double[] {arrive + 34, arrive + 62}) {
            double ra = Math.toRadians(a);
            int x = (int) Math.round(Math.cos(ra) * 11.2), z = (int) Math.round(Math.sin(ra) * 11.2);
            crystal(x, y + 1, z, Math.cos(ra) * 0.45, 1, Math.sin(ra) * 0.45, a < arrive + 50 ? 11 : 8, a < arrive + 50 ? 1.6 : 1.2);
        }
    }

    /** A halo of glass and amethyst floating high over the middle. */
    void halo() {
        int y = 126;
        for (int x = -46; x <= 0; x++) for (int z = -46; z <= 46; z++) {
            if (!w.canon(x, z)) continue;
            double d = Math.hypot(x, z);
            if (d < 42 || d > 44.6) continue;
            double a = Math.toDegrees(Math.atan2(z, x));
            boolean seg = Math.floorMod((int) Math.floor(a / 3.75), 4) == 0;
            w.set(x, y, z, seg ? "amethyst_block" : "purple_stained_glass");
            if (d > 42.8 && d < 43.8) w.set(x, y + 1, z, seg ? "amethyst_cluster[facing=up]" : "magenta_stained_glass");
        }
    }

    // ================================================================ growth

    /** Roots and fungi on the realms' ground, chorus on the ring. */
    void undergrowth() {
        for (int x = -128; x <= 0; x++) for (int z = -128; z <= 128; z++) {
            if (!w.canon(x, z) || isClear(x, z, 0)) continue;
            Zone zn = zones.get(key(x, z));
            if (zn == null) continue;
            int g = land(x, z);
            if (!w.air(x, g + 1, z)) continue;
            String ground = w.type(x, g, z).name();
            if ((zn == Zone.CITADEL || zn == Zone.GATE || zn == Zone.GROVE) && ground.equals("CRIMSON_NYLIUM")) {
                double n = w.r.nextDouble();
                if (n < 0.12) w.set(x, g + 1, z, PLANT);
                else if (n < 0.15) w.set(x, g + 1, z, PLANT2);
            } else if (zn == Zone.RING && ground.equals("END_STONE") && w.r.nextDouble() < 0.012 && Math.hypot(x, z) > 30) {
                w.set(x, g + 1, z, "chorus_flower");
            }
        }
    }
}
