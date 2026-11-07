package com.bearnation.ctf.util;

import com.bearnation.ctf.CTF;
import com.bearnation.ctf.schematic.SchematicPastePayload;
import com.bearnation.ctf.schematic.SchematicUtil;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.Chunk;

import java.io.File;

public class WorldUtil {

    private static final ChunkGenerator FALLBACK_VOID_GENERATOR = new ChunkGenerator() {
        @Override
        public ChunkData generateChunkData(World world, java.util.Random random, int chunkX, int chunkZ, BiomeGrid biome) {
            return createChunkData(world);
        }
    };

    /**
     * Resets a world, then pastes the schematic when the new world is ready.
     */
    public static void resetWorldAndPaste(String worldName, SchematicPastePayload payload, Runnable onComplete) {
        log("Starting full reset for world: " + worldName + " with paste for map '" + payload.mapId + "'");
        resetWorld(worldName, () -> {
            log("World reset complete. Beginning schematic paste for map: " + payload.mapId);
            SchematicUtil.pasteSchematic(payload);
            log("Schematic paste finished for map: " + payload.mapId);

            // Teleport all players to new world spawn
            World ctfWorld = Bukkit.getWorld(worldName);
            if (ctfWorld != null) {
                Location spawn = ctfWorld.getSpawnLocation();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    log("Teleporting " + p.getName() + " to CTF spawn at " + spawn);
                    p.teleport(spawn);
                }
            } else {
                log("ERROR: Could not find CTF world after paste — teleport skipped.");
            }

            if (onComplete != null) {
                onComplete.run();
            }
        });
    }


    /**
     * Resets a world and runs callback when done.
     */
    public static void resetWorld(String worldName, Runnable onComplete) {
        World world = Bukkit.getWorld(worldName);

        if (world != null) {
            log("Found loaded world '" + worldName + "'. Preparing to unload...");

            // Log loaded chunks
            Chunk[] loadedChunks = world.getLoadedChunks();
            log("Loaded chunks: " + loadedChunks.length);
            for (Chunk chunk : loadedChunks) {
                log(" - Chunk " + chunk.getX() + "," + chunk.getZ());
            }

            // Log entities
            log("Entities before unload: " + world.getEntities().size());
            for (Entity e : world.getEntities()) {
                log(" - " + e.getType() + " at " + e.getLocation().toVector());
            }

            // Teleport players out
            World fallbackWorld = Bukkit.getWorlds().stream()
                    .filter(w -> !w.getName().equals(worldName))
                    .findFirst().orElse(null);

            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getWorld().equals(world)) {
                    Location spawn = (fallbackWorld != null ? fallbackWorld : Bukkit.getWorlds().get(0)).getSpawnLocation();
                    log("Teleporting " + p.getName() + " to " + spawn);
                    p.teleport(spawn);
                }
            }

            // Remove all entities
            try {
                log("Removing all entities...");
                world.getEntities().forEach(e -> {
                    try { e.remove(); } catch (Exception ignored) {}
                });
            } catch (Exception ex) {
                log("Error removing entities: " + ex.getMessage());
            }

            // Save world
            try {
                log("Saving world before unload...");
                world.save();
            } catch (Exception ex) {
                log("Error saving world: " + ex.getMessage());
            }

            // Unload all chunks manually
            log("Manually unloading chunks...");
            for (Chunk chunk : loadedChunks) {
                boolean result = world.unloadChunk(chunk);
                log(" - Unload " + chunk.getX() + "," + chunk.getZ() + ": " + result);
            }

            // Schedule actual world unload
            Bukkit.getScheduler().runTaskLater(CTF.getInstance(), () -> {
                log("Attempting to unload world '" + worldName + "'");
                if (!Bukkit.unloadWorld(world, true)) {
                    log("Unload failed. Retrying in 1 second...");
                    Bukkit.getScheduler().runTaskLater(CTF.getInstance(), () -> {
                        log("Retrying unload...");
                        if (!Bukkit.unloadWorld(world, true)) {
                            log("SEVERE: Could not unload world after retry. Using existing world.");
                            onComplete.run();
                        } else {
                            finishWorldReset(worldName, onComplete);
                        }
                    }, 20L);
                } else {
                    finishWorldReset(worldName, onComplete);
                }
            }, 2L);

        } else {
            log("World '" + worldName + "' not loaded. Creating fresh...");
            finishWorldReset(worldName, onComplete);
        }
    }

    private static void finishWorldReset(String worldName, Runnable onComplete) {
        log("Deleting old world folder for '" + worldName + "'");
        deleteDirectory(new File(Bukkit.getWorldContainer(), worldName));

        // Get generator
        ChunkGenerator generator = null;
        boolean hasVoidGen = Bukkit.getPluginManager().isPluginEnabled("VoidGen");

        if (hasVoidGen) {
            try {
                var pv = Bukkit.getPluginManager().getPlugin("VoidGen");
                if (pv instanceof org.bukkit.plugin.java.JavaPlugin jp) {
                    generator = jp.getDefaultWorldGenerator(worldName, "");
                    log("Using VoidGen generator: " + generator.getClass().getName());
                }
            } catch (Throwable t) {
                log("Failed to get VoidGen generator: " + t);
            }
        } else {
            log("VoidGen not found. Using fallback void generator.");
        }

        WorldCreator creator = new WorldCreator(worldName)
                .environment(World.Environment.NORMAL)
                .generateStructures(false);

        if (generator != null) {
            creator.generator(generator);
        } else if (hasVoidGen) {
            creator.generator("VoidGen");
        } else {
            creator.generator(FALLBACK_VOID_GENERATOR);
        }

        log("Creating world: " + worldName);
        World newWorld = Bukkit.createWorld(creator);
        if (newWorld == null) {
            log("SEVERE: Failed to create world '" + worldName + "'");
            onComplete.run();
            return;
        }

        log("World created: " + newWorld.getName());
        newWorld.getBlockAt(0, 64, 0).setType(Material.BEDROCK);
        newWorld.setSpawnLocation(new Location(newWorld, 0.5, 65, 0.5));

        log("World reset finished.");
        onComplete.run();
    }

    private static void deleteDirectory(File file) {
        if (file == null || !file.exists()) return;
        File[] contents = file.listFiles();
        if (contents != null) {
            for (File f : contents) deleteDirectory(f);
        }
        if (!file.delete()) {
            log("Could not delete: " + file.getAbsolutePath());
        }
    }

    private static void log(String msg) {
        Bukkit.getLogger().info("[CTF RESET] " + msg);
    }
}
