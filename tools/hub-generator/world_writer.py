"""
world_writer.py: a Minecraft world writer in pure Python (standard library only).

It holds blocks in memory, works out the block states that depend on neighbours (fence, wall, pane and bar
connections, stair corner shapes, block entities), and writes a complete world folder in the layout Minecraft 26.x
uses (the one Paper 26 loads and saves):

    world/level.dat
    world/data/minecraft/world_clocks.dat
    world/dimensions/minecraft/overworld/region/r.X.Z.mca        the blocks (Anvil)
    world/dimensions/minecraft/overworld/entities/r.X.Z.mca      saved entities (the hub's floating labels)
    world/dimensions/minecraft/overworld/data/minecraft/*.dat    void generator, game rules, clock, weather

Block states are written as plain strings: "stone_bricks", "oak_stairs[facing=east,half=top]".
"""
import functools
import gzip
import os
import random
import struct
import time
import zlib
from array import array

DATA_VERSION = 4903          # Minecraft 26.2; newer servers upgrade the data themselves when they load it
VERSION_NAME = '26.2'
MIN_Y, MAX_Y = -64, 319
BIOME = 'minecraft:plains'


# ---------------------------------------------------------------------------------------------------- NBT

class Byte(int): pass
class Short(int): pass
class Long(int): pass
class Float(float): pass
class IntArray(list): pass
class LongArray(list): pass


def _tag(v):
    if isinstance(v, (bool, Byte)): return 1
    if isinstance(v, Short): return 2
    if isinstance(v, Long): return 4
    if isinstance(v, int): return 3
    if isinstance(v, Float): return 5
    if isinstance(v, float): return 6
    if isinstance(v, (bytes, bytearray)): return 7
    if isinstance(v, str): return 8
    if isinstance(v, IntArray): return 11
    if isinstance(v, LongArray): return 12
    if isinstance(v, (list, tuple)): return 9
    if isinstance(v, dict): return 10
    raise TypeError('no NBT tag for %r' % (v,))


def _string(s):
    raw = s.encode('utf-8')
    return struct.pack('>H', len(raw)) + raw


def _write(out, v):
    t = _tag(v)
    if t == 1: out.append(struct.pack('>b', int(v)))
    elif t == 2: out.append(struct.pack('>h', int(v)))
    elif t == 3: out.append(struct.pack('>i', int(v)))
    elif t == 4: out.append(struct.pack('>q', int(v)))
    elif t == 5: out.append(struct.pack('>f', float(v)))
    elif t == 6: out.append(struct.pack('>d', float(v)))
    elif t == 7: out.append(struct.pack('>i', len(v)) + bytes(v))
    elif t == 8: out.append(_string(v))
    elif t == 9:
        out.append(struct.pack('>bi', _tag(v[0]) if v else 0, len(v)))
        for item in v: _write(out, item)
    elif t == 10:
        for key, item in v.items():
            out.append(struct.pack('>b', _tag(item)))
            out.append(_string(key))
            _write(out, item)
        out.append(b'\x00')
    elif t == 11: out.append(struct.pack('>i%di' % len(v), len(v), *v))
    elif t == 12: out.append(struct.pack('>i%dq' % len(v), len(v), *v))


def nbt_bytes(root):
    """A compound as an uncompressed, unnamed-root NBT file."""
    out = [b'\x0a\x00\x00']
    _write(out, root)
    return b''.join(out)


def read_nbt(buf):
    """A small NBT reader, used to check what was written."""
    pos = [0]

    def take(fmt, size):
        value = struct.unpack_from(fmt, buf, pos[0])[0]
        pos[0] += size
        return value

    def string():
        n = take('>H', 2)
        s = buf[pos[0]:pos[0] + n].decode('utf-8')
        pos[0] += n
        return s

    def payload(t):
        if t == 1: return take('>b', 1)
        if t == 2: return take('>h', 2)
        if t == 3: return take('>i', 4)
        if t == 4: return take('>q', 8)
        if t == 5: return take('>f', 4)
        if t == 6: return take('>d', 8)
        if t == 7:
            n = take('>i', 4); pos[0] += n
            return buf[pos[0] - n:pos[0]]
        if t == 8: return string()
        if t == 9:
            et, n = take('>b', 1), take('>i', 4)
            return [payload(et) for _ in range(n)]
        if t == 10:
            d = {}
            while True:
                tt = take('>b', 1)
                if tt == 0: return d
                key = string()
                d[key] = payload(tt)
        if t == 11:
            n = take('>i', 4)
            return [take('>i', 4) for _ in range(n)]
        if t == 12:
            n = take('>i', 4)
            return [take('>q', 8) for _ in range(n)]
        raise ValueError('bad tag %d' % t)

    t = take('>b', 1)
    string()
    return payload(t)


# ---------------------------------------------------------------------------------------------------- block states

@functools.lru_cache(maxsize=None)
def parse_state(state):
    """'oak_stairs[facing=east,half=top]' -> ('oak_stairs', {'facing': 'east', 'half': 'top'})."""
    if '[' not in state: return state, {}
    name, rest = state.split('[', 1)
    props = {}
    for pair in rest.rstrip(']').split(','):
        key, value = pair.split('=')
        props[key] = value
    return name, props


def make_state(name, props):
    if not props: return name
    return name + '[' + ','.join('%s=%s' % (k, props[k]) for k in sorted(props)) + ']'


DIRS = {'north': (0, -1), 'east': (1, 0), 'south': (0, 1), 'west': (-1, 0)}
OPPOSITE = {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east'}
LEFT_OF = {'north': 'west', 'west': 'south', 'south': 'east', 'east': 'north'}     # counter-clockwise

_OPEN_SUFFIX = ('_slab', '_stairs', '_wall', '_fence', '_fence_gate', '_pane', '_leaves', '_carpet', '_trapdoor', '_door',
                '_banner', '_button', '_torch', '_sign', '_candle', '_cluster', '_bud', '_bed', '_head', '_skull', '_sapling',
                '_vines', '_vines_plant', '_bars', '_chain', 'copper_lantern', '_campfire', '_rod', '_pot', '_tulip', '_grass',
                '_dripleaf', '_fan', '_coral', '_plate', '_rail', '_egg', '_anvil')
_OPEN = {'air', 'water', 'lava', 'lantern', 'soul_lantern', 'chain', 'torch', 'vine', 'fern', 'large_fern', 'campfire', 'bell', 'lectern',
         'candle', 'pointed_dripstone', 'ladder', 'light', 'chest', 'ender_chest', 'composter', 'anvil', 'grindstone',
         'brewing_stand', 'enchanting_table', 'scaffolding', 'cobweb', 'azalea', 'flowering_azalea', 'spore_blossom',
         'glow_lichen', 'pink_petals', 'dandelion', 'poppy', 'allium', 'azure_bluet', 'cornflower', 'oxeye_daisy',
         'lily_of_the_valley', 'blue_orchid', 'hanging_roots', 'snow', 'stonecutter', 'hopper', 'cauldron', 'conduit',
         'lily_pad', 'sea_pickle', 'bamboo', 'sugar_cane', 'dead_bush', 'bush', 'moss_carpet', 'pale_moss_carpet',
         'leaf_litter', 'wildflowers', 'structure_void', 'barrier', 'end_portal_frame', 'daylight_detector', 'cake'}


@functools.lru_cache(maxsize=None)
def is_full_cube(state):
    """Whether fences, walls and panes join onto this block (a full block with solid faces)."""
    name, props = parse_state(state)
    if name.endswith('_slab'): return props.get('type') == 'double'
    return name not in _OPEN and not name.endswith(_OPEN_SUFFIX)


def _block_entity_id(name):
    if name.endswith('_banner'): return 'minecraft:banner'
    if name.endswith('_hanging_sign'): return 'minecraft:hanging_sign'
    if name.endswith('_sign'): return 'minecraft:sign'
    if name.endswith(('_head', '_skull')): return 'minecraft:skull'
    if name.endswith('campfire'): return 'minecraft:campfire'
    if name.endswith('shulker_box'): return 'minecraft:shulker_box'
    if name in ('chest', 'barrel', 'bell', 'lectern', 'beacon', 'decorated_pot', 'enchanting_table', 'brewing_stand',
                'hopper', 'furnace', 'blast_furnace', 'smoker', 'jukebox', 'chiseled_bookshelf', 'conduit', 'ender_chest',
                'end_gateway', 'beehive', 'dispenser', 'dropper', 'crafter', 'trapped_chest'):
        return 'minecraft:' + name
    if name == 'bee_nest': return 'minecraft:beehive'
    return None


class World:
    """Blocks by position, kept in 16x16x16 sections of palette ids, plus entities and block entities."""

    def __init__(self):
        self.states = ['air']
        self.ids = {'air': 0}
        self.sections = {}           # (chunk x, section y, chunk z) -> array of 4096 ids
        self.block_entities = {}     # (x, y, z) -> compound
        self.entities = []           # compounds with a 'Pos'
        self._uuid = random.Random(20261122)

    def sid(self, state):
        i = self.ids.get(state)
        if i is None:
            norm = state[10:] if state.startswith('minecraft:') else state
            name = norm.split('[')[0]
            if name.endswith('_leaves') and 'persistent' not in norm:
                n, props = parse_state(norm)
                norm = make_state(n, dict(props, persistent='true'))
            i = self.ids.get(norm)
            if i is None:
                i = len(self.states)
                self.states.append(norm)
                self.ids[norm] = i
            self.ids[state] = i
        return i

    def set(self, x, y, z, state):
        key = (x >> 4, y >> 4, z >> 4)
        a = self.sections.get(key)
        if a is None:
            a = self.sections[key] = array('H', bytes(8192))
        a[((y & 15) << 8) | ((z & 15) << 4) | (x & 15)] = self.sid(state)

    def get(self, x, y, z):
        a = self.sections.get((x >> 4, y >> 4, z >> 4))
        return 'air' if a is None else self.states[a[((y & 15) << 8) | ((z & 15) << 4) | (x & 15)]]

    def name(self, x, y, z):
        return self.get(x, y, z).split('[')[0]

    def air(self, x, y, z):
        a = self.sections.get((x >> 4, y >> 4, z >> 4))
        return a is None or a[((y & 15) << 8) | ((z & 15) << 4) | (x & 15)] == 0

    def setif(self, x, y, z, state):
        """Sets only where there is air."""
        if self.air(x, y, z): self.set(x, y, z, state)

    def fill(self, x0, y0, z0, x1, y1, z1, state):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, state)

    def top(self, x, z, y0=MAX_Y, y1=MIN_Y):
        """The highest block that isn't air in a column, or None."""
        for y in range(y0, y1 - 1, -1):
            if not self.air(x, y, z): return y
        return None

    def _ints(self):
        return IntArray([self._uuid.randint(-2 ** 31, 2 ** 31 - 1) for _ in range(4)])

    def text(self, x, y, z, text, color='white', bold=False, scale=1.0, background=0):
        """A floating label: a text display that always faces the viewer, with no box behind it unless one is given."""
        self.entities.append({
            'id': 'minecraft:text_display', 'Pos': [float(x), float(y), float(z)], 'Rotation': [Float(0), Float(0)],
            'UUID': self._ints(), 'billboard': 'center', 'alignment': 'center', 'line_width': 1000,
            'background': background, 'shadow': Byte(1), 'see_through': Byte(0), 'default_background': Byte(0),
            'view_range': Float(1.0), 'text_opacity': Byte(-1),
            'text': {'text': text, 'color': color, 'bold': Byte(1 if bold else 0)},
            'transformation': {'translation': [Float(0), Float(0), Float(0)], 'left_rotation': [Float(0), Float(0), Float(0), Float(1)],
                               'scale': [Float(scale), Float(scale), Float(scale)],
                               'right_rotation': [Float(0), Float(0), Float(0), Float(1)]}})

    def armor_stand(self, x, y, z, yaw, gear):
        """
        A display stand that nobody can strip or knock over. `gear` maps head, chest, legs, feet, mainhand, offhand
        to an item id, or to (item id, dye colour) for leather.
        """
        equipment = {}
        for slot, item in gear.items():
            name, dye = item if isinstance(item, tuple) else (item, None)
            tag = {'id': 'minecraft:' + name, 'count': 1}
            if dye is not None: tag['components'] = {'minecraft:dyed_color': dye}
            equipment[slot] = tag
        self.entities.append({
            'id': 'minecraft:armor_stand', 'Pos': [float(x), float(y), float(z)], 'Rotation': [Float(yaw), Float(0)],
            'UUID': self._ints(), 'NoGravity': Byte(1), 'Invulnerable': Byte(1), 'ShowArms': Byte(1), 'NoBasePlate': Byte(1),
            'DisabledSlots': 4144959, 'equipment': equipment})

    def sign(self, x, y, z, state, lines, color='black', glow=False):
        """A sign with up to four lines on its front, waxed so it stays as written."""
        self.set(x, y, z, state)
        msgs = (list(lines) + ['', '', '', ''])[:4]
        blank = {'messages': ['', '', '', ''], 'color': 'black', 'has_glowing_text': Byte(0)}
        self.block_entities[(x, y, z)] = {'id': 'minecraft:sign', 'is_waxed': Byte(1), 'back_text': blank,
                                          'front_text': {'messages': msgs, 'color': color, 'has_glowing_text': Byte(1 if glow else 0)}}

    def bounds(self):
        xs = [k[0] for k in self.sections]; ys = [k[1] for k in self.sections]; zs = [k[2] for k in self.sections]
        return (min(xs) * 16, min(ys) * 16, min(zs) * 16, max(xs) * 16 + 15, max(ys) * 16 + 15, max(zs) * 16 + 15)

    def counts(self):
        """How many of each block (by name) the world holds."""
        per_id = {}
        for a in self.sections.values():
            for i in set(a):
                if i: per_id[i] = per_id.get(i, 0) + a.count(i)
        out = {}
        for i, n in per_id.items():
            name = self.states[i].split('[')[0]
            out[name] = out.get(name, 0) + n
        return out


# ---------------------------------------------------------------------------------------------------- neighbour states

def _kind(name):
    if name.endswith('_wall'): return 'wall'
    if name.endswith('_fence'): return 'fence'
    if name.endswith('_pane') or name.endswith('_bars'): return 'pane'
    if name.endswith('_stairs'): return 'stairs'
    if _block_entity_id(name): return 'entity'
    return None


def finalize_states(world):
    """
    Works out every state that depends on the blocks around it, the way the game would when the block was placed:
    which sides fences, walls, panes and bars join on, whether a wall has its post and where it is tall, the corner
    shape of stairs; and gives every block that needs one its block entity. Returns how many blocks it touched.
    """
    kinds = {}
    found = []
    for (cx, sy, cz), a in world.sections.items():
        wanted = set()
        for i in set(a):
            if i not in kinds: kinds[i] = _kind(world.states[i].split('[')[0])
            if kinds[i]: wanted.add(i)
        if not wanted: continue
        for index, i in enumerate(a):
            if i in wanted:
                found.append(((cx << 4) | (index & 15), (sy << 4) | (index >> 8), (cz << 4) | ((index >> 4) & 15), kinds[i]))

    def joins(kind, name, x, y, z, d):
        other = world.get(x + DIRS[d][0], y, z + DIRS[d][1])
        oname, oprops = parse_state(other)
        if oname.endswith('_fence_gate'):
            return oprops.get('facing') in (LEFT_OF[d], OPPOSITE[LEFT_OF[d]])
        if kind == 'fence':
            if oname.endswith('_fence'): return ('nether_brick' in oname) == ('nether_brick' in name)
            return is_full_cube(other)
        if oname.endswith(('_wall', '_pane', '_bars')): return True
        if oname.endswith('_stairs'):                       # the tall back of a stair is a solid face
            return oprops.get('facing') == OPPOSITE[d]
        return is_full_cube(other)

    sides = {}
    for x, y, z, kind in found:
        if kind in ('wall', 'fence', 'pane'):
            name = world.name(x, y, z)
            sides[(x, y, z)] = {d: joins(kind, name, x, y, z, d) for d in DIRS}
    stair_shapes = {}
    for x, y, z, kind in found:
        if kind != 'stairs': continue
        name, props = parse_state(world.get(x, y, z))
        facing, half = props.get('facing', 'north'), props.get('half', 'bottom')

        def stair(d):
            n, p = parse_state(world.get(x + DIRS[d][0], y, z + DIRS[d][1]))
            return p if n.endswith('_stairs') and p.get('half', 'bottom') == half else None

        def free(d):
            p = stair(d)
            return p is None or p.get('facing', 'north') != facing

        shape = 'straight'
        front = stair(facing)
        if front and (front.get('facing', 'north') in ('north', 'south')) != (facing in ('north', 'south')) and free(OPPOSITE[front['facing']]):
            shape = 'outer_left' if front['facing'] == LEFT_OF[facing] else 'outer_right'
        else:
            back = stair(OPPOSITE[facing])
            if back and (back.get('facing', 'north') in ('north', 'south')) != (facing in ('north', 'south')) and free(back['facing']):
                shape = 'inner_left' if back['facing'] == LEFT_OF[facing] else 'inner_right'
        stair_shapes[(x, y, z)] = make_state(name, dict(props, facing=facing, half=half, shape=shape))

    for x, y, z, kind in found:
        state = world.get(x, y, z)
        name, props = parse_state(state)
        if kind == 'stairs':
            world.set(x, y, z, stair_shapes[(x, y, z)])
        elif kind in ('fence', 'pane'):
            world.set(x, y, z, make_state(name, dict(props, **{d: 'true' if on else 'false' for d, on in sides[(x, y, z)].items()})))
        elif kind == 'wall':
            mine = sides[(x, y, z)]
            above = world.get(x, y + 1, z)
            aname = above.split('[')[0]
            above_sides = sides.get((x, y + 1, z)) if aname.endswith('_wall') else None
            covered = is_full_cube(above)
            new = dict(props)
            for d, on in mine.items():
                tall = on and (covered or (above_sides is not None and above_sides[d]))
                new[d] = 'tall' if tall else 'low' if on else 'none'
            line = (mine['north'] and mine['south'] and not mine['east'] and not mine['west']) or \
                   (mine['east'] and mine['west'] and not mine['north'] and not mine['south'])
            post_above = aname != 'air' and not covered and not (above_sides is not None and above_sides == mine)
            new['up'] = 'false' if line and not post_above else 'true'
            world.set(x, y, z, make_state(name, new))
        elif kind == 'entity' and (x, y, z) not in world.block_entities:
            world.block_entities[(x, y, z)] = {'id': _block_entity_id(name)}
    return len(found)


# ---------------------------------------------------------------------------------------------------- Anvil

def _state_nbt(state):
    name, props = parse_state(state)
    tag = {'Name': 'minecraft:' + name}
    if props: tag['Properties'] = dict(props)
    return tag


def _section_nbt(world, sy, a):
    tag = {'Y': Byte(sy), 'biomes': {'palette': [BIOME]}}
    if a is None:
        tag['block_states'] = {'palette': [{'Name': 'minecraft:air'}]}
        return tag
    used = sorted(set(a))
    states = {'palette': [_state_nbt(world.states[i]) for i in used]}
    if len(used) > 1:
        remap = {v: k for k, v in enumerate(used)}
        bits = max(4, (len(used) - 1).bit_length())
        per = 64 // bits
        idx = [remap[v] for v in a]
        longs = []
        for start in range(0, 4096, per):
            value, shift = 0, 0
            for k in idx[start:start + per]:
                value |= k << shift
                shift += bits
            longs.append(value - (1 << 64) if value >> 63 else value)
        states['data'] = LongArray(longs)
    tag['block_states'] = states
    return tag


def _chunk_nbt(world, cx, cz, sections, block_entities):
    return {
        'DataVersion': DATA_VERSION, 'xPos': cx, 'yPos': MIN_Y >> 4, 'zPos': cz, 'Status': 'minecraft:full',
        'LastUpdate': Long(0), 'InhabitedTime': Long(0), 'isLightOn': Byte(0),      # the server lights it on first load
        'sections': [_section_nbt(world, sy, sections.get(sy)) for sy in range(MIN_Y >> 4, (MAX_Y >> 4) + 1)],
        'block_entities': block_entities, 'block_ticks': [], 'fluid_ticks': [],
        'structures': {'References': {}, 'starts': {}}}


def _region_bytes(chunks):
    """{(chunk x, chunk z): uncompressed NBT} -> one .mca file."""
    locations, stamps, body = bytearray(4096), bytearray(4096), bytearray()
    sector, now = 2, int(time.time())
    for (cx, cz), raw in sorted(chunks.items()):
        data = zlib.compress(raw, 6)
        blob = struct.pack('>IB', len(data) + 1, 2) + data
        blob += b'\x00' * (-len(blob) % 4096)
        count = len(blob) // 4096
        if count > 255: raise ValueError('chunk %d,%d is too large' % (cx, cz))
        at = 4 * ((cx & 31) + (cz & 31) * 32)
        locations[at:at + 4] = struct.pack('>I', (sector << 8) | count)
        stamps[at:at + 4] = struct.pack('>I', now)
        body += blob
        sector += count
    return bytes(locations) + bytes(stamps) + bytes(body)


def _write_regions(folder, chunks):
    os.makedirs(folder, exist_ok=True)
    regions = {}
    for (cx, cz), raw in chunks.items():
        regions.setdefault((cx >> 5, cz >> 5), {})[(cx, cz)] = raw
    for (rx, rz), part in regions.items():
        with open(os.path.join(folder, 'r.%d.%d.mca' % (rx, rz)), 'wb') as f:
            f.write(_region_bytes(part))
    return len(regions)


def _dat(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with gzip.open(path, 'wb') as f:
        f.write(nbt_bytes(data))


GAME_RULES = {
    # a lobby: the sun stands still, no weather, nothing spawns, nothing grows, burns or breaks
    'advance_time': False, 'advance_weather': False, 'spawn_mobs': False, 'spawn_monsters': False, 'spawn_patrols': False,
    'spawn_phantoms': False, 'spawn_wandering_traders': False, 'spawn_wardens': False, 'raids': False, 'mob_griefing': False,
    'random_tick_speed': 0, 'spread_vines': False, 'fire_spread_radius_around_player': 0, 'tnt_explodes': False,
    'show_advancement_messages': False, 'locator_bar': False,
    # the rest as the game sets them
    'max_entity_cramming': 24, 'fall_damage': True, 'command_block_output': True, 'respawn_radius': 0,
    'max_command_sequence_length': 65536, 'immediate_respawn': True, 'drowning_damage': True, 'tnt_explosion_drop_decay': False,
    'player_movement_check': True, 'natural_health_regeneration': True, 'keep_inventory': True,
    'players_nether_portal_creative_delay': 0, 'fire_damage': True, 'command_blocks_work': True,
    'spectators_generate_chunks': False, 'log_admin_commands': True, 'block_drops': True, 'limited_crafting': False,
    'elytra_movement_check': True, 'global_sound_events': True, 'lava_source_conversion': False,
    'players_nether_portal_default_delay': 80, 'players_sleeping_percentage': 100, 'entity_drops': True, 'freeze_damage': True,
    'forgive_dead_players': True, 'mob_drops': True, 'max_command_forks': 65536, 'water_source_conversion': False,
    'allow_entering_nether_using_portals': False, 'show_death_messages': True, 'block_explosion_drop_decay': True,
    'send_command_feedback': True, 'max_block_modifications': 32768, 'pvp': True, 'ender_pearls_vanish_on_death': True,
    'spawner_blocks_work': True, 'mob_explosion_drop_decay': True, 'max_snow_accumulation_height': 1,
    'projectiles_can_break_blocks': False, 'reduced_debug_info': False, 'universal_anger': False}


def save_world(world, folder, spawn=(0, 101, 72), yaw=180.0, time_of_day=6000, margin=10):
    """
    Writes the whole world folder. `margin` is how many empty chunks to write round the build, so the void is
    already there whatever the server's own generator would have made. Returns a summary.
    """
    by_chunk = {}
    for (cx, sy, cz), a in world.sections.items():
        if a.count(0) < 4096: by_chunk.setdefault((cx, cz), {})[sy] = a
    entity_blocks = {}
    for (x, y, z), tag in world.block_entities.items():
        if _block_entity_id(world.name(x, y, z)) != tag.get('id'): continue      # the block was built over
        full = dict(tag, x=x, y=y, z=z, keepPacked=Byte(0))
        entity_blocks.setdefault((x >> 4, z >> 4), []).append(full)
    xs = [c[0] for c in by_chunk]; zs = [c[1] for c in by_chunk]
    chunks = {}
    for cx in range(min(xs) - margin, max(xs) + margin + 1):
        for cz in range(min(zs) - margin, max(zs) + margin + 1):
            chunks[(cx, cz)] = nbt_bytes(_chunk_nbt(world, cx, cz, by_chunk.get((cx, cz), {}), entity_blocks.get((cx, cz), [])))
    dim = os.path.join(folder, 'dimensions', 'minecraft', 'overworld')
    regions = _write_regions(os.path.join(dim, 'region'), chunks)

    by_entity_chunk = {}
    for e in world.entities:
        by_entity_chunk.setdefault((int(e['Pos'][0] // 16), int(e['Pos'][2] // 16)), []).append(e)
    _write_regions(os.path.join(dim, 'entities'), {
        (cx, cz): nbt_bytes({'DataVersion': DATA_VERSION, 'Position': IntArray([cx, cz]), 'Entities': es})
        for (cx, cz), es in by_entity_chunk.items()})

    def wrapped(data): return {'DataVersion': DATA_VERSION, 'data': data}
    data = os.path.join(dim, 'data', 'minecraft')
    noise = lambda kind, source, settings: {'type': kind, 'generator': {'type': 'minecraft:noise', 'biome_source': source, 'settings': settings}}
    _dat(os.path.join(data, 'world_gen_settings.dat'), wrapped({
        'generate_structures': Byte(0), 'bonus_chest': Byte(0), 'seed': Long(0),
        'dimensions': {
            'minecraft:overworld': {'type': 'minecraft:overworld', 'generator': {'type': 'minecraft:flat', 'settings': {
                'biome': 'minecraft:the_void', 'features': Byte(0), 'lakes': Byte(0),
                'layers': [{'block': 'minecraft:air', 'height': 1}], 'structure_overrides': []}}},
            'minecraft:the_nether': noise('minecraft:the_nether', {'type': 'minecraft:multi_noise', 'preset': 'minecraft:nether'}, 'minecraft:nether'),
            'minecraft:the_end': noise('minecraft:the_end', {'type': 'minecraft:the_end'}, 'minecraft:end')}}))
    _dat(os.path.join(data, 'game_rules.dat'), wrapped({
        'minecraft:' + k: (Byte(1 if v else 0) if isinstance(v, bool) else int(v)) for k, v in GAME_RULES.items()}))
    clocks = wrapped({'minecraft:overworld': {'total_ticks': Long(time_of_day)}, 'minecraft:the_end': {'total_ticks': Long(0)}})
    _dat(os.path.join(data, 'world_clocks.dat'), clocks)
    _dat(os.path.join(folder, 'data', 'minecraft', 'world_clocks.dat'), clocks)
    _dat(os.path.join(data, 'weather.dat'), wrapped({
        'thunder_time': 0, 'thundering': Byte(0), 'rain_time': 0, 'raining': Byte(0), 'clear_weather_time': 0}))
    # Paper's own files for the dimension, so a Paper server takes the folder as it is (without them it first
    # runs its "import a vanilla world" step); a vanilla server ignores them
    paper = os.path.join(dim, 'data', 'paper')
    _dat(os.path.join(paper, 'metadata.dat'), wrapped({'uuid': IntArray([world._uuid.randint(-2 ** 31, 2 ** 31 - 1) for _ in range(4)])}))
    _dat(os.path.join(paper, 'persistent_data_container.dat'), wrapped({}))
    _dat(os.path.join(paper, 'level_overrides.dat'), wrapped({
        'difficulty_settings': {'difficulty': 'peaceful', 'hardcore': Byte(0), 'locked': Byte(0)},
        'spawn': {'pos': IntArray(list(spawn)), 'yaw': Float(yaw), 'pitch': Float(0)},
        'initialized': Byte(1), 'game_time': Long(0), 'game_type': 0}))
    _dat(os.path.join(folder, 'level.dat'), {'Data': {
        'DataVersion': DATA_VERSION, 'version': 19133, 'LevelName': 'world', 'initialized': Byte(1), 'WasModded': Byte(0),
        'allowCommands': Byte(0), 'GameType': 0, 'Time': Long(0), 'LastPlayed': Long(int(time.time() * 1000)),
        'difficulty_settings': {'difficulty': 'peaceful', 'hardcore': Byte(0), 'locked': Byte(0)},
        'spawn': {'pos': IntArray(list(spawn)), 'yaw': Float(yaw), 'pitch': Float(0), 'dimension': 'minecraft:overworld'},
        'DataPacks': {'Enabled': ['vanilla'], 'Disabled': []},
        'Version': {'Name': VERSION_NAME, 'Series': 'main', 'Snapshot': Byte(0), 'Id': DATA_VERSION}}})
    return {'chunks': len(chunks), 'built_chunks': len(by_chunk), 'regions': regions, 'entities': len(world.entities),
            'block_entities': sum(len(v) for v in entity_blocks.values()), 'states': len(world.states)}


def check_world(folder):
    """Reads every region file back: header, compression and NBT of each chunk. Returns (chunks, non-air sections)."""
    chunks = sections = 0
    region = os.path.join(folder, 'dimensions', 'minecraft', 'overworld', 'region')
    for name in sorted(os.listdir(region)):
        raw = open(os.path.join(region, name), 'rb').read()
        if len(raw) % 4096: raise ValueError(name + ' is not a whole number of sectors')
        for i in range(1024):
            entry = struct.unpack_from('>I', raw, i * 4)[0]
            if not entry: continue
            at = (entry >> 8) * 4096
            length, kind = struct.unpack_from('>IB', raw, at)
            if kind != 2: raise ValueError(name + ' has a chunk that is not zlib')
            chunk = read_nbt(zlib.decompress(raw[at + 5:at + 4 + length]))
            if (chunk['xPos'] & 31, chunk['zPos'] & 31) != (i % 32, i // 32): raise ValueError(name + ' has a chunk in the wrong slot')
            chunks += 1
            for s in chunk['sections']:
                pal = s['block_states']['palette']
                if len(pal) > 1:
                    bits = max(4, (len(pal) - 1).bit_length())
                    if len(s['block_states']['data']) != -(-4096 // (64 // bits)): raise ValueError('bad section data length')
                    sections += 1
    gzip.open(os.path.join(folder, 'level.dat')).read()
    return chunks, sections
