package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.bridge.IEntityRenderState;
import mchorse.bbs_mod.client.renderer.MorphRenderer;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormUtilsClient;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderManager.class)
public class EntityRenderDispatcherMixin
{
    @WrapOperation(
        method = "render(Lnet/minecraft/client/render/entity/state/EntityRenderState;Lnet/minecraft/client/render/state/CameraRenderState;DDDLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/entity/EntityRenderer;render(Lnet/minecraft/client/render/entity/state/EntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V"
        ),
        require = 0
    )
    private void wrapRender(
        EntityRenderer renderer, EntityRenderState state,
        MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState,
        Operation<Void> original
    )
    {
        Entity entity = ((IEntityRenderState) state).bbs$getEntity();

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
            CustomVertexConsumerProvider vcp = FormUtilsClient.getProvider();
            int light = livingState.light;
            float tickDelta = 0F;

            if (MorphRenderer.renderLivingEntity(livingEntity, yaw, tickDelta, matrices, vcp, light, o))
            {
                return;
            }
        }

        original.call(renderer, state, matrices, queue, cameraState);
    }
}