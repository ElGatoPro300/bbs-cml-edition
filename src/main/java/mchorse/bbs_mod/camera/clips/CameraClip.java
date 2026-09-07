package mchorse.bbs_mod.camera.clips;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.interps.Lerps;

public abstract class CameraClip extends Clip
{
    public boolean isPositionClip()
    {
        return true;
    }

    public void shutdown(ClipContext context)
    {}

    public void fromCamera(Camera camera)
    {}

    public void applyLast(ClipContext context, Position position)
    {
        int duration = this.duration.get();

        this.applyClip(context.setup(this.tick.get() + duration, duration, 0, 0), position);
    }

    public void apply(ClipContext context, Position position)
    {
        if (!this.enabled.get())
        {
            return;
        }

        float factor = this.envelope.factorEnabled(this.duration.get(), context.relativeTick + context.transition);

        /* Non-position clips (screen FX, etc.) apply envelope inside applyClip — do not
         * lerp FOV/position here or fisheye FOV overscan gets double-attenuated. */
        if (factor == 1 || !this.isPositionClip())
        {
            this.applyClip(context, position);
        }
        else
        {
            Position temporary = new Position();

            temporary.set(position);
            this.applyClip(context, temporary);

            position.point.x = Lerps.lerp(position.point.x, temporary.point.x, factor);
            position.point.y = Lerps.lerp(position.point.y, temporary.point.y, factor);
            position.point.z = Lerps.lerp(position.point.z, temporary.point.z, factor);

            position.angle.yaw = (float) Lerps.lerpYaw(position.angle.yaw, temporary.angle.yaw, factor);
            position.angle.pitch = Lerps.lerp(position.angle.pitch, temporary.angle.pitch, factor);
            position.angle.roll = Lerps.lerp(position.angle.roll, temporary.angle.roll, factor);
            position.angle.fov = Lerps.lerp(position.angle.fov, temporary.angle.fov, factor);
        }
    }

    protected abstract void applyClip(ClipContext context, Position position);
}