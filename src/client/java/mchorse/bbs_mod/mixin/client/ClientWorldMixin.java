package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.client.renderer.MorphMobParticles;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleEffect;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public class ClientWorldMixin
{
    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V", at = @At("HEAD"), cancellable = true)
    private void onAddParticle(ParticleEffect parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ, CallbackInfo info)
    {
        if (MorphMobParticles.shouldSuppress())
        {
            info.cancel();
        }
    }

    @Inject(method = "addImportantParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void onAddImportantParticle(ParticleEffect parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ, CallbackInfo info)
    {
        if (MorphMobParticles.shouldSuppress())
        {
            info.cancel();
        }
    }
}
