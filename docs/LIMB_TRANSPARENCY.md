# Limb / Bone Transparency Plan

Plan to make per-limb (pose bone) transparency behave like form-wide transparency under Iris, and to add an optional per-limb **Noshading** tradeoff (same idea as the Color track toggle).

Related code lives mainly in:

- `ModelFormRenderer` — form opacity deferral gates
- `BBSRendering` / `ShaderOpacityPatch` — Iris translucent / post-deferred queues
- `CubicVAORenderer` / `ModelGroup.color` — per-bone alpha multiply
- `PoseTransform` / `UITransformKeyframeFactory` / `UIPoseEditor` — limb data + UI

Also see:

- [`SOFT_OPACITY_FABULOUS.md`](SOFT_OPACITY_FABULOUS.md) — **accepted** Fabulous (no shaders) soft-through-soft wash limitation

---

## Problem summary

| Behavior | Form-wide alpha &lt; 1 | Limb-only alpha (form alpha = 1) |
|----------|------------------------|----------------------------------|
| See actors / world behind | Yes (post-deferred / BBS queue) | No (Iris live entity pass) |
| Clouds / fluid compositing | Yes | No |
| Paint overlays | Consistent deferred path | Split live + overlay; paint often wrong |
| Noshading tradeoff | Color track + `Form.noshadingOpacity` | Missing |

**Root cause:** all Iris deferral keys off **form** `color.a` only. Limb alpha is applied later as `vertexAlpha *= group.color.a` and never opens the deferred path.

---

## Goals

1. Soft limbs use the **same** deferred / post-deferred / paint path as soft forms.
2. Optional **Noshading** per limb (films + model-block pose editor), with the same meaning as form Color noshading.
3. Keep opaque limbs of a fully opaque form on the live path when possible (no unnecessary deferral).
4. Do **not** double-multiply bone alpha (deferral gates use effective alpha; draw still uses form α × bone α).

---

## Step 1 — Bone-aware opacity deferral (priority)

**Idea:** compute an *effective* opacity for gate decisions:

```text
opacityAlpha = formColor.a * min(bone.color.a over ModelGroups)
```

Use `opacityAlpha` for:

- `needsIrisTranslucentModelDeferral`
- `needsIrisNoshadingOpacityDeferral` (still form-level noshading flag in this step)
- `softOpacityIrisPath`
- `ShaderOpacityPatch.shouldDelayUntilPostDeferred` / depth / after-fluids helpers

Keep **draw** tint alpha as **form** `color.a` only so `CubicVAORenderer` still does `form × bone` once.

### Implementation checklist

1. Add `getMinBoneOpacityAlpha(ModelInstance)` on `ModelFormRenderer` (default `1F` if no groups).
2. In `renderModel()`, set `formOpacityAlpha = color.a` and `opacityAlpha = formOpacityAlpha * minBone`.
3. Wire gates to `opacityAlpha`; snapshots / easing / RGB handoff to `formOpacityAlpha` (only apply deferred RGB handoff when **form** alpha is below the discard threshold).
4. Smoke-test under Iris:
   - One soft limb, rest opaque → see-through, clouds, paint
   - Form-wide soft → unchanged
   - UI / shadow passes → unchanged
5. Document any residual cases (BOBJ path, body-part children) as follow-ups if needed.

**Trade-off (accepted):** if *any* bone is soft, the **whole** model may enter the deferred queue (opaque bones of that actor also redraw deferred). True per-bone split draws are out of scope for Step 1.

**Files:** `ModelFormRenderer.java` (primary). Optionally tiny helpers in `BBSRendering.java` if shared.

---

## Step 2 — Per-limb Noshading

Depends on Step 1 (without deferral, noshading has nothing useful to switch).

### Data

- Add `boolean noshadingOpacity` (or equivalent) on `PoseTransform`, serialized as `noshading_opacity`.
- Copy through `FormProperties` limb track apply / pose lerp / identity / equals as needed.
- Runtime: if **any** soft bone has noshading **or** form Color noshading is on → treat as noshading for deferral (`needsIrisNoshadingOpacityDeferral(effectiveAlpha, flag)`).

### UI

- Film limb Transform factory: `UITransformKeyframeFactory` — toggle next to color (reuse `UIKeys.FILM_REPLAY_OPACITY_NO_SHADING` / tooltip).
- Model block / form pose: `UIPoseEditor` — same toggle on bone color section.
- Localize if new keys are required; prefer reusing existing noshading strings.

### Renderer

- `ModelFormRenderer`: `noshadingOpacityDefer = needsIrisNoshadingOpacityDeferral(opacityAlpha, form.noshadingOpacity || anyBoneNoshading)`.
- Collect `anyBoneNoshading` from current pose / `ModelGroup` if the flag is mirrored onto groups when applying pose.

### Checklist

1. `PoseTransform` field + serialize + copy/lerp/identity.
2. `Model.applyPose` (or equivalent) propagates flag if needed for runtime scan.
3. UI toggles in transform + pose editors.
4. Wire OR of form + bone flags into deferral.
5. Test: soft limb + noshading on → paint through, pack body shadows lost; off → Iris post-deferred shadows kept.

**Files:** `PoseTransform.java`, `Model.java` / apply pose, `FormProperties.java`, `UITransformKeyframeFactory.java`, `UIPoseEditor.java`, `ModelFormRenderer.java`, strings if needed.

---

## Step 3 — Optional hardening (later)

Only if Step 1 leaves gaps:

| Item | Notes |
|------|--------|
| Split opaque vs soft group draws | Hard; only if mixed opacity must keep opaque limbs fully live |
| BOBJ / non-VAO bone alpha audit | Ensure same effective-alpha gates |
| Body-part child forms | Soft parent limb vs child form opacity interaction |
| Docs / wiki | Short user note: limb opacity uses same Iris tradeoffs as form Color |

---

## Non-goals

- Changing paint/glow math for opaque limbs.
- Per-limb deferred **sort** as separate queue entries (unless Step 3).
- Removing form-level Color noshading (limbs add an OR path, they do not replace it).

---

## Suggested order of work

1. ✅ Write this plan (`docs/LIMB_TRANSPARENCY.md`).
2. ✅ Implement Step 1 — bone-aware opacity deferral in `ModelFormRenderer`.
3. ✅ Implement Step 2 — `PoseTransform.noshadingOpacity` + UI (film limbs / pose editor) + renderer OR with form noshading.
4. ✅ Step 3 (partial) — split opaque vs soft group draws for **limb-only** soft (form still opaque).

## Known follow-ups (after Steps 1–2)

| Issue | Notes |
|-------|--------|
| Soft limb behind soft limb (same actor) | **v1 done:** per-bone post-deferred submit + `distanceSq` — [`SOFT_LIMB_BONE_SORT.md`](SOFT_LIMB_BONE_SORT.md) |
| Model-block preview invisible with soft limbs | **Fixed:** preview draws live |
| Soft Block / Item / Structure (chests, etc.) invisible in inventory GUI | **Fixed:** `FormRenderingContext.isLocalPreview()` skips post-deferred enqueue for `ITEM_INVENTORY` / UI / model-block preview (same contract as ModelForm soft limbs) |
| Soft limb occludes actors/clouds/billboards behind | **Fixed:** opaque live + soft post-deferred |
| Soft limb invisible under Iris (any alpha &lt; 255) | **Fixed:** Iris uses camera matrices + `submitPostDeferredForm` (baked BBS MVP was wrong) |
| Fully transparent limb (alpha 0) still occludes | **Fixed:** drawable bones at alpha ≤ 0.001 are hidden (no depth stamp) |
| Limb Noshading | **Per soft bone:** only that bone uses the BBS noshading queue; other soft limbs stay on Iris post-deferred |
| Iris soft limbs darken as alpha falls (noshading off) | Tradeoff via `soft_transparency_backfaces` setting (default ON = backfaces; OFF = cull / cleaner) |
| Iris soft limbs hide backfaces | **Fixed:** default ON draws backfaces for form + limb soft; OFF culls both consistently |
| Iris fog / paint from behind on soft limbs (noshading off) | **Fixed:** soft limbs depth-stamp; multi soft uses color then depth-only stamp |
| Film soft-vs-soft erased after depth-write | **Fixed:** multi soft color pass without depth-write, then depth stamp |
| Vanilla clouds hidden behind soft actors | **Fixed:** without Iris, soft form/limb flush moves to `WorldRenderEvents.LAST` (after clouds); Iris unchanged |
| Fabulous (no shaders): soft limbs washed / too bright through soft billboards | **Accepted limitation** — see [`SOFT_OPACITY_FABULOUS.md`](SOFT_OPACITY_FABULOUS.md). Flush-target experiments regress soft-vs-soft or chest/opaque occlusion. Use Fancy or Iris when soft-through-soft quality matters. |

## Mixing form-wide + limb transparency

Safe and supported:

- Form soft (any `color.a` &lt; 1) → whole-model deferred path (unchanged; limbs multiply on top).
- Form opaque + some limbs soft → split draws (this step).
- Form soft + limbs soft → form path wins (whole mesh deferred); limb alphas still multiply in vertices.

Limitations to expect:

- Soft-vs-soft on the **same** actor: v1 per-bone sort (see [`SOFT_LIMB_BONE_SORT.md`](SOFT_LIMB_BONE_SORT.md)). Bone centers approximate depth — interpenetrating meshes / nearly coplanar faces can still look wrong from odd angles. Multi soft: color without depth-write, then depth-only stamp (Iris fog/paint stay behind with noshading off).
- No per-triangle / OIT sort — out of scope (Iris-hostile / too heavy).
- **Fabulous graphics without shaders:** soft limbs viewed through soft billboards can look washed / over-bright. Fancy matches expected soft-through-soft; Fabulous uses a layered translucency combine that is not equivalent. Documented in [`SOFT_OPACITY_FABULOUS.md`](SOFT_OPACITY_FABULOUS.md).
- Paint/glow overlays on soft-only limbs may still follow form-level Iris overlay timing.
- Fully transparent bones are skipped for deferral gates (anchors at alpha 0 no longer force a bogus soft path).

## Test plan (manual)

- [ ] Film replay: lower one limb alpha → actors behind + clouds visible; paint still shows.
- [ ] Same with form Color alpha (regression).
- [ ] Soft limb + Noshading on/off (after Step 2).
- [ ] Model block pose editor: bone alpha + noshading. Soft limbs stay visible in preview.
- [ ] Soft limb in front of another actor / clouds / billboard (shaders on and off).
- [ ] Form soft + one softer limb (both paths).
- [ ] Shadow pass / F7 world: no crash, shadows still reasonable.
- [ ] No Iris: limb alpha still fades mesh.
