/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** One choice in the hub's vote: a map and a mode it supports. */
public record VoteOption(String mapId, String mapName, Mode mode) {

    /**
     * Up to {@code count} options, each a different map where there are enough maps, with a random mode
     * the map supports. {@code maps} is map id → (name, playable modes).
     */
    public static List<VoteOption> pick(Map<String, Map.Entry<String, Set<Mode>>> maps, int count, String avoidMap, Random random) {
        List<String> ids = new ArrayList<>();
        for (Map.Entry<String, Map.Entry<String, Set<Mode>>> entry : maps.entrySet()) {
            if (!entry.getValue().getValue().isEmpty()) ids.add(entry.getKey());
        }
        Collections.shuffle(ids, random);
        // The map just played goes last, so it only comes up again when there aren't enough others.
        if (avoidMap != null && ids.remove(avoidMap)) ids.add(avoidMap);
        List<VoteOption> options = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (String id : ids) {
            if (options.size() >= count) break;
            options.add(option(id, maps.get(id), random));
            used.add(id);
        }
        // Fewer maps than slots: fill up with other modes on the same maps.
        for (String id : ids) {
            if (options.size() >= count) break;
            List<Mode> modes = new ArrayList<>(maps.get(id).getValue());
            Collections.shuffle(modes, random);
            for (Mode mode : modes) {
                if (options.size() >= count) break;
                VoteOption option = new VoteOption(id, maps.get(id).getKey(), mode);
                if (!options.contains(option)) options.add(option);
            }
        }
        return options;
    }

    private static VoteOption option(String id, Map.Entry<String, Set<Mode>> map, Random random) {
        List<Mode> modes = new ArrayList<>(map.getValue());
        return new VoteOption(id, map.getKey(), modes.get(random.nextInt(modes.size())));
    }

    /** The option with the most votes; ties go to a random one of the leaders. */
    public static int winner(int options, Map<UUID, Integer> votes, Random random) {
        int[] counts = new int[options];
        for (int vote : votes.values()) if (vote >= 0 && vote < options) counts[vote]++;
        int best = 0;
        for (int count : counts) best = Math.max(best, count);
        List<Integer> leaders = new ArrayList<>();
        for (int i = 0; i < options; i++) if (counts[i] == best) leaders.add(i);
        return leaders.get(random.nextInt(leaders.size()));
    }
}
