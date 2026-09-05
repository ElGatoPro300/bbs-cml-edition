package mchorse.bbs_mod.client.renderer.item;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.blocks.entities.ModelProperties;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.pose.Transform;

import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ModelBlockItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer
{
    private Map<ItemStack, Item> map = new HashMap<>();

    public void update()
    {
        Iterator<Item> it = this.map.values().iterator();

        while (it.hasNext())
        {
            Item item = it.next();

            if (item.expiration <= 0)
            {
                it.remove();
            }

            item.expiration -= 1;
            item.entity.getProperties().update(item.formEntity);
            item.formEntity.update();
        }
    }

    @Override
    public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay)
    {
        Item item = this.get(stack);

        if (item != null)
        {
            ModelProperties properties = item.entity.getProperties();
            Form form = properties.getForm(mode);

            if (form != null)
            {
                item.expiration = 20;

                Transform transform = properties.getTransform(mode);

                matrices.push();
                matrices.translate(0.5F, 0F, 0.5F);
                MatrixStackUtils.applyTransform(matrices, transform);

                RenderSystem.enableDepthTest();

                try
                {
                    if (mode == ModelTransformationMode.GUI)
                    {
                        Vector3f a = new Vector3f(0.85F, 0.85F, -1.0F).normalize();
                        Vector3f b = new Vector3f(-0.85F, 0.85F, 1.0F).normalize();
                        RenderSystem.setupGui3DDiffuseLighting(a, b);
                    }

                    FormUtilsClient.render(form, new FormRenderingContext()
                        .set(FormRenderType.fromModelMode(mode), item.formEntity, matrices, light, overlay, MinecraftClient.getInstance().getTickDelta())
                        .camera(MinecraftClient.getInstance().gameRenderer.getCamera()));
                }
                finally
                {
                    if (mode == ModelTransformationMode.GUI)
                    {
                        /* Re-enable GUI lights — disable left hotbar widgets / later slots dark. */
                        BBSRendering.restoreAfterGuiItemForm();
                    }
                    else
                    {
                        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
                    }

                    RenderSystem.disableDepthTest();
                }

                matrices.pop();
            }
        }
    }

    public Item get(ItemStack stack)
    {
        if (stack == null || stack.getItem() != BBSMod.MODEL_BLOCK_ITEM)
        {
            return null;
        }

        if (this.map.containsKey(stack))
        {
            return this.map.get(stack);
        }

        NbtCompound nbt = stack.getNbt();
        ModelBlockEntity entity = new ModelBlockEntity(BlockPos.ORIGIN, BBSMod.MODEL_BLOCK.getDefaultState());
        Item item = new Item(entity);

        this.map.put(stack, item);

        if (nbt == null)
        {
            return item;
        }

        entity.readNbt(nbt.getCompound("BlockEntityTag"));

        return item;
    }

    public static class Item
    {
        public ModelBlockEntity entity;
        public IEntity formEntity;
        public int expiration = 20;

        public Item(ModelBlockEntity entity)
        {
            this.entity = entity;
            this.formEntity = new StubEntity(MinecraftClient.getInstance().world);
        }
    }
}
