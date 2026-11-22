package com.sasha.ctfbuilder.v2;

import com.sasha.ctfbuilder.Lobby;
import com.sasha.ctfbuilder.v2.Terrain.Island;
import com.sasha.ctfbuilder.v2.W.P;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.EulerAngle;

/**
 * The CTF hub: "Skyhold", a floating citadel. A big central island with the spawn plaza and the war
 * monument (four team beacons), ringed by satellite islands joined by bridges: the Armoury (all kits on
 * stands, right-click to preview), the Map Hall, the Hall of Fame, and the red and blue war camps.
 * Waterfalls pour off the edges into the void.
 */
public final class Hub {

    private Hub() {}

    static final int Y = 64;

    public static String build(World world) {
        for (int cx = -26; cx <= 26; cx++) for (int cz = -26; cz <= 26; cz++) world.getChunkAt(cx, cz).load(true);
        W w = new W(world, 424242, 1);
        Theme t = hubTheme();
        Terrain land = new Terrain(w, t);
        Build b = new Build(w, t, land);
        Flora flora = new Flora(w, t, land);

        land.add(new Island("core", 0, 0, 52, 46, 0.1, Y).hill(2).depth(44).trees(0.55));
        land.add(new Island("armoury", 86, -8, 24, 20, 0.3, Y + 3).hill(1).depth(26).trees(0.5));
        land.add(new Island("maps", -4, -84, 26, 18, 0.0, Y + 5).hill(1).depth(26).trees(0.5));
        land.add(new Island("fame", -86, 4, 22, 20, -0.2, Y + 2).hill(1).depth(24).trees(0.5));
        land.add(new Island("redcamp", -52, 70, 20, 16, 0.5, Y - 2).hill(2).depth(20));
        land.add(new Island("bluecamp", 54, 70, 20, 16, -0.5, Y - 2).hill(2).depth(20));
        land.add(new Island("isle1", 58, -58, 9, 7, 0, Y + 9).depth(12));
        land.add(new Island("isle2", -60, -56, 8, 8, 0, Y + 7).depth(12));
        land.add(new Island("isle3", 0, 92, 10, 8, 0, Y - 6).depth(14));
        land.flats.add(new Terrain.Flat(0, 0, 20, Y));
        land.flats.add(new Terrain.Flat(86, -8, 15, Y + 3));
        land.flats.add(new Terrain.Flat(-4, -84, 14, Y + 5));
        land.flats.add(new Terrain.Flat(-86, 4, 13, Y + 2));
        land.flats.add(new Terrain.Flat(-52, 70, 11, Y - 2));
        land.flats.add(new Terrain.Flat(54, 70, 11, Y - 2));
        land.build();

        plaza(w, t, b, land);
        armoury(w, t, b, land);
        mapHall(w, t, b, land);
        hallOfFame(w, t, b, land);
        camp(w, t, b, land, -52, 70, Y - 2, "red");
        camp(w, t, b, land, 54, 70, Y - 2, "blue");
        // bridges from the core out to each island (all walkable here)
        b.ropeBridge(42, Y, -4, 64, Y + 3, -6);
        b.ropeBridge(-2, Y, -40, -3, Y + 5, -68);
        b.ropeBridge(-42, Y, 2, -66, Y + 2, 4);
        b.ropeBridge(-28, Y, 34, -40, Y - 2, 58);
        b.ropeBridge(30, Y, 34, 42, Y - 2, 58);
        // paths across the core
        land.path(2, 0, 21, 0, 40);
        land.path(2, 21, 0, 42, -4);
        land.path(2, -21, 0, -42, 2);
        land.path(2, 0, -21, -2, -40);
        land.path(1, 14, 16, 30, 34);
        land.path(1, -14, 16, -28, 34);
        // waterfalls off the edges
        for (int[] f : new int[][] {{-30, -40}, {40, 30}, {-46, -12}, {20, -44}, {-20, 42}}) waterfall(w, land, f[0], f[1]);
        b.pond(-26, -18, 5);
        b.pond(28, 20, 4);
        flora.decorate();
        return "0.5 " + (Y + 1) + " 12.5 180 0";
    }

    static Theme hubTheme() {
        Theme t = Theme.get("kingdoms");
        t.trees = new Theme.Tree[] {Theme.Tree.OAK, Theme.Tree.CHERRY, Theme.Tree.BIRCH, Theme.Tree.SPRUCE};
        t.treeWeights = new int[] {5, 3, 2, 2};
        t.treeDensity = 0.012;
        t.wall = P.of("stone_bricks", 6, "polished_andesite", 2, "cracked_stone_bricks", 1);
        t.wall2 = P.of("white_terracotta", 3, "calcite", 1);
        t.trim = P.of("polished_andesite");
        t.log = "dark_oak_log"; t.planks = "dark_oak_planks"; t.wood = "dark_oak"; t.fence = "dark_oak_fence"; t.trapdoor = "dark_oak_trapdoor";
        t.stairsRoof = "deepslate_tile_stairs"; t.slabRoof = "deepslate_tile_slab"; t.fullRoof = "deepslate_tiles";
        t.floor = P.of("polished_andesite", 2, "stone_bricks", 2, "dark_oak_planks", 1);
        return t;
    }

    // ---------------------------------------------------------------- the plaza and the war monument

    static void plaza(W w, Theme t, Build b, Terrain land) {
        for (int x = -19; x <= 19; x++)
            for (int z = -19; z <= 19; z++) {
                double d = Math.hypot(x, z);
                if (d > 18.5) continue;
                String block;
                if (d > 17.5) block = "polished_andesite";
                else if (Math.abs(d - 12) < 0.6) block = "chiseled_stone_bricks";
                else if (Math.abs(x) <= 1 || Math.abs(z) <= 1) block = "smooth_stone";
                else {
                    int seg = (int) Math.floor((Math.atan2(z, x) + Math.PI) / (Math.PI / 8));
                    block = (seg & 1) == 0 ? "stone_bricks" : "polished_andesite";
                }
                w.set(x, Y, z, block);
                for (int dy = 1; dy <= 8; dy++) if (!w.air(x, Y + dy, z)) w.set(x, Y + dy, z, "air");
            }
        // the war monument: a stepped plinth, a pillar crowned by crossed swords, four team beacons around
        for (int s = 0; s < 3; s++)
            for (int x = -5 + s; x <= 5 - s; x++)
                for (int z = -5 + s; z <= 5 - s; z++) w.set(x, Y + 1 + s, z, s == 2 ? "chiseled_stone_bricks" : "polished_andesite");
        for (int dy = 4; dy <= 14; dy++) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            boolean corner = Math.abs(x) == 1 && Math.abs(z) == 1;
            w.set(x, Y + dy, z, corner ? "polished_deepslate" : dy % 3 == 0 ? "chiseled_deepslate" : "deepslate_bricks");
        }
        w.set(0, Y + 15, 0, "gold_block");
        w.set(0, Y + 16, 0, "lightning_rod");
        for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            w.set(d[0] * 2, Y + 15, d[1] * 2, "polished_deepslate_wall");
            w.set(d[0] * 2, Y + 16, d[1] * 2, "lantern[hanging=false]");
        }
        String[] teams = {"red", "blue", "lime", "yellow"};
        int[][] spots = {{-9, -9}, {9, 9}, {9, -9}, {-9, 9}};
        for (int i = 0; i < 4; i++) {
            int x = spots[i][0], z = spots[i][1];
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) w.set(x + dx, Y - 1, z + dz, "iron_block");
            w.set(x, Y, z, "beacon");
            w.set(x, Y + 1, z, teams[i] + "_stained_glass");
            for (int[] d : new int[][] {{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) {
                w.set(x + d[0], Y + 1, z + d[1], "andesite_wall");
                w.set(x + d[0], Y + 2, z + d[1], teams[i] + "_banner[rotation=" + (d[0] > 0 ? 4 : d[0] < 0 ? 12 : d[1] > 0 ? 0 : 8) + "]");
            }
        }
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4 + Math.PI / 8;
            b.lamp((int) Math.round(Math.cos(a) * 16), Y + 1, (int) Math.round(Math.sin(a) * 16));
        }
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2 + Math.PI / 4;
            int x = (int) Math.round(Math.cos(a) * 14.5), z = (int) Math.round(Math.sin(a) * 14.5);
            w.set(x, Y + 1, z, "dark_oak_stairs[facing=" + (Math.abs(x) > Math.abs(z) ? (x > 0 ? "west" : "east") : (z > 0 ? "north" : "south")) + "]");
        }
        land.keeps.add(new Terrain.Keep(0, 0, 22));
        label(w.w, 0.5, Y + 6.6, 6.5, "FRACTAL CTF", NamedTextColor.GOLD, true);
        label(w.w, 0.5, Y + 6.25, 6.5, "Capture the Flag - King of the Hill - Destroy the Wool", NamedTextColor.GRAY, false);
    }

    // ---------------------------------------------------------------- islands

    static void armoury(W w, Theme t, Build b, Terrain land) {
        int y = Y + 3, x0 = 74, x1 = 98, z0 = -18, z1 = 2;
        b.hall(x0, x1, z0, z1, y, 9, 9);
        for (int dy = 1; dy <= 4; dy++) for (int z = -10; z <= -6; z++) w.set(x0, y + dy, z, "air");
        b.roof(x0, x1, z0, z1, y + 10);
        for (int x = x0 + 1; x < x1; x++) w.set(x, y + 1, -8, "red_carpet");
        int i = 0;
        for (int x = x0 + 3; x <= x1 - 2 && i < Lobby.KITS.length; x += 2) {
            stand(w.w, x + 0.5, y + 1, z0 + 2.5, 0, i++);
            if (i < Lobby.KITS.length) stand(w.w, x + 0.5, y + 1, z1 - 1.5, 180, i++);
        }
        for (int x = x0 + 4; x <= x1 - 4; x += 5) w.set(x, y + 8, -8, "lantern[hanging=true]");
        label(w.w, x0 - 0.5, y + 4.4, -7.5, "ARMOURY", NamedTextColor.GOLD, true);
        label(w.w, x0 - 0.5, y + 4.05, -7.5, "Right-click a kit to preview it", NamedTextColor.GRAY, false);
        land.keeps.add(new Terrain.Keep(86, -8, 16));
    }

    static void mapHall(W w, Theme t, Build b, Terrain land) {
        int y = Y + 5, x0 = -14, x1 = 6, z0 = -94, z1 = -76;
        Theme pagoda = Theme.get("sakura");
        Build pb = new Build(w, pagoda, land);
        pb.hall(x0, x1, z0, z1, y, 9, 9);
        for (int dy = 1; dy <= 4; dy++) for (int x = -6; x <= -2; x++) w.set(x, y + dy, z1, "air");
        pb.roof(x0, x1, z0, z1, y + 10);
        // ten map plinths, each topped with that map's signature block
        String[] tops = {"snow_block", "red_sand", "grass_block", "cherry_leaves[persistent=true]", "magma_block", "moss_block", "sand",
                "mycelium", "pale_moss_block", "end_stone"};
        String[] names = {"Frostfall", "Dustbowl", "Four Kingdoms", "Sakura Isles", "Emberforge", "Verdant Ruins", "Tidecrest", "Mycelia", "Pale Grove", "Skyreach"};
        for (int k = 0; k < 10; k++) {
            int px = x0 + 3 + (k % 5) * 4, pz = k < 5 ? z0 + 3 : z1 - 3;
            w.set(px, y + 1, pz, "polished_andesite");
            w.set(px, y + 2, pz, tops[k]);
            label(w.w, px + 0.5, y + 3.3, pz + 0.5, names[k], NamedTextColor.WHITE, false);
        }
        label(w.w, -3.5, y + 6.4, z1 + 1.5, "MAP HALL", NamedTextColor.GOLD, true);
        label(w.w, -3.5, y + 6.05, z1 + 1.5, "Vote with the paper - rate maps with /rate", NamedTextColor.GRAY, false);
        land.keeps.add(new Terrain.Keep(-4, -84, 16));
    }

    static void hallOfFame(W w, Theme t, Build b, Terrain land) {
        int y = Y + 2;
        // an open colonnade around a three-step podium
        for (int x = -97; x <= -75; x++)
            for (int z = -8; z <= 16; z++) {
                double d = Math.hypot(x + 86, z - 4);
                if (d > 11.5) continue;
                w.set(x, y, z, d > 10.5 ? "chiseled_stone_bricks" : (((x + z) & 1) == 0 ? "smooth_quartz" : "polished_diorite"));
                for (int dy = 1; dy <= 9; dy++) if (!w.air(x, y + dy, z)) w.set(x, y + dy, z, "air");
            }
        for (int k = 0; k < 10; k++) {
            double a = k * Math.PI / 5;
            int px = -86 + (int) Math.round(Math.cos(a) * 10), pz = 4 + (int) Math.round(Math.sin(a) * 10);
            for (int dy = 1; dy <= 6; dy++) w.set(px, y + dy, pz, dy == 1 || dy == 6 ? "chiseled_quartz_block" : "quartz_pillar[axis=y]");
            w.set(px, y + 7, pz, "lantern[hanging=false]");
        }
        int[][] steps = {{-1, 2, 0}, {0, 3, 1}, {1, 1, 2}};
        String[] blocks = {"iron_block", "gold_block", "copper_block"};
        String[][] armour = {{"iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots"}, {"golden_helmet", "golden_chestplate", "golden_leggings", "golden_boots"},
                {"chainmail_helmet", "chainmail_chestplate", "chainmail_leggings", "chainmail_boots"}};
        String[] place = {"#2", "#1", "#3"};
        for (int[] s : steps) {
            int z = 4 + s[0] * 3, h = s[1];
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = 1; dy <= h; dy++) w.set(-86 + dx, y + dy, z + dz, blocks[s[2]]);
            int fi = s[2];
            w.w.spawn(new Location(w.w, -85.5, y + h + 1, z + 0.5, 90, 0), ArmorStand.class, st -> {
                st.setArms(true);
                st.setBasePlate(false);
                st.setGravity(false);
                st.setInvulnerable(true);
                st.setPersistent(true);
                st.customName(Component.text(place[fi], NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true));
                st.setCustomNameVisible(true);
                var eq = st.getEquipment();
                eq.setHelmet(ItemStack.of(Material.matchMaterial(armour[fi][0])));
                eq.setChestplate(ItemStack.of(Material.matchMaterial(armour[fi][1])));
                eq.setLeggings(ItemStack.of(Material.matchMaterial(armour[fi][2])));
                eq.setBoots(ItemStack.of(Material.matchMaterial(armour[fi][3])));
                eq.setItemInMainHand(ItemStack.of(Material.RED_BANNER));
            }).setDisabledSlots(EquipmentSlot.values());
        }
        label(w.w, -73.5, y + 3.6, 4.5, "HALL OF FAME", NamedTextColor.GOLD, true);
        label(w.w, -73.5, y + 3.25, 4.5, "/ctfstats - kills, captures and wins", NamedTextColor.GRAY, false);
        land.keeps.add(new Terrain.Keep(-86, 4, 14));
    }

    static void camp(W w, Theme t, Build b, Terrain land, int cx, int cz, int y, String team) {
        int[][] tents = {{-7, -4}, {0, -7}, {7, -3}};
        for (int[] p : tents) {
            int x = cx + p[0], z = cz + p[1];
            for (int i = -3; i <= 3; i++)
                for (int h = 0; h <= 3; h++) {
                    int wdt = 3 - h;
                    for (int s : new int[] {-wdt, wdt}) w.set(x + i, y + 1 + h, z + s, (i + h) % 3 == 0 ? "white_wool" : team + "_wool");
                }
            for (int i = -3; i <= 3; i++) w.set(x + i, y + 4, z, "stripped_spruce_log[axis=x]");
            for (int h = 1; h <= 2; h++) for (int s = -1; s <= 1; s++) w.set(x + 3, y + h, z + s, "air");
            w.set(x - 1, y + 1, z, team + "_carpet");
            w.set(x + 1, y + 1, z, team + "_carpet");
        }
        w.set(cx, y, cz + 4, "cobblestone");
        w.set(cx, y + 1, cz + 4, "campfire[lit=true]");
        for (int[] d : new int[][] {{-2, 0}, {2, 0}, {0, 2}, {0, -2}}) w.set(cx + d[0], y + 1, cz + 4 + d[1], "stripped_oak_log[axis=" + (d[0] == 0 ? "x" : "z") + "]");
        for (int dy = 1; dy <= 7; dy++) w.set(cx + 9, y + dy, cz + 6, "spruce_fence");
        w.set(cx + 9, y + 6, cz + 7, team + "_wall_banner[facing=south]");
        w.set(cx + 9, y + 6, cz + 5, team + "_wall_banner[facing=north]");
        w.set(cx - 8, y + 1, cz + 6, "barrel[facing=up]");
        w.set(cx - 7, y + 1, cz + 6, "hay_block");
        w.set(cx - 7, y + 2, cz + 6, "barrel[facing=up]");
        label(w.w, cx + 0.5, y + 3.4, cz + 8.5, team.equals("red") ? "RED WAR CAMP" : "BLUE WAR CAMP",
                team.equals("red") ? NamedTextColor.RED : NamedTextColor.BLUE, true);
        land.keeps.add(new Terrain.Keep(cx, cz, 13));
    }

    static void waterfall(W w, Terrain land, int x, int z) {
        // walk outward from (x, z) to the last land column, then put a spring at the rim
        double a = Math.atan2(z, x);
        int lx = x, lz = z;
        for (int r = 0; r < 40; r++) {
            int px = x + (int) Math.round(Math.cos(a) * r), pz = z + (int) Math.round(Math.sin(a) * r);
            if (land.column(px, pz) == null) break;
            lx = px;
            lz = pz;
        }
        int y = w.ground(lx, lz);
        if (y < 0) return;
        w.set(lx, y, lz, "water");
        int bx = lx - (int) Math.round(Math.cos(a) * 2), bz = lz - (int) Math.round(Math.sin(a) * 2);
        for (int[] d : new int[][] {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int gy = w.ground(bx + d[0], bz + d[1]);
            if (gy > 0) w.set(bx + d[0], gy + 1, bz + d[1], "mossy_cobblestone");
        }
        land.keeps.add(new Terrain.Keep(lx, lz, 3));
    }

    // ---------------------------------------------------------------- entities

    static void label(World world, double x, double y, double z, String text, NamedTextColor colour, boolean bold) {
        world.spawn(new Location(world, x, y, z), TextDisplay.class, d -> {
            d.text(Component.text(text, colour).decoration(TextDecoration.BOLD, bold));
            d.setBillboard(Display.Billboard.CENTER);
            d.setPersistent(true);
            d.setShadowed(true);
            d.setBackgroundColor(Color.fromARGB(0x40000000));
        });
    }

    static void stand(World world, double x, int y, double z, float yaw, int kit) {
        Object[] k = Lobby.KITS[kit];
        ArmorStand stand = world.spawn(new Location(world, x, y, z, yaw, 0), ArmorStand.class, s -> {
            s.setArms(true);
            s.setBasePlate(false);
            s.setGravity(false);
            s.setInvulnerable(true);
            s.setPersistent(true);
            s.customName(Component.text((String) k[0], NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true));
            s.setCustomNameVisible(true);
            s.addScoreboardTag("ctfkit:" + ((String) k[0]).toLowerCase(java.util.Locale.ROOT).replace(' ', '_'));
            s.setRightArmPose(new EulerAngle(Math.toRadians(-20), 0, Math.toRadians(-10)));
            var eq = s.getEquipment();
            eq.setHelmet(Lobby.piece((String) k[1], Material.LEATHER_HELMET));
            eq.setChestplate(Lobby.piece((String) k[2], Material.LEATHER_CHESTPLATE));
            eq.setLeggings(Lobby.piece((String) k[3], Material.LEATHER_LEGGINGS));
            eq.setBoots(Lobby.piece((String) k[4], Material.LEATHER_BOOTS));
            eq.setItemInMainHand(ItemStack.of(Material.matchMaterial(Lobby.HAND[kit])));
            if (k[0].equals("Shielder")) eq.setItemInOffHand(ItemStack.of(Material.SHIELD));
        });
        stand.setDisabledSlots(EquipmentSlot.values());
    }
}
