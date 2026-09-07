package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.ui.framework.styles.UIStyle;
import mchorse.bbs_mod.ui.utils.icons.Icon;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Registration hub for the optional film UI skin addon (Minecut).
 * Core CML stays classic unless an addon registers here.
 */
public final class FilmUiCapabilities
{
    private static Function<UIFilmPanel, IFilmUiWorkspace> workspaceFactory;
    private static Supplier<UIStyle> addonStyleFactory;
    private static Function<String, Icon> trackIconResolver;
    private static boolean sparseTracksPreferred;

    private FilmUiCapabilities()
    {}

    public static void registerWorkspaceFactory(Function<UIFilmPanel, IFilmUiWorkspace> factory)
    {
        workspaceFactory = factory;
    }

    public static void registerAddonStyleFactory(Supplier<UIStyle> factory)
    {
        addonStyleFactory = factory;
    }

    /**
     * @deprecated Use {@link #registerAddonStyleFactory(Supplier)} instead.
     */
    @Deprecated
    public static void registerMinecutStyleFactory(Supplier<UIStyle> factory)
    {
        registerAddonStyleFactory(factory);
    }

    public static void registerTrackIconResolver(Function<String, Icon> resolver)
    {
        trackIconResolver = resolver;
    }

    /**
     * When true, sparse Model-track timeline UX is preferred while an addon skin is active
     * (default Pose/Transform, keep tracks with keyframes, Remove track in the context menu).
     */
    public static void setSparseTracksPreferred(boolean preferred)
    {
        sparseTracksPreferred = preferred;
    }

    public static boolean hasAddon()
    {
        return workspaceFactory != null;
    }

    public static IFilmUiWorkspace createWorkspace(UIFilmPanel panel)
    {
        return workspaceFactory == null ? null : workspaceFactory.apply(panel);
    }

    public static UIStyle createAddonStyle()
    {
        return addonStyleFactory == null ? null : addonStyleFactory.get();
    }

    /**
     * @deprecated Use {@link #createAddonStyle()} instead.
     */
    @Deprecated
    public static UIStyle createMinecutStyle()
    {
        return createAddonStyle();
    }

    public static Icon resolveTrackIcon(String trackId)
    {
        if (trackIconResolver == null || trackId == null || !UIStyle.isAddon())
        {
            return null;
        }

        return trackIconResolver.apply(trackId);
    }

    public static boolean prefersSparseModelTracks()
    {
        return sparseTracksPreferred && hasAddon();
    }

    /** Test / unload helper. */
    public static void clear()
    {
        workspaceFactory = null;
        addonStyleFactory = null;
        trackIconResolver = null;
        sparseTracksPreferred = false;
    }
}
