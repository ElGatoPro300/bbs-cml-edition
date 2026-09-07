package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.clips.misc.SubtitleClip;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueDouble;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import net.minecraft.util.math.MathHelper;

public class UISubtitleClip extends UIClip<SubtitleClip>
{
    private static final Color DEFAULT_COLOR = Color.white();
    private static final Color DEFAULT_BACKGROUND = new Color().set(0);

    public UITrackpad x;
    public UITrackpad y;
    public UITrackpad size;
    public UITrackpad anchorX;
    public UITrackpad anchorY;
    public UIColor color;
    public UIToggle textShadow;
    public UITrackpad windowX;
    public UITrackpad windowY;
    public UIColor background;
    public UITrackpad backgroundOffset;
    public UITrackpad shadow;
    public UIToggle shadowOpaque;
    public UITrackpad lineHeight;
    public UITrackpad maxWidth;
    public UITrackpad rotationX;
    public UITrackpad rotationY;
    public UITrackpad rotation;
    public UIToggle useKeyframes;
    public UIButton edit;
    public UIKeyframeEditor keyframes;

    private int lastSyncedCursor = Integer.MIN_VALUE;

    public UISubtitleClip(SubtitleClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.title.callback = (t) ->
        {
            this.writeString(this.clip.text, this.clip.uniform.text, t);
            this.clip.title.set(t);
            this.fillData();
        };

        this.x = this.createDoubleTrackpad(this.clip.x, this.clip.uniform.x, UIKeys.CAMERA_PANELS_SUBTITLE_OFFSET_X, false, null, null);
        this.y = this.createDoubleTrackpad(this.clip.y, this.clip.uniform.y, UIKeys.CAMERA_PANELS_SUBTITLE_OFFSET_Y, false, null, null);

        this.size = this.createDoubleTrackpad(this.clip.size, this.clip.uniform.size, UIKeys.CAMERA_PANELS_SUBTITLE_SIZE, false, null, null);

        this.anchorX = this.createDoubleTrackpad(this.clip.anchorX, this.clip.uniform.anchorX, UIKeys.CAMERA_PANELS_SUBTITLE_ANCHOR_X, false, null, null);
        this.anchorY = this.createDoubleTrackpad(this.clip.anchorY, this.clip.uniform.anchorY, UIKeys.CAMERA_PANELS_SUBTITLE_ANCHOR_Y, false, null, null);

        this.color = this.createColorField(this.clip.color, this.clip.uniform.color);
        this.color.withAlpha();

        this.textShadow = this.createBooleanField(this.clip.textShadow, this.clip.uniform.textShadow, UIKeys.CAMERA_PANELS_SUBTITLE_TEXT_SHADOW);

        this.windowX = this.createDoubleTrackpad(this.clip.windowX, this.clip.uniform.windowX, UIKeys.CAMERA_PANELS_SUBTITLE_WINDOW_X, false, null, null);
        this.windowY = this.createDoubleTrackpad(this.clip.windowY, this.clip.uniform.windowY, UIKeys.CAMERA_PANELS_SUBTITLE_WINDOW_Y, false, null, null);

        this.background = this.createColorField(this.clip.background, this.clip.uniform.background);
        this.background.withAlpha();

        this.backgroundOffset = this.createDoubleTrackpad(this.clip.backgroundOffset, this.clip.uniform.backgroundOffset, UIKeys.CAMERA_PANELS_SUBTITLE_BACKGROUND_OFFSET, false, null, null);
        this.shadow = this.createDoubleTrackpad(this.clip.shadow, this.clip.uniform.shadow, UIKeys.CAMERA_PANELS_SUBTITLE_SHADOW, false, 0F, null);
        this.shadowOpaque = this.createBooleanField(this.clip.shadowOpaque, this.clip.uniform.shadowOpaque, UIKeys.CAMERA_PANELS_SUBTITLE_OPAQUE);

        this.lineHeight = this.createDoubleTrackpad(this.clip.lineHeight, this.clip.uniform.lineHeight, UIKeys.CAMERA_PANELS_SUBTITLE_LINE_HEIGHT, false, 0F, null);
        this.lineHeight.tooltip(UIKeys.CAMERA_PANELS_SUBTITLE_LINE_HEIGHT, Direction.BOTTOM);

        this.maxWidth = this.createDoubleTrackpad(this.clip.maxWidth, this.clip.uniform.maxWidth, UIKeys.CAMERA_PANELS_SUBTITLE_MAX_WIDTH, false, 0F, null);
        this.maxWidth.tooltip(UIKeys.CAMERA_PANELS_SUBTITLE_MAX_WIDTH, Direction.BOTTOM);

        this.rotationX = this.createDoubleTrackpad(this.clip.rotationX, this.clip.uniform.rotationX, UIKeys.CAMERA_PANELS_SUBTITLE_ROTATION_X, false, null, null);
        this.rotationY = this.createDoubleTrackpad(this.clip.rotationY, this.clip.uniform.rotationY, UIKeys.CAMERA_PANELS_SUBTITLE_ROTATION_Y, false, null, null);
        this.rotation = this.createDoubleTrackpad(this.clip.rotation, this.clip.uniform.rotation, UIKeys.CAMERA_PANELS_SUBTITLE_ROTATION_Z, false, null, null);

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

        this.keyframes = this.createKeyframeEditor("subtitle_keyframes");

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

    private UITrackpad createDoubleTrackpad(KeyframeChannel<Double> channel, ValueDouble uniform, IKey tooltip, boolean integer, Float min, Float max)
    {
        UITrackpad trackpad = new UITrackpad((v) ->
        {
            this.writeDouble(channel, uniform, v.doubleValue());
            this.fillData();
        });

        if (integer)
        {
            trackpad.integer();
        }

        if (min != null)
        {
            if (max != null)
            {
                trackpad.limit(min, max);
            }
            else
            {
                trackpad.limit(min);
            }
        }

        if (tooltip != null)
        {
            trackpad.tooltip(tooltip);
        }

        return trackpad;
    }

    private UIColor createColorField(KeyframeChannel<Color> channel, ValueColor uniform)
    {
        return new UIColor((c) ->
        {
            this.writeColor(channel, uniform, Color.rgba(c));
            this.fillData();
        });
    }

    private UIToggle createBooleanField(KeyframeChannel<Boolean> channel, ValueBoolean uniform, IKey label)
    {
        return new UIToggle(label, (b) ->
        {
            this.writeBoolean(channel, uniform, b.getValue());
            this.fillData();
        });
    }

    private void writeString(KeyframeChannel<String> channel, ValueString uniform, String value)
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

    private void writeDouble(KeyframeChannel<Double> channel, ValueDouble uniform, double value)
    {
        if (channel == this.clip.lineHeight || channel == this.clip.maxWidth)
        {
            value = Math.max(SubtitleClip.CONSTRAINT_MIN, value);
        }

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

    private void writeBoolean(KeyframeChannel<Boolean> channel, ValueBoolean uniform, boolean value)
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
            UIReplaysEditor.renderBackground(context, editor.view, (Clips) this.clip.getParent(), Math.round(this.clip.tick.get()), this.clip);
        });
        editor.view.duration(() -> this.clip.duration.get());
        editor.view.changed(() -> this.fillData());
        editor.setUndoId(undoId);

        return editor;
    }

    private int getClipTick()
    {
        return MathHelper.clamp(this.editor.getCursor() - Math.round(this.clip.tick.get()), 0, this.clip.duration.get());
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(UIKeys.CAMERA_PANELS_SUBTITLE_OFFSET, UI.row(this.x, this.y)));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_SUBTITLE_SIZE, this.size, this.color, this.textShadow));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_SUBTITLE_ANCHOR, UI.row(this.anchorX, this.anchorY)));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_SUBTITLE_ROTATION, UI.row(this.rotationX, this.rotationY, this.rotation)));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_SUBTITLE_WINDOW, UI.row(this.windowX, this.windowY)));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_SUBTITLE_BACKGROUND, this.background, this.backgroundOffset));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_SUBTITLE_SHADOW, this.shadow, this.shadowOpaque));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_SUBTITLE_CONSTRAINT, UI.row(this.lineHeight, this.maxWidth)));
        this.panels.add(this.section(UIKeys.SCREEN_PANELS_KEYFRAMES, this.useKeyframes, this.edit));
    }

    @Override
    public void fillData()
    {
        super.fillData();

        /* setText() moves the caret to the start — skip while the user is typing. */
        if (!this.title.isFocused())
        {
            this.title.setText(this.getStringValue(this.clip.text, this.clip.uniform.text, this.clip.title.get()));
        }

        this.x.setValue(this.getDoubleValue(this.clip.x, this.clip.uniform.x, 0D));
        this.y.setValue(this.getDoubleValue(this.clip.y, this.clip.uniform.y, 0D));
        this.size.setValue(this.getDoubleValue(this.clip.size, this.clip.uniform.size, 10D));
        this.anchorX.setValue(this.getDoubleValue(this.clip.anchorX, this.clip.uniform.anchorX, 0.5D));
        this.anchorY.setValue(this.getDoubleValue(this.clip.anchorY, this.clip.uniform.anchorY, 0.5D));
        this.color.setColor(this.getColorValue(this.clip.color, this.clip.uniform.color, DEFAULT_COLOR).getARGBColor());
        this.textShadow.setValue(this.getBooleanValue(this.clip.textShadow, this.clip.uniform.textShadow, true));
        this.windowX.setValue(this.getDoubleValue(this.clip.windowX, this.clip.uniform.windowX, 0.5D));
        this.windowY.setValue(this.getDoubleValue(this.clip.windowY, this.clip.uniform.windowY, 0.5D));
        this.background.setColor(this.getColorValue(this.clip.background, this.clip.uniform.background, DEFAULT_BACKGROUND).getARGBColor());
        this.backgroundOffset.setValue(this.getDoubleValue(this.clip.backgroundOffset, this.clip.uniform.backgroundOffset, 2D));
        this.shadow.setValue(this.getDoubleValue(this.clip.shadow, this.clip.uniform.shadow, 0D));
        this.shadowOpaque.setValue(this.getBooleanValue(this.clip.shadowOpaque, this.clip.uniform.shadowOpaque, false));
        this.lineHeight.setValue(this.getDoubleValue(this.clip.lineHeight, this.clip.uniform.lineHeight, 12D));
        this.maxWidth.setValue(this.getDoubleValue(this.clip.maxWidth, this.clip.uniform.maxWidth, 0D));
        this.rotationX.setValue(this.getDoubleValue(this.clip.rotationX, this.clip.uniform.rotationX, 0D));
        this.rotationY.setValue(this.getDoubleValue(this.clip.rotationY, this.clip.uniform.rotationY, 0D));
        this.rotation.setValue(this.getDoubleValue(this.clip.rotation, this.clip.uniform.rotation, 0D));
        this.useKeyframes.setValue(this.clip.useKeyframes.get());
        this.updateKeyframesControls();

        /* Avoid rebuilding keyframe sheets on every cursor scrub — only when empty. */
        if (this.keyframes.view.getGraph().getSheets().isEmpty())
        {
            this.keyframes.setChannels(this.clip.channels);
        }

        this.applySheetLimits();
        this.updateTrackTitles();
        this.lastSyncedCursor = this.editor.getCursor();
    }

    /**
     * Keep property inputs in sync if the film cursor moves without going through
     * the normal scrub refresh path (e.g. after a prior fillData failure).
     */
    @Override
    public void render(UIContext context)
    {
        int cursor = this.editor.getCursor();

        if (cursor != this.lastSyncedCursor)
        {
            this.fillData();
        }

        super.render(context);
    }

    private void applySheetLimits()
    {
        for (UIKeyframeSheet sheet : this.keyframes.view.getGraph().getSheets())
        {
            if ("lineHeight".equals(sheet.id) || "maxWidth".equals(sheet.id) || "shadow".equals(sheet.id))
            {
                sheet.limit(0D, null);
            }
        }
    }

    private String getStringValue(KeyframeChannel<String> channel, ValueString uniform, String fallback)
    {
        if (!this.clip.useKeyframes.get())
        {
            String value = uniform.get();

            return value == null || value.isEmpty() ? fallback : value;
        }

        if (channel.isEmpty())
        {
            String value = uniform.get();

            return value == null || value.isEmpty() ? fallback : value;
        }

        return channel.interpolate(this.getClipTick(), fallback);
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

        return this.readDouble(channel, this.getClipTick(), fallback);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private double readDouble(KeyframeChannel<Double> channel, float tick, double fallback)
    {
        Object value = ((KeyframeChannel) channel).interpolate(tick);

        if (value instanceof Number)
        {
            return ((Number) value).doubleValue();
        }

        return fallback;
    }

    private boolean getBooleanValue(KeyframeChannel<Boolean> channel, ValueBoolean uniform, boolean fallback)
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

    private void updateTrackTitles()
    {
        for (UIKeyframeSheet sheet : this.keyframes.view.getGraph().getSheets())
        {
            sheet.title = this.getTrackTitle(sheet.id);
        }
    }

    private IKey getTrackTitle(String id)
    {
        return switch (id)
        {
            case "text" -> UIKeys.CAMERA_PANELS_TITLE;
            case "x" -> UIKeys.CAMERA_PANELS_SUBTITLE_OFFSET_X;
            case "y" -> UIKeys.CAMERA_PANELS_SUBTITLE_OFFSET_Y;
            case "size" -> UIKeys.CAMERA_PANELS_SUBTITLE_SIZE;
            case "anchorX" -> UIKeys.CAMERA_PANELS_SUBTITLE_ANCHOR_X;
            case "anchorY" -> UIKeys.CAMERA_PANELS_SUBTITLE_ANCHOR_Y;
            case "color" -> UIKeys.CAMERA_PANELS_SUBTITLE_COLOR;
            case "textShadow" -> UIKeys.CAMERA_PANELS_SUBTITLE_TEXT_SHADOW;
            case "windowX" -> UIKeys.CAMERA_PANELS_SUBTITLE_WINDOW_X;
            case "windowY" -> UIKeys.CAMERA_PANELS_SUBTITLE_WINDOW_Y;
            case "background" -> UIKeys.CAMERA_PANELS_SUBTITLE_BACKGROUND;
            case "backgroundOffset" -> UIKeys.CAMERA_PANELS_SUBTITLE_BACKGROUND_OFFSET;
            case "shadow" -> UIKeys.CAMERA_PANELS_SUBTITLE_SHADOW;
            case "shadowOpaque" -> UIKeys.CAMERA_PANELS_SUBTITLE_OPAQUE;
            case "lineHeight" -> UIKeys.CAMERA_PANELS_SUBTITLE_LINE_HEIGHT;
            case "maxWidth" -> UIKeys.CAMERA_PANELS_SUBTITLE_MAX_WIDTH;
            case "rotationX" -> UIKeys.CAMERA_PANELS_SUBTITLE_ROTATION_X;
            case "rotationY" -> UIKeys.CAMERA_PANELS_SUBTITLE_ROTATION_Y;
            case "rotation" -> UIKeys.CAMERA_PANELS_SUBTITLE_ROTATION_Z;
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

        if (data.getString("embed").equals("subtitle_keyframes") && this.clip.useKeyframes.get())
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
            data.putString("embed", "subtitle_keyframes");
        }
    }
}
