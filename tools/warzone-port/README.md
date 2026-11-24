# Porting Warzone TGM maps

1. Sparse-clone github.com/WarzoneMC/tgm-maps into `src/` (map.json, level.dat, region/ of the maps you want).
2. `convert.sh "<Cat/Map Name>" <id>` upgrades the world on Paper 26.2 with `upgrader` (a void-generator plugin
   that loads every chunk in the region files, saves and stops) and lays it out as `maps/<id>/`.
3. `run_translate.py [ids]` writes each `map.yml` (see EXTRA there for icons, minutes and extra-mode hints).
4. `prune.py <ids>` drops empty/stray chunks; `render.py <ids>` draws top-down checks with markers.

The maps are CC BY-SA 4.0: keep `original-authors` in every map.yml.
