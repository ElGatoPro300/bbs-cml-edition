package mchorse.bbs_mod.forms.renderers.utils;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.graphics.texture.AdoptedTexture;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.utils.iris.IrisFormPipelines;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat;

public class BillboardRenderLayers
{
    private static final RenderPipeline[] PIPELINES = new RenderPipeline[32];

    private static RenderPipeline pipeline(boolean shaded, boolean depthWrite, boolean cull, boolean quads, boolean glow)
    {
        int index = (shaded ? 1 : 0) | (depthWrite ? 2 : 0) | (cull ? 4 : 0) | (quads ? 8 : 0) | (glow ? 16 : 0);

        if (PIPELINES[index] == null)
        {
            RenderPipeline source = shaded ? RenderPipelines.ENTITY_TRANSLUCENT : RenderPipelines.GUI_TEXTURED;
            BlendFunction blend = glow
                ? new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE, SourceFactor.ONE, DestFactor.ZERO)
                : BlendFunction.TRANSLUCENT;

            RenderPipeline.Builder builder = RenderPipeline.builder(shaded
                ? RenderPipelines.TRANSFORMS_PROJECTION_FOG_LIGHTING_SNIPPET
                : RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                .withLocation(Identifier.of(BBSMod.MOD_ID, "pipeline/billboard_" + index))
                .withVertexShader(source.getVertexShader())
                .withFragmentShader(source.getFragmentShader())
                .withVertexFormat(shaded ? VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL : VertexFormats.POSITION_TEXTURE_COLOR,
                    quads ? VertexFormat.DrawMode.QUADS : VertexFormat.DrawMode.TRIANGLES)
                .withSampler("Sampler0")
                .withBlend(blend)
                .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                .withDepthWrite(depthWrite)
                .withCull(cull);

            if (shaded)
            {
                builder.withSampler("Sampler1").withSampler("Sampler2")
                    .withShaderDefine("PER_FACE_LIGHTING")
                    .withShaderDefine("ALPHA_CUTOUT", 0.001F);
            }

            PIPELINES[index] = RenderPipelines.register(builder.build());

            if (BBSRendering.isIrisLoaded())
            {
                IrisFormPipelines.register(PIPELINES[index], shaded ? source : null);
            }
        }

        return PIPELINES[index];
    }

    public static void draw(BuiltBuffer buffer, Texture texture, boolean linear, boolean mipmap, boolean depthWrite, boolean cull)
    {
        draw(buffer, texture, linear, mipmap, depthWrite, cull, false);
    }

    public static void draw(BuiltBuffer buffer, Texture texture, boolean linear, boolean mipmap, boolean depthWrite, boolean cull, boolean glow)
    {
        Identifier id = AdoptedTexture.identifier(texture);

        if (id == null)
        {
            buffer.close();

            return;
        }

        VertexFormat format = buffer.getDrawParameters().format();
        boolean shaded = format == VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL
            || (BBSRendering.isIrisLoaded() && IrisFormPipelines.isEntityFormat(format));
        boolean quads = buffer.getDrawParameters().mode() == VertexFormat.DrawMode.QUADS;
        FilterMode filter = linear ? FilterMode.LINEAR : FilterMode.NEAREST;
        RenderSetup.Builder setup = RenderSetup.builder(pipeline(shaded, depthWrite, cull, quads, glow))
            .texture("Sampler0", id, () -> RenderSystem.getSamplerCache().get(
                AddressMode.REPEAT, AddressMode.REPEAT, filter, filter, mipmap));

        if (shaded)
        {
            setup.useLightmap().useOverlay();
        }

        /* RenderLayer owns the buffer and binds texture views, samplers and uniform buffers.
         * A raw glBindTexture/glUseProgram does not configure a vanilla render pass. */
        try
        {
            RenderLayer.of("bbs_billboard", setup.build()).draw(buffer);
        }
        finally
        {
            /* Legacy callers reset this texture's filtering after drawing. Do not let
             * that reset modify the lightmap or overlay bound by the render pass. */
            texture.bind(0);
        }
    }
}
