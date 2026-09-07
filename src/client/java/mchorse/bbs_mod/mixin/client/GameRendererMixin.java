package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.controller.CameraController;
import mchorse.bbs_mod.camera.controller.ICameraController;
import mchorse.bbs_mod.camera.controller.PlayCameraController;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.cubic.render.vao.ModelVAORenderer;
import mchorse.bbs_mod.film.Films;
import mchorse.bbs_mod.items.GunZoom;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin
{
    private long bbs$lastFpBobbingTick = Long.MIN_VALUE;
    private float bbs$fpBobPhase;
    private float bbs$fpBobPrevPhase;
    private float bbs$fpBobStride;
    private float bbs$fpBobPrevStride;

    /**
     * This injection cancels bobbing when camera controller takes over
     */
    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    public void onBob(MatrixStack matrices, float tickDelta, CallbackInfo ci)
    {
        Films.FirstPersonBobbingSample sample = BBSModClient.getFilms().getFirstPersonBobbingSample(tickDelta);

        if (sample != null)
        {
            this.bbs$applyReplayFirstPersonBobbing(matrices, tickDelta, sample);
            ci.cancel();

            return;
        }

        this.bbs$resetReplayFirstPersonBobbing();

        if (BBSModClient.getCameraController().getCurrent() != null)
        {
            ci.cancel();
        }
    }

    private void bbs$applyReplayFirstPersonBobbing(MatrixStack matrices, float tickDelta, Films.FirstPersonBobbingSample sample)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.world == null)
        {
            return;
        }

        long worldTick = mc.world.getTime();

        if (this.bbs$lastFpBobbingTick != worldTick)
        {
            this.bbs$lastFpBobbingTick = worldTick;
            this.bbs$fpBobPrevPhase = this.bbs$fpBobPhase;
            this.bbs$fpBobPrevStride = this.bbs$fpBobStride;

            if (!sample.paused)
            {
                float movement = sample.grounded ? MathHelper.sqrt(sample.vX * sample.vX + sample.vZ * sample.vZ) * 4F : 0F;
                float frequency = BBSSettings.replayFpBobbingFrequency == null ? 1F : MathHelper.clamp(BBSSettings.replayFpBobbingFrequency.get(), 0F, 3F);

                movement = Math.min(1F, movement);
                this.bbs$fpBobStride += (movement - this.bbs$fpBobStride) * 0.4F;
                this.bbs$fpBobPhase += this.bbs$fpBobStride * frequency;
            }
        }

        float phase = MathHelper.lerp(tickDelta, this.bbs$fpBobPrevPhase, this.bbs$fpBobPhase);
        float intensity = BBSSettings.replayFpBobbingIntensity == null ? 1F : MathHelper.clamp(BBSSettings.replayFpBobbingIntensity.get(), 0F, 2F);
        float stride = MathHelper.lerp(tickDelta, this.bbs$fpBobPrevStride, this.bbs$fpBobStride) * intensity;

        matrices.translate(MathHelper.sin(phase * (float) Math.PI) * stride * 0.5F, -Math.abs(MathHelper.cos(phase * (float) Math.PI) * stride), 0F);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(MathHelper.sin(phase * (float) Math.PI) * stride * 3F));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(Math.abs(MathHelper.cos(phase * (float) Math.PI - 0.2F) * stride) * 5F));
    }

    private void bbs$resetReplayFirstPersonBobbing()
    {
        this.bbs$lastFpBobbingTick = Long.MIN_VALUE;
        this.bbs$fpBobPhase = 0F;
        this.bbs$fpBobPrevPhase = 0F;
        this.bbs$fpBobStride = 0F;
        this.bbs$fpBobPrevStride = 0F;
    }

    /**
     * This injection replaces the camera FOV when camera controller takes over
     */
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    public void onGetFov(CallbackInfoReturnable<Float> info)
    {
        GunZoom gunZoom = BBSModClient.getGunZoom();

        if (gunZoom != null)
        {
            info.setReturnValue(gunZoom.getFOV(info.getReturnValue()));

            return;
        }

        CameraController controller = BBSModClient.getCameraController();

        if (controller.getCurrent() != null && !BBSRendering.isIrisShadowPass())
        {
            info.setReturnValue((float) controller.getFOV());
        }
    }

    /**
     * Replaces vanilla camera roll with the active BBS film/editor roll, while still
     * applying vanilla hurt/death tilt from the camera entity. Cancelling the whole
     * method previously removed damage shake during first-person film playback whenever
     * a camera controller (e.g. film editor runner) was active.
     */
    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    public void onTiltViewWhenHurt(MatrixStack matrices, float tickDelta, CallbackInfo info)
    {
        CameraController controller = BBSModClient.getCameraController();

        if (controller.getCurrent() == null || BBSRendering.isIrisShadowPass())
        {
            return;
        }

        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(controller.getRoll()));

        MinecraftClient client = MinecraftClient.getInstance();

        if (client.getCameraEntity() instanceof LivingEntity livingEntity)
        {
            float f = livingEntity.hurtTime - tickDelta;

            if (livingEntity.isDead())
            {
                float deathTilt = Math.min(livingEntity.deathTime + tickDelta, 20.0F);

                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(40.0F - 8000.0F / (deathTilt + 200.0F)));
            }

            if (f >= 0.0F && livingEntity.maxHurtTime > 0)
            {
                f /= livingEntity.maxHurtTime;
                f = MathHelper.sin(f * f * f * f * (float) Math.PI);

                float tiltYaw = livingEntity.getDamageTiltYaw();
                float strength = (float) (-f * 14.0 * client.options.getDamageTiltStrength().getValue());

                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-tiltYaw));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(strength));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(tiltYaw));
            }
        }

        info.cancel();
    }

    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    public void onRenderHand(CallbackInfo info)
    {
        ICameraController current = BBSModClient.getCameraController().getCurrent();

        if (current instanceof PlayCameraController)
        {
            info.cancel();
        }
    }

    @Inject(at = @At("HEAD"), method = "renderWorld")
    private void onWorldRenderBegin(CallbackInfo callbackInfo)
    {
        BBSRendering.onWorldRenderBegin();
    }

    /**
     * Flush Iris-deferred paint overlays after the world has been composited but before
     * AAA Particles pastes a cleared depth buffer and draws Effekseer (same GETFIELD point
     * as AAA's {@code beforeRenderHand}, earlier {@code order} so we run first).
     */
    @Inject(
        method = "renderWorld",
        at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = "Lnet/minecraft/client/render/GameRenderer;renderHand:Z"),
        order = 900
    )
    private void bbsFlushPaintOverlaysBeforeHand(CallbackInfo callbackInfo)
    {
        if (!BBSRendering.isIrisShadersEnabled() || !ModelVAORenderer.hasQueuedPaintOverlays())
        {
            return;
        }

        ModelVAORenderer.flushPaintOverlayQueue();
    }

    @Inject(at = @At("RETURN"), method = "renderWorld")
    private void onWorldRenderEnd(CallbackInfo callbackInfo)
    {
        BBSRendering.onWorldRenderEnd();
    }

    @Inject(method = "render", at = @At(value = "FIELD", target = "Lnet/minecraft/client/option/GameOptions;hudHidden:Z", opcode = Opcodes.GETFIELD, ordinal = 0))
    private void onBeforeHudRendering(RenderTickCounter tickCounter, boolean tick, CallbackInfo info)
    {
        ICameraController current = BBSModClient.getCameraController().getCurrent();

        if (MinecraftClient.getInstance().options.hudHidden && current == null)
        {
            BBSRendering.onRenderBeforeScreen();
        }
    }
}
