package mchorse.bbs_mod.cubic.animation;

import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.IModelInstance;
import mchorse.bbs_mod.cubic.animation.gecko.config.GeckoAnimationsConfig;
import mchorse.bbs_mod.cubic.animation.gecko.controllers.GeckoAnimationController;
import mchorse.bbs_mod.cubic.animation.gecko.model.GeckoAnimationContext;
import mchorse.bbs_mod.cubic.animation.gecko.routes.GeckoAnimationRouteRegistry;
import mchorse.bbs_mod.cubic.animation.gecko.routes.GeckoLimbRole;
import mchorse.bbs_mod.cubic.animation.gecko.services.GeckoAnimationBlendService;
import mchorse.bbs_mod.cubic.animation.gecko.services.GeckoAnimationEventBus;
import mchorse.bbs_mod.cubic.animation.gecko.services.GeckoAnimationService;
import mchorse.bbs_mod.cubic.animation.gecko.services.GeckoBOBJLimbService;
import mchorse.bbs_mod.cubic.animation.gecko.services.GeckoModelLimbService;
import mchorse.bbs_mod.cubic.data.animation.Animation;
import mchorse.bbs_mod.cubic.data.animation.Animations;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.entity.ActorEntity;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.pose.Pose;

import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ProceduralAnimator implements IAnimator
{
    public ActionPlayback basePre;
    public ActionPlayback basePost;
    public ActionPlayback riding;
    public ActionPlayback ridingIdle;

    private IModelInstance model;
    private GeckoAnimationsConfig geckoAnimations = new GeckoAnimationsConfig();
    private final GeckoAnimationController geckoController = new GeckoAnimationController(
        new GeckoAnimationService(
            new GeckoAnimationRouteRegistry(),
            new GeckoModelLimbService(),
            new GeckoBOBJLimbService(),
            new GeckoAnimationBlendService(),
            new GeckoAnimationEventBus()
        )
    );

    @Override
    public List<String> getActions()
    {
        return Arrays.asList("base_pre", "base_post");
    }

    @Override
    public void setup(IModelInstance model, ActionsConfig actions, boolean fade)
    {
        this.model = model;
        ActionsConfig safeActions = actions == null ? new ActionsConfig() : actions;
        this.geckoAnimations = safeActions.geckoAnimations;

        this.basePre = this.createAction(this.basePre, safeActions.getConfig("base_pre"), true);
        this.basePost = this.createAction(this.basePost, safeActions.getConfig("base_post"), true);
        this.riding = this.createAction(this.riding, safeActions.getConfig("riding"), true);
        this.ridingIdle = this.createAction(this.ridingIdle, safeActions.getConfig("riding_idle"), true);
    }

    /**
     * Create an action with default priority
     */
    public ActionPlayback createAction(ActionPlayback old, ActionConfig config, boolean looping)
    {
        return this.createAction(old, config, looping, 1);
    }

    /**
     * Create an action playback based on given arguments. This method
     * is used for creating actions so it was easier to tell which
     * actions are missing. Besides that, you can pass an old action so
     * in form merging situation it wouldn't interrupt animation.
     */
    public ActionPlayback createAction(ActionPlayback old, ActionConfig config, boolean looping, int priority)
    {
        Animations animations = this.model == null ? null : this.model.getAnimations();

        if (animations == null)
        {
            return null;
        }

        Animation action = animations.get(config.name);

        /* If given action is missing, then omit creation of ActionPlayback */
        if (action == null)
        {
            return null;
        }

        /* If old is the same, then there is no point creating a new one */
        if (old != null && old.action == action)
        {
            old.config = config;
            old.setSpeed(1);

            return old;
        }

        return new ActionPlayback(action, config, looping, priority);
    }

    @Override
    public void update(IEntity entity)
    {
        /* Update primary actions */
        if (this.basePre != null)
        {
            this.basePre.update();
        }

        if (this.basePost != null)
        {
            this.basePost.update();
        }
    }

    @Override
    public void applyActions(IEntity target, IModelInstance armature, float transition)
    {
        if (target == null)
        {
            return;
        }

        if (this.basePre != null)
        {
            this.basePre.apply(target, armature.getModel(), transition, 1F, false);
        }

        IModel model = armature.getModel();
        ItemStack main = target.getEquipmentStack(EquipmentSlot.MAINHAND);
        ItemStack offhand = target.getEquipmentStack(EquipmentSlot.OFFHAND);

        boolean isRolling = target.getRoll() > 4;
        boolean isInSwimmingPose = target.getEntityPose() == EntityPose.SWIMMING;

        /* Common variables */
        float handSwingProgress = target.getHandSwingProgress(transition);
        float age = target.getAge() + transition;
        float bodyYaw = (float) Lerps.lerpYaw(target.getPrevBodyYaw(), target.getBodyYaw(), transition);
        float headYaw = (float) Lerps.lerpYaw(target.getPrevHeadYaw(), target.getHeadYaw(), transition);
        float yaw = headYaw - bodyYaw;
        float pitch = (float) Lerps.lerp(target.getPrevPitch(), target.getPitch(), transition);
        float limbSpeed = target.getLimbSpeed(transition);
        float limbPhase = target.getLimbPos(transition);
        Vec3d entityVelocity = target.getVelocity();
        double dx = target.getX() - target.getPrevX();
        double dz = target.getZ() - target.getPrevZ();
        float velocityHorizontalSpeed = (float) Math.sqrt(entityVelocity.x * entityVelocity.x + entityVelocity.z * entityVelocity.z) * 20F;
        float displacementHorizontalSpeed = (float) Math.sqrt(dx * dx + dz * dz) * 20F;
        float horizontalSpeed = Math.max(velocityHorizontalSpeed, displacementHorizontalSpeed);
        float bodyYawRad = MathUtils.toRad(bodyYaw);
        float forwardX = -MathHelper.sin(bodyYawRad);
        float forwardZ = MathHelper.cos(bodyYawRad);
        float velocityForwardSpeed = ((float) entityVelocity.x * forwardX + (float) entityVelocity.z * forwardZ) * 20F;
        float displacementForwardSpeed = ((float) dx * forwardX + (float) dz * forwardZ) * 20F;
        float forwardSpeed = Math.abs(velocityForwardSpeed) >= Math.abs(displacementForwardSpeed) ? velocityForwardSpeed : displacementForwardSpeed;
        /* Film actors: ActionPlayer lookahead velocity creates a one-frame void
         * (velocity says walking, LimbAnimator still idle). Filling that void from
         * velocity + age*0.6662 caused the idle→walk microsnap. Gate swing/gecko
         * motion on real prev→pos displacement instead; keep LimbAnimator for the
         * rest of the walk once it catches up. */
        boolean filmActor = target instanceof MCEntity mcEntity
            && mcEntity.getMcEntity() instanceof ActorEntity;

        if (filmActor)
        {
            horizontalSpeed = displacementHorizontalSpeed;
            forwardSpeed = displacementForwardSpeed;
        }

        if (!target.isRiding() && !target.isSitting())
        {
            if (limbSpeed < 0.01F && horizontalSpeed > 0.08F)
            {
                float synthesized = MathHelper.clamp(horizontalSpeed / 4F, 0F, 1F);

                /* Soft-fill like one vanilla updateLimbs(0.4) step from real
                 * motion — complements the void without a phase jump. */
                limbSpeed = synthesized * 0.4F;
                limbPhase = limbPhase + limbSpeed;

                if (displacementHorizontalSpeed > 10F)
                {
                    limbPhase = age * 0.6662F;
                }
            }
            else if (limbPhase == 0F && horizontalSpeed > 0.08F)
            {
                float synthesized = MathHelper.clamp(horizontalSpeed / 4F, 0F, 1F);

                limbSpeed = Math.max(limbSpeed, synthesized * 0.4F);
                limbPhase = limbPhase + Math.max(limbSpeed, 0.01F);
            }
        }
        else
        {
            limbSpeed = 0F;
            horizontalSpeed = 0F;
            forwardSpeed = 0F;
        }

        boolean riding = target.isRiding() || target.isSitting();
        boolean isMainHandBlocking = target.getActiveHand() == Hand.MAIN_HAND;
        Map<GeckoLimbRole, String> roleBones = ProceduralPoseDefaults.buildRoleBoneMap(model);
        String rightArmBone = this.roleBone(roleBones, GeckoLimbRole.RIGHT_ARM, "right_arm");
        String leftArmBone = this.roleBone(roleBones, GeckoLimbRole.LEFT_ARM, "left_arm");
        String rightLegBone = this.roleBone(roleBones, GeckoLimbRole.RIGHT_LEG, "right_leg");
        String leftLegBone = this.roleBone(roleBones, GeckoLimbRole.LEFT_LEG, "left_leg");
        String torsoBone = this.roleBone(roleBones, GeckoLimbRole.TORSO, "torso");

        float leaningPitch = target.getLeaningPitch(transition);
        float flyProgress = target.getFallFlyingProgress(transition);
        String headBone = armature.getHeadBone();

        float coefficient = 1F;

        if (isRolling)
        {
            coefficient = (float) (target.getVelocity().lengthSquared() / 2D);
            coefficient = Math.min(1F, coefficient * coefficient * coefficient);
        }

        model.resetPose();

        if (riding)
        {
            this.applyRidingPoseOrAnimation(target, armature, model, transition);
        }
        else if (target.isSneaking())
        {
            model.applyPose(armature.getSneakingPose());
        }

        /* For regular models */
        if (model instanceof Model)
        {
            ModelGroup leftArm = null;
            ModelGroup rightArm = null;
            ModelGroup torso = null;

            for (ModelGroup group : model.getAllGroups())
            {
                if (group.id.equals("anchor"))
                {
                    if (target.isSleeping())
                    {
                        group.current.rotate.x = 90.0F;
                        group.current.translate.y -= 14F;
                    }
                    else if (target.isUsingRiptide())
                    {
                        group.current.rotate.x = -90.0F - pitch;
                        group.current.rotate2.y = age * -75.0F;
                    }
                    else if (flyProgress > 0F || target.isFallFlying())
                    {
                        group.current.rotate.x = MathHelper.lerp(flyProgress, 0F, -90.0F - pitch);
                        group.current.rotate.y = 0F;
                        group.current.rotate.z = 0F;
                    }
                    else if (target.isCrawling())
                    {
                        group.current.rotate.x = -90.0F - pitch;
                        group.current.translate.y -= 0.5F * 16F;
                        group.current.translate.z += 0.3F * 16F;
                    }
                    else if (leaningPitch > 0F || target.isSwimming())
                    {
                        float newPitch = target.isTouchingWater() ? -90F - pitch : -90F;

                        group.current.rotate.x = MathHelper.lerp(leaningPitch > 0F ? leaningPitch : 1F, 0F, newPitch);

                        if (target.getEntityPose() == EntityPose.SWIMMING || target.isSwimming())
                        {
                            group.current.translate.y -= 0.5F * 16F;
                            group.current.translate.z += 0.3F * 16F;
                        }
                    }
                }
                else if (group.id.equals(headBone))
                {
                    group.current.rotate.y = -yaw;

                    if (isRolling)
                    {
                        group.current.rotate.x = 45;
                    }
                    else if (flyProgress > 0F || target.isFallFlying() || target.getEntityPose() == EntityPose.GLIDING)
                    {
                        group.current.rotate.x = this.lerpAngle(flyProgress, -pitch, -pitch + 90F);
                    }
                    else if (leaningPitch > 0F)
                    {
                        group.current.rotate.x = this.lerpAngle(leaningPitch, group.current.rotate.x, isInSwimmingPose ? 45 : -pitch);
                    }
                    else
                    {
                        group.current.rotate.x = -pitch;
                    }
                }
                else if (group.id.equals(rightArmBone))
                {
                    if (!riding)
                    {
                        if (target.isFallFlying())
                        {
                            group.current.rotate.x = 0F;
                            group.current.rotate.y = 0F;
                            group.current.rotate.z = 0F;
                        }
                        else if (target.isSwimming())
                        {
                            float swimProgress = age * 0.12F + limbPhase * 0.2F;
                            float strokePhase = (float) (Math.sin(swimProgress) * 0.5F + 0.5F);
                            float armPitch = 180F - strokePhase * 90F;
                            float armSweep = strokePhase * 90F;

                            group.current.rotate.x = armPitch;
                            group.current.rotate.y = -armSweep;
                            group.current.rotate.z = 0F;
                        }
                        else if (target.isBlocking() && isMainHandBlocking)
                        {
                            group.current.rotate.x = 45F;
                            group.current.rotate.y = 25F;
                            group.current.rotate.z = 15F;
                        }
                        else
                        {
                            group.current.rotate.x += MathUtils.toDeg(MathHelper.cos(limbPhase * 0.6662F) * 2.0F * limbSpeed * 0.5F / coefficient);
                            group.current.rotate.z += MathUtils.toDeg(1F * (MathHelper.cos(-age * 0.09F) * 0.05F + 0.05F));
                            group.current.rotate.x += MathUtils.toDeg(1F * MathHelper.sin(-age * 0.067F) * 0.05F);

                            if (!main.isEmpty())
                            {
                                group.current.rotate.x = group.current.rotate.x * 0.5F + 18F;
                            }
                        }
                    }

                    rightArm = group;
                }
                else if (group.id.equals(leftArmBone))
                {
                    if (!riding)
                    {
                        if (target.isFallFlying())
                        {
                            group.current.rotate.x = 0F;
                            group.current.rotate.y = 0F;
                            group.current.rotate.z = 0F;
                        }
                        else if (target.isSwimming())
                        {
                            float swimProgress = age * 0.12F + limbPhase * 0.2F;
                            float strokePhase = (float) (Math.sin(swimProgress) * 0.5F + 0.5F);
                            float armPitch = 180F - strokePhase * 90F;
                            float armSweep = strokePhase * 90F;

                            group.current.rotate.x = armPitch;
                            group.current.rotate.y = armSweep;
                            group.current.rotate.z = 0F;
                        }
                        else if (target.isBlocking() && !isMainHandBlocking)
                        {
                            group.current.rotate.x = 45F;
                            group.current.rotate.y = -25F;
                            group.current.rotate.z = -15F;
                        }
                        else
                        {
                            group.current.rotate.x += MathUtils.toDeg(MathHelper.cos(limbPhase * 0.6662F + 3.1415927F) * 2.0F * limbSpeed * 0.5F / coefficient);
                            group.current.rotate.z += MathUtils.toDeg(-1F * (MathHelper.cos(-age * 0.09F) * 0.05F + 0.05F));
                            group.current.rotate.x += MathUtils.toDeg(-1F * MathHelper.sin(-age * 0.067F) * 0.05F);

                            if (!offhand.isEmpty())
                            {
                                group.current.rotate.x = group.current.rotate.x * 0.5F + 18F;
                            }
                        }
                    }

                    leftArm = group;
                }
                else if (group.id.equals(torsoBone))
                {
                    torso = group;
                }
                else if (group.id.equals(rightLegBone))
                {
                    if (!riding)
                    {
                        if (target.isFallFlying())
                        {
                            group.current.rotate.x = 0F;
                            group.current.rotate.y = 0F;
                            group.current.rotate.z = 0F;
                        }
                        else if (target.isSwimming())
                        {
                            float swimProgress = age * 0.15F + limbPhase * 0.2F;
                            group.current.rotate.x = (float) Math.cos(swimProgress * 0.5F) * 18F;
                            group.current.rotate.y = 0F;
                            group.current.rotate.z = 0F;
                        }
                        else
                        {
                            group.current.rotate.x = MathUtils.toDeg(MathHelper.cos(limbPhase * 0.6662F + 3.1415927F) * 1.4F * limbSpeed / coefficient);
                        }
                    }
                }
                else if (group.id.equals(leftLegBone))
                {
                    if (!riding)
                    {
                        if (target.isFallFlying())
                        {
                            group.current.rotate.x = 0F;
                            group.current.rotate.y = 0F;
                            group.current.rotate.z = 0F;
                        }
                        else if (target.isSwimming())
                        {
                            float swimProgress = age * 0.15F + limbPhase * 0.2F;
                            group.current.rotate.x = -(float) Math.cos(swimProgress * 0.5F) * 18F;
                            group.current.rotate.y = 0F;
                            group.current.rotate.z = 0F;
                        }
                        else
                        {
                            group.current.rotate.x = MathUtils.toDeg(MathHelper.cos(limbPhase * 0.6662F) * 1.4F * limbSpeed / coefficient);
                        }
                    }
                }
            }

            if (!riding && leftArm != null && rightArm != null)
            {
                ProceduralItemUsePoses.applyModel(target, leftArm, rightArm, main, offhand, pitch, yaw, transition);
            }

            if (!riding && handSwingProgress > 0F && leftArm != null && rightArm != null)
            {
                float swingFactor = handSwingProgress;
                float swingBodyYaw = -MathUtils.toDeg(MathHelper.sin(MathHelper.sqrt(swingFactor) * MathUtils.PI * 2F) * 0.2F);

                if (torso != null)
                {
                    torso.current.rotate.y = swingBodyYaw;
                }

                leftArm.current.translate.z += (float) Math.sin(MathUtils.toRad(swingBodyYaw)) * 5F;
                leftArm.current.translate.x += (float) Math.cos(MathUtils.toRad(swingBodyYaw)) * 5F - 5F;
                rightArm.current.translate.z -= (float) Math.sin(MathUtils.toRad(swingBodyYaw)) * 5F;
                rightArm.current.translate.x -= (float) Math.cos(MathUtils.toRad(swingBodyYaw)) * 5F - 5F;

                rightArm.current.rotate.y += swingBodyYaw;
                leftArm.current.rotate.y += swingBodyYaw;
                leftArm.current.rotate.x += swingBodyYaw;

                swingFactor = 1F - handSwingProgress;
                swingFactor *= swingFactor;
                swingFactor *= swingFactor;
                swingFactor = 1F - swingFactor;

                float headPitch = 0F;
                float swing1 = MathHelper.sin(swingFactor * MathUtils.PI);
                float swing2 = MathHelper.sin(handSwingProgress * MathUtils.PI) * -(headPitch - 0.7F) * 0.75F;

                /* Apply swing on the attacking arm's own pitch (vanilla BipedEntityModel).
                 * Using the other arm's pitch as base made the arm snap when progress hit 0. */
                rightArm.current.rotate.x += MathUtils.toDeg(swing1 * 1.2F + swing2);
                rightArm.current.rotate.y += swingBodyYaw * 2F;
                rightArm.current.rotate.z += MathUtils.toDeg(MathHelper.sin(handSwingProgress * MathUtils.PI) * -0.4F);
            }
        }
        /* For BOBJ models */
        else
        {
            BOBJBone bobjLeftArm = null;
            BOBJBone bobjRightArm = null;

            for (BOBJBone bone : model.getAllBOBJBones())
            {
                if (bone.name.equals("anchor"))
                {
                    if (target.isSleeping())
                    {
                        bone.transform.rotate.x = MathUtils.toRad(90.0F);
                        bone.transform.translate.y -= MathUtils.toRad(14F);
                    }
                    else if (target.isUsingRiptide())
                    {
                        bone.transform.rotate.x = MathUtils.toRad(-90.0F - pitch);
                        bone.transform.rotate2.y = MathUtils.toRad(age * -75.0F);
                    }
                    else if (flyProgress > 0F || target.isFallFlying())
                    {
                        bone.transform.rotate.x = MathUtils.toRad(MathHelper.lerp(flyProgress, 0F, -90.0F - pitch));
                        bone.transform.rotate.y = 0F;
                        bone.transform.rotate.z = 0F;
                    }
                    else if (target.isCrawling())
                    {
                        bone.transform.rotate.x = MathUtils.toRad(-90.0F - pitch);
                        bone.transform.translate.y -= MathUtils.toRad(0.5F * 16F);
                        bone.transform.translate.z += MathUtils.toRad(0.3F * 16F);
                    }
                    else if (leaningPitch > 0F || target.isSwimming())
                    {
                        float newPitch = target.isTouchingWater() ? -90F - pitch : -90F;

                        bone.transform.rotate.x = MathUtils.toRad(MathHelper.lerp(leaningPitch > 0F ? leaningPitch : 1F, 0F, newPitch));

                        if (target.getEntityPose() == EntityPose.SWIMMING || target.isSwimming())
                        {
                            bone.transform.translate.y -= MathUtils.toRad(0.5F * 16F);
                            bone.transform.translate.z += MathUtils.toRad(0.3F * 16F);
                        }
                    }
                }
                else if (bone.name.equals(headBone))
                {
                    bone.transform.rotate.y = MathUtils.toRad(-yaw);

                    if (isRolling)
                    {
                        bone.transform.rotate.x = -MathUtils.toRad(45);
                    }
                    else if (flyProgress > 0F || target.isFallFlying() || target.getEntityPose() == EntityPose.GLIDING)
                    {
                        bone.transform.rotate.x = -MathUtils.toRad(this.lerpAngle(flyProgress, -pitch, -pitch + 90F));
                    }
                    else if (leaningPitch > 0F)
                    {
                        bone.transform.rotate.x = -MathUtils.toRad(this.lerpAngle(leaningPitch, bone.transform.rotate.x, isInSwimmingPose ? 45 : -pitch));
                    }
                    else
                    {
                        bone.transform.rotate.x = -MathUtils.toRad(-pitch);
                    }
                }
                else if (bone.name.equals(rightArmBone))
                {
                    if (!riding)
                    {
                        if (target.isFallFlying())
                        {
                            bone.transform.rotate.x = 0F;
                            bone.transform.rotate.y = 0F;
                            bone.transform.rotate.z = 0F;
                        }
                        else if (target.isSwimming())
                        {
                            float swimProgress = age * 0.12F + limbPhase * 0.2F;
                            float strokePhase = (float) (Math.sin(swimProgress) * 0.5F + 0.5F);
                            float armPitch = 180F - strokePhase * 90F;
                            float armSweep = strokePhase * 90F;

                            bone.transform.rotate.x = MathUtils.toRad(armPitch);
                            bone.transform.rotate.y = -MathUtils.toRad(armSweep);
                            bone.transform.rotate.z = 0F;
                        }
                        else if (target.isBlocking() && isMainHandBlocking)
                        {
                            bone.transform.rotate.x = MathUtils.toRad(45F);
                            bone.transform.rotate.y = MathUtils.toRad(25F);
                            bone.transform.rotate.z = MathUtils.toRad(15F);
                        }
                        else
                        {
                            bone.transform.rotate.x += MathHelper.cos(limbPhase * 0.6662F) * 2.0F * limbSpeed * 0.5F / coefficient;
                            bone.transform.rotate.z -= 1F * (MathHelper.cos(-age * 0.09F) * 0.05F + 0.05F);
                            bone.transform.rotate.x += 1F * MathHelper.sin(-age * 0.067F) * 0.05F;

                            if (!main.isEmpty())
                            {
                                bone.transform.rotate.x = bone.transform.rotate.x * 0.5F + MathUtils.toRad(18F);
                            }
                        }
                    }

                    bobjRightArm = bone;
                }
                else if (bone.name.equals(leftArmBone))
                {
                    if (!riding)
                    {
                        if (target.isFallFlying())
                        {
                            bone.transform.rotate.x = 0F;
                            bone.transform.rotate.y = 0F;
                            bone.transform.rotate.z = 0F;
                        }
                        else if (target.isSwimming())
                        {
                            float swimProgress = age * 0.12F + limbPhase * 0.2F;
                            float strokePhase = (float) (Math.sin(swimProgress) * 0.5F + 0.5F);
                            float armPitch = 180F - strokePhase * 90F;
                            float armSweep = strokePhase * 90F;

                            bone.transform.rotate.x = MathUtils.toRad(armPitch);
                            bone.transform.rotate.y = MathUtils.toRad(armSweep);
                            bone.transform.rotate.z = 0F;
                        }
                        else if (target.isBlocking() && !isMainHandBlocking)
                        {
                            bone.transform.rotate.x = MathUtils.toRad(45F);
                            bone.transform.rotate.y = -MathUtils.toRad(25F);
                            bone.transform.rotate.z = -MathUtils.toRad(15F);
                        }
                        else
                        {
                            bone.transform.rotate.x += MathHelper.cos(limbPhase * 0.6662F + 3.1415927F) * 2.0F * limbSpeed * 0.5F / coefficient;
                            bone.transform.rotate.z -= -1F * (MathHelper.cos(-age * 0.09F) * 0.05F + 0.05F);
                            bone.transform.rotate.x += -1F * MathHelper.sin(-age * 0.067F) * 0.05F;

                            if (!offhand.isEmpty())
                            {
                                bone.transform.rotate.x = bone.transform.rotate.x * 0.5F + MathUtils.toRad(18F);
                            }
                        }
                    }

                    bobjLeftArm = bone;
                }
                else if (bone.name.equals(rightLegBone))
                {
                    if (!riding)
                    {
                        if (target.isFallFlying())
                        {
                            bone.transform.rotate.x = 0F;
                            bone.transform.rotate.y = 0F;
                            bone.transform.rotate.z = 0F;
                        }
                        else if (target.isSwimming())
                        {
                            float swimProgress = age * 0.15F + limbPhase * 0.2F;
                            bone.transform.rotate.x = MathUtils.toRad((float) Math.cos(swimProgress * 0.5F) * 18F);
                            bone.transform.rotate.y = 0F;
                            bone.transform.rotate.z = 0F;
                        }
                        else
                        {
                            bone.transform.rotate.x = MathHelper.cos(limbPhase * 0.6662F + 3.1415927F) * 1.4F * limbSpeed / coefficient;
                        }
                    }
                }
                else if (bone.name.equals(leftLegBone))
                {
                    if (!riding)
                    {
                        if (target.isFallFlying())
                        {
                            bone.transform.rotate.x = 0F;
                            bone.transform.rotate.y = 0F;
                            bone.transform.rotate.z = 0F;
                        }
                        else if (target.isSwimming())
                        {
                            float swimProgress = age * 0.15F + limbPhase * 0.2F;
                            bone.transform.rotate.x = -MathUtils.toRad((float) Math.cos(swimProgress * 0.5F) * 18F);
                            bone.transform.rotate.y = 0F;
                            bone.transform.rotate.z = 0F;
                        }
                        else
                        {
                            bone.transform.rotate.x = MathHelper.cos(limbPhase * 0.6662F) * 1.4F * limbSpeed / coefficient;
                        }
                    }
                }
            }

            if (!riding && bobjLeftArm != null && bobjRightArm != null)
            {
                ProceduralItemUsePoses.applyBobj(target, bobjLeftArm, bobjRightArm, main, offhand, pitch, yaw, transition);
            }

            if (!riding && handSwingProgress > 0F && bobjLeftArm != null && bobjRightArm != null)
            {
                float swingFactor = handSwingProgress;
                float rotate = -MathUtils.toDeg(MathHelper.sin(MathHelper.sqrt(swingFactor) * MathUtils.PI * 2F) * 0.2F);

                bobjLeftArm.transform.translate.z -= ((float) Math.sin(MathUtils.toRad(rotate)) * 5F) / 16F;
                bobjLeftArm.transform.translate.x -= ((float) Math.cos(MathUtils.toRad(rotate)) * 5F - 5F) / 16F;
                bobjRightArm.transform.translate.z += ((float) Math.sin(MathUtils.toRad(rotate)) * 5F) / 16F;
                bobjRightArm.transform.translate.x += ((float) Math.cos(MathUtils.toRad(rotate)) * 5F - 5F) / 16F;

                bobjRightArm.transform.rotate.y -= MathUtils.toRad(rotate);
                bobjLeftArm.transform.rotate.y -= MathUtils.toRad(rotate);
                bobjLeftArm.transform.rotate.x += MathUtils.toRad(rotate);

                swingFactor = 1F - handSwingProgress;
                swingFactor *= swingFactor;
                swingFactor *= swingFactor;
                swingFactor = 1F - swingFactor;

                float headPitch = 0F;
                float swing1 = MathHelper.sin(swingFactor * MathUtils.PI);
                float swing2 = MathHelper.sin(handSwingProgress * MathUtils.PI) * -(headPitch - 0.7F) * 0.75F;

                /* Same as the non-BOBJ path: swing offsets the attacking arm, do not
                 * replace its pitch with the other arm's (caused end-of-swipe snaps). */
                bobjRightArm.transform.rotate.x += swing1 * 1.2F + swing2;
                bobjRightArm.transform.rotate.y -= MathUtils.toRad(rotate * 2F);
                bobjRightArm.transform.rotate.z -= MathHelper.sin(handSwingProgress * MathUtils.PI) * -0.4F;
            }
        }

        GeckoAnimationContext geckoContext = new GeckoAnimationContext();
        geckoContext.handSwing = handSwingProgress;
        geckoContext.age = age;
        geckoContext.yaw = yaw;
        geckoContext.pitch = pitch;
        geckoContext.limbSpeed = limbSpeed;
        geckoContext.limbPhase = limbPhase;
        geckoContext.movementCoefficient = coefficient;
        geckoContext.roll = target.getRoll() + transition;
        geckoContext.forwardSpeed = forwardSpeed;
        geckoContext.horizontalSpeed = horizontalSpeed;
        geckoContext.verticalSpeed = (float) entityVelocity.y * 20F;
        geckoContext.onGround = target.isOnGround();
        geckoContext.sneaking = target.isSneaking();
        geckoContext.riding = target.isRiding() || target.isSitting();
        geckoContext.sprinting = target.isSprinting();
        geckoContext.swimming = target.getEntityPose() == EntityPose.SWIMMING;
        geckoContext.fallFlying = target.isFallFlying();
        geckoContext.usingRiptide = target.isUsingRiptide();
        geckoContext.hasMainHandItem = !main.isEmpty();
        geckoContext.hasOffHandItem = !offhand.isEmpty();
        geckoContext.preview = target instanceof StubEntity;
        geckoContext.previewWheelSpeed = this.geckoAnimations.previewWheelSpeed;

        this.geckoController.apply(target, model, this.model == null ? null : this.model.getAnimations(), this.geckoAnimations, geckoContext);

        if (this.basePost != null)
        {
            this.basePost.postApply(target, armature.getModel(), transition);
        }
    }

    @Override
    public void playAnimation(String name)
    {}

    private ActionPlayback getRidingAction(IEntity target)
    {
        IEntity mount = target.getMountTarget();
        boolean mountMoves = false;

        if (mount != null)
        {
            double mdx = mount.getX() - mount.getPrevX();
            double mdz = mount.getZ() - mount.getPrevZ();

            mountMoves = Math.abs(mdx) > 0.003D || Math.abs(mdz) > 0.003D;
        }

        if (!mountMoves && this.ridingIdle != null)
        {
            return this.ridingIdle;
        }

        return this.riding;
    }

    private void applyRidingPoseOrAnimation(IEntity target, IModelInstance armature, IModel model, float transition)
    {
        Pose ridingPose = armature.getRidingPose();

        if (ridingPose != null && !ridingPose.isEmpty())
        {
            /* Anchor offset is for pose-editor preview; world position is handled by mount sync */
            Pose applied = ridingPose.copy();
            String anchorBone = model.getAnchor();

            if (anchorBone == null || anchorBone.isEmpty())
            {
                anchorBone = "anchor";
            }

            applied.transforms.remove(anchorBone);
            model.applyPose(applied);

            return;
        }

        ActionPlayback ridingAction = this.getRidingAction(target);

        if (ridingAction == null)
        {
            return;
        }

        ridingAction.update();
        ridingAction.apply(target, model, transition, 1F, false);
    }

    private String roleBone(Map<GeckoLimbRole, String> roleBones, GeckoLimbRole role, String fallback)
    {
        String bone = roleBones.get(role);

        return bone != null ? bone : fallback;
    }

    protected float lerpAngle(float a, float b, float magnitude)
    {
        float factor = (magnitude - b) % (360);

        if (factor < -180) factor += 360;
        if (factor >= 180) factor -= 360;

        return b + a * factor;
    }

}
