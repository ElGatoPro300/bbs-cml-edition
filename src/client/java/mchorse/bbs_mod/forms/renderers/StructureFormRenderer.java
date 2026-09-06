package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.cubic.render.vao.IModelVAO;
import mchorse.bbs_mod.cubic.render.vao.ModelVAORenderer;
import mchorse.bbs_mod.cubic.render.vao.StructureVAOCollector;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.StructureForm;
import mchorse.bbs_mod.forms.forms.utils.EffectTransform;
import mchorse.bbs_mod.forms.forms.utils.GlowSettings;
import mchorse.bbs_mod.forms.forms.utils.PaintSettings;
import mchorse.bbs_mod.forms.forms.utils.StructureLightSettings;
import mchorse.bbs_mod.forms.renderers.utils.FormColorEffects;
import mchorse.bbs_mod.forms.renderers.utils.RecolorVertexConsumer;
import mchorse.bbs_mod.forms.renderers.utils.StructureData;
import mchorse.bbs_mod.forms.renderers.utils.StructureData.BlockEntry;
import mchorse.bbs_mod.forms.renderers.utils.StructureFormOverlayRenderer;
import mchorse.bbs_mod.forms.renderers.utils.StructureFormOverlayRenderer.StructurePaintLayer;
import mchorse.bbs_mod.forms.renderers.utils.StructureVaoManager;
import mchorse.bbs_mod.forms.renderers.utils.StructureVirtualBlockRenderView;
import mchorse.bbs_mod.forms.renderers.utils.VirtualBlockRenderView;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.iris.ShaderOpacityPatch;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.LightType;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.mojang.blaze3d.systems.RenderSystem;

import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * StructureForm Renderer
 *
 * Coordinates structure preview and 3D rendering using modular StructureData,
 * StructureVaoManager, and StructureFormOverlayRenderer delegates.
 */
public class StructureFormRenderer extends FormRenderer<StructureForm>
{
    private final StructureData data = new StructureData();
    private final StructureVaoManager vaoManager = new StructureVaoManager();
    private final StructureFormOverlayRenderer overlayRenderer = new StructureFormOverlayRenderer();

    private boolean lastEmitLight = false;
    private int lastLightIntensity = 0;

    public static void clearAllCachedVaos()
    {
        StructureVaoManager.clearAllCachedVaos();
    }

    public StructureFormRenderer(StructureForm form)
    {
        super(form);
    }

    @Override
    public void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        context.batcher.getContext().draw();

        StructureVaoManager.ensureLightingRevision();
        this.ensureLoaded();

        MatrixStack matrices = context.batcher.getContext().getMatrices();
        Matrix4f uiMatrix = ModelFormRenderer.getUIMatrix(context, x1, y1, x2, y2);

        matrices.push();
        MatrixStackUtils.multiply(matrices, uiMatrix);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);

        float cellW = x2 - x1;
        float cellH = y2 - y1;
        float baseScale = cellH / 2.5F;
        float targetPixels = Math.min(cellW, cellH) * 0.9F;

        int wUnits = 1;
        int hUnits = 1;
        int dUnits = 1;

        if (this.data.getBoundsMin() != null && this.data.getBoundsMax() != null)
        {
            wUnits = Math.max(1, this.data.getBoundsMax().getX() - this.data.getBoundsMin().getX() + 1);
            hUnits = Math.max(1, this.data.getBoundsMax().getY() - this.data.getBoundsMin().getY() + 1);
            dUnits = Math.max(1, this.data.getBoundsMax().getZ() - this.data.getBoundsMin().getZ() + 1);
        }
        else
        {
            wUnits = Math.max(1, this.data.getSize().getX());
            hUnits = Math.max(1, this.data.getSize().getY());
            dUnits = Math.max(1, this.data.getSize().getZ());
        }

        int maxUnits = Math.max(wUnits, Math.max(hUnits, dUnits));
        float auto = maxUnits > 0 ? targetPixels / (baseScale * maxUnits) : 1F;
        float finalScale = this.form.uiScale.get() * Math.min(1F, auto);
        float structScaleUI = Math.max(Math.max(this.form.scaleX.get(), this.form.scaleY.get()), this.form.scaleZ.get());

        finalScale *= structScaleUI;
        matrices.scale(finalScale, finalScale, finalScale);
        MatrixStackUtils.invertUiNormalY(matrices);

        Vector3f light0 = new Vector3f(0.85F, 0.85F, -1F).normalize();
        Vector3f light1 = new Vector3f(-0.85F, 0.85F, 1F).normalize();
        RenderSystem.setupLevelDiffuseLighting(light0, light1, RenderSystem.getModelViewMatrix());

        this.checkLightState();

        Color storedFormColor = this.form.color.get();
        Color rawFormColor = storedFormColor.copyBakingColorGrade();
        Color formColor = rawFormColor.copy();
        boolean colorTransformWanted = FormColorEffects.wantsColorTintOverlay(storedFormColor);
        Color tint = Color.white();

        if (FormColorEffects.shouldBakeFormColor(storedFormColor))
        {
            tint.mul(rawFormColor);
        }

        this.form.applyFormOpacity(tint);
        this.form.applyFormOpacity(formColor);

        GlowSettings glowSettings = this.form.glowSettings.get();
        Color legacyGlow = this.form.glowingColor.get();
        float glowIntensity = glowSettings.resolveIntensity(legacyGlow);

        if (glowIntensity < 0F)
        {
            FormColorEffects.blendFormGlowBrighten(tint, glowSettings, legacyGlow);
        }

        boolean irisWorldPaintDeferral = BBSRendering.isIrisWorldPaintDeferral();
        boolean deferColorTintToOverlay = colorTransformWanted && irisWorldPaintDeferral;
        Color resolvedPaint = FormColorEffects.resolvePaintColor(this.form.paintSettings.get(), this.form.paintColor.get());
        boolean positivePaint = FormColorEffects.hasPositivePaint(this.form.paintSettings.get(), this.form.paintColor.get());
        boolean positiveGlow = glowIntensity > 0F;
        Function<VertexConsumer, VertexConsumer> mainRecolor = this.getMainConsumer(tint, resolvedPaint);

        IModelVAO vao = this.getVao();

            if (vao != null)
            {
                GameRenderer gameRenderer = MinecraftClient.getInstance().gameRenderer;
                ShaderProgram shader = BBSShaders.getModel();

                gameRenderer.getLightmapTextureManager().enable();
                gameRenderer.getOverlayTexture().setupOverlayColor();

                RenderSystem.setShader(() -> shader);
                RenderSystem.setShaderTexture(0, PlayerScreenHandler.BLOCK_ATLAS_TEXTURE);

            boolean needBlendUI = tint.a < 0.999F || this.data.hasTranslucentLayer();

                if (needBlendUI)
                {
                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                }
                else
                {
                    RenderSystem.disableBlend();
                }

                RenderSystem.enableCull();

            this.overlayRenderer.prepareVaoPaintForMainPass(resolvedPaint);
            this.overlayRenderer.prepareVaoGlowForMainPass(glowSettings, legacyGlow, glowIntensity);

                try
                {
                    ModelVAORenderer.render(shader, vao, matrices, tint.r, tint.g, tint.b, tint.a, LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
                }
                finally
                {
                this.overlayRenderer.clearVaoColorTint();
                this.overlayRenderer.clearVaoPaint();
                this.overlayRenderer.clearVaoGlow();
            }

            FormRenderingContext passContext = new FormRenderingContext()
                            .set(FormRenderType.PREVIEW, null, matrices, LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, 0F);

            if (this.data.hasBlockEntityLayer())
            {
                this.renderBlockEntitiesPass(passContext, matrices, LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, true);
            }

            if (this.data.hasBiomeTintedLayer())
            {
                this.renderLayerGroup(this.data.getBiomeTintedBlocks(), passContext, matrices, LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, mainRecolor, null, true);
            }

            if (this.data.hasAnimatedLayer())
            {
                this.renderLayerGroup(this.data.getAnimatedBlocks(), passContext, matrices, LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, mainRecolor, null, false);
            }

            if (this.data.hasTranslucentLayer())
            {
                this.renderLayerGroup(this.data.getTranslucentBlocks(), passContext, matrices, LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, mainRecolor, null, false);
            }

                gameRenderer.getLightmapTextureManager().disable();
                gameRenderer.getOverlayTexture().teardownOverlayColor();
                RenderSystem.disableBlend();

                if (positivePaint)
                {
                    EffectTransform paintTransform = this.form.paintSettings.get().transform;
                    this.overlayRenderer.renderStructurePaintOverlay(this.data, vao, passContext, matrices, resolvedPaint, tint.a, OverlayTexture.DEFAULT_UV, true, BBSRendering.isIrisShadersEnabled(), paintTransform, glowSettings, legacyGlow, glowIntensity, layer -> this.renderPaintLayer(layer, passContext, matrices, OverlayTexture.DEFAULT_UV, null), (s) -> this.renderStructureCulledWorld(passContext, s, FormUtilsClient.getProvider(), LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, BBSRendering.isIrisShadersEnabled(), null, true, false));
                }

                if (colorTransformWanted)
                {
                    this.overlayRenderer.renderStructureColorTintOverlay(this.data, this.form, passContext, matrices, formColor, tint.a, OverlayTexture.DEFAULT_UV, true, BBSRendering.isIrisShadersEnabled(), deferColorTintToOverlay, layer -> this.renderPaintLayer(layer, passContext, matrices, OverlayTexture.DEFAULT_UV, null), (s) -> this.renderStructureCulledWorld(passContext, s, FormUtilsClient.getProvider(), LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, BBSRendering.isIrisShadersEnabled(), null, true, false));
                }

                if (positiveGlow)
                {
                    this.overlayRenderer.renderStructureGlowOverlay(this.data, passContext, matrices, glowSettings, legacyGlow, glowIntensity, tint.a, OverlayTexture.DEFAULT_UV, false, BBSRendering.isIrisShadersEnabled(), null, (s) -> this.renderStructureCulledWorld(passContext, s, FormUtilsClient.getProvider(), LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, BBSRendering.isIrisShadersEnabled(), null, true, false));
                }
        }

        DiffuseLighting.disableGuiDepthLighting();
        matrices.pop();
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        StructureVaoManager.ensureLightingRevision();
        this.ensureLoaded();

        context.stack.push();

        try
        {
            context.stack.scale(this.form.scaleX.get(), this.form.scaleY.get(), this.form.scaleZ.get());

            boolean picking = context.isPicking();
            this.checkLightState();

            IModelVAO vao = this.getVao();

            Color storedFormColor3D = this.form.color.get();
            Color rawFormColor3D = storedFormColor3D.copyBakingColorGrade();
            Color formColor3D = rawFormColor3D.copy();
            boolean colorTransformWanted = FormColorEffects.wantsColorTintOverlay(storedFormColor3D);
            Color mainTint3D = new Color().set(context.color);

            boolean shadowPass = context.isShadowPass || BBSRendering.isIrisShadowPass();

            if (shadowPass)
            {
                mainTint3D.a *= storedFormColor3D.a;
            }
            else if (FormColorEffects.shouldBakeFormColor(storedFormColor3D))
            {
                mainTint3D.mul(rawFormColor3D);
            }

            this.form.applyFormOpacity(mainTint3D);
            this.form.applyFormOpacity(formColor3D);

            FormColorEffects.applyShadowPassColorFix(mainTint3D, storedFormColor3D, this.form.paintSettings.get(), this.form.paintColor.get(), shadowPass);
            this.applyBlockEntityOnlyShaderShadow(mainTint3D, shadowPass);

            if (mainTint3D.a <= 0.001F && !shadowPass && !picking)
            {
                return;
            }

            GlowSettings glowSettings = this.form.glowSettings.get();
            Color legacyGlow = this.form.glowingColor.get();
            float glowIntensity = glowSettings.resolveIntensity(legacyGlow);
            EffectTransform glowTransform = FormColorEffects.resolveGlowEffectTransform(glowSettings, legacyGlow);
            boolean hasGlowTransform = glowTransform != null && glowTransform.isActive();
            boolean positiveGlow = !picking && !shadowPass && glowIntensity > 0F;
            boolean hasEmissiveGlow = positiveGlow && !glowSettings.resolvePaintOnly();

            boolean localPreview = context.isLocalPreview();
            boolean noshadingDefer = !localPreview
                && !shadowPass
                && BBSRendering.needsIrisNoshadingOpacityDeferral(mainTint3D.a, this.form.noshadingOpacity.get());
            boolean softPostDeferred = !localPreview
                && !shadowPass
                && ShaderOpacityPatch.shouldDelayUntilPostDeferred(mainTint3D.a)
                && !noshadingDefer;

            boolean irisWorldPaintDeferral = BBSRendering.isIrisWorldPaintDeferral();
            boolean deferColorTintToOverlay = colorTransformWanted && irisWorldPaintDeferral && !shadowPass;
            PaintSettings paintSettings = this.form.paintSettings.get();
            Color legacyPaint = this.form.paintColor.get();
            Color resolvedPaint = FormColorEffects.resolvePaintColor(paintSettings, legacyPaint);
            boolean positivePaint = !picking && !shadowPass && FormColorEffects.hasPositivePaint(paintSettings, legacyPaint);
            boolean applyColorTint = colorTransformWanted && !picking && !shadowPass;
            Function<VertexConsumer, VertexConsumer> mainRecolor = this.getMainConsumer(mainTint3D, resolvedPaint);
            Color vaoTint = mainTint3D.copy();
            Function<VertexConsumer, VertexConsumer> layerRecolor = mainRecolor;

            if (glowIntensity < 0F)
            {
                FormColorEffects.blendFormGlowBrighten(mainTint3D, glowSettings, legacyGlow);
                FormColorEffects.blendFormGlowBrighten(vaoTint, glowSettings, legacyGlow);
            }
            else if (irisWorldPaintDeferral && hasEmissiveGlow && !softPostDeferred && !noshadingDefer && !hasGlowTransform)
            {
                /* Must hit the Iris entity/gbuffer pass — post-composite BBS additive never blooms.
                 * Base emission on a neutral white base so form color tint does not distort bloom. */
                vaoTint = new Color(1F, 1F, 1F, mainTint3D.a);
                FormColorEffects.blendFormGlowBrighten(vaoTint, glowSettings, legacyGlow);
                layerRecolor = this.getMainConsumer(new Color(1F, 1F, 1F, mainTint3D.a), resolvedPaint);
            }

            boolean shaders = BBSRendering.isIrisShadersEnabled();

            if (vao != null)
            {
                int light = context.isPicking() ? 0 : context.light;
                GameRenderer gameRenderer = MinecraftClient.getInstance().gameRenderer;

                gameRenderer.getLightmapTextureManager().enable();
                gameRenderer.getOverlayTexture().setupOverlayColor();

                if (context.isPicking())
                {
                    IModelVAO pickingVao = this.getPickingVao();

                    this.setupTarget(context, BBSShaders.getPickerModelsProgram());
                    RenderSystem.setShader(BBSShaders::getPickerModelsProgram);
                    RenderSystem.enableBlend();
                    RenderSystem.setShaderTexture(0, PlayerScreenHandler.BLOCK_ATLAS_TEXTURE);

                    ModelVAORenderer.render(BBSShaders.getPickerModelsProgram(), pickingVao, context.stack, mainTint3D.r, mainTint3D.g, mainTint3D.b, mainTint3D.a, light, context.overlay);
                }
                else
                {
                    if (softPostDeferred || noshadingDefer)
                    {
                        boolean irisCamera = BBSRendering.isIrisWorldModelPass() && !noshadingDefer;
                        Matrix4f positionMatrix = irisCamera
                            ? new Matrix4f(context.stack.peek().getPositionMatrix())
                            : ModelVAORenderer.capturePaintOverlayRootMatrix(new Matrix4f(context.stack.peek().getPositionMatrix()));
                        Matrix3f normalMatrix = new Matrix3f(context.stack.peek().getNormalMatrix());
                        Matrix4f sortRootMatrix = new Matrix4f(context.stack.peek().getPositionMatrix());
                        Color mainTintSnapshot = mainTint3D.copy();

                        /* Soft Structure draws entity_translucent — same Iris discard floor as
                         * ModelForm (~28/255). Ease only the ultra-low band; squared-alpha
                         * vanish near ~82/255 is fixed in RecolorVertexSodiumConsumer. */
                        if (mainTintSnapshot.a > 0.001F && mainTintSnapshot.a < BBSRendering.TRANSLUCENT_ALPHA_DISCARD_REF)
                        {
                            mainTintSnapshot.a = BBSRendering.easeDeferredModelAlpha(mainTintSnapshot.a);
                        }

                        Color formColor3DSnapshot = formColor3D.copy();
                        Color resolvedPaintSnapshot = resolvedPaint == null ? null : resolvedPaint.copy();
                        PaintSettings paintSettingsSnapshot = paintSettings == null ? null : paintSettings.copy();
                        int lightSnapshot = light;
                        int overlaySnapshot = context.overlay;
                        boolean depthWrite = ShaderOpacityPatch.shouldWriteDepthForOpacity(mainTint3D.a);
                        boolean afterFluids = ShaderOpacityPatch.shouldFlushAfterFluids(mainTint3D.a);
                        boolean positiveGlowSnapshot = positiveGlow && !glowSettings.resolvePaintOnly();
                        float glowIntensitySnapshot = glowIntensity;
                        GlowSettings glowSettingsSnapshot = glowSettings;
                        Color legacyGlowSnapshot = legacyGlow;
                        boolean positivePaintSnapshot = positivePaint;
                        boolean applyColorTintSnapshot = applyColorTint;
                        boolean beTintSnapshot = !irisWorldPaintDeferral;
                        IModelVAO vaoSnapshot = vao;
                        boolean shadersSnapshot = shaders;
                        Function<VertexConsumer, VertexConsumer> mainRecolorSnapshot = this.getMainConsumer(mainTintSnapshot, resolvedPaintSnapshot);
                        RenderInfo sortInfo = this.calculateRenderInfo(context, false);
                        List<BlockEntry> softBlocks = new ArrayList<>(this.data.getBlocks());

                        if (noshadingDefer)
                        {
                            Runnable deferredDraw = () -> this.runStructureSoftDeferredPass(
                                context, positionMatrix, normalMatrix, mainRecolorSnapshot, lightSnapshot, overlaySnapshot,
                                beTintSnapshot, depthWrite, vaoSnapshot, mainTintSnapshot, positivePaintSnapshot,
                                paintSettingsSnapshot, resolvedPaintSnapshot, applyColorTintSnapshot, formColor3DSnapshot,
                                positiveGlowSnapshot, glowSettingsSnapshot, legacyGlowSnapshot, glowIntensitySnapshot,
                                shadersSnapshot, true);

                            ModelVAORenderer.submitDeferredTranslucentModel(deferredDraw, depthWrite);
                        }
                        else
                        {
                            /* One queue entry per block (same contract as soft limbs / soft Block forms)
                             * so nearby soft BlockForms can interleave instead of painting over the
                             * whole tree after a single form-origin Structure entry. */
                            double nearestBlockKey = Double.POSITIVE_INFINITY;

                            for (BlockEntry entry : softBlocks)
                            {
                                double blockKey = this.computeStructureBlockFormSortKey(entry, sortInfo, sortRootMatrix, context);

                                /* Prefer drawing cutout/leaves after solids / soft BlockForms at the
                                 * same depth (painter: nearer key draws later). */
                                if (this.isSoftStructureNonSolid(entry.state))
                                {
                                    blockKey -= 1.0E-3D;
                                }

                                if (blockKey < nearestBlockKey)
                                {
                                    nearestBlockKey = blockKey;
                                }

                                BlockEntry entrySnapshot = entry;
                                Runnable blockDraw = () -> this.runStructureSoftBlockDeferredColor(
                                    context, positionMatrix, normalMatrix, entrySnapshot, sortInfo,
                                    mainRecolorSnapshot, lightSnapshot, overlaySnapshot);

                                if (irisCamera)
                                {
                                    ShaderOpacityPatch.submitPostDeferredForm(0D, blockKey, false, afterFluids, blockDraw);
                                }
                                else
                                {
                                    ShaderOpacityPatch.submitPostDeferredBbsForm(0D, blockKey, false, afterFluids, blockDraw);
                                }
                            }

                            if (softBlocks.isEmpty())
                            {
                                nearestBlockKey = this.computeStructureFormSortKey(sortRootMatrix, context);
                            }

                            /* After all Structure color entries (and after same-depth BlockForms that
                             * lost the non-solid bias). */
                            double tailKey = nearestBlockKey - 1.0E-3D;
                            Runnable tailDraw = () -> this.runStructureSoftDeferredTail(
                                context, positionMatrix, normalMatrix, lightSnapshot, overlaySnapshot,
                                beTintSnapshot, depthWrite, vaoSnapshot, mainTintSnapshot, positivePaintSnapshot,
                                paintSettingsSnapshot, resolvedPaintSnapshot, applyColorTintSnapshot, formColor3DSnapshot,
                                positiveGlowSnapshot, glowSettingsSnapshot, legacyGlowSnapshot, glowIntensitySnapshot,
                                shadersSnapshot);

                            if (irisCamera)
                            {
                                ShaderOpacityPatch.submitPostDeferredForm(0D, tailKey, depthWrite, afterFluids, tailDraw);
                            }
                            else
                            {
                                ShaderOpacityPatch.submitPostDeferredBbsForm(0D, tailKey, depthWrite, afterFluids, tailDraw);
                            }
                        }
                    }
                    else
                    {
                        if (shadowPass)
                        {
                            ShaderOpacityPatch.beginShadowForm();
                        }

                        try
                        {
                            ShaderProgram shader = (BBSRendering.isIrisShadersEnabled() && BBSRendering.isRenderingWorld())
                                ? GameRenderer.getRenderTypeEntityTranslucentCullProgram()
                                : BBSShaders.getModel();

                            RenderSystem.setShader(() -> shader);
                            RenderSystem.setShaderTexture(0, PlayerScreenHandler.BLOCK_ATLAS_TEXTURE);
                            RenderSystem.enableBlend();
                            RenderSystem.defaultBlendFunc();

                            this.overlayRenderer.prepareVaoPaintForMainPass(resolvedPaint);
                            this.overlayRenderer.prepareVaoGlowForMainPass(glowSettings, legacyGlow, glowIntensity);

                            try
                            {
                                ModelVAORenderer.render(shader, vao, context.stack, vaoTint.r, vaoTint.g, vaoTint.b, vaoTint.a, light, context.overlay);
                            }
                            finally
                            {
                                this.overlayRenderer.clearVaoColorTint();
                                this.overlayRenderer.clearVaoPaint();
                                this.overlayRenderer.clearVaoGlow();
                            }

                            Color layerShaderTint = (BBSRendering.isIrisShadersEnabled() && BBSRendering.isRenderingWorld()) ? vaoTint : mainTint3D;

                            if (this.data.hasBlockEntityLayer())
                            {
                                boolean beTint = !irisWorldPaintDeferral;
                                this.renderBlockEntitiesPass(context, context.stack, light, context.overlay, beTint);
                            }

                            if (this.data.hasBiomeTintedLayer())
                            {
                                this.renderLayerGroup(this.data.getBiomeTintedBlocks(), context, context.stack, light, context.overlay, layerRecolor, layerShaderTint, true);
                            }

                            if (this.data.hasAnimatedLayer())
                            {
                                this.renderLayerGroup(this.data.getAnimatedBlocks(), context, context.stack, light, context.overlay, layerRecolor, layerShaderTint, false);
                            }

                            if (this.data.hasTranslucentLayer())
                            {
                                this.renderLayerGroup(this.data.getTranslucentBlocks(), context, context.stack, light, context.overlay, layerRecolor, layerShaderTint, false);
                            }
                        }
                        finally
                        {
                            if (shadowPass)
                            {
                                ShaderOpacityPatch.endShadowForm();
                            }
                        }
                    }
                }

                boolean submitIrisOverlays = irisWorldPaintDeferral && !noshadingDefer;

                /* Soft + Iris: keep color/paint/glow masks on the Iris paint-overlay queue
                 * (same contract as soft BlockForm). Drawing them only inside the soft flush
                 * skips beginColorTintOverlayPass and the masks vanish with shaders. */
                if (((!softPostDeferred && !noshadingDefer) || (softPostDeferred && submitIrisOverlays)) && applyColorTint)
                {
                    if (irisWorldPaintDeferral)
                    {
                        this.overlayRenderer.submitDeferredStructureColorTintOverlay(this.data, this.form, context, formColor3D, mainTint3D.a, context.overlay, true, shaders, layer -> this.renderPaintLayer(layer, context, context.stack, context.overlay, null), (s) -> this.renderStructureCulledWorld(context, s, FormUtilsClient.getProvider(), light, context.overlay, shaders, null, true, false));
                    }
                    else if (!softPostDeferred)
                    {
                        this.overlayRenderer.renderStructureColorTintOverlay(this.data, this.form, context, context.stack, formColor3D, mainTint3D.a, context.overlay, true, shaders, false, layer -> this.renderPaintLayer(layer, context, context.stack, context.overlay, null), (s) -> this.renderStructureCulledWorld(context, s, FormUtilsClient.getProvider(), light, context.overlay, shaders, null, true, false));
                    }
                }

                if ((!softPostDeferred && !noshadingDefer && positivePaint) || (softPostDeferred && submitIrisOverlays && positivePaint))
                {
                    EffectTransform paintTransform = paintSettings.transform;
                    this.overlayRenderer.submitDeferredStructurePaintOverlay(this.data, vao, context, resolvedPaint, mainTint3D.a, context.overlay, true, shaders, paintTransform, glowSettings, legacyGlow, glowIntensity, layer -> this.renderPaintLayer(layer, context, context.stack, context.overlay, null), (s) -> this.renderStructureCulledWorld(context, s, FormUtilsClient.getProvider(), light, context.overlay, shaders, null, true, false));
                }

                if ((!softPostDeferred && !noshadingDefer && positiveGlow) || (softPostDeferred && submitIrisOverlays && positiveGlow))
                {
                    if (irisWorldPaintDeferral)
                    {
                        this.overlayRenderer.submitDeferredStructureGlowOverlay(this.data, context, glowSettings, legacyGlow, glowIntensity, mainTint3D.a, context.overlay, false, shaders, hasGlowTransform ? glowTransform : null, null, (s) -> this.renderStructureCulledWorld(context, s, FormUtilsClient.getProvider(), light, context.overlay, shaders, null, true, false));
                    }
                    else if (!softPostDeferred)
                    {
                        this.overlayRenderer.renderStructureGlowOverlay(this.data, context, context.stack, glowSettings, legacyGlow, glowIntensity, mainTint3D.a, context.overlay, false, shaders, null, (s) -> this.renderStructureCulledWorld(context, s, FormUtilsClient.getProvider(), light, context.overlay, shaders, null, true, false));
                    }
                }

                gameRenderer.getLightmapTextureManager().disable();
                gameRenderer.getOverlayTexture().teardownOverlayColor();

                RenderSystem.disableBlend();
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc(GL11.GL_LEQUAL);
            }

            CustomVertexConsumerProvider.clearRunnables();
        }
        finally
        {
            context.stack.pop();
        }
    }

    /**
     * Noshading soft Structure: single deferred pass (sorted color + stamp + overlays).
     */
    private void runStructureSoftDeferredPass(FormRenderingContext context, Matrix4f positionMatrix, Matrix3f normalMatrix, Function<VertexConsumer, VertexConsumer> mainRecolor, int light, int overlay, boolean beTint, boolean depthWrite, IModelVAO vao, Color mainTint, boolean positivePaint, PaintSettings paintSettings, Color resolvedPaint, boolean applyColorTint, Color formColor3D, boolean positiveGlow, GlowSettings glowSettings, Color legacyGlow, float glowIntensity, boolean shaders, boolean sortedColor)
    {
        MatrixStack overlayStack = new MatrixStack();
        GameRenderer deferredGameRenderer = MinecraftClient.getInstance().gameRenderer;

        overlayStack.peek().getPositionMatrix().set(positionMatrix);
        overlayStack.peek().getNormalMatrix().set(normalMatrix);

        deferredGameRenderer.getLightmapTextureManager().enable();
        deferredGameRenderer.getOverlayTexture().setupOverlayColor();

        try
        {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            if (sortedColor)
            {
                ShaderOpacityPatch.setFlushingDepthWrite(false);
                RenderSystem.depthMask(false);
                this.renderStructureSoftSortedColor(context, overlayStack, mainRecolor, light, overlay);
            }

            this.runStructureSoftDeferredTailBody(context, overlayStack, light, overlay, beTint, depthWrite, vao, mainTint, positivePaint, paintSettings, resolvedPaint, applyColorTint, formColor3D, positiveGlow, glowSettings, legacyGlow, glowIntensity, shaders);
        }
        finally
        {
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
            ShaderOpacityPatch.setFlushingDepthWrite(depthWrite);
            RenderSystem.depthMask(depthWrite);

            if (!ShaderOpacityPatch.isFlushingPostDeferred())
            {
                deferredGameRenderer.getLightmapTextureManager().disable();
                deferredGameRenderer.getOverlayTexture().teardownOverlayColor();
            }
        }
    }

    /**
     * Soft Structure color for one block — own post-deferred queue entry so soft BlockForms
     * can sort between trunk and leaves.
     * <p>
     * Iris: soft cutout/leaves draw with {@code depthMask false}, so pack fog that samples
     * {@code depthtex} sees terrain behind the foliage and washes leaf faces. Soft limbs avoid
     * this via a mesh depth stamp; solid Structure blocks get the VAO depth stamp. Non-solids
     * need a per-block depth-only prepass before color (color still depthMask false for soft
     * compositing). Vanilla FogStart/FogEnd toggles do nothing here — Iris ignores them.
     */
    private void runStructureSoftBlockDeferredColor(FormRenderingContext context, Matrix4f positionMatrix, Matrix3f normalMatrix, BlockEntry entry, RenderInfo info, Function<VertexConsumer, VertexConsumer> recolor, int light, int overlay)
    {
        MatrixStack overlayStack = new MatrixStack();
        GameRenderer deferredGameRenderer = MinecraftClient.getInstance().gameRenderer;
        boolean irisCutoutDepthPrepass = BBSRendering.isIrisShadersEnabled()
            && this.isSoftStructureNonSolid(entry.state);

        overlayStack.peek().getPositionMatrix().set(positionMatrix);
        overlayStack.peek().getNormalMatrix().set(normalMatrix);

        deferredGameRenderer.getLightmapTextureManager().enable();
        deferredGameRenderer.getOverlayTexture().setupOverlayColor();

        try
        {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            /* Never leave a leftover ColorModulator.a from prior form draws — Iris multiplies
             * it with recolor vertex alpha (opacity² → vanish near 82/255, leaf shadows thin). */
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
            RenderSystem.setShaderTexture(0, PlayerScreenHandler.BLOCK_ATLAS_TEXTURE);
            StructureData.syncFancyGraphicsFromOptions();

            VertexConsumerProvider.Immediate immediateConsumers = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();

            overlayStack.push();
            overlayStack.translate(entry.pos.getX() - info.pivotX, entry.pos.getY() - info.pivotY, entry.pos.getZ() - info.pivotZ);

            if (irisCutoutDepthPrepass)
            {
                ShaderOpacityPatch.setFlushingDepthWrite(true);
                RenderSystem.depthMask(true);
                RenderSystem.colorMask(false, false, false, false);
                RenderSystem.disableBlend();
                this.renderStructureSoftBlock(entry, info, overlayStack, immediateConsumers, recolor);
                immediateConsumers.draw();
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.colorMask(true, true, true, true);
            }

            ShaderOpacityPatch.setFlushingDepthWrite(false);
            RenderSystem.depthMask(false);
            this.renderStructureSoftBlock(entry, info, overlayStack, immediateConsumers, recolor);
            overlayStack.pop();
            immediateConsumers.draw();
            RecolorVertexConsumer.newColor = null;
            CustomVertexConsumerProvider.clearRunnables();
        }
        finally
        {
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
            ShaderOpacityPatch.setFlushingDepthWrite(false);
            RenderSystem.depthMask(false);
        }
    }

    /**
     * Soft Structure tail after per-block colors: block entities, solid VAO depth stamp, overlays.
     * Sorted with the nearest block key so it runs after Structure color entries.
     */
    private void runStructureSoftDeferredTail(FormRenderingContext context, Matrix4f positionMatrix, Matrix3f normalMatrix, int light, int overlay, boolean beTint, boolean depthWrite, IModelVAO vao, Color mainTint, boolean positivePaint, PaintSettings paintSettings, Color resolvedPaint, boolean applyColorTint, Color formColor3D, boolean positiveGlow, GlowSettings glowSettings, Color legacyGlow, float glowIntensity, boolean shaders)
    {
        MatrixStack overlayStack = new MatrixStack();
        GameRenderer deferredGameRenderer = MinecraftClient.getInstance().gameRenderer;

        overlayStack.peek().getPositionMatrix().set(positionMatrix);
        overlayStack.peek().getNormalMatrix().set(normalMatrix);

        deferredGameRenderer.getLightmapTextureManager().enable();
        deferredGameRenderer.getOverlayTexture().setupOverlayColor();

        try
        {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            this.runStructureSoftDeferredTailBody(context, overlayStack, light, overlay, beTint, depthWrite, vao, mainTint, positivePaint, paintSettings, resolvedPaint, applyColorTint, formColor3D, positiveGlow, glowSettings, legacyGlow, glowIntensity, shaders);
        }
        finally
        {
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
            ShaderOpacityPatch.setFlushingDepthWrite(depthWrite);
            RenderSystem.depthMask(depthWrite);

            if (!ShaderOpacityPatch.isFlushingPostDeferred())
            {
                deferredGameRenderer.getLightmapTextureManager().disable();
                deferredGameRenderer.getOverlayTexture().teardownOverlayColor();
            }
        }
    }

    private void runStructureSoftDeferredTailBody(FormRenderingContext context, MatrixStack overlayStack, int light, int overlay, boolean beTint, boolean depthWrite, IModelVAO vao, Color mainTint, boolean positivePaint, PaintSettings paintSettings, Color resolvedPaint, boolean applyColorTint, Color formColor3D, boolean positiveGlow, GlowSettings glowSettings, Color legacyGlow, float glowIntensity, boolean shaders)
    {
        if (this.data.hasBlockEntityLayer())
        {
            this.renderBlockEntitiesPass(context, overlayStack, light, overlay, beTint);
            ShaderOpacityPatch.setFlushingDepthWrite(false);
            RenderSystem.depthMask(false);
        }

        if (depthWrite)
        {
            ShaderOpacityPatch.setFlushingDepthWrite(true);
            RenderSystem.depthMask(true);
            RenderSystem.colorMask(false, false, false, false);
            RenderSystem.disableBlend();

            try
            {
                this.renderStructureSoftDepthStamp(overlayStack, vao, mainTint, light, overlay);
            }
            finally
            {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.colorMask(true, true, true, true);
            }
        }

        /* Iris paint/tint/glow overlays are submitted on the paint-overlay queue when soft
         * (see softPostDeferred + submitIrisOverlays). Only draw them here without Iris. */
        boolean irisOverlays = BBSRendering.isIrisWorldPaintDeferral();

        if (positivePaint && !irisOverlays)
        {
            EffectTransform paintTransform = paintSettings == null ? null : paintSettings.transform;

            this.overlayRenderer.renderStructurePaintOverlay(this.data, vao, context, overlayStack, resolvedPaint, mainTint.a, overlay, true, shaders, paintTransform, glowSettings, legacyGlow, glowIntensity, layer -> this.renderPaintLayer(layer, context, overlayStack, overlay, null), (s) -> this.renderStructureCulledWorld(context, s, FormUtilsClient.getProvider(), light, overlay, shaders, null, true, false));
        }

        if (applyColorTint && !irisOverlays)
        {
            this.overlayRenderer.renderStructureColorTintOverlay(this.data, this.form, context, overlayStack, formColor3D, mainTint.a, overlay, true, shaders, false, layer -> this.renderPaintLayer(layer, context, overlayStack, overlay, null), (s) -> this.renderStructureCulledWorld(context, s, FormUtilsClient.getProvider(), light, overlay, shaders, null, true, false));
        }

        if (positiveGlow && !irisOverlays)
        {
            ShaderOpacityPatch.setFlushingDepthWrite(false);
            RenderSystem.depthMask(false);
            this.overlayRenderer.renderStructureGlowOverlay(this.data, context, overlayStack, glowSettings, legacyGlow, glowIntensity, mainTint.a, overlay, false, shaders, null, (s) -> this.renderStructureCulledWorld(context, s, FormUtilsClient.getProvider(), light, overlay, shaders, null, true, false));
        }

        ShaderOpacityPatch.setFlushingDepthWrite(depthWrite);
        RenderSystem.depthMask(depthWrite);
        CustomVertexConsumerProvider.clearRunnables();
    }

    /**
     * Soft Structure color: every block back-to-front with depth-write off.
     * Used by the noshading single-pass path; Iris/BBS soft uses per-block queue entries.
     */
    private void renderStructureSoftSortedColor(FormRenderingContext context, MatrixStack stack, Function<VertexConsumer, VertexConsumer> recolor, int light, int overlay)
    {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, PlayerScreenHandler.BLOCK_ATLAS_TEXTURE);
        StructureData.syncFancyGraphicsFromOptions();
        ShaderOpacityPatch.setFlushingDepthWrite(false);
        RenderSystem.depthMask(false);

        RenderInfo info = this.calculateRenderInfo(context, false);
        List<BlockEntry> sorted = new ArrayList<>(this.data.getBlocks());
        Matrix4f drawMatrix = stack.peek().getPositionMatrix();
        Matrix4f viewLocal = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(drawMatrix);

        sorted.sort((a, b) ->
        {
            int byDepth = Double.compare(
                this.computeStructureBlockViewSortKey(b, info, viewLocal),
                this.computeStructureBlockViewSortKey(a, info, viewLocal)
            );

            if (byDepth != 0)
            {
                return byDepth;
            }

            return Boolean.compare(this.isSoftStructureNonSolid(a.state), this.isSoftStructureNonSolid(b.state));
        });

        VertexConsumerProvider.Immediate immediateConsumers = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();

        for (BlockEntry entry : sorted)
        {
            stack.push();
            stack.translate(entry.pos.getX() - info.pivotX, entry.pos.getY() - info.pivotY, entry.pos.getZ() - info.pivotZ);
            this.renderStructureSoftBlock(entry, info, stack, immediateConsumers, recolor);
            stack.pop();
        }

        immediateConsumers.draw();
        RenderSystem.disableBlend();
        RecolorVertexConsumer.newColor = null;
        ShaderOpacityPatch.setFlushingDepthWrite(false);
    }

    /**
     * Queue sort key for one Structure block — same formula as soft {@code BlockForm}
     * ({@code capturePaintOverlayRootMatrix} + film look-axis −Z / lengthSq) so soft
     * BlockForms and Structure leaves share one comparable depth axis.
     */
    private double computeStructureBlockFormSortKey(BlockEntry entry, RenderInfo info, Matrix4f formLocalMatrix, FormRenderingContext context)
    {
        Matrix4f blockMatrix = new Matrix4f(formLocalMatrix);

        blockMatrix.translate(
            entry.pos.getX() - info.pivotX + 0.5F,
            entry.pos.getY() - info.pivotY + 0.5F,
            entry.pos.getZ() - info.pivotZ + 0.5F
        );

        return this.computeStructureCompatibleFormSortKey(blockMatrix, context);
    }

    private double computeStructureBlockViewSortKey(BlockEntry entry, RenderInfo info, Matrix4f viewLocal)
    {
        Vector4f center = new Vector4f(
            entry.pos.getX() - info.pivotX + 0.5F,
            entry.pos.getY() - info.pivotY + 0.5F,
            entry.pos.getZ() - info.pivotZ + 0.5F,
            1F
        );

        viewLocal.transform(center);

        return -center.z;
    }

    private boolean isSoftStructureNonSolid(BlockState state)
    {
        if (state == null)
        {
            return false;
        }

        if (state.getBlock() instanceof LeavesBlock
            || StructureData.isTranslucentBlock(state)
            || StructureData.isBiomeTinted(state))
        {
            return true;
        }

        RenderLayer terrain = RenderLayers.getBlockLayer(state);

        return terrain == RenderLayer.getCutout()
            || terrain == RenderLayer.getCutoutMipped()
            || terrain == RenderLayer.getTranslucent()
            || terrain == RenderLayer.getTranslucentMovingBlock()
            || terrain == RenderLayer.getTripwire();
    }

    private void renderStructureSoftBlock(BlockEntry entry, RenderInfo info, MatrixStack stack, VertexConsumerProvider consumers, Function<VertexConsumer, VertexConsumer> recolor)
    {
        if (entry.state.getBlock() instanceof LeavesBlock)
        {
            this.renderStructureLeaves(entry.state, entry.pos, info.view, stack, consumers, recolor, false);

            return;
        }

        boolean shadersEnabled = BBSRendering.isIrisShadersEnabled() && BBSRendering.isRenderingWorld();
        RenderLayer layer = TexturedRenderLayers.getEntityTranslucentCull();
        VertexConsumer vc = consumers.getBuffer(layer);

        if (recolor != null)
        {
            vc = recolor.apply(vc);
        }

        if (this.form.renderFluid.get() && !entry.state.getFluidState().isEmpty())
        {
            RenderLayer fluidLayer = shadersEnabled
                ? RenderLayers.getEntityBlockLayer(entry.state, false)
                : RenderLayers.getFluidLayer(entry.state.getFluidState());
            VertexConsumer fluidVc = consumers.getBuffer(fluidLayer);

            if (recolor != null)
            {
                fluidVc = recolor.apply(fluidVc);
            }

            fluidVc = new TransformingVertexConsumer(fluidVc, stack.peek(), entry.pos, shadersEnabled);
            MinecraftClient.getInstance().getBlockRenderManager().renderFluid(entry.pos, info.view, fluidVc, entry.state, entry.state.getFluidState());
        }

        if (entry.state.getRenderType() != BlockRenderType.INVISIBLE)
        {
            MinecraftClient.getInstance().getBlockRenderManager().renderBlock(entry.state, entry.pos, info.view, stack, vc, true, Random.create());
        }
    }

    /**
     * Depth-only stamp of the solid structure VAO after soft color (no color write).
     * Keeps other forms/world occluded without letting soft color depth-kill itself.
     */
    private void renderStructureSoftDepthStamp(MatrixStack stack, IModelVAO vao, Color mainTint, int light, int overlay)
    {
        if (vao == null)
        {
            return;
        }

        ShaderProgram shader = (BBSRendering.isIrisShadersEnabled() && BBSRendering.isRenderingWorld())
            ? GameRenderer.getRenderTypeEntityTranslucentCullProgram()
            : BBSShaders.getModel();

        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, PlayerScreenHandler.BLOCK_ATLAS_TEXTURE);
        ModelVAORenderer.render(shader, vao, stack, mainTint.r, mainTint.g, mainTint.b, mainTint.a, light, overlay);
    }

    /**
     * Soft-opacity queue key — same axis as soft BlockForm so forms interleave correctly.
     */
    private double computeStructureFormSortKey(Matrix4f drawMatrix, FormRenderingContext context)
    {
        return this.computeStructureCompatibleFormSortKey(drawMatrix, context);
    }

    /**
     * Matches {@code BlockFormRenderer.computeBlockFormSortKey}: camera-baked view space,
     * film look-axis −Z, otherwise lengthSq.
     */
    private double computeStructureCompatibleFormSortKey(Matrix4f drawMatrix, FormRenderingContext context)
    {
        Vector4f origin = new Vector4f(0F, 0F, 0F, 1F);
        Matrix4f viewSpace = ModelVAORenderer.capturePaintOverlayRootMatrix(new Matrix4f(drawMatrix));

        viewSpace.transform(origin);

        boolean filmLookAxis = context != null
            && context.type == FormRenderType.ENTITY
            && context.camera != null
            && !context.modelRenderer;

        if (filmLookAxis)
        {
            return -origin.z;
        }

        return origin.x * origin.x + origin.y * origin.y + origin.z * origin.z;
    }

    private void checkLightState()
    {
        StructureLightSettings sl = this.form.structureLight.get();
        boolean currentEmitLight = (sl != null) ? sl.enabled : this.form.emitLight.get();
        int currentLightIntensity = (sl != null) ? sl.intensity : this.form.lightIntensity.get();

        if (currentEmitLight != this.lastEmitLight || currentLightIntensity != this.lastLightIntensity)
        {
            this.vaoManager.setVaoDirty(true);
            this.lastEmitLight = currentEmitLight;
            this.lastLightIntensity = currentLightIntensity;
        }
    }

    private IModelVAO getVao()
    {
        IModelVAO vao = this.vaoManager.getStructureVao(this.data.getLastFile());

        if (vao == null || this.vaoManager.isVaoDirty())
        {
            this.vaoManager.buildStructureVAO(this.data.getLastFile(), () ->
            {
                Function<VertexConsumer, VertexConsumer> captureRecolor = BBSRendering.getColorConsumer(this.resolveStructureBlendColor());
                MatrixStack captureStack = new MatrixStack();
                FormRenderingContext captureContext = new FormRenderingContext()
                    .set(FormRenderType.PREVIEW, null, captureStack, LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, 0F);

                this.renderStructureCulledWorld(captureContext, captureStack, FormUtilsClient.getProvider(), LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, false, captureRecolor, false, false);
            });

            vao = this.vaoManager.getStructureVao(this.data.getLastFile());
        }

        return vao;
    }

    private IModelVAO getPickingVao()
    {
        IModelVAO pickingVao = this.vaoManager.getStructureVaoPicking(this.data.getLastFile());

        if (pickingVao == null || this.vaoManager.isVaoPickingDirty())
        {
            this.vaoManager.buildStructureVAOPicking(
                this.data.getLastFile(),
                this.data,
                () ->
                {
                    Function<VertexConsumer, VertexConsumer> captureRecolor = BBSRendering.getColorConsumer(this.resolveStructureBlendColor());
                    MatrixStack captureStack = new MatrixStack();
                    FormRenderingContext captureContext = new FormRenderingContext()
                        .set(FormRenderType.PREVIEW, null, captureStack, LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, 0F);

                    this.renderStructureCulledWorld(captureContext, captureStack, FormUtilsClient.getProvider(), LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, false, captureRecolor, false, false);
                },
                () ->
                {
                    MatrixStack captureStack = new MatrixStack();
                    FormRenderingContext captureContext = new FormRenderingContext()
                        .set(FormRenderType.PREVIEW, null, captureStack, LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, 0F);

                    this.renderBlockEntitiesOnly(captureContext, captureStack, FormUtilsClient.getProvider(), LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, false);
                },
                collector ->
                {
                    MatrixStack captureStack = new MatrixStack();
                    FormRenderingContext captureContext = new FormRenderingContext()
                        .set(FormRenderType.PREVIEW, null, captureStack, LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, 0F);

                    this.appendBlockEntityPickCubes(collector, captureContext);
                }
            );

            pickingVao = this.vaoManager.getStructureVaoPicking(this.data.getLastFile());
        }

        return pickingVao;
    }

    private static class RenderInfo
    {
        public float pivotX;
        public float pivotY;
        public float pivotZ;
        public VirtualBlockRenderView view;
        public BlockPos anchor;
    }

    private RenderInfo calculateRenderInfo(FormRenderingContext context, boolean forceMaxSkyLight)
    {
        RenderInfo info = new RenderInfo();
        float cx;
        float cy;
        float cz;
        float parityXAuto = 0F;
        float parityZAuto = 0F;

        if (this.data.getBoundsMin() != null && this.data.getBoundsMax() != null)
        {
            cx = (this.data.getBoundsMin().getX() + this.data.getBoundsMax().getX()) / 2F;
            cz = (this.data.getBoundsMin().getZ() + this.data.getBoundsMax().getZ()) / 2F;
            cy = this.data.getBoundsMin().getY();

            int widthX = this.data.getBoundsMax().getX() - this.data.getBoundsMin().getX() + 1;
            int widthZ = this.data.getBoundsMax().getZ() - this.data.getBoundsMin().getZ() + 1;

            parityXAuto = (widthX % 2 == 1) ? -0.5F : 0F;
            parityZAuto = (widthZ % 2 == 1) ? -0.5F : 0F;
        }
        else
        {
            cx = this.data.getSize().getX() / 2F;
            cy = 0F;
            cz = this.data.getSize().getZ() / 2F;
        }

        info.pivotX = cx - parityXAuto;
        info.pivotY = cy;
        info.pivotZ = cz - parityZAuto;

        if (this.data.getEntriesCache() == null || this.data.getEntriesCache().length != this.data.getBlocks().size())
        {
            VirtualBlockRenderView.Entry[] cache = new VirtualBlockRenderView.Entry[this.data.getBlocks().size()];

            for (int i = 0; i < this.data.getBlocks().size(); i++)
            {
                BlockEntry be = this.data.getBlocks().get(i);
                cache[i] = new VirtualBlockRenderView.Entry(be.state, be.pos);
            }

            this.data.setEntriesCache(cache);
        }

        StructureLightSettings slRuntime = this.form.structureLight.get();
        boolean lightsEnabled;
        int lightIntensity;

        if (slRuntime != null)
        {
            lightsEnabled = slRuntime.enabled;
            lightIntensity = slRuntime.intensity;
        }
        else
        {
            lightsEnabled = this.form.emitLight.get();
            lightIntensity = this.form.lightIntensity.get();
        }

        if (this.data.getCachedView() == null)
        {
            this.data.setCachedView(new StructureVirtualBlockRenderView(Arrays.asList(this.data.getEntriesCache())));
        }

        info.view = this.data.getCachedView()
            .setBiomeOverride(this.form.biomeId.get())
            .setLightsEnabled(lightsEnabled)
            .setLightIntensity(lightIntensity);

        if (lightsEnabled)
        {
            this.data.getCachedView().setVirtualMode(true, lightIntensity).setIgnoreWorldBlockLight(false);
        }
        else
        {
            this.data.getCachedView().setVirtualMode(false, 0).setIgnoreWorldBlockLight(true);
        }

        boolean isItemContext = (context.type == FormRenderType.ITEM
            || context.type == FormRenderType.ITEM_FP
            || context.type == FormRenderType.ITEM_TP
            || context.type == FormRenderType.ITEM_INVENTORY);

        if (isItemContext || context.entity == null)
        {
            MinecraftClient mc = MinecraftClient.getInstance();
            info.anchor = (mc.player != null) ? mc.player.getBlockPos() : BlockPos.ORIGIN;
        }
        else
        {
            info.anchor = new BlockPos(
                (int) Math.floor(context.entity.getX()),
                (int) Math.floor(context.entity.getY()),
                (int) Math.floor(context.entity.getZ())
            );
        }

        int baseDx = (int) Math.floor(-info.pivotX);
        int baseDy = (int) Math.floor(-info.pivotY);
        int baseDz = (int) Math.floor(-info.pivotZ);

        info.view.setWorldAnchor(info.anchor, baseDx, baseDy, baseDz)
            .setForceMaxSkyLight(!this.vaoManager.isCapturingVAO() && (context.ui
                || context.type == FormRenderType.PREVIEW
                || context.type == FormRenderType.ITEM_INVENTORY || forceMaxSkyLight));

        return info;
    }

    private RenderLayer resolveStructureBlockLayer(BlockState state, boolean useEntityLayers)
    {
        if (state.getBlock() instanceof LeavesBlock)
        {
            return this.resolveStructureLeavesLayer(state, useEntityLayers);
        }

        return RenderLayers.getEntityBlockLayer(state, false);
    }

    private RenderLayer resolveStructureLeavesLayer(BlockState state, boolean useEntityLayers)
    {
        StructureData.syncFancyGraphicsFromOptions();
        RenderLayer base = RenderLayers.getBlockLayer(state);

        if (base == RenderLayer.getSolid())
        {
            return TexturedRenderLayers.getEntitySolid();
        }

        return RenderLayers.getEntityBlockLayer(state, false);
    }

    private void renderStructureLeaves(BlockState state, BlockPos pos, BlockRenderView view, MatrixStack stack, VertexConsumerProvider consumers, Function<VertexConsumer, VertexConsumer> recolor, boolean shadowPass)
    {
        boolean softOpacity = this.wantsSoftStructureBlockLayers(shadowPass);
        RenderLayer layer = softOpacity
            ? TexturedRenderLayers.getEntityTranslucentCull()
            : this.resolveStructureLeavesLayer(state, false);
        VertexConsumer vc = consumers.getBuffer(layer);

        if (recolor != null)
        {
            vc = recolor.apply(vc);
        }

        MinecraftClient.getInstance().getBlockRenderManager().renderBlock(state, pos, view, stack, vc, true, Random.create());
    }

    /**
     * Soft form opacity (or an active soft post-deferred redraw) needs entity translucent
     * layers for cutout/biome/translucent special blocks — not terrain cutout/translucent.
     * <p>
     * Iris / film shadow keeps cutout/entity-block layers so leaf holes stay texture-cutout while
     * form opacity is Bayer-dithered via {@code bbs_is_shadow_form} (same as solid VAO).
     * Use the combined shadow flag (context + Iris), not Iris alone — 1.20.4 film shadows
     * may not always report {@link BBSRendering#isIrisShadowPass()} when the form draws.
     */
    private boolean wantsSoftStructureBlockLayers(boolean shadowPass)
    {
        if (shadowPass || BBSRendering.isIrisShadowPass())
        {
            return false;
        }

        return this.form.getFormOpacity() < ShaderOpacityPatch.LIVE_DEPTH_WRITE_ALPHA
            || ShaderOpacityPatch.isPostDeferredPhase();
    }

    private void renderStructureCulledWorld(FormRenderingContext context, MatrixStack stack, VertexConsumerProvider consumers, int light, int overlay, boolean useEntityLayers, Function<VertexConsumer, VertexConsumer> recolor, boolean skipBlockEntities, boolean skipSpecialBlocks)
    {
        RenderInfo info = this.calculateRenderInfo(context, false);
        float globalAlpha;
        boolean shadowPass = BBSRendering.isIrisShadowPass()
            || (context != null && context.isShadowPass);

        StructureData.syncFancyGraphicsFromOptions();

        for (BlockEntry entry : this.data.getBlocks())
        {
            RenderLayer layer;
            VertexConsumer vc;
            Block block;

            stack.push();
            stack.translate(entry.pos.getX() - info.pivotX, entry.pos.getY() - info.pivotY, entry.pos.getZ() - info.pivotZ);

            if (this.vaoManager.isCapturingVAO() && !this.vaoManager.isCapturingIncludeSpecialBlocks()
                && (StructureData.isAnimatedTexture(entry.state) || StructureData.isBiomeTinted(entry.state) || StructureData.isTranslucentBlock(entry.state)))
            {
                stack.pop();
                continue;
            }

            if (skipSpecialBlocks && (StructureData.isAnimatedTexture(entry.state) || StructureData.isBiomeTinted(entry.state) || StructureData.isTranslucentBlock(entry.state)))
            {
                stack.pop();
                continue;
            }

            layer = this.resolveStructureBlockLayer(entry.state, useEntityLayers);
            globalAlpha = this.form.getFormOpacity();

            if (globalAlpha < ShaderOpacityPatch.LIVE_DEPTH_WRITE_ALPHA || ShaderOpacityPatch.isPostDeferredPhase())
            {
                if (!shadowPass)
                {
                    /* Entity translucent — terrain translucent/cutout fails in soft post-deferred. */
                    layer = TexturedRenderLayers.getEntityTranslucentCull();
                }
            }

            vc = consumers.getBuffer(layer);

            if (recolor != null)
            {
                vc = recolor.apply(vc);
            }

            if (this.form.renderFluid.get() && !entry.state.getFluidState().isEmpty())
            {
                boolean shaders = BBSRendering.isIrisShadersEnabled() && BBSRendering.isRenderingWorld();
                RenderLayer fluidLayer = shaders
                    ? RenderLayers.getEntityBlockLayer(entry.state, false)
                    : RenderLayers.getFluidLayer(entry.state.getFluidState());
                VertexConsumer fluidVc = consumers.getBuffer(fluidLayer);

                if (recolor != null)
                {
                    fluidVc = recolor.apply(fluidVc);
                }

                fluidVc = new TransformingVertexConsumer(fluidVc, stack.peek(), entry.pos, shaders);
                MinecraftClient.getInstance().getBlockRenderManager().renderFluid(entry.pos, info.view, fluidVc, entry.state, entry.state.getFluidState());
            }

            if (entry.state.getRenderType() != BlockRenderType.INVISIBLE)
            {
                if (entry.state.getBlock() instanceof LeavesBlock)
                {
                    this.renderStructureLeaves(entry.state, entry.pos, info.view, stack, consumers, recolor, shadowPass);
                }
                else
                {
                    MinecraftClient.getInstance().getBlockRenderManager().renderBlock(entry.state, entry.pos, info.view, stack, vc, true, Random.create());
                }
            }

            block = entry.state.getBlock();

            if (!this.vaoManager.isCapturingVAO() && !skipBlockEntities && block instanceof BlockEntityProvider)
            {
                this.renderSingleBlockEntity(entry, info, context, stack, overlay);
            }

            stack.pop();
        }

        RecolorVertexConsumer.newColor = null;
    }

    private void renderSingleBlockEntity(BlockEntry entry, RenderInfo info, FormRenderingContext context, MatrixStack stack, int overlay)
    {
        Block block = entry.state.getBlock();
        int dx = (int) Math.floor(entry.pos.getX() - info.pivotX);
        int dy = (int) Math.floor(entry.pos.getY() - info.pivotY);
        int dz = (int) Math.floor(entry.pos.getZ() - info.pivotZ);
        BlockPos worldPos = info.anchor.add(dx, dy, dz);
        BlockEntity be = ((BlockEntityProvider) block).createBlockEntity(worldPos, entry.state);

        if (be != null)
        {
            if (entry.nbt != null)
            {
                be.readNbt(entry.nbt);
            }

            if (MinecraftClient.getInstance().world != null)
            {
                be.setWorld(MinecraftClient.getInstance().world);
            }

            BlockEntityRenderDispatcher beDispatcher = MinecraftClient.getInstance().getBlockEntityRenderDispatcher();
            BlockEntityRenderer<?> renderer = beDispatcher.get(be);

            int skyLight = info.view.getLightLevel(LightType.SKY, entry.pos);
            int blockLight = info.view.getLightLevel(LightType.BLOCK, entry.pos);
            int beLight = LightmapTextureManager.pack(blockLight, skyLight);

            if (renderer != null)
            {
                @SuppressWarnings({"rawtypes", "unchecked"})
                BlockEntityRenderer raw = (BlockEntityRenderer) renderer;
                CustomVertexConsumerProvider beProvider = FormUtilsClient.getProvider();

                Color beTint = this.resolveStructureBlockEntityColor();
                boolean beShadowPass = context.isShadowPass || BBSRendering.isIrisShadowPass();

                this.applyBlockEntityOnlyShaderShadow(beTint, beShadowPass);
                beProvider.setSubstitute(BBSRendering.getColorConsumer(beTint));

                try
                {
                    RenderSystem.setShaderColor(beTint.r, beTint.g, beTint.b, beTint.a);
                    raw.render(be, 0F, stack, beProvider, beLight, overlay);
                }
                finally
                {
                    RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
                    beProvider.draw();
                    beProvider.setSubstitute(null);
                    CustomVertexConsumerProvider.clearRunnables();
                }
            }
        }
    }

    private void renderLayerGroup(List<BlockEntry> group, FormRenderingContext context, MatrixStack stack, int light, int overlay, Function<VertexConsumer, VertexConsumer> recolor, Color shaderTint, boolean forceDrawLeaves)
    {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, PlayerScreenHandler.BLOCK_ATLAS_TEXTURE);
        StructureData.syncFancyGraphicsFromOptions();

        RenderInfo info = this.calculateRenderInfo(context, false);
        CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
        boolean shadersEnabled = BBSRendering.isIrisShadersEnabled() && BBSRendering.isRenderingWorld();
        boolean shadowPass = BBSRendering.isIrisShadowPass()
            || (context != null && context.isShadowPass);

        /* Reset before hijack — leftover ColorModulator.a squares leaf/cutout opacity. */
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);

        if (shadowPass)
        {
            /* Always force ColorModulator.a = 1 in shadow (even without shaderTint / when
             * isRenderingWorld is false). Leftover modulator alpha × recolor opacity squares
             * leaf Bayer dither vs solid VAO. */
            final Color tint = shaderTint;

            CustomVertexConsumerProvider.hijackVertexFormat((l) ->
            {
                if (tint != null)
                {
                    RenderSystem.setShaderColor(tint.r, tint.g, tint.b, 1F);
                }
                else
                {
                    RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
                }

                ShaderOpacityPatch.uploadShadowFormUniform();
            });
        }
        else if (shadersEnabled && shaderTint != null)
        {
            CustomVertexConsumerProvider.hijackVertexFormat((l) ->
            {
                RenderSystem.setShaderColor(shaderTint.r, shaderTint.g, shaderTint.b, shaderTint.a);
            });
        }
        else
        {
            CustomVertexConsumerProvider.clearRunnables();
        }

        try
        {
            for (BlockEntry entry : group)
            {
                stack.push();
                stack.translate(entry.pos.getX() - info.pivotX, entry.pos.getY() - info.pivotY, entry.pos.getZ() - info.pivotZ);

                if (entry.state.getBlock() instanceof LeavesBlock)
                {
                    this.renderStructureLeaves(entry.state, entry.pos, info.view, stack, consumers, recolor, shadowPass);
                    stack.pop();
                    continue;
                }

                RenderLayer layer = this.resolveStructureBlockLayer(entry.state, shadersEnabled);

                if (this.wantsSoftStructureBlockLayers(shadowPass))
                {
                    /* Always entity translucent under soft opacity — terrain translucent vanishes
                     * when drawn from the soft post-deferred flush without shaders. */
                    layer = TexturedRenderLayers.getEntityTranslucentCull();
                }

                VertexConsumer vc = consumers.getBuffer(layer);

                if (recolor != null)
                {
                    vc = recolor.apply(vc);
                }

                if (this.form.renderFluid.get() && !entry.state.getFluidState().isEmpty())
                {
                    RenderLayer fluidLayer = shadersEnabled
                        ? RenderLayers.getEntityBlockLayer(entry.state, false)
                        : RenderLayers.getFluidLayer(entry.state.getFluidState());
                    VertexConsumer fluidVc = consumers.getBuffer(fluidLayer);

                    if (recolor != null)
                    {
                        fluidVc = recolor.apply(fluidVc);
                    }

                    fluidVc = new TransformingVertexConsumer(fluidVc, stack.peek(), entry.pos, shadersEnabled);
                    MinecraftClient.getInstance().getBlockRenderManager().renderFluid(entry.pos, info.view, fluidVc, entry.state, entry.state.getFluidState());
                }

                if (entry.state.getRenderType() != BlockRenderType.INVISIBLE)
                {
                    MinecraftClient.getInstance().getBlockRenderManager().renderBlock(entry.state, entry.pos, info.view, stack, vc, true, Random.create());
                }

                stack.pop();
            }

            consumers.draw();
        }
        finally
        {
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
            RenderSystem.disableBlend();
            CustomVertexConsumerProvider.clearRunnables();
            RecolorVertexConsumer.newColor = null;
        }
    }

    private void renderPaintLayer(StructurePaintLayer layer, FormRenderingContext context, MatrixStack stack, int overlay, Function<VertexConsumer, VertexConsumer> recolor)
    {
        if (layer == StructurePaintLayer.BIOME)
        {
            this.renderLayerGroup(this.data.getBiomeTintedBlocks(), context, stack, LightmapTextureManager.MAX_LIGHT_COORDINATE, overlay, recolor, null, true);
        }
        else if (layer == StructurePaintLayer.ANIMATED)
        {
            this.renderLayerGroup(this.data.getAnimatedBlocks(), context, stack, LightmapTextureManager.MAX_LIGHT_COORDINATE, overlay, recolor, null, false);
        }
        else
        {
            this.renderLayerGroup(this.data.getTranslucentBlocks(), context, stack, LightmapTextureManager.MAX_LIGHT_COORDINATE, overlay, recolor, null, false);
        }
    }

    private Color resolveStructureBlendColor()
    {
        Color storedFormColor = this.form.color.get();
        Color rawFormColor = storedFormColor.copyBakingColorGrade();
        Color tint = Color.white();

        if (FormColorEffects.shouldBakeFormColor(storedFormColor))
        {
            tint.mul(rawFormColor);
        }

        this.form.applyFormOpacity(tint);
        return tint;
    }

    private Color resolveStructureBlockEntityColor()
    {
        Color tint = FormColorEffects.resolveBlockEntityTint(this.form.color.get(), this.form.paintSettings.get(), this.form.paintColor.get());
        this.form.applyFormOpacity(tint);
        return tint;
    }

    private void applyBlockEntityOnlyShaderShadow(Color color, boolean shadowPass)
    {
        if (color == null || !shadowPass || !this.data.isEntirelyBlockEntities())
        {
            return;
        }

        color.a = PaintSettings.SHADER_SHADOW_BLOCK_ENTITY;
    }

    private boolean needsDeferredBlockEntityTint(boolean positivePaint, boolean applyColorTint, Color storedFormColor)
    {
        Color beTint = this.resolveStructureBlockEntityColor();
        return beTint.r < 0.999F || beTint.g < 0.999F || beTint.b < 0.999F;
    }

    private void submitDeferredStructureBlockEntityTint(FormRenderingContext context, int overlay)
    {
        Matrix4f exactMvm = new Matrix4f(RenderSystem.getModelViewMatrix());
        Matrix4f exactStack = new Matrix4f(context.stack.peek().getPositionMatrix());
        Matrix3f normalMatrix = new Matrix3f(context.stack.peek().getNormalMatrix());

        ModelVAORenderer.submitVanillaPostComposite(() ->
        {
            MatrixStack overlayStack = new MatrixStack();
            CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();

            overlayStack.peek().getPositionMatrix().set(exactStack);
            overlayStack.peek().getNormalMatrix().set(normalMatrix);

            RenderSystem.getModelViewStack().push();
            RenderSystem.getModelViewStack().peek().getPositionMatrix().set(exactMvm);
            RenderSystem.applyModelViewMatrix();

            try
            {
                this.renderBlockEntitiesOnly(context, overlayStack, consumers, LightmapTextureManager.MAX_LIGHT_COORDINATE, overlay, true);
                consumers.draw();
            }
            catch (Throwable ignored)
            {
            }
            finally
            {
                RenderSystem.getModelViewStack().pop();
                RenderSystem.applyModelViewMatrix();
                consumers.setSubstitute(null);
                RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
            }
        });
    }

    private Function<VertexConsumer, VertexConsumer> getMainConsumer(Color color, Color resolvedPaint)
    {
        if (resolvedPaint != null && resolvedPaint.a < 0F)
        {
            return BBSRendering.getBlockPaintConsumer(color, resolvedPaint);
        }

        return BBSRendering.getColorConsumer(color);
    }

    private void renderBlockEntitiesPass(FormRenderingContext context, MatrixStack stack, int light, int overlay, boolean applyColorTint)
    {
        try
        {
            VertexConsumerProvider beConsumers = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
            this.renderBlockEntitiesOnly(context, stack, beConsumers, light, overlay, applyColorTint);

            if (beConsumers instanceof VertexConsumerProvider.Immediate immediate)
            {
                immediate.draw();
            }
        }
        catch (Throwable ignored)
        {
        }
    }

    private void renderBlockEntitiesOnly(FormRenderingContext context, MatrixStack stack, VertexConsumerProvider consumers, int light, int overlay, boolean applyColorTint)
    {
        RenderInfo info = this.calculateRenderInfo(context, false);
        BlockEntityRenderDispatcher beDispatcher = MinecraftClient.getInstance().getBlockEntityRenderDispatcher();

        for (BlockEntry entry : this.data.getBlockEntitiesList())
        {
            Block block = entry.state.getBlock();

            stack.push();
            stack.translate(entry.pos.getX() - info.pivotX, entry.pos.getY() - info.pivotY, entry.pos.getZ() - info.pivotZ);

            int dx = (int) Math.floor(entry.pos.getX() - info.pivotX);
            int dy = (int) Math.floor(entry.pos.getY() - info.pivotY);
            int dz = (int) Math.floor(entry.pos.getZ() - info.pivotZ);
            BlockPos worldPos = info.anchor.add(dx, dy, dz);

            BlockEntity be = ((BlockEntityProvider) block).createBlockEntity(worldPos, entry.state);

            if (be != null)
            {
                if (entry.nbt != null)
                {
                    be.readNbt(entry.nbt);
                }

                if (MinecraftClient.getInstance().world != null)
                {
                    be.setWorld(MinecraftClient.getInstance().world);
                }

                BlockEntityRenderer<?> renderer = beDispatcher.get(be);
                int skyLight = info.view.getLightLevel(LightType.SKY, entry.pos);
                int blockLight = info.view.getLightLevel(LightType.BLOCK, entry.pos);
                int beLight = LightmapTextureManager.pack(blockLight, skyLight);

                if (renderer != null)
                {
                    @SuppressWarnings({"rawtypes", "unchecked"})
                    BlockEntityRenderer raw = (BlockEntityRenderer) renderer;
                    CustomVertexConsumerProvider beProvider = FormUtilsClient.getProvider();

                    Color beTint = null;
                    boolean shadowPass = context.isShadowPass || BBSRendering.isIrisShadowPass();

                    if (applyColorTint)
                    {
                        beTint = this.resolveStructureBlockEntityColor();
                        this.applyBlockEntityOnlyShaderShadow(beTint, shadowPass);
                        beProvider.setSubstitute(BBSRendering.getColorConsumer(beTint));
                    }
                    else if (shadowPass && this.data.isEntirelyBlockEntities())
                    {
                        beTint = new Color(1F, 1F, 1F, PaintSettings.SHADER_SHADOW_BLOCK_ENTITY);
                        beProvider.setSubstitute(BBSRendering.getColorConsumer(beTint));
                    }

                    try
                    {
                        if (beTint != null)
                        {
                            RenderSystem.setShaderColor(beTint.r, beTint.g, beTint.b, beTint.a);
                        }

                        raw.render(be, 0F, stack, beProvider, beLight, overlay);
                    }
                    finally
                    {
                        if (beTint != null)
                        {
                            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
                        }

                        beProvider.draw();

                        if (applyColorTint)
                        {
                            beProvider.setSubstitute(null);
                            CustomVertexConsumerProvider.clearRunnables();
                        }
                    }
                }
            }

            stack.pop();
        }
    }

    private void appendBlockEntityPickCubes(StructureVAOCollector collector, FormRenderingContext context)
    {
        RenderInfo info = this.calculateRenderInfo(context, false);

        for (BlockEntry entry : this.data.getBlockEntitiesList())
        {
            float x0 = entry.pos.getX() - info.pivotX;
            float y0 = entry.pos.getY() - info.pivotY;
            float z0 = entry.pos.getZ() - info.pivotZ;

            this.emitPickCube(collector, x0, y0, z0, x0 + 1F, y0 + 1F, z0 + 1F);
        }
    }

    private void emitPickCube(StructureVAOCollector collector, float x0, float y0, float z0, float x1, float y1, float z1)
    {
        this.emitPickQuad(collector, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0F, 0F, -1F);
        this.emitPickQuad(collector, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, 0F, 0F, 1F);
        this.emitPickQuad(collector, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0, 0F, -1F, 0F);
        this.emitPickQuad(collector, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0F, 1F, 0F);
        this.emitPickQuad(collector, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, -1F, 0F, 0F);
        this.emitPickQuad(collector, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 1F, 0F, 0F);
    }

    private void emitPickQuad(StructureVAOCollector collector, float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float nx, float ny, float nz)
    {
        collector.vertex(x0, y0, z0).color(255, 255, 255, 255).texture(0F, 0F).overlay(0, 0).light(0, 0).normal(nx, ny, nz);
        collector.vertex(x1, y1, z1).color(255, 255, 255, 255).texture(1F, 0F).overlay(0, 0).light(0, 0).normal(nx, ny, nz);
        collector.vertex(x2, y2, z2).color(255, 255, 255, 255).texture(1F, 1F).overlay(0, 0).light(0, 0).normal(nx, ny, nz);
        collector.vertex(x3, y3, z3).color(255, 255, 255, 255).texture(0F, 1F).overlay(0, 0).light(0, 0).normal(nx, ny, nz);
    }

    private void ensureLoaded()
    {
        String file = this.form.structureFile.get();

        if (this.data.ensureLoaded(file))
        {
            this.vaoManager.clearCachedVao(this.data.getLastFile());
            this.vaoManager.setVaoDirty(true);
            this.vaoManager.setVaoPickingDirty(true);
        }
    }

    private static class TransformingVertexConsumer implements VertexConsumer
    {
        private final VertexConsumer parent;
        private final Matrix4f positionMatrix;
        private final Matrix3f normalMatrix;
        private final BlockPos offset;
        private final boolean injectOverlay;

        public TransformingVertexConsumer(VertexConsumer parent, MatrixStack.Entry entry, BlockPos offset, boolean injectOverlay)
        {
            this.parent = parent;
            this.positionMatrix = new Matrix4f(entry.getPositionMatrix());
            this.normalMatrix = new Matrix3f(entry.getNormalMatrix());
            this.offset = offset;
            this.injectOverlay = injectOverlay;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z)
        {
            double nx = x - this.offset.getX();
            double ny = y - this.offset.getY();
            double nz = z - this.offset.getZ();

            double tx = this.positionMatrix.m00() * nx + this.positionMatrix.m10() * ny + this.positionMatrix.m20() * nz + this.positionMatrix.m30();
            double ty = this.positionMatrix.m01() * nx + this.positionMatrix.m11() * ny + this.positionMatrix.m21() * nz + this.positionMatrix.m31();
            double tz = this.positionMatrix.m02() * nx + this.positionMatrix.m12() * ny + this.positionMatrix.m22() * nz + this.positionMatrix.m32();

            this.parent.vertex(tx, ty, tz);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha)
        {
            this.parent.color(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v)
        {
            this.parent.texture(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v)
        {
            this.parent.overlay(u, v);
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v)
        {
            if (this.injectOverlay)
            {
                this.parent.overlay(0, 10);
            }
            this.parent.light(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z)
        {
            float tx = this.normalMatrix.m00() * x + this.normalMatrix.m10() * y + this.normalMatrix.m20() * z;
            float ty = this.normalMatrix.m01() * x + this.normalMatrix.m11() * y + this.normalMatrix.m21() * z;
            float tz = this.normalMatrix.m02() * x + this.normalMatrix.m12() * y + this.normalMatrix.m22() * z;

            this.parent.normal(tx, ty, tz);
            return this;
        }

        @Override
        public void next()
        {
            this.parent.next();
        }

        @Override
        public void fixedColor(int red, int green, int blue, int alpha)
        {
            this.parent.fixedColor(red, green, blue, alpha);
        }

        @Override
        public void unfixColor()
        {
            this.parent.unfixColor();
        }
    }
}
