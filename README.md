# Minecraft × Geometry Dash

Actual Minecraft world blocks define the playable obstacles. Geometry Dash supplies native physics, input, triggers, icons and original portals/orbs/pads. Both games run concurrently over localhost TCP18471.

Current versions: Minecraft1.20.1/Fabric bridge0.4.2 and GeometryDash2.2081/Geode bridge0.4.3.

## Develop here

Project: C:/MinecraftGDBridge. Current sources are outputs/bridge/minecraft and outputs/bridge/gd-world-authority-next. Previous transition-stage sources remain as a historical snapshot; use the world-authority stage. Runtime/toolchains remain in the original workspace while build paths are consolidated. The ignored .local.json points to that runtime. Control.ps1 sends background native commands. No remote is configured and nothing has been pushed.

- outputs/README.txt: Russian usage and current verified limitations.
- outputs/scenery: generated decoration plans.
- tools/scenery: scene generators.
- outputs/shaders: preset configuration, not shader binaries.
- outputs/Scene-Target-Selected.png: selected art reference.
- docs/world-authority-move.json: actual Minecraft-block to native-GD collider verification.

## Build and play

F7 enters Minecraft Creative. Place actual full-cube blocks and stone-spike blocks in the gameplay layer. F6 reads server block states, compiles native GD collision objects, then reveals the real Minecraft geometry after GD acknowledges the new scene. Original special sprites bind to marker cells; native GD physics remain authoritative during playback.

First scope: X0..512, Y67..100, Z0. Ordinary full cubes become native GDID1 at exact cell centers; the real gdbridge:stone_spike becomes GDID8. Outside the first region original GD continuation is retained. Stairs/slabs are not supported yet. Gold/glass/etc. are currently reserved special markers; dedicated marker blocks are planned.

The tested conversion of EasyXO58898913 contains2714cells (1648solids/832spikes/234specials). All native object centers matched. Moving a real stone block from(29,72,0) to(30,72,0) moved its native collider by30GDunits and removed the old collider. The block was restored. Voxel conversion changes the course; normal full completion is not verified. Noclip recordings are visual QA only. Original level files and worlds are backed up.

Compilation passed. The earlier level-transition crash was fixed with two-phase oldscene retirement before newPlayLayer creation, and runtime transitions have passed. Visual quality and particle/effect coverage are still being improved.

Game binaries, saves/worlds, downloaded levels, toolchains, textures, compiled packages and videos are excluded from Git. See source-sync.json for source hashes.
