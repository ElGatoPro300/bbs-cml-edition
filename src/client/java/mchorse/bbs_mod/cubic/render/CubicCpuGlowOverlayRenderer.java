package mchorse.bbs_mod.cubic.render;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.data.model.ModelVertex;
import mchorse.bbs_mod.cubic.render.vao.ModelVAORenderer;
import mchorse.bbs_mod.obj.shapes.ShapeKeys;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;

import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;

import org.joml.Vector3f;

/**
 * Additive glow overlay for shape-key CPU meshes. Uses the same block/item overlay formula:
 * emissive vertex color, full bright lightmap, and SRC_ALPHA / ONE blending.
 */
public class CubicCpuGlowOverlayRenderer extends CubicCubeRenderer
{
    private final ShaderProgram shader;
    private final Link defaultTexture;
    private final Color glowLayerColor;
    private final boolean boneGlowOnly;
    private final float overlayIntensity;
    private final String targetGroupId;
    private final boolean skipBoneGlowGroups;

    public CubicCpuGlowOverlayRenderer(int light, int overlay, StencilMap stencilMap, ShapeKeys shapeKeys, ShaderProgram shader, Link defaultTexture, Color glowLayerColor, boolean boneGlowOnly, float overlayIntensity, String targetGroupId, boolean skipBoneGlowGroups)
    {
        super(light, overlay, stencilMap, shapeKeys);

        this.shader = shader;
        this.defaultTexture = defaultTexture;
        this.glowLayerColor = glowLayerColor;
        this.boneGlowOnly = boneGlowOnly;
        this.overlayIntensity = overlayIntensity;
        this.targetGroupId = targetGroupId;
        this.skipBoneGlowGroups = skipBoneGlowGroups;
    }

    @Override
    public boolean renderGroup(BufferBuilder builder, MatrixStack stack, ModelGroup group, Model model)
    {
        if (group.cubes.isEmpty() && group.meshes.isEmpty())
        {
            return false;
        }

        if (this.targetGroupId != null && !this.targetGroupId.equals(group.id))
        {
            return false;
        }

        if (this.skipBoneGlowGroups && group.glowIntensity > 0F)
        {
            return false;
        }

        if (this.boneGlowOnly)
        {
            if (group.glowIntensity <= 0F)
            {
                return false;
            }
        }
        else if (group.glowIntensity < 0F)
        {
            return false;
        }

        CubicGroupTextureBlend textureBlend = CubicGroupTextureBlend.resolve(group, this.defaultTexture);

        if (textureBlend != null && textureBlend.isPartial() && !CubicGroupTextureBlend.supportsShader(this.shader))
        {
            float fromA = this.a * (1F - textureBlend.blend);
            float toA = this.a * textureBlend.blend;

            CubicGroupTextureBlend.drawTwoPass(
                () -> this.drawGroup(stack, group, model, textureBlend.from, fromA),
                () -> this.drawGroup(stack, group, model, textureBlend.to, toA),
                textureBlend.blend
            );
        }
        else
        {
            CubicGroupTextureBlend.bindForDraw(this.shader, textureBlend, this.defaultTexture);

            try
            {
                this.drawGroup(stack, group, model, CubicGroupTextureBlend.resolveDrawTexture(textureBlend, this.defaultTexture), this.a);
            }
            finally
            {
                /* Glow overlay does not use BBS paint/blend uniforms. */
            }
        }

        return false;
    }

    private void drawGroup(MatrixStack stack, ModelGroup group, Model model, Link texture, float alpha)
    {
        if (texture != null)
        {
            BBSModClient.getTextures().bindTexture(texture);
        }

        this.setColor(1F, 1F, 1F, alpha);

        BufferBuilder groupBuilder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);

        super.renderGroup(groupBuilder, stack, group, model);

        try
        {
            ModelVAORenderer.setupUniformsCpuPretransformed(this.shader);

            GlUniform glowing = this.shader.getUniform("GlowingColor");

            if (glowing != null)
            {
                glowing.set(0F, 0F, 0F, 0F);
            }

            GlUniform paint = this.shader.getUniform("PaintColor");

            if (paint != null)
            {
                paint.set(0F, 0F, 0F, 0F);
            }

            this.shader.bind();
            BufferRenderer.drawWithGlobalProgram(groupBuilder.end());
            this.shader.unbind();
        }
        catch (IllegalStateException e)
        {
            /* Empty or invalid buffer */
        }
    }

    @Override
    protected void writeVertex(BufferBuilder builder, MatrixStack stack, ModelGroup group, ModelVertex vertex, Vector3f normal)
    {
        float gr;
        float gg;
        float gb;
        float ga;

        if (!this.boneGlowOnly && group.glowIntensity <= 0F)
        {
            gr = this.glowLayerColor.r * group.color.r;
            gg = this.glowLayerColor.g * group.color.g;
            gb = this.glowLayerColor.b * group.color.b;
            ga = MathUtils.clamp(this.glowLayerColor.a * group.color.a * this.a, 0F, 1F);
        }
        else
        {
            gr = group.glowingColor.r * group.color.r;
            gg = group.glowingColor.g * group.color.g;
            gb = group.glowingColor.b * group.color.b;
            ga = MathUtils.clamp(group.color.a * this.a, 0F, 1F);
        }

        this.vertex.set(vertex.vertex.x, vertex.vertex.y, vertex.vertex.z, 1);
        stack.peek().getPositionMatrix().transform(this.vertex);

        builder.vertex(this.vertex.x, this.vertex.y, this.vertex.z)
            .color(
                MathUtils.clamp(gr, 0F, 1F),
                MathUtils.clamp(gg, 0F, 1F),
                MathUtils.clamp(gb, 0F, 1F),
                MathUtils.clamp(ga, 0F, 1F)
            )
            .texture(vertex.uv.x, vertex.uv.y)
            .overlay(this.overlay);

        if (this.stencilMap != null)
        {
            builder.light(this.stencilMap.increment ? group.index : 0, 0);
        }
        else
        {
            builder.light(LightmapTextureManager.MAX_LIGHT_COORDINATE);
        }

        builder.normal(normal.x, normal.y, normal.z);
    }
}
