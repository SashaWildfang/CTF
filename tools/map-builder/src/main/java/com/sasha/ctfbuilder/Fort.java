package com.sasha.ctfbuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * A team fortress, built in local coordinates (u = forward toward the gate, v = to the right) and rotated
 * to face any axis direction. Records where the spawn, flag and wool ended up for map.yml.
 */
final class Fort {

    final B b;
    final int cx, cz, y;      // centre and floor level (the courtyard surface is at y)
    final int fx, fz;         // forward direction (unit along x or z)
    final int depth, width;   // half sizes along u and v
    final String team;        // red, blue, lime, yellow (block colour names)
    final B.Pal stone, cap, floor, trim;
    final String roof;

    double[] spawn;           // x y z yaw
    int[] flag;
    final List<int[]> wools = new ArrayList<>();

    Fort(B b, int cx, int cz, int y, int fx, int fz, int depth, int width, String team, B.Pal stone, B.Pal cap, B.Pal floor, B.Pal trim, String roof) {
        this.b = b;
        this.cx = cx;
        this.cz = cz;
        this.y = y;
        this.fx = fx;
        this.fz = fz;
        this.depth = depth;
        this.width = width;
        this.team = team;
        this.stone = stone;
        this.cap = cap;
        this.floor = floor;
        this.trim = trim;
        this.roof = roof;
    }

    // local → world
    int X(int u, int v) { return cx + u * fx + v * -fz; }
    int Z(int u, int v) { return cz + u * fz + v * fx; }

    void set(int u, int dy, int v, B.Pal pal) { b.set(X(u, v), y + dy, Z(u, v), pal); }
    void set(int u, int dy, int v, String spec) { b.set(X(u, v), y + dy, Z(u, v), spec); }

    void fill(int u1, int dy1, int v1, int u2, int dy2, int v2, B.Pal pal) {
        for (int u = Math.min(u1, u2); u <= Math.max(u1, u2); u++)
            for (int dy = Math.min(dy1, dy2); dy <= Math.max(dy1, dy2); dy++)
                for (int v = Math.min(v1, v2); v <= Math.max(v1, v2); v++) set(u, dy, v, pal);
    }

    void fill(int u1, int dy1, int v1, int u2, int dy2, int v2, String spec) {
        fill(u1, dy1, v1, u2, dy2, v2, B.Pal.of(spec));
    }

    /** Yaw that looks along +u (toward the gate). */
    float forwardYaw() {
        if (fx == 1) return -90;
        if (fx == -1) return 90;
        if (fz == 1) return 0;
        return 180;
    }

    /** Facing name of +u / -u / +v for wall banners and stairs. */
    String face(int du, int dv) {
        int wx = du * fx + dv * -fz, wz = du * fz + dv * fx;
        if (wx == 1) return "east";
        if (wx == -1) return "west";
        if (wz == 1) return "south";
        return "north";
    }

    void build() {
        int D = depth, W = width;
        int wallH = 7;
        // foundation and courtyard
        fill(-D, -6, -W, D, -1, W, stone);
        for (int u = -D; u <= D; u++)
            for (int v = -W; v <= W; v++) set(u, 0, v, floor);
        fill(-D + 1, 1, -W + 1, D - 1, 14, W - 1, "air");

        // outer walls with battlements (2 thick)
        for (int u = -D; u <= D; u++) {
            column(u, -W, wallH);
            column(u, -W + 1, wallH);
            column(u, W, wallH);
            column(u, W - 1, wallH);
        }
        for (int v = -W; v <= W; v++) {
            column(-D, v, wallH);
            column(-D + 1, v, wallH);
            column(D, v, wallH);
            column(D - 1, v, wallH);
        }
        crenels(wallH);

        // gate facing forward: 5 wide, 5 high, with a gatehouse arch
        fill(D - 1, 1, -2, D, 5, 2, "air");
        for (int v = -3; v <= 3; v++) {
            set(D + 1, 6, v, cap);
            set(D, 6, v, trim);
        }
        set(D, 5, -2, trim);
        set(D, 5, 2, trim);
        for (int v : new int[] {-4, 4}) {
            fill(D - 1, 1, v, D + 1, 9, v, stone);
            set(D + 1, 10, v, stone);
        }
        b.wallBanner(X(D + 1, -3), y + 5, Z(D + 1, -3), team, face(1, 0));
        b.wallBanner(X(D + 1, 3), y + 5, Z(D + 1, 3), team, face(1, 0));
        // portcullis teeth
        for (int v = -2; v <= 2; v++) set(D, 5, v, "iron_bars");

        // corner towers
        int[][] corners = {{-D, -W}, {-D, W}, {D, -W}, {D, W}};
        for (int[] c : corners) b.tower(X(c[0], c[1]), Z(c[0], c[1]), y, 4, 12, stone, floor, B.roof(team), false);

        // wool shrines: the two gate-side corner towers get a doorway to the courtyard and a wool on a pedestal
        for (int side : new int[] {-1, 1}) {
            int tu = D, tv = side * W;
            int doorU = D - 3, doorV = side * (W - 3);
            // doorway from the courtyard into the tower
            for (int dy = 1; dy <= 3; dy++) {
                set(doorU, dy, side * (W - 2), "air");
                set(doorU, dy, side * (W - 3), "air");
                set(D - 2, dy, side * (W - 3), "air");
                set(D - 3, dy, side * (W - 4), "air");
            }
            set(tu, 1, tv, trim);
            set(tu, 2, tv, team + "_wool");
            wools.add(new int[] {X(tu, tv), y + 2, Z(tu, tv)});
            set(tu, 4, tv, "lantern[hanging=true]");
        }

        // the keep at the back: two floors, door toward the courtyard
        int ku1 = -D + 2, ku2 = -D + 11, kv = Math.min(W - 4, 8);
        fill(ku1, 1, -kv, ku2, 10, kv, stone);
        fill(ku1 + 1, 1, -kv + 1, ku2 - 1, 4, kv - 1, "air");
        fill(ku1 + 1, 6, -kv + 1, ku2 - 1, 9, kv - 1, "air");
        fill(ku1 + 1, 5, -kv + 1, ku2 - 1, 5, kv - 1, "spruce_planks");
        fill(ku1 + 1, 0, -kv + 1, ku2 - 1, 0, kv - 1, B.Pal.of("polished_andesite", 3, "andesite", 1));
        // windows
        for (int v = -kv + 2; v <= kv - 2; v += 3) {
            set(ku2, 3, v, "glass_pane");
            set(ku2, 7, v, "glass_pane");
            set(ku1, 7, v, "glass_pane");
        }
        // door
        fill(ku2, 1, -1, ku2, 3, 1, "air");
        set(ku2, 4, 0, trim);
        // roof: stepped gable in team colour
        for (int i = 0; i <= kv + 1; i++) {
            fill(ku1 - 1, 11 + Math.min(i, kv + 1) / 2, -kv - 1 + i, ku2 + 1, 11 + Math.min(i, kv + 1) / 2, -kv - 1 + i, B.roof(team));
            fill(ku1 - 1, 11 + Math.min(i, kv + 1) / 2, kv + 1 - i, ku2 + 1, 11 + Math.min(i, kv + 1) / 2, kv + 1 - i, B.roof(team));
        }
        // inside the keep: stairs to the upper floor, banners, chests, lighting
        for (int i = 0; i < 4; i++) {
            set(ku1 + 1 + i, 1 + i, kv - 1, "spruce_planks");
            set(ku1 + 1 + i, 5, kv - 1, "air");
            set(ku1 + 1 + i, 5, kv - 2, i == 3 ? "air" : "spruce_planks");
        }
        set(ku1 + 1, 1, -kv + 1, "barrel[facing=up]");
        set(ku1 + 2, 1, -kv + 1, "barrel[facing=up]");
        set(ku1 + 1, 1, -kv + 2, "smithing_table");
        set(ku1 + 1, 2, -kv + 1, "lantern");
        set(ku2 - 1, 4, 0, "lantern[hanging=true]");
        set(ku1 + 4, 4, 0, "lantern[hanging=true]");
        set(ku1 + 4, 9, 0, "lantern[hanging=true]");
        b.wallBanner(X(ku1 + 1, 0), y + 3, Z(ku1 + 1, 0), team, face(1, 0));
        // carpet runner from the door
        for (int u = ku1 + 2; u <= ku2 - 1; u++) set(u, 1, 0, team + "_carpet");
        spawn = new double[] {X(ku1 + 3, 0) + 0.5, y + 1, Z(ku1 + 3, 0) + 0.5, forwardYaw()};

        // flag dais in the middle of the courtyard
        int du = Math.max(ku2 + 4, -1);
        for (int u = du - 2; u <= du + 2; u++)
            for (int v = -2; v <= 2; v++) set(u, 0, v, Math.abs(u - du) == 2 || Math.abs(v) == 2 ? trim : B.Pal.of("polished_deepslate"));
        set(du, 0, 0, "chiseled_stone_bricks");
        for (int[] corner : new int[][] {{-2, -2}, {-2, 2}, {2, -2}, {2, 2}}) {
            set(du + corner[0], 1, corner[1], "stone_brick_wall");
            set(du + corner[0], 2, corner[1], "lantern");
        }
        flag = new int[] {X(du, 0), y + 1, Z(du, 0)};

        // courtyard details: weapon racks, hay, barrels, a training dummy area and lanterns
        for (int v : new int[] {-W + 3, W - 3}) {
            set(0, 1, v, "hay_block");
            set(1, 1, v, "barrel[facing=up]");
            set(-1, 1, v, "composter");
            set(2, 1, v, "grindstone[face=floor,facing=north]");
        }
        for (int u = -D + 14; u <= D - 4; u += 6) {
            for (int v : new int[] {-W + 2, W - 2}) {
                set(u, 4, v, "lantern[hanging=true]");
            }
        }
        // inner wall-walk ladders to the battlements
        for (int dy = 1; dy <= wallH; dy++) {
            set(D - 2, dy, -W + 2, "ladder[facing=" + face(-1, 0) + "]");
            set(D - 2, dy, W - 2, "ladder[facing=" + face(-1, 0) + "]");
        }
        // team banners along the inner walls
        for (int u = -D + 14; u <= D - 4; u += 6) {
            b.wallBanner(X(u, -W + 2), y + 5, Z(u, -W + 2), team, face(0, 1));
            b.wallBanner(X(u, W - 2), y + 5, Z(u, W - 2), team, face(0, -1));
        }
    }

    private void column(int u, int v, int height) {
        for (int dy = 1; dy <= height; dy++) set(u, dy, v, dy == height ? cap : stone);
    }

    private void crenels(int wallH) {
        for (int u = -depth; u <= depth; u++)
            for (int v : new int[] {-width, width}) if (((u + v) & 1) == 0) set(u, wallH + 1, v, stone);
        for (int v = -width; v <= width; v++)
            for (int u : new int[] {-depth, depth}) if (((u + v) & 1) == 0) set(u, wallH + 1, v, stone);
    }
}
