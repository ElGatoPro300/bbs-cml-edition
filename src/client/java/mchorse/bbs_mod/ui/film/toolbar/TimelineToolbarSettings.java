package mchorse.bbs_mod.ui.film.toolbar;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.tooltips.styles.DarkTooltipStyle;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * Tweakable constants for the timeline toolbar system. Kept in a dedicated
 * class so future adjustments (density, dismiss distance, colors) can be
 * changed in one place without touching UI logic.
 */
public class TimelineToolbarSettings
{
    /**
     * Whether timeline toolbars are shown in the film editor. Keyboard shortcuts
     * remain active when toolbars are hidden.
     */
    public static boolean isEnabled()
    {
        return BBSSettings.editorTimelineToolbar == null || BBSSettings.editorTimelineToolbar.get();
    }

    /* Layout */

    /**
     * Height in pixels of the toolbar itself (anchored to the bottom of a
     * timeline panel). Matches the viewport 3D icon bar ({@code UIIcon} is 20px tall).
     */
    public static final int TOOLBAR_HEIGHT = 20;

    /**
     * Horizontal gap between root section buttons. Matches the viewport 3D icon
     * bar ({@code UI.row(0, ...)}).
     */
    public static final int TOOLBAR_SECTION_SPACING = 0;

    /**
     * Icon size for root section buttons. Matches {@link UIIcon}.
     */
    public static final int SECTION_ICON_SIZE = 20;

    /**
     * Gap between the section icon and its visible label text.
     */
    public static final int SECTION_LABEL_PADDING = 4;

    /**
     * Padding after the label text, before the section hit-area edge.
     */
    public static final int SECTION_LABEL_TRAILING_PADDING = 4;

    /**
     * Horizontal padding inside the toolbar bar itself (left/right insets).
     */
    public static final int TOOLBAR_PADDING = 4;

    /**
     * Width/height of the drag handle at the end of the toolbar.
     */
    public static final int DRAG_HANDLE_SIZE = 12;

    /**
     * Gap between the drag separator and the drag handle icon.
     */
    public static final int DRAG_HANDLE_SEPARATOR_GAP = 4;

    /**
     * Thickness of the separator line before the drag handle.
     */
    public static final int DRAG_HANDLE_SEPARATOR_SIZE = 1;

    /**
     * Total width/height reserved at the end of the toolbar for separator + handle.
     */
    public static int getDragHandleReserved()
    {
        return DRAG_HANDLE_SEPARATOR_GAP + DRAG_HANDLE_SEPARATOR_SIZE + DRAG_HANDLE_SIZE;
    }

    /**
     * Pointer movement on the scroll axis (in pixels) required before a held
     * press in the section area is treated as drag-scrolling instead of a click.
     */
    public static final int SECTIONS_SCROLL_DRAG_THRESHOLD = 2;

    /**
     * Thickness of the toolbar strip for any dock edge (matches {@link #TOOLBAR_HEIGHT}).
     */
    public static int getThickness(TimelineToolbarDock dock)
    {
        return TOOLBAR_HEIGHT;
    }

    /**
     * Border width for dock preview rectangles during toolbar drag.
     */
    public static final int DOCK_PREVIEW_BORDER = 2;

    /**
     * Default horizontal offset from the cursor when a hover card starts to the
     * right/below the pointer. Flipped when the card would leave the screen.
     */
    public static final int TOOLTIP_CURSOR_OFFSET_X = 8;

    /**
     * Horizontal gap between the cursor and a hover card anchored to the left
     * (left-docked toolbar while a menu is open). Lower values pull the card
     * closer to the pointer.
     */
    public static final int TOOLTIP_CURSOR_OFFSET_X_LEFT = 2;

    /**
     * Default vertical offset from the cursor when a hover card starts below the
     * pointer. Flipped when the card would leave the screen.
     */
    public static final int TOOLTIP_CURSOR_OFFSET_Y = 12;

    /* Menu popup layout */

    /**
     * Height in pixels of a single menu row (item).
     */
    public static final int MENU_ITEM_HEIGHT = 18;

    /**
     * Height in pixels of a separator row.
     */
    public static final int MENU_SEPARATOR_HEIGHT = 6;

    /**
     * Left padding inside a menu row before the icon slot.
     */
    public static final int MENU_ITEM_PADDING_LEFT = 6;

    /**
     * Right padding inside a menu row after the shortcut / arrow.
     */
    public static final int MENU_ITEM_PADDING_RIGHT = 6;

    /**
     * Fixed width reserved for the leading icon slot on every row. Rows with
     * no icon leave this slot empty so labels line up across items.
     */
    public static final int MENU_ITEM_ICON_SLOT = 18;

    /**
     * Gap between the icon slot and the label.
     */
    public static final int MENU_ITEM_ICON_LABEL_GAP = 2;

    /**
     * Minimum gap between the label and the shortcut column.
     */
    public static final int MENU_LABEL_SHORTCUT_GAP = 20;

    /**
     * Gap between the shortcut column and the trailing arrow (>) for
     * submenus.
     */
    public static final int MENU_SHORTCUT_ARROW_GAP = 6;

    /**
     * Width reserved for the trailing arrow indicating a submenu.
     */
    public static final int MENU_ARROW_WIDTH = 6;

    /**
     * Minimum overall width of a popup menu.
     */
    public static final int MENU_MIN_WIDTH = 100;

    /**
     * Extra padding added to a popup's width to keep things breathable.
     */
    public static final int MENU_WIDTH_PADDING = 8;

    public static final int MENU_SCREEN_MARGIN = 8;

    /**
     * Vertical gap between a section button and its popup (or between a
     * parent submenu row and its child popup on overlap).
     */
    public static final int MENU_GAP = 1;

    /**
     * Extra hit-test padding for the invisible corridor between a submenu row
     * and its child popup so moving the cursor across {@link #MENU_GAP} does
     * not dismiss the submenu prematurely.
     */
    public static final int SUBMENU_BRIDGE_PADDING = 6;

    /* Interaction */

    /**
     * How far (in pixels) the mouse must travel away from every open menu
     * rectangle (root section + open popups) before the entire chain closes.
     * Blender-like dismiss distance.
     */
    public static final int TOOLBAR_MENU_DISMISS_DISTANCE_PX = 40;

    /**
     * Delay in ms before a hovered submenu row expands its child popup.
     * Kept small to feel responsive; larger values reduce accidental opens
     * when the mouse crosses a submenu row on its way somewhere else.
     */
    public static final long SUBMENU_HOVER_OPEN_DELAY_MS = 120L;

    /* Interaction modes (track pick, future tick pick) */

    /**
     * Period of one pulse cycle for interaction highlights (tracks, clips,
     * viewport edge).
     */
    public static final long INTERACTION_TRACK_PULSE_PERIOD_MS = 1200L;

    /**
     * Minimum alpha of the pulsing clip / viewport placement preview (0 to 1).
     */
    public static final float INTERACTION_CLIP_PULSE_MIN_ALPHA = 0.1F;

    /**
     * Maximum alpha of the pulsing clip / viewport placement preview (0 to 1).
     */
    public static final float INTERACTION_CLIP_PULSE_MAX_ALPHA = 1F;

    /**
     * Minimum alpha of the pulsing highlight on eligible tracks when the
     * cursor is not over that row.
     */
    public static final float INTERACTION_TRACK_ELIGIBLE_PULSE_MIN_ALPHA = 0F;

    /**
     * Maximum alpha of the pulsing highlight on eligible tracks when the
     * cursor is not over that row.
     */
    public static final float INTERACTION_TRACK_ELIGIBLE_PULSE_MAX_ALPHA = 0.25F;

    /**
     * Minimum alpha of the pulsing highlight on the track row under the
     * cursor during track-pick interaction.
     */
    public static final float INTERACTION_TRACK_HOVER_PULSE_MIN_ALPHA = 0.1F;

    /**
     * Maximum alpha of the pulsing highlight on the track row under the
     * cursor during track-pick interaction.
     */
    public static final float INTERACTION_TRACK_HOVER_PULSE_MAX_ALPHA = 0.5F;

    /**
     * Minimum alpha of the pulsing tick column during tick-pick interactions.
     */
    public static final float INTERACTION_TICK_PULSE_MIN_ALPHA = 0F;

    /**
     * Maximum alpha of the pulsing tick column during tick-pick interactions.
     */
    public static final float INTERACTION_TICK_PULSE_MAX_ALPHA = 1F;

    /**
     * Width in pixels of the pulsing tick column outline.
     */
    public static final int INTERACTION_TICK_COLUMN_WIDTH = 1;

    /**
     * Width of the pulsing primary-color edge fade drawn inside the 3D
     * viewport during viewport interaction modes.
     */
    public static final int INTERACTION_VIEWPORT_EDGE_SIZE = 48;

    /**
     * Margin from the bottom-left of the timeline area to the hint card
     * (sits just above the toolbar).
     */
    public static final int INTERACTION_HINT_MARGIN = 6;

    /**
     * Horizontal inset between hint text and card edges.
     */
    public static final int INTERACTION_HINT_PADDING_X = 4;

    /**
     * Vertical inset between hint text and card edges.
     */
    public static final int INTERACTION_HINT_PADDING_Y = 4;

    /**
     * Pixels trimmed from the bottom of the hint card so text sits visually
     * centered (Minecraft font metrics leave extra space below glyphs).
     */
    public static final int INTERACTION_HINT_BOTTOM_TRIM = 1;

    /**
     * Downward offset applied to hint text within the card.
     */
    public static final int INTERACTION_HINT_TEXT_Y_OFFSET = 0;

    /**
     * When {@code false}, hint cards omit the white outline (temporary toggle).
     */
    public static boolean INTERACTION_HINT_DRAW_BORDER = false;

    public static final int INTERACTION_HINT_BACKGROUND = Colors.A90 | 0x1a1a1e;

    public static final int INTERACTION_HINT_BORDER = Colors.A50 | Colors.WHITE;

    public static final int INTERACTION_HINT_FG = Colors.WHITE;

    /**
     * When {@code true}, timeline keyboard shortcuts also show toolbar-style
     * interaction hints while transform modes (stack, scale time, etc.) are
     * active. Toolbar menu clicks always show hints regardless of this flag.
     */
    public static boolean SHORTCUTS_USE_INTERACTION_HINTS = false;

    /* Colors */

    /**
     * Background color of the toolbar bar.
     */
    public static final int TOOLBAR_BACKGROUND = 0xff1a1a1e;

    /**
     * Top separator line color for the toolbar.
     */
    public static final int TOOLBAR_BORDER = 0xff3a3a42;

    /**
     * Hover tint for a root section button.
     */
    public static final int SECTION_HOVER_COLOR = Colors.A50 | 0x4488ff;

    /**
     * Highlight color when a section popup is currently open.
     */
    public static final int SECTION_OPEN_COLOR = Colors.A75 | 0x4488ff;

    /**
     * Background color of a popup menu. Same opaque black as context menus and
     * {@link DarkTooltipStyle}
     * ({@link Colors#A100}), before optional primary tint.
     */
    public static final int MENU_BACKGROUND_BASE = Colors.A100; /* old value:0xff1e1e22 */

    /**
     * How much of the UI primary color is mixed into {@link #MENU_BACKGROUND_BASE}
     * (0 = pure black, 1 = full primary).
     */
    public static float MENU_BACKGROUND_PRIMARY_MIX = 0F;

    /**
     * Opacity of the menu panel background (0 = fully transparent, 1 = opaque).
     * Adjust this value to change menu transparency.
     */
    public static float MENU_BACKGROUND_ALPHA = 0.88F;

    /**
     * Pixel spread of the primary-color glow around popup menus. Matches
     * {@link DarkTooltipStyle}.
     */
    public static final int MENU_SHADOW_OFFSET = 6;

    /**
     * Base border tone before primary tint (former {@code MENU_BORDER} gray).
     */
    public static final int MENU_BORDER_BASE = 0xff3a3a42;

    /**
     * How much of the UI primary color is mixed into {@link #MENU_BORDER_BASE}
     * (0 = base only, 1 = full primary).
     */
    public static float MENU_BORDER_PRIMARY_MIX = 0.8F;

    /**
     * Opacity of menu borders and separator lines (0 = fully transparent, 1 = opaque).
     */
    public static float MENU_BORDER_ALPHA = 1F;

    /**
     * Hover tint applied to a menu row.
     */
    public static final int MENU_ITEM_HOVER = Colors.A50 | 0x4488ff;

    /**
     * Color of a menu item label / icon when enabled.
     */
    public static final int MENU_ITEM_FG = Colors.WHITE;

    /**
     * Color of the shortcut column when enabled.
     */
    public static final int MENU_ITEM_SHORTCUT_FG = 0xff9aa0a6;

    /**
     * Color of a menu item label / icon when disabled (any cause).
     */
    public static final int MENU_ITEM_DISABLED_FG = 0xff707078;

    /**
     * Text color for the "reason" tooltip shown when a red-disabled item is
     * hovered (e.g. viewport hidden). Only the tooltip text is red; the row
     * itself stays with the normal disabled color to avoid noise.
     */
    public static final int MENU_ITEM_DISABLED_REASON_FG = Colors.A100 | Colors.RED;

    /**
     * Color of the trailing arrow for submenus (enabled state).
     */
    public static final int MENU_ARROW_FG = 0xffcccccc;

    public static int getMenuBackground()
    {
        Color color = new Color();

        Colors.interpolate(color, MENU_BACKGROUND_BASE, BBSSettings.primaryColor.get() | Colors.A100,
            MENU_BACKGROUND_PRIMARY_MIX);

        return Colors.setA(color.getARGBColor(), MENU_BACKGROUND_ALPHA);
    }

    public static int getMenuShadowInner()
    {
        return Colors.A25 + BBSSettings.primaryColor.get();
    }

    public static int getMenuShadowOuter()
    {
        return BBSSettings.primaryColor.get();
    }

    public static int getMenuBorder()
    {
        Color color = new Color();

        Colors.interpolate(color, MENU_BORDER_BASE, BBSSettings.primaryColor.get() | Colors.A100,
            MENU_BORDER_PRIMARY_MIX);

        return Colors.setA(color.getARGBColor(), MENU_BORDER_ALPHA);
    }

    /* Constructor */

    private TimelineToolbarSettings()
    {}
}
