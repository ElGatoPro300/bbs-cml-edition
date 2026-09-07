package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.forms.forms.utils.VideoResolution;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.colors.Color;

public class VideoForm extends Form
{
    public final ValueString video = new ValueString("video", "");
    public final ValueBoolean billboard = new ValueBoolean("billboard", false);
    public final ValueBoolean linear = new ValueBoolean("linear", true);
    public final ValueBoolean loop = new ValueBoolean("loop", true);
    public final ValueBoolean paused = new ValueBoolean("paused", false);
    /* 720p default — Native/1080 are soft-capped to 720 decode for FPS. */
    public final ValueInt resolution = new ValueInt("resolution", VideoResolution.P720);
    public final ValueFloat speed = new ValueFloat("speed", 1F, 0.01F, 8F);
    /* Absolute video timeline position in ticks — keyframe to scrub forward/back. */
    public final ValueInt time = new ValueInt("time", 0, 0, Integer.MAX_VALUE);
    public final ValueInt offset = new ValueInt("offset", 0, Integer.MIN_VALUE, Integer.MAX_VALUE);
    public final ValueColor color = new ValueColor("color", new Color(1F, 1F, 1F, 1F));
    public final ValueFloat offsetX = new ValueFloat("offsetX", 0F);
    public final ValueFloat offsetY = new ValueFloat("offsetY", 0F);
    public final ValueFloat rotation = new ValueFloat("rotation", 0F);
    public final ValueBoolean shading = new ValueBoolean("shading", true);

    public VideoForm()
    {
        super();

        this.shading.invisible();
        /* Legacy / unused on VideoForm — keep for NBT compat, hide from tracks. */
        this.offset.invisible();
        this.offsetX.invisible();
        this.offsetY.invisible();
        this.rotation.invisible();

        this.add(this.video);
        this.add(this.billboard);
        this.add(this.linear);
        this.add(this.loop);
        this.add(this.paused);
        this.add(this.resolution);
        this.add(this.speed);
        this.add(this.time);
        this.add(this.offset);
        this.add(this.color);
        this.registerColorOverlays();
        this.add(this.offsetX);
        this.add(this.offsetY);
        this.add(this.rotation);
        this.add(this.shading);
    }

    public int getMaxLongSide()
    {
        return VideoResolution.clampPreset(this.resolution.get());
    }

    @Override
    public String getDefaultDisplayName()
    {
        String path = this.video.get();

        return path == null || path.isEmpty() ? "video" : path;
    }
}
