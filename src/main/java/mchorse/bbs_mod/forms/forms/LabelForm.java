package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.colors.Color;

public class LabelForm extends Form
{
    public final ValueString text = new ValueString("text", "Hello, World!");
    public final ValueBoolean billboard = new ValueBoolean("billboard", false);
    public final ValueBoolean nametag = new ValueBoolean("nametag", false);
    public final ValueColor color = new ValueColor("color", new Color(1F, 1F, 1F, 1F));

    public final ValueInt max = new ValueInt("max", -1);
    public final ValueInt wrapLineGap = new ValueInt("wrapLineGap", 2);
    public final ValueFloat anchorX = new ValueFloat("anchorX", 0.5F);
    public final ValueFloat anchorY = new ValueFloat("anchorY", 0.5F);
    public final ValueBoolean anchorLines = new ValueBoolean("anchorLines", false);

    /* Shadow properties */
    public final ValueFloat shadowX = new ValueFloat("shadowX", 1F);
    public final ValueFloat shadowY = new ValueFloat("shadowY", 1F);
    public final ValueColor shadowColor = new ValueColor("shadowColor", new Color(0, 0, 0, 0));

    /* Background */
    public final ValueColor background = new ValueColor("background", new Color(0, 0, 0, 0));
    public final ValueFloat offset = new ValueFloat("offset", 3F);

    /* Advanced Text Properties */
    public final ValueString font = new ValueString("font", "");
    public final ValueFloat fontSize = new ValueFloat("fontSize", 1.0F);
    public final ValueInt fontWeight = new ValueInt("fontWeight", 400); /* 100-900 */
    public final ValueInt fontStyle = new ValueInt("fontStyle", 0); /* 0: Normal, 1: Italic, 2: Oblique */
    public final ValueFloat letterSpacing = new ValueFloat("letterSpacing", 0F);
    public final ValueFloat lineHeight = new ValueFloat("lineHeight", 0F);
    public final ValueInt textAlign = new ValueInt("textAlign", 0); /* 0: Left, 1: Center, 2: Right, 3: Justify */

    /* Decorations */
    public final ValueBoolean underline = new ValueBoolean("underline", false);
    public final ValueBoolean strikethrough = new ValueBoolean("strikethrough", false);

    /* Special Effects */
    public final ValueFloat shadowBlur = new ValueFloat("shadowBlur", 0F);
    public final ValueBoolean outline = new ValueBoolean("outline", false);
    public final ValueColor outlineColor = new ValueColor("outlineColor", new Color(0, 0, 0, 1));
    public final ValueFloat outlineWidth = new ValueFloat("outlineWidth", 1F);

    /* Gradient */
    public final ValueBoolean gradient = new ValueBoolean("gradient", false);
    public final ValueColor gradientEndColor = new ValueColor("gradientEndColor", Color.white());
    public final ValueFloat gradientOffset = new ValueFloat("gradientOffset", 0.5F);

    public LabelForm()
    {
        super();

        this.add(this.text);
        this.add(this.billboard);
        this.add(this.nametag);
        this.add(this.color);
        this.add(this.max);
        this.add(this.wrapLineGap);
        this.add(this.anchorX);
        this.add(this.anchorY);
        this.add(this.anchorLines);
        this.add(this.shadowX);
        this.add(this.shadowY);
        this.add(this.shadowColor);
        this.add(this.background);
        this.add(this.offset);

        this.add(this.font);
        this.add(this.fontSize);
        this.add(this.fontWeight);
        this.add(this.fontStyle);
        this.add(this.letterSpacing);
        this.add(this.lineHeight);
        this.add(this.textAlign);
        this.add(this.underline);
        this.add(this.strikethrough);
        this.add(this.shadowBlur);
        this.add(this.outline);
        this.add(this.outlineColor);
        this.add(this.outlineWidth);
        this.add(this.gradient);
        this.add(this.gradientEndColor);
        this.add(this.gradientOffset);
    }

    @Override
    public String getDefaultDisplayName()
    {
        return this.text.get();
    }
}