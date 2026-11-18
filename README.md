# The Hollow Atlas

An original open-world fantasy action RPG built as a Fabric mod for **Minecraft Java Edition 1.21.1**.

> **Spoiler warning:** `narrative/private/`, `tools/content_src/` and `src/main/resources/data/hollowatlas/`
> (except `*/devtest/`) contain the story and quest scripts. Public docs in `docs/` are spoiler-free. Content gating in-game is a gameplay feature, not encryption.

## Play it

See **[QUICKSTART.txt](QUICKSTART.txt)** (Windows steps). In short: put `hollowatlas-0.1.2.jar` and Fabric API
into `.minecraft/mods`, unzip `TheHollowAtlas-world.zip` into `.minecraft/saves`, launch your Fabric 1.21.1 profile.

## Status

v0.1.2 (v0.1 plus fixes and the developer test world). See [docs/PROGRESS.md](docs/PROGRESS.md) (spoiler-free status per milestone) and
[docs/TESTING.md](docs/TESTING.md) (what was actually run and its results).

## Requirements

| Component | Version |
|---|---|
| Minecraft Java Edition | 1.21.1 |
| Fabric Loader | 0.16.0 or newer (built against 0.16.14) |
| Fabric API | 0.116.6+1.21.1 (bundled in the release folder) |
| Java | 21 (bundled with the Minecraft launcher) |

## Building from source

```bash
./gradlew build                 # mod JAR -> build/libs/hollowatlas-<version>.jar
./tools/build_world.sh          # regenerate the playable world -> dist/TheHollowAtlas-world.zip
./tools/package_release.sh      # assemble dist/TheHollowAtlas-v<version>/ (+ zip and SHA256SUMS)
./gradlew test                  # content-graph, conditions, isolation, spoiler guard, saves, region layout
```

Windows: use `gradlew.bat build`. World generation needs bash (Git Bash or WSL) or run `gradlew runBuildWorld`
after creating `run-world/server.properties` as in `tools/build_world.sh`.

## Testing without story spoilers

A separate **developer test world** (`hollow_atlas_devtest`) uses the real game systems with fictional test content only.
Press F7 there for the Developer Menu. Guide and checklist: **[docs/DEV_TESTING.md](docs/DEV_TESTING.md)**.

Developer QA (Linux, needs Xvfb + Mesa): `tools/dev_client_test.sh` plays the test world end-to-end in the real client;
`tools/client_test.sh` does the same for the story world; `tools/build_devtest_world.sh` builds the test world zip.

## Developer commands (operators only, `/atlas ...`)

`build`, `status`, `builder on|off`, `tp <anchor>`, `where`, `anchors <prefix>`, `rendermap`, `validate`, `profile`,
`npc list|info|profile|variant`, `quest list|reset_sandbox`, `difficulty`. None of them completes a story quest.

## Docs

[ARCHITECTURE](docs/ARCHITECTURE.md) · [GAME_DESIGN](docs/GAME_DESIGN.md) · [ROADMAP](docs/ROADMAP.md) ·
[DEPENDENCIES](docs/DEPENDENCIES.md) · [PROGRESS](docs/PROGRESS.md) · [TESTING](docs/TESTING.md) · [DEV_TESTING](docs/DEV_TESTING.md)
