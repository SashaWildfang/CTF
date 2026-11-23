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

    /**
     * The same, but players who asked for a team get it where that keeps the teams even: sizes still differ by at
     * most one. When more ask for a team than it can take, who gets it is luck; the rest are dealt out as usual.
     */
    public static Map<UUID, TeamColor> balance(Collection<UUID> players, List<TeamColor> teams, Random random, Map<UUID, TeamColor> wishes) {
        List<UUID> order = new ArrayList<>(players);
        Collections.shuffle(order, random);
        int base = order.size() / teams.size(), bigger = order.size() % teams.size();   // every team has `base`, and `bigger` of them one more
        Map<TeamColor, Integer> sizes = new EnumMap<>(TeamColor.class);
        for (TeamColor team : teams) sizes.put(team, 0);
        Map<UUID, TeamColor> roster = new LinkedHashMap<>();
        int atMost = 0;                                              // teams that already have base + 1
        for (UUID player : order) {
            TeamColor wish = wishes.get(player);
            if (wish == null || !sizes.containsKey(wish)) continue;
            int size = sizes.get(wish);
            if (size < base) {
                sizes.put(wish, size + 1);
            } else if (size == base && atMost < bigger) {
                sizes.put(wish, size + 1);
                atMost++;
            } else {
                continue;                                            // that team is full: dealt out below
            }
            roster.put(player, wish);
        }
        for (UUID player : order) {
            if (roster.containsKey(player)) continue;
            TeamColor least = teams.get(0);
            for (TeamColor team : teams) if (sizes.get(team) < sizes.get(least)) least = team;
            sizes.merge(least, 1, Integer::sum);
            roster.put(player, least);
        }
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
