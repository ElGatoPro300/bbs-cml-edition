package mchorse.bbs_mod.forms.forms.utils;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;

import java.util.Objects;

/**
 * Unified shake settings for model tracks: frequency, amount, and active axis flags.
 */
public class ShakeSettings
{
    public float shake;
    public float shakeAmount;
    public int active;

    public ShakeSettings()
    {
        this(0F, 0F, 0b0011000);
    }

    public ShakeSettings(float shake, float shakeAmount, int active)
    {
        this.shake = shake;
        this.shakeAmount = shakeAmount;
        this.active = active & 0b1111111;
    }

    public ShakeSettings copy()
    {
        return new ShakeSettings(this.shake, this.shakeAmount, this.active);
    }

    public void fromData(BaseType data)
    {
        if (data instanceof MapType map)
        {
            this.shake = map.getFloat("shake", 0F);

            if (map.has("shake_amount"))
            {
                this.shakeAmount = map.getFloat("shake_amount", 0F);
            }
            else
            {
                this.shakeAmount = map.getFloat("shakeAmount", 0F);
            }

            if (map.has("active"))
            {
                this.active = map.getInt("active", 0b0011000) & 0b1111111;
            }
            else
            {
                this.active = map.getInt("shake_active", 0b0011000) & 0b1111111;
            }
        }
    }

    public BaseType toData()
    {
        MapType map = new MapType();

        map.putFloat("shake", this.shake);
        map.putFloat("shake_amount", this.shakeAmount);
        map.putInt("active", this.active);

        return map;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }

        if (!(o instanceof ShakeSettings))
        {
            return false;
        }

        ShakeSettings that = (ShakeSettings) o;

        return this.shake == that.shake
            && this.shakeAmount == that.shakeAmount
            && this.active == that.active;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(this.shake, this.shakeAmount, this.active);
    }
}
