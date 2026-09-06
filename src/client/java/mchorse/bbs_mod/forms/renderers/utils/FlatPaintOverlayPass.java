package mchorse.bbs_mod.forms.renderers.utils;

import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.forms.forms.utils.EffectTransform;

import net.minecraft.client.gl.ShaderProgram;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;

import org.lwjgl.opengl.GL11;

/**
 * Second-pass paint for flat textured forms (billboards, trails). Samples the caller-bound
 * texture and tints it toward the paint color; spatial mask is evaluated per fragment.
 */
public class FlatPaintOverlayPass
{

    /**
     * Camera-facing quads have ~0 depth slope, so only {@code units} separates the overlay.
     * Far away, float depth precision needs a larger units bias than near-camera draws.
     * <p>
     * {@code factor} must stay {@code 0} for Iris world paint: OpenGL offset is
     * {@code factor * maxDepthSlope + r * units}. Edge-on / grazing faces have huge slope, so
     * a negative factor pulls paint toward the camera and lets it punch through walls from
     * thin viewing angles. Units alone avoid that while still clearing self z-fight on facing
     * surfaces.
     */
    public static final float POLYGON_OFFSET_FACTOR = 0F;
    public static final float POLYGON_OFFSET_UNITS = -64F;

    /** Default bias — clears the camera-facing base face when close / angled. */
    public static final float DEFAULT_FACTOR = -2F;
    public static final float DEFAULT_UNITS = -4F;
    /**
     * Iris deferred billboard paint (flush after pack composite). Factor stays 0 — same
     * wall punch-through rule as {@link #POLYGON_OFFSET_FACTOR}; units carry the self bias.
     */
    public static final float DEFERRED_BILLBOARD_FACTOR = 0F;
    public static final float DEFERRED_BILLBOARD_UNITS = -64F;


    private FlatPaintOverlayPass()
    {
    }

    public static void render(EffectTransform transform, Runnable draw)
    {
        render(DEFAULT_FACTOR, DEFAULT_UNITS, null, null, false, null, draw);
    }

    public static void render(Runnable draw)
    {
        render(DEFAULT_FACTOR, DEFAULT_UNITS, null, null, false, null, draw);
    }

    public static void render(float factor, float units, Runnable draw)
    {
        render(factor, units, null, null, false, null, draw);
    }

    public static void render(float factor, float units, Matrix4f formRootInverse, EffectTransform transform, boolean bottomAnchored, Vector3f maskHalf, Runnable draw)
    {
        if (draw == null)
        {
            return;
        }

        boolean savedDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean savedPolygonOffsetFill = GL11.glGetBoolean(GL11.GL_POLYGON_OFFSET_FILL);

        if (formRootInverse != null)
        {
            BlockEffectOverlayUniforms.configureFlatPaintOverlay(formRootInverse, transform, bottomAnchored, maskHalf);
        }
        else
        {
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);

            ShaderProgram program = BBSShaders.getFlatPaintOverlayProgram();

            if (program != null)
            {
                RenderSystem.setShader(program);
                /* Inactive mask — full paint strength from vertex alpha. */
                BlockEffectOverlayUniforms.bindFormRootInverse(program, null);
                BlockEffectOverlayUniforms.bindPaintPrecomputed(program, null, bottomAnchored, maskHalf);
            }

            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        }

        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(POLYGON_OFFSET_FACTOR, POLYGON_OFFSET_UNITS);
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
