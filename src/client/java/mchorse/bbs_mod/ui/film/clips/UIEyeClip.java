package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.clips.screen.EyeClip;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import net.minecraft.util.math.MathHelper;

public class UIEyeClip extends UIClip<EyeClip>
{
    private static final Color DEFAULT_COLOR = Color.rgba(Colors.A100);

    public UIColor color;
    public UITrackpad colorOpacity;
    public UITrackpad rotation;
    public UITrackpad zoom;
    public UITrackpad width;
    public UITrackpad height;
    public UITrackpad offsetX;
    public UITrackpad offsetY;
    public UIButton edit;
    public UIKeyframeEditor keyframes;

    public UIEyeClip(EyeClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.color = this.createColorChannel(this.clip.color);
        this.color.withAlpha();

        this.colorOpacity = new UITrackpad((v) ->
        {
            int tick = this.getClipTick();

            this.clip.colorOpacity.insert(tick, v.doubleValue() / 100D);
            this.fillData();
        });
        this.colorOpacity.integer();
        this.colorOpacity.limit(0, 100);
        this.colorOpacity.tooltip(UIKeys.SCREEN_PANELS_EYE_COLOR_OPACITY);

        this.rotation = this.createChannelTrackpad(this.clip.rotation, -45, 45, UIKeys.SCREEN_PANELS_EYE_ROTATION);
        this.zoom = this.createChannelTrackpad(this.clip.zoom, 0.1F, 10F, UIKeys.SCREEN_PANELS_EYE_ZOOM);
        this.width = this.createChannelTrackpad(this.clip.width, UIKeys.SCREEN_PANELS_EYE_WIDTH);
        this.height = this.createChannelTrackpad(this.clip.height, 0F, 1F, UIKeys.SCREEN_PANELS_EYE_HEIGHT);

        this.height.increment(0.01D).values(0.1D, 0.01D, 0.25D);
        this.offsetX = this.createChannelTrackpad(this.clip.offsetX, -1F, 1F, UIKeys.SCREEN_PANELS_EYE_OFFSET_X);
        this.offsetY = this.createChannelTrackpad(this.clip.offsetY, -1F, 1F, UIKeys.SCREEN_PANELS_EYE_OFFSET_Y);

        this.keyframes = this.createKeyframeEditor("eye_keyframes");

        this.edit = new UIButton(UIKeys.GENERAL_EDIT, (b) ->
        {
            this.editor.embedView(this.keyframes);
            this.keyframes.view.resetView();
            this.keyframes.view.getGraph().clearSelection();
        });
        this.edit.keys().register(Keys.FORMS_EDIT, () -> this.edit.clickItself());
    }

    private UIColor createColorChannel(KeyframeChannel<Color> channel)
    {
        return new UIColor((c) ->
        {
            int tick = this.getClipTick();

            channel.insert(tick, Color.rgba(Colors.setA(c, 1F)));
            this.fillData();
        });
    }

    private UITrackpad createChannelTrackpad(KeyframeChannel<Double> channel, IKey tooltip)
    {
        UITrackpad trackpad = new UITrackpad((v) ->
        {
            int tick = this.getClipTick();

            channel.insert(tick, v.doubleValue());
            this.fillData();
        });

        trackpad.tooltip(tooltip);

        return trackpad;
    }

    private UITrackpad createChannelTrackpad(KeyframeChannel<Double> channel, float min, float max, IKey tooltip)
    {
        UITrackpad trackpad = this.createChannelTrackpad(channel, tooltip);

        trackpad.limit(min, max);

        return trackpad;
    }

    private UIKeyframeEditor createKeyframeEditor(String undoId)
    {
        UIKeyframeEditor editor = new UIKeyframeEditor((consumer) -> new UIFilmKeyframes(this.editor, consumer));

        editor.view.backgroundRenderer((context) ->
        {
            UIReplaysEditor.renderBackground(context, editor.view, (Clips) this.clip.getParent(), this.clip.tick.get(), this.clip);
        });
        editor.view.duration(() -> this.clip.duration.get());
        editor.setUndoId(undoId);

        return editor;
    }

    private int getClipTick()
    {
        return MathHelper.clamp(this.editor.getCursor() - this.clip.tick.get(), 0, this.clip.duration.get());
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(UIKeys.SCREEN_PANELS_EYE_COLOR, this.color, this.colorOpacity));
        this.panels.add(this.section(UIKeys.SCREEN_PANELS_EYE_TILT, this.rotation, this.zoom, this.width, this.height));
        this.panels.add(this.section(UIKeys.SCREEN_PANELS_EYE_OFFSET, UI.row(this.offsetX, this.offsetY)));
        this.panels.add(this.section(UIKeys.SCREEN_PANELS_KEYFRAMES, this.edit));
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.color.setColor(Colors.setA(this.getColorValue(this.clip.color, DEFAULT_COLOR).getARGBColor(), 1F));
        this.colorOpacity.setValue(this.getChannelValue(this.clip.colorOpacity, 1D) * 100F);
        this.rotation.setValue(this.getChannelValue(this.clip.rotation, 0D));
        this.zoom.setValue(this.getChannelValue(this.clip.zoom, 1D));
        this.width.setValue(this.getChannelValue(this.clip.width, 1D));
        this.height.setValue(this.getChannelValue(this.clip.height, 0D));
        this.offsetX.setValue(this.getChannelValue(this.clip.offsetX, 0D));
        this.offsetY.setValue(this.getChannelValue(this.clip.offsetY, 0D));

        this.keyframes.setChannels(this.clip.channels);
        this.applySheetLimits();
        this.updateTrackTitles(this.keyframes);
    }

    private double getChannelValue(KeyframeChannel<Double> channel, double fallback)
    {
        int tick = this.getClipTick();

        if (channel.isEmpty())
        {
            return fallback;
        }

        return channel.interpolate(tick);
    }

    private Color getColorValue(KeyframeChannel<Color> channel, Color fallback)
    {
        int tick = this.getClipTick();

        if (channel.isEmpty())
        {
            return fallback;
        }

        return channel.interpolate(tick, fallback);
    }

    /**
     * Match keyframe graph/trackpad clamps to the property panel limits.
     */
    private void applySheetLimits()
    {
        for (UIKeyframeSheet sheet : this.keyframes.view.getGraph().getSheets())
        {
            if ("color".equals(sheet.id))
            {
                sheet.defaultInsertValue = DEFAULT_COLOR.copy();
            }
            else if ("color_opacity".equals(sheet.id) || "height".equals(sheet.id))
            {
                sheet.limit(0D, 1D);
            }
            else if ("width".equals(sheet.id) || "zoom".equals(sheet.id))
            {
                sheet.defaultInsertValue = 1D;

                if ("zoom".equals(sheet.id))
                {
                    sheet.limit(0.1D, 10D);
                }
            }
            else if ("rotation".equals(sheet.id))
            {
                sheet.limit(-45D, 45D);
            }
        }
    }

    private void updateTrackTitles(UIKeyframeEditor editor)
    {
        for (UIKeyframeSheet sheet : editor.view.getGraph().getSheets())
        {
            sheet.title = this.getTrackTitle(sheet.id);
        }
    }

    private IKey getTrackTitle(String id)
    {
        return switch (id)
        {
            case "color" -> UIKeys.SCREEN_PANELS_EYE_COLOR;
            case "color_opacity" -> UIKeys.SCREEN_PANELS_EYE_COLOR_OPACITY;
            case "height" -> UIKeys.SCREEN_PANELS_EYE_HEIGHT;
            case "size" -> UIKeys.SCREEN_PANELS_EYE_HEIGHT;
            case "width" -> UIKeys.SCREEN_PANELS_EYE_WIDTH;
            case "smoothness" -> UIKeys.SCREEN_PANELS_EYE_SMOOTHNESS;
            case "rotation" -> UIKeys.SCREEN_PANELS_EYE_ROTATION;
            case "zoom" -> UIKeys.SCREEN_PANELS_EYE_ZOOM;
            default -> IKey.constant(id);
        };
    }

    @Override
    protected UIKeyframeEditor resolveClipEmbeddableView(String undoId)
    {
        return undoId.equals(this.keyframes.getUndoId()) ? this.keyframes : null;
    }

    @Override
    public void applyUndoData(MapType data)
    {
        super.applyUndoData(data);

        if (data.getString("embed").equals("eye_keyframes"))
        {
            this.editor.embedView(this.keyframes);
            this.keyframes.view.resetView();
        }
    }

    @Override
    public void collectUndoData(MapType data)
    {
        super.collectUndoData(data);

        if (this.keyframes.hasParent())
        {
            data.putString("embed", "eye_keyframes");
        }
    }
}
