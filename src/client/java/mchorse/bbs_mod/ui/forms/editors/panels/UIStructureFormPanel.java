package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.forms.forms.StructureForm;
import mchorse.bbs_mod.forms.forms.utils.EffectTransform;
import mchorse.bbs_mod.forms.forms.utils.GlowSettings;
import mchorse.bbs_mod.forms.forms.utils.PaintSettings;
import mchorse.bbs_mod.forms.forms.utils.StructureLightSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIFormColorAdjustments;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIFormColorLayout;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIFormColorTransform;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIFormPaintTransform;
import mchorse.bbs_mod.ui.forms.editors.utils.UIStructureOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIListOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Color;

import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;

import java.util.ArrayList;
import java.util.List;

public class UIStructureFormPanel extends UIFormPanel<StructureForm>
{
    public UIButton pickStructure;
    public UIButton pickBiome;
    public UITextbox structureFile;
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
    public UIToggle toggleLight;
    public UITrackpad lightIntensity;
    public UITrackpad scaleX;
    public UITrackpad scaleY;
    public UITrackpad scaleZ;
    public UIToggle toggleFluid;
    /* Pivot controls removed per request; structure pivots automatically */

    public UIStructureFormPanel(UIForm editor)
    {
        super(editor);

        this.pickStructure = new UIButton(UIKeys.FORMS_EDITORS_STRUCTURE_PICK_STRUCTURE, (b) -> this.pickStructure());
        this.structureFile = new UITextbox(100, (s) -> this.form.structureFile.set(s)).path().border();
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
        this.pickBiome = new UIButton(UIKeys.FORMS_EDITORS_STRUCTURE_PICK_BIOME, (b) -> this.pickBiome());
        // Inicializar con valor por defecto; se sincroniza en startEdit
        this.toggleLight = new UIToggle(UIKeys.FORMS_EDITORS_STRUCTURE_LIGHT, false, (t) -> this.toggleLight(t));
        this.lightIntensity = new UITrackpad((v) -> this.setLightIntensity(v.intValue()))
                .integer()
                .limit(1D, 15D);
        this.toggleFluid = new UIToggle(UIKeys.FORMS_EDITORS_STRUCTURE_FLUID, false, (t) -> this.form.renderFluid.set(t.getValue()));

        this.scaleX = new UITrackpad((v) -> this.form.scaleX.set(v.floatValue())).limit(0.01D, 100D);
        this.scaleX.tooltip(UIKeys.FORMS_EDITORS_STRUCTURE_SCALE_X);
        this.scaleY = new UITrackpad((v) -> this.form.scaleY.set(v.floatValue())).limit(0.01D, 100D);
        this.scaleY.tooltip(UIKeys.FORMS_EDITORS_STRUCTURE_SCALE_Y);
        this.scaleZ = new UITrackpad((v) -> this.form.scaleZ.set(v.floatValue())).limit(0.01D, 100D);
        this.scaleZ.tooltip(UIKeys.FORMS_EDITORS_STRUCTURE_SCALE_Z);

        // Pivot UI removed; calculate center moved to Transform panel

        /* Quitar etiquetas; mostrar solo los controles */
        this.options.add(
            UIFormColorLayout.sectionLabel(UIKeys.FORMS_EDITOR_FORM),
            UIFormColorLayout.colorWithTransform(this.color, this.colorTransform),
            UIFormColorLayout.createExtraSection(
                this.glowSection,
                UIFormColorLayout.paintColorRowWithTransform(this.paintColor, this.paintIntensity, this.paintTransform),
                this.colorAdjustments.marginTop(4)
            ).marginTop(4)
        );
        this.options.add(this.pickStructure);
        this.options.add(this.pickBiome);
        this.options.add(this.toggleLight);
        this.options.add(this.toggleFluid);
        this.options.add(UI.label(UIKeys.FORMS_EDITORS_STRUCTURE_LIGHT_INTENSITY_LABEL).marginTop(6), this.lightIntensity);
        this.options.add(UI.label(UIKeys.FORMS_EDITORS_STRUCTURE_SIZE).marginTop(10));
        this.options.add(UI.row(this.scaleX, this.scaleY, this.scaleZ));

        // Pivot controls removed
    }

    private void pickStructure()
    {
        UIStructureOverlayPanel overlay = new UIStructureOverlayPanel(
                UIKeys.FORMS_EDITORS_STRUCTURE_PICK_STRUCTURE,
                (link) -> this.setStructure(link)
        );

        String current = this.form.structureFile.get();
        if (current == null || current.isEmpty())
        {
            overlay.set("");
        }
        else
        {
            try
            {
                overlay.set(Link.create(current));
            }
            catch (Exception e)
            {
                overlay.set("");
            }
        }
        /* Igualar tamaño al overlay usado en el panel de keyframes */
        UIOverlay.addOverlay(this.getContext(), overlay, 280, 0.5F);
    }

    private void pickBiome()
    {
        UIListOverlayPanel overlay = new UIListOverlayPanel(UIKeys.FORMS_EDITORS_STRUCTURE_PICK_BIOME, (value) ->
        {
            String id = value == null ? "" : value;
            this.form.biomeId.set(id);
        });

        // Construir lista de biomas de forma segura
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
        catch (Throwable ignored) {}

        overlay.addValues(ids);
        overlay.setValue(this.form.biomeId.get());
        UIOverlay.addOverlay(this.getContext(), overlay, 280, 0.5F);
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


    /* calculate center moved to Transform panel */


    private void setStructure(Link link)
    {
        String path = link == null ? "" : link.toString();

        this.form.structureFile.set(path);
        this.structureFile.setText(path);
    }

    @Override
    public void startEdit(StructureForm form)
    {
        super.startEdit(form);

        this.structureFile.setText(form.structureFile.get());
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
        StructureLightSettings s = form.structureLight.get();
        boolean enabled = (s != null) ? s.enabled : form.emitLight.get();
        int intensity = (s != null) ? s.intensity : form.lightIntensity.get();

        this.toggleLight.setValue(enabled);
        this.lightIntensity.setValue((double) intensity);
        this.scaleX.setValue((double) form.scaleX.get());
        this.scaleY.setValue((double) form.scaleY.get());
        this.scaleZ.setValue((double) form.scaleZ.get());
        this.toggleFluid.setValue(form.renderFluid.get());
        // Pivot controls removed
    }
}