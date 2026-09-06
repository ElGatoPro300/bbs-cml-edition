package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.client.renderer.MorphRenderer;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin
{
    @WrapOperation(
        method = "render(Lnet/minecraft/entity/Entity;DDDFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/entity/EntityRenderer;render(Lnet/minecraft/client/render/entity/state/EntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"
        ),
        require = 0
    )
    private void wrapRender(
        EntityRenderer renderer, EntityRenderState state,
        MatrixStack matrices, VertexConsumerProvider vcp, int light,
        Operation<Void> original,
        @Local(argsOnly = true) Entity entity,
        @Local(argsOnly = true) float tickDelta
    ) {
        if (entity instanceof LivingEntity livingEntity && state instanceof LivingEntityRenderState livingState)
        {
            float whiteOverlayProgress = 0F;

            if (renderer instanceof LivingEntityRendererInvoker invoker)
            {
                whiteOverlayProgress = invoker.bbs$getAnimationCounter(livingState);
            }

            int u = OverlayTexture.getU(whiteOverlayProgress);
            int v = OverlayTexture.getV(livingEntity.hurtTime > 0 || livingEntity.deathTime > 0);
            int o = u | (v << 16);

            float yaw = livingState.bodyYaw;

            if (MorphRenderer.renderLivingEntity(livingEntity, yaw, tickDelta, matrices, vcp, light, o))
            {
                return;
            }
        }

        original.call(renderer, state, matrices, vcp, light);
    }
}