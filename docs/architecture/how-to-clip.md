# How to add a new Clip

1. **Data class**: `public class MyClip extends Clip`. Define properties.
2. **Factory**: implement `create()` to return a new instance.
3. **UI editor**: `public class UIMyClip extends UIClip<MyClip>`. Populate fields.
4. **Registration**:
   * **Common**: `BBSMod.factoryActionClips.register(Link.bbs("my_clip"), MyClip.class, new ClipFactoryData(...));`
   * **Client**: in the static block of `UIClip.java`: `register(MyClip.class, UIMyClip::new);`

## Action clips

If the clip runs gameplay/client effects, extend `ActionClip` and override:

* `applyAction()` — server / fake-player
* `applyClientAction()` — client-only
* Use `frequency` for one-shot (`0`) vs repeating every N ticks

## Checklist

* Read wiki + `docs/architecture/clips.md` first
* Localization for editor labels: `docs/architecture/ui-localization.md`
* Timeline/frame notes: `docs/FILM_FRAME_TIMELINE.md`

## Related

* Addons / custom clips: `docs/ADDONS.md`
