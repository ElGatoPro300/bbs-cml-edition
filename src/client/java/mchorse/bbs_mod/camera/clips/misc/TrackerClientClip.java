package mchorse.bbs_mod.camera.clips.misc;

import mchorse.bbs_mod.camera.clips.CameraClipContext;
import mchorse.bbs_mod.camera.clips.modifiers.TrackerClip;
import mchorse.bbs_mod.camera.data.Angle;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.ui.film.clips.UITrackerClip;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;

import org.joml.Vector3d;

import java.util.List;
import java.util.Objects;

public class TrackerClientClip extends TrackerClip
{
    /**
     * Position produced by the clips underneath at the tick this clip was last
     * applied at. Relative mode measures the camera's travel against it, and the
     * camera editor needs the same measurement to invert the placement — see
     * {@link UITrackerClip}.
     */
    private final Position underneath = new Position();

    private boolean evaluated;

    /**
     * Whether this clip has been applied at least once, i.e. whether
     * {@link #getUnderneath()} holds anything meaningful.
     */
    public boolean isEvaluated()
    {
        return this.evaluated;
    }

    public Position getUnderneath()
    {
        return this.underneath;
    }

    @Override
    protected void applyClip(ClipContext context, Position position)
    {
        CameraClipContext cameraContext = (CameraClipContext) context;
        float t = context.relativeTick + context.transition;
        boolean keyframed = this.useKeyframes.get();

        if (!context.applyUnderneath(Math.round(this.tick.get()), 0F, this.position))
        {
            this.position.copy(position);
        }

        Point offset = keyframed ? this.evaluateOffset(t) : this.offset.get();
        Point angle = keyframed ? this.evaluateAngle(t) : this.angle.get();
        float fov = keyframed ? this.evaluateFov(t) : this.fov.get();

        int targetA = this.selector.get();
        int targetB = targetA;
        float targetBlend = 0F;
        String groupA = this.group.get();
        String groupB = groupA;
        float groupBlend = 0F;

        if (keyframed && !this.keyframeTarget.isEmpty())
        {
            KeyframeSegment<Double> segment = this.keyframeTarget.findSegment(t);

            if (segment != null)
            {
                targetA = (int) Math.round(segment.a.getValue());
                targetB = (int) Math.round(segment.b.getValue());

                /* Ease 0→1 with the channel's curve — don't derive blend from the
                 * interpolated actor index (that breaks across non-adjacent ids). */
                if (targetA != targetB)
                {
                    targetBlend = segment.a.getInterpolation().interpolate(0F, 1F, segment.x);
                }
            }
        }
        else if (!keyframed)
        {
            List<IEntity> entities = this.getEntities(context);

            if (entities.isEmpty())
            {
                return;
            }
        }

        if (keyframed && !this.keyframeAttachment.isEmpty())
        {
            KeyframeSegment<String> segment = this.keyframeAttachment.findSegment(t);

            if (segment != null)
            {
                groupA = segment.a.getValue() == null ? "" : segment.a.getValue();
                groupB = segment.b.getValue() == null ? groupA : segment.b.getValue();

                if (!Objects.equals(groupA, groupB))
                {
                    groupBlend = segment.a.getInterpolation().interpolate(0F, 1F, segment.x);
                }
            }
        }

        Position tracked = this.resolveTracked(cameraContext, targetA, targetB, targetBlend, groupA, groupB, groupBlend, offset, angle, fov, position);

        if (tracked == null)
        {
            return;
        }

        this.underneath.copy(position);
        this.evaluated = true;

        position.point.x = this.isActive(0) ? tracked.point.x : position.point.x;
        position.point.y = this.isActive(1) ? tracked.point.y : position.point.y;
        position.point.z = this.isActive(2) ? tracked.point.z : position.point.z;
        position.angle.yaw = this.isActive(3) ? tracked.angle.yaw : position.angle.yaw;
        position.angle.pitch = this.isActive(4) ? tracked.angle.pitch : position.angle.pitch;
        position.angle.roll = this.isActive(5) ? tracked.angle.roll : position.angle.roll;
        position.angle.fov = this.isActive(6) ? tracked.angle.fov : position.angle.fov;
    }

    /**
     * Resolve camera placement, blending across target and/or attachment changes.
     */
    private Position resolveTracked(CameraClipContext context, int targetA, int targetB, float targetBlend, String groupA, String groupB, float groupBlend, Point offset, Point angle, float fov, Position base)
    {
        Position left = this.resolveTarget(context, targetA, groupA, groupB, groupBlend, offset, angle, fov, base);

        if (targetBlend <= 0F || targetA == targetB)
        {
            return left;
        }

        Position right = this.resolveTarget(context, targetB, groupA, groupB, groupBlend, offset, angle, fov, base);

        return this.blendPositions(left, right, targetBlend);
    }

    private Position resolveTarget(CameraClipContext context, int target, String groupA, String groupB, float groupBlend, Point offset, Point angle, float fov, Position base)
    {
        Position a = new Position();
        boolean resolvedA = this.track(context, target, groupA, offset, angle, fov, base, a);

        if (groupBlend <= 0F || Objects.equals(groupA, groupB))
        {
            return resolvedA ? a : null;
        }

        Position b = new Position();
        boolean resolvedB = this.track(context, target, groupB, offset, angle, fov, base, b);

        return this.blendPositions(resolvedA ? a : null, resolvedB ? b : null, groupBlend);
    }

    private Position blendPositions(Position a, Position b, float blend)
    {
        if (a == null)
        {
            return b;
        }

        if (b == null || blend <= 0F)
        {
            return a;
        }

        if (blend >= 1F)
        {
            return b;
        }

        Position out = a.copy();

        out.point.x = Lerps.lerp(a.point.x, b.point.x, blend);
        out.point.y = Lerps.lerp(a.point.y, b.point.y, blend);
        out.point.z = Lerps.lerp(a.point.z, b.point.z, blend);
        out.angle.yaw = (float) Lerps.lerpYaw(a.angle.yaw, b.angle.yaw, blend);
        out.angle.pitch = Lerps.lerp(a.angle.pitch, b.angle.pitch, blend);
        out.angle.roll = Lerps.lerp(a.angle.roll, b.angle.roll, blend);
        out.angle.fov = Lerps.lerp(a.angle.fov, b.angle.fov, blend);

        return out;
    }

    /**
     * Place the camera against a single tracked entity + attachment, writing the
     * full result into {@code out}.
     *
     * @return {@code false} when the entity or its bone can't be resolved
     */
    private boolean track(CameraClipContext context, int selector, String group, Point offset, Point angle, float fov, Position base, Position out)
    {
        IEntity entity = selector >= 0 ? context.entities.get(selector) : null;

        if (entity == null)
        {
            return false;
        }

        TrackerFrame frame = TrackerFrame.resolve(
            context.entities,
            entity,
            group == null ? "" : group,
            base.point.x,
            base.point.y,
            base.point.z,
            context.transition
        );

        if (frame == null)
        {
            return false;
        }

        if (this.relative.get())
        {
            frame.relative(base, this.position);
        }

        out.copy(base);

        boolean lookAt = this.lookAt.get();
        Angle newAngle = lookAt ? frame.lookAtAngles(offset, angle) : frame.angles(angle);

        if (!lookAt)
        {
            Vector3d newPosition = frame.position(offset);

            out.point.x = newPosition.x;
            out.point.y = newPosition.y;
            out.point.z = newPosition.z;
        }

        out.angle.yaw = newAngle.yaw;
        out.angle.pitch = newAngle.pitch;
        out.angle.roll = newAngle.roll;
        out.angle.fov = fov;

        return true;
    }

    public boolean isActive(int bit)
    {
        return (this.active.get() >> bit & 1) == 1;
    }

    @Override
    protected Clip create()
    {
        return new TrackerClientClip();
    }
}
