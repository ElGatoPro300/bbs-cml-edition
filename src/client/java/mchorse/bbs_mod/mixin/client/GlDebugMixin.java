package mchorse.bbs_mod.mixin.client;

import net.minecraft.client.gl.GlDebug;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlDebug.class)
public class GlDebugMixin
{
    @Inject(method = "onDebugMessage", at = @At("HEAD"), cancellable = true)
    private void onDebugMessage(int source, int type, int id, int severity, int length, long message, long l, CallbackInfo ci)
    {
        /* Suppress repetitive OpenGL driver error 1282 (GL_INVALID_OPERATION <location> is invalid)
         * generated when Minecraft's GlCommandEncoder uploads sampler uniform locations
         * that do not exist in active Iris shaderpack programs. */
        if (id == 1282)
        {
            ci.cancel();
        }
    }
}
