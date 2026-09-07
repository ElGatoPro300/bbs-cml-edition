package mchorse.bbs_mod.forms;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.graphics.Framebuffer;
import mchorse.bbs_mod.graphics.Renderbuffer;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;

import org.joml.Matrix4f;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Caches form UI previews (color + depth) keyed by form instance, revision, and orbit angle.
 * List cells clip with scissors — those must be disabled while filling the scratch FBO
 * or thumbnails bake black. Keys use identity hash (not {@link Form#getFormId()}) so
 * different models of the same type do not share one thumbnail.
 * <p>
 * Incomplete ModelForm loads (BBS spinner) are never cached — otherwise a frozen spinner
 * sticks until the form list is rebuilt.
 */
public final class FormUIPreviewCache
{
    private static final int MAX_ENTRIES = 512;
    private static final int ANGLE_BUCKETS = 48;
    private static final int MAX_FILLS_PER_FRAME = 96;
    private static final long FRAME_BUDGET_NS = 28_000_000L;
    /* Bake at 2× cell size, then linear-downscale for sharper morph thumbs. */
    private static final int SUPERSAMPLE_STATIC = 2;
    private static final int SUPERSAMPLE_FOLLOW = 2;
    /* Bump when bake settings change so stale soft thumbs are discarded. */
    private static final int BAKE_QUALITY = 2;

    private static final Map<Long, CacheEntry> CACHE = new LinkedHashMap<>()
    {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, CacheEntry> eldest)
        {
            if (this.size() > MAX_ENTRIES)
            {
                eldest.getValue().delete();

                return true;
            }

            return false;
        }
    };

    private static Framebuffer scratchFramebuffer;
    private static Texture scratchTexture;
    private static Renderbuffer scratchDepth;
    private static int scratchWidth;
    private static int scratchHeight;
    private static long budgetFrameNs;
    private static int fillsThisFrame;
    private static int clearedBakeQuality = -1;

    private FormUIPreviewCache()
    {}

    public static void clear()
    {
        for (CacheEntry entry : CACHE.values())
        {
            entry.delete();
        }

        CACHE.clear();
    }

    public static void render(Form form, UIContext context, int x1, int y1, int x2, int y2)
    {
        render(form, context, x1, y1, x2, y2, true);
    }

    /**
     * @param followMouse when false, uses a fixed preview angle (cheap; for category-card
     *                    mini-thumbs that must not thrash the fill budget on mouse move).
     */
    public static void render(Form form, UIContext context, int x1, int y1, int x2, int y2, boolean followMouse)
    {
        if (clearedBakeQuality != BAKE_QUALITY)
        {
            clearedBakeQuality = BAKE_QUALITY;
            clear();
        }

        if (form == null)
        {
            return;
        }

        int width = x2 - x1;
        int height = y2 - y1;

        if (width <= 0 || height <= 0)
        {
            return;
        }

        /* Never bake the BBS loading spinner into the cache. */
        if (!isPreviewReady(form))
        {
            FormUtilsClient.renderUI(form, context, x1, y1, x2, y2, false);

            return;
        }

        int revision = form.getEditRevision();
        int angleBucket = followMouse ? getAngleBucket(context, x1, x2) : getFixedAngleBucket();
        long key = buildKey(form, width, height, angleBucket);
        CacheEntry entry = CACHE.get(key);

        if (entry != null && entry.revision == revision && entry.texture != null && entry.texture.isValid())
        {
            thisBlit(context, entry.texture, x1, y1, width, height);

            return;
        }

        if (!thisBeginFill())
        {
            CacheEntry fallback = thisFindFallback(form, width, height, revision);

            if (fallback != null)
            {
                thisBlit(context, fallback.texture, x1, y1, width, height);
            }
            else
            {
                /* Live draw still works under the cell scissor. */
                FormUtilsClient.renderUI(form, context, x1, y1, x2, y2, false);
            }

            return;
        }

        if (entry == null)
        {
            entry = new CacheEntry();
            CACHE.put(key, entry);
        }

        int supersample = followMouse ? SUPERSAMPLE_FOLLOW : SUPERSAMPLE_STATIC;

        thisRenderToEntry(form, context, entry, width, height, revision, angleBucket, supersample);
        thisBlit(context, entry.texture, x1, y1, width, height);
    }

    /**
     * Model forms are ready only after the mesh finished loading. Other forms can bake immediately.
     */
    public static boolean isPreviewReady(Form form)
    {
        if (!(form instanceof ModelForm modelForm))
        {
            return true;
        }

        String modelId = modelForm.model.get();

        if (modelId == null || modelId.isEmpty())
        {
            return true;
        }

        /* Priority bump for anything the UI is trying to show right now. */
        return BBSModClient.getModels().getModel(modelId, true) != null;
    }

    private static void thisBlit(UIContext context, Texture texture, int x1, int y1, int width, int height)
    {
        context.batcher.flush();
        /* glCopyTexSubImage2D keeps OpenGL's bottom-left origin; GUI quads are top-left. */
        context.batcher.texturedBox(
            texture,
            Colors.WHITE,
            x1,
            y1,
            width,
            height,
            0,
            texture.height,
            texture.width,
            0,
            texture.width,
            texture.height
        );
        context.batcher.flush();
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        BBSRendering.restoreGuiRenderState();
    }

    private static boolean thisBeginFill()
    {
        long now = System.nanoTime();

        if (now - budgetFrameNs > FRAME_BUDGET_NS)
        {
            budgetFrameNs = now;
            fillsThisFrame = 0;
        }

        if (fillsThisFrame >= MAX_FILLS_PER_FRAME)
        {
            return false;
        }

        fillsThisFrame += 1;

        return true;
    }

    private static CacheEntry thisFindFallback(Form form, int width, int height, int revision)
    {
        int formKey = System.identityHashCode(form);

        for (CacheEntry entry : CACHE.values())
        {
            if (entry != null && entry.formKey == formKey && entry.width == width && entry.height == height
                && entry.revision == revision && entry.texture != null && entry.texture.isValid())
            {
                return entry;
            }
        }

        return null;
    }

    private static void thisRenderToEntry(Form form, UIContext context, CacheEntry entry, int width, int height, int revision, int angleBucket, int supersample)
    {
        int renderW = Math.max(1, width * supersample);
        int renderH = Math.max(1, height * supersample);

        ensureScratchFramebuffer(renderW, renderH);

        MinecraftClient client = MinecraftClient.getInstance();
        int[] viewport = new int[4];
        boolean scissorWasEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        Matrix4f previousProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        MatrixStack matrices = context.batcher.getContext().getMatrices();

        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);

        context.batcher.flush();

        /* Morph list cells clip with screen-space scissors. Those scissors do not
         * overlap the scratch FBO viewport (0,0,w,h), so leaving them on caches black. */
        if (scissorWasEnabled)
        {
            GlStateManager._disableScissorTest();
        }

        /* Preview fill uses cell-local coords. Match GUI Y-down ortho to the supersampled
         * target so getUIMatrix scale fills the thumbnail instead of a screen speck. */
        RenderSystem.setProjectionMatrix(
            new Matrix4f().ortho(0F, renderW, renderH, 0F, -1000F, 3000F),
            VertexSorter.BY_Z
        );
        RenderSystem.getModelViewStack().pushMatrix();
        RenderSystem.getModelViewStack().identity();
        RenderSystem.applyModelViewMatrix();
        matrices.push();
        matrices.peek().getPositionMatrix().identity();
        matrices.peek().getNormalMatrix().identity();

        scratchFramebuffer.bind();
        scratchFramebuffer.applyClear();
        FormRenderer.setSuppressFormDisplayName(true);

        try
        {
            /* Bake with the keyed yaw — scratch FBO coords would otherwise use
             * screen mouseX and face the wrong way. */
            ModelFormRenderer.setUIAngleOverride(angleFromBucket(angleBucket));
            FormUtilsClient.renderUI(form, context, 0, 0, renderW, renderH);
            context.batcher.flush();
        }
        finally
        {
            ModelFormRenderer.setUIAngleOverride(null);
            FormRenderer.setSuppressFormDisplayName(false);
        }

        entry.ensure(renderW, renderH);
        entry.texture.bind();
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, renderW, renderH);
        entry.texture.unbind();

        scratchFramebuffer.unbind();

        matrices.pop();
        RenderSystem.getModelViewStack().popMatrix();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(previousProjection, VertexSorter.BY_Z);

        if (client != null && client.getFramebuffer() != null)
        {
            /* Do not clear — wiping the main FB mid-UI causes white wash / text corruption. */
            BBSRendering.ensureMainFramebuffer();
            client.getFramebuffer().beginWrite(false);
        }

        GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);

        if (scissorWasEnabled)
        {
            GlStateManager._enableScissorTest();
        }

        BBSRendering.restoreGuiRenderState();

        entry.revision = revision;
        entry.angleBucket = angleBucket;
        entry.width = width;
        entry.height = height;
        entry.formKey = System.identityHashCode(form);
    }

    private static void ensureScratchFramebuffer(int width, int height)
    {
        if (scratchFramebuffer != null && scratchWidth == width && scratchHeight == height)
        {
            return;
        }

        if (scratchFramebuffer == null)
        {
            scratchFramebuffer = BBSModClient.getFramebuffers().getFramebuffer(Link.bbs("form_ui_preview_scratch"), (framebuffer) ->
            {
                scratchTexture = new Texture();

                scratchTexture.setSize(width, height);
                scratchTexture.setFilter(GL11.GL_LINEAR);
                scratchTexture.setWrap(GL13.GL_CLAMP_TO_EDGE);

                scratchDepth = new Renderbuffer();

                scratchDepth.resize(width, height);

                framebuffer.deleteTextures().attach(scratchTexture, GL30.GL_COLOR_ATTACHMENT0);
                framebuffer.attach(scratchDepth);
                framebuffer.unbind();
            });
        }
        else
        {
            scratchFramebuffer.resize(width, height);
        }

        scratchWidth = width;
        scratchHeight = height;
    }

    private static int getFixedAngleBucket()
    {
        return toAngleBucket(-MathUtils.PI + MathUtils.PI / 8F);
    }

    private static int getAngleBucket(UIContext context, int x1, int x2)
    {
        float angle = MathUtils.toRad(context.mouseX - (x1 + x2) / 2) + MathUtils.PI;

        if (BBSSettings.freezeModels.get())
        {
            angle = -MathUtils.PI + MathUtils.PI / 8F;
        }

        return toAngleBucket(angle);
    }

    private static int toAngleBucket(float angle)
    {
        int bucket = (int) (angle * ANGLE_BUCKETS / (MathUtils.PI * 2F));

        return Math.floorMod(bucket, ANGLE_BUCKETS);
    }

    private static float angleFromBucket(int bucket)
    {
        return (bucket + 0.5F) * (MathUtils.PI * 2F) / ANGLE_BUCKETS;
    }

    private static long buildKey(Form form, int width, int height, int angleBucket)
    {
        int formKey = System.identityHashCode(form);

        return ((long) formKey << 32)
            ^ ((long) width << 16)
            ^ (long) height
            ^ ((long) angleBucket << 48);
    }

    private static final class CacheEntry
    {
        public Texture texture;
        public int revision = -1;
        public int angleBucket = Integer.MIN_VALUE;
        public int width;
        public int height;
        public int formKey;

        public void ensure(int w, int h)
        {
            if (this.texture == null)
            {
                this.texture = new Texture();
                this.texture.setFilter(GL11.GL_LINEAR);
                this.texture.setWrap(GL13.GL_CLAMP_TO_EDGE);
            }

            if (this.texture.width != w || this.texture.height != h)
            {
                this.texture.setSize(w, h);
            }
        }

        public void delete()
        {
            if (this.texture != null)
            {
                this.texture.delete();
                this.texture = null;
            }
        }
    }
}
