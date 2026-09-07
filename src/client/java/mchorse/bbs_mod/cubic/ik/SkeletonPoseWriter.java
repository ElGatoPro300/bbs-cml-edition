package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.constraints.JointLimitConfig.JointLimit;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.cubic.render.BoneFrameCollector;
import mchorse.bbs_mod.cubic.render.CubicRenderer.PivotFrame;
import mchorse.bbs_mod.cubic.render.SolvedPoseApplicator;
import mchorse.bbs_mod.utils.joml.QuaternionMath;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Writes solved limb poses onto cubic / BOBJ skeletons: parallel-transport
 * orientations, optional stretch offsets, and tip rotation.
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
        apply(model, limbs, controllerTargets, poleTargets, targetWeights, poleWeights, controlOverrides, boneLimits, null, null);
    }

    public static void apply(IModel model, List<LimbConstraintCompiler.CompiledLimb> limbs, Map<String, Vector3f> controllerTargets, Map<String, Vector3f> poleTargets, Map<String, Float> targetWeights, Map<String, Float> poleWeights, Map<String, LimbDynamicParams> controlOverrides, Map<String, JointLimit> boneLimits, Map<String, Quaternionf> tipRotations, Map<String, Float> tipRotationWeights)
    {
        if (model == null || limbs == null || limbs.isEmpty())
        {
            return;
        }

        List<LimbConstraintCompiler.CompiledLimb> ordered = new ArrayList<>(limbs);

        ordered.sort(Comparator.comparingInt((LimbConstraintCompiler.CompiledLimb limb) -> rootDepth(model, limb)));

        for (LimbConstraintCompiler.CompiledLimb limb : ordered)
        {
            Set<String> wanted = new HashSet<>();

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

            Map<String, PivotFrame> frames = new HashMap<>(wanted.size() * 2);

            BoneFrameCollector.collect(model, wanted, frames, null, true);
            applyChain(model, limb, frames, controllerTargets, poleTargets, targetWeights, poleWeights, controlOverrides, boneLimits, tipRotations, tipRotationWeights);
        }
    }

    private static int rootDepth(IModel model, LimbConstraintCompiler.CompiledLimb limb)
    {
        List<String> ids = limb.chainRootToEffector();
        String group = ids.isEmpty() ? limb.tipBone() : ids.get(0);
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
}
