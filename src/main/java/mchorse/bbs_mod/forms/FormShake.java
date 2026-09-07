package mchorse.bbs_mod.forms;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.utils.ShakeSettings;
import mchorse.bbs_mod.settings.values.misc.ValueShakeSettings;
import mchorse.bbs_mod.utils.ShakeApplicator;
import mchorse.bbs_mod.utils.pose.Transform;

/**
 * Applies procedural shake from form shake tracks onto a composed transform.
 */
public final class FormShake
{
    private FormShake()
    {}

    public static void apply(Transform transform, Form form, float animTime)
    {
        if (transform == null || form == null)
        {
            return;
        }

        ShakeSettings settings = resolveShake(form.shake);

        if (settings == null || settings.shakeAmount == 0F)
        {
            return;
        }

        ShakeApplicator.apply(transform, animTime, settings.shake, settings.shakeAmount, settings.active);
    }

    private static ShakeSettings resolveShake(ValueShakeSettings value)
    {
        if (value == null)
        {
            return null;
        }

        ShakeSettings runtime = value.getRuntimeValue();

        return runtime != null ? runtime : value.get();
    }
}
