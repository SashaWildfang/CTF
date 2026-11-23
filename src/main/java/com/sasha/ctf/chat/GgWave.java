/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GG waves (FractalGG on the proxy): while one is on, every "gg" in chat comes out bold in a random gradient.
 * The proxy says when a wave starts (and for how long) and when it ends, over the fractal:ggwave channel.
 */
public final class GgWave implements PluginMessageListener {

    public static final String CHANNEL = "fractal:ggwave";
    private static final Pattern GG = Pattern.compile("(?i)(?<![a-z0-9])g{2,}s?(?![a-z0-9])");
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private volatile long until;

    public void register(Plugin plugin) {
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
    }

    public void unregister(Plugin plugin) {
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL, this);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) return;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            String what = in.readUTF();
            int seconds = in.readInt();
            until = "start".equals(what) ? System.currentTimeMillis() + seconds * 1000L : 0;
        } catch (IOException e) {
            // not ours
        }
    }

    public boolean active() {
        return System.currentTimeMillis() < until;
    }

    /** The typed text, painted as usual, with each "gg" in a random gradient while a wave is on. */
    public Component paint(String text, Function<String, Component> plain) {
        if (!active()) return plain.apply(text);
        Matcher matcher = GG.matcher(text);
        if (!matcher.find()) return plain.apply(text);
        TextComponent.Builder out = Component.text();
        int last = 0;
        do {
            if (matcher.start() > last) out.append(plain.apply(text.substring(last, matcher.start())));
            out.append(MM.deserialize("<bold>" + gradient() + MM.escapeTags(matcher.group()) + "</gradient></bold>"));
            last = matcher.end();
        } while (matcher.find());
        if (last < text.length()) out.append(plain.apply(text.substring(last)));
        return out.build();
    }

    private static String gradient() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        float hue = random.nextFloat();
        int a = java.awt.Color.HSBtoRGB(hue, 0.75f, 1f) & 0xFFFFFF;
        int b = java.awt.Color.HSBtoRGB((hue + 0.18f + random.nextFloat() * 0.3f) % 1f, 0.75f, 1f) & 0xFFFFFF;
        int c = java.awt.Color.HSBtoRGB((hue + 0.5f + random.nextFloat() * 0.2f) % 1f, 0.6f, 1f) & 0xFFFFFF;
        return random.nextBoolean() ? String.format("<gradient:#%06X:#%06X>", a, b) : String.format("<gradient:#%06X:#%06X:#%06X>", a, b, c);
    }
}
