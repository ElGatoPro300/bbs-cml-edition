package mchorse.bbs_mod.mixin.client.iris;

import mchorse.bbs_mod.utils.iris.FormColorGradePatch;
import mchorse.bbs_mod.utils.iris.QueueMap;
import mchorse.bbs_mod.utils.iris.ShaderCurves;
import mchorse.bbs_mod.utils.iris.ShaderOpacityPatch;

import net.irisshaders.iris.Iris;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Iris.class)
public class IrisMixin
{
    @Inject(method = "getShaderPackOptionQueue", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void onGetShaderPackOptionQueue(CallbackInfoReturnable<Map<String, String>> info)
    {
        Map<String, String> returnValue = info.getReturnValue() == null ? null : new QueueMap<>(info.getReturnValue());

        info.setReturnValue(returnValue);
    }

    @Inject(method = "loadExternalShaderpack", at = @At("HEAD"), remap = false, require = 0)
    private static void onLoadExternalShaderpack(String name, CallbackInfoReturnable<Boolean> info)
    {
        ShaderCurves.reset();
        FormColorGradePatch.resetPackState();
        ShaderOpacityPatch.setLoadingPackName(name);
        ShaderCurves.ensureSunPathRotationVariable();
    }

    @Inject(method = "setShadersDisabled", at = @At("HEAD"), remap = false, require = 0)
    private static void onSetShadersDisabled(CallbackInfo info)
    {
        ShaderCurves.reset();
        FormColorGradePatch.resetPackState();
        ShaderOpacityPatch.clearLoadingPackName();
    }

    @Inject(method = "loadExternalShaderpack", at = @At(value = "RETURN", ordinal = 9), remap = false, require = 0)
    private static void onTrueReturnLoadExternalShaderPack(CallbackInfoReturnable<Boolean> info)
    {
        ShaderCurves.finishLoading();
    }
}
