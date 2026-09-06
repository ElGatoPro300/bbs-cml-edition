package mchorse.bbs_mod.ui.utils;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.Framebuffer;
import mchorse.bbs_mod.graphics.Renderbuffer;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.Pair;

import net.minecraft.client.texture.GlTexture;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * The off-screen colour target the picker shaders render the per-form/per-bone index colours into.
 */
public class StencilFormFramebuffer
{
    private Framebuffer framebuffer;

    private int index;
    private Map<Integer, Pair<Form, String>> indexMap = new HashMap<>();

    private GpuTexture colorTexture;
    private GpuTextureView colorView;
    private GpuTexture depthTexture;
    private GpuTextureView depthView;
    private int gpuWidth = -1;
    private int gpuHeight = -1;

    private int readFbo = -1;
    private int drawFbo = -1;
    private int previousDrawFbo = -1;
    private GpuTextureView previousColorView;
    private GpuTextureView previousDepthView;
    private boolean applied;

    public Framebuffer getFramebuffer()
    {
        return this.framebuffer;
    }

    public GpuTextureView getColorView()
    {
        return this.colorView;
    }

    public int getIndex()
    {
        return this.index;
    }

    public Map<Integer, Pair<Form, String>> getIndexMap()
    {
        return this.indexMap;
    }

    public Pair<Form, String> getPicked()
    {
        return this.indexMap.get(this.index);
    }

    public void setup(Link id)
    {
        if (this.framebuffer != null)
        {
            return;
        }

        this.framebuffer = BBSModClient.getFramebuffers().getFramebuffer(id, (framebuffer) ->
        {
            Texture texture = new Texture();

            texture.setSize(2, 2);
            texture.setFilter(GL11.GL_NEAREST);
            texture.setWrap(GL13.GL_CLAMP_TO_EDGE);

            Renderbuffer renderbuffer = new Renderbuffer();

            renderbuffer.resize(2, 2);

            framebuffer.deleteTextures().attach(texture, GL30.GL_COLOR_ATTACHMENT0);
            framebuffer.attach(renderbuffer);
            framebuffer.unbind();
        });
    }

    public void resizeGUI(int w, int h)
    {
        this.resize(w, h, BBSModClient.getGUIScale());
    }

    public void resize(int w, int h, int scale)
    {
        this.resize(w * scale, h * scale);
    }

    public void resize(int w, int h)
    {
        if (w <= 0 || h <= 0)
        {
            return;
        }

        if (this.framebuffer != null)
        {
            this.framebuffer.resize(w, h);
        }
    }

    private void ensureGpuTargets()
    {
        Texture texture = this.framebuffer.getMainTexture();
        int w = Math.max(1, texture.width);
        int h = Math.max(1, texture.height);

        if (this.colorView != null && this.gpuWidth == w && this.gpuHeight == h)
        {
            return;
        }

        this.releaseGpuTargets();

        this.colorTexture = RenderSystem.getDevice().createTexture("bbs_stencil_color",
            GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_SRC,
            TextureFormat.RGBA8, w, h, 1, 1);
        this.colorView = RenderSystem.getDevice().createTextureView(this.colorTexture);

        this.depthTexture = RenderSystem.getDevice().createTexture("bbs_stencil_depth",
            GpuTexture.USAGE_RENDER_ATTACHMENT, TextureFormat.DEPTH32, w, h, 1, 1);
        this.depthView = RenderSystem.getDevice().createTextureView(this.depthTexture);

        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        this.drawFbo = GL30.glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.drawFbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D,
            ((GlTexture) this.colorTexture).getGlId(), 0);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D,
            ((GlTexture) this.depthTexture).getGlId(), 0);
        GL30.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL30.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);

        this.gpuWidth = w;
        this.gpuHeight = h;
    }

    public void apply()
    {
        this.ensureGpuTargets();

        this.previousDrawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(this.colorTexture, 0, this.depthTexture, 1D);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, this.drawFbo);

        if (!this.applied)
        {
            this.previousColorView = RenderSystem.outputColorTextureOverride;
            this.previousDepthView = RenderSystem.outputDepthTextureOverride;
            this.applied = true;
        }

        RenderSystem.outputColorTextureOverride = this.colorView;
        RenderSystem.outputDepthTextureOverride = this.depthView;
    }

    public void pickGUI(UIContext context, Area area)
    {
        this.pickGUI(context, area, BBSModClient.getGUIScale(), BBSModClient.getGUIScale());
    }

    public void pickGUI(UIContext context, Area area, float scaleX, float scaleY)
    {
        int localX = context.mouseX - context.globalX(area.x);
        int localY = context.mouseY - context.globalY(area.y);

        if (localX < 0 || localY < 0 || localX >= area.w || localY >= area.h)
        {
            this.index = 0;

            return;
        }

        this.pick((int) (localX * scaleX), this.gpuHeight - 1 - (int) (localY * scaleY));
    }

    public void pickGUI(int x, int y)
    {
        this.pick(x * BBSModClient.getGUIScale(), y * BBSModClient.getGUIScale());
    }

    public void pick(int x, int y)
    {
        if (this.colorTexture == null || x < 0 || y < 0 || x >= this.gpuWidth || y >= this.gpuHeight)
        {
            this.index = 0;

            return;
        }

        if (this.readFbo < 0)
        {
            this.readFbo = GL30.glGenFramebuffers();
        }

        int glId = ((GlTexture) this.colorTexture).getGlId();
        int previousReadFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);

        try (MemoryStack stack = MemoryStack.stackPush())
        {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, this.readFbo);
            GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, glId, 0);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);

            ByteBuffer pixel = stack.malloc(4);

            GL11.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);

            int r = Byte.toUnsignedInt(pixel.get(0));
            int g = Byte.toUnsignedInt(pixel.get(1));
            int b = Byte.toUnsignedInt(pixel.get(2));
            int a = Byte.toUnsignedInt(pixel.get(3));

            this.index = a == 0 ? 0 : r | (g << 8) | (b << 16);
        }
        finally
        {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFbo);
        }
    }

    public void unbind(StencilMap map)
    {
        this.unbind();

        this.indexMap.clear();
        this.indexMap.putAll(map.indexMap);
    }

    public void unbind()
    {
        if (this.applied)
        {
            RenderSystem.outputColorTextureOverride = this.previousColorView;
            RenderSystem.outputDepthTextureOverride = this.previousDepthView;
            this.previousColorView = null;
            this.previousDepthView = null;
            this.applied = false;
        }

        if (this.previousDrawFbo >= 0)
        {
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, this.previousDrawFbo);
            this.previousDrawFbo = -1;
        }
    }

    public void clearPicking()
    {
        this.index = 0;
        this.indexMap.clear();
    }

    public boolean hasPicked()
    {
        return this.index > 0;
    }


    private void releaseGpuTargets()
    {
        if (this.colorView != null)
        {
            this.colorView.close();
            this.colorView = null;
        }

        if (this.colorTexture != null)
        {
            this.colorTexture.close();
            this.colorTexture = null;
        }

        if (this.depthView != null)
        {
            this.depthView.close();
            this.depthView = null;
        }

        if (this.depthTexture != null)
        {
            this.depthTexture.close();
            this.depthTexture = null;
        }

        if (this.drawFbo >= 0)
        {
            GL30.glDeleteFramebuffers(this.drawFbo);
            this.drawFbo = -1;
        }
    }
}
