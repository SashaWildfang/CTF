/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.kit;

import com.sasha.ctf.FractalCTF;
import com.sasha.ctf.config.Messages;
import com.sasha.ctf.data.Database;
import com.sasha.ctf.model.TeamColor;
import com.sasha.ctf.util.Items;
import com.sasha.ctf.util.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Players' kits: which one they picked, which they own, how they laid each one out, and their coins.
 * All of it lives in the database, so what's chosen in the hub is there on the game server.
 */
public final class Kits {

    /** What we know about one player's kits. */
    static final class PlayerKits {
        volatile Kit chosen;
        final Set<Kit> owned = ConcurrentHashMap.newKeySet();
        final Map<Kit, Map<String, Integer>> layouts = new ConcurrentHashMap<>();
        volatile int coins;
        volatile boolean loaded;
    }

    private final FractalCTF plugin;
    private final Map<UUID, PlayerKits> players = new ConcurrentHashMap<>();
    private final KitEditor editor;

    public Kits(FractalCTF plugin) {
        this.plugin = plugin;
        this.editor = new KitEditor(plugin, this);
        Bukkit.getPluginManager().registerEvents(editor, plugin);
    }

    private PlayerKits data(UUID player) {
        return players.computeIfAbsent(player, id -> new PlayerKits());
    }

    public Kit defaultKit() {
        return Kit.parse(plugin.ctfConfig().defaultKit()).orElse(Kit.BARBARIAN);
    }

    public Kit of(UUID player) {
        PlayerKits data = players.get(player);
        Kit kit = data == null ? null : data.chosen;
        return kit == null || !owns(player, kit) ? defaultKit() : kit;
    }

    public boolean owns(UUID player, Kit kit) {
        if (plugin.ctfConfig().price(kit) <= 0) return true;
        PlayerKits data = players.get(player);
        return data != null && data.owned.contains(kit);
    }

    public int coins(UUID player) {
        PlayerKits data = players.get(player);
        return data == null ? 0 : data.coins;
    }

    /** After a match the game server added coins: keeps the hub's number in step without a reload. */
    public void setCoins(UUID player, int coins) {
        data(player).coins = coins;
    }

    /** Loads a player's kit choice, owned kits, layouts and coins; {@code then} runs on the main thread after. */
    public void load(UUID player, Runnable then) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Database database = plugin.database();
            if (database != null) {
                try {
                    PlayerKits data = data(player);
                    database.readKit(player).flatMap(Kit::parse).ifPresent(kit -> data.chosen = kit);
                    Database.KitData kitData = database.readKitData(player);
                    data.owned.clear();
                    for (String id : kitData.owned()) Kit.parse(id).ifPresent(data.owned::add);
                    data.layouts.clear();
                    for (Map.Entry<String, String> entry : kitData.layouts().entrySet()) {
                        Kit.parse(entry.getKey()).ifPresent(kit -> data.layouts.put(kit, decode(entry.getValue())));
                    }
                    data.coins = kitData.coins();
                    data.loaded = true;
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "Couldn't load a player's kits", e);
                }
            }
            if (then != null) Bukkit.getScheduler().runTask(plugin, then);
        });
    }

    public void forget(UUID player) {
        players.remove(player);
    }

    // ---------------------------------------------------------------- choosing and buying

    public void choose(Player player, Kit kit) {
        if (!owns(player.getUniqueId(), kit)) {
            plugin.messages().send(player, "kits.locked", Messages.arg("kit", kit.display()), Messages.arg("price", plugin.ctfConfig().price(kit)));
            return;
        }
        data(player.getUniqueId()).chosen = kit;
        UUID id = player.getUniqueId();
        async(() -> plugin.database().saveKit(id, kit.id()));
        plugin.messages().send(player, plugin.ctfConfig().hub() ? "kits.chosen-hub" : "kits.chosen-game", Messages.arg("kit", kit.display()));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
        if (plugin.hub() != null) plugin.hub().board().update(player);
    }

    public void buy(Player player, Kit kit) {
        Messages messages = plugin.messages();
        int price = plugin.ctfConfig().price(kit);
        if (owns(player.getUniqueId(), kit)) {
            choose(player, kit);
            return;
        }
        if (plugin.database() == null) {
            messages.send(player, "general.no-database");
            return;
        }
        if (coins(player.getUniqueId()) < price) {
            messages.send(player, "kits.not-enough", Messages.arg("kit", kit.display()), Messages.arg("price", price),
                    Messages.arg("coins", coins(player.getUniqueId())));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        UUID id = player.getUniqueId();
        String name = player.getName();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int left;
            try {
                left = plugin.database().buyKit(id, name, kit.id(), price);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Couldn't buy a kit", e);
                left = -2;
            }
            int result = left;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (result < 0) {
                    messages.send(player, result == -1 ? "kits.not-enough" : "general.no-database", Messages.arg("kit", kit.display()),
                            Messages.arg("price", price), Messages.arg("coins", coins(id)));
                    return;
                }
                PlayerKits data = data(id);
                data.owned.add(kit);
                data.coins = result;
                messages.send(player, "kits.bought", Messages.arg("kit", kit.display()), Messages.arg("price", price), Messages.arg("coins", result));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                choose(player, kit);
            });
        });
    }

    // ---------------------------------------------------------------- layouts

    public Map<String, Integer> layout(UUID player, Kit kit) {
        PlayerKits data = players.get(player);
        Map<String, Integer> layout = data == null ? null : data.layouts.get(kit);
        return layout == null ? Map.of() : layout;
    }

    void saveLayout(Player player, Kit kit, Map<String, Integer> layout) {
        UUID id = player.getUniqueId();
        if (layout == null) data(id).layouts.remove(kit);
        else data(id).layouts.put(kit, layout);
        String encoded = layout == null ? null : encode(layout);
        async(() -> plugin.database().saveLayout(id, kit.id(), encoded));
    }

    static String encode(Map<String, Integer> layout) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : layout.entrySet()) parts.add(entry.getKey() + ":" + entry.getValue());
        return String.join(",", parts);
    }

    static Map<String, Integer> decode(String text) {
        Map<String, Integer> layout = new LinkedHashMap<>();
        if (text == null || text.isBlank()) return layout;
        for (String part : text.split(",")) {
            String[] pair = part.split(":", 2);
            if (pair.length != 2) continue;
            try {
                int slot = Integer.parseInt(pair[1]);
                if (slot >= 0 && slot < 36) layout.put(pair[0], slot);
            } catch (NumberFormatException ignored) {
                // skip it
            }
        }
        return layout;
    }

    /** Where each kit item goes: the player's layout where it fits, the default otherwise. */
    public static Map<KitSlot, ItemStack> arrange(Kit kit, TeamColor team, Map<String, Integer> layout) {
        Map<Integer, ItemStack> bySlot = new HashMap<>();
        List<Kit.KitItem> leftOver = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        for (Kit.KitItem item : kit.items(team)) {
            Integer wanted = layout.get(item.key());
            int slot = wanted != null ? wanted : item.slot();
            if (used.contains(slot)) {
                leftOver.add(item);
                continue;
            }
            used.add(slot);
            bySlot.put(slot, Items.layoutKey(item.item().clone(), item.key()));
        }
        for (Kit.KitItem item : leftOver) {
            for (int slot = 0; slot < 36; slot++) {
                if (used.add(slot)) {
                    bySlot.put(slot, Items.layoutKey(item.item().clone(), item.key()));
                    break;
                }
            }
        }
        Map<KitSlot, ItemStack> result = new HashMap<>();
        for (Map.Entry<Integer, ItemStack> entry : bySlot.entrySet()) result.put(new KitSlot(entry.getKey()), entry.getValue());
        return result;
    }

    /** A player inventory slot (0-8 hotbar, 9-35 the rest). */
    public record KitSlot(int slot) {}

    // ---------------------------------------------------------------- equipping

    /** Clears the player and gives them their kit, laid out their way, in their team's colours. */
    public void equip(Player player, TeamColor team) {
        Kit kit = of(player.getUniqueId());
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        for (PotionEffect effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
        player.setFireTicks(0);
        var max = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(max == null ? 20 : max.getValue());
        player.setFoodLevel(20);
        // No saturation: health comes back at the normal pace, not the fast "full saturation" one.
        player.setSaturation(0);
        Kit.Armour armour = kit.armour(team);
        inventory.setHelmet(armour.helmet());
        inventory.setChestplate(armour.chestplate());
        inventory.setLeggings(armour.leggings());
        inventory.setBoots(armour.boots());
        if (kit == Kit.SHIELDER) inventory.setItemInOffHand(Kit.plain(Material.SHIELD));
        for (Map.Entry<KitSlot, ItemStack> entry : arrange(kit, team, layout(player.getUniqueId(), kit)).entrySet()) {
            inventory.setItem(entry.getKey().slot(), entry.getValue());
        }
        Bukkit.getScheduler().runTask(plugin, player::updateInventory);
    }

    // ---------------------------------------------------------------- menus

    public void openMenu(Player player) {
        Messages messages = plugin.messages();
        UUID id = player.getUniqueId();
        Menu menu = new Menu(5, messages.component("kits.menu-title"));
        menu.set(4, Items.named(Material.SUNFLOWER, messages.component("kits.coins-name", Messages.arg("coins", coins(id))),
                messages.lines("kits.coins-lore")), null);
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        Kit current = of(id);
        Kit[] kits = Kit.values();
        for (int i = 0; i < kits.length && i < slots.length; i++) {
            Kit kit = kits[i];
            menu.set(slots[i], icon(player, kit, kit == current), clicker -> openKit(clicker, kit));
        }
        menu.fill().open(player);
    }

    private ItemStack icon(Player player, Kit kit, boolean selected) {
        Messages messages = plugin.messages();
        boolean owned = owns(player.getUniqueId(), kit);
        int price = plugin.ctfConfig().price(kit);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(kit.description(), NamedTextColor.GRAY));
        lore.add(Component.empty());
        for (String line : kit.contents()) lore.add(Component.text("▪ ", NamedTextColor.DARK_GRAY).append(Component.text(line, NamedTextColor.WHITE)));
        lore.add(Component.empty());
        if (selected) lore.add(messages.component("kits.selected"));
        else if (owned) lore.add(messages.component("kits.owned"));
        else lore.add(messages.component("kits.price", Messages.arg("price", price)));
        NamedTextColor color = selected ? NamedTextColor.GREEN : owned ? NamedTextColor.GOLD : NamedTextColor.RED;
        ItemStack icon = Items.named(kit.icon(), Component.text(kit.display(), color), lore);
        if (selected) icon.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        return icon;
    }

    /** One kit: select it, buy it, or edit its layout. */
    public void openKit(Player player, Kit kit) {
        Messages messages = plugin.messages();
        UUID id = player.getUniqueId();
        boolean owned = owns(id, kit);
        int price = plugin.ctfConfig().price(kit);
        Menu menu = new Menu(3, messages.component("kits.detail-title", Messages.arg("kit", kit.display())));
        menu.set(4, icon(player, kit, kit == of(id)), null);
        if (owned) {
            menu.set(11, Items.named(Material.LIME_DYE, messages.component("kits.select"), messages.lines("kits.select-lore")), clicker -> {
                choose(clicker, kit);
                clicker.closeInventory();
            });
        } else {
            boolean afford = coins(id) >= price;
            menu.set(11, Items.named(afford ? Material.GOLD_INGOT : Material.BARRIER,
                    messages.component(afford ? "kits.buy" : "kits.cant-afford", Messages.arg("price", price), Messages.arg("coins", coins(id))),
                    messages.lines("kits.buy-lore", Messages.arg("coins", coins(id)))), clicker -> {
                buy(clicker, kit);
                clicker.closeInventory();
            });
        }
        if (owned && plugin.ctfConfig().hub()) {
            menu.set(13, Items.named(Material.CRAFTING_TABLE, messages.component("kits.edit"), messages.lines("kits.edit-lore")),
                    clicker -> editor.open(clicker, kit));
        }
        menu.set(15, Items.named(Material.ARROW, messages.component("kits.back"), List.of()), this::openMenu);
        menu.fill().open(player);
    }

    private void async(SqlTask task) {
        if (plugin.database() == null) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                task.run();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Couldn't save kit data", e);
            }
        });
    }

    private interface SqlTask {
        void run() throws SQLException;
    }
}
