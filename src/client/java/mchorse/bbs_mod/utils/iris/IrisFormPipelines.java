package mchorse.bbs_mod.utils.iris;

import mchorse.bbs_mod.mixin.client.iris.IrisPipelinesAccessor;

import net.irisshaders.iris.pipeline.IrisPipelines;
import net.irisshaders.iris.pipeline.programs.ShaderKey;
import net.irisshaders.iris.vertices.IrisVertexFormats;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;

public class IrisFormPipelines
{
    public static boolean isEntityFormat(VertexFormat format)
    {
        return format == IrisVertexFormats.ENTITY;
    }

    public static void register(RenderPipeline pipeline, RenderPipeline source)
    {
        if (source != null)
        {
            /* Copy both maps, retaining Iris's hand and block-entity selection. */
            IrisPipelines.copyPipeline(source, pipeline);
        }
        else
        {
            /* Unlit textured forms use world programs, never a GUI override. */
            IrisPipelines.assignPipeline(pipeline, ShaderKey.TEXTURED_COLOR);
            IrisPipelinesAccessor.bbs$assignToShadow(pipeline, ignored -> ShaderKey.SHADOW_TEX_COLOR);
        }
    }
}
