package mchorse.bbs_mod.film.replays;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.entity.ActorEntity;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.mixin.LimbAnimatorAccessor;

import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;

/**
 * Copies replay vanilla pose / action state onto a physical {@link LivingEntity} actor.
 * Stub playback applies these via {@link ReplayKeyframes#apply}; actor mode previously only
 * synced rotation, so procedural/Gecko animators saw walk instead of run (and other poses).
 */
public final class ActorReplayStateSync
{
    private ActorReplayStateSync()
    {}

    /**
     * Sync animation-driving state from the keyframe-updated stub (or any {@link IEntity})
     * onto the spawned actor. Does not touch position/rotation — callers own those.
     */
    public static void syncFromSource(LivingEntity actor, IEntity source)
    {
        syncFromSource(actor, source, true);
    }

    /**
     * @param syncLimbs when false, leave {@link LivingEntity#limbAnimator} alone so the
     *                  actor can keep natural walk-cycle motion from {@code tick()} / scrub steps.
     */
    public static void syncFromSource(LivingEntity actor, IEntity source, boolean syncLimbs)
    {
        if (actor == null || source == null)
        {
            return;
        }

        boolean mounted = source.getMountTarget() != null || source.isSitting();

        actor.setSneaking(mounted ? false : source.isSneaking());
        actor.setSprinting(mounted ? false : source.isSprinting());
        actor.setSwimming(mounted ? false : source.isSwimming());
        actor.setOnGround(source.isOnGround());
        actor.setFlag(7, mounted ? false : source.isFallFlying());
        actor.setPose(resolvePose(source, mounted));
        applyHurtAndDeath(actor, source.getHurtTimer(), source.getDeathTime());
        actor.setFireTicks(source.getFireTicks());
        actor.fallDistance = source.getFallDistance();

        if (actor instanceof PlayerEntity player)
        {
            player.getAbilities().flying = mounted ? false : source.isFlying();
        }

        boolean usingItem = source.isUsingItem();

        actor.setLivingFlag(1, usingItem || source.isBlocking());
        actor.setLivingFlag(2, source.getActiveHand() == Hand.OFF_HAND && usingItem);
        actor.setLivingFlag(4, source.isUsingRiptide());

        if (syncLimbs)
        {
            syncLimbAnimator(actor, source, mounted);
        }
        else if (mounted && actor.limbAnimator instanceof LimbAnimatorAccessor actorLimb)
        {
            actorLimb.setPrevSpeed(0F);
            actorLimb.setSpeed(0F);
        }
    }

    /**
     * One StubEntity-style limb step from a horizontal move (used when scrubbing the
     * timeline with actor-pause-animations enabled).
     */
    public static void advanceLimbStep(LivingEntity actor, double fromX, double fromZ, double toX, double toZ)
    {
        if (actor == null || !(actor.limbAnimator instanceof LimbAnimatorAccessor limb))
        {
            return;
        }

        float speed = limbSpeedFromDelta(toX - fromX, toZ - fromZ);

        limb.setPrevSpeed(limb.getSpeed());
        limb.setSpeed(speed);
        limb.setPos(limb.getPos() + speed);
    }

    /**
     * Deterministic limb phase for a film tick so scrubbing (and cursor jitter) always
     * lands on the same walk pose — same stability as the alt-hover highlight silhouette.
     */
    public static void applyTimelineLimbs(LivingEntity actor, ReplayKeyframes keyframes, int tick, boolean mounted)
    {
        if (actor == null || !(actor.limbAnimator instanceof LimbAnimatorAccessor limb))
        {
            return;
        }

        if (mounted || keyframes == null)
        {
            limb.setPrevSpeed(0F);
            limb.setSpeed(0F);

            return;
        }

        int t = Math.max(0, tick);
        float speed = limbSpeedAt(keyframes, t);
        float pos = limbPosUntil(keyframes, t);

        limb.setPrevSpeed(speed);
        limb.setSpeed(speed);
        limb.setPos(pos);
    }

    public static float limbSpeedAt(ReplayKeyframes keyframes, int tick)
    {
        if (keyframes == null)
        {
            return 0F;
        }

        double x = keyframes.x.interpolate(tick);
        double z = keyframes.z.interpolate(tick);
        double prevX = keyframes.x.interpolate(tick - 1F);
        double prevZ = keyframes.z.interpolate(tick - 1F);

        return limbSpeedFromDelta(x - prevX, z - prevZ);
    }

    public static float limbPosUntil(ReplayKeyframes keyframes, int tick)
    {
        if (keyframes == null || tick <= 0)
        {
            return 0F;
        }

        float pos = 0F;
        int end = Math.min(tick, 200000);

        for (int t = 1; t <= end; t++)
        {
            pos += limbSpeedAt(keyframes, t);
        }

        return pos;
    }

    private static float limbSpeedFromDelta(double dx, double dz)
    {
        float delta = (float) MathHelper.magnitude(dx, 0D, dz);

        return Math.min(delta * 4F, 1F);
    }

    /**
     * Server-side path when only keyframes are available (no stub). Applies the same vanilla
     * pose/action flags {@link ReplayKeyframes#apply} would set on a stub.
     *
     * @param advanceLimbs when true (playing), drive limb swing from keyframe motion like
     *                     {@link StubEntity#update}; when false
     *                     (paused), settle limbs/sprint so emoticon/BOBJ can leave run for idle
     *                     (timeline-freeze mode freezes the form clock separately).
     */
    public static void applyFromKeyframes(ReplayKeyframes keyframes, float tick, LivingEntity actor, boolean mounted, boolean advanceLimbs)
    {
        if (actor == null || keyframes == null)
        {
            return;
        }

        /* Idle-settle is only for timeline-freeze OFF. With freeze ON, keep keyframe
         * sprint/limb cadence so run dust and frozen run pose stay consistent. */
        boolean timelineFreeze = BBSSettings.editorActorPauseAnimations != null
            && BBSSettings.editorActorPauseAnimations.get();
        boolean settleWhenPaused = !advanceLimbs
            && !timelineFreeze
            && BBSSettings.shouldSettleActorNaturalStopWhenPaused();
        boolean sneaking = !mounted && keyframes.sneaking.interpolate(tick) != 0D;
        boolean sprinting = !mounted && keyframes.sprinting.interpolate(tick) != 0D && (advanceLimbs || !settleWhenPaused);
        boolean swimming = !mounted && keyframes.swimming.interpolate(tick) != 0D;
        boolean flying = !mounted && keyframes.flying.interpolate(tick) != 0D;
        boolean fallFlying = !mounted && keyframes.fallFlying.interpolate(tick) != 0D;
        boolean crawling = !mounted && keyframes.crawling.interpolate(tick) != 0D;
        boolean blocking = !mounted && keyframes.blocking.interpolate(tick) != 0D;
        boolean sleeping = !mounted && keyframes.sleeping.interpolate(tick) != 0D;
        boolean riptide = !mounted && keyframes.riptide.interpolate(tick) != 0D;
        boolean grounded = keyframes.grounded.interpolate(tick) != 0D;
        boolean usingItem = keyframes.isUsingItemAt(tick);
        boolean offHand = keyframes.activeHand.interpolate(tick) > 0D;

        actor.setSneaking(sneaking);
        actor.setSprinting(sprinting);
        actor.setSwimming(swimming);
        actor.setOnGround(grounded);
        actor.setFlag(7, fallFlying);
        actor.setPose(resolvePose(sneaking, swimming, crawling, sleeping, mounted));
        applyHurtAndDeath(actor,
            keyframes.damage.interpolate(tick).intValue(),
            keyframes.deathTime.interpolate(tick).intValue());
        actor.setFireTicks(keyframes.getFireTicksAt((int) tick));
        actor.fallDistance = keyframes.fall.interpolate(tick).floatValue();

        if (actor instanceof PlayerEntity player)
        {
            player.getAbilities().flying = flying;
        }

        /* FP binds the real player — a vanilla use would consume food and
         * fight client input (stopUsingItem every tick). Client
         * ItemUseRenderState drives first-person use visuals instead. */
        boolean applyUseFlags = !(actor instanceof PlayerEntity);

        actor.setLivingFlag(1, (usingItem && applyUseFlags) || blocking);
        actor.setLivingFlag(2, offHand && usingItem && applyUseFlags);
        actor.setLivingFlag(4, riptide);

        if (!mounted && actor.limbAnimator instanceof LimbAnimatorAccessor)
        {
            if (advanceLimbs)
            {
                /* Same horizontal target as ActionPlayer forward playback velocity, with
                 * vanilla LimbAnimator lerp (0.4) — not an instant setSpeed/setPos snap. */
                double x = keyframes.x.interpolate(tick);
                double z = keyframes.z.interpolate(tick);
                double nextX = keyframes.x.interpolate(tick + 1F);
                double nextZ = keyframes.z.interpolate(tick + 1F);
                float delta = (float) MathHelper.magnitude(nextX - x, 0D, nextZ - z);
                float speed = Math.min(delta * 4F, 1F);

                actor.limbAnimator.updateLimbs(speed, 0.4F);
            }
            else if (settleWhenPaused)
            {
                /* Paused (default): do not keep refreshing walk speed from keyframe
                 * deltas — that traps emoticon/BOBJ run/sprint ActionPlayback in a loop. */
                settleNaturalStop(actor);
            }
            else
            {
                /* Legacy run-in-place: refresh limb speed from keyframes without advancing pos. */
                double x = keyframes.x.interpolate(tick);
                double z = keyframes.z.interpolate(tick);
                double prevX = keyframes.x.interpolate(tick - 1F);
                double prevZ = keyframes.z.interpolate(tick - 1F);
                float delta = (float) MathHelper.magnitude(x - prevX, 0D, z - prevZ);
                float speed = Math.min(delta * 4F, 1F);

                if (actor.limbAnimator instanceof LimbAnimatorAccessor limb)
                {
                    limb.setPrevSpeed(limb.getSpeed());
                    limb.setSpeed(speed);
                }
            }
        }
        else if (mounted && actor.limbAnimator instanceof LimbAnimatorAccessor limb)
        {
            limb.setPrevSpeed(0F);
            limb.setSpeed(0F);
        }
    }

    /**
     * Player-stop style settle for paused actor bodies when timeline animation
     * freeze is off: clear sprint so emoticon/BOBJ leave run for idle.
     * <p>
     * Does <b>not</b> zero {@code limbAnimator} — procedural forms keep decaying
     * walk swing naturally from {@link LivingEntity#tick} (forcing speed to 0
     * snapped actors to idle the moment the film paused).
     */
    public static void settleNaturalStop(LivingEntity actor)
    {
        if (actor == null)
        {
            return;
        }

        actor.setSprinting(false);
    }

    public static void applyFromKeyframes(ReplayKeyframes keyframes, float tick, LivingEntity actor, boolean mounted)
    {
        applyFromKeyframes(keyframes, tick, actor, mounted, true);
    }

    /**
     * Merge keyframed damage with any live combat on {@link ActorEntity}.
     * ActionPlayer used to assign keyframe values every tick, which wiped vanilla
     * {@code hurtTime} and made actors look immune after a few hits.
     * <p>
     * Actor death is Attack/combat-driven at playback — {@code death_time} keyframes
     * must not force-kill an actor (or keep them dead when Attack clips are disabled).
     * Live {@code hurtTime} is kept when damage flash and/or damage animation is enabled.
     * <p>
     * First-person playback binds the real {@link PlayerEntity} as the actor body; that
     * path must keep the same live/keyframe merge or Attack/Damage clips never produce
     * camera shake / hurt overlay in FP view.
     */
    private static void applyHurtAndDeath(LivingEntity actor, int keyframeHurt, int keyframeDeath)
    {
        if (!(actor instanceof ActorEntity actorEntity))
        {
            if (BBSSettings.shouldKeepActorLiveHurtTime())
            {
                actor.hurtTime = Math.max(actor.hurtTime, keyframeHurt);
            }
            else
            {
                actor.hurtTime = keyframeHurt;
            }

            if (actor.hurtTime > 0 && actor.maxHurtTime < actor.hurtTime)
            {
                actor.maxHurtTime = Math.max(10, actor.hurtTime);
            }

            /* Never force deathTime onto the real FP player from keyframes. */
            if (!(actor instanceof PlayerEntity))
            {
                actor.deathTime = keyframeDeath;
            }

            return;
        }

        actorEntity.setKeyframeHurtActive(keyframeHurt > 0);

        if (BBSSettings.shouldKeepActorLiveHurtTime())
        {
            actor.hurtTime = Math.max(actor.hurtTime, keyframeHurt);
        }
        else
        {
            actor.hurtTime = keyframeHurt;
        }

        if (actor.hurtTime > 0 && actor.maxHurtTime < actor.hurtTime)
        {
            actor.maxHurtTime = Math.max(10, actor.hurtTime);
        }
    }

    private static void syncLimbAnimator(LivingEntity actor, IEntity source, boolean mounted)
    {
        if (!(actor.limbAnimator instanceof LimbAnimatorAccessor actorLimb))
        {
            return;
        }

        if (mounted)
        {
            actorLimb.setPrevSpeed(0F);
            actorLimb.setSpeed(0F);

            return;
        }

        if (source.getLimbAnimator() instanceof LimbAnimatorAccessor sourceLimb)
        {
            actorLimb.setPrevSpeed(sourceLimb.getPrevSpeed());
            actorLimb.setSpeed(sourceLimb.getSpeed());
            actorLimb.setPos(sourceLimb.getPos());
        }
    }

    private static EntityPose resolvePose(IEntity source, boolean mounted)
    {
        EntityPose pose = source.getEntityPose();

        if ((mounted || source.isSitting()) && pose == EntityPose.STANDING)
        {
            return EntityPose.SITTING;
        }

        if (source.isSneaking() && pose == EntityPose.STANDING)
        {
            return EntityPose.CROUCHING;
        }

        return pose;
    }

    private static EntityPose resolvePose(boolean sneaking, boolean swimming, boolean crawling, boolean sleeping, boolean mounted)
    {
        if (sleeping)
        {
            return EntityPose.SLEEPING;
        }

        if (mounted)
        {
            return EntityPose.SITTING;
        }

        if (swimming || crawling)
        {
            return EntityPose.SWIMMING;
        }

        if (sneaking)
        {
            return EntityPose.CROUCHING;
        }

        return EntityPose.STANDING;
    }
}
