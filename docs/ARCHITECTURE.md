# Architecture

Server-authoritative Fabric mod (`hollowatlas`), Minecraft 1.21.1, Yarn mappings, split client/common source sets.

## Data ownership

| State | Owner | Persistence |
|---|---|---|
| Quests, flags, evidence, theories, coins, abilities, cooldowns, checkpoint, settings | `state.PlayerProfile` (per player) inside `state.GameState` | `<world>/data/hollowatlas_state.dat` (vanilla PersistentState, autosaves with the world) |
| World flags (repairs, reopened places, puzzle states...), resident records, gathering cooldowns, shop stock, difficulty | `state.GameState` | same file |
| Items | vanilla player inventory (the satchel is a view, not a second inventory) | vanilla player data |
| Terrain/buildings | the world save, generated from `data/hollowatlas/region/*.json` | region files |

`GameState` is versioned (`VERSION`); `StateMigration` upgrades old data, `StateBackup` copies the file aside before any migration.
Client screens never mutate state: they send intents over `net.Net` (one C2S and one S2C payload carrying a kind + JSON body) and every handler re-validates on the server (session ids, proximity, stock, funds, ownership).

## Modules

| Package | Responsibility |
|---|---|
| `world` | `RegionLayout` (map source), `RegionModel` (deterministic terrain + anchors), `BuildingPlanner`/`FeaturePlanner` (pure block plans), `RegionBuilder` (staged in-world generation), `LocationIndex` (named anchors), `ProtectionService`, `BoundaryService`, `WorldRules`, `WorldChanges` (flag-driven persistent mutations), `SiteService` (Atlas discovery), `MapRenderer` |
| `content` | `ContentManager`: data-pack loader for every content kind, per-file error report, cross-reference validators |
| `npc` | `NpcDefinition` (data), `ScheduleService` (pure), `NpcDirector` (tiered simulation, rate-limited path planning, abstract progression, barks), `NpcBrain` (per-entity behaviour + stuck recovery), `NpcEntity` (disposable body), `AmbientChatter` |
| `dialogue` | `DialogueService`: data trees, conditional nodes/choices, server sessions with sequence numbers |
| `quest` | `QuestDef` (data), `QuestEngine` (events + polling, branching, idempotent completion, journal sync) |
| `investigation` | `CaseService` (fact/testimony/rumor evidence, revisable theories), `Interactions` (designated block interactions: inspect, gather, puzzle, station, cache, rest) |
| `logic` | `Logic`: shared condition predicates and action effects used by all data |
| `combat` | `CombatService` (stamina, heavy, dodge, guard/parry/counter, guard break, difficulty scaling, death/checkpoint), `EnemyEntity` (telegraphed patterns), `EncounterService` (curated encounters) |
| `ability` | `AbilityService` (data registry + executors, stamina/focus pools, cooldowns, hotbar tokens) |
| `inventory` | `ModItems`, `ItemCategory`, `RewardService` (the only item/currency source; overflow queue), `ShopService`, `CraftingService`, `InventoryActions`, `HudSync` |
| `client` | key bindings, HUD, screens (dialogue, journal/case board/settings, satchel, shop, craft, atlas), renderers, dev harnesses (`client.dev`, inert unless enabled by system property) |

## Content (data packs)

`src/main/resources/data/hollowatlas/<kind>/*.json` where kind is one of
`region, npcs, ambient, dialogue, quests, interactions, cases, shops, recipes, encounters, world_changes, sites, abilities`.
Authoring sources that generate these live in `tools/content_src/` (story, spoilers) and `tools/devtest_src/` (developer test
content, spoiler-free; output under `<kind>/devtest/`). `/atlas validate` reports problems.

## World modes

A world is either a story world or the developer test world (`state.WorldMode`, world flag `hollowatlas:mode_devtest`).
Content files under a `devtest/` folder are tagged as test content; every service (residents, ambient talk, quests,
encounters, interactions, world changes, sites, case board) only runs content of the current world's mode. The developer
menu (`client.ui.DevMenuScreen`, F7) and `/devtest` (`devtest.DevTestService`) refuse to act outside the test world.

## Performance design

- Residents: live entities only within ~96-128 blocks of a player; others advance abstractly by schedule. New path searches are
  capped at 3 per tick globally; schedules re-evaluate once per second; stuck residents re-plan, then relocate only when unobserved.
- No per-tick world scans: interactions are a position map, quest polling runs once per second, encounters once per second.
- `/atlas profile` and `/atlas npc profile` show live/abstract counts, path searches per second and recoveries.

## Extending to the full map

The region's `origin` places it in the eventual 8,000 x 8,000 world. New regions add another layout file; all gameplay refers
to anchors by name, never raw coordinates.
