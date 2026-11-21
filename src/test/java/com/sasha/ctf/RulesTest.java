/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf;

import com.sasha.ctf.model.BlockPos;
import com.sasha.ctf.model.Cuboid;
import com.sasha.ctf.model.MapDef;
import com.sasha.ctf.model.Mode;
import com.sasha.ctf.model.Spot;
import com.sasha.ctf.model.TeamBalancer;
import com.sasha.ctf.model.TeamColor;
import com.sasha.ctf.model.VoteOption;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RulesTest {

    @Test
    void balancedTeamsDifferByAtMostOne() {
        for (int players = 1; players <= 17; players++) {
            for (Mode mode : Mode.values()) {
                List<UUID> ids = new ArrayList<>();
                for (int i = 0; i < players; i++) ids.add(UUID.randomUUID());
                Map<UUID, TeamColor> roster = TeamBalancer.balance(ids, mode.teams(), new Random(players));
                assertEquals(players, roster.size());
                Map<TeamColor, Integer> sizes = new EnumMap<>(TeamColor.class);
                for (TeamColor team : mode.teams()) sizes.put(team, 0);
                for (TeamColor team : roster.values()) {
                    assertTrue(mode.teams().contains(team), "only the mode's teams");
                    sizes.merge(team, 1, Integer::sum);
                }
                int min = sizes.values().stream().mapToInt(i -> i).min().orElse(0);
                int max = sizes.values().stream().mapToInt(i -> i).max().orElse(0);
                assertTrue(max - min <= 1, "teams within one player for " + players + " in " + mode);
            }
        }
    }

    @Test
    void lateJoinerGoesToSmallestTeam() {
        Map<UUID, TeamColor> current = new LinkedHashMap<>();
        current.put(UUID.randomUUID(), TeamColor.RED);
        current.put(UUID.randomUUID(), TeamColor.RED);
        current.put(UUID.randomUUID(), TeamColor.BLUE);
        assertEquals(TeamColor.BLUE, TeamBalancer.smallest(current, Mode.CTF.teams()));
        assertEquals(TeamColor.GREEN, TeamBalancer.smallest(current, Mode.CTF4.teams()));
        assertEquals(TeamColor.RED, TeamBalancer.smallest(Map.of(), Mode.CTF.teams()));
    }

    @Test
    void voteOptionsUseDifferentMapsAndPlayableModes() {
        Map<String, Map.Entry<String, Set<Mode>>> maps = new HashMap<>();
        maps.put("frost", Map.entry("Frostbite", EnumSet.of(Mode.CTF, Mode.KOTH)));
        maps.put("canyon", Map.entry("Canyon", EnumSet.of(Mode.DTW)));
        maps.put("castle", Map.entry("Castle", EnumSet.of(Mode.CTF4)));
        maps.put("broken", Map.entry("Not ready", EnumSet.noneOf(Mode.class)));
        for (int seed = 0; seed < 50; seed++) {
            List<VoteOption> options = VoteOption.pick(maps, 3, null, new Random(seed));
            assertEquals(3, options.size());
            Set<String> ids = new HashSet<>();
            for (VoteOption option : options) {
                assertTrue(ids.add(option.mapId()), "no map twice when there are enough maps");
                assertTrue(maps.get(option.mapId()).getValue().contains(option.mode()), "only modes the map supports");
                assertFalse(option.mapId().equals("broken"));
            }
        }
    }

    @Test
    void voteFillsUpWithModesWhenFewMaps() {
        Map<String, Map.Entry<String, Set<Mode>>> maps = Map.of("frost", Map.entry("Frostbite", EnumSet.of(Mode.CTF, Mode.KOTH, Mode.DTW)));
        List<VoteOption> options = VoteOption.pick(maps, 3, null, new Random(1));
        assertEquals(3, options.size());
        assertEquals(3, new HashSet<>(options).size(), "three different modes");
    }

    @Test
    void lastMapGoesLastInTheVote() {
        Map<String, Map.Entry<String, Set<Mode>>> maps = new HashMap<>();
        for (String id : List.of("a", "b", "c", "d")) maps.put(id, Map.entry(id, EnumSet.of(Mode.CTF)));
        for (int seed = 0; seed < 30; seed++) {
            for (VoteOption option : VoteOption.pick(maps, 3, "a", new Random(seed))) assertFalse(option.mapId().equals("a"));
        }
    }

    @Test
    void mostVotesWins() {
        Map<UUID, Integer> votes = new HashMap<>();
        votes.put(UUID.randomUUID(), 2);
        votes.put(UUID.randomUUID(), 2);
        votes.put(UUID.randomUUID(), 0);
        assertEquals(2, VoteOption.winner(3, votes, new Random()));
        // No votes: any option can win, but it's always a real one.
        for (int seed = 0; seed < 20; seed++) {
            int winner = VoteOption.winner(3, Map.of(), new Random(seed));
            assertTrue(winner >= 0 && winner < 3);
        }
    }

    @Test
    void mapSaysWhatIsMissing() {
        MapDef map = new MapDef("frost");
        map.modes.addAll(EnumSet.allOf(Mode.class));
        assertTrue(map.playableModes().isEmpty());
        map.team(TeamColor.RED).spawn = new Spot(0, 64, 0, 0, 0);
        map.team(TeamColor.BLUE).spawn = new Spot(50, 64, 0, 180, 0);
        assertTrue(map.missing(Mode.CTF).contains("Red flag"));
        map.team(TeamColor.RED).flag = new BlockPos(2, 64, 0);
        map.team(TeamColor.BLUE).flag = new BlockPos(48, 64, 0);
        assertTrue(map.missing(Mode.CTF).isEmpty());
        assertFalse(map.missing(Mode.CTF4).isEmpty(), "4-team needs green and yellow too");
        assertTrue(map.missing(Mode.KOTH).contains("hill (both corners)"));
        map.hillA = new BlockPos(20, 64, -5);
        map.hillB = new BlockPos(30, 70, 5);
        map.team(TeamColor.RED).wools.add(new BlockPos(1, 65, 3));
        map.team(TeamColor.BLUE).wools.add(new BlockPos(49, 65, 3));
        assertEquals(EnumSet.of(Mode.CTF, Mode.KOTH, Mode.DTW), map.playableModes());
    }

    @Test
    void mapSurvivesSaveAndLoad() throws Exception {
        MapDef map = new MapDef("frost");
        map.name = "Frostbite";
        map.authors.add("Sasha");
        map.modes.add(Mode.CTF);
        map.modes.add(Mode.DTW);
        map.captureLimit = 5;
        map.voidY = 10;
        map.spectatorSpawn = new Spot(1.5, 80, 2.5, 90, 10);
        map.team(TeamColor.RED).spawn = new Spot(0.5, 64, 0.5, -90, 0);
        map.team(TeamColor.RED).flag = new BlockPos(2, 64, 0);
        map.team(TeamColor.RED).wools.add(new BlockPos(3, 65, 4));
        map.hillA = new BlockPos(1, 2, 3);
        map.hillB = new BlockPos(4, 5, 6);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(map.save().saveToString());
        MapDef loaded = MapDef.load("frost", yaml);
        assertEquals("Frostbite", loaded.name);
        assertEquals(List.of("Sasha"), loaded.authors);
        assertEquals(EnumSet.of(Mode.CTF, Mode.DTW), loaded.modes);
        assertEquals(5, loaded.captureLimit);
        assertEquals(10, loaded.voidY);
        assertEquals(new BlockPos(2, 64, 0), loaded.teams.get(TeamColor.RED).flag);
        assertEquals(List.of(new BlockPos(3, 65, 4)), loaded.teams.get(TeamColor.RED).wools);
        assertEquals(-90f, loaded.teams.get(TeamColor.RED).spawn.yaw());
        assertNotNull(loaded.hill());
        assertEquals(90f, loaded.spectatorSpawn.yaw());
    }

    @Test
    void hillContainsWholeBlocks() {
        Cuboid hill = Cuboid.of(new BlockPos(10, 64, 10), new BlockPos(5, 66, 5));
        assertTrue(hill.contains(5.0, 64.0, 5.0));
        assertTrue(hill.contains(10.9, 66.9, 10.9));
        assertFalse(hill.contains(11.0, 65, 8));
        assertFalse(hill.contains(8, 63.9, 8));
    }

    @Test
    void positionsParse() {
        assertEquals(new BlockPos(-3, 70, 12), BlockPos.parse("-3 70 12"));
        assertEquals(new BlockPos(1, 2, 3), BlockPos.parse("1,2,3"));
        assertNull(BlockPos.parse("1 2"));
        assertNull(BlockPos.parse("a b c"));
        Spot spot = Spot.parse("1.5 64 -2.5 90 -10");
        assertNotNull(spot);
        assertEquals(-10f, spot.pitch());
        assertEquals(spot, Spot.parse(spot.serialize()));
    }

    @Test
    void modesAndTeamsParse() {
        assertEquals(Mode.CTF4, Mode.parse("ctf4").orElseThrow());
        assertEquals(Mode.KOTH, Mode.parse(" KOTH ").orElseThrow());
        assertTrue(Mode.parse("tdm").isEmpty());
        assertEquals(TeamColor.YELLOW, TeamColor.parse("yellow").orElseThrow());
        assertEquals(4, Mode.CTF4.teams().size());
        assertEquals(List.of(TeamColor.RED, TeamColor.BLUE), Mode.DTW.teams());
    }
}
