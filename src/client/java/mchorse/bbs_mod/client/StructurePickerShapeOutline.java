package mchorse.bbs_mod.client;

import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.items.StructurePickerMode;
import mchorse.bbs_mod.items.StructurePickerSelection;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StructurePickerShapeOutline
{
    private static final float FACE_FILL_ALPHA = 0.42F;

    public static void render(MatrixStack stack, BlockPos first, BlockPos second, StructurePickerMode mode, Direction triangleFacing, float r, float g, float b, float a)
    {
        if (!mode.hasShapeOutline())
        {
            return;
        }

        List<BlockPos> blocks = StructurePickerSelection.preview(null, first, second, mode, triangleFacing);

        if (blocks.isEmpty())
        {
            return;
        }

        StructurePickerShapeOutline.renderFaces(stack, new HashSet<>(blocks), r, g, b);
    }

    private static void renderFaces(MatrixStack stack, Set<BlockPos> blocks, float r, float g, float b)
    {
        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        for (BlockPos pos : blocks)
        {
            if (!blocks.contains(pos.up()))
            {
                StructurePickerShapeOutline.drawTopFaceFill(builder, stack, pos, r, g, b);
            }

            if (!blocks.contains(pos.down()))
            {
                StructurePickerShapeOutline.drawBottomFaceFill(builder, stack, pos, r, g, b);
            }

            if (!blocks.contains(pos.north()))
            {
                StructurePickerShapeOutline.drawNorthFaceFill(builder, stack, pos, r, g, b);
            }

            if (!blocks.contains(pos.south()))
            {
                StructurePickerShapeOutline.drawSouthFaceFill(builder, stack, pos, r, g, b);
            }

            if (!blocks.contains(pos.west()))
            {
                StructurePickerShapeOutline.drawWestFaceFill(builder, stack, pos, r, g, b);
            }

            if (!blocks.contains(pos.east()))
            {
                StructurePickerShapeOutline.drawEastFaceFill(builder, stack, pos, r, g, b);
            }
        }

        BufferRenderer.drawWithGlobalProgram(builder.end());
    }

    private static void drawTopFaceFill(BufferBuilder builder, MatrixStack stack, BlockPos pos, float r, float g, float b)
    {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        Draw.fillBox(builder, stack, x, y + 1F, z, x + 1F, y + 1.001F, z + 1F, r, g, b, FACE_FILL_ALPHA);
    }

    private static void drawBottomFaceFill(BufferBuilder builder, MatrixStack stack, BlockPos pos, float r, float g, float b)
    {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        Draw.fillBox(builder, stack, x, y, z, x + 1F, y + 0.001F, z + 1F, r, g, b, FACE_FILL_ALPHA);
    }

    private static void drawNorthFaceFill(BufferBuilder builder, MatrixStack stack, BlockPos pos, float r, float g, float b)
    {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        Draw.fillBox(builder, stack, x, y, z, x + 1F, y + 1F, z + 0.001F, r, g, b, FACE_FILL_ALPHA);
    }

    private static void drawSouthFaceFill(BufferBuilder builder, MatrixStack stack, BlockPos pos, float r, float g, float b)
    {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        Draw.fillBox(builder, stack, x, y, z + 1F - 0.001F, x + 1F, y + 1F, z + 1F, r, g, b, FACE_FILL_ALPHA);
    }

    private static void drawWestFaceFill(BufferBuilder builder, MatrixStack stack, BlockPos pos, float r, float g, float b)
    {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        Draw.fillBox(builder, stack, x, y, z, x + 0.001F, y + 1F, z + 1F, r, g, b, FACE_FILL_ALPHA);
    }

    private static void drawEastFaceFill(BufferBuilder builder, MatrixStack stack, BlockPos pos, float r, float g, float b)
    {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        Draw.fillBox(builder, stack, x + 1F - 0.001F, y, z, x + 1F, y + 1F, z + 1F, r, g, b, FACE_FILL_ALPHA);
    }
}
