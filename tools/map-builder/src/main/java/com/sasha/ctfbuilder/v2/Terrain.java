package com.sasha.ctfbuilder.v2;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/** Floating islands: organic outlines, rolling tops, layered cliffs and long tapered undersides. */
public final class Terrain {

    /** One island, in the built (canonical) part of the map. */
    public enum Shape { ELLIPSE, CRESCENT, BLOB, MESA, TERRACE, SPINE }

    public static final class Island {
        public final int cx, cz, top;
        public final double rx, rz, angle;
        public double hill = 3, depth = 22, trees = 1;
        public String name;
        double[][] cones;   // hanging rock cones under the island: {x, z, radius, extra depth}
        public Shape shape = Shape.ELLIPSE;
        public double wobble = 0.28;
        /** BLOB: extra lobes {dx, dz, radius}; CRESCENT: the bite {dx, dz, scale}. */
        public double[][] lobes;
        public double[] bite;
        /** Terrace step height (TERRACE). */
        public int step = 3;
        /** Sand (or the theme's beach) around the rim. */
        public String beach;
        /** A different top for this island (e.g. ice, sand). */
        public W.P surface;

        public Island shape(Shape s) { this.shape = s; return this; }
        public Island wobble(double v) { this.wobble = v; return this; }
        public Island lobes(double[]... l) { this.lobes = l; this.shape = Shape.BLOB; return this; }
        public Island bite(double dx, double dz, double scale) { this.bite = new double[] {dx, dz, scale}; this.shape = Shape.CRESCENT; return this; }
        public Island beach(String b) { this.beach = b; return this; }
        public Island surface(W.P p) { this.surface = p; return this; }
        public Island step(int s) { this.step = s; return this; }

        public Island(String name, int cx, int cz, double rx, double rz, double angle, int top) {
            this.name = name;
            this.cx = cx;
            this.cz = cz;
            this.rx = rx;
            this.rz = rz;
            this.angle = angle;
            this.top = top;
        }

        public Island hill(double h) { this.hill = h; return this; }
        public Island depth(double d) { this.depth = d; return this; }
        public Island trees(double f) { this.trees = f; return this; }
    }

    /** A level area (for a base, a monument, the hill...), blended into the land around it. */
    public record Flat(int x, int z, double radius, int y) {}

    /** A place nothing grows on (paths, buildings). */
    public record Keep(int x, int z, double radius) {}

    final W w;
    final Theme t;
    public final List<Island> islands = new ArrayList<>();
    public final List<Flat> flats = new ArrayList<>();
    public final List<Keep> keeps = new ArrayList<>();

    public Terrain(W w, Theme t) {
        this.w = w;
        this.t = t;
    }

    public Island add(Island island) {
        islands.add(island);
        return island;
    }

    // ---------------------------------------------------------------- shape

    /** How far inside an island a point is: 1 at the centre, 0 at the edge, negative outside. */
    double inside(Island i, int x, int z) {
        double dx = x - i.cx, dz = z - i.cz;
        double c = Math.cos(-i.angle), s = Math.sin(-i.angle);
        double u = dx * c - dz * s, v = dx * s + dz * c;
        double d = Math.sqrt(u * u / (i.rx * i.rx) + v * v / (i.rz * i.rz));
        double wobble = w.fbm(x * 1.0 + i.cx * 7, z * 1.0 + i.cz * 3, 20, 3) * i.wobble + w.n2(x, z, 5) * 0.05;
        if (i.shape == Shape.MESA) wobble = w.fbm(x * 1.0 + i.cx * 7, z * 1.0 + i.cz * 3, 9, 2) * i.wobble * 0.7 + w.n2(x, z, 3) * 0.04;
        double in = 1 - (d + wobble);
        if (i.lobes != null) {
            for (double[] lobe : i.lobes) {
                double lu = u - lobe[0], lv = v - lobe[1];
                double ld = Math.sqrt(lu * lu + lv * lv) / lobe[2];
                in = Math.max(in, 1 - (ld + wobble));
            }
        }
        if (i.bite != null) {
            double bu = u - i.bite[0] * i.rx, bv = v - i.bite[1] * i.rz;
            double bd = Math.sqrt(bu * bu / (i.rx * i.rx * i.bite[2] * i.bite[2]) + bv * bv / (i.rz * i.rz * i.bite[2] * i.bite[2]));
            in = Math.min(in, (bd - 1) * 0.9 + w.n2(x, z, 6) * 0.04);
        }
        return in;
    }

    /** The island a column belongs to most (for per-island surfaces and terraces), or null. */
    Island owner(int x0, int z0) {
        Island best = null;
        double bestIn = 0;
        for (int k = 0; k < w.copies; k++) {
            int[] p = turn(x0, z0, -k);
            for (Island i : islands) {
                double in = inside(i, p[0], p[1]);
                if (in > bestIn) {
                    bestIn = in;
                    best = i;
                }
            }
        }
        return best;
    }

    /** Point (x, z) turned by the k-th copy's rotation (or back with negative k). */
    int[] turn(int x, int z, int k) {
        int turns = w.copies == 2 ? k * 2 : k;
        turns = Math.floorMod(turns, 4);
        for (int i = 0; i < turns; i++) {
            int nx = -z, nz = x;
            x = nx;
            z = nz;
        }
        return new int[] {x, z};
    }

    /** How far inside any island (or any island's copy) a point is. */
    public double insideAny(int x, int z) {
        double best = -1;
        for (int k = 0; k < w.copies; k++) {
            int[] p = turn(x, z, -k);
            for (Island i : islands) best = Math.max(best, inside(i, p[0], p[1]));
        }
        return best;
    }

    double[][] cones(Island i) {
        if (i.cones != null) return i.cones;
        java.util.Random random = new java.util.Random(i.cx * 31L + i.cz * 17L + 5);
        int n = Math.max(2, (int) (i.rx * i.rz / 70));
        i.cones = new double[n][];
        for (int k = 0; k < n; k++) {
            double a = random.nextDouble() * Math.PI * 2, rr = Math.sqrt(random.nextDouble()) * 0.7;
            i.cones[k] = new double[] {i.cx + Math.cos(a) * rr * i.rx, i.cz + Math.sin(a) * rr * i.rz, 3 + random.nextDouble() * Math.min(9, i.rx / 2.5),
                    i.depth * (0.5 + random.nextDouble() * 0.9)};
        }
        return i.cones;
    }

    static double smooth(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    /** {top, bottom} of the land at (x, z), or null where there's none. */
    public int[] column(int x0, int z0) {
        int top = Integer.MIN_VALUE, bottom = Integer.MAX_VALUE;
        for (int k = 0; k < w.copies; k++) {
        int[] p = turn(x0, z0, -k);
        int x = p[0], z = p[1];
        for (Island i : islands) {
            double in = inside(i, x, z);
            if (in < 0) continue;
            double edge = smooth(in / (i.shape == Shape.MESA ? 0.06 : 0.35));
            double h;
            if (i.shape == Shape.TERRACE) {
                double raw = i.hill * Math.min(1, in / 0.7) + w.fbm(x + 300, z - 120, 30, 2) * 1.2;
                h = i.top + Math.floor(Math.max(0, raw) / i.step) * i.step + Math.round(edge);
            } else if (i.shape == Shape.MESA) {
                h = i.top + i.hill * w.fbm(x + 300, z - 120, 40, 2) * 0.4 * edge + Math.round(edge);
            } else {
                h = i.top + i.hill * w.fbm(x + 300, z - 120, 34, 3) * edge + Math.round(1.5 * edge);
            }
            int t0 = (int) Math.round(h);
            double taper = i.shape == Shape.MESA ? Math.pow(Math.min(1, in / 0.25), 0.35) : Math.pow(Math.min(1, in / 0.9), 0.75);
            double depth = 3 + i.depth * taper * (0.65 + 0.35 * (w.fbm(x - 77, z + 51, 16, 2) + 1) / 2);
            for (double[] cone : cones(i)) {
                double d = Math.hypot(x - cone[0], z - cone[1]);
                if (d < cone[2]) depth = Math.max(depth, 3 + i.depth * 0.5 * Math.min(1, in * 2.5) + cone[3] * Math.pow(1 - d / cone[2], 1.6));
            }
            if (in < 0.05 && i.shape != Shape.MESA) t0 -= 1; // rounded rim
            top = Math.max(top, t0);
            bottom = Math.min(bottom, t0 - (int) Math.round(depth));
        }
        }
        int x = x0, z = z0;
        if (top == Integer.MIN_VALUE) return null;
        for (Flat f : flats) {
            double d = Math.hypot(x - f.x, z - f.z);
            if (d <= f.radius) top = f.y;
            else if (d <= f.radius + 5) {
                double k = smooth((d - f.radius) / 5);
                top = (int) Math.round(f.y * (1 - k) + top * k);
            }
        }
        bottom = Math.min(bottom, top - 3);
        return new int[] {top, bottom};
    }

    // ---------------------------------------------------------------- building it

    public void build() {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (Island i : islands) {
            double r = Math.max(i.rx, i.rz) * 1.35 + 4;
            for (int k = 0; k < w.copies; k++) {
                int[] c = turn(i.cx, i.cz, k);
                minX = Math.min(minX, (int) (c[0] - r));
                maxX = Math.max(maxX, (int) (c[0] + r));
                minZ = Math.min(minZ, (int) (c[1] - r));
                maxZ = Math.max(maxZ, (int) (c[1] + r));
            }
        }
        java.util.Map<Long, int[]> cols = new java.util.HashMap<>();
        for (int x = minX - 1; x <= maxX + 1; x++)
            for (int z = minZ - 1; z <= maxZ + 1; z++) {
                int[] c = w.toCanon(x, z);
                int[] col = column(c[0], c[1]);
                if (col != null) cols.put(key(x, z), col);
            }
        heights = cols;
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++) {
                if (!w.canon(x, z)) continue;
                int[] col = cols.get(key(x, z));
                if (col == null) continue;
                // how far the land drops to the lowest neighbour: a terrace face or a cliff edge
                int drop = 0;
                boolean rim = false;
                for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int[] n = cols.get(key(x + d[0], z + d[1]));
                    if (n == null) rim = true;
                    else drop = Math.max(drop, col[0] - n[0]);
                }
                fillColumn(x, z, col[0], col[1], drop, rim);
            }
    }

    private java.util.Map<Long, int[]> heights = java.util.Map.of();

    static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    void fillColumn(int x, int z, int top, int bottom) {
        fillColumn(x, z, top, bottom, 0, false);
    }

    void fillColumn(int x, int z, int top, int bottom, int drop, boolean rim) {
        Island owner = owner(x, z);
        int soil = 1 + (int) Math.round((w.n(x * 1.3, z * 1.3, 7) + 1) * 1.3);
        boolean terrace = owner != null && owner.shape == Shape.TERRACE && drop >= 2;
        for (int y = bottom; y <= top; y++) {
            String spec;
            int down = top - y;
            if (terrace && down >= 1 && down <= drop) spec = t.wall.pick(w.r); // a dry-stone retaining wall
            else if (down == 0 && owner != null && owner.beach != null && (rim || insideAny(x, z) < 0.10)) spec = owner.beach;
            else if (down == 0 && owner != null && owner.surface != null) spec = w.fbm(x + 913, z - 377, 13, 2) > 0.45 ? surfaceAt(x, z) : owner.surface.pick(w.r);
            else if (down == 0) spec = surfaceAt(x, z);
            else if (down <= soil) spec = t.soil.pick(w.r);
            else if (y < bottom + 2 + (int) Math.round((w.n2(x, z, 3) + 1) * 1.5)) spec = t.under.pick(w.r);
            else {
                double warp = w.n3(x, y, z, 23) * 5 + w.n(x, z, 40) * 3;
                int band = Math.floorMod((int) Math.floor((y + warp) / 2.6), t.bands.length);
                spec = w.n3(x * 2.1, y * 2.1, z * 2.1, 5) > 0.55 ? t.speck.pick(w.r) : t.bands[band];
            }
            w.set(x, y, z, spec);
        }
        // hanging from the underside
        if (w.r.nextDouble() < 0.10) hang(x, bottom - 1, z);
    }

    String surfaceAt(int x, int z) {
        double a = w.fbm(x + 913, z - 377, 13, 2), b = w.n2(x + 51, z + 19, 8);
        if (a > 0.30) return t.surface2.pick(w.r);
        if (b > 0.42) return t.surface3.pick(w.r);
        return t.surface.pick(w.r);
    }

    void hang(int x, int y, int z) {
        String kind = t.hangers[w.r.nextInt(t.hangers.length)];
        int length = 1 + w.r.nextInt(5);
        switch (kind.replaceAll("\\[.*", "")) {
            case "vine" -> {
                for (int i = 0; i < length - 1; i++) w.set(x, y - i, z, "cave_vines_plant[berries=" + (w.r.nextInt(5) == 0) + "]");
                w.set(x, y - length + 1, z, "cave_vines[berries=" + (w.r.nextInt(4) == 0) + "]");
            }
            case "pointed_dripstone" -> {
                String[] parts = length >= 4 ? new String[] {"base", "middle", "frustum", "tip"} : length == 3 ? new String[] {"base", "frustum", "tip"}
                        : length == 2 ? new String[] {"frustum", "tip"} : new String[] {"tip"};
                for (int i = 0; i < parts.length; i++) w.set(x, y - i, z, "pointed_dripstone[vertical_direction=down,thickness=" + parts[i] + "]");
            }
            case "weeping_vines_plant" -> {
                for (int i = 0; i < length - 1; i++) w.set(x, y - i, z, "weeping_vines_plant");
                w.set(x, y - length + 1, z, "weeping_vines");
            }
            case "pale_hanging_moss" -> {
                for (int i = 0; i < length - 1; i++) w.set(x, y - i, z, "pale_hanging_moss[tip=false]");
                w.set(x, y - length + 1, z, "pale_hanging_moss[tip=true]");
            }
            case "glow_lichen" -> w.set(x, y, z, "glow_lichen[up=true]");
            case "chorus_plant" -> {
                for (int i = 0; i < length; i++) w.set(x, y - i, z, "chorus_plant[up=true,down=true]");
                w.set(x, y - length, z, "chorus_flower[age=5]");
            }
            default -> w.set(x, y, z, kind);
        }
    }

    // ---------------------------------------------------------------- after buildings: paths and the finishing touches

    /** A winding path across the land between points (canonical coordinates). */
    public void path(int width, int... xz) {
        for (int i = 0; i + 3 < xz.length; i += 2) {
            int ax = xz[i], az = xz[i + 1], bx = xz[i + 2], bz = xz[i + 3];
            int steps = (int) Math.max(1, Math.hypot(bx - ax, bz - az));
            for (int s = 0; s <= steps; s++) {
                double k = s / (double) steps;
                double px = ax + (bx - ax) * k + w.n(s * 0.3 + ax, az, 9) * 2.5, pz = az + (bz - az) * k + w.n2(ax, s * 0.3 + az, 9) * 2.5;
                for (int dx = -width - 1; dx <= width + 1; dx++)
                    for (int dz = -width - 1; dz <= width + 1; dz++) {
                        double d = Math.hypot(dx, dz) + w.n(px + dx, pz + dz, 2) * 0.8;
                        if (d > width + 0.3) continue;
                        int x = (int) Math.round(px) + dx, z = (int) Math.round(pz) + dz;
                        if (!w.canon(x, z)) continue;
                        int y = w.ground(x, z);
                        if (y < 0 || !natural(w.type(x, y, z))) continue;
                        w.set(x, y, z, t.path.pick(w.r));
                        if (!w.free(x, y + 1, z)) continue;
                        w.set(x, y + 1, z, "air");
                    }
                keeps.add(new Keep((int) Math.round(px), (int) Math.round(pz), width + 1.5));
            }
        }
    }

    /** Is this ground the island made (not a building)? */
    boolean natural(Material m) {
        String n = m.name();
        return n.contains("GRASS_BLOCK") || n.contains("DIRT") || n.contains("SAND") && !n.contains("SANDSTONE") || n.equals("SNOW_BLOCK")
                || n.contains("MOSS") || n.contains("PODZOL") || n.contains("MYCELIUM") || n.contains("NYLIUM") || n.equals("MUD")
                || n.equals("GRAVEL") || n.equals("END_STONE") || n.equals("BLACKSTONE") || n.equals("BASALT") || n.equals("SMOOTH_BASALT")
                || n.equals("NETHERRACK") || n.equals("STONE") || n.equals("TERRACOTTA") || n.equals("CALCITE") || n.equals("PACKED_ICE")
                || n.equals("SANDSTONE") || n.equals("RED_SANDSTONE") || n.equals("COARSE_DIRT") || n.equals("ROOTED_DIRT");
    }

    boolean kept(int x, int z) {
        return kept(x, z, 0);
    }

    /** Within {@code margin} blocks of a kept area (trees need room for their crowns). */
    boolean kept(int x, int z, double margin) {
        for (Keep k : keeps) if (Math.hypot(x - k.x(), z - k.z()) < k.radius() + margin) return true;
        return false;
    }

    /** Snow on everything open to the sky (snowy themes). */
    public void snow(int minX, int maxX, int minZ, int maxZ) {
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++) {
                if (!w.canon(x, z)) continue;
                int y = w.top(x, z);
                if (y < 0) continue;
                Material m = w.type(x, y, z);
                if (!m.isSolid() || m.name().contains("STAIRS") || m.name().contains("SLAB") || m.name().contains("FENCE") || m.name().contains("WALL")
                        || m.name().contains("GLASS") || m == Material.ICE || m == Material.BEACON) continue;
                if (w.r.nextInt(10) < 8) w.set(x, y + 1, z, "snow[layers=" + (1 + w.r.nextInt(m.name().contains("LEAVES") ? 1 : 3)) + "]");
            }
    }
}
