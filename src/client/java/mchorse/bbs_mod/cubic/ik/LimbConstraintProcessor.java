package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.constraints.JointLimitConfig.JointLimit;
import mchorse.bbs_mod.cubic.constraints.JointLimitEnforcer;
import mchorse.bbs_mod.cubic.ik.LimbDynamicParams;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.ModelForm;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runtime entry point that compiles form-embedded limb constraints and writes
 * the solved pose onto the live model instance.
 */
public final class LimbConstraintProcessor
{
    private LimbConstraintProcessor()
    {
    }

    public static void clearCache()
    {
        LimbConstraintCompiler.clear();
    }

    public static void invalidate(String modelId)
    {
        clearCache();
    }

    public static void process(ModelInstance instance, Map<String, Vector3f> targets, Map<String, Vector3f> poles)
    {
        process(instance, targets, poles, null, null);
    }

    public static void process(ModelInstance instance, Map<String, Vector3f> targets, Map<String, Vector3f> poles, Map<String, Quaternionf> tipRotations, Map<String, Float> tipRotationWeights)
    {
        if (instance == null || instance.model == null)
        {
            return;
        }

        IModel model = instance.model;
        LimbConstraintCompiler.Compiled compiled = null;
        MapType map = null;
        Map<String, LimbDynamicParams> controlOverrides = null;
        Map<String, Float> targetWeights = null;
        Map<String, Float> poleWeights = null;

        map = resolveIkMap(instance);

        if (instance.form instanceof ModelForm form)
        {
            controlOverrides = form.limbParamOverrides;
            targetWeights = form.ikTargetWeights;
            poleWeights = form.poleTargetWeights;

            if (tipRotations == null && !form.ikTipRotationOverrides.isEmpty())
            {
                tipRotations = form.ikTipRotationOverrides;
                tipRotationWeights = form.ikTipRotationWeights;
            }
        }

        if (map != null)
        {
            compiled = LimbConstraintCompiler.getFromData(model, map);
        }
        else
        {
            IKLog.note("this form carries no ik map at all — nothing was ever saved, or the form is not a ModelForm");
        }

        if (compiled == null)
        {
            return;
        }

        List<LimbConstraintCompiler.CompiledLimb> limbs = compiled.limbs();

        if (limbs == null || limbs.isEmpty())
        {
            return;
        }

        Map<String, JointLimit> boneLimits = JointLimitEnforcer.getJoints(instance);

        SkeletonPoseWriter.apply(model, limbs, compiled.joints(), targets, poles, targetWeights, poleWeights, controlOverrides, boneLimits, tipRotations, tipRotationWeights);
    }

    public static List<String> getControllers(ModelInstance instance)
    {
        LimbConstraintCompiler.Compiled compiled = compiledOf(instance);

        if (compiled == null || compiled.limbs() == null || compiled.limbs().isEmpty())
        {
            return Collections.emptyList();
        }

        Set<String> unique = new LinkedHashSet<>();

        for (LimbConstraintCompiler.CompiledLimb limb : compiled.limbs())
        {
            if (limb != null && limb.controllerBone() != null && !limb.controllerBone().isEmpty())
            {
                unique.add(limb.controllerBone());
            }
        }

        return unique.isEmpty() ? Collections.emptyList() : new ArrayList<>(unique);
    }

    public static List<String> getPoleControllers(ModelInstance instance)
    {
        LimbConstraintCompiler.Compiled compiled = compiledOf(instance);

        if (compiled == null || compiled.limbs() == null || compiled.limbs().isEmpty())
        {
            return Collections.emptyList();
        }

        Set<String> unique = new LinkedHashSet<>();

        for (LimbConstraintCompiler.CompiledLimb limb : compiled.limbs())
        {
            if (limb != null && limb.poleEnabled() && limb.poleBone() != null && !limb.poleBone().isEmpty())
            {
                unique.add(limb.poleBone());
            }
        }

        return unique.isEmpty() ? Collections.emptyList() : new ArrayList<>(unique);
    }

    private static LimbConstraintCompiler.Compiled compiledOf(ModelInstance instance)
    {
        if (instance == null || instance.model == null)
        {
            return null;
        }

        MapType map = resolveIkMap(instance);

        if (map != null)
        {
            return LimbConstraintCompiler.getFromData(instance.model, map);
        }

        return null;
    }

    /**
     * The IK map actually in effect for this instance: the form's own config merged with
     * the model's, falling back to the model's alone when the instance carries no
     * {@link ModelForm}.
     *
     * <p>Public because the debug overlay needs the SAME map the solver uses. Reading
     * {@code form.ik} alone is wrong outside the model editor — a model's IK config lives
     * in {@code instance.limbConstraints}, and only the editor's
     * {@code syncSolverConfig} copies it onto the form.
     */
    public static MapType resolveIkMap(ModelInstance instance)
    {
        if (instance == null)
        {
            return null;
        }

        if (instance.form instanceof ModelForm form)
        {
            return mergeIkMap(form, instance.limbConstraints);
        }

        return instance.limbConstraints;
    }

    private static MapType mergeIkMap(ModelForm form, MapType instanceLimbs)
    {
        MapType base = null;

        if (form.ik.get() instanceof MapType map && !map.isEmpty())
        {
            base = (MapType) map.copy();
        }
        else if (instanceLimbs != null)
        {
            base = (MapType) instanceLimbs.copy();
        }

        if (form.inverseKinematicsLimbs == null || form.inverseKinematicsLimbs.isEmpty())
        {
            return base;
        }

        if (base == null)
        {
            base = new MapType();
        }

        /* The auto-limbs are a FLAT tip-keyed map; the form's config may be either
         * shape, so they have to land in its limb sub-map rather than beside it —
         * dropped at the top level of a wrapped config they would be silently
         * ignored by the deserializer. */
        MapType entries = LimbConstraintSerializer.limbEntries(base);

        if (entries == null)
        {
            return base;
        }

        for (String key : form.inverseKinematicsLimbs.keys())
        {
            if (!entries.has(key))
            {
                entries.put(key, form.inverseKinematicsLimbs.get(key).copy());
            }
        }

        return base;
    }
}
