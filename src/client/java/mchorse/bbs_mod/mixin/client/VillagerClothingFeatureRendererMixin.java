package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.MobForm;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.VillagerClothingFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Villager type/profession layers are separate dynamic cutouts. On film stubs
 * ({@code AFTER_ENTITIES}) the last clothing layer was deferred until a late
 * {@code Immediate.draw()} after held-item/shadow, with the wrong light basis.
 * Restore vanilla entity lights before clothing, then flush the pending layer
 * as soon as the clothing feature finishes.
 */
@Mixin(VillagerClothingFeatureRenderer.class)
public class VillagerClothingFeatureRendererMixin
{
    @Inject(
        method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V",
        at = @At("HEAD")
    )
    private void bbs$prepareClothingLighting(
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
        if (!this.bbs$shouldFixMobFormClothing())
        {
            return;
        }

        BBSRendering.prepareVanillaEntityLighting();
    }

    @Inject(
        method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V",
        at = @At("TAIL")
    )
    private void bbs$flushClothingLayers(
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
        if (!this.bbs$shouldFixMobFormClothing())
        {
            return;
        }

        BBSRendering.prepareVanillaEntityLighting();

        if (vertexConsumers instanceof CustomVertexConsumerProvider custom)
        {
            custom.drawCurrentLayer();
        }
        else if (vertexConsumers instanceof VertexConsumerProvider.Immediate immediate)
        {
            immediate.drawCurrentLayer();
        }
    }

    private boolean bbs$shouldFixMobFormClothing()
    {
        if (!(FormUtilsClient.getCurrentForm() instanceof MobForm))
        {
            return false;
        }

        return BBSRendering.isRenderingWorld() && !BBSRendering.isIrisShadowPass();
    }
}
