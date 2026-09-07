package mchorse.bbs_mod.utils.keyframes.factories;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.utils.ShakeSettings;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

public class ShakeSettingsKeyframeFactory implements IKeyframeFactory<ShakeSettings>
{
    private final ShakeSettings i = new ShakeSettings();

    @Override
    public ShakeSettings fromData(BaseType data)
    {
        ShakeSettings value = new ShakeSettings();

        if (data.isMap())
        {
            value.fromData(data);
        }
        else if (data.isNumeric())
        {
            value.shake = (float) data.asNumeric().doubleValue();
        }

        return value;
    }

    @Override
    public BaseType toData(ShakeSettings value)
    {
        return value == null ? new MapType() : value.toData();
    }

    @Override
    public ShakeSettings createEmpty()
    {
        return new ShakeSettings();
    }

    @Override
    public ShakeSettings copy(ShakeSettings value)
    {
        return value == null ? null : value.copy();
    }

    @Override
    public ShakeSettings interpolate(Keyframe<ShakeSettings> preA, Keyframe<ShakeSettings> a, Keyframe<ShakeSettings> b, Keyframe<ShakeSettings> postB, IInterp interpolation, float x)
    {
        ShakeSettings preAValue = this.valueOrDefault(preA.getValue());
        ShakeSettings aValue = this.valueOrDefault(a.getValue());
        ShakeSettings bValue = this.valueOrDefault(b.getValue());
        ShakeSettings postBValue = this.valueOrDefault(postB.getValue());

        return this.interpolate(preAValue, aValue, bValue, postBValue, interpolation, x);
    }

    @Override
    public ShakeSettings interpolate(ShakeSettings preA, ShakeSettings a, ShakeSettings b, ShakeSettings postB, IInterp interpolation, float x)
    {
        ShakeSettings preAValue = this.valueOrDefault(preA);
        ShakeSettings aValue = this.valueOrDefault(a);
        ShakeSettings bValue = this.valueOrDefault(b);
        ShakeSettings postBValue = this.valueOrDefault(postB);

        this.i.shake = (float) interpolation.interpolate(IInterp.context.set(preAValue.shake, aValue.shake, bValue.shake, postBValue.shake, x));
        this.i.shakeAmount = (float) interpolation.interpolate(IInterp.context.set(preAValue.shakeAmount, aValue.shakeAmount, bValue.shakeAmount, postBValue.shakeAmount, x));
        this.i.active = x >= 0.5F ? bValue.active : aValue.active;

        return this.i;
    }

    @Override
    public double getY(ShakeSettings value)
    {
        ShakeSettings settings = this.valueOrDefault(value);

        return settings.shakeAmount;
    }

    @Override
    public Object yToValue(double y)
    {
        return (float) y;
    }

    private ShakeSettings valueOrDefault(ShakeSettings value)
    {
        return value == null ? new ShakeSettings() : value;
    }
}
