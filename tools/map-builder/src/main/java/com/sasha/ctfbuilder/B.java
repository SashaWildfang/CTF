package com.sasha.ctfbuilder;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Block-placing toolkit for the CTF maps: palettes, terrain, walls, towers, trees and decoration. */
public final class B {

    final World w;
    final Random r;
    private final Map<String, BlockData> cache = new HashMap<>();
    private final Noise noise;

    B(World world, long seed) {
        this.w = world;
        this.r = new Random(seed);
        this.noise = new Noise(seed);
    }

    // ---------------------------------------------------------------- basics

    /** The roof block for a team colour (blue and green terracotta read as purple and olive, so those use concrete). */
    static String roof(String team) {
        return switch (team) {
            case "blue" -> "blue_concrete";
            case "lime", "green" -> "green_concrete";
            case "yellow" -> "yellow_terracotta";
            default -> team + "_terracotta";
        };
    }

    /** Noise patches: the alt palette where the noise is above the threshold, otherwise the main one. */
    Pal patch(int x, int z, Pal main, Pal alt, double threshold, double scale) {
        return fbm(x + 431, z - 211, scale, 2) > threshold ? alt : main;
    }

    BlockData d(String spec) {
        return cache.computeIfAbsent(spec, s -> Bukkit.createBlockData(s.contains(":") ? s : "minecraft:" + s));
    }

    void set(int x, int y, int z, String spec) {
        set(x, y, z, d(spec));
    }

    void set(int x, int y, int z, BlockData data) {
        if (y < w.getMinHeight() || y >= w.getMaxHeight()) return;
        w.getBlockAt(x, y, z).setBlockData(data, false);
    }

    void set(int x, int y, int z, Pal palette) {
        set(x, y, z, palette.pick(r));
    }

    Block at(int x, int y, int z) {
        return w.getBlockAt(x, y, z);
    }

    boolean air(int x, int y, int z) {
        return w.getBlockAt(x, y, z).getType().isAir();
    }

    void fill(int x1, int y1, int z1, int x2, int y2, int z2, String spec) {
        fill(x1, y1, z1, x2, y2, z2, Pal.of(spec));
    }

    void fill(int x1, int y1, int z1, int x2, int y2, int z2, Pal palette) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) set(x, y, z, palette.pick(r));
    }

    void clear(int x1, int y1, int z1, int x2, int y2, int z2) {
        fill(x1, y1, z1, x2, y2, z2, "air");
    }

    /** Highest non-air block in the column, or minHeight - 1. */
    int top(int x, int z) {
        for (int y = 200; y >= w.getMinHeight(); y--) if (!w.getBlockAt(x, y, z).getType().isAir()) return y;
        return w.getMinHeight() - 1;
    }

    /** Highest solid (not plants, snow layers, flowers) block. */
    int ground(int x, int z) {
        for (int y = 200; y >= w.getMinHeight(); y--) {
            Material type = w.getBlockAt(x, y, z).getType();
            if (type.isSolid() && !type.name().contains("LEAVES") && !type.name().endsWith("_LOG")) return y;
        }
        return w.getMinHeight() - 1;
    }

    double n(double x, double z, double scale) {
        return noise.get(x / scale, z / scale);
    }

    /** Fractal noise in roughly -1..1. */
    double fbm(double x, double z, double scale, int octaves) {
        double sum = 0, amp = 1, norm = 0, f = 1;
        for (int i = 0; i < octaves; i++) {
            sum += noise.get(x * f / scale + i * 31.7, z * f / scale - i * 17.3) * amp;
            norm += amp;
            amp *= 0.5;
            f *= 2;
        }
        return sum / norm;
    }

    // ---------------------------------------------------------------- terrain

    /** One terrain column: top height and the blocks of each layer. */
    record Column(int top, Pal surface, Pal sub, Pal deep) {}

    interface Shape {
        /** Signed distance to the island edge (positive inside), in blocks. */
        double inside(int x, int z);
    }

    interface Columns {
        Column at(int x, int z);
    }

    /**
     * A floating island: columns from a height function, with a rocky underside that hangs deeper the
     * further from the edge (so the edges look like cliffs over the void).
     */
    void island(int minX, int maxX, int minZ, int maxZ, Shape shape, Columns columns) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double inside = shape.inside(x, z);
                if (inside < 0) continue;
                Column column = columns.at(x, z);
                if (column == null) continue;
                double edge = Math.min(1, inside / 18.0);
                int depth = (int) (5 + edge * 26 + fbm(x, z, 9, 2) * 6 * edge + n(x, z, 3) * 2);
                int bottom = column.top() - Math.max(3, depth);
                for (int y = bottom; y <= column.top(); y++) {
                    Pal palette = y == column.top() ? column.surface() : y >= column.top() - 3 ? column.sub() : column.deep();
                    set(x, y, z, palette.pick(r));
                }
            }
        }
    }

    /** Distance from a point to a rounded rectangle's edge, with a wobbly noisy outline. */
    double roundRect(int x, int z, int cx, int cz, int halfX, int halfZ, int corner, double wobble) {
        double dx = Math.abs(x - cx) - (halfX - corner), dz = Math.abs(z - cz) - (halfZ - corner);
        double outside = Math.hypot(Math.max(dx, 0), Math.max(dz, 0)) + Math.min(Math.max(dx, dz), 0) - corner;
        return -outside + fbm(x, z, 14, 3) * wobble;
    }

    // ---------------------------------------------------------------- building blocks

    /** A straight wall along x or z with optional battlements on top. */
    void wall(int x1, int z1, int x2, int z2, int y, int height, int thickness, Pal stone, Pal cap, boolean battlements) {
        boolean alongX = z1 == z2;
        int length = alongX ? Math.abs(x2 - x1) : Math.abs(z2 - z1);
        int sx = Integer.signum(x2 - x1), sz = Integer.signum(z2 - z1);
        for (int i = 0; i <= length; i++) {
            int x = x1 + sx * i, z = z1 + sz * i;
            for (int t = 0; t < thickness; t++) {
                int wx = alongX ? x : x + t, wz = alongX ? z + t : z;
                for (int dy = 0; dy < height; dy++) set(wx, y + dy, wz, stone);
                set(wx, y + height, wz, cap);
            }
            if (battlements && i % 2 == 0) {
                int bx = alongX ? x : x + thickness - 1, bz = alongX ? z + thickness - 1 : z;
                set(bx, y + height + 1, bz, stone);
                int ix = alongX ? x : x, iz = alongX ? z : z;
                if (thickness > 1) set(ix, y + height + 1, iz, stone);
            }
        }
    }

    /** A round tower with arrow slits, a floor platform with battlements, and an optional cone roof. */
    void tower(int cx, int cz, int y, int radius, int height, Pal walls, Pal floor, String roof, boolean battlements) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > radius + 0.4) continue;
                boolean shell = dist > radius - 0.9;
                for (int dy = 0; dy < height; dy++) {
                    if (shell) {
                        boolean slit = (dy % 5 == 3) && (dx == 0 || dz == 0) && Math.abs(dx) + Math.abs(dz) == radius;
                        set(cx + dx, y + dy, cz + dz, slit ? d("air") : walls.pick(r));
                    } else if (dy == 0 || dy % 6 == 0) {
                        set(cx + dx, y + dy, cz + dz, floor);
                    } else {
                        set(cx + dx, y + dy, cz + dz, "air");
                    }
                }
                set(cx + dx, y + height, cz + dz, shell ? walls.pick(r) : floor.pick(r));
                if (battlements && shell && ((dx + dz) & 1) == 0) set(cx + dx, y + height + 1, cz + dz, walls);
            }
        }
        if (roof != null) cone(cx, y + height + 1, cz, radius + 1, roof);
    }

    /** A stepped cone roof. */
    void cone(int cx, int y, int cz, int radius, String block) {
        int level = 0;
        for (double rr = radius; rr > 0.4; rr -= 0.75, level++) {
            for (int dx = -radius; dx <= radius; dx++)
                for (int dz = -radius; dz <= radius; dz++) {
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    if (dist <= rr && dist > rr - 1.2) set(cx + dx, y + level, cz + dz, block);
                }
        }
        set(cx, y + level, cz, block);
        set(cx, y + level + 1, cz, "lightning_rod");
    }

    /** A spruce tree: a trunk and layered, slightly ragged needle rings. */
    void spruce(int x, int y, int z, int height, boolean snowy) {
        for (int dy = 0; dy < height; dy++) set(x, y + dy, z, "spruce_log");
        int layers = height - 2;
        for (int i = 0; i < layers; i++) {
            int yy = y + 2 + i;
            int radius = (int) Math.round((layers - i) / (double) layers * (height / 3.2) + (i % 2 == 0 ? 0.6 : 0));
            for (int dx = -radius; dx <= radius; dx++)
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz > radius * radius + 0.5) continue;
                    if ((dx != 0 || dz != 0) && air(x + dx, yy, z + dz)) {
                        if (Math.abs(dx) == radius && Math.abs(dz) == radius && r.nextBoolean()) continue;
                        set(x + dx, yy, z + dz, "spruce_leaves[persistent=true]");
                        if (snowy && air(x + dx, yy + 1, z + dz) && r.nextInt(3) > 0) set(x + dx, yy + 1, z + dz, "snow[layers=1]");
                    }
                }
        }
        set(x, y + height, z, "spruce_leaves[persistent=true]");
        set(x, y + height + 1, z, "spruce_leaves[persistent=true]");
        if (snowy) set(x, y + height + 2, z, "snow[layers=2]");
    }

    /** A rounded deciduous tree (oak, birch, dark oak, cherry...). */
    void blobTree(int x, int y, int z, int height, String log, String leaves) {
        for (int dy = 0; dy < height; dy++) set(x, y + dy, z, log);
        int cy = y + height - 1;
        int radius = 2 + r.nextInt(2);
        for (int dx = -radius; dx <= radius; dx++)
            for (int dy = -1; dy <= radius; dy++)
                for (int dz = -radius; dz <= radius; dz++) {
                    double dist = Math.sqrt(dx * dx + dy * dy * 1.6 + dz * dz);
                    if (dist <= radius + 0.3 - r.nextDouble() * 0.6 && air(x + dx, cy + dy, z + dz)) {
                        set(x + dx, cy + dy, z + dz, leaves + "[persistent=true]");
                    }
                }
    }

    /** A lumpy rock. */
    void boulder(int x, int y, int z, double radius, Pal stone) {
        int ir = (int) Math.ceil(radius);
        for (int dx = -ir; dx <= ir; dx++)
            for (int dy = -ir; dy <= ir; dy++)
                for (int dz = -ir; dz <= ir; dz++) {
                    double dist = Math.sqrt(dx * dx + dy * dy * 1.4 + dz * dz) + n(x + dx, z + dz + dy * 7, 2) * 0.8;
                    if (dist <= radius) set(x + dx, y + dy, z + dz, stone);
                }
    }

    /** A lamp post: fence pole with a hanging lantern. */
    void lampPost(int x, int y, int z, String fence) {
        for (int dy = 0; dy < 3; dy++) set(x, y + dy, z, fence);
        set(x, y + 3, z, fence);
        set(x, y + 4, z, "lantern[hanging=false]");
    }

    /** A standing banner with a rotation 0-15 (0 = facing south). */
    void banner(int x, int y, int z, String colour, int rotation) {
        set(x, y, z, colour + "_banner[rotation=" + rotation + "]");
    }

    void wallBanner(int x, int y, int z, String colour, String facing) {
        set(x, y, z, colour + "_wall_banner[facing=" + facing + "]");
    }

    /** Re-surfaces the ground along a path: replaces the top block of each column with the palette. */
    void path(List<int[]> points, int width, Pal surface) {
        for (int i = 0; i + 1 < points.size(); i++) {
            int[] a = points.get(i), b = points.get(i + 1);
            int steps = Math.max(Math.abs(b[0] - a[0]), Math.abs(b[1] - a[1]));
            for (int s = 0; s <= steps; s++) {
                double t = steps == 0 ? 0 : s / (double) steps;
                int px = (int) Math.round(a[0] + (b[0] - a[0]) * t), pz = (int) Math.round(a[1] + (b[1] - a[1]) * t);
                for (int dx = -width; dx <= width; dx++)
                    for (int dz = -width; dz <= width; dz++) {
                        if (dx * dx + dz * dz > width * width + 1) continue;
                        int gx = px + dx, gz = pz + dz;
                        int gy = ground(gx, gz);
                        if (gy < w.getMinHeight()) continue;
                        Material type = at(gx, gy, gz).getType();
                        if (type.name().contains("GRASS_BLOCK") || type == Material.DIRT || type == Material.COARSE_DIRT
                                || type == Material.SNOW_BLOCK || type == Material.SAND || type == Material.RED_SAND
                                || type == Material.PODZOL || type == Material.MOSS_BLOCK || type == Material.GRAVEL || type == Material.MUD) {
                            set(gx, gy, gz, surface);
                            Material above = at(gx, gy + 1, gz).getType();
                            if (!above.isSolid() && !above.isAir()) set(gx, gy + 1, gz, "air");
                        }
                    }
            }
        }
    }

    static List<int[]> pts(int... xz) {
        List<int[]> list = new ArrayList<>();
        for (int i = 0; i + 1 < xz.length; i += 2) list.add(new int[] {xz[i], xz[i + 1]});
        return list;
    }

    /** Scatters plants on grass tops. */
    void plants(int minX, int maxX, int minZ, int maxZ, double chance, Pal plants, String onlyOn) {
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++) {
                if (r.nextDouble() > chance) continue;
                int y = top(x, z);
                if (y < w.getMinHeight() || !at(x, y, z).getType().name().equals(onlyOn)) continue;
                set(x, y + 1, z, plants);
            }
    }

    // ---------------------------------------------------------------- palettes

    /** Weighted block choices: Pal.of("stone_bricks", 6, "cracked_stone_bricks", 2, "mossy_stone_bricks", 1). */
    static final class Pal {
        private final List<BlockData> blocks = new ArrayList<>();
        private final List<Integer> weights = new ArrayList<>();
        private int total;

        static Pal of(Object... specs) {
            Pal pal = new Pal();
            for (int i = 0; i < specs.length; i++) {
                String spec = (String) specs[i];
                int weight = 1;
                if (i + 1 < specs.length && specs[i + 1] instanceof Integer next) {
                    weight = next;
                    i++;
                }
                pal.blocks.add(Bukkit.createBlockData(spec.contains(":") ? spec : "minecraft:" + spec));
                pal.weights.add(weight);
                pal.total += weight;
            }
            return pal;
        }

        BlockData pick(Random random) {
            int roll = random.nextInt(total);
            for (int i = 0; i < blocks.size(); i++) {
                roll -= weights.get(i);
                if (roll < 0) return blocks.get(i);
            }
            return blocks.get(0);
        }
    }

    // ---------------------------------------------------------------- noise

    /** Smooth 2D value noise. */
    static final class Noise {
        private final int[] perm = new int[512];

        Noise(long seed) {
            Random random = new Random(seed);
            int[] p = new int[256];
            for (int i = 0; i < 256; i++) p[i] = i;
            for (int i = 255; i > 0; i--) {
                int j = random.nextInt(i + 1);
                int t = p[i];
                p[i] = p[j];
                p[j] = t;
            }
            for (int i = 0; i < 512; i++) perm[i] = p[i & 255];
        }

        private double grad(int x, int z) {
            return perm[(perm[x & 255] + z) & 511] / 127.5 - 1;
        }

        double get(double x, double z) {
            int x0 = (int) Math.floor(x), z0 = (int) Math.floor(z);
            double fx = x - x0, fz = z - z0;
            double u = fx * fx * (3 - 2 * fx), v = fz * fz * (3 - 2 * fz);
            double a = grad(x0, z0), b = grad(x0 + 1, z0), c = grad(x0, z0 + 1), d = grad(x0 + 1, z0 + 1);
            return (a + (b - a) * u) * (1 - v) + (c + (d - c) * u) * v;
        }
    }
}
