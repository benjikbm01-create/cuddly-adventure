#!/usr/bin/env bash
# Builds the scenery SHOWCASE world (2001 x 2001 blocks, terrain + buildings only, no residents or quests).
#   tools/build_showcase_world.sh        full build  -> run-showcase/hollow_atlas_showcase (for screenshots/QA)
#   tools/build_showcase_world.sh seed   small seed  -> dist/TheHollowAtlas-Showcase-world.zip
#                                        (an empty world that builds the map itself the first time it is opened)
# Its save folder name differs from the story and test worlds, so it can never overwrite them.
set -euo pipefail
cd "$(dirname "$0")/.."
python3 tools/showcase_src/showcase.py
MODE="${1:-full}"
if [ "$MODE" = "seed" ]; then DIR=run-showcase-seed; TASK=runPrepareShowcaseWorld; else DIR=run-showcase; TASK=runBuildShowcaseWorld; fi
WORLD=$DIR/hollow_atlas_showcase
rm -rf "$WORLD"
mkdir -p "$DIR"
echo "eula=true" > "$DIR/eula.txt"
cat > "$DIR/server.properties" <<'PROPS'
level-name=hollow_atlas_showcase
level-type=minecraft\:flat
generator-settings={"layers"\:[{"block"\:"minecraft\:bedrock","height"\:1},{"block"\:"minecraft\:stone","height"\:123},{"block"\:"minecraft\:dirt","height"\:4},{"block"\:"minecraft\:grass_block","height"\:1}],"biome"\:"minecraft\:plains","features"\:false,"lakes"\:false,"structure_overrides"\:[]}
generate-structures=false
gamemode=creative
difficulty=peaceful
online-mode=false
spawn-protection=0
view-distance=6
simulation-distance=4
spawn-monsters=false
level-seed=2026
PROPS
./gradlew $TASK --no-daemon
if [ "$MODE" = "seed" ]; then
  mkdir -p dist && rm -f dist/TheHollowAtlas-Showcase-world.zip
  ( cd "$DIR" && rm -f hollow_atlas_showcase/session.lock && zip -qr ../dist/TheHollowAtlas-Showcase-world.zip hollow_atlas_showcase -x 'hollow_atlas_showcase/playerdata/*' -x 'hollow_atlas_showcase/stats/*' -x 'hollow_atlas_showcase/advancements/*' )
  ls -la dist/TheHollowAtlas-Showcase-world.zip
else
  du -sh "$WORLD"
fi
