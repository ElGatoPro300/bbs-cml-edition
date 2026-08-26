package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Serializes {@link LimbConstraintDef}. The stored shape is
 * {@code {"limbs": {tip: {...}}, "joints": {bone: {...}}}} — limbs keyed by their
 * tip bone, per-bone joint freedom keyed by bone. Limb keys are: controller,
 * depth, pole_enabled, pole_bone, bend_offset, flexibility, influence, active,
 * orient_tip, extensible, classic.
 *
 * <p>Data written before the joints existed was the flat limbs map itself (no
 * wrapper); it is still read: a map WITHOUT a {@code "limbs"} key is taken as the
 * legacy flat form, so old scenes and presets load unchanged.
 *
 * <p>That missing wrapper doubles as the version marker of the IK redesign: a flat
 * map was authored against the OLD position-level solver, so its limbs migrate
 * with {@code classic} ON and nothing an animator already tuned shifts under them.
 * Re-saving from the panel writes the wrapper, and from then on the toggle is
 * whatever the animator set.
 */
public final class LimbConstraintSerializer
{
    private static final String KEY_LIMBS = "limbs";
    private static final String KEY_JOINTS = "joints";

    private static final String KEY_CONTROLLER = "controller";
    private static final String KEY_DEPTH = "depth";
    private static final String KEY_POLE_ENABLED = "pole_enabled";
    private static final String KEY_POLE_BONE = "pole_bone";
    private static final String KEY_BEND_OFFSET = "bend_offset";
    private static final String KEY_FLEXIBILITY = "flexibility";
    private static final String KEY_INFLUENCE = "influence";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_ORIENT_TIP = "orient_tip";
    private static final String KEY_EXTENSIBLE = "extensible";
    private static final String KEY_CLASSIC = "classic";

    private static final String KEY_LOCK = "lock";
    private static final String KEY_LIMITED = "limited";
    private static final String KEY_MIN = "min";
    private static final String KEY_MAX = "max";
    private static final String KEY_STIFFNESS = "stiffness";

    private static final boolean DEFAULT_ACTIVE = true;
    private static final boolean DEFAULT_POLE_ENABLED = true;

    private LimbConstraintSerializer()
    {
    }

    /** Whether the map is the wrapped (post-redesign) shape rather than a flat limbs map. */
    public static boolean isWrapped(MapType map)
    {
        return map != null && map.has(KEY_LIMBS, BaseType.TYPE_MAP);
    }

    /**
     * The sub-map holding the limb entries, whichever shape {@code map} is in —
     * for callers that merge extra limbs in (the Mine-imator auto-limbs). Returns
     * the map itself for the legacy flat form, so a merge lands in the right place
     * either way. {@code null} when there is nowhere to put them.
     */
    public static MapType limbEntries(MapType map)
    {
        if (map == null)
        {
            return null;
        }

        return isWrapped(map) ? map.getMap(KEY_LIMBS) : map;
    }

    public static LimbConstraintDef fromData(MapType map)
    {
        if (map == null || map.isEmpty())
        {
            return null;
        }

        boolean wrapped = isWrapped(map);
        MapType limbsMap = wrapped ? map.getMap(KEY_LIMBS) : map;

        /* Pre-redesign data: the old solver is what these limbs were posed against,
         * so they come back with it on. */
        boolean defaultClassic = wrapped ? LimbConstraintDef.DEFAULT_CLASSIC : true;

        List<LimbConstraintDef.Limb> limbs = new ArrayList<>();

        for (String tip : new ArrayList<>(limbsMap.keys()))
        {
            if (!limbsMap.has(tip, BaseType.TYPE_MAP))
            {
                continue;
            }

            MapType entry = limbsMap.getMap(tip);
            String controller = entry.getString(KEY_CONTROLLER);
            int depth = entry.getInt(KEY_DEPTH, LimbConstraintDef.DEFAULT_DEPTH);
            boolean poleEnabled = entry.getBool(KEY_POLE_ENABLED, DEFAULT_POLE_ENABLED);
            String poleBone = entry.getString(KEY_POLE_BONE);
            float bendOffset = (float) entry.getDouble(KEY_BEND_OFFSET, LimbConstraintDef.DEFAULT_BEND_OFFSET);
            float flexibility = (float) entry.getDouble(KEY_FLEXIBILITY, LimbConstraintDef.DEFAULT_FLEXIBILITY);
            float influence = (float) entry.getDouble(KEY_INFLUENCE, LimbConstraintDef.DEFAULT_INFLUENCE);
            boolean active = entry.getBool(KEY_ACTIVE, DEFAULT_ACTIVE);
            boolean orientTip = entry.getBool(KEY_ORIENT_TIP, LimbConstraintDef.DEFAULT_ORIENT_TIP);
            boolean extensible = entry.getBool(KEY_EXTENSIBLE, LimbConstraintDef.DEFAULT_EXTENSIBLE);
            boolean classic = entry.getBool(KEY_CLASSIC, defaultClassic);

            limbs.add(new LimbConstraintDef.Limb(tip, controller, depth, poleEnabled, poleBone, bendOffset, flexibility, influence, active, orientTip, extensible, classic));
        }

        Map<String, LimbConstraintDef.JointDoF> joints = new HashMap<>();

        if (map.has(KEY_JOINTS, BaseType.TYPE_MAP))
        {
            MapType jointsMap = map.getMap(KEY_JOINTS);

            for (String bone : jointsMap.keys())
            {
                if (!jointsMap.has(bone, BaseType.TYPE_MAP))
                {
                    continue;
                }

                LimbConstraintDef.JointDoF joint = jointFromData(jointsMap.getMap(bone));

                if (!joint.isFree())
                {
                    joints.put(bone, joint);
                }
            }
        }

        return limbs.isEmpty() && joints.isEmpty() ? null : new LimbConstraintDef(limbs, joints);
    }

    public static MapType toData(LimbConstraintDef config)
    {
        MapType root = new MapType();
        MapType limbs = new MapType();

        if (config != null && config.limbs() != null)
        {
            for (LimbConstraintDef.Limb limb : config.limbs())
            {
                if (limb == null || limb.tipBone() == null || limb.tipBone().isEmpty())
                {
                    continue;
                }

                MapType entry = new MapType();
                String controller = limb.controllerBone() == null ? "" : limb.controllerBone();

                entry.putString(KEY_CONTROLLER, controller);
                entry.putBool(KEY_ACTIVE, limb.active());

                if (limb.depth() != LimbConstraintDef.DEFAULT_DEPTH)
                {
                    entry.putInt(KEY_DEPTH, limb.depth());
                }

                if (limb.poleEnabled() != DEFAULT_POLE_ENABLED)
                {
                    entry.putBool(KEY_POLE_ENABLED, limb.poleEnabled());
                }

                if (limb.poleBone() != null && !limb.poleBone().isEmpty())
                {
                    entry.putString(KEY_POLE_BONE, limb.poleBone());
                }

                if (limb.bendOffset() != LimbConstraintDef.DEFAULT_BEND_OFFSET)
                {
                    entry.putDouble(KEY_BEND_OFFSET, limb.bendOffset());
                }

                if (limb.flexibility() != LimbConstraintDef.DEFAULT_FLEXIBILITY)
                {
                    entry.putDouble(KEY_FLEXIBILITY, limb.flexibility());
                }

                if (limb.influence() != LimbConstraintDef.DEFAULT_INFLUENCE)
                {
                    entry.putDouble(KEY_INFLUENCE, limb.influence());
                }

                if (limb.orientTip() != LimbConstraintDef.DEFAULT_ORIENT_TIP)
                {
                    entry.putBool(KEY_ORIENT_TIP, limb.orientTip());
                }

                if (limb.extensible() != LimbConstraintDef.DEFAULT_EXTENSIBLE)
                {
                    entry.putBool(KEY_EXTENSIBLE, limb.extensible());
                }

                if (limb.classic() != LimbConstraintDef.DEFAULT_CLASSIC)
                {
                    entry.putBool(KEY_CLASSIC, limb.classic());
                }

                limbs.put(limb.tipBone(), entry);
            }
        }

        MapType joints = new MapType();

        if (config != null && config.joints() != null)
        {
            for (Map.Entry<String, LimbConstraintDef.JointDoF> entry : config.joints().entrySet())
            {
                String bone = entry.getKey();
                LimbConstraintDef.JointDoF joint = entry.getValue();

                if (bone == null || bone.isEmpty() || joint == null || joint.isFree())
                {
                    continue;
                }

                joints.put(bone, jointToData(joint));
            }
        }

        if (limbs.isEmpty() && joints.isEmpty())
        {
            return root;
        }

        root.put(KEY_LIMBS, limbs);

        if (!joints.isEmpty())
        {
            root.put(KEY_JOINTS, joints);
        }

        return root;
    }

    private static LimbConstraintDef.JointDoF jointFromData(MapType map)
    {
        boolean lockX = false, lockY = false, lockZ = false;
        boolean limitX = false, limitY = false, limitZ = false;
        float minX = LimbConstraintDef.JointDoF.DEFAULT_MIN, minY = minX, minZ = minX;
        float maxX = LimbConstraintDef.JointDoF.DEFAULT_MAX, maxY = maxX, maxZ = maxX;
        float stiffnessX = 0F, stiffnessY = 0F, stiffnessZ = 0F;

        if (map.has(KEY_LOCK, BaseType.TYPE_LIST))
        {
            ListType list = map.getList(KEY_LOCK);

            lockX = list.getBool(0);
            lockY = list.getBool(1);
            lockZ = list.getBool(2);
        }

        if (map.has(KEY_LIMITED, BaseType.TYPE_LIST))
        {
            ListType list = map.getList(KEY_LIMITED);

            limitX = list.getBool(0);
            limitY = list.getBool(1);
            limitZ = list.getBool(2);
        }

        if (map.has(KEY_MIN, BaseType.TYPE_LIST))
        {
            ListType list = map.getList(KEY_MIN);

            minX = getFloat(list, 0, minX);
            minY = getFloat(list, 1, minY);
            minZ = getFloat(list, 2, minZ);
        }

        if (map.has(KEY_MAX, BaseType.TYPE_LIST))
        {
            ListType list = map.getList(KEY_MAX);

            maxX = getFloat(list, 0, maxX);
            maxY = getFloat(list, 1, maxY);
            maxZ = getFloat(list, 2, maxZ);
        }

        if (map.has(KEY_STIFFNESS, BaseType.TYPE_LIST))
        {
            ListType list = map.getList(KEY_STIFFNESS);

            stiffnessX = getFloat(list, 0, 0F);
            stiffnessY = getFloat(list, 1, 0F);
            stiffnessZ = getFloat(list, 2, 0F);
        }

        return new LimbConstraintDef.JointDoF(lockX, lockY, lockZ,
            limitX, minX, maxX,
            limitY, minY, maxY,
            limitZ, minZ, maxZ,
            stiffnessX, stiffnessY, stiffnessZ);
    }

    private static MapType jointToData(LimbConstraintDef.JointDoF joint)
    {
        MapType map = new MapType();

        if (joint.lockX() || joint.lockY() || joint.lockZ())
        {
            ListType lock = new ListType();

            lock.addBool(joint.lockX());
            lock.addBool(joint.lockY());
            lock.addBool(joint.lockZ());
            map.put(KEY_LOCK, lock);
        }

        if (joint.limitX() || joint.limitY() || joint.limitZ())
        {
            ListType limited = new ListType();

            limited.addBool(joint.limitX());
            limited.addBool(joint.limitY());
            limited.addBool(joint.limitZ());
            map.put(KEY_LIMITED, limited);

            ListType min = new ListType();

            min.addFloat(joint.minX());
            min.addFloat(joint.minY());
            min.addFloat(joint.minZ());
            map.put(KEY_MIN, min);

            ListType max = new ListType();

            max.addFloat(joint.maxX());
            max.addFloat(joint.maxY());
            max.addFloat(joint.maxZ());
            map.put(KEY_MAX, max);
        }

        if (joint.stiffnessX() > 0F || joint.stiffnessY() > 0F || joint.stiffnessZ() > 0F)
        {
            ListType stiffness = new ListType();

            stiffness.addFloat(joint.stiffnessX());
            stiffness.addFloat(joint.stiffnessY());
            stiffness.addFloat(joint.stiffnessZ());
            map.put(KEY_STIFFNESS, stiffness);
        }

        return map;
    }

    private static float getFloat(ListType list, int index, float def)
    {
        BaseType element = list == null ? null : list.get(index);

        if (BaseType.isNumeric(element))
        {
            return element.asNumeric().floatValue();
        }

        return def;
    }
}
