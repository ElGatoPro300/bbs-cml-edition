package mchorse.bbs_mod.cubic.render.vao;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.client.BBSUniform;
import mchorse.bbs_mod.forms.forms.utils.EffectTransform;
import mchorse.bbs_mod.forms.forms.utils.EffectTransformMath;
import mchorse.bbs_mod.forms.forms.utils.GlowSettings;
import mchorse.bbs_mod.forms.renderers.utils.FlatPaintOverlayPass;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.iris.FormColorGradePatch;
import mchorse.bbs_mod.utils.iris.ShaderOpacityPatch;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.client.util.math.MatrixStack;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class ModelVAORenderer
{
    private static final Matrix3f IDENTITY_NORMAL = new Matrix3f();
    private static final Matrix4f IDENTITY_MODEL_VIEW = new Matrix4f();
    private static final Matrix4f SCRATCH_MODEL_VIEW = new Matrix4f();
    private static final Matrix4f SCRATCH_FOG_MAT = new Matrix4f();
    private static final Matrix4f SCRATCH_INV_VIEW = new Matrix4f();
    private static final Matrix4f SCRATCH_COMPOSED = new Matrix4f();

    /* FS-style paint overlay uniform state (rgb + strength). Set by form renderers before a draw and reset after.
     * "base" holds the whole-form paint; "current" is what the uniform uses and can be overridden per model group (bone). */
    private static float baseR;
    private static float baseG;
    private static float baseB;
    private static float baseStrength;

    private static float paintR;
    private static float paintG;
    private static float paintB;
    private static float paintStrength;

    private static float baseGlowR;
    private static float baseGlowG;
    private static float baseGlowB;
    private static float baseGlowStrength;

    private static float glowR;
    private static float glowG;
    private static float glowB;
    private static float glowStrength;
    private static boolean glowPaintOnly;

    private static boolean glowingUniformActive;

    private static boolean textureBlendActive;
    private static float textureBlendFactor;
    private static Link textureBlendTo;

    /* When true, the model is being drawn as a shader-pack paint overlay pass. Groups still sample their
     * real skin texture so transparent UV regions are discarded; only textured pixels receive paint. */
    private static boolean paintPass;
    private static boolean paintOverlayPass;
    private static boolean paintOverlaySynced;
    /* Multiply Iris-lit pixels by FormColorTint inside the color mask (keeps pack lighting/shadows). */
    private static boolean colorTintOverlayPass;
    /* Replace Iris-lit model pixels with FormColorGrade(sceneColor) after composite. */
    private static boolean colorGradeOverlayPass;
    /* Captured-matrix redraw after Iris (or immediate low-opacity bypass) — not the paint-overlay shader branch. */
    private static boolean deferredTranslucentPass;

    /**
     * Fog state at enqueue time for soft / deferred mesh redraws. After Iris composite (and often
     * by vanilla LAST) {@link RenderSystem} fog is collapsed — without this snapshot soft forms
     * either skip fog or wash to FogColor.
     * <p>
     * {@code modelViewInverse} is the inverse of {@link RenderSystem#getModelViewMatrix()} at
     * enqueue. Soft BBS draws bake that same ModelView into the MatrixStack
     * ({@code capturePaintOverlayRootMatrix}); FogMat must strip with this matrix — not
     * {@code Camera.getRotation()} — or cylindrical fog drifts with yaw/pitch when ModelView was
     * identity (stack already camera-relative) or when the quaternion disagrees with the pose matrix.
     */
    public static final class DeferredFogSnapshot
    {
        private final GpuBufferSlice fogBuffer;
        private final Matrix4f modelViewInverse;

        private DeferredFogSnapshot(GpuBufferSlice fogBuffer, Matrix4f modelViewInverse)
        {
            this.fogBuffer = fogBuffer;
            this.modelViewInverse = modelViewInverse;
        }
    }

    private static DeferredFogSnapshot activeDeferredFog;
    private static GpuBufferSlice savedFogBeforePush;

    public static DeferredFogSnapshot captureCurrentFog()
    {
        GpuBufferSlice fogBuffer = RenderSystem.getShaderFog();
        Matrix4f modelViewInverse = new Matrix4f(RenderSystem.getModelViewMatrix());

        /* Identity / near-singular MV → leave identity inverse (stack is already camera-relative). */
        if (Math.abs(modelViewInverse.determinant()) > 1.0E-8F)
        {
            modelViewInverse.invert();
        }
        else
        {
            modelViewInverse.identity();
        }

        return new DeferredFogSnapshot(fogBuffer, modelViewInverse);
    }

    public static void pushDeferredFog(DeferredFogSnapshot snapshot)
    {
        activeDeferredFog = snapshot;
        if (snapshot != null && snapshot.fogBuffer != null)
        {
            savedFogBeforePush = RenderSystem.getShaderFog();
            RenderSystem.setShaderFog(snapshot.fogBuffer);
        }
    }

    public static void popDeferredFog()
    {
        if (savedFogBeforePush != null)
        {
            RenderSystem.setShaderFog(savedFogBeforePush);
            savedFogBeforePush = null;
        }
        activeDeferredFog = null;
    }

    private static final Matrix4f formRootInverse = new Matrix4f();
    private static final Matrix4f paintEffectInverse = new Matrix4f();
    private static final Vector3f paintMaskHalf = new Vector3f(EffectTransformMath.MODEL_MASK_HALF_BASE);
    private static final Matrix4f colorEffectInverse = new Matrix4f();
    private static final Vector3f colorMaskHalf = new Vector3f(EffectTransformMath.MODEL_MASK_HALF_BASE);
    private static final Matrix4f overlayFormRootInverse = new Matrix4f();
    private static float paintMaskShape;
    private static float colorMaskShape;
    private static boolean paintEffectActive;
    private static boolean colorEffectActive;
    private static boolean paintMaskBottomAnchored = true;
    private static boolean colorMaskBottomAnchored = true;
    /* Form-level paint/color masks snapshotted by set*EffectTransform; setGroup* restores these. */
    private static final Matrix4f basePaintEffectInverse = new Matrix4f();
    private static final Vector3f basePaintMaskHalf = new Vector3f(EffectTransformMath.MODEL_MASK_HALF_BASE);
    private static float basePaintMaskShape;
    private static boolean basePaintEffectActive;
    private static boolean basePaintMaskBottomAnchored = true;
    private static final Matrix4f baseColorEffectInverse = new Matrix4f();
    private static final Vector3f baseColorMaskHalf = new Vector3f(EffectTransformMath.MODEL_MASK_HALF_BASE);
    private static float baseColorMaskShape;
    private static boolean baseColorEffectActive;
    private static boolean baseColorMaskBottomAnchored = true;
    private static final Matrix4f glowEffectInverse = new Matrix4f();
    private static final Vector3f glowMaskHalf = new Vector3f(EffectTransformMath.MODEL_MASK_HALF_BASE);
    private static float glowMaskShape;
    private static boolean glowEffectActive;
    private static boolean glowMaskBottomAnchored = true;
    private static final Matrix4f baseGlowEffectInverse = new Matrix4f();
    private static final Vector3f baseGlowMaskHalf = new Vector3f(EffectTransformMath.MODEL_MASK_HALF_BASE);
    private static float baseGlowMaskShape;
    private static boolean baseGlowEffectActive;
    private static boolean baseGlowMaskBottomAnchored = true;
    private static final GradeMaskState gradeBrightnessMask = new GradeMaskState();
    private static final GradeMaskState gradeContrastMask = new GradeMaskState();
    private static final GradeMaskState gradeHueMask = new GradeMaskState();
    private static final GradeMaskState gradeSaturationMask = new GradeMaskState();
    private static float formColorR = 1F;
    private static float formColorG = 1F;
    private static float formColorB = 1F;
    private static float formColorA = 1F;
    private static boolean colorTintMasked;
    private static float baseFormColorR = 1F;
    private static float baseFormColorG = 1F;
    private static float baseFormColorB = 1F;
    private static float baseFormColorA = 1F;
    private static boolean baseColorTintMasked;
    private static float formColorGradeBrightness;
    private static float formColorGradeContrast;
    private static float formColorGradeHue;
    private static float formColorGradeSaturation;
    private static float baseFormColorGradeBrightness;
    private static float baseFormColorGradeContrast;
    private static float baseFormColorGradeHue;
    private static float baseFormColorGradeSaturation;
    private static final EffectTransform baseGradeBrightnessTransform = new EffectTransform();
    private static final EffectTransform baseGradeContrastTransform = new EffectTransform();
    private static final EffectTransform baseGradeHueTransform = new EffectTransform();
    private static final EffectTransform baseGradeSaturationTransform = new EffectTransform();
    private static boolean suppressShapeKeyMainPassGlow;

    /* 1x1 white texture used as the albedo source during the paint overlay pass. */
    private static int whiteTextureId;
    /* Scene color copy for ColorGradeOverlay (Iris-lit pixels → FormColorGrade). */
    private static Texture gradeSceneColor;

    /* Saved GL state for the paint overlay pass (restored in endPaintOverlayPass). */
    private static int savedDepthFunc;
    private static boolean savedDepthMask;
    private static boolean savedPolygonOffsetFill;
    private static boolean savedCullEnabled;

    private static final class GradeMaskState
    {
        private final Matrix4f inverse = new Matrix4f();
        private final Vector3f half = new Vector3f(EffectTransformMath.MODEL_MASK_HALF_BASE, EffectTransformMath.MODEL_MASK_HALF_BASE * EffectTransformMath.MODEL_MASK_Y_BIAS, EffectTransformMath.MODEL_MASK_HALF_BASE);
        private boolean active;
        private boolean bottomAnchored = true;
        private float shape;

        private void set(EffectTransform transform)
        {
            EffectTransformMath.buildInverseMatrix(transform, this.inverse);
            this.active = EffectTransformMath.isTransformActive(transform);
            this.shape = transform == null || transform.shape == null ? 0F : transform.shape.id;
            EffectTransformMath.resolveModelMaskHalfExtents(transform, this.half);
            this.bottomAnchored = true;
        }

        private void clear()
        {
            this.inverse.identity();
            this.active = false;
            this.bottomAnchored = true;
            this.shape = 0F;
            this.half.set(EffectTransformMath.MODEL_MASK_HALF_BASE, EffectTransformMath.MODEL_MASK_HALF_BASE * EffectTransformMath.MODEL_MASK_Y_BIAS, EffectTransformMath.MODEL_MASK_HALF_BASE);
        }

        private void upload(ShaderProgram shader, String prefix)
        {
            BBSUniform.setMatrix4f(shader, prefix + "Inverse", this.inverse);
            BBSUniform.set(shader, prefix + "Active", this.active ? 1F : 0F);
            BBSUniform.set(shader, prefix + "Half", this.half.x, this.half.y, this.half.z);
            BBSUniform.set(shader, prefix + "BottomAnchored", this.bottomAnchored ? 1F : 0F);
            BBSUniform.set(shader, prefix + "Shape", this.shape);
        }
    }

    private static final List<PaintOverlayEntry> paintOverlayQueue = new ArrayList<>();

    private static final class PaintOverlayEntry
    {
        private final GpuBufferSlice projection;
        private final Matrix4f modelView;
        private final boolean synced;
        private final boolean fullModel;
        private final boolean colorTint;
        private final boolean colorGrade;
        private final boolean vanillaComposite;
        private final boolean depthWrite;
        private final boolean depthTest;
        private final DeferredFogSnapshot fog;
        private final Runnable draw;

        private PaintOverlayEntry(GpuBufferSlice projection, Matrix4f modelView, boolean synced, boolean fullModel, boolean colorTint, boolean colorGrade, boolean vanillaComposite, boolean depthWrite, boolean depthTest, DeferredFogSnapshot fog, Runnable draw)
        {
            this.projection = projection;
            this.modelView = modelView;
            this.synced = synced;
            this.fullModel = fullModel;
            this.colorTint = colorTint;
            this.colorGrade = colorGrade;
            this.vanillaComposite = vanillaComposite;
            this.depthWrite = depthWrite;
            this.depthTest = depthTest;
            this.fog = fog;
            this.draw = draw;
        }
    }

    /**
     * Full root matrix for deferred Iris paint overlays (terrain/camera matrix already baked in).
     */
    public static Matrix4f capturePaintOverlayRootMatrix(Matrix4f rootStackMatrix)
    {
        return new Matrix4f(RenderSystem.getModelViewMatrix()).mul(rootStackMatrix);
    }

    public static void clearPaintOverlayQueue()
    {
        paintOverlayQueue.clear();
    }

    public static void enqueuePaintOverlay(GpuBufferSlice projection, Matrix4f modelView, Runnable draw)
    {
        enqueuePaintOverlay(projection, modelView, false, false, false, true, true, draw);
    }

    public static void enqueuePaintOverlay(GpuBufferSlice projection, Matrix4f modelView, boolean synced, Runnable draw)
    {
        enqueuePaintOverlay(projection, modelView, synced, false, false, true, true, draw);
    }

    /**
     * Queues a full translucent mesh redraw for after Iris compositing.
     * {@code depthWrite} true = character meshes (self-occlusion); false = flat panels (keep scene depth / fog).
     */
    public static void submitDeferredTranslucentModel(Runnable draw)
    {
        /* Flat / thin translucent meshes z-fight when depth is rewritten after composite.
         * Character self-occlusion uses the two-arg overload with depthWrite true. */
        submitDeferredTranslucentModel(draw, false, true);
    }

    public static void submitDeferredTranslucentModel(Runnable draw, boolean depthWrite)
    {
        submitDeferredTranslucentModel(draw, depthWrite, true);
    }

    /**
     * @param depthTest false for zero-thickness billboards — post-Iris depth does not match
     *                  captured matrices and LEQUAL produces stippled grass bleed-through.
     */
    public static void submitDeferredTranslucentModel(Runnable draw, boolean depthWrite, boolean depthTest)
    {
        enqueuePaintOverlay(
            RenderSystem.getProjectionMatrixBuffer(),
            new Matrix4f(RenderSystem.getModelViewMatrix()),
            false,
            true,
            false,
            depthWrite,
            depthTest,
            draw
        );
    }

    private static void enqueuePaintOverlay(GpuBufferSlice projection, Matrix4f modelView, boolean synced, boolean fullModel, boolean depthWrite, Runnable draw)
    {
        enqueuePaintOverlay(projection, modelView, synced, fullModel, false, depthWrite, true, draw);
    }

    private static void enqueuePaintOverlay(GpuBufferSlice projection, Matrix4f modelView, boolean synced, boolean fullModel, boolean depthWrite, boolean depthTest, Runnable draw)
    {
        enqueuePaintOverlay(projection, modelView, synced, fullModel, false, depthWrite, depthTest, draw);
    }

    private static void enqueuePaintOverlay(GpuBufferSlice projection, Matrix4f modelView, boolean synced, boolean fullModel, boolean colorTint, boolean depthWrite, boolean depthTest, Runnable draw)
    {
        enqueuePaintOverlay(projection, modelView, synced, fullModel, colorTint, false, depthWrite, depthTest, draw);
    }

    private static void enqueuePaintOverlay(GpuBufferSlice projection, Matrix4f modelView, boolean synced, boolean fullModel, boolean colorTint, boolean colorGrade, boolean depthWrite, boolean depthTest, Runnable draw)
    {
        enqueuePaintOverlay(projection, modelView, synced, fullModel, colorTint, colorGrade, false, depthWrite, depthTest, draw);
    }

    private static void enqueuePaintOverlay(GpuBufferSlice projection, Matrix4f modelView, boolean synced, boolean fullModel, boolean colorTint, boolean colorGrade, boolean vanillaComposite, boolean depthWrite, boolean depthTest, Runnable draw)
    {
        /* Shadow-pass matrices are light-space (Iris and IRLights bake). Flushing them on the
         * color buffer draws tint/paint ghosts at wrong NDC (screen-edge masks when a light
         * touches a colored actor, or tiny blobs at center for Iris shadows). */
        if (BBSRendering.isIrisShadowPass())
        {
            return;
        }

        PaintOverlayEntry entry = new PaintOverlayEntry(
            projection,
            new Matrix4f(modelView),
            synced,
            fullModel,
            colorTint,
            colorGrade,
            vanillaComposite,
            depthWrite,
            depthTest,
            fullModel ? captureCurrentFog() : null,
            draw
        );

        if (BBSRendering.shouldDeferPaintOverlayToFrameEnd())
        {
            paintOverlayQueue.add(entry);
        }
        else
        {
            if (colorGrade && !captureGradeSceneColor())
            {
                return;
            }

            ModelVAORenderer.runPaintOverlayEntry(entry, false);
        }
    }

    /**
     * After Iris composite: run vanilla entity/BE draws with ColorModulator (no BBS paint pass).
     * Used for structure chests/beds where gbuffer ignores setShaderColor and paint overlays break shading.
     */
    public static void submitVanillaPostComposite(Runnable draw)
    {
        enqueuePaintOverlay(
            RenderSystem.getProjectionMatrixBuffer(),
            new Matrix4f(RenderSystem.getModelViewMatrix()),
            false,
            false,
            false,
            false,
            true,
            true,
            true,
            draw
        );
    }

    private static void runPaintOverlayEntry(PaintOverlayEntry entry, boolean restoreFramebuffer)
    {
        if (restoreFramebuffer)
        {
            BBSRendering.ensurePaintOverlayTargetFramebuffer();
        }

        BBSRendering.enableBlend();
        BBSRendering.defaultBlendFunc();
        BBSRendering.setShaderColor(1F, 1F, 1F, 1F);
        BBSRendering.bindProgram(BBSShaders.getModel());

        RenderSystem.backupProjectionMatrix();
        Matrix4f savedModelView = new Matrix4f(RenderSystem.getModelViewMatrix());

        try
        {
            paintOverlaySynced = entry.synced;

            RenderSystem.setProjectionMatrix(entry.projection, ProjectionType.ORTHOGRAPHIC);

            MatrixStackUtils.pushIdentityModelView();

            if (entry.fullModel)
            {
                beginDeferredTranslucentModelPass(entry.depthWrite, entry.depthTest);
            }
            else if (entry.colorGrade)
            {
                beginColorGradeOverlayPass();
            }
            else if (entry.colorTint)
            {
                beginColorTintOverlayPass();
            }
            else if (entry.vanillaComposite)
            {
                beginVanillaPostCompositePass();
            }
            else
            {
                beginPaintOverlayPass(entry.synced);
            }

            try
            {
                if (entry.fog != null)
                {
                    pushDeferredFog(entry.fog);
                }

                entry.draw.run();
            }
            finally
            {
                if (entry.fog != null)
                {
                    popDeferredFog();
                }

                if (entry.fullModel)
                {
                    endDeferredTranslucentModelPass();
                }
                else if (entry.colorGrade)
                {
                    endColorGradeOverlayPass();
                }
                else if (entry.colorTint)
                {
                    endColorTintOverlayPass();
                }
                else if (entry.vanillaComposite)
                {
                    endVanillaPostCompositePass();
                }
                else
                {
                    endPaintOverlayPass();
                }

                MatrixStackUtils.popModelView();
            }
        }
        finally
        {
            RenderSystem.restoreProjectionMatrix();

            Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();

            modelViewStack.pushMatrix();
            modelViewStack.set(savedModelView);
            modelViewStack.popMatrix();

            BBSRendering.setShaderColor(1F, 1F, 1F, 1F);
        }
    }

    /**
     * Queues a paint/glow overlay for {@link #flushPaintOverlayQueue()} at the end of the
     * world frame.
     */
    public static void submitPaintOverlay(boolean synced, Runnable draw)
    {
        ModelVAORenderer.enqueuePaintOverlay(
            RenderSystem.getProjectionMatrixBuffer(),
            new Matrix4f(RenderSystem.getModelViewMatrix()),
            synced,
            draw
        );
    }

    /**
     * Queues a multiply color-mask overlay after Iris composite so FormColorTint keeps pack
     * lighting/shadows instead of redrawing the whole mesh with the unlit BBS path.
     */
    public static void submitColorTintOverlay(Runnable draw)
    {
        ModelVAORenderer.enqueuePaintOverlay(
            RenderSystem.getProjectionMatrixBuffer(),
            new Matrix4f(RenderSystem.getModelViewMatrix()),
            false,
            false,
            true,
            false,
            true,
            true,
            draw
        );
    }

    /**
     * Queues a post-composite regrade of Iris-lit model pixels (scene color → FormColorGrade).
     * Keeps pack lighting/shadows; avoids binding BBS during the gbuffer pass.
     */
    public static void submitColorGradeOverlay(Runnable draw)
    {
        ModelVAORenderer.enqueuePaintOverlay(
            RenderSystem.getProjectionMatrixBuffer(),
            new Matrix4f(RenderSystem.getModelViewMatrix()),
            false,
            false,
            false,
            true,
            false,
            true,
            draw
        );
    }

    /**
     * Queues a paint/glow overlay for {@link #flushPaintOverlayQueue()} at the end of the
     * world frame.
     */
    public static void submitPaintOverlay(GpuBufferSlice projection, Matrix4f modelView, boolean synced, Runnable draw)
    {
        enqueuePaintOverlay(projection, modelView, synced, draw);
    }

    public static void submitPaintOverlay(GpuBufferSlice projection, Matrix4f modelView, Runnable draw)
    {
        enqueuePaintOverlay(projection, modelView, draw);
    }

    public static boolean hasQueuedPaintOverlays()
    {
        return !paintOverlayQueue.isEmpty();
    }

    /**
     * Runs deferred paint overlay draws. Prefer the final framebuffer at world-render end
     * ({@code restoreFramebuffer = true}). When compositing under soft post-deferred forms
     * during Iris {@code beginTranslucents}, pass {@code false} so draws stay on Iris'
     * already-bound translucent target (rebinding Minecraft's main FB loses paint).
     */
    public static void flushPaintOverlayQueue()
    {
        flushPaintOverlayQueue(true);
    }

    public static void flushPaintOverlayQueue(boolean restoreFramebuffer)
    {
        if (paintOverlayQueue.isEmpty())
        {
            return;
        }

        try
        {
            boolean needsSceneCapture = false;

            for (PaintOverlayEntry entry : paintOverlayQueue)
            {
                if (entry.colorGrade)
                {
                    needsSceneCapture = true;

                    break;
                }
            }

            if (restoreFramebuffer)
            {
                ShaderOpacityPatch.syncPaintOverlayDepth();
            }
            else if (needsSceneCapture)
            {
                BBSRendering.ensurePaintOverlayTargetFramebuffer();
            }

            if (needsSceneCapture)
            {
                if (!captureGradeSceneColor())
                {
                    /* Keep Iris-lit mesh; skip broken regrade rather than painting black. */
                    paintOverlayQueue.removeIf(entry -> entry.colorGrade);
                }
            }

            /* Paint/glow overlays first, then full soft-model redraws (Opacity "No shading"
             * path) so translucency composites over painted actors behind the soft form.
             * Ensure color tint runs before paint overlays so paint covers the primary tint. */
            paintOverlayQueue.sort((a, b) ->
            {
                int cmp = Boolean.compare(a.fullModel, b.fullModel);

                if (cmp != 0)
                {
                    return cmp;
                }

                if (a.colorTint != b.colorTint)
                {
                    return a.colorTint ? -1 : 1;
                }

                return 0;
            });

            for (PaintOverlayEntry entry : paintOverlayQueue)
            {
                ModelVAORenderer.runPaintOverlayEntry(entry, restoreFramebuffer);
            }
        }
        finally
        {
            paintOverlayQueue.clear();
        }
    }

    public static void beginPaintPass()
    {
        paintPass = true;
    }

    public static void endPaintPass()
    {
        paintPass = false;
    }

    /**
     * Second-pass paint overlay for external shader packs. Re-draws the same geometry with the BBS
     * model shader so paint can be alpha-blended over the shader-pack first pass using the same
     * mix semantics as the no-shader path: mix(litTextureRgb, paintRgb, paintStrength).
     */
    public static void beginPaintOverlayPass(boolean synced)
    {
        beginPaintPass();
        paintOverlayPass = true;
        paintOverlaySynced = synced;

        savedDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        savedDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        savedPolygonOffsetFill = GL11.glGetBoolean(GL11.GL_POLYGON_OFFSET_FILL);
        savedCullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);

        BBSRendering.enableBlend();
        BBSRendering.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        BBSRendering.enableDepthTest();
        BBSRendering.depthFunc(GL11.GL_LEQUAL);
        BBSRendering.depthMask(false);

        /* Match the no-shader model path: paint both front and back faces (eye sockets,
         * hollow heads, etc.). Iris often leaves cull enabled; keeping it would skip interiors. */
        BBSRendering.disableCull();

        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        /* Units-only bias: a negative factor punches edge-on paint through terrain under Iris
         * (slope-scaled offset). Facing quads need a large units value at distance. */
        GL11.glPolygonOffset(FlatPaintOverlayPass.POLYGON_OFFSET_FACTOR, FlatPaintOverlayPass.POLYGON_OFFSET_UNITS);
    }

    /**
     * Post-Iris composite path for vanilla block-entity redraws. Pulls slightly toward the
     * camera without rewriting depth so the tinted pass does not z-fight the Iris-lit BE.
     */
    public static void beginVanillaPostCompositePass()
    {
        paintOverlayPass = false;
        paintOverlaySynced = false;
        colorTintOverlayPass = false;
        colorGradeOverlayPass = false;

        savedDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        savedDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        savedPolygonOffsetFill = GL11.glGetBoolean(GL11.GL_POLYGON_OFFSET_FILL);
        savedCullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);

        BBSRendering.enableBlend();
        BBSRendering.defaultBlendFunc();
        BBSRendering.enableDepthTest();
        BBSRendering.depthFunc(GL11.GL_LEQUAL);
        BBSRendering.depthMask(false);
        BBSRendering.setShaderColor(1F, 1F, 1F, 1F);
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(-1F, -2F);
    }

    public static void endVanillaPostCompositePass()
    {
        BBSRendering.depthFunc(savedDepthFunc);
        BBSRendering.depthMask(savedDepthMask);

        if (savedPolygonOffsetFill)
        {
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        }
        else
        {
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        }

        GL11.glPolygonOffset(0F, 0F);

        if (savedCullEnabled)
        {
            BBSRendering.enableCull();
        }
        else
        {
            BBSRendering.disableCull();
        }

        BBSRendering.setShaderColor(1F, 1F, 1F, 1F);
        BBSRendering.defaultBlendFunc();
    }

    /**
     * Multiply the Iris-lit framebuffer by FormColorTint inside the color mask. Keeps pack
     * lighting/shadows while applying the spatial Color transform.
     */
    public static void beginColorTintOverlayPass()
    {
        colorTintOverlayPass = true;
        paintOverlayPass = false;
        paintOverlaySynced = false;

        savedDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        savedDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        savedPolygonOffsetFill = GL11.glGetBoolean(GL11.GL_POLYGON_OFFSET_FILL);
        savedCullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);

        BBSRendering.enableBlend();
        BBSRendering.blendFuncSeparate(
            GL11.GL_DST_COLOR,
            GL11.GL_ZERO,
            GL11.GL_DST_ALPHA,
            GL11.GL_ZERO
        );

        BBSRendering.enableDepthTest();
        BBSRendering.depthFunc(GL11.GL_LEQUAL);
        BBSRendering.depthMask(false);

        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(FlatPaintOverlayPass.POLYGON_OFFSET_FACTOR, FlatPaintOverlayPass.POLYGON_OFFSET_UNITS);
    }

    /**
     * Replace Iris-lit model pixels with FormColorGrade(sceneColor). Sampler3 holds the
     * pre-overlay scene copy from {@link #captureGradeSceneColor()}.
     */
    public static void beginColorGradeOverlayPass()
    {
        colorGradeOverlayPass = true;
        colorTintOverlayPass = false;
        paintOverlayPass = false;
        paintOverlaySynced = false;

        savedDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        savedDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        savedPolygonOffsetFill = GL11.glGetBoolean(GL11.GL_POLYGON_OFFSET_FILL);
        savedCullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);

        if (gradeSceneColor != null && gradeSceneColor.isValid())
        {
            GlStateManager._activeTexture(GL30.GL_TEXTURE3);
            GlStateManager._bindTexture(gradeSceneColor.id);
            GlStateManager._activeTexture(GL30.GL_TEXTURE0);
        }

        BBSRendering.enableBlend();
        BBSRendering.defaultBlendFunc();

        BBSRendering.enableDepthTest();
        BBSRendering.depthFunc(GL11.GL_LEQUAL);
        BBSRendering.depthMask(false);

        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(FlatPaintOverlayPass.POLYGON_OFFSET_FACTOR, FlatPaintOverlayPass.POLYGON_OFFSET_UNITS);
    }

    public static void endColorGradeOverlayPass()
    {
        colorGradeOverlayPass = false;

        GL11.glPolygonOffset(0F, 0F);

        if (savedPolygonOffsetFill)
        {
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        }
        else
        {
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        }

        BBSRendering.depthMask(savedDepthMask);
        BBSRendering.depthFunc(savedDepthFunc);
        BBSRendering.enableDepthTest();
        BBSRendering.defaultBlendFunc();

        if (savedCullEnabled)
        {
            BBSRendering.enableCull();
        }
        else
        {
            BBSRendering.disableCull();
        }
    }

    /**
     * Copy the current paint-overlay target color into {@link #gradeSceneColor} so
     * ColorGradeOverlay can sample Iris-lit pixels without feedback loops.
     *
     * @return true when Sampler3 has a valid scene copy for this frame
     */
    public static boolean captureGradeSceneColor()
    {
        net.minecraft.client.gl.Framebuffer source = BBSRendering.getPaintOverlaySourceFramebuffer();

        if (source == null)
        {
            return false;
        }

        int width = source.textureWidth;
        int height = source.textureHeight;

        if (width <= 0 || height <= 0)
        {
            return false;
        }

        if (gradeSceneColor == null)
        {
            gradeSceneColor = new Texture();
            gradeSceneColor.setFilter(GL11.GL_NEAREST);
        }

        int prevRead = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevDraw = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int prevTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

        try
        {
            gradeSceneColor.bind();

            if (gradeSceneColor.width != width || gradeSceneColor.height != height)
            {
                gradeSceneColor.setSize(width, height);
            }

            if (source.getColorAttachment() instanceof GlTexture glTexture)
            {
                GL43.glCopyImageSubData(
                    glTexture.getGlId(), GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
                    gradeSceneColor.id, GL11.GL_TEXTURE_2D, 0, 0, 0, 0,
                    width, height, 1
                );
            }
        }
        finally
        {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDraw);
            BBSRendering.ensurePaintOverlayTargetFramebuffer();
        }

        return gradeSceneColor.isValid() && gradeSceneColor.width == width && gradeSceneColor.height == height;
    }

    /**
     * Bind the scene copy from {@link #captureGradeSceneColor()} to texture unit 3.
     */
    public static void bindGradeSceneColorTexture()
    {
        if (gradeSceneColor != null && gradeSceneColor.isValid())
        {
            GlStateManager._activeTexture(GL30.GL_TEXTURE3);
            GlStateManager._bindTexture(gradeSceneColor.id);
            GlStateManager._activeTexture(GL30.GL_TEXTURE0);
        }
    }

    public static void endColorTintOverlayPass()
    {
        colorTintOverlayPass = false;

        GL11.glPolygonOffset(0F, 0F);

        if (savedPolygonOffsetFill)
        {
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        }
        else
        {
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        }

        BBSRendering.depthMask(savedDepthMask);
        BBSRendering.depthFunc(savedDepthFunc);
        BBSRendering.defaultBlendFunc();

        if (savedCullEnabled)
        {
            BBSRendering.enableCull();
        }
        else
        {
            BBSRendering.disableCull();
        }
    }

    /**
     * Full translucent redraw after Iris composite — BBS model shader keeps low form alpha.
     * {@code depthWrite} true matches the no-shader path so render-depth panels can occlude
     * forms behind them. {@code depthTest} false for zero-thickness billboards whose captured
     * depth does not match the post-Iris depth buffer (stippled bleed-through).
     */
    public static void beginDeferredTranslucentModelPass(boolean depthWrite)
    {
        beginDeferredTranslucentModelPass(depthWrite, true);
    }

    public static void beginDeferredTranslucentModelPass(boolean depthWrite, boolean depthTest)
    {
        savedDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        savedDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        savedCullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        /* Captured full camera+entity matrix while RenderSystem model-view is identity.
         * Do not set paintOverlayPass — that enables the paint-only shader branch and would
         * discard textured geometry when PaintColor.a is 0. */
        deferredTranslucentPass = true;

        BBSRendering.enableBlend();
        BBSRendering.blendFuncSeparate(
            GL11.GL_SRC_ALPHA,
            GL11.GL_ONE_MINUS_SRC_ALPHA,
            GL11.GL_ONE,
            GL11.GL_ONE_MINUS_SRC_ALPHA
        );

        if (depthTest)
        {
            BBSRendering.enableDepthTest();
            BBSRendering.depthFunc(GL11.GL_LEQUAL);
        }
        else
        {
            BBSRendering.disableDepthTest();
        }

        BBSRendering.depthMask(depthWrite);
        BBSRendering.enableCull();
    }

    public static void beginDeferredTranslucentModelPass()
    {
        beginDeferredTranslucentModelPass(true, true);
    }

    public static void endDeferredTranslucentModelPass()
    {
        deferredTranslucentPass = false;
        BBSRendering.depthMask(savedDepthMask);
        BBSRendering.depthFunc(savedDepthFunc);
        BBSRendering.enableDepthTest();
        BBSRendering.defaultBlendFunc();

        if (savedCullEnabled)
        {
            BBSRendering.enableCull();
        }
        else
        {
            BBSRendering.disableCull();
        }
    }

    public static void endPaintOverlayPass()
    {
        endPaintPass();
        paintOverlayPass = false;
        paintOverlaySynced = false;

        GL11.glPolygonOffset(0F, 0F);

        if (savedPolygonOffsetFill)
        {
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        }
        else
        {
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        }

        BBSRendering.depthMask(savedDepthMask);
        BBSRendering.depthFunc(savedDepthFunc);
        BBSRendering.defaultBlendFunc();

        if (savedCullEnabled)
        {
            BBSRendering.enableCull();
        }
        else
        {
            BBSRendering.disableCull();
        }
    }

    public static boolean isPaintOverlayPass()
    {
        return paintOverlayPass;
    }

    public static boolean isColorTintOverlayPass()
    {
        return colorTintOverlayPass;
    }

    public static boolean isColorGradeOverlayPass()
    {
        return colorGradeOverlayPass;
    }

    public static boolean isDeferredTranslucentPass()
    {
        return deferredTranslucentPass;
    }

    private static boolean usesCapturedModelView()
    {
        return paintOverlayPass || deferredTranslucentPass || colorTintOverlayPass || colorGradeOverlayPass;
    }

    /**
     * Temporarily toggles the paint-overlay shader branch while a deferred Iris overlay draw is running.
     */
    public static void runWithPaintOverlayPass(boolean paintOverlay, Runnable draw)
    {
        boolean previous = paintOverlayPass;

        paintOverlayPass = paintOverlay;

        try
        {
            draw.run();
        }
        finally
        {
            paintOverlayPass = previous;
        }
    }

    public static boolean isPaintOverlaySynced()
    {
        return paintOverlaySynced;
    }

    public static boolean isPaintPass()
    {
        return paintPass;
    }

    public static boolean isGlowEffectActive()
    {
        return glowEffectActive;
    }

    public static boolean isPaintEffectActive()
    {
        return paintEffectActive;
    }

    public static boolean isColorEffectActive()
    {
        return colorEffectActive;
    }

    public static float getBasePaintR()
    {
        return baseR;
    }

    public static float getBasePaintG()
    {
        return baseG;
    }

    public static float getBasePaintB()
    {
        return baseB;
    }

    public static float getBasePaintStrength()
    {
        return baseStrength;
    }

    /**
     * Lazily builds (on the render thread) a 1x1 fully-white texture and returns its GL id. Sampling this
     * texture yields white, so a shader's texel * vertexColour becomes the vertex colour verbatim.
     */
    public static int getWhiteTextureId()
    {
        if (whiteTextureId == 0)
        {
            whiteTextureId = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, whiteTextureId);
            ByteBuffer pixel = BufferUtils.createByteBuffer(4);
            pixel.put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF);
            pixel.flip();
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, 1, 1, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        }

        return whiteTextureId;
    }

    public static void setPaint(float r, float g, float b, float strength)
    {
        baseR = r;
        baseG = g;
        baseB = b;
        baseStrength = strength;

        paintR = r;
        paintG = g;
        paintB = b;
        paintStrength = strength;
    }

    public static void setGroupPaint(float r, float g, float b, float strength)
    {
        if (strength != 0F)
        {
            paintR = r;
            paintG = g;
            paintB = b;
            paintStrength = strength;
        }
        else
        {
            paintR = baseR;
            paintG = baseG;
            paintB = baseB;
            paintStrength = baseStrength;
        }
    }

    public static void setGlow(GlowSettings settings, float colorR, float colorG, float colorB)
    {
        setGlow(settings, colorR, colorG, colorB, null);
    }

    public static void setGlow(GlowSettings settings, float colorR, float colorG, float colorB, Color legacyColor)
    {
        float strength = settings.resolveIntensity(legacyColor);

        baseGlowR = colorR;
        baseGlowG = colorG;
        baseGlowB = colorB;
        baseGlowStrength = strength;

        glowR = colorR;
        glowG = colorG;
        glowB = colorB;
        glowStrength = strength;
        glowPaintOnly = settings != null && settings.resolvePaintOnly();
    }

    public static void setGlowing(float r, float g, float b, float strength, float radius)
    {
        GlowSettings settings = new GlowSettings(strength, radius);

        setGlow(settings, r, g, b);
    }

    public static void setGroupGlowing(float r, float g, float b, float strength)
    {
        glowR = r;
        glowG = g;
        glowB = b;
        glowStrength = strength;
    }

    public static void clearGlowing()
    {
        baseGlowR = 0F;
        baseGlowG = 0F;
        baseGlowB = 0F;
        baseGlowStrength = 0F;

        glowR = 0F;
        glowG = 0F;
        glowB = 0F;
        glowStrength = 0F;
        glowPaintOnly = false;
    }

    public static boolean isGlowPaintOnly()
    {
        return glowPaintOnly;
    }

    public static boolean isGlowingUniformActive()
    {
        return glowingUniformActive;
    }

    /**
     * CPU mesh builders emit vertices before draw-time uniform upload. Probe the active shader early so
     * shape-key geometry can skip vanilla brighten/light boosts when the BBS GlowingColor uniform applies.
     */
    public static boolean isSuppressShapeKeyMainPassGlow()
    {
        return suppressShapeKeyMainPassGlow;
    }

    public static void setSuppressShapeKeyMainPassGlow(boolean suppress)
    {
        suppressShapeKeyMainPassGlow = suppress;
    }

    public static void beginCpuGeometry(ShaderProgram shader)
    {
        GlUniform glowingUniform = shader.getUniform("GlowingColor");

        glowingUniformActive = glowingUniform != null;
    }

    public static float getBaseGlowingStrength()
    {
        return baseGlowStrength;
    }

    public static float getBaseGlowingR()
    {
        return baseGlowR;
    }

    public static float getBaseGlowingG()
    {
        return baseGlowG;
    }

    public static float getBaseGlowingB()
    {
        return baseGlowB;
    }

    public static void clearPaint()
    {
        baseR = 0F;
        baseG = 0F;
        baseB = 0F;
        baseStrength = 0F;

        paintR = 0F;
        paintG = 0F;
        paintB = 0F;
        paintStrength = 0F;
    }

    public static void setTextureBlend(Link toTexture, float blend)
    {
        ModelVAORenderer.textureBlendActive = toTexture != null && blend > 0F && blend < 1F;
        ModelVAORenderer.textureBlendFactor = blend;
        ModelVAORenderer.textureBlendTo = toTexture;
    }

    public static void clearTextureBlend()
    {
        ModelVAORenderer.textureBlendActive = false;
        ModelVAORenderer.textureBlendFactor = 0F;
        ModelVAORenderer.textureBlendTo = null;
    }

    public static void setPaintEffectTransform(Matrix4f formRootInverseMatrix, EffectTransform transform, Vector3f maskHalf)
    {
        setPaintEffectTransform(formRootInverseMatrix, transform, maskHalf, true);
    }

    public static void setPaintEffectTransform(Matrix4f formRootInverseMatrix, EffectTransform transform, Vector3f maskHalf, boolean bottomAnchoredY)
    {
        if (formRootInverseMatrix != null)
        {
            formRootInverse.set(formRootInverseMatrix);
        }
        else
        {
            formRootInverse.identity();
        }

        EffectTransformMath.buildInverseMatrix(transform, paintEffectInverse);
        paintEffectActive = EffectTransformMath.isTransformActive(transform);
        paintMaskShape = transform == null || transform.shape == null ? 0F : transform.shape.id;

        if (maskHalf != null)
        {
            paintMaskHalf.set(maskHalf);
        }
        else
        {
            EffectTransformMath.resolveModelMaskHalfExtents(transform, paintMaskHalf);
        }

        paintMaskBottomAnchored = bottomAnchoredY;
        snapshotPaintEffectBase();
    }

    /**
     * Per-bone paint mask override (same idea as {@link #setGroupPaint}). When the group has an
     * active transform/shape, it replaces the form paint mask for this draw; otherwise restore base.
     */
    public static void setGroupPaintEffectTransform(EffectTransform transform)
    {
        if (EffectTransformMath.isTransformActive(transform))
        {
            EffectTransformMath.buildInverseMatrix(transform, paintEffectInverse);
            paintEffectActive = true;
            paintMaskShape = transform.shape == null ? 0F : transform.shape.id;
            EffectTransformMath.resolveModelMaskHalfExtents(transform, paintMaskHalf);
            paintMaskBottomAnchored = basePaintMaskBottomAnchored;
        }
        else
        {
            paintEffectInverse.set(basePaintEffectInverse);
            paintMaskHalf.set(basePaintMaskHalf);
            paintMaskShape = basePaintMaskShape;
            paintEffectActive = basePaintEffectActive;
            paintMaskBottomAnchored = basePaintMaskBottomAnchored;
        }
    }

    public static void clearPaintEffectTransform()
    {
        if (!colorEffectActive && !glowEffectActive)
        {
            formRootInverse.identity();
        }

        paintEffectInverse.identity();
        paintEffectActive = false;
        paintMaskBottomAnchored = true;
        paintMaskShape = 0F;
        paintMaskHalf.set(EffectTransformMath.MODEL_MASK_HALF_BASE, EffectTransformMath.MODEL_MASK_HALF_BASE * EffectTransformMath.MODEL_MASK_Y_BIAS, EffectTransformMath.MODEL_MASK_HALF_BASE);
        basePaintEffectInverse.identity();
        basePaintEffectActive = false;
        basePaintMaskBottomAnchored = true;
        basePaintMaskShape = 0F;
        basePaintMaskHalf.set(EffectTransformMath.MODEL_MASK_HALF_BASE, EffectTransformMath.MODEL_MASK_HALF_BASE * EffectTransformMath.MODEL_MASK_Y_BIAS, EffectTransformMath.MODEL_MASK_HALF_BASE);
    }

    public static void setColorEffectTransform(Matrix4f formRootInverseMatrix, EffectTransform transform, Vector3f maskHalf)
    {
        if (formRootInverseMatrix != null)
        {
            formRootInverse.set(formRootInverseMatrix);
        }
        else
        {
            formRootInverse.identity();
        }

        EffectTransformMath.buildInverseMatrix(transform, colorEffectInverse);
        colorEffectActive = EffectTransformMath.isTransformActive(transform);
        colorMaskShape = transform == null || transform.shape == null ? 0F : transform.shape.id;

        if (maskHalf != null)
        {
            colorMaskHalf.set(maskHalf);
        }
        else
        {
            EffectTransformMath.resolveModelMaskHalfExtents(transform, colorMaskHalf);
        }

        colorMaskBottomAnchored = true;
        snapshotColorEffectBase();
    }

    /**
     * Per-bone color (tint) mask override. Active bone transform replaces the form color mask
     * for this draw; otherwise restore the form/base mask.
     */
    public static void setGroupColorEffectTransform(EffectTransform transform)
    {
        if (EffectTransformMath.isTransformActive(transform))
        {
            EffectTransformMath.buildInverseMatrix(transform, colorEffectInverse);
            colorEffectActive = true;
            colorMaskShape = transform.shape == null ? 0F : transform.shape.id;
            EffectTransformMath.resolveModelMaskHalfExtents(transform, colorMaskHalf);
            colorMaskBottomAnchored = baseColorMaskBottomAnchored;
        }
        else
        {
            colorEffectInverse.set(baseColorEffectInverse);
            colorMaskHalf.set(baseColorMaskHalf);
            colorMaskShape = baseColorMaskShape;
            colorEffectActive = baseColorEffectActive;
            colorMaskBottomAnchored = baseColorMaskBottomAnchored;
        }
    }

    public static void setGlowEffectTransform(Matrix4f formRootInverseMatrix, EffectTransform transform, Vector3f maskHalf)
    {
        setGlowEffectTransform(formRootInverseMatrix, transform, maskHalf, true);
    }

    public static void setGlowEffectTransform(Matrix4f formRootInverseMatrix, EffectTransform transform, Vector3f maskHalf, boolean bottomAnchoredY)
    {
        if (formRootInverseMatrix != null)
        {
            formRootInverse.set(formRootInverseMatrix);
        }
        else
        {
            formRootInverse.identity();
        }

        EffectTransformMath.buildInverseMatrix(transform, glowEffectInverse);
        glowEffectActive = EffectTransformMath.isTransformActive(transform);
        glowMaskShape = transform == null || transform.shape == null ? 0F : transform.shape.id;

        if (maskHalf != null)
        {
            glowMaskHalf.set(maskHalf);
        }
        else
        {
            EffectTransformMath.resolveModelMaskHalfExtents(transform, glowMaskHalf);
        }

        glowMaskBottomAnchored = bottomAnchoredY;
        snapshotGlowEffectBase();
    }

    /**
     * Per-bone glow mask override. Active bone transform replaces the form glow mask for this
     * draw; otherwise restore the form/base mask.
     */
    public static void setGroupGlowEffectTransform(EffectTransform transform)
    {
        if (EffectTransformMath.isTransformActive(transform))
        {
            EffectTransformMath.buildInverseMatrix(transform, glowEffectInverse);
            glowEffectActive = true;
            glowMaskShape = transform.shape == null ? 0F : transform.shape.id;
            EffectTransformMath.resolveModelMaskHalfExtents(transform, glowMaskHalf);
            glowMaskBottomAnchored = baseGlowMaskBottomAnchored;
        }
        else
        {
            glowEffectInverse.set(baseGlowEffectInverse);
            glowMaskHalf.set(baseGlowMaskHalf);
            glowMaskShape = baseGlowMaskShape;
            glowEffectActive = baseGlowEffectActive;
            glowMaskBottomAnchored = baseGlowMaskBottomAnchored;
        }
    }

    public static void clearGlowEffectTransform()
    {
        if (!paintEffectActive && !colorEffectActive)
        {
            formRootInverse.identity();
        }

        glowEffectInverse.identity();
        glowEffectActive = false;
        glowMaskBottomAnchored = true;
        glowMaskShape = 0F;
        glowMaskHalf.set(EffectTransformMath.MODEL_MASK_HALF_BASE, EffectTransformMath.MODEL_MASK_HALF_BASE * EffectTransformMath.MODEL_MASK_Y_BIAS, EffectTransformMath.MODEL_MASK_HALF_BASE);
        baseGlowEffectInverse.identity();
        baseGlowEffectActive = false;
        baseGlowMaskBottomAnchored = true;
        baseGlowMaskShape = 0F;
        baseGlowMaskHalf.set(EffectTransformMath.MODEL_MASK_HALF_BASE, EffectTransformMath.MODEL_MASK_HALF_BASE * EffectTransformMath.MODEL_MASK_Y_BIAS, EffectTransformMath.MODEL_MASK_HALF_BASE);
    }

    public static void setFormColorTint(float r, float g, float b, float a)
    {
        formColorR = r;
        formColorG = g;
        formColorB = b;
        formColorA = a;
        colorTintMasked = true;
        baseFormColorR = r;
        baseFormColorG = g;
        baseFormColorB = b;
        baseFormColorA = a;
        baseColorTintMasked = true;
    }

    public static void clearFormColorTint()
    {
        formColorR = 1F;
        formColorG = 1F;
        formColorB = 1F;
        formColorA = 1F;
        colorTintMasked = false;
        baseFormColorR = 1F;
        baseFormColorG = 1F;
        baseFormColorB = 1F;
        baseFormColorA = 1F;
        baseColorTintMasked = false;
    }

    /**
     * Per-bone FormColorTint override when the bone owns a spatial color mask. Otherwise restore
     * the form/base tint so vertex-multiplied bone colors keep working without a transform.
     */
    public static void setGroupFormColorTint(Color color)
    {
        if (color != null && color.hasActiveTransform())
        {
            formColorR = color.r;
            formColorG = color.g;
            formColorB = color.b;
            formColorA = color.a;
            colorTintMasked = true;
        }
        else
        {
            formColorR = baseFormColorR;
            formColorG = baseFormColorG;
            formColorB = baseFormColorB;
            formColorA = baseFormColorA;
            colorTintMasked = baseColorTintMasked;
        }
    }

    public static void setFormColorGrade(float brightness, float contrast, float hue, float saturation)
    {
        baseFormColorGradeBrightness = brightness;
        baseFormColorGradeContrast = contrast;
        baseFormColorGradeHue = hue;
        baseFormColorGradeSaturation = saturation;
        applyFormColorGrade(brightness, contrast, hue, saturation);
    }

    public static void setGradeEffectTransforms(Color color)
    {
        if (color == null)
        {
            clearGradeEffectTransforms();
            clearBaseGradeEffectTransforms();

            return;
        }

        copyEffectTransform(baseGradeBrightnessTransform, color.brightnessTransform);
        copyEffectTransform(baseGradeContrastTransform, color.contrastTransform);
        copyEffectTransform(baseGradeHueTransform, color.hueTransform);
        copyEffectTransform(baseGradeSaturationTransform, color.saturationTransform);
        applyGradeEffectTransforms(color.brightnessTransform, color.contrastTransform, color.hueTransform, color.saturationTransform);
    }

    public static void setGradeEffectTransforms(EffectTransform brightness, EffectTransform contrast, EffectTransform hue, EffectTransform saturation)
    {
        copyEffectTransform(baseGradeBrightnessTransform, brightness);
        copyEffectTransform(baseGradeContrastTransform, contrast);
        copyEffectTransform(baseGradeHueTransform, hue);
        copyEffectTransform(baseGradeSaturationTransform, saturation);
        applyGradeEffectTransforms(brightness, contrast, hue, saturation);
    }

    /**
     * Per-bone Color Grade override (same idea as {@link #setGroupPaint}). When the group
     * has adjustments, they replace the form/base grade for this draw; otherwise restore base.
     */
    public static void setGroupFormColorGrade(Color color)
    {
        if (color != null && (color.hasColorAdjustments() || color.hasActiveGradeTransform()))
        {
            applyFormColorGrade(color.brightness, color.contrast, color.hue, color.saturation);
            applyGradeEffectTransforms(color.brightnessTransform, color.contrastTransform, color.hueTransform, color.saturationTransform);
        }
        else
        {
            applyFormColorGrade(baseFormColorGradeBrightness, baseFormColorGradeContrast, baseFormColorGradeHue, baseFormColorGradeSaturation);
            applyGradeEffectTransforms(baseGradeBrightnessTransform, baseGradeContrastTransform, baseGradeHueTransform, baseGradeSaturationTransform);
        }
    }

    private static void applyFormColorGrade(float brightness, float contrast, float hue, float saturation)
    {
        formColorGradeBrightness = brightness;
        formColorGradeContrast = contrast;
        formColorGradeHue = hue;
        formColorGradeSaturation = saturation;
        FormColorGradePatch.set(brightness, contrast, hue, saturation);
    }

    private static void applyGradeEffectTransforms(EffectTransform brightness, EffectTransform contrast, EffectTransform hue, EffectTransform saturation)
    {
        gradeBrightnessMask.set(brightness);
        gradeContrastMask.set(contrast);
        gradeHueMask.set(hue);
        gradeSaturationMask.set(saturation);
    }

    private static void copyEffectTransform(EffectTransform target, EffectTransform source)
    {
        EffectTransform value = source == null ? new EffectTransform() : source;

        target.offsetX = value.offsetX;
        target.offsetY = value.offsetY;
        target.offsetZ = value.offsetZ;
        target.scaleX = value.scaleX;
        target.scaleY = value.scaleY;
        target.scaleZ = value.scaleZ;
        target.rotateX = value.rotateX;
        target.rotateY = value.rotateY;
        target.rotateZ = value.rotateZ;
        target.shape = value.shape;
    }

    private static void clearBaseGradeEffectTransforms()
    {
        copyEffectTransform(baseGradeBrightnessTransform, null);
        copyEffectTransform(baseGradeContrastTransform, null);
        copyEffectTransform(baseGradeHueTransform, null);
        copyEffectTransform(baseGradeSaturationTransform, null);
    }

    public static void clearGradeEffectTransforms()
    {
        gradeBrightnessMask.clear();
        gradeContrastMask.clear();
        gradeHueMask.clear();
        gradeSaturationMask.clear();
    }

    public static void clearFormColorGrade()
    {
        baseFormColorGradeBrightness = 0F;
        baseFormColorGradeContrast = 0F;
        baseFormColorGradeHue = 0F;
        baseFormColorGradeSaturation = 0F;
        formColorGradeBrightness = 0F;
        formColorGradeContrast = 0F;
        formColorGradeHue = 0F;
        formColorGradeSaturation = 0F;
        clearBaseGradeEffectTransforms();
        clearGradeEffectTransforms();
        FormColorGradePatch.clear();
    }

    public static void clearColorEffectTransform()
    {
        if (!paintEffectActive && !glowEffectActive)
        {
            formRootInverse.identity();
        }

        colorEffectInverse.identity();
        colorEffectActive = false;
        colorMaskBottomAnchored = true;
        colorMaskShape = 0F;
        colorMaskHalf.set(EffectTransformMath.MODEL_MASK_HALF_BASE, EffectTransformMath.MODEL_MASK_HALF_BASE * EffectTransformMath.MODEL_MASK_Y_BIAS, EffectTransformMath.MODEL_MASK_HALF_BASE);
        baseColorEffectInverse.identity();
        baseColorEffectActive = false;
        baseColorMaskBottomAnchored = true;
        baseColorMaskShape = 0F;
        baseColorMaskHalf.set(EffectTransformMath.MODEL_MASK_HALF_BASE, EffectTransformMath.MODEL_MASK_HALF_BASE * EffectTransformMath.MODEL_MASK_Y_BIAS, EffectTransformMath.MODEL_MASK_HALF_BASE);
    }

    private static void snapshotPaintEffectBase()
    {
        basePaintEffectInverse.set(paintEffectInverse);
        basePaintMaskHalf.set(paintMaskHalf);
        basePaintMaskShape = paintMaskShape;
        basePaintEffectActive = paintEffectActive;
        basePaintMaskBottomAnchored = paintMaskBottomAnchored;
    }

    private static void snapshotColorEffectBase()
    {
        baseColorEffectInverse.set(colorEffectInverse);
        baseColorMaskHalf.set(colorMaskHalf);
        baseColorMaskShape = colorMaskShape;
        baseColorEffectActive = colorEffectActive;
        baseColorMaskBottomAnchored = colorMaskBottomAnchored;
    }

    private static void snapshotGlowEffectBase()
    {
        baseGlowEffectInverse.set(glowEffectInverse);
        baseGlowMaskHalf.set(glowMaskHalf);
        baseGlowMaskShape = glowMaskShape;
        baseGlowEffectActive = glowEffectActive;
        baseGlowMaskBottomAnchored = glowMaskBottomAnchored;
    }

    private static Matrix4f overlayFormRootInverse()
    {
        if (usesCapturedModelView())
        {
            return overlayFormRootInverse.identity();
        }

        return formRootInverse;
    }

    public static void render(ShaderProgram shader, IModelVAO modelVAO, MatrixStack stack, float r, float g, float b, float a, int light, int overlay)
    {
        /* Iris / resource-reload races can leave BBSShaders.getModel() null while
         * form-list UI cards still try to draw Extruded/Structure VAOs. */
        if (shader == null || shader == ShaderProgram.INVALID || modelVAO == null)
        {
            return;
        }

        int currentVAO = GL30.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int currentElementArrayBuffer = GL30.glGetInteger(GL30.GL_ELEMENT_ARRAY_BUFFER_BINDING);

        if (ModelVAORenderer.textureBlendActive && ModelVAORenderer.textureBlendTo != null)
        {
            BBSModClient.getTextures().bindTexture(ModelVAORenderer.textureBlendTo, 3);
        }

        BBSRendering.bindProgram(shader);
        setupUniforms(stack, shader);

        ShaderOpacityPatch.reassertPostDeferredDepthState();
        ShaderOpacityPatch.uploadShadowFormUniform();
        FormColorGradePatch.uploadToCurrentProgram();
        modelVAO.render(VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, r, g, b, a, light, overlay);

        GlStateManager._activeTexture(GL30.GL_TEXTURE0);

        BBSRendering.unbindProgram();

        GL30.glBindVertexArray(currentVAO);

        if (currentVAO != 0)
        {
            GL30.glBindBuffer(GL30.GL_ELEMENT_ARRAY_BUFFER, currentElementArrayBuffer);
        }
    }

    public static void setupUniforms(MatrixStack stack, ShaderProgram shader)
    {
        if (shader == null)
        {
            return;
        }

        BBSRendering.bindProgram(shader);

        if (colorGradeOverlayPass && gradeSceneColor != null && gradeSceneColor.isValid())
        {
            GlStateManager._activeTexture(GL30.GL_TEXTURE3);
            GlStateManager._bindTexture(gradeSceneColor.id);
            GlStateManager._activeTexture(GL30.GL_TEXTURE0);
        }

        setupUniforms(stack, shader, false, null);
    }

    /**
     * CPU shape-key path writes positions/normals already transformed by the render stack.
     * ModelViewMat must not multiply that stack again (or meshes vanish at the origin when
     * {@code drawWithGlobalProgram} keeps only the camera matrix), and NormalMat must stay
     * identity or diffuse lighting is applied twice.
     */
    public static void setupUniformsCpuPretransformed(ShaderProgram shader)
    {
        setupUniformsCpuPretransformed(shader, null);
    }

    public static void setupUniformsCpuPretransformed(ShaderProgram shader, Matrix4f rootInverse)
    {
        if (shader == null)
        {
            return;
        }

        BBSRendering.bindProgram(shader);
        setupUniforms(null, shader, true, rootInverse);
    }

    private static void setupUniforms(MatrixStack stack, ShaderProgram shader, boolean cpuPretransformed, Matrix4f rootInverse)
    {
        if (shader == null)
        {
            return;
        }

        BBSRendering.bindProgram(shader);

        BBSUniform.setMatrix4f(shader, "ProjMat", BBSRendering.getProjectionMatrix());

        for (int i = 0; i < 12; i++)
        {
            BBSUniform.set(shader, "Sampler" + i, i);
        }

        if (cpuPretransformed)
        {
            if (usesCapturedModelView())
            {
                /* Captured draws already baked the full transform into the vertex buffer. */
                BBSUniform.setMatrix4f(shader, "ModelViewMat", IDENTITY_MODEL_VIEW);
            }
            else
            {
                BBSUniform.setMatrix4f(shader, "ModelViewMat", RenderSystem.getModelViewMatrix());
            }
        }
        else
        {
            ModelVAORenderer.setModelViewUniform(stack, shader);
        }

        ModelVAORenderer.uploadFogMatUniform(stack, shader, cpuPretransformed);

        /* NormalMat is present by default in Iris' shaders, but when there is no Iris,
         * the BBS mod's model.json shader is being used instead that provides NormalMat
         * uniform.
         */
        if (cpuPretransformed && stack == null)
        {
            BBSUniform.setMatrix3f(shader, "NormalMat", RenderSystem.getModelViewMatrix().normal(new Matrix3f()));
        }
        else if (stack != null)
        {
            if (usesCapturedModelView() || !BBSRendering.isIrisShadersEnabled())
            {
                BBSUniform.setMatrix3f(shader, "NormalMat", stack.peek().getNormalMatrix());
            }
            else
            {
                Matrix3f normalMat = RenderSystem.getModelViewMatrix().normal(new Matrix3f());
                normalMat.mul(stack.peek().getNormalMatrix());
                BBSUniform.setMatrix3f(shader, "NormalMat", normalMat);
            }
        }

        BBSUniform.set(shader, "PaintColor", paintR, paintG, paintB, paintStrength);

        glowingUniformActive = shader.getUniform("GlowingColor") != null;
        BBSUniform.set(shader, "GlowingColor", glowR, glowG, glowB, glowStrength);

        BBSUniform.set(shader, "GlowPaintOnly", glowPaintOnly ? 1F : 0F);
        BBSUniform.set(shader, "PaintOverlay", paintOverlayPass ? 1F : 0F);
        BBSUniform.set(shader, "TextureBlendFactor", ModelVAORenderer.textureBlendActive ? ModelVAORenderer.textureBlendFactor : 0F);
        BBSUniform.set(shader, "TextureBlendActive", ModelVAORenderer.textureBlendActive ? 1F : 0F);

        if (cpuPretransformed && rootInverse != null)
        {
            BBSUniform.setMatrix4f(shader, "FormRootInverse", rootInverse);
        }
        else
        {
            BBSUniform.setMatrix4f(shader, "FormRootInverse", overlayFormRootInverse());
        }

        BBSUniform.setMatrix4f(shader, "PaintEffectInverse", paintEffectInverse);
        BBSUniform.set(shader, "PaintEffectActive", paintEffectActive ? 1F : 0F);
        BBSUniform.set(shader, "PaintMaskHalf", paintMaskHalf.x, paintMaskHalf.y, paintMaskHalf.z);
        BBSUniform.set(shader, "PaintMaskBottomAnchored", paintMaskBottomAnchored ? 1F : 0F);
        BBSUniform.set(shader, "PaintMaskShape", paintMaskShape);

        BBSUniform.setMatrix4f(shader, "GlowEffectInverse", glowEffectInverse);
        BBSUniform.set(shader, "GlowEffectActive", glowEffectActive ? 1F : 0F);
        BBSUniform.set(shader, "GlowMaskHalf", glowMaskHalf.x, glowMaskHalf.y, glowMaskHalf.z);
        BBSUniform.set(shader, "GlowMaskBottomAnchored", glowMaskBottomAnchored ? 1F : 0F);
        BBSUniform.set(shader, "GlowMaskShape", glowMaskShape);

        BBSUniform.setMatrix4f(shader, "ColorEffectInverse", colorEffectInverse);
        BBSUniform.set(shader, "ColorEffectActive", colorEffectActive ? 1F : 0F);
        BBSUniform.set(shader, "ColorMaskHalf", colorMaskHalf.x, colorMaskHalf.y, colorMaskHalf.z);
        BBSUniform.set(shader, "ColorMaskBottomAnchored", colorMaskBottomAnchored ? 1F : 0F);
        BBSUniform.set(shader, "ColorMaskShape", colorMaskShape);

        BBSUniform.set(shader, "FormColorTint", formColorR, formColorG, formColorB, formColorA);
        BBSUniform.set(shader, "FormColorGrade", formColorGradeBrightness, formColorGradeContrast, formColorGradeHue, formColorGradeSaturation);

        gradeBrightnessMask.upload(shader, "GradeBrightness");
        gradeContrastMask.upload(shader, "GradeContrast");
        gradeHueMask.upload(shader, "GradeHue");
        gradeSaturationMask.upload(shader, "GradeSaturation");

        BBSUniform.set(shader, "ColorTintMasked", colorTintMasked ? 1F : 0F);
        BBSUniform.set(shader, "ColorTintOverlay", colorTintOverlayPass ? 1F : 0F);
        BBSUniform.set(shader, "ColorGradeOverlay", colorGradeOverlayPass ? 1F : 0F);

        /* Paint/tint/grade overlays multiply an already-fogged base — skip distance fog.
         * Full-mesh deferred redraws (soft opacity / soft limbs) use fog captured at enqueue
         * (RenderSystem is often wrong after Iris composite or vanilla LAST). Live draws use
         * current RenderSystem fog. */
        if (paintOverlayPass || colorTintOverlayPass || colorGradeOverlayPass)
        {
            BBSUniform.set(shader, "FogStart", 1_000_000F);
            BBSUniform.set(shader, "FogEnd", 1_000_001F);
            BBSUniform.set(shader, "FogColor", 0F, 0F, 0F, 0F);
            BBSUniform.set(shader, "FogShape", 0);
        }

        BBSUniform.set(shader, "ColorModulator", 1F, 1F, 1F, 1F);
    }

    private static float viewOriginLengthSq(Matrix4f view)
    {
        float x = view.m30();
        float y = view.m31();
        float z = view.m32();

        return x * x + y * y + z * z;
    }

    /**
     * Fog uniforms for overlays that bake {@code bakedModelMatrix} into vertex {@code Position}
     * (flat color-tint / paint on labels & billboards). {@code FogMat} maps those positions
     * back to camera-relative Y-up for cylindrical fog — identity when the bake was already
     * camera-relative, inverse-view when the bake included view rotation.
     */
    public static void uploadCpuBakedVertexFog(ShaderProgram shader, Matrix4f bakedModelMatrix)
    {
        if (shader == null)
        {
            return;
        }

        if (bakedModelMatrix == null)
        {
            BBSUniform.setMatrix4f(shader, "FogMat", IDENTITY_MODEL_VIEW);

            return;
        }

        if (BBSRendering.isRenderingWorld())
        {
            float bakedDist = viewOriginLengthSq(bakedModelMatrix);

            SCRATCH_COMPOSED.set(BBSRendering.camera).mul(bakedModelMatrix);

            if (bakedDist > 1.0E-6F && viewOriginLengthSq(SCRATCH_COMPOSED) < bakedDist * 0.49F)
            {
                /* Bake already included view — Position is view-space; strip for fog. */
                MatrixStackUtils.loadInverseViewRotationMatrix4(SCRATCH_INV_VIEW);
                BBSUniform.setMatrix4f(shader, "FogMat", SCRATCH_INV_VIEW);
            }
            else
            {
                /* Bake was camera-relative — Position is already Y-up cam-rel. */
                BBSUniform.setMatrix4f(shader, "FogMat", IDENTITY_MODEL_VIEW);
            }
        }
        else
        {
            BBSUniform.setMatrix4f(shader, "FogMat", IDENTITY_MODEL_VIEW);
        }
    }

    /**
     * Camera-relative model matrix for fog — same space vanilla bakes into entity
     * {@code Position} and terrain {@code Position + ChunkOffset} (Y-up, no view rotation).
     */
    private static void uploadFogMatUniform(MatrixStack stack, ShaderProgram shader, boolean cpuPretransformed)
    {
        if (cpuPretransformed || stack == null)
        {
            BBSUniform.setMatrix4f(shader, "FogMat", IDENTITY_MODEL_VIEW);

            return;
        }

        if (paintOverlayPass || colorTintOverlayPass || colorGradeOverlayPass)
        {
            /* Fog disabled for these passes — FogMat unused. */
            BBSUniform.setMatrix4f(shader, "FogMat", IDENTITY_MODEL_VIEW);

            return;
        }

        Matrix4f stackMatrix = stack.peek().getPositionMatrix();

        if (deferredTranslucentPass)
        {
            /* Soft / deferred BBS path: stack is capturePaintOverlayRootMatrix = MV_enqueue × camRel
             * (or camRel alone when MV was identity). Strip with the enqueue-time MV inverse from
             * DeferredFogSnapshot so FogMat stays camera-relative Y-up at every yaw/pitch. */
            if (activeDeferredFog != null)
            {
                SCRATCH_FOG_MAT.set(activeDeferredFog.modelViewInverse).mul(stackMatrix);
            }
            else
            {
                /* No snapshot — assume stack is already camera-relative (do not use Camera quaternion). */
                SCRATCH_FOG_MAT.set(stackMatrix);
            }

            BBSUniform.setMatrix4f(shader, "FogMat", SCRATCH_FOG_MAT);

            return;
        }

        if (BBSRendering.isRenderingWorld() && !BBSRendering.isIrisShadersEnabled())
        {
            float bakedDist = viewOriginLengthSq(stackMatrix);

            SCRATCH_COMPOSED.set(BBSRendering.camera).mul(stackMatrix);

            if (bakedDist > 1.0E-6F && viewOriginLengthSq(SCRATCH_COMPOSED) < bakedDist * 0.49F)
            {
                /* Stack already includes view (AFTER_ENTITIES) — strip rotation for fog only. */
                MatrixStackUtils.loadInverseViewRotationMatrix4(SCRATCH_INV_VIEW);
                SCRATCH_FOG_MAT.set(SCRATCH_INV_VIEW).mul(stackMatrix);
            }
            else
            {
                /* Stack is camera-relative entity transform — same as WorldRenderer entity MatrixStack. */
                SCRATCH_FOG_MAT.set(stackMatrix);
            }
        }
        else
        {
            /* Iris / UI: best-effort strip view from composed model-view. */
            SCRATCH_COMPOSED.set(RenderSystem.getModelViewMatrix()).mul(stackMatrix);
            MatrixStackUtils.loadInverseViewRotationMatrix4(SCRATCH_INV_VIEW);
            SCRATCH_FOG_MAT.set(SCRATCH_INV_VIEW).mul(SCRATCH_COMPOSED);
        }

        BBSUniform.setMatrix4f(shader, "FogMat", SCRATCH_FOG_MAT);
    }

    private static void setModelViewUniform(MatrixStack stack, ShaderProgram shader)
    {
        if (usesCapturedModelView())
        {
            /* Overlay/deferred stack already carries the full terrain + entity transform captured
             * at enqueue; RenderSystem model-view is identity during these draws. */
            BBSUniform.setMatrix4f(shader, "ModelViewMat", stack.peek().getPositionMatrix());

            return;
        }

        Matrix4f stackMatrix = stack.peek().getPositionMatrix();

        if (BBSRendering.isRenderingWorld() && !BBSRendering.isIrisShadersEnabled())
        {
            float bakedDist = viewOriginLengthSq(stackMatrix);

            SCRATCH_MODEL_VIEW.set(BBSRendering.camera).mul(stackMatrix);

            if (bakedDist > 1.0E-6F && viewOriginLengthSq(SCRATCH_MODEL_VIEW) < bakedDist * 0.49F)
            {
                SCRATCH_MODEL_VIEW.set(stackMatrix);
            }

            BBSUniform.setMatrix4f(shader, "ModelViewMat", SCRATCH_MODEL_VIEW);

            return;
        }

        SCRATCH_MODEL_VIEW.set(RenderSystem.getModelViewMatrix()).mul(stackMatrix);
        BBSUniform.setMatrix4f(shader, "ModelViewMat", SCRATCH_MODEL_VIEW);
    }
}
