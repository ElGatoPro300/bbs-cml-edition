package mchorse.bbs_mod.mixin;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.actions.AttackDamage;
import mchorse.bbs_mod.actions.types.AttackActionClip;
import mchorse.bbs_mod.entity.ActorEntity;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.structure.ModelBlockSolidCollisions;
import mchorse.bbs_mod.network.ServerNetwork;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class LivingEntityMixin
{
    /**
     * Record the amount passed into {@link LivingEntity#damage} after a successful
     * hit. That value already includes vanilla attack cooldown, critical hits,
     * strength, and weapon enchants — do <b>not</b> replace it with full weapon
     * damage ({@link AttackDamage#fromAttacker}), or spam-clicks replay as full hits.
     */
    @Inject(method = "damage", at = @At("RETURN"))
    private void onDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> info)
    {
        if (!Boolean.TRUE.equals(info.getReturnValue()))
        {
            return;
        }

        LivingEntity target = (LivingEntity) (Object) this;
        Entity attacker = source.getAttacker();

        /* Player melee → ActionRecorder on the player replay (existing path). */
        if (source.isDirect() && attacker instanceof ServerPlayerEntity player)
        {
            float recorded = amount;

            if (AttackDamage.isMobKiller(player.getMainHandStack()))
            {
                recorded = AttackDamage.MOB_KILLER_DAMAGE;
            }
            else if (recorded < 0F)
            {
                recorded = 0F;
            }

            float damageToStore = recorded;

            BBSMod.getActions().addAction(player, () ->
            {
                AttackActionClip clip = new AttackActionClip();

                clip.damage.set(damageToStore);

                if (target instanceof ActorEntity actorEntity)
                {
                    Replay replay = actorEntity.getReplay();

                    if (replay != null)
                    {
                        clip.target.set(replay.getId());
                    }
                }

                return clip;
            });
        }

        /* Mob autocapture combat clips (client places them on captured replays). */
        if (!(target.getWorld() instanceof ServerWorld serverWorld))
        {
            return;
        }

        if (!BBSMod.getActions().hasActiveRecorders(serverWorld))
        {
            return;
        }

        float recorded = Math.max(0F, amount);
        byte kind;
        int sourceEntityId = -1;
        Entity sourceEntity = source.getSource();

        if (source.isOf(DamageTypes.THORNS))
        {
            kind = ServerNetwork.MOB_COMBAT_KIND_DAMAGE;
        }
        else if (sourceEntity instanceof ProjectileEntity projectile)
        {
            Entity owner = projectile.getOwner();

            if (owner != null)
            {
                kind = ServerNetwork.MOB_COMBAT_KIND_PROJECTILE;
                sourceEntityId = owner.getId();
            }
            else
            {
                kind = ServerNetwork.MOB_COMBAT_KIND_DAMAGE;
            }
        }
        else if (source.isDirect() && attacker instanceof LivingEntity)
        {
            kind = ServerNetwork.MOB_COMBAT_KIND_MELEE;
            sourceEntityId = attacker.getId();
        }
        else
        {
            /* Magic / environmental / other — Damage clip on the victim if captured. */
            kind = ServerNetwork.MOB_COMBAT_KIND_DAMAGE;
        }

        BBSMod.getActions().broadcastMobCombatHit(serverWorld, target.getId(), sourceEntityId, recorded, kind);
    }

    /**
     * LivingEntity overrides {@link Entity#getStepHeight()}, so the boost must live here
     * (not on Entity) or players never receive the higher step for short solid hitboxes.
     */
    @Inject(method = "getStepHeight", at = @At("RETURN"), cancellable = true)
    private void bbs$boostSolidHitboxStepHeight(CallbackInfoReturnable<Float> info)
    {
        Entity entity = (Entity) (Object) this;
        float boosted = ModelBlockSolidCollisions.boostStepHeight(entity, info.getReturnValueF());

        if (boosted > info.getReturnValueF())
        {
            info.setReturnValue(boosted);
        }
    }
}
