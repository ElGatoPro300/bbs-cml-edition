package mchorse.bbs_mod.mixin;

import mchorse.bbs_mod.utils.ExportWorldFxFreeze;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * HQ export runs many catch-up world ticks while the film clock is frozen.
 * Without this, block drops fall to the ground before the settled frame is captured.
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin
{
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void bbs$freezeDuringHqSettle(CallbackInfo info)
    {
        if (!ExportWorldFxFreeze.areItemPhysicsFrozen())
        {
            return;
        }

        Entity self = (Entity) (Object) this;

        /* Keep pose for rendering; skip gravity, merge, and despawn aging. */
        self.setVelocity(0D, 0D, 0D);
        self.velocityModified = true;
        self.fallDistance = 0F;
        info.cancel();
    }
}
