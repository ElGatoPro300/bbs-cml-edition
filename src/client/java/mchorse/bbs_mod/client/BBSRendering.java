package mchorse.bbs_mod.client;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.camera.clips.misc.ChromaSkyCurveSettings;
import mchorse.bbs_mod.camera.clips.misc.CurveClip;
import mchorse.bbs_mod.camera.controller.CameraWorkCameraController;
import mchorse.bbs_mod.camera.controller.PlayCameraController;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.client.compat.HdrModCompat;
import mchorse.bbs_mod.client.renderer.ModelBlockEntityRenderer;
import mchorse.bbs_mod.client.renderer.MorphRenderer;
import mchorse.bbs_mod.client.renderer.TriggerBlockEntityRenderer;
import mchorse.bbs_mod.client.screen.ScreenEffectRenderer;
import mchorse.bbs_mod.client.video.VideoRenderer;
import mchorse.bbs_mod.cubic.render.vao.ModelVAORenderer;
import mchorse.bbs_mod.events.ModelBlockEntityUpdateCallback;
import mchorse.bbs_mod.events.TriggerBlockEntityUpdateCallback;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.WorldFilmController;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.forms.renderers.utils.BlockPaintOverlayVertexConsumer;
import mchorse.bbs_mod.forms.renderers.utils.BlockPaintOverlayVertexSodiumConsumer;
import mchorse.bbs_mod.forms.renderers.utils.BlockPaintVertexConsumer;
import mchorse.bbs_mod.forms.renderers.utils.BlockPaintVertexSodiumConsumer;
import mchorse.bbs_mod.forms.renderers.utils.GlowEmissionVertexConsumer;
import mchorse.bbs_mod.forms.renderers.utils.GlowEmissionVertexSodiumConsumer;
import mchorse.bbs_mod.forms.renderers.utils.RecolorVertexConsumer;
import mchorse.bbs_mod.forms.renderers.utils.TextGlowEmissionVertexConsumer;
import mchorse.bbs_mod.forms.renderers.utils.TextGlowEmissionVertexSodiumConsumer;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.texture.TextureFormat;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.WorldPropertiesHelper;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIRenderingContext;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.VideoRecorder;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.iris.IrisUtils;
import mchorse.bbs_mod.utils.iris.ShaderCurves;
import mchorse.bbs_mod.utils.iris.ShaderOpacityPatch;
import mchorse.bbs_mod.utils.sodium.SodiumUtils;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.CompiledShaderPipeline;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.WindowFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import net.irisshaders.iris.uniforms.custom.cached.CachedUniform;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class BBSRendering
{
    /**
     * Cached rendered model blocks
     */
    public static final Set<ModelBlockEntity> capturedModelBlocks = new HashSet<>();

    /** Vanilla level diffuse basis (same as UIModelRenderer / DiffuseLighting world pass). */
    private static final Vector3f WORLD_LEVEL_LIGHT_0 = new Vector3f(0.2F, 1.0F, -0.7F).normalize();
    private static final Vector3f WORLD_LEVEL_LIGHT_1 = new Vector3f(-0.2F, 1.0F, 0.7F).normalize();

    public static boolean canRender;

    public static boolean renderingWorld;
    private static boolean irisChunkLayerPass;
    public static int lastAction;

    /* Optional IRLights / IRL-editor shadow baker (no hard dependency). */
    private static final String IRL_SHADOW_BAKE_STATE = "org.qualet.irl.light.shadow.ShadowBakeState";
    private static Boolean irlShadowBakePresent;
    private static Method irlShadowBakeIsBaking;

    public static final Matrix4f camera = new Matrix4f();
    public static final Matrix4f projection = new Matrix4f();

    /**
     * Iris world rendering multiplies the terrain {@code positionMatrix} into the
     * {@link MatrixStack} before entity transforms.
     * {@link Matrix4f#getTranslation()} on that product no longer equals the
     * camera-relative entity offset, so callers that rebuild world space from
     * translation + camera position must strip the terrain matrix first.
     */
    public static Matrix4f stripTerrainPositionMatrix(Matrix4f composed)
    {
        Matrix4f inverse = new Matrix4f(camera);

        inverse.invert();

        Matrix4f entity = new Matrix4f();

        inverse.mul(composed, entity);

        return entity;
    }

    private static boolean customSize;
    private static boolean iris;
    private static boolean sodium;
    private static boolean optifine;
    private static boolean forceDisableCull;

    private static int width;
    private static int height;
    /**
     * Scale used for this frame's fisheye FOV match (1 = off, {@code >1} widen,
     * {@code <1} narrow). Color grade reads this so the UV warp matches the projection.
     */
    private static float lensOverscanScale = 1F;

    private static final UIBaseMenu replayHudMenu = new UIBaseMenu() {};

    private static boolean toggleFramebuffer;
    private static Framebuffer framebuffer;
    private static Framebuffer clientFramebuffer;
    private static Texture texture;

    /** Private read FBO used to snapshot our framebuffer's colour attachment into {@link #texture}. */
    private static int captureReadFramebuffer = -1;

    private static CloudRenderMode cachedCloudRenderMode;
    private static boolean cloudsForced;

    public static int getMotionBlur()
    {
        return getMotionBlur(BBSSettings.videoSettings.frameRate.get(), getMotionBlurFactor());
    }

    public static int getMotionBlur(double fps, int target)
    {
        int i = 0;

        while (fps < target)
        {
            fps *= 2;

            i++;
        }

        return i;
    }

    public static int getMotionBlurFactor()
    {
        return getMotionBlurFactor(BBSSettings.videoSettings.motionBlur.get());
    }

    public static int getMotionBlurFactor(int integer)
    {
        return integer == 0 ? 0 : (int) Math.pow(2, 6 + integer);
    }

    public static int getVideoWidth()
    {
        return width == 0 ? BBSSettings.videoSettings.width.get() : width;
    }

    public static int getVideoHeight()
    {
        return height == 0 ? BBSSettings.videoSettings.height.get() : height;
    }

    public static float getLensOverscanScale()
    {
        return lensOverscanScale;
    }

    public static void setLensOverscanScale(float scale)
    {
        if (!Float.isFinite(scale) || scale <= 1.0e-3F)
        {
            lensOverscanScale = 1F;

            return;
        }

        lensOverscanScale = Math.abs(scale - 1F) > 1.0e-4F ? scale : 1F;
    }

    public static int getVideoFrameRate()
    {
        int frameRate = BBSSettings.videoSettings.frameRate.get();

        return frameRate * (1 << getMotionBlur(frameRate, getMotionBlurFactor()));
    }

    public static File getVideoFolder()
    {
        File movies = new File(BBSMod.getSettingsFolder().getParentFile(), "movies");
        File exportPath = new File(BBSSettings.videoSettings.path.get());

        if (exportPath.isDirectory())
        {
            movies = exportPath;
        }

        movies.mkdirs();

        return movies;
    }

    public static boolean canReplaceFramebuffer()
    {
        return customSize && renderingWorld;
    }

    /**
     * Skip the vanilla world pass when the open BBS menu does not need it (opaque editors, film
     * home page, model editor, etc.). Panels that show the live world override
     * {@link UIBaseMenu#needsWorldRender()}.
     */
    public static boolean shouldSkipWorldRender()
    {
        UIBaseMenu menu = UIScreen.getCurrentMenu();

        return menu != null && !menu.needsWorldRender();
    }

    /**
     * Ensures paint overlays draw into the same framebuffer as the film viewport world pass.
     */
    public static void ensurePaintOverlayTargetFramebuffer()
    {
        if (toggleFramebuffer && framebuffer != null)
        {
            reassignFramebuffer(framebuffer);
        }
        else
        {
            /* World / non-film path: Iris may leave a different FBO bound at frame end. */
            bindMainFramebuffer(false);
        }
    }

    /**
     * 1.21.11 replacement for {@code Framebuffer.beginWrite(boolean)}.
     */
    public static void bindMainFramebuffer(boolean clear)
    {
        Framebuffer fb = MinecraftClient.getInstance().getFramebuffer();

        RenderSystem.outputColorTextureOverride = null;
        RenderSystem.outputDepthTextureOverride = null;

        if (clear && fb != null && fb.getColorAttachment() != null && fb.getDepthAttachment() != null)
        {
            RenderSystem.getDevice().createCommandEncoder()
                .clearColorAndDepthTextures(fb.getColorAttachment(), 0, fb.getDepthAttachment(), 1.0D);
        }
    }

    public static void bindFramebuffer(Framebuffer fb, boolean clear)
    {
        if (fb == null)
        {
            bindMainFramebuffer(clear);
            return;
        }

        RenderSystem.outputColorTextureOverride = fb.getColorAttachmentView();
        RenderSystem.outputDepthTextureOverride = fb.getDepthAttachmentView();

        if (clear && fb.getColorAttachment() != null && fb.getDepthAttachment() != null)
        {
            RenderSystem.getDevice().createCommandEncoder()
                .clearColorAndDepthTextures(fb.getColorAttachment(), 0, fb.getDepthAttachment(), 1.0D);
        }
    }

    /**
     * Framebuffer whose color is sampled by ColorGradeOverlay (Iris-lit scene before regrade).
     */
    public static Framebuffer getPaintOverlaySourceFramebuffer()
    {
        if (toggleFramebuffer && framebuffer != null)
        {
            return framebuffer;
        }

        return MinecraftClient.getInstance().getFramebuffer();
    }

    public static boolean isCustomSize()
    {
        return customSize;
    }

    public static boolean isFramebufferToggled()
    {
        return toggleFramebuffer;
    }

    public static void setCustomSize(boolean customSize)
    {
        setCustomSize(customSize, 0, 0);
    }

    public static void setCustomSize(boolean customSize, int w, int h)
    {
        BBSRendering.customSize = customSize;

        width = !customSize ? 0 : w;
        height = !customSize ? 0 : h;

        if (!customSize)
        {
            ensureMainFramebuffer();
            resizeExtraFramebuffers();
        }
    }

    /**
     * Model/trigger block panels render directly to the main framebuffer. If a film
     * session left {@link #toggleFramebuffer} enabled, the world keeps drawing offscreen
     * and only the cleared sky color is visible behind the UI.
     */
    public static void ensureMainFramebuffer()
    {
        if (!toggleFramebuffer)
        {
            return;
        }

        toggleFramebuffer(false);
    }

    /**
     * After world / film FBO present: re-bind the client framebuffer and reset GUI draw state.
     * Used when HDR Mod's blit color-transform leaves FBO 0 / blend disabled before Batcher2D.
     */
    public static void prepareGuiAfterWorldPresent()
    {
        ensureMainFramebuffer();
        bindMainFramebuffer(false);
        restoreGuiRenderState();
        GlStateManager._colorMask(true, true, true, true);
        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
    }

    /**
     * Reset GL state after mid-UI 3D form/model draws so later Batcher2D text is not
     * left with additive blend / depthMask false / grade uniforms (white doubled glyphs).
     *
     * Never unbind VAO / ARRAY_BUFFER / ELEMENT_ARRAY_BUFFER here. On AMD (atio6axx)
     * that leaves Batcher2D's next glDrawElements with a null index path (hard crash)
     * or silently skips card chrome while form previews still draw through their own VAO.
     */
    public static void restoreGuiRenderState()
    {
        ModelVAORenderer.clearFormColorGrade();
        /* Keep vanilla's GL state cache synchronized between preview render passes. */
        GlStateManager._colorMask(true, true, true, true);
        GlStateManager._depthMask(true);
        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager._disableCull();
        GlStateManager._depthFunc(GL11.GL_ALWAYS);
        GL11.glPolygonOffset(0F, 0F);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
    }

    /**
     * Soft-opacity / glow / form draws can leave depthMask, blend, depth test, lightmap,
     * or overlay wrong. After model-block forms that also matters for WorldRenderer's later
     * flush of buffered vanilla entity layers (enchanted armor). Do not rewrite level lights.
     */
    public static void restoreWorldRenderState()
    {
        GlStateManager._depthMask(true);
        GlStateManager._colorMask(true, true, true, true);
        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(770, 771, 1, 0);
        GlStateManager._enableDepthTest();
        GlStateManager._depthFunc(GL11.GL_LEQUAL);
        GL11.glPolygonOffset(0F, 0F);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        CustomVertexConsumerProvider.clearRunnables();

        MinecraftClient client = MinecraftClient.getInstance();

        if (client != null && client.gameRenderer != null)
        {
            /* In 1.21.11, lightmap & overlay textures are managed via UBOs / shader pipelines automatically */
        }
    }

    /** Vanilla level diffuse basis shared by morphs and editor previews. */
    public static void setupWorldLevelDiffuseLighting()
    {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client != null && client.gameRenderer != null)
        {
            client.gameRenderer.getDiffuseLighting().setShaderLights(DiffuseLighting.Type.LEVEL);
        }
    }

    /**
     * Same diffuse choice {@link WorldRenderer} uses before entities:
     * {@link DiffuseLighting.Type#LEVEL} in level dimensions, otherwise the shared
     * {@link #setupWorldLevelDiffuseLighting()} basis.
     * Keeps model-block F7 world draws and editor UI previews on one lighting basis.
     */
    public static void setupMatchingWorldDiffuseLighting()
    {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client != null && client.world != null && client.gameRenderer != null)
        {
            client.gameRenderer.getDiffuseLighting().setShaderLights(DiffuseLighting.Type.LEVEL);
        }
    }

    /**
     * Block/sky lightmap at an entity position, or {@code fallback} when the entity has no world
     * (pure UI stubs). Used so form editor / model-block previews match F7 world shading.
     */
    public static int resolveEntityBlockLight(IEntity entity, int fallback)
    {
        if (entity == null || entity.getWorld() == null)
        {
            return fallback;
        }

        BlockPos pos = BlockPos.ofFloored(entity.getX(), entity.getY(), entity.getZ());

        return WorldRenderer.getLightmapCoordinates(entity.getWorld(), pos);
    }

    /**
     * Level diffuse + lightmap + overlay expected by LivingEntityRenderer cutout layers.
     * Used for MobForm morph draws (private Immediate) and villager clothing flush.
     */
    public static void prepareVanillaEntityLighting()
    {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client == null || client.gameRenderer == null)
        {
            return;
        }

        setupMatchingWorldDiffuseLighting();
    }

    public static Texture getTexture()
    {
        if (texture == null)
        {
            texture = new Texture();
            texture.setFormat(TextureFormat.RGB_U8);
            texture.setFilter(GL11.GL_NEAREST);
        }

        return texture;
    }

    public static void startTick()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        /* Client ticks still run while the pause menu is open, but world/block-entity ticks do
         * not — clearing here would empty the set with nothing to refill it, killing model-block
         * Iris shadows (and UI lists that reuse this cache) until unpause. */
        if (mc != null && mc.isPaused())
        {
            return;
        }

        capturedModelBlocks.clear();
        TriggerBlockEntityRenderer.capturedTriggerBlocks.clear();
    }

    public static void setup()
    {
        iris = FabricLoader.getInstance().isModLoaded("iris");
        sodium = FabricLoader.getInstance().isModLoaded("sodium");
        optifine = FabricLoader.getInstance().isModLoaded("optifabric");

        ModelBlockEntityUpdateCallback.EVENT.register((entity) ->
        {
            if (entity.hasWorld() && entity.getWorld().isClient())
            {
                capturedModelBlocks.add(entity);
            }
        });

        TriggerBlockEntityUpdateCallback.EVENT.register((entity) ->
        {
            if (entity.hasWorld() && entity.getWorld().isClient())
            {
                TriggerBlockEntityRenderer.capturedTriggerBlocks.add(entity);
            }
        });

        if (!iris)
        {
            return;
        }

        IrisUtils.setup();
    }

    /* Framebuffers */

    public static Framebuffer getFramebuffer()
    {
        return framebuffer;
    }

    public static void setupFramebuffer()
    {
        Window window = MinecraftClient.getInstance().getWindow();

        framebuffer = new WindowFramebuffer(window.getFramebufferWidth(), window.getFramebufferHeight());
    }

    public static void resizeExtraFramebuffers()
    {
        Set<Framebuffer> buffers = new HashSet<>();
        MinecraftClient mc = MinecraftClient.getInstance();

        buffers.add(mc.worldRenderer.getEntityOutlinesFramebuffer());
        buffers.add(mc.worldRenderer.getTranslucentFramebuffer());
        buffers.add(mc.worldRenderer.getEntityFramebuffer());
        buffers.add(mc.worldRenderer.getParticlesFramebuffer());
        buffers.add(mc.worldRenderer.getWeatherFramebuffer());
        buffers.add(mc.worldRenderer.getCloudsFramebuffer());

        for (Framebuffer buffer : buffers)
        {
            resizeFramebuffer(buffer);
        }
    }

    public static void resizeFramebuffer(Framebuffer framebuffer)
    {
        if (framebuffer == null)
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        int w = Math.max(2, mc.getWindow().getFramebufferWidth());
        int h = Math.max(2, mc.getWindow().getFramebufferHeight());

        if (framebuffer.textureWidth == w && framebuffer.textureHeight == h)
        {
            return;
        }

        framebuffer.resize(w, h);
    }

    public static void toggleFramebuffer(boolean toggleFramebuffer)
    {
        if (toggleFramebuffer == BBSRendering.toggleFramebuffer)
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        Window window = mc.getWindow();

        BBSRendering.toggleFramebuffer = toggleFramebuffer;

        if (toggleFramebuffer)
        {
            int w = Math.max(2, mc.getWindow().getFramebufferWidth());
            int h = Math.max(2, mc.getWindow().getFramebufferHeight());

            resizeExtraFramebuffers();

            if (framebuffer.textureWidth != w || framebuffer.textureHeight != h)
            {
                framebuffer.resize(w, h);
            }

            /* Never overwrite the real window FBO with our video-sized one. */
            Framebuffer current = mc.getFramebuffer();

            if (current != null && current != framebuffer)
            {
                clientFramebuffer = current;
            }

            reassignFramebuffer(framebuffer);
            bindFramebuffer(framebuffer, false);

            mc.worldRenderer.onResized(w, h);
        }
        else
        {
            Framebuffer target = clientFramebuffer != null ? clientFramebuffer : mc.getFramebuffer();

            if ((width != 0 || customSize) && framebuffer != null)
            {
                /* 1.21.11: Framebuffer.draw(w, h) -> blitToScreen() */
                framebuffer.blitToScreen();
            }

            if (target != null && target != framebuffer)
            {
                reassignFramebuffer(target);
            }

            /* framebuffer.draw() leaves its color-attachment texture bound on TU0
             * inside GlStateManager / RenderSystem.shaderTextures[0].  If we don't
             * clear it, every subsequent draw (chunks, forms, HUD) that queries
             * getShaderTexture(0) reads a stale id and renders black. */
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            GlStateManager._bindTexture(0);
            
            bindMainFramebuffer(false);

            int realW = window.getFramebufferWidth();
            int realH = window.getFramebufferHeight();

            mc.worldRenderer.onResized(realW, realH);
            resizeExtraFramebuffers();
        }
    }

    private static void reassignFramebuffer(Framebuffer framebuffer)
    {
        MinecraftClient.getInstance().framebuffer = framebuffer;
    }

    /* Rendering */

    public static void onWorldRenderBegin()
    {
        if (BBSRendering.shouldSkipWorldRender())
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        BBSModClient.getFilms().startRenderFrame(mc.getRenderTickCounter().getTickProgress(false));

        UIBaseMenu menu = UIScreen.getCurrentMenu();

        if (menu != null)
        {
            menu.startRenderFrame(mc.getRenderTickCounter().getTickProgress(false));
        }

        GlStateManager._depthFunc(GL11.GL_LEQUAL);
        GlStateManager._enableDepthTest();
        GlStateManager._depthMask(true);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);

        renderingWorld = true;
        ShaderOpacityPatch.onWorldRenderBegin();
        updateCloudRenderMode(mc);
        ModelVAORenderer.clearPaintOverlayQueue();

        if (!customSize)
        {
            ensureMainFramebuffer();

            return;
        }

        toggleFramebuffer(true);
    }

    public static void onWorldRenderEnd()
    {
        if (BBSRendering.shouldSkipWorldRender())
        {
            return;
        }

        /* Paint overlays first (and noshading soft forms in the same queue, after paint via
         * sort). Iris soft forms (noshading off) already flushed at beginTranslucents. */
        ModelVAORenderer.flushPaintOverlayQueue();
        ShaderOpacityPatch.onWorldRenderEnd();

        MinecraftClient mc = MinecraftClient.getInstance();
        UIBaseMenu currentMenu = UIScreen.getCurrentMenu();
        if (BBSModClient.getCameraController().getCurrent() instanceof PlayCameraController controller)
        {
            DrawContext drawContext = new DrawContext(mc, new GuiRenderState(), mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
            Batcher2D batcher = new Batcher2D(drawContext);
            Window window = mc.getWindow();
            Area area = new Area(0, 0, window.getScaledWidth(), window.getScaledHeight());

            VideoRenderer.renderClips(new MatrixStack(), batcher, controller.getContext().clips.getClips(controller.getContext().relativeTick), controller.getContext().relativeTick, true, area, area, null, area.w, area.h, false);
            VideoRenderer.renderClips(new MatrixStack(), batcher, controller.getContext().clips.getClips(controller.getContext().relativeTick), controller.getContext().relativeTick, true, area, area, null, area.w, area.h, true);

            ScreenEffectRenderer.render(batcher, controller.getContext(), area.w, area.h);
        }

        if (!customSize && BBSModClient.getVideoRecorder().isRecording() && BBSModClient.getCameraController().getCurrent() instanceof CameraWorkCameraController controller)
        {
            DrawContext drawContext = new DrawContext(mc, new GuiRenderState(), mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
            Batcher2D batcher = new Batcher2D(drawContext);
            Window window = mc.getWindow();
            Area area = new Area(0, 0, window.getScaledWidth(), window.getScaledHeight());

            VideoRenderer.renderClips(new MatrixStack(), batcher, controller.getContext().clips.getClips(controller.getContext().relativeTick), controller.getContext().relativeTick, true, area, area, null, area.w, area.h, false);
            VideoRenderer.renderClips(new MatrixStack(), batcher, controller.getContext().clips.getClips(controller.getContext().relativeTick), controller.getContext().relativeTick, true, area, area, null, area.w, area.h, true);

            ScreenEffectRenderer.render(batcher, controller.getContext(), area.w, area.h);
        }

        if (!customSize)
        {
            renderingWorld = false;

            return;
        }

        if (currentMenu instanceof UIDashboard dashboard)
        {
            if (dashboard.getPanels().panel instanceof UIFilmPanel panel && panel.needsViewportRender())
            {
                DrawContext drawContext = new DrawContext(mc, new GuiRenderState(), mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
                Batcher2D offscreenBatcher = new Batcher2D(drawContext);

                Window window = mc.getWindow();
                Area fullScreen = new Area(0, 0, window.getScaledWidth(), window.getScaledHeight());

                VideoRenderer.renderClips(new MatrixStack(), offscreenBatcher, panel.getData().camera.getClips(panel.getCursor()), panel.getCursor(), panel.getRunner().isRunning(), fullScreen, fullScreen, null, window.getScaledWidth(), window.getScaledHeight(), false);

            }
        }

        renderingWorld = false;
    }

    private static void updateCloudRenderMode(MinecraftClient mc)
    {
        boolean shouldHideClouds = isChromaSkyEnabled() && !isChromaSkyClouds();

        if (shouldHideClouds)
        {
            if (!cloudsForced)
            {
                cachedCloudRenderMode = mc.options.getCloudRenderMode().getValue();
                cloudsForced = true;
            }

            if (mc.options.getCloudRenderMode().getValue() != CloudRenderMode.OFF)
            {
                mc.options.getCloudRenderMode().setValue(CloudRenderMode.OFF);
            }
        }
        else if (cloudsForced)
        {
            if (cachedCloudRenderMode != null)
            {
                mc.options.getCloudRenderMode().setValue(cachedCloudRenderMode);
            }

            cloudsForced = false;
        }
    }

    public static void onRenderBeforeScreen()
    {
        /* Snapshot only when we actually redirected the world into our framebuffer this frame (film panel
         * open / recording). Outside that, mc.framebuffer was never swapped, so our framebuffer holds nothing
         * worth copying and the snapshot would just waste a per-frame GPU copy. */
        if (customSize)
        {
            Texture texture = getTexture();
            int w = framebuffer.textureWidth;
            int h = framebuffer.textureHeight;

            /* Snapshot the world that just rendered into our reassigned WindowFramebuffer into the BBS texture
             * that the film preview blits and the VideoRecorder reads back.
             *
             * 1.21.11: Framebuffer.beginWrite() was removed, so glCopyTexSubImage2D no longer has our framebuffer
             * bound as the GL read target (it would copy the desktop/window instead). Bind the colour attachment
             * to our own read FBO first, then glCopyTexSubImage2D into the (RGB8) snapshot — this also drops the
             * framebuffer's non-opaque sky alpha so the preview stays opaque. */
            if (texture.width != w || texture.height != h)
            {
                texture.bind();
                texture.setSize(w, h);
                texture.unbind();
            }

            if (captureReadFramebuffer == -1)
            {
                captureReadFramebuffer = GL30.glGenFramebuffers();
            }

            int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            int sourceId = ((GlTexture) framebuffer.getColorAttachment()).getGlId();

            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, captureReadFramebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, sourceId, 0);
            GL30.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);

            texture.bind();
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, w, h);
            texture.unbind();

            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
        }


        toggleFramebuffer(false);

        if (HdrModCompat.isHdrPresentationActive())
        {
            prepareGuiAfterWorldPresent();
        }
    }

    public static void onRenderChunkLayer(MatrixStack stack)
    {
        /* Fabric WorldRenderEvents.AFTER_ENTITIES already drives renderCoolStuff.
         * Legacy chunk-layer injection path is inert under 1.21.11 FrameGraph terrain. */
    }

    public static void onRenderChunkLayer(Matrix4f positionMatrix, Matrix4f projectionMatrix)
    {
    }

    public static void renderHud(DrawContext drawContext, float tickDelta)
    {
        Batcher2D batcher2D = new Batcher2D(drawContext);
        VideoRecorder videoRecorder = BBSModClient.getVideoRecorder();

        BBSModClient.getFilms().renderHud(batcher2D, tickDelta);
        StructurePickerClient.renderHud(batcher2D);

        boolean showRecordingOverlay = videoRecorder.isRecording() && BBSSettings.recordingOverlays.get() && UIScreen.getCurrentMenu() == null;

        if (showRecordingOverlay)
        {
            int count = videoRecorder.getCounter();
            String label = UIKeys.FILM_VIDEO_RECORDING.format(
                count,
                BBSModClient.getKeyRecordVideo().getBoundKeyLocalizedText().getString()
            ).get();

            int x = 5;
            int y = 5;
            int w = batcher2D.getFont().getWidth(label);

            batcher2D.box(x, y, x + 18 + w + 3, y + 16, Colors.A50);
            batcher2D.icon(Icons.SPHERE, Colors.RED | Colors.A100, x, y);
            batcher2D.textShadow(label, x + 18, y + 4);
        }

        if (UIScreen.getCurrentMenu() == null && BBSSettings.editorReplayHud.get())
        {
            renderSelectedReplayHud(drawContext, batcher2D, showRecordingOverlay ? 20 : 0);
        }
    }

    private static void renderSelectedReplayHud(DrawContext drawContext, Batcher2D batcher2D, int yOffset)
    {
        Replay replay = BBSModClient.getSelectedReplay();

        if (replay == null)
        {
            return;
        }

        /* P toggles visibility, but the HUD must only show while a film session is active. */
        UIDashboard dashboard = BBSModClient.getDashboard();

        if (dashboard != null)
        {
            UIFilmPanel filmPanel = dashboard.getPanel(UIFilmPanel.class);

            if (filmPanel == null || !filmPanel.hasActiveFilmSession())
            {
                return;
            }
        }

        Form form = replay.form.get();
        String label = getReplayHudLabel(replay);
        boolean hasLabel = BBSSettings.editorReplayHudDisplayName.get() && !label.isEmpty();
        boolean hasForm = form != null;

        if (!hasForm && !hasLabel)
        {
            return;
        }

        int size = hasForm ? 24 : 0;
        int padding = 3;
        int gap = hasForm && hasLabel ? 4 : 0;

        int margin = 5;
        float textScale = 0.67F;
        int textWidth = hasLabel ? batcher2D.getFont().getWidth(label) : 0;
        int textHeight = hasLabel ? batcher2D.getFont().getHeight() : 0;
        int scaledTextWidth = Math.round(textWidth * textScale);
        int scaledTextHeight = Math.round(textHeight * textScale);
        int boxH = Math.max(size, scaledTextHeight) + padding * 2;
        int textBoxW = hasLabel ? scaledTextWidth + padding * 2 : 0;
        int totalW = (hasForm ? size : 0) + (hasLabel ? gap + textBoxW : 0);
        int x = getReplayHudX(margin, totalW);
        int y = getReplayHudY(margin + yOffset, boxH);
        int contentX = x + padding;
        int contentY = y + padding;

        int textBoxX = contentX + (hasForm ? size + gap : 0) - padding;
        int textBoxH = scaledTextHeight + padding * 2;
        int textBoxY = y + (boxH - textBoxH) / 2;

        if (hasLabel)
        {
            batcher2D.box(textBoxX, textBoxY, textBoxX + textBoxW, textBoxY + textBoxH, Colors.A50);
        }

        if (hasForm)
        {
            MinecraftClient mc = MinecraftClient.getInstance();
            Window window = mc.getWindow();

            replayHudMenu.resize(window.getScaledWidth(), window.getScaledHeight());
            replayHudMenu.context.setup(new UIRenderingContext(drawContext));

            int modelX1 = contentX;
            int modelY1 = contentY + (boxH - padding * 2 - size) / 2;
            int modelX2 = modelX1 + size;
            int modelY2 = modelY1 + size;

            try
            {
                FormRenderer.setSuppressFormDisplayName(true);
                FormUtilsClient.renderUI(form, replayHudMenu.context, modelX1, modelY1, modelX2, modelY2);
            }
            finally
            {
                FormRenderer.setSuppressFormDisplayName(false);
            }
        }

        if (hasLabel)
        {
            int textX = textBoxX + padding;
            int textY = textBoxY + padding;

            /* 1.21.11: drawContext.getMatrices() returns Matrix3x2fStack without push/scale/pop */
            batcher2D.textShadow(label, textX / textScale, textY / textScale);
        }
    }

    private static String getReplayHudLabel(Replay replay)
    {
        String label = replay.label.get();

        if (!label.isEmpty())
        {
            return label;
        }

        Form form = replay.form.get();

        return form == null ? "" : form.getDefaultDisplayNameForHud();
    }

    private static int getReplayHudX(int margin, int totalW)
    {
        int position = BBSSettings.editorReplayHudPosition.get();
        int screenW = MinecraftClient.getInstance().getWindow().getScaledWidth();
        boolean right = position == 1 || position == 3;

        return right ? screenW - margin - totalW : margin;
    }

    private static int getReplayHudY(int margin, int boxH)
    {
        int position = BBSSettings.editorReplayHudPosition.get();
        int screenH = MinecraftClient.getInstance().getWindow().getScaledHeight();
        boolean bottom = position == 2 || position == 3;
        int extraTopLeft = position == 0 ? 12 : 0;

        return bottom ? screenH - margin - boxH : margin + extraTopLeft;
    }

    public static void renderCoolStuff(WorldRenderContext worldRenderContext)
    {
        if (MinecraftClient.getInstance().currentScreen instanceof UIScreen screen)
        {
            screen.renderInWorld(worldRenderContext);
        }

        BBSModClient.getFilms().render(worldRenderContext);
        StructurePickerRenderer.render(worldRenderContext);
    }

    public static boolean isOptifinePresent()
    {
        return optifine;
    }

    public static boolean isRenderingWorld()
    {
        return renderingWorld;
    }

    public static boolean isIrisChunkLayerPass()
    {
        return irisChunkLayerPass;
    }

    /**
     * Any Iris world draw (chunk-layer film/editor pass or entity/gbuffer pass). VAO models
     * must use the vanilla translucent program for the base pass so Iris can composite them;
     * the custom BBS model shader is only used for deferred paint/glow overlays.
     * Exception: form color alpha &lt; 1 must be deferred too — shader packs and vanilla
     * entity_translucent discard low vertex alpha; the BBS model shader only cuts out texture holes.
     */
    public static boolean isIrisWorldModelPass()
    {
        return isIrisShadersEnabled() && isRenderingWorld();
    }

    /**
     * With the Iris opacity fix, translucent opacities are redrawn after
     * VL clouds (post-deferred) so soft fades never punch the sky or get clouds composited
     * over the mesh. Near-opaque keeps the live Iris path with depth writes.
     */
    public static final float TRANSLUCENT_ALPHA_DISCARD_REF = 28F / 255F;

    /**
     * True when Iris would discard/mis-composite very low form opacity; queue a BBS redraw
     * after compositing. Slight opacity (e.g. {@code #e7}/{@code #fc}) stays on Iris.
     */
    public static boolean needsIrisTranslucentModelDeferral(float alpha)
    {
        if (!isIrisWorldModelPass() || isIrisShadowPass())
        {
            return false;
        }

        return alpha < TRANSLUCENT_ALPHA_DISCARD_REF;
    }

    /**
     * Opt-in "No shading": redraw this form on the BBS deferred queue
     * after Iris composite.
     * When off, forms stay on Iris live pipeline with pack shaders and lighting.
     * When on, forms are deferred and drawn with vanilla/BBS shader (no pack lighting/shadows).
     * Controlled by {@link BBSSettings#noshadingOpaqueForms} (default true).
     */
    public static boolean needsIrisNoshadingOpacityDeferral(float alpha, boolean noshadingOpacity)
    {
        if (!noshadingOpacity || !isIrisWorldModelPass() || isIrisShadowPass())
        {
            return false;
        }

        boolean allowOpaque = BBSSettings.noshadingOpaqueForms == null || BBSSettings.noshadingOpaqueForms.get();

        return alpha > 0.001F && (allowOpaque || alpha < 0.999F);
    }

    /**
     * Iris live path keeps the user's alpha. Do not pull toward a sub-{@code alphaTestRef}
     * handoff — that made models vanish around {@code #2e}/{@code #2c} before the
     * {@code #1c}→{@code #1b} deferral switch.
     */
    public static float easeIrisModelAlpha(float alpha)
    {
        return alpha;
    }

    /**
     * Lift deferred alpha toward {@link #TRANSLUCENT_ALPHA_DISCARD_REF} so the first deferred
     * step ({@code #1b}) matches the last Iris step ({@code #1d}) — continuous handoff with
     * near-zero jump; deeper alphas stay near the user value.
     */
    public static float easeDeferredModelAlpha(float alpha)
    {
        if (!isIrisWorldModelPass() || isIrisShadowPass())
        {
            return alpha;
        }

        if (alpha <= 0F || alpha >= TRANSLUCENT_ALPHA_DISCARD_REF)
        {
            return alpha;
        }

        float t = alpha / TRANSLUCENT_ALPHA_DISCARD_REF;

        t = t * t * (3F - 2F * t);

        /* t→1 at #1b/#1c edge → ≈ REF (match Iris #1d); t→0 → stay near zero. */
        return alpha + (TRANSLUCENT_ALPHA_DISCARD_REF - alpha) * t;
    }

    /**
     * Deferred Iris low-alpha redraw ({@code #1b} and below): keep alpha, force RGB black
     * ({@code #aa000000}). White RGB on the BBS deferred path brightens vs Iris; black matches
     * the Iris handoff. Above the threshold the live Iris path keeps the user RGB ({@code ffffff}).
     */
    public static void applyDeferredModelHandoffRgb(Color color)
    {
        if (color == null)
        {
            return;
        }

        color.r = 0F;
        color.g = 0F;
        color.b = 0F;
    }

    /**
     * Flat forms (shape/billboard) through Iris translucent at any alpha &lt; 1 wash fog/sky.
     * Always defer them under Iris; they do not need pack mesh shading.
     */
    public static boolean needsIrisTranslucentFlatDeferral(float alpha)
    {
        return isIrisWorldModelPass() && !isIrisShadowPass() && alpha < 0.999F;
    }

    /**
     * Vanilla entity_translucent discards below {@link #TRANSLUCENT_ALPHA_DISCARD_REF}. Use for
     * Shape/Billboard without Iris: switch to the BBS model shader in-place with normal depth.
     */
    public static boolean needsBbsModelForLowOpacity(float alpha)
    {
        return !isIrisShadowPass() && alpha < TRANSLUCENT_ALPHA_DISCARD_REF;
    }

    /**
     * Iris entity/gbuffer pass (not the chunk-layer film/editor hook). Used to decide whether
     * paint overlays run immediately or are queued for {@code WorldRenderEvents.LAST}.
     */
    public static boolean isIrisDeferredModelPass()
    {
        return isIrisWorldModelPass() && !isIrisChunkLayerPass();
    }

    /**
     * When true, paint overlays must be queued for {@link ModelVAORenderer#flushPaintOverlayQueue()}
     * at the end of the world frame (Iris shader-pack path). Without Iris they run immediately
     * after each form so depth ordering against other entities stays correct.
     */
    public static boolean shouldDeferPaintOverlayToFrameEnd()
    {
        return isIrisWorldModelPass();
    }

    /**
     * When true, VAO model paint must not be applied in the base pass; use the BBS model
     * shader overlay ({@link ModelVAORenderer#submitPaintOverlay}) so paint matches under Iris.
     */
    public static boolean isIrisWorldPaintDeferral()
    {
        return isIrisWorldModelPass();
    }

    public static boolean isIrisLoaded()
    {
        return iris;
    }

    public static boolean isIrisShadersEnabled()
    {
        if (!iris)
        {
            return false;
        }

        return IrisUtils.isShaderPackEnabled();
    }

    public static void toggleShaders()
    {
        if (!iris)
        {
            return;
        }

        IrisUtils.toggleShaders();
    }

    public static void openShaderPackScreen()
    {
        if (!iris)
        {
            return;
        }

        IrisUtils.openShaderPackScreen();
    }

    /**
     * True while any depth/shadow bake is drawing casters with light-space matrices.
     * Includes Iris's shadow pass and optional IRLights ({@code ShadowBakeState}) so
     * color/paint/grade overlays are not queued with light projections and flushed onto
     * the film color buffer (side-of-screen tint masks when a light touches an actor).
     */
    public static boolean isIrisShadowPass()
    {
        if (isIrlShadowBakePass())
        {
            return true;
        }

        if (!iris)
        {
            return false;
        }

        return IrisUtils.isShadowPass();
    }

    /**
     * IRLights / IRL-editor bake forms into per-light depth maps outside Iris's
     * {@code isRenderingShadowPass()}. Detected via reflection so BBS stays optional.
     */
    public static boolean isIrlShadowBakePass()
    {
        if (irlShadowBakePresent == Boolean.FALSE)
        {
            return false;
        }

        try
        {
            if (irlShadowBakePresent == null)
            {
                Class<?> bakeState = Class.forName(IRL_SHADOW_BAKE_STATE);

                irlShadowBakeIsBaking = bakeState.getMethod("isBaking");
                irlShadowBakePresent = Boolean.TRUE;
            }

            Object baking = irlShadowBakeIsBaking.invoke(null);

            return baking instanceof Boolean && (Boolean) baking;
        }
        catch (Throwable t)
        {
            irlShadowBakePresent = Boolean.FALSE;
            irlShadowBakeIsBaking = null;

            return false;
        }
    }

    public static void trackTexture(Texture texture)
    {
        if (!iris)
        {
            return;
        }

        IrisUtils.trackTexture(texture);
    }

    public static void setPBRTextureIntensity(float normalIntensity, float specularIntensity)
    {
        if (!iris)
        {
            return;
        }

        IrisUtils.setPBRTextureIntensity(normalIntensity, specularIntensity);
    }

    public static void clearPBRTextureIntensity()
    {
        if (!iris)
        {
            return;
        }

        IrisUtils.clearPBRTextureIntensity();
    }

    public static float[] calculateTangents(float[] t, float[] v, float[] n, float[] u)
    {
        if (!iris)
        {
            return t;
        }

        return IrisUtils.calculateTangents(t, v, n, u);
    }

    public static float[] calculateTangents(float[] v, float[] n, float[] u)
    {
        if (!iris)
        {
            return v;
        }

        return IrisUtils.calculateTangents(v, n, u);
    }

    public static void addUniforms(List<CachedUniform> list, Map<String, ShaderCurves.ShaderVariable> variableMap)
    {
        if (!iris)
        {
            return;
        }

        IrisUtils.addUniforms(list, variableMap);
    }

    public static List<String> getShadersSliderOptions()
    {
        if (!iris)
        {
            return Collections.emptyList();
        }

        return IrisUtils.getSliderProperties();
    }

    public static Map<String, String> getShadersLanguageMap(String language)
    {
        if (!iris)
        {
            return Collections.emptyMap();
        }

        return IrisUtils.getShadersLanguageMap(language);
    }

    /* Curves */

    private static Double getCurveValue(String key)
    {
        if (!MinecraftClient.getInstance().isOnThread())
        {
            return null;
        }

        if (BBSModClient.getCameraController().getCurrent() instanceof CameraWorkCameraController controller)
        {
            Map<String, Double> values = CurveClip.getValues(controller.getContext());

            if (values != null && values.containsKey(key))
            {
                return values.get(key);
            }
        }

        return getWorldFilmCurveValue(key);
    }

    /**
     * Curve values from an in-world film playback ({@link WorldFilmController}),
     * used when playing a film outside the BBS editor (no camera controller).
     */
    private static Double getWorldFilmCurveValue(String key)
    {
        for (BaseFilmController controller : BBSModClient.getFilms().getControllers())
        {
            if (!(controller instanceof WorldFilmController worldFilm))
            {
                continue;
            }

            if (worldFilm.hasFinished())
            {
                continue;
            }

            Map<String, Double> values = CurveClip.getValues(worldFilm.getCameraContext());

            if (values != null && values.containsKey(key))
            {
                return values.get(key);
            }
        }

        return null;
    }

    public static boolean isImmersiveWorldPanel()
    {
        UIBaseMenu menu = UIScreen.getCurrentMenu();

        if (!(menu instanceof UIDashboard dashboard))
        {
            return false;
        }

        UIDashboardPanel panel = dashboard.getPanels().panel;

        return panel != null && !panel.needsBackground();
    }

    /**
     * Chroma sky can hide terrain for film export and film editor preview, but
     * model/trigger block (and other world-editing) panels must always show the
     * live world behind their UI cards.
     */
    public static boolean shouldHideChromaTerrain()
    {
        if (!isChromaSkyEnabled() || isChromaSkyTerrain())
        {
            return false;
        }

        /* Film preview must match export: hide terrain when the toggle says so.
         * Other immersive panels (model/trigger editors, etc.) keep the world visible. */
        return !isImmersiveWorldPanel() || isFilmPanelOpen();
    }

    /**
     * Whether a specific block entity must be skipped while chroma sky is hiding terrain.
     * Model blocks can opt in (global setting overrides per-block).
     */
    public static boolean shouldHideChromaBlockEntity(BlockEntity blockEntity)
    {
        if (!shouldHideChromaTerrain())
        {
            return false;
        }

        if (blockEntity instanceof ModelBlockEntity modelBlock)
        {
            return !shouldRenderModelBlockOnChroma(modelBlock);
        }

        return true;
    }

    /**
     * Global chroma-sky model-block setting takes precedence over the per-block toggle.
     */
    public static boolean shouldRenderModelBlockOnChroma(ModelBlockEntity modelBlock)
    {
        if (BBSSettings.chromaSkyModelBlocks.get())
        {
            return true;
        }

        return modelBlock.getProperties().isChromaSky();
    }

    private static boolean isFilmPanelOpen()
    {
        UIBaseMenu menu = UIScreen.getCurrentMenu();

        if (!(menu instanceof UIDashboard dashboard))
        {
            return false;
        }

        return dashboard.getPanels().panel instanceof UIFilmPanel;
    }

    public static boolean isChromaSkyEnabled()
    {
        ChromaSkyCurveSettings settings = getChromaSkySettings();

        return settings != null ? settings.enabled : BBSSettings.chromaSkyEnabled.get();
    }

    public static boolean isChromaSkyTerrain()
    {
        ChromaSkyCurveSettings settings = getChromaSkySettings();

        return settings != null ? settings.terrain : BBSSettings.chromaSkyTerrain.get();
    }

    public static boolean isChromaSkyClouds()
    {
        ChromaSkyCurveSettings settings = getChromaSkySettings();

        return settings != null ? settings.clouds : BBSSettings.chromaSkyClouds.get();
    }

    public static float getChromaSkyBillboard()
    {
        ChromaSkyCurveSettings settings = getChromaSkySettings();

        return settings == null ? BBSSettings.chromaSkyBillboard.get() : settings.billboard;
    }

    public static int getChromaSkyColor()
    {
        ChromaSkyCurveSettings settings = getChromaSkySettings();

        return settings == null ? BBSSettings.chromaSkyColor.get() : settings.color.getARGBColor();
    }

    private static ChromaSkyCurveSettings getChromaSkySettings()
    {
        if (getCurveValue(CurveClip.CHROMA_SKY_MARKER) == null)
        {
            return null;
        }

        if (BBSModClient.getCameraController().getCurrent() instanceof CameraWorkCameraController controller)
        {
            return CurveClip.getChromaSkySettings(controller.getContext());
        }

        return null;
    }

    public static Long getTimeOfDay()
    {
        Double v = getCurveValue(ShaderCurves.SUN_ROTATION);

        return v == null ? null : (long) (v * 1000L);
    }

    /**
     * Sun-path yaw in degrees. Film curve (editor or in-world playback) overrides
     * World Properties when present.
     */
    public static float getSunPathRotationDegrees()
    {
        Double v = getCurveValue(ShaderCurves.SUN_PATH_ROTATION);

        if (v != null)
        {
            return v.floatValue();
        }

        return WorldPropertiesHelper.getSunPathRotation();
    }

    public static Double getBrightness()
    {
        Double v = getCurveValue(ShaderCurves.BRIGHTNESS);

        if (v != null)
        {
            return Math.max(0D, v) / 100D;
        }

        return null;
    }

    public static Double getWeather()
    {
        return getCurveValue(ShaderCurves.WEATHER);
    }

    public static Function<VertexConsumer, VertexConsumer> getColorConsumer(Color color)
    {
        if (sodium)
        {
            return (b) -> SodiumUtils.createVertexBuffer(b, color);
        }

        return (b) -> new RecolorVertexConsumer(b, color);
    }

    public static Function<VertexConsumer, VertexConsumer> getColorConsumer(Color color, Color paintColor)
    {
        if (paintColor == null || paintColor.a == 0F)
        {
            return getColorConsumer(color);
        }

        if (sodium)
        {
            return (b) -> SodiumUtils.createVertexBuffer(b, color, paintColor);
        }

        return (b) -> new RecolorVertexConsumer(b, color, paintColor);
    }

    public static Function<VertexConsumer, VertexConsumer> getBlockPaintConsumer(Color color, Color paintColor)
    {
        if (paintColor == null || paintColor.a == 0F)
        {
            return getColorConsumer(color);
        }

        if (sodium)
        {
            return (b) -> new BlockPaintVertexSodiumConsumer(b, color, paintColor);
        }

        return (b) -> new BlockPaintVertexConsumer(b, color, paintColor);
    }

    public static Function<VertexConsumer, VertexConsumer> getGlowOverlayConsumer(Color glowColor)
    {
        if (sodium)
        {
            return (b) -> new GlowEmissionVertexSodiumConsumer(b, glowColor);
        }

        return (b) -> new GlowEmissionVertexConsumer(b, glowColor);
    }

    public static Function<VertexConsumer, VertexConsumer> getTextGlowOverlayConsumer(Color glowColor)
    {
        if (sodium)
        {
            return (b) -> new TextGlowEmissionVertexSodiumConsumer(b, glowColor);
        }

        return (b) -> new TextGlowEmissionVertexConsumer(b, glowColor);
    }

    public static Function<VertexConsumer, VertexConsumer> getBlockPaintOverlayConsumer(Color paintColor)
    {
        if (sodium)
        {
            return (b) -> new BlockPaintOverlayVertexSodiumConsumer(b, paintColor);
        }

        return (b) -> new BlockPaintOverlayVertexConsumer(b, paintColor);
    }

    /**
     * Neutral white vertex colors for block color-tint multiply overlays (tint lives in uniforms).
     */
    public static Function<VertexConsumer, VertexConsumer> getBlockColorTintOverlayConsumer()
    {
        return getColorConsumer(Color.white());
    }

    public static void enableBlend()
    {
        GlStateManager._enableBlend();
    }

    public static void disableBlend()
    {
        GlStateManager._disableBlend();
    }

    public static void defaultBlendFunc()
    {
        GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
    }

    public static void blendFunc(int src, int dst)
    {
        GlStateManager._blendFuncSeparate(src, dst, GL11.GL_ONE, GL11.GL_ZERO);
    }

    public static void enableCull()
    {
        GlStateManager._enableCull();
    }

    public static void disableCull()
    {
        GlStateManager._disableCull();
    }

    public static void forceDisableCull(boolean force)
    {
        forceDisableCull = force;

        if (force)
        {
            GlStateManager._disableCull();
        }
    }

    public static boolean isCullForcedDisabled()
    {
        return forceDisableCull;
    }

    public static void enableDepthTest()
    {
        GlStateManager._enableDepthTest();
    }

    public static void disableDepthTest()
    {
        GlStateManager._disableDepthTest();
    }

    public static void depthFunc(int func)
    {
        GlStateManager._depthFunc(func);
    }

    public static void depthMask(boolean mask)
    {
        GlStateManager._depthMask(mask);
    }

    public static ShaderProgram getProgram(RenderPipeline pipeline)
    {
        if (pipeline == null)
        {
            return null;
        }

        try
        {
            CompiledRenderPipeline compiled = RenderSystem.getDevice().precompilePipeline(pipeline);
            if (compiled instanceof CompiledShaderPipeline shaderPipeline)
            {
                return shaderPipeline.program();
            }
        }
        catch (Exception e)
        {
            System.err.println("[BBSRendering] Failed to compile pipeline " + (pipeline != null ? pipeline.getLocation() : "null") + ": " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    public static ShaderProgram getEntityTranslucentProgram()
    {
        return getProgram(RenderPipelines.ENTITY_TRANSLUCENT);
    }

    public static ShaderProgram getPositionTexColorProgram()
    {
        return getProgram(RenderPipelines.GUI_TEXTURED);
    }

    public static ShaderProgram getGuiProgram()
    {
        return getProgram(RenderPipelines.GUI);
    }

    public static ShaderProgram getParticleProgram()
    {
        return getProgram(RenderPipelines.TRANSLUCENT_PARTICLE);
    }

    public static void setupEntityInUiLighting()
    {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.gameRenderer != null && client.gameRenderer.getDiffuseLighting() != null)
        {
            client.gameRenderer.getDiffuseLighting().setShaderLights(DiffuseLighting.Type.ENTITY_IN_UI);
        }
    }

    public static void setupLevelLighting()
    {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.gameRenderer != null && client.gameRenderer.getDiffuseLighting() != null)
        {
            client.gameRenderer.getDiffuseLighting().setShaderLights(DiffuseLighting.Type.LEVEL);
        }
    }

    public static void colorMask(boolean red, boolean green, boolean blue, boolean alpha)
    {
        GlStateManager._colorMask(red, green, blue, alpha);
    }

    public static void setShaderColor(float r, float g, float b, float a)
    {
        /* In 1.21.11 color modulation is handled per-layer or via uniform buffers */
    }

    public static void bindProgram(ShaderProgram program)
    {
        if (program != null && program != ShaderProgram.INVALID && program.getGlRef() > 0)
        {
            GL20.glUseProgram(program.getGlRef());
        }
    }

    public static void unbindProgram()
    {
        GL20.glUseProgram(0);
    }

    public static int getBoundTexture()
    {
        return GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
    }

    public static void blendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha)
    {
        GlStateManager._blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
    }

    public static void enableScissor(int x, int y, int w, int h)
    {
        GlStateManager._enableScissorTest();
        GlStateManager._scissorBox(x, y, w, h);
        RenderSystem.enableScissorForRenderTypeDraws(x, y, w, h);
    }

    public static void disableScissor()
    {
        GlStateManager._disableScissorTest();
        RenderSystem.disableScissorForRenderTypeDraws();
    }

    public static void setProjectionMatrix(Matrix4f matrix, ProjectionType type)
    {
        if (matrix != null)
        {
            projection.set(matrix);
        }

        if (RenderSystem.getDynamicUniforms() != null && matrix != null)
        {
            GpuBufferSlice slice = RenderSystem.getDynamicUniforms().write(matrix, new Vector4f(), new Vector3f(), new Matrix4f());
            RenderSystem.setProjectionMatrix(slice, type);
        }
    }

    public static Matrix4f getProjectionMatrix()
    {
        return projection;
    }

    public static int getTextureGlId(Identifier id)
    {
        AbstractTexture texture = MinecraftClient.getInstance().getTextureManager().getTexture(id);

        if (texture != null && texture.getGlTexture() instanceof GlTexture glTexture)
        {
            return glTexture.getGlId();
        }

        return 0;
    }

    public static void bindTexture(Identifier id)
    {
        int glId = getTextureGlId(id);

        if (glId > 0)
        {
            GlStateManager._bindTexture(glId);
        }
    }
}

