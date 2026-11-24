"""Drop chunks that are empty, or far (> 12 chunks) from the map's spawns, from a map's region/entities/poi files."""
import sys, os, yaml
sys.path.insert(0, os.path.dirname(__file__))
from world import World
def prune(mid, margin=12):
    root = f'/home/claude/tgm/maps/{mid}'
    y = yaml.safe_load(open(root + '/map.yml'))
    pts = [[float(v) for v in t['spawn'].split()] for t in y['teams'].values()]
    x0, x1 = min(p[0] for p in pts) // 16 - margin, max(p[0] for p in pts) // 16 + margin
    z0, z1 = min(p[2] for p in pts) // 16 - margin, max(p[2] for p in pts) // 16 + margin
    w = World(root)
    keep = {c for c in w.chunks() if x0 <= c[0] <= x1 and z0 <= c[1] <= z1 and w.chunk(*c) and any(p != ['air'] for p, d in w.chunk(*c)['sections'].values())}
    dropped = 0
    for kind in ('region', 'entities', 'poi'):
        d = f'{root}/dimensions/minecraft/overworld/{kind}'
        if not os.path.isdir(d): continue
        for f in os.listdir(d):
            _, rx, rz, _ = f.split('.'); rx, rz = int(rx), int(rz)
            data = bytearray(open(f'{d}/{f}', 'rb').read())
            if len(data) < 8192: os.remove(f'{d}/{f}'); continue
            live = 0
            for i in range(1024):
                c = (rx * 32 + (i & 31), rz * 32 + (i >> 5))
                if data[4*i:4*i+4] == b'\0\0\0\0': continue
                if c in keep: live += 1; continue
                data[4*i:4*i+4] = b'\0\0\0\0'; data[4096+4*i:4096+4*i+4] = b'\0\0\0\0'; dropped += (kind == 'region')
            if live == 0: os.remove(f'{d}/{f}')
            else:
                # rewrite compactly: copy only live sectors
                out = bytearray(8192); sector = 2
                for i in range(1024):
                    e = data[4*i:4*i+4]
                    if e == b'\0\0\0\0': continue
                    off = (e[0] << 16 | e[1] << 8 | e[2]) * 4096; n = e[3]
                    out[4*i:4*i+4] = bytes([(sector >> 16) & 255, (sector >> 8) & 255, sector & 255, n])
                    out[4096+4*i:4096+4*i+4] = data[4096+4*i:4096+4*i+4]
                    out += data[off:off + n * 4096]; sector += n
                open(f'{d}/{f}', 'wb').write(out)
    return len(keep), dropped
if __name__ == '__main__':
    for m in sys.argv[1:]: print(m, 'kept %d chunks, dropped %d' % prune(m))
