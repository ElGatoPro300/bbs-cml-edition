package mchorse.bbs_mod.client.renderer.item;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
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

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.model.LoadedEntityModels;
import net.minecraft.client.render.item.model.special.SpecialModelRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.storage.NbtReadView;
import net.minecraft.util.ErrorReporter;
import net.minecraft.util.math.BlockPos;

import org.joml.Vector3fc;

import com.mojang.serialization.MapCodec;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;

public class ModelBlockItemRenderer implements SpecialModelRenderer<ItemStack>
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
    public ItemStack getData(ItemStack stack)
    {
        return stack;
    }

    @Override
    public void collectVertices(Consumer<Vector3fc> consumer)
    {
    }

    @Override
    public void render(ItemStack stack, ItemDisplayContext mode, MatrixStack matrices, OrderedRenderCommandQueue queue, int light, int overlay, boolean hasGlint, int outlineColor)
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

                BBSRendering.enableDepthTest();

                try
                {
                    if (mode == ItemDisplayContext.GUI)
                    {
                        MinecraftClient.getInstance().gameRenderer.getDiffuseLighting().setShaderLights(DiffuseLighting.Type.ITEMS_3D);
                    }

                    FormUtilsClient.render(form, new FormRenderingContext()
                        .set(FormRenderType.fromModelMode(mode), item.formEntity, matrices, light, overlay, MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(false))
                        .camera(MinecraftClient.getInstance().gameRenderer.getCamera()));
                }
                finally
                {
                    if (mode == ItemDisplayContext.GUI)
                    {
                        /* Re-enable GUI lights — disable left hotbar widgets / later slots dark. */
                        MinecraftClient.getInstance().gameRenderer.getDiffuseLighting().setShaderLights(DiffuseLighting.Type.ITEMS_FLAT);
                        BBSRendering.restoreAfterGuiItemForm();
                    }
                    else
                    {
                        BBSRendering.setShaderColor(1F, 1F, 1F, 1F);
                    }

                    BBSRendering.disableDepthTest();
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

        ModelBlockEntity entity = new ModelBlockEntity(BlockPos.ORIGIN, BBSMod.MODEL_BLOCK.getDefaultState());
        Item item = new Item(entity);

        this.map.put(stack, item);

        var nbtComponent = stack.get(DataComponentTypes.BLOCK_ENTITY_DATA);
        if (nbtComponent == null)
        {
            return item;
        }

        NbtCompound nbt = nbtComponent.copyNbtWithoutId();
        var world = MinecraftClient.getInstance().world;
        if (world != null)
        {
            entity.read(NbtReadView.create(ErrorReporter.EMPTY, world.getRegistryManager(), nbt));
        }

        return item;
    }

    public static class Unbaked implements SpecialModelRenderer.Unbaked
    {
        public static final MapCodec<Unbaked> CODEC = MapCodec.unit(new Unbaked());

        @Override
        public MapCodec<Unbaked> getCodec()
        {
            return CODEC;
        }

        @Override
        public SpecialModelRenderer<?> bake(SpecialModelRenderer.BakeContext context)
        {
            return BBSModClient.getModelBlockItemRenderer();
        }
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
