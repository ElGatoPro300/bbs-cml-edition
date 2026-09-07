package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.SunPathRotation;
import mchorse.bbs_mod.utils.colors.Color;

import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;

import org.lwjgl.opengl.GL11;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin
{
    @Shadow
    public Framebuffer entityOutlinesFramebuffer;

    /**
     * Feed sky a yaw-rotated <em>copy</em> of model-view. Mutating the live matrix (old
     * begin/end) leaked sun-path yaw into world rendering under Sodium when sun rotation ≠ 0.
     * <p>
     * Handler takes only the modified arg — do not list the rest of the method params
     * (Mixin expects {@code (Matrix4f value, …fullArgs)} which double-counts arg0).
     */
    @ModifyVariable(
        method = "renderSky(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FLnet/minecraft/client/render/Camera;ZLjava/lang/Runnable;)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0,
        require = 0
    )
    private Matrix4f bbs$sunPathSkyModelViewCopy(Matrix4f modelView)
    {
        return SunPathRotation.copyWithSkyYaw(modelView);
    }

    @Inject(method = "renderSky(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FLnet/minecraft/client/render/Camera;ZLjava/lang/Runnable;)V", at = @At("HEAD"), cancellable = true, require = 0)
    public void onRenderSky(Matrix4f modelView, Matrix4f projectionMatrix, float tickDelta, Camera camera, boolean thickFog, Runnable fogCallback, CallbackInfo info)
    {
        if (BBSRendering.isChromaSkyEnabled())
        {
            Color color = Color.rgb(BBSRendering.getChromaSkyColor());

            GL11.glClearColor(color.r, color.g, color.b, 1F);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            RenderSystem.setShaderFogColor(color.r, color.g, color.b, 1F);

            info.cancel();
        }
    }

    @Inject(method = "renderLayer", at = @At("HEAD"), cancellable = true)
    public void onRenderLayer(RenderLayer renderLayer, double cameraX, double cameraY, double cameraZ, Matrix4f positionMatrix, Matrix4f projectionMatrix, CallbackInfo info)
    {
        /* Video form uploads must never leave UNPACK_* dirty before terrain draws. */
        BBSRendering.restoreWorldRenderState();

        if (BBSRendering.shouldHideChromaTerrain())
        {
            BBSRendering.onRenderChunkLayer(positionMatrix, projectionMatrix);

            info.cancel();
        }
    }

    @Inject(method = "renderLayer", at = @At("TAIL"))
    public void onRenderChunkLayer(RenderLayer layer, double cameraX, double cameraY, double cameraZ, Matrix4f positionMatrix, Matrix4f projectionMatrix, CallbackInfo info)
    {
        if (layer == RenderLayer.getSolid())
        {
            BBSRendering.onRenderChunkLayer(positionMatrix, projectionMatrix);
        }
    }

    @Inject(method = "setupFrustum", at = @At("HEAD"))
    public void onSetupFrustum(Vec3d vec3d, Matrix4f matrix4f, Matrix4f positionMatrix, CallbackInfo info)
    {
        BBSRendering.camera.set(matrix4f);
    }

    @Inject(at = @At("RETURN"), method = "loadEntityOutlinePostProcessor")
    private void onLoadEntityOutlineShader(CallbackInfo info)
    {
        BBSRendering.resizeExtraFramebuffers();
    }

    @Inject(at = @At("RETURN"), method = "onResized")
    private void onResized(CallbackInfo info)
    {
        if (this.entityOutlinesFramebuffer == null)
        {
            return;
        }

        BBSRendering.resizeExtraFramebuffers();
    }
}
