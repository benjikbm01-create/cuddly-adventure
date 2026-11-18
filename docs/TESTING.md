# Testing

All results below come from actual runs in the development container. Nothing here is a human playtest.

## 1. Build and unit tests
- `./gradlew build` with pinned dependencies and JDK 21: **passes**.
- `./gradlew test` (`RegionModelTest`, 5 tests: no layout warnings, buildings don't overlap or sit in water, every door opens onto
  free ground at the right height, ground anchors are on land, preview render): **5/5 pass**.

## 2. Headless server
- Dedicated 1.21.1 server with Fabric API loads the mod; content load reports per kind, e.g.
  `npcs=15 ambient=14 quests=9 dialogue=16 cases=2 encounters=2 abilities=2 shops=6 recipes=4 world_changes=1 sites=1 interactions=1`,
  **73 files, 0 problems**.
- `tools/build_world.sh` generates the region in about 50 seconds and packages the world zip.
- `runRenderMap` renders the real generated blocks to `region_map.png` (also used as the Atlas texture).

## 3. Real client, scripted end-to-end (`tools/client_test.sh`)
The Minecraft 1.21.1 client runs off-screen (Xvfb, Mesa llvmpipe, no audio device) with the built world. A dev-only harness
(inert unless `-Dhollowatlas.autotest` is set) sends the same packets a player's clicks send (entity/block interaction,
dialogue choices, key intents) and checks the server's authoritative state. Teleports only skip walking between test points,
and the player is parked out of sight while an area loads, the way a walking player would approach.

Phase 1 plays: arrival, protection (break/place blocked), reaching the village, the innkeeper conversation, room key,
checkpoint, resting, notice board, opening quest completion, journal/satchel/atlas screens, the courier offer, key-item
drop guard, the orchard pump quest (three clues, smith, store purchase, repair with a visible world change, reward paid
once), shop buy/sell/stock/key-item refusal, gathering with cooldown, hearth cooking, a road encounter with heavy/dodge/
guard inputs, learning and using an ability, the ribbon side quest (clues, witness, wrong then revised theory on the case
board, forged theory rejected, finding and returning the item, stall decision changing two residents' routines and the
market stall), full-pack reward queueing and delivery, HUD values matching server state, difficulty change, residents
asleep at night, residents talking to each other, third-person combat input, death and checkpoint recovery.

Phase 2 relaunches the client on the same save and checks persistence of quests, coins, evidence, theories, abilities, key
items, world changes, routine variants, cleared encounters and resident records.

### Latest results
See `run/autotest_results_phase1.txt` / `phase2.txt` after running. Summary of the most recent completed run is in
PROGRESS.md and in the session report.

### Known gaps the script does NOT prove
- **Real melee:** the harness ends fights directly. Enemy telegraph readability, parry timing and damage balance need a person.
- **Performance:** software rendering in a container says nothing about FPS on your PC.
- **Sound:** no audio device in the container.
- Main-story steps after the courier offer and two of the side quests are validated as data only, not played by the script.

## 4. Manual QA checklist for the commissioner (Windows)
1. Launch, open the world: you should arrive on a road in the rain with a journal entry.
2. Try to dig or place a block: refused with a short message.
3. Walk to the village, talk to the innkeeper, take the room, read the notice board.
4. Fight at the old cart track in the south-east woods: watch for raised-arm wind-ups, try a parry (tap V as the hit lands).
5. Press F5 and fight in third person.
6. Quit to title, reopen: quest log, coins and items are as you left them.
7. Change keys in Options > Controls > The Hollow Atlas; bind one onto an existing key and rejoin: a warning appears.
8. Note FPS (F3) in the village at noon and in the woods.
