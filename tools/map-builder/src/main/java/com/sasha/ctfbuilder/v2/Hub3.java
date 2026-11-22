package com.sasha.ctfbuilder.v2;

import com.sasha.ctfbuilder.v2.Arch.Room;
import com.sasha.ctfbuilder.v2.Terrain.Island;
import com.sasha.ctfbuilder.v2.W.P;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

import java.util.Locale;

/**
 * The CTF hub, "Skyhold" (second edition). A garden commons with a three-tier fountain in the middle, and
 * five islands round it, each with a reason to visit: the Armoury (every kit on a mannequin in your own
 * skin), the Observatory (watch the match, vote for maps), the Broken Flagpole tavern (stats, gossip) and
 * the red and blue war camps. Solid bridges link them all.
 */
public final class Hub3 {

    private Hub3() {}

    static final int Y = 64;
    static final String[] KITS = {"barbarian", "big_spender", "builder", "choruser", "demoman", "engineer", "fire_archer", "infiltrator", "medic",
            "necromancer", "ninja", "phoenix", "redstone_mechanic", "seeking_archer", "sheep_farmer", "shielder", "spy", "tank", "thor"};

    /** Returns {hub spawn, hub-npcs.yml}. */
    public static String[] build(World world) {
        for (int cx = -26; cx <= 26; cx++) for (int cz = -26; cz <= 26; cz++) world.getChunkAt(cx, cz).load(true);
        W w = new W(world, 777_2026, 1);
        Theme t = theme();
        Terrain land = new Terrain(w, t);
        Arch a = new Arch(w, t, land);
        Flora flora = new Flora(w, t, land);
        StringBuilder npcs = new StringBuilder();
        NPCS.setLength(0);

        land.add(new Island("core", 0, 0, 50, 44, 0.1, Y).hill(2).depth(44).trees(0.35)
                .lobes(new double[] {18, 20, 26}, new double[] {-16, -20, 24}));
        land.add(new Island("armoury", 86, -8, 25, 19, 0.15, Y + 3).hill(1).depth(28).trees(0.3));
        land.add(new Island("observatory", -4, -86, 20, 20, 0.0, Y + 6).hill(2).depth(30).trees(0.4));
        land.add(new Island("tavern", -88, 4, 23, 20, -0.2, Y + 2).hill(1).depth(26).trees(0.4));
        land.add(new Island("redcamp", -54, 72, 19, 17, 0.5, Y - 2).hill(1).depth(22).trees(0.3));
        land.add(new Island("bluecamp", 56, 72, 19, 17, -0.5, Y - 2).hill(1).depth(22).trees(0.3));
        land.add(new Island("isle1", 62, -60, 9, 8, 0, Y + 10).depth(16).trees(0.6));
        land.add(new Island("isle2", -62, -58, 9, 9, 0, Y + 8).depth(14).trees(0.6));
        land.add(new Island("isle3", 2, 96, 11, 8, 0.3, Y - 7).depth(16).trees(0.5));
        land.add(new Island("rock1", 30, -78, 4, 4, 0, Y + 14).depth(10));
        land.add(new Island("rock2", -96, -40, 5, 4, 0, Y - 3).depth(10));
        land.flats.add(new Terrain.Flat(0, 0, 24, Y));
        land.flats.add(new Terrain.Flat(86, -8, 17, Y + 3));
        land.flats.add(new Terrain.Flat(-4, -86, 13, Y + 6));
        land.flats.add(new Terrain.Flat(-88, 4, 15, Y + 2));
        land.flats.add(new Terrain.Flat(-54, 72, 13, Y - 2));
        land.flats.add(new Terrain.Flat(56, 72, 13, Y - 2));
        land.build();

        commons(w, t, a, land);
        armoury(w, t, a, land, npcs);
        observatory(w, t, a, land);
        tavern(w, t, a, land);
        camp(w, a, land, -54, 72, Y - 2, "red");
        camp(w, a, land, 56, 72, Y - 2, "blue");
        islets(w, t, a, land);

        // bridges: solid decks with railings, only over the void
        a.bridge(40, Y, -5, 66, Y + 3, -8, "spruce_planks", "stripped_spruce_log[axis=y]", "spruce_fence", true);
        a.bridge(-3, Y, -36, -4, Y + 6, -70, "spruce_planks", "stripped_spruce_log[axis=y]", "spruce_fence", true);
        a.bridge(-44, Y, 2, -68, Y + 2, 4, "spruce_planks", "stripped_spruce_log[axis=y]", "spruce_fence", true);
        a.bridge(-28, Y, 34, -46, Y - 2, 58, "spruce_planks", "stripped_spruce_log[axis=y]", "spruce_fence", true);
        a.bridge(28, Y, 34, 48, Y - 2, 58, "spruce_planks", "stripped_spruce_log[axis=y]", "spruce_fence", true);
        // paths from the commons to each bridge, and from each bridge to its building
        land.path(1, 0, 16, 0, 26, -14, 30, -26, 34);
        land.path(1, 0, 16, 0, 26, 14, 30, 26, 34);
        land.path(1, 16, 0, 30, -2, 42, -5);
        land.path(1, -16, 0, -30, 1, -44, 2);
        land.path(1, 0, -16, -2, -28, -3, -38);
        land.path(1, 66, -8, 72, -8);
        land.path(1, -4, -70, -4, -78);
        land.path(1, -68, 4, -76, 4);
        land.path(1, -46, 58, -54, 60);
        land.path(1, 48, 58, 56, 60);
        waterfall(w, land, -34, -30);
        waterfall(w, land, 38, 26);
        waterfall(w, land, 94, 6);
        waterfall(w, land, -96, 18);
        flora.decorate();
        w.joinAll();
        String yml = "# Where the hub's characters stand (written by the map builder).\n"
                + "armoury: {x: 86.5, y: " + (Y + 4) + ", z: -7.5, radius: 34}\n" + npcs + "npcs:\n" + NPCS;
        return new String[] {"0.5 " + (Y + 1) + " 18.5 180 0", yml};
    }

    static Theme theme() {
        Theme t = Theme.get("kingdoms");
        t.trees = new Theme.Tree[] {Theme.Tree.CHERRY, Theme.Tree.AZALEA, Theme.Tree.BIRCH, Theme.Tree.SPRUCE};
        t.treeWeights = new int[] {5, 4, 2, 1};
        t.treeDensity = 0.006;
        t.flora = P.of("short_grass", 10, "poppy", 1, "dandelion", 1, "cornflower", 2, "oxeye_daisy", 1, "azure_bluet", 1, "allium", 1,
                "pink_tulip", 1, "lily_of_the_valley", 1, "fern", 2, "pink_petals[flower_amount=3]", 3, "bush", 1);
        t.floraDensity = 0.16;
        t.bushes = P.of("azalea_leaves[persistent=true]", 2, "flowering_azalea_leaves[persistent=true]", 2, "oak_leaves[persistent=true]", 1);
        t.bushDensity = 0.0015;
        t.wall = P.of("stone_bricks", 6, "cracked_stone_bricks", 1, "mossy_stone_bricks", 1);
        t.wall2 = P.of("white_terracotta", 3, "calcite", 1);
        t.trim = P.of("polished_andesite");
        t.log = "dark_oak_log"; t.planks = "dark_oak_planks"; t.wood = "dark_oak"; t.fence = "dark_oak_fence"; t.trapdoor = "dark_oak_trapdoor";
        t.stairsRoof = "deepslate_tile_stairs"; t.slabRoof = "deepslate_tile_slab"; t.fullRoof = "deepslate_tiles";
        t.stairsTrim = "stone_brick_stairs";
        t.floor = P.of("dark_oak_planks", 3, "stripped_dark_oak_wood", 1);
        t.path = P.of("dirt_path", 5, "coarse_dirt", 1, "gravel", 1);
        return t;
    }

    // ---------------------------------------------------------------- the commons

    static void commons(W w, Theme t, Arch a, Terrain land) {
        // a ring walk and four garden quarters; the paving is gravel and old stone set into the grass
        for (int x = -23; x <= 23; x++)
            for (int z = -23; z <= 23; z++) {
                double d = Math.hypot(x, z);
                if (d > 23) continue;
                for (int dy = 1; dy <= 10; dy++) if (!w.air(x, Y + dy, z)) w.set(x, Y + dy, z, "air");
                boolean ring = Math.abs(d - 13) < 1.6;
                boolean cross = (Math.abs(x) <= 1 || Math.abs(z) <= 1) && d > 8;
                boolean inner = d <= 9.5;
                String block;
                if (inner) block = d > 8.6 ? "mossy_stone_bricks" : ((x * 3 + z * 7) & 3) == 0 ? "mossy_cobblestone" : "stone_bricks";
                else if (ring || cross) block = w.r.nextInt(6) == 0 ? "coarse_dirt" : w.r.nextInt(5) == 0 ? "cobblestone" : "gravel";
                else block = "grass_block";
                w.set(x, Y, z, block);
            }
        fountain(w, 0, Y, 0);
        // hedges and flower beds in the four quarters
        for (int q = 0; q < 4; q++) {
            double ang = q * Math.PI / 2 + Math.PI / 4;
            for (double rr = 15.5; rr <= 21.5; rr += 0.5)
                for (double ad = -0.45; ad <= 0.45; ad += 0.02) {
                    int x = (int) Math.round(Math.cos(ang + ad) * rr), z = (int) Math.round(Math.sin(ang + ad) * rr);
                    if (Math.abs(x) <= 2 || Math.abs(z) <= 2) continue;
                    boolean hedge = rr >= 21 || Math.abs(ad) > 0.42;
                    if (hedge) {
                        w.set(x, Y + 1, z, (x + z & 3) == 0 ? "flowering_azalea_leaves[persistent=true]" : "azalea_leaves[persistent=true]");
                    } else if (w.air(x, Y + 1, z) && w.type(x, Y, z) == org.bukkit.Material.GRASS_BLOCK) {
                        String[] flowers = {"cornflower", "allium", "pink_tulip", "oxeye_daisy", "lily_of_the_valley", "poppy", "azure_bluet"};
                        w.set(x, Y + 1, z, w.r.nextInt(5) == 0 ? "short_grass" : flowers[(int) ((rr * 2 + q) % flowers.length)]);
                    }
                }
            // a cherry tree just outside each quarter's hedge, framing the garden without covering it
            new Flora(w, t, land).tree(Theme.Tree.CHERRY, (int) Math.round(Math.cos(ang + 0.3) * 28), Y + 1, (int) Math.round(Math.sin(ang + 0.3) * 28));
        }
        // benches facing the fountain, lamp posts around the ring
        for (int i = 0; i < 8; i++) {
            double ang = i * Math.PI / 4 + Math.PI / 8;
            int x = (int) Math.round(Math.cos(ang) * 11), z = (int) Math.round(Math.sin(ang) * 11);
            String back = Math.abs(x) > Math.abs(z) ? (x > 0 ? "east" : "west") : (z > 0 ? "south" : "north");
            w.set(x, Y + 1, z, Arch.st("dark_oak_stairs", back, false));
            int sx = Math.abs(x) > Math.abs(z) ? 0 : 1, sz = 1 - sx;
            w.set(x + sx, Y + 1, z + sz, "dark_oak_trapdoor[facing=" + (sx == 1 ? "east" : "south") + ",open=true,half=bottom]");
            w.set(x - sx, Y + 1, z - sz, "dark_oak_trapdoor[facing=" + (sx == 1 ? "west" : "north") + ",open=true,half=bottom]");
            int lx = (int) Math.round(Math.cos(ang + 0.2) * 15.2), lz = (int) Math.round(Math.sin(ang + 0.2) * 15.2);
            a.lampPost(lx, Y + 1, lz, 3);
        }
        // four team beacons, set in raised planters on the diagonals
        String[] teams = {"red", "blue", "lime", "yellow"};
        int[][] spots = {{-10, -10}, {10, 10}, {10, -10}, {-10, 10}};
        for (int i = 0; i < 4; i++) {
            int x = spots[i][0], z = spots[i][1];
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                boolean rim = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                w.set(x + dx, Y, z + dz, rim ? "mossy_stone_bricks" : "grass_block");
                w.set(x + dx, Y + 1, z + dz, rim ? "stone_brick_slab[type=bottom]" : "air");
            }
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) w.set(x + dx, Y - 1, z + dz, "iron_block");
            w.set(x, Y, z, "beacon");
            w.set(x, Y + 1, z, teams[i] + "_stained_glass");
            String flower = switch (teams[i]) {
                case "red" -> "red_tulip";
                case "blue" -> "cornflower";
                case "lime" -> "lily_of_the_valley";
                default -> "dandelion";
            };
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) w.set(x + d[0], Y + 1, z + d[1], flower);
        }
        // the welcome sign in front of the spawn, low and readable
        label(w.w, 0.5, Y + 3.4, 11.5, "FRACTAL CTF", NamedTextColor.GOLD, true, 1.4f);
        label(w.w, 0.5, Y + 2.95, 11.5, "Capture the Flag  •  King of the Hill  •  Destroy the Wool", NamedTextColor.WHITE, false, 0.8f);
        land.keeps.add(new Terrain.Keep(0, 0, 24));
    }

    /** A three-tier fountain: a wide basin, a raised bowl, and a spout on a column, all spilling down. */
    static void fountain(W w, int cx, int y, int cz) {
        // basin
        for (int dx = -7; dx <= 7; dx++)
            for (int dz = -7; dz <= 7; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > 7.3) continue;
                int x = cx + dx, z = cz + dz;
                if (d > 6.4) {
                    w.set(x, y, z, "polished_andesite");
                    w.set(x, y + 1, z, "stone_brick_slab[type=bottom]");
                } else {
                    w.set(x, y - 2, z, "mossy_stone_bricks");
                    w.set(x, y - 1, z, "water");
                    w.set(x, y, z, "water");
                    if (w.r.nextInt(14) == 0 && d > 3.5) w.set(x, y + 1, z, "lily_pad");
                }
            }
        // the raised bowl on a column
        for (int dy = -1; dy <= 2; dy++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) w.set(cx + dx, y + dy, cz + dz, "chiseled_stone_bricks");
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > 3.3) continue;
                int x = cx + dx, z = cz + dz;
                w.set(x, y + 3, z, "polished_andesite");
                if (d > 2.4) w.set(x, y + 4, z, "stone_brick_wall");
                else w.set(x, y + 4, z, "water");
            }
        // notches in the bowl's rim: water spills out and falls into the basin
        for (int[] d : new int[][] {{3, 0}, {-3, 0}, {0, 3}, {0, -3}}) {
            int x = cx + d[0], z = cz + d[1];
            w.set(x, y + 4, z, "water[level=1]");
            int ox = Integer.signum(d[0]), oz = Integer.signum(d[1]);
            for (int yy = y + 1; yy <= y + 4; yy++) w.set(x + ox, yy, z + oz, "water[level=8]");
        }
        // the spout column and the top bowl
        for (int dy = 5; dy <= 7; dy++) w.set(cx, y + dy, cz, dy == 7 ? "chiseled_stone_bricks" : "stone_brick_wall");
        w.set(cx, y + 8, cz, "water");
        for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            w.set(cx + d[0], y + 8, cz + d[1], "water[level=1]");
            for (int yy = y + 5; yy <= y + 7; yy++) w.set(cx + d[0], yy, cz + d[1], "water[level=8]");
        }
        for (int[] d : new int[][] {{1, 1}, {-1, 1}, {1, -1}, {-1, -1}}) w.set(cx + d[0], y + 8, cz + d[1], "stone_brick_slab[type=bottom]");
    }

    // ---------------------------------------------------------------- the armoury

    static void armoury(W w, Theme t, Arch a, Terrain land, StringBuilder npcs) {
        int y = Y + 3, x0 = 74, x1 = 98, z0 = -16, z1 = 0;
        int door = -8;
        a.foundation(x0 - 3, z0, x1, z1, y, P.of("stone_bricks"));
        a.storey(x0, z0, x1, z1, y, 8, P.of("stone_bricks", 6, "cracked_stone_bricks", 1, "mossy_stone_bricks", 1), "polished_andesite",
                "stripped_dark_oak_log", P.of("polished_andesite", 2, "andesite", 1), 4);
        // floor: a checker of stone and dark oak, with a red runner up the middle
        for (int x = x0 + 1; x < x1; x++) for (int z = z0 + 1; z < z1; z++) w.set(x, y, z, ((x + z) & 1) == 0 ? "polished_andesite" : "dark_oak_planks");
        a.windows(x0, z0, x1, z1, y, 3, 3, 4, "glass_pane", false);
        // the entrance: a portico with columns and a little roof, the label above at head height
        a.archway(x0, y, door, "west", 3, 4, "stone_brick_stairs");
        for (int z = door - 3; z <= door + 3; z++) for (int x = x0 - 3; x <= x0 - 1; x++) w.set(x, y, z, "polished_andesite");
        for (int side : new int[] {-3, 3}) {
            for (int dy = 1; dy <= 4; dy++) w.set(x0 - 3, y + dy, door + side, dy == 1 ? "chiseled_stone_bricks" : "stone_brick_wall");
            w.set(x0 - 2, y + 4, door + side, "stone_bricks");
        }
        a.gable(x0 - 3, door - 3, x0, door + 3, y + 5, true, 0, "deepslate_tile_stairs", "deepslate_tile_slab", "deepslate_tiles", P.of("stone_bricks"));
        for (int dy = 0; dy <= 0; dy++) w.set(x0 - 1, y + 4, door, "lantern[hanging=true]");
        a.banner(x0 - 1, y + 3, door - 2, "red", "west");
        a.banner(x0 - 1, y + 3, door + 2, "blue", "west");
        // roof, with beams across inside to hang the chandeliers from
        a.gable(x0, z0, x1, z1, y + 9, true, 1);
        for (int x = x0 + 4; x < x1; x += 4) for (int z = z0 + 1; z < z1; z++) w.set(x, y + 8, z, "stripped_dark_oak_log[axis=z]");
        for (int x = x0 + 4; x < x1 - 3; x += 4) w.hangLight(x, y + 6, door, "lantern");
        // the aisle
        for (int x = x0 + 1; x < x1 - 4; x++) for (int z = door - 1; z <= door + 1; z++) w.set(x, y + 1, z, z == door ? "red_carpet" : "gray_carpet");
        // pedestals for the mannequins (the plugin stands a kit on each, in the viewer's own skin)
        int k = 0;
        for (int x = x0 + 2; x <= x1 - 4 && k < KITS.length; x += 2) {
            for (int side = 0; side < 2 && k < KITS.length; side++) {
                int z = side == 0 ? z0 + 2 : z1 - 2;
                w.set(x, y + 1, z, "polished_andesite");
                w.set(x, y + 1, z + (side == 0 ? -1 : 1), "stone_brick_stairs[facing=" + (side == 0 ? "south" : "north") + "]");
                npcs.append(k == 0 ? "kits:\n" : "").append(String.format(Locale.ROOT, "  - {kit: %s, x: %.1f, y: %d, z: %.1f, yaw: %d, team: %s}%n",
                        KITS[k], x + 0.5, y + 2, z + 0.5, side == 0 ? 0 : 180, k % 2 == 0 ? "red" : "blue"));
                k++;
            }
        }
        // banners in the team colours on the walls between the windows, and weapon racks under them
        for (int x = x0 + 2; x <= x1 - 4; x += 4) {
            a.banner(x, y + 6, z0 + 1, (x / 4 & 1) == 0 ? "red" : "blue", "south");
            a.banner(x, y + 6, z1 - 1, (x / 4 & 1) == 0 ? "blue" : "red", "north");
        }
        // the far end: the armourer's dais, a forge and weapon racks
        for (int z = z0 + 4; z <= z1 - 4; z++) for (int x = x1 - 3; x < x1; x++) w.set(x, y + 1, z, "polished_andesite");
        for (int z = z0 + 4; z <= z1 - 4; z++) w.set(x1 - 4, y + 1, z, "stone_brick_stairs[facing=east]");
        w.set(x1 - 1, y + 2, door - 3, "smithing_table");
        w.set(x1 - 1, y + 2, door + 3, "anvil[facing=north]");
        w.set(x1 - 1, y + 2, door - 2, "grindstone[face=floor,facing=north]");
        w.set(x1 - 1, y + 2, door + 2, "barrel[facing=up]");
        a.fireplace(x1, y + 1, door, "east", y + 14);
        a.banner(x1 - 1, y + 6, door - 4, "red", "west");
        a.banner(x1 - 1, y + 6, door + 4, "blue", "west");
        NPCS.append(npc("armourer", "<gold><bold>Armourer", "kits", "weaponsmith", "plains", x1 - 2.5, y + 2, door + 0.5, 90,
                "Every kit's on a stand: <yellow>right-click</yellow> to look, <yellow>left-click</yellow> to equip.",
                "Earn coins in matches, then come back and unlock something new.",
                "The Tank can take a beating. The Ninja can take a flag."));
        label(w.w, x0 - 3.5, y + 3.6, door + 0.5, "ARMOURY", NamedTextColor.GOLD, true, 1.2f);
        label(w.w, x0 - 3.5, y + 3.2, door + 0.5, "Right-click a kit to preview it", NamedTextColor.WHITE, false, 0.7f);
        land.keeps.add(new Terrain.Keep(86, -8, 19));
    }

    // ---------------------------------------------------------------- the observatory

    static void observatory(W w, Theme t, Arch a, Terrain land) {
        int y = Y + 6, cx = -4, cz = -90, r = 7;
        // a stone tower with a white dome and a telescope looking out of the slit
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > r + 0.45) continue;
                boolean shell = d > r - 0.8;
                for (int dy = 0; dy <= 16; dy++) {
                    int x = cx + dx, z = cz + dz, yy = y + dy;
                    if (shell) {
                        String spec = dy == 0 ? "polished_andesite" : dy == 6 || dy == 12 ? "polished_deepslate" : (dy % 6 >= 2 && dy % 6 <= 4)
                                && (Math.abs(dx) <= 1 || Math.abs(dz) <= 1) && !(dz > 0 && Math.abs(dx) <= 1 && dy < 6) ? "glass_pane" : w.r.nextInt(6) == 0 ? "cracked_stone_bricks" : "stone_bricks";
                        w.set(x, yy, z, spec);
                    } else {
                        w.set(x, yy, z, dy == 0 || dy == 6 || dy == 12 ? "spruce_planks" : "air");
                    }
                }
            }
        // buttresses, a band of spruce at each floor, and a balcony ring under the dome
        for (int i = 0; i < 8; i++) {
            double ang = i * Math.PI / 4 + Math.PI / 8;
            int bx = cx + (int) Math.round(Math.cos(ang) * (r + 1)), bz = cz + (int) Math.round(Math.sin(ang) * (r + 1));
            if (Math.abs(bx - cx) <= 1 && bz > cz) continue;
            for (int dy = 0; dy <= 9; dy++) w.set(bx, y + dy, bz, dy == 9 ? "stone_brick_slab[type=bottom]" : "stone_bricks");
        }
        for (int dx = -r - 2; dx <= r + 2; dx++)
            for (int dz = -r - 2; dz <= r + 2; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > r + 2.4 || d <= r + 0.45) continue;
                w.set(cx + dx, y + 16, cz + dz, d > r + 1.5 ? "stripped_spruce_log[axis=y]" : "spruce_planks");
                if (d > r + 1.5) w.set(cx + dx, y + 17, cz + dz, "spruce_fence");
                w.set(cx + dx, y + 15, cz + dz, "spruce_stairs[facing=" + (Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? "west" : "east") : (dz > 0 ? "north" : "south")) + ",half=top]");
            }
        for (int i = 0; i < 4; i++) {
            double ang = i * Math.PI / 2;
            w.set(cx + (int) Math.round(Math.cos(ang) * (r + 2)), y + 18, cz + (int) Math.round(Math.sin(ang) * (r + 2)), "lantern[hanging=false]");
        }
        w.set(cx + r, y + 13, cz, "air");
        w.set(cx + r, y + 14, cz, "air");
        // dome
        a.dome(cx, y + 17, cz, r + 0.4, "smooth_quartz", "lightning_rod");
        for (int dy = 0; dy <= 6; dy++) w.set(cx, y + 17 + dy, cz - r + 1 + dy / 2, "air"); // the slit
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) if (Math.hypot(dx, dz) <= r - 0.8) w.set(cx + dx, y + 16, cz + dz, "spruce_planks");
        // the telescope: a tube of copper and a spyglass end pointing at the slit
        for (int i = 0; i < 6; i++) w.set(cx, y + 18 + i / 2, cz + 2 - i, i < 5 ? "waxed_copper_block" : "waxed_cut_copper");
        w.set(cx, y + 17, cz + 2, "waxed_copper_grate");
        w.set(cx, y + 17, cz + 3, "lectern[facing=north]");
        // door to the south, steps, and stairs up the inside wall
        a.door(cx, y, cz + r, "south", "spruce_door");
        for (int dy = 1; dy <= 2; dy++) w.set(cx, y + dy, cz + r, dy == 1 ? "spruce_door[facing=north,half=lower,hinge=left]" : "spruce_door[facing=north,half=upper,hinge=left]");
        for (int dy = 1; dy <= 16; dy++) w.set(cx - r + 1, y + dy, cz, "ladder[facing=east]");
        for (int f : new int[] {6, 12, 16}) w.set(cx - r + 1, y + f, cz, "ladder[facing=east]");
        // ground floor: a round library with a star-map table
        a.shelves(cx - 5, cz - 5, cx + 5, cz - 5, y + 1, 3, "south");
        for (int dz = -4; dz <= 1; dz++) {
            if (Math.hypot(5, dz) <= r - 0.8) {
                w.set(cx + 5, y + 1, cz + dz, "bookshelf");
                w.set(cx + 5, y + 2, cz + dz, "bookshelf");
            }
        }
        w.set(cx, y + 1, cz, "cartography_table");
        w.set(cx + 1, y + 1, cz, "lectern[facing=west]");
        a.chair(cx - 1, y + 1, cz, "west");
        a.chair(cx, y + 1, cz + 1, "south");
        a.rug(cx - 2, cz - 2, cx + 2, cz + 2, y + 1, "blue", "light_blue");
        w.hangLight(cx, y + 5, cz, "lantern");
        // middle floor: an astronomer's study
        w.set(cx + 3, y + 7, cz - 3, "enchanting_table");
        a.shelves(cx + 2, cz - 5, cx + 4, cz - 5, y + 7, 2, "south");
        w.set(cx - 3, y + 7, cz + 3, "brewing_stand");
        a.bed(cx + 2, y + 7, cz + 3, "east", "blue");
        w.set(cx - 2, y + 7, cz - 3, "chest[facing=south]");
        w.hangLight(cx, y + 11, cz, "lantern");
        // the courtyard in front, with a sundial and benches
        for (int dx = -6; dx <= 6; dx++)
            for (int dz = 0; dz <= 9; dz++) {
                double d = Math.hypot(dx, dz - 4);
                if (d > 6) continue;
                int x = cx + dx, z = cz + r + 1 + dz;
                w.set(x, y, z, d > 5.2 ? "mossy_stone_bricks" : (x + z & 1) == 0 ? "stone_bricks" : "polished_andesite");
                for (int dy = 1; dy <= 6; dy++) if (!w.air(x, y + dy, z)) w.set(x, y + dy, z, "air");
            }
        int sx = cx, sz = cz + r + 5;
        w.set(sx, y + 1, sz, "chiseled_stone_bricks");
        w.set(sx, y + 2, sz, "lightning_rod");
        for (int side : new int[] {-1, 1}) {
            w.set(cx + side * 4, y + 1, sz, Arch.st("spruce_stairs", side < 0 ? "west" : "east", false));
            w.set(cx + side * 4, y + 1, sz + 1, Arch.st("spruce_stairs", side < 0 ? "west" : "east", false));
            a.lampPost(cx + side * 5, y + 1, cz + r + 2, 3);
        }
        NPCS.append(npc("astronomer", "<aqua><bold>Astronomer", "spectate", "cartographer", "snow", cx - 2.5, y + 1, cz + r + 3.5, 180,
                "From up here you can see every island. Want to watch the match?",
                "I'll send you over as a spectator. Nobody will see you, promise."));
        NPCS.append(npc("map-scout", "<yellow><bold>Map Scout", "vote", "cartographer", "plains", cx + 3.5, y + 1, cz + r + 3.5, 180,
                "Ten islands charted so far. Which one next? Cast your vote.",
                "Small maps are quick and bloody. The big ones need bridges and patience."));
        label(w.w, cx + 0.5, y + 4.0, cz + r + 10.5, "OBSERVATORY", NamedTextColor.AQUA, true, 1.2f);
        label(w.w, cx + 0.5, y + 3.6, cz + r + 10.5, "Watch the match  •  Vote for the next map", NamedTextColor.WHITE, false, 0.7f);
        land.keeps.add(new Terrain.Keep(cx, cz + 4, 15));
    }

    // ---------------------------------------------------------------- the tavern

    static void tavern(W w, Theme t, Arch a, Terrain land) {
        int y = Y + 2, x0 = -98, x1 = -80, z0 = -4, z1 = 8;
        a.foundation(x0, z0, x1 + 6, z1, y, P.of("cobblestone"));
        P plaster = P.of("white_terracotta", 4, "calcite", 1);
        a.storey(x0, z0, x1, z1, y, 5, P.of("cobblestone", 3, "mossy_cobblestone", 1, "stone_bricks", 1), "stripped_spruce_log", "spruce_log", P.of("spruce_planks", 3, "stripped_spruce_wood", 1), 3);
        a.storey(x0, z0, x1, z1, y + 5, 5, plaster, "stripped_spruce_log", "spruce_log", P.of("spruce_planks"), 3);
        // jettied upper floor: a row of upside-down stairs under the upper walls
        for (int x = x0; x <= x1; x++) {
            w.set(x, y + 5, z0 - 1, "spruce_stairs[facing=south,half=top]");
            w.set(x, y + 5, z1 + 1, "spruce_stairs[facing=north,half=top]");
        }
        a.windows(x0, z0, x1, z1, y, 2, 2, 3, "glass_pane", true);
        a.windows(x0, z0, x1, z1, y + 5, 2, 2, 3, "glass_pane", true);
        int door = 2;
        a.door(x1, y, door, "east", "spruce_door");
        a.gable(x0, z0, x1, z1, y + 11, true, 1, "spruce_stairs", "spruce_slab", "spruce_planks", plaster);
        // the porch and the sign
        for (int z = door - 3; z <= door + 3; z++) for (int x = x1 + 1; x <= x1 + 3; x++) w.set(x, y, z, "spruce_planks");
        for (int side : new int[] {-3, 3}) for (int dy = 1; dy <= 3; dy++) w.set(x1 + 3, y + dy, door + side, "stripped_spruce_log[axis=y]");
        for (int z = door - 3; z <= door + 3; z++) w.set(x1 + 3, y + 4, z, "spruce_slab[type=bottom]");
        for (int z = door - 3; z <= door + 3; z++) for (int x = x1 + 1; x <= x1 + 2; x++) w.set(x, y + 4, z, "spruce_slab[type=bottom]");
        w.set(x1 + 2, y + 3, door, "lantern[hanging=true]");
        w.set(x1 + 1, y + 1, door - 2, Arch.st("spruce_stairs", "west", false));
        w.set(x1 + 1, y + 1, door + 2, Arch.st("spruce_stairs", "west", false));
        // ground floor: the bar along the north wall, tables, a fireplace on the west wall
        for (int x = x0 + 2; x <= x0 + 10; x++) {
            w.set(x, y + 1, z0 + 2, x == x0 + 10 ? "spruce_planks" : "stripped_spruce_wood");
            w.set(x, y + 2, z0 + 2, "spruce_slab[type=bottom]");
            if (x % 2 == 0) w.set(x, y + 1, z0 + 3, "spruce_fence");
            if (x % 2 == 0) w.set(x, y + 2, z0 + 3, "spruce_pressure_plate");
        }
        for (int x = x0 + 1; x <= x0 + 10; x++) {
            w.set(x, y + 1, z0 + 1, (x & 1) == 0 ? "barrel[facing=south]" : "barrel[facing=up]");
            w.set(x, y + 2, z0 + 1, (x & 1) == 0 ? "barrel[facing=south]" : "decorated_pot");
            if ((x & 3) == 0) w.set(x, y + 3, z0 + 1, "potted_fern");
        }
        a.fireplace(x0, y, door + 2, "west", y + 18);
        a.table(x0 + 4, y + 1, z1 - 2);
        a.chair(x0 + 3, y + 1, z1 - 2, "west");
        a.chair(x0 + 5, y + 1, z1 - 2, "east");
        a.table(x0 + 9, y + 1, z1 - 2);
        a.chair(x0 + 8, y + 1, z1 - 2, "west");
        a.chair(x0 + 10, y + 1, z1 - 2, "east");
        a.table(x0 + 13, y + 1, z1 - 3);
        a.chair(x0 + 13, y + 1, z1 - 2, "south");
        a.chair(x0 + 13, y + 1, z1 - 4, "north");
        a.rug(x0 + 2, door, x0 + 5, door + 3, y + 1, "red", "orange");
        w.set(x1 - 1, y + 1, z0 + 1, "crafting_table");
        w.set(x1 - 2, y + 1, z0 + 1, "smoker[facing=south,lit=true]");
        w.set(x1 - 3, y + 1, z0 + 1, "water_cauldron[level=3]");
        // stairs up along the south wall, and the rooms above
        a.flight(x1 - 6, y + 1, z1 - 1, "west", 5, "spruce_stairs", "spruce_planks");
        for (int x = x1 - 10; x <= x1 - 6; x++) w.set(x, y + 5, z1 - 1, "air");
        a.reserve(x1 - 11, z1 - 1, x1 - 5, z1 - 1);
        a.bed(x0 + 2, y + 6, z0 + 1, "west", "red");
        a.bed(x0 + 2, y + 6, z1 - 1, "west", "green");
        w.set(x0 + 1, y + 6, door + 1, "chest[facing=east]");
        w.set(x0 + 5, y + 6, z0 + 1, "barrel[facing=up]");
        a.rug(x0 + 3, door - 1, x0 + 6, door + 2, y + 6, "brown", null);
        a.storeroom(new Room(x0 + 9, z0, x1, z1, y + 5, 5));
        for (int x = x0 + 3; x <= x1 - 3; x += 5) w.hangLight(x, y + 4, door, "lantern");
        w.hangLight(x0 + 4, y + 9, door, "lantern");
        // the beer garden outside
        for (int[] p : new int[][] {{x1 + 6, door - 5}, {x1 + 6, door + 5}, {x1 + 10, door}}) {
            a.table(p[0], y + 1, p[1]);
            a.chair(p[0] - 1, y + 1, p[1], "west");
            a.chair(p[0] + 1, y + 1, p[1], "east");
        }
        a.lampPost(x1 + 8, y + 1, door - 7, 3);
        a.lampPost(x1 + 8, y + 1, door + 7, 3);
        NPCS.append(npc("innkeeper", "<gold>Innkeeper Bram", "talk", "butcher", "plains", x0 + 6.5, y + 1, z0 + 1.5, 0,
                "Welcome to the Broken Flagpole! Named after the last match I played.",
                "The Tank kit? Wears a diamond chestplate to dinner. Every time.",
                "Heard someone took a flag across Frostfall without placing a single block. Heard wrong, probably."));
        NPCS.append(npc("chronicler", "<aqua><bold>Chronicler", "stats", "librarian", "plains", x0 + 13.5, y + 1, z1 - 1.5, 180,
                "I write down every kill, capture and win. Let me show you yours.",
                "The leaderboards are in my book too. Top ten, every stat."));
        NPCS.append(npc("bard", "<light_purple>Bard", "talk", "cleric", "plains", x0 + 3.5, y + 1, door + 4.5, 90,
                "♪ Oh the wool it was red, and the tower stood tall... ♪",
                "I'd write a song about your last match, but I only know sad tunes.",
                "Sing with me! No? Rate a map then: <yellow>/rate 1-5</yellow>."));
        label(w.w, x1 + 4.5, y + 3.7, door + 0.5, "THE BROKEN FLAGPOLE", NamedTextColor.GOLD, true, 1.0f);
        label(w.w, x1 + 4.5, y + 3.3, door + 0.5, "Tavern  •  Stats  •  Leaderboards", NamedTextColor.WHITE, false, 0.7f);
        land.keeps.add(new Terrain.Keep(-86, 3, 17));
    }

    // ---------------------------------------------------------------- the war camps

    static void camp(W w, Arch a, Terrain land, int cx, int cz, int y, String team) {
        // a palisade of sharpened logs with a gate toward the commons (north)
        int r = 12;
        for (int i = 0; i < 360; i += 2) {
            double ang = Math.toRadians(i);
            int x = cx + (int) Math.round(Math.cos(ang) * r), z = cz + (int) Math.round(Math.sin(ang) * r);
            if (Math.abs(x - cx) <= 2 && z < cz) continue; // the gate
            int gy = w.ground(x, z);
            if (gy < y - 3) gy = y;
            for (int dy = 1; dy <= 3; dy++) w.set(x, gy + dy, z, "spruce_log[axis=y]");
            w.set(x, gy + 4, z, "spruce_fence");
        }
        for (int side : new int[] {-3, 3}) {
            for (int dy = 1; dy <= 5; dy++) w.set(cx + side, y + dy, cz - r, "stripped_spruce_log[axis=y]");
            w.set(cx + side, y + 6, cz - r, "lantern[hanging=false]");
            w.set(cx + side, y + 4, cz - r - 1, team + "_wall_banner[facing=north]");
        }
        for (int x = cx - 3; x <= cx + 3; x++) w.set(x, y + 5, cz - r, "spruce_log[axis=x]");
        a.reserve(cx - 3, cz - r - 3, cx + 3, cz - r + 5);
        // trodden ground inside
        for (int dx = -r + 1; dx <= r - 1; dx++)
            for (int dz = -r + 1; dz <= r - 1; dz++) {
                if (Math.hypot(dx, dz) > r - 0.8) continue;
                int x = cx + dx, z = cz + dz;
                for (int dy = 1; dy <= 6; dy++) if (!w.air(x, y + dy, z) && !w.type(x, y + dy, z).name().contains("LOG")) w.set(x, y + dy, z, "air");
                w.set(x, y, z, w.r.nextInt(4) == 0 ? "coarse_dirt" : w.r.nextInt(5) == 0 ? "gravel" : "dirt_path");
            }
        // two small tents set back from the gate, flaps facing the fire
        for (int side : new int[] {-1, 1}) tent(w, cx + side * 6, y, cz + 6, team);
        // the fire, log benches round it
        w.set(cx, y, cz + 1, "cobblestone");
        w.set(cx, y + 1, cz + 1, "campfire[lit=true]");
        for (int[] d : new int[][] {{-2, 0}, {2, 0}, {0, 2}}) w.set(cx + d[0], y + 1, cz + 1 + d[1], "stripped_oak_log[axis=" + (d[0] == 0 ? "x" : "z") + "]");
        // archery butts on the west side, training dummies on the east
        for (int dz = -4; dz <= 0; dz += 4) {
            w.set(cx - 9, y + 1, cz + dz, "hay_block");
            w.set(cx - 9, y + 2, cz + dz, "target");
        }
        for (int dz = -4; dz <= 0; dz += 4) {
            w.set(cx + 9, y + 1, cz + dz, "spruce_fence");
            w.set(cx + 9, y + 2, cz + dz, "hay_block");
            w.set(cx + 9, y + 3, cz + dz, "carved_pumpkin[facing=west]");
            w.set(cx + 9, y + 2, cz + dz - 1, "spruce_fence");
            w.set(cx + 9, y + 2, cz + dz + 1, "spruce_fence");
        }
        // weapon rack and stores by the tents
        w.set(cx - 2, y + 1, cz + 9, "grindstone[face=floor,facing=east]");
        w.set(cx + 2, y + 1, cz + 9, "anvil[facing=east]");
        w.set(cx, y + 1, cz + 10, "smithing_table");
        a.stores(cx - 10, y + 1, cz + 4, 3);
        // the camp's standard
        for (int dy = 1; dy <= 7; dy++) w.set(cx, y + dy, cz - 6, "spruce_fence");
        w.set(cx, y + 8, cz - 6, "stripped_spruce_log[axis=y]");
        w.set(cx, y + 8, cz - 7, team + "_wall_banner[facing=north]");
        w.set(cx, y + 8, cz - 5, team + "_wall_banner[facing=south]");
        w.set(cx, y + 9, cz - 6, "lantern[hanging=false]");
        String colour = team.equals("red") ? "<red>" : "<blue>";
        String name = team.equals("red") ? "Red" : "Blue";
        label(w.w, cx + 0.5, y + 4.3, cz - r - 1.5, name.toUpperCase(Locale.ROOT) + " WAR CAMP", team.equals("red") ? NamedTextColor.RED : NamedTextColor.BLUE, true, 1.1f);
        NPCS.append(npc(team + "-captain", colour + "<bold>Captain " + (team.equals("red") ? "Rhea" : "Corin"), "kits", "weaponsmith",
                team.equals("red") ? "savanna" : "taiga", cx + 0.5, y + 1, cz + 4.5, 180,
                "Pick your kit before the horn sounds. Grab the <yellow>book</yellow> any time.",
                "Defenders win games. Somebody has to stay home.",
                "Bridge fast, bridge wide, and never bridge alone."));
        NPCS.append(npc(team + "-quartermaster", colour + "Quartermaster", "kits", "armorer", "plains", cx - 4.5, y + 1, cz + 2.5, 135,
                "Golden apples for every kill, but eat them slowly: there's a cooldown.",
                "Out of blocks? Break the map: everything but spawns and objectives breaks."));
        NPCS.append(npc(team + "-scout", colour + "Scout", "join", "cartographer", "plains", cx + 4.5, y + 1, cz - 8.5, 180,
                "A match is on? Talk to me and I'll get you in.",
                "Long shots count double in my book. Not in the scoreboard, though."));
        NPCS.append(npc(team + "-recruit", colour + "Recruit", "talk", "none", "plains", cx + 7.5, y + 1, cz - 2.5, 90,
                "I've hit that dummy a hundred times. It's winning.",
                "They say the wool towers fall faster than they look.",
                "Did you know you can rate maps? <yellow>/rate 1-5</yellow> after a match."));
        land.keeps.add(new Terrain.Keep(cx, cz, r + 2));
    }

    /** Every villager in the hub, as hub-npcs.yml lines. */
    static final StringBuilder NPCS = new StringBuilder();

    /** A small A-frame tent, 5 wide and 5 deep, flap toward the north. */
    static void tent(W w, int cx, int y, int cz, String team) {
        for (int dz = -2; dz <= 2; dz++)
            for (int h = 0; h <= 2; h++) {
                int half = 2 - h;
                for (int s : new int[] {-half, half}) {
                    String side = s < 0 ? "east" : "west";
                    w.set(cx + s, y + 1 + h, cz + dz, h == 2 ? "white_wool" : ((dz + h) & 1) == 0 ? team + "_wool" : "white_wool");
                }
            }
        for (int dz = -2; dz <= 2; dz++) w.set(cx, y + 4, cz + dz, "stripped_spruce_log[axis=z]");
        for (int dz = -1; dz <= 2; dz++) w.set(cx, y + 1, cz + dz, team + "_carpet");
        w.set(cx - 1, y + 1, cz + 1, team + "_bed[facing=south,part=foot]");
        w.set(cx - 1, y + 1, cz + 2, team + "_bed[facing=south,part=head]");
        w.set(cx + 1, y + 1, cz + 2, "barrel[facing=up]");
        w.hangLight(cx, y + 3, cz, "lantern");
    }

    // ---------------------------------------------------------------- the little islands

    static void islets(W w, Theme t, Arch a, Terrain land) {
        // a ruined watchtower on the north-east islet
        int gx = 62, gz = -60, gy = w.ground(gx, gz);
        if (gy > 0) {
            for (int dx = -3; dx <= 3; dx++)
                for (int dz = -3; dz <= 3; dz++) {
                    double d = Math.hypot(dx, dz);
                    if (d > 3.4 || d < 2.4) continue;
                    int h = 3 + (int) Math.round((w.n(gx + dx, gz + dz, 2) + 1) * 3);
                    for (int dy = 1; dy <= h; dy++) w.set(gx + dx, gy + dy, gz + dz, w.r.nextInt(4) == 0 ? "mossy_stone_bricks" : "stone_bricks");
                }
            land.keeps.add(new Terrain.Keep(gx, gz, 5));
        }
        // a little shrine with a big cherry on the north-west islet
        int sx = -62, sz = -58, sy = w.ground(sx, sz);
        if (sy > 0) {
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) w.set(sx + dx, sy, sz + dz, "polished_andesite");
            for (int side : new int[] {-2, 2}) for (int dy = 1; dy <= 4; dy++) w.set(sx + side, sy + dy, sz + 2, "red_concrete");
            for (int dx = -3; dx <= 3; dx++) w.set(sx + dx, sy + 5, sz + 2, dx == -3 || dx == 3 ? "dark_oak_slab[type=bottom]" : "dark_oak_planks");
            new Flora(w, t, land).tree(Theme.Tree.CHERRY, sx, sy + 1, sz - 3);
            w.set(sx, sy + 1, sz, "lantern[hanging=false]");
            land.keeps.add(new Terrain.Keep(sx, sz, 5));
        }
        // a windmill on the south islet
        int mx = 2, mz = 96, my = w.ground(mx, mz);
        if (my > 0) {
            for (int dy = 1; dy <= 9; dy++) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                int shrink = dy > 6 ? 1 : 0;
                if (Math.abs(dx) > 2 - shrink || Math.abs(dz) > 2 - shrink) continue;
                boolean e = Math.abs(dx) == 2 - shrink || Math.abs(dz) == 2 - shrink;
                w.set(mx + dx, my + dy, mz + dz, e ? (dy % 4 == 0 ? "stripped_spruce_log[axis=y]" : "white_terracotta") : "air");
            }
            new Arch(w, t, land).hip(mx - 1, mz - 1, mx + 1, mz + 1, my + 10, 1, "spruce_stairs", "spruce_slab", "spruce_planks", "lightning_rod");
            for (int i = -5; i <= 5; i++) {
                w.set(mx + i, my + 7 + 0, mz - 3, i == 0 ? "spruce_log[axis=z]" : "spruce_fence");
                w.set(mx, my + 7 + i, mz - 3, i == 0 ? "spruce_log[axis=z]" : "spruce_fence");
            }
            for (int i = 2; i <= 5; i++) {
                w.set(mx + i, my + 6, mz - 3, "white_wool");
                w.set(mx - i, my + 8, mz - 3, "white_wool");
                w.set(mx - 1, my + 7 + i, mz - 3, "white_wool");
                w.set(mx + 1, my + 7 - i, mz - 3, "white_wool");
            }
            land.keeps.add(new Terrain.Keep(mx, mz, 5));
        }
    }

    static void waterfall(W w, Terrain land, int x, int z) {
        double ang = Math.atan2(z, x);
        int lx = x, lz = z;
        for (int r = 0; r < 60; r++) {
            int px = x + (int) Math.round(Math.cos(ang) * r), pz = z + (int) Math.round(Math.sin(ang) * r);
            if (land.column(px, pz) == null) break;
            lx = px;
            lz = pz;
        }
        int y = w.ground(lx, lz);
        if (y < 0) return;
        // a spring in a little mossy pool at the rim, spilling over the edge
        for (int[] d : new int[][] {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int px = lx - (int) Math.round(Math.cos(ang) * 2) + d[0], pz = lz - (int) Math.round(Math.sin(ang) * 2) + d[1];
            int gy = w.ground(px, pz);
            if (gy > 0 && (d[0] != 0 || d[1] != 0)) w.set(px, gy + 1, pz, "mossy_cobblestone");
        }
        w.set(lx, y, lz, "water");
        land.keeps.add(new Terrain.Keep(lx, lz, 4));
    }

    // ---------------------------------------------------------------- labels and NPC lines

    static void label(World world, double x, double y, double z, String text, NamedTextColor colour, boolean bold, float scale) {
        world.spawn(new Location(world, x, y, z), TextDisplay.class, d -> {
            d.text(Component.text(text, colour).decoration(TextDecoration.BOLD, bold));
            d.setBillboard(Display.Billboard.CENTER);
            d.setPersistent(true);
            d.setShadowed(true);
            d.setBackgroundColor(Color.fromARGB(0x50000000));
            d.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.AxisAngle4f(),
                    new org.joml.Vector3f(scale, scale, scale), new org.joml.AxisAngle4f()));
        });
    }

    static String npc(String id, String name, String role, String profession, String type, double x, int y, double z, int yaw, String... lines) {
        StringBuilder out = new StringBuilder(String.format(Locale.ROOT,
                "  - {id: %s, name: \"%s\", role: %s, profession: %s, type: %s, x: %.1f, y: %d, z: %.1f, yaw: %d, lines: [",
                id, name, role, profession, type, x, y, z, yaw));
        for (int i = 0; i < lines.length; i++) out.append(i == 0 ? "" : ", ").append('"').append(lines[i].replace("\"", "'")).append('"');
        return out.append("]}\n").toString();
    }
}
