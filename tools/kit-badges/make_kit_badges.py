"""
Kit badges for FractalCTF: one pill per kit per team, drawn like the network's rank badges (same 9-pixel pill, the
same 5-pixel letters lifted from the rank badges, light rim, darker lower band, white letters fading to a tint).
Writes a resource pack add-on (font fractalctf:kits, chars U+E000 + team*32 + kit) to put in
plugins/Nexo/pack/external_packs/ on both CTF servers. The plugin uses the same numbering (KitBadges.java).

    python3 make_kit_badges.py <out.zip> [preview.png]
"""
import io, json, os, sys, zipfile
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
LETTERS = json.load(open(os.path.join(HERE, 'letters.json')))
# the four letters no rank badge has, drawn to match
LETTERS.update({
    'S': [".####", "#....", ".###.", "....#", "####."],
    'X': ["#...#", ".#.#.", "..#..", ".#.#.", "#...#"],
    'Y': ["#...#", ".#.#.", "..#..", "..#..", "..#.."],
    'J': ["..###", "...#.", "...#.", "#..#.", ".##.."],
})
# in the plugin's order (Kit enum), and the team order (TeamColor enum: RED, BLUE, GREEN, YELLOW)
KITS = ["Barbarian", "Big Spender", "Builder", "Choruser", "Demoman", "Engineer", "Fire Archer", "Infiltrator", "Medic",
        "Necromancer", "Ninja", "Phoenix", "Redstone Mechanic", "Seeking Archer", "Sheep Farmer", "Shielder", "Spy", "Tank", "Thor"]
TEAMS = {  # rim, main, dark band, letters top, letters bottom (red/blue/green from the Owner/Champion/Guardian badges)
    'red':    ('#f64242', '#c42b2b', '#911f1f', '#fefefe', '#ffc3be'),
    'blue':   ('#41c7fc', '#009ddb', '#007cad', '#ffffff', '#b8ebff'),
    'green':  ('#7ef448', '#2bc804', '#27a607', '#ffffff', '#b4f895'),
    'yellow': ('#ffd84a', '#e8b400', '#b38600', '#ffffff', '#fff0b3'),
}
TEAM_ORDER = ['red', 'blue', 'green', 'yellow']


def hexc(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


def badge(text, colours):
    text = text.upper()
    width = 0
    for ch in text:
        width += 3 if ch == ' ' else len(LETTERS[ch][0]) + 1
    width -= 1
    W = 4 + width + 4
    a, b, d, c, e = (hexc(x) for x in colours)
    im = Image.new('RGBA', (W, 9), (0, 0, 0, 0))
    for x in range(W):
        for y in range(9):
            edge = x == 0 or x == W - 1
            if y in (0, 8) and edge: continue
            if y == 0: col = a
            elif y == 1: col = a if x < 2 or x >= W - 2 else b
            elif y <= 4: col = a if edge else b
            elif y <= 6: col = b if edge else d
            elif y == 7: col = b if x < 2 or x >= W - 2 else d
            else: col = b
            im.putpixel((x, y), col + (255,))
    x0 = 4
    for ch in text:
        if ch == ' ':
            x0 += 3
            continue
        rows = LETTERS[ch]
        for y, row in enumerate(rows):
            for x, p in enumerate(row):
                if p == '#': im.putpixel((x0 + x, 2 + y), (c if y < 3 else e) + (255,))
        x0 += len(rows[0]) + 1
    return im


def main():
    out = sys.argv[1]
    providers, images = [], []
    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as z:
        z.writestr('pack.mcmeta', json.dumps({"pack": {"pack_format": 46, "supported_formats": [46, 99],
                                                       "description": "FractalCTF kit badges"}}, indent=1))
        for t, team in enumerate(TEAM_ORDER):
            for k, kit in enumerate(KITS):
                im = badge(kit, TEAMS[team])
                images.append(im)
                name = kit.lower().replace(' ', '_')
                buf = io.BytesIO(); im.save(buf, 'PNG')
                z.writestr(f'assets/fractalctf/textures/font/kits/{team}/{name}.png', buf.getvalue())
                providers.append({"type": "bitmap", "file": f"fractalctf:font/kits/{team}/{name}.png",
                                  "ascent": 8, "height": 9, "chars": [chr(0xE000 + t * 32 + k)]})
        z.writestr('assets/fractalctf/font/kits.json', json.dumps({"providers": providers}, ensure_ascii=False, indent=1))
    if len(sys.argv) > 2:  # a preview sheet: one column per team, 4x
        colw = max(i.width for i in images) + 6
        sheet = Image.new('RGBA', (colw * 4 + 6, (9 + 4) * len(KITS) + 6), (34, 34, 42, 255))
        for n, im in enumerate(images):
            t, k = divmod(n, len(KITS))
            sheet.alpha_composite(im, (6 + t * colw, 6 + k * 13))
        sheet.resize((sheet.width * 4, sheet.height * 4), Image.NEAREST).save(sys.argv[2])
    print(len(providers), 'badges ->', out)


main()
