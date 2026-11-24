/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.quest;

import com.sasha.ctf.FractalCTF;
import com.sasha.ctf.config.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * CTF quests and challenges.
 *
 * <ul>
 *   <li>Quests: 5 daily, 2 weekly and 1 monthly, picked from {@link QuestDef#QUESTS} for each day, week and month
 *       (the same for everyone, from the date, so both CTF servers agree with nothing stored). A quest counts once
 *       it's accepted: in /quests or at the Quest Master, or straight away for players with Auto-Accept on
 *       (ctf.quests.autoaccept). Done, it pays CTF coins and network XP (quests.rewards in config.yml).</li>
 *   <li>Challenges: 3 a day (two for any mode, one for objectives), always on. Each is done at most once per game, and
 *       a player can finish quests.challenges-per-day of them a day; each pays network XP.</li>
 * </ul>
 * Progress is kept per player in {@code ctf_quests} (and the day's challenge count in {@code ctf_quest_days}),
 * read when a player joins either server and written every few seconds as it changes.
 */
public final class Quests implements Listener {

    /** A player's progress on one quest this period. */
    static final class Progress {
        int progress;
        boolean accepted, done, dirty;
        final Set<String> seen = new LinkedHashSet<>();               // for "different kits / modes" quests
    }

    /** One player's quests (current periods), their challenges today and their Auto-Accept setting. */
    static final class Profile {
        final Map<String, Progress> quests = new ConcurrentHashMap<>();   // quest id → progress (this period)
        String day;
        int challengesToday;
        boolean challengesDirty;
        boolean autoAccept, optionDirty;
    }

    private final FractalCTF plugin;
    private final String table, days;
    private final Map<UUID, Profile> profiles = new ConcurrentHashMap<>();

    public Quests(FractalCTF plugin) {
        this.plugin = plugin;
        String prefix = plugin.getConfig().getString("database.table-prefix", "ctf_").replaceAll("[^A-Za-z0-9_]", "");
        this.table = prefix + "quests";
        this.days = prefix + "quest_days";
    }

    public void start() {
        if (plugin.database() == null) return;
        try (Connection c = plugin.database().connection(); Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS " + table + " (uuid CHAR(36) NOT NULL, quest VARCHAR(32) NOT NULL,"
                    + " period VARCHAR(16) NOT NULL, progress INT NOT NULL DEFAULT 0, accepted TINYINT NOT NULL DEFAULT 0,"
                    + " done TINYINT NOT NULL DEFAULT 0, seen VARCHAR(255) NOT NULL DEFAULT '', PRIMARY KEY (uuid, quest, period))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS " + days + " (uuid CHAR(36) NOT NULL, day VARCHAR(16) NOT NULL,"
                    + " challenges INT NOT NULL DEFAULT 0, PRIMARY KEY (uuid, day))");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Couldn't set up the quest tables", e);
        }
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::flushAll, 100L, 100L);
        for (Player player : Bukkit.getOnlinePlayers()) load(player);
    }

    public boolean enabled() {
        return plugin.database() != null && plugin.getConfig().getBoolean("quests.enabled", true);
    }

    // ---------------------------------------------------------------- the rotation

    ZoneId zone() {
        try {
            return ZoneId.of(plugin.getConfig().getString("quests.time-zone", "America/Denver"));
        } catch (RuntimeException e) {
            return ZoneId.of("UTC");
        }
    }

    /** The key of the period running now: D2026-11-24, W2026-48, M2026-11 (C = the day, for challenges). */
    String periodKey(QuestDef.Period period) {
        LocalDate today = LocalDate.now(zone());
        return switch (period) {
            case DAILY, CHALLENGE -> "D" + today;
            case WEEKLY -> "W" + today.get(IsoFields.WEEK_BASED_YEAR) + "-" + today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            case MONTHLY -> "M" + today.getYear() + "-" + today.getMonthValue();
        };
    }

    /** How long until this period ends. */
    Duration resetsIn(QuestDef.Period period) {
        ZonedDateTime now = ZonedDateTime.now(zone());
        ZonedDateTime next = switch (period) {
            case DAILY, CHALLENGE -> now.toLocalDate().plusDays(1).atStartOfDay(zone());
            case WEEKLY -> now.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atStartOfDay(zone());
            case MONTHLY -> now.toLocalDate().with(TemporalAdjusters.firstDayOfNextMonth()).atStartOfDay(zone());
        };
        return Duration.between(now, next);
    }

    private static List<QuestDef> pick(List<QuestDef> pool, String key, int count) {
        List<QuestDef> copy = new ArrayList<>(pool);
        Collections.shuffle(copy, new Random(key.hashCode() * 31L + 7));
        return copy.subList(0, Math.min(count, copy.size()));
    }

    /** The quests running now for this period, in menu order. */
    public List<QuestDef> active(QuestDef.Period period) {
        if (period == QuestDef.Period.CHALLENGE) {
            String key = periodKey(QuestDef.Period.CHALLENGE);
            List<QuestDef> out = new ArrayList<>(pick(QuestDef.CHALLENGES_ANY, key + "a", 2));
            out.addAll(pick(QuestDef.CHALLENGES_OBJECTIVE, key + "o", 1));
            return out;
        }
        int count = switch (period) {
            case DAILY -> 5;
            case WEEKLY -> 2;
            default -> 1;
        };
        List<QuestDef> pool = QuestDef.QUESTS.stream().filter(q -> q.period() == period).toList();
        return pick(pool, periodKey(period), count);
    }

    private List<QuestDef> allActiveQuests() {
        List<QuestDef> all = new ArrayList<>(active(QuestDef.Period.DAILY));
        all.addAll(active(QuestDef.Period.WEEKLY));
        all.addAll(active(QuestDef.Period.MONTHLY));
        return all;
    }

    // ---------------------------------------------------------------- players

    Profile profile(Player player) {
        return profiles.get(player.getUniqueId());
    }

    Progress progress(Player player, QuestDef quest) {
        Profile p = profile(player);
        return p == null ? new Progress() : p.quests.computeIfAbsent(quest.id(), id -> new Progress());
    }

    public boolean autoAccept(Player player) {
        Profile p = profile(player);
        return p != null && p.autoAccept && player.hasPermission("ctf.quests.autoaccept");
    }

    /** Reads the player's quests for the current periods, then runs {@code then} on the main thread. */
    public void load(Player player, Runnable then) {
        if (!enabled()) return;
        UUID id = player.getUniqueId();
        List<QuestDef> current = allActiveQuests();
        String day = periodKey(QuestDef.Period.DAILY);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Profile profile = new Profile();
            profile.day = day;
            try (Connection c = plugin.database().connection()) {
                try (PreparedStatement s = c.prepareStatement("SELECT quest, period, progress, accepted, done, seen FROM " + table + " WHERE uuid = ?")) {
                    s.setString(1, id.toString());
                    try (ResultSet r = s.executeQuery()) {
                        while (r.next()) {
                            String quest = r.getString(1);
                            if (quest.equals("_autoaccept")) {
                                profile.autoAccept = r.getInt(4) == 1;
                                continue;
                            }
                            QuestDef def = QuestDef.byId(quest);
                            if (def == null || !current.contains(def) || !r.getString(2).equals(periodKey(def.period()))) continue;
                            Progress p = new Progress();
                            p.progress = r.getInt(3);
                            p.accepted = r.getInt(4) == 1;
                            p.done = r.getInt(5) == 1;
                            for (String s2 : r.getString(6).split(",")) if (!s2.isBlank()) p.seen.add(s2);
                            profile.quests.put(quest, p);
                        }
                    }
                }
                try (PreparedStatement s = c.prepareStatement("SELECT challenges FROM " + days + " WHERE uuid = ? AND day = ?")) {
                    s.setString(1, id.toString());
                    s.setString(2, day);
                    try (ResultSet r = s.executeQuery()) {
                        if (r.next()) profile.challengesToday = r.getInt(1);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Couldn't read the quests of " + player.getName(), e);
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                profiles.put(id, profile);
                if (autoAccept(player)) acceptAll(player, false);
                if (then != null) then.run();
            });
        });
    }

    void load(Player player) {
        load(player, null);
    }

    /** A new day (week, month) while someone is online: their progress belongs to the old one, so start afresh. */
    private void roll(Player player) {
        Profile p = profile(player);
        if (p == null || p.day.equals(periodKey(QuestDef.Period.DAILY))) return;
        flush(player.getUniqueId());
        profiles.remove(player.getUniqueId());
        load(player);
    }

    public void accept(Player player, QuestDef quest) {
        Progress p = progress(player, quest);
        if (p.accepted) return;
        p.accepted = true;
        p.dirty = true;
        plugin.messages().send(player, "quests.accepted", Messages.comp("quest", name(quest)));
    }

    /** Accepts every quest running now (Auto-Accept, or the menu's Accept All). */
    public int acceptAll(Player player, boolean tell) {
        int count = 0;
        for (QuestDef quest : allActiveQuests()) {
            Progress p = progress(player, quest);
            if (p.accepted) continue;
            p.accepted = true;
            p.dirty = true;
            count++;
        }
        if (count > 0) plugin.messages().send(player, tell ? "quests.accepted-all" : "quests.auto-accepted", Messages.arg("count", count));
        return count;
    }

    public void setAutoAccept(Player player, boolean on) {
        Profile p = profile(player);
        if (p == null) return;
        p.autoAccept = on;
        p.optionDirty = true;
        if (on) acceptAll(player, false);
    }

    // ---------------------------------------------------------------- progress (from QuestTracker, game server)

    /** Something a quest counts happened: {@code amount} more of {@code objective}, or a new {@code distinct} thing. */
    public void record(Player player, String objective, int amount, String distinct) {
        if (!enabled()) return;
        roll(player);
        for (QuestDef quest : allActiveQuests()) {
            if (!quest.objective().equals(objective)) continue;
            Progress p = progress(player, quest);
            if (!p.accepted || p.done) continue;
            if (distinct != null) {
                if (!p.seen.add(distinct)) continue;
                p.progress = p.seen.size();
            } else {
                p.progress += amount;
            }
            p.dirty = true;
            if (p.progress >= quest.goal()) {
                p.progress = quest.goal();
                p.done = true;
                complete(player, quest);
            } else if (plugin.getConfig().getBoolean("quests.progress-messages", true)) {
                plugin.messages().actionBar(player, "quests.progress", Messages.comp("quest", name(quest)),
                        Messages.arg("progress", p.progress), Messages.arg("goal", quest.goal()));
            }
        }
    }

    /** A challenge's objective was met this game. True if it counted (it's on today, under the day's limit). */
    public boolean challenge(Player player, String objective) {
        if (!enabled()) return false;
        roll(player);
        Profile profile = profile(player);
        if (profile == null) return false;
        for (QuestDef challenge : active(QuestDef.Period.CHALLENGE)) {
            if (!challenge.objective().equals(objective)) continue;
            int limit = plugin.getConfig().getInt("quests.challenges-per-day", 10);
            if (profile.challengesToday >= limit) {
                plugin.messages().send(player, "quests.challenge-limit", Messages.arg("limit", limit));
                return false;
            }
            profile.challengesToday++;
            profile.challengesDirty = true;
            long xp = xp("challenge");
            plugin.messages().send(player, "quests.challenge-done", Messages.comp("quest", name(challenge)),
                    Messages.arg("xp", xp), Messages.arg("count", profile.challengesToday), Messages.arg("limit", limit));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.4f);
            giveXp(player, xp);
            return true;
        }
        return false;
    }

    private void complete(Player player, QuestDef quest) {
        String kind = quest.period().name().toLowerCase(java.util.Locale.ROOT);
        int coins = coins(kind);
        long xp = xp(kind);
        TagResolver tags = TagResolver.resolver(Messages.comp("quest", name(quest)), Messages.arg("coins", coins), Messages.arg("xp", xp),
                Messages.comp("kind", plugin.messages().component("quests.kind." + kind)));
        for (var line : plugin.messages().lines("quests.completed", tags)) player.sendMessage(com.sasha.ctf.util.ChatCenter.center(line));
        plugin.messages().title(player, "quests.completed-title", 5, 40, 10, tags);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
        if (coins > 0 && plugin.database() != null) {
            UUID id = player.getUniqueId();
            String name = player.getName();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    int balance = plugin.database().addCoins(id, name, coins);
                    Bukkit.getScheduler().runTask(plugin, () -> plugin.kits().setCoins(id, balance));
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "Couldn't pay the quest coins for " + name, e);
                }
            });
        }
        giveXp(player, xp);
    }

    private void giveXp(Player player, long xp) {
        if (xp <= 0) return;
        if (plugin.networkXp() != null) plugin.networkXp().give(player, xp);
        else if (plugin.network() != null) {
            UUID id = player.getUniqueId();
            String name = player.getName();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    plugin.network().addXp(Map.of(id, name), xp);
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "Couldn't give quest XP to " + name, e);
                }
            });
        }
    }

    /** CTF coins a quest of this kind (daily, weekly, monthly) pays (quests.rewards, or the built-in amount). */
    int coins(String kind) {
        int fallback = switch (kind) {
            case "daily" -> 150;
            case "weekly" -> 750;
            case "monthly" -> 3000;
            default -> 0;
        };
        return plugin.getConfig().getInt("quests.rewards." + kind + ".coins", fallback);
    }

    /** Network XP a quest of this kind (or "challenge") pays. */
    long xp(String kind) {
        long fallback = switch (kind) {
            case "daily" -> 150;
            case "weekly" -> 600;
            case "monthly" -> 2000;
            case "challenge" -> 50;
            default -> 0;
        };
        return plugin.getConfig().getLong("quests.rewards." + kind + ".network-xp", fallback);
    }

    net.kyori.adventure.text.Component name(QuestDef quest) {
        return plugin.messages().component("quests.list." + quest.id() + ".name");
    }

    // ---------------------------------------------------------------- saving

    private void flushAll() {
        for (UUID id : profiles.keySet()) flush(id);
    }

    /** Writes what changed for this player (off the main thread, or at shutdown). */
    synchronized void flush(UUID id) {
        Profile profile = profiles.get(id);
        if (profile == null || plugin.database() == null) return;
        try (Connection c = plugin.database().connection()) {
            try (PreparedStatement s = c.prepareStatement("INSERT INTO " + table + " (uuid, quest, period, progress, accepted, done, seen)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE"
                    // the other CTF server may have saved more already (a player moves between them): never go backwards,
                    // except the Auto-Accept row, which can be turned off
                    + " progress = GREATEST(progress, VALUES(progress)),"
                    + " accepted = IF(quest = '_autoaccept', VALUES(accepted), GREATEST(accepted, VALUES(accepted))),"
                    + " done = GREATEST(done, VALUES(done)), seen = IF(CHAR_LENGTH(VALUES(seen)) >= CHAR_LENGTH(seen), VALUES(seen), seen)")) {
                boolean any = false;
                for (var entry : profile.quests.entrySet()) {
                    Progress p = entry.getValue();
                    if (!p.dirty) continue;
                    QuestDef def = QuestDef.byId(entry.getKey());
                    if (def == null) continue;
                    p.dirty = false;
                    s.setString(1, id.toString());
                    s.setString(2, def.id());
                    s.setString(3, periodKey(def.period()));
                    s.setInt(4, p.progress);
                    s.setInt(5, p.accepted ? 1 : 0);
                    s.setInt(6, p.done ? 1 : 0);
                    String seen = String.join(",", p.seen);
                    s.setString(7, seen.length() > 250 ? seen.substring(0, 250) : seen);
                    s.addBatch();
                    any = true;
                }
                if (profile.optionDirty) {
                    profile.optionDirty = false;
                    s.setString(1, id.toString());
                    s.setString(2, "_autoaccept");
                    s.setString(3, "-");
                    s.setInt(4, 0);
                    s.setInt(5, profile.autoAccept ? 1 : 0);
                    s.setInt(6, 0);
                    s.setString(7, "");
                    s.addBatch();
                    any = true;
                }
                if (any) s.executeBatch();
            }
            if (profile.challengesDirty) {
                profile.challengesDirty = false;
                try (PreparedStatement s = c.prepareStatement("INSERT INTO " + days + " (uuid, day, challenges) VALUES (?, ?, ?)"
                        + " ON DUPLICATE KEY UPDATE challenges = VALUES(challenges)")) {
                    s.setString(1, id.toString());
                    s.setString(2, profile.day);
                    s.setInt(3, profile.challengesToday);
                    s.executeUpdate();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Couldn't save quests", e);
        }
    }

    public void shutdown() {
        flushAll();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        load(player, () -> {
            // in the hub: a reminder about quests not accepted yet (none with Auto-Accept)
            if (!plugin.ctfConfig().hub() || autoAccept(player)) return;
            int waiting = 0;
            for (QuestDef q : allActiveQuests()) if (!progress(player, q).accepted) waiting++;
            if (waiting > 0) plugin.messages().send(player, "quests.reminder", Messages.arg("count", waiting));
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            flush(id);
            profiles.remove(id);
        });
    }

    /** For the menu: which quests are which. */
    Set<String> doneThisGame(Player player) {
        return plugin.questTracker() == null ? new HashSet<>() : plugin.questTracker().doneThisGame(player);
    }
}
