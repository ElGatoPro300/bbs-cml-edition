package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.bridge.IRenderLayerBridge;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.renderers.utils.ModelEffectPass;
import mchorse.bbs_mod.graphics.texture.AdoptedTexture;

import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.texture.GlTexture;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderLayer.class)
public class RenderLayerMixin implements IRenderLayerBridge
{
    @Shadow
    private RenderSetup renderSetup;

    @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
    public void onDraw(BuiltBuffer buffer, CallbackInfo info)
    {
        ModelEffectPass.bound(null);
        CustomVertexConsumerProvider.drawLayer((RenderLayer) (Object) this);

        if (ModelEffectPass.hasBinding())
        {
            RenderSetup.Texture texture = this.renderSetup.resolveTextures().get("Sampler0");

            if (texture != null && texture.textureView().texture() instanceof GlTexture glTexture
                && ModelEffectPass.drawBound(buffer, AdoptedTexture.identifier(glTexture.getGlId(), glTexture.getWidth(0), glTexture.getHeight(0), false)))
            {
                info.cancel();
            }
        }
    }

    @Override
    public int bbs$getTextureId()
    {
        if (this.renderSetup != null)
        {
            Map<String, RenderSetup.Texture> textures = this.renderSetup.resolveTextures();

            if (textures != null)
            {
                for (RenderSetup.Texture texture : textures.values())
                {
                    if (texture != null && texture.textureView() != null && texture.textureView().texture() instanceof GlTexture glTexture)
                    {
                        return glTexture.getGlId();
                    }
                }
            }
        }

        return 0;
    }
}
