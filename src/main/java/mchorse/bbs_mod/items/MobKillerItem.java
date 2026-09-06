package mchorse.bbs_mod.items;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.server.world.ServerWorld;

public class MobKillerItem extends SwordItem
{
    public MobKillerItem(Settings settings)
    {
        super(ToolMaterial.WOOD, 3, -2.4F, settings);
    }

    @Override
    public boolean hasGlint(ItemStack stack)
    {
        return true;
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker)
    {
        if (!target.getWorld().isClient && !(target instanceof PlayerEntity))
        {
            target.kill((ServerWorld) target.getWorld());
        }

        return super.postHit(stack, target, attacker);
    }
}
