"""
architecture_and_details.py: everything built on the islands of the Shattered Sky-Citadel.

One architectural language throughout: stone-brick walls recessed between polished pilasters, on stepped deepslate
plinths, under corbelled cornices and steep roofs. The two teams enter through colour only: Crimson (red nether
brick, crimson wood, dark oak, gold, shroomlight and fire) in the west wing, Cobalt (dark prismarine, spruce,
oxidised copper, sea lanterns and soul fire) in the east.

The hub's characters, kit stands and floating labels are collected on the Hub object as they are built, so they
always stand where the architecture puts them.
"""
import math

from terrain_and_bridges import Xf, fbm, rnd3, slab, stairs, wall
from world_writer import DIRS, OPPOSITE

F = 100                                    # the floor block everywhere; you stand at F + 1
W1, W2, DK, TL, CH = 'stone_bricks', 'polished_andesite', 'polished_deepslate', 'deepslate_tiles', 'chiseled_stone_bricks'

CRIMSON = dict(side='red', title='CRIMSON BASTION', colour='red', roof='red_nether_bricks', accent='red_terracotta',
               accent2='nether_bricks', gem='gold_block', beam='dark_oak_log', plank='crimson_planks', fence='crimson_fence',
               light='shroomlight', lamp='lantern', fire='campfire[lit=true]', cloth='red_wool', cloth2='red_concrete',
               stripe='yellow_wool', banner='red', glass='red_stained_glass_pane', carpet='red_carpet')
COBALT = dict(side='blue', title='COBALT CITADEL', colour='blue', roof='dark_prismarine', accent='blue_terracotta',
              accent2='prismarine_bricks', gem='waxed_oxidized_cut_copper', beam='spruce_log', plank='spruce_planks',
              fence='spruce_fence', light='sea_lantern', lamp='soul_lantern', fire='soul_campfire[lit=true]', cloth='blue_wool',
              cloth2='light_blue_wool', stripe='white_wool', banner='blue', glass='light_blue_stained_glass_pane',
              carpet='blue_carpet')

KITS = ['barbarian', 'big_spender', 'builder', 'choruser', 'demoman', 'engineer', 'fire_archer', 'infiltrator', 'medic',
        'necromancer', 'ninja', 'phoenix', 'redstone_mechanic', 'seeking_archer', 'sheep_farmer', 'shielder', 'spy', 'tank',
        'thor']


class Hub:
    """The world being built, its islands' ground cells, and the characters, kit stands and labels collected so far."""

    def __init__(self, world, cells):
        self.w, self.cells = world, cells
        self.npcs, self.kits = [], []

    def label(self, x, y, z, title, colour='gold', sub=None, scale=1.4):
        self.w.text(x, y, z, title, colour, True, scale)
        if sub: self.w.text(x, y - 0.3 - 0.25 * (sub.count('\n') + 1), z, sub, 'white', False, 1.0)

    def npc(self, ident, name, role, profession, kind, spot, lines):
        self.npcs.append(dict(id=ident, name=name, role=role, profession=profession, type=kind, x=spot[0], y=spot[1], z=spot[2],
                              yaw=spot[3], lines=lines))


def octa(dx, dz):
    """Distance in an octagon's own measure (a square with its corners cut)."""
    return max(abs(dx), abs(dz), (abs(dx) + abs(dz)) / 1.4142)


def aged(x, y, z):
    """Stone brick, mossy in drifts and cracked in patches."""
    n = fbm(x + y * 0.7, z - y * 0.7, 6, 41)
    return 'mossy_stone_bricks' if n > 0.46 else 'cracked_stone_bricks' if n < -0.5 else W1


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
            for k in range(7):
                if octa(dx, dz) <= 3.2 * (1 - k / 7.0) + 0.2: X.set(cx + dx, y1 + 1 + k, cz + dz, P['roof'])
    X.set(cx, y1 + 8, cz, P['gem'])
    X.set(cx, y1 + 9, cz, 'end_rod[facing=up]')


# ---------------------------------------------------------------------------------------------------- the sanctum

def sanctum(hub):
    """The War-Sanctum plaza: rings and rays of paving round the monument, a ring of lights, four planters, lamp posts."""
    w = hub.w
    lights = {(int(round(15.8 * math.cos(k * math.pi / 8))), int(round(15.8 * math.sin(k * math.pi / 8)))) for k in range(16)}
    for (x, z), e in hub.cells['core'].items():
        d = math.hypot(x, z)
        if d > 22.5 or z < -22: continue
        a = math.degrees(math.atan2(z, x)) % 360
        cross = min(abs(x), abs(z))
        if d < 6.6: s = W2
        elif d < 8.2: s = DK
        elif d < 15: s = DK if cross <= 1 else W1 if int(a // 22.5) % 2 == 0 else 'deepslate_bricks'
        elif d < 16.7: s = 'ochre_froglight' if (x, z) in lights else TL
        elif cross <= 2: s = W2
        elif cross == 3: s = DK
        elif abs(abs(x) - abs(z)) <= 1: s = TL
        else: s = aged(x, 0, z)
        w.set(x, F, z, s)
    for k in range(8):
        a = math.radians(22.5 + 45 * k)
        lamp_post(w, int(round(20.4 * math.cos(a))), int(round(20.4 * math.sin(a))), F + 1)
    for sx in (-1, 1):
        for sz in (-1, 1):
            cx, cz = sx * 14, sz * 14
            for dx in range(-4, 5):
                for dz in range(-4, 5):
                    d = math.hypot(dx, dz)
                    if d <= 3.4:
                        w.set(cx + dx, F + 1, cz + dz, DK if d > 2.3 else 'moss_block' if (dx + dz) % 2 else 'rooted_dirt')
                    elif d <= 4.3 and (cx + dx, cz + dz) in hub.cells['core']:
                        w.set(cx + dx, F, cz + dz, TL)
    for k in range(4):
        X = Xf(w, 0, 0, rot=k)
        for x in (10, 11, 12): X.set(x, F + 1, 19, stairs('dark_oak_planks', 'south'))
        for x in (9, 13): X.set(x, F + 1, 19, slab(DK))
    hub.label(0.5, F + 6.2, 9.5, 'WAR-SANCTUM', 'gold', 'Vote for the next map with the paper\nPick a kit with the book', 1.3)


def monument(hub):
    """
    The Flag Monument: a three-tier eight-sided plinth; four buttress piers on the diagonals whose flying arches
    meet in a crown; the crystal core hanging from the crown on a chain over a lit altar; and above the crown a
    mast with two crossed yards, the crimson banner flying from one and the cobalt from the other.
    """
    w = hub.w
    for x in range(-7, 8):
        for z in range(-7, 8):
            o = octa(x, z)
            if o <= 5: w.set(x, F + 1, z, DK)
            elif o <= 6: w.set(x, F + 1, z, slab(DK))
            if o <= 3.6: w.set(x, F + 2, z, TL)
            elif o <= 4.6: w.set(x, F + 2, z, slab(TL))
            if o <= 2.4: w.set(x, F + 3, z, 'gold_block' if o > 1.5 and (x == 0 or z == 0) else W2)
    w.set(0, F + 3, 0, 'ochre_froglight')
    for x in range(-4, 5):                                           # the crystal core
        for z in range(-4, 5):
            for y in range(F + 6, F + 18):
                v = abs(x) + abs(z) + abs(y - (F + 11.5)) * 0.62
                if v <= 1.2: w.set(x, y, z, 'pearlescent_froglight')
                elif v <= 2.4: w.set(x, y, z, 'amethyst_block')
                elif v <= 3.5: w.set(x, y, z, 'purple_stained_glass')
    for y in range(F + 18, F + 22): w.set(0, y, 0, 'iron_chain[axis=y]')
    for k in range(4):                                               # the buttresses
        X = Xf(w, 0, 0, rot=k)
        for dx in range(5, 9):
            for dz in range(5, 9):
                X.set(dx, F + 1, dz, DK if 5 < dx < 8 and 5 < dz < 8 else slab(DK))
        for dx in (6, 7):
            for dz in (6, 7):
                for y in range(F + 2, F + 15): X.set(dx, y, dz, DK if y in (F + 7, F + 14) else aged(dx, y, dz))
        X.set(7, F + 15, 7, CH)
        X.set(7, F + 16, 7, wall(W1))
        X.set(7, F + 17, 7, 'lantern[hanging=false]')
        for i in range(40):                                          # the flying arch, two blocks deep
            t = i / 39.0
            r = (2.2 + 6.2 * math.cos(t * math.pi / 2)) / 1.4142
            y = F + 13 + int(round(9.0 * math.sin(t * math.pi / 2)))
            c = int(round(r))
            for cell in ((c, c), (c + 1, c), (c, c + 1)):
                X.set(cell[0], y, cell[1], W1)
                X.set(cell[0], y - 1, cell[1], DK)
    for x in range(-3, 4):                                           # the crown
        for z in range(-3, 4):
            o = octa(x, z)
            if o <= 3.2: w.set(x, F + 22, z, DK)
            if o <= 2.3: w.set(x, F + 23, z, CH)
            if o <= 1.2:
                for y in range(F + 24, F + 27): w.set(x, y, z, W2)
    for y in range(F + 27, F + 42): w.set(0, y, 0, 'dark_oak_log[axis=y]')
    w.set(0, F + 42, 0, 'gold_block')
    w.set(0, F + 43, 0, 'end_rod[facing=up]')
    for sx, P in ((-1, CRIMSON), (1, COBALT)):                       # the crossed yards and their banners
        top = []
        for i in range(-7, 8):
            x, y = sx * i, F + 33 + int(round(i * 6 / 7.0))
            w.setif(x, y, 0, 'stripped_dark_oak_wood[axis=x]')
            w.setif(x, y - 1 if i > 0 else y + 1, 0, 'stripped_dark_oak_wood[axis=x]')
            if i >= 1: top.append((x, y - 1 - (1 if i > 0 else 0), 1))
        w.set(sx * 7, F + 40, 0, P['gem'])
        cloth(w.setif, top, 9, (0, 1), P['cloth'], P['cloth2'], P['stripe'], phase=sx)
    for x, z in ((5, 0), (-5, 0), (0, 5), (0, -5)): brazier(w, x, z, F + 2, 'campfire[lit=true]')


# ---------------------------------------------------------------------------------------------------- the gate

def gate(hub):
    """
    The Match Portal Gate (x -20..20, y 100..146, z -48..-24): two towers and, between them, a pointed arch in
    three stepped orders round a glowing portal; a battlement over it with the two colours meeting at a gilded
    keystone; a crimson banner down the west tower and a cobalt one down the east.
    """
    w = hub.w
    for (x, z) in hub.cells['core']:
        if z > -22 or abs(x) > 10: continue
        w.set(x, F, z, DK if abs(x) == 10 else W2 if abs(x) <= 2 else TL if abs(x) == 3 else aged(x, 0, z))
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
                w.set(x, y, z, trims[k] if edge else DK if rel in (0, 27) else aged(x, y, z))
    for x in range(-10, 11):                                         # the battlement and its corbel table
        for z in (-38, -30):
            w.set(x, F + 28, z, stairs(W1, 'south' if z == -38 else 'north', top=True))
            w.set(x, F + 29, z, W1)
            if x % 2 == 0: w.set(x, F + 30, z, W1)
    for x in range(-4, 5):                                           # the two colours meeting at a gilded keystone
        for y in (F + 26, F + 27):
            block = 'gold_block' if x == 0 else CRIMSON['accent'] if x < 0 else COBALT['accent']
            w.set(x, y, -31, block)
            w.set(x, y, -37, block)
    for z in (-35, -34, -33):                                        # the portal
        for x in range(-6, 7):
            for y in range(F + 1, F + 24):
                rel = y - F - 1
                if not opening(x, rel, 2): continue
                frame = not opening(abs(x) + 1, rel, 2) or not opening(x, rel + 1, 2)
                if frame: w.set(x, y, z, 'crying_obsidian' if (x + y) % 3 else 'obsidian')
                elif z == -34: w.set(x, y, z, 'pearlescent_froglight' if (x * 2 + y) % 5 == 0 else 'amethyst_block')
                else:
                    swirl = math.sin(math.hypot(x, rel - 9) * 1.1 - math.atan2(rel - 9, x or 0.01) * 2)
                    w.set(x, y, z, 'magenta_stained_glass' if swirl > 0.35 else 'purple_stained_glass')
    for sx, P in ((-1, CRIMSON), (1, COBALT)):
        tower(Xf(w, sx * 15, -34, mirror=sx < 0), 0, 0, F + 1, 4, 31)
        for x in range(sx * 12, sx * 19, sx):                        # the yard the banner hangs from
            w.set(x, F + 29, -28, 'stripped_dark_oak_wood[axis=x]')
        w.set(sx * 12, F + 29, -29, 'stripped_dark_oak_wood[axis=z]')
        w.set(sx * 18, F + 29, -29, 'stripped_dark_oak_wood[axis=z]')
        cloth(w.setif, [(sx * 15 + i, F + 28, -28) for i in range(-2, 3)], 15, (0, 1), P['cloth'], P['cloth2'], P['stripe'], phase=sx * 2)
        brazier(w, sx * 12, -26, F + 1, P['fire'])
    hub.label(0.5, F + 6.4, -27.5, 'MATCH GATE', 'light_purple',
              'Matches start from the countdown\nwhen enough players are here', 1.5)


# ---------------------------------------------------------------------------------------------------- the pavilion

def pavilion(hub, cx=0, cz=48):
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
        for z in range(cz - 14, cz - 9): w.set(cx + x, F, z, DK if abs(x) == 3 else W2)
    for sx in (-1, 1):
        for z in range(cz - 3, cz + 4):
            for x in range(10, 19): w.set(cx + sx * x, F, z, DK if abs(z - cz) == 3 else W2)
        lamp_post(w, cx + sx * 4, cz - 13, F + 1)
        for i in range(3): w.set(cx + sx * (5 + i), F + 1, cz + 12, stairs('dark_oak_planks', 'north'))
    hub.label(cx + 0.5, F + 5.6, cz - 7.5, 'FRACTAL CTF', 'gold', 'Capture the Flag  •  King of the Hill\nDestroy the Wool', 1.5)
    hub.npc('herald', '<gold><bold>Herald', 'talk', 'cleric', 'plains', (cx + 3.5, F + 1, cz - 4.5, 38),
            ['Welcome to the Sky-Citadel. The <yellow>paper</yellow> votes for the next map, the <yellow>book</yellow> picks your kit.',
             'Crimson holds the west wing, Cobalt the east. The kits stand on show in both.',
             'When enough of you are here the countdown starts. The gate to the north does the rest.'])


# ---------------------------------------------------------------------------------------------------- the wings

def wing(hub, X, P, kits):
    """
    A team's wing, written for the east side (the west is its mirror): a forecourt with the team's emblem inlaid
    and two braziers; an arcade down each side with five kit stands in arched bays under a lean-to roof; and the war
    hall: a gabled hall behind four giant pilasters, a pointed portal, lancet windows, buttressed side walls, corner
    turrets, dormers, tie beams with chandeliers, a dais with the captain's seat, and the team's great banner
    billowing down the gable.
    """
    red = P['side'] == 'red'
    for x in range(51, 76):                                          # the forecourt
        for z in range(-10, 11):
            a = abs(z)
            if a <= 2: s = W2
            elif a == 3 or a == 10 or x == 51: s = DK
            else: s = TL if x % 4 == 1 and a % 3 == 0 else aged(x, 0, z)
            m = abs(x - 63) + a
            if m <= 5: s = DK if m == 5 else P['accent'] if m >= 3 else P['gem'] if m else P['light']
            X.set(x, F, z, s)
    for sz in (-1, 1):
        brazier(X, 53, sz * 7, F + 1, P['fire'])
        for bx, by, bz in ((73, 1, 9), (73, 2, 9), (72, 1, 9), (73, 1, 8)): X.set(bx, F + by, sz * bz, 'barrel[facing=up]')
    for s in (-1, 1):                                                # the kit arcades
        inward, outward = ('north', 'south') if s > 0 else ('south', 'north')
        for x in range(53, 75):
            for q in range(11, 17): X.set(x, F, s * q, DK if q in (11, 15) else W2)
            for y in range(F + 1, F + 9): X.set(x, y, s * 16, DK if y in (F + 1, F + 8) else aged(x, y, s * 16))
            k = (x - 54) % 4
            if k == 0:
                for y in range(F + 1, F + 7): X.set(x, y, s * 17, DK if y == F + 1 else W2)      # a buttress outside
                X.set(x, F + 7, s * 17, stairs(W1, inward))
                for y in range(F + 1, F + 7): X.set(x, y, s * 15, DK if y == F + 1 else W2)
                for y in range(F + 1, F + 6): X.set(x, y, s * 11, DK if y == F + 1 else CH if y == F + 5 else W2)
            elif x > 54:
                if k != 2: X.set(x, F + 5, s * 11, stairs(W1, 'west' if k == 1 else 'east', top=True))
            X.set(x, F + 6, s * 11, W1)
            X.set(x, F + 7, s * 10, stairs(P['roof'], outward))
            for q in (11, 12): X.set(x, F + 7, s * q, P['roof'])
            X.set(x, F + 8, s * 13, stairs(P['roof'], outward))
            for q in (14, 15): X.set(x, F + 8, s * q, P['roof'])
            X.set(x, F + 9, s * 16, slab(P['roof']))
        for q in range(11, 17):
            for y in range(F + 1, F + 8): X.set(53, y, s * q, aged(53, y, s * q))
        for i, xc in enumerate((56, 60, 64, 68, 72)):
            X.set(xc, F + 1, s * 13, 'chiseled_deepslate')
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)): X.set(xc + dx, F, s * 13 + dz, P['accent'])
            X.set(xc, F + 4, s * 15, '%s_wall_banner[facing=%s]' % (P['banner'], inward))
            X.set(xc, F + 6, s * 12, P['lamp'] + '[hanging=true]')
            index = i if s < 0 else 5 + i
            if index < len(kits):
                sx, sy, sz, yaw = X.spot(xc, F + 2, s * 13, 0 if s < 0 else 180)
                hub.kits.append(dict(kit=kits[index], x=sx, y=sy, z=sz, yaw=yaw, team=P['side']))
            else:
                X.set(xc, F + 2, s * 13, P['lamp'] + '[hanging=false]')
    x0, x1, zh = 76, 94, 11                                          # the war hall
    for x in range(x0, x1 + 1):
        for z in range(-zh, zh + 1):
            X.set(x, F, z, TL if (x + z) % 2 else W2)
            if x in (x0, x1) or abs(z) == zh:
                for y in range(F + 1, F + 17): X.set(x, y, z, DK if y in (F + 1, F + 16) else TL if y == F + 9 else aged(x, y, z))
    for s in (-1, 1):
        z = s * zh
        for x in (79, 85, 91):
            for y in range(F + 4, F + 9): X.set(x, y, z, P['glass'])
            X.set(x, F + 9, z, CH)
            X.set(x, F + 3, z, DK)
        for x in (82, 88):
            for y in range(F + 1, F + 14): X.set(x, y, z + s, DK if y == F + 1 else W2)
            X.set(x, F + 14, z + s, stairs(W1, 'north' if s > 0 else 'south'))
    for i in range(12):                                              # the gables
        for z in range(-(11 - i), 12 - i):
            X.set(x0, F + 17 + i, z, aged(x0, F + 17 + i, z))
            X.set(x1, F + 17 + i, z, aged(x1, F + 17 + i, z))
    for z in (-11, -5, 5, 11):                                       # the giant pilasters of the front
        for y in range(F + 1, F + 17): X.set(x0 - 1, y, z, DK if y == F + 1 else CH if y == F + 16 else W2)
    for y in range(F + 1, F + 12):                                   # the portal
        hw = 3 if y <= F + 8 else F + 11 - y
        for z in range(-4, 5):
            if abs(z) <= hw: X.set(x0, y, z, 'air')
            elif abs(z) == hw + 1: X.set(x0, y, z, P['accent2'])
    X.set(x0, F + 12, 0, P['gem'])
    for z in (-8, 8):
        for y in range(F + 4, F + 10): X.set(x0, y, z, P['glass'])
        X.set(x0, F + 10, z, CH)
    for x in range(x0 - 1, x1 + 2):                                  # the roof
        for i in range(12):
            q, y = 12 - i, F + 16 + i
            X.set(x, y, q, stairs(P['roof'], 'north'))
            X.set(x, y, -q, stairs(P['roof'], 'south'))
        X.set(x, F + 27, 0, P['roof'])
        X.set(x, F + 28, 0, P['gem'] if (x - x0) % 6 == 3 else slab(P['roof']))
    for x in (82, 88):                                               # dormers
        for s in (-1, 1):
            for dx in (-1, 0, 1):
                for y in (F + 20, F + 21): X.set(x + dx, y, s * 9, W1 if dx else P['glass'])
                X.set(x + dx, F + 21, s * 8, W1)
                for q in (9, 8, 7):
                    X.set(x + dx, F + 22, s * q, P['roof'] if dx == 0 else stairs(P['roof'], 'east' if dx < 0 else 'west'))
    for cz in (-12, 12):
        turret(X, x0, cz, F + 1, F + 20, P)
        turret(X, x1, cz, F + 1, F + 20, P)
    for x in (80, 86, 92):                                           # tie beams and chandeliers
        for z in range(-10, 11): X.set(x, F + 17, z, P['beam'] + '[axis=z]')
        X.set(x, F + 16, 0, 'iron_chain[axis=y]')
        X.set(x, F + 15, 0, P['lamp'] + '[hanging=true]')
        for z in (-5, 5): X.set(x, F + 16, z, P['lamp'] + '[hanging=true]')
    for x in range(77, 89): X.set(x, F + 1, 0, P['carpet'])
    for z in range(-5, 6):                                           # the dais and the captain's seat
        X.set(89, F + 1, z, stairs(P['accent2'], 'east'))
        for x in range(90, 94): X.set(x, F + 1, z, P['accent2'])
    X.set(92, F + 2, 0, stairs(P['plank'], 'east'))
    for z in (-1, 1):
        X.set(92, F + 2, z, wall(DK))
        X.set(92, F + 3, z, P['lamp'] + '[hanging=false]')
    for z in (-3, 3): X.set(93, F + 6, z, '%s_wall_banner[facing=west]' % P['banner'])
    for x, z in ((78, -8), (78, 8), (88, -8), (88, 8)): X.set(x, F, z, P['light'])
    X.set(80, F + 1, 8, P['fence'])                                  # the training dummy and a target
    X.set(80, F + 2, 8, 'hay_block[axis=y]')
    X.set(80, F + 3, 8, 'carved_pumpkin[facing=north]')
    X.set(84, F + 1, 9, P['fence'])
    X.set(84, F + 2, 9, 'target')
    for z in range(-3, 4):                                           # the yard the great banner hangs from
        X.set(x0 - 2, F + 25, z, 'stripped_dark_oak_wood[axis=z]')
    X.set(x0 - 1, F + 25, -2, 'stripped_dark_oak_wood[axis=x]')
    X.set(x0 - 1, F + 25, 2, 'stripped_dark_oak_wood[axis=x]')
    cloth(X.setif, [(x0 - 2, F + 24, z) for z in range(-2, 3)], 12, (-1, 0), P['cloth'], P['cloth2'], P['stripe'], phase=1.0)
    lx, lz = X.at(64, 0)
    hub.label(lx + 0.5, F + 6.0, lz + 0.5, P['title'], P['colour'], 'Left-click a kit to wear it\nRight-click to look closer', 1.4)
    who = 'Rhea' if red else 'Corin'
    side = P['side']
    hub.npc(side + '-captain', '<%s><bold>Captain %s' % (P['colour'], who), 'kits', 'weaponsmith', 'savanna' if red else 'taiga',
            X.spot(90, F + 2, 2, 90),
            ['Pick your kit before the horn sounds. The <yellow>book</yellow> in your bag has them all.',
             'Defenders win games. Somebody has to stay home.', 'Bridge fast, bridge wide, and never bridge alone.'])
    hub.npc(side + '-quartermaster', '<%s>Quartermaster' % P['colour'], 'kits', 'armorer', 'plains', X.spot(56, F + 1, 5, 90),
            ['Golden apples for every kill. They stack with the ones in your kit.',
             'Out of blocks? Break the map: everything but spawns and objectives breaks.'])
    hub.npc(side + '-scout', '<%s>Scout' % P['colour'], 'join', 'cartographer', 'plains', X.spot(52, F + 1, -5, 90),
            ["I've scouted every island. The void is the deadliest player on all of them.",
             'Long shots count double in my book. Not in the scoreboard, though.'])
    hub.npc(side + '-recruit', '<%s>Recruit' % P['colour'], 'talk', 'none', 'plains', X.spot(80, F + 1, 6, 0),
            ["I've hit that dummy a hundred times. It's winning.", 'They say the wool towers fall faster than they look.',
             'Sarge says if I lose the flag again I\'m on latrine duty. Forever.'])


# ---------------------------------------------------------------------------------------------------- records and parkour

def records(hub, cx=44, cz=48):
    """
    The Hall of Records: a court open to the west, closed on the east by a pilastered wall under a tiled canopy with
    three framed boards; three crates on plinths in front of them; two reading stands at the way in.
    """
    w = hub.w
    X = Xf(w, cx, cz)
    for x in range(-9, 11):
        for z in range(-11, 12):
            if (cx + x, cz + z) not in hub.cells['records']: continue
            edge = x in (-9, 8) or abs(z) == 11
            X.set(x, F, z, DK if edge else W2 if abs(z) <= 2 and x < 0 else TL if (x + z) % 4 == 0 else aged(x, 0, z))
    for z in range(-11, 12):
        pier = abs(z) in (11, 4, 3)
        for y in range(F + 1, F + 11):
            X.set(9, y, z, DK if y in (F + 1, F + 10) else aged(9, y, z))
            if pier: X.set(8, y, z, DK if y == F + 1 else CH if y == F + 10 else W2)
        if abs(z) in (11, 7, 4, 0):                                  # buttresses down the back
            for y in range(F + 1, F + 9): X.set(10, y, z, DK if y == F + 1 else W2)
            X.set(10, F + 9, z, stairs(W1, 'west'))
        X.set(7, F + 11, z, stairs(TL, 'east'))                      # the canopy, overhanging the boards
        X.set(8, F + 11, z, TL)
        X.set(9, F + 11, z, TL)
        X.set(10, F + 11, z, stairs(TL, 'west'))
        X.set(8, F + 12, z, slab(TL))
        X.set(9, F + 12, z, slab(TL))
    names = (('TOP KILLS', -7), ('TOP CAPTURES', 0), ('TOP WINS', 7))
    for title, zc in names:
        for dz in range(-2, 3):
            for y in range(F + 3, F + 8):
                rim_ = abs(dz) == 2 or y in (F + 3, F + 7)
                X.set(8, y, zc + dz, ('gold_block' if abs(dz) == 2 and y in (F + 3, F + 7) else 'stripped_dark_oak_wood[axis=y]') if rim_ else 'black_concrete')
            X.set(8, F + 2, zc + dz, stairs(DK, 'east', top=True))
            X.set(8, F + 8, zc + dz, stairs(DK, 'east'))
        X.set(7, F + 10, zc, 'iron_chain[axis=y]')                   # a lamp hung from the canopy's eave
        X.set(7, F + 9, zc, 'iron_chain[axis=y]')
        X.set(7, F + 8, zc, 'lantern[hanging=true]')
        X.set(8, F + 9, zc, CH)
        hub.label(cx + 7.4, F + 6.3, cz + zc + 0.5, title, 'yellow', 'The top ten:\n/ctfstats', 0.9)
        for dx in (-1, 0, 1):                                        # the crate on its plinth
            for dz in (-1, 0, 1):
                X.set(2 + dx, F + 1, zc + dz, DK if not (dx or dz) else slab(DK))
        X.set(2, F + 2, zc, 'chest[facing=west]')
    for z in (-11, 11):                                              # the short return walls
        for x in range(4, 9):
            for y in range(F + 1, F + 6): X.set(x, y, z, DK if y == F + 1 else aged(x, y, z))
            X.set(x, F + 6, z, slab(TL))
    for z in (-5, 5):
        X.set(-6, F + 1, z, 'lectern[facing=west,has_book=false]')
        lamp_post(X, -8, z, F + 1)
    hub.label(cx - 7.5, F + 5.4, cz + 0.5, 'HALL OF RECORDS', 'aqua', 'Your stats and the leaderboards: /ctfstats', 1.3)
    hub.npc('chronicler', '<aqua><bold>Chronicler', 'talk', 'librarian', 'plains', (cx + 5.5, F + 1, cz + 3.5, 90),
            ['I write down every kill, capture and win. Your page: <yellow>/ctfstats</yellow>.',
             'The leaderboards are in my book too. Top ten, every stat.', 'Someone asked me to erase their deaths. I wrote that down too.'])
    hub.npc('treasurer', '<gold>Treasurer', 'talk', 'toolsmith', 'plains', (cx - 0.5, F + 1, cz - 3.5, 90),
            ['Two coins a kill, five for an objective, ten for the win. <yellow>/coins</yellow> counts them for you.',
             'Save up. The better kits are not cheap, and I do not haggle.'])


PADS = [(-59, 100, 48, 2.1), (-64, 100, 51, 2.0), (-68, 101, 55, 2.1), (-72, 101, 59, 2.0), (-74, 102, 64, 2.1), (-72, 103, 69, 2.0),
        (-68, 103, 73, 2.1), (-63, 104, 75, 2.0), (-58, 105, 74, 2.1), (-53, 105, 71, 2.0), (-49, 106, 68, 2.1)]
FINISH = (-42, 106, 65)


def parkour(hub, shard, cx=-42, cz=48):
    """
    The parkour course: a start arch on its own island, then eleven floating pads climbing in an arc to a finish
    kiosk with a bell, high over the start. Each jump is two or three blocks, never more than one block up.
    """
    w = hub.w
    for (x, z) in hub.cells['parkour']:
        dx, dz = x - cx, z - cz
        w.set(x, F, z, W2 if abs(dz) <= 2 and dx > -9 else DK if abs(dz) == 3 and dx > -9 else aged(x, 0, z))
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
    hub.label(cx - 5.5, F + 10.4, cz + 0.5, 'PARKOUR', 'green', 'Eleven jumps to the bell', 1.3)
    hub.label(fx + 0.5, fy + 8.6, fz + 0.5, 'FINISH', 'gold', None, 1.1)
    hub.npc('trailmaster', '<green><bold>Trailmaster', 'talk', 'shepherd', 'plains', (cx - 2.5, F + 1, cz + 5.5, -120),
            ['Eleven jumps to the bell. Nobody has rung it twice in a row.', 'Miss a jump and the void hands you straight back to the pavilion.',
             'Sprint, jump late, and do not look down.'])


def build(hub, shard):
    """Everything on the islands, in the order that lets later pieces sit on earlier ones."""
    sanctum(hub)
    monument(hub)
    gate(hub)
    pavilion(hub)
    wing(hub, Xf(hub.w, mirror=True), CRIMSON, KITS[0::2])
    wing(hub, Xf(hub.w), COBALT, KITS[1::2])
    records(hub)
    parkour(hub, shard)
