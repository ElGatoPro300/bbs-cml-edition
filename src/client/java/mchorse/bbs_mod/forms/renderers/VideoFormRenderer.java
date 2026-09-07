package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.client.video.VideoFormEngine;
import mchorse.bbs_mod.client.video.VideoFormPlayback;
import mchorse.bbs_mod.client.video.VideoRenderer;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.ITickable;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.VideoForm;
import mchorse.bbs_mod.forms.forms.utils.VideoResolution;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.FFMpegUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.Quad;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.io.File;
import java.util.function.Supplier;

/**
 * VideoForm quad powered by {@link VideoFormEngine} (async scaled ffmpeg).
 * Single-sided front only. WaterMedia is only a last-resort fallback.
 */
public class VideoFormRenderer extends FormRenderer<VideoForm> implements ITickable
{
    private static final Quad QUAD = new Quad();
    private static final float FACE_Z_BIAS = 0.0005F;
    /** Dark waiting tint — never cyan/blue “error screen”. */
    private static final int PLACEHOLDER_COLOR = 0xFF141414;
    /** Wall-clock freeze while Minecraft pause menu is open. */
    private static long pauseFreezeMs = -1L;
    /** Wall/time anchor so play continues from the scrubbed Time value. */
    private long playAnchorTime = -1L;
    private long playAnchorWallMs = -1L;
    private int lastScrubTime = Integer.MIN_VALUE;
    private float lastSpeed = Float.NaN;
    /** Last decoded frame size — used for stencil pick quads (never decode during pick). */
    private float lastFrameW = 16F;
    private float lastFrameH = 9F;

    public VideoFormRenderer(VideoForm form)
    {
        super(form);
    }

    @Override
    public void tick(IEntity entity)
    {}

    @Override
    public void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        MatrixStack stack = context.batcher.getContext().getMatrices();

        stack.push();

        Matrix4f uiMatrix = ModelFormRenderer.getUIMatrix(context, x1, y1, x2, y2);

        this.applyTransforms(uiMatrix, context.getTransition());
        MatrixStackUtils.multiply(stack, uiMatrix);
        stack.translate(0F, 1F, 0F);
        stack.scale(1.5F, 1.5F, 1.5F);
        stack.scale(this.form.uiScale.get(), this.form.uiScale.get(), this.form.uiScale.get());

        Vector3f light0 = new Vector3f(0.85F, 0.85F, -1F).normalize();
        Vector3f light1 = new Vector3f(-0.85F, 0.85F, 1F).normalize();
        RenderSystem.setupLevelDiffuseLighting(light0, light1);

        this.renderModel(stack, Colors.WHITE, context.getTransition(), null, true, true, null);

        DiffuseLighting.disableGuiDepthLighting();
        stack.pop();
    }

    @Override
    public void render3D(FormRenderingContext context)
    {
        if (context.isShadowPass || BBSRendering.isIrisShadowPass())
        {
            return;
        }

        /* Stencil / Alt-pick must NEVER touch WaterMedia or ffmpeg. Decoding while the
         * pick FBO is bound seeks the shared player every mouse move → FPS collapse and
         * the film preview looking like it scrubs forward/back. */
        if (context.isPicking())
        {
            this.renderPickProxy(context);

            return;
        }

        this.renderModel(context.stack, context.color, context.getTransition(), context.camera,
            false, context.modelRenderer, context);
    }

    /**
     * Cheap solid quad for entity picking — same aspect as the last decoded frame.
     */
    private void renderPickProxy(FormRenderingContext context)
    {
        float w = Math.max(1F, this.lastFrameW);
        float h = Math.max(1F, this.lastFrameH);
        float ratioX = w > h ? h / w : 1F;
        float ratioY = h > w ? w / h : 1F;
        float halfW = 0.5F * ratioY;
        float fullH = ratioX;

        QUAD.p1.set(-halfW, fullH, 0F);
        QUAD.p2.set(halfW, fullH, 0F);
        QUAD.p3.set(-halfW, 0F, 0F);
        QUAD.p4.set(halfW, 0F, 0F);

        if (this.form.billboard.get())
        {
            Matrix4f modelMatrix = context.stack.peek().getPositionMatrix();
            Vector3f scale = new Vector3f();

            modelMatrix.getScale(scale);
            modelMatrix.m00(1).m01(0).m02(0);
            modelMatrix.m10(0).m11(1).m12(0);
            modelMatrix.m20(0).m21(0).m22(1);

            if (context.camera != null)
            {
                modelMatrix.mul(context.camera.view);
            }

            modelMatrix.scale(scale);
            context.stack.peek().getNormalMatrix().identity();
        }

        Color tint = new Color().set(context.color, true);
        Color formColor = this.form.getFormColor().copyWithBlendIntensity();

        tint.mul(formColor);

        float formOpacity = this.form.getFormOpacity();

        if (formOpacity <= 0.001F)
        {
            tint.a = Math.max(tint.a, 1F);
        }
        else
        {
            this.form.applyFormOpacity(tint);
        }

        if (tint.a <= 0.001F)
        {
            tint.a = 1F;
        }

        Supplier<ShaderProgram> pickShader = this.getShader(context,
            GameRenderer::getPositionColorProgram, BBSShaders::getPickerBillboardNoShadingProgram);
        Matrix4f positionMatrix = new Matrix4f(context.stack.peek().getPositionMatrix());
        Quad localQuad = new Quad();

        localQuad.copy(QUAD);
        this.drawSolidFront(positionMatrix, tint, localQuad, pickShader);
    }

    private static long playbackClockMs()
    {
        long now = System.currentTimeMillis();
        MinecraftClient client = MinecraftClient.getInstance();
        boolean paused = client != null && client.isPaused();

        if (paused)
        {
            if (pauseFreezeMs < 0L)
            {
                pauseFreezeMs = now;
            }

            return pauseFreezeMs;
        }

        if (pauseFreezeMs >= 0L)
        {
            pauseFreezeMs = -1L;
        }

        return now;
    }

    /**
     * Video frame tick.
     * <p>
     * In the film editor, time follows the film cursor (plus Time scrub), skipping spans where
     * {@code paused} keyframes are true so unpause resumes in sync with the timeline.
     * Outside the film editor, wall-clock advances from the scrub anchor.
     */
    private long getTickPosition(boolean playing, boolean filmDriven, int filmCursor, UIFilmPanel filmPanel)
    {
        int scrub = Math.max(0, this.form.time.get()) + this.form.offset.get();
        float speed = Math.max(0.01F, this.form.speed.get());

        if (filmDriven)
        {
            this.playAnchorTime = -1L;
            this.playAnchorWallMs = -1L;
            this.lastScrubTime = scrub;
            this.lastSpeed = speed;

            long activeFilmTicks = countUnpausedFilmTicks(filmPanel, filmCursor);

            return scrub + (long) (activeFilmTicks * speed);
        }

        if (!playing)
        {
            this.playAnchorTime = -1L;
            this.playAnchorWallMs = -1L;
            this.lastScrubTime = scrub;
            this.lastSpeed = speed;

            return scrub;
        }

        long now = playbackClockMs();
        boolean scrubChanged = scrub != this.lastScrubTime;
        boolean speedChanged = Float.isNaN(this.lastSpeed) || speed != this.lastSpeed;

        if (this.playAnchorTime < 0L || this.playAnchorWallMs < 0L || scrubChanged || speedChanged)
        {
            if (this.playAnchorTime >= 0L && this.playAnchorWallMs >= 0L && !scrubChanged && speedChanged)
            {
                this.playAnchorTime = this.playAnchorTime
                    + (long) ((now - this.playAnchorWallMs) / 50.0D * this.lastSpeed);
            }
            else
            {
                this.playAnchorTime = scrub;
            }

            this.playAnchorWallMs = now;
            this.lastScrubTime = scrub;
            this.lastSpeed = speed;
        }

        return this.playAnchorTime + (long) ((now - this.playAnchorWallMs) / 50.0D * speed);
    }

    /**
     * Film ticks from 0..cursor where VideoForm {@code paused} was false.
     * Pause keyframes freeze the video clock; after unpause, only post-unpause film time counts.
     */
    private static long countUnpausedFilmTicks(UIFilmPanel filmPanel, int filmCursor)
    {
        if (filmCursor <= 0)
        {
            return 0L;
        }

        KeyframeChannel<Boolean> pausedChannel = findPausedChannel(filmPanel);

        if (pausedChannel == null || pausedChannel.isEmpty())
        {
            return filmCursor;
        }

        double active = 0D;
        float prevTick = 0F;
        boolean paused = Boolean.TRUE.equals(pausedChannel.interpolate(0F, Boolean.FALSE));

        for (Keyframe<Boolean> keyframe : pausedChannel.getKeyframes())
        {
            float tick = keyframe.getTick();

            if (tick <= 0F)
            {
                paused = Boolean.TRUE.equals(keyframe.getValue());
                prevTick = Math.max(prevTick, tick);
                continue;
            }

            if (tick > filmCursor)
            {
                break;
            }

            if (!paused)
            {
                active += tick - prevTick;
            }

            paused = Boolean.TRUE.equals(keyframe.getValue());
            prevTick = tick;
        }

        if (!paused && filmCursor > prevTick)
        {
            active += filmCursor - prevTick;
        }

        return Math.max(0L, Math.round(active));
    }

    @SuppressWarnings("unchecked")
    private static KeyframeChannel<Boolean> findPausedChannel(UIFilmPanel filmPanel)
    {
        if (filmPanel == null || filmPanel.replayEditor == null)
        {
            return null;
        }

        try
        {
            Replay replay = filmPanel.replayEditor.getReplay();

            if (replay == null || replay.properties == null)
            {
                return null;
            }

            KeyframeChannel<?> channel = replay.properties.properties.get("paused");

            if (channel == null || channel.getFactory() != KeyframeFactories.BOOLEAN)
            {
                return null;
            }

            return (KeyframeChannel<Boolean>) channel;
        }
        catch (Exception ignored)
        {
            return null;
        }
    }

    private static UIFilmPanel getActiveFilmPanel()
    {
        try
        {
            UIDashboard dashboard = BBSModClient.getDashboard();

            if (dashboard != null && dashboard.getPanels() != null && dashboard.getPanels().panel instanceof UIFilmPanel film)
            {
                return film;
            }
        }
        catch (Exception ignored)
        {}

        return null;
    }

    /**
     * Film editor ENTITY renders must follow the timeline; world model-blocks keep wall-clock.
     */
    private static boolean isFilmDrivenContext(FormRenderingContext context)
    {
        return context != null && context.type == FormRenderType.ENTITY && getActiveFilmPanel() != null;
    }

    private void renderModel(MatrixStack matrices, int overlayColor, float transition, Camera camera,
        boolean invertY, boolean modelRenderer, FormRenderingContext deferContext)
    {
        String path = this.form.video.get();

        if (path == null || path.isEmpty() || path.equalsIgnoreCase("none") || path.startsWith("<"))
        {
            return;
        }

        boolean staticPreview = this.isStaticPreview(modelRenderer, deferContext);
        boolean allowFfmpegFallback = this.allowsFfmpegFallback(deferContext);
        String ffmpegPath = FFMpegUtils.getFFMPEG();
        boolean ffmpegOk = ffmpegPath != null && !ffmpegPath.isEmpty() && new File(ffmpegPath).isFile();
        boolean waterMedia = VideoRenderer.isAvailable();

        int textureId = 0;
        float w = 16F;
        float h = 9F;

        if (staticPreview)
        {
            /* Editor / inventory / item: still frame at scrubbed Time. */
            long stillTick = Math.max(0, this.form.time.get()) + this.form.offset.get();

            if (waterMedia)
            {
                VideoRenderer.FrameInfo waterStill = VideoRenderer.ensureFormStillFrame(path, stillTick, this.form.loop.get());

                if (waterStill != null && waterStill.textureId > 0)
                {
                    textureId = waterStill.textureId;
                    w = Math.max(1, waterStill.width);
                    h = Math.max(1, waterStill.height);
                }
            }

            if (textureId <= 0 && ffmpegOk)
            {
                VideoFormEngine.Frame still = VideoFormEngine.bindStill(path, stillTick, this.form.resolution.get());

                if (still != null && still.textureId > 0)
                {
                    textureId = still.textureId;
                    w = Math.max(1, still.width);
                    h = Math.max(1, still.height);
                }
            }
        }
        else
        {
            boolean gamePaused = MinecraftClient.getInstance().isPaused();
            boolean formPaused = this.form.paused.get();
            boolean filmDriven = isFilmDrivenContext(deferContext);
            UIFilmPanel filmPanel = filmDriven ? getActiveFilmPanel() : null;
            int filmCursor = filmPanel == null ? 0 : filmPanel.getCursor();
            boolean filmPlaying = filmPanel != null
                && filmPanel.isRunning()
                && !filmPanel.getController().isPaused();
            /* Film stop/pause OR form Pause track → freeze. Never free-run against the timeline. */
            boolean playing = !gamePaused && !formPaused && (!filmDriven || filmPlaying);

            long tickPosition = this.getTickPosition(playing, filmDriven, filmCursor, filmPanel);
            /* Film-driven: do not independently loop — that flashes the intro mid-timeline. */
            boolean loop = filmDriven ? false : this.form.loop.get();
            float distSq = this.getDistanceSqToCamera(deferContext);
            float speed = this.form.speed.get();
            int resolutionPreset = this.form.resolution.get();
            int maxLongSide = resolutionPreset > 0 ? resolutionPreset : 0;

            /* WaterMedia v3 primary decoder (hardware-accelerated, zero-copy, dynamic LODs). */
            if (waterMedia)
            {
                VideoRenderer.FrameInfo waterFrame = VideoRenderer.prepareFormFrame(
                    path, tickPosition, loop, distSq, maxLongSide, playing, filmDriven, speed, 0);

                if (waterFrame != null && waterFrame.textureId > 0)
                {
                    textureId = waterFrame.textureId;
                    w = Math.max(1, waterFrame.width);
                    h = Math.max(1, waterFrame.height);
                }
            }

            /* Fallback to background ffmpeg engine if WaterMedia is unavailable or pending. */
            if (textureId <= 0 && ffmpegOk)
            {
                VideoFormEngine.Frame engineFrame = VideoFormEngine.bindFrame(
                    path, tickPosition, speed, loop, this.form.resolution.get(), playing);

                if (engineFrame != null && engineFrame.textureId > 0)
                {
                    textureId = engineFrame.textureId;
                    w = Math.max(1, engineFrame.width);
                    h = Math.max(1, engineFrame.height);
                }
            }

            if (textureId <= 0 && allowFfmpegFallback && ffmpegOk)
            {
                VideoFormPlayback playback = VideoFormPlayback.get(path, maxLongSide);
                Texture ffmpegTexture = playback == null ? null : playback.ensureFrame(tickPosition, speed, loop);

                if (playback != null && playback.getWidth() > 0 && playback.getHeight() > 0)
                {
                    w = playback.getWidth();
                    h = playback.getHeight();
                }

                if (ffmpegTexture != null && ffmpegTexture.isValid())
                {
                    textureId = ffmpegTexture.id;
                }
                else if (!playing && playback != null)
                {
                    Texture last = playback.peekTexture();

                    if (last != null && last.isValid())
                    {
                        textureId = last.id;
                    }
                }
            }
        }

        if (textureId > 0 && w >= 2F && h >= 2F)
        {
            this.lastFrameW = w;
            this.lastFrameH = h;
        }

        float ratioX = w > h ? h / w : 1F;
        float ratioY = h > w ? w / h : 1F;
        float halfW = 0.5F * ratioY;
        float fullH = ratioX;

        QUAD.p1.set(-halfW, fullH, 0F);
        QUAD.p2.set(halfW, fullH, 0F);
        QUAD.p3.set(-halfW, 0F, 0F);
        QUAD.p4.set(halfW, 0F, 0F);

        if (this.form.billboard.get() && (deferContext == null || !deferContext.modelRenderer))
        {
            Matrix4f modelMatrix = matrices.peek().getPositionMatrix();
            Vector3f scale = new Vector3f();

            modelMatrix.getScale(scale);

            if (invertY)
            {
                scale.y = -scale.y;
            }

            modelMatrix.m00(1).m01(0).m02(0);
            modelMatrix.m10(0).m11(1).m12(0);
            modelMatrix.m20(0).m21(0).m22(1);

            if (camera != null && !modelRenderer)
            {
                modelMatrix.mul(camera.view);
            }

            modelMatrix.scale(scale);
            matrices.peek().getNormalMatrix().identity();
        }

        Color tint = new Color().set(overlayColor, true);
        Color formColor = this.form.getFormColor().copyWithBlendIntensity();

        tint.mul(formColor);

        /* Legacy VideoForm used color.a=0 as “no tint”, not invisible. Keep film visible. */
        float formOpacity = this.form.getFormOpacity();

        if (formOpacity <= 0.001F)
        {
            tint.a = Math.max(tint.a, 1F);
        }
        else
        {
            this.form.applyFormOpacity(tint);
        }

        if (tint.a <= 0.001F)
        {
            return;
        }

        /* Iris world pass: keep entity-local stack verts; live ModelView is the camera.
         * Do NOT bake ModelView into the matrix (that was only for deferred identity-MV flush). */
        boolean irisPass = deferContext != null
            && BBSRendering.isIrisWorldModelPass()
            && !BBSRendering.isIrisShadowPass();
        Matrix4f positionMatrix = new Matrix4f(matrices.peek().getPositionMatrix());
        Color tintSnapshot = tint.copy();
        Quad localQuad = new Quad();

        localQuad.copy(QUAD);

        int textureIdSnapshot = textureId;
        boolean linear = this.form.linear.get();
        boolean picking = deferContext != null && deferContext.isPicking();
        Supplier<ShaderProgram> pickShader = picking
            ? this.getShader(deferContext, GameRenderer::getPositionTexColorProgram, BBSShaders::getPickerBillboardNoShadingProgram)
            : null;

        Runnable draw = () ->
        {
            if (textureIdSnapshot > 0 && !picking)
            {
                this.drawVideoFront(positionMatrix, tintSnapshot, localQuad, textureIdSnapshot, linear);
            }
            else if (picking && pickShader != null)
            {
                this.drawSolidFront(positionMatrix, tintSnapshot, localQuad, pickShader);
            }
            else if (staticPreview)
            {
                /* UI / inventory only — never paint a bright blue error plane in the world. */
                Color placeholder = Color.rgba(PLACEHOLDER_COLOR);

                placeholder.a = tintSnapshot.a;
                this.drawSolidFront(positionMatrix, placeholder, localQuad, GameRenderer::getPositionColorProgram);
            }
        };

        if (irisPass)
        {
            /* Draw immediately. Deferring a WaterMedia/VLC texture lets Iris recycle or
             * rebind samplers before flush → magenta/black-stripe garbage and shader thrash.
             * Depth test+write in drawVideoFront still keeps the plane behind nearer geometry. */
            try
            {
                draw.run();
            }
            finally
            {
                BBSRendering.restoreWorldRenderState();
            }

            return;
        }

        try
        {
            draw.run();
        }
        finally
        {
            BBSRendering.restoreWorldRenderState();
        }
    }

    private float getDistanceSqToCamera(FormRenderingContext context)
    {
        if (context == null || context.ui || context.camera == null || context.stack == null)
        {
            return 0F;
        }

        Matrix4f m = context.stack.peek().getPositionMatrix();
        float x = m.m30();
        float y = m.m31();
        float z = m.m32();

        return x * x + y * y + z * z;
    }

    private boolean isStaticPreview(boolean modelRenderer, FormRenderingContext context)
    {
        if (context == null)
        {
            return true;
        }

        FormRenderType type = context.type;

        /* Form editor viewport (PREVIEW): play the video so authors see frames, not a blank still. */
        if (type == FormRenderType.PREVIEW)
        {
            return false;
        }

        if (modelRenderer)
        {
            return true;
        }

        return type == FormRenderType.ITEM_INVENTORY || type == FormRenderType.ITEM;
    }

    private boolean allowsFfmpegFallback(FormRenderingContext context)
    {
        if (context == null)
        {
            return true;
        }

        FormRenderType type = context.type;

        return type == FormRenderType.ENTITY
            || type == FormRenderType.MODEL_BLOCK
            || type == FormRenderType.PREVIEW;
    }

    /** Front face only — nothing on the back. Restores GL state so terrain stays valid. */
    private void drawVideoFront(Matrix4f matrix, Color tint, Quad quad, int textureId, boolean linear)
    {
        boolean previousCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean previousDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);

        try
        {
            RenderSystem.setShader(GameRenderer::getPositionTexProgram);
            RenderSystem.setShaderColor(tint.r, tint.g, tint.b, tint.a);
            /* Only RenderSystem — never glTexParameteri on WaterMedia/VLC textures.
             * Mutating wrap/filter on a non-2D / foreign texture throws GL_INVALID_ENUM
             * and can poison the block atlas → black world. */
            RenderSystem.setShaderTexture(0, textureId);

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);

            BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_TEXTURE);

            this.tex(buffer, matrix, quad.p3.x, quad.p3.y, FACE_Z_BIAS, 0F, 1F);
            this.tex(buffer, matrix, quad.p4.x, quad.p4.y, FACE_Z_BIAS, 1F, 1F);
            this.tex(buffer, matrix, quad.p2.x, quad.p2.y, FACE_Z_BIAS, 1F, 0F);

            this.tex(buffer, matrix, quad.p3.x, quad.p3.y, FACE_Z_BIAS, 0F, 1F);
            this.tex(buffer, matrix, quad.p2.x, quad.p2.y, FACE_Z_BIAS, 1F, 0F);
            this.tex(buffer, matrix, quad.p1.x, quad.p1.y, FACE_Z_BIAS, 0F, 0F);

            BufferRenderer.drawWithGlobalProgram(buffer.end());
        }
        finally
        {
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
            BBSRendering.restoreWorldRenderState();

            if (!previousCull)
            {
                RenderSystem.disableCull();
            }

            if (!previousDepthMask)
            {
                RenderSystem.depthMask(false);
            }
        }
    }

    private void drawSolidFront(Matrix4f matrix, Color color, Quad quad, Supplier<ShaderProgram> shader)
    {
        boolean previousCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean previousDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);

        try
        {
            RenderSystem.setShader(shader);
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);

            BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

            this.col(buffer, matrix, quad.p3.x, quad.p3.y, FACE_Z_BIAS, color);
            this.col(buffer, matrix, quad.p2.x, quad.p2.y, FACE_Z_BIAS, color);
            this.col(buffer, matrix, quad.p1.x, quad.p1.y, FACE_Z_BIAS, color);

            this.col(buffer, matrix, quad.p3.x, quad.p3.y, FACE_Z_BIAS, color);
            this.col(buffer, matrix, quad.p4.x, quad.p4.y, FACE_Z_BIAS, color);
            this.col(buffer, matrix, quad.p2.x, quad.p2.y, FACE_Z_BIAS, color);

            BufferRenderer.drawWithGlobalProgram(buffer.end());
        }
        finally
        {
            RenderSystem.depthMask(previousDepthMask);

            if (previousCull)
            {
                RenderSystem.enableCull();
            }
            else
            {
                RenderSystem.disableCull();
            }
        }
    }

    private void tex(BufferBuilder buffer, Matrix4f matrix, float x, float y, float z, float u, float v)
    {
        buffer.vertex(matrix, x, y, z).texture(u, v);
    }

    private void col(BufferBuilder buffer, Matrix4f matrix, float x, float y, float z, Color color)
    {
        buffer.vertex(matrix, x, y, z).color(color.r, color.g, color.b, color.a);
    }
}
