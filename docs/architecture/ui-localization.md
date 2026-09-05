# UI localization (UIKey)

The mod uses a strict localization system wrapped in `IKey` interfaces. **Never hardcode strings** in the UI.

* **Interface**: `mchorse.bbs_mod.l10n.keys.IKey`
* **Usage**: Define static keys in a `UIKeys` class.
* **Localization file**: `src/client/resources/assets/bbs/assets/strings/en_us.json`

## Implementation

```java
// 1. Define Key
public static final IKey MY_LABEL = L10n.lang("bbs.ui.my_label");

// 2. Use in UI
button.tooltip(UIKeys.MY_LABEL);
```

## Agent notes

* New UI labels/tooltips need a key definition **and** an `en_us.json` entry.
* Prefer existing `UIKeys` constants when the same phrase already exists.
