package mchorse.bbs_mod.cubic.physics;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;

import java.util.HashMap;
import java.util.Map;

/**
 * Serializes {@link SpringChainsConfig} / {@link SpringChainDef}
 * / {@link WindDef} with the new spring-chain key names.
 */
public final class SpringChainSerializer
{
    private static final String KEY_CHAINS = "chains";
    private static final String KEY_END_BONE = "end_bone";
    private static final String KEY_PIN_TARGET = "pin_target";
    private static final String KEY_PULL_STRENGTH = "pull_strength";
    private static final String KEY_DRAG = "drag";
    private static final String KEY_SPRING_RETURN = "spring_return";
    private static final String KEY_RELAX_STEPS = "relax_steps";
    private static final String KEY_BODY_RELATIVE_PULL = "body_relative_pull";
    private static final String KEY_PULL_ROT_X = "pull_rot_x";
    private static final String KEY_PULL_ROT_Y = "pull_rot_y";
    private static final String KEY_PULL_ROT_Z = "pull_rot_z";
    private static final String KEY_HIT_DETECTION = "hit_detection";
    private static final String KEY_HIT_RADIUS = "hit_radius";
    private static final String KEY_INFLUENCE = "influence";
    private static final String KEY_WIND = "wind";
    private static final String KEY_WIND_POWER = "power";
    private static final String KEY_WIND_DIR_X = "dir_x";
    private static final String KEY_WIND_DIR_Y = "dir_y";
    private static final String KEY_WIND_DIR_Z = "dir_z";
    private static final String KEY_WIND_GUSTINESS = "gustiness";
    private static final String KEY_WIND_GUST_SPEED = "gust_speed";
    private static final String KEY_WIND_GUST_SCALE = "gust_scale";
    private static final String KEY_WIND_MODEL_RELATIVE = "model_relative";

    private static final float DEFAULT_PULL_STRENGTH = 1F;
    private static final float DEFAULT_DRAG = 0.15F;
    private static final float DEFAULT_SPRING_RETURN = SpringChainDef.DEFAULT_SPRING_RETURN;
    private static final int DEFAULT_RELAX_STEPS = 4;
    private static final boolean DEFAULT_BODY_RELATIVE_PULL = false;
    private static final float DEFAULT_PULL_ROT_X = 0F;
    private static final float DEFAULT_PULL_ROT_Y = 0F;
    private static final float DEFAULT_PULL_ROT_Z = 0F;
    private static final boolean DEFAULT_HIT_DETECTION = false;
    private static final float DEFAULT_HIT_RADIUS = 0.1F;
    private static final float DEFAULT_INFLUENCE = SpringChainDef.DEFAULT_INFLUENCE;
    private static final float DEFAULT_WIND_POWER = WindDef.NONE.power();
    private static final float DEFAULT_WIND_DIR_X = WindDef.NONE.dirX();
    private static final float DEFAULT_WIND_DIR_Y = WindDef.NONE.dirY();
    private static final float DEFAULT_WIND_DIR_Z = WindDef.NONE.dirZ();
    private static final float DEFAULT_WIND_GUSTINESS = WindDef.NONE.gustiness();
    private static final float DEFAULT_WIND_GUST_SPEED = WindDef.NONE.gustSpeed();
    private static final float DEFAULT_WIND_GUST_SCALE = WindDef.NONE.gustScale();
    private static final boolean DEFAULT_WIND_MODEL_RELATIVE = WindDef.NONE.modelRelative();

    private SpringChainSerializer()
    {
    }

    public static SpringChainsConfig fromData(MapType map)
    {
        if (map == null || !map.has(KEY_CHAINS, BaseType.TYPE_MAP))
        {
            return null;
        }

        MapType chains = map.getMap(KEY_CHAINS);
        Map<String, SpringChainDef> out = new HashMap<>();

        for (String root : chains.keys())
        {
            if (!chains.has(root, BaseType.TYPE_MAP))
            {
                continue;
            }

            MapType entry = chains.getMap(root);
            String endBone = entry.getString(KEY_END_BONE);
            String pinTarget = entry.getString(KEY_PIN_TARGET, "");

            if (root == null || root.isEmpty() || endBone == null || endBone.isEmpty())
            {
                continue;
            }

            float pullStrength = entry.getFloat(KEY_PULL_STRENGTH, DEFAULT_PULL_STRENGTH);
            float drag = entry.getFloat(KEY_DRAG, DEFAULT_DRAG);
            float springReturn = entry.getFloat(KEY_SPRING_RETURN, DEFAULT_SPRING_RETURN);
            int relaxSteps = entry.getInt(KEY_RELAX_STEPS, DEFAULT_RELAX_STEPS);
            boolean bodyRelativePull = entry.getBool(KEY_BODY_RELATIVE_PULL, DEFAULT_BODY_RELATIVE_PULL);
            float pullRotX = entry.getFloat(KEY_PULL_ROT_X, DEFAULT_PULL_ROT_X);
            float pullRotY = entry.getFloat(KEY_PULL_ROT_Y, DEFAULT_PULL_ROT_Y);
            float pullRotZ = entry.getFloat(KEY_PULL_ROT_Z, DEFAULT_PULL_ROT_Z);
            boolean hitDetection = entry.getBool(KEY_HIT_DETECTION, DEFAULT_HIT_DETECTION);
            float hitRadius = entry.getFloat(KEY_HIT_RADIUS, DEFAULT_HIT_RADIUS);
            float influence = entry.getFloat(KEY_INFLUENCE, DEFAULT_INFLUENCE);

            out.put(root, new SpringChainDef(endBone, pinTarget, pullStrength, drag, springReturn, relaxSteps, bodyRelativePull, pullRotX, pullRotY, pullRotZ, hitDetection, hitRadius, influence));
        }

        WindDef wind = readWind(map);

        if (out.isEmpty() && wind.isDefault())
        {
            return null;
        }

        return new SpringChainsConfig(out, wind);
    }

    private static WindDef readWind(MapType map)
    {
        if (!map.has(KEY_WIND, BaseType.TYPE_MAP))
        {
            return WindDef.NONE;
        }

        MapType wind = map.getMap(KEY_WIND);
        float power = wind.getFloat(KEY_WIND_POWER, DEFAULT_WIND_POWER);
        float dirX = wind.getFloat(KEY_WIND_DIR_X, DEFAULT_WIND_DIR_X);
        float dirY = wind.getFloat(KEY_WIND_DIR_Y, DEFAULT_WIND_DIR_Y);
        float dirZ = wind.getFloat(KEY_WIND_DIR_Z, DEFAULT_WIND_DIR_Z);
        float gustiness = wind.getFloat(KEY_WIND_GUSTINESS, DEFAULT_WIND_GUSTINESS);
        float gustSpeed = wind.getFloat(KEY_WIND_GUST_SPEED, DEFAULT_WIND_GUST_SPEED);
        float gustScale = wind.getFloat(KEY_WIND_GUST_SCALE, DEFAULT_WIND_GUST_SCALE);
        boolean modelRelative = wind.getBool(KEY_WIND_MODEL_RELATIVE, DEFAULT_WIND_MODEL_RELATIVE);

        return new WindDef(power, dirX, dirY, dirZ, gustiness, gustSpeed, gustScale, modelRelative);
    }

    public static MapType toData(SpringChainsConfig config)
    {
        MapType root = new MapType();
        MapType chains = new MapType();

        if (config != null && config.chains() != null)
        {
            for (Map.Entry<String, SpringChainDef> entry : config.chains().entrySet())
            {
                String rootId = entry.getKey();
                SpringChainDef chain = entry.getValue();

                if (rootId == null || rootId.isEmpty() || chain == null || chain.endBone() == null || chain.endBone().isEmpty())
                {
                    continue;
                }

                MapType map = new MapType();
                map.putString(KEY_END_BONE, chain.endBone());

                if (chain.pinTarget() != null && !chain.pinTarget().isEmpty())
                {
                    map.putString(KEY_PIN_TARGET, chain.pinTarget());
                }

                map.putFloat(KEY_PULL_STRENGTH, chain.pullStrength());
                map.putFloat(KEY_DRAG, chain.drag());

                if (chain.springReturn() != DEFAULT_SPRING_RETURN)
                {
                    map.putFloat(KEY_SPRING_RETURN, chain.springReturn());
                }

                map.putInt(KEY_RELAX_STEPS, chain.relaxSteps());

                if (chain.bodyRelativePull())
                {
                    map.putBool(KEY_BODY_RELATIVE_PULL, true);
                }

                if (chain.pullRotX() != DEFAULT_PULL_ROT_X)
                {
                    map.putFloat(KEY_PULL_ROT_X, chain.pullRotX());
                }

                if (chain.pullRotY() != DEFAULT_PULL_ROT_Y)
                {
                    map.putFloat(KEY_PULL_ROT_Y, chain.pullRotY());
                }

                if (chain.pullRotZ() != DEFAULT_PULL_ROT_Z)
                {
                    map.putFloat(KEY_PULL_ROT_Z, chain.pullRotZ());
                }

                if (chain.hitDetection())
                {
                    map.putBool(KEY_HIT_DETECTION, true);
                }

                if (chain.hitRadius() != DEFAULT_HIT_RADIUS)
                {
                    map.putFloat(KEY_HIT_RADIUS, chain.hitRadius());
                }

                if (chain.influence() != DEFAULT_INFLUENCE)
                {
                    map.putFloat(KEY_INFLUENCE, chain.influence());
                }

                chains.put(rootId, map);
            }
        }

        root.put(KEY_CHAINS, chains);
        writeWind(root, config == null ? null : config.wind());

        return root;
    }

    private static void writeWind(MapType root, WindDef wind)
    {
        if (wind == null || wind.isDefault())
        {
            return;
        }

        MapType windMap = new MapType();

        if (wind.power() != DEFAULT_WIND_POWER)
        {
            windMap.putFloat(KEY_WIND_POWER, wind.power());
        }

        if (wind.dirX() != DEFAULT_WIND_DIR_X)
        {
            windMap.putFloat(KEY_WIND_DIR_X, wind.dirX());
        }

        if (wind.dirY() != DEFAULT_WIND_DIR_Y)
        {
            windMap.putFloat(KEY_WIND_DIR_Y, wind.dirY());
        }

        if (wind.dirZ() != DEFAULT_WIND_DIR_Z)
        {
            windMap.putFloat(KEY_WIND_DIR_Z, wind.dirZ());
        }

        if (wind.gustiness() != DEFAULT_WIND_GUSTINESS)
        {
            windMap.putFloat(KEY_WIND_GUSTINESS, wind.gustiness());
        }

        if (wind.gustSpeed() != DEFAULT_WIND_GUST_SPEED)
        {
            windMap.putFloat(KEY_WIND_GUST_SPEED, wind.gustSpeed());
        }

        if (wind.gustScale() != DEFAULT_WIND_GUST_SCALE)
        {
            windMap.putFloat(KEY_WIND_GUST_SCALE, wind.gustScale());
        }

        if (wind.modelRelative() != DEFAULT_WIND_MODEL_RELATIVE)
        {
            windMap.putBool(KEY_WIND_MODEL_RELATIVE, wind.modelRelative());
        }

        if (!windMap.isEmpty())
        {
            root.put(KEY_WIND, windMap);
        }
    }
}
