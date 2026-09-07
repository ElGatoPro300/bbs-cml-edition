package mchorse.bbs_mod.client;

import mchorse.bbs_mod.BBSMod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceFactory;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BBSShaders
{
    public static final List<Runnable> LOADERS = new ArrayList<>();

    private static ShaderProgram model;
    private static ShaderProgram multiLink;
    private static ShaderProgram subtitles;
    private static ShaderProgram imageOverlay;
    private static ShaderProgram video;

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

        if (video != null)
        {
            video.close();
            video = null;
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

        try
        {
            ResourceFactory factory = new ProxyResourceFactory(MinecraftClient.getInstance().getResourceManager());

            model = new ShaderProgram(factory, "model", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
            multiLink = new ShaderProgram(factory, "multilink", VertexFormats.POSITION_TEXTURE_COLOR);
            subtitles = new ShaderProgram(factory, "subtitles", VertexFormats.POSITION_TEXTURE_COLOR);
            imageOverlay = new ShaderProgram(factory, "image_overlay", VertexFormats.POSITION_TEXTURE_COLOR);

            pickerPreview = new ShaderProgram(factory, "picker_preview", VertexFormats.POSITION_TEXTURE_COLOR);
            pickerBillboard = new ShaderProgram(factory, "picker_billboard", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
            pickerBillboardNoShading = new ShaderProgram(factory, "picker_billboard_no_shading", VertexFormats.POSITION_TEXTURE_LIGHT_COLOR);
            pickerParticles = new ShaderProgram(factory, "picker_particles", VertexFormats.POSITION_COLOR_TEXTURE_LIGHT);
            pickerModels = new ShaderProgram(factory, "picker_models", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
            blockPaintOverlay = new ShaderProgram(factory, "block_paint_overlay", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
            flatPaintOverlay = new ShaderProgram(factory, "flat_paint_overlay", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
            blockGlowOverlay = new ShaderProgram(factory, "block_glow_overlay", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
            blockColorTintOverlay = new ShaderProgram(factory, "block_color_tint_overlay", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
            flatColorTintOverlay = new ShaderProgram(factory, "flat_color_tint_overlay", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
            video = new ShaderProgram(factory, "video", VertexFormats.POSITION_TEXTURE);
        
            for (Runnable runnable : LOADERS)
            {
                runnable.run();
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
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

    public static ShaderProgram getVideoProgram()
    {
        if (video == null)
        {
            setup();
        }

        return video;
    }

    private static class ProxyResourceFactory implements ResourceFactory
    {
        private ResourceManager manager;

        public ProxyResourceFactory(ResourceManager manager)
        {
            this.manager = manager;
        }

        @Override
        public Optional<Resource> getResource(Identifier id)
        {
            if (id.getPath().contains("/core/"))
            {
                return this.manager.getResource(Identifier.of(BBSMod.MOD_ID, id.getPath()));
            }

            return this.manager.getResource(id);
        }
    }
}
