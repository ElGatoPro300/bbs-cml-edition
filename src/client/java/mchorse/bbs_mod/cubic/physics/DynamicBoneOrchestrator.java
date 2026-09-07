package mchorse.bbs_mod.cubic.physics;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.constraints.JointLimitConfig;
import mchorse.bbs_mod.cubic.constraints.JointLimitEnforcer;
import mchorse.bbs_mod.cubic.render.BoneFrameCollector;
import mchorse.bbs_mod.cubic.render.CubicRenderer.PivotFrame;
import mchorse.bbs_mod.cubic.render.SolvedPoseApplicator;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.ModelForm;

import net.minecraft.world.World;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Orchestrates spring-chain bone physics: owns per-entity simulation state and
 * feeds each chain to {@link VerletChainSimulator}.
 */
public final class DynamicBoneOrchestrator
{
    static final class InstanceState
    {
        public final Map<String, VerletChainSnapshot> chains = new HashMap<>();
    }

    private static final WeakHashMap<IEntity, Map<String, InstanceState>> STATES = new WeakHashMap<>();

    private DynamicBoneOrchestrator()
    {
    }

    public static void clearCache()
    {
        SpringChainCompiler.clear();
        STATES.clear();
    }

    public static void invalidate(String modelId)
    {
        for (Map<String, InstanceState> byModel : STATES.values())
        {
            if (byModel != null)
            {
                byModel.remove(modelId);
            }
        }
    }

    public static void apply(IEntity entity, ModelInstance instance, float transition, Matrix4f baseTransform)
    {
        if (entity == null || instance == null || instance.model == null)
        {
            return;
        }

        IModel model = instance.model;

        SpringChainCompiler.Compiled compiled = null;
        MapType map = null;

        if (instance.form instanceof ModelForm modelForm && modelForm.springs.get() instanceof MapType m)
        {
            map = m;
        }
        else if (instance.springChains != null)
        {
            map = instance.springChains;
        }

        if (map != null)
        {
            compiled = SpringChainCompiler.getFromData(model, map);
        }

        if (compiled == null || compiled.chains() == null || compiled.chains().isEmpty())
        {
            return;
        }

        Map<String, JointLimitConfig.JointLimit> jointLimits = JointLimitEnforcer.getJoints(instance);

        Map<String, InstanceState> byModel = STATES.computeIfAbsent(entity, (e) -> new HashMap<>());
        InstanceState state = byModel.computeIfAbsent(instance.id, (k) -> new InstanceState());

        WindDef wind = compiled.wind();

        if (instance.form instanceof ModelForm modelForm && modelForm.windDynamicOverride != null)
        {
            WindDynamicParams override = modelForm.windDynamicOverride;

            wind = new WindDef(override.power, override.dirX, override.dirY, override.dirZ, override.gustiness, override.gustSpeed, override.gustScale, override.modelRelative);
        }

        wind = resolveWindDirection(wind, baseTransform);

        applyCompiled(entity.getWorld(), entity.getAge(), transition, model, instance, compiled.chains(), wind, jointLimits, state, baseTransform);
    }

    private static WindDef resolveWindDirection(WindDef wind, Matrix4f baseTransform)
    {
        if (wind == null || !wind.modelRelative() || !wind.active() || baseTransform == null)
        {
            return wind;
        }

        Vector3f dir = new Vector3f(wind.dirX(), wind.dirY(), wind.dirZ());

        baseTransform.transformDirection(dir);

        return new WindDef(wind.power(), dir.x, dir.y, dir.z, wind.gustiness(), wind.gustSpeed(), wind.gustScale(), false);
    }

    private static void applyCompiled(World world, int age, float transition, IModel model, ModelInstance instance, List<SpringChainCompiler.CompiledChain> compiledChains, WindDef wind, Map<String, JointLimitConfig.JointLimit> jointLimits, InstanceState state, Matrix4f baseTransform)
    {
        Set<String> wanted = new HashSet<>();
        Set<String> chainIds = new HashSet<>();

        for (SpringChainCompiler.CompiledChain chain : compiledChains)
        {
            chainIds.add(chain.id());
            wanted.addAll(chain.chainRootToEnd());

            if (chain.pinTarget() != null && !chain.pinTarget().isEmpty())
            {
                wanted.add(chain.pinTarget());
            }
        }

        if (!state.chains.isEmpty())
        {
            Iterator<String> it = state.chains.keySet().iterator();

            while (it.hasNext())
            {
                if (!chainIds.contains(it.next()))
                {
                    it.remove();
                }
            }
        }

        Map<String, PivotFrame> frames = new HashMap<>(wanted.size() * 2);
        BoneFrameCollector.collect(model, wanted, frames, baseTransform);

        for (SpringChainCompiler.CompiledChain chain : compiledChains)
        {
            applyChain(world, age, transition, model, instance, chain, wind, jointLimits, frames, state);
        }
    }

    private static void applyChain(World world, int age, float transition, IModel model, ModelInstance instance, SpringChainCompiler.CompiledChain chain, WindDef wind, Map<String, JointLimitConfig.JointLimit> jointLimits, Map<String, PivotFrame> frames, InstanceState instanceState)
    {
        List<String> ids = chain.chainRootToEnd();
        int pivotCount = ids.size();
        int pointCount = pivotCount + 1;

        if (pivotCount < 1)
        {
            return;
        }

        SpringDynamicParams control = null;

        if (instance != null && instance.form instanceof ModelForm modelForm && !modelForm.springParamsOverrides.isEmpty())
        {
            control = modelForm.springParamsOverrides.get(ids.get(0));
        }

        if (control != null && !control.active)
        {
            return;
        }

        float influence = control != null ? control.influence : chain.influence();

        if (influence <= 0F)
        {
            return;
        }

        float pullStrength = control != null ? control.pullStrength : chain.pullStrength();
        float drag = control != null ? control.drag : chain.drag();
        float springReturn = control != null ? control.springReturn : chain.springReturn();

        VerletChainSnapshot state = instanceState.chains.computeIfAbsent(chain.id(), (k) -> new VerletChainSnapshot());

        if (state.particles == null || state.particles.length != pointCount)
        {
            state.particles = new VerletChainSnapshot.Particle[pointCount];
            state.snapshotCurrent = new Vector3f[pointCount];
            state.snapshotPrevious = new Vector3f[pointCount];
            state.renderOutput = new Vector3f[pointCount];

            for (int i = 0; i < pointCount; i++)
            {
                state.particles[i] = new VerletChainSnapshot.Particle();
                state.snapshotCurrent[i] = new Vector3f();
                state.snapshotPrevious[i] = new Vector3f();
                state.renderOutput[i] = new Vector3f();
            }

            state.lastTick = Integer.MIN_VALUE;
        }

        List<PivotFrame> chainFrames = new ArrayList<>(pivotCount);

        for (int i = 0; i < pivotCount; i++)
        {
            PivotFrame frame = frames.get(ids.get(i));

            if (frame == null)
            {
                return;
            }

            chainFrames.add(frame);
        }

        PivotFrame rootFrame = chainFrames.get(0);
        Vector3f anchor = rootFrame.position();
        Quaternionf anchorOrientation = rootFrame.worldRotation();

        Vector3f target = null;

        if (instance != null && instance.form instanceof ModelForm modelForm)
        {
            String rootBone = ids.get(0);
            Vector3f worldPos = modelForm.springTargetOverrides.get(rootBone);

            if (worldPos != null)
            {
                float targetWeight = modelForm.springTargetWeights.getOrDefault(rootBone, 1F);

                if (targetWeight >= 1F)
                {
                    target = new Vector3f(worldPos);
                }
                else if (targetWeight > 0F)
                {
                    Vector3f tip = state.particles[state.particles.length - 1].position;

                    target = state.lastTick == Integer.MIN_VALUE
                        ? new Vector3f(worldPos)
                        : new Vector3f(tip).lerp(worldPos, targetWeight);
                }
            }
        }

        if (target != null)
        {
            if (state.lastTick == Integer.MIN_VALUE)
            {
                int tip = state.particles.length - 1;
                state.particles[tip].position.set(target);
                state.particles[tip].previousPosition.set(target);
            }
        }
        else if (chain.pinTarget() != null && !chain.pinTarget().isEmpty())
        {
            PivotFrame targetFrame = frames.get(chain.pinTarget());

            if (targetFrame != null)
            {
                target = targetFrame.position();

                if (state.lastTick == Integer.MIN_VALUE)
                {
                    int tip = state.particles.length - 1;
                    state.particles[tip].position.set(target);
                    state.particles[tip].previousPosition.set(target);
                }
            }
        }

        VerletChainSimulator.computePoseTargets(model, ids, chainFrames, chain.restLengths(), anchor, anchorOrientation, target != null, state);
        VerletChainSimulator.step(world, age, transition, model, ids, chain, pullStrength, drag, springReturn, wind, jointLimits, anchor, anchorOrientation, chainFrames.get(0).parentRotation(), target, chainFrames, state);

        Vector3f[] positions = VerletChainSimulator.renderInterpolate(state, state.renderBlend, anchor, anchorOrientation, target);
        SolvedPoseApplicator.applyWeightedRotations(model, chainFrames.get(0).parentRotation(), ids, positions, influence);
    }
}
