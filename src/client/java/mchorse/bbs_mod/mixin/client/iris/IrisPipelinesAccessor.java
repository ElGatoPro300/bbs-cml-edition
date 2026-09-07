package mchorse.bbs_mod.mixin.client.iris;

import net.irisshaders.iris.pipeline.IrisPipelines;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.programs.ShaderKey;

import com.mojang.blaze3d.pipeline.RenderPipeline;

import it.unimi.dsi.fastutil.Function;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = IrisPipelines.class, remap = false)
public interface IrisPipelinesAccessor
{
    @Invoker("assignToShadow")
    public static void bbs$assignToShadow(RenderPipeline pipeline, Function<IrisRenderingPipeline, ShaderKey> shader)
    {
        throw new AssertionError();
    }
}
