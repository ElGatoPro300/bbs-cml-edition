package mchorse.bbs_mod.mixin;

import mchorse.bbs_mod.BBSMod;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Notify recording clients when vanilla converts a mob into another type
 * (villager → zombie villager, hoglin → zoglin, etc.) so autocapture can
 * hide the old replay and start a new one for the successor.
 */
@Mixin(MobEntity.class)
public class MobEntityMixin
{
    @Inject(method = "convertTo", at = @At("RETURN"))
    private <T extends MobEntity> void bbs$onConvertTo(EntityType<T> entityType, boolean keepEquipment, CallbackInfoReturnable<T> cir)
    {
        T converted = cir.getReturnValue();

        if (converted == null)
        {
            return;
        }

        MobEntity self = (MobEntity) (Object) this;

        if (!(self.getWorld() instanceof ServerWorld serverWorld))
        {
            return;
        }

        if (!BBSMod.getActions().hasActiveRecorders(serverWorld))
        {
            return;
        }

        BBSMod.getActions().broadcastMobConversion(serverWorld, self.getId(), converted.getId());
    }
}
