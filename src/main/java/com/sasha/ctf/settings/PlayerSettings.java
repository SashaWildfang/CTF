/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.settings;

import com.sasha.ctf.FractalCTF;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Each player's /settings, kept in the CTF database (so the lobby and the match server agree), loaded when they join
 * and cached while they're on. Without a database they're kept in memory for the session only.
 */
public final class PlayerSettings implements Listener {

    /** A setting: its key, what it is when never changed, and the permission it needs (null: everyone). */
    public enum Setting {
        AUTO_JOIN("auto-join", true, null),
        JOIN_AS_SPECTATOR("join-spectating", false, "ctf.spectate"),
        HIDE_PLAYERS("hide-players", false, null),
        TIPS("tips", true, "ctf.hints.toggle"),
        SOUNDS("lobby-sounds", true, null);

        public final String key;
        public final boolean fallback;
        public final String permission;

        Setting(String key, boolean fallback, String permission) {
            this.key = key;
            this.fallback = fallback;
            this.permission = permission;
        }
    }

    private final FractalCTF plugin;
    private final Map<UUID, Map<String, String>> cache = new ConcurrentHashMap<>();

    public PlayerSettings(FractalCTF plugin) {
        this.plugin = plugin;
    }

    /** What the setting is for this player. A perk setting they no longer have the rank for is back at its default. */
    public boolean get(Player player, Setting setting) {
        if (setting.permission != null && !player.hasPermission(setting.permission)) return setting.fallback;
        String value = cache.getOrDefault(player.getUniqueId(), Map.of()).get(setting.key);
        return value == null ? setting.fallback : Boolean.parseBoolean(value);
    }

    public void set(Player player, Setting setting, boolean on) {
        UUID id = player.getUniqueId();
        cache.computeIfAbsent(id, k -> new ConcurrentHashMap<>()).put(setting.key, Boolean.toString(on));
        if (plugin.database() == null) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.database().savePlayerSetting(id, setting.key, Boolean.toString(on));
            } catch (java.sql.SQLException e) {
                plugin.getLogger().warning("Couldn't save a setting for " + player.getName() + ": " + e.getMessage());
            }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (plugin.database() == null) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Map<String, String> loaded = new ConcurrentHashMap<>(plugin.database().playerSettings(id));
                cache.put(id, loaded);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player player = Bukkit.getPlayer(id);
                    if (player != null && plugin.hub() != null) plugin.hub().applyHidden(player);
                });
            } catch (java.sql.SQLException e) {
                plugin.getLogger().warning("Couldn't load settings: " + e.getMessage());
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cache.remove(event.getPlayer().getUniqueId());
    }
}
