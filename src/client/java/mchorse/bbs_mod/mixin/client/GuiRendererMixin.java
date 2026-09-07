package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.client.render.special.BbsFormGuiElementRenderer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.SpecialGuiElementRenderer;
import net.minecraft.client.render.VertexConsumerProvider;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(GuiRenderer.class)
public class GuiRendererMixin
{
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
    private static List<SpecialGuiElementRenderer<?>> bbs$addBbsRenderers(List<SpecialGuiElementRenderer<?>> original)
    {
        VertexConsumerProvider.Immediate immediate =
            MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();

        List<SpecialGuiElementRenderer<?>> list = new ArrayList<>(original);

        list.add(new BbsFormGuiElementRenderer(immediate));

        return list;
    }
}
