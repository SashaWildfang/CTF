/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.quest;

import com.sasha.ctf.FractalCTF;
import com.sasha.ctf.data.PlayerStats;
import com.sasha.ctf.game.Match;
import com.sasha.ctf.model.Mode;
import com.sasha.ctf.model.TeamColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Watches matches on the game server and turns what players do into quest progress and challenges. Four times a
 * second it compares each player's match numbers (kills, captures, wool…) with the last look, so every kind of kill
 * and objective counts the same way the stats do; a few things it notes itself: who was carrying a flag, when they
 * picked it up and whether they were hit since, kills in this life, recent kill times.
 */
public final class QuestTracker implements Listener {

    private static final class Look {
        int kills, deaths, assists, bowKills, voidKills, captures, pickups, wools, hillSeconds, playSeconds, longestShot;
        int lifeKills, lifeAssists, playCarry;
        boolean carrying, hitWhileCarrying, streakCounted;
        long pickedUpAt;
        final Deque<Long> killTimes = new ArrayDeque<>();
    }

    private final FractalCTF plugin;
    private Match match;
    private boolean firstBlood, endHandled;
    private final Map<UUID, Look> looks = new HashMap<>();
    private final Map<UUID, Set<String>> doneThisGame = new HashMap<>();

    public QuestTracker(FractalCTF plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::look, 20L, 5L);
    }

    /** Challenges this player has done in the current game. */
    public Set<String> doneThisGame(Player player) {
        return doneThisGame.getOrDefault(player.getUniqueId(), Set.of());
    }

    private Quests quests() {
        return plugin.quests();
    }

    private void challenge(Player player, String objective) {
        boolean onToday = false;
        for (QuestDef c : quests().active(QuestDef.Period.CHALLENGE)) if (c.objective().equals(objective)) onToday = true;
        if (!onToday) return;
        Set<String> done = doneThisGame.computeIfAbsent(player.getUniqueId(), id -> new HashSet<>());
        if (!done.add(objective)) return;                             // once per game (even if the day's limit was hit)
        quests().challenge(player, objective);
    }

    private void look() {
        Match now = plugin.game() == null ? null : plugin.game().match();
        if (now != match) {
            match = now;
            looks.clear();
            doneThisGame.clear();
            firstBlood = false;
            endHandled = false;
        }
        if (match == null || quests() == null || !quests().enabled()) return;
        if (match.phase() == Match.Phase.OVER) {
            if (!endHandled) {
                endHandled = true;
                finish();
            }
            return;
        }
        boolean counts = match.contested();
        long time = System.currentTimeMillis();
        Map<UUID, Boolean> diedCarrying = new HashMap<>();
        Map<Player, PlayerStats> current = new HashMap<>();
        for (Player player : match.playing()) current.put(player, match.stats(player.getUniqueId()));

        // deaths first, so a kill this look can see whether the one who died was carrying a flag
        for (var entry : current.entrySet()) {
            Player player = entry.getKey();
            PlayerStats st = entry.getValue();
            Look look = looks.get(player.getUniqueId());
            if (look == null || st.deaths <= look.deaths) continue;
            diedCarrying.put(player.getUniqueId(), look.carrying);
            look.lifeKills = 0;
            look.lifeAssists = 0;
            look.streakCounted = false;
            look.carrying = false;
        }
        for (var entry : current.entrySet()) {
            Player player = entry.getKey();
            PlayerStats st = entry.getValue();
            Look look = looks.get(player.getUniqueId());
            if (look == null) {
                looks.put(player.getUniqueId(), snapshot(new Look(), st, player));
                continue;
            }
            if (counts) count(player, st, look, time, diedCarrying);
            snapshot(look, st, player);
        }
    }

    private Look snapshot(Look look, PlayerStats st, Player player) {
        look.kills = st.kills;
        look.deaths = st.deaths;
        look.assists = st.assists;
        look.bowKills = st.bowKills;
        look.voidKills = st.voidKills;
        look.captures = st.captures;
        look.pickups = st.flagPickups;
        look.wools = st.wools;
        look.hillSeconds = st.hillSeconds;
        look.playSeconds = st.playSeconds;
        look.longestShot = st.longestShot;
        look.carrying = match.carrying(player);
        return look;
    }

    private void count(Player player, PlayerStats st, Look look, long time, Map<UUID, Boolean> diedCarrying) {
        Quests q = quests();
        boolean flags = match.mode() == Mode.CTF || match.mode() == Mode.CTF4;

        int kills = st.kills - look.kills;
        if (kills > 0) {
            q.record(player, "kills", kills, null);
            look.lifeKills += kills;
            for (int i = 0; i < kills; i++) look.killTimes.addLast(time);
            while (!look.killTimes.isEmpty() && time - look.killTimes.peekFirst() > 15_000) look.killTimes.removeFirst();
            if (look.killTimes.size() >= 3) challenge(player, "hat_trick");
            if (look.lifeKills >= 5 && !look.streakCounted) {
                look.streakCounted = true;
                q.record(player, "streaks", 1, null);
                challenge(player, "survivor");
            }
            if (!firstBlood) {
                firstBlood = true;
                challenge(player, "first_blood");
            }
            TeamColor team = match.teamOf(player.getUniqueId());
            Location base = team == null ? null : match.spawn(team);
            if (base != null && base.getWorld() == player.getWorld() && base.distanceSquared(player.getLocation()) <= 25 * 25) {
                q.record(player, "defend_kills", kills, null);
            }
            // someone on another team died carrying a flag this same moment: that was this kill
            for (var died : diedCarrying.entrySet()) {
                if (!died.getValue()) continue;
                TeamColor theirs = match.teamOf(died.getKey());
                if (theirs == null || theirs == team) continue;
                q.record(player, "carrier_kills", 1, null);
                challenge(player, "carrier_kill");
                died.setValue(false);
                break;
            }
        }
        int assists = st.assists - look.assists;
        if (assists > 0) {
            q.record(player, "assists", assists, null);
            look.lifeAssists += assists;
            if (look.lifeAssists >= 3) challenge(player, "bodyguard");
        }
        int bow = st.bowKills - look.bowKills;
        if (bow > 0) {
            q.record(player, "bow_kills", bow, null);
            if (st.longestShot >= 30 && st.longestShot > look.longestShot) challenge(player, "long_shot");
        }
        int voids = st.voidKills - look.voidKills;
        if (voids > 0) {
            q.record(player, "void_kills", voids, null);
            if (st.voidKills >= 2) challenge(player, "void_double");
        }
        if (st.flagPickups > look.pickups) {
            q.record(player, "flag_pickups", st.flagPickups - look.pickups, null);
            look.pickedUpAt = time;
            look.hitWhileCarrying = false;
        }
        int captures = st.captures - look.captures;
        if (captures > 0) {
            if (flags) {
                q.record(player, "flag_captures", captures, null);
                if (!look.hitWhileCarrying) challenge(player, "clean_capture");
                if (time - look.pickedUpAt <= 25_000) challenge(player, "speed_capture");
            } else if (match.mode() == Mode.KOTH) {
                q.record(player, "hill_captures", captures, null);
            }
        }
        int wools = st.wools - look.wools;
        if (wools > 0) {
            q.record(player, "wools", wools, null);
            if (st.wools >= 3) challenge(player, "wool_thief");
        }
        int hill = st.hillSeconds - look.hillSeconds;
        if (hill > 0) {
            q.record(player, "hill_seconds", hill, null);
            if (st.hillSeconds >= 45) challenge(player, "hill_hold");
        }
        int played = st.playSeconds - look.playSeconds;
        if (played > 0) {
            look.playCarry += played;
            if (look.playCarry >= 60) {
                q.record(player, "play_minutes", look.playCarry / 60, null);
                look.playCarry %= 60;
            }
        }
    }

    /** The match ended: games, wins (and which kit and mode they were won with), flawless wins. */
    private void finish() {
        for (UUID id : looks.keySet()) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) continue;
            PlayerStats st = match.stats(id);
            if (st.games < 1) continue;                               // not a real match (one team the whole time)
            quests().record(player, "games", 1, null);
            if (st.wins < 1) continue;
            quests().record(player, "wins", 1, null);
            quests().record(player, "mode_wins", 0, match.mode().name());
            var kit = plugin.kits().current(id);
            if (kit != null) quests().record(player, "kit_wins", 0, kit.name());
            if (st.deaths == 0) challenge(player, "flawless");
        }
    }

    /** Hit while carrying a flag: no clean capture this run. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || match == null) return;
        Look look = looks.get(player.getUniqueId());
        if (look != null && match.carrying(player)) look.hitWhileCarrying = true;
    }
}
