package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.utils.colors.Color;

import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.DefaultFramebufferSet;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.client.render.FramePass;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.memory.ObjectAllocator;
import net.minecraft.util.math.Vec3d;

import org.joml.Matrix4f;
import org.joml.Vector4f;

import com.mojang.blaze3d.buffers.GpuBufferSlice;

import org.lwjgl.opengl.GL11;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin
{
    @Shadow
    private DefaultFramebufferSet framebufferSet;

    @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true, require = 0)
    public void onRenderSky(FrameGraphBuilder frameGraphBuilder, Camera camera, GpuBufferSlice fogBuffer, CallbackInfo info)
    {
        if (BBSRendering.isChromaSkyEnabled())
        {
            FramePass pass = frameGraphBuilder.createPass("sky");

            this.framebufferSet.mainFramebuffer = pass.transfer(this.framebufferSet.mainFramebuffer);
            pass.setRenderer(() -> {
                Color color = Color.rgb(BBSRendering.getChromaSkyColor());

                GL11.glClearColor(color.r, color.g, color.b, 1F);
                GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            });

            info.cancel();
        }
    }

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true, require = 0)
    public void onRenderClouds(FrameGraphBuilder frameGraphBuilder, CloudRenderMode cloudRenderMode, Vec3d cameraPos, long tick, float tickDelta, int color, float cloudHeight, CallbackInfo info)
    {
        if (BBSRendering.isChromaSkyEnabled() && !BBSRendering.isChromaSkyClouds())
        {
            info.cancel();
        }
    }

    @Inject(method = "renderWeather", at = @At("HEAD"), cancellable = true, require = 0)
    public void onRenderWeather(FrameGraphBuilder frameGraphBuilder, GpuBufferSlice fogBuffer, CallbackInfo info)
    {
        if (BBSRendering.shouldHideChromaTerrain())
        {
            info.cancel();
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    public void onCaptureWorldMatrices(ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline,
        Camera camera, Matrix4f positionMatrix, Matrix4f basicProjectionMatrix, Matrix4f projectionMatrix,
        GpuBufferSlice fogBuffer, Vector4f fogColor, boolean renderSky, CallbackInfo info)
    {
        /* The frustum projection omits camera effects. Rendering must match the terrain projection. */
        BBSRendering.camera.set(positionMatrix);
        BBSRendering.projection.set(basicProjectionMatrix);
    }

    @Inject(at = @At("RETURN"), method = "loadEntityOutlinePostProcessor")
    private void onLoadEntityOutlineShader(CallbackInfo info)
    {
        BBSRendering.resizeExtraFramebuffers();
    }

    @Inject(at = @At("RETURN"), method = "onResized")
    private void onResized(int width, int height, CallbackInfo info)
    {
        BBSRendering.resizeExtraFramebuffers();
    }
}
