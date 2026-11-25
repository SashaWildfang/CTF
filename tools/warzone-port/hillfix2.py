"""Moves (or drops) hills that are covered or shut in. A hill is good when most of its 5x5 sees the sky (nothing
solid within 30 blocks above; glass and panes count as clear), its floor is solid with two blocks of headroom,
and a player can get there from every spawn (walking, or bridging/pillaring through open air: never through walls).
Bad hills search nearby for a good 5x5 (fair between the spawns, clear of spawns, flags and other hills); side
hills that were mirror pairs move as a mirror pair. Prints the plan; --write updates map.yml."""
import sys, math, json, collections, re
import numpy as np, yaml
sys.path.insert(0, '/home/claude/tgm/tools')
from world import World
from hillreach import Grid, bfs, P

SKY_UP = 30

def sky_frac(g, x0, z0, x1, z1, ym):
    cols = ok = 0
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            cols += 1
            i, j, k = x - g.x0, ym - g.y0, z - g.z0
            if not (0 <= i < g.shape[0] and 0 <= j < g.shape[1] and 0 <= k < g.shape[2]) or not g.covered[i, j, k]: ok += 1
    return ok / cols

def floor_ok(g, x0, z0, x1, z1, ym):
    cells = good = water = 0
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            cells += 1
            if g.get(g.water, x, ym, z) or g.get(g.water, x, ym - 1, z): water += 1
            if g.S(x, ym - 1, z) and not g.S(x, ym, z) and not g.S(x, ym + 1, z): good += 1
    return good / cells, water

def air_reach(g, start):
    """Open space a player can move through by bridging and pillaring: 2-high clear cells, 6-connected."""
    def room(x, y, z): return not g.S(x, y, z) and not g.S(x, y + 1, z) and g.y0 <= y < g.y0 + g.shape[1] - 2
    seen = {start}; q = collections.deque([start])
    while q:
        x, y, z = q.popleft()
        for dx, dy, dz in ((1,0,0),(-1,0,0),(0,0,1),(0,0,-1),(0,1,0),(0,-1,0)):
            n = (x + dx, y + dy, z + dz)
            if n in seen or not (g.x0 <= n[0] < g.x0 + g.shape[0] and g.z0 <= n[2] < g.z0 + g.shape[2]): continue
            if room(*n): seen.add(n); q.append(n)
    return seen

def main(write, only):
    ids = [l.strip().split('|')[1] for f in ('ids.txt', 'ids2.txt') for l in open('/home/claude/tgm/' + f) if '|' in l]
    if only: ids = only
    report = {}
    for mid in ids:
        path = f'/home/claude/tgm/maps/{mid}/map.yml'
        y = yaml.safe_load(open(path))
        hills = y.get('hills') or []
        if not hills: continue
        w = World(f'/home/claude/tgm/maps/{mid}')
        spawns = {t: P(d['spawn']) for t, d in y['teams'].items()}
        flags = [P(d['flag']) for d in y['teams'].values() if d.get('flag')]
        pts = list(spawns.values()) + [P(h['corner-1']) for h in hills]
        x0 = int(min(p[0] for p in pts)) - 60; x1 = int(max(p[0] for p in pts)) + 60
        z0 = int(min(p[2] for p in pts)) - 60; z1 = int(max(p[2] for p in pts)) + 60
        ys = [p[1] for p in pts]; y0 = max(-64, int(min(ys)) - 25); y1 = int(max(ys)) + 30 + SKY_UP
        g = Grid(w, x0, x1, y0, y1, z0, z1)
        opaque = g.solid & ~g.clear
        cs = np.concatenate([np.zeros((g.shape[0], 1, g.shape[2]), int), np.cumsum(opaque, axis=1)], axis=1)
        ny = g.shape[1]
        top = np.minimum(np.arange(ny) + SKY_UP, ny)
        g.covered = (cs[:, top, :] - cs[:, np.arange(ny), :]) > 0          # [x, y, z]: something opaque in y..y+SKY_UP
        walk, air = {}, {}
        for t, s in spawns.items():
            sx, sy, sz = math.floor(s[0]), math.floor(s[1]), math.floor(s[2])
            st = next(((sx, sy + d, sz) for d in [0, 1, 2] + list(range(-1, -41, -1)) if g.stand(sx, sy + d, sz)), None)   # floating spawns drop players
            walk[t] = bfs(g, st, y0) if st else set()
            air[t] = air_reach(g, st) if st else set()
        nx, ny, nz = g.shape
        def to_arr(cells):
            arr = np.zeros(g.shape, bool)
            for (x, yy, z) in cells:
                i, j, k = x - g.x0, yy - g.y0, z - g.z0
                if 0 <= i < nx and 0 <= j < ny and 0 <= k < nz: arr[i, j, k] = True
            return arr
        def box(arr, size, ylo=0, yhi=0):
            """sum over [i, i+size) x [j+ylo, j+yhi] x [k, k+size) for every (i, j, k)."""
            a = arr.astype(np.int32)
            if ylo != 0 or yhi != 0:
                acc = np.zeros_like(a)
                for dy in range(ylo, yhi + 1):
                    sh = np.roll(a, -dy, axis=1)
                    if dy > 0: sh[:, ny - dy:, :] = 0
                    if dy < 0: sh[:, :-dy, :] = 0
                    acc += sh
                a = acc
            c = np.zeros((nx + 1, ny, nz + 1), np.int32)
            c[1:, :, 1:] = a.cumsum(0).cumsum(2)
            out = np.zeros(g.shape, np.int32)
            out[:nx - size + 1, :, :nz - size + 1] = (c[size:, :, size:] - c[:-size, :, size:] - c[size:, :, :-size] + c[:-size, :, :-size])[:nx - size + 1, :, :nz - size + 1]
            return out
        S = g.solid
        below = np.zeros_like(S); below[:, 1:, :] = S[:, :-1, :]
        above = np.zeros_like(S); above[:, :-1, :] = S[:, 1:, :]
        floor_cell = below & ~S & ~above
        wat = g.water.copy()                                  # water at the feet (ice over water is a fine floor)
        F5 = box(floor_cell, 5); W5 = box(wat, 5); K5 = box(~g.covered, 5)
        # a spawn shut in a box (Warzone teleporters, doors on buttons): reach can't be judged here, only sky and floor
        reach_known = all(len(air[t]) > 3000 for t in air)
        if not reach_known: print(f'   ({mid}: spawn reach unknown: {[len(air[t]) for t in air]} cells)', flush=True)
        mid_x = sum(s[0] for s in spawns.values()) / len(spawns); mid_z = sum(s[2] for s in spawns.values()) / len(spawns)
        # reach near a 5x5 at feet height y: any reached cell in x-1..x+5, z-1..z+5, y-1..y+2  (7x7 window starting at x-1)
        def zone_any(cells):
            arr = to_arr(cells)
            z7 = box(arr, 7, -1, 2) > 0
            out = np.zeros(g.shape, bool); out[1:, :, 1:] = z7[:-1, :, :-1]
            return out
        WALK = np.logical_and.reduce([zone_any(walk[t]) for t in walk]) if reach_known else np.ones(g.shape, bool)
        AIR = np.logical_and.reduce([zone_any(air[t]) for t in air]) if reach_known else np.ones(g.shape, bool)

        def judge(hx0, hz0, ym, size=5):
            i, j, k = hx0 - g.x0, ym - g.y0, hz0 - g.z0
            if size == 5 and 0 <= i < nx - 5 and 0 <= j < ny and 0 <= k < nz - 5:
                return dict(sky=round(K5[i, j, k] / 25, 2), floor=round(F5[i, j, k] / 25, 2), water=int(W5[i, j, k]), walk=bool(WALK[i, j, k]), air=bool(AIR[i, j, k]))
            hx1, hz1 = hx0 + size - 1, hz0 + size - 1
            sky = sky_frac(g, hx0, hz0, hx1, hz1, ym)
            fl, water = floor_ok(g, hx0, hz0, hx1, hz1, ym)
            zone = [(x, yy, z) for x in range(hx0 - 1, hx1 + 2) for z in range(hz0 - 1, hz1 + 2) for yy in range(ym - 1, ym + 3)]
            w_ok = all(any(c in walk[t] for c in zone) for t in walk) if reach_known else True
            a_ok = all(any(c in air[t] for c in zone) for t in air) if reach_known else True
            return dict(sky=round(sky, 2), floor=round(fl, 2), water=water, walk=w_ok, air=a_ok)

        def good(j, strict=True):
            return j['sky'] >= (0.9 if strict else 0.6) and j['floor'] >= 0.8 and j['water'] == 0 and j['air']

        cur = []
        for h in hills:
            a, b = P(h['corner-1']), P(h['corner-2'])
            hx0, hz0 = int(min(a[0], b[0])), int(min(a[2], b[2])); size = int(max(a[0], b[0])) - hx0 + 1
            ym = int(min(a[1], b[1]))
            j = judge(hx0, hz0, ym, size)
            cur.append(dict(h=h, x0=hx0, z0=hz0, y=ym, size=size, j=j, ok=good(j, False)))

        sp = list(spawns.values())
        def side(mx, mz):
            ds = [math.hypot(mx - q[0], mz - q[2]) for q in sp]
            return ds[0] - ds[1] if len(ds) == 2 else max(ds) - min(ds)

        def candidates(cx, cz, cy, others, radius=int(__import__("os").environ.get("HILL_RADIUS", "24"))):
            out = []
            want = side(cx, cz)                       # keep the hill as far toward a side as it was (0 = a middle hill)
            for dx in range(-radius, radius + 1):
                for dz in range(-radius, radius + 1):
                    x, z = int(cx + dx) - 2, int(cz + dz) - 2
                    mx, mz = x + 2.5, z + 2.5
                    if any(math.hypot(mx - s[0], mz - s[2]) < 12 for s in spawns.values()): continue
                    if any(math.hypot(mx - f[0], mz - f[2]) < 10 for f in flags): continue
                    if any(math.hypot(mx - o[0], mz - o[1]) < 14 for o in others): continue
                    # the floor: the highest standable surface within 8 of the old height
                    for ym in sorted(range(int(cy) - 10, int(cy) + 11), key=lambda v: abs(v - cy)):
                        if not g.stand(x + 2, ym, z + 2): continue
                        j = judge(x, z, ym)
                        if good(j):
                            score = math.hypot(dx, dz) + 3 * abs(side(mx, mz) - want) + (0 if j['walk'] else 30) + abs(ym - cy)
                            out.append((score, x, z, ym, j))
                            break                                   # the nearest good height at this spot
            return sorted(out)

        plan = []
        keep_names = None
        if mid == 'sandstone':                      # Sasha: one hill on Sandstone
            keep_names = {'Dune Bridge'}
        if mid == 'eclipse':                        # an indoor arena: the roof covers the whole map, hills are the builder's own
            continue
        centers = {c['h']['name']: (c['x0'] + c['size'] / 2, c['z0'] + c['size'] / 2) for c in cur}
        done = set()
        new = []
        for c in cur:
            name = c['h']['name']
            if keep_names and name not in keep_names:
                plan.append(f'drop {name} (one hill on this map)'); done.add(name); continue
            if name in done: continue
            if c['ok']:
                new.append(c['h']); continue
            cx, cz = centers[name]
            # its mirror partner (2M - centre), if that is also bad
            partner = None
            for o in cur:
                if o is c or o['h']['name'] in done: continue
                ox, oz = centers[o['h']['name']]
                if math.hypot(ox - (2 * mid_x - cx), oz - (2 * mid_z - cz)) < 3 and abs(cx - mid_x) + abs(cz - mid_z) > 4:
                    partner = o
            dropped = {p.split(' ')[1] for p in plan if p.startswith('drop ')}
            others = [centers[o['h']['name']] for o in cur if o is not c and o is not partner and o['h']['name'] not in dropped
                      and (not keep_names or o['h']['name'] in keep_names)]
            picked = None
            for score, x, z, ym, j in candidates(cx, cz, c['y'], others):
                if partner is None: picked = (x, z, ym, j, None); break
                px, pz = int(round(2 * mid_x - (x + 2.5) - 2.5)), int(round(2 * mid_z - (z + 2.5) - 2.5))
                pym = next((v for v in sorted(range(ym - 4, ym + 5), key=lambda v: abs(v - ym)) if g.stand(px + 2, v, pz + 2)), None)
                if pym is None: continue
                pj = judge(px, pz, pym)
                if good(pj) and math.hypot(px + 2.5 - (x + 2.5), pz + 2.5 - (z + 2.5)) >= 14:
                    picked = (x, z, ym, j, (px, pz, pym, pj)); break
            if picked is None and partner is not None:          # no mirror pair fits: move each on its own
                partner = None
                for score, x, z, ym, j in candidates(cx, cz, c['y'], others):
                    picked = (x, z, ym, j, None); break
            if picked is None:
                plan.append(f"drop {name} (no visible, reachable 5x5 nearby; was {c['j']})")
                done.add(name)
                if partner: plan.append(f"drop {partner['h']['name']} (its mirror)"); done.add(partner['h']['name'])
                continue
            x, z, ym, j, pp = picked
            moved = dict(c['h']); moved['corner-1'] = f'{x} {ym} {z}'; moved['corner-2'] = f'{x + 4} {ym + 3} {z + 4}'; moved.pop('beacon', None)
            new.append(moved); done.add(name); centers[name] = (x + 2.5, z + 2.5)
            plan.append(f"move {name}: {c['x0']},{c['y']},{c['z0']} {c['j']} -> {x},{ym},{z} {j}")
            if pp:
                px, pz, pym, pj = pp
                m2 = dict(partner['h']); m2['corner-1'] = f'{px} {pym} {pz}'; m2['corner-2'] = f'{px + 4} {pym + 3} {pz + 4}'; m2.pop('beacon', None)
                new.append(m2); done.add(partner['h']['name']); centers[partner['h']['name']] = (px + 2.5, pz + 2.5)
                plan.append(f"move {partner['h']['name']} (mirror): -> {px},{pym},{pz} {pj}")
        # keep the original order
        order = [h['name'] for h in hills]
        new.sort(key=lambda h: order.index(h['name']))
        report[mid] = dict(plan=plan, before=[(c['h']['name'], c['j']) for c in cur], after=[h['name'] for h in new])
        if plan:
            print(f'== {mid} ({len(hills)} -> {len(new)} hills)'); [print('   ', p) for p in plan]
        if write and plan:
            text = open(path).read()
            block = 'hills:\n' + ''.join(f'  - name: "{h["name"]}"\n    corner-1: "{h["corner-1"]}"\n    corner-2: "{h["corner-2"]}"\n'
                                         + (f'    beacon: "{h["beacon"]}"\n' if h.get('beacon') else '') for h in new)
            text = re.sub(r'hills:\n(?:  .*\n)+', block if new else '', text, count=1)
            if not new:
                text = re.sub(r'modes: \[(.*)\]', lambda m: 'modes: [' + ', '.join(x for x in m.group(1).split(', ') if x.strip('"') != 'koth') + ']', text, count=1)
            open(path, 'w').write(text)
    json.dump(report, open('/tmp/hillfix2.json', 'w'), indent=1, default=str)

if __name__ == '__main__':
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    main('--write' in sys.argv, args)
