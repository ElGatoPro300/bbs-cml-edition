package mchorse.bbs_mod.cubic.render;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.data.model.ModelMesh;
import mchorse.bbs_mod.forms.renderers.utils.BillboardRenderLayers;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.obj.shapes.ShapeKeys;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.iris.ShaderOpacityPatch;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;

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
                BillboardRenderLayers.draw(buffer, texture, texture.isLinear(), false,
                    this.a * group.color.a >= ShaderOpacityPatch.LIVE_DEPTH_WRITE_ALPHA, this.cull);
            }
        }
        finally
        {
            this.a = savedAlpha;
        }
    }
}
