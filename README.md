# Minecraft × Geometry Dash

Minecraft Java 1.20.1 supplies the world and camera. Geometry Dash 2.2081 / Geode supplies real physics, triggers and progress. Both run concurrently through localhost TCP18471. Default hybrid mode uses Minecraft platforms/spikes and original GD character, trails, portals, orbs and pads. Volumetric special objects remain optional.

## Sources

- outputs/bridge/minecraft: Fabric source/Gradle project.
- outputs/bridge/gd-transition-next: current Geode source.
- outputs: control, import, install, recording and music scripts.
- tools/scenery: authored decoration generators.
- outputs/scenery: decoration plans.
- outputs/shaders: preset text; shader packs downloaded separately.

## This installation

Git project: C:\MinecraftGDBridge. Runtime/toolchains currently remain in the original workspace to keep the existing installation working. The ignored .local.json records its path. Control.ps1 routes background commands to that runtime. No remote is configured and nothing has been pushed.

Build inputs still expect the existing workspace/toolchains. Source migration is complete; portable build/setup consolidation is pending. Do not run copied installation scripts until runtime paths are configured. No game binaries, saves/worlds, downloaded levels, recordings or proprietary texture assets are committed. source-sync.json records copied files and hashes.

## Current limits

Editable markers cover Minecraft X0..512 / Y67..100 / Z0; GD continues beyond this region. Manual QA uses Easy XO58898913; Auto XO58835426 is for visual QA. First20seconds ofAutoXO have passed without inputs/noclip; fullcompletion remains unverified. The selected sunnyforest concept is an art target, not a current screenshot. Native particles/effects and visual quality are incomplete. GD042 addresses native level transition lifetime and signed-scale export; runtime validation determines actual stability.
