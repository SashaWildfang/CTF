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
    /** Leaderboard categories (the top row) and the numbers each can be sorted by (the second row). */
    private static final String[] CATEGORIES = {"kills", "wins", "kdr", "objectives", "best_streak", "longest_shot", "assists", "play_seconds"};
    private static final Map<String, String[]> SUBS = Map.of(
            "kills", new String[]{"kills", "bow_kills", "void_kills"},
            "wins", new String[]{"wins", "games"},
            "objectives", new String[]{"captures", "flag_captures", "flag_pickups", "wools", "hill_seconds"});
    private static final Material[] CATEGORY_ICONS = {Material.DIAMOND_SWORD, Material.GOLDEN_HELMET, Material.IRON_SWORD, Material.RED_BANNER,
            Material.BLAZE_POWDER, Material.BOW, Material.LEAD, Material.CLOCK};
    private static final String[] PLAYER_SORTS = {"kills", "wins", "games", "kdr", "captures", "name"};
    private static final int[] PAGE_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
    private static final int[] BROWSE_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    /** Each viewer's leaderboard: category, sort within it, page. */
    private static final class Board {
        int category;
        int sub;
        int page;
        int sort;
        int browsePage;
    }

    private final Map<UUID, Board> boards = new java.util.concurrent.ConcurrentHashMap<>();

    private final FractalCTF plugin;
    /** Each viewer's map-list sort. */
    private final Map<UUID, int[]> view = new java.util.concurrent.ConcurrentHashMap<>();

    public StatsMenu(FractalCTF plugin) {
        this.plugin = plugin;
    }

    /** Everything known about one player, loaded once per menu visit. */
    private record Loaded(UUID id, String name, PlayerStats totals, Map<String, Map<Mode, PlayerStats>> maps, PlayerStats detail) {}

    public void open(Player viewer, OfflinePlayer target) {
        load(viewer, target.getUniqueId(), target.getName(), data -> overview(viewer, data));
    }

    /** /stats <name>: looks the name up in the stats table first, so it works for anyone who has played, online or not. */
    public void open(Player viewer, String name) {
        if (plugin.database() == null) {
            plugin.messages().send(viewer, "general.no-database");
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map.Entry<UUID, String> found = null;
            try {
                found = plugin.database().playerByName(name);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Couldn't look up a player", e);
            }
            Map.Entry<UUID, String> result = found;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!viewer.isOnline()) return;
                if (result == null) {
                    Player online = Bukkit.getPlayerExact(name);
                    if (online != null) open(viewer, online);
                    else plugin.messages().send(viewer, "stats.unknown-player", Messages.arg("player", name));
                    return;
                }
                load(viewer, result.getKey(), result.getValue(), data -> overview(viewer, data));
            });
        });
    }

    /** Straight to one leaderboard (a Top 10 board in the hub was clicked). */
    public void openLeaderboard(Player viewer, String what) {
        Board board = boards.computeIfAbsent(viewer.getUniqueId(), k -> new Board());
        board.page = 0;
        board.sub = 0;
        for (int c = 0; c < CATEGORIES.length; c++) {
            String[] subs = subs(CATEGORIES[c]);
            for (int i = 0; i < subs.length; i++) {
                if (subs[i].equals(what)) {
                    board.category = c;
                    board.sub = i;
                }
            }
        }
        load(viewer, viewer.getUniqueId(), viewer.getName(), data -> leaderboard(viewer, data));
    }

    private static String[] subs(String category) {
        return SUBS.getOrDefault(category, new String[]{category});
    }

    private void load(Player viewer, UUID id, String knownName, java.util.function.Consumer<Loaded> then) {
        if (plugin.database() == null) {
            plugin.messages().send(viewer, "general.no-database");
            return;
        }
        String name = knownName == null ? "?" : knownName;
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
        menu.set(47, Items.named(Material.PLAYER_HEAD, m.component("stats.browse"), m.lines("stats.browse-lore")),
                clicker -> browse(clicker, data));
        if (!data.id().equals(viewer.getUniqueId())) {
            menu.set(51, Items.named(Material.COMPARATOR, m.component("stats.mine"), m.lines("stats.mine-lore")),
                    clicker -> load(clicker, clicker.getUniqueId(), clicker.getName(), mine -> overview(clicker, mine)));
        }
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
        return Items.named(Material.PAPER, m.component("kits.sort-name"), lore);
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
        Board board = boards.computeIfAbsent(viewer.getUniqueId(), k -> new Board());
        String what = subs(CATEGORIES[board.category])[Math.min(board.sub, subs(CATEGORIES[board.category]).length - 1)];
        int page = board.page;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Database.Place> rows;
            int count;
            int[] mine;
            try {
                rows = plugin.database().places(what, page * PAGE_SLOTS.length, PAGE_SLOTS.length);
                count = plugin.database().placesCount(what);
                mine = plugin.database().rank(what, viewer.getUniqueId());
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Couldn't read a leaderboard", e);
                rows = List.of();
                count = 0;
                mine = new int[]{0, 0};
            }
            List<Database.Place> result = rows;
            int total = count;
            int[] place = mine;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (viewer.isOnline()) showLeaderboard(viewer, data, what, result, total, place);
            });
        });
    }

    private void showLeaderboard(Player viewer, Loaded data, String what, List<Database.Place> rows, int count, int[] mine) {
        Messages m = plugin.messages();
        Board board = boards.computeIfAbsent(viewer.getUniqueId(), k -> new Board());
        int pages = Math.max(1, (count + PAGE_SLOTS.length - 1) / PAGE_SLOTS.length);
        Menu menu = new Menu(6, m.component("stats.board-title", Messages.comp("stat", m.component("stats.board." + what)),
                Messages.arg("page", board.page + 1), Messages.arg("pages", pages)));
        // the categories
        for (int i = 0; i < CATEGORIES.length; i++) {
            int index = i;
            boolean on = i == board.category;
            List<Component> lore = new ArrayList<>();
            if (subs(CATEGORIES[i]).length > 1) lore.add(m.component("stats.board-has-sorts", Messages.arg("count", subs(CATEGORIES[i]).length)));
            lore.add(m.component(on ? "stats.board-showing" : "stats.board-click"));
            ItemStack icon = Items.named(CATEGORY_ICONS[i], m.component(on ? "stats.board-on" : "stats.board-off",
                    Messages.comp("stat", m.component("stats.category." + CATEGORIES[i]))), lore);
            if (on) icon.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
            menu.set(i < 4 ? i : i + 1, icon, clicker -> {
                board.category = index;
                board.sub = 0;
                board.page = 0;
                clicker.playSound(clicker.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.6f);
                leaderboard(clicker, data);
            });
        }
        // what to rank by within the category (objectives: all, flags, pickups, wool, hill time)
        String[] subs = subs(CATEGORIES[board.category]);
        if (subs.length > 1) {
            int start = 13 - (subs.length - 1) / 2;
            for (int i = 0; i < subs.length; i++) {
                int index = i;
                boolean on = i == board.sub;
                String label = i == 0 ? "stats.sort-all." + CATEGORIES[board.category] : "stats.board." + subs[i];
                ItemStack icon = Items.named(on ? Material.LIME_DYE : Material.GRAY_DYE, m.component(on ? "stats.sort-on" : "stats.sort-off",
                        Messages.comp("stat", m.component(label))), List.of(m.component(on ? "stats.board-showing" : "stats.sort-click")));
                menu.set(start + i, icon, clicker -> {
                    board.sub = index;
                    board.page = 0;
                    clicker.playSound(clicker.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.8f);
                    leaderboard(clicker, data);
                });
            }
        }
        // the places on this page
        for (int i = 0; i < rows.size() && i < PAGE_SLOTS.length; i++) {
            Database.Place row = rows.get(i);
            int position = board.page * PAGE_SLOTS.length + i + 1;
            boolean you = row.uuid().equals(viewer.getUniqueId());
            ItemStack item = Items.named(Material.PLAYER_HEAD, m.component(position <= 3 ? "stats.board-place-top" : you ? "stats.board-place-you" : "stats.board-place",
                    Messages.arg("place", position), Messages.arg("player", row.name()), Messages.parsed("medal", position == 1 ? "<gold>" : position == 2 ? "<white>" : "<#CD7F32>")),
                    m.lines("stats.board-value", Messages.arg("value", value(what, row.value()))));
            item.editMeta(SkullMeta.class, meta -> meta.setPlayerProfile(Bukkit.createProfile(row.uuid(), row.name())));
            if (you) item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
            if (position <= 64) item.setAmount(position);
            UUID id = row.uuid();
            String name = row.name();
            menu.set(PAGE_SLOTS[i], item, clicker -> load(clicker, id, name, other -> overview(clicker, other)));
        }
        if (rows.isEmpty()) menu.set(31, Items.named(Material.PAPER, m.component("stats.board-empty"), List.of()), null);
        // your place, and the page arrows
        int myPage = mine[0] > 0 ? (mine[0] - 1) / PAGE_SLOTS.length : -1;
        ItemStack you = Items.named(Material.NAME_TAG, m.component("stats.board-you"), mine[0] > 0
                ? m.lines("stats.board-you-lore", Messages.arg("rank", String.format(Locale.US, "%,d", mine[0])), Messages.arg("total", String.format(Locale.US, "%,d", mine[1])))
                : m.lines("stats.board-you-none"));
        menu.set(49, you, myPage < 0 ? null : clicker -> {
            board.page = myPage;
            leaderboard(clicker, data);
        });
        if (board.page > 0) menu.set(48, Items.named(Material.ARROW, m.component("stats.page-back", Messages.arg("page", board.page)), List.of()), clicker -> {
            board.page--;
            leaderboard(clicker, data);
        });
        if (board.page + 1 < pages) menu.set(50, Items.named(Material.ARROW, m.component("stats.page-next", Messages.arg("page", board.page + 2)), List.of()), clicker -> {
            board.page++;
            leaderboard(clicker, data);
        });
        menu.set(45, Items.named(Material.ARROW, m.component("kits.back"), List.of()), clicker -> overview(clicker, data));
        menu.set(53, Items.named(Material.BARRIER, m.component("kits.close"), List.of()), Player::closeInventory);
        menu.fill().open(viewer);
    }

    // ---------------------------------------------------------------- every player

    private void browse(Player viewer, Loaded data) {
        Board board = boards.computeIfAbsent(viewer.getUniqueId(), k -> new Board());
        String sort = PLAYER_SORTS[board.sort];
        int page = board.browsePage;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Database.PlayerRow> rows;
            int count;
            try {
                rows = plugin.database().players(sort, page * BROWSE_SLOTS.length, BROWSE_SLOTS.length);
                count = plugin.database().playerCount();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Couldn't read the players", e);
                rows = List.of();
                count = 0;
            }
            List<Database.PlayerRow> result = rows;
            int total = count;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (viewer.isOnline()) showBrowse(viewer, data, result, total);
            });
        });
    }

    private void showBrowse(Player viewer, Loaded data, List<Database.PlayerRow> rows, int count) {
        Messages m = plugin.messages();
        Board board = boards.computeIfAbsent(viewer.getUniqueId(), k -> new Board());
        int pages = Math.max(1, (count + BROWSE_SLOTS.length - 1) / BROWSE_SLOTS.length);
        Menu menu = new Menu(6, m.component("stats.browse-title", Messages.arg("page", board.browsePage + 1), Messages.arg("pages", pages),
                Messages.arg("players", count)));
        for (int i = 0; i < rows.size() && i < BROWSE_SLOTS.length; i++) {
            Database.PlayerRow row = rows.get(i);
            boolean online = Bukkit.getPlayer(row.uuid()) != null;
            double kdr = row.kills() / (double) Math.max(1, row.deaths());
            double rate = row.games() == 0 ? 0 : 100.0 * row.wins() / row.games();
            ItemStack item = Items.named(Material.PLAYER_HEAD, m.component(online ? "stats.browse-name-online" : "stats.browse-name", Messages.arg("player", row.name())),
                    m.lines("stats.browse-player-lore", Messages.arg("kills", row.kills()), Messages.arg("deaths", row.deaths()), Messages.arg("kdr", two(kdr)),
                            Messages.arg("wins", row.wins()), Messages.arg("games", row.games()), Messages.arg("rate", pct(rate)),
                            Messages.arg("objectives", row.captures()), Messages.arg("assists", row.assists())));
            item.editMeta(SkullMeta.class, meta -> meta.setPlayerProfile(Bukkit.createProfile(row.uuid(), row.name())));
            UUID id = row.uuid();
            String name = row.name();
            menu.set(BROWSE_SLOTS[i], item, clicker -> load(clicker, id, name, other -> overview(clicker, other)));
        }
        if (rows.isEmpty()) menu.set(22, Items.named(Material.PAPER, m.component("stats.board-empty"), List.of()), null);
        List<Component> lore = new ArrayList<>();
        for (int i = 0; i < PLAYER_SORTS.length; i++) {
            lore.add(m.component(i == board.sort ? "kits.option-on" : "kits.option-off", Messages.comp("option", m.component("stats.player-sort." + PLAYER_SORTS[i]))));
        }
        lore.add(Component.empty());
        lore.add(m.component("kits.sort-click"));
        menu.set(49, Items.named(Material.HOPPER, m.component("kits.sort-name"), lore), clicker -> {
            board.sort = (board.sort + 1) % PLAYER_SORTS.length;
            board.browsePage = 0;
            clicker.playSound(clicker.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.6f);
            browse(clicker, data);
        });
        menu.set(47, Items.named(Material.OAK_SIGN, m.component("stats.find"), m.lines("stats.find-lore")), null);
        if (board.browsePage > 0) menu.set(48, Items.named(Material.ARROW, m.component("stats.page-back", Messages.arg("page", board.browsePage)), List.of()), clicker -> {
            board.browsePage--;
            browse(clicker, data);
        });
        if (board.browsePage + 1 < pages) menu.set(50, Items.named(Material.ARROW, m.component("stats.page-next", Messages.arg("page", board.browsePage + 2)), List.of()), clicker -> {
            board.browsePage++;
            browse(clicker, data);
        });
        menu.set(45, Items.named(Material.ARROW, m.component("kits.back"), List.of()), clicker -> overview(clicker, data));
        menu.set(53, Items.named(Material.BARRIER, m.component("kits.close"), List.of()), Player::closeInventory);
        menu.fill().open(viewer);
    }

    static String value(String what, double number) {
        return switch (what) {
            case "kdr" -> String.format(Locale.ROOT, "%.2f", number);
            case "hill_seconds", "play_seconds" -> time((int) number);
            case "longest_shot" -> (int) number + " blocks";
            default -> String.format(Locale.US, "%,d", (long) number);
        };
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
