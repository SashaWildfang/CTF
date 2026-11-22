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
        volatile boolean forcedDefault;
    }

    private final FractalCTF plugin;
    private final Map<UUID, PlayerKits> players = new ConcurrentHashMap<>();
    private final KitEditor editor;
    /** Kits an admin switched off (shared through the database): nobody can pick or use them. */
    private volatile Set<Kit> disabled = Set.of();

    public Kits(FractalCTF plugin) {
        this.plugin = plugin;
        this.editor = new KitEditor(plugin, this);
        Bukkit.getPluginManager().registerEvents(editor, plugin);
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::readDisabled, 20L, 200L);
    }

    private void readDisabled() {
        if (plugin.database() == null) return;
        try {
            disabled = parseList(plugin.database().setting("disabled-kits"));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.FINE, "Couldn't read the disabled kits", e);
        }
    }

    static Set<Kit> parseList(String text) {
        Set<Kit> kits = java.util.EnumSet.noneOf(Kit.class);
        if (text != null) for (String id : text.split(",")) Kit.parse(id.trim()).ifPresent(kits::add);
        return kits;
    }

    public boolean disabled(Kit kit) {
        return disabled.contains(kit) && kit != defaultKit();
    }

    /** /ctf kit disable|enable: saved for both servers. Returns false without a database. */
    public boolean setDisabled(Kit kit, boolean off) {
        if (plugin.database() == null) return false;
        Set<Kit> next = java.util.EnumSet.noneOf(Kit.class);
        next.addAll(disabled);
        if (off) next.add(kit);
        else next.remove(kit);
        disabled = next;
        List<String> ids = new ArrayList<>();
        for (Kit k : next) ids.add(k.id());
        String value = ids.isEmpty() ? null : String.join(",", ids);
        async(() -> plugin.database().setting("disabled-kits", value));
        return true;
    }

    /** /ctf kit grant|remove: gives or takes a kit, also in the cache if they're on this server. */
    public void setOwned(UUID player, Kit kit, boolean owns) {
        PlayerKits data = players.get(player);
        if (data != null) {
            if (owns) data.owned.add(kit);
            else data.owned.remove(kit);
        }
        async(() -> plugin.database().setOwned(player, kit.id(), owns));
    }

    /** Late joiners play the match with the default kit. */
    public void forceDefault(UUID player) {
        data(player).forcedDefault = true;
    }

    private PlayerKits data(UUID player) {
        return players.computeIfAbsent(player, id -> new PlayerKits());
    }

    public Kit defaultKit() {
        return Kit.parse(plugin.ctfConfig().defaultKit()).orElse(Kit.BARBARIAN);
    }

    public Kit of(UUID player) {
        PlayerKits data = players.get(player);
        Kit kit = data == null || data.forcedDefault ? null : data.chosen;
        return kit == null || !owns(player, kit) || disabled(kit) ? defaultKit() : kit;
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
        if (disabled(kit)) {
            plugin.messages().send(player, "kits.disabled", Messages.arg("kit", kit.display()));
            return;
        }
        if (!owns(player.getUniqueId(), kit)) {
            plugin.messages().send(player, "kits.locked", Messages.arg("kit", kit.display()), Messages.arg("price", plugin.ctfConfig().price(kit)));
            return;
        }
        data(player.getUniqueId()).chosen = kit;
        data(player.getUniqueId()).forcedDefault = false;
        UUID id = player.getUniqueId();
        async(() -> plugin.database().saveKit(id, kit.id()));
        plugin.messages().send(player, plugin.ctfConfig().hub() ? "kits.chosen-hub" : "kits.chosen-game", Messages.arg("kit", kit.display()));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
        if (plugin.hub() != null) {
            plugin.hub().board().update(player);
            plugin.hub().npcs().refresh(player);
        }
    }

    public void buy(Player player, Kit kit) {
        Messages messages = plugin.messages();
        int price = plugin.ctfConfig().price(kit);
        if (owns(player.getUniqueId(), kit)) {
            choose(player, kit);
            return;
        }
        if (disabled(kit)) {
            messages.send(player, "kits.disabled", Messages.arg("kit", kit.display()));
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
        // 1.8-style combat: swing as fast as you can click
        var speed = player.getAttribute(Attribute.ATTACK_SPEED);
        if (speed != null) speed.setBaseValue(plugin.ctfConfig().legacyCombat() ? 1024 : 4);
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

    /** How each player last filtered and sorted the kit menu. */
    private final Map<UUID, int[]> menuView = new ConcurrentHashMap<>();
    private static final String[] FILTERS = {"all", "unlocked", "locked", "affordable"};
    private static final String[] SORTS = {"default", "price-low", "price-high", "name"};

    /** The kit menu: left-click equips (or buys), right-click previews, shift-click edits the layout. */
    public void openMenu(Player player) {
        Messages messages = plugin.messages();
        UUID id = player.getUniqueId();
        int[] view = menuView.computeIfAbsent(id, k -> new int[2]);
        Menu menu = new Menu(6, messages.component("kits.menu-title"));
        Kit current = of(id);
        menu.set(4, Items.named(Material.SUNFLOWER, messages.component("kits.coins-name", Messages.arg("coins", coins(id))),
                messages.lines("kits.coins-lore")), null);
        ItemStack chosen = Items.named(current.icon(), messages.component("kits.current-name", Messages.arg("kit", current.display())),
                messages.lines("kits.current-lore", Messages.arg("role", current.role())));
        chosen.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        menu.set(0, chosen, null);
        List<Kit> kits = new ArrayList<>();
        for (Kit kit : Kit.values()) {
            boolean owned = owns(id, kit);
            boolean keep = switch (FILTERS[view[0]]) {
                case "unlocked" -> owned;
                case "locked" -> !owned;
                case "affordable" -> !owned && coins(id) >= plugin.ctfConfig().price(kit);
                default -> true;
            };
            if (keep) kits.add(kit);
        }
        switch (SORTS[view[1]]) {
            case "price-low" -> kits.sort(java.util.Comparator.comparingInt(k -> plugin.ctfConfig().price(k)));
            case "price-high" -> kits.sort(java.util.Comparator.comparingInt((Kit k) -> plugin.ctfConfig().price(k)).reversed());
            case "name" -> kits.sort(java.util.Comparator.comparing(Kit::display));
            default -> { }
        }
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
        for (int i = 0; i < kits.size() && i < slots.length; i++) {
            Kit kit = kits.get(i);
            menu.setClick(slots[i], icon(player, kit, kit == current), (clicker, click) -> clickKit(clicker, kit, click));
        }
        if (kits.isEmpty()) menu.set(22, Items.named(Material.BARRIER, messages.component("kits.filter-empty"), List.of()), null);
        // filter (left-click) and sort (right-click)
        List<Component> lore = new ArrayList<>();
        for (int f = 0; f < FILTERS.length; f++) {
            lore.add(messages.component(f == view[0] ? "kits.option-on" : "kits.option-off",
                    Messages.comp("option", messages.component("kits.filter." + FILTERS[f]))));
        }
        lore.add(Component.empty());
        lore.add(messages.component("kits.filter-click"));
        menu.set(48, Items.named(Material.HOPPER, messages.component("kits.filter-name"), lore), clicker -> {
            view[0] = (view[0] + 1) % FILTERS.length;
            openMenu(clicker);
            clicker.playSound(clicker.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.6f);
        });
        List<Component> sortLore = new ArrayList<>();
        for (int o = 0; o < SORTS.length; o++) {
            sortLore.add(messages.component(o == view[1] ? "kits.option-on" : "kits.option-off",
                    Messages.comp("option", messages.component("kits.sort." + SORTS[o]))));
        }
        sortLore.add(Component.empty());
        sortLore.add(messages.component("kits.sort-click"));
        menu.set(50, Items.named(Material.COMPARATOR, messages.component("kits.sort-name"), sortLore), clicker -> {
            view[1] = (view[1] + 1) % SORTS.length;
            openMenu(clicker);
            clicker.playSound(clicker.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.6f);
        });
        menu.set(49, Items.named(Material.BOOK, messages.component("kits.help-name"), messages.lines("kits.help-lore")), null);
        menu.set(53, Items.named(Material.BARRIER, messages.component("kits.close"), List.of()), Player::closeInventory);
        menu.fill().open(player);
    }

    private void clickKit(Player player, Kit kit, org.bukkit.event.inventory.ClickType click) {
        boolean owned = owns(player.getUniqueId(), kit);
        if (click.isRightClick() && !click.isShiftClick()) {
            preview(player, kit);
        } else if (click.isShiftClick() && owned && plugin.ctfConfig().hub()) {
            editor.open(player, kit);
        } else if (owned) {
            choose(player, kit);
            openMenu(player);
        } else {
            confirmBuy(player, kit);
        }
    }

    /** A kit's icon in the menu: what it is, what it has, and what clicking does. */
    private ItemStack icon(Player player, Kit kit, boolean selected) {
        Messages messages = plugin.messages();
        UUID id = player.getUniqueId();
        boolean owned = owns(id, kit);
        int price = plugin.ctfConfig().price(kit);
        List<Component> lore = new ArrayList<>();
        lore.add(messages.component("kits.lore.role", Messages.arg("role", kit.role())));
        lore.add(messages.component("kits.lore.blurb", Messages.arg("blurb", kit.blurb())));
        lore.add(Component.empty());
        if (kit.ability() != Kit.Ability.NONE) {
            lore.add(messages.component(kit.ability().cooldownSeconds() > 0 ? "kits.lore.ability" : "kits.lore.ability-no-cooldown",
                    Messages.arg("ability", kit.ability().display()), Messages.arg("cooldown", kit.ability().cooldownSeconds())));
            lore.add(messages.component("kits.lore.ability-help", Messages.arg("help", kit.ability().help())));
        } else {
            lore.add(messages.component("kits.lore.perk", Messages.arg("perk", kit.description())));
        }
        lore.add(Component.empty());
        lore.add(messages.component("kits.lore.armour-title"));
        List<String> armour = kit.armourNames();
        if (armour.isEmpty()) lore.add(messages.component("kits.lore.none"));
        for (String piece : armour) lore.add(messages.component("kits.lore.entry", Messages.arg("item", piece)));
        lore.add(Component.empty());
        lore.add(messages.component("kits.lore.items-title"));
        for (String item : kit.itemNames()) lore.add(messages.component("kits.lore.entry", Messages.arg("item", item)));
        lore.add(Component.empty());
        if (disabled(kit)) lore.add(messages.component("kits.disabled-lore"));
        else if (selected) lore.add(messages.component("kits.selected"));
        else if (owned) lore.add(messages.component("kits.owned"));
        else if (coins(id) >= price) lore.add(messages.component("kits.price", Messages.arg("price", price)));
        else lore.add(messages.component("kits.price-short", Messages.arg("price", price), Messages.arg("missing", price - coins(id))));
        lore.add(Component.empty());
        lore.add(messages.component(owned ? (selected ? "kits.click.selected" : "kits.click.equip") : "kits.click.buy"));
        lore.add(messages.component("kits.click.preview"));
        if (owned && plugin.ctfConfig().hub()) lore.add(messages.component("kits.click.edit"));
        NamedTextColor color = disabled(kit) ? NamedTextColor.DARK_GRAY : selected ? NamedTextColor.GREEN : owned ? NamedTextColor.YELLOW : NamedTextColor.RED;
        ItemStack icon = Items.named(disabled(kit) ? Material.BARRIER : kit.icon(),
                Component.text(kit.display(), color).decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true), lore);
        if (selected) icon.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        return icon;
    }

    /** Buying a kit asks once, so nobody spends coins by accident. */
    public void confirmBuy(Player player, Kit kit) {
        Messages messages = plugin.messages();
        UUID id = player.getUniqueId();
        int price = plugin.ctfConfig().price(kit);
        boolean afford = coins(id) >= price;
        Menu menu = new Menu(3, messages.component("kits.buy-title", Messages.arg("kit", kit.display())));
        menu.set(13, icon(player, kit, false), clicker -> preview(clicker, kit));
        menu.set(11, Items.named(afford ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE,
                messages.component(afford ? "kits.buy" : "kits.cant-afford", Messages.arg("price", price), Messages.arg("coins", coins(id))),
                messages.lines("kits.buy-lore", Messages.arg("coins", coins(id)), Messages.arg("price", price))), clicker -> {
            if (afford) buy(clicker, kit);
            else clicker.playSound(clicker.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            openMenu(clicker);
        });
        menu.set(15, Items.named(Material.RED_CONCRETE, messages.component("kits.cancel"), List.of()), this::openMenu);
        menu.fill().open(player);
    }

    /** Kept for the layout editor's Back button. */
    public void openKit(Player player, Kit kit) {
        openMenu(player);
    }

    /**
     * What a kit comes with, laid out like the inventory: the kit and its ability on top, the armour and
     * off-hand, then the hotbar and the rest of the items in the slots they spawn in.
     */
    public void preview(Player player, Kit kit) {
        Messages messages = plugin.messages();
        UUID id = player.getUniqueId();
        Menu menu = new Menu(6, messages.component("kits.preview-title", Messages.arg("kit", kit.display())));
        TeamColor shown = TeamColor.RED;
        menu.set(4, icon(player, kit, kit == of(id)), null);
        Kit.Armour armour = kit.armour(shown);
        ItemStack[] pieces = {armour.helmet(), armour.chestplate(), armour.leggings(), armour.boots()};
        String[] slotNames = {"helmet", "chestplate", "leggings", "boots"};
        for (int i = 0; i < 4; i++) {
            ItemStack piece = pieces[i] != null ? pieces[i].clone()
                    : Items.named(Material.LIGHT_GRAY_STAINED_GLASS_PANE, messages.component("kits.preview-empty." + slotNames[i]), List.of());
            menu.set(10 + i, piece, null);
        }
        menu.set(15, kit == Kit.SHIELDER ? Kit.plain(Material.SHIELD)
                : Items.named(Material.LIGHT_GRAY_STAINED_GLASS_PANE, messages.component("kits.preview-empty.offhand"), List.of()), null);
        if (kit.ability() != Kit.Ability.NONE) menu.set(16, kit.ability().item(), null);
        else menu.set(16, Items.named(Material.LIGHT_GRAY_STAINED_GLASS_PANE, messages.component("kits.preview-empty.ability"), List.of()), null);
        // labels
        ItemStack hotbarLabel = Items.named(Material.YELLOW_STAINED_GLASS_PANE, messages.component("kits.preview-hotbar"), List.of());
        ItemStack bagLabel = Items.named(Material.ORANGE_STAINED_GLASS_PANE, messages.component("kits.preview-inventory"), List.of());
        for (int i = 18; i < 27; i++) menu.set(i, hotbarLabel, null);
        // hotbar (slots 0-8) on row 3, the first inventory row (9-17) on row 4, in this player's layout
        for (Map.Entry<KitSlot, ItemStack> entry : arrange(kit, shown, layout(id, kit)).entrySet()) {
            int slot = entry.getKey().slot();
            if (slot < 9) menu.set(27 + slot, entry.getValue(), null);
            else if (slot < 18) menu.set(36 + slot - 9, entry.getValue(), null);
        }
        for (int i = 36; i < 45; i++) if (menu.getInventory().getItem(i) == null) menu.set(i, bagLabel, null);
        menu.set(45, Items.named(Material.ARROW, messages.component("kits.back"), List.of()), this::openMenu);
        boolean owned = owns(id, kit);
        if (owned) {
            menu.set(49, Items.named(Material.LIME_CONCRETE, messages.component(kit == of(id) ? "kits.equipped" : "kits.select"),
                    messages.lines("kits.select-lore")), clicker -> {
                choose(clicker, kit);
                openMenu(clicker);
            });
        } else {
            menu.set(49, Items.named(Material.GOLD_INGOT, messages.component("kits.buy", Messages.arg("price", plugin.ctfConfig().price(kit))),
                    messages.lines("kits.buy-lore", Messages.arg("coins", coins(id)), Messages.arg("price", plugin.ctfConfig().price(kit)))),
                    clicker -> confirmBuy(clicker, kit));
        }
        if (owned && plugin.ctfConfig().hub()) {
            menu.set(51, Items.named(Material.CRAFTING_TABLE, messages.component("kits.edit"), messages.lines("kits.edit-lore")),
                    clicker -> editor.open(clicker, kit));
        }
        menu.set(53, Items.named(Material.BARRIER, messages.component("kits.close"), List.of()), Player::closeInventory);
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
