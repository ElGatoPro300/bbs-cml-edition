package mchorse.bbs_mod.blocks;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.blocks.entities.TriggerBlockEntity;
import mchorse.bbs_mod.network.ServerNetwork;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

import org.joml.Vector3f;

import org.jetbrains.annotations.Nullable;

public class TriggerBlock extends Block implements BlockEntityProvider
{
    public TriggerBlock(Settings settings)
    {
        super(settings);
    }

    @Override
    public ItemStack getPickStack(WorldView world, BlockPos pos, BlockState state)
    {
        BlockEntity entity = world.getBlockEntity(pos);

        if (entity instanceof TriggerBlockEntity triggerBlock)
        {
            ItemStack stack = new ItemStack(this);
            stack.set(DataComponentTypes.BLOCK_ENTITY_DATA, NbtComponent.of(triggerBlock.createNbtWithId(world.getRegistryManager())));

            return stack;
        }

        return super.getPickStack(world, pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state)
    {
        return BlockRenderType.INVISIBLE;
    }

    @Override
    public float getAmbientOcclusionLightLevel(BlockState state, BlockView world, BlockPos pos)
    {
        return 1.0F;
    }

    @Override
    public boolean isTransparent(BlockState state, BlockView world, BlockPos pos)
    {
        return true;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state)
    {
        return new TriggerBlockEntity(pos, state);
    }

    @Override
    public void onBlockBreakStart(BlockState state, World world, BlockPos pos, PlayerEntity player)
    {
        if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer && !player.isCreative())
        {
            BlockEntity be = world.getBlockEntity(pos);

            if (be instanceof TriggerBlockEntity triggerBlock)
            {
                triggerBlock.trigger(serverPlayer, false);
            }
        }

        super.onBlockBreakStart(state, world, pos, player);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit)
    {
        if (player.getMainHandStack().isEmpty())
        {
            if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer)
            {
                if (!player.isCreative() || (player.isCreative() && player.isSneaking()))
                {
                    BlockEntity be = world.getBlockEntity(pos);

                    if (be instanceof TriggerBlockEntity triggerBlock)
                    {
                        triggerBlock.trigger(serverPlayer, true);

                        return ActionResult.SUCCESS;
                    }
                }
                else
                {
                    ServerNetwork.sendClickedTriggerBlock(serverPlayer, pos);

                    return ActionResult.SUCCESS;
                }
            }

            return ActionResult.SUCCESS;
        }

        return super.onUse(state, world, pos, player, hit);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context)
    {
        return this.getShape(world, pos);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context)
    {
        try
        {
            BlockEntity be = world.getBlockEntity(pos);

            if (be instanceof TriggerBlockEntity trigger)
            {
                if (!trigger.collidable.get())
                {
                    return VoxelShapes.empty();
                }

                return this.getShape(world, pos);
            }
        }
        catch (Exception e)
        {
        }

        /* Never fall back to a solid full cube for a non-solid block: an exception or
         * a missing block entity here must not eject the player through the world. */
        return VoxelShapes.empty();
    }

    private VoxelShape getShape(BlockView world, BlockPos pos)
    {
        try
        {
            BlockEntity be = world.getBlockEntity(pos);

            if (be instanceof TriggerBlockEntity trigger)
            {
                Vector3f min = trigger.pos1.get();
                Vector3f max = trigger.pos2.get();

                if (min == null || max == null)
                {
                    return VoxelShapes.empty();
                }

                double minX = Math.max(0D, Math.min(min.x, max.x));
                double minY = Math.max(0D, Math.min(min.y, max.y));
                double minZ = Math.max(0D, Math.min(min.z, max.z));
                double maxX = Math.min(1D, Math.max(min.x, max.x));
                double maxY = Math.min(1D, Math.max(min.y, max.y));
                double maxZ = Math.min(1D, Math.max(min.z, max.z));

                if (minX < maxX && minY < maxY && minZ < maxZ)
                {
                    return VoxelShapes.cuboid(minX, minY, minZ, maxX, maxY, maxZ);
                }
            }
        }
        catch (Exception e)
        {
        }

        return VoxelShapes.empty();
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type)
    {
        return type == BBSMod.TRIGGER_BLOCK_ENTITY ? (BlockEntityTicker<T>) (BlockEntityTicker<TriggerBlockEntity>) (w, p, s, e) -> TriggerBlockEntity.tick(w, p, s, e) : null;
    }
}
