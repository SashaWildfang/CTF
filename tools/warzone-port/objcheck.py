"""Every objective point of a map (flags, wools, monument blocks, wool sources/podiums, hill corners, spawns) must sit in
a chunk that exists. python3 objcheck.py ID... -> lists points with no chunk."""
import sys, yaml
sys.path.insert(0, '/home/claude/tgm/tools')
from world import World
def pts(y):
    out = []
    for t, d in y['teams'].items():
        out.append((f'{t} spawn', d['spawn']))
        if d.get('flag'): out.append((f'{t} flag', d['flag']))
        for k in ('wools', 'monument-blocks'):
            for q in d.get(k) or []: out.append((f'{t} {k}', q))
    for g in y.get('ctw-wools') or []:
        out.append((f'podium {g["name"]}', g['podium']))
        for q in g['sources']: out.append((f'source {g["name"]}', q))
    for h in y.get('hills') or []: out.append((f'hill {h["name"]}', h['corner-1']))
    return out
bad_maps = 0
for mid in sys.argv[1:]:
    y = yaml.safe_load(open(f'/home/claude/tgm/maps/{mid}/map.yml')); w = World(f'/home/claude/tgm/maps/{mid}')
    have = set(w.chunks())
    miss = []
    for name, p in pts(y):
        v = [float(x) for x in str(p).split()[:3]]
        c = (int(v[0]) >> 4, int(v[2]) >> 4)
        if c not in have or not w.chunk(*c): miss.append(name)
    if miss: bad_maps += 1; print(f'{mid}: MISSING {len(miss)}: {sorted(set(miss))[:8]}')
print('maps with missing objectives:', bad_maps)
