package mchorse.bbs_mod.mixin.client.sodium;

import mchorse.bbs_mod.forms.renderers.utils.BlockPaintOverlayVertexConsumer;
import mchorse.bbs_mod.forms.renderers.utils.FormColorEffects;
import mchorse.bbs_mod.forms.renderers.utils.GlowEmissionVertexConsumer;
import mchorse.bbs_mod.forms.renderers.utils.RecolorVertexConsumer;
import mchorse.bbs_mod.utils.colors.Colors;

import net.caffeinemc.mods.sodium.api.vertex.attributes.common.ColorAttribute;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.api.vertex.attributes.common.ColorAttribute")
public class ColorAttributeMixin
{
    @ModifyVariable(method = "set", at = @At("HEAD"), ordinal = 0, remap = false)
    private static int onSet(int color)
    {
        if (GlowEmissionVertexConsumer.emissionColor != null)
        {
            Colors.COLOR.set(Colors.fromAbgr(color));
            Colors.COLOR.mul(GlowEmissionVertexConsumer.emissionColor);

            return Colors.toAbgr(Colors.COLOR.getARGBColor());
        }

        if (BlockPaintOverlayVertexConsumer.paintOverlayColor != null)
        {
            Colors.COLOR.set(Colors.fromAbgr(color));
            float outA = Colors.COLOR.a * BlockPaintOverlayVertexConsumer.paintOverlayColor.a;

            Colors.COLOR.set(BlockPaintOverlayVertexConsumer.paintOverlayColor.r, BlockPaintOverlayVertexConsumer.paintOverlayColor.g, BlockPaintOverlayVertexConsumer.paintOverlayColor.b, outA);

            return Colors.toAbgr(Colors.COLOR.getARGBColor());
        }

        if (RecolorVertexConsumer.newColor != null)
        {
            Colors.COLOR.set(Colors.fromAbgr(color));
            Colors.COLOR.mul(RecolorVertexConsumer.newColor);

            if (RecolorVertexConsumer.newPaintColor != null && RecolorVertexConsumer.newPaintColor.a != 0F)
            {
                FormColorEffects.applyPaintBlend(Colors.COLOR, RecolorVertexConsumer.newPaintColor, RecolorVertexConsumer.newPaintColor.a);
            }

            return Colors.toAbgr(Colors.COLOR.getARGBColor());
        }

        return color;
    }
}