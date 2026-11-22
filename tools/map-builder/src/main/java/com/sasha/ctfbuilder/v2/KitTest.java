package com.sasha.ctfbuilder.v2;

import com.sasha.ctfbuilder.v2.W.P;
import org.bukkit.World;

/** A test bed for the building kit: roofs of every kind and a bridge, to look at in renders. */
public final class KitTest {
    private KitTest() {}

    public static String build(World world) {
        for (int cx = -6; cx <= 6; cx++) for (int cz = -6; cz <= 6; cz++) world.getChunkAt(cx, cz).load(true);
        W w = new W(world, 1, 1);
        Kit k = new Kit(w);
        int y = 64;
        w.fill(-60, y - 3, -30, -5, y, 30, "stone");
        w.fill(5, y - 3, -30, 60, y, 30, "stone");
        w.fill(-60, y, -30, -5, y, 30, "grass_block");
        w.fill(5, y + 2, -30, 60, y + 2, 30, "grass_block");
        w.fill(5, y - 3, -30, 60, y + 1, 30, "dirt");
        P wall = P.of("stone_bricks", 4, "cobblestone", 1);
        Kit.Roof tiles = Kit.Roof.of("deepslate_tiles");
        // a gable along x, even width
        k.walls(-55, -25, -42, -18, y, 5, Kit.Bands.of(0, "cobblestone", 1, "white_terracotta"), "stripped_spruce_log", 4, P.of("spruce_planks"));
        k.gable(-55, -25, -42, -18, y + 6, true, 1, Kit.Roof.of("spruce_planks"), P.of("white_terracotta"));
        k.doorway(-49, y, -18, "south", 1, "spruce_door", "stone_brick_stairs", "spruce_fence", "lantern");
        k.window(-52, y + 2, -18, "south", 1, 2, "glass_pane", "spruce_stairs", "spruce_trapdoor");
        k.window(-45, y + 2, -18, "south", 2, 2, "glass_pane", "spruce_stairs", null);
        // a gable along z, odd width
        k.walls(-38, -25, -30, -12, y, 4, Kit.Bands.of(0, wall), "dark_oak_log", 0, P.of("oak_planks"));
        k.gable(-38, -25, -30, -12, y + 5, false, 1, tiles, wall);
        // a hip roof
        k.walls(-55, 0, -44, 9, y, 4, Kit.Bands.of(0, "bricks"), null, 0, P.of("oak_planks"));
        k.hip(-55, 0, -44, 9, y + 5, 1, Kit.Roof.of("dark_oak_planks"), P.of("bricks"));
        // a round tower with a cone
        for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) {
            double d = Math.hypot(dx, dz);
            if (d > 5.45) continue;
            for (int dy = 1; dy <= 10; dy++) w.set(-30 + dx, y + dy, 5 + dz, d > 4.45 ? "stone_bricks" : "air");
        }
        k.cone(-30, 5, y + 11, 5, 1, 1.6, tiles);
        // a cross gable: two rectangles' height fields merged
        int ax0 = -58, az0 = 15, ax1 = -46, az1 = 22, bx0 = -54, bz0 = 13, bx1 = -50, bz1 = 28;
        k.walls(ax0, az0, ax1, az1, y, 4, Kit.Bands.of(0, "oak_planks"), "oak_log", 0, null);
        k.walls(bx0, bz0, bx1, bz1, y, 4, Kit.Bands.of(0, "oak_planks"), "oak_log", 0, null);
        w.fill(ax0 + 1, y + 1, az0 + 1, ax1 - 1, y + 4, az1 - 1, "air");
        w.fill(bx0 + 1, y + 1, bz0 + 1, bx1 - 1, y + 4, bz1 - 1, "air");
        java.util.function.IntBinaryOperator ha = (x, z) -> x < ax0 - 1 || x > ax1 + 1 || z < az0 - 1 || z > az1 + 1 ? Kit.NONE : Math.min(z - az0 + 1, az1 + 1 - z);
        java.util.function.IntBinaryOperator hb = (x, z) -> x < bx0 - 1 || x > bx1 + 1 || z < bz0 - 1 || z > bz1 + 1 ? Kit.NONE : Math.min(x - bx0 + 1, bx1 + 1 - x);
        java.util.function.IntBinaryOperator hc = (x, z) -> Math.max(ha.applyAsInt(x, z), hb.applyAsInt(x, z));
        java.util.function.IntBinaryOperator wl = (x, z) -> {
            boolean inA = x >= ax0 && x <= ax1 && z >= az0 && z <= az1, inB = x >= bx0 && x <= bx1 && z >= bz0 && z <= bz1;
            boolean edgeA = inA && (x == ax0 || x == ax1 || z == az0 || z == az1), edgeB = inB && (x == bx0 || x == bx1 || z == bz0 || z == bz1);
            boolean interior = inA && !edgeA || inB && !edgeB;
            return (edgeA || edgeB) && !interior ? 1 : 0;
        };
        k.roof(-60, 11, -44, 30, y + 4, hc, wl, Kit.Roof.of("dark_oak_planks"), P.of("oak_planks"), true, y + 5);
        // a bridge between the two pads (different heights)
        k.bridge(-15, 0, 15, 4, 3, "spruce_planks", "spruce_slab", "stripped_spruce_log[axis=x]", "spruce_fence", "spruce_log", "lantern");
        w.joinAll();
        Lint.run(world, -64, 64, -34, 34, 40, 100, 40);
        return "test";
    }
}
