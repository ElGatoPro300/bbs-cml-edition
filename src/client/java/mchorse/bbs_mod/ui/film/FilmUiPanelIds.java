package mchorse.bbs_mod.ui.film;

/**
 * Dock leaf IDs used by the optional Minecut film UI addon.
 * Kept in core so layout NBT and migration stay stable without the addon present.
 */
public final class FilmUiPanelIds
{
    public static final String REPLAYS = "minecutReplays";
    public static final String MEDIA_CAMERA = "minecutMediaCamera";
    public static final String MEDIA_ACTIONS = "minecutMediaActions";
    public static final String MEDIA_TRACKS = "minecutMediaTracks";
    public static final String KEYFRAME = "minecutKeyframe";
    public static final String PROPS_CAMERA = "minecutPropsCamera";
    public static final String PROPS_ACTION = "minecutPropsAction";
    public static final String PLAYER = "minecutPlayer";
    public static final String TIMELINE_REPLAY = "minecutTimelineReplay";
    public static final String TIMELINE_CAMERA = "minecutTimelineCamera";
    public static final String TIMELINE_ACTION = "minecutTimelineAction";

    public static final String[] ALL = {
        REPLAYS, MEDIA_CAMERA, MEDIA_ACTIONS, MEDIA_TRACKS,
        KEYFRAME, PROPS_CAMERA, PROPS_ACTION,
        PLAYER,
        TIMELINE_REPLAY, TIMELINE_CAMERA, TIMELINE_ACTION
    };

    public static final String[] LEGACY = {
        "minecutMedia", "minecutProperties", "minecutTimeline"
    };

    private FilmUiPanelIds()
    {}

    public static boolean isAddonPanelId(String panelId)
    {
        return panelId != null && (panelId.startsWith("minecut") || panelId.startsWith("addon") || panelId.startsWith("nle"));
    }

    /**
     * @deprecated Use {@link #isAddonPanelId(String)} instead.
     */
    @Deprecated
    public static boolean isMinecutPanelId(String panelId)
    {
        return isAddonPanelId(panelId);
    }
}
