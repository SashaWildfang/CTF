/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.config;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Everything players read, from messages.yml. Keys missing from the server's file come from the copy in
 * the jar, so new messages show up after an update without touching the file. Text is MiniMessage;
 * {@code <prefix>} is the CTF prefix.
 */
public final class Messages {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final YamlConfiguration yaml;
    private final TagResolver prefix;

    private Messages(YamlConfiguration yaml) {
        this.yaml = yaml;
        this.prefix = Placeholder.parsed("prefix", yaml.getString("prefix") == null ? "" : yaml.getString("prefix"));
    }

    public static Messages load(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) plugin.saveResource("messages.yml", false);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        var bundled = plugin.getResource("messages.yml");
        if (bundled != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(bundled, StandardCharsets.UTF_8));
            // An update changed what some messages say or which placeholders they use: the old file is
            // kept as messages-old.yml (to copy edits back from) and the new one is written.
            if (yaml.getInt("messages-version", 1) < defaults.getInt("messages-version", 1)) {
                File old = new File(plugin.getDataFolder(), "messages-old.yml");
                if (old.exists() && !old.delete()) plugin.getLogger().warning("Couldn't replace messages-old.yml");
                if (file.renameTo(old)) {
                    plugin.saveResource("messages.yml", true);
                    yaml = YamlConfiguration.loadConfiguration(file);
                    plugin.getLogger().info("messages.yml was updated for this version; your old one is messages-old.yml.");
                }
            }
            yaml.setDefaults(defaults);
        }
        return new Messages(yaml);
    }

    /** Money-like placeholders that are shown as 4,084 or 1.2M. */
    private static final java.util.Set<String> MONEY = java.util.Set.of("coins", "price", "earned", "missing");

    public static TagResolver arg(String key, Object value) {
        if (value instanceof Number number && MONEY.contains(key)) return Placeholder.unparsed(key, com.sasha.ctf.util.Numbers.coins(number.longValue()));
        return Placeholder.unparsed(key, String.valueOf(value));
    }

    public static TagResolver comp(String key, Component value) {
        return Placeholder.component(key, value);
    }

    public static TagResolver parsed(String key, String miniMessage) {
        return Placeholder.parsed(key, miniMessage);
    }

    /** A list of lines (e.g. kill message variants); a single string counts as a list of one. */
    public List<String> list(String key) {
        if (yaml.isList(key)) return yaml.getStringList(key);
        String one = raw(key);
        return one.isEmpty() ? List.of() : List.of(one);
    }

    public String raw(String key) {
        // getString(key, def) would skip the jar's defaults, so new keys must go through getString(key)
        String text = yaml.getString(key);
        return text == null ? "" : text;
    }

    public boolean empty(String key) {
        return yaml.isList(key) ? yaml.getStringList(key).isEmpty() : raw(key).isEmpty();
    }

    public Component component(String key, TagResolver... args) {
        if (yaml.isList(key)) {
            List<Component> lines = new ArrayList<>();
            for (String line : yaml.getStringList(key)) lines.add(parse(line, args));
            return Component.join(JoinConfiguration.newlines(), lines);
        }
        return parse(raw(key), args);
    }

    public List<Component> lines(String key, TagResolver... args) {
        List<Component> lines = new ArrayList<>();
        if (yaml.isList(key)) for (String line : yaml.getStringList(key)) lines.add(parse(line, args));
        else if (!raw(key).isEmpty()) lines.add(parse(raw(key), args));
        return lines;
    }

    public Component parse(String text, TagResolver... args) {
        return MINI.deserialize(text, TagResolver.resolver(TagResolver.resolver(args), prefix));
    }

    public void send(Audience audience, String key, TagResolver... args) {
        if (empty(key)) return;
        audience.sendMessage(component(key, args));
    }

    /** To everyone on this server (and the console). */
    public void broadcast(String key, TagResolver... args) {
        if (empty(key)) return;
        org.bukkit.Bukkit.getServer().sendMessage(component(key, args));
    }

    public void actionBar(Audience audience, String key, TagResolver... args) {
        if (empty(key)) return;
        audience.sendActionBar(component(key, args));
    }

    /** A title from "KEY.title" and "KEY.subtitle". */
    public void title(Audience audience, String key, int fadeIn, int stay, int fadeOut, TagResolver... args) {
        audience.showTitle(Title.title(component(key + ".title", args), component(key + ".subtitle", args),
                Title.Times.times(Duration.ofMillis(fadeIn * 50L), Duration.ofMillis(stay * 50L), Duration.ofMillis(fadeOut * 50L))));
    }
}
