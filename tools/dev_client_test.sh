#!/usr/bin/env bash
# DEV QA: end-to-end client test of the DEVELOPER TEST WORLD (spoiler-free; no story content involved).
#   tools/dev_client_test.sh            dev1 (exercise dev menu + every test quest) then dev2 (relaunch, verify the save)
# Needs run-devworld/hollow_atlas_devtest (tools/build_devtest_world.sh). Results: run/autotest_results_dev*.txt
set -uo pipefail
cd "$(dirname "$0")/.."
SCALE="${SCALE:-0.65}"
PHASES="${PHASES:-dev1 dev2}"
mkdir -p run/saves
rm -rf run/saves/hollow_atlas_devtest run/autotest_results_dev*.txt run/autotest_dev_expect.txt
cp -r run-devworld/hollow_atlas_devtest run/saves/hollow_atlas_devtest
rm -f run/saves/hollow_atlas_devtest/session.lock
export LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe
for phase in $PHASES; do
  xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew runClient -q -Dorg.gradle.jvmargs=-Xmx3G \
    --args="--username HATester --quickPlaySingleplayer hollow_atlas_devtest --width 1280 --height 720" \
    -Phollowatlas.autotest=$phase -Phollowatlas.autotest.scale="$SCALE"
  echo "---- $phase"; cat run/autotest_results_$phase.txt 2>/dev/null || echo "no results file"
done
