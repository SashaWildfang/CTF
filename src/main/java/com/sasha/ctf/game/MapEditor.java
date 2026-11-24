/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.game;

import com.sasha.ctf.FractalCTF;
import com.sasha.ctf.config.Messages;
import com.sasha.ctf.model.BlockPos;
import com.sasha.ctf.model.MapDef;
import com.sasha.ctf.model.Mode;
import com.sasha.ctf.model.Spot;
import com.sasha.ctf.model.TeamColor;
import com.sasha.ctf.util.WorldFiles;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * /ctfmap: setting maps up in game on the game server. "edit" opens a copy of the map's world; the set
 * commands record spawns, flags, wool and the hill where you stand or look; "save" writes map.yml and
 * tells the hub. "close save" also copies any building you did back into the map.
 */
public final class MapEditor {

    private static final String WORLD_PREFIX = "ctf_edit_";

    private final FractalCTF plugin;
    private final Map<UUID, String> editing = new HashMap<>();

    private final MapMirror mirror;

    public MapEditor(FractalCTF plugin) {
        this.plugin = plugin;
        this.mirror = new MapMirror(plugin);
        Bukkit.getPluginManager().registerEvents(mirror, plugin);
    }

    public static final List<String> SUBCOMMANDS = List.of("list", "info", "create", "import", "edit", "setspawn", "setflag",
            "addwool", "clearwool", "sethill", "addhill", "clearhills", "capturetime", "modes", "name", "authors", "timelimit", "caps", "kothpoints", "voidy", "save", "close", "reload", "mirror");

    public void run(CommandSender sender, String[] args) {
        Messages messages = plugin.messages();
        if (args.length == 0) {
            messages.send(sender, "editor.help");
            return;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "list" -> list(sender);
            case "reload" -> {
                plugin.maps().reload();
                messages.send(sender, "editor.reloaded", Messages.arg("count", plugin.maps().all().size()));
            }
            case "info" -> {
                MapDef map = args.length > 1 ? plugin.maps().get(args[1].toLowerCase()).orElse(null) : current(sender);
                if (map == null) messages.send(sender, "editor.no-map");
                else info(sender, map);
            }
            case "import" -> {
                if (args.length < 3) messages.send(sender, "editor.usage-import");
                else importWorld(sender, args[1], args[2].toLowerCase());
            }
            default -> {
                if (!(sender instanceof Player player)) {
                    messages.send(sender, "general.players-only");
                    return;
                }
                player(player, sub, Arrays.copyOfRange(args, 1, args.length));
            }
        }
    }

    private void player(Player player, String sub, String[] args) {
        Messages messages = plugin.messages();
        switch (sub) {
            case "create" -> {
                if (args.length < 1) messages.send(player, "editor.usage-create");
                else create(player, args[0].toLowerCase());
            }
            case "edit" -> {
                if (args.length < 1) messages.send(player, "editor.usage-edit");
                else edit(player, args[0].toLowerCase());
            }
            case "close" -> {
                mirror.off(player);
                close(player, args.length > 0 && args[0].equalsIgnoreCase("save"));
            }
            case "mirror" -> {
                // only while editing a map: what you build or break is copied to the other base(s)
                MapDef map = current(player);
                if (map == null) {
                    messages.send(player, "editor.mirror-not-editing");
                } else if (mirror.active(player) && !(args.length > 0 && args[0].equalsIgnoreCase("on"))) {
                    mirror.off(player);
                    messages.send(player, "editor.mirror-off");
                } else if (!(args.length > 0 && args[0].equalsIgnoreCase("off"))) {
                    String how = mirror.on(player, map, player.getWorld());
                    if (how == null) messages.send(player, "editor.mirror-none");
                    else messages.send(player, "editor.mirror-on", Messages.arg("how", how));
                } else {
                    messages.send(player, "editor.mirror-off");
                }
            }
            default -> {
                MapDef map = current(player);
                if (map == null) {
                    messages.send(player, "editor.not-editing");
                    return;
                }
                set(player, map, sub, args);
            }
        }
    }

    // ---------------------------------------------------------------- opening and closing

    private void create(Player player, String id) {
        Messages messages = plugin.messages();
        if (!id.matches("[a-z0-9_-]{1,64}")) {
            messages.send(player, "editor.bad-id");
            return;
        }
        Path folder = plugin.maps().folder(id);
        if (Files.exists(folder)) {
            messages.send(player, "editor.exists", Messages.arg("map", id));
            return;
        }
        World world = new WorldCreator(WORLD_PREFIX + id).generator(new VoidGenerator()).createWorld();
        if (world == null) return;
        world.getBlockAt(0, 64, 0).setType(Material.GLASS);
        world.setSpawnLocation(0, 65, 0);
        world.save();
        try {
            Path saved = world.getWorldFolder().toPath();
            if (WorldFiles.isWorld(saved)) {
                WorldFiles.copy(saved, folder);
            } else {
                // Paper 26 keeps the new world's chunks under world/dimensions/: give the map a level.dat and its chunks
                Files.createDirectories(folder);
                Path level = WorldFiles.levelDat(saved);
                if (level != null) Files.copy(level, folder.resolve("level.dat"));
                WorldFiles.storeChunks(saved, folder);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Couldn't create map " + id, e);
            return;
        }
        MapDef map = new MapDef(id);
        plugin.maps().put(map);
        plugin.maps().save(map);
        open(player, map, world);
        messages.send(player, "editor.created", Messages.arg("map", id));
    }

    private void importWorld(CommandSender sender, String worldFolder, String id) {
        Messages messages = plugin.messages();
        Path source = Bukkit.getWorldContainer().toPath().resolve(worldFolder);
        if (!id.matches("[a-z0-9_-]{1,64}") || worldFolder.contains("..")) {
            messages.send(sender, "editor.bad-id");
            return;
        }
        if (!WorldFiles.isWorld(source)) {
            messages.send(sender, "editor.no-world", Messages.arg("folder", worldFolder));
            return;
        }
        if (Bukkit.getWorld(worldFolder) != null) {
            messages.send(sender, "editor.world-loaded", Messages.arg("folder", worldFolder));
            return;
        }
        Path target = plugin.maps().folder(id);
        if (Files.exists(target)) {
            messages.send(sender, "editor.exists", Messages.arg("map", id));
            return;
        }
        try {
            WorldFiles.copy(source, target);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Couldn't import " + worldFolder, e);
            return;
        }
        plugin.maps().reload();
        messages.send(sender, "editor.imported", Messages.arg("folder", worldFolder), Messages.arg("map", id));
    }

    private void edit(Player player, String id) {
        Messages messages = plugin.messages();
        MapDef map = plugin.maps().get(id).orElse(null);
        if (map == null) {
            messages.send(player, "editor.unknown-map", Messages.arg("map", id));
            return;
        }
        World world = Bukkit.getWorld(WORLD_PREFIX + id);
        if (world == null) {
            Path target = Bukkit.getWorldContainer().toPath().resolve(WORLD_PREFIX + id);
            Path dimensions = WorldFiles.dimensionsFolder(Bukkit.getWorlds().get(0).getWorldFolder().toPath());
            try {
                WorldFiles.delete(target);
                // an edit copy left behind by a crash would be opened instead of the map
                if (dimensions != null) WorldFiles.delete(dimensions.resolve(WORLD_PREFIX + id));
                WorldFiles.copy(plugin.maps().folder(id), target);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Couldn't open map " + id, e);
                return;
            }
            world = new WorldCreator(WORLD_PREFIX + id).generator(new VoidGenerator()).createWorld();
        }
        if (world == null) return;
        open(player, map, world);
        messages.send(player, "editor.editing", Messages.arg("map", map.name));
        info(player, map);
    }

    private void open(Player player, MapDef map, World world) {
        editing.put(player.getUniqueId(), map.id());
        player.setGameMode(GameMode.CREATIVE);
        org.bukkit.Location to = map.spectatorSpawn != null ? map.spectatorSpawn.in(world) : world.getSpawnLocation();
        if (map.spectatorSpawn == null) {
            // a new map's world spawn can point into the void: stand on the top block there, or on the starting glass
            Block top = world.getHighestBlockAt(to.getBlockX(), to.getBlockZ());
            to = top.getType().isAir() ? new org.bukkit.Location(world, 0.5, 65, 0.5) : top.getLocation().add(0.5, 1, 0.5);
        }
        player.teleport(to);
        player.setFlying(true);
    }

    private void close(Player player, boolean keepBuilding) {
        Messages messages = plugin.messages();
        String id = editing.remove(player.getUniqueId());
        if (id == null) {
            messages.send(player, "editor.not-editing");
            return;
        }
        World world = Bukkit.getWorld(WORLD_PREFIX + id);
        player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
        if (world == null) return;
        if (editing.containsValue(id)) {
            messages.send(player, "editor.closed-others");
            return;
        }
        if (keepBuilding) world.save();
        Path folder = world.getWorldFolder().toPath();
        for (Player other : world.getPlayers()) other.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
        Bukkit.unloadWorld(world, keepBuilding);
        try {
            // Swap the map's chunks for the edited ones; its level.dat, data and map.yml stay.
            if (keepBuilding) WorldFiles.storeChunks(folder, plugin.maps().folder(id));
            WorldFiles.delete(folder);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Couldn't close map " + id, e);
        }
        messages.send(player, keepBuilding ? "editor.closed-saved" : "editor.closed");
    }

    private MapDef current(CommandSender sender) {
        if (!(sender instanceof Player player)) return null;
        String id = editing.get(player.getUniqueId());
        return id == null ? null : plugin.maps().get(id).orElse(null);
    }

    // ---------------------------------------------------------------- setting things

    private void set(Player player, MapDef map, String sub, String[] args) {
        Messages messages = plugin.messages();
        switch (sub) {
            case "setspawn" -> {
                if (args.length > 0 && args[0].equalsIgnoreCase("spectator")) {
                    map.spectatorSpawn = Spot.of(player.getLocation());
                    messages.send(player, "editor.set", Messages.arg("what", "spectator spawn"));
                    return;
                }
                TeamColor team = team(player, args);
                if (team == null) return;
                map.team(team).spawn = Spot.of(player.getLocation());
                messages.send(player, "editor.set", Messages.arg("what", team.display() + " spawn"));
            }
            case "setflag" -> {
                TeamColor team = team(player, args);
                if (team == null) return;
                BlockPos pos = BlockPos.of(player.getLocation());
                map.team(team).flag = pos;
                pos.block(player.getWorld()).setType(team.banner());
                messages.send(player, "editor.set", Messages.arg("what", team.display() + " flag at " + pos.serialize()));
            }
            case "addwool" -> {
                TeamColor team = team(player, args);
                if (team == null) return;
                Block target = player.getTargetBlockExact(6);
                if (target == null || !Tag.WOOL.isTagged(target.getType())) {
                    messages.send(player, "editor.look-at-wool");
                    return;
                }
                BlockPos pos = BlockPos.of(target);
                if (!map.team(team).wools.contains(pos)) map.team(team).wools.add(pos);
                target.setType(team.wool());
                messages.send(player, "editor.set", Messages.arg("what", team.display() + " wool #" + map.team(team).wools.size()));
            }
            case "clearwool" -> {
                TeamColor team = team(player, args);
                if (team == null) return;
                map.team(team).wools.clear();
                messages.send(player, "editor.set", Messages.arg("what", team.display() + " wool cleared"));
            }
            case "sethill" -> {
                BlockPos pos = BlockPos.of(player.getLocation());
                if (args.length > 0 && args[0].equals("2")) map.hillB = pos;
                else map.hillA = pos;
                messages.send(player, "editor.set", Messages.arg("what", "hill corner " + (args.length > 0 && args[0].equals("2") ? 2 : 1)));
            }
            case "addhill" -> {
                // the two corners set with sethill become one more point (for maps with several)
                if (map.hillA == null || map.hillB == null) {
                    messages.send(player, "editor.hill-corners-first");
                    return;
                }
                String name = args.length > 0 ? String.join(" ", args) : String.valueOf((char) ('A' + map.hills.size()));
                map.hills.add(new com.sasha.ctf.model.MapDef.HillDef(name, map.hillA, map.hillB));
                messages.send(player, "editor.set", Messages.arg("what", "hill point " + name + " (#" + map.hills.size() + ")"));
            }
            case "clearhills" -> {
                map.hills.clear();
                messages.send(player, "editor.set", Messages.arg("what", "hill points cleared"));
            }
            case "capturetime" -> {
                Integer seconds = args.length > 0 ? parse(args[0]) : null;
                if (seconds == null) {
                    messages.send(player, "editor.need-number");
                    return;
                }
                map.captureSeconds = Math.max(2, Math.min(60, seconds));
                messages.send(player, "editor.set", Messages.arg("what", "capture time " + map.captureSeconds + "s"));
            }
            case "modes" -> {
                Set<Mode> modes = EnumSet.noneOf(Mode.class);
                for (String part : String.join(",", args).split("[, ]+")) Mode.parse(part).ifPresent(modes::add);
                if (modes.isEmpty()) {
                    messages.send(player, "editor.usage-modes");
                    return;
                }
                map.modes.clear();
                map.modes.addAll(modes);
                messages.send(player, "editor.set", Messages.arg("what", "modes " + String.join(", ", modes.stream().map(Mode::id).toList())));
            }
            case "name" -> {
                if (args.length == 0) return;
                map.name = String.join(" ", args);
                messages.send(player, "editor.set", Messages.arg("what", "name " + map.name));
            }
            case "authors" -> {
                map.authors.clear();
                for (String part : String.join(",", args).split("[, ]+")) if (!part.isBlank()) map.authors.add(part);
                messages.send(player, "editor.set", Messages.arg("what", "authors " + String.join(", ", map.authors)));
            }
            case "timelimit", "caps", "kothpoints", "voidy" -> {
                Integer value = args.length > 0 ? parse(args[0]) : null;
                if (value == null) {
                    messages.send(player, "editor.need-number");
                    return;
                }
                switch (sub) {
                    case "timelimit" -> map.timeLimitMinutes = Math.max(1, value);
                    case "caps" -> map.captureLimit = Math.max(1, value);
                    case "kothpoints" -> map.kothPoints = Math.max(10, value);
                    default -> map.voidY = value;
                }
                messages.send(player, "editor.set", Messages.arg("what", sub + " " + value));
            }
            case "save" -> {
                plugin.maps().save(map);
                plugin.maps().publish();
                messages.send(player, "editor.saved", Messages.arg("map", map.name));
                info(player, map);
            }
            default -> messages.send(player, "editor.help");
        }
    }

    private TeamColor team(Player player, String[] args) {
        TeamColor team = args.length > 0 ? TeamColor.parse(args[0]).orElse(null) : null;
        if (team == null) plugin.messages().send(player, "editor.need-team");
        return team;
    }

    private static Integer parse(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- showing

    private void list(CommandSender sender) {
        Messages messages = plugin.messages();
        messages.send(sender, "editor.list-header", Messages.arg("count", plugin.maps().all().size()));
        for (MapDef map : plugin.maps().all().values()) {
            Set<Mode> playable = map.playableModes();
            messages.send(sender, "editor.list-line", Messages.arg("map", map.id()), Messages.arg("name", map.name),
                    Messages.arg("modes", playable.isEmpty() ? "-" : String.join(", ", playable.stream().map(Mode::id).toList())));
        }
    }

    private void info(CommandSender sender, MapDef map) {
        Messages messages = plugin.messages();
        messages.send(sender, "editor.info-header", Messages.arg("map", map.id()), Messages.arg("name", map.name));
        for (Mode mode : Mode.values()) {
            List<String> missing = map.missing(mode);
            boolean listed = map.modes.contains(mode);
            String state = !listed ? "not listed" : missing.isEmpty() ? "ready" : "missing " + String.join(", ", missing);
            messages.send(sender, listed && missing.isEmpty() ? "editor.info-ready" : "editor.info-mode",
                    Messages.arg("mode", mode.display()), Messages.arg("state", state));
        }
        List<String> extra = new ArrayList<>();
        extra.add("time " + map.timeLimitMinutes + " min");
        extra.add("caps " + map.captureLimit);
        extra.add("koth " + map.kothPoints);
        extra.add("void-y " + map.voidY);
        messages.send(sender, "editor.info-extra", Messages.arg("extra", String.join(", ", extra)));
    }

    public List<String> complete(String[] args) {
        if (args.length == 1) return SUBCOMMANDS;
        String sub = args[0].toLowerCase();
        if (args.length == 2) {
            return switch (sub) {
                case "edit", "info" -> new ArrayList<>(plugin.maps().all().keySet());
                case "setspawn" -> List.of("red", "blue", "green", "yellow", "spectator");
                case "setflag", "addwool", "clearwool" -> List.of("red", "blue", "green", "yellow");
                case "sethill" -> List.of("1", "2");
                case "modes" -> List.of("ctf,ctf4,koth,dtw", "ctf", "ctf4", "koth", "dtw", "dtm", "ctw");
                case "close" -> List.of("save");
                case "mirror" -> List.of("on", "off");
                default -> List.of();
            };
        }
        return List.of();
    }
}
