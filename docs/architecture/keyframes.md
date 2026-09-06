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

1. Snapshot interpolated values from **non-empty** channels only (for the groups being recorded).
2. `clearFrom(T, groups)` (same channel set).
3. Restore snapshots at `T`.

Empty channels are never seeded with defaults (avoids planting `0°` / south yaw on from-scratch takes).

### Vanilla pose / action tracks

The record overlay has no dedicated pose/action buttons. Pose flags (`sneaking`, `sleeping`, …) and action doubles (`using_item`, `death_time`, …) — plus viewport `riding` via `recordMountKeyframes` — are only cleared / bridged / recorded when capturing **all groups** (`groups == null` or empty).

Position-only / rotation-only / stick takes **leave those tracks alone**.

When all-groups recording writes a pose/action channel that is **empty**, a value of `0` is **not** inserted (intentionally cleared tracks stay empty until the entity actually enters a non-zero state).

`riding` / `ridden` are cleared from `T` on all-groups re-record (same gate). They are **not** bridge-restored from old timeline values — `recordMountKeyframes` rewrites them from live mount state so a non-sitting re-take does not keep leftover sitting keys. On other replays, only `ridden` keys from `T` that **link to this rider index** are removed (other mounts' links stay intact).

### Factories / channels covered by the bridge

| Factory | Replay channels bridged |
|---------|-------------------------|
| **Double** | Position, velocity, fall, rotation (yaw/pitch/head/body), sticks/triggers/extras; vanilla pose/action flags **only for all-groups** (not `riding`) |
| **ItemStack** | Hands + armor (only when recording **all groups**) |
| **Integer** | `selected_slot` (all groups only) |

Yaw/pitch/body stay on plain `DOUBLE`. `apply()` unwraps prev yaw toward current with `Lerps.normalizeYaw` for short-arc **render** only (stored keys unchanged).

### Not bridge-restored (cleared + live-recaptured on all-groups)

* `riding` (**Double**), `ridden` (**MountLink**) — cleared from `T` in `clearFrom`; rewritten by `RecorderMobCapture.recordMountKeyframes` (empty `riding` is not seeded with `0`)

### Not bridged / not viewport-recorded (pre-existing)

These exist on `ReplayKeyframes` but are **outside** `clearFrom` / `record` / `bridgeRecordingFrom` today:

* `invulnerable` (**Boolean**)
* `shadow_size` (**ShadowSettings**), `shadow_opacity` (**Double** — opacity exists but is not in the clear/record set either)

Form property tracks (Pose, Color, Transform, …) live under `FormProperties`, not this bridge.

**If you later add viewport recording (or resume-bridge) for a new factory type:** extend `bridgeRecordingFrom` / `clearFrom` / `record` with typed snapshot+restore (same pattern as `snapshotItem` / `snapshotInteger`), and decide empty-channel policy (`createEmpty()` vs skip). Do not rely on `interpolate(tick)` alone for non-numeric factories.

## Related

* Replays: `docs/architecture/replays.md`
* Frame/tick conversion: `docs/FILM_FRAME_TIMELINE.md`
