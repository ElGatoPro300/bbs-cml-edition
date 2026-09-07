package mchorse.bbs_mod.settings.values.misc;

import mchorse.bbs_mod.forms.forms.utils.ShakeSettings;
import mchorse.bbs_mod.settings.values.base.BaseKeyframeFactoryValue;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

public class ValueShakeSettings extends BaseKeyframeFactoryValue<ShakeSettings>
{
    public ValueShakeSettings(String id, ShakeSettings value)
    {
        super(id, KeyframeFactories.SHAKE_SETTINGS, value);
    }
}
