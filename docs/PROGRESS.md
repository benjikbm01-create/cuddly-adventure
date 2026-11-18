# Progress (spoiler-free)

Status legend
- **BUILD-PASSING**: compiles; unit tests pass; content loads with 0 problems in a headless server.
- **CLIENT-AUTOTESTED**: exercised in the real Minecraft 1.21.1 client (software-rendered, off-screen) by the
  scripted end-to-end test, which drives real interaction packets and checks server state. This is a launched client,
  but it is **not** a human playtest.
- **IN-GAME-VERIFIED**: played by a person. **Nothing is marked this yet**; it needs your playtest.
- **NOT VERIFIED**: implemented but no automated or manual check exercised it.

| Milestone | Status | Notes |
|---|---|---|
| M0 Environment + build | BUILD-PASSING | Pinned toolchain, checksum-verified wrapper, server smoke launch. |
| M1 Starting region + protection | CLIENT-AUTOTESTED | ~750x750 region generated from source data; village with 17 enterable buildings, farms, orchard, river, gorge, woods, hill, cave, 5+ discoverables, road out, soft boundary. Break/place blocked in client test. |
| M2 Living residents | CLIENT-AUTOTESTED | 15 residents with full schedules. Client test: residents present in the village, asleep in their beds at night, talk to each other, routines change after a quest, records persist across relaunch. |
| M3 Dialogue, quests, journal, investigation | CLIENT-AUTOTESTED | Opening quest, the pump quest, the courier offer and the ribbon side quest (clues, witness, case board theories, persistent stall change) run end-to-end; journal/case board screens render. Later story steps and the other three side quests: content validated only, NOT played by the script. |
| M4 Combat + abilities | CLIENT-AUTOTESTED (inputs only) | Encounter spawns, heavy/dodge/guard intents change server state, ability cooldowns, death/checkpoint. The harness ends fights directly; **real melee feel, parry timing and enemy telegraphs need a human playtest.** |
| M5 Inventory, hotbar, shop, crafting, gathering | CLIENT-AUTOTESTED | Satchel renders real inventory; buy/sell/stock validation; key items can't be sold/dropped; gathering with cooldown; hearth cooking; reward overflow queue. |
| M6 Integration, save/reload, packaging | CLIENT-AUTOTESTED, PACKAGED | Two-launch test: 10/10 persistence checks. Release: `release/TheHollowAtlas-v0.1.zip` (mod + Fabric API + world + QUICKSTART). Awaiting your playtest. |

## Known limitations (v0.1)
- World art is procedurally assembled from authored data: coherent, but less detailed than a hand-built map. Interiors are simple.
- Textures are vanilla (no 32x pack was supplied). Item icons reuse vanilla art.
- No music/ambient-audio additions; no optional third-party mods integrated.
- Residents walk with vanilla pathfinding; on rare blocked paths they wait or re-plan (they only relocate when unobserved).
- Enemy animations are simple pose changes on the player model.
- Atlas map is a static survey image of the starting region.
- Multiplayer is not a v0.1 target (state is per-player, but untested with several players).
- Story files ship inside the mod jar; anyone who unpacks it can read them.

## Environment notes
Developed in a Linux cloud container without a display. The client was run under Xvfb with Mesa software rendering
(no sound device), which is enough for scripted tests and screenshots but says nothing about performance on real hardware.
