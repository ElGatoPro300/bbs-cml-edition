package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;

import net.minecraft.client.Keyboard;
import net.minecraft.client.input.KeyInput;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class KeyboardMixin
{
    @Inject(method = "onKey", at = @At("HEAD"))
    public void onOnKey(long window, int action, KeyInput input, CallbackInfo info)
    {
        BBSRendering.lastAction = action;
    }

    @Inject(method = "onKey", at = @At("TAIL"))
    public void onOnEndKey(long window, int action, KeyInput input, CallbackInfo info)
    {
        BBSModClient.onEndKey(window, input.key(), input.scancode(), action, input.modifiers(), info);
    }
}