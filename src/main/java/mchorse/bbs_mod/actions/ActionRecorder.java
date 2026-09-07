package mchorse.bbs_mod.actions;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.actions.AttackDamage;
import mchorse.bbs_mod.actions.types.ActionClip;
import mchorse.bbs_mod.actions.types.AttackActionClip;
import mchorse.bbs_mod.actions.types.SwipeActionClip;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.utils.clips.Clips;

import net.minecraft.server.network.ServerPlayerEntity;

public class ActionRecorder
{
    private Film film;
    private ServerPlayerEntity entity;
    private Clips clips = new Clips("...", BBSMod.getFactoryActionClips());
    private float tick;
    private int countdown;
    private int initialTick;
    private int actionsThisTick;

    public ActionRecorder(Film film, ServerPlayerEntity entity, int tick, int countdown)
    {
        this.film = film;
        this.entity = entity;
        this.tick = tick;
        this.countdown = countdown;
        this.initialTick = tick;
    }

    public Film getFilm()
    {
        return this.film;
    }

    public Clips getClips()
    {
        return this.clips;
    }

    public int getInitialTick()
    {
        return this.initialTick;
    }

    public Clips composeClips()
    {
        Clips clips = this.clips;

        clips.sortLayers();

        return clips;
    }

    public void add(ActionClip clip)
    {
        if (this.countdown > 0)
        {
            return;
        }

        /* Multiple events in one world tick keep sub-tick spacing so export can
         * replay them on consecutive frames instead of stacking on the same tick. */
        float step = this.getSubTickStep();
        float placed = this.tick + this.actionsThisTick * step;

        clip.tick.set(placed);
        clip.duration.set(1);

        this.clips.addClip(clip);
        this.actionsThisTick += 1;
    }

    private float getSubTickStep()
    {
        int fps = 60;

        if (BBSSettings.videoSettings != null && BBSSettings.videoSettings.frameRate != null)
        {
            fps = Math.max(20, BBSSettings.videoSettings.frameRate.get());
        }

        return 20F / fps;
    }

    public void tick(ServerPlayerEntity player)
    {
        if (this.countdown > 0)
        {
            this.countdown -= 1;

            return;
        }

        if (player.handSwingTicks == -1)
        {
            this.add(new SwipeActionClip());

            if (BBSSettings.recordingSwipeDamage.get())
            {
                AttackActionClip clip = new AttackActionClip();

                clip.damage.set(AttackDamage.fromAttacker(player, null));
                this.add(clip);
            }
        }

        this.tick += 1F;
        this.actionsThisTick = 0;
    }
}
