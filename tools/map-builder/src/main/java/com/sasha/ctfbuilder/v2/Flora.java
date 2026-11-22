package com.sasha.ctfbuilder.v2;

import com.sasha.ctfbuilder.v2.Theme.Tree;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/** Trees, bushes, boulders, fallen logs and ground plants, shaped by hand-written growth rules. */
public final class Flora {

    final W w;
    final Theme t;
    final Terrain land;
    final List<int[]> placed = new ArrayList<>();

    public Flora(W w, Theme t, Terrain land) {
        this.w = w;
        this.t = t;
        this.land = land;
    }

    // ---------------------------------------------------------------- scattering

    public void decorate() {
        for (Terrain.Island island : land.islands) {
            double area = Math.PI * island.rx * island.rz;
            int trees = (int) Math.round(area * t.treeDensity * island.trees);
            for (int i = 0; i < trees * 6 && trees > 0; i++) {
                int[] p = spot(island, 0.18);
                if (p == null || crowded(p[0], p[1], 5)) continue;
                tree(pickTree(), p[0], p[2] + 1, p[1]);
                placed.add(new int[] {p[0], p[1]});
                if (--trees <= 0) break;
            }
            int boulders = (int) Math.round(area * 0.002) + w.r.nextInt(2);
            for (int i = 0; i < boulders; i++) {
                int[] p = spot(island, 0.1);
                if (p != null && !crowded(p[0], p[1], 3)) boulder(p[0], p[2], p[1], 1.3 + w.r.nextDouble() * 1.6);
            }
            int bushes = (int) Math.round(area * 0.006);
            for (int i = 0; i < bushes; i++) {
                int[] p = spot(island, 0.08);
                if (p != null && !crowded(p[0], p[1], 2)) bush(p[0], p[2] + 1, p[1], 1 + w.r.nextDouble() * 0.9);
            }
            if (w.r.nextInt(3) == 0) {
                int[] p = spot(island, 0.25);
                if (p != null && !crowded(p[0], p[1], 4)) fallenLog(p[0], p[2] + 1, p[1]);
            }
        }
        plants();
    }

    Tree pickTree() {
        int total = 0;
        for (int weight : t.treeWeights) total += weight;
        int roll = w.r.nextInt(total);
        for (int i = 0; i < t.trees.length; i++) {
            roll -= t.treeWeights[i];
            if (roll < 0) return t.trees[i];
        }
        return t.trees[0];
    }

    /** A random natural spot on an island at least {@code margin} in from the edge: {x, z, groundY}. */
    int[] spot(Terrain.Island island, double margin) {
        for (int tries = 0; tries < 16; tries++) {
            int[] c = land.turn(island.cx, island.cz, w.r.nextInt(w.copies));
            double r = Math.max(island.rx, island.rz);
            int x = c[0] + (int) Math.round((w.r.nextDouble() * 2 - 1) * r);
            int z = c[1] + (int) Math.round((w.r.nextDouble() * 2 - 1) * r);
            if (!w.canon(x, z) || land.insideAny(x, z) < margin || land.kept(x, z)) continue;
            int y = w.ground(x, z);
            if (y < 0 || !land.natural(w.type(x, y, z)) || !w.free(x, y + 1, z)) continue;
            return new int[] {x, z, y};
        }
        return null;
    }

    boolean crowded(int x, int z, double distance) {
        for (int[] p : placed) if (Math.hypot(p[0] - x, p[1] - z) < distance) return true;
        return false;
    }

    void plants() {
        java.util.Set<Long> done = new java.util.HashSet<>();
        for (Terrain.Island island : land.islands) for (int k = 0; k < w.copies; k++) {
            int r = (int) Math.ceil(Math.max(island.rx, island.rz) * 1.35);
            int[] c = land.turn(island.cx, island.cz, k);
            for (int x = c[0] - r; x <= c[0] + r; x++)
                for (int z = c[1] - r; z <= c[1] + r; z++) {
                    if (!w.canon(x, z) || !done.add(((long) x << 32) ^ (z & 0xffffffffL))) continue;
                    double cluster = (w.fbm(x + 41, z - 99, 9, 2) + 1) / 2;
                    if (w.r.nextDouble() > t.floraDensity * (0.3 + cluster * 1.6)) continue;
                    if (land.kept(x, z)) continue;
                    int y = w.ground(x, z);
                    if (y < 0 || !land.natural(w.type(x, y, z)) || !w.air(x, y + 1, z)) continue;
                    plant(x, y + 1, z, t.flora.pick(w.r));
                }
        }
    }

    void plant(int x, int y, int z, String spec) {
        if (spec.contains("half=lower")) {
            w.set(x, y, z, spec);
            w.set(x, y + 1, z, spec.replace("half=lower", "half=upper"));
        } else if (spec.equals("cactus")) {
            int h = 1 + w.r.nextInt(3);
            for (int i = 0; i < h; i++) w.set(x, y + i, z, "cactus");
            if (w.r.nextBoolean()) w.set(x, y + h, z, "cactus_flower");
        } else {
            w.set(x, y, z, spec);
        }
    }

    // ---------------------------------------------------------------- trees

    public void tree(Tree kind, int x, int y, int z) {
        switch (kind) {
            case OAK -> broadleaf(x, y, z, "oak_log", "oak_leaves", 6 + w.r.nextInt(4), w.r.nextInt(3) == 0);
            case BIRCH -> birch(x, y, z);
            case SPRUCE -> spruce(x, y, z, 11 + w.r.nextInt(9));
            case CHERRY -> cherry(x, y, z);
            case PALM -> palm(x, y, z);
            case JUNGLE -> jungle(x, y, z);
            case DEAD -> dead(x, y, z);
            case CRIMSON -> fungus(x, y, z, "crimson_stem", "nether_wart_block", "weeping_vines");
            case WARPED -> fungus(x, y, z, "warped_stem", "warped_wart_block", "twisting_vines");
            case RED_MUSHROOM -> mushroom(x, y, z, true);
            case BROWN_MUSHROOM -> mushroom(x, y, z, false);
            case PALE -> pale(x, y, z);
            case CHORUS -> chorus(x, y, z);
            case AZALEA -> broadleaf(x, y, z, "oak_log", w.r.nextBoolean() ? "azalea_leaves" : "flowering_azalea_leaves", 4 + w.r.nextInt(2), false);
            case AUTUMN -> broadleaf(x, y, z, "dark_oak_log", "acacia_leaves", 7, true);
        }
    }

    String axis(int dx, int dy, int dz) {
        int ax = Math.abs(dx), ay = Math.abs(dy), az = Math.abs(dz);
        return ay >= ax && ay >= az ? "y" : ax >= az ? "x" : "z";
    }

    /** A line of logs from a to b, each turned along the line. */
    void limb(String log, double x1, double y1, double z1, double x2, double y2, double z2) {
        int steps = (int) Math.ceil(Math.max(Math.abs(x2 - x1), Math.max(Math.abs(y2 - y1), Math.abs(z2 - z1))) * 1.2) + 1;
        String ax = axis((int) Math.round(x2 - x1), (int) Math.round(y2 - y1), (int) Math.round(z2 - z1));
        for (int i = 0; i <= steps; i++) {
            double k = i / (double) steps;
            int x = (int) Math.round(x1 + (x2 - x1) * k), y = (int) Math.round(y1 + (y2 - y1) * k), z = (int) Math.round(z1 + (z2 - z1) * k);
            w.set(x, y, z, log + "[axis=" + ax + "]");
        }
    }

    /** A ragged blob of leaves. */
    void canopy(String leaves, double cx, double cy, double cz, double rx, double ry, double rz) {
        String spec = leaves.contains("[") || !leaves.endsWith("leaves") ? leaves : leaves + "[persistent=true]";
        for (int x = (int) Math.floor(cx - rx - 1); x <= cx + rx + 1; x++)
            for (int y = (int) Math.floor(cy - ry - 1); y <= cy + ry + 1; y++)
                for (int z = (int) Math.floor(cz - rz - 1); z <= cz + rz + 1; z++) {
                    double dx = (x - cx) / rx, dy = (y - cy) / ry, dz = (z - cz) / rz;
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz) + w.n3(x, y, z, 2.2) * 0.35;
                    if (d > 1) continue;
                    if (d > 0.8 && w.r.nextDouble() < 0.35) continue;
                    if (w.free(x, y, z)) w.set(x, y, z, spec);
                }
    }

    void roots(String log, int x, int y, int z) {
        for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            if (w.r.nextInt(3) == 0) continue;
            w.set(x + d[0], y, z + d[1], log + "[axis=" + (d[0] != 0 ? "x" : "z") + "]");
            if (w.r.nextBoolean()) w.set(x + d[0], y - 1, z + d[1], log + "[axis=y]");
        }
    }

    void broadleaf(int x, int y, int z, String log, String leaves, int height, boolean thick) {
        String llog = log.replace("[axis=y]", "");
        for (int i = 0; i < height; i++) {
            w.set(x, y + i, z, llog + "[axis=y]");
            if (thick) {
                w.set(x + 1, y + i, z, llog + "[axis=y]");
                w.set(x, y + i, z + 1, llog + "[axis=y]");
                w.set(x + 1, y + i, z + 1, llog + "[axis=y]");
            }
        }
        roots(llog, x, y, z);
        int branches = 3 + w.r.nextInt(3);
        double radius = thick ? 3.6 : 2.9;
        canopy(leaves, x + 0.5, y + height, z + 0.5, radius + 0.6, radius * 0.75, radius + 0.6);
        for (int b = 0; b < branches; b++) {
            double a = w.r.nextDouble() * Math.PI * 2;
            int by = y + height - 2 - w.r.nextInt(Math.max(1, height / 2));
            double len = 2.5 + w.r.nextDouble() * (thick ? 4 : 2.5);
            double ex = x + Math.cos(a) * len, ez = z + Math.sin(a) * len, ey = by + 1.5 + w.r.nextDouble() * 2;
            limb(llog, x, by, z, ex, ey, ez);
            canopy(leaves, ex, ey + 0.8, ez, radius * 0.85, radius * 0.6, radius * 0.85);
        }
    }

    void birch(int x, int y, int z) {
        int h = 8 + w.r.nextInt(4);
        for (int i = 0; i < h; i++) w.set(x, y + i, z, "birch_log[axis=y]");
        canopy("birch_leaves", x, y + h - 1, z, 2.4, 3.2, 2.4);
        double a = w.r.nextDouble() * Math.PI * 2;
        limb("birch_log", x, y + h - 4, z, x + Math.cos(a) * 2, y + h - 2, z + Math.sin(a) * 2);
        canopy("birch_leaves", x + Math.cos(a) * 2, y + h - 1.5, z + Math.sin(a) * 2, 1.8, 1.6, 1.8);
    }

    void spruce(int x, int y, int z, int h) {
        boolean thick = h > 15;
        for (int i = 0; i < h; i++) {
            w.set(x, y + i, z, "spruce_log[axis=y]");
            if (thick && i < h - 4) {
                w.set(x + 1, y + i, z, "spruce_log[axis=y]");
                w.set(x, y + i, z + 1, "spruce_log[axis=y]");
                w.set(x + 1, y + i, z + 1, "spruce_log[axis=y]");
            }
        }
        if (thick) roots("spruce_log", x, y, z);
        double cx = x + (thick ? 0.5 : 0), cz = z + (thick ? 0.5 : 0);
        int start = 3 + w.r.nextInt(2);
        for (int i = start; i <= h; i++) {
            double k = (i - start) / (double) (h - start);
            double radius = (1 - k) * (h / 3.4) + 0.6;
            if ((i - start) % 2 == 1) radius *= 0.62; // alternating wide and narrow rings give the layered look
            for (int dx = (int) -Math.ceil(radius) - 1; dx <= radius + 1; dx++)
                for (int dz = (int) -Math.ceil(radius) - 1; dz <= radius + 1; dz++) {
                    double d = Math.hypot(x + dx - cx, z + dz - cz) + w.n3(x + dx, y + i, z + dz, 1.7) * 0.6;
                    if (d > radius) continue;
                    if (w.free(x + dx, y + i, z + dz)) w.set(x + dx, y + i, z + dz, "spruce_leaves[persistent=true]");
                    // drooping tips on the wide rings
                    if (d > radius - 1 && (i - start) % 2 == 0 && w.r.nextInt(3) == 0 && w.free(x + dx, y + i - 1, z + dz)) {
                        w.set(x + dx, y + i - 1, z + dz, "spruce_leaves[persistent=true]");
                    }
                }
        }
        w.set(x, y + h + 1, z, "spruce_leaves[persistent=true]");
        w.set(x, y + h + 2, z, "spruce_leaves[persistent=true]");
    }

    void cherry(int x, int y, int z) {
        int h = 4 + w.r.nextInt(2);
        for (int i = 0; i < h; i++) w.set(x, y + i, z, "cherry_log[axis=y]");
        int arms = 2 + w.r.nextInt(2);
        double a0 = w.r.nextDouble() * Math.PI * 2;
        for (int b = 0; b < arms; b++) {
            double a = a0 + b * Math.PI * 2 / arms + w.r.nextDouble() * 0.6;
            double len = 3 + w.r.nextDouble() * 2.5;
            double ex = x + Math.cos(a) * len, ez = z + Math.sin(a) * len, ey = y + h + 2 + w.r.nextDouble() * 2;
            limb("cherry_log", x, y + h - 1, z, ex, ey, ez);
            canopy("cherry_leaves", ex, ey + 1, ez, 3.4, 1.7, 3.4);
            for (int i = 0; i < 6; i++) { // petals underneath
                int px = (int) Math.round(ex + w.r.nextGaussian() * 2.5), pz = (int) Math.round(ez + w.r.nextGaussian() * 2.5);
                int gy = w.ground(px, pz);
                if (gy > 0 && w.air(px, gy + 1, pz) && land.natural(w.type(px, gy, pz))) w.set(px, gy + 1, pz, "pink_petals[flower_amount=" + (1 + w.r.nextInt(4)) + "]");
            }
        }
        canopy("cherry_leaves", x, y + h + 2, z, 2.6, 1.6, 2.6);
    }

    void palm(int x, int y, int z) {
        int h = 6 + w.r.nextInt(4);
        double a = w.r.nextDouble() * Math.PI * 2, lean = 0.25 + w.r.nextDouble() * 0.2;
        double px = x, pz = z;
        for (int i = 0; i < h; i++) {
            px += Math.cos(a) * lean * (i / (double) h) * 1.4;
            pz += Math.sin(a) * lean * (i / (double) h) * 1.4;
            w.set((int) Math.round(px), y + i, (int) Math.round(pz), "jungle_log[axis=y]");
        }
        int tx = (int) Math.round(px), tz = (int) Math.round(pz), ty = y + h;
        w.set(tx, ty, tz, "jungle_leaves[persistent=true]");
        int fronds = 6 + w.r.nextInt(3);
        for (int f = 0; f < fronds; f++) {
            double fa = f * Math.PI * 2 / fronds + w.r.nextDouble() * 0.3;
            for (int i = 1; i <= 5; i++) {
                int fx = tx + (int) Math.round(Math.cos(fa) * i), fz = tz + (int) Math.round(Math.sin(fa) * i);
                int fy = ty + (i <= 2 ? 1 : i == 3 ? 0 : -1 - (i - 4));
                w.soft(fx, fy, fz, "jungle_leaves[persistent=true]");
            }
        }
        if (w.r.nextBoolean()) w.soft(tx + 1, ty - 1, tz, "cocoa[age=2,facing=west]");
    }

    void jungle(int x, int y, int z) {
        int h = 12 + w.r.nextInt(7);
        broadleaf(x, y, z, "jungle_log", "jungle_leaves", h, true);
        for (int i = 0; i < 24; i++) { // vines off the canopy edge
            int vx = x + w.r.nextInt(11) - 5, vz = z + w.r.nextInt(11) - 5;
            for (int vy = y + h + 3; vy > y + 3; vy--) {
                if (w.type(vx, vy, vz).name().endsWith("LEAVES") && w.air(vx, vy - 1, vz)) {
                    int len = 1 + w.r.nextInt(4);
                    for (int k = 1; k <= len; k++) if (w.air(vx, vy - k, vz)) w.set(vx, vy - k, vz, k == len ? "cave_vines[berries=false]" : "cave_vines_plant[berries=false]");
                    break;
                }
            }
        }
    }

    void dead(int x, int y, int z) {
        String log = t.id.equals("emberforge") ? "blackstone_wall" : w.r.nextBoolean() ? "stripped_oak_log" : "stripped_spruce_log";
        int h = 4 + w.r.nextInt(4);
        boolean wall = log.endsWith("wall");
        for (int i = 0; i < h; i++) w.set(x, y + i, z, wall ? log : log + "[axis=y]");
        if (wall) return;
        for (int b = 0; b < 2 + w.r.nextInt(2); b++) {
            double a = w.r.nextDouble() * Math.PI * 2;
            limb(log, x, y + h - 1 - w.r.nextInt(3), z, x + Math.cos(a) * 2.5, y + h + w.r.nextInt(2), z + Math.sin(a) * 2.5);
        }
    }

    void fungus(int x, int y, int z, String stem, String wart, String vines) {
        int h = 7 + w.r.nextInt(6);
        for (int i = 0; i < h; i++) w.set(x, y + i, z, stem + "[axis=y]");
        double r = 3 + w.r.nextDouble() * 1.5;
        for (int dx = -5; dx <= 5; dx++)
            for (int dz = -5; dz <= 5; dz++)
                for (int dy = -3; dy <= 2; dy++) {
                    double d = Math.sqrt(dx * dx + dz * dz + (dy + 1) * (dy + 1) * 2.2);
                    if (d > r || d < r - 1.6 && dy < 1) continue;
                    int bx = x + dx, by = y + h + dy, bz = z + dz;
                    if (!w.free(bx, by, bz)) continue;
                    w.set(bx, by, bz, w.r.nextInt(14) == 0 ? "shroomlight" : wart);
                    if (dy <= -2 && w.r.nextInt(5) == 0) {
                        int len = 1 + w.r.nextInt(4);
                        String plant = vines.startsWith("weeping") ? "weeping_vines_plant" : "twisting_vines_plant";
                        if (vines.startsWith("weeping")) for (int k = 1; k <= len; k++) w.soft(bx, by - k, bz, k == len ? vines : plant);
                    }
                }
    }

    void mushroom(int x, int y, int z, boolean red) {
        int h = 6 + w.r.nextInt(5);
        for (int i = 0; i < h; i++) w.set(x, y + i, z, "mushroom_stem");
        String cap = red ? "red_mushroom_block" : "brown_mushroom_block";
        double r = red ? 3.2 + w.r.nextDouble() : 4.2 + w.r.nextDouble() * 1.3;
        for (int dx = -6; dx <= 6; dx++)
            for (int dz = -6; dz <= 6; dz++) {
                double d = Math.hypot(dx, dz) + w.n(x + dx, z + dz, 2) * 0.4;
                if (d > r) continue;
                if (red) {
                    int dome = (int) Math.round(Math.sqrt(Math.max(0, r * r - d * d)) * 0.8);
                    w.set(x + dx, y + h + dome, z + dz, cap);
                    if (d > r - 1.2) for (int k = 1; k <= 2; k++) w.soft(x + dx, y + h + dome - k, z + dz, cap);
                } else {
                    int drop = d > r - 1 ? -1 : 0;
                    w.set(x + dx, y + h + drop, z + dz, cap);
                }
            }
        if (red) for (int i = 0; i < 4; i++) w.soft(x + w.r.nextInt(5) - 2, y + h - 1, z + w.r.nextInt(5) - 2, "shroomlight");
    }

    void pale(int x, int y, int z) {
        int h = 7 + w.r.nextInt(4);
        broadleaf(x, y, z, "pale_oak_log", "pale_oak_leaves", h, true);
        for (int i = 0; i < 18; i++) {
            int vx = x + w.r.nextInt(9) - 4, vz = z + w.r.nextInt(9) - 4;
            for (int vy = y + h + 3; vy > y + 2; vy--) {
                if (w.type(vx, vy, vz).name().endsWith("LEAVES") && w.air(vx, vy - 1, vz)) {
                    int len = 1 + w.r.nextInt(3);
                    for (int k = 1; k <= len; k++) if (w.air(vx, vy - k, vz)) w.set(vx, vy - k, vz, "pale_hanging_moss[tip=" + (k == len) + "]");
                    break;
                }
            }
        }
        if (w.r.nextInt(3) == 0) w.set(x, y + 2, z - 1, "resin_clump[north=false,south=true]");
    }

    void chorus(int x, int y, int z) {
        growChorus(x, y, z, 0, 5 + w.r.nextInt(4));
    }

    private void growChorus(int x, int y, int z, int depth, int height) {
        for (int i = 0; i < height; i++) w.set(x, y + i, z, "chorus_plant[up=true,down=true]");
        int ty = y + height;
        if (depth >= 2) {
            w.set(x, ty, z, "chorus_flower[age=5]");
            return;
        }
        int arms = 1 + w.r.nextInt(3);
        for (int a = 0; a < arms; a++) {
            int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            int[] d = dirs[w.r.nextInt(4)];
            int len = 1 + w.r.nextInt(2);
            for (int k = 1; k <= len; k++) w.set(x + d[0] * k, ty - 1, z + d[1] * k, "chorus_plant[east=true,west=true,north=true,south=true]");
            growChorus(x + d[0] * len, ty, z + d[1] * len, depth + 1, 2 + w.r.nextInt(3));
        }
        w.set(x, ty, z, "chorus_flower[age=5]");
    }

    // ---------------------------------------------------------------- small things

    public void bush(int x, int y, int z, double r) {
        canopy(t.bushes.pick(w.r), x, y + r * 0.3, z, r + 0.3, r * 0.7, r + 0.3);
    }

    public void boulder(int x, int y, int z, double r) {
        int ir = (int) Math.ceil(r) + 1;
        for (int dx = -ir; dx <= ir; dx++)
            for (int dy = -ir; dy <= ir; dy++)
                for (int dz = -ir; dz <= ir; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy * 1.5 + dz * dz) + w.n3(x + dx, y + dy, z + dz, 2) * 0.7;
                    if (d > r) continue;
                    w.set(x + dx, y + dy, z + dz, t.boulder.pick(w.r));
                }
        if (t.surface.specs.contains("grass_block") || t.id.equals("verdant") || t.id.equals("sakura")) {
            for (int dx = -ir; dx <= ir; dx++)
                for (int dz = -ir; dz <= ir; dz++) {
                    int ty = w.top(x + dx, z + dz);
                    if (ty >= y && w.air(x + dx, ty + 1, z + dz) && w.r.nextInt(2) == 0 && !w.type(x + dx, ty, z + dz).name().contains("LEAVES")) {
                        w.set(x + dx, ty + 1, z + dz, "moss_carpet");
                    }
                }
        }
    }

    public void fallenLog(int x, int y, int z) {
        boolean alongX = w.r.nextBoolean();
        int len = 3 + w.r.nextInt(4);
        String log = t.log.contains("pillar") ? "oak_log" : t.log.replace("stripped_", "");
        for (int i = 0; i < len; i++) {
            int bx = alongX ? x + i : x, bz = alongX ? z : z + i;
            int gy = w.ground(bx, bz);
            if (Math.abs(gy + 1 - y) > 1) break;
            w.set(bx, y, bz, log + "[axis=" + (alongX ? "x" : "z") + "]");
            if (w.r.nextInt(3) == 0) w.soft(bx, y + 1, bz, w.r.nextBoolean() ? "moss_carpet" : "brown_mushroom");
        }
    }

    boolean natural(Material m) {
        return land.natural(m);
    }
}
