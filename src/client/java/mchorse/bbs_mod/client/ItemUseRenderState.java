package mchorse.bbs_mod.client;

import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.mixin.client.LivingEntityItemAccessor;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

import com.mojang.authlib.GameProfile;

import java.util.UUID;

/**
 * Syncs item use state onto a {@link LivingEntity} so vanilla item model stages
 * (bow pull, crossbow charge, trident throw, etc.) resolve correctly during {@code renderItem}.
 */
public final class ItemUseRenderState
{
    private static final int USING_ITEM_FLAG = 1;
    private static final int OFF_HAND_ACTIVE_FLAG = 2;

    private static OtherClientPlayerEntity proxy;
    private static ClientWorld proxyWorld;
    private static boolean drivingLocalPlayerUse;

    private ItemUseRenderState()
    {}

    public static boolean isDrivingLocalPlayerUse()
    {
        return drivingLocalPlayerUse;
    }

    /**
     * Called at the start of {@code Films.updateEndWorld} so a missing FP replay
     * this tick can release last tick's driven use instead of leaving it stuck.
     */
    public static void beginEndWorldUpdate()
    {
        drivingLocalPlayerUse = false;
    }

    public static void releaseLocalPlayerUse()
    {
        drivingLocalPlayerUse = false;

        MinecraftClient client = MinecraftClient.getInstance();

        if (client.player == null)
        {
            return;
        }

        ItemUseRenderState.clearUseFlags(client.player);
    }

    public static LivingEntity prepareProxy(World world, IEntity source, EquipmentSlot slot, ItemStack stack)
    {
        if (!(world instanceof ClientWorld clientWorld) || stack == null || stack.isEmpty())
        {
            return null;
        }

        if (proxy == null || proxyWorld != clientWorld)
        {
            proxy = new OtherClientPlayerEntity(clientWorld, new GameProfile(UUID.randomUUID(), "bbs_item_use"));
            proxy.noClip = true;
            proxyWorld = clientWorld;
        }

        Hand hand = slot == EquipmentSlot.OFFHAND ? Hand.OFF_HAND : Hand.MAIN_HAND;

        ItemUseRenderState.syncEquipment(proxy, source);
        ItemUseRenderState.syncItemUse(proxy, source, hand, stack);

        return proxy;
    }

    public static void syncEquipment(LivingEntity living, IEntity source)
    {
        if (source == null)
        {
            return;
        }

        living.equipStack(EquipmentSlot.MAINHAND, source.getEquipmentStack(EquipmentSlot.MAINHAND));
        living.equipStack(EquipmentSlot.OFFHAND, source.getEquipmentStack(EquipmentSlot.OFFHAND));
        living.equipStack(EquipmentSlot.HEAD, source.getEquipmentStack(EquipmentSlot.HEAD));
        living.equipStack(EquipmentSlot.CHEST, source.getEquipmentStack(EquipmentSlot.CHEST));
        living.equipStack(EquipmentSlot.LEGS, source.getEquipmentStack(EquipmentSlot.LEGS));
        living.equipStack(EquipmentSlot.FEET, source.getEquipmentStack(EquipmentSlot.FEET));
    }

    /**
     * Timeline {@link IEntity#getItemUseTimeLeft()} stores elapsed ticks on replay stubs,
     * but vanilla {@link LivingEntity#getItemUseTimeLeft()} stores remaining ticks.
     */
    public static int getItemUseElapsed(IEntity source, LivingEntity living, ItemStack stack)
    {
        if (source == null)
        {
            return 0;
        }

        if (!source.isUsingItem())
        {
            return 0;
        }

        if (source instanceof StubEntity)
        {
            return Math.max(0, source.getItemUseTimeLeft());
        }

        if (stack == null || stack.isEmpty())
        {
            return 0;
        }

        int maxUseTime = stack.getMaxUseTime(living);
        int remaining = source.getItemUseTimeLeft();

        if (maxUseTime <= 0)
        {
            return Math.max(0, remaining);
        }

        return Math.max(0, maxUseTime - remaining);
    }

    /**
     * Applies item-use fields on {@code living}. {@code stack} must be the same reference
     * that will be passed to {@code ItemRenderer.renderItem} for model predicates.
     */
    public static void syncItemUse(LivingEntity living, IEntity source, Hand hand, ItemStack stack)
    {
        if (living == null)
        {
            return;
        }

        if (source == null || stack == null || stack.isEmpty() || !source.isUsingItem())
        {
            ItemUseRenderState.clearUseFlags(living);

            return;
        }

        int itemUseElapsed = ItemUseRenderState.getItemUseElapsed(source, living, stack);

        int maxUseTime = stack.getMaxUseTime(living);
        int itemUseTimeLeft = Math.max(0, maxUseTime - itemUseElapsed);
        boolean localPlayer = living instanceof ClientPlayerEntity;
        ItemStack active = stack;

        if (localPlayer)
        {
            /* Never write into the real hotbar — setStackInHand copies the use
             * item into whichever slot is selected, and a new interpolate() copy
             * every tick resets HeldItemRenderer's identity-based equip pose. */
            drivingLocalPlayerUse = true;
            active = living.getStackInHand(hand);

            if (active.isEmpty() || !ItemStack.areItemsAndComponentsEqual(active, stack))
            {
                active = stack.copy();
            }
        }
        else
        {
            ItemStack current = living.getStackInHand(hand);

            if (!ItemStack.areEqual(current, stack))
            {
                living.setStackInHand(hand, stack.copy());
                current = living.getStackInHand(hand);
            }

            active = current.isEmpty() ? stack.copy() : current;

            if (!living.isUsingItem() || living.getActiveHand() != hand)
            {
                living.setCurrentHand(hand);
            }
        }

        ((LivingEntityItemAccessor) living).setActiveItemStack(active);
        ((LivingEntityItemAccessor) living).setItemUseTimeLeft(itemUseTimeLeft);
        living.setLivingFlag(USING_ITEM_FLAG, true);
        living.setLivingFlag(OFF_HAND_ACTIVE_FLAG, hand == Hand.OFF_HAND);
    }

    private static void clearUseFlags(LivingEntity living)
    {
        living.clearActiveItem();
        living.setLivingFlag(USING_ITEM_FLAG, false);
        living.setLivingFlag(OFF_HAND_ACTIVE_FLAG, false);
    }
}
