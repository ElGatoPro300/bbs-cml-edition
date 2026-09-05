# Clip system

Clips are segments on the timeline (camera paths, effects, media, utilities).

## Common clips

* **Camera**: Path, Keyframe (`distance` track), Dolly, Idle, Look, Orbit, Tracker (attaches to body parts)
* **Effects**: Shake, Drag (smooth), Math (expressions)
* **Media**: Audio, Subtitle
* **Utility**: Remapper (time warp), Envelope (transitions)
* **Curve clip**: animates generic values (sun rotation, shader options, …)
* **Dolly zoom**: vertigo effect via FOV/Distance animation

## Action clips

Action clips extend `ActionClip`:

* `applyAction()` — server / fake-player logic
* `applyClientAction()` — client-only effects
* `frequency` — one-shot (`0`) vs repeating every N ticks

See also `AGENTS.md` (film pipeline notes) and `docs/architecture/how-to-clip.md`.

## Related

* How to add a clip: `docs/architecture/how-to-clip.md`
* Frame/tick timeline: `docs/FILM_FRAME_TIMELINE.md`
* Addons / custom clips: `docs/ADDONS.md`
