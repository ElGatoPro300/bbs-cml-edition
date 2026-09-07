package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.BBSFeatures;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.ITickable;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.forms.forms.utils.FormLighting;
import mchorse.bbs_mod.forms.forms.utils.GlowSettings;
import mchorse.bbs_mod.forms.forms.utils.Illusion;
import mchorse.bbs_mod.forms.forms.utils.InverseKinematics;
import mchorse.bbs_mod.forms.forms.utils.LightingSettings;
import mchorse.bbs_mod.forms.forms.utils.LookAt;
import mchorse.bbs_mod.forms.forms.utils.PaintSettings;
import mchorse.bbs_mod.forms.forms.utils.ShakeSettings;
import mchorse.bbs_mod.forms.forms.utils.TextureBlend;
import mchorse.bbs_mod.forms.states.AnimationState;
import mchorse.bbs_mod.forms.states.AnimationStates;
import mchorse.bbs_mod.forms.states.StatePlayer;
import mchorse.bbs_mod.forms.values.ValueAnchor;
import mchorse.bbs_mod.forms.values.ValueIllusion;
import mchorse.bbs_mod.forms.values.ValueInverseKinematics;
import mchorse.bbs_mod.forms.values.ValueLookAt;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.core.ValueTransform;
import mchorse.bbs_mod.settings.values.misc.ValueGlowSettings;
import mchorse.bbs_mod.settings.values.misc.ValuePaintSettings;
import mchorse.bbs_mod.settings.values.misc.ValueShakeSettings;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.keyframes.factories.ColorKeyframeFactory;
import mchorse.bbs_mod.utils.pose.Transform;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public abstract class Form extends ValueGroup
{
    public final ValueBoolean visible = new ValueBoolean("visible", true);
    public final ValueBoolean render = new ValueBoolean("render", true);
    public final ValueBoolean animatable = new ValueBoolean("animatable", true);
    public final ValueString trackName = new ValueString("track_name", "");
    public final ValueFloat lighting = new ValueFloat("lighting", 0F);
    public final ValueString name = new ValueString("name", "");
    public final ValueTransform transform = new ValueTransform("transform", new Transform());
    public final ValueTransform transformOverlay = new ValueTransform("transform_overlay", new Transform());
    public final ValueShakeSettings shake = new ValueShakeSettings("shake", new ShakeSettings());
    public final ValueFloat uiScale = new ValueFloat("uiScale", 1F);
    public final ValueAnchor anchor = new ValueAnchor("anchor", new Anchor());
    public final ValueLookAt lookAt = new ValueLookAt("look_at", new LookAt());
    public final ValueInverseKinematics inverseKinematics = new ValueInverseKinematics("inverse_kinematics", new InverseKinematics());
    public final ValueBoolean shaderShadow = new ValueBoolean("shaderShadow", true);
    /**
     * When true under Iris, this form uses the clean deferred opacity path for its
     * {@code color} alpha (same compositing as post-{@code #1b}) without affecting
     * paint redraw. Legacy films may still store this flag on Color keyframes.
     */
    public final ValueBoolean noshadingOpacity = new ValueBoolean("noshading_opacity", false);

    /* FS-style paint overlay: paintSettings controls color and intensity; paintColor is kept for backward compatibility */
    public final ValueColor paintColor = new ValueColor("paint_color", new Color().set(1F, 1F, 1F, 0F));
    public final ValuePaintSettings paintSettings = new ValuePaintSettings("paint", new PaintSettings());

    /* FS-style additive glow: glowingColor is RGB only; glowSettings controls brightness and spread */
    public final ValueColor glowingColor = new ValueColor("glowing_color", new Color().set(1F, 1F, 1F, 1F));
    public final ValueGlowSettings glowSettings = new ValueGlowSettings("glow", new GlowSettings());

    /* Illusions: purely visual duplicates of this form that spread away from it in
     * the picked directions (no extra entities, so they're cheap to render) */
    public final ValueIllusion illusion = new ValueIllusion("illusion", new Illusion());
    public final ValueIllusion illusionOverlay = new ValueIllusion("illusion_overlay", new Illusion());

    /* Extra transform that gets applied only to the illusions (optionally gradually
     * from the first illusion to the last one, see Illusion.gradual) */
    public final ValueTransform illusionTransform = new ValueTransform("illusion_transform", new Transform());
    public final ValueTransform illusionTransformOverlay = new ValueTransform("illusion_transform_overlay", new Transform());

    public final List<ValueTransform> additionalTransforms = new ArrayList<>();
    public final List<ValueIllusion> additionalIllusions = new ArrayList<>();
    public final List<ValueTransform> additionalIllusionTransforms = new ArrayList<>();

    /* Hitbox properties */
    public final ValueBoolean hitbox = new ValueBoolean("hitbox", false);
    public final ValueFloat hitboxWidth = new ValueFloat("hitboxWidth", 0.5F);
    public final ValueFloat hitboxHeight = new ValueFloat("hitboxHeight", 1.8F);
    public final ValueFloat hitboxSneakMultiplier = new ValueFloat("hitboxSneakMultiplier", 0.9F);
    public final ValueFloat hitboxEyeHeight = new ValueFloat("hitboxEyeHeight", 0.9F);

    /* Morphing properties */
    public final ValueFloat hp = new ValueFloat("hp", 20F);
    public final ValueFloat speed = new ValueFloat("movement_speed", 0.1F);
    public final ValueFloat stepHeight = new ValueFloat("step_height", 0.6F);
    /**
     * Default actor-mode film invulnerability when the replay {@code invulnerable}
     * keyframe track is empty. Keyframes on that track override this.
     */
    public final ValueBoolean filmInvulnerable = new ValueBoolean("film_invulnerable", false);

    public final ValueInt hotkey = new ValueInt("keybind", 0);

    public final BodyPartManager parts = new BodyPartManager("parts");
    public final AnimationStates states = new AnimationStates("states");

    protected Object renderer;
    protected String cachedID;

    /** Bumped when any nested value changes; used for incremental entity sync and UI preview cache. */
    private transient int editRevision = 0;

    /** Runtime texture crossfade state driven by the film texture track bend keyframes. */
    public transient TextureBlend textureBlend;

    /** Runtime texture crossfade between illusion keyframes with bend enabled. */
    public transient TextureBlend illusionTextureBlend;

    /**
     * Film lighting-track override. When non-null, renderers use this instead of only
     * {@link #lighting} (supports fixed absolute light levels).
     */
    public transient LightingSettings lightingSettings;

    private final List<StatePlayer> statePlayers = new ArrayList<>();

    public Form()
    {
        super("");

        this.animatable.invisible();
        this.trackName.invisible();
        this.name.invisible();
        this.uiScale.invisible();
        this.shaderShadow.invisible();
        this.noshadingOpacity.invisible();
        this.render.invisible();
        this.add(this.visible);
        this.add(this.render);
        this.add(this.animatable);
        this.add(this.trackName);
        this.add(this.lighting);
        this.add(this.name);
        this.add(this.transform);
        this.add(this.transformOverlay);
        this.add(this.shake);

        for (int i = 0; i < BBSSettings.recordingPoseTransformOverlays.get(); i++)
        {
            ValueTransform valueTransform = new ValueTransform("transform_overlay" + i, new Transform());

            this.additionalTransforms.add(valueTransform);
            this.add(valueTransform);
        }

        this.add(this.uiScale);
        this.add(this.anchor);
        this.add(this.lookAt);
        this.add(this.inverseKinematics);
        this.add(this.shaderShadow);

        if (!BBSFeatures.isFormIkLookAtUiEnabled())
        {
            this.lookAt.invisible();
            this.inverseKinematics.invisible();
        }
        this.add(this.noshadingOpacity);
        this.add(this.paintColor);
        this.add(this.paintSettings);
        this.add(this.glowingColor);
        this.add(this.glowSettings);

        this.add(this.illusion);
        this.add(this.illusionOverlay);

        for (int i = 0; i < BBSSettings.recordingPoseTransformOverlays.get(); i++)
        {
            ValueIllusion valueIllusion = new ValueIllusion("illusion_overlay" + i, new Illusion());

            this.additionalIllusions.add(valueIllusion);
            this.add(valueIllusion);
        }

        this.add(this.illusionTransform);
        this.add(this.illusionTransformOverlay);

        this.illusionTransform.invisible();
        this.illusionTransformOverlay.invisible();

        for (int i = 0; i < BBSSettings.recordingPoseTransformOverlays.get(); i++)
        {
            ValueTransform valueTransform = new ValueTransform("illusion_transform_overlay" + i, new Transform());

            valueTransform.invisible();
            this.additionalIllusionTransforms.add(valueTransform);
            this.add(valueTransform);
        }

        this.hitbox.invisible();
        this.hitboxWidth.invisible();
        this.hitboxHeight.invisible();
        this.hitboxSneakMultiplier.invisible();
        this.hitboxEyeHeight.invisible();

        this.add(this.hitbox);
        this.add(this.hitboxWidth);
        this.add(this.hitboxHeight);
        this.add(this.hitboxSneakMultiplier);
        this.add(this.hitboxEyeHeight);

        this.hp.invisible();
        this.speed.invisible();
        this.stepHeight.invisible();
        this.filmInvulnerable.invisible();

        this.add(this.hp);
        this.add(this.speed);
        this.add(this.stepHeight);
        this.add(this.filmInvulnerable);

        this.hotkey.invisible();

        this.add(this.hotkey);

        this.add(this.parts);
        this.add(this.states);
    }

    public Object getRenderer()
    {
        return this.renderer;
    }

    public void setRenderer(Object renderer)
    {
        this.renderer = renderer;
    }

    public Form getParentForm()
    {
        BaseValue parentValue = this.getParent();

        while (parentValue != null)
        {
            if (parentValue instanceof Form form)
            {
                return form;
            }

            parentValue = parentValue.getParent();
        }

        return null;
    }

    /* Animation states */

    public boolean findState(int hotkey, IStateFoundCallback callback)
    {
        if (callback == null)
        {
            return false;
        }

        for (AnimationState state : this.states.getAllTyped())
        {
            if (state.keybind.get() == hotkey)
            {
                callback.acceptState(this, state);

                return true;
            }
        }

        return false;
    }

    public void clearStatePlayers()
    {
        this.statePlayers.clear();
    }

    public void playState(AnimationState state)
    {
        if (state != null)
        {
            if (state.looping.get())
            {
                for (StatePlayer statePlayer : this.statePlayers)
                {
                    if (statePlayer.getState() == state)
                    {
                        statePlayer.expire();

                        return;
                    }
                }
            }

            this.statePlayers.add(new StatePlayer(state));
        }
    }

    public void playState(String stateId)
    {
        this.playState(this.states.getById(stateId));
    }

    public void playMain()
    {
        this.clearStatePlayers();
        this.playState(this.states.getMainRandom());
    }

    public void applyStates(float transition)
    {
        for (StatePlayer statePlayer : this.statePlayers)
        {
            statePlayer.assignValues(this, transition);
        }
    }

    public void unapplyStates()
    {
        for (StatePlayer statePlayer : this.statePlayers)
        {
            statePlayer.resetValues(this);
        }
    }

    /* Morphing */

    public void onMorph(LivingEntity entity)
    {
        float hp = this.hp.get();
        float speed = this.speed.get();
        float stepHeight = this.stepHeight.get();

        if (hp != 20F)
        {
            entity.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(hp);
            entity.setHealth(hp);
        }
        if (speed != 0.1F) entity.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(speed);
        /* setStepHeight() was removed in 1.20.5+; step-up is GENERIC_STEP_HEIGHT now.
         * Default matches vanilla living/player step height (0.6). */
        if (stepHeight != 0.6F)
        {
            EntityAttributeInstance step = entity.getAttributeInstance(EntityAttributes.GENERIC_STEP_HEIGHT);

            if (step != null)
            {
                step.setBaseValue(stepHeight);
            }
        }
    }

    public void onDemorph(LivingEntity entity)
    {
        entity.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(20F);
        entity.setHealth(20F);
        entity.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(0.1F);

        EntityAttributeInstance step = entity.getAttributeInstance(EntityAttributes.GENERIC_STEP_HEIGHT);

        if (step != null)
        {
            /* Vanilla player / living default step height. */
            step.setBaseValue(0.6D);
        }
    }

    /* ID and display name */

    public String getFormId()
    {
        if (this.cachedID == null)
        {
            this.cachedID = BBSMod.getForms().getType(this).toString();
        }

        return this.cachedID;
    }

    public String getFormIdOrName()
    {
        String name = this.name.get();

        return name.isEmpty() ? this.getFormId() : name;
    }

    public final String getDisplayName()
    {
        String name = this.name.get();

        if (!name.isEmpty())
        {
            return name;
        }

        return this.getDefaultDisplayName();
    }

    protected String getDefaultDisplayName()
    {
        return this.getFormId();
    }

    public final String getDefaultDisplayNameForHud()
    {
        return this.getDefaultDisplayName();
    }

    public String getTrackName(String property)
    {
        String s = this.trackName.get();

        if (!s.isEmpty())
        {
            if (property.isEmpty())
            {
                return s;
            }

            int slash = property.lastIndexOf('/');
            String last = slash == -1 ? property : property.substring(slash + 1);

            return s + (StringUtils.isInteger(last) ? "" : "/" + last);
        }

        return property;
    }

    /* Update */

    public void update(IEntity entity)
    {
        this.parts.update(entity);

        if (this.renderer instanceof ITickable)
        {
            ((ITickable) this.renderer).tick(entity);
        }

        Iterator<StatePlayer> it = this.statePlayers.iterator();

        while (it.hasNext())
        {
            StatePlayer next = it.next();

            next.update();

            if (next.canBeRemoved())
            {
                it.remove();
            }
        }
    }

    public int getEditRevision()
    {
        return this.editRevision;
    }

    @Override
    public void postNotify(BaseValue value, int flag)
    {
        this.editRevision += 1;

        super.postNotify(value, flag);
    }

    /* Data comparison and (de)serialization */

    @Override
    public void fromData(BaseType data)
    {
        if (data instanceof MapType map)
        {
            /* Compatibility with older forms */
            if (map.has("bodyParts"))
            {
                MapType bodyParts = map.getMap("bodyParts");

                if (bodyParts.has("parts"))
                {
                    map.remove("bodyParts");
                    map.put("parts", bodyParts.getList("parts"));
                }
            }

            if (map.has("glow_settings") && !map.has("glow"))
            {
                map.put("glow", map.get("glow_settings"));
                map.remove("glow_settings");
            }

            /* Drop removed render-depth feature keys from older morphs/films. */
            map.remove("render_depth");
            map.remove("render_depth_enabled");

            if (map.has("shake_amount") || map.has("shake_active"))
            {
                ShakeSettings settings = this.shake.get().copy();

                if (map.has("shake_amount"))
                {
                    BaseType amount = map.get("shake_amount");

                    if (amount.isNumeric())
                    {
                        settings.shakeAmount = (float) amount.asNumeric().doubleValue();
                    }

                    map.remove("shake_amount");
                }

                if (map.has("shake_active"))
                {
                    settings.active = map.getInt("shake_active", settings.active);
                    map.remove("shake_active");
                }

                if (map.has("shake") && map.get("shake").isNumeric())
                {
                    settings.shake = (float) map.get("shake").asNumeric().doubleValue();
                    map.remove("shake");
                }

                this.shake.set(settings);
            }
        }

        super.fromData(data);

        if (data instanceof MapType map)
        {
            /* Legacy lighting was world-influence (1=natural, 0/neg=full bright). */
            if (!map.getBool("lighting_v2") && map.has("lighting"))
            {
                BaseType lightingData = map.get("lighting");

                if (lightingData != null && lightingData.isNumeric())
                {
                    this.lighting.set(FormLighting.legacyToBrightness(lightingData.asNumeric().floatValue()));
                }
            }

            if (map.has("glow"))
            {
                MapType glowMap = map.getMap("glow");

                if (!glowMap.has("r") && map.has("glowing_color"))
                {
                    GlowSettings settings = this.glowSettings.get().copy();
                    Color glowing = this.glowingColor.get();

                    settings.r = glowing.r;
                    settings.g = glowing.g;
                    settings.b = glowing.b;
                    this.glowSettings.set(settings);
                }
            }

            if (map.has("paint"))
            {
                MapType paintMap = map.getMap("paint");

                if (!paintMap.has("r") && map.has("paint_color"))
                {
                    PaintSettings settings = this.paintSettings.get().copy();
                    Color legacy = this.paintColor.get();

                    settings.r = legacy.r;
                    settings.g = legacy.g;
                    settings.b = legacy.b;
                    settings.intensity = PaintSettings.resolveLegacyPaintIntensity(legacy);

                    if (legacy.transform != null && legacy.transform.isActive())
                    {
                        settings.transform = legacy.transform.copy();
                    }

                    this.paintSettings.set(settings);
                }
            }
            else if (map.has("paint_color"))
            {
                PaintSettings settings = this.paintSettings.get().copy();
                Color legacy = this.paintColor.get();

                settings.r = legacy.r;
                settings.g = legacy.g;
                settings.b = legacy.b;
                settings.intensity = PaintSettings.resolveLegacyPaintIntensity(legacy);

                if (legacy.transform != null && legacy.transform.isActive())
                {
                    settings.transform = legacy.transform.copy();
                }

                this.paintSettings.set(settings);
            }

            /* Compatibility with state triggers */
            FormUtils.readOldStateTriggers(this, map);

            /* One-shot merge: legacy era stored fade in "opacity" and tint strength in
             * color.a. Traditional color uses color.a as opacity; bake intensity into RGB. */
            this.mergeLegacyOpacityIntoColor(map);
        }
    }

    /**
     * Converts legacy Opacity-track form data into traditional {@code color.a} opacity.
     */
    private void mergeLegacyOpacityIntoColor(MapType map)
    {
        BaseValue colorValue = this.get("color");

        if (!(colorValue instanceof ValueColor valueColor))
        {
            return;
        }

        Color color = valueColor.get().copy();
        boolean hadOpacityField = map.has("opacity");
        boolean colorHadBlendA = colorDataHasBlendA(map.get("color"));

        if (hadOpacityField)
        {
            float opacityA = 1F;
            BaseType opacityType = map.get("opacity");

            if (opacityType != null && opacityType.isNumeric())
            {
                opacityA = MathUtils.clamp(opacityType.asNumeric().floatValue(), 0F, 1F);
            }

            /* Early Blend Color: color.a was tint intensity. Dual-write already baked via blend_a. */
            if (!colorHadBlendA)
            {
                float intensity = MathUtils.clamp(color.a, 0F, 1F);

                color.r = Lerps.lerp(1F, color.r, intensity);
                color.g = Lerps.lerp(1F, color.g, intensity);
                color.b = Lerps.lerp(1F, color.b, intensity);
            }

            color.a = opacityA;
            valueColor.set(color);
        }
        else if (!colorHadBlendA && color.a <= 0.001F)
        {
            /* Legacy tint-off default would be invisible under traditional alpha. */
            color.r = 1F;
            color.g = 1F;
            color.b = 1F;
            color.a = 1F;
            valueColor.set(color);
        }
    }

    private static boolean colorDataHasBlendA(BaseType colorData)
    {
        return colorData instanceof MapType colorMap && colorMap.has(ColorKeyframeFactory.BLEND_A);
    }

    /**
     * Soft-fade alpha for shadows / depth sorting. Reads traditional {@code color.a} when present.
     */
    public float getFormOpacity()
    {
        BaseValue colorValue = this.get("color");

        if (colorValue instanceof ValueColor valueColor)
        {
            return MathUtils.clamp(valueColor.get().a, 0F, 1F);
        }

        return 1F;
    }

    /**
     * Writes traditional form opacity ({@code color.a}) onto the render tint.
     */
    public void applyFormOpacity(Color color)
    {
        if (color == null)
        {
            return;
        }

        color.a = MathUtils.clamp(this.getFormOpacity(), 0F, 1F);
    }

    @Override
    public BaseType toData()
    {
        BaseType data = super.toData();

        if (data instanceof MapType map)
        {
            BBSMod.getForms().appendId(this, map);
            map.remove("opacity");

            /* ShapeForm replaces lighting with a boolean; only rewrite form float lighting. */
            if (this.get("lighting") instanceof ValueFloat valueFloat)
            {
                if (BBSSettings.isSaveAsCompatible())
                {
                    /* Older builds expect world-influence lighting and ignore lighting_v2. */
                    map.putFloat("lighting", FormLighting.brightnessToLegacy(valueFloat.get()));
                    map.remove("lighting_v2");
                }
                else
                {
                    map.putBool("lighting_v2", true);
                }
            }
        }

        return data;
    }
}