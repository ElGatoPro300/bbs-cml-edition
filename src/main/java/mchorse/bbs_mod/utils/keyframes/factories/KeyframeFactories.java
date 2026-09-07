package mchorse.bbs_mod.utils.keyframes.factories;

import java.util.HashMap;
import java.util.Map;

public class KeyframeFactories
{
    public static final Map<String, IKeyframeFactory> FACTORIES = new HashMap<>();

    public static final ColorKeyframeFactory COLOR = new ColorKeyframeFactory();
    public static final TransformKeyframeFactory TRANSFORM = new TransformKeyframeFactory();
    public static final PoseKeyframeFactory POSE = new PoseKeyframeFactory();
    public static final BooleanKeyframeFactory BOOLEAN = new BooleanKeyframeFactory();
    public static final StringKeyframeFactory STRING = new StringKeyframeFactory();
    public static final FloatKeyframeFactory FLOAT = new FloatKeyframeFactory();
    /** @deprecated Prefer {@link #LIGHTING_SETTINGS}; kept for loading brightness-only saves. */
    @Deprecated
    public static final FloatKeyframeFactory LIGHTING_BRIGHTNESS = new FloatKeyframeFactory();
    public static final LightingSettingsKeyframeFactory LIGHTING_SETTINGS = new LightingSettingsKeyframeFactory();
    public static final DoubleKeyframeFactory DOUBLE = new DoubleKeyframeFactory();
    public static final IntegerKeyframeFactory INTEGER = new IntegerKeyframeFactory();
    public static final LinkKeyframeFactory LINK = new LinkKeyframeFactory();
    public static final Vector4fKeyframeFactory VECTOR4F = new Vector4fKeyframeFactory();
    public static final AnchorKeyframeFactory ANCHOR = new AnchorKeyframeFactory();
    public static final MountLinkKeyframeFactory MOUNT_LINK = new MountLinkKeyframeFactory();
    public static final LookAtKeyframeFactory LOOK_AT = new LookAtKeyframeFactory();
    public static final InverseKinematicsKeyframeFactory INVERSE_KINEMATICS = new InverseKinematicsKeyframeFactory();
    public static final IllusionKeyframeFactory ILLUSION = new IllusionKeyframeFactory();
    public static final BlockStateKeyframeFactory BLOCK_STATE = new BlockStateKeyframeFactory();
    public static final ItemStackKeyframeFactory ITEM_STACK = new ItemStackKeyframeFactory();
    public static final ActionsConfigKeyframeFactory ACTIONS_CONFIG = new ActionsConfigKeyframeFactory();
    public static final ShapeKeysKeyframeFactory SHAPE_KEYS = new ShapeKeysKeyframeFactory();
    public static final ParticleSettingsKeyframeFactory PARTICLE_SETTINGS = new ParticleSettingsKeyframeFactory();
    public static final StructureLightSettingsKeyframeFactory STRUCTURE_LIGHT_SETTINGS = new StructureLightSettingsKeyframeFactory();
    public static final GlowSettingsKeyframeFactory GLOW_SETTINGS = new GlowSettingsKeyframeFactory();
    public static final PaintSettingsKeyframeFactory PAINT_SETTINGS = new PaintSettingsKeyframeFactory();
    public static final ShadowSettingsKeyframeFactory SHADOW_SETTINGS = new ShadowSettingsKeyframeFactory();
    public static final LensRadiusSettingsKeyframeFactory LENS_RADIUS_SETTINGS = new LensRadiusSettingsKeyframeFactory();
    public static final ChromaSkyCurveSettingsKeyframeFactory CHROMA_SKY_SETTINGS = new ChromaSkyCurveSettingsKeyframeFactory();
    public static final ShakeSettingsKeyframeFactory SHAKE_SETTINGS = new ShakeSettingsKeyframeFactory();

    public static boolean isNumeric(IKeyframeFactory factory)
    {
        return factory instanceof DoubleKeyframeFactory
            || factory instanceof FloatKeyframeFactory
            || factory instanceof IntegerKeyframeFactory;
    }

    static
    {
        FACTORIES.put("color", COLOR);
        FACTORIES.put("transform", TRANSFORM);
        FACTORIES.put("pose", POSE);
        FACTORIES.put("boolean", BOOLEAN);
        FACTORIES.put("string", STRING);
        FACTORIES.put("float", FLOAT);
        FACTORIES.put("lighting_brightness", LIGHTING_BRIGHTNESS);
        FACTORIES.put("lighting_settings", LIGHTING_SETTINGS);
        FACTORIES.put("double", DOUBLE);
        FACTORIES.put("integer", INTEGER);
        FACTORIES.put("link", LINK);
        FACTORIES.put("vector4f", VECTOR4F);
        FACTORIES.put("anchor", ANCHOR);
        FACTORIES.put("mount_link", MOUNT_LINK);
        FACTORIES.put("look_at", LOOK_AT);
        FACTORIES.put("inverse_kinematics", INVERSE_KINEMATICS);
        FACTORIES.put("illusion", ILLUSION);
        FACTORIES.put("block_state", BLOCK_STATE);
        FACTORIES.put("item_stack", ITEM_STACK);
        FACTORIES.put("actions_config", ACTIONS_CONFIG);
        FACTORIES.put("shape_keys", SHAPE_KEYS);
        FACTORIES.put("particle_settings", PARTICLE_SETTINGS);
        FACTORIES.put("structure_light_settings", STRUCTURE_LIGHT_SETTINGS);
        FACTORIES.put("glow_settings", GLOW_SETTINGS);
        FACTORIES.put("glow", GLOW_SETTINGS);
        FACTORIES.put("paint_settings", PAINT_SETTINGS);
        FACTORIES.put("paint", PAINT_SETTINGS);
        FACTORIES.put("shadow_settings", SHADOW_SETTINGS);
        FACTORIES.put("shadow", SHADOW_SETTINGS);
        FACTORIES.put("lens_radius_settings", LENS_RADIUS_SETTINGS);
        FACTORIES.put("chroma_sky_settings", CHROMA_SKY_SETTINGS);
        FACTORIES.put("shake_settings", SHAKE_SETTINGS);
        FACTORIES.put("shake", SHAKE_SETTINGS);
    }
}
