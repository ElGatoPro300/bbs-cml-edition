package mchorse.bbs_mod.items;

public enum StructurePickerBrushShape
{
    SPHERE(0),
    CUBE(1);

    public final int index;

    StructurePickerBrushShape(int index)
    {
        this.index = index;
    }

    public static StructurePickerBrushShape fromIndex(int index)
    {
        for (StructurePickerBrushShape shape : values())
        {
            if (shape.index == index)
            {
                return shape;
            }
        }

        return SPHERE;
    }
}
