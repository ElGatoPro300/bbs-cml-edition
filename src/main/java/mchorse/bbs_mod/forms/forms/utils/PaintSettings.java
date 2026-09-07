package mchorse.bbs_mod.forms.forms.utils;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.colors.Color;

import java.util.Objects;

/**
 * Paint color and intensity settings. Intensity is clamped to [-1, 1]; negative values
 * darken the surface. Legacy paint_color used alpha as opacity.
 */
public class PaintSettings
{
    public static final float MIN_INTENSITY = -1F;
    public static final float MAX_INTENSITY = 1F;
    public static final float SHADER_SHADOW_DEFAULT = 1F;
    /**
     * Legacy Complementary fringe flag. Must stay high enough to pass Iris/Complementary
     * shadow-map alpha tests (~0.1); crushing to ~0.001 removed ground shadows entirely.
     */
    public static final float SHADER_SHADOW_FIX_BUG = SHADER_SHADOW_DEFAULT;
    public static final float SHADER_SHADOW_FIX_BUG_THRESHOLD = 0.01F;
    /* Legacy constant; Opacity 0 no longer forces a faint caster silhouette. */
    public static final float SHADER_SHADOW_ZERO_OPACITY = 0F;
    /**
     * Structure / BE shadow-pass floor. Must stay ≥ Iris alpha tests and never trigger
     * Complementary foliage dither (values like 0.05 cast holey leaf shadows on solids).
     */
    public static final float SHADER_SHADOW_BLOCK_ENTITY = 1F;

    public float r = 1F;
    public float g = 1F;
    public float b = 1F;
    public float intensity;
    public boolean sync = false;
    public float shaderShadow = SHADER_SHADOW_DEFAULT;
    public EffectTransform transform = new EffectTransform();

    public PaintSettings()
    {}

    public PaintSettings copy()
    {
        PaintSettings copy = new PaintSettings();

        copy.r = this.r;
        copy.g = this.g;
        copy.b = this.b;
        copy.intensity = this.intensity;
        copy.sync = this.sync;
        copy.shaderShadow = this.shaderShadow;
        copy.transform = this.transform.copy();

        return copy;
    }

    public static boolean isFixBugShaderShadow(float value)
    {
        return value <= SHADER_SHADOW_FIX_BUG_THRESHOLD;
    }

    public static float fixBugShaderShadow(boolean enabled)
    {
        return enabled ? SHADER_SHADOW_FIX_BUG : SHADER_SHADOW_DEFAULT;
    }

    public static float clampIntensity(float value)
    {
        return Math.max(MIN_INTENSITY, Math.min(MAX_INTENSITY, value));
    }

    /**
     * Maps a legacy paint_color into an intensity value. Non-white RGB with alpha 0
     * used to mean full-strength paint before intensity lived in {@link PaintSettings}.
     */
    public static float resolveLegacyPaintIntensity(Color legacy)
    {
        if (legacy == null)
        {
            return 0F;
        }

        if (legacy.a != 0F)
        {
            return clampIntensity(legacy.a);
        }

        if (legacy.r != 1F || legacy.g != 1F || legacy.b != 1F)
        {
            return 1F;
        }

        return 0F;
    }

    /**
     * Paint / pose paint no longer softens shadow-map alpha (that killed ground shadows under
     * Iris). Casting is controlled by {@code Form.shaderShadow} and form opacity only.
     */
    public static float resolveAutoShaderShadow(float intensity)
    {
        return SHADER_SHADOW_DEFAULT;
    }

    public static float resolveAutoShaderShadowForPoseAlpha(float paintAlpha)
    {
        return SHADER_SHADOW_DEFAULT;
    }

    /**
     * Shadow-pass alpha follows form display opacity (0 = no ground shadow).
     */
    public static float resolveZeroOpacityShaderShadowAlpha(float formAlpha)
    {
        return formAlpha;
    }

    public float effectiveShaderShadow(Color legacy)
    {
        return resolveAutoShaderShadow(this.resolveIntensity(legacy));
    }

    public void applyAutoShaderShadow()
    {
        this.shaderShadow = resolveAutoShaderShadow(this.intensity);
    }

    public void resolveColor(Color fallback, Color out)
    {
        out.set(this.r, this.g, this.b, 1F);

        if (this.intensity != 0F)
        {
            return;
        }

        if (this.r == 1F && this.g == 1F && this.b == 1F && fallback != null)
        {
            if (fallback.r != 1F || fallback.g != 1F || fallback.b != 1F)
            {
                out.set(fallback.r, fallback.g, fallback.b, 1F);
            }
        }
    }

    /**
     * Returns paint intensity. {@link #intensity} is always authoritative (including {@code 0} =
     * paint off). Legacy {@code paint_color} alpha is migrated into {@link #intensity} in
     * {@code Form.fromData} / FormProperties — do not re-interpret it at render time, or white
     * {@code paint_color} with alpha 1 (common after dual-write saves) forces full-strength paint.
     */
    public float resolveIntensity(Color legacy)
    {
        return clampIntensity(this.intensity);
    }

    public boolean resolveSync()
    {
        return this.sync;
    }

    public void fromData(BaseType data)
    {
        if (data instanceof MapType map)
        {
            this.r = map.has("r") ? map.getFloat("r") : 1F;
            this.g = map.has("g") ? map.getFloat("g") : 1F;
            this.b = map.has("b") ? map.getFloat("b") : 1F;
            this.intensity = clampIntensity(map.getFloat("intensity"));
            this.sync = map.getBool("sync", false);
            if (map.has("shaderShadow"))
            {
                if (map.get("shaderShadow").isNumeric())
                {
                    this.shaderShadow = map.getFloat("shaderShadow", 1F);
                }
                else
                {
                    this.shaderShadow = map.getBool("shaderShadow", true) ? 1F : 0F;
                }
            }
            else
            {
                this.shaderShadow = SHADER_SHADOW_DEFAULT;
            }

            if (map.has("transform"))
            {
                this.transform.fromData(map.get("transform"));
            }
        }
    }

    public BaseType toData()
    {
        MapType map = new MapType();

        map.putFloat("r", this.r);
        map.putFloat("g", this.g);
        map.putFloat("b", this.b);
        map.putFloat("intensity", this.intensity);
        map.putBool("sync", this.sync);
        map.putFloat("shaderShadow", this.shaderShadow);
        map.put("transform", this.transform.toData());

        return map;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }

        if (!(o instanceof PaintSettings that))
        {
            return false;
        }

        return Float.compare(this.r, that.r) == 0
            && Float.compare(this.g, that.g) == 0
            && Float.compare(this.b, that.b) == 0
            && Float.compare(this.intensity, that.intensity) == 0
            && this.sync == that.sync
            && Float.compare(this.shaderShadow, that.shaderShadow) == 0
            && Objects.equals(this.transform, that.transform);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(this.r, this.g, this.b, this.intensity, this.sync, this.shaderShadow, this.transform);
    }
}
