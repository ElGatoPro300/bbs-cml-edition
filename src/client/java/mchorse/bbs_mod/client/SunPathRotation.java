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
 * <p>
 * Never mutate the live world/camera model-view in place — Sodium reuses that matrix after
 * sky drawing; an unrestored yaw leak flips the view (~180°) and makes WASD look reversed.
 * Use {@link #copyWithSkyYaw(Matrix4f)} for vanilla sky instead.
 */
public final class SunPathRotation
{
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

    /**
     * Returns {@code modelView} unchanged, or a yaw-rotated <em>copy</em> for sky rendering.
     * The input matrix is never written.
     */
    public static Matrix4f copyWithSkyYaw(Matrix4f modelView)
    {
        return copyWithSkyYaw(modelView, false);
    }

    /**
     * @param thickFog Nether / thick-fog sky — skip rotation (sky is not drawn usefully).
     */
    public static Matrix4f copyWithSkyYaw(Matrix4f modelView, boolean thickFog)
    {
        if (modelView == null || thickFog || !isActive())
        {
            return modelView;
        }

        Matrix4f copy = new Matrix4f(modelView);

        applyY(copy, getDegrees());

        return copy;
    }

    /** @deprecated No-op kept for call-site compatibility; do not mutate shared matrices. */
    @Deprecated
    public static void begin(Matrix4f matrix)
    {
    }

    /** @deprecated No-op kept for call-site compatibility. */
    @Deprecated
    public static void begin(Matrix4f matrix, boolean thickFog)
    {
    }

    /** @deprecated No-op kept for call-site compatibility. */
    @Deprecated
    public static void end(Matrix4f matrix)
    {
    }

    /** @deprecated No-op; shared model-view is no longer mutated by sky path. */
    @Deprecated
    public static void forceClear()
    {
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
