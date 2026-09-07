package mchorse.bbs_mod.items;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;

public class StructurePickerItem extends Item
{
    public StructurePickerItem(Settings settings)
    {
        super(settings);
    }

    @Override
    public boolean hasGlint(ItemStack stack)
    {
        return true;
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context)
    {
        return ActionResult.SUCCESS;
    }
}
