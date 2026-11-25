import sys, yaml, json, re, math
sys.path.insert(0, '/home/claude/tgm/tools')
from world import World, solid
WATER = {'water', 'lava', 'bubble_column'}
ids = [l.strip().split('|')[1] for f in ('ids.txt', 'ids2.txt') for l in open('/home/claude/tgm/' + f) if l.strip()]
write = '--write' in sys.argv
R = 6 if '--wide' in sys.argv else 4
only = [a for a in sys.argv[1:] if not a.startswith('--')]
if only: ids = only
def P(s): return [float(v) for v in str(s).split()[:3]]
report = []
for mid in ids:
    path = f'/home/claude/tgm/maps/{mid}/map.yml'
    text = open(path).read(); y = yaml.safe_load(text)
    hills = y.get('hills') or []
    if not hills: continue
    w = World(f'/home/claude/tgm/maps/{mid}')
    spawns = [P(t['spawn']) for t in y['teams'].values()]
    flags = [P(t['flag']) for t in y['teams'].values() if t.get('flag')]
    def cellok(x, yy, z):
        f = w.block(x, yy - 1, z)
        return solid(f) and f not in WATER and not solid(w.block(x, yy, z)) and not solid(w.block(x, yy + 1, z)) and w.block(x, yy, z) not in WATER
    def evaluate(x0, x1, z0, z1, yy, beacon):
        cells = ok = 0
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                cells += 1; ok += cellok(x, yy, z)
        cov = ok / cells
        bx, bz = beacon
        # the beacon goes in the floor at yy-1: its spot must be floor, the beam needs the column clear
        sky = all(not solid(w.block(bx, t, bz)) for t in range(yy, min(yy + 80, 319)))
        cx, cz = (x0 + x1 + 1) / 2, (z0 + z1 + 1) / 2
        half = min(x1 - x0 + 1, z1 - z0 + 1) / 2; out = max(1.6, min(2.5, half - .5))
        label = any(not solid(w.block(math.floor(cx + math.cos(a) * out), yy + 2, math.floor(cz + math.sin(a) * out)))
                    for a in [k * math.pi / 4 for k in range(8)])
        near = min(math.hypot(cx - s[0], cz - s[2]) for s in spawns)
        return cov, sky, label, near
    out_hills = []
    for h in hills:
        a, b = P(h['corner-1']), P(h['corner-2'])
        x0, x1 = int(min(a[0], b[0])), int(max(a[0], b[0])); z0, z1 = int(min(a[2], b[2])), int(max(a[2], b[2]))
        y0, y1 = int(min(a[1], b[1])), int(max(a[1], b[1]))
        hb = P(h['beacon']) if h.get('beacon') else None
        bcx, bcz = (int(hb[0]), int(hb[2])) if hb else ((x0 + x1) // 2, (z0 + z1) // 2)
        cov, sky, label, near = evaluate(x0, x1, z0, z1, y0, (bcx, bcz))
        if cov >= 0.8 and label:
            out_hills.append(h); report.append((mid, h['name'], 'kept', cov, sky)); continue
        shapes = [(x0, x1, z0, z1)]
        if x1 - x0 > 4 or z1 - z0 > 4:
            mx, mz = (x0 + x1) // 2, (z0 + z1) // 2
            shapes.append((mx - 2, mx + 2, mz - 2, mz + 2))
        best = None
        ylo, yhi = y0 - 6, max(y1, y0) + 8
        for (sx0, sx1, sz0, sz1) in shapes:
            for dx in range(-R, R + 1):
                for dz in range(-R, R + 1):
                    for yy in range(ylo, yhi + 1):
                        bx = (bcx + dx) if hb and (sx0, sx1) == (x0, x1) else (sx0 + sx1) // 2 + dx
                        bz = (bcz + dz) if hb and (sz0, sz1) == (z0, z1) else (sz0 + sz1) // 2 + dz
                        if not cellok(bx, yy, bz): continue
                        c, s, l, n = evaluate(sx0 + dx, sx1 + dx, sz0 + dz, sz1 + dz, yy, (bx, bz))
                        if c < 0.8 or not l or n < 8: continue
                        score = c * 10 + (2 if s else 0) - 0.3 * (abs(dx) + abs(dz)) - 0.08 * abs(yy - y0) - (1 if (sx0, sx1) != (x0, x1) else 0)
                        if best is None or score > best[0]: best = (score, sx0 + dx, sx1 + dx, sz0 + dz, sz1 + dz, yy, bx, bz, c, s)
        if best is None:
            report.append((mid, h['name'], 'DROPPED', cov, sky)); continue
        _, nx0, nx1, nz0, nz1, ny, bx, bz, c, s = best
        nh = {'name': h['name'], 'corner-1': f"{nx0} {ny} {nz0}", 'corner-2': f"{nx1} {ny + 3} {nz1}"}
        if hb: nh['beacon'] = f"{bx} {ny - 1} {bz}"
        out_hills.append(nh)
        report.append((mid, h['name'], f'moved {nx0},{ny},{nz0}..{nx1},{nz1} (was y{y0})', c, s))
    if write and out_hills != hills:
        lines = ['hills:']
        for h in out_hills:
            lines.append(f'  - name: "{h["name"]}"')
            lines.append(f'    corner-1: "{h["corner-1"]}"')
            lines.append(f'    corner-2: "{h["corner-2"]}"')
            if h.get('beacon'): lines.append(f'    beacon: "{h["beacon"]}"')
        block = '\n'.join(lines) + '\n'
        new = re.sub(r'^hills:\n(?:[ \-].*\n)*', block if out_hills else '', text, count=1, flags=re.M)
        if not out_hills:
            new = re.sub(r'^modes: \[(.*)\]$', lambda m: 'modes: [' + ', '.join(x for x in [v.strip() for v in m.group(1).split(',')] if x != 'koth') + ']', new, count=1, flags=re.M)
        open(path, 'w').write(new)
for r in report:
    if r[2] != 'kept': print(f"{r[0]:14} {r[1]:20} {r[2]}  stand {r[3]:.2f} sky {r[4]}")
print('kept', sum(1 for r in report if r[2] == 'kept'), 'of', len(report))
