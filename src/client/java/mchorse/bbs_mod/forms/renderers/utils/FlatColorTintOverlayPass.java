package mchorse.bbs_mod.forms.renderers.utils;

import mchorse.bbs_mod.forms.forms.utils.EffectTransform;
import mchorse.bbs_mod.utils.colors.Color;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;

import org.lwjgl.opengl.GL11;

/**
 * Multiply-blend color-mask overlay for flat textured forms (billboards, shapes).
 * Mask is evaluated per fragment in {@code flat_color_tint_overlay} (billboards only have
 * four corners, so vertex-baked masks cannot form a spatial strip).
 */
public final class FlatColorTintOverlayPass
{
    private FlatColorTintOverlayPass()
    {}

    public static void render(Runnable draw)
    {
        render(FlatPaintOverlayPass.DEFAULT_FACTOR, FlatPaintOverlayPass.DEFAULT_UNITS, null, null, false, null, null, draw);
    }

    public static void render(float factor, float units, Runnable draw)
    {
        render(factor, units, null, null, false, null, null, draw);
    }

    public static void render(float factor, float units, Matrix4f formRootInverse, EffectTransform transform, boolean bottomAnchored, Vector3f maskHalf, Color formColor, Runnable draw)
    {
        if (draw == null)
        {
            return;
        }

        boolean savedDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean savedPolygonOffsetFill = GL11.glGetBoolean(GL11.GL_POLYGON_OFFSET_FILL);

        if (formRootInverse != null)
        {
            BlockEffectOverlayUniforms.configureFlatColorTintOverlay(formRootInverse, transform, bottomAnchored, maskHalf, formColor);
        }
        else
        {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        }

        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
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
