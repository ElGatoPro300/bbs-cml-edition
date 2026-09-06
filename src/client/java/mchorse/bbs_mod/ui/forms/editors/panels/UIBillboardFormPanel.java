package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forms.forms.BillboardForm;
import mchorse.bbs_mod.forms.forms.utils.EffectTransform;
import mchorse.bbs_mod.forms.forms.utils.GlowSettings;
import mchorse.bbs_mod.forms.forms.utils.PaintSettings;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIFormColorAdjustments;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIFormColorLayout;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIFormColorTransform;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIFormPaintTransform;
import mchorse.bbs_mod.ui.forms.editors.utils.UICropOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Color;

public class UIBillboardFormPanel extends UIFormPanel<BillboardForm>
{
    public UIButton pick;
    public UIToggle billboard;
    public UIToggle linear;
    public UIToggle mipmap;

    public UIButton openCrop;
    public UIToggle resizeCrop;
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

    public UITrackpad offsetX;
    public UITrackpad offsetY;
    public UITrackpad rotation;

    public UIToggle shading;
    public UITrackpad pbrNormalIntensity;
    public UITrackpad pbrSpecularIntensity;
    public UITrackpad subdivision;

    public UIBillboardFormPanel(UIForm editor)
    {
        super(editor);

        this.pick = new UIButton(UIKeys.FORMS_EDITORS_BILLBOARD_PICK_TEXTURE, (b) ->
        {
            UITexturePicker.open(this.getContext(), this.form.texture.get(), (l) -> this.form.texture.set(l));
        });
        this.billboard = new UIToggle(UIKeys.FORMS_EDITORS_BILLBOARD_TITLE, false, (b) -> this.form.billboard.set(b.getValue()));
        this.linear = new UIToggle(UIKeys.TEXTURES_LINEAR, false, (b) -> this.form.linear.set(b.getValue()));
        this.mipmap = new UIToggle(UIKeys.TEXTURES_MIPMAP, false, (b) -> this.form.mipmap.set(b.getValue()));
        this.openCrop = new UIButton(UIKeys.FORMS_EDITORS_BILLBOARD_EDIT_CROP, (b) ->
        {
            UIOverlay.addOverlay(this.getContext(), new UICropOverlayPanel(this.form.texture.get(), this.form.crop.get()), 0.5F, 0.5F);
        });
        this.resizeCrop = new UIToggle(UIKeys.FORMS_EDITORS_BILLBOARD_RESIZE_CROP, false, (b) -> this.form.resizeCrop.set(b.getValue()));
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

        this.offsetX = new UITrackpad((value) -> this.form.offsetX.set(value.floatValue()));
        this.offsetX.tooltip(UIKeys.FORMS_EDITORS_BILLBOARD_OFFSET_X);
        this.offsetY = new UITrackpad((value) -> this.form.offsetY.set(value.floatValue()));
        this.offsetY.tooltip(UIKeys.FORMS_EDITORS_BILLBOARD_OFFSET_Y);
        this.rotation = new UITrackpad((value) -> this.form.rotation.set(value.floatValue()));
        this.rotation.tooltip(UIKeys.FORMS_EDITORS_BILLBOARD_ROTATION);

        this.shading = new UIToggle(UIKeys.FORMS_EDITORS_BILLBOARD_SHADING, false, (b) -> this.form.shading.set(b.getValue()));
        this.pbrNormalIntensity = new UITrackpad((value) -> this.form.pbrNormalIntensity.set(value.floatValue()));
        this.pbrNormalIntensity.tooltip(UIKeys.FORMS_EDITOR_MODEL_PBR_NORMAL_INTENSITY);
        this.pbrSpecularIntensity = new UITrackpad((value) -> this.form.pbrSpecularIntensity.set(value.floatValue()));
        this.pbrSpecularIntensity.tooltip(UIKeys.FORMS_EDITOR_MODEL_PBR_SPECULAR_INTENSITY);
        this.subdivision = new UITrackpad((value) -> this.form.subdivision.set(value.floatValue()));
        this.subdivision.values(0.1D, 0.05D, 0.5D).limit(0D, 16D);
        this.subdivision.tooltip(UIKeys.FORMS_EDITORS_BILLBOARD_SUBDIVISION);

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
            this.mipmap,
            this.subdivision
        );

        if (BBSSettings.modelPbrPanelControls != null && BBSSettings.modelPbrPanelControls.get())
        {
            this.options.add(this.pbrNormalIntensity, this.pbrSpecularIntensity);
        }

        this.options.add(UI.label(UIKeys.FORMS_EDITORS_BILLBOARD_CROP).marginTop(8), this.openCrop, this.resizeCrop);
        this.options.add(UI.label(UIKeys.FORMS_EDITORS_BILLBOARD_UV_SHIFT).marginTop(8), UI.row(this.offsetX, this.offsetY), this.rotation, this.shading);
    }

    @Override
    public void startEdit(BillboardForm form)
    {
        super.startEdit(form);

        this.billboard.setValue(form.billboard.get());
        this.linear.setValue(form.linear.get());
        this.mipmap.setValue(form.mipmap.get());

        this.resizeCrop.setValue(form.resizeCrop.get());
        this.color.setColor(form.color.get().getARGBColor());
        this.colorTransform.syncFromForm();
        this.colorAdjustments.prepareSession();
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

        this.offsetX.setValue(form.offsetX.get());
        this.offsetY.setValue(form.offsetY.get());
        this.rotation.setValue(form.rotation.get());

        this.shading.setValue(form.shading.get());
        this.pbrNormalIntensity.setValue(form.pbrNormalIntensity.get());
        this.pbrSpecularIntensity.setValue(form.pbrSpecularIntensity.get());
        this.subdivision.setValue(form.subdivision.get());
    }
}
