package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.cubic.render.vao.ModelVAORenderer;
import mchorse.bbs_mod.forms.forms.BillboardForm;
import mchorse.bbs_mod.forms.forms.utils.EffectTransform;
import mchorse.bbs_mod.forms.forms.utils.EffectTransformMath;
import mchorse.bbs_mod.forms.forms.utils.GlowSettings;
import mchorse.bbs_mod.forms.forms.utils.PaintSettings;
import mchorse.bbs_mod.forms.renderers.utils.FlatColorTintOverlayPass;
import mchorse.bbs_mod.forms.renderers.utils.FlatGlowOverlayPass;
import mchorse.bbs_mod.forms.renderers.utils.FlatPaintOverlayPass;
import mchorse.bbs_mod.forms.renderers.utils.FormColorEffects;
import mchorse.bbs_mod.forms.renderers.utils.FormTextureBlendRenderer;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.Quad;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.iris.ShaderOpacityPatch;
import mchorse.bbs_mod.utils.joml.Vectors;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.mojang.blaze3d.systems.RenderSystem;

import org.lwjgl.opengl.GL11;

import java.util.function.Supplier;

public class BillboardFormRenderer extends FormRenderer<BillboardForm>
{
    private static final Quad quad = new Quad();
    private static final Quad uvQuad = new Quad();

    private static final Matrix4f matrix = new Matrix4f();
    /* Used by paint/glow camera-facing offset and face sort keys — not for dual ±Z base meshes.
     * Base two-sided look: both windings at z=0 with cull on (see drawBillboardFaces). */
    private static final float FACE_Z_BIAS = 0.0005F;

    /* Paint/glow overlays sit further outward along each face normal so back faces are not
     * pushed through the base geometry (a shared +Z translate caused near-camera z-fighting). */
    private static final float GLOW_FACE_Z_BIAS = 0.002F;

    /* Paint/glow sit just outside the camera-facing base face (not mid-plane, not ±dual).
     * Mid-plane lost depth to the nearer base face when close/angled; dual faces split the
     * silhouette. Camera-facing single plane + polygon offset stays in front from either side. */
    private static final float OVERLAY_FACE_EXTRA = 0.0015F;
    private static final Vector3f OVERLAY_TO_CAMERA = new Vector3f();
    private static final Vector3f OVERLAY_LOCAL_Z = new Vector3f();
    private static final Vector3f MASK_HALF = new Vector3f();


    public BillboardFormRenderer(BillboardForm form)
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

    private void bindFormTexture(Texture texture)
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
        MatrixStack stack = context.batcher.getContext().getMatrices();

        stack.push();

        Matrix4f uiMatrix = ModelFormRenderer.getUIMatrix(context, x1, y1, x2, y2);

        this.applyTransforms(uiMatrix, context.getTransition());
        MatrixStackUtils.multiply(stack, uiMatrix);
        stack.translate(0F, 1F, 0F);
        stack.scale(1.5F, 1.5F, 1.5F);
        stack.scale(this.form.uiScale.get(), this.form.uiScale.get(), this.form.uiScale.get());

        VertexFormat format = VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL;

        this.renderModel(format, GameRenderer::getRenderTypeEntityTranslucentProgram,
            stack,
            OverlayTexture.DEFAULT_UV, LightmapTextureManager.MAX_LIGHT_COORDINATE, Colors.WHITE,
            context.getTransition(),
            null,
            true,
            false,
            null
        );

        stack.pop();
    }

    @Override
    public void render3D(FormRenderingContext context)
    {
        /* Do not force shading under Iris — camera-facing normals + pack/BBS lighting make
         * the billboard pulse bright/dark when the orbit camera moves. Respect form.shading. */
        boolean shadowPass = context.isShadowPass || BBSRendering.isIrisShadowPass();
        boolean shading = this.form.shading.get() || shadowPass;

        VertexFormat format = shading ? VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL : VertexFormats.POSITION_TEXTURE_COLOR;
        Supplier<ShaderProgram> shader = this.getShader(context,
            shading ? GameRenderer::getRenderTypeEntityTranslucentProgram : GameRenderer::getPositionTexColorProgram,
            shading ? BBSShaders::getPickerBillboardProgram : BBSShaders::getPickerBillboardNoShadingProgram
        );

        this.renderModel(format, shader, context.stack, context.overlay, context.light, context.color, context.getTransition(), context.camera, false, context.modelRenderer || context.isPicking(), context);
    }

    private void renderModel(VertexFormat format, Supplier<ShaderProgram> shader, MatrixStack matrices, int overlay, int light, int overlayColor, float transition, Camera camera, boolean invertY, boolean modelRenderer, FormRenderingContext deferContext)
    {
        Link defaultLink = this.form.texture.get();

        if (defaultLink == null)
        {
            return;
        }

        FormTextureBlendRenderer.draw(this.form.textureBlend, defaultLink, (link, alphaFactor) ->
        {
            Texture texture = BBSModClient.getTextures().getTexture(link);

            if (texture == null)
            {
                return;
            }

            this.renderModelPass(format, texture, shader, matrices, overlay, light, overlayColor, transition, camera, invertY, modelRenderer, alphaFactor, deferContext, link);
        });
    }

    private void renderModelPass(VertexFormat format, Texture texture, Supplier<ShaderProgram> shader, MatrixStack matrices, int overlay, int light, int overlayColor, float transition, Camera camera, boolean invertY, boolean modelRenderer, float alphaFactor, FormRenderingContext deferContext, Link textureLink)
    {
        float w = texture.width;
        float h = texture.height;
        float ow = w;
        float oh = h;

        /* TL = top left, BR = bottom right*/
        Vector4f crop = this.form.crop.get();
        float uvTLx = crop.x / w;
        float uvTLy = crop.y / h;
        float uvBRx = 1 - crop.z / w;
        float uvBRy = 1 - crop.w / h;

        uvQuad.p1.set(uvTLx, uvTLy, 0);
        uvQuad.p2.set(uvBRx, uvTLy, 0);
        uvQuad.p3.set(uvTLx, uvBRy, 0);
        uvQuad.p4.set(uvBRx, uvBRy, 0);

        float uvFinalTLx = uvTLx;
        float uvFinalTLy = uvTLy;
        float uvFinalBRx = uvBRx;
        float uvFinalBRy = uvBRy;

        if (this.form.resizeCrop.get())
        {
            uvFinalTLx = uvFinalTLy = 0F;
            uvFinalBRx = uvFinalBRy = 1F;

            w = w - crop.x - crop.z;
            h = h - crop.y - crop.w;
        }

        /* Calculate quad's size (vertices, not UV) */
        float ratioX = w > h ? h / w : 1F;
        float ratioY = h > w ? w / h : 1F;
        float TLx = (uvFinalTLx - 0.5F) * ratioY;
        float TLy = -(uvFinalTLy - 0.5F) * ratioX;
        float BRx = (uvFinalBRx - 0.5F) * ratioY;
        float BRy = -(uvFinalBRy - 0.5F) * ratioX;

        quad.p1.set(TLx, TLy, 0);
        quad.p2.set(BRx, TLy, 0);
        quad.p3.set(TLx, BRy, 0);
        quad.p4.set(BRx, BRy, 0);

        float offsetX = this.form.offsetX.get();
        float offsetY = this.form.offsetY.get();
        float rotation = this.form.rotation.get();

        if (offsetX != 0F || offsetY != 0F || rotation != 0F)
        {
            float centerX = (crop.x + (ow - crop.z)) / 2F / ow;
            float centerY = (crop.y + (oh - crop.w)) / 2F / ow;

            matrix.identity()
                .translate(centerX, centerY, 0)
                .rotateZ(MathUtils.toRad(rotation))
                .translate(offsetX / ow, offsetY / oh, 0)
                .translate(-centerX, -centerY, 0);

            uvQuad.transform(matrix);
        }

        this.renderQuad(format, texture, shader, matrices, overlay, light, overlayColor, transition, camera, invertY, modelRenderer, alphaFactor, deferContext, textureLink);
    }

    private void renderQuad(VertexFormat format, Texture texture, Supplier<ShaderProgram> shader, MatrixStack matrices, int overlay, int light, int overlayColor, float transition, Camera camera, boolean invertY, boolean modelRenderer, float alphaFactor, FormRenderingContext deferContext, Link textureLink)
    {
        Color storedFormColor = this.form.color.get();
        boolean hasColorAdjustments = storedFormColor != null && storedFormColor.hasColorAdjustments();
        boolean colorTransformWanted = FormColorEffects.wantsColorTransformMask(storedFormColor);
        Color color = new Color().set(overlayColor, true);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        MatrixStack.Entry entry = matrices.peek();
        boolean shadowPassEarly = BBSRendering.isIrisShadowPass()
            || (deferContext != null && deferContext.isShadowPass);
        /* Orbit UI / form preview / inventory GUI: draw soft live. World post-deferred
         * queues never flush for those passes (same as ModelFormRenderer localPreview). */
        boolean localPreview = modelRenderer
            || (deferContext != null && deferContext.isLocalPreview());
        boolean irisWorld = BBSRendering.isIrisWorldModelPass() && !shadowPassEarly && !localPreview;
        /* No-shader: FormColorGrade in model.fsh. Iris: deferred BBS redraw with FormColorGrade
         * (ColorGradeOverlay scene-replace makes thin billboards look invisible). */
        boolean useFormColorGrade = hasColorAdjustments && !irisWorld;
        boolean irisDeferredColorGrade = hasColorAdjustments && irisWorld;
        Color formColor = storedFormColor.copyDeferringColorGrade().copy();

        /* Bake blend into vertices when FlatColorTint will not apply; grade stays in-shader / deferred. */
        if (colorTransformWanted)
        {
            color.r = 1F;
            color.g = 1F;
            color.b = 1F;
        }
        else if (useFormColorGrade || irisDeferredColorGrade)
        {
            color.mul(storedFormColor.copyDeferringColorGrade());
        }
        else
        {
            color.mul(storedFormColor.copyBakingColorGrade());
        }

        this.form.applyFormOpacity(color);
        this.form.applyFormOpacity(formColor);
        color.a *= alphaFactor;
        formColor.a *= alphaFactor;

        boolean shadowPass = shadowPassEarly;

        FormColorEffects.applyShadowPassColorFix(color, this.form.color.get(), this.form.paintSettings.get(), this.form.paintColor.get(), shadowPass);

        if (color.a <= 0.001F)
        {
            return;
        }

        /* Main pass: negative paint only; positive paint is drawn in a separate overlay pass */
        PaintSettings paintSettings = this.form.paintSettings.get();
        Color legacyPaint = this.form.paintColor.get();
        float paintStrength = paintSettings.resolveIntensity(legacyPaint);

        if (paintStrength < 0F)
        {
            FormColorEffects.applyPaintBlend(color, paintSettings, legacyPaint);
        }

        GlowSettings glowSettings = this.form.glowSettings.get();
        Color legacyGlow = this.form.glowingColor.get();
        float glowIntensity = glowSettings.resolveIntensity(legacyGlow);

        if (glowIntensity < 0F)
        {
            FormColorEffects.blendFormGlowBrighten(color, glowSettings, legacyGlow);
        }

        /* World/entity billboard: face the camera and ignore authored rotation.
         * Form/model editor preview (modelRenderer) must keep the real transform so
         * gizmo handles and General translate/rotate/scale fields match what you see. */
        if (this.form.billboard.get() && (deferContext == null || !deferContext.modelRenderer))
        {
            Matrix4f modelMatrix = matrices.peek().getPositionMatrix();
            Vector3f scale = Vectors.TEMP_3F;

            modelMatrix.getScale(scale);

            modelMatrix.m00(1).m01(0).m02(0);
            modelMatrix.m10(0).m11(1).m12(0);
            modelMatrix.m20(0).m21(0).m22(1);

            modelMatrix.scale(scale);

            /* Keep identity normals. Baking camera.view into the normal matrix made Iris/BBS
             * lighting track the orbit camera and pulse the billboard bright/dark. */
            matrices.peek().getNormalMatrix().identity();
            matrices.peek().getNormalMatrix().scale(
                MatrixStackUtils.safeNormalScaleReciprocal(scale.x),
                MatrixStackUtils.safeNormalScaleReciprocal(scale.y),
                MatrixStackUtils.safeNormalScaleReciprocal(scale.z)
            );
        }

        GameRenderer gameRenderer = MinecraftClient.getInstance().gameRenderer;
        if (format == VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL)
        {
            gameRenderer.getLightmapTextureManager().enable();
            gameRenderer.getOverlayTexture().setupOverlayColor();
        }

        this.bindFormTexture(texture);
        RenderSystem.setShader(shader);

        texture.bind();
        texture.setFilterMipmap(this.form.linear.get(), this.form.mipmap.get());

        RenderSystem.disableCull();

        /* Soft opacity: ShaderOpacityPatch (Iris lighting). Noshading opts into the
         * after-paint BBS queue instead so paint/masks show through — never both. */
        /* Paint / color-tint overlays must not write into the shadow map (same as Structure/Block). */
        boolean positivePaint = !shadowPass && FormColorEffects.hasPositivePaint(paintSettings, legacyPaint);
        Color resolvedPaint = positivePaint ? FormColorEffects.resolvePaintColor(paintSettings, legacyPaint) : null;
        boolean applyColorTint = colorTransformWanted && !shadowPass;
        boolean noshadingAfterPaint = irisWorld && BBSRendering.needsIrisNoshadingOpacityDeferral(color.a, this.form.noshadingOpacity.get());
        /* Soft opacity always uses ShaderOpacityPatch (same queue/sort as soft limbs / blocks).
         * Iris: color masks stay on the frame-end paint overlay (not inline in soft flush).
         * No-shader soft: paint/tint must draw in the same deferred entry after the base mesh
         * (c86b118f) — frame-end overlays lose depth order and sit under the soft billboard. */
        boolean softPostDeferred = !localPreview && !shadowPass
            && ShaderOpacityPatch.shouldDelayUntilPostDeferred(color.a);
        boolean noShaderSoft = softPostDeferred && !irisWorld;
        boolean deferForColorGrade = hasColorAdjustments && irisWorld;
        boolean deferNoshading = irisWorld && (noshadingAfterPaint || !this.form.shading.get());
        /* Opaque-ish Iris grade/noshading only — soft stays on ShaderOpacityPatch above. */
        boolean deferTranslucent = !softPostDeferred && !localPreview && !shadowPass
            && (deferForColorGrade
                || deferNoshading);

        if (softPostDeferred)
        {
            /* Iris + shaded: entity-local matrices + restore camera ModelView (fog-safe).
             * Unshaded / BBS model shader: camera-baked matrices + BBS post-deferred path
             * (DeferredFogSnapshot in ShaderOpacityPatch). Matches 0b19a7c transparency. */
            boolean irisCamera = BBSRendering.isIrisWorldModelPass() && this.form.shading.get();
            Matrix4f positionMatrix = irisCamera
                ? new Matrix4f(matrix)
                : ModelVAORenderer.capturePaintOverlayRootMatrix(new Matrix4f(matrix));
            Matrix3f normalMatrix = new Matrix3f(matrices.peek().getNormalMatrix());
            Color colorSnapshot = color.copy();
            Quad localQuad = new Quad();
            Quad localUvQuad = new Quad();

            localQuad.copy(quad);
            localUvQuad.copy(uvQuad);

            boolean linear = this.form.linear.get();
            boolean mipmap = this.form.mipmap.get();
            Link textureLinkSnapshot = textureLink;
            int overlaySnapshot = overlay;
            int lightSnapshot = light;
            float glowIntensitySnapshot = glowIntensity;
            GlowSettings glowSettingsSnapshot = glowSettings;
            Color legacyGlowSnapshot = legacyGlow;
            boolean emitGlowSnapshot = glowIntensity > 0F && !glowSettings.resolvePaintOnly();
            boolean positivePaintSnapshot = positivePaint;
            Color resolvedPaintSnapshot = resolvedPaint == null ? null : resolvedPaint.copy();
            PaintSettings paintSettingsSnapshot = paintSettings == null ? null : paintSettings.copy();
            boolean applyColorTintSnapshot = applyColorTint;
            Color formColorSnapshot = formColor.copy();
            EffectTransform colorTransformSnapshot = formColor.transform == null ? null : formColor.transform.copy();
            boolean noShaderSoftSnapshot = noShaderSoft;
            boolean depthWrite = ShaderOpacityPatch.shouldWriteDepthForOpacity(color.a);
            boolean afterFluids = ShaderOpacityPatch.shouldFlushAfterFluids(color.a);
            boolean gradeOnDeferredDraw = useFormColorGrade || irisDeferredColorGrade;
            /* Preserve live format/shader unless Color Grade needs model.fsh.
             * Note: unshaded soft still uses position_tex_color (no fog in that shader) —
             * that is a separate limitation, not the yaw-dependent cylindrical-fog bug. */
            VertexFormat deferredFormat = gradeOnDeferredDraw
                ? VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL
                : format;
            Supplier<ShaderProgram> deferredShader = gradeOnDeferredDraw
                ? BBSShaders::getModel
                : shader;
            float gradeBrightnessSnapshot = storedFormColor.brightness;
            float gradeContrastSnapshot = storedFormColor.contrast;
            float gradeHueSnapshot = storedFormColor.hue;
            float gradeSaturationSnapshot = storedFormColor.saturation;
            boolean gradeActiveSnapshot = gradeOnDeferredDraw;
            Color gradeSourceSnapshot = storedFormColor;
            double faceSortKey = this.computeBillboardFaceSortKey(matrix, deferContext);

            Runnable deferredDraw = () ->
            {
                Texture deferredTexture = texture;

                if (textureLinkSnapshot != null)
                {
                    Texture linkedTexture = BBSModClient.getTextures().getTexture(textureLinkSnapshot);

                    if (linkedTexture != null)
                    {
                        deferredTexture = linkedTexture;
                    }
                }

                if (deferredTexture == null)
                {
                    return;
                }

                MatrixStack overlayStack = new MatrixStack();

                overlayStack.peek().getPositionMatrix().set(positionMatrix);
                overlayStack.peek().getNormalMatrix().set(normalMatrix);

                if (deferredFormat == VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL)
                {
                    gameRenderer.getLightmapTextureManager().enable();
                    gameRenderer.getOverlayTexture().setupOverlayColor();
                }

                try
                {
                    /* drawBillboardFaces enables cull for dual mid-plane windings. */
                    RenderSystem.enableDepthTest();
                    ShaderOpacityPatch.reassertPostDeferredDepthState(depthWrite);

                    if (gradeActiveSnapshot)
                    {
                        ModelVAORenderer.setFormColorGrade(gradeBrightnessSnapshot, gradeContrastSnapshot, gradeHueSnapshot, gradeSaturationSnapshot);
                        ModelVAORenderer.setGradeEffectTransforms(gradeSourceSnapshot);

                        ShaderProgram gradeShader = BBSShaders.getModel();
                        MatrixStack gradeStack = new MatrixStack();

                        RenderSystem.setShader(() -> gradeShader);
                        ModelVAORenderer.setupUniforms(gradeStack, gradeShader);
                    }

                    this.drawBillboardFaces(
                        deferredFormat,
                        deferredTexture,
                        deferredShader,
                        overlayStack,
                        colorSnapshot,
                        localQuad,
                        localUvQuad,
                        overlaySnapshot,
                        lightSnapshot,
                        linear,
                        mipmap,
                        false
                    );

                    /* No-shader soft only: tint/paint in the same entry after the base mesh so
                     * color masks sit on top at 99% opacity (c86b118f). Iris soft keeps
                     * frame-end overlays so soft-vs-soft depth is not disturbed. */
                    if (noShaderSoftSnapshot && applyColorTintSnapshot)
                    {
                        this.renderColorTintOverlay(
                            deferredTexture,
                            deferredShader,
                            overlayStack,
                            overlaySnapshot,
                            formColorSnapshot,
                            localQuad,
                            localUvQuad,
                            colorTransformSnapshot
                        );
                    }

                    if (noShaderSoftSnapshot && positivePaintSnapshot)
                    {
                        this.renderPaintOverlay(
                            deferredTexture,
                            deferredShader,
                            overlayStack,
                            overlaySnapshot,
                            resolvedPaintSnapshot,
                            colorSnapshot.a,
                            localQuad,
                            localUvQuad,
                            paintSettingsSnapshot.transform,
                            glowSettingsSnapshot,
                            legacyGlowSnapshot,
                            glowIntensitySnapshot
                        );
                    }

                    if (emitGlowSnapshot)
                    {
                        EffectTransform glowTransform = FormColorEffects.resolveGlowEffectTransform(glowSettingsSnapshot, legacyGlowSnapshot);
                        boolean hasGlowTransform = glowTransform != null && glowTransform.isActive();

                        if (hasGlowTransform)
                        {
                            this.renderGlowOverlayMasked(
                                deferredTexture,
                                GameRenderer::getPositionTexColorProgram,
                                overlayStack,
                                glowSettingsSnapshot,
                                legacyGlowSnapshot,
                                colorSnapshot.a,
                                glowIntensitySnapshot,
                                localQuad,
                                localUvQuad,
                                glowTransform
                            );
                        }
                        else
                        {
                            this.renderGlowOverlay(
                                deferredTexture,
                                GameRenderer::getPositionTexColorProgram,
                                overlayStack,
                                glowSettingsSnapshot,
                                legacyGlowSnapshot,
                                colorSnapshot.a,
                                glowIntensitySnapshot,
                                localQuad,
                                localUvQuad
                            );
                        }
                    }
                }
                finally
                {
                    if (gradeActiveSnapshot)
                    {
                        ModelVAORenderer.clearFormColorGrade();
                    }
                }
            };

            if (irisCamera)
            {
                ShaderOpacityPatch.submitPostDeferredForm(0D, faceSortKey, depthWrite, afterFluids, deferredDraw);
            }
            else
            {
                ShaderOpacityPatch.submitPostDeferredBbsForm(0D, faceSortKey, depthWrite, afterFluids, deferredDraw);
            }
        }
        else if (deferTranslucent)
        {
            /* Under Iris, opaque-ish billboards may still need a BBS redraw — live
             * entity_translucent often washes them. Color Grade: never use ColorGradeOverlay
             * (scene capture misses the thin plane). Soft opacity does not enter here. */
            Matrix4f positionMatrix = ModelVAORenderer.capturePaintOverlayRootMatrix(new Matrix4f(matrix));
            Color colorSnapshot = color.copy();
            Quad localQuad = new Quad();
            Quad localUvQuad = new Quad();

            localQuad.copy(quad);
            localUvQuad.copy(uvQuad);

            boolean linear = this.form.linear.get();
            boolean mipmap = this.form.mipmap.get();
            Link textureLinkSnapshot = textureLink;
            int overlaySnapshot = overlay;
            int lightSnapshot = light;
            float glowIntensitySnapshot = glowIntensity;
            GlowSettings glowSettingsSnapshot = glowSettings;
            Color legacyGlowSnapshot = legacyGlow;
            boolean emitGlowSnapshot = glowIntensity > 0F && !glowSettings.resolvePaintOnly();
            boolean depthWrite = color.a >= ShaderOpacityPatch.LIVE_DEPTH_WRITE_ALPHA;
            VertexFormat deferredFormat = VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL;
            boolean gradeOnDeferredDraw = useFormColorGrade || irisDeferredColorGrade;
            Supplier<ShaderProgram> deferredShader = gradeOnDeferredDraw
                ? BBSShaders::getModel
                : GameRenderer::getRenderTypeEntityTranslucentProgram;
            float gradeBrightnessSnapshot = storedFormColor.brightness;
            float gradeContrastSnapshot = storedFormColor.contrast;
            float gradeHueSnapshot = storedFormColor.hue;
            float gradeSaturationSnapshot = storedFormColor.saturation;
            boolean gradeActiveSnapshot = gradeOnDeferredDraw;
            Color gradeSourceSnapshot = storedFormColor;

            Runnable deferredDraw = () ->
            {
                Texture deferredTexture = texture;

                if (textureLinkSnapshot != null)
                {
                    Texture linkedTexture = BBSModClient.getTextures().getTexture(textureLinkSnapshot);

                    if (linkedTexture != null)
                    {
                        deferredTexture = linkedTexture;
                    }
                }

                if (deferredTexture == null)
                {
                    return;
                }

                MatrixStack overlayStack = new MatrixStack();

                overlayStack.peek().getPositionMatrix().set(positionMatrix);
                overlayStack.peek().getNormalMatrix().identity();

                gameRenderer.getLightmapTextureManager().enable();
                gameRenderer.getOverlayTexture().setupOverlayColor();

                try
                {
                    /* beginDeferredTranslucentModelPass enables cull; drawBillboardFaces sets
                     * cull for dual mid-plane windings (or disableCull for single-sided). */
                    if (gradeActiveSnapshot)
                    {
                        ModelVAORenderer.setFormColorGrade(gradeBrightnessSnapshot, gradeContrastSnapshot, gradeHueSnapshot, gradeSaturationSnapshot);
                        ModelVAORenderer.setGradeEffectTransforms(gradeSourceSnapshot);

                        ShaderProgram gradeShader = BBSShaders.getModel();
                        MatrixStack gradeStack = new MatrixStack();

                        RenderSystem.setShader(() -> gradeShader);
                        ModelVAORenderer.setupUniforms(gradeStack, gradeShader);
                    }

                    /* Two-sided via both windings at mid-plane + cull (not ±FACE_Z_BIAS). */
                    this.drawBillboardFaces(
                        deferredFormat,
                        deferredTexture,
                        deferredShader,
                        overlayStack,
                        colorSnapshot,
                        localQuad,
                        localUvQuad,
                        overlaySnapshot,
                        lightSnapshot,
                        linear,
                        mipmap,
                        false
                    );

                    if (emitGlowSnapshot)
                    {
                        EffectTransform glowTransform = FormColorEffects.resolveGlowEffectTransform(glowSettingsSnapshot, legacyGlowSnapshot);
                        boolean hasGlowTransform = glowTransform != null && glowTransform.isActive();

                        if (hasGlowTransform)
                        {
                            this.renderGlowOverlayMasked(
                                deferredTexture,
                                GameRenderer::getPositionTexColorProgram,
                                overlayStack,
                                glowSettingsSnapshot,
                                legacyGlowSnapshot,
                                colorSnapshot.a,
                                glowIntensitySnapshot,
                                localQuad,
                                localUvQuad,
                                glowTransform
                            );
                        }
                        else
                        {
                            this.renderGlowOverlay(
                                deferredTexture,
                                GameRenderer::getPositionTexColorProgram,
                                overlayStack,
                                glowSettingsSnapshot,
                                legacyGlowSnapshot,
                                colorSnapshot.a,
                                glowIntensitySnapshot,
                                localQuad,
                                localUvQuad
                            );
                        }
                    }
                }
                finally
                {
                    if (gradeActiveSnapshot)
                    {
                        ModelVAORenderer.clearFormColorGrade();
                    }
                }
            };

            ModelVAORenderer.submitDeferredTranslucentModel(deferredDraw, depthWrite);
        }
        else
        {
            /* Live path — opaque / no-shader / Iris without deferral / inventory preview.
             * Soft alpha used to only hit this path in world when not deferred; inventory
             * localPreview now draws soft live too — must restore depthMask (soft clears it). */
            boolean savedDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
            boolean touchedDepthMask = false;

            if (format == VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL)
            {
                if (!irisWorld && (useFormColorGrade || BBSRendering.needsBbsModelForLowOpacity(color.a)))
                {
                    RenderSystem.setShader(BBSShaders::getModel);
                }

                RenderSystem.enableDepthTest();
                /* Inventory/GUI preview: keep depth writes on. Soft world draws may suppress
                 * depth; leaving depthMask false leaks into later GUI (bright undimmed hotbar). */
                boolean writeDepth = shadowPass || localPreview
                    || color.a >= ShaderOpacityPatch.LIVE_DEPTH_WRITE_ALPHA;

                if (writeDepth != savedDepthMask)
                {
                    RenderSystem.depthMask(writeDepth);
                    touchedDepthMask = true;
                }
            }

            if (useFormColorGrade)
            {
                ModelVAORenderer.setFormColorGrade(storedFormColor.brightness, storedFormColor.contrast, storedFormColor.hue, storedFormColor.saturation);
                ModelVAORenderer.setGradeEffectTransforms(storedFormColor);
            }

            if (shadowPass)
            {
                ShaderOpacityPatch.beginShadowForm();
            }

            try
            {
                BufferBuilder builder = Tessellator.getInstance().getBuffer();
                builder.begin(VertexFormat.DrawMode.TRIANGLES, format);

                float quadWidth = Math.abs(quad.p2.x - quad.p1.x);
                float quadHeight = Math.abs(quad.p1.y - quad.p3.y);
                Vector3f worldScale = new Vector3f();

                matrix.getScale(worldScale);

                float worldWidth = quadWidth * Math.abs(worldScale.x);
                float worldHeight = quadHeight * Math.abs(worldScale.y);

                /* Subdivide into user-configured block segments so non-linear shadow distortion in shaders (Complementary/BSL)
                 * curves accurately per-vertex instead of cutting a straight chord across huge billboards. */
                float step = this.form.subdivision.get();
                int segmentsX = 1;
                int segmentsY = 1;

                if (step > 0.001F)
                {
                    segmentsX = Math.min(64, Math.max(1, (int) Math.ceil(worldWidth / step)));
                    segmentsY = Math.min(64, Math.max(1, (int) Math.ceil(worldHeight / step)));
                }

                for (int ix = 0; ix < segmentsX; ix++)
                {
                    float fx0 = (float) ix / segmentsX;
                    float fx1 = (float) (ix + 1) / segmentsX;

                    float x0 = Lerps.lerp(quad.p1.x, quad.p2.x, fx0);
                    float x1 = Lerps.lerp(quad.p1.x, quad.p2.x, fx1);
                    float u0 = Lerps.lerp(uvQuad.p1.x, uvQuad.p2.x, fx0);
                    float u1 = Lerps.lerp(uvQuad.p1.x, uvQuad.p2.x, fx1);

                    for (int iy = 0; iy < segmentsY; iy++)
                    {
                        float fy0 = (float) iy / segmentsY;
                        float fy1 = (float) (iy + 1) / segmentsY;

                        float y0 = Lerps.lerp(quad.p1.y, quad.p3.y, fy0);
                        float y1 = Lerps.lerp(quad.p1.y, quad.p3.y, fy1);
                        float v0 = Lerps.lerp(uvQuad.p1.y, uvQuad.p3.y, fy0);
                        float v1 = Lerps.lerp(uvQuad.p1.y, uvQuad.p3.y, fy1);

                        /* Front + back windings on the same mid-plane. Cull (below) keeps
                         * only the camera-facing winding so front/back never share depth. */
                        this.fill(format, builder, matrix, x0, y1, 0F, color, u0, v1, overlay, light, entry, 1F);
                        this.fill(format, builder, matrix, x1, y0, 0F, color, u1, v0, overlay, light, entry, 1F);
                        this.fill(format, builder, matrix, x0, y0, 0F, color, u0, v0, overlay, light, entry, 1F);

                        this.fill(format, builder, matrix, x0, y1, 0F, color, u0, v1, overlay, light, entry, 1F);
                        this.fill(format, builder, matrix, x1, y1, 0F, color, u1, v1, overlay, light, entry, 1F);
                        this.fill(format, builder, matrix, x1, y0, 0F, color, u1, v0, overlay, light, entry, 1F);

                        this.fill(format, builder, matrix, x0, y0, 0F, color, u0, v0, overlay, light, entry, -1F);
                        this.fill(format, builder, matrix, x1, y0, 0F, color, u1, v0, overlay, light, entry, -1F);
                        this.fill(format, builder, matrix, x0, y1, 0F, color, u0, v1, overlay, light, entry, -1F);

                        this.fill(format, builder, matrix, x1, y0, 0F, color, u1, v0, overlay, light, entry, -1F);
                        this.fill(format, builder, matrix, x1, y1, 0F, color, u1, v1, overlay, light, entry, -1F);
                        this.fill(format, builder, matrix, x0, y1, 0F, color, u0, v1, overlay, light, entry, -1F);
                    }
                }

                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                /* Outer path disables cull for overlays; base mesh needs it or both
                 * windings at z=0 would z-fight identically. */
                RenderSystem.enableCull();

                if (useFormColorGrade)
                {
                    ShaderProgram gradeShader = BBSShaders.getModel();
                    MatrixStack gradeStack = new MatrixStack();

                    /* Vertices already include the model matrix; keep ModelView identity. */
                    ModelVAORenderer.setupUniforms(gradeStack, gradeShader);
                }

                ShaderProgram activeShader = RenderSystem.getShader();

                if (activeShader != null)
                {
                    activeShader.bind();

                    if (shadowPass)
                    {
                        ShaderOpacityPatch.uploadShadowFormUniform();
                    }
                }

                BufferRenderer.drawWithGlobalProgram(builder.end());
            }
            finally
            {
                if (shadowPass)
                {
                    ShaderOpacityPatch.endShadowForm();
                }

                if (useFormColorGrade)
                {
                    ModelVAORenderer.clearFormColorGrade();
                }

                if (touchedDepthMask)
                {
                    RenderSystem.depthMask(savedDepthMask);
                }
            }
        }

        if (applyColorTint && !noShaderSoft && !shadowPass)
        {
            EffectTransform colorTransform = formColor.transform == null ? null : formColor.transform.copy();

            if (localPreview)
            {
                /* UI / form editor preview: draw color tint immediately (no world deferral). */
                this.renderColorTintOverlay(texture, shader, matrices, overlay, formColor, colorTransform);
            }
            else
            {
                this.submitDeferredBillboardColorTintOverlay(texture, textureLink, shader, matrices, formColor, colorTransform);
            }
        }

        if (positivePaint && !noShaderSoft && !shadowPass)
        {
            if (localPreview)
            {
                /* Form editor / UI preview: draw paint immediately (no world deferral). */
                this.renderPaintOverlay(texture, shader, matrices, OverlayTexture.DEFAULT_UV, resolvedPaint, color.a, this.form.paintSettings.get().transform, glowSettings, legacyGlow, glowIntensity);
            }
            else
            {
                /* After ShaderOpacityPatch soft flush / Iris base redraw (onWorldRenderEnd).
                 * Iris soft: frame-end paint keeps masks out of the soft queue. */
                this.submitDeferredBillboardPaintOverlay(texture, textureLink, shader, matrices, resolvedPaint, color.a, glowSettings, legacyGlow, glowIntensity);
            }
        }

        /* Color grade with Iris is handled on the deferred BBS redraw above — do not run
         * ColorGradeOverlay (scene-copy replace makes thin billboards look invisible). */

        if (glowIntensity > 0F && !glowSettings.resolvePaintOnly() && !softPostDeferred && !deferTranslucent && !shadowPass)
        {
            EffectTransform glowTransform = FormColorEffects.resolveGlowEffectTransform(glowSettings, legacyGlow);
            boolean hasGlowTransform = glowTransform != null && glowTransform.isActive();

            if (hasGlowTransform)
            {
                if (deferContext == null || modelRenderer)
                {
                    this.renderGlowOverlayMasked(texture, shader, matrices, glowSettings, legacyGlow, color.a, glowIntensity, glowTransform);
                }
                else
                {
                    this.submitDeferredBillboardGlowOverlayMasked(texture, textureLink, shader, matrices, glowSettings, legacyGlow, color.a, glowIntensity, glowTransform);
                }
            }
            else
            {
                this.renderGlowOverlay(texture, shader, matrices, glowSettings, legacyGlow, color.a, glowIntensity);
            }
        }

        RenderSystem.enableCull();

        texture.setFilterMipmap(false, false);
        if (format == VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL)
        {
            gameRenderer.getLightmapTextureManager().disable();
            gameRenderer.getOverlayTexture().teardownOverlayColor();
        }
    }

    /**
     * @param singleSided paint/tint overlays: one camera-facing plane. Otherwise both
     *        windings share z=0 and cull keeps only the facing side (avoids ±FACE_Z_BIAS
     *        depth fighting at distance / Iris reversed-Z).
     */
    private void drawBillboardFaces(VertexFormat format, Texture texture, Supplier<ShaderProgram> shader, MatrixStack matrices, Color color, Quad drawQuad, Quad drawUvQuad, int overlay, int light, boolean linear, boolean mipmap, boolean singleSided)
    {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        MatrixStack.Entry entry = matrices.peek();
        BufferBuilder builder = Tessellator.getInstance().getBuffer();
        builder.begin(VertexFormat.DrawMode.TRIANGLES, format);
        boolean dualSided = !singleSided && !ModelVAORenderer.isPaintOverlayPass();
        float faceZ = singleSided ? this.resolveOverlayFaceZ(matrix) : 0F;
        float frontNz = faceZ >= 0F ? 1F : -1F;

        this.bindFormTexture(texture);
        RenderSystem.setShader(shader);
        texture.bind();
        texture.setFilterMipmap(linear, mipmap);

        if (dualSided)
        {
            RenderSystem.enableCull();
        }
        else
        {
            /* Single plane must stay visible from behind (Iris deferred / paint). */
            RenderSystem.disableCull();
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        this.fill(format, builder, matrix, drawQuad.p3.x, drawQuad.p3.y, faceZ, color, drawUvQuad.p3.x, drawUvQuad.p3.y, overlay, light, entry, frontNz);
        this.fill(format, builder, matrix, drawQuad.p2.x, drawQuad.p2.y, faceZ, color, drawUvQuad.p2.x, drawUvQuad.p2.y, overlay, light, entry, frontNz);
        this.fill(format, builder, matrix, drawQuad.p1.x, drawQuad.p1.y, faceZ, color, drawUvQuad.p1.x, drawUvQuad.p1.y, overlay, light, entry, frontNz);

        this.fill(format, builder, matrix, drawQuad.p3.x, drawQuad.p3.y, faceZ, color, drawUvQuad.p3.x, drawUvQuad.p3.y, overlay, light, entry, frontNz);
        this.fill(format, builder, matrix, drawQuad.p4.x, drawQuad.p4.y, faceZ, color, drawUvQuad.p4.x, drawUvQuad.p4.y, overlay, light, entry, frontNz);
        this.fill(format, builder, matrix, drawQuad.p2.x, drawQuad.p2.y, faceZ, color, drawUvQuad.p2.x, drawUvQuad.p2.y, overlay, light, entry, frontNz);

        if (dualSided)
        {
            this.fill(format, builder, matrix, drawQuad.p1.x, drawQuad.p1.y, faceZ, color, drawUvQuad.p1.x, drawUvQuad.p1.y, overlay, light, entry, -1F);
            this.fill(format, builder, matrix, drawQuad.p2.x, drawQuad.p2.y, faceZ, color, drawUvQuad.p2.x, drawUvQuad.p2.y, overlay, light, entry, -1F);
            this.fill(format, builder, matrix, drawQuad.p3.x, drawQuad.p3.y, faceZ, color, drawUvQuad.p3.x, drawUvQuad.p3.y, overlay, light, entry, -1F);

            this.fill(format, builder, matrix, drawQuad.p2.x, drawQuad.p2.y, faceZ, color, drawUvQuad.p2.x, drawUvQuad.p2.y, overlay, light, entry, -1F);
            this.fill(format, builder, matrix, drawQuad.p4.x, drawQuad.p4.y, faceZ, color, drawUvQuad.p4.x, drawUvQuad.p4.y, overlay, light, entry, -1F);
            this.fill(format, builder, matrix, drawQuad.p3.x, drawQuad.p3.y, faceZ, color, drawUvQuad.p3.x, drawUvQuad.p3.y, overlay, light, entry, -1F);
        }

        ShaderProgram bound = shader.get();

        if (bound != null)
        {
            bound.bind();

            /* Vertices already include the model matrix; keep ModelView identity for BBS uniforms
             * (FormColorGrade / ColorGradeOverlay) right before draw. */
            if (bound == BBSShaders.getModel())
            {
                ModelVAORenderer.setupUniforms(new MatrixStack(), bound);
            }

            if (BBSRendering.isIrisShadowPass())
            {
                ShaderOpacityPatch.uploadShadowFormUniform();
            }
        }

        BufferRenderer.drawWithGlobalProgram(builder.end());
        texture.setFilterMipmap(false, false);
    }

    private void fill(VertexFormat format, VertexConsumer consumer, Matrix4f matrix, float x, float y, float z, Color color, float u, float v, int overlay, int light, MatrixStack.Entry entry, float nz)
    {
        if (format == VertexFormats.POSITION_TEXTURE_LIGHT_COLOR)
        {
            consumer.vertex(matrix, x, y, z).texture(u, v).light(light).color(color.r, color.g, color.b, color.a).next();
            return;
        }

        if (format == VertexFormats.POSITION_TEXTURE_COLOR)
        {
            consumer.vertex(matrix, x, y, z).texture(u, v).color(color.r, color.g, color.b, color.a).next();
            return;
        }

        consumer.vertex(matrix, x, y, z).color(color.r, color.g, color.b, color.a).texture(u, v).overlay(overlay).light(light).normal(entry.getNormalMatrix(), 0F, 0F, nz).next();
    }

    private void submitDeferredBillboardPaintOverlay(Texture texture, Link textureLink, Supplier<ShaderProgram> shader, MatrixStack matrices, Color resolvedPaint, float alpha, GlowSettings glowSettings, Color legacyGlow, float glowIntensity)
    {
        Matrix4f positionMatrix = ModelVAORenderer.capturePaintOverlayRootMatrix(new Matrix4f(matrices.peek().getPositionMatrix()));
        Matrix3f normalMatrix = new Matrix3f(matrices.peek().getNormalMatrix());
        Color paintOverlay = new Color(resolvedPaint.r, resolvedPaint.g, resolvedPaint.b, resolvedPaint.a);

        paintOverlay.a *= alpha;

        Quad localQuad = new Quad();
        Quad localUvQuad = new Quad();

        localQuad.copy(quad);
        localUvQuad.copy(uvQuad);

        EffectTransform paintTransform = this.form.paintSettings.get().transform.copy();

        ModelVAORenderer.submitPaintOverlay(false, () ->
        {
            Texture deferredTexture = texture;

            if (textureLink != null)
            {
                Texture linkedTexture = BBSModClient.getTextures().getTexture(textureLink);

                if (linkedTexture != null)
                {
                    deferredTexture = linkedTexture;
                }
            }

            if (deferredTexture == null)
            {
                return;
            }

            MatrixStack overlayStack = new MatrixStack();

            overlayStack.peek().getPositionMatrix().set(positionMatrix);
            overlayStack.peek().getNormalMatrix().set(normalMatrix);

            this.renderPaintOverlay(deferredTexture, shader, overlayStack, OverlayTexture.DEFAULT_UV, paintOverlay, 1F, localQuad, localUvQuad, paintTransform, glowSettings, legacyGlow, glowIntensity, FlatPaintOverlayPass.DEFERRED_BILLBOARD_FACTOR, FlatPaintOverlayPass.DEFERRED_BILLBOARD_UNITS);
        });
    }

    private void renderPaintOverlay(Texture texture, Supplier<ShaderProgram> shader, MatrixStack matrices, int overlay, Color resolvedPaint, float alpha, EffectTransform transform)
    {
        this.renderPaintOverlay(texture, shader, matrices, overlay, resolvedPaint, alpha, quad, uvQuad, transform, null, null, 0F, FlatPaintOverlayPass.DEFAULT_FACTOR, FlatPaintOverlayPass.DEFAULT_UNITS);
    }

    private void renderPaintOverlay(Texture texture, Supplier<ShaderProgram> shader, MatrixStack matrices, int overlay, Color resolvedPaint, float alpha, EffectTransform transform, GlowSettings glowSettings, Color legacyGlow, float glowIntensity)
    {
        this.renderPaintOverlay(texture, shader, matrices, overlay, resolvedPaint, alpha, quad, uvQuad, transform, glowSettings, legacyGlow, glowIntensity, FlatPaintOverlayPass.DEFAULT_FACTOR, FlatPaintOverlayPass.DEFAULT_UNITS);
    }

    private void renderPaintOverlay(Texture texture, Supplier<ShaderProgram> shader, MatrixStack matrices, int overlay, Color resolvedPaint, float alpha, Quad drawQuad, Quad drawUvQuad, EffectTransform transform)
    {
        this.renderPaintOverlay(texture, shader, matrices, overlay, resolvedPaint, alpha, drawQuad, drawUvQuad, transform, null, null, 0F, FlatPaintOverlayPass.DEFAULT_FACTOR, FlatPaintOverlayPass.DEFAULT_UNITS);
    }

    private void renderPaintOverlay(Texture texture, Supplier<ShaderProgram> shader, MatrixStack matrices, int overlay, Color resolvedPaint, float alpha, Quad drawQuad, Quad drawUvQuad, EffectTransform transform, GlowSettings glowSettings, Color legacyGlow, float glowIntensity)
    {
        this.renderPaintOverlay(texture, shader, matrices, overlay, resolvedPaint, alpha, drawQuad, drawUvQuad, transform, glowSettings, legacyGlow, glowIntensity, FlatPaintOverlayPass.DEFAULT_FACTOR, FlatPaintOverlayPass.DEFAULT_UNITS);
    }

    private void renderPaintOverlay(Texture texture, Supplier<ShaderProgram> shader, MatrixStack matrices, int overlay, Color resolvedPaint, float alpha, Quad drawQuad, Quad drawUvQuad, EffectTransform transform, GlowSettings glowSettings, Color legacyGlow, float glowIntensity, float polygonOffsetFactor, float polygonOffsetUnits)
    {
        Color paintOverlay = new Color(resolvedPaint.r, resolvedPaint.g, resolvedPaint.b, resolvedPaint.a);

        paintOverlay.a *= alpha;
        this.applyPaintOnlyGlow(paintOverlay, glowSettings, legacyGlow, glowIntensity);

        matrices.push();

        Matrix4f paintMatrix = matrices.peek().getPositionMatrix();
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f formRootInverse = new Matrix4f(paintMatrix).invert();

        this.resolveQuadMaskHalf(drawQuad, transform, MASK_HALF);
        this.bindFormTexture(texture);
        texture.bind();
        texture.setFilterMipmap(this.form.linear.get(), this.form.mipmap.get());

        FlatPaintOverlayPass.render(polygonOffsetFactor, polygonOffsetUnits, formRootInverse, transform, false, MASK_HALF, () ->
        {
            BufferBuilder paintBuilder = Tessellator.getInstance().getBuffer();
            paintBuilder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
            int paintLight = LightmapTextureManager.MAX_LIGHT_COORDINATE;
            float paintZ = this.resolveOverlayFaceZ(paintMatrix);
            float paintNz = paintZ >= 0F ? 1F : -1F;

            /* One camera-facing plane, both sides via disableCull.
             * Spatial paint mask is evaluated per fragment in flat_paint_overlay. */
            RenderSystem.disableCull();

            this.fillPaint(paintBuilder, paintMatrix, drawQuad.p3.x, drawQuad.p3.y, paintZ, paintOverlay, drawUvQuad.p3.x, drawUvQuad.p3.y, overlay, paintLight, entry, paintNz);
            this.fillPaint(paintBuilder, paintMatrix, drawQuad.p2.x, drawQuad.p2.y, paintZ, paintOverlay, drawUvQuad.p2.x, drawUvQuad.p2.y, overlay, paintLight, entry, paintNz);
            this.fillPaint(paintBuilder, paintMatrix, drawQuad.p1.x, drawQuad.p1.y, paintZ, paintOverlay, drawUvQuad.p1.x, drawUvQuad.p1.y, overlay, paintLight, entry, paintNz);

            this.fillPaint(paintBuilder, paintMatrix, drawQuad.p3.x, drawQuad.p3.y, paintZ, paintOverlay, drawUvQuad.p3.x, drawUvQuad.p3.y, overlay, paintLight, entry, paintNz);
            this.fillPaint(paintBuilder, paintMatrix, drawQuad.p4.x, drawQuad.p4.y, paintZ, paintOverlay, drawUvQuad.p4.x, drawUvQuad.p4.y, overlay, paintLight, entry, paintNz);
            this.fillPaint(paintBuilder, paintMatrix, drawQuad.p2.x, drawQuad.p2.y, paintZ, paintOverlay, drawUvQuad.p2.x, drawUvQuad.p2.y, overlay, paintLight, entry, paintNz);

            BufferRenderer.drawWithGlobalProgram(paintBuilder.end());

            RenderSystem.enableCull();
        });

        texture.setFilterMipmap(false, false);
        RenderSystem.setShader(shader);
        matrices.pop();
    }

    private void fillPaint(BufferBuilder builder, Matrix4f matrix, float x, float y, float z, Color color, float u, float v, int overlay, int light, MatrixStack.Entry entry, float nz)
    {
        builder.vertex(matrix, x, y, z).color(color.r, color.g, color.b, color.a).texture(u, v).overlay(overlay).light(light).normal(entry.getNormalMatrix(), 0F, 0F, nz).next();
    }

    private void submitDeferredBillboardColorTintOverlay(Texture texture, Link textureLink, Supplier<ShaderProgram> shader, MatrixStack matrices, Color formTintColor, EffectTransform colorTransform)
    {
        Matrix4f positionMatrix = ModelVAORenderer.capturePaintOverlayRootMatrix(new Matrix4f(matrices.peek().getPositionMatrix()));
        Matrix3f normalMatrix = new Matrix3f(matrices.peek().getNormalMatrix());
        Color tintSnapshot = new Color(formTintColor.r, formTintColor.g, formTintColor.b, formTintColor.a);

        Quad localQuad = new Quad();
        Quad localUvQuad = new Quad();

        localQuad.copy(quad);
        localUvQuad.copy(uvQuad);

        EffectTransform colorTransformSnapshot = colorTransform == null ? null : colorTransform.copy();

        ModelVAORenderer.submitColorTintOverlay(() ->
        {
            Texture deferredTexture = texture;

            if (textureLink != null)
            {
                Texture linkedTexture = BBSModClient.getTextures().getTexture(textureLink);

                if (linkedTexture != null)
                {
                    deferredTexture = linkedTexture;
                }
            }

            if (deferredTexture == null)
            {
                return;
            }

            MatrixStack overlayStack = new MatrixStack();

            overlayStack.peek().getPositionMatrix().set(positionMatrix);
            overlayStack.peek().getNormalMatrix().set(normalMatrix);

            this.renderColorTintOverlay(deferredTexture, shader, overlayStack, OverlayTexture.DEFAULT_UV, tintSnapshot, localQuad, localUvQuad, colorTransformSnapshot, FlatPaintOverlayPass.DEFERRED_BILLBOARD_FACTOR, FlatPaintOverlayPass.DEFERRED_BILLBOARD_UNITS);
        });
    }

    private void submitDeferredBillboardColorGradeOverlay(Texture texture, Link textureLink, MatrixStack matrices, Color drawColor, Color gradeSource)
    {
        Matrix4f positionMatrix = ModelVAORenderer.capturePaintOverlayRootMatrix(new Matrix4f(matrices.peek().getPositionMatrix()));
        Matrix3f normalMatrix = new Matrix3f(matrices.peek().getNormalMatrix());
        Color colorSnapshot = drawColor.copy();
        float gradeBrightness = gradeSource.brightness;
        float gradeContrast = gradeSource.contrast;
        float gradeHue = gradeSource.hue;
        float gradeSaturation = gradeSource.saturation;
        boolean linear = this.form.linear.get();
        boolean mipmap = this.form.mipmap.get();

        Quad localQuad = new Quad();
        Quad localUvQuad = new Quad();

        localQuad.copy(quad);
        localUvQuad.copy(uvQuad);

        ModelVAORenderer.submitColorGradeOverlay(() ->
        {
            Texture deferredTexture = texture;

            if (textureLink != null)
            {
                Texture linkedTexture = BBSModClient.getTextures().getTexture(textureLink);

                if (linkedTexture != null)
                {
                    deferredTexture = linkedTexture;
                }
            }

            if (deferredTexture == null)
            {
                return;
            }

            try
            {
                ModelVAORenderer.setFormColorGrade(gradeBrightness, gradeContrast, gradeHue, gradeSaturation);
                ModelVAORenderer.setGradeEffectTransforms(gradeSource);
                ModelVAORenderer.clearPaint();
                ModelVAORenderer.clearGlowing();

                MatrixStack overlayStack = new MatrixStack();

                overlayStack.peek().getPositionMatrix().set(positionMatrix);
                overlayStack.peek().getNormalMatrix().set(normalMatrix);

                ShaderProgram gradeShader = BBSShaders.getModel();
                MatrixStack uniformStack = new MatrixStack();

                RenderSystem.setShader(() -> gradeShader);
                ModelVAORenderer.setupUniforms(uniformStack, gradeShader);

                this.drawBillboardFaces(
                    VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
                    deferredTexture,
                    BBSShaders::getModel,
                    overlayStack,
                    colorSnapshot,
                    localQuad,
                    localUvQuad,
                    OverlayTexture.DEFAULT_UV,
                    LightmapTextureManager.MAX_LIGHT_COORDINATE,
                    linear,
                    mipmap,
                    true
                );
            }
            finally
            {
                ModelVAORenderer.clearFormColorGrade();
                ModelVAORenderer.clearPaint();
                ModelVAORenderer.clearGlowing();
            }
        });
    }

    private void renderColorTintOverlay(Texture texture, Supplier<ShaderProgram> shader, MatrixStack matrices, int overlay, Color formTintColor, EffectTransform transform)
    {
        this.renderColorTintOverlay(texture, shader, matrices, overlay, formTintColor, quad, uvQuad, transform);
    }

    private void renderColorTintOverlay(Texture texture, Supplier<ShaderProgram> shader, MatrixStack matrices, int overlay, Color formTintColor, Quad drawQuad, Quad drawUvQuad, EffectTransform transform)
    {
        this.renderColorTintOverlay(texture, shader, matrices, overlay, formTintColor, drawQuad, drawUvQuad, transform, FlatPaintOverlayPass.DEFAULT_FACTOR, FlatPaintOverlayPass.DEFAULT_UNITS);
    }

    private void renderColorTintOverlay(Texture texture, Supplier<ShaderProgram> shader, MatrixStack matrices, int overlay, Color formTintColor, Quad drawQuad, Quad drawUvQuad, EffectTransform transform, float polygonOffsetFactor, float polygonOffsetUnits)
    {
        matrices.push();

        Matrix4f tintMatrix = matrices.peek().getPositionMatrix();
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f formRootInverse = new Matrix4f(tintMatrix).invert();

        this.resolveQuadMaskHalf(drawQuad, transform, MASK_HALF);
        this.bindFormTexture(texture);
        texture.bind();
        texture.setFilterMipmap(this.form.linear.get(), this.form.mipmap.get());

        FlatColorTintOverlayPass.render(polygonOffsetFactor, polygonOffsetUnits, formRootInverse, transform, false, MASK_HALF, formTintColor, () ->
        {
            BufferBuilder tintBuilder = Tessellator.getInstance().getBuffer();
            tintBuilder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
            int tintLight = LightmapTextureManager.MAX_LIGHT_COORDINATE;
            float tintZ = this.resolveOverlayFaceZ(tintMatrix);
            float tintNz = tintZ >= 0F ? 1F : -1F;

            /* One camera-facing plane, both sides via disableCull — same as glow/paint.
             * Mask is evaluated per fragment in the flat_color_tint_overlay shader. */
            RenderSystem.disableCull();

            this.fillColorTint(tintBuilder, tintMatrix, drawQuad.p3.x, drawQuad.p3.y, tintZ, drawUvQuad.p3.x, drawUvQuad.p3.y, overlay, tintLight, entry, tintNz);
            this.fillColorTint(tintBuilder, tintMatrix, drawQuad.p2.x, drawQuad.p2.y, tintZ, drawUvQuad.p2.x, drawUvQuad.p2.y, overlay, tintLight, entry, tintNz);
            this.fillColorTint(tintBuilder, tintMatrix, drawQuad.p1.x, drawQuad.p1.y, tintZ, drawUvQuad.p1.x, drawUvQuad.p1.y, overlay, tintLight, entry, tintNz);

            this.fillColorTint(tintBuilder, tintMatrix, drawQuad.p3.x, drawQuad.p3.y, tintZ, drawUvQuad.p3.x, drawUvQuad.p3.y, overlay, tintLight, entry, tintNz);
            this.fillColorTint(tintBuilder, tintMatrix, drawQuad.p4.x, drawQuad.p4.y, tintZ, drawUvQuad.p4.x, drawUvQuad.p4.y, overlay, tintLight, entry, tintNz);
            this.fillColorTint(tintBuilder, tintMatrix, drawQuad.p2.x, drawQuad.p2.y, tintZ, drawUvQuad.p2.x, drawUvQuad.p2.y, overlay, tintLight, entry, tintNz);

            BufferRenderer.drawWithGlobalProgram(tintBuilder.end());

            RenderSystem.enableCull();
        });

        texture.setFilterMipmap(false, false);
        RenderSystem.setShader(shader);
        matrices.pop();
    }

    private void fillColorTint(BufferBuilder builder, Matrix4f matrix, float x, float y, float z, float u, float v, int overlay, int light, MatrixStack.Entry entry, float nz)
    {
        /* Neutral verts — FormColorTint + spatial mask live in the fragment shader. */
        builder.vertex(matrix, x, y, z).color(1F, 1F, 1F, 1F).texture(u, v).overlay(overlay).light(light).normal(entry.getNormalMatrix(), 0F, 0F, nz).next();
    }

    /**
     * Half extents of the aspect-scaled billboard quad so color/paint masks match geometry
     * (fixed 0.5 covered the whole face on tall/wide images at scale 0.5).
     */
    private void resolveQuadMaskHalf(Quad drawQuad, EffectTransform transform, Vector3f dest)
    {
        float halfX = Math.max(
            Math.max(Math.abs(drawQuad.p1.x), Math.abs(drawQuad.p2.x)),
            Math.max(Math.abs(drawQuad.p3.x), Math.abs(drawQuad.p4.x))
        );
        float halfY = Math.max(
            Math.max(Math.abs(drawQuad.p1.y), Math.abs(drawQuad.p2.y)),
            Math.max(Math.abs(drawQuad.p3.y), Math.abs(drawQuad.p4.y))
        );

        EffectTransformMath.resolveBillboardMaskHalfExtents(transform, dest, halfX, halfY);
    }

    /**
     * Local Z just outside the base face that points toward the camera. {@code viewModel}
     * is the same matrix used to transform overlay verts (camera × stack when deferred).
     */
    private float resolveOverlayFaceZ(Matrix4f viewModel)
    {
        /* Translation ≈ billboard origin in view space; toward camera is -origin. */
        OVERLAY_TO_CAMERA.set(-viewModel.m30(), -viewModel.m31(), -viewModel.m32());
        /* Third column = local +Z axis in view space. */
        OVERLAY_LOCAL_Z.set(viewModel.m20(), viewModel.m21(), viewModel.m22());

        float facing = OVERLAY_LOCAL_Z.dot(OVERLAY_TO_CAMERA);
        float sign = facing >= 0F ? 1F : -1F;

        return sign * (FACE_Z_BIAS + OVERLAY_FACE_EXTRA);
    }

    private void submitDeferredBillboardGlowOverlayMasked(Texture texture, Link textureLink, Supplier<ShaderProgram> shader, MatrixStack matrices, GlowSettings glowSettings, Color legacyGlow, float alpha, float glowIntensity, EffectTransform glowTransform)
    {
        Matrix4f positionMatrix = ModelVAORenderer.capturePaintOverlayRootMatrix(new Matrix4f(matrices.peek().getPositionMatrix()));
        Matrix3f normalMatrix = new Matrix3f(matrices.peek().getNormalMatrix());
        GlowSettings glowSnapshot = glowSettings == null ? null : glowSettings.copy();
        Color legacyGlowSnapshot = legacyGlow == null ? null : legacyGlow.copy();
        EffectTransform glowTransformSnapshot = glowTransform == null ? null : glowTransform.copy();

        Quad localQuad = new Quad();
        Quad localUvQuad = new Quad();

        localQuad.copy(quad);
        localUvQuad.copy(uvQuad);

        ModelVAORenderer.submitPaintOverlay(false, () ->
        {
            Texture deferredTexture = texture;

            if (textureLink != null)
            {
                Texture linkedTexture = BBSModClient.getTextures().getTexture(textureLink);

                if (linkedTexture != null)
                {
                    deferredTexture = linkedTexture;
                }
            }

            if (deferredTexture == null)
            {
                return;
            }

            MatrixStack overlayStack = new MatrixStack();

            overlayStack.peek().getPositionMatrix().set(positionMatrix);
            overlayStack.peek().getNormalMatrix().set(normalMatrix);

            this.renderGlowOverlayMasked(deferredTexture, shader, overlayStack, glowSnapshot, legacyGlowSnapshot, alpha, glowIntensity, localQuad, localUvQuad, glowTransformSnapshot, FlatPaintOverlayPass.DEFERRED_BILLBOARD_FACTOR, FlatPaintOverlayPass.DEFERRED_BILLBOARD_UNITS);
        });
    }

    private void renderGlowOverlayMasked(Texture texture, Supplier<ShaderProgram> shader, MatrixStack matrices, GlowSettings glowSettings, Color legacyGlow, float alpha, float glowIntensity, EffectTransform glowTransform)
    {
        this.renderGlowOverlayMasked(texture, shader, matrices, glowSettings, legacyGlow, alpha, glowIntensity, quad, uvQuad, glowTransform, FlatPaintOverlayPass.DEFAULT_FACTOR, FlatPaintOverlayPass.DEFAULT_UNITS);
    }

    private void renderGlowOverlayMasked(Texture texture, Supplier<ShaderProgram> shader, MatrixStack matrices, GlowSettings glowSettings, Color legacyGlow, float alpha, float glowIntensity, Quad drawQuad, Quad drawUvQuad, EffectTransform glowTransform)
    {
        this.renderGlowOverlayMasked(texture, shader, matrices, glowSettings, legacyGlow, alpha, glowIntensity, drawQuad, drawUvQuad, glowTransform, FlatPaintOverlayPass.DEFAULT_FACTOR, FlatPaintOverlayPass.DEFAULT_UNITS);
    }

    private void renderGlowOverlayMasked(Texture texture, Supplier<ShaderProgram> shader, MatrixStack matrices, GlowSettings glowSettings, Color legacyGlow, float alpha, float glowIntensity, Quad drawQuad, Quad drawUvQuad, EffectTransform glowTransform, float polygonOffsetFactor, float polygonOffsetUnits)
    {
        Color resolvedGlow = new Color();
        glowSettings.resolveColor(legacyGlow, resolvedGlow);

        float shaderScale = FormColorEffects.resolveGlowOverlayShaderScale(glowIntensity);
        Color glowColor = new Color(
            resolvedGlow.r,
            resolvedGlow.g,
            resolvedGlow.b,
            alpha
        );

        matrices.push();

        Matrix4f glowMatrix = matrices.peek().getPositionMatrix();
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f formRootInverse = new Matrix4f(glowMatrix).invert();

        this.resolveQuadMaskHalf(drawQuad, glowTransform, MASK_HALF);
        this.bindFormTexture(texture);
        texture.bind();
        texture.setFilterMipmap(this.form.linear.get(), this.form.mipmap.get());

        FlatGlowOverlayPass.renderMasked(polygonOffsetFactor, polygonOffsetUnits, formRootInverse, glowTransform, false, MASK_HALF, shaderScale, () ->
        {
            BufferBuilder glowBuilder = Tessellator.getInstance().getBuffer();
            glowBuilder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
            int glowLight = LightmapTextureManager.MAX_LIGHT_COORDINATE;
            float glowZ = this.resolveOverlayFaceZ(glowMatrix);
            float glowNz = glowZ >= 0F ? 1F : -1F;

            /* One camera-facing plane, both sides via disableCull — same as paint. */
            RenderSystem.disableCull();

            this.fillPaint(glowBuilder, glowMatrix, drawQuad.p3.x, drawQuad.p3.y, glowZ, glowColor, drawUvQuad.p3.x, drawUvQuad.p3.y, OverlayTexture.DEFAULT_UV, glowLight, entry, glowNz);
            this.fillPaint(glowBuilder, glowMatrix, drawQuad.p2.x, drawQuad.p2.y, glowZ, glowColor, drawUvQuad.p2.x, drawUvQuad.p2.y, OverlayTexture.DEFAULT_UV, glowLight, entry, glowNz);
            this.fillPaint(glowBuilder, glowMatrix, drawQuad.p1.x, drawQuad.p1.y, glowZ, glowColor, drawUvQuad.p1.x, drawUvQuad.p1.y, OverlayTexture.DEFAULT_UV, glowLight, entry, glowNz);

            this.fillPaint(glowBuilder, glowMatrix, drawQuad.p3.x, drawQuad.p3.y, glowZ, glowColor, drawUvQuad.p3.x, drawUvQuad.p3.y, OverlayTexture.DEFAULT_UV, glowLight, entry, glowNz);
            this.fillPaint(glowBuilder, glowMatrix, drawQuad.p4.x, drawQuad.p4.y, glowZ, glowColor, drawUvQuad.p4.x, drawUvQuad.p4.y, OverlayTexture.DEFAULT_UV, glowLight, entry, glowNz);
            this.fillPaint(glowBuilder, glowMatrix, drawQuad.p2.x, drawQuad.p2.y, glowZ, glowColor, drawUvQuad.p2.x, drawUvQuad.p2.y, OverlayTexture.DEFAULT_UV, glowLight, entry, glowNz);

            BufferRenderer.drawWithGlobalProgram(glowBuilder.end());

            RenderSystem.enableCull();
        });

        texture.setFilterMipmap(false, false);
        RenderSystem.setShader(shader);
        matrices.pop();
    }

    private void renderGlowOverlay(Texture texture, Supplier<ShaderProgram> shader, MatrixStack matrices, GlowSettings glowSettings, Color legacyGlow, float alpha, float glowIntensity)
    {
        this.renderGlowOverlay(texture, shader, matrices, glowSettings, legacyGlow, alpha, glowIntensity, quad, uvQuad);
    }

    private void renderGlowOverlay(Texture texture, Supplier<ShaderProgram> shader, MatrixStack matrices, GlowSettings glowSettings, Color legacyGlow, float alpha, float glowIntensity, Quad drawQuad, Quad drawUvQuad)
    {
        matrices.push();

        Matrix4f glowMatrix = matrices.peek().getPositionMatrix();

        this.bindFormTexture(texture);
        texture.bind();
        texture.setFilterMipmap(this.form.linear.get(), this.form.mipmap.get());

        FlatGlowOverlayPass.render(glowSettings, legacyGlow, alpha, glowIntensity, (glowColor) ->
        {
            BufferBuilder glowBuilder = Tessellator.getInstance().getBuffer();
            glowBuilder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_TEXTURE_COLOR);

            RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
            float glowZ = this.resolveOverlayFaceZ(glowMatrix);

            /* One camera-facing plane, both sides via disableCull — same as paint. */
            RenderSystem.disableCull();

            this.fillGlow(glowBuilder, glowMatrix, drawQuad.p3.x, drawQuad.p3.y, glowZ, glowColor, drawUvQuad.p3.x, drawUvQuad.p3.y);
            this.fillGlow(glowBuilder, glowMatrix, drawQuad.p2.x, drawQuad.p2.y, glowZ, glowColor, drawUvQuad.p2.x, drawUvQuad.p2.y);
            this.fillGlow(glowBuilder, glowMatrix, drawQuad.p1.x, drawQuad.p1.y, glowZ, glowColor, drawUvQuad.p1.x, drawUvQuad.p1.y);

            this.fillGlow(glowBuilder, glowMatrix, drawQuad.p3.x, drawQuad.p3.y, glowZ, glowColor, drawUvQuad.p3.x, drawUvQuad.p3.y);
            this.fillGlow(glowBuilder, glowMatrix, drawQuad.p4.x, drawQuad.p4.y, glowZ, glowColor, drawUvQuad.p4.x, drawUvQuad.p4.y);
            this.fillGlow(glowBuilder, glowMatrix, drawQuad.p2.x, drawQuad.p2.y, glowZ, glowColor, drawUvQuad.p2.x, drawUvQuad.p2.y);

            BufferRenderer.drawWithGlobalProgram(glowBuilder.end());

            RenderSystem.enableCull();
        });

        texture.setFilterMipmap(false, false);
        RenderSystem.setShader(shader);
        matrices.pop();
    }

    private void fillGlow(BufferBuilder builder, Matrix4f matrix, float x, float y, float z, Color color, float u, float v)
    {
        builder.vertex(matrix, x, y, z).texture(u, v).color(color.r, color.g, color.b, color.a).next();
    }

    private void applyPaintOnlyGlow(Color paintOverlay, GlowSettings glowSettings, Color legacyGlow, float glowIntensity)
    {
        if (paintOverlay == null || glowSettings == null || !glowSettings.resolvePaintOnly() || glowIntensity <= 0F)
        {
            return;
        }

        Color glowResolved = new Color();

        glowSettings.resolveColor(legacyGlow, glowResolved);
        FormColorEffects.blendEmission(paintOverlay, glowResolved, glowIntensity);
    }

    /**
     * Soft-opacity queue key for the billboard face (farther first). Film ENTITY uses look-axis
     * depth ({@code -z} in view space ≡ soft-limb film keys). Model-block / other: lengthSq.
     */
    private double computeBillboardFaceSortKey(Matrix4f drawMatrix, FormRenderingContext context)
    {
        float cx = (quad.p1.x + quad.p2.x + quad.p3.x + quad.p4.x) * 0.25F;
        float cy = (quad.p1.y + quad.p2.y + quad.p3.y + quad.p4.y) * 0.25F;
        Vector4f face = new Vector4f(cx, cy, FACE_Z_BIAS, 1F);
        Matrix4f viewSpace = ModelVAORenderer.capturePaintOverlayRootMatrix(new Matrix4f(drawMatrix));

        viewSpace.transform(face);

        boolean filmLookAxis = context != null
            && context.type == FormRenderType.ENTITY
            && context.camera != null
            && !context.modelRenderer;

        if (filmLookAxis)
        {
            return -face.z;
        }

        return face.x * face.x + face.y * face.y + face.z * face.z;
    }
}