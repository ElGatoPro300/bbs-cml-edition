package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.cubic.render.vao.ModelVAORenderer;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.LabelForm;
import mchorse.bbs_mod.forms.forms.utils.EffectTransform;
import mchorse.bbs_mod.forms.forms.utils.EffectTransformMath;
import mchorse.bbs_mod.forms.forms.utils.GlowSettings;
import mchorse.bbs_mod.forms.forms.utils.PaintSettings;
import mchorse.bbs_mod.forms.renderers.utils.BlockEffectOverlayUniforms;
import mchorse.bbs_mod.forms.renderers.utils.FlatColorTintOverlayPass;
import mchorse.bbs_mod.forms.renderers.utils.FlatGlowOverlayPass;
import mchorse.bbs_mod.forms.renderers.utils.FlatPaintOverlayPass;
import mchorse.bbs_mod.forms.renderers.utils.FormColorEffects;
import mchorse.bbs_mod.forms.renderers.utils.LabelTextTintQuadCapture;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.utils.FontUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.TextureFont;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.iris.ShaderOpacityPatch;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import org.lwjgl.opengl.GL11;

import java.awt.Font;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class LabelFormRenderer extends FormRenderer<LabelForm>
{
    /**
     * Minecraft's {@link TextRenderer} treats {@code (color & 0xFC000000) == 0} as fully
     * opaque, so alpha bytes 0–3 become 255. Keep a minimum of 4 when opacity is intended.
     */
    private static final int MIN_TEXT_ALPHA_BYTE = 4;
    /* Positive offset pushes decorations away from the camera (stable on front, back, and grazing). */
    private static final float LABEL_DECORATION_POLYGON_FACTOR = 1F;
    private static final float LABEL_DECORATION_POLYGON_UNITS = 8F;
    /* Extra gap between wrapped lines so descenders do not touch the next line. */
    private int resolveWrapLineGap()
    {
        return Math.max(0, this.form.wrapLineGap.get());
    }

    /** Units-only bias — factor 0 avoids Iris wall punch-through on grazing label planes. */
    private static final float LABEL_COLOR_TINT_OFFSET_FACTOR = FlatPaintOverlayPass.POLYGON_OFFSET_FACTOR;
    private static final float LABEL_COLOR_TINT_OFFSET_UNITS = -96F;
    private static final float LABEL_PAINT_OFFSET_FACTOR = FlatPaintOverlayPass.POLYGON_OFFSET_FACTOR;
    private static final float LABEL_PAINT_OFFSET_UNITS = -128F;
    private static final float LABEL_DEFERRED_PAINT_OFFSET_UNITS = -128F;
    /* Color/paint overlays: camera-facing local Z (same rule as BillboardFormRenderer). */
    private static final float FACE_Z_BIAS = 0.0005F;
    private static final float OVERLAY_FACE_EXTRA = 0.0015F;
    /* Depth precision falls off with distance — scale overlay bias (FlatPaintOverlayPass). */
    private static final float LABEL_OVERLAY_DISTANCE_SCALE_START = 4F;
    private static final float LABEL_OVERLAY_DISTANCE_SCALE_LINEAR = 0.4F;
    private static final float LABEL_OVERLAY_DISTANCE_SCALE_QUADRATIC = 0.0025F;
    private static final float LABEL_OVERLAY_DISTANCE_SCALE_CAP = 32F;
    private static final float LABEL_OVERLAY_SEPARATION_LINEAR = 0.0012F;
    private static final float LABEL_OVERLAY_SEPARATION_QUADRATIC = 0.00001F;
    private static final float LABEL_OVERLAY_SEPARATION_MAX = 2F;
    private static final float LABEL_BASE_FILL_PULLBACK = 0.4F;
    private static final float LABEL_OVERLAY_OFFSET_UNITS_CAP = -4096F;
    private static final Vector3f OVERLAY_TO_CAMERA = new Vector3f();
    private static final Vector3f OVERLAY_LOCAL_Z = new Vector3f();

    private float nametagAlpha = 1F;
    private int lastBoundTextTexture;
    private final Vector3f maskHalfExtents = new Vector3f();
    private final LabelTextTintQuadCapture tintCapture = new LabelTextTintQuadCapture();
    private final Matrix4f identityMatrix = new Matrix4f();

    public static void fillQuad(BufferBuilder builder, MatrixStack stack, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, float r, float g, float b, float a)
    {
        Matrix4f matrix4f = stack.peek().getPositionMatrix();

        /* 1 - BR, 2 - BL, 3 - TL, 4 - TR */
        builder.vertex(matrix4f, x1, y1, z1).color(r, g, b, a);
        builder.vertex(matrix4f, x2, y2, z2).color(r, g, b, a);
        builder.vertex(matrix4f, x3, y3, z3).color(r, g, b, a);
        builder.vertex(matrix4f, x1, y1, z1).color(r, g, b, a);
        builder.vertex(matrix4f, x3, y3, z3).color(r, g, b, a);
        builder.vertex(matrix4f, x4, y4, z4).color(r, g, b, a);
    }

    public LabelFormRenderer(LabelForm form)
    {
        super(form);
    }

    @Override
    public void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        GlowSettings glowSettings = this.form.glowSettings.get();
        Color legacyGlow = this.form.glowingColor.get();
        float glowIntensity = glowSettings.resolveIntensity(legacyGlow);
        Color color = this.form.color.get().copy();

        if (glowIntensity < 0F)
        {
            FormColorEffects.blendFormGlowBrighten(color, glowSettings, legacyGlow);
        }

        /* Minecraft TextRenderer treats ARGB alpha 0 as fully opaque. */
        if (isFullyTransparent(color))
        {
            return;
        }

        int argb = toSafeTextArgb(color);
        String text = StringUtils.processColoredText(this.form.text.get());
        List<String> wrap = context.batcher.getFont().wrap(text, x2 - x1 - 4);

        int th = context.batcher.getFont().getHeight();
        int lineHeight = th + 4;
        int h = th + (wrap.size() - 1) * lineHeight;
        int y = (y2 + y1) / 2 - h / 2;

        for (String s : wrap)
        {
            context.batcher.textShadow(s, x1 + 2, y, argb);

            y += lineHeight;
        }

        if (glowIntensity > 0F)
        {
            Color glowColor = FormColorEffects.resolveGlowOverlayEmissionColor(glowSettings, legacyGlow, 1F, glowIntensity);
            float shaderScale = FormColorEffects.resolveGlowOverlayShaderScale(glowIntensity);

            glowColor.r *= color.r;
            glowColor.g *= color.g;
            glowColor.b *= color.b;

            int glowArgb = toSafeTextArgb(glowColor);
            int glowY = (y2 + y1) / 2 - h / 2;

            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            RenderSystem.setShaderColor(shaderScale, shaderScale, shaderScale, 1F);

            for (String s : wrap)
            {
                context.batcher.text(s, x1 + 2, glowY, glowArgb);

                glowY += lineHeight;
            }

            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
            RenderSystem.defaultBlendFunc();
        }
    }

    @Override
    public void render3D(FormRenderingContext context)
    {
        Color contextColor = new Color().set(context.color, true);
        Color formColor = this.form.color.get();
        float alpha = contextColor.a * (formColor != null ? formColor.a : 1F);

        if (alpha <= 0.001F)
        {
            return;
        }

        context.stack.push();

        if (this.form.billboard.get())
        {
            Matrix4f modelMatrix = context.stack.peek().getPositionMatrix();
            Vector3f scale = new Vector3f();

            modelMatrix.getScale(scale);

            modelMatrix.m00(1).m01(0).m02(0);
            modelMatrix.m10(0).m11(1).m12(0);
            modelMatrix.m20(0).m21(0).m22(1);

            if (!context.modelRenderer && !context.isPicking())
            {
                modelMatrix.mul(context.camera.view);
            }

            modelMatrix.scale(scale);

            context.stack.peek().getNormalMatrix().identity();
            context.stack.peek().getNormalMatrix().scale(
                MatrixStackUtils.safeNormalScaleReciprocal(scale.x),
                MatrixStackUtils.safeNormalScaleReciprocal(scale.y),
                MatrixStackUtils.safeNormalScaleReciprocal(scale.z)
            );
        }

        TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
        float fontSize = this.form.fontSize.get();
        float scale = (1F / 16F) * (fontSize <= 0 ? 1F : fontSize);
        int light = context.light;

        this.nametagAlpha = 1F;

        boolean shadowPass = this.isShadowPass(context);

        if (shadowPass)
        {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
        }

        if (this.form.nametag.get() && context.entity != null && context.entity.isSneaking())
        {
            context.stack.translate(0F, -0.5F, 0F);
            this.nametagAlpha = 0.125F;
        }

        MatrixStackUtils.scaleStack(context.stack, scale, -scale, scale);

        RenderSystem.disableCull();

        if (context.isPicking())
        {
            CustomVertexConsumerProvider.hijackVertexFormat((layer) ->
            {
                /* startDrawing may re-enable culling; keep both sides of the label visible. */
                RenderSystem.disableCull();
                this.setupTarget(context, BBSShaders.getPickerModelsProgram());
                RenderSystem.setShader(BBSShaders.getPickerModelsProgram());
            });

            light = 0;
        }
        else
        {
            CustomVertexConsumerProvider.hijackVertexFormat((layer) ->
            {
                RenderSystem.disableCull();
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
            });
        }

        if (this.form.max.get() <= 0)
        {
            this.renderString(context, consumers, renderer, light);
        }
        else
        {
            this.renderLimitedString(context, consumers, renderer, light);
        }

        /* Glow overlay clears the hijack; re-apply disableCull for any leftover shared-buffer
         * flush so the last label keeps both faces when WorldRenderer draws later. */
        CustomVertexConsumerProvider.hijackVertexFormat((layer) -> RenderSystem.disableCull());
        this.flushLabelConsumers(consumers);

        CustomVertexConsumerProvider.clearRunnables();
        RenderSystem.defaultBlendFunc();

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();

        context.stack.pop();
    }

    /**
     * Text {@link RenderLayer}s restore GL culling in
     * {@code startDrawing}. Labels use a negative Y scale (flipped winding), so both faces
     * must stay unculled at flush time or the back of the last drawn label disappears.
     */
    private void flushLabelConsumers(CustomVertexConsumerProvider consumers)
    {
        RenderSystem.disableCull();
        consumers.draw();
    }

    private Consumer<RenderLayer> createLabelBaseHijack(FormRenderingContext context)
    {
        if (context.isPicking())
        {
            return (layer) ->
            {
                RenderSystem.disableCull();
                this.setupTarget(context, BBSShaders.getPickerModelsProgram());
                RenderSystem.setShader(BBSShaders.getPickerModelsProgram());
            };
        }

        return (layer) ->
        {
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            if (this.isShadowPass(context))
            {
                ShaderOpacityPatch.uploadShadowFormUniform();
            }
        };
    }

    private boolean isShadowPass(FormRenderingContext context)
    {
        return context.isShadowPass || BBSRendering.isIrisShadowPass();
    }

    /**
     * Outline/shadow: depth write on + polygon offset away from the camera (not local ±Z).
     * Local/view normal sign flips at grazing angles and put the outline in front of the fill.
     */
    private void beginLabelDecorationDepthPass(Consumer<RenderLayer> baseHijack)
    {
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(LABEL_DECORATION_POLYGON_FACTOR, LABEL_DECORATION_POLYGON_UNITS);
        CustomVertexConsumerProvider.hijackVertexFormat((layer) ->
        {
            baseHijack.accept(layer);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glPolygonOffset(LABEL_DECORATION_POLYGON_FACTOR, LABEL_DECORATION_POLYGON_UNITS);
        });
    }

    /**
     * Fill glyphs at true depth (no polygon offset) so they composite over decorations.
     */
    private void beginLabelFillDepthPass(Consumer<RenderLayer> baseHijack)
    {
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        GL11.glPolygonOffset(0F, 0F);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        CustomVertexConsumerProvider.hijackVertexFormat((layer) ->
        {
            baseHijack.accept(layer);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            GL11.glPolygonOffset(0F, 0F);
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        });
    }

    private void restoreLabelPolygonOffset(boolean wasEnabled)
    {
        GL11.glPolygonOffset(0F, 0F);

        if (wasEnabled)
        {
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        }
        else
        {
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        }
    }

    private String applyStyles(String content)
    {
        StringBuilder prefix = new StringBuilder();

        if (this.form.fontWeight.get() >= 700)
        {
            prefix.append("\u00A7l");
        }

        if (this.form.fontStyle.get() >= 1)
        {
            prefix.append("\u00A7o");
        }

        if (this.form.underline.get())
        {
            prefix.append("\u00A7n");
        }

        if (this.form.strikethrough.get())
        {
            prefix.append("\u00A7m");
        }

        return prefix.toString() + content;
    }

    private int resolveLabelLight(int light)
    {
        if (this.form.noshadingOpacity.get())
        {
            return LightmapTextureManager.MAX_LIGHT_COORDINATE;
        }

        return light;
    }

    private void renderTextShadow(FormRenderingContext context, CustomVertexConsumerProvider consumers, TextRenderer renderer, TextureFont customFont, String content, float x, float y, float letterSpacing, int light, Color shadowColor)
    {
        if (isFullyTransparent(shadowColor))
        {
            return;
        }

        context.stack.push();

        float sx = this.form.shadowX.get();
        float sy = this.form.shadowY.get();
        float blur = this.form.shadowBlur.get();

        if (blur > 0)
        {
            int originalColor = toSafeTextArgb(shadowColor);
            int alpha = (originalColor >> 24) & 0xFF;
            int rgb = originalColor & 0x00FFFFFF;
            int blurAlpha = Math.max(1, alpha / 4);
            int blurColor = (blurAlpha << 24) | rgb;

            this.drawSimpleText(context, consumers, renderer, customFont, content, x + sx - blur, y + sy, letterSpacing, light, blurColor);
            this.drawSimpleText(context, consumers, renderer, customFont, content, x + sx + blur, y + sy, letterSpacing, light, blurColor);
            this.drawSimpleText(context, consumers, renderer, customFont, content, x + sx, y + sy - blur, letterSpacing, light, blurColor);
            this.drawSimpleText(context, consumers, renderer, customFont, content, x + sx, y + sy + blur, letterSpacing, light, blurColor);
        }
        else
        {
            this.drawSimpleText(context, consumers, renderer, customFont, content, x + sx, y + sy, letterSpacing, light, toSafeTextArgb(shadowColor));
        }

        context.stack.pop();
    }

    private void drawSimpleText(FormRenderingContext context, CustomVertexConsumerProvider consumers, TextRenderer renderer, TextureFont customFont, String content, float x, float y, float letterSpacing, int light, int color)
    {
        int resolvedLight = this.resolveLabelLight(light);

        if (customFont != null)
        {
            customFont.draw(content, x, y, color, color, letterSpacing, 0F, context.stack.peek().getPositionMatrix(), consumers, resolvedLight);
        }
        else
        {
            renderer.draw(
                content,
                x,
                y,
                color, false,
                context.stack.peek().getPositionMatrix(),
                consumers,
                TextRenderer.TextLayerType.NORMAL,
                0,
                resolvedLight
            );
        }
    }

    private void submitOrRenderLabelGlowOverlay(FormRenderingContext context, float x, float y, float w, float h, GlowSettings glowSettings, Color legacyGlow, float glowIntensity, float alpha, EffectTransform glowTransform, List<LabelTextTintQuadCapture.GlyphQuad> quads)
    {
        if (glowIntensity <= 0F || glowSettings.resolvePaintOnly() || quads == null || quads.isEmpty())
        {
            return;
        }

        LabelOverlayLayout layout = this.resolveLabelOverlayLayout(x, y, w, h, quads);
        GlowSettings glowSnapshot = glowSettings.copy();
        Color legacyGlowSnapshot = legacyGlow == null ? null : legacyGlow.copy();
        EffectTransform transformSnapshot = glowTransform == null ? null : glowTransform.copy();
        List<LabelTextTintQuadCapture.GlyphQuad> quadSnapshot = new ArrayList<>(quads);
        boolean defer = BBSRendering.isIrisWorldModelPass() && !context.modelRenderer && !context.isPicking();
        Matrix4f rootMatrix = this.captureLabelOverlayRootMatrix(context, layout.centerX, layout.centerY);

        if (defer)
        {
            Matrix4f positionMatrix = ModelVAORenderer.capturePaintOverlayRootMatrix(rootMatrix);

            ModelVAORenderer.submitPaintOverlay(false, () ->
            {
                MatrixStack overlayStack = new MatrixStack();

                overlayStack.peek().getPositionMatrix().set(positionMatrix);
                this.renderLabelGlowOverlay(overlayStack, layout.centerX, layout.centerY, layout.halfX, layout.halfY, glowSnapshot, legacyGlowSnapshot, glowIntensity, alpha, transformSnapshot, quadSnapshot, FlatPaintOverlayPass.DEFERRED_BILLBOARD_FACTOR, LabelFormRenderer.LABEL_DEFERRED_PAINT_OFFSET_UNITS);
            });
        }
        else
        {
            MatrixStack overlayStack = new MatrixStack();

            overlayStack.peek().getPositionMatrix().set(rootMatrix);
            this.renderLabelGlowOverlay(overlayStack, layout.centerX, layout.centerY, layout.halfX, layout.halfY, glowSnapshot, legacyGlowSnapshot, glowIntensity, alpha, transformSnapshot, quadSnapshot, LabelFormRenderer.LABEL_PAINT_OFFSET_FACTOR, LabelFormRenderer.LABEL_PAINT_OFFSET_UNITS);
        }
    }

    private void renderLabelGlowOverlay(MatrixStack stack, float centerX, float centerY, float halfX, float halfY, GlowSettings glowSettings, Color legacyGlow, float glowIntensity, float alpha, EffectTransform glowTransform, List<LabelTextTintQuadCapture.GlyphQuad> quads, float polygonOffsetFactor, float polygonOffsetUnits)
    {
        Matrix4f glowMatrix = stack.peek().getPositionMatrix();
        MatrixStack.Entry entry = stack.peek();
        Matrix4f formRootInverse = new Matrix4f(glowMatrix).invert();

        Color glowColor = FormColorEffects.resolveGlowOverlayEmissionColor(glowSettings, legacyGlow, alpha, glowIntensity);
        float shaderScale = FormColorEffects.resolveGlowOverlayShaderScale(glowIntensity);

        EffectTransformMath.resolveBillboardMaskHalfExtents(glowTransform, this.maskHalfExtents, halfX, halfY);

        Map<RenderLayer, List<LabelTextTintQuadCapture.GlyphQuad>> byLayer = new LinkedHashMap<>();

        for (LabelTextTintQuadCapture.GlyphQuad quad : quads)
        {
            byLayer.computeIfAbsent(quad.layer, (layer) -> new ArrayList<>()).add(quad);
        }

        float offsetUnits = this.resolveLabelOverlayOffsetUnits(glowMatrix, polygonOffsetUnits);

        FlatGlowOverlayPass.renderMasked(polygonOffsetFactor, offsetUnits, formRootInverse, glowTransform, false, this.maskHalfExtents, shaderScale, () ->
        {
            int glowLight = LightmapTextureManager.MAX_LIGHT_COORDINATE;
            int overlay = OverlayTexture.DEFAULT_UV;
            float glowZ = this.resolveOverlayFaceZ(glowMatrix);
            float glowNz = glowZ >= 0F ? 1F : -1F;

            RenderSystem.disableCull();

            for (Map.Entry<RenderLayer, List<LabelTextTintQuadCapture.GlyphQuad>> layerEntry : byLayer.entrySet())
            {
                this.bindTextLayerTexture(layerEntry.getKey());
                BlockEffectOverlayUniforms.configureFlatGlowOverlay(formRootInverse, glowTransform, false, this.maskHalfExtents, shaderScale);
                GlStateManager._bindTexture(this.lastBoundTextTexture);

                BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);

                for (LabelTextTintQuadCapture.GlyphQuad quad : layerEntry.getValue())
                {
                    this.fillLabelPaint(builder, glowMatrix, entry, quad.x0 - centerX, quad.y0 - centerY, glowZ, quad.u0, quad.v0, overlay, glowLight, glowNz, glowColor);
                    this.fillLabelPaint(builder, glowMatrix, entry, quad.x1 - centerX, quad.y1 - centerY, glowZ, quad.u1, quad.v1, overlay, glowLight, glowNz, glowColor);
                    this.fillLabelPaint(builder, glowMatrix, entry, quad.x2 - centerX, quad.y2 - centerY, glowZ, quad.u2, quad.v2, overlay, glowLight, glowNz, glowColor);

                    this.fillLabelPaint(builder, glowMatrix, entry, quad.x0 - centerX, quad.y0 - centerY, glowZ, quad.u0, quad.v0, overlay, glowLight, glowNz, glowColor);
                    this.fillLabelPaint(builder, glowMatrix, entry, quad.x2 - centerX, quad.y2 - centerY, glowZ, quad.u2, quad.v2, overlay, glowLight, glowNz, glowColor);
                    this.fillLabelPaint(builder, glowMatrix, entry, quad.x3 - centerX, quad.y3 - centerY, glowZ, quad.u3, quad.v3, overlay, glowLight, glowNz, glowColor);
                }

                BufferRenderer.drawWithGlobalProgram(builder.end());
            }

            RenderSystem.enableCull();
        });
    }



    private void renderString(FormRenderingContext context, CustomVertexConsumerProvider consumers, TextRenderer renderer, int light)
    {
        String content = applyStyles(StringUtils.processColoredText(this.form.text.get()));
        String fontName = this.form.font.get();
        TextureFont customFont = null;
        
        if (!fontName.isEmpty())
        {
            int style = Font.PLAIN;
            if (this.form.fontWeight.get() >= 700) style |= Font.BOLD;
            if (this.form.fontStyle.get() >= 1) style |= Font.ITALIC;
            
            customFont = FontUtils.getFont(fontName, style);
        }

        float transition = context.getTransition();
        float letterSpacing = this.form.letterSpacing.get();
        int w = customFont != null ? customFont.getWidth(content, letterSpacing) : renderer.getWidth(content) - 1;
        int h = customFont != null ? customFont.getHeight() : renderer.fontHeight - 2;
        int x = (int) (-w * this.form.anchorX.get());
        int y = (int) (-h * this.form.anchorY.get());

        GlowSettings glowSettings = this.form.glowSettings.get();
        Color legacyGlow = this.form.glowingColor.get();
        float glowIntensity = glowSettings.resolveIntensity(legacyGlow);
        PaintSettings paintSettings = this.form.paintSettings.get();
        Color legacyPaint = this.form.paintColor.get();
        Color shadowColor = this.form.shadowColor.get().copy();
        Color storedFormColor = this.form.color.get();
        boolean colorTransformWanted = FormColorEffects.wantsColorTransformMask(storedFormColor) && !context.isPicking();
        Color contextColor = new Color().set(context.color, true);
        Color color = contextColor.copy();
        Color formTintColor = null;
        EffectTransform colorTransform = null;

        /* Spatial Color transform: bake mask per glyph (AABB overlay would tint the background). */
        if (colorTransformWanted)
        {
            color.r = 1F;
            color.g = 1F;
            color.b = 1F;
            this.form.applyFormOpacity(color);
            /* Keep base + FlatColorTint opacity in sync (context alpha used to hit only the tint). */
            color.a *= contextColor.a;
            formTintColor = storedFormColor.copyBakingColorGrade().copy();
            this.form.applyFormOpacity(formTintColor);
            formTintColor.mul(contextColor);
            colorTransform = storedFormColor.transform == null ? null : storedFormColor.transform.copy();
        }
        else
        {
            color.mul(storedFormColor.copyBakingColorGrade());
        }

        float paintStrength = paintSettings.resolveIntensity(legacyPaint);
        boolean positivePaint = !context.isPicking() && FormColorEffects.hasPositivePaint(paintSettings, legacyPaint);
        boolean shadowPass = this.isShadowPass(context);

        if (!shadowPass && !context.isPicking() && (!colorTransformWanted || paintStrength < 0F))
        {
            FormColorEffects.applyPaintBlend(color, paintSettings, legacyPaint);
        }

        if (!shadowPass && !context.isPicking() && glowIntensity < 0F)
        {
            FormColorEffects.blendFormGlowBrighten(color, glowSettings, legacyGlow);
        }

        shadowColor.a *= this.nametagAlpha;
        color.a *= this.nametagAlpha;

        if (formTintColor != null)
        {
            formTintColor.a *= this.nametagAlpha;
        }

        float formOpacity = color.a;
        shadowColor.a *= this.form.color.get().a;
        shadowColor.mul(context.color);

        if (isFullyTransparent(color) && !context.isPicking())
        {
            if (!shadowPass)
            {
                this.renderShadow(context, x, y, w, h);
            }

            return;
        }

        boolean hasShadow = !shadowPass && !isFullyTransparent(shadowColor);
        boolean hasOutline = !shadowPass && this.form.outline.get() && !isFullyTransparent(color);
        Consumer<RenderLayer> baseHijack = this.createLabelBaseHijack(context);
        boolean savedDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean savedPolygonOffsetFill = GL11.glGetBoolean(GL11.GL_POLYGON_OFFSET_FILL);

        try
        {
            /* Decorations: same plane + polygon offset away from camera (no local-Z sign flip),
             * flush, then fill at true depth. Rim still writes depth for translucents. */
            if (hasShadow || hasOutline)
            {
                this.beginLabelDecorationDepthPass(baseHijack);

                if (hasShadow)
                {
                    this.renderTextShadow(context, consumers, renderer, customFont, content, x, y, letterSpacing, light, shadowColor);
                }

                if (hasOutline)
                {
                    Color outlineColor = this.form.outlineColor.get().copy();
                    outlineColor.a *= formOpacity;
                    int oc = toSafeTextArgb(outlineColor);
                    float ow = this.form.outlineWidth.get();

                    if (customFont != null)
                    {
                        customFont.draw(content, x - ow, y, oc, oc, letterSpacing, 0F, context.stack.peek().getPositionMatrix(), consumers, this.resolveLabelLight(light));
                        customFont.draw(content, x + ow, y, oc, oc, letterSpacing, 0F, context.stack.peek().getPositionMatrix(), consumers, this.resolveLabelLight(light));
                        customFont.draw(content, x, y - ow, oc, oc, letterSpacing, 0F, context.stack.peek().getPositionMatrix(), consumers, this.resolveLabelLight(light));
                        customFont.draw(content, x, y + ow, oc, oc, letterSpacing, 0F, context.stack.peek().getPositionMatrix(), consumers, this.resolveLabelLight(light));
                    }
                    else
                    {
                        renderer.draw(content, x - ow, y, oc, false, context.stack.peek().getPositionMatrix(), consumers, TextRenderer.TextLayerType.NORMAL, 0, this.resolveLabelLight(light));
                        renderer.draw(content, x + ow, y, oc, false, context.stack.peek().getPositionMatrix(), consumers, TextRenderer.TextLayerType.NORMAL, 0, this.resolveLabelLight(light));
                        renderer.draw(content, x, y - ow, oc, false, context.stack.peek().getPositionMatrix(), consumers, TextRenderer.TextLayerType.NORMAL, 0, this.resolveLabelLight(light));
                        renderer.draw(content, x, y + ow, oc, false, context.stack.peek().getPositionMatrix(), consumers, TextRenderer.TextLayerType.NORMAL, 0, this.resolveLabelLight(light));
                    }
                }

                this.flushLabelConsumers(consumers);
            }

            this.beginLabelFillDepthPass(baseHijack);

            Color gradientEnd = null;

            if (this.form.gradient.get() && !colorTransformWanted)
            {
                gradientEnd = this.form.gradientEndColor.get().copy();
                gradientEnd.a *= formOpacity;
                gradientEnd.mul(context.color);
            }

            float baseFillZ = colorTransformWanted ? this.resolveBaseFillFaceZ(context.stack.peek().getPositionMatrix()) : 0F;

            if (baseFillZ != 0F)
            {
                context.stack.push();
                context.stack.translate(0F, 0F, baseFillZ);
            }

            int textArgb = this.drawLabelContent(context, consumers, renderer, customFont, content, x, y, letterSpacing, light, color, gradientEnd);

            if (baseFillZ != 0F)
            {
                context.stack.pop();
            }

            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            this.flushLabelConsumers(consumers);

            List<LabelTextTintQuadCapture.GlyphQuad> overlayQuads = null;

            if (!shadowPass && !context.isPicking())
            {
                boolean hasPositiveGlow = glowIntensity > 0F && !glowSettings.resolvePaintOnly();
                EffectTransform glowTransform = hasPositiveGlow ? FormColorEffects.resolveGlowEffectTransform(glowSettings, legacyGlow) : null;
                boolean hasGlowTransform = glowTransform != null && glowTransform.isActive();
                boolean needsGlyphs = formTintColor != null || (colorTransformWanted && positivePaint) || hasPositiveGlow;

                if (needsGlyphs)
                {
                    this.tintCapture.clear();
                    this.captureLabelGlyphs(this.tintCapture, renderer, customFont, content, x, y, letterSpacing, light);
                    overlayQuads = this.tintCapture.snapshot();
                }

                if (formTintColor != null && overlayQuads != null && !overlayQuads.isEmpty())
                {
                    this.submitOrRenderLabelColorTint(context, x, y, w, h, formTintColor, colorTransform, overlayQuads);
                }

                if (colorTransformWanted && positivePaint && overlayQuads != null && !overlayQuads.isEmpty())
                {
                    Color resolvedPaint = FormColorEffects.resolvePaintColor(paintSettings, legacyPaint);

                    resolvedPaint.a *= formOpacity;
                    EffectTransform paintTransform = paintSettings.transform == null ? null : paintSettings.transform.copy();
                    this.submitOrRenderLabelPaintOverlay(context, x, y, w, h, resolvedPaint, paintTransform, overlayQuads);
                }

                if (hasPositiveGlow && overlayQuads != null && !overlayQuads.isEmpty())
                {
                    this.submitOrRenderLabelGlowOverlay(context, x, y, w, h, glowSettings, legacyGlow, glowIntensity, color.a, hasGlowTransform ? glowTransform : null, overlayQuads);
                }

                this.renderShadow(context, x, y, w, h);
            }
        }
        finally
        {
            RenderSystem.depthMask(savedDepthMask);
            this.restoreLabelPolygonOffset(savedPolygonOffsetFill);
        }
    }

    private void renderLimitedString(FormRenderingContext context, CustomVertexConsumerProvider consumers, TextRenderer renderer, int light)
    {
        float transition = context.getTransition();
        int w = 0;
        int h = renderer.fontHeight - 2;
        String content = applyStyles(StringUtils.processColoredText(this.form.text.get()));
        
        String fontName = this.form.font.get();
        TextureFont customFont = null;
        
        if (!fontName.isEmpty())
        {
            int style = Font.PLAIN;
            if (this.form.fontWeight.get() >= 700) style |= Font.BOLD;
            if (this.form.fontStyle.get() >= 1) style |= Font.ITALIC;
            
            customFont = FontUtils.getFont(fontName, style);
        }

        float letterSpacing = this.form.letterSpacing.get();
        List<String> lines;
        
        if (customFont != null)
        {
            lines = customFont.wrap(content, this.form.max.get(), letterSpacing);
        }
        else
        {
            lines = FontRenderer.wrap(renderer, content, this.form.max.get());
        }

        if (lines.size() <= 1)
        {
            this.renderString(context, consumers, renderer, light);
            return;
        }

        for (int i = 0; i < lines.size(); i++)
        {
            lines.set(i, lines.get(i).trim());
        }

        for (String line : lines)
        {
            int lw = customFont != null ? customFont.getWidth(line, letterSpacing) : renderer.getWidth(line) - 1;
            w = Math.max(lw, w);
        }

        int fh = customFont != null ? customFont.getHeight() : renderer.fontHeight - 2;
        int lineStep = fh + this.form.lineHeight.get().intValue() + this.resolveWrapLineGap();
        int totalHeight = (lines.size() - 1) * lineStep + fh;

        float anchorX = this.form.anchorX.get();
        int x = (int) (-w * anchorX);
        int y = (int) (-totalHeight * this.form.anchorY.get());
        int shadowY = y;

        GlowSettings glowSettings = this.form.glowSettings.get();
        Color legacyGlow = this.form.glowingColor.get();
        float glowIntensity = glowSettings.resolveIntensity(legacyGlow);
        PaintSettings paintSettings = this.form.paintSettings.get();
        Color legacyPaint = this.form.paintColor.get();
        Color shadowColor = this.form.shadowColor.get().copy();
        Color storedFormColor = this.form.color.get();
        boolean colorTransformWanted = FormColorEffects.wantsColorTransformMask(storedFormColor) && !context.isPicking();
        Color contextColor = new Color().set(context.color, true);
        Color color = contextColor.copy();
        Color formTintColor = null;
        EffectTransform colorTransform = null;

        if (colorTransformWanted)
        {
            color.r = 1F;
            color.g = 1F;
            color.b = 1F;
            this.form.applyFormOpacity(color);
            color.a *= contextColor.a;
            formTintColor = storedFormColor.copyBakingColorGrade().copy();
            this.form.applyFormOpacity(formTintColor);
            formTintColor.mul(contextColor);
            colorTransform = storedFormColor.transform == null ? null : storedFormColor.transform.copy();
        }
        else
        {
            color.mul(storedFormColor.copyBakingColorGrade());
        }

        float paintStrength = paintSettings.resolveIntensity(legacyPaint);
        boolean positivePaint = !context.isPicking() && FormColorEffects.hasPositivePaint(paintSettings, legacyPaint);
        boolean shadowPass = this.isShadowPass(context);

        if (!shadowPass && !context.isPicking() && (!colorTransformWanted || paintStrength < 0F))
        {
            FormColorEffects.applyPaintBlend(color, paintSettings, legacyPaint);
        }

        if (!shadowPass && !context.isPicking() && glowIntensity < 0F)
        {
            FormColorEffects.blendFormGlowBrighten(color, glowSettings, legacyGlow);
        }

        float formOpacity = color.a;
        shadowColor.a *= this.form.color.get().a;

        shadowColor.mul(context.color);
        shadowColor.a *= this.nametagAlpha;
        color.a *= this.nametagAlpha;

        if (formTintColor != null)
        {
            formTintColor.a *= this.nametagAlpha;
        }

        if (isFullyTransparent(color) && !context.isPicking())
        {
            if (!this.isShadowPass(context))
            {
                this.renderShadow(context, x, shadowY, w, totalHeight);
            }

            return;
        }

        int align = this.form.textAlign.get(); /* 0: Left, 1: Center, 2: Right */
        boolean anchorLines = this.form.anchorLines.get();
        Color gradientEnd = null;

        if (this.form.gradient.get() && !colorTransformWanted)
        {
            gradientEnd = this.form.gradientEndColor.get().copy();
            gradientEnd.a *= formOpacity;
            gradientEnd.mul(context.color);
        }

        int textArgb = toSafeTextArgb(color);

        this.tintCapture.clear();

        boolean hasShadow = !shadowPass && !isFullyTransparent(shadowColor);
        boolean hasOutline = !shadowPass && this.form.outline.get() && !isFullyTransparent(color);
        Consumer<RenderLayer> baseHijack = this.createLabelBaseHijack(context);
        boolean savedDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean savedPolygonOffsetFill = GL11.glGetBoolean(GL11.GL_POLYGON_OFFSET_FILL);

        try
        {
            if (hasShadow || hasOutline)
            {
                this.beginLabelDecorationDepthPass(baseHijack);

                int outlineY = shadowY;

                for (String line : lines)
                {
                    int lw = customFont != null ? customFont.getWidth(line, letterSpacing) : renderer.getWidth(line) - 1;
                    int lx = x;

                    if (anchorLines)
                    {
                        lx = (int) (-lw * anchorX);
                    }
                    else if (align == 1)
                    {
                        lx = x + (w - lw) / 2;
                    }
                    else if (align == 2)
                    {
                        lx = x + (w - lw);
                    }

                    if (hasShadow)
                    {
                        this.renderTextShadow(context, consumers, renderer, customFont, line, lx, outlineY, letterSpacing, light, shadowColor);
                    }

                    if (hasOutline)
                    {
                        Color outlineColor = this.form.outlineColor.get().copy();
                        outlineColor.a *= formOpacity;
                        int oc = toSafeTextArgb(outlineColor);
                        float ow = this.form.outlineWidth.get();

                        if (customFont != null)
                        {
                            customFont.draw(line, lx - ow, outlineY, oc, oc, letterSpacing, 0F, context.stack.peek().getPositionMatrix(), consumers, this.resolveLabelLight(light));
                            customFont.draw(line, lx + ow, outlineY, oc, oc, letterSpacing, 0F, context.stack.peek().getPositionMatrix(), consumers, this.resolveLabelLight(light));
                            customFont.draw(line, lx, outlineY - ow, oc, oc, letterSpacing, 0F, context.stack.peek().getPositionMatrix(), consumers, this.resolveLabelLight(light));
                            customFont.draw(line, lx, outlineY + ow, oc, oc, letterSpacing, 0F, context.stack.peek().getPositionMatrix(), consumers, this.resolveLabelLight(light));
                        }
                        else
                        {
                            renderer.draw(line, lx - ow, outlineY, oc, false, context.stack.peek().getPositionMatrix(), consumers, TextRenderer.TextLayerType.NORMAL, 0, this.resolveLabelLight(light));
                            renderer.draw(line, lx + ow, outlineY, oc, false, context.stack.peek().getPositionMatrix(), consumers, TextRenderer.TextLayerType.NORMAL, 0, this.resolveLabelLight(light));
                            renderer.draw(line, lx, outlineY - ow, oc, false, context.stack.peek().getPositionMatrix(), consumers, TextRenderer.TextLayerType.NORMAL, 0, this.resolveLabelLight(light));
                            renderer.draw(line, lx, outlineY + ow, oc, false, context.stack.peek().getPositionMatrix(), consumers, TextRenderer.TextLayerType.NORMAL, 0, this.resolveLabelLight(light));
                        }
                    }

                    outlineY += lineStep;
                }

                this.flushLabelConsumers(consumers);
            }

            this.beginLabelFillDepthPass(baseHijack);

            y = shadowY;
            int textArgbFill = 0;

            float baseFillZ = colorTransformWanted ? this.resolveBaseFillFaceZ(context.stack.peek().getPositionMatrix()) : 0F;

            if (baseFillZ != 0F)
            {
                context.stack.push();
                context.stack.translate(0F, 0F, baseFillZ);
            }

            for (String line : lines)
            {
                int lw = customFont != null ? customFont.getWidth(line, letterSpacing) : renderer.getWidth(line) - 1;
                int lx = x;

                if (anchorLines)
                {
                    lx = (int) (-lw * anchorX);
                }
                else if (align == 1)
                {
                    lx = x + (w - lw) / 2;
                }
                else if (align == 2)
                {
                    lx = x + (w - lw);
                }

                textArgbFill = this.drawLabelContent(context, consumers, renderer, customFont, line, lx, y, letterSpacing, light, color, gradientEnd);

                boolean hasPositiveGlow = glowIntensity > 0F && !glowSettings.resolvePaintOnly();

                if (!shadowPass && !context.isPicking() && (formTintColor != null || (colorTransformWanted && positivePaint) || hasPositiveGlow))
                {
                    this.captureLabelGlyphs(this.tintCapture, renderer, customFont, line, lx, y, letterSpacing, light);
                }

                y += lineStep;
            }

            if (baseFillZ != 0F)
            {
                context.stack.pop();
            }

            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            this.flushLabelConsumers(consumers);

            if (!shadowPass && !context.isPicking())
            {
                List<LabelTextTintQuadCapture.GlyphQuad> overlayQuads = this.tintCapture.snapshot();
                boolean hasPositiveGlow = glowIntensity > 0F && !glowSettings.resolvePaintOnly();
                EffectTransform glowTransform = hasPositiveGlow ? FormColorEffects.resolveGlowEffectTransform(glowSettings, legacyGlow) : null;
                boolean hasGlowTransform = glowTransform != null && glowTransform.isActive();

                if (formTintColor != null && overlayQuads != null && !overlayQuads.isEmpty())
                {
                    this.submitOrRenderLabelColorTint(context, x, shadowY, w, totalHeight, formTintColor, colorTransform, overlayQuads);
                }

                if (colorTransformWanted && positivePaint && overlayQuads != null && !overlayQuads.isEmpty())
                {
                    Color resolvedPaint = FormColorEffects.resolvePaintColor(paintSettings, legacyPaint);

                    resolvedPaint.a *= formOpacity;
                    EffectTransform paintTransform = paintSettings.transform == null ? null : paintSettings.transform.copy();
                    this.submitOrRenderLabelPaintOverlay(context, x, shadowY, w, totalHeight, resolvedPaint, paintTransform, overlayQuads);
                }

                if (hasPositiveGlow && overlayQuads != null && !overlayQuads.isEmpty())
                {
                    this.submitOrRenderLabelGlowOverlay(context, x, shadowY, w, totalHeight, glowSettings, legacyGlow, glowIntensity, color.a, hasGlowTransform ? glowTransform : null, overlayQuads);
                }

                this.renderShadow(context, x, shadowY, w, totalHeight);
            }
        }
        finally
        {
            RenderSystem.depthMask(savedDepthMask);
            this.restoreLabelPolygonOffset(savedPolygonOffsetFill);
        }
    }

    /**
     * Draws label glyphs with a flat vertex color (no spatial mask bake). Color transform is
     * applied afterward via FlatColorTint on captured glyph quads.
     */
    private int drawLabelContent(FormRenderingContext context, CustomVertexConsumerProvider consumers, TextRenderer renderer, TextureFont customFont, String content, float drawX, float drawY, float letterSpacing, int light, Color color, Color gradientEnd)
    {
        int c1 = toSafeTextArgb(color);
        int c2 = c1;

        if (gradientEnd != null)
        {
            c2 = toSafeTextArgb(gradientEnd);
        }

        if (customFont != null)
        {
            customFont.draw(content, drawX, drawY, c1, c2, letterSpacing, 0F, context.stack.peek().getPositionMatrix(), consumers, this.resolveLabelLight(light), this.form.gradientOffset.get());
        }
        else
        {
            renderer.draw(
                content,
                drawX,
                drawY,
                c1, false,
                context.stack.peek().getPositionMatrix(),
                consumers,
                TextRenderer.TextLayerType.NORMAL,
                0,
                this.resolveLabelLight(light)
            );
        }

        return c1;
    }

    private void captureLabelGlyphs(LabelTextTintQuadCapture capture, TextRenderer renderer, TextureFont customFont, String content, float x, float y, float letterSpacing, int light)
    {
        int opaqueWhite = 0xFFFFFFFF;

        this.identityMatrix.identity();

        if (customFont != null)
        {
            customFont.draw(content, x, y, opaqueWhite, opaqueWhite, letterSpacing, 0F, this.identityMatrix, capture, this.resolveLabelLight(light));
        }
        else
        {
            renderer.draw(content, x, y, opaqueWhite, false, this.identityMatrix, capture, TextRenderer.TextLayerType.NORMAL, 0, this.resolveLabelLight(light));
        }
    }

    private void submitOrRenderLabelColorTint(FormRenderingContext context, float x, float y, float w, float h, Color formTintColor, EffectTransform colorTransform, List<LabelTextTintQuadCapture.GlyphQuad> quads)
    {
        if (formTintColor == null || quads == null || quads.isEmpty())
        {
            return;
        }

        LabelOverlayLayout layout = this.resolveLabelOverlayLayout(x, y, w, h, quads);
        Color tintSnapshot = formTintColor.copy();
        EffectTransform transformSnapshot = colorTransform == null ? null : colorTransform.copy();
        List<LabelTextTintQuadCapture.GlyphQuad> quadSnapshot = new ArrayList<>(quads);
        boolean defer = BBSRendering.isIrisWorldModelPass() && !context.modelRenderer && !context.isPicking();
        Matrix4f rootMatrix = this.captureLabelOverlayRootMatrix(context, layout.centerX, layout.centerY);

        if (defer)
        {
            Matrix4f positionMatrix = ModelVAORenderer.capturePaintOverlayRootMatrix(rootMatrix);

            ModelVAORenderer.submitColorTintOverlay(() ->
            {
                MatrixStack overlayStack = new MatrixStack();

                overlayStack.peek().getPositionMatrix().set(positionMatrix);
                this.renderLabelColorTintOverlay(overlayStack, layout.centerX, layout.centerY, layout.halfX, layout.halfY, tintSnapshot, transformSnapshot, quadSnapshot, FlatPaintOverlayPass.DEFERRED_BILLBOARD_FACTOR, FlatPaintOverlayPass.DEFERRED_BILLBOARD_UNITS);
            });
        }
        else
        {
            MatrixStack overlayStack = new MatrixStack();

            overlayStack.peek().getPositionMatrix().set(rootMatrix);
            this.renderLabelColorTintOverlay(overlayStack, layout.centerX, layout.centerY, layout.halfX, layout.halfY, tintSnapshot, transformSnapshot, quadSnapshot, LabelFormRenderer.LABEL_COLOR_TINT_OFFSET_FACTOR, LabelFormRenderer.LABEL_COLOR_TINT_OFFSET_UNITS);
        }
    }

    private void submitOrRenderLabelPaintOverlay(FormRenderingContext context, float x, float y, float w, float h, Color resolvedPaint, EffectTransform paintTransform, List<LabelTextTintQuadCapture.GlyphQuad> quads)
    {
        if (resolvedPaint == null || resolvedPaint.a <= 0.001F || quads == null || quads.isEmpty())
        {
            return;
        }

        LabelOverlayLayout layout = this.resolveLabelOverlayLayout(x, y, w, h, quads);
        Color paintSnapshot = resolvedPaint.copy();
        EffectTransform transformSnapshot = paintTransform == null ? null : paintTransform.copy();
        List<LabelTextTintQuadCapture.GlyphQuad> quadSnapshot = new ArrayList<>(quads);
        boolean defer = BBSRendering.isIrisWorldModelPass() && !context.modelRenderer && !context.isPicking();
        Matrix4f rootMatrix = this.captureLabelOverlayRootMatrix(context, layout.centerX, layout.centerY);

        if (defer)
        {
            Matrix4f positionMatrix = ModelVAORenderer.capturePaintOverlayRootMatrix(rootMatrix);

            ModelVAORenderer.submitPaintOverlay(false, () ->
            {
                MatrixStack overlayStack = new MatrixStack();

                overlayStack.peek().getPositionMatrix().set(positionMatrix);
                this.renderLabelPaintOverlay(overlayStack, layout.centerX, layout.centerY, layout.halfX, layout.halfY, paintSnapshot, transformSnapshot, quadSnapshot, FlatPaintOverlayPass.DEFERRED_BILLBOARD_FACTOR, LabelFormRenderer.LABEL_DEFERRED_PAINT_OFFSET_UNITS);
            });
        }
        else
        {
            MatrixStack overlayStack = new MatrixStack();

            overlayStack.peek().getPositionMatrix().set(rootMatrix);
            this.renderLabelPaintOverlay(overlayStack, layout.centerX, layout.centerY, layout.halfX, layout.halfY, paintSnapshot, transformSnapshot, quadSnapshot, LabelFormRenderer.LABEL_PAINT_OFFSET_FACTOR, LabelFormRenderer.LABEL_PAINT_OFFSET_UNITS);
        }
    }

    private Matrix4f captureLabelOverlayRootMatrix(FormRenderingContext context, float centerX, float centerY)
    {
        context.stack.push();
        context.stack.translate(centerX, centerY, 0F);

        Matrix4f rootMatrix = new Matrix4f(context.stack.peek().getPositionMatrix());

        context.stack.pop();

        return rootMatrix;
    }

    private LabelOverlayLayout resolveLabelOverlayLayout(float x, float y, float w, float h, List<LabelTextTintQuadCapture.GlyphQuad> quads)
    {
        /* Glyph AABB can extend past layout metrics (descenders, bearings). Keep the mask
         * origin on the label layout center (same as the form gizmo), and grow half extents
         * so every captured glyph stays inside. */
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;

        for (LabelTextTintQuadCapture.GlyphQuad quad : quads)
        {
            minX = Math.min(minX, Math.min(Math.min(quad.x0, quad.x1), Math.min(quad.x2, quad.x3)));
            minY = Math.min(minY, Math.min(Math.min(quad.y0, quad.y1), Math.min(quad.y2, quad.y3)));
            maxX = Math.max(maxX, Math.max(Math.max(quad.x0, quad.x1), Math.max(quad.x2, quad.x3)));
            maxY = Math.max(maxY, Math.max(Math.max(quad.y0, quad.y1), Math.max(quad.y2, quad.y3)));
        }

        float centerX = x + w * 0.5F;
        float centerY = y + h * 0.5F;
        float resolvedHalfX = w * 0.5F;
        float resolvedHalfY = h * 0.5F;

        if (minX < maxX && minY < maxY)
        {
            resolvedHalfX = Math.max(resolvedHalfX, Math.max(Math.abs(maxX - centerX), Math.abs(minX - centerX)));
            resolvedHalfY = Math.max(resolvedHalfY, Math.max(Math.abs(maxY - centerY), Math.abs(minY - centerY)));
        }

        LabelOverlayLayout layout = new LabelOverlayLayout();

        layout.centerX = centerX;
        layout.centerY = centerY;
        layout.halfX = Math.max(resolvedHalfX, 0.001F);
        layout.halfY = Math.max(resolvedHalfY, 0.001F);

        return layout;
    }

    /**
     * Billboard-style FlatColorTint on glyph quads. Glyph positions are converted into
     * AABB-centered local space so mask scale/offset match other forms (origin at text center).
     */
    private void renderLabelColorTintOverlay(MatrixStack stack, float centerX, float centerY, float halfX, float halfY, Color formTintColor, EffectTransform colorTransform, List<LabelTextTintQuadCapture.GlyphQuad> quads, float polygonOffsetFactor, float polygonOffsetUnits)
    {
        Matrix4f tintMatrix = stack.peek().getPositionMatrix();
        MatrixStack.Entry entry = stack.peek();
        Matrix4f formRootInverse = new Matrix4f(tintMatrix).invert();

        EffectTransformMath.resolveBillboardMaskHalfExtents(colorTransform, this.maskHalfExtents, halfX, halfY);

        Map<RenderLayer, List<LabelTextTintQuadCapture.GlyphQuad>> byLayer = new LinkedHashMap<>();

        for (LabelTextTintQuadCapture.GlyphQuad quad : quads)
        {
            byLayer.computeIfAbsent(quad.layer, (layer) -> new ArrayList<>()).add(quad);
        }

        float offsetUnits = this.resolveLabelOverlayOffsetUnits(tintMatrix, polygonOffsetUnits);

        FlatColorTintOverlayPass.render(polygonOffsetFactor, offsetUnits, formRootInverse, colorTransform, false, this.maskHalfExtents, formTintColor, () ->
        {
            int tintLight = LightmapTextureManager.MAX_LIGHT_COORDINATE;
            int overlay = OverlayTexture.DEFAULT_UV;
            float tintZ = this.resolveOverlayFaceZ(tintMatrix);
            float tintNz = tintZ >= 0F ? 1F : -1F;

            RenderSystem.disableCull();

            for (Map.Entry<RenderLayer, List<LabelTextTintQuadCapture.GlyphQuad>> layerEntry : byLayer.entrySet())
            {
                this.bindTextLayerTexture(layerEntry.getKey());
                /* Text RenderLayer.startDrawing replaces the FlatColorTint program — restore it. */
                BlockEffectOverlayUniforms.configureFlatColorTintOverlay(formRootInverse, colorTransform, false, this.maskHalfExtents, formTintColor);
                GlStateManager._bindTexture(this.lastBoundTextTexture);

                BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);

                for (LabelTextTintQuadCapture.GlyphQuad quad : layerEntry.getValue())
                {
                    this.fillLabelTint(builder, tintMatrix, entry, quad.x0 - centerX, quad.y0 - centerY, tintZ, quad.u0, quad.v0, overlay, tintLight, tintNz);
                    this.fillLabelTint(builder, tintMatrix, entry, quad.x1 - centerX, quad.y1 - centerY, tintZ, quad.u1, quad.v1, overlay, tintLight, tintNz);
                    this.fillLabelTint(builder, tintMatrix, entry, quad.x2 - centerX, quad.y2 - centerY, tintZ, quad.u2, quad.v2, overlay, tintLight, tintNz);

                    this.fillLabelTint(builder, tintMatrix, entry, quad.x0 - centerX, quad.y0 - centerY, tintZ, quad.u0, quad.v0, overlay, tintLight, tintNz);
                    this.fillLabelTint(builder, tintMatrix, entry, quad.x2 - centerX, quad.y2 - centerY, tintZ, quad.u2, quad.v2, overlay, tintLight, tintNz);
                    this.fillLabelTint(builder, tintMatrix, entry, quad.x3 - centerX, quad.y3 - centerY, tintZ, quad.u3, quad.v3, overlay, tintLight, tintNz);
                }

                BufferRenderer.drawWithGlobalProgram(builder.end());
            }

            RenderSystem.enableCull();
        });
    }

    private void renderLabelPaintOverlay(MatrixStack stack, float centerX, float centerY, float halfX, float halfY, Color resolvedPaint, EffectTransform paintTransform, List<LabelTextTintQuadCapture.GlyphQuad> quads, float polygonOffsetFactor, float polygonOffsetUnits)
    {
        Matrix4f paintMatrix = stack.peek().getPositionMatrix();
        MatrixStack.Entry entry = stack.peek();
        Matrix4f formRootInverse = new Matrix4f(paintMatrix).invert();

        EffectTransformMath.resolveBillboardMaskHalfExtents(paintTransform, this.maskHalfExtents, halfX, halfY);

        Map<RenderLayer, List<LabelTextTintQuadCapture.GlyphQuad>> byLayer = new LinkedHashMap<>();

        for (LabelTextTintQuadCapture.GlyphQuad quad : quads)
        {
            byLayer.computeIfAbsent(quad.layer, (layer) -> new ArrayList<>()).add(quad);
        }

        float offsetUnits = this.resolveLabelOverlayOffsetUnits(paintMatrix, polygonOffsetUnits);

        FlatPaintOverlayPass.render(polygonOffsetFactor, offsetUnits, formRootInverse, paintTransform, false, this.maskHalfExtents, () ->
        {
            int paintLight = LightmapTextureManager.MAX_LIGHT_COORDINATE;
            int overlay = OverlayTexture.DEFAULT_UV;
            float paintZ = this.resolveOverlayFaceZ(paintMatrix);
            float paintNz = paintZ >= 0F ? 1F : -1F;

            RenderSystem.disableCull();

            for (Map.Entry<RenderLayer, List<LabelTextTintQuadCapture.GlyphQuad>> layerEntry : byLayer.entrySet())
            {
                this.bindTextLayerTexture(layerEntry.getKey());
                BlockEffectOverlayUniforms.configureFlatPaintOverlay(formRootInverse, paintTransform, false, this.maskHalfExtents);
                GlStateManager._bindTexture(this.lastBoundTextTexture);

                BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);

                for (LabelTextTintQuadCapture.GlyphQuad quad : layerEntry.getValue())
                {
                    this.fillLabelPaint(builder, paintMatrix, entry, quad.x0 - centerX, quad.y0 - centerY, paintZ, quad.u0, quad.v0, overlay, paintLight, paintNz, resolvedPaint);
                    this.fillLabelPaint(builder, paintMatrix, entry, quad.x1 - centerX, quad.y1 - centerY, paintZ, quad.u1, quad.v1, overlay, paintLight, paintNz, resolvedPaint);
                    this.fillLabelPaint(builder, paintMatrix, entry, quad.x2 - centerX, quad.y2 - centerY, paintZ, quad.u2, quad.v2, overlay, paintLight, paintNz, resolvedPaint);

                    this.fillLabelPaint(builder, paintMatrix, entry, quad.x0 - centerX, quad.y0 - centerY, paintZ, quad.u0, quad.v0, overlay, paintLight, paintNz, resolvedPaint);
                    this.fillLabelPaint(builder, paintMatrix, entry, quad.x2 - centerX, quad.y2 - centerY, paintZ, quad.u2, quad.v2, overlay, paintLight, paintNz, resolvedPaint);
                    this.fillLabelPaint(builder, paintMatrix, entry, quad.x3 - centerX, quad.y3 - centerY, paintZ, quad.u3, quad.v3, overlay, paintLight, paintNz, resolvedPaint);
                }

                BufferRenderer.drawWithGlobalProgram(builder.end());
            }

            RenderSystem.enableCull();
        });
    }

    private void bindTextLayerTexture(RenderLayer layer)
    {
        this.lastBoundTextTexture = 0;

        if (layer == null)
        {
            return;
        }

        layer.startDrawing();
        this.lastBoundTextTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        layer.endDrawing();
    }

    private void fillLabelTint(BufferBuilder builder, Matrix4f matrix, MatrixStack.Entry entry, float x, float y, float z, float u, float v, int overlay, int light, float nz)
    {
        builder.vertex(matrix, x, y, z).color(1F, 1F, 1F, 1F).texture(u, v).overlay(overlay).light(light).normal(entry, 0F, 0F, nz);
    }

    private void fillLabelPaint(BufferBuilder builder, Matrix4f matrix, MatrixStack.Entry entry, float x, float y, float z, float u, float v, int overlay, int light, float nz, Color paintColor)
    {
        builder.vertex(matrix, x, y, z).color(paintColor.r, paintColor.g, paintColor.b, paintColor.a).texture(u, v).overlay(overlay).light(light).normal(entry, 0F, 0F, nz);
    }

    /**
     * Local Z just outside the base face that points toward the camera. Same rule as
     * {@link BillboardFormRenderer#resolveOverlayFaceZ} — fixed ±Z fails when the label
     * plane is rotated (e.g. on X); polygon offset alone is not enough at grazing angles.
     */
    private float resolveOverlayFaceZ(Matrix4f viewModel)
    {
        return this.resolveOverlayPlaneSign(viewModel) * this.resolveLabelOverlaySeparation(viewModel);
    }

    /**
     * Base fill sits slightly behind the mid-plane when a color-transform overlay follows,
     * doubling the effective depth gap without relying on polygon offset alone at distance.
     */
    private float resolveBaseFillFaceZ(Matrix4f viewModel)
    {
        return -this.resolveOverlayPlaneSign(viewModel) * this.resolveLabelOverlaySeparation(viewModel) * LABEL_BASE_FILL_PULLBACK;
    }

    private float resolveOverlayPlaneSign(Matrix4f viewModel)
    {
        OVERLAY_TO_CAMERA.set(-viewModel.m30(), -viewModel.m31(), -viewModel.m32());
        OVERLAY_LOCAL_Z.set(viewModel.m20(), viewModel.m21(), viewModel.m22());

        float facing = OVERLAY_LOCAL_Z.dot(OVERLAY_TO_CAMERA);

        return facing >= 0F ? 1F : -1F;
    }

    private float resolveOverlayViewDistance(Matrix4f viewModel)
    {
        float dx = viewModel.m30();
        float dy = viewModel.m31();
        float dz = viewModel.m32();

        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Camera-facing separation in label plane space. Grows linearly and quadratically with
     * view distance because perspective depth precision collapses coplanar passes when far.
     */
    private float resolveLabelOverlaySeparation(Matrix4f viewModel)
    {
        float distance = this.resolveOverlayViewDistance(viewModel);
        float scale = this.resolveLabelOverlayDistanceScale(viewModel);
        float separation = (FACE_Z_BIAS + OVERLAY_FACE_EXTRA) * scale;

        separation += distance * LABEL_OVERLAY_SEPARATION_LINEAR;
        separation += distance * distance * LABEL_OVERLAY_SEPARATION_QUADRATIC;

        return Math.min(separation, LABEL_OVERLAY_SEPARATION_MAX);
    }

    /**
     * Extra overlay separation when the label is far from the camera — float depth
     * precision shrinks and fixed polygon offset / Z bias collapse to the base pass.
     */
    private float resolveLabelOverlayDistanceScale(Matrix4f viewModel)
    {
        float distance = this.resolveOverlayViewDistance(viewModel);
        float beyond = Math.max(0F, distance - LABEL_OVERLAY_DISTANCE_SCALE_START);
        float linear = beyond * LABEL_OVERLAY_DISTANCE_SCALE_LINEAR;
        float quadratic = beyond * beyond * LABEL_OVERLAY_DISTANCE_SCALE_QUADRATIC;

        return 1F + Math.min(linear + quadratic, LABEL_OVERLAY_DISTANCE_SCALE_CAP);
    }

    private float resolveLabelOverlayOffsetUnits(Matrix4f viewModel, float baseUnits)
    {
        float scaled = baseUnits * this.resolveLabelOverlayDistanceScale(viewModel);

        return Math.max(scaled, LABEL_OVERLAY_OFFSET_UNITS_CAP);
    }

    private void renderShadow(FormRenderingContext context, int x, int y, int w, int h)
    {
        float offset = this.form.offset.get();
        Color color = this.form.background.get().copy();

        color.mul(context.color);

        if (isFullyTransparent(color))
        {
            return;
        }

        context.stack.push();
        context.stack.translate(0, 0, -0.2F);

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        fillQuad(
            builder, context.stack,
            x + w + offset, y - offset, 0,
            x - offset, y - offset, 0,
            x - offset, y + h + offset, 0,
            x + w + offset, y + h + offset, 0,
            color.r, color.g, color.b, color.a
        );

        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        BufferRenderer.drawWithGlobalProgram(builder.end());
        context.stack.pop();
    }

    /**
     * Skip only when opacity is truly zero. Minecraft forces alpha bytes 0–3 to opaque.
     */
    private static boolean isFullyTransparent(Color color)
    {
        return color == null || color.a <= 0F;
    }

    private static int toSafeTextArgb(Color color)
    {
        int argb = color.getARGBColor();
        int alpha = (argb >>> 24) & 0xFF;

        if (color.a > 0F && alpha < MIN_TEXT_ALPHA_BYTE)
        {
            argb = (argb & 0x00FFFFFF) | (MIN_TEXT_ALPHA_BYTE << 24);
        }

        return argb;
    }

    private static final class LabelOverlayLayout
    {
        private float centerX;
        private float centerY;
        private float halfX;
        private float halfY;
    }
}
