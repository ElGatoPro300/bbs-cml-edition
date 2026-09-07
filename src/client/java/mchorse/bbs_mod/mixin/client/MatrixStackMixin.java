package mchorse.bbs_mod.mixin.client;

import net.minecraft.client.util.math.MatrixStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MatrixStack.class)
public class MatrixStackMixin
{
    @Inject(method = "pop", at = @At("HEAD"), cancellable = true)
    private void bbs$preventUnderflow(CallbackInfo info)
    {
        MatrixStack self = (MatrixStack) (Object) this;

        if (self.stack.size() <= 1)
        {
            info.cancel();
        }
    }

    @Inject(method = "peek", at = @At("HEAD"))
    private void bbs$ensureNotEmptyPeek(CallbackInfoReturnable<MatrixStack.Entry> info)
    {
        MatrixStack self = (MatrixStack) (Object) this;

        if (self.stack.isEmpty())
        {
            self.stack.add(new MatrixStack().peek());
        }
    }

    @Inject(method = "push", at = @At("HEAD"))
    private void bbs$ensureNotEmptyPush(CallbackInfo info)
    {
        MatrixStack self = (MatrixStack) (Object) this;

        if (self.stack.isEmpty())
        {
            self.stack.add(new MatrixStack().peek());
        }
    }
}
