package mchorse.bbs_mod.items;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StructurePickerRegionMerger
{
    public static List<MergedRegion> merge(Set<BlockPos> blocks)
    {
        Set<BlockPos> open = new HashSet<>(blocks);
        List<MergedRegion> merged = new ArrayList<>();

        while (!open.isEmpty())
        {
            BlockPos seed = open.iterator().next();
            int x0 = seed.getX();
            int x1 = seed.getX();
            int y0 = seed.getY();
            int y1 = seed.getY();
            int z0 = seed.getZ();
            int z1 = seed.getZ();
            boolean expanded;

            do
            {
                expanded = false;

                if (StructurePickerRegionMerger.canFill(open, x0 - 1, y0, z0, x1, y1, z1))
                {
                    x0--;
                    expanded = true;
                }

                if (StructurePickerRegionMerger.canFill(open, x0, y0, z0, x1 + 1, y1, z1))
                {
                    x1++;
                    expanded = true;
                }

                if (StructurePickerRegionMerger.canFill(open, x0, y0, z0 - 1, x1, y1, z1))
                {
                    z0--;
                    expanded = true;
                }

                if (StructurePickerRegionMerger.canFill(open, x0, y0, z0, x1, y1, z1 + 1))
                {
                    z1++;
                    expanded = true;
                }

                if (StructurePickerRegionMerger.canFill(open, x0, y0 - 1, z0, x1, y1, z1))
                {
                    y0--;
                    expanded = true;
                }

                if (StructurePickerRegionMerger.canFill(open, x0, y0, z0, x1, y1 + 1, z1))
                {
                    y1++;
                    expanded = true;
                }
            }
            while (expanded);

            for (int x = x0; x <= x1; x++)
            {
                for (int y = y0; y <= y1; y++)
                {
                    for (int z = z0; z <= z1; z++)
                    {
                        open.remove(new BlockPos(x, y, z));
                    }
                }
            }

            StructurePickerMode mode = y0 == y1 ? StructurePickerMode.RECTANGLE : StructurePickerMode.CUBE;

            merged.add(new MergedRegion(new BlockPos(x0, y0, z0), new BlockPos(x1, y1, z1), mode));
        }

        return merged;
    }

    private static boolean canFill(Set<BlockPos> open, int x0, int y0, int z0, int x1, int y1, int z1)
    {
        for (int x = x0; x <= x1; x++)
        {
            for (int y = y0; y <= y1; y++)
            {
                for (int z = z0; z <= z1; z++)
                {
                    if (!open.contains(new BlockPos(x, y, z)))
                    {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    public record MergedRegion(BlockPos min, BlockPos max, StructurePickerMode mode)
    {
    }
}
