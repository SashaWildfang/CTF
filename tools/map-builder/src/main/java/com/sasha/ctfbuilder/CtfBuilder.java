package com.sasha.ctfbuilder;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.function.Function;

/** /ctfbuild <lobby|frostfall|canyon|kingdoms> builds a map into a fresh void world, saves it and renders previews. */
public final class CtfBuilder extends JavaPlugin {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            sender.sendMessage("/ctfbuild <lobby|frostfall|canyon|kingdoms>");
            return true;
        }
        String id = args[0].toLowerCase();
        if (id.equals("colors")) {
            StringBuilder json = new StringBuilder("{");
            for (Material m : Material.values()) {
                if (!m.isBlock() || m.isLegacy()) continue;
                try {
                    org.bukkit.Color c = m.createBlockData().getMapColor();
                    json.append('"').append(m.getKey().getKey()).append("\":").append(c.asRGB()).append(',');
                } catch (Throwable ignored) { }
            }
            json.setLength(json.length() - 1);
            json.append('}');
            try {
                getDataFolder().mkdirs();
                Files.writeString(new File(getDataFolder(), "colors.json").toPath(), json);
            } catch (IOException e) {
                sender.sendMessage(e.toString());
            }
            sender.sendMessage("colors written");
            return true;
        }
        if (java.util.Arrays.asList(com.sasha.ctfbuilder.v2.Maps.IDS).contains(id) || id.equals("hub")) {
            buildV2(sender, id);
            return true;
        }
        Function<B, String> builder = switch (id) {
            case "lobby" -> Lobby::build;
            case "frostfall" -> Arenas::frostfall;
            case "canyon" -> Arenas::canyon;
            case "kingdoms" -> Arenas::kingdoms;
            default -> null;
        };
        if (builder == null) {
            sender.sendMessage("Unknown map " + id);
            return true;
        }
        long started = System.currentTimeMillis();
        World old = Bukkit.getWorld("build_" + id);
        if (old != null) Bukkit.unloadWorld(old, false);
        // new worlds live under the main world's dimensions folder: start from nothing every time
        deleteTree(new File(Bukkit.getWorlds().get(0).getWorldFolder(), "dimensions/minecraft/build_" + id));
        World world = new WorldCreator("build_" + id).generator(new Void()).environment(World.Environment.NORMAL).createWorld();
        if (world == null) {
            sender.sendMessage("Couldn't create the world");
            return true;
        }
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setDifficulty(Difficulty.PEACEFUL);
        world.setTime(6000);
        world.setSpawnLocation(0, 80, 0);
        // load every chunk we'll touch
        // the hub keeps its server's own generator, so pre-make empty chunks well past view distance
        int reach = id.equals("lobby") ? 26 : 9;
        for (int cx = -reach; cx <= reach; cx++) for (int cz = -reach; cz <= reach; cz++) world.getChunkAt(cx, cz).load(true);
        B b = new B(world, id.hashCode());
        String yml = builder.apply(b);
        world.save();
        File out = new File(getDataFolder(), id);
        out.mkdirs();
        try {
            Files.writeString(new File(out, id.equals("lobby") ? "hub-spawn.txt" : "map.yml").toPath(), yml);
            render(world, new File(out, "top.png"), false);
            render(world, new File(out, "oblique.png"), true);
        } catch (IOException e) {
            sender.sendMessage("Couldn't write the output: " + e);
        }
        sender.sendMessage("Built " + id + " in " + (System.currentTimeMillis() - started) + " ms -> " + out);
        return true;
    }

    static final class Void extends ChunkGenerator {}

    void buildV2(CommandSender sender, String id) {
        long started = System.currentTimeMillis();
        String worldName = "v2_" + id;
        World old = Bukkit.getWorld(worldName);
        if (old != null) Bukkit.unloadWorld(old, false);
        deleteTree(new File(Bukkit.getWorldContainer(), "world/dimensions/minecraft/" + worldName));
        World world = new WorldCreator(worldName).generator(new Void()).environment(World.Environment.NORMAL).createWorld();
        if (world == null) return;
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.BLOCK_DROPS, false);
        world.setDifficulty(Difficulty.PEACEFUL);
        world.setTime(6000);
        String yml;
        try {
            yml = id.equals("hub") ? com.sasha.ctfbuilder.v2.Hub.build(world) : com.sasha.ctfbuilder.v2.Maps.build(world, id);
        } catch (Throwable e) {
            getLogger().log(java.util.logging.Level.SEVERE, "Build failed", e);
            sender.sendMessage("Build failed: " + e);
            return;
        }
        world.save();
        File out = new File(getDataFolder(), "v2/" + id);
        out.mkdirs();
        try {
            Files.writeString(new File(out, id.equals("hub") ? "hub-spawn.txt" : "map.yml").toPath(), yml);
            render(world, new File(out, "top.png"), false);
            render(world, new File(out, "oblique.png"), true);
        } catch (IOException e) {
            sender.sendMessage("Couldn't write the output: " + e);
        }
        sender.sendMessage("Built " + id + " in " + (System.currentTimeMillis() - started) + " ms -> " + out);
    }

    static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }

    // ---------------------------------------------------------------- previews

    static int R = 210, MIN_Y = 0, MAX_Y = 140;

    /** Top-down (height-shaded) or a cabinet-style oblique view looking north, 4 px per block. */
    static void render(World w, File file, boolean oblique) throws IOException {
        int s = 4;
        int size = (2 * R + 1) * s;
        int height = oblique ? size + (MAX_Y - MIN_Y) * s / 2 + 40 : size;
        BufferedImage img = new BufferedImage(size, height, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        g.setColor(new Color(0x7FA8D8));
        g.fillRect(0, 0, size, height);
        if (!oblique) {
            for (int x = -R; x <= R; x++)
                for (int z = -R; z <= R; z++) {
                    int y = top(w, x, z);
                    if (y < MIN_Y) continue;
                    Block block = w.getBlockAt(x, y, z);
                    Color c = colour(block);
                    int north = top(w, x, z - 1);
                    double shade = north > y ? 0.82 : north < y ? 1.12 : 1.0;
                    shade *= 0.85 + (y - 50) / 160.0;
                    g.setColor(scale(c, shade));
                    g.fillRect((x + R) * s, (z + R) * s, s, s);
                }
        } else {
            int yOff = (MAX_Y - MIN_Y) * s / 2 + 20;
            // paint from the back (north) to the front, bottom to top, so nearer and higher blocks overwrite
            for (int z = -R; z <= R; z++)
                for (int y = MIN_Y; y <= MAX_Y; y++)
                    for (int x = -R; x <= R; x++) {
                        Block block = w.getBlockAt(x, y, z);
                        if (block.getType().isAir()) continue;
                        boolean upOpen = !w.getBlockAt(x, y + 1, z).getType().isOccluding();
                        boolean southOpen = !w.getBlockAt(x, y, z + 1).getType().isOccluding();
                        if (!upOpen && !southOpen) continue;
                        Color c = colour(block);
                        int sx = (x + R) * s, sy = (z + R) * s - (y - MIN_Y) * s / 2 + yOff;
                        if (upOpen) {
                            g.setColor(c);
                            g.fillRect(sx, sy - s / 2, s, s);
                        }
                        if (southOpen) {
                            g.setColor(scale(c, 0.68));
                            g.fillRect(sx, sy + s / 2, s, s / 2);
                        }
                    }
        }
        g.dispose();
        ImageIO.write(img, "png", file);
    }

    static int top(World w, int x, int z) {
        for (int y = MAX_Y; y >= MIN_Y; y--) if (!w.getBlockAt(x, y, z).getType().isAir()) return y;
        return MIN_Y - 1;
    }

    static Color colour(Block block) {
        Material type = block.getType();
        if (type == Material.WATER) return new Color(0x3F76E4);
        if (type.name().endsWith("LEAVES")) return type == Material.SPRUCE_LEAVES ? new Color(0x3C5C3C) : type == Material.BIRCH_LEAVES ? new Color(0x6E9A48)
                : type == Material.AZALEA_LEAVES ? new Color(0x5E8C3A) : new Color(0x4C8A2C);
        if (type == Material.GRASS_BLOCK) return new Color(0x6DA544);
        if (type == Material.SHORT_GRASS || type == Material.TALL_GRASS) return new Color(0x5E9A3A);
        try {
            org.bukkit.Color c = block.getBlockData().getMapColor();
            return new Color(c.getRed(), c.getGreen(), c.getBlue());
        } catch (Throwable t) {
            return Color.MAGENTA;
        }
    }

    static Color scale(Color c, double f) {
        return new Color(clamp(c.getRed() * f), clamp(c.getGreen() * f), clamp(c.getBlue() * f));
    }

    static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }
}
