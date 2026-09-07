package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.cubic.render.vao.ModelVAORenderer;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.BlockForm;
import mchorse.bbs_mod.forms.forms.utils.EffectTransform;
import mchorse.bbs_mod.forms.forms.utils.GlowSettings;
import mchorse.bbs_mod.forms.forms.utils.PaintSettings;
import mchorse.bbs_mod.forms.forms.utils.StructureLightSettings;
import mchorse.bbs_mod.forms.renderers.utils.BlockEffectOverlayUniforms;
import mchorse.bbs_mod.forms.renderers.utils.FormColorEffects;
import mchorse.bbs_mod.forms.renderers.utils.FormLightingRender;
import mchorse.bbs_mod.forms.renderers.utils.GlowEmissionVertexConsumer;
import mchorse.bbs_mod.forms.renderers.utils.StructureData;
import mchorse.bbs_mod.forms.renderers.utils.VirtualBlockRenderView;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.iris.FormFluidShaderPatch;
import mchorse.bbs_mod.utils.iris.ShaderOpacityPatch;
import mchorse.bbs_mod.utils.joml.Vectors;
import mchorse.bbs_mod.utils.pose.Transform;

import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.OverlayVertexConsumer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.ModelLoader;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.biome.ColorResolver;
import net.minecraft.world.chunk.light.LightingProvider;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.mojang.blaze3d.systems.RenderSystem;

import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.function.Function;

public class BlockFormRenderer extends FormRenderer<BlockForm>
{
    public static final Color color = new Color();

    private final Vector3f blockVisualMaskSize = new Vector3f(1F, 1F, 1F);

    /* Iris gbuffer bloom for entity-visual BER (signs, chests): vertex emission, not ColorModulator. */
    private Color blockMainPassGlowEmission;
    private VirtualBlockRenderView blockView;

    public BlockFormRenderer(BlockForm form)
    {
        super(form);
    }

    @Override
    public void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        context.batcher.getContext().draw();

        CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
        MatrixStack matrices = context.batcher.getContext().getMatrices();

        Matrix4f uiMatrix = ModelFormRenderer.getUIMatrix(context, x1, y1, x2, y2);

        matrices.push();
        MatrixStackUtils.multiply(matrices, uiMatrix);
        matrices.scale(this.form.uiScale.get(), this.form.uiScale.get(), this.form.uiScale.get());

        Color storedFormColor = this.form.color.get();
        Color rawFormColor = storedFormColor.copyBakingColorGrade();
        Color formColor = rawFormColor.copy();
        boolean colorGradeWanted = storedFormColor.hasColorAdjustments();
        Color set = Color.white();

        if (this.shouldBakeBlockFormColor(storedFormColor))
        {
            set.mul(rawFormColor);
        }

        this.form.applyFormOpacity(set);
        this.form.applyFormOpacity(formColor);

        GlowSettings glowSettings = this.form.glowSettings.get();
        Color legacyGlow = this.form.glowingColor.get();
        float glowIntensity = glowSettings.resolveIntensity(legacyGlow);

        if (glowIntensity < 0F)
        {
            FormColorEffects.blendFormGlowBrighten(set, glowSettings, legacyGlow, this.form.paintSettings.get(), this.form.paintColor.get(), storedFormColor);
        }

        Color resolvedPaint = FormColorEffects.resolvePaintColor(this.form.paintSettings.get(), this.form.paintColor.get());
        boolean positivePaint = FormColorEffects.hasPositivePaint(this.form.paintSettings.get(), this.form.paintColor.get());
        boolean blockEntityVisual = this.isBlockEntityVisual();

        CustomVertexConsumerProvider.clearRunnables();

        Vector3f light0 = new Vector3f(0.85F, 0.85F, -1F).normalize();
        Vector3f light1 = new Vector3f(-0.85F, 0.85F, 1F).normalize();
        RenderSystem.setupLevelDiffuseLighting(light0, light1);

        consumers.setSubstitute(this.getBlockMainConsumer(set, resolvedPaint));
        consumers.setUI(true);
        this.renderRepeatedBlocks(null, matrices, consumers, LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, false, true, false, false, false);

        consumers.draw();
        consumers.setSubstitute(null);
        CustomVertexConsumerProvider.clearRunnables();

        if (this.hasFluid())
        {
            this.renderFluidPass(null, matrices, true, LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, set, resolvedPaint);
        }

        boolean runPaintOverlay = this.shouldRunBlockPaintOverlay(blockEntityVisual, this.form.paintSettings.get(), positivePaint);
        boolean runColorTintOverlay = this.shouldRunBlockColorTintOverlay(blockEntityVisual, storedFormColor);
        EffectTransform glowTransform = FormColorEffects.resolveGlowEffectTransform(glowSettings, legacyGlow);
        boolean hasGlowTransform = glowTransform != null && glowTransform.isActive();
        boolean runGlowOverlay = this.shouldRunBlockGlowOverlay(glowIntensity > 0F && !glowSettings.resolvePaintOnly());

        if (runColorTintOverlay)
        {
            Color overlayTint = colorGradeWanted ? storedFormColor.copyDeferringColorGrade() : formColor;

            this.form.applyFormOpacity(overlayTint);
            this.renderBlockColorTintOverlay(null, matrices, overlayTint, set.a, OverlayTexture.DEFAULT_UV, true, storedFormColor);
        }

        if (runPaintOverlay)
        {
            this.submitDeferredBlockPaintOverlay(null, matrices, resolvedPaint, set.a, OverlayTexture.DEFAULT_UV, this.form.paintSettings.get().transform, glowSettings, legacyGlow, glowIntensity, true);
        }

        if (runGlowOverlay)
        {
            this.renderGlowOverlayMasked(null, matrices, consumers, glowSettings, legacyGlow, glowIntensity, set.a, OverlayTexture.DEFAULT_UV, true, hasGlowTransform ? glowTransform : null);
        }

        consumers.setUI(false);
        consumers.setSubstitute(null);
        CustomVertexConsumerProvider.clearRunnables();

        DiffuseLighting.disableGuiDepthLighting();

        matrices.pop();
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
        int light = context.light;

        StructureLightSettings sl = this.form.structureLight.get();
        boolean lightsEnabled = (sl != null) ? sl.enabled : this.form.emitLight.get();
        int lightIntensity = (sl != null) ? sl.intensity : this.form.lightIntensity.get();
        BlockState currentBlockState = this.form.blockState.get();
        int luminance = (lightsEnabled && currentBlockState != null) ? Math.min(currentBlockState.getLuminance(), lightIntensity) : 0;

        if (luminance > 0 && !context.isPicking())
        {
            int blockLight = Math.max(LightmapTextureManager.getBlockLightCoordinates(light), luminance);
            int skyLight = LightmapTextureManager.getSkyLightCoordinates(light);

            light = LightmapTextureManager.pack(blockLight, skyLight);
        }

        context.stack.push();

        try
        {
            if (context.isPicking())
            {
                CustomVertexConsumerProvider.hijackVertexFormat((layer) ->
                {
                    this.setupTarget(context, BBSShaders.getPickerModelsProgram());
                    RenderSystem.setShader(BBSShaders::getPickerModelsProgram);
                    RenderSystem.setShaderTexture(0, PlayerScreenHandler.BLOCK_ATLAS_TEXTURE);
                    /* Unit pick cubes need both faces; culling clipped the volume to a flat slab. */
                    RenderSystem.disableCull();
                });

                light = 0;
                /* Form opacity / blend intensity must not discard pick pixels (picker_models a < 0.1). */
                consumers.setSubstitute(BBSRendering.getColorConsumer(new Color(1F, 1F, 1F, 1F)));
            }
            else if (context.isShadowPass || BBSRendering.isIrisShadowPass())
            {
                /* Opaque casters — enabling blend here made Complementary treat solids like leaves. */
                CustomVertexConsumerProvider.hijackVertexFormat((l) ->
                {
                    RenderSystem.disableBlend();
                    RenderSystem.depthMask(true);
                });
            }
            else
            {
                CustomVertexConsumerProvider.hijackVertexFormat((l) ->
                {
                    this.applyBlockMainPassHijackLayer(l, null);
                });
            }

            Color storedFormColor = this.form.color.get();
            Color rawFormColor = storedFormColor.copyBakingColorGrade();
            Color formColor = rawFormColor.copy();
            boolean colorGradeWanted = storedFormColor.hasColorAdjustments();

            color.set(context.color);

            boolean shadowPass = context.isShadowPass || BBSRendering.isIrisShadowPass();

            if (shadowPass)
            {
                color.a *= storedFormColor.a;
            }
            else if (this.shouldBakeBlockFormColor(storedFormColor))
            {
                color.mul(rawFormColor);
            }

            this.form.applyFormOpacity(color);
            this.form.applyFormOpacity(formColor);

            FormColorEffects.applyShadowPassColorFix(color, storedFormColor, this.form.paintSettings.get(), this.form.paintColor.get(), shadowPass);

            if (color.a <= 0.001F && !shadowPass && !context.isPicking())
            {
                return;
            }

            GlowSettings glowSettings = this.form.glowSettings.get();
            Color legacyGlow = this.form.glowingColor.get();
            float glowIntensity = glowSettings.resolveIntensity(legacyGlow);
            boolean positiveGlow = !context.isPicking() && !shadowPass && glowIntensity > 0F;
            PaintSettings paintSettings = this.form.paintSettings.get();
            Color legacyPaint = this.form.paintColor.get();
            Color resolvedPaint = FormColorEffects.resolvePaintColor(paintSettings, legacyPaint);
            boolean positivePaint = !context.isPicking() && !shadowPass && FormColorEffects.hasPositivePaint(paintSettings, legacyPaint);
            /* Chests/beds/signs use entity textures — block atlas paint/tint overlays corrupt them.
             * Bake blend/paint/grade into ColorModulator tint instead (Iris: deferred redraw).
             * Spatial transform masks still use the block-atlas overlay pass (BE redraw skipped). */
            boolean blockEntityVisual = this.isBlockEntityVisual();
            EffectTransform glowTransform = FormColorEffects.resolveGlowEffectTransform(glowSettings, legacyGlow);
            boolean hasGlowTransform = glowTransform != null && glowTransform.isActive();
            boolean runPaintOverlay = this.shouldRunBlockPaintOverlay(blockEntityVisual, paintSettings, positivePaint);
            boolean runColorTintOverlay = this.shouldRunBlockColorTintOverlay(blockEntityVisual, storedFormColor);
            boolean hasEmissiveGlow = positiveGlow && !glowSettings.resolvePaintOnly();
            boolean irisWorldPaintDeferral = BBSRendering.isIrisWorldPaintDeferral();
            final EffectTransform deferredGlowTransform = hasGlowTransform ? glowTransform.copy() : null;

            if (glowIntensity < 0F)
            {
                FormColorEffects.blendFormGlowBrighten(color, glowSettings, legacyGlow, this.form.paintSettings.get(), this.form.paintColor.get(), storedFormColor);
            }

            boolean localPreview = context.isLocalPreview();
            boolean noshadingDefer = !localPreview
                && !context.isPicking()
                && !shadowPass
                && BBSRendering.needsIrisNoshadingOpacityDeferral(color.a, this.form.noshadingOpacity.get());
            boolean softPostDeferred = !localPreview
                && !context.isPicking()
                && !shadowPass
                && ShaderOpacityPatch.shouldDelayUntilPostDeferred(color.a)
                && !noshadingDefer;
            boolean glowBakedInMainPass = irisWorldPaintDeferral && hasEmissiveGlow && !hasGlowTransform && !noshadingDefer;
            boolean runGlowOverlay = this.shouldRunBlockGlowOverlay(positiveGlow && !glowSettings.resolvePaintOnly() && !glowBakedInMainPass);
            final Color blockRecolorSource;

            if (glowBakedInMainPass)
            {
                blockRecolorSource = new Color(1F, 1F, 1F, color.a);
            }
            else
            {
                blockRecolorSource = color;
            }

            final Function<VertexConsumer, VertexConsumer> blockMainRecolor = this.getBlockMainConsumer(blockRecolorSource, resolvedPaint);
            final Color blockShaderTint;

            if (glowBakedInMainPass && BBSRendering.isIrisShadersEnabled() && BBSRendering.isRenderingWorld())
            {
                /* Match StructureForm / ItemForm: emission via ColorModulator on neutral white
                 * vertices during the Iris gbuffer pass (post-composite overlays never bloom).
                 * Entity-visual blocks (signs, chests, …) hit this through BER render layers +
                 * blockMainRecolor, not only renderBlockAsEntity quads. */
                blockShaderTint = new Color(1F, 1F, 1F, color.a);
                FormColorEffects.blendFormGlowBrighten(blockShaderTint, glowSettings, legacyGlow);
            }
            else
            {
                blockShaderTint = null;
            }

            if (blockEntityVisual && blockShaderTint != null)
            {
                this.blockMainPassGlowEmission = blockShaderTint.copy();
            }
            else
            {
                this.blockMainPassGlowEmission = null;
            }

            if (softPostDeferred || noshadingDefer)
            {
                boolean irisCamera = BBSRendering.isIrisWorldModelPass() && !noshadingDefer;
                Matrix4f positionMatrix = irisCamera
                    ? new Matrix4f(context.stack.peek().getPositionMatrix())
                    : ModelVAORenderer.capturePaintOverlayRootMatrix(new Matrix4f(context.stack.peek().getPositionMatrix()));
                Matrix3f normalMatrix = new Matrix3f(context.stack.peek().getNormalMatrix());
                Color colorSnapshot = blockRecolorSource.copy();
                Color blockShaderTintSnapshot = blockShaderTint == null ? null : blockShaderTint.copy();
                Color resolvedPaintSnapshot = resolvedPaint == null ? null : resolvedPaint.copy();
                int lightSnapshot = light;
                int overlaySnapshot = context.overlay;
                boolean depthWrite = ShaderOpacityPatch.shouldWriteDepthForOpacity(color.a);
                boolean afterFluids = ShaderOpacityPatch.shouldFlushAfterFluids(color.a);
                double formSortKey = this.computeBlockFormSortKey(context.stack.peek().getPositionMatrix(), context);
                boolean positiveGlowSnapshot = runGlowOverlay;
                float glowIntensitySnapshot = glowIntensity;
                GlowSettings glowSettingsSnapshot = glowSettings;
                Color legacyGlowSnapshot = legacyGlow;
                boolean positivePaintSnapshot = runPaintOverlay;
                PaintSettings paintSettingsSnapshot = paintSettings == null ? null : paintSettings.copy();
                boolean colorTransformWantedSnapshot = runColorTintOverlay;
                Color storedFormColorSnapshot = storedFormColor == null ? null : storedFormColor.copy();
                Color formColorSnapshot = formColor.copy();
                boolean colorGradeWantedSnapshot = colorGradeWanted;

                boolean irisWorldPaintDeferralSnapshot = irisWorldPaintDeferral;

                Runnable deferredDraw = () ->
                {
                    MatrixStack overlayStack = new MatrixStack();

                    overlayStack.peek().getPositionMatrix().set(positionMatrix);
                    overlayStack.peek().getNormalMatrix().set(normalMatrix);

                    CustomVertexConsumerProvider deferredConsumers = FormUtilsClient.getProvider();

                    RenderSystem.enableDepthTest();
                    RenderSystem.depthMask(depthWrite);
                    ShaderOpacityPatch.reassertPostDeferredDepthState(depthWrite);
                    CustomVertexConsumerProvider.hijackVertexFormat((layer) ->
                    {
                        if (FormUtilsClient.isCrumblingLayer(layer))
                        {
                            return;
                        }

                        if (blockShaderTintSnapshot != null)
                        {
                            this.applyBlockMainPassHijackLayer(layer, blockShaderTintSnapshot);
                        }
                        else
                        {
                            RenderSystem.enableBlend();
                            RenderSystem.defaultBlendFunc();
                        }
                        RenderSystem.depthMask(depthWrite);
                        ShaderOpacityPatch.reassertPostDeferredDepthState(depthWrite);
                    });

                    deferredConsumers.setSubstitute(this.getBlockMainConsumer(colorSnapshot, resolvedPaintSnapshot));

                    try
                    {
                        this.renderRepeatedBlocks(context, overlayStack, deferredConsumers, lightSnapshot, overlaySnapshot, false, false, false, false, false);
                        deferredConsumers.draw();
                    }
                    finally
                    {
                        if (blockShaderTintSnapshot != null)
                        {
                            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
                        }

                        deferredConsumers.setSubstitute(null);
                        CustomVertexConsumerProvider.clearRunnables();
                    }

                    if (colorTransformWantedSnapshot && !irisWorldPaintDeferralSnapshot)
                    {
                        Color overlayTint = colorGradeWantedSnapshot ? storedFormColorSnapshot.copyDeferringColorGrade() : formColorSnapshot;

                        this.form.applyFormOpacity(overlayTint);
                        this.renderBlockColorTintOverlay(context, overlayStack, overlayTint, colorSnapshot.a, overlaySnapshot, false, storedFormColorSnapshot);
                    }

                    if (positivePaintSnapshot && !irisWorldPaintDeferralSnapshot)
                    {
                        this.renderPaintOverlay(context, overlayStack, deferredConsumers, resolvedPaintSnapshot, colorSnapshot.a, overlaySnapshot, false, paintSettingsSnapshot.transform, glowSettingsSnapshot, legacyGlowSnapshot, glowIntensitySnapshot);
                    }

                    if (positiveGlowSnapshot && !irisWorldPaintDeferralSnapshot)
                    {
                        this.renderGlowOverlayMasked(context, overlayStack, deferredConsumers, glowSettingsSnapshot, legacyGlowSnapshot, glowIntensitySnapshot, colorSnapshot.a, overlaySnapshot, false, deferredGlowTransform);
                    }

                    /* Soft flush isolation — glow leaves additive blend / depthMask false. */
                    RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
                    RenderSystem.defaultBlendFunc();
                    CustomVertexConsumerProvider.clearRunnables();
                    RenderSystem.depthMask(depthWrite);
                    ShaderOpacityPatch.reassertPostDeferredDepthState(depthWrite);
                };

                if (noshadingDefer)
                {
                    ModelVAORenderer.submitDeferredTranslucentModel(deferredDraw, depthWrite);
                }
                else if (irisCamera)
                {
                    ShaderOpacityPatch.submitPostDeferredForm(0D, formSortKey, depthWrite, afterFluids, deferredDraw);
                }
                else
                {
                    ShaderOpacityPatch.submitPostDeferredBbsForm(0D, formSortKey, depthWrite, afterFluids, deferredDraw);
                }
            }
            else
            {
                if (shadowPass)
                {
                    ShaderOpacityPatch.beginShadowForm();
                }

                if (!context.isPicking())
                {
                    consumers.setSubstitute(blockMainRecolor);
                }

                if (blockShaderTint != null)
                {
                    CustomVertexConsumerProvider.hijackVertexFormat((layer) ->
                    {
                        this.applyBlockMainPassHijackLayer(layer, blockShaderTint);
                        ShaderOpacityPatch.uploadShadowFormUniform();
                    });
                }
                else if (shadowPass)
                {
                    CustomVertexConsumerProvider.hijackVertexFormat((layer) ->
                    {
                        /* Recolor already has form opacity; leftover ColorModulator.a would square
                         * Bayer dither (same as soft Structure leaves / negative paint). */
                        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
                        ShaderOpacityPatch.uploadShadowFormUniform();
                    });
                }

                try
                {
                    /* Solid / BE first — fluids are a separate pass so GL state cannot leak into the world. */
                    this.renderRepeatedBlocks(context, context.stack, consumers, light, context.overlay, context.isPicking(), false, false, false, false);
                    consumers.draw();
                }
                finally
                {
                    if (shadowPass)
                    {
                        ShaderOpacityPatch.endShadowForm();
                    }

                    if (blockShaderTint != null)
                    {
                        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
                    }

                    consumers.setSubstitute(null);
                    CustomVertexConsumerProvider.clearRunnables();
                    RenderSystem.depthMask(true);
                    RenderSystem.enableCull();
                    RenderSystem.defaultBlendFunc();
                    RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
                }

                if (!context.isPicking() && this.hasFluid())
                {
                    /* Shadow keeps an in-pass mesh for silhouettes. World fluids are deferred to the
                     * end of the frame so every entity (player, cauldron, …) is already in the depth
                     * buffer — then water composites on top (see-through) instead of being overdrawn. */
                    if (shadowPass)
                    {
                        this.renderFluidPass(context, context.stack, false, light, context.overlay, color, resolvedPaint);
                    }
                    else
                    {
                        this.submitDeferredFluidPass(context, color, resolvedPaint, light);
                    }
                }
            }

            boolean submitIrisOverlays = irisWorldPaintDeferral && !noshadingDefer;

            if (((!softPostDeferred && !noshadingDefer) || (softPostDeferred && submitIrisOverlays)) && runColorTintOverlay && !shadowPass && !context.isPicking())
            {
                Color overlayTint = colorGradeWanted ? storedFormColor.copyDeferringColorGrade() : formColor;

                this.form.applyFormOpacity(overlayTint);

                if (BBSRendering.isIrisWorldPaintDeferral())
                {
                    this.submitDeferredBlockColorTintOverlay(context, context.stack, overlayTint, color.a, context.overlay, false, storedFormColor);
                }
                else if (!softPostDeferred)
                {
                    this.renderBlockColorTintOverlay(context, context.stack, overlayTint, color.a, context.overlay, false, storedFormColor);
                }
            }

            if ((!softPostDeferred && !noshadingDefer && runPaintOverlay) || (softPostDeferred && submitIrisOverlays && runPaintOverlay))
            {
                this.submitDeferredBlockPaintOverlay(context, context.stack, resolvedPaint, color.a, context.overlay, paintSettings.transform, glowSettings, legacyGlow, glowIntensity, false);
            }



            if ((!softPostDeferred && !noshadingDefer && runGlowOverlay) || (softPostDeferred && submitIrisOverlays && runGlowOverlay))
            {
                if (irisWorldPaintDeferral)
                {
                    this.submitDeferredBlockGlowOverlayMasked(context, context.stack, glowSettings, legacyGlow, glowIntensity, color.a, context.overlay, deferredGlowTransform);
                }
                else if (!softPostDeferred)
                {
                    this.renderGlowOverlayMasked(context, context.stack, consumers, glowSettings, legacyGlow, glowIntensity, color.a, context.overlay, false, deferredGlowTransform);
                }
            }
            else if (!softPostDeferred && !noshadingDefer)
            {
                CustomVertexConsumerProvider.clearRunnables();
            }

            RenderSystem.defaultBlendFunc();
        }
        finally
        {
            this.blockMainPassGlowEmission = null;
            CustomVertexConsumerProvider.clearRunnables();
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);

            if (context.isPicking())
            {
                RenderSystem.enableCull();
            }

            context.stack.pop();
        }

        RenderSystem.enableDepthTest();
    }

    private void applyBlockMainPassHijackLayer(RenderLayer layer, Color shaderTint)
    {
        if (FormUtilsClient.isCrumblingLayer(layer))
        {
            return;
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        if (shaderTint != null)
        {
            RenderSystem.setShaderColor(shaderTint.r, shaderTint.g, shaderTint.b, shaderTint.a);
        }
    }

    private Function<VertexConsumer, VertexConsumer> getBlockMainConsumer(Color color, Color resolvedPaint)
    {
        if (resolvedPaint != null && resolvedPaint.a < 0F)
        {
            return BBSRendering.getBlockPaintConsumer(color, resolvedPaint);
        }

        return BBSRendering.getColorConsumer(color);
    }

    private void renderRepeatedBlocks(FormRenderingContext context, MatrixStack stack, CustomVertexConsumerProvider consumers, int light, int overlay, boolean picking, boolean ui, boolean glowOverlay, boolean paintOverlay, boolean entityVisualOverlay)
    {
        int repeatX = this.form.repeatX.get();
        int repeatY = this.form.repeatY.get();
        int repeatZ = this.form.repeatZ.get();
        int startX = BlockForm.repeatAxisStart(repeatX, this.form.repeatCenterX.get());
        int startY = BlockForm.repeatAxisStart(repeatY, this.form.repeatCenterY.get());
        int startZ = BlockForm.repeatAxisStart(repeatZ, this.form.repeatCenterZ.get());

        for (int y = 0; y < repeatY; y++)
        {
            for (int z = 0; z < repeatZ; z++)
            {
                for (int x = 0; x < repeatX; x++)
                {
                    stack.push();
                    stack.translate(startX + x, startY + y, startZ + z);

                    int blockLight = light;
                    BlockPos worldPos = null;

                    if (context != null)
                    {
                        worldPos = this.getRepeatBlockWorldPos(context, startX + x, startY + y, startZ + z);
                    }

                    if (!glowOverlay && context != null)
                    {
                        blockLight = this.resolveBlockLight(context, startX + x, startY + y, startZ + z, light);
                    }

                    boolean coarsePick = picking && context != null && context.stencilMap != null && !context.stencilMap.increment;

                    this.renderSingleBlock(stack, consumers, blockLight, overlay, picking, coarsePick, ui, glowOverlay, paintOverlay, entityVisualOverlay, worldPos);
                    stack.pop();
                }
            }
        }
    }

    /**
     * Samples world skylight/blocklight at each repeated block's world position.
     * Uses the entity/world matrix instead of the camera-relative render matrix.
     */
    private int resolveBlockLight(FormRenderingContext context, int localX, int localY, int localZ, int fallback)
    {
        StructureLightSettings sl = this.form.structureLight.get();
        boolean lightsEnabled = (sl != null) ? sl.enabled : this.form.emitLight.get();
        int lightIntensity = (sl != null) ? sl.intensity : this.form.lightIntensity.get();
        BlockState blockState = this.form.blockState.get();
        int luminance = (lightsEnabled && blockState != null) ? Math.min(blockState.getLuminance(), lightIntensity) : 0;

        if (this.form.repeatX.get() == 1 && this.form.repeatY.get() == 1 && this.form.repeatZ.get() == 1)
        {
            if (luminance > 0)
            {
                int blockLight = Math.max(LightmapTextureManager.getBlockLightCoordinates(fallback), luminance);
                int skyLight = LightmapTextureManager.getSkyLightCoordinates(fallback);

                fallback = LightmapTextureManager.pack(blockLight, skyLight);
            }

            return fallback;
        }

        World world = null;

        if (context.entity != null)
        {
            world = context.entity.getWorld();
        }

        if (world == null)
        {
            world = MinecraftClient.getInstance().world;
        }

        if (world == null)
        {
            if (luminance > 0)
            {
                int blockLight = Math.max(LightmapTextureManager.getBlockLightCoordinates(fallback), luminance);
                int skyLight = LightmapTextureManager.getSkyLightCoordinates(fallback);

                fallback = LightmapTextureManager.pack(blockLight, skyLight);
            }

            return fallback;
        }

        BlockPos blockPos = this.getRepeatBlockWorldPos(context, localX, localY, localZ);

        if (blockPos == null)
        {
            if (luminance > 0)
            {
                int blockLight = Math.max(LightmapTextureManager.getBlockLightCoordinates(fallback), luminance);
                int skyLight = LightmapTextureManager.getSkyLightCoordinates(fallback);

                fallback = LightmapTextureManager.pack(blockLight, skyLight);
            }

            return fallback;
        }

        int sampled = WorldRenderer.getLightmapCoordinates(world, blockPos);

        if (luminance > 0)
        {
            int blockLight = Math.max(LightmapTextureManager.getBlockLightCoordinates(sampled), luminance);
            int skyLight = LightmapTextureManager.getSkyLightCoordinates(sampled);

            sampled = LightmapTextureManager.pack(blockLight, skyLight);
        }

        return FormLightingRender.apply(sampled, this.form.lightingSettings, this.form.lighting.get());
    }

    private BlockPos getRepeatBlockWorldPos(FormRenderingContext context, int localX, int localY, int localZ)
    {
        if (context.world != null)
        {
            MatrixStack probe = new MatrixStack();

            probe.peek().getPositionMatrix().set(context.world.peek().getPositionMatrix());
            probe.translate(localX, localY, localZ);

            Vector3f translation = probe.peek().getPositionMatrix().getTranslation(new Vector3f());

            return BlockPos.ofFloored(translation.x, translation.y + 0.5D, translation.z);
        }

        if (context.entity == null)
        {
            return null;
        }

        Transform transform = this.createTransform();
        Vector3f offset = transform.createMatrix().transformPosition(new Vector3f(localX + 0.5F, localY, localZ + 0.5F), new Vector3f());
        float transition = context.getTransition();
        double x = Lerps.lerp(context.entity.getPrevX(), context.entity.getX(), transition) + offset.x;
        double y = Lerps.lerp(context.entity.getPrevY(), context.entity.getY(), transition) + offset.y;
        double z = Lerps.lerp(context.entity.getPrevZ(), context.entity.getZ(), transition) + offset.z;

        return BlockPos.ofFloored(x, y, z);
    }

    private void renderSingleBlock(MatrixStack stack, CustomVertexConsumerProvider consumers, int light, int overlay, boolean picking, boolean coarsePick, boolean ui, boolean glowOverlay, boolean paintOverlay, boolean entityVisualOverlay, BlockPos worldPos)
    {
        stack.push();
        stack.translate(-0.5F, 0F, -0.5F);

        /* UI preview uses fixed diffuse lights; world rendering relied on vanilla block lighting before repeat. */
        if (ui && !picking)
        {
            MatrixStackUtils.invertUiNormalY(stack);
        }

        /* Glass/ice etc. write depth in the entity pass and hide models behind the morph.
         * Terrain glass is drawn later in translucent; match that by not writing depth here.
         * Soft post-deferred already owns depth write — do not suppress it there. */
        boolean effectOverlay = paintOverlay || glowOverlay || entityVisualOverlay;
        boolean translucent = !picking && !effectOverlay && this.isTranslucentBlockState(this.form.blockState.get())
            && !ShaderOpacityPatch.isPostDeferredPhase();
        boolean savedDepthMask = false;

        if (translucent)
        {
            savedDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
            RenderSystem.depthMask(false);
        }

        try
        {
            BlockState blockState = this.form.blockState.get();
            boolean stencilPickProxy = picking && this.needsStencilPickProxy(blockState);
            boolean coarseUnitPick = stencilPickProxy && coarsePick;
            boolean coarseThinPick = coarsePick && !stencilPickProxy && this.needsPickVolume(blockState);

            /* Entity-visual blocks skip BER during picking (wrong shader) and often have no baked
             * quads — draw picker_models proxy geometry instead. Alt-pick uses a unit cube; hover
             * uses the outline/model bounds so the highlight follows the sign shape. */
            if (coarseUnitPick || coarseThinPick)
            {
                this.renderPickVolume(stack, consumers, light, overlay);
            }
            else if (stencilPickProxy)
            {
                this.renderOutlinePickVolume(stack, consumers, blockState, light, overlay);
            }
            else
            {
                /* ENTITYBLOCK_ANIMATED (chests, ender chests, shulker boxes, …) delegates to
                 * BuiltinModelItemRenderer in renderBlockAsEntity, drawing a second duplicate item chest.
                 * Only invoke renderBlockModel when the block has a baked model. */
                if (blockState.getRenderType() == BlockRenderType.MODEL)
                {
                    this.renderBlockModel(blockState, stack, consumers, light, overlay, worldPos);
                }

                boolean skipBlockEntity = effectOverlay && !entityVisualOverlay;

                if (!picking && !skipBlockEntity)
                {
                    this.renderBlockEntity(stack, consumers, light, overlay, false, effectOverlay);
                }

                int breakingLevel = this.form.breaking.get();

                if (!picking && !effectOverlay && breakingLevel > 0 && breakingLevel <= 10 && blockState.getRenderType() == BlockRenderType.MODEL)
                {
                    RenderLayer crackingLayer = ModelLoader.BLOCK_DESTRUCTION_RENDER_LAYERS.get(breakingLevel - 1);
                    VertexConsumer delegateConsumer = consumers.getBuffer(crackingLayer);
                    VertexConsumer crackingConsumer = new OverlayVertexConsumer(delegateConsumer, stack.peek(), 1.0F);
                    Function<VertexConsumer, VertexConsumer> previousSubstitute = consumers.getSubstitute();

                    consumers.setSubstitute((vertexConsumer) -> crackingConsumer);

                    try
                    {
                        this.renderBlockModel(this.form.blockState.get(), stack, consumers, light, overlay, worldPos);
                    }
                    finally
                    {
                        consumers.setSubstitute(previousSubstitute);
                    }
                }
            }
        }
        finally
        {
            if (translucent)
            {
                RenderSystem.depthMask(savedDepthMask);
            }
        }

        stack.pop();
    }

    private void renderBlockModel(BlockState blockState, MatrixStack stack, CustomVertexConsumerProvider consumers, int light, int overlay, BlockPos worldPos)
    {
        BakedModel bakedModel = MinecraftClient.getInstance().getBlockRenderManager().getModel(blockState);
        int tint = this.resolveBlockTint(blockState, worldPos);
        float r = (float) (tint >> 16 & 0xFF) / 255.0F;
        float g = (float) (tint >> 8 & 0xFF) / 255.0F;
        float b = (float) (tint & 0xFF) / 255.0F;

        MinecraftClient.getInstance().getBlockRenderManager().getModelRenderer().render(
            stack.peek(),
            consumers.getBuffer(this.resolveBlockLayer(blockState)),
            blockState,
            bakedModel,
            r,
            g,
            b,
            light,
            overlay
        );
    }

    private RenderLayer resolveBlockLayer(BlockState state)
    {
        StructureData.syncFancyGraphicsFromOptions();
        RenderLayer base = RenderLayers.getBlockLayer(state);

        if (base == RenderLayer.getSolid())
        {
            return TexturedRenderLayers.getEntitySolid();
        }

        return RenderLayers.getEntityBlockLayer(state, false);
    }

    private int resolveBlockTint(BlockState state, BlockPos worldPos)
    {
        String biomeId = this.form.biomeId.get();
        boolean hasBiomeOverride = biomeId != null && !biomeId.isEmpty();

        if (hasBiomeOverride || MinecraftClient.getInstance().world != null)
        {
            if (this.blockView == null)
            {
                this.blockView = new VirtualBlockRenderView(new ArrayList<>());
            }

            this.blockView.setBiomeOverride(biomeId);

            if (worldPos != null)
            {
                this.blockView.setWorldAnchor(worldPos, 0, 0, 0);
            }
            else if (MinecraftClient.getInstance().player != null)
            {
                this.blockView.setWorldAnchor(MinecraftClient.getInstance().player.getBlockPos(), 0, 0, 0);
            }
            else
            {
                this.blockView.setWorldAnchor(BlockPos.ORIGIN, 0, 0, 0);
            }

            return MinecraftClient.getInstance().getBlockColors().getColor(state, this.blockView, BlockPos.ORIGIN, 0);
        }

        return MinecraftClient.getInstance().getBlockColors().getColor(state, null, null, 0);
    }

    private boolean isTranslucentBlockState(BlockState state)
    {
        if (state == null)
        {
            return false;
        }

        FluidState fluidState = state.getFluidState();

        /* Water renders in terrain translucent â€” match that by not writing depth in the entity pass. */
        if (!fluidState.isEmpty() && RenderLayers.getFluidLayer(fluidState) == RenderLayer.getTranslucent())
        {
            return true;
        }

        RenderLayer layer = RenderLayers.getBlockLayer(state);

        return layer == RenderLayer.getTranslucent() || layer == RenderLayer.getTripwire();
    }

    private boolean hasFluid()
    {
        BlockState state = this.form.blockState.get();

        return state != null && !state.getFluidState().isEmpty();
    }

    /**
     * Draws fluids after every entity so depth sorting is correct: models behind the water stay
     * behind it, while the player inside remains visible through the translucent surface.
     * Under Iris + Complementary/BSL, fluids flush in the translucent-terrain phase so
     * {@code gbuffers_water} (waves, foam, pack color) actually runs.
     */
    private void submitDeferredFluidPass(FormRenderingContext context, Color mainColor, Color resolvedPaint, int light)
    {
        Matrix4f positionMatrix = ModelVAORenderer.capturePaintOverlayRootMatrix(new Matrix4f(context.stack.peek().getPositionMatrix()));
        Matrix3f normalMatrix = new Matrix3f(context.stack.peek().getNormalMatrix());
        Color mainSnapshot = mainColor == null ? null : mainColor.copy();
        Color paintSnapshot = resolvedPaint == null ? null : resolvedPaint.copy();
        int overlay = context.overlay;
        float fluidMode = this.resolveFormFluidMode();

        Runnable draw = () ->
        {
            MatrixStack fluidStack = new MatrixStack();

            fluidStack.peek().getPositionMatrix().set(positionMatrix);
            fluidStack.peek().getNormalMatrix().set(normalMatrix);

            try
            {
                if (FormFluidShaderPatch.isWaterPhaseEnabled())
                {
                    FormFluidShaderPatch.setFormFluid(fluidMode);
                    FormFluidShaderPatch.uploadToCurrentProgram();
                }

                this.renderFluidPass(context, fluidStack, false, light, overlay, mainSnapshot, paintSnapshot);
            }
            finally
            {
                FormFluidShaderPatch.clearFormFluid();
                FormFluidShaderPatch.uploadToCurrentProgram();
            }
        };

        /* Complementary/BSL patch: Iris translucent-terrain phase (unchanged).
         * No shaders: AFTER_TRANSLUCENT so world depth occludes form fluids.
         * Other Iris packs: end-of-frame composite (no form-fluid patch — leave those alone). */
        if (FormFluidShaderPatch.isWaterPhaseEnabled() && fluidMode > 0.5F)
        {
            FormFluidShaderPatch.submitWaterPhaseFluid(fluidMode, draw);
        }
        else if (!BBSRendering.isIrisShadersEnabled())
        {
            FormFluidShaderPatch.submitVanillaFluid(fluidMode, draw);
        }
        else
        {
            ModelVAORenderer.submitTranslucentEndOfFrame(draw);
        }
    }

    private float resolveFormFluidMode()
    {
        BlockState state = this.form.blockState.get();

        if (state == null || state.getFluidState().isEmpty())
        {
            return 0F;
        }

        if (state.getFluidState().isIn(FluidTags.LAVA))
        {
            return 2F;
        }

        return 1F;
    }

    /**
     * Isolated fluid pass using vanilla FluidRenderer (sloped corners, level, biome tint,
     * interact-with-blocks). Color/paint bake through the same substitute as solid blocks.
     * Iris: entity block layer + overlay inject. Vanilla: real fluid translucent layer.
     */
    private void renderFluidPass(FormRenderingContext context, MatrixStack stack, boolean ui, int light, int overlay, Color mainColor, Color resolvedPaint)
    {
        CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
        boolean shaders = BBSRendering.isIrisShadersEnabled() && BBSRendering.isRenderingWorld() && !ui;
        boolean savedDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean savedCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);

        /* Pack fluid path: no form color substitute — the pack (water.glsl / lava terrain) owns
         * the look. packPhase covers water (1) and lava (2); lava is opaque so no blend forcing. */
        float packFluid = FormFluidShaderPatch.getFormFluid();
        boolean packPhase = packFluid > 0.5F;
        boolean lavaPhase = packFluid > 1.5F;
        float fluidMode = this.resolveFormFluidMode();
        /* When outer walls are on: optional double-sided draw (non-vanilla-water / non-shader) so far
         * faces stay visible. Vanilla water keeps normal cull — back-faces would double opacity. */
        boolean vanillaWater = !shaders && !ui && fluidMode > 0.5F && fluidMode < 1.5F;
        boolean allowDisableCull = this.form.outerFluidWalls.get() && !shaders && !vanillaWater;

        CustomVertexConsumerProvider.clearRunnables();
        CustomVertexConsumerProvider.hijackVertexFormat((l) ->
        {
            if (!lavaPhase)
            {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
            }

            RenderSystem.setShaderTexture(0, PlayerScreenHandler.BLOCK_ATLAS_TEXTURE);
            /* Pack phase writes depth exactly like world fluids — Complementary's depth-based
             * effects (water fog column, refraction) need the surface in depthtex0. */
            RenderSystem.depthMask(packPhase);

            if (allowDisableCull)
            {
                RenderSystem.disableCull();
            }
            else if (vanillaWater)
            {
                RenderSystem.enableCull();
            }

            if (packPhase)
            {
                FormFluidShaderPatch.uploadToCurrentProgram();
            }
        });

        if (!lavaPhase)
        {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        }

        RenderSystem.depthMask(packPhase);
        RenderSystem.enableCull();

        if (allowDisableCull)
        {
            RenderSystem.disableCull();
        }

        /* Vanilla water: keep FluidRenderer biome tint + texture alpha untouched. */
        consumers.setSubstitute(packPhase || vanillaWater || mainColor == null ? null : this.getBlockMainConsumer(mainColor, resolvedPaint));
        consumers.setUI(ui);

        try
        {
            if (packPhase)
            {
                FormFluidShaderPatch.uploadToCurrentProgram();
            }

            if (vanillaWater)
            {
                /* beginVanillaPostCompositePass pulls geometry toward the camera with polygon
                 * offset — that makes form water draw through nearby grass/stone. World water
                 * has no such bias; cancel it for this draw only. */
                GL11.glPolygonOffset(0F, 0F);
                GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc(GL11.GL_LEQUAL);
                RenderSystem.depthMask(false);
                RenderSystem.enableCull();
            }

            this.renderRepeatedFluids(context, stack, consumers, light, overlay, ui, shaders);
            consumers.draw();
        }
        finally
        {
            consumers.setUI(false);
            consumers.setSubstitute(null);
            CustomVertexConsumerProvider.clearRunnables();
            RenderSystem.depthMask(savedDepthMask);

            if (savedCull)
            {
                RenderSystem.enableCull();
            }
            else
            {
                RenderSystem.disableCull();
            }

            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
            RenderSystem.setShaderTexture(0, PlayerScreenHandler.BLOCK_ATLAS_TEXTURE);
            RenderSystem.enableDepthTest();
        }
    }

    private void renderRepeatedFluids(FormRenderingContext context, MatrixStack stack, CustomVertexConsumerProvider consumers, int light, int overlay, boolean ui, boolean shaders)
    {
        int repeatX = this.form.repeatX.get();
        int repeatY = this.form.repeatY.get();
        int repeatZ = this.form.repeatZ.get();
        int startX = BlockForm.repeatAxisStart(repeatX, this.form.repeatCenterX.get());
        int startY = BlockForm.repeatAxisStart(repeatY, this.form.repeatCenterY.get());
        int startZ = BlockForm.repeatAxisStart(repeatZ, this.form.repeatCenterZ.get());
        int cachedBiomeColor = this.sampleFluidBiomeColor(context, startX, startY, startZ);
        boolean outerWalls = this.form.outerFluidWalls.get();
        /* Without outer walls only the top surface matters — skip buried layers entirely. */
        int yBegin = outerWalls ? 0 : Math.max(0, repeatY - 1);

        for (int y = yBegin; y < repeatY; y++)
        {
            for (int z = 0; z < repeatZ; z++)
            {
                for (int x = 0; x < repeatX; x++)
                {
                    int localX = startX + x;
                    int localY = startY + y;
                    int localZ = startZ + z;
                    BlockPos fluidWorldPos = context != null ? this.getRepeatBlockWorldPos(context, localX, localY, localZ) : null;

                    stack.push();
                    stack.translate(localX, localY, localZ);
                    stack.translate(-0.5F, 0F, -0.5F);

                    if (ui)
                    {
                        MatrixStackUtils.invertUiNormalY(stack);
                    }

                    this.renderFluid(stack, consumers, fluidWorldPos, localX, localY, localZ, startX, startY, startZ, startX + repeatX, startY + repeatY, startZ + repeatZ, cachedBiomeColor, shaders, outerWalls);
                    stack.pop();
                }
            }
        }
    }

    private int sampleFluidBiomeColor(FormRenderingContext context, int localX, int localY, int localZ)
    {
        World world = MinecraftClient.getInstance().world;

        if (world == null)
        {
            return 0x3F76E4;
        }

        BlockPos sample = null;

        if (context != null)
        {
            sample = this.getRepeatBlockWorldPos(context, localX, localY, localZ);
        }

        if (sample == null && MinecraftClient.getInstance().player != null)
        {
            sample = MinecraftClient.getInstance().player.getBlockPos();
        }

        if (sample == null)
        {
            return 0x3F76E4;
        }

        return world.getColor(sample, BiomeColors.WATER_COLOR);
    }

    /**
     * Tessellate at ORIGIN with a relative neighbor view (fixes repeat-center).
     * Vanilla FluidRenderer supplies sloping corners, level height and solid-neighbor flattening.
     */
    private void renderFluid(MatrixStack stack, CustomVertexConsumerProvider consumers, BlockPos fluidWorldPos, int localX, int localY, int localZ, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int cachedBiomeColor, boolean shaders, boolean outerWalls)
    {
        BlockState blockState = this.form.blockState.get();
        FluidState fluidState = blockState.getFluidState();

        if (fluidWorldPos == null && MinecraftClient.getInstance().player != null)
        {
            fluidWorldPos = MinecraftClient.getInstance().player.getBlockPos();
        }

        /* Pack-phase flush: real fluid layer so Iris binds gbuffers_water (water) or
         * gbuffers_terrain (lava), exactly like world fluids. */
        boolean packPhase = FormFluidShaderPatch.getFormFluid() > 0.5F;
        RenderLayer fluidLayer = packPhase
            ? RenderLayers.getFluidLayer(fluidState)
            : (shaders ? RenderLayers.getEntityBlockLayer(blockState, false) : RenderLayers.getFluidLayer(fluidState));
        boolean injectOverlay = shaders && !packPhase;
        /* Always cull shared faces when any axis repeats — prevents seam “holes” between cells. */
        boolean cull = this.form.cullFluid.get()
            && (this.form.repeatX.get() > 1 || this.form.repeatY.get() > 1 || this.form.repeatZ.get() > 1);
        boolean interact = this.form.interactBlocks.get();
        BlockPos cellLocal = new BlockPos(localX, localY, localZ);
        VertexConsumer baseConsumer = consumers.getBuffer(fluidLayer);
        /* Tag Iris' extended buffer with the fluid's real pack block id (mc_Entity) so the pack
         * sees genuine fluid geometry — water: waves/water.glsl; lava: lava waves/emission. */
        boolean blockTagged = packPhase
            && FormFluidShaderPatch.beginFluidBlockTag(baseConsumer, fluidState, blockState.getLuminance());
        VertexConsumer fluidConsumer = new FluidVertexConsumer(baseConsumer, stack.peek(), BlockPos.ORIGIN, injectOverlay);
        BlockFormFluidView view = new BlockFormFluidView(
            blockState,
            fluidWorldPos,
            cellLocal,
            cull,
            outerWalls,
            interact,
            minX,
            minY,
            minZ,
            maxX,
            maxY,
            maxZ,
            cachedBiomeColor
        );

        try
        {
            MinecraftClient.getInstance().getBlockRenderManager().renderFluid(BlockPos.ORIGIN, view, fluidConsumer, blockState, fluidState);
        }
        finally
        {
            if (blockTagged)
            {
                FormFluidShaderPatch.endFluidBlockTag(baseConsumer);
            }
        }
    }

    /**
     * Soft-opacity queue key for the block form origin (farther first).
     */
    private double computeBlockFormSortKey(Matrix4f drawMatrix, FormRenderingContext context)
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
    /**
     * Signs, chests, beds, … — stencil picking skips BER (non-picker shaders) and baked block
     * meshes are often empty; draw a picker_models proxy instead.
     */
    private boolean needsStencilPickProxy(BlockState state)
    {
        if (state == null)
        {
            return false;
        }

        return state.getRenderType() == BlockRenderType.INVISIBLE
            || state.getRenderType() == BlockRenderType.ENTITYBLOCK_ANIMATED
            || state.getBlock() instanceof BlockEntityProvider;
    }
    private boolean needsPickVolume(BlockState state)
    {
        if (state == null)
        {
            return false;
        }

        /* Signs / hanging signs / chests / beds / â€¦ â€” animated or invisible mesh, or any BE. */
        if (state.getRenderType() == BlockRenderType.INVISIBLE
            || state.getRenderType() == BlockRenderType.ENTITYBLOCK_ANIMATED
            || state.getBlock() instanceof BlockEntityProvider)
        {
            return true;
        }

        try
        {
            VoxelShape shape = state.getOutlineShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN, ShapeContext.absent());

            if (shape.isEmpty())
            {
                return true;
            }

            Box box = shape.getBoundingBox();

            /* Fences, panes, rods, chains, â€¦ â€” thin outline is nearly impossible to Alt-pick from the side. */
            return (box.maxX - box.minX) < 0.999D
                || (box.maxY - box.minY) < 0.999D
                || (box.maxZ - box.minZ) < 0.999D;
        }
        catch (Exception e)
        {
            return true;
        }
    }

    /**
     * One solid unit cube for Alt-pick stencil â€” clean single hitbox for signs/chests/beds/â€¦.
     * Stack is already translated to block local space (-0.5, 0, -0.5).
     * UVs must sample an opaque atlas texel; UV 0â€“1 spans the whole atlas and picker_models
     * discards transparent samples, which left only a noisy flat square (and looked like
     * extra offset hitboxes from the side).
     */
    private void renderPickVolume(MatrixStack stack, CustomVertexConsumerProvider consumers, int light, int overlay)
    {
        RenderSystem.setShaderTexture(0, PlayerScreenHandler.BLOCK_ATLAS_TEXTURE);
        RenderSystem.disableCull();

        VertexConsumer buffer = consumers.getBuffer(RenderLayer.getEntitySolid(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE));
        MatrixStack.Entry entry = stack.peek();
        Matrix4f matrix = entry.getPositionMatrix();
        float[] uv = this.getOpaquePickUv();

        this.emitPickCube(buffer, entry, matrix, 0F, 0F, 0F, 1F, 1F, 1F, uv[0], uv[1], light, overlay);
    }

    /**
     * Hover stencil proxy for entity-visual blocks — outline plus baked-model bounds so the
     * highlight wraps the sign board/post instead of a full 16×16 unit cube.
     */
    private void renderOutlinePickVolume(MatrixStack stack, CustomVertexConsumerProvider consumers, BlockState state, int light, int overlay)
    {
        float x0 = 0F;
        float y0 = 0F;
        float z0 = 0F;
        float x1 = 1F;
        float y1 = 1F;
        float z1 = 1F;

        if (state != null)
        {
            try
            {
                VoxelShape shape = state.getOutlineShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN, ShapeContext.absent());

                if (!shape.isEmpty())
                {
                    Box box = shape.getBoundingBox();

                    x0 = (float) box.minX;
                    y0 = (float) box.minY;
                    z0 = (float) box.minZ;
                    x1 = (float) box.maxX;
                    y1 = (float) box.maxY;
                    z1 = (float) box.maxZ;
                }
            }
            catch (Exception ignored)
            {}

            Vector3f modelMin = new Vector3f();
            Vector3f modelMax = new Vector3f();

            if (this.sampleBlockModelBounds(state, modelMin, modelMax))
            {
                x0 = Math.min(x0, modelMin.x);
                y0 = Math.min(y0, modelMin.y);
                z0 = Math.min(z0, modelMin.z);
                x1 = Math.max(x1, modelMax.x);
                y1 = Math.max(y1, modelMax.y);
                z1 = Math.max(z1, modelMax.z);
            }
        }

        RenderSystem.setShaderTexture(0, PlayerScreenHandler.BLOCK_ATLAS_TEXTURE);
        RenderSystem.disableCull();

        VertexConsumer buffer = consumers.getBuffer(RenderLayer.getEntitySolid(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE));
        MatrixStack.Entry entry = stack.peek();
        Matrix4f matrix = entry.getPositionMatrix();
        float[] uv = this.getOpaquePickUv();

        this.emitPickCube(buffer, entry, matrix, x0, y0, z0, x1, y1, z1, uv[0], uv[1], light, overlay);
    }

    private float[] getOpaquePickUv()
    {
        Sprite sprite = MinecraftClient.getInstance().getBakedModelManager()
            .getAtlas(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE)
            .getSprite(Identifier.of("minecraft", "block/white_concrete"));
        float u = (sprite.getMinU() + sprite.getMaxU()) * 0.5F;
        float v = (sprite.getMinV() + sprite.getMaxV()) * 0.5F;

        return new float[] {u, v};
    }

    private void emitPickCube(VertexConsumer buffer, MatrixStack.Entry entry, Matrix4f matrix, float x0, float y0, float z0, float x1, float y1, float z1, float u, float v, int light, int overlay)
    {
        /* Front faces */
        this.emitPickQuad(buffer, entry, matrix, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0F, 0F, -1F, u, v, light, overlay);
        this.emitPickQuad(buffer, entry, matrix, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, 0F, 0F, 1F, u, v, light, overlay);
        this.emitPickQuad(buffer, entry, matrix, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0, 0F, -1F, 0F, u, v, light, overlay);
        this.emitPickQuad(buffer, entry, matrix, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0F, 1F, 0F, u, v, light, overlay);
        this.emitPickQuad(buffer, entry, matrix, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, -1F, 0F, 0F, u, v, light, overlay);
        this.emitPickQuad(buffer, entry, matrix, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 1F, 0F, 0F, u, v, light, overlay);
        /* Back faces â€” entity solid layers may re-enable cull after hijack. */
        this.emitPickQuad(buffer, entry, matrix, x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, z0, 0F, 0F, 1F, u, v, light, overlay);
        this.emitPickQuad(buffer, entry, matrix, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, 0F, 0F, -1F, u, v, light, overlay);
        this.emitPickQuad(buffer, entry, matrix, x1, y0, z0, x1, y0, z1, x0, y0, z1, x0, y0, z0, 0F, 1F, 0F, u, v, light, overlay);
        this.emitPickQuad(buffer, entry, matrix, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0F, -1F, 0F, u, v, light, overlay);
        this.emitPickQuad(buffer, entry, matrix, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, 1F, 0F, 0F, u, v, light, overlay);
        this.emitPickQuad(buffer, entry, matrix, x1, y1, z0, x1, y1, z1, x1, y0, z1, x1, y0, z0, -1F, 0F, 0F, u, v, light, overlay);
    }

    private void emitPickQuad(VertexConsumer buffer, MatrixStack.Entry entry, Matrix4f matrix, float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float nx, float ny, float nz, float u, float v, int light, int overlay)
    {
        buffer.vertex(matrix, x0, y0, z0).color(1F, 1F, 1F, 1F).texture(u, v).overlay(overlay).light(light).normal(entry, nx, ny, nz);
        buffer.vertex(matrix, x1, y1, z1).color(1F, 1F, 1F, 1F).texture(u, v).overlay(overlay).light(light).normal(entry, nx, ny, nz);
        buffer.vertex(matrix, x2, y2, z2).color(1F, 1F, 1F, 1F).texture(u, v).overlay(overlay).light(light).normal(entry, nx, ny, nz);
        buffer.vertex(matrix, x3, y3, z3).color(1F, 1F, 1F, 1F).texture(u, v).overlay(overlay).light(light).normal(entry, nx, ny, nz);
    }

    private boolean isBlockEntityVisual()
    {
        BlockState state = this.form.blockState.get();

        if (state == null)
        {
            return false;
        }

        return state.getBlock() instanceof BlockEntityProvider
            || state.getRenderType() == BlockRenderType.ENTITYBLOCK_ANIMATED
            || state.getRenderType() == BlockRenderType.INVISIBLE;
    }

    /**
     * Solid blocks use the overlay when {@link FormColorEffects#wantsColorTintOverlay} is true.
     * Entity-visual blocks (signs, chests, …) use the spatial overlay for color transform at
     * scale 1 as well as 0.99 — only Color Grade alone stays on flat BE tint.
     */
    private boolean shouldRunBlockColorTintOverlay(boolean blockEntityVisual, Color storedFormColor)
    {
        if (!blockEntityVisual)
        {
            return FormColorEffects.wantsColorTintOverlay(storedFormColor);
        }

        return this.shouldRunBlockEntitySpatialColorOverlay(storedFormColor);
    }

    /**
     * Color tint / Color Grade overlay on entity-visual blocks (chests, beds, etc.).
     */
    private boolean shouldRunBlockEntitySpatialColorOverlay(Color storedFormColor)
    {
        if (storedFormColor == null)
        {
            return false;
        }

        return FormColorEffects.wantsColorTintOverlay(storedFormColor);
    }

    /**
     * Main-pass vertex bake — skipped on entity-visual blocks when the spatial overlay will
     * apply the same color (avoids double tint at scale 1).
     */
    private boolean shouldBakeBlockFormColor(Color storedFormColor)
    {
        if (!FormColorEffects.shouldBakeFormColor(storedFormColor))
        {
            return false;
        }

        if (this.isBlockEntityVisual() && this.shouldRunBlockEntitySpatialColorOverlay(storedFormColor))
        {
            return false;
        }

        return true;
    }

    private boolean shouldRunBlockPaintOverlay(boolean blockEntityVisual, PaintSettings paintSettings, boolean positivePaint)
    {
        return positivePaint;
    }

    private boolean shouldUseEntityVisualPaintOverlay()
    {
        return this.isBlockEntityVisual();
    }

    private boolean shouldRunBlockGlowOverlay(boolean positiveGlow)
    {
        return positiveGlow;
    }

    private boolean shouldUseEntityVisualGlowOverlay()
    {
        return this.isBlockEntityVisual();
    }

    private Color resolveBlockEntityColor()
    {
        Color storedFormColor = this.form.color.get();
        PaintSettings paintSettings = this.form.paintSettings.get();
        Color legacyPaint = this.form.paintColor.get();
        boolean blockEntityVisual = this.isBlockEntityVisual();
        boolean runPaintOverlay = this.shouldRunBlockPaintOverlay(blockEntityVisual, paintSettings, FormColorEffects.hasPositivePaint(paintSettings, legacyPaint));
        Color tint;

        if (this.shouldUseEntityVisualColorTintOverlay(storedFormColor))
        {
            tint = Color.white();
        }
        else
        {
            tint = storedFormColor == null ? Color.white() : storedFormColor.copyBakingColorGrade();
        }

        /* Uniform and masked paint on entity-visual blocks use the BER paint overlay pass
         * (block atlas overlays corrupt sign/chest atlases). Do not also bake paint into BE tint. */
        if (!runPaintOverlay && paintSettings != null && paintSettings.resolveIntensity(legacyPaint) != 0F)
        {
            FormColorEffects.applyPaintBlend(tint, paintSettings, legacyPaint);
        }

        this.form.applyFormOpacity(tint);

        return tint;
    }

    private boolean shouldUseEntityVisualColorTintOverlay(Color storedFormColor)
    {
        return this.isBlockEntityVisual() && this.shouldRunBlockEntitySpatialColorOverlay(storedFormColor);
    }

    /**
     * Block-local spans for entity-visual spatial masks when the transform is active (scale != 1).
     * At neutral scale the shader uses the inactive full-mask shortcut instead; these sizes apply
     * once scale deviates. Never below a full block so 1 → 0.99 does not jump to a tiny sign AABB.
     */
    private void resolveBlockFormMaskSize(Vector3f dest)
    {
        dest.set(1F, 1F, 1F);

        BlockState state = this.form.blockState.get();

        if (state == null)
        {
            return;
        }

        float sizeX = 1F;
        float sizeY = 1F;
        float sizeZ = 1F;

        try
        {
            VoxelShape shape = state.getOutlineShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN, ShapeContext.absent());

            if (!shape.isEmpty())
            {
                Box box = shape.getBoundingBox();

                sizeX = (float) Math.max(box.maxX - box.minX, 0.001D);
                sizeZ = (float) Math.max(box.maxZ - box.minZ, 0.001D);
                sizeY = (float) Math.max(box.maxY, 0.001D);
            }
        }
        catch (Exception ignored)
        {}

        Vector3f modelMin = new Vector3f();
        Vector3f modelMax = new Vector3f();

        if (this.sampleBlockModelBounds(state, modelMin, modelMax))
        {
            sizeX = Math.max(sizeX, modelMax.x - modelMin.x);
            sizeY = Math.max(sizeY, modelMax.y - modelMin.y);
            sizeZ = Math.max(sizeZ, modelMax.z - modelMin.z);
        }

        dest.set(
            Math.max(sizeX, 1F),
            Math.max(sizeY, 1F),
            Math.max(sizeZ, 1F)
        );
    }

    /**
     * @return false when the baked model exposes no quads (caller keeps outline / unit fallback).
     */
    private boolean sampleBlockModelBounds(BlockState state, Vector3f min, Vector3f max)
    {
        min.set(1F, 1F, 1F);
        max.set(0F, 0F, 0F);

        MinecraftClient client = MinecraftClient.getInstance();
        BakedModel model = client.getBlockRenderManager().getModels().getModel(state);

        if (model == null)
        {
            return false;
        }

        boolean found = false;
        Random random = Random.create(42L);

        for (Direction direction : Direction.values())
        {
            for (BakedQuad quad : model.getQuads(state, direction, random))
            {
                found = this.expandBoundsFromQuad(quad, min, max) || found;
            }
        }

        for (BakedQuad quad : model.getQuads(state, null, random))
        {
            found = this.expandBoundsFromQuad(quad, min, max) || found;
        }

        return found;
    }

    private boolean expandBoundsFromQuad(BakedQuad quad, Vector3f min, Vector3f max)
    {
        int[] data = quad.getVertexData();
        int stride = Math.max(data.length / 4, 3);
        boolean found = false;

        for (int v = 0; v < 4; v++)
        {
            int base = v * stride;

            if (base + 2 >= data.length)
            {
                continue;
            }

            float x = Float.intBitsToFloat(data[base]);
            float y = Float.intBitsToFloat(data[base + 1]);
            float z = Float.intBitsToFloat(data[base + 2]);

            min.x = Math.min(min.x, x);
            min.y = Math.min(min.y, y);
            min.z = Math.min(min.z, z);
            max.x = Math.max(max.x, x);
            max.y = Math.max(max.y, y);
            max.z = Math.max(max.z, z);
            found = true;
        }

        return found;
    }

    private void beginBlockVisualMaskSize(boolean enabled)
    {
        if (!enabled)
        {
            return;
        }

        this.resolveBlockFormMaskSize(this.blockVisualMaskSize);
        BlockEffectOverlayUniforms.setBlockVisualMaskSize(this.blockVisualMaskSize);
    }

    private void endBlockVisualMaskSize(boolean enabled)
    {
        if (!enabled)
        {
            return;
        }

        BlockEffectOverlayUniforms.clearBlockVisualMaskSize();
    }

    private boolean needsDeferredBlockEntityTint()
    {
        if (!this.isBlockEntityVisual() || !BBSRendering.isIrisWorldPaintDeferral())
        {
            return false;
        }

        Color beTint = this.resolveBlockEntityColor();

        return beTint.r < 0.999F || beTint.g < 0.999F || beTint.b < 0.999F || beTint.a < 0.999F;
    }

    private void submitDeferredBlockEntityTint(FormRenderingContext context, int overlay)
    {
        Matrix4f exactMvm = new Matrix4f(RenderSystem.getModelViewMatrix());
        Matrix4f exactStack = new Matrix4f(context.stack.peek().getPositionMatrix());
        Matrix3f normalMatrix = new Matrix3f(context.stack.peek().getNormalMatrix());

        ModelVAORenderer.submitVanillaPostComposite(() ->
        {
            CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
            MatrixStack overlayStack = new MatrixStack();

            overlayStack.peek().getPositionMatrix().set(exactStack);
            overlayStack.peek().getNormalMatrix().set(normalMatrix);

            RenderSystem.getModelViewStack().pushMatrix();
            RenderSystem.getModelViewStack().set(exactMvm);
            RenderSystem.applyModelViewMatrix();

            try
            {
                this.renderRepeatedBlockEntitiesTinted(context, overlayStack, consumers, LightmapTextureManager.MAX_LIGHT_COORDINATE, overlay);
                consumers.draw();
            }
            catch (Throwable ignored)
            {}
            finally
            {
                RenderSystem.getModelViewStack().popMatrix();
                RenderSystem.applyModelViewMatrix();
                consumers.setSubstitute(null);
                RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
            }
        });
    }

    private void renderRepeatedBlockEntitiesTinted(FormRenderingContext context, MatrixStack stack, CustomVertexConsumerProvider consumers, int light, int overlay)
    {
        int repeatX = this.form.repeatX.get();
        int repeatY = this.form.repeatY.get();
        int repeatZ = this.form.repeatZ.get();
        int startX = BlockForm.repeatAxisStart(repeatX, this.form.repeatCenterX.get());
        int startY = BlockForm.repeatAxisStart(repeatY, this.form.repeatCenterY.get());
        int startZ = BlockForm.repeatAxisStart(repeatZ, this.form.repeatCenterZ.get());

        for (int y = 0; y < repeatY; y++)
        {
            for (int z = 0; z < repeatZ; z++)
            {
                for (int x = 0; x < repeatX; x++)
                {
                    stack.push();
                    stack.translate(startX + x, startY + y, startZ + z);
                    stack.translate(-0.5F, 0F, -0.5F);

                    int blockLight = light;

                    if (context != null)
                    {
                        blockLight = this.resolveBlockLight(context, startX + x, startY + y, startZ + z, light);
                    }

                    this.renderBlockEntity(stack, consumers, blockLight, overlay, true);
                    stack.pop();
                }
            }
        }
    }

    private void renderBlockEntity(MatrixStack stack, CustomVertexConsumerProvider consumers, int light, int overlay, boolean forceTint)
    {
        this.renderBlockEntity(stack, consumers, light, overlay, forceTint, false);
    }

    private void renderBlockEntity(MatrixStack stack, CustomVertexConsumerProvider consumers, int light, int overlay, boolean forceTint, boolean effectOverlay)
    {
        if (!(this.form.blockState.get().getBlock() instanceof BlockEntityProvider provider))
        {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        BlockEntity blockEntity = provider.createBlockEntity(BlockPos.ORIGIN, this.form.blockState.get());

        if (blockEntity == null)
        {
            return;
        }

        if (client.world != null)
        {
            blockEntity.setWorld(client.world);
        }

        BlockEntityRenderDispatcher dispatcher = client.getBlockEntityRenderDispatcher();
        BlockEntityRenderer<?> renderer = dispatcher.get(blockEntity);

        if (renderer == null)
        {
            return;
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        BlockEntityRenderer raw = (BlockEntityRenderer) renderer;
        Function<VertexConsumer, VertexConsumer> previousSubstitute = consumers.getSubstitute();
        Color beTint = this.resolveBlockEntityColor();
        boolean applyTint = !effectOverlay;
        Color glowEmissionBake = !forceTint && !effectOverlay ? this.blockMainPassGlowEmission : null;
        boolean glowEmissionBakeActive = glowEmissionBake != null;

        try
        {
            if (applyTint)
            {
                if (glowEmissionBakeActive)
                {
                    Function<VertexConsumer, VertexConsumer> glowWrap = BBSRendering.getGlowOverlayConsumer(glowEmissionBake);
                    Function<VertexConsumer, VertexConsumer> colorWrap = BBSRendering.getColorConsumer(beTint);

                    consumers.setSubstitute((vertexConsumer) -> glowWrap.apply(colorWrap.apply(vertexConsumer)));
                }
                else
                {
                    consumers.setSubstitute(BBSRendering.getColorConsumer(beTint));
                }

                RenderSystem.setShaderColor(beTint.r, beTint.g, beTint.b, beTint.a);
            }
            else if (glowEmissionBakeActive)
            {
                Function<VertexConsumer, VertexConsumer> parentSub = previousSubstitute != null ? previousSubstitute : Function.identity();
                Function<VertexConsumer, VertexConsumer> glowWrap = BBSRendering.getGlowOverlayConsumer(glowEmissionBake);

                consumers.setSubstitute((vertexConsumer) -> glowWrap.apply(parentSub.apply(vertexConsumer)));
            }

            raw.render(blockEntity, 0F, stack, consumers, light, overlay);
        }
        finally
        {
            if (glowEmissionBakeActive)
            {
                GlowEmissionVertexConsumer.emissionColor = null;
            }

            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
            consumers.setSubstitute(previousSubstitute);
        }
    }

    private void submitDeferredBlockColorTintOverlay(FormRenderingContext context, MatrixStack stack, Color formColor, float alpha, int overlay, boolean ui, Color gradeSource)
    {
        Matrix4f exactMvm = new Matrix4f(RenderSystem.getModelViewMatrix());
        Matrix4f exactStack = new Matrix4f(stack.peek().getPositionMatrix());
        Matrix3f normalMatrix = new Matrix3f(stack.peek().getNormalMatrix());
        Color formColorSnapshot = formColor.copy();
        Color gradeSnapshot = gradeSource == null ? null : gradeSource.copy();

        ModelVAORenderer.submitColorTintOverlay(() ->
        {
            CustomVertexConsumerProvider overlayConsumers = FormUtilsClient.getProvider();
            MatrixStack overlayStack = new MatrixStack();

            overlayStack.peek().getPositionMatrix().set(exactStack);
            overlayStack.peek().getNormalMatrix().set(normalMatrix);

            RenderSystem.getModelViewStack().pushMatrix();
            RenderSystem.getModelViewStack().set(exactMvm);
            RenderSystem.applyModelViewMatrix();

            try
            {
                this.renderBlockColorTintOverlay(context, overlayStack, formColorSnapshot, alpha, overlay, ui, gradeSnapshot);
            }
            finally
            {
                RenderSystem.getModelViewStack().popMatrix();
                RenderSystem.applyModelViewMatrix();
            }
        });
    }

    private void renderBlockColorTintOverlay(FormRenderingContext context, MatrixStack stack, Color formColor, float alpha, int overlay, boolean ui, Color gradeSource)
    {
        CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
        boolean entityVisual = this.shouldUseEntityVisualColorTintOverlay(gradeSource != null ? gradeSource : formColor);

        this.renderColorTintOverlayPass(context, stack, consumers, formColor, alpha, overlay, ui, gradeSource, entityVisual);
    }

    private void renderColorTintOverlayPass(FormRenderingContext context, MatrixStack stack, CustomVertexConsumerProvider consumers, Color formColor, float alpha, int overlay, boolean ui, Color gradeSource, boolean entityVisual)
    {
        Matrix4f formRootInverse = new Matrix4f(stack.peek().getPositionMatrix()).invert();
        int savedDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        boolean savedDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean savedPolygonOffsetFill = GL11.glGetBoolean(GL11.GL_POLYGON_OFFSET_FILL);
        boolean savedCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);

        CustomVertexConsumerProvider.clearRunnables();
        CustomVertexConsumerProvider.hijackVertexFormat((l) ->
        {
            if (entityVisual)
            {
                BlockEffectOverlayUniforms.configureColorTintOverlayRenderStateEntityVisual(formRootInverse, formColor.transform, true, formColor, 0.5F, gradeSource);
            }
            else
            {
                BlockEffectOverlayUniforms.configureColorTintOverlayRenderState(formRootInverse, formColor.transform, true, formColor, 0.5F, gradeSource);
            }
        });

        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        /* Pull tint overlay toward camera so it does not z-fight the main block pass. */
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(-1F, -2F);

        /* Neutral vertices â€” lighting lives in the scene copy when grading. */
        consumers.setSubstitute(BBSRendering.getBlockColorTintOverlayConsumer());

        this.beginBlockVisualMaskSize(entityVisual);

        try
        {
            this.renderRepeatedBlocks(context, stack, consumers, LightmapTextureManager.MAX_LIGHT_COORDINATE, overlay, false, ui, false, !entityVisual, entityVisual);

            if (this.hasFluid())
            {
                this.renderRepeatedFluids(context, stack, consumers, LightmapTextureManager.MAX_LIGHT_COORDINATE, overlay, ui, false);
            }
            consumers.draw();
        }
        finally
        {
            this.endBlockVisualMaskSize(entityVisual);

            consumers.setSubstitute(null);
            RenderSystem.depthMask(savedDepthMask);
            RenderSystem.depthFunc(savedDepthFunc);
            GL11.glPolygonOffset(0F, 0F);

            if (savedPolygonOffsetFill)
            {
                GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
            }
            else
            {
                GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
            }

            if (savedCull)
            {
                RenderSystem.enableCull();
            }
            else
            {
                RenderSystem.disableCull();
            }

            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
            RenderSystem.defaultBlendFunc();
            CustomVertexConsumerProvider.clearRunnables();
        }
    }

    private void submitDeferredBlockPaintOverlay(FormRenderingContext context, MatrixStack stack, Color resolvedPaint, float alpha, int overlay, EffectTransform transform, GlowSettings glowSettings, Color legacyGlow, float glowIntensity, boolean ui)
    {
        Matrix4f exactMvm = new Matrix4f(RenderSystem.getModelViewMatrix());
        Matrix4f exactStack = new Matrix4f(stack.peek().getPositionMatrix());
        Matrix3f normalMatrix = new Matrix3f(stack.peek().getNormalMatrix());
        Color paintOverlay = new Color(resolvedPaint.r, resolvedPaint.g, resolvedPaint.b, resolvedPaint.a);

        paintOverlay.a *= alpha;

        ModelVAORenderer.submitPaintOverlay(false, () ->
        {
            CustomVertexConsumerProvider overlayConsumers = FormUtilsClient.getProvider();
            MatrixStack overlayStack = new MatrixStack();

            overlayStack.peek().getPositionMatrix().set(exactStack);
            overlayStack.peek().getNormalMatrix().set(normalMatrix);

            RenderSystem.getModelViewStack().pushMatrix();
            RenderSystem.getModelViewStack().set(exactMvm);
            RenderSystem.applyModelViewMatrix();

            try
            {
                this.renderPaintOverlayPass(null, overlayStack, overlayConsumers, paintOverlay, overlay, ui, transform, glowSettings, legacyGlow, glowIntensity, alpha);
            }
            finally
            {
                RenderSystem.getModelViewStack().popMatrix();
                RenderSystem.applyModelViewMatrix();
            }
        });
    }

    private void renderPaintOverlay(FormRenderingContext context, MatrixStack stack, CustomVertexConsumerProvider consumers, Color resolvedPaint, float alpha, int overlay, boolean ui, EffectTransform transform)
    {
        this.renderPaintOverlay(context, stack, consumers, resolvedPaint, alpha, overlay, ui, transform, null, null, 0F);
    }

    private void renderPaintOverlay(FormRenderingContext context, MatrixStack stack, CustomVertexConsumerProvider consumers, Color resolvedPaint, float alpha, int overlay, boolean ui, EffectTransform transform, GlowSettings glowSettings, Color legacyGlow, float glowIntensity)
    {
        Color paintOverlay = new Color(resolvedPaint.r, resolvedPaint.g, resolvedPaint.b, resolvedPaint.a);

        paintOverlay.a *= alpha;

        this.renderPaintOverlayPass(context, stack, consumers, paintOverlay, overlay, ui, transform, glowSettings, legacyGlow, glowIntensity, alpha);
    }

    private void renderPaintOverlayPass(FormRenderingContext context, MatrixStack stack, CustomVertexConsumerProvider consumers, Color paintOverlay, int overlay, boolean ui, EffectTransform transform)
    {
        this.renderPaintOverlayPass(context, stack, consumers, paintOverlay, overlay, ui, transform, null, null, 0F, 1F);
    }

    private void renderPaintOverlayPass(FormRenderingContext context, MatrixStack stack, CustomVertexConsumerProvider consumers, Color paintOverlay, int overlay, boolean ui, EffectTransform transform, GlowSettings glowSettings, Color legacyGlow, float glowIntensity, float alpha)
    {
        Matrix4f formRootInverse = new Matrix4f(stack.peek().getPositionMatrix()).invert();
        boolean entityVisual = this.shouldUseEntityVisualPaintOverlay();
        EffectTransform paintTransform = transform;

        if (entityVisual && paintTransform != null && !paintTransform.isActive())
        {
            paintTransform = null;
        }

        final EffectTransform maskTransform = paintTransform;
        boolean savedCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);

        CustomVertexConsumerProvider.clearRunnables();
        CustomVertexConsumerProvider.hijackVertexFormat((l) ->
        {
            if (entityVisual)
            {
                BlockEffectOverlayUniforms.configurePaintOverlayRenderStateEntityVisual(formRootInverse, maskTransform, true, glowSettings, legacyGlow, glowIntensity, alpha);
            }
            else
            {
                BlockEffectOverlayUniforms.configurePaintOverlayRenderState(formRootInverse, maskTransform, true, glowSettings, legacyGlow, glowIntensity, alpha, this.form.paintSettings.get(), this.form.paintColor.get(), this.form.getFormColor());
            }
        });

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderSystem.depthMask(false);

        consumers.setSubstitute(BBSRendering.getBlockPaintOverlayConsumer(paintOverlay));

        this.beginBlockVisualMaskSize(entityVisual);

        try
        {
            this.renderRepeatedBlocks(context, stack, consumers, LightmapTextureManager.MAX_LIGHT_COORDINATE, overlay, false, ui, false, !entityVisual, entityVisual);

            if (this.hasFluid())
            {
                this.renderRepeatedFluids(context, stack, consumers, LightmapTextureManager.MAX_LIGHT_COORDINATE, overlay, ui, false);
            }
            consumers.draw();
        }
        finally
        {
            this.endBlockVisualMaskSize(entityVisual);

            consumers.setSubstitute(null);
            RenderSystem.depthMask(true);

            if (savedCull)
            {
                RenderSystem.enableCull();
            }
            else
            {
                RenderSystem.disableCull();
            }

            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
            RenderSystem.defaultBlendFunc();
            CustomVertexConsumerProvider.clearRunnables();
        }
    }

    private void renderGlowOverlay(FormRenderingContext context, MatrixStack stack, CustomVertexConsumerProvider consumers, GlowSettings glowSettings, Color legacyGlow, float glowIntensity, float alpha, int overlay, boolean ui)
    {
        this.renderGlowOverlayMasked(context, stack, consumers, glowSettings, legacyGlow, glowIntensity, alpha, overlay, ui, null);
    }

    private void submitDeferredBlockGlowOverlayMasked(FormRenderingContext context, MatrixStack stack, GlowSettings glowSettings, Color legacyGlow, float glowIntensity, float alpha, int overlay, EffectTransform glowTransform)
    {
        Matrix4f exactMvm = new Matrix4f(RenderSystem.getModelViewMatrix());
        Matrix4f exactStack = new Matrix4f(stack.peek().getPositionMatrix());
        Matrix3f normalMatrix = new Matrix3f(stack.peek().getNormalMatrix());
        GlowSettings glowSnapshot = glowSettings.copy();
        Color legacyGlowSnapshot = legacyGlow == null ? null : legacyGlow.copy();
        EffectTransform glowTransformSnapshot = glowTransform == null ? null : glowTransform.copy();

        ModelVAORenderer.submitPaintOverlay(false, () ->
        {
            CustomVertexConsumerProvider overlayConsumers = FormUtilsClient.getProvider();
            MatrixStack overlayStack = new MatrixStack();

            overlayStack.peek().getPositionMatrix().set(exactStack);
            overlayStack.peek().getNormalMatrix().set(normalMatrix);

            RenderSystem.getModelViewStack().pushMatrix();
            RenderSystem.getModelViewStack().set(exactMvm);
            RenderSystem.applyModelViewMatrix();

            try
            {
                this.renderGlowOverlayMasked(context, overlayStack, overlayConsumers, glowSnapshot, legacyGlowSnapshot, glowIntensity, alpha, overlay, false, glowTransformSnapshot);
            }
            finally
            {
                RenderSystem.getModelViewStack().popMatrix();
                RenderSystem.applyModelViewMatrix();
            }
        });
    }

    private void renderGlowOverlayMasked(FormRenderingContext context, MatrixStack stack, CustomVertexConsumerProvider consumers, GlowSettings glowSettings, Color legacyGlow, float glowIntensity, float alpha, int overlay, boolean ui, EffectTransform glowTransform)
    {
        Color glowColor = FormColorEffects.resolveGlowOverlayEmissionColor(glowSettings, legacyGlow, alpha, glowIntensity);
        float shaderScale = FormColorEffects.resolveGlowOverlayShaderScale(glowIntensity);
        EffectTransform resolvedTransform = glowTransform;

        if (resolvedTransform == null)
        {
            resolvedTransform = FormColorEffects.resolveGlowEffectTransform(glowSettings, legacyGlow);
        }

        final EffectTransform maskTransform = resolvedTransform;
        boolean entityVisual = this.shouldUseEntityVisualGlowOverlay();

        Matrix4f formRootInverse = new Matrix4f(stack.peek().getPositionMatrix()).invert();
        boolean savedCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);

        CustomVertexConsumerProvider.clearRunnables();
        CustomVertexConsumerProvider.hijackVertexFormat((l) ->
        {
            if (entityVisual)
            {
                BlockEffectOverlayUniforms.configureGlowOverlayRenderStateEntityVisual(formRootInverse, maskTransform, true, 0.5F, shaderScale);
            }
            else
            {
                BlockEffectOverlayUniforms.configureGlowOverlayRenderState(formRootInverse, maskTransform, true, 0.5F, shaderScale);
            }

            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        });

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        RenderSystem.depthMask(false);

        boolean wasOffset = GL11.glGetBoolean(GL11.GL_POLYGON_OFFSET_FILL);
        if (wasOffset) GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);

        consumers.setSubstitute(BBSRendering.getBlockPaintOverlayConsumer(glowColor));
        consumers.setUI(ui);

        this.beginBlockVisualMaskSize(entityVisual);

        try
        {
            this.renderRepeatedBlocks(context, stack, consumers, LightmapTextureManager.MAX_LIGHT_COORDINATE, overlay, false, ui, true, false, entityVisual);

            if (this.hasFluid())
            {
                this.renderRepeatedFluids(context, stack, consumers, LightmapTextureManager.MAX_LIGHT_COORDINATE, overlay, ui, false);
            }
            consumers.draw();
        }
        finally
        {
            this.endBlockVisualMaskSize(entityVisual);

            if (wasOffset) GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
            else GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);

            GL11.glPolygonOffset(0F, 0F);

            consumers.setUI(false);
            consumers.setSubstitute(null);
            RenderSystem.depthMask(true);

            if (savedCull)
            {
                RenderSystem.enableCull();
            }
            else
            {
                RenderSystem.disableCull();
            }

            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
            RenderSystem.defaultBlendFunc();
            CustomVertexConsumerProvider.clearRunnables();
        }
    }

    /**
     * FluidRenderer emits vertices in absolute block coords for fluidPos. The form stack
     * is already translated to that cell, so this consumer subtracts the cell offset, then bakes
     * the matrix. With Iris, entity block layers expect an overlay element which fluid geometry
     * lacks, so it is injected before light (same as StructureFormRenderer).
     */
    private static class FluidVertexConsumer implements VertexConsumer
    {
        private final VertexConsumer parent;
        private final Matrix4f positionMatrix;
        private final Matrix3f normalMatrix;
        private final BlockPos offset;
        private final boolean injectOverlay;

        public FluidVertexConsumer(VertexConsumer parent, MatrixStack.Entry entry, BlockPos offset, boolean injectOverlay)
        {
            this.parent = parent;
            this.positionMatrix = new Matrix4f(entry.getPositionMatrix());
            this.normalMatrix = new Matrix3f(entry.getNormalMatrix());
            this.offset = offset;
            this.injectOverlay = injectOverlay;
        }

        @Override
        public VertexConsumer vertex(float x, float y, float z)
        {
            float nx = x - this.offset.getX();
            float ny = y - this.offset.getY();
            float nz = z - this.offset.getZ();
            float tx = this.positionMatrix.m00() * nx + this.positionMatrix.m10() * ny + this.positionMatrix.m20() * nz + this.positionMatrix.m30();
            float ty = this.positionMatrix.m01() * nx + this.positionMatrix.m11() * ny + this.positionMatrix.m21() * nz + this.positionMatrix.m31();
            float tz = this.positionMatrix.m02() * nx + this.positionMatrix.m12() * ny + this.positionMatrix.m22() * nz + this.positionMatrix.m32();

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
    }

    /**
     * Virtual world for FluidRenderer. Tessellation is always at ORIGIN; cellLocal is the
     * absolute repeat-cell coordinate so centered (negative) repeats still cull correctly.
     */
    private static class BlockFormFluidView implements BlockRenderView
    {
        private final BlockState state;
        private final BlockPos worldPos;
        private final BlockPos cellLocal;
        private final boolean cull;
        private final boolean outerWalls;
        private final boolean interact;
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;
        private final int cachedBiomeColor;

        public BlockFormFluidView(BlockState state, BlockPos worldPos, BlockPos cellLocal, boolean cull, boolean outerWalls, boolean interact, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int cachedBiomeColor)
        {
            this.state = state;
            this.worldPos = worldPos;
            this.cellLocal = cellLocal;
            this.cull = cull;
            this.outerWalls = outerWalls;
            this.interact = interact;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            this.cachedBiomeColor = cachedBiomeColor;
        }

        /**
         * Read neighbor coords as ints immediately — FluidRenderer reuses MutableBlockPos, so
         * holding the BlockPos reference across later checks can see stale values.
         */
        private boolean hasFormFluidAt(BlockPos relative)
        {
            int rx = relative.getX();
            int ry = relative.getY();
            int rz = relative.getZ();

            if (rx == 0 && ry == 0 && rz == 0)
            {
                return true;
            }

            /* Top-surface-only: pretend fluid covers every side and the bottom so FluidRenderer
             * drops those faces; leave above empty so the top surface still emits. */
            if (!this.outerWalls)
            {
                if (ry < 0 || (ry == 0 && (rx != 0 || rz != 0)))
                {
                    return true;
                }

                if (ry > 0)
                {
                    int ax = this.cellLocal.getX() + rx;
                    int ay = this.cellLocal.getY() + ry;
                    int az = this.cellLocal.getZ() + rz;

                    return ax >= this.minX && ax < this.maxX
                        && ay >= this.minY && ay < this.maxY
                        && az >= this.minZ && az < this.maxZ;
                }
            }

            if (!this.cull)
            {
                return false;
            }

            int ax = this.cellLocal.getX() + rx;
            int ay = this.cellLocal.getY() + ry;
            int az = this.cellLocal.getZ() + rz;

            return ax >= this.minX && ax < this.maxX
                && ay >= this.minY && ay < this.maxY
                && az >= this.minZ && az < this.maxZ;
        }

        private World getWorld()
        {
            return MinecraftClient.getInstance().world;
        }

        private BlockPos toWorldPos(BlockPos relative)
        {
            if (this.worldPos == null)
            {
                return relative;
            }

            return this.worldPos.add(relative.getX(), relative.getY(), relative.getZ());
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos)
        {
            if (!this.interact || this.hasFormFluidAt(pos))
            {
                return null;
            }

            World world = this.getWorld();

            return world != null && this.worldPos != null ? world.getBlockEntity(this.toWorldPos(pos)) : null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos)
        {
            if (this.hasFormFluidAt(pos))
            {
                return this.state;
            }

            if (this.interact)
            {
                World world = this.getWorld();

                if (world != null && this.worldPos != null)
                {
                    return world.getBlockState(this.toWorldPos(pos));
                }
            }

            return Blocks.AIR.getDefaultState();
        }

        @Override
        public FluidState getFluidState(BlockPos pos)
        {
            if (this.hasFormFluidAt(pos))
            {
                return this.state.getFluidState();
            }

            if (this.interact)
            {
                World world = this.getWorld();

                if (world != null && this.worldPos != null)
                {
                    return world.getFluidState(this.toWorldPos(pos));
                }
            }

            return Fluids.EMPTY.getDefaultState();
        }

        @Override
        public int getLuminance(BlockPos pos)
        {
            return this.getBlockState(pos).getLuminance();
        }

        @Override
        public float getBrightness(Direction direction, boolean shaded)
        {
            World world = this.getWorld();

            return world != null ? world.getBrightness(direction, shaded) : 1F;
        }

        @Override
        public LightingProvider getLightingProvider()
        {
            World world = this.getWorld();

            return world != null ? world.getLightingProvider() : null;
        }

        @Override
        public int getColor(BlockPos pos, ColorResolver colorResolver)
        {
            if (colorResolver == BiomeColors.WATER_COLOR)
            {
                return this.cachedBiomeColor;
            }

            World world = this.getWorld();

            if (world != null && this.worldPos != null)
            {
                return world.getColor(this.toWorldPos(pos), colorResolver);
            }

            return 0xFFFFFF;
        }

        @Override
        public int getLightLevel(LightType type, BlockPos pos)
        {
            World world = this.getWorld();

            if (world != null && this.worldPos != null)
            {
                return world.getLightLevel(type, this.toWorldPos(pos));
            }

            return type == LightType.SKY ? 15 : 0;
        }

        @Override
        public int getBaseLightLevel(BlockPos pos, int ambientDarkness)
        {
            World world = this.getWorld();

            if (world != null && this.worldPos != null)
            {
                return world.getBaseLightLevel(this.toWorldPos(pos), ambientDarkness);
            }

            return 15;
        }

        @Override
        public boolean isSkyVisible(BlockPos pos)
        {
            World world = this.getWorld();

            if (world != null && this.worldPos != null)
            {
                return world.isSkyVisible(this.toWorldPos(pos));
            }

            return true;
        }

        @Override
        public int getBottomY()
        {
            World world = this.getWorld();

            return world != null ? world.getBottomY() : -64;
        }

        @Override
        public int getTopY()
        {
            World world = this.getWorld();

            return world != null ? world.getTopY() : 320;
        }

        @Override
        public int getHeight()
        {
            return this.getTopY() - this.getBottomY();
        }
    }
}
