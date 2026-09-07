package mchorse.bbs_mod.utils.keyframes.factories;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.IntType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.utils.EffectTransform;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.ColorAdjustments;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

public class ColorKeyframeFactory implements IKeyframeFactory<Color>
{
    /**
     * Legacy dual-write field: tint strength lived here while ARGB alpha held opacity.
     * Still read once for migration into traditional {@code color.a}; no longer written.
     */
    public static final String BLEND_A = "blend_a";

    private Color i = new Color();

    @Override
    public Color fromData(BaseType data)
    {
        if (data instanceof IntType)
        {
            return Color.rgba(data.asNumeric().intValue());
        }

        if (data instanceof MapType map)
        {
            Color color = Color.rgba(map.getInt("color"));

            /* Dual-write era: ARGB alpha = opacity, blend_a = tint intensity. Bake intensity
             * into RGB and keep ARGB alpha as traditional opacity. */
            if (map.has(BLEND_A))
            {
                float intensity = MathUtils.clamp(map.getFloat(BLEND_A), 0F, 1F);
                float opacityA = MathUtils.clamp(color.a, 0F, 1F);

                color.r = Lerps.lerp(1F, color.r, intensity);
                color.g = Lerps.lerp(1F, color.g, intensity);
                color.b = Lerps.lerp(1F, color.b, intensity);
                color.a = opacityA <= 0.001F ? 1F : opacityA;
            }

            if (map.has("transform"))
            {
                color.transform.fromData(map.get("transform"));
            }

            color.brightness = ColorAdjustments.clampBrightness(map.getFloat("brightness", 0F));
            color.contrast = ColorAdjustments.clampContrast(map.getFloat("contrast", 0F));
            color.hue = ColorAdjustments.clampHue(map.getFloat("hue", 0F));
            color.saturation = ColorAdjustments.clampSaturation(map.getFloat("saturation", 0F));

            this.readTransform(map, "brightnessTransform", color.brightnessTransform);
            this.readTransform(map, "contrastTransform", color.contrastTransform);
            this.readTransform(map, "hueTransform", color.hueTransform);
            this.readTransform(map, "saturationTransform", color.saturationTransform);

            /* Legacy: one Color Transform masked both blend and grade. */
            boolean hasGradeTransformData = map.has("brightnessTransform")
                || map.has("contrastTransform")
                || map.has("hueTransform")
                || map.has("saturationTransform");

            if (!hasGradeTransformData && color.hasColorAdjustments() && color.hasActiveTransform())
            {
                color.brightnessTransform = color.transform.copy();
                color.contrastTransform = color.transform.copy();
                color.hueTransform = color.transform.copy();
                color.saturationTransform = color.transform.copy();
            }

            return color;
        }

        return new Color();
    }

    @Override
    public BaseType toData(Color value)
    {
        if (value.needsMapSerialization())
        {
            MapType map = new MapType();

            map.putInt("color", value.getARGBColor());

            if (value.hasActiveTransform())
            {
                map.put("transform", value.transform.toData());
            }

            if (Math.abs(value.brightness) > ColorAdjustments.EPSILON)
            {
                map.putFloat("brightness", value.brightness);
            }

            if (Math.abs(value.contrast) > ColorAdjustments.EPSILON)
            {
                map.putFloat("contrast", value.contrast);
            }

            if (Math.abs(value.hue) > ColorAdjustments.EPSILON)
            {
                map.putFloat("hue", value.hue);
            }

            if (Math.abs(value.saturation) > ColorAdjustments.EPSILON)
            {
                map.putFloat("saturation", value.saturation);
            }

            this.writeTransform(map, "brightnessTransform", value.brightnessTransform);
            this.writeTransform(map, "contrastTransform", value.contrastTransform);
            this.writeTransform(map, "hueTransform", value.hueTransform);
            this.writeTransform(map, "saturationTransform", value.saturationTransform);

            return map;
        }

        return new IntType(value.getARGBColor());
    }

    @Override
    public Color createEmpty()
    {
        /* Traditional form color: opaque white (alpha = opacity). */
        return new Color(1F, 1F, 1F, 1F);
    }

    @Override
    public Color copy(Color value)
    {
        return value.copy();
    }

    @Override
    public Color interpolate(Keyframe<Color> preA, Keyframe<Color> a, Keyframe<Color> b, Keyframe<Color> postB, IInterp interpolation, float x)
    {
        if (a.isSpectrum())
        {
            Colors.interpolateKeyframeColorHSV(this.i, preA.getValue(), a.getValue(), b.getValue(), postB.getValue(), interpolation, x);
        }
        else
        {
            Colors.interpolateKeyframeColorRGB(this.i, preA.getValue(), a.getValue(), b.getValue(), postB.getValue(), interpolation, x);
        }

        this.interpolateAdjustments(preA.getValue(), a.getValue(), b.getValue(), postB.getValue(), interpolation, x);
        this.interpolateTransforms(preA.getValue(), a.getValue(), b.getValue(), postB.getValue(), interpolation, x);

        return this.i;
    }

    @Override
    public Color interpolate(Color preA, Color a, Color b, Color postB, IInterp interpolation, float x)
    {
        Colors.interpolateKeyframeColorRGB(this.i, preA, a, b, postB, interpolation, x);
        this.interpolateAdjustments(preA, a, b, postB, interpolation, x);
        this.interpolateTransforms(preA, a, b, postB, interpolation, x);

        return this.i;
    }

    private void interpolateAdjustments(Color preA, Color a, Color b, Color postB, IInterp interpolation, float x)
    {
        this.i.brightness = ColorAdjustments.clampBrightness((float) interpolation.interpolate(IInterp.context.set(preA.brightness, a.brightness, b.brightness, postB.brightness, x)));
        this.i.contrast = ColorAdjustments.clampContrast((float) interpolation.interpolate(IInterp.context.set(preA.contrast, a.contrast, b.contrast, postB.contrast, x)));
        this.i.hue = ColorAdjustments.clampHue((float) interpolation.interpolate(IInterp.context.set(preA.hue, a.hue, b.hue, postB.hue, x)));
        this.i.saturation = ColorAdjustments.clampSaturation((float) interpolation.interpolate(IInterp.context.set(preA.saturation, a.saturation, b.saturation, postB.saturation, x)));
    }

    private void interpolateTransforms(Color preA, Color a, Color b, Color postB, IInterp interpolation, float x)
    {
        EffectTransformInterpolation.interpolate(this.i.transform, preA.transform, a.transform, b.transform, postB.transform, interpolation, x);
        EffectTransformInterpolation.interpolate(this.i.brightnessTransform, preA.brightnessTransform, a.brightnessTransform, b.brightnessTransform, postB.brightnessTransform, interpolation, x);
        EffectTransformInterpolation.interpolate(this.i.contrastTransform, preA.contrastTransform, a.contrastTransform, b.contrastTransform, postB.contrastTransform, interpolation, x);
        EffectTransformInterpolation.interpolate(this.i.hueTransform, preA.hueTransform, a.hueTransform, b.hueTransform, postB.hueTransform, interpolation, x);
        EffectTransformInterpolation.interpolate(this.i.saturationTransform, preA.saturationTransform, a.saturationTransform, b.saturationTransform, postB.saturationTransform, interpolation, x);
    }

    private void readTransform(MapType map, String key, EffectTransform target)
    {
        if (map.has(key) && target != null)
        {
            target.fromData(map.get(key));
        }
    }

    private void writeTransform(MapType map, String key, EffectTransform transform)
    {
        if (transform != null && transform.isActive())
        {
            map.put(key, transform.toData());
        }
    }
}
