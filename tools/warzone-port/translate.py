"""Translate a TGM map.json + upgraded world into FractalCTF map.yml (with extra modes where they fit)."""
import json, re, sys, math, os, collections
sys.path.insert(0, os.path.dirname(__file__))
from world import World, solid

ROOT = '/home/claude/tgm'

def load_json(p):
    return json.loads(re.sub(r',(\s*[\]}])', r'\1', open(p, encoding='utf-8-sig').read()))

def nums(v):
    if isinstance(v, dict): return [float(v['x']), float(v['y']), float(v['z'])]
    return [float(x) if x.strip() not in ('oo', '-oo') else (300 if not x.strip().startswith('-') else -64) for x in str(v).split(',')]

def box(region):
    """Bounding box (x1,y1,z1,x2,y2,z2) of a TGM region (cuboid, meta union, or dict corners)."""
    if 'regions' in region:
        bs = [box(r) for r in region['regions']]
        return (min(b[0] for b in bs), min(b[1] for b in bs), min(b[2] for b in bs), max(b[3] for b in bs), max(b[4] for b in bs), max(b[5] for b in bs))
    a, b = nums(region['min']), nums(region.get('max', region['min']))
    return (int(min(a[0], b[0])), int(min(a[1], b[1])), int(min(a[2], b[2])), int(max(a[0], b[0])), int(max(a[1], b[1])), int(max(a[2], b[2])))

def team_map(teams):
    ids = [t['id'] for t in teams]
    out = {}
    if 'red' in ids:
        out['red'] = 'red'; other = [i for i in ids if i != 'red'][0]; out[other] = 'blue'
    elif 'blue' in ids:
        out['blue'] = 'blue'; other = [i for i in ids if i != 'blue'][0]; out[other] = 'red'
    else:
        out[ids[0]] = 'red'; out[ids[1]] = 'blue'
    return out

def spot(coords, default_yaw=0):
    v = nums(coords)
    yaw = v[3] if len(v) > 3 else default_yaw
    pitch = v[4] if len(v) > 4 else 0
    f = lambda x: ('%.1f' % x).rstrip('0').rstrip('.') if x != int(x) else str(int(x))
    return f"{f(v[0])} {f(v[1])} {f(v[2])} {f(yaw)} {f(pitch)}", v

def floor_ok(w, x, y, z):
    """Standing at feet y: solid below, two blocks of air-ish above."""
    return solid(w.block(x, y - 1, z)) and not solid(w.block(x, y, z)) and not solid(w.block(x, y + 1, z))

def flat_patch(w, cx, cz, y_lo, y_hi, r=2, search=10, avoid=(), y_pref=None):
    """The best (x, y, z) feet spot near (cx, cz) where a (2r+1)^2 patch is standable at one height."""
    best = None
    for d in range(0, search + 1):
        for dx in range(-d, d + 1):
            for dz in range(-d, d + 1):
                if max(abs(dx), abs(dz)) != d: continue
                x, z = int(cx) + dx, int(cz) + dz
                if any(abs(x - a[0]) <= r + 2 and abs(z - a[2]) <= r + 2 for a in avoid): continue
                ys = list(range(int(y_hi), int(y_lo) - 1, -1))
                if y_pref is not None: ys.sort(key=lambda v: abs(v - y_pref))
                for y in ys:
                    if not floor_ok(w, x, y, z): continue
                    good = sum(1 for ax in range(-r, r + 1) for az in range(-r, r + 1) if floor_ok(w, x + ax, y, z + az))
                    score = good - 0.15 * d
                    if good >= (2 * r + 1) ** 2 * 0.8 and (best is None or score > best[0]):
                        best = (score, x, y, z)
                    break
        if best and best[0] >= (2 * r + 1) ** 2 - 0.15 * d: break
    return None if best is None else best[1:]

def min_y(w):
    lo = 999
    for cx, cz in w.chunks():
        c = w.chunk(cx, cz)
        if not c: continue
        for sy, (pal, data) in c['sections'].items():
            if pal != ['air']: lo = min(lo, sy * 16)
    return lo

def translate(cat_name, mid, extra):
    j = load_json(f'{ROOT}/src/{cat_name}/map.json')
    w = World(f'{ROOT}/maps/{mid}')
    tm = team_map(j['teams'])
    y = {'name': j['name'].replace(' CTF', '').replace(' DTM', '').replace(' TE', ''), 'authors': ['SashaWildfang'],
         'original-authors': [a.get('username') for a in j.get('authors', [])]}
    teams = {'red': {}, 'blue': {}}
    spec = None
    for s in j['spawns']:
        for t in s['teams']:
            if t == 'spectators':
                if spec is None: spec = spot(s['coords'])[0]
            elif t in tm and 'spawn' not in teams[tm[t]]:
                teams[tm[t]]['spawn'], teams[tm[t]]['_v'] = spot(s['coords'])
    modes = []
    notes = []
    # ---- native objectives
    if 'ctf' in j:
        modes.append('ctf')
        for f in j['ctf']['flags']:
            v = nums(f['location']); teams[tm[f['team']]]['flag'] = f"{int(v[0])} {int(v[1])} {int(v[2])}"
    if 'ctw' in j:
        modes.append('ctw')
        goals = []
        chests = [(int(be['x']), int(be['y']), int(be['z']), [str(i.get('id')).replace('minecraft:', '') for i in be.get('Items', [])]) for be in w.block_entities()]
        for g in j['ctw']['wools']:
            colour = g['woolcolor'].replace(' ', '_')
            pod = box(g['region'])
            mat = colour + '_wool'
            src = [f"{x} {yy} {z}" for x, yy, z, items in chests if mat in items]
            if not src:
                blocks = [p for p in w.find([mat]) if (p[0], p[1], p[2]) != (pod[0], pod[1], pod[2])]
                src = [f"{x} {yy} {z}" for x, yy, z in blocks]
            goals.append({'name': g['name'], 'team': tm[g['teams'][0]], 'colour': colour, 'podium': f"{pod[0]} {pod[1]} {pod[2]}", 'sources': src})
            notes.append(f"ctw {g['name']} for {tm[g['teams'][0]]}: {len(src)} sources")
        y['ctw-wools'] = goals
    if 'dtm' in j:
        mons = j['dtm']['monuments']
        mats = set()
        for m in mons:
            for mm in m['materials']: mats.add(mm.replace(' ', '_').lower())
        wool = all(m.endswith('_wool') for m in mats)
        health = collections.Counter()
        for m in mons:
            b = box(m['region'])
            blocks = [p for p in w.find({mm.replace(' ', '_').lower() for mm in m['materials']}, b)]
            team = tm[m['teams'][0]]
            key = 'wools' if wool else 'monument-blocks'
            teams[team].setdefault(key, []).extend(f"{x} {yy} {z}" for x, yy, z in blocks)
            health[team] += int(m.get('health', len(blocks)) or len(blocks))
            notes.append(f"monument {team} {b}: {len(blocks)} blocks of {sorted(mats)} health {m.get('health')}")
        modes.append('dtw' if wool else 'dtm')
        if not wool: y['blocks-to-break'] = min(health.values())
    if 'koth' in j:
        modes.append('koth')
        y['hills'] = []
        for h in j['koth']['hills']:
            b = box(h['region'])
            y['hills'].append({'name': h['name'].replace(' Point', '').replace(' Hill', '') or h['name'], 'corner-1': f"{b[0]} {b[1]} {b[2]}", 'corner-2': f"{b[3]} {b[4]} {b[5]}"})
    # ---- extra modes
    rv, bv = teams['red']['_v'], teams['blue']['_v']
    ys = [rv[1], bv[1]]
    if 'koth' not in modes and 'koth' in extra:
        cx, cz = (rv[0] + bv[0]) / 2, (rv[2] + bv[2]) / 2
        at = extra.get('koth-at')
        if at: cx, cz = at[0], at[1]
        hit = flat_patch(w, cx, cz, min(ys) - 20, max(ys) + 25, r=2, search=extra.get('koth-search', 14), y_pref=extra.get('koth-y', (ys[0] + ys[1]) / 2))
        if hit:
            x, yy, z = hit
            y['hills'] = [{'name': 'Middle', 'corner-1': f"{x-2} {yy} {z-2}", 'corner-2': f"{x+2} {yy+3} {z+2}"}]
            modes.append('koth'); notes.append(f"koth extra at {hit}")
        else: notes.append('koth extra: no flat middle found')
    if 'ctf' not in modes and 'ctf' in extra:
        avoid = [list(map(int, q.split())) for t in teams.values() for q in t.get('monument-blocks', []) + t.get('wools', [])]
        avoid += [list(map(int, q.split())) for g in y.get('ctw-wools', []) for q in g['sources'] + [g['podium']]]
        for frac in [extra.get('flag-at', 0.25), 0.2, 0.3, 0.16, 0.35]:
            found = {}
            for team in ('red', 'blue'):
                other = 'blue' if team == 'red' else 'red'
                sv, ov = teams[team]['_v'], teams[other]['_v']
                d = math.dist((sv[0], sv[2]), (ov[0], ov[2]))
                t = max(frac, min(0.45, 14 / max(d, 1)))
                anchor = [sv[0] + (ov[0] - sv[0]) * t, sv[1], sv[2] + (ov[2] - sv[2]) * t]
                hit = flat_patch(w, anchor[0], anchor[2], anchor[1] - 10, anchor[1] + 8, r=1, search=12, avoid=avoid, y_pref=anchor[1])
                if hit: found[team] = (hit, anchor)
            if len(found) == 2:
                for team, (hit, anchor) in found.items():
                    teams[team]['flag'] = f"{hit[0]} {hit[1]} {hit[2]}"; notes.append(f"ctf flag {team} {hit} at {frac} (anchor {[round(a) for a in anchor]})")
                modes.append('ctf')
                break
        else:
            notes.append('ctf extra: no flag spots')
    y['modes'] = modes
    y['time-limit-minutes'] = extra.get('minutes', 12)
    y['capture-limit'] = 3
    y['koth-points'] = 250
    y['koth-capture-seconds'] = 8
    y['void-y'] = min_y(w) - 6
    if j.get('buildHeight'): y['build-height'] = int(j['buildHeight'])
    y['icon'] = extra.get('icon', 'FILLED_MAP')
    if spec: y['spectator-spawn'] = spec
    for t in teams.values(): t.pop('_v', None)
    y['teams'] = teams
    return y, notes

def dump(y):
    out = []
    order = ['name', 'authors', 'original-authors', 'icon', 'modes', 'time-limit-minutes', 'capture-limit', 'koth-points', 'koth-capture-seconds', 'blocks-to-break', 'build-height', 'void-y', 'spectator-spawn', 'hills', 'ctw-wools', 'teams']
    q = lambda s: json.dumps(s, ensure_ascii=False)
    for k in order:
        if k not in y: continue
        v = y[k]
        if k in ('authors', 'original-authors', 'modes'): out.append(f"{k}: [{', '.join(q(x) for x in v)}]")
        elif k == 'hills':
            out.append('hills:')
            for h in v: out.append(f"  - name: {q(h['name'])}\n    corner-1: {q(h['corner-1'])}\n    corner-2: {q(h['corner-2'])}")
        elif k == 'ctw-wools':
            out.append('ctw-wools:')
            for g in v: out.append(f"  - name: {q(g['name'])}\n    team: {g['team']}\n    colour: {g['colour']}\n    podium: {q(g['podium'])}\n    sources: [{', '.join(q(s) for s in g['sources'])}]")
        elif k == 'teams':
            out.append('teams:')
            for t, d in v.items():
                out.append(f"  {t}:")
                for kk in ('spawn', 'flag'):
                    if kk in d: out.append(f"    {kk}: {q(d[kk])}")
                for kk in ('wools', 'monument-blocks'):
                    if kk in d: out.append(f"    {kk}: [{', '.join(q(s) for s in d[kk])}]")
        elif isinstance(v, str): out.append(f"{k}: {q(v)}")
        else: out.append(f"{k}: {v}")
    return '\n'.join(out) + '\n'
