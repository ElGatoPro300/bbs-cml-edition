package mchorse.bbs_mod.ui.framework.styles;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.settings.UiStyleCapabilities;
import mchorse.bbs_mod.ui.film.FilmUiCapabilities;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.utils.Area;

/**
 * Selectable GUI chrome strategy. Classic stays in core; Minecut chrome is
 * supplied by the optional film UI addon via {@link FilmUiCapabilities}.
 */
public abstract class UIStyle
{
    public static final int CLASSIC = 0;
    public static final int ADDON = 1;

    /**
     * @deprecated Use {@link #ADDON} instead.
     */
    @Deprecated
    public static final int MINECUT = ADDON;

    private static UIStyle classic;
    private static UIStyle addon;

    public static UIStyle active()
    {
        if (isAddon())
        {
            if (addon == null)
            {
                addon = FilmUiCapabilities.createAddonStyle();
            }

            if (addon != null)
            {
                return addon;
            }
        }

        if (classic == null)
        {
            classic = new ClassicUIStyle();
        }

        return classic;
    }

    /**
     * True only when an addon UI style is present and the user selected it.
     */
    public static boolean isAddon()
    {
        return (FilmUiCapabilities.hasAddon() || UiStyleCapabilities.isAddonStyleAvailable())
            && BBSSettings.uiStyle != null
            && BBSSettings.uiStyle.get() == ADDON;
    }

    /**
     * @deprecated Use {@link #isAddon()} instead.
     */
    @Deprecated
    public static boolean isMinecut()
    {
        return isAddon();
    }

    /** Drop cached addon instance after addon reload / style switch. */
    public static void invalidateAddonCache()
    {
        addon = null;
    }

    /**
     * @deprecated Use {@link #invalidateAddonCache()} instead.
     */
    @Deprecated
    public static void invalidateMinecutCache()
    {
        invalidateAddonCache();
    }

    public abstract int chrome();

    public abstract int panel();

    public abstract int elevated();

    public abstract int inner();

    public abstract int border();

    public abstract int borderSoft();

    public abstract int accent();

    public abstract int accentDim();

    public abstract int text();

    public abstract int textDim();

    public abstract int textMuted();

    public abstract void drawPanel(Batcher2D batcher, int x, int y, int w, int h);

    public abstract void drawPanel(Batcher2D batcher, Area area);

    public abstract void drawSoftRect(Batcher2D batcher, int x, int y, int w, int h, int color);

    public abstract void drawButton(UIContext context, Area area, int baseRgb, boolean hover, boolean customAccent,
        boolean nLeft, boolean nRight, boolean nTop, boolean nBottom);

    public abstract void drawListSelection(Batcher2D batcher, Area area, boolean selected, boolean hover);

    public abstract void drawOverlayChrome(Batcher2D batcher, Area area);

    public abstract void drawFormCell(Batcher2D batcher, int x, int y, int w, int h, boolean selected, boolean hover);

    public abstract void drawTabUnderline(Batcher2D batcher, int x, int y, int textWidth, boolean active);
}
