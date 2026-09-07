package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.clips.screen.LetterboxClip;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.numeric.ValueDouble;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
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

public class UILetterboxClip extends UIClip<LetterboxClip>
{
    private static final Color DEFAULT_COLOR = Color.rgba(Colors.A100);

    public UIColor color;
    public UITrackpad rotation;
    public UITrackpad zoom;
    public UITrackpad width;
    public UITrackpad height;
    public UITrackpad offsetX;
    public UITrackpad offsetY;
    public UIToggle useKeyframes;
    public UIButton edit;
    public UIKeyframeEditor keyframes;

    public UILetterboxClip(LetterboxClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.color = this.createColorField(this.clip.color, this.clip.uniform.color);

        /* Size/zoom stay non-negative; rotation is unbounded so negative tilt works. */
        this.rotation = this.createDoubleTrackpad(this.clip.rotation, this.clip.uniform.rotation, UIKeys.SCREEN_PANELS_LETTERBOX_ROTATION);
        this.zoom = this.createDoubleTrackpad(this.clip.zoom, this.clip.uniform.zoom, 0F, UIKeys.SCREEN_PANELS_LETTERBOX_ZOOM);
        this.width = this.createDoubleTrackpad(this.clip.width, this.clip.uniform.width, 0F, UIKeys.SCREEN_PANELS_LETTERBOX_WIDTH);
        this.height = this.createDoubleTrackpad(this.clip.height, this.clip.uniform.height, 0F, UIKeys.SCREEN_PANELS_LETTERBOX_HEIGHT);
        this.height.increment(0.01D).values(0.1D, 0.01D, 0.25D);
        /* Offsets are relative screen shifts — negatives must be allowed. */
        this.offsetX = this.createDoubleTrackpad(this.clip.offsetX, this.clip.uniform.offsetX, UIKeys.SCREEN_PANELS_LETTERBOX_OFFSET_X);
        this.offsetY = this.createDoubleTrackpad(this.clip.offsetY, this.clip.uniform.offsetY, UIKeys.SCREEN_PANELS_LETTERBOX_OFFSET_Y);

        this.useKeyframes = new UIToggle(UIKeys.SCREEN_PANELS_USE_KEYFRAMES, (b) ->
        {
            boolean enabled = b.getValue();
            float tick = this.getClipTick();

            this.clip.useKeyframes.set(enabled);

            if (enabled)
            {
                this.clip.ensureChannelsSeeded(tick);
                this.keyframes.setChannels(this.clip.channels);
            }
            else
            {
                this.clip.ensureUniformSeeded(tick);

                if (this.keyframes.hasParent())
                {
                    this.editor.embedView(null);
                }
            }

            this.updateKeyframesControls();
            this.fillData();
        });
        this.useKeyframes.tooltip(UIKeys.SCREEN_PANELS_USE_KEYFRAMES_TOOLTIP);

        this.keyframes = this.createKeyframeEditor("letterbox_keyframes");

        this.edit = new UIButton(UIKeys.GENERAL_EDIT, (b) ->
        {
            if (!this.clip.useKeyframes.get())
            {
                return;
            }

            this.editor.embedView(this.keyframes);
            this.keyframes.view.resetView();
            this.keyframes.view.getGraph().clearSelection();
        });
        this.edit.keys().register(Keys.FORMS_EDIT, () -> this.edit.clickItself());
    }

    private UIColor createColorField(KeyframeChannel<Color> channel, ValueColor uniform)
    {
        return new UIColor((c) ->
        {
            this.writeColor(channel, uniform, Color.rgba(Colors.setA(c, 1F)));
            this.fillData();
        });
    }

    private UITrackpad createDoubleTrackpad(KeyframeChannel<Double> channel, ValueDouble uniform, IKey tooltip)
    {
        UITrackpad trackpad = new UITrackpad((v) ->
        {
            this.writeDouble(channel, uniform, v.doubleValue());
            this.fillData();
        });

        trackpad.tooltip(tooltip);

        return trackpad;
    }

    private UITrackpad createDoubleTrackpad(KeyframeChannel<Double> channel, ValueDouble uniform, float min, IKey tooltip)
    {
        UITrackpad trackpad = this.createDoubleTrackpad(channel, uniform, tooltip);

        trackpad.limit(min);

        return trackpad;
    }

    private void writeDouble(KeyframeChannel<Double> channel, ValueDouble uniform, double value)
    {
        if (this.clip.useKeyframes.get())
        {
            channel.insert(this.getClipTick(), value);
        }
        else
        {
            this.clip.uniformSeeded.set(true);
            uniform.set(value);
        }
    }

    private void writeColor(KeyframeChannel<Color> channel, ValueColor uniform, Color value)
    {
        if (this.clip.useKeyframes.get())
        {
            channel.insert(this.getClipTick(), value);
        }
        else
        {
            this.clip.uniformSeeded.set(true);
            uniform.set(value);
        }
    }

    private void updateKeyframesControls()
    {
        this.edit.setEnabled(this.clip.useKeyframes.get());
    }

    private UIKeyframeEditor createKeyframeEditor(String undoId)
    {
        UIKeyframeEditor editor = new UIKeyframeEditor((consumer) -> new UIFilmKeyframes(this.editor, consumer));

        editor.view.backgroundRenderer((context) ->
        {
            UIReplaysEditor.renderBackground(context, editor.view, (Clips) this.clip.getParent(), this.clip.tick.get(), this.clip);
        });
        editor.view.duration(() -> this.clip.duration.get());
        /* Do not call fillData on every keyframe edit — that rebuilt sheets via setChannels
         * and cleared the selection (closing the value editor while typing/dragging). */
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

        this.panels.add(this.section(UIKeys.SCREEN_PANELS_LETTERBOX_COLOR, this.color));
        this.panels.add(this.section(UIKeys.SCREEN_PANELS_LETTERBOX_TILT, this.rotation, this.zoom, this.width, this.height));
        this.panels.add(this.section(UIKeys.SCREEN_PANELS_LETTERBOX_OFFSET, UI.row(this.offsetX, this.offsetY)));
        this.panels.add(this.section(UIKeys.SCREEN_PANELS_KEYFRAMES, this.useKeyframes, this.edit));
    }

    @Override
    public void fillData()
    {
        super.fillData();

        /* Skip rewriting focused keyframe factory inputs mid-edit. */
        if (!this.keyframes.isEditorInputFocused())
        {
            this.color.setColor(Colors.setA(this.getColorValue(this.clip.color, this.clip.uniform.color, DEFAULT_COLOR).getARGBColor(), 1F));
            this.rotation.setValue(this.getDoubleValue(this.clip.rotation, this.clip.uniform.rotation, 0D));
            this.zoom.setValue(this.getDoubleValue(this.clip.zoom, this.clip.uniform.zoom, 1D));
            this.width.setValue(this.getDoubleValue(this.clip.width, this.clip.uniform.width, 1D));
            this.height.setValue(this.getHeightValue());
            this.offsetX.setValue(this.getDoubleValue(this.clip.offsetX, this.clip.uniform.offsetX, 0D));
            this.offsetY.setValue(this.getDoubleValue(this.clip.offsetY, this.clip.uniform.offsetY, 0D));
        }

        this.useKeyframes.setValue(this.clip.useKeyframes.get());
        this.updateKeyframesControls();

        /* Avoid rebuilding sheets on every scrub/edit — setChannels clears keyframe pick. */
        if (this.keyframes.view.getGraph().getSheets().isEmpty()
            || this.keyframes.view.getGraph().getSheets().size() != this.clip.channels.length)
        {
            this.keyframes.setChannels(this.clip.channels);
        }

        for (UIKeyframeSheet sheet : this.keyframes.view.getGraph().getSheets())
        {
            if ("color".equals(sheet.id))
            {
                sheet.defaultInsertValue = DEFAULT_COLOR.copy();
            }
            else if ("width".equals(sheet.id))
            {
                sheet.defaultInsertValue = 1D;
                sheet.limit(0D, null);
            }
            else if ("height".equals(sheet.id) || "size".equals(sheet.id))
            {
                sheet.defaultInsertValue = 0.4D;
                sheet.limit(0D, null);
            }
            else if ("zoom".equals(sheet.id))
            {
                sheet.defaultInsertValue = 1D;
                sheet.limit(0D, null);
            }
            else if ("smoothness".equals(sheet.id))
            {
                sheet.limit(0D, null);
            }
        }

        this.updateTrackTitles(this.keyframes);
    }

    private double getHeightValue()
    {
        if (!this.clip.useKeyframes.get())
        {
            return this.clip.uniform.height.get();
        }

        if (!this.clip.height.isEmpty())
        {
            return this.clip.height.interpolate(this.getClipTick());
        }

        if (!this.clip.size.isEmpty())
        {
            return this.clip.size.interpolate(this.getClipTick());
        }

        return this.clip.uniformSeeded.get() ? this.clip.uniform.height.get() : 0.4D;
    }

    private double getDoubleValue(KeyframeChannel<Double> channel, ValueDouble uniform, double fallback)
    {
        if (!this.clip.useKeyframes.get())
        {
            return uniform.get();
        }

        if (channel.isEmpty())
        {
            return this.clip.uniformSeeded.get() ? uniform.get() : fallback;
        }

        return channel.interpolate(this.getClipTick());
    }

    private Color getColorValue(KeyframeChannel<Color> channel, ValueColor uniform, Color fallback)
    {
        if (!this.clip.useKeyframes.get())
        {
            return uniform.get();
        }

        if (channel.isEmpty())
        {
            return this.clip.uniformSeeded.get() ? uniform.get() : fallback;
        }

        return channel.interpolate(this.getClipTick(), fallback);
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
            case "color" -> UIKeys.SCREEN_PANELS_LETTERBOX_COLOR;
            case "height" -> UIKeys.SCREEN_PANELS_LETTERBOX_HEIGHT;
            case "size" -> UIKeys.SCREEN_PANELS_LETTERBOX_HEIGHT;
            case "width" -> UIKeys.SCREEN_PANELS_LETTERBOX_WIDTH;
            case "smoothness" -> UIKeys.SCREEN_PANELS_LETTERBOX_SMOOTHNESS;
            case "rotation" -> UIKeys.SCREEN_PANELS_LETTERBOX_ROTATION;
            case "zoom" -> UIKeys.SCREEN_PANELS_LETTERBOX_ZOOM;
            case "offsetX" -> UIKeys.SCREEN_PANELS_LETTERBOX_OFFSET_X;
            case "offsetY" -> UIKeys.SCREEN_PANELS_LETTERBOX_OFFSET_Y;
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

        if (data.getString("embed").equals("letterbox_keyframes") && this.clip.useKeyframes.get())
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
            data.putString("embed", "letterbox_keyframes");
        }
    }
}
