package mchorse.bbs_mod.ui.utils.context;

import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;

import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;

import org.lwjgl.opengl.GL11;

public class ItemStackContextAction extends ContextAction
{
    public ItemStack stack = ItemStack.EMPTY;

    public ItemStackContextAction(ItemStack stack, IKey label, Runnable runnable)
    {
        super(Icons.NONE, label, runnable);

        this.stack = stack;
    }

    @Override
    public void render(UIContext context, FontRenderer font, int x, int y, int w, int h, boolean hover, boolean selected)
    {
        this.renderBackground(context, x, y, w, h, hover, selected);

        if (this.stack != null && !this.stack.isEmpty())
        {
            MatrixStack matrices = context.batcher.getContext().getMatrices();
            CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();

            matrices.push();
            RenderSystem.disableDepthTest();
            consumers.setUI(true);

            Vector3f light0 = new Vector3f(0.85F, 0.85F, -1.0F).normalize();
            Vector3f light1 = new Vector3f(-0.85F, 0.85F, 1.0F).normalize();
            RenderSystem.setupGui3DDiffuseLighting(light0, light1);

            context.batcher.getContext().drawItem(this.stack, x + 2, y + 2);
            context.batcher.getContext().drawItemInSlot(context.batcher.getFont().getRenderer(), this.stack, x + 2, y + 2);

            context.batcher.getContext().draw();

            DiffuseLighting.disableGuiDepthLighting();

            consumers.setUI(false);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_ALWAYS);
            matrices.pop();
        }

        context.batcher.text(this.label.get(), x + 22, y + (h - font.getHeight()) / 2 + 1, Colors.WHITE, false);
    }
}
