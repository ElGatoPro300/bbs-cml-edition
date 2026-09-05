# Rendering / Iris compatibility

When implementing renderers, follow these rules for Iris/shaders compatibility.

## 1. Shadow pass check

Avoid rendering 2D elements or complex transparency during shadow passes.

```java
if (BBSSettings.isIrisShadowPass()) return;
```

## 2. State management

Always push/pop the matrix stack and restore render state.

```java
RenderSystem.enableBlend();
// ... draw ...
RenderSystem.disableBlend();
```

## 3. Shader attributes

Check `BBSRendering.isIrisShadersEnabled()` before using custom vertex attributes (e.g. tangents) that might crash vanilla shaders.

## 4. World vs GUI rendering

Use `BBSRendering.isRenderingWorld()` to distinguish world vs GUI, and adjust lighting (GUI typically needs fake lighting).

## Actor Mode note

For actor replays, stub bodies must not cast a second shadow — see `docs/architecture/actor-mode.md` (`ShadowRendererMixin`).

## Related

* Forms: `docs/architecture/forms.md`
* ModelForm pipeline: `docs/architecture/model-form.md`
* Opacity / limb docs under `docs/` (`LIMB_TRANSPARENCY.md`, `SOFT_OPACITY_*.md`, …)
