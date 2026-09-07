package mchorse.bbs_mod.actions.types.item;

import mchorse.bbs_mod.actions.SuperFakePlayer;
import mchorse.bbs_mod.actions.values.ValueBlockHitResult;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.items.GunItem;
import mchorse.bbs_mod.utils.clips.Clip;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

public class UseBlockItemActionClip extends ItemActionClip
{
    public final ValueBlockHitResult hit = new ValueBlockHitResult("hit");

    public UseBlockItemActionClip()
    {
        super();

        this.add(this.hit);
    }

    @Override
    public void shift(double dx, double dy, double dz)
    {
        super.shift(dx, dy, dz);

        this.hit.shift(dx, dy, dz);
    }

    @Override
    public void applyAction(LivingEntity actor, SuperFakePlayer player, Film film, Replay replay, int tick)
    {
        Hand hand = this.hand.get() ? Hand.MAIN_HAND : Hand.OFF_HAND;
        ItemStack copy = this.itemStack.get().copy();
        ItemStack previous = player.getStackInHand(hand).copy();

        GunItem.actor = actor;

        this.applyPositionRotation(player, replay, tick);
        player.setStackInHand(hand, copy);
        player.interactionManager.interactBlock(player, player.getWorld(), copy, hand, this.hit.getHitResult());
        player.setStackInHand(hand, previous);

        GunItem.actor = null;
    }

    @Override
    protected Clip create()
    {
        return new UseBlockItemActionClip();
    }
}
