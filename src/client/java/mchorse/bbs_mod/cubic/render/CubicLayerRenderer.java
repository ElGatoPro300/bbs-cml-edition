package mchorse.bbs_mod.cubic.render;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSUniform;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.data.model.ModelMesh;
import mchorse.bbs_mod.cubic.data.model.ModelVertex;
import mchorse.bbs_mod.cubic.render.vao.ModelVAORenderer;
import mchorse.bbs_mod.forms.renderers.utils.BillboardRenderLayers;
import mchorse.bbs_mod.forms.renderers.utils.ModelEffectPass;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.obj.shapes.ShapeKeys;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.iris.ShaderOpacityPatch;

import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.VertexFormat;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;

/** Draws the existing animated CPU geometry with explicit 1.21.11 render passes. */
public class CubicLayerRenderer extends CubicCubeRenderer
{
    private final Function<String, Link> textures;
    private final Link fallback;
    private final boolean cull;
    private ShaderProgram effectShader;
    private Matrix4f rootInverse;

    public void setEffects(ShaderProgram shader, Matrix4f rootInverse, StencilMap stencilMap)
    {
        this.effectShader = shader;
        this.rootInverse = rootInverse;
        this.stencilMap = stencilMap;
    }

    public CubicLayerRenderer(int light, int overlay, ShapeKeys keys, Function<String, Link> textures, Link fallback, boolean cull)
    {
        super(light, overlay, null, keys);
        this.textures = textures;
        this.fallback = fallback;
        this.cull = cull;
    }

    @Override
    public boolean renderGroup(BufferBuilder unused, MatrixStack stack, ModelGroup group, Model model)
    {
        if (this.stencilMap != null && !this.stencilMap.isBoneAllowed(group.id))
        {
            return false;
        }

        Set<String> materials = new LinkedHashSet<>();

        if (!group.cubes.isEmpty())
        {
            materials.add("");
        }

        for (ModelMesh mesh : group.meshes)
        {
            materials.add(mesh.material == null ? "" : mesh.material);
        }

        for (String material : materials)
        {
            Link texture = this.textures == null ? null : this.textures.apply(material);

            if (texture == null)
            {
                texture = this.fallback;
            }

            CubicGroupTextureBlend blend = CubicGroupTextureBlend.resolve(group, texture);

            if (blend != null && blend.isPartial())
            {
                this.drawMaterial(stack, group, model, material, blend.from, 1F - blend.blend);
                this.drawMaterial(stack, group, model, material, blend.to, blend.blend);
            }
            else
            {
                this.drawMaterial(stack, group, model, material, CubicGroupTextureBlend.resolveDrawTexture(blend, texture), 1F);
            }
        }

        return false;
    }

    private void drawMaterial(MatrixStack stack, ModelGroup group, Model model, String material, Link link, float alpha)
    {
        Texture texture = BBSModClient.getTextures().getTexture(link);

        if (texture == null || this.a * group.color.a * alpha <= 0.001F)
        {
            return;
        }

        float savedAlpha = this.a;
        this.a *= alpha;

        try
        {
            if (this.effectShader != null)
            {
                ModelVAORenderer.beginCpuGeometry(this.effectShader);
                ModelVAORenderer.setGroupPaint(this.resolveEffectivePaintR(group), this.resolveEffectivePaintG(group),
                    this.resolveEffectivePaintB(group), this.resolveEffectivePaintStrength(group));
                ModelVAORenderer.setGroupPaintEffectTransform(group.paintColor.transform);
                ModelVAORenderer.setGroupGlowing(this.resolveEffectiveGlowR(group), this.resolveEffectiveGlowG(group),
                    this.resolveEffectiveGlowB(group), this.resolveEffectiveGlowStrength(group));
                ModelVAORenderer.setGroupGlowEffectTransform(group.glowingColor.transform);
                ModelVAORenderer.setGroupFormColorGrade(group.color);
                ModelVAORenderer.setGroupColorEffectTransform(group.color.transform);
                ModelVAORenderer.setGroupFormColorTint(group.color);
            }

            BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES,
                VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);

            if (material.isEmpty())
            {
                for (ModelCube cube : group.cubes)
                {
                    if (cube.visible)
                    {
                        this.renderCube(builder, stack, group, cube);
                    }
                }
            }

            for (ModelMesh mesh : group.meshes)
            {
                if (material.equals(mesh.material == null ? "" : mesh.material))
                {
                    this.renderMesh(builder, stack, model, group, mesh);
                }
            }

            BuiltBuffer buffer = builder.endNullable();

            if (buffer != null)
            {
                if (this.effectShader != null)
                {
                    ModelVAORenderer.setupUniformsCpuPretransformed(this.effectShader, this.rootInverse);
                    BBSUniform.set(this.effectShader, "TextureBlendActive", 0F);

                    if (this.stencilMap != null)
                    {
                        BBSUniform.set(this.effectShader, "Target", this.stencilMap.objectIndex);
                    }

                    boolean overlayPass = ModelVAORenderer.isPaintOverlayPass() || ModelVAORenderer.isColorTintOverlayPass() || ModelVAORenderer.isColorGradeOverlayPass();
                    ModelEffectPass.draw(buffer, texture, this.effectShader, this.stencilMap != null,
                        this.stencilMap != null || (!overlayPass && this.a * group.color.a >= ShaderOpacityPatch.LIVE_DEPTH_WRITE_ALPHA), this.cull, overlayPass);
                }
                else
                {
                    BillboardRenderLayers.draw(buffer, texture, texture.isLinear(), false,
                        this.a * group.color.a >= ShaderOpacityPatch.LIVE_DEPTH_WRITE_ALPHA, this.cull);
                }
            }
        }
        finally
        {
            this.a = savedAlpha;
        }
    }

    @Override
    protected void writeVertex(BufferBuilder builder, MatrixStack stack, ModelGroup group, ModelVertex vertex, Vector3f normal)
    {
        if (this.effectShader == null || !group.color.hasActiveTransform() || this.stencilMap != null)
        {
            super.writeVertex(builder, stack, group, vertex, normal);

            return;
        }

        /* Masked bone tint is evaluated in the fragment shader, not across the whole mesh. */
        this.vertex.set(vertex.vertex.x, vertex.vertex.y, vertex.vertex.z, 1F);
        stack.peek().getPositionMatrix().transform(this.vertex);
        int blockLight = Math.round((this.light & 65535) + (240 - (this.light & 65535)) * Math.max(0F, Math.min(1F, group.lighting)));

        builder.vertex(this.vertex.x, this.vertex.y, this.vertex.z).color(this.r, this.g, this.b, this.a)
            .texture(vertex.uv.x, vertex.uv.y).overlay(this.overlay).light(blockLight, this.light >>> 16)
            .normal(normal.x, normal.y, normal.z);
    }
}
