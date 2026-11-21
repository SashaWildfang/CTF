/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.model;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Material;

import java.util.Locale;
import java.util.Optional;

/** The four team colours. Two-team modes use red and blue; 4-Team CTF adds green and yellow. */
public enum TeamColor {
    RED("Red", NamedTextColor.RED, Color.fromRGB(0xB02E26), Material.RED_WOOL, Material.RED_BANNER, Material.RED_CONCRETE),
    BLUE("Blue", NamedTextColor.BLUE, Color.fromRGB(0x3C44AA), Material.BLUE_WOOL, Material.BLUE_BANNER, Material.BLUE_CONCRETE),
    GREEN("Green", NamedTextColor.GREEN, Color.fromRGB(0x5E7C16), Material.LIME_WOOL, Material.LIME_BANNER, Material.LIME_CONCRETE),
    YELLOW("Yellow", NamedTextColor.YELLOW, Color.fromRGB(0xFED83D), Material.YELLOW_WOOL, Material.YELLOW_BANNER, Material.YELLOW_CONCRETE);

    private final String display;
    private final NamedTextColor text;
    private final Color leather;
    private final Material wool;
    private final Material banner;
    private final Material block;

    TeamColor(String display, NamedTextColor text, Color leather, Material wool, Material banner, Material block) {
        this.display = display;
        this.text = text;
        this.leather = leather;
        this.wool = wool;
        this.banner = banner;
        this.block = block;
    }

    public String display() { return display; }
    public NamedTextColor text() { return text; }
    public Color leather() { return leather; }
    public Material wool() { return wool; }
    public Material banner() { return banner; }
    /** Building blocks handed out with every kit, in the team colour. */
    public Material block() { return block; }
    public String id() { return name().toLowerCase(Locale.ROOT); }

    public static Optional<TeamColor> parse(String input) {
        if (input == null) return Optional.empty();
        for (TeamColor team : values()) {
            if (team.name().equalsIgnoreCase(input) || team.display.equalsIgnoreCase(input)) return Optional.of(team);
        }
        return Optional.empty();
    }
}
