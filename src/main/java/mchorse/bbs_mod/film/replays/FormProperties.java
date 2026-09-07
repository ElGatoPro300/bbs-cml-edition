package mchorse.bbs_mod.film.replays;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.IntType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.EffectTransform;
import mchorse.bbs_mod.forms.forms.utils.FormLighting;
import mchorse.bbs_mod.forms.forms.utils.GlowSettings;
import mchorse.bbs_mod.forms.forms.utils.Illusion;
import mchorse.bbs_mod.forms.forms.utils.LightingSettings;
import mchorse.bbs_mod.forms.forms.utils.PaintSettings;
import mchorse.bbs_mod.forms.forms.utils.StructureLightSettings;
import mchorse.bbs_mod.forms.forms.utils.TextureBlend;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.base.BaseKeyframeFactoryValue;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValuePose;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import mchorse.bbs_mod.utils.keyframes.factories.ColorKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;
import mchorse.bbs_mod.utils.resources.LinkUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public class FormProperties extends ValueGroup
{
    public final Map<String, KeyframeChannel> properties = new HashMap<>();

    public FormProperties(String id)
    {
        super(id);
    }

    public void shift(float tick)
    {
        for (KeyframeChannel<?> value : this.properties.values())
        {
            for (Keyframe<?> keyframe : value.getKeyframes())
            {
                keyframe.setTick(keyframe.getTick() + tick);
            }
        }
    }

    public KeyframeChannel getOrCreate(Form form, String key)
    {
        BaseValue value = this.get(key);

        if (value instanceof KeyframeChannel channel)
        {
            return channel;
        }

        /* Color grade is a synthetic sibling of Color — no Form property, own channel. */
        if (isColorGradeChannelKey(key))
        {
            String colorPath = colorPropertyPathForGrade(key);
            BaseValue colorProperty = FormUtils.getProperty(form, colorPath);

            if (colorProperty instanceof ValueColor)
            {
                KeyframeChannel channel = new KeyframeChannel(key, KeyframeFactories.COLOR);

                channel.setModel(true);
                this.properties.put(key, channel);
                this.add(channel);

                return channel;
            }

            return null;
        }

        if (PerLimbService.isMaterialTextureChannel(key))
        {
            KeyframeChannel channel = new KeyframeChannel(key, KeyframeFactories.LINK);

            channel.setModel(true);
            this.properties.put(key, channel);
            this.add(channel);

            return channel;
        }

        int colon = key.indexOf(':');

        if (colon != -1)
        {
            String propertyId = key.substring(0, colon);
            BaseValue property = FormUtils.getProperty(form, propertyId);

            if (property instanceof ValuePose)
            {
                KeyframeChannel channel = new KeyframeChannel(key, KeyframeFactories.TRANSFORM);

                channel.setModel(true);
                this.properties.put(key, channel);
                this.add(channel);

                return channel;
            }
        }

        BaseValue property = FormUtils.getProperty(form, key);

        return property != null ? this.create(property) : null;
    }

    public static boolean isColorGradeChannelKey(String key)
    {
        if (key == null || key.isEmpty())
        {
            return false;
        }

        int colon = key.indexOf(':');
        String path = colon == -1 ? key : key.substring(0, colon);

        return path.equals("color_grade") || path.endsWith("/color_grade");
    }

    private static boolean isWorldLightingChannelKey(String key)
    {
        return key != null && (key.equals("lighting") || key.endsWith("/lighting"));
    }

    public static String colorPropertyPathForGrade(String gradeKey)
    {
        int colon = gradeKey.indexOf(':');
        String path = colon == -1 ? gradeKey : gradeKey.substring(0, colon);
        String colorPath;

        if (path.equals("color_grade"))
        {
            colorPath = "color";
        }
        else if (path.endsWith("/color_grade"))
        {
            colorPath = path.substring(0, path.length() - "color_grade".length()) + "color";
        }
        else
        {
            colorPath = "color";
        }

        if (colon == -1)
        {
            return colorPath;
        }

        return colorPath + gradeKey.substring(colon);
    }

    public KeyframeChannel create(BaseValue property)
    {
        if (property instanceof BaseKeyframeFactoryValue<?> keyframeFactoryValue)
        {
            String key = FormUtils.getPropertyPath(property);
            boolean allowed = property.isVisible() || FormUtils.isRenderPropertyPath(key);

            if (allowed)
            {
                IKeyframeFactory factory = keyframeFactoryValue.getFactory();

                if (isWorldLightingChannelKey(key) && (factory == KeyframeFactories.FLOAT || factory == KeyframeFactories.LIGHTING_BRIGHTNESS))
                {
                    factory = KeyframeFactories.LIGHTING_SETTINGS;
                }

                KeyframeChannel channel = new KeyframeChannel(key, factory);

                channel.setModel(true);
                this.properties.put(key, channel);
                this.add(channel);

                return channel;
            }
        }

        return null;
    }

    public void applyProperties(Form form, float tick)
    {
        this.applyProperties(form, tick, 1F);
    }

    public void applyProperties(Form form, float tick, float blend)
    {
        if (form == null)
        {
            return;
        }

        form.lightingSettings = null;

        ArrayList<KeyframeChannel> deferredColorGrade = new ArrayList<>();

        /* First pass: apply standard properties (defer color_grade until after color). */
        for (KeyframeChannel value : this.properties.values())
        {
            if (value.getId().indexOf(':') == -1)
            {
                if (isColorGradeChannelKey(value.getId()))
                {
                    deferredColorGrade.add(value);
                }
                else
                {
                    this.applyProperty(tick, form, value, blend);
                }
            }
        }

        /* Second pass: apply limb tracks (which override standard properties) */
        for (KeyframeChannel value : this.properties.values())
        {
            if (value.getId().indexOf(':') != -1)
            {
                this.applyProperty(tick, form, value, blend);
            }
        }

        for (KeyframeChannel grade : deferredColorGrade)
        {
            this.applyColorGradeProperty(tick, form, grade, blend);
        }
    }

    private void applyProperty(float tick, Form form, KeyframeChannel value, float blend)
    {
        PerLimbService.MaterialTexturePath materialPath = PerLimbService.parseMaterialTexturePath(value.getId());

        if (materialPath != null)
        {
            Form targetForm = FormUtils.getForm(form, materialPath.formPath());

            if (targetForm instanceof ModelForm modelForm)
            {
                KeyframeSegment segment = value.find(tick);

                if (segment != null)
                {
                    modelForm.materialTextureOverrides.put(materialPath.material(), (Link) segment.createInterpolated());
                }
                else if (blend >= 1F)
                {
                    modelForm.materialTextureOverrides.remove(materialPath.material());
                }
            }

            return;
        }

        String id = value.getId();
        int colon = id.indexOf(':');

        if (colon != -1)
        {
            String propertyId = id.substring(0, colon);
            String boneName = id.substring(colon + 1);
            BaseValueBasic property = FormUtils.getProperty(form, propertyId);

            if (property instanceof ValuePose valuePose)
            {
                KeyframeSegment segment = value.find(tick);

                if (segment != null)
                {
                    Transform transform = (Transform) segment.createInterpolated();
                    Pose pose = valuePose.getRuntimeValue();

                    if (pose == null)
                    {
                        pose = new Pose();
                        valuePose.setRuntimeValue(pose);
                    }

                    PoseTransform poseTransform = pose.get(boneName);

                    if (blend < 1F)
                    {
                        poseTransform.translate.add(transform.translate.x * blend, transform.translate.y * blend, transform.translate.z * blend);
                        poseTransform.scale.mul(1F + (transform.scale.x - 1F) * blend, 1F + (transform.scale.y - 1F) * blend, 1F + (transform.scale.z - 1F) * blend);
                        poseTransform.rotate.add(transform.rotate.x * blend, transform.rotate.y * blend, transform.rotate.z * blend);
                        poseTransform.rotate2.add(transform.rotate2.x * blend, transform.rotate2.y * blend, transform.rotate2.z * blend);
                        poseTransform.pivot.add(transform.pivot.x * blend, transform.pivot.y * blend, transform.pivot.z * blend);
                    }
                    else
                    {
                        poseTransform.translate.add(transform.translate);
                        poseTransform.scale.mul(transform.scale);
                        poseTransform.rotate.add(transform.rotate);
                        poseTransform.rotate2.add(transform.rotate2);
                        poseTransform.pivot.add(transform.pivot);
                    }

                    PoseTransform sourcePose = null;

                    if (transform instanceof PoseTransform transformPose)
                    {
                        sourcePose = transformPose;
                    }
                    else
                    {
                        Object closestValue = segment.getClosest().getValue();

                        if (closestValue instanceof PoseTransform closestPose)
                        {
                            sourcePose = closestPose;
                        }
                    }

                    if (sourcePose != null)
                    {
                        if (blend < 1F)
                        {
                            poseTransform.fix = Lerps.lerp(poseTransform.fix, sourcePose.fix, blend);
                            poseTransform.color.r = Lerps.lerp(poseTransform.color.r, sourcePose.color.r, blend);
                            poseTransform.color.g = Lerps.lerp(poseTransform.color.g, sourcePose.color.g, blend);
                            poseTransform.color.b = Lerps.lerp(poseTransform.color.b, sourcePose.color.b, blend);
                            poseTransform.color.a = Lerps.lerp(poseTransform.color.a, sourcePose.color.a, blend);
                            poseTransform.paintColor.r = Lerps.lerp(poseTransform.paintColor.r, sourcePose.paintColor.r, blend);
                            poseTransform.paintColor.g = Lerps.lerp(poseTransform.paintColor.g, sourcePose.paintColor.g, blend);
                            poseTransform.paintColor.b = Lerps.lerp(poseTransform.paintColor.b, sourcePose.paintColor.b, blend);
                            poseTransform.paintColor.a = Lerps.lerp(poseTransform.paintColor.a, sourcePose.paintColor.a, blend);
                            poseTransform.glowingColor.r = Lerps.lerp(poseTransform.glowingColor.r, sourcePose.glowingColor.r, blend);
                            poseTransform.glowingColor.g = Lerps.lerp(poseTransform.glowingColor.g, sourcePose.glowingColor.g, blend);
                            poseTransform.glowingColor.b = Lerps.lerp(poseTransform.glowingColor.b, sourcePose.glowingColor.b, blend);
                            poseTransform.glowingColor.a = 1F;
                            poseTransform.glowIntensity = Lerps.lerp(poseTransform.glowIntensity, sourcePose.glowIntensity, blend);
                            poseTransform.glowRadius = Lerps.lerp(poseTransform.glowRadius, sourcePose.glowRadius, blend);
                            poseTransform.lighting = Lerps.lerp(poseTransform.lighting, sourcePose.lighting, blend);
                            poseTransform.noshadingOpacity = poseTransform.noshadingOpacity || sourcePose.noshadingOpacity;
                            poseTransform.shaderShadow = PaintSettings.resolveAutoShaderShadowForPoseAlpha(poseTransform.paintColor.a);
                        }
                        else
                        {
                            poseTransform.fix = sourcePose.fix;
                            poseTransform.color.copy(sourcePose.color);
                            poseTransform.paintColor.copy(sourcePose.paintColor);
                            poseTransform.glowingColor.copy(sourcePose.glowingColor);
                            poseTransform.glowingColor.a = 1F;
                            poseTransform.glowIntensity = sourcePose.glowIntensity;
                            poseTransform.glowRadius = sourcePose.glowRadius;
                            poseTransform.lighting = sourcePose.lighting;
                            poseTransform.noshadingOpacity = sourcePose.noshadingOpacity;
                            poseTransform.shaderShadow = PaintSettings.resolveAutoShaderShadowForPoseAlpha(poseTransform.paintColor.a);
                        }

                        this.applyPoseBoneTexture(poseTransform, segment, transform);
                    }
                }

                return;
            }
        }

        BaseValueBasic property = FormUtils.getProperty(form, id);

        if (property == null)
        {
            if (isColorGradeChannelKey(id))
            {
                this.applyColorGradeProperty(tick, form, value, blend);
            }

            return;
        }

        if (FormUtils.isRenderPropertyPath(id) && value.getFactory() == KeyframeFactories.BOOLEAN)
        {
            @SuppressWarnings("unchecked")
            KeyframeChannel<Boolean> render = (KeyframeChannel<Boolean>) value;

            this.applyRenderProperty(tick, property, render);

            return;
        }

        if (value.getFactory() == KeyframeFactories.LIGHTING_SETTINGS)
        {
            this.applyLightingProperty(tick, form, property, value, blend);

            return;
        }

        KeyframeSegment segment = value.find(tick);

        if (segment != null)
        {
            if ("texture".equals(id))
            {
                this.applyTextureBlend(form, segment);
            }

            if (id.startsWith("illusion"))
            {
                this.applyIllusionTextureBlend(form, segment);
            }

            /* Always sync true/false from the Color keyframe — only setting true left the
             * runtime stuck on after toggling Noshading off (BBS deferred / no body shadows). */
            if ("color".equals(id) || id.endsWith("/color"))
            {
                form.noshadingOpacity.setRuntimeValue(segment.getClosest().isNoshadingOpacity());
            }

            if (blend < 1F)
            {
                IKeyframeFactory factory = value.getFactory();
                Object v = factory.copy(property.get());
                Object a = factory.copy(segment.createInterpolated());
                Object interpolated = factory.interpolate(v, v, a, a, Interpolations.LINEAR, MathUtils.clamp(blend, 0F, 1F));

                property.setRuntimeValue(coerceRuntimeValue(property, factory.copy(interpolated)));
            }
            else
            {
                property.setRuntimeValue(coerceRuntimeValue(property, segment.createInterpolated()));
            }

            /* Color track keyframes are often RGBA-only; keep morph Color Grade unless
             * the track itself keyframes brightness/contrast/hue/saturation. */
            if ("color".equals(id) || id.endsWith("/color"))
            {
                this.mergeColorAdjustmentsFromForm(property, value, id);
            }
        }
        else
        {
            if ("texture".equals(id))
            {
                form.textureBlend = null;
            }

            if (id.startsWith("illusion"))
            {
                form.illusionTextureBlend = null;
            }

            if ("color".equals(id) || id.endsWith("/color"))
            {
                form.noshadingOpacity.setRuntimeValue(null);
            }

            if (isWorldLightingChannelKey(id))
            {
                form.lightingSettings = null;
            }

            property.setRuntimeValue(null);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void applyLightingProperty(float tick, Form form, BaseValueBasic property, KeyframeChannel channel, float blend)
    {
        KeyframeSegment segment = channel.find(tick);

        if (segment == null)
        {
            form.lightingSettings = null;
            property.setRuntimeValue(null);

            return;
        }

        Object interpolated = segment.createInterpolated();
        LightingSettings settings = interpolated instanceof LightingSettings lighting
            ? lighting.copy()
            : new LightingSettings();

        if (blend < 1F)
        {
            LightingSettings base = form.lightingSettings != null
                ? form.lightingSettings.copy()
                : LightingSettings.fromBrightness(property.get() instanceof Number number ? number.floatValue() : 0F);
            IKeyframeFactory factory = channel.getFactory();
            Object mixed = factory.interpolate(base, base, settings, settings, Interpolations.LINEAR, MathUtils.clamp(blend, 0F, 1F));

            settings = mixed instanceof LightingSettings lighting ? lighting.copy() : settings;
        }

        form.lightingSettings = settings;

        if (settings.fixed)
        {
            /* Keep morph brightness untouched while fixed light level drives rendering. */
            property.setRuntimeValue(null);
        }
        else
        {
            property.setRuntimeValue(FormLighting.clampBrightness(settings.brightness));
        }
    }

    /**
     * Overlay brightness / contrast / hue / saturation from the independent color_grade
     * channel onto the form Color runtime (after the Color track has been applied).
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void applyColorGradeProperty(float tick, Form form, KeyframeChannel gradeChannel, float blend)
    {
        if (gradeChannel == null || gradeChannel.getFactory() != KeyframeFactories.COLOR)
        {
            return;
        }

        String colorPath = colorPropertyPathForGrade(gradeChannel.getId());
        BaseValueBasic property = FormUtils.getProperty(form, colorPath);

        if (!(property instanceof ValueColor valueColor))
        {
            return;
        }

        KeyframeSegment segment = gradeChannel.find(tick);

        if (segment == null)
        {
            return;
        }

        Object interpolated = segment.createInterpolated();

        if (!(interpolated instanceof Color grade))
        {
            return;
        }

        Color runtime = valueColor.getRuntimeValue() instanceof Color runtimeColor
            ? runtimeColor
            : null;

        if (runtime == null)
        {
            Color base = valueColor.getOriginalValue();

            runtime = base == null ? new Color(1F, 1F, 1F, 1F) : base.copy();
            valueColor.setRuntimeValue(runtime);
        }

        if (blend < 1F)
        {
            runtime.brightness = Lerps.lerp(runtime.brightness, grade.brightness, blend);
            runtime.contrast = Lerps.lerp(runtime.contrast, grade.contrast, blend);
            runtime.hue = Lerps.lerp(runtime.hue, grade.hue, blend);
            runtime.saturation = Lerps.lerp(runtime.saturation, grade.saturation, blend);
        }
        else
        {
            runtime.brightness = grade.brightness;
            runtime.contrast = grade.contrast;
            runtime.hue = grade.hue;
            runtime.saturation = grade.saturation;
            runtime.brightnessTransform = grade.brightnessTransform == null ? new EffectTransform() : grade.brightnessTransform.copy();
            runtime.contrastTransform = grade.contrastTransform == null ? new EffectTransform() : grade.contrastTransform.copy();
            runtime.hueTransform = grade.hueTransform == null ? new EffectTransform() : grade.hueTransform.copy();
            runtime.saturationTransform = grade.saturationTransform == null ? new EffectTransform() : grade.saturationTransform.copy();
        }
    }

    private void applyRenderProperty(float tick, BaseValueBasic property, KeyframeChannel<Boolean> channel)
    {
        if (channel.isEmpty())
        {
            property.setRuntimeValue(null);

            return;
        }

        /* Hold first keyframe before its tick (same as KeyframeChannel.findSegment). */
        property.setRuntimeValue(channel.interpolate(tick, true));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object coerceRuntimeValue(BaseValueBasic property, Object value)
    {
        if (property instanceof ValueBoolean)
        {
            if (value instanceof Boolean)
            {
                return value;
            }

            if (value instanceof Number)
            {
                return ((Number) value).floatValue() > 0.001F;
            }

            return value != null;
        }

        return value;
    }

    /**
     * Film Color tracks often store only RGB + opacity. Morph-level brightness /
     * contrast / hue / saturation (and their transforms) must still apply unless the
     * Color track itself keyframes them.
     */
    @SuppressWarnings("rawtypes")
    private void mergeColorAdjustmentsFromForm(BaseValueBasic property, KeyframeChannel channel, String colorKey)
    {
        if (!(property instanceof ValueColor valueColor))
        {
            return;
        }

        Object runtimeObj = valueColor.getRuntimeValue();

        if (!(runtimeObj instanceof Color runtime))
        {
            return;
        }

        Color base = valueColor.getOriginalValue();

        if (base == null)
        {
            return;
        }

        String gradeKey = colorKey.equals("color")
            ? "color_grade"
            : colorKey.substring(0, colorKey.length() - "color".length()) + "color_grade";
        KeyframeChannel gradeChannel = this.properties.get(gradeKey);

        /* Independent color_grade channel owns grade when it has keyframes. */
        if (gradeChannel != null && !gradeChannel.isEmpty() && this.colorChannelHasAdjustments(gradeChannel))
        {
            return;
        }

        if (!this.colorChannelHasAdjustments(channel) && base.hasColorAdjustments())
        {
            runtime.brightness = base.brightness;
            runtime.contrast = base.contrast;
            runtime.hue = base.hue;
            runtime.saturation = base.saturation;
        }

        if (gradeChannel != null && !gradeChannel.isEmpty() && this.colorChannelHasGradeTransforms(gradeChannel))
        {
            return;
        }

        if (!this.colorChannelHasGradeTransforms(channel) && base.hasActiveGradeTransform())
        {
            runtime.brightnessTransform = base.brightnessTransform == null ? new EffectTransform() : base.brightnessTransform.copy();
            runtime.contrastTransform = base.contrastTransform == null ? new EffectTransform() : base.contrastTransform.copy();
            runtime.hueTransform = base.hueTransform == null ? new EffectTransform() : base.hueTransform.copy();
            runtime.saturationTransform = base.saturationTransform == null ? new EffectTransform() : base.saturationTransform.copy();
        }
    }

    @SuppressWarnings("rawtypes")
    private boolean colorChannelHasAdjustments(KeyframeChannel channel)
    {
        if (channel == null)
        {
            return false;
        }

        for (Object kfObj : channel.getKeyframes())
        {
            Keyframe<?> keyframe = (Keyframe<?>) kfObj;
            Object value = keyframe.getValue();

            if (value instanceof Color color && color.hasColorAdjustments())
            {
                return true;
            }
        }

        return false;
    }

    @SuppressWarnings("rawtypes")
    private boolean colorChannelHasGradeTransforms(KeyframeChannel channel)
    {
        if (channel == null)
        {
            return false;
        }

        for (Object kfObj : channel.getKeyframes())
        {
            Keyframe<?> keyframe = (Keyframe<?>) kfObj;
            Object value = keyframe.getValue();

            if (value instanceof Color color && color.hasActiveGradeTransform())
            {
                return true;
            }
        }

        return false;
    }

    private void applyTextureBlend(Form form, KeyframeSegment segment)
    {
        if (segment.a.isBend() && !segment.isSame())
        {
            float blendFactor = getSegmentBlendFactor(segment);
            Link from = LinkUtils.copy((Link) segment.a.getValue());
            Link to = LinkUtils.copy((Link) segment.b.getValue());

            if (form.textureBlend == null)
            {
                form.textureBlend = new TextureBlend();
            }

            form.textureBlend.from = from;
            form.textureBlend.to = to;
            form.textureBlend.blend = blendFactor;
        }
        else
        {
            form.textureBlend = null;
        }
    }

    private void applyIllusionTextureBlend(Form form, KeyframeSegment segment)
    {
        if (segment.a.isBend() && !segment.isSame())
        {
            Illusion fromIllusion = (Illusion) segment.a.getValue();
            Illusion toIllusion = (Illusion) segment.b.getValue();
            Link from = this.getIllusionPrimaryTexture(fromIllusion);
            Link to = this.getIllusionPrimaryTexture(toIllusion);

            if (from != null && to != null && !from.equals(to))
            {
                float blendFactor = getSegmentBlendFactor(segment);

                if (form.illusionTextureBlend == null)
                {
                    form.illusionTextureBlend = new TextureBlend();
                }

                form.illusionTextureBlend.from = LinkUtils.copy(from);
                form.illusionTextureBlend.to = LinkUtils.copy(to);
                form.illusionTextureBlend.blend = blendFactor;

                return;
            }
        }

        form.illusionTextureBlend = null;
    }

    private Link getIllusionPrimaryTexture(Illusion illusion)
    {
        if (illusion == null || illusion.textures.isEmpty())
        {
            return null;
        }

        return illusion.textures.get(0);
    }

    private static float getSegmentBlendFactor(KeyframeSegment segment)
    {
        return (float) segment.a.getInterpolation().interpolate(0D, 1D, segment.x);
    }

    private static PoseTransform getKeyframeBoneTransform(Keyframe<?> keyframe, String boneName)
    {
        if (keyframe == null)
        {
            return null;
        }

        Object value = keyframe.getValue();

        if (value instanceof PoseTransform poseTransform)
        {
            return poseTransform;
        }

        if (value instanceof Pose pose)
        {
            return pose.get(boneName);
        }

        return null;
    }

    /**
     * Applies bone texture crossfade for pose overlay limb tracks, matching the
     * form-level texture timeline blend (from fades out, to fades in).
     */
    private void applyPoseBoneTexture(PoseTransform target, KeyframeSegment segment, Transform transform)
    {
        if (segment.a.isBend() && !segment.isSame()
            && segment.a.getValue() instanceof PoseTransform aPose
            && segment.b.getValue() instanceof PoseTransform bPose
            && aPose.texture != null && bPose.texture != null
            && !aPose.texture.equals(bPose.texture))
        {
            target.texture = LinkUtils.copy(aPose.texture);
            target.textureBlendTo = LinkUtils.copy(bPose.texture);
            target.textureBlend = getSegmentBlendFactor(segment);

            return;
        }

        if (transform instanceof PoseTransform pose)
        {
            PoseTransform pick = pose;

            if (segment.a.getValue() instanceof PoseTransform aPose)
            {
                pick = aPose;

                if (segment.x >= 1F && segment.b.getValue() instanceof PoseTransform bPose)
                {
                    pick = bPose;
                }
            }

            target.texture = pick.texture != null ? LinkUtils.copy(pick.texture) : null;
            target.textureBlendTo = pose.textureBlendTo != null ? LinkUtils.copy(pose.textureBlendTo) : null;
            target.textureBlend = pose.textureBlend;
        }
    }

    public void resetProperties(Form form)
    {
        if (form == null)
        {
            return;
        }

        form.textureBlend = null;
        form.illusionTextureBlend = null;

        if (form instanceof ModelForm modelForm)
        {
            modelForm.materialTextureOverrides.clear();
        }

        for (KeyframeChannel value : this.properties.values())
        {
            String id = value.getId();
            int colon = id.indexOf(':');

            if (colon != -1)
            {
                String propertyId = id.substring(0, colon);
                BaseValueBasic property = FormUtils.getProperty(form, propertyId);

                if (property instanceof ValuePose valuePose)
                {
                    valuePose.setRuntimeValue(null);
                }
            }

            BaseValueBasic property = FormUtils.getProperty(form, id);

            if (property == null)
            {
                continue;
            }

            property.setRuntimeValue(null);
        }
    }

    public void cleanUp()
    {
        Iterator<KeyframeChannel> it = this.properties.values().iterator();

        while (it.hasNext())
        {
            KeyframeChannel next = it.next();

            if (next.isEmpty())
            {
                it.remove();
                this.remove(next);
            }
        }
    }

    /**
     * Paint is edited from the Color inspector but stored on a hidden {@code "paint"}
     * channel. When Color keyframes at {@code ticks} are removed, drop matching paint
     * companions so leftover paint does not keep rendering. Color grade has its own
     * channel and must not drive paint companions.
     */
    public void removeCompanionPaintAtTicks(Form form, Collection<Float> ticks)
    {
        if (ticks == null || ticks.isEmpty())
        {
            return;
        }

        this.removeKeyframesAtTicks("paint", ticks);
        this.removeKeyframesAtTicks("paint_color", ticks);

        KeyframeChannel color = this.properties.get("color");

        if (color == null || color.isEmpty())
        {
            this.clearPaintChannels(form);
        }
        else
        {
            this.clearPaintRuntimeIfChannelsEmpty(form);
        }
    }

    /**
     * Move hidden paint companions by the same delta as Color keyframes.
     */
    public void moveCompanionPaintBy(float diff, Collection<Float> fromTicks)
    {
        if (Math.abs(diff) < 0.0001F || fromTicks == null || fromTicks.isEmpty())
        {
            return;
        }

        this.moveKeyframesBy("paint", diff, fromTicks);
        this.moveKeyframesBy("paint_color", diff, fromTicks);
    }

    private void clearPaintChannels(Form form)
    {
        KeyframeChannel paint = this.properties.get("paint");

        if (paint != null)
        {
            paint.removeAll();
        }

        KeyframeChannel legacy = this.properties.get("paint_color");

        if (legacy != null)
        {
            legacy.removeAll();
        }

        this.clearPaintRuntime(form);
    }

    private void clearPaintRuntimeIfChannelsEmpty(Form form)
    {
        KeyframeChannel paint = this.properties.get("paint");
        KeyframeChannel legacy = this.properties.get("paint_color");
        boolean paintEmpty = paint == null || paint.isEmpty();
        boolean legacyEmpty = legacy == null || legacy.isEmpty();

        if (paintEmpty && legacyEmpty)
        {
            this.clearPaintRuntime(form);
        }
    }

    private void clearPaintRuntime(Form form)
    {
        if (form == null)
        {
            return;
        }

        form.paintSettings.setRuntimeValue(null);
        form.paintColor.setRuntimeValue(null);
    }

    private void removeKeyframesAtTicks(String channelId, Collection<Float> ticks)
    {
        KeyframeChannel channel = this.properties.get(channelId);

        if (channel == null || channel.isEmpty())
        {
            return;
        }

        List keyframes = channel.getKeyframes();
        List<Integer> indices = new ArrayList<>();

        for (int i = 0; i < keyframes.size(); i++)
        {
            Keyframe keyframe = (Keyframe) keyframes.get(i);

            for (Float tick : ticks)
            {
                if (tick != null && Math.abs(keyframe.getTick() - tick) < 0.001F)
                {
                    indices.add(i);

                    break;
                }
            }
        }

        indices.sort(Comparator.reverseOrder());

        for (Integer index : indices)
        {
            channel.remove(index);
        }
    }

    private void moveKeyframesBy(String channelId, float diff, Collection<Float> fromTicks)
    {
        KeyframeChannel channel = this.properties.get(channelId);

        if (channel == null || channel.isEmpty())
        {
            return;
        }

        List keyframes = channel.getKeyframes();
        List<Keyframe> moving = new ArrayList<>();

        for (Object kfObj : keyframes)
        {
            Keyframe keyframe = (Keyframe) kfObj;

            for (Float tick : fromTicks)
            {
                if (tick != null && Math.abs(keyframe.getTick() - tick) < 0.001F)
                {
                    moving.add(keyframe);

                    break;
                }
            }
        }

        for (Keyframe keyframe : moving)
        {
            keyframe.setTick(keyframe.getTick() + diff, false);
        }

        channel.sort();
    }

    @Override
    public BaseType toData()
    {
        MapType data = (MapType) super.toData();

        if (BBSSettings.isSaveAsCompatible())
        {
            this.rewriteForLegacyCompat(data);
        }

        return data;
    }

    /**
     * Older FormProperties loaders crash (NPE / ClassCast) on unknown keyframe {@code type}
     * strings and on Color values stored as maps. Dual-write legacy companions for older
     * builds, but always keep modern paint / glow / structure_light channels on disk so
     * this edition can reload them without depending on migration.
     */
    private void rewriteForLegacyCompat(MapType data)
    {
        this.dualWritePaintToLegacy(data);
        this.dualWriteGlowToLegacy(data);
        this.dualWriteStructureLightToLegacy(data);
        this.rewriteLightingSettingsToLegacy(data);
        this.flattenColorKeyframeValuesToInt(data);
        this.stripUnsafeKeyframeTypes(data);
    }

    /**
     * Older builds only know {@code float} lighting (world-influence). Rewrite modern
     * lighting settings so values and type match that format.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void rewriteLightingSettingsToLegacy(MapType data)
    {
        ArrayList<String> keys = new ArrayList<>();

        for (String key : data.keys())
        {
            if (isWorldLightingChannelKey(key))
            {
                keys.add(key);
            }
        }

        for (String key : keys)
        {
            MapType channelData = data.getMap(key);

            if (channelData.isEmpty())
            {
                continue;
            }

            String type = channelData.getString("type");

            if (!"lighting_settings".equals(type) && !"lighting_brightness".equals(type))
            {
                continue;
            }

            KeyframeChannel live = this.properties.get(key);

            if (live == null)
            {
                continue;
            }

            if (live.getFactory() != KeyframeFactories.LIGHTING_SETTINGS
                && live.getFactory() != KeyframeFactories.LIGHTING_BRIGHTNESS)
            {
                continue;
            }

            KeyframeChannel<Float> legacy = new KeyframeChannel<>(key, KeyframeFactories.FLOAT);

            legacy.setModel(true);

            for (Object object : live.getKeyframes())
            {
                Keyframe keyframe = (Keyframe) object;
                Object raw = keyframe.getValue();
                float legacyValue;

                if (raw instanceof LightingSettings settings)
                {
                    legacyValue = FormLighting.settingsToLegacy(settings);
                }
                else if (raw instanceof Number number)
                {
                    legacyValue = FormLighting.brightnessToLegacy(number.floatValue());
                }
                else
                {
                    legacyValue = FormLighting.brightnessToLegacy(0F);
                }

                int index = legacy.insert(keyframe.getTick(), legacyValue);
                Keyframe<Float> out = legacy.get(index);

                if (out != null)
                {
                    out.getInterpolation().copy(keyframe.getInterpolation());
                    out.setDuration(keyframe.getDuration());
                    out.lx = keyframe.lx;
                    out.ly = keyframe.ly;
                    out.rx = keyframe.rx;
                    out.ry = keyframe.ry;
                }
            }

            data.put(key, legacy.toData());
        }
    }

    private void dualWritePaintToLegacy(MapType data)
    {
        KeyframeChannel<?> paintAny = this.properties.get("paint");

        if (paintAny == null || paintAny.isEmpty() || paintAny.getFactory() != KeyframeFactories.PAINT_SETTINGS)
        {
            /* Modern paint cleared — do not leave stale dual-write companions on disk. */
            data.remove("paint_color");

            return;
        }

        @SuppressWarnings("unchecked")
        KeyframeChannel<PaintSettings> paint = (KeyframeChannel<PaintSettings>) paintAny;
        KeyframeChannel<Color> paintColor = new KeyframeChannel<>("paint_color", KeyframeFactories.COLOR);

        paintColor.setModel(true);

        for (Keyframe<PaintSettings> kf : paint.getKeyframes())
        {
            PaintSettings settings = kf.getValue();

            if (settings == null)
            {
                continue;
            }

            Color color = new Color(settings.r, settings.g, settings.b, settings.intensity);

            if (settings.transform != null)
            {
                color.transform = settings.transform.copy();
            }

            int index = paintColor.insert(kf.getTick(), color);
            Keyframe<Color> out = paintColor.get(index);

            if (out != null)
            {
                out.getInterpolation().copy(kf.getInterpolation());
            }
        }

        /* Keep modern "paint" — legacy paint_color is only for older BBS builds. */
        data.put("paint_color", paintColor.toData());
    }

    private void dualWriteGlowToLegacy(MapType data)
    {
        KeyframeChannel<?> glowAny = this.properties.get("glow");

        if (glowAny == null)
        {
            glowAny = this.properties.get("glow_settings");
        }

        if (glowAny == null || glowAny.isEmpty() || glowAny.getFactory() != KeyframeFactories.GLOW_SETTINGS)
        {
            /* Modern glow cleared — drop stale dual-write companions or they resurrect on load. */
            data.remove("glowing_color");
            data.remove("glow_intensity");

            return;
        }

        @SuppressWarnings("unchecked")
        KeyframeChannel<GlowSettings> glow = (KeyframeChannel<GlowSettings>) glowAny;
        KeyframeChannel<Color> glowingColor = new KeyframeChannel<>("glowing_color", KeyframeFactories.COLOR);
        KeyframeChannel<Float> glowIntensity = new KeyframeChannel<>("glow_intensity", KeyframeFactories.FLOAT);

        glowingColor.setModel(true);
        glowIntensity.setModel(true);

        for (Keyframe<GlowSettings> kf : glow.getKeyframes())
        {
            GlowSettings settings = kf.getValue();

            if (settings == null)
            {
                continue;
            }

            Color color = new Color(settings.r, settings.g, settings.b, 1F);

            if (settings.transform != null)
            {
                color.transform = settings.transform.copy();
            }

            int colorIndex = glowingColor.insert(kf.getTick(), color);
            int intensityIndex = glowIntensity.insert(kf.getTick(), settings.intensity);
            Keyframe<Color> colorKf = glowingColor.get(colorIndex);
            Keyframe<Float> intensityKf = glowIntensity.get(intensityIndex);

            if (colorKf != null)
            {
                colorKf.getInterpolation().copy(kf.getInterpolation());
            }

            if (intensityKf != null)
            {
                intensityKf.getInterpolation().copy(kf.getInterpolation());
            }
        }

        /* Keep modern "glow" — legacy channels are only for older BBS builds. */
        data.put("glowing_color", glowingColor.toData());
        data.put("glow_intensity", glowIntensity.toData());
    }

    private void dualWriteStructureLightToLegacy(MapType data)
    {
        KeyframeChannel<?> lightAny = this.properties.get("structure_light");

        if (lightAny == null || lightAny.isEmpty() || lightAny.getFactory() != KeyframeFactories.STRUCTURE_LIGHT_SETTINGS)
        {
            data.remove("emit_light");
            data.remove("light_intensity");

            return;
        }

        @SuppressWarnings("unchecked")
        KeyframeChannel<StructureLightSettings> light = (KeyframeChannel<StructureLightSettings>) lightAny;
        KeyframeChannel<Boolean> emit = new KeyframeChannel<>("emit_light", KeyframeFactories.BOOLEAN);
        KeyframeChannel<Float> intensity = new KeyframeChannel<>("light_intensity", KeyframeFactories.FLOAT);

        emit.setModel(true);
        intensity.setModel(true);

        for (Keyframe<StructureLightSettings> kf : light.getKeyframes())
        {
            StructureLightSettings settings = kf.getValue();

            if (settings == null)
            {
                continue;
            }

            int emitIndex = emit.insert(kf.getTick(), settings.enabled);
            int intensityIndex = intensity.insert(kf.getTick(), (float) settings.intensity);
            Keyframe<Boolean> emitKf = emit.get(emitIndex);
            Keyframe<Float> intensityKf = intensity.get(intensityIndex);

            if (emitKf != null)
            {
                emitKf.getInterpolation().copy(kf.getInterpolation());
            }

            if (intensityKf != null)
            {
                intensityKf.getInterpolation().copy(kf.getInterpolation());
            }
        }

        data.put("emit_light", emit.toData());
        data.put("light_intensity", intensity.toData());
    }

    private void flattenColorKeyframeValuesToInt(MapType data)
    {
        this.flattenPlainColorChannelValues(data.getMap("color"));
        this.flattenPlainColorChannelValues(data.getMap("paint_color"));
        this.flattenPlainColorChannelValues(data.getMap("glowing_color"));
    }

    /**
     * Older loaders only accept Color keyframe values as ints. Keep map values when they
     * carry transform / color-grade data so compatible saves do not wipe those fields.
     */
    private void flattenPlainColorChannelValues(MapType channelData)
    {
        if (channelData.isEmpty() || !channelData.has("keyframes"))
        {
            return;
        }

        ListType keyframes = channelData.getList("keyframes");

        for (int i = 0; i < keyframes.size(); i++)
        {
            BaseType raw = keyframes.get(i);

            if (raw == null || !raw.isMap())
            {
                continue;
            }

            MapType keyframeMap = raw.asMap();
            BaseType value = keyframeMap.get("value");

            if (value == null || !value.isMap())
            {
                continue;
            }

            MapType valueMap = value.asMap();

            if (!valueMap.has("color") || this.colorValueMapHasExtras(valueMap))
            {
                continue;
            }

            keyframeMap.put("value", new IntType(valueMap.getInt("color")));
        }
    }

    private boolean colorValueMapHasExtras(MapType valueMap)
    {
        return valueMap.has("transform")
            || valueMap.has("brightness")
            || valueMap.has("contrast")
            || valueMap.has("hue")
            || valueMap.has("saturation")
            || valueMap.has("brightnessTransform")
            || valueMap.has("contrastTransform")
            || valueMap.has("hueTransform")
            || valueMap.has("saturationTransform");
    }

    private void stripUnsafeKeyframeTypes(MapType data)
    {
        ArrayList<String> remove = new ArrayList<>();

        for (String key : data.keys())
        {
            MapType channel = data.getMap(key);

            if (channel.isEmpty())
            {
                continue;
            }

            String type = channel.getString("type");

            if (!isLegacySafeKeyframeType(type))
            {
                remove.add(key);
            }
        }

        for (String key : remove)
        {
            data.remove(key);
        }
    }

    private static boolean isLegacySafeKeyframeType(String type)
    {
        if (type == null || type.isEmpty())
        {
            return false;
        }

        return switch (type)
        {
            case "color", "transform", "pose", "boolean", "string",
                 "float", "double", "integer", "link", "vector4f",
                 "anchor", "block_state", "item_stack", "actions_config",
                 "shape_keys",
                 /* Modern channels this edition must round-trip even with save_as_compatible */
                 "paint", "paint_settings", "glow", "glow_settings",
                 "structure_light_settings", "structure_light",
                 "shadow", "shadow_settings", "chroma_sky_settings",
                 "particle_settings", "look_at", "inverse_kinematics",
                 "illusion", "mount_link" -> true;
            default -> false;
        };
    }

    private Color sampleColorChannel(KeyframeChannel<Color> colorChannel, float tick)
    {
        if (colorChannel == null || colorChannel.isEmpty())
        {
            return new Color(1F, 1F, 1F, 1F);
        }

        KeyframeSegment segment = colorChannel.find(tick);

        if (segment != null)
        {
            Object interpolated = segment.createInterpolated();

            if (interpolated instanceof Color color)
            {
                return color.copy();
            }
        }

        return new Color(1F, 1F, 1F, 1F);
    }

    /**
     * True when serialized Color keyframes still carry dual-write {@code blend_a}.
     * Those are already migrated by {@link ColorKeyframeFactory}
     * and must not treat {@code color.a} as tint intensity again.
     */
    private static boolean colorChannelDataHasBlendA(MapType channelData)
    {
        if (channelData == null || !channelData.has("keyframes"))
        {
            return false;
        }

        ListType keyframes = channelData.getList("keyframes");

        for (int i = 0; i < keyframes.size(); i++)
        {
            MapType keyframeMap = keyframes.getMap(i);

            if (keyframeMap == null || !keyframeMap.has("value"))
            {
                continue;
            }

            BaseType value = keyframeMap.get("value");

            if (value instanceof MapType valueMap && valueMap.has(ColorKeyframeFactory.BLEND_A))
            {
                return true;
            }
        }

        return false;
    }

    @Override
    public void fromData(BaseType data)
    {
        /* FormProperties stores dynamic channels; rebuild from serialized data to avoid stale channels. */
        this.properties.clear();
        this.removeAll();

        if (!data.isMap())
        {
            return;
        }

        MapType map = data.asMap();
        boolean colorHadBlendA = false;

        for (String key : map.keys())
        {
            MapType mapType = map.getMap(key);

            if (mapType.isEmpty())
            {
                continue;
            }

            try
            {
                if ("color".equals(key))
                {
                    colorHadBlendA = colorChannelDataHasBlendA(mapType);
                }

                String type = mapType.getString("type");

                /* Skip unknown factories early — older builds NPE here; stay resilient too. */
                if (type != null && !type.isEmpty() && !KeyframeFactories.FACTORIES.containsKey(type))
                {
                    continue;
                }

                KeyframeChannel property = new KeyframeChannel(key, null);

                property.setModel(true);
                property.fromData(mapType);

            /* Patch 1.1.1 + brightness + settings: lighting track migrations. */
            if (isWorldLightingChannelKey(key) && property.getFactory() == KeyframeFactories.BOOLEAN)
            {
                KeyframeChannel newProperty = new KeyframeChannel(key, KeyframeFactories.LIGHTING_SETTINGS);

                newProperty.setModel(true);

                for (Object keyframe : property.getKeyframes())
                {
                    Keyframe kf = (Keyframe) keyframe;
                    Boolean v = (Boolean) kf.getValue();

                    /* Legacy boolean true = world lighting → brightness 0; false = full bright → 1. */
                    newProperty.insert(kf.getTick(), LightingSettings.fromBrightness(v ? 0F : 1F));
                }

                property = newProperty;
            }

            if (isWorldLightingChannelKey(key) && property.getFactory() == KeyframeFactories.FLOAT)
            {
                KeyframeChannel newProperty = new KeyframeChannel(key, KeyframeFactories.LIGHTING_SETTINGS);

                newProperty.setModel(true);

                for (Object keyframe : property.getKeyframes())
                {
                    Keyframe kf = (Keyframe) keyframe;
                    Object raw = kf.getValue();
                    float legacy = raw instanceof Number ? ((Number) raw).floatValue() : 1F;

                    newProperty.insert(kf.getTick(), LightingSettings.fromBrightness(FormLighting.legacyToBrightness(legacy)));
                }

                property = newProperty;
            }

            if (isWorldLightingChannelKey(key) && property.getFactory() == KeyframeFactories.LIGHTING_BRIGHTNESS)
            {
                KeyframeChannel newProperty = new KeyframeChannel(key, KeyframeFactories.LIGHTING_SETTINGS);

                newProperty.setModel(true);

                for (Object keyframe : property.getKeyframes())
                {
                    Keyframe kf = (Keyframe) keyframe;
                    Object raw = kf.getValue();
                    float brightness = raw instanceof Number ? ((Number) raw).floatValue() : 0F;

                    newProperty.insert(kf.getTick(), LightingSettings.fromBrightness(brightness));
                }

                property = newProperty;
            }

            /* shaderShadow was briefly a float; migrate float keyframes back to boolean */
            if (key.equals("shaderShadow") && property.getFactory() == KeyframeFactories.FLOAT)
            {
                KeyframeChannel newProperty = new KeyframeChannel(key, KeyframeFactories.BOOLEAN);

                newProperty.setModel(true);

                for (Object keyframe : property.getKeyframes())
                {
                    Keyframe kf = (Keyframe) keyframe;
                    Object raw = kf.getValue();
                    float f = raw instanceof Number ? ((Number) raw).floatValue() : 0F;

                    newProperty.insert(kf.getTick(), f > 0.001F);
                }

                property = newProperty;
            }

            if (property.getFactory() != null)
            {
                this.properties.put(key, property);
                this.add(property);
            }
            }
            catch (Throwable ignored)
            {
                /* Skip corrupt / unknown property channels so the rest of the film still loads. */
            }
        }

        /* Migration: synthesize structure_light from legacy emit_light and light_intensity channels */
        try
        {
            KeyframeChannel<?> emit = this.properties.get("emit_light");
            KeyframeChannel<?> intensity = this.properties.get("light_intensity");
            KeyframeChannel<?> mergedAny = this.properties.get("structure_light");
            boolean modernLight = mergedAny != null
                && !mergedAny.isEmpty()
                && mergedAny.getFactory() == KeyframeFactories.STRUCTURE_LIGHT_SETTINGS;

            if (modernLight)
            {
                if (emit != null)
                {
                    this.properties.remove("emit_light");
                    this.remove(emit);
                }

                if (intensity != null)
                {
                    this.properties.remove("light_intensity");
                    this.remove(intensity);
                }
            }
            else if (emit != null || intensity != null)
            {
                @SuppressWarnings("unchecked")
                KeyframeChannel<StructureLightSettings> merged = mergedAny != null
                    ? (KeyframeChannel<StructureLightSettings>) mergedAny
                    : new KeyframeChannel<>("structure_light", KeyframeFactories.STRUCTURE_LIGHT_SETTINGS);

                if (mergedAny == null)
                {
                    merged.setModel(true);
                    this.properties.put("structure_light", merged);
                    this.add(merged);
                }

                TreeSet<Float> ticks = new TreeSet<>();
                if (emit != null) for (Object kfObj : emit.getKeyframes()) { ticks.add(((Keyframe<?>) kfObj).getTick()); }
                if (intensity != null) for (Object kfObj : intensity.getKeyframes()) { ticks.add(((Keyframe<?>) kfObj).getTick()); }

                for (float t : ticks)
                {
                    boolean enabled = false;
                    int value = 0;

                    if (emit != null)
                    {
                        KeyframeSegment seg = emit.find(t);
                        if (seg != null)
                        {
                            Object v = seg.createInterpolated();
                            if (v instanceof Boolean b) enabled = b;
                            else if (v instanceof Number n) enabled = n.floatValue() >= 0.5F;
                        }
                    }

                    if (intensity != null)
                    {
                        KeyframeSegment seg = intensity.find(t);
                        if (seg != null)
                        {
                            Object v = seg.createInterpolated();
                            if (v instanceof Number n) value = Math.round(n.floatValue());
                        }
                    }

                    StructureLightSettings payload = new StructureLightSettings(
                        enabled,
                        Math.max(0, Math.min(15, value))
                    );

                    merged.insert(t, payload);
                }

                if (emit != null)
                {
                    this.properties.remove("emit_light");
                    this.remove(emit);
                }

                if (intensity != null)
                {
                    this.properties.remove("light_intensity");
                    this.remove(intensity);
                }
            }
        }
        catch (Throwable ignored) {}

        /* Migration: rename glow_settings -> glow, merge glowing_color, synthesize from glow_intensity */
        try
        {
            KeyframeChannel<?> renamed = this.properties.remove("glow_settings");

            if (renamed != null)
            {
                KeyframeChannel<?> mergedAny = this.properties.get("glow");
                @SuppressWarnings("unchecked")
                KeyframeChannel<GlowSettings> merged = mergedAny != null
                    ? (KeyframeChannel<GlowSettings>) mergedAny
                    : new KeyframeChannel<>("glow", KeyframeFactories.GLOW_SETTINGS);

                if (mergedAny == null)
                {
                    merged.setModel(true);
                    this.properties.put("glow", merged);
                    this.add(merged);
                }

                for (Object kfObj : renamed.getKeyframes())
                {
                    Keyframe<?> kf = (Keyframe<?>) kfObj;
                    Object v = kf.getValue();

                    if (v instanceof GlowSettings settings)
                    {
                        merged.insert(kf.getTick(), settings.copy());
                    }
                }

                this.remove(renamed);
            }

            KeyframeChannel<?> glowingColorChannel = this.properties.remove("glowing_color");

            if (glowingColorChannel != null)
            {
                KeyframeChannel<?> mergedAny = this.properties.get("glow");
                /* Present modern glow channel wins even when empty (user cleared keyframes). */
                boolean preferModernGlow = mergedAny != null
                    && mergedAny.getFactory() == KeyframeFactories.GLOW_SETTINGS;

                if (preferModernGlow)
                {
                    this.remove(glowingColorChannel);
                }
                else
                {
                    @SuppressWarnings("unchecked")
                    KeyframeChannel<GlowSettings> merged = mergedAny != null
                        ? (KeyframeChannel<GlowSettings>) mergedAny
                        : new KeyframeChannel<>("glow", KeyframeFactories.GLOW_SETTINGS);

                    if (mergedAny == null)
                    {
                        merged.setModel(true);
                        this.properties.put("glow", merged);
                        this.add(merged);
                    }

                    for (Object kfObj : glowingColorChannel.getKeyframes())
                    {
                        Keyframe<?> kf = (Keyframe<?>) kfObj;
                        float t = kf.getTick();
                        GlowSettings settings = this.getGlowSettingsAt(merged, t);
                        Object v = kf.getValue();

                        if (v instanceof Color color)
                        {
                            settings.r = color.r;
                            settings.g = color.g;
                            settings.b = color.b;

                            if (color.transform != null)
                            {
                                settings.transform = color.transform.copy();
                            }
                        }

                        merged.insert(t, settings);
                    }

                    this.remove(glowingColorChannel);
                }
            }

            KeyframeChannel<?> legacyGlow = this.properties.remove("glow_intensity");

            if (legacyGlow != null)
            {
                KeyframeChannel<?> mergedAny = this.properties.get("glow");
                boolean preferModernGlow = mergedAny != null
                    && mergedAny.getFactory() == KeyframeFactories.GLOW_SETTINGS;

                if (!preferModernGlow)
                {
                    @SuppressWarnings("unchecked")
                    KeyframeChannel<GlowSettings> merged = mergedAny != null
                        ? (KeyframeChannel<GlowSettings>) mergedAny
                        : new KeyframeChannel<>("glow", KeyframeFactories.GLOW_SETTINGS);

                    if (mergedAny == null)
                    {
                        merged.setModel(true);
                        this.properties.put("glow", merged);
                        this.add(merged);
                    }

                    for (Object kfObj : legacyGlow.getKeyframes())
                    {
                        Keyframe<?> kf = (Keyframe<?>) kfObj;
                        float t = kf.getTick();
                        float intensity = 0F;
                        Object v = kf.getValue();

                        if (v instanceof Number n)
                        {
                            intensity = n.floatValue();
                        }

                        GlowSettings settings = this.getGlowSettingsAt(merged, t);

                        settings.intensity = intensity;
                        merged.insert(t, settings);
                    }
                }

                this.remove(legacyGlow);
            }
        }
        catch (Throwable ignored) {}

        /* Migration: paint_color -> paint (PaintSettings). Prefer modern paint when present. */
        try
        {
            KeyframeChannel<?> paintColorChannel = this.properties.remove("paint_color");

            if (paintColorChannel != null)
            {
                KeyframeChannel<?> mergedAny = this.properties.get("paint");
                boolean preferModernPaint = mergedAny != null
                    && mergedAny.getFactory() == KeyframeFactories.PAINT_SETTINGS;

                if (preferModernPaint)
                {
                    this.remove(paintColorChannel);
                }
                else
                {
                    @SuppressWarnings("unchecked")
                    KeyframeChannel<PaintSettings> merged = mergedAny != null
                        ? (KeyframeChannel<PaintSettings>) mergedAny
                        : new KeyframeChannel<>("paint", KeyframeFactories.PAINT_SETTINGS);

                    if (mergedAny == null)
                    {
                        merged.setModel(true);
                        this.properties.put("paint", merged);
                        this.add(merged);
                    }

                    for (Object kfObj : paintColorChannel.getKeyframes())
                    {
                        Keyframe<?> kf = (Keyframe<?>) kfObj;
                        float t = kf.getTick();
                        PaintSettings settings = this.getPaintSettingsAt(merged, t);
                        Object v = kf.getValue();

                        if (v instanceof Color color)
                        {
                            settings.r = color.r;
                            settings.g = color.g;
                            settings.b = color.b;
                            settings.intensity = color.a;

                            if (color.transform != null)
                            {
                                settings.transform = color.transform.copy();
                            }
                        }

                        merged.insert(t, settings);
                    }

                    this.remove(paintColorChannel);
                }
            }
        }
        catch (Throwable ignored) {}

        /* Migration: legacy Opacity track + color.a tint strength → traditional color.a opacity */
        try
        {
            KeyframeChannel<?> opacityAny = this.properties.get("opacity");
            KeyframeChannel<?> colorAny = this.properties.get("color");

            if (opacityAny != null && opacityAny.getFactory() == KeyframeFactories.FLOAT)
            {
                @SuppressWarnings("unchecked")
                KeyframeChannel<Float> opacityChannel = (KeyframeChannel<Float>) opacityAny;
                KeyframeChannel<Color> colorChannel;

                if (colorAny != null && colorAny.getFactory() == KeyframeFactories.COLOR)
                {
                    @SuppressWarnings("unchecked")
                    KeyframeChannel<Color> typed = (KeyframeChannel<Color>) colorAny;

                    colorChannel = typed;
                }
                else
                {
                    colorChannel = new KeyframeChannel<>("color", KeyframeFactories.COLOR);
                    colorChannel.setModel(true);
                    this.properties.put("color", colorChannel);
                    this.add(colorChannel);
                }

                for (Object kfObj : opacityChannel.getKeyframes())
                {
                    Keyframe<?> opacityKf = (Keyframe<?>) kfObj;
                    Object opacityValue = opacityKf.getValue();

                    if (!(opacityValue instanceof Float))
                    {
                        continue;
                    }

                    float tick = opacityKf.getTick();
                    float opacityA = MathUtils.clamp((Float) opacityValue, 0F, 1F);
                    Color color = this.sampleColorChannel(colorChannel, tick);

                    /* Early Blend Color: color.a was tint intensity. Dual-write era already
                     * baked intensity via blend_a in ColorKeyframeFactory — only assign opacity. */
                    if (!colorHadBlendA)
                    {
                        float intensity = MathUtils.clamp(color.a, 0F, 1F);

                        color.r = Lerps.lerp(1F, color.r, intensity);
                        color.g = Lerps.lerp(1F, color.g, intensity);
                        color.b = Lerps.lerp(1F, color.b, intensity);
                    }

                    color.a = opacityA;

                    int index = colorChannel.insert(tick, color);
                    Keyframe<Color> colorKf = colorChannel.get(index);

                    if (colorKf != null)
                    {
                        colorKf.getInterpolation().copy(opacityKf.getInterpolation());
                        colorKf.setNoshadingOpacity(opacityKf.isNoshadingOpacity());
                    }
                }

                this.properties.remove("opacity");
                this.remove(opacityChannel);
            }
            else if (colorAny != null && colorAny.getFactory() == KeyframeFactories.COLOR)
            {
                @SuppressWarnings("unchecked")
                KeyframeChannel<Color> colorChannel = (KeyframeChannel<Color>) colorAny;

                for (Object kfObj : colorChannel.getKeyframes())
                {
                    Keyframe<?> kf = (Keyframe<?>) kfObj;
                    Object v = kf.getValue();

                    if (v instanceof Color color && color.a <= 0.001F)
                    {
                        /* Legacy tint-off (a≈0) → fully opaque white under traditional alpha. */
                        color.r = 1F;
                        color.g = 1F;
                        color.b = 1F;
                        color.a = 1F;
                    }
                }
            }
        }
        catch (Throwable ignored) {}

        /* Drop orphan render_depth tracks (feature removed; ignore legacy keyframes). */
        try
        {
            KeyframeChannel<?> renderDepth = this.properties.get("render_depth");

            if (renderDepth != null)
            {
                this.properties.remove("render_depth");
                this.remove(renderDepth);
            }

            KeyframeChannel<?> renderDepthEnabled = this.properties.get("render_depth_enabled");

            if (renderDepthEnabled != null)
            {
                this.properties.remove("render_depth_enabled");
                this.remove(renderDepthEnabled);
            }
        }
        catch (Throwable ignored) {}

        /* Migration: copy legacy visible render-gating keyframes into render while keeping visible */
        try
        {
            KeyframeChannel<?> legacyVisible = this.properties.get("visible");
            KeyframeChannel<?> renderChannel = this.properties.get("render");

            if (renderChannel == null && legacyVisible != null && legacyVisible.getFactory() == KeyframeFactories.BOOLEAN && !legacyVisible.isEmpty())
            {
                KeyframeChannel<Boolean> render = new KeyframeChannel<>("render", KeyframeFactories.BOOLEAN);

                render.setModel(true);

                for (Object kfObj : legacyVisible.getKeyframes())
                {
                    Keyframe<?> kf = (Keyframe<?>) kfObj;

                    render.insert(kf.getTick(), (Boolean) kf.getValue());
                }

                this.properties.put("render", render);
                this.add(render);
            }
        }
        catch (Throwable ignored) {}

        /* Migration: orphan render keys get visible twins so Enabled is editable on Visible. */
        try
        {
            this.migrateOrphanRenderToVisiblePairs();
        }
        catch (Throwable ignored) {}

        /* Migration: move Color Grade fields off the shared Color channel into color_grade. */
        try
        {
            this.migrateSharedColorGradeToChannel("color", "color_grade");

            ArrayList<String> colorKeys = new ArrayList<>();

            for (String key : this.properties.keySet())
            {
                if (key.endsWith("/color") && !key.endsWith("/color_grade"))
                {
                    colorKeys.add(key);
                }
            }

            for (String colorKey : colorKeys)
            {
                String gradeKey = colorKey.substring(0, colorKey.length() - "color".length()) + "color_grade";

                this.migrateSharedColorGradeToChannel(colorKey, gradeKey);
            }
        }
        catch (Throwable ignored) {}
    }

    /**
     * Insert a Visible keyframe (always {@code true}) paired with a Render/Enabled value
     * at the same tick — same shape the Visible track UI edits via its Render toggle.
     */
    @SuppressWarnings("unchecked")
    public void insertVisibleRenderEnabled(Form form, float tick, boolean renderEnabled)
    {
        if (form == null || tick < 0F)
        {
            return;
        }

        KeyframeChannel visibleAny = this.getOrCreate(form, "visible");
        KeyframeChannel renderAny = this.getOrCreate(form, "render");

        if (visibleAny != null && visibleAny.getFactory() == KeyframeFactories.BOOLEAN)
        {
            ((KeyframeChannel<Boolean>) visibleAny).insert(tick, Boolean.TRUE);
        }

        if (renderAny != null && renderAny.getFactory() == KeyframeFactories.BOOLEAN)
        {
            ((KeyframeChannel<Boolean>) renderAny).insert(tick, renderEnabled);
        }
    }

    /**
     * Autocapture / older films wrote only {@code render}. Pair each render key with a
     * Visible keyframe so Enabled can be edited from the Visible track.
     */
    @SuppressWarnings("unchecked")
    private void migrateOrphanRenderToVisiblePairs()
    {
        ArrayList<String> renderKeys = new ArrayList<>();

        for (String key : this.properties.keySet())
        {
            if (FormUtils.isRenderPropertyPath(key))
            {
                renderKeys.add(key);
            }
        }

        for (String renderKey : renderKeys)
        {
            KeyframeChannel<?> renderAny = this.properties.get(renderKey);

            if (renderAny == null || renderAny.getFactory() != KeyframeFactories.BOOLEAN || renderAny.isEmpty())
            {
                continue;
            }

            String visibleKey = FormUtils.getVisiblePropertyPath(renderKey);
            KeyframeChannel<?> visibleAny = this.properties.get(visibleKey);

            if (visibleAny == null)
            {
                KeyframeChannel<Boolean> created = new KeyframeChannel<>(visibleKey, KeyframeFactories.BOOLEAN);

                created.setModel(true);
                this.properties.put(visibleKey, created);
                this.add(created);
                visibleAny = created;
            }

            if (visibleAny.getFactory() != KeyframeFactories.BOOLEAN)
            {
                continue;
            }

            KeyframeChannel<Boolean> visible = (KeyframeChannel<Boolean>) visibleAny;
            KeyframeChannel<Boolean> render = (KeyframeChannel<Boolean>) renderAny;

            for (Keyframe<Boolean> renderKeyframe : render.getKeyframes())
            {
                if (renderKeyframe == null)
                {
                    continue;
                }

                float tick = renderKeyframe.getTick();

                if (this.findBooleanKeyframeAtTick(visible, tick) == null)
                {
                    visible.insert(tick, Boolean.TRUE);
                }
            }
        }
    }

    private Keyframe<Boolean> findBooleanKeyframeAtTick(KeyframeChannel<Boolean> channel, float tick)
    {
        if (channel == null)
        {
            return null;
        }

        for (Keyframe<Boolean> keyframe : channel.getKeyframes())
        {
            if (keyframe != null && Math.abs(keyframe.getTick() - tick) < 0.0001F)
            {
                return keyframe;
            }
        }

        return null;
    }

    /**
     * Older builds edited Color Grade on the same channel as Color. Split grade into
     * {@code gradeKey} so nested tracks keep independent keyframes. Plain white Color
     * keyframes that only stored grade are removed from the Color channel.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void migrateSharedColorGradeToChannel(String colorKey, String gradeKey)
    {
        KeyframeChannel<?> colorAny = this.properties.get(colorKey);

        if (colorAny == null || colorAny.getFactory() != KeyframeFactories.COLOR || colorAny.isEmpty())
        {
            return;
        }

        KeyframeChannel<?> existingGrade = this.properties.get(gradeKey);

        if (existingGrade != null && !existingGrade.isEmpty())
        {
            return;
        }

        @SuppressWarnings("unchecked")
        KeyframeChannel<Color> color = (KeyframeChannel<Color>) colorAny;
        KeyframeChannel<Color> grade = existingGrade != null
            ? (KeyframeChannel<Color>) existingGrade
            : new KeyframeChannel<>(gradeKey, KeyframeFactories.COLOR);

        if (existingGrade == null)
        {
            grade.setModel(true);
        }

        boolean migrated = false;
        ArrayList<Integer> removeFromColor = new ArrayList<>();
        List<Keyframe<Color>> colorKeyframes = color.getKeyframes();

        for (int i = 0; i < colorKeyframes.size(); i++)
        {
            Keyframe<Color> kf = colorKeyframes.get(i);
            Color value = kf.getValue();

            if (value == null || (!value.hasColorAdjustments() && !value.hasActiveGradeTransform()))
            {
                continue;
            }

            Color gradeValue = new Color(1F, 1F, 1F, 1F);

            gradeValue.brightness = value.brightness;
            gradeValue.contrast = value.contrast;
            gradeValue.hue = value.hue;
            gradeValue.saturation = value.saturation;
            gradeValue.brightnessTransform = value.brightnessTransform == null ? new EffectTransform() : value.brightnessTransform.copy();
            gradeValue.contrastTransform = value.contrastTransform == null ? new EffectTransform() : value.contrastTransform.copy();
            gradeValue.hueTransform = value.hueTransform == null ? new EffectTransform() : value.hueTransform.copy();
            gradeValue.saturationTransform = value.saturationTransform == null ? new EffectTransform() : value.saturationTransform.copy();

            int index = grade.insert(kf.getTick(), gradeValue);
            Keyframe<Color> out = grade.get(index);

            if (out != null)
            {
                out.getInterpolation().copy(kf.getInterpolation());
            }

            value.brightness = 0F;
            value.contrast = 0F;
            value.hue = 0F;
            value.saturation = 0F;
            value.brightnessTransform = new EffectTransform();
            value.contrastTransform = new EffectTransform();
            value.hueTransform = new EffectTransform();
            value.saturationTransform = new EffectTransform();

            boolean plainWhite = Float.compare(value.r, 1F) == 0
                && Float.compare(value.g, 1F) == 0
                && Float.compare(value.b, 1F) == 0
                && Float.compare(value.a, 1F) == 0
                && !value.hasActiveTransform();

            if (plainWhite)
            {
                removeFromColor.add(i);
            }

            migrated = true;
        }

        for (int i = removeFromColor.size() - 1; i >= 0; i--)
        {
            color.remove(removeFromColor.get(i));
        }

        if (migrated && existingGrade == null)
        {
            this.properties.put(gradeKey, grade);
            this.add(grade);
        }
    }

    private PaintSettings getPaintSettingsAt(KeyframeChannel<PaintSettings> channel, float tick)
    {
        KeyframeSegment segment = channel.find(tick);
        PaintSettings settings;

        if (segment != null)
        {
            Object interpolated = segment.createInterpolated();

            settings = interpolated instanceof PaintSettings paint ? paint.copy() : new PaintSettings();
        }
        else
        {
            settings = new PaintSettings();
        }

        return settings;
    }

    private GlowSettings getGlowSettingsAt(KeyframeChannel<GlowSettings> channel, float tick)
    {
        KeyframeSegment segment = channel.find(tick);
        GlowSettings settings;

        if (segment != null)
        {
            Object interpolated = segment.createInterpolated();

            settings = interpolated instanceof GlowSettings glow ? glow.copy() : new GlowSettings();
        }
        else
        {
            settings = new GlowSettings();
        }

        return settings;
    }

    @Override
    protected boolean canPersist(BaseValue value)
    {
        if (value instanceof KeyframeChannel<?> channel)
        {
            return !channel.isEmpty();
        }

        return super.canPersist(value);
    }
}