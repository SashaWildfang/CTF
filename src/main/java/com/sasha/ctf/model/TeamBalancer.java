/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Splits players into even teams, and picks the team a late joiner should go on. */
public final class TeamBalancer {

    private TeamBalancer() {}

    /** Shuffles the players and deals them out in turn, so team sizes differ by at most one. */
    public static Map<UUID, TeamColor> balance(Collection<UUID> players, List<TeamColor> teams, Random random) {
        List<UUID> order = new ArrayList<>(players);
        Collections.shuffle(order, random);
        Map<UUID, TeamColor> roster = new LinkedHashMap<>();
        for (int i = 0; i < order.size(); i++) roster.put(order.get(i), teams.get(i % teams.size()));
        return roster;
    }

    /** The team with the fewest players (the first such team in order on a tie). */
    public static TeamColor smallest(Map<UUID, TeamColor> current, List<TeamColor> teams) {
        Map<TeamColor, Integer> sizes = new EnumMap<>(TeamColor.class);
        for (TeamColor team : teams) sizes.put(team, 0);
        for (TeamColor team : current.values()) sizes.computeIfPresent(team, (t, n) -> n + 1);
        TeamColor best = teams.get(0);
        for (TeamColor team : teams) if (sizes.get(team) < sizes.get(best)) best = team;
        return best;
    }
}
