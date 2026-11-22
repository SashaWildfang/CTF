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
     * A wool tower on a monument pad: a straight column of wool ({@code height} blocks, wool on every side),
     * four stone posts at its corners carrying a little hipped roof, on a tiled plinth. The team's beacon is
     * set into the plinth on the side facing the middle of the map, under a pane of tinted glass flush with
     * the floor: it shines the team colour while the tower stands and black once it's down.
     * {@code base} is the first block above the pad.
     */
    static Monument woolTower(World world, BlockPos base, TeamColor team, int height) {
        int x = base.x(), y = base.y(), z = base.z();
        // plinth
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            boolean ring = Math.abs(dx) == 2 || Math.abs(dz) == 2;
            boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
            Material floor = corner ? Material.CHISELED_DEEPSLATE : ring ? Material.DEEPSLATE_TILES : Material.POLISHED_DEEPSLATE;
            world.getBlockAt(x + dx, y - 1, z + dz).setType(floor, false);
            if (world.getBlockAt(x + dx, y - 2, z + dz).getType().isAir()) world.getBlockAt(x + dx, y - 2, z + dz).setType(Material.DEEPSLATE_BRICKS, false);
            for (int up = 0; up <= height + 2; up++) {
                Block clear = world.getBlockAt(x + dx, y + up, z + dz);
                if (!clear.getType().isAir()) clear.setType(Material.AIR, false);
            }
        }
        // the wool
        List<BlockPos> wool = new ArrayList<>();
        for (int up = 0; up < height; up++) {
            Block block = world.getBlockAt(x, y + up, z);
            block.setType(team.wool(), false);
            wool.add(BlockPos.of(block));
        }
        // corner posts
        for (int dx = -1; dx <= 1; dx += 2) for (int dz = -1; dz <= 1; dz += 2) {
            for (int up = 0; up < height - 1; up++) {
                world.getBlockAt(x + dx, y + up, z + dz).setBlockData(org.bukkit.Bukkit.createBlockData(up == 0
                        ? "minecraft:polished_deepslate" : "minecraft:deepslate_brick_wall[up=true]"), false);
            }
            world.getBlockAt(x + dx, y + height - 1, z + dz).setType(Material.POLISHED_DEEPSLATE, false);
        }
        // hipped roof: stairs falling away on all four sides, a slab on top
        int roof = y + height;
        world.getBlockAt(x, roof, z).setType(Material.POLISHED_DEEPSLATE, false);
        String[][] sides = {{"0", "-1", "south"}, {"0", "1", "north"}, {"-1", "0", "east"}, {"1", "0", "west"}};
        for (String[] side : sides) {
            world.getBlockAt(x + Integer.parseInt(side[0]), roof, z + Integer.parseInt(side[1])).setBlockData(org.bukkit.Bukkit.createBlockData(
                    "minecraft:deepslate_tile_stairs[facing=" + side[2] + ",half=bottom,shape=straight]"), false);
        }
        String[][] corners = {{"-1", "-1", "south", "outer_left"}, {"1", "-1", "south", "outer_right"},
                {"-1", "1", "north", "outer_right"}, {"1", "1", "north", "outer_left"}};
        for (String[] corner : corners) {
            world.getBlockAt(x + Integer.parseInt(corner[0]), roof, z + Integer.parseInt(corner[1])).setBlockData(org.bukkit.Bukkit.createBlockData(
                    "minecraft:deepslate_tile_stairs[facing=" + corner[2] + ",half=bottom,shape=" + corner[3] + "]"), false);
        }
        world.getBlockAt(x, roof + 1, z).setBlockData(org.bukkit.Bukkit.createBlockData("minecraft:deepslate_tile_slab[type=bottom]"), false);
        // the beacon, on the side that faces the middle of the map
        int bx = x, bz = z;
        if (Math.abs(x) >= Math.abs(z)) bx += x > 0 ? -2 : 2;
        else bz += z > 0 ? -2 : 2;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) world.getBlockAt(bx + dx, y - 3, bz + dz).setType(Material.IRON_BLOCK, false);
        Block beacon = world.getBlockAt(bx, y - 2, bz);
        beacon.setType(Material.BEACON, false);
        Block glass = world.getBlockAt(bx, y - 1, bz);
        glass.setType(glass(team), false);
        for (int up = y; up < Math.min(world.getMaxHeight(), y + 60); up++) {
            Block above = world.getBlockAt(bx, up, bz);
            if (above.getType().isAir()) continue;
            if (above.getType().name().endsWith("LEAVES") || !above.getType().isOccluding()) above.setType(Material.AIR, false);
            else break;
        }
        return new Monument(wool, List.of(glass), beacon);
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
