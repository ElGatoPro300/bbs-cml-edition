package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.utils.VideoRecorder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.server.MinecraftServer;

import java.util.concurrent.TimeUnit;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderTickCounter.Dynamic.class)
public class RenderTickCounterMixin
{
    @Shadow
    public float tickDelta;

    @Shadow
    public float lastFrameDuration;

    @Shadow
    private long prevTimeMillis;

    private int heldFrames;

    @Inject(method = "beginRenderTick", at = @At("HEAD"), cancellable = true)
    public void onBeginRenderTick(long timeMillis, boolean tick, CallbackInfoReturnable<Integer> info)
    {
        VideoRecorder videoRecorder = BBSModClient.getVideoRecorder();

        if (videoRecorder.isRecording())
        {
            int heldTarget = videoRecorder.getEffectiveHeldFrames();

            if (videoRecorder.getCounter() == 0)
            {
                this.tickDelta = 0;
            }

            /* HQ settle: hold film clock, freeze actions, let client/server process chunks. */
            if (videoRecorder.isSettling())
            {
                boolean captureNow = videoRecorder.tickSettle();

                BBSRendering.canRender = captureNow;
                this.prevTimeMillis = timeMillis;

                if (captureNow)
                {
                    /* Capture the settled frame without advancing film/actions further. */
                    info.setReturnValue(0);
                }
                else
                {
                    int catchUp = videoRecorder.getSettleCatchUpTicks();

                    videoRecorder.serverTicks += catchUp;
                    info.setReturnValue(catchUp);
                }

                return;
            }

            if (this.heldFrames == 0)
            {
                this.lastFrameDuration = 20F / (float) BBSRendering.getVideoFrameRate();
                this.prevTimeMillis = timeMillis;
                this.tickDelta += this.lastFrameDuration;

                int i = (int) this.tickDelta;

                this.tickDelta -= (float) i;

                videoRecorder.serverTicks += i;

                boolean canCapture = true;

                if (videoRecorder.isHighQualityRender())
                {
                    boolean fired = syncExportActionsNow(videoRecorder, true);

                    if (fired)
                    {
                        videoRecorder.beginSettle();
                        canCapture = false;
                    }
                }

                BBSRendering.canRender = canCapture;

                info.setReturnValue(i);
            }
            else
            {
                BBSRendering.canRender = false;

                info.setReturnValue(0);
            }

            this.heldFrames += 1;

            if (this.heldFrames >= heldTarget)
            {
                this.heldFrames = 0;
            }
        }
        else
        {
            this.heldFrames = 0;
        }
    }

    private static boolean syncExportActionsNow(VideoRecorder recorder, boolean highQuality)
    {
        MinecraftClient client = MinecraftClient.getInstance();
        MinecraftServer server = client.getServer();

        if (server == null)
        {
            return false;
        }

        float filmTime = recorder.getFilmTime();
        long timeout = highQuality ? 10000L : 100L;
        boolean[] fired = new boolean[]{false};

        try
        {
            server.submit(() ->
            {
                fired[0] = BBSMod.getActions().syncActionsTo(filmTime);
            }).get(timeout, TimeUnit.MILLISECONDS);
        }
        catch (Exception e)
        {
            /* Next frame retries. */
        }

        return fired[0];
    }
}