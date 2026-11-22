package com.sasha.ctfbuilder.v2;

import com.sasha.ctfbuilder.v2.W.P;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiPredicate;

/**
 * One arena, fourth edition: each map is its own class with its own landform and layout. Everything is built
 * for the red team's part of the map (x < 0 with 2 teams, the west quarter with 4); the writer copies it round.
 * Subclasses fill in the spawn, flag, wool pads and hills, and the map file is written from those.
 */
public abstract class Arena {

    public record Hill(String name, int x0, int y0, int z0, int x1, int y1, int z1) {}

    protected final World world;
    protected final String id;
    protected final boolean four;
    protected final W w;
    protected final Kit k;
    protected final Theme t;
    protected final Flora flora;

    // what the map file needs (red's, rotated for the others)
    protected String name, icon = "FILLED_MAP";
    protected int minutes = 15, kothPoints = 100, captureSeconds = 8, captureLimit = 3, woolHeight = 5;
    protected double[] spawn;                          // x, y, z, yaw
    protected int[] flag;                              // x, y, z
    protected final List<int[]> pads = new ArrayList<>();
    protected final List<Hill> hills = new ArrayList<>(); // absolute, not rotated
    protected double[] spectator = {0.5, 100, 0.5, 0, 50};
    protected int reach = 120, low = 0, high = 160;
    protected int voidY = 0;

    /** Areas kept free of trees and plants (buildings, paths, spawns): {x0, z0, x1, z1}. */
    protected final List<int[]> clear = new ArrayList<>();

    protected Arena(World world, String id, boolean four, Theme theme) {
        this.world = world;
        this.id = id;
        this.four = four;
        this.w = new W(world, id.hashCode() * 7919L + 13, four ? 4 : 2);
        this.k = new Kit(w);
        this.t = theme;
        this.flora = new Flora(w, theme, new Terrain(w, theme));
        this.name = theme.name;
    }

    protected abstract void build();

    public final String run() {
        int chunks = reach / 16 + 2;
        for (int cx = -chunks; cx <= chunks; cx++) for (int cz = -chunks; cz <= chunks; cz++) world.getChunkAt(cx, cz).load(true);
        build();
        w.joinAll();
        Lint.run(world, -reach, reach, -reach, reach, Math.max(world.getMinHeight(), low), Math.min(world.getMaxHeight() - 1, high), 60);
        return yml();
    }

    // ---------------------------------------------------------------- helpers

    /** Sets a block only in the built part of the map (for things that cross the middle). */
    protected void c(int x, int y, int z, String spec) {
        if (w.canon(x, z)) w.set(x, y, z, spec);
    }

    protected void keepClear(int x0, int z0, int x1, int z1) {
        clear.add(new int[] {Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1)});
    }

    protected boolean isClear(int x, int z, int margin) {
        for (int[] r : clear) if (x >= r[0] - margin && x <= r[2] + margin && z >= r[1] - margin && z <= r[3] + margin) return true;
        return false;
    }

    protected static boolean soil(Material m) {
        return Flora.SOIL.contains(m.name()) || m == Material.SAND || m == Material.RED_SAND || m == Material.SNOW_BLOCK;
    }

    /** Scatters trees over the built part where {@code where} allows, at least {@code spacing} apart. */
    protected void trees(Theme.Tree[] kinds, int[] weights, double density, int spacing, int margin, BiPredicate<Integer, Integer> where) {
        List<int[]> placed = new ArrayList<>();
        int total = 0;
        for (int wgt : weights) total += wgt;
        for (int x = -reach; x <= reach; x++)
            for (int z = -reach; z <= reach; z++) {
                if (!w.canon(x, z) || !where.test(x, z) || w.r.nextDouble() > density) continue;
                if (isClear(x, z, margin)) continue;
                boolean crowded = false;
                for (int[] p : placed) if (Math.abs(p[0] - x) < spacing && Math.abs(p[1] - z) < spacing) { crowded = true; break; }
                if (crowded) continue;
                int gy = w.ground(x, z);
                if (gy <= world.getMinHeight() || !soil(w.type(x, gy, z)) || !w.free(x, gy + 1, z)) continue;
                int roll = w.r.nextInt(total);
                Theme.Tree kind = kinds[0];
                for (int i = 0; i < kinds.length; i++) {
                    roll -= weights[i];
                    if (roll < 0) { kind = kinds[i]; break; }
                }
                flora.tree(kind, x, gy + 1, z);
                placed.add(new int[] {x, z});
            }
    }

    /** Ground plants (grass, flowers...) on soil, where allowed. */
    protected void plants(P pool, double density, BiPredicate<Integer, Integer> where) {
        for (int x = -reach; x <= reach; x++)
            for (int z = -reach; z <= reach; z++) {
                if (!w.canon(x, z) || w.r.nextDouble() > density || !where.test(x, z) || isClear(x, z, 0)) continue;
                int gy = w.ground(x, z);
                if (gy <= world.getMinHeight() || !w.air(x, gy + 1, z)) continue;
                String spec = pool.pick(w.r);
                if (!flora.supports(spec, w.type(x, gy, z), x, gy, z)) continue;
                flora.plant(x, gy + 1, z, spec);
            }
    }

    /** Levels a rectangle of ground to y (fills under, clears 8 above), with the given top. */
    protected void level(int x0, int z0, int x1, int z1, int y, P top, P fill) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                for (int yy = y + 1; yy <= y + 8; yy++) if (!w.air(x, yy, z)) w.set(x, yy, z, "air");
                w.set(x, y, z, top.pick(w.r));
                for (int yy = y - 1; yy > y - 12 && !w.type(x, yy, z).isSolid(); yy--) w.set(x, yy, z, fill.pick(w.r));
            }
    }

    // ---------------------------------------------------------------- the map file

    String yml() {
        StringBuilder y = new StringBuilder();
        y.append("name: ").append(name).append('\n');
        y.append("authors: [Fractal Build Team]\n");
        y.append("icon: ").append(icon).append('\n');
        y.append("modes: ").append(four ? "[ctf, ctf4, koth, dtw]" : "[ctf, koth, dtw]").append('\n');
        y.append("time-limit-minutes: ").append(minutes).append('\n');
        y.append("capture-limit: ").append(captureLimit).append('\n');
        y.append("koth-points: ").append(kothPoints).append('\n');
        y.append("koth-capture-seconds: ").append(captureSeconds).append('\n');
        y.append("wool-height: ").append(woolHeight).append('\n');
        y.append("void-y: ").append(voidY).append('\n');
        y.append(String.format(Locale.ROOT, "spectator-spawn: \"%.1f %.0f %.1f %.0f %.0f\"%n", spectator[0], spectator[1], spectator[2], spectator[3], spectator[4]));
        if (!hills.isEmpty()) {
            Hill first = hills.get(0);
            y.append("hill:\n");
            y.append(String.format(Locale.ROOT, "  corner-1: \"%d %d %d\"%n  corner-2: \"%d %d %d\"%n", first.x0, first.y0, first.z0, first.x1, first.y1, first.z1));
            if (hills.size() > 1) {
                y.append("hills:\n");
                for (Hill h : hills) {
                    y.append(String.format(Locale.ROOT, "  - name: \"%s\"%n    corner-1: \"%d %d %d\"%n    corner-2: \"%d %d %d\"%n",
                            h.name, h.x0, h.y0, h.z0, h.x1, h.y1, h.z1));
                }
            }
        }
        y.append("teams:\n");
        String[] names = four ? new String[] {"red", "green", "blue", "yellow"} : new String[] {"red", "blue"};
        for (int i = 0; i < names.length; i++) {
            int turns = four ? i : i * 2;
            double[] sp = Maps.rotate(spawn[0], spawn[2], turns);
            float yaw = (float) (spawn[3] + 90 * turns);
            while (yaw > 180) yaw -= 360;
            int[] f = Maps.rotateBlock(flag[0], flag[2], turns);
            y.append("  ").append(names[i]).append(":\n");
            y.append(String.format(Locale.ROOT, "    spawn: \"%.1f %d %.1f %.0f 0\"%n", sp[0], (int) spawn[1], sp[1], yaw));
            y.append(String.format(Locale.ROOT, "    flag: \"%d %d %d\"%n", f[0], flag[1], f[1]));
            List<String> list = new ArrayList<>();
            for (int[] pad : pads) {
                int[] q = Maps.rotateBlock(pad[0], pad[2], turns);
                list.add("\"" + q[0] + " " + pad[1] + " " + q[1] + "\"");
            }
            y.append("    monuments: [").append(String.join(", ", list)).append("]\n");
            y.append("    wools: []\n");
        }
        return y.toString();
    }
}
