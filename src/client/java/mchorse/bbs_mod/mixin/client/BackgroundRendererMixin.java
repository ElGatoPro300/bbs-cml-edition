package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.utils.colors.Color;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.world.ClientWorld;

import org.joml.Vector4f;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public class BackgroundRendererMixin
{
    @Inject(method = "getFogColor", at = @At("HEAD"), cancellable = true)
    private void onGetFogColor(Camera camera, float tickDelta, ClientWorld world, int clampedViewDistance, float skyDarkness, CallbackInfoReturnable<Vector4f> info)
    {
        if (BBSRendering.isChromaSkyEnabled())
        {
            Color color = Color.rgb(BBSRendering.getChromaSkyColor());

            info.setReturnValue(new Vector4f(color.r, color.g, color.b, 1F));
        }
    }
}
