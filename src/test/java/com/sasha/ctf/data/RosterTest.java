/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.data;

import com.sasha.ctf.model.TeamColor;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RosterTest {

    @Test
    void rosterRoundTrips() {
        Map<UUID, TeamColor> roster = new LinkedHashMap<>();
        for (TeamColor team : TeamColor.values()) roster.put(UUID.randomUUID(), team);
        assertEquals(roster, Database.decodeRoster(Database.encodeRoster(roster)));
    }

    @Test
    void brokenEntriesAreSkipped() {
        UUID good = UUID.randomUUID();
        Map<UUID, TeamColor> roster = Database.decodeRoster("nonsense;" + good + "=blue;" + UUID.randomUUID() + "=purple;=red");
        assertEquals(Map.of(good, TeamColor.BLUE), roster);
        assertTrue(Database.decodeRoster(null).isEmpty());
        assertTrue(Database.decodeRoster("").isEmpty());
    }
}
