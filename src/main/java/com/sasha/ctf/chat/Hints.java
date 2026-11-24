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
 * Tips and announcements in chat every few minutes (hints.interval-seconds), one at a time from the hub's or the
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
        int every = Math.max(30, plugin.getConfig().getInt("hints.interval-seconds", 180));
        if (waited < every || busy(match)) return;
        waited = 0;
        String where = plugin.hub() != null ? "hints.hub" : "hints.game";
        if (queue.isEmpty()) {
            queue.addAll(plugin.messages().list(where));
            Collections.shuffle(queue);
        }
        if (queue.isEmpty()) return;
        String hint = queue.remove(0);
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
