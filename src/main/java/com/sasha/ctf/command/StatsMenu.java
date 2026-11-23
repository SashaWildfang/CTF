/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.command;

import com.sasha.ctf.FractalCTF;
import com.sasha.ctf.config.Messages;
import com.sasha.ctf.data.Database;
import com.sasha.ctf.data.PlayerStats;
import com.sasha.ctf.model.Mode;
import com.sasha.ctf.util.Items;
import com.sasha.ctf.util.Menu;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The stats menu: a player's overall numbers, then the same numbers for each map or each mode (sortable),
 * and the leaderboards.
 */
public final class StatsMenu {

    private static final String[] MAP_SORTS = {"games", "wins", "kills", "kdr", "name"};
    private static final String[] BOARDS = {"kills", "wins", "captures", "kdr", "wools", "best_streak", "longest_shot", "hill_seconds"};

    private final FractalCTF plugin;
    /** Each viewer's map-list sort and leaderboard choice. */
    private final Map<UUID, int[]> view = new java.util.concurrent.ConcurrentHashMap<>();

    public StatsMenu(FractalCTF plugin) {
        this.plugin = plugin;
    }

    /** Everything known about one player, loaded once per menu visit. */
    private record Loaded(UUID id, String name, PlayerStats totals, Map<String, Map<Mode, PlayerStats>> maps, PlayerStats detail) {}

    public void open(Player viewer, OfflinePlayer target) {
        load(viewer, target, data -> overview(viewer, data));
    }

    /** Straight to one leaderboard (a Top 10 board in the hub was clicked). */
    public void openLeaderboard(Player viewer, String what) {
        view.computeIfAbsent(viewer.getUniqueId(), k -> new int[2])[1] = Math.max(0, java.util.Arrays.asList(BOARDS).indexOf(what));
        load(viewer, viewer, data -> leaderboard(viewer, data));
    }

    private void load(Player viewer, OfflinePlayer target, java.util.function.Consumer<Loaded> then) {
        if (plugin.database() == null) {
            plugin.messages().send(viewer, "general.no-database");
            return;
        }
        UUID id = target.getUniqueId();
        String name = target.getName() == null ? "?" : target.getName();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                PlayerStats totals = plugin.database().readStats(id);
                Map<String, Map<Mode, PlayerStats>> maps = plugin.database().readMapStats(id);
                PlayerStats detail = new PlayerStats();
                for (Map<Mode, PlayerStats> modes : maps.values()) for (PlayerStats one : modes.values()) detail.add(one);
                Loaded data = new Loaded(id, name, totals, maps, detail);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (viewer.isOnline()) then.accept(data);
                });
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Couldn't read stats", e);
                Bukkit.getScheduler().runTask(plugin, () -> plugin.messages().send(viewer, "general.no-database"));
            }
        });
    }

    // ---------------------------------------------------------------- overview

    private void overview(Player viewer, Loaded data) {
        Messages m = plugin.messages();
        PlayerStats t = data.totals(), d = data.detail();
        Menu menu = new Menu(6, m.component("stats.title", Messages.arg("player", data.name())));
        ItemStack head = Items.named(Material.PLAYER_HEAD, m.component("stats.head-name", Messages.arg("player", data.name())),
                m.lines("stats.head-lore", Messages.arg("games", t.games), Messages.arg("wins", t.wins),
                        Messages.arg("rate", pct(t.winRate())), Messages.arg("coins", t.coins)));
        head.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(Bukkit.getOfflinePlayer(data.id())));
        menu.set(4, head, null);
        menu.set(19, Items.named(Material.IRON_SWORD, m.component("stats.combat"), m.lines("stats.combat-lore",
                Messages.arg("kills", t.kills), Messages.arg("deaths", t.deaths), Messages.arg("kdr", two(t.kdr())),
                Messages.arg("assists", t.assists), Messages.arg("bow", d.bowKills), Messages.arg("void", d.voidKills),
                Messages.arg("streak", d.bestStreak), Messages.arg("shot", d.longestShot))), null);
        menu.set(21, Items.named(Material.RED_BANNER, m.component("stats.objectives"), m.lines("stats.objectives-lore",
                Messages.arg("objectives", t.captures), Messages.arg("flags", d.captures), Messages.arg("pickups", d.flagPickups),
                Messages.arg("wools", d.wools), Messages.arg("hill", time(d.hillSeconds)))), null);
        menu.set(23, Items.named(Material.CLOCK, m.component("stats.matches"), m.lines("stats.matches-lore",
                Messages.arg("games", t.games), Messages.arg("wins", t.wins), Messages.arg("losses", t.losses),
                Messages.arg("rate", pct(t.winRate())), Messages.arg("played", time(d.playSeconds)))), null);
        menu.set(25, Items.named(Material.SUNFLOWER, m.component("stats.coins"), m.lines("stats.coins-lore",
                Messages.arg("coins", t.coins), Messages.arg("earned", d.coinsEarned))), null);
        // favourite map and mode
        String favourite = null;
        int most = 0;
        Map<Mode, PlayerStats> byMode = byMode(data);
        for (Map.Entry<String, Map<Mode, PlayerStats>> entry : data.maps().entrySet()) {
            int games = 0;
            for (PlayerStats one : entry.getValue().values()) games += one.games;
            if (games > most) {
                most = games;
                favourite = entry.getKey();
            }
        }
        Mode favouriteMode = null;
        int modeGames = 0;
        for (Map.Entry<Mode, PlayerStats> entry : byMode.entrySet()) {
            if (entry.getValue().games > modeGames) {
                modeGames = entry.getValue().games;
                favouriteMode = entry.getKey();
            }
        }
        menu.set(31, Items.named(Material.NETHER_STAR, m.component("stats.favourites"), m.lines("stats.favourites-lore",
                Messages.arg("map", favourite == null ? "-" : mapName(favourite)), Messages.arg("map-games", most),
                Messages.arg("mode", favouriteMode == null ? "-" : favouriteMode.display()), Messages.arg("mode-games", modeGames))), null);
        menu.set(38, Items.named(Material.FILLED_MAP, m.component("stats.by-map"), m.lines("stats.by-map-lore")),
                clicker -> byMaps(clicker, data));
        menu.set(40, Items.named(Material.COMPASS, m.component("stats.by-mode"), m.lines("stats.by-mode-lore")),
                clicker -> byModes(clicker, data));
        menu.set(42, Items.named(Material.GOLD_BLOCK, m.component("stats.leaderboards"), m.lines("stats.leaderboards-lore")),
                clicker -> leaderboard(clicker, data));
        menu.set(49, Items.named(Material.BARRIER, m.component("kits.close"), List.of()), Player::closeInventory);
        menu.fill().open(viewer);
    }

    private Map<Mode, PlayerStats> byMode(Loaded data) {
        Map<Mode, PlayerStats> byMode = new EnumMap<>(Mode.class);
        for (Map<Mode, PlayerStats> modes : data.maps().values()) {
            for (Map.Entry<Mode, PlayerStats> entry : modes.entrySet()) byMode.computeIfAbsent(entry.getKey(), k -> new PlayerStats()).add(entry.getValue());
        }
        return byMode;
    }

    private List<Component> detailLore(PlayerStats s) {
        return plugin.messages().lines("stats.detail-lore", Messages.arg("games", s.games), Messages.arg("wins", s.wins),
                Messages.arg("losses", s.losses), Messages.arg("rate", pct(s.winRate())), Messages.arg("kills", s.kills),
                Messages.arg("deaths", s.deaths), Messages.arg("kdr", two(s.kdr())), Messages.arg("assists", s.assists),
                Messages.arg("objectives", s.captures + s.wools), Messages.arg("streak", s.bestStreak),
                Messages.arg("shot", s.longestShot), Messages.arg("played", time(s.playSeconds)));
    }

    // ---------------------------------------------------------------- by map

    private void byMaps(Player viewer, Loaded data) {
        Messages m = plugin.messages();
        int[] state = view.computeIfAbsent(viewer.getUniqueId(), k -> new int[2]);
        Map<String, PlayerStats> perMap = new LinkedHashMap<>();
        for (Map.Entry<String, Map<Mode, PlayerStats>> entry : data.maps().entrySet()) {
            PlayerStats sum = new PlayerStats();
            for (PlayerStats one : entry.getValue().values()) sum.add(one);
            perMap.put(entry.getKey(), sum);
        }
        List<Map.Entry<String, PlayerStats>> rows = new ArrayList<>(perMap.entrySet());
        Comparator<Map.Entry<String, PlayerStats>> order = switch (MAP_SORTS[state[0]]) {
            case "wins" -> Comparator.comparingInt((Map.Entry<String, PlayerStats> e) -> e.getValue().wins).reversed();
            case "kills" -> Comparator.comparingInt((Map.Entry<String, PlayerStats> e) -> e.getValue().kills).reversed();
            case "kdr" -> Comparator.comparingDouble((Map.Entry<String, PlayerStats> e) -> e.getValue().kdr()).reversed();
            case "name" -> Comparator.comparing(e -> mapName(e.getKey()));
            default -> Comparator.comparingInt((Map.Entry<String, PlayerStats> e) -> e.getValue().games).reversed();
        };
        rows.sort(order);
        Menu menu = new Menu(6, m.component("stats.by-map-title", Messages.arg("player", data.name())));
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        for (int i = 0; i < rows.size() && i < slots.length; i++) {
            String id = rows.get(i).getKey();
            menu.set(slots[i], Items.named(mapIcon(id), m.component("stats.map-name", Messages.arg("place", i + 1), Messages.arg("map", mapName(id))),
                    detailLore(rows.get(i).getValue())), null);
        }
        if (rows.isEmpty()) menu.set(22, Items.named(Material.PAPER, m.component("stats.none-yet"), List.of()), null);
        menu.set(49, sortButton(state[0]), clicker -> {
            state[0] = (state[0] + 1) % MAP_SORTS.length;
            clicker.playSound(clicker.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.6f);
            byMaps(clicker, data);
        });
        menu.set(45, Items.named(Material.ARROW, m.component("kits.back"), List.of()), clicker -> overview(clicker, data));
        menu.set(53, Items.named(Material.BARRIER, m.component("kits.close"), List.of()), Player::closeInventory);
        menu.fill().open(viewer);
    }

    private ItemStack sortButton(int current) {
        Messages m = plugin.messages();
        List<Component> lore = new ArrayList<>();
        for (int i = 0; i < MAP_SORTS.length; i++) {
            lore.add(m.component(i == current ? "kits.option-on" : "kits.option-off", Messages.comp("option", m.component("stats.sort." + MAP_SORTS[i]))));
        }
        lore.add(Component.empty());
        lore.add(m.component("kits.sort-click"));
        return Items.named(Material.COMPARATOR, m.component("kits.sort-name"), lore);
    }

    // ---------------------------------------------------------------- by mode

    private void byModes(Player viewer, Loaded data) {
        Messages m = plugin.messages();
        Map<Mode, PlayerStats> byMode = byMode(data);
        Menu menu = new Menu(4, m.component("stats.by-mode-title", Messages.arg("player", data.name())));
        int[] slots = {10, 12, 14, 16};
        Mode[] modes = Mode.values();
        for (int i = 0; i < modes.length && i < slots.length; i++) {
            Mode mode = modes[i];
            PlayerStats s = byMode.getOrDefault(mode, new PlayerStats());
            List<Component> lore = new ArrayList<>(detailLore(s));
            if (mode == Mode.KOTH) lore.add(m.component("stats.hill-line", Messages.arg("hill", time(s.hillSeconds))));
            if (mode == Mode.DTW) lore.add(m.component("stats.wool-line", Messages.arg("wools", s.wools)));
            if (mode.flags()) lore.add(m.component("stats.flag-line", Messages.arg("flags", s.captures), Messages.arg("pickups", s.flagPickups)));
            menu.set(slots[i], Items.named(switch (mode) {
                case CTF -> Material.RED_BANNER;
                case CTF4 -> Material.LIME_BANNER;
                case KOTH -> Material.GOLDEN_HELMET;
                case DTW -> Material.WHITE_WOOL;
            }, m.component("stats.mode-name", Messages.arg("mode", mode.display())), lore), null);
        }
        menu.set(27, Items.named(Material.ARROW, m.component("kits.back"), List.of()), clicker -> overview(clicker, data));
        menu.set(35, Items.named(Material.BARRIER, m.component("kits.close"), List.of()), Player::closeInventory);
        menu.fill().open(viewer);
    }

    // ---------------------------------------------------------------- leaderboards

    private void leaderboard(Player viewer, Loaded data) {
        int[] state = view.computeIfAbsent(viewer.getUniqueId(), k -> new int[2]);
        String what = BOARDS[state[1]];
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Database.Ranked> top;
            try {
                top = plugin.database().leaderboard(what, 10);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Couldn't read a leaderboard", e);
                top = List.of();
            }
            List<Database.Ranked> result = top;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (viewer.isOnline()) showLeaderboard(viewer, data, what, result);
            });
        });
    }

    private void showLeaderboard(Player viewer, Loaded data, String what, List<Database.Ranked> top) {
        Messages m = plugin.messages();
        int[] state = view.computeIfAbsent(viewer.getUniqueId(), k -> new int[2]);
        Menu menu = new Menu(6, m.component("stats.board-title", Messages.comp("stat", m.component("stats.board." + what))));
        Material[] icons = {Material.DIAMOND_SWORD, Material.GOLDEN_HELMET, Material.RED_BANNER, Material.IRON_SWORD,
                Material.WHITE_WOOL, Material.BLAZE_POWDER, Material.BOW, Material.BEACON};
        for (int i = 0; i < BOARDS.length; i++) {
            int index = i;
            ItemStack icon = Items.named(icons[i], m.component(i == state[1] ? "stats.board-on" : "stats.board-off",
                    Messages.comp("stat", m.component("stats.board." + BOARDS[i]))), List.of());
            if (i == state[1]) icon.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
            menu.set(i < 4 ? i : i + 1, icon, clicker -> {
                state[1] = index;
                clicker.playSound(clicker.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.6f);
                leaderboard(clicker, data);
            });
        }
        int[] slots = {20, 21, 22, 23, 24, 29, 30, 31, 32, 33};
        Material[] medals = {Material.GOLD_BLOCK, Material.IRON_BLOCK, Material.COPPER_BLOCK};
        for (int i = 0; i < top.size() && i < slots.length; i++) {
            Database.Ranked row = top.get(i);
            String value = switch (what) {
                case "kdr" -> two(row.value());
                case "hill_seconds" -> time((int) row.value());
                case "longest_shot" -> (int) row.value() + " blocks";
                default -> String.valueOf((int) row.value());
            };
            ItemStack item;
            if (i < 3) {
                item = Items.named(medals[i], m.component("stats.board-place", Messages.arg("place", i + 1), Messages.arg("player", row.name())),
                        m.lines("stats.board-value", Messages.arg("value", value)));
            } else {
                item = Items.named(Material.PLAYER_HEAD, m.component("stats.board-place", Messages.arg("place", i + 1), Messages.arg("player", row.name())),
                        m.lines("stats.board-value", Messages.arg("value", value)));
                item.editMeta(SkullMeta.class, meta -> meta.setPlayerProfile(Bukkit.createProfile(row.name())));
            }
            item.setAmount(i + 1);
            menu.set(slots[i], item, null);
        }
        if (top.isEmpty()) menu.set(22, Items.named(Material.PAPER, m.component("stats.none-yet"), List.of()), null);
        menu.set(45, Items.named(Material.ARROW, m.component("kits.back"), List.of()), clicker -> overview(clicker, data));
        menu.set(53, Items.named(Material.BARRIER, m.component("kits.close"), List.of()), Player::closeInventory);
        menu.fill().open(viewer);
    }

    // ---------------------------------------------------------------- helpers

    private String mapName(String id) {
        if (plugin.hub() != null) return plugin.hub().mapName(id);
        if (plugin.maps() != null) return plugin.maps().get(id).map(map -> map.name).orElse(id);
        return id;
    }

    private Material mapIcon(String id) {
        String icon = plugin.hub() != null ? plugin.hub().mapIcon(id)
                : plugin.maps() != null ? plugin.maps().get(id).map(map -> map.icon).orElse(null) : null;
        Material material = icon == null ? null : Material.matchMaterial(icon);
        return material == null || !material.isItem() ? Material.FILLED_MAP : material;
    }

    private static String two(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String pct(double value) {
        return String.format(Locale.ROOT, "%.0f%%", value);
    }

    static String time(int seconds) {
        if (seconds < 60) return seconds + "s";
        int minutes = seconds / 60;
        if (minutes < 60) return minutes + "m " + seconds % 60 + "s";
        return minutes / 60 + "h " + minutes % 60 + "m";
    }
}
