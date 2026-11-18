# Testing (spoiler-free)

All results below come from actual runs in the development container. **Nothing here is a human playtest.**
Story-specific test notes are kept privately (`narrative/private/`); this page only names test content and systems.

For your own bug-hunting without story spoilers, see **[DEV_TESTING.md](DEV_TESTING.md)**.

## 1. Build and unit tests (`./gradlew test`)
| Suite | Tests | Result |
|---|---|---|
| Region layout (all regions: no warnings, no overlapping/flooded buildings, doors onto ground, anchors on land, map render) | 5 | pass |
| Content graph (stage reachability, every objective producible, every quest startable, dialogue/action references) | 4 | pass |
| Narrative conditions satisfiable (story and test content; story failures reported by opaque id) | 1 | pass |
| Story/test isolation | 1 | pass |
| Spoiler guard (test content, public docs) | 2 | pass |
| Save round trip, legacy migration, corrupt-value clamping | 3 | pass |

The content checks were confirmed to catch real faults by deliberately breaking a test quest (a missing objective
target and an unreachable stage): both were reported, then the file was restored.

## 2. Headless server
- Dedicated 1.21.1 server with Fabric API loads the mod with **0 content problems** in both the story world and the test world.
- The story world and the developer test world are generated from source data by `tools/build_world.sh` and
  `tools/build_devtest_world.sh`.

## 3. Real client, scripted end-to-end
The Minecraft 1.21.1 client runs off-screen (Xvfb, Mesa software rendering, no audio). A dev-only harness (inert unless
`-Dhollowatlas.autotest` is set) sends the same packets a player's clicks send and checks the server's state.
Teleports only skip walking between test points.

### Developer test world (`tools/dev_client_test.sh`), latest run 18 Nov 2026
- **Launch 1: 70 passed, 0 failed.** Mode checks (test world detected, only test residents, no story quest, menu allowed),
  developer menu opens, every item action, full-pack reward queue and delivery, coins, both abilities and cooldown reset,
  spawning both enemy types, heavy attack, refill, clear enemies, difficulty, time, weather, footbridge world change
  on/off, teleport (and refusal for non-test places), all six test quests completed through dialogue, board, reach,
  gather, case board theories (revised), shop, cooking, lever puzzle (wrong then right order, hatch, one-time cache) and
  arena encounter, once-only choice disappearing, checkpoint + death + respawn, reset of test quests and test world
  state, replaying a quest after reset, then starting a branch (option B) and saving mid-quest.
- **Launch 2 (relaunch on the same save): 8 passed, 0 failed.** Coins, mid-quest branch and choice, resident routine
  change, abilities and checkpoint persisted; the moved resident was found at the new place and the quest finished.
- First run had 9 failures, all in the test script itself (two over-strict checks and a misplaced teleport that cascaded);
  fixed and re-run.

### Story world (`tools/client_test.sh`)
Plays the real opening and the main systems and relaunches to check persistence. It also checks that **developer mode
is off** in the story world: the dev menu won't open, developer actions are refused, and no test residents appear
(4/4 pass). Latest full run (18 Nov 2026, this build): **launch 1: 80 passed, 0 failed; relaunch: 10 passed, 0 failed.**
Detailed story results are kept private.

### Known gaps the scripts do NOT prove
- **Real melee feel:** the harness ends fights directly. Enemy wind-up readability, parry timing and damage balance need a person.
- **Performance:** software rendering in a container says nothing about FPS on your PC.
- **Sound:** no audio device in the container.
