package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.forms.forms.utils.ShakeSettings;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.clips.UIClip;
import mchorse.bbs_mod.ui.film.clips.UIShakeClip;
import mchorse.bbs_mod.ui.film.clips.widgets.UIBitToggle;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

import java.util.function.Consumer;

/**
 * Keyframe editor for the model {@code shake} track — same layout as {@link UIShakeClip}.
 */
public class UIShakeSettingsKeyframeFactory extends UIKeyframeFactory<ShakeSettings>
{
    private UITrackpad shake;
    private UITrackpad shakeAmount;
    private UIBitToggle active;

    public UIShakeSettingsKeyframeFactory(Keyframe<ShakeSettings> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);

        this.shake = new UITrackpad((value) -> this.apply((settings) -> settings.shake = value.floatValue()));
        this.shake.tooltip(UIKeys.CAMERA_PANELS_SHAKE, Direction.BOTTOM);
        this.registerValueTrackpad(this.shake);

        this.shakeAmount = new UITrackpad((value) -> this.apply((settings) -> settings.shakeAmount = value.floatValue()));
        this.shakeAmount.tooltip(UIKeys.CAMERA_PANELS_SHAKE_AMOUNT, Direction.BOTTOM);
        this.registerValueTrackpad(this.shakeAmount);

        this.active = new UIBitToggle((value) -> this.apply((settings) -> settings.active = value)).all();

        this.scroll.add(UI.column(UIClip.label(UIKeys.C_CLIP.get("bbs:shake")), UI.row(5, 0, 20, this.shake, this.shakeAmount)).marginTop(12));
        this.scroll.add(this.active);

        this.update();
    }

    @Override
    public void update()
    {
        super.update();

        ShakeSettings value = this.getOrCreate(this.keyframe.getValue());

        this.shake.setValue(value.shake);
        this.shakeAmount.setValue(value.shakeAmount);
        this.active.setValue(value.active);
    }

    private ShakeSettings getOrCreate(ShakeSettings settings)
    {
        return settings == null ? new ShakeSettings() : settings;
    }

    private void apply(Consumer<ShakeSettings> consumer)
    {
        boolean[] applied = {false};

        UIReplaysEditorUtils.forEachSelectedKeyframe(this.editor, this.keyframe, (selected) ->
        {
            applied[0] = true;

            ShakeSettings settings = this.getOrCreate((ShakeSettings) selected.getValue()).copy();

            consumer.accept(settings);
            selected.setValue(settings, true);
        });

        if (!applied[0])
        {
            ShakeSettings settings = this.getOrCreate(this.keyframe.getValue()).copy();

            consumer.accept(settings);
            this.keyframe.setValue(settings, true);
        }

        this.editor.triggerChange();
    }
}
