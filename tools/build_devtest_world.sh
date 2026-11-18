#!/usr/bin/env bash
# Generates the separate Developer Testing world ("Proving Grounds"): noncanonical test content only.
#   -> run-devworld/hollow_atlas_devtest + dist/TheHollowAtlas-DevTest-world.zip
# Its save folder name differs from the story world, so it can never overwrite a story save.
set -euo pipefail
cd "$(dirname "$0")/.."
WORLD=run-devworld/hollow_atlas_devtest
rm -rf "$WORLD"
mkdir -p run-devworld
echo "eula=true" > run-devworld/eula.txt
cat > run-devworld/server.properties <<'PROPS'
level-name=hollow_atlas_devtest
level-type=minecraft\:flat
generator-settings={"layers"\:[{"block"\:"minecraft\:bedrock","height"\:1},{"block"\:"minecraft\:stone","height"\:123},{"block"\:"minecraft\:dirt","height"\:4},{"block"\:"minecraft\:grass_block","height"\:1}],"biome"\:"minecraft\:plains","features"\:false,"lakes"\:false,"structure_overrides"\:[]}
generate-structures=false
gamemode=adventure
difficulty=normal
online-mode=false
spawn-protection=0
view-distance=6
simulation-distance=6
spawn-monsters=false
level-seed=4242
PROPS
./gradlew runBuildDevWorld --no-daemon
mkdir -p dist
rm -f dist/TheHollowAtlas-DevTest-world.zip
( cd run-devworld && rm -f hollow_atlas_devtest/session.lock && zip -qr ../dist/TheHollowAtlas-DevTest-world.zip hollow_atlas_devtest -x 'hollow_atlas_devtest/playerdata/*' -x 'hollow_atlas_devtest/stats/*' -x 'hollow_atlas_devtest/advancements/*' )
cp run-devworld/hollow_atlas_devtest/hollowatlas/atlas_devtest_grounds.png src/main/resources/assets/hollowatlas/textures/gui/ 2>/dev/null || true
echo "Dev test world packaged at dist/TheHollowAtlas-DevTest-world.zip"
