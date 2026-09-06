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

import org.lwjgl.opengl.GL11;

public class ParticleRenderLayers
{
    private static final int TYPE_LIT = 0;
    private static final int TYPE_SHADED = 1;
    private static final int TYPE_UI = 2;
    private static final int TYPE_GLOW = 3;

    private static final RenderPipeline[] PIPELINES = new RenderPipeline[8];

    private static RenderPipeline pipeline(int type, boolean depthWrite)
    {
        int index = (type << 1) | (depthWrite ? 1 : 0);

        if (PIPELINES[index] == null)
        {
            RenderPipeline.Builder builder;

            if (type == TYPE_LIT)
            {
                RenderPipeline source = RenderPipelines.TRANSLUCENT_PARTICLE;

                builder = RenderPipeline.builder(RenderPipelines.TRANSFORMS_PROJECTION_FOG_SNIPPET)
                    .withLocation(Identifier.of(BBSMod.MOD_ID, "pipeline/particle_lit" + (depthWrite ? "" : "_no_depth")))
                    .withVertexShader(source.getVertexShader())
                    .withFragmentShader(source.getFragmentShader())
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR_LIGHT, VertexFormat.DrawMode.TRIANGLES)
                    .withSampler("Sampler0")
                    .withSampler("Sampler2")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(depthWrite)
                    .withCull(false);
            }
            else if (type == TYPE_SHADED)
            {
                RenderPipeline source = RenderPipelines.ENTITY_TRANSLUCENT;

                builder = RenderPipeline.builder(RenderPipelines.TRANSFORMS_PROJECTION_FOG_LIGHTING_SNIPPET)
                    .withLocation(Identifier.of(BBSMod.MOD_ID, "pipeline/particle_shaded" + (depthWrite ? "" : "_no_depth")))
                    .withVertexShader(source.getVertexShader())
                    .withFragmentShader(source.getFragmentShader())
                    .withVertexFormat(VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.TRIANGLES)
                    .withSampler("Sampler0")
                    .withSampler("Sampler1")
                    .withSampler("Sampler2")
                    .withShaderDefine("PER_FACE_LIGHTING")
                    .withShaderDefine("ALPHA_CUTOUT", 0.001F)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(depthWrite)
                    .withCull(false);
            }
            else if (type == TYPE_GLOW)
            {
                RenderPipeline source = RenderPipelines.GUI_TEXTURED;

                builder = RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of(BBSMod.MOD_ID, "pipeline/particle_glow"))
                    .withVertexShader(source.getVertexShader())
                    .withFragmentShader(source.getFragmentShader())
                    .withVertexFormat(source.getVertexFormat(), VertexFormat.DrawMode.TRIANGLES)
                    .withSampler("Sampler0")
                    .withBlend(new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE, SourceFactor.ONE, DestFactor.ZERO))
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false);
            }
            else
            {
                RenderPipeline source = RenderPipelines.GUI_TEXTURED;

                builder = RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                    .withLocation(Identifier.of(BBSMod.MOD_ID, "pipeline/particle_ui"))
                    .withVertexShader(source.getVertexShader())
                    .withFragmentShader(source.getFragmentShader())
                    .withVertexFormat(source.getVertexFormat(), VertexFormat.DrawMode.TRIANGLES)
                    .withSampler("Sampler0")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(false);
            }

            PIPELINES[index] = RenderPipelines.register(builder.build());

            if (BBSRendering.isIrisLoaded())
            {
                IrisFormPipelines.register(PIPELINES[index], type == TYPE_SHADED ? RenderPipelines.ENTITY_TRANSLUCENT
                    : type == TYPE_LIT ? RenderPipelines.TRANSLUCENT_PARTICLE : null);
            }
        }

        return PIPELINES[index];
    }

    public static void draw(BuiltBuffer buffer, Texture texture)
    {
        draw(buffer, texture, false, true);
    }

    public static void drawGlow(BuiltBuffer buffer, Texture texture)
    {
        draw(buffer, texture, true, false);
    }

    public static void draw(BuiltBuffer buffer, Texture texture, boolean glow)
    {
        draw(buffer, texture, glow, !glow);
    }

    public static void draw(BuiltBuffer buffer, Texture texture, boolean glow, boolean depthWrite)
    {
        if (buffer == null)
        {
            return;
        }

        if (buffer.getDrawParameters() == null || buffer.getDrawParameters().vertexCount() == 0)
        {
            buffer.close();

            return;
        }

        Identifier id = AdoptedTexture.identifier(texture);

        if (id == null)
        {
            buffer.close();

            return;
        }

        VertexFormat format = buffer.getDrawParameters().format();
        int type;

        if (glow)
        {
            type = TYPE_GLOW;
        }
        else if (format == VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL
            || (BBSRendering.isIrisLoaded() && IrisFormPipelines.isEntityFormat(format)))
        {
            type = TYPE_SHADED;
        }
        else if (format == VertexFormats.POSITION_TEXTURE_COLOR_LIGHT)
        {
            type = TYPE_LIT;
        }
        else
        {
            type = TYPE_UI;
        }

        boolean linear = texture != null && texture.getFilter() == GL11.GL_LINEAR;
        boolean mipmap = texture != null && texture.isReallyMipmap();
        FilterMode filter = linear ? FilterMode.LINEAR : FilterMode.NEAREST;

        RenderSetup.Builder setup = RenderSetup.builder(pipeline(type, depthWrite))
            .texture("Sampler0", id, () -> RenderSystem.getSamplerCache().get(
                AddressMode.REPEAT, AddressMode.REPEAT, filter, filter, mipmap));

        if (type == TYPE_LIT)
        {
            setup.useLightmap().translucent();
        }
        else if (type == TYPE_SHADED)
        {
            setup.useLightmap().useOverlay().translucent();
        }

        /* RenderLayer owns the buffer and binds texture views, samplers and uniform buffers.
         * A raw glBindTexture/glUseProgram does not configure a vanilla render pass. */
        try
        {
            RenderLayer.of("bbs_particle", setup.build()).draw(buffer);
        }
        finally
        {
            if (texture != null)
            {
                texture.bind(0);
            }
        }
    }
}
