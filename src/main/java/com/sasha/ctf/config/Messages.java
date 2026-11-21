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
        this.prefix = Placeholder.parsed("prefix", yaml.getString("prefix", ""));
    }

    public static Messages load(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) plugin.saveResource("messages.yml", false);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        var bundled = plugin.getResource("messages.yml");
        if (bundled != null) {
            yaml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(bundled, StandardCharsets.UTF_8)));
        }
        return new Messages(yaml);
    }

    public static TagResolver arg(String key, Object value) {
        return Placeholder.unparsed(key, String.valueOf(value));
    }

    public static TagResolver comp(String key, Component value) {
        return Placeholder.component(key, value);
    }

    public static TagResolver parsed(String key, String miniMessage) {
        return Placeholder.parsed(key, miniMessage);
    }

    public String raw(String key) {
        return yaml.getString(key, "");
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
