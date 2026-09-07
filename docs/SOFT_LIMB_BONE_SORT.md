# Soft Limb Bone Sort Plan

Plan to improve soft-vs-soft limb transparency by submitting **one post-deferred draw per soft bone** and letting the existing queue sort them back-to-front. This is the practical follow-up to the open item in `[LIMB_TRANSPARENCY.md](LIMB_TRANSPARENCY.md)` (*Soft limb behind soft limb, same actor*).

**Status:** implemented (v1 — `limbOnlySoft` per-bone submit + path-specific depth keys).

**Non-goals for this pass:** per-triangle sorting, OIT, addon-based triangle reorder (ruled out as too heavy / fragile with Iris).

---

## Implementation notes (v1 shipped)

- `ModelFormRenderer` `limbOnlySoft`: `collectSoftDrawableBones` → capture matrices → one draw per bone → `applyOnlySoftBoneVisible`.
- **World model blocks (`MODEL_BLOCK`):** post-deferred + `softBoneDistanceSq` (lengthSq on draw root).
- **Film / world entities (`ENTITY`):** post-deferred + `softBoneWorldDepthKey` = `(boneWorld − camera) · look` in `renderContext.world` space.
- **Multi soft bones:** color pass with depth **write off** (painter farther→nearer), then a **depth-only stamp** (color mask off). Keeps Iris fog/paint occlusion without erasing the far soft limb when film sort is imperfect. Single soft bone still color-writes depth in one pass.
- **Soft draws:** only on soft form/limb paths. With Iris: Settings → **Soft transparency backfaces** (default ON). Without shaders: model `culling` property.
- **UI / form / model-block edit preview (`localPreview`):** immediate sorted draws with lengthSq on the live stack.
- Soft must stay post-deferred in world/film (immediate soft in `AFTER_ENTITIES` erases clouds/translucents).
- Opaque-only actors: no collect/sort extras.
- Form-wide soft unchanged.
- Residual: heavily interpenetrating soft meshes / true triangle sort still out of scope.
- Residual (pipeline): Fabulous without shaders can still wash soft limbs viewed through soft billboards — sort alone does not fix it; see [`SOFT_OPACITY_FABULOUS.md`](SOFT_OPACITY_FABULOUS.md).

## Decision log

| Decision              | Choice                               | Why                                                   |
| --------------------- | ------------------------------------ | ----------------------------------------------------- |
| Sort granularity      | Per soft `ModelGroup`                | Matches draw API; affordable                          |
| Scope v1              | `limbOnlySoft` only                  | Highest ROI; form-wide already one alpha              |
| Addon / triangle sort | No                                   | Too heavy; Iris-hostile                               |
| Model-block sort key  | lengthSq on draw root                | Proven outdoors; stack is camera-relative             |
| Film sort key         | look-axis depth in world space       | Relative stacks / ±z signs are unreliable             |
| Soft depth write      | Color off + depth stamp when multi   | Film soft-vs-soft + Iris fog/paint need both            |
| Soft cull             | Settings toggle (default backfaces)  | Tradeoff: backfaces vs Iris darkening; form+limb share one setting |
