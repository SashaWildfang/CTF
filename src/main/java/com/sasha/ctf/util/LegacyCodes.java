/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.util;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns old-style colour codes (&amp;7, &amp;l, &amp;#B266FF, &amp;x&amp;B&amp;2..., and the same with §) into
 * MiniMessage tags, so text can use either style, including LuckPerms prefixes.
 */
public final class LegacyCodes {

    private static final Pattern HEX_SHORT = Pattern.compile("[&§]#([0-9a-fA-F]{6})");
    private static final Pattern HEX_LONG = Pattern.compile(
            "[&§][xX]([&§][0-9a-fA-F]){6}");
    private static final Pattern CODE = Pattern.compile("[&§]([0-9a-fk-orA-FK-OR])");

    private static final Map<Character, String> TAGS = Map.ofEntries(
            Map.entry('0', "black"), Map.entry('1', "dark_blue"), Map.entry('2', "dark_green"),
            Map.entry('3', "dark_aqua"), Map.entry('4', "dark_red"), Map.entry('5', "dark_purple"),
            Map.entry('6', "gold"), Map.entry('7', "gray"), Map.entry('8', "dark_gray"),
            Map.entry('9', "blue"), Map.entry('a', "green"), Map.entry('b', "aqua"),
            Map.entry('c', "red"), Map.entry('d', "light_purple"), Map.entry('e', "yellow"),
            Map.entry('f', "white"), Map.entry('k', "obfuscated"), Map.entry('l', "bold"),
            Map.entry('m', "strikethrough"), Map.entry('n', "underlined"), Map.entry('o', "italic"),
            Map.entry('r', "reset"));

    private static final Pattern ANY_COLOUR = Pattern.compile("[&§](#[0-9a-fA-F]{6}|[0-9a-fA-F])");

    private LegacyCodes() {
    }

    /** The last &amp; colour (or &amp;#hex) in the text, or null if it has none. */
    public static TextColor lastColor(String text) {
        Matcher matcher = ANY_COLOUR.matcher(text);
        String last = null;
        while (matcher.find()) last = matcher.group(1);
        if (last == null) return null;
        if (last.startsWith("#")) return TextColor.fromHexString(last);
        return NamedTextColor.NAMES.value(TAGS.get(Character.toLowerCase(last.charAt(0))));
    }

    /** Returns the text with every legacy code replaced by its MiniMessage tag. Text without codes is unchanged. */
    public static String toMiniMessage(String text) {
        if (text.indexOf('&') < 0 && text.indexOf('§') < 0) return text;

        Matcher longHex = HEX_LONG.matcher(text);
        StringBuilder out = new StringBuilder();
        while (longHex.find()) {
            String digits = longHex.group().replaceAll("[&§xX]", "");
            longHex.appendReplacement(out, "<reset><#" + digits + ">");
        }
        longHex.appendTail(out);

        Matcher shortHex = HEX_SHORT.matcher(out.toString());
        out = new StringBuilder();
        while (shortHex.find()) shortHex.appendReplacement(out, "<reset><#" + shortHex.group(1) + ">");
        shortHex.appendTail(out);

        Matcher code = CODE.matcher(out.toString());
        out = new StringBuilder();
        while (code.find()) {
            char c = Character.toLowerCase(code.group(1).charAt(0));
            String tag = TAGS.get(c);
            // Like the old system, a colour code also clears bold/italic etc. that came before it.
            String replacement = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') ? "<reset><" + tag + ">" : "<" + tag + ">";
            code.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        code.appendTail(out);
        return out.toString();
    }
}
