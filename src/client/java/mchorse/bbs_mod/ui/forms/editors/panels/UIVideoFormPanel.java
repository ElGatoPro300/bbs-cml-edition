package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.forms.VideoForm;
import mchorse.bbs_mod.forms.forms.utils.EffectTransform;
import mchorse.bbs_mod.forms.forms.utils.GlowSettings;
import mchorse.bbs_mod.forms.forms.utils.PaintSettings;
import mchorse.bbs_mod.forms.forms.utils.VideoResolution;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIFormColorAdjustments;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIFormColorLayout;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIFormColorTransform;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIFormPaintTransform;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIVideoOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Color;

import java.io.File;

public class UIVideoFormPanel extends UIFormPanel<VideoForm>
{
    public UIButton pick;
    public UIToggle billboard;
    public UIToggle linear;
    public UIToggle loop;
    public UIToggle paused;
    public UICirculate resolution;
    public UITrackpad speed;
    public UITrackpad time;

    public UIColor color;
    public UIFormColorTransform colorTransform;
    public UIFormColorAdjustments colorAdjustments;
    public UIColor paintColor;
    public UITrackpad paintIntensity;
    public UIFormPaintTransform paintTransform;
    public UIColor glowingColor;
    public UITrackpad glowIntensity;
    public UIFormColorTransform glowTransform;
    public UIElement glowSection;

    public UIVideoFormPanel(UIForm editor)
    {
        super(editor);

        this.pick = new UIButton(UIKeys.FORMS_EDITORS_VIDEO_PICK_VIDEO, (b) ->
        {
            UIVideoOverlayPanel panel = new UIVideoOverlayPanel((value) ->
            {
                String next = value == null ? "" : value;

                if (next.equals(UIKeys.GENERAL_NONE.get()) || next.equalsIgnoreCase("none"))
                {
                    next = "";
                }

                this.form.video.set(next);
                this.refreshPickLabel();
            }, this.getContext());

            UIOverlay.addOverlay(this.getContext(), panel.set(this.form.video.get()));
        });
        this.billboard = new UIToggle(UIKeys.FORMS_EDITORS_VIDEO_BILLBOARD, false, (b) -> this.form.billboard.set(b.getValue()));
        this.linear = new UIToggle(UIKeys.TEXTURES_LINEAR, true, (b) -> this.form.linear.set(b.getValue()));
        this.loop = new UIToggle(UIKeys.FORMS_EDITORS_VIDEO_LOOP, true, (b) -> this.form.loop.set(b.getValue()));
        this.paused = new UIToggle(UIKeys.FORMS_EDITORS_VIDEO_PAUSED, false, (b) -> this.form.paused.set(b.getValue()));
        this.paused.tooltip(UIKeys.FORMS_EDITORS_VIDEO_PAUSED_TOOLTIP);
        this.resolution = new UICirculate((b) ->
        {
            this.form.resolution.set(VideoResolution.fromIndex(this.resolution.getValue()));
        });
        this.resolution.addLabel(UIKeys.FORMS_EDITORS_VIDEO_RESOLUTION_NATIVE);
        this.resolution.addLabel(UIKeys.FORMS_EDITORS_VIDEO_RESOLUTION_1080);
        this.resolution.addLabel(UIKeys.FORMS_EDITORS_VIDEO_RESOLUTION_720);
        this.resolution.addLabel(UIKeys.FORMS_EDITORS_VIDEO_RESOLUTION_480);
        this.resolution.addLabel(UIKeys.FORMS_EDITORS_VIDEO_RESOLUTION_360);
        this.resolution.addLabel(UIKeys.FORMS_EDITORS_VIDEO_RESOLUTION_240);
        this.resolution.tooltip(UIKeys.FORMS_EDITORS_VIDEO_RESOLUTION_TOOLTIP);
        this.speed = new UITrackpad((value) -> this.form.speed.set(value.floatValue()));
        this.speed.limit(0.01D, 8D).values(0.25D, 0.05D, 1D);
        this.speed.tooltip(UIKeys.FORMS_EDITORS_VIDEO_SPEED_TOOLTIP);
        this.time = new UITrackpad((value) -> this.form.time.set(value.intValue()));
        this.time.integer().limit(0D, Integer.MAX_VALUE);
        this.time.tooltip(UIKeys.FORMS_EDITORS_VIDEO_TIME_TOOLTIP);

        this.color = new UIColor((value) ->
        {
            Color color = this.form.color.get().copy();
            Color next = Color.rgba(value);

            color.set(next.r, next.g, next.b, next.a);
            this.form.color.set(color);
        }).direction(Direction.LEFT).withAlpha();
        this.colorTransform = new UIFormColorTransform(() -> this.form.color.get(), (color) -> this.form.color.set(color));
        this.colorAdjustments = new UIFormColorAdjustments(() -> this.form.color.get(), (color) ->
        {
            this.form.color.setRuntimeValue(null);
            this.form.color.set(color);
        });
        this.paintColor = new UIColor((value) ->
        {
            Color color = Color.rgba(value);
            PaintSettings settings = this.form.paintSettings.get().copy();

            color.a = settings.intensity;
            this.form.paintColor.set(color);

            settings.r = color.r;
            settings.g = color.g;
            settings.b = color.b;
            settings.applyAutoShaderShadow();
            this.form.paintSettings.set(settings);
        }).direction(Direction.LEFT);
        this.paintColor.tooltip(UIKeys.FORMS_EDITORS_PAINT_COLOR);
        this.paintIntensity = new UITrackpad((value) ->
        {
            PaintSettings settings = this.form.paintSettings.get().copy();
            float intensity = PaintSettings.clampIntensity(value.floatValue());

            settings.intensity = intensity;
            settings.applyAutoShaderShadow();
            this.form.paintSettings.set(settings);

            Color legacy = this.form.paintColor.get().copy();

            legacy.a = intensity;
            this.form.paintColor.set(legacy);
        });
        this.paintIntensity.increment(0.05D).values(0.1D, 0.05D, 0.2D).limit(PaintSettings.MIN_INTENSITY, PaintSettings.MAX_INTENSITY);
        this.paintIntensity.tooltip(UIKeys.FORMS_EDITORS_PAINT_INTENSITY);
        this.paintTransform = new UIFormPaintTransform(() -> this.form.paintSettings.get(), (settings) -> this.form.paintSettings.set(settings));
        this.glowingColor = new UIColor((value) ->
        {
            Color copy = this.form.glowingColor.get().copy();
            Color color = Color.rgba(value);

            copy.r = color.r;
            copy.g = color.g;
            copy.b = color.b;
            copy.a = 1F;
            this.form.glowingColor.set(copy);

            GlowSettings settings = this.form.glowSettings.get().copy();

            settings.r = copy.r;
            settings.g = copy.g;
            settings.b = copy.b;
            this.form.glowSettings.set(settings);
        }).direction(Direction.LEFT);
        this.glowingColor.tooltip(UIKeys.FORMS_EDITORS_GLOW);
        this.glowIntensity = new UITrackpad((value) ->
        {
            GlowSettings settings = this.form.glowSettings.get().copy();

            settings.intensity = value.floatValue();
            this.form.glowSettings.set(settings);
        });
        this.glowIntensity.increment(0.05D).values(0.1D, 0.05D, 0.2D);
        this.glowIntensity.tooltip(UIKeys.FORMS_EDITORS_GLOW_INTENSITY);
        this.glowTransform = new UIFormColorTransform(() -> this.form.glowingColor.get(), (color) ->
        {
            this.form.glowingColor.set(color);

            GlowSettings settings = this.form.glowSettings.get().copy();

            settings.transform = color.transform == null ? new EffectTransform() : color.transform.copy();
            this.form.glowSettings.set(settings);
        });
        this.glowSection = UIFormColorLayout.createGlowSection(this.glowingColor, this.glowIntensity, this.glowTransform);

        this.options.add(
            this.pick,
            UIFormColorLayout.sectionLabel(UIKeys.FORMS_EDITOR_FORM),
            UIFormColorLayout.colorWithTransform(this.color, this.colorTransform),
            UIFormColorLayout.createExtraSection(
                this.glowSection,
                UIFormColorLayout.paintColorRowWithTransform(this.paintColor, this.paintIntensity, this.paintTransform),
                this.colorAdjustments.marginTop(4)
            ).marginTop(4),
            this.billboard,
            this.linear,
            this.loop,
            this.paused
        );
        this.options.add(UI.label(UIKeys.FORMS_EDITORS_VIDEO_RESOLUTION).marginTop(8), this.resolution);
        this.options.add(UI.label(UIKeys.FORMS_EDITORS_VIDEO_SPEED).marginTop(8), this.speed);
        this.options.add(UI.label(UIKeys.FORMS_EDITORS_VIDEO_TIME).marginTop(8), this.time);
    }

    @Override
    public void startEdit(VideoForm form)
    {
        super.startEdit(form);

        this.billboard.setValue(form.billboard.get());
        this.linear.setValue(form.linear.get());
        this.loop.setValue(form.loop.get());
        this.paused.setValue(form.paused.get());
        this.resolution.setValue(VideoResolution.indexOf(form.resolution.get()));
        this.speed.setValue(form.speed.get());
        this.time.setValue(form.time.get());

        this.color.setColor(form.color.get().getARGBColor());
        this.colorTransform.syncFromForm();
        this.colorAdjustments.syncFromForm();
        PaintSettings paint = form.paintSettings.get();
        Color paintDisplay = new Color();

        paint.resolveColor(form.paintColor.get(), paintDisplay);
        this.paintColor.setColor(paintDisplay.getRGBColor());
        this.paintIntensity.setValue(paint.intensity);
        this.paintTransform.syncFromForm();
        GlowSettings glow = form.glowSettings.get();
        Color glowDisplay = new Color();

        glow.resolveColor(form.glowingColor.get(), glowDisplay);
        this.glowingColor.setColor(glowDisplay.getRGBColor());
        this.glowIntensity.setValue(glow.intensity);
        this.glowTransform.syncFromForm();
        this.refreshPickLabel();
    }

    private void refreshPickLabel()
    {
        String path = this.form == null ? "" : this.form.video.get();

        if (path == null || path.isEmpty())
        {
            this.pick.label = UIKeys.FORMS_EDITORS_VIDEO_PICK_VIDEO;

            return;
        }

        String name = path;

        if (path.startsWith("external:"))
        {
            name = new File(path.substring("external:".length())).getName();
        }
        else
        {
            int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));

            if (slash >= 0 && slash + 1 < path.length())
            {
                name = path.substring(slash + 1);
            }
        }

        this.pick.label = IKey.constant(name);
    }
}
