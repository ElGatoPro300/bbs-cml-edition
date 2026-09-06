# Keyframe system

Handles interpolation of values over time on film/replay/form property tracks.

## Core classes

* `KeyframeChannel<T>`: sorted list of keyframes; binary search (`findSegment`) and `interpolate`
* `Keyframe<T>`: stores `tick`, `value`, `interp`, and Bezier handles (`lx`, `ly`, `rx`, `ry`)
* `Interpolation`: wraps `IInterp` (Linear, Bezier, Easing) and `EasingArgs` (v1–v4 params)

## Factories

`IKeyframeFactory<T>` must serialize/deserialize and interpolate type `T`.

* Registered in `KeyframeFactories`
* Example types: Float, Double, Integer, Pose, Color, Link, ItemStack, Boolean, ShadowSettings, MountLink, …

### Adding a new animatable type

1. Implement `IKeyframeFactory<MyType>`
2. Register: `KeyframeFactories.FACTORIES.put("my_type", new MyFactory())`

### `KeyframeChannel.interpolate(float)` empty-channel default

When the channel has **no** keyframes (or no segment), `interpolate(ticks)` picks a numeric zero only for:

* `FloatKeyframeFactory` → `0F`
* `DoubleKeyframeFactory` (and subclasses) → `0D`
* `IntegerKeyframeFactory` → `0`

For **every other factory** (Boolean, ItemStack, Pose, Color, ShadowSettings, MountLink, …) the one-arg overload returns **`null`**. Callers that need a typed empty value should use `interpolate(ticks, default)` or `factory.createEmpty()` / `interpolateHeld`.

This is intentional for the generic channel API — not a replay-recording limitation. Do **not** assume `interpolate(tick)` is non-null for non-numeric tracks.

## Replay recording resume

Viewport re-record at tick `T` uses `ReplayKeyframes.bridgeRecordingFrom(T, groups)`:

1. Snapshot interpolated values from **non-empty** channels only.
2. `clearFrom(T, groups)` (same channel set as before).
3. Restore snapshots at `T`.

Empty channels are never seeded with defaults (avoids planting `0°` / south yaw on from-scratch takes).

### Factories / channels covered by the bridge

| Factory | Replay channels bridged |
|---------|-------------------------|
| **Double** | Position, velocity, fall, rotation (yaw/pitch/head/body), sticks/triggers/extras, vanilla pose flags (`sneaking`, `using_item`, …) |
| **ItemStack** | Hands + armor (only when recording **all groups**) |
| **Integer** | `selected_slot` (all groups only) |

Yaw/pitch/body stay on plain `DOUBLE`. `apply()` unwraps prev yaw toward current with `Lerps.normalizeYaw` for short-arc **render** only (stored keys unchanged).

### Not bridged / not viewport-recorded (pre-existing)

These exist on `ReplayKeyframes` but are **outside** `clearFrom` / `record` / `bridgeRecordingFrom` today:

* `invulnerable` (**Boolean**)
* `shadow_size` (**ShadowSettings**), `shadow_opacity` (**Double** — opacity exists but is not in the clear/record set either)
* `riding` (**Double**), `ridden` (**MountLink**) — mount capture uses other paths (`RecorderMobCapture`, etc.)

Form property tracks (Pose, Color, Transform, …) live under `FormProperties`, not this bridge.

**If you later add viewport recording (or resume-bridge) for a new factory type:** extend `bridgeRecordingFrom` / `clearFrom` / `record` with typed snapshot+restore (same pattern as `snapshotItem` / `snapshotInteger`), and decide empty-channel policy (`createEmpty()` vs skip). Do not rely on `interpolate(tick)` alone for non-numeric factories.

## Related

* Replays: `docs/architecture/replays.md`
* Frame/tick conversion: `docs/FILM_FRAME_TIMELINE.md`
