package mchorse.bbs_mod.actions.types.item;

import mchorse.bbs_mod.actions.SuperFakePlayer;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.items.GunItem;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.clips.Clip;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

public class UseItemActionClip extends ItemActionClip
{
    public final ValueInt useTicks = new ValueInt("use_ticks", 0, 0, Integer.MAX_VALUE);

    public UseItemActionClip()
    {
        super();

        this.add(this.useTicks);
    }

    @Override
    public void applyAction(LivingEntity actor, SuperFakePlayer player, Film film, Replay replay, int tick)
    {
        Hand hand = this.hand.get() ? Hand.MAIN_HAND : Hand.OFF_HAND;

        GunItem.actor = actor;

        this.applyPositionRotation(player, replay, tick);
        ItemStack copy = this.itemStack.get().copy();
        int maxUseTime = copy.getMaxUseTime(player);
        int used = this.useTicks.get();

        player.setStackInHand(hand, copy);
        copy.use(player.getWorld(), player, hand);

        if (used > 0 && maxUseTime > 0)
        {
            int remaining = Math.max(0, maxUseTime - used);
            copy.onStoppedUsing(player.getWorld(), player, remaining);
            player.stopUsingItem();
        }

        player.setStackInHand(hand, ItemStack.EMPTY);

        GunItem.actor = null;
    }

    @Override
    protected Clip create()
    {
        return new UseItemActionClip();
    }
}