package mchorse.bbs_mod.utils.joml;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Rotation helpers used by the limb IK and spring-chain physics pipeline.
 * Algorithms are standard: ZYX euler conversion, X-mirrored from-to alignment,
 * frame orientation with pinned normals, and swing-twist extraction.
 */
public final class QuaternionMath
{
    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);
    private static final float FRAME_EPS = 1.0e-12f;

    private QuaternionMath()
    {
    }

    public static Quaternionf composeFromEulerZYX(float xDeg, float yDeg, float zDeg)
    {
        return new Quaternionf().rotationZYX(zDeg * DEG_TO_RAD, yDeg * DEG_TO_RAD, xDeg * DEG_TO_RAD);
    }

    public static Quaternionf composeFromEulerZYXRadians(float x, float y, float z)
    {
        return new Quaternionf().rotationZYX(z, y, x);
    }

    /**
     * Principal ZYX euler angles of {@code q}, in degrees.
     *
     * <p>Goes through {@link Matrices#toEulerZYXRadians} rather than JOML's
     * {@code Quaternionf.getEulerAnglesZYX}, which the JOML bundled with
     * Minecraft 1.20.x (1.10.5) computes wrong past the ±90° middle angle
     * (pure {@code Ry(150°)} comes back as {@code (0, 30, 180)}). Nothing in
     * the mod may call JOML's flavour.
     */
    public static Vector3f decomposeEulerZYX(Quaternionf q)
    {
        return Matrices.toEulerZYXDegrees(q);
    }

    /** See {@link #decomposeEulerZYX}; angles in radians. */
    public static Vector3f decomposeEulerZYXRadians(Quaternionf q)
    {
        return Matrices.toEulerZYXRadians(q, new Vector3f());
    }

    /**
     * ZYX euler angles (degrees) of {@code q} in the representation continuous
     * with {@code referenceDeg} — the readback for any channel that has to stay
     * near its previous value across a pole. See
     * {@link Matrices#toCompatibleEulerZYXRadians(Quaternionf, Vector3f, Vector3f)}.
     */
    public static Vector3f decomposeCompatibleEulerZYX(Quaternionf q, Vector3f referenceDeg)
    {
        return Matrices.toCompatibleEulerZYXDegrees(q, referenceDeg, new Vector3f());
    }

    /** See {@link #decomposeCompatibleEulerZYX}; angles in radians. */
    public static Vector3f decomposeCompatibleEulerZYXRadians(Quaternionf q, Vector3f referenceRad)
    {
        return Matrices.toCompatibleEulerZYXRadians(q, referenceRad, new Vector3f());
    }

    /**
     * Shortest-arc alignment from rest direction to desired direction in the
     * cubic model's X-mirrored local space, conjugated back to model space.
     */
    public static Quaternionf fromToWithMirror(Vector3f restDir, Vector3f desiredDir)
    {
        Vector3f restM = new Vector3f(restDir);
        Vector3f desiredM = new Vector3f(desiredDir);

        restM.x = -restM.x;
        desiredM.x = -desiredM.x;
        restM.normalize();
        desiredM.normalize();

        Quaternionf mirrored = new Quaternionf().rotationTo(restM, desiredM);
        Matrix3f rotMir = new Matrix3f().set(mirrored);
        Matrix3f mirror = new Matrix3f().scaling(-1F, 1F, 1F);
        Matrix3f result = new Matrix3f(mirror).mul(rotMir).mul(mirror);

        return new Quaternionf().setFromNormalized(result);
    }

    /**
     * Builds an orientation that takes the orthonormal rest frame
     * {@code (restDir, restNormal)} to {@code (toDir, toNormal)} in X-mirrored
     * space. The normal pins roll so swing past 180° does not flip.
     */
    public static Quaternionf buildOrientedFrame(Vector3f restDir, Vector3f restNormal, Vector3f toDir, Vector3f toNormal)
    {
        Matrix3f rest = mirroredOrthonormal(restDir, restNormal);
        Matrix3f to = mirroredOrthonormal(toDir, toNormal);
        Matrix3f rotMir = to.mul(rest.transpose());
        Matrix3f mirror = new Matrix3f().scaling(-1F, 1F, 1F);
        Matrix3f result = new Matrix3f(mirror).mul(rotMir).mul(mirror);

        return new Quaternionf().setFromNormalized(result);
    }

    /**
     * Extracts the twist component of {@code q} about a normalized {@code axis}.
     */
    public static Quaternionf extractTwistComponent(Quaternionf q, Vector3f axis)
    {
        float dot = q.x * axis.x + q.y * axis.y + q.z * axis.z;
        Quaternionf twist = new Quaternionf(axis.x * dot, axis.y * dot, axis.z * dot, q.w);

        if (twist.lengthSquared() < FRAME_EPS)
        {
            return new Quaternionf();
        }

        return twist.normalize();
    }

    /**
     * Shortest-arc alignment without the cubic X-mirror. BOBJ bones do not get a
     * per-bone Ry(180°) in their armature matrices (only a global render flip), so
     * mirrored from-to inverts swing for IK on short chains.
     */
    public static Quaternionf rotationFromTo(Vector3f restDir, Vector3f desiredDir)
    {
        Vector3f a = new Vector3f(restDir).normalize();
        Vector3f b = new Vector3f(desiredDir).normalize();

        return new Quaternionf().rotationTo(a, b);
    }

    /**
     * Frame orientation without the cubic X-mirror — BOBJ IK chain orientations.
     */
    public static Quaternionf buildOrientedFrameDirect(Vector3f restDir, Vector3f restNormal, Vector3f toDir, Vector3f toNormal)
    {
        Matrix3f rest = orthonormal(restDir, restNormal);
        Matrix3f to = orthonormal(toDir, toNormal);
        Matrix3f rot = to.mul(rest.transpose());

        return new Quaternionf().setFromNormalized(rot);
    }

    private static Matrix3f orthonormal(Vector3f dir, Vector3f normal)
    {
        Vector3f u = new Vector3f(dir).normalize();
        Vector3f w = new Vector3f(u).cross(normal);

        if (w.lengthSquared() < FRAME_EPS)
        {
            Vector3f ref = Math.abs(u.x) < 0.9F ? new Vector3f(1F, 0F, 0F) : new Vector3f(0F, 1F, 0F);

            w.set(u).cross(ref);
        }

        w.normalize();

        Vector3f v = new Vector3f(w).cross(u);
        Matrix3f m = new Matrix3f();

        m.m00 = u.x;
        m.m01 = u.y;
        m.m02 = u.z;
        m.m10 = v.x;
        m.m11 = v.y;
        m.m12 = v.z;
        m.m20 = w.x;
        m.m21 = w.y;
        m.m22 = w.z;

        return m;
    }

    private static Matrix3f mirroredOrthonormal(Vector3f dir, Vector3f normal)
    {
        Vector3f u = new Vector3f(-dir.x, dir.y, dir.z).normalize();
        Vector3f w = new Vector3f(u).cross(-normal.x, normal.y, normal.z);

        if (w.lengthSquared() < FRAME_EPS)
        {
            Vector3f ref = Math.abs(u.x) < 0.9F ? new Vector3f(1F, 0F, 0F) : new Vector3f(0F, 1F, 0F);

            w.set(u).cross(ref);
        }

        w.normalize();

        Vector3f v = new Vector3f(w).cross(u);
        Matrix3f m = new Matrix3f();

        m.m00 = u.x;
        m.m01 = u.y;
        m.m02 = u.z;
        m.m10 = v.x;
        m.m11 = v.y;
        m.m12 = v.z;
        m.m20 = w.x;
        m.m21 = w.y;
        m.m22 = w.z;

        return m;
    }
}
