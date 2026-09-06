package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.cubic.render.vao.ModelVAORenderer;
import mchorse.bbs_mod.forms.forms.ShapeForm;
import mchorse.bbs_mod.forms.forms.shape.ShapeGraphEvaluator;
import mchorse.bbs_mod.forms.forms.shape.nodes.IrisAttributeNode;
import mchorse.bbs_mod.forms.forms.shape.nodes.IrisShaderNode;
import mchorse.bbs_mod.forms.forms.shape.nodes.TextureNode;
import mchorse.bbs_mod.forms.forms.utils.EffectTransform;
import mchorse.bbs_mod.forms.forms.utils.EffectTransformMath;
import mchorse.bbs_mod.forms.forms.utils.GlowSettings;
import mchorse.bbs_mod.forms.forms.utils.PaintSettings;
import mchorse.bbs_mod.forms.renderers.utils.BillboardRenderLayers;
import mchorse.bbs_mod.forms.renderers.utils.FlatColorTintOverlayPass;
import mchorse.bbs_mod.forms.renderers.utils.FlatPaintOverlayPass;
import mchorse.bbs_mod.forms.renderers.utils.FormColorEffects;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.particles.ParticleScheme;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.iris.ShaderCurves;
import mchorse.bbs_mod.utils.iris.ShaderOpacityPatch;
import mchorse.bbs_mod.utils.math.Noise;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.VertexFormat;

import org.lwjgl.opengl.GL11;

import java.util.Random;
import java.util.function.Supplier;

public class ShapeFormRenderer extends FormRenderer<ShapeForm>
{
    private enum OverlayVertexMode
    {
        NONE,
        PAINT,
        COLOR_TINT
    }

    private ShapeGraphEvaluator evaluator;
    private float time;
    private Noise randomNoise = new Noise(0);
    private boolean unshadedVertices;
    private OverlayVertexMode overlayVertexMode = OverlayVertexMode.NONE;
    private EffectTransform overlayTransform;

    public ShapeFormRenderer(ShapeForm form)
    {
        super(form);
    }

    @Override
    protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        context.batcher.flush();

        MatrixStack stack = new MatrixStack();
        int scale = (y2 - y1) / 2;

        stack.push();
        stack.translate((x2 + x1) / 2, (y2 + y1) / 2, 40);
        MatrixStackUtils.scaleStack(stack, scale, scale, scale);

        /* Simple rotation for UI preview */
        stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(context.getTransition() * 2));
        stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(20));

        /* Shading fix for UI */
        MatrixStackUtils.invertUiNormalY(stack);

        BBSRendering.setupLevelLighting();

        this.renderShape(stack, BBSRendering::getEntityTranslucentProgram, OverlayTexture.DEFAULT_UV, LightmapTextureManager.MAX_LIGHT_COORDINATE, null);

        stack.pop();
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        this.renderShape(context.stack, BBSRendering::getEntityTranslucentProgram, context.overlay, context.light, context);
    }

    private void renderShape(MatrixStack stack, Supplier<ShaderProgram> shader, int overlay, int light, FormRenderingContext renderContext)
    {
        this.evaluator = new ShapeGraphEvaluator(this.form.graph.get());
        
        this.time = (System.currentTimeMillis() % 200000) / 1000F;

        if (!this.evaluator.irisNodes.isEmpty() && BBSRendering.isIrisShadersEnabled())
        {
            for (IrisShaderNode node : this.evaluator.irisNodes)
            {
                if (node.uniform.isEmpty()) continue;

                ShaderCurves.ShaderVariable variable = ShaderCurves.variableMap.get(node.uniform);

                if (variable != null)
                {
                    variable.value = (float) this.evaluator.evaluateInput(node.id, 0, 0, 0, 0, this.time);
                }
            }
        }

        BBSRendering.bindProgram(shader.get());
        BBSRendering.enableBlend();

        GlowSettings glowSettings = this.form.glowSettings.get();
        Color legacyGlow = this.form.glowingColor.get();
        float glowIntensity = glowSettings.resolveIntensity(legacyGlow);
        boolean positiveGlow = glowIntensity > 0F;

        if (this.form.lighting.get())
        {
            BBSRendering.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        }
        else
        {
            BBSRendering.defaultBlendFunc();
        }
        
        BBSRendering.disableCull();
        BBSRendering.enableDepthTest();

        // Bind texture — material node overrides the form's static texture
        Link texture = this.form.texture.get();

        TextureNode matNode = this.evaluator.getMaterialNode();

        if (matNode != null && matNode.texture != null)
        {
            texture = matNode.texture;
        }

        if (texture != null)
        {
            BBSModClient.getTextures().bindTexture(texture);
        }
        else
        {
            BBSModClient.getTextures().bindTexture(ParticleScheme.DEFAULT_TEXTURE);
        }

        Color rawFormColor = this.form.color.get();
        Color formColor = rawFormColor.copyBakingColorGrade();
        boolean wantsColorTransformMask = FormColorEffects.wantsColorTintOverlay(rawFormColor);
        PaintSettings paintSettings = this.form.paintSettings.get();
        Color legacyPaint = this.form.paintColor.get();
        boolean positivePaint = FormColorEffects.hasPositivePaint(paintSettings, legacyPaint);
        Color resolvedPaint = positivePaint ? FormColorEffects.resolvePaintColor(paintSettings, legacyPaint) : null;

        Color finalColor = this.resolveAppearanceColor(rawFormColor);

        this.form.applyFormOpacity(finalColor);

        if (finalColor.a <= 0.001F)
        {
            return;
        }

        if (!this.evaluator.irisAttributeNodes.isEmpty())
        {
            int blockLight = (light >> 4) & 0xF;
            int skyLight = (light >> 20) & 0xF;
            int overlayU = overlay & 0xFFFF;
            int overlayV = (overlay >> 16) & 0xFFFF;

            for (IrisAttributeNode node : this.evaluator.irisAttributeNodes)
            {
                double val = this.evaluator.evaluateInput(node.id, 0, 0, 0, 0, this.time);

                switch (node.attribute)
                {
                    case COLOR_R: finalColor.r = (float) val; break;
                    case COLOR_G: finalColor.g = (float) val; break;
                    case COLOR_B: finalColor.b = (float) val; break;
                    case COLOR_A: finalColor.a = (float) val; break;
                    case LIGHT_BLOCK: blockLight = (int) val; break;
                    case LIGHT_SKY: skyLight = (int) val; break;
                    case OVERLAY_U: overlayU = (int) val; break;
                    case OVERLAY_V: overlayV = (int) val; break;
                }
            }

            blockLight = Math.max(0, Math.min(15, blockLight));
            skyLight = Math.max(0, Math.min(15, skyLight));

            light = (blockLight << 4) | (skyLight << 20);
            overlay = overlayU | (overlayV << 16);
        }

        // Apply Color
        Color c = finalColor;
        FormColorEffects.applyShadowPassColorFix(c, this.form.color.get(), this.form.paintSettings.get(), this.form.paintColor.get(), BBSRendering.isIrisShadowPass());
        // RenderSystem.setShaderColor is not enough for VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL
        // We need to pass color per vertex

        // Transform
        stack.push();
        stack.scale(this.form.sizeX.get(), this.form.sizeY.get(), this.form.sizeZ.get());

        ShapeForm.ShapeType type = this.form.type.get();
        boolean shadowPass = BBSRendering.isIrisShadowPass();
        /* Under Iris, flats must defer — live path washes them. Opaque (#ff) is skipped by
         * needsIrisTranslucentFlatDeferral unless noshading is enabled. */
        boolean noshadingDefer = !shadowPass && BBSRendering.needsIrisNoshadingOpacityDeferral(c.a, this.form.noshadingOpacity.get());
        boolean deferTranslucent = (!shadowPass && BBSRendering.needsIrisTranslucentFlatDeferral(c.a)) || noshadingDefer;

        if (deferTranslucent)
        {
            Matrix4f positionMatrix = ModelVAORenderer.capturePaintOverlayRootMatrix(new Matrix4f(stack.peek().getPositionMatrix()));
            Matrix3f normalMatrix = new Matrix3f(stack.peek().getNormalMatrix());
            Color colorSnapshot = c.copy();
            int lightSnapshot = light;
            int overlaySnapshot = overlay;
            ShapeForm.ShapeType typeSnapshot = type;
            Link textureSnapshot = texture;
            boolean positiveGlowSnapshot = positiveGlow;
            float glowIntensitySnapshot = glowIntensity;
            GlowSettings glowSettingsSnapshot = glowSettings;
            Color legacyGlowSnapshot = legacyGlow;
            boolean lighting = this.form.lighting.get();
            /* Soft-opacity depth write stays opacity-based. */
            boolean depthWrite = ShaderOpacityPatch.shouldWriteDepthForOpacity(c.a);

            Runnable deferredDraw = () ->
            {
                MatrixStack overlayStack = new MatrixStack();

                overlayStack.peek().getPositionMatrix().set(positionMatrix);
                overlayStack.peek().getNormalMatrix().set(normalMatrix);

                this.drawDeferredShape(
                    overlayStack,
                    textureSnapshot,
                    typeSnapshot,
                    colorSnapshot,
                    overlaySnapshot,
                    lightSnapshot,
                    lighting,
                    positiveGlowSnapshot,
                    glowSettingsSnapshot,
                    legacyGlowSnapshot,
                    glowIntensitySnapshot,
                    BBSShaders::getModel,
                    false
                );
            };

            ModelVAORenderer.submitDeferredTranslucentModel(deferredDraw, depthWrite);
        }
        else
        {
            /* No-shader / opaque Iris path: depthMask true like vanilla. */
            if (BBSRendering.needsBbsModelForLowOpacity(c.a))
            {
                BBSRendering.bindProgram(BBSShaders.getModel());
            }

            BBSRendering.enableDepthTest();
            BBSRendering.depthMask(shadowPass || c.a >= ShaderOpacityPatch.LIVE_DEPTH_WRITE_ALPHA);

            Tessellator tessellator = Tessellator.getInstance();

            if (shadowPass)
            {
                ShaderOpacityPatch.beginShadowForm();
            }

            try
            {
                BufferBuilder builder = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);

                this.buildShapeGeometry(builder, stack, type, c, overlay, light);

                BillboardRenderLayers.draw(builder.end(), this.resolveTexture(texture), false, false, shadowPass || c.a >= ShaderOpacityPatch.LIVE_DEPTH_WRITE_ALPHA, false);
            }
            finally
            {
                if (shadowPass)
                {
                    ShaderOpacityPatch.endShadowForm();
                }
            }

            if (positiveGlow)
            {
                Color glowColor = FormColorEffects.resolveGlowOverlayEmissionColor(glowSettings, legacyGlow, c.a, glowIntensity);

                this.unshadedVertices = true;

                BufferBuilder glowBuilder = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

                this.buildShapeGeometry(glowBuilder, stack, type, glowColor, overlay, LightmapTextureManager.MAX_LIGHT_COORDINATE);

                BillboardRenderLayers.draw(glowBuilder.end(), this.resolveTexture(texture), false, false, false, false, true);

                this.unshadedVertices = false;
            }
        }

        if (positivePaint)
        {
            if (renderContext == null)
            {
                this.renderShapePaintOverlay(stack, texture, type, resolvedPaint, finalColor.a, paintSettings.transform, glowSettings, legacyGlow, glowIntensity);
            }
            else
            {
                this.submitDeferredShapePaintOverlay(stack, texture, type, resolvedPaint, finalColor.a, paintSettings.transform, glowSettings, legacyGlow, glowIntensity);
            }
        }

        if (wantsColorTransformMask)
        {
            EffectTransform colorTransform = rawFormColor.transform == null ? null : rawFormColor.transform.copy();

            if (renderContext == null)
            {
                this.renderShapeColorTintOverlay(stack, texture, type, formColor, overlay, colorTransform);
            }
            else
            {
                this.submitDeferredShapeColorTintOverlay(stack, texture, type, formColor, overlay, colorTransform);
            }
        }

        stack.pop();
        
        BBSRendering.disableBlend();
        BBSRendering.defaultBlendFunc();
    }

    private void drawDeferredShape(MatrixStack stack, Link texture, ShapeForm.ShapeType type, Color color, int overlay, int light, boolean lighting, boolean positiveGlow, GlowSettings glowSettings, Color legacyGlow, float glowIntensity, Supplier<ShaderProgram> shader, boolean unshaded)
    {
        Texture texObj = this.resolveTexture(texture);

        if (texture != null)
        {
            BBSModClient.getTextures().bindTexture(texture);
        }
        else
        {
            BBSModClient.getTextures().bindTexture(ParticleScheme.DEFAULT_TEXTURE);
        }

        BBSRendering.bindProgram(shader.get());
        BBSRendering.enableBlend();

        if (lighting)
        {
            BBSRendering.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        }
        else
        {
            BBSRendering.defaultBlendFunc();
        }

        /* beginDeferredTranslucentModelPass already set cull/depth — do not override. */
        this.unshadedVertices = unshaded;

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder builder = tessellator.begin(
            VertexFormat.DrawMode.QUADS,
            unshaded ? VertexFormats.POSITION_TEXTURE_COLOR : VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL
        );

        this.buildShapeGeometry(builder, stack, type, color, overlay, light);
        BillboardRenderLayers.draw(builder.end(), texObj, false, false, false, false);

        if (positiveGlow)
        {
            Color glowColor = FormColorEffects.resolveGlowOverlayEmissionColor(glowSettings, legacyGlow, color.a, glowIntensity);

            this.unshadedVertices = true;

            BufferBuilder glowBuilder = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

            this.buildShapeGeometry(glowBuilder, stack, type, glowColor, overlay, LightmapTextureManager.MAX_LIGHT_COORDINATE);
            BillboardRenderLayers.draw(glowBuilder.end(), texObj, false, false, false, false, true);

            this.unshadedVertices = false;
        }

        this.unshadedVertices = false;
        BBSRendering.defaultBlendFunc();
    }

    private void buildShapeGeometry(BufferBuilder builder, MatrixStack stack, ShapeForm.ShapeType type, Color c, int overlay, int light)
    {
        if (this.form.particles.get())
        {
            this.renderVolumeParticles(builder, stack, type, c, overlay, light);
        }
        else if (type == ShapeForm.ShapeType.BOX)
        {
            this.renderBox(builder, stack, c, overlay, light);
        }
        else if (type == ShapeForm.ShapeType.SPHERE)
        {
            this.renderSphere(builder, stack, c, overlay, light);
        }
        else if (type == ShapeForm.ShapeType.CYLINDER)
        {
            this.renderCylinder(builder, stack, false, c, overlay, light);
        }
        else if (type == ShapeForm.ShapeType.CAPSULE)
        {
            this.renderCylinder(builder, stack, true, c, overlay, light);
        }
    }

    private void renderVolumeParticles(BufferBuilder builder, MatrixStack stack, ShapeForm.ShapeType type, Color c, int overlay, int light)
    {
        float scale = this.form.particleScale.get();
        float density = this.form.particleDensity.get();
        float size = this.form.particleSize.get();
        ShapeForm.ParticleType particleType = this.form.particleType.get();
        
        if (scale <= 0) scale = 0.0001F;
        
        float step = 1.0F / scale;
        
        // Safety cap to avoid freezing the game with too many particles
        if (scale > 30) 
        {
             step = 1.0F / 30.0F;
        }

        float radius = 0.5F;
        float height = 1.0F;
        
        float minX = -radius;
        float maxX = radius;
        float minY = -radius;
        float maxY = radius;
        float minZ = -radius;
        float maxZ = radius;
        
        if (type == ShapeForm.ShapeType.BOX)
        {
            // Box is 1x1x1 by default in renderBox (0.5 extents)
        }
        else if (type == ShapeForm.ShapeType.SPHERE)
        {
            // Sphere radius 0.5
        }
        else if (type == ShapeForm.ShapeType.CYLINDER || type == ShapeForm.ShapeType.CAPSULE)
        {
            minY = -height / 2;
            maxY = height / 2;
        }
        
        this.randomNoise.setSeed(0);
        
        Matrix4f matrix = stack.peek().getPositionMatrix();
        Matrix3f normalMatrix = stack.peek().getNormalMatrix();
        
        for (float x = minX; x <= maxX; x += step)
        {
            for (float y = minY; y <= maxY; y += step)
            {
                for (float z = minZ; z <= maxZ; z += step)
                {
                    float jx = x;
                    float jy = y;
                    float jz = z;
                    
                    if (particleType == ShapeForm.ParticleType.DUST)
                    {
                        jx += (Math.random() - 0.5F) * step;
                        jy += (Math.random() - 0.5F) * step;
                        jz += (Math.random() - 0.5F) * step;
                    }
                    else
                    {
                        jx += (this.randomNoise.noise(x * 123.4F, y * 123.4F, z * 123.4F) - 0.5F) * step;
                    }
                    
                    boolean inside = false;
                    
                    if (type == ShapeForm.ShapeType.BOX)
                    {
                        inside = true;
                    }
                    else if (type == ShapeForm.ShapeType.SPHERE)
                    {
                        inside = (jx * jx + jy * jy + jz * jz) <= (radius * radius);
                    }
                    else if (type == ShapeForm.ShapeType.CYLINDER)
                    {
                        inside = (jx * jx + jz * jz) <= (radius * radius);
                    }
                    else if (type == ShapeForm.ShapeType.CAPSULE)
                    {
                        float r = radius;
                        float h = height / 2;
                        
                        if (jy > h - r) // Top hemisphere
                        {
                            float dy = jy - (h - r);
                            inside = (jx * jx + dy * dy + jz * jz) <= (r * r);
                        }
                        else if (jy < -h + r) // Bottom hemisphere
                        {
                            float dy = jy - (-h + r);
                            inside = (jx * jx + dy * dy + jz * jz) <= (r * r);
                        }
                        else // Body
                        {
                            inside = (jx * jx + jz * jz) <= (r * r);
                        }
                    }
                    
                    if (inside && this.evaluator != null)
                    {
                        double sdf = this.evaluator.compute(jx, jy, jz, this.time);
                        
                        if (sdf > 0)
                        {
                            inside = false;
                        }
                    }
                    
                    if (inside)
                    {
                        // Density check using noise for consistent pattern
                        double n = Math.abs(this.randomNoise.noise(jx * scale, jy * scale, jz * scale));
                        
                        if (n < density)
                        {
                            if (particleType == ShapeForm.ParticleType.SPHERE)
                            {
                                this.renderSphereParticle(builder, matrix, normalMatrix, jx, jy, jz, size, c, overlay, light);
                            }
                            else if (particleType == ShapeForm.ParticleType.BLOCK)
                            {
                                this.renderBlockParticle(builder, matrix, normalMatrix, jx, jy, jz, size, c, overlay, light);
                            }
                            else if (particleType == ShapeForm.ParticleType.DUST)
                            {
                                this.renderDustParticle(builder, matrix, normalMatrix, jx, jy, jz, size, c, overlay, light);
                            }
                            else
                            {
                                this.renderCrossedParticle(builder, matrix, normalMatrix, jx, jy, jz, size, c, overlay, light);
                            }
                        }
                    }
                }
            }
        }
    }
    
    private void renderCrossedParticle(BufferBuilder builder, Matrix4f matrix, Matrix3f normalMatrix, float x, float y, float z, float size, Color c, int overlay, int light)
    {
        float hs = size / 2;
        
        float disp = 0;
        
        if (this.evaluator != null)
        {
            disp = (float) this.evaluator.compute(x, y, z, this.time);
            int color = this.evaluator.computeColor(x, y, z, this.time);
            
            if (color != -1)
            {
                c = new Color().set(color);
            }
        }
        
        // Quad 1
        this.vertex(builder, matrix, normalMatrix, x - hs, y - hs, z - hs, 0, 0, 0, 1, 0, c, overlay, light);
        this.vertex(builder, matrix, normalMatrix, x + hs, y - hs, z + hs, 1, 0, 0, 1, 0, c, overlay, light);
        this.vertex(builder, matrix, normalMatrix, x + hs, y + hs, z + hs, 1, 1, 0, 1, 0, c, overlay, light);
        this.vertex(builder, matrix, normalMatrix, x - hs, y + hs, z - hs, 0, 1, 0, 1, 0, c, overlay, light);
        
        // Quad 2
        this.vertex(builder, matrix, normalMatrix, x - hs, y - hs, z + hs, 0, 0, 0, 1, 0, c, overlay, light);
        this.vertex(builder, matrix, normalMatrix, x + hs, y - hs, z - hs, 1, 0, 0, 1, 0, c, overlay, light);
        this.vertex(builder, matrix, normalMatrix, x + hs, y + hs, z - hs, 1, 1, 0, 1, 0, c, overlay, light);
        this.vertex(builder, matrix, normalMatrix, x - hs, y + hs, z + hs, 0, 1, 0, 1, 0, c, overlay, light);
    }
    
    private void renderBlockParticle(BufferBuilder builder, Matrix4f matrix, Matrix3f normal, float x, float y, float z, float size, Color c, int overlay, int light)
    {
        float hs = size / 2;
        
        if (this.evaluator != null)
        {
            int color = this.evaluator.computeColor(x, y, z, this.time);
            
            if (color != -1)
            {
                c = new Color().set(color);
            }
        }
        
        // Front
        this.vertex(builder, matrix, normal, x - hs, y - hs, z + hs, 0, 1, 0, 0, 1, c, overlay, light);
        this.vertex(builder, matrix, normal, x + hs, y - hs, z + hs, 1, 1, 0, 0, 1, c, overlay, light);
        this.vertex(builder, matrix, normal, x + hs, y + hs, z + hs, 1, 0, 0, 0, 1, c, overlay, light);
        this.vertex(builder, matrix, normal, x - hs, y + hs, z + hs, 0, 0, 0, 0, 1, c, overlay, light);
        
        // Back
        this.vertex(builder, matrix, normal, x + hs, y - hs, z - hs, 0, 1, 0, 0, -1, c, overlay, light);
        this.vertex(builder, matrix, normal, x - hs, y - hs, z - hs, 1, 1, 0, 0, -1, c, overlay, light);
        this.vertex(builder, matrix, normal, x - hs, y + hs, z - hs, 1, 0, 0, 0, -1, c, overlay, light);
        this.vertex(builder, matrix, normal, x + hs, y + hs, z - hs, 0, 0, 0, 0, -1, c, overlay, light);
        
        // Top
        this.vertex(builder, matrix, normal, x - hs, y + hs, z + hs, 0, 1, 0, 1, 0, c, overlay, light);
        this.vertex(builder, matrix, normal, x + hs, y + hs, z + hs, 1, 1, 0, 1, 0, c, overlay, light);
        this.vertex(builder, matrix, normal, x + hs, y + hs, z - hs, 1, 0, 0, 1, 0, c, overlay, light);
        this.vertex(builder, matrix, normal, x - hs, y + hs, z - hs, 0, 0, 0, 1, 0, c, overlay, light);
        
        // Bottom
        this.vertex(builder, matrix, normal, x - hs, y - hs, z - hs, 0, 1, 0, -1, 0, c, overlay, light);
        this.vertex(builder, matrix, normal, x + hs, y - hs, z - hs, 1, 1, 0, -1, 0, c, overlay, light);
        this.vertex(builder, matrix, normal, x + hs, y - hs, z + hs, 1, 0, 0, -1, 0, c, overlay, light);
        this.vertex(builder, matrix, normal, x - hs, y - hs, z + hs, 0, 0, 0, -1, 0, c, overlay, light);
        
        // Right
        this.vertex(builder, matrix, normal, x + hs, y - hs, z + hs, 0, 1, 1, 0, 0, c, overlay, light);
        this.vertex(builder, matrix, normal, x + hs, y - hs, z - hs, 1, 1, 1, 0, 0, c, overlay, light);
        this.vertex(builder, matrix, normal, x + hs, y + hs, z - hs, 1, 0, 1, 0, 0, c, overlay, light);
        this.vertex(builder, matrix, normal, x + hs, y + hs, z + hs, 0, 0, 1, 0, 0, c, overlay, light);
        
        // Left
        this.vertex(builder, matrix, normal, x - hs, y - hs, z - hs, 0, 1, -1, 0, 0, c, overlay, light);
        this.vertex(builder, matrix, normal, x - hs, y - hs, z + hs, 1, 1, -1, 0, 0, c, overlay, light);
        this.vertex(builder, matrix, normal, x - hs, y + hs, z + hs, 1, 0, -1, 0, 0, c, overlay, light);
        this.vertex(builder, matrix, normal, x - hs, y + hs, z - hs, 0, 0, -1, 0, 0, c, overlay, light);
    }

    private void renderSphereParticle(BufferBuilder builder, Matrix4f matrix, Matrix3f normalMatrix, float x, float y, float z, float size, Color c, int overlay, int light)
    {
        if (this.evaluator != null)
        {
            int color = this.evaluator.computeColor(x, y, z, this.time);
            
            if (color != -1)
            {
                c = new Color().set(color);
            }
        }

        int subdivisions = 4; // Low poly for particles
        float radius = size / 2;
        
        for (int i = 0; i < subdivisions; i++)
        {
            float lat0 = (float) (Math.PI * (-0.5 + (double) i / subdivisions));
            float z0  = (float) Math.sin(lat0);
            float zr0 = (float) Math.cos(lat0);
            
            float lat1 = (float) (Math.PI * (-0.5 + (double) (i + 1) / subdivisions));
            float z1 = (float) Math.sin(lat1);
            float zr1 = (float) Math.cos(lat1);
            
            for (int j = 0; j < subdivisions; j++)
            {
                float lng0 = (float) (2 * Math.PI * (double) j / subdivisions);
                float x0 = (float) Math.cos(lng0);
                float y0 = (float) Math.sin(lng0);
                
                float lng1 = (float) (2 * Math.PI * (double) (j + 1) / subdivisions);
                float x1 = (float) Math.cos(lng1);
                float y1 = (float) Math.sin(lng1);
                
                float u0 = (float) j / subdivisions;
                float u1 = (float) (j + 1) / subdivisions;
                float v0 = (float) i / subdivisions;
                float v1 = (float) (i + 1) / subdivisions;
                
                this.vertex(builder, matrix, normalMatrix, x + x0 * zr0 * radius, y + z0 * radius, z + y0 * zr0 * radius, u0, v0, x0 * zr0, z0, y0 * zr0, c, overlay, light);
                this.vertex(builder, matrix, normalMatrix, x + x0 * zr1 * radius, y + z1 * radius, z + y0 * zr1 * radius, u0, v1, x0 * zr1, z1, y0 * zr1, c, overlay, light);
                this.vertex(builder, matrix, normalMatrix, x + x1 * zr1 * radius, y + z1 * radius, z + y1 * zr1 * radius, u1, v1, x1 * zr1, z1, y1 * zr1, c, overlay, light);
                this.vertex(builder, matrix, normalMatrix, x + x1 * zr0 * radius, y + z0 * radius, z + y1 * zr0 * radius, u1, v0, x1 * zr0, z0, y1 * zr0, c, overlay, light);
            }
        }
    }
    
    private void renderDustParticle(BufferBuilder builder, Matrix4f matrix, Matrix3f normalMatrix, float x, float y, float z, float size, Color c, int overlay, int light)
    {
        if (this.evaluator != null)
        {
            int color = this.evaluator.computeColor(x, y, z, this.time);
            
            if (color != -1)
            {
                c = new Color().set(color);
            }
        }
        
        float hs = size / 2;
        
        double a = (this.randomNoise.noise(x * 3.23, y * 3.23, z * 3.23) * 0.5 + 0.5) * Math.PI * 2.0;
        float rx = (float) Math.cos(a) * hs;
        float rz = (float) Math.sin(a) * hs;
        
        float ux = 0;
        float uy = hs;
        float uz = 0;
        
        float x1 = x - rx - ux;
        float y1 = y - uy;
        float z1 = z - rz - uz;
        
        float x2 = x + rx - ux;
        float y2 = y - uy;
        float z2 = z + rz - uz;
        
        float x3 = x + rx + ux;
        float y3 = y + uy;
        float z3 = z + rz + uz;
        
        float x4 = x - rx + ux;
        float y4 = y + uy;
        float z4 = z - rz + uz;
        
        this.vertex(builder, matrix, normalMatrix, x1, y1, z1, 0, 0, 0, 1, 0, c, overlay, light);
        this.vertex(builder, matrix, normalMatrix, x2, y2, z2, 1, 0, 0, 1, 0, c, overlay, light);
        this.vertex(builder, matrix, normalMatrix, x3, y3, z3, 1, 1, 0, 1, 0, c, overlay, light);
        this.vertex(builder, matrix, normalMatrix, x4, y4, z4, 0, 1, 0, 1, 0, c, overlay, light);
    }

    private void renderBox(BufferBuilder builder, MatrixStack stack, Color c, int overlay, int light)
    {
        Matrix4f matrix = stack.peek().getPositionMatrix();
        Matrix3f normal = stack.peek().getNormalMatrix();
        
        float w = 0.5F;
        float h = 0.5F;
        float d = 0.5F;
        
        // Front
        this.vertex(builder, matrix, normal, -w, -h, d, 0, 1, 0, 0, 1, c, overlay, light);
        this.vertex(builder, matrix, normal, w, -h, d, 1, 1, 0, 0, 1, c, overlay, light);
        this.vertex(builder, matrix, normal, w, h, d, 1, 0, 0, 0, 1, c, overlay, light);
        this.vertex(builder, matrix, normal, -w, h, d, 0, 0, 0, 0, 1, c, overlay, light);
        
        // Back
        this.vertex(builder, matrix, normal, w, -h, -d, 0, 1, 0, 0, -1, c, overlay, light);
        this.vertex(builder, matrix, normal, -w, -h, -d, 1, 1, 0, 0, -1, c, overlay, light);
        this.vertex(builder, matrix, normal, -w, h, -d, 1, 0, 0, 0, -1, c, overlay, light);
        this.vertex(builder, matrix, normal, w, h, -d, 0, 0, 0, 0, -1, c, overlay, light);
        
        // Top
        this.vertex(builder, matrix, normal, -w, h, d, 0, 1, 0, 1, 0, c, overlay, light);
        this.vertex(builder, matrix, normal, w, h, d, 1, 1, 0, 1, 0, c, overlay, light);
        this.vertex(builder, matrix, normal, w, h, -d, 1, 0, 0, 1, 0, c, overlay, light);
        this.vertex(builder, matrix, normal, -w, h, -d, 0, 0, 0, 1, 0, c, overlay, light);
        
        // Bottom
        this.vertex(builder, matrix, normal, -w, -h, -d, 0, 1, 0, -1, 0, c, overlay, light);
        this.vertex(builder, matrix, normal, w, -h, -d, 1, 1, 0, -1, 0, c, overlay, light);
        this.vertex(builder, matrix, normal, w, -h, d, 1, 0, 0, -1, 0, c, overlay, light);
        this.vertex(builder, matrix, normal, -w, -h, d, 0, 0, 0, -1, 0, c, overlay, light);
        
        // Right
        this.vertex(builder, matrix, normal, w, -h, d, 0, 1, 1, 0, 0, c, overlay, light);
        this.vertex(builder, matrix, normal, w, -h, -d, 1, 1, 1, 0, 0, c, overlay, light);
        this.vertex(builder, matrix, normal, w, h, -d, 1, 0, 1, 0, 0, c, overlay, light);
        this.vertex(builder, matrix, normal, w, h, d, 0, 0, 1, 0, 0, c, overlay, light);
        
        // Left
        this.vertex(builder, matrix, normal, -w, -h, -d, 0, 1, -1, 0, 0, c, overlay, light);
        this.vertex(builder, matrix, normal, -w, -h, d, 1, 1, -1, 0, 0, c, overlay, light);
        this.vertex(builder, matrix, normal, -w, h, d, 1, 0, -1, 0, 0, c, overlay, light);
        this.vertex(builder, matrix, normal, -w, h, -d, 0, 0, -1, 0, 0, c, overlay, light);
    }
    
    private void renderSphere(BufferBuilder builder, MatrixStack stack, Color c, int overlay, int light)
    {
        Matrix4f matrix = stack.peek().getPositionMatrix();
        Matrix3f normalMatrix = stack.peek().getNormalMatrix();
        
        int subdivisions = Math.max(this.form.subdivisions.get(), 4);
        float radius = 0.5F;
        
        for (int i = 0; i < subdivisions; i++)
        {
            float lat0 = (float) (Math.PI * (-0.5 + (double) i / subdivisions));
            float z0  = (float) Math.sin(lat0);
            float zr0 = (float) Math.cos(lat0);
            
            float lat1 = (float) (Math.PI * (-0.5 + (double) (i + 1) / subdivisions));
            float z1 = (float) Math.sin(lat1);
            float zr1 = (float) Math.cos(lat1);
            
            for (int j = 0; j < subdivisions; j++)
            {
                float lng0 = (float) (2 * Math.PI * (double) j / subdivisions);
                float x0 = (float) Math.cos(lng0);
                float y0 = (float) Math.sin(lng0);
                
                float lng1 = (float) (2 * Math.PI * (double) (j + 1) / subdivisions);
                float x1 = (float) Math.cos(lng1);
                float y1 = (float) Math.sin(lng1);
                
                float u0 = (float) j / subdivisions;
                float u1 = (float) (j + 1) / subdivisions;
                float v0 = (float) i / subdivisions;
                float v1 = (float) (i + 1) / subdivisions;
                
                this.vertex(builder, matrix, normalMatrix, x0 * zr0 * radius, z0 * radius, y0 * zr0 * radius, u0, v0, x0 * zr0, z0, y0 * zr0, c, overlay, light);
                this.vertex(builder, matrix, normalMatrix, x0 * zr1 * radius, z1 * radius, y0 * zr1 * radius, u0, v1, x0 * zr1, z1, y0 * zr1, c, overlay, light);
                this.vertex(builder, matrix, normalMatrix, x1 * zr1 * radius, z1 * radius, y1 * zr1 * radius, u1, v1, x1 * zr1, z1, y1 * zr1, c, overlay, light);
                this.vertex(builder, matrix, normalMatrix, x1 * zr0 * radius, z0 * radius, y1 * zr0 * radius, u1, v0, x1 * zr0, z0, y1 * zr0, c, overlay, light);
            }
        }
    }
    
    private void renderCylinder(BufferBuilder builder, MatrixStack stack, boolean capsule, Color c, int overlay, int light)
    {
        Matrix4f matrix = stack.peek().getPositionMatrix();
        Matrix3f normalMatrix = stack.peek().getNormalMatrix();
        
        int subdivisions = Math.max(this.form.subdivisions.get(), 4);
        float radius = 0.5F;
        float height = 1.0F;
        float halfHeight = height / 2;
        
        // Body
        for (int i = 0; i < subdivisions; i++)
        {
            float angle0 = (float) (2 * Math.PI * i / subdivisions);
            float x0 = (float) Math.cos(angle0);
            float z0 = (float) Math.sin(angle0);
            
            float angle1 = (float) (2 * Math.PI * (i + 1) / subdivisions);
            float x1 = (float) Math.cos(angle1);
            float z1 = (float) Math.sin(angle1);
            
            float u0 = (float) i / subdivisions;
            float u1 = (float) (i + 1) / subdivisions;
            
            // Side
            this.vertex(builder, matrix, normalMatrix, x0 * radius, -halfHeight, z0 * radius, u0, 1, x0, 0, z0, c, overlay, light);
            this.vertex(builder, matrix, normalMatrix, x0 * radius, halfHeight, z0 * radius, u0, 0, x0, 0, z0, c, overlay, light);
            this.vertex(builder, matrix, normalMatrix, x1 * radius, halfHeight, z1 * radius, u1, 0, x1, 0, z1, c, overlay, light);
            this.vertex(builder, matrix, normalMatrix, x1 * radius, -halfHeight, z1 * radius, u1, 1, x1, 0, z1, c, overlay, light);
        }
        
        if (capsule)
        {
            // Top Hemisphere
            for (int i = subdivisions / 2; i < subdivisions; i++)
            {
                float lat0 = (float) (Math.PI * (-0.5 + (double) i / subdivisions));
                float z0  = (float) Math.sin(lat0);
                float zr0 = (float) Math.cos(lat0);
                
                float lat1 = (float) (Math.PI * (-0.5 + (double) (i + 1) / subdivisions));
                float z1 = (float) Math.sin(lat1);
                float zr1 = (float) Math.cos(lat1);
                
                for (int j = 0; j < subdivisions; j++)
                {
                    float lng0 = (float) (2 * Math.PI * (double) j / subdivisions);
                    float x0 = (float) Math.cos(lng0);
                    float y0 = (float) Math.sin(lng0);
                    
                    float lng1 = (float) (2 * Math.PI * (double) (j + 1) / subdivisions);
                    float x1 = (float) Math.cos(lng1);
                    float y1 = (float) Math.sin(lng1);
                    
                    float u0 = (float) j / subdivisions;
                    float u1 = (float) (j + 1) / subdivisions;
                    float v0 = (float) i / subdivisions;
                    float v1 = (float) (i + 1) / subdivisions;
                    
                    this.vertex(builder, matrix, normalMatrix, x0 * zr0 * radius, z0 * radius + halfHeight, y0 * zr0 * radius, u0, v0, x0 * zr0, z0, y0 * zr0, c, overlay, light);
                    this.vertex(builder, matrix, normalMatrix, x0 * zr1 * radius, z1 * radius + halfHeight, y0 * zr1 * radius, u0, v1, x0 * zr1, z1, y0 * zr1, c, overlay, light);
                    this.vertex(builder, matrix, normalMatrix, x1 * zr1 * radius, z1 * radius + halfHeight, y1 * zr1 * radius, u1, v1, x1 * zr1, z1, y1 * zr1, c, overlay, light);
                    this.vertex(builder, matrix, normalMatrix, x1 * zr0 * radius, z0 * radius + halfHeight, y1 * zr0 * radius, u1, v0, x1 * zr0, z0, y1 * zr0, c, overlay, light);
                }
            }
            
            // Bottom Hemisphere
            for (int i = 0; i < subdivisions / 2; i++)
            {
                float lat0 = (float) (Math.PI * (-0.5 + (double) i / subdivisions));
                float z0  = (float) Math.sin(lat0);
                float zr0 = (float) Math.cos(lat0);
                
                float lat1 = (float) (Math.PI * (-0.5 + (double) (i + 1) / subdivisions));
                float z1 = (float) Math.sin(lat1);
                float zr1 = (float) Math.cos(lat1);
                
                for (int j = 0; j < subdivisions; j++)
                {
                    float lng0 = (float) (2 * Math.PI * (double) j / subdivisions);
                    float x0 = (float) Math.cos(lng0);
                    float y0 = (float) Math.sin(lng0);
                    
                    float lng1 = (float) (2 * Math.PI * (double) (j + 1) / subdivisions);
                    float x1 = (float) Math.cos(lng1);
                    float y1 = (float) Math.sin(lng1);
                    
                    float u0 = (float) j / subdivisions;
                    float u1 = (float) (j + 1) / subdivisions;
                    float v0 = (float) i / subdivisions;
                    float v1 = (float) (i + 1) / subdivisions;
                    
                    this.vertex(builder, matrix, normalMatrix, x0 * zr0 * radius, z0 * radius - halfHeight, y0 * zr0 * radius, u0, v0, x0 * zr0, z0, y0 * zr0, c, overlay, light);
                    this.vertex(builder, matrix, normalMatrix, x0 * zr1 * radius, z1 * radius - halfHeight, y0 * zr1 * radius, u0, v1, x0 * zr1, z1, y0 * zr1, c, overlay, light);
                    this.vertex(builder, matrix, normalMatrix, x1 * zr1 * radius, z1 * radius - halfHeight, y1 * zr1 * radius, u1, v1, x1 * zr1, z1, y1 * zr1, c, overlay, light);
                    this.vertex(builder, matrix, normalMatrix, x1 * zr0 * radius, z0 * radius - halfHeight, y1 * zr0 * radius, u1, v0, x1 * zr0, z0, y1 * zr0, c, overlay, light);
                }
            }
        }
        else
        {
            // Caps
            for (int i = 0; i < subdivisions; i++)
            {
                float angle0 = (float) (2 * Math.PI * i / subdivisions);
                float x0 = (float) Math.cos(angle0);
                float z0 = (float) Math.sin(angle0);
                
                float angle1 = (float) (2 * Math.PI * (i + 1) / subdivisions);
                float x1 = (float) Math.cos(angle1);
                float z1 = (float) Math.sin(angle1);
                
                // Top
                this.vertex(builder, matrix, normalMatrix, x0 * radius, halfHeight, z0 * radius, 1, 0, 0, 1, 0, c, overlay, light);
                this.vertex(builder, matrix, normalMatrix, x1 * radius, halfHeight, z1 * radius, 0, 0, 0, 1, 0, c, overlay, light);
                this.vertex(builder, matrix, normalMatrix, 0, halfHeight, 0, 0.5f, 0.5f, 0, 1, 0, c, overlay, light);
                this.vertex(builder, matrix, normalMatrix, 0, halfHeight, 0, 0.5f, 0.5f, 0, 1, 0, c, overlay, light);
                
                // Bottom
                this.vertex(builder, matrix, normalMatrix, x1 * radius, -halfHeight, z1 * radius, 0, 0, 0, -1, 0, c, overlay, light);
                this.vertex(builder, matrix, normalMatrix, x0 * radius, -halfHeight, z0 * radius, 1, 0, 0, -1, 0, c, overlay, light);
                this.vertex(builder, matrix, normalMatrix, 0, -halfHeight, 0, 0.5f, 0.5f, 0, -1, 0, c, overlay, light);
                this.vertex(builder, matrix, normalMatrix, 0, -halfHeight, 0, 0.5f, 0.5f, 0, -1, 0, c, overlay, light);
            }
        }
    }
    
    private void vertex(BufferBuilder builder, Matrix4f matrix, Matrix3f normalMatrix, float x, float y, float z, float u, float v, float nx, float ny, float nz, Color c, int overlay, int light)
    {
        if (this.evaluator != null && this.overlayVertexMode == OverlayVertexMode.NONE)
        {
            float disp = (float) this.evaluator.compute(x, y, z, this.time);
            int color = this.evaluator.computeColor(x, y, z, this.time);
            
            if (color != -1)
            {
                c = new Color().set(color);
            }
            
            x += nx * disp;
            y += ny * disp;
            z += nz * disp;
        }
        else if (this.evaluator != null)
        {
            float disp = (float) this.evaluator.compute(x, y, z, this.time);

            x += nx * disp;
            y += ny * disp;
            z += nz * disp;
        }

        Vector3f normal = new Vector3f(nx, ny, nz);
        
        normal.mul(normalMatrix);

        if (this.unshadedVertices)
        {
            builder.vertex(matrix, x, y, z)
                   .texture(u, v)
                   .color(c.r, c.g, c.b, c.a);
        }
        else if (this.overlayVertexMode == OverlayVertexMode.PAINT)
        {
            /* Paint RGB/A from verts; spatial mask is applied in flat_paint_overlay. */
            builder.vertex(matrix, x, y, z)
                   .color(c.r, c.g, c.b, c.a)
                   .texture(u, v)
                   .overlay(overlay)
                   .light(light)
                   .normal(normal.x, normal.y, normal.z);
        }
        else if (this.overlayVertexMode == OverlayVertexMode.COLOR_TINT)
        {
            /* Neutral verts — FormColorTint + spatial mask live in flat_color_tint_overlay. */
            builder.vertex(matrix, x, y, z)
                   .color(1F, 1F, 1F, 1F)
                   .texture(u, v)
                   .overlay(overlay)
                   .light(light)
                   .normal(normal.x, normal.y, normal.z);
        }
        else
        {
            builder.vertex(matrix, x, y, z)
                   .color(c.r, c.g, c.b, c.a)
                   .texture(u, v)
                   .overlay(overlay)
                   .light(light)
                   .normal(normal.x, normal.y, normal.z);
        }
    }

    private Color resolveAppearanceColor(Color rawFormColor)
    {
        Color color = rawFormColor.copyBakingColorGrade();

        if (!FormColorEffects.shouldBakeFormColor(rawFormColor))
        {
            color.r = 1F;
            color.g = 1F;
            color.b = 1F;
        }

        PaintSettings paintSettings = this.form.paintSettings.get();
        Color legacyPaint = this.form.paintColor.get();
        float paintStrength = paintSettings.resolveIntensity(legacyPaint);

        if (paintStrength < 0F)
        {
            FormColorEffects.applyPaintBlend(color, paintSettings, legacyPaint);
        }

        GlowSettings glow = this.form.glowSettings.get();
        Color legacyGlow = this.form.glowingColor.get();

        if (glow.resolveIntensity(legacyGlow) < 0F)
        {
            FormColorEffects.blendFormGlowBrighten(color, glow, legacyGlow);
        }

        return color;
    }

    private void submitDeferredShapePaintOverlay(MatrixStack stack, Link texture, ShapeForm.ShapeType type, Color resolvedPaint, float alpha, EffectTransform paintTransform, GlowSettings glowSettings, Color legacyGlow, float glowIntensity)
    {
        Matrix4f positionMatrix = ModelVAORenderer.capturePaintOverlayRootMatrix(new Matrix4f(stack.peek().getPositionMatrix()));
        Matrix3f normalMatrix = new Matrix3f(stack.peek().getNormalMatrix());
        Color paintOverlay = new Color(resolvedPaint.r, resolvedPaint.g, resolvedPaint.b, resolvedPaint.a);

        paintOverlay.a *= alpha;

        ShapeForm.ShapeType typeSnapshot = type;
        Link textureSnapshot = texture;
        EffectTransform paintTransformSnapshot = paintTransform.copy();
        GlowSettings glowSettingsSnapshot = glowSettings;
        Color legacyGlowSnapshot = legacyGlow;
        float glowIntensitySnapshot = glowIntensity;

        ModelVAORenderer.submitPaintOverlay(false, () ->
        {
            MatrixStack overlayStack = new MatrixStack();

            overlayStack.peek().getPositionMatrix().set(positionMatrix);
            overlayStack.peek().getNormalMatrix().set(normalMatrix);

            this.renderShapePaintOverlay(overlayStack, textureSnapshot, typeSnapshot, paintOverlay, OverlayTexture.DEFAULT_UV, paintTransformSnapshot, glowSettingsSnapshot, legacyGlowSnapshot, glowIntensitySnapshot);
        });
    }

    private void renderShapePaintOverlay(MatrixStack stack, Link texture, ShapeForm.ShapeType type, Color paintOverlay, float alpha, EffectTransform paintTransform, GlowSettings glowSettings, Color legacyGlow, float glowIntensity)
    {
        Color paint = new Color(paintOverlay.r, paintOverlay.g, paintOverlay.b, paintOverlay.a);

        paint.a *= alpha;
        this.renderShapePaintOverlay(stack, texture, type, paint, OverlayTexture.DEFAULT_UV, paintTransform, glowSettings, legacyGlow, glowIntensity);
    }

    private void renderShapePaintOverlay(MatrixStack stack, Link texture, ShapeForm.ShapeType type, Color paintOverlay, int overlay, EffectTransform paintTransform, GlowSettings glowSettings, Color legacyGlow, float glowIntensity)
    {
        if (texture != null)
        {
            BBSModClient.getTextures().bindTexture(texture);
        }
        else
        {
            BBSModClient.getTextures().bindTexture(ParticleScheme.DEFAULT_TEXTURE);
        }

        Color paint = new Color(paintOverlay.r, paintOverlay.g, paintOverlay.b, paintOverlay.a);

        this.applyPaintOnlyGlow(paint, glowSettings, legacyGlow, glowIntensity);

        this.overlayVertexMode = OverlayVertexMode.PAINT;
        this.overlayTransform = paintTransform;

        Matrix4f formRootInverse = new Matrix4f(stack.peek().getPositionMatrix()).invert();
        Vector3f maskHalf = new Vector3f();

        EffectTransformMath.resolveBillboardMaskHalfExtents(paintTransform, maskHalf);

        FlatPaintOverlayPass.render(
            FlatPaintOverlayPass.DEFAULT_FACTOR,
            FlatPaintOverlayPass.DEFAULT_UNITS,
            formRootInverse,
            paintTransform,
            false,
            maskHalf,
            () ->
            {
                Texture texObj = this.resolveTexture(texture);
                Tessellator tessellator = Tessellator.getInstance();
                BufferBuilder builder = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
                int paintLight = LightmapTextureManager.MAX_LIGHT_COORDINATE;

                this.buildShapeGeometry(builder, stack, type, paint, overlay, paintLight);
                BillboardRenderLayers.draw(builder.end(), texObj, false, false, false, false);
            }
        );

        this.overlayVertexMode = OverlayVertexMode.NONE;
        this.overlayTransform = null;
    }

    private void submitDeferredShapeColorTintOverlay(MatrixStack stack, Link texture, ShapeForm.ShapeType type, Color formTintColor, int overlay, EffectTransform colorTransform)
    {
        Matrix4f positionMatrix = ModelVAORenderer.capturePaintOverlayRootMatrix(new Matrix4f(stack.peek().getPositionMatrix()));
        Matrix3f normalMatrix = new Matrix3f(stack.peek().getNormalMatrix());
        Color tintSnapshot = new Color(formTintColor.r, formTintColor.g, formTintColor.b, formTintColor.a);

        ShapeForm.ShapeType typeSnapshot = type;
        Link textureSnapshot = texture;
        int overlaySnapshot = overlay;
        EffectTransform colorTransformSnapshot = colorTransform == null ? null : colorTransform.copy();

        ModelVAORenderer.submitColorTintOverlay(() ->
        {
            MatrixStack overlayStack = new MatrixStack();

            overlayStack.peek().getPositionMatrix().set(positionMatrix);
            overlayStack.peek().getNormalMatrix().set(normalMatrix);

            this.renderShapeColorTintOverlay(overlayStack, textureSnapshot, typeSnapshot, tintSnapshot, overlaySnapshot, colorTransformSnapshot);
        });
    }

    private void renderShapeColorTintOverlay(MatrixStack stack, Link texture, ShapeForm.ShapeType type, Color formTintColor, int overlay, EffectTransform colorTransform)
    {
        if (texture != null)
        {
            BBSModClient.getTextures().bindTexture(texture);
        }
        else
        {
            BBSModClient.getTextures().bindTexture(ParticleScheme.DEFAULT_TEXTURE);
        }

        this.overlayVertexMode = OverlayVertexMode.COLOR_TINT;
        this.overlayTransform = colorTransform;

        Matrix4f formRootInverse = new Matrix4f(stack.peek().getPositionMatrix()).invert();
        Vector3f maskHalf = new Vector3f();

        EffectTransformMath.resolveBillboardMaskHalfExtents(colorTransform, maskHalf);

        FlatColorTintOverlayPass.render(
            FlatPaintOverlayPass.DEFAULT_FACTOR,
            FlatPaintOverlayPass.DEFAULT_UNITS,
            formRootInverse,
            colorTransform,
            false,
            maskHalf,
            formTintColor,
            () ->
            {
                Texture texObj = this.resolveTexture(texture);
                Tessellator tessellator = Tessellator.getInstance();
                BufferBuilder builder = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
                int tintLight = LightmapTextureManager.MAX_LIGHT_COORDINATE;

                this.buildShapeGeometry(builder, stack, type, formTintColor, overlay, tintLight);
                BillboardRenderLayers.draw(builder.end(), texObj, false, false, false, false);
            }
        );

        this.overlayVertexMode = OverlayVertexMode.NONE;
        this.overlayTransform = null;
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

    private Texture resolveTexture(Link texture)
    {
        Texture texObj = BBSModClient.getTextures().getTexture(texture != null ? texture : ParticleScheme.DEFAULT_TEXTURE);

        if (texObj == null || !texObj.isValid())
        {
            texObj = BBSModClient.getTextures().getTexture(Link.bbs("textures/block/white.png"));
        }

        return texObj;
    }
}
