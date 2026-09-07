package mchorse.bbs_mod.utils.colors;

import mchorse.bbs_mod.forms.forms.utils.EffectTransform;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.interps.Lerps;

import java.util.Objects;

public class Color
{
    public float r;
    public float g;
    public float b;
    public float a = 1F;
    public float brightness;
    public float contrast;
    public float hue;
    public float saturation;
    /** Spatial mask for form color tint (RGB). */
    public EffectTransform transform = new EffectTransform();
    /** Spatial mask for brightness grade only. */
    public EffectTransform brightnessTransform = new EffectTransform();
    /** Spatial mask for contrast grade only. */
    public EffectTransform contrastTransform = new EffectTransform();
    /** Spatial mask for hue grade only. */
    public EffectTransform hueTransform = new EffectTransform();
    /** Spatial mask for saturation grade only. */
    public EffectTransform saturationTransform = new EffectTransform();

    public static Color rgb(int rgb)
    {
        return new Color().set(rgb, false);
    }

    public static Color rgba(int rgba)
    {
        return new Color().set(rgba);
    }

    public static Color white()
    {
        return new Color(1F, 1F, 1F, 1F);
    }

    public Color()
    {}

    public Color(float r, float g, float b)
    {
        this.r = r;
        this.g = g;
        this.b = b;
    }

    public Color(float r, float g, float b, float a)
    {
        this(r, g, b);

        this.a = a;
    }

    public Color set(float r, float g, float b)
    {
        return this.set(r, g, b, 1);
    }

    public Color set(float r, float g, float b, float a)
    {
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;

        return this;
    }

    public Color set(float value, int component)
    {
        switch (component)
        {
            case 1:
                this.r = value;
            break;

            case 2:
                this.g = value;
            break;

            case 3:
                this.b = value;
            break;

            default:
                this.a = value;
            break;
        }

        return this;
    }

    public Color set(int color)
    {
        return this.set(color, true);
    }

    public Color set(int color, boolean alpha)
    {
        float r = (color >> 16 & 0xff) / 255F;
        float g = (color >> 8 & 0xff) / 255F;
        float b = (color & 0xff) / 255F;
        float a = alpha ? (color >> 24 & 0xff) / 255F : 1F;

        this.set(r, g, b, a);

        return this;
    }

    public void lerp(Color color, float factor)
    {
        this.r = Lerps.lerp(this.r, color.r, factor);
        this.g = Lerps.lerp(this.g, color.g, factor);
        this.b = Lerps.lerp(this.b, color.b, factor);
        this.a = Lerps.lerp(this.a, color.a, factor);
        this.brightness = Lerps.lerp(this.brightness, color.brightness, factor);
        this.contrast = Lerps.lerp(this.contrast, color.contrast, factor);
        this.hue = Lerps.lerp(this.hue, color.hue, factor);
        this.saturation = Lerps.lerp(this.saturation, color.saturation, factor);

        if (factor > 0F && color.hasActiveTransform())
        {
            this.transform = copyTransform(color.transform);
        }

        if (factor > 0F && (color.hasColorAdjustments() || color.hasActiveGradeTransform()))
        {
            this.brightnessTransform = copyTransform(color.brightnessTransform);
            this.contrastTransform = copyTransform(color.contrastTransform);
            this.hueTransform = copyTransform(color.hueTransform);
            this.saturationTransform = copyTransform(color.saturationTransform);
        }
    }

    public Color copy()
    {
        return new Color().copy(this);
    }

    public Color copy(Color color)
    {
        this.set(color.r, color.g, color.b, color.a);
        this.brightness = color.brightness;
        this.contrast = color.contrast;
        this.hue = color.hue;
        this.saturation = color.saturation;
        this.transform = copyTransform(color.transform);
        this.brightnessTransform = copyTransform(color.brightnessTransform);
        this.contrastTransform = copyTransform(color.contrastTransform);
        this.hueTransform = copyTransform(color.hueTransform);
        this.saturationTransform = copyTransform(color.saturationTransform);

        return this;
    }

    private static EffectTransform copyTransform(EffectTransform transform)
    {
        return transform == null ? new EffectTransform() : transform.copy();
    }

    /**
     * Copy for rendering when Color Grade is applied in-shader (FormColorGrade).
     * Keeps {@link #a} as opacity; clears baked grade fields so the shader owns them.
     */
    public Color copyDeferringColorGrade()
    {
        Color copy = this.copy();

        copy.brightness = 0F;
        copy.contrast = 0F;
        copy.hue = 0F;
        copy.saturation = 0F;

        return copy;
    }

    /**
     * Copy for rendering with brightness/contrast/hue/saturation baked into RGB.
     * Keeps {@link #a} as traditional opacity (does not treat alpha as tint intensity).
     */
    public Color copyBakingColorGrade()
    {
        Color copy = this.copy();
        float brightness = copy.brightness;
        float contrast = copy.contrast;
        float hue = copy.hue;
        float saturation = copy.saturation;
        boolean graded = ColorAdjustments.isActive(brightness, contrast, hue, saturation);

        copy.brightness = 0F;
        copy.contrast = 0F;
        copy.hue = 0F;
        copy.saturation = 0F;

        if (graded)
        {
            ColorAdjustments.apply(copy, brightness, contrast, hue, saturation);
        }

        return copy;
    }

    public boolean hasActiveTransform()
    {
        return this.transform != null && this.transform.isActive();
    }

    public boolean hasActiveGradeTransform()
    {
        return isTransformActive(this.brightnessTransform)
            || isTransformActive(this.contrastTransform)
            || isTransformActive(this.hueTransform)
            || isTransformActive(this.saturationTransform);
    }

    private static boolean isTransformActive(EffectTransform transform)
    {
        return transform != null && transform.isActive();
    }

    public boolean hasColorAdjustments()
    {
        return ColorAdjustments.isActive(this.brightness, this.contrast, this.hue, this.saturation);
    }

    public boolean needsMapSerialization()
    {
        return this.hasActiveTransform() || this.hasColorAdjustments() || this.hasActiveGradeTransform();
    }

    public int getARGBColor()
    {
        float r = MathUtils.clamp(this.r, 0, 1);
        float g = MathUtils.clamp(this.g, 0, 1);
        float b = MathUtils.clamp(this.b, 0, 1);
        float a = MathUtils.clamp(this.a, 0, 1);

        return ((int) (a * 255) << 24) | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    public int getRGBAColor()
    {
        return this.getRGBColor() << 8 + ((int) (this.a * 255) & 0xff);
    }

    public int getRGBColor()
    {
        return this.getARGBColor() & Colors.RGB;
    }

    public String stringify()
    {
        return this.stringify(false);
    }

    public String stringify(boolean alpha)
    {
        if (alpha)
        {
            return "#" + StringUtils.leftPad(Integer.toHexString(this.getARGBColor()), 8, "0");
        }

        return "#" + StringUtils.leftPad(Integer.toHexString(this.getRGBColor()), 6, "0");
    }

    public void mul(int color)
    {
        Color newColor = new Color().set(color, true);

        this.r *= newColor.r;
        this.g *= newColor.g;
        this.b *= newColor.b;
        this.a *= newColor.a;
    }

    public void mul(Color set)
    {
        this.r *= set.r;
        this.g *= set.g;
        this.b *= set.b;
        this.a *= set.a;

        if (set.hasActiveTransform())
        {
            this.transform = set.transform.copy();
        }

        if (set.hasColorAdjustments() || set.hasActiveGradeTransform())
        {
            this.brightness = set.brightness;
            this.contrast = set.contrast;
            this.hue = set.hue;
            this.saturation = set.saturation;
            this.brightnessTransform = copyTransform(set.brightnessTransform);
            this.contrastTransform = copyTransform(set.contrastTransform);
            this.hueTransform = copyTransform(set.hueTransform);
            this.saturationTransform = copyTransform(set.saturationTransform);
        }
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof Color)
        {
            Color color = (Color) obj;

            return color.getARGBColor() == this.getARGBColor()
                && Float.compare(this.brightness, color.brightness) == 0
                && Float.compare(this.contrast, color.contrast) == 0
                && Float.compare(this.hue, color.hue) == 0
                && Float.compare(this.saturation, color.saturation) == 0
                && Objects.equals(this.transform, color.transform)
                && Objects.equals(this.brightnessTransform, color.brightnessTransform)
                && Objects.equals(this.contrastTransform, color.contrastTransform)
                && Objects.equals(this.hueTransform, color.hueTransform)
                && Objects.equals(this.saturationTransform, color.saturationTransform);
        }

        return super.equals(obj);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(this.getARGBColor(), this.brightness, this.contrast, this.hue, this.saturation, this.transform, this.brightnessTransform, this.contrastTransform, this.hueTransform, this.saturationTransform);
    }

    @Override
    public String toString()
    {
        int r = (int) (MathUtils.clamp(this.r, 0F, 1F) * 255F);
        int g = (int) (MathUtils.clamp(this.g, 0F, 1F) * 255F);
        int b = (int) (MathUtils.clamp(this.b, 0F, 1F) * 255F);
        int a = (int) (MathUtils.clamp(this.a, 0F, 1F) * 255F);

        return "rgba(" + r + ", " + g + ", " + b + ", " + a + ")";
    }
}