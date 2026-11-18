#!/usr/bin/env bash
# Builds the mod, (re)generates the worlds if missing, and assembles dist/TheHollowAtlas-v<version>/
set -euo pipefail
cd "$(dirname "$0")/.."
VERSION=$(grep '^mod_version=' gradle.properties | cut -d= -f2)
./gradlew build --no-daemon -q
[ -f dist/TheHollowAtlas-world.zip ] || ./tools/build_world.sh
[ -f dist/TheHollowAtlas-DevTest-world.zip ] || ./tools/build_devtest_world.sh
NAME=TheHollowAtlas-v$VERSION
OUT=dist/$NAME
rm -rf "$OUT" && mkdir -p "$OUT/mods" "$OUT/licenses"
cp build/libs/hollowatlas-$VERSION.jar "$OUT/mods/"
FAPI=$(find ~/.gradle/caches/modules-2 -name "fabric-api-0.116.6+1.21.1.jar" | head -1)
cp "$FAPI" "$OUT/mods/"
cp dist/TheHollowAtlas-world.zip dist/TheHollowAtlas-DevTest-world.zip "$OUT/"
cp QUICKSTART.txt README.md docs/DEV_TESTING.md "$OUT/"
cat > "$OUT/licenses/FABRIC_API_LICENSE.txt" <<'LIC'
Fabric API (https://github.com/FabricMC/fabric) is licensed under the Apache License 2.0.
It is redistributed unmodified for convenience.
LIC
( cd dist && rm -f $NAME.zip && zip -qr $NAME.zip $NAME )
sha256sum "$OUT"/mods/*.jar "$OUT"/*.zip dist/$NAME.zip | tee dist/SHA256SUMS.txt
