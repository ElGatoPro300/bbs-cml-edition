package mchorse.bbs_mod.cubic.render;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.render.vao.ModelVAORenderer;
import mchorse.bbs_mod.forms.forms.utils.TextureBlend;
import mchorse.bbs_mod.resources.Link;

import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;

import com.mojang.blaze3d.systems.RenderSystem;

import org.lwjgl.opengl.GL11;

/**
 * Per-bone texture crossfade matching the form-level {@link TextureBlend}
 * timeline: skin A fades out while skin B fades in (opacity crossfade at 0.5).
 */
public final class CubicGroupTextureBlend
{
    public Link from;
    public Link to;
    public float blend;

    private CubicGroupTextureBlend()
    {}

    public boolean isPartial()
    {
        return this.blend > 0F && this.blend < 1F;
    }

    public static CubicGroupTextureBlend resolve(ModelGroup group, Link defaultTexture)
    {
        if (group.textureBlendTo != null)
        {
            CubicGroupTextureBlend state = new CubicGroupTextureBlend();

            state.from = group.textureOverride != null ? group.textureOverride : defaultTexture;
            state.to = group.textureBlendTo;
            state.blend = group.textureBlend;

            return state;
        }

        if (group.textureOverride != null)
        {
            CubicGroupTextureBlend state = new CubicGroupTextureBlend();

            state.from = defaultTexture;
            state.to = group.textureOverride;
            state.blend = group.textureBlend;

            return state;
        }

        return null;
    }

    public static boolean supportsShader(ShaderProgram shader)
    {
        if (shader == null)
        {
            return false;
        }

        GlUniform uniform = shader.getUniform("TextureBlendActive");

        return uniform != null;
    }

    public static Link resolveDrawTexture(CubicGroupTextureBlend state, Link defaultTexture)
    {
        if (state == null)
        {
            return defaultTexture;
        }

        if (state.blend >= 1F)
        {
            return state.to;
        }

        if (state.blend <= 0F)
        {
            return state.from;
        }

        return state.from;
    }

    /**
     * Binds the active texture and, when supported, enables single-pass shader crossfade.
     */
    public static void bindForDraw(ShaderProgram shader, CubicGroupTextureBlend state, Link defaultTexture)
    {
        if (state == null)
        {
            ModelVAORenderer.clearTextureBlend();
            BBSModClient.getTextures().bindTexture(defaultTexture);

            return;
        }

        if (state.blend >= 1F)
        {
            ModelVAORenderer.clearTextureBlend();
            BBSModClient.getTextures().bindTexture(state.to);
        }
        else if (state.blend <= 0F)
        {
            ModelVAORenderer.clearTextureBlend();
            BBSModClient.getTextures().bindTexture(state.from);
        }
        else if (supportsShader(shader))
        {
            BBSModClient.getTextures().bindTexture(state.from);
            ModelVAORenderer.setTextureBlend(state.to, state.blend);
        }
        else
        {
            ModelVAORenderer.clearTextureBlend();
            BBSModClient.getTextures().bindTexture(state.from);
        }
    }

    /**
     * Two-pass opacity crossfade for vanilla / Iris entity shaders (no TextureBlend uniforms).
     */
    public static void drawTwoPass(Runnable fromPass, Runnable toPass, float blend)
    {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);

        fromPass.run();

        RenderSystem.depthMask(false);

        try
        {
            toPass.run();
        }
        finally
        {
            RenderSystem.depthMask(depthMask);
        }
    }
}
