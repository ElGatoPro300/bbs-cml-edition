package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.forms.FormUtilsClient;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * MobForm body/armor stay on the private Immediate. Builtin held meshes
 * (trident, shield, skulls) tessellate on the world entity Immediate.
 */
@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin
{
    @ModifyVariable(
        method = "renderItem",
        at = @At("HEAD"),
        argsOnly = true
    )
    private VertexConsumerProvider bbs$routeMobFormBuiltinItems(
        VertexConsumerProvider consumers,
        LivingEntity entity,
        ItemStack stack,
        ItemDisplayContext mode,
        boolean leftHanded,
        MatrixStack matrices,
        VertexConsumerProvider ignored,
        int light
    )
    {
        return FormUtilsClient.routeMobFormBuiltinItemConsumers(stack, mode, consumers);
    }
}
