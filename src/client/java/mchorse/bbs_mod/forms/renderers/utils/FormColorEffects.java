package mchorse.bbs_mod.forms.renderers.utils;

import mchorse.bbs_mod.forms.forms.utils.EffectTransform;
import mchorse.bbs_mod.forms.forms.utils.GlowSettings;
import mchorse.bbs_mod.forms.forms.utils.PaintSettings;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;

public class FormColorEffects
{
    public static final float EMISSION_STRENGTH = 1F;
    public static final float OVERLAY_GLOW_BOOST = EMISSION_STRENGTH;

    /**
     * Shadow-pass alpha follows form opacity (0 = no ground shadow). Kept for call-site
     * compatibility; no longer boosts zero opacity to a faint silhouette.
     */
    public static void finishShadowOpacity(Color color, boolean shadowPass)
    {
        /* no-op: traditional color.a / applyFormOpacity already own caster alpha */
    }

    /**
     * Shadow-map alpha scale for Paint / form color tint / Color Grade.
     * <p>
     * Previously returned {@code 0.001} whenever any Color-track intensity was non-zero so
     * Complementary would drop a cursor-side fringe. Iris shadow programs still alpha-test
     * around ~0.1, so that crush also deleted the actor's ground shadow. Casting now stays
     * at full shadow-pass alpha; opacity 0 still skips via callers / {@code applyFormOpacity}.
     */
    public static float resolveEffectShaderShadow(Color storedFormColor, PaintSettings paintSettings, Color legacyPaint)
    {
        return resolveEffectShaderShadow(storedFormColor, paintSettings, legacyPaint, false);
    }

    /**
     * @param anyPaintActive true when form-level or per-bone paint is on (Model forms).
     */
    public static float resolveEffectShaderShadow(Color storedFormColor, PaintSettings paintSettings, Color legacyPaint, boolean anyPaintActive)
    {
        return PaintSettings.SHADER_SHADOW_DEFAULT;
    }

    /**
     * Kept for call-site compatibility. No longer multiplies shadow-pass alpha for Color-track
     * effects (see {@link #resolveEffectShaderShadow}).
     */
    public static void applyShadowPassColorFix(Color color, Color storedFormColor, PaintSettings paintSettings, Color legacyPaint, boolean shadowPass)
    {
        applyShadowPassColorFix(color, storedFormColor, paintSettings, legacyPaint, shadowPass, false);
    }

    public static void applyShadowPassColorFix(Color color, Color storedFormColor, PaintSettings paintSettings, Color legacyPaint, boolean shadowPass, boolean anyPaintActive)
    {
        /* no-op: crushing alpha for the Complementary fringe removed ground shadows under Iris */
    }

    public enum BlendMode
    {
        MULTIPLY,
        BRIGHTEN
    }

    public static void blend(Color base, Color overlay, boolean additive)
    {
        blend(base, overlay, additive ? BlendMode.BRIGHTEN : BlendMode.MULTIPLY);
    }

    public static void blendFormGlowBrighten(Color base, GlowSettings glow, Color fallback)
    {
        if (base == null || glow == null)
        {
            return;
        }

        float intensity = glow.resolveIntensity(fallback);

        if (intensity == 0F)
        {
            return;
        }

        Color resolved = new Color();

        glow.resolveColor(fallback, resolved);
        blendEmission(base, resolved, intensity);
    }

    public static void blendBrighten(Color base, Color glowColor, float intensity)
    {
        blendEmission(base, glowColor, intensity);
    }

    public static void blendEmission(Color base, Color glowColor, float intensity)
    {
        if (base == null || glowColor == null || intensity == 0F)
        {
            return;
        }

        float r = MathUtils.clamp(glowColor.r, 0F, 1F);
        float g = MathUtils.clamp(glowColor.g, 0F, 1F);
        float b = MathUtils.clamp(glowColor.b, 0F, 1F);

        if (intensity > 0F)
        {
            base.r += r * intensity * EMISSION_STRENGTH;
            base.g += g * intensity * EMISSION_STRENGTH;
            base.b += b * intensity * EMISSION_STRENGTH;
        }
        else
        {
            /* Linear darken: 0 = unchanged, -1 = fully black (smooth for keyframe animation). */
            float factor = Math.max(0F, 1F + intensity);

            base.r *= factor;
            base.g *= factor;
            base.b *= factor;
        }
    }

    public static boolean hasPositiveGlow(GlowSettings glow, Color legacyGlow)
    {
        return glow.resolveIntensity(legacyGlow) > 0F;
    }

    public static boolean hasPositivePaint(PaintSettings paintSettings, Color legacyPaint)
    {
        return paintSettings.resolveIntensity(legacyPaint) > 0F;
    }

    /**
     * True when Color should use a spatial mask / FormColorTint overlay instead of baking
     * into vertex color — same rules as ModelFormRenderer.canApplyColorTransformMask, without
     * requiring a ModelInstance.
     * <p>
     * Only an active color transform forces this path. Plain RGB tint bakes into the form
     * geometry (legacy behavior). Color Grade alone does not force this; see
     * {@link #wantsColorTintForAdjustments} / {@link #wantsColorTintOverlay}.
     * Alpha is traditional opacity and is ignored here.
     */
    public static boolean wantsColorTransformMask(Color color)
    {
        return color != null && (color.hasActiveTransform() || color.hasActiveGradeTransform());
    }

    /**
     * Whether Iris / BBS FormColorTint overlay should run for brightness-only grading when the
     * live Iris pass cannot run FormColorGrade (no BBS model.fsh).
     */
    public static boolean wantsColorTintForAdjustments(Color color, boolean shaderGradeActive)
    {
        return color != null && color.hasColorAdjustments() && !shaderGradeActive;
    }

    /**
     * Block / item / structure / billboard / shape: use FormColorTint overlay for color
     * spatial masks <b>or</b> Color Grade. Plain RGB without a transform bakes into vertices
     * ({@link #shouldBakeFormColor}).
     */
    public static boolean wantsColorTintOverlay(Color color)
    {
        return wantsColorTransformMask(color) || wantsColorTintForAdjustments(color, false);
    }

    /**
     * Bake form color into vertex tint when no FormColorTint overlay will run
     * (no active color transform and no Color Grade overlay).
     */
    public static boolean shouldBakeFormColor(Color color)
    {
        return !wantsColorTintOverlay(color);
    }

    /**
     * Block-entity renderers (beds, chests, …) are incompatible with
     * {@code block_color_tint_overlay} / {@code block_paint_overlay}: those force the block
     * atlas and (for Color Grade) regrade framebuffer pixels that often sample the UI.
     * Bake form color, Color Grade, and uniform paint into the tint instead (Iris reapplies
     * this tint after composite via a deferred BE redraw).
     */
    public static Color resolveBlockEntityTint(Color storedFormColor, PaintSettings paintSettings, Color legacyPaint)
    {
        Color tint = storedFormColor == null ? Color.white() : storedFormColor.copyBakingColorGrade();

        if (paintSettings != null && paintSettings.resolveIntensity(legacyPaint) != 0F)
        {
            applyPaintBlend(tint, paintSettings, legacyPaint);
        }

        return tint;
    }

    public static Color resolvePaintColor(PaintSettings paintSettings, Color legacyPaint)
    {
        Color resolvedPaint = new Color();

        paintSettings.resolveColor(legacyPaint, resolvedPaint);
        resolvedPaint.a = paintSettings.resolveIntensity(legacyPaint);

        return resolvedPaint;
    }

    public static void applyPaintBlend(Color base, Color paintRgb, float paintStrength)
    {
        if (base == null || paintRgb == null || paintStrength == 0F)
        {
            return;
        }

        if (paintStrength >= 1F)
        {
            base.r = paintRgb.r;
            base.g = paintRgb.g;
            base.b = paintRgb.b;
        }
        else if (paintStrength > 0F)
        {
            base.r = base.r + (paintRgb.r - base.r) * paintStrength;
            base.g = base.g + (paintRgb.g - base.g) * paintStrength;
            base.b = base.b + (paintRgb.b - base.b) * paintStrength;
        }
        else
        {
            float factor = Math.max(0F, 1F + paintStrength);

            base.r *= factor;
            base.g *= factor;
            base.b *= factor;
        }
    }

    public static void applyPaintBlend(Color base, PaintSettings paintSettings, Color legacyPaint)
    {
        Color paint = new Color();

        paintSettings.resolveColor(legacyPaint, paint);
        applyPaintBlend(base, paint, paintSettings.resolveIntensity(legacyPaint));
    }

    public static void applyPaintBlendToBytes(int[] rgb, Color paintColor)
    {
        if (paintColor == null || rgb == null || rgb.length < 3 || Math.abs(paintColor.a) == 0F)
        {
            return;
        }

        Color vertex = new Color(rgb[0] / 255F, rgb[1] / 255F, rgb[2] / 255F, 1F);

        applyPaintBlend(vertex, paintColor, paintColor.a);
        rgb[0] = MathUtils.clamp((int) (vertex.r * 255F), 0, 255);
        rgb[1] = MathUtils.clamp((int) (vertex.g * 255F), 0, 255);
        rgb[2] = MathUtils.clamp((int) (vertex.b * 255F), 0, 255);
    }

    public static int resolveGlowOverlayLayers(float intensity)
    {
        if (intensity <= 0F)
        {
            return 0;
        }

        float total = intensity * OVERLAY_GLOW_BOOST;

        return Math.max(1, (int) Math.ceil(total));
    }

    public static Color resolveGlowOverlayColor(GlowSettings glow, Color legacyGlow, float alpha, float intensity, int layers)
    {
        Color resolved = new Color();
        Color color = new Color();
        float layerStrength = MathUtils.clamp(intensity * OVERLAY_GLOW_BOOST / layers, 0F, 1F);

        glow.resolveColor(legacyGlow, resolved);
        color.r = MathUtils.clamp(resolved.r * layerStrength, 0F, 1F);
        color.g = MathUtils.clamp(resolved.g * layerStrength, 0F, 1F);
        color.b = MathUtils.clamp(resolved.b * layerStrength, 0F, 1F);
        color.a = alpha;

        return color;
    }

    /**
     * Full-strength glow tint for a single additive overlay pass. Pair with
     * {@link #resolveGlowOverlayShaderScale(float)} so total emission matches the legacy multi-layer path.
     */
    public static Color resolveGlowOverlayEmissionColor(GlowSettings glow, Color legacyGlow, float alpha, float intensity)
    {
        Color resolved = new Color();
        Color color = new Color();

        glow.resolveColor(legacyGlow, resolved);
        color.set(resolved.r, resolved.g, resolved.b, alpha);

        return color;
    }

    public static float resolveGlowOverlayShaderScale(float intensity)
    {
        if (intensity <= 0F)
        {
            return 0F;
        }

        return intensity * OVERLAY_GLOW_BOOST;
    }

    public static EffectTransform resolveGlowEffectTransform(GlowSettings glow, Color legacyGlow)
    {
        if (glow != null && glow.transform != null && glow.transform.isActive())
        {
            return glow.transform;
        }

        if (legacyGlow != null && legacyGlow.hasActiveTransform())
        {
            return legacyGlow.transform;
        }

        if (glow != null && glow.transform != null)
        {
            return glow.transform;
        }

        if (legacyGlow != null && legacyGlow.transform != null)
        {
            return legacyGlow.transform;
        }

        return new EffectTransform();
    }

    public static void blend(Color base, Color overlay, BlendMode mode)
    {
        if (base == null || overlay == null)
        {
            return;
        }

        float a = MathUtils.clamp(overlay.a, 0F, 1F);
        float r = MathUtils.clamp(overlay.r, 0F, 1F);
        float g = MathUtils.clamp(overlay.g, 0F, 1F);
        float b = MathUtils.clamp(overlay.b, 0F, 1F);

        if (mode == BlendMode.BRIGHTEN)
        {
            blendBrighten(base, overlay, a);
        }
        else
        {
            base.r *= r;
            base.g *= g;
            base.b *= b;
            base.a *= a;
        }
    }
}
