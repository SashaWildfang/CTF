/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.model;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

/** A block position in a map, independent of which world copy the match runs in. */
public record BlockPos(int x, int y, int z) {

    public static BlockPos of(Block block) {
        return new BlockPos(block.getX(), block.getY(), block.getZ());
    }

    public static BlockPos of(Location location) {
        return new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public Block block(World world) {
        return world.getBlockAt(x, y, z);
    }

    /** The middle of the block, for distance checks and teleports. */
    public Location center(World world) {
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    /** Close enough to touch: within 1.5 blocks sideways and 2 up or down (a flag, a capture point). */
    public boolean touching(Location location) {
        double dx = location.getX() - (x + 0.5);
        double dz = location.getZ() - (z + 0.5);
        double dy = location.getY() - y;
        return dx * dx + dz * dz <= 2.25 && dy > -2 && dy < 2.5;
    }

    public double distanceSquared(Location location) {
        double dx = location.getX() - (x + 0.5);
        double dy = location.getY() - y;
        double dz = location.getZ() - (z + 0.5);
        return dx * dx + dy * dy + dz * dz;
    }

    public long key() {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | ((long) y & 0xFFF);
    }

    /** "x y z", as written in map.yml. */
    public String serialize() {
        return x + " " + y + " " + z;
    }

    public static BlockPos parse(String text) {
        if (text == null || text.isBlank()) return null;
        String[] parts = text.trim().split("[ ,]+");
        if (parts.length != 3) return null;
        try {
            return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
