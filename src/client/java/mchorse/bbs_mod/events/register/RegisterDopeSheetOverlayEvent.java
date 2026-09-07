package mchorse.bbs_mod.events.register;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.UIKeyframeDopeSheet;
import mchorse.bbs_mod.ui.utils.Area;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Allows addons to register custom overlays on top of the UIKeyframeDopeSheet timeline,
 * such as collaborator cursors, markers, selection boxes, and visual guides.
 */
public class RegisterDopeSheetOverlayEvent
{
    @FunctionalInterface
    public interface DopeSheetOverlayRenderer
    {
        public void render(UIContext context, Area area, UIKeyframeDopeSheet dopeSheet);
    }

    private static final List<DopeSheetOverlayRenderer> backgroundRenderers = new ArrayList<>();
    private static final List<DopeSheetOverlayRenderer> foregroundRenderers = new ArrayList<>();
    private static final List<BiConsumer<UIContext, Area>> cursorRenderers = new ArrayList<>();

    public void registerBackgroundRenderer(DopeSheetOverlayRenderer renderer)
    {
        if (renderer != null)
        {
            backgroundRenderers.add(renderer);
        }
    }

    public void registerForegroundRenderer(DopeSheetOverlayRenderer renderer)
    {
        if (renderer != null)
        {
            foregroundRenderers.add(renderer);
        }
    }

    public void registerCursorRenderer(BiConsumer<UIContext, Area> renderer)
    {
        if (renderer != null)
        {
            cursorRenderers.add(renderer);
        }
    }

    public static void postBackgroundRender(UIContext context, Area area, UIKeyframeDopeSheet dopeSheet)
    {
        for (DopeSheetOverlayRenderer renderer : backgroundRenderers)
        {
            try
            {
                renderer.render(context, area, dopeSheet);
            }
            catch (Throwable ignored)
            {}
        }
    }

    public static void postForegroundRender(UIContext context, Area area, UIKeyframeDopeSheet dopeSheet)
    {
        for (DopeSheetOverlayRenderer renderer : foregroundRenderers)
        {
            try
            {
                renderer.render(context, area, dopeSheet);
            }
            catch (Throwable ignored)
            {}
        }
    }

    public static void postCursorRender(UIContext context, Area area)
    {
        for (BiConsumer<UIContext, Area> renderer : cursorRenderers)
        {
            try
            {
                renderer.accept(context, area);
            }
            catch (Throwable ignored)
            {}
        }
    }
}
