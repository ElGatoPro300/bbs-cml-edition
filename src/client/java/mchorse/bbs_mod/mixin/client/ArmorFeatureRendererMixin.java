package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.FormUtilsClient;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;

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
        method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V",
        at = @At("HEAD")
    )
    private void bbs$prepareArmorLighting(
        MatrixStack matrices,
        VertexConsumerProvider vertexConsumers,
        int light,
        LivingEntity entity,
        float limbAngle,
        float limbDistance,
        float tickDelta,
        float animationProgress,
        float headYaw,
        float headPitch,
        CallbackInfo info
    )
    {
        if (FormUtilsClient.shouldFlushMobFormFeatureLayers())
        {
            BBSRendering.prepareVanillaEntityLighting();
        }
    }

    @Inject(
        method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V",
        at = @At("TAIL")
    )
    private void bbs$flushArmorLayers(
        MatrixStack matrices,
        VertexConsumerProvider vertexConsumers,
        int light,
        LivingEntity entity,
        float limbAngle,
        float limbDistance,
        float tickDelta,
        float animationProgress,
        float headYaw,
        float headPitch,
        CallbackInfo info
    )
    {
        FormUtilsClient.flushMobFormFeatureLayers(vertexConsumers);
    }
}
