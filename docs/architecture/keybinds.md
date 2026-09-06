# Keybind system

BBS uses a custom keybind stack distinct from vanilla Minecraft `KeyMapping`.

## Core components

* **`KeyCombo`**: static combination (main key + modifiers)
  * Location: `mchorse.bbs_mod.ui.utils.keys.KeyCombo`
  * Usage: define static constants in `Keys.java`
* **`Keybind`**: runtime object linking a `KeyCombo` to a `Runnable`
  * Location: `mchorse.bbs_mod.ui.utils.keys.Keybind`
  * Validation: `Window.isKeyPressed()` and modifiers (`Shift`, `Ctrl`, `Alt`)
* **`KeybindManager`**: active keybinds for a UI context
  * Location: `mchorse.bbs_mod.ui.utils.keys.KeybindManager`

## Implementing a new keybind

1. Define a `KeyCombo` in `Keys.java`:

```java
public static final KeyCombo MY_ACTION = new KeyCombo(UIKeys.MY_ACTION_LABEL, GLFW.GLFW_KEY_M, GLFW.GLFW_MOD_CONTROL);
```

2. Register the action in a UI panel (e.g. `UIEditorPanel`):

```java
// In UIElement constructor
this.keys().register(Keys.MY_ACTION, () -> {
    this.performMyAction();
}).category(Keys.CATEGORY_EDITORS);
```

3. **Global keybinds** (overlay toggles, etc.): register in the main client event loop or `Overlay` class.

## Important keybinds (not all shown in UI)

* **General**: `0` (Dashboard), `.` (Demorph), `B` (Morph Menu), `F6` (Utility Panel), `F9` (Keybinds List)
* **Film editor**: Right Shift (Open Replays), Right Ctrl (Play), Right Alt (Record), `Y` (Teleport to Replay)
* **Tools**: `V` (Scale Keyframes), `B` (Stack Keyframes), `[` / `]` (Looping Region)

## Related

* Localization for keybind labels: `docs/architecture/ui-localization.md`
* UI framework: `docs/architecture/ui-framework.md`
