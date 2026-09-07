package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import mchorse.bbs_mod.cubic.ik.LimbDynamicParams;
import mchorse.bbs_mod.cubic.physics.SpringDynamicParams;
import mchorse.bbs_mod.cubic.physics.WindDynamicParams;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.values.ValueActionsConfig;
import mchorse.bbs_mod.forms.values.ValueShapeKeys;
import mchorse.bbs_mod.obj.shapes.ShapeKeys;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.core.ValueData;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.core.ValueLinks;
import mchorse.bbs_mod.settings.values.core.ValuePose;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.pose.Pose;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModelForm extends Form
{
    public final ValueLink texture = new ValueLink("texture", null);
    public final ValueLinks materialTextures = new ValueLinks("material_textures");
    public final ValueString model = new ValueString("model", "");
    public final ValueFloat pbrNormalIntensity = new ValueFloat("pbr_normal_intensity", 1F, 0F, 4F);
    public final ValueFloat pbrSpecularIntensity = new ValueFloat("pbr_specular_intensity", 1F, 0F, 4F);
    public final ValuePose pose = new ValuePose("pose", new Pose());
    public final ValuePose poseOverlay = new ValuePose("pose_overlay", new Pose());
    public final ValueActionsConfig actions = new ValueActionsConfig("actions", new ActionsConfig());
    public final ValueColor color = new ValueColor("color", new Color(1F, 1F, 1F, 1F));
    public final ValueBoolean solidHitbox = new ValueBoolean("solid_hitbox", false);
    public final ValueShapeKeys shapeKeys = new ValueShapeKeys("shape_keys", new ShapeKeys());
    public final ValueData ik = new ValueData("ik");
    public final ValueData springs = new ValueData("springs");
    public final ValueData constraints = new ValueData("constraints");

    public final List<ValuePose> additionalOverlays = new ArrayList<>();

    /**
     * Runtime per-material texture overrides driven by the per-material animation tracks
     * (keyed by material name). Set each frame by {@code FormProperties} during playback and
     * read first by the renderer's texture resolver; empty means "no track override, use the
     * material's default / the form's default texture".
     */
    public final transient Map<String, Link> materialTextureOverrides = new HashMap<>();

    public final transient Map<String, Vector3f> ikTargetOverrides = new HashMap<>();
    public final transient Map<String, Vector3f> poleTargetOverrides = new HashMap<>();
    public final transient Map<String, Float> ikTargetWeights = new HashMap<>();
    public final transient Map<String, Float> poleTargetWeights = new HashMap<>();
    public final transient Map<String, LimbDynamicParams> limbParamOverrides = new HashMap<>();
    public final transient Map<String, Quaternionf> ikTipRotationOverrides = new HashMap<>();
    public final transient Map<String, Float> ikTipRotationWeights = new HashMap<>();
    /** Per-frame limb entries generated from the inverse kinematics track for bones without model IK config. */
    public transient MapType inverseKinematicsLimbs;
    public final transient Map<String, Vector3f> springTargetOverrides = new HashMap<>();
    public final transient Map<String, Float> springTargetWeights = new HashMap<>();
    public final transient Map<String, SpringDynamicParams> springParamsOverrides = new HashMap<>();
    /* Global wind override from the wind track at playback; null when no keyframe. */
    public transient WindDynamicParams windDynamicOverride;

    public ModelForm()
    {
        super();

        this.add(this.texture);
        this.materialTextures.invisible();
        this.add(this.materialTextures);
        this.add(this.model);
        this.add(this.pbrNormalIntensity);
        this.add(this.pbrSpecularIntensity);
        this.add(this.pose);
        this.add(this.poseOverlay);

        for (int i = 0; i < BBSSettings.recordingPoseTransformOverlays.get(); i++)
        {
            ValuePose valuePose = new ValuePose("pose_overlay" + i, new Pose());

            this.additionalOverlays.add(valuePose);
            this.add(valuePose);
        }

        this.add(this.actions);
        this.add(this.color);
        this.solidHitbox.invisible();
        this.add(this.solidHitbox);
        this.add(this.shapeKeys);

        this.ik.invisible();
        this.springs.invisible();
        this.constraints.invisible();
        this.add(this.ik);
        this.add(this.springs);
        this.add(this.constraints);
    }

    @Override
    public String getDefaultDisplayName()
    {
        return this.model.get();
    }
}
