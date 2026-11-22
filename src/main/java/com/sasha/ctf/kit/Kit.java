/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.kit;

import com.sasha.ctf.model.TeamColor;
import com.sasha.ctf.util.Items;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The classic Warzone kits, with the armour, items and abilities they had on Minehut. Leather armour
 * is dyed in the team colour. Every item has a key, so players can rearrange their kit in the layout
 * editor and get it back the same way every respawn.
 */
public enum Kit {
    BARBARIAN("Barbarian", Material.STONE_SWORD, 0, Ability.BRUTE_FORCE,
            "Brute Force: your next hit lands extra hard."),
    BIG_SPENDER("Big Spender", Material.GOLD_INGOT, 1000, Ability.COIN_TOSS,
            "Coin Toss: throw coins that hurt on impact."),
    BUILDER("Builder", Material.IRON_PICKAXE, 500, Ability.SUPER_BREAKER,
            "Super Breaker: dig really fast for 5 seconds."),
    CHORUSER("Choruser", Material.CHORUS_FRUIT, 500, Ability.NONE,
            "Chorus fruit to teleport out of trouble."),
    DEMOMAN("Demoman", Material.TNT, 1000, Ability.THROW_TNT,
            "Throw TNT at players and their builds."),
    ENGINEER("Engineer", Material.PURPUR_BLOCK, 1500, Ability.TURRET,
            "Turret: a shulker that shoots enemies."),
    FIRE_ARCHER("Fire Archer", Material.BOW, 0, Ability.NONE,
            "Your arrows can set enemies on fire."),
    INFILTRATOR("Infiltrator", Material.SUGAR, 500, Ability.SPEED_BOOST,
            "Speed Boost: 3 seconds of speed to grab the flag."),
    MEDIC("Medic", Material.GLISTERING_MELON_SLICE, 0, Ability.HEALING_ZONE,
            "Healing Zone: heal yourself and nearby teammates."),
    NECROMANCER("Necromancer", Material.WITHER_SKELETON_SKULL, 1500, Ability.RAISE_DEAD,
            "Raise Dead: undead that fight the enemy team."),
    NINJA("Ninja", Material.NETHER_STAR, 500, Ability.STAR_SPIN,
            "Star Spin: teleport somewhere nearby."),
    PHOENIX("Phoenix", Material.BLAZE_POWDER, 1500, Ability.FIRE_BREATH,
            "Fire Breath: burn enemies in front of you."),
    REDSTONE_MECHANIC("Redstone Mechanic", Material.REDSTONE_TORCH, 1500, Ability.SPAWN_TORCH,
            "Respawn at your redstone torch until it is broken."),
    SEEKING_ARCHER("Seeking Archer", Material.SPECTRAL_ARROW, 1000, Ability.NONE,
            "Your arrows home in on enemies."),
    SHEEP_FARMER("Sheep Farmer", Material.SHEEP_SPAWN_EGG, 1500, Ability.EXPLODING_SHEEP,
            "Exploding Sheep: they blow up near enemies."),
    SHIELDER("Shielder", Material.SHIELD, 0, Ability.NONE,
            "Block hits with your shield."),
    SPY("Spy", Material.LEATHER_CHESTPLATE, 1000, Ability.DISGUISE,
            "Disguise: wear the enemy's colours for 10 seconds."),
    TANK("Tank", Material.DIAMOND_CHESTPLATE, 1000, Ability.GROUND_SLAM,
            "Ground Slam: launch nearby enemies into the air."),
    THOR("Thor", Material.LIGHTNING_ROD, 1500, Ability.LIGHTNING,
            "Lightning: strike where you look.");

    /** One item in a kit: its key (for layouts), the item, and its default slot (0-8 hotbar, 9-35 inventory). */
    public record KitItem(String key, ItemStack item, int slot) {}

    /** What the armour slots hold: helmet, chestplate, leggings, boots (null = nothing). */
    public record Armour(ItemStack helmet, ItemStack chestplate, ItemStack leggings, ItemStack boots) {}

    public static final String TAG_PREFIX = "kit:";

    private final String display;
    private final Material icon;
    private final int price;
    private final Ability ability;
    private final String description;

    Kit(String display, Material icon, int price, Ability ability, String description) {
        this.display = display;
        this.icon = icon;
        this.price = price;
        this.ability = ability;
        this.description = description;
    }

    public String display() { return display; }
    public Material icon() { return icon; }
    public int defaultPrice() { return price; }
    public Ability ability() { return ability; }
    public String description() { return description; }
    public String id() { return name().toLowerCase(Locale.ROOT); }

    public static Optional<Kit> parse(String input) {
        if (input == null) return Optional.empty();
        String key = input.replace(' ', '_');
        for (Kit kit : values()) if (kit.name().equalsIgnoreCase(key) || kit.display.equalsIgnoreCase(input)) return Optional.of(kit);
        return Optional.empty();
    }

    // ---------------------------------------------------------------- loadouts

    public Armour armour(TeamColor team) {
        Color c = team.leather();
        return switch (this) {
            case BARBARIAN, SPY -> new Armour(leather(Material.LEATHER_HELMET, c), leather(Material.LEATHER_CHESTPLATE, c),
                    leather(Material.LEATHER_LEGGINGS, c), leather(Material.LEATHER_BOOTS, c));
            case BIG_SPENDER -> new Armour(plain(Material.GOLDEN_HELMET), plain(Material.GOLDEN_CHESTPLATE),
                    plain(Material.GOLDEN_LEGGINGS), plain(Material.GOLDEN_BOOTS));
            case BUILDER -> new Armour(leather(Material.LEATHER_HELMET, c), leather(Material.LEATHER_CHESTPLATE, c), null,
                    leather(Material.LEATHER_BOOTS, c));
            case CHORUSER -> new Armour(leather(Material.LEATHER_HELMET, c), leather(Material.LEATHER_CHESTPLATE, c),
                    plain(Material.CHAINMAIL_LEGGINGS), leather(Material.LEATHER_BOOTS, c));
            case DEMOMAN -> new Armour(null, plain(Material.IRON_CHESTPLATE), leather(Material.LEATHER_LEGGINGS, c),
                    leather(Material.LEATHER_BOOTS, c));
            case ENGINEER -> new Armour(plain(Material.GOLDEN_HELMET), leather(Material.LEATHER_CHESTPLATE, c), null, null);
            case FIRE_ARCHER, SHEEP_FARMER -> new Armour(null, leather(Material.LEATHER_CHESTPLATE, c),
                    leather(Material.LEATHER_LEGGINGS, c), leather(Material.LEATHER_BOOTS, c));
            case INFILTRATOR -> new Armour(null, leather(Material.LEATHER_CHESTPLATE, c), leather(Material.LEATHER_LEGGINGS, c), null);
            case MEDIC -> new Armour(leather(Material.LEATHER_HELMET, c), plain(Material.GOLDEN_CHESTPLATE), null,
                    leather(Material.LEATHER_BOOTS, c));
            case NECROMANCER -> new Armour(plain(Material.WITHER_SKELETON_SKULL), leather(Material.LEATHER_CHESTPLATE, c), null,
                    plain(Material.IRON_BOOTS));
            case NINJA -> new Armour(leather(Material.LEATHER_HELMET, c), null, null, leather(Material.LEATHER_BOOTS, c));
            case PHOENIX -> new Armour(plain(Material.CHAINMAIL_HELMET), leather(Material.LEATHER_CHESTPLATE, c), null, null);
            case REDSTONE_MECHANIC -> new Armour(leather(Material.LEATHER_HELMET, c), plain(Material.IRON_CHESTPLATE), null, null);
            case SEEKING_ARCHER -> new Armour(leather(Material.LEATHER_HELMET, c), leather(Material.LEATHER_CHESTPLATE, c), null,
                    plain(Material.GOLDEN_BOOTS));
            case SHIELDER -> new Armour(leather(Material.LEATHER_HELMET, c), leather(Material.LEATHER_CHESTPLATE, c),
                    plain(Material.IRON_LEGGINGS), leather(Material.LEATHER_BOOTS, c));
            case TANK -> new Armour(leather(Material.LEATHER_HELMET, c), plain(Material.DIAMOND_CHESTPLATE),
                    leather(Material.LEATHER_LEGGINGS, c), plain(Material.IRON_BOOTS));
            case THOR -> new Armour(plain(Material.DIAMOND_HELMET), leather(Material.LEATHER_CHESTPLATE, c), null, null);
        };
    }

    /** The kit's items, in their default slots. */
    public List<KitItem> items(TeamColor team) {
        List<KitItem> items = new ArrayList<>();
        switch (this) {
            case BARBARIAN -> standard(items, Material.STONE_SWORD, false);
            case BIG_SPENDER -> {
                items.add(new KitItem("sword", plain(Material.GOLDEN_SWORD), 0));
                items.add(new KitItem("bow", plain(Material.BOW), 1));
                items.add(new KitItem("gapple", ItemStack.of(Material.GOLDEN_APPLE), 3));
                items.add(new KitItem("blocks", ItemStack.of(Material.YELLOW_STAINED_GLASS, 64), 4));
                items.add(new KitItem("axe", plain(Material.GOLDEN_AXE), 5));
                items.add(new KitItem("arrows", ItemStack.of(Material.ARROW, 64), 9));
            }
            case BUILDER -> {
                items.add(new KitItem("pickaxe", plain(Material.IRON_PICKAXE), 0));
                items.add(new KitItem("axe", plain(Material.STONE_AXE), 1));
                items.add(new KitItem("bow", plain(Material.BOW), 2));
                items.add(new KitItem("gapple", ItemStack.of(Material.GOLDEN_APPLE), 4));
                items.add(new KitItem("planks", ItemStack.of(Material.OAK_PLANKS, 64), 5));
                items.add(new KitItem("stairs", ItemStack.of(Material.OAK_STAIRS, 64), 6));
                items.add(new KitItem("ladders", ItemStack.of(Material.LADDER, 16), 7));
                items.add(new KitItem("glass", ItemStack.of(Material.GLASS, 64), 8));
                items.add(new KitItem("logs", ItemStack.of(Material.OAK_LOG, 64), 9));
                items.add(new KitItem("logs2", ItemStack.of(Material.OAK_LOG, 64), 10));
                items.add(new KitItem("arrows", ItemStack.of(Material.ARROW, 32), 11));
            }
            case CHORUSER -> {
                standard(items, Material.STONE_SWORD, false);
                items.add(new KitItem("chorus", ItemStack.of(Material.CHORUS_FRUIT, 2), 7));
            }
            case DEMOMAN, SHEEP_FARMER, REDSTONE_MECHANIC -> standard(items, Material.STONE_SWORD, false);
            case ENGINEER -> {
                items.add(new KitItem("pickaxe", plain(Material.IRON_PICKAXE), 0));
                items.add(new KitItem("bow", plain(Material.BOW), 1));
                items.add(new KitItem("gapple", ItemStack.of(Material.GOLDEN_APPLE), 3));
                items.add(new KitItem("planks", ItemStack.of(Material.OAK_PLANKS, 64), 4));
                items.add(new KitItem("axe", plain(Material.WOODEN_AXE), 5));
                items.add(new KitItem("arrows", ItemStack.of(Material.ARROW, 64), 9));
            }
            case FIRE_ARCHER -> {
                items.add(new KitItem("sword", plain(Material.STONE_SWORD), 0));
                items.add(new KitItem("bow", plain(Material.BOW), 1));
                items.add(new KitItem("gapple", ItemStack.of(Material.GOLDEN_APPLE), 3));
                items.add(new KitItem("planks", ItemStack.of(Material.OAK_PLANKS, 64), 4));
                items.add(new KitItem("arrows", ItemStack.of(Material.ARROW, 64), 9));
            }
            case INFILTRATOR -> {
                items.add(new KitItem("bow", plain(Material.BOW), 0));
                items.add(new KitItem("gapple", ItemStack.of(Material.GOLDEN_APPLE), 3));
                items.add(new KitItem("planks", ItemStack.of(Material.OAK_PLANKS, 64), 4));
                items.add(new KitItem("arrows", ItemStack.of(Material.ARROW, 64), 9));
            }
            case MEDIC -> {
                items.add(new KitItem("axe", plain(Material.STONE_AXE), 0));
                items.add(new KitItem("bow", plain(Material.BOW), 1));
                items.add(new KitItem("gapple", ItemStack.of(Material.GOLDEN_APPLE), 3));
                items.add(new KitItem("planks", ItemStack.of(Material.OAK_PLANKS, 64), 4));
                items.add(new KitItem("arrows", ItemStack.of(Material.ARROW, 64), 9));
            }
            case NECROMANCER, TANK -> {
                items.add(new KitItem("sword", enchanted(Material.WOODEN_SWORD, Enchantment.SHARPNESS, 1), 0));
                items.add(new KitItem("bow", plain(Material.BOW), 1));
                items.add(new KitItem("gapple", ItemStack.of(Material.GOLDEN_APPLE), 3));
                items.add(new KitItem("planks", ItemStack.of(Material.OAK_PLANKS, 64), 4));
                items.add(new KitItem("axe", plain(Material.WOODEN_AXE), 5));
                items.add(new KitItem("arrows", ItemStack.of(Material.ARROW, 64), 9));
            }
            case NINJA -> {
                items.add(new KitItem("sword", plain(Material.WOODEN_SWORD), 0));
                items.add(new KitItem("gapple", ItemStack.of(Material.GOLDEN_APPLE), 3));
                items.add(new KitItem("planks", ItemStack.of(Material.OAK_PLANKS, 64), 4));
                items.add(new KitItem("axe", plain(Material.WOODEN_AXE), 5));
            }
            case PHOENIX, SEEKING_ARCHER, SPY -> standard(items, Material.WOODEN_SWORD, false);
            case SHIELDER -> standard(items, Material.STONE_SWORD, false);
            case THOR -> {
                items.add(new KitItem("bow", plain(Material.BOW), 0));
                items.add(new KitItem("gapple", ItemStack.of(Material.GOLDEN_APPLE), 3));
                items.add(new KitItem("planks", ItemStack.of(Material.OAK_PLANKS, 64), 4));
                items.add(new KitItem("axe", plain(Material.WOODEN_AXE), 5));
                items.add(new KitItem("arrows", ItemStack.of(Material.ARROW, 64), 9));
            }
        }
        if (ability != Ability.NONE) items.add(new KitItem("ability", ability.item(), 2));
        return items;
    }

    /** Sword, bow, golden apple, 64 planks, wooden axe and 64 arrows: what most Warzone kits had. */
    private static void standard(List<KitItem> items, Material sword, boolean sharp) {
        items.add(new KitItem("sword", sharp ? enchanted(sword, Enchantment.SHARPNESS, 1) : plain(sword), 0));
        items.add(new KitItem("bow", plain(Material.BOW), 1));
        items.add(new KitItem("gapple", ItemStack.of(Material.GOLDEN_APPLE), 3));
        items.add(new KitItem("planks", ItemStack.of(Material.OAK_PLANKS, 64), 4));
        items.add(new KitItem("axe", plain(Material.WOODEN_AXE), 5));
        items.add(new KitItem("arrows", ItemStack.of(Material.ARROW, 64), 9));
    }

    /** What's in the kit, one line each, for the menus. */
    public List<String> contents() {
        List<String> lines = new ArrayList<>();
        Armour armour = armour(TeamColor.RED);
        List<String> worn = new ArrayList<>();
        for (ItemStack piece : new ItemStack[] {armour.helmet(), armour.chestplate(), armour.leggings(), armour.boots()}) {
            if (piece != null) worn.add(nice(piece.getType()));
        }
        if (!worn.isEmpty()) lines.add(String.join(", ", worn));
        List<String> carried = new ArrayList<>();
        for (KitItem item : items(TeamColor.RED)) {
            if (item.key().equals("ability")) continue;
            int amount = item.item().getAmount();
            carried.add((amount > 1 ? amount + " " : "") + nice(item.item().getType()));
        }
        if (this == SHIELDER) carried.add(0, "Shield");
        lines.add(String.join(", ", carried));
        if (ability != Ability.NONE) lines.add("Ability: " + ability.display());
        return lines;
    }

    private static String nice(Material material) {
        String name = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    // ---------------------------------------------------------------- items

    static ItemStack plain(Material material) {
        return Items.item(material, 1);
    }

    static ItemStack leather(Material material, Color color) {
        return Items.leather(material, color);
    }

    static ItemStack enchanted(Material material, Enchantment enchantment, int level) {
        return Items.enchanted(material, enchantment, level);
    }

    /** The kits' abilities: the item that triggers it (right-click), and its cooldown. */
    public enum Ability {
        NONE(null, "", 0, ""),
        BRUTE_FORCE(Material.FLINT, "Brute Force", 20, "Right-click: your next hit deals 5 extra damage."),
        COIN_TOSS(Material.GOLD_NUGGET, "Coin Toss", 3, "Right-click: throw a coin (4 damage)."),
        SUPER_BREAKER(Material.GLOWSTONE_DUST, "Super Breaker", 30, "Right-click: dig very fast for 5 seconds."),
        THROW_TNT(Material.TNT, "Throwing TNT", 15, "Right-click: throw TNT at players and their builds."),
        TURRET(Material.PURPUR_BLOCK, "Turret", 0, "Place it: a shulker turret that shoots enemies (one per life)."),
        SPEED_BOOST(Material.SUGAR, "Speed Boost", 15, "Right-click: Speed III for 3 seconds."),
        HEALING_ZONE(Material.GLISTERING_MELON_SLICE, "Healing Zone", 20, "Right-click: heal yourself and teammates nearby."),
        RAISE_DEAD(Material.BONE, "Raise Dead", 40, "Right-click: raise 3 zombies that hunt the enemy for 20 seconds."),
        STAR_SPIN(Material.NETHER_STAR, "Star Spin", 10, "Right-click: teleport somewhere nearby."),
        FIRE_BREATH(Material.BLAZE_POWDER, "Fire Breath", 12, "Right-click: set enemies in front of you on fire."),
        SPAWN_TORCH(Material.REDSTONE_TORCH, "Redstone Spawn", 0, "Place it: you respawn there until an enemy breaks it."),
        EXPLODING_SHEEP(Material.SHEEP_SPAWN_EGG, "Exploding Sheep", 20, "Right-click a block: a sheep that explodes after 3 seconds."),
        DISGUISE(Material.GRAY_DYE, "Disguise", 30, "Right-click: look like the enemy for 10 seconds."),
        GROUND_SLAM(Material.IRON_INGOT, "Ground Slam", 20, "Right-click: launch nearby enemies into the air."),
        LIGHTNING(Material.LIGHTNING_ROD, "Lightning Strike", 20, "Right-click: strike lightning where you look.");

        private final Material material;
        private final String display;
        private final int cooldown;
        private final String help;

        Ability(Material material, String display, int cooldown, String help) {
            this.material = material;
            this.display = display;
            this.cooldown = cooldown;
            this.help = help;
        }

        public Material material() { return material; }
        public String display() { return display; }
        public int cooldownSeconds() { return cooldown; }

        public ItemStack item() {
            ItemStack item = Items.named(material, Component.text(display, NamedTextColor.GOLD),
                    List.of(Component.text(help, NamedTextColor.GRAY)));
            return Items.tag(item, TAG_PREFIX + name());
        }

        public static Ability of(String tag) {
            if (tag == null || !tag.startsWith(TAG_PREFIX)) return NONE;
            try {
                return valueOf(tag.substring(TAG_PREFIX.length()));
            } catch (IllegalArgumentException e) {
                return NONE;
            }
        }
    }
}
