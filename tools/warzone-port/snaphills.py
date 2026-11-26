"""Original (builder-made) hills: snap the zone to its real floor and keep the best 5x5 inside it (the one with the most
floor, headroom and open sky, nearest the middle). python3 snaphills.py MAPID... (writes map.yml)"""
import sys, math, re, yaml
sys.path.insert(0, '/home/claude/tgm/tools')
from world import World
from hillreach import Grid, P

def search(g, opaque, x0, x1, z0, z1, size, band, y0):
    best = None
    cxm, czm = (x0 + x1) / 2, (z0 + z1) / 2
    for sx in range(x0, x1 - size + 2):
        for sz in range(z0, z1 - size + 2):
            for fy in range(*band):
                cells = [(x, z) for x in range(sx, sx + size) for z in range(sz, sz + size)]
                stand = sum(g.S(x, fy - 1, z) and not g.S(x, fy, z) and not g.S(x, fy + 1, z) for x, z in cells) / len(cells)
                if stand < 0.7: continue
                sky = sum(not opaque[x - g.x0, fy - g.y0:fy - g.y0 + 12, z - g.z0].any() for x, z in cells) / len(cells)
                off = math.hypot(sx + size / 2 - cxm - 0.5, sz + size / 2 - czm - 0.5)
                score = stand * 3 + sky * 2 - off * 0.05 - abs(fy - y0) * 0.02
                if best is None or score > best[0]: best = (score, sx, sz, fy, stand, sky)
    return best

def snap(mid):
    path = f'/home/claude/tgm/maps/{mid}/map.yml'
    text = open(path).read(); y = yaml.safe_load(text)
    w = World(f'/home/claude/tgm/maps/{mid}')
    out = []
    for h in y.get('hills') or []:
        a, b = P(h['corner-1']), P(h['corner-2'])
        x0, x1 = int(min(a[0], b[0])), int(max(a[0], b[0])); z0, z1 = int(min(a[2], b[2])), int(max(a[2], b[2]))
        y0, y1 = int(min(a[1], b[1])), int(max(a[1], b[1]))
        g = Grid(w, x0 - 3, x1 + 3, y0 - 6, y1 + 30, z0 - 3, z1 + 3)
        opaque = g.solid & ~g.clear
        best = None
        size = min(5, x1 - x0 + 1, z1 - z0 + 1)
        for band in ((y0 - 2, min(y1, y0 + 4) + 2), (y0 - 2, y1 + 4)):   # near the bottom first; else anywhere in the zone
            best = search(g, opaque, x0, x1, z0, z1, size, band, y0)
            if best: break
        if best is None:
            print(f'  {mid} {h["name"]}: no floor found in {x0},{y0},{z0}..{x1},{y1},{z1}; left as is'); out.append(h); continue
        _, sx, sz, fy, stand, sky = best
        nh = dict(h); nh['corner-1'] = f'{sx} {fy} {sz}'; nh['corner-2'] = f'{sx + size - 1} {fy + 3} {sz + size - 1}'; nh.pop('beacon', None)
        print(f'  {mid} {h["name"]}: {x0},{y0},{z0}..{x1},{y1},{z1} -> {sx},{fy},{sz} {size}x{size} (floor {stand:.2f}, open {sky:.2f})')
        out.append(nh)
    block = 'hills:\n' + ''.join(f'  - name: "{h["name"]}"\n    corner-1: "{h["corner-1"]}"\n    corner-2: "{h["corner-2"]}"\n' for h in out)
    text = re.sub(r'hills:\n(?:  .*\n)+', block, text, count=1)
    open(path, 'w').write(text)

for m in sys.argv[1:]: snap(m)
