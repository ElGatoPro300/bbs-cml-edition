# Replay system

Handles recording and playback of entities on the film timeline.

* **Class**: `mchorse.bbs_mod.film.replays.Replay`
* **Structure**: Extends `ValueGroup`. Contains `ReplayKeyframes` for motion data and `FormProperties` for visual animation.

## Features

* **Multi-replay editing**: bulk editing of properties and keyframe offsets
* **Onion skinning**: ghost overlays for animation reference (**stub-only**)
* **Actor Mode**: flag `Replay.actor` — see `docs/architecture/actor-mode.md`

## Agent notes

* Replays are the timeline identity; Actor Mode changes how the body exists in the world but does not replace the replay/stub data model.
* Relative camera (`Replay.relative`) is stub-oriented; Actor Mode has stricter rules (see actor-mode deep-dive).

## Related

* Actor Mode: `docs/architecture/actor-mode.md`
* Keyframes: `docs/architecture/keyframes.md`
* Frame/tick timeline: `docs/FILM_FRAME_TIMELINE.md`
