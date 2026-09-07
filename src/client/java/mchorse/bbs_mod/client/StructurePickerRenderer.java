package mchorse.bbs_mod.client;

import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.StructureForm;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.items.StructurePickerAxis;
import mchorse.bbs_mod.items.StructurePickerExporter;
import mchorse.bbs_mod.items.StructurePickerMode;
import mchorse.bbs_mod.items.StructurePickerRegionMerger;
import mchorse.bbs_mod.items.StructurePickerSelection;
import mchorse.bbs_mod.ui.items.UIStructurePickerPanel;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import com.mojang.blaze3d.systems.RenderSystem;

import org.lwjgl.opengl.GL11;

import java.util.List;

public class StructurePickerRenderer
{
    private static final float CORNER_HANDLE = 0.28F;
    private static final float GIZMO_AXIS_LENGTH = 2.15F;
    private static final float GIZMO_AXIS_HALF = 0.028F;
    private static final float GIZMO_KNOB = 0.20F;
    private static final float GIZMO_HUB = 0.10F;
    /* Slight outward expand so selection faces do not Z-fight with block surfaces. */
    private static final double VOLUME_EXPAND = 0.005D;
    private static final double[] AABB_SCRATCH = new double[6];

    public static void render(WorldRenderContext context)
    {
        StructurePickerRenderer.renderModelBlockFlash(context);

        boolean showSelection = StructurePickerClient.isActive() || UIStructurePickerPanel.isOpened();
        boolean showPlacement = StructurePickerClient.isPlacementActive();
        boolean showBrushPreview = showSelection
            && !showPlacement
            && StructurePickerClient.getMode() == StructurePickerMode.BRUSH;

        if (!showSelection && !showPlacement)
        {
            return;
        }

        if (!showPlacement
            && !showBrushPreview
            && !StructurePickerClient.hasAnySelection()
            && !StructurePickerClient.isResizeGizmoActive())
        {
            return;
        }

        if (context.matrixStack() == null)
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        Vec3d camera = mc.gameRenderer.getCamera().getPos();
        float pulse = StructurePickerRenderer.selectionPulseAlpha();
        float edgeAlpha = 0.75F + 0.25F * pulse;
        float fillAlpha = 0.22F + 0.28F * pulse;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        MatrixStack stack = context.matrixStack();

        stack.push();
        stack.translate(-camera.x, -camera.y, -camera.z);

        if (showPlacement)
        {
            StructurePickerRenderer.renderPlacementGhostForm(stack, context);
        }

        /* Selection volumes respect depth so they do not tint the first-person hand.
         * Gizmos/corners still draw on top of clouds and model blocks below. */
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        if (showPlacement)
        {
            StructurePickerRenderer.renderPlacementOverlayVolumes(stack, edgeAlpha);
        }
        else
        {
            StructurePickerRenderer.renderSelectionVolumes(edgeAlpha, fillAlpha, stack);
            StructurePickerRenderer.renderBrushPreview(edgeAlpha, fillAlpha, stack);
        }

        RenderSystem.disableDepthTest();
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        if (showPlacement)
        {
            StructurePickerRenderer.renderPlacementOverlayGizmos(stack);
        }
        else
        {
            StructurePickerRenderer.renderSelectionGizmos(stack);
        }

        stack.pop();

        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /**
     * Real StructureForm VAO at low opacity — same pivot as model-block / film import.
     */
    private static void renderPlacementGhostForm(MatrixStack stack, WorldRenderContext context)
    {
        StructureForm form = StructurePickerClient.getPlacementPreviewForm();
        BlockPos min = StructurePickerClient.getPlacementOrigin();
        BlockPos max = StructurePickerClient.getPlacementMax();

        if (form == null || min == null || max == null)
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        World world = mc.world;

        if (world == null)
        {
            return;
        }

        BlockPos placement = StructurePickerExporter.getPlacementPos(min, max);
        float tickDelta = context.tickCounter().getTickDelta(false);
        int light = WorldRenderer.getLightmapCoordinates(world, placement);

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        stack.push();
        stack.translate(placement.getX() + 0.5D, placement.getY(), placement.getZ() + 0.5D);

        FormRenderingContext formContext = new FormRenderingContext()
            .set(FormRenderType.MODEL_BLOCK, null, stack, light, OverlayTexture.DEFAULT_UV, tickDelta)
            .camera(mc.gameRenderer.getCamera());

        FormUtilsClient.render(form, formContext);

        stack.pop();
    }

    private static void renderSelectionVolumes(float edgeAlpha, float fillAlpha, MatrixStack stack)
    {
        boolean paintStyle = StructurePickerClient.getMode().isSingleClick()
            || StructurePickerClient.isSelectionFromPaint();

        if (paintStyle)
        {
            List<StructurePickerRegionMerger.MergedRegion> lod = StructurePickerClient.getSelectionLodRegions();

            if (lod != null)
            {
                /* Large paint selections: coarse cells, fill-only (edges are too heavy). */
                StructurePickerRenderer.renderMergedAabbs(stack, lod, 1F, 1F, 0F, 0F, Math.min(fillAlpha, 0.28F));

                BlockPos min = StructurePickerClient.getSelectionBoundsMin();
                BlockPos max = StructurePickerClient.getSelectionBoundsMax();

                if (min != null && max != null)
                {
                    StructurePickerRenderer.renderMergedBlockBox(
                        stack,
                        min,
                        max,
                        1F,
                        1F,
                        0F,
                        edgeAlpha * 0.85F,
                        0.04F
                    );
                }
            }
            else
            {
                int regionCount = StructurePickerClient.getRegions().size();
                float edges = regionCount > 220 ? 0F : edgeAlpha;
                float fills = regionCount > 220 ? Math.min(fillAlpha, 0.30F) : fillAlpha;

                StructurePickerRenderer.renderRegionAabbs(
                    stack,
                    StructurePickerClient.getRegions(),
                    1F,
                    1F,
                    0F,
                    edges,
                    fills
                );
            }
        }
        else
        {
            for (StructurePickerClient.Region region : StructurePickerClient.getRegions())
            {
                StructurePickerRenderer.renderRegionBox(stack, region.first(), region.second(), region.mode(), region.triangleFacing(), 1F, 1F, 0F, edgeAlpha, fillAlpha);
            }
        }

        if (StructurePickerClient.hasInProgress())
        {
            if (StructurePickerClient.isSubtractMode())
            {
                StructurePickerRenderer.renderRegionBox(stack, StructurePickerClient.getFirstCorner(), StructurePickerClient.getSecondCorner(), StructurePickerClient.getMode(), StructurePickerClient.getTriangleFacing(), 1F, 0.25F, 0.25F, edgeAlpha, fillAlpha);
            }
            else
            {
                StructurePickerRenderer.renderRegionBox(stack, StructurePickerClient.getFirstCorner(), StructurePickerClient.getSecondCorner(), StructurePickerClient.getMode(), StructurePickerClient.getTriangleFacing(), 1F, 1F, 0F, edgeAlpha, fillAlpha);
            }
        }
    }

    private static void renderBrushPreview(float edgeAlpha, float fillAlpha, MatrixStack stack)
    {
        if (StructurePickerClient.getMode() != StructurePickerMode.BRUSH)
        {
            return;
        }

        List<StructurePickerRegionMerger.MergedRegion> preview = StructurePickerClient.getBrushPreviewRegions();

        if (preview.isEmpty())
        {
            return;
        }

        float pulse = StructurePickerRenderer.selectionPulseAlpha();
        float r = StructurePickerClient.isSubtractMode() ? 1F : 0.35F;
        float g = StructurePickerClient.isSubtractMode() ? 0.35F : 0.85F;
        float b = StructurePickerClient.isSubtractMode() ? 0.35F : 1F;
        float previewEdge = 0.45F + 0.55F * pulse;
        float previewFill = 0.12F + 0.22F * pulse;

        StructurePickerRenderer.renderMergedAabbs(stack, preview, r, g, b, previewEdge, previewFill);
    }

    private static void renderRegionAabbs(MatrixStack stack, List<StructurePickerClient.Region> regions, float r, float g, float b, float edgeAlpha, float fillAlpha)
    {
        if (regions.isEmpty())
        {
            return;
        }

        StructurePickerRenderer.beginAabbBatch(stack, r, g, b, edgeAlpha, fillAlpha, regions.size(), (i, out) ->
        {
            StructurePickerClient.Region region = regions.get(i);
            BlockPos min = StructurePickerSelection.min(region.first(), region.second());
            BlockPos max = StructurePickerSelection.max(region.first(), region.second());

            out[0] = min.getX();
            out[1] = min.getY();
            out[2] = min.getZ();
            out[3] = max.getX() - min.getX() + 1D;
            out[4] = max.getY() - min.getY() + 1D;
            out[5] = max.getZ() - min.getZ() + 1D;
        });
    }

    private static void renderMergedAabbs(MatrixStack stack, List<StructurePickerRegionMerger.MergedRegion> regions, float r, float g, float b, float edgeAlpha, float fillAlpha)
    {
        if (regions.isEmpty())
        {
            return;
        }

        StructurePickerRenderer.beginAabbBatch(stack, r, g, b, edgeAlpha, fillAlpha, regions.size(), (i, out) ->
        {
            StructurePickerRegionMerger.MergedRegion region = regions.get(i);
            BlockPos min = region.min();
            BlockPos max = region.max();

            out[0] = min.getX();
            out[1] = min.getY();
            out[2] = min.getZ();
            out[3] = max.getX() - min.getX() + 1D;
            out[4] = max.getY() - min.getY() + 1D;
            out[5] = max.getZ() - min.getZ() + 1D;
        });
    }

    @FunctionalInterface
    private interface AabbWriter
    {
        void write(int index, double[] out);
    }

    /**
     * One fill draw + one edge draw (or Iris-queued edges) for all AABBs.
     */
    private static void beginAabbBatch(MatrixStack stack, float r, float g, float b, float edgeAlpha, float fillAlpha, int count, AabbWriter writer)
    {
        double e = VOLUME_EXPAND;
        boolean irisEdges = BBSRendering.isIrisShadersEnabled() && BBSRendering.isRenderingWorld();
        double[] box = AABB_SCRATCH;

        if (fillAlpha > 0.001F)
        {
            BufferBuilder fill = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

            for (int i = 0; i < count; i++)
            {
                writer.write(i, box);
                stack.push();
                stack.translate(box[0] - e, box[1] - e, box[2] - e);
                Draw.fillBox(
                    fill,
                    stack,
                    0F,
                    0F,
                    0F,
                    (float) (box[3] + e * 2D),
                    (float) (box[4] + e * 2D),
                    (float) (box[5] + e * 2D),
                    r,
                    g,
                    b,
                    fillAlpha
                );
                stack.pop();
            }

            BufferRenderer.drawWithGlobalProgram(fill.end());
        }

        if (edgeAlpha <= 0.001F)
        {
            return;
        }

        if (irisEdges)
        {
            for (int i = 0; i < count; i++)
            {
                writer.write(i, box);
                Draw.renderBox(stack, box[0] - e, box[1] - e, box[2] - e, box[3] + e * 2D, box[4] + e * 2D, box[5] + e * 2D, r, g, b, edgeAlpha);
            }

            return;
        }

        BufferBuilder edges = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < count; i++)
        {
            writer.write(i, box);
            StructurePickerRenderer.appendBoxEdges(
                edges,
                stack,
                box[0] - e,
                box[1] - e,
                box[2] - e,
                box[3] + e * 2D,
                box[4] + e * 2D,
                box[5] + e * 2D,
                r,
                g,
                b,
                edgeAlpha
            );
        }

        BufferRenderer.drawWithGlobalProgram(edges.end());
    }

    private static void appendBoxEdges(BufferBuilder builder, MatrixStack stack, double x, double y, double z, double w, double h, double d, float r, float g, float b, float a)
    {
        float fw = (float) w;
        float fh = (float) h;
        float fd = (float) d;
        float t = 1F / 96F + (float) (Math.sqrt(w * w + h * h + d * d) / 2000D);

        stack.push();
        stack.translate(x, y, z);

        /* Pillars */
        Draw.fillBox(builder, stack, -t, -t, -t, t, t + fh, t, r, g, b, a);
        Draw.fillBox(builder, stack, -t + fw, -t, -t, t + fw, t + fh, t, r, g, b, a);
        Draw.fillBox(builder, stack, -t, -t, -t + fd, t, t + fh, t + fd, r, g, b, a);
        Draw.fillBox(builder, stack, -t + fw, -t, -t + fd, t + fw, t + fh, t + fd, r, g, b, a);

        /* Top */
        Draw.fillBox(builder, stack, -t, -t + fh, -t, t + fw, t + fh, t, r, g, b, a);
        Draw.fillBox(builder, stack, -t, -t + fh, -t + fd, t + fw, t + fh, t + fd, r, g, b, a);
        Draw.fillBox(builder, stack, -t, -t + fh, -t, t, t + fh, t + fd, r, g, b, a);
        Draw.fillBox(builder, stack, -t + fw, -t + fh, -t, t + fw, t + fh, t + fd, r, g, b, a);

        /* Bottom */
        Draw.fillBox(builder, stack, -t, -t, -t, t + fw, t, t, r, g, b, a);
        Draw.fillBox(builder, stack, -t, -t, -t + fd, t + fw, t, t + fd, r, g, b, a);
        Draw.fillBox(builder, stack, -t, -t, -t, t, t, t + fd, r, g, b, a);
        Draw.fillBox(builder, stack, -t + fw, -t, -t, t + fw, t, t + fd, r, g, b, a);

        stack.pop();
    }

    private static void renderSelectionGizmos(MatrixStack stack)
    {
        if (StructurePickerClient.hasActiveCubeSelection())
        {
            for (StructurePickerClient.Region region : StructurePickerClient.getRegions())
            {
                if (region.mode() == StructurePickerMode.CUBE)
                {
                    StructurePickerRenderer.renderCubeCorners(stack, region.first(), region.second());
                }
            }
        }

        if (StructurePickerClient.isResizeGizmoActive())
        {
            StructurePickerRenderer.renderResizeGizmo(stack, StructurePickerClient.getResizeFreeCorner());
        }
    }

    private static void renderPlacementOverlayVolumes(MatrixStack stack, float edgeAlpha)
    {
        BlockPos min = StructurePickerClient.getPlacementOrigin();
        BlockPos max = StructurePickerClient.getPlacementMax();

        if (min == null || max == null)
        {
            return;
        }

        double sizeX = max.getX() - min.getX() + 1D;
        double sizeY = max.getY() - min.getY() + 1D;
        double sizeZ = max.getZ() - min.getZ() + 1D;
        double e = VOLUME_EXPAND;

        Draw.renderBox(stack, min.getX() - e, min.getY() - e, min.getZ() - e, sizeX + e * 2D, sizeY + e * 2D, sizeZ + e * 2D, 1F, 1F, 0.35F, edgeAlpha);
    }

    private static void renderPlacementOverlayGizmos(MatrixStack stack)
    {
        Vec3d gizmo = StructurePickerClient.getPlacementGizmoPoint();

        if (gizmo != null)
        {
            StructurePickerRenderer.renderPlacementGizmo(stack, gizmo);
        }
    }

    private static void renderPlacementGizmo(MatrixStack stack, Vec3d center)
    {
        double ox = center.x;
        double oy = center.y;
        double oz = center.z;
        float scale = StructurePickerClient.getHandleVisualScale(ox, oy, oz);
        float len = GIZMO_AXIS_LENGTH * scale;
        float t = GIZMO_AXIS_HALF * scale * 1.35F;
        float hub = GIZMO_HUB * scale;
        StructurePickerAxis hover = StructurePickerClient.getPlacementDragAxis();

        StructurePickerRenderer.renderVolumeFill(stack, ox - hub * 0.5D, oy - hub * 0.5D, oz - hub * 0.5D, hub, hub, hub, 1F, 1F, 1F, 1F);
        StructurePickerRenderer.renderTranslateAxis(stack, ox, oy, oz, StructurePickerAxis.X, len, t, 1F, 0.22F, 0.22F, hover == StructurePickerAxis.X);
        StructurePickerRenderer.renderTranslateAxis(stack, ox, oy, oz, StructurePickerAxis.Y, len, t, 0.22F, 1F, 0.22F, hover == StructurePickerAxis.Y);
        StructurePickerRenderer.renderTranslateAxis(stack, ox, oy, oz, StructurePickerAxis.Z, len, t, 0.25F, 0.5F, 1F, hover == StructurePickerAxis.Z);
    }

    public static float selectionPulseAlpha()
    {
        return 0.5F + 0.5F * (float) Math.sin(System.currentTimeMillis() * 0.004D);
    }

    private static void renderModelBlockFlash(WorldRenderContext context)
    {
        BlockPos pos = StructurePickerClient.getModelBlockFlashPos();

        if (pos == null || context.matrixStack() == null)
        {
            return;
        }

        float alpha = StructurePickerClient.getModelBlockFlashAlpha();

        if (alpha <= 0.01F)
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        Vec3d camera = mc.gameRenderer.getCamera().getPos();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        MatrixStack stack = context.matrixStack();

        stack.push();
        stack.translate(-camera.x, -camera.y, -camera.z);
        /* Same cyan as F3 model-block outline (see ModelBlockEntityRenderer) */
        Draw.renderBox(stack, pos.getX(), pos.getY(), pos.getZ(), 1D, 1D, 1D, 0F, 0.5F, 1F, alpha);
        stack.pop();

        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void renderRegionBox(MatrixStack stack, BlockPos first, BlockPos second, StructurePickerMode mode, Direction triangleFacing, float r, float g, float b, float edgeAlpha, float fillAlpha)
    {
        BlockPos adjusted = StructurePickerSelection.adjustSecond(first, second, mode);
        BlockPos min = StructurePickerSelection.min(first, adjusted);
        BlockPos max = StructurePickerSelection.max(first, adjusted);
        double sizeX = max.getX() - min.getX() + 1D;
        double sizeY = max.getY() - min.getY() + 1D;
        double sizeZ = max.getZ() - min.getZ() + 1D;

        if (mode.hasShapeOutline())
        {
            StructurePickerShapeOutline.render(stack, first, second, mode, triangleFacing, r, g, b, edgeAlpha);
        }

        StructurePickerRenderer.renderExpandedVolume(stack, min.getX(), min.getY(), min.getZ(), sizeX, sizeY, sizeZ, r, g, b, edgeAlpha, fillAlpha);
    }

    private static void renderMergedBlockBox(MatrixStack stack, BlockPos min, BlockPos max, float r, float g, float b, float edgeAlpha, float fillAlpha)
    {
        double sizeX = max.getX() - min.getX() + 1D;
        double sizeY = max.getY() - min.getY() + 1D;
        double sizeZ = max.getZ() - min.getZ() + 1D;

        StructurePickerRenderer.renderExpandedVolume(stack, min.getX(), min.getY(), min.getZ(), sizeX, sizeY, sizeZ, r, g, b, edgeAlpha, fillAlpha);
    }

    private static void renderExpandedVolume(MatrixStack stack, double x, double y, double z, double w, double h, double d, float r, float g, float b, float edgeAlpha, float fillAlpha)
    {
        double e = VOLUME_EXPAND;

        StructurePickerRenderer.renderVolumeFill(stack, x - e, y - e, z - e, w + e * 2D, h + e * 2D, d + e * 2D, r, g, b, fillAlpha);
        Draw.renderBox(stack, x - e, y - e, z - e, w + e * 2D, h + e * 2D, d + e * 2D, r, g, b, edgeAlpha);
    }

    private static void renderVolumeFill(MatrixStack stack, double x, double y, double z, double w, double h, double d, float r, float g, float b, float a)
    {
        if (a <= 0.001F)
        {
            return;
        }

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        stack.push();
        stack.translate(x, y, z);
        Draw.fillBox(builder, stack, 0F, 0F, 0F, (float) w, (float) h, (float) d, r, g, b, a);
        stack.pop();

        BufferRenderer.drawWithGlobalProgram(builder.end());
    }

    private static void renderCubeCorners(MatrixStack stack, BlockPos first, BlockPos second)
    {
        BlockPos min = StructurePickerSelection.min(first, second);
        BlockPos max = StructurePickerSelection.max(first, second);
        float pulse = 0.8F + 0.2F * StructurePickerRenderer.selectionPulseAlpha();
        float hMin = CORNER_HANDLE * StructurePickerClient.getHandleVisualScale(min.getX(), min.getY(), min.getZ());
        float hMax = CORNER_HANDLE * StructurePickerClient.getHandleVisualScale(max.getX() + 1, max.getY() + 1, max.getZ() + 1);

        StructurePickerRenderer.renderCornerHandle(stack, min.getX(), min.getY(), min.getZ(), hMin, pulse);
        StructurePickerRenderer.renderCornerHandle(stack, max.getX() + 1, max.getY() + 1, max.getZ() + 1, hMax, pulse);
    }

    private static void renderCornerHandle(MatrixStack stack, double x, double y, double z, float h, float alpha)
    {
        float rim = h * 1.18F;

        StructurePickerRenderer.renderVolumeFill(stack, x - rim * 0.5D, y - rim * 0.5D, z - rim * 0.5D, rim, rim, rim, 1F, 1F, 1F, alpha * 0.55F);
        StructurePickerRenderer.renderVolumeFill(stack, x - h * 0.5D, y - h * 0.5D, z - h * 0.5D, h, h, h, 1F, 1F, 1F, alpha);
    }

    private static void renderResizeGizmo(MatrixStack stack, BlockPos freeCorner)
    {
        if (freeCorner == null)
        {
            return;
        }

        double ox = freeCorner.getX();
        double oy = freeCorner.getY();
        double oz = freeCorner.getZ();

        if (StructurePickerClient.isSelectionMoveUsingMaxCorner())
        {
            ox += 1D;
            oy += 1D;
            oz += 1D;
        }

        boolean positive = StructurePickerClient.isScaleGizmoPositive();
        float scale = StructurePickerClient.getHandleVisualScale(ox, oy, oz);
        float len = (positive ? GIZMO_AXIS_LENGTH : -GIZMO_AXIS_LENGTH) * scale;
        float t = GIZMO_AXIS_HALF * scale;
        float knob = GIZMO_KNOB * scale;
        float hub = GIZMO_HUB * scale;
        StructurePickerAxis hover = StructurePickerClient.getResizeDragAxis();

        StructurePickerRenderer.renderVolumeFill(stack, ox - hub * 0.5D, oy - hub * 0.5D, oz - hub * 0.5D, hub, hub, hub, 1F, 1F, 1F, 1F);
        StructurePickerRenderer.renderScaleAxis(stack, ox, oy, oz, StructurePickerAxis.X, len, t, knob, 1F, 0.22F, 0.22F, hover == StructurePickerAxis.X);
        StructurePickerRenderer.renderScaleAxis(stack, ox, oy, oz, StructurePickerAxis.Y, len, t, knob, 0.22F, 1F, 0.22F, hover == StructurePickerAxis.Y);
        StructurePickerRenderer.renderScaleAxis(stack, ox, oy, oz, StructurePickerAxis.Z, len, t, knob, 0.25F, 0.5F, 1F, hover == StructurePickerAxis.Z);
    }

    private static void renderScaleAxis(MatrixStack stack, double ox, double oy, double oz, StructurePickerAxis axis, float len, float t, float knob, float r, float g, float b, boolean highlight)
    {
        StructurePickerRenderer.renderAxisArm(stack, ox, oy, oz, axis, len, t, r, g, b, highlight);

        double ex = ox;
        double ey = oy;
        double ez = oz;

        if (axis == StructurePickerAxis.X)
        {
            ex += len;
        }
        else if (axis == StructurePickerAxis.Y)
        {
            ey += len;
        }
        else
        {
            ez += len;
        }

        float a = highlight ? 1F : 0.95F;
        float s = knob * (highlight ? 1.25F : 1F);
        float rim = s * 1.2F;

        StructurePickerRenderer.renderVolumeFill(stack, ex - rim * 0.5D, ey - rim * 0.5D, ez - rim * 0.5D, rim, rim, rim, 1F, 1F, 1F, a * 0.45F);
        StructurePickerRenderer.renderVolumeFill(stack, ex - s * 0.5D, ey - s * 0.5D, ez - s * 0.5D, s, s, s, r, g, b, a);
    }

    /**
     * Placement move handle: thick stem + cone arrow tip (Blender-style translate gizmo).
     */
    private static void renderTranslateAxis(MatrixStack stack, double ox, double oy, double oz, StructurePickerAxis axis, float len, float t, float r, float g, float b, boolean highlight)
    {
        float tipLength = Math.abs(len) * 0.35F;
        float tipRadius = t * 2.6F * (highlight ? 1.2F : 1F);
        float a = highlight ? 1F : 0.95F;
        float sign = len >= 0F ? 1F : -1F;
        float base = Math.abs(len) * sign;
        float apex = (Math.abs(len) + tipLength) * sign;

        StructurePickerRenderer.renderAxisArm(stack, ox, oy, oz, axis, len, t, r, g, b, highlight);

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        stack.push();
        stack.translate(ox, oy, oz);

        if (axis == StructurePickerAxis.X)
        {
            Draw.cone(builder, stack, apex, 0F, 0F, base, 0F, 0F, tipRadius, 10, r, g, b, a);
        }
        else if (axis == StructurePickerAxis.Y)
        {
            Draw.cone(builder, stack, 0F, apex, 0F, 0F, base, 0F, tipRadius, 10, r, g, b, a);
        }
        else
        {
            Draw.cone(builder, stack, 0F, 0F, apex, 0F, 0F, base, tipRadius, 10, r, g, b, a);
        }

        stack.pop();
        BufferRenderer.drawWithGlobalProgram(builder.end());
    }

    private static void renderAxisArm(MatrixStack stack, double ox, double oy, double oz, StructurePickerAxis axis, float len, float t, float r, float g, float b, boolean highlight)
    {
        float thick = t * (highlight ? 1.45F : 1F);
        float a = highlight ? 1F : 0.92F;
        double x0 = ox;
        double y0 = oy;
        double z0 = oz;
        double w = Math.abs(len);
        double h = Math.abs(len);
        double d = Math.abs(len);

        if (axis == StructurePickerAxis.X)
        {
            x0 = len >= 0F ? ox : ox + len;
            StructurePickerRenderer.renderVolumeFill(stack, x0, oy - thick, oz - thick, w, thick * 2F, thick * 2F, r, g, b, a);
        }
        else if (axis == StructurePickerAxis.Y)
        {
            y0 = len >= 0F ? oy : oy + len;
            StructurePickerRenderer.renderVolumeFill(stack, ox - thick, y0, oz - thick, thick * 2F, h, thick * 2F, r, g, b, a);
        }
        else
        {
            z0 = len >= 0F ? oz : oz + len;
            StructurePickerRenderer.renderVolumeFill(stack, ox - thick, oy - thick, z0, thick * 2F, thick * 2F, d, r, g, b, a);
        }
    }
}
