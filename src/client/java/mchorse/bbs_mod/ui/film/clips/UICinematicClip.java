package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.clips.screen.CinematicClip;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.HashMap;
import java.util.Map;

public class UICinematicClip extends UIClip<CinematicClip>
{
    private static final int COLOR_GRADE = Colors.MAGENTA;

    public UIButton edit;
    public UIKeyframeEditor keyframes;

    private final Map<String, Boolean> collapsed = new HashMap<>();

    public UICinematicClip(CinematicClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.keyframes = new UIKeyframeEditor((consumer) -> new UIFilmKeyframes(this.editor, consumer));
        this.keyframes.view.backgroundRenderer((context) ->
        {
            UIReplaysEditor.renderBackground(context, this.keyframes.view, (Clips) this.clip.getParent(), Math.round(this.clip.tick.get()), this.clip);
        });
        this.keyframes.view.duration(() -> this.clip.duration.get());
        this.keyframes.setUndoId("cinematic_keyframes");

        this.edit = new UIButton(UIKeys.GENERAL_EDIT, (b) ->
        {
            this.editor.embedView(this.keyframes);
            this.keyframes.view.resetView();
            this.keyframes.view.getGraph().clearSelection();
        });
        this.edit.keys().register(Keys.FORMS_EDIT, () -> this.edit.clickItself());
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(UIKeys.SCREEN_PANELS_KEYFRAMES, this.edit));
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.rebuildChannels();
    }

    private void rebuildChannels()
    {
        UIKeyframes view = this.keyframes.view;

        view.removeAllSheets();

        String key = "cinematic";
        boolean expanded = !this.collapsed.getOrDefault(key, false);

        UIKeyframeSheet header = UIKeyframeSheet.groupHeader(
            "__cinematic__" + key,
            UIKeys.CAMERA_CLIPS_BBS_CINEMATIC,
            COLOR_GRADE,
            key,
            expanded,
            () ->
            {
                this.collapsed.put(key, !this.collapsed.getOrDefault(key, false));
                this.rebuildChannels();
            }
        );

        header.level = 0;
        view.addSheet(header);

        if (expanded)
        {
            UIKeyframeSheet shVHS = new UIKeyframeSheet(
                "vhs",
                UIKeys.CAMERA_CLIPS_CHANNEL_VHS,
                Colors.GREEN,
                false,
                this.clip.vhs,
                null
            );
            shVHS.level = 1;
            shVHS.groupKey = key;
            view.addSheet(shVHS);

            UIKeyframeSheet shVintage = new UIKeyframeSheet(
                "vintage",
                UIKeys.CAMERA_CLIPS_CHANNEL_VINTAGE,
                Colors.YELLOW,
                false,
                this.clip.vintage,
                null
            );
            shVintage.level = 1;
            shVintage.groupKey = key;
            view.addSheet(shVintage);

            UIKeyframeSheet shRadialBlur = new UIKeyframeSheet(
                "radialBlur",
                UIKeys.CAMERA_CLIPS_CHANNEL_RADIAL_BLUR,
                Colors.CYAN,
                false,
                this.clip.radialBlur,
                null
            );
            shRadialBlur.level = 1;
            shRadialBlur.groupKey = key;
            view.addSheet(shRadialBlur);

            UIKeyframeSheet shRain = new UIKeyframeSheet(
                "rain",
                UIKeys.CAMERA_CLIPS_CHANNEL_RAIN,
                0xff5577ff,
                false,
                this.clip.rain,
                null
            );
            shRain.level = 1;
            shRain.groupKey = key;
            view.addSheet(shRain);

            UIKeyframeSheet shDust = new UIKeyframeSheet(
                "dust",
                UIKeys.CAMERA_CLIPS_CHANNEL_DUST,
                0xffcccccc,
                false,
                this.clip.dust,
                null
            );
            shDust.level = 1;
            shDust.groupKey = key;
            view.addSheet(shDust);

            UIKeyframeSheet shLightLeak = new UIKeyframeSheet(
                "lightLeak",
                UIKeys.CAMERA_CLIPS_CHANNEL_LIGHT_LEAK,
                0xffffa033,
                false,
                this.clip.lightLeak,
                null
            );
            shLightLeak.level = 1;
            shLightLeak.groupKey = key;
            view.addSheet(shLightLeak);

            this.addAberrationGroup(view, key);
            this.addFisheyeGroup(view, key);
            this.addHeatGroup(view, key);
        }

        this.keyframes.view.getGraph().clearSelection();
    }

    private void addAberrationGroup(UIKeyframes view, String parentKey)
    {
        String aberrationKey = "aberration_group";
        boolean expanded = !this.collapsed.getOrDefault(aberrationKey, false);

        UIKeyframeSheet header = UIKeyframeSheet.groupHeader(
            "__cinematic__" + aberrationKey,
            UIKeys.CAMERA_CLIPS_CHANNEL_ABERRATION,
            Colors.RED,
            aberrationKey,
            expanded,
            () ->
            {
                this.collapsed.put(aberrationKey, !this.collapsed.getOrDefault(aberrationKey, false));
                this.rebuildChannels();
            }
        );

        header.level = 1;
        header.groupKey = parentKey;
        view.addSheet(header);

        if (expanded)
        {
            UIKeyframeSheet shIntensity = new UIKeyframeSheet(
                "aberration",
                UIKeys.CAMERA_CLIPS_CHANNEL_ABERRATION_INTENSITY,
                0xffff4444,
                false,
                this.clip.aberration,
                null
            );
            shIntensity.level = 2;
            shIntensity.groupKey = aberrationKey;
            view.addSheet(shIntensity);

            UIKeyframeSheet shAngle = new UIKeyframeSheet(
                "aberration_angle",
                UIKeys.CAMERA_CLIPS_CHANNEL_ABERRATION_ANGLE,
                0xffff6644,
                false,
                this.clip.aberrationAngle,
                null
            );
            shAngle.level = 2;
            shAngle.groupKey = aberrationKey;
            shAngle.defaultInsertValue = CinematicClip.DEFAULT_ABERRATION_ANGLE;
            view.addSheet(shAngle);

            UIKeyframeSheet shDirectional = new UIKeyframeSheet(
                "aberration_directional",
                UIKeys.CAMERA_CLIPS_CHANNEL_ABERRATION_DIRECTIONAL,
                0xffff8844,
                false,
                this.clip.aberrationDirectional,
                null
            );
            shDirectional.level = 2;
            shDirectional.groupKey = aberrationKey;
            shDirectional.defaultInsertValue = CinematicClip.DEFAULT_ABERRATION_DIRECTIONAL;
            view.addSheet(shDirectional);

            UIKeyframeSheet shRadius = new UIKeyframeSheet(
                "aberration_radius",
                UIKeys.CAMERA_CLIPS_CHANNEL_ABERRATION_RADIUS,
                0xffffaa44,
                false,
                this.clip.aberrationRadius,
                null
            );
            shRadius.level = 2;
            shRadius.groupKey = aberrationKey;
            shRadius.defaultInsertValue = CinematicClip.DEFAULT_ABERRATION_RADIUS;
            view.addSheet(shRadius);

            UIKeyframeSheet shHardness = new UIKeyframeSheet(
                "aberration_hardness",
                UIKeys.CAMERA_CLIPS_CHANNEL_ABERRATION_HARDNESS,
                0xffffcc44,
                false,
                this.clip.aberrationHardness,
                null
            );
            shHardness.level = 2;
            shHardness.groupKey = aberrationKey;
            shHardness.defaultInsertValue = CinematicClip.DEFAULT_ABERRATION_HARDNESS;
            view.addSheet(shHardness);

            UIKeyframeSheet shBalance = new UIKeyframeSheet(
                "aberration_balance",
                UIKeys.CAMERA_CLIPS_CHANNEL_ABERRATION_BALANCE,
                0xffff44aa,
                false,
                this.clip.aberrationBalance,
                null
            );
            shBalance.level = 2;
            shBalance.groupKey = aberrationKey;
            shBalance.defaultInsertValue = CinematicClip.DEFAULT_ABERRATION_BALANCE;
            view.addSheet(shBalance);

            UIKeyframeSheet shCenterX = new UIKeyframeSheet(
                "aberration_center_x",
                UIKeys.CAMERA_CLIPS_CHANNEL_ABERRATION_CENTER_X,
                0xffff6666,
                false,
                this.clip.aberrationCenterX,
                null
            );
            shCenterX.level = 2;
            shCenterX.groupKey = aberrationKey;
            shCenterX.defaultInsertValue = CinematicClip.DEFAULT_ABERRATION_CENTER_X;
            view.addSheet(shCenterX);

            UIKeyframeSheet shCenterY = new UIKeyframeSheet(
                "aberration_center_y",
                UIKeys.CAMERA_CLIPS_CHANNEL_ABERRATION_CENTER_Y,
                0xffff8888,
                false,
                this.clip.aberrationCenterY,
                null
            );
            shCenterY.level = 2;
            shCenterY.groupKey = aberrationKey;
            shCenterY.defaultInsertValue = CinematicClip.DEFAULT_ABERRATION_CENTER_Y;
            view.addSheet(shCenterY);

            UIKeyframeSheet shGreen = new UIKeyframeSheet(
                "aberration_green",
                UIKeys.CAMERA_CLIPS_CHANNEL_ABERRATION_GREEN,
                0xff44ff88,
                false,
                this.clip.aberrationGreen,
                null
            );
            shGreen.level = 2;
            shGreen.groupKey = aberrationKey;
            shGreen.defaultInsertValue = CinematicClip.DEFAULT_ABERRATION_GREEN;
            view.addSheet(shGreen);

            UIKeyframeSheet shSpectrum = new UIKeyframeSheet(
                "aberration_spectrum",
                UIKeys.CAMERA_CLIPS_CHANNEL_ABERRATION_SPECTRUM,
                0xffaa66ff,
                false,
                this.clip.aberrationSpectrum,
                null
            );
            shSpectrum.level = 2;
            shSpectrum.groupKey = aberrationKey;
            shSpectrum.defaultInsertValue = CinematicClip.DEFAULT_ABERRATION_SPECTRUM;
            view.addSheet(shSpectrum);
        }
    }

    private void addFisheyeGroup(UIKeyframes view, String parentKey)
    {
        String fisheyeKey = "fisheye";
        boolean expanded = !this.collapsed.getOrDefault(fisheyeKey, false);

        UIKeyframeSheet header = UIKeyframeSheet.groupHeader(
            "__cinematic__" + fisheyeKey,
            UIKeys.CAMERA_CLIPS_CHANNEL_FISHEYE_EFFECT,
            Colors.BLUE,
            fisheyeKey,
            expanded,
            () ->
            {
                this.collapsed.put(fisheyeKey, !this.collapsed.getOrDefault(fisheyeKey, false));
                this.rebuildChannels();
            }
        );

        header.level = 1;
        header.groupKey = parentKey;
        view.addSheet(header);

        if (expanded)
        {
            UIKeyframeSheet shIntensity = new UIKeyframeSheet(
                "lensDistortion",
                UIKeys.CAMERA_CLIPS_CHANNEL_FISHEYE_INTENSITY,
                0xff4488ff,
                false,
                this.clip.lensDistortion,
                null
            );
            shIntensity.level = 2;
            shIntensity.groupKey = fisheyeKey;
            view.addSheet(shIntensity);

            UIKeyframeSheet shDistanceFactor = new UIKeyframeSheet(
                "lens_distance_factor",
                UIKeys.CAMERA_CLIPS_CHANNEL_FISHEYE_DISTANCE_FACTOR,
                0xff5599ff,
                false,
                this.clip.lensDistanceFactor,
                null
            );
            shDistanceFactor.level = 2;
            shDistanceFactor.groupKey = fisheyeKey;
            shDistanceFactor.defaultInsertValue = CinematicClip.DEFAULT_LENS_DISTANCE_FACTOR;
            view.addSheet(shDistanceFactor);

            UIKeyframeSheet shRadius = new UIKeyframeSheet(
                "lens_radius",
                UIKeys.CAMERA_CLIPS_CHANNEL_FISHEYE_RADIUS,
                0xff66aaff,
                false,
                this.clip.lensRadius,
                null
            );
            shRadius.level = 2;
            shRadius.groupKey = fisheyeKey;
            shRadius.defaultInsertValue = CinematicClip.DEFAULT_LENS_RADIUS_SETTINGS;
            view.addSheet(shRadius);

            UIKeyframeSheet shHardness = new UIKeyframeSheet(
                "lens_hardness",
                UIKeys.CAMERA_CLIPS_CHANNEL_FISHEYE_HARDNESS,
                0xff88ccff,
                false,
                this.clip.lensHardness,
                null
            );
            shHardness.level = 2;
            shHardness.groupKey = fisheyeKey;
            shHardness.defaultInsertValue = CinematicClip.DEFAULT_LENS_HARDNESS;
            view.addSheet(shHardness);

            UIKeyframeSheet shSharpen = new UIKeyframeSheet(
                "lens_sharpen",
                UIKeys.CAMERA_CLIPS_CHANNEL_FISHEYE_SHARPEN,
                0xffaaddff,
                false,
                this.clip.lensSharpen,
                null
            );
            shSharpen.level = 2;
            shSharpen.groupKey = fisheyeKey;
            shSharpen.defaultInsertValue = CinematicClip.DEFAULT_LENS_SHARPEN;
            view.addSheet(shSharpen);
        }
    }

    private void addHeatGroup(UIKeyframes view, String parentKey)
    {
        String heatKey = "heat";
        boolean expanded = !this.collapsed.getOrDefault(heatKey, false);

        UIKeyframeSheet header = UIKeyframeSheet.groupHeader(
            "__cinematic__" + heatKey,
            UIKeys.CAMERA_CLIPS_CHANNEL_HEAT_DISTORTION,
            0xffff6633,
            heatKey,
            expanded,
            () ->
            {
                this.collapsed.put(heatKey, !this.collapsed.getOrDefault(heatKey, false));
                this.rebuildChannels();
            }
        );

        header.level = 1;
        header.groupKey = parentKey;
        view.addSheet(header);

        if (expanded)
        {
            UIKeyframeSheet shStrength = new UIKeyframeSheet(
                "heat_strength",
                UIKeys.CAMERA_CLIPS_CHANNEL_HEAT_STRENGTH,
                0xffff4422,
                false,
                this.clip.heatStrength,
                null
            );
            shStrength.level = 2;
            shStrength.groupKey = heatKey;
            view.addSheet(shStrength);

            UIKeyframeSheet shSpeed = new UIKeyframeSheet(
                "heat_speed",
                UIKeys.CAMERA_CLIPS_CHANNEL_HEAT_SPEED,
                0xffff8844,
                false,
                this.clip.heatSpeed,
                null
            );
            shSpeed.level = 2;
            shSpeed.groupKey = heatKey;
            view.addSheet(shSpeed);

            UIKeyframeSheet shScale = new UIKeyframeSheet(
                "heat_scale",
                UIKeys.CAMERA_CLIPS_CHANNEL_HEAT_SCALE,
                0xffffaa66,
                false,
                this.clip.heatScale,
                null
            );
            shScale.level = 2;
            shScale.groupKey = heatKey;
            view.addSheet(shScale);
        }
    }

    @Override
    protected UIKeyframeEditor resolveClipEmbeddableView(String undoId)
    {
        return undoId.equals(this.keyframes.getUndoId()) ? this.keyframes : null;
    }
}
