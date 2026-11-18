#!/usr/bin/env bash
# DEV QA: launches the real client off-screen (Xvfb + Mesa software GL), opens the built world and
# saves screenshots at named anchors.  Usage: tools/client_shots.sh "<autoshot spec>"
set -euo pipefail
cd "$(dirname "$0")/.."
SPEC="$1"
mkdir -p run/saves run/screenshots
rm -rf run/saves/hollow_atlas
cp -r run-world/hollow_atlas run/saves/hollow_atlas
rm -f run/saves/hollow_atlas/session.lock
cat > run/options.txt <<'OPTS'
renderDistance:8
simulationDistance:6
maxFps:30
graphicsMode:0
renderClouds:"false"
particles:2
entityShadows:false
fov:0.25
guiScale:2
pauseOnLostFocus:false
onboardAccessibility:false
tutorialStep:none
skipMultiplayerWarning:true
narrator:0
soundCategory_master:0.0
OPTS
export LIBGL_ALWAYS_SOFTWARE=1
export GALLIUM_DRIVER=llvmpipe
xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew runClient --no-daemon \
  -Dorg.gradle.jvmargs=-Xmx3G \
  --args="--quickPlaySingleplayer hollow_atlas --width 1280 --height 720" \
  -Phollowatlas.autoshot="$SPEC"
