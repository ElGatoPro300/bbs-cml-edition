package mchorse.bbs_mod.actions.types.blocks;

import mchorse.bbs_mod.actions.SuperFakePlayer;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.mc.ValueBlockState;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.utils.clips.Clip;

import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.util.math.BlockPos;

public class PlaceBlockActionClip extends BlockActionClip
{
    public final ValueBlockState state = new ValueBlockState("state");
    public final ValueBoolean drop = new ValueBoolean("drop", false);
    public final ValueString blockEntityNbt = new ValueString("block_entity_nbt", "");

    public PlaceBlockActionClip()
    {
        super();

        this.add(this.state);
        this.add(this.drop);
        this.add(this.blockEntityNbt);
    }

    @Override
    public void applyAction(LivingEntity actor, SuperFakePlayer player, Film film, Replay replay, int tick)
    {
        BlockPos pos = new BlockPos(this.x.get(), this.y.get(), this.z.get());

        if (this.state.get().getBlock() == Blocks.AIR)
        {
            player.getEntityWorld().breakBlock(pos, this.drop.get());
        }
        else
        {
            player.getEntityWorld().setBlockState(pos, this.state.get());

            String nbtString = this.blockEntityNbt.get();

            if (!nbtString.isEmpty())
            {
                try
                {
                    NbtCompound nbt = StringNbtReader.readCompound(nbtString);
                    nbt.putInt("x", pos.getX());
                    nbt.putInt("y", pos.getY());
                    nbt.putInt("z", pos.getZ());
                    BlockEntity created = BlockEntity.createFromNbt(pos, this.state.get(), nbt, player.getEntityWorld().getRegistryManager());

                    if (created != null)
                    {
                        player.getEntityWorld().removeBlockEntity(pos);
                        player.getEntityWorld().addBlockEntity(created);
                        created.markDirty();
                        player.getEntityWorld().updateListeners(pos, this.state.get(), this.state.get(), 3);
                    }
                }
                catch (Exception ignored)
                {}
            }
        }
    }

    @Override
    protected Clip create()
    {
        return new PlaceBlockActionClip();
    }
}
