package mchorse.bbs_mod.ui.film.clips.widgets;

import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.clips.UIClip;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.context.UIInterpolationContextMenu;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.tooltips.InterpolationTooltip;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.TimeUtilsClient;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.clips.Envelope;
import mchorse.bbs_mod.utils.colors.Colors;

public class UIEnvelope extends UIElement
{
    public UIClip<? extends Clip> panel;

    public UIToggle enabled;
    public UICirculate mode;
    public UIButton pre;
    public UIButton post;
    public UITrackpad fadeIn;
    public UITrackpad fadeOut;

    public UIToggle keyframes;
    public UIButton editKeyframes;
    public UIKeyframeEditor channel;

    public UIEnvelope(UIClip<? extends Clip> panel)
    {
        super();

        this.panel = panel;

        InterpolationTooltip preTooltip = new InterpolationTooltip(0F, 0.5F, () -> this.get().pre);
        InterpolationTooltip postTooltip = new InterpolationTooltip(0F, 0.5F, () -> this.get().post);

        this.enabled = new UIToggle(UIKeys.CAMERA_PANELS_ENABLED, (b) ->
        {
            this.panel.editor.editMultiple(this.get().enabled, (value) -> value.set(b.getValue()));
        });
        this.mode = new UICirculate((b) ->
        {
            this.panel.editor.editMultiple(this.get().mode, (value) -> value.set(b.getValue()));
        });
        this.mode.addLabel(UIKeys.CAMERA_PANELS_ENVELOPES_MODES_NORMAL);
        this.mode.addLabel(UIKeys.CAMERA_PANELS_ENVELOPES_START_D);
        this.mode.addLabel(UIKeys.CAMERA_PANELS_ENVELOPES_END_D);
        this.pre = new UIButton(UIKeys.CAMERA_PANELS_ENVELOPES_PRE, (b) ->
        {
            this.getContext().replaceContextMenu(new UIInterpolationContextMenu(this.get().pre));
        });
        this.pre.tooltip(preTooltip);
        this.post = new UIButton(UIKeys.CAMERA_PANELS_ENVELOPES_POST, (b) ->
        {
            this.getContext().replaceContextMenu(new UIInterpolationContextMenu(this.get().post));
        });
        this.post.tooltip(postTooltip);

        this.fadeIn = new UITrackpad((v) ->
        {
            this.panel.editor.editMultiple(this.get().fadeIn, (value) -> value.set((float) TimeUtils.fromTime(v.floatValue())));
        });
        this.fadeIn.tooltip(UIKeys.CAMERA_PANELS_ENVELOPES_START_D, Direction.TOP);
        this.fadeOut = new UITrackpad((v) ->
        {
            this.panel.editor.editMultiple(this.get().fadeOut, (value) -> value.set((float) TimeUtils.fromTime(v.floatValue())));
        });
        this.fadeOut.tooltip(UIKeys.CAMERA_PANELS_ENVELOPES_END_D, Direction.TOP);

        this.keyframes = new UIToggle(UIKeys.CAMERA_PANELS_KEYFRAMES, (b) ->
        {
            this.panel.editor.editMultiple(this.get().keyframes, (value) -> value.set(b.getValue()));
            this.toggleKeyframes(b.getValue());
        });
        this.editKeyframes = new UIButton(UIKeys.CAMERA_PANELS_EDIT_KEYFRAMES, (b) ->
        {
            this.panel.editor.embedView(this.channel);
            this.channel.view.resetView();
            this.channel.view.editSheet(this.channel.view.getGraph().getSheets().get(0));
            this.channel.view.getGraph().clearSelection();
        });
        this.channel = new UIKeyframeEditor((consumer) -> new UIFilmKeyframes(this.panel.editor, consumer));
        this.channel.view.backgroundRenderer((context) ->
        {
            UIReplaysEditor.renderBackground(context, this.channel.view, (Clips) this.panel.clip.getParent(), Math.round(this.panel.clip.tick.get()), this.panel.clip);
        });
        this.channel.view.single().duration(() -> this.panel.clip.duration.get());

        this.column().vertical().stretch();
    }

    private void toggleKeyframes(boolean toggled)
    {
        this.removeAll();

        this.add(this.enabled, this.mode);

        if (toggled)
        {
            this.add(this.editKeyframes);
        }
        else
        {
            this.add(UI.row(this.pre, this.post), UI.row(this.fadeIn, this.fadeOut));
        }

        this.add(this.keyframes);

        this.resizeClipPanels();

        if (toggled && this.hasParent())
        {
            this.initiate();
        }
    }

    /**
     * Reflow the clip {@link UIScrollView} columns. Walking only two parents broke after
     * sections wrapped envelopes ({@code panels → section → fields → envelope}): resizing
     * the section alone re-entered the horizontal {@code ColumnResizer} without resetting
     * column cursors and drifted widgets on every scrub {@link #fillData()}.
     */
    private void resizeClipPanels()
    {
        UIElement element = this;

        while (element != null)
        {
            if (element instanceof UIScrollView)
            {
                element.resize();

                return;
            }

            element = element.getParent();
        }

        if (this.hasParent())
        {
            this.getParent().resize();
        }
    }

    public void initiate()
    {
        this.channel.view.resetView();

        TimeUtilsClient.configure(this.fadeIn, 0);
        TimeUtilsClient.configure(this.fadeOut, 0);

        this.fillIntervals();
    }

    public void fillData()
    {
        Envelope envelope = this.get();
        boolean keyframesOn = envelope.keyframes.get();

        this.enabled.setValue(envelope.enabled.get());
        this.mode.setValue(envelope.mode.get());
        this.fillIntervals();
        this.keyframes.setValue(keyframesOn);
        this.channel.setChannel(envelope.channel, Colors.ACTIVE);

        /* Rebuild only when the keyframe mode actually changes — scrub/fillData used to
         * tear down and re-add children every tick, which corrupted multi-column layout. */
        if (this.shouldRebuildKeyframesLayout(keyframesOn))
        {
            this.toggleKeyframes(keyframesOn);
        }
    }

    private boolean shouldRebuildKeyframesLayout(boolean keyframesOn)
    {
        if (this.getChildren().isEmpty())
        {
            return true;
        }

        boolean showingEdit = false;

        for (IUIElement child : this.getChildren())
        {
            if (child == this.editKeyframes)
            {
                showingEdit = true;

                break;
            }
        }

        return showingEdit != keyframesOn;
    }

    private void fillIntervals()
    {
        Envelope envelope = this.get();

        this.fadeIn.setValue(TimeUtils.toTime(envelope.fadeIn.get().intValue()));
        this.fadeOut.setValue(TimeUtils.toTime(envelope.fadeOut.get().intValue()));
    }

    public Envelope get()
    {
        return this.panel.clip.envelope;
    }
}