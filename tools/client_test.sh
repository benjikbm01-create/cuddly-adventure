#!/usr/bin/env bash
# DEV QA: end-to-end client test (Xvfb + Mesa software GL).
#   tools/client_test.sh                       full run: phase1 (play) + phase2 (relaunch, verify persistence)
#   ONLY=combat,ribbon tools/client_test.sh    only those sections (+ the always-run opening), phase1 only
#   PHASES="phase1" SCALE=0.5 tools/client_test.sh
# Sections: courier pump shop gather combat ribbon systems residents death evening
# Results: run/autotest_results_<phase>.txt
set -uo pipefail
cd "$(dirname "$0")/.."
ONLY="${ONLY:-}"
SCALE="${SCALE:-0.65}"
if [ -n "$ONLY" ]; then PHASES="${PHASES:-phase1}"; else PHASES="${PHASES:-phase1 phase2}"; fi
mkdir -p run/saves
rm -rf run/saves/hollow_atlas run/autotest_*.txt
cp -r run-world/hollow_atlas run/saves/hollow_atlas
rm -f run/saves/hollow_atlas/session.lock
export LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe
for phase in $PHASES; do
  # daemon kept warm between launches (faster than --no-daemon)
  xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew runClient -q -Dorg.gradle.jvmargs=-Xmx3G \
    --args="--username HATester --quickPlaySingleplayer hollow_atlas --width 1280 --height 720" \
    -Phollowatlas.autotest=$phase -Phollowatlas.autotest.only="$ONLY" -Phollowatlas.autotest.scale="$SCALE"
  echo "---- $phase"; cat run/autotest_results_$phase.txt 2>/dev/null || echo "no results file"
done
