package mchorse.bbs_mod.client;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.StructureForm;
import mchorse.bbs_mod.items.StructurePickerAxis;
import mchorse.bbs_mod.items.StructurePickerExporter;
import mchorse.bbs_mod.items.StructurePickerMode;
import mchorse.bbs_mod.items.StructurePickerPlane;
import mchorse.bbs_mod.items.StructurePickerRegionMerger;
import mchorse.bbs_mod.items.StructurePickerSelection;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.items.UIStructurePickerPanel;
import mchorse.bbs_mod.utils.colors.Colors;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public class StructurePickerClient
{
    private static final int PLANE_LOCK_MOUSE_THRESHOLD_SQ = 16;
    private static final double REACH_MULTIPLIER = 4D;
    private static final double MIN_PICKER_REACH = 128D;

    private static StructurePickerMode mode = StructurePickerMode.CUBE;
    private static final List<Region> regions = new ArrayList<>();
    private static BlockPos firstCorner;
    private static BlockPos secondCorner;
    private static StructurePickerPlane selectionPlane;
    private static boolean depthAdjust;
    private static boolean subtractMode;
    private static StructurePickerAxis depthAxis;
    private static BlockPos slabMin;
    private static BlockPos slabMax;
    private static boolean rightMouseDown;
    private static boolean leftMouseDown;
    private static double planeMouseX;
    private static double planeMouseY;
    private static StructurePickerAxis planeHorizontalAxis;
    private static boolean clickOnAir;
    private static BlockHitResult lastRaycastHit;
    private static BlockPos lastPaintedBlock;
    private static Direction triangleFacing;

    public static StructurePickerMode getMode()
    {
        return StructurePickerClient.mode;
    }

    public static void setMode(StructurePickerMode mode)
    {
        StructurePickerClient.mode = mode;
    }

    public static boolean isSubtractMode()
    {
        return StructurePickerClient.subtractMode;
    }

    public static void setSubtractMode(boolean subtractMode)
    {
        StructurePickerClient.subtractMode = subtractMode;
    }

    public static boolean isClickOnAir()
    {
        return StructurePickerClient.clickOnAir;
    }

    public static void setClickOnAir(boolean clickOnAir)
    {
        StructurePickerClient.clickOnAir = clickOnAir;
    }

    public static Direction getTriangleFacing()
    {
        return StructurePickerClient.triangleFacing;
    }

    public static List<Region> getRegions()
    {
        return StructurePickerClient.regions;
    }

    public static BlockPos getFirstCorner()
    {
        return StructurePickerClient.firstCorner;
    }

    public static BlockPos getSecondCorner()
    {
        return StructurePickerClient.secondCorner;
    }

    public static boolean hasInProgress()
    {
        return StructurePickerClient.firstCorner != null && StructurePickerClient.secondCorner != null;
    }

    public static boolean hasAnySelection()
    {
        return !StructurePickerClient.regions.isEmpty() || StructurePickerClient.hasInProgress();
    }

    public static boolean hasBlockSelection()
    {
        return StructurePickerClient.mode == StructurePickerMode.BLOCK && !StructurePickerClient.regions.isEmpty();
    }

    public static Set<BlockPos> getAllRegionBlocks()
    {
        Set<BlockPos> blocks = new LinkedHashSet<>();

        for (StructurePickerClient.Region region : StructurePickerClient.regions)
        {
            blocks.addAll(StructurePickerSelection.preview(null, region.first(), region.second(), region.mode(), region.triangleFacing()));
        }

        return blocks;
    }

    private static void setRegionsFromBlocks(Set<BlockPos> blocks)
    {
        StructurePickerClient.regions.clear();

        for (StructurePickerRegionMerger.MergedRegion merged : StructurePickerRegionMerger.merge(blocks))
        {
            StructurePickerClient.regions.add(new Region(merged.min(), merged.max(), merged.mode()));
        }
    }

    public static boolean isActive()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null)
        {
            return false;
        }

        ItemStack stack = mc.player.getMainHandStack();

        return stack.getItem() == BBSMod.STRUCTURE_PICKER_ITEM;
    }

    public static ActionResult onUseBlock(BlockHitResult hitResult, boolean sneaking)
    {
        if (!StructurePickerClient.isActive())
        {
            return ActionResult.PASS;
        }

        return ActionResult.SUCCESS;
    }

    public static void openPanel()
    {
        StructurePickerClient.finalizeInProgress();
        UIStructurePickerPanel.open();
    }

    public static ActionResult onAttackBlock()
    {
        if (!StructurePickerClient.isActive())
        {
            return ActionResult.PASS;
        }

        StructurePickerClient.clearSelection();

        return ActionResult.SUCCESS;
    }

    public static void tick(MinecraftClient mc)
    {
        if (mc.world == null || mc.player == null)
        {
            StructurePickerClient.clearSelection();

            return;
        }

        boolean rightDown = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        boolean released = !rightDown && StructurePickerClient.rightMouseDown;
        boolean leftDown = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean leftReleased = !leftDown && StructurePickerClient.leftMouseDown;

        StructurePickerClient.rightMouseDown = rightDown;
        StructurePickerClient.leftMouseDown = leftDown;

        if (UIStructurePickerPanel.isOpened() || mc.currentScreen != null || !StructurePickerClient.isActive())
        {
            return;
        }

        if (mc.player.isSneaking())
        {
            if (released)
            {
                StructurePickerClient.openPanel();
            }

            return;
        }

        if (leftReleased)
        {
            StructurePickerClient.clearSelection();

            return;
        }

        if (StructurePickerClient.mode.isSingleClick())
        {
            if (rightDown)
            {
                StructurePickerClient.updateBlockPaint(mc);
            }
            else if (released)
            {
                StructurePickerClient.lastPaintedBlock = null;
            }

            return;
        }

        if (StructurePickerClient.firstCorner != null && !StructurePickerClient.depthAdjust)
        {
            StructurePickerClient.updatePlaneSelection(mc);
        }

        if (StructurePickerClient.depthAdjust && StructurePickerClient.selectionPlane != null)
        {
            StructurePickerClient.updateDepthSelection(mc);
        }

        if (released)
        {
            StructurePickerClient.handleClick(mc);
        }
    }

    private static void updatePlaneSelection(MinecraftClient mc)
    {
        StructurePickerClient.tryLockPlane(mc);
        StructurePickerClient.ensureSelectionPlane(mc);

        Vec3d look = mc.player.getRotationVec(1.0F);
        BlockPos target = StructurePickerClient.resolvePlaneTarget(mc, look);

        if (target == null)
        {
            return;
        }

        if (StructurePickerClient.selectionPlane == null)
        {
            StructurePickerClient.secondCorner = target.toImmutable();

            return;
        }

        if (StructurePickerClient.selectionPlane == StructurePickerPlane.VERTICAL && StructurePickerClient.planeHorizontalAxis == null)
        {
            StructurePickerClient.planeHorizontalAxis = StructurePickerClient.resolveVerticalPlaneAxis(mc, look);
        }

        StructurePickerClient.secondCorner = StructurePickerClient.selectionPlane.clampSecond(
            StructurePickerClient.firstCorner,
            target,
            StructurePickerClient.planeHorizontalAxis
        );
    }

    private static BlockPos resolvePlaneTarget(MinecraftClient mc, Vec3d look)
    {
        BlockPos hovered = StructurePickerClient.resolveTargetBlock(mc);

        if (hovered != null)
        {
            return hovered;
        }

        if (StructurePickerClient.selectionPlane == null)
        {
            return null;
        }

        return switch (StructurePickerClient.selectionPlane)
        {
            case XZ -> StructurePickerClient.raycastHorizontalPlane(mc, StructurePickerClient.firstCorner.getY() + 0.5D);
            case VERTICAL -> StructurePickerClient.raycastVerticalPlane(mc, StructurePickerClient.firstCorner, StructurePickerClient.planeHorizontalAxis);
        };
    }

    private static BlockPos raycastHorizontalPlane(MinecraftClient mc, double planeY)
    {
        Vec3d eye = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVec(1.0F);

        if (Math.abs(look.y) < 0.001D)
        {
            return null;
        }

        double distance = (planeY - eye.y) / look.y;

        if (distance < 0D)
        {
            return null;
        }

        Vec3d hit = eye.add(look.multiply(distance));

        return BlockPos.ofFloored(hit);
    }

    private static BlockPos raycastVerticalPlane(MinecraftClient mc, BlockPos anchor, StructurePickerAxis lockedHorizontal)
    {
        Vec3d eye = mc.player.getEyePos();
        Vec3d dir = mc.player.getRotationVec(1.0F);

        if (lockedHorizontal == StructurePickerAxis.X)
        {
            if (Math.abs(dir.z) < 0.001D)
            {
                return null;
            }

            double distance = (anchor.getZ() + 0.5D - eye.z) / dir.z;

            if (distance < 0D)
            {
                return null;
            }

            return BlockPos.ofFloored(eye.add(dir.multiply(distance)));
        }

        if (Math.abs(dir.x) < 0.001D)
        {
            return null;
        }

        double distance = (anchor.getX() + 0.5D - eye.x) / dir.x;

        if (distance < 0D)
        {
            return null;
        }

        return BlockPos.ofFloored(eye.add(dir.multiply(distance)));
    }

    private static StructurePickerAxis resolveVerticalPlaneAxis(MinecraftClient mc, Vec3d look)
    {
        StructurePickerAxis along = StructurePickerClient.resolveLookHorizontalAxis(mc, look);

        return along == StructurePickerAxis.X ? StructurePickerAxis.Z : StructurePickerAxis.X;
    }

    private static StructurePickerAxis resolveLookHorizontalAxis(MinecraftClient mc, Vec3d look)
    {
        if (Math.abs(look.x) >= 0.1D || Math.abs(look.z) >= 0.1D)
        {
            return StructurePickerAxis.pickHorizontal(look);
        }

        float yaw = mc.player.getYaw() * ((float) Math.PI / 180F);
        double facingX = -Math.sin(yaw);
        double facingZ = Math.cos(yaw);

        return Math.abs(facingX) >= Math.abs(facingZ) ? StructurePickerAxis.X : StructurePickerAxis.Z;
    }

    private static void applyPlaneFromFace(Direction face)
    {
        switch (face)
        {
            case UP, DOWN -> StructurePickerClient.selectionPlane = StructurePickerPlane.XZ;
            case NORTH, SOUTH ->
            {
                StructurePickerClient.selectionPlane = StructurePickerPlane.VERTICAL;
                StructurePickerClient.planeHorizontalAxis = StructurePickerAxis.X;
            }
            case EAST, WEST ->
            {
                StructurePickerClient.selectionPlane = StructurePickerPlane.VERTICAL;
                StructurePickerClient.planeHorizontalAxis = StructurePickerAxis.Z;
            }
        }
    }

    private static void applyPlaneFromLook(MinecraftClient mc)
    {
        Vec3d look = mc.player.getRotationVec(1.0F);
        double absX = Math.abs(look.x);
        double absY = Math.abs(look.y);
        double absZ = Math.abs(look.z);

        if (absY > absX && absY > absZ)
        {
            StructurePickerClient.selectionPlane = StructurePickerPlane.XZ;
        }
        else
        {
            StructurePickerClient.selectionPlane = StructurePickerPlane.VERTICAL;
            StructurePickerClient.planeHorizontalAxis = StructurePickerClient.resolveVerticalPlaneAxis(mc, look);
        }
    }

    private static void ensureSelectionPlane(MinecraftClient mc)
    {
        if (StructurePickerClient.selectionPlane != null)
        {
            return;
        }

        StructurePickerClient.applyPlaneFromLook(mc);

        if (StructurePickerClient.selectionPlane == StructurePickerPlane.VERTICAL && StructurePickerClient.planeHorizontalAxis == null)
        {
            StructurePickerClient.planeHorizontalAxis = StructurePickerClient.resolveVerticalPlaneAxis(mc, mc.player.getRotationVec(1.0F));
        }
    }

    private static void tryLockPlane(MinecraftClient mc)
    {
        if (StructurePickerClient.selectionPlane != null)
        {
            return;
        }

        double[] cursorX = new double[1];
        double[] cursorY = new double[1];

        GLFW.glfwGetCursorPos(mc.getWindow().getHandle(), cursorX, cursorY);

        double dx = cursorX[0] - StructurePickerClient.planeMouseX;
        double dy = cursorY[0] - StructurePickerClient.planeMouseY;

        if (dx * dx + dy * dy < PLANE_LOCK_MOUSE_THRESHOLD_SQ)
        {
            return;
        }

        StructurePickerClient.selectionPlane = StructurePickerPlane.fromMouseDrag(dx, dy);

        if (StructurePickerClient.selectionPlane == StructurePickerPlane.VERTICAL)
        {
            StructurePickerClient.planeHorizontalAxis = StructurePickerClient.resolveVerticalPlaneAxis(mc, mc.player.getRotationVec(1.0F));
        }
    }

    private static void updateDepthSelection(MinecraftClient mc)
    {
        if (StructurePickerClient.slabMin == null || StructurePickerClient.slabMax == null || StructurePickerClient.selectionPlane == null || StructurePickerClient.depthAxis == null)
        {
            return;
        }

        int depth = StructurePickerClient.resolveDepthCoord(mc);
        BlockPos[] corners = new BlockPos[2];

        StructurePickerClient.selectionPlane.applyDepth(StructurePickerClient.slabMin, StructurePickerClient.slabMax, StructurePickerClient.depthAxis, depth, corners);
        StructurePickerClient.firstCorner = corners[0];
        StructurePickerClient.secondCorner = corners[1];
    }

    private static int resolveDepthCoord(MinecraftClient mc)
    {
        BlockPos hit = StructurePickerClient.resolveTargetBlock(mc);

        if (hit != null)
        {
            return StructurePickerClient.depthAxis.read(hit);
        }

        Vec3d eye = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVec(1.0F);
        double reach = StructurePickerClient.getPickerReach(mc);
        Vec3d end = eye.add(look.multiply(reach));
        Box box = StructurePickerClient.getDepthRayBox(StructurePickerClient.depthAxis, StructurePickerClient.slabMin, StructurePickerClient.slabMax, reach);
        Optional<Vec3d> intersection = box.raycast(eye, end);

        if (intersection.isPresent())
        {
            return StructurePickerClient.depthAxis.read(BlockPos.ofFloored(intersection.get()));
        }

        return Math.min(
            StructurePickerClient.depthAxis.read(StructurePickerClient.slabMin),
            StructurePickerClient.depthAxis.read(StructurePickerClient.slabMax)
        );
    }

    private static Box getDepthRayBox(StructurePickerAxis axis, BlockPos slabMin, BlockPos slabMax, double margin)
    {
        BlockPos min = StructurePickerSelection.min(slabMin, slabMax);
        BlockPos max = StructurePickerSelection.max(slabMin, slabMax);

        return switch (axis)
        {
            case X -> new Box(min.getX() - margin, min.getY(), min.getZ(), max.getX() + margin + 1D, max.getY() + 1D, max.getZ() + 1D);
            case Y -> new Box(min.getX(), min.getY() - margin, min.getZ(), max.getX() + 1D, max.getY() + margin + 1D, max.getZ() + 1D);
            case Z -> new Box(min.getX(), min.getY(), min.getZ() - margin, max.getX() + 1D, max.getY() + 1D, max.getZ() + margin + 1D);
        };
    }

    private static void updateBlockPaint(MinecraftClient mc)
    {
        BlockPos hovered = StructurePickerClient.resolveTargetBlock(mc);

        if (hovered == null)
        {
            return;
        }

        if (StructurePickerClient.lastPaintedBlock != null)
        {
            if (hovered.equals(StructurePickerClient.lastPaintedBlock))
            {
                return;
            }

            for (BlockPos pos : StructurePickerClient.iterateBlockLine(StructurePickerClient.lastPaintedBlock, hovered))
            {
                StructurePickerClient.applyBlockPaint(pos);
            }

            StructurePickerClient.lastPaintedBlock = hovered.toImmutable();

            return;
        }

        StructurePickerClient.applyBlockPaint(hovered);
        StructurePickerClient.lastPaintedBlock = hovered.toImmutable();
    }

    private static void applyBlockPaint(BlockPos pos)
    {
        if (StructurePickerClient.subtractMode)
        {
            StructurePickerClient.applySubtract(pos, pos, StructurePickerMode.BLOCK);
        }
        else if (!StructurePickerClient.isBlockSelected(pos))
        {
            Set<BlockPos> blocks = StructurePickerClient.getAllRegionBlocks();

            blocks.add(pos);
            StructurePickerClient.setRegionsFromBlocks(blocks);
        }
    }

    private static boolean isBlockSelected(BlockPos pos)
    {
        return StructurePickerClient.getAllRegionBlocks().contains(pos);
    }

    private static List<BlockPos> iterateBlockLine(BlockPos from, BlockPos to)
    {
        List<BlockPos> line = new ArrayList<>();
        int x0 = from.getX();
        int y0 = from.getY();
        int z0 = from.getZ();
        int x1 = to.getX();
        int y1 = to.getY();
        int z1 = to.getZ();
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int dz = Math.abs(z1 - z0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int sz = z0 < z1 ? 1 : -1;
        int dm = Math.max(dx, Math.max(dy, dz));

        if (dm == 0)
        {
            line.add(to.toImmutable());

            return line;
        }

        for (int step = 1; step <= dm; step++)
        {
            int x = x0 + (dx == 0 ? 0 : (int) Math.round((double) dx * step / dm) * sx);
            int y = y0 + (dy == 0 ? 0 : (int) Math.round((double) dy * step / dm) * sy);
            int z = z0 + (dz == 0 ? 0 : (int) Math.round((double) dz * step / dm) * sz);

            line.add(new BlockPos(x, y, z));
        }

        return line;
    }

    private static void handleClick(MinecraftClient mc)
    {
        if (StructurePickerClient.depthAdjust)
        {
            StructurePickerClient.commitRegion();

            return;
        }

        BlockPos hovered = StructurePickerClient.resolveTargetBlock(mc);

        if (hovered == null)
        {
            return;
        }

        if (StructurePickerClient.mode.isSingleClick())
        {
            return;
        }

        if (StructurePickerClient.firstCorner == null)
        {
            StructurePickerClient.beginPlaneSelection(mc, hovered);

            return;
        }

        StructurePickerClient.tryLockPlane(mc);

        StructurePickerClient.ensureSelectionPlane(mc);

        if (StructurePickerClient.selectionPlane == StructurePickerPlane.VERTICAL && StructurePickerClient.planeHorizontalAxis == null)
        {
            StructurePickerClient.planeHorizontalAxis = StructurePickerClient.resolveVerticalPlaneAxis(mc, mc.player.getRotationVec(1.0F));
        }

        StructurePickerClient.secondCorner = StructurePickerClient.selectionPlane.clampSecond(
            StructurePickerClient.firstCorner,
            hovered,
            StructurePickerClient.planeHorizontalAxis
        );

        if (StructurePickerClient.mode.isFlat())
        {
            StructurePickerClient.commitRegion();
        }
        else
        {
            StructurePickerClient.beginDepthSelection(mc);
        }
    }

    private static void beginPlaneSelection(MinecraftClient mc, BlockPos hovered)
    {
        StructurePickerClient.firstCorner = hovered.toImmutable();
        StructurePickerClient.secondCorner = hovered.toImmutable();
        StructurePickerClient.selectionPlane = null;
        StructurePickerClient.planeHorizontalAxis = null;
        StructurePickerClient.depthAdjust = false;
        StructurePickerClient.slabMin = null;
        StructurePickerClient.slabMax = null;

        if (StructurePickerClient.mode == StructurePickerMode.TRIANGLE)
        {
            StructurePickerClient.triangleFacing = mc.player.getHorizontalFacing();
        }
        else
        {
            StructurePickerClient.triangleFacing = null;
        }

        if (!StructurePickerClient.mode.isSingleClick())
        {
            if (StructurePickerClient.lastRaycastHit != null && StructurePickerClient.lastRaycastHit.getType() == HitResult.Type.BLOCK)
            {
                StructurePickerClient.applyPlaneFromFace(StructurePickerClient.lastRaycastHit.getSide());
            }
            else
            {
                StructurePickerClient.applyPlaneFromLook(mc);
            }
        }

        double[] cursorX = new double[1];
        double[] cursorY = new double[1];

        GLFW.glfwGetCursorPos(mc.getWindow().getHandle(), cursorX, cursorY);
        StructurePickerClient.planeMouseX = cursorX[0];
        StructurePickerClient.planeMouseY = cursorY[0];
    }

    private static void beginDepthSelection(MinecraftClient mc)
    {
        Vec3d look = mc.player.getRotationVec(1.0F);

        StructurePickerClient.slabMin = StructurePickerSelection.min(StructurePickerClient.firstCorner, StructurePickerClient.secondCorner);
        StructurePickerClient.slabMax = StructurePickerSelection.max(StructurePickerClient.firstCorner, StructurePickerClient.secondCorner);
        StructurePickerClient.depthAdjust = true;
        StructurePickerClient.depthAxis = StructurePickerClient.selectionPlane == StructurePickerPlane.VERTICAL
            ? StructurePickerClient.verticalDepthAxis(StructurePickerClient.planeHorizontalAxis)
            : StructurePickerAxis.Y;

        if (StructurePickerClient.depthAxis == null)
        {
            StructurePickerClient.depthAxis = StructurePickerClient.selectionPlane.defaultDepthAxis(look);
        }

        StructurePickerClient.updateDepthSelection(mc);
    }

    private static StructurePickerAxis verticalDepthAxis(StructurePickerAxis planeLockedHorizontal)
    {
        if (planeLockedHorizontal == StructurePickerAxis.X)
        {
            return StructurePickerAxis.Z;
        }

        if (planeLockedHorizontal == StructurePickerAxis.Z)
        {
            return StructurePickerAxis.X;
        }

        return StructurePickerAxis.Y;
    }

    private static void commitRegion()
    {
        if (StructurePickerClient.hasInProgress())
        {
            if (StructurePickerClient.subtractMode)
            {
                StructurePickerClient.applySubtract(StructurePickerClient.firstCorner, StructurePickerClient.secondCorner, StructurePickerClient.mode);
            }
            else
            {
                StructurePickerClient.regions.add(new Region(
                    StructurePickerClient.firstCorner,
                    StructurePickerClient.secondCorner,
                    StructurePickerClient.mode,
                    StructurePickerClient.triangleFacing
                ));
            }
        }

        StructurePickerClient.clearInProgress();
    }

    private static void applySubtract(BlockPos first, BlockPos second, StructurePickerMode mode)
    {
        Set<BlockPos> removeBlocks = new LinkedHashSet<>(StructurePickerSelection.preview(null, first, second, mode, StructurePickerClient.triangleFacing));
        Set<BlockPos> remaining = new LinkedHashSet<>();
        boolean removedAny = false;

        for (Region region : StructurePickerClient.regions)
        {
            for (BlockPos pos : StructurePickerSelection.preview(null, region.first(), region.second(), region.mode(), region.triangleFacing()))
            {
                if (removeBlocks.contains(pos))
                {
                    removedAny = true;
                }
                else
                {
                    remaining.add(pos);
                }
            }
        }

        if (!removedAny)
        {
            return;
        }

        StructurePickerClient.regions.clear();

        StructurePickerClient.setRegionsFromBlocks(remaining);
    }

    private static void finalizeInProgress()
    {
        if (StructurePickerClient.depthAdjust)
        {
            StructurePickerClient.commitRegion();
        }
        else
        {
            StructurePickerClient.clearInProgress();
        }
    }

    private static void clearInProgress()
    {
        StructurePickerClient.firstCorner = null;
        StructurePickerClient.secondCorner = null;
        StructurePickerClient.selectionPlane = null;
        StructurePickerClient.planeHorizontalAxis = null;
        StructurePickerClient.depthAdjust = false;
        StructurePickerClient.depthAxis = null;
        StructurePickerClient.slabMin = null;
        StructurePickerClient.slabMax = null;
        StructurePickerClient.triangleFacing = null;
    }

    public static void clearSelection()
    {
        StructurePickerClient.regions.clear();
        StructurePickerClient.lastPaintedBlock = null;
        StructurePickerClient.clearInProgress();
    }

    public static Set<BlockPos> getSelectedBlocks(World world)
    {
        Set<BlockPos> blocks = new LinkedHashSet<>();

        for (Region region : StructurePickerClient.regions)
        {
            blocks.addAll(StructurePickerSelection.collect(world, region.first(), region.second(), region.mode(), StructurePickerClient.clickOnAir, region.triangleFacing()));
        }

        return blocks;
    }

    public static Set<BlockPos> getPreviewBlocks()
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        World world = mc.world;
        Set<BlockPos> blocks = new LinkedHashSet<>();

        if (world == null)
        {
            return blocks;
        }

        for (Region region : StructurePickerClient.regions)
        {
            StructurePickerClient.addPreviewBlocks(blocks, world, region.first(), region.second(), region.mode(), region.triangleFacing());
        }

        if (StructurePickerClient.hasInProgress() && !StructurePickerClient.subtractMode)
        {
            StructurePickerClient.addPreviewBlocks(blocks, world, StructurePickerClient.firstCorner, StructurePickerClient.secondCorner, StructurePickerClient.mode, StructurePickerClient.triangleFacing);
        }

        return blocks;
    }

    private static void addPreviewBlocks(Set<BlockPos> blocks, World world, BlockPos first, BlockPos second, StructurePickerMode mode, Direction triangleFacing)
    {
        for (BlockPos pos : StructurePickerSelection.preview(world, first, second, mode, triangleFacing))
        {
            if (StructurePickerClient.clickOnAir || !world.getBlockState(pos).isAir())
            {
                blocks.add(pos);
            }
        }
    }

    public static void importSelection(boolean toModelBlock)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        World world = mc.world;

        if (world == null)
        {
            return;
        }

        List<BlockPos> blocks = new ArrayList<>(StructurePickerClient.getSelectedBlocks(world));

        if (blocks.isEmpty())
        {
            return;
        }

        BlockPos min = blocks.getFirst();
        BlockPos max = blocks.getFirst();

        for (BlockPos pos : blocks)
        {
            min = StructurePickerSelection.min(min, pos);
            max = StructurePickerSelection.max(max, pos);
        }

        BlockPos placement = StructurePickerExporter.getPlacementPos(min, max);

        if (toModelBlock)
        {
            StructurePickerClient.runOnServer(mc, (serverWorld) ->
            {
                String path = StructurePickerExporter.export(serverWorld, blocks);

                if (path != null)
                {
                    StructurePickerExporter.placeModelBlock(serverWorld, placement, path);
                }
            });
        }
        else
        {
            StructurePickerClient.runOnServer(mc, (serverWorld) ->
            {
                String path = StructurePickerExporter.export(serverWorld, blocks);

                if (path != null)
                {
                    mc.execute(() -> StructurePickerClient.importToFilm(path, placement));
                }
            });
        }
    }

    public static void breakSelection()
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        World world = mc.world;

        if (world == null)
        {
            return;
        }

        List<BlockPos> blocks = new ArrayList<>(StructurePickerClient.getSelectedBlocks(world));

        if (!blocks.isEmpty())
        {
            StructurePickerClient.runOnServer(mc, (serverWorld) -> StructurePickerExporter.removeBlocks(serverWorld, blocks));
        }

        StructurePickerClient.clearSelection();
    }

    private static void runOnServer(MinecraftClient mc, Consumer<ServerWorld> task)
    {
        if (mc.getServer() == null || mc.player == null)
        {
            return;
        }

        RegistryKey<World> key = mc.player.getWorld().getRegistryKey();

        mc.getServer().execute(() ->
        {
            ServerWorld serverWorld = mc.getServer().getWorld(key);

            if (serverWorld != null)
            {
                task.accept(serverWorld);
            }
        });
    }

    private static final int HUD_HOTBAR_HEIGHT = 22;
    private static final int HUD_LINE_HEIGHT = 20;
    private static final int HUD_MARGIN_ABOVE_HOTBAR = 6;

    private static int hudLineY(int screenH, int lineIndex)
    {
        return screenH - HUD_HOTBAR_HEIGHT - HUD_MARGIN_ABOVE_HOTBAR - HUD_LINE_HEIGHT * (lineIndex + 1);
    }

    private static void renderHudLine(Batcher2D batcher, int screenW, int screenH, int lineIndex, String text)
    {
        int w = batcher.getFont().getWidth(text) + 12;
        int x = screenW - w - 8;
        int y = StructurePickerClient.hudLineY(screenH, lineIndex);

        batcher.box(x, y, x + w, y + 16, Colors.A50);
        batcher.textShadow(text, x + 6, y + 4);
    }

    public static void renderHud(Batcher2D batcher)
    {
        if (!StructurePickerClient.isActive())
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();
        int lineIndex = 0;

        if (StructurePickerClient.subtractMode && !StructurePickerClient.hasInProgress())
        {
            StructurePickerClient.renderHudLine(batcher, screenW, screenH, lineIndex, UIKeys.STRUCTURE_PICKER_SUBTRACTING.get());
            lineIndex += 1;
        }

        if (!StructurePickerClient.hasInProgress())
        {
            if (StructurePickerClient.mode == StructurePickerMode.BLOCK && StructurePickerClient.hasBlockSelection())
            {
                Set<BlockPos> blocks = StructurePickerClient.getAllRegionBlocks();
                BlockPos blockMin = null;
                BlockPos blockMax = null;

                for (BlockPos pos : blocks)
                {
                    if (blockMin == null)
                    {
                        blockMin = pos;
                        blockMax = pos;
                    }
                    else
                    {
                        blockMin = StructurePickerSelection.min(blockMin, pos);
                        blockMax = StructurePickerSelection.max(blockMax, pos);
                    }
                }

                int width = StructurePickerSelection.spanX(blockMin, blockMax);
                int depth = StructurePickerSelection.spanZ(blockMin, blockMax);
                String modeLabel = UIKeys.STRUCTURE_PICKER_MODE_LABELS[StructurePickerClient.mode.index].get();
                String text = UIKeys.STRUCTURE_PICKER_INTERACTING.format(modeLabel, width, depth, blocks.size()).get();

                StructurePickerClient.renderHudLine(batcher, screenW, screenH, lineIndex, text);
            }

            return;
        }

        BlockPos adjusted = StructurePickerSelection.adjustSecond(StructurePickerClient.firstCorner, StructurePickerClient.secondCorner, StructurePickerClient.mode);
        BlockPos min = StructurePickerSelection.min(StructurePickerClient.firstCorner, adjusted);
        BlockPos max = StructurePickerSelection.max(StructurePickerClient.firstCorner, adjusted);
        int width = StructurePickerSelection.spanX(min, max);
        int depth = StructurePickerSelection.spanZ(min, max);
        int count = StructurePickerClient.getPreviewBlocks().size();
        String modeLabel = UIKeys.STRUCTURE_PICKER_MODE_LABELS[StructurePickerClient.mode.index].get();
        String text = UIKeys.STRUCTURE_PICKER_INTERACTING.format(modeLabel, width, depth, count).get();

        StructurePickerClient.renderHudLine(batcher, screenW, screenH, lineIndex, text);
    }

    private static double getPickerReach(MinecraftClient mc)
    {
        if (StructurePickerClient.clickOnAir)
        {
            return mc.player.getBlockInteractionRange();
        }

        return Math.max(mc.player.getBlockInteractionRange() * REACH_MULTIPLIER, MIN_PICKER_REACH);
    }

    private static double getAirClickReach(MinecraftClient mc)
    {
        return mc.player.getBlockInteractionRange();
    }

    private static BlockPos resolveTargetBlock(MinecraftClient mc)
    {
        BlockHitResult hit = StructurePickerClient.performRaycast(mc, StructurePickerClient.clickOnAir);

        if (hit == null)
        {
            return null;
        }

        if (hit.getType() == HitResult.Type.BLOCK)
        {
            return hit.getBlockPos();
        }

        if (StructurePickerClient.clickOnAir)
        {
            return BlockPos.ofFloored(hit.getPos());
        }

        return null;
    }

    private static BlockHitResult performRaycast(MinecraftClient mc, boolean allowAir)
    {
        StructurePickerClient.lastRaycastHit = null;

        if (mc.player == null || mc.world == null)
        {
            return null;
        }

        Vec3d eye = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVec(1.0F);
        double reach = StructurePickerClient.getPickerReach(mc);
        Vec3d end = eye.add(look.multiply(reach));
        BlockHitResult hit = mc.world.raycast(new RaycastContext(
            eye,
            end,
            RaycastContext.ShapeType.OUTLINE,
            RaycastContext.FluidHandling.NONE,
            mc.player
        ));

        if (hit.getType() == HitResult.Type.BLOCK)
        {
            StructurePickerClient.lastRaycastHit = hit;

            return hit;
        }

        if (allowAir)
        {
            double airReach = StructurePickerClient.getAirClickReach(mc);
            Vec3d airPoint = eye.add(look.multiply(airReach));
            BlockHitResult airHit = BlockHitResult.createMissed(airPoint, Direction.getFacing(look.x, look.y, look.z), BlockPos.ofFloored(airPoint));

            StructurePickerClient.lastRaycastHit = airHit;

            return airHit;
        }

        return null;
    }

    private static BlockPos raycastTarget(MinecraftClient mc, boolean allowAir)
    {
        BlockHitResult hit = StructurePickerClient.performRaycast(mc, allowAir);

        if (hit == null)
        {
            return null;
        }

        if (hit.getType() == HitResult.Type.BLOCK)
        {
            return hit.getBlockPos();
        }

        if (allowAir)
        {
            return BlockPos.ofFloored(hit.getPos());
        }

        return null;
    }

    private static BlockPos raycastBlock(MinecraftClient mc)
    {
        return StructurePickerClient.raycastTarget(mc, false);
    }

    private static void importToFilm(String structurePath, BlockPos placement)
    {
        UIFilmPanel panel = BBSModClient.getDashboard().getPanel(UIFilmPanel.class);

        if (panel == null || panel.getData() == null)
        {
            return;
        }

        StructureForm form = new StructureForm();

        form.structureFile.set(structurePath);

        Film film = panel.getData();
        Replay replay = film.replays.addReplay();

        replay.form.set(FormUtils.copy(form));

        replay.keyframes.x.insert(0, placement.getX() + 0.5D);
        replay.keyframes.y.insert(0, (double) placement.getY());
        replay.keyframes.z.insert(0, placement.getZ() + 0.5D);

        panel.replayEditor.replays.replays.finishImport(replay);
    }

    public record Region(BlockPos first, BlockPos second, StructurePickerMode mode, Direction triangleFacing)
    {
        public Region(BlockPos first, BlockPos second, StructurePickerMode mode)
        {
            this(first, second, mode, null);
        }
    }
}
