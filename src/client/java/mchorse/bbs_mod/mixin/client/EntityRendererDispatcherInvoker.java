package mchorse.bbs_mod.mixin.client;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.world.WorldView;

import org.joml.Quaternionf;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(EntityRenderDispatcher.class)
public interface EntityRendererDispatcherInvoker
{
    @Invoker("renderShadow")
    public static void bbs$renderShadow(MatrixStack matrices, VertexConsumerProvider vertexConsumers, EntityRenderState state, float opacity, float tickDelta, WorldView world, float radius)
    {
        throw new AssertionError();
    }

    @Invoker("renderFire")
    public void bbs$renderFire(MatrixStack matrices, VertexConsumerProvider vertexConsumers, EntityRenderState state, Quaternionf rotation);
}
