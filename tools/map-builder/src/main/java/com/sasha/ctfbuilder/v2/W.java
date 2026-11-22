package com.sasha.ctfbuilder.v2;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.structure.StructureRotation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * World writer with built-in symmetry. Everything is built once in the "canonical" part of the map (the
 * west half for 2 teams, the west quarter for 4) as the red team; every block is copied to the other
 * team's side, rotated (stairs, logs, banners turn with it) and recoloured: the token TEAM in a block
 * spec becomes red / blue / lime / yellow for each copy.
 */
public final class W {

    public final World w;
    public final Random r;
    public final int copies;               // 1, 2 or 4
    private final String[] teams;
    private final Map<String, BlockData> cache = new HashMap<>();
    private final Noise noise;
    private final Noise noise2;
    static final java.util.Set<String> warned = new java.util.HashSet<>();

    public W(World world, long seed, int copies) {
        this.w = world;
        this.r = new Random(seed);
        this.copies = copies;
        this.noise = new Noise(seed);
        this.noise2 = new Noise(seed * 31 + 7);
        this.teams = copies == 4 ? new String[] {"red", "lime", "blue", "yellow"} : copies == 2 ? new String[] {"red", "blue"} : new String[] {"red"};
    }

    // ---------------------------------------------------------------- symmetry

    /** Is (x, z) in the part of the map that gets built (the rest is copied)? */
    public boolean canon(int x, int z) {
        if (copies == 1) return true;
        if (copies == 2) return x < 0 || x == 0 && z <= 0;
        return x < 0 && (Math.abs(z) < -x || z == x) || x == 0 && z == 0;
    }

    /** The canonical point for any (x, z): rotated into the built part. */
    public int[] toCanon(int x, int z) {
        for (int k = 0; k < Math.max(1, copies); k++) {
            if (canon(x, z)) return new int[] {x, z};
            int nx = z, nz = -x; // rotate 90° counter-clockwise (the inverse of a copy's rotation)
            if (copies == 2) {
                nx = -x;
                nz = -z;
            }
            x = nx;
            z = nz;
        }
        return new int[] {x, z};
    }

    private static int[] rot(int x, int z, int k) {
        for (int i = 0; i < k; i++) {
            int nx = -z, nz = x;
            x = nx;
            z = nz;
        }
        return new int[] {x, z};
    }

    private static final StructureRotation[] ROTATIONS = {StructureRotation.NONE, StructureRotation.CLOCKWISE_90,
            StructureRotation.CLOCKWISE_180, StructureRotation.COUNTERCLOCKWISE_90};

    BlockData data(String spec, int copy) {
        String key = spec + "#" + copy;
        BlockData cached = cache.get(key);
        if (cached != null) return cached;
        String resolved = spec.replace("TEAM", teams[Math.min(copy, teams.length - 1)]);
        BlockData data;
        try {
            data = Bukkit.createBlockData(resolved.contains(":") ? resolved : "minecraft:" + resolved);
        } catch (IllegalArgumentException e) {
            if (warned.add(resolved)) Bukkit.getLogger().warning("[CtfBuilder] unknown block " + resolved);
            data = Bukkit.createBlockData(Material.AIR);
        }
        int turns = copies == 2 ? copy * 2 : copy;
        if (turns % 4 != 0) {
            data = data.clone();
            data.rotate(ROTATIONS[turns % 4]);
        }
        cache.put(key, data);
        return data;
    }

    /** Sets a block (and its symmetric copies). */
    public void set(int x, int y, int z, String spec) {
        if (y < w.getMinHeight() || y >= w.getMaxHeight()) return;
        boolean joins = joins(spec);
        boolean stairs = spec.contains("_stairs");
        for (int k = 0; k < copies; k++) {
            int[] p = rot(x, z, copies == 2 ? k * 2 : k);
            w.getBlockAt(p[0], y, p[1]).setBlockData(data(spec, k), false);
            if (joins) toJoin.add(new long[] {p[0], y, p[1]});
            if (stairs) toShape.add(new long[] {p[0], y, p[1]});
        }
    }

    // ---------------------------------------------------------------- connecting fences, panes, bars and walls

    /** Blocks whose sides join their neighbours: they're placed without physics, so they get joined at the end. */
    private final List<long[]> toJoin = new ArrayList<>();

    static boolean joins(String spec) {
        String s = spec.contains("[") ? spec.substring(0, spec.indexOf('[')) : spec;
        return s.endsWith("_fence") || s.endsWith("_pane") || s.equals("iron_bars") || s.endsWith("_wall") && !s.endsWith("_wall_banner")
                && !s.endsWith("_wall_sign") && !s.endsWith("_wall_head") && !s.endsWith("_wall_skull") && !s.endsWith("_wall_torch")
                && !s.endsWith("wall_hanging_sign") && !s.endsWith("_wall_fan");
    }

    private static final org.bukkit.block.BlockFace[] SIDES = {org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.EAST,
            org.bukkit.block.BlockFace.SOUTH, org.bukkit.block.BlockFace.WEST};

    /** Stairs placed without physics: their corner shapes are worked out at the end, the way the game does. */
    private final List<long[]> toShape = new ArrayList<>();

    private static boolean sameStairs(org.bukkit.block.data.BlockData data, org.bukkit.block.data.type.Stairs like) {
        return data instanceof org.bukkit.block.data.type.Stairs other && other.getHalf() == like.getHalf();
    }

    /** Vanilla's stair-shape rule (StairBlock#getStairsShape). */
    public void shapeStairs() {
        for (long[] p : toShape) {
            Block block = w.getBlockAt((int) p[0], (int) p[1], (int) p[2]);
            if (!(block.getBlockData() instanceof org.bukkit.block.data.type.Stairs stair)) continue;
            org.bukkit.block.BlockFace facing = stair.getFacing();
            org.bukkit.block.data.type.Stairs.Shape shape = org.bukkit.block.data.type.Stairs.Shape.STRAIGHT;
            org.bukkit.block.data.BlockData behind = block.getRelative(facing).getBlockData();
            if (sameStairs(behind, stair)) {
                org.bukkit.block.BlockFace d = ((org.bukkit.block.data.type.Stairs) behind).getFacing();
                if (axis(d) != axis(facing) && canTake(block, stair, d.getOppositeFace())) {
                    shape = d == ccw(facing) ? org.bukkit.block.data.type.Stairs.Shape.OUTER_LEFT : org.bukkit.block.data.type.Stairs.Shape.OUTER_RIGHT;
                }
            }
            if (shape == org.bukkit.block.data.type.Stairs.Shape.STRAIGHT) {
                org.bukkit.block.data.BlockData front = block.getRelative(facing.getOppositeFace()).getBlockData();
                if (sameStairs(front, stair)) {
                    org.bukkit.block.BlockFace d = ((org.bukkit.block.data.type.Stairs) front).getFacing();
                    if (axis(d) != axis(facing) && canTake(block, stair, d)) {
                        shape = d == ccw(facing) ? org.bukkit.block.data.type.Stairs.Shape.INNER_LEFT : org.bukkit.block.data.type.Stairs.Shape.INNER_RIGHT;
                    }
                }
            }
            if (stair.getShape() != shape) {
                stair.setShape(shape);
                block.setBlockData(stair, false);
            }
        }
        toShape.clear();
    }

    private static boolean canTake(Block block, org.bukkit.block.data.type.Stairs stair, org.bukkit.block.BlockFace dir) {
        org.bukkit.block.data.BlockData n = block.getRelative(dir).getBlockData();
        return !(n instanceof org.bukkit.block.data.type.Stairs other) || other.getFacing() != stair.getFacing() || other.getHalf() != stair.getHalf();
    }

    private static int axis(org.bukkit.block.BlockFace f) {
        return f == org.bukkit.block.BlockFace.NORTH || f == org.bukkit.block.BlockFace.SOUTH ? 1 : 0;
    }

    /** 90° counter-clockwise seen from above. */
    private static org.bukkit.block.BlockFace ccw(org.bukkit.block.BlockFace f) {
        return switch (f) {
            case NORTH -> org.bukkit.block.BlockFace.WEST;
            case WEST -> org.bukkit.block.BlockFace.SOUTH;
            case SOUTH -> org.bukkit.block.BlockFace.EAST;
            default -> org.bukkit.block.BlockFace.NORTH;
        };
    }

    /** Joins every fence, pane, bar and wall to what's next to it (call once everything is built). */
    public void joinAll() {
        shapeStairs();
        for (long[] p : toJoin) {
            Block block = w.getBlockAt((int) p[0], (int) p[1], (int) p[2]);
            BlockData data = block.getBlockData();
            String family = family(block.getType());
            if (family == null) continue;
            if (data instanceof org.bukkit.block.data.type.Wall wall) {
                boolean any = false;
                int count = 0;
                for (org.bukkit.block.BlockFace face : SIDES) {
                    Block next = block.getRelative(face);
                    boolean join = joinsTo(family, next);
                    Block above = block.getRelative(org.bukkit.block.BlockFace.UP);
                    org.bukkit.block.data.type.Wall.Height h = !join ? org.bukkit.block.data.type.Wall.Height.NONE
                            : above.getType().isSolid() && !above.getType().isAir() ? org.bukkit.block.data.type.Wall.Height.TALL
                            : org.bukkit.block.data.type.Wall.Height.LOW;
                    wall.setHeight(face, h);
                    if (join) count++;
                    any |= join;
                }
                boolean straight = count == 2 && (wall.getHeight(org.bukkit.block.BlockFace.NORTH) != org.bukkit.block.data.type.Wall.Height.NONE
                        && wall.getHeight(org.bukkit.block.BlockFace.SOUTH) != org.bukkit.block.data.type.Wall.Height.NONE
                        || wall.getHeight(org.bukkit.block.BlockFace.EAST) != org.bukkit.block.data.type.Wall.Height.NONE
                        && wall.getHeight(org.bukkit.block.BlockFace.WEST) != org.bukkit.block.data.type.Wall.Height.NONE);
                Block above = block.getRelative(org.bukkit.block.BlockFace.UP);
                wall.setUp(!any || !straight || !above.getType().isAir());
                block.setBlockData(wall, false);
            } else if (data instanceof org.bukkit.block.data.MultipleFacing facing) {
                for (org.bukkit.block.BlockFace face : SIDES) {
                    if (!facing.getAllowedFaces().contains(face)) continue;
                    facing.setFace(face, joinsTo(family, block.getRelative(face)));
                }
                block.setBlockData(facing, false);
            }
        }
        toJoin.clear();
    }

    private static String family(Material m) {
        String n = m.name();
        if (n.endsWith("_FENCE")) return n.startsWith("NETHER_BRICK") ? "nfence" : "fence";
        if (n.endsWith("_PANE") || m == Material.IRON_BARS) return "pane";
        if (n.endsWith("_WALL")) return "wall";
        return null;
    }

    private static boolean joinsTo(String family, Block next) {
        Material m = next.getType();
        if (m.isAir()) return false;
        String other = family(m);
        String n = m.name();
        if (family.equals("fence") || family.equals("nfence")) {
            if (other != null && other.equals(family)) return true;
            if (n.endsWith("_FENCE_GATE")) return true;
        } else if (family.equals("pane")) {
            if ("pane".equals(other) || "wall".equals(other)) return true;
        } else if (family.equals("wall")) {
            if ("wall".equals(other) || "pane".equals(other) || n.endsWith("_FENCE_GATE")) return true;
        }
        // a solid full block (not leaves or glass-like see-through things)
        return m.isOccluding() && !n.endsWith("LEAVES");
    }

    // ---------------------------------------------------------------- lights that hang from something

    /**
     * A lantern hanging under whatever is above: chains reach up to the ceiling (up to 6 blocks), so it never
     * floats. With nothing above it stands on the block below instead.
     */
    public void hangLight(int x, int y, int z, String lantern) {
        for (int up = 1; up <= 6; up++) {
            Material above = type(x, y + up, z);
            if (above.isAir()) continue;
            if (!above.isSolid() && !above.name().contains("CHAIN") && !above.name().endsWith("FENCE") && !above.name().endsWith("SLAB")) break;
            for (int k = 1; k < up; k++) set(x, y + k, z, "iron_chain[axis=y]");
            set(x, y, z, lantern + "[hanging=true]");
            return;
        }
        if (!air(x, y - 1, z) && type(x, y - 1, z).isSolid()) set(x, y, z, lantern + "[hanging=false]");
    }

    public void set(int x, int y, int z, P pal) {
        set(x, y, z, pal.pick(r));
    }

    /** Sets only if the spot is air (or replaceable plants). */
    public void soft(int x, int y, int z, String spec) {
        if (free(x, y, z)) set(x, y, z, spec);
    }

    public Block at(int x, int y, int z) {
        return w.getBlockAt(x, y, z);
    }

    public Material type(int x, int y, int z) {
        return w.getBlockAt(x, y, z).getType();
    }

    public boolean air(int x, int y, int z) {
        return w.getBlockAt(x, y, z).getType().isAir();
    }

    /** Air or something soft that building can replace (grass, flowers, snow layers). */
    public boolean free(int x, int y, int z) {
        Material m = type(x, y, z);
        return m.isAir() || !m.isSolid() && !m.name().contains("LADDER") && !m.name().contains("TORCH") && !m.name().contains("LANTERN") && !m.name().contains("BANNER") && !m.name().contains("CHAIN") && m != Material.WATER && m != Material.LAVA;
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, String spec) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) set(x, y, z, spec);
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, P pal) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) set(x, y, z, pal.pick(r));
    }

    /** Highest solid ground (not leaves, logs or plants) in a column, or min height - 1. */
    public int ground(int x, int z) {
        for (int y = 190; y > w.getMinHeight(); y--) {
            Material m = type(x, y, z);
            if (m.isAir() || !m.isSolid()) continue;
            String n = m.name();
            if (n.endsWith("LEAVES") || n.endsWith("_LOG") || n.endsWith("_STEM") || n.contains("WOOL") || n.contains("FENCE")) continue;
            return y;
        }
        return w.getMinHeight() - 1;
    }

    /** Highest non-air block. */
    public int top(int x, int z) {
        for (int y = 190; y > w.getMinHeight(); y--) if (!air(x, y, z)) return y;
        return w.getMinHeight() - 1;
    }

    // ---------------------------------------------------------------- noise

    public double n(double x, double z, double scale) {
        return noise.get(x / scale, z / scale);
    }

    public double n2(double x, double z, double scale) {
        return noise2.get(x / scale, z / scale);
    }

    public double fbm(double x, double z, double scale, int octaves) {
        double sum = 0, amp = 1, total = 0;
        for (int i = 0; i < octaves; i++) {
            sum += noise.get(x / scale + i * 17.3, z / scale - i * 9.1) * amp;
            total += amp;
            amp *= 0.5;
            scale /= 2;
        }
        return sum / total;
    }

    /** 3D-ish noise from two 2D layers (good enough for strata and ore-like speckles). */
    public double n3(double x, double y, double z, double scale) {
        return (noise.get(x / scale + y / scale * 0.71, z / scale) + noise2.get(x / scale, z / scale + y / scale * 0.63)) * 0.5;
    }

    // ---------------------------------------------------------------- palettes

    /** Weighted block specs: P.of("stone", 5, "andesite", 2). Specs can contain TEAM. */
    public static final class P {
        final List<String> specs = new ArrayList<>();
        final List<Integer> weights = new ArrayList<>();
        int total;

        public static P of(Object... specs) {
            P p = new P();
            for (int i = 0; i < specs.length; i++) {
                String spec = (String) specs[i];
                int weight = 1;
                if (i + 1 < specs.length && specs[i + 1] instanceof Integer next) {
                    weight = next;
                    i++;
                }
                if (weight <= 0) continue;
                p.specs.add(spec);
                p.weights.add(weight);
                p.total += weight;
            }
            return p;
        }

        public String pick(Random random) {
            int roll = random.nextInt(total);
            for (int i = 0; i < specs.size(); i++) {
                roll -= weights.get(i);
                if (roll < 0) return specs.get(i);
            }
            return specs.get(0);
        }

        /** Picks by a value in [0,1) instead of randomly (for smooth noise-driven blends). */
        public String at(double t) {
            t = Math.max(0, Math.min(0.9999, t));
            int target = (int) (t * total);
            for (int i = 0; i < specs.size(); i++) {
                target -= weights.get(i);
                if (target < 0) return specs.get(i);
            }
            return specs.get(specs.size() - 1);
        }
    }

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
