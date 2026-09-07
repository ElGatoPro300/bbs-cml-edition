package mchorse.bbs_mod.film;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.ik.LimbConstraintDef;
import mchorse.bbs_mod.cubic.ik.LimbConstraintProcessor;
import mchorse.bbs_mod.cubic.ik.LimbConstraintSerializer;
import mchorse.bbs_mod.cubic.ik.LimbDynamicParams;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.InverseKinematics;
import mchorse.bbs_mod.forms.forms.utils.InverseKinematicsBone;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.pose.PoseTransform;

import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Resolves Mine-imator style inverse kinematics keyframes into per-frame limb IK
 * overrides consumed by {@link LimbConstraintProcessor}.
 */
public final class InverseKinematicsApplier
{
    /* Mine-imator limbs bend at a single joint: root bone + bend bone + tip joint.
     * Capping the auto chain at three bones makes the solver take the analytic
     * two-bone (law of cosines) path, matching Mine-imator's IK exactly. */
    private static final int AUTO_CHAIN_MAX_BONES = 3;

    private InverseKinematicsApplier()
    {
    }

    public static void apply(FilmControllerContext context, Form form)
    {
        if (!(form instanceof ModelForm modelForm))
        {
            return;
        }

        clearOverrides(modelForm);

        InverseKinematics ik = form.inverseKinematics.get();

        if (ik == null || !ik.isActive() || context == null)
        {
            return;
        }

        if (!(FormUtilsClient.getRenderer(form) instanceof ModelFormRenderer))
        {
            return;
        }

        ModelInstance instance = ModelFormRenderer.getModel(modelForm);

        if (instance == null || instance.model == null)
        {
            return;
        }

        IModel model = instance.model;
        MapType staticIk = resolveStaticIkMap(modelForm, instance.limbConstraints);
        MapType autoLimbs = new MapType();

        for (Map.Entry<String, InverseKinematicsBone> entry : ik.bones.entrySet())
        {
            InverseKinematicsBone bone = entry.getValue();

            if (!bone.isActive())
            {
                continue;
            }

            String tipBone = entry.getKey();
            LimbConstraintDef.Limb limb = findLimb(staticIk, tipBone);

            if (limb == null)
            {
                limb = createAutoLimb(model, tipBone);

                if (limb == null)
                {
                    continue;
                }

                MapType entryMap = new MapType();

                entryMap.putString("controller", limb.controllerBone());
                entryMap.putInt("depth", limb.depth());
                entryMap.putBool("pole_enabled", limb.poleEnabled());
                entryMap.putBool("active", limb.active());

                if (limb.poleBone() != null && !limb.poleBone().isEmpty())
                {
                    entryMap.putString("pole_bone", limb.poleBone());
                }

                autoLimbs.put(tipBone, entryMap);
            }

            Vector3d targetPoint = BaseFilmController.resolveReplayAttachmentPoint(context, bone.targetReplay, bone.targetAttachment);

            if (targetPoint == null)
            {
                continue;
            }

            modelForm.ikTargetOverrides.put(limb.controllerBone(), new Vector3f((float) targetPoint.x, (float) targetPoint.y, (float) targetPoint.z));
            modelForm.ikTargetWeights.put(limb.controllerBone(), 1F);

            boolean hasAngleTarget = bone.angleTargetReplay != InverseKinematics.NO_TARGET;

            if (hasAngleTarget)
            {
                Quaternionf tipRotation = BaseFilmController.resolveReplayAttachmentRotation(context, bone.angleTargetReplay, bone.angleTargetAttachment);

                if (tipRotation != null)
                {
                    if (Math.abs(bone.angleOffset) > 0.0001F)
                    {
                        tipRotation = new Quaternionf(tipRotation).mul(new Quaternionf().rotationX(MathUtils.toRad(bone.angleOffset)));
                    }

                    modelForm.ikTipRotationOverrides.put(tipBone, tipRotation);
                    modelForm.ikTipRotationWeights.put(tipBone, MathUtils.clamp(bone.blend, 0F, 1F));
                }
            }

            LimbDynamicParams params = new LimbDynamicParams();

            params.active = true;
            params.influence = MathUtils.clamp(bone.blend, 0F, 1F);
            /* Mine-imator extends the limb straight toward an unreachable target
             * instead of softly stopping short of it. */
            params.flexibility = 0F;
            params.usePole = bone.bendXAsOffset;
            params.bendOffset = 0F;

            if (bone.bendXAsOffset)
            {
                PoseTransform poseTransform = modelForm.pose.get().get(tipBone);

                params.bendOffset = poseTransform.rotate.x;
            }

            if (bone.bendXAsOffset)
            {
                String implicitPole = model.getParentGroupKey(tipBone);

                if (implicitPole != null && !implicitPole.isEmpty())
                {
                    MapType autoEntry = autoLimbs.getMap(tipBone);

                    if (autoEntry != null)
                    {
                        autoEntry.putString("pole_bone", implicitPole);
                        autoEntry.putBool("pole_enabled", true);
                    }
                }
            }

            modelForm.limbParamOverrides.put(tipBone, params);
        }

        if (!autoLimbs.isEmpty())
        {
            modelForm.inverseKinematicsLimbs = autoLimbs;
        }
    }

    public static void clearOverrides(ModelForm modelForm)
    {
        modelForm.ikTargetOverrides.clear();
        modelForm.poleTargetOverrides.clear();
        modelForm.ikTargetWeights.clear();
        modelForm.poleTargetWeights.clear();
        modelForm.limbParamOverrides.clear();
        modelForm.ikTipRotationOverrides.clear();
        modelForm.ikTipRotationWeights.clear();
        modelForm.inverseKinematicsLimbs = null;
    }

    private static MapType resolveStaticIkMap(ModelForm modelForm, MapType instanceLimbs)
    {
        if (modelForm.ik.get() instanceof MapType map && !map.isEmpty())
        {
            return map;
        }

        return instanceLimbs;
    }

    private static LimbConstraintDef.Limb findLimb(MapType map, String tipBone)
    {
        if (map == null || tipBone == null || tipBone.isEmpty())
        {
            return null;
        }

        LimbConstraintDef config = LimbConstraintSerializer.fromData(map);

        if (config == null || config.limbs() == null)
        {
            return null;
        }

        for (LimbConstraintDef.Limb limb : config.limbs())
        {
            if (limb != null && tipBone.equals(limb.tipBone()))
            {
                return limb;
            }
        }

        return null;
    }

    private static LimbConstraintDef.Limb createAutoLimb(IModel model, String tipBone)
    {
        if (tipBone == null || tipBone.isEmpty() || !model.getAllGroupKeys().contains(tipBone))
        {
            return null;
        }

        List<String> chain = buildDescendantChain(model, tipBone, AUTO_CHAIN_MAX_BONES);

        if (chain.isEmpty())
        {
            return null;
        }

        String effector = chain.get(chain.size() - 1);
        int depth = -chain.size();

        return new LimbConstraintDef.Limb(
            tipBone,
            effector,
            depth,
            false,
            "",
            0F,
            LimbConstraintDef.DEFAULT_FLEXIBILITY,
            1F,
            true,
            false,
            false
        );
    }

    /**
     * Walks down deform children from the selected limb root so IK never rotates
     * parent torso bones. When the limb has no further deform bones, a helper
     * bone (e.g. {@code left_arm_item} at the hand) is appended as the tip joint
     * so the end of the limb, not its pivot, reaches the target.
     */
    private static List<String> buildDescendantChain(IModel model, String root, int maxBones)
    {
        List<String> chain = new ArrayList<>();

        if (root == null || root.isEmpty() || maxBones <= 0)
        {
            return chain;
        }

        String current = root;

        while (current != null && !current.isEmpty() && chain.size() < maxBones)
        {
            chain.add(current);

            String child = pickIkChild(model, current);

            if (child == null)
            {
                break;
            }

            current = child;
        }

        if (chain.size() < maxBones)
        {
            String tail = pickTailHelper(model, chain.get(chain.size() - 1));

            if (tail != null)
            {
                chain.add(tail);
            }
        }

        return chain;
    }

    /**
     * Finds a helper child (item hold point or locator) that marks the end of the
     * limb's geometry, used as the effector joint of the chain.
     */
    private static String pickTailHelper(IModel model, String bone)
    {
        String locator = null;

        for (String child : model.getDirectChildrenKeys(bone))
        {
            if (child == null || child.isEmpty())
            {
                continue;
            }

            if (child.endsWith("_item"))
            {
                return child;
            }

            if (locator == null && child.contains("_locator"))
            {
                locator = child;
            }
        }

        return locator;
    }

    private static String pickIkChild(IModel model, String parent)
    {
        String best = null;
        int bestLength = -1;

        for (String child : model.getDirectChildrenKeys(parent))
        {
            if (isIkHelperBone(child))
            {
                continue;
            }

            int length = descendantChainLength(model, child);

            if (length > bestLength)
            {
                best = child;
                bestLength = length;
            }
        }

        return best;
    }

    private static int descendantChainLength(IModel model, String bone)
    {
        int length = 1;
        String current = bone;

        while (true)
        {
            String child = pickIkChild(model, current);

            if (child == null)
            {
                break;
            }

            length++;
            current = child;
        }

        return length;
    }

    private static boolean isIkHelperBone(String bone)
    {
        if (bone == null || bone.isEmpty())
        {
            return true;
        }

        return bone.endsWith("_item")
            || bone.contains("armor_")
            || bone.contains("_locator")
            || bone.contains("_ik_")
            || bone.startsWith("ik_");
    }
}
