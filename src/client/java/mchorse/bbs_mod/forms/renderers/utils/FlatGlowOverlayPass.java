package mchorse.bbs_mod.forms.renderers.utils;

import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.forms.forms.utils.EffectTransform;
import mchorse.bbs_mod.forms.forms.utils.GlowSettings;
import mchorse.bbs_mod.forms.forms.utils.PaintSettings;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.iris.FormGlowBloomPatch;

import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.GameRenderer;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;

import org.lwjgl.opengl.GL11;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Photoshop-like Outer Glow for flat textured forms.
 * <p>
 * Intensity = bright layered PTC (always visible). Size/Spread = soft outer halo shells.
 */
public class FlatGlowOverlayPass
{
    private FlatGlowOverlayPass()
    {
    }

    public static void render(GlowSettings glowSettings, Color legacyGlow, float alpha, float glowIntensity, Consumer<Color> drawLayer)
    {
        render(glowSettings, legacyGlow, null, null, null, alpha, glowIntensity, drawLayer);
    }

    public static void render(GlowSettings glowSettings, Color legacyGlow, PaintSettings paint, Color legacyPaint, Color formColor, float alpha, float glowIntensity, Consumer<Color> drawLayer)
    {
        renderSized(glowSettings, legacyGlow, paint, legacyPaint, formColor, alpha, glowIntensity, (layer, expand) -> drawLayer.accept(layer));
    }

    /**
     * @param drawLayer receives glow tint and expand (quad scale delta from Size).
     *                  Callers must remap UVs with {@link #remapUvForOuterGlow} when expand != 0.
     */
    public static void renderSized(GlowSettings glowSettings, Color legacyGlow, PaintSettings paint, Color legacyPaint, Color formColor, float alpha, float glowIntensity, BiConsumer<Color, Float> drawLayer)
    {
        if (glowIntensity <= 0F || drawLayer == null)
        {
            return;
        }

        float size = glowSettings == null ? 0F : glowSettings.resolveSize();
        float spread = glowSettings == null ? 0F : glowSettings.resolveSpread();

        FormGlowBloomPatch.setFromGlow(glowSettings, legacyGlow);

        boolean savedDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean savedPolygonOffsetFill = GL11.glGetBoolean(GL11.GL_POLYGON_OFFSET_FILL);

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(-2F, -2F);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);

        try
        {
            /* Intensity MUST use PTC layers — soft-only path was invisible under HDR / full-bleed sprites. */
            RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);

            int intensityLayers = Math.min(6, Math.max(1, FormColorEffects.resolveGlowOverlayLayers(glowIntensity)));
            int bundles = Math.min(3, Math.max(1, FormColorEffects.resolveGlowOverlayBundles(glowIntensity)));

            for (int bundle = 0; bundle < bundles; bundle++)
            {
                for (int i = 0; i < intensityLayers; i++)
                {
                    Color layer = FormColorEffects.resolveGlowOverlayColor(glowSettings, legacyGlow, paint, legacyPaint, formColor, alpha, glowIntensity, intensityLayers);

                    drawLayer.accept(layer, 0F);
                }
            }

            int sizeShells = FormColorEffects.resolveGlowSizeShells(size, spread);

            if (sizeShells <= 0)
            {
                return;
            }

            ShaderProgram softProgram = BBSShaders.getFlatGlowOverlayProgram();

            if (softProgram != null)
            {
                RenderSystem.setShader(() -> softProgram);

                for (int i = 0; i < sizeShells; i++)
                {
                    float expand = FormColorEffects.resolveGlowShellExpand(size, spread, i, sizeShells);
                    float fade = FormColorEffects.resolveGlowShellFade(spread, i, sizeShells);
                    float shellSize = Math.max(0.5F, size * (1F - (i + 1F) / (sizeShells + 1F) * 0.35F));
                    Color tint = FormColorEffects.resolveGlowOverlayEmissionColor(glowSettings, legacyGlow, paint, legacyPaint, formColor, alpha, glowIntensity);
                    Color layer = tint.copy();

                    layer.r = Math.min(1F, layer.r * Math.max(0.45F, fade));
                    layer.g = Math.min(1F, layer.g * Math.max(0.45F, fade));
                    layer.b = Math.min(1F, layer.b * Math.max(0.45F, fade));
                    layer.a = Math.max(0.3F, alpha * fade);
                    bindGlowUniforms(softProgram, glowIntensity * Math.max(0.4F, fade), shellSize, spread);
                    drawLayer.accept(layer, expand);
                }
            }
            else
            {
                for (int i = 0; i < sizeShells; i++)
                {
                    float expand = FormColorEffects.resolveGlowShellExpand(size, spread, i, sizeShells);
                    float fade = FormColorEffects.resolveGlowShellFade(spread, i, sizeShells);
                    Color layer = FormColorEffects.resolveGlowOverlayColor(glowSettings, legacyGlow, paint, legacyPaint, formColor, alpha, glowIntensity, Math.max(1, sizeShells));

                    layer.r *= fade;
                    layer.g *= fade;
                    layer.b *= fade;
                    layer.a *= Math.max(0.3F, fade);
                    drawLayer.accept(layer, expand);
                }
            }
        }
        finally
        {
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
            GL11.glPolygonOffset(0F, 0F);

            if (!savedPolygonOffsetFill)
            {
                GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
            }

            RenderSystem.depthMask(savedDepthMask);
            RenderSystem.defaultBlendFunc();
        }
    }

    /**
     * Map UV on an expanded Outer Glow quad so the texture stays world-sized in the center
     * and padded UV (outside 0..1 after remap) becomes empty for true outside glow.
     */
    public static float remapUvForOuterGlow(float uv, float expand)
    {
        if (Math.abs(expand) <= 0.0001F)
        {
            return uv;
        }

        float scale = Math.max(0.05F, 1F + expand);

        return 0.5F + (uv - 0.5F) * scale;
    }

    private static void bindGlowUniforms(ShaderProgram program, float intensity, float size, float spread)
    {
        if (program == null)
        {
            return;
        }

        GlUniform intensityUniform = program.getUniform("GlowIntensity");
        GlUniform sizeUniform = program.getUniform("GlowSize");
        GlUniform spreadUniform = program.getUniform("GlowSpread");

        if (intensityUniform != null)
        {
            intensityUniform.set(intensity);
        }

        if (sizeUniform != null)
        {
            sizeUniform.set(size);
        }

        if (spreadUniform != null)
        {
            spreadUniform.set(spread);
        }
    }

    public static void renderMasked(float factor, float units, Matrix4f formRootInverse, EffectTransform transform, boolean bottomAnchored, Vector3f maskHalf, float glowScale, Runnable draw)
    {
        if (draw == null)
        {
            return;
        }

        boolean savedDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean savedPolygonOffsetFill = GL11.glGetBoolean(GL11.GL_POLYGON_OFFSET_FILL);

        BlockEffectOverlayUniforms.configureFlatGlowOverlay(formRootInverse, transform, bottomAnchored, maskHalf, glowScale);

        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(FlatPaintOverlayPass.POLYGON_OFFSET_FACTOR, FlatPaintOverlayPass.POLYGON_OFFSET_UNITS);
        GL11.glPolygonOffset(factor, units);

        try
        {
            draw.run();
        }
        finally
        {
            GL11.glPolygonOffset(0F, 0F);

            if (!savedPolygonOffsetFill)
            {
                GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
            }

            RenderSystem.depthMask(savedDepthMask);
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
            RenderSystem.defaultBlendFunc();
        }
    }
}
