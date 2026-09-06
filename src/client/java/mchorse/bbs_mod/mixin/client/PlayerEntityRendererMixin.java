package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.client.renderer.MorphRenderer;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.morphing.Morph;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntityRenderer.class)
public class PlayerEntityRendererMixin
{
    @Inject(method = "getPositionOffset", at = @At("HEAD"), cancellable = true)
    public void onPositionOffset(PlayerEntityRenderState state, CallbackInfoReturnable<Vec3d> info)
    {
        World world = MinecraftClient.getInstance().world;
        Entity entity = world != null ? world.getEntityById(state.id) : null;

        if (entity instanceof AbstractClientPlayerEntity abstractClientPlayerEntity)
        {
            if (abstractClientPlayerEntity.isSpectator())
            {
                return;
            }

            Morph morph = Morph.getMorph(abstractClientPlayerEntity);

            if (morph != null && morph.getForm() != null)
            {
                info.setReturnValue(Vec3d.ZERO);
            }
        }
    }

    @Inject(method = "renderArm", at = @At("HEAD"), cancellable = true)
    public void onRenderArmBegin(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, Identifier skin, ModelPart arm, boolean sleeve, CallbackInfo info)
    {
        AbstractClientPlayerEntity player = MinecraftClient.getInstance().player;

        if (player == null || player.isSpectator())
        {
            return;
        }
        Morph morph = Morph.getMorph(player);

        if (morph != null)
        {
            Form form = morph.getForm();

            if (form != null)
            {
                FormRenderer renderer = FormUtilsClient.getRenderer(form);
                Hand hand = ((PlayerEntityRenderer) (Object) this).getModel().rightArm == arm ? Hand.MAIN_HAND : Hand.OFF_HAND;

                if (renderer != null && renderer.renderArm(matrices, light, player, hand))
                {
                    info.cancel();
                }
            }
        }
    }
}