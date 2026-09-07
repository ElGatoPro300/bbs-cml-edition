package mchorse.bbs_mod.camera.clips.modifiers;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.clips.CameraClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

public class RemapperClip extends CameraClip
{
    public final KeyframeChannel<Double> channel = new KeyframeChannel<>("channel", KeyframeFactories.DOUBLE);

    public RemapperClip()
    {
        super();

        this.add(this.channel);

        this.channel.insert(0, 0D);
        this.channel.insert(BBSSettings.getDefaultDuration(), 1D);
    }

    @Override
    public void applyClip(ClipContext context, Position position)
    {
        double factor = this.channel.interpolate(context.relativeTick + context.transition);
        int duration = this.duration.get();

        factor *= duration;
        factor = MathUtils.clamp(factor, 0, duration - 0.0001F);

        context.applyUnderneath(this.tick.get() + (int) factor, (float) (factor % 1), position);
    }

    @Override
    public boolean isPositionClip()
    {
        return false;
    }

    @Override
    public Clip create()
    {
        return new RemapperClip();
    }

    @Override
    protected void breakDownClip(Clip original, int offset)
    {
        super.breakDownClip(original, offset);

        this.channel.moveX(-offset);
    }
}