package mchorse.bbs_mod.client.render;

import mchorse.bbs_mod.forms.FormUtilsClient;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.render.command.RenderDispatcher;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/**
 * 1.21.11 item draw path: ItemModelManager fills {@link ItemRenderState}, then submits
 * into an {@link OrderedRenderCommandQueue}.
 * An isolated queue/dispatcher is used for immediate previews to avoid clearing or corrupting
 * unrelated global queues.
 */
public final class ItemRenderHelper
{
    private static final ItemRenderState STATE = new ItemRenderState();
    private static OrderedRenderCommandQueueImpl isolatedQueue;
    private static RenderDispatcher isolatedDispatcher;

    private static void ensureIsolatedDispatcher()
    {
        if (isolatedDispatcher == null)
        {
            MinecraftClient client = MinecraftClient.getInstance();

            isolatedQueue = new OrderedRenderCommandQueueImpl();
            isolatedDispatcher = new RenderDispatcher(
                isolatedQueue,
                client.getBlockRenderManager(),
                client.getBufferBuilders().getEntityVertexConsumers(),
                client.getAtlasManager(),
                client.getBufferBuilders().getOutlineVertexConsumers(),
                client.getBufferBuilders().getEffectVertexConsumers(),
                client.textRenderer
            );
        }
    }

    private ItemRenderHelper()
    {}

    public static void renderItem(ItemStack stack, ItemDisplayContext mode, MatrixStack matrices, int light, int overlay, World world, LivingEntity entity)
    {
        renderItem(stack, mode, matrices, light, overlay, world, entity, false);
    }

    public static void renderItem(ItemStack stack, ItemDisplayContext mode, MatrixStack matrices, int light, int overlay, World world, LivingEntity entity, boolean flush)
    {
        if (stack == null || stack.isEmpty())
        {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();

        STATE.clear();

        if (entity != null)
        {
            client.getItemModelManager().updateForLivingEntity(STATE, stack, mode, entity);
        }
        else
        {
            client.getItemModelManager().clearAndUpdate(STATE, stack, mode, world, null, 0);
        }

        if (STATE.isEmpty())
        {
            return;
        }

        if (flush)
        {
            ensureIsolatedDispatcher();
            STATE.render(matrices, isolatedQueue, light, overlay, 0);
            isolatedDispatcher.render();
            FormUtilsClient.getProvider().draw();
        }
        else
        {
            OrderedRenderCommandQueue queue = client.gameRenderer.getEntityRenderCommandQueue();

            STATE.render(matrices, queue, light, overlay, 0);
        }
    }

    public static void renderItem(ItemStack stack, ItemDisplayContext mode, MatrixStack matrices, int light, int overlay, World world, LivingEntity entity, OrderedRenderCommandQueue queue)
    {
        if (stack == null || stack.isEmpty() || queue == null)
        {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();

        STATE.clear();

        if (entity != null)
        {
            client.getItemModelManager().updateForLivingEntity(STATE, stack, mode, entity);
        }
        else
        {
            client.getItemModelManager().clearAndUpdate(STATE, stack, mode, world, null, 0);
        }

        if (!STATE.isEmpty())
        {
            STATE.render(matrices, queue, light, overlay, 0);
        }
    }
}
