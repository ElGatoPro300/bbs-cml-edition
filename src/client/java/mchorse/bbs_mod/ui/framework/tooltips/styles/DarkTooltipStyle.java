package mchorse.bbs_mod.ui.framework.tooltips.styles;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.colors.Colors;

public class DarkTooltipStyle extends TooltipStyle
{
    @Override
    public void renderBackground(UIContext context, Area area)
    {
        int color = BBSSettings.primaryColor.get();

        context.batcher.dropShadow(area.x, area.y, area.ex(), area.ey(), 6, Colors.A25 + color, color);
        area.render(context.batcher, Colors.A100);
    }

    @Override
    public int getTextColor()
    {
        return Colors.WHITE;
    }

    @Override
    public int getForegroundColor()
    {
        return BBSSettings.primaryColor.get();
    }
}