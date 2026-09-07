package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;

import java.util.ArrayList;
import java.util.List;

/**
 * Serializes {@link LimbConstraintDef} with renamed keys:
 * controller, depth, pole_enabled, pole_bone, bend_offset, flexibility,
 * influence, active, orient_tip, extensible.
 */
public final class LimbConstraintSerializer
{
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

    private static final boolean DEFAULT_ACTIVE = true;
    private static final boolean DEFAULT_POLE_ENABLED = true;

    private LimbConstraintSerializer()
    {
    }

    public static LimbConstraintDef fromData(MapType map)
    {
        if (map == null || map.isEmpty())
        {
            return null;
        }

        List<LimbConstraintDef.Limb> limbs = new ArrayList<>();

        for (String tip : new ArrayList<>(map.keys()))
        {
            if (!map.has(tip, BaseType.TYPE_MAP))
            {
                continue;
            }

            MapType entry = map.getMap(tip);
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

            limbs.add(new LimbConstraintDef.Limb(tip, controller, depth, poleEnabled, poleBone, bendOffset, flexibility, influence, active, orientTip, extensible));
        }

        return limbs.isEmpty() ? null : new LimbConstraintDef(limbs);
    }

    public static MapType toData(LimbConstraintDef config)
    {
        MapType root = new MapType();

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

                root.put(limb.tipBone(), entry);
            }
        }

        return root;
    }
}
