# XO scene staging and visual QA

This is an XO-only authored chapter sequence. It does not implement the deferred generic GD-to-Minecraft decor generator. Selected forest reference: outputs/Scene-Target-Selected.png. Existing Complementary Reimagined reference/cloudfix preset is unchanged.

## Scope

Original native xo auto 58835426 occupies approximately 1610.5 Minecraft blocks. World mapping is x=GD x/30, y=64+GD y/30. Staged scenery spans X-16..1632; every box has Z strictly negative or positive. No authored route cell, special marker or native collision is edited.

| Chapter | X bounds | Treatment |
|---|---:|---|
| cave | -16..120 | cave |
| library | 121..270 | library |
| nether | 271..383 | nether |
| sunny-forest | 384..640 | forest |
| deep-mines | 641..860 | cave |
| lush-ravine | 861..1040 | ravine |
| nether-fortress | 1041..1190 | nether |
| forest-river | 1191..1440 | forest |
| forest-finale | 1441..1632 | forest |

Indoor chapters use a one-block rear enclosure up to Y151 and a rear roof at Y152, above the highest original true solid top Y146.25. Local lamps and strata/shelves/fortress bays recur at multiple heights. The first cave background begins at X-16, covering the camera-visible area before level start. Rear transition walls X111..131 and263..273 close the earlier sky slits.

Outdoor chapters retain open blue sky. Grass/dirt, two staggered oak layers, low foreground terraces and eight elevated rear island/tree tiers supply depth. Elevated tier selection uses only non-invisible, non-disabled native solid objects, with at least six objects per32-block window aboveY94; portals and Modifier records do not drive it. Static blueprint fields cannot fully resolve dynamic group opacity.

## Application

1. Root backs up the isolated world before applying this larger overlay.
2. Extend the MC scenery validator to the exact bounds in manifest.json. Existing original limits X0..512/Y50..90 are insufficient.
3. Copy only numbered xo-scene-NN.json files into runtime outputs/bridge/levels. MC readSource currently restricts files to that directory.
4. Apply all15 plans sequentially using native scenery control. Wait for actual world-side applied status after each queued ACK. Maximum request is15999cells, within16384 limit.
5. Preserve the current shader preset and normal gameplay physics.

Generator: node tools/scenery/Build-XOScene.cjs, using .local.json runtimeRoot. It never launches games or edits live files. manifest.json gives ordered plans/checksums; operations.json records exact footprints/reasons. restore-old-roofs.json only restores the five older roof footprints; a complete scene rollback requires the saved world backup. The contained decorative river lies entirely behind the route, with stone below and grass walls on all four sides.

## Evidence and limits

Structural QA passed:15plans,184081requested block operations,1257boxes, no Z0 intersection, allowed materials, legal expanded bounds and byte-identical plans on a second generator run. Plans are staged; render verification and live application remain false.

Latest actual MC042/GD043 visual recording1791021730391-a2be0346: samples149..151 and299..301 show readable native portal mode icons with the former diagonal corruption gone; real gray triangular Minecraft spike prisms have consistent face lighting. Cave geometry is substantially clearer than the earlier nearlyblack meshes. The cave/library sky slit still existed in this recording and is what the rear seam fix targets.

That recording was explicitly noclip. Its final sky-only frame is caused by the uncontrolled ship trajectory and is excluded from normal camera/playability conclusions. A separate1.8second normal jump smoke test passed, but full XO playability is not verified. No obvious completely sealed corridor can be established from the few sampled frames; quantized geometry needs normal/native-physics route testing. Do not remove obstacles or add gameplay hacks from a noclip visual observation.
