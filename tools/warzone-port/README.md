# Porting Warzone TGM maps

1. Sparse-clone github.com/WarzoneMC/tgm-maps into `src/` (map.json, level.dat, region/ of the maps you want).
2. `convert.sh "<Cat/Map Name>" <id>` upgrades the world on Paper 26.2 with `upgrader` (a void-generator plugin
   that loads every chunk in the region files, saves and stops) and lays it out as `maps/<id>/`.
3. `run_translate.py [ids]` writes each `map.yml` (see EXTRA there for icons, minutes and extra-mode hints).
4. `prune.py <ids>` drops empty/stray chunks; `render.py <ids>` draws top-down checks with markers.

The maps are CC BY-SA 4.0: keep `original-authors` in every map.yml.

## Batches and hills
- `ids.txt` is batch 1, `ids2.txt` batch 2 (`Cat/Map|id` per line). Pick one with `IDS=ids2.txt python3 run_translate.py [ids]`.
  Don't rerun a finished batch without checking the renders: hand-checked hills may move if the tools changed.
- KOTH hills are found automatically (open sky, flat, raised, near the middle between the spawns, never in water,
  ≥10 blocks from flags and spawns), as a middle hill plus a mirrored pair. A map only gets KOTH with 2+ good hills.
- Per-map overrides in EXTRA: `hills=[(name,(x,y,z))]` places them by hand; `extra-hills` replaces a found hill
  in that zone (corners, optional `beacon`); `koth-search` widens the middle search; `hill-margin` changes the 10.
- Hill names come from `hill_names.json` by position (North/West first, Middle, South/East last).
- Display names lose trailing version tags (" II", " 2", " CTW").
