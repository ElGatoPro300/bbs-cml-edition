package mchorse.bbs_mod.ui.forms.editors.panels.widgets;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UIEffectTransformCollapse;
import mchorse.bbs_mod.ui.framework.elements.input.UIFormDisclosureCollapse;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;

/**
 * Shared Color / Glow / Paint / Grade layout for form + pose editors.
 * Transform uses a trailing fixed-size icon beside each assigned field
 * ({@link UIEffectTransformCollapse}); the grid opens full-width underneath.
 */
public final class UIFormColorLayout
{
    private UIFormColorLayout()
    {}

    public static UIElement sectionLabel(IKey key)
    {
        return UI.label(key).marginTop(8);
    }

    /**
     * Color swatch + linked numeric input on one row, each with its own label above.
     * Columns stretch so children receive the row cell width.
     */
    public static UIElement labeledColorValueRow(IKey colorLabel, UIColor color, IKey valueLabel, UITrackpad value)
    {
        UIElement colorColumn = new UIElement();
        UIElement valueColumn = new UIElement();

        colorColumn.column(5).vertical().stretch();
        colorColumn.add(UI.label(colorLabel), color);
        valueColumn.column(5).vertical().stretch();
        valueColumn.add(UI.label(valueLabel), value);

        return UI.row(colorColumn, valueColumn);
    }

    public static UIElement colorValueRow(UIColor color, UITrackpad value)
    {
        return UI.row(color, value);
    }

    public static UIElement paintColorRow(UIColor paintColor, UITrackpad paintIntensity)
    {
        return labeledColorValueRow(
            UIKeys.FORMS_EDITORS_PAINT_COLOR,
            paintColor,
            UIKeys.FORMS_EDITORS_PAINT_INTENSITY,
            paintIntensity
        ).marginTop(4);
    }

    /**
     * Paint color + intensity; Transform icon at the trailing edge of the paint swatch.
     */
    public static UIElement paintColorRowWithTransform(UIColor paintColor, UITrackpad paintIntensity, UIEffectTransformCollapse transform)
    {
        return transform.withLabeledColorValue(
            UIKeys.FORMS_EDITORS_PAINT_COLOR,
            paintColor,
            UIKeys.FORMS_EDITORS_PAINT_INTENSITY,
            paintIntensity
        ).marginTop(4);
    }

    /**
     * Main / bone tint swatch with Transform icon at the trailing edge.
     */
    public static UIElement colorWithTransform(UIColor color, UIEffectTransformCollapse transform)
    {
        return transform.withLeading(color);
    }

    /**
     * Bone tint + lighting on one header row; transform grid still opens full-width below.
     */
    public static UIElement colorWithTransformAndExtras(UIColor color, UIEffectTransformCollapse transform, UIElement... extras)
    {
        return transform.withLeading(color).withHeaderExtras(extras);
    }

    /**
     * Glow color + intensity without Transform (forms that have no glow transform).
     */
    public static UIElement createGlowSection(UIColor glowingColor, UITrackpad glowIntensity)
    {
        return labeledColorValueRow(
            UIKeys.FORMS_EDITORS_GLOWING_COLOR,
            glowingColor,
            UIKeys.FORMS_EDITORS_GLOW_INTENSITY,
            glowIntensity
        ).marginTop(4);
    }

    /**
     * Glow color + intensity; Transform icon at the trailing edge of the glow swatch.
     */
    public static UIElement createGlowSection(UIColor glowingColor, UITrackpad glowIntensity, UIEffectTransformCollapse transform)
    {
        return transform.withLabeledColorValue(
            UIKeys.FORMS_EDITORS_GLOWING_COLOR,
            glowingColor,
            UIKeys.FORMS_EDITORS_GLOW_INTENSITY,
            glowIntensity
        ).marginTop(4);
    }

    /**
     * Glow Size + Spread row — Photoshop Outer Glow style.
     */
    public static UIElement createGlowSizeSpreadRow(UITrackpad glowSize, UITrackpad glowSpread)
    {
        UIElement sizeColumn = new UIElement();
        UIElement spreadColumn = new UIElement();

        sizeColumn.column(5).vertical().stretch();
        sizeColumn.add(UI.label(UIKeys.FORMS_EDITORS_GLOW_SIZE), glowSize);
        spreadColumn.column(5).vertical().stretch();
        spreadColumn.add(UI.label(UIKeys.FORMS_EDITORS_GLOW_SPREAD), glowSpread);

        return UI.row(sizeColumn, spreadColumn).marginTop(4);
    }

    /**
     * Collapsible Extra block: paint / glow / color grade (and similar) rows.
     * The first Extra open in a session snaps Color grade open (no nested
     * animation) so Extra's height animation includes that content; later
     * Extra toggles keep the user's Color grade collapsed/expanded choice.
     */
    public static UIFormDisclosureCollapse createExtraSection(UIElement... children)
    {
        UIFormDisclosureCollapse section = new UIFormDisclosureCollapse(
            UIKeys.FORMS_EDITORS_COLOR_EXTRA,
            UI.column(children),
            UIFormDisclosureCollapse.EXTRA_COLOR
        );

        section.onExpand(() ->
        {
            for (UIElement child : children)
            {
                if (child instanceof UIFormColorAdjustments grade)
                {
                    grade.tryAutoExpandOnce();
                }
            }
        });

        return section;
    }
}
