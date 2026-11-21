/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.model;

import java.util.Map;
import java.util.UUID;

/**
 * The shared match row, as both servers read it.
 *
 * @param roster   who the hub sent and on which team (late joiners aren't in it)
 * @param score    a short score line for the hub's sidebar, e.g. "Red 2 - 1 Blue"
 * @param winner   the winning team at the end, or null for a draw
 * @param changed  when the row last changed (epoch millis), to spot a server that stopped mid-way
 */
public record MatchRow(MatchState state, String map, Mode mode, Map<UUID, TeamColor> roster,
                       String score, TeamColor winner, long changed) {

    public static MatchRow idle() {
        return new MatchRow(MatchState.IDLE, null, null, Map.of(), "", null, System.currentTimeMillis());
    }
}
