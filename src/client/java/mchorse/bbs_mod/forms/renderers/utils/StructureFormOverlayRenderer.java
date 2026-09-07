package mchorse.bbs_mod.forms.renderers.utils;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.cubic.render.vao.IModelVAO;
import mchorse.bbs_mod.cubic.render.vao.ModelVAORenderer;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.StructureForm;
import mchorse.bbs_mod.forms.forms.utils.EffectTransform;
import mchorse.bbs_mod.forms.forms.utils.EffectTransformMath;
import mchorse.bbs_mod.forms.forms.utils.GlowSettings;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.colors.Color;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;

import org.lwjgl.opengl.GL11;

import java.util.function.Consumer;

/**
 * Handles multi-pass shader overlays (Glow, Paint, Color Tint / Grade) and Iris deferred submissions.
 */
public class StructureFormOverlayRenderer
{
    public enum StructurePaintLayer
    {
        BIOME,
        ANIMATED,
        TRANSLUCENT
    }

    public StructureFormOverlayRenderer()
    {
    }

    public void prepareVaoPaintForMainPass(Color resolvedPaint)
    {
        if (resolvedPaint != null && resolvedPaint.a < 0F)
        {
            ModelVAORenderer.setPaint(resolvedPaint.r, resolvedPaint.g, resolvedPaint.b, resolvedPaint.a);
        }
        else
        {
            this.clearVaoPaint();
        }
    }

    public void clearVaoPaint()
    {
        ModelVAORenderer.setPaint(0F, 0F, 0F, 0F);
    }

    public void prepareVaoGlowForMainPass(GlowSettings glowSettings, Color legacyGlow, float glowIntensity)
    {
        if (glowIntensity < 0F)
        {
            Color glowColor = new Color();
            glowSettings.resolveColor(legacyGlow, glowColor);
            ModelVAORenderer.setGlow(glowSettings, glowColor.r, glowColor.g, glowColor.b, legacyGlow);
        }
        else
        {
            this.clearVaoGlow();
        }
    }

    public void clearVaoGlow()
    {
        GlowSettings glowOff = new GlowSettings();
        glowOff.intensity = 0F;
        ModelVAORenderer.setGlow(glowOff, 0F, 0F, 0F, null);
    }

    public void clearVaoColorTint()
    {
        ModelVAORenderer.clearColorEffectTransform();
        ModelVAORenderer.clearFormColorTint();
        ModelVAORenderer.clearFormColorGrade();
        ModelVAORenderer.clearGradeEffectTransforms();
    }

    public void resolveStructureMaskSize(StructureData data, Vector3f dest)
    {
        BlockPos min = data.getBoundsMin();
        BlockPos max = data.getBoundsMax();

        if (min != null && max != null)
        {
            dest.set(
                Math.max(1, max.getX() - min.getX() + 1),
                Math.max(1, max.getY() - min.getY() + 1),
                Math.max(1, max.getZ() - min.getZ() + 1)
            );
            return;
        }

        BlockPos sz = data.getSize();

        dest.set(
            Math.max(1, sz.getX()),
            Math.max(1, sz.getY()),
            Math.max(1, sz.getZ())
        );
    }

    public void resolveStructureMaskHalf(StructureData data, EffectTransform transform, Vector3f dest)
    {
        Vector3f size = new Vector3f();
        this.resolveStructureMaskSize(data, size);
        EffectTransformMath.resolveStructureMaskHalfExtents(transform, dest, size.x, size.y, size.z);
    }

    public void renderStructureGlowOverlay(StructureData data, FormRenderingContext context, MatrixStack stack, GlowSettings glowSettings, Color legacyGlow, float glowIntensity, float alpha, int overlay, boolean optimize, boolean useEntityLayers, Consumer<StructurePaintLayer> layerDraw, Consumer<MatrixStack> culledWorldDraw)
    {
        if (culledWorldDraw == null)
        {
            return;
        }

        EffectTransform glowTransform = FormColorEffects.resolveGlowEffectTransform(glowSettings, legacyGlow);
        boolean hasGlowTransform = glowTransform != null && glowTransform.isActive();

        this.runStructureBlocksGlowOverlayMasked(data, stack, glowSettings, legacyGlow, alpha, glowIntensity, hasGlowTransform ? glowTransform : null, culledWorldDraw);
    }

    public void submitDeferredStructureGlowOverlay(StructureData data, FormRenderingContext context, GlowSettings glowSettings, Color legacyGlow, float glowIntensity, float alpha, int overlay, boolean optimize, boolean useEntityLayers, EffectTransform glowTransform, Consumer<StructurePaintLayer> layerDraw, Consumer<MatrixStack> culledWorldDraw)
    {
        if (culledWorldDraw == null)
        {
            return;
        }

        Matrix4f exactMvm = new Matrix4f(RenderSystem.getModelViewMatrix());
        Matrix4f exactStack = new Matrix4f(context.stack.peek().getPositionMatrix());
        Matrix3f normalMatrix = new Matrix3f(context.stack.peek().getNormalMatrix());
        GlowSettings glowSnapshot = glowSettings.copy();
        Color legacyGlowSnapshot = legacyGlow == null ? null : legacyGlow.copy();

        ModelVAORenderer.submitPaintOverlay(false, () ->
        {
            MatrixStack overlayStack = new MatrixStack();
            overlayStack.peek().getPositionMatrix().set(exactStack);
            overlayStack.peek().getNormalMatrix().set(normalMatrix);

            RenderSystem.getModelViewStack().pushMatrix();
            RenderSystem.getModelViewStack().set(exactMvm);
            MatrixStackUtils.applyModelViewMatrix();

            try
            {
                this.renderStructureGlowOverlay(data, context, overlayStack, glowSnapshot, legacyGlowSnapshot, glowIntensity, alpha, overlay, optimize, useEntityLayers, layerDraw, culledWorldDraw);
            }
            finally
            {
                RenderSystem.getModelViewStack().popMatrix();
                MatrixStackUtils.applyModelViewMatrix();
            }
        });
    }

    private void runStructureBlocksGlowOverlayMasked(StructureData data, MatrixStack stack, GlowSettings glowSettings, Color legacyGlow, float alpha, float glowIntensity, EffectTransform glowTransform, Consumer<MatrixStack> draw)
    {
        if (draw == null)
        {
            return;
        }

        CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
        Vector3f structureSize = new Vector3f();
        this.resolveStructureMaskSize(data, structureSize);
        Matrix4f formRootInverse = new Matrix4f(stack.peek().getPositionMatrix()).invert();

        Color resolvedGlow = new Color();
        glowSettings.resolveColor(legacyGlow, resolvedGlow);

        float shaderScale = FormColorEffects.resolveGlowOverlayShaderScale(glowIntensity);
        Color glowColor = new Color(
            resolvedGlow.r,
            resolvedGlow.g,
            resolvedGlow.b,
            alpha
        );

        int savedDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        boolean savedDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean savedPolygonOffsetFill = GL11.glGetBoolean(GL11.GL_POLYGON_OFFSET_FILL);
        boolean savedCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);

        CustomVertexConsumerProvider.clearRunnables();
        CustomVertexConsumerProvider.hijackVertexFormat((l) ->
        {
            BlockEffectOverlayUniforms.configureGlowOverlayRenderStateStructure(formRootInverse, glowTransform, true, structureSize.x, structureSize.y, structureSize.z, shaderScale);
            GlStateManager._enableBlend();
            GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, 1, 0);
            GlStateManager._enableDepthTest();
            GlStateManager._depthFunc(GL11.GL_LEQUAL);
            GlStateManager._depthMask(false);
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glPolygonOffset(-1F, -16F);
        });

        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, 1, 0);
        GlStateManager._enableDepthTest();
        GlStateManager._depthFunc(GL11.GL_LEQUAL);
        GlStateManager._depthMask(false);
        GL11.glPolygonOffset(-1F, -16F);

        consumers.setSubstitute(BBSRendering.getBlockPaintOverlayConsumer(glowColor));

        try
        {
            draw.accept(stack);
            consumers.draw();
        }
        finally
        {
            consumers.setSubstitute(null);
            // RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
            GlStateManager._depthMask(savedDepthMask);
            GlStateManager._depthFunc(savedDepthFunc);

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
                GlStateManager._enableCull();
            }
            else
            {
                GlStateManager._disableCull();
            }

            GL11.glPolygonOffset(0F, 0F);
            GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            CustomVertexConsumerProvider.clearRunnables();
        }
    }

    private void runStructureBlocksGlowOverlay(StructureData data, MatrixStack stack, GlowSettings glowSettings, Color legacyGlow, float alpha, float glowIntensity, Consumer<MatrixStack> draw)
    {
        this.runStructureBlocksGlowOverlayMasked(data, stack, glowSettings, legacyGlow, alpha, glowIntensity, null, draw);
    }

    public void renderStructurePaintOverlay(StructureData data, IModelVAO vao, FormRenderingContext context, MatrixStack stack, Color resolvedPaint, float alpha, int overlay, boolean optimize, boolean useEntityLayers, EffectTransform transform, GlowSettings glowSettings, Color legacyGlow, float glowIntensity, Consumer<StructurePaintLayer> layerDraw, Consumer<MatrixStack> culledWorldDraw)
    {
        Color paintOverlay = new Color(resolvedPaint.r, resolvedPaint.g, resolvedPaint.b, resolvedPaint.a);
        paintOverlay.a *= alpha;

        this.renderStructurePaintOverlayPass(data, vao, context, stack, paintOverlay, overlay, optimize, useEntityLayers, transform, glowSettings, legacyGlow, glowIntensity, alpha, layerDraw, culledWorldDraw);
    }

    public void submitDeferredStructurePaintOverlay(StructureData data, IModelVAO vao, FormRenderingContext context, Color resolvedPaint, float alpha, int overlay, boolean optimize, boolean useEntityLayers, EffectTransform transform, GlowSettings glowSettings, Color legacyGlow, float glowIntensity, Consumer<StructurePaintLayer> layerDraw, Consumer<MatrixStack> culledWorldDraw)
    {
        Matrix4f positionMatrix = ModelVAORenderer.capturePaintOverlayRootMatrix(new Matrix4f(context.stack.peek().getPositionMatrix()));
        Matrix3f normalMatrix = new Matrix3f(context.stack.peek().getNormalMatrix());
        Color paintOverlay = new Color(resolvedPaint.r, resolvedPaint.g, resolvedPaint.b, resolvedPaint.a);
        paintOverlay.a *= alpha;

        ModelVAORenderer.submitPaintOverlay(false, () ->
        {
            MatrixStack overlayStack = new MatrixStack();
            overlayStack.peek().getPositionMatrix().set(positionMatrix);
            overlayStack.peek().getNormalMatrix().set(normalMatrix);

            this.renderStructurePaintOverlayPass(data, vao, context, overlayStack, paintOverlay, overlay, optimize, useEntityLayers, transform, glowSettings, legacyGlow, glowIntensity, alpha, layerDraw, culledWorldDraw);
        });
    }

    private void renderStructurePaintOverlayPass(StructureData data, IModelVAO vao, FormRenderingContext context, MatrixStack stack, Color paintOverlay, int overlay, boolean optimize, boolean useEntityLayers, EffectTransform transform, GlowSettings glowSettings, Color legacyGlow, float glowIntensity, float alpha, Consumer<StructurePaintLayer> layerDraw, Consumer<MatrixStack> culledWorldDraw)
    {
        if (culledWorldDraw != null)
        {
            this.runStructureBlocksPaintOverlay(data, paintOverlay, stack, transform, glowSettings, legacyGlow, glowIntensity, alpha, () -> culledWorldDraw.accept(stack));
        }
        else if (optimize)
        {
            if (vao != null)
            {
                this.renderStructureVaoPaintOverlay(data, vao, stack, Color.white(), paintOverlay, LightmapTextureManager.MAX_LIGHT_COORDINATE, overlay, transform);
            }

            if (data.hasBiomeTintedLayer() && layerDraw != null)
            {
                this.runStructureBlocksPaintOverlay(data, paintOverlay, stack, transform, glowSettings, legacyGlow, glowIntensity, alpha, () -> layerDraw.accept(StructurePaintLayer.BIOME));
            }

            if (data.hasAnimatedLayer() && layerDraw != null)
            {
                this.runStructureBlocksPaintOverlay(data, paintOverlay, stack, transform, glowSettings, legacyGlow, glowIntensity, alpha, () -> layerDraw.accept(StructurePaintLayer.ANIMATED));
            }

            if (data.hasTranslucentLayer() && layerDraw != null)
            {
                this.runStructureBlocksPaintOverlay(data, paintOverlay, stack, transform, glowSettings, legacyGlow, glowIntensity, alpha, () -> layerDraw.accept(StructurePaintLayer.TRANSLUCENT));
            }
        }
    }

    private void renderStructureVaoPaintOverlay(StructureData data, IModelVAO vao, MatrixStack stack, Color tint, Color paintOverlay, int light, int overlay, EffectTransform transform)
    {
        GameRenderer gameRenderer = MinecraftClient.getInstance().gameRenderer;
        Matrix4f formRootInverse = new Matrix4f(stack.peek().getPositionMatrix()).invert();
        Vector3f paintMaskHalf = new Vector3f();

        this.resolveStructureMaskHalf(data, transform, paintMaskHalf);

        // MinecraftClient.getInstance().gameRenderer.getLightmapTextureManager().enable();
        // MinecraftClient.getInstance().gameRenderer.getOverlayTexture().setupOverlayColor();

        try
        {
            this.clearVaoColorTint();
            ModelVAORenderer.beginPaintOverlayPass(false);
            GL11.glPolygonOffset(FlatPaintOverlayPass.POLYGON_OFFSET_FACTOR, FlatPaintOverlayPass.POLYGON_OFFSET_UNITS);
            ModelVAORenderer.setPaint(paintOverlay.r, paintOverlay.g, paintOverlay.b, paintOverlay.a);
            ModelVAORenderer.setPaintEffectTransform(formRootInverse, transform, paintMaskHalf, true);
            BBSModClient.getTextures().bindTexture(new Link(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE.getNamespace(), SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE.getPath()));
            GlStateManager._enableBlend();
            GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            GlStateManager._depthMask(false);
            ModelVAORenderer.render(BBSShaders.getModel(), vao, stack, tint.r, tint.g, tint.b, tint.a, light, overlay);
        }
        finally
        {
            GlStateManager._depthMask(true);
            ModelVAORenderer.clearPaintEffectTransform();
            ModelVAORenderer.endPaintOverlayPass();
            this.clearVaoPaint();
            // MinecraftClient.getInstance().gameRenderer.getLightmapTextureManager().disable();
            // MinecraftClient.getInstance().gameRenderer.getOverlayTexture().teardownOverlayColor();
        }
    }

    private void runStructureBlocksPaintOverlay(StructureData data, Color paintOverlay, MatrixStack stack, EffectTransform transform, GlowSettings glowSettings, Color legacyGlow, float glowIntensity, float alpha, Runnable draw)
    {
        CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
        Matrix4f formRootInverse = new Matrix4f(stack.peek().getPositionMatrix()).invert();
        int savedDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        boolean savedDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean savedPolygonOffsetFill = GL11.glGetBoolean(GL11.GL_POLYGON_OFFSET_FILL);
        boolean savedCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);

        CustomVertexConsumerProvider.clearRunnables();

        Vector3f structureSize = new Vector3f();
        this.resolveStructureMaskSize(data, structureSize);
        CustomVertexConsumerProvider.hijackVertexFormat((l) ->
        {
            BlockEffectOverlayUniforms.configurePaintOverlayRenderStateStructure(formRootInverse, transform, true, glowSettings, legacyGlow, glowIntensity, alpha, structureSize.x, structureSize.y, structureSize.z);
        });

        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager._enableDepthTest();
        GlStateManager._depthFunc(GL11.GL_LEQUAL);
        GlStateManager._depthMask(false);
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(FlatPaintOverlayPass.POLYGON_OFFSET_FACTOR, FlatPaintOverlayPass.POLYGON_OFFSET_UNITS);

        consumers.setSubstitute(BBSRendering.getBlockPaintOverlayConsumer(paintOverlay));

        try
        {
            draw.run();
            consumers.draw();
        }
        finally
        {
            consumers.setSubstitute(null);
            GlStateManager._depthMask(savedDepthMask);
            GlStateManager._depthFunc(savedDepthFunc);
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
                GlStateManager._enableCull();
            }
            else
            {
                GlStateManager._disableCull();
            }

            CustomVertexConsumerProvider.clearRunnables();
        }
    }

    public Color resolveStructureColorTintUniform(StructureForm form, Color formColor)
    {
        Color stored = form.color.get();

        if (stored != null && stored.hasColorAdjustments())
        {
            Color tint = stored.copyDeferringColorGrade();

            if (formColor != null && formColor.transform != null)
            {
                tint.transform = formColor.transform.copy();
            }
            else if (stored.transform != null)
            {
                tint.transform = stored.transform.copy();
            }

            form.applyFormOpacity(tint);
            return tint;
        }

        return formColor;
    }

    public void renderStructureColorTintOverlay(StructureData data, StructureForm form, FormRenderingContext context, MatrixStack stack, Color formColor, float alpha, int overlay, boolean optimize, boolean useEntityLayers, boolean includeVao, Consumer<StructurePaintLayer> layerDraw, Consumer<MatrixStack> culledWorldDraw)
    {
        this.renderStructureColorTintOverlayPass(data, form, context, stack, formColor, alpha, overlay, optimize, useEntityLayers, includeVao, layerDraw, culledWorldDraw);
    }

    public void submitDeferredStructureColorTintOverlay(StructureData data, StructureForm form, FormRenderingContext context, Color formColor, float alpha, int overlay, boolean optimize, boolean useEntityLayers, Consumer<StructurePaintLayer> layerDraw, Consumer<MatrixStack> culledWorldDraw)
    {
        Matrix4f positionMatrix = ModelVAORenderer.capturePaintOverlayRootMatrix(new Matrix4f(context.stack.peek().getPositionMatrix()));
        Matrix3f normalMatrix = new Matrix3f(context.stack.peek().getNormalMatrix());
        Color formColorSnapshot = formColor.copy();

        ModelVAORenderer.submitColorTintOverlay(() ->
        {
            MatrixStack overlayStack = new MatrixStack();
            overlayStack.peek().getPositionMatrix().set(positionMatrix);
            overlayStack.peek().getNormalMatrix().set(normalMatrix);

            this.renderStructureColorTintOverlayPass(data, form, context, overlayStack, formColorSnapshot, alpha, overlay, optimize, useEntityLayers, false, layerDraw, culledWorldDraw);
        });
    }

    private void renderStructureColorTintOverlayPass(StructureData data, StructureForm form, FormRenderingContext context, MatrixStack stack, Color formColor, float alpha, int overlay, boolean optimize, boolean useEntityLayers, boolean includeVao, Consumer<StructurePaintLayer> layerDraw, Consumer<MatrixStack> culledWorldDraw)
    {
        Color tintUniform = this.resolveStructureColorTintUniform(form, formColor);

        if (culledWorldDraw != null)
        {
            this.runStructureBlocksColorTintOverlay(data, form, tintUniform, stack, form.color.get(), () -> culledWorldDraw.accept(stack));
        }
        else if (optimize)
        {
            if (data.hasBiomeTintedLayer() && layerDraw != null)
            {
                this.runStructureBlocksColorTintOverlay(data, form, tintUniform, stack, form.color.get(), () -> layerDraw.accept(StructurePaintLayer.BIOME));
            }

            if (data.hasAnimatedLayer() && layerDraw != null)
            {
                this.runStructureBlocksColorTintOverlay(data, form, tintUniform, stack, form.color.get(), () -> layerDraw.accept(StructurePaintLayer.ANIMATED));
            }

            if (data.hasTranslucentLayer() && layerDraw != null)
            {
                this.runStructureBlocksColorTintOverlay(data, form, tintUniform, stack, form.color.get(), () -> layerDraw.accept(StructurePaintLayer.TRANSLUCENT));
            }
        }
    }

    private void runStructureBlocksColorTintOverlay(StructureData data, StructureForm form, Color formColor, MatrixStack stack, Color gradeSource, Runnable draw)
    {
        if (draw == null)
        {
            return;
        }

        CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
        Matrix4f formRootInverse = new Matrix4f(stack.peek().getPositionMatrix()).invert();
        int savedDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        boolean savedDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean savedPolygonOffsetFill = GL11.glGetBoolean(GL11.GL_POLYGON_OFFSET_FILL);
        boolean savedCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);

        CustomVertexConsumerProvider.clearRunnables();

        Vector3f structureSize = new Vector3f();
        this.resolveStructureMaskSize(data, structureSize);
        CustomVertexConsumerProvider.hijackVertexFormat((l) ->
        {
            BlockEffectOverlayUniforms.configureColorTintOverlayRenderStateStructure(formRootInverse, formColor.transform, true, formColor, gradeSource, structureSize.x, structureSize.y, structureSize.z);
        });

        GlStateManager._enableBlend();
        GlStateManager._enableDepthTest();
        GlStateManager._depthFunc(GL11.GL_LEQUAL);
        GlStateManager._depthMask(false);
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(-1F, -2F);

        consumers.setSubstitute(BBSRendering.getBlockColorTintOverlayConsumer());

        try
        {
            draw.run();
            consumers.draw();
        }
        finally
        {
            consumers.setSubstitute(null);
            GlStateManager._depthMask(savedDepthMask);
            GlStateManager._depthFunc(savedDepthFunc);
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
                GlStateManager._enableCull();
            }
            else
            {
                GlStateManager._disableCull();
            }

            GlStateManager._blendFuncSeparate(770, 771, 1, 0);
            CustomVertexConsumerProvider.clearRunnables();
        }
    }
}
