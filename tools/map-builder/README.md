# CtfBuilder (map builder)

A small Paper plugin that builds the FractalCTF maps from code: `/ctfbuild <lobby|frostfall|canyon|kingdoms>`.
Each run makes a fresh empty world, builds the map, saves it, and writes into `plugins/CtfBuilder/<map>/`:

- `map.yml` (arenas) or `hub-spawn.txt` (lobby): spawns, flags, wool and the hill, ready for FractalCTF
- `top.png` and `oblique.png`: preview renders

Use it on a throwaway server, never a live one. Built worlds land in `world/dimensions/minecraft/build_<map>/`.
To install an arena: make `plugins/FractalCTF/maps/<map>/` with a `level.dat`, the `map.yml`, and the built
folder's `region`, `entities` and `poi` in `dimensions/minecraft/overworld/`. The lobby replaces the hub world's
`dimensions/minecraft/overworld/` (it includes empty chunks out to 26 chunks, so no terrain generates around it).

Build: `./gradlew build` here (it isn't part of the main FractalCTF build).
