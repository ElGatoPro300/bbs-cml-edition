package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.clips.screen.ColorClip;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import java.util.HashMap;
import java.util.Map;

public class UIColorClip extends UIClip<ColorClip>
{
    private static final int COLOR_OVERLAY = Colors.YELLOW;
    private static final int COLOR_VIGNETTE = Colors.CYAN;
    private static final int COLOR_GRADE = Colors.MAGENTA;
    private static final int COLOR_LIFT = Colors.RED;
    private static final int COLOR_GAMMA = Colors.GREEN;
    private static final int COLOR_GAIN = 0xffffff;
    private static final int COLOR_GROUP = Colors.LIGHTEST_GRAY & 0xffffff;

    public UIColor overlayColor;
    public UIButton edit;
    public UIKeyframeEditor keyframes;

    private final Map<String, Boolean> collapsed = new HashMap<>();

    public UIColorClip(ColorClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.overlayColor = new UIColor((c) -> this.editor.editMultiple(this.clip.overlayColor, (value) ->
        {
            value.set(c);
        }));

        this.keyframes = new UIKeyframeEditor((consumer) -> new UIFilmKeyframes(this.editor, consumer));
        this.keyframes.view.backgroundRenderer((context) ->
        {
            UIReplaysEditor.renderBackground(context, this.keyframes.view, (Clips) this.clip.getParent(), Math.round(this.clip.tick.get()), this.clip);
        });
        this.keyframes.view.duration(() -> this.clip.duration.get());
        this.keyframes.setUndoId("color_keyframes");

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

        this.panels.add(this.section(UIKeys.SCREEN_PANELS_OVERLAY_COLOR, this.overlayColor));
        this.panels.add(this.section(UIKeys.SCREEN_PANELS_KEYFRAMES, this.edit));
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.overlayColor.setColor(this.clip.overlayColor.get());
        this.rebuildChannels();
    }

    private void rebuildChannels()
    {
        UIKeyframes view = this.keyframes.view;

        view.removeAllSheets();

        this.addGroup(view, "overlay", UIKeys.COLOR_CLIP_OVERLAY, COLOR_OVERLAY,
            new KeyframeChannel[] {this.clip.overlayAlpha},
            new int[] {COLOR_OVERLAY});

        this.addGroup(view, "grade", UIKeys.COLOR_CLIP_GRADE, COLOR_GRADE,
            new KeyframeChannel[] {this.clip.saturation, this.clip.hue, this.clip.brightness, this.clip.contrast},
            new int[] {Colors.YELLOW, Colors.MAGENTA, Colors.WHITE & 0xffffff, Colors.CYAN});

        this.addGroup(view, "lift", UIKeys.COLOR_CLIP_LIFT, COLOR_LIFT,
            new KeyframeChannel[] {this.clip.liftR, this.clip.liftG, this.clip.liftB},
            new int[] {Colors.RED, Colors.GREEN, Colors.BLUE});

        this.addGroup(view, "gamma", UIKeys.COLOR_CLIP_GAMMA, COLOR_GAMMA,
            new KeyframeChannel[] {this.clip.gammaR, this.clip.gammaG, this.clip.gammaB},
            new int[] {Colors.RED, Colors.GREEN, Colors.BLUE});

        this.addGroup(view, "gain", UIKeys.COLOR_CLIP_GAIN, COLOR_GAIN,
            new KeyframeChannel[] {this.clip.gainR, this.clip.gainG, this.clip.gainB},
            new int[] {Colors.RED, Colors.GREEN, Colors.BLUE});

        this.keyframes.view.getGraph().clearSelection();
    }

    private void addGroup(UIKeyframes view, String key, IKey title, int color, KeyframeChannel[] channels, int[] colors)
    {
        boolean expanded = !this.collapsed.getOrDefault(key, false);

        UIKeyframeSheet header = UIKeyframeSheet.groupHeader(
            "__color__" + key,
            title,
            COLOR_GROUP,
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
            for (int i = 0; i < channels.length; i++)
            {
                UIKeyframeSheet sheet = new UIKeyframeSheet(colors[i % colors.length], false, channels[i], null);

                sheet.level = 1;
                sheet.groupKey = key;
                view.addSheet(sheet);
            }
        }
    }

    @Override
    protected UIKeyframeEditor resolveClipEmbeddableView(String undoId)
    {
        return undoId.equals(this.keyframes.getUndoId()) ? this.keyframes : null;
    }
}
