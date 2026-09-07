package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.utils.colors.Color;

public class ExtrudedForm extends Form
{
    public final ValueLink texture = new ValueLink("texture", null);
    public final ValueColor color = new ValueColor("color", new Color(1F, 1F, 1F, 1F));
    public final ValueBoolean billboard = new ValueBoolean("billboard", false);
    public final ValueBoolean shading = new ValueBoolean("shading", true);
    public final ValueFloat pbrNormalIntensity = new ValueFloat("pbr_normal_intensity", 1F, 0F, 4F);
    public final ValueFloat pbrSpecularIntensity = new ValueFloat("pbr_specular_intensity", 1F, 0F, 4F);

    public ExtrudedForm()
    {
        super();

        this.add(this.texture);
        this.add(this.color);
        this.add(this.billboard);
        this.add(this.shading);
        this.add(this.pbrNormalIntensity);
        this.add(this.pbrSpecularIntensity);
    }

    @Override
    public String getDefaultDisplayName()
    {
        Link link = this.texture.get();

        return link == null ? "none" : link.toString();
    }
}