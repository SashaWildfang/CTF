package com.sasha.ctf.chat;

import com.sasha.ctf.FractalCTF;
import com.sasha.ctf.config.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat moderation for public and team chat: the word filter (chat-filter.yml), anti-spam (repeats, caps, character
 * floods, links and IPs), /mutechat, /slowchat and /clearchat. Staff (ctf.chat.staff) are told about blocked messages.
 *
 * <p>Word list entries: {@code fuck} matches any word starting with it (fucking, fucker), {@code =ass} only that
 * exact word, {@code *fuck*} it anywhere inside a word (motherfucker). Words are matched after undoing leetspeak
 * (f4ck, $hit), repeated letters (fuuuck) and dots/dashes between letters (f.u.c.k).
 */
public final class ChatGuard {

    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}@$!|]+(?:[._\\-][\\p{L}\\p{N}@$!|]+)*");
    private static final Pattern LINK = Pattern.compile("(?i)(https?://\\S+|www\\.\\S+|\\b[a-z0-9-]{2,}\\s?(?:\\.|\\(dot\\)|\\[dot]|\\sdot\\s)\\s?(?:com|net|org|gg|io|xyz|me|co|tv|ly|uk|us|de|fr|ru|club|fun|pro|world|online|site|store|link|info|biz)\\b)");
    private static final Pattern IP = Pattern.compile("\\b\\d{1,3}(?:\\s?[.,]\\s?\\d{1,3}){3}(?::\\d{2,5})?\\b");

    private final FractalCTF plugin;
    private final File file;
    private YamlConfiguration words;
    private volatile boolean muted;
    private volatile String muteReason = "";
    /** /slowchat: seconds between messages for everyone without ctf.chat.staff (0 = off). */
    private volatile double slow;
    private final Map<UUID, String> lastText = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastTime = new ConcurrentHashMap<>();

    public ChatGuard(FractalCTF plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "chat-filter.yml");
        load();
    }

    public void load() {
        if (!file.exists()) plugin.saveResource("chat-filter.yml", false);
        words = YamlConfiguration.loadConfiguration(file);
        var in = plugin.getResource("chat-filter.yml");
        if (in != null) words.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
    }

    private boolean on(String key, boolean fallback) {
        return plugin.getConfig().getBoolean("chat-moderation." + key, fallback);
    }

    // ---------------------------------------------------------------- checking a message

    /** The text to send, cleaned up, or null when the message is stopped (the sender has been told why). */
    public String check(Player sender, String text) {
        boolean staff = sender.hasPermission("ctf.chat.staff");
        if (muted && !staff) {
            plugin.messages().send(sender, muteReason.isBlank() ? "chat.muted" : "chat.muted-reason", Messages.arg("reason", muteReason));
            return null;
        }
        if (!staff && slow > 0) {
            long now = System.currentTimeMillis(), last = lastTime.getOrDefault(sender.getUniqueId(), 0L);
            long wait = (long) (slow * 1000);
            if (now - last < wait) {
                plugin.messages().send(sender, "chat.slow", Messages.arg("seconds", String.format(Locale.ROOT, "%.1f", (wait - (now - last)) / 1000.0)),
                        Messages.arg("slow", fmt(slow)));
                return null;
            }
        }
        String clean = text.strip();
        if (clean.isEmpty()) return null;

        // words that stop the message outright (slurs and the like)
        if (!sender.hasPermission("ctf.chat.nofilter")) {
            String hit = firstHit(clean, words.getStringList("block"));
            if (hit != null) {
                plugin.messages().send(sender, "chat.blocked");
                alert(sender, "word", clean);
                return null;
            }
        }
        // links and server IPs
        if (on("block-links", true) && !sender.hasPermission("ctf.chat.links")) {
            if (hasLink(clean)) {
                plugin.messages().send(sender, "chat.no-links");
                alert(sender, "link", clean);
                return null;
            }
        }
        // the same message again straight away
        int repeat = plugin.getConfig().getInt("chat-moderation.repeat-seconds", 30);
        String key = normal(clean).replaceAll("[^a-z0-9]", "");
        if (repeat > 0 && !staff && !key.isEmpty()) {
            long now = System.currentTimeMillis();
            if (key.equals(lastText.get(sender.getUniqueId())) && now - lastTime.getOrDefault(sender.getUniqueId(), 0L) < repeat * 1000L) {
                plugin.messages().send(sender, "chat.repeat");
                return null;
            }
        }
        // aaaaaaaa / !!!!!!!! → aaaa / !!!!
        int flood = plugin.getConfig().getInt("chat-moderation.max-repeated-characters", 4);
        if (flood > 0) clean = clean.replaceAll("(.)\\1{" + flood + ",}", "$1".repeat(flood));
        // SHOUTING → shouting
        if (on("anti-caps", true) && !staff) clean = calmCaps(clean);
        // swear words → ****
        if (!sender.hasPermission("ctf.chat.nofilter")) clean = censor(clean, words.getStringList("censor"));

        lastText.put(sender.getUniqueId(), key);
        lastTime.put(sender.getUniqueId(), System.currentTimeMillis());
        return clean;
    }

    public void forget(UUID player) {
        lastText.remove(player);
        lastTime.remove(player);
    }

    /** A link or server address that isn't one of chat-moderation.allowed-links. */
    private boolean hasLink(String text) {
        String stripped = text;
        for (String allowed : plugin.getConfig().getStringList("chat-moderation.allowed-links")) {
            stripped = stripped.replaceAll("(?i)(https?://)?(www\\.)?" + Pattern.quote(allowed) + "\\S*", "");
        }
        return LINK.matcher(stripped).find() || IP.matcher(stripped).find();
    }

    private String calmCaps(String text) {
        int letters = 0, upper = 0;
        for (char c : text.toCharArray()) {
            if (Character.isLetter(c)) {
                letters++;
                if (Character.isUpperCase(c)) upper++;
            }
        }
        int min = plugin.getConfig().getInt("chat-moderation.caps-min-letters", 8);
        double max = plugin.getConfig().getDouble("chat-moderation.caps-max-percent", 70) / 100.0;
        if (letters < min || (double) upper / letters <= max) return text;
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.isEmpty() ? lower : Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    /** Each filtered word swapped for stars, as long as the word was. */
    String censor(String text, List<String> list) {
        StringBuilder out = new StringBuilder();
        Matcher matcher = WORD.matcher(text);
        int at = 0;
        char star = plugin.getConfig().getString("chat-moderation.censor-character", "*").charAt(0);
        while (matcher.find()) {
            out.append(text, at, matcher.start());
            String word = matcher.group();
            out.append(matches(normal(word), list) ? String.valueOf(star).repeat(word.length()) : word);
            at = matcher.end();
        }
        out.append(text.substring(at));
        // words spaced out letter by letter: "f u c k"
        Matcher spaced = Pattern.compile("(?<!\\S)(?:\\p{L}\\s){2,}\\p{L}(?!\\S)").matcher(out.toString());
        StringBuilder again = new StringBuilder();
        at = 0;
        String done = out.toString();
        while (spaced.find()) {
            again.append(done, at, spaced.start());
            String joined = spaced.group().replace(" ", "");
            again.append(matches(normal(joined), list) ? spaced.group().replaceAll("\\S", String.valueOf(star)) : spaced.group());
            at = spaced.end();
        }
        again.append(done.substring(at));
        return again.toString();
    }

    /** The first list entry the message hits, or null. */
    private String firstHit(String text, List<String> list) {
        Matcher matcher = WORD.matcher(text);
        while (matcher.find()) {
            String word = normal(matcher.group());
            for (String entry : list) if (matchesOne(word, entry)) return entry;
        }
        String joined = normal(text.replaceAll("[^\\p{L}\\p{N}@$!|]", ""));
        for (String entry : list) {
            String bare = entry.replace("*", "").replace("=", "");
            if (entry.startsWith("*") && !bare.isEmpty() && joined.contains(squash(bare))) return entry;
        }
        return null;
    }

    private static boolean matches(String word, List<String> list) {
        for (String entry : list) if (matchesOne(word, entry)) return true;
        return false;
    }

    private static boolean matchesOne(String word, String entry) {
        String e = entry.toLowerCase(Locale.ROOT).strip();
        if (e.isEmpty()) return false;
        if (e.startsWith("=")) return word.equals(squash(e.substring(1)));
        if (e.startsWith("*") && e.endsWith("*") && e.length() > 2) return word.contains(squash(e.substring(1, e.length() - 1)));
        return word.startsWith(squash(e.replace("*", "")));
    }

    /** Lower case, leetspeak undone, separators dropped, runs of one letter squashed to one. */
    static String normal(String word) {
        StringBuilder out = new StringBuilder();
        for (char c : word.toLowerCase(Locale.ROOT).toCharArray()) {
            char m = switch (c) {
                case '0' -> 'o';
                case '1', '!', '|' -> 'i';
                case '3' -> 'e';
                case '4', '@' -> 'a';
                case '5', '$' -> 's';
                case '7' -> 't';
                case '.', '_', '-' -> 0;
                default -> c;
            };
            if (m != 0) out.append(m);
        }
        return squash(out.toString());
    }

    private static String squash(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("(.)\\1+", "$1");
    }

    private void alert(Player sender, String why, String text) {
        plugin.getLogger().info("[Chat filter] " + sender.getName() + " (" + why + "): " + text);
        if (!on("alert-staff", true)) return;
        Component line = plugin.messages().component("chat.staff-alert", Messages.arg("player", sender.getName()), Messages.arg("why", why),
                Messages.arg("message", text));
        for (Player staff : Bukkit.getOnlinePlayers()) if (staff.hasPermission("ctf.chat.staff")) staff.sendMessage(line);
    }

    // ---------------------------------------------------------------- staff controls

    public boolean muted() { return muted; }

    public void mute(String by, String reason) {
        muted = true;
        muteReason = reason == null ? "" : reason;
        plugin.messages().broadcast(muteReason.isBlank() ? "chat.mute-on" : "chat.mute-on-reason", Messages.arg("player", by), Messages.arg("reason", muteReason));
    }

    public void unmute(String by) {
        muted = false;
        muteReason = "";
        plugin.messages().broadcast("chat.mute-off", Messages.arg("player", by));
    }

    public double slow() { return slow; }

    public void slow(String by, double seconds) {
        slow = Math.max(0, seconds);
        if (slow > 0) plugin.messages().broadcast("chat.slow-on", Messages.arg("player", by), Messages.arg("seconds", fmt(slow)));
        else plugin.messages().broadcast("chat.slow-off", Messages.arg("player", by));
    }

    /** Pushes chat off everyone's screen (staff keep theirs), then says who cleared it. */
    public void clear(String by) {
        Component blank = Component.text(" ");
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("ctf.chat.staff")) {
                plugin.messages().send(player, "chat.cleared-staff", Messages.arg("player", by));
                continue;
            }
            for (int i = 0; i < 120; i++) player.sendMessage(blank);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.hasPermission("ctf.chat.staff")) plugin.messages().send(player, "chat.cleared", Messages.arg("player", by));
        }
    }

    // ---------------------------------------------------------------- /chatfilter

    public List<String> list(String which) {
        return new ArrayList<>(words.getStringList(which));
    }

    public boolean add(String which, String entry) {
        List<String> list = list(which);
        String e = entry.toLowerCase(Locale.ROOT).strip();
        if (e.isEmpty() || list.contains(e)) return false;
        list.add(e);
        words.set(which, list);
        return save();
    }

    public boolean remove(String which, String entry) {
        List<String> list = list(which);
        if (!list.remove(entry.toLowerCase(Locale.ROOT).strip())) return false;
        words.set(which, list);
        return save();
    }

    /** What the filter would do with this text: the cleaned text, or why it would be stopped. */
    public String test(String text) {
        String block = firstHit(text, words.getStringList("block"));
        if (block != null) return "blocked (" + block + ")";
        if (hasLink(text)) return "blocked (link)";
        return censor(text, words.getStringList("censor"));
    }

    private boolean save() {
        try {
            words.save(file);
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("Couldn't save chat-filter.yml: " + e.getMessage());
            return false;
        }
    }

    private static String fmt(double seconds) {
        return seconds == Math.floor(seconds) ? String.valueOf((long) seconds) : String.format(Locale.ROOT, "%.1f", seconds);
    }
}
