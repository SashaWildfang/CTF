"""Read blocks from a 26.x map folder (maps/<id>/dimensions/minecraft/overworld/region)."""
import os, struct, zlib, io, math
import nbtlib

class World:
    def __init__(self, root):
        self.dir = os.path.join(root, 'dimensions/minecraft/overworld/region')
        self.cache = {}
        self.regions = {}

    def _region(self, rx, rz):
        key = (rx, rz)
        if key not in self.regions:
            p = os.path.join(self.dir, f'r.{rx}.{rz}.mca')
            self.regions[key] = open(p, 'rb').read() if os.path.exists(p) else None
        return self.regions[key]

    def chunk(self, cx, cz):
        key = (cx, cz)
        if key in self.cache: return self.cache[key]
        data = self._region(cx >> 5, cz >> 5); res = None
        if data:
            i = 4 * ((cx & 31) + (cz & 31) * 32)
            off = (data[i] << 16 | data[i+1] << 8 | data[i+2]) * 4096
            if off:
                length = struct.unpack('>i', data[off:off+4])[0]; comp = data[off+4]
                raw = data[off+5:off+4+length]
                raw = zlib.decompress(raw) if comp == 2 else raw
                tag = nbtlib.File.parse(io.BytesIO(raw))
                res = {}
                for sec in tag.get('sections', []):
                    bs = sec.get('block_states')
                    if bs is None: continue
                    pal = [str(p['Name']).replace('minecraft:', '') + (('[' + ','.join(f'{k}={v}' for k, v in p['Properties'].items()) + ']') if 'Properties' in p else '') for p in bs['palette']]
                    res[int(sec['Y'])] = (pal, [int(x) & 0xFFFFFFFFFFFFFFFF for x in bs.get('data', [])])
                res = {'sections': res, 'be': [dict(b) for b in tag.get('block_entities', [])]}
        self.cache[key] = res
        return res

    def block(self, x, y, z, props=False):
        c = self.chunk(x >> 4, z >> 4)
        if not c: return 'air'
        s = c['sections'].get(y >> 4)
        if not s: return 'air'
        pal, data = s
        if len(pal) == 1: n = pal[0]
        else:
            bits = max(4, math.ceil(math.log2(len(pal))))
            per = 64 // bits
            idx = ((y & 15) * 16 + (z & 15)) * 16 + (x & 15)
            v = (data[idx // per] >> ((idx % per) * bits)) & ((1 << bits) - 1)
            n = pal[v] if v < len(pal) else 'air'
        return n if props else n.split('[')[0]

    def chunks(self):
        out = []
        for f in os.listdir(self.dir):
            _, rx, rz, _ = f.split('.')
            data = self._region(int(rx), int(rz))
            for i in range(1024):
                if data[4*i:4*i+3] != b'\0\0\0':
                    out.append((int(rx) * 32 + (i & 31), int(rz) * 32 + (i >> 5)))
        return out

    def block_entities(self):
        for cx, cz in self.chunks():
            c = self.chunk(cx, cz)
            if c:
                for be in c['be']: yield be

    def find(self, names, box=None):
        """Every block whose name is in names (in box (x1,y1,z1,x2,y2,z2) if given)."""
        names = set(names)
        found = []
        for cx, cz in self.chunks():
            c = self.chunk(cx, cz)
            if not c: continue
            for sy, (pal, data) in c['sections'].items():
                if not any(p.split('[')[0] in names for p in pal): continue
                for y in range(sy * 16, sy * 16 + 16):
                    for z in range(cz * 16, cz * 16 + 16):
                        for x in range(cx * 16, cx * 16 + 16):
                            if box and not (box[0] <= x <= box[3] and box[1] <= y <= box[4] and box[2] <= z <= box[5]): continue
                            if self.block(x, y, z) in names: found.append((x, y, z))
        return found

SOLID_SKIP = {'air', 'cave_air', 'void_air', 'water', 'lava', 'short_grass', 'tall_grass', 'fern', 'snow', 'torch', 'wall_torch', 'light'}
def solid(name):
    return name not in SOLID_SKIP and not any(k in name for k in ('sign', 'banner', 'flower', 'button', 'pressure_plate', 'carpet', 'rail', 'vine', 'sapling', 'dandelion', 'poppy', 'tulip', 'orchid', 'allium', 'bluet', 'daisy', 'lily', 'torch', 'lantern', 'head', 'skull', 'pot'))
