package mchorse.bbs_mod.items;

public enum StructurePickerMode
{
    BLOCK(0),
    RECTANGLE(1),
    CUBE(2),
    CIRCLE(3),
    SPHERE(4),
    TRIANGLE(5),
    CONE(6),
    CYLINDER(7);

    public final int index;

    StructurePickerMode(int index)
    {
        this.index = index;
    }

    public static StructurePickerMode fromIndex(int index)
    {
        for (StructurePickerMode mode : values())
        {
            if (mode.index == index)
            {
                return mode;
            }
        }

        return CUBE;
    }

    public boolean isFlat()
    {
        return this == RECTANGLE || this == TRIANGLE || this == CIRCLE;
    }

    public boolean isSingleClick()
    {
        return this == BLOCK;
    }

    public boolean hasShapeOutline()
    {
        return this == TRIANGLE || this == CIRCLE || this == CONE || this == SPHERE || this == CYLINDER;
    }
}
