package mchorse.bbs_mod.client;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.blocks.ModelBlock;
import mchorse.bbs_mod.camera.OrbitCamera;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.StructureForm;
import mchorse.bbs_mod.forms.renderers.StructureFormRenderer;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.items.StructurePickerAxis;
import mchorse.bbs_mod.items.StructurePickerBrushShape;
import mchorse.bbs_mod.items.StructurePickerExporter;
import mchorse.bbs_mod.items.StructurePickerMode;
import mchorse.bbs_mod.items.StructurePickerPlane;
import mchorse.bbs_mod.items.StructurePickerRegionMerger;
import mchorse.bbs_mod.items.StructurePickerSelection;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.items.UIStructurePickerPanel;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.joml.Matrices;

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

import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

public class StructurePickerClient
{
    private static final int PLANE_LOCK_MOUSE_THRESHOLD_SQ = 16;
    private static final double REACH_MULTIPLIER = 4D;
    private static final double MIN_PICKER_REACH = 128D;
    /** Must match StructurePickerRenderer.CORNER_HANDLE — world size before distance scale. */
    private static final float CORNER_HANDLE = 0.28F;
    private static final double GIZMO_AXIS_LENGTH = 2.15D;
    /** Must match StructurePickerRenderer knob / axis half thickness. */
    private static final float GIZMO_KNOB = 0.20F;
    private static final float GIZMO_AXIS_HALF = 0.028F;
    /** Extra pick padding so handles are easy to grab without feeling larger than the preview. */
    private static final double GIZMO_PICK_PAD = 1.15D;
    /** Corner cubes: larger than the preview so they stay clickable from any distance. */
    private static final double CORNER_PICK_PAD = 2.35D;
    private static final double CORNER_PICK_MIN_PIXELS = 52D;
    /** Placement translate gizmo: whole stem + arrow in screen pixels. */
    private static final double GIZMO_STEM_PICK_PIXELS = 48D;
    private static final double GIZMO_TIP_PICK_PIXELS = 56D;
    /** Reference distance where visual handle scale == 1. */
    private static final double HANDLE_REF_DISTANCE = 8D;

    private static StructurePickerMode mode = StructurePickerMode.CUBE;
    private static final List<Region> regions = new ArrayList<>();
    /** Live block set for paint modes — avoids expand+merge on every brush stamp. */
    private static final LongOpenHashSet selectedBlocks = new LongOpenHashSet();
    private static boolean selectedBlocksDirty = true;
    private static boolean regionsNeedCompact;
    /** True when selection was built by Block/Same/Brush paint (many AABB fragments). */
    private static boolean selectionFromPaint;
    private static int selectionVersion;
    private static int lodCacheVersion = -1;
    private static int lodCacheShift = -1;
    private static List<StructurePickerRegionMerger.MergedRegion> lodCacheRegions = List.of();
    private static BlockPos selectionBoundsMin;
    private static BlockPos selectionBoundsMax;
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
    /** True only after LMB went down while already in-world with a locked cursor. */
    private static boolean leftClearArmed;
    private static boolean wasCursorLocked;
    private static double planeMouseX;
    private static double planeMouseY;
    private static StructurePickerAxis planeHorizontalAxis;
    private static boolean clickOnAir;
    private static int sameBlockLimit = 100;
    private static int brushRadius = 2;
    private static int brushDepth = 1;
    private static StructurePickerBrushShape brushShape = StructurePickerBrushShape.SPHERE;
    private static BlockPos brushPreviewHover;
    private static Direction brushPreviewFace;
    private static int brushPreviewRadius = Integer.MIN_VALUE;
    private static int brushPreviewDepth = Integer.MIN_VALUE;
    private static StructurePickerBrushShape brushPreviewShape;
    private static List<StructurePickerRegionMerger.MergedRegion> brushPreviewRegions = List.of();
    private static BlockHitResult lastRaycastHit;
    private static BlockPos lastPaintedBlock;
    private static Direction triangleFacing;

    private static BlockPos modelBlockFlashPos;
    private static long modelBlockFlashUntilMs;

    private static boolean resizeGizmoActive;
    private static int resizeRegionIndex = -1;
    private static BlockPos resizeFreeCorner;
    private static BlockPos resizeFixedCorner;
    private static boolean resizeAnchorIsMax;
    private static StructurePickerAxis resizeDragAxis;
    private static boolean resizeDragging;
    private static int resizeDragOriginCoord;

    private static boolean undoKeyDown;
    private static boolean redoKeyDown;

    private static boolean placementActive;
    private static String placementPath;
    private static BlockPos placementOrigin;
    private static int placementSizeX;
    private static int placementSizeY;
    private static int placementSizeZ;
    private static StructureForm placementPreviewForm;
    private static StructurePickerAxis placementDragAxis;
    private static boolean placementDragging;
    private static int placementDragOriginCoord;
    private static Runnable placementUiListener;
    private static OrbitCamera freecamOrbit;
    /** Structure path bound after Place and Select — Save overwrites this file. */
    private static String boundStructurePath;

    public static StructurePickerMode getMode()
    {
        return StructurePickerClient.mode;
    }

    public static void setPlacementUiListener(Runnable listener)
    {
        StructurePickerClient.placementUiListener = listener;
    }

    private static void notifyPlacementUi()
    {
        if (StructurePickerClient.placementUiListener != null)
        {
            StructurePickerClient.placementUiListener.run();
        }
    }

    public static boolean isPlacementActive()
    {
        return StructurePickerClient.placementActive;
    }

    public static BlockPos getPlacementOrigin()
    {
        return StructurePickerClient.placementOrigin;
    }

    public static void setPlacementOrigin(BlockPos origin)
    {
        if (origin == null)
        {
            return;
        }

        StructurePickerClient.placementOrigin = origin.toImmutable();
        StructurePickerClient.notifyPlacementUi();
    }

    public static void setPlacementOriginCoords(int x, int y, int z)
    {
        StructurePickerClient.setPlacementOrigin(new BlockPos(x, y, z));
    }

    /**
     * Faded StructureForm used for placement ghost preview (VAO), or null.
     */
    public static StructureForm getPlacementPreviewForm()
    {
        return StructurePickerClient.placementPreviewForm;
    }

    /**
     * World-space center of the placement AABB (gizmo pivot).
     */
    public static Vec3d getPlacementGizmoPoint()
    {
        if (!StructurePickerClient.placementActive || StructurePickerClient.placementOrigin == null)
        {
            return null;
        }

        return new Vec3d(
            StructurePickerClient.placementOrigin.getX() + StructurePickerClient.placementSizeX * 0.5D,
            StructurePickerClient.placementOrigin.getY() + StructurePickerClient.placementSizeY * 0.5D,
            StructurePickerClient.placementOrigin.getZ() + StructurePickerClient.placementSizeZ * 0.5D
        );
    }

    public static BlockPos getPlacementMax()
    {
        if (!StructurePickerClient.placementActive || StructurePickerClient.placementOrigin == null)
        {
            return null;
        }

        return StructurePickerClient.placementOrigin.add(
            Math.max(0, StructurePickerClient.placementSizeX - 1),
            Math.max(0, StructurePickerClient.placementSizeY - 1),
            Math.max(0, StructurePickerClient.placementSizeZ - 1)
        );
    }

    public static StructurePickerAxis getPlacementDragAxis()
    {
        if (StructurePickerClient.placementDragAxis != null)
        {
            return StructurePickerClient.placementDragAxis;
        }

        if (!StructurePickerClient.placementActive)
        {
            return null;
        }

        return StructurePickerClient.pickPlacementGizmoAxis(MinecraftClient.getInstance());
    }

    public static void startPlacement(String path)
    {
        if (path == null || path.isEmpty())
        {
            return;
        }

        StructurePickerExporter.TemplateSize size = StructurePickerExporter.getTemplateSize(path);

        if (size.isEmpty())
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        BlockPos origin = StructurePickerClient.defaultPlacementOrigin(mc);

        StructurePickerClient.clearInProgress();
        StructurePickerClient.clearResizeGizmo();
        StructureForm preview = new StructureForm();

        preview.structureFile.set(path);

        Color previewTint = preview.color.get().copy();

        previewTint.a = 0.75F;
        preview.color.set(previewTint);

        StructurePickerClient.placementActive = true;
        StructurePickerClient.placementPath = path;
        StructurePickerClient.placementOrigin = origin;
        StructurePickerClient.placementSizeX = size.x();
        StructurePickerClient.placementSizeY = size.y();
        StructurePickerClient.placementSizeZ = size.z();
        StructurePickerClient.placementPreviewForm = preview;
        StructurePickerClient.placementDragAxis = null;
        StructurePickerClient.placementDragging = false;
        StructurePickerClient.boundStructurePath = path;
        StructurePickerClient.notifyPlacementUi();
    }

    public static void cancelPlacement()
    {
        if (!StructurePickerClient.placementActive)
        {
            return;
        }

        StructurePickerClient.placementActive = false;
        StructurePickerClient.placementPath = null;
        StructurePickerClient.placementOrigin = null;
        StructurePickerClient.placementSizeX = 0;
        StructurePickerClient.placementSizeY = 0;
        StructurePickerClient.placementSizeZ = 0;
        StructurePickerClient.placementPreviewForm = null;
        StructurePickerClient.placementDragAxis = null;
        StructurePickerClient.placementDragging = false;
        StructurePickerClient.notifyPlacementUi();
    }

    public static void confirmPlaceAndSelect()
    {
        if (!StructurePickerClient.placementActive || StructurePickerClient.placementPath == null || StructurePickerClient.placementOrigin == null)
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        String path = StructurePickerClient.placementPath;
        BlockPos origin = StructurePickerClient.placementOrigin.toImmutable();
        List<Region> previousRegions = StructurePickerClient.copyRegions();

        StructurePickerClient.runOnServer(mc, (serverWorld) ->
        {
            StructurePickerExporter.PlaceResult result = StructurePickerExporter.placeStructure(serverWorld, path, origin);

            if (result == null)
            {
                return;
            }

            mc.execute(() ->
            {
                StructurePickerClient.cancelPlacement();
                StructurePickerClient.mode = StructurePickerMode.CUBE;
                StructurePickerClient.restoreRegions(List.of(new Region(result.min(), result.max(), StructurePickerMode.CUBE)));
                StructurePickerClient.boundStructurePath = path;
                StructurePickerClient.ensureCubeScaleGizmo();
                StructurePickerClient.notifySelectionUi();
                StructurePickerHistory.push(new PlaceStructureEntry(path, origin, previousRegions, result.previousBlocks(), result.min(), result.max()));
            });
        });
    }

    public static String getBoundStructurePath()
    {
        return StructurePickerClient.boundStructurePath;
    }

    public static boolean canSaveBoundStructure()
    {
        return StructurePickerClient.hasAnySelection()
            && !StructurePickerClient.isPlacementActive();
    }

    public static void saveBoundStructure()
    {
        StructurePickerClient.saveBoundStructure(null);
    }

    public static void saveBoundStructure(String customName)
    {
        if (!StructurePickerClient.canSaveBoundStructure())
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        World world = mc.world;

        if (world == null)
        {
            return;
        }

        String path = StructurePickerClient.boundStructurePath;
        List<BlockPos> blocks = new ArrayList<>(StructurePickerClient.getSelectedBlocks(world));

        if (blocks.isEmpty())
        {
            return;
        }

        String name = customName == null ? "" : customName.trim();

        StructurePickerClient.runOnServer(mc, (serverWorld) ->
        {
            if (path != null && !path.isEmpty())
            {
                boolean ok = StructurePickerExporter.exportOverwrite(serverWorld, blocks, path);

                if (ok)
                {
                    mc.execute(() ->
                    {
                        StructureFormRenderer.notifyStructureFileChanged();
                        StructurePickerClient.notifySelectionUi();
                    });
                }

                return;
            }

            String exported = StructurePickerExporter.export(serverWorld, blocks, name);

            if (exported != null)
            {
                mc.execute(() ->
                {
                    StructurePickerClient.boundStructurePath = exported;
                    StructureFormRenderer.notifyStructureFileChanged();
                    StructurePickerClient.notifySelectionUi();
                });
            }
        });
    }

    public static void clearBoundStructurePath()
    {
        StructurePickerClient.boundStructurePath = null;
    }

    public static void setFreecamOrbit(OrbitCamera orbit)
    {
        StructurePickerClient.freecamOrbit = orbit;
    }

    private static BlockPos defaultPlacementOrigin(MinecraftClient mc)
    {
        Vec3d eye = StructurePickerClient.getViewEye(mc);
        Vec3d look = StructurePickerClient.getViewLook(mc);
        Vec3d target = eye.add(look.multiply(4.0D));

        return BlockPos.ofFloored(target.x, target.y, target.z);
    }

    private static Vec3d getViewEye(MinecraftClient mc)
    {
        if (StructurePickerClient.freecamOrbit != null && UIStructurePickerPanel.isOpened())
        {
            Vector3d pos = StructurePickerClient.freecamOrbit.position;

            return new Vec3d(pos.x, pos.y, pos.z);
        }

        if (UIStructurePickerPanel.isOpened())
        {
            Vector3d pos = BBSModClient.getCameraController().getPosition();

            return new Vec3d(pos.x, pos.y, pos.z);
        }

        if (mc.player != null)
        {
            return mc.player.getEyePos();
        }

        return Vec3d.ZERO;
    }

    private static Vec3d getViewLook(MinecraftClient mc)
    {
        if (StructurePickerClient.freecamOrbit != null && UIStructurePickerPanel.isOpened())
        {
            Vector3f look = StructurePickerClient.freecamOrbit.getLook();

            return new Vec3d(look.x, look.y, look.z);
        }

        if (UIStructurePickerPanel.isOpened())
        {
            mchorse.bbs_mod.camera.Camera camera = BBSModClient.getCameraController().camera;
            Vector3f look = Matrices.rotation(camera.rotation.x, MathUtils.PI - camera.rotation.y);

            return new Vec3d(look.x, look.y, look.z);
        }

        if (mc.player != null)
        {
            return mc.player.getRotationVec(1.0F);
        }

        return new Vec3d(0.0D, 0.0D, 1.0D);
    }

    /**
     * Eye used for structure picking — freecam when the immersive panel is open.
     */
    private static Vec3d getPickEye(MinecraftClient mc)
    {
        return StructurePickerClient.getViewEye(mc);
    }

    /**
     * Aim used for structure picking. Panel mode aims through the mouse cursor so
     * unlocked-cursor clicks select the block under the pointer. While freecam
     * look-dragging, aim from view center like locked-cursor in-world picking.
     */
    private static Vec3d getPickLook(MinecraftClient mc)
    {
        if (UIStructurePickerPanel.isOpened())
        {
            if (StructurePickerClient.freecamOrbit != null && StructurePickerClient.freecamOrbit.isDragging())
            {
                return StructurePickerClient.getViewLook(mc);
            }

            return StructurePickerClient.getPlacementLook(mc);
        }

        return StructurePickerClient.getViewLook(mc);
    }

    private static Direction getPickHorizontalFacing(MinecraftClient mc)
    {
        if (UIStructurePickerPanel.isOpened())
        {
            Vec3d look = StructurePickerClient.getPickLook(mc);

            return Direction.getFacing(look.x, 0.0D, look.z);
        }

        return mc.player.getHorizontalFacing();
    }

    private static Vec3d getPlacementLook(MinecraftClient mc)
    {
        if (UIStructurePickerPanel.isOpened())
        {
            mchorse.bbs_mod.camera.Camera camera = BBSModClient.getCameraController().camera;

            if (StructurePickerClient.freecamOrbit != null)
            {
                camera.position.set(StructurePickerClient.freecamOrbit.position);
                camera.rotation.set(StructurePickerClient.freecamOrbit.rotation);

                if (StructurePickerClient.freecamOrbit.fov > 0.01F)
                {
                    camera.fov = StructurePickerClient.freecamOrbit.fov;
                }
            }

            int width = mc.getWindow().getWidth();
            int height = mc.getWindow().getHeight();

            if (width > 0 && height > 0)
            {
                Vector3f dir = camera.getMouseDirectionFov(
                    (int) mc.mouse.getX(),
                    (int) mc.mouse.getY(),
                    0,
                    0,
                    width,
                    height
                );

                return new Vec3d(dir.x, dir.y, dir.z);
            }
        }

        return StructurePickerClient.getViewLook(mc);
    }

    public static void setMode(StructurePickerMode mode)
    {
        StructurePickerMode previous = StructurePickerClient.mode;

        StructurePickerClient.mode = mode;
        StructurePickerClient.clearResizeGizmo();
        StructurePickerClient.clearInProgress();
        StructurePickerClient.lastPaintedBlock = null;

        if (previous.isSingleClick() && !mode.isSingleClick())
        {
            StructurePickerClient.compactPaintSelection();
        }

        if (mode != StructurePickerMode.BRUSH)
        {
            StructurePickerClient.clearBrushPreviewCache();
        }
    }

    public static boolean isSelectionFromPaint()
    {
        return StructurePickerClient.selectionFromPaint;
    }

    public static boolean isResizeGizmoActive()
    {
        return StructurePickerClient.resizeGizmoActive && StructurePickerClient.resizeFreeCorner != null;
    }

    public static boolean isScaleDragging()
    {
        return StructurePickerClient.resizeDragging;
    }

    public static boolean isPlacementDragging()
    {
        return StructurePickerClient.placementDragging;
    }

    /**
     * Tight handle hit for freecam ownership — matches the on-screen gizmo/corner preview,
     * not the enlarged corner click target used for selection.
     */
    public static boolean isOverPreciseHandle()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (StructurePickerClient.isPlacementActive())
        {
            return StructurePickerClient.isOverPlacementGizmo();
        }

        if (StructurePickerClient.isResizeGizmoActive()
            && StructurePickerClient.pickSelectionScaleGizmoAxis(mc) != null)
        {
            return true;
        }

        return StructurePickerClient.findPreciseCornerHit(mc) != null;
    }

    public static boolean isSelectionMoveUsingMaxCorner()
    {
        return StructurePickerClient.resizeAnchorIsMax;
    }

    public static boolean isOverSelectionCorner()
    {
        return StructurePickerClient.findSelectionCornerHit(MinecraftClient.getInstance()) != null;
    }

    public static boolean isOverSelectionMoveGizmo()
    {
        return StructurePickerClient.isResizeGizmoActive()
            && StructurePickerClient.pickSelectionScaleGizmoAxis(MinecraftClient.getInstance()) != null;
    }

    /**
     * True when the look ray hits a corner cube or scale-axis gizmo of the active selection.
     */
    public static boolean isOverSelectionInteractable(MinecraftClient mc)
    {
        if (StructurePickerClient.mode != StructurePickerMode.CUBE || !StructurePickerClient.hasActiveCubeSelection())
        {
            return false;
        }

        StructurePickerClient.ensureCubeScaleGizmo();

        return StructurePickerClient.findSelectionCornerHit(mc) != null
            || (StructurePickerClient.isResizeGizmoActive()
                && StructurePickerClient.pickSelectionScaleGizmoAxis(mc) != null);
    }

    public static boolean tryActivateSelectionMoveFromUi()
    {
        if (StructurePickerClient.tryPickCubeCorner(MinecraftClient.getInstance()))
        {
            StructurePickerClient.notifySelectionUi();

            return true;
        }

        StructurePickerClient.ensureCubeScaleGizmo();

        return StructurePickerClient.isResizeGizmoActive()
            && StructurePickerClient.pickSelectionScaleGizmoAxis(MinecraftClient.getInstance()) != null;
    }

    /**
     * Keep a scale gizmo on the latest cube region so handles are always available.
     */
    public static void ensureCubeScaleGizmo()
    {
        if (StructurePickerClient.mode != StructurePickerMode.CUBE || StructurePickerClient.regions.isEmpty())
        {
            StructurePickerClient.clearResizeGizmo();

            return;
        }

        if (StructurePickerClient.resizeGizmoActive
            && StructurePickerClient.resizeRegionIndex >= 0
            && StructurePickerClient.resizeRegionIndex < StructurePickerClient.regions.size()
            && StructurePickerClient.regions.get(StructurePickerClient.resizeRegionIndex).mode() == StructurePickerMode.CUBE)
        {
            StructurePickerClient.syncScaleCornersFromRegion();

            return;
        }

        for (int i = StructurePickerClient.regions.size() - 1; i >= 0; i--)
        {
            if (StructurePickerClient.regions.get(i).mode() == StructurePickerMode.CUBE)
            {
                StructurePickerClient.activateRegionScale(i, true);
                StructurePickerClient.notifySelectionUi();

                return;
            }
        }

        StructurePickerClient.clearResizeGizmo();
    }

    public static boolean hasActiveCubeSelection()
    {
        if (StructurePickerClient.mode != StructurePickerMode.CUBE || StructurePickerClient.selectionFromPaint)
        {
            return false;
        }

        int cubeRegions = 0;

        for (Region region : StructurePickerClient.regions)
        {
            if (region.mode() == StructurePickerMode.CUBE)
            {
                cubeRegions++;
            }
        }

        /* Brush paint stores many tiny CUBE AABBs — only a real cube-tool pick is scalable. */
        return cubeRegions == 1 && StructurePickerClient.regions.size() == 1;
    }

    public static int getActiveSelectionSizeX()
    {
        return StructurePickerClient.getActiveSelectionSize().getX();
    }

    public static int getActiveSelectionSizeY()
    {
        return StructurePickerClient.getActiveSelectionSize().getY();
    }

    public static int getActiveSelectionSizeZ()
    {
        return StructurePickerClient.getActiveSelectionSize().getZ();
    }

    public static void setActiveSelectionSize(int sizeX, int sizeY, int sizeZ)
    {
        StructurePickerClient.ensureCubeScaleGizmo();

        if (!StructurePickerClient.resizeGizmoActive
            || StructurePickerClient.resizeFixedCorner == null
            || StructurePickerClient.resizeFreeCorner == null
            || StructurePickerClient.resizeRegionIndex < 0
            || StructurePickerClient.resizeRegionIndex >= StructurePickerClient.regions.size())
        {
            return;
        }

        sizeX = Math.max(1, sizeX);
        sizeY = Math.max(1, sizeY);
        sizeZ = Math.max(1, sizeZ);

        BlockPos fixed = StructurePickerClient.resizeFixedCorner.toImmutable();
        BlockPos free;

        /* Keep the fixed corner; only the free corner moves (one direction). */
        if (StructurePickerClient.resizeAnchorIsMax)
        {
            free = new BlockPos(
                fixed.getX() + sizeX - 1,
                fixed.getY() + sizeY - 1,
                fixed.getZ() + sizeZ - 1
            );
        }
        else
        {
            free = new BlockPos(
                fixed.getX() - (sizeX - 1),
                fixed.getY() - (sizeY - 1),
                fixed.getZ() - (sizeZ - 1)
            );
        }

        StructurePickerClient.resizeFreeCorner = free.toImmutable();
        StructurePickerClient.regions.set(StructurePickerClient.resizeRegionIndex, new Region(
            free,
            fixed,
            StructurePickerMode.CUBE,
            StructurePickerClient.regions.get(StructurePickerClient.resizeRegionIndex).triangleFacing()
        ));
        StructurePickerClient.notifySelectionUi();
    }

    private static BlockPos getActiveSelectionSize()
    {
        StructurePickerClient.ensureCubeScaleGizmo();

        if (!StructurePickerClient.resizeGizmoActive
            || StructurePickerClient.resizeFreeCorner == null
            || StructurePickerClient.resizeFixedCorner == null)
        {
            return BlockPos.ORIGIN;
        }

        BlockPos min = StructurePickerSelection.min(StructurePickerClient.resizeFreeCorner, StructurePickerClient.resizeFixedCorner);
        BlockPos max = StructurePickerSelection.max(StructurePickerClient.resizeFreeCorner, StructurePickerClient.resizeFixedCorner);

        return new BlockPos(
            StructurePickerSelection.spanX(min, max),
            StructurePickerSelection.spanY(min, max),
            StructurePickerSelection.spanZ(min, max)
        );
    }

    private static void activateRegionScale(int regionIndex, boolean useMaxCorner)
    {
        Region region = StructurePickerClient.regions.get(regionIndex);
        BlockPos min = StructurePickerSelection.min(region.first(), region.second());
        BlockPos max = StructurePickerSelection.max(region.first(), region.second());

        StructurePickerClient.resizeGizmoActive = true;
        StructurePickerClient.resizeRegionIndex = regionIndex;
        StructurePickerClient.resizeFreeCorner = useMaxCorner ? max.toImmutable() : min.toImmutable();
        StructurePickerClient.resizeFixedCorner = useMaxCorner ? min.toImmutable() : max.toImmutable();
        StructurePickerClient.resizeAnchorIsMax = useMaxCorner;
        StructurePickerClient.resizeDragAxis = null;
        StructurePickerClient.resizeDragging = false;
    }

    private static void syncScaleCornersFromRegion()
    {
        if (StructurePickerClient.resizeRegionIndex < 0
            || StructurePickerClient.resizeRegionIndex >= StructurePickerClient.regions.size())
        {
            return;
        }

        Region region = StructurePickerClient.regions.get(StructurePickerClient.resizeRegionIndex);
        BlockPos min = StructurePickerSelection.min(region.first(), region.second());
        BlockPos max = StructurePickerSelection.max(region.first(), region.second());

        if (StructurePickerClient.resizeAnchorIsMax)
        {
            StructurePickerClient.resizeFreeCorner = max.toImmutable();
            StructurePickerClient.resizeFixedCorner = min.toImmutable();
        }
        else
        {
            StructurePickerClient.resizeFreeCorner = min.toImmutable();
            StructurePickerClient.resizeFixedCorner = max.toImmutable();
        }
    }

    public static void clearResizeGizmo()
    {
        StructurePickerClient.resizeGizmoActive = false;
        StructurePickerClient.resizeRegionIndex = -1;
        StructurePickerClient.resizeFreeCorner = null;
        StructurePickerClient.resizeFixedCorner = null;
        StructurePickerClient.resizeAnchorIsMax = false;
        StructurePickerClient.resizeDragAxis = null;
        StructurePickerClient.resizeDragging = false;
    }

    public static BlockPos getResizeFreeCorner()
    {
        return StructurePickerClient.resizeFreeCorner;
    }

    public static StructurePickerAxis getResizeDragAxis()
    {
        if (StructurePickerClient.resizeDragAxis != null)
        {
            return StructurePickerClient.resizeDragAxis;
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.player == null || !StructurePickerClient.resizeGizmoActive)
        {
            return null;
        }

        return StructurePickerClient.pickSelectionScaleGizmoAxis(mc);
    }

    /** Outward axis direction for the active scale corner (max = +, min = -). */
    public static boolean isScaleGizmoPositive()
    {
        return StructurePickerClient.resizeAnchorIsMax;
    }

    public static double getScaleGizmoAxisLength()
    {
        return StructurePickerClient.GIZMO_AXIS_LENGTH;
    }

    /**
     * Keeps corner/gizmo visuals readable from far away (roughly constant on-screen size).
     */
    public static float getHandleVisualScale(double x, double y, double z)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        Vec3d eye = StructurePickerClient.getViewEye(mc);
        double dist = eye.distanceTo(new Vec3d(x, y, z));
        float scale = (float) (dist / StructurePickerClient.HANDLE_REF_DISTANCE);

        return MathUtils.clamp(scale, 0.7F, 7.5F);
    }

    /**
     * World-space pick radius that stays roughly constant in screen pixels.
     */
    private static double screenSpacePickRadius(MinecraftClient mc, Vec3d eye, Vec3d point, double pixels)
    {
        double dist = Math.max(0.35D, eye.distanceTo(point));
        double screenH = Math.max(1, mc.getWindow().getFramebufferHeight());
        double fovDeg = StructurePickerClient.getPickFovDegrees(mc);
        double halfFov = Math.toRadians(fovDeg) * 0.5D;
        double worldPerPixel = (2.0D * dist * Math.tan(halfFov)) / screenH;

        return Math.max(0.22D, worldPerPixel * pixels);
    }

    private static double getPickFovDegrees(MinecraftClient mc)
    {
        if (StructurePickerClient.freecamOrbit != null && UIStructurePickerPanel.isOpened() && StructurePickerClient.freecamOrbit.fov > 0.01F)
        {
            return MathUtils.toDeg(StructurePickerClient.freecamOrbit.fov);
        }

        return mc.options.getFov().getValue().doubleValue();
    }

    /**
     * Pick radius matching the rendered handle size (plus padding).
     */
    private static double visualPickRadius(float visualSize, double pad)
    {
        return Math.max(0.12D, visualSize * 0.5D * pad);
    }

    private static void notifySelectionUi()
    {
        StructurePickerClient.notifyPlacementUi();
    }

    public static void startModelBlockFlash(BlockPos pos)
    {
        StructurePickerClient.modelBlockFlashPos = pos.toImmutable();
        StructurePickerClient.modelBlockFlashUntilMs = System.currentTimeMillis() + 3000L;
    }

    public static BlockPos getModelBlockFlashPos()
    {
        if (StructurePickerClient.modelBlockFlashPos == null)
        {
            return null;
        }

        if (System.currentTimeMillis() > StructurePickerClient.modelBlockFlashUntilMs)
        {
            StructurePickerClient.modelBlockFlashPos = null;

            return null;
        }

        return StructurePickerClient.modelBlockFlashPos;
    }

    public static float getModelBlockFlashAlpha()
    {
        if (StructurePickerClient.getModelBlockFlashPos() == null)
        {
            return 0F;
        }

        long remaining = StructurePickerClient.modelBlockFlashUntilMs - System.currentTimeMillis();
        long elapsed = 3000L - remaining;

        /* Blink roughly 4 times per second */
        return (elapsed / 250L) % 2L == 0L ? 0.95F : 0F;
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

    public static int getSameBlockLimit()
    {
        return StructurePickerClient.sameBlockLimit;
    }

    public static void setSameBlockLimit(int limit)
    {
        StructurePickerClient.sameBlockLimit = MathUtils.clamp(limit, 1, 500);
    }

    public static int getBrushRadius()
    {
        return StructurePickerClient.brushRadius;
    }

    public static void setBrushRadius(int radius)
    {
        int clamped = MathUtils.clamp(radius, 0, 16);

        if (StructurePickerClient.brushRadius != clamped)
        {
            StructurePickerClient.brushRadius = clamped;
            StructurePickerClient.clearBrushPreviewCache();
        }
    }

    public static int getBrushDepth()
    {
        return StructurePickerClient.brushDepth;
    }

    public static void setBrushDepth(int depth)
    {
        int clamped = MathUtils.clamp(depth, 1, 16);

        if (StructurePickerClient.brushDepth != clamped)
        {
            StructurePickerClient.brushDepth = clamped;
            StructurePickerClient.clearBrushPreviewCache();
        }
    }

    public static StructurePickerBrushShape getBrushShape()
    {
        return StructurePickerClient.brushShape;
    }

    public static void setBrushShape(StructurePickerBrushShape shape)
    {
        StructurePickerBrushShape next = shape == null ? StructurePickerBrushShape.SPHERE : shape;

        if (StructurePickerClient.brushShape != next)
        {
            StructurePickerClient.brushShape = next;
            StructurePickerClient.clearBrushPreviewCache();
        }
    }

    public static List<BlockPos> getBrushPreviewBlocks()
    {
        /* Prefer getBrushPreviewRegions() for rendering — this expands only if needed. */
        List<StructurePickerRegionMerger.MergedRegion> regions = StructurePickerClient.getBrushPreviewRegions();

        if (regions.isEmpty())
        {
            return List.of();
        }

        List<BlockPos> blocks = new ArrayList<>();

        for (StructurePickerRegionMerger.MergedRegion region : regions)
        {
            BlockPos min = region.min();
            BlockPos max = region.max();

            for (int x = min.getX(); x <= max.getX(); x++)
            {
                for (int y = min.getY(); y <= max.getY(); y++)
                {
                    for (int z = min.getZ(); z <= max.getZ(); z++)
                    {
                        blocks.add(new BlockPos(x, y, z));
                    }
                }
            }
        }

        return blocks;
    }

    /**
     * Cached merged AABBs for brush hover preview — recomputed only when the
     * hover target or brush settings change.
     */
    public static List<StructurePickerRegionMerger.MergedRegion> getBrushPreviewRegions()
    {
        if (StructurePickerClient.mode != StructurePickerMode.BRUSH)
        {
            StructurePickerClient.clearBrushPreviewCache();

            return List.of();
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.world == null)
        {
            StructurePickerClient.clearBrushPreviewCache();

            return List.of();
        }

        BlockPos hovered = StructurePickerClient.resolveTargetBlock(mc);

        if (hovered == null)
        {
            StructurePickerClient.clearBrushPreviewCache();

            return List.of();
        }

        Direction face = StructurePickerClient.getBrushFace();
        int radius = StructurePickerClient.getBrushRadius();
        int depth = StructurePickerClient.getBrushDepth();
        StructurePickerBrushShape shape = StructurePickerClient.getBrushShape();

        if (hovered.equals(StructurePickerClient.brushPreviewHover)
            && face == StructurePickerClient.brushPreviewFace
            && radius == StructurePickerClient.brushPreviewRadius
            && depth == StructurePickerClient.brushPreviewDepth
            && shape == StructurePickerClient.brushPreviewShape)
        {
            return StructurePickerClient.brushPreviewRegions;
        }

        List<BlockPos> blocks = StructurePickerSelection.collectBrushSurface(
            mc.world,
            hovered,
            shape,
            radius,
            depth,
            face
        );
        List<StructurePickerRegionMerger.MergedRegion> merged = StructurePickerRegionMerger.merge(blocks);

        StructurePickerClient.brushPreviewHover = hovered.toImmutable();
        StructurePickerClient.brushPreviewFace = face;
        StructurePickerClient.brushPreviewRadius = radius;
        StructurePickerClient.brushPreviewDepth = depth;
        StructurePickerClient.brushPreviewShape = shape;
        StructurePickerClient.brushPreviewRegions = merged;

        return merged;
    }

    private static void clearBrushPreviewCache()
    {
        StructurePickerClient.brushPreviewHover = null;
        StructurePickerClient.brushPreviewFace = null;
        StructurePickerClient.brushPreviewRadius = Integer.MIN_VALUE;
        StructurePickerClient.brushPreviewDepth = Integer.MIN_VALUE;
        StructurePickerClient.brushPreviewShape = null;
        StructurePickerClient.brushPreviewRegions = List.of();
    }

    private static Direction getBrushFace()
    {
        if (StructurePickerClient.lastRaycastHit != null)
        {
            return StructurePickerClient.lastRaycastHit.getSide();
        }

        return Direction.UP;
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
        return StructurePickerClient.mode.isSingleClick() && !StructurePickerClient.regions.isEmpty();
    }

    public static Set<BlockPos> getAllRegionBlocks()
    {
        StructurePickerClient.ensureSelectedBlocks();

        Set<BlockPos> blocks = new LinkedHashSet<>(Math.max(16, StructurePickerClient.selectedBlocks.size()));
        LongIterator iterator = StructurePickerClient.selectedBlocks.iterator();

        while (iterator.hasNext())
        {
            blocks.add(BlockPos.fromLong(iterator.nextLong()));
        }

        return blocks;
    }

    public static int getSelectedBlockCount()
    {
        StructurePickerClient.ensureSelectedBlocks();

        return StructurePickerClient.selectedBlocks.size();
    }

    public static BlockPos getSelectionBoundsMin()
    {
        StructurePickerClient.ensureSelectedBlocks();

        return StructurePickerClient.selectionBoundsMin;
    }

    public static BlockPos getSelectionBoundsMax()
    {
        StructurePickerClient.ensureSelectedBlocks();

        return StructurePickerClient.selectionBoundsMax;
    }

    /**
     * Coarse AABBs for large paint selections. {@code null} means use fine {@link #getRegions()}.
     */
    public static List<StructurePickerRegionMerger.MergedRegion> getSelectionLodRegions()
    {
        StructurePickerClient.ensureSelectedBlocks();

        int count = StructurePickerClient.selectedBlocks.size();

        if (count <= 4000 && StructurePickerClient.regions.size() <= 400)
        {
            return null;
        }

        int shift = count > 120000 ? 4 : count > 40000 ? 3 : 2;

        if (StructurePickerClient.lodCacheVersion == StructurePickerClient.selectionVersion
            && StructurePickerClient.lodCacheShift == shift)
        {
            return StructurePickerClient.lodCacheRegions;
        }

        /* While painting, keep the last LOD mesh — rebuilding 100k+ cells every stamp stutters hard. */
        if (StructurePickerClient.lastPaintedBlock != null && !StructurePickerClient.lodCacheRegions.isEmpty())
        {
            return StructurePickerClient.lodCacheRegions;
        }

        LongOpenHashSet cells = new LongOpenHashSet(Math.max(16, count >> (shift + shift)));
        LongIterator iterator = StructurePickerClient.selectedBlocks.iterator();

        while (iterator.hasNext())
        {
            long key = iterator.nextLong();
            int x = BlockPos.unpackLongX(key) >> shift;
            int y = BlockPos.unpackLongY(key) >> shift;
            int z = BlockPos.unpackLongZ(key) >> shift;

            cells.add(BlockPos.asLong(x, y, z));
        }

        int size = 1 << shift;
        List<StructurePickerRegionMerger.MergedRegion> lod = new ArrayList<>(cells.size());
        LongIterator cellIterator = cells.iterator();

        while (cellIterator.hasNext())
        {
            long cell = cellIterator.nextLong();
            int cx = BlockPos.unpackLongX(cell) << shift;
            int cy = BlockPos.unpackLongY(cell) << shift;
            int cz = BlockPos.unpackLongZ(cell) << shift;

            lod.add(new StructurePickerRegionMerger.MergedRegion(
                new BlockPos(cx, cy, cz),
                new BlockPos(cx + size - 1, cy + size - 1, cz + size - 1),
                StructurePickerMode.CUBE
            ));
        }

        StructurePickerClient.lodCacheVersion = StructurePickerClient.selectionVersion;
        StructurePickerClient.lodCacheShift = shift;
        StructurePickerClient.lodCacheRegions = lod;

        return lod;
    }

    private static void ensureSelectedBlocks()
    {
        if (!StructurePickerClient.selectedBlocksDirty)
        {
            return;
        }

        StructurePickerClient.selectedBlocks.clear();
        StructurePickerClient.selectionBoundsMin = null;
        StructurePickerClient.selectionBoundsMax = null;

        for (StructurePickerClient.Region region : StructurePickerClient.regions)
        {
            for (BlockPos pos : StructurePickerSelection.preview(null, region.first(), region.second(), region.mode(), region.triangleFacing()))
            {
                StructurePickerClient.addSelectedBlockKey(pos.asLong());
            }
        }

        StructurePickerClient.selectedBlocksDirty = false;
        StructurePickerClient.selectionVersion++;
        StructurePickerClient.regionsNeedCompact = false;
        StructurePickerClient.lodCacheVersion = -1;
    }

    private static void addSelectedBlockKey(long key)
    {
        if (!StructurePickerClient.selectedBlocks.add(key))
        {
            return;
        }

        int x = BlockPos.unpackLongX(key);
        int y = BlockPos.unpackLongY(key);
        int z = BlockPos.unpackLongZ(key);

        if (StructurePickerClient.selectionBoundsMin == null)
        {
            StructurePickerClient.selectionBoundsMin = new BlockPos(x, y, z);
            StructurePickerClient.selectionBoundsMax = new BlockPos(x, y, z);
        }
        else
        {
            StructurePickerClient.selectionBoundsMin = StructurePickerSelection.min(
                StructurePickerClient.selectionBoundsMin,
                new BlockPos(x, y, z)
            );
            StructurePickerClient.selectionBoundsMax = StructurePickerSelection.max(
                StructurePickerClient.selectionBoundsMax,
                new BlockPos(x, y, z)
            );
        }
    }

    private static void invalidateSelectedBlocks()
    {
        StructurePickerClient.selectedBlocksDirty = true;
        StructurePickerClient.regionsNeedCompact = false;
        StructurePickerClient.selectionVersion++;
        StructurePickerClient.lodCacheVersion = -1;
    }

    private static void markSelectionChanged()
    {
        StructurePickerClient.selectionVersion++;

        /* Invalidate LOD immediately only when not mid-stroke. */
        if (StructurePickerClient.lastPaintedBlock == null)
        {
            StructurePickerClient.lodCacheVersion = -1;
        }
    }

    private static void compactPaintSelection()
    {
        if (!StructurePickerClient.regionsNeedCompact)
        {
            return;
        }

        StructurePickerClient.setRegionsFromBlocks(StructurePickerClient.selectedBlocksToPosSet());
        StructurePickerClient.lodCacheVersion = -1;
    }

    private static void setRegionsFromBlocks(Set<BlockPos> blocks)
    {
        StructurePickerClient.regions.clear();
        StructurePickerClient.selectedBlocks.clear();
        StructurePickerClient.selectionBoundsMin = null;
        StructurePickerClient.selectionBoundsMax = null;

        if (blocks != null)
        {
            for (BlockPos pos : blocks)
            {
                StructurePickerClient.addSelectedBlockKey(pos.asLong());
            }
        }

        for (StructurePickerRegionMerger.MergedRegion merged : StructurePickerRegionMerger.merge(blocks == null ? Set.of() : blocks))
        {
            StructurePickerClient.regions.add(new Region(merged.min(), merged.max(), merged.mode()));
        }

        StructurePickerClient.selectedBlocksDirty = false;
        StructurePickerClient.regionsNeedCompact = false;
        StructurePickerClient.selectionFromPaint = true;
        StructurePickerClient.markSelectionChanged();
    }

    private static Set<BlockPos> selectedBlocksToPosSet()
    {
        Set<BlockPos> blocks = new LinkedHashSet<>(Math.max(16, StructurePickerClient.selectedBlocks.size()));
        LongIterator iterator = StructurePickerClient.selectedBlocks.iterator();

        while (iterator.hasNext())
        {
            blocks.add(BlockPos.fromLong(iterator.nextLong()));
        }

        return blocks;
    }

    private static void appendPaintBlocks(Collection<BlockPos> stamped)
    {
        boolean addedAny = false;
        List<BlockPos> fresh = new ArrayList<>();

        for (BlockPos pos : stamped)
        {
            long key = pos.asLong();

            if (StructurePickerClient.selectedBlocks.add(key))
            {
                StructurePickerClient.addSelectedBlockKeyBoundsOnly(key);
                fresh.add(pos.toImmutable());
                addedAny = true;
            }
        }

        if (!addedAny)
        {
            return;
        }

        for (StructurePickerRegionMerger.MergedRegion merged : StructurePickerRegionMerger.merge(fresh))
        {
            StructurePickerClient.regions.add(new Region(merged.min(), merged.max(), merged.mode()));
        }

        StructurePickerClient.selectedBlocksDirty = false;
        StructurePickerClient.regionsNeedCompact = true;
        StructurePickerClient.selectionFromPaint = true;
        StructurePickerClient.markSelectionChanged();
    }

    private static void addSelectedBlockKeyBoundsOnly(long key)
    {
        int x = BlockPos.unpackLongX(key);
        int y = BlockPos.unpackLongY(key);
        int z = BlockPos.unpackLongZ(key);

        if (StructurePickerClient.selectionBoundsMin == null)
        {
            StructurePickerClient.selectionBoundsMin = new BlockPos(x, y, z);
            StructurePickerClient.selectionBoundsMax = new BlockPos(x, y, z);
        }
        else
        {
            StructurePickerClient.selectionBoundsMin = new BlockPos(
                Math.min(StructurePickerClient.selectionBoundsMin.getX(), x),
                Math.min(StructurePickerClient.selectionBoundsMin.getY(), y),
                Math.min(StructurePickerClient.selectionBoundsMin.getZ(), z)
            );
            StructurePickerClient.selectionBoundsMax = new BlockPos(
                Math.max(StructurePickerClient.selectionBoundsMax.getX(), x),
                Math.max(StructurePickerClient.selectionBoundsMax.getY(), y),
                Math.max(StructurePickerClient.selectionBoundsMax.getZ(), z)
            );
        }
    }

    private static void removePaintBlocks(Collection<BlockPos> stamped)
    {
        StructurePickerClient.ensureSelectedBlocks();
        boolean removedAny = false;

        for (BlockPos pos : stamped)
        {
            if (StructurePickerClient.selectedBlocks.remove(pos.asLong()))
            {
                removedAny = true;
            }
        }

        if (!removedAny)
        {
            return;
        }

        StructurePickerClient.rebuildBoundsFromSelected();
        StructurePickerClient.setRegionsFromBlocks(StructurePickerClient.selectedBlocksToPosSet());
    }

    private static void rebuildBoundsFromSelected()
    {
        StructurePickerClient.selectionBoundsMin = null;
        StructurePickerClient.selectionBoundsMax = null;
        LongIterator iterator = StructurePickerClient.selectedBlocks.iterator();

        while (iterator.hasNext())
        {
            StructurePickerClient.addSelectedBlockKeyBoundsOnly(iterator.nextLong());
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

        /* Let Model Block open its editor while holding the picker. */
        if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK)
        {
            MinecraftClient mc = MinecraftClient.getInstance();

            if (mc.world != null && mc.world.getBlockState(hitResult.getBlockPos()).getBlock() instanceof ModelBlock)
            {
                return ActionResult.PASS;
            }
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

        long window = mc.getWindow().getHandle();
        boolean focused = GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE;

        /* Alt+Tab / click-away: ignore button edges and reset trackers so regaining
         * focus does not look like a left-click clear. */
        if (!focused)
        {
            StructurePickerClient.rightMouseDown = false;
            StructurePickerClient.leftMouseDown = false;
            StructurePickerClient.leftClearArmed = false;
            StructurePickerClient.wasCursorLocked = false;
            StructurePickerClient.undoKeyDown = false;
            StructurePickerClient.redoKeyDown = false;

            return;
        }

        if (StructurePickerClient.isActive() || UIStructurePickerPanel.isOpened())
        {
            StructurePickerClient.tickUndoRedoKeys();
        }
        else
        {
            StructurePickerClient.undoKeyDown = false;
            StructurePickerClient.redoKeyDown = false;
        }

        if (StructurePickerClient.isPlacementActive())
        {
            StructurePickerClient.tickPlacement(mc);
        }
        else if (UIStructurePickerPanel.isOpened())
        {
            StructurePickerClient.tickPanelWorld(mc);
        }
        else if (StructurePickerClient.isActive()
            && StructurePickerClient.mode == StructurePickerMode.CUBE
            && StructurePickerClient.hasActiveCubeSelection())
        {
            StructurePickerClient.ensureCubeScaleGizmo();
        }

        boolean rightDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        boolean pressed = rightDown && !StructurePickerClient.rightMouseDown;
        boolean released = !rightDown && StructurePickerClient.rightMouseDown;
        boolean leftDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean leftPressed = leftDown && !StructurePickerClient.leftMouseDown;
        boolean leftReleased = !leftDown && StructurePickerClient.leftMouseDown;

        /* Panel world tick owns its mouse edges; do not overwrite them here. */
        if (!UIStructurePickerPanel.isOpened() && !StructurePickerClient.isPlacementActive())
        {
            StructurePickerClient.rightMouseDown = rightDown;
            StructurePickerClient.leftMouseDown = leftDown;
        }

        /* Pause menu / any screen: do not track press edges. The click that closes
         * pause would otherwise look like an in-world LMB release and wipe selection. */
        if (UIStructurePickerPanel.isOpened() || mc.currentScreen != null || !StructurePickerClient.isActive() || StructurePickerClient.isPlacementActive())
        {
            if (!UIStructurePickerPanel.isOpened() && !StructurePickerClient.isPlacementActive())
            {
                StructurePickerClient.rightMouseDown = false;
                StructurePickerClient.leftMouseDown = false;
            }

            StructurePickerClient.leftClearArmed = false;
            StructurePickerClient.wasCursorLocked = false;

            if (!UIStructurePickerPanel.isOpened() && !StructurePickerClient.isPlacementActive())
            {
                StructurePickerClient.rightMouseDown = false;
            }

            return;
        }

        boolean cursorLocked = mc.mouse.isCursorLocked();

        if (mc.player.isSneaking())
        {
            if (released)
            {
                StructurePickerClient.openPanel();
            }

            StructurePickerClient.wasCursorLocked = cursorLocked;

            return;
        }

        if (leftPressed && cursorLocked && StructurePickerClient.wasCursorLocked)
        {
            if (StructurePickerClient.isOverSelectionInteractable(mc))
            {
                /* Corner / scale gizmo owns LMB — never clear or start a new region. */
                StructurePickerClient.leftClearArmed = false;

                if (StructurePickerClient.isOverSelectionCorner())
                {
                    StructurePickerClient.tryPickCubeCorner(mc);
                    StructurePickerClient.notifySelectionUi();
                }
            }
            else
            {
                StructurePickerClient.leftClearArmed = true;
            }
        }

        /* Only clear while the cursor is locked in-game. Focus-regain / unpause clicks
         * must not wipe the committed selection. */
        if (leftDown && cursorLocked)
        {
            StructurePickerClient.tickCubeResize(mc, leftPressed, leftReleased);
        }

        if (leftReleased && cursorLocked && StructurePickerClient.leftClearArmed)
        {
            StructurePickerClient.leftClearArmed = false;

            if (Window.isShiftPressed())
            {
                if (StructurePickerClient.tryPickCubeCorner(mc))
                {
                    StructurePickerClient.wasCursorLocked = cursorLocked;

                    return;
                }

                if (StructurePickerClient.resizeGizmoActive)
                {
                    StructurePickerClient.clearResizeGizmo();
                }

                /* Shift+click never clears the whole selection. */
                StructurePickerClient.wasCursorLocked = cursorLocked;

                return;
            }

            if (StructurePickerClient.resizeDragging || StructurePickerClient.isOverSelectionInteractable(mc))
            {
                StructurePickerClient.resizeDragging = false;
                StructurePickerClient.resizeDragAxis = null;
                StructurePickerClient.wasCursorLocked = cursorLocked;

                return;
            }

            StructurePickerClient.clearSelection();
            StructurePickerClient.wasCursorLocked = cursorLocked;

            return;
        }

        if (leftReleased)
        {
            StructurePickerClient.leftClearArmed = false;
            StructurePickerClient.resizeDragging = false;
            StructurePickerClient.resizeDragAxis = null;
        }

        StructurePickerClient.wasCursorLocked = cursorLocked;
        StructurePickerClient.tickRegionClickSelect(mc, rightDown, pressed, released);
    }

    /**
     * Immersive panel: RMB press-hold-drag selects; LMB is freecam only.
     */
    private static void tickPanelWorld(MinecraftClient mc)
    {
        long window = mc.getWindow().getHandle();
        boolean rightDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        boolean pressed = rightDown && !StructurePickerClient.rightMouseDown;
        boolean released = !rightDown && StructurePickerClient.rightMouseDown;
        boolean freecamLook = StructurePickerClient.freecamOrbit != null && StructurePickerClient.freecamOrbit.isDragging();

        StructurePickerClient.rightMouseDown = rightDown;

        if (!freecamLook)
        {
            StructurePickerClient.tickSelectionMove(mc);
        }
        else
        {
            StructurePickerClient.resizeDragging = false;
            StructurePickerClient.resizeDragAxis = null;
        }

        StructurePickerClient.tickRegionDragSelect(mc, rightDown, pressed, released);
    }

    /**
     * In-world item: click once to start, look/move to resize, click again to advance/commit.
     * Holding is not required after the first click.
     */
    private static void tickRegionClickSelect(MinecraftClient mc, boolean rightDown, boolean pressed, boolean released)
    {
        if (StructurePickerClient.mode.isSingleClick())
        {
            if (rightDown)
            {
                StructurePickerClient.updateBlockPaint(mc);
            }
            else if (released)
            {
                StructurePickerClient.lastPaintedBlock = null;
                StructurePickerClient.compactPaintSelection();
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

        if (!pressed)
        {
            return;
        }

        /* Looking at a corner cube or scale gizmo: never start / advance a region select. */
        if (StructurePickerClient.isOverSelectionInteractable(mc))
        {
            return;
        }

        if (StructurePickerClient.depthAdjust)
        {
            StructurePickerClient.commitRegion();

            return;
        }

        if (StructurePickerClient.firstCorner == null)
        {
            BlockPos hovered = StructurePickerClient.resolveTargetBlock(mc);

            if (hovered != null)
            {
                StructurePickerClient.beginPlaneSelection(mc, hovered);
            }

            return;
        }

        StructurePickerClient.finishPlaneDrag(mc);
    }

    /**
     * Hold-RMB region select: press starts, drag updates while held, release advances/commits.
     */
    private static void tickRegionDragSelect(MinecraftClient mc, boolean rightDown, boolean pressed, boolean released)
    {
        if (StructurePickerClient.mode.isSingleClick())
        {
            if (rightDown)
            {
                StructurePickerClient.updateBlockPaint(mc);
            }
            else if (released)
            {
                StructurePickerClient.lastPaintedBlock = null;
                StructurePickerClient.compactPaintSelection();
            }

            return;
        }

        if (pressed && !StructurePickerClient.depthAdjust && StructurePickerClient.firstCorner == null)
        {
            if (StructurePickerClient.isOverSelectionInteractable(mc))
            {
                /* Keep gizmo/corner ownership; do not begin a new region under the handle. */
            }
            else
            {
                BlockPos hovered = StructurePickerClient.resolveTargetBlock(mc);

                if (hovered != null)
                {
                    StructurePickerClient.beginPlaneSelection(mc, hovered);
                }
            }
        }

        if (rightDown)
        {
            if (StructurePickerClient.firstCorner != null && !StructurePickerClient.depthAdjust)
            {
                StructurePickerClient.updatePlaneSelection(mc);
            }

            if (StructurePickerClient.depthAdjust && StructurePickerClient.selectionPlane != null)
            {
                StructurePickerClient.updateDepthSelection(mc);
            }
        }

        if (released)
        {
            if (StructurePickerClient.depthAdjust)
            {
                StructurePickerClient.commitRegion();
            }
            else if (StructurePickerClient.firstCorner != null)
            {
                StructurePickerClient.finishPlaneDrag(mc);
            }
        }
    }

    /**
     * Lock the current plane (flat → commit, volume → depth phase).
     */
    private static void finishPlaneDrag(MinecraftClient mc)
    {
        StructurePickerClient.tryLockPlane(mc);
        StructurePickerClient.ensureSelectionPlane(mc);

        if (StructurePickerClient.selectionPlane == StructurePickerPlane.VERTICAL && StructurePickerClient.planeHorizontalAxis == null)
        {
            StructurePickerClient.planeHorizontalAxis = StructurePickerClient.resolveVerticalPlaneAxis(mc, StructurePickerClient.getPickLook(mc));
        }

        if (StructurePickerClient.secondCorner == null)
        {
            StructurePickerClient.secondCorner = StructurePickerClient.firstCorner.toImmutable();
        }

        if (StructurePickerClient.mode.isFlat())
        {
            StructurePickerClient.commitRegion();
        }
        else
        {
            StructurePickerClient.beginDepthSelection(mc);
        }
    }

    private static void tickPlacement(MinecraftClient mc)
    {
        if (StructurePickerClient.placementOrigin == null)
        {
            return;
        }

        /* Freecam look-drag owns LMB when active. */
        if (StructurePickerClient.freecamOrbit != null && StructurePickerClient.freecamOrbit.isDragging())
        {
            long window = mc.getWindow().getHandle();
            boolean leftDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

            StructurePickerClient.leftMouseDown = leftDown;
            StructurePickerClient.placementDragging = false;
            StructurePickerClient.placementDragAxis = null;

            return;
        }

        long window = mc.getWindow().getHandle();
        boolean leftDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean leftReleased = !leftDown && StructurePickerClient.leftMouseDown;

        StructurePickerClient.leftMouseDown = leftDown;

        if (leftReleased)
        {
            StructurePickerClient.placementDragging = false;
            StructurePickerClient.placementDragAxis = null;

            return;
        }

        if (!leftDown)
        {
            return;
        }

        if (!StructurePickerClient.placementDragging)
        {
            StructurePickerAxis axis = StructurePickerClient.pickPlacementGizmoAxis(mc);

            if (axis == null)
            {
                return;
            }

            StructurePickerClient.placementDragAxis = axis;
            StructurePickerClient.placementDragging = true;

            Vec3d gizmo = StructurePickerClient.getPlacementGizmoPoint();
            Vec3d eye = StructurePickerClient.getViewEye(mc);
            Vec3d look = StructurePickerClient.getPlacementLook(mc);
            Double hit = gizmo == null ? null : StructurePickerClient.projectLookOntoAxis(eye, look, gizmo, axis);

            /* Seed from the click ray so the first held frame does not jump size. */
            if (hit != null)
            {
                StructurePickerClient.placementDragOriginCoord = (int) Math.floor(hit);
            }
            else if (gizmo != null)
            {
                StructurePickerClient.placementDragOriginCoord = (int) Math.floor(axis == StructurePickerAxis.X ? gizmo.x : (axis == StructurePickerAxis.Y ? gizmo.y : gizmo.z));
            }

            return;
        }

        StructurePickerClient.updatePlacementGizmoDrag(mc);
    }

    private static StructurePickerAxis pickPlacementGizmoAxis(MinecraftClient mc)
    {
        return StructurePickerClient.pickAxisGizmo(mc, StructurePickerClient.getPlacementGizmoPoint(), true, true);
    }

    /**
     * Distance-independent axis pick. Placement arrows use large screen-pixel hitboxes
     * along the full stem + tip so the whole visible handle is easy to grab.
     */
    private static StructurePickerAxis pickAxisGizmo(MinecraftClient mc, Vec3d gizmo, boolean positive)
    {
        return StructurePickerClient.pickAxisGizmo(mc, gizmo, positive, false);
    }

    private static StructurePickerAxis pickAxisGizmo(MinecraftClient mc, Vec3d gizmo, boolean positive, boolean arrowTips)
    {
        if (gizmo == null)
        {
            return null;
        }

        /* Screen-pixel stem pick first — whole colored handle, not only the tip cube/arrow. */
        StructurePickerAxis screenHit = StructurePickerClient.pickAxisGizmoScreen(mc, gizmo, positive, arrowTips);

        if (screenHit != null)
        {
            return screenHit;
        }

        Vec3d eye = StructurePickerClient.getViewEye(mc);
        Vec3d look = StructurePickerClient.normalizeLook(UIStructurePickerPanel.isOpened()
            ? StructurePickerClient.getPlacementLook(mc)
            : StructurePickerClient.getViewLook(mc));

        if (look == null)
        {
            return null;
        }

        float visualScale = StructurePickerClient.getHandleVisualScale(gizmo.x, gizmo.y, gizmo.z);
        double axisLength = StructurePickerClient.GIZMO_AXIS_LENGTH * visualScale;
        double tipLength = arrowTips ? axisLength * 0.35D : 0D;
        double tipReach = axisLength + tipLength;
        double tipRadius = StructurePickerClient.screenSpacePickRadius(mc, eye, gizmo, StructurePickerClient.GIZMO_TIP_PICK_PIXELS);
        double axisRadius = StructurePickerClient.screenSpacePickRadius(mc, eye, gizmo, StructurePickerClient.GIZMO_STEM_PICK_PIXELS);
        StructurePickerAxis best = null;
        double bestDist = Double.MAX_VALUE;

        for (StructurePickerAxis axis : StructurePickerAxis.values())
        {
            Vec3d shaftEnd = StructurePickerClient.axisEnd(gizmo, axis, axisLength, positive);
            Vec3d tipApex = tipLength > 0D
                ? StructurePickerClient.axisEnd(gizmo, axis, tipReach, positive)
                : shaftEnd;
            double tipPick = StructurePickerClient.screenSpacePickRadius(mc, eye, tipApex, StructurePickerClient.GIZMO_TIP_PICK_PIXELS);
            double stemPick = Math.max(axisRadius, StructurePickerClient.screenSpacePickRadius(mc, eye, shaftEnd, StructurePickerClient.GIZMO_STEM_PICK_PIXELS));
            double distTip = StructurePickerClient.distanceRayToPoint(eye, look, tipApex);
            double distSeg = StructurePickerClient.distanceRayToSegment(eye, look, gizmo, tipApex);
            double dist = Double.MAX_VALUE;

            if (distTip < tipPick)
            {
                dist = Math.min(dist, distTip * 0.85D);
            }

            if (distSeg < stemPick)
            {
                dist = Math.min(dist, distSeg);
            }

            if (dist < bestDist)
            {
                bestDist = dist;
                best = axis;
            }
        }

        return best;
    }

    /**
     * Pixel-space pick against the drawn stem + tip — matches what the user sees.
     */
    private static StructurePickerAxis pickAxisGizmoScreen(MinecraftClient mc, Vec3d gizmo, boolean positive, boolean arrowTips)
    {
        mchorse.bbs_mod.camera.Camera camera = StructurePickerClient.syncPickCamera(mc);

        if (camera == null)
        {
            return null;
        }

        int width = mc.getWindow().getWidth();
        int height = mc.getWindow().getHeight();

        if (width <= 0 || height <= 0)
        {
            return null;
        }

        camera.updatePerspectiveProjection(width, height);
        camera.updateView();

        double mx = mc.mouse.getX();
        double my = mc.mouse.getY();
        float visualScale = StructurePickerClient.getHandleVisualScale(gizmo.x, gizmo.y, gizmo.z);
        double axisLength = StructurePickerClient.GIZMO_AXIS_LENGTH * visualScale;
        double tipReach = arrowTips ? axisLength * 1.35D : axisLength;
        double[] origin = StructurePickerClient.projectWorldToScreen(camera, gizmo, width, height);

        if (origin == null)
        {
            return null;
        }

        StructurePickerAxis best = null;
        double bestDist = Double.MAX_VALUE;
        double maxPixels = StructurePickerClient.GIZMO_STEM_PICK_PIXELS;

        for (StructurePickerAxis axis : StructurePickerAxis.values())
        {
            Vec3d tipApex = StructurePickerClient.axisEnd(gizmo, axis, tipReach, positive);
            double[] tip = StructurePickerClient.projectWorldToScreen(camera, tipApex, width, height);

            if (tip == null)
            {
                continue;
            }

            double dist = StructurePickerClient.distancePointToSegment2D(mx, my, origin[0], origin[1], tip[0], tip[1]);

            if (dist <= maxPixels && dist < bestDist)
            {
                bestDist = dist;
                best = axis;
            }
        }

        return best;
    }

    private static mchorse.bbs_mod.camera.Camera syncPickCamera(MinecraftClient mc)
    {
        mchorse.bbs_mod.camera.Camera camera = BBSModClient.getCameraController().camera;

        if (StructurePickerClient.freecamOrbit != null && UIStructurePickerPanel.isOpened())
        {
            camera.position.set(StructurePickerClient.freecamOrbit.position);
            camera.rotation.set(StructurePickerClient.freecamOrbit.rotation);

            if (StructurePickerClient.freecamOrbit.fov > 0.01F)
            {
                camera.fov = StructurePickerClient.freecamOrbit.fov;
            }
        }

        return camera;
    }

    private static double[] projectWorldToScreen(mchorse.bbs_mod.camera.Camera camera, Vec3d world, int width, int height)
    {
        Vector3f rel = camera.getRelative(world.x, world.y, world.z);
        Vector4f clip = new Vector4f(rel.x, rel.y, rel.z, 1F);

        camera.view.transform(clip);
        camera.projection.transform(clip);

        if (Math.abs(clip.w) < 1.0E-5F || clip.w < 0F)
        {
            return null;
        }

        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;

        return new double[] {
            (ndcX * 0.5D + 0.5D) * width,
            (1.0D - (ndcY * 0.5D + 0.5D)) * height
        };
    }

    private static double distancePointToSegment2D(double px, double py, double ax, double ay, double bx, double by)
    {
        double abx = bx - ax;
        double aby = by - ay;
        double lenSq = abx * abx + aby * aby;

        if (lenSq < 1.0E-6D)
        {
            double dx = px - ax;
            double dy = py - ay;

            return Math.sqrt(dx * dx + dy * dy);
        }

        double t = ((px - ax) * abx + (py - ay) * aby) / lenSq;

        t = Math.max(0D, Math.min(1D, t));

        double cx = ax + abx * t;
        double cy = ay + aby * t;
        double dx = px - cx;
        double dy = py - cy;

        return Math.sqrt(dx * dx + dy * dy);
    }

    private static Vec3d normalizeLook(Vec3d look)
    {
        double len = look.length();

        if (len < 1.0E-6D)
        {
            return null;
        }

        return look.multiply(1.0D / len);
    }

    public static boolean isOverPlacementGizmo()
    {
        return StructurePickerClient.isPlacementActive()
            && StructurePickerClient.pickPlacementGizmoAxis(MinecraftClient.getInstance()) != null;
    }

    private static void updatePlacementGizmoDrag(MinecraftClient mc)
    {
        if (!StructurePickerClient.placementDragging || StructurePickerClient.placementDragAxis == null || StructurePickerClient.placementOrigin == null)
        {
            return;
        }

        StructurePickerAxis axis = StructurePickerClient.placementDragAxis;
        Vec3d eye = StructurePickerClient.getViewEye(mc);
        Vec3d look = StructurePickerClient.getPlacementLook(mc);
        Vec3d gizmo = StructurePickerClient.getPlacementGizmoPoint();

        if (gizmo == null)
        {
            return;
        }

        Double hit = StructurePickerClient.projectLookOntoAxis(eye, look, gizmo, axis);

        if (hit == null)
        {
            return;
        }

        int newCenter = (int) Math.floor(hit);
        int delta = newCenter - StructurePickerClient.placementDragOriginCoord;

        if (delta == 0)
        {
            return;
        }

        StructurePickerClient.placementDragOriginCoord = newCenter;
        BlockPos next = axis.write(
            StructurePickerClient.placementOrigin,
            axis.read(StructurePickerClient.placementOrigin) + delta
        );

        StructurePickerClient.placementOrigin = next.toImmutable();
        StructurePickerClient.notifyPlacementUi();
    }

    private static void tickSelectionMove(MinecraftClient mc)
    {
        if (StructurePickerClient.mode != StructurePickerMode.CUBE)
        {
            return;
        }

        /* Freecam look-drag owns LMB; do not steal it for scale/corner picks. */
        if (StructurePickerClient.freecamOrbit != null && StructurePickerClient.freecamOrbit.isDragging())
        {
            long window = mc.getWindow().getHandle();
            boolean leftDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

            StructurePickerClient.leftMouseDown = leftDown;
            StructurePickerClient.resizeDragging = false;
            StructurePickerClient.resizeDragAxis = null;

            return;
        }

        StructurePickerClient.ensureCubeScaleGizmo();

        long window = mc.getWindow().getHandle();
        boolean leftDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean leftPressed = leftDown && !StructurePickerClient.leftMouseDown;
        boolean leftReleased = !leftDown && StructurePickerClient.leftMouseDown;

        StructurePickerClient.leftMouseDown = leftDown;

        if (leftReleased)
        {
            StructurePickerClient.resizeDragging = false;
            StructurePickerClient.resizeDragAxis = null;
            StructurePickerClient.notifySelectionUi();

            return;
        }

        if (leftPressed && StructurePickerClient.isOverSelectionCorner() && StructurePickerClient.tryPickCubeCorner(mc))
        {
            StructurePickerClient.notifySelectionUi();

            return;
        }

        if (!StructurePickerClient.resizeGizmoActive || !leftDown)
        {
            return;
        }

        if (!StructurePickerClient.resizeDragging)
        {
            /* Only arm scale on a fresh LMB press — not while holding after a pivot-corner click. */
            if (!leftPressed)
            {
                return;
            }

            StructurePickerAxis axis = StructurePickerClient.pickSelectionScaleGizmoAxis(mc);

            if (axis == null)
            {
                return;
            }

            StructurePickerClient.beginSelectionScaleDrag(mc, axis);

            return;
        }

        StructurePickerClient.updateSelectionScaleDrag(mc);
    }

    private static void tickCubeResize(MinecraftClient mc, boolean leftPressed, boolean leftReleased)
    {
        if (StructurePickerClient.mode != StructurePickerMode.CUBE)
        {
            return;
        }

        StructurePickerClient.ensureCubeScaleGizmo();

        if (!StructurePickerClient.resizeGizmoActive)
        {
            return;
        }

        if (leftReleased)
        {
            StructurePickerClient.resizeDragging = false;
            StructurePickerClient.resizeDragAxis = null;
            StructurePickerClient.notifySelectionUi();

            return;
        }

        if (!StructurePickerClient.leftMouseDown)
        {
            return;
        }

        if (!StructurePickerClient.resizeDragging)
        {
            if (!leftPressed)
            {
                return;
            }

            StructurePickerAxis axis = StructurePickerClient.pickSelectionScaleGizmoAxis(mc);

            if (axis == null)
            {
                return;
            }

            StructurePickerClient.beginSelectionScaleDrag(mc, axis);

            return;
        }

        StructurePickerClient.updateSelectionScaleDrag(mc);
    }

    private record CornerHit(int regionIndex, BlockPos min, BlockPos max, boolean isMax)
    {
    }

    private static CornerHit findSelectionCornerHit(MinecraftClient mc)
    {
        return StructurePickerClient.findCornerHit(mc, true);
    }

    private static CornerHit findPreciseCornerHit(MinecraftClient mc)
    {
        return StructurePickerClient.findCornerHit(mc, false);
    }

    private static CornerHit findCornerHit(MinecraftClient mc, boolean enlarged)
    {
        if (StructurePickerClient.mode != StructurePickerMode.CUBE || StructurePickerClient.regions.isEmpty())
        {
            return null;
        }

        Vec3d eye = StructurePickerClient.getViewEye(mc);
        Vec3d look = UIStructurePickerPanel.isOpened()
            ? StructurePickerClient.getPlacementLook(mc)
            : StructurePickerClient.getViewLook(mc);
        double bestDist = Double.MAX_VALUE;
        CornerHit best = null;

        for (int regionIndex = 0; regionIndex < StructurePickerClient.regions.size(); regionIndex++)
        {
            Region region = StructurePickerClient.regions.get(regionIndex);

            if (region.mode() != StructurePickerMode.CUBE)
            {
                continue;
            }

            BlockPos min = StructurePickerSelection.min(region.first(), region.second());
            BlockPos max = StructurePickerSelection.max(region.first(), region.second());
            Vec3d minCorner = new Vec3d(min.getX(), min.getY(), min.getZ());
            Vec3d maxCorner = new Vec3d(max.getX() + 1, max.getY() + 1, max.getZ() + 1);
            float scaleMin = StructurePickerClient.getHandleVisualScale(minCorner.x, minCorner.y, minCorner.z);
            float scaleMax = StructurePickerClient.getHandleVisualScale(maxCorner.x, maxCorner.y, maxCorner.z);
            double radiusMin;
            double radiusMax;

            if (enlarged)
            {
                radiusMin = Math.max(
                    StructurePickerClient.visualPickRadius(StructurePickerClient.CORNER_HANDLE * scaleMin * 1.18F, StructurePickerClient.CORNER_PICK_PAD),
                    StructurePickerClient.screenSpacePickRadius(mc, eye, minCorner, StructurePickerClient.CORNER_PICK_MIN_PIXELS)
                );
                radiusMax = Math.max(
                    StructurePickerClient.visualPickRadius(StructurePickerClient.CORNER_HANDLE * scaleMax * 1.18F, StructurePickerClient.CORNER_PICK_PAD),
                    StructurePickerClient.screenSpacePickRadius(mc, eye, maxCorner, StructurePickerClient.CORNER_PICK_MIN_PIXELS)
                );
            }
            else
            {
                radiusMin = StructurePickerClient.visualPickRadius(StructurePickerClient.CORNER_HANDLE * scaleMin * 1.18F, StructurePickerClient.GIZMO_PICK_PAD);
                radiusMax = StructurePickerClient.visualPickRadius(StructurePickerClient.CORNER_HANDLE * scaleMax * 1.18F, StructurePickerClient.GIZMO_PICK_PAD);
            }

            double distMin = StructurePickerClient.distanceRayToPoint(eye, look, minCorner);
            double distMax = StructurePickerClient.distanceRayToPoint(eye, look, maxCorner);

            if (distMin < radiusMin && distMin < bestDist)
            {
                bestDist = distMin;
                best = new CornerHit(regionIndex, min, max, false);
            }

            if (distMax < radiusMax && distMax < bestDist)
            {
                bestDist = distMax;
                best = new CornerHit(regionIndex, min, max, true);
            }
        }

        return best;
    }

    private static boolean tryPickCubeCorner(MinecraftClient mc)
    {
        CornerHit hit = StructurePickerClient.findSelectionCornerHit(mc);

        if (hit == null)
        {
            return false;
        }

        StructurePickerClient.resizeGizmoActive = true;
        StructurePickerClient.resizeRegionIndex = hit.regionIndex();
        StructurePickerClient.resizeFreeCorner = hit.isMax() ? hit.max().toImmutable() : hit.min().toImmutable();
        StructurePickerClient.resizeFixedCorner = hit.isMax() ? hit.min().toImmutable() : hit.max().toImmutable();
        StructurePickerClient.resizeAnchorIsMax = hit.isMax();
        StructurePickerClient.resizeDragAxis = null;
        StructurePickerClient.resizeDragging = false;

        return true;
    }

    private static Vec3d getSelectionGizmoPoint()
    {
        if (StructurePickerClient.resizeFreeCorner == null)
        {
            return null;
        }

        if (StructurePickerClient.resizeAnchorIsMax)
        {
            return new Vec3d(
                StructurePickerClient.resizeFreeCorner.getX() + 1,
                StructurePickerClient.resizeFreeCorner.getY() + 1,
                StructurePickerClient.resizeFreeCorner.getZ() + 1
            );
        }

        return new Vec3d(
            StructurePickerClient.resizeFreeCorner.getX(),
            StructurePickerClient.resizeFreeCorner.getY(),
            StructurePickerClient.resizeFreeCorner.getZ()
        );
    }

    private static int readSelectionGizmoCoord(StructurePickerAxis axis)
    {
        Vec3d point = StructurePickerClient.getSelectionGizmoPoint();

        if (point == null)
        {
            return 0;
        }

        return (int) Math.floor(axis == StructurePickerAxis.X ? point.x : (axis == StructurePickerAxis.Y ? point.y : point.z));
    }

    /**
     * Arm a scale drag from the click ray. Size only changes on later frames while dragging.
     */
    private static void beginSelectionScaleDrag(MinecraftClient mc, StructurePickerAxis axis)
    {
        StructurePickerClient.resizeDragAxis = axis;
        StructurePickerClient.resizeDragging = true;

        Vec3d gizmo = StructurePickerClient.getSelectionGizmoPoint();
        Vec3d eye = StructurePickerClient.getViewEye(mc);
        Vec3d look = UIStructurePickerPanel.isOpened()
            ? StructurePickerClient.getPlacementLook(mc)
            : StructurePickerClient.getViewLook(mc);
        Double hit = gizmo == null ? null : StructurePickerClient.projectLookOntoAxis(eye, look, gizmo, axis);

        if (hit != null)
        {
            StructurePickerClient.resizeDragOriginCoord = (int) Math.floor(hit);
        }
        else
        {
            StructurePickerClient.resizeDragOriginCoord = StructurePickerClient.readSelectionGizmoCoord(axis);
        }
    }

    private static StructurePickerAxis pickSelectionScaleGizmoAxis(MinecraftClient mc)
    {
        return StructurePickerClient.pickAxisGizmo(
            mc,
            StructurePickerClient.getSelectionGizmoPoint(),
            StructurePickerClient.isScaleGizmoPositive()
        );
    }

    private static StructurePickerAxis pickSelectionMoveGizmoAxis(MinecraftClient mc)
    {
        return StructurePickerClient.pickSelectionScaleGizmoAxis(mc);
    }

    private static StructurePickerAxis pickGizmoAxis(MinecraftClient mc)
    {
        return StructurePickerClient.pickSelectionScaleGizmoAxis(mc);
    }

    private static void updateSelectionScaleDrag(MinecraftClient mc)
    {
        if (!StructurePickerClient.resizeDragging || StructurePickerClient.resizeDragAxis == null || StructurePickerClient.resizeFreeCorner == null)
        {
            return;
        }

        if (StructurePickerClient.resizeFixedCorner == null
            || StructurePickerClient.resizeRegionIndex < 0
            || StructurePickerClient.resizeRegionIndex >= StructurePickerClient.regions.size())
        {
            return;
        }

        StructurePickerAxis axis = StructurePickerClient.resizeDragAxis;
        Vec3d eye = StructurePickerClient.getViewEye(mc);
        Vec3d look = UIStructurePickerPanel.isOpened()
            ? StructurePickerClient.getPlacementLook(mc)
            : StructurePickerClient.getViewLook(mc);
        Vec3d gizmo = StructurePickerClient.getSelectionGizmoPoint();

        if (gizmo == null)
        {
            return;
        }

        Double hit = StructurePickerClient.projectLookOntoAxis(eye, look, gizmo, axis);

        if (hit == null)
        {
            return;
        }

        int newCoord = (int) Math.floor(hit);
        int delta = newCoord - StructurePickerClient.resizeDragOriginCoord;

        if (delta == 0)
        {
            return;
        }

        BlockPos fixed = StructurePickerClient.resizeFixedCorner;
        int fixedCoord = axis.read(fixed);
        int nextFree = axis.read(StructurePickerClient.resizeFreeCorner) + delta;

        /* Past size 1: flip which corner is free so the dragged handle continues
         * through the opposite face (upper ↔ lower / max ↔ min). */
        boolean flipped = false;

        if (StructurePickerClient.resizeAnchorIsMax)
        {
            if (nextFree < fixedCoord)
            {
                StructurePickerClient.resizeAnchorIsMax = false;
                flipped = true;
            }
        }
        else if (nextFree > fixedCoord)
        {
            StructurePickerClient.resizeAnchorIsMax = true;
            flipped = true;
        }

        BlockPos free = axis.write(StructurePickerClient.resizeFreeCorner, nextFree);

        StructurePickerClient.resizeDragOriginCoord = newCoord;
        StructurePickerClient.resizeFreeCorner = free.toImmutable();

        /* Keep fixed/free aligned with min/max after a flip so gizmo polarity matches. */
        BlockPos min = StructurePickerSelection.min(free, StructurePickerClient.resizeFixedCorner);
        BlockPos max = StructurePickerSelection.max(free, StructurePickerClient.resizeFixedCorner);

        if (StructurePickerClient.resizeAnchorIsMax)
        {
            StructurePickerClient.resizeFreeCorner = max.toImmutable();
            StructurePickerClient.resizeFixedCorner = min.toImmutable();
        }
        else
        {
            StructurePickerClient.resizeFreeCorner = min.toImmutable();
            StructurePickerClient.resizeFixedCorner = max.toImmutable();
        }

        if (flipped)
        {
            /* Re-seed from the new gizmo so the next frame does not jump after teleport. */
            Vec3d gizmoAfter = StructurePickerClient.getSelectionGizmoPoint();
            Double hitAfter = gizmoAfter == null ? null : StructurePickerClient.projectLookOntoAxis(eye, look, gizmoAfter, axis);

            if (hitAfter != null)
            {
                StructurePickerClient.resizeDragOriginCoord = (int) Math.floor(hitAfter);
            }
            else
            {
                StructurePickerClient.resizeDragOriginCoord = StructurePickerClient.readSelectionGizmoCoord(axis);
            }
        }

        StructurePickerClient.regions.set(StructurePickerClient.resizeRegionIndex, new Region(
            StructurePickerClient.resizeFreeCorner,
            StructurePickerClient.resizeFixedCorner,
            StructurePickerMode.CUBE,
            StructurePickerClient.regions.get(StructurePickerClient.resizeRegionIndex).triangleFacing()
        ));
        StructurePickerClient.notifySelectionUi();
    }

    private static void updateSelectionMoveDrag(MinecraftClient mc)
    {
        StructurePickerClient.updateSelectionScaleDrag(mc);
    }

    private static void updateGizmoDrag(MinecraftClient mc)
    {
        StructurePickerClient.updateSelectionScaleDrag(mc);
    }

    private static void applyResizeToRegion()
    {
        /* Scale updates the region directly in updateSelectionScaleDrag. */
    }

    private static Vec3d axisEnd(Vec3d origin, StructurePickerAxis axis, double length)
    {
        return StructurePickerClient.axisEnd(origin, axis, length, true);
    }

    private static Vec3d axisEnd(Vec3d origin, StructurePickerAxis axis, double length, boolean positive)
    {
        double signed = positive ? length : -length;

        return switch (axis)
        {
            case X -> origin.add(signed, 0D, 0D);
            case Y -> origin.add(0D, signed, 0D);
            case Z -> origin.add(0D, 0D, signed);
        };
    }

    private static double distanceRayToPoint(Vec3d eye, Vec3d look, Vec3d point)
    {
        Vec3d toPoint = point.subtract(eye);
        double along = toPoint.dotProduct(look);

        if (along < 0D)
        {
            return Double.MAX_VALUE;
        }

        Vec3d closest = eye.add(look.multiply(along));

        return closest.distanceTo(point);
    }

    private static double distanceRayToSegment(Vec3d eye, Vec3d look, Vec3d a, Vec3d b)
    {
        Vec3d ab = b.subtract(a);
        double abLenSq = ab.lengthSquared();

        if (abLenSq < 1.0E-6D)
        {
            return StructurePickerClient.distanceRayToPoint(eye, look, a);
        }

        /* Closest approach between ray (eye + t*look) and segment (a + u*ab). */
        Vec3d ao = a.subtract(eye);
        double lookDotAb = look.dotProduct(ab);
        double lookDotAo = look.dotProduct(ao);
        double abDotAo = ab.dotProduct(ao);
        double denom = 1D - lookDotAb * lookDotAb / abLenSq;

        if (Math.abs(denom) < 1.0E-6D)
        {
            return StructurePickerClient.distanceRayToPoint(eye, look, a);
        }

        double t = (lookDotAo - lookDotAb * abDotAo / abLenSq) / denom;
        double u = (abDotAo + t * lookDotAb) / abLenSq;

        t = Math.max(0D, t);
        u = Math.max(0D, Math.min(1D, u));

        Vec3d onRay = eye.add(look.multiply(t));
        Vec3d onSeg = a.add(ab.multiply(u));

        return onRay.distanceTo(onSeg);
    }

    private static Double projectLookOntoAxis(Vec3d eye, Vec3d look, Vec3d origin, StructurePickerAxis axis)
    {
        double axisLook = axis.readLook(look);

        if (Math.abs(axisLook) < 0.02D)
        {
            return null;
        }

        /* Intersect look ray with plane through origin perpendicular to camera-forward
         * projected onto the remaining two axes — simpler: use the axis component of
         * the hit on the plane normal to the weaker look components. */
        Vec3d planeNormal;

        if (axis == StructurePickerAxis.Y)
        {
            planeNormal = new Vec3d(look.x, 0D, look.z);

            if (planeNormal.lengthSquared() < 1.0E-6D)
            {
                planeNormal = new Vec3d(1D, 0D, 0D);
            }
            else
            {
                planeNormal = planeNormal.normalize();
            }
        }
        else
        {
            planeNormal = new Vec3d(0D, 1D, 0D);
        }

        double denom = look.dotProduct(planeNormal);

        if (Math.abs(denom) < 1.0E-6D)
        {
            /* Fall back: parameterize by axis look component from eye to origin. */
            double t = (axis.read(BlockPos.ofFloored(origin)) + 0.5D - StructurePickerClient.readVec(eye, axis)) / axisLook;

            if (t < 0D)
            {
                return null;
            }

            return StructurePickerClient.readVec(eye.add(look.multiply(t)), axis);
        }

        double t = origin.subtract(eye).dotProduct(planeNormal) / denom;

        if (t < 0D)
        {
            return null;
        }

        return StructurePickerClient.readVec(eye.add(look.multiply(t)), axis);
    }

    private static double readVec(Vec3d v, StructurePickerAxis axis)
    {
        return switch (axis)
        {
            case X -> v.x;
            case Y -> v.y;
            case Z -> v.z;
        };
    }

    private static void updatePlaneSelection(MinecraftClient mc)
    {
        StructurePickerClient.tryLockPlane(mc);
        StructurePickerClient.ensureSelectionPlane(mc);

        Vec3d look = StructurePickerClient.getPickLook(mc);
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
        Vec3d eye = StructurePickerClient.getPickEye(mc);
        Vec3d look = StructurePickerClient.getPickLook(mc);

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
        Vec3d eye = StructurePickerClient.getPickEye(mc);
        Vec3d dir = StructurePickerClient.getPickLook(mc);

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

        float yaw;

        if (UIStructurePickerPanel.isOpened() && StructurePickerClient.freecamOrbit != null)
        {
            yaw = StructurePickerClient.freecamOrbit.rotation.y;
        }
        else
        {
            yaw = mc.player.getYaw() * ((float) Math.PI / 180F);
        }

        double facingX = -Math.sin(yaw);
        double facingZ = Math.cos(yaw);

        return Math.abs(facingX) >= Math.abs(facingZ) ? StructurePickerAxis.X : StructurePickerAxis.Z;
    }

    private static void ensureSelectionPlane(MinecraftClient mc)
    {
        if (StructurePickerClient.selectionPlane == null)
        {
            StructurePickerClient.tryLockPlane(mc);
        }

        if (StructurePickerClient.selectionPlane == null)
        {
            /* Released with almost no mouse move — default flat ground plane. */
            StructurePickerClient.selectionPlane = StructurePickerPlane.XZ;

            return;
        }

        if (StructurePickerClient.selectionPlane == StructurePickerPlane.VERTICAL && StructurePickerClient.planeHorizontalAxis == null)
        {
            StructurePickerClient.planeHorizontalAxis = StructurePickerClient.resolveVerticalPlaneAxis(mc, StructurePickerClient.getPickLook(mc));
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

        /* First dominant mouse move decides the plane — not the clicked block face. */
        StructurePickerClient.selectionPlane = StructurePickerPlane.fromMouseDrag(dx, dy);

        if (StructurePickerClient.selectionPlane == StructurePickerPlane.VERTICAL)
        {
            StructurePickerClient.planeHorizontalAxis = StructurePickerClient.resolveVerticalPlaneAxis(mc, StructurePickerClient.getPickLook(mc));
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

        Vec3d eye = StructurePickerClient.getPickEye(mc);
        Vec3d look = StructurePickerClient.getPickLook(mc);
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
                if (StructurePickerClient.mode == StructurePickerMode.BRUSH
                    && mc.world != null
                    && mc.world.getBlockState(pos).isAir())
                {
                    continue;
                }

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
        if (StructurePickerClient.mode == StructurePickerMode.SAME)
        {
            StructurePickerClient.applySameBlockPaint(pos);

            return;
        }

        if (StructurePickerClient.mode == StructurePickerMode.BRUSH)
        {
            StructurePickerClient.applyBrushPaint(pos);

            return;
        }

        if (StructurePickerClient.subtractMode)
        {
            StructurePickerClient.applySubtract(pos, pos, StructurePickerMode.BLOCK);
        }
        else
        {
            StructurePickerClient.ensureSelectedBlocks();
            StructurePickerClient.appendPaintBlocks(List.of(pos));
        }
    }

    private static void applyBrushPaint(BlockPos origin)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        World world = mc.world;

        if (world == null)
        {
            return;
        }

        List<BlockPos> stamped = StructurePickerSelection.collectBrushSurface(
            world,
            origin,
            StructurePickerClient.getBrushShape(),
            StructurePickerClient.getBrushRadius(),
            StructurePickerClient.getBrushDepth(),
            StructurePickerClient.getBrushFace()
        );

        if (stamped.isEmpty())
        {
            return;
        }

        StructurePickerClient.ensureSelectedBlocks();

        if (StructurePickerClient.subtractMode)
        {
            StructurePickerClient.removePaintBlocks(stamped);

            return;
        }

        StructurePickerClient.appendPaintBlocks(stamped);
    }

    private static void applySameBlockPaint(BlockPos origin)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        World world = mc.world;

        if (world == null)
        {
            return;
        }

        List<BlockPos> connected = StructurePickerSelection.collectConnectedSame(world, origin, StructurePickerClient.getSameBlockLimit());

        if (connected.isEmpty())
        {
            return;
        }

        StructurePickerClient.ensureSelectedBlocks();

        if (StructurePickerClient.subtractMode)
        {
            StructurePickerClient.removePaintBlocks(connected);

            return;
        }

        StructurePickerClient.appendPaintBlocks(connected);
    }

    private static boolean isBlockSelected(BlockPos pos)
    {
        StructurePickerClient.ensureSelectedBlocks();

        return StructurePickerClient.selectedBlocks.contains(pos.asLong());
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
            StructurePickerClient.triangleFacing = StructurePickerClient.getPickHorizontalFacing(mc);
        }
        else
        {
            StructurePickerClient.triangleFacing = null;
        }

        /* Plane stays unlocked until the first mouse move (horizontal → XZ, vertical → wall). */

        double[] cursorX = new double[1];
        double[] cursorY = new double[1];

        GLFW.glfwGetCursorPos(mc.getWindow().getHandle(), cursorX, cursorY);
        StructurePickerClient.planeMouseX = cursorX[0];
        StructurePickerClient.planeMouseY = cursorY[0];
    }

    private static void beginDepthSelection(MinecraftClient mc)
    {
        Vec3d look = StructurePickerClient.getPickLook(mc);

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
                boolean wasEmpty = StructurePickerClient.regions.isEmpty();

                StructurePickerClient.regions.add(new Region(
                    StructurePickerClient.firstCorner,
                    StructurePickerClient.secondCorner,
                    StructurePickerClient.mode,
                    StructurePickerClient.triangleFacing
                ));

                if (wasEmpty)
                {
                    StructurePickerClient.selectionFromPaint = false;
                }

                StructurePickerClient.invalidateSelectedBlocks();
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
        StructurePickerClient.clearSelection(true);
    }

    private static void clearSelection(boolean clearBoundPath)
    {
        StructurePickerClient.regions.clear();
        StructurePickerClient.selectedBlocks.clear();
        StructurePickerClient.selectionBoundsMin = null;
        StructurePickerClient.selectionBoundsMax = null;
        StructurePickerClient.selectedBlocksDirty = false;
        StructurePickerClient.regionsNeedCompact = false;
        StructurePickerClient.selectionFromPaint = false;
        StructurePickerClient.markSelectionChanged();
        StructurePickerClient.lastPaintedBlock = null;
        StructurePickerClient.clearInProgress();
        StructurePickerClient.clearResizeGizmo();

        if (clearBoundPath)
        {
            StructurePickerClient.boundStructurePath = null;
        }

        StructurePickerClient.notifySelectionUi();
    }

    public static void removeSelection()
    {
        if (StructurePickerClient.regions.isEmpty() && !StructurePickerClient.hasInProgress())
        {
            return;
        }

        List<Region> previous = StructurePickerClient.copyRegions();

        StructurePickerClient.clearSelection(true);
        StructurePickerHistory.push(new RemoveSelectionEntry(previous));
    }

    public static List<Region> copyRegions()
    {
        List<Region> copy = new ArrayList<>(StructurePickerClient.regions.size());

        for (Region region : StructurePickerClient.regions)
        {
            copy.add(new Region(
                region.first().toImmutable(),
                region.second().toImmutable(),
                region.mode(),
                region.triangleFacing()
            ));
        }

        return copy;
    }

    public static void restoreRegions(List<Region> regions)
    {
        /* Keep bound structure path — Place and Select / undo restore must not unlock Save. */
        StructurePickerClient.clearSelection(false);

        if (regions == null || regions.isEmpty())
        {
            return;
        }

        StructurePickerClient.regions.addAll(regions);
        StructurePickerClient.selectionFromPaint = regions.size() > 8;
        StructurePickerClient.invalidateSelectedBlocks();
    }

    private static void tickUndoRedoKeys()
    {
        /* Panel owns Ctrl+Z/Y while open so text fields / overlays can take priority. */
        if (UIStructurePickerPanel.isOpened())
        {
            StructurePickerClient.undoKeyDown = Window.isCtrlPressed() && Window.isKeyPressed(GLFW.GLFW_KEY_Z);
            StructurePickerClient.redoKeyDown = Window.isCtrlPressed() && Window.isKeyPressed(GLFW.GLFW_KEY_Y);

            return;
        }

        boolean undoDown = Window.isCtrlPressed() && Window.isKeyPressed(GLFW.GLFW_KEY_Z) && !Window.isShiftPressed();
        boolean redoDown = Window.isCtrlPressed() && Window.isKeyPressed(GLFW.GLFW_KEY_Y);

        if (undoDown && !StructurePickerClient.undoKeyDown)
        {
            StructurePickerClient.undo();
        }

        if (redoDown && !StructurePickerClient.redoKeyDown)
        {
            StructurePickerClient.redo();
        }

        StructurePickerClient.undoKeyDown = undoDown;
        StructurePickerClient.redoKeyDown = redoDown;
    }

    public static boolean undo()
    {
        return StructurePickerHistory.undo();
    }

    public static boolean redo()
    {
        return StructurePickerHistory.redo();
    }

    public static boolean canUndo()
    {
        return StructurePickerHistory.canUndo();
    }

    public static boolean canRedo()
    {
        return StructurePickerHistory.canRedo();
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
        StructurePickerClient.importSelection(toModelBlock, null);
    }

    public static void importSelection(boolean toModelBlock, String customName)
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
        String name = customName == null ? "" : customName.trim();

        if (toModelBlock)
        {
            StructurePickerClient.startModelBlockFlash(placement);
            StructurePickerClient.runOnServer(mc, (serverWorld) ->
            {
                StructurePickerExporter.BlockSnapshot previous = StructurePickerExporter.captureBlock(serverWorld, placement);
                String path = StructurePickerExporter.export(serverWorld, blocks, name);

                if (path == null)
                {
                    return;
                }

                if (!StructurePickerExporter.placeModelBlock(serverWorld, placement, path, name))
                {
                    return;
                }

                mc.execute(() -> StructurePickerHistory.push(new ImportModelBlockEntry(placement.toImmutable(), previous, path, name)));
            });
        }
        else
        {
            StructurePickerClient.runOnServer(mc, (serverWorld) ->
            {
                String path = StructurePickerExporter.export(serverWorld, blocks, name);

                if (path != null)
                {
                    mc.execute(() ->
                    {
                        Replay replay = StructurePickerClient.importToFilm(path, placement);

                        if (replay != null)
                        {
                            StructurePickerHistory.push(new ImportFilmEntry(path, placement.toImmutable(), replay));
                        }
                    });
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

        List<Region> previousRegions = StructurePickerClient.copyRegions();
        List<BlockPos> blocks = new ArrayList<>(StructurePickerClient.getSelectedBlocks(world));

        if (blocks.isEmpty())
        {
            StructurePickerClient.clearSelection();

            if (!previousRegions.isEmpty())
            {
                StructurePickerHistory.push(new RemoveSelectionEntry(previousRegions));
            }

            return;
        }

        StructurePickerClient.runOnServer(mc, (serverWorld) ->
        {
            List<StructurePickerExporter.BlockSnapshot> snapshots = StructurePickerExporter.captureBlocks(serverWorld, blocks);

            StructurePickerExporter.removeBlocks(serverWorld, blocks);
            mc.execute(() ->
            {
                StructurePickerClient.clearSelection();
                StructurePickerHistory.push(new BreakSelectionEntry(previousRegions, snapshots));
            });
        });
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
        if (!StructurePickerClient.isActive() && !UIStructurePickerPanel.isOpened())
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();
        int lineIndex = 0;

        if (StructurePickerClient.isPlacementActive())
        {
            StructurePickerClient.renderHudLine(batcher, screenW, screenH, lineIndex, UIKeys.STRUCTURE_PICKER_PLACE_HINT.get());

            return;
        }

        if (!StructurePickerClient.isActive())
        {
            return;
        }

        if (StructurePickerClient.subtractMode && !StructurePickerClient.hasInProgress())
        {
            StructurePickerClient.renderHudLine(batcher, screenW, screenH, lineIndex, UIKeys.STRUCTURE_PICKER_SUBTRACTING.get());
            lineIndex += 1;
        }

        if (StructurePickerClient.isResizeGizmoActive())
        {
            StructurePickerClient.renderHudLine(batcher, screenW, screenH, lineIndex, UIKeys.STRUCTURE_PICKER_CUBE_RESIZE.get());
            lineIndex += 1;
        }

        if (!StructurePickerClient.hasInProgress())
        {
            if (StructurePickerClient.mode.isSingleClick() && StructurePickerClient.hasBlockSelection())
            {
                StructurePickerClient.ensureSelectedBlocks();
                BlockPos blockMin = StructurePickerClient.getSelectionBoundsMin();
                BlockPos blockMax = StructurePickerClient.getSelectionBoundsMax();

                if (blockMin != null && blockMax != null)
                {
                    int width = StructurePickerSelection.spanX(blockMin, blockMax);
                    int depth = StructurePickerSelection.spanZ(blockMin, blockMax);
                    int count = StructurePickerClient.getSelectedBlockCount();
                    String modeLabel = UIKeys.STRUCTURE_PICKER_MODE_LABELS[StructurePickerClient.mode.index].get();
                    String text = UIKeys.STRUCTURE_PICKER_INTERACTING.format(modeLabel, width, depth, count).get();

                    StructurePickerClient.renderHudLine(batcher, screenW, screenH, lineIndex, text);
                }
            }
            else if (StructurePickerClient.mode == StructurePickerMode.CUBE && !StructurePickerClient.regions.isEmpty() && !StructurePickerClient.isResizeGizmoActive())
            {
                StructurePickerClient.renderHudLine(batcher, screenW, screenH, lineIndex, UIKeys.STRUCTURE_PICKER_CUBE_RESIZE_HINT.get());
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

        Vec3d eye = StructurePickerClient.getPickEye(mc);
        Vec3d look = StructurePickerClient.getPickLook(mc);
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

    private static Replay importToFilm(String structurePath, BlockPos placement)
    {
        UIFilmPanel panel = BBSModClient.getDashboard().getPanel(UIFilmPanel.class);

        if (panel == null || panel.getData() == null)
        {
            return null;
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

        return replay;
    }

    private static void removeFilmReplay(Replay replay)
    {
        if (replay == null)
        {
            return;
        }

        UIFilmPanel panel = BBSModClient.getDashboard().getPanel(UIFilmPanel.class);

        if (panel == null || panel.getData() == null)
        {
            return;
        }

        panel.getData().replays.remove(replay);

        if (panel.replayEditor != null && panel.replayEditor.replays != null && panel.replayEditor.replays.replays != null)
        {
            panel.replayEditor.replays.replays.refreshAfterExternalEdit();
        }
    }

    private static final class RemoveSelectionEntry implements StructurePickerHistory.Entry
    {
        private final List<Region> regions;

        private RemoveSelectionEntry(List<Region> regions)
        {
            this.regions = regions;
        }

        @Override
        public void undo()
        {
            StructurePickerClient.restoreRegions(this.regions);
        }

        @Override
        public void redo()
        {
            StructurePickerClient.clearSelection();
        }
    }

    private static final class BreakSelectionEntry implements StructurePickerHistory.Entry
    {
        private final List<Region> regions;
        private final List<StructurePickerExporter.BlockSnapshot> snapshots;

        private BreakSelectionEntry(List<Region> regions, List<StructurePickerExporter.BlockSnapshot> snapshots)
        {
            this.regions = regions;
            this.snapshots = snapshots;
        }

        @Override
        public void undo()
        {
            MinecraftClient mc = MinecraftClient.getInstance();

            StructurePickerClient.runOnServer(mc, (serverWorld) -> StructurePickerExporter.restoreBlocks(serverWorld, this.snapshots));
            StructurePickerClient.restoreRegions(this.regions);
        }

        @Override
        public void redo()
        {
            MinecraftClient mc = MinecraftClient.getInstance();
            List<BlockPos> blocks = new ArrayList<>(this.snapshots.size());

            for (StructurePickerExporter.BlockSnapshot snapshot : this.snapshots)
            {
                blocks.add(snapshot.pos());
            }

            StructurePickerClient.runOnServer(mc, (serverWorld) -> StructurePickerExporter.removeBlocks(serverWorld, blocks));
            StructurePickerClient.clearSelection();
        }
    }

    private static final class PlaceStructureEntry implements StructurePickerHistory.Entry
    {
        private final String path;
        private final BlockPos origin;
        private final List<Region> previousRegions;
        private final List<StructurePickerExporter.BlockSnapshot> previousBlocks;
        private final BlockPos placedMin;
        private final BlockPos placedMax;

        private PlaceStructureEntry(String path, BlockPos origin, List<Region> previousRegions, List<StructurePickerExporter.BlockSnapshot> previousBlocks, BlockPos placedMin, BlockPos placedMax)
        {
            this.path = path;
            this.origin = origin;
            this.previousRegions = previousRegions;
            this.previousBlocks = previousBlocks;
            this.placedMin = placedMin;
            this.placedMax = placedMax;
        }

        @Override
        public void undo()
        {
            MinecraftClient mc = MinecraftClient.getInstance();

            StructurePickerClient.runOnServer(mc, (serverWorld) -> StructurePickerExporter.restoreBlocks(serverWorld, this.previousBlocks));
            StructurePickerClient.restoreRegions(this.previousRegions);
        }

        @Override
        public void redo()
        {
            MinecraftClient mc = MinecraftClient.getInstance();

            StructurePickerClient.runOnServer(mc, (serverWorld) ->
            {
                StructurePickerExporter.PlaceResult result = StructurePickerExporter.placeStructure(serverWorld, this.path, this.origin);

                if (result == null)
                {
                    return;
                }

                mc.execute(() ->
                {
                    StructurePickerClient.mode = StructurePickerMode.CUBE;
                    StructurePickerClient.restoreRegions(List.of(new Region(result.min(), result.max(), StructurePickerMode.CUBE)));
                    StructurePickerClient.boundStructurePath = this.path;
                    StructurePickerClient.ensureCubeScaleGizmo();
                    StructurePickerClient.notifySelectionUi();
                });
            });
        }
    }

    private static final class ImportModelBlockEntry implements StructurePickerHistory.Entry
    {
        private final BlockPos placement;
        private final StructurePickerExporter.BlockSnapshot previous;
        private final String structurePath;
        private final String customName;

        private ImportModelBlockEntry(BlockPos placement, StructurePickerExporter.BlockSnapshot previous, String structurePath, String customName)
        {
            this.placement = placement;
            this.previous = previous;
            this.structurePath = structurePath;
            this.customName = customName;
        }

        @Override
        public void undo()
        {
            MinecraftClient mc = MinecraftClient.getInstance();

            StructurePickerClient.runOnServer(mc, (serverWorld) -> StructurePickerExporter.restoreBlock(serverWorld, this.previous));
        }

        @Override
        public void redo()
        {
            MinecraftClient mc = MinecraftClient.getInstance();

            StructurePickerClient.startModelBlockFlash(this.placement);
            StructurePickerClient.runOnServer(mc, (serverWorld) ->
                StructurePickerExporter.placeModelBlock(serverWorld, this.placement, this.structurePath, this.customName));
        }
    }

    private static final class ImportFilmEntry implements StructurePickerHistory.Entry
    {
        private final String structurePath;
        private final BlockPos placement;
        private Replay replay;

        private ImportFilmEntry(String structurePath, BlockPos placement, Replay replay)
        {
            this.structurePath = structurePath;
            this.placement = placement;
            this.replay = replay;
        }

        @Override
        public void undo()
        {
            StructurePickerClient.removeFilmReplay(this.replay);
            this.replay = null;
        }

        @Override
        public void redo()
        {
            this.replay = StructurePickerClient.importToFilm(this.structurePath, this.placement);
        }
    }

    public record Region(BlockPos first, BlockPos second, StructurePickerMode mode, Direction triangleFacing)
    {
        public Region(BlockPos first, BlockPos second, StructurePickerMode mode)
        {
            this(first, second, mode, null);
        }
    }
}
