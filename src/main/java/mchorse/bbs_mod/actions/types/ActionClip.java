package mchorse.bbs_mod.actions.types;

import mchorse.bbs_mod.actions.SuperFakePlayer;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.clips.Clip;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

public abstract class ActionClip extends Clip
{
    public final ValueInt frequency = new ValueInt("frequency", 0, 0, 1000);

    public ActionClip()
    {
        this.add(this.frequency);
    }

    public boolean isClient()
    {
        return false;
    }

    public final void applyClient(IEntity entity, Film film, Replay replay, int tick)
    {
        this.applyClientCrossing(entity, film, replay, tick - 1F, tick);
    }

    /**
     * Fire client actions whose start (and frequency hits) cross {@code (prevTime, currTime]}.
     */
    public final void applyClientCrossing(IEntity entity, Film film, Replay replay, float prevTime, float currTime)
    {
        if (!this.enabled.get() || currTime <= prevTime)
        {
            return;
        }

        int frequency = this.frequency.get();
        float start = this.tick.get();

        if (frequency <= 0)
        {
            if (prevTime < start && currTime >= start)
            {
                this.applyClientAction(entity, film, replay, Math.round(start));
            }

            return;
        }

        float first = start;

        if (first <= prevTime)
        {
            float steps = (float) Math.floor((prevTime - start) / frequency) + 1F;

            first = start + steps * frequency;
        }

        for (float t = first; t <= currTime + 1e-4F; t += frequency)
        {
            if (t > prevTime)
            {
                this.applyClientAction(entity, film, replay, Math.round(t));
            }
        }
    }

    protected void applyClientAction(IEntity entity, Film film, Replay replay, int tick)
    {}

    public final void apply(LivingEntity actor, SuperFakePlayer player, Film film, Replay replay, int tick)
    {
        this.applyCrossing(actor, player, film, replay, tick - 1F, tick);
    }

    /**
     * Fire server actions whose start (and frequency hits) cross {@code (prevTime, currTime]}.
     *
     * @return how many action applications ran
     */
    public final int applyCrossing(LivingEntity actor, SuperFakePlayer player, Film film, Replay replay, float prevTime, float currTime)
    {
        if (!this.enabled.get() || currTime <= prevTime)
        {
            return 0;
        }

        int frequency = this.frequency.get();
        float start = this.tick.get();
        int fired = 0;

        if (frequency <= 0)
        {
            if (prevTime < start && currTime >= start)
            {
                this.applyAction(actor, player, film, replay, Math.round(start));
                fired += 1;
            }

            return fired;
        }

        float first = start;

        if (first <= prevTime)
        {
            float steps = (float) Math.floor((prevTime - start) / frequency) + 1F;

            first = start + steps * frequency;
        }

        for (float t = first; t <= currTime + 1e-4F; t += frequency)
        {
            if (t > prevTime)
            {
                this.applyAction(actor, player, film, replay, Math.round(t));
                fired += 1;
            }
        }

        return fired;
    }

    public void applyAction(LivingEntity actor, SuperFakePlayer player, Film film, Replay replay, int tick)
    {}

    protected void applyPositionRotation(SuperFakePlayer player, Replay replay, int tick)
    {
        ReplayKeyframes keyframes = replay.keyframes;

        player.setPosition(keyframes.x.interpolate(tick), keyframes.y.interpolate(tick), keyframes.z.interpolate(tick));
        player.setYaw(keyframes.yaw.interpolate(tick).floatValue());
        player.setHeadYaw(keyframes.headYaw.interpolate(tick).floatValue());
        player.setBodyYaw(keyframes.bodyYaw.interpolate(tick).floatValue());
        player.setPitch(keyframes.pitch.interpolate(tick).floatValue());
        player.setStackInHand(Hand.MAIN_HAND, keyframes.mainHand.interpolate(tick, ItemStack.EMPTY).copy());
        player.setStackInHand(Hand.OFF_HAND, keyframes.offHand.interpolate(tick, ItemStack.EMPTY).copy());
    }
}
