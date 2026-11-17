# Dependencies

All versions are pinned in `gradle.properties` and were verified to exist on maven.fabricmc.net.

| Dependency | Version | Scope | License |
|---|---|---|---|
| Minecraft | 1.21.1 | game | Mojang EULA |
| Fabric Loom | 1.11.8 | build plugin | MIT |
| Gradle | 8.14.3 (wrapper, SHA-256 pinned) | build | Apache-2.0 |
| Yarn mappings | 1.21.1+build.3 | build | CC0-1.0 |
| Fabric Loader | 0.16.14 (runtime requires >= 0.16.0) | runtime | Apache-2.0 |
| Fabric API | 0.116.6+1.21.1 | runtime, required | Apache-2.0 |
| JUnit Jupiter | 5.10.2 | tests | EPL-2.0 |

## Optional third-party mods

None bundled. Any optional mod (sound, wildlife, performance) will only be listed here after its
real Fabric 1.21.1 release, dependencies and license have been checked. The game must start
without them.
