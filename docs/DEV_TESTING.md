# Developer Testing Mode (spoiler-free)

Test every gameplay system **without playing or seeing the real story**.

Everything in this mode is noncanonical: a separate world ("Proving Grounds"), five fictional test residents, six test
quests, a test shop, a test arena and a test puzzle. It runs on **the same game code** as the real game (same quest engine,
dialogue, combat, shops, crafting, saving); only the content is different.

## Why it can't touch your real save

| Guarantee | How |
|---|---|
| Separate save | The test world's folder is `hollow_atlas_devtest`; the real one is `hollow_atlas`. Minecraft keeps saves per folder. |
| Story content is switched off in the test world | Story residents, quests, encounters, places and world changes don't load there (and test content doesn't load in the story world). Checked by automated tests. |
| Dev tools are switched off in the real game | The F7 menu and `/devtest` only work in the test world. In the story world they do nothing. Checked by the automated client test. |
| Nothing in the test world names story things | An automated spoiler check fails the build if test content or these public docs use story names. |

## Install the test world (Windows)

1. Install the mod as in `QUICKSTART.txt` (same jar; nothing else to install).
2. Unzip `TheHollowAtlas-DevTest-world.zip` into `%APPDATA%\.minecraft\saves`, so you get
   `saves\hollow_atlas_devtest\level.dat`.
3. Start Minecraft (Fabric 1.21.1) > Singleplayer > **hollow_atlas_devtest**.

You start in the test square with 100 coins. Press **F7** for the Developer Menu.
To start over completely, delete the `hollow_atlas_devtest` folder and unzip it again. Your story save isn't affected.

## The Developer Menu (F7)

| Tab | Buttons |
|---|---|
| Combat | spawn brigand / slinger / both, clear enemies, refill health+stamina+focus, difficulty Story/Normal/Hard/Expert |
| Items | weapons, armor, food, crafting materials, test key item, fill pack (to test full-pack rewards), clear pack, +50 coins, reset shop stock |
| Abilities | unlock Windstep, unlock Echo Sight, reset cooldowns |
| World | morning / noon / evening / night, next day, rain / clear, toggle footbridge (world change), reset gathering spots |
| Quests | start each test quest, reset all test quests (also resets test evidence, test world changes, test routines) |
| Travel | teleport to any test location |
| Saves | set checkpoint here, test death, save now |

The same actions work as chat commands: `/devtest <action> [arg]` (for example `/devtest tp dt_arena.center`,
`/devtest coins 200`, `/devtest start_quest dtq_branch`). Type `/devtest` and press Tab to see them.

## Test residents

| Name | Role | What to test with them |
|---|---|---|
| Alda Quill | Test Clerk, by the square (west) | Starts every test quest; once-only and locked dialogue choices |
| Bertram Ode | Test Merchant, test store | Shop buy/sell/stock; relationship-gated line; evening greeting (time condition) |
| Kade Halvard | Drill Sergeant, patrols the square | Teaches Windstep; rain greeting (weather condition) |
| Juno Pike | Test Farmer | Rain routine; routine change from the branch quest |
| Remy Tallow | Test Innkeeper, test hall | Sleeps upstairs at night; errand step |

## Test quests

| Quest | What it exercises |
|---|---|
| Errand Chain | talk, read a board, reach a place, gather 2 items, return for a reward |
| Branching Choice | a dialogue choice with two outcomes: A builds a footbridge (world change), B moves a resident's routine. Must persist after reload. |
| Case Board | fact, testimony and rumor evidence; picking and revising a theory |
| Arena Trial | an encounter with one melee and one ranged enemy; kill objectives |
| Cooking | gathering, buying, cooking at a station |
| Lever Puzzle | wrong order resets, right order opens a hatch, one-time cache |

## Bug-hunting checklist

Tick each line. If something goes wrong, note **what you did, what you expected, what happened**, and the time of day
in game. A screenshot (F2) helps; screenshots are in `%APPDATA%\.minecraft\screenshots`. Crash reports are in
`%APPDATA%\.minecraft\crash-reports`.

### A. Start-up and safety
- [ ] The game starts with the mod and Fabric API, no crash on the title screen.
- [ ] `hollow_atlas_devtest` opens; you're in the square with 100 coins and the HUD (coins, stamina) shows.
- [ ] F7 opens the Developer Menu. Every tab opens.
- [ ] Open your **story** world for a moment: F7 does nothing there, and `/devtest coins 50` is refused. Leave right away.
- [ ] Try to break or place a block in the test world: refused.

### B. Combat (Combat tab, near the arena)
- [ ] Spawn a brigand. Watch for its wind-up before each swing.
- [ ] Light attack (left click), heavy attack (R) uses stamina, dodge (Left Alt) avoids a hit.
- [ ] Hold V to guard; tap V right as a hit lands to parry, then counter.
- [ ] Spawn a slinger: it keeps distance and throws; you can close in or dodge.
- [ ] Switch difficulty Story to Expert: damage taken changes noticeably.
- [ ] Press F5 and fight in third person.
- [ ] "Clear enemies" removes them.

### C. Abilities
- [ ] Unlock Windstep and Echo Sight; Z and X use them; cooldown shows on the HUD; spamming doesn't bypass it.
- [ ] In a fresh copy of the test world, learn Windstep from Kade by talking to him instead of using the menu.
      ("Reset test quests" doesn't remove learned abilities; that's intended.)

### D. Inventory, shop, crafting
- [ ] I opens the satchel; items are in the right categories.
- [ ] Give weapons/armor/food: they show up; armor can be worn.
- [ ] Bertram > Trade: buy, sell, stock runs out, "reset shop stock" refills it.
- [ ] Try selling or dropping the Test Token (key item): refused.
- [ ] "Fill pack", then finish a test quest: the reward waits and arrives once you clear space.
- [ ] Cooking quest: gather, buy, cook at the hall hearth.

### E. Residents and world
- [ ] Morning / noon / evening / night: residents go to work, eat, socialise, sleep in beds.
- [ ] Two residents chat to each other in the evening (Alda + Juno near their house, Bertram + Kade in the square).
- [ ] Rain: Juno and Alda move indoors.
- [ ] Talk to a sleeping resident: you get the "asleep" reply, no crash.
- [ ] Toggle footbridge: the bridge over the brook appears and disappears.
- [ ] Gathering spots: pick a flower, it's empty, it regrows after ~2 minutes (or "reset gathering spots").
- [ ] Doors, beds, stairs work; residents walk around without getting stuck for long.
- [ ] M opens the Atlas with the test map and discovered places.

### F. Dialogue and quests
- [ ] Each test quest can be started from Alda and from the Quests tab.
- [ ] J opens the journal: active quest, objectives, markers; switching the tracked quest works.
- [ ] Dialogue: text types out; Space skips; keys 1-9 pick choices; once-only choice disappears after use; locked
      choice shows why it's locked.
- [ ] Every test quest can be finished. Rewards are paid once.
- [ ] Case Board: evidence shows its kind (fact/testimony/rumor); you can pick a theory and change it.
- [ ] "Reset test quests": all test quests can be started again.

### G. Saving, loading, checkpoints
- [ ] Start Branching Choice, pick an option, **Save and Quit to Title**, reopen: the choice, the bridge or Juno's new
      routine, quest stage, coins, items and abilities are all still there.
- [ ] Set checkpoint here, walk away, Test death: you respawn at the checkpoint, keeping your gear (a few coins lost).
- [ ] Quit in the middle of a fight, reopen: no stuck enemies, no crash.
- [ ] "Save now", then close Minecraft with the window X: progress up to the save is kept.

### H. Settings and controls
- [ ] Journal > Settings: difficulty, HUD, markers, dialogue speed/size apply immediately.
- [ ] Options > Controls > The Hollow Atlas: rebind a key; binding onto an existing key gives a warning on join.
- [ ] Check FPS (F3) in the square at noon.

## Automated tests (run by the developer, no story spoilers in output)

| Suite | What it proves |
|---|---|
| Content graph | every quest stage reachable, every objective has something in the world that can complete it, every quest can be started, dialogue links valid |
| Narrative conditions | every condition used by dialogue, quests and the world can actually become true (story failures are reported by opaque id only) |
| Isolation | test content never references story content and the other way round |
| Spoiler guard | test content and public docs contain no story names |
| Save round trip | save > load > save is identical; old saves migrate; corrupt values are clamped instead of crashing |
| Region layout | no overlapping buildings, doors open onto ground, anchors on land (all regions) |
| Dev world client test | real client in the test world: every dev menu category, all six test quests, checkpoint/death, reset, then a relaunch to check the save |
| Story world client test | the real game plays its opening and systems, and confirms dev tools are switched off there (results kept private) |

Commands (Linux): `./gradlew test`, `tools/dev_client_test.sh`, `tools/client_test.sh`.
