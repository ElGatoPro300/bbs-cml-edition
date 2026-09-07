package mchorse.bbs_mod.client;

import mchorse.bbs_mod.BBSMod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.ShaderLoader;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class BBSShaders
{
    public static final List<Runnable> LOADERS = new ArrayList<>();

    private static ShaderProgram model;
    private static ShaderProgram multiLink;
    private static ShaderProgram subtitles;
    private static ShaderProgram imageOverlay;

    private static ShaderProgram pickerPreview;
    private static ShaderProgram pickerBillboard;
    private static ShaderProgram pickerBillboardNoShading;
    private static ShaderProgram pickerParticles;
    private static ShaderProgram pickerModels;
    private static ShaderProgram blockPaintOverlay;
    private static ShaderProgram flatPaintOverlay;
    private static ShaderProgram blockGlowOverlay;
    private static ShaderProgram blockColorTintOverlay;
    private static ShaderProgram flatColorTintOverlay;

    /* Avoid reloading every BBS shader on each draw when model compile fails. */
    private static boolean modelLoadRetried;

    static
    {
        setup();
    }

    public static void setup()
    {
        modelLoadRetried = false;

        if (model != null)
        {
            model.close();
            model = null;
        }

        if (multiLink != null)
        {
            multiLink.close();
            multiLink = null;
        }

        if (subtitles != null)
        {
            subtitles.close();
            subtitles = null;
        }

        if (imageOverlay != null)
        {
            imageOverlay.close();
            imageOverlay = null;
        }

        if (pickerPreview != null)
        {
            pickerPreview.close();
            pickerPreview = null;
        }

        if (pickerBillboard != null)
        {
            pickerBillboard.close();
            pickerBillboard = null;
        }

        if (pickerBillboardNoShading != null)
        {
            pickerBillboardNoShading.close();
            pickerBillboardNoShading = null;
        }

        if (pickerParticles != null)
        {
            pickerParticles.close();
            pickerParticles = null;
        }

        if (pickerModels != null)
        {
            pickerModels.close();
            pickerModels = null;
        }

        if (blockPaintOverlay != null)
        {
            blockPaintOverlay.close();
            blockPaintOverlay = null;
        }

        if (flatPaintOverlay != null)
        {
            flatPaintOverlay.close();
            flatPaintOverlay = null;
        }

        if (blockGlowOverlay != null)
        {
            blockGlowOverlay.close();
            blockGlowOverlay = null;
        }

        if (blockColorTintOverlay != null)
        {
            blockColorTintOverlay.close();
            blockColorTintOverlay = null;
        }

        if (flatColorTintOverlay != null)
        {
            flatColorTintOverlay.close();
            flatColorTintOverlay = null;
        }

        ShaderLoader loader = MinecraftClient.getInstance().getShaderLoader();
        Defines defines = Defines.EMPTY;

        ShaderProgramKey modelKey = new ShaderProgramKey(Identifier.of(BBSMod.MOD_ID, "core/model"), VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, defines);
        ShaderProgramKey multiLinkKey = new ShaderProgramKey(Identifier.of(BBSMod.MOD_ID, "core/multilink"), VertexFormats.POSITION_TEXTURE_COLOR, defines);
        ShaderProgramKey subtitlesKey = new ShaderProgramKey(Identifier.of(BBSMod.MOD_ID, "core/subtitles"), VertexFormats.POSITION_TEXTURE_COLOR, defines);
        ShaderProgramKey imageOverlayKey = new ShaderProgramKey(Identifier.of(BBSMod.MOD_ID, "core/image_overlay"), VertexFormats.POSITION_TEXTURE_COLOR, defines);

        ShaderProgramKey pickerPreviewKey = new ShaderProgramKey(Identifier.of(BBSMod.MOD_ID, "core/picker_preview"), VertexFormats.POSITION_TEXTURE_COLOR, defines);
        ShaderProgramKey pickerBillboardKey = new ShaderProgramKey(Identifier.of(BBSMod.MOD_ID, "core/picker_billboard"), VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, defines);
        ShaderProgramKey pickerBillboardNoShadingKey = new ShaderProgramKey(Identifier.of(BBSMod.MOD_ID, "core/picker_billboard_no_shading"), VertexFormats.POSITION_TEXTURE_LIGHT_COLOR, defines);
        ShaderProgramKey pickerParticlesKey = new ShaderProgramKey(Identifier.of(BBSMod.MOD_ID, "core/picker_particles"), VertexFormats.POSITION_COLOR_TEXTURE_LIGHT, defines);
        ShaderProgramKey pickerModelsKey = new ShaderProgramKey(Identifier.of(BBSMod.MOD_ID, "core/picker_models"), VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, defines);

        ShaderProgramKey blockPaintOverlayKey = new ShaderProgramKey(Identifier.of(BBSMod.MOD_ID, "core/block_paint_overlay"), VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, defines);
        ShaderProgramKey flatPaintOverlayKey = new ShaderProgramKey(Identifier.of(BBSMod.MOD_ID, "core/flat_paint_overlay"), VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, defines);
        ShaderProgramKey blockGlowOverlayKey = new ShaderProgramKey(Identifier.of(BBSMod.MOD_ID, "core/block_glow_overlay"), VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, defines);
        ShaderProgramKey blockColorTintOverlayKey = new ShaderProgramKey(Identifier.of(BBSMod.MOD_ID, "core/block_color_tint_overlay"), VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, defines);
        ShaderProgramKey flatColorTintOverlayKey = new ShaderProgramKey(Identifier.of(BBSMod.MOD_ID, "core/flat_color_tint_overlay"), VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, defines);

        model = loader.getOrCreateProgram(modelKey);
        multiLink = loader.getOrCreateProgram(multiLinkKey);
        subtitles = loader.getOrCreateProgram(subtitlesKey);
        imageOverlay = loader.getOrCreateProgram(imageOverlayKey);

        pickerPreview = loader.getOrCreateProgram(pickerPreviewKey);
        pickerBillboard = loader.getOrCreateProgram(pickerBillboardKey);
        pickerBillboardNoShading = loader.getOrCreateProgram(pickerBillboardNoShadingKey);
        pickerParticles = loader.getOrCreateProgram(pickerParticlesKey);
        pickerModels = loader.getOrCreateProgram(pickerModelsKey);
        blockPaintOverlay = loader.getOrCreateProgram(blockPaintOverlayKey);
        flatPaintOverlay = loader.getOrCreateProgram(flatPaintOverlayKey);
        blockGlowOverlay = loader.getOrCreateProgram(blockGlowOverlayKey);
        blockColorTintOverlay = loader.getOrCreateProgram(blockColorTintOverlayKey);
        flatColorTintOverlay = loader.getOrCreateProgram(flatColorTintOverlayKey);

        for (Runnable runnable : LOADERS)
        {
            runnable.run();
        }
    }

    public static ShaderProgram getModel()
    {
        if (model == null && !modelLoadRetried)
        {
            modelLoadRetried = true;
            setup();
        }

        return model;
    }

    public static ShaderProgram getMultilinkProgram()
    {
        return multiLink;
    }

    public static ShaderProgram getSubtitlesProgram()
    {
        return subtitles;
    }

    public static ShaderProgram getImageOverlayProgram()
    {
        if (imageOverlay == null)
        {
            setup();
        }

        return imageOverlay;
    }

    public static ShaderProgram getPickerPreviewProgram()
    {
        return pickerPreview;
    }

    public static ShaderProgram getPickerBillboardProgram()
    {
        return pickerBillboard;
    }

    public static ShaderProgram getPickerBillboardNoShadingProgram()
    {
        return pickerBillboardNoShading;
    }

    public static ShaderProgram getPickerParticlesProgram()
    {
        return pickerParticles;
    }

    public static ShaderProgram getPickerModelsProgram()
    {
        return pickerModels;
    }

    public static ShaderProgram getBlockPaintOverlayProgram()
    {
        return blockPaintOverlay;
    }

    public static ShaderProgram getFlatPaintOverlayProgram()
    {
        return flatPaintOverlay;
    }

    public static ShaderProgram getBlockGlowOverlayProgram()
    {
        return blockGlowOverlay;
    }

    public static ShaderProgram getBlockColorTintOverlayProgram()
    {
        return blockColorTintOverlay;
    }

    public static ShaderProgram getFlatColorTintOverlayProgram()
    {
        return flatColorTintOverlay;
    }
}
