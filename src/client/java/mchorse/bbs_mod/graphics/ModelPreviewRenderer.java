package mchorse.bbs_mod.graphics;

import mchorse.bbs_mod.client.BBSRendering;

import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.texture.GlTexture;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;

import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;

/**
 * Owns an off-screen preview target and scopes the uniforms used by its render passes.
 */
public class ModelPreviewRenderer implements AutoCloseable
{
    private SimpleFramebuffer framebuffer;
    private GpuBuffer projection;
    private GpuBuffer fog;
    private GpuTextureView previousColor;
    private GpuTextureView previousDepth;
    private GpuBufferSlice previousProjection;
    private GpuBufferSlice previousFog;
    private GpuBufferSlice previousLights;
    private final Matrix4f previousBbsProjection = new Matrix4f();
    private ProjectionType previousProjectionType;
    private boolean active;
    private int guiFramebuffer;

    public void beginGui(int width, int height, int guiWidth, int guiHeight)
    {
        this.begin(width, height, new Matrix4f().setOrtho(0F, guiWidth, guiHeight, 0F, -3000F, 3000F));
        RenderSystem.setProjectionMatrix(this.projection.slice(), ProjectionType.ORTHOGRAPHIC);

        if (this.guiFramebuffer == 0)
        {
            this.guiFramebuffer = GL30.glGenFramebuffers();
        }

        /* Legacy forms still issue direct GL draws. Bind the same attachments that
         * RenderLayer sees through output overrides, including after target resize. */
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.guiFramebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL30.GL_TEXTURE_2D,
            ((GlTexture) this.framebuffer.getColorAttachment()).getGlId(), 0);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_TEXTURE_2D,
            ((GlTexture) this.framebuffer.getDepthAttachment()).getGlId(), 0);
        GL30.glViewport(0, 0, width, height);
    }

    public void begin(int width, int height, Matrix4fc projectionMatrix)
    {
        RenderSystem.assertOnRenderThread();

        if (this.active)
        {
            throw new IllegalStateException("Model preview is already active");
        }

        if (width <= 0 || height <= 0)
        {
            throw new IllegalArgumentException("Model preview dimensions must be positive");
        }

        this.ensureTarget(width, height);
        this.updateProjection(projectionMatrix);
        this.ensureFog();

        this.previousBbsProjection.set(BBSRendering.projection);
        BBSRendering.projection.set(projectionMatrix);

        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
            this.framebuffer.getColorAttachment(), 0, this.framebuffer.getDepthAttachment(), 1D);

        this.previousColor = RenderSystem.outputColorTextureOverride;
        this.previousDepth = RenderSystem.outputDepthTextureOverride;
        this.previousProjection = RenderSystem.getProjectionMatrixBuffer();
        this.previousProjectionType = RenderSystem.getProjectionType();
        this.previousFog = RenderSystem.getShaderFog();
        this.previousLights = RenderSystem.getShaderLights();

        RenderSystem.getModelViewStack().pushMatrix();
        this.active = true;

        try
        {
            RenderSystem.getModelViewStack().identity();
            RenderSystem.outputColorTextureOverride = this.framebuffer.getColorAttachmentView();
            RenderSystem.outputDepthTextureOverride = this.framebuffer.getDepthAttachmentView();
            RenderSystem.setProjectionMatrix(this.projection.slice(), ProjectionType.PERSPECTIVE);
            RenderSystem.setShaderFog(this.fog.slice());
        }
        catch (RuntimeException | Error e)
        {
            this.end();

            throw e;
        }
    }

    private void ensureTarget(int width, int height)
    {
        if (this.framebuffer == null)
        {
            this.framebuffer = new SimpleFramebuffer("BBS model preview", width, height, true);
        }
        else if (this.framebuffer.textureWidth != width || this.framebuffer.textureHeight != height)
        {
            this.framebuffer.resize(width, height);
        }
    }

    private void updateProjection(Matrix4fc projectionMatrix)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            ByteBuffer data = Std140Builder.onStack(stack, RenderSystem.PROJECTION_MATRIX_UBO_SIZE)
                .putMat4f(projectionMatrix).get();

            if (this.projection == null)
            {
                this.projection = RenderSystem.getDevice().createBuffer(() -> "BBS preview projection",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, data);
            }
            else
            {
                RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.projection.slice(), data);
            }
        }
    }

    private void ensureFog()
    {
        if (this.fog != null)
        {
            return;
        }

        try (MemoryStack stack = MemoryStack.stackPush())
        {
            ByteBuffer data = Std140Builder.intoBuffer(stack.calloc((FogRenderer.FOG_UBO_SIZE + 15) & ~15))
                .putVec4(0F, 0F, 0F, 0F)
                .putFloat(Float.MAX_VALUE).putFloat(Float.MAX_VALUE)
                .putFloat(Float.MAX_VALUE).putFloat(Float.MAX_VALUE)
                .putFloat(Float.MAX_VALUE).putFloat(Float.MAX_VALUE)
                .align(16).get();

            this.fog = RenderSystem.getDevice().createBuffer(() -> "BBS preview fog", GpuBuffer.USAGE_UNIFORM, data);
        }
    }

    public GpuTextureView getColorView()
    {
        return this.framebuffer == null ? null : this.framebuffer.getColorAttachmentView();
    }

    public void end()
    {
        if (!this.active)
        {
            return;
        }

        RenderSystem.outputColorTextureOverride = this.previousColor;
        RenderSystem.outputDepthTextureOverride = this.previousDepth;
        RenderSystem.setProjectionMatrix(this.previousProjection, this.previousProjectionType);
        RenderSystem.setShaderFog(this.previousFog);
        RenderSystem.setShaderLights(this.previousLights);
        RenderSystem.getModelViewStack().popMatrix();

        this.previousColor = null;
        this.previousDepth = null;
        this.previousProjection = null;
        this.previousFog = null;
        this.previousLights = null;
        this.active = false;

        BBSRendering.projection.set(this.previousBbsProjection);
    }

    @Override
    public void close()
    {
        this.end();

        if (this.guiFramebuffer != 0)
        {
            GL30.glDeleteFramebuffers(this.guiFramebuffer);
            this.guiFramebuffer = 0;
        }

        if (this.framebuffer != null)
        {
            this.framebuffer.delete();
            this.framebuffer = null;
        }

        if (this.projection != null)
        {
            this.projection.close();
            this.projection = null;
        }

        if (this.fog != null)
        {
            this.fog.close();
            this.fog = null;
        }
    }
}
