"""python3 probe.py MAP x z [y] [size]: the best floor for a hill centred on x,z (within 8 of y) and its numbers."""
import sys, math, yaml
sys.path.insert(0, '/home/claude/tgm/tools')
from world import World
from hillreach import Grid, P
mid = sys.argv[1]; cx, cz = int(sys.argv[2]), int(sys.argv[3]); yh = int(sys.argv[4]) if len(sys.argv) > 4 else None
size = int(sys.argv[5]) if len(sys.argv) > 5 else 5
w = World(f'/home/claude/tgm/maps/{mid}'); y = yaml.safe_load(open(f'/home/claude/tgm/maps/{mid}/map.yml'))
spawns = {t: P(d['spawn']) for t, d in y['teams'].items()}
if yh is None: yh = int(sum(s[1] for s in spawns.values()) / len(spawns))
g = Grid(w, cx - 10, cx + 10, yh - 20, yh + 45, cz - 10, cz + 10); op = g.solid & ~g.clear
x0, z0 = cx - size // 2, cz - size // 2
res = []
for fy in range(yh - 12, yh + 13):
    cells = [(x, z) for x in range(x0, x0 + size) for z in range(z0, z0 + size)]
    fl = sum(g.S(x, fy - 1, z) for x, z in cells) / len(cells)
    hd = sum(not g.S(x, fy, z) and not g.S(x, fy + 1, z) for x, z in cells) / len(cells)
    if fl < 0.5 or hd < 0.5: continue
    sky12 = sum(not op[x - g.x0, fy - g.y0:fy - g.y0 + 12, z - g.z0].any() for x, z in cells) / len(cells)
    sky30 = sum(not op[x - g.x0, fy - g.y0:fy - g.y0 + 30, z - g.z0].any() for x, z in cells) / len(cells)
    wat = sum(g.get(g.water, x, fy, z) for x, z in cells)
    ds = {t: round(math.hypot(cx + 0.5 - s[0], cz + 0.5 - s[2])) for t, s in spawns.items()}
    res.append((fy, fl, hd, sky12, sky30, wat, ds))
for r in res: print(f'  y{r[0]}: floor {r[1]:.2f} head {r[2]:.2f} sky12 {r[3]:.2f} sky30 {r[4]:.2f} water {r[5]} spawn-dist {r[6]}')
if not res: print('  no floor here')
