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
     * A wool tower on a monument pad: 3 x 3 blocks and {@code height} tall, the eight outer columns of the team's
     * wool and the middle column of the team's stained glass, with the team's beacon straight under it so its
     * beam shoots up through the middle of the tower. The beam shines the team colour while the tower stands and
     * black once its wool is gone. It stands on a tiled plinth; {@code base} is the first block above the pad.
     */
    static Material concrete(TeamColor team) {
        return Material.valueOf((team == TeamColor.GREEN ? "LIME" : team.name()) + "_CONCRETE");
    }

    static Monument woolTower(World world, BlockPos base, TeamColor team, int height) {
        int x = base.x(), y = base.y(), z = base.z();
        // plinth, and room around and above the tower
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            boolean ring = Math.abs(dx) == 2 || Math.abs(dz) == 2;
            boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
            // a ring in the team's colour round the tower, with the middle of each side marked
            Material floor = corner ? Material.CHISELED_DEEPSLATE : ring ? ((dx == 0 || dz == 0) ? concrete(team) : Material.DEEPSLATE_TILES) : Material.POLISHED_DEEPSLATE;
            world.getBlockAt(x + dx, y - 1, z + dz).setType(floor, false);
            if (world.getBlockAt(x + dx, y - 2, z + dz).getType().isAir()) world.getBlockAt(x + dx, y - 2, z + dz).setType(Material.DEEPSLATE_BRICKS, false);
            for (int up = 0; up <= height + 2; up++) {
                Block clear = world.getBlockAt(x + dx, y + up, z + dz);
                if (!clear.getType().isAir()) clear.setType(Material.AIR, false);
            }
        }
        // the beacon under the middle: iron under it, the beacon in the pad, glass in the floor above it
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) world.getBlockAt(x + dx, y - 3, z + dz).setType(Material.IRON_BLOCK, false);
        Block beacon = world.getBlockAt(x, y - 2, z);
        beacon.setType(Material.BEACON, false);
        List<Block> glass = new ArrayList<>();
        Block floorGlass = world.getBlockAt(x, y - 1, z);
        floorGlass.setType(glass(team), false);
        glass.add(floorGlass);
        // the tower: wool all round, the beam's glass up the middle
        List<BlockPos> wool = new ArrayList<>();
        for (int up = 0; up < height; up++) {
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                Block block = world.getBlockAt(x + dx, y + up, z + dz);
                if (dx == 0 && dz == 0) {
                    block.setType(glass(team), false);
                    glass.add(block);
                } else {
                    block.setType(team.wool(), false);
                    wool.add(BlockPos.of(block));
                }
            }
        }
        // a crown on top: chiseled corners with lanterns, slabs between them, the beam's glass in the middle
        int top = y + height;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            Block block = world.getBlockAt(x + dx, top, z + dz);
            if (dx == 0 && dz == 0) {
                block.setType(glass(team), false);
                glass.add(block);
            } else if (dx != 0 && dz != 0) {
                block.setType(Material.CHISELED_DEEPSLATE, false);
                world.getBlockAt(x + dx, top + 1, z + dz).setType(Material.LANTERN, false);
            } else {
                block.setType(Material.DEEPSLATE_TILE_SLAB, false);
            }
        }
        // posts at the plinth's corners: a deepslate wall with a team banner and a lantern
        for (int dx = -2; dx <= 2; dx += 4) for (int dz = -2; dz <= 2; dz += 4) {
            Block post = world.getBlockAt(x + dx, y, z + dz);
            post.setType(Material.POLISHED_DEEPSLATE_WALL, false);
            world.getBlockAt(x + dx, y + 1, z + dz).setType(Material.POLISHED_DEEPSLATE_WALL, false);
            world.getBlockAt(x + dx, y + 2, z + dz).setType(Material.LANTERN, false);
            Block banner = world.getBlockAt(x + dx + Integer.signum(dx), y + 1, z + dz);
            if (banner.getType().isAir()) {
                org.bukkit.block.data.BlockData data = Material.valueOf(team.banner().name().replace("_BANNER", "_WALL_BANNER")).createBlockData();
                if (data instanceof org.bukkit.block.data.Directional facing) facing.setFacing(dx > 0 ? org.bukkit.block.BlockFace.EAST : org.bukkit.block.BlockFace.WEST);
                banner.setBlockData(data, false);
            }
        }
        // nothing over the tower may stop the beam
        for (int up = y + height + 1; up < Math.min(world.getMaxHeight(), y + height + 60); up++) {
            Block above = world.getBlockAt(x, up, z);
            if (above.getType().isAir()) continue;
            if (above.getType().name().endsWith("LEAVES") || !above.getType().isOccluding()) above.setType(Material.AIR, false);
            else break;
        }
        return new Monument(wool, glass, beacon);
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
