# Minecraft × Geometry Dash

MC bridge 0.4.6 (Minecraft 1.20.1 / Fabric) and GD bridge 0.4.6 (GD 2.2081 / Geode) are compiled, installed and verified on simple fixtures. The batch fixes completed-level restart, restores ordinary blocks after the physics ACK and scales obstacle depth proportionally. Package hashes and playback evidence: `docs/geometry-fixture-a-playback.json`.

The current milestone is exact native GD geometry → stored Minecraft pieces → F6 native physics, using small original fixtures before returning to XO. Minecraft server blocks/piece data define edited obstacles; GD supplies actual movement and collisions.
Original GD avatar, trails/wave, portals, orbs and pads remain the default. Special-object volume is optional; compound editor cells preserve multiple source pieces instead of merging them into a full cube.
Art target: `outputs/Scene-Target-Selected.png` — nearby side camera, readable native icon, warm light and layered foreground/background with real occlusion. It is a concept, not gameplay evidence.

Canonical sources: `outputs/bridge/minecraft` and `outputs/bridge/gd-world-authority-next`. Ignored `.local.json` maps the separate runtime/toolchains; `outputs/Runtime-Paths.ps1` resolves them.
Build: `.\Build-Bridge.ps1 -Target minecraft` or `-Target gd`. Build, installation and live verification are separate steps.
Control: `.\outputs\Control-Bridge.ps1 -Action <action> -PayloadJson '<json>'`. F7 edits; F6 scans/compiles the world. Use explicit `-Action resume-world` to close only the Minecraft pause menu and allow queued server work.
Native framebuffer recording uses real timestamps and no desktop capture/focus/input. A command ACK confirms receipt, not completion. Run `outputs/Verify-GeometryFixture.ps1` with `-FixturePath` and an optional `-OutputPath docs/geometry-fixture-a.json` after fresh exports.

- Fixture A: all 12 source pieces roundtripped, including transforms and available visual-quad/hitbox metadata; zero mismatches. Evidence: `docs/geometry-fixture-a.json`.
- Fixture B: all 11 pieces roundtripped, including fractional/half/quarter scale, 45°/90° rotations, flips and two distinct pieces in cell (17,71,0); zero geometry-metadata mismatches. Evidence: `docs/geometry-fixture-b.json`.
- Fixture A completed normally with zero deaths, no input and noclip off in a 12-second recording (239 frames, 20 FPS). Sampled visual QA found the avatar visible through completion and no duplicate faces or flicker in reviewed neighboring frames; native texture-load errors: zero. Evidence: `docs/geometry-fixture-a-playback.json`.
- Moving compound cell (17,71,0) to (18,71,0) shifted both quarter-scale native colliders by 30 units and removed their old positions; restoring the cell passed fixture B again. Evidence: `docs/compound-world-move.json`.
- Fixture C: all 5 separated special/reference pieces roundtripped with exact transforms and available geometry metadata; zero mismatches (`docs/geometry-fixture-c.json`). Slopes, moving geometry, effects and broader shape/trigger behavior remain untested; a universal exact importer is not established.
- Historical XO position checks: `docs/world-authority-full.json` matched 11,378 cells; `docs/world-authority-move.json` verified a 2,714-cell export and one block move. Neither proves unchanged XO geometry or full normal XO completion.
Native implicit floor/ceiling authority remains unresolved. Vanilla stairs/slabs are unsupported; native forgiving spike hitboxes must not be enlarged to their visual bounds. The 15 full-scene plans are prepared, not applied/visually verified.
Keep game binaries, licensed assets, downloaded levels, worlds, toolchains, compiled packages, recordings, secrets and local settings out of Git. See `AGENTS.md`; fixture geometry is original and asset-free.
