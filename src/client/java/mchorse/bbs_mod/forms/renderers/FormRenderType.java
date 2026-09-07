package mchorse.bbs_mod.forms.renderers;

import net.minecraft.item.ModelTransformationMode;

public enum FormRenderType
{
    MODEL_BLOCK, ENTITY, ITEM_FP, ITEM_TP, ITEM_INVENTORY, ITEM, PREVIEW;

    public static FormRenderType fromModelMode(ModelTransformationMode mode)
    {
        if (mode.isFirstPerson())
        {
            return ITEM_FP;
        }
        else if (mode == ModelTransformationMode.THIRD_PERSON_LEFT_HAND || mode == ModelTransformationMode.THIRD_PERSON_RIGHT_HAND)
        {
            return ITEM_TP;
        }
        else if (mode == ModelTransformationMode.GROUND)
        {
            return ITEM;
        }
        else if (mode == ModelTransformationMode.GUI)
        {
            return ITEM_INVENTORY;
        }

        return ENTITY;
    }
}