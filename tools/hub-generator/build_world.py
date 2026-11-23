"""
build_world.py: builds the Shattered Sky-Citadel hub and writes it out.

    python3 build_world.py [output folder] [--blocks names.txt]

Writes <output>/world (the world folder for the CTF-Hub server) and <output>/hub-npcs.yml (where the plugin's
characters and kit stands go, for plugins/FractalCTF/), then prints a verification summary. `--blocks` takes a
file of valid block names (one per line, any case) to check every block used against.
"""
import json
import os
import shutil
import sys
import time

import architecture_and_details as ad
import terrain_and_bridges as tb
import world_writer as ww

SPAWN = (0, 101, 48)          # the lodestone at the heart of the arrival pavilion, facing north


def _num(v):
    v = round(float(v), 2)
    return str(int(v)) if v == int(v) else str(v)


def write_npcs(path, hub):
    """hub-npcs.yml as the plugin reads it: spawn, the armoury (where kit stands show), kit stands, characters."""
    q = lambda s: json.dumps(s, ensure_ascii=False)
    lines = ['# Where the hub\'s characters stand (written by build_world.py for the Sky-Citadel hub).',
             'spawn: "%s %d %s 180 0"' % (SPAWN[0] + 0.5, SPAWN[1], SPAWN[2] + 0.5),
             '# the kit stands are in both wings, so they show from anywhere in the hub',
             'armoury: {x: 0.5, y: %d, z: 0.5, radius: 130}' % SPAWN[1],
             'kits:']
    for k in sorted(hub.kits, key=lambda k: k['kit']):
        lines.append('  - {kit: %s, x: %s, y: %s, z: %s, yaw: %s, team: %s}' % (k['kit'], _num(k['x']), _num(k['y']), _num(k['z']), _num(k['yaw']), k['team']))
    lines.append('npcs:')
    for n in hub.npcs:
        lines.append('  - {id: %s, name: %s, role: %s, profession: %s, type: %s, x: %s, y: %s, z: %s, yaw: %s, lines: [%s]}' % (
            n['id'], q(n['name']), n['role'], n['profession'], n['type'], _num(n['x']), _num(n['y']), _num(n['z']), _num(n['yaw']),
            ', '.join(q(line) for line in n['lines'])))
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
    tb.dress(w, cells)
    touched = ww.finalize_states(w)
    folder = os.path.join(out, 'world')
    shutil.rmtree(folder, ignore_errors=True)
    saved = ww.save_world(w, folder, SPAWN, yaw=180.0, time_of_day=6000, margin=10)
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
    if len(hub.kits) != len(ad.KITS): problems.append('%d kit stands for %d kits' % (len(hub.kits), len(ad.KITS)))
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
    print('  saved with it     %d block entities, %d labels' % (saved['block_entities'], saved['entities']))
    print('  plugin            spawn %s facing north, %d kit stands (%d red, %d blue), %d characters' % (
        SPAWN, len(hub.kits), sum(k['team'] == 'red' for k in hub.kits), sum(k['team'] == 'blue' for k in hub.kits), len(hub.npcs)))
    print('  block names       ' + ('checked against %d known names' % len(names) if names is not None else 'not checked (no --blocks file)'))
    print('  result            ' + ('OK' if not problems else '%d PROBLEMS' % len(problems)))
    for p in problems: print('    - ' + p)
    return 1 if problems else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
