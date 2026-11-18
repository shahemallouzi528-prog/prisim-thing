# Prism Engine (phase 1: foundation)

Fabric client mod for Minecraft 26.2 combining visual enhancements with an adaptive performance controller,
targeting low-end hardware (i3-1315U, UHD graphics, 8 GB RAM).

## Honest status

| Part | State |
|---|---|
| Config (profiles Potato/Balanced/Quality/Ultra, per-feature toggle + level 0-3, custom FPS target) | Written, **unit-tested** |
| Frame statistics (avg, 1% low, percentiles, graph samples) | Written, **unit-tested** |
| Adaptive controller (degrade costly effects under target FPS, restore with headroom, hysteresis, floor) | Written, **unit-tested** |
| Fabric entrypoint (load config, feed controller each client tick) | Written, **never compiled** against 26.2 |
| Visual effects (lighting, fog, water, leaves, clouds, particles, weather...) | **Not implemented** |
| Original render/particle/entity culling | **Not implemented** (config switches exist) |
| Config screen with graphs | **Not implemented** |
| Sodium / Iris compatibility testing | **Not done** |
| Compiled JAR | **Not produced** |

The 90 core checks were compiled with `javac -Xlint:all` (JDK 21, zero warnings) and passed.
Nothing that touches Minecraft was compiled or run: the build sandbox could not reach Fabric's Maven and has no Java 25.

## Build

Requires JDK 25 and Gradle 9.5.1 (or run the included GitHub Actions workflow).

    gradle wrapper --gradle-version 9.5.1
    ./gradlew build
    # JAR: build/libs/prism-engine-0.1.0.jar

Run only the core tests: `./gradlew coreTest`.

Before building, confirm the versions in `gradle.properties` at https://fabricmc.net/develop (they came from third-party pages).

## Install

Put Fabric Loader (0.19.5+) for 26.2, Fabric API, and the JAR in `.minecraft/mods`.

## Verifying Minecraft 26.2 APIs

26.x uses Mojang names with no remapping. `PrismClient` calls `client.getFps()`, which is unverified. If the build fails
there, inspect the real class:

    javap -p -cp <deobfuscated minecraft jar from ~/.gradle/caches/fabric-loom> net.minecraft.client.Minecraft | grep -i fps

Rendering hooks, GUI classes and mixin targets must be checked the same way before they are written.

## Next phases

1. Get CI green; fix any API mismatch in the entrypoint.
2. Frame-time overlay and config screen (after verifying the GUI/HUD API names in 26.2).
3. Effects in cost order, each behind its toggle, tested on UHD graphics with and without Sodium/Iris.
