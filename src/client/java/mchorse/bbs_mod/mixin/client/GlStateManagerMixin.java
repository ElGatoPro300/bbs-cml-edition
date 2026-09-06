package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.client.BBSRendering;

import com.mojang.blaze3d.opengl.GlStateManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlStateManager.class)
public class GlStateManagerMixin
{
    @Inject(method = "_enableCull", at = @At("HEAD"), cancellable = true)
    private static void onEnableCull(CallbackInfo ci)
    {
        if (BBSRendering.isCullForcedDisabled())
        {
            ci.cancel();
        }
    }

    @Inject(method = "_glUniform1i", at = @At("HEAD"), cancellable = true)
    private static void onGlUniform1i(int location, int value, CallbackInfo ci)
    {
        if (location < 0)
        {
            ci.cancel();
        }
    }
}
