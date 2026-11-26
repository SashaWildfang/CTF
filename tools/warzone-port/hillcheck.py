"""Full KOTH check for a map's hills: floor, headroom, open sky, water, label room, spacing, reachability and
fairness (walking distance from each team's spawn). Prints one line per hill and a verdict.

    python3 hillcheck.py MAPID [MAPID...]      (reads maps/<id>/map.yml)
"""
import sys, math, collections
import numpy as np, yaml
sys.path.insert(0, '/home/claude/tgm/tools')
from world import World
from hillreach import Grid, P

SKY_UP = 30

def walk_dist(g, start, ymin, limit=600000):
    """BFS like hillreach.bfs, but keeps the step count to every cell (drops count as one step)."""
    dist = {start: 0}; q = collections.deque([start])
    while q and len(dist) < limit:
        x, y, z = q.popleft(); d = dist[(x, y, z)]
        moves = []
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx_, nz_ = x + dx, z + dz
            if g.stand(nx_, y, nz_): moves.append((nx_, y, nz_))
            elif g.S(nx_, y, nz_) and not g.get(g.tall, nx_, y, nz_) and not g.S(x, y + 2, z) and g.stand(nx_, y + 1, nz_):
                moves.append((nx_, y + 1, nz_))
            elif not g.S(nx_, y, nz_) and not g.S(nx_, y + 1, nz_):
                for k in range(1, 7):
                    yy = y - k
                    if yy < ymin or g.S(nx_, yy, nz_): break
                    if g.stand(nx_, yy, nz_): moves.append((nx_, yy, nz_)); break
        if (g.get(g.climb, x, y, z) or g.get(g.water, x, y, z)) and g.stand(x, y + 1, z): moves.append((x, y + 1, z))
        if (g.get(g.water, x, y, z) or g.get(g.climb, x, y, z)) and g.stand(x, y - 1, z): moves.append((x, y - 1, z))
        for m in moves:
            if m not in dist: dist[m] = d + 1; q.append(m)
    return dist

def air_dist(g, start, limit=3000000):
    """Bridging / pillaring through open air (2-high), 6-connected: a lower bound on how far a builder has to go."""
    dist = {start: 0}; q = collections.deque([start])
    room = lambda x, y, z: not g.S(x, y, z) and not g.S(x, y + 1, z) and g.y0 <= y < g.y0 + g.shape[1] - 2
    while q and len(dist) < limit:
        x, y, z = q.popleft(); d = dist[(x, y, z)]
        for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, 1, 0), (0, -1, 0)):
            n = (x + dx, y + dy, z + dz)
            if n in dist or not (g.x0 <= n[0] < g.x0 + g.shape[0] and g.z0 <= n[2] < g.z0 + g.shape[2]): continue
            if room(*n): dist[n] = d + 1; q.append(n)
    return dist

def check(mid, quiet=False):
    y = yaml.safe_load(open(f'/home/claude/tgm/maps/{mid}/map.yml'))
    hills = y.get('hills') or []
    if not hills: return None
    w = World(f'/home/claude/tgm/maps/{mid}')
    spawns = {t: P(d['spawn']) for t, d in y['teams'].items()}
    flags = {t: P(d['flag']) for t, d in y['teams'].items() if d.get('flag')}
    pts = list(spawns.values()) + [P(h['corner-1']) for h in hills] + [P(h['corner-2']) for h in hills]
    x0 = int(min(p[0] for p in pts)) - 50; x1 = int(max(p[0] for p in pts)) + 50
    z0 = int(min(p[2] for p in pts)) - 50; z1 = int(max(p[2] for p in pts)) + 50
    ys = [p[1] for p in pts]; y0 = max(-64, int(min(ys)) - 30); y1 = int(max(ys)) + 30 + SKY_UP
    g = Grid(w, x0, x1, y0, y1, z0, z1)
    opaque = g.solid & ~g.clear
    walk, air = {}, {}
    for t, s in spawns.items():
        sx, sy, sz = math.floor(s[0]), math.floor(s[1]), math.floor(s[2])
        st = next(((sx, sy + d, sz) for d in [0, 1, 2] + list(range(-1, -41, -1)) if g.stand(sx, sy + d, sz)), None)
        walk[t] = walk_dist(g, st, y0) if st else {}
        air[t] = air_dist(g, st) if st else {}
    boxed = any(len(a) < 3000 for a in air.values())
    rows = []
    for h in hills:
        a, b = P(h['corner-1']), P(h['corner-2'])
        hx0, hx1 = int(min(a[0], b[0])), int(max(a[0], b[0])); hz0, hz1 = int(min(a[2], b[2])), int(max(a[2], b[2]))
        ym = int(min(a[1], b[1])); cx, cz = (hx0 + hx1 + 1) / 2, (hz0 + hz1 + 1) / 2
        cells = [(x, z) for x in range(hx0, hx1 + 1) for z in range(hz0, hz1 + 1)]
        floor = sum(g.S(x, ym - 1, z) for x, z in cells) / len(cells)
        head = sum(not g.S(x, ym, z) and not g.S(x, ym + 1, z) for x, z in cells) / len(cells)
        sky = sky12 = 0
        for x, z in cells:
            i, k = x - g.x0, z - g.z0
            j0 = ym - g.y0
            if not opaque[i, j0:j0 + SKY_UP, k].any(): sky += 1
            if not opaque[i, j0:j0 + 12, k].any(): sky12 += 1
        sky /= len(cells); sky12 /= len(cells)
        water = sum(g.get(g.water, x, ym, z) for x, z in cells)
        zone = [(x, yy, z) for x in range(hx0 - 1, hx1 + 2) for z in range(hz0 - 1, hz1 + 2) for yy in range(ym - 1, ym + 3)]
        wd = {t: min((walk[t][c] for c in zone if c in walk[t]), default=None) for t in walk}
        ad = {t: min((air[t][c] for c in zone if c in air[t]), default=None) for t in air}
        near_spawn = min(math.hypot(cx - s[0], cz - s[2]) for s in spawns.values())
        near_flag = min((math.hypot(cx - f[0], cz - f[2]) for f in flags.values()), default=99)
        others = [((int(P(o['corner-1'])[0]) + int(P(o['corner-2'])[0]) + 1) / 2, (int(P(o['corner-1'])[2]) + int(P(o['corner-2'])[2]) + 1) / 2) for o in hills if o is not h]
        near_hill = min((math.hypot(cx - ox, cz - oz) for ox, oz in others), default=99)
        # label room: some spot 1.6-2.5 from the middle, 2 blocks clear above the floor
        label = any(not g.S(int(math.floor(cx + r * math.cos(t))), ym, int(math.floor(cz + r * math.sin(t))))
                    and not g.S(int(math.floor(cx + r * math.cos(t))), ym + 1, int(math.floor(cz + r * math.sin(t))))
                    for r in (1.6, 2.0, 2.5) for t in [k * math.pi / 4 for k in range(8)])
        problems = []
        if floor < 0.8: problems.append(f'floor {floor:.2f}')
        if head < 0.9: problems.append(f'headroom {head:.2f}')
        if sky12 < 0.9: problems.append(f'covered (nothing solid within 12 above on only {sky12:.0%})')
        if water: problems.append(f'water {water}')
        if not label: problems.append('no label room')
        if near_spawn < 12: problems.append(f'{near_spawn:.0f} from a spawn')
        if near_flag < 10: problems.append(f'{near_flag:.0f} from a flag')
        if near_hill < 14: problems.append(f'{near_hill:.0f} from another hill')
        if not boxed and any(v is None for v in ad.values()): problems.append('shut in')
        rows.append(dict(name=h['name'], box=[hx0, ym, hz0, hx1, hz1], floor=floor, head=head, sky=sky, sky12=sky12, walk=wd, air=ad,
                         spawn=near_spawn, flag=near_flag, problems=problems))
    if not quiet:
        print(f'== {mid}  modes={y["modes"]}  {"(spawns boxed in: reach not judged)" if boxed else ""}')
        for r in rows:
            wtxt = ' '.join(f'{t}:{"-" if v is None else v}' for t, v in r['walk'].items())
            atxt = ' '.join(f'{t}:{"-" if v is None else v}' for t, v in r['air'].items())
            print(f"   {'OK ' if not r['problems'] else 'BAD'} {r['name']:18} {r['box']}  sky {r['sky12']:.2f}/{r['sky']:.2f} floor {r['floor']:.2f}"
                  f"  walk[{wtxt}] air[{atxt}]  {'; '.join(r['problems'])}")
        # fairness over the set: each team's total walk (or air) distance to all hills
        for kind in ('walk', 'air'):
            tot = {t: sum((r[kind][t] or 0) for r in rows) for t in spawns}
            if all(all(r[kind][t] is not None for r in rows) for t in spawns):
                v = list(tot.values()); print(f'   fairness ({kind}): {tot}  diff {abs(v[0]-v[1])/max(1,max(v)):.0%}')
                break
    return rows

if __name__ == '__main__':
    for m in sys.argv[1:]: check(m)
