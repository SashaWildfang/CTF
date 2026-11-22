/*
 * FractalCTF - built by Sasha Wildfang for Fractal Network.
 */
package com.sasha.ctf.game;

import com.sasha.ctf.model.BlockPos;
import com.sasha.ctf.model.Cuboid;
import com.sasha.ctf.model.TeamColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * The pieces only one mode needs, built into the fresh match world when it loads, so a map doesn't carry
 * wool or a beacon in the modes that don't use them.
 */
final class Structures {

    private Structures() {}

    /** A Destroy the Wool monument: its wool, and the glass column its beacon shines through. */
    record Monument(List<BlockPos> wool, List<Block> glass, Block beacon) {}

    static Material glass(TeamColor team) {
        return team == null ? Material.BLACK_STAINED_GLASS : Material.valueOf((team == TeamColor.GREEN ? "LIME" : team.name()) + "_STAINED_GLASS");
    }

    /**
     * A wool tower on a monument pad: a beacon set into the pad, corner posts, wool on all four sides of each
     * layer around a column of team glass (so the beam shines out of the top), and a cap.
     * {@code base} is the first block above the pad.
     */
    static Monument woolTower(World world, BlockPos base, TeamColor team, int layers) {
        int x = base.x(), y = base.y(), z = base.z();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            world.getBlockAt(x + dx, y - 2, z + dz).setType(Material.IRON_BLOCK, false);
            world.getBlockAt(x + dx, y - 1, z + dz).setType(dx == 0 && dz == 0 ? Material.BEACON : Material.POLISHED_DEEPSLATE, false);
        }
        // a ring around the tower, so it stands on something tidy
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            if (Math.abs(dx) < 2 && Math.abs(dz) < 2) continue;
            boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
            world.getBlockAt(x + dx, y - 1, z + dz).setType(corner ? Material.CHISELED_DEEPSLATE : Material.DEEPSLATE_TILES, false);
        }
        List<BlockPos> wool = new ArrayList<>();
        List<Block> glass = new ArrayList<>();
        Material teamGlass = glass(team);
        for (int layer = 0; layer < layers; layer++) {
            int ly = y + layer;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                Block block = world.getBlockAt(x + dx, ly, z + dz);
                if (dx == 0 && dz == 0) {
                    block.setType(teamGlass, false);
                    glass.add(block);
                } else if (dx != 0 && dz != 0) {
                    block.setType(Material.POLISHED_DEEPSLATE, false);
                } else {
                    block.setType(team.wool(), false);
                    wool.add(BlockPos.of(block));
                }
            }
        }
        int top = y + layers;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            Block block = world.getBlockAt(x + dx, top, z + dz);
            if (dx == 0 && dz == 0) {
                block.setType(teamGlass, false);
                glass.add(block);
            } else {
                block.setBlockData(org.bukkit.Bukkit.createBlockData(dx != 0 && dz != 0 ? "minecraft:deepslate_tile_slab[type=bottom]"
                        : "minecraft:polished_deepslate_slab[type=bottom]"), false);
            }
        }
        // keep the beam's way up clear
        for (int up = top + 1; up < Math.min(world.getMaxHeight(), top + 40); up++) {
            Block above = world.getBlockAt(x, up, z);
            if (!above.getType().isAir() && !above.getType().name().endsWith("LEAVES")) break;
            if (above.getType().name().endsWith("LEAVES")) above.setType(Material.AIR, false);
        }
        return new Monument(wool, glass, world.getBlockAt(x, y - 1, z));
    }

    /**
     * The hill's beacon: set into the middle of the hill's floor under a pane of glass that shows who holds
     * it. Returns the glass.
     */
    static Block hillBeacon(World world, Cuboid hill) {
        int x = (hill.min().x() + hill.max().x()) / 2, z = (hill.min().z() + hill.max().z()) / 2;
        int floor = hill.min().y() - 1;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            world.getBlockAt(x + dx, floor - 2, z + dz).setType(Material.IRON_BLOCK, false);
        }
        world.getBlockAt(x, floor - 1, z).setType(Material.BEACON, false);
        Block glass = world.getBlockAt(x, floor, z);
        glass.setType(Material.WHITE_STAINED_GLASS, false);
        // a ring of polished stone around the glass marks the middle of the hill
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0) continue;
            world.getBlockAt(x + dx, floor, z + dz).setType(dx != 0 && dz != 0 ? Material.CHISELED_QUARTZ_BLOCK : Material.QUARTZ_BRICKS, false);
        }
        for (int up = floor + 1; up < Math.min(world.getMaxHeight(), floor + 60); up++) {
            Block above = world.getBlockAt(x, up, z);
            if (above.getType().isAir()) continue;
            if (above.getType().name().endsWith("LEAVES") || above.getType().name().endsWith("CARPET")
                    || !above.getType().isOccluding()) above.setType(Material.AIR, false);
            else break;
        }
        return glass;
    }
}
