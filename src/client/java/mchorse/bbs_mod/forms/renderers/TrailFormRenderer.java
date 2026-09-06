package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.cubic.render.vao.ModelVAORenderer;
import mchorse.bbs_mod.forms.ITickable;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.TrailForm;
import mchorse.bbs_mod.forms.forms.utils.EffectTransform;
import mchorse.bbs_mod.forms.forms.utils.EffectTransformMath;
import mchorse.bbs_mod.forms.forms.utils.GlowSettings;
import mchorse.bbs_mod.forms.forms.utils.PaintSettings;
import mchorse.bbs_mod.forms.renderers.utils.FlatGlowOverlayPass;
import mchorse.bbs_mod.forms.renderers.utils.FlatPaintOverlayPass;
import mchorse.bbs_mod.forms.renderers.utils.FormColorEffects;
import mchorse.bbs_mod.forms.renderers.utils.FormTextureBlendRenderer;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.colors.Color;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;

import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class TrailFormRenderer extends FormRenderer<TrailForm> implements ITickable
{
    private final Map<Integer, Map<FormRenderType, ArrayDeque<Trail>>> recordsByInstance = new HashMap<>();
    private final Matrix4f formRootInverse = new Matrix4f();
    private final Vector3f maskLocal = new Vector3f();
    private int tick;

    public TrailFormRenderer(TrailForm form)
    {
        super(form);
    }

    private ArrayDeque<Trail> getTrails(FormRenderType type, int trailInstance)
    {
        Map<FormRenderType, ArrayDeque<Trail>> byType = this.recordsByInstance.computeIfAbsent(trailInstance, (k) -> new HashMap<>());

        return byType.computeIfAbsent(type, (k) -> new ArrayDeque<>());
    }

    @Override
    protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        Texture texture = context.render.getTextures().getTexture(this.form.texture.get());
        float min = Math.min(texture.width, texture.height);
        int ow = (x2 - x1) - 4;
        int oh = (y2 - y1) - 4;
        int w = (int) ((texture.width / min) * ow);
        int h = (int) ((texture.height / min) * ow);
        int x = x1 + (ow - w) / 2 + 2;
        int y = y1 + (oh - h) / 2 + 2;

        context.batcher.fullTexturedBox(texture, x, y, w, h);
    }

    @Override
    public boolean is3D()
    {
        return false;
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        super.render3D(context);

        if (BBSRendering.isIrisShadowPass() || context.type == FormRenderType.ITEM_INVENTORY)
        {
            return;
        }

        if (context.modelRenderer || context.ui)
        {
            MatrixStack stack = context.stack;
            float scale = BBSSettings.axesScale.get();
            float axisOffset = 0.01F * scale;
            float outlineSize = 1.01F;
            float outlineOffset = 0.02F * scale;

            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder builder = tessellator.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

            Draw.fillBox(builder, stack, -outlineOffset, -outlineSize, -outlineOffset, outlineOffset, outlineSize, outlineOffset, 0, 0, 0);
            Draw.fillBox(builder, stack, -axisOffset, -1F, -axisOffset, axisOffset, 1F, axisOffset, 0, 1, 0);

            BBSRendering.bindProgram(BBSRendering.getGuiProgram());
            BBSRendering.disableDepthTest();
            BufferRenderer.drawWithGlobalProgram(builder.end());
            BBSRendering.enableDepthTest();

            return;
        }

        if (!BBSRendering.isRenderingWorld())
        {
            return;
        }

        MatrixStack stack = context.stack;
        Camera camera = context.camera;
        double baseX = camera.position.x;
        double baseY = camera.position.y;
        double baseZ = camera.position.z;
        float current = (float) this.tick + context.transition;
        ArrayDeque<Trail> trails = this.getTrails(context.type, context.trailInstance);

        if (!this.form.paused.get())
        {
            Matrix4f modelPosMatrix = new Matrix4f(stack.peek().getPositionMatrix());
            Vector4f topVec = new Vector4f(0F, 1F, 0F, 1F);
            Vector4f bottomVec = new Vector4f(0F, -1F, 0F, 1F);

            modelPosMatrix.transform(topVec);
            modelPosMatrix.transform(bottomVec);

            Trail record = new Trail();
            record.tick = current;
            record.top = new Vector3d(topVec.x + baseX, topVec.y + baseY, topVec.z + baseZ);
            record.bottom = new Vector3d(bottomVec.x + baseX, bottomVec.y + baseY, bottomVec.z + baseZ);
            record.stop = new Vector3f((float) (topVec.x - bottomVec.x), (float) (topVec.y - bottomVec.y), (float) (topVec.z - bottomVec.z)).lengthSquared() < 1.0E-4D;

            /* Same frame may re-render (illusion streaks); keep one sample per tick. */
            Trail last = trails.peekLast();

            if (last != null && Math.abs(last.tick - current) < 0.0001F)
            {
                trails.removeLast();
            }

            trails.addLast(record);
        }

        boolean loop = this.form.loop.get();
        float length = this.form.length.get();
        float end = current - length;
        Iterator<Trail> it = trails.iterator();
        boolean hasSomethingToRender = false;
        boolean lastStop = true;

        while (it.hasNext())
        {
            Trail trail = it.next();

            if (trail.tick < end)
            {
                it.remove();
            }
            else
            {
                hasSomethingToRender |= !trail.stop && !lastStop;
                lastStop = trail.stop;
            }
        }

        if (!hasSomethingToRender || trails.size() <= 1 || !(length > 0.001D))
        {
            return;
        }

        Link defaultTexture = this.form.texture.get();
        Color storedFormColor = this.form.color.get();
        Color blendedTint = new Color().set(context.color, true);
        Color unblendedTint = new Color().set(context.color, true);

        /* When color Transform is active, mask tint in form-local space per vertex. */
        blendedTint.mul(storedFormColor.copyBakingColorGrade());
        FormColorEffects.applyShadowPassColorFix(blendedTint, storedFormColor, this.form.paintSettings.get(), this.form.paintColor.get(), context.isShadowPass || BBSRendering.isIrisShadowPass());
        FormColorEffects.applyShadowPassColorFix(unblendedTint, storedFormColor, this.form.paintSettings.get(), this.form.paintColor.get(), context.isShadowPass || BBSRendering.isIrisShadowPass());

        if (blendedTint.a <= 0.001F && !context.isShadowPass && !BBSRendering.isIrisShadowPass())
        {
            return;
        }

        this.formRootInverse.set(stack.peek().getPositionMatrix()).invert();

        FormTextureBlendRenderer.draw(this.form.textureBlend, defaultTexture, (link, alphaFactor) ->
        {
            this.renderTrailPass(stack, trails, loop, length, current, baseX, baseY, baseZ, link, unblendedTint, blendedTint, alphaFactor);
        });
    }

    private void renderTrailPass(MatrixStack stack, ArrayDeque<Trail> trails, boolean loop, float length, float current, double baseX, double baseY, double baseZ, Link textureLink, Color unblendedTint, Color blendedTint, float alphaFactor)
    {
        if (textureLink == null)
        {
            return;
        }

        BBSModClient.getTextures().bindTexture(textureLink);
        stack.push();

        PaintSettings paintSettings = this.form.paintSettings.get();
        Color legacyPaint = this.form.paintColor.get();
        float paintStrength = paintSettings.resolveIntensity(legacyPaint);
        boolean positivePaint = FormColorEffects.hasPositivePaint(paintSettings, legacyPaint);
        Color resolvedPaint = positivePaint ? FormColorEffects.resolvePaintColor(paintSettings, legacyPaint) : null;
        EffectTransform colorTransform = this.form.color.get().transform;
        EffectTransform paintTransform = paintSettings.transform;

        GlowSettings glowSettings = this.form.glowSettings.get();
        Color legacyGlow = this.form.glowingColor.get();
        float glowIntensity = glowSettings.resolveIntensity(legacyGlow);

        Color unblended = unblendedTint.copy();
        Color blended = blendedTint.copy();

        unblended.a *= alphaFactor;
        blended.a *= alphaFactor;

        if (paintStrength < 0F)
        {
            FormColorEffects.applyPaintBlend(unblended, paintSettings, legacyPaint);
            FormColorEffects.applyPaintBlend(blended, paintSettings, legacyPaint);
        }

        if (glowIntensity < 0F)
        {
            FormColorEffects.blendFormGlowBrighten(unblended, glowSettings, legacyGlow);
            FormColorEffects.blendFormGlowBrighten(blended, glowSettings, legacyGlow);
        }

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder builder = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        Matrix4f identityMatrix = new Matrix4f();

        this.buildTrailQuads(builder, identityMatrix, trails, loop, length, current, baseX, baseY, baseZ, unblended, blended, colorTransform);

        BBSRendering.bindProgram(BBSRendering.getPositionTexColorProgram());
        BBSRendering.enableBlend();
        BBSRendering.defaultBlendFunc();
        BufferRenderer.drawWithGlobalProgram(builder.end());

        if (positivePaint)
        {
            this.submitDeferredTrailPaintOverlay(trails, loop, length, current, baseX, baseY, baseZ, textureLink, resolvedPaint, blended.a, paintTransform);
        }

        if (glowIntensity > 0F)
        {
            this.renderGlowOverlay(tessellator, identityMatrix, trails, loop, length, current, baseX, baseY, baseZ, glowSettings, legacyGlow, blended.a, glowIntensity, this.resolveGlowEffectTransform(glowSettings, legacyGlow));
        }

        BBSRendering.enableDepthTest();
        stack.pop();
    }

    private void submitDeferredTrailPaintOverlay(ArrayDeque<Trail> trails, boolean loop, float length, float current, double baseX, double baseY, double baseZ, Link textureLink, Color resolvedPaint, float alpha, EffectTransform paintTransform)
    {
        ArrayDeque<Trail> trailSnapshot = this.copyTrails(trails);
        Color paintOverlay = new Color(resolvedPaint.r, resolvedPaint.g, resolvedPaint.b, resolvedPaint.a);
        Matrix4f paintMatrix = new Matrix4f(RenderSystem.getModelViewMatrix());
        EffectTransform paintTransformSnapshot = paintTransform == null ? null : paintTransform.copy();
        Matrix4f formRootInverseSnapshot = new Matrix4f(this.formRootInverse);

        paintOverlay.a *= alpha;

        ModelVAORenderer.submitPaintOverlay(false, () ->
        {
            this.formRootInverse.set(formRootInverseSnapshot);
            BBSModClient.getTextures().bindTexture(textureLink);
            this.renderPaintOverlayPass(trailSnapshot, loop, length, current, baseX, baseY, baseZ, paintOverlay, paintMatrix, paintTransformSnapshot);
        });
    }

    private ArrayDeque<Trail> copyTrails(ArrayDeque<Trail> trails)
    {
        ArrayDeque<Trail> copy = new ArrayDeque<>();

        for (Trail trail : trails)
        {
            Trail snapshot = new Trail();

            snapshot.tick = trail.tick;
            snapshot.stop = trail.stop;
            snapshot.top = new Vector3d(trail.top);
            snapshot.bottom = new Vector3d(trail.bottom);
            copy.addLast(snapshot);
        }

        return copy;
    }

    private void renderPaintOverlayPass(ArrayDeque<Trail> trails, boolean loop, float length, float current, double baseX, double baseY, double baseZ, Color paintOverlay, Matrix4f vertexMatrix, EffectTransform paintTransform)
    {
        Tessellator tessellator = Tessellator.getInstance();

        FlatPaintOverlayPass.render(() ->
        {
            BufferBuilder paintBuilder = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
            int paintLight = LightmapTextureManager.MAX_LIGHT_COORDINATE;
            int overlay = OverlayTexture.DEFAULT_UV;

            this.buildTrailPaintQuads(paintBuilder, vertexMatrix, trails, loop, length, current, baseX, baseY, baseZ, paintOverlay, overlay, paintLight, paintTransform);
            BufferRenderer.drawWithGlobalProgram(paintBuilder.end());
        });
    }

    private void renderGlowOverlay(Tessellator tessellator, Matrix4f matrix, ArrayDeque<Trail> trails, boolean loop, float length, float current, double baseX, double baseY, double baseZ, GlowSettings glowSettings, Color legacyGlow, float alpha, float glowIntensity, EffectTransform glowTransform)
    {
        FlatGlowOverlayPass.render(glowSettings, legacyGlow, alpha, glowIntensity, (glowColor) ->
        {
            /* Outside the mask: fully transparent; inside: full glow. Same soft volume as Color/Paint. */
            Color glowOutside = glowColor.copy();

            glowOutside.a = 0F;

            BufferBuilder glowBuilder = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

            BBSRendering.bindProgram(BBSRendering.getPositionTexColorProgram());
            this.buildTrailQuads(glowBuilder, matrix, trails, loop, length, current, baseX, baseY, baseZ, glowColor, glowColor, glowTransform);
            BufferRenderer.drawWithGlobalProgram(glowBuilder.end());
        });
    }

    /**
     * Prefer {@link GlowSettings#transform}; fall back to legacy {@code glowingColor.transform}.
     */
    private EffectTransform resolveGlowEffectTransform(GlowSettings glow, Color legacyGlow)
    {
        if (glow != null && glow.transform != null && glow.transform.isActive())
        {
            return glow.transform;
        }

        if (legacyGlow != null && legacyGlow.hasActiveTransform())
        {
            return legacyGlow.transform;
        }

        if (glow != null && glow.transform != null)
        {
            return glow.transform;
        }

        if (legacyGlow != null && legacyGlow.transform != null)
        {
            return legacyGlow.transform;
        }

        return new EffectTransform();
    }

    private void buildTrailQuads(BufferBuilder builder, Matrix4f matrix, ArrayDeque<Trail> trails, boolean loop, float length, float current, double baseX, double baseY, double baseZ, Color unblended, Color blended, EffectTransform colorTransform)
    {
        Trail lastTrail = null;

        for (Trail trail : trails)
        {
            if (lastTrail != null && !lastTrail.stop && !trail.stop)
            {
                float u1 = loop ? trail.tick / length : (current - trail.tick) / length;
                float u2 = loop ? lastTrail.tick / length : (current - lastTrail.tick) / length;

                this.addTrailSegment(builder, matrix, trail, lastTrail, baseX, baseY, baseZ, u1, u2, unblended, blended, colorTransform);
            }

            lastTrail = trail;
        }
    }

    private void buildTrailPaintQuads(BufferBuilder builder, Matrix4f matrix, ArrayDeque<Trail> trails, boolean loop, float length, float current, double baseX, double baseY, double baseZ, Color color, int overlay, int light, EffectTransform paintTransform)
    {
        Trail lastTrail = null;

        for (Trail trail : trails)
        {
            if (lastTrail != null && !lastTrail.stop && !trail.stop)
            {
                float u1 = loop ? trail.tick / length : (current - trail.tick) / length;
                float u2 = loop ? lastTrail.tick / length : (current - lastTrail.tick) / length;

                this.addTrailPaintSegment(builder, matrix, trail, lastTrail, baseX, baseY, baseZ, u1, u2, color, overlay, light, paintTransform);
            }

            lastTrail = trail;
        }
    }

    private void addTrailSegment(BufferBuilder builder, Matrix4f matrix, Trail trail, Trail lastTrail, double baseX, double baseY, double baseZ, float u1, float u2, Color unblended, Color blended, EffectTransform colorTransform)
    {
        float x1 = (float) (trail.top.x - baseX);
        float x2 = (float) (trail.bottom.x - baseX);
        float x3 = (float) (lastTrail.bottom.x - baseX);
        float x4 = (float) (lastTrail.top.x - baseX);

        float y1 = (float) (trail.top.y - baseY);
        float y2 = (float) (trail.bottom.y - baseY);
        float y3 = (float) (lastTrail.bottom.y - baseY);
        float y4 = (float) (lastTrail.top.y - baseY);

        float z1 = (float) (trail.top.z - baseZ);
        float z2 = (float) (trail.bottom.z - baseZ);
        float z3 = (float) (lastTrail.bottom.z - baseZ);
        float z4 = (float) (lastTrail.top.z - baseZ);

        this.fillTrailVertex(builder, matrix, x1, y1, z1, u1, 0F, unblended, blended, colorTransform);
        this.fillTrailVertex(builder, matrix, x2, y2, z2, u1, 1F, unblended, blended, colorTransform);
        this.fillTrailVertex(builder, matrix, x3, y3, z3, u2, 1F, unblended, blended, colorTransform);
        this.fillTrailVertex(builder, matrix, x4, y4, z4, u2, 0F, unblended, blended, colorTransform);

        this.fillTrailVertex(builder, matrix, x4, y4, z4, u2, 0F, unblended, blended, colorTransform);
        this.fillTrailVertex(builder, matrix, x3, y3, z3, u2, 1F, unblended, blended, colorTransform);
        this.fillTrailVertex(builder, matrix, x2, y2, z2, u1, 1F, unblended, blended, colorTransform);
        this.fillTrailVertex(builder, matrix, x1, y1, z1, u1, 0F, unblended, blended, colorTransform);
    }

    private void addTrailPaintSegment(BufferBuilder builder, Matrix4f matrix, Trail trail, Trail lastTrail, double baseX, double baseY, double baseZ, float u1, float u2, Color color, int overlay, int light, EffectTransform paintTransform)
    {
        float x1 = (float) (trail.top.x - baseX);
        float x2 = (float) (trail.bottom.x - baseX);
        float x3 = (float) (lastTrail.bottom.x - baseX);
        float x4 = (float) (lastTrail.top.x - baseX);

        float y1 = (float) (trail.top.y - baseY);
        float y2 = (float) (trail.bottom.y - baseY);
        float y3 = (float) (lastTrail.bottom.y - baseY);
        float y4 = (float) (lastTrail.top.y - baseY);

        float z1 = (float) (trail.top.z - baseZ);
        float z2 = (float) (trail.bottom.z - baseZ);
        float z3 = (float) (lastTrail.bottom.z - baseZ);
        float z4 = (float) (lastTrail.top.z - baseZ);

        this.fillPaintVertex(builder, matrix, x1, y1, z1, u1, 0F, color, overlay, light, paintTransform);
        this.fillPaintVertex(builder, matrix, x2, y2, z2, u1, 1F, color, overlay, light, paintTransform);
        this.fillPaintVertex(builder, matrix, x3, y3, z3, u2, 1F, color, overlay, light, paintTransform);
        this.fillPaintVertex(builder, matrix, x4, y4, z4, u2, 0F, color, overlay, light, paintTransform);

        this.fillPaintVertex(builder, matrix, x4, y4, z4, u2, 0F, color, overlay, light, paintTransform);
        this.fillPaintVertex(builder, matrix, x3, y3, z3, u2, 1F, color, overlay, light, paintTransform);
        this.fillPaintVertex(builder, matrix, x2, y2, z2, u1, 1F, color, overlay, light, paintTransform);
        this.fillPaintVertex(builder, matrix, x1, y1, z1, u1, 0F, color, overlay, light, paintTransform);
    }

    private void fillTrailVertex(BufferBuilder builder, Matrix4f matrix, float x, float y, float z, float u, float v, Color unblended, Color blended, EffectTransform colorTransform)
    {
        float mask = this.sampleMask(x, y, z, colorTransform);
        float r = unblended.r + (blended.r - unblended.r) * mask;
        float g = unblended.g + (blended.g - unblended.g) * mask;
        float b = unblended.b + (blended.b - unblended.b) * mask;
        float a = unblended.a + (blended.a - unblended.a) * mask;

        builder.vertex(matrix, x, y, z).texture(u, v).color(r, g, b, a);
    }

    private void fillPaintVertex(BufferBuilder builder, Matrix4f matrix, float x, float y, float z, float u, float v, Color color, int overlay, int light, EffectTransform paintTransform)
    {
        float mask = this.sampleMask(x, y, z, paintTransform);

        builder.vertex(matrix, x, y, z).color(color.r, color.g, color.b, color.a * mask).texture(u, v).overlay(overlay).light(light).normal(0F, 0F, 1F);
    }

    /**
     * Soft EffectTransform mask in current form-local space (emitter root).
     */
    private float sampleMask(float x, float y, float z, EffectTransform transform)
    {
        if (!EffectTransformMath.isTransformActive(transform))
        {
            return 1F;
        }

        this.maskLocal.set(x, y, z);
        this.formRootInverse.transformPosition(this.maskLocal);

        return EffectTransformMath.maskBillboard(this.maskLocal.x, this.maskLocal.y, this.maskLocal.z, transform);
    }

    @Override
    public void tick(IEntity entity)
    {
        this.tick += 1;

        float end = this.tick - Math.max(this.form.length.get(), 0F) - 1F;
        Iterator<Map.Entry<Integer, Map<FormRenderType, ArrayDeque<Trail>>>> instances = this.recordsByInstance.entrySet().iterator();

        while (instances.hasNext())
        {
            Map.Entry<Integer, Map<FormRenderType, ArrayDeque<Trail>>> instanceEntry = instances.next();
            Map<FormRenderType, ArrayDeque<Trail>> byType = instanceEntry.getValue();
            Iterator<Map.Entry<FormRenderType, ArrayDeque<Trail>>> types = byType.entrySet().iterator();

            while (types.hasNext())
            {
                Map.Entry<FormRenderType, ArrayDeque<Trail>> typeEntry = types.next();
                ArrayDeque<Trail> trails = typeEntry.getValue();

                while (!trails.isEmpty() && trails.peekFirst().tick < end)
                {
                    trails.removeFirst();
                }

                if (trails.isEmpty() && instanceEntry.getKey() != 0)
                {
                    types.remove();
                }
            }

            if (instanceEntry.getKey() != 0 && byType.isEmpty())
            {
                instances.remove();
            }
        }
    }

    public static class Trail
    {
        public Vector3d top;
        public Vector3d bottom;
        public float tick;
        public boolean stop;
    }
}
