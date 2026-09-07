package mchorse.bbs_mod.client;

import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.items.StructurePickerMode;
import mchorse.bbs_mod.items.StructurePickerRegionMerger;
import mchorse.bbs_mod.items.StructurePickerSelection;
import mchorse.bbs_mod.ui.items.UIStructurePickerPanel;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import com.mojang.blaze3d.systems.RenderSystem;

import java.util.LinkedHashSet;
import java.util.Set;

public class StructurePickerRenderer
{
    public static void render(WorldRenderContext context)
    {
        if (!StructurePickerClient.isActive() && !UIStructurePickerPanel.isOpened())
        {
            return;
        }

        if (!StructurePickerClient.hasAnySelection())
        {
            return;
        }

        if (context.matrixStack() == null)
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        Vec3d camera = mc.gameRenderer.getCamera().getPos();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        MatrixStack stack = context.matrixStack();

        stack.push();
        stack.translate(-camera.x, -camera.y, -camera.z);

        Set<BlockPos> blockPositions = new LinkedHashSet<>();

        if (StructurePickerClient.getMode() == StructurePickerMode.BLOCK)
        {
            blockPositions.addAll(StructurePickerClient.getAllRegionBlocks());

            for (StructurePickerRegionMerger.MergedRegion merged : StructurePickerRegionMerger.merge(blockPositions))
            {
                StructurePickerRenderer.renderMergedBlockBox(stack, merged.min(), merged.max(), 1F, 1F, 0F);
            }
        }
        else
        {
            for (StructurePickerClient.Region region : StructurePickerClient.getRegions())
            {
                StructurePickerRenderer.renderRegionBox(stack, region.first(), region.second(), region.mode(), region.triangleFacing(), 1F, 1F, 0F);
            }
        }

        if (StructurePickerClient.hasInProgress())
        {
            if (StructurePickerClient.isSubtractMode())
            {
                StructurePickerRenderer.renderRegionBox(stack, StructurePickerClient.getFirstCorner(), StructurePickerClient.getSecondCorner(), StructurePickerClient.getMode(), StructurePickerClient.getTriangleFacing(), 1F, 0.25F, 0.25F);
            }
            else
            {
                StructurePickerRenderer.renderRegionBox(stack, StructurePickerClient.getFirstCorner(), StructurePickerClient.getSecondCorner(), StructurePickerClient.getMode(), StructurePickerClient.getTriangleFacing(), 1F, 1F, 0F);
            }
        }

        stack.pop();

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static void renderRegionBox(MatrixStack stack, BlockPos first, BlockPos second, StructurePickerMode mode, Direction triangleFacing, float r, float g, float b)
    {
        BlockPos adjusted = StructurePickerSelection.adjustSecond(first, second, mode);
        BlockPos min = StructurePickerSelection.min(first, adjusted);
        BlockPos max = StructurePickerSelection.max(first, adjusted);
        double sizeX = max.getX() - min.getX() + 1D;
        double sizeY = max.getY() - min.getY() + 1D;
        double sizeZ = max.getZ() - min.getZ() + 1D;

        if (mode.hasShapeOutline())
        {
            StructurePickerShapeOutline.render(stack, first, second, mode, triangleFacing, r, g, b, 0.95F);
        }

        Draw.renderBox(stack, min.getX(), min.getY(), min.getZ(), sizeX, sizeY, sizeZ, r, g, b, 0.95F);
    }

    private static void renderMergedBlockBox(MatrixStack stack, BlockPos min, BlockPos max, float r, float g, float b)
    {
        double sizeX = max.getX() - min.getX() + 1D;
        double sizeY = max.getY() - min.getY() + 1D;
        double sizeZ = max.getZ() - min.getZ() + 1D;

        Draw.renderBox(stack, min.getX(), min.getY(), min.getZ(), sizeX, sizeY, sizeZ, r, g, b, 0.95F);
    }
}
