package mchorse.bbs_mod.forms.forms.utils;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.colors.Color;

import java.util.Objects;

/**
 * Glow color and intensity settings. Intensity is unbounded and may be negative
 * (negative values darken the surface).
 * <p>
 * {@link #radius} is Photoshop-like Outer Glow <b>Size</b> (signed: negative = tighter bloom).
 * {@link #spread} is Photoshop-like <b>Spread</b> (0 = soft, 1 = sharp/choked).
 * Legacy center/width/height stay for serialization compatibility.
 */
public class GlowSettings
{
    public float r = 1F;
    public float g = 1F;
    public float b = 1F;
    public float intensity;
    public boolean sync = false;
    /** When true, glow emission is applied only where paint is active. */
    public boolean paintOnly = false;
    /** Photoshop-like Outer Glow Size (signed bloom radius bias). */
    public float radius;
    /** Photoshop-like Outer Glow Spread, 0..1 (0 soft → 1 sharp). */
    public float spread;
    public float centerX;
    public float centerY;
    public float centerZ;
    public float width;
    public float height;
    public EffectTransform transform = new EffectTransform();

    public GlowSettings()
    {
        this(0F, 0F);
    }

    public GlowSettings(float intensity, float radius)
    {
        this.intensity = intensity;
        this.radius = radius;
    }

    public GlowSettings copy()
    {
        GlowSettings copy = new GlowSettings(this.intensity, this.radius);

        copy.r = this.r;
        copy.g = this.g;
        copy.b = this.b;
        copy.sync = this.sync;
        copy.paintOnly = this.paintOnly;
        copy.spread = this.spread;
        copy.centerX = this.centerX;
        copy.centerY = this.centerY;
        copy.centerZ = this.centerZ;
        copy.width = this.width;
        copy.height = this.height;
        copy.transform = this.transform == null ? new EffectTransform() : this.transform.copy();

        return copy;
    }

    /** Photoshop-like Outer Glow Size (signed; negative tightens bloom). */
    public float resolveSize()
    {
        return this.radius;
    }

    /** Photoshop-like Outer Glow Spread (0 soft … 1 sharp). */
    public float resolveSpread()
    {
        if (this.spread < 0F)
        {
            return 0F;
        }

        if (this.spread > 1F)
        {
            return 1F;
        }

        return this.spread;
    }

    public float getSpreadX()
    {
        if (this.width > 0F)
        {
            return this.width;
        }

        return (float) Math.max(Math.pow(2D, this.radius), 0.001D);
    }

    public float getSpreadZ()
    {
        if (this.height > 0F)
        {
            return this.height;
        }

        return this.getSpreadX();
    }

    public void resolveColor(Color fallback, Color out)
    {
        out.set(this.r, this.g, this.b, 1F);

        if (this.r == 1F && this.g == 1F && this.b == 1F && fallback != null)
        {
            if (fallback.r != 1F || fallback.g != 1F || fallback.b != 1F)
            {
                out.set(fallback.r, fallback.g, fallback.b, 1F);
            }
        }
    }

    /**
     * Returns glow intensity. {@link #intensity} is always authoritative (including {@code 0} =
     * glow off). Legacy {@code glowing_color} is migrated into modern glow in
     * {@code Form.fromData} / FormProperties — do not re-interpret it at render time, or
     * dual-written white {@code glowing_color} with alpha 1 forces full-strength glow.
     */
    public float resolveIntensity(Color legacy)
    {
        return this.intensity;
    }

    public boolean resolveSync()
    {
        return this.sync;
    }

    public boolean resolvePaintOnly()
    {
        return this.paintOnly;
    }

    public void fromData(BaseType data)
    {
        if (data instanceof MapType map)
        {
            this.r = map.has("r") ? map.getFloat("r") : 1F;
            this.g = map.has("g") ? map.getFloat("g") : 1F;
            this.b = map.has("b") ? map.getFloat("b") : 1F;
            this.intensity = map.getFloat("intensity");
            this.sync = map.getBool("sync", false);
            this.paintOnly = map.getBool("paintOnly", false);
            this.radius = map.getFloat("radius");
            this.spread = map.has("spread") ? map.getFloat("spread") : 0F;
            this.centerX = map.getFloat("centerX");
            this.centerY = map.getFloat("centerY");
            this.centerZ = map.getFloat("centerZ");
            this.width = map.getFloat("width");
            this.height = map.getFloat("height");

            if (map.has("transform"))
            {
                if (this.transform == null)
                {
                    this.transform = new EffectTransform();
                }

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
        map.putBool("paintOnly", this.paintOnly);
        map.putFloat("radius", this.radius);
        map.putFloat("spread", this.spread);
        map.putFloat("centerX", this.centerX);
        map.putFloat("centerY", this.centerY);
        map.putFloat("centerZ", this.centerZ);
        map.putFloat("width", this.width);
        map.putFloat("height", this.height);

        if (this.transform != null)
        {
            map.put("transform", this.transform.toData());
        }

        return map;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }

        if (!(o instanceof GlowSettings that))
        {
            return false;
        }

        return Float.compare(this.r, that.r) == 0
            && Float.compare(this.g, that.g) == 0
            && Float.compare(this.b, that.b) == 0
            && Float.compare(this.intensity, that.intensity) == 0
            && this.sync == that.sync
            && this.paintOnly == that.paintOnly
            && Float.compare(this.radius, that.radius) == 0
            && Float.compare(this.spread, that.spread) == 0
            && Float.compare(this.centerX, that.centerX) == 0
            && Float.compare(this.centerY, that.centerY) == 0
            && Float.compare(this.centerZ, that.centerZ) == 0
            && Float.compare(this.width, that.width) == 0
            && Float.compare(this.height, that.height) == 0
            && Objects.equals(this.transform, that.transform);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(this.r, this.g, this.b, this.intensity, this.sync, this.paintOnly, this.radius, this.spread, this.centerX, this.centerY, this.centerZ, this.width, this.height, this.transform);
    }
}
