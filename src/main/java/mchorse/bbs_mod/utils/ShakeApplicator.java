package mchorse.bbs_mod.utils;

import mchorse.bbs_mod.camera.clips.modifiers.ShakeClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.utils.pose.Transform;

/**
 * Shared shake math for camera {@link ShakeClip}
 * and model form shake tracks.
 */
public final class ShakeApplicator
{
    private ShakeApplicator()
    {}

    public static void apply(Position position, float time, float shake, float amount, int active)
    {
        if (position == null || amount == 0F)
        {
            return;
        }

        float x = time / (shake == 0F ? 1F : shake);

        boolean isX = isActive(active, 0);
        boolean isY = isActive(active, 1);
        boolean isZ = isActive(active, 2);
        boolean isYaw = isActive(active, 3);
        boolean isPitch = isActive(active, 4);
        boolean isRoll = isActive(active, 5);
        boolean isFov = isActive(active, 6);

        double sin = Math.sin(x);
        double cos = Math.cos(x);

        if (isYaw && isPitch && !isX && !isY && !isZ && !isRoll && !isFov)
        {
            float swingX = (float) (sin * sin * cos * Math.cos(x / 2D));
            float swingY = (float) (cos * sin * sin);

            position.angle.yaw += swingX * amount;
            position.angle.pitch += swingY * amount;
        }
        else
        {
            if (isX)
            {
                position.point.x += sin * amount;
            }

            if (isY)
            {
                position.point.y -= sin * amount;
            }

            if (isZ)
            {
                position.point.z += cos * amount;
            }

            if (isYaw)
            {
                position.angle.yaw += sin * amount;
            }

            if (isPitch)
            {
                position.angle.pitch += cos * amount;
            }

            if (isRoll)
            {
                position.angle.roll += sin * amount;
            }

            if (isFov)
            {
                position.angle.fov += cos * amount;
            }
        }
    }

    /**
     * Applies shake offsets onto a form transform (rotate channels are radians).
     */
    public static void apply(Transform transform, float time, float shake, float amount, int active)
    {
        if (transform == null || amount == 0F)
        {
            return;
        }

        float x = time / (shake == 0F ? 1F : shake);

        boolean isX = isActive(active, 0);
        boolean isY = isActive(active, 1);
        boolean isZ = isActive(active, 2);
        boolean isYaw = isActive(active, 3);
        boolean isPitch = isActive(active, 4);
        boolean isRoll = isActive(active, 5);

        double sin = Math.sin(x);
        double cos = Math.cos(x);

        if (isYaw && isPitch && !isX && !isY && !isZ && !isRoll)
        {
            float swingX = (float) (sin * sin * cos * Math.cos(x / 2D));
            float swingY = (float) (cos * sin * sin);

            transform.rotate.y += MathUtils.toRad(swingX * amount);
            transform.rotate.x += MathUtils.toRad(swingY * amount);
        }
        else
        {
            if (isX)
            {
                transform.translate.x += (float) (sin * amount);
            }

            if (isY)
            {
                transform.translate.y -= (float) (sin * amount);
            }

            if (isZ)
            {
                transform.translate.z += (float) (cos * amount);
            }

            if (isYaw)
            {
                transform.rotate.y += MathUtils.toRad((float) (sin * amount));
            }

            if (isPitch)
            {
                transform.rotate.x += MathUtils.toRad((float) (cos * amount));
            }

            if (isRoll)
            {
                transform.rotate.z += MathUtils.toRad((float) (sin * amount));
            }
        }
    }

    public static boolean isActive(int active, int bit)
    {
        return (active >> bit & 1) == 1;
    }
}
