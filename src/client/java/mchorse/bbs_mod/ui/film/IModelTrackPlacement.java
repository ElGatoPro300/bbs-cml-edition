package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.ui.framework.UIContext;

/**
 * Palette → timeline track insert preview. Implemented by the Minecut UI addon.
 */
public interface IModelTrackPlacement
{
    boolean isActive();

    void begin(String paletteType);

    void updatePreview(UIFilmPanel panel, UIContext context);

    void renderPreview(UIContext context);

    boolean isOverTimeline();

    int getPreviewIndex();

    /** Body-part form path for the drop ({@code "0"}), or empty for the root form. */
    String getPreviewFormPath();

    String getPaletteType();

    void cancel();
}
