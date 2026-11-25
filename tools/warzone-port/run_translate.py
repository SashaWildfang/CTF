import sys, os
sys.path.insert(0, '/home/claude/tgm/tools')
from translate import translate, dump
EXTRA = {
 'museum': dict(koth=1, icon='PAINTING', minutes=12), 'cabinfever': dict(koth=1, icon='SNOW_BLOCK', minutes=12),
 'grassymound': dict(koth=1, icon='GRASS_BLOCK', minutes=10), 'acear': dict(icon='PRISMARINE', minutes=12),
 'cargo': dict(ctf=1, koth=1, icon='CHEST', minutes=15), 'sixtymine': dict(ctf=1, koth=1, icon='STONE_BRICK_WALL', minutes=15, hills=[('Mineshaft', (-20, 8, 5)), ('Bell Tower', (20, 8, -5))]),
 'aequabilis': dict(ctf=1, icon='ORANGE_WOOL', minutes=15), 'keipha': dict(ctf=1, koth=1, icon='CAKE', minutes=15),
 'junglebeat': dict(ctf=1, koth=1, icon='JUNGLE_LOG', minutes=15), 'molendinis': dict(ctf=1, koth=1, icon='WHEAT', minutes=12),
 'formorgar': dict(ctf=1, koth=1, icon='QUARTZ_PILLAR', minutes=15), 'mushtastic': dict(ctf=1, koth=1, icon='RED_MUSHROOM_BLOCK', minutes=12, **{'koth-search': 10}),
 'sectorsix': dict(ctf=1, icon='PRISMARINE_BRICKS', minutes=15), 'navalconquest': dict(ctf=1, koth=1, icon='OAK_BOAT', minutes=15),
 'anasaria': dict(ctf=1, koth=1, icon='MOSSY_COBBLESTONE', minutes=12, **{'koth-search': 10}), 'steamgrown': dict(ctf=1, icon='PISTON', minutes=12),
 'templum': dict(ctf=1, koth=1, icon='CHISELED_SANDSTONE', minutes=12), 'autunno': dict(ctf=1, icon='ORANGE_TERRACOTTA', minutes=12),
 'northwinds': dict(ctf=1, icon='SPRUCE_SAPLING', minutes=10), 'volitare': dict(ctf=1, icon='MOSS_BLOCK', minutes=12),
 'palace': dict(koth=1, icon='QUARTZ_BLOCK', minutes=15), 'voltage': dict(koth=1, icon='LIGHTNING_ROD', minutes=10),
 'bulwark': dict(koth=1, icon='STONE_BRICKS', minutes=10), 'rampant': dict(koth=1, icon='MOSSY_COBBLESTONE', minutes=12, hills=[('Upper Ford', (165, 25, 34)), ('Lower Ford', (166, 25, 78))]),
 'persisto': dict(ctf=1, koth=1, icon='OAK_LOG', minutes=15), 'tranquility': dict(ctf=1, koth=1, icon='JUNGLE_SAPLING', minutes=15),
 'chestnut': dict(ctf=1, koth=1, icon='SPRUCE_LOG', minutes=12), 'sandyparadise': dict(ctf=1, koth=1, icon='SAND', minutes=15),
 'timber': dict(ctf=1, koth=1, icon='DARK_OAK_LOG', minutes=15), 'arcticruins': dict(ctf=1, koth=1, icon='PACKED_ICE', minutes=15, **{'koth-search': 6, 'extra-hills': [{'index': 1, 'name': 'Glacier Hall', 'corner-1': '216 10 131', 'corner-2': '223 13 142', 'beacon': '219 9 131'}]}),
 'loyalty': dict(ctf=1, koth=1, icon='OBSIDIAN', minutes=12, **{'koth-at': (-316, 1173), 'koth-y': 8, 'extra-hills': [{'index': 1, 'name': 'Loyal Isle', 'corner-1': '-322 4 1170', 'corner-2': '-310 8 1178', 'beacon': '-316 3 1178'}]}), 'riverbank': dict(ctf=1, koth=1, icon='WATER_BUCKET', minutes=15, **{'extra-hills': [{'index': 1, 'name': 'Mill Island', 'corner-1': '-18 7 -2', 'corner-2': '-14 10 2'}]}),
 'antiquis': dict(ctf=1, koth=1, icon='MOSSY_STONE_BRICKS', minutes=15), 'kyoto': dict(ctf=1, koth=1, icon='CHERRY_LEAVES', minutes=15),
 'sandstone': dict(ctf=1, koth=1, icon='SANDSTONE', minutes=12, hills=[('North', (-1, 7, -8)), ('Middle', (0, 9, 21)), ('South', (1, 7, 50))]), 'oriental': dict(ctf=1, koth=1, icon='RED_TERRACOTTA', minutes=12, **{'extra-hills': [{'index': 1, 'name': 'Dragon Court', 'corner-1': '112 22 -35', 'corner-2': '116 25 -31'}]}),
 'paddocks': dict(ctf=1, koth=1, icon='HAY_BLOCK', minutes=15), 'ricco': dict(ctf=1, icon='QUARTZ_PILLAR', minutes=12),
 'cobalt': dict(ctf=1, icon='LAPIS_BLOCK', minutes=12), 'eclipse': dict(icon='BLACK_CONCRETE', minutes=10),
}
import json
NAMES = json.load(open('/home/claude/tgm/hill_names.json'))
for k, v in NAMES.items(): EXTRA[k]['hill-names'] = v
only = sys.argv[1:]
IDS = os.environ.get('IDS', '/home/claude/tgm/ids.txt')
for line in open(IDS):
    p, mid = line.strip().split('|')
    if only and mid not in only: continue
    if not os.path.isdir(f'/home/claude/tgm/maps/{mid}/dimensions'): print(mid, 'not converted yet'); continue
    y, notes = translate(p, mid, EXTRA[mid])
    open(f'/home/claude/tgm/maps/{mid}/map.yml', 'w').write(dump(y))
    print(f"## {mid}: modes={y['modes']} void-y={y['void-y']}")
    for n in notes: print('   ', n)
