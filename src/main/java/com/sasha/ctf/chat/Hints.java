package com.sasha.ctf.chat;

import com.sasha.ctf.FractalCTF;
import com.sasha.ctf.config.Messages;
import com.sasha.ctf.game.Match;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tips and announcements in chat every few minutes (hints.minutes-between), one at a time from the hub's or the
 * game's list in messages.yml, shuffled so none repeats until all have been seen. They stay out of the way: never
 * while the lobby's vote is closing or the gate is counting down, and never during a match's countdown, its first
 * seconds or its end screen (then they wait and try again shortly). /hints turns them off or on for each player.
 */
public final class Hints {

    private final FractalCTF plugin;
    private final NamespacedKey off;
    private final List<String> queue = new ArrayList<>();
    private int waited;
    private int playingFor;

    public Hints(FractalCTF plugin) {
        this.plugin = plugin;
        this.off = new NamespacedKey(plugin, "hints-off");
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::second, 20L, 20L);
    }

    private void second() {
        Match match = plugin.game() == null ? null : plugin.game().match();
        playingFor = match != null && match.phase() == Match.Phase.PLAYING ? playingFor + 1 : 0;
        if (!plugin.getConfig().getBoolean("hints.enabled", true) || Bukkit.getOnlinePlayers().isEmpty()) return;
        waited++;
        int every = (int) Math.round(Math.max(1, plugin.getConfig().getDouble("hints.minutes-between", 6)) * 60);
        if (waited < every || busy(match)) return;
        waited = 0;
        String where = plugin.hub() != null ? "hints.hub" : "hints.game";
        if (queue.isEmpty()) {
            queue.addAll(plugin.messages().list(where));
            Collections.shuffle(queue);
        }
        if (queue.isEmpty()) return;
        String hint = wrap(queue.remove(0), Math.max(150, plugin.getConfig().getInt("hints.line-width", 290)));
        var line = plugin.messages().parse(plugin.messages().raw("hints.format"), Messages.parsed("hint", hint));
        String sound = plugin.getConfig().getString("hints.sound", "block.amethyst_block.chime");
        float volume = (float) plugin.getConfig().getDouble("hints.sound-volume", 0.8), pitch = (float) plugin.getConfig().getDouble("hints.sound-pitch", 1.5);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getPersistentDataContainer().has(off, PersistentDataType.BYTE)) continue;
            player.sendMessage(line);
            if (sound != null && !sound.isBlank()) {
                // a soft two-note chime (any sound key works, including resource-pack sounds like fractal:...)
                player.playSound(net.kyori.adventure.sound.Sound.sound(net.kyori.adventure.key.Key.key(sound), net.kyori.adventure.sound.Sound.Source.MASTER, volume, pitch));
                Bukkit.getScheduler().runTaskLater(plugin, () -> player.playSound(net.kyori.adventure.sound.Sound.sound(
                        net.kyori.adventure.key.Key.key("block.note_block.chime"), net.kyori.adventure.sound.Sound.Source.MASTER, volume * 0.6f, 1.8f)), 3L);
            }
        }
    }

    private static final net.kyori.adventure.text.minimessage.MiniMessage MM = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage();

    private record Unit(String text, String plain, int width) {}

    /**
     * Breaks a tip into lines that fit the chat (default width) ourselves, so the client never wraps it:
     * a command stays with what follows it ("/t <message>", "/rate 1-5", "/report [name]"), and the lines come
     * out about the same length, so there's never one lonely word on the last line.
     */
    static String wrap(String tip, int max) {
        List<String> tokens = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        int depth = 0;
        for (char c : tip.toCharArray()) {
            if (c == '<') depth++;
            else if (c == '>' && depth > 0) depth--;
            if (c == ' ' && depth == 0) {
                if (!word.isEmpty()) tokens.add(word.toString());
                word.setLength(0);
            } else {
                word.append(c);
            }
        }
        if (!word.isEmpty()) tokens.add(word.toString());
        List<Unit> units = new ArrayList<>();
        for (String token : tokens) {
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(MM.deserialize(token));
            Unit last = units.isEmpty() ? null : units.getLast();
            boolean argument = plain.startsWith("<") || plain.startsWith("[") || plain.startsWith("(");
            boolean afterCommand = last != null && last.plain().startsWith("/") && !plain.isEmpty()
                    && (argument || Character.isDigit(plain.charAt(0)));
            if (last != null && (argument || afterCommand)) {
                String text = last.text() + " " + token;
                String all = last.plain() + " " + plain;
                units.set(units.size() - 1, new Unit(text, all, width(all)));
            } else {
                units.add(new Unit(token, plain, width(plain)));
            }
        }
        int widest = 0, total = 0;
        for (Unit u : units) {
            widest = Math.max(widest, u.width());
            total += u.width() + SPACE;
        }
        int lines = lines(units, max).size();
        List<List<Unit>> best = lines(units, max);
        if (lines == 2 || lines == 3) {
            // try every place to break (as few lines as possible): breaks after a full stop or colon read best,
            // then the most even lines, and never one word alone on the last line
            double bestCost = Double.MAX_VALUE;
            int n = units.size();
            for (int a = 1; a < n; a++) {
                for (int b = lines == 3 ? a + 1 : n; b <= n; b++) {
                    if (lines == 3 && b == n) continue;
                    List<List<Unit>> split = new ArrayList<>();
                    split.add(units.subList(0, a));
                    if (lines == 3) split.add(units.subList(a, b));
                    split.add(units.subList(lines == 3 ? b : a, n));
                    double cost = cost(split, max);
                    if (cost < bestCost) {
                        bestCost = cost;
                        best = split;
                    }
                    if (lines == 2) break;
                }
            }
        } else if (lines > 3) {
            for (int w = Math.max(widest, total / lines); w <= max; w += 4) {
                if (lines(units, w).size() <= lines) {
                    best = lines(units, w);
                    break;
                }
            }
        }
        List<String> out = new ArrayList<>();
        for (List<Unit> line : best) {
            StringBuilder b = new StringBuilder();
            for (Unit u : line) {
                if (!b.isEmpty()) b.append(' ');
                b.append(u.text());
            }
            out.add(b.toString());
        }
        return String.join("\n  ", out);
    }

    private static final int SPACE = 4;

    /** How badly a way of breaking a tip reads (lower is better); too wide is out. */
    private static double cost(List<List<Unit>> split, int max) {
        int widest = 0, narrowest = Integer.MAX_VALUE;
        double cost = 0;
        for (int i = 0; i < split.size(); i++) {
            List<Unit> line = split.get(i);
            int w = -SPACE;
            for (Unit u : line) w += u.width() + SPACE;
            if (w > max) return Double.MAX_VALUE;
            widest = Math.max(widest, w);
            narrowest = Math.min(narrowest, w);
            if (i < split.size() - 1) {
                String end = line.getLast().plain();
                if (end.endsWith(".") || end.endsWith(":") || end.endsWith("?") || end.endsWith("!")) cost -= 60;
                else if (end.endsWith(",")) cost -= 25;
            }
        }
        if (split.getLast().size() == 1) cost += 400;
        return cost + (widest - narrowest);
    }

    private static List<List<Unit>> lines(List<Unit> units, int max) {
        List<List<Unit>> out = new ArrayList<>();
        List<Unit> line = new ArrayList<>();
        int width = 0;
        for (Unit u : units) {
            int add = line.isEmpty() ? u.width() : SPACE + u.width();
            if (!line.isEmpty() && width + add > max) {
                out.add(line);
                line = new ArrayList<>();
                width = 0;
                add = u.width();
            }
            line.add(u);
            width += add;
        }
        if (!line.isEmpty()) out.add(line);
        return out;
    }

    private static int width(String plain) {
        return com.sasha.ctf.util.ChatCenter.pixels(net.kyori.adventure.text.Component.text(plain));
    }

    /** Something with a countdown or a result is going on: a tip now would get in its way. */
    private boolean busy(Match match) {
        var hub = plugin.hub();
        if (hub != null) {
            if (hub.paused()) return false;
            return switch (hub.phase()) {
                case COUNTDOWN -> hub.locked() || hub.secondsLeft() - hub.gateSeconds() <= 15;
                case STARTING -> true;
                default -> false;
            };
        }
        if (match == null) return false;
        return match.phase() != Match.Phase.PLAYING || playingFor < 30;
    }

    /** /hints: off, or back on. */
    public void toggle(Player player) {
        var data = player.getPersistentDataContainer();
        if (data.has(off, PersistentDataType.BYTE)) {
            data.remove(off);
            plugin.messages().send(player, "hints.turned-on");
        } else {
            data.set(off, PersistentDataType.BYTE, (byte) 1);
            plugin.messages().send(player, "hints.turned-off");
        }
    }
}
