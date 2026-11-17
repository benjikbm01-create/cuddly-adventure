# The Hollow Atlas

An original open-world fantasy action RPG built as a Fabric mod for **Minecraft Java Edition 1.21.1**.

> **Spoiler warning:** `narrative/private/` contains the private story bible. Players
> who want to stay unspoiled should not open that folder or browse `src/main/resources/data/hollowatlas/`
> (quest and dialogue scripts). Content gating in-game is a gameplay feature, not encryption.

## Status

v0.1 is in development. See [docs/PROGRESS.md](docs/PROGRESS.md) for milestone status
(spoiler-free) and [docs/TESTING.md](docs/TESTING.md) for what has actually been verified.

## Requirements

| Component | Version |
|---|---|
| Minecraft Java Edition | 1.21.1 |
| Fabric Loader | 0.16.0 or newer (built against 0.16.14) |
| Fabric API | 0.116.6+1.21.1 or newer for 1.21.1 |
| Java | 21 (the Minecraft launcher bundles this) |

## Building from source

```bash
./gradlew build          # Linux / macOS
gradlew.bat build        # Windows
```

The mod JAR is written to `build/libs/hollowatlas-0.1.0.jar`
(ignore the `-sources.jar`). The Gradle wrapper downloads Gradle 8.14.3 and verifies its SHA-256.

Dev runs:

```bash
./gradlew runClient      # launches a dev client
./gradlew runServer      # launches a dev dedicated server in ./run
./gradlew test           # unit tests
```

## Installing (Windows)

See [QUICKSTART.txt](QUICKSTART.txt) once a release build is packaged.
