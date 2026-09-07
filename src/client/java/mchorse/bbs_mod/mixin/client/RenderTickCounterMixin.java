package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.utils.VideoRecorder;

import net.minecraft.client.render.RenderTickCounter;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderTickCounter.Dynamic.class)
public class RenderTickCounterMixin
{
    @Shadow
    private float tickProgress;

    @Shadow
    private float dynamicDeltaTicks;

    @Shadow
    private long lastTimeMillis;

    private int heldFrames;

    @Inject(method = "beginRenderTick", at = @At("HEAD"), cancellable = true)
    public void onBeginRenderTick(long timeMillis, boolean tick, CallbackInfoReturnable<Integer> info)
    {
        VideoRecorder videoRecorder = BBSModClient.getVideoRecorder();

        if (videoRecorder.isRecording())
        {
            if (videoRecorder.getCounter() == 0)
            {
                this.tickProgress = 0F;
            }

            if (this.heldFrames == 0)
            {
                this.dynamicDeltaTicks = 20F / (float) BBSRendering.getVideoFrameRate();
                this.lastTimeMillis = timeMillis;
                this.tickProgress += this.dynamicDeltaTicks;

                int i = (int) this.tickProgress;

                this.tickProgress -= (float) i;

                videoRecorder.serverTicks += i;
                BBSRendering.canRender = true;

                info.setReturnValue(i);
            }
            else
            {
                BBSRendering.canRender = false;

                info.setReturnValue(0);
            }

            this.heldFrames += 1;

            if (this.heldFrames >= BBSSettings.videoSettings.heldFrames.get())
            {
                this.heldFrames = 0;
            }
        }
        else
        {
            this.heldFrames = 0;
        }
    }
}