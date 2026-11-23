"""
terrain_and_bridges.py: the land and what joins it.

Sculpted void islands whose undersides taper in one curve to stalactite points, faceted floating shards, arched and hanging
bridges, giant anchor chains, waterfalls, and trees grown from splines (flared trunk, root buttresses, branches,
layered drooping leaf clusters, vines, glow berries, lanterns tucked under the boughs).
"""
import math
import random

from world_writer import DIRS, OPPOSITE, make_state, parse_state

TAU = math.pi * 2


# ---------------------------------------------------------------------------------------------------- noise

def _hash(ix, iz, seed):
    n = (ix * 374761393 + iz * 668265263 + seed * 2246822519) & 0xffffffff
    n = ((n ^ (n >> 13)) * 1274126177) & 0xffffffff
    return ((n ^ (n >> 16)) & 0xffff) / 65535.0


def rnd3(x, y, z, seed=0):
    """A fixed random number in 0..1 for a block position."""
    return _hash(x * 73 + y * 179, z * 283 - y * 31, seed)


def vnoise(x, z, seed=0):
    ix, iz = math.floor(x), math.floor(z)
    fx, fz = x - ix, z - iz
    fx, fz = fx * fx * (3 - 2 * fx), fz * fz * (3 - 2 * fz)
    a, b = _hash(ix, iz, seed), _hash(ix + 1, iz, seed)
    c, d = _hash(ix, iz + 1, seed), _hash(ix + 1, iz + 1, seed)
    return (a + (b - a) * fx) * (1 - fz) + (c + (d - c) * fx) * fz


def fbm(x, z, scale, seed=0, octaves=3):
    """Smooth noise in -1..1 with features about `scale` blocks across."""
    total, amp, norm = 0.0, 1.0, 0.0
    for o in range(octaves):
        total += (vnoise(x / scale, z / scale, seed + o * 31) * 2 - 1) * amp
        norm += amp
        amp *= 0.5
        scale *= 0.5
    return total / norm


# ---------------------------------------------------------------------------------------------------- placing

_TURN = ['north', 'east', 'south', 'west']


class Xf:
    """
    A local frame on the world: optionally mirrored east-west, turned by quarter turns, then moved. Blocks set
    through it land in the right place with their directions (facing, axis, sides, banner rotation) turned to match,
    so a building is written once and stood wherever it is wanted.
    """

    def __init__(self, world, ox=0, oz=0, rot=0, mirror=False):
        self.w, self.ox, self.oz, self.rot, self.mirror = world, ox, oz, rot % 4, mirror
        self._cache = {}

    def at(self, x, z):
        if self.mirror: x = -x
        for _ in range(self.rot): x, z = -z, x
        return self.ox + x, self.oz + z

    def child(self, x, z, rot=0):
        ox, oz = self.at(x, z)
        return Xf(self.w, ox, oz, self.rot - rot if self.mirror else self.rot + rot, self.mirror)

    def direction(self, d):
        if self.mirror and d in ('east', 'west'): d = OPPOSITE[d]
        return _TURN[(_TURN.index(d) + self.rot) % 4]

    def state(self, s):
        if '[' not in s or not (self.rot or self.mirror): return s
        t = self._cache.get(s)
        if t is None:
            name, props = parse_state(s)
            out = {}
            for k, v in props.items():
                if k in DIRS: k = self.direction(k)
                if v in DIRS: v = self.direction(v)
                elif k == 'axis' and v in ('x', 'z') and self.rot % 2: v = 'z' if v == 'x' else 'x'
                elif k == 'rotation':
                    r = int(v)
                    v = str(((16 - r) % 16 if self.mirror else r) + 4 * self.rot & 15)
                elif k == 'hinge' and self.mirror: v = 'left' if v == 'right' else 'right'
                out[k] = v
            t = self._cache[s] = make_state(name, out)
        return t

    def set(self, x, y, z, s):
        wx, wz = self.at(x, z)
        self.w.set(wx, y, wz, self.state(s))

    def setif(self, x, y, z, s):
        wx, wz = self.at(x, z)
        self.w.setif(wx, y, wz, self.state(s))

    def get(self, x, y, z):
        wx, wz = self.at(x, z)
        return self.w.get(wx, y, wz)

    def air(self, x, y, z):
        wx, wz = self.at(x, z)
        return self.w.air(wx, y, wz)

    def fill(self, x0, y0, z0, x1, y1, z1, s):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, s)

    def spot(self, x, y, z, yaw=0.0):
        """A local standing place (block x, z; its middle) and a local yaw as world x, y, z, yaw."""
        wx, wz = self.at(x, z)
        if self.mirror: yaw = -yaw
        return wx + 0.5, y, wz + 0.5, (yaw + 90 * self.rot + 180) % 360 - 180


def family(base):
    if base.endswith('_bricks') or base.endswith('_tiles'): return base[:-1]
    if base.endswith('_planks'): return base[:-7]
    return base


def stairs(base, facing, top=False):
    return '%s_stairs[facing=%s,half=%s]' % (family(base), facing, 'top' if top else 'bottom')


def slab(base, top=False):
    return '%s_slab[type=%s]' % (family(base), 'top' if top else 'bottom')


def wall(base):
    return family(base) + '_wall'


def ball(w, p, r, state, only_air=False):
    put = w.setif if only_air else w.set
    cx, cy, cz = p
    if r < 0.75:
        put(int(math.floor(cx)), int(math.floor(cy)), int(math.floor(cz)), state)
        return
    for x in range(int(math.floor(cx - r)), int(math.floor(cx + r)) + 1):
        for y in range(int(math.floor(cy - r)), int(math.floor(cy + r)) + 1):
            for z in range(int(math.floor(cz - r)), int(math.floor(cz + r)) + 1):
                if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 + (z + 0.5 - cz) ** 2 <= r * r: put(x, y, z, state)


def bezier(p0, p1, p2, p3, n):
    """n + 1 points along a cubic curve."""
    out = []
    for i in range(n + 1):
        t = i / n
        u = 1 - t
        out.append(tuple(u * u * u * a + 3 * u * u * t * b + 3 * u * t * t * c + t * t * t * d for a, b, c, d in zip(p0, p1, p2, p3)))
    return out


def tube(w, pts, r0, r1, state, only_air=False):
    for i, p in enumerate(pts):
        t = i / max(1, len(pts) - 1)
        ball(w, p, r0 + (r1 - r0) * t, state, only_air)


# ---------------------------------------------------------------------------------------------------- islands

STRATA = [(3, 'stone'), (5, 'andesite'), (8, 'stone'), (10, 'tuff'), (13, 'stone'), (16, 'deepslate'), (18, 'tuff'),
          (26, 'deepslate'), (999, 'blackstone')]


def strata(x, y, z, top, seed):
    """Rock in warped beds: pale stone under the paving, darker with depth, cobbled where it has broken."""
    v = (top - y) + fbm(x, z, 11, seed + 5) * 2.2 + (vnoise(x * 0.45 + y * 0.3, z * 0.45 - y * 0.3, seed + 9) - 0.5) * 2.4
    name = 'blackstone'
    for limit, rock in STRATA:
        if v < limit:
            name = rock
            break
    if vnoise(x * 0.6 + y * 0.8, z * 0.6 - y * 0.5, seed + 17) > 0.74:
        name = {'stone': 'cobblestone', 'deepslate': 'cobbled_deepslate', 'andesite': 'stone'}.get(name, name)
    return name


def star(cx, cz, r, lobes, amp, phase=0.0, seed=0, rough=1.0):
    def inside(x, z):
        dx, dz = x - cx, z - cz
        return r + amp * math.cos(lobes * math.atan2(dz, dx) + phase) - math.hypot(dx, dz) + fbm(x, z, 6, seed) * rough
    return inside


def oval(cx, cz, rx, rz, power=2.4, seed=0, rough=1.4):
    def inside(x, z):
        v = (abs((x - cx) / rx) ** power + abs((z - cz) / rz) ** power) ** (1 / power)
        return (1 - v) * min(rx, rz) + fbm(x, z, 6, seed) * rough
    return inside


def box(x0, z0, x1, z1):
    return lambda x, z: min(x - x0, x1 - x, z - z0, z1 - z) + 1


def union(*shapes):
    return lambda x, z: max(f(x, z) for f in shapes)


def island(w, inside, bounds, top, depth, reach, seed, cap='stone_bricks'):
    """
    A floating island. `inside(x, z)` says how far a column is inside the outline (blocks, above 0 inside). The top
    is flat at `top`. Underneath, a thin overhanging lip at the rim deepens in one smooth curve to a keel `depth`
    blocks down under the island's heart; great stalactites grow out of that curve and narrow to single blocks,
    smaller needles hang between them, and dripstone finishes every point. `reach` is how far in from the rim the
    lip stays thin. Returns {(x, z): inside}.
    """
    x0, z0, x1, z1 = bounds
    cells = {}
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            e = inside(x, z)
            if e > 0: cells[(x, z)] = e
    if not cells: return cells
    rng = random.Random(seed)
    inner = max(cells.values())
    deep = sorted((c for c, e in cells.items() if e > inner * 0.3), key=lambda c: (c[0] * 7919 + c[1] * 104729 + seed * 31) % 9973)
    fangs = [(c[0] + 0.5, c[1] + 0.5, rng.uniform(0.2, 0.3) * inner + 1.8, depth * rng.uniform(0.22, 0.45))
             for c in deep[:len(cells) // 420]]
    heart = max(cells, key=cells.get)
    fangs.append((heart[0] + 0.5, heart[1] + 0.5, inner * 0.55 + 1.5, depth * 0.5))           # the keel's own point
    needles = {}
    for gx in range(x0 // 6 - 1, x1 // 6 + 2):
        for gz in range(z0 // 6 - 1, z1 // 6 + 2):
            if rng.random() < 0.55:
                needles[(gx, gz)] = (gx * 6 + rng.uniform(0, 6), gz * 6 + rng.uniform(0, 6), rng.uniform(1.4, 2.4), rng.uniform(2, 6))
    bottoms = {}
    for (x, z), e in cells.items():
        t = min(1.0, e / inner)
        lip = min(1.0, e / max(1.0, reach * 0.35))
        d = 2.2 + depth * t ** 1.2 * lip + fbm(x, z, 5, seed) * 1.3 * min(1.0, e / 3)
        for fx, fz, fr, fh in fangs:
            dd = math.hypot(x + 0.5 - fx, z + 0.5 - fz)
            if dd < fr: d += fh * (1 - dd / fr) ** 2.3 * min(1.0, e / 4)
        gx, gz = x // 6, z // 6
        for ix in (-1, 0, 1):
            for iz in (-1, 0, 1):
                s = needles.get((gx + ix, gz + iz))
                if s:
                    dd = math.hypot(x + 0.5 - s[0], z + 0.5 - s[1])
                    if dd < s[2]: d += s[3] * (1 - dd / s[2]) ** 2.0 * min(1.0, e / 5)
        bottom = top - int(d)
        bottoms[(x, z)] = bottom
        for y in range(bottom, top): w.set(x, y, z, strata(x, y, z, top, seed))
        w.set(x, top, z, cap(x, z, e) if callable(cap) else cap)
    for (x, z), bottom in bottoms.items():                              # dripstone on every point
        near = [bottoms.get((x + dx, z + dz), top) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))]
        if all(n > bottom for n in near) and top - bottom > 5:
            w.set(x, bottom - 1, z, 'pointed_dripstone[thickness=frustum,vertical_direction=down]')
            w.set(x, bottom - 2, z, 'pointed_dripstone[thickness=tip,vertical_direction=down]')
        elif top - bottom > 7 and sum(n > bottom for n in near) >= 2 and rnd3(x, 0, z, seed) < 0.3:
            w.set(x, bottom - 1, z, 'pointed_dripstone[thickness=tip,vertical_direction=down]')
    return cells


def weathered(base, mossy, cracked, seed=0):
    """A paving of mostly `base`, mossy in drifts and cracked in patches (never single random blocks)."""
    def cap(x, z, e=0):
        if fbm(x, z, 7, seed + 3) > 0.42: return mossy
        if fbm(x, z, 5, seed + 11) < -0.48: return cracked
        return base
    return cap


def rim(w, cells, y, gaps=(), walling='stone_brick_wall', post='chiseled_stone_bricks', lamp='lantern', every=6):
    """A parapet round an island's brink (on the cells that have open air beside them), with lamp posts, open at `gaps`."""
    for (x, z) in cells:
        if all((x + dx, z + dz) in cells for dx, dz in DIRS.values()): continue
        if any(math.hypot(x - gx, z - gz) <= gr for gx, gz, gr in gaps): continue
        if not w.air(x, y, z): continue
        if (x * 5 + z * 3) % every == 0 and rnd3(x, 1, z) < 0.6:
            w.set(x, y, z, post)
            w.set(x, y + 1, z, lamp)
        else:
            w.set(x, y, z, walling)


def shard(w, cx, cy, cz, r, h, seed, top='moss_block'):
    """
    A floating crag: a faceted spike of bedded rock, widest two thirds of the way up, leaning a little, with a ledge
    or an overhang every few layers. `cy` is its flat top. Returns the cells of the top.
    """
    rng = random.Random(seed)
    n = rng.randint(5, 7)
    facets = [(k * TAU / n + rng.uniform(-0.3, 0.3), rng.uniform(0.72, 1.0)) for k in range(n)]
    lean = (rng.uniform(-0.22, 0.22), rng.uniform(-0.22, 0.22))
    crown = max(1, int(h * 0.22))
    tops = []
    for dy in range(-(h - crown), 1):
        t = (dy + h - crown) / max(1, h - crown)                     # 0 at the tip, 1 at the top
        profile = t ** 0.75 if t < 0.8 else 1 - 0.18 * (t - 0.8) / 0.2
        ledge = 1.18 if (dy // 3) % 2 == 0 else 1.0
        rr = max(0.6, r * profile * ledge)
        ox, oz = lean[0] * dy, lean[1] * dy
        if dy < 0:                                                   # a spine down the middle, so no layer hangs loose
            for yy in (dy, dy + 1): w.setif(cx + int(round(ox)), cy + yy, cz + int(round(oz)), strata(cx, cy + yy, cz, cy, seed))
        reach = int(rr) + 2
        for dx in range(-reach, reach + 1):
            for dz in range(-reach, reach + 1):
                px, pz = dx - ox, dz - oz
                d = math.hypot(px, pz)
                a = math.atan2(pz, px)
                limit = min(rr * f / max(0.35, math.cos(a - fa)) for fa, f in facets if math.cos(a - fa) > 0.35)
                if d > limit: continue
                x, y, z = cx + dx, cy + dy, cz + dz
                if dy == 0:
                    w.set(x, y, z, top)
                    tops.append((x, z))
                else:
                    w.set(x, y, z, strata(x, y, z, cy, seed))
    return tops


# ---------------------------------------------------------------------------------------------------- trees

def leaf_cluster(w, c, radius, rng, leaf, leaf2):
    """Leaves in layers, widest under the middle, with a ragged outline, a drooping fringe and vines off the fringe."""
    cx, cy, cz = int(math.floor(c[0])), int(math.floor(c[1])), int(math.floor(c[2]))
    seed = rng.randint(0, 9999)
    for dy, f in ((2, 0.36), (1, 0.72), (0, 1.0), (-1, 0.8)):
        rr = radius * f
        n = int(rr) + 2
        for dx in range(-n, n + 1):
            for dz in range(-n, n + 1):
                d = math.hypot(dx, dz) + (vnoise((cx + dx) * 0.9, (cz + dz) * 0.9, seed + dy) - 0.5) * 2.0
                if d > rr: continue
                x, y, z = cx + dx, cy + dy, cz + dz
                w.setif(x, y, z, leaf2 if vnoise(x * 0.33 + y, z * 0.33 - y, seed) > 0.68 else leaf)
                if dy != -1 or d < rr - 1.7: continue
                hang = int(rnd3(x, y, z, seed) * 3.4)                # the fringe droops 0 to 3 blocks
                for j in range(1, hang + 1): w.setif(x, y - j, z, leaf)
                if rnd3(x, y + 9, z, seed) > 0.34 or not (dx or dz): continue
                side = ('east' if dx > 0 else 'west') if abs(dx) >= abs(dz) else ('south' if dz > 0 else 'north')
                vx, vz = x + DIRS[side][0], z + DIRS[side][1]
                if not w.name(x, y - hang, z).endswith('_leaves'): continue
                for j in range(2 + int(rnd3(x, y + 5, z, seed) * 4)):
                    if not w.air(vx, y - hang - j, vz): break
                    w.set(vx, y - hang - j, vz, 'vine[%s=true]' % OPPOSITE[side])


def _under(w, p, bark):
    """The first free block under the bough at p, or None."""
    x, y, z = int(math.floor(p[0])), int(math.floor(p[1])), int(math.floor(p[2]))
    if w.get(x, y, z) != bark: return None
    while w.get(x, y - 1, z) == bark: y -= 1
    return (x, y - 1, z) if all(w.air(x, y - k, z) for k in (1, 2, 3, 4)) else None


def spline_tree(w, x, y, z, seed, height=13, spread=8.0, wood='dark_oak', leaf='oak_leaves', leaf2='flowering_azalea_leaves',
                lamp='lantern'):
    """
    A tree grown from curves. The trunk is a spline that flares into the ground, with root buttresses snaking out
    from its foot; four or five boughs leave it in a spiral, each with a side branch; every tip carries a layered,
    ragged, drooping cluster of leaves with vines off its fringe. A lantern hangs on a chain under each bough and
    glow berries under some.
    """
    rng = random.Random(seed)
    bark = wood + '_wood'
    base = (x + 0.5, float(y), z + 0.5)
    crown = (base[0] + rng.uniform(-2.2, 2.2), y + height, base[2] + rng.uniform(-2.2, 2.2))
    trunk = bezier(base, (base[0] + rng.uniform(-1.6, 1.6), y + height * 0.38, base[2] + rng.uniform(-1.6, 1.6)),
                   (crown[0] + rng.uniform(-1.2, 1.2), y + height * 0.72, crown[2] + rng.uniform(-1.2, 1.2)), crown, height * 3)
    for i, p in enumerate(trunk):
        t = i / (len(trunk) - 1)
        ball(w, p, 1.7 * (1 - t) + 0.75 * t + max(0.0, 1 - t * 7) ** 2 * 1.5, bark)
    for k in range(5):                                               # root buttresses
        a = k * TAU / 5 + rng.uniform(-0.3, 0.3)
        reach = rng.uniform(3.2, 5.0)
        ca, sa = math.cos(a), math.sin(a)
        tube(w, bezier((base[0] + ca * 1.3, y + 2.4, base[2] + sa * 1.3), (base[0] + ca * reach * 0.55, y + 2.0, base[2] + sa * reach * 0.55),
                       (base[0] + ca * reach * 0.9, y + 0.7, base[2] + sa * reach * 0.9), (base[0] + ca * reach, y - 0.4, base[2] + sa * reach), 14),
             1.0, 0.55, bark, only_air=True)
    tips = [(crown, spread * 0.5)]
    boughs = rng.randint(4, 5)
    a0 = rng.uniform(0, TAU)
    for k in range(boughs):
        s = trunk[int((0.5 + 0.4 * k / (boughs - 1)) * (len(trunk) - 1))]
        a = a0 + k * 2.4 + rng.uniform(-0.3, 0.3)
        ca, sa = math.cos(a), math.sin(a)
        reach, rise = spread * rng.uniform(0.62, 1.0), rng.uniform(1.5, 4.0)
        end = (s[0] + ca * reach, s[1] + rise, s[2] + sa * reach)
        pts = bezier(s, (s[0] + ca * reach * 0.35, s[1] - 0.3, s[2] + sa * reach * 0.35),
                     (s[0] + ca * reach * 0.8, s[1] + rise * 1.05, s[2] + sa * reach * 0.8), end, int(reach * 3) + 4)
        tube(w, pts, 1.0, 0.5, bark)
        tips.append((end, spread * rng.uniform(0.36, 0.48)))
        m = pts[int(len(pts) * 0.6)]
        a2 = a + rng.choice((-1, 1)) * rng.uniform(0.7, 1.1)
        reach2 = rng.uniform(2.8, 4.2)
        end2 = (m[0] + math.cos(a2) * reach2, m[1] + rng.uniform(0.5, 2.0), m[2] + math.sin(a2) * reach2)
        tube(w, bezier(m, m, end2, end2, 10), 0.5, 0.5, bark)
        tips.append((end2, rng.uniform(2.2, 3.0)))
        at = _under(w, pts[int(len(pts) * 0.45)], bark)
        if at:                                                       # a lantern tucked under the bough
            w.set(at[0], at[1], at[2], 'iron_chain[axis=y]')
            w.set(at[0], at[1] - 1, at[2], lamp + '[hanging=true]')
        at = _under(w, pts[int(len(pts) * 0.75)], bark)
        if at and k % 2 == 0:                                        # glow berries
            w.set(at[0], at[1], at[2], 'cave_vines_plant[berries=%s]' % ('true' if rng.random() < 0.5 else 'false'))
            w.set(at[0], at[1] - 1, at[2], 'cave_vines[age=25,berries=true]')
    for c, radius in tips: leaf_cluster(w, c, radius, rng, leaf, leaf2)


# ---------------------------------------------------------------------------------------------------- chains, bridges, water

def giant_chain(w, a, b, sag, dark='polished_blackstone', light='blackstone', link=5.0):
    """A chain of block-built links, each turned a quarter turn from the last, hanging in a curve from a to b."""
    def at(t):
        return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t - sag * 4 * t * (1 - t), a[2] + (b[2] - a[2]) * t)
    count = max(2, int(round(math.dist(a, b) / (link * 0.78))))
    for i in range(count):
        t = (i + 0.5) / count
        c, p, q = at(t), at(t - 0.01), at(t + 0.01)
        tx, ty, tz = q[0] - p[0], q[1] - p[1], q[2] - p[2]
        n = math.sqrt(tx * tx + ty * ty + tz * tz)
        tx, ty, tz = tx / n, ty / n, tz / n
        h = math.hypot(tx, tz) or 1.0
        side = (-tz / h, 0.0, tx / h)
        up = (ty * side[2] - tz * side[1], tz * side[0] - tx * side[2], tx * side[1] - ty * side[0])
        nx, ny, nz = side if i % 2 else up
        for k in range(56):
            th = k * TAU / 56
            lx, ln = math.cos(th) * link * 0.62, math.sin(th) * link * 0.3
            w.setif(int(math.floor(c[0] + tx * lx + nx * ln)), int(math.floor(c[1] + ty * lx + ny * ln)),
                    int(math.floor(c[2] + tz * lx + nz * ln)), dark if i % 2 else light)


CHAIN_ENDS = []               # where every anchor chain ends (the build checks each is buried in rock)


def anchor_chain(w, a, b, sag):
    """
    A giant chain from a to b, both of which lie inside an island's rock: the links run on into the stone (they are
    only laid in air, so the rock closes over them), and a boss of deepslate masonry stands where each end comes out.
    """
    def at(t):
        return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t - sag * 4 * t * (1 - t), a[2] + (b[2] - a[2]) * t)
    for order in (range(0, 60), range(100, 40, -1)):
        for i in order:
            p = at(i / 100.0)
            if w.air(int(math.floor(p[0])), int(math.floor(p[1])), int(math.floor(p[2]))):
                ball(w, p, 2.0, 'polished_deepslate', only_air=True)
                ball(w, p, 1.1, 'chiseled_deepslate')
                break
    giant_chain(w, a, b, sag)
    CHAIN_ENDS.append((a, b))


def _deck_heights(length, lift):
    """The standing height above the ends' floor for each step along a span, in half blocks, never steeper than half a block."""
    return [round(lift * math.sin(math.pi * x / length) * 2) / 2.0 for x in range(length + 1)]


SPANS = set()                 # every column a bridge deck covers, so nothing is scattered under or beside one


def arch_bridge(X, length, y, half, rise, P, lamp='lantern'):
    """
    A stone bridge from local x = 0 to `length`, its floor at y at both ends and `rise` higher in the middle (in
    half steps, so it walks without a jump). The deck is paved in a pattern between trim lines; kerbs carry a
    parapet with lamp posts. Under it one true arch: three ribs (under each kerb and under the crown of the road)
    follow a half ellipse from a springing deep below each end up to a thin crown, edged in deepslate voussoirs,
    with the deck's belly spanning between them. At each end a wedge of masonry carries the springing back and
    down into the island until it meets the rock, so the arch stands on something.
    """
    heights = _deck_heights(length, rise)
    spring = max(3, min(8, length // 3))
    for x, hq in enumerate(heights):
        u = 2.0 * x / length - 1
        depth = 1 + int(round(spring * (1 - math.sqrt(max(0.0, 1 - u * u)))))
        fy, step = y + int(hq), hq != int(hq)
        for z in range(-half, half + 1):
            a = abs(z)
            SPANS.add(X.at(x, z))
            mat = P['kerb'] if a == half else P['trim'] if a == half - 1 or a == 0 else P['pave'] if (x + a) % 2 == 0 else P['pave2']
            X.set(x, fy, z, mat)
            rib = a == half or z == 0
            reach = depth if rib else min(depth, 2)
            for d in range(1, reach + 1): X.set(x, fy - d, z, P['pier2'] if rib and d == reach else P['under'])
            if step: X.set(x, fy + 1, z, P['kerb'] if a == half else slab(mat))
            if a != half: continue
            top = fy + (2 if step else 1)
            if x % 4 == 0:
                X.set(x, top, z, P['post'])
                X.set(x, top + 1, z, lamp + '[hanging=false]')
            else:
                X.set(x, top, z, wall(P['kerb']))
        if depth <= 2 and x % 4 == 2:                                # lanterns under the crown
            X.set(x, fy - depth - 1, 0, 'iron_chain[axis=y]')
            X.set(x, fy - depth - 2, 0, lamp + '[hanging=true]')
    for end, sign in ((0, -1), (length, 1)):                         # the abutments
        for i in range(1, 13):
            deep = spring + 1 - int(i * 0.75)
            if deep < 1: break
            for z in range(-half, half + 1):
                for d in range(1, deep + 1): X.setif(end + sign * i, y - d, z, P['pier2'] if d == deep and abs(z) in (0, half) else P['under'])


def hanging_bridge(X, length, y, half, sag, P, lamp='lantern'):
    """
    A plank bridge hung between two stone gateposts: the deck dips `sag` in the middle (half steps), planks on
    beams, fence rails with lamps on every fourth post.
    """
    for x, hq in enumerate(_deck_heights(length, -sag)):
        height = y + 1 + hq                                           # where you stand
        k = int(math.floor(height))
        step = height != k
        for z in range(-half, half + 1):
            edge = abs(z) == half
            SPANS.add(X.at(x, z))
            if step and not edge:
                X.set(x, k, z, slab(P['plank']))
                X.set(x, k - 1, z, P['beam'] + '[axis=z]' if x % 2 == 0 else slab(P['plank'], top=True))
            else:
                X.set(x, k if step else k - 1, z, P['plank'])
                if x % 2 == 0: X.set(x, (k if step else k - 1) - 1, z, P['beam'] + '[axis=z]')
            if edge:
                top = k + 1 if step else k
                if x in (0, length):
                    for dy in range(3): X.set(x, top + dy, z, P['post'] if dy < 2 else P['cap'])
                    X.set(x, top + 3, z, lamp + '[hanging=false]')
                else:
                    X.set(x, top, z, P['fence'])
                    if x % 4 == 0: X.set(x, top + 1, z, lamp + '[hanging=false]')


def waterfall(w, x, z, dx, dz, y, bottom, P, width=1):
    """
    A water channel cut into the floor (its top at y) from (x, z) toward (dx, dz) until the land ends, lined and
    edged, pouring off the brink in a clean sheet down to `bottom`.
    """
    px, pz = -dz, dx
    i = 0
    while i < 40 and not w.air(x + dx * i, y - 1, z + dz * i):
        for s in range(-width, width + 1):
            cx, cz = x + dx * i + px * s, z + dz * i + pz * s
            w.set(cx, y, cz, 'water[level=0]')
            w.set(cx, y - 1, cz, P['channel'])
            w.set(cx, y + 1, cz, 'air')
        for s in (-width - 1, width + 1):
            cx, cz = x + dx * i + px * s, z + dz * i + pz * s
            if not w.air(cx, y - 1, cz):
                w.set(cx, y, cz, P['edge'])
                if w.name(cx, y + 1, cz).endswith('_wall'): w.set(cx, y + 1, cz, P['edge'])
        i += 1
    for s in range(-width, width + 1):
        cx, cz = x + dx * i + px * s, z + dz * i + pz * s
        w.set(cx, y, cz, 'water[level=1]')
        for yy in range(y - 1, bottom - 1, -1): w.setif(cx, yy, cz, 'water[level=8]')
    return i


# ---------------------------------------------------------------------------------------------------- the hub's layout

FLOOR = 100                  # every island's floor block; you stand at FLOOR + 1
BRIDGE = dict(kerb='stone_bricks', trim='polished_deepslate', pave='stone_bricks', pave2='polished_andesite', under='stone_bricks',
              post='chiseled_stone_bricks', pier='stone_bricks', pier2='polished_deepslate')
ROPE = dict(plank='dark_oak_planks', beam='stripped_dark_oak_log', post='stone_bricks', cap='chiseled_stone_bricks', fence='dark_oak_fence')
CHANNEL = dict(channel='polished_deepslate', edge='polished_andesite')
# the four camps: where each one's frame stands (it is written once, for the south-east) -> (z, quarter turns, mirrored)
CAMPS = {'cobalt': (72, 0, False), 'crimson': (72, 0, True), 'aurelian': (-72, 2, True), 'emerald': (-72, 2, False)}
# where the parapets open: the bridge heads (x, z, radius)
GAPS = {'core': ((35, 0, 5), (-35, 0, 5), (0, 35, 5), (0, -35, 5)), 'gate': ((0, -57, 5), (34, -72, 5), (-34, -72, 5)),
        'arrival': ((0, 55, 5), (24, 72, 5), (-24, 72, 5), (0, 89, 4)), 'dock': ((0, 98, 4), (16, 111, 4)),
        'records': ((52, 0, 5), (62, 19, 4), (62, -19, 4)), 'terrace': ((-52, 0, 5), (-60, 20, 4), (-60, -20, 4), (-108, 0, 4)),
        'belfry': ((-116, 0, 4), (-140, 0, 4)),
        'cobalt': ((48, 72, 5), (62, 46, 4)), 'crimson': ((-48, 72, 5), (-60, 47, 4)),
        'aurelian': ((48, -72, 5), (62, -46, 4)), 'emerald': ((-48, -72, 5), (-60, -47, 4))}
MAIN = ('core', 'gate', 'arrival', 'dock', 'records', 'terrace', 'belfry', 'cobalt', 'crimson', 'aurelian', 'emerald')
# where no mini-island may stand (x0, z0, x1, z1): the parkour, the skiff, the gantry's crate, the two waterfalls
KEEP_OUT = ((-168, -10, -118, 36), (8, 100, 48, 128), (-28, 102, -16, 116), (15, -66, 27, -48), (-27, -66, -15, -48))
CHAINS = [((sx * 60, 95, sz * 58), (sx * 19, 95, sz * 19)) for sx in (-1, 1) for sz in (-1, 1)]


def camp_frame(w, name):
    z, rot, mirror = CAMPS[name]
    return Xf(w, 0, z, rot, mirror)


def _paving(seed):
    base = weathered('stone_bricks', 'mossy_stone_bricks', 'cracked_stone_bricks', seed)
    return lambda x, z, e: 'polished_deepslate' if e < 2.3 else base(x, z)


def _meadow(seed):
    return lambda x, z, e: 'moss_block' if fbm(x, z, 5, seed) > 0.25 else 'coarse_dirt' if e < 1.4 else 'grass_block'


def lay_islands(w):
    """The hub's eleven islands and the bridges between them. Returns {name: {(x, z): how far inside}}."""
    cells = {}
    cells['core'] = island(w, star(0, 0, 33, 8, 2.0, seed=1), (-40, -40, 40, 40), FLOOR, 30, 12, 11, _paving(1))
    cells['gate'] = island(w, oval(0, -76, 36, 19, 2.8, seed=2), (-42, -100, 42, -54), FLOOR, 22, 9, 12, _paving(2))
    cells['arrival'] = island(w, oval(0, 72, 24, 17, 2.5, seed=3), (-30, 50, 30, 94), FLOOR, 18, 9, 13, _paving(3))
    cells['dock'] = island(w, oval(0, 110, 16, 12, 2.4, seed=8), (-21, 94, 21, 126), FLOOR, 12, 6, 21, _paving(8))
    cells['records'] = island(w, union(oval(76, 0, 24, 21, 2.6, seed=6), oval(86, -26, 10, 9, 2.4, seed=18)), (47, -38, 105, 27),
                               FLOOR, 20, 9, 15, _paving(5))      # with the overlook's promontory on its north-east
    cells['terrace'] = island(w, oval(-80, 0, 28, 24, 2.6, seed=7), (-113, -30, -47, 30), FLOOR, 22, 9, 16, _paving(6))
    cells['belfry'] = island(w, union(oval(-128, 0, 12, 11, 2.4, seed=9), oval(-130, -17, 12, 11, 2.4, seed=10)),
                             (-148, -34, -112, 16), FLOOR, 13, 6, 17, _paving(7))
    for i, name in enumerate(('cobalt', 'crimson', 'aurelian', 'emerald')):
        cx, cz = (74 if name in ('cobalt', 'aurelian') else -74), CAMPS[name][0]
        cells[name] = island(w, oval(cx, cz, 26, 27, 2.6, seed=30 + i), (cx - 32, cz - 33, cx + 32, cz + 33), FLOOR, 24, 10, 40 + i,
                             _paving(10 + i))
    arch_bridge(Xf(w, 33, 0), 21, FLOOR, 3, 2.5, BRIDGE)                     # the sanctum to the Hall of Records
    arch_bridge(Xf(w, -33, 0, rot=2), 21, FLOOR, 3, 2.5, BRIDGE)             # to the Armoury Terrace
    arch_bridge(Xf(w, 0, -33, rot=3), 26, FLOOR, 3, 3, BRIDGE)               # to the Match Gate
    arch_bridge(Xf(w, 0, 33, rot=1), 24, FLOOR, 3, 3, BRIDGE)                # to the arrival pavilion
    arch_bridge(Xf(w, 22, 72), 29, FLOOR, 3, 3, BRIDGE)                      # the pavilion to the Cobalt camp
    arch_bridge(Xf(w, -22, 72, rot=2), 29, FLOOR, 3, 3, BRIDGE)              # and to the Crimson camp
    arch_bridge(Xf(w, 32, -72), 19, FLOOR, 3, 2, BRIDGE)                     # the gate to the Aurelian camp
    arch_bridge(Xf(w, -32, -72, rot=2), 19, FLOOR, 3, 2, BRIDGE)             # and to the Emerald camp
    arch_bridge(Xf(w, -106, 0, rot=2), 12, FLOOR, 2, 1, BRIDGE)               # the terrace to the belfry
    arch_bridge(Xf(w, 0, 88, rot=1), 11, FLOOR, 2, 1, BRIDGE)                # the pavilion to the Sky-Dock
    for x, z in ((62, 17), (-60, 18)):                                       # the ring: each camp to the records or the terrace
        hanging_bridge(Xf(w, x, z, rot=1), 31, FLOOR, 2, 2, ROPE)
        hanging_bridge(Xf(w, x, -z, rot=3), 31, FLOOR, 2, 2, ROPE)
    return cells


def garden(w, cells, x, z, r):
    """A round bed of grass and moss in the paving, edged in tile, for a tree to stand in."""
    for dx in range(-int(r) - 1, int(r) + 2):
        for dz in range(-int(r) - 1, int(r) + 2):
            d = math.hypot(dx, dz)
            if d > r + 0.6 or (x + dx, z + dz) not in cells: continue
            w.set(x + dx, FLOOR, z + dz, 'deepslate_tiles' if d > r - 0.6 else 'moss_block' if vnoise((x + dx) * 0.4, (z + dz) * 0.4, 7) > 0.55 else 'grass_block')


def _tree(w, cells, x, z, seed, height, spread, r=2.6, **kw):
    garden(w, cells, x, z, r)
    spline_tree(w, x, FLOOR + 1, z, seed, height=height, spread=spread, **kw)


def _mini_stone(w, x, y, z, r, seed):
    """A stepping stone: a squat mossy rock with one thing on it (a lantern, a bush, or moss and a kerb stone)."""
    shard(w, x, y, z, r, 3 + int(r), seed, top='moss_block' if seed % 2 else 'mossy_stone_bricks')
    kind = seed % 3
    if kind == 0:
        w.set(x, y + 1, z, 'stone_brick_wall')
        w.set(x, y + 2, z, 'lantern[hanging=false]')
    elif kind == 1:
        w.set(x, y, z, 'moss_block')
        w.set(x, y + 1, z, 'flowering_azalea' if seed % 2 else 'azalea')
    else:
        w.set(x, y, z, 'polished_deepslate')
        w.set(x, y + 1, z, 'polished_deepslate_slab[type=bottom]')
        for dx, dz in ((1, 0), (0, 1), (-1, 0), (0, -1)):
            if not w.air(x + dx, y, z + dz) and w.air(x + dx, y + 1, z + dz) and rnd3(x + dx, y, z + dz, seed) < 0.6: w.set(x + dx, y + 1, z + dz, 'moss_carpet')


def _mini_ruin(w, x, y, z, r, seed):
    """A ruin rock: old paving trimmed in deepslate, two piers of a fallen arch, a lantern, moss and a bush."""
    tops = set(shard(w, x, y, z, r, 5 + int(r), seed, top='stone_bricks'))
    for (tx, tz) in tops:
        rim_ = any((tx + dx, tz + dz) not in tops for dx, dz in DIRS.values())
        n = fbm(tx, tz, 4, seed)
        w.set(tx, y, tz, 'polished_deepslate' if rim_ else 'mossy_stone_bricks' if n > 0.15 else 'cracked_stone_bricks' if n < -0.3 else 'stone_bricks')
    ax, az = ((1, 0), (0, 1))[seed % 2]
    lo, hi = (x - ax, z - az), (x + 2 * ax, z + 2 * az)
    toward = 'west' if ax else 'north'                               # the tall pier keeps the first stone of its arch
    for dy in range(1, 5): w.set(lo[0], y + dy, lo[1], 'chiseled_stone_bricks' if dy == 4 else 'mossy_stone_bricks' if dy == 1 else 'stone_bricks')
    w.set(lo[0], y + 5, lo[1], 'stone_brick_slab[type=bottom]')
    w.set(lo[0] + ax, y + 4, lo[1] + az, stairs('stone_bricks', toward, top=True))
    if hi in tops:
        for dy in (1, 2): w.set(hi[0], y + dy, hi[1], 'cracked_stone_bricks' if dy == 2 else 'stone_bricks')
        w.set(hi[0], y + 3, hi[1], 'stone_brick_slab[type=bottom]')
    px, pz = x - az, z + ax                                          # beside them: a lantern on a stub of wall, a bush
    if (px, pz) in tops:
        w.set(px, y + 1, pz, 'polished_deepslate_wall')
        w.set(px, y + 2, pz, 'lantern[hanging=false]')
    qx, qz = x + az, z - ax
    if (qx, qz) in tops and (qx, qz) not in (lo, hi):
        w.set(qx, y, qz, 'moss_block')
        w.set(qx, y + 1, qz, 'azalea')
    for (tx, tz) in tops:
        if w.air(tx, y + 1, tz) and rnd3(tx, y, tz, seed + 3) < 0.25: w.set(tx, y + 1, tz, 'moss_carpet')


def _mini_crystal(w, x, y, z, r, seed):
    """A crystal shard: calcite rimmed in basalt, amethyst growing out of it in spikes, buds between, a soul lantern."""
    rng = random.Random(seed)
    tops = set(shard(w, x, y, z, r, 5 + int(r), seed, top='calcite'))
    for (tx, tz) in tops:
        if any((tx + dx, tz + dz) not in tops for dx, dz in DIRS.values()): w.set(tx, y, tz, 'smooth_basalt')
    inner = sorted(t for t in tops if math.hypot(t[0] - x, t[1] - z) <= r - 1.2)
    rng.shuffle(inner)
    for i, (tx, tz) in enumerate([(x, z)] + inner[:4]):
        h = 4 - i if i < 3 else 1
        for dy in range(1, h + 1): w.set(tx, y + dy, tz, 'amethyst_block')
        w.set(tx, y + h + 1, tz, 'amethyst_cluster[facing=up]')
    for (tx, tz) in inner[4:9]:
        if w.air(tx, y + 1, tz): w.set(tx, y + 1, tz, ('small_amethyst_bud[facing=up]', 'medium_amethyst_bud[facing=up]', 'large_amethyst_bud[facing=up]')[rng.randint(0, 2)])
    for (tx, tz) in sorted(tops):
        if w.get(tx, y, tz) == 'smooth_basalt' and w.air(tx, y + 1, tz):
            w.set(tx, y + 1, tz, 'polished_deepslate_wall')
            w.set(tx, y + 2, tz, 'soul_lantern[hanging=false]')
            break


def _mini_lush(w, cells, name, x, y, z, rx, rz, seed):
    """A garden island: meadow on a tapering root of rock, one tree sized to it, flowers, a lantern on a mossy stub."""
    ground = cells[name] = island(w, oval(x, z, rx, rz, 2.2, seed=seed, rough=1.6), (x - int(rx) - 3, z - int(rz) - 3, x + int(rx) + 3, z + int(rz) + 3),
                                  y, min(rx, rz) * 1.3, 3, seed + 1, _meadow(seed))
    kind = seed % 3
    spline_tree(w, x, y + 1, z, seed, height=6 + kind, spread=min(rx, rz) * 0.6, wood=('oak', 'birch', 'dark_oak')[kind],
                leaf=('oak_leaves', 'birch_leaves', 'dark_oak_leaves')[kind], leaf2=('flowering_azalea_leaves', 'birch_leaves', 'oak_leaves')[kind])
    for (cx, cz), e in sorted(ground.items()):
        if e < 1.5 or not w.air(cx, y + 1, cz) or w.name(cx, y, cz) not in ('grass_block', 'moss_block'): continue
        r = rnd3(cx, y, cz, seed)
        if r < 0.22: w.set(cx, y + 1, cz, 'short_grass')
        elif r < 0.28: w.set(cx, y + 1, cz, 'fern')
        elif r < 0.34: w.set(cx, y + 1, cz, ('oxeye_daisy', 'cornflower', 'poppy', 'dandelion')[int(r * 1000) % 4])
    for (cx, cz), e in sorted(ground.items()):                       # the lantern, near the brink
        if 1.6 < e < 2.6 and w.air(cx, y + 1, cz) or (1.6 < e < 2.6 and w.name(cx, y + 1, cz) in ('short_grass', 'fern')):
            w.set(cx, y, cz, 'mossy_stone_bricks')
            w.set(cx, y + 1, cz, 'mossy_stone_brick_wall')
            w.set(cx, y + 2, cz, 'lantern[hanging=false]')
            break


def _off_chain(x, z, far):
    for (a, b) in CHAINS:
        ax, az, bx, bz = a[0], a[2], b[0], b[2]
        t = max(0.0, min(1.0, ((x - ax) * (bx - ax) + (z - az) * (bz - az)) / float((bx - ax) ** 2 + (bz - az) ** 2)))
        if math.hypot(x - ax - (bx - ax) * t, z - az - (bz - az) * t) < far: return False
    return True


def scatter(w, cells):
    """
    The mini-islands, in clusters that hang off the main islands: round each one, two to four clusters; each is one
    larger piece (a garden island, a ruin rock or a crystal shard, in turn) eleven to fourteen blocks out from the
    brink, at its own height, with a stepping stone or two between it and the island. Nothing stands under or
    beside a bridge, on a chain's line, in a waterfall, or in the parkour's or the skiff's air. Returns the tally.
    """
    rng = random.Random(4242)
    taken = set(SPANS)
    for name in MAIN: taken.update(cells[name])

    def free(x, z, r, gap):
        if any(x0 - r <= x <= x1 + r and z0 - r <= z <= z1 + r for x0, z0, x1, z1 in KEEP_OUT): return False
        if not _off_chain(x, z, r + 4): return False
        reach = int(r + gap) + 1
        return not any(dx * dx + dz * dz <= (r + gap) ** 2 and (x + dx, z + dz) in taken
                       for dx in range(-reach, reach + 1) for dz in range(-reach, reach + 1))

    def claim(x, z, r):
        reach = int(r) + 1
        taken.update((x + dx, z + dz) for dx in range(-reach, reach + 1) for dz in range(-reach, reach + 1) if dx * dx + dz * dz <= (r + 0.5) ** 2)

    tally = {'lush': 0, 'ruin': 0, 'crystal': 0, 'stone': 0}
    turn = 0
    for name in MAIN:
        ground = cells[name]
        cx = sum(c[0] for c in ground) / float(len(ground))
        cz = sum(c[1] for c in ground) / float(len(ground))
        want = 2 if len(ground) < 900 else 3 if len(ground) < 2200 else 4
        a0, made = rng.uniform(0, TAU), 0
        for j in range(40):
            if made >= want: break
            a = a0 + j * TAU * 0.381966                              # golden-angle steps: spread out, never the same bearing twice
            ca, sa = math.cos(a), math.sin(a)
            edge = 0
            while edge < 90 and (int(round(cx + ca * edge)), int(round(cz + sa * edge))) in ground: edge += 1
            role = ('lush', 'ruin', 'crystal')[turn % 3]
            r = {'lush': rng.uniform(5.2, 6.8), 'ruin': rng.uniform(3.2, 4.2), 'crystal': rng.uniform(3.0, 3.8)}[role]
            dist = edge + rng.uniform(11, 14) + r
            bx, bz = int(round(cx + ca * dist)), int(round(cz + sa * dist))
            if not free(bx, bz, r, 5): continue
            by = FLOOR + (rng.choice((-8, -6, -4)) if name == 'core' else rng.choice((-7, -5, -3, 3, 5, 7)))      # round the sanctum they sit low, under every sightline
            seed = 900 + turn * 7
            if role == 'lush': _mini_lush(w, cells, 'mini%d' % turn, bx, by, bz, r, r * rng.uniform(0.8, 1.0), seed)
            elif role == 'ruin': _mini_ruin(w, bx, by, bz, r, seed)
            else: _mini_crystal(w, bx, by, bz, r, seed)
            claim(bx, bz, r + 1)
            tally[role] += 1
            for k, f in enumerate((0.3, 0.64)):                      # stepping stones on the way out to it
                d = edge + (dist - r - edge) * f
                sx, sz = int(round(cx + ca * d + rng.uniform(-2, 2))), int(round(cz + sa * d + rng.uniform(-2, 2)))
                sr = rng.uniform(1.6, 2.3)
                if not free(sx, sz, sr, 2): continue
                _mini_stone(w, sx, FLOOR - 1 + int(round((by - FLOOR) * f)), sz, sr, seed + 1 + k)
                claim(sx, sz, sr + 1)
                tally['stone'] += 1
            made += 1
            turn += 1
    return tally


def dress(w, cells):
    """
    Parapets, waterfalls, the four anchor chains (each end buried in rock), the trees (on real islands only) and the
    mini-islands, once the buildings stand. Returns the mini-islands' tally.
    """
    for name, ground in list(cells.items()):
        if name in MAIN: rim(w, ground, FLOOR + 1, GAPS.get(name, ()))
    for sx in (-1, 1):
        waterfall(w, sx * 21, -68, 0, 1, FLOOR, 44, CHANNEL, width=1)        # the gate's twin falls, toward the sanctum
        _tree(w, cells['gate'], sx * 27, -65, 150 + sx, 8, 4.0, 2.4)
        _tree(w, cells['arrival'], sx * 16, 82, 140 + sx, 9, 5.0, 2.4)
        _tree(w, cells['records'], 57, sx * 11, 160 + sx, 9, 5.0, 2.4, lamp='soul_lantern')
    for a, b in CHAINS: anchor_chain(w, a, b, 3.5)                           # each camp's anchor chain to the sanctum's keel
    for k, (x, z) in enumerate(((25, 11), (-11, 25), (-25, -11), (11, -25))):   # the plaza: four low trees, off every sightline
        _tree(w, cells['core'], x, z, 100 + k, 7, 3.8, 2.4)
    lamps = {'cobalt': 'soul_lantern', 'crimson': 'lantern', 'aurelian': 'lantern', 'emerald': 'lantern'}
    leaves = {'emerald': ('oak_leaves', 'flowering_azalea_leaves'), 'aurelian': ('birch_leaves', 'birch_leaves'),
              'crimson': ('dark_oak_leaves', 'oak_leaves'), 'cobalt': ('spruce_leaves', 'oak_leaves')}
    woods = {'emerald': 'oak', 'aurelian': 'birch', 'crimson': 'dark_oak', 'cobalt': 'spruce'}
    for i, name in enumerate(CAMPS):
        X = camp_frame(w, name)
        for sz in (-1, 1):
            x, z = X.at(88, sz * 21)
            _tree(w, cells[name], x, z, 120 + i * 4 + sz, 11, 5.5, 3.0, lamp=lamps[name], wood=woods[name], leaf=leaves[name][0],
                  leaf2=leaves[name][1])
    return scatter(w, cells)
