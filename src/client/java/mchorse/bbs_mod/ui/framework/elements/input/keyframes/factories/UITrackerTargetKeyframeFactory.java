package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.utils.UIBezierHandles;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

import java.util.List;

import io.netty.util.collection.IntObjectMap;

/**
 * Target track editor for tracker keyframes: pick the tracked replay/morph
 * with the same actor menu as anchors, instead of typing an index.
 */
public class UITrackerTargetKeyframeFactory extends UIKeyframeFactory<Double>
{
    private final UIButton actor;
    private final UIBezierHandles handles;

    public UITrackerTargetKeyframeFactory(Keyframe<Double> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);

        this.actor = new UIButton(UIKeys.GENERIC_KEYFRAMES_ANCHOR_PICK_ACTOR, (b) -> this.displayActors());
        this.actor.tooltip(UIKeys.CAMERA_PANELS_TARGET_TOOLTIP);
        this.handles = new UIBezierHandles(keyframe);

        this.scroll.add(this.actor, this.handles.createColumn());
        this.refreshActorLabel();
    }

    private void displayActors()
    {
        UIFilmPanel panel = this.getPanel();

        if (panel == null)
        {
            return;
        }

        int current = this.keyframe.getValue() == null ? -1 : (int) Math.round(this.keyframe.getValue());

        UIAnchorKeyframeFactory.displayActors(this.getContext(), panel.getController().getEntities(), current, this::setActor);
    }

    private void setActor(int actor)
    {
        this.setValue((double) actor);
        this.refreshActorLabel();
    }

    private void refreshActorLabel()
    {
        int index = this.keyframe.getValue() == null ? -1 : (int) Math.round(this.keyframe.getValue());

        if (index < 0)
        {
            this.actor.label = UIKeys.GENERAL_NONE;

            return;
        }

        UIFilmPanel panel = this.getPanel();

        if (panel == null)
        {
            this.actor.label = IKey.constant(String.valueOf(index));

            return;
        }

        IntObjectMap<IEntity> entities = panel.getController().getEntities();
        IEntity entity = entities.get(index);
        List<Replay> replays = panel.getData() != null ? panel.getData().replays.getList() : null;
        Replay replay = replays != null && index < replays.size() ? replays.get(index) : null;
        Form form = entity == null ? null : entity.getForm();
        String name = replay != null
            ? replay.getName()
            : (form == null ? String.valueOf(index) : form.getFormIdOrName());

        this.actor.label = IKey.constant(name);
    }

    private UIFilmPanel getPanel()
    {
        return this.getParent(UIFilmPanel.class);
    }

    @Override
    public void update()
    {
        super.update();

        this.refreshActorLabel();
        this.handles.setKeyframe(this.keyframe);
        this.handles.update();
    }
}
