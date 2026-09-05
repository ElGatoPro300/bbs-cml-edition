# Soft opacity on Fabulous graphics (no shaders)

**Status:** known limitation (accepted). Do not “fix” by only moving the soft flush target / timing without a larger design.

Related:

- [`LIMB_TRANSPARENCY.md`](LIMB_TRANSPARENCY.md) — ModelForm / soft limbs
- [`SOFT_OPACITY_FLAT_FORMS.md`](SOFT_OPACITY_FLAT_FORMS.md) — billboards and other flats
- [`SOFT_LIMB_BONE_SORT.md`](SOFT_LIMB_BONE_SORT.md) — soft-vs-soft bone sort
- `ShaderOpacityPatch` — queue + Fabulous translucent-FB flush
- `BBSModClient` — `WorldRenderEvents.AFTER_TRANSLUCENT` / `LAST`

---

## Symptom

With **Graphics = Fabulous!** and **no Iris shaders**, soft limbs (and similar soft meshes) can look **too bright / washed / “clearer”** when seen **through soft billboards** (and sometimes other soft flats).

- **Fancy** (and Iris): soft-vs-soft through billboards looks correct.
- **Fabulous:** soft stays **visible**, but that wash can remain.

This is separate from soft-behind-glass / cloud trade-offs documented elsewhere.

---

## Why Fabulous is different

Fancy draws the world into **one** main framebuffer. Soft flushes at `WorldRenderEvents.LAST` onto that final color, so soft-vs-soft alpha-blends like a normal transparent pass over the real scene.

Fabulous splits the frame into layers (terrain, translucent, item/entity, particles, clouds, …) and runs a **transparency combine** shader that depth-sorts those layers. Soft must be written into a layer **before** that combine, or it never shows up (or only shows if painted onto main afterward with incomplete depth).

Current stable choice: on Fabulous, soft flushes into the **translucent** framebuffer before combine (`ShaderOpacityPatch.onAfterTranslucentTerrain`). That keeps soft visible and preserves occlusion against many opaque things via the combine — but soft-vs-soft is no longer “blend once over final world color” like Fancy. The combine’s blend assumes layer contents suitable for that pipeline; soft limb + soft billboard stacks can read as **washed / over-bright**.

---

## What was tried (and why it was reverted)

| Approach | Wash | Soft vs soft order | Soft vs chests / opaque forms |
|----------|------|--------------------|-------------------------------|
| Soft in translucent FB (current) | Bad | Generally OK | Generally OK |
| Soft on main at `LAST` (no depth restore) | Soft often vanished | — | — |
| Soft on main at `LAST` + blit live entity depth | Better | OK-ish | Soft on top (post-combine entity depth is dead) |
| Soft on main at `LAST` + depth stash from `AFTER_ENTITIES` | Better | Regressed | Soft / billboards on top of soft forms or block entities |
| Soft in entity (ItemEntity) FB | — | Limbs on top of soft billboards / soft forms | Better vs chests |

Moving only the flush **target** or **event** trades one failure for another. A single soft flush cannot satisfy all of: Fabulous visibility, Fancy-like soft-vs-soft, and correct occlusion vs entity/block-entity depth.

---

## Accepted limitation

**Vanilla Fabulous + no shaders:** washed / overly bright soft limbs when viewed through soft billboards (and similar soft-over-soft see-through) may remain.

**Workarounds for users / filmmakers:**

- Use **Fancy** graphics when soft-through-soft quality matters and shaders are off.
- Or use **Iris** (shader pack) where soft uses the Iris post-deferred path instead of Fabulous layers.

---

## What would be needed for a real fix

Not in scope for small flush tweaks. Would need something like:

- A dedicated soft / OIT-style pass, or
- A custom Fabulous transparency layer + combine that matches Fancy’s single-buffer soft composite, or
- Another compositor-level design — with ongoing maintenance cost.

Until then, treat Fabulous soft-through-soft wash as **documented, accepted** behavior.

---

## Do not re-apply without a new design

Avoid reintroducing experimental Fabulous soft flush variants (main-at-`LAST` with ad-hoc depth blit/stash, entity-FB soft flush, dual soft passes) unless they ship with a clear design that covers visibility, soft-vs-soft, and chest/opaque occlusion together — and with manual test sign-off for all three.
