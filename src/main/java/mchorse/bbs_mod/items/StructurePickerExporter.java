package mchorse.bbs_mod.items;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.blocks.ModelBlock;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.blocks.entities.ModelProperties;
import mchorse.bbs_mod.forms.forms.StructureForm;
import mchorse.bbs_mod.mixin.StructureTemplateAccessor;
import mchorse.bbs_mod.mixin.StructureTemplatePalettedListAccessor;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StructurePickerExporter
{
    public static String export(ServerWorld world, List<BlockPos> blocks)
    {
        if (blocks.isEmpty())
        {
            return null;
        }

        BlockPos min = blocks.getFirst();
        BlockPos max = blocks.getFirst();

        for (BlockPos pos : blocks)
        {
            min = StructurePickerSelection.min(min, pos);
            max = StructurePickerSelection.max(max, pos);
        }

        Vec3i size = max.subtract(min).add(1, 1, 1);
        StructureTemplate template = new StructureTemplate();

        template.saveFromWorld(world, min, size, true, Blocks.STRUCTURE_VOID);
        filterTemplate(template, min, new HashSet<>(blocks));

        File generatedFolder = world.getServer().getSavePath(WorldSavePath.GENERATED).toFile();
        File folder = new File(new File(generatedFolder, "minecraft"), "structures");

        if (!folder.exists())
        {
            folder.mkdirs();
        }

        String fileName = "pick_" + System.currentTimeMillis() + ".nbt";
        File file = new File(folder, fileName);

        try
        {
            NbtCompound nbt = new NbtCompound();

            template.writeNbt(nbt);
            NbtIo.writeCompressed(nbt, file.toPath());
        }
        catch (IOException e)
        {
            e.printStackTrace();

            return null;
        }

        return "world:" + fileName;
    }

    public static boolean placeModelBlock(ServerWorld world, BlockPos center, String structurePath)
    {
        if (structurePath == null || structurePath.isEmpty())
        {
            return false;
        }

        if (world.getBlockState(center).isOf(BBSMod.MODEL_BLOCK))
        {
            BlockEntity blockEntity = world.getBlockEntity(center);

            if (blockEntity instanceof ModelBlockEntity modelBlockEntity)
            {
                StructureForm form = new StructureForm();

                form.structureFile.set(structurePath);

                ModelProperties properties = modelBlockEntity.getProperties();

                properties.setForm(form);
                properties.setName("Structure");
                properties.setHitbox(true);
                modelBlockEntity.markDirty();
                world.updateListeners(center, world.getBlockState(center), world.getBlockState(center), 3);

                return true;
            }
        }

        StructureForm form = new StructureForm();

        form.structureFile.set(structurePath);

        BlockState modelState = BBSMod.MODEL_BLOCK.getDefaultState()
            .with(Properties.WATERLOGGED, world.getFluidState(center).isOf(Fluids.WATER))
            .with(ModelBlock.LIGHT_LEVEL, 0);

        if (!world.setBlockState(center, modelState, 3))
        {
            return false;
        }

        BlockEntity blockEntity = world.getBlockEntity(center);

        if (!(blockEntity instanceof ModelBlockEntity modelBlockEntity))
        {
            return false;
        }

        ModelProperties properties = modelBlockEntity.getProperties();

        properties.setForm(form);
        properties.setName("Structure");
        properties.setHitbox(true);
        modelBlockEntity.markDirty();
        world.updateListeners(center, modelState, modelState, 3);

        return true;
    }

    public static void removeBlocks(ServerWorld world, List<BlockPos> blocks)
    {
        for (BlockPos pos : blocks)
        {
            /* Never break model blocks (e.g. one just placed at the selection center) */
            if (world.getBlockState(pos).isOf(BBSMod.MODEL_BLOCK))
            {
                continue;
            }

            world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
        }
    }

    /**
     * Block position whose center matches the structure form's render pivot,
     * so the rendered structure lines up with the original world blocks.
     */
    public static BlockPos getPlacementPos(BlockPos min, BlockPos max)
    {
        int sizeX = max.getX() - min.getX() + 1;
        int sizeZ = max.getZ() - min.getZ() + 1;

        return new BlockPos(
            min.getX() + (sizeX - 1) / 2,
            min.getY(),
            min.getZ() + (sizeZ - 1) / 2
        );
    }

    private static void filterTemplate(StructureTemplate template, BlockPos origin, Set<BlockPos> selected)
    {
        StructureTemplateAccessor accessor = (StructureTemplateAccessor) template;

        for (StructureTemplate.PalettedBlockInfoList list : accessor.bbs$getBlockInfoLists())
        {
            StructureTemplatePalettedListAccessor palette = (StructureTemplatePalettedListAccessor) (Object) list;

            palette.bbs$getInfos().removeIf((info) -> !selected.contains(origin.add(info.pos())));
        }

        accessor.bbs$getBlockInfoLists().removeIf((list) -> ((StructureTemplatePalettedListAccessor) (Object) list).bbs$getInfos().isEmpty());
    }
}
