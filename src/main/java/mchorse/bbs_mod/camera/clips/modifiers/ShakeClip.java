package mchorse.bbs_mod.camera.clips.modifiers;

import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.utils.ShakeApplicator;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;

/**
 * Shake modifier
 * 
 * This modifier shakes the camera depending on the given component 
 * flags.
 */
public class ShakeClip extends ComponentClip
{
    public final ValueFloat shake = new ValueFloat("shake", 0F);
    public final ValueFloat shakeAmount = new ValueFloat("shakeAmount", 0F);

    public ShakeClip()
    {
        super();

        this.add(this.shake);
        this.add(this.shakeAmount);

        /* Yaw and pitch should be enabled by default */
        this.active.set(0b0011000);
    }

    @Override
    public void applyClip(ClipContext context, Position position)
    {
        ShakeApplicator.apply(position, context.ticks + context.transition, this.shake.get(), this.shakeAmount.get(), this.active.get());
    }

    @Override
    public Clip create()
    {
        return new ShakeClip();
    }
}