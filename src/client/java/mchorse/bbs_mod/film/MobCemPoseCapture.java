package mchorse.bbs_mod.film;

import mchorse.bbs_mod.client.ItemUseRenderState;
import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.renderers.MobFormRenderer;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValuePose;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;

import net.minecraft.client.model.ModelPart;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

import java.util.Iterator;
import java.util.Map;

public class MobCemPoseCapture
{
    private static final float FIX_ROT_THRESHOLD = 0.05F;
    private static final float FIX_PIVOT_THRESHOLD = 0.05F;
    private static final float FIX_SCALE_THRESHOLD = 0.01F;
    private static final float POSE_EQUAL_EPSILON = 0.001F;

    private MobCemPoseCapture()
    {}

    public static void syncReplay(Replay replay)
    {
    }

    public static boolean isMobPlaybackActive(Replay replay)
    {
        return replay != null && replay.vanillaMobPlayback.get() && replay.form.get() instanceof MobForm;
    }

    public static boolean isActive(Replay replay)
    {
        return MobCemPoseCapture.isMobPlaybackActive(replay);
    }

    public static void applyPlaybackPose(Replay replay, Form form, IEntity entity, float tick)
    {
        if (!MobCemPoseCapture.isPlaybackActive(replay, form))
        {
            return;
        }

        Form resolved = MobCemPoseCapture.resolveForm(form, entity);
        float transition = tick - (float) Math.floor(tick);
        Pose sampled = MobCemPoseCapture.samplePose(resolved, entity, transition);

        if (sampled.isEmpty())
        {
            return;
        }

        MobCemPoseCapture.applySampledPose(resolved, sampled);
    }

    public static void recordPoseKeyframe(Replay replay, Form form, IEntity entity, int tick, float transition)
    {
        MobCemPoseCapture.recordPoseKeyframe(replay, form, entity, tick, transition, null);
    }

    public static void recordPoseKeyframe(Replay replay, Form form, IEntity entity, int tick, float transition, FormProperties propertiesTarget)
    {
        Form resolved = MobCemPoseCapture.resolveForm(form, entity);

        if (!MobCemPoseCapture.isPlaybackActive(replay, resolved))
        {
            return;
        }

        if (entity != null)
        {
            resolved.update(entity);
        }

        Pose sampled = MobCemPoseCapture.samplePose(resolved, entity, transition);

        if (sampled.isEmpty())
        {
            return;
        }

        FormProperties target = propertiesTarget != null ? propertiesTarget : replay.properties;

        MobCemPoseCapture.insertPoseKeyframe(target, resolved, (float) tick, sampled);
    }

    private static boolean isPlaybackActive(Replay replay, Form form)
    {
        if (form instanceof MobForm)
        {
            return MobCemPoseCapture.isMobPlaybackActive(replay);
        }

        return false;
    }

    public static Pose samplePose(Form form, IEntity entity, float transition)
    {
        if (form instanceof MobForm mobForm)
        {
            return MobCemPoseCapture.sampleMobPose(mobForm, entity, transition);
        }

        return new Pose();
    }

    public static Pose sampleMobPose(MobForm mobForm, IEntity entity, float transition)
    {
        MobFormRenderer renderer = (MobFormRenderer) FormUtilsClient.getRenderer(mobForm);

        renderer.ensureRenderEntity();

        Map<String, ModelPart> parts = renderer.sampleVanillaParts(entity, transition);
        Pose pose = MobCemPoseCapture.partsToPose(parts);

        MobCemPoseCapture.applyFixWeights(pose, entity);

        return pose;
    }

    private static Form resolveForm(Form form, IEntity entity)
    {
        if (entity != null && entity.getForm() != null)
        {
            Form entityForm = entity.getForm();

            if (entityForm instanceof MobForm)
            {
                return entityForm;
            }
        }

        return form;
    }

    private static void applySampledPose(Form form, Pose sampled)
    {
        ValuePose valuePose = MobCemPoseCapture.getPoseValue(form);

        if (valuePose == null)
        {
            return;
        }

        Pose merged = valuePose.get().copy();

        MobCemPoseCapture.mergeSampledPose(merged, sampled);
        /* Runtime only — keyframes are written by recordPoseKeyframe() while recording. */
        valuePose.setRuntimeValue(merged);
    }

    private static ValuePose getPoseValue(Form form)
    {
        if (form instanceof MobForm mobForm)
        {
            return mobForm.pose;
        }

        return null;
    }

    private static void insertPoseKeyframe(FormProperties properties, Form form, float tick, Pose pose)
    {
        BaseValue.edit(properties, (edited) ->
        {
            KeyframeChannel<Pose> channel = edited.getOrCreate(form, "pose");

            if (channel == null)
            {
                return;
            }

            /* Skip duplicate consecutive poses to limit film bloat / save hangs. */
            if (!channel.isEmpty())
            {
                Pose last = channel.get(channel.getKeyframes().size() - 1).getValue();

                if (MobCemPoseCapture.posesApproximatelyEqual(last, pose))
                {
                    return;
                }
            }

            channel.insert(tick, pose.copy());
        });
    }

    private static boolean posesApproximatelyEqual(Pose a, Pose b)
    {
        if (a == b)
        {
            return true;
        }

        if (a == null || b == null || a.transforms.size() != b.transforms.size())
        {
            return false;
        }

        for (Map.Entry<String, PoseTransform> entry : a.transforms.entrySet())
        {
            PoseTransform other = b.transforms.get(entry.getKey());

            if (other == null || !MobCemPoseCapture.transformsApproximatelyEqual(entry.getValue(), other))
            {
                return false;
            }
        }

        return true;
    }

    private static boolean transformsApproximatelyEqual(PoseTransform a, PoseTransform b)
    {
        return Math.abs(a.fix - b.fix) <= POSE_EQUAL_EPSILON
            && Math.abs(a.rotate.x - b.rotate.x) <= POSE_EQUAL_EPSILON
            && Math.abs(a.rotate.y - b.rotate.y) <= POSE_EQUAL_EPSILON
            && Math.abs(a.rotate.z - b.rotate.z) <= POSE_EQUAL_EPSILON
            && Math.abs(a.pivot.x - b.pivot.x) <= POSE_EQUAL_EPSILON
            && Math.abs(a.pivot.y - b.pivot.y) <= POSE_EQUAL_EPSILON
            && Math.abs(a.pivot.z - b.pivot.z) <= POSE_EQUAL_EPSILON
            && Math.abs(a.scale.x - b.scale.x) <= POSE_EQUAL_EPSILON
            && Math.abs(a.scale.y - b.scale.y) <= POSE_EQUAL_EPSILON
            && Math.abs(a.scale.z - b.scale.z) <= POSE_EQUAL_EPSILON;
    }

    public static Pose partsToPose(Map<String, ModelPart> parts)
    {
        Pose pose = new Pose();

        if (parts == null || parts.isEmpty())
        {
            return pose;
        }

        for (Map.Entry<String, ModelPart> entry : parts.entrySet())
        {
            ModelPart part = entry.getValue();

            if (part == null)
            {
                continue;
            }

            PoseTransform transform = pose.get(entry.getKey());

            transform.rotate.set(part.pitch, part.yaw, part.roll);
            transform.pivot.set(part.pivotX, part.pivotY, part.pivotZ);
            transform.scale.set(part.xScale, part.yScale, part.zScale);
        }

        return pose;
    }

    /**
     * fix 1 locks a bone to the sampled CEM angle (attacks, damage, bow draw, etc.).
     * Bones below the threshold are omitted so vanilla locomotion is not doubled.
     */
    public static void applyFixWeights(Pose pose, IEntity entity)
    {
        boolean usingItem = MobCemPoseCapture.isEntityUsingItem(entity);
        int hurtTimer = entity != null ? entity.getHurtTimer() : 0;
        int deathTime = entity != null ? entity.getDeathTime() : 0;
        boolean forced = usingItem || hurtTimer > 0 || deathTime > 0;
        Iterator<Map.Entry<String, PoseTransform>> iterator = pose.transforms.entrySet().iterator();

        while (iterator.hasNext())
        {
            Map.Entry<String, PoseTransform> entry = iterator.next();
            PoseTransform transform = entry.getValue();

            if (!MobCemPoseCapture.hasSampledTransform(transform))
            {
                iterator.remove();
                continue;
            }

            float maxRot = Math.max(Math.abs(transform.rotate.x), Math.max(Math.abs(transform.rotate.y), Math.abs(transform.rotate.z)));
            float maxPivot = Math.max(Math.abs(transform.pivot.x), Math.max(Math.abs(transform.pivot.y), Math.abs(transform.pivot.z)));
            float maxScale = Math.max(Math.abs(transform.scale.x - 1F), Math.max(Math.abs(transform.scale.y - 1F), Math.abs(transform.scale.z - 1F)));

            if (forced || maxRot >= FIX_ROT_THRESHOLD || maxPivot >= FIX_PIVOT_THRESHOLD || maxScale >= FIX_SCALE_THRESHOLD)
            {
                transform.fix = 1F;
            }
            else
            {
                iterator.remove();
            }
        }
    }

    public static boolean hasSampledTransform(PoseTransform transform)
    {
        if (transform == null)
        {
            return false;
        }

        return Math.abs(transform.rotate.x) > 0.001F
            || Math.abs(transform.rotate.y) > 0.001F
            || Math.abs(transform.rotate.z) > 0.001F
            || Math.abs(transform.pivot.x) > 0.001F
            || Math.abs(transform.pivot.y) > 0.001F
            || Math.abs(transform.pivot.z) > 0.001F
            || Math.abs(transform.scale.x - 1F) > 0.001F
            || Math.abs(transform.scale.y - 1F) > 0.001F
            || Math.abs(transform.scale.z - 1F) > 0.001F;
    }

    private static boolean isEntityUsingItem(IEntity entity)
    {
        if (entity == null)
        {
            return false;
        }

        if (entity.isUsingItem())
        {
            return true;
        }

        Hand hand = entity.getActiveHand();
        EquipmentSlot slot = hand == Hand.OFF_HAND ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
        ItemStack stack = entity.getEquipmentStack(slot);

        return ItemUseRenderState.getItemUseElapsed(entity, null, stack) > 0;
    }

    private static void mergeSampledPose(Pose target, Pose sampled)
    {
        for (Map.Entry<String, PoseTransform> entry : sampled.transforms.entrySet())
        {
            PoseTransform value = entry.getValue();

            if (value == null)
            {
                continue;
            }

            PoseTransform transform = target.get(entry.getKey());

            transform.rotate.set(value.rotate);
            transform.pivot.set(value.pivot);
            transform.scale.set(value.scale);
            transform.fix = value.fix;
        }
    }
}
