package mchorse.bbs_mod.forms;

import mchorse.bbs_mod.forms.renderers.utils.BlockPaintOverlayVertexConsumer;
import mchorse.bbs_mod.forms.renderers.utils.GlowEmissionVertexConsumer;
import mchorse.bbs_mod.forms.renderers.utils.RecolorVertexConsumer;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;

import com.mojang.blaze3d.systems.RenderSystem;

import org.lwjgl.opengl.GL11;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public class CustomVertexConsumerProvider implements VertexConsumerProvider
{
    private static Consumer<RenderLayer> runnables;

    private final VertexConsumerProvider.Immediate delegate;
    private Function<VertexConsumer, VertexConsumer> substitute;
    private boolean ui;

    public static void drawLayer(RenderLayer layer)
    {
        if (runnables != null)
        {
            runnables.accept(layer);
        }
    }

    public static void hijackVertexFormat(Consumer<RenderLayer> runnable)
    {
        runnables = runnable;
    }

    public static void clearRunnables()
    {
        runnables = null;
    }

    public CustomVertexConsumerProvider(VertexConsumerProvider.Immediate delegate)
    {
        this.delegate = delegate;
    }

    public Function<VertexConsumer, VertexConsumer> getSubstitute()
    {
        return this.substitute;
    }

    public void setSubstitute(Function<VertexConsumer, VertexConsumer> substitute)
    {
        this.substitute = substitute;

        if (this.substitute == null)
        {
            RecolorVertexConsumer.newColor = null;
            RecolorVertexConsumer.newPaintColor = null;
            GlowEmissionVertexConsumer.emissionColor = null;
            BlockPaintOverlayVertexConsumer.paintOverlayColor = null;
        }
    }

    public void setUI(boolean ui)
    {
        this.ui = ui;
    }

    /**
     * Iris {@code BlockSensitive} tagging must target the Immediate buffer, not a recolor wrap.
     */
    public VertexConsumer getRawBuffer(RenderLayer renderLayer)
    {
        return this.delegate.getBuffer(renderLayer);
    }

    @Override
    public VertexConsumer getBuffer(RenderLayer renderLayer)
    {
        VertexConsumer buffer = this.getRawBuffer(renderLayer);

        if (this.substitute != null)
        {
            VertexConsumer apply = this.substitute.apply(buffer);

            if (apply != null)
            {
                return apply;
            }
        }

        return buffer;
    }

    public void draw()
    {
        this.delegate.draw();

        if (this.ui)
        {
            /* Force back the depth func because it seems like stuff rendered by a vertex
             * consumer is resetting the depth func to GL_LESS, and since this vertex consumer
             * is designed  */
            RenderSystem.depthFunc(GL11.GL_ALWAYS);
        }
    }

    /**
     * Flushes only the active dynamic layer (e.g. last villager clothing pass) without
     * iterating fixed world layerBuffers.
     */
    public void drawCurrentLayer()
    {
        this.delegate.drawCurrentLayer();
    }
}
