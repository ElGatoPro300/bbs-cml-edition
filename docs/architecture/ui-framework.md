# UI framework

The UI is built on a custom framework centered around `UIElement`.

* **Base class**: `mchorse.bbs_mod.ui.framework.elements.UIElement`
* **Common elements**:
  * **Button**: `UIButton` (action triggers)
  * **Toggle**: `UIToggle` (boolean switch)
  * **Color picker**: `UIColor` (RGB/Alpha selection)
  * **Icon**: `UIIcon` (renders an icon)
* **Layouts**: Use `UI.row(element)` and `UI.column(element)` for flex-like layouts.
* **Context**: `UIContext` is passed to render methods for font rendering and mouse coordinates.

## Panels and dashboards

* **UIDashboard**: The main editor interface (`0` key). Manages sub-panels.
* **UIPanelBase**: Base class for full-screen UI panels.
* **UIOverlayPanel**: Base for modal overlays.

## Related

* Creating a new widget: `docs/architecture/how-to-ui-element.md`
* Localization: `docs/architecture/ui-localization.md`
