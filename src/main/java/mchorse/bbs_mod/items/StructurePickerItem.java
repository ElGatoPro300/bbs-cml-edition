package mchorse.bbs_mod.items;

import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.ShovelItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.util.ActionResult;

public class StructurePickerItem extends ShovelItem
{
    public StructurePickerItem(Settings settings)
    {
        super(ToolMaterials.WOOD, settings);
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
