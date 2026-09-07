package mchorse.bbs_mod.forms.renderers.utils;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSUniform;
import mchorse.bbs_mod.client.ModelEffectUniforms;
import mchorse.bbs_mod.cubic.render.vao.ModelVAORenderer;
import mchorse.bbs_mod.graphics.texture.AdoptedTexture;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.utils.iris.IrisCustomPass;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.ScissorState;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

import org.joml.Matrix4f;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/** Explicit model effect/picking pass. Each draw owns its uniform snapshot. */
public final class ModelEffectPass
{
    private record Key(VertexFormat format, VertexFormat.DrawMode mode, boolean picking, boolean depthWrite, boolean cull, boolean overlay, String shader, boolean multiply) {}

    private static final Map<Key, RenderPipeline> PIPELINES = new HashMap<>();
    private static final Map<ShaderProgram, String> PROGRAMS = new WeakHashMap<>();
    private static ShaderProgram boundEffects;

    public static void bound(ShaderProgram program)
    {
        boundEffects = PROGRAMS.containsKey(program) ? program : null;
    }

    public static boolean hasBinding()
    {
        return boundEffects != null;
    }

    private static <T> T custom(Supplier<T> draw)
    {
        return BBSRendering.isIrisLoaded() ? IrisCustomPass.run(draw) : draw.get();
    }

    private static RenderPipeline pipeline(Key key)
    {
        return PIPELINES.computeIfAbsent(key, ignored ->
        {
            Identifier vertex = Identifier.of("bbs", "core/" + (key.shader.equals("block_glow_overlay") ? "block_paint_overlay" : key.shader));
            Identifier fragment = Identifier.of("bbs", "core/" + key.shader);
            RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.TRANSFORMS_PROJECTION_FOG_LIGHTING_SNIPPET)
                .withLocation(Identifier.of("bbs", "pipeline/model_effect_" + PIPELINES.size()))
                .withVertexShader(vertex).withFragmentShader(fragment)
                .withVertexFormat(key.format, key.mode)
                .withUniform("BbsModelEffects", UniformType.UNIFORM_BUFFER)
                .withSampler("Sampler0")
                .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                .withDepthWrite(key.depthWrite).withCull(key.cull);

            if (!key.picking)
            {
                builder.withSampler("Sampler1").withSampler("Sampler2").withSampler("Sampler3")
                    .withBlend(key.multiply
                        ? new BlendFunction(SourceFactor.DST_COLOR, DestFactor.ZERO, SourceFactor.ZERO, DestFactor.ONE)
                        : key.shader.equals("block_glow_overlay")
                        ? new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE, SourceFactor.ONE, DestFactor.ZERO)
                        : BlendFunction.TRANSLUCENT);
            }

            if (key.overlay)
            {
                builder.withDepthBias(0F, -4F);
            }

            return RenderPipelines.register(builder.build());
        });
    }

    public static ShaderProgram program(boolean picking)
    {
        return program(picking ? "picker_models" : "model");
    }

    public static ShaderProgram program(String name)
    {
        return custom(() ->
        {
            ShaderProgram shader = ModelEffectUniforms.register(BBSRendering.getProgram(pipeline(new Key(
                VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.TRIANGLES,
                name.startsWith("picker_"), true, false, false, name, false))));

            if (shader != null && shader != ShaderProgram.INVALID)
            {
                PROGRAMS.put(shader, name);
            }

            return shader;
        });
    }

    public static boolean drawBound(BuiltBuffer buffer)
    {
        return drawBound(buffer, null);
    }

    public static boolean drawBound(BuiltBuffer buffer, Identifier layerTexture)
    {
        int current = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        ShaderProgram shader = null;

        for (ShaderProgram candidate : PROGRAMS.keySet())
        {
            if (candidate.getGlRef() == current)
            {
                shader = candidate;
                break;
            }
        }

        if (shader == null)
        {
            return false;
        }

        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        GL13.glActiveTexture(active);
        Identifier id = layerTexture != null ? layerTexture : AdoptedTexture.identifier(texture, width, height, false);
        boolean picking = PROGRAMS.get(shader).startsWith("picker_");
        boolean overlay = PROGRAMS.get(shader).endsWith("_overlay") || ModelVAORenderer.isPaintOverlayPass()
            || ModelVAORenderer.isColorTintOverlayPass() || ModelVAORenderer.isColorGradeOverlayPass();
        boolean depthWrite = picking || (!overlay && GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK));
        ShaderProgram parameters = shader;
        BBSUniform.setMatrix4f(parameters, "ModelViewMat", new Matrix4f(RenderSystem.getModelViewMatrix()));

        custom(() ->
        {
            drawCustom(buffer, id, parameters, picking, depthWrite, false, overlay);
            return null;
        });

        return true;
    }

    public static void draw(BuiltBuffer buffer, Texture texture, ShaderProgram parameters, boolean picking, boolean depthWrite, boolean cull, boolean overlay)
    {
        custom(() ->
        {
            drawCustom(buffer, AdoptedTexture.identifier(texture), parameters, picking, depthWrite, cull, overlay);

            return null;
        });
    }

    private static void drawCustom(BuiltBuffer buffer, Identifier id, ShaderProgram parameters, boolean picking, boolean depthWrite, boolean cull, boolean overlay)
    {
        try (buffer)
        {
            if (id == null)
            {
                return;
            }

            BuiltBuffer.DrawParameters draws = buffer.getDrawParameters();
            RenderPipeline pipeline = pipeline(new Key(draws.format(), draws.mode(), picking, depthWrite, cull, overlay, PROGRAMS.get(parameters),
                (ModelVAORenderer.isColorTintOverlayPass() || PROGRAMS.get(parameters).endsWith("color_tint_overlay"))
                    && ModelEffectUniforms.value(parameters, "ColorGradeActive") < 0.5F));
            RenderSetup.Builder setup = RenderSetup.builder(pipeline).texture("Sampler0", id);

            if (!picking)
            {
                Texture scene = ModelVAORenderer.isColorGradeOverlayPass() || ModelEffectUniforms.value(parameters, "ColorGradeActive") > 0.5F
                    ? ModelVAORenderer.getGradeSceneColor() : null;
                Identifier sceneId = scene != null ? AdoptedTexture.identifier(scene) : null;
                setup.useLightmap().useOverlay().texture("Sampler3", sceneId != null ? sceneId : id);
            }

            Map<String, RenderSetup.Texture> textures = setup.build().resolveTextures();
            GpuBuffer vertices = draws.format().uploadImmediateVertexBuffer(buffer.getBuffer());
            GpuBuffer indices;
            VertexFormat.IndexType indexType;

            if (buffer.getSortedBuffer() == null)
            {
                RenderSystem.ShapeIndexBuffer sequential = RenderSystem.getSequentialBuffer(draws.mode());
                indices = sequential.getIndexBuffer(draws.indexCount());
                indexType = sequential.getIndexType();
            }
            else
            {
                indices = draws.format().uploadImmediateIndexBuffer(buffer.getSortedBuffer());
                indexType = draws.indexType();
            }

            net.minecraft.client.gl.Framebuffer target = MinecraftClient.getInstance().getFramebuffer();

            try (GpuBuffer uniforms = RenderSystem.getDevice().createBuffer(() -> "BBS model effects", GpuBuffer.USAGE_UNIFORM, ModelEffectUniforms.data(parameters));
                 RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "BBS model effects",
                     RenderSystem.outputColorTextureOverride != null ? RenderSystem.outputColorTextureOverride : target.getColorAttachmentView(), OptionalInt.empty(),
                     RenderSystem.outputDepthTextureOverride != null ? RenderSystem.outputDepthTextureOverride : target.getDepthAttachmentView(), OptionalDouble.empty()))
            {
                pass.setPipeline(pipeline);
                ScissorState scissor = RenderSystem.getScissorStateForRenderTypeDraws();

                if (scissor.isEnabled())
                {
                    pass.enableScissor(scissor.getX(), scissor.getY(), scissor.getWidth(), scissor.getHeight());
                }

                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("BbsModelEffects", uniforms);
                pass.setVertexBuffer(0, vertices);
                pass.setIndexBuffer(indices, indexType);

                for (Map.Entry<String, RenderSetup.Texture> entry : textures.entrySet())
                {
                    pass.bindTexture(entry.getKey(), entry.getValue().textureView(), entry.getValue().sampler());
                }

                pass.drawIndexed(0, 0, draws.indexCount(), 1);
            }
        }
    }
}
