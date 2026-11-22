package com.sasha.ctfbuilder;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.util.EulerAngle;

/** The CTF hub: a walled war camp on a floating island. Returns the hub spawn line for config.yml. */
public final class Lobby {

    private Lobby() {}

    static final int BASE = 64;

    static String build(B b) {
        B.Pal grass = B.Pal.of("grass_block", 14, "moss_block", 1);
        B.Pal dirt = B.Pal.of("dirt", 5, "coarse_dirt", 1);
        B.Pal rock = B.Pal.of("stone", 6, "andesite", 2, "cobblestone", 1, "tuff", 1);
        // island: flat inside the walls, gentle hills outside
        b.island(-82, 82, -82, 82, (x, z) -> b.roundRect(x, z, 0, 0, 76, 76, 40, 7), (x, z) -> {
            double inner = Math.max(Math.abs(x), Math.abs(z));
            int h = inner <= 50 ? BASE : BASE + (int) Math.round(b.fbm(x, z, 22, 2) * 2 + (inner - 50) * 0.05);
            return new B.Column(h, grass, dirt, rock);
        });

        walls(b);
        plaza(b);
        voteHall(b);
        armoury(b);
        hallOfFame(b);
        tavern(b);
        camp(b, -27, 31, "red");
        camp(b, 27, 31, "blue");
        trainingYard(b);
        parkour(b);
        paths(b);
        gardens(b);
        return "0.5 " + (BASE + 1) + " 6.5 180 0";
    }

    // ---------------------------------------------------------------- walls and towers

    static void walls(B b) {
        B.Pal stone = B.Pal.of("stone_bricks", 8, "cracked_stone_bricks", 2, "mossy_stone_bricks", 2, "andesite", 1);
        B.Pal cap = B.Pal.of("smooth_stone");
        int R = 48;
        b.wall(-R, -R, R, -R, BASE + 1, 8, 2, stone, cap, true);
        b.wall(-R, R - 1, R, R - 1, BASE + 1, 8, 2, stone, cap, true);
        b.wall(-R, -R, -R, R, BASE + 1, 8, 2, stone, cap, true);
        b.wall(R - 1, -R, R - 1, R, BASE + 1, 8, 2, stone, cap, true);
        String[] roofs = {"red_terracotta", "blue_terracotta", "blue_terracotta", "red_terracotta"};
        int i = 0;
        for (int sx : new int[] {-1, 1})
            for (int sz : new int[] {-1, 1}) {
                b.tower(sx * R, sz * R, BASE + 1, 5, 16, stone, B.Pal.of("spruce_planks"), B.roof(sx < 0 ? "red" : "blue"), false);
                i++;
            }
        // south gatehouse with an open arch onto the outer gardens
        for (int x = -3; x <= 3; x++) for (int dy = 1; dy <= 5; dy++) {
            b.set(x, BASE + dy, R - 1, "air");
            b.set(x, BASE + dy, R, "air");
        }
        for (int x : new int[] {-5, 5}) {
            b.tower(x, R, BASE + 1, 2, 12, stone, B.Pal.of("spruce_planks"), null, true);
        }
        for (int x = -4; x <= 4; x++) {
            b.set(x, BASE + 6, R - 1, "chiseled_stone_bricks");
            b.set(x, BASE + 6, R, "chiseled_stone_bricks");
        }
        b.wallBanner(-2, BASE + 5, R + 1, "red", "south");
        b.wallBanner(2, BASE + 5, R + 1, "blue", "south");
        // banners all around the inside of the walls, alternating red and blue
        for (int t = -40; t <= 40; t += 8) {
            String colour = (t / 8) % 2 == 0 ? "red" : "blue";
            b.wallBanner(t, BASE + 6, -R + 2, colour, "south");
            b.wallBanner(-R + 2, BASE + 6, t, colour, "east");
            b.wallBanner(R - 2, BASE + 6, t, colour, "west");
            if (Math.abs(t) > 6) b.wallBanner(t, BASE + 6, R - 2, colour, "north");
        }
    }

    // ---------------------------------------------------------------- spawn plaza

    static void plaza(B b) {
        for (int x = -14; x <= 14; x++)
            for (int z = -14; z <= 14; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > 13.5) continue;
                String block;
                if (d > 12.5) block = "chiseled_stone_bricks";
                else if (d > 11.5) block = "polished_andesite";
                else if (Math.abs(d - 8) < 0.6) block = x < 0 ? "red_terracotta" : "blue_concrete";
                else if (Math.abs(x) <= 0 || Math.abs(z) <= 0) block = "smooth_stone";
                else block = ((int) (Math.atan2(z, x) * 8 / Math.PI) & 1) == 0 ? "stone_bricks" : "polished_andesite";
                b.set(x, BASE, z, block);
            }
        // monument: stepped base, dark pillar, a glowing crown, crossed team banner poles
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) b.set(x, BASE + 1, z, Math.abs(x) == 2 || Math.abs(z) == 2 ? "stone_brick_slab[type=bottom]" : "chiseled_stone_bricks");
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) b.set(x, BASE + 2, z, "polished_blackstone_bricks");
        for (int dy = 3; dy <= 9; dy++) b.set(0, BASE + dy, 0, dy == 9 ? "sea_lantern" : "polished_deepslate");
        for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) b.set(d[0], BASE + 9, d[1], "polished_blackstone_wall");
        b.set(0, BASE + 10, 0, "lightning_rod");
        for (int side : new int[] {-1, 1}) {
            int px = side * 4;
            for (int dy = 1; dy <= 7; dy++) b.set(px, BASE + dy, -4, "dark_oak_fence");
            b.wallBanner(px, BASE + 6, -3, side < 0 ? "red" : "blue", "south");
            b.wallBanner(px, BASE + 6, -5, side < 0 ? "red" : "blue", "north");
        }
        // benches and lamps around the ring
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4 + Math.PI / 8;
            int x = (int) Math.round(Math.cos(a) * 11), z = (int) Math.round(Math.sin(a) * 11);
            b.lampPost(x, BASE + 1, z, "dark_oak_fence");
        }
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2 + Math.PI / 4;
            int x = (int) Math.round(Math.cos(a) * 9.5), z = (int) Math.round(Math.sin(a) * 9.5);
            b.set(x, BASE + 1, z, "spruce_stairs");
        }
    }

    // ---------------------------------------------------------------- north: the map vote hall

    static void voteHall(B b) {
        int z1 = -40, z2 = -20, x1 = -18, x2 = 18;
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                b.set(x, BASE, z, ((x + z) & 1) == 0 ? "polished_andesite" : "stone_bricks");
                b.set(x, BASE + 1, z, (z == z1 || x == x1 || x == x2) && (x + z) % 7 != 0 ? "stone_brick_wall" : "air");
            }
        // pillars and a pitched roof (dark oak)
        for (int x = x1; x <= x2; x += 6)
            for (int z : new int[] {z1, z2}) for (int dy = 1; dy <= 7; dy++) b.set(x, BASE + dy, z, dy == 1 ? "stone_bricks" : "stripped_dark_oak_log");
        for (int step = 0; step <= 11; step++) {
            int y = BASE + 8 + step / 2;
            for (int x = x1 - 1; x <= x2 + 1; x++) {
                b.set(x, y, z1 - 1 + step, step % 2 == 0 ? "dark_oak_slab[type=bottom]" : "dark_oak_slab[type=top]");
                b.set(x, y, z2 + 1 - step, step % 2 == 0 ? "dark_oak_slab[type=bottom]" : "dark_oak_slab[type=top]");
            }
        }
        for (int x = x1; x <= x2; x += 3) b.set(x, BASE + 7, (z1 + z2) / 2, "lantern[hanging=true]");
        // three map podiums, each dressed for a map: snow, desert and meadow
        String[][] themes = {{"snow_block", "white", "spruce_leaves[persistent=true]"}, {"smooth_sandstone", "orange", "cactus"}, {"moss_block", "lime", "oak_leaves[persistent=true]"}};
        for (int i = 0; i < 3; i++) {
            int px = -10 + i * 10, pz = -31;
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) b.set(px + dx, BASE + 1, pz + dz, themes[i][0]);
            b.set(px, BASE + 2, pz, "lectern[facing=south]");
            b.banner(px - 2, BASE + 2, pz - 2, themes[i][1], 0);
            b.banner(px + 2, BASE + 2, pz - 2, themes[i][1], 0);
            b.set(px - 2, BASE + 2, pz + 2, themes[i][2]);
            b.set(px + 2, BASE + 2, pz + 2, themes[i][2]);
            for (int dy = 2; dy <= 3; dy++) b.set(px, BASE + 5, pz, "air");
            b.set(px, BASE + 6, pz, "lantern[hanging=true]");
        }
        floating(b, 0.5, BASE + 3.3, -24.5, "VOTE FOR THE NEXT MAP", NamedTextColor.GOLD);
        floating(b, 0.5, BASE + 3.0, -24.5, "right-click the paper in your hotbar", NamedTextColor.GRAY);
    }

    // ---------------------------------------------------------------- east: the armoury with every kit on display

    public static final Object[][] KITS = {
            {"Barbarian", "l", "l", "l", "l"}, {"Big Spender", "golden_helmet", "golden_chestplate", "golden_leggings", "golden_boots"},
            {"Builder", "l", "l", null, "l"}, {"Choruser", "l", "l", "chainmail_leggings", "l"}, {"Demoman", null, "iron_chestplate", "l", "l"},
            {"Engineer", "golden_helmet", "l", null, null}, {"Fire Archer", null, "l", "l", "l"}, {"Infiltrator", null, "l", "l", null},
            {"Medic", "l", "golden_chestplate", null, "l"}, {"Necromancer", "wither_skeleton_skull", "l", null, "iron_boots"},
            {"Ninja", "l", null, null, "l"}, {"Phoenix", "chainmail_helmet", "l", null, null}, {"Redstone Mechanic", "l", "iron_chestplate", null, null},
            {"Seeking Archer", "l", "l", null, "golden_boots"}, {"Sheep Farmer", null, "l", "l", "l"}, {"Shielder", "l", "l", "iron_leggings", "l"},
            {"Spy", "l", "l", "l", "l"}, {"Tank", "l", "diamond_chestplate", "l", "iron_boots"}, {"Thor", "diamond_helmet", "l", null, null}};
    public static final String[] HAND = {"stone_sword", "golden_sword", "iron_pickaxe", "chorus_fruit", "tnt", "purpur_block", "bow", "sugar",
            "glistering_melon_slice", "bone", "nether_star", "blaze_powder", "redstone_torch", "spectral_arrow", "sheep_spawn_egg", "shield",
            "gray_dye", "iron_ingot", "lightning_rod"};

    static void armoury(B b) {
        int x1 = 20, x2 = 44, z1 = -14, z2 = 14;
        building(b, x1, z1, x2, z2, 8, B.Pal.of("stone_bricks", 4, "cobblestone", 1), B.Pal.of("spruce_planks", 3, "stripped_spruce_log", 1),
                "spruce", "west", B.roof("red"));
        // display rows: two rows of stands facing the aisle
        int i = 0;
        for (int z = -11; z <= 11 && i < KITS.length; z += 2) {
            stand(b, 25.5, BASE + 1, z + 0.5, -90, i++);
            if (i < KITS.length) stand(b, 39.5, BASE + 1, z + 0.5, 90, i++);
        }
        for (int z = -12; z <= 12; z += 4) {
            b.set(x2 - 1, BASE + 1, z, "anvil[facing=north]");
            b.set(x2 - 1, BASE + 2, z + 1, "lantern");
            b.set(x2 - 1, BASE + 1, z + 1, "smithing_table");
        }
        for (int z = -12; z <= 12; z++) b.set(32, BASE, z, "red_carpet".equals("") ? "air" : "stripped_spruce_wood");
        for (int z = -12; z <= 12; z++) b.set(32, BASE + 1, z, "red_carpet");
        floating(b, 32.5, BASE + 3.3, 0.5, "ARMOURY", NamedTextColor.GOLD);
        floating(b, 32.5, BASE + 3.0, 0.5, "right-click a kit to preview it", NamedTextColor.GRAY);
    }

    static void stand(B b, double x, int y, double z, float yaw, int kit) {
        Object[] k = KITS[kit];
        b.set((int) Math.floor(x), y - 1, (int) Math.floor(z), "polished_andesite");
        ArmorStand stand = b.w.spawn(new Location(b.w, x, y, z, yaw, 0), ArmorStand.class, s -> {
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
            eq.setHelmet(piece((String) k[1], Material.LEATHER_HELMET));
            eq.setChestplate(piece((String) k[2], Material.LEATHER_CHESTPLATE));
            eq.setLeggings(piece((String) k[3], Material.LEATHER_LEGGINGS));
            eq.setBoots(piece((String) k[4], Material.LEATHER_BOOTS));
            eq.setItemInMainHand(ItemStack.of(Material.matchMaterial(HAND[kit])));
            if (k[0].equals("Shielder")) eq.setItemInOffHand(ItemStack.of(Material.SHIELD));
            for (var slot : org.bukkit.inventory.EquipmentSlot.values()) {
                try {
                    s.addEquipmentLock(slot, ArmorStand.LockType.REMOVING_OR_CHANGING);
                } catch (IllegalArgumentException ignored) {
                    // not every slot exists on an armour stand
                }
            }
        });
        stand.setDisabledSlots(org.bukkit.inventory.EquipmentSlot.values());
    }

    public static ItemStack piece(String spec, Material leather) {
        if (spec == null) return null;
        if (spec.equals("l")) {
            ItemStack item = ItemStack.of(leather);
            item.editMeta(LeatherArmorMeta.class, meta -> meta.setColor(Color.fromRGB(0xB02E26)));
            return item;
        }
        return ItemStack.of(Material.matchMaterial(spec));
    }

    // ---------------------------------------------------------------- west: hall of fame

    static void hallOfFame(B b) {
        int x1 = -44, x2 = -20, z1 = -14, z2 = 14;
        building(b, x1, z1, x2, z2, 9, B.Pal.of("calcite", 3, "polished_diorite", 2, "smooth_quartz", 1), B.Pal.of("quartz_bricks", 3, "smooth_quartz", 1),
                "oak", "east", B.roof("blue"));
        // podium: 2nd, 1st, 3rd
        int[][] steps = {{-1, 2}, {0, 3}, {1, 1}};
        String[] blocks = {"iron_block", "gold_block", "copper_block"};
        String[][] armour = {{"iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots"}, {"golden_helmet", "golden_chestplate", "golden_leggings", "golden_boots"},
                {"chainmail_helmet", "chainmail_chestplate", "chainmail_leggings", "chainmail_boots"}};
        String[] places = {"#2", "#1", "#3"};
        for (int i = 0; i < 3; i++) {
            int z = steps[i][0] * 4, h = steps[i][1];
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) for (int dy = 1; dy <= h; dy++) b.set(-38 + dx, BASE + dy, z + dz, blocks[i]);
            int fi = i;
            b.w.spawn(new Location(b.w, -37.5, BASE + h + 1, z + 0.5, -90, 0), ArmorStand.class, s -> {
                s.setArms(true);
                s.setBasePlate(false);
                s.setGravity(false);
                s.setInvulnerable(true);
                s.setPersistent(true);
                s.customName(Component.text(places[fi], fi == 1 ? NamedTextColor.GOLD : fi == 0 ? NamedTextColor.WHITE : NamedTextColor.GOLD));
                s.setCustomNameVisible(true);
                var eq = s.getEquipment();
                eq.setHelmet(ItemStack.of(Material.matchMaterial(armour[fi][0])));
                eq.setChestplate(ItemStack.of(Material.matchMaterial(armour[fi][1])));
                eq.setLeggings(ItemStack.of(Material.matchMaterial(armour[fi][2])));
                eq.setBoots(ItemStack.of(Material.matchMaterial(armour[fi][3])));
                eq.setItemInMainHand(ItemStack.of(Material.RED_BANNER));
            }).setDisabledSlots(org.bukkit.inventory.EquipmentSlot.values());
        }
        for (int z = -12; z <= 12; z += 3) {
            b.wallBanner(-43, BASE + 6, z, z % 2 == 0 ? "red" : "blue", "east");
        }
        for (int z = -10; z <= 10; z += 5) b.set(-30, BASE + 7, z, "lantern[hanging=true]");
        floating(b, -31.5, BASE + 3.3, 0.5, "HALL OF FAME", NamedTextColor.GOLD);
        floating(b, -31.5, BASE + 3.0, 0.5, "/ctfstats - kills, captures and wins", NamedTextColor.GRAY);
    }

    // ---------------------------------------------------------------- north-west: the tavern

    static void tavern(B b) {
        int x1 = -44, x2 = -28, z1 = -42, z2 = -26;
        building(b, x1, z1, x2, z2, 6, B.Pal.of("cobblestone", 3, "mossy_cobblestone", 1), B.Pal.of("oak_planks", 3, "stripped_oak_log", 1),
                "dark_oak", "south", "brown_terracotta");
        for (int x = x1 + 1; x < x2; x++) for (int z = z1 + 1; z < z2; z++) b.set(x, BASE, z, ((x + z) & 1) == 0 ? "spruce_planks" : "dark_oak_planks");
        // the bar along the north wall, kegs behind it
        for (int x = x1 + 3; x <= x2 - 3; x++) {
            b.set(x, BASE + 1, z1 + 4, "spruce_planks");
            b.set(x, BASE + 2, z1 + 4, "spruce_slab[type=bottom]");
            b.set(x, BASE + 1, z1 + 1, x % 2 == 0 ? "barrel[facing=south]" : "barrel[facing=up]");
            if (x % 3 == 0) b.set(x, BASE + 2, z1 + 1, "barrel[facing=south]");
        }
        b.set(x1 + 3, BASE + 3, z1 + 4, "brewing_stand");
        // fireplace on the west wall
        for (int z = -36; z <= -32; z++) for (int dy = 1; dy <= 4; dy++) b.set(x1 + 1, BASE + dy, z, "bricks");
        b.set(x1 + 1, BASE + 1, -34, "campfire[lit=true]");
        b.set(x1 + 1, BASE + 2, -34, "air");
        for (int dy = 5; dy <= 13; dy++) b.set(x1, BASE + dy, -34, "bricks");
        b.set(x1, BASE + 14, -34, "campfire[lit=true,signal_fire=false]");
        // tables with stools
        for (int[] t : new int[][] {{-38, -31}, {-33, -31}, {-38, -28}, {-33, -28}}) {
            b.set(t[0], BASE + 1, t[1], "oak_fence");
            b.set(t[0], BASE + 2, t[1], "oak_pressure_plate");
            b.set(t[0] - 1, BASE + 1, t[1], "spruce_stairs[facing=east]");
            b.set(t[0] + 1, BASE + 1, t[1], "spruce_stairs[facing=west]");
        }
        for (int x = x1 + 3; x <= x2 - 3; x += 4) b.set(x, BASE + 6, (z1 + z2) / 2, "lantern[hanging=true]");
        b.wallBanner(-36, BASE + 5, z2 + 1, "orange", "south");
        floating(b, -35.5, BASE + 3.6, -24.5, "THE BROKEN FLAG", NamedTextColor.GOLD);
        floating(b, -35.5, BASE + 3.3, -24.5, "tavern & rest stop", NamedTextColor.GRAY);
    }

    // ---------------------------------------------------------------- south: camps, training yard, parkour

    static void camp(B b, int cx, int cz, String colour) {
        for (int i = 0; i < 3; i++) tent(b, cx - 10 + i * 10, cz - 7 + (i % 2) * 3, colour, i % 2 == 0);
        // fire pit with log benches
        b.set(cx, BASE, cz + 6, "cobblestone");
        b.set(cx, BASE + 1, cz + 6, "campfire[lit=true]");
        for (int[] d : new int[][] {{-2, 0}, {2, 0}, {0, 2}}) b.set(cx + d[0], BASE + 1, cz + 6 + d[1], "stripped_oak_log[axis=" + (d[0] == 0 ? "x" : "z") + "]");
        for (int[] d : new int[][] {{-6, 6}, {6, 4}, {-5, 9}}) {
            b.set(cx + d[0], BASE + 1, cz + d[1], "barrel[facing=up]");
            b.set(cx + d[0] + 1, BASE + 1, cz + d[1], "hay_block");
        }
        for (int dy = 1; dy <= 6; dy++) b.set(cx + 8, BASE + dy, cz + 8, "spruce_fence");
        b.wallBanner(cx + 8, BASE + 5, cz + 9, colour, "south");
        b.wallBanner(cx + 8, BASE + 5, cz + 7, colour, "north");
    }

    static void tent(B b, int x, int z, String colour, boolean alongX) {
        for (int i = -3; i <= 3; i++)
            for (int h = 0; h <= 3; h++) {
                int w = 3 - h;
                for (int s : new int[] {-w, w}) {
                    int bx = alongX ? x + i : x + s, bz = alongX ? z + s : z + i;
                    b.set(bx, BASE + 1 + h, bz, (i + h) % 3 == 0 ? "white_wool" : colour + "_wool");
                }
            }
        for (int i = -3; i <= 3; i++) {
            int bx = alongX ? x + i : x, bz = alongX ? z : z + i;
            b.set(bx, BASE + 4, bz, "stripped_spruce_log[axis=" + (alongX ? "x" : "z") + "]");
        }
        // open both ends and put bedrolls inside
        for (int h = 1; h <= 2; h++) for (int s = -1; s <= 1; s++) {
            int ex = alongX ? x + 3 : x + s, ez = alongX ? z + s : z + 3;
            b.set(ex, BASE + h, ez, "air");
        }
        b.set(alongX ? x - 1 : x - 1, BASE + 1, alongX ? z - 1 : z - 1, colour + "_carpet");
        b.set(alongX ? x + 1 : x + 1, BASE + 1, alongX ? z + 1 : z + 1, colour + "_carpet");
    }

    static void trainingYard(B b) {
        // sparring ring
        for (int x = -7; x <= 7; x++)
            for (int z = 22; z <= 36; z++) {
                double d = Math.sqrt(x * x + (z - 29) * (z - 29));
                if (d <= 6.5) b.set(x, BASE, z, d > 5.5 ? "stripped_oak_wood" : "coarse_dirt");
                if (Math.abs(d - 6.5) < 0.5 && !(Math.abs(x) <= 1 && z < 29)) b.set(x, BASE + 1, z, "oak_fence");
            }
        for (int dx : new int[] {-2, 2}) {
            b.w.spawn(new Location(b.w, dx + 0.5, BASE + 1, 29.5, dx < 0 ? -90 : 90, 0), ArmorStand.class, s -> {
                s.setArms(true);
                s.setGravity(false);
                s.setPersistent(true);
                s.customName(Component.text("Training Dummy", NamedTextColor.GRAY));
                s.setCustomNameVisible(true);
                s.getEquipment().setHelmet(ItemStack.of(Material.CARVED_PUMPKIN));
                s.getEquipment().setChestplate(ItemStack.of(Material.CHAINMAIL_CHESTPLATE));
                s.getEquipment().setItemInMainHand(ItemStack.of(Material.WOODEN_SWORD));
            }).setDisabledSlots(org.bukkit.inventory.EquipmentSlot.values());
        }
        // archery range against the south wall
        for (int x = -10; x <= 10; x += 5) {
            b.set(x, BASE + 1, 44, "hay_block");
            b.set(x, BASE + 2, 44, "target");
            b.set(x, BASE + 3, 44, "hay_block");
        }
        for (int x = -11; x <= 11; x++) b.set(x, BASE + 1, 39, x % 3 == 0 ? "oak_fence" : "air");
        b.set(-12, BASE + 1, 39, "barrel[facing=up]");
        b.set(12, BASE + 1, 39, "fletching_table");
    }

    /** A parkour climb around the north-east tower, ending on its roof walk. */
    static void parkour(B b) {
        String[] blocks = {"quartz_block", "smooth_quartz", "white_concrete", "light_blue_concrete", "cyan_concrete"};
        double angle = Math.PI;
        int y = BASE + 1;
        int cx = 40, cz = -40;
        b.set(30, BASE + 1, -34, "light_weighted_pressure_plate");
        for (int i = 0; i < 26; i++) {
            double radius = 9;
            int x = cx + (int) Math.round(Math.cos(angle) * radius), z = cz + (int) Math.round(Math.sin(angle) * radius);
            b.set(x, y, z, blocks[i % blocks.length]);
            if (i % 6 == 5) b.set(x, y + 1, z, "lantern");
            angle += 0.42;
            y += (i % 3 == 2) ? 0 : 1;
        }
        int topX = cx + (int) Math.round(Math.cos(angle) * 9), topZ = cz + (int) Math.round(Math.sin(angle) * 9);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) b.set(topX + dx, y, topZ + dz, "gold_block");
        b.set(topX, y + 1, topZ, "heavy_weighted_pressure_plate");
        floating(b, topX + 0.5, y + 1.6, topZ + 0.5, "PARKOUR COMPLETE!", NamedTextColor.GOLD);
        floating(b, 30.5, BASE + 2.2, -33.5, "PARKOUR - climb to the gold", NamedTextColor.AQUA);
    }

    // ---------------------------------------------------------------- paths, gardens, buildings

    static void paths(B b) {
        B.Pal path = B.Pal.of("stone_bricks", 4, "cobblestone", 2, "gravel", 1, "andesite", 1);
        B.Pal dirtPath = B.Pal.of("dirt_path", 4, "coarse_dirt", 1);
        b.path(B.pts(-44, -45, 44, -45, 44, 44, -44, 44, -44, -45), 1, dirtPath);
        b.path(B.pts(-36, -24, -36, -16), 1, dirtPath);
        b.path(B.pts(0, -14, 0, -20), 3, path);
        b.path(B.pts(14, 0, 20, 0), 3, path);
        b.path(B.pts(-14, 0, -20, 0), 3, path);
        b.path(B.pts(0, 14, 0, 47), 3, path);
        b.path(B.pts(-10, 10, -27, 24), 2, path);
        b.path(B.pts(10, 10, 27, 24), 2, path);
        b.path(B.pts(10, -10, 30, -32), 1, path);
        for (int[] p : new int[][] {{4, -17}, {-4, -17}, {17, 4}, {17, -4}, {-17, 4}, {-17, -4}, {4, 18}, {-4, 18}, {4, 42}, {-4, 42}}) {
            b.lampPost(p[0], BASE + 1, p[1], "dark_oak_fence");
        }
    }

    static void gardens(B b) {
        // hedges, flower beds and trees in the open spaces inside the walls, and woods outside
        int[][] beds = {{30, 24}, {-14, 18}, {14, 18}, {-40, 22}, {40, 40}, {-40, -40}};
        for (int[] bed : beds) {
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) == 2 || Math.abs(dz) == 2) b.set(bed[0] + dx, BASE + 1, bed[1] + dz, "oak_leaves[persistent=true]");
                    else b.set(bed[0] + dx, BASE + 1, bed[1] + dz, B.Pal.of("poppy", 1, "cornflower", 1, "allium", 1, "oxeye_daisy", 1, "red_tulip", 1));
                }
        }
        for (int[] t : new int[][] {{-30, -18}, {-22, -40}, {30, -20}, {-44, 0}, {44, 18}, {-10, -44}, {12, -44}, {-44, 40}, {22, 44}, {-22, 44}}) {
            if (b.air(t[0], BASE + 1, t[1])) b.blobTree(t[0], BASE + 1, t[1], 4 + b.r.nextInt(2), b.r.nextBoolean() ? "oak_log" : "birch_log",
                    b.r.nextBoolean() ? "oak_leaves" : "azalea_leaves");
        }
        for (int i = 0; i < 160; i++) {
            int x = b.r.nextInt(150) - 75, z = b.r.nextInt(150) - 75;
            if (Math.max(Math.abs(x), Math.abs(z)) <= 52) continue;
            int gy = b.ground(x, z);
            if (!b.at(x, gy, z).getType().name().equals("GRASS_BLOCK") || !b.air(x, gy + 1, z)) continue;
            if (b.r.nextInt(3) == 0) b.spruce(x, gy + 1, z, 7 + b.r.nextInt(4), false);
            else b.blobTree(x, gy + 1, z, 4 + b.r.nextInt(3), "oak_log", "oak_leaves");
        }
        b.plants(-80, 80, -80, 80, 0.10, B.Pal.of("short_grass", 10, "poppy", 1, "dandelion", 1, "cornflower", 1), "GRASS_BLOCK");
    }

    /** A rectangular hall: stone base, framed walls, windows, a pitched terracotta roof, a big door on one side. */
    static void building(B b, int x1, int z1, int x2, int z2, int height, B.Pal base, B.Pal walls, String wood, String door, String roof) {
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                b.set(x, BASE, z, edge ? "stone_bricks" : "polished_andesite");
                for (int dy = 1; dy <= height; dy++) {
                    if (!edge) {
                        b.set(x, BASE + dy, z, "air");
                        continue;
                    }
                    boolean pillar = (x - x1) % 6 == 0 && (z == z1 || z == z2) || (z - z1) % 6 == 0 && (x == x1 || x == x2);
                    String block;
                    if (pillar) block = "stripped_" + wood + "_log";
                    else if (dy <= 2) block = null;
                    else if (dy >= 3 && dy <= height - 3 && ((x + z) % 3 != 0)) block = "glass_pane";
                    else block = null;
                    if (block == null) b.set(x, BASE + dy, z, dy <= 2 ? base : walls);
                    else b.set(x, BASE + dy, z, block);
                }
            }
        // roof along the long axis
        boolean alongZ = (z2 - z1) >= (x2 - x1);
        int span = alongZ ? (x2 - x1) : (z2 - z1);
        for (int s = 0; s <= span / 2 + 1; s++) {
            int y = BASE + height + 1 + s / 1;
            if (alongZ) {
                for (int z = z1 - 1; z <= z2 + 1; z++) {
                    b.set(x1 - 1 + s, y, z, roof);
                    b.set(x2 + 1 - s, y, z, roof);
                }
            } else {
                for (int x = x1 - 1; x <= x2 + 1; x++) {
                    b.set(x, y, z1 - 1 + s, roof);
                    b.set(x, y, z2 + 1 - s, roof);
                }
            }
        }
        // the door: 3 wide, 4 high, in the middle of the given side
        int mx = (x1 + x2) / 2, mz = (z1 + z2) / 2;
        for (int dy = 1; dy <= 4; dy++)
            for (int d = -1; d <= 1; d++) {
                switch (door) {
                    case "west" -> b.set(x1, BASE + dy, mz + d, "air");
                    case "east" -> b.set(x2, BASE + dy, mz + d, "air");
                    case "north" -> b.set(mx + d, BASE + dy, z1, "air");
                    default -> b.set(mx + d, BASE + dy, z2, "air");
                }
            }
    }

    /** A floating label (a text display, so it sits exactly where it's put). */
    static void floating(B b, double x, double y, double z, String text, NamedTextColor colour) {
        b.w.spawn(new Location(b.w, x, y, z), org.bukkit.entity.TextDisplay.class, d -> {
            d.text(Component.text(text, colour).decoration(TextDecoration.BOLD, colour == NamedTextColor.GOLD));
            d.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            d.setPersistent(true);
            d.setShadowed(true);
            d.setBackgroundColor(Color.fromARGB(0x40000000));
        });
    }
}
