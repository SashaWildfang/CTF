/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.model;

import org.bukkit.Location;

/** A box between two corners (inclusive), for the King of the Hill area. */
public record Cuboid(BlockPos min, BlockPos max) {

    public static Cuboid of(BlockPos a, BlockPos b) {
        return new Cuboid(new BlockPos(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.min(a.z(), b.z())),
                new BlockPos(Math.max(a.x(), b.x()), Math.max(a.y(), b.y()), Math.max(a.z(), b.z())));
    }

    public boolean contains(Location location) {
        return contains(location.getX(), location.getY(), location.getZ());
    }

    public boolean contains(double x, double y, double z) {
        return x >= min.x() && x < max.x() + 1
                && y >= min.y() && y < max.y() + 1
                && z >= min.z() && z < max.z() + 1;
    }
}
