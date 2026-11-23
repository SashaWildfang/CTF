"""
terrain_and_bridges.py: the land and what joins it.

Sculpted void islands with layered rock and stalactites underneath, faceted floating shards, arched and hanging
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
    is flat at `top`; underneath the rock thickens from a thin overhanging lip at the rim to `depth` at `reach`
    blocks in, in warped beds, with stalactites hanging from it and dripstone on their tips. Returns {(x, z): inside}.
    """
    x0, z0, x1, z1 = bounds
    cells = {}
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            e = inside(x, z)
            if e > 0: cells[(x, z)] = e
    rng = random.Random(seed)
    spires = {}
    for gx in range(x0 // 7 - 1, x1 // 7 + 2):
        for gz in range(z0 // 7 - 1, z1 // 7 + 2):
            if rng.random() < 0.6:
                spires[(gx, gz)] = (gx * 7 + rng.uniform(0, 7), gz * 7 + rng.uniform(0, 7), rng.uniform(1.8, 4.4), rng.uniform(5, 17))
    for (x, z), e in cells.items():
        lens = 1 - (1 - min(1.0, e / reach)) ** 1.8
        d = 2.5 + depth * lens + fbm(x, z, 5, seed) * 1.5 * min(1.0, e / 3)
        gx, gz = x // 7, z // 7
        for ix in (-1, 0, 1):
            for iz in (-1, 0, 1):
                s = spires.get((gx + ix, gz + iz))
                if s:
                    dd = math.hypot(x + 0.5 - s[0], z + 0.5 - s[1])
                    if dd < s[2]: d += s[3] * (1 - dd / s[2]) ** 1.4 * min(1.0, e / 5)
        bottom = top - int(d)
        for y in range(bottom, top): w.set(x, y, z, strata(x, y, z, top, seed))
        w.set(x, top, z, cap(x, z, e) if callable(cap) else cap)
        if int(d) > 9 and d - int(d) > 0.5 and rnd3(x, 0, z, seed) < 0.5:
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


def _deck_heights(length, lift):
    """The standing height above the ends' floor for each step along a span, in half blocks, never steeper than half a block."""
    return [round(lift * math.sin(math.pi * x / length) * 2) / 2.0 for x in range(length + 1)]


def arch_bridge(X, length, y, half, rise, P, lamp='lantern'):
    """
    A stone bridge from local x = 0 to `length`, its floor at y at both ends and `rise` higher in the middle (in
    half steps, so it walks without a jump). The deck is paved in a pattern between trim lines; kerbs carry a
    parapet with lamp posts; the belly thickens toward the abutments into an arch, on corbels, with lanterns hung
    under the kerbs; two pendant piers hang under the thirds.
    """
    heights = _deck_heights(length, rise)
    floors = []
    for x, hq in enumerate(heights):
        s = math.sin(math.pi * x / length)
        fy, step = y + int(hq), hq != int(hq)
        belly = 1 + int(round(3.2 * (1 - s) ** 2))
        floors.append((fy, belly))
        for z in range(-half, half + 1):
            a = abs(z)
            mat = P['kerb'] if a == half else P['trim'] if a == half - 1 or a == 0 else P['pave'] if (x + a) % 2 == 0 else P['pave2']
            X.set(x, fy, z, mat)
            for d in range(1, belly + 1): X.set(x, fy - d, z, P['under'])
            if step: X.set(x, fy + 1, z, P['kerb'] if a == half else slab(mat))
            if a != half: continue
            top = fy + (2 if step else 1)
            inward = 'north' if z > 0 else 'south'
            if x % 4 == 0:
                X.set(x, top, z, P['post'])
                X.set(x, top + 1, z, lamp + '[hanging=false]')
            else:
                X.set(x, top, z, wall(P['kerb']))
            X.set(x, fy - belly, z, stairs(P['under'], inward, top=True))
            if x % 4 == 2 and 2 < x < length - 2:
                X.set(x, fy - belly - 1, z, 'iron_chain[axis=y]')
                X.set(x, fy - belly - 2, z, lamp + '[hanging=true]')
    for px in (round(length / 3), round(2 * length / 3)):
        fy, belly = floors[px]
        for d in range(9):
            zw = half + 1 - (d * (half + 2)) // 9
            xt = 1 if d < 6 else 0
            for z in range(-max(0, zw), max(0, zw) + 1):
                for xx in range(px - xt, px + xt + 1):
                    X.set(xx, fy - belly - 1 - d, z, P['pier2'] if d % 3 == 0 else P['pier'])
        X.set(px, fy - belly - 10, 0, wall(P['kerb']))
        X.set(px, fy - belly - 11, 0, lamp + '[hanging=true]')


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
# where the parapets open: the bridge heads (x, z, radius)
GAPS = {'core': ((26, 0, 5), (-26, 0, 5), (0, 26, 5)), 'pavilion': ((0, 36, 5), (18, 48, 4), (-18, 48, 4)),
        'cobalt': ((50, 0, 5),), 'crimson': ((-50, 0, 5),), 'records': ((31, 48, 4),), 'parkour': ((-31, 48, 4), (-54, 48, 4))}
CRAGS = ((-36, 96, -24, 3.2, 10), (37, 106, 25, 2.8, 9), (-62, 112, -42, 3.6, 12), (64, 92, 42, 3.0, 10), (0, 118, 76, 3.4, 11),
         (-90, 96, 46, 3.0, 10), (90, 110, -46, 3.4, 11), (-18, 88, 72, 2.6, 8), (26, 114, 74, 2.8, 9), (-110, 106, 4, 3.2, 12),
         (110, 96, -6, 3.2, 12), (0, 126, -72, 4.0, 14), (-42, 124, -60, 3.0, 10), (46, 90, -62, 3.2, 11))


def _paving(seed):
    base = weathered('stone_bricks', 'mossy_stone_bricks', 'cracked_stone_bricks', seed)
    return lambda x, z, e: 'polished_deepslate' if e < 2.3 else base(x, z)


def lay_islands(w):
    """The hub's six islands and the five bridges between them. Returns {name: {(x, z): how far inside}}."""
    cells = {}
    core = union(star(0, 0, 25, 8, 2.2, seed=1), box(-10, -32, 10, -18), oval(0, -38, 28, 15, 2.6, seed=2))
    cells['core'] = island(w, core, (-34, -58, 34, 32), FLOOR, 26, 15, 11, _paving(1))
    cells['pavilion'] = island(w, oval(0, 49, 19, 14.5, 2.4, seed=3), (-24, 32, 24, 67), FLOOR, 16, 10, 12, _paving(2))
    cells['cobalt'] = island(w, oval(74, 0, 25, 27, 2.6, seed=4), (45, -32, 104, 32), FLOOR, 24, 14, 13, _paving(3))
    cells['crimson'] = island(w, oval(-74, 0, 25, 27, 2.6, seed=5), (-104, -32, -45, 32), FLOOR, 24, 14, 14, _paving(4))
    cells['records'] = island(w, oval(44, 48, 14, 13, 2.6, seed=6), (27, 32, 62, 64), FLOOR, 13, 8, 15, _paving(5))
    cells['parkour'] = island(w, oval(-42, 48, 12, 11, 2.4, seed=7), (-58, 34, -27, 62), FLOOR, 12, 7, 16, _paving(6))
    arch_bridge(Xf(w, 25, 0), 26, FLOOR, 3, 3, BRIDGE)                       # the sanctum to the Cobalt wing
    arch_bridge(Xf(w, -25, 0, rot=2), 26, FLOOR, 3, 3, BRIDGE)               # and to the Crimson wing
    arch_bridge(Xf(w, 0, 25, rot=1), 12, FLOOR, 3, 1.5, BRIDGE)              # the pavilion to the sanctum
    hanging_bridge(Xf(w, 17, 48), 15, FLOOR, 2, 1.5, ROPE)                   # the pavilion to the Hall of Records
    hanging_bridge(Xf(w, -17, 48, rot=2), 15, FLOOR, 2, 1.5, ROPE)           # and to the parkour
    return cells


def garden(w, cells, x, z, r):
    """A round bed of grass and moss in the paving, edged in tile, for a tree to stand in."""
    for dx in range(-int(r) - 1, int(r) + 2):
        for dz in range(-int(r) - 1, int(r) + 2):
            d = math.hypot(dx, dz)
            if d > r + 0.6 or (x + dx, z + dz) not in cells: continue
            w.set(x + dx, FLOOR, z + dz, 'deepslate_tiles' if d > r - 0.6 else 'moss_block' if vnoise((x + dx) * 0.4, (z + dz) * 0.4, 7) > 0.55 else 'grass_block')


def dress(w, cells):
    """Parapets, the two waterfalls, the anchor chains, the trees and the floating crags, once the buildings stand."""
    for name, ground in cells.items(): rim(w, ground, FLOOR + 1, GAPS.get(name, ()))
    for sx in (-1, 1):
        waterfall(w, sx * 15, -27, 0, 1, FLOOR, 46, CHANNEL, width=1)
        for sz in (-1, 1): giant_chain(w, (sx * 53, 91, sz * 7), (sx * 17, 80, sz * 4), 3.0)
        lamp = 'soul_lantern' if sx > 0 else 'lantern'
        for sz in (-1, 1):
            spline_tree(w, sx * 14, FLOOR + 2, sz * 14, 100 + sx * 3 + sz, height=9, spread=4.6)
            garden(w, cells['cobalt' if sx > 0 else 'crimson'], sx * 88, sz * 21, 3.0)
            spline_tree(w, sx * 88, FLOOR + 1, sz * 21, 120 + sx * 3 + sz, height=11, spread=5.5, lamp=lamp)
        garden(w, cells['pavilion'], sx * 13, 58, 2.4)
        spline_tree(w, sx * 13, FLOOR + 1, 58, 140 + sx, height=9, spread=5.0)
        garden(w, cells['core'], sx * 24, -43, 3.0)
        spline_tree(w, sx * 24, FLOOR + 1, -43, 150 + sx, height=13, spread=7.5)
    garden(w, cells['parkour'], -39, 41, 2.6)
    spline_tree(w, -39, FLOOR + 1, 41, 160, height=10, spread=5.5)
    garden(w, cells['records'], 36, 57, 2.6)
    spline_tree(w, 36, FLOOR + 1, 57, 161, height=10, spread=6.0, lamp='soul_lantern')
    for i, (x, y, z, r, h) in enumerate(CRAGS):
        shard(w, x, y, z, r, h, 700 + i)
        if i % 3 == 0:                                               # a broken column of the old citadel
            for dy in range(1, 3 + i % 2): w.set(x, y + dy, z, 'polished_andesite')
            w.set(x, y + 3 + i % 2, z, 'chiseled_stone_bricks')
        elif i % 3 == 1:
            w.set(x, y + 1, z, 'stone_brick_wall')
            w.set(x, y + 2, z, 'lantern[hanging=false]')
        else:
            spline_tree(w, x, y + 1, z, 180 + i, height=6, spread=3.6)
