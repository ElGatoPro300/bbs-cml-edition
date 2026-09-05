# Keyframe system

Handles interpolation of values over time on film/replay/form property tracks.

## Core classes

* `KeyframeChannel<T>`: sorted list of keyframes; binary search (`findSegment`) and `interpolate`
* `Keyframe<T>`: stores `tick`, `value`, `interp`, and Bezier handles (`lx`, `ly`, `rx`, `ry`)
* `Interpolation`: wraps `IInterp` (Linear, Bezier, Easing) and `EasingArgs` (v1–v4 params)

## Factories

`IKeyframeFactory<T>` must serialize/deserialize and interpolate type `T`.

* Registered in `KeyframeFactories`
* Example types: Float, Double, Integer, Pose, Color, Link

### Adding a new animatable type

1. Implement `IKeyframeFactory<MyType>`
2. Register: `KeyframeFactories.FACTORIES.put("my_type", new MyFactory())`

## Advanced features

* **Floating-point ticks**: sub-tick precision
* **Texture animation**: interpolates `_NUMBER.png` sequences
* **Anchor track**: attaches replays to other replays/bones (Translate/Scale modes)

## Related

* Replays: `docs/architecture/replays.md`
* Frame/tick conversion: `docs/FILM_FRAME_TIMELINE.md`
