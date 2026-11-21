/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.model;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The game modes. Each map lists which of these it supports in its map.yml. */
public enum Mode {
    CTF("ctf", "Capture the Flag", 2),
    CTF4("ctf4", "4-Team CTF", 4),
    KOTH("koth", "King of the Hill", 2),
    DTW("dtw", "Destroy the Wool", 2);

    private final String id;
    private final String display;
    private final int teams;

    Mode(String id, String display, int teams) {
        this.id = id;
        this.display = display;
        this.teams = teams;
    }

    public String id() { return id; }
    public String display() { return display; }
    public int teamCount() { return teams; }

    /** The teams playing this mode, in order (red, blue, then green and yellow for 4-team). */
    public List<TeamColor> teams() {
        return Arrays.asList(TeamColor.values()).subList(0, teams);
    }

    public boolean flags() {
        return this == CTF || this == CTF4;
    }

    public static Optional<Mode> parse(String input) {
        if (input == null) return Optional.empty();
        String key = input.trim().toLowerCase(Locale.ROOT);
        for (Mode mode : values()) {
            if (mode.id.equals(key) || mode.name().equalsIgnoreCase(key)) return Optional.of(mode);
        }
        return Optional.empty();
    }
}
