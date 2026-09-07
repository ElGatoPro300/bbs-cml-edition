package mchorse.bbs_mod.client;

import net.minecraft.util.math.RotationAxis;

import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Rotates the celestial dome around the vertical axis (Mine-imator sun path rotation).
 * <p>
 * Degrees come from {@link BBSRendering#getSunPathRotationDegrees()} (film curve while
 * playing, otherwise World Properties). Sky / Iris celestial use {@link #getDegrees()};
 * GLSL helpers and shadow {@code MODELVIEW} use {@link #getLightYawDegrees()}.
 */
public final class SunPathRotation
{
    private static Matrix4f savedMatrix;

    private SunPathRotation()
    {
    }

    public static float getDegrees()
    {
        return BBSRendering.getSunPathRotationDegrees();
    }

    /**
     * Yaw fed to {@code bbs_sun_path_rotation} / Iris shadow model-view.
     */
    public static float getLightYawDegrees()
    {
        return -getDegrees();
    }

    public static boolean isActive()
    {
        return getDegrees() != 0F;
    }

    public static void begin(Matrix4f matrix)
    {
        float degrees = getDegrees();

        if (degrees == 0F)
        {
            savedMatrix = null;

            return;
        }

        savedMatrix = new Matrix4f(matrix);
        applyY(matrix, degrees);
    }

    public static void end(Matrix4f matrix)
    {
        if (savedMatrix != null)
        {
            matrix.set(savedMatrix);
            savedMatrix = null;
        }
    }

    public static void applyY(Matrix4f matrix)
    {
        applyY(matrix, getDegrees());
    }

    public static void applyLightYaw(Matrix4f matrix)
    {
        applyY(matrix, getLightYawDegrees());
    }

    public static void applyY(Matrix4f matrix, float degrees)
    {
        if (matrix == null || degrees == 0F)
        {
            return;
        }

        matrix.rotate(RotationAxis.POSITIVE_Y.rotationDegrees(degrees));
    }

    public static void applyY(Vector4f vector)
    {
        applyY(vector, getDegrees());
    }

    public static void applyLightYaw(Vector4f vector)
    {
        applyY(vector, getLightYawDegrees());
    }

    public static void applyY(Vector4f vector, float degrees)
    {
        if (vector == null || degrees == 0F)
        {
            return;
        }

        vector.rotateY((float) Math.toRadians(degrees));
    }
}
