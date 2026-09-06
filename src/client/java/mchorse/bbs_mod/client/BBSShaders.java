package mchorse.bbs_mod.client;

import mchorse.bbs_mod.BBSMod;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;

import java.util.ArrayList;
import java.util.List;

public class BBSShaders
{
    public static final List<Runnable> LOADERS = new ArrayList<>();

    public static RenderPipeline modelPipeline;
    public static RenderPipeline multiLinkPipeline;
    public static RenderPipeline subtitlesPipeline;
    public static RenderPipeline imageOverlayPipeline;

    public static RenderPipeline pickerBillboardPipeline;
    public static RenderPipeline pickerBillboardNoShadingPipeline;
    public static RenderPipeline pickerParticlesPipeline;
    public static RenderPipeline pickerModelsPipeline;
    public static RenderPipeline blockPaintOverlayPipeline;
    public static RenderPipeline flatPaintOverlayPipeline;
    public static RenderPipeline blockGlowOverlayPipeline;
    public static RenderPipeline blockColorTintOverlayPipeline;
    public static RenderPipeline flatColorTintOverlayPipeline;

    static
    {
        setup();
    }

    public static void setup()
    {
        modelPipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.TRANSFORMS_PROJECTION_FOG_LIGHTING_SNIPPET)
            .withLocation(Identifier.of(BBSMod.MOD_ID, "pipeline/model"))
            .withVertexShader(Identifier.of(BBSMod.MOD_ID, "core/model"))
            .withFragmentShader(Identifier.of(BBSMod.MOD_ID, "core/model"))
            .withSampler("Sampler0")
            .withSampler("Sampler1")
            .withSampler("Sampler2")
            .withSampler("Sampler3")
            .withVertexFormat(VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS)
            .build());

        multiLinkPipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
            .withLocation(Identifier.of(BBSMod.MOD_ID, "pipeline/multilink"))
            .withVertexShader(Identifier.of(BBSMod.MOD_ID, "core/multilink"))
            .withFragmentShader(Identifier.of(BBSMod.MOD_ID, "core/multilink"))
            .withSampler("Sampler0")
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
            .build());

        subtitlesPipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
            .withLocation(Identifier.of(BBSMod.MOD_ID, "pipeline/subtitles"))
            .withVertexShader(Identifier.of(BBSMod.MOD_ID, "core/subtitles"))
            .withFragmentShader(Identifier.of(BBSMod.MOD_ID, "core/subtitles"))
            .withSampler("Sampler0")
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
            .build());

        imageOverlayPipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
            .withLocation(Identifier.of(BBSMod.MOD_ID, "pipeline/image_overlay"))
            .withVertexShader(Identifier.of(BBSMod.MOD_ID, "core/image_overlay"))
            .withFragmentShader(Identifier.of(BBSMod.MOD_ID, "core/image_overlay"))
            .withSampler("Sampler0")
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
            .build());

        pickerBillboardPipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.TRANSFORMS_PROJECTION_FOG_LIGHTING_SNIPPET)
            .withLocation(Identifier.of(BBSMod.MOD_ID, "pipeline/picker_billboard"))
            .withVertexShader(Identifier.of(BBSMod.MOD_ID, "core/picker_billboard"))
            .withFragmentShader(Identifier.of(BBSMod.MOD_ID, "core/picker_billboard"))
            .withSampler("Sampler0")
            .withVertexFormat(VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS)
            .build());

        pickerBillboardNoShadingPipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.TRANSFORMS_PROJECTION_FOG_SNIPPET)
            .withLocation(Identifier.of(BBSMod.MOD_ID, "pipeline/picker_billboard_no_shading"))
            .withVertexShader(Identifier.of(BBSMod.MOD_ID, "core/picker_billboard_no_shading"))
            .withFragmentShader(Identifier.of(BBSMod.MOD_ID, "core/picker_billboard_no_shading"))
            .withSampler("Sampler0")
            .withVertexFormat(VertexFormats.POSITION_TEXTURE_LIGHT_COLOR, VertexFormat.DrawMode.QUADS)
            .build());

        pickerParticlesPipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.TRANSFORMS_PROJECTION_FOG_LIGHTING_SNIPPET)
            .withLocation(Identifier.of(BBSMod.MOD_ID, "pipeline/picker_particles"))
            .withVertexShader(Identifier.of(BBSMod.MOD_ID, "core/picker_particles"))
            .withFragmentShader(Identifier.of(BBSMod.MOD_ID, "core/picker_particles"))
            .withSampler("Sampler0")
            .withVertexFormat(VertexFormats.POSITION_COLOR_TEXTURE_LIGHT, VertexFormat.DrawMode.QUADS)
            .build());

        pickerModelsPipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.TRANSFORMS_PROJECTION_FOG_LIGHTING_SNIPPET)
            .withLocation(Identifier.of(BBSMod.MOD_ID, "pipeline/picker_models"))
            .withVertexShader(Identifier.of(BBSMod.MOD_ID, "core/picker_models"))
            .withFragmentShader(Identifier.of(BBSMod.MOD_ID, "core/picker_models"))
            .withSampler("Sampler0")
            .withVertexFormat(VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS)
            .build());

        blockPaintOverlayPipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.BLOCK_SNIPPET)
            .withLocation(Identifier.of(BBSMod.MOD_ID, "pipeline/block_paint_overlay"))
            .withVertexShader(Identifier.of(BBSMod.MOD_ID, "core/block_paint_overlay"))
            .withFragmentShader(Identifier.of(BBSMod.MOD_ID, "core/block_paint_overlay"))
            .withSampler("Sampler0")
            .withVertexFormat(VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS)
            .build());

        flatPaintOverlayPipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.TRANSFORMS_PROJECTION_FOG_LIGHTING_SNIPPET)
            .withLocation(Identifier.of(BBSMod.MOD_ID, "pipeline/flat_paint_overlay"))
            .withVertexShader(Identifier.of(BBSMod.MOD_ID, "core/flat_paint_overlay"))
            .withFragmentShader(Identifier.of(BBSMod.MOD_ID, "core/flat_paint_overlay"))
            .withSampler("Sampler0")
            .withVertexFormat(VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS)
            .build());

        blockGlowOverlayPipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.BLOCK_SNIPPET)
            .withLocation(Identifier.of(BBSMod.MOD_ID, "pipeline/block_glow_overlay"))
            .withVertexShader(Identifier.of(BBSMod.MOD_ID, "core/block_glow_overlay"))
            .withFragmentShader(Identifier.of(BBSMod.MOD_ID, "core/block_glow_overlay"))
            .withSampler("Sampler0")
            .withVertexFormat(VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS)
            .build());

        blockColorTintOverlayPipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.BLOCK_SNIPPET)
            .withLocation(Identifier.of(BBSMod.MOD_ID, "pipeline/block_color_tint_overlay"))
            .withVertexShader(Identifier.of(BBSMod.MOD_ID, "core/block_color_tint_overlay"))
            .withFragmentShader(Identifier.of(BBSMod.MOD_ID, "core/block_color_tint_overlay"))
            .withSampler("Sampler0")
            .withVertexFormat(VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS)
            .build());

        flatColorTintOverlayPipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.TRANSFORMS_PROJECTION_FOG_LIGHTING_SNIPPET)
            .withLocation(Identifier.of(BBSMod.MOD_ID, "pipeline/flat_color_tint_overlay"))
            .withVertexShader(Identifier.of(BBSMod.MOD_ID, "core/flat_color_tint_overlay"))
            .withFragmentShader(Identifier.of(BBSMod.MOD_ID, "core/flat_color_tint_overlay"))
            .withSampler("Sampler0")
            .withVertexFormat(VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS)
            .build());

        for (Runnable runnable : LOADERS)
        {
            runnable.run();
        }
    }

    public static ShaderProgram getModel()
    {
        ShaderProgram program = BBSRendering.getProgram(modelPipeline);

        if (program == null || program == ShaderProgram.INVALID)
        {
            return BBSRendering.getEntityTranslucentProgram();
        }

        return program;
    }

    public static ShaderProgram getMultilinkProgram()
    {
        return BBSRendering.getProgram(multiLinkPipeline);
    }

    public static ShaderProgram getSubtitlesProgram()
    {
        return BBSRendering.getProgram(subtitlesPipeline);
    }

    public static ShaderProgram getImageOverlayProgram()
    {
        return BBSRendering.getProgram(imageOverlayPipeline);
    }

    public static ShaderProgram getPickerBillboardProgram()
    {
        return BBSRendering.getProgram(pickerBillboardPipeline);
    }

    public static ShaderProgram getPickerBillboardNoShadingProgram()
    {
        return BBSRendering.getProgram(pickerBillboardNoShadingPipeline);
    }

    public static ShaderProgram getPickerParticlesProgram()
    {
        return BBSRendering.getProgram(pickerParticlesPipeline);
    }

    public static ShaderProgram getPickerModelsProgram()
    {
        return BBSRendering.getProgram(pickerModelsPipeline);
    }

    public static ShaderProgram getBlockPaintOverlayProgram()
    {
        return BBSRendering.getProgram(blockPaintOverlayPipeline);
    }

    public static ShaderProgram getFlatPaintOverlayProgram()
    {
        return BBSRendering.getProgram(flatPaintOverlayPipeline);
    }

    public static ShaderProgram getBlockGlowOverlayProgram()
    {
        return BBSRendering.getProgram(blockGlowOverlayPipeline);
    }

    public static ShaderProgram getBlockColorTintOverlayProgram()
    {
        return BBSRendering.getProgram(blockColorTintOverlayPipeline);
    }

    public static ShaderProgram getFlatColorTintOverlayProgram()
    {
        return BBSRendering.getProgram(flatColorTintOverlayPipeline);
    }
}
