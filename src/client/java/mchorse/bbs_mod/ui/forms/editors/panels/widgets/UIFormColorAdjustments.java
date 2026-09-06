package mchorse.bbs_mod.ui.forms.editors.panels.widgets;

import mchorse.bbs_mod.forms.forms.utils.EffectTransform;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.UIEffectTransformCollapse;
import mchorse.bbs_mod.ui.framework.elements.input.UIFormDisclosureCollapse;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.ColorAdjustments;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Color grade controls: brightness / contrast / saturation / hue in a responsive
 * grid (4 / 2 / 1 columns from available width). Each channel keeps its Transform
 * icon; only one transform grid is visible at a time under the channel strip.
 * <p>
 * Form editors wrap this in a Color grade disclosure; keyframe panels use
 * {@linkplain #UIFormColorAdjustments(Supplier, Consumer, boolean) flat mode}
 * so channels appear directly without the collapsible header.
 */
public class UIFormColorAdjustments extends UIElement
{
    private static final int CHANNEL_GAP = 8;
    /** Compact enough that four grade channels stay on one row in typical form panels. */
    private static final int MIN_CHANNEL_WIDTH = 54;

    public final UITrackpad brightness;
    public final UITrackpad contrast;
    public final UITrackpad saturation;
    public final UITrackpad hue;
    public final UIEffectTransformCollapse brightnessTransform;
    public final UIEffectTransformCollapse contrastTransform;
    public final UIEffectTransformCollapse saturationTransform;
    public final UIEffectTransformCollapse hueTransform;

    private final Supplier<Color> color;
    private final Consumer<Color> setter;
    private final boolean collapsible;
    private final UIFormDisclosureCollapse disclosure;
    private final GradeChannelsGrid channelsGrid;
    private final UIEffectTransformCollapse[] gradeTransforms;
    private UIEffectTransformCollapse activeGradeTransform;
    /** One automatic Color grade expand per editor session (first Extra open). */
    private boolean autoExpandPending = true;

    public UIFormColorAdjustments(Supplier<Color> color, Consumer<Color> setter)
    {
        this(color, setter, true);
    }

    /**
     * @param collapsible when false, mounts the channel grid directly (no Color grade header)
     */
    public UIFormColorAdjustments(Supplier<Color> color, Consumer<Color> setter, boolean collapsible)
    {
        super();

        this.color = color;
        this.setter = setter;
        this.collapsible = collapsible;

        this.brightness = this.createTrackpad((value) ->
        {
            Color copy = this.color.get().copy();

            copy.brightness = ColorAdjustments.clampBrightness(value);
            this.setter.accept(copy);
        });
        this.brightness.tooltip(UIKeys.FORMS_EDITORS_COLOR_BRIGHTNESS);
        this.brightnessTransform = this.createTransform((c) -> c.brightnessTransform, (c, t) -> c.brightnessTransform = t);

        this.contrast = this.createTrackpad((value) ->
        {
            Color copy = this.color.get().copy();

            copy.contrast = ColorAdjustments.clampContrast(value);
            this.setter.accept(copy);
        });
        this.contrast.tooltip(UIKeys.FORMS_EDITORS_COLOR_CONTRAST);
        this.contrastTransform = this.createTransform((c) -> c.contrastTransform, (c, t) -> c.contrastTransform = t);

        this.saturation = this.createTrackpad((value) ->
        {
            Color copy = this.color.get().copy();

            copy.saturation = ColorAdjustments.clampSaturation(value);
            this.setter.accept(copy);
        });
        this.saturation.tooltip(UIKeys.FORMS_EDITORS_COLOR_SATURATION);
        this.saturationTransform = this.createTransform((c) -> c.saturationTransform, (c, t) -> c.saturationTransform = t);

        this.hue = this.createTrackpad((value) ->
        {
            Color copy = this.color.get().copy();

            copy.hue = ColorAdjustments.clampHue(value);
            this.setter.accept(copy);
        });
        this.hue.increment(1D).values(5D, 1D, 15D);
        this.hue.tooltip(UIKeys.FORMS_EDITORS_COLOR_HUE);
        this.hueTransform = this.createTransform((c) -> c.hueTransform, (c, t) -> c.hueTransform = t);

        this.gradeTransforms = new UIEffectTransformCollapse[] {
            this.brightnessTransform, this.contrastTransform, this.saturationTransform, this.hueTransform
        };

        for (UIEffectTransformCollapse transform : this.gradeTransforms)
        {
            transform.manageOwnShell(false).onToggle(this::toggleGradeTransform);
        }

        this.brightnessTransform.withLabeledField(UIKeys.FORMS_EDITORS_COLOR_BRIGHTNESS, this.brightness);
        this.contrastTransform.withLabeledField(UIKeys.FORMS_EDITORS_COLOR_CONTRAST, this.contrast);
        this.saturationTransform.withLabeledField(UIKeys.FORMS_EDITORS_COLOR_SATURATION, this.saturation);
        this.hueTransform.withLabeledField(UIKeys.FORMS_EDITORS_COLOR_HUE, this.hue);

        this.channelsGrid = new GradeChannelsGrid(
            this::refreshOpenShells,
            this.brightnessTransform,
            this.contrastTransform,
            this.saturationTransform,
            this.hueTransform
        );

        if (this.collapsible)
        {
            this.h(20);

            /* Column body so exclusive Transform shells (siblings after the channel
               strip) contribute to the Color grade disclosure height / scissor.
               Attaching directly under the animated shell left the grid clipped. */
            UIElement gradeBody = new UIElement();

            gradeBody.column(0).vertical().stretch();
            gradeBody.add(this.channelsGrid);

            this.disclosure = new UIFormDisclosureCollapse(UIKeys.FORMS_EDITORS_COLOR_GRADE, gradeBody)
            {
                @Override
                public void setExpanded(boolean expanded, boolean animate)
                {
                    if (!expanded)
                    {
                        UIFormColorAdjustments.this.closeActiveGradeTransform();
                    }

                    super.setExpanded(expanded, animate);
                }
            };
            this.disclosure.shellHost(this);
            this.disclosure.full(this);
            this.add(this.disclosure);
        }
        else
        {
            /* Flat: channel strip + exclusive transform shells as column siblings. */
            this.disclosure = null;
            this.column(0).vertical().stretch();
            this.add(this.channelsGrid);
        }
    }

    public void registerUndo(UIKeyframes editor)
    {
        this.brightnessTransform.registerUndo(editor);
        this.contrastTransform.registerUndo(editor);
        this.saturationTransform.registerUndo(editor);
        this.hueTransform.registerUndo(editor);
    }

    /**
     * Wire per-trackpad "Reset this value" (sets that grade channel to 0).
     */
    public void wireResetThisValue(BiConsumer<UITrackpad, Runnable> wire)
    {
        wire.accept(this.brightness, () -> this.resetChannel((color) -> color.brightness = 0F));
        wire.accept(this.contrast, () -> this.resetChannel((color) -> color.contrast = 0F));
        wire.accept(this.saturation, () -> this.resetChannel((color) -> color.saturation = 0F));
        wire.accept(this.hue, () -> this.resetChannel((color) -> color.hue = 0F));
    }

    public void resetGrade()
    {
        this.resetChannel((color) ->
        {
            color.brightness = 0F;
            color.contrast = 0F;
            color.saturation = 0F;
            color.hue = 0F;
            color.brightnessTransform = new EffectTransform();
            color.contrastTransform = new EffectTransform();
            color.saturationTransform = new EffectTransform();
            color.hueTransform = new EffectTransform();
        });
    }

    private void resetChannel(Consumer<Color> editor)
    {
        Color copy = this.color.get().copy();

        editor.accept(copy);
        this.setter.accept(copy);
    }

    public void setExpanded(boolean expanded)
    {
        if (this.disclosure != null)
        {
            this.disclosure.setExpanded(expanded);
        }
    }

    public void setExpanded(boolean expanded, boolean animate)
    {
        if (this.disclosure != null)
        {
            this.disclosure.setExpanded(expanded, animate);
        }
    }

    /**
     * Expand Color grade once per editor session (typically the first time Extra opens).
     * Opens instantly so Extra can measure the nested height before its own animation.
     */
    public void tryAutoExpandOnce()
    {
        if (!this.collapsible || !this.autoExpandPending)
        {
            return;
        }

        this.autoExpandPending = false;
        this.setExpanded(true, false);
    }

    /**
     * Called when the owning editor reloads (e.g. startEdit / syncFromForm) so the
     * next Extra open can auto-expand Color grade again.
     */
    public void prepareSession()
    {
        this.autoExpandPending = true;
    }

    private void toggleGradeTransform(UIEffectTransformCollapse source)
    {
        if (source.isExpanded())
        {
            source.setExpanded(false);
            source.setShellExpanded(false, this.channelsGrid);

            if (this.activeGradeTransform == source)
            {
                this.activeGradeTransform = null;
            }

            this.refreshOpenShells();

            return;
        }

        /* Switching between transforms: snap both shells so two grids never
           animate on screen at once. First open / last close keep animation. */
        boolean switching = this.activeGradeTransform != null && this.activeGradeTransform != source;

        if (switching)
        {
            this.activeGradeTransform.setExpanded(false);
            this.activeGradeTransform.setShellExpanded(false, this.channelsGrid, false);
        }

        source.setExpanded(true);
        source.setShellExpanded(true, this.channelsGrid, !switching);
        this.activeGradeTransform = source;
        this.refreshOpenShells();
    }

    private void closeActiveGradeTransform()
    {
        if (this.activeGradeTransform == null)
        {
            return;
        }

        this.activeGradeTransform.setExpanded(false);
        this.activeGradeTransform.setShellExpanded(false, this.channelsGrid, false);
        this.activeGradeTransform = null;
    }

    private void refreshOpenShells()
    {
        if (this.activeGradeTransform != null && this.activeGradeTransform.isExpanded())
        {
            this.activeGradeTransform.getShell().queueRemeasure();
        }

        if (this.disclosure != null && this.disclosure.isExpanded() && this.disclosure.getShell().isOpen())
        {
            this.disclosure.getShell().queueRemeasure();
        }
    }

    private UIEffectTransformCollapse createTransform(Function<Color, EffectTransform> getter, BiConsumer<Color, EffectTransform> assign)
    {
        return new UIEffectTransformCollapse((apply) ->
        {
            Color copy = this.color.get().copy();
            EffectTransform transform = getter.apply(copy);

            if (transform == null)
            {
                transform = new EffectTransform();
                assign.accept(copy, transform);
            }

            apply.accept(transform);
            this.setter.accept(copy);
        });
    }

    private UITrackpad createTrackpad(Consumer<Float> editor)
    {
        UITrackpad trackpad = new UITrackpad((value) -> editor.accept(value.floatValue()));

        trackpad.increment(0.05D).values(0.1D, 0.05D, 0.25D);

        return trackpad;
    }

    public void syncFromForm()
    {
        Color value = this.color.get();

        this.brightness.setValue(value.brightness);
        this.contrast.setValue(value.contrast);
        this.saturation.setValue(value.saturation);
        this.hue.setValue(value.hue);
        this.brightnessTransform.setEffectTransform(value.brightnessTransform);
        this.contrastTransform.setEffectTransform(value.contrastTransform);
        this.saturationTransform.setEffectTransform(value.saturationTransform);
        this.hueTransform.setEffectTransform(value.hueTransform);
    }

    /**
     * Lays out grade channel cells in 4 / 2 / 1 columns from available width.
     */
    private static class GradeChannelsGrid extends UIElement
    {
        private final UIElement[] cells;
        private final Runnable onLayoutChanged;
        private int columns = -1;
        private int lastWidth = -1;

        private GradeChannelsGrid(Runnable onLayoutChanged, UIElement... cells)
        {
            super();

            this.onLayoutChanged = onLayoutChanged;
            this.cells = cells;
            this.column(5).vertical().stretch();
            this.columns = 4;
            this.rebuildRows();
        }

        @Override
        public void resize()
        {
            int width = this.resolveWidth();
            int nextColumns = this.resolveColumns(width);
            boolean columnsChanged = nextColumns != this.columns;
            boolean widthChanged = this.lastWidth >= 0 && Math.abs(width - this.lastWidth) > 1;

            if (columnsChanged)
            {
                this.columns = nextColumns;
                this.rebuildRows();
            }

            super.resize();

            this.lastWidth = this.area.w > 0 ? this.area.w : width;

            if ((columnsChanged || widthChanged) && this.onLayoutChanged != null)
            {
                this.onLayoutChanged.run();
            }
        }

        private int resolveWidth()
        {
            if (this.area.w > 0)
            {
                return this.area.w;
            }

            UIElement parent = this.getParent();

            while (parent != null)
            {
                if (parent.area.w > 0)
                {
                    return parent.area.w;
                }

                parent = parent.getParent();
            }

            return 0;
        }

        private int resolveColumns(int width)
        {
            if (width <= 0)
            {
                return 4;
            }

            int four = MIN_CHANNEL_WIDTH * 4 + CHANNEL_GAP * 3;
            int two = MIN_CHANNEL_WIDTH * 2 + CHANNEL_GAP;

            if (width >= four)
            {
                return 4;
            }

            if (width >= two)
            {
                return 2;
            }

            return 1;
        }

        private void rebuildRows()
        {
            this.removeAll();

            for (int i = 0; i < this.cells.length; i += this.columns)
            {
                int count = Math.min(this.columns, this.cells.length - i);
                UIElement[] row = new UIElement[count];

                System.arraycopy(this.cells, i, row, 0, count);
                this.add(UI.row(CHANNEL_GAP, row));
            }

            this.getFlex().h.reset();
        }
    }
}
