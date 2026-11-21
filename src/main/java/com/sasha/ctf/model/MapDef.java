/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.model;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One map: its world folder lives next to its map.yml in plugins/FractalCTF/maps/&lt;id&gt;/. Everything
 * a match needs to know about the map (spawns, flags, wool, the hill) is in here, set with /ctfmap.
 */
public final class MapDef {

    /** Spawn, flag stand and wool blocks for one team. */
    public static final class TeamPoints {
        public Spot spawn;
        public BlockPos flag;
        public final List<BlockPos> wools = new ArrayList<>();
    }

    private final String id;
    public String name;
    public final List<String> authors = new ArrayList<>();
    public final Set<Mode> modes = EnumSet.noneOf(Mode.class);
    public int timeLimitMinutes = 15;
    public int captureLimit = 3;
    public int kothPoints = 250;
    public int voidY = -64;
    public Spot spectatorSpawn;
    public BlockPos hillA;
    public BlockPos hillB;
    public final Map<TeamColor, TeamPoints> teams = new EnumMap<>(TeamColor.class);

    public MapDef(String id) {
        this.id = id;
        this.name = id;
    }

    public String id() { return id; }

    public TeamPoints team(TeamColor color) {
        return teams.computeIfAbsent(color, c -> new TeamPoints());
    }

    public Cuboid hill() {
        return hillA == null || hillB == null ? null : Cuboid.of(hillA, hillB);
    }

    /** What's still missing before this map can be played in the mode; empty = ready. */
    public List<String> missing(Mode mode) {
        List<String> missing = new ArrayList<>();
        for (TeamColor color : mode.teams()) {
            TeamPoints points = teams.get(color);
            if (points == null || points.spawn == null) missing.add(color.display() + " spawn");
            if (mode.flags() && (points == null || points.flag == null)) missing.add(color.display() + " flag");
            if (mode == Mode.DTW && (points == null || points.wools.isEmpty())) missing.add(color.display() + " wool");
        }
        if (mode == Mode.KOTH && hill() == null) missing.add("hill (both corners)");
        return missing;
    }

    /** The listed modes that have everything they need. */
    public Set<Mode> playableModes() {
        Set<Mode> playable = EnumSet.noneOf(Mode.class);
        for (Mode mode : modes) if (missing(mode).isEmpty()) playable.add(mode);
        return playable;
    }

    // ---------------------------------------------------------------- map.yml

    public static MapDef load(String id, YamlConfiguration yaml) {
        MapDef map = new MapDef(id);
        map.name = yaml.getString("name", id);
        map.authors.addAll(yaml.getStringList("authors"));
        for (String mode : yaml.getStringList("modes")) Mode.parse(mode).ifPresent(map.modes::add);
        map.timeLimitMinutes = Math.max(1, yaml.getInt("time-limit-minutes", 15));
        map.captureLimit = Math.max(1, yaml.getInt("capture-limit", 3));
        map.kothPoints = Math.max(10, yaml.getInt("koth-points", 250));
        map.voidY = yaml.getInt("void-y", -64);
        map.spectatorSpawn = Spot.parse(yaml.getString("spectator-spawn"));
        map.hillA = BlockPos.parse(yaml.getString("hill.corner-1"));
        map.hillB = BlockPos.parse(yaml.getString("hill.corner-2"));
        ConfigurationSection teams = yaml.getConfigurationSection("teams");
        if (teams != null) {
            for (String key : teams.getKeys(false)) {
                TeamColor color = TeamColor.parse(key).orElse(null);
                ConfigurationSection section = teams.getConfigurationSection(key);
                if (color == null || section == null) continue;
                TeamPoints points = map.team(color);
                points.spawn = Spot.parse(section.getString("spawn"));
                points.flag = BlockPos.parse(section.getString("flag"));
                for (String wool : section.getStringList("wools")) {
                    BlockPos pos = BlockPos.parse(wool);
                    if (pos != null) points.wools.add(pos);
                }
            }
        }
        return map;
    }

    public YamlConfiguration save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of(
                "FractalCTF map. Set these in game with /ctfmap (or by hand, then /ctfmap reload).",
                "modes: ctf, ctf4, koth, dtw. Positions are \"x y z\" (spawns add yaw and pitch)."));
        yaml.set("name", name);
        yaml.set("authors", authors);
        yaml.set("modes", modes.stream().map(Mode::id).toList());
        yaml.set("time-limit-minutes", timeLimitMinutes);
        yaml.set("capture-limit", captureLimit);
        yaml.set("koth-points", kothPoints);
        yaml.set("void-y", voidY);
        yaml.set("spectator-spawn", spectatorSpawn == null ? null : spectatorSpawn.serialize());
        yaml.set("hill.corner-1", hillA == null ? null : hillA.serialize());
        yaml.set("hill.corner-2", hillB == null ? null : hillB.serialize());
        for (Map.Entry<TeamColor, TeamPoints> entry : teams.entrySet()) {
            String path = "teams." + entry.getKey().id() + ".";
            TeamPoints points = entry.getValue();
            yaml.set(path + "spawn", points.spawn == null ? null : points.spawn.serialize());
            yaml.set(path + "flag", points.flag == null ? null : points.flag.serialize());
            yaml.set(path + "wools", points.wools.stream().map(BlockPos::serialize).toList());
        }
        return yaml;
    }
}
