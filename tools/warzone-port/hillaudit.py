import sys, yaml, json
sys.path.insert(0, '/home/claude/tgm/tools')
from world import World, solid
ids = [l.strip().split('|')[1] for f in ('ids.txt', 'ids2.txt') for l in open('/home/claude/tgm/' + f) if l.strip()]
if len(sys.argv) > 1: ids = sys.argv[1:]
def P(s): return [float(v) for v in str(s).split()[:3]]
out = {}
for mid in ids:
    y = yaml.safe_load(open(f'/home/claude/tgm/maps/{mid}/map.yml'))
    hills = y.get('hills') or []
    if not hills: continue
    w = World(f'/home/claude/tgm/maps/{mid}')
    spawns = [P(t['spawn']) for t in y['teams'].values()]
    sx = sum(s[0] for s in spawns) / len(spawns); sz = sum(s[2] for s in spawns) / len(spawns)
    res = []
    for h in hills:
        a, b = P(h['corner-1']), P(h['corner-2'])
        x0, x1 = int(min(a[0], b[0])), int(max(a[0], b[0])); z0, z1 = int(min(a[2], b[2])), int(max(a[2], b[2])); ym = int(min(a[1], b[1]))
        cells = ok = blocked = nofloor = 0
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                cells += 1
                f = solid(w.block(x, ym - 1, z)); h1 = solid(w.block(x, ym, z)); h2 = solid(w.block(x, ym + 1, z))
                if not f: nofloor += 1
                if h1 or h2: blocked += 1
                if f and not h1 and not h2: ok += 1
        cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
        if 'beacon' in h: cx, _, cz = [int(v) for v in P(h['beacon'])]
        sky = all(not solid(w.block(cx, yy, cz)) for yy in range(ym, ym + 60))
        # label: about 2 blocks from the beacon toward the spawns' centre, 2.4 over the floor
        import math
        mx_, mz_ = (x0 + x1 + 1) / 2, (z0 + z1 + 1) / 2
        out_ = max(1.6, min(2.5, min(x1 - x0 + 1, z1 - z0 + 1) / 2 - .5))
        label = any(not solid(w.block(math.floor(mx_ + math.cos(k * math.pi / 4) * out_), ym + 2, math.floor(mz_ + math.sin(k * math.pi / 4) * out_))) for k in range(8))
        res.append(dict(name=h['name'], ok=round(ok / cells, 2), blocked=blocked, nofloor=nofloor, cells=cells, sky=sky, label=label, y=ym, c=(cx, cz)))
    out[mid] = res
    for r in res:
        bad = r['ok'] < 0.75 or not r['label']
        print(('BAD ' if bad else 'ok  ') + f"{mid:14} {r['name']:20} stand {r['ok']:.2f} blocked {r['blocked']}/{r['cells']} nofloor {r['nofloor']} sky {r['sky']} label {r['label']} y{r['y']} at {r['c']}")
json.dump(out, open('/tmp/hillaudit.json', 'w'))
