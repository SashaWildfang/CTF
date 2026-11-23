"""
build_world.py: builds the Shattered Sky-Citadel hub and writes it out.

    python3 build_world.py [output folder] [--blocks names.txt]

Writes <output>/world (the world folder for the CTF-Hub server) and <output>/hub-npcs.yml (where the plugin's
characters and kit stands go, for plugins/FractalCTF/), then prints a verification summary. `--blocks` takes a
file of valid block names (one per line, any case) to check every block used against.
"""
import json
import math
import os
import shutil
import sys
import time

import architecture_and_details as ad
import terrain_and_bridges as tb
import world_writer as ww

SPAWN = (0, 101, 72)          # the lodestone at the heart of the arrival pavilion, facing north


def _num(v):
    v = round(float(v), 2)
    return str(int(v)) if v == int(v) else str(v)


def write_npcs(path, hub):
    """hub-npcs.yml as the plugin reads it: spawn, the armoury (where kit stands show), kit stands, characters."""
    q = lambda s: json.dumps(s, ensure_ascii=False)
    lines = ['# Where the hub\'s characters stand (written by build_world.py for the Sky-Citadel hub).',
             'spawn: "%s %d %s 180 0"' % (SPAWN[0] + 0.5, SPAWN[1], SPAWN[2] + 0.5),
             '# every kit stands on the Armoury Terrace; the radius covers the whole hub, so they are there from the moment you arrive',
             'armoury: {x: 0.5, y: %d, z: 0.5, radius: 230}' % SPAWN[1],
             'kits:']
    for k in sorted(hub.kits, key=lambda k: k['kit']):
        lines.append('  - {kit: %s, x: %s, y: %s, z: %s, yaw: %s, team: %s}' % (k['kit'], _num(k['x']), _num(k['y']), _num(k['z']), _num(k['yaw']), k['team']))
    lines.append('npcs:')
    for n in hub.npcs:
        pace = ', pace: "%s"' % '; '.join('%s %s' % (_num(x), _num(z)) for x, z in n['pace']) if n.get('pace') else ''
        lines.append('  - {id: %s, name: %s, role: %s, profession: %s, type: %s, x: %s, y: %s, z: %s, yaw: %s%s, lines: [%s]}' % (
            n['id'], q(n['name']), n['role'], n['profession'], n['type'], _num(n['x']), _num(n['y']), _num(n['z']), _num(n['yaw']), pace,
            ', '.join(q(line) for line in n['lines'])))
    lines.append('# Top 10 boards (FractalCTF 1.7.0+): the stat, the foot of the text, the way it faces (leave yaw out for a')
    lines.append('# board that turns to face whoever looks at it). Stats: kills, wins, captures, kdr, wools, best_streak, longest_shot, hill_seconds.')
    lines.append('boards:')
    for b in hub.boards:
        lines.append('  - {stat: %s, x: %s, y: %s, z: %s, yaw: %s, scale: %s}' % (b['stat'], _num(b['x']), _num(b['y']), _num(b['z']), _num(b['yaw']), _num(b['scale'])))
    with open(path, 'w', encoding='utf-8') as f:
        f.write('\n'.join(lines) + '\n')


def _stand(w, x, y, z):
    """Whether someone can stand at (x, y, z): something under their feet and two blocks of room."""
    bx, by, bz = int(x // 1), int(y), int(z // 1)
    below = w.get(bx, by - 1, bz)
    room = all(w.air(bx, by + k, bz) or w.name(bx, by + k, bz).endswith('_carpet') for k in (0, 1))
    return below != 'air' and room, below


def main(argv):
    out = next((a for a in argv[1:] if not a.startswith('--')), 'out')
    names = None
    if '--blocks' in argv:
        names = {line.strip().lower() for line in open(argv[argv.index('--blocks') + 1]) if line.strip()}
    started = time.time()
    w = ww.World()
    cells = tb.lay_islands(w)
    hub = ad.Hub(w, cells)
    ad.build(hub, tb.shard)
    minis = tb.dress(w, cells)
    touched = ww.finalize_states(w)
    folder = os.path.join(out, 'world')
    shutil.rmtree(folder, ignore_errors=True)
    saved = ww.save_world(w, folder, SPAWN, yaw=180.0, time_of_day=6000, margin=8)
    write_npcs(os.path.join(out, 'hub-npcs.yml'), hub)
    read_chunks, read_sections = ww.check_world(folder)

    counts = w.counts()
    problems = []
    if names is not None:
        problems += ['unknown block: ' + n for n in sorted(counts) if n not in names]
    ok, below = _stand(w, SPAWN[0] + 0.5, SPAWN[1], SPAWN[2] + 0.5)
    if not ok: problems.append('nobody can stand at the spawn (on %s)' % below)
    for k in hub.kits:
        ok, below = _stand(w, k['x'], k['y'], k['z'])
        if not ok: problems.append('kit stand %s has no room (on %s)' % (k['kit'], below))
    for n in hub.npcs:
        ok, below = _stand(w, n['x'], n['y'], n['z'])
        if not ok: problems.append('%s has no room (on %s)' % (n['id'], below))
    if sorted(k['kit'] for k in hub.kits) != sorted(ad.KITS): problems.append('%d kit stands for %d kits' % (len(hub.kits), len(ad.KITS)))
    if len({n['id'] for n in hub.npcs}) != len(hub.npcs): problems.append('two characters share an id')
    for n in hub.npcs:                                               # a pacing character's whole walk is level, open ground
        pts = n.get('pace') or []
        for (ax, az), (bx, bz) in zip(pts, pts[1:]):
            steps = int(max(abs(bx - ax), abs(bz - az)) * 2) + 1
            for i in range(steps + 1):
                ok, below = _stand(w, ax + (bx - ax) * i / steps, n['y'], az + (bz - az) * i / steps)
                if not ok:
                    problems.append('%s cannot walk its beat (blocked near %.1f, %.1f)' % (n['id'], ax + (bx - ax) * i / steps, az + (bz - az) * i / steps))
                    break
    for a, b in tb.CHAIN_ENDS:                                       # both ends of every anchor chain are buried in rock
        for p in (a, b):
            cell = (int(p[0] // 1), int(p[1] // 1), int(p[2] // 1))
            if w.air(*cell) or w.name(*cell) in ('polished_blackstone', 'blackstone'): problems.append('an anchor chain ends in the air at %s' % (p,))
    for title, x, low, high, z, r in hub.labels:                     # every label hangs in open air, whichever way it turns
        hit = set()
        for bx in range(int((x - r) // 1), int((x + r) // 1) + 1):
            for bz in range(int((z - r) // 1), int((z + r) // 1) + 1):
                if math.hypot(bx + 0.5 - x, bz + 0.5 - z) > r + 0.5: continue
                for by in range(int(low // 1), int(high // 1) + 1):
                    if not w.air(bx, by, bz): hit.add(w.name(bx, by, bz))
        if hit: problems.append('label %s touches %s' % (title, ', '.join(sorted(hit))))
        if high > ad.F + 8 and title != 'FINISH': problems.append('label %s hangs too high' % title)
    if read_chunks != saved['chunks']: problems.append('read back %d chunks of %d' % (read_chunks, saved['chunks']))

    x0, y0, z0, x1, y1, z1 = w.bounds()
    total = sum(counts.values())
    print('Shattered Sky-Citadel hub: built in %.1f s' % (time.time() - started))
    print('  blocks placed     %d of %d kinds (%d block states)' % (total, len(counts), saved['states']))
    print('  most used         ' + ', '.join('%s %d' % kv for kv in sorted(counts.items(), key=lambda kv: -kv[1])[:8]))
    print('  build box         x %d..%d  y %d..%d  z %d..%d (whole sections)' % (x0, x1, y0, y1, z0, z1))
    print('  neighbour states  %d fences, walls, panes, stairs and block entities worked out' % touched)
    print('  chunks            %d written (%d built, the rest empty void margin) in %d region files; %d sections read back' % (
        saved['chunks'], saved['built_chunks'], saved['regions'], read_sections))
    print('  saved with it     %d block entities, %d entities (labels and display stands)' % (saved['block_entities'], saved['entities']))
    print('  plugin            spawn %s facing north, %d kit stands (%s), %d characters, %d labels in open air' % (
        SPAWN, len(hub.kits), ', '.join('%d %s' % (sum(k['team'] == t for k in hub.kits), t) for t in ad.TEAMS), len(hub.npcs), len(hub.labels)))
    print('  mini-islands      %d garden islands, %d ruin rocks, %d crystal shards, %d stepping stones, in clusters off the main islands' % (
        minis['lush'], minis['ruin'], minis['crystal'], minis['stone']))
    print('  anchor chains     %d, every end inside rock; %d Top 10 boards; %d characters walk a beat' % (
        len(tb.CHAIN_ENDS), len(hub.boards), sum(1 for n in hub.npcs if n.get('pace'))))
    print('  block names       ' + ('checked against %d known names' % len(names) if names is not None else 'not checked (no --blocks file)'))
    print('  result            ' + ('OK' if not problems else '%d PROBLEMS' % len(problems)))
    for p in problems: print('    - ' + p)
    return 1 if problems else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
