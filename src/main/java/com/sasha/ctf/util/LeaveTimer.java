/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.util;

import com.sasha.ctf.FractalCTF;
import com.sasha.ctf.config.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Back to Lobby items: a short countdown before the player is sent, so a misclick can be taken back.
 * Using the item again during it cancels.
 */
public final class LeaveTimer {

    private static final Map<UUID, BukkitTask> waiting = new HashMap<>();

    private LeaveTimer() {}

    /** Starts the countdown to {@code server}, or cancels it if one is running. */
    public static void toggle(FractalCTF plugin, Player player, String server, String where) {
        Messages messages = plugin.messages();
        BukkitTask running = waiting.remove(player.getUniqueId());
        if (running != null) {
            running.cancel();
            messages.actionBar(player, "leave.cancelled");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.8f);
            return;
        }
        int seconds = Math.max(1, plugin.getConfig().getInt("leave-seconds", 3));
        final int[] left = {seconds};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                cancel(player);
                return;
            }
            if (left[0] <= 0) {
                cancel(player);
                Network.send(plugin, player, server);
                return;
            }
            messages.actionBar(player, "leave.counting", Messages.arg("seconds", left[0]), Messages.arg("where", where));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.2f);
            left[0]--;
        }, 0L, 20L);
        waiting.put(player.getUniqueId(), task);
    }

    public static void cancel(Player player) {
        BukkitTask task = waiting.remove(player.getUniqueId());
        if (task != null) task.cancel();
    }
}
