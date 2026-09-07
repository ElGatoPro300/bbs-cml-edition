package mchorse.bbs_mod.cubic.render;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.forms.forms.utils.GlowSettings;
import mchorse.bbs_mod.forms.renderers.utils.FlatGlowOverlayPass;
import mchorse.bbs_mod.utils.colors.Color;

import java.util.function.Consumer;

/**
 * Shape-key / OBJ CPU models use the block-item additive glow overlay (layered emissive
 * vertex colors) instead of the BBS model shader GlowingColor uniform.
 */
public final class ShapeKeyGlowPass
{
    private ShapeKeyGlowPass()
    {}

    public static boolean hasShapeKeyGlow(ModelInstance model, GlowSettings glow, Color legacyGlow)
    {
        return model != null && model.hasShapeKeys() && glow != null && glow.resolveIntensity(legacyGlow) > 0F;
    }

    public static boolean shouldUseGlowOverlay(ModelInstance model, boolean hasPositiveGlow, boolean glowDeferredToOverlay)
    {
        return false;
    }

    public static void renderOverlay(GlowSettings glowSettings, Color legacyGlow, float alpha, float glowIntensity, Consumer<Color> drawLayer)
    {
        FlatGlowOverlayPass.render(glowSettings, legacyGlow, alpha, glowIntensity, drawLayer);
    }
}
