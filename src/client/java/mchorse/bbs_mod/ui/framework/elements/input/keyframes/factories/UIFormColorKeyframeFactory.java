package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.LabelForm;
import mchorse.bbs_mod.forms.forms.utils.EffectTransform;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIFormColorLayout;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UIEffectTransformCollapse;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

import java.util.List;
import java.util.function.Consumer;

/**
 * Film Color track: blend color (RGBA) + transform, plus spectrum / noshading flags.
 * Paint, glow and color grade live on nested tracks under Color.
 */
public class UIFormColorKeyframeFactory extends UIKeyframeFactory<Color>
{
    private final boolean simpleBlendColorOnly;

    private UIColor blendColor;
    private UIEffectTransformCollapse blendTransform;
    private UIToggle spectrum;
    private UIToggle noShading;
    private boolean fillingNoshading;

    public UIFormColorKeyframeFactory(Keyframe<Color> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);

        this.simpleBlendColorOnly = this.isSimpleBlendColorOnly();

        this.blendColor = new UIColor((c) -> this.applyColorEdit((color) ->
        {
            Color rgba = Color.rgba(c);

            color.set(rgba.r, rgba.g, rgba.b, rgba.a);
        })).withAlpha();
        this.blendColor.setColor(keyframe.getValue().getARGBColor());

        this.spectrum = new UIToggle(UIKeys.GENERIC_KEYFRAMES_COLOR_SPECTRUM, (b) -> this.setSpectrum(b.getValue()));
        this.spectrum.tooltip(UIKeys.GENERIC_KEYFRAMES_COLOR_SPECTRUM_TOOLTIP);
        this.spectrum.setValue(keyframe.isSpectrum());

        this.noShading = new UIToggle(UIKeys.FORMS_EDITORS_NOSHADING_SHADERS, (b) ->
        {
            if (this.fillingNoshading)
            {
                return;
            }

            this.setNoshadingOpacity(b.getValue());
        });
        this.noShading.tooltip(UIKeys.FORMS_EDITORS_COLOR_NOSHADING_OPACITY_TOOLTIP);
        this.noShading.setValue(keyframe.isNoshadingOpacity());

        this.scroll.add(UI.label(UIKeys.FILM_REPLAY_TRACK_COLOR).marginTop(4));

        if (!this.simpleBlendColorOnly)
        {
            this.blendTransform = new UIEffectTransformCollapse((apply) -> this.applyColorEdit((color) ->
            {
                if (color.transform == null)
                {
                    color.transform = new EffectTransform();
                }

                apply.accept(color.transform);
            }));
            this.blendTransform.registerUndo(editor);

            this.scroll.add(UIFormColorLayout.colorWithTransform(this.blendColor, this.blendTransform));
            this.scroll.add(this.spectrum.marginTop(8));
            this.scroll.add(this.noShading.marginTop(4));
        }
        else
        {
            this.scroll.add(this.blendColor);
            this.scroll.add(this.spectrum.marginTop(8));
            this.scroll.add(this.noShading.marginTop(4));
        }

        this.context((menu) ->
        {
            menu.action(Icons.CLOSE, UIKeys.FORMS_EDITORS_COLOR_RESET_ALL, this::resetAll);
            menu.action(Icons.REFRESH, UIKeys.FORMS_EDITORS_COLOR_RESET_BLEND, this::resetBlendColor);
        });

        this.update();
    }

    private Form getEditingForm()
    {
        if (this.editor == null)
        {
            return null;
        }

        UIKeyframeSheet sheet = this.editor.getGraph().getSheet(this.keyframe);

        if (sheet != null && sheet.property != null)
        {
            return FormUtils.getForm(sheet.property);
        }

        UIReplaysEditor replays = this.editor.getParent(UIReplaysEditor.class);

        if (replays == null || replays.getReplay() == null)
        {
            return null;
        }

        return replays.getReplay().form.get();
    }

    private boolean isSimpleBlendColorOnly()
    {
        return this.getEditingForm() instanceof LabelForm;
    }

    private void resetAll()
    {
        this.resetBlendColor();
    }

    private void resetBlendColor()
    {
        this.applyColorEdit((color) ->
        {
            color.set(1F, 1F, 1F, 1F);
            color.transform = new EffectTransform();
        });
        this.update();
    }

    @Override
    public void update()
    {
        super.update();

        this.syncLiveColorKeyframe();

        Color value = this.getOrCreateColor(this.keyframe.getValue());

        this.blendColor.setColor(value.getARGBColor());
        this.spectrum.setValue(this.keyframe.isSpectrum());

        this.fillingNoshading = true;

        try
        {
            this.noShading.setValue(this.keyframe.isNoshadingOpacity());
        }
        finally
        {
            this.fillingNoshading = false;
        }

        if (!this.simpleBlendColorOnly && this.blendTransform != null)
        {
            this.blendTransform.setEffectTransform(value.transform);
        }
    }

    private void setNoshadingOpacity(boolean value)
    {
        boolean[] applied = {false};

        UIReplaysEditorUtils.forEachSelectedKeyframe(this.editor, this.keyframe, (selected) ->
        {
            applied[0] = true;
            selected.setNoshadingOpacity(value);
        });

        if (!applied[0])
        {
            this.keyframe.setNoshadingOpacity(value);
        }
    }

    /**
     * {@link UIKeyframes#submitKeyframes()} replaces channel keyframe instances. Keep
     * {@link #keyframe} pointed at the live selected color keyframe so edits are not
     * read back from an orphaned copy.
     */
    @SuppressWarnings("unchecked")
    private void syncLiveColorKeyframe()
    {
        if (this.editor == null || this.keyframe == null)
        {
            return;
        }

        UIKeyframeSheet colorSheet = null;

        for (UIKeyframeSheet sheet : this.editor.getGraph().getSheets())
        {
            if (sheet.channel.getFactory() != KeyframeFactories.COLOR)
            {
                continue;
            }

            String name = StringUtils.fileName(sheet.id);

            if (!name.equals("color"))
            {
                continue;
            }

            colorSheet = sheet;
            break;
        }

        if (colorSheet == null)
        {
            return;
        }

        List selected = colorSheet.selection.getSelected();

        if (!selected.isEmpty())
        {
            this.keyframe = (Keyframe<Color>) selected.get(0);

            return;
        }

        float tick = this.keyframe.getTick();

        for (Object kfObj : colorSheet.channel.getKeyframes())
        {
            Keyframe<?> kf = (Keyframe<?>) kfObj;

            if (Math.abs(kf.getTick() - tick) < 0.001F && kf.getValue() instanceof Color)
            {
                this.keyframe = (Keyframe<Color>) kf;

                return;
            }
        }
    }

    private Color getOrCreateColor(Color color)
    {
        if (color == null)
        {
            color = Color.white();
        }

        if (color.transform == null)
        {
            color.transform = new EffectTransform();
        }

        return color;
    }

    @SuppressWarnings("unchecked")
    private void applyColorEdit(Consumer<Color> editor)
    {
        this.syncLiveColorKeyframe();

        boolean[] applied = {false};

        UIReplaysEditorUtils.forEachSelectedKeyframe(this.editor, this.keyframe, (selected) ->
        {
            applied[0] = true;
            this.keyframe = (Keyframe<Color>) (Keyframe<?>) selected;

            Color color = this.getOrCreateColor((Color) selected.getValue());

            selected.preNotify();
            editor.accept(color);
            selected.postNotify();
        });

        if (!applied[0])
        {
            Color color = this.getOrCreateColor(this.keyframe.getValue());

            this.keyframe.preNotify();
            editor.accept(color);
            this.keyframe.postNotify();
        }
    }

    private void setSpectrum(boolean value)
    {
        boolean[] applied = {false};

        UIReplaysEditorUtils.forEachSelectedKeyframe(this.editor, this.keyframe, (selected) ->
        {
            applied[0] = true;
            selected.setSpectrum(value);
        });

        if (!applied[0])
        {
            this.keyframe.setSpectrum(value);
        }
    }
}
