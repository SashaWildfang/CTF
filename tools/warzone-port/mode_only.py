"""Tags a ported map's own objectives with the mode they belong to (map.yml mode-only:), so FractalCTF clears them
in every other mode: wool towers (dtw), monuments (dtm), the beacons that mark towers, monuments, wool rooms (ctw),
hills (koth) and flags (ctf), and the map's own flag banners (ctf). Beacons are swapped for the floor round them."""
import sys, math, re, yaml
sys.path.insert(0, '/home/claude/tgm/tools')
from world import World

P = lambda s: [int(math.floor(float(v))) for v in str(s).split()[:3]]
SOLID_SKIP = ('air', 'glass', 'beacon', 'water', 'lava', 'banner', 'torch', 'carpet')

def floor_block(w, x, y, z):
    counts = {}
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
        b = w.block(x + dx, y, z + dz, props=True)
        if any(k in b for k in SOLID_SKIP): continue
        counts[b] = counts.get(b, 0) + 1
    if not counts: return 'minecraft:air'          # a beacon standing on its pyramid: the pyramid stays as a platform
    best = max(counts, key=counts.get)
    return best if best.startswith('minecraft:') else 'minecraft:' + best

def tag(mid):
    path = f'/home/claude/tgm/maps/{mid}/map.yml'
    text = open(path).read()
    y = yaml.safe_load(text)
    w = World(f'/home/claude/tgm/maps/{mid}')
    out = {}
    add = lambda mode, line: out.setdefault(mode, []).append(line)
    teams = y.get('teams') or {}
    wools = [P(p) for t in teams.values() for p in (t.get('wools') or [])]
    monuments = [P(p) for t in teams.values() for p in (t.get('monument-blocks') or [])]
    flags = [P(t['flag']) for t in teams.values() if t.get('flag')]
    rooms = [P(s) for g in (y.get('ctw-wools') or []) for s in g['sources']]
    hills = []
    for h in y.get('hills') or []:
        a, b = P(h['corner-1']), P(h['corner-2'])
        hills.append(((a[0] + b[0]) / 2, (a[2] + b[2]) / 2))
    for p in wools: add('dtw', f'{p[0]} {p[1]} {p[2]}')
    for p in monuments: add('dtm', f'{p[0]} {p[1]} {p[2]}')
    for p in flags:
        b = w.block(*p)
        if 'banner' in b: add('ctf', f'{p[0]} {p[1]} {p[2]}')
    near = lambda b, pts, r: any(math.hypot(b[0] - p[0], b[2] - p[-1]) <= r for p in pts)
    for b in w.find(['beacon']):
        line = f'{b[0]} {b[1]} {b[2]} {floor_block(w, *b)}'
        if near(b, wools, 5): add('dtw', line)
        elif near(b, monuments, 5): add('dtm', line)
        elif near(b, rooms, 9): add('ctw', line)
        elif near(b, flags, 4): add('ctf', line)
        elif near(b, [(h[0], 0, h[1]) for h in hills], 8): add('koth', line)
    text = re.sub(r'\n?mode-only:\n(?:  .*\n|    .*\n)*', '\n', text)
    if out:
        block = 'mode-only:\n' + ''.join(f'  {m}:\n' + ''.join(f'    - "{l}"\n' for l in lines) for m, lines in out.items())
        text = text.rstrip('\n') + '\n' + block
    open(path, 'w').write(text)
    return {m: len(v) for m, v in out.items()}

ids = [l.strip().split('|')[1] for f in ('ids.txt', 'ids2.txt') for l in open('/home/claude/tgm/' + f) if '|' in l]
for mid in (sys.argv[1:] or ids):
    print(f'{mid:14}', tag(mid))
