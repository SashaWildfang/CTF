package com.sasha.ctfbuilder.v2.maps;

import com.sasha.ctfbuilder.v2.Arena;
import com.sasha.ctfbuilder.v2.Kit;
import com.sasha.ctfbuilder.v2.Land;
import com.sasha.ctfbuilder.v2.Theme;
import com.sasha.ctfbuilder.v2.W.P;
import org.bukkit.World;

/**
 * Kingdoms: four castles on four hills round a market town, for four teams. Each castle has a curtain wall
 * with round towers, a gatehouse toward the town and a keep (the spawn); the flag stands in the bailey. A
 * road runs from every gate down into the town square (the hill). Between neighbouring kingdoms lie farms
 * round old watchtowers and patches of forest: the ways round the side to hit a neighbour while the others
 * fight in the town.
 */
public final class Kingdoms extends Arena {

    static final int Y = 68, CASTLE = 74, TOWN = 70, CX = -80;

    public Kingdoms(World world) {
        super(world, "kingdoms", true, Theme.get("kingdoms"));
        name = "Kingdoms";
        icon = "GOLDEN_HELMET";
        minutes = 18;
        kothPoints = 120;
        reach = 120;
        low = 15;
        high = 140;
        voidY = 15;
        spectator = new double[] {0.5, 112, 0.5, -90, 50};
    }

    double height(int x, int z) {
        // a rounded square of land, deeper notches between the castles
        double r = Math.pow(Math.pow(Math.abs(x), 3.2) + Math.pow(Math.abs(z), 3.2), 1 / 3.2);
        double edge = 108 + w.fbm(x * 0.5, z * 0.5, 14, 2) * 7 - 12 * Math.pow(Math.abs(Math.sin(2 * Math.atan2(z, x))), 6);
        if (r > edge) return Double.NaN;
        double h = Y + w.fbm(x + 300, z - 120, 22, 3) * 3;
        // the castle hill
        double c = Math.hypot(x - CX, z);
        if (c < 30) h = Math.max(h, CASTLE - Math.max(0, c - 21) * 0.7);
        // the town's low hill
        double t = Math.hypot(x, z);
        if (t < 26) h = Math.max(h, TOWN - Math.max(0, t - 18) * 0.25);
        if (r > edge - 3) h -= 1;
        return h;
    }

    String surface(int x, int z, int y, int steep, double edge) {
        double n = w.fbm(x + 913, z - 377, 10, 2);
        if (steep >= 3) return w.r.nextBoolean() ? "stone" : "cobblestone";
        if (n > 0.42) return "coarse_dirt";
        if (n < -0.45) return "podzol";
        return "grass_block";
    }

    @Override
    protected void build() {
        Land land = new Land(w, t, -116, 116, -116, 116);
        land.top = this::height;
        land.surface = this::surface;
        land.depth = 26;
        land.build();

        castle(CX, 0);
        road();
        town();
        farm(-56, -56);
        farm(-56, 56);
        // the farm towers' roofs (one cone, copied round to all four farms)
        int ty = w.ground(-56, -56);
        k.cone(-56, -56, w.top(-56, -56) + 1, 3, 1, 1.3, Kit.Roof.of("spruce_planks"));

        trees(new Theme.Tree[] {Theme.Tree.OAK, Theme.Tree.BIRCH, Theme.Tree.SPRUCE}, new int[] {4, 2, 1}, 0.008, 8, 3,
                (x, z) -> Math.hypot(x, z) > 30 && Math.hypot(x - CX, z) > 34);
        plants(P.of("short_grass", 10, "poppy", 1, "dandelion", 1, "cornflower", 1, "oxeye_daisy", 1, "fern", 2, "bush", 2), 0.2, (x, z) -> true);

        spawn = new double[] {CX - 9.5, CASTLE + 1, 0.5, -90};
        flag = new int[] {CX + 6, CASTLE + 1, 0};
        pads.add(new int[] {CX + 4, CASTLE + 1, -10});
        pads.add(new int[] {CX + 4, CASTLE + 1, 10});
        hills.add(new Hill("Market Square", -4, TOWN + 1, -4, 4, TOWN + 4, 4));
    }

    // ---------------------------------------------------------------- the castle

    void castle(int cx, int cz) {
        int y = CASTLE;
        int x0 = cx - 16, x1 = cx + 16, z0 = cz - 16, z1 = cz + 16;
        level(x0 - 3, z0 - 3, x1 + 3, z1 + 3, y, P.of("grass_block", 3, "coarse_dirt", 1), P.of("dirt"));
        // the bailey floor: worn paving near the gate and keep, grass elsewhere
        for (int x = x0 + 1; x < x1; x++) for (int z = z0 + 1; z < z1; z++) {
            boolean path = Math.abs(z - cz) <= 2 || x <= cx - 6;
            if (path) w.set(x, y, z, ((x * 3 + z) & 7) == 0 ? "mossy_cobblestone" : ((x + z) & 1) == 0 ? "cobblestone" : "stone_bricks");
        }
        // curtain walls: 8 high, a wall-walk, crenellations
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            boolean wall = x == x0 || x == x1 || z == z0 || z == z1;
            boolean walk = !wall && (x == x0 + 1 || x == x1 - 1 || z == z0 + 1 || z == z1 - 1);
            if (wall) {
                for (int dy = 1; dy <= 8; dy++) w.set(x, y + dy, z, dy <= 2 ? P.of("cobblestone", 2, "mossy_cobblestone", 1, "stone_bricks", 1).pick(w.r)
                        : P.of("stone_bricks", 6, "cracked_stone_bricks", 1, "mossy_stone_bricks", 1).pick(w.r));
                if (((x + z) & 1) == 0) w.set(x, y + 9, z, "stone_brick_wall");
            } else if (walk) {
                w.set(x, y + 7, z, "stone_brick_slab[type=top]");
            }
        }
        // round towers at the corners with cone roofs
        for (int[] c : new int[][] {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) roundTower(c[0], c[1], y, 4, 12);
        // the gatehouse on the east wall: two towers, an arch, a portcullis, the team's banners
        for (int s : new int[] {-4, 4}) roundTower(x1, cz + s, y, 3, 11);
        for (int dz = -1; dz <= 1; dz++) for (int dy = 1; dy <= 4; dy++) {
            w.set(x1, y + dy, cz + dz, "air");
            w.set(x1 - 1, y + dy, cz + dz, "air");
        }
        for (int dz = -1; dz <= 1; dz++) w.set(x1, y + 5, cz + dz, dz == 0 ? "chiseled_stone_bricks" : "stone_bricks");
        w.set(x1 + 1, y + 4, cz - 1, "stone_brick_stairs[facing=south,half=top]");
        w.set(x1 + 1, y + 4, cz + 1, "stone_brick_stairs[facing=north,half=top]");
        for (int dz = -1; dz <= 1; dz++) w.set(x1 - 1, y + 4, cz + dz, "iron_bars");
        for (int dz = -2; dz <= 2; dz++) for (int dy = 6; dy <= 8; dy++) w.set(x1 + 1, y + dy, cz + dz, dy == 8 ? "stone_brick_slab[type=bottom]" : "stone_bricks");
        w.set(x1 + 2, y + 7, cz, "TEAM_wall_banner[facing=east]");
        for (int s : new int[] {-2, 2}) w.set(x1 + 2, y + 6, cz + s, "TEAM_wall_banner[facing=east]");
        // the keep at the back: three storeys, crenellated, the great hall on the ground floor (the spawn)
        int kx0 = x0 + 2, kx1 = x0 + 12, kz0 = cz - 7, kz1 = cz + 7;
        k.walls(kx0, kz0, kx1, kz1, y, 15, Kit.Bands.of(0, P.of("cobblestone", 2, "stone_bricks", 2), 2,
                P.of("stone_bricks", 6, "cracked_stone_bricks", 1, "mossy_stone_bricks", 1), 15, "polished_andesite"), null, 0,
                P.of("spruce_planks", 3, "stripped_spruce_wood", 1));
        for (int x = kx0; x <= kx1; x++) for (int z = kz0; z <= kz1; z++) {
            boolean edge = x == kx0 || x == kx1 || z == kz0 || z == kz1;
            if (!edge) {
                w.set(x, y + 6, z, "spruce_planks");
                w.set(x, y + 11, z, "spruce_planks");
                w.set(x, y + 15, z, "stone_bricks");
            } else if (((x + z) & 1) == 0) {
                w.set(x, y + 16, z, "stone_brick_wall");
            }
        }
        // corner turrets on the keep
        for (int[] c : new int[][] {{kx0, kz0}, {kx1, kz0}, {kx0, kz1}, {kx1, kz1}}) {
            for (int dy = 15; dy <= 18; dy++) w.set(c[0], y + dy, c[1], "stone_bricks");
            w.set(c[0], y + 19, c[1], "stone_brick_slab[type=bottom]");
        }
        // keep windows: tall on the hall, small above
        for (int z = kz0 + 2; z <= kz1 - 2; z += 4) {
            for (int dy = 2; dy <= 4; dy++) w.set(kx1, y + dy, z, "glass_pane");
            w.set(kx1, y + 8, z, "glass_pane");
            w.set(kx1, y + 9, z, "glass_pane");
            w.set(kx1, y + 13, z, "glass_pane");
        }
        for (int x = kx0 + 2; x <= kx1 - 2; x += 4) for (int z : new int[] {kz0, kz1}) {
            for (int dy = 2; dy <= 4; dy++) w.set(x, y + dy, z, "glass_pane");
            w.set(x, y + 8, z, "glass_pane");
            w.set(x, y + 13, z, "glass_pane");
        }
        // the hall's door: a wide arch to the bailey, banners either side
        for (int dz = -1; dz <= 1; dz++) for (int dy = 1; dy <= 3; dy++) w.set(kx1, y + dy, cz + dz, "air");
        w.set(kx1, y + 4, cz, "chiseled_stone_bricks");
        w.set(kx1, y + 3, cz - 1, "stone_brick_stairs[facing=south,half=top]");
        w.set(kx1, y + 3, cz + 1, "stone_brick_stairs[facing=north,half=top]");
        for (int s : new int[] {-3, 3}) w.set(kx1 + 1, y + 4, cz + s, "TEAM_wall_banner[facing=east]");
        // inside the hall: a long table, the lord's table on a dais, a big fireplace with no fire in reach
        for (int x = kx0 + 3; x <= kx1 - 3; x++) {
            w.set(x, y + 1, cz - 3, "spruce_fence");
            w.set(x, y + 2, cz - 3, "spruce_pressure_plate");
            w.set(x, y + 1, cz - 4, "spruce_stairs[facing=south,half=bottom]");
            w.set(x, y + 1, cz + 3, "spruce_fence");
            w.set(x, y + 2, cz + 3, "spruce_pressure_plate");
            w.set(x, y + 1, cz + 4, "spruce_stairs[facing=north,half=bottom]");
        }
        for (int z = cz - 2; z <= cz + 2; z++) w.set(kx0 + 1, y + 1, z, "dark_oak_planks");
        w.set(kx0 + 1, y + 2, cz, "TEAM_carpet");
        for (int x = kx0 + 2; x <= kx1 - 1; x++) w.set(x, y + 1, cz, "TEAM_carpet");
        for (int x = kx0 + 3; x <= kx1 - 3; x += 4) w.hangLight(x, y + 5, cz, "lantern");
        for (int z = kz0 + 2; z <= kz1 - 2; z += 3) w.set(kx0 + 1, y + 4, z, "TEAM_wall_banner[facing=east]");
        // stairs up through the keep's floors in the north-west corner
        for (int i = 0; i < 5; i++) {
            w.set(kx0 + 1 + i, y + 1 + i, kz0 + 1, "stone_brick_stairs[facing=east,half=bottom]");
            for (int h = 1; h <= 3; h++) w.set(kx0 + 1 + i, y + 1 + i + h, kz0 + 1, "air");
        }
        for (int i = 0; i < 5; i++) {
            w.set(kx0 + 5 - i, y + 7 + i, kz0 + 2, "stone_brick_stairs[facing=west,half=bottom]");
            for (int h = 1; h <= 3; h++) w.set(kx0 + 5 - i, y + 7 + i + h, kz0 + 2, "air");
        }
        // lean-to stables along the north wall, a smithy along the south wall
        for (int x = cx - 2; x <= cx + 12; x++) {
            for (int z : new int[] {z0 + 1, z0 + 2, z0 + 3}) w.set(x, y + 4, z, "spruce_slab[type=bottom]");
            if ((x - cx) % 4 == 0) for (int dy = 1; dy <= 3; dy++) w.set(x, y + dy, z0 + 3, "spruce_log[axis=y]");
            if ((x - cx) % 4 == 2) w.set(x, y + 1, z0 + 1, "hay_block");
        }
        for (int x = cx - 2; x <= cx + 6; x++) {
            for (int z : new int[] {z1 - 1, z1 - 2, z1 - 3}) w.set(x, y + 4, z, "spruce_slab[type=bottom]");
            if ((x - cx) % 4 == 0) for (int dy = 1; dy <= 3; dy++) w.set(x, y + dy, z1 - 3, "spruce_log[axis=y]");
        }
        w.set(cx, y + 1, z1 - 1, "anvil[facing=east]");
        w.set(cx + 2, y + 1, z1 - 1, "smithing_table");
        w.set(cx + 4, y + 1, z1 - 1, "grindstone[face=floor,facing=north]");
        // the flag's circle in the bailey, a well beside it
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) if (Math.hypot(dx, dz) <= 2.5)
            w.set(cx + 6 + dx, y, cz + dz, Math.hypot(dx, dz) < 1 ? "chiseled_stone_bricks" : "polished_andesite");
        // stairs up onto the wall-walk by the gate
        for (int i = 0; i < 6; i++) {
            w.set(x1 - 2 - i, y + 1 + i, cz - 6, "stone_brick_stairs[facing=east,half=bottom]");
            for (int b = y + i; b > y; b--) w.set(x1 - 2 - i, b, cz - 6, "stone_bricks");
        }
        keepClear(x0 - 5, z0 - 5, x1 + 5, z1 + 5);
    }

    /** A round tower with arrow slits and a pointed roof, hollow with a ladder. */
    void roundTower(int cx, int cz, int y, int r, int h) {
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            double d = Math.hypot(dx, dz);
            if (d > r + 0.45) continue;
            k.footing(cx + dx, cz + dz, cx + dx, cz + dz, y, P.of("cobblestone", 2, "stone_bricks", 1));
            for (int dy = 1; dy <= h; dy++) {
                boolean shell = d > r - 0.75;
                w.set(cx + dx, y + dy, cz + dz, shell ? (dy <= 2 ? "cobblestone" : P.of("stone_bricks", 5, "mossy_stone_bricks", 1, "cracked_stone_bricks", 1).pick(w.r))
                        : dy == h ? "spruce_planks" : "air");
            }
            if (d > r - 0.75 && ((dx + dz) & 1) == 0) w.set(cx + dx, y + h + 1, cz + dz, "stone_brick_wall");
        }
        // slits
        for (int[] s : new int[][] {{r, 0}, {-r, 0}, {0, r}, {0, -r}}) w.set(cx + s[0], y + h - 3, cz + s[1], "air");
        k.cone(cx, cz, y + h + 2, r, 1, 1.4, Kit.Roof.of("deepslate_tiles"));
        int top = w.top(cx, cz);
        w.set(cx, top + 1, cz, "stripped_spruce_log[axis=y]");
        w.set(cx, top + 2, cz, "stripped_spruce_log[axis=y]");
        w.set(cx + 1, top + 2, cz, "TEAM_wall_banner[facing=east]");
    }

    // ---------------------------------------------------------------- the countryside

    /** The road from the gate down into the town, cobbles with a grass verge, lamp posts. */
    void road() {
        for (int x = CX + 19; x <= -18; x++) {
            int z0 = -2, z1 = 2;
            for (int z = z0; z <= z1; z++) {
                int gy = w.ground(x, z);
                if (gy < 50) continue;
                w.set(x, gy, z, Math.abs(z) == 2 ? "coarse_dirt" : ((x + z) & 3) == 0 ? "mossy_cobblestone" : ((x + z) & 1) == 0 ? "cobblestone" : "gravel");
                for (int h = 1; h <= 4; h++) if (!w.air(x, gy + h, z)) w.set(x, gy + h, z, "air");
            }
            if ((x - CX) % 12 == 0) {
                int gy = w.ground(x, 4);
                if (gy > 50) {
                    w.set(x, gy + 1, 4, "cobblestone_wall");
                    w.set(x, gy + 2, 4, "spruce_fence");
                    w.set(x, gy + 3, 4, "lantern[hanging=false]");
                }
            }
        }
        keepClear(CX + 19, -4, -18, 5);
    }

    /** A quarter of the market town: the square's paving, two timber houses facing it, a stall. */
    void town() {
        int y = TOWN;
        // the square, built a quarter at a time (rotated round with the rest)
        for (int x = -12; x <= 0; x++) for (int z = -12; z <= 12; z++) {
            if (!w.canon(x, z)) continue;
            double d = Math.max(Math.abs(x), Math.abs(z));
            int gy = w.ground(x, z);
            for (int yy = gy + 1; yy < y; yy++) c(x, yy, z, "dirt");
            for (int h = 1; h <= 8; h++) c(x, y + h, z, "air");
            c(x, y, z, d == 12 ? "stone_bricks" : Math.hypot(x, z) < 2.5 ? "chiseled_stone_bricks" : ((x + z) & 1) == 0 ? "cobblestone" : "stone_bricks");
        }
        // a stall at this quarter's corner of the square
        int sx = -9, sz = -9;
        // (the whole stall, not just this quarter's cells: its copies land on the other three corners)
        for (int[] p : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) for (int dy = 1; dy <= 3; dy++) w.set(sx + p[0], y + dy, sz + p[1], "spruce_fence");
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) w.set(sx + dx, y + 4, sz + dz, ((dx + dz) & 1) == 0 ? "TEAM_wool" : "white_wool");
        w.set(sx, y + 1, sz, "barrel[facing=up]");
        house(-22, -9, "east");
        house(-22, 9, "east");
        keepClear(-30, -16, 0, 16);
    }

    /** A two-storey timber-framed house with a jettied upper floor and a steep roof. */
    void house(int cx, int cz, String faces) {
        int y = w.ground(cx, cz);
        if (y < 50) return;
        int x0 = cx - 3, x1 = cx + 3, z0 = cz - 3, z1 = cz + 3;
        level(x0 - 1, z0 - 1, x1 + 1, z1 + 1, y, P.of("coarse_dirt", 1, "grass_block", 2), P.of("dirt"));
        k.walls(x0, z0, x1, z1, y, 4, Kit.Bands.of(0, "cobblestone", 1, "stone_bricks", 2, "white_terracotta"), "spruce_log", 3, P.of("spruce_planks"));
        // the upper floor overhangs by one on the street side
        int ux0 = x0, ux1 = x1 + 1;
        k.walls(ux0, z0, ux1, z1, y + 4, 4, Kit.Bands.of(0, "stripped_spruce_wood", 1, "white_terracotta", 4, "stripped_spruce_log[axis=x]"), "spruce_log", 3,
                P.of("spruce_planks"));
        for (int z = z0; z <= z1; z++) w.set(x1 + 1, y + 3, z, "spruce_stairs[facing=west,half=top]");
        k.gable(ux0, z0, ux1, z1, y + 9, false, 1, Kit.Roof.of("dark_oak_planks"), P.of("white_terracotta"));
        k.doorway(x1, y, cz, "east", 1, "spruce_door", null, null, null);
        k.window(x1, y + 2, cz - 2, "east", 1, 1, "glass_pane", null, "spruce_trapdoor");
        k.window(x1, y + 2, cz + 2, "east", 1, 1, "glass_pane", null, "spruce_trapdoor");
        k.window(ux1, y + 6, cz, "east", 1, 2, "glass_pane", "spruce_stairs", null);
        k.bed(x0 + 1, y + 5, z0 + 1, "west", "TEAM");
        w.set(x0 + 1, y + 1, z1 - 1, "crafting_table");
        w.set(x0 + 1, y + 1, z0 + 1, "barrel[facing=up]");
        // a ladder to the upper floor
        for (int dy = 1; dy <= 4; dy++) w.set(x0 + 1, y + dy, cz, "ladder[facing=east]");
        w.hangLight(cx, y + 3, cz, "lantern");
        keepClear(x0 - 2, z0 - 2, x1 + 2, z1 + 2);
    }

    /**
     * A farm between two kingdoms: a round stone watchtower with a cone roof in the middle of fields cut by
     * a cross of paths, haystacks. Round and symmetric, so the two kingdoms' halves of it match.
     */
    void farm(int fx, int fz) {
        int y = w.ground(fx, fz);
        if (y < 50) return;
        for (int dx = -14; dx <= 14; dx++) for (int dz = -14; dz <= 14; dz++) {
            int x = fx + dx, z = fz + dz;
            if (!w.canon(x, z)) continue;
            double d = Math.hypot(dx, dz);
            if (d > 13.5) continue;
            int gy = w.ground(x, z);
            if (gy < 50) continue;
            boolean path = Math.abs(dx) <= 1 || Math.abs(dz) <= 1;
            boolean ditch = !path && (Math.abs(dx) == 6 || Math.abs(dz) == 6);
            for (int h = 1; h <= 6; h++) if (!w.air(x, gy + h, z)) c(x, gy + h, z, "air");
            if (d < 5) { c(x, gy, z, "coarse_dirt"); continue; }
            if (path) c(x, gy, z, "dirt_path");
            else if (ditch) c(x, gy, z, "water");
            else {
                c(x, gy, z, "farmland[moisture=7]");
                String crop = (Math.floorMod(dx, 4) < 2) ? "wheat[age=7]" : Math.abs(dz) > 9 ? "carrots[age=7]" : "potatoes[age=7]";
                c(x, gy + 1, z, crop);
            }
        }
        // the watchtower
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            double d = Math.hypot(dx, dz);
            if (d > 3.45) continue;
            int x = fx + dx, z = fz + dz;
            if (!w.canon(x, z)) continue;
            for (int dy = 1; dy <= 12; dy++) c(x, y + dy, z, d > 2.6 ? (dy <= 2 ? "cobblestone" : (dy % 5 == 0 ? "polished_andesite" : "stone_bricks")) : dy == 12 ? "spruce_planks" : "air");
        }
        keepClear(fx - 15, fz - 15, fx + 15, fz + 15);
    }
}
