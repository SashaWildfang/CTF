package com.sasha.ctfbuilder.v2;

import com.sasha.ctfbuilder.v2.W.P;

import java.util.ArrayList;
import java.util.List;

/**
 * Buildings and set pieces: the team base (keep, towers, courtyard, flag dais, wool monuments), the hill
 * shrine, bridges and small props. Bases are built for red, facing +x (toward the middle of the map);
 * the writer copies them to the other teams.
 */
public final class Build {

    final W w;
    final Theme t;
    final Terrain land;

    public Build(W w, Theme t, Terrain land) {
        this.w = w;
        this.t = t;
        this.land = land;
    }

    /** What a base gives the map file. */
    public static final class Base {
        public double[] spawn;          // x y z yaw
        public int[] flag;
        public final List<int[]> wools = new ArrayList<>();
    }

    // ---------------------------------------------------------------- helpers

    String light(boolean hanging) {
        return t.light.endsWith("lantern") && !t.light.equals("sea_lantern") ? t.light + "[hanging=" + hanging + "]" : t.light;
    }

    String stairs(String base, String facing, boolean top) {
        return base + "[facing=" + facing + ",half=" + (top ? "top" : "bottom") + "]";
    }

    void post(int x, int y, int z, int h) {
        for (int i = 0; i < h; i++) w.set(x, y + i, z, t.fence);
        w.set(x, y + h, z, light(false));
    }

    // ---------------------------------------------------------------- roofs

    /** A pitched roof over [x0..x1] x [z0..z1] whose ridge runs along z, starting at height y. */
    void gable(int x0, int x1, int z0, int z1, int y) {
        int half = (x1 - x0 + 2) / 2;
        for (int i = 0; i <= half; i++) {
            int ry = y + i;
            int west = x0 - 1 + i, east = x1 + 1 - i;
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                if (west < east) {
                    w.set(west, ry, z, stairs(t.stairsRoof, "east", false));
                    w.set(east, ry, z, stairs(t.stairsRoof, "west", false));
                    if (i > 0) {
                        w.set(west, ry - 1, z, t.fullRoof);
                        w.set(east, ry - 1, z, t.fullRoof);
                    }
                } else {
                    w.set(west, ry, z, t.slabRoof + "[type=bottom]");
                    if (west != east) w.set(east, ry, z, t.slabRoof + "[type=bottom]");
                }
            }
            // gable ends
            for (int x = west + 1; x < east; x++) {
                for (int yy = y; yy < ry; yy++) {
                    w.set(x, yy, z0, t.wall2.pick(w.r));
                    w.set(x, yy, z1, t.wall2.pick(w.r));
                }
            }
        }
        // eave trim
        for (int z = z0 - 1; z <= z1 + 1; z++) {
            w.set(x0 - 1, y - 1, z, stairs(t.stairsRoof, "west", true));
            w.set(x1 + 1, y - 1, z, stairs(t.stairsRoof, "east", true));
        }
    }

    /** A hipped roof (slopes on all four sides), {@code rise} blocks per step in, capped with a spire if asked. */
    void hip(int x0, int x1, int z0, int z1, int y, boolean spire) {
        int k = 0;
        while (x0 + k <= x1 - k && z0 + k <= z1 - k) {
            int ax = x0 - 1 + k, bx = x1 + 1 - k, az = z0 - 1 + k, bz = z1 + 1 - k;
            int ry = y + k;
            if (ax >= bx - 1 || az >= bz - 1) {
                for (int x = ax; x <= bx; x++) for (int z = az; z <= bz; z++) w.set(x, ry, z, t.slabRoof + "[type=bottom]");
                break;
            }
            for (int x = ax; x <= bx; x++) {
                w.set(x, ry, az, stairs(t.stairsRoof, "south", false));
                w.set(x, ry, bz, stairs(t.stairsRoof, "north", false));
            }
            for (int z = az + 1; z < bz; z++) {
                w.set(ax, ry, z, stairs(t.stairsRoof, "east", false));
                w.set(bx, ry, z, stairs(t.stairsRoof, "west", false));
            }
            for (int[] c : new int[][] {{ax, az}, {ax, bz}, {bx, az}, {bx, bz}}) w.set(c[0], ry, c[1], t.fullRoof);
            if (k > 0) for (int x = ax + 1; x < bx; x++) for (int z = az + 1; z < bz; z++) if (x == ax + 1 || x == bx - 1 || z == az + 1 || z == bz - 1) w.set(x, ry - 1, z, t.fullRoof);
            k++;
        }
        if (spire) {
            int cx = (x0 + x1) / 2, cz = (z0 + z1) / 2, sy = y + k;
            for (int i = 0; i < 3; i++) w.set(cx, sy + i, cz, t.fence);
            w.set(cx, sy + 3, cz, "lightning_rod");
        }
    }

    /** Pagoda roof: wide flared eaves in tiers. */
    void tiered(int x0, int x1, int z0, int z1, int y, int tiers) {
        int inset = 0;
        int ry = y;
        for (int tier = 0; tier < tiers; tier++) {
            int ax = x0 - 2 + inset, bx = x1 + 2 - inset, az = z0 - 2 + inset, bz = z1 + 2 - inset;
            if (ax >= bx - 2 || az >= bz - 2) break;
            // flared eave: upside-down stairs at the rim, then a slope of two rows
            for (int x = ax; x <= bx; x++) {
                w.set(x, ry, az, stairs(t.stairsRoof, "south", true));
                w.set(x, ry, bz, stairs(t.stairsRoof, "north", true));
            }
            for (int z = az; z <= bz; z++) {
                w.set(ax, ry, z, stairs(t.stairsRoof, "east", true));
                w.set(bx, ry, z, stairs(t.stairsRoof, "west", true));
            }
            for (int[] c : new int[][] {{ax, az}, {ax, bz}, {bx, az}, {bx, bz}}) w.set(c[0], ry + 1, c[1], t.slabRoof + "[type=bottom]");
            for (int x = ax + 1; x <= bx - 1; x++) for (int z = az + 1; z <= bz - 1; z++) w.set(x, ry + 1, z, t.fullRoof);
            for (int x = ax + 1; x <= bx - 1; x++) {
                w.set(x, ry + 1, az + 1, stairs(t.stairsRoof, "south", false));
                w.set(x, ry + 1, bz - 1, stairs(t.stairsRoof, "north", false));
            }
            for (int z = az + 1; z <= bz - 1; z++) {
                w.set(ax + 1, ry + 1, z, stairs(t.stairsRoof, "east", false));
                w.set(bx - 1, ry + 1, z, stairs(t.stairsRoof, "west", false));
            }
            // a short wall up to the next tier
            int wx0 = ax + 3, wx1 = bx - 3, wz0 = az + 3, wz1 = bz - 3;
            for (int x = wx0; x <= wx1; x++) for (int z = wz0; z <= wz1; z++) {
                boolean edge = x == wx0 || x == wx1 || z == wz0 || z == wz1;
                for (int dy = 2; dy <= 3; dy++) if (edge) w.set(x, ry + dy, z, (x == wx0 || x == wx1) && (z == wz0 || z == wz1) ? t.log + "[axis=y]" : t.wall.pick(w.r));
            }
            inset += 3;
            ry += 4;
        }
        hip(x0 - 2 + inset, x1 + 2 - inset, z0 - 2 + inset, z1 + 2 - inset, ry, true);
    }

    void flatRoof(int x0, int x1, int z0, int z1, int y) {
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) w.set(x, y, z, t.trim.pick(w.r));
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
            if (edge && ((x + z) & 1) == 0) w.set(x, y + 1, z, t.wall.pick(w.r));
            else if (edge) w.set(x, y + 1, z, t.slabRoof + "[type=bottom]");
        }
        // a dome in the middle
        int cx = (x0 + x1) / 2, cz = (z0 + z1) / 2;
        double r = Math.min(x1 - x0, z1 - z0) / 3.0;
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) for (int dy = 0; dy <= 4; dy++) {
            double d = Math.sqrt(dx * dx + dz * dz + dy * dy * 1.3);
            if (d <= r && d > r - 1.2) w.set(cx + dx, y + 1 + dy, cz + dz, t.fullRoof);
        }
        w.set(cx, y + 2 + (int) r, cz, t.fence);
    }

    void roof(int x0, int x1, int z0, int z1, int y) {
        switch (t.roof) {
            case GABLE -> gable(x0, x1, z0, z1, y);
            case TIERED -> tiered(x0, x1, z0, z1, y, 2);
            case FLAT -> flatRoof(x0, x1, z0, z1, y);
            case SPIRE -> hip(x0, x1, z0, z1, y, true);
        }
    }

    // ---------------------------------------------------------------- walls

    /** A rectangular building shell with framed corners, windows with shutters and floors every {@code storey}. */
    void hall(int x0, int x1, int z0, int z1, int y, int height, int storey) {
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
                boolean pillar = edge && ((x - x0) % 4 == 0 && (z == z0 || z == z1) || (z - z0) % 4 == 0 && (x == x0 || x == x1));
                for (int dy = 0; dy <= height; dy++) {
                    int yy = y + dy;
                    if (!edge) {
                        w.set(x, yy, z, dy == 0 || dy % storey == 0 && dy < height ? t.floor.pick(w.r) : "air");
                        continue;
                    }
                    if (corner || pillar) w.set(x, yy, z, corner ? t.trim.pick(w.r) : t.log + "[axis=y]");
                    else if (dy == 0) w.set(x, yy, z, t.trim.pick(w.r));
                    else if (dy % storey == 0) w.set(x, yy, z, t.log + "[axis=" + (z == z0 || z == z1 ? "x" : "z") + "]");
                    else w.set(x, yy, z, dy < storey ? t.wall.pick(w.r) : t.wall2.pick(w.r));
                }
            }
        // windows between pillars on every storey
        for (int s = 0; s * storey + 2 < height; s++) {
            int wy = y + s * storey + 2;
            for (int x = x0 + 2; x <= x1 - 2; x += 4) {
                window(x, wy, z0, "north");
                window(x, wy, z1, "south");
            }
            for (int z = z0 + 2; z <= z1 - 2; z += 4) {
                window(x0, wy, z, "west");
                window(x1, wy, z, "east");
            }
        }
    }

    /** A two-high window in a wall facing {@code out}, with open shutters on the outside. */
    void window(int x, int y, int z, String out) {
        w.set(x, y, z, t.pane);
        w.set(x, y + 1, z, t.pane);
        int ox = out.equals("east") ? 1 : out.equals("west") ? -1 : 0, oz = out.equals("south") ? 1 : out.equals("north") ? -1 : 0;
        int sx = oz != 0 ? 1 : 0, sz = ox != 0 ? 1 : 0;
        for (int side : new int[] {-1, 1}) {
            for (int dy = 0; dy <= 1; dy++) {
                w.set(x + ox + sx * side, y + dy, z + oz + sz * side, t.trapdoor + "[facing=" + out + ",open=true,half=" + (dy == 0 ? "bottom" : "top") + "]");
            }
        }
        w.set(x + ox, y - 1, z + oz, stairs(t.stairsTrim, opposite(out), true));
    }

    static String opposite(String facing) {
        return switch (facing) {
            case "north" -> "south";
            case "south" -> "north";
            case "east" -> "west";
            default -> "east";
        };
    }

    /** A round tower with a ladder up to a battlemented top and a roof on posts. */
    void tower(int cx, int cz, int y, int r, int h) {
        for (int dx = -r - 1; dx <= r + 1; dx++)
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > r + 0.45) continue;
                boolean shell = d > r - 0.8;
                for (int dy = 0; dy <= h; dy++) {
                    int x = cx + dx, z = cz + dz, yy = y + dy;
                    if (shell) {
                        boolean slit = dy % 4 == 2 && (dx == 0 || dz == 0);
                        w.set(x, yy, z, slit ? "air" : dy == h ? t.trim.pick(w.r) : t.wall.pick(w.r));
                    } else {
                        w.set(x, yy, z, dy == 0 || dy == h ? t.floor.pick(w.r) : "air");
                    }
                }
                if (shell && ((dx + dz) & 1) == 0) w.set(cx + dx, y + h + 1, cz + dz, t.wall.pick(w.r));
            }
        // ladder against the inside of the shell, and a hatch through the top floor
        for (int dy = 1; dy <= h; dy++) w.set(cx + r - 1, y + dy, cz, "ladder[facing=west]");
        w.set(cx + r - 1, y + h, cz, "ladder[facing=west]");
        // corbelled cap: a small spire roof
        hip(cx - r + 1, cx + r - 1, cz - r + 1, cz + r - 1, y + h + 4, true);
        for (int[] c : new int[][] {{-r + 1, -r + 1}, {-r + 1, r - 1}, {r - 1, -r + 1}, {r - 1, r - 1}}) {
            for (int dy = 1; dy <= 3; dy++) w.set(cx + c[0], y + h + dy, cz + c[1], t.fence);
        }
        w.set(cx, y + h + 3, cz, light(true));
    }

    // ---------------------------------------------------------------- the base

    /**
     * A team base around (bx, by, 0) facing +x: the keep at the back (spawn inside), a paved courtyard with
     * the flag dais, two towers by the front, and two wool monuments on the flanks.
     */
    public Base base(int bx, int by, int monumentZ) {
        Base base = new Base();
        // keep
        int kx0 = bx - 15, kx1 = bx - 5, kz0 = -7, kz1 = 7;
        hall(kx0, kx1, kz0, kz1, by, 10, 5);
        // door arch on the front (+x) wall
        for (int dy = 1; dy <= 3; dy++) for (int z = -1; z <= 1; z++) w.set(kx1, by + dy, z, "air");
        w.set(kx1, by + 4, -1, stairs(t.stairsTrim, "south", true));
        w.set(kx1, by + 4, 1, stairs(t.stairsTrim, "north", true));
        w.set(kx1 + 1, by + 4, -2, "TEAM_wall_banner[facing=east]");
        w.set(kx1 + 1, by + 4, 2, "TEAM_wall_banner[facing=east]");
        roof(kx0, kx1, kz0, kz1, by + 11);
        // inside: carpet runner, chandelier, benches, stairs to the upper floor
        for (int x = kx0 + 2; x <= kx1 - 1; x++) w.set(x, by + 1, 0, "TEAM_carpet");
        for (int i = 0; i < 4; i++) {
            w.set(kx0 + 2 + i, by + 1 + i, kz1 - 1, stairs(t.wood + "_stairs", "east", false));
            for (int dy = 1; dy < 1 + i; dy++) w.set(kx0 + 2 + i, by + dy, kz1 - 1, t.planks);
            w.set(kx0 + 2 + i, by + 5, kz1 - 1, "air");
        }
        for (int x = kx0 + 3; x <= kx0 + 5; x++) w.set(x, by + 6, kz1 - 2, t.fence);
        w.set((kx0 + kx1) / 2, by + 4, 0, light(true));
        w.set((kx0 + kx1) / 2, by + 9, 0, light(true));
        for (int x : new int[] {kx0 + 3, kx1 - 3}) {
            w.set(x, by + 1, -4, stairs(t.wood + "_stairs", "south", false));
            w.set(x, by + 1, 4, stairs(t.wood + "_stairs", "north", false));
        }
        w.set(kx0 + 1, by + 1, kz0 + 1, "barrel[facing=up]");
        w.set(kx0 + 1, by + 2, kz0 + 1, "barrel[facing=up]");
        w.set(kx0 + 2, by + 1, kz0 + 1, "crafting_table");
        w.set(kx0 + 1, by + 3, 0, "TEAM_wall_banner[facing=east]");
        base.spawn = new double[] {kx0 + 4.5, by + 1, 0.5, -90};
        // porch, chimney
        for (int z = -2; z <= 2; z++) w.set(kx1 + 1, by, z, t.trim.pick(w.r));
        for (int side : new int[] {-1, 1}) post(kx1 + 1, by + 1, side * 3, 2);
        for (int dy = 6; dy <= 19; dy++) for (int dx = 0; dx <= 1; dx++) for (int dz = 0; dz <= 1; dz++) w.set(kx0 + 2 + dx, by + dy, kz0 + 2 + dz, dy > 16 ? "bricks" : "cobblestone");
        w.set(kx0 + 2, by + 20, kz0 + 2, "campfire[lit=true,signal_fire=true]");
        w.set(kx0 + 3, by + 20, kz0 + 3, "cobblestone_wall");
        // side buttresses
        for (int x = kx0 + 2; x <= kx1 - 2; x += 4) for (int side : new int[] {-1, 1}) {
            int z = side * (kz1 + 1);
            for (int dy = 0; dy <= 3; dy++) w.set(x, by + dy, z, t.trim.pick(w.r));
            w.set(x, by + 4, z, stairs(t.stairsTrim, side < 0 ? "south" : "north", false));
        }

        // courtyard paving and low walls
        int cx0 = bx - 4, cx1 = bx + 13, cz = 13;
        for (int x = cx0; x <= cx1; x++)
            for (int z = -cz; z <= cz; z++) {
                double d = Math.max(Math.abs(z) / (double) cz, 0);
                w.set(x, by, z, d > 0.95 ? t.trim.pick(w.r) : (Math.abs(z) <= 2 && x > bx - 4) ? t.path.pick(w.r) : t.floor.pick(w.r));
                for (int dy = 1; dy <= 6; dy++) if (!w.air(x, by + dy, z) && x > kx1) w.set(x, by + dy, z, "air");
            }
        for (int x = cx0; x <= cx1; x++) for (int side : new int[] {-1, 1}) {
            w.set(x, by + 1, side * cz, t.wall.pick(w.r));
            if (((x + cz) & 1) == 0) w.set(x, by + 2, side * cz, t.wall.pick(w.r));
            else w.set(x, by + 2, side * cz, t.slabRoof + "[type=bottom]");
        }
        // front towers and the gate between them
        for (int side : new int[] {-1, 1}) tower(cx1, side * (cz - 1), by, 3, 13);
        // market tents in the team colour
        for (int side : new int[] {-1, 1}) tent(bx + 8, by + 1, side * 8);
        for (int z = -cz + 4; z <= cz - 4; z++) {
            if (Math.abs(z) <= 3) continue;
            w.set(cx1, by + 1, z, t.wall.pick(w.r));
            w.set(cx1, by + 2, z, ((z & 1) == 0) ? t.wall.pick(w.r) : t.slabRoof + "[type=bottom]");
        }
        for (int side : new int[] {-1, 1}) {
            for (int dy = 1; dy <= 5; dy++) w.set(cx1, by + dy, side * 4, t.log + "[axis=y]");
            w.set(cx1, by + 6, side * 4, light(false));
            w.set(cx1 + 1, by + 4, side * 4, "TEAM_wall_banner[facing=east]");
        }
        // flag dais
        int fx = bx + 4;
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                boolean rim = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                w.set(fx + dx, by, fz(dz), rim ? t.trim.pick(w.r) : "TEAM_concrete");
            }
        w.set(fx, by, 0, "chiseled_stone_bricks".equals(t.trim.specs.get(0)) ? "chiseled_stone_bricks" : t.trim.pick(w.r));
        for (int[] c : new int[][] {{-2, -2}, {-2, 2}, {2, -2}, {2, 2}}) post(fx + c[0], by + 1, c[1], 2);
        base.flag = new int[] {fx, by + 1, 0};
        land.keeps.add(new Terrain.Keep(bx, 0, 27));

        // two wool monuments on the flanks, behind the courtyard walls
        for (int side : new int[] {-1, 1}) monument(bx - 2, by, side * monumentZ, base);
        land.keeps.add(new Terrain.Keep(bx - 2, monumentZ, 6));
        land.keeps.add(new Terrain.Keep(bx - 2, -monumentZ, 6));
        // props around the yard
        for (int side : new int[] {-1, 1}) {
            int z = side * (cz - 2);
            w.set(bx + 1, by + 1, z, "barrel[facing=up]");
            w.set(bx + 2, by + 1, z, "hay_block");
            w.set(bx + 2, by + 2, z, "barrel[facing=up]");
            w.set(bx + 6, by + 1, z, "composter");
            w.set(bx + 8, by + 1, z, "grindstone[face=floor,facing=north]");
            post(bx + 10, by + 1, side * (cz - 1), 3);
        }
        return base;
    }

    private static int fz(int z) { return z; }

    /** A little striped market tent on four posts. */
    void tent(int x, int y, int z) {
        for (int[] c : new int[][] {{-2, -2}, {-2, 2}, {2, -2}, {2, 2}}) for (int dy = 0; dy <= 2; dy++) w.set(x + c[0], y + dy, z + c[1], t.fence);
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                int lift = (Math.abs(dz) <= 0 ? 1 : 0);
                w.set(x + dx, y + 3 + lift, z + dz, ((dx + 2) % 2 == 0) ? "TEAM_wool" : "white_wool");
            }
        w.set(x - 1, y, z, "barrel[facing=up]");
        w.set(x + 1, y, z + 1, "hay_block");
        w.set(x, y, z - 1, "composter");
        w.set(x, y + 2, z, light(true));
    }

    /** A wool monument: a plinth with the team's beacon in the middle and five wool around it. */
    public void monument(int x, int y, int z, Base base) {
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) {
                double d = Math.max(Math.abs(dx), Math.abs(dz));
                for (int dy = -3; dy <= 0; dy++) w.set(x + dx, y + dy, z + dz, d >= 3 ? t.trim.pick(w.r) : t.wall.pick(w.r));
                for (int dy = 1; dy <= 6; dy++) if (!w.air(x + dx, y + dy, z + dz)) w.set(x + dx, y + dy, z + dz, "air");
            }
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) w.set(x + dx, y - 1, z + dz, "iron_block");
        w.set(x, y, z, "beacon");
        w.set(x, y + 1, z, "TEAM_stained_glass");
        for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            w.set(x + d[0], y + 1, z + d[1], "TEAM_wool");
            base.wools.add(new int[] {x + d[0], y + 1, z + d[1]});
        }
        // the fifth on a post behind
        int px = x - 2, pz = z;
        w.set(px, y + 1, pz, t.fence);
        w.set(px, y + 2, pz, "TEAM_wool");
        base.wools.add(new int[] {px, y + 2, pz});
        for (int[] c : new int[][] {{-3, -3}, {-3, 3}, {3, -3}, {3, 3}}) post(x + c[0], y + 1, z + c[1], 2);
    }

    // ---------------------------------------------------------------- the middle

    /** The hill: a round terrace with a ring of pillars and the beacon in the middle. Returns the hill corners. */
    public int[][] hill(int y) {
        int r = 8;
        for (int x = -r - 2; x <= r + 2; x++)
            for (int z = -r - 2; z <= r + 2; z++) {
                if (!w.canon(x, z)) continue;
                double d = Math.hypot(x, z);
                if (d > r + 1.5) continue;
                for (int dy = -4; dy <= 0; dy++) w.set(x, y + dy, z, t.wall.pick(w.r));
                w.set(x, y, z, d > r + 0.5 ? t.trim.pick(w.r) : d > 5.5 ? t.floor.pick(w.r) : ((x + z) & 1) == 0 ? t.trim.pick(w.r) : t.floor.pick(w.r));
                for (int dy = 1; dy <= 10; dy++) if (!w.air(x, y + dy, z)) w.set(x, y + dy, z, "air");
            }
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (w.canon(dx, dz)) w.set(dx, y - 2, dz, "iron_block");
        w.set(0, y - 1, 0, "beacon");
        w.set(0, y, 0, "white_stained_glass");
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4 + Math.PI / 8;
            int px = (int) Math.round(Math.cos(a) * (r + 0.5)), pz = (int) Math.round(Math.sin(a) * (r + 0.5));
            if (!w.canon(px, pz)) continue;
            for (int dy = 1; dy <= 5; dy++) w.set(px, y + dy, pz, dy == 1 || dy == 5 ? t.trim.pick(w.r) : t.log + "[axis=y]");
            w.set(px, y + 6, pz, light(false));
        }
        land.keeps.add(new Terrain.Keep(0, 0, r + 4));
        return new int[][] {{-4, y + 1, -4}, {4, y + 4, 4}};
    }

    // ---------------------------------------------------------------- bridges and props

    /** A sagging rope-and-plank bridge between two points (canonical), {@code width} planks wide. */
    public void ropeBridge(int x1, int y1, int z1, int x2, int y2, int z2) {
        int steps = (int) Math.ceil(Math.hypot(x2 - x1, z2 - z1));
        double nx = -(z2 - z1) / (double) steps, nz = (x2 - x1) / (double) steps;
        for (int s = 0; s <= steps; s++) {
            double k = s / (double) steps;
            double sag = Math.sin(k * Math.PI) * Math.min(3, steps / 12.0);
            double px = x1 + (x2 - x1) * k, pz = z1 + (z2 - z1) * k;
            int py = (int) Math.round(y1 + (y2 - y1) * k - sag);
            for (int o = -1; o <= 1; o++) {
                int bx = (int) Math.round(px + nx * o), bz = (int) Math.round(pz + nz * o);
                if (!w.canon(bx, bz)) continue;
                w.set(bx, py, bz, o == 0 ? t.planks : t.wood + "_slab[type=top]");
            }
            for (int o : new int[] {-2, 2}) {
                int bx = (int) Math.round(px + nx * o), bz = (int) Math.round(pz + nz * o);
                if (!w.canon(bx, bz)) continue;
                w.set(bx, py, bz, t.wood + "_slab[type=top]");
                w.set(bx, py + 1, bz, s % 4 == 0 ? t.fence : "iron_chain[axis=" + (Math.abs(x2 - x1) > Math.abs(z2 - z1) ? "x" : "z") + "]");
                if (s % 8 == 0) w.set(bx, py + 2, bz, light(false));
            }
        }
    }

    /** A pond sunk into the ground, edged with stones and reeds. */
    public void pond(int cx, int cz, double r) {
        int y = w.ground(cx, cz);
        if (y < 0) return;
        for (int dx = (int) -r - 2; dx <= r + 2; dx++)
            for (int dz = (int) -r - 2; dz <= r + 2; dz++) {
                int x = cx + dx, z = cz + dz;
                if (!w.canon(x, z)) continue;
                double d = Math.hypot(dx, dz) + w.n(x, z, 3) * 1.2;
                if (d > r + 1.5) continue;
                for (int dy = 1; dy <= 6; dy++) if (!w.air(x, y + dy, z)) w.set(x, y + dy, z, "air");
                if (d <= r) {
                    int deep = d < r - 2 ? 2 : 1;
                    for (int k = 0; k < deep; k++) w.set(x, y - k, z, t.liquid);
                    w.set(x, y - deep, z, t.liquid.equals("lava") ? "magma_block" : "clay");
                    if (t.liquid.equals("water") && w.r.nextInt(9) == 0) w.set(x, y + 1, z, "lily_pad");
                } else {
                    w.set(x, y, z, t.boulder.pick(w.r));
                    if (t.liquid.equals("water") && w.r.nextInt(3) == 0) {
                        w.set(x, y + 1, z, "sugar_cane");
                        w.set(x, y + 2, z, "sugar_cane");
                    }
                }
            }
        land.keeps.add(new Terrain.Keep(cx, cz, r + 2));
    }

    public void lamp(int x, int y, int z) {
        post(x, y, z, 3);
    }

    /** A small ruined watch post (cover on the islands). */
    public void ruin(int x, int y, int z) {
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) {
                boolean edge = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                if (!edge) continue;
                int h = (int) Math.round((w.n(x + dx, z + dz, 2.5) + 1) * 2.2);
                if (dx == 0 || dz == 0) h = Math.min(h, 0);
                for (int dy = 1; dy <= h; dy++) w.set(x + dx, y + dy, z + dz, t.wall.pick(w.r));
                if (h >= 3) w.set(x + dx, y + h + 1, z + dz, t.slabRoof + "[type=bottom]");
            }
        w.set(x, y + 1, z, "campfire[lit=false]");
        land.keeps.add(new Terrain.Keep(x, z, 5));
    }
}
