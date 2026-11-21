/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.kit;

import com.sasha.ctf.model.TeamColor;
import com.sasha.ctf.util.Items;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The eight classic Warzone kits. Every kit also gets a cap in the team colour (so teams can tell each
 * other apart, and the flag carrier's banner goes there) and 32 blocks in the team colour for building.
 */
public enum Kit {
    SOLDIER("Soldier", Material.IRON_SWORD, "Tough all-rounder with an iron sword and iron armour."),
    ARCHER("Archer", Material.BOW, "Picks enemies off from range with a Power bow."),
    MEDIC("Medic", Material.GLISTERING_MELON_SLICE, "Heals teammates with the Medkit and healing potions."),
    NINJA("Ninja", Material.ENDER_PEARL, "Fast and sharp, with ender pearls to slip past defenders."),
    PYRO("Pyro", Material.BLAZE_POWDER, "Sets enemies alight; immune to fire."),
    TANK("Tank", Material.SHIELD, "Diamond chestplate and a shield, but slow."),
    SCOUT("Scout", Material.FEATHER, "Very fast with a high jump: the best flag runner."),
    CHEMIST("Chemist", Material.BREWING_STAND, "Throws poison, slowness and harming potions.");

    /** Tag on the Medic's Medkit, used by the game listener. */
    public static final String MEDKIT = "medkit";

    private final String display;
    private final Material icon;
    private final String description;

    Kit(String display, Material icon, String description) {
        this.display = display;
        this.icon = icon;
        this.description = description;
    }

    public String display() { return display; }
    public Material icon() { return icon; }
    public String description() { return description; }
    public String id() { return name().toLowerCase(Locale.ROOT); }

    public static Optional<Kit> parse(String input) {
        if (input == null) return Optional.empty();
        for (Kit kit : values()) if (kit.name().equalsIgnoreCase(input) || kit.display.equalsIgnoreCase(input)) return Optional.of(kit);
        return Optional.empty();
    }

    /** The loadout, one line each, for the kit menu. */
    public List<String> contents() {
        return switch (this) {
            case SOLDIER -> List.of("Iron sword", "Iron chestplate, leggings and boots", "2 golden apples");
            case ARCHER -> List.of("Stone sword", "Bow (Power I) and 64 arrows", "Chainmail chestplate");
            case MEDIC -> List.of("Stone sword", "Medkit: right-click a teammate to heal them", "4 healing potions", "Chainmail armour");
            case NINJA -> List.of("Golden sword (Sharpness II)", "3 ender pearls", "Speed II");
            case PYRO -> List.of("Stone sword (Fire Aspect)", "Bow (Flame) and 32 arrows", "Fire Resistance");
            case TANK -> List.of("Stone sword and a shield", "Diamond chestplate, iron leggings and boots", "Slowness I");
            case SCOUT -> List.of("Stone sword", "Speed II and Jump Boost II", "Light leather armour");
            case CHEMIST -> List.of("Wooden sword", "Splash potions: 3 harming, 3 poison, 3 slowness", "2 healing potions");
        };
    }

    /** Clears the player and gives them this kit in their team's colours. */
    public void apply(Player player, TeamColor team) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        for (PotionEffect effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
        player.setFireTicks(0);
        player.setHealth(player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) == null ? 20
                : player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
        player.setFoodLevel(20);
        player.setSaturation(20);

        inventory.setHelmet(Items.leather(Material.LEATHER_HELMET, team.leather()));
        switch (this) {
            case SOLDIER -> {
                inventory.addItem(Items.item(Material.IRON_SWORD, 1));
                inventory.setChestplate(Items.item(Material.IRON_CHESTPLATE, 1));
                inventory.setLeggings(Items.item(Material.IRON_LEGGINGS, 1));
                inventory.setBoots(Items.item(Material.IRON_BOOTS, 1));
                inventory.addItem(ItemStack.of(Material.GOLDEN_APPLE, 2));
            }
            case ARCHER -> {
                inventory.addItem(Items.item(Material.STONE_SWORD, 1));
                inventory.addItem(Items.enchanted(Material.BOW, Enchantment.POWER, 1));
                inventory.setChestplate(Items.item(Material.CHAINMAIL_CHESTPLATE, 1));
                inventory.setLeggings(Items.leather(Material.LEATHER_LEGGINGS, team.leather()));
                inventory.setBoots(Items.leather(Material.LEATHER_BOOTS, team.leather()));
                inventory.setItem(9, ItemStack.of(Material.ARROW, 64));
            }
            case MEDIC -> {
                inventory.addItem(Items.item(Material.STONE_SWORD, 1));
                inventory.addItem(Items.tag(Items.named(Material.RED_DYE,
                        Component.text("Medkit", NamedTextColor.RED),
                        List.of(Component.text("Right-click a teammate to heal them.", NamedTextColor.GRAY),
                                Component.text("Right-click the air to heal yourself.", NamedTextColor.GRAY))), MEDKIT));
                inventory.addItem(Items.potion(Material.SPLASH_POTION, PotionType.STRONG_HEALING, 4));
                inventory.setChestplate(Items.item(Material.CHAINMAIL_CHESTPLATE, 1));
                inventory.setLeggings(Items.item(Material.CHAINMAIL_LEGGINGS, 1));
                inventory.setBoots(Items.item(Material.CHAINMAIL_BOOTS, 1));
            }
            case NINJA -> {
                inventory.addItem(Items.enchanted(Material.GOLDEN_SWORD, Enchantment.SHARPNESS, 2));
                inventory.addItem(ItemStack.of(Material.ENDER_PEARL, 3));
                inventory.setChestplate(Items.leather(Material.LEATHER_CHESTPLATE, org.bukkit.Color.fromRGB(0x1D1D21)));
                inventory.setLeggings(Items.leather(Material.LEATHER_LEGGINGS, org.bukkit.Color.fromRGB(0x1D1D21)));
                inventory.setBoots(Items.leather(Material.LEATHER_BOOTS, org.bukkit.Color.fromRGB(0x1D1D21)));
                effect(player, PotionEffectType.SPEED, 1);
            }
            case PYRO -> {
                inventory.addItem(Items.enchanted(Material.STONE_SWORD, Enchantment.FIRE_ASPECT, 1));
                inventory.addItem(Items.enchanted(Material.BOW, Enchantment.FLAME, 1));
                inventory.setChestplate(Items.item(Material.IRON_CHESTPLATE, 1));
                inventory.setLeggings(Items.leather(Material.LEATHER_LEGGINGS, team.leather()));
                inventory.setBoots(Items.leather(Material.LEATHER_BOOTS, team.leather()));
                inventory.setItem(9, ItemStack.of(Material.ARROW, 32));
                effect(player, PotionEffectType.FIRE_RESISTANCE, 0);
            }
            case TANK -> {
                inventory.addItem(Items.item(Material.STONE_SWORD, 1));
                inventory.setItemInOffHand(Items.item(Material.SHIELD, 1));
                inventory.setChestplate(Items.item(Material.DIAMOND_CHESTPLATE, 1));
                inventory.setLeggings(Items.item(Material.IRON_LEGGINGS, 1));
                inventory.setBoots(Items.item(Material.IRON_BOOTS, 1));
                inventory.addItem(ItemStack.of(Material.GOLDEN_APPLE, 1));
                effect(player, PotionEffectType.SLOWNESS, 0);
            }
            case SCOUT -> {
                inventory.addItem(Items.item(Material.STONE_SWORD, 1));
                inventory.setChestplate(Items.leather(Material.LEATHER_CHESTPLATE, team.leather()));
                inventory.setBoots(Items.leather(Material.LEATHER_BOOTS, team.leather()));
                effect(player, PotionEffectType.SPEED, 1);
                effect(player, PotionEffectType.JUMP_BOOST, 1);
            }
            case CHEMIST -> {
                inventory.addItem(Items.item(Material.WOODEN_SWORD, 1));
                inventory.addItem(Items.potion(Material.SPLASH_POTION, PotionType.HARMING, 3));
                inventory.addItem(Items.potion(Material.SPLASH_POTION, PotionType.POISON, 3));
                inventory.addItem(Items.potion(Material.SPLASH_POTION, PotionType.SLOWNESS, 3));
                inventory.addItem(Items.potion(Material.SPLASH_POTION, PotionType.HEALING, 2));
                inventory.setChestplate(Items.item(Material.CHAINMAIL_CHESTPLATE, 1));
                inventory.setLeggings(Items.leather(Material.LEATHER_LEGGINGS, team.leather()));
                inventory.setBoots(Items.leather(Material.LEATHER_BOOTS, team.leather()));
            }
        }
        inventory.addItem(ItemStack.of(team.block(), 32));
    }

    private static void effect(Player player, PotionEffectType type, int amplifier) {
        player.addPotionEffect(new PotionEffect(type, PotionEffect.INFINITE_DURATION, amplifier, true, false, true));
    }
}
