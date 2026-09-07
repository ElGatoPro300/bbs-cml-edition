package mchorse.bbs_mod.settings;

/**
 * Optional UI skins registered by Fabric addons (e.g. Minecut).
 * Core CML stays Classic unless an addon calls {@link #enableAddonStyle()}.
 */
public final class UiStyleCapabilities
{
    public static final int CLASSIC = 0;
    public static final int ADDON = 1;

    /**
     * @deprecated Use {@link #ADDON} instead.
     */
    @Deprecated
    public static final int MINECUT = ADDON;

    private static boolean addonStyleAvailable;

    private static int addonChromeSurface = 0xFF101014;
    private static int addonBaseSurface = 0xFF16161A;
    private static int addonRaisedSurface = 0xFF1E1E24;
    private static int addonDeepSurface = 0xFF0A0A0C;
    private static int addonDividerColor = 0xFF00C2D4;
    private static int addonAccentColor = 0x00C2D4;

    private UiStyleCapabilities()
    {}

    public static void enableAddonStyle()
    {
        addonStyleAvailable = true;
    }

    public static boolean isAddonStyleAvailable()
    {
        return addonStyleAvailable;
    }

    /**
     * @deprecated Use {@link #enableAddonStyle()} instead.
     */
    @Deprecated
    public static void enableMinecutStyle()
    {
        enableAddonStyle();
    }

    /**
     * @deprecated Use {@link #isAddonStyleAvailable()} instead.
     */
    @Deprecated
    public static boolean isMinecutStyleAvailable()
    {
        return isAddonStyleAvailable();
    }

    public static int getAddonChromeSurface()
    {
        return addonChromeSurface;
    }

    public static void setAddonChromeSurface(int color)
    {
        addonChromeSurface = color;
    }

    public static int getAddonBaseSurface()
    {
        return addonBaseSurface;
    }

    public static void setAddonBaseSurface(int color)
    {
        addonBaseSurface = color;
    }

    public static int getAddonRaisedSurface()
    {
        return addonRaisedSurface;
    }

    public static void setAddonRaisedSurface(int color)
    {
        addonRaisedSurface = color;
    }

    public static int getAddonDeepSurface()
    {
        return addonDeepSurface;
    }

    public static void setAddonDeepSurface(int color)
    {
        addonDeepSurface = color;
    }

    public static int getAddonDividerColor()
    {
        return addonDividerColor;
    }

    public static void setAddonDividerColor(int color)
    {
        addonDividerColor = color;
    }

    public static int getAddonAccentColor()
    {
        return addonAccentColor;
    }

    public static void setAddonAccentColor(int color)
    {
        addonAccentColor = color;
    }
}
