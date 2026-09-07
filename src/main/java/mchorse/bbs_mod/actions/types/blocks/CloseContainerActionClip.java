package mchorse.bbs_mod.actions.types.blocks;

import mchorse.bbs_mod.actions.SuperFakePlayer;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.settings.values.mc.ValueBlockState;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.utils.clips.Clip;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;

public class CloseContainerActionClip extends BlockActionClip
{
    public final ValueBoolean applyState = new ValueBoolean("apply_state", false);
    public final ValueBlockState state = new ValueBlockState("state");

    public CloseContainerActionClip()
    {
        super();

        this.add(this.applyState);
        this.add(this.state);
    }

    @Override
    public void applyAction(LivingEntity actor, SuperFakePlayer player, Film film, Replay replay, int tick)
    {
        if (this.applyState.get())
        {
            player.getWorld().setBlockState(new BlockPos(this.x.get(), this.y.get(), this.z.get()), this.state.get());
        }

        player.closeReplayChest(replay.getId());
        player.closeHandledScreen();
    }

    @Override
    protected Clip create()
    {
        return new CloseContainerActionClip();
    }
}
