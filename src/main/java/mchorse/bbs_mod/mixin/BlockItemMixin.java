package mchorse.bbs_mod.mixin;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.actions.types.blocks.PlaceBlockActionClip;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.TypedEntityData;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public class BlockItemMixin
{
    @Inject(method = "place(Lnet/minecraft/item/ItemPlacementContext;Lnet/minecraft/block/BlockState;)Z", at = @At("RETURN"))
    public void onPlace(ItemPlacementContext context, BlockState state, CallbackInfoReturnable<Boolean> info)
    {
        if (info.getReturnValue() && context.getPlayer() instanceof ServerPlayerEntity player)
        {
            BBSMod.getActions().addAction(player, () ->
            {
                PlaceBlockActionClip clip = new PlaceBlockActionClip();
                BlockPos pos = context.getBlockPos();
                BlockState placedState = context.getWorld().getBlockState(pos);
                BlockEntity blockEntity = context.getWorld().getBlockEntity(pos);

                clip.x.set(pos.getX());
                clip.y.set(pos.getY());
                clip.z.set(pos.getZ());
                clip.state.set(placedState);

                TypedEntityData<BlockEntityType<?>> stackBlockEntityData = context.getStack().get(DataComponentTypes.BLOCK_ENTITY_DATA);

                if (stackBlockEntityData != null)
                {
                    clip.blockEntityNbt.set(stackBlockEntityData.copyNbtWithoutId().toString());
                }
                else if (blockEntity != null)
                {
                    clip.blockEntityNbt.set(blockEntity.createNbtWithIdentifyingData(context.getWorld().getRegistryManager()).toString());
                }

                return clip;
            });
        }
    }
}
