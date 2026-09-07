package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.client.SunPathRotation;

import net.minecraft.client.render.Fog;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRendering.class)
public class SkyRenderingMixin
{
    @Inject(method = "renderCelestialBodies", at = @At("HEAD"))
    private void bbs$applySunPathToCelestialBodies(MatrixStack matrices, VertexConsumerProvider.Immediate vertexConsumers, float skyAngle, int moonPhase, float skyDarkness, float starBrightness, Fog fog, CallbackInfo info)
    {
        SunPathRotation.applyY(matrices.peek().getPositionMatrix());
    }

    @Inject(method = "renderGlowingSky", at = @At("HEAD"))
    private void bbs$applySunPathToGlowingSky(MatrixStack matrices, VertexConsumerProvider.Immediate vertexConsumers, float skyAngle, int color, CallbackInfo info)
    {
        matrices.push();
        SunPathRotation.applyY(matrices.peek().getPositionMatrix());
    }

    @Inject(method = "renderGlowingSky", at = @At("RETURN"))
    private void bbs$popSunPathFromGlowingSky(MatrixStack matrices, VertexConsumerProvider.Immediate vertexConsumers, float skyAngle, int color, CallbackInfo info)
    {
        matrices.pop();
    }
}
