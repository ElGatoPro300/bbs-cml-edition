package mchorse.bbs_mod.client.render.special;

import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.render.SpecialGuiElementRenderer;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.util.math.MatrixStack;

import org.joml.AxisAngle4f;
import org.joml.Quaternionf;

/**
 * Special GUI element renderer for BBS form previews in 1.21.11.
 */
public class BbsFormGuiElementRenderer extends SpecialGuiElementRenderer<BbsFormGuiElementRenderState>
{
    private static int errorLog;

    public BbsFormGuiElementRenderer(Immediate vertexConsumers)
    {
        super(vertexConsumers);
    }

    @Override
    public Class<BbsFormGuiElementRenderState> getElementClass()
    {
        return BbsFormGuiElementRenderState.class;
    }

    @Override
    protected void render(BbsFormGuiElementRenderState state, MatrixStack matrices)
    {
        MinecraftClient.getInstance().gameRenderer.getDiffuseLighting().setShaderLights(DiffuseLighting.Type.ENTITY_IN_UI);

        try
        {
            if (state.angle() != 0F)
            {
                matrices.multiply(new Quaternionf(new AxisAngle4f((float) Math.toRadians(state.angle()), 0F, 1F, 0F)));
            }

            FormRenderingContext context = new FormRenderingContext();

            context.set(FormRenderType.PREVIEW, null, matrices, 0x00F000F0, OverlayTexture.DEFAULT_UV, state.transition());
            context.ui = true;
            context.modelRenderer = true;

            state.renderer().render(context);
        }
        catch (Exception e)
        {
            if (errorLog++ % 120 == 0)
            {
                System.out.println("[BBS list preview] form render failed: " + e);
            }
        }
    }

    @Override
    protected float getYOffset(int height, int windowScaleFactor)
    {
        return 0.85F * height;
    }

    @Override
    protected String getName()
    {
        return "bbs form";
    }
}
