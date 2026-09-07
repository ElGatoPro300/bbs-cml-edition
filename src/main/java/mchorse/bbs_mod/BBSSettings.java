package mchorse.bbs_mod;

import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.events.register.RegisterBBSSettingsEvent;
import mchorse.bbs_mod.settings.SettingsBuilder;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueDouble;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.settings.values.ui.ValueColors;
import mchorse.bbs_mod.settings.values.ui.ValueEditorLayout;
import mchorse.bbs_mod.settings.values.ui.ValueFormEditorGizmoToolbar;
import mchorse.bbs_mod.settings.values.ui.ValueGizmoToolbar;
import mchorse.bbs_mod.settings.values.ui.ValueIKDebug;
import mchorse.bbs_mod.settings.values.ui.ValueLanguage;
import mchorse.bbs_mod.settings.values.ui.ValueMobCaptureConditions;
import mchorse.bbs_mod.settings.values.ui.ValueOnionSkin;
import mchorse.bbs_mod.settings.values.ui.ValuePhysicsDebug;
import mchorse.bbs_mod.settings.values.ui.ValueStringKeys;
import mchorse.bbs_mod.settings.values.ui.ValueTimelineToolbarDocks;
import mchorse.bbs_mod.settings.values.ui.ValueUILayoutPreferences;
import mchorse.bbs_mod.settings.values.ui.ValueVideoSettings;
import mchorse.bbs_mod.settings.values.ui.ValueViewportToolbar;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.KeyframeShape;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class BBSSettings
{
    private static final int[] DEFAULT_ALT_HOVER_PALETTE = {
        Colors.GREEN,
        Colors.YELLOW,
        Colors.BLUE,
        Colors.RED,
        Colors.MAGENTA,
        Colors.CYAN,
        Colors.ORANGE,
        Colors.DEEP_PINK
    };

    public static ValueColors favoriteColors;
    public static ValueStringKeys favoriteModelForms;
    public static ValueString favoriteFormCategoriesData;
    public static ValueStringKeys disabledSheets;
    public static ValueLanguage language;
    public static ValueInt primaryColor;
    public static ValueInt modelEditorHoverColor;
    public static ValueFloat modelEditorHoverOpacity;
    public static ValueInt modelEditorAltHoverColor;
    public static ValueFloat modelEditorAltHoverOpacity;
    public static ValueBoolean modelEditorAltHoverMultipleColors;

    public static ValueBoolean enableTrackpadIncrements;
    public static ValueBoolean enableTrackpadScrolling;
    public static ValueBoolean welcomePanelSeen21;
    public static ValueBoolean hideSettingDescriptions;
    public static ValueFloat userIntefaceScale;
    public static ValueBoolean linkUiScaleToGame;
    public static ValueString uiFont;
    public static ValueFloat uiFontSize;
    public static ValueInt tooltipStyle;
    public static ValueFloat fov;
    public static ValueBoolean hsvColorPicker;
    public static ValueBoolean forceQwerty;
    public static ValueBoolean freezeModels;
    /** Cached form-picker thumbnails (morph menu). Off by default — opt in for lighter UI. */
    public static ValueBoolean optimizedMorphMenu;
    public static ValueGizmoToolbar editorGizmoToolbar;
    public static ValueBoolean editorGizmoToolbarHorizontal;
    public static ValueFloat axesScale;
    public static ValueFloat axesThickness;
    public static ValueFloat gizmoHitbox;
    public static ValueBoolean gizmoConstantSize;
    public static ValueFloat gizmoConstantSizeMin;
    public static ValueInt gizmoDefaultMode;
    /* 0 = Style 1, 1 = Style 2 (cubes outside view ring), 2 = Style 3 (cubes on ring, no cones). */
    public static ValueInt gizmoStyle;
    public static ValueBoolean gizmoFullRotationRings;
    public static ValueFloat gizmoGuideLength;
    public static ValueFloat gizmoGuideThickness;
    public static ValueFloat gizmoGuideOpacity;
    public static ValueInt gizmoTranslateSpeed;
    public static ValueBoolean uniformScale;
    public static ValueBoolean clickSound;
    public static ValueBoolean disablePivotTransform;
    public static ValueBoolean gizmos;
    public static ValueBoolean gizmosWorldRendering;
    public static ValueBoolean previewEquipment;
    public static ValueBoolean gizmoFlipAxes;
    public static ValueBoolean gizmoYAxisHorizontal;
    public static ValueBoolean gizmoTrackball;
    public static ValueInt gizmoTrackballScale;
    public static ValueInt defaultInterpolation;
    public static ValueInt defaultModelInterpolation;
    public static ValueInt defaultPathInterpolation;
    public static ValueInt defaultCameraKeyframeInterpolation;
    public static ValueInt defaultKeyframeShape;
    public static ValueInt keyframePreviewColor;
    public static ValueFloat keyframePreviewOpacity;

    public static ValueBoolean enableCursorRendering;
    public static ValueBoolean enableMouseButtonRendering;
    public static ValueBoolean enableKeystrokeRendering;
    public static ValueInt keystrokeOffset;
    public static ValueInt keystrokeMode;

    public static ValueLink backgroundImage;
    public static ValueInt backgroundColor;

    public static ValueBoolean chromaSkyEnabled;
    public static ValueInt chromaSkyColor;
    public static ValueBoolean chromaSkyTerrain;
    public static ValueBoolean chromaSkyClouds;
    public static ValueBoolean chromaSkyModelBlocks;
    public static ValueFloat chromaSkyBillboard;

    public static ValueInt scrollbarShadow;
    public static ValueInt scrollbarWidth;
    public static ValueFloat scrollingSensitivity;
    public static ValueFloat scrollingSensitivityHorizontal;
    public static ValueBoolean scrollingSmoothness;

    public static ValueBoolean multiskinMultiThreaded;

    public static ValueString videoEncoderPath;
    public static ValueBoolean videoEncoderLog;
    public static ValueVideoSettings videoSettings;

    public static ValueFloat editorCameraSpeed;
    public static ValueFloat editorCameraAngleSpeed;
    public static ValueInt duration;
    public static ValueBoolean editorLoop;
    public static ValueInt editorJump;
    public static ValueInt editorGuidesColor;
    public static ValueInt editorSafeMarginsColor;
    public static ValueBoolean editorRuleOfThirds;
    public static ValueBoolean editorSafeMargins;
    public static ValueBoolean editorCenterLines;
    public static ValueBoolean editorCrosshair;
    public static ValueBoolean editorFilmOverlayVisible;
    public static ValueBoolean editorFisheyeWidenFov;
    public static ValueInt editorPeriodicSave;
    public static ValueBoolean editorHorizontalFlight;
    public static ValueBoolean editorFlightFreeLook;
    public static ValueBoolean editorOrbitWithoutFlight;
    public static ValueBoolean editorOrbitSmoothTransition;
    public static ValueBoolean editorOrbitRestrictToViewport;
    public static ValueFloat editorOrbitTransitionDuration;
    private static ValueBoolean orbitSmoothDefaultMigrated;
    public static ValueEditorLayout editorLayoutSettings;
    public static ValueUILayoutPreferences uiLayoutPreferences;
    public static ValueTimelineToolbarDocks timelineToolbarDocks;
    public static ValueViewportToolbar editorViewportToolbar;
    public static ValueFormEditorGizmoToolbar editorFormGizmoToolbar;
    public static ValueOnionSkin editorOnionSkin;
    public static ValueIKDebug ikDebug;
    public static ValuePhysicsDebug physicsDebug;
    public static ValueBoolean editorSnapToMarkers;
    public static ValueBoolean editorClipPreview;
    public static ValueBoolean editorClipTypeLabels;
    public static ValueBoolean editorReplaySprintParticles;
    public static ValueBoolean editorCameraPreviewPlayerSync;
    public static ValueInt editorDockGuideColor;
    public static ValueFloat editorDockGuideOpacity;
    public static ValueBoolean editorReplayStepSound;
    public static ValueBoolean editorActorPausedSwipeLoop;
    public static ValueBoolean editorActorPauseAnimations;
    public static ValueBoolean editorActorPausedRunInPlace;
    public static ValueBoolean actorDamageFlash;
    public static ValueBoolean actorDamageAnimation;
    public static ValueBoolean eatingArmAnimation;
    public static ValueBoolean replayDeathTimelineSync;
    public static ValueBoolean editorSimplifyAnimations;
    public static ValueBoolean editorMuteRenderAudioClips;
    public static ValueInt editorTimeMode;
    public static ValueInt editorImportMode;
    public static ValueInt editorReplayEditorTitleLimit;
    public static ValueBoolean editorAnchoredReplaysPanel;
    public static ValueBoolean editorSeparateReplayPropertiesPanel;
    public static ValueInt blockPickerDefaultMode;
    public static ValueInt editorAnchoredReplaysPanelHeight;
    public static ValueBoolean editorReplayHud;
    public static ValueInt editorReplayHudPosition;
    public static ValueBoolean editorReplayHudDisplayName;
    public static ValueInt editorCommandWidth;
    public static ValueInt editorCommandHeight;
    public static ValueBoolean editorCommandAutoWrap;
    public static ValueInt replayContextOptions;
    public static ValueBoolean editorRewind;
    public static ValueBoolean editorHorizontalClipEditor;
    public static ValueBoolean editorGlobalClipPanels;
    public static ValueBoolean editorEmbeddedKeyframeSidePanel;
    public static ValueBoolean editorMinutesBackup;
    public static ValueBoolean editorTimelineToolbar;
    public static ValueBoolean modelPbrPanelControls;

    public static ValueFloat recordingCountdown;
    public static ValueBoolean recordingSwipeDamage;
    public static ValueBoolean recordingAutoCaptureMobs;
    public static ValueBoolean recordingAutoCaptureProjectiles;
    public static ValueBoolean recordingAutoCaptureMobActions;
    public static ValueBoolean recordingMobCaptureOnAlt;
    public static ValueBoolean recordingMobCaptureConditionsSummary;
    public static ValueMobCaptureConditions recordingMobCaptureConditions;
    public static ValueBoolean recordingOverlays;
    public static ValueInt recordingPoseTransformOverlays;
    public static ValueBoolean recordingCameraPreview;
    public static ValueInt recordingCameraPreviewFutureCount;

    public static ValueBoolean renderAllModelBlocks;
    public static ValueBoolean clickModelBlocks;
    public static ValueBoolean modelBlockCategoriesPanelEnabled;
    public static ValueFloat modelBlockAnimationStateDistance;
    public static ValueString modelBlockPanelLayout;
    public static ValueString triggerBlockPanelLayout;

    /* Shared "mosaic vs list" view preference for the home pages and the open
       asset overlay. Persisted globally so toggling it anywhere takes effect
       everywhere. */
    public static ValueBoolean lastViewMosaic;
    public static ValueBoolean coloredBackground;
    public static ValueFloat backgroundBrightness;
    public static ValueDouble worldGammaPercent;
    public static ValueBoolean worldGammaOverride;
    public static ValueFloat worldSunPathRotation;
    public static ValueBoolean interfaceShadows;

    public static ValueString entitySelectorsPropertyWhitelist;

    public static ValueBoolean damageControl;

    public static ValueBoolean shaderCurvesEnabled;
    public static ValueBoolean irisOpacityFix;
    public static ValueBoolean noshadingOpaqueForms;
    /**
     * Soft form/limb transparency under Iris only: ON draws backfaces (two-pass; may darken),
     * OFF culls backfaces (cleaner colors). Default ON. Without shaders, {@code model.culling} applies.
     */
    public static ValueBoolean softTransparencyBackfaces;
    /** Kept invisible for migrating saved Complementary/BSL toggles. */
    @Deprecated
    public static ValueBoolean complementaryOpacityFix;
    /** Kept invisible for migrating saved Complementary/BSL toggles. */
    @Deprecated
    public static ValueBoolean bslOpacityFix;
    public static ValueBoolean shaderOpacityPatchesDefaultOnMigrated;
    public static ValueFloat shaderShadowOpacity;
    public static ValueBoolean shaderShadowDither;
    public static ValueBoolean lodShaderReloadFix;

    public static ValueBoolean audioWaveformVisible;
    public static ValueInt audioWaveformDensity;
    public static ValueFloat audioWaveformWidth;
    public static ValueInt audioWaveformHeight;
    public static ValueBoolean audioWaveformFilename;
    public static ValueBoolean audioWaveformTime;
    public static ValueBoolean realtimeKeyframes;
    public static ValueBoolean autoKeyframes;
    public static ValueBoolean poseBonesFilterMarked;
    public static ValueBoolean replayMarkedBonesOnly;
    public static ValueBoolean presetsGridPanel;
    public static ValueBoolean presetsGridTrackers;
    public static ValueInt presetsGridCellSize;
    public static ValueFloat replayFpBobbingIntensity;
    public static ValueFloat replayFpBobbingFrequency;
    public static ValueBoolean pickLimbTexture;
    public static ValueBoolean fluidRealisticModelInteraction;

    public static ValueBoolean saveAsCompatible;

    public static ValueLink textureDefaultPath;
    public static ValueInt texturePickerItemSize;

    public static ValueString cdnUrl;
    public static ValueString cdnToken;
    public static ValueBoolean morphingAutoMorph;
    public static ValueBoolean autoSpectatorInEditors;

    public static ValueBoolean usingInMemoryClipboard;
    public static ValueBoolean discordPresence;
    public static ValueString discordApplicationId;

    /**
     * When enabled (default), films dual-write legacy-friendly data for fields
     * that newer builds store differently (subtitle lineHeight/maxWidth, Opacity
     * mirrored into Color alpha, form lighting brightness rewritten as legacy
     * world-influence floats, etc.).
     */
    public static boolean isSaveAsCompatible()
    {
        return saveAsCompatible == null || saveAsCompatible.get();
    }

    /**
     * When enabled (default), embedded clip keyframe editors show properties
     * in an overlay side/bottom panel. When disabled, properties go to the
     * general Properties layout tab (same host as replay keyframes).
     */
    public static boolean isEmbeddedKeyframeSidePanelEnabled()
    {
        return editorEmbeddedKeyframeSidePanel == null || editorEmbeddedKeyframeSidePanel.get();
    }

    /**
     * When {@link #editorActorPauseAnimations} is off and the film is paused:
     * default ({@code false}) settles emoticon/BOBJ to idle; enabled keeps the
     * older run-in-place cadence from paused keyframes.
     */
    public static boolean shouldSettleActorNaturalStopWhenPaused()
    {
        return editorActorPausedRunInPlace == null || !editorActorPausedRunInPlace.get();
    }

    public static boolean shouldFlashActorLiveDamage()
    {
        return actorDamageFlash != null && actorDamageFlash.get();
    }

    public static boolean shouldPlayActorDamageAnimation()
    {
        return actorDamageAnimation == null || actorDamageAnimation.get();
    }

    /**
     * Extra ModelForm arm chew while eating/drinking. Off matches vanilla
     * third-person players (the holding arm stays in idle/walk).
     */
    public static boolean shouldAnimateEatingArm()
    {
        return eatingArmAnimation != null && eatingArmAnimation.get();
    }

    public static boolean shouldKeepActorLiveHurtTime()
    {
        return shouldFlashActorLiveDamage() || shouldPlayActorDamageAnimation();
    }

    public static int primaryColor()
    {
        return primaryColor(Colors.A50);
    }

    public static int primaryColor(int alpha)
    {
        return primaryColor.get() | alpha;
    }

    public static int modelEditorHoverColor(float alpha)
    {
        int color = modelEditorHoverColor == null ? Colors.ACTIVE : (modelEditorHoverColor.get() & Colors.RGB);

        return Colors.setA(color, alpha);
    }

    public static void syncAppliedAppearance()
    {}

    /**
     * Opacity shader patches originally shipped default-off; defaults are now on.
     * Existing bbs.json still has false — flip them on once after load.
     */
    public static void migrateShaderOpacityPatchesAfterLoad()
    {
        if (shaderOpacityPatchesDefaultOnMigrated == null || shaderOpacityPatchesDefaultOnMigrated.get())
        {
            return;
        }

        if (complementaryOpacityFix != null)
        {
            complementaryOpacityFix.set(true);
        }

        if (bslOpacityFix != null)
        {
            bslOpacityFix.set(true);
        }

        shaderOpacityPatchesDefaultOnMigrated.set(true);
    }

    public static int modelEditorHoverHighlight()
    {
        return buildHoverHighlight(modelEditorHoverColor, modelEditorHoverOpacity, Colors.ACTIVE, 0.5F);
    }

    /**
     * Alt+click replay hover in the film viewport. When {@link #modelEditorAltHoverMultipleColors}
     * is enabled, each replay index picks a different color from the favorite-colors palette
     * (or {@link #DEFAULT_ALT_HOVER_PALETTE} when no favorites are saved).
     */
    public static int modelEditorAltHoverHighlight(int paletteIndex)
    {
        if (modelEditorAltHoverMultipleColors != null && modelEditorAltHoverMultipleColors.get())
        {
            float opacity = modelEditorAltHoverOpacity == null ? 0.5F : modelEditorAltHoverOpacity.get();
            List<Color> palette = favoriteColors == null ? null : favoriteColors.getCurrentColors();
            int color;

            if (palette != null && !palette.isEmpty())
            {
                color = palette.get(Math.floorMod(paletteIndex, palette.size())).getRGBColor();
            }
            else
            {
                color = DEFAULT_ALT_HOVER_PALETTE[Math.floorMod(paletteIndex, DEFAULT_ALT_HOVER_PALETTE.length)];
            }

            return Colors.setA(color, opacity);
        }

        return buildHoverHighlight(modelEditorAltHoverColor, modelEditorAltHoverOpacity, Colors.YELLOW, 0.5F);
    }

    private static int buildHoverHighlight(ValueInt colorValue, ValueFloat opacityValue, int defaultColor, float defaultOpacity)
    {
        int color = colorValue == null ? defaultColor : (colorValue.get() & Colors.RGB);
        float opacity = opacityValue == null ? defaultOpacity : opacityValue.get();

        return Colors.setA(color, opacity);
    }

    public static int keyframePreviewHighlight(float pulseAlpha)
    {
        return Colors.setA(keyframePreviewColor.get(), pulseAlpha);
    }

    /** The raw setting value is used directly as the UI scale multiplier (2 = 100%, i.e. the
     *  previous default look), instead of being quantized into discrete steps. */
    public static float getUIScaleFactor()
    {
        if (userIntefaceScale == null || userIntefaceScale.get() <= 0F)
        {
            return 0F;
        }

        return userIntefaceScale.get();
    }

    /**
     * When true, BBS writes Minecraft's GUI scale (legacy). Default false keeps BBS
     * scale independent of the game's GUI scale / hotbar / vanilla menus.
     */
    public static boolean isUiScaleLinkedToGame()
    {
        return linkUiScaleToGame != null && linkUiScaleToGame.get();
    }

    public static boolean hasColoredBackground()
    {
        return coloredBackground == null || coloredBackground.get();
    }

    public static boolean isLightTheme()
    {
        return tooltipStyle != null && tooltipStyle.get() == 0;
    }

    public static float getBackgroundBrightness()
    {
        return backgroundBrightness == null ? 1F : backgroundBrightness.get();
    }

    private static int withAlpha(int color, int alpha)
    {
        return (color & Colors.RGB) | alpha;
    }

    private static int applyBackgroundBrightness(int color)
    {
        float brightness = MathUtils.clamp(getBackgroundBrightness(), 0.5F, 1.5F);

        if (Math.abs(brightness - 1F) < 0.001F)
        {
            return color;
        }

        int a = color & 0xff000000;
        int r = (color >> 16) & 0xff;
        int g = (color >> 8) & 0xff;
        int b = color & 0xff;

        if (brightness < 1F)
        {
            r = Math.round(r * brightness);
            g = Math.round(g * brightness);
            b = Math.round(b * brightness);
        }
        else
        {
            float factor = brightness - 1F;

            r += Math.round((255 - r) * factor);
            g += Math.round((255 - g) * factor);
            b += Math.round((255 - b) * factor);
        }

        r = MathUtils.clamp(r, 0, 255);
        g = MathUtils.clamp(g, 0, 255);
        b = MathUtils.clamp(b, 0, 255);

        return a | (r << 16) | (g << 8) | b;
    }

    private static int getThemeChromeSurface()
    {
        return applyBackgroundBrightness(isLightTheme() ? 0xffe6e9ef : 0xff111316);
    }

    private static int getThemeBaseSurface()
    {
        return applyBackgroundBrightness(isLightTheme() ? 0xfff1f4f8 : 0xff171a1f);
    }

    private static int getThemeRaisedSurface()
    {
        return applyBackgroundBrightness(isLightTheme() ? 0xfff8fafd : 0xff1d2127);
    }

    private static int getThemeDeepSurface()
    {
        return applyBackgroundBrightness(isLightTheme() ? 0xffdee4ed : 0xff0f1217);
    }

    private static int getThemeDividerColor()
    {
        return isLightTheme() ? 0xffc2cbd8 : 0xff30353d;
    }

    public static int chromeSurface()
    {
        return getThemeChromeSurface();
    }

    public static int baseSurface()
    {
        return getThemeBaseSurface();
    }

    public static int raisedSurface()
    {
        return getThemeRaisedSurface();
    }

    public static int deepSurface()
    {
        return getThemeDeepSurface();
    }

    public static int dividerColor()
    {
        return getThemeDividerColor();
    }

    public static int color(int color, int alpha)
    {
        return withAlpha(color, alpha);
    }

    public static int backgroundTint(int alpha)
    {
        return hasColoredBackground() ? primaryColor(alpha) : 0;
    }

    public static int accentOverlay(int alpha)
    {
        return primaryColor(alpha);
    }

    public static int panelBackground(float tint)
    {
        return tint <= 0.08F ? deepSurface() : tint <= 0.16F ? baseSurface() : raisedSurface();
    }

    public static int panelBackground(int alpha)
    {
        return color(baseSurface(), alpha);
    }

    public static int panelOverlay(int alpha)
    {
        return hasColoredBackground() ? accentOverlay(alpha) : color(dividerColor(), alpha);
    }

    public static int panelChromeColor()
    {
        return chromeSurface();
    }

    public static int panelShadowOpaqueColor()
    {
        return Colors.A25 | primaryColor.get();
    }

    public static int panelShadowTransparentColor()
    {
        return Colors.setA(primaryColor.get(), 0F);
    }

    public static int getDefaultDuration()
    {
        return duration == null ? 30 : duration.get();
    }

    public static float getFov()
    {
        return BBSSettings.fov == null ? MathUtils.toRad(50) : MathUtils.toRad(BBSSettings.fov.get());
    }

    public static void register(SettingsBuilder builder)
    {
        HashSet<String> defaultFilters = new HashSet<>();

        defaultFilters.add("vX");
        defaultFilters.add("vY");
        defaultFilters.add("vZ");
        defaultFilters.add("grounded");
        defaultFilters.add("stick_rx");
        defaultFilters.add("stick_ry");
        defaultFilters.add("trigger_l");
        defaultFilters.add("trigger_r");
        defaultFilters.add("extra1_x");
        defaultFilters.add("extra1_y");
        defaultFilters.add("extra2_x");
        defaultFilters.add("extra2_y");

        builder.category("appearance");
        builder.register(language = new ValueLanguage("language"));
        primaryColor = builder.getInt("primary_color", Colors.ACTIVE).color();
        modelEditorHoverColor = builder.getInt("model_editor_hover_color", Colors.ACTIVE).color();
        modelEditorHoverOpacity = builder.getFloat("model_editor_hover_opacity", 0.5F, 0F, 1F);
        modelEditorAltHoverColor = builder.getInt("model_editor_alt_hover_color", Colors.YELLOW).color();
        modelEditorAltHoverOpacity = builder.getFloat("model_editor_alt_hover_opacity", 0.5F, 0F, 1F);
        modelEditorAltHoverMultipleColors = builder.getBoolean("model_editor_alt_hover_multiple_colors", false);
        enableTrackpadIncrements = builder.getBoolean("trackpad_increments", true);
        enableTrackpadScrolling = builder.getBoolean("trackpad_scrolling", true);
        hideSettingDescriptions = builder.getBoolean("hide_setting_descriptions", false);
        welcomePanelSeen21 = builder.getBoolean("welcome_panel_seen_2_1", false);
        welcomePanelSeen21.invisible();
        userIntefaceScale = builder.getFloat("ui_scale", 2F, 0.1F, 4F);
        linkUiScaleToGame = builder.getBoolean("link_ui_scale_to_game", false);
        uiFont = builder.getString("ui_font", "");
        uiFontSize = builder.getFloat("ui_font_size", 1F, 0.25F, 4F);
        tooltipStyle = builder.getInt("tooltip_style", 1);
        coloredBackground = builder.getBoolean("colored_background", true);
        backgroundBrightness = builder.getFloat("background_brightness", 1F, 0.5F, 1.5F);
        worldGammaPercent = builder.getDouble("world_gamma_percent", 100D, 0D, 1500D);
        worldGammaOverride = builder.getBoolean("world_gamma_override", false);
        worldSunPathRotation = builder.getFloat("world_sun_path_rotation", 0F, -180F, 180F);
        interfaceShadows = builder.getBoolean("interface_shadows", true);
        fov = builder.getFloat("fov", 40, 0, 180);
        hsvColorPicker = builder.getBoolean("hsv_color_picker", true);
        forceQwerty = builder.getBoolean("force_qwerty", false);
        freezeModels = builder.getBoolean("freeze_models", false);
        optimizedMorphMenu = builder.getBoolean("optimized_morph_menu", false);
        uniformScale = builder.getBoolean("uniform_scale", false);
        clickSound = builder.getBoolean("click_sound", false);
        pickLimbTexture = builder.getBoolean("pick_limb_texture", true);
        morphingAutoMorph = builder.getBoolean("auto_morph", false);
        autoSpectatorInEditors = builder.getBoolean("auto_spectator_in_editors", true);
        editorSimplifyAnimations = builder.getBoolean("simplify_animations", false);
        favoriteColors = new ValueColors("favorite_colors");
        favoriteModelForms = new ValueStringKeys("favorite_model_forms");
        favoriteFormCategoriesData = builder.getString("favorite_form_categories_data", "");
        favoriteFormCategoriesData.invisible();
        disabledSheets = new ValueStringKeys("disabled_sheets");
        disabledSheets.set(defaultFilters);
        builder.register(favoriteColors);
        builder.register(favoriteModelForms);
        builder.register(disabledSheets);
        textureDefaultPath = builder.getRL("texture_default_path", null);
        texturePickerItemSize = builder.getInt("texture_picker_item_size", 16, 16, 220);
        discordPresence = builder.getBoolean("discord_presence", true);
        discordApplicationId = builder.getString("discord_application_id", "");

        builder.category("axes");
        gizmos = builder.getBoolean("gizmos", true);
        /* Keep form-editor gizmos / bone picking while model-block F7 world rendering is on. */
        gizmosWorldRendering = builder.getBoolean("gizmos_world_rendering", true);
        /* Armor, held items, skulls, etc. in form / model-block preview editors. */
        previewEquipment = builder.getBoolean("preview_equipment", true);
        axesScale = builder.getFloat("axes_scale", 1.5F, 0F, 100F);
        /* Default >1 so arrows/rings are easier to pick; floor keeps thickness off zero. */
        axesThickness = builder.getFloat("axes_thickness", 1.2F, 0.25F, 6F);
        /* Multiplier applied only to the invisible picking pass, so the clickable area can be
         * fatter than the visible handles (or thinner) independently of axes_thickness. */
        gizmoHitbox = builder.getFloat("gizmo_hitbox", 1.5F, 0.25F, 5F);
        /* When enabled, gizmo size scales with camera distance to stay roughly constant on screen. */
        gizmoConstantSize = builder.getBoolean("gizmo_constant_size", true);
        /* Floor in Math.max(floor, dist * 0.12). 0 disables the floor so it can keep shrinking when close. */
        gizmoConstantSizeMin = builder.getFloat("gizmo_constant_size_min", 0.5F, 0F, 10F);
        disablePivotTransform = builder.getBoolean("disable_pivot_transform", false);
        /* When enabled, translate/scale handles and half rotation rings reorient toward the camera;
         * when disabled, stay on +X/+Y/+Z with fixed half-rings. */
        gizmoFlipAxes = builder.getBoolean("gizmo_flip_axes", true);
        gizmoYAxisHorizontal = builder.getBoolean("gizmo_y_axis_horizontal", true);
        gizmoTrackball = builder.getBoolean("gizmo_trackball", true);
        gizmoTrackballScale = builder.getInt("gizmo_trackball_scale", 1, 1, 5);
        /* 0 = Translate, 1 = Scale, 2 = Rotate, 3 = Combined, 4 = Trackball only; see Gizmo.Mode (ordinal order matches). */
        gizmoDefaultMode = builder.getInt("gizmo_default_mode", 0, 0, 4);
        /* Combined / rotate visual style: 0 = Style 1, 1 = Style 2, 2 = Style 3 (cubes on ring, no cones). */
        gizmoStyle = builder.getInt("gizmo_style", 0, 0, 2);
        /* When true, XYZ rotation rings are full circles; when false, camera-facing half-rings. */
        gizmoFullRotationRings = builder.getBoolean("gizmo_full_rotation_rings", false);
        /* Faint guide line(s) shown along the dragged axis/plane: length (multiplier),
         * thickness (multiplier) and opacity (0..1). */
        gizmoGuideLength = builder.getFloat("gizmo_guide_length", 2F, 0.1F, 10F);
        gizmoGuideThickness = builder.getFloat("gizmo_guide_thickness", 2F, 0.1F, 10F);
        gizmoGuideOpacity = builder.getFloat("gizmo_guide_opacity", 0.35F, 0.05F, 1F);
        gizmoTranslateSpeed = builder.getInt("gizmo_translate_speed", 5, 1, 20);
        builder.register(editorGizmoToolbar = new ValueGizmoToolbar("gizmo_toolbar"));
        editorGizmoToolbarHorizontal = builder.getBoolean("gizmo_toolbar_horizontal", true);
        builder.register(editorFormGizmoToolbar = new ValueFormEditorGizmoToolbar("form_gizmo_toolbar"));

        builder.category("tutorials");
        enableCursorRendering = builder.getBoolean("cursor", false);
        enableMouseButtonRendering = builder.getBoolean("mouse_buttons", false);
        enableKeystrokeRendering = builder.getBoolean("keystrokes", false);
        keystrokeOffset = builder.getInt("keystrokes_offset", 10, 0, 20);
        keystrokeMode = builder.getInt("keystrokes_position", 1);

        builder.category("background");
        backgroundImage = builder.getRL("image", null);
        backgroundColor = builder.getInt("color", Colors.A75).colorAlpha();

        builder.category("chroma_sky");
        chromaSkyEnabled = builder.getBoolean("enabled", false);
        chromaSkyColor = builder.getInt("color", Colors.A75).color();
        chromaSkyTerrain = builder.getBoolean("terrain", true);
        chromaSkyClouds = builder.getBoolean("clouds", true);
        chromaSkyModelBlocks = builder.getBoolean("model_blocks", false);
        chromaSkyBillboard = builder.getFloat("billboard", 0F, 0F, 256F);

        builder.category("scrollbars");
        scrollbarShadow = builder.getInt("shadow", Colors.A50).colorAlpha();
        scrollbarWidth = builder.getInt("width", 4, 2, 10);
        scrollingSensitivity = builder.getFloat("sensitivity", 1F, 0F, 10F);
        scrollingSensitivityHorizontal = builder.getFloat("sensitivity_horizontal", 1F, 0F, 10F);
        scrollingSmoothness = builder.getBoolean("smoothness", true);

        builder.category("multiskin");
        multiskinMultiThreaded = builder.getBoolean("multithreaded", true);

        builder.category("video");
        videoEncoderPath = builder.getString("encoder_path", "ffmpeg");
        videoEncoderLog = builder.getBoolean("log", true);
        builder.register(videoSettings = new ValueVideoSettings("settings"));

        /* Camera editor */
        builder.category("editor");
        editorCameraSpeed = builder.getFloat("speed", 1F, 0.1F, 100F);
        editorCameraAngleSpeed = builder.getFloat("angle_speed", 1F, 0.1F, 100F);
        duration = builder.getInt("duration", 30, 1, 1000);
        editorJump = builder.getInt("jump", 5, 1, 1000);
        editorLoop = builder.getBoolean("loop", false);
        editorGuidesColor = builder.getInt("guides_color", 0xcccc0000).colorAlpha();
        editorRuleOfThirds = builder.getBoolean("rule_of_thirds", false);
        editorCenterLines = builder.getBoolean("center_lines", false);
        editorCrosshair = builder.getBoolean("crosshair", false);
        editorFilmOverlayVisible = builder.getBoolean("film_overlay_visible", true);
        editorGlobalClipPanels = builder.getBoolean("global_clip_panels", true);
        editorFisheyeWidenFov = builder.getBoolean("fisheye_widen_fov", true);
        editorPeriodicSave = builder.getInt("periodic_save", 60, 0, 3600);
        editorHorizontalFlight = builder.getBoolean("horizontal_flight", false);
        builder.register(editorLayoutSettings = new ValueEditorLayout("layout"));
        builder.register(uiLayoutPreferences = new ValueUILayoutPreferences("ui_layout"));
        uiLayoutPreferences.invisible();
        builder.register(timelineToolbarDocks = new ValueTimelineToolbarDocks("timeline_toolbar_docks"));
        builder.register(editorOnionSkin = new ValueOnionSkin("onion_skin"));
        /* Overlays drawn over the preview, edited through the gear in the IK and
         * dynamic-bone panels — stored here, no row of their own in the settings. */
        builder.register(ikDebug = new ValueIKDebug("ik_debug"));
        builder.register(physicsDebug = new ValuePhysicsDebug("physics_debug"));
        editorSnapToMarkers = builder.getBoolean("snap_to_markers", false);
        editorClipPreview = builder.getBoolean("clip_preview", true);
        editorRewind = builder.getBoolean("rewind", true);
        editorHorizontalClipEditor = builder.getBoolean("horizontal_clip_editor", true);
        editorEmbeddedKeyframeSidePanel = builder.getBoolean("embedded_keyframe_side_panel", true);
        editorMinutesBackup = builder.getBoolean("minutes_backup", true);
        editorDockGuideColor = builder.getInt("dock_guide_color", 0x57CCFF).color();
        editorDockGuideOpacity = builder.getFloat("dock_guide_opacity", 0.5F, 0F, 1F);
        defaultInterpolation = builder.getInt("default_interpolation", 0);
        defaultModelInterpolation = builder.getInt("default_model_interpolation", 0);
        defaultPathInterpolation = builder.getInt("default_path_interpolation", 34);
        defaultCameraKeyframeInterpolation = builder.getInt("default_camera_keyframe_interpolation", 0);
        defaultKeyframeShape = builder.getInt("default_keyframe_shape", 0, 0, KeyframeShape.values().length - 1);
        keyframePreviewColor = builder.getInt("keyframe_preview_color", Colors.WHITE).color();
        keyframePreviewOpacity = builder.getFloat("keyframe_preview_opacity", 0.75F, 0F, 1F);
        editorSafeMarginsColor = builder.getInt("safe_margins_color", 0xcccc0000).colorAlpha();
        editorSafeMargins = builder.getBoolean("safe_margins", false);
        editorFlightFreeLook = builder.getBoolean("flight_free_look", false);
        editorOrbitWithoutFlight = builder.getBoolean("orbit_without_flight", false);
        editorOrbitSmoothTransition = builder.getBoolean("orbit_smooth_transition", false);
        editorOrbitRestrictToViewport = builder.getBoolean("orbit_restrict_to_viewport", false);
        editorOrbitTransitionDuration = builder.getFloat("orbit_transition_duration", 1.25F, 0.1F, 10F);
        orbitSmoothDefaultMigrated = builder.getBoolean("orbit_smooth_default_migrated", false);
        orbitSmoothDefaultMigrated.invisible();
        editorClipTypeLabels = builder.getBoolean("clip_type_labels", false);
        editorCameraPreviewPlayerSync = builder.getBoolean("camera_preview_player_sync", false);
        editorMuteRenderAudioClips = builder.getBoolean("mute_render_audio_clips", false);
        editorTimeMode = builder.getInt("time_mode", 0, 0, 2);
        editorImportMode = builder.getInt("import_mode", 0, 0, 1);
        realtimeKeyframes = builder.getBoolean("realtime_keyframes", false);
        autoKeyframes = builder.getBoolean("auto_keyframes", true);
        usingInMemoryClipboard = builder.getBoolean("using_in_memory_clipboard", false);
        builder.register(editorViewportToolbar = new ValueViewportToolbar("viewport_toolbar"));

        builder.category("timeline_toolbar");
        editorTimelineToolbar = builder.getBoolean("enabled", true);

        builder.category("replays");
        replayContextOptions = builder.getInt("compacted_options", 0, 0, 2);
        editorReplaySprintParticles = builder.getBoolean("replay_sprint_particles", false);
        editorReplayStepSound = builder.getBoolean("replay_step_sound", false);
        editorActorPausedSwipeLoop = builder.getBoolean("actor_paused_swipe_loop", false);
        editorActorPauseAnimations = builder.getBoolean("actor_pause_animations", false);
        editorActorPausedRunInPlace = builder.getBoolean("actor_paused_run_in_place", false);
        actorDamageFlash = builder.getBoolean("actor_damage_flash", true);
        actorDamageAnimation = builder.getBoolean("actor_damage_animation", true);
        eatingArmAnimation = builder.getBoolean("eating_arm_animation", false);
        replayDeathTimelineSync = builder.getBoolean("sync_death_timeline", true);
        replayMarkedBonesOnly = builder.getBoolean("replay_marked_bones_only", false);
        editorReplayEditorTitleLimit = builder.getInt("replay_editor_title_limit", 12, 0, 64);
        replayFpBobbingIntensity = builder.getFloat("replay_fp_bobbing_intensity", 0.25F, 0F, 2F);
        replayFpBobbingFrequency = builder.getFloat("replay_fp_bobbing_frequency", 0.25F, 0F, 3F);
        editorAnchoredReplaysPanel = builder.getBoolean("anchored_replays_panel", true);
        editorSeparateReplayPropertiesPanel = builder.getBoolean("separate_replay_properties_panel", true);
        blockPickerDefaultMode = builder.getInt("block_picker_default_mode", 0, 0, 2);
        editorAnchoredReplaysPanelHeight = builder.getInt("anchored_replays_panel_height", 170, 70, 2000);
        editorAnchoredReplaysPanelHeight.invisible();
        editorReplayHud = builder.getBoolean("replay_hud", false);
        editorReplayHudPosition = builder.getInt("replay_hud_position", 0, 0, 3);
        editorReplayHudDisplayName = builder.getBoolean("replay_hud_display_name", true);
        poseBonesFilterMarked = builder.getBoolean("pose_bones_filter_marked", false);
        poseBonesFilterMarked.invisible();
        presetsGridPanel = builder.getBoolean("presets_grid_panel", false);
        presetsGridTrackers = builder.getBoolean("presets_grid_trackers", true);
        presetsGridTrackers.invisible();
        presetsGridCellSize = builder.getInt("presets_grid_cell_size", 1, 0, 3);
        presetsGridCellSize.invisible();

        builder.category("recording");
        recordingCountdown = builder.getFloat("countdown", 1.5F, 0F, 30F);
        recordingSwipeDamage = builder.getBoolean("swipe_damage", false);
        recordingAutoCaptureMobs = builder.getBoolean("auto_capture_mobs", true);
        recordingAutoCaptureProjectiles = builder.getBoolean("auto_capture_projectiles", true);
        recordingAutoCaptureMobActions = builder.getBoolean("auto_capture_mob_actions", true);
        recordingMobCaptureOnAlt = builder.getBoolean("mob_capture_on_alt", false);
        recordingMobCaptureConditionsSummary = builder.getBoolean("mob_capture_conditions_summary", true);
        builder.register(recordingMobCaptureConditions = new ValueMobCaptureConditions("mob_capture_conditions"));
        recordingMobCaptureConditions.invisible();
        recordingOverlays = builder.getBoolean("overlays", true);
        recordingPoseTransformOverlays = builder.getInt("pose_transform_overlays", 0, 0, 42);
        recordingCameraPreview = builder.getBoolean("camera_preview", true);
        recordingCameraPreviewFutureCount = builder.getInt("camera_preview_future_count", 3, 1, 8);

        builder.category("model_blocks");
        renderAllModelBlocks = builder.getBoolean("render_all", true);
        clickModelBlocks = builder.getBoolean("click", true);
        modelBlockAnimationStateDistance = builder.getFloat("distance", 64F);
        modelBlockCategoriesPanelEnabled = builder.getBoolean("categories_panel_enabled", false);
        modelPbrPanelControls = builder.getBoolean("model_pbr_panel_controls", false);
        modelBlockPanelLayout = builder.getString("panel_layout", "");
        modelBlockPanelLayout.invisible();
        triggerBlockPanelLayout = builder.getString("trigger_panel_layout", "");
        triggerBlockPanelLayout.invisible();
        lastViewMosaic = builder.getBoolean("last_view_mosaic", true);
        lastViewMosaic.invisible();

        builder.category("entity_selectors");
        entitySelectorsPropertyWhitelist = builder.getString("whitelist", "CustomName,Name");

        builder.category("dc");
        damageControl = builder.getBoolean("enabled", true);

        builder.category("shader_curves");
        shaderCurvesEnabled = builder.getBoolean("enabled", true);
        irisOpacityFix = builder.getBoolean("iris_opacity_fix", true);
        noshadingOpaqueForms = builder.getBoolean("noshading_opaque_forms", true);
        softTransparencyBackfaces = builder.getBoolean("soft_transparency_backfaces", true);
        complementaryOpacityFix = builder.getBoolean("complementary_opacity_fix", true);
        complementaryOpacityFix.invisible();
        bslOpacityFix = builder.getBoolean("bsl_opacity_fix", true);
        bslOpacityFix.invisible();
        shaderOpacityPatchesDefaultOnMigrated = builder.getBoolean("opacity_patches_default_on_migrated", false);
        shaderOpacityPatchesDefaultOnMigrated.invisible();
        shaderShadowOpacity = builder.getFloat("shader_shadow_opacity", 1F, 0F, 1F);
        shaderShadowDither = builder.getBoolean("shader_shadow_dither", true);
        lodShaderReloadFix = builder.getBoolean("lod_shader_reload_fix", true);

        builder.category("fluid_simulation");
        fluidRealisticModelInteraction = builder.getBoolean("realistic_model_interaction", false);

        builder.category("compatibility");
        saveAsCompatible = builder.getBoolean("save_as_compatible", true);

        builder.category("audio");
        audioWaveformVisible = builder.getBoolean("waveform_visible", true);
        audioWaveformDensity = builder.getInt("waveform_density", 20, 10, 100);
        audioWaveformWidth = builder.getFloat("waveform_width", 0.8F, 0F, 1F);
        audioWaveformHeight = builder.getInt("waveform_height", 24, 10, 40);
        audioWaveformFilename = builder.getBoolean("waveform_filename", false);
        audioWaveformTime = builder.getBoolean("waveform_time", false);

        builder.category("cdn");
        cdnUrl = builder.getString("url", "");
        cdnToken = builder.getString("token", "");

        BBSMod.events.post(new RegisterBBSSettingsEvent(builder));
        syncAppliedAppearance();
    }

    /**
     * Orbit smooth transitions shipped default-on via {@code orbit_no_animation} (default false).
     * New default is instant switching; preserve smooth only when that legacy key was saved as false.
     */
    public static void migrateOrbitSettingsAfterLoad(File settingsFile)
    {
        if (orbitSmoothDefaultMigrated == null || orbitSmoothDefaultMigrated.get())
        {
            return;
        }

        boolean legacySmooth = false;

        if (settingsFile != null && settingsFile.isFile())
        {
            try
            {
                BaseType data = DataToString.read(settingsFile);

                if (data != null && data.isMap())
                {
                    MapType editor = data.asMap().getMap("editor");

                    if (editor != null && editor.has("orbit_no_animation"))
                    {
                        legacySmooth = !editor.getBool("orbit_no_animation");
                    }
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        if (editorOrbitSmoothTransition != null)
        {
            editorOrbitSmoothTransition.set(legacySmooth);
        }

        orbitSmoothDefaultMigrated.set(true);
    }

    /**
     * Fold legacy Complementary/BSL opacity toggles into {@link #irisOpacityFix}.
     * Call after settings are loaded from disk.
     */
    public static void migrateIrisOpacityFix()
    {
        if (irisOpacityFix == null)
        {
            return;
        }

        boolean legacyOn = (complementaryOpacityFix != null && complementaryOpacityFix.get())
            || (bslOpacityFix != null && bslOpacityFix.get());

        if (legacyOn && !irisOpacityFix.get())
        {
            irisOpacityFix.set(true);
        }

        if (complementaryOpacityFix != null && complementaryOpacityFix.get())
        {
            complementaryOpacityFix.set(false);
        }

        if (bslOpacityFix != null && bslOpacityFix.get())
        {
            bslOpacityFix.set(false);
        }
    }
}
