"""Top-down render of a map with its map.yml markers: python3 render.py <id>"""
import sys, os, yaml, colorsys, hashlib
sys.path.insert(0, os.path.dirname(__file__))
from world import World
from PIL import Image, ImageDraw
KNOWN = {'grass_block': (95,150,60), 'water': (50,90,200), 'lava': (230,110,20), 'sand': (220,205,150), 'sandstone': (215,200,140), 'snow': (240,240,250),
         'snow_block': (240,240,250), 'stone': (120,120,120), 'cobblestone': (110,110,110), 'dirt': (130,95,60), 'oak_leaves': (60,120,40), 'spruce_leaves': (50,90,50),
         'quartz_block': (235,230,225), 'obsidian': (20,10,30), 'ice': (150,180,250), 'packed_ice': (140,170,240), 'gold_block': (250,210,50)}
def colour(name):
    if name in KNOWN: return KNOWN[name]
    for k in ('wool', 'concrete', 'terracotta', 'glass'):
        if k in name:
            c = name.replace('_' + k, '').replace('stained_', '')
            pal = {'red': (180,40,40), 'blue': (50,60,180), 'white': (230,230,230), 'black': (30,30,30), 'yellow': (240,200,40), 'lime': (120,200,40), 'green': (70,110,30),
                   'orange': (230,120,30), 'purple': (120,50,170), 'magenta': (190,70,180), 'cyan': (30,140,150), 'pink': (240,140,170), 'light_blue': (90,170,220),
                   'gray': (70,70,70), 'light_gray': (150,150,150), 'brown': (110,70,40)}
            if c in pal: return pal[c]
    if 'leaves' in name: return (60,120,50)
    if 'log' in name or 'planks' in name or 'wood' in name: return (140,100,60)
    if 'brick' in name: return (140,130,125)
    h = int(hashlib.md5(name.encode()).hexdigest()[:6], 16)
    r, g, b = colorsys.hsv_to_rgb((h % 360) / 360, 0.25, 0.55 + (h % 40) / 100)
    return (int(r*255), int(g*255), int(b*255))
def render(mid, scale=4, out=None):
    root = f'/home/claude/tgm/maps/{mid}'
    y = yaml.safe_load(open(root + '/map.yml'))
    w = World(root)
    cs = [c for c in w.chunks() if w.chunk(*c) and any(p != ['air'] for p, d in w.chunk(*c)['sections'].values())]
    x0 = min(c[0] for c in cs) * 16; x1 = max(c[0] for c in cs) * 16 + 15; z0 = min(c[1] for c in cs) * 16; z1 = max(c[1] for c in cs) * 16 + 15
    tops = {}
    for cx, cz in cs:
        c = w.chunk(cx, cz); secs = sorted(c['sections'], reverse=True)
        for z in range(cz*16, cz*16+16):
            for x in range(cx*16, cx*16+16):
                for sy in secs:
                    if c['sections'][sy][0] == ['air']: continue
                    hit = None
                    for yy in range(sy*16+15, sy*16-1, -1):
                        n = w.block(x, yy, z)
                        if n not in ('air', 'cave_air', 'barrier', 'light', 'structure_void'): hit = (yy, n); break
                    if hit: tops[(x, z)] = hit; break
    hs = [t[0] for t in tops.values()]; lo, hi = min(hs), max(hs)
    img = Image.new('RGB', ((x1-x0+1)*scale, (z1-z0+1)*scale), (25,25,35)); d = ImageDraw.Draw(img)
    for (x, z), (yy, n) in tops.items():
        c = colour(n); f = 0.6 + 0.4 * (yy - lo) / max(1, hi - lo)
        d.rectangle([(x-x0)*scale, (z-z0)*scale, (x-x0+1)*scale-1, (z-z0+1)*scale-1], fill=tuple(int(v*f) for v in c))
    P = lambda x, z: ((x - x0 + 0.5) * scale, (z - z0 + 0.5) * scale)
    def mark(x, z, col, label, r=6):
        px, pz = P(x, z); d.ellipse([px-r, pz-r, px+r, pz+r], outline=col, width=3); d.text((px+r+2, pz-6), label, fill=col)
    tc = {'red': (255,60,60), 'blue': (80,140,255)}
    for t, td in y.get('teams', {}).items():
        sv = [float(v) for v in td['spawn'].split()]; mark(sv[0], sv[2], tc[t], 'SPAWN', 8)
        if 'flag' in td:
            fv = [int(v) for v in td['flag'].split()]; mark(fv[0], fv[2], tc[t], f'FLAG y{fv[1]}', 5)
        for k in ('wools', 'monument-blocks'):
            for s in td.get(k, []):
                v = [int(q) for q in s.split()]; d.rectangle([*P(v[0]-0.5, v[2]-0.5), *P(v[0]+0.5, v[2]+0.5)], outline=tc[t])
    for h in y.get('hills', []):
        a = [int(q) for q in h['corner-1'].split()]; b = [int(q) for q in h['corner-2'].split()]
        d.rectangle([*P(min(a[0],b[0])-0.5, min(a[2],b[2])-0.5), *P(max(a[0],b[0])+0.5, max(a[2],b[2])+0.5)], outline=(255,255,0), width=2)
        d.text(P(a[0], a[2]), h['name'] + f" y{a[1]}", fill=(255,255,0))
    for g in y.get('ctw-wools', []):
        v = [int(q) for q in g['podium'].split()]; mark(v[0], v[2], tc[g['team']], 'POD ' + g['name'], 4)
        for s in g['sources']:
            v = [int(q) for q in s.split()]; d.rectangle([*P(v[0]-0.5, v[2]-0.5), *P(v[0]+0.5, v[2]+0.5)], fill=(255,0,255))
    d.text((4, 4), f"{y['name']} {y['modes']} y{lo}-{hi}", fill=(255,255,255))
    if max(img.size) > 1600:
        k = 1600 / max(img.size); img = img.resize((int(img.size[0]*k), int(img.size[1]*k)))
    img.save(out or f'/tmp/tgmrender/{mid}.png')
if __name__ == '__main__':
    os.makedirs('/tmp/tgmrender', exist_ok=True)
    for m in sys.argv[1:]: render(m); print('rendered', m)
