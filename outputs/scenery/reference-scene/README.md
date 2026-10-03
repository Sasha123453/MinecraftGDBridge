# Sunny reference scene

Regenerate from the canonical repository with `node tools/scenery/Build-ReferenceScene.cjs`.
The generator is deterministic and contains original, asset-free geometry. It never writes runtime controls.

This is a short forest course based on the selected art reference, not an XO reconstruction.
`reference-auto-source.json` contains 20 authored native GD objects. The five numbered scenery plans
contain 65,815 requested off-plane cell writes, including cleanup of the new showcase footprint.
Their boxes never touch Z0; front decoration stays below the character's route.

Load the native source into the isolated `GDBridge-Reference` world, import its precise persistent
geometry, then copy/apply numbered plans in order through the native scenery command. Apply
`reference-route-floor.optional.json` when the validator permits grass/dirt terrain floors; it replaces
only full floor materials at Y64–66, preserving the native floor at Y90 GD units. Re-export after
building. World tasks need an unpaused server and must finish before the next command.

The scene uses 22 oaks in two staggered layers, small raised crown tiers, low rear shrubs,
grass/fern/flower detail, a grass lip above exposed dirt, lower front terraces, low stone fragments
and mossed rear relics. Foreground terraces and stones are primarily at Z3–5, visible from the
current Z8 camera, with their caps below the playable icon route. Off-plane decoration extends
to X110 to cover the finish view; all 20 native source objects and the Z0 floor endpoint at X74
remain unchanged. The scenery validator must permit this Reference-only off-plane extension.
Persistent leaf states prevent
decorative crown corners from decaying. Bright morning time is 1800 Minecraft ticks; shader and
camera calibration are separate runtime work.

The native cube-return portal (ID12) does not change the cube AUTO route. An optional cyan pixel
skin changes its presentation only. The yellow orb remains a real interactive object, but the automatic
route does not require clicking it. The optional volumetric specials renderer supplies textures/models
separately; these scene plans contain no game textures.

The generator's manifest records preparation state rather than local runtime results. Fresh MC0.4.8 /
GD0.4.6 playback completed this short world-compiled AUTO course with zero input, deaths or noclip;
239 native frames were captured and selected frames visually reviewed. Actual portal emission was8
and adjacent air received blocklight7. See `docs/reference-scene-playback.json` and
`docs/reference-world-authority.json` for package hashes, scope and remaining limits.
