package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.client.BBSRendering;

import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.render.BlockRenderLayerGroup;
import net.minecraft.client.render.SectionRenderState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SectionRenderState.class)
public class SectionRenderStateMixin
{
    @Inject(method = "renderSection", at = @At("HEAD"), cancellable = true)
    public void onRenderSection(BlockRenderLayerGroup group, GpuSampler sampler, CallbackInfo info)
    {
        if (BBSRendering.shouldHideChromaTerrain())
        {
            info.cancel();
        }
    }
}
