package com.sasha.upgrader;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Loads every chunk stored in the main world's region files (upgrading them), saves, and stops the server. */
public final class Upgrader extends JavaPlugin {
    @Override
    public ChunkGenerator getDefaultWorldGenerator(String world, String id) {
        return new ChunkGenerator() {};   // nothing new is generated: void
    }

    @Override
    public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, this::run, 40L);
    }

    private void run() {
        World world = Bukkit.getWorlds().get(0);
        List<int[]> chunks = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(world.getWorldFolder().toPath().getParent())) {
            for (Path p : walk.filter(f -> f.toString().endsWith(".mca") && f.getParent().getFileName().toString().equals("region")
                    && !f.toString().contains("nether") && !f.toString().contains("the_end") && !f.toString().contains("DIM")).toList()) {
                String[] parts = p.getFileName().toString().split("\\.");
                int rx = Integer.parseInt(parts[1]), rz = Integer.parseInt(parts[2]);
                try (RandomAccessFile raf = new RandomAccessFile(p.toFile(), "r")) {
                    if (raf.length() < 4096) continue;
                    for (int i = 0; i < 1024; i++) {
                        raf.seek(i * 4L);
                        if (raf.readInt() != 0) chunks.add(new int[] {rx * 32 + (i & 31), rz * 32 + (i >> 5)});
                    }
                }
                getLogger().info("region " + p);
            }
        } catch (Exception e) {
            getLogger().severe("UPGRADE FAILED " + e);
        }
        int n = 0;
        int[] min = {Integer.MAX_VALUE, Integer.MAX_VALUE}, max = {Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (int[] c : chunks) {
            world.getChunkAt(c[0], c[1]).load(false);
            min[0] = Math.min(min[0], c[0]); min[1] = Math.min(min[1], c[1]); max[0] = Math.max(max[0], c[0]); max[1] = Math.max(max[1], c[1]);
            if (++n % 64 == 0) for (var loaded : world.getLoadedChunks()) loaded.unload(true);
        }
        for (var loaded : world.getLoadedChunks()) loaded.unload(true);
        world.save();
        getLogger().info("UPGRADE DONE chunks=" + n + " min=" + min[0] + "," + min[1] + " max=" + max[0] + "," + max[1]
                + " folder=" + world.getWorldFolder());
        Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 40L);
    }
}
