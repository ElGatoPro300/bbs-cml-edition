package mchorse.bbs_mod.utils.clips;

import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;

public abstract class Clip extends ValueGroup
{
    /**
     * Soft upper bound for clip duration (ticks). Extremely large values
     * (e.g. 9999999999 in film data) overflow int on load and freeze the
     * camera timeline ruler when it iterates the full span.
     * 1_000_000 ticks ≈ 13.9 h at 20 TPS — enough for long static holds.
     */
    public static final int MAX_DURATION_TICKS = 1_000_000;

    public final ValueBoolean enabled = new ValueBoolean("enabled", true);
    public final ValueString title = new ValueString("title", "");
    public final ValueInt layer = new ValueInt("layer", 0, 0, Integer.MAX_VALUE);
    public final ValueInt tick = new ValueInt("tick", 0, 0, Integer.MAX_VALUE);
    public final ValueInt duration = new ValueInt("duration", 1, 1, MAX_DURATION_TICKS);
    public final Envelope envelope = new Envelope("envelope");

    public Clip()
    {
        super("");

        this.add(this.enabled);
        this.add(this.title);
        this.add(this.layer);
        this.add(this.tick);
        this.add(this.duration);
        this.add(this.envelope);
    }

    public boolean isGlobal()
    {
        return false;
    }

    public boolean isInside(int tick)
    {
        int offset = this.tick.get();

        return tick >= offset && (long) tick < (long) offset + this.duration.get();
    }

    public void shift(double dx, double dy, double dz)
    {}

    public void shiftLeft(int tick)
    {
        this.shiftLeft(tick, false);
    }

    public void shiftLeft(int tick, boolean direct)
    {}

    public Clip copy()
    {
        Clip clip = this.create();

        clip.copy(this);

        return clip;
    }

    protected abstract Clip create();

    /**
     * Breakdown this fixture into another piece starting at given offset
     */
    public Clip breakDown(int offset)
    {
        int duration = this.duration.get();

        if (offset <= 0 || offset >= duration)
        {
            return null;
        }

        Clip clip = this.copy();

        clip.duration.set(duration - offset);
        clip.breakDownClip(this, offset);

        return clip;
    }

    protected void breakDownClip(Clip original, int offset)
    {
        this.envelope.breakDown(original, offset);
    }
}