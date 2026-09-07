package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.data.types.MapType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.WeakHashMap;

/**
 * Compiles form-embedded limb constraints into root-to-tip bone id chains.
 */
public final class LimbConstraintCompiler
{
    private LimbConstraintCompiler()
    {
    }

    public record CompiledLimb(String tipBone, String controllerBone, boolean poleEnabled, String poleBone, float bendOffset, float flexibility, float influence, boolean orientTip, boolean extensible, List<String> chainRootToEffector)
    {
    }

    public record Compiled(List<CompiledLimb> limbs)
    {
    }

    private static final WeakHashMap<MapType, EmbeddedCompiled> EMBEDDED = new WeakHashMap<>();

    private record EmbeddedCompiled(IModel model, List<CompiledLimb> limbs)
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
            return new Compiled(cached.limbs);
        }

        LimbConstraintDef config = LimbConstraintSerializer.fromData(data);
        List<CompiledLimb> compiled = compile(model, config);
        EmbeddedCompiled next = new EmbeddedCompiled(model, compiled);

        EMBEDDED.put(data, next);

        return new Compiled(compiled);
    }

    private static List<CompiledLimb> compile(IModel model, LimbConstraintDef config)
    {
        if (config == null || config.limbs() == null || config.limbs().isEmpty())
        {
            return Collections.emptyList();
        }

        List<CompiledLimb> out = new ArrayList<>(config.limbs().size());

        for (LimbConstraintDef.Limb limb : config.limbs())
        {
            if (limb == null || !limb.active())
            {
                continue;
            }

            if (!model.getAllGroupKeys().contains(limb.tipBone()) || !model.getAllGroupKeys().contains(limb.controllerBone()))
            {
                continue;
            }

            List<String> chainIds = buildChainIds(model, limb.tipBone(), limb.depth());
            int minChainSize = limb.depth() < 0 ? 1 : 2;

            if (chainIds.size() < minChainSize)
            {
                continue;
            }

            String poleBone = limb.poleBone();

            if (poleBone != null && !poleBone.isEmpty() && !model.getAllGroupKeys().contains(poleBone))
            {
                poleBone = "";
            }

            out.add(new CompiledLimb(limb.tipBone(), limb.controllerBone(), limb.poleEnabled(), poleBone, limb.bendOffset(), limb.flexibility(), limb.influence(), limb.orientTip(), limb.extensible(), chainIds));
        }

        return out;
    }

    private static List<String> buildChainIds(IModel model, String tip, int depth)
    {
        if (depth < 0)
        {
            return buildDescendantChainIds(model, tip, -depth);
        }

        List<String> list = new ArrayList<>();
        String group = tip;

        while (group != null && !group.isEmpty())
        {
            list.add(group);

            if (depth > 0 && list.size() >= depth)
            {
                break;
            }

            String parent = model.getParentGroupKey(group);

            if (parent == null || parent.equals(group))
            {
                break;
            }

            group = parent;
        }

        Collections.reverse(list);

        return list;
    }

    /**
     * Builds a root-to-tip chain by walking down deform bones from {@code root},
     * without including ancestors of {@code root} (so torso bones stay fixed).
     * When the limb runs out of deform bones, a helper bone (item hold point or
     * locator at the end of the limb) is appended as the effector joint so the
     * limb's end, not its pivot, reaches the target.
     */
    private static List<String> buildDescendantChainIds(IModel model, String root, int maxBones)
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
