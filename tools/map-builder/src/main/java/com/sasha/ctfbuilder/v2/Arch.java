package com.sasha.ctfbuilder.v2;

import com.sasha.ctfbuilder.v2.W.P;

import java.util.ArrayList;
import java.util.List;

/**
 * Architecture kit: walls with posts and beams, windows that sit in their walls, doors, roofs of several
 * kinds, towers, stairs, bridges, and furnished rooms. Every map's base is composed from these, in its
 * own style, so no two look alike.
 */
public final class Arch {

    public final W w;
    public final Theme t;
    public final Terrain land;
    /** Floor areas kept free of props (doorways and the walk in front of them): {x0, z0, x1, z1}. */
    private final List<int[]> keepClear = new ArrayList<>();

    public Arch(W w, Theme t, Terrain land) {
        this.w = w;
        this.t = t;
        this.land = land;
    }

    // ---------------------------------------------------------------- directions

    public static int dx(String f) { return f.equals("east") ? 1 : f.equals("west") ? -1 : 0; }
    public static int dz(String f) { return f.equals("south") ? 1 : f.equals("north") ? -1 : 0; }

    public static String opposite(String f) {
        return switch (f) {
            case "north" -> "south";
            case "south" -> "north";
            case "east" -> "west";
            default -> "east";
        };
    }

    /** 90° clockwise (seen from above). */
    public static String cw(String f) {
        return switch (f) {
            case "north" -> "east";
            case "east" -> "south";
            case "south" -> "west";
            default -> "north";
        };
    }

    public static String st(String stairs, String facing, boolean top) {
        return stairs + "[facing=" + facing + ",half=" + (top ? "top" : "bottom") + "]";
    }

    public static String slab(String slab, boolean top) {
        return slab + "[type=" + (top ? "top" : "bottom") + "]";
    }

    public void reserve(int x0, int z0, int x1, int z1) {
        keepClear.add(new int[] {Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1)});
    }

    public boolean reserved(int x, int z) {
        for (int[] r : keepClear) if (x >= r[0] && x <= r[2] && z >= r[1] && z <= r[3]) return true;
        return false;
    }

    // ---------------------------------------------------------------- filling

    public void fill(int x0, int y0, int z0, int x1, int y1, int z1, String spec) {
        w.fill(x0, y0, z0, x1, y1, z1, spec);
    }

    public void fill(int x0, int y0, int z0, int x1, int y1, int z1, P pal) {
        w.fill(x0, y0, z0, x1, y1, z1, pal);
    }

    public void air(int x0, int y0, int z0, int x1, int y1, int z1) {
        w.fill(x0, y0, z0, x1, y1, z1, "air");
    }

    /** Solid ground under a footprint, down to the island (so buildings never float). */
    public void foundation(int x0, int z0, int x1, int z1, int y, P pal) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                for (int yy = y; yy > y - 14; yy--) {
                    if (yy < y && !w.air(x, yy, z) && w.type(x, yy, z).isSolid()) break;
                    w.set(x, yy, z, pal.pick(w.r));
                }
            }
    }

    // ---------------------------------------------------------------- walls

    /**
     * One storey: a plinth course, walls, posts at the corners and every {@code bay} blocks, a beam course
     * along the top, and the floor. Inside is cleared. y is the floor; walls run y+1..y+h.
     */
    public void storey(int x0, int z0, int x1, int z1, int y, int h, P wall, String post, String beam, P floor, int bay) {
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                if (!edge) {
                    w.set(x, y, z, floor.pick(w.r));
                    for (int dy = 1; dy <= h; dy++) w.set(x, y + dy, z, "air");
                    continue;
                }
                boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
                boolean isPost = corner || bay > 0 && ((z == z0 || z == z1) && (x - x0) % bay == 0 || (x == x0 || x == x1) && (z - z0) % bay == 0);
                w.set(x, y, z, isPost && post != null ? post : t.trim.pick(w.r));
                for (int dy = 1; dy <= h; dy++) {
                    String spec;
                    if (isPost && post != null) spec = post.contains("[") || !post.endsWith("_log") && !post.endsWith("_stem") && !post.contains("pillar") ? post : post + "[axis=y]";
                    else if (dy == h && beam != null) spec = beam.endsWith("_log") || beam.endsWith("_wood") && !beam.contains("stripped") ? beam + "[axis=" + (z == z0 || z == z1 ? "x" : "z") + "]" : beam;
                    else spec = wall.pick(w.r);
                    w.set(x, y + dy, z, spec);
                }
            }
    }

    /**
     * Windows along all four walls of a storey: {@code high} blocks tall, centred in each bay, glass joined
     * into the wall, with a sill outside and optional shutters.
     */
    public void windows(int x0, int z0, int x1, int z1, int y, int sill, int high, int bay, String pane, boolean shutters) {
        for (int x = x0 + bay / 2; x < x1; x += bay) {
            if ((x - x0) % bay == 0) continue;
            window(x, y + sill, z0, "north", high, pane, shutters);
            window(x, y + sill, z1, "south", high, pane, shutters);
        }
        for (int z = z0 + bay / 2; z < z1; z += bay) {
            if ((z - z0) % bay == 0) continue;
            window(x0, y + sill, z, "west", high, pane, shutters);
            window(x1, y + sill, z, "east", high, pane, shutters);
        }
    }

    public void window(int x, int y, int z, String out, int high, String pane, boolean shutters) {
        if (reserved(x, z)) return;
        for (int dy = 0; dy < high; dy++) w.set(x, y + dy, z, pane);
        int ox = dx(out), oz = dz(out);
        int sx = oz != 0 ? 1 : 0, sz = ox != 0 ? 1 : 0;
        if (shutters) {
            for (int side : new int[] {-1, 1}) {
                for (int dy = 0; dy < high; dy++) {
                    int px = x + ox + sx * side, pz = z + oz + sz * side;
                    if (w.free(px, y + dy, pz)) w.set(px, y + dy, pz, t.trapdoor + "[facing=" + out + ",open=true,half=" + (dy == 0 ? "bottom" : "top") + "]");
                }
            }
        }
        if (w.free(x + ox, y - 1, z + oz)) w.set(x + ox, y - 1, z + oz, st(t.stairsTrim, opposite(out), true));
        // a flower box under some ground-floor windows
        if (shutters && w.r.nextInt(3) == 0 && w.free(x + ox, y - 2, z + oz) && !w.free(x, y - 2, z)) {
            w.set(x + ox, y - 1, z + oz, t.trapdoor.replace("trapdoor", "trapdoor") + "[facing=" + out + ",half=top]");
        }
    }

    /** A 2-high door (both halves), with a step outside. */
    public void door(int x, int y, int z, String out, String doorType) {
        w.set(x, y + 1, z, doorType + "[facing=" + opposite(out) + ",half=lower,hinge=left,open=false]");
        w.set(x, y + 2, z, doorType + "[facing=" + opposite(out) + ",half=upper,hinge=left,open=false]");
        int ox = dx(out), oz = dz(out);
        reserve(x - ox * 3 - 1, z - oz * 3 - 1, x + ox * 3 + 1, z + oz * 3 + 1);
    }

    /** A wide opening with an arched top of upside-down stairs: {@code width} odd, centred on (x, z). */
    public void archway(int x, int y, int z, String out, int width, int height, String stairs) {
        int ax = dz(out) != 0 ? 1 : 0, az = dx(out) != 0 ? 1 : 0;
        int half = width / 2;
        for (int o = -half; o <= half; o++)
            for (int dy = 1; dy <= height; dy++) w.set(x + ax * o, y + dy, z + az * o, "air");
        if (stairs != null) {
            String left = ax != 0 ? "east" : "south", right = ax != 0 ? "west" : "north";
            w.set(x - ax * half, y + height, z - az * half, st(stairs, left, true));
            w.set(x + ax * half, y + height, z + az * half, st(stairs, right, true));
        }
        int ox = dx(out), oz = dz(out);
        reserve(Math.min(x - ax * half, x + ox * 4) - 1, Math.min(z - az * half, z + oz * 4) - 1,
                Math.max(x + ax * half, x - ox * 4) + 1, Math.max(z + az * half, z - oz * 4) + 1);
    }

    // ---------------------------------------------------------------- roofs

    /**
     * A pitched roof over the rectangle, ridge along x or z, with {@code over} blocks of overhang, a ridge
     * cap and the gable ends filled. Returns the ridge height.
     */
    public int gable(int x0, int z0, int x1, int z1, int y, boolean ridgeAlongX, int over, String stairs, String slab, String full, P gableWall) {
        int a0 = ridgeAlongX ? z0 : x0, a1 = ridgeAlongX ? z1 : x1;     // across the slope
        int b0 = (ridgeAlongX ? x0 : z0) - over, b1 = (ridgeAlongX ? x1 : z1) + over; // along the ridge
        String up = ridgeAlongX ? "south" : "east", down = ridgeAlongX ? "north" : "west";
        int width = a1 - a0 + 2 * over;
        int steps = (width + 1) / 2;
        int ridge = y;
        for (int i = 0; i <= steps; i++) {
            int lo = a0 - over + i, hi = a1 + over - i;
            int ry = y + i;
            if (lo > hi) break;
            for (int b = b0; b <= b1; b++) {
                int[] pl = ridgeAlongX ? new int[] {b, lo} : new int[] {lo, b};
                int[] ph = ridgeAlongX ? new int[] {b, hi} : new int[] {hi, b};
                if (lo == hi) {
                    w.set(pl[0], ry, pl[1], slab(slab, false));
                } else if (hi - lo == 1) {
                    w.set(pl[0], ry, pl[1], st(stairs, up, false));
                    w.set(ph[0], ry, ph[1], st(stairs, down, false));
                    if (i > 0 || over == 0) w.set(pl[0], ry + 1, pl[1], slab(slab, false));
                    if (i > 0 || over == 0) w.set(ph[0], ry + 1, ph[1], slab(slab, false));
                } else {
                    w.set(pl[0], ry, pl[1], st(stairs, up, false));
                    w.set(ph[0], ry, ph[1], st(stairs, down, false));
                    if (i > 0) {
                        w.set(pl[0], ry - 1, pl[1], full);
                        w.set(ph[0], ry - 1, ph[1], full);
                    }
                }
            }
            // gable end walls, between the slopes, at both ends
            if (gableWall != null) {
                for (int a = lo + 1; a < hi; a++) {
                    if (a < a0 || a > a1) continue;
                    for (int yy = y; yy < ry; yy++) {
                        int[] e0 = ridgeAlongX ? new int[] {x0, a} : new int[] {a, z0};
                        int[] e1 = ridgeAlongX ? new int[] {x1, a} : new int[] {a, z1};
                        w.set(e0[0], yy, e0[1], gableWall.pick(w.r));
                        w.set(e1[0], yy, e1[1], gableWall.pick(w.r));
                    }
                }
            }
            ridge = ry;
        }
        // underside trim of the overhang along the eaves
        if (over > 0) {
            for (int b = b0; b <= b1; b++) {
                int[] pl = ridgeAlongX ? new int[] {b, a0 - over} : new int[] {a0 - over, b};
                int[] ph = ridgeAlongX ? new int[] {b, a1 + over} : new int[] {a1 + over, b};
                if (w.free(pl[0], y - 1, pl[1])) w.set(pl[0], y - 1, pl[1], st(stairs, down, true));
                if (w.free(ph[0], y - 1, ph[1])) w.set(ph[0], y - 1, ph[1], st(stairs, up, true));
            }
        }
        return ridge;
    }

    public int gable(int x0, int z0, int x1, int z1, int y, boolean ridgeAlongX, int over) {
        return gable(x0, z0, x1, z1, y, ridgeAlongX, over, t.stairsRoof, t.slabRoof, t.fullRoof, t.wall2);
    }

    /** A hipped roof (slopes on all four sides) with an optional finial. */
    public int hip(int x0, int z0, int x1, int z1, int y, int over, String stairs, String slab, String full, String finial) {
        int k = 0;
        int ry = y;
        while (true) {
            int ax = x0 - over + k, bx = x1 + over - k, az = z0 - over + k, bz = z1 + over - k;
            ry = y + k;
            if (ax > bx || az > bz) break;
            if (bx - ax <= 1 || bz - az <= 1) {
                for (int x = ax; x <= bx; x++) for (int z = az; z <= bz; z++) w.set(x, ry, z, slab(slab, false));
                break;
            }
            for (int x = ax; x <= bx; x++) {
                w.set(x, ry, az, st(stairs, "south", false));
                w.set(x, ry, bz, st(stairs, "north", false));
            }
            for (int z = az + 1; z < bz; z++) {
                w.set(ax, ry, z, st(stairs, "east", false));
                w.set(bx, ry, z, st(stairs, "west", false));
            }
            for (int[] c : new int[][] {{ax, az}, {ax, bz}, {bx, az}, {bx, bz}}) w.set(c[0], ry, c[1], full);
            if (k > 0) for (int x = ax + 1; x < bx; x++) for (int z = az + 1; z < bz; z++) if (x == ax + 1 || x == bx - 1 || z == az + 1 || z == bz - 1) w.set(x, ry - 1, z, full);
            if (k == 0 && over > 0) {
                for (int x = ax; x <= bx; x++) {
                    if (w.free(x, ry - 1, az)) w.set(x, ry - 1, az, st(stairs, "north", true));
                    if (w.free(x, ry - 1, bz)) w.set(x, ry - 1, bz, st(stairs, "south", true));
                }
                for (int z = az + 1; z < bz; z++) {
                    if (w.free(ax, ry - 1, z)) w.set(ax, ry - 1, z, st(stairs, "west", true));
                    if (w.free(bx, ry - 1, z)) w.set(bx, ry - 1, z, st(stairs, "east", true));
                }
            }
            k++;
        }
        if (finial != null) {
            int cx = (x0 + x1) / 2, cz = (z0 + z1) / 2;
            w.set(cx, ry + 1, cz, finial);
        }
        return ry;
    }

    public int hip(int x0, int z0, int x1, int z1, int y, int over) {
        return hip(x0, z0, x1, z1, y, over, t.stairsRoof, t.slabRoof, t.fullRoof, null);
    }

    /** Pagoda eaves: a flared tier (upturned corners) around a footprint; returns the next tier's height. */
    public int pagodaTier(int x0, int z0, int x1, int z1, int y, int over, String stairs, String slab, String full) {
        int ax = x0 - over, bx = x1 + over, az = z0 - over, bz = z1 + over;
        for (int x = ax; x <= bx; x++) {
            w.set(x, y, az, st(stairs, "south", true));
            w.set(x, y, bz, st(stairs, "north", true));
        }
        for (int z = az; z <= bz; z++) {
            w.set(ax, y, z, st(stairs, "east", true));
            w.set(bx, y, z, st(stairs, "west", true));
        }
        for (int[] c : new int[][] {{ax, az}, {ax, bz}, {bx, az}, {bx, bz}}) {
            w.set(c[0], y, c[1], full);
            w.set(c[0], y + 1, c[1], slab(slab, false)); // the upturned tips
        }
        for (int x = ax + 1; x <= bx - 1; x++) for (int z = az + 1; z <= bz - 1; z++) w.set(x, y + 1, z, full);
        for (int x = ax + 1; x <= bx - 1; x++) {
            w.set(x, y + 1, az + 1, st(stairs, "south", false));
            w.set(x, y + 1, bz - 1, st(stairs, "north", false));
        }
        for (int z = az + 1; z <= bz - 1; z++) {
            w.set(ax + 1, y + 1, z, st(stairs, "east", false));
            w.set(bx - 1, y + 1, z, st(stairs, "west", false));
        }
        for (int x = ax + 2; x <= bx - 2; x++) for (int z = az + 2; z <= bz - 2; z++) {
            boolean rim = x == ax + 2 || x == bx - 2 || z == az + 2 || z == bz - 2;
            if (rim) w.set(x, y + 2, z, slab(slab, false));
        }
        return y + 2;
    }

    /** A dome of {@code spec} (hemisphere shell) centred at (cx, cy, cz). */
    public void dome(int cx, int cy, int cz, double r, String spec, String top) {
        int ir = (int) Math.ceil(r);
        for (int dx = -ir; dx <= ir; dx++)
            for (int dz = -ir; dz <= ir; dz++)
                for (int dy = 0; dy <= ir; dy++) {
                    double d = Math.sqrt(dx * dx + dz * dz + dy * dy);
                    if (d <= r && d > r - 1.1) w.set(cx + dx, cy + dy, cz + dz, spec);
                    else if (d <= r - 1.1) w.set(cx + dx, cy + dy, cz + dz, "air");
                }
        if (top != null) w.set(cx, cy + ir + 1, cz, top);
    }

    /** A cone roof over a round tower: rings of stairs stepping in. */
    public int cone(int cx, int cz, int y, double r, String stairs, String full, String finial) {
        int ry = y;
        for (double rr = r + 1; rr > 0.4; rr -= 0.7) {
            int ir = (int) Math.ceil(rr);
            for (int dx = -ir; dx <= ir; dx++)
                for (int dz = -ir; dz <= ir; dz++) {
                    double d = Math.hypot(dx, dz);
                    if (d > rr + 0.4) continue;
                    if (d > rr - 0.6) {
                        String face = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? "west" : "east") : (dz > 0 ? "north" : "south");
                        w.set(cx + dx, ry, cz + dz, st(stairs, face, false));
                    } else {
                        w.set(cx + dx, ry, cz + dz, full);
                    }
                }
            ry++;
        }
        if (finial != null) w.set(cx, ry, cz, finial);
        return ry;
    }

    /** Battlements: merlons on alternate blocks around a rectangle's edge at height y. */
    public void crenels(int x0, int z0, int x1, int z1, int y, String merlon, String gap) {
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                if (x != x0 && x != x1 && z != z0 && z != z1) continue;
                w.set(x, y, z, ((x + z) & 1) == 0 ? merlon : gap == null ? "air" : gap);
            }
    }

    // ---------------------------------------------------------------- towers and stairs

    /**
     * A round tower: shell of {@code wall}, floors every 5, a ladder up the inside to a platform with
     * battlements or a cone roof on posts. Door on the {@code out} side.
     */
    public void roundTower(int cx, int cz, int y, int r, int h, P wall, String floorSpec, String out, boolean coneRoof) {
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > r + 0.45) continue;
                boolean shell = d > r - 0.8;
                for (int dy = 0; dy <= h; dy++) {
                    int x = cx + dx, z = cz + dz, yy = y + dy;
                    if (shell) {
                        boolean slit = dy % 5 == 3 && (dx == 0 || dz == 0) && dy < h - 1;
                        w.set(x, yy, z, slit ? (t.pane.contains("glass") ? "air" : t.pane) : dy == 0 ? t.trim.pick(w.r) : wall.pick(w.r));
                    } else {
                        w.set(x, yy, z, dy == 0 || dy == h || dy % 5 == 0 ? floorSpec : "air");
                    }
                }
            }
        // ladder up the inside wall opposite the door
        int lx = cx - dx(out) * (r - 1), lz = cz - dz(out) * (r - 1);
        for (int dy = 1; dy <= h; dy++) w.set(lx, y + dy, lz, "ladder[facing=" + out + "]");
        for (int dy = 1; dy <= 2; dy++) w.set(cx + dx(out) * r, y + dy, cz + dz(out) * r, "air");
        door(cx + dx(out) * r, y, cz + dz(out) * r, out, t.wood + "_door");
        for (int dy = 5; dy < h; dy += 5) w.hangLight(cx, y + dy - 1, cz, lantern());
        if (coneRoof) {
            for (int[] c : new int[][] {{-r + 1, -r + 1}, {-r + 1, r - 1}, {r - 1, -r + 1}, {r - 1, r - 1}}) {
                for (int dy = 1; dy <= 3; dy++) w.set(cx + c[0], y + h + dy, cz + c[1], t.fence);
            }
            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > r + 0.45 || d <= r - 0.8) continue;
                w.set(cx + dx, y + h + 1, cz + dz, ((dx + dz) & 1) == 0 ? t.fence : "air");
            }
            cone(cx, cz, y + h + 4, r + 0.6, t.stairsRoof, t.fullRoof, "lightning_rod");
            w.hangLight(cx, y + h + 3, cz, lantern());
        } else {
            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > r + 0.45 || d <= r - 0.8) continue;
                w.set(cx + dx, y + h + 1, cz + dz, ((dx + dz) & 1) == 0 ? wall.pick(w.r) : slab(t.slabRoof, false));
            }
        }
    }

    /** A straight flight of stairs going up toward {@code facing}, solid underneath. */
    public void flight(int x, int y, int z, String facing, int rise, String stairs, String under) {
        for (int i = 0; i < rise; i++) {
            int px = x + dx(facing) * i, pz = z + dz(facing) * i;
            w.set(px, y + i, pz, st(stairs, facing, false));
            for (int k = 1; k <= 2; k++) if (!w.free(px, y + i + k, pz)) w.set(px, y + i + k, pz, "air");
            if (under != null) for (int dy = 0; dy < i; dy++) w.set(px, y + dy, pz, under);
        }
    }

    // ---------------------------------------------------------------- light

    public String lantern() {
        return t.light.contains("soul") ? "soul_lantern" : "lantern";
    }

    /** A post with a light on top (fence + lantern), for paths and yards. */
    public void lampPost(int x, int y, int z, int h) {
        if (reserved(x, z)) return;
        for (int i = 0; i < h; i++) w.set(x, y + i, z, t.fence);
        if (t.light.endsWith("lantern")) w.set(x, y + h, z, lantern() + "[hanging=false]");
        else w.set(x, y + h, z, t.light);
    }

    // ---------------------------------------------------------------- furniture

    public void bed(int x, int y, int z, String facing, String colour) {
        w.set(x, y, z, colour + "_bed[facing=" + facing + ",part=foot]");
        w.set(x + dx(facing), y, z + dz(facing), colour + "_bed[facing=" + facing + ",part=head]");
    }

    /** A small table: a fence leg and a pressure plate top. */
    public void table(int x, int y, int z) {
        w.set(x, y, z, t.fence);
        w.set(x, y + 1, z, t.wood + "_pressure_plate");
    }

    /** A long table of top slabs on fence legs, with chairs down both sides and candles. */
    public void longTable(int x0, int z0, int x1, int z1, int y, String chairWood) {
        boolean alongX = x1 - x0 >= z1 - z0;
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                w.set(x, y, z, slab(t.wood + "_slab", true));
                if ((alongX ? x - x0 : z - z0) % 3 == 0 && (x == x0 || x == x1 || z == z0 || z == z1)) w.set(x, y, z, t.fence);
            }
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                if (w.r.nextInt(4) == 0) w.set(x, y + 1, z, "candle[candles=" + (1 + w.r.nextInt(3)) + ",lit=true]");
            }
        if (alongX) {
            for (int x = x0; x <= x1; x++) {
                chair(x, y, z0 - 1, "north");
                chair(x, y, z1 + 1, "south");
            }
        } else {
            for (int z = z0; z <= z1; z++) {
                chair(x0 - 1, y, z, "west");
                chair(x1 + 1, y, z, "east");
            }
        }
    }

    /** A chair: stairs whose back is toward {@code back}. */
    public void chair(int x, int y, int z, String back) {
        if (reserved(x, z) || !w.free(x, y, z)) return;
        w.set(x, y, z, st(t.wood + "_stairs", back, false));
    }

    public void rug(int x0, int z0, int x1, int z1, int y, String colour, String border) {
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                if (!w.free(x, y, z)) continue;
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                w.set(x, y, z, (edge && border != null ? border : colour) + "_carpet");
            }
    }

    /** Bookshelves (with the odd chiseled one) along a wall line, {@code h} high. */
    public void shelves(int x0, int z0, int x1, int z1, int y, int h, String facing) {
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++)
                for (int dy = 0; dy < h; dy++) {
                    if (reserved(x, z)) continue;
                    w.set(x, y + dy, z, w.r.nextInt(5) == 0 ? "chiseled_bookshelf[facing=" + facing + ",slot_0_occupied=true,slot_1_occupied="
                            + w.r.nextBoolean() + ",slot_3_occupied=true,slot_4_occupied=" + w.r.nextBoolean() + "]" : "bookshelf");
                }
    }

    /** A stack of barrels, chests and crates in a corner. */
    public void stores(int x, int y, int z, int n) {
        for (int i = 0; i < n; i++) {
            int px = x + (i % 2), pz = z + (i / 2 % 2);
            if (reserved(px, pz)) continue;
            int h = 1 + w.r.nextInt(2);
            for (int dy = 0; dy < h; dy++) {
                String spec = switch (w.r.nextInt(4)) {
                    case 0 -> "barrel[facing=up]";
                    case 1 -> "barrel[facing=north]";
                    case 2 -> "chest[facing=south]";
                    default -> "composter";
                };
                if (dy > 0 && spec.startsWith("chest")) spec = "barrel[facing=up]";
                w.set(px, y + dy, pz, spec);
            }
        }
    }

    /** A fireplace in a wall: brick hearth, a campfire, mantel, and a chimney up to {@code top}. */
    public void fireplace(int x, int y, int z, String into, int top) {
        int ix = dx(into), iz = dz(into);
        int sx = iz != 0 ? 1 : 0, sz = ix != 0 ? 1 : 0;
        for (int s = -1; s <= 1; s++)
            for (int dy = 1; dy <= 3; dy++) w.set(x + sx * s, y + dy, z + sz * s, "bricks");
        w.set(x, y + 1, z, "campfire[lit=true,facing=" + opposite(into) + "]");
        w.set(x, y + 2, z, "air");
        w.set(x - ix, y + 3, z - iz, st("brick_stairs", into, true));
        w.set(x - ix + sx, y + 3, z - iz + sz, st("brick_stairs", into, true));
        w.set(x - ix - sx, y + 3, z - iz - sz, st("brick_stairs", into, true));
        for (int yy = y + 4; yy <= top; yy++) w.set(x + ix, yy, z + iz, yy == top ? "campfire[lit=true,signal_fire=false]" : "bricks");
        w.set(x - ix - sx, y + 4, z - iz - sz, "flower_pot");
        w.set(x - ix + sx, y + 4, z - iz + sz, "candle[candles=3,lit=true]");
    }

    public void banner(int x, int y, int z, String colour, String facing) {
        w.set(x, y, z, colour + "_wall_banner[facing=" + facing + "]");
    }

    static final String[] POTS = {"potted_fern", "potted_azalea_bush", "potted_red_tulip", "potted_cornflower", "potted_oak_sapling",
            "potted_flowering_azalea_bush", "potted_poppy", "potted_blue_orchid"};

    public void pot(int x, int y, int z) {
        if (w.free(x, y, z)) w.set(x, y, z, POTS[w.r.nextInt(POTS.length)]);
    }

    // ---------------------------------------------------------------- whole rooms

    /** A room to furnish: floor at y, ceiling at y + h, walls on the rectangle's edge. */
    public record Room(int x0, int z0, int x1, int z1, int y, int h) {
        int ix0() { return x0 + 1; }
        int ix1() { return x1 - 1; }
        int iz0() { return z0 + 1; }
        int iz1() { return z1 - 1; }
        int cx() { return (x0 + x1) / 2; }
        int cz() { return (z0 + z1) / 2; }
    }

    /** Bunks along the long walls, chests at their feet, a rug down the middle and lights. */
    public void bunkroom(Room r, String colour) {
        boolean alongX = r.x1() - r.x0() >= r.z1() - r.z0();
        int y = r.y() + 1;
        if (alongX) {
            for (int x = r.ix0() + 1; x <= r.ix1() - 1; x += 3) {
                if (!reserved(x, r.iz0()) && !reserved(x, r.iz0() + 1)) {
                    bed(x, y, r.iz0() + 1, "north", colour);
                    if (w.free(x + 1, y, r.iz0())) w.set(x + 1, y, r.iz0(), "chest[facing=south]");
                }
                if (!reserved(x, r.iz1()) && !reserved(x, r.iz1() - 1)) {
                    bed(x, y, r.iz1() - 1, "south", colour);
                    if (w.free(x + 1, y, r.iz1())) w.set(x + 1, y, r.iz1(), "barrel[facing=up]");
                }
            }
        } else {
            for (int z = r.iz0() + 1; z <= r.iz1() - 1; z += 3) {
                if (!reserved(r.ix0(), z) && !reserved(r.ix0() + 1, z)) {
                    bed(r.ix0() + 1, y, z, "west", colour);
                    if (w.free(r.ix0(), y, z + 1)) w.set(r.ix0(), y, z + 1, "chest[facing=east]");
                }
                if (!reserved(r.ix1(), z) && !reserved(r.ix1() - 1, z)) {
                    bed(r.ix1() - 1, y, z, "east", colour);
                    if (w.free(r.ix1(), y, z + 1)) w.set(r.ix1(), y, z + 1, "barrel[facing=up]");
                }
            }
        }
        rug(alongX ? r.ix0() + 1 : r.cx() - 1, alongX ? r.cz() - 1 : r.iz0() + 1, alongX ? r.ix1() - 1 : r.cx() + 1, alongX ? r.cz() + 1 : r.iz1() - 1, y, colour, "white");
        lights(r);
    }

    /** Weapon racks: anvils, grindstones, smithing tables, barrels of arrows and a forge corner. */
    public void armoury(Room r) {
        int y = r.y() + 1;
        String[] props = {"anvil[facing=north]", "grindstone[face=floor,facing=east]", "smithing_table", "barrel[facing=up]", "fletching_table",
                "chipped_anvil[facing=east]", "stonecutter[facing=north]", "barrel[facing=up]"};
        int i = 0;
        for (int x = r.ix0(); x <= r.ix1(); x += 2) {
            if (!reserved(x, r.iz0()) && w.free(x, y, r.iz0())) w.set(x, y, r.iz0(), props[i++ % props.length]);
            if (!reserved(x, r.iz1()) && w.free(x, y, r.iz1())) w.set(x, y, r.iz1(), props[i++ % props.length]);
        }
        for (int z = r.iz0() + 1; z <= r.iz1() - 1; z += 2) {
            if (!reserved(r.ix0(), z) && w.free(r.ix0(), y, z)) w.set(r.ix0(), y, z, "barrel[facing=east]");
        }
        rug(r.ix0() + 2, r.cz() - 1, r.ix1() - 2, r.cz() + 1, y, "TEAM", "black");
        lights(r);
    }

    /** A great hall: a long table with chairs, a fireplace, banners, a rug. */
    public void greatHall(Room r, String fireWall, int chimneyTop) {
        int y = r.y() + 1;
        boolean alongX = r.x1() - r.x0() >= r.z1() - r.z0();
        int cx = r.cx(), cz = r.cz();
        if (alongX) longTable(r.ix0() + 3, cz, r.ix1() - 3, cz, y, t.wood);
        else longTable(cx, r.iz0() + 3, cx, r.iz1() - 3, y, t.wood);
        if (fireWall != null) {
            int fx = fireWall.equals("west") ? r.x0() : fireWall.equals("east") ? r.x1() : cx;
            int fz = fireWall.equals("north") ? r.z0() : fireWall.equals("south") ? r.z1() : cz;
            fireplace(fx, r.y(), fz, fireWall, chimneyTop);
        }
        lights(r);
    }

    /** Kitchen and pantry: smoker, furnace, cauldron, barrels, a prep table and hanging herbs. */
    public void kitchen(Room r) {
        int y = r.y() + 1;
        String[] line = {"smoker[facing=south,lit=true]", "furnace[facing=south]", "water_cauldron[level=3]", "barrel[facing=up]",
                "crafting_table", "barrel[facing=north]", "composter"};
        int i = 0;
        for (int x = r.ix0(); x <= r.ix1(); x++) {
            if (reserved(x, r.iz0()) || !w.free(x, y, r.iz0())) continue;
            w.set(x, y, r.iz0(), line[i++ % line.length]);
            if (w.r.nextBoolean() && w.free(x, y + 1, r.iz0())) w.set(x, y + 1, r.iz0(), w.r.nextBoolean() ? "potted_fern" : "candle[candles=2,lit=true]");
        }
        table(r.cx(), y, r.cz());
        stores(r.ix1() - 1, y, r.iz1() - 1, 3);
        lights(r);
    }

    /** Library / war room: shelves round the walls, a map table, a lectern and armchairs. */
    public void library(Room r) {
        int y = r.y() + 1;
        int h = Math.min(3, r.h() - 1);
        shelves(r.ix0(), r.iz0(), r.ix1(), r.iz0(), y, h, "south");
        shelves(r.ix0(), r.iz1(), r.ix1(), r.iz1(), y, h, "north");
        w.set(r.cx(), y, r.cz(), "cartography_table");
        w.set(r.cx() + 1, y, r.cz(), "lectern[facing=west]");
        chair(r.cx() - 1, y, r.cz(), "west");
        chair(r.cx(), y, r.cz() - 1, "north");
        rug(r.cx() - 2, r.cz() - 2, r.cx() + 2, r.cz() + 2, y, "brown", "black");
        lights(r);
    }

    /** A storeroom: barrels, chests, hay and crates around the walls. */
    public void storeroom(Room r) {
        int y = r.y() + 1;
        for (int x = r.ix0(); x <= r.ix1(); x += 2) stores(x, y, r.iz0(), 2);
        for (int x = r.ix0(); x <= r.ix1(); x += 3) if (!reserved(x, r.iz1()) && w.free(x, y, r.iz1())) w.set(x, y, r.iz1(), w.r.nextBoolean() ? "hay_block" : "barrel[facing=up]");
        lights(r);
    }

    /** Ceiling lights spread over a room (hung on chains from the ceiling). */
    public void lights(Room r) {
        int top = r.y() + r.h() - 1;
        int nx = Math.max(1, (r.x1() - r.x0()) / 6), nz = Math.max(1, (r.z1() - r.z0()) / 6);
        for (int i = 0; i < nx; i++)
            for (int j = 0; j < nz; j++) {
                int x = r.x0() + (r.x1() - r.x0()) * (2 * i + 1) / (2 * nx);
                int z = r.z0() + (r.z1() - r.z0()) * (2 * j + 1) / (2 * nz);
                if (w.free(x, top, z)) w.hangLight(x, top, z, lantern());
            }
    }

    // ---------------------------------------------------------------- bridges

    /**
     * A walkable bridge between two points: a solid 3-wide deck (no gaps, even on diagonals), side beams,
     * railings with posts and lights, and a truss underneath. It's only built over open air: where it meets
     * the ground it stops, so it never runs through an island.
     */
    public void bridge(int x1, int y1, int z1, int x2, int y2, int z2, String deck, String edge, String rail, boolean lights) {
        double len = Math.hypot(x2 - x1, z2 - z1);
        int steps = (int) Math.ceil(len * 2);
        double ux = (x2 - x1) / len, uz = (z2 - z1) / len;
        double nx = -uz, nz = ux;
        java.util.Set<Long> deckCells = new java.util.HashSet<>();
        java.util.Map<Long, Integer> deckY = new java.util.HashMap<>();
        for (int s = 0; s <= steps; s++) {
            double k = s / (double) steps;
            double px = x1 + (x2 - x1) * k, pz = z1 + (z2 - z1) * k;
            double arch = Math.sin(k * Math.PI) * Math.min(2, len / 20.0);
            int py = (int) Math.round(y1 + (y2 - y1) * k + arch);
            for (double o = -1.6; o <= 1.6; o += 0.4) {
                int bx = (int) Math.round(px + nx * o), bz = (int) Math.round(pz + nz * o);
                long key = Terrain.key(bx, bz);
                if (deckCells.add(key)) deckY.put(key, py);
            }
        }
        java.util.Set<Long> railCells = new java.util.HashSet<>();
        for (long key : deckCells) {
            int bx = (int) (key >> 32), bz = (int) key;
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                long n = Terrain.key(bx + d[0], bz + d[1]);
                if (!deckCells.contains(n)) railCells.add(n);
            }
        }
        for (long key : deckCells) {
            int bx = (int) (key >> 32), bz = (int) key;
            if (!w.canon(bx, bz)) continue;
            int y = deckY.get(key);
            if (onLand(bx, y, bz)) continue;
            w.set(bx, y, bz, deck);
            for (int dy = 1; dy <= 3; dy++) if (!w.free(bx, y + dy, bz)) w.set(bx, y + dy, bz, "air");
            w.set(bx, y - 1, bz, edge);
        }
        int count = 0;
        for (long key : railCells) {
            int bx = (int) (key >> 32), bz = (int) key;
            if (!w.canon(bx, bz)) continue;
            Integer y = null;
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                Integer ny = deckY.get(Terrain.key(bx + d[0], bz + d[1]));
                if (ny != null) y = y == null ? ny : Math.max(y, ny);
            }
            if (y == null || onLand(bx, y, bz)) continue;
            w.set(bx, y, bz, edge);
            boolean post = (bx * 7 + bz * 13 & 7) == 0;
            w.set(bx, y + 1, bz, post ? edge : rail);
            if (post && lights) {
                w.set(bx, y + 2, bz, rail);
                w.set(bx, y + 3, bz, t.light.endsWith("lantern") ? lantern() + "[hanging=false]" : t.light);
            }
            if (count++ % 3 == 0) w.set(bx, y - 1, bz, st(t.stairsTrim, "north", true));
        }
    }

    /** Is there island under (or at) this deck spot? */
    private boolean onLand(int x, int y, int z) {
        for (int dy = -2; dy <= 1; dy++) {
            org.bukkit.Material m = w.type(x, y + dy, z);
            if (m.isSolid() && land.natural(m)) return true;
        }
        return false;
    }
}
