package mchorse.bbs_mod.utils.iris;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.mixin.client.iris.IrisRenderingPipelineAccessor;
import mchorse.bbs_mod.utils.MatrixStackUtils;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.fluid.FluidState;

import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPhase;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.irisshaders.iris.uniforms.custom.cached.CachedUniform;
import net.irisshaders.iris.uniforms.custom.cached.FloatCachedUniform;
import net.irisshaders.iris.vertices.BlockSensitiveBufferBuilder;
import net.irisshaders.iris.vertices.ExtendedDataHelper;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Forces Complementary / BSL {@code gbuffers_water} to treat BBS block-form fluids as real
 * water (material id + {@code water.glsl} / {@code DoWave_Water}), and flushes those draws
 * during Iris' translucent-terrain phase so the pack actually runs {@code GBUFFERS_WATER}.
 * <p>
 * Immediate-mode fluid draws lack terrain {@code mc_Entity} / {@code mc_midTexCoord}, so the
 * GLSL patch forces water mat and synthesizes mid-tex coords when {@code bbs_form_fluid} is set.
 * The uniform must be re-uploaded after Iris binds the program (see hijack in BlockFormRenderer).
 */
public final class FormFluidShaderPatch
{
    public static final String FORM_FLUID = "form_fluid";

    private static final String U_FORM_FLUID = ShaderCurves.UNIFORM_IDENTIFIER + FORM_FLUID;
    private static final String GUARD = "BBS_FORM_FLUID_PATCH";
    private static final String GUARD_MID = "BBS_FORM_FLUID_MID";
    private static final String GUARD_TANGENT = "BBS_FORM_FLUID_TANGENT";

    /** Complementary: {@code block.32000=water}, {@code block.10068=lava}. */
    private static final int COMPLEMENTARY_WATER_MAT = 32000;

    private static final Pattern COMP_MAT_ASSIGN = Pattern.compile(
        "mat\\s*=\\s*int\\s*\\(\\s*mc_Entity\\.x\\s*\\+\\s*0\\.5\\s*\\)\\s*;"
    );

    private static final Pattern ABS_MID_ASSIGN = Pattern.compile(
        "absMidCoordPos\\s*=\\s*abs\\s*\\(\\s*texMinMidCoord\\s*\\)\\s*;"
    );

    private static final Pattern TANGENT_NORMALIZE = Pattern.compile(
        "tangent\\s*=\\s*rawTangent\\s*\\*\\s*inversesqrt\\s*\\(\\s*max\\s*\\(\\s*dot\\s*\\(\\s*rawTangent\\s*,\\s*rawTangent\\s*\\)\\s*,\\s*1e-8\\s*\\)\\s*\\)\\s*;"
    );

    private static final Pattern BSL_WATER_OR = Pattern.compile(
        "mc_Entity\\.x\\s*==\\s*8(?:\\.0)?\\s*\\|\\|\\s*mc_Entity\\.x\\s*==\\s*9(?:\\.0)?"
    );

    private static float formFluid;
    private static boolean patchedThisPack;

    private static final List<WaterPhaseEntry> waterPhaseQueue = new ArrayList<>();
    private static final List<WaterPhaseEntry> vanillaFluidQueue = new ArrayList<>();

    private static final class WaterPhaseEntry
    {
        private final Matrix4f projection;
        private final Matrix4f modelView;
        private final float fluidMode;
        private final Runnable draw;

        private WaterPhaseEntry(Matrix4f projection, Matrix4f modelView, float fluidMode, Runnable draw)
        {
            this.projection = projection;
            this.modelView = modelView;
            this.fluidMode = fluidMode;
            this.draw = draw;
        }
    }

    private FormFluidShaderPatch()
    {}

    public static void setFormFluid(float mode)
    {
        formFluid = mode;
    }

    public static void clearFormFluid()
    {
        formFluid = 0F;
    }

    public static float getFormFluid()
    {
        return formFluid;
    }

    public static boolean isFormWaterActive()
    {
        return formFluid > 0.5F && formFluid < 1.5F;
    }

    public static void clearFrameQueue()
    {
        clearFormFluid();
        waterPhaseQueue.clear();
        vanillaFluidQueue.clear();
    }

    public static void resetPackState()
    {
        patchedThisPack = false;
        clearFrameQueue();
    }

    public static boolean shouldPatchPack()
    {
        if (BBSSettings.irisFormFluidPatch != null
            && !BBSSettings.irisFormFluidPatch.get())
        {
            return false;
        }

        String pack = resolvePackName();

        if (pack.isEmpty())
        {
            return false;
        }

        String lower = pack.toLowerCase(Locale.ROOT);

        return lower.contains("complementary") || lower.contains("bsl");
    }

    public static boolean isWaterPhaseEnabled()
    {
        return BBSRendering.isIrisShadersEnabled() && shouldPatchPack();
    }

    public static boolean isPackPatched()
    {
        return patchedThisPack && shouldPatchPack();
    }

    public static void addUniforms(List<CachedUniform> list)
    {
        list.add(new FloatCachedUniform(U_FORM_FLUID, UniformUpdateFrequency.PER_FRAME, FormFluidShaderPatch::getFormFluid));
    }

    private static String resolvePackName()
    {
        String loading = ShaderOpacityPatch.getLoadingPackName();

        if (loading != null && !loading.isEmpty())
        {
            return loading;
        }

        try
        {
            String current = net.irisshaders.iris.Iris.getCurrentPackName();

            return current == null ? "" : current;
        }
        catch (Throwable t)
        {
            return "";
        }
    }

    public static void submitWaterPhaseFluid(float fluidMode, Runnable draw)
    {
        if (draw == null)
        {
            return;
        }

        waterPhaseQueue.add(new WaterPhaseEntry(
            new Matrix4f(RenderSystem.getProjectionMatrix()),
            new Matrix4f(RenderSystem.getModelViewMatrix()),
            fluidMode,
            draw
        ));
    }

    /**
     * Vanilla (no Iris) fluid draw — flushed at {@code AFTER_TRANSLUCENT} while the world depth
     * buffer is still intact, so form water/lava occlude behind solid blocks like world fluids.
     * End-of-frame paint overlay draws after depth is no longer trustworthy and x-rayed through stone.
     */
    public static void submitVanillaFluid(float fluidMode, Runnable draw)
    {
        if (draw == null)
        {
            return;
        }

        vanillaFluidQueue.add(new WaterPhaseEntry(
            new Matrix4f(RenderSystem.getProjectionMatrix()),
            new Matrix4f(RenderSystem.getModelViewMatrix()),
            fluidMode,
            draw
        ));
    }

    public static boolean hasQueuedWaterPhaseFluids()
    {
        return !waterPhaseQueue.isEmpty();
    }

    /**
     * Flush vanilla form fluids right after translucent terrain (world water/lava already drawn).
     * Depth-test against the live scene; no polygon offset (that pulled fluids through blocks).
     */
    public static void flushVanillaFluids()
    {
        if (vanillaFluidQueue.isEmpty() || BBSRendering.isIrisShadersEnabled())
        {
            vanillaFluidQueue.clear();

            return;
        }

        List<WaterPhaseEntry> batch = new ArrayList<>(vanillaFluidQueue);

        vanillaFluidQueue.clear();

        Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        boolean savedDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int savedDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        boolean savedPolygonOffset = GL11.glGetBoolean(GL11.GL_POLYGON_OFFSET_FILL);

        try
        {
            MinecraftClient.getInstance().getFramebuffer().beginWrite(false);

            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glPolygonOffset(0F, 0F);

            for (WaterPhaseEntry entry : batch)
            {
                setFormFluid(entry.fluidMode);

                /* Lava is opaque (writes depth); water is translucent (depth test only). */
                RenderSystem.depthMask(entry.fluidMode > 1.5F);

                RenderSystem.setProjectionMatrix(entry.projection, VertexSorter.BY_Z);
                MatrixStackUtils.pushIdentityModelView();

                try
                {
                    entry.draw.run();
                }
                finally
                {
                    MatrixStackUtils.popModelView();
                }
            }
        }
        finally
        {
            clearFormFluid();
            RenderSystem.setProjectionMatrix(savedProjection, VertexSorter.BY_Z);
            RenderSystem.depthFunc(savedDepthFunc);
            RenderSystem.depthMask(savedDepthMask);

            if (savedPolygonOffset)
            {
                GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
            }
            else
            {
                GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
            }

            GL11.glPolygonOffset(0F, 0F);
        }
    }

    /**
     * Called from {@link ShaderOpacityPatch#onAfterTranslucentTerrain()} under Iris, before soft
     * forms flush.
     * <p>
     * Model-view must be identity: {@code draw} already bakes camera×entity into its MatrixStack
     * via capturePaintOverlayRootMatrix. Applying the live camera MV again stuck fluids to the camera.
     */
    public static void flushWaterPhaseFluids()
    {
        if (waterPhaseQueue.isEmpty() || !BBSRendering.isIrisShadersEnabled())
        {
            waterPhaseQueue.clear();

            return;
        }

        List<WaterPhaseEntry> batch = new ArrayList<>(waterPhaseQueue);

        waterPhaseQueue.clear();

        WorldRenderingPipeline pipeline = null;

        try
        {
            pipeline = net.irisshaders.iris.Iris.getPipelineManager().getPipelineNullable();
        }
        catch (Throwable ignored)
        {}

        Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());

        try
        {
            if (pipeline != null)
            {
                pipeline.setOverridePhase(WorldRenderingPhase.TERRAIN_TRANSLUCENT);
            }

            bindIrisTranslucentTarget(pipeline);

            for (WaterPhaseEntry entry : batch)
            {
                setFormFluid(entry.fluidMode);
                uploadToCurrentProgram();

                RenderSystem.setProjectionMatrix(entry.projection, VertexSorter.BY_Z);
                MatrixStackUtils.pushIdentityModelView();

                try
                {
                    entry.draw.run();
                    uploadToCurrentProgram();
                }
                finally
                {
                    MatrixStackUtils.popModelView();
                }
            }
        }
        finally
        {
            clearFormFluid();
            uploadToCurrentProgram();
            RenderSystem.setProjectionMatrix(savedProjection, VertexSorter.BY_Z);

            if (pipeline != null)
            {
                try
                {
                    pipeline.setOverridePhase(null);
                }
                catch (Throwable ignored)
                {}
            }
        }
    }

    private static void bindIrisTranslucentTarget(WorldRenderingPipeline pipeline)
    {
        try
        {
            if (!(pipeline instanceof IrisRenderingPipeline irisPipeline))
            {
                return;
            }

            IrisRenderingPipelineAccessor access =
                (IrisRenderingPipelineAccessor) irisPipeline;

            /* Keep the live depth (terrain + entities + world water) so form water occludes
             * exactly like world water. Copying opaque depth here made it draw over everything. */
            access.bbs$bindDefault();
        }
        catch (Throwable ignored)
        {}
    }

    /**
     * Tag the underlying Iris-extended BufferBuilder so every vertex written afterwards carries
     * real terrain attributes: {@code mc_Entity} = pack block id of the fluid (Complementary
     * water = 32000), fluid render type, plus per-quad {@code mc_midTexCoord} / {@code at_tangent}
     * computed by Iris itself. This makes the pack's {@code gbuffers_water} treat form water
     * exactly like world water (waves, water.glsl color/alpha, foam) with no GLSL guesswork.
     *
     * @return true when the buffer was tagged and {@link #endFluidBlockTag(Object)} must be called.
     */
    public static boolean beginFluidBlockTag(Object buffer, FluidState fluidState, int luminance)
    {
        if (!(buffer instanceof BlockSensitiveBufferBuilder sensitive))
        {
            return false;
        }

        try
        {
            int id = resolveBlockId(fluidState.getBlockState());

            sensitive.beginBlock(id, (byte) ExtendedDataHelper.FLUID_RENDER_TYPE, (byte) luminance, 0, 0, 0);

            return true;
        }
        catch (Throwable t)
        {
            return false;
        }
    }

    /**
     * Tag Iris vertices with the real pack block id so Complementary shadow/gbuffer materials
     * match terrain (solids stay solid — not foliage dither).
     */
    public static boolean beginSolidBlockTag(Object buffer, BlockState state)
    {
        if (state == null || !(buffer instanceof BlockSensitiveBufferBuilder sensitive))
        {
            return false;
        }

        try
        {
            int id = resolveBlockId(state);

            if (id < 0)
            {
                return false;
            }

            sensitive.beginBlock(id, (byte) 0, (byte) 0, 0, 0, 0);

            return true;
        }
        catch (Throwable t)
        {
            return false;
        }
    }

    public static void endFluidBlockTag(Object buffer)
    {
        endBlockTag(buffer);
    }

    public static void endBlockTag(Object buffer)
    {
        if (buffer instanceof BlockSensitiveBufferBuilder sensitive)
        {
            try
            {
                sensitive.endBlock();
            }
            catch (Throwable ignored)
            {}
        }
    }

    /**
     * Pack block id from block.properties (Complementary: water states → 32000).
     */
    private static int resolveBlockId(BlockState state)
    {
        try
        {
            it.unimi.dsi.fastutil.objects.Object2IntMap<BlockState> ids =
                WorldRenderingSettings.INSTANCE.getBlockStateIds();

            if (ids == null || ids.isEmpty() || !ids.containsKey(state))
            {
                return -1;
            }

            return ids.getInt(state);
        }
        catch (Throwable t)
        {
            return -1;
        }
    }

    /**
     * Bind {@code bbs_form_fluid} onto the active GL program. Call from RenderLayer startDrawing
     * (hijack) so the value lands after Iris binds {@code gbuffers_water}.
     */
    public static void uploadToCurrentProgram()
    {
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);

        if (program <= 0)
        {
            return;
        }

        int location = GL20.glGetUniformLocation(program, U_FORM_FLUID);

        if (location >= 0)
        {
            GL20.glUniform1f(location, formFluid);
        }
    }

    public static String processSource(String source)
    {
        if (!shouldPatchPack() || source == null || source.isEmpty())
        {
            return source;
        }

        if (!isWaterProgramSource(source))
        {
            return source;
        }

        if (source.contains(GUARD) && source.contains(U_FORM_FLUID) && source.contains(GUARD_MID))
        {
            patchedThisPack = true;

            return source;
        }

        String patched = insertUniform(source);
        String before = patched;

        patched = patchComplementaryMat(patched);
        patched = patchComplementaryMidCoords(patched);
        patched = patchComplementaryTangent(patched);
        patched = patchBslWaterFlags(patched);

        if (!patched.equals(before) || patched.contains(U_FORM_FLUID))
        {
            patchedThisPack = true;
        }

        return patched;
    }

    private static boolean isWaterProgramSource(String source)
    {
        if (source.contains("GBUFFERS_WATER") || source.contains("gbuffers_water"))
        {
            return true;
        }

        if (source.contains("DoWave") && source.contains("mc_Entity") && source.contains("mat = int(mc_Entity.x"))
        {
            return true;
        }

        if (source.contains("mc_Entity") && BSL_WATER_OR.matcher(source).find())
        {
            return source.contains("gl_Position") || source.contains("attribute") || source.contains("VERTEX_SHADER");
        }

        return false;
    }

    private static String insertUniform(String source)
    {
        if (source.contains(U_FORM_FLUID))
        {
            return source;
        }

        int version = source.indexOf("#version");

        if (version < 0)
        {
            return "uniform float " + U_FORM_FLUID + "; /* " + GUARD + " */\n" + source;
        }

        int nextNewLine = source.indexOf('\n', version);

        if (nextNewLine < 0)
        {
            return source;
        }

        return source.substring(0, nextNewLine + 1)
            + "uniform float " + U_FORM_FLUID + "; /* " + GUARD + " */\n"
            + source.substring(nextNewLine + 1);
    }

    private static String patchComplementaryMat(String source)
    {
        if (source.contains(GUARD) && source.contains("mat = " + COMPLEMENTARY_WATER_MAT))
        {
            return source;
        }

        Matcher matcher = COMP_MAT_ASSIGN.matcher(source);

        if (!matcher.find())
        {
            return source;
        }

        String injection =
            "mat = int(mc_Entity.x + 0.5);\n"
                + " /* " + GUARD + " */\n"
                + " if (" + U_FORM_FLUID + " > 0.5 && " + U_FORM_FLUID + " < 1.5) mat = " + COMPLEMENTARY_WATER_MAT + ";";

        return matcher.replaceFirst(Matcher.quoteReplacement(injection));
    }

    /**
     * Immediate fluid draws omit {@code mc_midTexCoord}. Complementary WATER_STYLE&lt;3 uses
     * {@code absMidCoordPos} as a block resolution — zero produces NaN water UVs / flat tiles.
     */
    private static String patchComplementaryMidCoords(String source)
    {
        if (source.contains(GUARD_MID))
        {
            return source;
        }

        Matcher matcher = ABS_MID_ASSIGN.matcher(source);

        if (!matcher.find())
        {
            return source;
        }

        String injection =
            "absMidCoordPos = abs(texMinMidCoord);\n"
                + " /* " + GUARD_MID + " */\n"
                + " if (" + U_FORM_FLUID + " > 0.5 && " + U_FORM_FLUID + " < 1.5 && absMidCoordPos.x + absMidCoordPos.y < 1e-6) {\n"
                + "  absMidCoordPos = vec2(0.0078125);\n"
                + "  signMidCoordPos = vec2(1.0);\n"
                + " }";

        return matcher.replaceFirst(Matcher.quoteReplacement(injection));
    }

    /**
     * Immediate draws omit {@code at_tangent}. Complementary WATER_STYLE≥2 builds wave normals
     * via TBN — synthesize a stable tangent/binormal for form water.
     */
    private static String patchComplementaryTangent(String source)
    {
        if (source.contains(GUARD_TANGENT))
        {
            return source;
        }

        Matcher matcher = TANGENT_NORMALIZE.matcher(source);

        if (!matcher.find())
        {
            return source;
        }

        String injection =
            "tangent = rawTangent * inversesqrt(max(dot(rawTangent, rawTangent), 1e-8));\n"
                + " /* " + GUARD_TANGENT + " */\n"
                + " if (" + U_FORM_FLUID + " > 0.5 && " + U_FORM_FLUID + " < 1.5 && length(tangent) < 1e-3) {\n"
                + "  tangent = normalize(cross(normal, upVec));\n"
                + "  if (length(tangent) < 1e-3) tangent = normalize(eastVec);\n"
                + "  binormal = normalize(cross(normal, tangent));\n"
                + " }";

        return matcher.replaceFirst(Matcher.quoteReplacement(injection));
    }

    private static String patchBslWaterFlags(String source)
    {
        Matcher or = BSL_WATER_OR.matcher(source);

        if (!or.find())
        {
            return source;
        }

        return or.replaceAll("(mc_Entity.x == 8.0 || mc_Entity.x == 9.0 || " + U_FORM_FLUID + " > 0.5)");
    }
}