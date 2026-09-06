package net.minecraft.client.render;

import mchorse.bbs_mod.forms.renderers.utils.ModelEffectPass;

import net.minecraft.client.gl.RenderPipelines;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;

/**
 * Compatibility shim for 1.21.11 where vanilla {@code BufferRenderer} was removed.
 * Routes {@link BuiltBuffer} draws to appropriate {@link RenderLayer} pipelines.
 */
public class BufferRenderer
{
    private static RenderLayer defaultGuiTexturedLayer;
    private static RenderLayer defaultGuiLayer;
    private static RenderLayer defaultTranslucentParticleLayer;
    private static RenderLayer defaultLinesLayer;
    private static RenderLayer defaultDebugQuadsLayer;

    public static void drawWithGlobalProgram(BuiltBuffer buffer)
    {
        if (buffer == null)
        {
            return;
        }

        BuiltBuffer.DrawParameters params = buffer.getDrawParameters();

        if (params == null || params.vertexCount() == 0)
        {
            buffer.close();

            return;
        }

        if (ModelEffectPass.drawBound(buffer))
        {
            return;
        }

        RenderLayer layer = resolveLayer(params);

        layer.draw(buffer);
    }

    public static void draw(BuiltBuffer buffer)
    {
        drawWithGlobalProgram(buffer);
    }

    private static RenderLayer resolveLayer(BuiltBuffer.DrawParameters params)
    {
        VertexFormat format = params.format();
        VertexFormat.DrawMode mode = params.mode();

        if (mode == VertexFormat.DrawMode.LINES || mode == VertexFormat.DrawMode.DEBUG_LINES || mode == VertexFormat.DrawMode.DEBUG_LINE_STRIP)
        {
            if (defaultLinesLayer == null)
            {
                defaultLinesLayer = RenderLayer.of("bbs_compat_lines", RenderSetup.builder(RenderPipelines.LINES).build());
            }

            return defaultLinesLayer;
        }

        if (format == VertexFormats.POSITION_TEXTURE_COLOR_LIGHT)
        {
            if (defaultTranslucentParticleLayer == null)
            {
                defaultTranslucentParticleLayer = RenderLayer.of("bbs_compat_particle", RenderSetup.builder(RenderPipelines.TRANSLUCENT_PARTICLE).build());
            }

            return defaultTranslucentParticleLayer;
        }

        if (format == VertexFormats.POSITION_COLOR)
        {
            if (defaultGuiLayer == null)
            {
                defaultGuiLayer = RenderLayer.of("bbs_compat_gui", RenderSetup.builder(RenderPipelines.GUI).build());
            }

            return defaultGuiLayer;
        }

        if (format == VertexFormats.POSITION)
        {
            if (defaultDebugQuadsLayer == null)
            {
                defaultDebugQuadsLayer = RenderLayer.of("bbs_compat_debug_quads", RenderSetup.builder(RenderPipelines.DEBUG_QUADS).build());
            }

            return defaultDebugQuadsLayer;
        }

        if (defaultGuiTexturedLayer == null)
        {
            defaultGuiTexturedLayer = RenderLayer.of("bbs_compat_gui_textured", RenderSetup.builder(RenderPipelines.GUI_TEXTURED).build());
        }

        return defaultGuiTexturedLayer;
    }
}
