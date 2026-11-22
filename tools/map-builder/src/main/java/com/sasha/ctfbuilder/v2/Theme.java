package com.sasha.ctfbuilder.v2;

import com.sasha.ctfbuilder.v2.W.P;

/** Everything that makes a map look like itself: ground, cliffs, plants, trees and how its bases are built. */
public final class Theme {

    public enum Tree { OAK, BIRCH, SPRUCE, CHERRY, PALM, JUNGLE, DEAD, CRIMSON, WARPED, RED_MUSHROOM, BROWN_MUSHROOM, PALE, CHORUS, AZALEA, AUTUMN }
    public enum Roof { GABLE, TIERED, FLAT, SPIRE }

    public String id, name;
    // ground
    public P surface, surface2, surface3, soil, path, boulder, under;
    public String[] bands;          // cliff strata, top to bottom repeating
    public P speck;                 // odd blocks sprinkled through cliffs
    public String[] hangers;        // hanging from island undersides
    public boolean snow;            // snow layers on top
    // plants
    public Tree[] trees;
    public int[] treeWeights;
    public double treeDensity = 0.012;
    public P flora;
    public double floraDensity = 0.18;
    public P bushes;
    // buildings
    public P wall, wall2, trim, floor;
    public String log, planks, stairsRoof, slabRoof, fullRoof, fence, pane, light, wood, trapdoor, stairsTrim;
    public Roof roof = Roof.GABLE;
    public String liquid = "water";

    public static Theme get(String id) {
        return switch (id) {
            case "frostfall" -> frostfall();
            case "dustbowl" -> dustbowl();
            case "kingdoms" -> kingdoms();
            case "sakura" -> sakura();
            case "emberforge" -> emberforge();
            case "verdant" -> verdant();
            case "tidecrest" -> tidecrest();
            case "mycelia" -> mycelia();
            case "palegrove" -> palegrove();
            case "skyreach" -> skyreach();
            default -> kingdoms();
        };
    }

    private static Theme base() {
        Theme t = new Theme();
        t.hangers = new String[] {"vine", "hanging_roots"};
        t.under = P.of("stone", 4, "andesite", 2, "tuff", 2, "cobblestone", 1);
        t.speck = P.of("coal_ore", 1, "gravel", 2, "mossy_cobblestone", 1);
        t.boulder = P.of("stone", 3, "andesite", 3, "cobblestone", 2, "mossy_cobblestone", 2);
        t.bushes = P.of("oak_leaves[persistent=true]");
        t.log = "spruce_log";
        t.planks = "spruce_planks";
        t.wood = "spruce";
        t.fence = "spruce_fence";
        t.trapdoor = "spruce_trapdoor";
        t.pane = "glass_pane";
        t.light = "lantern";
        t.stairsTrim = "stone_brick_stairs";
        return t;
    }

    static Theme frostfall() {
        Theme t = base();
        t.id = "frostfall"; t.name = "Frostfall";
        t.surface = P.of("snow_block");
        t.surface2 = P.of("snow_block", 3, "powder_snow_cauldron", 0, "packed_ice", 1, "stone", 1);
        t.surface3 = P.of("podzol", 2, "coarse_dirt", 1, "snow_block", 2);
        t.soil = P.of("dirt", 4, "coarse_dirt", 2, "snow_block", 1);
        t.bands = new String[] {"stone", "andesite", "stone", "calcite", "tuff", "stone", "diorite", "andesite", "stone", "tuff", "cobblestone", "deepslate"};
        t.speck = P.of("blue_ice", 1, "packed_ice", 2, "gravel", 2, "iron_ore", 1);
        t.under = P.of("stone", 3, "packed_ice", 1, "tuff", 2, "deepslate", 1);
        t.hangers = new String[] {"pointed_dripstone", "hanging_roots"};
        t.path = P.of("gravel", 3, "coarse_dirt", 2, "packed_mud", 1, "snow_block", 2);
        t.snow = true;
        t.trees = new Tree[] {Tree.SPRUCE, Tree.DEAD};
        t.treeWeights = new int[] {12, 1};
        t.treeDensity = 0.016;
        t.flora = P.of("fern", 3, "short_grass", 1, "sweet_berry_bush[age=1]", 1, "snow[layers=2]", 4);
        t.floraDensity = 0.12;
        t.bushes = P.of("spruce_leaves[persistent=true]");
        t.wall = P.of("stone_bricks", 6, "cracked_stone_bricks", 2, "mossy_stone_bricks", 1, "andesite", 1);
        t.wall2 = P.of("spruce_planks", 3, "stripped_spruce_log[axis=y]", 1);
        t.trim = P.of("polished_andesite");
        t.floor = P.of("spruce_planks", 3, "stripped_spruce_wood", 1);
        t.log = "dark_oak_log"; t.planks = "dark_oak_planks"; t.wood = "dark_oak";
        t.stairsRoof = "deepslate_tile_stairs"; t.slabRoof = "deepslate_tile_slab"; t.fullRoof = "deepslate_tiles";
        t.fence = "dark_oak_fence"; t.trapdoor = "spruce_trapdoor"; t.stairsTrim = "stone_brick_stairs";
        t.liquid = "ice";
        return t;
    }

    static Theme dustbowl() {
        Theme t = base();
        t.id = "dustbowl"; t.name = "Dustbowl";
        t.surface = P.of("sand", 8, "sandstone", 1);
        t.surface2 = P.of("red_sand", 6, "coarse_dirt", 1);
        t.surface3 = P.of("terracotta", 2, "smooth_sandstone", 1, "packed_mud", 1);
        t.soil = P.of("sandstone", 3, "sand", 2, "terracotta", 1);
        t.bands = new String[] {"terracotta", "orange_terracotta", "terracotta", "yellow_terracotta", "red_terracotta", "white_terracotta",
                "terracotta", "brown_terracotta", "orange_terracotta", "light_gray_terracotta", "red_sandstone", "terracotta"};
        t.speck = P.of("gold_ore", 1, "red_sandstone", 2, "granite", 2);
        t.under = P.of("terracotta", 3, "red_sandstone", 2, "granite", 2, "sandstone", 1);
        t.hangers = new String[] {"hanging_roots", "pointed_dripstone"};
        t.path = P.of("smooth_sandstone", 2, "sandstone", 2, "coarse_dirt", 2, "packed_mud", 1);
        t.trees = new Tree[] {Tree.PALM, Tree.DEAD};
        t.treeWeights = new int[] {3, 2};
        t.treeDensity = 0.006;
        t.flora = P.of("dead_bush", 4, "short_dry_grass", 4, "tall_dry_grass", 2, "cactus", 1);
        t.floraDensity = 0.08;
        t.bushes = P.of("jungle_leaves[persistent=true]");
        t.boulder = P.of("sandstone", 2, "terracotta", 2, "granite", 1, "red_sandstone", 2);
        t.wall = P.of("sandstone", 4, "smooth_sandstone", 3, "cut_sandstone", 2);
        t.wall2 = P.of("cut_sandstone", 3, "chiseled_sandstone", 1);
        t.trim = P.of("smooth_red_sandstone");
        t.floor = P.of("smooth_sandstone", 3, "terracotta", 1);
        t.log = "stripped_jungle_log"; t.planks = "jungle_planks"; t.wood = "jungle";
        t.stairsRoof = "smooth_red_sandstone_stairs"; t.slabRoof = "smooth_red_sandstone_slab"; t.fullRoof = "smooth_red_sandstone";
        t.fence = "jungle_fence"; t.trapdoor = "jungle_trapdoor"; t.stairsTrim = "sandstone_stairs";
        t.roof = Roof.FLAT;
        return t;
    }

    static Theme kingdoms() {
        Theme t = base();
        t.id = "kingdoms"; t.name = "Four Kingdoms";
        t.surface = P.of("grass_block");
        t.surface2 = P.of("grass_block", 4, "moss_block", 2, "coarse_dirt", 1);
        t.surface3 = P.of("podzol", 2, "rooted_dirt", 1, "grass_block", 2);
        t.soil = P.of("dirt", 5, "coarse_dirt", 1, "rooted_dirt", 1);
        t.bands = new String[] {"stone", "andesite", "stone", "granite", "stone", "tuff", "andesite", "diorite", "stone", "deepslate"};
        t.path = P.of("dirt_path", 4, "coarse_dirt", 2, "gravel", 1, "packed_mud", 1);
        t.trees = new Tree[] {Tree.OAK, Tree.BIRCH, Tree.AZALEA};
        t.treeWeights = new int[] {6, 3, 1};
        t.flora = P.of("short_grass", 10, "tall_grass[half=lower]", 0, "poppy", 1, "dandelion", 1, "cornflower", 1, "oxeye_daisy", 1, "azure_bluet", 1, "fern", 2, "leaf_litter[segment_amount=2]", 2);
        t.wall = P.of("stone_bricks", 7, "cracked_stone_bricks", 2, "mossy_stone_bricks", 2, "cobblestone", 1);
        t.wall2 = P.of("white_terracotta", 1, "stripped_spruce_log[axis=y]", 0);
        t.trim = P.of("polished_andesite");
        t.floor = P.of("spruce_planks", 3, "stripped_spruce_wood", 1);
        t.stairsRoof = "spruce_stairs"; t.slabRoof = "spruce_slab"; t.fullRoof = "spruce_planks";
        return t;
    }

    static Theme sakura() {
        Theme t = base();
        t.id = "sakura"; t.name = "Sakura Isles";
        t.surface = P.of("grass_block");
        t.surface2 = P.of("moss_block", 3, "grass_block", 2);
        t.surface3 = P.of("podzol", 1, "coarse_dirt", 1, "grass_block", 3);
        t.soil = P.of("dirt", 4, "rooted_dirt", 1);
        t.bands = new String[] {"calcite", "stone", "diorite", "calcite", "andesite", "stone", "tuff", "calcite"};
        t.speck = P.of("mossy_cobblestone", 2, "gravel", 1, "white_terracotta", 1);
        t.path = P.of("gravel", 3, "andesite", 2, "polished_andesite", 1, "dirt_path", 2);
        t.trees = new Tree[] {Tree.CHERRY, Tree.AZALEA};
        t.treeWeights = new int[] {6, 1};
        t.treeDensity = 0.011;
        t.flora = P.of("pink_petals[flower_amount=4]", 5, "short_grass", 6, "fern", 2, "lily_of_the_valley", 1, "allium", 1, "pink_tulip", 1);
        t.bushes = P.of("cherry_leaves[persistent=true]", 2, "azalea_leaves[persistent=true]", 1);
        t.wall = P.of("white_terracotta", 3, "calcite", 2, "smooth_quartz", 1);
        t.wall2 = P.of("stripped_dark_oak_log[axis=y]");
        t.trim = P.of("dark_oak_planks");
        t.floor = P.of("dark_oak_planks", 2, "stripped_dark_oak_wood", 1);
        t.log = "stripped_dark_oak_log"; t.planks = "dark_oak_planks"; t.wood = "dark_oak";
        t.stairsRoof = "deepslate_tile_stairs"; t.slabRoof = "deepslate_tile_slab"; t.fullRoof = "deepslate_tiles";
        t.fence = "dark_oak_fence"; t.trapdoor = "dark_oak_trapdoor"; t.stairsTrim = "dark_oak_stairs";
        t.roof = Roof.TIERED;
        t.light = "lantern";
        return t;
    }

    static Theme emberforge() {
        Theme t = base();
        t.id = "emberforge"; t.name = "Emberforge";
        t.surface = P.of("blackstone", 3, "basalt[axis=y]", 2, "smooth_basalt", 1);
        t.surface2 = P.of("crimson_nylium", 3, "netherrack", 1);
        t.surface3 = P.of("magma_block", 1, "blackstone", 3, "gilded_blackstone", 0);
        t.soil = P.of("blackstone", 3, "basalt[axis=y]", 2, "netherrack", 1);
        t.bands = new String[] {"blackstone", "basalt[axis=y]", "smooth_basalt", "blackstone", "tuff", "deepslate", "basalt[axis=y]", "netherrack"};
        t.speck = P.of("magma_block", 2, "nether_gold_ore", 1, "gilded_blackstone", 1, "crying_obsidian", 1);
        t.under = P.of("basalt[axis=y]", 3, "blackstone", 2, "magma_block", 1);
        t.hangers = new String[] {"weeping_vines_plant", "pointed_dripstone"};
        t.path = P.of("polished_blackstone", 2, "blackstone", 2, "smooth_basalt", 1);
        t.trees = new Tree[] {Tree.CRIMSON, Tree.WARPED, Tree.DEAD};
        t.treeWeights = new int[] {5, 2, 1};
        t.treeDensity = 0.008;
        t.flora = P.of("crimson_roots", 3, "nether_sprouts", 2, "crimson_fungus", 1, "warped_roots", 1);
        t.floraDensity = 0.1;
        t.bushes = P.of("nether_wart_block", 2, "shroomlight", 0);
        t.boulder = P.of("blackstone", 3, "basalt[axis=y]", 2, "magma_block", 1);
        t.wall = P.of("polished_blackstone_bricks", 6, "cracked_polished_blackstone_bricks", 2, "blackstone", 1);
        t.wall2 = P.of("red_nether_bricks", 1);
        t.trim = P.of("polished_blackstone");
        t.floor = P.of("polished_blackstone", 2, "polished_basalt[axis=y]", 1);
        t.log = "crimson_stem"; t.planks = "crimson_planks"; t.wood = "crimson";
        t.stairsRoof = "red_nether_brick_stairs"; t.slabRoof = "red_nether_brick_slab"; t.fullRoof = "red_nether_bricks";
        t.fence = "nether_brick_fence"; t.trapdoor = "crimson_trapdoor"; t.stairsTrim = "polished_blackstone_brick_stairs";
        t.light = "soul_lantern"; t.pane = "black_stained_glass_pane";
        t.roof = Roof.SPIRE;
        t.liquid = "lava";
        return t;
    }

    static Theme verdant() {
        Theme t = base();
        t.id = "verdant"; t.name = "Verdant Ruins";
        t.surface = P.of("grass_block", 3, "moss_block", 2);
        t.surface2 = P.of("moss_block", 3, "podzol", 1, "rooted_dirt", 1);
        t.surface3 = P.of("mud", 1, "coarse_dirt", 1, "moss_block", 2);
        t.soil = P.of("dirt", 3, "rooted_dirt", 2, "mud", 1);
        t.bands = new String[] {"mossy_cobblestone", "stone", "andesite", "tuff", "mossy_stone_bricks", "stone", "deepslate", "andesite"};
        t.speck = P.of("emerald_ore", 1, "mossy_cobblestone", 3, "clay", 1);
        t.hangers = new String[] {"vine", "hanging_roots", "glow_lichen[down=true]"};
        t.path = P.of("mossy_cobblestone", 2, "coarse_dirt", 2, "moss_block", 1, "mud_bricks", 1);
        t.trees = new Tree[] {Tree.JUNGLE, Tree.OAK, Tree.AZALEA};
        t.treeWeights = new int[] {5, 2, 2};
        t.treeDensity = 0.018;
        t.flora = P.of("fern", 6, "large_fern[half=lower]", 0, "short_grass", 5, "moss_carpet", 3, "small_dripleaf[half=lower]", 0, "azalea", 1, "flowering_azalea", 1);
        t.bushes = P.of("jungle_leaves[persistent=true]", 2, "oak_leaves[persistent=true]", 1);
        t.wall = P.of("mossy_stone_bricks", 4, "cracked_stone_bricks", 2, "stone_bricks", 2, "mossy_cobblestone", 1);
        t.wall2 = P.of("chiseled_stone_bricks");
        t.trim = P.of("mossy_stone_bricks");
        t.floor = P.of("mossy_stone_bricks", 2, "stone_bricks", 2, "moss_block", 1);
        t.log = "jungle_log"; t.planks = "jungle_planks"; t.wood = "jungle";
        t.stairsRoof = "mossy_stone_brick_stairs"; t.slabRoof = "mossy_stone_brick_slab"; t.fullRoof = "mossy_stone_bricks";
        t.fence = "jungle_fence"; t.trapdoor = "jungle_trapdoor"; t.stairsTrim = "mossy_stone_brick_stairs";
        t.roof = Roof.TIERED;
        return t;
    }

    static Theme tidecrest() {
        Theme t = base();
        t.id = "tidecrest"; t.name = "Tidecrest";
        t.surface = P.of("grass_block", 3, "sand", 1);
        t.surface2 = P.of("sand", 5, "suspicious_sand", 0, "gravel", 1);
        t.surface3 = P.of("grass_block", 3, "coarse_dirt", 1);
        t.soil = P.of("dirt", 2, "sand", 2, "sandstone", 1);
        t.bands = new String[] {"sandstone", "stone", "smooth_sandstone", "andesite", "sandstone", "prismarine", "stone", "dark_prismarine"};
        t.speck = P.of("prismarine", 2, "sea_lantern", 0, "dead_brain_coral_block", 1, "dead_tube_coral_block", 1);
        t.under = P.of("prismarine", 2, "dark_prismarine", 1, "stone", 2, "sandstone", 2);
        t.hangers = new String[] {"vine", "glow_lichen[down=true]"};
        t.path = P.of("sandstone", 2, "smooth_sandstone", 1, "sand", 2, "birch_planks", 1);
        t.trees = new Tree[] {Tree.PALM, Tree.OAK};
        t.treeWeights = new int[] {5, 1};
        t.treeDensity = 0.010;
        t.flora = P.of("short_grass", 6, "fern", 1, "seagrass", 0, "dead_bush", 1, "sea_pickle", 0, "dandelion", 1, "bush", 2);
        t.bushes = P.of("jungle_leaves[persistent=true]");
        t.wall = P.of("white_concrete", 3, "white_terracotta", 1, "smooth_quartz", 2);
        t.wall2 = P.of("prismarine_bricks", 2, "dark_prismarine", 1);
        t.trim = P.of("dark_prismarine");
        t.floor = P.of("birch_planks", 2, "stripped_birch_wood", 1);
        t.log = "stripped_birch_log"; t.planks = "birch_planks"; t.wood = "birch";
        t.stairsRoof = "prismarine_brick_stairs"; t.slabRoof = "prismarine_brick_slab"; t.fullRoof = "prismarine_bricks";
        t.fence = "birch_fence"; t.trapdoor = "birch_trapdoor"; t.stairsTrim = "dark_prismarine_stairs";
        t.light = "sea_lantern";
        return t;
    }

    static Theme mycelia() {
        Theme t = base();
        t.id = "mycelia"; t.name = "Mycelia";
        t.surface = P.of("mycelium");
        t.surface2 = P.of("podzol", 3, "moss_block", 1);
        t.surface3 = P.of("mycelium", 2, "rooted_dirt", 1, "coarse_dirt", 1);
        t.soil = P.of("dirt", 4, "rooted_dirt", 2);
        t.bands = new String[] {"stone", "granite", "andesite", "stone", "dripstone_block", "tuff", "granite", "deepslate"};
        t.speck = P.of("brown_mushroom_block[down=false,up=true]", 1, "red_mushroom_block", 1, "gravel", 2);
        t.hangers = new String[] {"hanging_roots", "glow_lichen[down=true]", "vine"};
        t.path = P.of("coarse_dirt", 3, "podzol", 2, "dirt_path", 2, "gravel", 1);
        t.trees = new Tree[] {Tree.RED_MUSHROOM, Tree.BROWN_MUSHROOM, Tree.DEAD};
        t.treeWeights = new int[] {4, 4, 1};
        t.treeDensity = 0.010;
        t.flora = P.of("red_mushroom", 3, "brown_mushroom", 3, "short_grass", 2, "fern", 1);
        t.floraDensity = 0.14;
        t.bushes = P.of("brown_mushroom_block", 2, "red_mushroom_block", 1);
        t.wall = P.of("mushroom_stem", 4, "white_terracotta", 1);
        t.wall2 = P.of("brown_mushroom_block", 1);
        t.trim = P.of("stripped_oak_wood");
        t.floor = P.of("oak_planks", 2, "stripped_oak_wood", 1);
        t.log = "stripped_oak_log"; t.planks = "oak_planks"; t.wood = "oak";
        t.stairsRoof = "red_nether_brick_stairs"; t.slabRoof = "red_nether_brick_slab"; t.fullRoof = "red_mushroom_block";
        t.fence = "oak_fence"; t.trapdoor = "oak_trapdoor"; t.stairsTrim = "oak_stairs";
        t.light = "shroomlight";
        return t;
    }

    static Theme palegrove() {
        Theme t = base();
        t.id = "palegrove"; t.name = "Pale Grove";
        t.surface = P.of("pale_moss_block", 3, "grass_block", 1);
        t.surface2 = P.of("pale_moss_block", 2, "coarse_dirt", 1, "podzol", 1);
        t.surface3 = P.of("rooted_dirt", 1, "pale_moss_block", 2, "mud", 1);
        t.soil = P.of("dirt", 3, "rooted_dirt", 2, "coarse_dirt", 1);
        t.bands = new String[] {"deepslate", "tuff", "cobbled_deepslate", "calcite", "deepslate", "tuff", "smooth_basalt", "deepslate"};
        t.speck = P.of("resin_block", 1, "cobbled_deepslate", 3, "deepslate_coal_ore", 1);
        t.under = P.of("deepslate", 3, "tuff", 2, "cobbled_deepslate", 2);
        t.hangers = new String[] {"pale_hanging_moss[tip=true]", "hanging_roots"};
        t.path = P.of("cobbled_deepslate", 2, "deepslate_tiles", 1, "coarse_dirt", 2, "pale_moss_carpet", 1);
        t.trees = new Tree[] {Tree.PALE, Tree.DEAD};
        t.treeWeights = new int[] {8, 1};
        t.treeDensity = 0.014;
        t.flora = P.of("pale_moss_carpet", 5, "short_grass", 2, "fern", 2, "open_eyeblossom", 1, "closed_eyeblossom", 1);
        t.bushes = P.of("pale_oak_leaves[persistent=true]");
        t.wall = P.of("deepslate_bricks", 5, "cracked_deepslate_bricks", 2, "deepslate_tiles", 1);
        t.wall2 = P.of("pale_oak_planks", 2, "stripped_pale_oak_log[axis=y]", 1);
        t.trim = P.of("polished_deepslate");
        t.floor = P.of("pale_oak_planks", 2, "stripped_pale_oak_wood", 1);
        t.log = "pale_oak_log"; t.planks = "pale_oak_planks"; t.wood = "pale_oak";
        t.stairsRoof = "deepslate_tile_stairs"; t.slabRoof = "deepslate_tile_slab"; t.fullRoof = "deepslate_tiles";
        t.fence = "pale_oak_fence"; t.trapdoor = "pale_oak_trapdoor"; t.stairsTrim = "deepslate_brick_stairs";
        t.light = "soul_lantern";
        t.roof = Roof.SPIRE;
        return t;
    }

    static Theme skyreach() {
        Theme t = base();
        t.id = "skyreach"; t.name = "Skyreach";
        t.surface = P.of("end_stone", 4, "end_stone_bricks", 0);
        t.surface2 = P.of("end_stone", 2, "purpur_block", 0, "moss_block", 0, "calcite", 1);
        t.surface3 = P.of("end_stone", 3, "obsidian", 0, "smooth_quartz", 0, "amethyst_block", 1);
        t.soil = P.of("end_stone", 4, "calcite", 1);
        t.bands = new String[] {"end_stone", "calcite", "end_stone", "purpur_block", "end_stone", "smooth_basalt", "end_stone", "amethyst_block"};
        t.speck = P.of("amethyst_block", 2, "budding_amethyst", 0, "obsidian", 1, "crying_obsidian", 1);
        t.under = P.of("end_stone", 3, "obsidian", 1, "calcite", 1);
        t.hangers = new String[] {"amethyst_cluster[facing=down]", "chorus_plant[up=true,down=true]"};
        t.path = P.of("end_stone_bricks", 3, "purpur_block", 1, "polished_blackstone", 0);
        t.trees = new Tree[] {Tree.CHORUS};
        t.treeWeights = new int[] {1};
        t.treeDensity = 0.005;
        t.flora = P.of("small_amethyst_bud[facing=up]", 2, "medium_amethyst_bud[facing=up]", 1, "end_rod[facing=up]", 1);
        t.floraDensity = 0.04;
        t.bushes = P.of("purpur_block", 1);
        t.boulder = P.of("end_stone", 3, "obsidian", 1, "amethyst_block", 1);
        t.wall = P.of("purpur_block", 4, "purpur_pillar[axis=y]", 1, "end_stone_bricks", 2);
        t.wall2 = P.of("end_stone_bricks", 2);
        t.trim = P.of("purpur_pillar[axis=y]");
        t.floor = P.of("purpur_block", 2, "end_stone_bricks", 1);
        t.log = "purpur_pillar"; t.planks = "purpur_block"; t.wood = "warped";
        t.stairsRoof = "purpur_stairs"; t.slabRoof = "purpur_slab"; t.fullRoof = "purpur_block";
        t.fence = "warped_fence"; t.trapdoor = "warped_trapdoor"; t.stairsTrim = "end_stone_brick_stairs";
        t.light = "end_rod[facing=up]"; t.pane = "purple_stained_glass_pane";
        t.roof = Roof.SPIRE;
        return t;
    }
}
