package mchorse.bbs_mod.client.renderer.item;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.items.GunProperties;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.model_blocks.UIModelBlockEditorMenu;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.pose.Transform;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.model.special.SpecialModelRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;

import org.joml.Vector3fc;

import com.mojang.serialization.MapCodec;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;

public class GunItemRenderer implements SpecialModelRenderer<ItemStack>
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
            item.properties.update(item.formEntity);
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
    public void render(ItemStack data, ItemDisplayContext mode, MatrixStack matrices, OrderedRenderCommandQueue queue, int light, int overlay, boolean hasGlint, int outlineColor)
    {
        Item item = this.get(data);

        if (item != null)
        {
            GunProperties properties = item.properties;
            Form form = properties.getForm(mode);
            Transform transform = properties.getTransform(mode);
            boolean zoom = mode.isFirstPerson() && BBSModClient.getGunZoom() != null && properties.getZoomForm() != null;

            if (zoom)
            {
                form = properties.getZoomForm();
                transform = properties.zoomTransform;
            }

            /* Preview zoom form */
            if (UIScreen.getCurrentMenu() instanceof UIModelBlockEditorMenu editorMenu && editorMenu.currentSection == editorMenu.sectionZoom)
            {
                form = editorMenu.getGunProperties().getZoomForm();
                transform = editorMenu.getGunProperties().zoomTransform;
            }

            if (form != null)
            {
                item.expiration = 20;

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

                    int maxLight = LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE;
                    FormUtilsClient.render(form, new FormRenderingContext()
                        .set(FormRenderType.fromModelMode(mode), item.formEntity, matrices, maxLight, overlay, MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(false))
                        .camera(MinecraftClient.getInstance().gameRenderer.getCamera()));
                }
                finally
                {
                    if (mode == ItemDisplayContext.GUI)
                    {
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
        if (stack == null || stack.getItem() != BBSMod.GUN_ITEM)
        {
            return null;
        }

        if (this.map.containsKey(stack))
        {
            return this.map.get(stack);
        }

        Item item = new Item(GunProperties.get(stack));

        this.map.put(stack, item);

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
            return BBSModClient.getGunItemRenderer();
        }
    }

    public static class Item
    {
        public GunProperties properties;
        public IEntity formEntity;
        public int expiration = 20;

        public Item(GunProperties properties)
        {
            this.properties = properties;
            this.formEntity = new StubEntity(MinecraftClient.getInstance().world);
        }
    }
}
