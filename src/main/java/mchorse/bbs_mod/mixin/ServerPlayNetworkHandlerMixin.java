package mchorse.bbs_mod.mixin;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.actions.types.blocks.CloseContainerActionClip;
import mchorse.bbs_mod.actions.types.blocks.InteractBlockActionClip;
import mchorse.bbs_mod.actions.types.chat.CommandActionClip;

import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import com.mojang.brigadier.ParseResults;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayNetworkHandler.class)
public class ServerPlayNetworkHandlerMixin
{
    private static final Map<UUID, BlockPos> OPEN_CONTAINERS = new HashMap<>();

    @Shadow
    public ServerPlayerEntity player;

    @Inject(method = "parse", at = @At("HEAD"))
    public void onParse(String command, CallbackInfoReturnable<ParseResults<ServerCommandSource>> info)
    {
        BBSMod.getActions().addAction(this.player, () ->
        {
            CommandActionClip clip = new CommandActionClip();

            clip.command.set(command);

            return clip;
        });
    }

    @Redirect(method = "onPlayerInteractBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerInteractionManager;interactBlock(Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/Hand;Lnet/minecraft/util/hit/BlockHitResult;)Lnet/minecraft/util/ActionResult;"))
    private ActionResult redirectOnBlockInteract(ServerPlayerInteractionManager manager, ServerPlayerEntity player, World world, ItemStack stack, Hand hand, BlockHitResult hitResult)
    {
        BlockPos interactedPos = hitResult.getBlockPos();

        BBSMod.getActions().addAction(this.player, () ->
        {
            InteractBlockActionClip clip = new InteractBlockActionClip();

            clip.hit.setHitResult(hitResult);
            clip.hand.set(hand == Hand.MAIN_HAND);

            return clip;
        });

        if (world.getBlockState(interactedPos).getBlock() instanceof ChestBlock)
        {
            OPEN_CONTAINERS.put(this.player.getUuid(), interactedPos.toImmutable());
        }
        else if (world.getBlockState(interactedPos).getBlock() instanceof AbstractFurnaceBlock)
        {
            OPEN_CONTAINERS.put(this.player.getUuid(), interactedPos.toImmutable());
        }

        return manager.interactBlock(player, world, stack, hand, hitResult);
    }

    @Inject(method = "onCloseHandledScreen", at = @At("HEAD"))
    private void onCloseHandledScreen(CloseHandledScreenC2SPacket packet, CallbackInfo ci)
    {
        BlockPos containerPos = OPEN_CONTAINERS.remove(this.player.getUuid());

        if (containerPos == null)
        {
            return;
        }

        BBSMod.getActions().addAction(this.player, () ->
        {
            CloseContainerActionClip clip = new CloseContainerActionClip();
            BlockState state = this.player.getEntityWorld().getBlockState(containerPos);

            clip.x.set(containerPos.getX());
            clip.y.set(containerPos.getY());
            clip.z.set(containerPos.getZ());

            if (state.getBlock() instanceof AbstractFurnaceBlock)
            {
                clip.applyState.set(true);
                clip.state.set(state);
            }

            return clip;
        });
    }
}
