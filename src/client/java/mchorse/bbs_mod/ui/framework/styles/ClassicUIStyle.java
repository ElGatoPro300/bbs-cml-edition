package mchorse.bbs_mod.ui.framework.styles;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * Legacy BBS look — mirrors the pre-Minecut widget drawing.
 */
public class ClassicUIStyle extends UIStyle
{
    @Override
    public int chrome()
    {
        return 0xFF141418;
    }

    @Override
    public int panel()
    {
        return 0xFF1A1A22;
    }

    @Override
    public int elevated()
    {
        return 0xFF1D2127;
    }

    @Override
    public int inner()
    {
        return 0xFF222228;
    }

    @Override
    public int border()
    {
        return 0xFF30353D;
    }

    @Override
    public int borderSoft()
    {
        return 0xFF2A2A30;
    }

    @Override
    public int accent()
    {
        return 0xFF000000 | (BBSSettings.primaryColor.get() & Colors.RGB);
    }

    @Override
    public int accentDim()
    {
        return 0xFF000000 | Colors.mulRGB(BBSSettings.primaryColor.get(), 0.66F);
    }

    @Override
    public int text()
    {
        return Colors.WHITE;
    }

    @Override
    public int textDim()
    {
        return 0xFFAAAAAA;
    }

    @Override
    public int textMuted()
    {
        return 0xFF777777;
    }

    @Override
    public void drawPanel(Batcher2D batcher, int x, int y, int w, int h)
    {
        batcher.box(x, y, x + w, y + h, this.panel());
    }

    @Override
    public void drawPanel(Batcher2D batcher, Area area)
    {
        this.drawPanel(batcher, area.x, area.y, area.w, area.h);
    }

    @Override
    public void drawSoftRect(Batcher2D batcher, int x, int y, int w, int h, int color)
    {
        batcher.box(x, y, x + w, y + h, color);
    }

    @Override
    public void drawButton(UIContext context, Area area, int baseRgb, boolean hover, boolean customAccent,
        boolean nLeft, boolean nRight, boolean nTop, boolean nBottom)
    {
        int base = baseRgb & Colors.RGB;
        int fill;
        int border;

        if (hover)
        {
            fill = 0xFF000000 | Colors.mulRGB(base, 1.18F);
            border = 0xFF000000 | Colors.mulRGB(base, 1.5F);
        }
        else
        {
            fill = 0xFF000000 | base;
            border = 0xFF000000 | Colors.mulRGB(base, 0.5F);
        }

        int x1 = area.x;
        int y1 = area.y;
        int x2 = area.ex();
        int y2 = area.ey();

        if (!nBottom)
        {
            context.batcher.box(x1, y1 + 2, x2, y2 + 2, 0x55000000);
        }

        context.batcher.box(x1, y1, x2, y2, fill);

        if (!nBottom)
        {
            context.batcher.box(x1, y2 - 2, x2, y2, 0xFF000000 | Colors.mulRGB(base, hover ? 0.9F : 0.66F));
        }

        if (!nTop)
        {
            context.batcher.box(x1, y1, x2, y1 + 1, border);
        }

        if (!nBottom)
        {
            context.batcher.box(x1, y2 - 1, x2, y2, border);
        }

        if (!nLeft)
        {
            context.batcher.box(x1, y1, x1 + 1, y2, border);
        }

        if (!nRight)
        {
            context.batcher.box(x2 - 1, y1, x2, y2, border);
        }
    }

    @Override
    public void drawListSelection(Batcher2D batcher, Area area, boolean selected, boolean hover)
    {
        if (selected)
        {
            batcher.box(area.x, area.y, area.ex(), area.ey(), Colors.A50 | (BBSSettings.primaryColor.get() & Colors.RGB));
        }
        else if (hover)
        {
            batcher.box(area.x, area.y, area.ex(), area.ey(), Colors.A25 | (BBSSettings.primaryColor.get() & Colors.RGB));
        }
    }

    @Override
    public void drawOverlayChrome(Batcher2D batcher, Area area)
    {
        batcher.box(area.x, area.y, area.ex(), area.ey(), 0xFF141418);
        batcher.outline(area.x, area.y, area.ex(), area.ey(), 0xFF1A1A22);
    }

    @Override
    public void drawFormCell(Batcher2D batcher, int x, int y, int w, int h, boolean selected, boolean hover)
    {
        int bg = selected
            ? (Colors.A50 | (BBSSettings.primaryColor.get() & Colors.RGB))
            : (hover ? 0x44000000 : 0x33000000);

        batcher.box(x, y, x + w, y + h, bg);

        if (selected)
        {
            batcher.outline(x, y, x + w, y + h, 0xFF000000 | (BBSSettings.primaryColor.get() & Colors.RGB));
        }
    }

    @Override
    public void drawTabUnderline(Batcher2D batcher, int x, int y, int textWidth, boolean active)
    {
        if (active)
        {
            batcher.box(x - 2, y + 10, x + textWidth + 2, y + 12, this.accent());
        }
    }
}
