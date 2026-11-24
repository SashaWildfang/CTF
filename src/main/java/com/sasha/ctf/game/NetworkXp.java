/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.game;

import com.sasha.ctf.FractalCTF;
import com.sasha.ctf.config.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Network XP for playing (game server only, with the network database). Every minute of a match, each player on a
 * team who actually played that minute gets network-xp.per-minute XP. Playing means walking at least
 * network-xp.min-blocks-moved blocks (on their own: a step can't count more than a block, so knockback, teleports
 * and being carried don't add up) or fighting, building or breaking. Standing AFK, or only looking around, earns
 * nothing. Spectators and the warm-up don't count either. The Lobby shows the level in My Profile.
 */
public final class NetworkXp implements Listener {

    private final FractalCTF plugin;
    private final Map<UUID, Double> walked = new HashMap<>();
    private final Set<UUID> acted = new HashSet<>();
    private BukkitTask task;

    public NetworkXp(FractalCTF plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::minute, 1200L, 1200L);
    }

    public void flush() {
        if (task != null) task.cancel();
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("network-xp.enabled", true);
    }

    /** Once a minute: XP for everyone who played it, then a fresh minute. */
    private void minute() {
        Match match = plugin.game() == null ? null : plugin.game().match();
        Map<UUID, String> earned = new HashMap<>();
        if (enabled() && match != null && match.contested()) {
            double needed = Math.max(0, plugin.getConfig().getDouble("network-xp.min-blocks-moved", 10));
            for (Player player : match.playing()) {
                UUID id = player.getUniqueId();
                if (acted.contains(id) || walked.getOrDefault(id, 0.0) >= needed) earned.put(id, player.getName());
            }
        }
        walked.clear();
        acted.clear();
        if (earned.isEmpty()) return;
        long xp = Math.max(1, plugin.getConfig().getLong("network-xp.per-minute", 10));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Map<UUID, Long> totals = plugin.network().addXp(earned, xp);
                Bukkit.getScheduler().runTask(plugin, () -> levelUps(totals, xp));
            } catch (SQLException e) {
                plugin.getLogger().warning("Couldn't give network XP: " + e.getMessage());
            }
        });
    }

    private void levelUps(Map<UUID, Long> totals, long xp) {
        long first = Math.max(1, plugin.getConfig().getLong("network-xp.first-level-xp", 200));
        long extra = Math.max(0, plugin.getConfig().getLong("network-xp.extra-xp-per-level", 50));
        for (var entry : totals.entrySet()) {
            int before = level(entry.getValue() - xp, first, extra), after = level(entry.getValue(), first, extra);
            Player player = Bukkit.getPlayer(entry.getKey());
            if (after > before && player != null) {
                plugin.messages().send(player, "network-xp.level-up", Messages.arg("level", after));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1f);
            }
        }
    }

    /** The level for this much XP: level L to L+1 takes first + extra × (L − 1) (the Lobby uses the same). */
    static int level(long total, long first, long extra) {
        int level = 1;
        long left = Math.max(0, total);
        while (level < 10_000) {
            long need = Math.max(1, first + extra * (level - 1L));
            if (left < need) break;
            left -= need;
            level++;
        }
        return level;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom(), to = event.getTo();
        if (from.getWorld() != to.getWorld() || event.getPlayer().isInsideVehicle()) return;
        double dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
        double step = Math.sqrt(dx * dx + dz * dz);
        if (step <= 0.01) return;                                 // only turning the head
        walked.merge(event.getPlayer().getUniqueId(), Math.min(1.0, step), Double::sum);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) acted.add(player.getUniqueId());
        else if (event.getDamager() instanceof Projectile arrow && arrow.getShooter() instanceof Player player) acted.add(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        acted.add(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        acted.add(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        walked.remove(event.getPlayer().getUniqueId());
        acted.remove(event.getPlayer().getUniqueId());
    }
}
