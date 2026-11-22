/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;

import java.util.List;

/** Small helpers for building items. */
public final class Items {

    private static NamespacedKey tagKey;
    private static NamespacedKey slotKey;

    private Items() {}

    public static void init(NamespacedKey key, NamespacedKey layoutKey) {
        tagKey = key;
        slotKey = layoutKey;
    }

    /** Which kit item this is ("sword", "bow", ...), so the layout editor can find it again. */
    public static ItemStack layoutKey(ItemStack item, String key) {
        item.editMeta(meta -> meta.getPersistentDataContainer().set(slotKey, PersistentDataType.STRING, key));
        return item;
    }

    public static String layoutKeyOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(slotKey, PersistentDataType.STRING);
    }

    public static ItemStack item(Material material, int amount) {
        ItemStack item = ItemStack.of(material, amount);
        item.editMeta(meta -> meta.setUnbreakable(true));
        return item;
    }

    public static ItemStack named(Material material, Component name, List<Component> lore) {
        ItemStack item = ItemStack.of(material);
        item.editMeta(meta -> {
            meta.displayName(name.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            meta.lore(lore.stream().map(line -> line.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)).toList());
            meta.addItemFlags(ItemFlag.values());
        });
        return item;
    }

    /** Marks an item so the plugin can tell what it's for ("kit-menu", "vote", "medkit"...). */
    public static ItemStack tag(ItemStack item, String tag) {
        item.editMeta(meta -> meta.getPersistentDataContainer().set(tagKey, PersistentDataType.STRING, tag));
        return item;
    }

    public static String tagOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(tagKey, PersistentDataType.STRING);
    }

    public static ItemStack enchanted(Material material, Enchantment enchantment, int level) {
        ItemStack item = item(material, 1);
        item.addUnsafeEnchantment(enchantment, level);
        return item;
    }

    public static ItemStack leather(Material material, Color color) {
        ItemStack item = item(material, 1);
        item.editMeta(LeatherArmorMeta.class, meta -> meta.setColor(color));
        return item;
    }

    public static ItemStack potion(Material material, PotionType type, int amount) {
        ItemStack item = ItemStack.of(material, amount);
        item.editMeta(PotionMeta.class, meta -> meta.setBasePotionType(type));
        return item;
    }
}
