#!/usr/bin/env bash
# DEV QA: end-to-end client test in two launches (play, then relaunch to verify persistence).
# Uses Xvfb + Mesa software GL. Results: run/autotest_results_phase1.txt / _phase2.txt
set -uo pipefail
cd "$(dirname "$0")/.."
mkdir -p run/saves
rm -rf run/saves/hollow_atlas run/autotest_*.txt
cp -r run-world/hollow_atlas run/saves/hollow_atlas
rm -f run/saves/hollow_atlas/session.lock
[ -f run/options.txt ] || ./tools/client_shots.sh "" >/dev/null 2>&1 || true
export LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe
for phase in phase1 phase2; do
  xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew runClient --no-daemon -Dorg.gradle.jvmargs=-Xmx3G \
    --args="--username HATester --quickPlaySingleplayer hollow_atlas --width 1280 --height 720" -Phollowatlas.autotest=$phase
  echo "---- $phase"; cat run/autotest_results_$phase.txt 2>/dev/null || echo "no results file"
done
