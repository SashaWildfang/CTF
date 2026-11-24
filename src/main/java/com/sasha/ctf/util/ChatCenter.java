/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;

/** Centres chat lines in the default chat width (Java Edition's default font). */
public final class ChatCenter {

    /** Half the default chat width, in pixels. */
    private static final int CENTER = 154;

    private ChatCenter() {}

    public static Component center(Component line) {
        int width = width(line, Style.empty());
        if (width <= 0 || width >= CENTER * 2) return line;
        int pad = (CENTER - width / 2) / 4; // a space is 3 pixels plus 1 of spacing
        return pad <= 0 ? line : Component.text(" ".repeat(pad)).append(line);
    }

    /** How wide a line is in chat, in pixels (default font). */
    public static int pixels(Component line) {
        return width(line, Style.empty());
    }

    private static int width(Component component, Style parent) {
        Style style = parent.merge(component.style(), Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
        int width = 0;
        if (component instanceof TextComponent text) {
            boolean bold = component.style().decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE
                    || component.style().decoration(TextDecoration.BOLD) == TextDecoration.State.NOT_SET
                    && parent.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE;
            for (char c : text.content().toCharArray()) width += charWidth(c) + 1 + (bold && c != ' ' ? 1 : 0);
            style = bold ? style.decoration(TextDecoration.BOLD, true) : style;
        }
        for (Component child : component.children()) width += width(child, style);
        return width;
    }

    private static int charWidth(char c) {
        return switch (c) {
            case 'i', '!', ',', '.', ':', ';', '|', '\'' -> 1;
            case 'l', '`' -> 2;
            case 'I', 't', '[', ']', ' ', '"' -> 3;
            case 'f', 'k', '<', '>', '(', ')', '{', '}', '*' -> 4;
            case '@', '~' -> 6;
            case '★', '✖', '✔', '⚑', '●', '♛', '❤', '▪', '•' -> 7;
            default -> c > 0x2000 ? 7 : 5;
        };
    }
}
