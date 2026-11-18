#!/usr/bin/env bash
# DEV QA: launches the real client off-screen (Xvfb + Mesa software GL), opens the built world and
# saves screenshots at named anchors.  Usage: tools/client_shots.sh "<autoshot spec>"
set -euo pipefail
cd "$(dirname "$0")/.."
#   WORLD=dev tools/client_shots.sh "..."   uses the developer test world instead of the story world
SPEC="$1"
if [ "${WORLD:-story}" = "dev" ]; then SRC=run-devworld/hollow_atlas_devtest; NAME=hollow_atlas_devtest
elif [ "${WORLD:-story}" = "showcase" ]; then SRC=run-showcase/hollow_atlas_showcase; NAME=hollow_atlas_showcase; else SRC=run-world/hollow_atlas; NAME=hollow_atlas; fi
mkdir -p run/saves run/screenshots
rm -rf "run/saves/$NAME"
cp -r "$SRC" "run/saves/$NAME"
rm -f "run/saves/$NAME/session.lock"
cat > run/options.txt <<OPTS
renderDistance:${RD:-8}
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
  --args="--username HATester --quickPlaySingleplayer $NAME --width 1280 --height 720" \
  -Phollowatlas.autoshot="$SPEC"
