package mchorse.bbs_mod.camera.clips.misc;

import mchorse.bbs_mod.camera.clips.CameraClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;

import java.util.function.Predicate;

public class VideoClip extends CameraClip
{
    public static final Predicate<Clip> NO_VIDEO = (clip) -> !(clip instanceof VideoClip);

    public ValueString video = new ValueString("video", "");
    public ValueInt offset = new ValueInt("offset", 0, Integer.MIN_VALUE, Integer.MAX_VALUE);
    /* Volumen del clip: 0–100 */
    public ValueInt volume = new ValueInt("volume", 50, 0, 100);
    public ValueInt x = new ValueInt("x", 0);
    public ValueInt y = new ValueInt("y", 0);
    public ValueInt width = new ValueInt("width", 100);
    public ValueInt height = new ValueInt("height", 100);
    /* Recorte por lados en porcentaje (0-100): izquierda, arriba, derecha, abajo. */
    public ValueInt cropX = new ValueInt("cropX", 0, 0, 100);
    public ValueInt cropY = new ValueInt("cropY", 0, 0, 100);
    public ValueInt cropWidth = new ValueInt("cropWidth", 0, 0, 100);
    public ValueInt cropHeight = new ValueInt("cropHeight", 0, 0, 100);
    /* Opacidad interna en 0-1 para el render, UI en porcentaje 0-100. */
    public ValueFloat opacity = new ValueFloat("opacity", 1.0F, 0.0F, 1.0F);
    /* Por defecto el loop queda desactivado. */
    public ValueBoolean loops = new ValueBoolean("loops", false);
    public ValueBoolean global = new ValueBoolean("global", false);

    public VideoClip()
    {
        super();

        this.add(this.video);
        this.add(this.offset);
        this.add(this.volume);
        this.add(this.x);
        this.add(this.y);
        this.add(this.width);
        this.add(this.height);
        this.add(this.cropX);
        this.add(this.cropY);
        this.add(this.cropWidth);
        this.add(this.cropHeight);
        this.add(this.opacity);
        this.add(this.loops);
        this.add(this.global);
    }

    @Override
    public void shiftLeft(int tick, boolean direct)
    {
        super.shiftLeft(tick, direct);

        int newOffset = this.offset.get() - Math.round(this.tick.get() - tick);

        if (direct)
        {
            this.offset.setDirect(newOffset);
        }
        else
        {
            this.offset.set(newOffset);
        }
    }

    @Override
    public boolean isPositionClip()
    {
        return false;
    }

    @Override
    protected void applyClip(ClipContext context, Position position)
    {}

    @Override
    protected Clip create()
    {
        return new VideoClip();
    }

    @Override
    protected void breakDownClip(Clip original, int offset)
    {
        super.breakDownClip(original, offset);

        this.offset.set(this.offset.get() + offset);
    }
}