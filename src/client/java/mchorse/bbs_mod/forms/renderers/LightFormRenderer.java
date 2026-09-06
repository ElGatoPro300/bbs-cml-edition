package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.LightForm;
import mchorse.bbs_mod.ui.framework.UIContext;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BlockStateComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import org.joml.Matrix3x2fStack;

import java.util.Map;

public class LightFormRenderer extends FormRenderer<LightForm>
{
    private final ItemStack stack;

    public LightFormRenderer(LightForm form)
    {
        super(form);
        this.stack = new ItemStack(Registries.ITEM.get(Identifier.of("minecraft", "light")));
    }

    @Override
    public boolean is3D()
    {
        return false;
    }

    @Override
    protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        context.batcher.flush();

        int level = Math.max(0, Math.min(15, this.form.level.get()));
        ItemStack stack = this.stack.copy();

        if (!stack.isEmpty())
        {
            stack.set(DataComponentTypes.BLOCK_STATE, new BlockStateComponent(Map.of("level", Integer.toString(level))));
        }

        if (stack.isEmpty())
        {
            return;
        }

        CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
        Matrix3x2fStack matrices = context.batcher.getContext().getMatrices();

        float cellW = x2 - x1;
        float cellH = y2 - y1;
        float scale = Math.min(cellW, cellH) / 16F * 0.8F * this.form.uiScale.get();
        float centerX = x1 + cellW / 2F;
        float centerY = y1 + cellH / 2F;

        matrices.pushMatrix();
        matrices.translate(centerX, centerY);
        matrices.scale(scale, scale);

        consumers.setUI(true);
        context.batcher.getContext().drawItem(stack, -8, -8);
        context.batcher.getContext().drawStackOverlay(context.batcher.getFont().getRenderer(), stack, -8, -8);
        consumers.setUI(false);
        matrices.popMatrix();
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
    }
}
