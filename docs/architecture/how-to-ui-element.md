# How to create a new UI element

1. Create a class extending `UIElement`.
2. Override `render(UIContext context)` for drawing.
3. Handle input via `mouseClicked`, `keyPressed`, etc.

```java
public class UICustomButton extends UIElement {
    @Override
    public void render(UIContext context) {
        // Draw background
        context.batcher.box(this.area, 0xFF000000);
        super.render(context); // Render children
    }
}
```

## Checklist

* User-facing strings → `IKey` / `UIKeys` (`docs/architecture/ui-localization.md`)
* Prefer `UI.row` / `UI.column` for layout
* Follow project code style (`CONTRIBUTING.md` / `AGENTS.md`)

## Related

* UI framework overview: `docs/architecture/ui-framework.md`
