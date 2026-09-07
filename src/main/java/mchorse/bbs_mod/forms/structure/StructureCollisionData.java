package mchorse.bbs_mod.forms.structure;

import mchorse.bbs_mod.items.StructurePickerExporter;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.EmptyBlockView;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Cached structure-local collision boxes (after structure pivot, before form/model transforms).
 * Full cubes are greedy-meshed and queried through a spatial grid so large structures stay cheap.
 */
public final class StructureCollisionData
{
    private static final Map<String, StructureCollisionData> CACHE = new ConcurrentHashMap<>();
    private static final int CELL = 4;
    private static final ThreadLocal<BitSet> QUERY_SEEN = ThreadLocal.withInitial(BitSet::new);

    public final List<Box> localBoxes;
    public final Box localBounds;
    private final Map<Long, int[]> spatialGrid;

    private StructureCollisionData(List<Box> localBoxes, Box localBounds, Map<Long, int[]> spatialGrid)
    {
        this.localBoxes = List.copyOf(localBoxes);
        this.localBounds = localBounds;
        this.spatialGrid = spatialGrid;
    }

    public static StructureCollisionData get(String structurePath)
    {
        if (structurePath == null || structurePath.isEmpty())
        {
            return null;
        }

        return CACHE.computeIfAbsent(structurePath, StructureCollisionData::build);
    }

    public static void invalidate(String structurePath)
    {
        if (structurePath != null)
        {
            CACHE.remove(structurePath);
        }
    }

    /**
     * Invoke {@code consumer} for every local box whose AABB intersects {@code localQuery}.
     * Uses a coarse spatial grid so large structures only touch nearby cells.
     */
    public void forEachOverlapping(Box localQuery, Consumer<Box> consumer)
    {
        if (this.localBoxes.isEmpty() || !this.localBounds.intersects(localQuery))
        {
            return;
        }

        if (this.spatialGrid.isEmpty() || this.localBoxes.size() <= 24)
        {
            for (Box local : this.localBoxes)
            {
                if (local.intersects(localQuery))
                {
                    consumer.accept(local);
                }
            }

            return;
        }

        int minCX = floorDiv(localQuery.minX, CELL);
        int minCY = floorDiv(localQuery.minY, CELL);
        int minCZ = floorDiv(localQuery.minZ, CELL);
        int maxCX = floorDiv(localQuery.maxX, CELL);
        int maxCY = floorDiv(localQuery.maxY, CELL);
        int maxCZ = floorDiv(localQuery.maxZ, CELL);
        BitSet seen = QUERY_SEEN.get();

        seen.clear();

        for (int cy = minCY; cy <= maxCY; cy++)
        {
            for (int cz = minCZ; cz <= maxCZ; cz++)
            {
                for (int cx = minCX; cx <= maxCX; cx++)
                {
                    int[] indices = this.spatialGrid.get(packCell(cx, cy, cz));

                    if (indices == null)
                    {
                        continue;
                    }

                    for (int index : indices)
                    {
                        if (seen.get(index))
                        {
                            continue;
                        }

                        seen.set(index);
                        Box local = this.localBoxes.get(index);

                        if (local.intersects(localQuery))
                        {
                            consumer.accept(local);
                        }
                    }
                }
            }
        }
    }

    private static StructureCollisionData build(String path)
    {
        NbtCompound root = StructurePickerExporter.readStructureNbt(path);

        if (root == null || !root.contains("blocks", NbtElement.LIST_TYPE) || !root.contains("palette", NbtElement.LIST_TYPE))
        {
            return empty();
        }

        List<BlockState> palette = new ArrayList<>();
        NbtList paletteNbt = root.getList("palette", NbtElement.COMPOUND_TYPE);

        for (int i = 0; i < paletteNbt.size(); i++)
        {
            palette.add(readBlockState(paletteNbt.getCompound(i)));
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        Set<Long> fullCubes = new HashSet<>();
        List<BlockPos> partialPos = new ArrayList<>();
        List<BlockState> partialStates = new ArrayList<>();
        NbtList blocks = root.getList("blocks", NbtElement.COMPOUND_TYPE);
        boolean hasAny = false;

        for (int i = 0; i < blocks.size(); i++)
        {
            NbtCompound entry = blocks.getCompound(i);
            int stateIndex = entry.getInt("state");

            if (stateIndex < 0 || stateIndex >= palette.size())
            {
                continue;
            }

            BlockState state = palette.get(stateIndex);

            if (state == null || state.isAir())
            {
                continue;
            }

            NbtList posList = entry.getList("pos", NbtElement.INT_TYPE);

            if (posList.size() < 3)
            {
                continue;
            }

            BlockPos pos = new BlockPos(posList.getInt(0), posList.getInt(1), posList.getInt(2));

            /* Bounds from all non-air blocks — same pivot basis as StructureFormRenderer. */
            hasAny = true;
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());

            VoxelShape shape = state.getCollisionShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);

            if (shape.isEmpty())
            {
                continue;
            }

            if (isFullBlockCube(shape))
            {
                fullCubes.add(packBlock(pos.getX(), pos.getY(), pos.getZ()));
            }
            else
            {
                partialPos.add(pos);
                partialStates.add(state);
            }
        }

        if (!hasAny || (fullCubes.isEmpty() && partialPos.isEmpty()))
        {
            return empty();
        }

        /* Same pivot as StructureFormRenderer.calculateRenderInfo / getStructurePivot. */
        float pivotX = (minX + maxX) / 2F;
        float pivotY = minY;
        float pivotZ = (minZ + maxZ) / 2F;
        int widthX = maxX - minX + 1;
        int widthZ = maxZ - minZ + 1;
        float parityX = (widthX % 2 == 1) ? -0.5F : 0F;
        float parityZ = (widthZ % 2 == 1) ? -0.5F : 0F;

        pivotX -= parityX;
        pivotZ -= parityZ;

        List<Box> boxes = new ArrayList<>();
        double boundsMinX = Double.POSITIVE_INFINITY;
        double boundsMinY = Double.POSITIVE_INFINITY;
        double boundsMinZ = Double.POSITIVE_INFINITY;
        double boundsMaxX = Double.NEGATIVE_INFINITY;
        double boundsMaxY = Double.NEGATIVE_INFINITY;
        double boundsMaxZ = Double.NEGATIVE_INFINITY;

        for (Box merged : greedyMergeFullCubes(fullCubes))
        {
            Box local = new Box(
                merged.minX - pivotX,
                merged.minY - pivotY,
                merged.minZ - pivotZ,
                merged.maxX - pivotX,
                merged.maxY - pivotY,
                merged.maxZ - pivotZ
            );

            boxes.add(local);
            boundsMinX = Math.min(boundsMinX, local.minX);
            boundsMinY = Math.min(boundsMinY, local.minY);
            boundsMinZ = Math.min(boundsMinZ, local.minZ);
            boundsMaxX = Math.max(boundsMaxX, local.maxX);
            boundsMaxY = Math.max(boundsMaxY, local.maxY);
            boundsMaxZ = Math.max(boundsMaxZ, local.maxZ);
        }

        for (int i = 0; i < partialPos.size(); i++)
        {
            BlockPos pos = partialPos.get(i);
            BlockState state = partialStates.get(i);
            VoxelShape shape = state.getCollisionShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);

            for (Box part : shape.getBoundingBoxes())
            {
                Box local = new Box(
                    pos.getX() - pivotX + part.minX,
                    pos.getY() - pivotY + part.minY,
                    pos.getZ() - pivotZ + part.minZ,
                    pos.getX() - pivotX + part.maxX,
                    pos.getY() - pivotY + part.maxY,
                    pos.getZ() - pivotZ + part.maxZ
                );

                boxes.add(local);
                boundsMinX = Math.min(boundsMinX, local.minX);
                boundsMinY = Math.min(boundsMinY, local.minY);
                boundsMinZ = Math.min(boundsMinZ, local.minZ);
                boundsMaxX = Math.max(boundsMaxX, local.maxX);
                boundsMaxY = Math.max(boundsMaxY, local.maxY);
                boundsMaxZ = Math.max(boundsMaxZ, local.maxZ);
            }
        }

        if (boxes.isEmpty())
        {
            return empty();
        }

        Box bounds = new Box(boundsMinX, boundsMinY, boundsMinZ, boundsMaxX, boundsMaxY, boundsMaxZ);

        return new StructureCollisionData(boxes, bounds, buildSpatialGrid(boxes));
    }

    private static StructureCollisionData empty()
    {
        return new StructureCollisionData(List.of(), new Box(0, 0, 0, 0, 0, 0), Map.of());
    }

    private static boolean isFullBlockCube(VoxelShape shape)
    {
        List<Box> parts = shape.getBoundingBoxes();

        if (parts.size() != 1)
        {
            return false;
        }

        Box part = parts.get(0);

        return part.minX <= 1.0E-4D && part.minY <= 1.0E-4D && part.minZ <= 1.0E-4D
            && part.maxX >= 1D - 1.0E-4D && part.maxY >= 1D - 1.0E-4D && part.maxZ >= 1D - 1.0E-4D;
    }

    /**
     * Merge adjacent full cubes into large AABBs (greedy meshing in X then Z then Y).
     */
    private static List<Box> greedyMergeFullCubes(Set<Long> fullCubes)
    {
        List<Box> merged = new ArrayList<>();
        Set<Long> remaining = new HashSet<>(fullCubes);

        while (!remaining.isEmpty())
        {
            long key = remaining.iterator().next();
            int x0 = unpackX(key);
            int y0 = unpackY(key);
            int z0 = unpackZ(key);
            int x1 = x0;

            while (remaining.contains(packBlock(x1 + 1, y0, z0)))
            {
                x1++;
            }

            int z1 = z0;

            expandZ:
            while (true)
            {
                for (int x = x0; x <= x1; x++)
                {
                    if (!remaining.contains(packBlock(x, y0, z1 + 1)))
                    {
                        break expandZ;
                    }
                }

                z1++;
            }

            int y1 = y0;

            expandY:
            while (true)
            {
                for (int z = z0; z <= z1; z++)
                {
                    for (int x = x0; x <= x1; x++)
                    {
                        if (!remaining.contains(packBlock(x, y1 + 1, z)))
                        {
                            break expandY;
                        }
                    }
                }

                y1++;
            }

            for (int y = y0; y <= y1; y++)
            {
                for (int z = z0; z <= z1; z++)
                {
                    for (int x = x0; x <= x1; x++)
                    {
                        remaining.remove(packBlock(x, y, z));
                    }
                }
            }

            merged.add(new Box(x0, y0, z0, x1 + 1, y1 + 1, z1 + 1));
        }

        return merged;
    }

    private static Map<Long, int[]> buildSpatialGrid(List<Box> boxes)
    {
        Map<Long, List<Integer>> temp = new HashMap<>();

        for (int i = 0; i < boxes.size(); i++)
        {
            Box box = boxes.get(i);
            int minCX = floorDiv(box.minX, CELL);
            int minCY = floorDiv(box.minY, CELL);
            int minCZ = floorDiv(box.minZ, CELL);
            int maxCX = floorDiv(box.maxX - 1.0E-6D, CELL);
            int maxCY = floorDiv(box.maxY - 1.0E-6D, CELL);
            int maxCZ = floorDiv(box.maxZ - 1.0E-6D, CELL);

            for (int cy = minCY; cy <= maxCY; cy++)
            {
                for (int cz = minCZ; cz <= maxCZ; cz++)
                {
                    for (int cx = minCX; cx <= maxCX; cx++)
                    {
                        temp.computeIfAbsent(packCell(cx, cy, cz), k -> new ArrayList<>()).add(i);
                    }
                }
            }
        }

        Map<Long, int[]> grid = new HashMap<>(temp.size());

        for (Map.Entry<Long, List<Integer>> entry : temp.entrySet())
        {
            List<Integer> list = entry.getValue();
            int[] indices = new int[list.size()];

            for (int i = 0; i < list.size(); i++)
            {
                indices[i] = list.get(i);
            }

            grid.put(entry.getKey(), indices);
        }

        return grid;
    }

    private static int floorDiv(double value, int cell)
    {
        return (int) Math.floor(value / cell);
    }

    private static long packCell(int x, int y, int z)
    {
        return BlockPos.asLong(x, y, z);
    }

    private static long packBlock(int x, int y, int z)
    {
        return BlockPos.asLong(x, y, z);
    }

    private static int unpackX(long key)
    {
        return BlockPos.unpackLongX(key);
    }

    private static int unpackY(long key)
    {
        return BlockPos.unpackLongY(key);
    }

    private static int unpackZ(long key)
    {
        return BlockPos.unpackLongZ(key);
    }

    private static BlockState readBlockState(NbtCompound entry)
    {
        String name = entry.getString("Name");
        Block block;

        try
        {
            block = Registries.BLOCK.get(Identifier.of(name));

            if (block == null)
            {
                block = Blocks.AIR;
            }
        }
        catch (Exception e)
        {
            block = Blocks.AIR;
        }

        if ("minecraft:jigsaw".equals(name) || block == Blocks.JIGSAW)
        {
            return Blocks.AIR.getDefaultState();
        }

        BlockState state = block.getDefaultState();

        if (entry.contains("Properties", NbtElement.COMPOUND_TYPE))
        {
            NbtCompound props = entry.getCompound("Properties");

            for (String key : props.getKeys())
            {
                String value = props.getString(key);
                Property<?> property = block.getStateManager().getProperty(key);

                if (property == null)
                {
                    continue;
                }

                Optional<?> parsed = property.parse(value);

                if (parsed.isPresent())
                {
                    try
                    {
                        @SuppressWarnings({"rawtypes", "unchecked"})
                        Property raw = property;
                        @SuppressWarnings("unchecked")
                        Comparable c = (Comparable) parsed.get();

                        state = state.with(raw, c);
                    }
                    catch (Exception ignored)
                    {}
                }
            }
        }

        return state;
    }
}
