#!/usr/bin/env bash
# Regenerates the playable world from the region source data and packages it.
#   tools/build_world.sh            -> run-world/hollow_atlas (world) + dist/TheHollowAtlas-world.zip
# Requires JDK 21. Runs a headless dedicated server once; no client needed.
set -euo pipefail
cd "$(dirname "$0")/.."
WORLD=run-world/hollow_atlas
rm -rf "$WORLD"
mkdir -p run-world
echo "eula=true" > run-world/eula.txt
cat > run-world/server.properties <<'PROPS'
level-name=hollow_atlas
level-type=minecraft\:flat
generator-settings={"layers"\:[{"block"\:"minecraft\:bedrock","height"\:1},{"block"\:"minecraft\:stone","height"\:123},{"block"\:"minecraft\:dirt","height"\:4},{"block"\:"minecraft\:grass_block","height"\:1}],"biome"\:"minecraft\:plains","features"\:false,"lakes"\:false,"structure_overrides"\:[]}
generate-structures=false
gamemode=adventure
force-gamemode=false
difficulty=normal
online-mode=false
spawn-protection=0
view-distance=6
simulation-distance=6
spawn-animals=true
spawn-monsters=false
level-seed=1171
PROPS
./gradlew runBuildWorld --no-daemon
mkdir -p dist
rm -f dist/TheHollowAtlas-world.zip
( cd run-world && rm -f hollow_atlas/session.lock && zip -qr ../dist/TheHollowAtlas-world.zip hollow_atlas -x 'hollow_atlas/playerdata/*' -x 'hollow_atlas/stats/*' -x 'hollow_atlas/advancements/*' )
echo "World packaged at dist/TheHollowAtlas-world.zip"
