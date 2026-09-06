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
     */
    public static final boolean MODEL_IK_UI = false;

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
