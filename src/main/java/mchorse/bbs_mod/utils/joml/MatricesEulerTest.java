package mchorse.bbs_mod.utils.joml;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Checks for the ZYX euler readback in {@link Matrices} and {@link QuaternionMath}.
 *
 * <p>Run as a plain {@code main} (same style as
 * {@link mchorse.bbs_mod.utils.MatrixUtilsTest}); it needs nothing but JOML, and
 * exits non-zero when a check fails.
 *
 * <p>These exist because the readback cannot use JOML's own: the JOML bundled
 * with Minecraft 1.20.x (1.10.5) gets {@code getEulerAnglesZYX} wrong past a
 * ±90° middle angle. The sanity check below asserts that JOML really is still
 * broken — if it ever starts passing, the game shipped a fixed JOML and the
 * comments in {@link Matrices} need revisiting.
 */
public class MatricesEulerTest
{
    private static int failures;

    public static void main(String[] args)
    {
        roundTripSweep();
        poleSweep();
        knownCases();
        compatibleReadback();
        delegates();

        System.out.println(failures == 0 ? "ALL PASS" : (failures + " FAILURES"));
        System.exit(failures == 0 ? 0 : 1);
    }

    /** Every 15° over all three axes, well past the ±90° middle angle. */
    private static void roundTripSweep()
    {
        float worst = 0F;
        float worstJoml = 0F;

        for (int x = -180; x <= 180; x += 15)
        {
            for (int y = -180; y <= 180; y += 15)
            {
                for (int z = -180; z <= 180; z += 15)
                {
                    Quaternionf q = QuaternionMath.composeFromEulerZYX(x, y, z);
                    Vector3f e = QuaternionMath.decomposeEulerZYX(q);

                    worst = Math.max(worst, distanceDeg(q, QuaternionMath.composeFromEulerZYX(e.x, e.y, e.z)));

                    Vector3f j = new Quaternionf(q).normalize().getEulerAnglesZYX(new Vector3f());

                    worstJoml = Math.max(worstJoml, distanceDeg(q, QuaternionMath.composeFromEulerZYXRadians(j.x, j.y, j.z)));
                }
            }
        }

        System.out.println("round-trip worst error: ours = " + worst + " deg, JOML = " + worstJoml + " deg");
        check("round-trip stays exact", worst < 0.1F);
        check("JOML really is still broken (sanity)", worstJoml > 1F);
    }

    /**
     * The ±90° gimbal pole, where the x/z split is ill-conditioned. Straying
     * onto the generic {@code atan2} branch too close to the pole reads x and z
     * out of float rounding noise — this is what sizes {@code POLE_EPSILON}.
     */
    private static void poleSweep()
    {
        float worstNear = 0F;

        for (float y = 88F; y <= 92F; y += 0.01F)
        {
            for (int x = -180; x <= 180; x += 45)
            {
                for (int z = -180; z <= 180; z += 45)
                {
                    Quaternionf q = QuaternionMath.composeFromEulerZYX(x, y, z);
                    Vector3f e = QuaternionMath.decomposeEulerZYX(q);

                    worstNear = Math.max(worstNear, distanceDeg(q, QuaternionMath.composeFromEulerZYX(e.x, e.y, e.z)));
                }
            }
        }

        float worstAt = 0F;

        for (int x = -180; x <= 180; x += 15)
        {
            for (int z = -180; z <= 180; z += 15)
            {
                for (int sign = -1; sign <= 1; sign += 2)
                {
                    Quaternionf q = QuaternionMath.composeFromEulerZYX(x, 90F * sign, z);
                    Vector3f e = QuaternionMath.decomposeEulerZYX(q);

                    worstAt = Math.max(worstAt, distanceDeg(q, QuaternionMath.composeFromEulerZYX(e.x, e.y, e.z)));
                }
            }
        }

        System.out.println("pole: near worst = " + worstNear + " deg, exact worst = " + worstAt + " deg");

        /* Crossing over to the pole branch costs at most snapping y onto ±90°
         * from 89.94°, which is under 0.06° of rotation. */
        check("through-pole sweep stays exact", worstNear < 0.1F);
        check("exact pole stays exact", worstAt < 0.1F);
    }

    /** The two documented JOML failures: the 180° flip, and NaN at the pole. */
    private static void knownCases()
    {
        Quaternionf ry150 = QuaternionMath.composeFromEulerZYX(0F, 150F, 0F);
        Vector3f e150 = QuaternionMath.decomposeEulerZYX(ry150);

        System.out.println("Ry(150) -> " + e150);
        check("Ry(150) round-trips", distanceDeg(ry150, QuaternionMath.composeFromEulerZYX(e150.x, e150.y, e150.z)) < 0.01F);

        Quaternionf atPole = QuaternionMath.composeFromEulerZYX(20F, 90F, 35F);
        Vector3f pole = QuaternionMath.decomposeEulerZYX(atPole);

        System.out.println("(20, 90, 35) -> " + pole);
        check("pole is finite", Float.isFinite(pole.x) && Float.isFinite(pole.y) && Float.isFinite(pole.z));
        check("pole round-trips", distanceDeg(atPole, QuaternionMath.composeFromEulerZYX(pole.x, pole.y, pole.z)) < 0.05F);
    }

    /** The branch stays where the reference is, and the winding keeps counting. */
    private static void compatibleReadback()
    {
        Vector3f reference = new Vector3f(170F, 85F, -160F);
        Quaternionf q = QuaternionMath.composeFromEulerZYX(reference.x, 95F, reference.z);
        Vector3f compatible = QuaternionMath.decomposeCompatibleEulerZYX(q, reference);
        Vector3f plain = QuaternionMath.decomposeEulerZYX(q);

        System.out.println("past-pole reference " + reference + " -> plain " + plain + ", compatible " + compatible);
        check("compatible holds the reference's branch", offsetL1(compatible, reference) < offsetL1(plain, reference));
        check("compatible round-trips", distanceDeg(q, QuaternionMath.composeFromEulerZYX(compatible.x, compatible.y, compatible.z)) < 0.01F);

        Vector3f wound = QuaternionMath.decomposeCompatibleEulerZYX(
            QuaternionMath.composeFromEulerZYX(0F, 0F, 370F), new Vector3f(0F, 0F, 350F));

        System.out.println("wound z = 350 -> " + wound);
        check("winding continues past 180", Math.abs(wound.z - 370F) < 0.01F);
    }

    /** {@link Matrices}' conversions and {@link QuaternionMath}'s cannot fork. */
    private static void delegates()
    {
        Quaternionf q = QuaternionMath.composeFromEulerZYX(12F, 130F, -47F);
        Vector3f axis = new Vector3f(0F, 1F, 0F);
        Vector3f radians = new Vector3f(0.3F, 1.2F, -0.7F);

        check("toEulerZYXDegrees == decomposeEulerZYX",
            offsetL1(Matrices.toEulerZYXDegrees(q), QuaternionMath.decomposeEulerZYX(q)) < 1.0E-4F);
        check("toQuaternionZYXDegrees == composeFromEulerZYX",
            distanceDeg(Matrices.toQuaternionZYXDegrees(12F, 130F, -47F), q) < 1.0E-3F);
        check("toLocalRotationZYXRadians == composeFromEulerZYXRadians",
            distanceDeg(Matrices.toLocalRotationZYXRadians(radians),
                QuaternionMath.composeFromEulerZYXRadians(radians.x, radians.y, radians.z)) < 1.0E-3F);
        check("twistAbout == extractTwistComponent",
            distanceDeg(Matrices.twistAbout(q, axis), QuaternionMath.extractTwistComponent(q, axis)) < 1.0E-3F);
    }

    private static float offsetL1(Vector3f a, Vector3f b)
    {
        return Math.abs(a.x - b.x) + Math.abs(a.y - b.y) + Math.abs(a.z - b.z);
    }

    /**
     * The angle between two orientations, in degrees, sign-folded so {@code q}
     * and {@code -q} read as equal.
     *
     * <p>Through {@code atan2} of the vector part rather than {@code acos(w)}:
     * near identity a float {@code acos} bottoms out around 0.04°, which is
     * larger than every error measured here.
     */
    private static float distanceDeg(Quaternionf a, Quaternionf b)
    {
        Quaternionf d = new Quaternionf(a).normalize().invert().mul(new Quaternionf(b).normalize());
        double vector = Math.sqrt((double) d.x * d.x + (double) d.y * d.y + (double) d.z * d.z);

        return (float) Math.toDegrees(2.0 * Math.atan2(vector, Math.abs(d.w)));
    }

    private static void check(String name, boolean passed)
    {
        System.out.println((passed ? "  PASS  " : "  FAIL  ") + name);

        if (!passed)
        {
            failures++;
        }
    }
}
