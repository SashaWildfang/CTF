import sys, yaml, math
sys.path.insert(0, '/home/claude/tgm/tools')
from world import World, solid
WATER = {'water', 'lava', 'bubble_column'}
mid = sys.argv[1]
y = yaml.safe_load(open(f'/home/claude/tgm/maps/{mid}/map.yml'))
P = lambda s: [float(v) for v in str(s).split()[:3]]
spawns = [P(t['spawn']) for t in y['teams'].values()]
mx = sum(s[0] for s in spawns) / 2; mz = sum(s[2] for s in spawns) / 2
sy = min(s[1] for s in spawns)
half = math.hypot(spawns[0][0] - spawns[1][0], spawns[0][2] - spawns[1][2]) / 2
w = World(f'/home/claude/tgm/maps/{mid}')
def ok(x, yy, z):
    f = w.block(x, yy - 1, z)
    return solid(f) and f not in WATER and not solid(w.block(x, yy, z)) and not solid(w.block(x, yy + 1, z)) and w.block(x, yy, z) not in WATER
found = []
R = int(half * 0.9)
for cx in range(int(mx) - R, int(mx) + R + 1, 2):
    for cz in range(int(mz) - R, int(mz) + R + 1, 2):
        for yy in range(int(sy) - 12, int(sy) + 10):
            if not ok(cx, yy, cz): continue
            cov = sum(ok(x, yy, z) for x in range(cx - 2, cx + 3) for z in range(cz - 2, cz + 3)) / 25
            if cov < 0.88: continue
            sky = all(not solid(w.block(cx, t, cz)) for t in range(yy, yy + 60))
            near = min(math.hypot(cx - s[0], cz - s[2]) for s in spawns)
            if near < 15: continue
            d0 = math.hypot(cx - spawns[0][0], cz - spawns[0][2]); d1 = math.hypot(cx - spawns[1][0], cz - spawns[1][2])
            found.append((round(abs(d0 - d1), 1), round(math.hypot(cx - mx, cz - mz), 1), cx, yy, cz, cov, sky))
found.sort(key=lambda f: (not f[6], f[1]))
print(mid, 'mid', (round(mx), round(mz)), 'spawns', [(round(s[0]), round(s[1]), round(s[2])) for s in spawns])
for f in found[:25]: print(f)
