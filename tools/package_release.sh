#!/usr/bin/env bash
# Builds the mod, (re)generates the world if missing, and assembles dist/TheHollowAtlas-v0.1/
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew build --no-daemon -q
[ -f dist/TheHollowAtlas-world.zip ] || ./tools/build_world.sh
OUT=dist/TheHollowAtlas-v0.1
rm -rf "$OUT" && mkdir -p "$OUT/mods" "$OUT/licenses"
cp build/libs/hollowatlas-0.1.0.jar "$OUT/mods/"
FAPI=$(find ~/.gradle/caches/modules-2 -name "fabric-api-0.116.6+1.21.1.jar" | head -1)
cp "$FAPI" "$OUT/mods/"
cp dist/TheHollowAtlas-world.zip "$OUT/"
cp QUICKSTART.txt README.md "$OUT/"
cat > "$OUT/licenses/FABRIC_API_LICENSE.txt" <<'EOF'
Fabric API (https://github.com/FabricMC/fabric) is licensed under the Apache License 2.0.
It is redistributed unmodified for convenience.
EOF
( cd dist && rm -f TheHollowAtlas-v0.1.zip && zip -qr TheHollowAtlas-v0.1.zip TheHollowAtlas-v0.1 )
sha256sum "$OUT"/mods/*.jar "$OUT"/TheHollowAtlas-world.zip dist/TheHollowAtlas-v0.1.zip | tee dist/SHA256SUMS.txt
