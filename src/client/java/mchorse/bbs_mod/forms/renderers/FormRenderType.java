package mchorse.bbs_mod.forms.renderers;

import net.minecraft.item.ItemDisplayContext;

public enum FormRenderType
{
    MODEL_BLOCK, ENTITY, ITEM_FP, ITEM_TP, ITEM_INVENTORY, ITEM, PREVIEW;

    public static FormRenderType fromModelMode(ItemDisplayContext mode)
    {
        if (mode.isFirstPerson())
        {
            return ITEM_FP;
        }
        else if (mode == ItemDisplayContext.THIRD_PERSON_LEFT_HAND || mode == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
        {
            return ITEM_TP;
        }
        else if (mode == ItemDisplayContext.GROUND)
        {
            return ITEM;
        }
        else if (mode == ItemDisplayContext.GUI)
        {
            return ITEM_INVENTORY;
        }

        return ENTITY;
    }
}