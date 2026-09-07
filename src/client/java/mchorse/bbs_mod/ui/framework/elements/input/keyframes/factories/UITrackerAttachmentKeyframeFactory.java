package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.camera.clips.modifiers.TrackerClip;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.utils.UIBezierHandles;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

/**
 * Attachment track editor for tracker keyframes: pick a bone / body part with
 * the same menu as anchors, and show that part's name on the button.
 */
public class UITrackerAttachmentKeyframeFactory extends UIKeyframeFactory<String>
{
    private final UIButton attachment;
    private final UIBezierHandles handles;

    public UITrackerAttachmentKeyframeFactory(Keyframe<String> keyframe, UIKeyframes editor)
    {
        super(keyframe, editor);

        this.attachment = new UIButton(UIKeys.GENERIC_KEYFRAMES_ANCHOR_PICK_ATTACHMENT, (b) -> this.displayAttachments());
        this.handles = new UIBezierHandles(keyframe);

        this.scroll.add(this.attachment, this.handles.createColumn());
        this.refreshAttachmentLabel();
    }

    private void displayAttachments()
    {
        UIFilmPanel panel = this.getPanel();

        if (panel == null)
        {
            return;
        }

        String current = this.keyframe.getValue() == null ? "" : this.keyframe.getValue();

        UIAnchorKeyframeFactory.displayAttachments(panel, this.resolveTargetIndex(), current, this::setAttachment);
    }

    private void setAttachment(String attachment)
    {
        this.setValue(attachment == null ? "" : attachment);
        this.refreshAttachmentLabel();
    }

    private int resolveTargetIndex()
    {
        TrackerClip clip = this.findTrackerClip();

        if (clip == null)
        {
            return -1;
        }

        if (clip.useKeyframes.get())
        {
            return clip.evaluateTarget(this.keyframe.getTick());
        }

        return clip.selector.get();
    }

    private TrackerClip findTrackerClip()
    {
        BaseValue current = this.keyframe.getParent();

        while (current != null)
        {
            if (current instanceof TrackerClip clip)
            {
                return clip;
            }

            current = current.getParent();
        }

        return null;
    }

    private void refreshAttachmentLabel()
    {
        String value = this.keyframe.getValue();

        if (value == null || value.isEmpty())
        {
            this.attachment.label = UIKeys.GENERAL_NONE;

            return;
        }

        UIFilmPanel panel = this.getPanel();
        int index = this.resolveTargetIndex();

        if (panel == null || index < 0)
        {
            this.attachment.label = IKey.constant(this.displayName(value));

            return;
        }

        IEntity entity = panel.getController().getEntities().get(index);
        Form form = entity == null ? null : entity.getForm();

        if (form != null)
        {
            Form path = FormUtils.getForm(form, value);

            if (path != null)
            {
                this.attachment.label = IKey.constant(path.getTrackName(value));

                return;
            }
        }

        this.attachment.label = IKey.constant(this.displayName(value));
    }

    private String displayName(String path)
    {
        int slash = path.lastIndexOf('/');

        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private UIFilmPanel getPanel()
    {
        return this.getParent(UIFilmPanel.class);
    }

    @Override
    public void update()
    {
        super.update();

        this.refreshAttachmentLabel();
        this.handles.setKeyframe(this.keyframe);
        this.handles.update();
    }
}
