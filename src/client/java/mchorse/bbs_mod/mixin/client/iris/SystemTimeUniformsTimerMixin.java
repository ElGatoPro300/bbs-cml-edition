package mchorse.bbs_mod.mixin.client.iris;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.utils.VideoRecorder;

import net.irisshaders.iris.uniforms.SystemTimeUniforms;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SystemTimeUniforms.Timer.class)
public class SystemTimeUniformsTimerMixin
{
    @Shadow(remap = false)
    private float frameTimeCounter;

    @Shadow(remap = false)
    private float lastFrameTime;

    private int heldFrames;

    @Inject(method = "beginFrame", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    public void onBeginFrame(CallbackInfo info)
    {
        VideoRecorder videoRecorder = BBSModClient.getVideoRecorder();

        if (videoRecorder.isRecording())
        {
            float videoFrameRate = BBSRendering.getVideoFrameRate();

            if (this.heldFrames == 0)
            {
                this.lastFrameTime = 20F / videoFrameRate;
                this.frameTimeCounter += 1F / videoFrameRate;
            }

            this.heldFrames += 1;

            if (this.heldFrames >= BBSSettings.videoSettings.heldFrames.get())
            {
                this.heldFrames = 0;
            }

            info.cancel();
        }
        else
        {
            this.heldFrames = 0;
        }
    }
}