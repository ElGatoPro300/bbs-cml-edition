package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.client.SunPathRotation;

import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.world.MoonPhase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRendering.class)
public class SkyRenderingMixin
{
    @Inject(method = "renderCelestialBodies", at = @At("HEAD"))
    private void bbs$applySunPathToCelestialBodies(MatrixStack matrices, float skyAngle, float starBrightness, float rainGradient, MoonPhase moonPhase, float alpha, float skyDarkness, CallbackInfo info)
    {
        SunPathRotation.applyY(matrices.peek().getPositionMatrix());
    }

    @Inject(method = "renderGlowingSky", at = @At("HEAD"))
    private void bbs$applySunPathToGlowingSky(MatrixStack matrices, float skyAngle, int color, CallbackInfo info)
    {
        matrices.push();
        SunPathRotation.applyY(matrices.peek().getPositionMatrix());
    }

    @Inject(method = "renderGlowingSky", at = @At("RETURN"))
    private void bbs$popSunPathFromGlowingSky(MatrixStack matrices, float skyAngle, int color, CallbackInfo info)
    {
        matrices.pop();
    }
}
