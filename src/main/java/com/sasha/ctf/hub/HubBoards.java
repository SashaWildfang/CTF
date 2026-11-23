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
 * The Top 10 boards in the hub, in the Lobby's style: a title, ten numbered places (the empty ones shown as
 * dashes), the viewer's own number and a hint, as one block of text that each player sees their own copy of.
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
    private final Map<UUID, Long> lastOpened = new HashMap<>();
    private BukkitTask task;
    private int seconds;

    HubBoards(FractalCTF plugin, HubManager hub) {
        this.plugin = plugin;
        this.hub = hub;
    }

    void start() {
        load();
        if (boards.isEmpty()) return;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (Entity entity : Bukkit.getWorlds().get(0).getEntities()) if (tagged(entity) != null) entity.remove();
        reload();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::second, 40L, 20L);
    }

    void stop() {
        if (task != null) task.cancel();
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
        List<String> stats = boards.stream().map(board -> board.stat).distinct().toList();
        List<UUID> online = Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).toList();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                for (String stat : stats) top.put(stat, plugin.database().leaderboard(stat, PLACES));
                for (UUID id : online) own.put(id, plugin.database().readStats(id));
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
                    box.setInteractionHeight((float) ((PLACES + 4) * LINE * board.scale + 0.2));
                });
            }
        }
        if (unread) reload();
    }

    // ---------------------------------------------------------------- the text

    private Component text(Board board, Player viewer) {
        Messages m = plugin.messages();
        List<Component> lines = new ArrayList<>();
        lines.add(m.component("board.title", Messages.comp("stat", m.component("stats.board." + board.stat))));
        List<Database.Ranked> rows = top.getOrDefault(board.stat, List.of());
        for (int i = 0; i < PLACES; i++) {
            lines.add(i < rows.size()
                    ? m.component("board.line", Messages.arg("position", i + 1), Messages.arg("name", rows.get(i).name()),
                    Messages.arg("value", value(board.stat, rows.get(i).value())))
                    : m.component("board.empty", Messages.arg("position", i + 1)));
        }
        lines.add(Component.empty());
        PlayerStats mine = own.get(viewer.getUniqueId());
        Double number = mine == null ? null : switch (board.stat) {
            case "kills" -> (double) mine.kills;
            case "wins" -> (double) mine.wins;
            case "captures" -> (double) mine.captures;
            case "kdr" -> mine.kills / (double) Math.max(1, mine.deaths);
            default -> null;
        };
        lines.add(number == null ? Component.empty() : m.component("board.you", Messages.arg("value", value(board.stat, number))));
        lines.add(m.component("board.hint"));
        return Component.join(JoinConfiguration.newlines(), lines);
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
        hub.stats().openLeaderboard(player, board.stat);
    }

    /** Left-click: the full leaderboard for this board's number. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onHit(PrePlayerAttackEntityEvent event) {
        if (!(event.getAttacked() instanceof Interaction)) return;
        Board board = tagged(event.getAttacked());
        if (board == null) return;
        event.setCancelled(true);
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
        lastOpened.remove(id);
    }
}
