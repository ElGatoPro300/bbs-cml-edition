package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.forms.forms.BlockForm;
import mchorse.bbs_mod.forms.forms.utils.EffectTransform;
import mchorse.bbs_mod.forms.forms.utils.GlowSettings;
import mchorse.bbs_mod.forms.forms.utils.PaintSettings;
import mchorse.bbs_mod.forms.forms.utils.StructureLightSettings;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIBlockStateEditor;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIFormColorAdjustments;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIFormColorLayout;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIFormColorTransform;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIFormPaintTransform;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIListOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.colors.Color;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;

import java.util.ArrayList;
import java.util.List;

public class UIBlockFormPanel extends UIFormPanel<BlockForm>
{
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
    public UIBlockStateEditor stateEditor;
    public UIButton pickBiome;
    public UIToggle toggleLight;
    public UITrackpad lightIntensity;
    public UITrackpad breaking;
    public UITrackpad repeatX;
    public UITrackpad repeatY;
    public UITrackpad repeatZ;
    public UIToggle repeatCenterX;
    public UIToggle repeatCenterY;
    public UIToggle repeatCenterZ;
    public UIToggle cullFluid;
    public UIToggle outerFluidWalls;
    public UIToggle interactBlocks;
    public UIToggle noShading;

    public UIBlockFormPanel(UIForm editor)
    {
        super(editor);

        this.color = new UIColor((c) ->
        {
            Color color = this.form.color.get().copy();
            Color value = Color.rgba(c);

            color.set(value.r, value.g, value.b, value.a);
            this.form.color.set(color);
        }).withAlpha();
        this.colorTransform = new UIFormColorTransform(() -> this.form.color.get(), (color) -> this.form.color.set(color));
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
        this.stateEditor = new UIBlockStateEditor((blockState) -> this.form.blockState.set(blockState));
        this.pickBiome = new UIButton(UIKeys.FORMS_EDITORS_STRUCTURE_PICK_BIOME, (b) -> this.pickBiome());
        this.toggleLight = new UIToggle(UIKeys.FORMS_EDITORS_STRUCTURE_LIGHT, false, (t) -> this.toggleLight(t));
        this.lightIntensity = new UITrackpad((v) -> this.setLightIntensity(v.intValue()))
                .integer()
                .limit(1D, 15D);
        this.breaking = new UITrackpad((v) -> this.form.breaking.set(v.intValue())).integer().limit(0, 10);
        this.breaking.tooltip(UIKeys.FORMS_EDITORS_BLOCK_BREAKING);
        this.repeatX = new UITrackpad((v) -> this.form.repeatX.set(v.intValue())).integer().limit(1, 64);
        this.repeatX.tooltip(UIKeys.FORMS_EDITORS_BLOCK_REPEAT_X);
        this.repeatY = new UITrackpad((v) -> this.form.repeatY.set(v.intValue())).integer().limit(1, 64);
        this.repeatY.tooltip(UIKeys.FORMS_EDITORS_BLOCK_REPEAT_Y);
        this.repeatZ = new UITrackpad((v) -> this.form.repeatZ.set(v.intValue())).integer().limit(1, 64);
        this.repeatZ.tooltip(UIKeys.FORMS_EDITORS_BLOCK_REPEAT_Z);
        this.repeatCenterX = new UIToggle(UIKeys.FORMS_EDITORS_BLOCK_REPEAT_CENTER_X, (b) -> this.form.repeatCenterX.set(b.getValue()));
        this.repeatCenterX.tooltip(UIKeys.FORMS_EDITORS_BLOCK_REPEAT_CENTER_X_TOOLTIP);
        this.repeatCenterY = new UIToggle(UIKeys.FORMS_EDITORS_BLOCK_REPEAT_CENTER_Y, (b) -> this.form.repeatCenterY.set(b.getValue()));
        this.repeatCenterY.tooltip(UIKeys.FORMS_EDITORS_BLOCK_REPEAT_CENTER_Y_TOOLTIP);
        this.repeatCenterZ = new UIToggle(UIKeys.FORMS_EDITORS_BLOCK_REPEAT_CENTER_Z, (b) -> this.form.repeatCenterZ.set(b.getValue()));
        this.repeatCenterZ.tooltip(UIKeys.FORMS_EDITORS_BLOCK_REPEAT_CENTER_Z_TOOLTIP);
        this.cullFluid = new UIToggle(UIKeys.FORMS_EDITORS_BLOCK_CULL_FLUID, true, (b) -> this.form.cullFluid.set(b.getValue()));
        this.cullFluid.tooltip(UIKeys.FORMS_EDITORS_BLOCK_CULL_FLUID_TOOLTIP);
        this.outerFluidWalls = new UIToggle(UIKeys.FORMS_EDITORS_BLOCK_OUTER_FLUID_WALLS, false, (b) -> this.form.outerFluidWalls.set(b.getValue()));
        this.outerFluidWalls.tooltip(UIKeys.FORMS_EDITORS_BLOCK_OUTER_FLUID_WALLS_TOOLTIP);
        this.interactBlocks = new UIToggle(UIKeys.FORMS_EDITORS_BLOCK_INTERACT_BLOCKS, false, (b) -> this.form.interactBlocks.set(b.getValue()));
        this.interactBlocks.tooltip(UIKeys.FORMS_EDITORS_BLOCK_INTERACT_BLOCKS_TOOLTIP);
        this.noShading = new UIToggle(UIKeys.FORMS_EDITORS_NOSHADING_SHADERS, (b) -> this.form.noshadingOpacity.set(b.getValue()));
        this.noShading.tooltip(UIKeys.FORMS_EDITORS_COLOR_NOSHADING_OPACITY_TOOLTIP);

        this.options.add(
            UIFormColorLayout.sectionLabel(UIKeys.FORMS_EDITOR_FORM),
            UIFormColorLayout.colorWithTransform(this.color, this.colorTransform),
            UIFormColorLayout.createExtraSection(
                this.glowSection,
                UIFormColorLayout.paintColorRowWithTransform(this.paintColor, this.paintIntensity, this.paintTransform),
                this.colorAdjustments.marginTop(4)
            ).marginTop(4),
            this.stateEditor
        );
        this.options.add(this.pickBiome);
        this.options.add(this.toggleLight);
        this.options.add(UI.label(UIKeys.FORMS_EDITORS_STRUCTURE_LIGHT_INTENSITY_LABEL).marginTop(6), this.lightIntensity);
        this.options.add(UI.label(UIKeys.FORMS_EDITORS_BLOCK_REPEAT).marginTop(6), UI.row(this.repeatX, this.repeatY, this.repeatZ));
        this.options.add(UI.label(UIKeys.FORMS_EDITORS_BLOCK_REPEAT_CENTER).marginTop(6), UI.row(this.repeatCenterX, this.repeatCenterY, this.repeatCenterZ));
        this.options.add(this.noShading);
        this.options.add(this.cullFluid.marginTop(6));
        this.options.add(this.outerFluidWalls.marginTop(4));
        this.options.add(this.interactBlocks.marginTop(4));
        this.options.add(UI.label(UIKeys.FORMS_EDITORS_BLOCK_BREAKING).marginTop(6), this.breaking);
    }

    private void toggleLight(UIToggle t)
    {
        StructureLightSettings settings = this.form.structureLight.get();
        boolean enabled = t.getValue();

        if (settings == null)
        {
            settings = new StructureLightSettings();
        }
        else
        {
            settings = settings.copy();
        }

        settings.enabled = enabled;

        this.form.structureLight.set(settings);
        this.form.emitLight.set(enabled);
    }

    private void setLightIntensity(int intensity)
    {
        StructureLightSettings settings = this.form.structureLight.get();

        if (settings == null)
        {
            settings = new StructureLightSettings();
        }
        else
        {
            settings = settings.copy();
        }

        settings.intensity = intensity;

        this.form.structureLight.set(settings);
        this.form.lightIntensity.set(intensity);
    }

    private void pickBiome()
    {
        UIListOverlayPanel overlay = new UIListOverlayPanel(UIKeys.FORMS_EDITORS_STRUCTURE_PICK_BIOME, (value) ->
        {
            String id = value == null ? "" : value;

            this.form.biomeId.set(id);
        });

        List<String> ids = new ArrayList<>();

        try
        {
            if (MinecraftClient.getInstance().world != null)
            {
                Registry<Biome> reg = MinecraftClient.getInstance().world.getRegistryManager().get(RegistryKeys.BIOME);

                for (Identifier id : reg.getIds())
                {
                    ids.add(id.toString());
                }
            }
        }
        catch (Throwable ignored)
        {
        }

        overlay.addValues(ids);
        overlay.setValue(this.form.biomeId.get());
        UIOverlay.addOverlay(this.getContext(), overlay, 280, 0.5F);
    }

    @Override
    public void startEdit(BlockForm form)
    {
        super.startEdit(form);

        BlockState blockState = this.form.blockState.get();

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
        this.stateEditor.setBlockState(blockState);
        this.breaking.setValue(form.breaking.get());
        this.repeatX.setValue(form.repeatX.get());
        this.repeatY.setValue(form.repeatY.get());
        this.repeatZ.setValue(form.repeatZ.get());
        this.repeatCenterX.setValue(form.repeatCenterX.get());
        this.repeatCenterY.setValue(form.repeatCenterY.get());
        this.repeatCenterZ.setValue(form.repeatCenterZ.get());
        this.noShading.setValue(form.noshadingOpacity.get());
        this.cullFluid.setValue(form.cullFluid.get());
        this.outerFluidWalls.setValue(form.outerFluidWalls.get());
        this.interactBlocks.setValue(form.interactBlocks.get());

        StructureLightSettings s = form.structureLight.get();
        boolean enabled = (s != null) ? s.enabled : form.emitLight.get();
        int intensity = (s != null) ? s.intensity : form.lightIntensity.get();

        this.toggleLight.setValue(enabled);
        this.lightIntensity.setValue((double) intensity);
    }
}