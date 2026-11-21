/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.game;

import com.sasha.ctf.FractalCTF;
import com.sasha.ctf.model.MapDef;
import com.sasha.ctf.util.WorldFiles;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.stream.Stream;

/**
 * The game server's maps: plugins/FractalCTF/maps/&lt;id&gt;/ holds the world (level.dat, region/...) and
 * map.yml. The list, with the modes each map is ready for, is shared with the hub through the database.
 */
public final class MapStore {

    private final FractalCTF plugin;
    private final Path folder;
    private final Map<String, MapDef> maps = new TreeMap<>();

    public MapStore(FractalCTF plugin) {
        this.plugin = plugin;
        this.folder = plugin.getDataFolder().toPath().resolve("maps");
    }

    public Path folder() {
        return folder;
    }

    public Path folder(String id) {
        return folder.resolve(id);
    }

    public Map<String, MapDef> all() {
        return maps;
    }

    public Optional<MapDef> get(String id) {
        return Optional.ofNullable(maps.get(id));
    }

    /** Reads every map folder. A folder with a world but no map.yml gets an empty map.yml to fill in. */
    public void reload() {
        maps.clear();
        try {
            Files.createDirectories(folder);
            try (Stream<Path> folders = Files.list(folder)) {
                for (Path dir : folders.filter(Files::isDirectory).toList()) {
                    String id = dir.getFileName().toString().toLowerCase();
                    if (!id.matches("[a-z0-9_-]{1,64}")) {
                        plugin.getLogger().warning("Skipping map folder '" + dir.getFileName() + "': use only a-z, 0-9, _ and - in map folder names.");
                        continue;
                    }
                    Path file = dir.resolve("map.yml");
                    MapDef map = Files.exists(file) ? MapDef.load(id, YamlConfiguration.loadConfiguration(file.toFile())) : new MapDef(id);
                    if (!WorldFiles.isWorld(dir)) {
                        plugin.getLogger().warning("Map '" + id + "' has no world in it (no level.dat): put the world's files in " + dir);
                        continue;
                    }
                    if (!Files.exists(file)) save(map);
                    maps.put(id, map);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Couldn't read the maps folder", e);
        }
        for (MapDef map : maps.values()) {
            if (map.playableModes().isEmpty()) {
                plugin.getLogger().info("Map '" + map.id() + "' isn't ready for any mode yet: set it up with /ctfmap edit " + map.id());
            }
        }
        plugin.getLogger().info("Maps: " + maps.size() + " (" + String.join(", ", maps.keySet()) + ")");
        publish();
    }

    public void save(MapDef map) {
        try {
            map.save().save(folder(map.id()).resolve("map.yml").toFile());
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Couldn't save map.yml for " + map.id(), e);
        }
    }

    public void put(MapDef map) {
        maps.put(map.id(), map);
    }

    /** Tells the hub which maps there are and what they can be played as. */
    public void publish() {
        if (plugin.database() == null) return;
        Map<String, MapDef> snapshot = new HashMap<>(maps);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                for (MapDef map : snapshot.values()) plugin.database().saveMap(map.id(), map.name, map.playableModes());
                plugin.database().keepOnlyMaps(snapshot.keySet());
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Couldn't share the map list with the hub", e);
            }
        });
    }
}
