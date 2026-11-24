#!/bin/sh
# convert.sh "<Cat/Map Name>" <id>: upgrade a TGM world to 26.2 and lay it out as maps/<id>/
set -e
SRC="/home/claude/tgm/src/$1"; ID="$2"; OUT="/home/claude/tgm/maps/$ID"
cd /tmp/conv/srv
rm -rf world && mkdir world && cp "$SRC/level.dat" world/ && cp -r "$SRC/region" world/
timeout 400 java -Xmx3G -jar paper.jar --nogui < /dev/null > "/home/claude/tgm/logs/$ID.log" 2>&1 || true
grep -h "UPGRADE" "/home/claude/tgm/logs/$ID.log" | sed 's/.*UPGRADE/UPGRADE/'
rm -rf "${OUT:?}" && mkdir -p "$OUT/dimensions/minecraft/overworld"
cp world/level.dat "$OUT/"
cp -r world/data "$OUT/"
for d in region entities poi; do [ -d "world/dimensions/minecraft/overworld/$d" ] && cp -r "world/dimensions/minecraft/overworld/$d" "$OUT/dimensions/minecraft/overworld/"; done
true
