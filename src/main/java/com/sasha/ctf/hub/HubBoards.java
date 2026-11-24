/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.hub;

import com.sasha.ctf.FractalCTF;
import com.sasha.ctf.config.Messages;
import com.sasha.ctf.data.Database;
import com.sasha.ctf.data.PlayerStats;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * The Top 10 boards in the hub, laid out like the Lobby's but in CTF's colours (the title and the place numbers
 * fade from red to blue): a title, ten numbered places (the empty ones shown as dashes), the viewer's own
 * number, their rank among everyone, and a hint, as one block of text that each player sees their own copy of.
 * Clicking a board (left or right) opens that leaderboard in the stats menu. Where they hang comes from the
 * `boards:` list in hub-npcs.yml: with a yaw the text lies flat on a wall, without one it turns to face you.
 */
public final class HubBoards implements Listener {

    private static final String TAG = "ctfboard";
    private static final double LINE = 0.25;
    private static final int PLACES = 10;

    private static final class Board {
        final int index;
        final String stat;
        final Location at;
        final boolean fixed;
        final float scale;
        final Map<UUID, TextDisplay> displays = new HashMap<>();
        Interaction box;

        Board(int index, String stat, Location at, boolean fixed, float scale) {
            this.index = index;
            this.stat = stat;
            this.at = at;
            this.fixed = fixed;
            this.scale = scale;
        }
    }

    private final FractalCTF plugin;
    private final HubManager hub;
    private final List<Board> boards = new ArrayList<>();
    private final Map<String, List<Database.Ranked>> top = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerStats> own = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, int[]>> ranks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastOpened = new HashMap<>();
    /** Each player's own per-map numbers added up (flags, pickups, wool, hill time). */
    private final Map<UUID, PlayerStats> detail = new ConcurrentHashMap<>();
    /** The Top Objectives board can show each kind of objective: which one each player has picked (punch to change). */
    private static final String[] OBJECTIVES = {"captures", "flag_captures", "flag_pickups", "wools", "hill_seconds"};
    private final Map<UUID, Integer> objective = new ConcurrentHashMap<>();

    /** The number this board shows this player (the Top Objectives board: the kind they picked). */
    private String stat(Board board, Player viewer) {
        if (!board.stat.equals("captures")) return board.stat;
        return OBJECTIVES[objective.getOrDefault(viewer.getUniqueId(), 0) % OBJECTIVES.length];
    }
    private BukkitTask task;
    private int seconds;

    HubBoards(FractalCTF plugin, HubManager hub) {
        this.plugin = plugin;
        this.hub = hub;
    }

    void start() {
        load();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (Entity entity : Bukkit.getWorlds().get(0).getEntities()) if (entity.getScoreboardTags().contains(TAG)) entity.remove();
        reload();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::second, 40L, 20L);
    }

    /** hub-npcs.yml has changed (the lobby was rebuilt): the boards are hung again from it. */
    void reloadFile() {
        clear();
        boards.clear();
        load();
        reload();
    }

    void stop() {
        if (task != null) task.cancel();
        clear();
    }

    private void clear() {
        for (Board board : boards) {
            for (TextDisplay display : board.displays.values()) display.remove();
            board.displays.clear();
            if (board.box != null) board.box.remove();
            board.box = null;
        }
    }

    private void load() {
        File file = new File(plugin.getDataFolder(), "hub-npcs.yml");
        YamlConfiguration yaml;
        if (file.isFile()) {
            yaml = YamlConfiguration.loadConfiguration(file);
        } else {
            var resource = plugin.getResource("hub-npcs.yml");
            if (resource == null) return;
            yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8));
        }
        World world = Bukkit.getWorlds().get(0);
        for (Map<?, ?> raw : yaml.getMapList("boards")) {
            String stat = String.valueOf(raw.get("stat"));
            boolean fixed = raw.get("yaw") instanceof Number;
            float yaw = fixed ? ((Number) raw.get("yaw")).floatValue() : 0;
            float scale = raw.get("scale") instanceof Number number ? number.floatValue() : 1.0f;
            boards.add(new Board(boards.size(), stat, new Location(world, num(raw, "x"), num(raw, "y"), num(raw, "z"), yaw, 0), fixed, scale));
        }
    }

    private static double num(Map<?, ?> map, String key) {
        return map.get(key) instanceof Number number ? number.doubleValue() : 0;
    }

    // ---------------------------------------------------------------- keeping them up to date

    /** Reads the leaderboards and everyone's own numbers off the main thread, then redraws. */
    private void reload() {
        if (plugin.database() == null) return;
        List<String> stats = new ArrayList<>(boards.stream().map(board -> board.stat).distinct().toList());
        if (stats.contains("captures")) for (String kind : OBJECTIVES) if (!stats.contains(kind)) stats.add(kind);
        List<UUID> online = Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).toList();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                for (String stat : stats) top.put(stat, plugin.database().leaderboard(stat, PLACES));
                for (UUID id : online) {
                    own.put(id, plugin.database().readStats(id));
                    PlayerStats sum = new PlayerStats();
                    for (var modes : plugin.database().readMapStats(id).values()) for (PlayerStats one : modes.values()) sum.add(one);
                    detail.put(id, sum);
                    Map<String, int[]> places = new HashMap<>();
                    for (String stat : stats) {
                        int[] place = plugin.database().rank(stat, id);
                        if (place != null) places.put(stat, place);
                    }
                    ranks.put(id, places);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Couldn't read the Top 10 boards", e);
            }
            Bukkit.getScheduler().runTask(plugin, this::redraw);
        });
    }

    private void redraw() {
        for (Board board : boards) {
            for (Map.Entry<UUID, TextDisplay> entry : board.displays.entrySet()) {
                Player viewer = Bukkit.getPlayer(entry.getKey());
                if (viewer != null && entry.getValue().isValid()) entry.getValue().text(text(board, viewer));
            }
        }
    }

    private void second() {
        seconds++;
        int every = Math.max(10, plugin.getConfig().getInt("hub.board-refresh-seconds", 30));
        boolean unread = seconds % every == 0;                       // or someone has just walked up whose own number isn't read yet
        for (Board board : boards) {
            World world = board.at.getWorld();
            board.displays.entrySet().removeIf(entry -> {
                Player viewer = Bukkit.getPlayer(entry.getKey());
                boolean gone = viewer == null || !viewer.getWorld().equals(world) || !entry.getValue().isValid()
                        || viewer.getLocation().distanceSquared(board.at) > 72 * 72;
                if (gone) entry.getValue().remove();
                return gone;
            });
            for (Player viewer : world.getPlayers()) {
                if (board.displays.containsKey(viewer.getUniqueId()) || viewer.getLocation().distanceSquared(board.at) > 56 * 56) continue;
                TextDisplay display = world.spawn(board.at, TextDisplay.class, spawned -> {
                    spawned.addScoreboardTag(TAG);
                    spawned.setBillboard(board.fixed ? Display.Billboard.FIXED : Display.Billboard.CENTER);
                    spawned.setAlignment(TextDisplay.TextAlignment.CENTER);
                    spawned.setShadowed(true);
                    spawned.setSeeThrough(false);
                    spawned.setBackgroundColor(Color.fromARGB(0));
                    spawned.setLineWidth(600);
                    spawned.setPersistent(false);
                    spawned.setVisibleByDefault(false);                // only its own viewer sees it
                    spawned.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.AxisAngle4f(),
                            new org.joml.Vector3f(board.scale, board.scale, board.scale), new org.joml.AxisAngle4f()));
                    spawned.text(text(board, viewer));
                });
                viewer.showEntity(plugin, display);
                board.displays.put(viewer.getUniqueId(), display);
                if (!own.containsKey(viewer.getUniqueId())) unread = true;
            }
            if (board.displays.isEmpty()) {
                if (board.box != null) board.box.remove();
                board.box = null;
            } else if (board.box == null || !board.box.isValid()) {
                // clicks only reach the server when they hit an entity: this box, over the whole board
                board.box = world.spawn(board.at, Interaction.class, box -> {
                    box.setResponsive(true);
                    box.setPersistent(false);
                    box.addScoreboardTag(TAG);
                    box.addScoreboardTag(TAG + ":" + board.index);
                    box.setInteractionWidth(board.fixed ? 3.2f : 2.6f);
                    box.setInteractionHeight((float) ((PLACES + (board.stat.equals("captures") ? 6 : 5)) * LINE * board.scale + 0.2));
                });
            }
        }
        if (unread) reload();
    }

    // ---------------------------------------------------------------- the text

    private Component text(Board board, Player viewer) {
        Messages m = plugin.messages();
        List<Component> lines = new ArrayList<>();
        String stat = stat(board, viewer);
        lines.add(m.component("board.title", Messages.comp("stat", m.component("stats.board." + stat))));
        if (board.stat.equals("captures")) {
            List<Component> kinds = new ArrayList<>();
            for (String kind : OBJECTIVES) {
                kinds.add(m.component(kind.equals(stat) ? "board.kind-on" : "board.kind-off", Messages.comp("kind", m.component("board.kind." + kind))));
            }
            lines.add(Component.join(JoinConfiguration.separator(m.component("board.kind-separator")), kinds));
        }
        List<Database.Ranked> rows = top.getOrDefault(stat, List.of());
        for (int i = 0; i < PLACES; i++) {
            lines.add(i < rows.size()
                    ? m.component("board.line", Messages.parsed("colour", fade(i)), Messages.arg("position", i + 1),
                    Messages.arg("name", rows.get(i).name()), Messages.arg("value", value(stat, rows.get(i).value())))
                    : m.component("board.empty", Messages.parsed("colour", fade(i)), Messages.arg("position", i + 1)));
        }
        lines.add(Component.empty());
        PlayerStats mine = own.get(viewer.getUniqueId());
        PlayerStats more = detail.get(viewer.getUniqueId());
        Double number = mine == null ? null : switch (stat) {
            case "kills" -> (double) mine.kills;
            case "wins" -> (double) mine.wins;
            case "captures" -> (double) mine.captures;
            case "assists" -> (double) mine.assists;
            case "games" -> (double) mine.games;
            case "kdr" -> mine.kills / (double) Math.max(1, mine.deaths);
            case "flag_captures" -> more == null ? null : (double) more.captures;
            case "flag_pickups" -> more == null ? null : (double) more.flagPickups;
            case "wools" -> more == null ? null : (double) more.wools;
            case "hill_seconds" -> more == null ? null : (double) more.hillSeconds;
            case "best_streak" -> more == null ? null : (double) more.bestStreak;
            case "longest_shot" -> more == null ? null : (double) more.longestShot;
            default -> null;
        };
        lines.add(number == null ? Component.empty() : m.component("board.your-score", Messages.arg("value", value(stat, number))));
        int[] place = ranks.getOrDefault(viewer.getUniqueId(), Map.of()).get(stat);
        lines.add(place == null ? Component.empty() : place[0] == 0 ? m.component("board.unranked")
                : m.component("board.rank", Messages.arg("rank", String.format(java.util.Locale.US, "%,d", place[0])),
                Messages.arg("total", String.format(java.util.Locale.US, "%,d", place[1]))));
        lines.add(m.component(board.stat.equals("captures") ? "board.click-hint-objectives" : "board.click-hint"));
        return Component.join(JoinConfiguration.newlines(), lines);
    }

    /** CTF's red fading to its blue, down the ten places. */
    private static String fade(int place) {
        int r = 0xFF + (0x55 - 0xFF) * place / (PLACES - 1), g = 0x55 + (0x99 - 0x55) * place / (PLACES - 1), b = 0x55 + (0xFF - 0x55) * place / (PLACES - 1);
        return String.format("<#%02X%02X%02X>", r, g, b);
    }

    private static String value(String stat, double number) {
        return switch (stat) {
            case "kdr" -> String.format(java.util.Locale.ROOT, "%.2f", number);
            case "longest_shot" -> (int) number + " blocks";
            case "hill_seconds" -> (int) number / 60 + "m " + (int) number % 60 + "s";
            default -> String.format(java.util.Locale.US, "%,d", (long) number);
        };
    }

    // ---------------------------------------------------------------- clicks

    private Board tagged(Entity entity) {
        if (!entity.getScoreboardTags().contains(TAG)) return null;
        for (String tag : entity.getScoreboardTags()) {
            if (!tag.startsWith(TAG + ":")) continue;
            try {
                int index = Integer.parseInt(tag.substring(TAG.length() + 1));
                if (index >= 0 && index < boards.size()) return boards.get(index);
            } catch (NumberFormatException ignored) {
                // not ours
            }
        }
        return boards.isEmpty() ? null : boards.get(0);
    }

    private void open(Player player, Board board) {
        long now = System.currentTimeMillis();
        if (now - lastOpened.getOrDefault(player.getUniqueId(), 0L) < 400) return;
        lastOpened.put(player.getUniqueId(), now);
        hub.stats().openLeaderboard(player, stat(board, player));
    }

    /** Left-click: the full leaderboard for this board's number. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onHit(PrePlayerAttackEntityEvent event) {
        if (!(event.getAttacked() instanceof Interaction)) return;
        Board board = tagged(event.getAttacked());
        if (board == null) return;
        event.setCancelled(true);
        if (board.stat.equals("captures")) {
            // punching the Top Objectives board changes which kind of objective it ranks
            Player player = event.getPlayer();
            long now = System.currentTimeMillis();
            if (now - lastOpened.getOrDefault(player.getUniqueId(), 0L) < 200) return;
            lastOpened.put(player.getUniqueId(), now);
            objective.merge(player.getUniqueId(), 1, (a, b) -> (a + b) % OBJECTIVES.length);
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.6f, 1.7f);
            TextDisplay display = board.displays.get(player.getUniqueId());
            if (display != null && display.isValid()) display.text(text(board, player));
            return;
        }
        open(event.getPlayer(), board);
    }

    /** Right-click does the same. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onUse(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Interaction)) return;
        Board board = tagged(event.getRightClicked());
        if (board == null) return;
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND) open(event.getPlayer(), board);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        for (Board board : boards) {
            TextDisplay display = board.displays.remove(id);
            if (display != null) display.remove();
        }
        own.remove(id);
        ranks.remove(id);
        detail.remove(id);
        objective.remove(id);
        lastOpened.remove(id);
    }
}
