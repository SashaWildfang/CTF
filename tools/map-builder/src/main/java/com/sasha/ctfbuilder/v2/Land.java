package com.sasha.ctfbuilder.v2;

import com.sasha.ctfbuilder.v2.W.P;

import java.util.HashMap;
import java.util.Map;

/**
 * Terrain from height fields, for maps whose land isn't a set of round islands: an atoll, a canyon, ridges.
 * A map gives the surface height of its half (NaN = void), and Land builds the columns: the chosen surface
 * on top, soil, then rock in warped strata, the underside tapering away from the edges in hanging cones.
 * Water can be poured over low ground up to a level. Only the built half is computed; the writer copies it.
 */
public final class Land {

    public interface Field { double at(int x, int z); }

    /** Picks the top block: (x, z, y, steep = largest drop to a neighbour, edge = distance to the void). */
    public interface Surface { String at(int x, int z, int y, int steep, double edge); }

    final W w;
    final Theme t;
    public Field top;
    public Field water;                 // water surface level (NaN = none)
    public Surface surface;
    public double depth = 22;           // how far the underside hangs at the middle
    public double cones = 0.018;        // hanging cones per column (roughly)
    public double warp = 1.0;           // how much the rock strata wave (badlands want them nearly flat)
    public P soil;
    public String[] bands;
    public P speck, under;
    public int minX, maxX, minZ, maxZ;

    private final Map<Long, Integer> tops = new HashMap<>();
    private final Map<Long, Double> edges = new HashMap<>();

    public Land(W w, Theme t, int minX, int maxX, int minZ, int maxZ) {
        this.w = w;
        this.t = t;
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
        this.soil = t.soil;
        this.bands = t.bands;
        this.speck = t.speck;
        this.under = t.under;
    }

    static long key(int x, int z) {
        return Terrain.key(x, z);
    }

    /** The land's surface at any (x, z) (looked up in the built half), or Integer.MIN_VALUE for void. */
    public int topAt(int x, int z) {
        int[] c = w.toCanon(x, z);
        Integer v = tops.get(key(c[0], c[1]));
        return v == null ? Integer.MIN_VALUE : v;
    }

    /** Distance from (x, z) to the nearest void, in blocks (0 outside). */
    public double edgeAt(int x, int z) {
        int[] c = w.toCanon(x, z);
        Double v = edges.get(key(c[0], c[1]));
        return v == null ? 0 : v;
    }

    public void build() {
        // 1. heights, over the whole box (both halves read the built half's field through toCanon)
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++) {
                if (!w.canon(x, z)) continue;
                double h = top.at(x, z);
                if (Double.isNaN(h)) continue;
                tops.put(key(x, z), (int) Math.round(h));
            }
        // 2. distance to the void (two-pass chamfer over the full box)
        int sx = maxX - minX + 1, sz = maxZ - minZ + 1;
        double[] d = new double[sx * sz];
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++) d[(x - minX) * sz + (z - minZ)] = topAt(x, z) == Integer.MIN_VALUE ? 0 : 1e9;
        double s2 = Math.sqrt(2);
        for (int i = 0; i < sx; i++)
            for (int j = 0; j < sz; j++) {
                int k = i * sz + j;
                if (d[k] == 0) continue;
                double v = d[k];
                if (i > 0) v = Math.min(v, d[k - sz] + 1);
                if (j > 0) v = Math.min(v, d[k - 1] + 1);
                if (i > 0 && j > 0) v = Math.min(v, d[k - sz - 1] + s2);
                if (i > 0 && j < sz - 1) v = Math.min(v, d[k - sz + 1] + s2);
                if (i == 0 || j == 0 || i == sx - 1 || j == sz - 1) v = Math.min(v, 1);
                d[k] = v;
            }
        for (int i = sx - 1; i >= 0; i--)
            for (int j = sz - 1; j >= 0; j--) {
                int k = i * sz + j;
                if (d[k] == 0) continue;
                double v = d[k];
                if (i < sx - 1) v = Math.min(v, d[k + sz] + 1);
                if (j < sz - 1) v = Math.min(v, d[k + 1] + 1);
                if (i < sx - 1 && j < sz - 1) v = Math.min(v, d[k + sz + 1] + s2);
                if (i < sx - 1 && j > 0) v = Math.min(v, d[k + sz - 1] + s2);
                d[k] = v;
            }
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++) {
                if (!w.canon(x, z)) continue;
                double v = d[(x - minX) * sz + (z - minZ)];
                if (v > 0) edges.put(key(x, z), v);
            }
        // 3. columns
        for (var e : tops.entrySet()) {
            int x = (int) (e.getKey() >> 32), z = (int) (long) e.getKey();
            int y = e.getValue();
            double edge = edges.getOrDefault(e.getKey(), 1.0);
            int steep = 0;
            for (int[] n : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int ny = topAt(x + n[0], z + n[1]);
                steep = Math.max(steep, ny == Integer.MIN_VALUE ? 3 : y - ny);
            }
            // the underside: deep in the middle, tapering to the rim, with hanging cones
            double taper = Math.pow(Math.min(1, edge / 14.0), 0.8);
            double hang = 3 + depth * taper * (0.7 + 0.3 * (w.fbm(x - 77, z + 51, 16, 2) + 1) / 2);
            double cone = w.fbm(x * 1.7 + 300, z * 1.7 - 200, 9, 2);
            if (cone > 0.35 && edge > 3) hang += (cone - 0.35) * 40 * Math.min(1, edge / 10.0);
            int bottom = y - (int) Math.round(hang);
            int soilDepth = 1 + (int) Math.round((w.n(x * 1.3, z * 1.3, 7) + 1) * 1.3);
            for (int yy = bottom; yy <= y; yy++) {
                int down = y - yy;
                String spec;
                if (down == 0) spec = surface.at(x, z, y, steep, edge);
                else if (down <= soilDepth && steep < 3) spec = soil.pick(w.r);
                else if (yy < bottom + 2 + (int) Math.round((w.n2(x, z, 3) + 1) * 1.5)) spec = under.pick(w.r);
                else {
                    double warp = (w.n3(x, yy, z, 23) * 5 + w.n(x, z, 40) * 3) * this.warp;
                    int band = Math.floorMod((int) Math.floor((yy + warp) / 2.6), bands.length);
                    spec = w.n3(x * 2.1, yy * 2.1, z * 2.1, 5) > 0.55 ? speck.pick(w.r) : bands[band];
                }
                w.set(x, yy, z, spec);
            }
            if (water != null) {
                double lvl = water.at(x, z);
                if (!Double.isNaN(lvl)) for (int yy = y + 1; yy <= (int) Math.round(lvl); yy++) w.set(x, yy, z, "water");
            }
        }
    }

    // ---------------------------------------------------------------- shapes (inside > 0, edge = 0, outside < 0)

    /** A rotated ellipse: 1 at the centre, 0 at the rim, negative outside. */
    public static double ellipse(double x, double z, double cx, double cz, double rx, double rz, double angle) {
        double dx = x - cx, dz = z - cz;
        double c = Math.cos(angle), s = Math.sin(angle);
        double u = (dx * c + dz * s) / rx, v = (-dx * s + dz * c) / rz;
        return 1 - Math.sqrt(u * u + v * v);
    }

    /** Distance from (x, z) to the segment a-b. */
    public static double segment(double x, double z, double ax, double az, double bx, double bz) {
        double vx = bx - ax, vz = bz - az;
        double t = Math.max(0, Math.min(1, ((x - ax) * vx + (z - az) * vz) / (vx * vx + vz * vz)));
        return Math.hypot(x - (ax + vx * t), z - (az + vz * t));
    }

    /** Where along a-b (0..1) the nearest point to (x, z) is. */
    public static double along(double x, double z, double ax, double az, double bx, double bz) {
        double vx = bx - ax, vz = bz - az;
        return Math.max(0, Math.min(1, ((x - ax) * vx + (z - az) * vz) / (vx * vx + vz * vz)));
    }

    public static double smooth(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }
}
