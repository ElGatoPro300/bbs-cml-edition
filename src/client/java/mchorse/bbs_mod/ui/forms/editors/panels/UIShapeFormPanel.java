package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.forms.ShapeForm;
import mchorse.bbs_mod.forms.forms.utils.EffectTransform;
import mchorse.bbs_mod.forms.forms.utils.GlowSettings;
import mchorse.bbs_mod.forms.forms.utils.PaintSettings;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.panels.shape.UIShapeNodeEditor;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIFormColorAdjustments;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIFormColorLayout;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIFormColorTransform;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIFormPaintTransform;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Color;

public class UIShapeFormPanel extends UIFormPanel<ShapeForm>
{
    public UIShapeNodeEditor nodeEditor;

    public UICirculate type;
    public UIIcon toggleNodeEditor;
    public UITrackpad sizeX;
    public UITrackpad sizeY;
    public UITrackpad sizeZ;
    public UITrackpad subdivisions;
    
    public UIButton pickTexture;
    public UIColor color;
    public UIFormColorAdjustments colorAdjustments;
    public UIColor paintColor;
    public UITrackpad paintIntensity;
    public UIFormPaintTransform paintTransform;
    public UIColor glowingColor;
    public UITrackpad glowIntensity;
    public UIFormColorTransform glowTransform;
    public UIElement glowSection;
    public UITrackpad textureScale;
    public UITrackpad textureScrollX;
    public UITrackpad textureScrollY;
    public UIToggle lighting;

    public UIToggle particles;
    public UICirculate particleType;
    public UITrackpad particleScale;
    public UITrackpad particleDensity;
    public UITrackpad particleSize;

    public UIShapeFormPanel(UIForm editor)
    {
        super(editor);

        this.nodeEditor = new UIShapeNodeEditor();
        this.nodeEditor.relative(this).w(1F).h(1F).setVisible(true);
        this.prepend(this.nodeEditor);

        /* Geometry */
        this.toggleNodeEditor = new UIIcon(Icons.GRAPH, (b) -> this.nodeEditor.toggleVisible());
        this.toggleNodeEditor.tooltip(UIKeys.RAW_TOGGLE_NODE_EDITOR);

        this.type = new UICirculate((b) -> this.form.type.set(ShapeForm.ShapeType.values()[b.getValue()]));
        for (ShapeForm.ShapeType type : ShapeForm.ShapeType.values())
        {
            IKey label = IKey.raw(type.name());

            if (type == ShapeForm.ShapeType.BOX) label = UIKeys.FORMS_EDITORS_SHAPE_TYPE_BOX;
            else if (type == ShapeForm.ShapeType.SPHERE) label = UIKeys.FORMS_EDITORS_SHAPE_TYPE_SPHERE;
            else if (type == ShapeForm.ShapeType.CYLINDER) label = UIKeys.FORMS_EDITORS_SHAPE_TYPE_CYLINDER;
            else if (type == ShapeForm.ShapeType.CAPSULE) label = UIKeys.FORMS_EDITORS_SHAPE_TYPE_CAPSULE;

            this.type.addLabel(label);
        }
        
        this.sizeX = new UITrackpad((value) -> this.form.sizeX.set(value.floatValue()));
        this.sizeX.tooltip(UIKeys.FORMS_EDITORS_SHAPE_SIZE_X);
        
        this.sizeY = new UITrackpad((value) -> this.form.sizeY.set(value.floatValue()));
        this.sizeY.tooltip(UIKeys.FORMS_EDITORS_SHAPE_SIZE_Y);
        
        this.sizeZ = new UITrackpad((value) -> this.form.sizeZ.set(value.floatValue()));
        this.sizeZ.tooltip(UIKeys.FORMS_EDITORS_SHAPE_SIZE_Z);
        
        this.subdivisions = new UITrackpad((value) -> this.form.subdivisions.set(value.intValue()));
        this.subdivisions.integer();
        this.subdivisions.tooltip(UIKeys.FORMS_EDITORS_SHAPE_SUBDIVISIONS);

        /* Appearance */
        this.pickTexture = new UIButton(UIKeys.FORMS_EDITORS_SHAPE_PICK_TEXTURE, (b) ->
        {
            UITexturePicker.open(this.getContext(), this.form.texture.get(), (l) -> this.form.texture.set(l));
        });
        
        this.color = new UIColor((value) ->
        {
            Color color = this.form.color.get().copy();
            Color next = Color.rgba(value);

            color.set(next.r, next.g, next.b, next.a);
            this.form.color.set(color);
        }).direction(Direction.LEFT).withAlpha();
        this.colorAdjustments = new UIFormColorAdjustments(() -> this.form.color.get(), (color) ->
        {
            this.form.color.setRuntimeValue(null);
            this.form.color.set(color);
        });

        this.paintColor = new UIColor((c) ->
        {
            Color color = Color.rgba(c);
            PaintSettings settings = this.form.paintSettings.get().copy();

            color.a = settings.intensity;
            this.form.paintColor.set(color);

            settings.r = color.r;
            settings.g = color.g;
            settings.b = color.b;
            settings.applyAutoShaderShadow();
            this.form.paintSettings.set(settings);
        });
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
        this.glowingColor = new UIColor((c) ->
        {
            Color copy = this.form.glowingColor.get().copy();
            Color color = Color.rgba(c);

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
        });
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

        this.textureScale = new UITrackpad((value) -> this.form.textureScale.set(value.floatValue()));
        this.textureScale.tooltip(UIKeys.FORMS_EDITORS_SHAPE_TEXTURE_SCALE);
        
        this.textureScrollX = new UITrackpad((value) -> this.form.textureScrollX.set(value.floatValue()));
        this.textureScrollX.tooltip(UIKeys.FORMS_EDITORS_SHAPE_TEXTURE_SCROLL_X);
        
        this.textureScrollY = new UITrackpad((value) -> this.form.textureScrollY.set(value.floatValue()));
        this.textureScrollY.tooltip(UIKeys.FORMS_EDITORS_SHAPE_TEXTURE_SCROLL_Y);
        
        this.lighting = new UIToggle(UIKeys.RAW_ADDITIVE, (b) -> this.form.lighting.set(b.getValue()));
        this.lighting.tooltip(UIKeys.RAW_ENABLES_ADDITIVE_BLENDING_GLOWING);

        /* Particles */
        this.particles = new UIToggle(UIKeys.RAW_PARTICLES, (b) ->
        {
            this.form.particles.set(b.getValue());
            this.updateParticleVisibility();
        });

        this.particleType = new UICirculate((b) -> this.form.particleType.set(ShapeForm.ParticleType.values()[b.getValue()]));
        for (ShapeForm.ParticleType type : ShapeForm.ParticleType.values())
        {
            this.particleType.addLabel(IKey.raw(type.name()));
        }

        this.particleScale = new UITrackpad((value) -> this.form.particleScale.set(value.floatValue()));
        this.particleScale.tooltip(UIKeys.RAW_PARTICLE_SCALE);

        this.particleDensity = new UITrackpad((value) -> this.form.particleDensity.set(value.floatValue()));
        this.particleDensity.tooltip(UIKeys.RAW_PARTICLE_DENSITY);

        this.particleSize = new UITrackpad((value) -> this.form.particleSize.set(value.floatValue()));
        this.particleSize.tooltip(UIKeys.RAW_PARTICLE_SIZE);

        /* Layout */
        this.options.add(UI.label(UIKeys.FORMS_EDITORS_SHAPE_GEOMETRY).marginTop(8), UI.row(this.type, this.toggleNodeEditor), UI.row(this.sizeX, this.sizeY, this.sizeZ), this.subdivisions);
        this.options.add(
            UI.label(UIKeys.FORMS_EDITORS_SHAPE_APPEARANCE).marginTop(8),
            this.pickTexture,
            UIFormColorLayout.sectionLabel(UIKeys.FORMS_EDITOR_FORM),
            this.color,
            UIFormColorLayout.createExtraSection(
                this.glowSection,
                UIFormColorLayout.paintColorRowWithTransform(this.paintColor, this.paintIntensity, this.paintTransform),
                this.colorAdjustments.marginTop(4)
            ).marginTop(4),
            this.textureScale,
            UI.row(this.textureScrollX, this.textureScrollY),
            this.lighting
        );
        this.options.add(UI.label(UIKeys.RAW_PARTICLES).marginTop(8), this.particles, this.particleType, this.particleScale, this.particleDensity, this.particleSize);
    }

    private void updateParticleVisibility()
    {
        boolean visible = this.particles.getValue();
        
        this.particleType.setVisible(visible);
        this.particleScale.setVisible(visible);
        this.particleDensity.setVisible(visible);
        this.particleSize.setVisible(visible);
    }

    @Override
    public void resize()
    {
        super.resize();

        if (this.nodeEditor != null && this.options != null)
        {
            this.nodeEditor.w(this.options.area.x - this.area.x);
            this.nodeEditor.resize();
        }
    }

    @Override
    public void startEdit(ShapeForm form)
    {
        super.startEdit(form);

        this.nodeEditor.setVisible(true);
        this.nodeEditor.setGraph(form.graph.get());
        this.nodeEditor.setValue(form.graph);

        this.type.setValue(form.type.get().ordinal());
        this.sizeX.setValue(form.sizeX.get());
        this.sizeY.setValue(form.sizeY.get());
        this.sizeZ.setValue(form.sizeZ.get());
        this.subdivisions.setValue(form.subdivisions.get());

        this.color.setColor(form.color.get().getARGBColor());
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
        this.textureScale.setValue(form.textureScale.get());
        this.textureScrollX.setValue(form.textureScrollX.get());
        this.textureScrollY.setValue(form.textureScrollY.get());
        this.lighting.setValue(form.lighting.get());
        
        this.particles.setValue(form.particles.get());
        this.particleType.setValue(form.particleType.get().ordinal());
        this.particleScale.setValue(form.particleScale.get());
        this.particleDensity.setValue(form.particleDensity.get());
        this.particleSize.setValue(form.particleSize.get());
        this.updateParticleVisibility();
    }
}
