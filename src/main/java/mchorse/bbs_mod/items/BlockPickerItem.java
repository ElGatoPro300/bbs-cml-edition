package mchorse.bbs_mod.items;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.blocks.ModelBlock;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.blocks.entities.ModelProperties;
import mchorse.bbs_mod.forms.forms.BlockForm;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class BlockPickerItem extends Item
{
    public BlockPickerItem(Settings settings)
    {
        super(settings);
    }

    @Override
    public boolean hasGlint(ItemStack stack)
    {
        return true;
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context)
    {
        World world = context.getWorld();

        if (world.isClient())
        {
            return ActionResult.SUCCESS;
        }

        if (context.getPlayer() == null)
        {
            return ActionResult.PASS;
        }

        BlockPos pos = context.getBlockPos();
        BlockState sourceState = world.getBlockState(pos);

        if (sourceState.isOf(BBSMod.MODEL_BLOCK))
        {
            return ActionResult.PASS;
        }

        BlockForm form = createBlockForm(world, pos, sourceState);

        BlockState modelState = BBSMod.MODEL_BLOCK.getDefaultState()
            .with(Properties.WATERLOGGED, world.getFluidState(pos).isOf(Fluids.WATER))
            .with(ModelBlock.LIGHT_LEVEL, 0);

        if (!world.setBlockState(pos, modelState, 3))
        {
            return ActionResult.PASS;
        }

        BlockEntity blockEntity = world.getBlockEntity(pos);

        if (!(blockEntity instanceof ModelBlockEntity modelBlockEntity))
        {
            return ActionResult.PASS;
        }

        ModelProperties properties = modelBlockEntity.getProperties();

        properties.setForm(form);
        properties.setName(sourceState.getBlock().getName().getString());
        properties.setHitbox(true);

        float hardness = sourceState.getHardness(world, pos);

        if (hardness >= 0F)
        {
            properties.setHardness(hardness);
        }

        modelBlockEntity.markDirty();
        world.updateListeners(pos, modelState, modelState, 3);

        return ActionResult.SUCCESS;
    }

    public static BlockForm createBlockForm(World world, BlockPos pos, BlockState state)
    {
        BlockForm form = new BlockForm();

        form.blockState.set(state);

        BlockEntity sourceEntity = world.getBlockEntity(pos);

        if (sourceEntity != null)
        {
            NbtCompound nbt = sourceEntity.createNbt(world.getRegistryManager());

            nbt.putInt("x", 0);
            nbt.putInt("y", 0);
            nbt.putInt("z", 0);
            form.blockEntityNbt.set(nbt.toString());
        }

        return form;
    }
}
