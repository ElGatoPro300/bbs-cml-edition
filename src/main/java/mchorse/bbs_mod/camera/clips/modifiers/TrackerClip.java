package mchorse.bbs_mod.camera.clips.modifiers;

import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.camera.values.ValuePoint;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

public class TrackerClip extends EntityClip
{
    public final ValuePoint angle = new ValuePoint("angle", new Point(0, 0, 0));
    public final ValueFloat fov = new ValueFloat("fov", 70F);
    public final ValueString group = new ValueString("group", "");
    public final ValueBoolean lookAt = new ValueBoolean("look_at", false);
    public final ValueBoolean relative = new ValueBoolean("relative");
    public final ValueInt active = new ValueInt("active", 0b1111111, 0, 0b1111111);

    /* Keyframe mode: opt-in animated offset/angle/fov and target on top of the
     * static values above. Channels are in tracker-local space, like the statics. */
    public final ValueBoolean useKeyframes = new ValueBoolean("use_keyframes", false);
    public final KeyframeChannel<Double> keyframeTarget = new KeyframeChannel<>("target", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<String> keyframeAttachment = new KeyframeChannel<>("attachment", KeyframeFactories.STRING);
    public final KeyframeChannel<Double> keyframeX = new KeyframeChannel<>("x", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> keyframeY = new KeyframeChannel<>("y", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> keyframeZ = new KeyframeChannel<>("z", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> keyframePitch = new KeyframeChannel<>("pitch", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> keyframeYaw = new KeyframeChannel<>("yaw", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> keyframeRoll = new KeyframeChannel<>("roll", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> keyframeFov = new KeyframeChannel<>("kf_fov", KeyframeFactories.DOUBLE);

    public final KeyframeChannel[] channels;

    public TrackerClip()
    {
        super();

        this.channels = new KeyframeChannel[]
        {
            this.keyframeTarget,
            this.keyframeAttachment,
            this.keyframeX,
            this.keyframeY,
            this.keyframeZ,
            this.keyframePitch,
            this.keyframeYaw,
            this.keyframeRoll,
            this.keyframeFov
        };

        this.add(this.angle);
        this.add(this.fov);
        this.add(this.group);
        this.add(this.lookAt);
        this.add(this.relative);
        this.add(this.active);
        this.add(this.useKeyframes);
        this.add(this.keyframeTarget);
        this.add(this.keyframeAttachment);
        this.add(this.keyframeX);
        this.add(this.keyframeY);
        this.add(this.keyframeZ);
        this.add(this.keyframePitch);
        this.add(this.keyframeYaw);
        this.add(this.keyframeRoll);
        this.add(this.keyframeFov);
    }

    /**
     * Offset at the given local tick: keyframed per component, falling back to
     * the static offset where a channel has no keyframes.
     */
    public Point evaluateOffset(float tick)
    {
        Point offset = this.offset.get();

        return new Point(
            this.keyframeX.isEmpty() ? offset.x : this.keyframeX.interpolate(tick),
            this.keyframeY.isEmpty() ? offset.y : this.keyframeY.interpolate(tick),
            this.keyframeZ.isEmpty() ? offset.z : this.keyframeZ.interpolate(tick)
        );
    }

    /**
     * Angle offset at the given local tick, in the tracker's Point convention
     * (x = pitch, y = yaw, z = roll).
     */
    public Point evaluateAngle(float tick)
    {
        Point angle = this.angle.get();

        return new Point(
            this.keyframePitch.isEmpty() ? angle.x : this.keyframePitch.interpolate(tick),
            this.keyframeYaw.isEmpty() ? angle.y : this.keyframeYaw.interpolate(tick),
            this.keyframeRoll.isEmpty() ? angle.z : this.keyframeRoll.interpolate(tick)
        );
    }

    public float evaluateFov(float tick)
    {
        return this.keyframeFov.isEmpty() ? this.fov.get() : this.keyframeFov.interpolate(tick).floatValue();
    }

    /** Tracked entity index at the given local tick, rounded to the nearest keyframed value. */
    public int evaluateTarget(float tick)
    {
        return this.keyframeTarget.isEmpty() ? this.selector.get() : (int) Math.round(this.keyframeTarget.interpolate(tick));
    }

    /** Attachment / bone group at the given local tick. */
    public String evaluateAttachment(float tick)
    {
        if (this.keyframeAttachment.isEmpty())
        {
            return this.group.get();
        }

        String value = this.keyframeAttachment.interpolate(tick);

        return value == null ? this.group.get() : value;
    }

    /**
     * When enabling keyframe mode, fill any empty channels from the static
     * values so playback starts from what's on screen. Existing keyframes are
     * preserved.
     */
    public void ensureChannelsSeeded()
    {
        Point offset = this.offset.get();
        Point angle = this.angle.get();

        this.seed(this.keyframeTarget, this.selector.get());
        this.seedAttachment(this.group.get());
        this.seed(this.keyframeX, offset.x);
        this.seed(this.keyframeY, offset.y);
        this.seed(this.keyframeZ, offset.z);
        this.seed(this.keyframePitch, angle.x);
        this.seed(this.keyframeYaw, angle.y);
        this.seed(this.keyframeRoll, angle.z);
        this.seed(this.keyframeFov, this.fov.get());
    }

    private void seed(KeyframeChannel<Double> channel, double value)
    {
        if (channel.isEmpty())
        {
            channel.insert(0, value);
        }
    }

    private void seedAttachment(String value)
    {
        if (this.keyframeAttachment.isEmpty())
        {
            this.keyframeAttachment.insert(0, value == null ? "" : value);
        }
    }

    @Override
    protected void applyClip(ClipContext context, Position position)
    {}

    @Override
    protected Clip create()
    {
        return new TrackerClip();
    }
}
