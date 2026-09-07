package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.utils.colors.Color;

public class TrailForm extends Form
{
    public final ValueLink texture = new ValueLink("texture", Link.assets("textures/default_trail.png"));
    public final ValueColor color = new ValueColor("color", new Color(1F, 1F, 1F, 1F));
    public final ValueFloat length = new ValueFloat("length", 10F);
    public final ValueBoolean loop = new ValueBoolean("loop", false);
    public final ValueBoolean paused = new ValueBoolean("paused", false);

    public TrailForm()
    {
        this.add(this.texture);
        this.add(this.color);
        this.add(this.length);
        this.add(this.loop);
        this.add(this.paused);
    }
}
