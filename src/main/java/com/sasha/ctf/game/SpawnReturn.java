/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.game;

import com.sasha.ctf.FractalCTF;
import com.sasha.ctf.config.Messages;
import com.sasha.ctf.model.TeamColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * /spawn in a match: back to your team's base after game.spawn-seconds (10) of standing still. Looking around is
 * fine; walking off, being hurt or picking up the flag stops it. While it counts down the player sees the time in
 * front of them, swirls of their team's colour wind round them, and everyone sees "Returning to Base in 7s" under
 * their name.
 */
public final class SpawnReturn implements Listener {

    private final FractalCTF plugin;
    private final GameManager game;
    private final Map<UUID, Channel> channels = new HashMap<>();

    private final class Channel {
        final Player player;
        final Location from;
        final TeamColor team;
        final long ends;
        final BukkitTask task;
        TextDisplay line;
        int ticks;

        Channel(Player player, TeamColor team, int seconds) {
            this.player = player;
            this.from = player.getLocation();
            this.team = team;
            this.ends = System.currentTimeMillis() + seconds * 1000L;
            this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        }

        int left() {
            return (int) Math.max(0, Math.ceil((ends - System.currentTimeMillis()) / 1000.0));
        }

        void tick() {
            Match match = game.match();
            if (!player.isOnline() || match == null || !match.live() || !match.world().equals(player.getWorld()) || player.isDead()
                    || match.ghost(player.getUniqueId()) || match.teamOf(player.getUniqueId()) != team) {
                stop(player, null);
                return;
            }
            Location now = player.getLocation();
            double dx = now.getX() - from.getX(), dz = now.getZ() - from.getZ(), dy = now.getY() - from.getY();
            if (dx * dx + dz * dz > 0.5 * 0.5 || Math.abs(dy) > 1.2) {
                stop(player, "spawn.moved");
                return;
            }
            if (match.rules().carrying(player)) {
                stop(player, "spawn.carrying");
                return;
            }
            int left = left();
            if (left <= 0) {
                arrive(match);
                return;
            }
            // two swirls of team-coloured dust winding up round them
            double angle = ticks * 0.35;
            double height = (ticks % 40) / 20.0;
            Particle.DustOptions dust = new Particle.DustOptions(team.leather(), 1.1f);
            for (int arm = 0; arm < 2; arm++) {
                double a = angle + arm * Math.PI;
                player.getWorld().spawnParticle(Particle.DUST, now.getX() + Math.cos(a) * 0.8, now.getY() + height, now.getZ() + Math.sin(a) * 0.8, 1, 0, 0, 0, dust);
            }
            if (ticks % 4 == 0) player.getWorld().spawnParticle(Particle.WITCH, now.clone().add(0, 1, 0), 2, 0.4, 0.6, 0.4, 0);
            if (ticks % 20 == 0) {
                plugin.messages().title(player, "spawn.title", 0, 25, 0, Messages.arg("seconds", left));
                player.playSound(now, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 0.8f + (10 - Math.min(10, left)) * 0.06f);
                nameLine(left);
            }
            ticks++;
        }

        /** "Returning to Base in 7s" under their name, for everyone else. */
        void nameLine(int left) {
            var text = plugin.messages().component("spawn.under-name", Messages.arg("seconds", left), Messages.comp("team", Match.teamName(team)));
            if (line == null || !line.isValid()) {
                line = player.getWorld().spawn(player.getLocation(), TextDisplay.class, made -> {
                    made.setPersistent(false);
                    made.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                    made.setDefaultBackground(true);
                    made.setSeeThrough(false);
                    made.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(0f, 0.04f, 0f), new org.joml.AxisAngle4f(),
                            new org.joml.Vector3f(0.8f, 0.8f, 0.8f), new org.joml.AxisAngle4f()));
                    made.text(text);
                });
                player.hideEntity(plugin, line);
            } else {
                line.text(text);
            }
            if (!player.getPassengers().contains(line)) player.addPassenger(line);
        }

        void arrive(Match match) {
            stop(player, null);
            Location base = match.spawn(team);
            player.teleport(base);
            player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, base.clone().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.05);
            player.playSound(base, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);
            plugin.messages().title(player, "spawn.arrived-title", 0, 30, 10);
        }

        void end() {
            task.cancel();
            if (line != null) {
                player.removePassenger(line);
                line.remove();
            }
        }
    }

    SpawnReturn(FractalCTF plugin, GameManager game) {
        this.plugin = plugin;
        this.game = game;
    }

    /** /spawn: start the count, or say why not. */
    public void start(Player player) {
        Match match = game.match();
        var messages = plugin.messages();
        TeamColor team = match == null ? null : match.teamOf(player.getUniqueId());
        if (match == null || !match.world().equals(player.getWorld()) || !match.live() || team == null || match.ghost(player.getUniqueId())) {
            messages.send(player, "spawn.not-now");
            return;
        }
        if (match.paused()) {
            messages.send(player, "spawn.paused");
            return;
        }
        if (match.rules().carrying(player)) {
            messages.send(player, "spawn.carrying");
            return;
        }
        if (channels.containsKey(player.getUniqueId())) {
            messages.send(player, "spawn.already");
            return;
        }
        int seconds = Math.max(1, plugin.getConfig().getInt("game.spawn-seconds", 10));
        channels.put(player.getUniqueId(), new Channel(player, team, seconds));
        messages.send(player, "spawn.started", Messages.arg("seconds", seconds));
    }

    /** Stops a player's count (with a reason to tell them, or null to say nothing). */
    void stop(Player player, String reason) {
        Channel channel = channels.remove(player.getUniqueId());
        if (channel == null) return;
        channel.end();
        if (reason != null && player.isOnline()) {
            plugin.messages().send(player, reason);
            plugin.messages().title(player, "spawn.cancelled-title", 0, 25, 10);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.6f);
        }
    }

    boolean returning(Player player) {
        return channels.containsKey(player.getUniqueId());
    }

    void clear() {
        for (Channel channel : channels.values()) channel.end();
        channels.clear();
    }

    /** Being hurt stops it. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && channels.containsKey(player.getUniqueId())) stop(player, "spawn.hurt");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stop(event.getPlayer(), null);
    }
}
