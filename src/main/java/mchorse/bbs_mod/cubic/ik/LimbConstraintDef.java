package mchorse.bbs_mod.cubic.ik;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Static limb IK constraint definitions keyed by tip bone on the model form,
 * plus the per-bone joint freedom the tree solver reads.
 */
public record LimbConstraintDef(List<Limb> limbs, Map<String, JointDoF> joints)
{
    public LimbConstraintDef
    {
        joints = joints == null ? Collections.emptyMap() : joints;
    }

    public LimbConstraintDef(List<Limb> limbs)
    {
        this(limbs, Collections.emptyMap());
    }

    public static final float DEFAULT_INFLUENCE = 1F;
    public static final String DEFAULT_POLE_BONE = "";
    public static final float DEFAULT_BEND_OFFSET = 0F;
    public static final float DEFAULT_FLEXIBILITY = 0.05F;
    public static final int DEFAULT_DEPTH = 0;
    public static final boolean DEFAULT_ORIENT_TIP = false;
    public static final boolean DEFAULT_EXTENSIBLE = false;
    public static final boolean DEFAULT_CLASSIC = false;

    /**
     * One IK constraint living on {@code tipBone}, reaching {@code controllerBone},
     * spanning {@code depth} bones up the hierarchy ({@code 0} = to the root).
     * Negative {@code depth} walks down deform children from {@code tipBone} instead
     * ({@code -N} = at most {@code N} bones on the limb, excluding parents).
     *
     * <p>With {@code classic} on, the limb is solved by the pre-redesign
     * position-level solver ({@link LimbConstraintDef} consumers call it the classic
     * path) instead of the channel-space tree: the old limb feel, at the cost of
     * per-bone joint freedom and of merging with other limbs. A classic limb that
     * OVERLAPS another limb falls back to the tree anyway — shared bones have to
     * negotiate, which the classic path cannot do.
     */
    public record Limb(String tipBone, String controllerBone, int depth, boolean poleEnabled, String poleBone, float bendOffset, float flexibility, float influence, boolean active, boolean orientTip, boolean extensible, boolean classic)
    {
        public Limb
        {
            tipBone = tipBone == null ? "" : tipBone;
            controllerBone = controllerBone == null ? "" : controllerBone;
            poleBone = poleBone == null ? "" : poleBone;
            /* Negative depth is meaningful: it walks down deform children instead of up
             * the parent chain (see class javadoc), so it must not be clamped away. */
            flexibility = clamp01(flexibility);
            influence = clamp01(influence);
        }

        public Limb(String tipBone, String controllerBone, int depth, boolean poleEnabled, String poleBone, float bendOffset, float flexibility, float influence, boolean active, boolean orientTip, boolean extensible)
        {
            this(tipBone, controllerBone, depth, poleEnabled, poleBone, bendOffset, flexibility, influence, active, orientTip, extensible, DEFAULT_CLASSIC);
        }

        static float clamp01(float value)
        {
            if (value < 0F)
            {
                return 0F;
            }

            return Math.min(value, 1F);
        }
    }

    /**
     * Per-bone joint freedom for the IK solve. Per axis: {@code lock} removes the
     * axis from the solve entirely (it stays frozen at its FK value, so an authored
     * twist survives); {@code limit} clamps the CHANNEL angle into [min, max]
     * degrees — the same numbers the animator sees on the rotation pads;
     * {@code stiffness} 0..1 makes the axis increasingly reluctant to move,
     * shifting the bend to freer joints.
     *
     * <p>One entry per bone of the MODEL, not per limb: a bone shared by several
     * limbs has one set of joints.
     *
     * <p>Distinct from {@code JointLimitConfig.JointLimit}, which is the older
     * shared constraint the physics solver reads too and which clamps the POSE
     * after the fact. Where a bone has both, this one wins for the IK solve.
     */
    public record JointDoF(boolean lockX, boolean lockY, boolean lockZ,
                           boolean limitX, float minX, float maxX,
                           boolean limitY, float minY, float maxY,
                           boolean limitZ, float minZ, float maxZ,
                           float stiffnessX, float stiffnessY, float stiffnessZ)
    {
        public static final float DEFAULT_MIN = -180F;
        public static final float DEFAULT_MAX = 180F;

        public static final JointDoF FREE = new JointDoF(false, false, false,
            false, DEFAULT_MIN, DEFAULT_MAX,
            false, DEFAULT_MIN, DEFAULT_MAX,
            false, DEFAULT_MIN, DEFAULT_MAX,
            0F, 0F, 0F);

        public JointDoF
        {
            stiffnessX = Limb.clamp01(stiffnessX);
            stiffnessY = Limb.clamp01(stiffnessY);
            stiffnessZ = Limb.clamp01(stiffnessZ);
        }

        /** A free joint carries no information and is not serialized. */
        public boolean isFree()
        {
            return !this.lockX && !this.lockY && !this.lockZ
                && !this.limitX && !this.limitY && !this.limitZ
                && this.stiffnessX <= 0F && this.stiffnessY <= 0F && this.stiffnessZ <= 0F;
        }
    }
}
