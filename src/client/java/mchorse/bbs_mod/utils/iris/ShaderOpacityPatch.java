package mchorse.bbs_mod.utils.iris;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.cubic.render.vao.ModelVAORenderer;
import mchorse.bbs_mod.mixin.client.iris.IrisRenderingPipelineAccessor;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.WindowFramebuffer;

import net.irisshaders.iris.gl.texture.DepthCopyStrategy;
import net.irisshaders.iris.helpers.OptionalBoolean;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.properties.ShaderProperties;
import net.irisshaders.iris.targets.RenderTargets;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runtime soft-opacity queue. Soft forms draw after translucent terrain with depth writes
 * (fluids stay, limbs do not X-ray). Pack GLSL / shaders.properties are left vanilla —
 * Complementary light shafts sample the same shadow map those patches used to rewrite.
 * <p>
 * Fabulous (no shaders): soft flushes into the translucent FB before combine so soft remains
 * visible. Soft limbs viewed through soft billboards can look washed — accepted limitation;
 * see {@code docs/SOFT_OPACITY_FABULOUS.md}.
 */
public class ShaderOpacityPatch
{
    private static final List<PostDeferredEntry> postDeferredForms = new ArrayList<>();
    private static boolean postDeferredPhase;
    private static boolean flushingPostDeferred;
    private static boolean flushingDepthWrite = true;
    private static boolean forceLiveDepthWrite;
    private static boolean suppressLiveDepthWrite;

    private static String loadingPackName = "";

    /**
     * Opaque Iris depth snapshotted at {@code beginTranslucents} (before AAA Particles can
     * blit a cleared main-FB depth over the live pipeline). Used by paint overlays at frame end.
     */
    private static Framebuffer paintOpaqueDepthStash;
    private static boolean paintOpaqueDepthStashValid;

    private static final class PostDeferredEntry
    {
        private final double renderDepth;
        private final double distanceSq;
        private final boolean depthWrite;
        private final boolean afterFluids;
        private final boolean irisCamera;
        private final Matrix4f projection;
        private final Matrix4f modelView;
        private final ModelVAORenderer.DeferredFogSnapshot fog;
        private final Runnable draw;

        private PostDeferredEntry(double renderDepth, double distanceSq, boolean depthWrite, boolean afterFluids, boolean irisCamera, Matrix4f projection, Matrix4f modelView, ModelVAORenderer.DeferredFogSnapshot fog, Runnable draw)
        {
            this.renderDepth = renderDepth;
            this.distanceSq = distanceSq;
            this.depthWrite = depthWrite;
            this.afterFluids = afterFluids;
            this.irisCamera = irisCamera;
            this.projection = projection;
            this.modelView = modelView;
            this.fog = fog;
            this.draw = draw;
        }
    }

    public static void setLoadingPackName(String name)
    {
        loadingPackName = name == null ? "" : name;
    }

    public static String getLoadingPackName()
    {
        return loadingPackName == null ? "" : loadingPackName;
    }

    public static void clearLoadingPackName()
    {
        loadingPackName = "";
    }

    public static boolean isComplementaryPack(String name)
    {
        return name != null && name.toLowerCase(Locale.ROOT).contains("complementary");
    }

    public static boolean isBslPack(String name)
    {
        return name != null && name.toLowerCase(Locale.ROOT).contains("bsl");
    }

    /**
     * Settings toggle for the Iris opacity-fix path. Pack GLSL is no longer rewritten
     * ({@link #shouldApplyPackGlslPatches}); soft forms use the post-deferred queue either way.
     */
    public static boolean isActive()
    {
        if (BBSSettings.irisOpacityFix != null && BBSSettings.irisOpacityFix.get())
        {
            return true;
        }

        /* Legacy: old Complementary/BSL toggles before migration. */
        if (BBSSettings.complementaryOpacityFix != null && BBSSettings.complementaryOpacityFix.get()
            && isComplementaryPack(resolvePackName()))
        {
            return true;
        }

        return BBSSettings.bslOpacityFix != null && BBSSettings.bslOpacityFix.get()
            && isBslPack(resolvePackName());
    }

    /**
     * Pack GLSL / shaders.properties rewrites. Always off: Complementary 5.8 light shafts
     * share shadowtex with lighting, and the old wrap / alpha-test / separateEntityDraws
     * patches leaked god rays through solid terrain.
     */
    public static boolean shouldApplyPackGlslPatches()
    {
        return false;
    }

    private static String resolvePackName()
    {
        if (loadingPackName != null && !loadingPackName.isEmpty())
        {
            return loadingPackName;
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

    public static void setForceLiveDepthWrite(boolean force)
    {
        forceLiveDepthWrite = force;
    }

    public static void setSuppressLiveDepthWrite(boolean suppress)
    {
        suppressLiveDepthWrite = suppress;
    }

    public static void reassertPostDeferredDepthState()
    {
        if (flushingPostDeferred)
        {
            reassertPostDeferredDepthState(flushingDepthWrite);

            return;
        }

        if (forceLiveDepthWrite)
        {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);
        }
        else if (suppressLiveDepthWrite)
        {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);
        }
    }

    public static void reassertPostDeferredDepthState(boolean depthWrite)
    {
        if (!flushingPostDeferred)
        {
            return;
        }

        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(depthWrite);
    }

    /**
     * Override {@link #flushingDepthWrite} mid-entry. Required for soft color-then-stamp:
     * {@code ModelVAORenderer.render} calls {@link #reassertPostDeferredDepthState()} with no
     * args and would otherwise restore the queue entry's depthWrite (undoing a local
     * {@code depthMask(false)}).
     */
    public static void setFlushingDepthWrite(boolean depthWrite)
    {
        if (!flushingPostDeferred)
        {
            return;
        }

        flushingDepthWrite = depthWrite;
        reassertPostDeferredDepthState(depthWrite);
    }

    /**
     * True while {@link #flushPostDeferredForms} is iterating queue entries.
     * Soft Block/Structure must not tear down lightmap/overlay here — later soft limbs in the
     * same flush still need them (player-position sort makes contamination look angle-independent).
     */
    public static boolean isFlushingPostDeferred()
    {
        return flushingPostDeferred;
    }

    /**
     * True while Iris is in the post-deferred translucent phase (after clouds are composited).
     */
    public static boolean isPostDeferredPhase()
    {
        return postDeferredPhase;
    }

    /**
     * Fully opaque floor. Softer alpha joins the post-deferred queue (after VL clouds /
     * translucent terrain; vanilla also waits until after vanilla clouds via LAST) with depth
     * write so limbs do not X-ray and fluids stay intact.
     * Fully solid keeps the live path.
     */
    public static final float LIVE_DEPTH_WRITE_ALPHA = 0.999F;

    /**
     * Queue soft-opacity forms until after translucent terrain.
     * Works with or without Iris and with or without the Complementary/BSL opacity patch —
     * patched packs get the best lighting; unpatched / no-shader still get correct depth
     * occlusion and no self X-ray. Never delay the shadow pass.
     */
    public static boolean shouldDelayUntilPostDeferred(float alpha)
    {
        if (postDeferredPhase || flushingPostDeferred || alpha <= 0.001F)
        {
            return false;
        }

        try
        {
            /* Casters must hit the shadow map live — post-deferred never writes shadows. */
            if (BBSRendering.isIrisShadowPass())
            {
                return false;
            }
        }
        catch (Throwable t)
        {
            return false;
        }

        /* Soft opacity: after fluids + depth write (water stays, no self X-ray). */
        return alpha < LIVE_DEPTH_WRITE_ALPHA;
    }

    /**
     * Live-path fallback only. Soft opacity should already be post-deferred; if a draw still
     * lands live, keep depth writes so the mesh does not X-ray itself (screenshot at 254).
     */
    public static boolean shouldSuppressDepthWrite(float alpha)
    {
        return false;
    }

    /**
     * Soft opacity waits until after water/lava/portals.
     */
    public static boolean shouldFlushAfterFluids(float alpha)
    {
        return alpha > 0.001F && alpha < LIVE_DEPTH_WRITE_ALPHA;
    }

    /**
     * Post-deferred meshes always write depth when visible so limbs do not X-ray themselves.
     * Soft forms still keep fluids: they flush {@link #shouldFlushAfterFluids after fluids},
     * so depth stamps cannot erase water/lava/portals already in the color buffer.
     */
    public static boolean shouldWriteDepthForOpacity(float alpha)
    {
        return alpha > 0.001F;
    }

    public static boolean shouldForceLiveDepthWrite(float alpha)
    {
        /* Near-opaque live path — force depth even if a pack left depthMask false. */
        return alpha >= LIVE_DEPTH_WRITE_ALPHA;
    }

    /**
     * Iris-lit mesh: restore camera ModelView; pass entity-local stack matrices in {@code draw}.
     */
    public static void submitPostDeferredForm(double renderDepth, boolean depthWrite, boolean afterFluids, Runnable draw)
    {
        submit(renderDepth, 0D, depthWrite, afterFluids, true, draw);
    }

    public static void submitPostDeferredForm(double renderDepth, double distanceSq, boolean depthWrite, boolean afterFluids, Runnable draw)
    {
        submit(renderDepth, distanceSq, depthWrite, afterFluids, true, draw);
    }

    /**
     * BBS model-shader flat: identity ModelView; pass camera-baked stack matrices in {@code draw}.
     */
    public static void submitPostDeferredBbsForm(double renderDepth, boolean depthWrite, boolean afterFluids, Runnable draw)
    {
        submit(renderDepth, 0D, depthWrite, afterFluids, false, draw);
    }

    public static void submitPostDeferredBbsForm(double renderDepth, double distanceSq, boolean depthWrite, boolean afterFluids, Runnable draw)
    {
        submit(renderDepth, distanceSq, depthWrite, afterFluids, false, draw);
    }

    public static void submitPostDeferredForm(Runnable draw)
    {
        submitPostDeferredForm(0D, 0D, true, false, draw);
    }

    private static void submit(double renderDepth, double distanceSq, boolean depthWrite, boolean afterFluids, boolean irisCamera, Runnable draw)
    {
        if (draw == null)
        {
            return;
        }

        postDeferredForms.add(new PostDeferredEntry(
            renderDepth,
            distanceSq,
            depthWrite,
            afterFluids,
            irisCamera,
            new Matrix4f(RenderSystem.getProjectionMatrix()),
            new Matrix4f(RenderSystem.getModelViewMatrix()),
            ModelVAORenderer.captureCurrentFog(),
            draw
        ));
    }

    public static void onBeginTranslucents()
    {
        /* Soft-opacity (and other after-fluids) forms must not flush here: Iris beginTranslucents
         * can run mid-frame while WorldRenderer still has an unbalanced pose stack; flushing
         * then throws IllegalStateException on pop(). Only mark the phase — actual soft-opacity
         * flush is WorldRenderEvents.AFTER_TRANSLUCENT / onAfterTranslucentTerrain().
         * Paint/blend/grade overlays stay queued until onWorldRenderEnd — Iris composites after
         * translucent terrain would overwrite an early color-tint multiply. */
        postDeferredPhase = true;
        /* Iris has just copied opaque depth into depthtex1. Stash it before AAA Particles
         * (Fabric + shaders) pastes a cleared main-FB depth onto the bound FBO before hand. */
        stashIrisOpaqueDepthForPaint();
    }

    /**
     * After translucent terrain (water/lava/portals).
     * <p>
     * Iris: flush soft forms here (pack clouds are already composited on that path).
     * Vanilla Fancy: do <em>not</em> flush yet — wait until {@link #onAfterVanillaClouds()} so
     * soft depth does not erase clouds.
     * Vanilla Fabulous: flush into the translucent framebuffer <em>before</em> the translucency
     * combine; drawing soft only at LAST often never appears on Fabulous.
     * <p>
     * <b>Known limitation (accepted):</b> Fabulous without shaders can wash / over-brighten soft
     * limbs seen through soft billboards. Fancy composites soft over final main color; Fabulous
     * layer combine is not equivalent. Moving soft to main/{@code LAST} or the entity FB fixes
     * wash partially but regresses occlusion or soft-vs-soft — see
     * {@code docs/SOFT_OPACITY_FABULOUS.md}. Do not re-shuffle Fabulous flush targets casually.
     */
    public static void onAfterTranslucentTerrain()
    {
        if (BBSRendering.isIrisShadersEnabled())
        {
            flushPostDeferredForms(null);

            return;
        }

        postDeferredPhase = true;

        if (MinecraftClient.isFabulousGraphicsOrBetter())
        {
            bindVanillaSoftFlushTarget(true);
            flushPostDeferredForms(null);
        }
    }

    /**
     * After vanilla clouds / weather ({@code WorldRenderEvents.LAST}).
     * Fancy: primary soft flush (after clouds). Fabulous: leftovers onto the main target
     * (main soft already flushed before Fabulous combine). Iris: no-op.
     */
    public static void onAfterVanillaClouds()
    {
        if (BBSRendering.isIrisShadersEnabled())
        {
            return;
        }

        bindVanillaSoftFlushTarget(false);
        flushPostDeferredForms(null);
    }

    /**
     * @param fabulousTranslucentPass {@code true} = Fabulous translucent FB before combine;
     *                                {@code false} = visible main framebuffer.
     */
    private static void bindVanillaSoftFlushTarget(boolean fabulousTranslucentPass)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null)
        {
            return;
        }

        if (fabulousTranslucentPass && mc.worldRenderer != null)
        {
            Framebuffer translucent = mc.worldRenderer.getTranslucentFramebuffer();

            if (translucent != null)
            {
                translucent.beginWrite(false);

                return;
            }
        }

        if (mc.getFramebuffer() != null)
        {
            mc.getFramebuffer().beginWrite(false);
        }
    }

    public static void onWorldRenderBegin()
    {
        postDeferredForms.clear();
        postDeferredPhase = false;
        flushingPostDeferred = false;
        paintOpaqueDepthStashValid = false;
    }

    public static void onWorldRenderEnd()
    {
        flushPostDeferredForms(null);
        postDeferredPhase = false;
    }

    public static void flushPostDeferredForms()
    {
        flushPostDeferredForms(null);
    }

    /**
     * @param afterFluidsOnly {@code true} = soft opacity (after water/lava/portals);
     *                        {@code false} = early batch (beginTranslucents);
     *                        {@code null} = everything remaining (frame-end safety net).
     */
    private static void flushPostDeferredForms(Boolean afterFluidsOnly)
    {
        if (postDeferredForms.isEmpty())
        {
            return;
        }

        List<PostDeferredEntry> batch = new ArrayList<>();

        for (PostDeferredEntry entry : postDeferredForms)
        {
            if (afterFluidsOnly == null || entry.afterFluids == afterFluidsOnly)
            {
                batch.add(entry);
            }
        }

        if (batch.isEmpty())
        {
            return;
        }

        postDeferredForms.removeAll(batch);
        flushingPostDeferred = true;

        try
        {
            /* Same order as film entities: lower render depth first; within a depth, farther
             * first so closer forms depth-test against what is already in the buffer. */
            batch.sort(Comparator
                .comparingDouble((PostDeferredEntry entry) -> entry.renderDepth)
                .thenComparing((PostDeferredEntry a, PostDeferredEntry b) -> Double.compare(b.distanceSq, a.distanceSq))
            );

            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            MinecraftClient mc = MinecraftClient.getInstance();

            if (mc != null && mc.gameRenderer != null)
            {
                mc.gameRenderer.getLightmapTextureManager().enable();
                mc.gameRenderer.getOverlayTexture().setupOverlayColor();
            }

            for (PostDeferredEntry entry : batch)
            {
                runEntry(entry);
            }
        }
        finally
        {
            flushingPostDeferred = false;
            /* Soft-opacity flushes can leave depthMask dirty for later world draws. */
            RenderSystem.depthMask(true);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        }
    }

    /**
     * Restores terrain-accurate depth on the paint overlay target before paint / grade / tint
     * flushes ({@code depthMask false}, {@code depthTest LEQUAL}). Iris deferred packs and AAA
     * Particles depth capture/paste can leave the visible framebuffer's depth stale or empty.
     * <p>
     * With Iris shaders + AAA Particles (Fabric): AAA captures depth at {@code LevelRenderer}
     * return from the bound DRAW FBO (often the composited main FB with cleared/useless depth),
     * then {@code pasteToCurrentDepthFrom} before hand — wiping occlusion for later paint.
     * Prefer the opaque depth stash from {@code beginTranslucents} over a live Iris query.
     */
    public static void syncPaintOverlayDepth()
    {
        BBSRendering.ensurePaintOverlayTargetFramebuffer();

        try
        {
            if (BBSRendering.isIrisShadersEnabled())
            {
                syncIrisDepthToPaintTarget();
            }
            else
            {
                syncVanillaPaintOverlayDepth();
            }
        }
        catch (Throwable ignored)
        {
            /* Iris API drift or optional mod reflection — still attempt overlays. */
        }

        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
    }

    private static int resolvePaintOverlayDepthAttachment()
    {
        Framebuffer framebuffer = BBSRendering.getPaintOverlaySourceFramebuffer();

        return framebuffer != null ? framebuffer.getDepthAttachment() : 0;
    }

    private static void copyDepthTextureToPaintTarget(int sourceDepth, int width, int height)
    {
        int targetDepth = resolvePaintOverlayDepthAttachment();

        if (sourceDepth <= 0 || targetDepth <= 0 || sourceDepth == targetDepth || width <= 0 || height <= 0)
        {
            return;
        }

        DepthCopyStrategy.fastest(false)
            .copy(null, sourceDepth, null, targetDepth, width, height);
    }

    private static void stashIrisOpaqueDepthForPaint()
    {
        paintOpaqueDepthStashValid = false;

        try
        {
            WorldRenderingPipeline pipeline =
                net.irisshaders.iris.Iris.getPipelineManager().getPipelineNullable();

            if (!(pipeline instanceof IrisRenderingPipeline irisPipeline))
            {
                return;
            }

            IrisRenderingPipelineAccessor access = (IrisRenderingPipelineAccessor) irisPipeline;
            RenderTargets targets = access.bbs$renderTargets();

            if (targets == null)
            {
                return;
            }

            int width = targets.getCurrentWidth();
            int height = targets.getCurrentHeight();
            int opaqueDepth = targets.getDepthTextureNoTranslucents().getTextureId();

            if (width <= 0 || height <= 0 || opaqueDepth <= 0)
            {
                Framebuffer paint = BBSRendering.getPaintOverlaySourceFramebuffer();

                if (paint != null)
                {
                    width = paint.textureWidth;
                    height = paint.textureHeight;
                }
            }

            if (width <= 0 || height <= 0 || opaqueDepth <= 0)
            {
                return;
            }

            ensurePaintOpaqueDepthStash(width, height);
            DepthCopyStrategy.fastest(false)
                .copy(null, opaqueDepth, null, paintOpaqueDepthStash.getDepthAttachment(), width, height);
            paintOpaqueDepthStashValid = paintOpaqueDepthStash.getDepthAttachment() > 0;
        }
        catch (Throwable ignored)
        {
            paintOpaqueDepthStashValid = false;
        }
    }

    private static void ensurePaintOpaqueDepthStash(int width, int height)
    {
        if (paintOpaqueDepthStash == null)
        {
            paintOpaqueDepthStash = new WindowFramebuffer(width, height);
        }
        else if (paintOpaqueDepthStash.textureWidth != width || paintOpaqueDepthStash.textureHeight != height)
        {
            paintOpaqueDepthStash.resize(width, height);
        }
    }

    private static void syncIrisDepthToPaintTarget()
    {
        Framebuffer paintTarget = BBSRendering.getPaintOverlaySourceFramebuffer();
        int paintWidth = paintTarget != null ? paintTarget.textureWidth : 0;
        int paintHeight = paintTarget != null ? paintTarget.textureHeight : 0;

        /* Prefer the beginTranslucents stash — survives AAA's pre-hand depth paste. */
        if (paintOpaqueDepthStashValid && paintOpaqueDepthStash != null)
        {
            int stashDepth = paintOpaqueDepthStash.getDepthAttachment();
            int width = paintOpaqueDepthStash.textureWidth;
            int height = paintOpaqueDepthStash.textureHeight;

            if (paintWidth > 0 && paintHeight > 0)
            {
                width = paintWidth;
                height = paintHeight;
            }

            copyDepthTextureToPaintTarget(stashDepth, width, height);
            blitFramebufferDepth(paintOpaqueDepthStash, paintTarget);

            return;
        }

        WorldRenderingPipeline pipeline =
            net.irisshaders.iris.Iris.getPipelineManager().getPipelineNullable();

        if (!(pipeline instanceof IrisRenderingPipeline irisPipeline))
        {
            return;
        }

        IrisRenderingPipelineAccessor access = (IrisRenderingPipelineAccessor) irisPipeline;
        RenderTargets targets = access.bbs$renderTargets();

        if (targets == null)
        {
            return;
        }

        int width = targets.getCurrentWidth();
        int height = targets.getCurrentHeight();
        int opaqueDepth = targets.getDepthTextureNoTranslucents().getTextureId();

        if ((width <= 0 || height <= 0) && paintWidth > 0 && paintHeight > 0)
        {
            width = paintWidth;
            height = paintHeight;
        }

        if (opaqueDepth > 0)
        {
            copyDepthTextureToPaintTarget(opaqueDepth, width, height);
        }
    }

    /**
     * AAA-style depth blit between Minecraft framebuffers (restores READ/DRAW bindings).
     * Used when Iris {@link DepthCopyStrategy} alone is not enough after AAA's own blit.
     */
    private static void blitFramebufferDepth(Framebuffer source, Framebuffer target)
    {
        if (source == null || target == null || source == target)
        {
            return;
        }

        int sourceDepth = source.getDepthAttachment();
        int targetDepth = target.getDepthAttachment();

        if (sourceDepth <= 0 || targetDepth <= 0 || sourceDepth == targetDepth)
        {
            return;
        }

        int readBackup = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int drawBackup = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);

        try
        {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.fbo);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target.fbo);
            GL30.glBlitFramebuffer(
                0, 0, source.textureWidth, source.textureHeight,
                0, 0, target.textureWidth, target.textureHeight,
                GL11.GL_DEPTH_BUFFER_BIT,
                GL11.GL_NEAREST
            );
        }
        finally
        {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readBackup);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawBackup);
        }
    }

    private static void syncVanillaPaintOverlayDepth()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc == null)
        {
            return;
        }

        if (FabricLoader.getInstance().isModLoaded("aaa_particles"))
        {
            pasteAAAParticlesCapturedWorldDepth();
        }

        Framebuffer paintTarget = BBSRendering.getPaintOverlaySourceFramebuffer();
        Framebuffer mainTarget = mc.getFramebuffer();

        if (paintTarget == null || mainTarget == null)
        {
            return;
        }

        int paintDepth = paintTarget.getDepthAttachment();
        int mainDepth = mainTarget.getDepthAttachment();

        if (paintDepth > 0 && mainDepth > 0 && paintDepth != mainDepth)
        {
            copyDepthTextureToPaintTarget(mainDepth, mainTarget.textureWidth, mainTarget.textureHeight);
        }
    }

    /**
     * AAA Particles defers Effekseer draws and {@code pasteToCurrentDepthFrom} its captured depth
     * mid-frame; hand/particle depth writes afterward can desync the buffer paint overlays test
     * against. Re-paste the world snapshot onto the paint target before overlay flush.
     */
    private static void pasteAAAParticlesCapturedWorldDepth()
    {
        try
        {
            Class<?> captureClass = Class.forName("mod.chloeprime.aaaparticles.client.internal.RenderStateCapture");
            Field capturedField = captureClass.getField("CAPTURED_WORLD_DEPTH_BUFFER");
            Object capturedBuffer = capturedField.get(null);

            if (capturedBuffer == null)
            {
                return;
            }

            Class<?> renderUtilClass = Class.forName("mod.chloeprime.aaaparticles.client.render.RenderUtil");

            for (Method method : renderUtilClass.getMethods())
            {
                if (!method.getName().equals("pasteToCurrentDepthFrom") || method.getParameterCount() != 1)
                {
                    continue;
                }

                method.invoke(null, capturedBuffer);

                return;
            }
        }
        catch (Throwable ignored)
        {
        }
    }

    /**
     * Complementary/BSL deferred can leave the live depth buffer unusable for occlusion. Iris
     * snapshots opaque depth into {@code depthtex1} at {@code beginTranslucents}; copy it back
     * so translucent BBS forms depth-test against models/terrain in front (render depth).
     *
     * @param bindIrisDefault when true, draw into Iris' translucent target (mid-pipeline only).
     *                        At world-render end keep Minecraft/film FB so draws stay visible.
     */
    private static void preparePostDeferredFramebufferAndDepth(boolean bindIrisDefault)
    {
        try
        {
            BBSRendering.ensurePaintOverlayTargetFramebuffer();

            WorldRenderingPipeline pipeline =
                net.irisshaders.iris.Iris.getPipelineManager().getPipelineNullable();

            if (!(pipeline instanceof IrisRenderingPipeline irisPipeline))
            {
                return;
            }

            IrisRenderingPipelineAccessor access = (IrisRenderingPipelineAccessor) irisPipeline;
            RenderTargets targets = access.bbs$renderTargets();

            if (targets == null)
            {
                return;
            }

            int width = targets.getCurrentWidth();
            int height = targets.getCurrentHeight();
            int opaqueDepth = targets.getDepthTextureNoTranslucents().getTextureId();
            int liveDepth = targets.getDepthTexture();

            if (width > 0 && height > 0 && opaqueDepth > 0 && liveDepth > 0)
            {
                DepthCopyStrategy.fastest(false)
                    .copy(null, opaqueDepth, null, liveDepth, width, height);
            }

            if (bindIrisDefault)
            {
                access.bbs$bindDefault();
            }
            else
            {
                /* Depth copy may have switched FBOs — return to the visible target. */
                BBSRendering.ensurePaintOverlayTargetFramebuffer();
            }
        }
        catch (Throwable ignored)
        {
            /* Iris API drift — still attempt draws with whatever depth is bound. */
        }
    }

    private static void runEntry(PostDeferredEntry entry)
    {
        Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        Matrix4f savedModelView = new Matrix4f(modelViewStack);
        boolean savedDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean beganDeferredPass = false;

        try
        {
            RenderSystem.setProjectionMatrix(entry.projection, ProjectionType.ORTHOGRAPHIC);
            flushingDepthWrite = entry.depthWrite;
            RenderSystem.depthMask(entry.depthWrite);

            /* Never push/pop ModelView during world render — unbalanced depth trips
             * WorldRenderer's "Pose stack not empty" check with Iris/Sodium. */
            if (entry.irisCamera)
            {
                modelViewStack.set(entry.modelView);
            }
            else
            {
                modelViewStack.identity();
                ModelVAORenderer.beginDeferredTranslucentModelPass(entry.depthWrite, true);
                beganDeferredPass = true;
            }

            reassertPostDeferredDepthState(entry.depthWrite);

            if (entry.fog != null)
            {
                ModelVAORenderer.pushDeferredFog(entry.fog);
            }

            try
            {
                entry.draw.run();
            }
            finally
            {
                if (entry.fog != null)
                {
                    ModelVAORenderer.popDeferredFog();
                }
            }
        }
        finally
        {
            if (beganDeferredPass)
            {
                ModelVAORenderer.endDeferredTranslucentModelPass();
            }

            /* Isolate entries: soft Block/Structure can leave lightmap off, additive blend,
             * or colorMask false — that darkens soft limbs drawn later in the same flush. */
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
            RenderSystem.depthMask(savedDepthMask);
            if (flushingPostDeferred)
            {
                MinecraftClient mc = MinecraftClient.getInstance();

                if (mc != null && mc.gameRenderer != null)
                {
                    mc.gameRenderer.getLightmapTextureManager().enable();
                    mc.gameRenderer.getOverlayTexture().setupOverlayColor();
                }

                flushingDepthWrite = entry.depthWrite;
                reassertPostDeferredDepthState(entry.depthWrite);
            }

            RenderSystem.setProjectionMatrix(savedProjection, ProjectionType.ORTHOGRAPHIC);
            modelViewStack.set(savedModelView);
        }
    }

    public static String patchPropertiesContents(String contents)
    {
        /* Pack shaders.properties stay vanilla. Forcing separateEntityDraws and rewriting
         * GLSL/alpha tests is what the opacity-fix toggle used to do, and it leaks
         * Complementary light shafts through solid terrain. Soft forms already use the
         * post-deferred queue without mutating the pack. */
        return contents;
    }

    public static void applyAlphaTestOverrides(ShaderProperties properties)
    {
        /* No-op: hardware alphaTest GREATER 0.0001 on gbuffers was part of the VL leak. */
    }

    public static void applySeparateEntityDraws(Consumer<OptionalBoolean> setter)
    {
        /* No-op: Complementary does not set separateEntityDraws. */
    }

    public static String processSource(String source)
    {
        if (source == null || source.isEmpty())
        {
            return source;
        }

        if (BBSSettings.shaderShadowDither != null && BBSSettings.shaderShadowDither.get())
        {
            return processShadowCasterAlpha(source);
        }

        return source;
    }

    public static void beginShadowForm()
    {
        uploadShadowFormUniform(1F);
    }

    public static void endShadowForm()
    {
        uploadShadowFormUniform(0F);
    }

    public static void uploadShadowFormUniform()
    {
        if (BBSRendering.isIrisShadowPass())
        {
            uploadShadowFormUniform(1F);
        }
    }

    public static void uploadShadowFormUniform(float value)
    {
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);

        if (program > 0)
        {
            int location = GL20.glGetUniformLocation(program, "bbs_is_shadow_form");

            if (location >= 0)
            {
                GL20.glUniform1f(location, value);
            }
        }
    }

    private static String insertShadowUniform(String source)
    {
        if (source.contains("bbs_is_shadow_form"))
        {
            return source;
        }

        int version = source.indexOf("#version");

        if (version < 0)
        {
            return "uniform float bbs_is_shadow_form;\n" + source;
        }

        int nextNewLine = source.indexOf('\n', version);

        if (nextNewLine < 0)
        {
            return source + "\nuniform float bbs_is_shadow_form;\n";
        }

        return source.substring(0, nextNewLine + 1) + "uniform float bbs_is_shadow_form;\n" + source.substring(nextNewLine + 1);
    }

    private static final Pattern PHOTON_TEX_ALPHA_DISCARD = Pattern.compile(
        "if\\s*\\(\\s*base_color\\.a\\s*<\\s*0\\.1\\s*\\)\\s*\\{\\s*discard\\s*;\\s*\\}"
    );
    private static final Pattern BLISS_SHADOW_FRAGDATA = Pattern.compile(
        "gl_FragData\\[0]\\s*=\\s*vec4\\(\\s*texture2D\\(\\s*tex\\s*,\\s*texcoord\\.xy\\s*\\)\\.rgb\\s*\\*\\s*color\\.rgb\\s*,\\s*texture2DLod\\(\\s*tex\\s*,\\s*texcoord\\.xy\\s*,\\s*0\\s*\\)\\.a\\s*\\)\\s*;"
    );

    /**
     * Ordered Bayer 4x4 dither discard on soft BBS shadow casters
     * ({@code bbs_is_shadow_form > 0.5}) when shader_shadow_dither is enabled.
     * Anchors: Complementary, BSL, Photon, Bliss.
     */
    public static String processShadowCasterAlpha(String source)
    {
        if (source == null || source.isEmpty())
        {
            return source;
        }

        if (source.contains("BBS_SHADOW_CASTER_DITHER") || source.contains("bbs_gl_color_a = gl_Color.a"))
        {
            return source;
        }

        /* Complementary shadow.glsl */
        if (source.contains("DoNaturalShadowCalculation"))
        {
            String dither = buildShadowDitherBlock("glColor.a", "    ");
            String patched = insertShadowUniform(source);

            if (patched.contains("gl_FragData[0] = color1;"))
            {
                return patched.replace(
                    "gl_FragData[0] = color1;",
                    dither + "    gl_FragData[0] = color1;"
                );
            }

            if (patched.contains("shadowColor = color1;"))
            {
                return patched.replace(
                    "shadowColor = color1;",
                    dither + "    shadowColor = color1;"
                );
            }
        }

        /* BSL shadow.glsl */
        if (source.contains("float premult = float(mat > 0.98") && source.contains("gl_FragData[0] = albedo;"))
        {
            String dither = buildShadowDitherBlock("color.a", "\t");
            String patched = insertShadowUniform(source);

            return patched.replace(
                "\tgl_FragData[0] = albedo;",
                dither + "\tgl_FragData[0] = albedo;"
            );
        }

        /* Photon: vertex only passes tint.rgb — forward gl_Color.a for FS dither. */
        if (isPhotonShadowVertex(source))
        {
            return patchPhotonShadowVertex(source);
        }

        if (isPhotonShadowFragment(source))
        {
            return patchPhotonShadowFragment(source);
        }

        /* Bliss: native Stochastic_Transparent_Shadows uses texture alpha; soft forms need color.a. */
        if (isBlissShadowFragment(source))
        {
            return patchBlissShadowFragment(source);
        }

        return source;
    }

    private static String buildShadowDitherBlock(String alphaExpr, String indent)
    {
        return indent + "/* BBS_SHADOW_CASTER_DITHER */\n"
            + indent + "if (bbs_is_shadow_form > 0.5 && " + alphaExpr + " < 0.999) {\n"
            + indent + "    const float bbsBayer4x4[16] = float[16](\n"
            + indent + "        0.0625, 0.5625, 0.1875, 0.6875,\n"
            + indent + "        0.8125, 0.3125, 0.9375, 0.4375,\n"
            + indent + "        0.2500, 0.7500, 0.1250, 0.6250,\n"
            + indent + "        1.0000, 0.5000, 0.8750, 0.3750\n"
            + indent + "    );\n"
            + indent + "    ivec2 bbsCoord = ivec2(mod(gl_FragCoord.xy, 4.0));\n"
            + indent + "    if (" + alphaExpr + " < bbsBayer4x4[bbsCoord.y * 4 + bbsCoord.x]) discard;\n"
            + indent + "}\n";
    }

    private static boolean isPhotonShadowVertex(String source)
    {
        return source.contains("flat out vec3 tint")
            && source.contains("tint = gl_Color.rgb")
            && (source.contains("distort_shadow_space") || source.contains("shadow_clip_pos") || source.contains("material_mask"));
    }

    private static boolean isPhotonShadowFragment(String source)
    {
        return source.contains("shadowcolor0_out")
            && source.contains("flat in vec3 tint")
            && source.contains("base_color.a");
    }

    private static boolean isBlissShadowFragment(String source)
    {
        return source.contains("Stochastic_Transparent_Shadows")
            && source.contains("blueNoise")
            && source.contains("texture2DLod")
            && source.contains("gl_FragData[0]");
    }

    private static String patchPhotonShadowVertex(String source)
    {
        String patched = source;

        if (!patched.contains("bbs_gl_color_a"))
        {
            if (patched.contains("flat out vec3 tint;"))
            {
                patched = patched.replace(
                    "flat out vec3 tint;",
                    "flat out vec3 tint;\nflat out float bbs_gl_color_a;"
                );
            }
            else
            {
                return source;
            }
        }

        if (!patched.contains("bbs_gl_color_a = gl_Color.a"))
        {
            if (patched.contains("tint = gl_Color.rgb;"))
            {
                patched = patched.replace(
                    "tint = gl_Color.rgb;",
                    "tint = gl_Color.rgb;\n\tbbs_gl_color_a = gl_Color.a;"
                );
            }
            else
            {
                return source;
            }
        }

        return patched;
    }

    private static String patchPhotonShadowFragment(String source)
    {
        String patched = insertShadowUniform(source);

        if (!patched.contains("flat in float bbs_gl_color_a"))
        {
            if (patched.contains("flat in vec3 tint;"))
            {
                patched = patched.replace(
                    "flat in vec3 tint;",
                    "flat in vec3 tint;\nflat in float bbs_gl_color_a;"
                );
            }
            else
            {
                return source;
            }
        }

        String dither = buildShadowDitherBlock("bbs_gl_color_a", "\t");
        Matcher matcher = PHOTON_TEX_ALPHA_DISCARD.matcher(patched);

        if (!matcher.find())
        {
            return source;
        }

        StringBuffer buffer = new StringBuffer();

        matcher.reset();

        while (matcher.find())
        {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group() + "\n" + dither));
        }

        matcher.appendTail(buffer);

        return buffer.toString();
    }

    private static String patchBlissShadowFragment(String source)
    {
        Matcher matcher = BLISS_SHADOW_FRAGDATA.matcher(source);

        if (!matcher.find())
        {
            return source;
        }

        /* Soft BBS casters: Bayer on vertex color.a (form opacity). Disable native texture
         * stochastic so cutout leaves do not fade twice (tex dither + form dither). Keep a
         * hard tex-alpha cutout so leaf holes stay empty. */
        String cutout = "\tif (bbs_is_shadow_form > 0.5 && texture2DLod(tex, texcoord.xy, 0).a < 0.1) discard;\n";
        String dither = cutout + buildShadowDitherBlock("color.a", "\t");
        String patched = insertShadowUniform(source);

        patched = patched.replace(
            "if (Stochastic_Transparent_Shadows)",
            "if (Stochastic_Transparent_Shadows && bbs_is_shadow_form < 0.5)"
        );
        patched = patched.replace(
            "if(Stochastic_Transparent_Shadows)",
            "if(Stochastic_Transparent_Shadows && bbs_is_shadow_form < 0.5)"
        );

        matcher = BLISS_SHADOW_FRAGDATA.matcher(patched);

        if (!matcher.find())
        {
            return source;
        }

        return patched.substring(0, matcher.start()) + dither + matcher.group() + patched.substring(matcher.end());
    }

    public static boolean isShadowCasterSourcePublic(String source)
    {
        return isShadowCasterSource(source);
    }

    private static boolean isShadowCasterSource(String source)
    {
        return source.contains("DoNaturalShadowCalculation")
            || source.contains("float premult = float(mat > 0.98")
            || source.contains("BBS_SHADOW_CASTER_DITHER")
            || isPhotonShadowFragment(source)
            || isBlissShadowFragment(source);
    }

    public static void ensureShadowOpacityVariable()
    {
        if (!shouldApplyPackGlslPatches())
        {
            return;
        }

        ShaderCurves.ShaderVariable variable = ShaderCurves.variableMap.get(ShaderCurves.SHADER_SHADOW_OPACITY);

        if (variable == null)
        {
            variable = new ShaderCurves.ShaderVariable(ShaderCurves.SHADER_SHADOW_OPACITY, "1.0", false);
            ShaderCurves.variableMap.put(ShaderCurves.SHADER_SHADOW_OPACITY, variable);
        }

        syncShadowOpacityDefault(variable);
    }

    public static void syncShadowOpacityDefault()
    {
        ShaderCurves.ShaderVariable variable = ShaderCurves.variableMap.get(ShaderCurves.SHADER_SHADOW_OPACITY);

        if (variable != null)
        {
            syncShadowOpacityDefault(variable);
        }
    }

    private static void syncShadowOpacityDefault(ShaderCurves.ShaderVariable variable)
    {
        float value = 1F;

        if (BBSSettings.shaderShadowOpacity != null)
        {
            value = BBSSettings.shaderShadowOpacity.get();
        }

        variable.defaultValue = Math.max(0F, Math.min(1F, value));
    }
}
