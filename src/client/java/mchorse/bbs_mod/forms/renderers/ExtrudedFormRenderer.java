package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.cubic.render.vao.ModelVAO;
import mchorse.bbs_mod.cubic.render.vao.ModelVAOData;
import mchorse.bbs_mod.cubic.render.vao.ModelVAORenderer;
import mchorse.bbs_mod.forms.forms.ExtrudedForm;
import mchorse.bbs_mod.forms.forms.utils.EffectTransform;
import mchorse.bbs_mod.forms.forms.utils.EffectTransformMath;
import mchorse.bbs_mod.forms.forms.utils.GlowSettings;
import mchorse.bbs_mod.forms.forms.utils.PaintSettings;
import mchorse.bbs_mod.forms.forms.utils.TextureBlend;
import mchorse.bbs_mod.forms.renderers.utils.BillboardRenderLayers;
import mchorse.bbs_mod.forms.renderers.utils.FormColorEffects;
import mchorse.bbs_mod.forms.renderers.utils.FormTextureBlendRenderer;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.iris.FormColorGradePatch;
import mchorse.bbs_mod.utils.iris.ShaderOpacityPatch;
import mchorse.bbs_mod.utils.joml.Vectors;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;

import org.lwjgl.opengl.GL11;

import java.util.function.Supplier;

public class ExtrudedFormRenderer extends FormRenderer<ExtrudedForm>
{
    /* Units-only under Iris: negative factor scales with depth slope and punches thin edges through walls. */
    private static final float EXTRUDED_PAINT_OFFSET_FACTOR = 0F;
    private static final float EXTRUDED_PAINT_OFFSET_UNITS = -64F;

    public ExtrudedFormRenderer(ExtrudedForm form)
    {
        super(form);
    }

    private void applyPBRTextureIntensity()
    {
        BBSRendering.setPBRTextureIntensity(this.form.pbrNormalIntensity.get(), this.form.pbrSpecularIntensity.get());
    }

    private void clearPBRTextureIntensity()
    {
        BBSRendering.clearPBRTextureIntensity();
    }

    private void bindFormTexture(Link texture)
    {
        this.applyPBRTextureIntensity();

        try
        {
            BBSModClient.getTextures().bindTexture(texture);
        }
        finally
        {
            this.clearPBRTextureIntensity();
        }
    }

    @Override
    public void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        context.batcher.flush();

        MatrixStack stack = new MatrixStack();

        stack.push();

        Matrix4f uiMatrix = ModelFormRenderer.getUIMatrix(context, x1, y1, x2, y2);

        this.applyTransforms(uiMatrix, context.getTransition());
        MatrixStackUtils.multiply(stack, uiMatrix);
        stack.translate(0F, 1F, 0F);
        stack.scale(1.5F, 1.5F, 4F);
        stack.scale(this.form.uiScale.get(), this.form.uiScale.get(), this.form.uiScale.get());

        /* Shading fix */
        MatrixStackUtils.invertUiNormalY(stack);

        BBSRendering.setupLevelLighting();

        BBSRendering.depthFunc(GL11.GL_LEQUAL);

        ShaderProgram modelShader = BBSRendering.getEntityTranslucentProgram();

        if (modelShader != null)
        {
            this.renderModel(() -> modelShader,
                stack,
                OverlayTexture.DEFAULT_UV, LightmapTextureManager.MAX_LIGHT_COORDINATE, Colors.WHITE,
                context.getTransition(),
                null,
                true,
                false,
                null,
                null
            );
        }

        BBSRendering.depthFunc(GL11.GL_ALWAYS);

        stack.pop();
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        boolean shading = this.form.shading.get();

        if (BBSRendering.isIrisShadersEnabled())
        {
            shading = true;
        }

        PaintSettings paint = this.form.paintSettings.get();
        float paintStrength = paint.resolveIntensity(this.form.paintColor.get());
        boolean irisWorldModelPass = BBSRendering.isIrisWorldModelPass();
        boolean hasColorGrade = this.form.color.get() != null && this.form.color.get().hasColorAdjustments();
        /* PositionTexColor has no PaintColor / FormColorGrade — keep BBS model.fsh when those run. */
        boolean useShadedFormat = shading
            || ((paintStrength != 0F || hasColorGrade) && !irisWorldModelPass);
        Supplier<ShaderProgram> shader = this.getShader(context,
            useShadedFormat ? BBSRendering::getEntityTranslucentProgram : BBSRendering::getPositionTexColorProgram,
            shading ? BBSShaders::getPickerBillboardProgram : BBSShaders::getPickerBillboardNoShadingProgram
        );

        this.renderModel(shader, context.stack, context.overlay, context.light, context.color, context.getTransition(), context.camera, false, context.modelRenderer || context.isPicking(), context.world, context);
    }

    private void renderModel(Supplier<ShaderProgram> shader, MatrixStack matrices, int overlay, int light, int overlayColor, float transition, Camera camera, boolean invertY, boolean modelRenderer, MatrixStack world, FormRenderingContext renderContext)
    {
        Link texture = this.form.texture.get();
        ModelVAOData data = BBSModClient.getTextures().getExtruder().getMesh(texture);

        if (data != null)
        {
            /* World/entity billboard: face the camera and ignore authored rotation.
             * Form/model editor preview (modelRenderer) must keep the real transform so
             * gizmo handles and General translate/rotate/scale fields match what you see. */
            if (this.form.billboard.get() && (renderContext == null || !renderContext.modelRenderer))
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

                if (camera != null && !modelRenderer)
                {
                    matrices.peek().getNormalMatrix().set(camera.view);
                }

                matrices.peek().getNormalMatrix().scale(
                    MatrixStackUtils.safeNormalScaleReciprocal(scale.x),
                    MatrixStackUtils.safeNormalScaleReciprocal(scale.y),
                    MatrixStackUtils.safeNormalScaleReciprocal(scale.z)
                );
            }

            if (renderContext == null || !renderContext.isPicking())
            {
                this.renderSurface(matrices, overlay, light, overlayColor, invertY || modelRenderer);

                return;
            }

            Color color = Colors.COLOR.set(overlayColor, true);
            GameRenderer gameRenderer = MinecraftClient.getInstance().gameRenderer;
            Color storedFormColor = this.form.color.get();
            boolean shadowPass = BBSRendering.isIrisShadowPass();
            boolean ui = modelRenderer;

            this.form.applyFormOpacity(color);
            FormColorEffects.applyShadowPassColorFix(color, storedFormColor, this.form.paintSettings.get(), this.form.paintColor.get(), shadowPass);

            if (color.a <= 0.001F && !shadowPass)
            {
                return;
            }

            GlowSettings glow = this.form.glowSettings.get();
            Color legacyGlow = this.form.glowingColor.get();
            boolean hasGlow = glow.resolveIntensity(legacyGlow) != 0F;
            Color resolvedGlow = new Color();

            glow.resolveColor(legacyGlow, resolvedGlow);

            PaintSettings paint = this.form.paintSettings.get();
            Color legacyPaint = this.form.paintColor.get();
            Color paintColor = new Color();

            paint.resolveColor(legacyPaint, paintColor);

            float paintStrength = paint.resolveIntensity(legacyPaint);

            paintColor.a = paintStrength;

            boolean irisWorldPaintDeferral = BBSRendering.isIrisWorldPaintDeferral();
            boolean paintActive = paintStrength != 0F;
            boolean lowAlphaDefer = BBSRendering.needsIrisTranslucentModelDeferral(color.a);
            boolean noshadingOpacityDefer = BBSRendering.needsIrisNoshadingOpacityDeferral(color.a, this.form.noshadingOpacity.get());
            boolean hasColorAdjustments = storedFormColor != null && storedFormColor.hasColorAdjustments();
            /* Iris entity shaders have no PaintColor / FormColorGrade. Live paint/grade overlays
             * also fail LEQUAL on extruded slabs under pack depth. Redraw once post-composite
             * with BBS model.fsh (same as no-shader) so both effects work. Keep depth test so
             * buried faces stay occluded. */
            boolean forceIrisEffectDeferred = irisWorldPaintDeferral && !shadowPass && !ui
                && (paintActive || hasColorAdjustments);
            boolean deferTranslucentModel = lowAlphaDefer || noshadingOpacityDefer || forceIrisEffectDeferred;
            boolean deferPaintToOverlay = paintActive && irisWorldPaintDeferral && !deferTranslucentModel;
            Supplier<ShaderProgram> renderShader = shader;
            boolean bbsModelShader = !BBSRendering.isIrisWorldModelPass() || deferTranslucentModel;
            /* No-shader / UI / Iris effect deferred: FormColorGrade in model.fsh. */
            boolean useFormColorGrade = hasColorAdjustments
                && !shadowPass
                && (!irisWorldPaintDeferral || deferTranslucentModel || ui);
            /* Complementary/BSL live Iris pass only when we are not forcing a BBS redraw. */
            boolean livePackGrade = hasColorAdjustments
                && irisWorldPaintDeferral
                && !shadowPass
                && !deferTranslucentModel
                && FormColorGradePatch.canUseLivePackGrade();
            /* Other Iris packs without force-deferred: scene-copy ColorGradeOverlay. */
            boolean useColorGradeOverlay = hasColorAdjustments
                && irisWorldPaintDeferral
                && !shadowPass
                && !deferTranslucentModel
                && !livePackGrade;
            boolean uploadGrade = useFormColorGrade || livePackGrade;
            Color formColor = (uploadGrade || useColorGradeOverlay)
                ? storedFormColor.copyDeferringColorGrade()
                : storedFormColor.copyBakingColorGrade();

            color.mul(formColor);

            boolean syncedGlow = hasGlow && glow.resolveSync();
            boolean shaderOverlay = irisWorldPaintDeferral && syncedGlow && !paintActive && !deferTranslucentModel;
            boolean deferGlowToOverlay = shaderOverlay;
            boolean paintOnlyGlow = glow.resolvePaintOnly();
            boolean stripMainPassGlow = deferGlowToOverlay || (deferPaintToOverlay && hasGlow && paintOnlyGlow);
            float gradeBrightnessSnapshot = storedFormColor.brightness;
            float gradeContrastSnapshot = storedFormColor.contrast;
            float gradeHueSnapshot = storedFormColor.hue;
            float gradeSaturationSnapshot = storedFormColor.saturation;

            if (!bbsModelShader && !shaderOverlay && !deferPaintToOverlay && !paintOnlyGlow && !deferTranslucentModel)
            {
                FormColorEffects.blendFormGlowBrighten(color, glow, legacyGlow);
            }

            Matrix4f formRootInverse = new Matrix4f();
            Vector3f paintMaskHalf = new Vector3f();

            EffectTransformMath.resolveBillboardMaskHalfExtents(paint.transform, paintMaskHalf);

            EffectTransform paintTransformSnapshot = paint.transform.copy();
            Vector3f paintMaskHalfSnapshot = new Vector3f(paintMaskHalf);

            if (paintActive && bbsModelShader)
            {
                ModelVAORenderer.setPaintEffectTransform(formRootInverse, paint.transform, paintMaskHalf, false);
            }

            EffectTransform glowTransform = FormColorEffects.resolveGlowEffectTransform(glow, legacyGlow);
            Vector3f glowMaskHalf = new Vector3f();
            EffectTransformMath.resolveBillboardMaskHalfExtents(glowTransform, glowMaskHalf);
            EffectTransform glowTransformSnapshot = glowTransform == null ? null : glowTransform.copy();
            Vector3f glowMaskHalfSnapshot = new Vector3f(glowMaskHalf);

            if (hasGlow && bbsModelShader)
            {
                ModelVAORenderer.setGlowEffectTransform(formRootInverse, glowTransform, glowMaskHalf, false);
            }

            /* Only upload grade on the live path — deferred callback re-sets its own snapshot. */
            if (uploadGrade && !deferTranslucentModel)
            {
                ModelVAORenderer.setFormColorGrade(gradeBrightnessSnapshot, gradeContrastSnapshot, gradeHueSnapshot, gradeSaturationSnapshot);
                ModelVAORenderer.setGradeEffectTransforms(storedFormColor);
            }
            else if (!deferTranslucentModel)
            {
                ModelVAORenderer.clearFormColorGrade();
            }

            BBSRendering.enableBlend();
            BBSRendering.defaultBlendFunc();

            if (deferTranslucentModel)
            {
                /* No Iris depth stamp — same as ModelForm: punching depth would erase entities behind. */
                ModelVAORenderer.setPaint(paintActive ? paintColor.r : 0F, paintActive ? paintColor.g : 0F, paintActive ? paintColor.b : 0F, paintActive ? paintStrength : 0F);

                if (hasGlow)
                {
                    ModelVAORenderer.setGlow(glow, resolvedGlow.r, resolvedGlow.g, resolvedGlow.b, legacyGlow);
                }
                else
                {
                    ModelVAORenderer.clearGlowing();
                }

                Matrix4f positionMatrix = ModelVAORenderer.capturePaintOverlayRootMatrix(new Matrix4f(matrices.peek().getPositionMatrix()));
                Matrix3f normalMatrix = new Matrix3f(matrices.peek().getNormalMatrix());
                TextureBlend textureBlendSnapshot = this.form.textureBlend == null ? null : new TextureBlend(this.form.textureBlend.from, this.form.textureBlend.to, this.form.textureBlend.blend);
                boolean useShaderBlend = FormTextureBlendRenderer.isBlending(this.form.textureBlend);
                float ca = lowAlphaDefer
                    ? BBSRendering.easeDeferredModelAlpha(color.a)
                    : color.a;
                final float cr;
                final float cg;
                final float cb;

                if (lowAlphaDefer && !noshadingOpacityDefer)
                {
                    cr = 0F;
                    cg = 0F;
                    cb = 0F;
                }
                else
                {
                    cr = color.r;
                    cg = color.g;
                    cb = color.b;
                }
                int overlayLight = light;
                int overlayOverlay = overlay;
                boolean paintActiveSnapshot = paintActive;
                float pr = paintColor.r;
                float pg = paintColor.g;
                float pb = paintColor.b;
                float pa = paintStrength;

                /* Extruded has real thickness — keep depth test/write so terrain occludes
                 * buried faces. (Billboards use depthTest=false; that made paint show through grass.) */
                boolean deferredDepthWrite = ShaderOpacityPatch.shouldWriteDepthForOpacity(ca);

                ModelVAORenderer.submitDeferredTranslucentModel(() ->
                {
                    try
                    {
                        if (paintActiveSnapshot)
                        {
                            ModelVAORenderer.setPaintEffectTransform(new Matrix4f().identity(), paintTransformSnapshot, paintMaskHalfSnapshot, false);
                            ModelVAORenderer.setPaint(pr, pg, pb, pa);
                        }
                        else
                        {
                            ModelVAORenderer.setPaint(0F, 0F, 0F, 0F);
                        }

                        if (hasGlow)
                        {
                            ModelVAORenderer.setGlowEffectTransform(new Matrix4f().identity(), glowTransformSnapshot, glowMaskHalfSnapshot, false);
                            ModelVAORenderer.setGlow(glow, resolvedGlow.r, resolvedGlow.g, resolvedGlow.b, legacyGlow);
                        }
                        else
                        {
                            ModelVAORenderer.clearGlowing();
                        }

                        if (uploadGrade)
                        {
                            ModelVAORenderer.setFormColorGrade(gradeBrightnessSnapshot, gradeContrastSnapshot, gradeHueSnapshot, gradeSaturationSnapshot);
                            ModelVAORenderer.setGradeEffectTransforms(storedFormColor);
                        }

                        MatrixStack overlayStack = new MatrixStack();

                        overlayStack.peek().getPositionMatrix().set(positionMatrix);
                        overlayStack.peek().getNormalMatrix().set(normalMatrix);

                        /* Full-mesh Iris effect redraw with BBS model.fsh (paint + FormColorGrade).
                         * Mild self-bias only — keep world depth so terrain still occludes. */
                        this.renderExtrudedOverlayPass(useShaderBlend, textureBlendSnapshot, texture, overlayStack, cr, cg, cb, ca, overlayLight, overlayOverlay, paintActiveSnapshot || uploadGrade);
                    }
                    finally
                    {
                        ModelVAORenderer.clearPaintEffectTransform();
                        ModelVAORenderer.clearPaint();
                        ModelVAORenderer.clearGlowing();
                        ModelVAORenderer.clearFormColorGrade();
                    }
                }, deferredDepthWrite, true);

                ModelVAORenderer.clearFormColorGrade();
            }
            else if (deferPaintToOverlay)
            {
                ModelVAORenderer.setPaint(0F, 0F, 0F, 0F);
            }
            else if (paintActive)
            {
                ModelVAORenderer.setPaint(paintColor.r, paintColor.g, paintColor.b, paintStrength);
            }
            else
            {
                ModelVAORenderer.setPaint(0F, 0F, 0F, 0F);
            }

            if (!deferTranslucentModel)
            {
            TextureBlend textureBlend = this.form.textureBlend;
            boolean useShaderBlend = bbsModelShader && FormTextureBlendRenderer.isBlending(textureBlend);
            TextureBlend textureBlendSnapshot = textureBlend == null ? null : new TextureBlend(textureBlend.from, textureBlend.to, textureBlend.blend);
            float opacityAlpha = color.a;
            boolean localPreview = modelRenderer
                || (renderContext != null && renderContext.isLocalPreview());

            if (!localPreview && ShaderOpacityPatch.shouldDelayUntilPostDeferred(opacityAlpha))
            {
                boolean irisCamera = BBSRendering.isIrisWorldModelPass() && !bbsModelShader;
                Matrix4f positionMatrix = irisCamera
                    ? new Matrix4f(matrices.peek().getPositionMatrix())
                    : ModelVAORenderer.capturePaintOverlayRootMatrix(new Matrix4f(matrices.peek().getPositionMatrix()));
                Matrix3f normalMatrix = new Matrix3f(matrices.peek().getNormalMatrix());
                Color colorSnapshot = color.copy();
                Color paintSnapshot = paintColor.copy();
                float paintStrengthSnapshot = paintStrength;
                boolean paintActiveSnapshot = paintActive;
                boolean stripGlowSnapshot = stripMainPassGlow;
                boolean hasGlowSnapshot = hasGlow;
                boolean paintOnlyGlowSnapshot = paintOnlyGlow;
                boolean deferPaintSnapshot = deferPaintToOverlay;
                boolean shaderOverlaySnapshot = shaderOverlay;
                GlowSettings glowSnapshot = glow.copy();
                Color resolvedGlowSnapshot = resolvedGlow.copy();
                Color legacyGlowSnapshot = legacyGlow.copy();
                Link textureSnapshot = texture;
                Supplier<ShaderProgram> shaderSnapshot = irisCamera ? renderShader : BBSShaders::getModel;
                int overlayLight = light;
                int overlayOverlay = overlay;
                EffectTransform paintTransformQueued = paintTransformSnapshot;
                Vector3f paintMaskHalfQueued = paintMaskHalfSnapshot;
                EffectTransform glowTransformQueued = glowTransformSnapshot;
                Vector3f glowMaskHalfQueued = glowMaskHalfSnapshot;
                boolean depthWrite = ShaderOpacityPatch.shouldWriteDepthForOpacity(opacityAlpha);
                boolean afterFluids = ShaderOpacityPatch.shouldFlushAfterFluids(opacityAlpha);
                boolean uploadGradeSnapshot = uploadGrade;
                Color gradeTransformsSnapshot = storedFormColor;
                Runnable deferredDraw = () ->
                {
                    MatrixStack overlayStack = new MatrixStack();

                    overlayStack.peek().getPositionMatrix().set(positionMatrix);
                    overlayStack.peek().getNormalMatrix().set(normalMatrix);

                    try
                    {
                        if (paintActiveSnapshot && !deferPaintSnapshot)
                        {
                            ModelVAORenderer.setPaintEffectTransform(new Matrix4f().identity(), paintTransformQueued, paintMaskHalfQueued, false);
                            ModelVAORenderer.setPaint(paintSnapshot.r, paintSnapshot.g, paintSnapshot.b, paintStrengthSnapshot);
                        }
                        else
                        {
                            ModelVAORenderer.setPaint(0F, 0F, 0F, 0F);
                        }

                        if (stripGlowSnapshot)
                        {
                            GlowSettings glowOff = glowSnapshot.copy();

                            glowOff.intensity = 0F;
                            ModelVAORenderer.setGlow(glowOff, resolvedGlowSnapshot.r, resolvedGlowSnapshot.g, resolvedGlowSnapshot.b, legacyGlowSnapshot);
                        }
                        else if (hasGlowSnapshot)
                        {
                            ModelVAORenderer.setGlow(glowSnapshot, resolvedGlowSnapshot.r, resolvedGlowSnapshot.g, resolvedGlowSnapshot.b, legacyGlowSnapshot);
                        }
                        else
                        {
                            ModelVAORenderer.clearGlowing();
                        }

                        if (uploadGradeSnapshot)
                        {
                            ModelVAORenderer.setFormColorGrade(gradeBrightnessSnapshot, gradeContrastSnapshot, gradeHueSnapshot, gradeSaturationSnapshot);
                            ModelVAORenderer.setGradeEffectTransforms(gradeTransformsSnapshot);
                        }

                        if (useShaderBlend)
                        {
                            Link fromTexture = FormTextureBlendRenderer.resolveFrom(textureBlendSnapshot, textureSnapshot);
                            Link toTexture = FormTextureBlendRenderer.resolveTo(textureBlendSnapshot, textureSnapshot);
                            ModelVAO fromData = BBSModClient.getTextures().getExtruder().get(fromTexture);

                            if (fromData != null)
                            {
                                ModelVAORenderer.setTextureBlend(toTexture, textureBlendSnapshot.blend);

                                try
                                {
                                    this.bindFormTexture(fromTexture);
                                    ModelVAORenderer.render(shaderSnapshot.get(), fromData, overlayStack, colorSnapshot.r, colorSnapshot.g, colorSnapshot.b, colorSnapshot.a, overlayLight, overlayOverlay);
                                }
                                finally
                                {
                                    ModelVAORenderer.clearTextureBlend();
                                }
                            }
                        }
                        else
                        {
                            FormTextureBlendRenderer.draw(textureBlendSnapshot, textureSnapshot, (link, alphaFactor) ->
                            {
                                ModelVAO passData = BBSModClient.getTextures().getExtruder().get(link);

                                if (passData == null)
                                {
                                    return;
                                }

                                Color passColor = colorSnapshot.copy();

                                passColor.a *= alphaFactor;
                                this.bindFormTexture(link);
                                ModelVAORenderer.render(shaderSnapshot.get(), passData, overlayStack, passColor.r, passColor.g, passColor.b, passColor.a, overlayLight, overlayOverlay);
                            });
                        }

                        if (deferPaintSnapshot)
                        {
                            if (hasGlowSnapshot)
                            {
                                ModelVAORenderer.setGlowEffectTransform(new Matrix4f().identity(), glowTransformQueued, glowMaskHalfQueued, false);
                                ModelVAORenderer.setGlow(glowSnapshot, resolvedGlowSnapshot.r, resolvedGlowSnapshot.g, resolvedGlowSnapshot.b, legacyGlowSnapshot);
                            }
                            else
                            {
                                GlowSettings glowOff = glowSnapshot.copy();

                                glowOff.intensity = 0F;
                                ModelVAORenderer.setGlow(glowOff, resolvedGlowSnapshot.r, resolvedGlowSnapshot.g, resolvedGlowSnapshot.b, legacyGlowSnapshot);
                            }

                            /* Post-deferred path never entered beginPaintOverlayPass — without it
                             * PaintOverlay=0 and thin extruded slabs fail LEQUAL depth. */
                            ModelVAORenderer.beginPaintOverlayPass(false);

                            try
                            {
                                ModelVAORenderer.setPaint(paintSnapshot.r, paintSnapshot.g, paintSnapshot.b, paintStrengthSnapshot);
                                ModelVAORenderer.setPaintEffectTransform(new Matrix4f().identity(), paintTransformQueued, paintMaskHalfQueued, false);
                                this.renderExtrudedOverlayPass(useShaderBlend, textureBlendSnapshot, textureSnapshot, overlayStack, colorSnapshot.r, colorSnapshot.g, colorSnapshot.b, colorSnapshot.a, overlayLight, overlayOverlay, true);

                                if (hasGlowSnapshot && !paintOnlyGlowSnapshot)
                                {
                                    ModelVAORenderer.setPaint(0F, 0F, 0F, 0F);
                                    ModelVAORenderer.setGlowEffectTransform(new Matrix4f().identity(), glowTransformQueued, glowMaskHalfQueued, false);
                                    ModelVAORenderer.setGlow(glowSnapshot, resolvedGlowSnapshot.r, resolvedGlowSnapshot.g, resolvedGlowSnapshot.b, legacyGlowSnapshot);
                                    BBSRendering.enableBlend();
                                    BBSRendering.depthMask(false);
                                    BBSRendering.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

                                    try
                                    {
                                        this.renderExtrudedOverlayPass(useShaderBlend, textureBlendSnapshot, textureSnapshot, overlayStack, 0F, 0F, 0F, colorSnapshot.a, LightmapTextureManager.MAX_LIGHT_COORDINATE, overlayOverlay, true);
                                    }
                                    finally
                                    {
                                        BBSRendering.depthMask(true);
                                        BBSRendering.defaultBlendFunc();
                                    }
                                }
                            }
                            finally
                            {
                                ModelVAORenderer.endPaintOverlayPass();
                            }
                        }
                        else if (shaderOverlaySnapshot)
                        {
                            ModelVAORenderer.setPaint(0F, 0F, 0F, 0F);
                            ModelVAORenderer.setGlowEffectTransform(new Matrix4f().identity(), glowTransformQueued, glowMaskHalfQueued, false);
                            ModelVAORenderer.setGlow(glowSnapshot, resolvedGlowSnapshot.r, resolvedGlowSnapshot.g, resolvedGlowSnapshot.b, legacyGlowSnapshot);
                            BBSRendering.enableBlend();
                            BBSRendering.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

                            try
                            {
                                this.renderExtrudedOverlayPass(useShaderBlend, textureBlendSnapshot, textureSnapshot, overlayStack, 0F, 0F, 0F, colorSnapshot.a, LightmapTextureManager.MAX_LIGHT_COORDINATE, overlayOverlay, true);
                            }
                            finally
                            {
                                BBSRendering.defaultBlendFunc();
                            }
                        }
                    }
                    finally
                    {
                        ModelVAORenderer.clearPaintEffectTransform();
                        ModelVAORenderer.clearPaint();
                        ModelVAORenderer.clearGlowing();
                        ModelVAORenderer.clearTextureBlend();
                        ModelVAORenderer.clearFormColorGrade();
                    }
                };

                if (irisCamera)
                {
                    ShaderOpacityPatch.submitPostDeferredForm(0D, 0D, depthWrite, afterFluids, deferredDraw);
                }
                else
                {
                    ShaderOpacityPatch.submitPostDeferredBbsForm(0D, 0D, depthWrite, afterFluids, deferredDraw);
                }

                ModelVAORenderer.clearPaintEffectTransform();
                ModelVAORenderer.clearPaint();
                ModelVAORenderer.clearGlowing();
                ModelVAORenderer.clearFormColorGrade();
            }
            else
            {
            boolean forceDepth = ShaderOpacityPatch.shouldForceLiveDepthWrite(opacityAlpha);
            boolean suppressDepth = ShaderOpacityPatch.shouldSuppressDepthWrite(opacityAlpha);
            boolean savedDepthMask = false;

            if (forceDepth || suppressDepth)
            {
                savedDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
                BBSRendering.enableDepthTest();

                if (forceDepth)
                {
                    ShaderOpacityPatch.setForceLiveDepthWrite(true);
                    BBSRendering.depthMask(true);
                }
                else
                {
                    ShaderOpacityPatch.setSuppressLiveDepthWrite(true);
                    BBSRendering.depthMask(false);
                }
            }

            if (stripMainPassGlow)
            {
                GlowSettings glowOff = glow.copy();

                glowOff.intensity = 0F;
                ModelVAORenderer.setGlow(glowOff, resolvedGlow.r, resolvedGlow.g, resolvedGlow.b, legacyGlow);
            }
            else if (hasGlow)
            {
                ModelVAORenderer.setGlow(glow, resolvedGlow.r, resolvedGlow.g, resolvedGlow.b, legacyGlow);
            }

            try
            {
                if (useShaderBlend)
                {
                    Link fromTexture = FormTextureBlendRenderer.resolveFrom(textureBlend, texture);
                    Link toTexture = FormTextureBlendRenderer.resolveTo(textureBlend, texture);
                    ModelVAO fromData = BBSModClient.getTextures().getExtruder().get(fromTexture);

                    if (fromData != null)
                    {
                        ModelVAORenderer.setTextureBlend(toTexture, textureBlend.blend);

                        try
                        {
                            this.bindFormTexture(fromTexture);
                            ModelVAORenderer.render(renderShader.get(), fromData, matrices, color.r, color.g, color.b, color.a, light, overlay);
                        }
                        finally
                        {
                            ModelVAORenderer.clearTextureBlend();
                        }
                    }
                }
                else
                {
                    FormTextureBlendRenderer.draw(textureBlend, texture, (link, alphaFactor) ->
                    {
                        ModelVAO passData = BBSModClient.getTextures().getExtruder().get(link);

                        if (passData == null)
                        {
                            return;
                        }

                        Color passColor = color.copy();

                        passColor.a *= alphaFactor;
                        this.bindFormTexture(link);
                        ModelVAORenderer.render(renderShader.get(), passData, matrices, passColor.r, passColor.g, passColor.b, passColor.a, light, overlay);
                    });
                }

                if (deferPaintToOverlay)
                {
                    Matrix4f positionMatrix = ModelVAORenderer.capturePaintOverlayRootMatrix(new Matrix4f(matrices.peek().getPositionMatrix()));
                    Matrix3f normalMatrix = new Matrix3f(matrices.peek().getNormalMatrix());
                    float cr = color.r;
                    float cg = color.g;
                    float cb = color.b;
                    float ca = color.a;
                    float pr = paintColor.r;
                    float pg = paintColor.g;
                    float pb = paintColor.b;
                    float pa = paintStrength;
                    int overlayLight = light;
                    int overlayOverlay = overlay;

                    ModelVAORenderer.submitPaintOverlay(false, () ->
                    {
                        /* Mild bias vs Iris-lit self surface — keep depth test so grass occludes. */
                        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
                        GL11.glPolygonOffset(EXTRUDED_PAINT_OFFSET_FACTOR, EXTRUDED_PAINT_OFFSET_UNITS);

                        if (hasGlow)
                        {
                            ModelVAORenderer.setGlowEffectTransform(new Matrix4f().identity(), glowTransformSnapshot, glowMaskHalfSnapshot, false);
                            ModelVAORenderer.setGlow(glow, resolvedGlow.r, resolvedGlow.g, resolvedGlow.b, legacyGlow);
                        }
                        else
                        {
                            GlowSettings glowOff = glow.copy();

                            glowOff.intensity = 0F;
                            ModelVAORenderer.setGlow(glowOff, resolvedGlow.r, resolvedGlow.g, resolvedGlow.b, legacyGlow);
                        }

                        ModelVAORenderer.setPaint(pr, pg, pb, pa);

                        try
                        {
                            ModelVAORenderer.setPaintEffectTransform(new Matrix4f().identity(), paintTransformSnapshot, paintMaskHalfSnapshot, false);

                            MatrixStack overlayStack = new MatrixStack();

                            overlayStack.peek().getPositionMatrix().set(positionMatrix);
                            overlayStack.peek().getNormalMatrix().set(normalMatrix);

                            this.renderExtrudedOverlayPass(useShaderBlend, textureBlendSnapshot, texture, overlayStack, cr, cg, cb, ca, overlayLight, overlayOverlay, true);

                            if (hasGlow && !paintOnlyGlow)
                            {
                                ModelVAORenderer.runWithPaintOverlayPass(false, () ->
                                {
                                    ModelVAORenderer.setPaint(0F, 0F, 0F, 0F);
                                    ModelVAORenderer.setGlowEffectTransform(new Matrix4f().identity(), glowTransformSnapshot, glowMaskHalfSnapshot, false);
                                    ModelVAORenderer.setGlow(glow, resolvedGlow.r, resolvedGlow.g, resolvedGlow.b, legacyGlow);

                                    BBSRendering.enableBlend();
                                    BBSRendering.depthMask(false);
                                    BBSRendering.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

                                    try
                                    {
                                        this.renderExtrudedOverlayPass(useShaderBlend, textureBlendSnapshot, texture, overlayStack, 0F, 0F, 0F, ca, LightmapTextureManager.MAX_LIGHT_COORDINATE, overlayOverlay, true);
                                    }
                                    finally
                                    {
                                        BBSRendering.depthMask(true);
                                        BBSRendering.defaultBlendFunc();
                                    }
                                });
                            }
                        }
                        finally
                        {
                            ModelVAORenderer.clearPaintEffectTransform();
                            ModelVAORenderer.clearPaint();
                            ModelVAORenderer.clearGlowing();
                        }
                    });
                }
                else if (shaderOverlay)
                {
                    Matrix4f positionMatrix = ModelVAORenderer.capturePaintOverlayRootMatrix(new Matrix4f(matrices.peek().getPositionMatrix()));
                    Matrix3f normalMatrix = new Matrix3f(matrices.peek().getNormalMatrix());
                    float cr = color.r;
                    float cg = color.g;
                    float cb = color.b;
                    float ca = color.a;
                    int overlayLight = light;
                    int overlayOverlay = overlay;

                    ModelVAORenderer.submitPaintOverlay(false, () ->
                    {
                        try
                        {
                            MatrixStack overlayStack = new MatrixStack();

                            overlayStack.peek().getPositionMatrix().set(positionMatrix);
                            overlayStack.peek().getNormalMatrix().set(normalMatrix);

                            ModelVAORenderer.runWithPaintOverlayPass(false, () ->
                            {
                                ModelVAORenderer.setPaint(0F, 0F, 0F, 0F);
                                ModelVAORenderer.setGlowEffectTransform(new Matrix4f().identity(), glowTransformSnapshot, glowMaskHalfSnapshot, false);
                                ModelVAORenderer.setGlow(glow, resolvedGlow.r, resolvedGlow.g, resolvedGlow.b, legacyGlow);

                                BBSRendering.enableBlend();
                                BBSRendering.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

                                try
                                {
                                    this.renderExtrudedOverlayPass(useShaderBlend, textureBlendSnapshot, texture, overlayStack, 0F, 0F, 0F, ca, LightmapTextureManager.MAX_LIGHT_COORDINATE, overlayOverlay, true);
                                }
                                finally
                                {
                                    BBSRendering.defaultBlendFunc();
                                }
                            });
                        }
                        finally
                        {
                            ModelVAORenderer.clearPaint();
                            ModelVAORenderer.clearGlowing();
                        }
                    });
                }

                if (useColorGradeOverlay)
                {
                    Matrix4f positionMatrix = ModelVAORenderer.capturePaintOverlayRootMatrix(new Matrix4f(matrices.peek().getPositionMatrix()));
                    Matrix3f normalMatrix = new Matrix3f(matrices.peek().getNormalMatrix());
                    Color colorSnapshot = color.copy();
                    TextureBlend textureBlendSnapshotFinal = textureBlendSnapshot;
                    boolean useShaderBlendFinal = useShaderBlend;
                    Link textureSnapshot = texture;
                    int overlayLight = light;
                    int overlayOverlay = overlay;

                    ModelVAORenderer.submitColorGradeOverlay(() ->
                    {
                        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
                        GL11.glPolygonOffset(EXTRUDED_PAINT_OFFSET_FACTOR, EXTRUDED_PAINT_OFFSET_UNITS);

                        try
                        {
                            ModelVAORenderer.setFormColorGrade(gradeBrightnessSnapshot, gradeContrastSnapshot, gradeHueSnapshot, gradeSaturationSnapshot);
                            ModelVAORenderer.setGradeEffectTransforms(storedFormColor);
                            ModelVAORenderer.clearPaint();
                            ModelVAORenderer.clearGlowing();

                            MatrixStack overlayStack = new MatrixStack();

                            overlayStack.peek().getPositionMatrix().set(positionMatrix);
                            overlayStack.peek().getNormalMatrix().set(normalMatrix);

                            this.renderExtrudedOverlayPass(useShaderBlendFinal, textureBlendSnapshotFinal, textureSnapshot, overlayStack, colorSnapshot.r, colorSnapshot.g, colorSnapshot.b, colorSnapshot.a, overlayLight, overlayOverlay, true);
                        }
                        finally
                        {
                            ModelVAORenderer.clearFormColorGrade();
                            ModelVAORenderer.clearPaint();
                            ModelVAORenderer.clearGlowing();
                        }
                    });
                }
            }
            finally
            {
                ModelVAORenderer.clearPaintEffectTransform();
                ModelVAORenderer.clearPaint();
                ModelVAORenderer.clearGlowing();
                ModelVAORenderer.clearFormColorGrade();

                if (forceDepth)
                {
                    ShaderOpacityPatch.setForceLiveDepthWrite(false);
                    BBSRendering.depthMask(savedDepthMask);
                }
                else if (suppressDepth)
                {
                    ShaderOpacityPatch.setSuppressLiveDepthWrite(false);
                    BBSRendering.depthMask(savedDepthMask);
                }
            }
            }
            }

            BBSRendering.defaultBlendFunc();
            BBSRendering.disableBlend();
        }
    }

    private void renderSurface(MatrixStack matrices, int overlay, int light, int overlayColor, boolean preview)
    {
        Color color = new Color().set(overlayColor, true);

        this.form.applyFormOpacity(color);
        color.mul(this.form.color.get().copyBakingColorGrade());

        if (color.a <= 0.001F)
        {
            return;
        }

        /* Keep the CPU extrusion, including the side faces along opaque pixel edges.
         * Bake transforms into vertices; RenderLayer supplies the draw-time uniform buffers. */
        boolean shaded = this.form.shading.get();
        VertexFormat format = shaded ? VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL : VertexFormats.POSITION_TEXTURE_COLOR;
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f position = entry.getPositionMatrix();

        FormTextureBlendRenderer.draw(this.form.textureBlend, this.form.texture.get(), (link, alphaFactor) ->
        {
            ModelVAOData mesh = BBSModClient.getTextures().getExtruder().getMesh(link);
            Texture texture = BBSModClient.getTextures().getTexture(link);

            if (mesh == null || texture == null || mesh.vertices().length == 0)
            {
                return;
            }

            float alpha = color.a * alphaFactor;
            float[] vertices = mesh.vertices();
            float[] normals = mesh.normals();
            float[] uvs = mesh.texCoords();
            BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, format);

            for (int vertex = 0; vertex < vertices.length / 3; vertex++)
            {
                int xyz = vertex * 3;
                int uv = vertex * 2;
                VertexConsumer consumer = builder.vertex(position, vertices[xyz], vertices[xyz + 1], vertices[xyz + 2])
                    .color(color.r, color.g, color.b, alpha).texture(uvs[uv], uvs[uv + 1]);

                if (shaded)
                {
                    consumer.overlay(overlay).light(light).normal(entry, normals[xyz], normals[xyz + 1], normals[xyz + 2]);
                }
            }

            texture.bind(0);
            texture.setFilterMipmap(false, false);
            BillboardRenderLayers.draw(builder.end(), texture, false, false,
                preview || alpha >= ShaderOpacityPatch.LIVE_DEPTH_WRITE_ALPHA, false);
        });
    }

    private void renderExtrudedOverlayPass(boolean useShaderBlend, TextureBlend textureBlendSnapshot, Link texture, MatrixStack overlayStack, float cr, float cg, float cb, float ca, int overlayLight, int overlayOverlay, boolean depthBias)
    {
        /* Extruded slabs are ~1/16 thick. Mild bias beats self z-fight with the Iris-lit
         * surface; billboard-scale units (-32) pull fragments through nearby grass/terrain. */
        boolean savedPolygonOffsetFill = false;

        if (depthBias)
        {
            savedPolygonOffsetFill = GL11.glGetBoolean(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glPolygonOffset(EXTRUDED_PAINT_OFFSET_FACTOR, EXTRUDED_PAINT_OFFSET_UNITS);
        }

        try
        {
            if (useShaderBlend && textureBlendSnapshot != null)
            {
                Link fromTexture = FormTextureBlendRenderer.resolveFrom(textureBlendSnapshot, texture);
                Link toTexture = FormTextureBlendRenderer.resolveTo(textureBlendSnapshot, texture);
                ModelVAO fromData = BBSModClient.getTextures().getExtruder().get(fromTexture);

                if (fromData != null)
                {
                    ModelVAORenderer.setTextureBlend(toTexture, textureBlendSnapshot.blend);

                    try
                    {
                        this.bindFormTexture(fromTexture);
                        ModelVAORenderer.render(BBSShaders.getModel(), fromData, overlayStack, cr, cg, cb, ca, overlayLight, overlayOverlay);
                    }
                    finally
                    {
                        ModelVAORenderer.clearTextureBlend();
                    }
                }
            }
            else
            {
                FormTextureBlendRenderer.draw(textureBlendSnapshot, texture, (link, alphaFactor) ->
                {
                    ModelVAO passData = BBSModClient.getTextures().getExtruder().get(link);

                    if (passData == null)
                    {
                        return;
                    }

                    this.bindFormTexture(link);
                    ModelVAORenderer.render(BBSShaders.getModel(), passData, overlayStack, cr, cg, cb, ca * alphaFactor, overlayLight, overlayOverlay);
                });
            }
        }
        finally
        {
            if (depthBias)
            {
                if (ModelVAORenderer.isPaintOverlayPass() || ModelVAORenderer.isColorGradeOverlayPass())
                {
                    GL11.glPolygonOffset(EXTRUDED_PAINT_OFFSET_FACTOR, EXTRUDED_PAINT_OFFSET_UNITS);
                }
                else
                {
                    GL11.glPolygonOffset(0F, 0F);

                    if (!savedPolygonOffsetFill)
                    {
                        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
                    }
                }
            }
        }
    }
}
