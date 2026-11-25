"""
architecture_and_details.py: everything built on the islands of the Shattered Sky-Citadel.

One architectural language throughout: walls recessed between pilasters, on stepped plinths, under corbelled
cornices. Four factions enter through colour, material and roofline:

    Crimson Hold       south-west   red nether brick, dark oak, gold, fire       a gambrel war hall and a forge
    Cobalt Citadel     south-east   dark prismarine, spruce, quartz, soul fire    a steep hall with a rose window, a tide pool
    Aurelian Bastion   north-east   sandstone, birch, gold, blackstone           a flat-roofed keep with a sun dome
    Emerald Enclave    north-west   mossy stone, oak, green copper, emerald      a broad mossy roof grown over, a grove

Between them: the Truce Sanctum and its Four-Way War Table (centre), the Match Gate (north), the arrival pavilion
and the Sky-Dock (south), the Armoury Terrace with every kit on show (west), the Hall of Records
and the Overlook Deck (east) and the Bell-Belfry over the parkour (far west).

The hub's characters, kit stands and floating labels are collected on the Hub object as they are built, so they
always stand where the architecture puts them.
"""
import math

from terrain_and_bridges import Xf, camp_frame, fbm, rnd3, slab, spline_tree, stairs, wall
from world_writer import DIRS, OPPOSITE

F = 100                                    # the floor block everywhere; you stand at F + 1
W1, W2, DK, TL, CH = 'stone_bricks', 'polished_andesite', 'polished_deepslate', 'deepslate_tiles', 'chiseled_stone_bricks'
STONE = (W1, W2, DK, TL, CH)
TITLE_Y = F + 4.7                          # where a label's title sits: just over head height, never higher

KITS = ['barbarian', 'big_spender', 'builder', 'choruser', 'demoman', 'engineer', 'fire_archer', 'infiltrator', 'medic',
        'necromancer', 'ninja', 'phoenix', 'redstone_mechanic', 'seeking_archer', 'sheep_farmer', 'shielder', 'spy', 'tank',
        'thor', 'frost_mage', 'tracker', 'grappler', 'trapper', 'vampire']
NEW_KITS = KITS[19:]                       # added in FractalCTF 1.30.0: on show in the New Arrivals row in the court
TEAMS = ('red', 'blue', 'yellow', 'green')


def aged(x, y, z):
    """Stone brick, mossy in drifts and cracked in patches."""
    n = fbm(x + y * 0.7, z - y * 0.7, 6, 41)
    return 'mossy_stone_bricks' if n > 0.46 else 'cracked_stone_bricks' if n < -0.5 else W1


def sandy(x, y, z):
    """The Bastion's wall: sandstone in courses, a cut course every sixth, smooth where the wind has worn it."""
    if y % 6 == 2: return 'cut_sandstone'
    return 'smooth_sandstone' if fbm(x + y * 0.7, z - y * 0.7, 6, 43) > 0.4 else 'sandstone'


def mossy(x, y, z):
    """The Enclave's wall: stone brick the moss has all but taken."""
    n = fbm(x + y * 0.7, z - y * 0.7, 5, 47)
    return 'moss_block' if n > 0.55 else 'stone_bricks' if n < -0.35 else 'mossy_stone_bricks'


CRIMSON = dict(key='crimson', side='red', title='CRIMSON HOLD', colour='red', roof='red_nether_bricks', accent='red_terracotta',
               accent2='nether_bricks', gem='gold_block', beam='dark_oak_log', plank='crimson_planks', fence='crimson_fence',
               light='shroomlight', lamp='lantern', fire='campfire[lit=true]', cloth='red_wool', cloth2='red_concrete',
               stripe='yellow_wool', banner='red', glass='red_stained_glass_pane', carpet='red_carpet', carpet2='yellow_carpet',
               path='dark_oak_planks', truss='stripped_dark_oak_wood', tassel='red_nether_brick_wall', inlay='red_terracotta',
               stud='gold_block', glazed='red_glazed_terracotta', candle='red_candle', table='red_concrete', pot='potted_crimson_fungus',
               stone=STONE, wallf=aged, style='gambrel', dye=0xB02E26, ring=60, sign='red',
               motto='The forge-legion of the south-west\nFirst to the flag, last to leave it')
COBALT = dict(key='cobalt', side='blue', title='COBALT CITADEL', colour='blue', roof='dark_prismarine', accent='blue_terracotta',
              accent2='prismarine_bricks', gem='chiseled_quartz_block', beam='spruce_log', plank='spruce_planks',
              fence='spruce_fence', light='sea_lantern', lamp='soul_lantern', fire='soul_campfire[lit=true]', cloth='blue_wool',
              cloth2='light_blue_wool', stripe='white_wool', banner='blue', glass='light_blue_stained_glass_pane',
              carpet='blue_carpet', carpet2='light_blue_carpet', path='spruce_planks', truss='smooth_quartz', tassel='prismarine_wall',
              inlay='prismarine_bricks', stud='smooth_quartz', glazed='light_blue_glazed_terracotta', candle='blue_candle',
              table='blue_concrete', pot='potted_blue_orchid', stone=STONE, wallf=aged, style='steep', dye=0x3C44AA, ring=62,
              sign='light_blue', motto='The tide-wardens of the south-east\nThey chart every wind before they move')
AURELIAN = dict(key='aurelian', side='yellow', title='AURELIAN BASTION', colour='yellow', roof='smooth_sandstone',
                accent='yellow_terracotta', accent2='polished_blackstone_bricks', gem='gold_block', beam='stripped_birch_log',
                plank='birch_planks', fence='birch_fence', light='ochre_froglight', lamp='lantern', fire='campfire[lit=true]',
                cloth='yellow_wool', cloth2='yellow_concrete', stripe='white_wool', banner='yellow', glass='yellow_stained_glass_pane',
                carpet='yellow_carpet', carpet2='white_carpet', path='birch_planks', truss='chiseled_sandstone', tassel='sandstone_wall',
                inlay='yellow_terracotta', stud='gold_block', glazed='yellow_glazed_terracotta', candle='yellow_candle',
                table='yellow_concrete', pot='potted_dandelion', stone=('sandstone', 'smooth_sandstone', DK, 'cut_sandstone', 'chiseled_sandstone'),
                wallf=sandy, style='keep', dye=0xFED83D, ring=62, sign='yellow',
                motto='The sun-guard of the north-east\nThey held the gate when the Citadel fell')
EMERALD = dict(key='emerald', side='green', title='EMERALD ENCLAVE', colour='green', roof='mossy_cobblestone',
               accent='green_terracotta', accent2='waxed_oxidized_cut_copper', gem='emerald_block', beam='oak_log', plank='oak_planks',
               fence='oak_fence', light='verdant_froglight', lamp='lantern', fire='campfire[lit=true]', cloth='green_wool',
               cloth2='lime_wool', stripe='white_wool', banner='green', glass='lime_stained_glass_pane', carpet='green_carpet',
               carpet2='lime_carpet', path='oak_planks', truss='stripped_oak_wood', tassel='mossy_cobblestone_wall',
               inlay='green_terracotta', stud='emerald_block', glazed='green_glazed_terracotta', candle='green_candle',
               table='green_concrete', pot='potted_fern', stone=('mossy_stone_bricks', 'mossy_cobblestone', DK, TL, CH), wallf=mossy,
               style='broad', dye=0x5E7C16, ring=60, sign='lime',
               motto='The grove-keepers of the north-west\nThe garden was here before the stone')
FACTIONS = {'crimson': CRIMSON, 'cobalt': COBALT, 'aurelian': AURELIAN, 'emerald': EMERALD}
# each faction's emblem (a flame, a wave, a sun, a leaf) and the four plaques on its banner gate in the sanctum
CRIMSON.update(emblem=('..#..', '.##..', '.###.', '#####', '.###.'), plaques=(
    ('CRIMSON HOLD', 'the forge-', 'legion of the', 'old Citadel'), ('In the', 'Sundering they', 'held the', 'furnaces'),
    ('Creed:', 'First to the', 'flag, last', 'to leave it'), ('Quarrel:', 'Cobalt took', 'their flag on', 'Accord night')))
COBALT.update(emblem=('..#..', '.#.#.', '#...#', '..#..', '.#.#.'), plaques=(
    ('COBALT CITADEL', 'wardens of the', 'sea-gates and', 'the charts'), ('In the', 'Sundering they', 'saved the', 'charts'),
    ('Creed:', 'Read the wind,', 'then move', ''), ('Quarrel:', 'the Bastion', 'taxes the dock', 'Cobalt built')))
AURELIAN.update(emblem=('#.#.#', '.###.', '#####', '.###.', '#.#.#'), plaques=(
    ('AURELIAN', 'BASTION', 'the sun-guard', 'of the gate'), ('In the', 'Sundering they', 'stood at the', 'gate'),
    ('Creed:', 'Hold the high', 'ground, keep', 'your word'), ('Quarrel:', 'all three', 'teams owe', 'them gold')))
EMERALD.update(emblem=('...##', '..###', '.###.', '###..', '#....'), plaques=(
    ('EMERALD', 'ENCLAVE', 'gardeners of', 'the deep roots'), ('In the', 'Sundering', 'their roots', 'held the rock'),
    ('Creed:', 'Patience', 'wins the wool', ''), ('Quarrel:', 'Crimson smoke,', 'Cobalt pipes,', 'Bastion coin')))


def quadrant(x, z):
    """The faction whose camp lies out past this corner of the sanctum."""
    return (COBALT if z > 0 else AURELIAN) if x > 0 else (CRIMSON if z > 0 else EMERALD)


class Hub:
    """The world being built, its islands' ground cells, and the characters, kit stands and labels collected so far."""

    def __init__(self, world, cells):
        self.w, self.cells = world, cells
        self.npcs, self.kits, self.labels, self.boards = [], [], [], []
        self.portal = None
        self.texts = []                                              # every floating line, for hub-npcs.yml

    def label(self, x, y, z, title, colour='gold', sub=None, scale=1.3):
        """
        A title with its foot at y and lines of small print under it. They go into hub-npcs.yml for the plugin to draw
        (so a label can change without a new world), and are remembered so the build can prove each hangs in open air.
        """
        self.texts.append(dict(x=x, y=y, z=z, scale=scale, text='<%s><bold>%s' % (colour, title)))
        low, wide = y, len(title) * 0.16 * scale
        if sub:
            low = y - 0.3 - 0.25 * (sub.count('\n') + 1)
            self.texts.append(dict(x=x, y=low, z=z, scale=1.0, text='<white>' + sub))
            wide = max(wide, max(len(line) for line in sub.split('\n')) * 0.14)
        self.labels.append((title, x, low, y + 0.3 * scale, z, wide / 2))

    def npc(self, ident, name, role, profession, kind, spot, lines, pace=None):
        """A character. `pace` is the (x, z) points it strolls between when nobody is near (its own spot is the first)."""
        self.npcs.append(dict(id=ident, name=name, role=role, profession=profession, type=kind, x=spot[0], y=spot[1], z=spot[2],
                              yaw=spot[3], lines=lines, pace=pace))

    def board(self, stat, x, y, z, yaw, scale=1.1):
        """A Top 10 board for the plugin to write on: the foot of its text, the way it faces, its size."""
        self.boards.append(dict(stat=stat, x=x, y=y, z=z, yaw=yaw, scale=scale))

    def kit(self, X, x, y, z, yaw, kit):
        sx, sy, sz, yw = X.spot(x, y, z, yaw)
        self.kits.append(dict(kit=kit, x=sx, y=sy, z=sz, yaw=yw, team=TEAMS[KITS.index(kit) % 4]))

    def stand(self, X, x, y, z, yaw, gear):
        sx, sy, sz, yw = X.spot(x, y, z, yaw)
        self.w.armor_stand(sx, sy, sz, yw, gear)

    def sign(self, X, x, y, z, state, lines, colour='black', glow=False):
        wx, wz = X.at(x, z)
        self.w.sign(wx, y, wz, X.state(state), lines, colour, glow)


def leather(dye, hand=None, **more):
    """A display stand's kit: leather in a faction's dye, and what it holds."""
    gear = {'head': ('leather_helmet', dye), 'chest': ('leather_chestplate', dye), 'legs': ('leather_leggings', dye),
            'feet': ('leather_boots', dye)}
    if hand: gear['mainhand'] = hand
    gear.update(more)
    return gear


def octa(dx, dz):
    """Distance in an octagon's own measure (a square with its corners cut)."""
    return max(abs(dx), abs(dz), (abs(dx) + abs(dz)) / 1.4142)


def lamp_post(X, x, z, y, lamp='lantern'):
    X.set(x, y, z, DK)
    X.set(x, y + 1, z, wall(W1))
    X.set(x, y + 2, z, wall(W1))
    X.set(x, y + 3, z, lamp + '[hanging=false]')


def brazier(X, x, z, y, fire):
    X.set(x, y, z, DK)
    X.set(x, y + 1, z, wall(DK))
    X.set(x, y + 2, z, fire)


def cloth(put, top, length, out, main, edge, stripe, phase=0.0):
    """
    A banner that billows: `top` is its top edge (x, y, z cells in order); it hangs `length` blocks, pushed along
    `out` by a wave that grows toward its free end, with an edge colour down both sides, a stripe near the top and
    a swallow-tail cut into the bottom.
    """
    n = len(top)
    mid = (n - 1) / 2.0
    for i, (x, y, z) in enumerate(top):
        for j in range(length):
            k = j - (length - 3)
            if k >= 0 and abs(i - mid) < 0.3 + k * 0.8: continue
            push = int(round(1.3 * math.sin(j * 0.55 + i * 0.6 + phase) * (j / length) ** 0.8))
            block = edge if i in (0, n - 1) else stripe if j in (1, 2) else main
            put(x + out[0] * push, y - j, z + out[1] * push, block)


def tower(X, cx, cz, y, half, h, roof=TL, spire=11):
    """
    A citadel tower: a stepped plinth with a stair moulding, corner piers with the wall faces recessed between them,
    projecting string courses every ten blocks, barred slit windows in dark recesses on every face at three heights,
    a corbel table carrying a battlement, and an eight-sided spire with a gilded finial.
    """
    top = y + h
    for dx in range(-half - 1, half + 2):
        for dz in range(-half - 1, half + 2):
            m = max(abs(dx), abs(dz))
            x, z = cx + dx, cz + dz
            if m == half + 1:
                corner = abs(dx) == abs(dz)
                toward = 'east' if dx == -half - 1 else 'west' if dx == half + 1 else 'south' if dz < 0 else 'north'
                X.set(x, y, z, slab(DK) if corner else DK)
                if not corner: X.set(x, y + 1, z, stairs(DK, toward))
                X.set(x, top, z, slab(W1, top=True) if corner else stairs(W1, toward, top=True))
                X.set(x, top + 1, z, W1)
                if corner or (dx + dz) % 2 == 0: X.set(x, top + 2, z, W1)
                continue
            pier = m == half and min(abs(dx), abs(dz)) >= half - 1
            face = dx == 0 or dz == 0
            for yy in range(y, top + 1):
                rel = yy - y
                if m == half:
                    if rel % 10 == 0: X.set(x, yy, z, DK)
                    elif pier: X.set(x, yy, z, W2)
                elif m == half - 1:
                    X.set(x, yy, z, 'iron_bars' if face and rel % 10 in (4, 5, 6) else CH if face and rel % 10 == 7 else aged(x, yy, z))
                elif m == half - 2 and face and rel % 10 in (4, 5, 6):
                    X.set(x, yy, z, 'air')
                else:
                    X.set(x, yy, z, W1)
    for k in range(spire + 1):
        r = (half - 0.5) * (1 - k / spire)
        for dx in range(-half, half + 1):
            for dz in range(-half, half + 1):
                if octa(dx, dz) <= r + 0.3: X.set(cx + dx, top + 1 + k, cz + dz, roof)
    X.set(cx, top + spire + 2, cz, 'gold_block')
    X.set(cx, top + spire + 3, cz, 'end_rod[facing=up]')


def turret(X, cx, cz, y0, y1, P):
    """A slim eight-sided corner turret with a coloured cone and finial."""
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            if octa(dx, dz) > 2.3: continue
            for y in range(y0, y1 + 1): X.set(cx + dx, y, cz + dz, DK if (y - y0) % 8 == 0 else aged(cx + dx, y, cz + dz))
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            if 2.3 < octa(dx, dz) <= 3.3: X.set(cx + dx, y1, cz + dz, slab(DK, top=True))
            for k in range(6):
                if octa(dx, dz) <= 2.9 * (1 - k / 6.0) + 0.2: X.set(cx + dx, y1 + 1 + k, cz + dz, P['roof'])
    X.set(cx, y1 + 7, cz, P['gem'])
    X.set(cx, y1 + 8, cz, 'end_rod[facing=up]')


# ---------------------------------------------------------------------------------------------------- the sanctum

def sanctum(hub):
    """
    The Truce Sanctum: a plaza paved in rings and rays round the monument, each quarter inlaid in the colour of the
    faction whose camp lies beyond it; a ring of lights; a plank way to each bridge; and astride each diagonal a
    faction's banner gate: two pylons hung with its colours, its emblem inlaid between them, four plaques telling
    who they were, what they did in the Sundering, their creed and their quarrel. You look through it at their hall.
    """
    w = hub.w
    lights = {(int(round(20.5 * math.cos(k * math.pi / 8))), int(round(20.5 * math.sin(k * math.pi / 8)))) for k in range(16)}
    for (x, z), e in hub.cells['core'].items():
        if e < 2.3: continue
        d = math.hypot(x, z)
        a = math.degrees(math.atan2(z, x)) % 360
        cross, diag = min(abs(x), abs(z)), abs(abs(x) - abs(z))
        Q = quadrant(x, z)
        if d < 7.4: s = W2
        elif d < 9.2: s = 'gold_block' if cross == 0 else Q['accent']
        elif d < 19.5: s = DK if cross <= 1 else Q['inlay'] if diag == 0 and d > 11 else W1 if int(a // 22.5) % 2 == 0 else 'deepslate_bricks'
        elif d < 21.5: s = 'ochre_froglight' if (x, z) in lights else TL
        elif cross <= 2: s = 'spruce_planks'
        elif cross == 3: s = 'gold_block' if int(d) % 4 == 0 else DK
        elif diag == 0: s = Q['accent']
        elif diag == 1: s = TL
        else: s = aged(x, 0, z)
        w.set(x, F, z, s)
    for k in range(4):
        X = Xf(w, 0, 0, rot=k)
        lamp_post(X, 11, 25, F + 1)
        for x in (14, 15, 16): X.set(x, F + 1, 26, stairs('dark_oak_planks', 'south'))       # a bench, its back to the void
        for x in (13, 17): X.set(x, F + 1, 26, slab(DK))
        P = quadrant(*X.at(22, 22))                                  # this faction's banner gate, astride its diagonal
        core = hub.cells['core']
        for r, row in enumerate(P['emblem']):                        # its emblem inlaid between the pylons
            for c, mark in enumerate(row):
                if core.get(X.at(20 + c, 20 + r), 0) >= 1.3: X.set(20 + c, F, 20 + r, P['gem'] if mark == '#' else P['accent'])
        for q in range(19, 26):
            for cell in ((q, 19), (q, 25), (19, q), (25, q)):
                if core.get(X.at(*cell), 0) >= 1.3: X.set(cell[0], F, cell[1], DK)
        for i, (px, pz) in enumerate(((19, 25), (25, 19))):
            X.set(px, F + 1, pz, DK)
            for y in range(F + 2, F + 8): X.set(px, y, pz, CH if y == F + 5 else W2)
            X.set(px, F + 8, pz, P['gem'])
            X.set(px, F + 9, pz, 'lantern[hanging=false]')
            bx, bz = (px, pz - 1) if i == 0 else (px - 1, pz)        # its banner hangs on the face toward the plaza
            X.set(bx, F + 8, bz, 'stripped_dark_oak_wood[axis=%s]' % ('z' if i == 0 else 'x'))
            for y in range(F + 4, F + 8): X.set(bx, y, bz, P['stripe'] if y == F + 6 else P['cloth2'] if y == F + 4 else P['cloth'])
            X.set(bx, F + 3, bz, P['tassel'])
            sx, sz, face = (px - 1, pz, 'west') if i == 0 else (px, pz - 1, 'north')     # two plaques on the other inward face
            for j in (0, 1):
                hub.sign(X, sx, F + 3 - j, sz, 'dark_oak_wall_sign[facing=%s]' % face, P['plaques'][i * 2 + j], P['sign'], True)
        lx, lz = X.at(18, 18)
        hub.label(lx + 0.5, F + 4.2, lz + 0.5, P['title'], P['colour'], P['motto'], 1.0)
    hub.label(0.5, TITLE_Y, 14.5, 'TRUCE SANCTUM', 'gold', 'Four banners, one truce: no blade is drawn here\nVote with the paper  •  Pick a kit with the book', 1.3)


def monument(hub):
    """
    The monument over the Four-Way War Table: a three-tier eight-sided plinth whose top tier is the table itself,
    quartered in the four colours with a candle for each host; four buttress piers on the diagonals, each draped
    with the banner of the faction it points at and carrying that faction's plaque; flying arches that meet in a
    crown; and one crystal (shroomlight in amethyst in two shells of stained glass) hung from the crown over the
    table. A low open crown of eight ribs closes it; nothing hangs inside that.
    """
    w = hub.w
    for x in range(-7, 8):
        for z in range(-7, 8):
            o = octa(x, z)
            if o <= 5: w.set(x, F + 1, z, DK)
            elif o <= 6: w.set(x, F + 1, z, slab(DK))
            if o <= 3.6: w.set(x, F + 2, z, TL)
            elif o <= 4.6: w.set(x, F + 2, z, slab(TL))
            if o <= 2.4:                                             # the war table
                w.set(x, F + 3, z, 'lodestone' if not (x or z) else 'gold_block' if x == 0 or z == 0 else quadrant(x, z)['table'])
    for sx in (-1, 1):
        for sz in (-1, 1): w.set(sx, F + 4, sz, quadrant(sx, sz)['candle'] + '[candles=3,lit=true]')
    for x in range(-4, 5):                                           # the crystal, in four layers
        for z in range(-4, 5):
            for y in range(F + 6, F + 18):
                v = abs(x) + abs(z) + abs(y - (F + 11.5)) * 0.62
                if v <= 1.2: w.set(x, y, z, 'shroomlight')
                elif v <= 2.3: w.set(x, y, z, 'amethyst_block')
                elif v <= 2.9: w.set(x, y, z, 'magenta_stained_glass')
                elif v <= 3.6: w.set(x, y, z, 'purple_stained_glass')
    for y in range(F + 18, F + 22): w.set(0, y, 0, 'iron_chain[axis=y]')
    for k in range(4):                                               # the buttresses
        X = Xf(w, 0, 0, rot=k)
        P = quadrant(*X.at(7, 7))
        for dx in range(5, 9):
            for dz in range(5, 9):
                X.set(dx, F + 1, dz, DK if 5 < dx < 8 and 5 < dz < 8 else slab(DK))
        for dx in (6, 7):
            for dz in (6, 7):
                for y in range(F + 2, F + 15): X.set(dx, y, dz, DK if y in (F + 7, F + 14) else aged(dx, y, dz))
        X.set(7, F + 15, 7, CH)
        X.set(7, F + 16, 7, wall(W1))
        X.set(7, F + 17, 7, 'lantern[hanging=false]')
        for face, toward in ((0, 'west'), (1, 'north')):             # a banner draped down each outward face
            for j in (6, 7):
                def put(y, state, j=j, face=face):
                    X.set(8 if face == 0 else j, y, j if face == 0 else 8, state)
                put(F + 14, 'stripped_dark_oak_wood[axis=%s]' % ('z' if face == 0 else 'x'))       # the rod it hangs from
                put(F + 13, stairs(P['roof'], toward, top=True))                                   # the fold over the rod
                for y in range(F + 5, F + 13):
                    put(y, P['stripe'] if y == F + 11 else P['cloth2'] if (y + j) % 4 == 0 else P['cloth'])
                tail = (j == 6) == (face == 0)                       # a swallow tail: one side longer, tassels under both
                put(F + 4, P['cloth'] if tail else P['tassel'])
                if tail: put(F + 3, P['tassel'])
        for gx, gz in ((8, 8), (8, 5), (5, 8)): X.set(gx, F + 14, gz, 'gold_block')
        name = P['title'].split()
        hub.sign(X, 5, F + 3, 7, 'dark_oak_wall_sign[facing=west]', [name[0], name[1], '', 'of the Accord'], P['sign'], True)
        for i in range(40):                                          # the flying arch, two blocks deep
            t = i / 39.0
            r = (2.2 + 6.2 * math.cos(t * math.pi / 2)) / 1.4142
            y = F + 13 + int(round(9.0 * math.sin(t * math.pi / 2)))
            c = int(round(r))
            for cell in ((c, c), (c + 1, c), (c, c + 1)):
                X.set(cell[0], y, cell[1], W1)
                X.set(cell[0], y - 1, cell[1], DK)
    for x in range(-4, 5):                                           # the crown the arches meet in
        for z in range(-4, 5):
            o = octa(x, z)
            if o <= 3.2: w.set(x, F + 22, z, DK)
            if o <= 2.3: w.set(x, F + 23, z, CH)
            elif o <= 3.4: w.set(x, F + 23, z, 'gilded_blackstone' if x == 0 or z == 0 or abs(x) == abs(z) else slab(DK))
    for k in range(8):                                               # eight ribs that swell a little and close to a point
        a = k * math.pi / 4
        last = None
        for i in range(90):
            t = i / 89.0
            r = 2.8 + 1.4 * math.sin(math.pi * t) - 2.8 * t ** 2.2
            x, y, z = int(round(r * math.cos(a))), F + 24 + int(round(9 * t)), int(round(r * math.sin(a)))
            block = 'gilded_blackstone' if (y - F) % 4 == 1 else DK if k % 2 == 0 else TL
            if last and last != (x, y, z):                           # step one axis at a time, so the rib never hangs by a corner
                w.setif(last[0], y, last[2], block)
                w.setif(x, y, last[2], block)
            w.set(x, y, z, block)
            last = (x, y, z)
    w.set(0, F + 34, 0, 'gold_block')
    w.set(0, F + 35, 0, 'end_rod[facing=up]')
    for x, z in ((5, 0), (-5, 0), (0, 5), (0, -5)): brazier(w, x, z, F + 2, 'campfire[lit=true]')
    hub.npc('truce-keeper', '<gold><bold>Arbiter Edmund', 'talk', 'cleric', 'plains', (0.5, F + 3, 3.5, 0),
            ['One fortress once stood here under one banner. The <light_purple>Heartcrystal</light_purple> held it in the sky.',
             'Then came the Sundering. The crystal split, the Citadel with it, and four teams each claimed a shard.',
             '<red>Crimson</red> took the forges, <blue>Cobalt</blue> the sea-gates, <yellow>the Bastion</yellow> the treasury, <green>the Enclave</green> the gardens.',
             'They fought until the last whole crystal began to crack. That one hangs over this table now.',
             'So they laid four blades on the War Table and swore the <gold>Flag Accord</gold>: no blood on the Citadel. Quarrels go through the gate.',
             'Out there you take a flag, not a life. Mostly. Go and vote for where the next quarrel is held.'])


# ---------------------------------------------------------------------------------------------------- the gate

def gate(hub):
    """
    The Match Portal Gate on its own island north of the sanctum: four towers and, between the inner pair, a
    pointed arch in three stepped orders round a glowing portal; a battlement on machicolations over it with the
    four colours meeting at a gilded keystone; a banner down each tower (Crimson and Cobalt on the inner pair,
    Emerald and Aurelian on the outer, each on the side its camp lies); plank ways to the sanctum and to both
    northern camps.
    """
    w = hub.w
    X = Xf(w, 0, -48)
    for (x, z), e in hub.cells['gate'].items():
        if e < 2.3: continue
        lz = z + 48
        way = (abs(x) <= 2 and lz > -31) or abs(lz + 24) <= 2
        edge = (abs(x) == 3 and lz > -31) or abs(lz + 24) == 3
        w.set(x, F, z, 'spruce_planks' if way else DK if edge else TL if (x + z) % 4 == 0 else aged(x, 0, z))
    half_w, apex, jamb = (8, 7, 6), (24, 22, 20), 11

    def opening(x, rel, k):
        if rel >= apex[k]: return False
        width = half_w[k] if rel <= jamb else half_w[k] * (1 - ((rel - jamb) / float(apex[k] - jamb)) ** 1.6)
        return abs(x) <= width

    trims = (DK, CH, W2)
    for z in range(-37, -30):
        k = min(z + 37, -31 - z, 2)
        for x in range(-10, 11):
            for y in range(F + 1, F + 29):
                rel = y - F - 1
                if opening(x, rel, k): continue
                edge = opening(abs(x) - 1, rel, k) or opening(x, rel - 1, k)
                X.set(x, y, z, trims[k] if edge else DK if rel in (0, 27) else aged(x, y, z))
    for x in range(-10, 11):                                         # machicolations: brackets, a corbel course, the battlement
        for z, toward in ((-38, 'south'), (-30, 'north')):
            if x % 2 == 0: X.set(x, F + 27, z, stairs(DK, toward, top=True))
            X.set(x, F + 28, z, stairs(W1, toward, top=True))
            X.set(x, F + 29, z, W1)
            if x % 3 != 1:
                X.set(x, F + 30, z, W1)
                X.set(x, F + 31, z, slab(DK))
    for sx in (-1, 1):                                               # on both faces: blind lancets sunk in shadow, lanterns in clusters
        for z, dz in ((-31, 1), (-37, -1)):
            for x in (sx * 8, sx * 9):
                for y in range(F + 14, F + 20): X.set(x, y, z, 'air')
                X.set(x, F + 13, z + dz, stairs(DK, 'north' if dz > 0 else 'south'))
                X.set(x, F + 20, z + dz, slab(DK, top=True))
            for i, x in enumerate((sx * 5, sx * 7, sx * 9)):
                X.set(x, F + 24, z + dz, DK)
                for j in range(1 + i % 2): X.set(x, F + 23 - j, z + dz, 'iron_chain[axis=y]')
                X.set(x, F + 22 - i % 2, z + dz, 'lantern[hanging=true]')
    for x in range(-4, 5):                                           # the four colours meeting at a gilded keystone
        for y in (F + 26, F + 27):
            block = 'gold_block' if x == 0 else (EMERALD if x < -2 else CRIMSON if x < 0 else COBALT if x < 3 else AURELIAN)['accent']
            X.set(x, y, -31, block)
            X.set(x, y, -37, block)
    for z in (-35, -34, -33):                                        # the portal
        for x in range(-6, 7):
            for y in range(F + 1, F + 24):
                rel = y - F - 1
                if not opening(x, rel, 2): continue
                frame = not opening(abs(x) + 1, rel, 2) or not opening(x, rel + 1, 2)
                if frame: X.set(x, y, z, 'crying_obsidian' if (x + y) % 3 else 'obsidian')
                elif z == -34: X.set(x, y, z, 'pearlescent_froglight' if (x * 2 + y) % 5 == 0 else 'amethyst_block')
                else:
                    swirl = math.sin(math.hypot(x, rel - 9) * 1.1 - math.atan2(rel - 9, x or 0.01) * 2)
                    X.set(x, y, z, 'magenta_stained_glass' if swirl > 0.35 else 'purple_stained_glass')
    for sx, inner, outer in ((-1, CRIMSON, EMERALD), (1, COBALT, AURELIAN)):
        tower(X.child(sx * 15, -34), 0, 0, F + 1, 4, 31)
        tower(X.child(sx * 26, -34), 0, 0, F + 1, 3, 23, spire=9)
        for c, P, top, zf in ((15, inner, 29, -28), (26, outer, 22, -29)):       # the yard each banner hangs from
            for x in range(c - 3, c + 4): X.set(sx * x, F + top, zf, 'stripped_dark_oak_wood[axis=x]')
            for x in (c - 3, c + 3): X.set(sx * x, F + top, zf - 1, 'stripped_dark_oak_wood[axis=z]')
            cloth(X.setif, [(sx * c + i, F + top - 1, zf) for i in range(-2, 3)], 15 if top == 29 else 11, (0, 1), P['cloth'], P['cloth2'],
                  P['stripe'], phase=sx * 2 + top)
        brazier(X, sx * 12, -27, F + 1, inner['fire'])
    hub.portal = (X.at(-6, -35), X.at(6, -33), F + 1, F + 23)        # everything in this box that isn't obsidian breaks open
    lx, lz = X.at(0, -23)
    hub.label(lx + 0.5, TITLE_Y, lz + 0.5, 'MATCH GATE', 'light_purple', 'It breaks open when the vote closes\nWalk through it into the match', 1.4)
    hub.npc('gatewarden', '<light_purple><bold>Gatewarden Alice', 'talk', 'mason', 'snow', X.spot(6, F + 1, -27, 0),
            ['The gate opens when the countdown ends. It does not care which colour you wear.',
             'Past the arch the Accord changes: flags, wool and hills are fair game. In here, hands off.',
             'The <yellow>Bastion</yellow> built these towers. All four teams hung a banner on them anyway.',
             'Vote with the <yellow>paper</yellow>. The gate takes you wherever the most hands point.'],
            pace=[X.spot(6, F + 1, -27)[0::2], X.spot(-7, F + 1, -27)[0::2]])


# ---------------------------------------------------------------------------------------------------- the pavilion

def pavilion(hub, cx=0, cz=72):
    """
    The arrival pavilion: a compass rose inlaid in the floor (you arrive on the lodestone at its heart, facing
    north), eight columns on moulded bases, a pointed arch between each pair, and eight ribs rising from the
    capitals to an open crown ring with a chandelier.
    """
    w = hub.w
    for x in range(-10, 11):
        for z in range(-10, 11):
            d = math.hypot(x, z)
            if d > 9.6: continue
            u, v = abs(x + z) / 1.4142, abs(x - z) / 1.4142
            if d <= 7:
                card = abs(x) <= (6.6 - abs(z)) / 2.6 or abs(z) <= (6.6 - abs(x)) / 2.6
                diag = u <= (4.6 - v) / 2.6 or v <= (4.6 - u) / 2.6
                s = DK if card else W2 if diag else 'calcite'
            else: s = DK if d <= 8 else TL
            w.set(cx + x, F, cz + z, s)
    w.set(cx, F, cz, 'lodestone')
    w.set(cx, F, cz - 6, 'gold_block')                               # the north point
    for k in range(4):
        X = Xf(w, cx, cz, rot=k)
        for px, pz in ((4, -8), (8, -4)):
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    if dx or dz:
                        X.set(px + dx, F + 1, pz + dz, slab(DK))
                        X.set(px + dx, F + 7, pz + dz, slab(DK, top=True) if dx and dz else stairs(DK, 'east' if dx < 0 else 'west' if dx > 0 else 'south' if dz < 0 else 'north', top=True))
            for y in range(F + 1, F + 8): X.set(px, y, pz, DK if y == F + 1 else CH if y in (F + 4, F + 7) else W2)
            for i in range(30):                                      # the rib
                t = i / 29.0
                rx, rz = px * (1 - t) + px * 0.34 * t, pz * (1 - t) + pz * 0.34 * t
                y = F + 8 + int(round(6 * math.sin(t * math.pi / 2)))
                X.set(int(round(rx)), y, int(round(rz)), W1)
                if t < 0.35: X.set(int(round(rx)), y + 1, int(round(rz)), W1)
        for x in range(-3, 4):                                       # the arch over the way through (north in this frame)
            X.set(x, F + 8, -8, W1 if x else CH)
            X.set(x, F + 9, -8, slab(DK))
            if abs(x) == 3: X.set(x, F + 7, -8, stairs(W1, 'west' if x < 0 else 'east', top=True))
        X.set(0, F + 7, -8, 'lantern[hanging=true]')
        for x, z in ((5, -7), (6, -6), (7, -5)):                     # and over the corner bay
            X.set(x, F + 8, z, W1)
            X.set(x, F + 9, z, slab(DK))
        X.set(0, F + 13, -3, 'iron_chain[axis=y]')                   # the chandelier, hung from the crown ring
        X.set(0, F + 12, -3, 'lantern[hanging=true]')
    for x in range(-4, 5):
        for z in range(-4, 5):
            o = octa(x, z)
            if 2.4 < o <= 3.6:
                w.set(cx + x, F + 14, cz + z, DK)
                w.set(cx + x, F + 15, cz + z, slab(DK))
    for x in range(-3, 4):                                           # the ways out: north to the sanctum, west and east
        for z in range(cz - 14, cz - 9): w.set(cx + x, F, z, DK if abs(x) == 3 else 'spruce_planks')
    for sx in (-1, 1):
        for z in range(cz - 3, cz + 4):
            for x in range(10, 25): w.set(cx + sx * x, F, z, DK if abs(z - cz) == 3 else 'spruce_planks')
        lamp_post(w, cx + sx * 4, cz - 13, F + 1)
        for i in range(3): w.set(cx + sx * (5 + i), F + 1, cz + 12, stairs('dark_oak_planks', 'north'))
    for z in range(cz + 10, cz + 18):                                # and south to the Sky-Dock
        for x in range(-3, 4): w.set(cx + x, F, z, DK if abs(x) == 3 else 'spruce_planks')
    hub.label(cx + 0.5, F + 5.3, cz - 9.5, 'FRACTAL CTF', 'gold',
              'Vote for the next map with the paper\nPick a kit with the book or on the terrace\nWhen the countdown ends, the gate opens\nFlags, hills and wool: three ways to win', 1.5)
    hub.npc('herald', '<gold><bold>Herald Thomas', 'talk', 'cleric', 'plains', (cx + 3.5, F + 1, cz - 4.5, 38),
            ['Welcome to the Sky-Citadel. The <yellow>paper</yellow> votes for the next map, the <yellow>book</yellow> picks your kit.',
             'Straight ahead is the Truce Sanctum. Every kit stands on show on the <yellow>Armoury Terrace</yellow>, west of it.',
             '<red>Crimson</red> are on your left and <blue>Cobalt</blue> on your right. The <yellow>Bastion</yellow> and the <green>Enclave</green> flank the gate, across the sanctum.',
             'Four teams share this rock under one truce. Ask Arbiter Edmund at the War Table how that happened.',
             'When enough of you are here the countdown starts. The gate to the north does the rest.'])


# ---------------------------------------------------------------------------------------------------- the camps

GAMBREL = {13: (15, 0), 12: (16, 0), 11: (17, 0), 10: (19, 1), 9: (21, 1), 8: (23, 1), 7: (25, 1), 6: (26, 2), 5: (26, 3),
           4: (27, 2), 3: (27, 3), 2: (28, 2), 1: (28, 3), 0: (28, 3)}
STEEP = {13: (15, 0), 12: (16, 0), 11: (17, 0), 0: (38, 3)}
STEEP.update({q: (19 + 2 * (10 - q), 1) for q in range(1, 11)})
BROAD = {q: (28 - q, 0) for q in range(1, 14)}
BROAD[0] = (27, 3)
X0, X1, ZH = 76, 94, 11                    # a hall's two gable walls and its half width, in its camp's frame


def pitched_roof(X, P, profile):
    """
    A hall's pitched roof from a profile (|z| -> height above F and kind: 0 a stair, 1 a stair on a block, 2 a slab,
    3 a block): gables framed with a truss, eaves hanging out, dormers on the steep slope, finials and a fleche.
    The steep profile carries a rose window in each gable.
    """
    W1, W2, DK, TL, CH = P['stone']
    aged_, top, steep = P['wallf'], profile[0][0] + 1, profile is STEEP
    pane = P['glass'].replace('_pane', '')
    for x in (X0, X1):
        for q, (ry, kind) in profile.items():
            if q > ZH: continue
            for y in range(F + 17, F + ry):
                rel = y - F
                rose = steep and q * q + (rel - 27) ** 2 <= 6.5
                frame = rel % 5 == 3 or q == 0 or (19 <= rel <= 22 and q == rel - 18)
                for z in {q, -q}:
                    X.set(x, y, z, (P['light'] if q == 0 and rel == 27 else pane) if rose else P['truss'] if frame else aged_(x, y, z))
    for x in range(X0 - 1, X1 + 2):
        for q, (ry, kind) in profile.items():
            for s in ((-1, 1) if q else (1,)):
                if kind <= 1:
                    X.set(x, F + ry, s * q, stairs(P['roof'], 'north' if s > 0 else 'south'))
                    if kind: X.set(x, F + ry - 1, s * q, P['roof'])
                else:
                    X.set(x, F + ry, s * q, slab(P['roof']) if kind == 2 else P['roof'])
        X.set(x, F + top, 0, P['gem'] if (x - X0) % 6 == 3 else slab(P['roof']))
    for x in (80, 85, 90) if profile is not BROAD else ():           # dormers standing on the steep slope
        for s in (-1, 1):
            for dx in (-1, 0, 1):
                for y in (F + 18, F + 19): X.set(x + dx, y, s * 11, W1 if dx else P['glass'])
                X.set(x + dx, F + 20, s * 11, W1 if dx else CH)
                X.set(x + dx, F + 20, s * 10, P['roof'])
                for q in (11, 10): X.set(x + dx, F + 21, s * q, P['roof'] if dx == 0 else stairs(P['roof'], 'east' if dx < 0 else 'west'))
            X.set(x, F + 22, s * 11, slab(P['roof']))
    for x in (X0 - 1, X1 + 1):                                       # finials on the gable peaks
        X.set(x, F + top, 0, P['gem'])
        X.set(x, F + top + 1, 0, wall(DK))
        X.set(x, F + top + 2, 0, 'end_rod[facing=up]')
    mid = (X0 + X1) // 2                                             # and a fleche on the middle of the ridge
    for dx, dz, toward in ((1, 0, 'west'), (-1, 0, 'east'), (0, 1, 'north'), (0, -1, 'south')): X.set(mid + dx, F + top, dz, stairs(P['roof'], toward))
    for y in range(F + top, F + top + 5): X.set(mid, y, 0, P['roof'] if y < F + top + 3 else wall(DK))
    X.set(mid, F + top + 5, 0, P['gem'])
    X.set(mid, F + top + 6, 0, 'end_rod[facing=up]')
    for cz in (-12, 12):
        turret(X, X0, cz, F + 1, F + 18, P)
        turret(X, X1, cz, F + 1, F + 18, P)
    for x in (80, 86, 92):                                           # tie beams
        for z in range(-10, 11): X.set(x, F + 17, z, P['beam'] + '[axis=z]')
    for z in range(-3, 4): X.set(X0 - 2, F + 25, z, 'stripped_dark_oak_wood[axis=z]')      # the yard the great banner hangs from
    X.set(X0 - 1, F + 25, -2, 'stripped_dark_oak_wood[axis=x]')
    X.set(X0 - 1, F + 25, 2, 'stripped_dark_oak_wood[axis=x]')
    cloth(X.setif, [(X0 - 2, F + 24, z) for z in range(-2, 3)], 12, (-1, 0), P['cloth'], P['cloth2'], P['stripe'], phase=1.0)
    if P['key'] == 'emerald':                                        # the Enclave's roof is grown over: leaves on the slope, berries under the eaves
        for x in range(X0 - 1, X1 + 2):
            for q, (ry, kind) in profile.items():
                for s in (-1, 1):
                    if q and fbm(x * 1.7, s * q * 1.7 + ry, 4, 53) > 0.33: X.setif(x, F + ry + 1, s * q, 'flowering_azalea_leaves' if (x + q) % 5 == 0 else 'azalea_leaves')
            for s in (-1, 1):
                n = int(rnd3(x, 3, s, 59) * 4)
                for j in range(n):
                    X.setif(x, F + 14 - j, s * 13, 'cave_vines[age=25,berries=true]' if j == n - 1 else 'cave_vines_plant[berries=%s]' % ('true' if j % 2 else 'false'))


def keep_roof(X, P):
    """
    The Bastion's roof: a flat deck behind a battlement on corbels, a square tower with a sun-brazier at each corner,
    a dome of gold ribs and yellow glass over the hall's heart, and over the gate a stepped frontispiece carrying
    a gilded sun.
    """
    W1, W2, DK, TL, CH = P['stone']
    aged_ = P['wallf']
    cx = (X0 + X1) // 2
    for x in range(X0, X1 + 1):
        for z in range(-ZH, ZH + 1):
            d = math.hypot(x - cx, z)
            if d > 6.3: X.set(x, F + 17, z, TL if (x + z) % 3 == 0 else W2)
            elif d > 5.2: X.set(x, F + 17, z, CH)                    # the drum the dome stands on
    for x in range(X0 - 1, X1 + 2):                                  # the battlement
        for z in range(-ZH - 1, ZH + 2):
            if X0 <= x <= X1 and abs(z) <= ZH: continue
            X.set(x, F + 16, z, W1)
            X.set(x, F + 17, z, DK)
            X.set(x, F + 18, z, W1)
            if (x + z) % 2 == 0: X.set(x, F + 19, z, slab(DK) if (x + z) % 4 else W1)
    for dx in range(-6, 7):                                          # the sun dome
        for dz in range(-6, 7):
            for dy in range(0, 7):
                r = math.sqrt(dx * dx + dz * dz + dy * dy)
                if 4.7 < r <= 5.7: X.set(cx + dx, F + 18 + dy, dz, 'gold_block' if dx == 0 or dz == 0 or abs(dx) == abs(dz) else 'yellow_stained_glass')
    X.set(cx, F + 24, 0, 'gold_block')
    X.set(cx, F + 25, 0, 'end_rod[facing=up]')
    for y in (F + 22, F + 21): X.set(cx, y, 0, 'iron_chain[axis=y]')
    X.set(cx, F + 20, 0, P['light'])
    for tx in (X0, X1):                                              # the corner towers
        for tz in (-12, 12):
            for dx in range(-3, 4):
                for dz in range(-3, 4):
                    m = max(abs(dx), abs(dz))
                    x, z = tx + dx, tz + dz
                    if m == 3:
                        toward = 'east' if dx == -3 else 'west' if dx == 3 else 'south' if dz < 0 else 'north'
                        X.set(x, F + 24, z, slab(W1, top=True) if abs(dx) == abs(dz) else stairs(W1, toward, top=True))
                        X.set(x, F + 25, z, W1)
                        if (dx + dz) % 2 == 0: X.set(x, F + 26, z, W1)
                        continue
                    for y in range(F + 1, F + 25):
                        slit = m == 2 and (dx == 0 or dz == 0) and (y - F) % 8 in (5, 6)
                        X.set(x, y, z, 'air' if slit else DK if (y - F) % 8 == 1 else CH if m == 2 and abs(dx) == abs(dz) else aged_(x, y, z))
            X.set(tx, F + 25, tz, 'gold_block')
            X.set(tx, F + 26, tz, 'campfire[lit=true]')
            if tx == X0:                                             # a long banner down each front tower
                for z in (tz - 1, tz, tz + 1): X.set(tx - 3, F + 23, z, 'stripped_birch_wood[axis=z]')
                cloth(X.setif, [(tx - 3, F + 22, z) for z in (tz - 1, tz, tz + 1)], 12, (-1, 0), P['cloth'], P['cloth2'], P['stripe'], phase=tz)
    for z in range(-5, 6):                                           # the frontispiece and its sun
        for y in range(F + 17, F + (26 if abs(z) <= 3 else 24)):
            d = math.hypot(z, y - (F + 21))
            X.set(X0, y, z, P['light'] if d <= 1.2 else 'gold_block' if d <= 2.4 else DK if y == F + 17 else aged_(X0, y, z))
        X.set(X0, F + (26 if abs(z) <= 3 else 24), z, slab(DK) if z % 2 else W1)
    for z, y in ((-3, 21), (3, 21), (0, 24)): X.set(X0 - 1, F + y, z, 'gold_block')


def interior(hub, X, P):
    """
    Inside a hall: an inlaid border and a three-stripe runner on the floor; timber posts dividing the nave from two
    aisles of alcoves (an armour rack, bunks, a study corner; a drill corner, a mess table, stores); a dais with the
    captain's seat under a tapestry; and behind the aisles two closed chambers, a study and the faction's own
    (forge, chart room, vault or herbarium). Chandeliers, bracket lanterns and candles throughout.
    """
    W1, W2, DK, TL, CH = P['stone']
    aged_, lamp, plank, key = P['wallf'], P['lamp'], P['plank'], P['key']
    keep = P['style'] == 'keep'
    candle = lambda n: '%s[candles=%d,lit=true]' % (P['candle'], n)
    for x in range(78, 93):                                          # the floor's border, studded at the corners
        for z in range(-9, 10):
            if x in (78, 92) or abs(z) == 9: X.set(x, F, z, P['stud'] if x in (78, 92) and abs(z) == 9 else P['inlay'])
    for x in range(77, 89):
        for z in (-1, 0, 1): X.set(x, F + 1, z, P['carpet'] if z == 0 else P['carpet2'])
    for x, z in ((78, -6), (78, 6), (84, -3), (84, 3)): X.set(x, F, z, P['light'])
    for x in (80, 86):                                               # posts, brackets and the low walls between alcoves
        for s in (-1, 1):
            for y in range(F + 1, F + 17): X.set(x, y, s * 6, DK if y == F + 1 else CH if y == F + 16 else P['beam'] + '[axis=y]')
            X.set(x, F + 15, s * 5, P['truss'])
            X.set(x, F + 14, s * 5, lamp + '[hanging=true]')
            for z in (8, 9, 10):
                X.set(x, F + 1, s * z, DK)
                X.set(x, F + 2, s * z, wall(DK))
            X.set(x, F + 3, s * 8, lamp + '[hanging=false]')
    if keep:
        for x, z in ((78, -8), (78, 8), (84, -8), (84, 8), (92, 0)): X.set(x, F + 16, z, lamp + '[hanging=true]')
    else:
        for x in (80, 86, 92):                                       # chandeliers under the tie beams
            X.set(x, F + 16, 0, 'iron_chain[axis=y]')
            X.set(x, F + 15, 0, lamp + '[hanging=true]')
    # the north aisle: armour rack, bunks, study corner
    hub.stand(X, 78, F + 1, -9, 0, leather(P['dye'], 'iron_sword'))
    X.set(77, F + 1, -10, 'smithing_table')
    X.set(79, F + 1, -10, 'grindstone[face=floor,facing=north]')
    for x in (81, 83):
        X.set(x, F + 1, -10, '%s_bed[facing=north,part=head]' % P['banner'])
        X.set(x, F + 1, -9, '%s_bed[facing=north,part=foot]' % P['banner'])
    X.set(82, F + 1, -10, plank)
    X.set(82, F + 2, -10, candle(2))
    X.set(82, F + 5, -10, '%s_wall_banner[facing=south]' % P['banner'])
    for y in (F + 1, F + 2): X.set(85, y, -10, 'bookshelf')
    X.set(87, F + 1, -10, 'cartography_table')
    X.set(88, F + 1, -10, 'bookshelf')
    X.set(88, F + 2, -10, P['pot'])
    # the south aisle: drill corner, mess table, stores
    X.set(78, F + 1, 9, P['fence'])
    X.set(78, F + 2, 9, 'hay_block[axis=y]')
    X.set(78, F + 3, 9, 'carved_pumpkin[facing=north]')
    X.set(77, F + 1, 10, 'hay_block[axis=x]')
    X.set(77, F + 2, 10, 'target')
    for x in (82, 83, 84): X.set(x, F + 1, 9, slab(plank, top=True))
    X.set(82, F + 2, 9, candle(1))
    X.set(84, F + 2, 9, P['pot'])
    for x in (82, 84): X.set(x, F + 1, 10, stairs(plank, 'south'))
    X.set(83, F + 1, 8, stairs(plank, 'north'))
    X.set(82, F + 5, 10, '%s_wall_banner[facing=north]' % P['banner'])
    for x, y in ((87, 1), (88, 1), (88, 2)): X.set(x, F + y, 10, 'hay_block[axis=y]' if (x + y) % 2 else P['beam'] + '[axis=x]')
    # the dais, the seat and the tapestry behind it
    for z in range(-5, 6):
        X.set(89, F + 1, z, stairs(P['accent2'], 'east'))
        for x in range(90, 94): X.set(x, F + 1, z, P['accent2'])
    X.set(92, F + 2, 0, stairs(plank, 'east'))
    for z in (-1, 1):
        X.set(92, F + 2, z, wall(DK))
        X.set(92, F + 3, z, lamp + '[hanging=false]')
    for z in (-5, 5):
        X.set(90, F + 2, z, wall(DK))
        X.set(90, F + 3, z, candle(3))
    for z in range(-2, 3):
        X.set(93, F + 10, z, P['truss'])
        for y in range(F + 3, F + 10):
            emblem = abs(z) + abs(y - (F + 6)) <= 1
            X.set(93, y, z, P['gem'] if emblem else P['cloth2'] if abs(z) == 2 else P['stripe'] if y == F + 8 else P['cloth'])
    for z in (-4, 4): X.set(93, F + 6, z, '%s_wall_banner[facing=west]' % P['banner'])
    # the two chambers either side of the dais
    for s in (-1, 1):
        for x in range(89, 94):
            for y in range(F + 1, F + 9): X.set(x, y, s * 6, DK if y in (F + 1, F + 8) else aged_(x, y, s * 6))
        for z in range(6, 11):
            for y in range(F + 1, F + 9):
                door = z in (8, 9) and y <= F + 3
                X.set(89, y, s * z, 'air' if door else DK if y in (F + 1, F + 8) else aged_(89, y, s * z))
            if z in (8, 9): X.set(89, F + 4, s * z, CH)
        for x in range(89, 94):
            for z in range(6, 11): X.set(x, F + 9, s * z, slab(plank))
        X.set(91, F + 8, s * 8, lamp + '[hanging=true]')
        for x in range(90, 93):
            for z in (7, 8): X.set(x, F + 1, s * z, P['carpet'])
    for z in (-10, -9, -8):                                          # the study: a wall of books, a desk, a chair
        for y in (F + 1, F + 2, F + 3): X.set(93, y, z, 'bookshelf')
    for x in (91, 92): X.set(x, F + 1, -10, slab(plank, top=True))
    X.set(92, F + 2, -10, candle(2))
    X.set(91, F + 2, -10, P['pot'])
    X.set(91, F + 1, -9, stairs(plank, 'south'))
    if key == 'crimson':                                             # the forge
        for z in (8, 9): X.set(93, F + 1, z, 'blast_furnace[facing=west,lit=true]')
        X.set(93, F + 1, 10, 'anvil[facing=north]')
        X.set(92, F + 1, 10, 'smithing_table')
        X.set(91, F + 1, 10, 'lava_cauldron')
        X.set(90, F + 1, 10, 'grindstone[face=floor,facing=north]')
    elif key == 'cobalt':                                            # the chart room
        X.set(93, F + 1, 8, 'cartography_table')
        X.set(93, F + 1, 9, 'lectern[facing=west,has_book=false]')
        for y in (F + 1, F + 2): X.set(93, y, 10, 'bookshelf')
        X.set(92, F + 1, 10, 'water_cauldron[level=3]')
        X.set(91, F + 1, 10, 'sea_lantern')
        X.set(91, F + 2, 10, candle(3))
    elif key == 'aurelian':                                          # the vault
        for z in (8, 9, 10): X.set(93, F + 1, z, 'gold_block')
        for z in (9, 10): X.set(93, F + 2, z, 'raw_gold_block')
        X.set(92, F + 1, 10, 'gold_block')
        X.set(92, F + 2, 10, candle(4))
        X.set(91, F + 1, 10, 'bell[attachment=floor,facing=north]')
        X.set(90, F + 1, 10, 'raw_gold_block')
    else:                                                            # the herbarium
        X.set(93, F + 1, 8, 'composter[level=6]')
        X.set(93, F + 1, 9, 'moss_block')
        X.set(93, F + 2, 9, 'flowering_azalea')
        X.set(93, F + 1, 10, 'beehive[facing=west,honey_level=5]')
        X.set(92, F + 1, 10, 'moss_block')
        X.set(92, F + 2, 10, 'potted_azalea_bush')
        X.set(91, F + 1, 10, 'oak_fence')
        X.set(91, F + 2, 10, 'potted_flowering_azalea_bush')
        X.set(90, F + 1, 10, 'moss_block')
        X.set(90, F + 2, 10, 'potted_oxeye_daisy')


def yards(hub, X, P):
    """
    The two yards beside a camp's forecourt. The inner one (toward the ring bridge) is crossed by the plank way
    from that bridge; each faction fills both in its own way: forge and drill yard, tide pool and chart desks,
    sundial and honour guard, grove pond and herb beds.
    """
    W1, W2, DK, TL, CH = P['stone']
    key, px, lamp = P['key'], P['ring'], P['lamp']
    for x in range(53, 76):
        for a in range(11, 18):
            for s in (-1, 1): X.set(x, F, s * a, DK if a in (11, 17) or x in (53, 75) else W2 if (x + a) % 2 else TL)
    for z in range(-26, -10):                                        # the way in from the ring bridge
        for x in range(px - 2, px + 3): X.set(x, F, z, DK if abs(x - px) == 2 else P['path'])
    free = lambda x: abs(x - px) > 3                                 # clear of that way
    if key == 'crimson':
        for x, z in ((62, 12), (72, 12), (62, 16), (72, 16)):        # the forge shed
            for y in range(F + 1, F + 5): X.set(x, y, z, DK if y == F + 1 else P['beam'] + '[axis=y]')
        for x in range(61, 74):
            for z in range(11, 18): X.set(x, F + 5, z, slab(P['roof']))
        for y in range(F + 1, F + 9): X.set(63, y, 16, 'nether_bricks')
        X.set(63, F + 9, 16, 'campfire[lit=true]')
        for x in (64, 65): X.set(x, F + 1, 16, 'blast_furnace[facing=north,lit=true]')
        X.set(67, F + 1, 16, 'anvil[facing=east]')
        X.set(69, F + 1, 16, 'smithing_table')
        X.set(70, F + 1, 16, 'grindstone[face=floor,facing=north]')
        X.set(71, F + 1, 15, 'lava_cauldron')
        X.set(67, F + 4, 14, lamp + '[hanging=true]')
        hub.stand(X, 55, F + 1, 14, 180, leather(P['dye'], 'iron_axe'))
        hub.stand(X, 58, F + 1, 14, 180, {'head': 'iron_helmet', 'chest': 'iron_chestplate', 'legs': 'iron_leggings', 'feet': 'iron_boots', 'mainhand': 'iron_sword'})
        for x in (54, 56, 67, 70, 73):                               # the drill yard
            if not free(x): continue
            X.set(x, F + 1, -15, P['fence'])
            X.set(x, F + 2, -15, 'hay_block[axis=y]' if x < 60 else 'target')
            if x < 60: X.set(x, F + 3, -15, 'carved_pumpkin[facing=south]')
        hub.npc('red-smith', '<red>Forgemaster Ned', 'talk', 'toolsmith', 'savanna', X.spot(67, F + 1, 13, 180),
                ['Every blade in the Hold is quenched in the old furnaces. The Citadel gave us those, at least.',
                 '<blue>Cobalt</blue> measure twice and cut once. We cut twice. Saves on measuring.',
                 'The <yellow>Bastion</yellow> pays in gold and counts it back out of your hand. Still, gold makes a fine pommel.',
                 'Bring me a flag and I will hang it over the forge. The heat keeps the colour bright.'])
    elif key == 'cobalt':
        for x in range(57, 72):                                      # the tide pool
            for z in range(12, 17):
                rim_ = x in (57, 71) or z in (12, 16)
                X.set(x, F, z, 'prismarine_bricks' if rim_ else 'water')
                if not rim_:
                    X.set(x, F - 1, z, 'sea_lantern' if (x + z) % 4 == 0 else 'dark_prismarine')
                    if rnd3(x, 1, z, 61) < 0.16: X.set(x, F + 1, z, 'lily_pad')
        for y in (F, F + 1, F + 2): X.set(64, y, 14, 'prismarine_wall' if y > F else 'prismarine_bricks')
        X.set(64, F + 3, 14, 'sea_lantern')
        for x in (55, 73): lamp_post(X, x, 14, F + 1, lamp)
        for x, state in ((55, 'cartography_table'), (56, 'lectern[facing=south,has_book=false]'), (67, 'water_cauldron[level=3]'),
                         (70, 'blue_banner[rotation=8]'), (73, 'cartography_table')):      # the chart desks
            if free(x): X.set(x, F + 1, -15, state)
        hub.npc('blue-tidekeeper', '<blue>Tidekeeper Helen', 'talk', 'fisherman', 'taiga', X.spot(64, F + 1, 11, 180),
                ['The sea-gates fell with the Citadel. We kept the water. It remembers the way down.',
                 '<red>Crimson</red> call it patience when they mean we are slow. We call it not falling off the map.',
                 'The <green>Enclave</green> talk to trees. I talk to tides. Only one of us gets an answer twice a day.',
                 'Read the wind before you bridge. The void does not take apologies.'])
    elif key == 'aurelian':
        for dx in range(-4, 5):                                      # the sundial
            for dz in range(-2, 3):
                d = math.hypot(dx, dz)
                if d <= 3.6: X.set(64 + dx, F, 14 + dz, 'gold_block' if d < 0.5 or (d > 2.6 and (dx == 0 or dz == 0)) else 'smooth_sandstone' if d <= 2.6 else 'yellow_terracotta')
        X.set(64, F + 1, 14, 'gold_block')
        X.set(64, F + 2, 14, 'end_rod[facing=up]')
        for x, y in ((55, 1), (56, 1), (55, 2)): X.set(x, F + y, 15, 'raw_gold_block' if y == 2 else 'gold_block')
        for x in (71, 73):                                           # the muster bell
            for y in (F + 1, F + 2): X.set(x, y, 15, P['fence'])
        for x in (71, 72, 73): X.set(x, F + 3, 15, 'stripped_birch_log[axis=x]')
        X.set(72, F + 2, 15, 'bell[attachment=ceiling,facing=north]')
        gold = {'head': 'golden_helmet', 'chest': 'golden_chestplate', 'legs': 'golden_leggings', 'feet': 'golden_boots', 'mainhand': 'golden_sword'}
        for x in (px - 4, px + 4):                                   # the honour guard either side of the way
            X.set(x, F + 1, -14, slab(DK))
            hub.stand(X, x, F + 1.5, -14, 180, gold)
            brazier(X, x, -16, F + 1, P['fire'])
        hub.npc('yellow-paymaster', '<yellow>Paymaster Charles', 'talk', 'librarian', 'desert', X.spot(60, F + 1, 13, 180),
                ['The treasury was ours to guard before the Sundering. It still is. Somebody has to pay for all this.',
                 'Two coins a kill, five for an objective, ten for the win. I did not set the rates. I only resent them.',
                 '<red>Crimson</red> and <blue>Cobalt</blue> both owe us for the gate tolls. They pay in excuses.',
                 'When the Citadel fell, three teams ran for a shard. We stood at the gate and counted them out.'])
    else:
        for dx in range(-4, 5):                                      # the grove pond
            for dz in range(-2, 3):
                d = math.hypot(dx * 0.8, dz)
                if d <= 2.2:
                    X.set(62 + dx, F, 14 + dz, 'water')
                    X.set(62 + dx, F - 1, 14 + dz, 'moss_block')
                    if rnd3(dx, 2, dz, 67) < 0.25: X.set(62 + dx, F + 1, 14 + dz, 'lily_pad')
                elif d <= 3.3:
                    X.set(62 + dx, F, 14 + dz, 'moss_block')
                    if rnd3(dx, 3, dz, 71) < 0.4: X.set(62 + dx, F + 1, 14 + dz, ('fern', 'short_grass', 'azalea', 'oxeye_daisy')[int(rnd3(dx, 4, dz, 73) * 4)])
        tx, tz = X.at(71, 14)
        for dx in (-2, -1, 0, 1, 2):
            for dz in (-2, -1, 0, 1, 2):
                if abs(dx) + abs(dz) <= 3: X.set(71 + dx, F, 14 + dz, 'moss_block' if (dx + dz) % 2 else 'grass_block')
        spline_tree(hub.w, tx, F + 1, tz, 231, height=9, spread=4.4, wood='oak', leaf='oak_leaves', leaf2='flowering_azalea_leaves')
        X.set(55, F + 1, 15, 'bee_nest[facing=north,honey_level=5]')
        X.set(55, F + 1, 14, 'moss_block')
        X.set(55, F + 2, 14, 'flowering_azalea')
        for x in range(54, 75):                                      # the herb beds
            if not free(x) or x % 5 == 4: continue
            X.set(x, F + 1, -15, 'moss_block')
            X.set(x, F + 2, -15, ('flowering_azalea', 'fern', 'azalea', 'oxeye_daisy', 'allium')[x % 5])
            X.set(x, F + 1, -14, slab(DK))
        hub.npc('green-warden', '<green>Grovewarden Hazel', 'talk', 'farmer', 'jungle', X.spot(58, F + 1, 12, 180),
                ['The gardens hung under the Citadel for an age before anyone built a wall on it. We tended them then. We tend them now.',
                 'The other three teams call this a base. It is a garden with visitors.',
                 'The <yellow>Bastion</yellow> counted the cost of the Sundering in gold. We counted it in trees.',
                 'Take a flag if you must. Step on the azaleas and we will have words.'])


# each camp's people: the leader (name, profession, villager type, lines), then the quartermaster, the scout and the recruit
PEOPLE = {
    'crimson': (('Captain Ruth Ashby', 'weaponsmith', 'savanna',
                 ["We were the Citadel's forge-legion. Every gate, chain and blade on this rock was hammered out by a Crimson hand.",
                  'When the Heartcrystal split, the forges were sliding into the void. We held them with our backs to the fire. That is how the Hold began.',
                  'Our creed is short: first to the flag, last to leave it.',
                  '<blue>Cobalt</blue> took our flag the night the Accord was signed. Edward Hale calls it salvage. I call it theft.',
                  'The <yellow>Bastion</yellow> lent us gold to rebuild and have charged interest ever since. Eleanor Sterling counts it daily.',
                  'The <green>Enclave</green> say our smoke kills their moss. Martha Greenwood sends me a fern every spring to make the point.',
                  'Pick a kit on the <yellow>Armoury Terrace</yellow> before the horn. Then come and earn the red.']),
                ('Quartermaster George', ['Golden apples for every kill. They stack with the ones in your kit.',
                                          'Out of blocks? Break the map: everything but spawns and objectives breaks.',
                                          'The Bastion sell us iron at twice the price and call it friendship. I weigh every bar.']),
                ('Scout Emma', ["I've scouted every island. The void is the deadliest player on all of them.",
                                'Cobalt scouts draw the map. Enclave scouts hide in it. We just run across it.',
                                'Long shots count double in my book. Not on the scoreboard, though.']),
                ('Recruit Tom', ["I've hit that dummy a hundred times. It's winning.",
                                 "Sarge says if I lose the flag again I'm on bellows duty. Forever.",
                                 'Is it true the crystal in the sanctum hums when all four captains are angry at once?'])),
    'cobalt': (('Captain Edward Hale', 'weaponsmith', 'taiga',
                ['We were the sea-gate wardens. We raised the water, kept the charts and steered the Citadel through every storm.',
                 'In the Sundering the sea-gates tore loose. We rode them down, cut them free and climbed back with the charts. Cobalt has planned ahead ever since.',
                 'Our creed: read the wind, then move. Bridge fast, bridge wide, never bridge alone.',
                 'Ruth Ashby says I stole a <red>Crimson</red> flag. It was lying on the ground with nobody near it. That is salvage.',
                 'The <yellow>Bastion</yellow> tax every skiff that docks. We built the dock. Think about that.',
                 'The <green>Enclave</green> let their roots grow through our pipes. Martha Greenwood calls it nature. I call it sabotage with leaves.',
                 'Kits are on show on the <yellow>Armoury Terrace</yellow>, west of the sanctum. Choose before the horn, not after.']),
               ('Quartermaster Frank', ['Every kit is counted out and counted back in. Mostly back in.',
                                        'Golden apples for every kill, and they stack. I keep the tally.',
                                        'Crimson return our borrowed tools bent. The Enclave return them planted.']),
               ('Scout Lucy', ['I have charted every map in the vote. Ask me where the short bridges are.',
                               'The Bastion never leave their hill. Makes them easy to find and hard to shift.',
                               'The void is patient. Be more patient.']),
               ('Recruit Billy', ['They made me learn the wind tables before they gave me a sword.',
                                  'Captain Hale says a plan is worth ten charges. Crimson keep charging anyway.',
                                  'Do you think the skiff pilot would let me steer? Just once?'])),
    'aurelian': (('Marshal Eleanor Sterling', 'armorer', 'desert',
                  ['We were the sun-guard: keepers of the gate, the vault and the oath. Two of the three still stand.',
                   'When the Citadel broke, three teams ran for a shard. We stayed at the gate and counted them out. Somebody had to know who was left.',
                   'Our creed: hold the high ground and keep your word. The hill pays whoever stands on it longest.',
                   '<red>Crimson</red> owe us for the rebuilding. Ruth Ashby pays in dented helmets.',
                   '<blue>Cobalt</blue> owe us for the gate tolls. Edward Hale pays in very long letters.',
                   'The <green>Enclave</green> owe us nothing, which worries me more. Martha Greenwood is too patient to be harmless.',
                   'Choose a kit on the <yellow>Armoury Terrace</yellow> and know what it costs. I signed the Accord first. Do not make me regret it.']),
                 ('Quartermaster Albert', ['Sign for your gear. Yes, all of it. Yes, the apples too.',
                                           'Two coins a kill, five for an objective, ten for the win. Spend them on the terrace.',
                                           'Cobalt haggle, Crimson shout, the Enclave pay in honey. I prefer the honey.']),
                 ('Scout Clara', ['From the towers you can see all four teams. Crimson is the smoky one.',
                                  'A Bastion scout does not sneak. We arrive in good order, exactly when expected.',
                                  'Watch the Enclave on wool maps. You will not see them until your tower is shorter.']),
                 ('Recruit Peter', ['I polish the sun on the gatehouse every morning. It is very high up.',
                                    'The Marshal says the Bastion has never lost the high ground. I am not allowed to ask about the low ground.',
                                    'Is it true Crimson recruits get to hit things on their first day?'])),
    'emerald': (('Elder Martha Greenwood', 'shepherd', 'jungle',
                 ['We were the gardeners of the under-Citadel. The hanging gardens fed every hall above them.',
                  'When the stone fell, the roots held. Ours is the only shard that did not crack, because it was never only stone.',
                  'Our creed: patience. Break their wool while they are busy admiring yours.',
                  '<red>Crimson</red> burn what they cannot hit. I send Ruth Ashby a fern each spring. She has killed eleven.',
                  '<blue>Cobalt</blue> drew a map of our grove and got it wrong on purpose, so nobody else could find it. I respect that. I have not forgiven it.',
                  'The <yellow>Bastion</yellow> offered to buy the garden. Eleanor Sterling thinks everything has a price. The bees disagreed.',
                  'Go to the <yellow>Armoury Terrace</yellow> and choose something quiet. The loud kits scare the bees.']),
                ('Quartermaster Joan', ['Take what you need and bring back what is left. The grove keeps count.',
                                        'Golden apples for every kill. Ours are grown, not minted.',
                                        'The Bastion wanted a receipt for a basket of pears. I gave them a pear.']),
                ('Scout Robin', ['I know every map by its trees. The ones with no trees I know by where I fell.',
                                 'Cobalt scouts look at charts. We look at the ground. The ground is usually right.',
                                 'Crimson are easy to track. Follow the scorch marks.']),
                ('Recruit Daisy', ['I was told to water the wool. I think it was a joke. I watered it anyway.',
                                   'Elder Greenwood says patience wins. I have been patient all morning and nothing has happened.',
                                   'Do the Bastion really count their coins before bed?'])),
}


def camp(hub, name):
    """
    A faction's camp, written once (for the south-east) and stood in its own frame: a forecourt with the faction's
    emblem inlaid on a plank way, azalea beds, streetlamps and braziers; a yard either side (see yards); and the
    hall: walls in arched bays between timber pillars with recessed lancets, sills, relief bands and a corbel
    course; a porch with a stepped pointed arch; the roof its style calls for; and a furnished interior.
    """
    P, X = FACTIONS[name], camp_frame(hub.w, name)
    W1, W2, DK, TL, CH = P['stone']
    aged_ = P['wallf']
    for x in range(51, 76):                                          # the forecourt: a plank way edged in the faction's inlay
        for z in range(-10, 11):
            a = abs(z)
            if a <= 2: s = P['path']
            elif a == 3: s = P['stud'] if x % 4 == 3 else P['inlay']
            elif a == 10 or x == 51: s = DK
            else: s = TL if x % 4 == 1 and a % 3 == 0 else aged_(x, 0, z)
            m = abs(x - 63) + a
            if m <= 5: s = DK if m == 5 else P['accent'] if m >= 3 else P['gem'] if m else P['light']
            X.set(x, F, z, s)
    for sz in (-1, 1):
        brazier(X, 53, sz * 7, F + 1, P['fire'])
        for bx, by, bz in ((73, 1, 9), (73, 2, 9), (72, 1, 9), (73, 1, 8)): X.set(bx, F + by, sz * bz, 'hay_block[axis=y]' if by == 1 else P['beam'] + '[axis=x]')
        for xc in (59, 67):                                          # beds of azalea beside the way
            for dx in (-1, 0, 1):
                X.set(xc + dx, F + 1, sz * 5, 'moss_block')
                X.set(xc + dx, F + 2, sz * 5, 'flowering_azalea' if dx == 0 else 'azalea')
            X.set(xc - 2, F + 1, sz * 5, stairs(DK, 'east'))
            X.set(xc + 2, F + 1, sz * 5, stairs(DK, 'west'))
        for xl in (54, 63, 74): lamp_post(X, xl, sz * (7 if xl == 63 else 4), F + 1, P['lamp'])      # streetlamps
        for dx in (0, 1):                                            # a glazed medallion
            for dz in (0, 1): X.set(70 + dx, F, sz * (7 + dz), '%s[facing=%s]' % (P['glazed'], ('north', 'east', 'west', 'south')[dx + 2 * dz]))
    for x in range(X0, X1 + 1):                                      # the hall's floor and shell
        for z in range(-ZH, ZH + 1):
            X.set(x, F, z, TL if (x + z) % 2 else W2)
            if x in (X0, X1) or abs(z) == ZH:
                for y in range(F + 1, F + 17): X.set(x, y, z, DK if y in (F + 1, F + 16) else TL if y == F + 12 else aged_(x, y, z))

    def pillar(x, z):
        for y in range(F + 1, F + 16): X.set(x, y, z, DK if y <= F + 2 or y >= F + 14 else P['beam'] + '[axis=y]')

    def lancet(x, z):
        for y in range(F + 4, F + 10): X.set(x, y, z, P['glass'])
        X.set(x, F + 10, z, CH)
        X.set(x, F + 2, z, P['accent'])

    for s in (-1, 1):                                                # the side walls: arched bays between timber pillars
        z, out = s * ZH, s * (ZH + 1)
        toward = 'north' if s > 0 else 'south'
        for x in range(X0 + 1, X1):
            k = (x - 78) % 4
            if k == 0:
                pillar(x, out)
                continue
            X.set(x, F + 1, out, stairs(DK, toward))                 # the plinth moulding
            X.set(x, F + 15, out, stairs(W1, toward, top=True))      # the corbel course under the eave
            for y in (F + 13, F + 14): X.set(x, y, z, P['accent'] if (x + y) % 2 else CH)       # a relief band
            if k == 2:
                lancet(x, z)
                X.set(x, F + 3, out, stairs(DK, toward))             # its sill
                X.set(x, F + 11, out, CH)                            # the keystone of the bay's arch
            else:
                X.set(x, F + 10, out, stairs(W1, 'west' if k == 1 else 'east', top=True))
                X.set(x, F + 11, out, DK)
            X.set(x, F + 12, out, DK)
    for z in (-9, -5, 5, 9): pillar(X0 - 1, z)                       # the front
    for z in (-8, -4, 0, 4, 8): pillar(X1 + 1, z)                    # and the back
    for z in range(-10, 11):
        if abs(z) not in (5, 9): X.set(X0 - 1, F + 15, z, stairs(W1, 'east', top=True))
        if abs(z) > 5 and abs(z) != 9: X.set(X0 - 1, F + 1, z, stairs(DK, 'east'))
        if z % 4: X.set(X1 + 1, F + 15, z, stairs(W1, 'west', top=True))
    for sz in (-1, 1):
        zc = sz * 7                                                  # a lancet in an arched bay each side of the porch
        lancet(X0, zc)
        X.set(X0 - 1, F + 3, zc, stairs(DK, 'east'))
        X.set(X0 - 1, F + 10, zc - 1, stairs(W1, 'north', top=True))
        X.set(X0 - 1, F + 10, zc + 1, stairs(W1, 'south', top=True))
        for dz in (-1, 0, 1):
            X.set(X0 - 1, F + 11, zc + dz, DK if dz else CH)
            for y in (F + 13, F + 14): X.set(X0, y, zc + dz, P['accent'] if (zc + dz + y) % 2 else CH)
    for y in range(F + 1, F + 12):                                   # the portal
        hw = 3 if y <= F + 8 else F + 11 - y
        for z in range(-4, 5):
            if abs(z) <= hw: X.set(X0, y, z, 'air')
            elif abs(z) == hw + 1: X.set(X0, y, z, P['accent2'])
    X.set(X0, F + 12, 0, P['gem'])
    for y in range(F + 1, F + 9):                                    # and its porch: jambs and a stepped pointed arch standing proud
        for z in (-4, 4): X.set(X0 - 1, y, z, DK if y == F + 1 else P['accent2'])
    for i in range(3):
        for sz in (-1, 1):
            X.set(X0 - 1, F + 9 + i, sz * (3 - i), stairs(P['accent2'], 'north' if sz < 0 else 'south', top=True))
            X.set(X0 - 1, F + 9 + i, sz * (4 - i), P['accent2'])
    for z in (-1, 0, 1): X.set(X0 - 1, F + 12, z, P['accent2'] if z else P['gem'])
    if P['style'] == 'keep': keep_roof(X, P)
    else: pitched_roof(X, P, {'gambrel': GAMBREL, 'steep': STEEP, 'broad': BROAD}[P['style']])
    interior(hub, X, P)
    yards(hub, X, P)
    lx, lz = X.at(64, 0)
    hub.label(lx + 0.5, TITLE_Y, lz + 0.5, P['title'], P['colour'], P['motto'], 1.4)
    side, c = P['side'], P['colour']
    (who, job, kind, lines), quarter, scout, recruit = PEOPLE[name]
    hub.npc(side + '-captain', '<%s><bold>%s' % (c, who), 'kits', job, kind, X.spot(90, F + 2, 2, 90), lines)
    hub.npc(side + '-quartermaster', '<%s>%s' % (c, quarter[0]), 'kits', 'armorer', 'plains', X.spot(87, F + 1, 8, 90), quarter[1])
    a, b = X.spot(52, F + 1, -5, 90), X.spot(52, F + 1, 5, 90)       # the scout walks the camp's gate
    hub.npc(side + '-scout', '<%s>%s' % (c, scout[0]), 'join', 'cartographer', 'plains', a, scout[1], pace=[(a[0], a[2]), (b[0], b[2])])
    hub.npc(side + '-recruit', '<%s>%s' % (c, recruit[0]), 'talk', 'none', 'plains', X.spot(82, F + 1, 6, 0), recruit[1])


# ---------------------------------------------------------------------------------------------------- the armoury terrace

def kit_arcade(hub, A, kits, booth=False):
    """
    A run of arched bays under a lean-to roof, a kit stand in each, written along its own frame: stands at
    x = 3, 7, 11... on z = 0, the back wall behind them at z = 3, open toward -z. Each bay carries the banner of the
    faction whose colours its kit is shown in. With `booth`, one more bay at the far end is fitted out as a counter.
    """
    n = len(kits) + (1 if booth else 0)
    end = 4 * n + 2
    for x in range(0, end + 1):
        for q in range(-2, 4): A.set(x, F, q, DK if q in (-2, 2) else W2)
        for y in range(F + 1, F + 9): A.set(x, y, 3, DK if y in (F + 1, F + 8) else aged(x, y, 3))
        k = (x - 1) % 4
        if k == 0:
            for y in range(F + 1, F + 7): A.set(x, y, 4, DK if y == F + 1 else W2)       # a buttress behind
            A.set(x, F + 7, 4, stairs(W1, 'north'))
            for y in range(F + 1, F + 7): A.set(x, y, 2, DK if y == F + 1 else W2)
            for y in range(F + 1, F + 6): A.set(x, y, -2, DK if y == F + 1 else CH if y == F + 5 else W2)
        elif 1 < x < end - 1 and k != 2:
            A.set(x, F + 5, -2, stairs(W1, 'west' if k == 1 else 'east', top=True))
        A.set(x, F + 6, -2, W1)
        A.set(x, F + 7, -3, stairs(TL, 'south'))
        for q in (-2, -1): A.set(x, F + 7, q, TL)
        A.set(x, F + 8, 0, stairs(TL, 'south'))
        for q in (1, 2): A.set(x, F + 8, q, TL)
        A.set(x, F + 9, 3, 'gold_block' if k == 0 else slab(TL))
    for x in (0, end):
        for q in range(-2, 4):
            for y in range(F + 1, F + 8): A.set(x, y, q, aged(x, y, q))
    for i in range(n):
        xc = 3 + 4 * i
        A.set(xc, F + 6, -1, 'lantern[hanging=true]')
        if i == len(kits):                                           # the booth
            A.set(xc - 1, F + 1, 1, 'smithing_table')
            A.set(xc + 1, F + 1, 1, 'anvil[facing=east]')
            A.set(xc - 1, F + 1, -1, slab(DK, top=True))
            A.set(xc + 1, F + 1, -1, slab(DK, top=True))
            A.set(xc + 1, F + 2, -1, 'grindstone[face=floor,facing=north]')
            A.set(xc, F + 4, 2, 'white_wall_banner[facing=north]')
            continue
        team = TEAMS[KITS.index(kits[i]) % 4]
        P = next(p for p in FACTIONS.values() if p['side'] == team)
        A.set(xc, F + 1, 0, 'chiseled_deepslate')
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)): A.set(xc + dx, F, dz, P['accent'])
        A.set(xc, F + 4, 2, '%s_wall_banner[facing=north]' % P['banner'])
        hub.kit(A, xc, F + 2, 0, 180, kits[i])


def terrace(hub, cx=-80, cz=0):
    """
    The Armoury Terrace: every kit on show in four arcades round an open court (seven along the north, six and the
    Armourer's booth along the south, three either side of the west gate), with a rack in the four factions'
    colours by the way in. The court itself is left clear.
    """
    w = hub.w
    T = Xf(w, cx, cz)
    for (x, z), e in hub.cells['terrace'].items():
        if e < 2.3: continue
        dx, dz = x - cx, z - cz
        way = abs(dz) <= 2 or (18 <= dx <= 22 and abs(dz) <= 22)
        edge = abs(dz) == 3 or (dx in (17, 23) and 3 < abs(dz) <= 22)
        w.set(x, F, z, 'spruce_planks' if way else DK if edge else TL if (dx + dz) % 4 == 0 else aged(x, 0, z))
    kit_arcade(hub, Xf(w, cx + 17, cz - 16, rot=2), KITS[0:7])
    kit_arcade(hub, Xf(w, cx - 13, cz + 16), KITS[7:13], booth=True)
    kit_arcade(hub, Xf(w, cx - 18, cz - 17, rot=1), KITS[13:16])
    kit_arcade(hub, Xf(w, cx - 18, cz + 3, rot=1), KITS[16:19])
    for z in (-3, 3):                                                # the west gate between the two short arcades
        for y in range(F + 1, F + 8): T.set(-19, y, z, DK if y == F + 1 else CH if y == F + 7 else W2)
    for z in range(-3, 4):
        T.set(-19, F + 8, z, W1)
        T.set(-19, F + 9, z, slab(DK) if abs(z) > 1 else W1)
    T.set(-19, F + 7, -2, stairs(W1, 'north', top=True))
    T.set(-19, F + 7, 2, stairs(W1, 'south', top=True))
    T.set(-19, F + 10, 0, CH)
    T.set(-19, F + 7, 0, 'lantern[hanging=true]')
    for i, P in enumerate((CRIMSON, COBALT, AURELIAN, EMERALD)):     # the rack: one stand in each faction's colours
        x = 5 + 3 * i
        T.set(x, F + 1, -6, slab(DK))
        hub.stand(T, x, F + 1.5, -6, 0, leather(P['dye'], ('iron_sword', 'bow', 'golden_sword', 'stone_axe')[i]))
        T.set(x, F + 3, -7, '%s_wall_banner[facing=south]' % P['banner'])
        T.set(x, F + 3, -8, 'stripped_dark_oak_wood[axis=x]')
    for x in range(4, 16): T.setif(x, F + 3, -8, 'stripped_dark_oak_wood[axis=x]')
    for x in (4, 15):
        for y in (F + 1, F + 2): T.set(x, y, -8, 'dark_oak_fence')
        T.set(x, F + 4, -8, 'lantern[hanging=false]')
    new_arrivals(hub, T)
    for x, z in ((-13, -11), (15, 11), (-13, 11)): lamp_post(T, x, z, F + 1)
    hub.label(cx + 14.5, TITLE_Y, cz + 0.5, 'ARMOURY TERRACE', 'gold', 'Left-click a kit to wear it\nRight-click to look closer', 1.4)
    ax, ay, az, ayaw = Xf(w, cx - 13, cz + 16).spot(27, F + 1, 0, 180)
    hub.npc('armourer', '<gold><bold>Armourer Henry', 'kits', 'weaponsmith', 'plains', (ax, ay, az, ayaw),
            ["Every kit's on a stand: <yellow>left-click</yellow> one to wear it, <yellow>right-click</yellow> to look closer.",
             'Earn coins in matches, then come back and unlock something new.',
             'The stands wear all four colours. The Accord says the armoury belongs to nobody, so everybody argues over it.',
             'The Tank can take a beating. The Ninja can take a flag. Pick the one that matches your temper.'])


def new_arrivals(hub, T):
    """
    The newest kits, in the court's south half facing the way through: a gilded plinth for each (z = 7, four blocks
    apart), the faction's accent round it and its banner on a post behind, a low step to stand on, and a label.
    """
    xs = [-8 + 4 * i for i in range(len(NEW_KITS))]
    for x in range(xs[0] - 2, xs[-1] + 3):                           # a dark step the row stands on
        for z in range(6, 9): T.set(x, F, z, DK if z == 8 or x in (xs[0] - 2, xs[-1] + 2) else W2)
    for x, kit in zip(xs, NEW_KITS):
        team = TEAMS[KITS.index(kit) % 4]
        P = next(p for p in FACTIONS.values() if p['side'] == team)
        T.set(x, F + 1, 7, 'chiseled_deepslate')
        T.set(x, F + 1, 8, 'gilded_blackstone')
        for dx in (-1, 1): T.set(x + dx, F, 7, P['accent'])
        for y in (F + 1, F + 2, F + 3): T.set(x, y, 9, 'dark_oak_fence')
        T.set(x, F + 4, 9, 'dark_oak_planks')
        T.set(x, F + 3, 8, '%s_wall_banner[facing=north]' % P['banner'])
        T.set(x, F + 5, 9, 'lantern[hanging=false]')
        hub.kit(T, x, F + 2, 7, 180, kit)
    lx, _, lz, _ = T.spot(0, F, 4)
    hub.label(lx, TITLE_Y, lz, 'NEW ARRIVALS', 'light_purple', 'The five newest kits', 1.2)


# ---------------------------------------------------------------------------------------------------- the hall of records

def records(hub, cx=76, cz=0):
    """
    The Hall of Records: an open court with a medal inlaid in the floor, closed on the east by a pilastered wall
    under a tiled canopy carrying three framed boards (the plugin writes the Top 10 on each; left-click one for
    the full list); a plaque on a low pillar before each; an arch over the way in. Nothing else stands on the floor.
    """
    w = hub.w
    X = Xf(w, cx, cz)
    for (x, z), e in hub.cells['records'].items():
        if e < 2.3: continue
        dx, dz = x - cx, z - cz
        d = math.hypot(dx - 1, dz)
        way = (abs(dz) <= 2 and dx < -5) or (abs(dx + 14) <= 2 and abs(dz) <= 20)
        if d <= 5.6: s = 'gold_block' if d < 0.8 or (4.4 < d and (dx == 1 or dz == 0)) else W2 if d <= 3.2 else 'light_blue_terracotta' if d <= 4.4 else DK
        elif way: s = 'spruce_planks'
        else: s = TL if (dx + dz) % 4 == 0 else aged(x, 0, z)
        w.set(x, F, z, s)
    for z in range(-17, 18):
        pier = abs(z) in (17, 6, 5)
        for y in range(F + 1, F + 13):
            X.set(13, y, z, DK if y in (F + 1, F + 12) else aged(13, y, z))
            if pier: X.set(12, y, z, DK if y == F + 1 else CH if y == F + 12 else W2)
        if abs(z) in (17, 11, 6, 0):                                 # buttresses down the back
            for y in range(F + 1, F + 10): X.set(14, y, z, DK if y == F + 1 else W2)
            X.set(14, F + 10, z, stairs(W1, 'west'))
        X.set(11, F + 13, z, stairs(TL, 'east'))                     # the canopy, overhanging the boards
        X.set(12, F + 13, z, TL)
        X.set(13, F + 13, z, TL)
        X.set(14, F + 13, z, stairs(TL, 'west'))
        X.set(12, F + 14, z, slab(TL))
        X.set(13, F + 14, z, slab(TL))
    for title, stat, zc in (('TOP KILLS', 'kills', -11), ('TOP OBJECTIVES', 'captures', 0), ('TOP WINS', 'wins', 11)):
        for dz in range(-3, 4):
            for y in range(F + 3, F + 10):
                rim_ = abs(dz) == 3 or y in (F + 3, F + 9)
                X.set(12, y, zc + dz, ('gold_block' if abs(dz) == 3 and y in (F + 3, F + 9) else 'stripped_dark_oak_wood[axis=y]') if rim_ else 'black_concrete')
            X.set(12, F + 2, zc + dz, stairs(DK, 'east', top=True))
            X.set(12, F + 10, zc + dz, stairs(DK, 'east'))
        for dz in (-2, 2):                                           # lamps hung from the canopy's eave
            X.set(11, F + 12, zc + dz, 'iron_chain[axis=y]')
            X.set(11, F + 11, zc + dz, 'lantern[hanging=true]')
        X.set(12, F + 11, zc, CH)
        X.set(8, F + 1, zc, DK)                                      # the plaque on its pillar
        X.set(8, F + 2, zc, CH)
        X.set(8, F + 3, zc, slab(DK))
        hub.sign(X, 7, F + 2, zc, 'dark_oak_wall_sign[facing=west]', [title, '', 'Left-click the', 'board for more'], 'yellow', True)
        hub.board(stat, cx + 12 - 0.03, F + 4.15, cz + zc + 0.5, 90)
    for z in (-4, 4):                                                # the arch over the way in
        for y in range(F + 1, F + 8): X.set(-12, y, z, DK if y == F + 1 else CH if y == F + 7 else W2)
    for z in range(-4, 5):
        X.set(-12, F + 8, z, W1)
        X.set(-12, F + 9, z, slab(DK) if abs(z) > 1 else W1)
    X.set(-12, F + 7, -3, stairs(W1, 'north', top=True))
    X.set(-12, F + 7, 3, stairs(W1, 'south', top=True))
    X.set(-12, F + 10, 0, CH)
    X.set(-12, F + 7, 0, 'lantern[hanging=true]')
    for x, z in ((-6, -9), (-6, 9), (5, -15), (5, 15)): lamp_post(X, x, z, F + 1)
    hub.label(cx - 7.5, TITLE_Y, cz + 0.5, 'HALL OF RECORDS', 'aqua', 'Your stats and the leaderboards: /ctfstats', 1.3)
    hub.npc('chronicler', '<aqua><bold>Chronicler Walter', 'talk', 'librarian', 'plains', X.spot(6, F + 1, 4, 90),
            ['I write down every kill, capture and win. Your page: <yellow>/ctfstats</yellow>.',
             'The three boards behind me carry the top ten. <yellow>Left-click</yellow> one and I will open the full list.',
             'Four teams, one ledger. The Accord made me promise not to say whose column is longest.',
             'Someone asked me to erase their deaths. I wrote that down too.'])
    hub.npc('treasurer', '<gold>Treasurer Arthur', 'talk', 'toolsmith', 'plains', X.spot(-2, F + 1, -6, 90),
            ['Two coins a kill, five for an objective, ten for the win. <yellow>/coins</yellow> counts them for you.',
             'The <yellow>Bastion</yellow> mints them, I count them, and the Armourer takes them off you. A fair system.',
             'Save up. The better kits are not cheap, and I do not haggle.'])
    hub.npc('loremaster', '<aqua>Loremaster Agnes', 'talk', 'cleric', 'snow', X.spot(6, F + 1, -6, 90),
            ['Before the Sundering this was the Citadel\'s east gallery. The boards listed its champions.',
             'The first name ever cut here was a <red>Crimson</red> smith. The second was the <blue>Cobalt</blue> pilot who out-flew her.',
             'The <yellow>Bastion</yellow> kept the old rolls safe in the vault. The <green>Enclave</green> say half of them were written on bark first.',
             'When the crystal split, each team took the names it liked and left the rest. I keep the rest.',
             'Win enough and you go on a board. Win too much and all four teams will know your name. Choose wisely.'])


# ---------------------------------------------------------------------------------------------------- the overlook

def overlook(hub, cx=86, cz=-27):
    """
    The Overlook Deck, on a promontory of the records island behind the boards' north end: a round timber deck with
    a compass at its heart, a bench row facing north and another facing east over the parapet, lamp posts, a
    lantern arch at the way in and a spyglass on a post.
    """
    w = hub.w
    for (x, z), e in hub.cells['records'].items():
        if e < 2.3 or z > -18: continue
        d = math.hypot(x - cx, z - cz)
        if d <= 6.4: s = 'waxed_cut_copper' if d < 0.8 or (d < 3.2 and (x == cx or z == cz)) else 'spruce_planks' if int(d) % 2 else 'dark_oak_planks'
        elif d <= 7.4: s = 'stripped_spruce_log[axis=x]'
        else: s = 'spruce_planks' if abs(x - 82) <= 1 else aged(x, 0, z)
        w.set(x, F, z, s)
    for x in range(cx - 3, cx + 4): w.set(x, F + 1, cz - 5, slab(DK) if abs(x - cx) == 3 else stairs('spruce_planks', 'south'))     # facing north
    for z in range(cz - 3, cz + 4): w.set(cx + 6, F + 1, z, slab(DK) if abs(z - cz) == 3 else stairs('spruce_planks', 'west'))      # facing east
    for x, z in ((cx - 5, cz - 4), (cx + 5, cz - 5), (cx + 6, cz + 5), (cx - 6, cz + 2)): lamp_post(w, x, z, F + 1)
    for x in (80, 84):                                               # the lantern arch at the way in
        for y in range(F + 1, F + 5): w.set(x, y, -19, 'stripped_spruce_log[axis=y]')
    for x in range(80, 85): w.set(x, F + 5, -19, 'stripped_spruce_log[axis=x]')
    w.set(82, F + 4, -19, 'lantern[hanging=true]')
    w.set(cx + 4, F + 1, cz - 3, 'spruce_fence')                     # the spyglass
    w.set(cx + 4, F + 2, cz - 3, 'end_rod[facing=east]')
    hub.label(cx + 0.5, TITLE_Y, cz + 3.5, 'OVERLOOK DECK', 'aqua', 'Sit a while and watch the shards drift', 1.3)
    hub.npc('watchman', '<aqua>Watchman Oliver', 'talk', 'shepherd', 'plains', (cx - 2.5, F + 1, cz - 0.5, -90),
            ['Quietest spot on the Citadel. You can see the little shards from here: gardens, old ruins, the crystal ones.',
             'They are all that is left of the outer wards. After the Sundering they drifted off, then drifted back. Nobody knows why.',
             'The teams argue over who owns them. <green>The Enclave</green> say the gardens, <red>Crimson</red> the ruins, <yellow>the Bastion</yellow> the crystals. <blue>Cobalt</blue> just want them charted.',
             'Sit down. The benches are free, which is more than you can say for most things the Bastion built.'],
            pace=[(cx - 2.5, cz - 0.5), (cx + 3.5, cz - 0.5)])


# ---------------------------------------------------------------------------------------------------- the belfry and the parkour

def belfry(hub, bx=-130, bz=-17):
    """
    The Bell-Belfry: a square tower on a two-step battered plinth, banded in deepslate every nine blocks. A stair
    winds round its newel (two blocks wide, twelve up per turn, lit from the newel) to the lookout: an open stage
    flush with the walls, four piers and four arches with a parapet between them, the bell hanging over it under a
    steep tiled spire no wider than the tower.
    """
    w = hub.w
    X = Xf(w, bx, bz)
    deck = F + 19
    for dx in range(-6, 7):
        for dz in range(-6, 7):
            m = max(abs(dx), abs(dz))
            door = dz > 0 and abs(dx) <= 1
            if m == 6:
                if not door: X.set(dx, F + 1, dz, slab(DK))
            elif m == 5:
                if door: continue
                toward = 'east' if dx == -5 else 'west' if dx == 5 else 'south' if dz < 0 else 'north'
                X.set(dx, F + 1, dz, DK)
                X.set(dx, F + 2, dz, slab(DK) if abs(dx) == abs(dz) else stairs(DK, toward))
            elif m == 4:
                pier = min(abs(dx), abs(dz)) >= 3
                face = dx == 0 or dz == 0
                for y in range(F + 1, deck + 1):
                    rel = y - F
                    X.set(dx, y, dz, DK if rel % 9 == 1 else W2 if pier else 'iron_bars' if face and rel % 9 in (5, 6) else aged(dx, y, dz))
                if pier:
                    for y in range(deck + 1, deck + 7): X.set(dx, y, dz, CH if y == deck + 6 else W2)
                else:
                    X.set(dx, deck + 1, dz, wall(W1))                # the lookout's parapet, between the piers
                    if abs(dz) == 4 and abs(dx) == 2: X.set(dx, deck + 6, dz, stairs(W1, 'west' if dx < 0 else 'east', top=True))
                    elif abs(dx) == 4 and abs(dz) == 2: X.set(dx, deck + 6, dz, stairs(W1, 'north' if dz < 0 else 'south', top=True))
                X.set(dx, deck + 7, dz, W1)
            elif m <= 1:
                for y in range(F + 1, deck + 1): X.set(dx, y, dz, 'ochre_froglight' if (y - F) % 6 == 3 and (dx == 0) != (dz == 0) else W2 if dx or dz else DK)
            else:
                X.set(dx, deck, dz, TL if (dx + dz) % 2 else W2)
    for y in range(F + 1, F + 5):                                    # the door, on the south face
        for dx in range(-1, 2):
            if abs(dx) <= (1 if y < F + 4 else 0): X.set(dx, y, 4, 'air')
    X.set(0, F + 5, 4, CH)
    for dx in (-2, 2): lamp_post(X, dx, 7, F + 1)
    y = F + 1
    walk = []                                                        # the stair: (cells of one tread, facing), a landing at each corner
    for turn in range(3):
        for facing in ('north', 'west', 'south', 'east'):
            for i in range(3):
                if facing == 'north': cells = [(2, 1 - i), (3, 1 - i)]
                elif facing == 'west': cells = [(1 - i, -2), (1 - i, -3)]
                elif facing == 'south': cells = [(-2, i - 1), (-3, i - 1)]
                else: cells = [(i - 1, 2), (i - 1, 3)]
                walk.append((cells, facing))
            corner = {'north': (1, -1), 'west': (-1, -1), 'south': (-1, 1), 'east': (1, 1)}[facing]
            walk.append(([(corner[0] * a, corner[1] * b) for a in (2, 3) for b in (2, 3)], None))
    for cells, facing in walk:
        if y > deck: break
        for dx, dz in cells:
            X.set(dx, y if facing else y - 1, dz, stairs(W1, facing) if facing else W1)
            for k in (1, 2, 3):                                      # headroom through the deck where the stair comes up
                if (y if facing else y - 1) + k == deck: X.set(dx, deck, dz, 'air')
        if facing: y += 1
    for dx in range(-1, 2): X.set(dx, deck + 6, 0, 'stripped_dark_oak_wood[axis=x]')       # the bell on its beam
    for dz in (-4, -3, -2, -1, 1, 2, 3, 4): X.set(0, deck + 6, dz, 'stripped_dark_oak_wood[axis=z]')
    X.set(0, deck + 5, 0, 'bell[attachment=ceiling,facing=north]')
    for k in range(9):                                               # the spire, as wide as the tower and no wider
        r = 4.7 * (1 - k / 8.0)
        for dx in range(-4, 5):
            for dz in range(-4, 5):
                if octa(dx, dz) <= r + 0.3: X.set(dx, deck + 8 + k, dz, TL)
    X.set(0, deck + 17, 0, 'gold_block')
    X.set(0, deck + 18, 0, 'end_rod[facing=up]')
    hub.label(bx + 0.5, TITLE_Y, bz + 8.5, 'BELL-BELFRY', 'gold', 'Climb to the lookout\nRing the bell', 1.3)
    hub.npc('bellringer', '<gold>Bell-Ringer Sam', 'talk', 'mason', 'plains', X.spot(2, deck + 1, 2, 135),
            ['Best view on the rock. <red>Crimson</red> smoke to the south, <green>Enclave</green> green to the north, and the fools on the parkour below.',
             'I ring it when a match starts. Before the Accord I rang it when a team was raided. I rang it a lot.',
             'All four teams asked me to ring it louder for their wins. I ring it the same for everyone. That is the job.',
             'Give the bell a pull. Everyone does. Nobody has broken it yet.'])


PADS = [(-145, 100, 0, 2.1), (-150, 100, 3, 2.0), (-154, 101, 7, 2.1), (-158, 101, 11, 2.0), (-160, 102, 16, 2.1), (-158, 103, 21, 2.0),
        (-154, 103, 25, 2.1), (-149, 104, 27, 2.0), (-144, 105, 26, 2.1), (-139, 105, 23, 2.0), (-135, 106, 20, 2.1)]
FINISH = (-128, 106, 17)


def parkour(hub, shard, cx=-128, cz=0):
    """
    The parkour course: a start arch at the foot of the belfry, then eleven floating pads climbing in an arc to a
    finish kiosk with a bell, high over the start. Each jump is two or three blocks, never more than one block up.
    """
    w = hub.w
    for (x, z), e in hub.cells['belfry'].items():
        if e < 2.3: continue
        dx, dz = x - cx, z - cz
        way = (abs(dz) <= 2 and dx > -9) or (abs(x + 130) <= 1 and -12 <= z <= 0)
        w.set(x, F, z, 'spruce_planks' if way else DK if abs(dz) == 3 and dx > -9 else aged(x, 0, z))
    for z in (cz - 3, cz + 3):                                       # the start arch
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if dx or dz: w.set(cx - 6 + dx, F + 1, z + dz, slab(DK))
        for y in range(F + 1, F + 7): w.set(cx - 6, y, z, DK if y == F + 1 else CH if y == F + 6 else W2)
    for z in range(cz - 3, cz + 4):
        w.set(cx - 6, F + 7, z, W1)
        w.set(cx - 6, F + 8, z, slab(DK) if abs(z - cz) > 1 else W1)
    w.set(cx - 6, F + 6, cz - 2, stairs(W1, 'north', top=True))
    w.set(cx - 6, F + 6, cz + 2, stairs(W1, 'south', top=True))
    w.set(cx - 6, F + 9, cz, CH)
    w.set(cx - 6, F + 6, cz, 'lantern[hanging=true]')
    w.set(cx - 9, F, cz, 'gold_block')
    for i, (x, y, z, r) in enumerate(PADS):
        for tx, tz in shard(w, x, y, z, r, 6 + i % 3 * 2, 500 + i, top=W2):
            if (tx, tz) == (x, z): w.set(tx, y, tz, DK)
    fx, fy, fz = FINISH
    for tx, tz in shard(w, fx, fy, fz, 4.2, 12, 590, top=W1):
        if math.hypot(tx - fx, tz - fz) <= 1.5: w.set(tx, fy, tz, TL)
    w.set(fx, fy, fz, 'gold_block')
    for dx, dz in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        for y in range(fy + 1, fy + 5): w.set(fx + dx, y, fz + dz, wall(W1) if y < fy + 4 else CH)
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            m = max(abs(dx), abs(dz))
            if m == 3: w.setif(fx + dx, fy + 5, fz + dz, slab(TL))
            elif m >= 1: w.set(fx + dx, fy + 5 + (2 - m) if m < 3 else fy + 5, fz + dz, TL if m == 2 else slab(TL))
    w.set(fx, fy + 6, fz, TL)
    w.set(fx, fy + 5, fz, 'bell[attachment=ceiling,facing=north]')
    hub.label(cx - 1.5, TITLE_Y, cz + 0.5, 'PARKOUR', 'green', 'Eleven jumps to the bell', 1.3)       # in the open, east of the arch
    hub.label(fx + 0.5, fy + 3.3, fz + 0.5, 'FINISH', 'gold', None, 1.0)
    hub.npc('trailmaster', '<green><bold>Trailmaster Jack', 'talk', 'shepherd', 'plains', (cx - 2.5, F + 1, cz + 5.5, -120),
            ['Eleven jumps to the bell. Nobody has rung it twice in a row.',
             'Miss a jump and the void hands you straight back to the pavilion.',
             'Scouts from all four teams train here. The Accord never said anything about racing.',
             'Sprint, jump late, and do not look down.'], pace=[(cx - 2.5, cz + 5.5), (cx + 3.5, cz + 5.5)])


# ---------------------------------------------------------------------------------------------------- the sky-dock

def skydock(hub, cz=110):
    """
    The Sky-Dock: a timber jetty with a copper runner and lantern arms, running east off the dock island, cargo
    stacked beside it, and a sky-skiff moored alongside on chains (plank hull,
    cabin, rudder, and an envelope striped in the four colours, rigged with chains); a gantry crane on the west
    side with a crate swinging from its boom; and a signal mast flying four pennants at the island's tip.
    """
    w = hub.w
    for (x, z), e in hub.cells['dock'].items():
        if e < 2.3: continue
        way = (abs(x) <= 2 and z <= cz + 2) or (abs(z - cz - 1) <= 2 and x >= 0)
        w.set(x, F, z, 'spruce_planks' if way else DK if (abs(x) == 3 and z < cz - 2) else aged(x, 0, z))
    for x in range(14, 38):                                          # the jetty
        for z in range(cz - 1, cz + 4):
            edge = z in (cz - 1, cz + 3)
            w.set(x, F, z, 'stripped_spruce_log[axis=x]' if edge else 'spruce_planks')
            if x % 2 == 0: w.set(x, F - 1, z, 'spruce_log[axis=z]')
            if not edge or x < 17: continue
            if z == cz + 3 and 27 <= x <= 29: continue               # the gangway
            if x % 5 == 2:
                for y in (F + 1, F + 2): w.set(x, y, z, 'stripped_spruce_log[axis=y]')
                w.set(x, F + 3, z, 'lantern[hanging=false]')
            else: w.set(x, F + 1, z, 'spruce_fence')
        if x % 8 == 4:
            for y in range(F - 5, F - 1): w.set(x, y, cz + 1, 'stripped_spruce_log[axis=y]')
    for z in range(cz - 1, cz + 4): w.set(37, F + 1, z, 'spruce_fence')
    for x in range(15, 37): w.set(x, F, cz + 1, 'waxed_cut_copper' if x % 4 else 'waxed_oxidized_cut_copper')      # a copper runner down the deck
    for x in (22, 32):                                               # lantern arms over the deck
        for y in (F + 3, F + 4): w.set(x, y, cz - 1, 'stripped_spruce_log[axis=y]')
        w.set(x, F + 4, cz, 'stripped_spruce_log[axis=z]')
        w.set(x, F + 3, cz, 'lantern[hanging=true]')
        w.set(x, F + 5, cz - 1, 'waxed_cut_copper_slab[type=bottom]')
    for x in (24, 31):                                               # mooring chains from bollards to the skiff's rail
        w.set(x, F + 1, cz + 3, 'polished_deepslate_wall')
        w.set(x, F + 1, cz + 4, 'iron_chain[axis=z]')
    for i, (x, y, z) in enumerate(((9, 1, cz + 5), (10, 1, cz + 5), (9, 2, cz + 5), (10, 1, cz + 6), (-4, 1, cz - 6), (-3, 1, cz - 6), (-4, 2, cz - 6), (-3, 1, cz - 5))):
        w.set(x, F + y, z, ('stripped_spruce_wood', 'stripped_oak_wood', 'stripped_dark_oak_wood')[i % 3])      # cargo crates
    w.set(10, F + 2, cz + 5, 'lantern[hanging=false]')
    S = Xf(w, 28, cz + 8)                                            # the skiff, bow to the east
    for dx in range(-13, 14):
        hw = 3.4 * (1 - (abs(dx) / 13.5) ** 2.4) ** 0.55
        for dz in range(-4, 5):
            a = abs(dz)
            if a <= hw:
                S.set(dx, F, dz, 'dark_oak_planks' if dz == 0 or dx % 6 == 0 else 'spruce_planks')
                if a > hw - 1 and not (dz < 0 and abs(dx) <= 1): S.set(dx, F + 1, dz, 'spruce_fence')
            for k, cut in ((1, 0.6), (2, 1.6), (3, 2.7)):
                if a <= hw - cut: S.set(dx, F - k, dz, 'stripped_dark_oak_wood[axis=x]' if k == 3 else 'dark_oak_planks')
    for dz in (-5, -4): S.set(0, F, dz, 'spruce_planks')             # the gangplank
    for dx in (-1, 1): S.set(dx, F, -4, 'spruce_planks')
    for dx in (14, 15, 16): S.set(dx, F + 1, 0, 'stripped_spruce_log[axis=x]')              # the bowsprit and its lamp
    S.set(16, F, 0, 'lantern[hanging=true]')
    for y in range(F - 3, F + 3):                                    # the rudder
        S.set(-14, y, 0, 'spruce_planks')
        if y >= F - 1: S.set(-15, y, 0, 'white_wool')
    for dx in range(-10, -6):                                        # the cabin
        for dz in (-1, 0, 1):
            wall_ = dx in (-10, -7) or dz
            for y in (F + 1, F + 2):
                if wall_ and not (dx == -7 and dz == 0): S.set(dx, y, dz, 'spruce_planks' if y == F + 1 or dz == 0 or dx in (-10, -7) else 'glass_pane')
            S.set(dx, F + 3, dz, 'dark_oak_slab[type=top]')
    S.set(-8, F + 4, 0, 'lantern[hanging=false]')
    bands = {-9: 'red_wool', -8: 'red_wool', -3: 'yellow_wool', -2: 'yellow_wool', 2: 'lime_wool', 3: 'lime_wool', 8: 'blue_wool', 9: 'blue_wool'}
    for dx in range(-13, 14):                                        # the envelope
        for dy in range(-5, 6):
            for dz in range(-5, 6):
                v = (dx / 13.2) ** 2 + (dy / 4.7) ** 2 + (dz / 4.7) ** 2
                if 0.6 < v <= 1.0: S.set(dx, F + 15 + dy, dz, bands.get(dx, 'light_gray_wool' if dy == 0 else 'white_wool'))
    for dx in (-8, -3, 3, 8):                                        # the rigging
        for s in (-1, 1):
            dz = next((s * a for a in (3, 2) if not S.air(dx, F + 1, s * a)), None)
            if dz is None: continue
            for y in range(F + 2, F + 15):
                if not S.air(dx, y, dz): break
                S.set(dx, y, dz, 'iron_chain[axis=y]')
    for x, z in ((-10, cz - 2), (-7, cz - 2), (-10, cz + 1), (-7, cz + 1)):                 # the gantry
        for y in range(F + 1, F + 12): w.set(x, y, z, 'stripped_spruce_log[axis=y]')
    for x in range(-11, -5):
        for z in range(cz - 3, cz + 3): w.set(x, F + 12, z, 'spruce_planks' if x in (-11, -6) or z in (cz - 3, cz + 2) else 'spruce_slab[type=top]')
    for x in range(-22, -3): w.set(x, F + 13, cz, 'dark_oak_log[axis=x]')                    # the boom and its counterweight
    w.set(-4, F + 12, cz, 'polished_deepslate')
    for y in range(F + 6, F + 13): w.set(-21, y, cz, 'iron_chain[axis=y]')
    for x in (-22, -21):
        for z in (cz, cz + 1):
            for y in (F + 4, F + 5): w.set(x, y, z, 'stripped_spruce_wood')
    w.set(-8, F + 14, cz, 'lantern[hanging=false]')
    for y in range(F + 1, F + 14): w.set(0, y, cz + 8, 'stripped_spruce_log[axis=y]')       # the signal mast
    for x in range(-2, 3): w.set(x, F + 11, cz + 8, 'stripped_spruce_log[axis=x]')
    for x, flag in ((-2, 'red_wool'), (-1, 'yellow_wool'), (1, 'lime_wool'), (2, 'blue_wool')):
        for y in (F + 10, F + 9): w.set(x, y, cz + 8, flag)
    w.set(0, F + 14, cz + 8, 'lantern[hanging=false]')
    for x, z in ((6, cz - 4), (7, cz - 4), (6, cz - 5)): w.set(x, F + 1, z, 'hay_block[axis=y]')
    w.set(6, F + 2, cz - 4, 'stripped_spruce_wood')
    for x, z in ((-5, cz - 8), (5, cz + 6)): lamp_post(w, x, z, F + 1)
    hub.label(0.5, TITLE_Y, cz - 5.5, 'SKY-DOCK', 'aqua', 'Skiffs from every shard moor here', 1.3)
    hub.npc('dockmaster', '<aqua><bold>Dockmaster Harold', 'talk', 'fisherman', 'plains', (4.5, F + 1, cz - 1.5, 30),
            ['Every recruit comes in on a skiff. Most of you are sick over the side before the pavilion.',
             'The envelope flies all four colours. A skiff with one colour gets shot at by the other three.',
             'After the Sundering the shards drifted. These chains and my ropes are why they stopped.',
             'Mind the gantry. That crate has been swinging there since before the Accord.'],
            pace=[(4.5, cz - 1.5), (4.5, cz - 7.5)])
    px, py, pz, pyaw = S.spot(5, F + 1, 0, 180)
    hub.npc('skiff-pilot', '<aqua>Pilot Nell', 'talk', 'cartographer', 'taiga', (px, py, pz, pyaw),
            ['She is called the <white>Fair Witness</white>. Neutral hull, neutral crew, and I charge all four teams the same.',
             'I have flown to every map you can vote for. The big ones look smaller from up here. So do the players.',
             'Step aboard if you like. We do not cast off until the horn sounds.'])


def wanderers(hub):
    """
    Ordinary people about the lobby, each walking a loop of their own without stopping unless you step right up to
    them: two round the sanctum (in opposite directions), one over each of the four bridges out of it and back
    down the other lane, one round the terrace court. Two lines each.
    """
    ring = lambda r, turn: [(round(r * math.cos(math.radians(22.5 + 45 * k * turn)) + 0.5, 1), round(r * math.sin(math.radians(22.5 + 45 * k * turn)) + 0.5, 1)) for k in range(8)]
    lane = lambda near, far: [(1.5, near), (1.5, far), (-1.5, far), (-1.5, near)]
    south = lane(25.5, 62.5)
    people = (
        ('Alfred', 'farmer', 'plains', ring(21, 1),
         ['Fine day for a match. Mind you, they all are up here.', 'I walk the ring every morning. Keeps the knees going.']),
        ('Molly', 'none', 'taiga', ring(15, -1),
         ['Have you voted yet? The paper in your hand, dear.', 'I only come for the view. And the shouting.']),
        ('Ben', 'fisherman', 'plains', south,
         ['Up from the dock again. One day I will take the skiff back down.', 'If you are lost: kits west, records east, the gate north.']),
        ('Grace', 'shepherd', 'savanna', [(-z, x) for x, z in lane(25.5, 66.5)],
         ['The Armourer says the Tank is selling well. I prefer something lighter.', 'Back and forth to the terrace all day. I still have not picked a kit.']),
        ('Harry', 'librarian', 'plains', [(z, -x) for x, z in lane(25.5, 62.5)],
         ['I check the boards every hour. My name is never on them.', 'Left-click a board and it shows you the whole list. Clever, that.']),
        ('Rose', 'cleric', 'snow', [(-x, -z) for x, z in lane(25.5, 66.5)],
         ['They say the gate breaks open just before a match. I never tire of watching it.', 'Stand back when it goes. Glass everywhere.']),
        ('Ted', 'fletcher', 'plains', [(-88.5, -10.5), (-70.5, -10.5), (-70.5, 10.5), (-88.5, 10.5)],
         ['Nineteen kits and I can afford one of them.', 'Go on, try a few on. Left-click a stand.']),
    )
    for name, job, kind, loop, lines in people:
        hub.npc('wanderer-' + name.lower(), '<white>' + name, 'wander', job, kind, (loop[0][0], F + 1, loop[0][1], 0), lines, pace=loop + [loop[0]])


def build(hub, shard):
    """Everything on the islands, in the order that lets later pieces sit on earlier ones."""
    sanctum(hub)
    monument(hub)
    gate(hub)
    pavilion(hub)
    for name in ('crimson', 'cobalt', 'aurelian', 'emerald'): camp(hub, name)
    terrace(hub)
    records(hub)
    overlook(hub)
    belfry(hub)
    parkour(hub, shard)
    skydock(hub)
    wanderers(hub)
