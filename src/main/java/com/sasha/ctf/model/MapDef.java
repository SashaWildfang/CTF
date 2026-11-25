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
        /** Destroy the Wool: the plugin builds a wool tower on each of these pads (only in that mode). */
        public final List<BlockPos> monuments = new ArrayList<>();
        /** Destroy the Monument: the map's own monument blocks (obsidian, gold...), broken as they are. */
        public final List<BlockPos> monumentBlocks = new ArrayList<>();
    }

    /**
     * Capture the Wool: one wool to bring home. {@code team} must take wool of {@code colour} (a dye colour, e.g.
     * "magenta") from the wool room and place it on {@code podium}.
     */
    public record WoolGoal(String name, TeamColor team, String colour, BlockPos podium, List<BlockPos> sources) {
        public org.bukkit.Material material() {
            org.bukkit.Material m = org.bukkit.Material.matchMaterial(colour.toUpperCase(java.util.Locale.ROOT).replace(' ', '_') + "_WOOL");
            return m == null ? org.bukkit.Material.WHITE_WOOL : m;
        }
    }

    private final String id;
    public String name;
    public final List<String> authors = new ArrayList<>();
    /** Who first built the map (ported maps: shown as "Original build by ..."). */
    public final List<String> originalAuthors = new ArrayList<>();
    /** Capture the Wool goals. */
    public final List<WoolGoal> woolGoals = new ArrayList<>();
    /** Destroy the Monument: blocks to break before a team is out (0 = game.wool-to-break). */
    public int blocksToBreak = 0;
    /** Nobody may place blocks above this height (0 = no limit). */
    public int buildHeight = 0;
    public final Set<Mode> modes = EnumSet.noneOf(Mode.class);
    public int timeLimitMinutes = 15;
    public int captureLimit = 3;
    public int kothPoints = 100;
    public int voidY = -64;
    /** How tall each wool tower is (blocks of wool). */
    public int woolLayers = 5;
    /** The material shown for the map in the vote menu. */
    public String icon = "FILLED_MAP";
    public Spot spectatorSpawn;
    public BlockPos hillA;
    public BlockPos hillB;
    /** King of the Hill points (a map can have one or several, e.g. left, middle and right). */
    public final List<HillDef> hills = new ArrayList<>();
    /** Seconds a team must stand on a point alone to capture it. */
    public int captureSeconds = 8;

    /** A hill: its zone (two corners) and, optionally, where its beacon goes (default: the middle of its floor). */
    public record HillDef(String name, BlockPos a, BlockPos b, BlockPos beacon) {
        public HillDef(String name, BlockPos a, BlockPos b) {
            this(name, a, b, null);
        }

        public Cuboid area() {
            return Cuboid.of(a, b);
        }
    }
    public final Map<TeamColor, TeamPoints> teams = new EnumMap<>(TeamColor.class);

    public MapDef(String id) {
        this.id = id;
        this.name = id;
    }

    public String id() { return id; }

    /** The credit line: the authors, then "Original build by ..." for ported maps. */
    public String credit() {
        String by = String.join(", ", authors);
        if (originalAuthors.isEmpty()) return by;
        String original = "Original build by " + String.join(", ", originalAuthors);
        return by.isEmpty() ? original : by + " · " + original;
    }

    public TeamPoints team(TeamColor color) {
        return teams.computeIfAbsent(color, c -> new TeamPoints());
    }

    public Cuboid hill() {
        return hillA == null || hillB == null ? null : Cuboid.of(hillA, hillB);
    }

    /** The map's hills: the "hills" list, or the single hill from the two corners. */
    public List<HillDef> allHills() {
        if (!hills.isEmpty()) return hills;
        return hill() == null ? List.of() : List.of(new HillDef("Hill", hillA, hillB));
    }

    /** What's still missing before this map can be played in the mode; empty = ready. */
    public List<String> missing(Mode mode) {
        List<String> missing = new ArrayList<>();
        for (TeamColor color : mode.teams()) {
            TeamPoints points = teams.get(color);
            if (points == null || points.spawn == null) missing.add(color.display() + " spawn");
            if (mode.flags() && (points == null || points.flag == null)) missing.add(color.display() + " flag");
            if (mode == Mode.DTW && (points == null || points.wools.isEmpty() && points.monuments.isEmpty())) missing.add(color.display() + " wool");
            if (mode == Mode.DTM && (points == null || points.monumentBlocks.isEmpty())) missing.add(color.display() + " monument");
            if (mode == Mode.CTW && woolGoals.stream().noneMatch(g -> g.team() == color)) missing.add(color.display() + " wool goal");
        }
        if (mode == Mode.KOTH && allHills().isEmpty()) missing.add("hill (both corners)");
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
        map.originalAuthors.addAll(yaml.getStringList("original-authors"));
        map.blocksToBreak = Math.max(0, yaml.getInt("blocks-to-break", 0));
        map.buildHeight = yaml.getInt("build-height", 0);
        for (Map<?, ?> raw : yaml.getMapList("ctw-wools")) {
            TeamColor team = TeamColor.parse(String.valueOf(raw.get("team"))).orElse(null);
            BlockPos podium = BlockPos.parse(String.valueOf(raw.get("podium")));
            Object colour = raw.get("colour");
            if (team == null || podium == null || colour == null) continue;
            Object name = raw.get("name");
            List<BlockPos> sources = new ArrayList<>();
            if (raw.get("sources") instanceof List<?> list) for (Object o : list) {
                BlockPos pos = BlockPos.parse(String.valueOf(o));
                if (pos != null) sources.add(pos);
            }
            map.woolGoals.add(new WoolGoal(name == null ? String.valueOf(colour) : String.valueOf(name), team, String.valueOf(colour), podium, sources));
        }
        for (String mode : yaml.getStringList("modes")) Mode.parse(mode).ifPresent(map.modes::add);
        map.timeLimitMinutes = Math.max(1, yaml.getInt("time-limit-minutes", 15));
        map.captureLimit = Math.max(1, yaml.getInt("capture-limit", 3));
        map.kothPoints = Math.max(10, yaml.getInt("koth-points", 100));
        map.voidY = yaml.getInt("void-y", -64);
        map.woolLayers = Math.max(3, Math.min(8, yaml.getInt("wool-height", 5)));
        map.icon = yaml.getString("icon", "FILLED_MAP");
        map.spectatorSpawn = Spot.parse(yaml.getString("spectator-spawn"));
        map.hillA = BlockPos.parse(yaml.getString("hill.corner-1"));
        map.hillB = BlockPos.parse(yaml.getString("hill.corner-2"));
        map.captureSeconds = Math.max(2, Math.min(60, yaml.getInt("koth-capture-seconds", 8)));
        for (Map<?, ?> raw : yaml.getMapList("hills")) {
            BlockPos a = BlockPos.parse(String.valueOf(raw.get("corner-1"))), b = BlockPos.parse(String.valueOf(raw.get("corner-2")));
            if (a == null || b == null) continue;
            Object name = raw.get("name");
            map.hills.add(new HillDef(name == null ? String.valueOf((char) ('A' + map.hills.size())) : String.valueOf(name), a, b,
                    raw.get("beacon") == null ? null : BlockPos.parse(String.valueOf(raw.get("beacon")))));
        }
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
                for (String pad : section.getStringList("monuments")) {
                    BlockPos pos = BlockPos.parse(pad);
                    if (pos != null) points.monuments.add(pos);
                }
                for (String block : section.getStringList("monument-blocks")) {
                    BlockPos pos = BlockPos.parse(block);
                    if (pos != null) points.monumentBlocks.add(pos);
                }
            }
        }
        return map;
    }

    public YamlConfiguration save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of(
                "FractalCTF map. Set these in game with /ctfmap (or by hand, then /ctfmap reload).",
                "modes: ctf, ctf4, koth, dtw, dtm, ctw. Positions are \"x y z\" (spawns add yaw and pitch)."));
        yaml.set("name", name);
        yaml.set("authors", authors);
        if (!originalAuthors.isEmpty()) yaml.set("original-authors", originalAuthors);
        if (blocksToBreak > 0) yaml.set("blocks-to-break", blocksToBreak);
        if (buildHeight != 0) yaml.set("build-height", buildHeight);
        if (!woolGoals.isEmpty()) {
            List<Map<String, Object>> goals = new ArrayList<>();
            for (WoolGoal goal : woolGoals) {
                Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("name", goal.name());
                m.put("team", goal.team().id());
                m.put("colour", goal.colour());
                m.put("podium", goal.podium().serialize());
                m.put("sources", goal.sources().stream().map(BlockPos::serialize).toList());
                goals.add(m);
            }
            yaml.set("ctw-wools", goals);
        }
        yaml.set("modes", modes.stream().map(Mode::id).toList());
        yaml.set("time-limit-minutes", timeLimitMinutes);
        yaml.set("capture-limit", captureLimit);
        yaml.set("koth-points", kothPoints);
        yaml.set("void-y", voidY);
        yaml.set("wool-height", woolLayers);
        yaml.set("icon", icon);
        yaml.set("spectator-spawn", spectatorSpawn == null ? null : spectatorSpawn.serialize());
        yaml.set("hill.corner-1", hillA == null ? null : hillA.serialize());
        yaml.set("hill.corner-2", hillB == null ? null : hillB.serialize());
        yaml.set("koth-capture-seconds", captureSeconds);
        if (!hills.isEmpty()) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (HillDef hill : hills) {
                Map<String, Object> entry = new java.util.LinkedHashMap<>();
                entry.put("name", hill.name());
                entry.put("corner-1", hill.a().serialize());
                entry.put("corner-2", hill.b().serialize());
                if (hill.beacon() != null) entry.put("beacon", hill.beacon().serialize());
                list.add(entry);
            }
            yaml.set("hills", list);
        }
        for (Map.Entry<TeamColor, TeamPoints> entry : teams.entrySet()) {
            String path = "teams." + entry.getKey().id() + ".";
            TeamPoints points = entry.getValue();
            yaml.set(path + "spawn", points.spawn == null ? null : points.spawn.serialize());
            yaml.set(path + "flag", points.flag == null ? null : points.flag.serialize());
            yaml.set(path + "wools", points.wools.stream().map(BlockPos::serialize).toList());
            yaml.set(path + "monuments", points.monuments.stream().map(BlockPos::serialize).toList());
            if (!points.monumentBlocks.isEmpty()) yaml.set(path + "monument-blocks", points.monumentBlocks.stream().map(BlockPos::serialize).toList());
        }
        return yaml;
    }
}
