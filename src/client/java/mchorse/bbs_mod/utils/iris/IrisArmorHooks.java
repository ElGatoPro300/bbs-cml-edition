package mchorse.bbs_mod.utils.iris;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.entities.IEntity;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.equipment.trim.ArmorTrim;

/**
 * Iris-optional entry points for armor / head-item material IDs.
 * <p>
 * Call sites must go through this class (not {@link IrisEntityArmorContext} directly)
 * so {@link IrisEntityArmorContext} is only class-loaded when Iris is present — same
 * pattern as {@link BBSRendering#isIrisShadersEnabled()}.
 */
public final class IrisArmorHooks
{
    @FunctionalInterface
    public interface Scope extends AutoCloseable
    {
        @Override
        void close();
    }

    private static final Scope NOOP = () ->
    {};

    private IrisArmorHooks()
    {}

    public static Scope beginArmorPiece(IEntity entity, Item item)
    {
        if (!BBSRendering.isIrisLoaded())
        {
            return NOOP;
        }

        return IrisEntityArmorContext.beginArmorPiece(entity, item)::close;
    }

    public static Scope beginEquippedItem(IEntity entity, ItemStack stack)
    {
        if (!BBSRendering.isIrisLoaded())
        {
            return NOOP;
        }

        return IrisEntityArmorContext.beginEquippedItem(entity, stack)::close;
    }

    public static VertexConsumerProvider wrapEntityBuffers(VertexConsumerProvider consumers)
    {
        if (!BBSRendering.isIrisLoaded())
        {
            return consumers;
        }

        return IrisEntityArmorContext.wrapEntityBuffers(consumers);
    }

    public static void beginTrim(ArmorTrim trim)
    {
        if (!BBSRendering.isIrisLoaded())
        {
            return;
        }

        IrisEntityArmorContext.beginTrim(trim);
    }

    public static void endTrim()
    {
        if (!BBSRendering.isIrisLoaded())
        {
            return;
        }

        IrisEntityArmorContext.endTrim();
    }
}
