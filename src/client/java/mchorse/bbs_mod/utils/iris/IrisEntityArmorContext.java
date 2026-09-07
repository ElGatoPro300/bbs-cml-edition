package mchorse.bbs_mod.utils.iris;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.MCEntity;

import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.Entity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.trim.ArmorTrim;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import net.irisshaders.iris.helpers.EntityState;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.irisshaders.iris.uniforms.CapturedRenderingState;

import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import it.unimi.dsi.fastutil.objects.Object2IntMap;

/**
 * Mirrors Iris {@code MixinHumanoidArmorLayer} / {@code MixinItemRenderer}: bake entity/item
 * (or block-state) IDs into vertices via {@link CapturedRenderingState} while ModelForm /
 * ModelBlock armor and head-slot geometry tessellates.
 * <p>
 * Do <b>not</b> wrap buffers with {@code EntityRenderStateShard} here. That calls
 * {@code GbufferPrograms.beginEntities()} during isolated {@code Immediate.draw()},
 * outside {@code EntityRenderDispatcher}'s MatrixStack pairing, and leaves WorldRenderer
 * with a non-empty pose stack (crash: "Pose stack not empty").
 */
public final class IrisEntityArmorContext
{
    private IrisEntityArmorContext()
    {}

    public static boolean isActive()
    {
        return BBSRendering.isIrisShadersEnabled()
            && BBSRendering.isRenderingWorld()
            && !BBSRendering.isIrisShadowPass();
    }

    /** Identity — phase wrapping is unsafe outside the entity dispatcher (see class javadoc). */
    public static VertexConsumerProvider wrapEntityBuffers(VertexConsumerProvider consumers)
    {
        return consumers;
    }

    public static int resolveEntityId(IEntity entity)
    {
        Object2IntFunction<NamespacedId> entityIds = WorldRenderingSettings.INSTANCE.getEntityIds();

        if (entityIds == null)
        {
            return 0;
        }

        if (entity instanceof MCEntity mc)
        {
            Entity mcEntity = mc.getMcEntity();

            if (mcEntity != null)
            {
                Identifier id = Registries.ENTITY_TYPE.getId(mcEntity.getType());

                return entityIds.applyAsInt(new NamespacedId(id.getNamespace(), id.getPath()));
            }
        }

        /* StubEntity / unknown — player materials match Complementary armor IPBR best. */
        return entityIds.applyAsInt(new NamespacedId("minecraft", "player"));
    }

    public static int resolveItemId(Item item)
    {
        Object2IntFunction<NamespacedId> itemIds = WorldRenderingSettings.INSTANCE.getItemIds();

        if (itemIds == null || item == null)
        {
            return 0;
        }

        Identifier id = Registries.ITEM.getId(item);

        return itemIds.applyAsInt(new NamespacedId(id.getNamespace(), id.getPath()));
    }

    /**
     * Same rules as Iris {@code MixinItemRenderer}: BlockItems use block-state IDs;
     * other items use the item ID map.
     */
    public static int resolveRenderedItemId(ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
        {
            return 0;
        }

        Item item = stack.getItem();

        if (item instanceof BlockItem blockItem)
        {
            Object2IntMap<BlockState> blockIds = WorldRenderingSettings.INSTANCE.getBlockStateIds();

            if (blockIds != null)
            {
                return blockIds.getOrDefault(blockItem.getBlock().getDefaultState(), 0);
            }
        }

        return resolveItemId(item);
    }

    public static int resolveTrimItemId(ArmorTrim trim)
    {
        Object2IntFunction<NamespacedId> itemIds = WorldRenderingSettings.INSTANCE.getItemIds();

        if (itemIds == null || trim == null)
        {
            return 0;
        }

        String asset = trim.getMaterial().value().assetName();

        return itemIds.applyAsInt(new NamespacedId("minecraft", "trim_" + asset));
    }

    public static Scope beginArmorPiece(IEntity entity, Item item)
    {
        return beginEquippedItem(entity, item == null ? ItemStack.EMPTY : item.getDefaultStack());
    }

    public static Scope beginEquippedItem(IEntity entity, ItemStack stack)
    {
        if (!isActive() || stack == null || stack.isEmpty())
        {
            return Scope.INACTIVE;
        }

        CapturedRenderingState state = CapturedRenderingState.INSTANCE;
        int prevEntity = state.getCurrentRenderedEntity();
        int prevItem = state.getCurrentRenderedItem();
        int prevBlockEntity = state.getCurrentRenderedBlockEntity();
        boolean setEntity = prevEntity <= 0;
        boolean setBlockEntity = false;

        if (setEntity)
        {
            state.setCurrentEntity(resolveEntityId(entity));
        }

        Item item = stack.getItem();

        if (item instanceof BlockItem && WorldRenderingSettings.INSTANCE.getBlockStateIds() != null)
        {
            /* Iris MixinItemRenderer marks block items as a synthetic block-entity sample. */
            state.setCurrentBlockEntity(1);
            setBlockEntity = true;
        }

        state.setCurrentRenderedItem(resolveRenderedItemId(stack));

        return new Scope(prevEntity, prevItem, prevBlockEntity, setEntity, setBlockEntity, false);
    }

    public static void beginTrim(ArmorTrim trim)
    {
        if (!isActive() || trim == null)
        {
            return;
        }

        EntityState.interposeItemId(resolveTrimItemId(trim));
    }

    public static void endTrim()
    {
        if (!isActive())
        {
            return;
        }

        EntityState.restoreItemId();
    }

    public static final class Scope implements AutoCloseable
    {
        private static final Scope INACTIVE = new Scope(0, 0, 0, false, false, true);

        private final int prevEntity;
        private final int prevItem;
        private final int prevBlockEntity;
        private final boolean restoreEntity;
        private final boolean restoreBlockEntity;
        private final boolean inactive;

        private Scope(int prevEntity, int prevItem, int prevBlockEntity, boolean restoreEntity, boolean restoreBlockEntity, boolean inactive)
        {
            this.prevEntity = prevEntity;
            this.prevItem = prevItem;
            this.prevBlockEntity = prevBlockEntity;
            this.restoreEntity = restoreEntity;
            this.restoreBlockEntity = restoreBlockEntity;
            this.inactive = inactive;
        }

        @Override
        public void close()
        {
            if (this.inactive)
            {
                return;
            }

            CapturedRenderingState state = CapturedRenderingState.INSTANCE;

            state.setCurrentRenderedItem(this.prevItem);

            if (this.restoreBlockEntity)
            {
                state.setCurrentBlockEntity(this.prevBlockEntity);
            }

            if (this.restoreEntity)
            {
                state.setCurrentEntity(this.prevEntity);
            }

            EntityState.restoreItemId();
        }
    }
}
