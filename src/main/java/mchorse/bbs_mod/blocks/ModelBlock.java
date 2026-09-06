package mchorse.bbs_mod.blocks;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.network.ServerNetwork;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.Waterloggable;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BlockStateComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

import org.joml.Vector3f;

import java.util.Map;

import org.jetbrains.annotations.Nullable;

public class ModelBlock extends Block implements BlockEntityProvider, Waterloggable
{
    public static final IntProperty LIGHT_LEVEL = IntProperty.of("light_level", 0, 15);

    public static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> validateTicker(BlockEntityType<A> givenType, BlockEntityType<E> expectedType, BlockEntityTicker<? super E> ticker)
    {
        return expectedType == givenType ? (BlockEntityTicker<A>) ticker : null;
    }

    public ModelBlock(Settings settings)
    {
        super(settings);

        this.setDefaultState(getDefaultState()
            .with(Properties.WATERLOGGED, false)
            .with(LIGHT_LEVEL, 0));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder)
    {
        builder.add(Properties.WATERLOGGED, LIGHT_LEVEL);
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx)
    {
        return this.getDefaultState()
            .with(Properties.WATERLOGGED, ctx.getWorld().getFluidState(ctx.getBlockPos()).isOf(Fluids.WATER));
    }

    @Override
    public ItemStack getPickStack(WorldView world, BlockPos pos, BlockState state, boolean includeData)
    {
        BlockEntity entity = world.getBlockEntity(pos);

        if (entity instanceof ModelBlockEntity modelBlock)
        {
            ItemStack stack = new ItemStack(this);
            stack.set(DataComponentTypes.BLOCK_ENTITY_DATA, NbtComponent.of(modelBlock.createNbtWithId(world.getRegistryManager())));
            
            stack.set(DataComponentTypes.BLOCK_STATE, new BlockStateComponent(Map.of("light_level", String.valueOf(modelBlock.getProperties().getLightLevel()))));

            return stack;
        }

        return super.getPickStack(world, pos, state, includeData);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state)
    {
        return BlockRenderType.INVISIBLE;
    }

    protected boolean isTransparent(BlockState state, BlockView world, BlockPos pos)
    {
        return true;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type)
    {
        return validateTicker(type, BBSMod.MODEL_BLOCK_ENTITY, (w, p, s, e) -> ModelBlockEntity.tick(w, p, s, e));
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state)
    {
        return new ModelBlockEntity(pos, state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context)
    {
        try
        {
            if (world instanceof World w)
            {
                BlockEntity be = w.getBlockEntity(pos);

                if (be instanceof ModelBlockEntity model && model.getProperties().isHitbox())
                {
                    Form form = model.getProperties().getForm();

                    if (form != null && form.hitbox.get())
                    {
                        float width = form.hitboxWidth.get();
                        float height = form.hitboxHeight.get();

                        if (width > 0F && height > 0F)
                        {
                            float halfWidth = width / 2F;

                            double minX = 0.5D - halfWidth;
                            double maxX = 0.5D + halfWidth;
                            double minZ = 0.5D - halfWidth;
                            double maxZ = 0.5D + halfWidth;
                            double minY = 0D;
                            double maxY = height;

                            minX = Math.max(0D, minX);
                            minZ = Math.max(0D, minZ);
                            maxX = Math.min(1D, maxX);
                            maxZ = Math.min(1D, maxZ);
                            maxY = Math.min(1D, maxY);

                            if (minX < maxX && minZ < maxZ && maxY > minY)
                            {
                                return VoxelShapes.cuboid(minX, minY, minZ, maxX, maxY, maxZ);
                            }
                        }
                    }

                    return VoxelShapes.fullCube();
                }
            }
        }
        catch (Exception e)
        {

        }

        return VoxelShapes.empty();
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit)
    {
        if (player instanceof ServerPlayerEntity serverPlayer)
        {
            ServerNetwork.sendClickedModelBlock(serverPlayer, pos);
        }

        return ActionResult.SUCCESS;
    }

    /* Waterloggable implementation */

    @Override
    public FluidState getFluidState(BlockState state)
    {
        return state.get(Properties.WATERLOGGED) ? Fluids.WATER.getStill(false) : super.getFluidState(state);
    }

    @Override
    public void afterBreak(World world, PlayerEntity player, BlockPos pos, BlockState state, BlockEntity be, ItemStack tool)
    {
        if (!world.isClient && !player.getAbilities().creativeMode)
        {
            if (be instanceof ModelBlockEntity model)
            {
                ItemStack stack = new ItemStack(this);
                stack.set(DataComponentTypes.BLOCK_ENTITY_DATA, NbtComponent.of(model.createNbtWithId(world.getRegistryManager())));
                
                stack.set(DataComponentTypes.BLOCK_STATE, new BlockStateComponent(Map.of("light_level", String.valueOf(model.getProperties().getLightLevel()))));

                ItemScatterer.spawn(world, pos, DefaultedList.ofSize(1, stack));
            }
        }

        super.afterBreak(world, player, pos, state, be, tool);
    }
}
