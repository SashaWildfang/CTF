/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.util;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;

/** Copying and deleting world folders. */
public final class WorldFiles {

    /** Files a copied world must not take along: the copy gets its own id and lock. */
    private static final Set<String> SKIP = Set.of("uid.dat", "session.lock", "map.yml");

    private WorldFiles() {}

    public static boolean isWorld(Path folder) {
        return Files.isRegularFile(folder.resolve("level.dat"));
    }

    /** The chunk folders of a world: what changes when someone builds. */
    private static final java.util.List<String> CHUNKS = java.util.List.of("region", "entities", "poi");

    /**
     * Where a map folder keeps region/, entities/ and poi/: dimensions/minecraft/overworld (the layout
     * since Minecraft 26), or the map's own folder for maps saved in the older layout.
     */
    public static Path chunkFolder(Path map) {
        Path overworld = map.resolve("dimensions").resolve("minecraft").resolve("overworld");
        if (Files.isDirectory(overworld)) return overworld;
        if (Files.isDirectory(map.resolve("region"))) return map;
        return overworld;
    }

    /**
     * Writes a saved world's chunks into a map folder, replacing the map's old chunks and keeping its
     * level.dat, data and map.yml. {@code worldFolder} is {@code World#getWorldFolder()}: on Paper 26
     * that is the world's dimension folder, which holds the chunks but no level.dat.
     */
    public static void storeChunks(Path worldFolder, Path map) throws IOException {
        Path into = chunkFolder(map);
        for (String part : CHUNKS) {
            Path to = into.resolve(part);
            delete(to);
            Path from = worldFolder.resolve(part);
            if (Files.isDirectory(from)) copy(from, to);
        }
    }

    /** The nearest level.dat at or above a world's folder (on Paper 26 that's the main world's). */
    public static Path levelDat(Path worldFolder) {
        Path at = worldFolder;
        for (int i = 0; i < 5 && at != null; i++, at = at.getParent()) {
            if (Files.isRegularFile(at.resolve("level.dat"))) return at.resolve("level.dat");
        }
        return null;
    }

    /**
     * Where Paper 26 moves worlds that plugins load (world/dimensions/minecraft/&lt;name&gt;), found from the
     * main world's folder; null on servers that keep each world in its own folder.
     */
    public static Path dimensionsFolder(Path mainWorldFolder) {
        Path parent = mainWorldFolder.getParent();
        return parent != null && parent.getFileName() != null && parent.getFileName().toString().equals("minecraft")
                && parent.getParent() != null && parent.getParent().getFileName().toString().equals("dimensions") ? parent : null;
    }

    public static void copy(Path from, Path to) throws IOException {
        Files.walkFileTree(from, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(to.resolve(from.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!SKIP.contains(file.getFileName().toString())) {
                    Files.copy(file, to.resolve(from.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void delete(Path folder) throws IOException {
        if (!Files.exists(folder)) return;
        Files.walkFileTree(folder, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
