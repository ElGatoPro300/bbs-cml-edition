package mchorse.bbs_mod.ui.framework.theme;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;

import java.util.List;

/**
 * Style provider interface for custom UI themes.
 */
public interface IUIStyleProvider
{
    public String getId();

    public String getName();

    public int getPrimaryColor();

    public int getBackgroundColor();

    public int getPanelBackgroundColor();

    public int getTextColor();

    public int getTooltipBackgroundColor();

    public int getTooltipTextColor();

    default public boolean renderButtonSkin(UIContext context, UIButton button)
    {
        return false;
    }

    default public boolean renderToggleSkin(UIContext context, UIToggle toggle)
    {
        return false;
    }

    default public boolean renderTrackpadSkin(UIContext context, UITrackpad trackpad)
    {
        return false;
    }

    default public boolean renderTextboxSkin(UIContext context, UITextbox textbox)
    {
        return false;
    }

    default public boolean renderTooltip(UIContext context, int x, int y, List<String> lines)
    {
        return false;
    }
}
