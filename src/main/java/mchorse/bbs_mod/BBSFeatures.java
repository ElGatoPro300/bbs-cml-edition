package mchorse.bbs_mod;

/**
 * Compile-time feature gates for unfinished or release-deferred UI.
 * Runtime playback of existing film/model data is left intact when UI is hidden.
 */
public final class BBSFeatures
{
    /**
     * Form Look At / Inverse Kinematics: film timeline tracks, form General sections,
     * and property pick menus. Set {@code true} to restore the unfinished UI.
     */
    public static final boolean FORM_IK_LOOK_AT_UI = false;

    /**
     * Model editor IK panel and Geometry "Add IK Locator" action.
     *
     * <p>On since the IK port landed: the panel now edits a solver that exists, so
     * there is nothing left to defer. It registers second in the icon bar, right
     * after the default panel — the slot BBS-FS gives it, immediately after Pose.
     */
    public static final boolean MODEL_IK_UI = true;

    /**
     * Model editor procedural "Look at limb" picker (config.lookAtHead).
     * Gecko animation settings use {@link UIModelGeckoAnimationsSection} and stay visible.
     */
    public static final boolean MODEL_PROCEDURAL_LOOK_AT_UI = false;

    private BBSFeatures()
    {}

    public static boolean isFormIkLookAtProperty(String name)
    {
        return "look_at".equals(name) || "inverse_kinematics".equals(name);
    }

    public static boolean isFormIkLookAtUiEnabled()
    {
        return FORM_IK_LOOK_AT_UI;
    }
}
