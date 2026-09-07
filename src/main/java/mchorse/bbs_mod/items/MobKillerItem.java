package mchorse.bbs_mod.items;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;

public class MobKillerItem extends Item
{
    public MobKillerItem(Settings settings)
    {
        super(settings);
    }

    @Override
    public boolean hasGlint(ItemStack stack)
    {
        return true;
    }

    @Override
    public void postHit(ItemStack stack, LivingEntity target, LivingEntity attacker)
    {
        if (!target.getEntityWorld().isClient() && !(target instanceof PlayerEntity) && target.getEntityWorld() instanceof ServerWorld serverWorld)
        {
            target.kill(serverWorld);
        }

        super.postHit(stack, target, attacker);
    }
}
