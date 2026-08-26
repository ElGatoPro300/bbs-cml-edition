package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.constraints.JointLimitConfig.JointLimit;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.ik.solver.IKJoint;
import mchorse.bbs_mod.cubic.ik.solver.IKTree;
import mchorse.bbs_mod.cubic.ik.solver.IKTreeSolver;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.cubic.render.BoneFrameCollector;
import mchorse.bbs_mod.cubic.render.CubicRenderer.PivotFrame;
import mchorse.bbs_mod.cubic.render.SolvedPoseApplicator;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.joml.QuaternionMath;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writes solved limb poses onto cubic / BOBJ skeletons.
 *
 * <p>Two solvers live here. The CORE path runs cubic limbs through the
 * channel-space tree solver ({@link IKTree} / {@link IKTreeSolver}): limbs
 * whose bones overlap merge into one tree and negotiate between their goals,
 * disjoint limbs solve independently ancestor-first, and each bone's solved
 * ZYX channel angles are written to its {@code orient} blended against the FK
 * base by the limb's influence. The channels themselves are never touched —
 * they stay the read-only FK truth, and the solve STARTS from them, so an
 * authored twist survives by construction.
 *
 * <p>The CLASSIC path ({@link #applyChain}) is the pre-tree position-level
 * solver — {@link LimbResolver} plus parallel-transport orientations. It still
 * carries every limb the tree cannot: BOBJ skeletons, and cubic limbs too
 * short to direct a tree (fewer than two directed bones — the negative-depth
 * Mine-imator auto-limbs land here).
 */
public final class SkeletonPoseWriter
{
    public static final int MAX_ITERATIONS = 12;
    public static final float TOLERANCE = 1.0e-4f;
    private static final float EPS = 1.0e-6f;

    private SkeletonPoseWriter()
    {
    }

    public static void apply(IModel model, List<LimbConstraintCompiler.CompiledLimb> limbs, Map<String, Vector3f> controllerTargets, Map<String, Vector3f> poleTargets, Map<String, Float> targetWeights, Map<String, Float> poleWeights, Map<String, LimbDynamicParams> controlOverrides, Map<String, JointLimit> boneLimits)
    {
        apply(model, limbs, null, controllerTargets, poleTargets, targetWeights, poleWeights, controlOverrides, boneLimits, null, null);
    }

    public static void apply(IModel model, List<LimbConstraintCompiler.CompiledLimb> limbs, Map<String, LimbConstraintDef.JointDoF> jointDoF, Map<String, Vector3f> controllerTargets, Map<String, Vector3f> poleTargets, Map<String, Float> targetWeights, Map<String, Float> poleWeights, Map<String, LimbDynamicParams> controlOverrides, Map<String, JointLimit> boneLimits, Map<String, Quaternionf> tipRotations, Map<String, Float> tipRotationWeights)
    {
        if (model == null || limbs == null || limbs.isEmpty())
        {
            return;
        }

        /* Ancestor limbs (shallower root) first, and frames re-collected per group,
         * so a child limb (an arm) sees the pose its parent limb (the body) already
         * produced and rides along with it. */
        List<LimbConstraintCompiler.CompiledLimb> ordered = new ArrayList<>(limbs);

        ordered.sort(Comparator.comparingInt((LimbConstraintCompiler.CompiledLimb limb) -> rootDepth(model, limb)));

        /* OVERLAPPING limbs merge into one tree and solve together — shared bones
         * negotiate between the goals. Disjoint limbs stay independent solves. */
        for (List<LimbConstraintCompiler.CompiledLimb> group : groupOverlapping(ordered))
        {
            Set<String> wanted = new HashSet<>();

            for (LimbConstraintCompiler.CompiledLimb limb : group)
            {
                wanted.add(limb.controllerBone());
                wanted.addAll(limb.chainRootToEffector());

                String chainRoot = limb.chainRootToEffector().isEmpty() ? null : limb.chainRootToEffector().get(0);

                if (chainRoot != null)
                {
                    String anchorParent = model.getParentGroupKey(chainRoot);

                    if (anchorParent != null && !anchorParent.isEmpty())
                    {
                        wanted.add(anchorParent);
                    }
                }

                if (limb.poleBone() != null && !limb.poleBone().isEmpty())
                {
                    wanted.add(limb.poleBone());
                }
            }

            /* Whole group or nothing: the tree path and the classic path write the
             * same bones from incompatible models, so a group with one limb the tree
             * cannot direct falls back entirely rather than interleaving the two.
             *
             * A `classic` limb standing ALONE keeps the pre-redesign solver. Overlapping
             * another limb it joins the tree regardless: shared bones have to negotiate,
             * which the classic path cannot do. */
            if (treeEligible(model, group) && !(group.size() == 1 && group.get(0).classic()))
            {
                Map<String, PivotFrame> frames = new HashMap<>(wanted.size() * 2);

                BoneFrameCollector.collect(model, wanted, frames, null, true);
                IKLog.path(group.get(0).tipBone() + (group.size() > 1 ? " (+" + (group.size() - 1) + " merged)" : ""), "tree solver");
                applyGroup(model, group, frames, jointDoF, controllerTargets, poleTargets, targetWeights, poleWeights, controlOverrides, boneLimits, tipRotations);

                continue;
            }

            IKLog.path(group.get(0).tipBone() + (group.size() > 1 ? " (+" + (group.size() - 1) + " more)" : ""),
                !(model instanceof Model) ? "classic — not a cubic model"
                    : group.size() == 1 && group.get(0).classic() ? "classic — the limb asks for it"
                    : "classic — a limb has fewer than 2 directed bones");

            /* The classic path negotiates nothing, so overlapping limbs still hand
             * off through the pose: each one re-collects, seeing what the previous
             * one wrote — the behaviour it has always had. */
            for (LimbConstraintCompiler.CompiledLimb limb : group)
            {
                Map<String, PivotFrame> frames = new HashMap<>(wanted.size() * 2);

                BoneFrameCollector.collect(model, wanted, frames, null, true);
                applyChain(model, limb, frames, controllerTargets, poleTargets, targetWeights, poleWeights, controlOverrides, boneLimits, tipRotations, tipRotationWeights);
            }
        }
    }

    /**
     * Whether every limb of the group can be directed by the channel-space tree.
     * Cubic only for now — BOBJ keeps the classic writer — and every limb must
     * leave at least two DIRECTED bones after the auto-tail trim, since the tree
     * needs a joint to turn and a bone to carry the effector.
     */
    private static boolean treeEligible(IModel model, List<LimbConstraintCompiler.CompiledLimb> group)
    {
        if (!(model instanceof Model))
        {
            return false;
        }

        for (LimbConstraintCompiler.CompiledLimb limb : group)
        {
            List<String> ids = limb.chainRootToEffector();
            String tailId = limb.orientTip() ? autoTailId(model, ids) : null;

            if ((tailId == null ? ids.size() : ids.size() - 1) < 2)
            {
                return false;
            }
        }

        return true;
    }

    /**
     * Buckets the (ancestor-first ordered) limbs into groups of transitively
     * overlapping bone sets. Group order follows the order of each group's first
     * limb, so ancestor groups still apply first.
     */
    private static List<List<LimbConstraintCompiler.CompiledLimb>> groupOverlapping(List<LimbConstraintCompiler.CompiledLimb> ordered)
    {
        List<List<LimbConstraintCompiler.CompiledLimb>> groups = new ArrayList<>();
        List<Set<String>> groupBones = new ArrayList<>();

        for (LimbConstraintCompiler.CompiledLimb limb : ordered)
        {
            List<Integer> touching = new ArrayList<>();

            for (int g = 0; g < groups.size(); g++)
            {
                for (String bone : limb.chainRootToEffector())
                {
                    if (groupBones.get(g).contains(bone))
                    {
                        touching.add(g);
                        break;
                    }
                }
            }

            if (touching.isEmpty())
            {
                List<LimbConstraintCompiler.CompiledLimb> group = new ArrayList<>();

                group.add(limb);
                groups.add(group);
                groupBones.add(new HashSet<>(limb.chainRootToEffector()));

                continue;
            }

            /* Merge every touched group into the first one, then add the limb. */
            int first = touching.get(0);

            for (int t = touching.size() - 1; t >= 1; t--)
            {
                int g = touching.get(t);

                groups.get(first).addAll(groups.get(g));
                groupBones.get(first).addAll(groupBones.get(g));
                groups.remove(g);
                groupBones.remove(g);
            }

            groups.get(first).add(limb);
            groupBones.get(first).addAll(limb.chainRootToEffector());
        }

        return groups;
    }

    /** Depth of the limb's root bone from the model root, for ancestor-first ordering. */
    private static int rootDepth(IModel model, LimbConstraintCompiler.CompiledLimb limb)
    {
        List<String> ids = limb.chainRootToEffector();

        return depthOf(model, ids.isEmpty() ? limb.tipBone() : ids.get(0));
    }

    /** Parent-walk depth of a bone from the model root. */
    private static int depthOf(IModel model, String bone)
    {
        String group = bone;
        int depth = 0;

        while (group != null && !group.isEmpty() && depth < 256)
        {
            String parent = model.getParentGroupKey(group);

            if (parent == null || parent.equals(group))
            {
                break;
            }

            group = parent;
            depth++;
        }

        return depth;
    }

    /**
     * The CLASSIC path: one limb solved at position level by {@link LimbResolver},
     * then written with parallel-transported orientations. Carries BOBJ skeletons
     * and cubic limbs the tree cannot direct; see {@link #treeEligible}.
     */
    private static void applyChain(IModel model, LimbConstraintCompiler.CompiledLimb limb, Map<String, PivotFrame> frames, Map<String, Vector3f> controllerTargets, Map<String, Vector3f> poleTargets, Map<String, Float> targetWeights, Map<String, Float> poleWeights, Map<String, LimbDynamicParams> controlOverrides, Map<String, JointLimit> boneLimits, Map<String, Quaternionf> tipRotations, Map<String, Float> tipRotationWeights)
    {
        LimbDynamicParams control = controlOverrides == null ? null : controlOverrides.get(limb.tipBone());

        if (control != null && !control.active)
        {
            return;
        }

        boolean usePole = control != null ? control.usePole : limb.poleEnabled();
        float flexibility = control != null ? control.flexibility : limb.flexibility();
        float influence = control != null ? control.influence : limb.influence();
        boolean extensible = limb.extensible();
        float bendOffsetRad = (float) Math.toRadians(control != null ? control.bendOffset : limb.bendOffset());

        if (influence <= 0F)
        {
            return;
        }

        PivotFrame targetFrame = frames.get(limb.controllerBone());

        if (targetFrame == null)
        {
            return;
        }

        List<String> chainIds = limb.chainRootToEffector();
        boolean tipRotation = limb.orientTip();
        String tailId = tipRotation ? autoTailId(model, chainIds) : null;
        List<String> workIds = tailId == null ? chainIds : chainIds.subList(0, chainIds.size() - 1);
        List<Vector3f> currentPositions = new ArrayList<>(workIds.size() + 1);
        Quaternionf rootParentRotation = null;
        String chainRootId = workIds.isEmpty() ? null : workIds.get(0);
        String anchorParentId = chainRootId == null ? null : model.getParentGroupKey(chainRootId);

        if (workIds.size() == 1 && anchorParentId != null && !anchorParentId.isEmpty())
        {
            PivotFrame anchorFrame = frames.get(anchorParentId);

            if (anchorFrame == null)
            {
                return;
            }

            currentPositions.add(new Vector3f(anchorFrame.position()));
            rootParentRotation = new Quaternionf(anchorFrame.parentRotation());
        }

        for (String id : workIds)
        {
            PivotFrame frame = frames.get(id);

            if (frame == null)
            {
                return;
            }

            currentPositions.add(new Vector3f(frame.position()));

            if (rootParentRotation == null)
            {
                rootParentRotation = new Quaternionf(frame.parentRotation());
            }
        }

        if (rootParentRotation == null)
        {
            return;
        }

        Vector3f override = controllerTargets == null ? null : controllerTargets.get(limb.controllerBone());
        Vector3f target = new Vector3f(targetFrame.position());

        if (override != null)
        {
            target.lerp(override, weightOf(targetWeights, limb.controllerBone()));
        }

        Quaternionf tipTarget = resolveTipTarget(limb, tipRotations, tipRotation, targetFrame);

        if (tailId != null && tipTarget != null)
        {
            shiftTargetForTail(target, tipTarget, workIds.get(workIds.size() - 1), tailId, frames);
        }

        Vector3f polePoint = resolvePolePoint(usePole, limb.poleBone(), frames, poleTargets, poleWeights);
        LimbResolver.Limit[] limits = buildLimits(model, workIds, boneLimits);
        Vector3f restHinge = restBendNormal(model, workIds, rootParentRotation);
        Vector3f bendNormal = new Vector3f();
        boolean invertBend = model instanceof BOBJModel;
        List<Vector3f> solved = LimbResolver.resolve(currentPositions, target, usePole, polePoint, bendOffsetRad, flexibility, MAX_ITERATIONS, TOLERANCE, limits, limits == null ? null : rootParentRotation, restHinge, bendNormal, invertBend);

        Vector3f bendSeed = bendNormal.lengthSquared() < EPS * EPS ? null : bendNormal;
        Vector3f stretchGap = null;

        if (extensible && solved.size() >= 3)
        {
            Vector3f gap = new Vector3f(target).sub(solved.get(solved.size() - 1));

            if (gap.lengthSquared() > EPS * EPS)
            {
                stretchGap = gap.mul(influence);
            }
        }

        if (model instanceof Model cubic && workIds.size() >= 3)
        {
            writeOrientations(cubic, workIds, solved, rootParentRotation, influence, tipTarget, stretchGap, bendSeed);
        }
        else if (model instanceof BOBJModel bobj && workIds.size() >= 3)
        {
            writeOrientationsBobj(bobj, workIds, solved, rootParentRotation, influence, tipTarget, stretchGap, bendSeed);
        }
        else
        {
            Vector3f[] solvedArray = solved.toArray(new Vector3f[solved.size()]);

            SolvedPoseApplicator.applyWeightedRotations(model, rootParentRotation, workIds, solvedArray, influence);
        }
    }

    public static void writeOrientations(Model model, List<String> chainIds, List<Vector3f> solved, Quaternionf rootParentRotation, float influence, Quaternionf tipTarget, Vector3f stretchGap, Vector3f bendSeed)
    {
        int bones = chainIds.size() - 1;
        Vector3f[] restDir = new Vector3f[bones];
        Vector3f[] segWorld = new Vector3f[bones];

        for (int i = 0; i < bones; i++)
        {
            Vector3f seg = new Vector3f(solved.get(i + 1)).sub(solved.get(i));

            restDir[i] = restDirection(model, chainIds, i);

            if (restDir[i] == null || seg.lengthSquared() < EPS * EPS)
            {
                return;
            }

            segWorld[i] = seg.normalize();
        }

        int reach = stretchGap == null ? -1 : lastGeometryIndex(model, chainIds);
        float reachTotal = 0F;

        for (int i = 0; i < reach; i++)
        {
            reachTotal += solved.get(i).distance(solved.get(i + 1));
        }

        boolean doStretch = stretchGap != null && reach >= 1 && reachTotal > EPS;
        Vector3f[] restNormal = transportNormals(restDir);
        Vector3f[] solvedNormal = transportNormals(segWorld, bendSeed);
        Quaternionf parentWorld = new Quaternionf(rootParentRotation);

        for (int i = 0; i < bones; i++)
        {
            ModelGroup bone = model.getGroup(chainIds.get(i));

            if (bone == null)
            {
                return;
            }

            Quaternionf invParent = new Quaternionf(parentWorld).conjugate();
            Vector3f segLocal = invParent.transform(new Vector3f(segWorld[i]));
            Vector3f normalLocal = invParent.transform(new Vector3f(solvedNormal[i]));
            Quaternionf localRot = QuaternionMath.buildOrientedFrame(restDir[i], restNormal[i], segLocal, normalLocal);
            Quaternionf oriented = influence >= 1F - EPS ? new Quaternionf(localRot) : fkLocal(bone).slerp(localRot, influence);

            bone.orient = oriented;

            if (doStretch && i >= 1 && i <= reach)
            {
                bone.offset = stretchOffset(stretchGap, solved.get(i - 1).distance(solved.get(i)), reachTotal, parentWorld);
            }

            parentWorld.mul(oriented);
        }

        ModelGroup tip = model.getGroup(chainIds.get(chainIds.size() - 1));

        if (tip == null)
        {
            return;
        }

        if (doStretch && bones <= reach)
        {
            tip.offset = stretchOffset(stretchGap, solved.get(bones - 1).distance(solved.get(bones)), reachTotal, parentWorld);
        }

        if (tipTarget != null)
        {
            Quaternionf tipLocal = new Quaternionf(parentWorld).conjugate().mul(tipTarget);

            tip.orient = influence >= 1F - EPS ? tipLocal : fkLocal(tip).slerp(tipLocal, influence);
        }
    }

    private static Quaternionf resolveTipTarget(LimbConstraintCompiler.CompiledLimb limb, Map<String, Quaternionf> tipRotations, boolean orientTip, PivotFrame targetFrame)
    {
        Quaternionf override = tipRotations == null ? null : tipRotations.get(limb.tipBone());

        if (override != null)
        {
            return new Quaternionf(override);
        }

        if (orientTip && targetFrame.worldRotation() != null)
        {
            return new Quaternionf(targetFrame.worldRotation());
        }

        return null;
    }

    private static String autoTailId(IModel model, List<String> chainIds)
    {
        if (chainIds.size() < 4 || !(model instanceof Model cubic))
        {
            return null;
        }

        String lastId = chainIds.get(chainIds.size() - 1);
        ModelGroup last = cubic.getGroup(lastId);

        if (last == null || !last.cubes.isEmpty() || !last.meshes.isEmpty() || !last.children.isEmpty())
        {
            return null;
        }

        return lastId;
    }

    private static void shiftTargetForTail(Vector3f target, Quaternionf tipTarget, String effectorId, String tailId, Map<String, PivotFrame> frames)
    {
        PivotFrame eff = frames.get(effectorId);
        PivotFrame tail = frames.get(tailId);

        if (eff == null || tail == null || eff.worldRotation() == null)
        {
            return;
        }

        Vector3f offsetLocal = new Quaternionf(eff.worldRotation()).conjugate().transform(new Vector3f(tail.position()).sub(eff.position()));
        Vector3f shift = new Quaternionf(tipTarget).transform(offsetLocal);

        target.sub(shift);
    }

    private static Vector3f stretchOffset(Vector3f gap, float segLength, float total, Quaternionf parentWorld)
    {
        Vector3f share = new Vector3f(gap).mul(segLength / total);

        return new Quaternionf(parentWorld).conjugate().transform(share);
    }

    private static int lastGeometryIndex(Model model, List<String> chainIds)
    {
        for (int i = chainIds.size() - 1; i >= 0; i--)
        {
            ModelGroup bone = model.getGroup(chainIds.get(i));

            if (bone != null && (!bone.cubes.isEmpty() || !bone.meshes.isEmpty()))
            {
                return i;
            }
        }

        return chainIds.size() - 1;
    }

    private static Vector3f[] transportNormals(Vector3f[] dirs)
    {
        return transportNormals(dirs, null);
    }

    private static Vector3f[] transportNormals(Vector3f[] dirs, Vector3f seedHint)
    {
        int m = dirs.length;
        Vector3f[] normals = new Vector3f[m];
        Vector3f seed = m >= 2 ? new Vector3f(dirs[0]).cross(dirs[1]) : new Vector3f();

        if (seed.lengthSquared() < 1.0e-10f)
        {
            Vector3f hint = seedHint == null ? null : perpendicularTo(seedHint, dirs[0]);

            normals[0] = hint != null ? hint : stablePerpendicular(dirs[0]);
        }
        else
        {
            normals[0] = seed.normalize();
        }

        for (int i = 1; i < m; i++)
        {
            Vector3f n = new Quaternionf().rotationTo(dirs[i - 1], dirs[i]).transform(new Vector3f(normals[i - 1]));

            normals[i] = n.normalize();
        }

        return normals;
    }

    private static Vector3f perpendicularTo(Vector3f v, Vector3f axis)
    {
        Vector3f out = new Vector3f(v);
        float dot = out.dot(axis);

        out.x -= axis.x * dot;
        out.y -= axis.y * dot;
        out.z -= axis.z * dot;

        return out.lengthSquared() < EPS * EPS ? null : out.normalize();
    }

    private static Quaternionf fkLocal(ModelGroup bone)
    {
        Vector3f r = bone.current.rotate;

        return QuaternionMath.composeFromEulerZYX(r.x, r.y, r.z);
    }

    private static void writeOrientationsBobj(BOBJModel model, List<String> chainIds, List<Vector3f> solved, Quaternionf rootParentRotation, float influence, Quaternionf tipTarget, Vector3f stretchGap, Vector3f bendSeed)
    {
        int bones = chainIds.size() - 1;
        Map<String, BOBJBone> bonesMap = model.getArmature().bones;
        BOBJBone[] chainBones = new BOBJBone[bones];
        Vector3f[] restDir = new Vector3f[bones];
        Quaternionf[] relRot = new Quaternionf[bones];
        Vector3f[] segWorld = new Vector3f[bones];

        for (int i = 0; i < bones; i++)
        {
            BOBJBone bone = bonesMap.get(chainIds.get(i));
            Vector3f seg = new Vector3f(solved.get(i + 1)).sub(solved.get(i));

            restDir[i] = restDirection(model, chainIds, i);

            if (bone == null || restDir[i] == null || seg.lengthSquared() < EPS * EPS)
            {
                return;
            }

            chainBones[i] = bone;
            relRot[i] = bone.relBoneMat.getNormalizedRotation(new Quaternionf());
            segWorld[i] = seg.normalize();
        }

        Quaternionf[] restFrame = new Quaternionf[bones];

        restFrame[0] = new Quaternionf(rootParentRotation);

        for (int i = 1; i < bones; i++)
        {
            restFrame[i] = new Quaternionf(restFrame[i - 1]).mul(relRot[i]);
        }

        Vector3f[] restDirWorld = new Vector3f[bones];

        for (int i = 0; i < bones; i++)
        {
            restDirWorld[i] = restFrame[i].transform(new Vector3f(restDir[i]));
        }

        Vector3f[] restNormalWorld = transportNormals(restDirWorld);
        Vector3f[] solvedNormalWorld = transportNormals(segWorld, bendSeed);
        Quaternionf originRot = new Quaternionf(rootParentRotation);

        for (int i = 0; i < bones; i++)
        {
            Quaternionf invOrigin = new Quaternionf(originRot).conjugate();
            Vector3f segLocal = invOrigin.transform(new Vector3f(segWorld[i]));
            Vector3f normalLocal = invOrigin.transform(new Vector3f(solvedNormalWorld[i]));
            Vector3f restNormalLocal = new Quaternionf(restFrame[i]).conjugate().transform(new Vector3f(restNormalWorld[i]));
            Quaternionf localRot = QuaternionMath.buildOrientedFrameDirect(restDir[i], restNormalLocal, segLocal, normalLocal);
            Quaternionf oriented = influence >= 1F - EPS ? new Quaternionf(localRot) : bobjFkLocal(chainBones[i]).slerp(localRot, influence);

            chainBones[i].orient = oriented;

            if (i + 1 < bones)
            {
                originRot.mul(oriented).mul(relRot[i + 1]);
            }
        }

        if (tipTarget != null)
        {
            BOBJBone tip = bonesMap.get(chainIds.get(chainIds.size() - 1));

            if (tip != null)
            {
                Quaternionf tipRelRot = tip.relBoneMat.getNormalizedRotation(new Quaternionf());
                Quaternionf tipParent = new Quaternionf(originRot).mul(chainBones[bones - 1].orient).mul(tipRelRot);
                Quaternionf tipLocal = tipParent.conjugate().mul(tipTarget);

                tip.orient = influence >= 1F - EPS ? new Quaternionf(tipLocal) : bobjFkLocal(tip).slerp(tipLocal, influence);
            }
        }

        if (stretchGap != null)
        {
            stretchBobj(model, bonesMap, chainIds, solved, stretchGap);
        }
    }

    /**
     * Tip-follows-target for the euler BOBJ path ({@code workIds.size() < 4}), matching
     * the tail of {@link #writeOrientationsBobj}.
     */
    private static void applyBobjTipOrient(BOBJModel model, List<String> chainIds, Quaternionf rootParentRotation, float influence, Quaternionf tipTarget)
    {
        int bones = chainIds.size() - 1;

        if (bones < 1)
        {
            return;
        }

        Map<String, BOBJBone> bonesMap = model.getArmature().bones;
        BOBJBone[] chainBones = new BOBJBone[bones];
        Quaternionf[] relRot = new Quaternionf[bones];

        for (int i = 0; i < bones; i++)
        {
            BOBJBone bone = bonesMap.get(chainIds.get(i));

            if (bone == null)
            {
                return;
            }

            chainBones[i] = bone;
            relRot[i] = bone.relBoneMat.getNormalizedRotation(new Quaternionf());
        }

        Quaternionf originRot = new Quaternionf(rootParentRotation);

        for (int i = 0; i < bones - 1; i++)
        {
            Quaternionf oriented = chainBones[i].orient != null ? new Quaternionf(chainBones[i].orient) : bobjFkLocal(chainBones[i]);

            originRot.mul(oriented).mul(relRot[i + 1]);
        }

        BOBJBone tip = bonesMap.get(chainIds.get(chainIds.size() - 1));

        if (tip == null)
        {
            return;
        }

        Quaternionf tipRelRot = tip.relBoneMat.getNormalizedRotation(new Quaternionf());
        Quaternionf lastOrient = chainBones[bones - 1].orient != null ? new Quaternionf(chainBones[bones - 1].orient) : bobjFkLocal(chainBones[bones - 1]);
        Quaternionf tipParent = new Quaternionf(originRot).mul(lastOrient).mul(tipRelRot);
        Quaternionf tipLocal = tipParent.conjugate().mul(tipTarget);

        tip.orient = influence >= 1F - EPS ? new Quaternionf(tipLocal) : bobjFkLocal(tip).slerp(tipLocal, influence);
    }

    private static void stretchBobj(BOBJModel model, Map<String, BOBJBone> bonesMap, List<String> chainIds, List<Vector3f> solved, Vector3f gap)
    {
        int joints = chainIds.size();
        int reach = lastInfluenceIndex(model, bonesMap, chainIds);
        float reachTotal = 0F;

        for (int i = 0; i < reach; i++)
        {
            reachTotal += solved.get(i).distance(solved.get(i + 1));
        }

        if (reach < 1 || reachTotal <= EPS)
        {
            return;
        }

        float arclen = 0F;

        for (int i = 1; i < joints; i++)
        {
            arclen += solved.get(i - 1).distance(solved.get(i));

            BOBJBone bone = bonesMap.get(chainIds.get(i));

            if (bone != null)
            {
                bone.offset = new Vector3f(gap).mul(Math.min(arclen / reachTotal, 1F));
            }
        }
    }

    private static int lastInfluenceIndex(BOBJModel model, Map<String, BOBJBone> bonesMap, List<String> chainIds)
    {
        for (int i = chainIds.size() - 1; i >= 0; i--)
        {
            BOBJBone bone = bonesMap.get(chainIds.get(i));

            if (bone != null && model.boneDeformsMesh(bone.index))
            {
                return i;
            }
        }

        return chainIds.size() - 1;
    }

    private static Quaternionf bobjFkLocal(BOBJBone bone)
    {
        Vector3f r = bone.transform.rotate;

        return new Quaternionf().rotationZYX(r.z, r.y, r.x);
    }

    private static Vector3f stablePerpendicular(Vector3f dir)
    {
        Vector3f perp = new Vector3f(dir).cross(0F, 0F, 1F);

        if (perp.lengthSquared() < EPS * EPS)
        {
            perp.set(dir).cross(0F, 1F, 0F);
        }

        return perp.normalize();
    }

    private static Vector3f resolvePolePoint(boolean usePole, String poleBone, Map<String, PivotFrame> frames, Map<String, Vector3f> poleTargets, Map<String, Float> poleWeights)
    {
        if (!usePole || poleBone == null || poleBone.isEmpty())
        {
            return null;
        }

        Vector3f override = poleTargets == null ? null : poleTargets.get(poleBone);
        PivotFrame frame = frames.get(poleBone);
        Vector3f config = frame == null ? null : new Vector3f(frame.position());

        if (override == null)
        {
            return config;
        }

        return config == null ? new Vector3f(override) : config.lerp(override, weightOf(poleWeights, poleBone));
    }

    private static float weightOf(Map<String, Float> weights, String id)
    {
        return weights == null ? 1F : weights.getOrDefault(id, 1F);
    }

    private static LimbResolver.Limit[] buildLimits(IModel model, List<String> chainIds, Map<String, JointLimit> boneLimits)
    {
        if (boneLimits == null || boneLimits.isEmpty())
        {
            return null;
        }

        int directed = chainIds.size() - 1;

        if (directed < 1)
        {
            return null;
        }

        boolean any = false;

        for (int i = 0; i < directed; i++)
        {
            JointLimit c = boneLimits.get(chainIds.get(i));

            if (c != null && c.active())
            {
                any = true;
                break;
            }
        }

        if (!any)
        {
            return null;
        }

        LimbResolver.Limit[] limits = new LimbResolver.Limit[directed];

        for (int i = 0; i < directed; i++)
        {
            String id = chainIds.get(i);
            Vector3f restDir = restDirection(model, chainIds, i);

            if (restDir == null)
            {
                return null;
            }

            JointLimit c = boneLimits.get(id);
            boolean enabled = c != null && c.active();

            limits[i] = enabled
                ? new LimbResolver.Limit(true, restDir, c.minX(), c.minY(), c.minZ(), c.maxX(), c.maxY(), c.maxZ())
                : new LimbResolver.Limit(false, restDir, 0F, 0F, 0F, 0F, 0F, 0F);
        }

        return limits;
    }

    private static Vector3f restDirection(IModel model, List<String> chainIds, int i)
    {
        String id = chainIds.get(i);
        String childId = chainIds.get(i + 1);

        if (model instanceof Model cubic)
        {
            ModelGroup bone = cubic.getGroup(id);
            ModelGroup child = cubic.getGroup(childId);

            if (bone == null || child == null)
            {
                return null;
            }

            return normalizeRest(new Vector3f(child.initial.translate).sub(bone.initial.translate));
        }

        if (model instanceof BOBJModel bobj)
        {
            BOBJBone bone = bobj.getArmature().bones.get(id);
            BOBJBone child = bobj.getArmature().bones.get(childId);

            if (bone == null)
            {
                return null;
            }

            return normalizeRest(SolvedPoseApplicator.getBobjRestDirection(bobj, bone, child, chainIds, i));
        }

        return null;
    }

    private static Vector3f restBendNormal(IModel model, List<String> chainIds, Quaternionf rootParentRotation)
    {
        if (chainIds.size() < 3)
        {
            return null;
        }

        Vector3f a = restDirection(model, chainIds, 0);
        Vector3f b = restDirection(model, chainIds, 1);

        if (a == null || b == null)
        {
            return null;
        }

        /* BOBJ rest directions are bone-local (relBoneMat translate); cubic ones are already
         * in model space. Match writeOrientationsBobj: lift BOBJ dirs into the root-parent
         * frame before the cross so the hinge is not mirrored. */
        if (model instanceof BOBJModel bobj)
        {
            Map<String, BOBJBone> bones = bobj.getArmature().bones;
            BOBJBone bone0 = bones.get(chainIds.get(0));
            BOBJBone bone1 = bones.get(chainIds.get(1));

            if (bone0 == null || bone1 == null)
            {
                return null;
            }

            Quaternionf frame0 = new Quaternionf(rootParentRotation);
            Quaternionf frame1 = new Quaternionf(frame0).mul(bone1.relBoneMat.getNormalizedRotation(new Quaternionf()));
            Vector3f aWorld = frame0.transform(new Vector3f(a));
            Vector3f bWorld = frame1.transform(new Vector3f(b));
            Vector3f normal = new Vector3f(aWorld).cross(bWorld);

            if (normal.lengthSquared() < EPS * EPS)
            {
                return null;
            }

            return normal.normalize();
        }

        Vector3f normal = new Vector3f(a).cross(b);

        if (normal.lengthSquared() < EPS * EPS)
        {
            return null;
        }

        return rootParentRotation.transform(normal.normalize());
    }

    private static Vector3f normalizeRest(Vector3f restDir)
    {
        if (restDir.lengthSquared() < 1.0e-12f)
        {
            restDir.set(0F, -1F, 0F);
        }

        restDir.normalize();

        return restDir;
    }

    /* ------------------------------------------------------------------ */
    /* The channel-space damped-least-squares solve over a merged tree     */
    /* ------------------------------------------------------------------ */

    /** A limb's per-frame solve inputs, resolved from config x film overrides x frames. */
    private record ResolvedLimb(LimbConstraintCompiler.CompiledLimb limb, List<String> workIds, Vector3f target, Quaternionf tipTarget, boolean pole, Vector3f polePoint, float bendOffset, float flexibility, float influence)
    {
    }

    /**
     * Resolves one limb's solve inputs for this frame: the film's overrides over
     * the config scalars, the (possibly faded) target and pole positions, the
     * auto-tail work ids and the tail-shifted target. Returns {@code null} when
     * the limb is off this frame — inactive, weightless, or its controller frame
     * is missing.
     */
    private static ResolvedLimb resolveLimb(IModel model, LimbConstraintCompiler.CompiledLimb limb, Map<String, PivotFrame> frames, Map<String, Vector3f> controllerTargets, Map<String, Vector3f> poleTargets, Map<String, Float> targetWeights, Map<String, Float> poleWeights, Map<String, LimbDynamicParams> controlOverrides, Map<String, Quaternionf> tipRotations)
    {
        LimbDynamicParams control = controlOverrides == null ? null : controlOverrides.get(limb.tipBone());

        if (control != null && !control.active)
        {
            return null;
        }

        boolean usePole = control != null ? control.usePole : limb.poleEnabled();
        float flexibility = control != null ? control.flexibility : limb.flexibility();
        float influence = control != null ? control.influence : limb.influence();
        float bendOffsetRad = (float) Math.toRadians(control != null ? control.bendOffset : limb.bendOffset());

        if (influence <= 0F)
        {
            return null;
        }

        PivotFrame targetFrame = frames.get(limb.controllerBone());

        if (targetFrame == null)
        {
            return null;
        }

        List<String> chainIds = limb.chainRootToEffector();

        /* Auto-tail (foot IK): with "orient tip" on, a chain ending in a bare marker
         * bone (no geometry, no children) treats that marker as the EFFECTOR's tail —
         * the bone before it becomes the orientable end, and the IK reaches the tail. */
        boolean tipRotation = limb.orientTip();
        String tailId = tipRotation ? autoTailId(model, chainIds) : null;
        List<String> workIds = tailId == null ? chainIds : chainIds.subList(0, chainIds.size() - 1);

        if (workIds.size() < 2)
        {
            return null;
        }

        Vector3f override = controllerTargets == null ? null : controllerTargets.get(limb.controllerBone());
        Vector3f target = new Vector3f(targetFrame.position());

        if (override != null)
        {
            target.lerp(override, weightOf(targetWeights, limb.controllerBone()));
        }

        Quaternionf tipTarget = resolveTipTarget(limb, tipRotations, tipRotation, targetFrame);

        /* Foot IK: back the reach off so the effector's TAIL (the marker), not its
         * pivot, lands on the target once the effector is turned to the controller. */
        if (tailId != null && tipTarget != null)
        {
            shiftTargetForTail(target, tipTarget, workIds.get(workIds.size() - 1), tailId, frames);
        }

        Vector3f polePoint = resolvePolePoint(usePole, limb.poleBone(), frames, poleTargets, poleWeights);

        return new ResolvedLimb(limb, workIds, target, tipTarget, usePole, polePoint, bendOffsetRad, flexibility, influence);
    }

    /**
     * The solve for one group of overlapping limbs: capture the union of their
     * directed bones into the channel-space tree ({@link IKTree}), one effector
     * per limb, soften each goal against its own limb's reach, run the DLS solve
     * (with the calibrated pole per limb and the tip orientation task), and write
     * each bone's solved local rotation to its {@code orient} blended against the
     * FK base by the limb's influence.
     */
    private static void applyGroup(IModel model, List<LimbConstraintCompiler.CompiledLimb> group, Map<String, PivotFrame> frames, Map<String, LimbConstraintDef.JointDoF> jointDoF, Map<String, Vector3f> controllerTargets, Map<String, Vector3f> poleTargets, Map<String, Float> targetWeights, Map<String, Float> poleWeights, Map<String, LimbDynamicParams> controlOverrides, Map<String, JointLimit> boneLimits, Map<String, Quaternionf> tipRotations)
    {
        List<ResolvedLimb> resolved = new ArrayList<>(group.size());

        for (LimbConstraintCompiler.CompiledLimb limb : group)
        {
            ResolvedLimb r = resolveLimb(model, limb, frames, controllerTargets, poleTargets, targetWeights, poleWeights, controlOverrides, tipRotations);

            if (r != null)
            {
                resolved.add(r);
            }
        }

        if (resolved.isEmpty())
        {
            return;
        }

        /* The tree's nodes: the union of every limb's DIRECTED bones (all work ids
         * but the last — the last is the effector bone, whose own angles move only
         * what hangs below it), parents-first. */
        LinkedHashSet<String> nodeSet = new LinkedHashSet<>();

        for (ResolvedLimb r : resolved)
        {
            nodeSet.addAll(r.workIds().subList(0, r.workIds().size() - 1));
        }

        List<String> nodes = new ArrayList<>(nodeSet);

        nodes.sort(Comparator.comparingInt((String bone) -> depthOf(model, bone)));

        IKTree tree = new IKTree(nodes.size(), resolved.size());
        Map<String, Integer> nodeIndex = new HashMap<>(nodes.size() * 2);

        for (int i = 0; i < nodes.size(); i++)
        {
            nodeIndex.put(nodes.get(i), i);
        }

        for (int i = 0; i < nodes.size(); i++)
        {
            PivotFrame frame = frames.get(nodes.get(i));

            if (frame == null)
            {
                return;
            }

            IKJoint joint = tree.joints[i];

            joint.startPosition.set(frame.position());
            joint.startWorldRotation.set(frame.worldRotation());
            tree.startParentRotation[i].set(frame.parentRotation());

            if (sourceAngles(model, nodes.get(i), joint.startAngles) == null)
            {
                return;
            }

            joint.angles.set(joint.startAngles);
            tree.parentIndex[i] = nearestAncestor(model, nodes.get(i), nodeIndex);

            /* The IK-only joint freedom wins where it exists; otherwise the older
             * shared angular constraint (which the physics solver reads too) still
             * clamps the solve, so a rig that only ever set those keeps working. */
            LimbConstraintDef.JointDoF dof = jointDoF == null ? null : jointDoF.get(nodes.get(i));

            if (dof != null)
            {
                applyDoF(joint, dof);
            }
            else
            {
                JointLimit limit = boneLimits == null ? null : boneLimits.get(nodes.get(i));

                if (limit != null && limit.active())
                {
                    applyJointLimit(joint, limit);
                }
            }
        }

        /* Effectors: one per limb, riding its last directed bone; each goal is
         * softened against its OWN limb's reach. Poles are per limb too. */
        IKTreeSolver.Pole[] poles = new IKTreeSolver.Pole[resolved.size()];

        for (int e = 0; e < resolved.size(); e++)
        {
            ResolvedLimb r = resolved.get(e);
            List<String> workIds = r.workIds();
            PivotFrame effectorFrame = frames.get(workIds.get(workIds.size() - 1));
            Integer lastJoint = nodeIndex.get(workIds.get(workIds.size() - 2));
            Integer rootJoint = nodeIndex.get(workIds.get(0));

            if (effectorFrame == null || lastJoint == null || rootJoint == null)
            {
                return;
            }

            IKTree.Effector effector = tree.effector(e, lastJoint);

            effector.startPosition.set(effectorFrame.position());
            effector.weight = r.influence();

            Vector3f rootPosition = tree.joints[rootJoint].startPosition;
            float reach = chainReach(tree, rootJoint, lastJoint, effector.startPosition);

            effector.goal.set(IKTreeSolver.softGoal(rootPosition, reach, r.target(), r.flexibility()));

            /* Orient tip, in-solver half: ask the limb to turn its LAST directed bone
             * so the tip, keeping its natural FK local pose, would already face the
             * controller — the limb shares the turn instead of the wrist absorbing it
             * all, and the exact tip snap after the solve has almost nothing left to
             * correct. One radian of orientation error is worth reach/pi length units. */
            if (r.tipTarget() != null)
            {
                ModelGroup tip = ((Model) model).getGroup(workIds.get(workIds.size() - 1));

                if (tip != null)
                {
                    effector.orientGoal = new Quaternionf(r.tipTarget()).mul(tip.evaluatedRotation().conjugate());
                    effector.orientWeight = reach / (float) Math.PI;
                }
            }

            Vector3f polePoint = r.polePoint();

            if (r.pole() && polePoint == null)
            {
                polePoint = restVirtualPole(model, workIds, tree.startParentRotation[rootJoint], rootPosition, reach);
            }

            Vector3f materialUp = polePoint == null ? null : poleMaterialUp(model, workIds, r.limb().poleBone(), tree.startParentRotation[rootJoint], tree.joints[rootJoint].startWorldRotation);

            poles[e] = polePoint == null || materialUp == null ? null : new IKTreeSolver.Pole(rootJoint, polePoint, r.bendOffset(), materialUp);
        }

        IKTreeSolver.solve(tree, poles, IKTreeSolver.Params.DEFAULT);

        for (int e = 0; e < resolved.size(); e++)
        {
            IKTree.Effector effector = tree.effectors[e];

            IKLog.solved(resolved.get(e).limb().tipBone(), fmt(effector.goal), fmt(effector.position), effector.goal.distance(effector.position));
        }

        writeTree(model, nodes, tree, resolved, frames);
    }

    /** The captured arc length root joint -> last joint -> effector point, along the tree. */
    private static float chainReach(IKTree tree, int rootJoint, int lastJoint, Vector3f effectorStart)
    {
        float total = effectorStart.distance(tree.joints[lastJoint].startPosition);
        int j = lastJoint;

        while (j != rootJoint && tree.parentIndex[j] >= 0)
        {
            int parent = tree.parentIndex[j];

            total += tree.joints[j].startPosition.distance(tree.joints[parent].startPosition);
            j = parent;
        }

        return total;
    }

    /** Index of the bone's nearest ancestor among the tree nodes; -1 when none. */
    private static int nearestAncestor(IModel model, String bone, Map<String, Integer> nodeIndex)
    {
        String group = model.getParentGroupKey(bone);
        int depth = 0;

        while (group != null && !group.isEmpty() && depth < 256)
        {
            Integer index = nodeIndex.get(group);

            if (index != null)
            {
                return index;
            }

            String parent = model.getParentGroupKey(group);

            if (parent == null || parent.equals(group))
            {
                break;
            }

            group = parent;
            depth++;
        }

        return -1;
    }

    /**
     * The bone's FK rotation as ZYX angles in radians — the solve's start value.
     * A plain euler bone reads its channels directly (cubic channels are degrees);
     * a bone carrying a composed {@code orient} (a layer stack) or a non-zero
     * {@code rotate2} decomposes its evaluated rotation compatibly against the
     * channels, so the start stays continuous with what the animator sees.
     * {@code null} when the bone does not exist.
     */
    private static Vector3f sourceAngles(IModel model, String id, Vector3f dest)
    {
        if (!(model instanceof Model cubic))
        {
            return null;
        }

        ModelGroup bone = cubic.getGroup(id);

        if (bone == null)
        {
            return null;
        }

        float toRad = (float) (Math.PI / 180.0);
        Vector3f channels = dest.set(bone.current.rotate).mul(toRad);
        Vector3f rotate2 = bone.current.rotate2;

        if (bone.orient == null && rotate2.x == 0F && rotate2.y == 0F && rotate2.z == 0F)
        {
            return channels;
        }

        return Matrices.toCompatibleEulerZYXRadians(bone.evaluatedRotation(), new Vector3f(channels), dest);
    }

    /** Copies the config's per-bone freedom onto a solver joint; limits are authored in degrees. */
    private static void applyDoF(IKJoint joint, LimbConstraintDef.JointDoF dof)
    {
        float toRad = (float) (Math.PI / 180.0);

        joint.locked[0] = dof.lockX();
        joint.locked[1] = dof.lockY();
        joint.locked[2] = dof.lockZ();

        joint.limited[0] = dof.limitX();
        joint.limited[1] = dof.limitY();
        joint.limited[2] = dof.limitZ();

        joint.limitMin[0] = dof.minX() * toRad;
        joint.limitMin[1] = dof.minY() * toRad;
        joint.limitMin[2] = dof.minZ() * toRad;

        joint.limitMax[0] = dof.maxX() * toRad;
        joint.limitMax[1] = dof.maxY() * toRad;
        joint.limitMax[2] = dof.maxZ() * toRad;

        joint.stiffness[0] = dof.stiffnessX();
        joint.stiffness[1] = dof.stiffnessY();
        joint.stiffness[2] = dof.stiffnessZ();
    }

    /**
     * Copies a bone's older shared angular limits onto a solver joint — the CHANNEL
     * angles the animator sees on the rotation pads, authored in degrees. That
     * config has no per-axis lock or stiffness, so the joint keeps the solver's
     * free defaults for those; a bone that wants them needs a {@code JointDoF}.
     */
    private static void applyJointLimit(IKJoint joint, JointLimit limit)
    {
        float toRad = (float) (Math.PI / 180.0);

        joint.limited[0] = true;
        joint.limited[1] = true;
        joint.limited[2] = true;

        joint.limitMin[0] = limit.minX() * toRad;
        joint.limitMin[1] = limit.minY() * toRad;
        joint.limitMin[2] = limit.minZ() * toRad;

        joint.limitMax[0] = limit.maxX() * toRad;
        joint.limitMax[1] = limit.maxY() * toRad;
        joint.limitMax[2] = limit.maxZ() * toRad;
    }

    /**
     * A limb's authored rest geometry: the root, first interior and effector rest
     * positions (absolute model rest space) plus the {@code lift} rotation folding
     * rest-space directions into the current pose.
     */
    private record RestChain(Vector3f root, Vector3f elbow, Vector3f effector, Quaternionf lift)
    {
    }

    /**
     * Loads a limb's rest geometry. Cubic rest geometry lives in the authored
     * pivots (absolute model coordinates), and the lift is the DELTA of the chain
     * root's current parent frame from its REST orientation — lifting by the raw
     * current parent frame would double-count any rest rotation the chain's
     * ancestors carry, tilting the result even in rest pose. {@code null} when the
     * limb is too short or a bone is missing.
     */
    private static RestChain restChain(IModel model, List<String> workIds, Quaternionf rootParentRotation)
    {
        if (workIds.size() < 3 || !(model instanceof Model cubic))
        {
            return null;
        }

        ModelGroup root = cubic.getGroup(workIds.get(0));
        ModelGroup elbow = cubic.getGroup(workIds.get(1));
        ModelGroup effector = cubic.getGroup(workIds.get(workIds.size() - 1));

        if (root == null || elbow == null || effector == null)
        {
            return null;
        }

        Quaternionf restParent = cubicRestParentRotation(cubic, workIds.get(0));

        return new RestChain(root.initial.translate, elbow.initial.translate, effector.initial.translate, new Quaternionf(rootParentRotation).mul(restParent.conjugate()));
    }

    /**
     * The rest-authored virtual pole point for a limb: the direction its first
     * interior pivot sticks out from the rest root-to-effector line — where the
     * model's own elbow/knee points — lifted into the world and placed a reach
     * away from the root. {@code null} when the limb is too short or authored dead
     * straight (no side to prefer).
     */
    private static Vector3f restVirtualPole(IModel model, List<String> workIds, Quaternionf rootParentRotation, Vector3f rootPosition, float reach)
    {
        RestChain rest = restChain(model, workIds, rootParentRotation);

        if (rest == null)
        {
            return null;
        }

        Vector3f axis = new Vector3f(rest.effector()).sub(rest.root());

        if (axis.lengthSquared() < EPS * EPS)
        {
            return null;
        }

        axis.normalize();

        Vector3f side = perpendicularTo(new Vector3f(rest.elbow()).sub(rest.root()), axis);

        if (side == null)
        {
            return null;
        }

        rest.lift().transform(side);

        return new Vector3f(rootPosition).fma(reach, side);
    }

    /**
     * The calibrated pole-plane anchor for a limb, in the ROOT joint's local
     * rotation frame. The plane is anchored to the root bone's own basis, so it
     * never degenerates however straight the limb solves; the knob is turned
     * automatically to ZERO TWIST IN REST POSE — the axis is the direction from
     * the rest chain axis to the POLE BONE's authored rest spot, and the limb's
     * bend offset then twists the plane from that zero. NEVER the rest bulge when
     * a pole bone exists: a limb bent only at its elbow bulges BACKWARD of its
     * root-to-tip chord even though it visibly bends forward, so calibrating on
     * the bulge would turn such an arm a permanent half-turn against its own pole.
     * Falls back to a deterministic rest-space perpendicular when everything above
     * sits on the chain axis.
     */
    private static Vector3f poleMaterialUp(IModel model, List<String> workIds, String poleBone, Quaternionf rootParentRotation, Quaternionf rootStartWorldRotation)
    {
        RestChain rest = restChain(model, workIds, rootParentRotation);

        if (rest == null)
        {
            return null;
        }

        Vector3f axis = new Vector3f(rest.effector()).sub(rest.root());

        if (axis.lengthSquared() < EPS * EPS)
        {
            return null;
        }

        axis.normalize();

        Vector3f poleRest = restPosition(model, poleBone);
        Vector3f side = poleRest == null ? null : perpendicularTo(new Vector3f(poleRest).sub(rest.root()), axis);

        if (side == null)
        {
            side = perpendicularTo(new Vector3f(rest.elbow()).sub(rest.root()), axis);
        }

        if (side == null)
        {
            /* Last resort: any fixed rest-space perpendicular anchors a stable plane
             * (world Z, falling back to world Y — the solver's own convention). */
            side = new Vector3f(axis).cross(0F, 0F, 1F);

            if (side.lengthSquared() < EPS * EPS)
            {
                side.set(axis).cross(0F, 1F, 0F);
            }

            side.normalize();
        }

        rest.lift().transform(side);

        return new Quaternionf(rootStartWorldRotation).conjugate().transform(side);
    }

    /** A bone's authored rest position in model rest space; {@code null} when absent. */
    private static Vector3f restPosition(IModel model, String id)
    {
        if (id == null || id.isEmpty() || !(model instanceof Model cubic))
        {
            return null;
        }

        ModelGroup bone = cubic.getGroup(id);

        return bone == null ? null : bone.initial.translate;
    }

    /**
     * The rest-pose world rotation of the chain root's PARENT — the product of
     * every ancestor's authored rest rotation ({@code initial.rotate}, degrees,
     * ZYX), in the same space {@link BoneFrameCollector} captures. Identity when
     * the root sits at the model top or its ancestors carry no rest rotation (the
     * common case, where the auto-pole lift is unchanged).
     */
    private static Quaternionf cubicRestParentRotation(Model cubic, String rootId)
    {
        Quaternionf rest = new Quaternionf();
        String id = cubic.getParentGroupKey(rootId);
        int guard = 0;

        while (id != null && !id.isEmpty() && guard++ < 256)
        {
            ModelGroup bone = cubic.getGroup(id);

            if (bone == null)
            {
                break;
            }

            /* World order is root-most first; walking up, each ancestor pre-multiplies. */
            rest = Matrices.toLocalRotationZYXDegrees(bone.initial.rotate).mul(rest);

            String parent = cubic.getParentGroupKey(id);

            if (parent == null || parent.equals(id))
            {
                break;
            }

            id = parent;
        }

        return rest;
    }

    /**
     * Writes the solved tree onto the bones: each node's local rotation is composed
     * from its solved channel angles and written raw to its {@code orient}, blended
     * against the FK base (the bone's evaluated rotation) by the strongest influence
     * of the limbs running through it. The blended world frames advance the same
     * rigid way the solve did — the frames the renderer establishes — so each limb's
     * tip target lands in the right frame at any influence.
     */
    private static void writeTree(IModel model, List<String> nodes, IKTree tree, List<ResolvedLimb> resolved, Map<String, PivotFrame> frames)
    {
        Model cubic = (Model) model;
        int n = nodes.size();
        float[] nodeWeight = new float[n];

        for (int e = 0; e < resolved.size(); e++)
        {
            float weight = resolved.get(e).influence();

            for (int i = 0; i < n; i++)
            {
                if (tree.moves(e, i))
                {
                    nodeWeight[i] = Math.max(nodeWeight[i], weight);
                }
            }
        }

        Quaternionf[] blendedWorld = new Quaternionf[n];
        Quaternionf[] blendedParentOf = new Quaternionf[n];

        for (int i = 0; i < n; i++)
        {
            ModelGroup bone = cubic.getGroup(nodes.get(i));

            if (bone == null)
            {
                return;
            }

            Quaternionf solvedLocal = Matrices.toLocalRotationZYXRadians(tree.joints[i].angles);
            Quaternionf applied = nodeWeight[i] >= 1F - EPS ? solvedLocal : bone.evaluatedRotation().slerp(solvedLocal, nodeWeight[i]);

            bone.orient = applied;

            /* Blended world walk, rigid-model style: the parent frame is the captured
             * one carried by how far the nearest captured ancestor's BLENDED world
             * rotation moved — the frame the renderer will actually establish. */
            int parent = tree.parentIndex[i];
            Quaternionf blendedParent;

            if (parent < 0)
            {
                blendedParent = new Quaternionf(tree.startParentRotation[i]);
            }
            else
            {
                Quaternionf delta = new Quaternionf(blendedWorld[parent]).mul(new Quaternionf(tree.joints[parent].startWorldRotation).conjugate());

                blendedParent = delta.mul(tree.startParentRotation[i]);
            }

            blendedParentOf[i] = new Quaternionf(blendedParent);
            blendedWorld[i] = blendedParent.mul(applied, new Quaternionf());
        }

        /* Orient tip: each limb's effector bone (not a solver node of its own limb)
         * copies the controller's world orientation, in its parent's BLENDED frame.
         * Written after the nodes, so on the rare rig where a tip doubles as another
         * limb's directed bone, the tip orientation wins. */
        for (ResolvedLimb r : resolved)
        {
            if (r.tipTarget() == null)
            {
                continue;
            }

            List<String> workIds = r.workIds();
            String tipId = workIds.get(workIds.size() - 1);
            PivotFrame tipFrame = frames.get(tipId);
            int lastJoint = indexOf(nodes, workIds.get(workIds.size() - 2));
            ModelGroup tip = cubic.getGroup(tipId);

            if (tipFrame == null || lastJoint < 0 || tip == null)
            {
                continue;
            }

            Quaternionf lastDelta = new Quaternionf(blendedWorld[lastJoint]).mul(new Quaternionf(tree.joints[lastJoint].startWorldRotation).conjugate());
            Quaternionf tipParent = lastDelta.mul(new Quaternionf(tipFrame.parentRotation()));
            Quaternionf tipLocal = tipParent.conjugate().mul(r.tipTarget());

            tip.orient = r.influence() >= 1F - EPS ? tipLocal : tip.evaluatedRotation().slerp(tipLocal, r.influence());
        }

        for (ResolvedLimb r : resolved)
        {
            if (r.limb().extensible())
            {
                stretchToTarget(cubic, nodes, tree, r, frames, blendedParentOf, blendedWorld);
            }
        }
    }

    /**
     * Telescopes a limb that came up short onto its controller: whatever gap the
     * rotation solve could not close is split among the limb's bones in proportion
     * to their lengths and written as per-bone translations, so every joint slides
     * out along the limb and the tip lands on the target. No bone is scaled — cubes
     * keep their proportions and their texels.
     *
     * <p>A post-process on purpose: the solve itself stays a pure rotation problem,
     * so nothing about a limb's bend, pole or limits changes when the box is
     * ticked. The gap is faded by the limb's influence. The share is distributed
     * only up to the last bone carrying GEOMETRY: a chain ending in a bare
     * end-marker (the auto-tail convention) would otherwise open its last seam
     * BEFORE the marker and leave the last visible bone short of the controller.
     */
    private static void stretchToTarget(Model cubic, List<String> nodes, IKTree tree, ResolvedLimb r, Map<String, PivotFrame> frames, Quaternionf[] blendedParentOf, Quaternionf[] blendedWorld)
    {
        List<String> workIds = r.workIds();
        int lastJoint = indexOf(nodes, workIds.get(workIds.size() - 2));
        int effectorIndex = -1;

        for (int e = 0; e < tree.effectors.length; e++)
        {
            if (tree.effectors[e].joint == lastJoint)
            {
                effectorIndex = e;
                break;
            }
        }

        if (effectorIndex < 0 || lastJoint < 0)
        {
            return;
        }

        /* The effector bone is not a solver node, so its parent frame is not in the
         * blended walk: it is its CAPTURED parent frame carried by how far the last
         * directed bone turned — the same advance the tip snap makes. Using the
         * captured frame raw would send its share of the gap off in the pre-solve
         * direction, which on a limb that turned a long way is a visible miss. */
        Quaternionf tipParent = null;
        PivotFrame tipFrame = frames.get(workIds.get(workIds.size() - 1));

        if (tipFrame != null)
        {
            tipParent = new Quaternionf(blendedWorld[lastJoint])
                .mul(new Quaternionf(tree.joints[lastJoint].startWorldRotation).conjugate())
                .mul(tipFrame.parentRotation());
        }

        Vector3f gap = new Vector3f(r.target()).sub(tree.effectors[effectorIndex].position).mul(r.influence());

        if (gap.lengthSquared() < EPS * EPS)
        {
            return;
        }

        int reach = lastGeometryIndex(cubic, workIds);

        if (reach < 1)
        {
            return;
        }

        /* Solved positions along the limb: the nodes from the tree, the effector
         * point for the last id. */
        Vector3f[] solved = new Vector3f[workIds.size()];

        for (int i = 0; i < workIds.size(); i++)
        {
            int node = indexOf(nodes, workIds.get(i));

            solved[i] = node >= 0 ? tree.joints[node].position
                : i == workIds.size() - 1 ? tree.effectors[effectorIndex].position : null;

            if (solved[i] == null)
            {
                return;
            }
        }

        float total = 0F;

        for (int i = 0; i < reach; i++)
        {
            total += solved[i].distance(solved[i + 1]);
        }

        if (total < EPS)
        {
            return;
        }

        for (int i = 1; i <= reach && i < workIds.size(); i++)
        {
            Vector3f share = new Vector3f(gap).mul(solved[i - 1].distance(solved[i]) / total);

            String bone = workIds.get(i);
            int node = indexOf(nodes, bone);
            Quaternionf parentFrame = node >= 0 && blendedParentOf[node] != null ? blendedParentOf[node] : tipParent;

            writeStretchOffset(cubic, bone, frames.get(bone), parentFrame, share);
        }
    }

    /**
     * Writes one bone's share of the telescope. The bone takes the LOCAL step in
     * its parent's frame — the renderer pre-translates there and the matrix stack
     * carries it to the whole subtree, so each bone writes only its own share.
     * Dividing by the frame's scale undoes the scaling the renderer would otherwise
     * apply on top of it.
     */
    private static void writeStretchOffset(Model cubic, String bone, PivotFrame frame, Quaternionf parentFrame, Vector3f share)
    {
        if (parentFrame == null)
        {
            return;
        }

        ModelGroup group = cubic.getGroup(bone);

        if (group == null)
        {
            return;
        }

        Vector3f local = new Quaternionf(parentFrame).conjugate().transform(new Vector3f(share));
        Vector3f scale = frame == null ? null : frame.scale();

        if (scale != null)
        {
            local.set(divide(local.x, scale.x), divide(local.y, scale.y), divide(local.z, scale.z));
        }

        group.offset = local;
    }

    private static float divide(float value, float by)
    {
        return Math.abs(by) < EPS ? value : value / by;
    }

    private static String fmt(Vector3f v)
    {
        return String.format("(%.2f, %.2f, %.2f)", v.x, v.y, v.z);
    }

    private static int indexOf(List<String> nodes, String bone)
    {
        for (int i = 0; i < nodes.size(); i++)
        {
            if (nodes.get(i).equals(bone))
            {
                return i;
            }
        }

        return -1;
    }
}
