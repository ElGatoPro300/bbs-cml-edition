package mchorse.bbs_mod.cubic.physics;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.joml.QuaternionMath;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.WeakHashMap;

/**
 * Compiles authored {@link SpringChainsConfig} into solver-ready chain geometry
 * (bone id lists and rest lengths), cached per MapType instance.
 */
public final class SpringChainCompiler
{
    public static final class CompiledChain
    {
        private final String id;
        private final String attach;
        private final String pinTarget;
        private final List<String> chainRootToEnd;
        private final float[] restLengths;
        private final float pullStrength;
        private final float drag;
        private final float springReturn;
        private final int relaxSteps;
        private final boolean bodyRelativePull;
        private final boolean hasPullRotation;
        private final Quaternionf pullRotation;
        private final boolean hitDetection;
        private final float hitRadius;
        private final float influence;

        public CompiledChain(String id, String attach, String pinTarget, List<String> chainRootToEnd, float[] restLengths, SpringChainDef chain)
        {
            this.id = id;
            this.attach = attach;
            this.pinTarget = pinTarget;
            this.chainRootToEnd = chainRootToEnd;
            this.restLengths = restLengths;
            this.pullStrength = chain.pullStrength();
            this.drag = chain.drag();
            this.springReturn = chain.springReturn();
            this.relaxSteps = chain.relaxSteps();
            this.bodyRelativePull = chain.bodyRelativePull();
            this.hasPullRotation = chain.hasPullRotation();
            this.pullRotation = this.hasPullRotation
                ? QuaternionMath.composeFromEulerZYX(chain.pullRotX(), chain.pullRotY(), chain.pullRotZ())
                : new Quaternionf();
            this.hitDetection = chain.hitDetection();
            this.hitRadius = chain.hitRadius();
            this.influence = chain.influence();
        }

        public String id()
        {
            return this.id;
        }

        public String attach()
        {
            return this.attach;
        }

        public String pinTarget()
        {
            return this.pinTarget;
        }

        public List<String> chainRootToEnd()
        {
            return this.chainRootToEnd;
        }

        public float[] restLengths()
        {
            return this.restLengths;
        }

        public float pullStrength()
        {
            return this.pullStrength;
        }

        public float drag()
        {
            return this.drag;
        }

        public float springReturn()
        {
            return this.springReturn;
        }

        public int relaxSteps()
        {
            return this.relaxSteps;
        }

        public boolean bodyRelativePull()
        {
            return this.bodyRelativePull;
        }

        public boolean hasPullRotation()
        {
            return this.hasPullRotation;
        }

        public void applyPullRotation(Vector3f direction)
        {
            if (this.hasPullRotation)
            {
                this.pullRotation.transform(direction);
            }
        }

        public boolean hitDetection()
        {
            return this.hitDetection;
        }

        public float hitRadius()
        {
            return this.hitRadius;
        }

        public float influence()
        {
            return this.influence;
        }
    }

    public record Compiled(List<CompiledChain> chains, WindDef wind)
    {
    }

    private static final WeakHashMap<MapType, EmbeddedCompiled> EMBEDDED = new WeakHashMap<>();

    private record EmbeddedCompiled(IModel model, List<CompiledChain> chains, WindDef wind)
    {
    }

    private SpringChainCompiler()
    {
    }

    public static void clear()
    {
        EMBEDDED.clear();
    }

    public static Compiled getFromData(IModel model, MapType data)
    {
        if (model == null || data == null)
        {
            return null;
        }

        EmbeddedCompiled cached = EMBEDDED.get(data);

        if (cached != null && cached.model == model)
        {
            return new Compiled(cached.chains, cached.wind);
        }

        SpringChainsConfig config = SpringChainSerializer.fromData(data);
        List<CompiledChain> compiled = compile(model, config);
        WindDef wind = config != null ? config.wind() : WindDef.NONE;

        EmbeddedCompiled next = new EmbeddedCompiled(model, compiled, wind);
        EMBEDDED.put(data, next);

        return new Compiled(compiled, wind);
    }

    private static List<CompiledChain> compile(IModel model, SpringChainsConfig config)
    {
        if (config == null || config.chains() == null || config.chains().isEmpty())
        {
            return Collections.emptyList();
        }

        List<CompiledChain> out = new ArrayList<>();

        List<String> roots = new ArrayList<>(config.chains().keySet());
        Collections.sort(roots);

        for (String rootId : roots)
        {
            SpringChainDef chain = config.chains().get(rootId);

            if (chain == null)
            {
                continue;
            }

            if (!model.getAllGroupKeys().contains(rootId))
            {
                continue;
            }

            /* The end bone is OPTIONAL. Leave it blank and the chain runs from the root
             * down to the deepest bone under it — turning physics on is enough, no second
             * bone to hunt for. Naming one still pins the chain to exactly that tip, which
             * is what you want when a bone forks and you care which branch simulates. */
            String endId = chain.endBone();
            List<String> ids;

            if (endId == null || endId.isEmpty() || !model.getAllGroupKeys().contains(endId))
            {
                ids = buildAutoChainIds(model, rootId);
                endId = ids.isEmpty() ? rootId : ids.get(ids.size() - 1);
            }
            else
            {
                ids = buildChainIds(model, endId, rootId);
            }

            if (ids.isEmpty())
            {
                continue;
            }

            float[] lengths = computeRestLengths(model, ids);

            if (lengths == null)
            {
                continue;
            }

            String attach = rootId;
            String id = rootId + ":" + endId;

            out.add(new CompiledChain(id, attach, chain.pinTarget(), ids, lengths, chain));
        }

        return out;
    }

    /**
     * The chain a bare root implies: walk DOWN from the root, taking the longest branch
     * at every fork, until the skeleton runs out. Helper bones are skipped — an item
     * hold point or an IK locator hanging off a bone is a marker, not a link of the
     * chain, and simulating it would whip a locator around instead of the limb.
     *
     * <p>Root-to-tip order, matching {@link #buildChainIds}. A root with no children is
     * a one-bone chain, which is legal: the solver still swings its virtual tip.
     */
    private static List<String> buildAutoChainIds(IModel model, String rootId)
    {
        List<String> chain = new ArrayList<>();
        String current = rootId;

        while (current != null && !current.isEmpty() && chain.size() < 256)
        {
            chain.add(current);
            current = pickLongestChild(model, current);
        }

        return chain;
    }

    /** The child that starts the longest run of real bones; {@code null} when there is none. */
    private static String pickLongestChild(IModel model, String parent)
    {
        String best = null;
        int bestLength = -1;

        for (String child : model.getDirectChildrenKeys(parent))
        {
            if (isHelperBone(child))
            {
                continue;
            }

            int length = descendantLength(model, child, 0);

            if (length > bestLength)
            {
                best = child;
                bestLength = length;
            }
        }

        return best;
    }

    private static int descendantLength(IModel model, String bone, int depth)
    {
        if (depth > 256)
        {
            return depth;
        }

        String child = pickLongestChild(model, bone);

        return child == null ? 1 : 1 + descendantLength(model, child, depth + 1);
    }

    /**
     * Markers that hang off a bone without being part of it — the same convention the IK
     * compiler uses ({@code LimbConstraintCompiler.isIkHelperBone}), plus armour, which
     * rides a limb rather than extending it.
     */
    private static boolean isHelperBone(String bone)
    {
        if (bone == null || bone.isEmpty())
        {
            return true;
        }

        return bone.endsWith("_item")
            || bone.contains("armor_")
            || bone.contains("_locator")
            || bone.contains("_ik_")
            || bone.startsWith("ik_")
            || bone.startsWith("controller_")
            || bone.startsWith("pole_");
    }

    private static List<String> buildChainIds(IModel model, String endId, String rootId)
    {
        List<String> list = new ArrayList<>();
        String group = endId;

        while (group != null && !group.isEmpty())
        {
            list.add(group);

            if (group.equals(rootId))
            {
                Collections.reverse(list);
                return list;
            }

            String parent = model.getParentGroupKey(group);

            if (parent == null || parent.equals(group))
            {
                break;
            }

            group = parent;
        }

        return Collections.emptyList();
    }

    private static float[] computeRestLengths(IModel model, List<String> ids)
    {
        SkeletonGeometryAdapter rig = SkeletonGeometryAdapter.of(model);

        if (rig == null)
        {
            return null;
        }

        int n = ids.size();
        float[] lengths = new float[n];

        if (n == 1)
        {
            float len = rig.restLength(ids.get(0), null);

            if (len < 0F)
            {
                return null;
            }

            lengths[0] = len;

            return lengths;
        }

        for (int i = 0; i < n - 1; i++)
        {
            float len = rig.restLength(ids.get(i), ids.get(i + 1));

            if (len < 0F)
            {
                return null;
            }

            lengths[i] = len;
        }

        lengths[n - 1] = lengths[n - 2];

        return lengths;
    }
}
