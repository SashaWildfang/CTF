"""Every KOTH hill on the ported maps: is it under open sky (visible), and can a player walk to it from each team's
spawn (steps up 1, drops up to 6, ladders/vines/water climbable, doors passable)? Writes /tmp/hillreach.json."""
import sys, os, json, math, collections
import numpy as np, yaml
sys.path.insert(0, '/home/claude/tgm/tools')
from world import World, solid

CLIMB = ('ladder', 'vine', 'scaffolding', 'twisting_vines', 'weeping_vines', 'cave_vines')
TALL = ('fence', '_wall')            # 1.5 high: can't be stepped up onto

def passable(name):
    if name in ('water', 'bubble_column'): return True
    if 'door' in name and 'trapdoor' not in name: return True
    if 'fence_gate' in name: return True
    if any(c in name for c in CLIMB): return True
    return not solid(name)

class Grid:
    def __init__(self, w, x0, x1, y0, y1, z0, z1):
        self.x0, self.y0, self.z0 = x0, y0, z0
        nx, ny, nz = x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1
        self.solid = np.zeros((nx, ny, nz), bool); self.water = np.zeros_like(self.solid)
        self.climb = np.zeros_like(self.solid); self.tall = np.zeros_like(self.solid); self.clear = np.zeros_like(self.solid)
        for cx in range(x0 >> 4, (x1 >> 4) + 1):
            for cz in range(z0 >> 4, (z1 >> 4) + 1):
                c = w.chunk(cx, cz)
                if not c: continue
                for sy, (pal, data) in c['sections'].items():
                    if sy * 16 + 15 < y0 or sy * 16 > y1: continue
                    names = [p.split('[')[0] for p in pal]
                    if len(pal) == 1:
                        idx = np.zeros(4096, int)
                    else:
                        bits = max(4, math.ceil(math.log2(len(pal)))); per = 64 // bits
                        arr = np.array(data, dtype=np.uint64)
                        i = np.arange(4096)
                        v = (arr[i // per] >> ((i % per) * bits).astype(np.uint64)) & np.uint64((1 << bits) - 1)
                        idx = np.minimum(v.astype(int), len(pal) - 1)
                    idx = idx.reshape(16, 16, 16)                     # [y][z][x]
                    sol = np.array([not passable(n) for n in names]); wat = np.array([n in ('water', 'bubble_column') for n in names])
                    clb = np.array([any(k in n for k in CLIMB) for n in names]); tal = np.array([any(k in n for k in TALL) and solid(n) for n in names])
                    clr = np.array([any(k in n for k in ('glass', 'pane', 'iron_bars', 'chain', 'fence', 'barrier')) for n in names])
                    for name, arr_ in (('solid', sol), ('water', wat), ('climb', clb), ('tall', tal), ('clear', clr)):
                        block = arr_[idx].transpose(2, 0, 1)            # [x][y][z]
                        gx0, gy0, gz0 = cx * 16 - x0, sy * 16 - y0, cz * 16 - z0
                        sx = slice(max(0, gx0), min(nx, gx0 + 16)); syy = slice(max(0, gy0), min(ny, gy0 + 16)); sz = slice(max(0, gz0), min(nz, gz0 + 16))
                        if sx.start >= sx.stop or syy.start >= syy.stop or sz.start >= sz.stop: continue
                        getattr(self, name)[sx, syy, sz] = block[sx.start - gx0:sx.stop - gx0, syy.start - gy0:syy.stop - gy0, sz.start - gz0:sz.stop - gz0]
        self.shape = (nx, ny, nz)

    def S(self, x, y, z):
        i, j, k = x - self.x0, y - self.y0, z - self.z0
        if not (0 <= i < self.shape[0] and 0 <= k < self.shape[2]): return True
        if j < 0: return False
        if j >= self.shape[1]: return False
        return self.solid[i, j, k]

    def get(self, arr, x, y, z):
        i, j, k = x - self.x0, y - self.y0, z - self.z0
        if not (0 <= i < self.shape[0] and 0 <= j < self.shape[1] and 0 <= k < self.shape[2]): return False
        return arr[i, j, k]

    def stand(self, x, y, z):
        """Feet at y: room for a player, and something under them (ground, water or a ladder)."""
        if self.S(x, y, z) or self.S(x, y + 1, z): return False
        return self.S(x, y - 1, z) or self.get(self.water, x, y, z) or self.get(self.climb, x, y, z)

def bfs(g, start, ymin):
    seen = {start}; q = collections.deque([start])
    while q:
        x, y, z = q.popleft()
        moves = []
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx_, nz_ = x + dx, z + dz
            if g.stand(nx_, y, nz_): moves.append((nx_, y, nz_))
            # step up a block (not onto a fence or wall), with headroom above you
            elif g.S(nx_, y, nz_) and not g.get(g.tall, nx_, y, nz_) and not g.S(x, y + 2, z) and g.stand(nx_, y + 1, nz_):
                moves.append((nx_, y + 1, nz_))
            elif not g.S(nx_, y, nz_) and not g.S(nx_, y + 1, nz_):
                for d in range(1, 7):                                    # walk off and drop
                    yy = y - d
                    if yy < ymin: break
                    if g.S(nx_, yy, nz_): break
                    if g.stand(nx_, yy, nz_): moves.append((nx_, yy, nz_)); break
        # climb: ladders / vines / water carry you up
        if (g.get(g.climb, x, y, z) or g.get(g.water, x, y, z)) and g.stand(x, y + 1, z): moves.append((x, y + 1, z))
        if (g.get(g.water, x, y, z) or g.get(g.climb, x, y, z)) and g.stand(x, y - 1, z): moves.append((x, y - 1, z))
        for m in moves:
            if m not in seen: seen.add(m); q.append(m)
    return seen

def P(s): return [float(v) for v in str(s).split()[:3]]

def audit(mid):
    y = yaml.safe_load(open(f'/home/claude/tgm/maps/{mid}/map.yml'))
    hills = y.get('hills') or []
    if not hills: return None
    w = World(f'/home/claude/tgm/maps/{mid}')
    spawns = {t: P(d['spawn']) for t, d in y['teams'].items()}
    pts = [s for s in spawns.values()] + [P(h['corner-1']) for h in hills] + [P(h['corner-2']) for h in hills]
    x0 = int(min(p[0] for p in pts)) - 40; x1 = int(max(p[0] for p in pts)) + 40
    z0 = int(min(p[2] for p in pts)) - 40; z1 = int(max(p[2] for p in pts)) + 40
    ys = [p[1] for p in pts]; y0 = max(-64, int(min(ys)) - 25); y1 = int(max(ys)) + 40
    g = Grid(w, x0, x1, y0, y1 + 70, z0, z1)
    reach = {}
    for t, s in spawns.items():
        sx, sy, sz = math.floor(s[0]), math.floor(s[1]), math.floor(s[2])
        start = None
        for dy in (0, 1, -1, 2, -2):
            if g.stand(sx, sy + dy, sz): start = (sx, sy + dy, sz); break
        reach[t] = bfs(g, start, y0) if start else set()
    out = []
    for h in hills:
        a, b = P(h['corner-1']), P(h['corner-2'])
        hx0, hx1 = int(min(a[0], b[0])), int(max(a[0], b[0])); hz0, hz1 = int(min(a[2], b[2])), int(max(a[2], b[2])); ym = int(min(a[1], b[1]))
        cols = sky = 0
        for x in range(hx0, hx1 + 1):
            for z in range(hz0, hz1 + 1):
                cols += 1
                if not any(g.S(x, yy, z) for yy in range(ym, ym + 64)): sky += 1
        zone = {(x, yy, z) for x in range(hx0 - 1, hx1 + 2) for z in range(hz0 - 1, hz1 + 2) for yy in range(ym - 1, ym + 3)}
        r = {t: len(zone & reach[t]) > 0 for t in reach}
        out.append(dict(name=h['name'], sky=round(sky / cols, 2), reach=r, y=ym, box=[hx0, hz0, hx1, hz1]))
    return out

if __name__ == '__main__':
    ids = [l.strip().split('|')[1] for f in ('ids.txt', 'ids2.txt') for l in open('/home/claude/tgm/' + f) if '|' in l]
    if len(sys.argv) > 1: ids = sys.argv[1:]
    res = {}
    for mid in ids:
        r = audit(mid)
        if r is None: continue
        res[mid] = r
        for h in r:
            bad = h['sky'] < 0.6 or not all(h['reach'].values())
            print(('BAD ' if bad else 'ok  ') + f"{mid:14} {h['name']:22} sky {h['sky']:.2f} reach {h['reach']} y{h['y']} box {h['box']}", flush=True)
    json.dump(res, open('/tmp/hillreach.json', 'w'))
