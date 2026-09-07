package mchorse.bbs_mod.items;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;

public class MobKillerItem extends SwordItem
{
    public MobKillerItem(Settings settings)
    {
        super(ToolMaterials.WOOD, settings);
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
            /* Instakill is kill(), not sword attributes. AttackDamage.fromAttacker
             * maps this item to MOB_KILLER_DAMAGE when the melee hit is recorded. */
            target.kill();
        }

        return super.postHit(stack, target, attacker);
    }
}
