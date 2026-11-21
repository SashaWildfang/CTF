/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.model;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Locale;

/** A spawn point: position plus the way players face. */
public record Spot(double x, double y, double z, float yaw, float pitch) {

    public static Spot of(Location location) {
        return new Spot(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }

    public Location in(World world) {
        return new Location(world, x, y, z, yaw, pitch);
    }

    /** "x y z yaw pitch", as written in map.yml and config.yml. */
    public String serialize() {
        return String.format(Locale.ROOT, "%.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch);
    }

    public static Spot parse(String text) {
        if (text == null || text.isBlank()) return null;
        String[] parts = text.trim().split("[ ,]+");
        if (parts.length < 3) return null;
        try {
            return new Spot(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]),
                    parts.length > 3 ? Float.parseFloat(parts[3]) : 0f, parts.length > 4 ? Float.parseFloat(parts[4]) : 0f);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
