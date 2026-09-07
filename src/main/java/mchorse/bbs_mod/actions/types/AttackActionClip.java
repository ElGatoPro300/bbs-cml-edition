package mchorse.bbs_mod.actions.types;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.actions.ActionPlayer;
import mchorse.bbs_mod.actions.AttackDamage;
import mchorse.bbs_mod.actions.SuperFakePlayer;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.utils.clips.Clip;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.Optional;

public class AttackActionClip extends ActionClip
{
    public final ValueFloat damage = new ValueFloat("damage", 0F);
    /**
     * Replay id of the {@link ActorEntity} hit while recording. Preferred over
     * raycast on playback so actor-vs-actor kills stay reliable when body yaw
     * and look diverge.
     */
    public final ValueString target = new ValueString("target", "");

    public AttackActionClip()
    {
        super();

        this.target.invisible();
        this.add(this.damage);
        this.add(this.target);
    }

    @Override
    public void applyAction(LivingEntity actor, SuperFakePlayer player, Film film, Replay replay, int tick)
    {
        /* Dead / discarded attackers must not keep dealing bound-target damage
         * (FakePlayer + target id would otherwise still hit survivors). */
        if (actor != null && (actor.isRemoved() || actor.isDead() || actor.getHealth() <= 0F || actor.deathTime > 0))
        {
            return;
        }

        this.applyPositionRotation(player, replay, tick);

        /* Aim with look (headYaw + pitch), not body yaw — ActionPlayer snaps
         * actor yaw to headYaw, but applyPositionRotation still sets body yaw. */
        float lookYaw = replay.keyframes.headYaw.interpolate(tick).floatValue();
        float lookPitch = replay.keyframes.pitch.interpolate(tick).floatValue();

        player.setYaw(lookYaw);
        player.setHeadYaw(lookYaw);
        player.setPitch(lookPitch);

        /* Keep fake player / actor weapon in sync so attribute + Mob Killer match. */
        if (actor != null)
        {
            ItemStack main = actor.getMainHandStack();

            if (!ItemStack.areEqual(player.getMainHandStack(), main))
            {
                player.setStackInHand(Hand.MAIN_HAND, main.copy());
            }
        }
        else if (replay != null)
        {
            ItemStack main = replay.keyframes.mainHand.interpolate(tick, ItemStack.EMPTY);

            player.setStackInHand(Hand.MAIN_HAND, main == null ? ItemStack.EMPTY : main.copy());
        }

        LivingEntity damageSource = actor != null ? actor : player;
        Entity hit = this.resolveTarget(film, damageSource, player, lookYaw, lookPitch);

        if (hit == null)
        {
            return;
        }

        /* A first-person replay is bound to the real player. When an ActorEntity hits
         * that player, route damage through the same fake-player source as stub replays
         * so player damage rules match both playback paths. */
        LivingEntity damageApplier = hit instanceof PlayerEntity ? player : damageSource;

        AttackDamage.applyHit(damageApplier, hit, this.damage.get());
    }

    private Entity resolveTarget(Film film, LivingEntity damageSource, SuperFakePlayer player, float lookYaw, float lookPitch)
    {
        String targetId = this.target.get();

        if (targetId != null && !targetId.isEmpty() && film != null)
        {
            ActionPlayer actionPlayer = BBSMod.getActions().getPlayer(film.getId());
            LivingEntity bound = actionPlayer == null ? null : actionPlayer.getActor(targetId);

            if (bound != null && !bound.isRemoved() && !bound.isDead() && bound.getHealth() > 0F && bound.deathTime <= 0 && bound != damageSource)
            {
                return bound;
            }
        }

        double distance = 6D;
        Vec3d origin = player.getCameraPosVec(1F);
        Vec3d rotation = this.getLookVector(lookPitch, lookYaw);
        Vec3d end = origin.add(rotation.x * distance, rotation.y * distance, rotation.z * distance);
        HitResult blockHit = player.raycast(distance, 1F, false);
        double maxDistSq = blockHit != null ? blockHit.getPos().squaredDistanceTo(origin) : distance * distance;
        Box box = player.getBoundingBox().stretch(rotation.multiply(distance)).expand(1D, 1D, 1D);
        EntityHitResult entityHit = ProjectileUtil.raycast(damageSource, origin, end, box,
            entity -> !entity.isSpectator() && entity.canHit(), maxDistSq);

        if (entityHit != null && entityHit.getEntity() != null)
        {
            return entityHit.getEntity();
        }

        /* World raycast can miss film actors when look is slightly off; scan
         * ActionPlayer bodies with a small AABB pad (same combat session only). */
        return this.findFilmActorAlongRay(film, damageSource, origin, end, maxDistSq);
    }

    private Entity findFilmActorAlongRay(Film film, LivingEntity exclude, Vec3d origin, Vec3d end, double maxDistSq)
    {
        if (film == null)
        {
            return null;
        }

        ActionPlayer actionPlayer = BBSMod.getActions().getPlayer(film.getId());

        if (actionPlayer == null)
        {
            return null;
        }

        Entity best = null;
        double bestDist = maxDistSq;

        for (Map.Entry<String, LivingEntity> entry : actionPlayer.getActors().entrySet())
        {
            LivingEntity entity = entry.getValue();

            if (entity == null || entity == exclude || entity.isRemoved() || entity.isDead() || entity.getHealth() <= 0F || entity.deathTime > 0 || !entity.canHit())
            {
                continue;
            }

            Optional<Vec3d> hit = entity.getBoundingBox().expand(0.35D).raycast(origin, end);

            if (hit.isEmpty())
            {
                continue;
            }

            double dist = origin.squaredDistanceTo(hit.get());

            if (dist < bestDist)
            {
                bestDist = dist;
                best = entity;
            }
        }

        return best;
    }

    private Vec3d getLookVector(float pitch, float yaw)
    {
        float pitchRad = pitch * ((float) Math.PI / 180F);
        float yawRad = -yaw * ((float) Math.PI / 180F);
        float cosYaw = MathHelper.cos(yawRad);
        float sinYaw = MathHelper.sin(yawRad);
        float cosPitch = MathHelper.cos(pitchRad);
        float sinPitch = MathHelper.sin(pitchRad);

        return new Vec3d(sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch);
    }

    @Override
    protected Clip create()
    {
        return new AttackActionClip();
    }

    /**
     * After paste/dupe, rewrite bound actor targets so clips still point at the
     * copies created in the same batch (old replay id → new replay id).
     */
    public static void remapTargets(Iterable<Replay> replays, Map<String, String> oldToNewId)
    {
        if (replays == null || oldToNewId == null || oldToNewId.isEmpty())
        {
            return;
        }

        for (Replay replay : replays)
        {
            if (replay == null || replay.actions == null)
            {
                continue;
            }

            for (Clip clip : replay.actions.get())
            {
                if (!(clip instanceof AttackActionClip attack))
                {
                    continue;
                }

                String targetId = attack.target.get();

                if (targetId == null || targetId.isEmpty())
                {
                    continue;
                }

                String mapped = oldToNewId.get(targetId);

                if (mapped != null && !mapped.isEmpty())
                {
                    attack.target.set(mapped);
                }
            }
        }
    }
}
