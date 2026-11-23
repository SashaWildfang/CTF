package com.sasha.ctfbuilder.v2;

import com.sasha.ctfbuilder.v2.W.P;

import java.util.function.IntBinaryOperator;

/**
 * Building kit, fourth edition. Roofs are worked out as a height field over the footprint, so any shape
 * (gable, hip, cross-gable, round) comes out watertight, with its gable ends walled up to the roof line and
 * no gaps; stairs get their corner shapes from {@link W#shapeStairs()}. Walls take a gradient (darker at the
 * base, lighter up top), windows sit in the wall with a sill and lintel, doorways are framed and lit from the
 * side, never from inside the doorway.
 */
public final class Kit {

    public final W w;

    public Kit(W w) {
        this.w = w;
    }

    /** Roof materials. */
    public record Roof(String stairs, String slab, String full) {
        public static Roof of(String base) {
            String full = base.endsWith("_tiles") || base.endsWith("_bricks") || base.endsWith("_planks") || base.endsWith("_shingles")
                    ? base : base;
            String stem = base.endsWith("_planks") ? base.substring(0, base.length() - 7)
                    : base.equals("bricks") ? "brick" : base.equals("purpur_block") ? "purpur"
                    : base.endsWith("_tiles") || base.endsWith("_bricks") ? base.substring(0, base.length() - 1) : base;
            return new Roof(stem + "_stairs", stem + "_slab", full);
        }
    }

    public static final int NONE = Integer.MIN_VALUE;

    // ---------------------------------------------------------------- roofs

    /**
     * A roof from a height field. {@code h(x, z)} is the number of steps up from {@code y} at that cell, or
     * {@link #NONE} outside the roof. Each cell gets a stair facing uphill, the tops get full blocks with a
     * slab cap, and {@code wall(x, z)} cells (the building's own outer wall) are walled up under the roof
     * with {@code gable}. Returns the highest step.
     */
    public int roof(int x0, int z0, int x1, int z1, int y, IntBinaryOperator h, IntBinaryOperator wall, Roof m, P gable, boolean cap) {
        return roof(x0, z0, x1, z1, y, h, wall, m, gable, cap, y);
    }

    /** As above; the walls are only filled from {@code fillFrom} up (the first row above the wall tops). */
    public int roof(int x0, int z0, int x1, int z1, int y, IntBinaryOperator h, IntBinaryOperator wall, Roof m, P gable, boolean cap, int fillFrom) {
        int top = 0;
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                int here = h.applyAsInt(x, z);
                if (here == NONE) continue;
                top = Math.max(top, here);
                String up = null;
                int best = here;
                int[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
                String[] names = {"north", "east", "south", "west"};
                for (int i = 0; i < 4; i++) {
                    int n = h.applyAsInt(x + dirs[i][0], z + dirs[i][1]);
                    if (n != NONE && n > best) {
                        best = n;
                        up = names[i];
                    }
                }
                if (up == null) {
                    // a corner of a hip (only a diagonal neighbour is higher) or the ridge
                    String diag = null;
                    int[][] dg = {{1, 1}, {-1, 1}, {1, -1}, {-1, -1}};
                    for (int[] d : dg) {
                        int n = h.applyAsInt(x + d[0], z + d[1]);
                        if (n != NONE && n > here) diag = d[1] > 0 ? "south" : "north";
                    }
                    if (diag != null) {
                        w.set(x, y + here, z, m.stairs + "[facing=" + diag + ",half=bottom]");
                    } else {
                        w.set(x, y + here, z, m.full);
                        if (cap) w.set(x, y + here + 1, z, m.slab + "[type=bottom]");
                    }
                } else {
                    w.set(x, y + here, z, m.stairs + "[facing=" + up + ",half=bottom]");
                }
                // where the roof steps down by more than one (steep cones), fill under so it stays one surface
                int lowest = here;
                for (int[] d : new int[][] {{0, -1}, {1, 0}, {0, 1}, {-1, 0}}) {
                    int n = h.applyAsInt(x + d[0], z + d[1]);
                    lowest = Math.min(lowest, n == NONE ? here : n);
                }
                for (int yy = y + lowest + 1; yy < y + here; yy++) w.set(x, yy, z, m.full);
                if (wall != null && wall.applyAsInt(x, z) != 0 && gable != null) {
                    for (int yy = Math.max(y, fillFrom); yy < y + here; yy++) w.set(x, yy, z, gable.pick(w.r));
                }
            }
        return top;
    }

    /**
     * A gable roof over a rectangular building (walls on x0..x1, z0..z1), ridge along x or z, with
     * {@code over} blocks of overhang all round. {@code y} is the first layer above the walls. Returns the y of
     * the ridge blocks (absolute, not a height above {@code y}).
     */
    public int gable(int x0, int z0, int x1, int z1, int y, boolean alongX, int over, Roof m, P gable) {
        int ex0 = x0 - over, ex1 = x1 + over, ez0 = z0 - over, ez1 = z1 + over;
        IntBinaryOperator h = (x, z) -> x < ex0 || x > ex1 || z < ez0 || z > ez1 ? NONE
                : alongX ? Math.min(z - ez0, ez1 - z) - over : Math.min(x - ex0, ex1 - x) - over;
        // the overhang is one step below the wall top, so eaves hang over the walls
        IntBinaryOperator lifted = (x, z) -> {
            int v = h.applyAsInt(x, z);
            return v == NONE ? NONE : v + over;
        };
        IntBinaryOperator onWall = (x, z) -> (x == x0 || x == x1) && z >= z0 && z <= z1 || (z == z0 || z == z1) && x >= x0 && x <= x1 ? 1 : 0;
        return y - over + roof(ex0, ez0, ex1, ez1, y - over, lifted, onWall, m, gable, true, y);
    }

    /** A hipped roof (slopes on all four sides). */
    public int hip(int x0, int z0, int x1, int z1, int y, int over, Roof m, P gable) {
        int ex0 = x0 - over, ex1 = x1 + over, ez0 = z0 - over, ez1 = z1 + over;
        IntBinaryOperator h = (x, z) -> x < ex0 || x > ex1 || z < ez0 || z > ez1 ? NONE
                : Math.min(Math.min(z - ez0, ez1 - z), Math.min(x - ex0, ex1 - x));
        IntBinaryOperator onWall = (x, z) -> (x == x0 || x == x1) && z >= z0 && z <= z1 || (z == z0 || z == z1) && x >= x0 && x <= x1 ? 1 : 0;
        return roof(ex0, ez0, ex1, ez1, y - over, h, onWall, m, gable, true, y) - over;
    }

    /**
     * A cone over a round tower of radius r (walls at distance r), {@code steep} steps per block inwards. Returns
     * the y of its apex block (absolute).
     */
    public int cone(int cx, int cz, int y, int r, int over, double steep, Roof m) {
        double rr = r + over + 0.45;
        IntBinaryOperator h = (x, z) -> {
            double d = Math.hypot(x - cx, z - cz);
            return d > rr ? NONE : (int) Math.floor((rr - d) * steep);
        };
        int top = roof(cx - r - over - 1, cz - r - over - 1, cx + r + over + 1, cz + r + over + 1, y - over, h, null, m, null, false);
        return y - over + top;
    }

    /**
     * A dome of radius r sitting on y (a solid shell, smooth: stairs where the curve steps by one, solid blocks
     * where it's steep). {@code open(x, z)} cells are left out (a slit). Returns the apex height.
     */
    public int dome(int cx, int cz, int y, double r, Roof m, java.util.function.BiPredicate<Integer, Integer> open) {
        int R = (int) Math.ceil(r);
        java.util.Map<Long, Integer> hs = new java.util.HashMap<>();
        for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) {
            double d = Math.hypot(dx, dz);
            if (d > r) continue;
            hs.put(Terrain.key(dx, dz), (int) Math.round(Math.sqrt(Math.max(0, r * r - d * d))));
        }
        int apex = 0;
        String[] names = {"north", "east", "south", "west"};
        int[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (var e : hs.entrySet()) {
            int dx = (int) (e.getKey() >> 32), dz = (int) (long) e.getKey();
            if (open != null && open.test(dx, dz)) continue;
            int h = e.getValue();
            apex = Math.max(apex, h);
            int low = h;
            String up = null;
            for (int i = 0; i < 4; i++) {
                Integer n = hs.get(Terrain.key(dx + dirs[i][0], dz + dirs[i][1]));
                int nh = n == null ? -1 : n;
                low = Math.min(low, nh);
                if (n != null && n == h + 1) up = names[i];
            }
            for (int yy = Math.max(0, low + 1); yy < h; yy++) w.set(cx + dx, y + yy, cz + dz, m.full);
            if (low >= h - 1 && up != null && m.stairs != null) w.set(cx + dx, y + h, cz + dz, m.stairs + "[facing=" + up + ",half=bottom]");
            else w.set(cx + dx, y + h, cz + dz, m.full);
        }
        return apex;
    }

    // ---------------------------------------------------------------- walls

    /** Wall material by height: {@code bands[i]} is used from row {@code rows[i]} up. */
    public record Bands(int[] rows, P[] bands) {
        public P at(int dy) {
            P p = bands[0];
            for (int i = 0; i < rows.length; i++) if (dy >= rows[i]) p = bands[i];
            return p;
        }

        public static Bands of(Object... rowsAndPalettes) {
            int n = rowsAndPalettes.length / 2;
            int[] rows = new int[n];
            P[] bands = new P[n];
            for (int i = 0; i < n; i++) {
                rows[i] = (Integer) rowsAndPalettes[i * 2];
                Object p = rowsAndPalettes[i * 2 + 1];
                bands[i] = p instanceof P pal ? pal : P.of((String) p);
            }
            return new Bands(rows, bands);
        }
    }

    /**
     * Four walls on the rectangle, rows y+1..y+h, with posts at the corners and every {@code bay} blocks
     * (post may be null), and a floor at y. Inside is cleared up to y+h.
     */
    public void walls(int x0, int z0, int x1, int z1, int y, int h, Bands wall, String post, int bay, P floor) {
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                if (!edge) {
                    if (floor != null) w.set(x, y, z, floor.pick(w.r));
                    for (int dy = 1; dy <= h; dy++) w.set(x, y + dy, z, "air");
                    continue;
                }
                boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
                boolean isPost = post != null && (corner || bay > 0 && ((z == z0 || z == z1) && (x - x0) % bay == 0
                        || (x == x0 || x == x1) && (z - z0) % bay == 0));
                for (int dy = 0; dy <= h; dy++) {
                    String spec = isPost ? axisY(post) : wall.at(dy).pick(w.r);
                    w.set(x, y + dy, z, spec);
                }
            }
    }

    static String axisY(String log) {
        return log.contains("[") || !(log.endsWith("_log") || log.endsWith("_stem") || log.endsWith("_wood") || log.contains("pillar")
                || log.endsWith("basalt") || log.equals("bone_block") || log.endsWith("hay_block")) ? log : log + "[axis=y]";
    }

    static String axis(String log, char a) {
        return log.contains("[") || !(log.endsWith("_log") || log.endsWith("_stem") || log.endsWith("_wood") || log.contains("pillar")
                || log.endsWith("basalt")) ? log : log + "[axis=" + a + "]";
    }

    /** A horizontal beam along the top of the walls (logs turned along the wall). */
    public void beam(int x0, int z0, int x1, int z1, int y, String log) {
        for (int x = x0; x <= x1; x++) {
            w.set(x, y, z0, axis(log, 'x'));
            w.set(x, y, z1, axis(log, 'x'));
        }
        for (int z = z0; z <= z1; z++) {
            w.set(x0, y, z, axis(log, 'z'));
            w.set(x1, y, z, axis(log, 'z'));
        }
    }

    /** Solid footing from y down into the ground below the footprint (stops at the first solid block). */
    public void footing(int x0, int z0, int x1, int z1, int y, P pal) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++)
                for (int yy = y; yy > y - 30; yy--) {
                    if (yy < y && w.type(x, yy, z).isSolid() && !w.type(x, yy, z).name().endsWith("LEAVES")) break;
                    w.set(x, yy, z, pal.pick(w.r));
                }
    }

    // ---------------------------------------------------------------- openings

    public static int dx(String f) { return f.equals("east") ? 1 : f.equals("west") ? -1 : 0; }
    public static int dz(String f) { return f.equals("south") ? 1 : f.equals("north") ? -1 : 0; }

    public static String opp(String f) {
        return switch (f) { case "north" -> "south"; case "south" -> "north"; case "east" -> "west"; default -> "east"; };
    }

    /** Left of a direction, seen from above (counter-clockwise). */
    public static String left(String f) {
        return switch (f) { case "north" -> "west"; case "west" -> "south"; case "south" -> "east"; default -> "north"; };
    }

    /**
     * A window in a wall facing {@code out}: panes {@code high} tall from y, an upside-down stair sill outside,
     * a slab lintel above, and trapdoor shutters if given.
     */
    public void window(int x, int y, int z, String out, int width, int high, String pane, String sill, String shutter) {
        int ax = dz(out) != 0 ? 1 : 0, az = dx(out) != 0 ? 1 : 0;
        int ox = dx(out), oz = dz(out);
        int half = width / 2;
        for (int o = -half; o <= width - 1 - half; o++)
            for (int dy = 0; dy < high; dy++) w.set(x + ax * o, y + dy, z + az * o, pane);
        if (sill != null) for (int o = -half; o <= width - 1 - half; o++) {
            int sx = x + ax * o + ox, sz = z + az * o + oz;
            if (w.free(sx, y - 1, sz)) w.set(sx, y - 1, sz, sill + "[facing=" + opp(out) + ",half=top]");
        }
        if (shutter != null) for (int side : new int[] {-half - 1, width - half}) {
            int sx = x + ax * side + ox, sz = z + az * side + oz;
            for (int dy = 0; dy < high; dy++) {
                if (!w.free(sx, y + dy, sz)) continue;
                String hinge = side < 0 ? left(out) : opp(left(out));
                w.set(sx, y + dy, sz, shutter + "[facing=" + hinge + ",open=true,half=" + (dy == 0 ? "bottom" : "top") + "]");
            }
        }
    }

    /**
     * A doorway in a wall facing {@code out}: a 1- or 2-wide door, a frame of the trim (stairs as brackets
     * above), a step down outside if the ground is lower, and a lantern hanging from a bracket either side.
     */
    public void doorway(int x, int y, int z, String out, int width, String door, String trimStairs, String bracket, String lantern) {
        int ax = dz(out) != 0 ? 1 : 0, az = dx(out) != 0 ? 1 : 0;
        int ox = dx(out), oz = dz(out);
        for (int o = 0; o < width; o++) {
            int px = x + ax * o, pz = z + az * o;
            String hinge = width == 2 ? (o == 0 ? "left" : "right") : "left";
            if (door != null) {
                w.set(px, y + 1, pz, door + "[facing=" + opp(out) + ",half=lower,hinge=" + hinge + ",open=false]");
                w.set(px, y + 2, pz, door + "[facing=" + opp(out) + ",half=upper,hinge=" + hinge + ",open=false]");
            } else {
                w.set(px, y + 1, pz, "air");
                w.set(px, y + 2, pz, "air");
            }
            // the threshold and the step outside
            w.set(px + ox, y, pz + oz, w.type(px + ox, y, pz + oz).isSolid() ? w.type(px + ox, y, pz + oz).name().toLowerCase() : "stone_bricks");
            for (int dy = 1; dy <= 3; dy++) if (!w.free(px + ox, y + dy, pz + oz)) w.set(px + ox, y + dy, pz + oz, "air");
        }
        if (trimStairs != null) {
            // a frame: brackets either side above the door, a lintel across
            int lx = x - ax, lz = z - az, rx = x + ax * width, rz = z + az * width;
            String toward = ax != 0 ? "east" : "south";
            w.set(lx + ox, y + 3, lz + oz, trimStairs + "[facing=" + opp(toward) + ",half=top]");
            w.set(rx + ox, y + 3, rz + oz, trimStairs + "[facing=" + toward + ",half=top]");
            for (int o = 0; o < width; o++) w.set(x + ax * o + ox, y + 3, z + az * o + oz, trimStairs.replace("_stairs", "_slab") + "[type=top]");
        }
        if (lantern != null && bracket != null) {
            for (int side : new int[] {-2, width + 1}) {
                int px = x + ax * side + ox, pz = z + az * side + oz;
                if (!w.free(px, y + 3, pz) || !w.free(px, y + 2, pz)) continue;
                w.set(px, y + 3, pz, bracket);
                w.set(px, y + 2, pz, lantern + "[hanging=true]");
            }
        }
    }

    // ---------------------------------------------------------------- furniture (always on the floor, against something)

    /** A bed with both halves, head against a wall: only if both cells are free. */
    public boolean bed(int x, int y, int z, String headToward, String colour) {
        int hx = x + dx(headToward), hz = z + dz(headToward);
        if (!w.free(x, y, z) || !w.free(hx, y, hz) || !w.type(x, y - 1, z).isSolid() || !w.type(hx, y - 1, hz).isSolid()) return false;
        w.set(x, y, z, colour + "_bed[facing=" + headToward + ",part=foot]");
        w.set(hx, y, hz, colour + "_bed[facing=" + headToward + ",part=head]");
        return true;
    }

    /** A table: a fence post with a pressure plate, or a slab on the floor for low tables. */
    public void table(int x, int y, int z, String fence, String plate) {
        w.set(x, y, z, fence);
        w.set(x, y + 1, z, plate);
    }

    /** A wall-hung lantern: an arm of fence out of the wall with the lantern under it. */
    public void wallLantern(int x, int y, int z, String fence, String lantern) {
        w.set(x, y, z, fence);
        w.set(x, y - 1, z, lantern + "[hanging=true]");
    }

    /** A pendant light: chain up to the ceiling at most 5 above. */
    public void pendant(int x, int y, int z, String lantern) {
        w.hangLight(x, y, z, lantern);
    }

    // ---------------------------------------------------------------- crossings

    /**
     * A bridge over the void between two points on land. Its ends are found where the straight line leaves one
     * island and reaches the other; the deck meets the ground flush at both ends (the ground there is levelled
     * to it), climbs in half-block steps of slabs, and has a rail with posts, lights and braces underneath.
     */
    public void bridge(int x1, int z1, int x2, int z2, int width, String deck, String deckSlab, String beam, String rail, String post, String light) {
        double len = Math.hypot(x2 - x1, z2 - z1);
        double ux = (x2 - x1) / len, uz = (z2 - z1) / len, nx = -uz, nz = ux;
        // where the land ends either side (the last ground found from each end)
        double a = 0, b = len;
        int gy1 = w.ground(x1, z1), gy2 = w.ground(x2, z2);
        for (double s = 0; s <= len; s += 0.5) {
            int gx = (int) Math.round(x1 + ux * s), gz = (int) Math.round(z1 + uz * s);
            int g = w.ground(gx, gz);
            if (g >= gy1 - 3 && g > w.w.getMinHeight()) a = s; else break;
        }
        for (double s = len; s >= 0; s -= 0.5) {
            int gx = (int) Math.round(x1 + ux * s), gz = (int) Math.round(z1 + uz * s);
            int g = w.ground(gx, gz);
            if (g >= gy2 - 3 && g > w.w.getMinHeight()) b = s; else break;
        }
        double start = Math.max(0, a - 3), end = Math.min(len, b + 3); // run 3 blocks onto the land
        int ya = w.ground((int) Math.round(x1 + ux * a), (int) Math.round(z1 + uz * a));
        int yb = w.ground((int) Math.round(x1 + ux * b), (int) Math.round(z1 + uz * b));
        double span = Math.max(1, end - start);
        java.util.Map<Long, Double> surface = new java.util.HashMap<>();
        double hw = width / 2.0;
        for (double s = start; s <= end; s += 0.25) {
            double k = (s - start) / span;
            double arch = Math.sin(Math.max(0, Math.min(1, (s - a) / Math.max(1, b - a))) * Math.PI) * Math.min(1.5, (b - a) / 24.0);
            double top = ya + 1 + (yb - ya) * k + arch;
            double snapped = Math.round(top * 2) / 2.0;
            for (double o = -hw + 0.5; o <= hw - 0.5 + 1e-6; o += 0.5) {
                int bx = (int) Math.floor(x1 + ux * s + nx * o + 0.5), bz = (int) Math.floor(z1 + uz * s + nz * o + 0.5);
                surface.putIfAbsent(Terrain.key(bx, bz), snapped);
            }
        }
        java.util.Set<Long> railCells = new java.util.HashSet<>();
        for (long key : surface.keySet()) {
            int bx = (int) (key >> 32), bz = (int) key;
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                long n = Terrain.key(bx + d[0], bz + d[1]);
                if (!surface.containsKey(n)) railCells.add(n);
            }
        }
        for (var e : surface.entrySet()) {
            int bx = (int) (e.getKey() >> 32), bz = (int) (long) e.getKey();
            if (!w.canon(bx, bz)) continue;
            double s = e.getValue();
            int base = (int) Math.floor(s) - 1;
            boolean half = s - Math.floor(s) > 0.25;
            boolean overLand = w.ground(bx, bz) >= base - 2;
            w.set(bx, base, bz, deck);
            if (half) w.set(bx, base + 1, bz, deckSlab + "[type=bottom]");
            for (int dy = half ? 2 : 1; dy <= 4; dy++) if (!w.free(bx, base + dy, bz) || !w.air(bx, base + dy, bz)) w.set(bx, base + dy, bz, "air");
            if (overLand) {
                // fill under the deck where it runs onto the land, so it sits on the ground
                for (int yy = base - 1; yy > base - 6 && !w.type(bx, yy, bz).isSolid(); yy--) w.set(bx, yy, bz, "dirt");
            } else {
                w.set(bx, base - 1, bz, beam);
            }
        }
        java.util.Set<String> posts = new java.util.HashSet<>();
        for (long key : railCells) {
            int bx = (int) (key >> 32), bz = (int) key;
            if (!w.canon(bx, bz)) continue;
            Double s = null;
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                Double n = surface.get(Terrain.key(bx + d[0], bz + d[1]));
                if (n != null) s = s == null ? n : Math.max(s, n);
            }
            if (s == null) continue;
            int g = w.ground(bx, bz);
            int top = (int) Math.ceil(s);
            if (g >= top - 1) continue; // on land the rail stops: the bridge opens onto the ground
            int base = top - 1;
            w.set(bx, base, bz, beam);
            double along = (bx - x1) * ux + (bz - z1) * uz, side = (bx - x1) * nx + (bz - z1) * nz;
            boolean isPost = Math.floorMod((int) Math.floor(along - start), 6) == 0 && posts.add((side > 0 ? "+" : "-") + (int) Math.floor((along - start) / 6));
            w.set(bx, base + 1, bz, isPost ? axisY(post) : rail);
            if (isPost && light != null) w.set(bx, base + 2, bz, light);
            if (isPost && w.air(bx, base - 1, bz)) w.set(bx, base - 1, bz, axisY(post));
        }
    }
}
