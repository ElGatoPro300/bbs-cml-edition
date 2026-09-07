package mchorse.bbs_mod.items;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class StructurePickerSelection
{
    public static BlockPos min(BlockPos a, BlockPos b)
    {
        return new BlockPos(
            Math.min(a.getX(), b.getX()),
            Math.min(a.getY(), b.getY()),
            Math.min(a.getZ(), b.getZ())
        );
    }

    public static BlockPos max(BlockPos a, BlockPos b)
    {
        return new BlockPos(
            Math.max(a.getX(), b.getX()),
            Math.max(a.getY(), b.getY()),
            Math.max(a.getZ(), b.getZ())
        );
    }

    public static int spanX(BlockPos min, BlockPos max)
    {
        return max.getX() - min.getX() + 1;
    }

    public static int spanY(BlockPos min, BlockPos max)
    {
        return max.getY() - min.getY() + 1;
    }

    public static int spanZ(BlockPos min, BlockPos max)
    {
        return max.getZ() - min.getZ() + 1;
    }

    public static StructurePickerPlane inferPlane(BlockPos first, BlockPos second, StructurePickerMode mode)
    {
        if (!mode.isFlat())
        {
            return StructurePickerPlane.XZ;
        }

        BlockPos min = StructurePickerSelection.min(first, second);
        BlockPos max = StructurePickerSelection.max(first, second);

        if (min.getY() == max.getY())
        {
            return StructurePickerPlane.XZ;
        }

        return StructurePickerPlane.VERTICAL;
    }

    public static StructurePickerAxis inferVerticalAxis(BlockPos first, BlockPos second)
    {
        BlockPos min = StructurePickerSelection.min(first, second);
        BlockPos max = StructurePickerSelection.max(first, second);

        if (min.getX() == max.getX())
        {
            return StructurePickerAxis.X;
        }

        if (min.getZ() == max.getZ())
        {
            return StructurePickerAxis.Z;
        }

        return StructurePickerSelection.spanX(min, max) <= StructurePickerSelection.spanZ(min, max)
            ? StructurePickerAxis.X
            : StructurePickerAxis.Z;
    }

    public static BlockPos adjustSecond(BlockPos first, BlockPos second, StructurePickerMode mode)
    {
        return second;
    }

    public static List<BlockPos> collect(World world, BlockPos first, BlockPos second, StructurePickerMode mode)
    {
        return StructurePickerSelection.collect(world, first, second, mode, false);
    }

    public static List<BlockPos> collect(World world, BlockPos first, BlockPos second, StructurePickerMode mode, boolean includeAir)
    {
        return StructurePickerSelection.collect(world, first, second, mode, includeAir, null);
    }

    public static List<BlockPos> collect(World world, BlockPos first, BlockPos second, StructurePickerMode mode, boolean includeAir, Direction triangleFacing)
    {
        BlockPos adjusted = StructurePickerSelection.adjustSecond(first, second, mode);
        BlockPos min = StructurePickerSelection.min(first, adjusted);
        BlockPos max = StructurePickerSelection.max(first, adjusted);
        List<BlockPos> blocks = new ArrayList<>();

        for (int x = min.getX(); x <= max.getX(); x++)
        {
            for (int y = min.getY(); y <= max.getY(); y++)
            {
                for (int z = min.getZ(); z <= max.getZ(); z++)
                {
                    BlockPos pos = new BlockPos(x, y, z);

                    if (StructurePickerSelection.contains(mode, first, adjusted, min, max, x, y, z, triangleFacing) && (includeAir || !world.getBlockState(pos).isAir()))
                    {
                        blocks.add(pos);
                    }
                }
            }
        }

        return blocks;
    }

    public static List<BlockPos> preview(World world, BlockPos first, BlockPos second, StructurePickerMode mode)
    {
        return StructurePickerSelection.preview(world, first, second, mode, null);
    }

    public static List<BlockPos> preview(World world, BlockPos first, BlockPos second, StructurePickerMode mode, Direction triangleFacing)
    {
        if (first == null || second == null)
        {
            return List.of();
        }

        BlockPos adjusted = StructurePickerSelection.adjustSecond(first, second, mode);
        BlockPos min = StructurePickerSelection.min(first, adjusted);
        BlockPos max = StructurePickerSelection.max(first, adjusted);
        List<BlockPos> blocks = new ArrayList<>();

        for (int x = min.getX(); x <= max.getX(); x++)
        {
            for (int y = min.getY(); y <= max.getY(); y++)
            {
                for (int z = min.getZ(); z <= max.getZ(); z++)
                {
                    if (StructurePickerSelection.contains(mode, first, adjusted, min, max, x, y, z, triangleFacing))
                    {
                        blocks.add(new BlockPos(x, y, z));
                    }
                }
            }
        }

        return blocks;
    }

    private static boolean contains(StructurePickerMode mode, BlockPos first, BlockPos second, BlockPos min, BlockPos max, int x, int y, int z, Direction triangleFacing)
    {
        return switch (mode)
        {
            case CUBE -> true;
            case RECTANGLE -> StructurePickerSelection.onFlatPlane(first, second, min, max, x, y, z);
            case TRIANGLE -> StructurePickerSelection.onFlatPlane(first, second, min, max, x, y, z)
                && StructurePickerSelection.inFlatTriangle(first, second, min, max, x, y, z, triangleFacing);
            case CIRCLE -> StructurePickerSelection.onFlatPlane(first, second, min, max, x, y, z)
                && StructurePickerSelection.inFlatCircle(first, second, min, max, x, y, z);
            case CONE -> StructurePickerSelection.inCone(min, max, x, y, z);
            case SPHERE -> StructurePickerSelection.inSphere(min, max, x, y, z);
            case CYLINDER -> StructurePickerSelection.inCircle(min, max, x, z);
            case BLOCK, SAME, BRUSH -> x == min.getX() && y == min.getY() && z == min.getZ();
        };
    }

    /**
     * Stamp a sphere/cube brush on the hit face plane. Radius spreads along the
     * surface; depth goes inward from that face. Only the painted face is selected
     * (e.g. a mountain wall), not the ground or interior volume. Plant covers
     * (grass, flowers, tall grass) also pull in the solid block beneath them.
     */
    public static List<BlockPos> collectBrushSurface(World world, BlockPos center, StructurePickerBrushShape shape, int radius, int depth, Direction face)
    {
        LinkedHashSet<BlockPos> blocks = new LinkedHashSet<>();

        if (world == null || center == null || radius < 0)
        {
            return new ArrayList<>();
        }

        Direction outward = face == null ? Direction.UP : face;
        Direction inward = outward.getOpposite();
        Direction[] tangents = StructurePickerSelection.tangentAxes(outward);
        int r = Math.max(0, radius);
        int layers = Math.max(1, depth);
        int scan = Math.max(1, r) + 2;

        for (int u = -r; u <= r; u++)
        {
            for (int v = -r; v <= r; v++)
            {
                if (shape == StructurePickerBrushShape.SPHERE && u * u + v * v > r * r)
                {
                    continue;
                }

                BlockPos column = center.offset(tangents[0], u).offset(tangents[1], v);
                BlockPos surface = StructurePickerSelection.findFaceSurface(world, column, outward, inward, scan);

                if (surface == null)
                {
                    continue;
                }

                for (int layer = 0; layer < layers; layer++)
                {
                    BlockPos pos = surface.offset(inward, layer);
                    BlockState state = world.getBlockState(pos);

                    if (state.isAir())
                    {
                        break;
                    }

                    blocks.add(pos.toImmutable());
                    StructurePickerSelection.addPlantSupport(world, pos, blocks);
                }
            }
        }

        return new ArrayList<>(blocks);
    }

    private static Direction[] tangentAxes(Direction face)
    {
        return switch (face.getAxis())
        {
            case Y -> new Direction[] {Direction.EAST, Direction.SOUTH};
            case X -> new Direction[] {Direction.UP, Direction.SOUTH};
            case Z -> new Direction[] {Direction.UP, Direction.EAST};
        };
    }

    /**
     * Walk from the air side of {@code column} inward until air meets solid —
     * that solid is the surface facing {@code outward}.
     */
    private static BlockPos findFaceSurface(World world, BlockPos column, Direction outward, Direction inward, int scan)
    {
        BlockPos cursor = column.offset(outward, scan);

        for (int i = 0; i < scan * 2 + 1; i++)
        {
            BlockPos next = cursor.offset(inward);
            boolean cursorEmpty = StructurePickerSelection.isBrushEmpty(world, cursor);
            boolean nextSolid = !StructurePickerSelection.isBrushEmpty(world, next);

            if (cursorEmpty && nextSolid)
            {
                return next.toImmutable();
            }

            cursor = next;
        }

        return null;
    }

    /**
     * Air and non-colliding fluids count as empty for surface finding; plants count
     * as occupied so grass on dirt still forms a selectable surface.
     */
    private static boolean isBrushEmpty(World world, BlockPos pos)
    {
        return world.getBlockState(pos).isAir();
    }

    private static boolean isPlantCover(World world, BlockPos pos, BlockState state)
    {
        if (state.isAir() || state.isSolidBlock(world, pos))
        {
            return false;
        }

        return !state.getFluidState().isStill();
    }

    private static void addPlantSupport(World world, BlockPos pos, Set<BlockPos> out)
    {
        BlockState state = world.getBlockState(pos);

        if (!StructurePickerSelection.isPlantCover(world, pos, state))
        {
            return;
        }

        BlockPos below = pos.down();

        for (int i = 0; i < 4; i++)
        {
            BlockState belowState = world.getBlockState(below);

            if (belowState.isAir())
            {
                return;
            }

            if (!StructurePickerSelection.isPlantCover(world, below, belowState))
            {
                out.add(below.toImmutable());

                return;
            }

            below = below.down();
        }
    }

    public static boolean isSurfaceBlock(World world, BlockPos pos)
    {
        for (Direction direction : Direction.values())
        {
            if (world.getBlockState(pos.offset(direction)).isAir())
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Face-connected flood fill of the same block type (ignores blockstate properties
     * so e.g. rotated logs still connect).
     */
    public static List<BlockPos> collectConnectedSame(World world, BlockPos origin, int maxBlocks)
    {
        List<BlockPos> found = new ArrayList<>();

        if (world == null || origin == null || maxBlocks <= 0)
        {
            return found;
        }

        BlockState originState = world.getBlockState(origin);
        Block match = originState.getBlock();

        if (originState.isAir())
        {
            return found;
        }

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();

        queue.add(origin.toImmutable());
        visited.add(origin.asLong());

        while (!queue.isEmpty() && found.size() < maxBlocks)
        {
            BlockPos pos = queue.removeFirst();

            if (world.getBlockState(pos).getBlock() != match)
            {
                continue;
            }

            found.add(pos);

            for (Direction direction : Direction.values())
            {
                BlockPos next = pos.offset(direction);
                long key = next.asLong();

                if (visited.add(key))
                {
                    queue.add(next.toImmutable());
                }
            }
        }

        return found;
    }

    private static boolean onFlatPlane(BlockPos first, BlockPos second, BlockPos min, BlockPos max, int x, int y, int z)
    {
        StructurePickerPlane plane = StructurePickerSelection.inferPlane(first, second, StructurePickerMode.RECTANGLE);

        if (plane == StructurePickerPlane.XZ)
        {
            return y == min.getY();
        }

        StructurePickerAxis locked = StructurePickerSelection.inferVerticalAxis(first, second);

        if (locked == StructurePickerAxis.Z)
        {
            return z == min.getZ();
        }

        return x == min.getX();
    }

    private static boolean inFlatCircle(BlockPos first, BlockPos second, BlockPos min, BlockPos max, int x, int y, int z)
    {
        int[] extents = StructurePickerSelection.flatExtents(first, second, min, max);

        return StructurePickerSelection.inCircle2D(
            extents[0],
            extents[1],
            extents[2],
            extents[3],
            StructurePickerSelection.flatCoordA(first, second, x, y, z) + 0.5D,
            StructurePickerSelection.flatCoordB(first, second, x, y, z) + 0.5D
        );
    }

    private static boolean inFlatTriangle(BlockPos first, BlockPos second, BlockPos min, BlockPos max, int x, int y, int z, Direction triangleFacing)
    {
        int[] extents = StructurePickerSelection.flatExtents(first, second, min, max);
        double[] forward = StructurePickerSelection.resolveTriangleForward(first, second, triangleFacing);

        return StructurePickerSelection.inEquilateralTriangleOriented(
            extents[0],
            extents[1],
            extents[2],
            extents[3],
            StructurePickerSelection.flatCoordA(first, second, x, y, z) + 0.5D,
            StructurePickerSelection.flatCoordB(first, second, x, y, z) + 0.5D,
            forward[0],
            forward[1]
        );
    }

    private static double[] resolveTriangleForward(BlockPos first, BlockPos second, Direction triangleFacing)
    {
        if (triangleFacing == null)
        {
            return new double[] {0D, 1D};
        }

        StructurePickerPlane plane = StructurePickerSelection.inferPlane(first, second, StructurePickerMode.TRIANGLE);
        double forwardA;
        double forwardB;

        if (plane == StructurePickerPlane.XZ)
        {
            forwardA = triangleFacing.getOffsetX();
            forwardB = triangleFacing.getOffsetZ();
        }
        else
        {
            StructurePickerAxis locked = StructurePickerSelection.inferVerticalAxis(first, second);

            if (locked == StructurePickerAxis.Z)
            {
                forwardA = triangleFacing.getOffsetX();
                forwardB = triangleFacing.getOffsetY();
            }
            else
            {
                forwardA = triangleFacing.getOffsetZ();
                forwardB = triangleFacing.getOffsetY();
            }
        }

        double length = Math.sqrt(forwardA * forwardA + forwardB * forwardB);

        if (length < 0.001D)
        {
            return new double[] {0D, 1D};
        }

        return new double[] {forwardA / length, forwardB / length};
    }

    private static boolean inEquilateralTriangleOriented(int minA, int maxA, int minB, int maxB, double a, double b, double forwardA, double forwardB)
    {
        double rightA = -forwardB;
        double rightB = forwardA;
        double minForward = Double.POSITIVE_INFINITY;
        double maxForward = Double.NEGATIVE_INFINITY;
        double minRight = Double.POSITIVE_INFINITY;
        double maxRight = Double.NEGATIVE_INFINITY;

        for (double cornerA : new double[] {minA, maxA + 1D})
        {
            for (double cornerB : new double[] {minB, maxB + 1D})
            {
                double forward = cornerA * forwardA + cornerB * forwardB;
                double right = cornerA * rightA + cornerB * rightB;

                minForward = Math.min(minForward, forward);
                maxForward = Math.max(maxForward, forward);
                minRight = Math.min(minRight, right);
                maxRight = Math.max(maxRight, right);
            }
        }

        double spanForward = maxForward - minForward;
        double spanRight = maxRight - minRight;

        if (spanForward <= 0D || spanRight <= 0D)
        {
            return false;
        }

        double sqrt3 = Math.sqrt(3D);
        double side = Math.min(spanRight, spanForward * 2D / sqrt3);

        if (side < 1D)
        {
            return false;
        }

        double height = side * sqrt3 / 2D;
        double centerRight = (minRight + maxRight) * 0.5D;
        double right0 = centerRight - side * 0.5D;
        double right1 = right0 + side;
        double baseForward = minForward + 0.5D;
        double apexForward = minForward + height - 0.5D;
        double pointForward = a * forwardA + b * forwardB;
        double pointRight = a * rightA + b * rightB;

        return StructurePickerSelection.pointInTriangle(pointForward, pointRight, baseForward, right0, baseForward, right1, apexForward, centerRight);
    }

    private static int[] flatExtents(BlockPos first, BlockPos second, BlockPos min, BlockPos max)
    {
        StructurePickerPlane plane = StructurePickerSelection.inferPlane(first, second, StructurePickerMode.CIRCLE);

        if (plane == StructurePickerPlane.XZ)
        {
            return new int[] {min.getX(), max.getX(), min.getZ(), max.getZ()};
        }

        StructurePickerAxis locked = StructurePickerSelection.inferVerticalAxis(first, second);

        if (locked == StructurePickerAxis.Z)
        {
            return new int[] {min.getX(), max.getX(), min.getY(), max.getY()};
        }

        return new int[] {min.getZ(), max.getZ(), min.getY(), max.getY()};
    }

    private static double flatCoordA(BlockPos first, BlockPos second, int x, int y, int z)
    {
        StructurePickerPlane plane = StructurePickerSelection.inferPlane(first, second, StructurePickerMode.CIRCLE);

        if (plane == StructurePickerPlane.XZ)
        {
            return x;
        }

        StructurePickerAxis locked = StructurePickerSelection.inferVerticalAxis(first, second);

        if (locked == StructurePickerAxis.Z)
        {
            return x;
        }

        return z;
    }

    private static double flatCoordB(BlockPos first, BlockPos second, int x, int y, int z)
    {
        StructurePickerPlane plane = StructurePickerSelection.inferPlane(first, second, StructurePickerMode.CIRCLE);

        if (plane == StructurePickerPlane.XZ)
        {
            return z;
        }

        return y;
    }

    private static boolean inCircle2D(int minA, int maxA, int minB, int maxB, double a, double b)
    {
        double ca = (minA + maxA + 1D) * 0.5D;
        double cb = (minB + maxB + 1D) * 0.5D;
        double ra = (maxA - minA + 1D) * 0.5D;
        double rb = (maxB - minB + 1D) * 0.5D;

        if (ra <= 0D || rb <= 0D)
        {
            return false;
        }

        double da = (a - ca) / ra;
        double db = (b - cb) / rb;

        return da * da + db * db <= 1D;
    }

    private static boolean inEquilateralTriangle2D(int minA, int maxA, int minB, int maxB, double a, double b)
    {
        return StructurePickerSelection.inEquilateralTriangleOriented(minA, maxA, minB, maxB, a, b, 0D, 1D);
    }

    private static boolean pointInTriangle(double px, double py, double x0, double y0, double x1, double y1, double x2, double y2)
    {
        double d1 = StructurePickerSelection.sign(px, py, x0, y0, x1, y1);
        double d2 = StructurePickerSelection.sign(px, py, x1, y1, x2, y2);
        double d3 = StructurePickerSelection.sign(px, py, x2, y2, x0, y0);
        boolean hasNeg = d1 < 0D || d2 < 0D || d3 < 0D;
        boolean hasPos = d1 > 0D || d2 > 0D || d3 > 0D;

        return !(hasNeg && hasPos);
    }

    private static double sign(double px, double py, double x0, double y0, double x1, double y1)
    {
        return (px - x1) * (y0 - y1) - (x0 - x1) * (py - y1);
    }

    private static boolean inCircle(BlockPos min, BlockPos max, int x, int z)
    {
        return StructurePickerSelection.inCircle2D(min.getX(), max.getX(), min.getZ(), max.getZ(), x + 0.5D, z + 0.5D);
    }

    private static boolean inSphere(BlockPos min, BlockPos max, int x, int y, int z)
    {
        double cx = (min.getX() + max.getX() + 1D) * 0.5D;
        double cy = (min.getY() + max.getY() + 1D) * 0.5D;
        double cz = (min.getZ() + max.getZ() + 1D) * 0.5D;
        double rx = StructurePickerSelection.spanX(min, max) * 0.5D;
        double ry = (max.getY() - min.getY() + 1D) * 0.5D;
        double rz = StructurePickerSelection.spanZ(min, max) * 0.5D;
        double dx = (x + 0.5D - cx) / rx;
        double dy = (y + 0.5D - cy) / ry;
        double dz = (z + 0.5D - cz) / rz;

        return dx * dx + dy * dy + dz * dz <= 1D;
    }

    private static boolean inCone(BlockPos min, BlockPos max, int x, int y, int z)
    {
        int height = max.getY() - min.getY() + 1;

        if (height <= 0)
        {
            return false;
        }

        double cx = (min.getX() + max.getX() + 1D) * 0.5D;
        double cz = (min.getZ() + max.getZ() + 1D) * 0.5D;
        double baseRadius = Math.min(StructurePickerSelection.spanX(min, max), StructurePickerSelection.spanZ(min, max)) * 0.5D;
        double t = (y + 0.5D - min.getY()) / height;
        double radius = baseRadius * (1D - t);
        double dx = x + 0.5D - cx;
        double dz = z + 0.5D - cz;

        return t >= 0D && t <= 1D && dx * dx + dz * dz <= radius * radius;
    }
}
