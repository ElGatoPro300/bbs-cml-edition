package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.FormUtilsClient;

import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Armor uses per-texture cutout layers on Immediate's fallback buffer.
 * Flush when the feature finishes so a later held-item throw cannot drop it.
 */
@Mixin(ArmorFeatureRenderer.class)
public class ArmorFeatureRendererMixin
{
    @Inject(
        method = "render",
        at = @At("HEAD")
    )
    private void bbs$prepareArmorLighting(
        MatrixStack matrices,
        OrderedRenderCommandQueue queue,
        int light,
        BipedEntityRenderState state,
        float armYaw,
        float pitch,
        CallbackInfo info
    )
    {
        if (FormUtilsClient.shouldFlushMobFormFeatureLayers())
        {
            BBSRendering.prepareVanillaEntityLighting();
        }
    }

    @Inject(
        method = "render",
        at = @At("TAIL")
    )
    private void bbs$flushArmorLayers(
        MatrixStack matrices,
        OrderedRenderCommandQueue queue,
        int light,
        BipedEntityRenderState state,
        float armYaw,
        float pitch,
        CallbackInfo info
    )
    {
        FormUtilsClient.flushMobFormFeatureLayers(queue);
    }
}
